package com.deepseek.harness4j.observability;

import com.deepseek.harness4j.RunResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObservabilityTest {

    @Test
    void test_langfuse_and_otel_trace_export() {
        List<Map<String, Object>> events = List.of(
                Map.of("type", "assistant/chunk", "data", Map.of("chunk", Map.of("type", "reasoning-delta", "text", "CoT step 1"))),
                Map.of("type", "tool/call", "time", 1000L, "data", Map.of("callId", "c1", "name", "bash", "arguments", "ls")),
                Map.of("type", "tool/result", "time", 1250L, "data", Map.of("callId", "c1", "message", "file.txt")),
                Map.of("type", "assistant/message", "data", Map.of(
                        "content", List.of(Map.of("type", "text", "text", "Done")),
                        "usage", Map.of("inputTokens", 100, "outputTokens", 50, "reasoningTokens", 30, "cacheReadTokens", 10, "cacheWriteTokens", 0))),
                Map.of("type", "turn/end", "data", Map.of("reason", Map.of("kind", "completed")))
        );

        RunResult result = new RunResult("sess-123", "Done", "completed", events, List.of(), "/tmp/sessions");

        // Langfuse trace export
        Map<String, Object> langfuse = LangfuseExporter.exportTrace(result, "test-trace");
        assertNotNull(langfuse);
        assertEquals("sess-123", langfuse.get("sessionId"));
        assertEquals("Done", langfuse.get("output"));
        assertTrue(((List<?>) langfuse.get("generations")).size() > 0);
        assertTrue(((List<?>) langfuse.get("toolSpans")).size() > 0);

        // OpenTelemetry GenAI Span export
        Map<String, Object> otel = OtelTraceExporter.exportGenAiSpan(result, "deepseek-reasoner");
        assertNotNull(otel);
        assertEquals("gen_ai.chat", otel.get("span.name"));

        @SuppressWarnings("unchecked")
        Map<String, Object> attrs = (Map<String, Object>) otel.get("attributes");
        assertEquals("deepseek", attrs.get("gen_ai.system"));
        assertEquals("deepseek-reasoner", attrs.get("gen_ai.request.model"));
        assertEquals(100, attrs.get("gen_ai.usage.input_tokens"));
        assertEquals(50, attrs.get("gen_ai.usage.output_tokens"));
        assertEquals(30, attrs.get("gen_ai.usage.reasoning_tokens"));
        assertEquals("CoT step 1", attrs.get("gen_ai.reasoning"));
    }
}
