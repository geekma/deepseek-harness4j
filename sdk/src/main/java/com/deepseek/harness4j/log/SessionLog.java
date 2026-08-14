package com.deepseek.harness4j.log;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Pure-Java offline session log engine: reads, replays, searches, and forks the
 * append-only JSONL session artifacts written by the bundled
 * {@code dsh-session-persistence-jsonl} runtime plugin.
 *
 * <p>Supports both physical encodings, auto-detected by file suffix:
 * <ul>
 *   <li>{@code session.jsonl} — newline-delimited UTF-8 JSON (runtime
 *       {@code compression: none});</li>
 *   <li>{@code session.jsonl.zstd} — checksummed Zstandard frames (runtime default
 *       {@code compression: zstd}); the first frame holds exactly the one-line
 *       header record, each later frame one durable append batch.</li>
 * </ul>
 *
 * <p>On-disk layout (mirrors upstream {@code dsh-session-persistence-jsonl}):
 * <pre>{@code
 * <root>/<projectKey(cwd)>/<encodeSegment(sessionId)>/session.jsonl[.zstd]
 * }</pre>
 *
 * <p>The first record is a {@code type:"session"} header line carrying the session
 * metadata ({@code version,id,createdAt,cwd,parentSession,seedLength,delegationDepth,...});
 * every later line is one {@code SessionEvent} ({@code seq,type,time,data}) or a packed
 * chunk row ({@code text-chunks} / {@code reasoning-chunks} / {@code tool-call-chunks}),
 * which this engine transparently expands back into {@code assistant/chunk} events so a
 * replay matches the live stream byte-for-byte.
 */
public final class SessionLog {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    /** Immutable session metadata parsed from the first header line of a log. */
    public record Header(
            int version,
            String id,
            long createdAt,
            String cwd,
            String parentSession,
            Integer seedLength,
            String origin,
            int delegationDepth,
            String agentPreset) {

        static Header from(Map<String, Object> line) {
            return new Header(
                    asInt(line, "version", 0),
                    asString(line, "id", ""),
                    asLong(line, "createdAt", 0L),
                    asString(line, "cwd", null),
                    asString(line, "parentSession", null),
                    asInteger(line, "seedLength", null),
                    asString(line, "origin", null),
                    asInt(line, "delegationDepth", 0),
                    asString(line, "agentPreset", null));
        }
    }

    /** Search predicate over one session's event stream. */
    public record Query(
            String eventType,
            String textContains,
            Long fromTimestamp,
            Long toTimestamp) {

        public Query {
            if (eventType == null && textContains == null
                    && fromTimestamp == null && toTimestamp == null) {
                throw new IllegalArgumentException(
                        "at least one filter (eventType/textContains/fromTimestamp/toTimestamp) is required");
            }
        }
    }

    /** One cross-session search hit. */
    public record SearchHit(String sessionId, Map<String, Object> event) {
    }

    private SessionLog() {
    }

    /**
     * List all persisted sessions under the root. Reads only each artifact's first
     * (header) record, so the call scales with session count, not log size.
     *
     * @param sessionRoot the configured session persistence root
     * @return one {@link Header} per discovered session log, in file-walk order
     */
    public static List<Header> list(Path sessionRoot) {
        Objects.requireNonNull(sessionRoot, "sessionRoot");
        if (!Files.isDirectory(sessionRoot)) {
            return List.of();
        }
        List<Header> headers = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(sessionRoot)) {
            walk.filter(SessionLog::isSessionArtifact).forEach(path -> {
                Header header = readHeader(path);
                if (header != null) {
                    headers.add(header);
                }
            });
        } catch (IOException error) {
            throw new IllegalStateException("cannot list sessions under " + sessionRoot, error);
        }
        return headers;
    }

    /**
     * Read the full event stream of one session, in log order, expanding packed chunk
     * rows into the original {@code assistant/chunk} events.
     *
     * @param sessionRoot the configured session persistence root
     * @param sessionId   the session id (looked up across project directories)
     * @return the decoded events; empty when the session does not exist
     */
    public static List<Map<String, Object>> read(Path sessionRoot, String sessionId) {
        return stream(sessionRoot, sessionId).toList();
    }

    /**
     * Low-memory streaming read of one session's events. The returned stream is lazy and
     * closes its underlying reader on terminal operations; callers must close it when
     * abandoning early.
     */
    public static Stream<Map<String, Object>> stream(Path sessionRoot, String sessionId) {
        Objects.requireNonNull(sessionRoot, "sessionRoot");
        Objects.requireNonNull(sessionId, "sessionId");
        Path artifact = findArtifact(sessionRoot, sessionId);
        if (artifact == null) {
            return Stream.empty();
        }
        boolean zstd = artifact.toString().endsWith(".jsonl.zstd");
        try {
            InputStream raw = Files.newInputStream(artifact);
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    zstd ? new ZstdInputStream(raw) : raw, StandardCharsets.UTF_8));
            String headerLine = reader.readLine();
            if (headerLine == null) {
                reader.close();
                return Stream.empty();
            }
            // Skip the header record; every remaining line is one event or packed chunk row.
            return reader.lines()
                    .flatMap(SessionLog::decodeLine)
                    .onClose(() -> closeQuietly(reader));
        } catch (IOException error) {
            throw new IllegalStateException(
                    "cannot read session log " + artifact + " for " + sessionId, error);
        }
    }

    /**
     * Replay view: the human/tool interaction events of one session in log order,
     * excluding pure metadata (request headers, context, token metering, todos).
     */
    public static List<Map<String, Object>> replay(Path sessionRoot, String sessionId) {
        return stream(sessionRoot, sessionId)
                .filter(event -> REPLAY_TYPES.contains(event.get("type")))
                .toList();
    }

    /**
     * In-session search: filter the event stream by {@link Query}.
     */
    public static List<Map<String, Object>> search(Path sessionRoot, String sessionId,
                                                   Query query) {
        Objects.requireNonNull(query, "query");
        return stream(sessionRoot, sessionId).filter(event -> matches(event, query)).toList();
    }

    /**
     * Cross-session search: apply one {@link Query} to every session under the root.
     */
    public static List<SearchHit> searchAll(Path sessionRoot, Query query) {
        Objects.requireNonNull(query, "query");
        List<SearchHit> hits = new ArrayList<>();
        for (Header header : list(sessionRoot)) {
            List<Map<String, Object>> matched = search(sessionRoot, header.id(), query);
            for (Map<String, Object> event : matched) {
                hits.add(new SearchHit(header.id(), event));
            }
        }
        return hits;
    }

    /**
     * Log-level fork: copy one session's log to a new id, setting the new header's
     * {@code parentSession} to the source id and {@code seedLength} to the source event
     * count. The produced artifact is placed in the same project directory and keeps the
     * source's physical encoding, so the runtime can {@code resume} it as a fresh branch.
     *
     * @param sessionRoot the configured session persistence root
     * @param sourceId    the session to branch from
     * @param newId       the new branch session id (must not exist yet)
     * @return the new session's header
     */
    public static Header fork(Path sessionRoot, String sourceId, String newId) {
        Objects.requireNonNull(sessionRoot, "sessionRoot");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(newId, "newId");
        Path source = findArtifact(sessionRoot, sourceId);
        if (source == null) {
            throw new IllegalArgumentException("source session not found: " + sourceId);
        }
        Header sourceHeader = requireHeader(source);
        List<Map<String, Object>> events = read(sessionRoot, sourceId);
        boolean zstd = source.toString().endsWith(".jsonl.zstd");

        Path projectDir = source.getParent().getParent();
        Path targetDir = projectDir.resolve(encodeSegment(newId));
        try {
            Files.createDirectories(targetDir);
        } catch (IOException error) {
            throw new IllegalStateException("cannot create fork directory " + targetDir, error);
        }
        Path target = targetDir.resolve(zstd ? "session.jsonl.zstd" : "session.jsonl");

        Map<String, Object> headerLine = new LinkedHashMap<>();
        headerLine.put("type", "session");
        headerLine.put("version", sourceHeader.version());
        headerLine.put("id", newId);
        headerLine.put("createdAt", System.currentTimeMillis());
        if (sourceHeader.cwd() != null) {
            headerLine.put("cwd", sourceHeader.cwd());
        }
        headerLine.put("parentSession", sourceId);
        headerLine.put("seedLength", events.size());
        headerLine.put("delegationDepth", sourceHeader.delegationDepth());
        if (sourceHeader.agentPreset() != null) {
            headerLine.put("agentPreset", sourceHeader.agentPreset());
        }

        try {
            if (zstd) {
                writeZstd(target, headerLine, events);
            } else {
                writePlain(target, headerLine, events);
            }
        } catch (IOException error) {
            throw new IllegalStateException("cannot write fork " + target, error);
        }
        return Header.from(headerLine);
    }

    // -----------------------------------------------------------------------------------
    // Event decoding
    // -----------------------------------------------------------------------------------

    /** The event types included in a {@link #replay} view. */
    private static final List<Object> REPLAY_TYPES = List.of(
            "user/message",
            "assistant/message",
            "assistant/chunk",
            "tool/call",
            "tool/result",
            "turn/start",
            "turn/end",
            "step/start",
            "step/end",
            "agent/inbox/spliced",
            "session",
            "session/title");

    /** Decode one JSONL record into zero (ignorable) or more events, expanding packed chunk rows. */
    private static Stream<Map<String, Object>> decodeLine(String line) {
        if (line.isBlank()) {
            return Stream.empty();
        }
        Map<String, Object> value;
        try {
            value = MAPPER.readValue(line, MAP_TYPE);
        } catch (IOException error) {
            throw new IllegalStateException("corrupt session log line: " + line, error);
        }
        String type = asString(value, "type", null);
        switch (type) {
            case "text-chunks":
            case "reasoning-chunks":
            case "tool-call-chunks":
                return expandChunkRow(value).stream();
            default:
                return Stream.of(value);
        }
    }

    /** Expand a packed chunk row back into its exact {@code assistant/chunk} events. */
    private static List<Map<String, Object>> expandChunkRow(Map<String, Object> row) {
        String type = asString(row, "type", null);
        long seq0 = asLong(row, "seq0", 0L);
        long time0 = asLong(row, "time0", 0L);
        Map<String, Object> data = asObject(row.get("data"));
        List<String> members;
        if ("tool-call-chunks".equals(type)) {
            members = stringList(data.get("args"));
        } else {
            members = stringList(data.get("texts"));
        }
        List<Long> dt = longList(data.get("dt"));
        List<Map<String, Object>> events = new ArrayList<>(members.size());
        long time = time0;
        for (int k = 0; k < members.size(); k++) {
            if (k > 0) {
                time += dt.get(k - 1);
            }
            Map<String, Object> chunk = new LinkedHashMap<>();
            switch (type) {
                case "text-chunks" -> {
                    chunk.put("type", "text-delta");
                    chunk.put("index", data.get("index"));
                    chunk.put("text", members.get(k));
                }
                case "reasoning-chunks" -> {
                    chunk.put("type", "reasoning-delta");
                    chunk.put("index", data.get("index"));
                    chunk.put("text", members.get(k));
                }
                case "tool-call-chunks" -> {
                    chunk.put("type", "tool-call-delta");
                    chunk.put("index", data.get("index"));
                    chunk.put("id", data.get("id"));
                    if (data.containsKey("name")) {
                        chunk.put("name", data.get("name"));
                    }
                    chunk.put("argumentsDelta", members.get(k));
                }
                default -> throw new IllegalStateException("unreachable: " + type);
            }
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", "assistant/chunk");
            event.put("seq", seq0 + k);
            event.put("time", time);
            Map<String, Object> eventData = new LinkedHashMap<>();
            eventData.put("turn", data.get("turn"));
            eventData.put("step", data.get("step"));
            eventData.put("chunk", chunk);
            event.put("data", eventData);
            events.add(event);
        }
        return events;
    }

    private static boolean matches(Map<String, Object> event, Query query) {
        if (query.eventType() != null && !query.eventType().equals(event.get("type"))) {
            return false;
        }
        if (query.fromTimestamp() != null || query.toTimestamp() != null) {
            Long time = asLong(event.get("time"), null);
            if (time == null) {
                return false;
            }
            if (query.fromTimestamp() != null && time < query.fromTimestamp()) {
                return false;
            }
            if (query.toTimestamp() != null && time > query.toTimestamp()) {
                return false;
            }
        }
        if (query.textContains() != null) {
            String json;
            try {
                json = MAPPER.writeValueAsString(event);
            } catch (IOException error) {
                throw new IllegalStateException("cannot serialize event for search", error);
            }
            if (!json.contains(query.textContains())) {
                return false;
            }
        }
        return true;
    }

    // -----------------------------------------------------------------------------------
    // Artifact discovery
    // -----------------------------------------------------------------------------------

    private static boolean isSessionArtifact(Path path) {
        String name = path.getFileName().toString();
        return "session.jsonl".equals(name) || "session.jsonl.zstd".equals(name);
    }

    /** Locate the artifact for a session id by scanning every header (cwd is unknown up front). */
    private static Path findArtifact(Path sessionRoot, String sessionId) {
        if (!Files.isDirectory(sessionRoot)) {
            return null;
        }
        try (Stream<Path> walk = Files.walk(sessionRoot)) {
            return walk.filter(SessionLog::isSessionArtifact)
                    .filter(path -> sessionId.equals(requireHeader(path).id()))
                    .findFirst()
                    .orElse(null);
        } catch (IOException error) {
            throw new IllegalStateException("cannot search sessions under " + sessionRoot, error);
        }
    }

    private static Header readHeader(Path artifact) {
        try (InputStream raw = Files.newInputStream(artifact)) {
            boolean zstd = artifact.toString().endsWith(".jsonl.zstd");
            InputStream in = zstd ? new ZstdInputStream(raw) : raw;
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            String firstLine = reader.readLine();
            if (firstLine == null) {
                return null;
            }
            Map<String, Object> line = MAPPER.readValue(firstLine, MAP_TYPE);
            if (!"session".equals(line.get("type"))) {
                return null;
            }
            return Header.from(line);
        } catch (IOException error) {
            throw new IllegalStateException("cannot read session header " + artifact, error);
        }
    }

    private static Header requireHeader(Path artifact) {
        Header header = readHeader(artifact);
        if (header == null) {
            throw new IllegalStateException("artifact has no session header: " + artifact);
        }
        return header;
    }

    // -----------------------------------------------------------------------------------
    // Writers
    // -----------------------------------------------------------------------------------

    private static void writePlain(Path target, Map<String, Object> headerLine,
                                   List<Map<String, Object>> events) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            writer.write(json(headerLine));
            writer.write('\n');
            for (Map<String, Object> event : events) {
                writer.write(json(event));
                writer.write('\n');
            }
        }
    }

    private static void writeZstd(Path target, Map<String, Object> headerLine,
                                  List<Map<String, Object>> events) throws IOException {
        // The runtime requires the first independently decodable frame to contain exactly
        // the header record; flush the header frame, then write the events in one frame.
        try (OutputStream raw = Files.newOutputStream(target)) {
            ZstdOutputStream out = new ZstdOutputStream(raw, 3);
            out.setChecksum(true);
            out.setCloseFrameOnFlush(true);
            out.write((json(headerLine) + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            for (Map<String, Object> event : events) {
                out.write((json(event) + "\n").getBytes(StandardCharsets.UTF_8));
            }
            out.flush();
            out.close();
        }
    }

    // -----------------------------------------------------------------------------------
    // Path segment encoding (port of upstream encodeSegment)
    // -----------------------------------------------------------------------------------

    /** Encode an arbitrary session id as one safe path segment (injective over UTF-16). */
    static String encodeSegment(String raw) {
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("cannot encode an empty path segment");
        }
        if (".".equals(raw)) {
            return "~002E";
        }
        if ("..".equals(raw)) {
            return "~002E~002E";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch != '~' && (Character.isLetterOrDigit(ch) || ch == '.' || ch == '-' || ch == '_')) {
                out.append(ch);
            } else {
                out.append('~').append(String.format("%04X", (int) ch));
            }
        }
        return out.toString();
    }

    // -----------------------------------------------------------------------------------
    // JSON and accessor helpers
    // -----------------------------------------------------------------------------------

    private static String json(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (IOException error) {
            throw new IllegalStateException("cannot serialize " + value, error);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObject(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : new LinkedHashMap<>();
    }

    private static String asString(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        return value instanceof String ? (String) value : fallback;
    }

    private static int asInt(Map<String, Object> map, String key, int fallback) {
        Object value = map.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static Integer asInteger(Map<String, Object> map, String key, Integer fallback) {
        Object value = map.get(key);
        // box explicitly: the conditional would otherwise unbox the Long/Integer fallback
        return value instanceof Number number
                ? Integer.valueOf(number.intValue())
                : fallback;
    }

    private static long asLong(Map<String, Object> map, String key, long fallback) {
        return asLong(map.get(key), fallback);
    }

    private static Long asLong(Object value, Long fallback) {
        return value instanceof Number number
                ? Long.valueOf(number.longValue())
                : fallback;
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalStateException("expected a string array in chunk row, got " + value);
        }
        List<String> out = new ArrayList<>(list.size());
        for (Object entry : list) {
            out.add(String.valueOf(entry));
        }
        return out;
    }

    private static List<Long> longList(Object value) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalStateException("expected a dt array in chunk row, got " + value);
        }
        List<Long> out = new ArrayList<>(list.size());
        for (Object entry : list) {
            out.add(((Number) entry).longValue());
        }
        return out;
    }

    private static void closeQuietly(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException ignored) {
            // best effort on stream teardown
        }
    }
}
