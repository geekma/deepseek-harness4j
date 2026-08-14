package com.deepseek.harness4j.observability;

import com.deepseek.harness4j.RunResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exporter converting {@link RunResult} into OpenTelemetry GenAI Semantic Convention attributes.
 *
 * <p>Conforms to OpenTelemetry standard conventions for generative AI systems:
 * {@code gen_ai.system}, {@code gen_ai.request.model}, {@code gen_ai.usage.input_tokens}, etc.
 */
public final class OtelTraceExporter {

    private OtelTraceExporter() {
    }

    /**
     * Build an OpenTelemetry GenAI span representation from a {@link RunResult}.
     */
    public static Map<String, Object> exportGenAiSpan(RunResult result, String model) {
        Map<String, Object> span = new LinkedHashMap<>();
        span.put("span.name", "gen_ai.chat");
        span.put("session.id", result.sessionId());

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("gen_ai.system", "deepseek");
        attributes.put("gen_ai.request.model", model != null ? model : "deepseek");
        attributes.put("gen_ai.response.finish_reasons", List.of(result.finishReason() != null ? result.finishReason() : "stop"));

        RunResult.TokenUsage usage = result.tokenUsage();
        attributes.put("gen_ai.usage.input_tokens", usage.promptTokens());
        attributes.put("gen_ai.usage.output_tokens", usage.completionTokens());
        attributes.put("gen_ai.usage.reasoning_tokens", usage.reasoningTokens());
        attributes.put("gen_ai.usage.total_tokens", usage.totalTokens());

        if (usage.cacheReadTokens() > 0) {
            attributes.put("gen_ai.usage.cache_read_tokens", usage.cacheReadTokens());
        }

        attributes.put("gen_ai.completion", result.finalResponse());
        String reasoning = result.reasoningContent();
        if (reasoning != null && !reasoning.isEmpty()) {
            attributes.put("gen_ai.reasoning", reasoning);
        }

        List<Map<String, Object>> toolCallAttrs = new ArrayList<>();
        for (RunResult.ToolCallRecord tool : result.toolCalls()) {
            Map<String, Object> tc = new LinkedHashMap<>();
            tc.put("tool.id", tool.callId());
            tc.put("tool.name", tool.toolName());
            tc.put("tool.arguments", tool.arguments());
            tc.put("tool.result", tool.result());
            tc.put("tool.is_error", tool.isError());
            tc.put("tool.duration_ms", tool.durationMs());
            toolCallAttrs.add(tc);
        }
        attributes.put("gen_ai.tool_calls", toolCallAttrs);

        span.put("attributes", attributes);
        return span;
    }
}
