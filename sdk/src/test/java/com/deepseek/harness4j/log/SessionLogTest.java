package com.deepseek.harness4j.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.luben.zstd.ZstdOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Offline session-log engine tests: plain JSONL, Zstandard encoding, packed chunk rows,
 * search, replay, and log-level fork. Fixtures are hand-written in the exact
 * {@code dsh-session-persistence-jsonl} layout so the engine is verified against the
 * upstream on-disk contract.
 */
class SessionLogTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String CWD = "/Users/dev/work/sample";
    private static final String PROJECT_DIR = "--Users-dev-work-sample--";

    @TempDir
    Path tmp;

    // -----------------------------------------------------------------------------------
    // list / read (plain JSONL)
    // -----------------------------------------------------------------------------------

    @Test
    void test_list_and_read_plain_jsonl() throws Exception {
        Path root = tmp.resolve("sessions");
        writePlainSession(root, PROJECT_DIR, "session-alpha", alphaHeader(), alphaEvents());

        List<SessionLog.Header> headers = SessionLog.list(root);
        assertEquals(1, headers.size());
        SessionLog.Header header = headers.get(0);
        assertEquals("session-alpha", header.id());
        assertEquals(CWD, header.cwd());
        assertEquals(0, header.version());
        assertEquals(1700000000000L, header.createdAt());

        List<Map<String, Object>> events = SessionLog.read(root, "session-alpha");
        assertEquals(3, events.size());
        assertEquals("user/message", events.get(0).get("type"));
        assertEquals("turn/end", events.get(2).get("type"));
        // events carry their envelope fields verbatim
        assertEquals(0, ((Number) events.get(0).get("seq")).longValue());
    }

    @Test
    void test_read_unknown_session_returns_empty() throws Exception {
        Path root = tmp.resolve("sessions");
        writePlainSession(root, PROJECT_DIR, "session-alpha", alphaHeader(), alphaEvents());

        assertTrue(SessionLog.read(root, "missing").isEmpty());
        assertTrue(SessionLog.list(root.resolve("does-not-exist")).isEmpty());
    }

    @Test
    void test_list_ignores_unrelated_files_and_project_grouping() throws Exception {
        Path root = tmp.resolve("sessions");
        writePlainSession(root, PROJECT_DIR, "session-alpha", alphaHeader(), alphaEvents());
        writePlainSession(root, "--Other-project--", "session-beta",
                betaHeader(), alphaEvents());
        Files.createDirectories(root.resolve(PROJECT_DIR).resolve("session-alpha").resolve("nested"));
        Files.writeString(root.resolve("README.txt"), "not a session");

        assertEquals(2, SessionLog.list(root).size());
    }

    @Test
    void test_encode_segment_escapes_unsafe_ids() {
        assertEquals("~002E", SessionLog.encodeSegment("."));
        assertEquals("~002E~002E", SessionLog.encodeSegment(".."));
        assertEquals("abc-123_x.y", SessionLog.encodeSegment("abc-123_x.y"));
        assertEquals("~007E~002F", SessionLog.encodeSegment("~/"));
        // '-' is safe and kept literal; '~' is always escaped
        assertEquals("main-child", SessionLog.encodeSegment("main-child"));
        assertEquals("a~007Eb", SessionLog.encodeSegment("a~b"));
    }

    // -----------------------------------------------------------------------------------
    // zstd encoding
    // -----------------------------------------------------------------------------------

    @Test
    void test_read_zstd_log() throws Exception {
        Path root = tmp.resolve("sessions");
        writeZstdSession(root, PROJECT_DIR, "session-z", alphaHeader(), alphaEvents());

        assertTrue(Files.exists(root.resolve(PROJECT_DIR).resolve("session-z")
                .resolve("session.jsonl.zstd")));

        List<SessionLog.Header> headers = SessionLog.list(root);
        assertEquals(1, headers.size());
        assertEquals("session-z", headers.get(0).id());

        List<Map<String, Object>> events = SessionLog.read(root, "session-z");
        assertEquals(3, events.size());
        assertEquals("user/message", events.get(0).get("type"));
        assertEquals("hello", textOf(events.get(1)));
        assertEquals("completed", reasonOf(events.get(2)));
    }

    @Test
    void test_read_mixed_plain_and_zstd_sessions() throws Exception {
        Path root = tmp.resolve("sessions");
        writePlainSession(root, PROJECT_DIR, "session-a", alphaHeader(), alphaEvents());
        writeZstdSession(root, PROJECT_DIR, "session-b", betaHeader(), alphaEvents());

        assertEquals(2, SessionLog.list(root).size());
        assertEquals(3, SessionLog.read(root, "session-a").size());
        assertEquals(3, SessionLog.read(root, "session-b").size());
    }

    @Test
    void test_packed_chunk_rows_expand_to_assistant_chunk_events() throws Exception {
        Path root = tmp.resolve("sessions");
        // One packed reasoning row covering seq 10-12 and one packed text row seq 13-14.
        Map<String, Object> reasoningRow = chunkRow("reasoning-chunks", 10, 1_700_000_000_010L,
                Map.of("turn", 1, "step", 0, "index", 0, "dt", List.of(1L, 2L),
                        "texts", List.of("think", " step", " two")));
        Map<String, Object> textRow = chunkRow("text-chunks", 13, 1_700_000_000_020L,
                Map.of("turn", 1, "step", 0, "index", 1, "dt", List.of(5L),
                        "texts", List.of("hello", " world")));
        writePlainSession(root, PROJECT_DIR, "session-packed",
                alphaHeader(), List.of(reasoningRow, textRow));

        List<Map<String, Object>> events = SessionLog.read(root, "session-packed");
        assertEquals(5, events.size());
        assertEquals("assistant/chunk", events.get(0).get("type"));
        assertEquals(10, ((Number) events.get(0).get("seq")).longValue());
        assertEquals("reasoning-delta", chunkOf(events.get(0)).get("type"));
        assertEquals("think", chunkOf(events.get(0)).get("text"));
        assertEquals(1_700_000_000_011L, ((Number) events.get(1).get("time")).longValue());
        assertEquals(1_700_000_000_013L, ((Number) events.get(2).get("time")).longValue());
        assertEquals("assistant/chunk", events.get(3).get("type"));
        assertEquals("text-delta", chunkOf(events.get(3)).get("type"));
        assertEquals("hello", chunkOf(events.get(3)).get("text"));
        assertEquals(" world", chunkOf(events.get(4)).get("text"));
    }

    @Test
    void test_packed_tool_call_chunks_expand_with_id_and_name() throws Exception {
        Path root = tmp.resolve("sessions");
        Map<String, Object> toolRow = chunkRow("tool-call-chunks", 7, 1_700_000_000_000L,
                Map.of("turn", 1, "step", 0, "index", 2, "id", "call-1", "name", "bash",
                        "dt", List.of(3L), "args", List.of("ls", " -la")));
        writePlainSession(root, PROJECT_DIR, "session-tool", alphaHeader(), List.of(toolRow));

        List<Map<String, Object>> events = SessionLog.read(root, "session-tool");
        assertEquals(2, events.size());
        assertEquals("assistant/chunk", events.get(0).get("type"));
        Map<String, Object> chunk = chunkOf(events.get(0));
        assertEquals("tool-call-delta", chunk.get("type"));
        assertEquals("call-1", chunk.get("id"));
        assertEquals("bash", chunk.get("name"));
        assertEquals("ls", chunk.get("argumentsDelta"));
        assertEquals(" -la", chunkOf(events.get(1)).get("argumentsDelta"));
        assertEquals(1_700_000_000_003L, ((Number) events.get(1).get("time")).longValue());
    }

    // -----------------------------------------------------------------------------------
    // replay / search
    // -----------------------------------------------------------------------------------

    @Test
    void test_replay_filters_metadata_and_keeps_interaction_events() throws Exception {
        Path root = tmp.resolve("sessions");
        List<Map<String, Object>> events = new java.util.ArrayList<>(alphaEvents());
        events.add(event(20, "request/header", 1_700_000_000_100L, Map.of("header", Map.of())));
        events.add(event(21, "token-meter", 1_700_000_000_110L, Map.of()));
        writePlainSession(root, PROJECT_DIR, "session-replay", alphaHeader(), events);

        List<Map<String, Object>> replay = SessionLog.replay(root, "session-replay");
        assertEquals(3, replay.size());
        assertFalse(replay.stream().anyMatch(e -> "request/header".equals(e.get("type"))));
        assertFalse(replay.stream().anyMatch(e -> "token-meter".equals(e.get("type"))));
        assertEquals(List.of("user/message", "assistant/message", "turn/end"),
                replay.stream().map(e -> String.valueOf(e.get("type"))).toList());
    }

    @Test
    void test_search_filters_by_event_type_and_text_and_time() throws Exception {
        Path root = tmp.resolve("sessions");
        writePlainSession(root, PROJECT_DIR, "session-search", alphaHeader(), alphaEvents());

        List<Map<String, Object>> byType = SessionLog.search(root, "session-search",
                new SessionLog.Query("assistant/message", null, null, null));
        assertEquals(1, byType.size());

        // "hello" appears in both the user message text and the assistant reply text
        List<Map<String, Object>> byText = SessionLog.search(root, "session-search",
                new SessionLog.Query(null, "hello", null, null));
        assertEquals(2, byText.size());

        // time window excludes every event (all at 1_700_000_000_0xx)
        List<Map<String, Object>> byTime = SessionLog.search(root, "session-search",
                new SessionLog.Query(null, null, 1_700_000_000_200L, null));
        assertTrue(byTime.isEmpty());

        // text + type combined narrows to the single user message
        List<Map<String, Object>> combined = SessionLog.search(root, "session-search",
                new SessionLog.Query("user/message", "hello", null, null));
        assertEquals(1, combined.size());
    }

    @Test
    void test_search_requires_at_least_one_filter() {
        assertThrows(IllegalArgumentException.class,
                () -> new SessionLog.Query(null, null, null, null));
    }

    @Test
    void test_search_all_crosses_sessions() throws Exception {
        Path root = tmp.resolve("sessions");
        writePlainSession(root, PROJECT_DIR, "session-a", alphaHeader(), alphaEvents());
        writePlainSession(root, "--Other-project--", "session-b", betaHeader(), alphaEvents());

        List<SessionLog.SearchHit> hits = SessionLog.searchAll(root,
                new SessionLog.Query(null, "hello", null, null));
        // two matching events (user + assistant) in each of two sessions
        assertEquals(4, hits.size());
        assertEquals(
                List.of("session-a", "session-b"),
                hits.stream().map(SessionLog.SearchHit::sessionId).distinct().sorted().toList());
    }

    // -----------------------------------------------------------------------------------
    // fork
    // -----------------------------------------------------------------------------------

    @Test
    void test_fork_plain_creates_child_with_parent_lineage_and_seed_length() throws Exception {
        Path root = tmp.resolve("sessions");
        writePlainSession(root, PROJECT_DIR, "session-parent", alphaHeader(), alphaEvents());

        SessionLog.Header child = SessionLog.fork(root, "session-parent", "session-child");

        assertEquals("session-child", child.id());
        assertEquals("session-parent", child.parentSession());
        assertEquals(3, child.seedLength());

        Path childPath = root.resolve(PROJECT_DIR).resolve("session-child")
                .resolve("session.jsonl");
        assertTrue(Files.exists(childPath));

        List<Map<String, Object>> childEvents = SessionLog.read(root, "session-child");
        assertEquals(alphaEvents().size(), childEvents.size());
        assertEquals("hello", textOf(childEvents.get(1)));

        // both sessions still listed
        assertEquals(2, SessionLog.list(root).size());
    }

    @Test
    void test_fork_zstd_preserves_encoding() throws Exception {
        Path root = tmp.resolve("sessions");
        writeZstdSession(root, PROJECT_DIR, "session-parent", alphaHeader(), alphaEvents());

        SessionLog.Header child = SessionLog.fork(root, "session-parent", "session-child-z");

        assertTrue(Files.exists(root.resolve(PROJECT_DIR).resolve("session-child-z")
                .resolve("session.jsonl.zstd")));
        List<Map<String, Object>> childEvents = SessionLog.read(root, "session-child-z");
        assertEquals(3, childEvents.size());
        assertEquals("hello", textOf(childEvents.get(1)));
        assertNotNull(child.parentSession());
    }

    @Test
    void test_fork_missing_source_throws() throws Exception {
        Path root = tmp.resolve("sessions");
        assertThrows(IllegalArgumentException.class,
                () -> SessionLog.fork(root, "missing", "new-id"));
    }

    // -----------------------------------------------------------------------------------
    // fixture builders
    // -----------------------------------------------------------------------------------

    private static Map<String, Object> alphaHeader() {
        return Map.of(
                "type", "session",
                "version", 0,
                "id", "session-alpha",
                "createdAt", 1_700_000_000_000L,
                "cwd", CWD,
                "delegationDepth", 0);
    }

    private static Map<String, Object> betaHeader() {
        Map<String, Object> header = new LinkedHashMap<>(alphaHeader());
        header.put("id", "session-beta");
        return header;
    }

    private static List<Map<String, Object>> alphaEvents() {
        return List.of(
                event(0, "user/message", 1_700_000_000_001L,
                        Map.of("content", List.of(Map.of("type", "text", "text", "hello")))),
                event(1, "assistant/message", 1_700_000_000_010L,
                        Map.of("message", Map.of("content",
                                List.of(Map.of("type", "text", "text", "hello")))),
                        Map.of("inputTokens", 42, "outputTokens", 17, "reasoningTokens", 5)),
                event(2, "turn/end", 1_700_000_000_020L,
                        Map.of("turn", 1, "reason", Map.of("kind", "completed"))));
    }

    private static Map<String, Object> event(long seq, String type, long time,
                                             Map<String, Object> data) {
        return event(seq, type, time, data, null);
    }

    private static Map<String, Object> event(long seq, String type, long time,
                                             Map<String, Object> data, Map<String, Object> usage) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("seq", seq);
        event.put("type", type);
        event.put("time", time);
        Map<String, Object> fullData = new LinkedHashMap<>(data);
        if (usage != null) {
            fullData.put("usage", usage);
        }
        event.put("data", fullData);
        return event;
    }

    private static Map<String, Object> chunkRow(String type, long seq0, long time0,
                                                Map<String, Object> data) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", type);
        row.put("seq0", seq0);
        row.put("time0", time0);
        row.put("data", new LinkedHashMap<>(data));
        return row;
    }

    private static String textOf(Map<String, Object> event) {
        Map<String, Object> data = (Map<String, Object>) event.get("data");
        Object message = data.get("message");
        Map<String, Object> owner = message instanceof Map m ? m : data;
        List<Map<String, Object>> content = (List<Map<String, Object>>) owner.get("content");
        return String.valueOf(content.get(0).get("text"));
    }

    private static String reasonOf(Map<String, Object> event) {
        Map<String, Object> data = (Map<String, Object>) event.get("data");
        Map<String, Object> reason = (Map<String, Object>) data.get("reason");
        return String.valueOf(reason.get("kind"));
    }

    private static Map<String, Object> chunkOf(Map<String, Object> event) {
        Map<String, Object> data = (Map<String, Object>) event.get("data");
        return (Map<String, Object>) data.get("chunk");
    }

    private static void writePlainSession(Path root, String projectDir, String id,
                                          Map<String, Object> header,
                                          List<Map<String, Object>> events) throws Exception {
        Path dir = root.resolve(projectDir).resolve(id);
        Files.createDirectories(dir);
        Map<String, Object> headerLine = new LinkedHashMap<>(header);
        headerLine.put("id", id);
        StringBuilder sb = new StringBuilder();
        sb.append(MAPPER.writeValueAsString(headerLine)).append('\n');
        for (Map<String, Object> event : events) {
            sb.append(MAPPER.writeValueAsString(event)).append('\n');
        }
        Files.writeString(dir.resolve("session.jsonl"), sb.toString(), StandardCharsets.UTF_8);
    }

    private static void writeZstdSession(Path root, String projectDir, String id,
                                         Map<String, Object> header,
                                         List<Map<String, Object>> events) throws Exception {
        Path dir = root.resolve(projectDir).resolve(id);
        Files.createDirectories(dir);
        Map<String, Object> headerLine = new LinkedHashMap<>(header);
        headerLine.put("id", id);
        try (OutputStream raw = Files.newOutputStream(dir.resolve("session.jsonl.zstd"));
             ZstdOutputStream out = new ZstdOutputStream(raw, 3)) {
            out.setChecksum(true);
            out.setCloseFrameOnFlush(true);
            out.write((MAPPER.writeValueAsString(headerLine) + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            for (Map<String, Object> event : events) {
                out.write((MAPPER.writeValueAsString(event) + "\n").getBytes(StandardCharsets.UTF_8));
            }
            out.flush();
            out.close();
        }
    }
}