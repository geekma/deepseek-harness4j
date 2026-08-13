package com.deepseek.harness4j;

import com.deepseek.harness4j.error.SdkProtocolException;
import com.deepseek.harness4j.model.Notification;
import com.deepseek.harness4j.test.FakeRuntime;
import com.deepseek.harness4j.test.TestRuntimes;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Port of the high-level SDK tests in {@code python/sdk/tests/test_client.py} that exercise
 * {@code DeepSeekHarness} / {@code Session} end to end against the {@link FakeRuntime}
 * subprocess.
 */
class HighLevelApiTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tmp;

    @Test
    void test_relative_cwd_is_absolute_in_process_environment_and_wire() throws Exception {
        Path capture = tmp.resolve("cwd.json");
        // Modern JDKs resolve Path.of(".") against the OS working directory, not user.dir, so
        // pass a genuinely relative path from the process cwd to the temp workspace.
        Path cwdAbsolute = Path.of("").toAbsolutePath().normalize();
        String relativeCwd = cwdAbsolute.relativize(tmp.toRealPath()).toString();

        try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
                .cwd(relativeCwd)
                .runtimeCwd(relativeCwd)
                .launchArgsOverride(TestRuntimes.fakeRuntimeCommand())
                .env(Map.of("FR_SCENARIO", "capture-cwd", "CAPTURE", capture.toString()))
                .build())) {
            // Python's context manager starts the harness on entry; Java try-with-resources
            // only closes, so the start (and the initialize handshake) must be explicit.
            harness.start();
        }

        String expected = tmp.toRealPath().toString();
        Map<String, Object> captured = readJson(capture);
        assertEquals(expected, captured.get("process"));
        assertEquals(expected, captured.get("environment"));
        assertEquals(expected, captured.get("wire"));
    }

    @Test
    void test_high_level_sdk_runs_turn_and_collects_final_response() throws Exception {
        Path envDump = tmp.resolve("env.json");
        Path initDump = tmp.resolve("init.json");
        Map<String, String> env = new LinkedHashMap<>();
        env.put("FR_SCENARIO", "main-turn");
        env.put("ENV_DUMP", envDump.toString());
        env.put("INIT_DUMP", initDump.toString());
        env.put("DEEPSEEK_API_KEY", "env-key");
        env.put("DEEPSEEK_BASE_URL", "http://127.0.0.1:4321");

        RunResult result;
        try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
                .model("deepseek-v4-flash")
                .maxTokens(4096)
                .cwd(tmp.toString())
                .cordis(tmp.resolve("cordis.yml").toString())
                .sessionRoot(tmp.resolve("sessions").toString())
                .launchArgsOverride(TestRuntimes.fakeRuntimeCommand())
                .env(env)
                .build())) {
            result = harness.run("say hello", "main", null);
        }

        assertEquals("hello from runtime", result.finalResponse());
        assertEquals("max-tokens", result.finishReason());
        assertEquals("turn/end", result.events().get(result.events().size() - 1).get("type"));
        Map<String, Object> dumpedEnv = readJson(envDump);
        assertEquals("env-key", dumpedEnv.get("DEEPSEEK_API_KEY"));
        assertEquals("http://127.0.0.1:4321", dumpedEnv.get("DEEPSEEK_BASE_URL"));
        assertEquals(tmp.toRealPath().toString(), dumpedEnv.get("DSH_CWD"));
        assertEquals(tmp.resolve("sessions").toString(), dumpedEnv.get("DSH_SESSION_ROOT"));
        assertEquals(tmp.resolve("cordis.yml").toString(), dumpedEnv.get("DSH_CORDIS_CONFIG"));
        Map<String, Object> expectedInit = new LinkedHashMap<>();
        expectedInit.put("cwd", tmp.toRealPath().toString());
        expectedInit.put("provider", "deepseek-official");
        expectedInit.put("model", "deepseek-v4-flash");
        expectedInit.put("maxTokens", 4096);
        assertEquals(expectedInit, readJson(initDump));
    }

    @Test
    void test_session_run_invokes_notification_callback_before_returning() {
        List<String> seen = new ArrayList<>();
        RunResult result;
        try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
                .launchArgsOverride(TestRuntimes.fakeRuntimeCommand())
                .cwd(tmp.toString())
                .env(Map.of("FR_SCENARIO", "callback-subagent"))
                .build())) {
            Session session = harness.startSession("main");
            result = session.run("spawn a helper",
                    notification -> seen.add(notification.method()));
        }

        assertEquals(List.of("session.event", "session.status", "subagent.started", "session.status"), seen);
        assertNull(result.finishReason());
    }

    @Test
    void test_high_level_sdk_rejects_turn_end_without_reason_kind() {
        try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
                .launchArgsOverride(TestRuntimes.fakeRuntimeCommand())
                .cwd(tmp.toString())
                .env(Map.of("FR_SCENARIO", "malformed-turn-end"))
                .build())) {
            SdkProtocolException exc = assertThrows(SdkProtocolException.class,
                    () -> harness.run("reject malformed turn ending", "main", null));
            assertTrue(exc.getMessage().contains(
                    "turn/end event requires a string data.reason.kind"));
        }
    }

    @Test
    void test_session_run_includes_subagent_finished_for_parent_session() {
        RunResult result;
        try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
                .launchArgsOverride(TestRuntimes.fakeRuntimeCommand())
                .cwd(tmp.toString())
                .env(Map.of("FR_SCENARIO", "subagent-finished"))
                .build())) {
            result = harness.run("spawn a helper", "main", null);
        }

        List<String> methods = result.notifications().stream().map(Notification::method).toList();
        assertEquals(List.of(
                "session.event",
                "session.status",
                "subagent.started",
                "subagent.finished",
                "session.status"), methods);
    }

    @Test
    void test_session_run_collects_nested_subagent_tree_without_polluting_root_events() {
        List<String> seen = new ArrayList<>();
        RunResult result;
        try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
                .launchArgsOverride(TestRuntimes.fakeRuntimeCommand())
                .cwd(tmp.toString())
                .env(Map.of("FR_SCENARIO", "nested-tree"))
                .build())) {
            result = harness.run("delegate recursively", "main",
                    notification -> seen.add(notification.method()));
            assertEquals(0, harness.client().notificationsQueue().size());
        }

        assertEquals("root response", result.finalResponse());
        List<String> assistantTexts = new ArrayList<>();
        for (Map<String, Object> event : result.events()) {
            if ("assistant/message".equals(event.get("type"))) {
                Map<String, Object> data = (Map<String, Object>) event.get("data");
                List<Map<String, Object>> content = (List<Map<String, Object>>) data.get("content");
                assistantTexts.add(String.valueOf(content.get(0).get("text")));
            }
        }
        assertEquals(List.of("root response"), assistantTexts);
        List<String> methods = result.notifications().stream().map(Notification::method).toList();
        assertEquals(List.of(
                "session.event",
                "session.status",
                "subagent.started",
                "session.event",
                "subagent.started",
                "session.event",
                "subagent.finished",
                "subagent.finished",
                "session.event",
                "session.status"), methods);
        assertEquals(methods, seen);
    }

    @Test
    void test_session_run_ignores_notifications_for_other_sessions() {
        RunResult result;
        try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
                .launchArgsOverride(TestRuntimes.fakeRuntimeCommand())
                .cwd(tmp.toString())
                .env(Map.of("FR_SCENARIO", "other-session"))
                .build())) {
            result = harness.run("stay in your lane", "main", null);
        }

        assertEquals("right session", result.finalResponse());
        assertEquals(List.of("main", "main", "main", "main"),
                result.notifications().stream().map(n -> n.payload().get("sessionId")).toList());
    }

    @Test
    void test_high_level_session_run_does_not_accumulate_global_notifications() {
        try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
                .launchArgsOverride(TestRuntimes.fakeRuntimeCommand())
                .cwd(tmp.toString())
                .env(Map.of("FR_SCENARIO", "minimal-turn"))
                .build())) {
            harness.run("one turn", "main", null);
            assertEquals(0, harness.client().notificationsQueue().size());
        }
    }

    @Test
    void test_session_run_waits_for_late_idle_without_replaying_stale_notifications() {
        RunResult first;
        RunResult second;
        try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
                .launchArgsOverride(TestRuntimes.fakeRuntimeCommand())
                .cwd(tmp.toString())
                .env(Map.of("FR_SCENARIO", "late-idle"))
                .build())) {
            first = harness.run("first turn", "main", null);
            second = harness.run("second turn", "main", null);
        }

        assertEquals("first", first.finalResponse());
        assertEquals("second", second.finalResponse());
        assertEquals(List.of("main", "main", "main", "main"),
                second.notifications().stream().map(n -> n.payload().get("sessionId")).toList());
    }

    private Map<String, Object> readJson(Path path) throws Exception {
        return MAPPER.readValue(Files.readAllBytes(path), new TypeReference<Map<String, Object>>() {
        });
    }
}
