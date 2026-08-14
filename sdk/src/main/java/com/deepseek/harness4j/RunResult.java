package com.deepseek.harness4j;

import com.deepseek.harness4j.model.Notification;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Result of one {@link Session#run(String, java.util.function.Consumer)} interval.
 *
 * <p>Python dataclass:
 * <pre>{@code
 * @dataclass(slots=True)
 * class RunResult:
 *     session_id: str
 *     final_response: str
 *     finish_reason: str | None
 *     events: list[JsonObject]
 *     notifications: list[Notification]
 *     session_root: str | None = None
 * }</pre>
 *
 * @param sessionId     the root session id of the run interval
 * @param finalResponse the last committed root-session assistant text in the interval
 * @param finishReason  the {@code kind} of the last root-session {@code turn/end} in the
 *                      interval, or {@code null} when no turn ended
 * @param events        root-session events only, in wire order
 * @param notifications root session and all known descendant notifications in wire order
 * @param sessionRoot   the configured session persistence root, or {@code null}
 */
public record RunResult(
        String sessionId,
        String finalResponse,
        String finishReason,
        List<Map<String, Object>> events,
        List<Notification> notifications,
        String sessionRoot) {

    public RunResult {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(finalResponse, "finalResponse");
        Objects.requireNonNull(events, "events");
        Objects.requireNonNull(notifications, "notifications");
    }

    /**
     * The full CoT reasoning chain: every {@code reasoning-delta} text in this interval,
     * concatenated in wire order. Empty string when the model produced no reasoning.
     */
    public String reasoningContent() {
        return SessionSupport.extractReasoningContent(events);
    }

    /**
     * Aggregated token accounting across every {@code assistant/message} that carried a
     * {@code usage} object, summed over all turns in the interval.
     */
    public TokenUsage tokenUsage() {
        return SessionSupport.extractTokenUsage(events);
    }

    /**
     * Structured tool-call tree: each {@code tool/call} paired with its matching
     * {@code tool/result} by {@code callId}, in wire order.
     */
    public List<ToolCallRecord> toolCalls() {
        return SessionSupport.extractToolCalls(events);
    }

    /**
     * Token accounting for one interval, mapping the upstream TokenUsage vocabulary
     * (input/output/cacheRead/cacheWrite/reasoning) to the conventional prompt/completion
     * split used by LLM SDKs.
     */
    public record TokenUsage(
            int promptTokens,
            int completionTokens,
            int reasoningTokens,
            int cacheReadTokens,
            int cacheWriteTokens,
            int totalTokens) {

        public static final TokenUsage EMPTY = new TokenUsage(0, 0, 0, 0, 0, 0);
    }

    /**
     * One paired tool invocation: the {@code tool/call} request and the {@code tool/result}
     * outcome, matched by {@code callId}. Arguments are the raw JSON string exactly as the
     * model produced it; {@code isError} is true when the result carried an {@code error}
     * object.
     */
    public record ToolCallRecord(
            String callId,
            String toolName,
            String argumentsJson,
            Object result,
            boolean isError,
            long durationMs) {

        /** Alias for argumentsJson. */
        public String arguments() {
            return argumentsJson;
        }
    }
}
