package com.deepseek.harness4j.client;

import com.deepseek.harness4j.DeepSeekHarnessConfig;
import com.deepseek.harness4j.error.HarnessTimeoutException;
import com.deepseek.harness4j.error.JsonRpcException;
import com.deepseek.harness4j.error.MissingRuntimeException;
import com.deepseek.harness4j.error.TransportClosedException;
import com.deepseek.harness4j.model.IncomingRequest;
import com.deepseek.harness4j.model.InitializeResponse;
import com.deepseek.harness4j.model.Notification;
import com.deepseek.harness4j.runtime.RuntimeResolver;
import com.deepseek.harness4j.test.FakeRuntime;
import com.deepseek.harness4j.test.TestRuntimes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Port of the low-level client tests in {@code python/sdk/tests/test_client.py} that exercise
 * {@link HarnessClient} directly.
 */
class ClientLevelTest {

    @TempDir
    Path tmp;

    private String previousRuntimeDir;

    @AfterEach
    void restoreRuntimeDir() {
        if (previousRuntimeDir != null) {
            System.setProperty(RuntimeResolver.RUNTIME_DIR_PROPERTY, previousRuntimeDir);
        } else {
            System.clearProperty(RuntimeResolver.RUNTIME_DIR_PROPERTY);
        }
    }

    @Test
    void test_client_starts_subprocess_sends_requests_and_routes_notifications() {
        try (HarnessClient client = new HarnessClient(TestRuntimes.fakeConfig("bridge-llm", tmp))) {
            client.start();
            InitializeResponse init = client.initialize("/workspace", "deepseek-official", "dsagent", null);
            assertEquals("fake-runtime", init.serverInfo().name());

            client.sessionPrompt("main", List.of(Map.of("type", "text", "text", "fix it")), null, null);
            Notification notification = client.nextNotification();
            assertEquals("llm/request", notification.method());
            assertEquals("req-1", notification.payload().get("requestId"));
            assertEquals("main", notification.payload().get("sessionId"));
        }
    }

    @Test
    void test_client_routes_bridge_requests_and_sends_responses() {
        try (HarnessClient client = new HarnessClient(TestRuntimes.fakeConfig("bridge-llm", tmp))) {
            client.start();
            client.initialize("/workspace", "deepseek-official", "dsagent", null);

            IncomingRequest request = client.nextRequest();
            assertEquals("bridge-req-1", request.id());
            assertEquals("llm.request", request.method());
            assertEquals("req-1", request.payload().get("requestId"));

            client.respond(request.id(), Map.of("content_blocks",
                    List.of(Map.of("type", "text", "text", "done"))));
            Notification notification = client.nextNotification();
            assertEquals("response/seen", notification.method());
            Map<String, Object> result = (Map<String, Object>) notification.payload().get("result");
            List<Map<String, Object>> contentBlocks = (List<Map<String, Object>>) result.get("content_blocks");
            assertEquals("done", contentBlocks.get(0).get("text"));
        }
    }

    @Test
    void test_client_ignores_non_json_stdout_lines() {
        try (HarnessClient client = new HarnessClient(TestRuntimes.fakeConfig("non-json-first", tmp))) {
            client.start();
            InitializeResponse init = client.initialize("/workspace", "deepseek-official", "dsagent", null);
            assertEquals("fake-runtime", init.serverInfo().name());
        }
    }

    @Test
    void test_client_request_times_out_when_bridge_does_not_respond() {
        HarnessConfig config = HarnessConfig.builder()
                .launchArgsOverride(TestRuntimes.fakeRuntimeCommand())
                .cwd(tmp.toString())
                .env(Map.of("FR_SCENARIO", "sleep-forever"))
                .requestTimeoutSeconds(1.5)
                .build();
        try (HarnessClient client = new HarnessClient(config)) {
            client.start();
            long start = System.nanoTime();
            HarnessTimeoutException exc = assertThrows(HarnessTimeoutException.class,
                    () -> client.initialize("/workspace", "deepseek-official", "dsagent", null));
            double elapsedSeconds = (System.nanoTime() - start) / 1_000_000_000.0;
            assertTrue(elapsedSeconds < 3,
                    "initialize should time out promptly, took " + elapsedSeconds + "s");
            assertTrue(exc.getMessage().contains("bridge is still starting"));
        }
    }

    @Test
    void test_client_close_times_out_when_shutdown_does_not_respond() {
        HarnessConfig config = HarnessConfig.builder()
                .launchArgsOverride(TestRuntimes.fakeRuntimeCommand())
                .cwd(tmp.toString())
                .env(Map.of("FR_SCENARIO", "ignore-sigterm"))
                .shutdownTimeoutSeconds(0.1)
                .build();
        HarnessClient client = new HarnessClient(config);
        client.start();
        Process proc = client.process();
        assertNotNull(proc);
        client.initialize("/workspace", "deepseek-official", "dsagent", null);
        long start = System.nanoTime();
        client.close();
        double elapsedSeconds = (System.nanoTime() - start) / 1_000_000_000.0;
        assertTrue(elapsedSeconds < 2, "close should time out the shutdown, took " + elapsedSeconds + "s");
        assertFalse(proc.isAlive());
        assertNull(client.process());
    }

    @Test
    void test_initialize_failure_reaps_started_runtime() throws Exception {
        HarnessClient client = new HarnessClient(TestRuntimes.fakeConfig("initialize-error", tmp));
        client.start();
        Process proc = client.process();
        assertNotNull(proc);

        JsonRpcException exc = assertThrows(JsonRpcException.class,
                () -> client.initialize(".", "deepseek-official", "dsagent", null));
        assertTrue(exc.getMessage().contains("bad initialize"));

        assertTrue(proc.waitFor(1, TimeUnit.SECONDS), "runtime must be reaped");
        assertNull(client.process());
    }

    @Test
    void test_public_signatures_omit_unsupported_wire_parameters() throws Exception {
        assertTrue(parameterNames(HarnessClient.class, "initialize",
                String.class, String.class, String.class, Integer.class).stream()
                .noneMatch("sessionRoot"::equals));
        assertTrue(parameterNames(HarnessClient.class, "initialize",
                String.class, String.class, String.class, Integer.class).stream()
                .noneMatch("systemPrompt"::equals));
        assertTrue(parameterNames(HarnessClient.class, "sessionPrompt",
                String.class, List.class, java.util.function.Consumer.class,
                NotificationSubscription.class).stream()
                .noneMatch("profile"::equals));
        assertTrue(parameterNames(com.deepseek.harness4j.DeepSeekHarness.class, "run",
                Object.class, String.class, java.util.function.Consumer.class).stream()
                .noneMatch("profile"::equals));
        assertTrue(parameterNames(com.deepseek.harness4j.Session.class, "run",
                Object.class, java.util.function.Consumer.class).stream()
                .noneMatch("profile"::equals));

        assertFalse(hasMethod(DeepSeekHarnessConfig.class, "systemPrompt"));
        assertTrue(hasMethod(DeepSeekHarnessConfig.class, "maxTokens"));
        assertTrue(hasMethod(HarnessClient.class, "initialize",
                String.class, String.class, String.class, Integer.class));

        assertFalse(hasMethod(HarnessConfig.class, "clientName"));
        assertFalse(hasMethod(HarnessConfig.class, "clientVersion"));
    }

    @Test
    void test_client_close_is_idempotent_before_and_after_start() {
        new HarnessClient().close();

        HarnessClient client = new HarnessClient(TestRuntimes.fakeConfig("none", tmp));
        client.start();
        client.initialize("/workspace", "deepseek-official", "dsagent", null);
        client.close();
        client.close();
    }

    @Test
    void test_runtime_closed_error_includes_stderr_tail() {
        try (HarnessClient client = new HarnessClient(TestRuntimes.fakeConfig("crash", tmp))) {
            client.start();
            TransportClosedException exc = assertThrows(TransportClosedException.class,
                    () -> client.initialize("/workspace", "deepseek-official", "dsagent", null));
            assertTrue(exc.getMessage().contains("fatal bridge exploded"));
        }
    }

    @Test
    void test_client_serializes_concurrent_writes() throws Exception {
        Path seen = tmp.resolve("seen.jsonl");
        try (HarnessClient client = new HarnessClient(TestRuntimes.fakeConfig(
                "seen-writes", tmp, Map.of("SEEN", seen.toString())))) {
            client.start();
            client.initialize("/workspace", "deepseek-official", "dsagent", null);
            List<Thread> threads = new java.util.ArrayList<>();
            CountDownLatch latch = new CountDownLatch(50);
            for (int index = 0; index < 50; index++) {
                int i = index;
                Thread thread = new Thread(() -> {
                    try {
                        client.notify("notice-" + i, Map.of("index", i));
                    } finally {
                        latch.countDown();
                    }
                });
                threads.add(thread);
            }
            for (Thread thread : threads) {
                thread.start();
            }
            assertTrue(latch.await(10, TimeUnit.SECONDS), "concurrent writes must finish");
            for (Thread thread : threads) {
                thread.join();
            }
        }
        List<String> lines = Files.readAllLines(seen);
        assertTrue(lines.size() >= 51, "initialize + 50 notices (+ shutdown) must be written");
        for (String line : lines) {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.readTree(line); // every line must be valid JSON
        }
    }

    @Test
    void test_client_rejects_unaccepted_session_prompt_response() {
        try (HarnessClient client = new HarnessClient(TestRuntimes.fakeConfig("reject-prompt", tmp))) {
            client.start();
            client.initialize("/workspace", "deepseek-official", "dsagent", null);
            IllegalArgumentException exc = assertThrows(IllegalArgumentException.class,
                    () -> client.sessionPrompt("main",
                            List.of(Map.of("type", "text", "text", "fix it")), null, null));
            assertTrue(exc.getMessage().contains("requires a messageId"));
        }
    }

    @Test
    void test_client_contains_notification_filter_failure_to_its_subscription() {
        HarnessConfig config = HarnessConfig.builder()
                .launchArgsOverride(TestRuntimes.fakeRuntimeCommand())
                .cwd(tmp.toString())
                .env(Map.of("FR_SCENARIO", "tick"))
                .build();
        try (HarnessClient client = new HarnessClient(config)) {
            client.start();
            client.initialize("/workspace", "deepseek-official", "dsagent", null);
            NotificationFilter brokenFilter = ignored -> {
                throw new IllegalStateException("bad notification filter");
            };
            NotificationFilter healthyFilter = notification -> "tick".equals(notification.method());
            try (NotificationSubscription broken = client.subscribeNotifications(brokenFilter);
                 NotificationSubscription healthy = client.subscribeNotifications(healthyFilter)) {
                client.notify("emit-first", null);
                IllegalStateException exc = assertThrows(IllegalStateException.class, broken::next);
                assertEquals("bad notification filter", exc.getMessage());
                assertEquals(Map.of("source", "emit-first"), healthy.next().payload());
                assertEquals(0, client.notificationsQueue().size());

                client.sessionPrompt("main",
                        List.of(Map.of("type", "text", "text", "reader still works")), null, null);
                client.notify("emit-second", null);
                assertEquals(Map.of("source", "emit-second"), healthy.next().payload());
            }
        }
    }

    @Test
    void test_client_default_launch_uses_bundled_runtime_and_injects_default_config() throws Exception {
        // Port of the pytest parametrization "unset" and "empty-counts-as-absent".
        for (String ambientConfig : new String[]{null, ""}) {
            Path envDump = tmp.resolve("env-" + (ambientConfig == null ? "unset" : "empty") + ".json");
            Path defaultConfig = installFakeBundledRuntime(envDump);

            Map<String, String> env = new LinkedHashMap<>();
            env.put("ENV_DUMP", envDump.toString());
            env.put("FR_SCENARIO", "bundled");
            env.put("FR_CP", System.getProperty("java.class.path"));
            if (ambientConfig != null) {
                env.put("DSH_CORDIS_CONFIG", ambientConfig);
            }

            try (HarnessClient client = new HarnessClient(
                    HarnessConfig.builder().env(env).build())) {
                client.start();
                InitializeResponse init = client.initialize(
                        "/workspace", "deepseek-official", "deepseek-v4-pro", null);
                assertEquals("bundled-runtime", init.serverInfo().name());
            }

            Map<String, Object> dump = readJson(envDump);
            assertEquals(defaultConfig.toString(), dump.get("DSH_CORDIS_CONFIG"));
        }
    }

    @Test
    void test_client_respects_explicit_config_over_bundled_default() throws Exception {
        Path envDump = tmp.resolve("env.json");
        installFakeBundledRuntime(envDump);

        try (HarnessClient client = new HarnessClient(HarnessConfig.builder()
                .env(Map.of("ENV_DUMP", envDump.toString(),
                        "FR_SCENARIO", "bundled",
                        "FR_CP", System.getProperty("java.class.path"),
                        "DSH_CORDIS_CONFIG", "./explicit.yml"))
                .build())) {
            client.start();
            client.initialize("/workspace", "deepseek-official", "deepseek-v4-pro", null);
        }

        assertEquals("./explicit.yml", readJson(envDump).get("DSH_CORDIS_CONFIG"));
    }

    @Test
    void test_client_reports_missing_bundled_runtime_dependency() {
        Path emptyRuntimeDir = tmp.resolve("empty-runtime");
        assertTrue(emptyRuntimeDir.toFile().mkdirs());
        previousRuntimeDir = System.setProperty(
                RuntimeResolver.RUNTIME_DIR_PROPERTY, emptyRuntimeDir.toString());

        MissingRuntimeException exc = assertThrows(MissingRuntimeException.class,
                () -> new HarnessClient().start());
        assertTrue(exc.getMessage().contains("Install deepseek-harness4j-runtime-bin"));
    }

    /**
     * Install a fake bundled runtime package: metadata, default cordis.yml, and an executable
     * wrapper that launches {@link FakeRuntime} on the test classpath.
     *
     * @param envDump file the fake runtime writes its DSH_CORDIS_CONFIG to
     * @return the fake bundled default config path
     */
    private Path installFakeBundledRuntime(Path envDump) throws Exception {
        Path runtimeDir = tmp.resolve("bundled-runtime");
        Path runtime = runtimeDir.resolve("runtime");
        Files.createDirectories(runtime);
        Files.writeString(runtimeDir.resolve(RuntimeResolver.PACKAGE_METADATA_FILENAME),
                "{\"name\":\"deepseek-harness4j-runtime-bin\",\"version\":\"0.0.1\"}\n");

        Path defaultConfig = runtime.resolve("cordis.yml");
        Files.writeString(defaultConfig,
                "- id: sdk-jsonrpc-server\n"
                        + "  name: '@deepseek-ai/dsh-sdk-jsonrpc-server'\n"
                        + "- id: agent-core\n"
                        + "  name: '@deepseek-ai/dsh-agent-spine-demo'\n"
                        + "- id: sessions\n"
                        + "  name: '@deepseek-ai/dsh-session-persistence-jsonl'\n"
                        + "- id: session-checkpoints\n"
                        + "  name: '@deepseek-ai/dsh-session-checkpoint-policy'\n");

        Path executable = runtime.resolve("dsh-jsonrpc-agent-pkg-" + currentPlatformTag());
        Files.writeString(executable,
                "#!/bin/sh\n"
                        + "exec java -cp \"$FR_CP\" " + FakeRuntime.class.getName() + "\n");
        setExecutable(executable);
        if (currentPlatformTag().startsWith("macos-")) {
            Path helper = runtime.resolve(executable.getFileName() + "-spawn-helper");
            Files.writeString(helper, "#!/bin/sh\n");
            setExecutable(helper);
        }

        previousRuntimeDir = System.setProperty(
                RuntimeResolver.RUNTIME_DIR_PROPERTY, runtimeDir.toString());
        return defaultConfig;
    }

    private static void setExecutable(Path path) throws Exception {
        Files.setPosixFilePermissions(path, EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE,
                PosixFilePermission.GROUP_READ,
                PosixFilePermission.GROUP_EXECUTE,
                PosixFilePermission.OTHERS_READ,
                PosixFilePermission.OTHERS_EXECUTE));
    }

    private static String currentPlatformTag() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        String platform = osName.contains("mac") || osName.contains("darwin") ? "macos" : "linux";
        String machine = System.getProperty("os.arch", "").toLowerCase();
        String arch = machine.equals("aarch64") || machine.equals("arm64")
                ? "arm64"
                : "x64";
        return platform + "-" + arch;
    }

    private static Map<String, Object> readJson(Path path) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                Files.readAllBytes(path),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                });
    }

    private static List<String> parameterNames(Class<?> type, String methodName, Class<?>... params)
            throws NoSuchMethodException {
        Method method = type.getDeclaredMethod(methodName, params);
        List<String> names = new java.util.ArrayList<>();
        for (Parameter parameter : method.getParameters()) {
            names.add(parameter.getName());
        }
        return names;
    }

    private static boolean hasMethod(Class<?> type, String name, Class<?>... params) {
        try {
            type.getDeclaredMethod(name, params);
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }
}
