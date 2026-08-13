package com.deepseek.harness4j.test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Test-only fake JSON-RPC runtime launched as a subprocess by the SDK tests.
 *
 * <p>This is the Java port of the inline Python "fake_runtime.py" / "fake_bridge.py"
 * scripts embedded in {@code python/sdk/tests/test_client.py}. The behavior is selected by
 * the {@code FR_SCENARIO} environment variable so each test maps to one scripted sequence.
 */
public final class FakeRuntime {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FakeRuntime() {
    }

    public static void main(String[] args) throws Exception {
        String scenario = System.getenv().getOrDefault("FR_SCENARIO", "none");
        PrintStream out = System.out;

        switch (scenario) {
            case "non-json-first" -> out.println("node warning: experimental loader");
            case "sleep-forever" -> {
                System.err.println("bridge is still starting");
                Thread.sleep(60_000);
                return;
            }
            case "crash" -> {
                System.err.println("fatal bridge exploded");
                System.exit(42);
                return;
            }
            default -> {
                // fall through to the reader loop
            }
        }

        if ("ignore-sigterm".equals(scenario)) {
            Thread hook = new Thread(() -> {
                while (true) {
                    try {
                        Thread.sleep(10_000);
                    } catch (InterruptedException ignored) {
                        // keep the JVM alive against SIGTERM (port of SIG_IGN)
                    }
                }
            });
            hook.setDaemon(false);
            Runtime.getRuntime().addShutdownHook(hook);
        }

        if ("bundled".equals(scenario)) {
            Map<String, Object> dump = new LinkedHashMap<>();
            dump.put("DSH_CORDIS_CONFIG", System.getenv().get("DSH_CORDIS_CONFIG"));
            Files.writeString(Path.of(System.getenv("ENV_DUMP")), json(dump));
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if ("seen-writes".equals(scenario)) {
                    java.nio.file.Files.writeString(
                            Path.of(System.getenv("SEEN")), line + "\n",
                            java.nio.file.StandardOpenOption.CREATE,
                            java.nio.file.StandardOpenOption.APPEND);
                }
                Map<String, Object> message;
                try {
                    message = MAPPER.readValue(line, new TypeReference<Map<String, Object>>() {
                    });
                } catch (Exception ignored) {
                    continue;
                }
                Object id = message.get("id");
                Object method = message.get("method");
                if (method instanceof String methodName) {
                    handleRequest(scenario, id, methodName, message);
                } else if (id != null) {
                    handleResponse(scenario, message);
                }
            }
        }
    }

    private static void handleRequest(String scenario, Object id, String method,
                                      Map<String, Object> message) throws Exception {
        PrintStream out = System.out;
        Map<String, Object> params = paramsOf(message);
        switch (method) {
            case "initialize" -> {
                switch (scenario) {
                    case "main-turn" -> {
                        Files.writeString(Path.of(System.getenv("ENV_DUMP")), json(envDump()));
                        Files.writeString(Path.of(System.getenv("INIT_DUMP")), json(params));
                    }
                    case "capture-cwd" -> {
                        Map<String, Object> capture = new LinkedHashMap<>();
                        capture.put("process", System.getProperty("user.dir"));
                        capture.put("environment", System.getenv("DSH_CWD"));
                        capture.put("wire", params.get("cwd"));
                        Files.writeString(Path.of(System.getenv("CAPTURE")), json(capture));
                    }
                    case "initialize-error" -> {
                        respondError(id, -32000, "bad initialize");
                        return;
                    }
                    default -> {
                        // plain initialize response
                    }
                }
                respond(id, Map.of("serverInfo", Map.of("name", fakeName(scenario))));
                if ("bridge-llm".equals(scenario)) {
                    emit("{\"jsonrpc\":\"2.0\",\"id\":\"bridge-req-1\",\"method\":\"llm.request\","
                            + "\"params\":{\"requestId\":\"req-1\",\"sessionId\":\"main\","
                            + "\"model\":\"dsagent\",\"messages\":[]}}");
                }
            }
            case "session/prompt" -> {
                switch (scenario) {
                    case "main-turn" -> {
                        emit(inbox(params));
                        emit(status(params, "running"));
                        respond(id, Map.of("messageId", "message-1"));
                        emit(assistantMessage(params, Map.of("role", "assistant", "content",
                                List.of(Map.of("type", "text", "text", "hello from runtime")))));
                        emit(turnEnd(params, 1, "completed"));
                        emit(turnEnd(params, 2, "max-tokens"));
                        emit(status(params, "idle"));
                    }
                    case "callback-subagent" -> {
                        emit(inbox(params));
                        emit(status(params, "running"));
                        respond(id, Map.of("messageId", "message-1"));
                        emit(subagent("subagent.started", "main", "child", null));
                        emit(status(params, "idle"));
                    }
                    case "malformed-turn-end" -> {
                        emit(inbox(params));
                        respond(id, Map.of("messageId", "message-1"));
                        emit(turnEnd(params, 1, null));
                        emit(status(params, "idle"));
                    }
                    case "subagent-finished" -> {
                        emit(inbox(params));
                        emit(status(params, "running"));
                        respond(id, Map.of("messageId", "message-1"));
                        emit(subagent("subagent.started", "main", "child", null));
                        emit(subagent("subagent.finished", "main", "child", "completed"));
                        emit(status(params, "idle"));
                    }
                    case "nested-tree" -> {
                        String root = String.valueOf(params.get("sessionId"));
                        emit(inbox(params));
                        emit(status(params, "running"));
                        respond(id, Map.of("messageId", "message-1"));
                        emit(subagent("subagent.started", root, "child", null));
                        emit(sessionEvent("child", Map.of("type", "assistant/message",
                                "data", Map.of("content",
                                        List.of(Map.of("type", "text", "text", "child response"))))));
                        emit(subagent("subagent.started", "child", "grandchild", null));
                        emit(sessionEvent("grandchild", Map.of("type", "assistant/message",
                                "data", Map.of("content",
                                        List.of(Map.of("type", "text", "text", "grandchild response"))))));
                        emit(subagent("subagent.finished", "child", "grandchild", null));
                        emit(subagent("subagent.finished", root, "child", null));
                        emit(sessionEvent(root, Map.of("type", "assistant/message",
                                "data", Map.of("content",
                                        List.of(Map.of("type", "text", "text", "root response"))))));
                        emit(status(params, "idle"));
                    }
                    case "other-session" -> {
                        emit(assistantMessage(Map.of("sessionId", "other"), Map.of("content",
                                List.of(Map.of("type", "text", "text", "wrong session")))));
                        emit(status(Map.of("sessionId", "other"), "idle"));
                        emit(inbox(params));
                        emit(status(params, "running"));
                        respond(id, Map.of("messageId", "message-1"));
                        emit(assistantMessage(params, Map.of("content",
                                List.of(Map.of("type", "text", "text", "right session")))));
                        emit(status(params, "idle"));
                    }
                    case "minimal-turn" -> {
                        emit(inbox(params));
                        emit(status(params, "running"));
                        respond(id, Map.of("messageId", "message-1"));
                        emit(assistantMessage(params, Map.of("content",
                                List.of(Map.of("type", "text", "text", "ok")))));
                        emit(status(params, "idle"));
                    }
                    case "late-idle" -> {
                        int turn = nextTurn();
                        String messageId = "message-" + turn;
                        emit(inbox(params, messageId));
                        emit(status(params, "running"));
                        respond(id, Map.of("messageId", messageId));
                        if (turn == 1) {
                            emit(assistantMessage(params, Map.of("content",
                                    List.of(Map.of("type", "text", "text", "first")))));
                            emit(status(params, "idle"));
                        } else {
                            Thread.sleep(50);
                            emit(assistantMessage(params, Map.of("content",
                                    List.of(Map.of("type", "text", "text", "second")))));
                            emit(status(params, "idle"));
                        }
                    }
                    case "bridge-llm" -> {
                        emit(notification("llm/request", Map.of(
                                "requestId", "req-1",
                                "sessionId", params.get("sessionId"),
                                "model", "dsagent",
                                "messages", List.of())));
                        respond(id, Map.of("messageId", "message-1"));
                    }
                    case "tick" -> {
                        respond(id, Map.of("messageId", "message-1"));
                    }
                    case "reject-prompt" -> {
                        respond(id, Map.of("accepted", false));
                    }
                    default -> {
                        respond(id, Map.of("messageId", "message-1"));
                    }
                }
            }
            case "emit-first", "emit-second" -> {
                emit(notification("tick", Map.of("source", method)));
            }
            case "shutdown" -> {
                if ("ignore-sigterm".equals(scenario)) {
                    Thread.sleep(60_000);
                    return;
                }
                respond(id, Map.of());
                return;
            }
            default -> {
                // ignore unknown methods
            }
        }
    }

    private static void handleResponse(String scenario, Map<String, Object> message) {
        if ("bridge-llm".equals(scenario)) {
            emit(notification("response/seen", Map.of("result", message.get("result"))));
        }
    }

    private static int nextTurn() {
        int turn = ++TURN_COUNTER;
        return turn;
    }

    private static int TURN_COUNTER = 0;

    private static Map<String, Object> envDump() {
        Map<String, Object> dump = new LinkedHashMap<>();
        dump.put("DEEPSEEK_API_KEY", System.getenv("DEEPSEEK_API_KEY"));
        dump.put("DEEPSEEK_BASE_URL", System.getenv("DEEPSEEK_BASE_URL"));
        dump.put("DSH_CWD", System.getenv("DSH_CWD"));
        dump.put("DSH_SESSION_ROOT", System.getenv("DSH_SESSION_ROOT"));
        dump.put("DSH_CORDIS_CONFIG", System.getenv("DSH_CORDIS_CONFIG"));
        return dump;
    }

    private static String fakeName(String scenario) {
        return "bundled".equals(scenario) ? "bundled-runtime" : "fake-runtime";
    }

    private static Map<String, Object> paramsOf(Map<String, Object> message) {
        Object params = message.get("params");
        if (params instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) params;
            return map;
        }
        return new LinkedHashMap<>();
    }

    private static Map<String, Object> inbox(Map<String, Object> params) {
        return inbox(params, "message-1");
    }

    private static Map<String, Object> inbox(Map<String, Object> params, String messageId) {
        return sessionEvent(String.valueOf(params.get("sessionId")), Map.of(
                "type", "agent/inbox/spliced",
                "data", Map.of("target", "next-turn", "start", 0,
                        "inserted", List.of(Map.of("id", messageId)))));
    }

    private static Map<String, Object> status(Map<String, Object> params, String status) {
        return notification("session.status", Map.of(
                "sessionId", params.get("sessionId"),
                "status", status));
    }

    private static Map<String, Object> assistantMessage(Map<String, Object> params,
                                                       Map<String, Object> data) {
        return sessionEvent(String.valueOf(params.get("sessionId")),
                Map.of("type", "assistant/message", "data", data));
    }

    private static Map<String, Object> turnEnd(Map<String, Object> params, int turn, String kind) {
        Map<String, Object> reason = kind == null ? Map.of() : Map.of("kind", kind);
        return sessionEvent(String.valueOf(params.get("sessionId")),
                Map.of("type", "turn/end", "data", Map.of("turn", turn, "reason", reason)));
    }

    private static Map<String, Object> subagent(String method, String parent, String child,
                                                String stopReason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("parentSessionId", parent);
        payload.put("childSessionId", child);
        if (stopReason != null) {
            payload.put("status", "ok");
            payload.put("stopReason", stopReason);
        }
        return notification(method, payload);
    }

    private static Map<String, Object> sessionEvent(String sessionId, Object event) {
        return notification("session.event", Map.of("sessionId", sessionId, "event", event));
    }

    private static Map<String, Object> notification(String method, Map<String, Object> params) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("jsonrpc", "2.0");
        message.put("method", method);
        message.put("params", params);
        return message;
    }

    private static void respond(Object id, Object result) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("jsonrpc", "2.0");
        message.put("id", id);
        message.put("result", result);
        emit(json(message));
    }

    private static void respondError(Object id, int code, String messageText) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", messageText);
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("jsonrpc", "2.0");
        message.put("id", id);
        message.put("error", error);
        emit(json(message));
    }

    private static void emit(Map<String, Object> message) {
        emit(json(message));
    }

    private static void emit(String line) {
        System.out.println(line);
        System.out.flush();
    }

    private static String json(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception exc) {
            throw new IllegalStateException("could not serialize fake runtime message", exc);
        }
    }
}
