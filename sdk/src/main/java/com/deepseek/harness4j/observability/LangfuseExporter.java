package com.deepseek.harness4j.observability;

import com.deepseek.harness4j.RunResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exporter converting {@link RunResult} trajectory and token usage into Langfuse-compliant trace payloads.
 */
public final class LangfuseExporter {

    private LangfuseExporter() {
    }

    /**
     * Convert a {@link RunResult} to a Langfuse Trace Map structure.
     */
    public static Map<String, Object> exportTrace(RunResult result, String name) {
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("id", result.sessionId());
        trace.put("name", name != null ? name : "deepseek-turn");
        trace.put("sessionId", result.sessionId());
        trace.put("output", result.finalResponse());

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("finishReason", result.finishReason());
        metadata.put("sessionRoot", result.sessionRoot());
        metadata.put("eventCount", result.events().size());
        metadata.put("notificationCount", result.notifications().size());
        trace.put("metadata", metadata);

        // Add generations and spans
        List<Map<String, Object>> generations = new ArrayList<>();
        Map<String, Object> generation = new LinkedHashMap<>();
        generation.put("name", "generation");
        generation.put("model", "deepseek");
        generation.put("output", result.finalResponse());

        String reasoning = result.reasoningContent();
        if (reasoning != null && !reasoning.isEmpty()) {
            generation.put("reasoningContent", reasoning);
        }

        RunResult.TokenUsage usage = result.tokenUsage();
        Map<String, Object> usageMap = new LinkedHashMap<>();
        usageMap.put("promptTokens", usage.promptTokens());
        usageMap.put("completionTokens", usage.completionTokens());
        usageMap.put("reasoningTokens", usage.reasoningTokens());
        usageMap.put("cacheReadTokens", usage.cacheReadTokens());
        usageMap.put("cacheWriteTokens", usage.cacheWriteTokens());
        usageMap.put("totalTokens", usage.totalTokens());
        generation.put("usage", usageMap);
        generations.add(generation);

        // Add tool call spans
        List<Map<String, Object>> toolSpans = new ArrayList<>();
        for (RunResult.ToolCallRecord tool : result.toolCalls()) {
            Map<String, Object> span = new LinkedHashMap<>();
            span.put("id", tool.callId());
            span.put("name", tool.toolName());
            span.put("input", tool.arguments());
            span.put("output", tool.result());
            span.put("isError", tool.isError());
            span.put("durationMs", tool.durationMs());
            toolSpans.add(span);
        }

        trace.put("generations", generations);
        trace.put("toolSpans", toolSpans);
        return trace;
    }
}
