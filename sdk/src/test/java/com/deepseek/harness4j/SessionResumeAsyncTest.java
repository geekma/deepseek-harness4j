package com.deepseek.harness4j;

import com.deepseek.harness4j.test.FakeRuntime;
import com.deepseek.harness4j.test.TestRuntimes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0 {@link Session} enhancements: the explicit {@code resume()} alias and the asynchronous
 * {@code runAsync()} execution path.
 */
class SessionResumeAsyncTest {

    @TempDir
    Path tmp;

    @Test
    void test_resume_is_an_explicit_alias_for_run_on_the_same_session() {
        RunResult first;
        RunResult second;
        try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
                .launchArgsOverride(TestRuntimes.fakeRuntimeCommand())
                .cwd(tmp.toString())
                .env(Map.of("FR_SCENARIO", "late-idle"))
                .build())) {
            first = harness.run("first turn", "main", null);
            Session session = harness.startSession("main");
            second = session.resume("second turn", null);
        }

        assertEquals("first", first.finalResponse());
        assertEquals("second", second.finalResponse());
        assertEquals("main", second.sessionId());
    }

    @Test
    void test_run_async_resolves_the_same_result_as_run() throws Exception {
        RunResult result;
        try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
                .launchArgsOverride(TestRuntimes.fakeRuntimeCommand())
                .cwd(tmp.toString())
                .env(Map.of("FR_SCENARIO", "minimal-turn"))
                .build())) {
            Session session = harness.startSession("async-main");
            result = session.runAsync("async turn").get(30, TimeUnit.SECONDS);
        }

        assertEquals("async-main", result.sessionId());
        assertEquals("ok", result.finalResponse());
    }

    @Test
    void test_run_async_with_notification_callback() throws Exception {
        java.util.List<String> methods = new java.util.ArrayList<>();
        try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
                .launchArgsOverride(TestRuntimes.fakeRuntimeCommand())
                .cwd(tmp.toString())
                .env(Map.of("FR_SCENARIO", "callback-subagent"))
                .build())) {
            // the scripted subagent edges are rooted at "main"
            Session session = harness.startSession("main");
            RunResult result = session.runAsync("spawn helper",
                    notification -> methods.add(notification.method()))
                    .get(30, TimeUnit.SECONDS);
            assertTrue(methods.contains("subagent.started"));
            assertEquals("main", result.sessionId());
        }
    }
}