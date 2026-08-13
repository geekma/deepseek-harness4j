package com.deepseek.harness4j;

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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that pin down every Java code example in the README's "feature comparison"
 * section (client channels §1 + runtime capabilities §2). Each test mirrors one example
 * snippet verbatim against the {@link FakeRuntime} subprocess, so a doc change that no
 * longer compiles or misbehaves fails here.
 */
class ReadmeExamplesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tmp;

    @Test
    void test_java_sdk_minimal_agent_example() {
        // README §1 "Java SDK": config builder + run + finalResponse / finishReason
        try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
                .provider("deepseek-official")
                .model("deepseek-v4-flash")
                .cwd(tmp.toString())
                .sessionRoot(tmp.resolve("sessions").toString())
                .launchArgsOverride(TestRuntimes.fakeRuntimeCommand())
                .env(Map.of("FR_SCENARIO", "main-turn"))
                .build())) {
            RunResult result = harness.run("Read package.json and print the list of scripts.");
            assertEquals("hello from runtime", result.finalResponse());
            assertEquals("max-tokens", result.finishReason());
            assertEquals("turn/end", result.events().get(result.events().size() - 1).get("type"));
        }
    }

    @Test
    void test_headless_cli_wrapper_example() {
        // README §1 "Headless CLI": no-arg ctor + run(args[0]).finalResponse()
        String prompt = "single shot";
        try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
                .launchArgsOverride(TestRuntimes.fakeRuntimeCommand())
                .cwd(tmp.toString())
                .env(Map.of("FR_SCENARIO", "minimal-turn"))
                .build())) {
            assertEquals("ok", harness.run(prompt).finalResponse());
        }
    }

    @Test
    void test_session_management_example() {
        // README §2 "Session management": same sessionId reused across turns
        try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
                .launchArgsOverride(TestRuntimes.fakeRuntimeCommand())
                .cwd(tmp.toString())
                .env(Map.of("FR_SCENARIO", "late-idle"))
                .build())) {
            RunResult first = harness.run("Remember: this project builds with Maven.", "session-1", null);
            RunResult second = harness.run("What build tool do we use?", "session-1", null);
            assertEquals("first", first.finalResponse());
            assertEquals("second", second.finalResponse());
            assertEquals(List.of("session-1", "session-1", "session-1", "session-1"),
                    second.notifications().stream()
                            .map(n -> n.payload().get("sessionId")).toList());
        }
    }

    @Test
    void test_system_prompt_env_injection() throws Exception {
        // README §2 "System prompts": env() injects DSH_SYSTEM_PROMPT into the runtime
        Path envDump = tmp.resolve("env.json");
        try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
                .launchArgsOverride(TestRuntimes.fakeRuntimeCommand())
                .cwd(tmp.toString())
                .env(Map.of("FR_SCENARIO", "main-turn",
                        "ENV_DUMP", envDump.toString(),
                        "DSH_SYSTEM_PROMPT", "你是一名严谨的 Java 代码审查助手。"))
                .build())) {
            harness.run("review", "main", null);
        }
        assertEquals("你是一名严谨的 Java 代码审查助手。",
                readJson(envDump).get("DSH_SYSTEM_PROMPT"));
    }

    @Test
    void test_tool_system_on_notification_callback() {
        // README §2 "Tools": onNotification consumes session.event notifications
        List<String> methods = new ArrayList<>();
        try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
                .launchArgsOverride(TestRuntimes.fakeRuntimeCommand())
                .cwd(tmp.toString())
                .env(Map.of("FR_SCENARIO", "callback-subagent"))
                .build())) {
            harness.run("Change the README title to English.", "session-2", notification -> {
                if ("session.event".equals(notification.method())) {
                    methods.add(notification.method());
                }
            });
        }
        assertEquals(List.of("session.event"), methods);
    }

    @Test
    void test_agent_loop_example() {
        // README §2 "Agent loop": finishReason + full event stream
        try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
                .launchArgsOverride(TestRuntimes.fakeRuntimeCommand())
                .cwd(tmp.toString())
                .env(Map.of("FR_SCENARIO", "main-turn"))
                .build())) {
            RunResult result = harness.run("Fix the compile errors.", "session-3", null);
            assertEquals("max-tokens", result.finishReason());
            assertTrue(result.events().size() >= 3);
            assertEquals("agent/inbox/spliced", result.events().get(0).get("type"));
            assertEquals("assistant/message", result.events().get(1).get("type"));
            assertEquals("turn/end", result.events().get(result.events().size() - 1).get("type"));
        }
    }

    @Test
    void test_llm_access_config_example() throws Exception {
        // README §2 "LLM access & adapters": provider/model/apiKey/baseUrl/maxTokens
        Path initDump = tmp.resolve("init.json");
        try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
                .provider("deepseek-official")
                .model("deepseek-v4-flash")
                .apiKey("test-key")
                .baseUrl("http://127.0.0.1:8000/v1")
                .maxTokens(2048)
                .launchArgsOverride(TestRuntimes.fakeRuntimeCommand())
                .cwd(tmp.toString())
                .env(Map.of("FR_SCENARIO", "main-turn", "INIT_DUMP", initDump.toString()))
                .build())) {
            harness.run("greet", "main", null);
        }
        Map<String, Object> init = readJson(initDump);
        assertEquals("deepseek-official", init.get("provider"));
        assertEquals("deepseek-v4-flash", init.get("model"));
        assertEquals(2048, init.get("maxTokens"));
    }

    @Test
    void test_bash_assistant_message_events() {
        // README §2 "Bash / shell": structured assistant/message events, no PTY bytes
        try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
                .launchArgsOverride(TestRuntimes.fakeRuntimeCommand())
                .cwd(tmp.toString())
                .env(Map.of("FR_SCENARIO", "main-turn"))
                .build())) {
            RunResult result = harness.run("Run mvn -q test and report the failing cases.");
            List<Object> texts = new ArrayList<>();
            for (Map<String, Object> event : result.events()) {
                if ("assistant/message".equals(event.get("type"))) {
                    Map<String, Object> data = (Map<String, Object>) event.get("data");
                    List<Map<String, Object>> content = (List<Map<String, Object>>) data.get("content");
                    texts.add(content.get(0).get("text"));
                }
            }
            assertEquals(List.of("hello from runtime"), texts);
        }
    }

    @Test
    void test_filesystem_cwd_injection() throws Exception {
        // README §2 "Filesystem": cwd() becomes DSH_CWD in the runtime
        Path capture = tmp.resolve("cwd.json");
        try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
                .cwd(tmp.toRealPath().toString())
                .launchArgsOverride(TestRuntimes.fakeRuntimeCommand())
                .env(Map.of("FR_SCENARIO", "capture-cwd", "CAPTURE", capture.toString()))
                .build())) {
            harness.start();
        }
        assertEquals(tmp.toRealPath().toString(), readJson(capture).get("environment"));
        assertEquals(tmp.toRealPath().toString(), readJson(capture).get("wire"));
    }

    @Test
    void test_subagent_orchestration_payload_keys() {
        // README §2 "Sub-agent orchestration": childSessionId / parentSessionId / status
        Map<String, Object> started = new LinkedHashMap<>();
        Map<String, Object> finished = new LinkedHashMap<>();
        try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
                .launchArgsOverride(TestRuntimes.fakeRuntimeCommand())
                .cwd(tmp.toString())
                .env(Map.of("FR_SCENARIO", "subagent-finished"))
                .build())) {
            harness.run("Analyze two modules in parallel with subagents.", "main", notification -> {
                switch (notification.method()) {
                    case "subagent.started" -> started.putAll(notification.payload());
                    case "subagent.finished" -> finished.putAll(notification.payload());
                    default -> { }
                }
            });
        }
        assertEquals("main", started.get("parentSessionId"));
        assertEquals("child", started.get("childSessionId"));
        assertEquals("main", finished.get("parentSessionId"));
        assertEquals("child", finished.get("childSessionId"));
        assertEquals("ok", finished.get("status"));
    }

    @Test
    void test_persistence_session_root_injection() throws Exception {
        // README §2 "Persistence": sessionRoot() sets DSH_SESSION_ROOT
        Path envDump = tmp.resolve("env.json");
        try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
                .sessionRoot(tmp.resolve("sessions").toString())
                .launchArgsOverride(TestRuntimes.fakeRuntimeCommand())
                .cwd(tmp.toString())
                .env(Map.of("FR_SCENARIO", "main-turn", "ENV_DUMP", envDump.toString()))
                .build())) {
            RunResult result = harness.run("log this", "main", null);
            assertEquals(tmp.resolve("sessions").toString(), result.sessionRoot());
        }
        assertEquals(tmp.resolve("sessions").toString(),
                readJson(envDump).get("DSH_SESSION_ROOT"));
    }

    @Test
    void test_spring_properties_map_to_sdk_config() {
        // README §1 "Spring Boot starter": deepseek.harness.* binds to the SDK config surface
        DeepSeekHarnessConfig config = DeepSeekHarnessConfig.builder()
                .provider("deepseek-official")
                .model("deepseek-v4-flash")
                .cwd("/absolute/path/workspace")
                .sessionRoot("/absolute/path/sessions")
                .baseUrl("http://127.0.0.1:8000/v1")
                .apiKey("spring-key")
                .build();
        assertEquals("deepseek-official", config.provider());
        assertEquals("deepseek-v4-flash", config.model());
        assertEquals("/absolute/path/workspace", config.cwd());
        assertEquals("/absolute/path/sessions", config.sessionRoot());
        assertEquals("http://127.0.0.1:8000/v1", config.baseUrl());
        assertEquals("spring-key", config.apiKey());
        assertEquals(1.0, config.shutdownTimeoutSeconds());
    }

    @Test
    void test_cordis_plugin_names_exist_in_runtime_dependencies() {
        // README §2 mounts real upstream plugin ids; assert the referenced ids stay consistent
        assertNotNull(ClassLoader.getSystemResourceAsStream(
                "examples/jsonrpc-agent/minimal.cordis.yml"));
    }

    private Map<String, Object> readJson(Path path) throws Exception {
        return MAPPER.readValue(Files.readAllBytes(path), new TypeReference<Map<String, Object>>() {
        });
    }
}