package com.deepseek.harness4j;

import com.deepseek.harness4j.log.SessionLog;
import com.deepseek.harness4j.test.TestRuntimes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepSeekHarnessFacadeTest {

    @TempDir
    Path tmp;

    @Test
    void test_harness_async_and_resume_facades() throws Exception {
        try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
                .launchArgsOverride(TestRuntimes.fakeRuntimeCommand())
                .cwd(tmp.toString())
                .env(Map.of("FR_SCENARIO", "late-idle"))
                .build())) {

            RunResult first = harness.runAsync("first turn", "facade-sess").get(10, TimeUnit.SECONDS);
            assertEquals("first", first.finalResponse());

            RunResult second = harness.resumeAsync("second turn", "facade-sess").get(10, TimeUnit.SECONDS);
            assertEquals("second", second.finalResponse());
        }
    }

    @Test
    void test_harness_session_log_facades() throws Exception {
        Path sessionRoot = tmp.resolve(".sessions");
        Path sessDir = sessionRoot.resolve("proj").resolve("sess-facade");
        Files.createDirectories(sessDir);

        String jsonl = "{\"type\":\"session\",\"version\":1,\"id\":\"sess-facade\",\"cwd\":\"/w\",\"parentSession\":null,\"createdAt\":1000}\n"
                + "{\"type\":\"user/message\",\"time\":1010,\"data\":{\"content\":[{\"type\":\"text\",\"text\":\"hello\"}]}}\n"
                + "{\"type\":\"assistant/message\",\"time\":1020,\"data\":{\"content\":[{\"type\":\"text\",\"text\":\"world\"}]}}\n";

        Files.writeString(sessDir.resolve("session.jsonl"), jsonl, StandardCharsets.UTF_8);

        try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
                .launchArgsOverride(TestRuntimes.fakeRuntimeCommand())
                .cwd(tmp.toString())
                .sessionRoot(sessionRoot.toString())
                .build())) {

            List<SessionLog.Header> headers = harness.listSessions();
            assertEquals(1, headers.size());
            assertEquals("sess-facade", headers.get(0).id());

            List<Map<String, Object>> events = harness.readSessionLog("sess-facade");
            assertEquals(2, events.size());

            List<Map<String, Object>> replay = harness.replaySessionLog("sess-facade");
            assertEquals(2, replay.size());

            List<Map<String, Object>> search = harness.searchSessionLog("sess-facade", new SessionLog.Query(null, "world", null, null));
            assertEquals(1, search.size());

            List<SessionLog.SearchHit> searchAll = harness.searchAllSessions(new SessionLog.Query(null, "hello", null, null));
            assertEquals(1, searchAll.size());
            assertEquals("sess-facade", searchAll.get(0).sessionId());

            SessionLog.Header forked = harness.forkSession("sess-facade", "sess-forked");
            assertEquals("sess-forked", forked.id());
            assertEquals("sess-facade", forked.parentSession());
        }
    }
}
