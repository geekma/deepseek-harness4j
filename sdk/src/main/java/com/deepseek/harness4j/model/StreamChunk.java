package com.deepseek.harness4j.model;

import java.util.Map;

/**
 * A streamed event chunk emitted during real-time turn execution.
 *
 * @param type         the logical chunk type (e.g. {@code "reasoning"}, {@code "content"},
 *                     {@code "tool_call"}, {@code "tool_result"}, {@code "turn_end"},
 *                     {@code "status"})
 * @param text         incremental text or formatted content for this chunk (may be empty)
 * @param rawEvent     the underlying event map if this chunk came from a {@code session.event},
 *                     or {@code null}
 * @param notification the raw notification that triggered this chunk
 */
public record StreamChunk(
        String type,
        String text,
        Map<String, Object> rawEvent,
        Notification notification) {

    public static final String TYPE_REASONING = "reasoning";
    public static final String TYPE_CONTENT = "content";
    public static final String TYPE_TOOL_CALL = "tool_call";
    public static final String TYPE_TOOL_RESULT = "tool_result";
    public static final String TYPE_TURN_END = "turn_end";
    public static final String TYPE_STATUS = "status";
    public static final String TYPE_RAW = "raw";

    public boolean isReasoning() {
        return TYPE_REASONING.equals(type);
    }

    public boolean isContent() {
        return TYPE_CONTENT.equals(type);
    }

    public boolean isToolCall() {
        return TYPE_TOOL_CALL.equals(type);
    }

    public boolean isToolResult() {
        return TYPE_TOOL_RESULT.equals(type);
    }

    public boolean isTurnEnd() {
        return TYPE_TURN_END.equals(type);
    }
}
