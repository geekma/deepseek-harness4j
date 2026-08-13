package com.deepseek.harness4j;

import com.deepseek.harness4j.client.HarnessClient;
import com.deepseek.harness4j.client.HarnessConfig;
import com.deepseek.harness4j.error.MissingRuntimeException;
import com.deepseek.harness4j.error.TransportClosedException;
import com.deepseek.harness4j.runtime.RuntimeResolver;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Port of the keyless boot tests in {@code python/sdk/tests/test_bundled_runtime.py} for the
 * production exe and development node carrier. Each carrier skips independently when its
 * artifact has not been installed; the dummy API key only satisfies adapter loading —
 * initialize and shutdown do not call a model.
 */
class BundledRuntimeBootTest {

    private static final String[] MODES = {"exe", "node"};

    private static final String CORDIS_YML = """
            - id: sdk-jsonrpc-server
              name: '@deepseek-ai/dsh-sdk-jsonrpc-server'
            - id: agent-core
              name: '@deepseek-ai/dsh-agent-spine-demo'
              config:
                workspaceContext: false
            - id: sessions
              name: '@deepseek-ai/dsh-session-persistence-jsonl'
              config:
                root: './sessions'
            - id: session-checkpoints
              name: '@deepseek-ai/dsh-session-checkpoint-policy'
            - id: subprocess
              name: '@deepseek-ai/dsh-subprocess-local'
            - id: bash
              name: '@deepseek-ai/dsh-bash-local'
              config:
                cwd: '.'
            - id: todo
              name: '@deepseek-ai/dsh-tool-todo'
              config:
                allowParallelInProgress: true
            """;

    @TempDir
    Path tmp;

    private static String[] launchArgsForMode(String mode) {
        try {
            return RuntimeResolver.resolveBundledLaunchArgs(mode);
        } catch (MissingRuntimeException exc) {
            Assumptions.abort("bundled " + mode + "-mode runtime unavailable on this machine: "
                    + exc.getMessage());
            throw new AssertionError("unreachable");
        }
    }

    private static HarnessClient client(Path tmp, String[] launchArgs) {
        return new HarnessClient(HarnessConfig.builder()
                .launchArgsOverride(launchArgs)
                .cwd(tmp.toString())
                .env(Map.of(
                        "DSH_CORDIS_CONFIG", "./cordis.yml",
                        "DSH_SESSION_ROOT", tmp.resolve("sessions").toString(),
                        "DSH_CWD", tmp.toString(),
                        // The lazily mounted adapter requires a key even without a model call.
                        "DEEPSEEK_API_KEY", "sk-dummy-for-boot",
                        "DEEPSEEK_BASE_URL", "http://127.0.0.1:9"))
                .requestTimeoutSeconds(120.0)
                .build());
    }

    @ParameterizedTest
    @ValueSource(strings = {"exe", "node"})
    void test_bundled_runtime_boots_a_cordis_config(String mode) throws Exception {
        String[] launchArgs = launchArgsForMode(mode);
        Files.writeString(tmp.resolve("cordis.yml"), CORDIS_YML);

        try (HarnessClient client = client(tmp, launchArgs)) {
            var init = client.initialize(tmp.toString(), "deepseek-official", "deepseek-v4-pro", null);
            assertNotNull(init.serverInfo());
            assertTrue(init.serverInfo().name().endsWith("sdk-runtime"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"exe", "node"})
    void test_python_sdk_boots_minimal_jsonrpc_config(String mode) {
        String[] launchArgs = launchArgsForMode(mode);
        String model = "minimal-environment-model";
        DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
                .model(model)
                .cwd(tmp.toString())
                .sessionRoot(tmp.resolve("sessions").toString())
                .env(Map.of(
                        "DSH_MODEL", model,
                        "DSH_CONTEXT_WINDOW", "1000000",
                        "DSH_SYSTEM_PROMPT", "You are the Java SDK minimal boot test agent."))
                .apiKey("sk-dummy-for-boot")
                .baseUrl("http://127.0.0.1:9")
                .launchArgsOverride(launchArgs)
                .requestTimeoutSeconds(120.0)
                .build());

        try (harness) {
            // start + initialize only; no model call
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"exe", "node"})
    void test_bundled_runtime_surfaces_unbundled_plugin_failure(String mode) throws Exception {
        String[] launchArgs = launchArgsForMode(mode);
        Files.writeString(tmp.resolve("cordis.yml"),
                "- id: missing\n  name: '@deepseek-ai/dsh-does-not-exist'\n");

        HarnessClient client = client(tmp, launchArgs);
        client.start();
        try {
            Exception exc = assertThrows(Exception.class, () -> client.initialize(
                    tmp.toString(), "deepseek-official", "deepseek-v4-pro", null));
            assertTrue(exc instanceof TransportClosedException
                            || exc instanceof com.deepseek.harness4j.error.HarnessTimeoutException,
                    "expected TransportClosedError or TimeoutError, got " + exc);
            assertTrue(String.valueOf(exc.getMessage()).contains("@deepseek-ai/dsh-does-not-exist"));
        } finally {
            client.close();
        }
    }

    @Test
    void test_zero_config_run_injects_bundled_default_cordis_config() {
        launchArgsForMode("exe"); // skip early when this carrier is unavailable
        RuntimeResolver.runtimeModeEnvOverride = "exe";

        DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
                .model("deepseek-v4-pro")
                .cwd(tmp.toString())
                .sessionRoot(tmp.resolve("sessions").toString())
                .apiKey("sk-dummy-for-boot")
                .baseUrl("http://127.0.0.1:9")
                .requestTimeoutSeconds(120.0)
                .build());
        try (harness) {
            // zero-config run: the bundled default cordis.yml is injected
        }
    }
}
