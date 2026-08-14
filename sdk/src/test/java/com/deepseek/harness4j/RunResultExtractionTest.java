package com.deepseek.harness4j;

import com.deepseek.harness4j.test.FakeRuntime;
import com.deepseek.harness4j.test.TestRuntimes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0 enhanced {@code RunResult} model: CoT reasoning extraction, token accounting, and
 * structured tool-call records. Exercises the extractors against both the live wire stream
 * (via {@link FakeRuntime}) and synthetic event fixtures.
 */
class RunResultExtractionTest {

    @TempDir
    Path tmp;

    // -----------------------------------------------------------------------------------
    // reasoningContent
    // -----------------------------------------------------------------------------------

    @Test
    void test_reasoning_content_extracted_from_reasoning_delta_chunks() {
        List<Map<String, Object>> events = List.of(
                chunk("reasoning-delta", "Let me think"),
                chunk("text-delta", "answer"),
                chunk("reasoning-delta", " about the plan"),
                chunk("reasoning-delta", "."));

        RunResult.TokenUsage usage = RunResult.TokenUsage.EMPTY;
        String reasoning = reasoningOf(events);
        assertEquals("Let me think about the plan.", reasoning);
    }

    @Test
    void test_reasoning_content_handles_packed_reasoning_chunks_row() {
        List<Map<String, Object>> events = List.of(
                Map.of("type", "reasoning-chunks",
                        "data", Map.of("texts", List.of("packed ", "reasoning"))));

        assertEquals("packed reasoning", reasoningOf(events));
    }

    @Test
    void test_reasoning_content_empty_when_no_reasoning() {
        assertEquals("", reasoningOf(List.of(
                Map.of("type", "assistant/chunk",
                        "data", Map.of("chunk", Map.of("type", "text-delta", "text", "hi"))))));
    }

    // -----------------------------------------------------------------------------------
    // tokenUsage
    // -----------------------------------------------------------------------------------

    @Test
    void test_token_usage_aggregates_assistant_messages() {
        List<Map<String, Object>> events = List.of(
                assistantWithUsage(Map.of("inputTokens", 10, "outputTokens", 5,
                        "reasoningTokens", 2)),
                assistantWithUsage(Map.of("inputTokens", 20, "outputTokens", 8,
                        "cacheReadTokens", 100)),
                // no usage field -> ignored, not zeroed
                Map.of("type", "assistant/message", "data", Map.of("message", Map.of())));

        RunResult.TokenUsage usage = tokenUsageOf(events);
        assertEquals(30, usage.promptTokens());
        assertEquals(13, usage.completionTokens());
        assertEquals(2, usage.reasoningTokens());
        assertEquals(100, usage.cacheReadTokens());
        assertEquals(0, usage.cacheWriteTokens());
        assertEquals(30 + 13 + 100, usage.totalTokens());
    }

    @Test
    void test_token_usage_empty_when_no_usage_reported() {
        RunResult.TokenUsage usage = tokenUsageOf(List.of(
                Map.of("type", "assistant/message", "data", Map.of("message", Map.of()))));
        assertEquals(RunResult.TokenUsage.EMPTY, usage);
    }

    // -----------------------------------------------------------------------------------
    // toolCalls
    // -----------------------------------------------------------------------------------

    @Test
    void test_tool_calls_pair_call_and_result_by_call_id() {
        List<Map<String, Object>> events = List.of(
                toolCall("call-1", "bash", "{\"command\":\"ls\"}", 1_700_000_000_000L),
                toolCall("call-2", "str-replace-editor", "{\"old\":\"a\"}", 1_700_000_000_100L),
                toolResult("call-1", "file list", null, 1_700_000_000_050L),
                toolResult("call-2", "done", null, 1_700_000_000_120L));

        List<RunResult.ToolCallRecord> calls = toolCallsOf(events);
        assertEquals(2, calls.size());

        RunResult.ToolCallRecord first = calls.get(0);
        assertEquals("call-1", first.callId());
        assertEquals("bash", first.toolName());
        assertEquals("{\"command\":\"ls\"}", first.argumentsJson());
        assertEquals("file list", first.result());
        assertFalse(first.isError());
        assertEquals(50L, first.durationMs());

        RunResult.ToolCallRecord second = calls.get(1);
        assertEquals(20L, second.durationMs());
        assertFalse(second.isError());
    }

    @Test
    void test_tool_calls_flag_error_results_and_unmatched_calls() {
        List<Map<String, Object>> events = List.of(
                toolCall("call-err", "bash", "{}", 1_700_000_000_000L),
                toolCall("call-orphan", "bash", "{}", 1_700_000_000_200L),
                toolResult("call-err", "boom", Map.of("name", "CommandFailed", "code", "E1"),
                        1_700_000_000_050L));

        List<RunResult.ToolCallRecord> calls = toolCallsOf(events);
        assertEquals(2, calls.size());
        assertTrue(calls.get(0).isError());
        assertEquals("boom", calls.get(0).result());
        assertEquals(50L, calls.get(0).durationMs());
        assertFalse(calls.get(1).isError());
        assertEquals(null, calls.get(1).result());
        assertEquals(0L, calls.get(1).durationMs());
    }

    @Test
    void test_tool_calls_empty_when_no_tool_events() {
        assertTrue(toolCallsOf(List.of()).isEmpty());
    }

    // -----------------------------------------------------------------------------------
    // end-to-end via FakeRuntime: reasoning/token/tool events travel the live stream
    // -----------------------------------------------------------------------------------

    @Test
    void test_end_to_end_run_result_extractions_from_wire_stream() {
        // Reuse the plain minimal turn; verify the enhanced accessors degrade gracefully
        // (no reasoning/usage/tool events in this fixture).
        RunResult result;
        try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
                .launchArgsOverride(TestRuntimes.fakeRuntimeCommand())
                .cwd(tmp.toString())
                .env(Map.of("FR_SCENARIO", "minimal-turn"))
                .build())) {
            result = harness.run("one turn", "main", null);
        }

        assertEquals("", result.reasoningContent());
        assertEquals(RunResult.TokenUsage.EMPTY, result.tokenUsage());
        assertTrue(result.toolCalls().isEmpty());
        assertEquals("ok", result.finalResponse());
    }

    // -----------------------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------------------

    private static Map<String, Object> chunk(String deltaType, String text) {
        return Map.of("type", "assistant/chunk",
                "data", Map.of("chunk", Map.of("type", deltaType, "text", text)));
    }

    private static Map<String, Object> assistantWithUsage(Map<String, Object> usage) {
        return Map.of("type", "assistant/message",
                "data", Map.of("message", Map.of("content", List.of()), "usage", usage));
    }

    private static Map<String, Object> toolCall(String callId, String name, String args,
                                                long time) {
        return Map.of("type", "tool/call", "time", time,
                "data", Map.of("callId", callId, "name", name, "arguments", args));
    }

    private static Map<String, Object> toolResult(String callId, String message,
                                                  Map<String, Object> error, long time) {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("callId", callId);
        data.put("message", message);
        if (error != null) {
            data.put("error", error);
        }
        return Map.of("type", "tool/result", "time", time, "data", data);
    }

    private static String reasoningOf(List<Map<String, Object>> events) {
        return SessionSupport.extractReasoningContent(events);
    }

    private static RunResult.TokenUsage tokenUsageOf(List<Map<String, Object>> events) {
        return SessionSupport.extractTokenUsage(events);
    }

    private static List<RunResult.ToolCallRecord> toolCallsOf(List<Map<String, Object>> events) {
        return SessionSupport.extractToolCalls(events);
    }
}