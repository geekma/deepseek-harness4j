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
}
