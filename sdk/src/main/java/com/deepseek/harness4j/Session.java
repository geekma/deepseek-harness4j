package com.deepseek.harness4j;

import com.deepseek.harness4j.client.NotificationSubscription;
import com.deepseek.harness4j.model.JsonValues;
import com.deepseek.harness4j.model.Notification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * One runnable session against a {@link DeepSeekHarness} instance.
 *
 * <p>Line-by-line Java port of the Python {@code Session} class in {@code api.py}.
 *
 * @param harness the owning high-level harness
 * @param id      the durable session id
 */
public record Session(DeepSeekHarness harness, String id) {

    /**
     * Run one turn interval: from the prompt's durable inbox receipt through the next
     * whole-agent idle.
     *
     * @param input          a plain text prompt, or a list of content blocks
     * @param onNotification optional callback invoked per received notification
     * @return the run result; {@code final_response} is the last committed root-session
     *         assistant text, {@code finish_reason} the kind of the last root-session
     *         {@code turn/end} (e.g. {@code completed}, {@code max-tokens}, {@code error}),
     *         or {@code null} when no turn ended
     */
    public RunResult run(Object input, Consumer<Notification> onNotification) {
        List<Map<String, Object>> contentBlocks = SessionSupport.normalizeInput(input);
        List<Notification> collectedNotifications = new ArrayList<>();
        List<Map<String, Object>> events = new ArrayList<>();

        Consumer<Notification> collect = notification -> {
            collectedNotifications.add(notification);
            if (onNotification != null) {
                onNotification.accept(notification);
            }
            if ("session.event".equals(notification.method())
                    && id.equals(notification.payload().get("sessionId"))) {
                Map<String, Object> event = JsonValues.asObject(notification.payload().get("event"));
                if (event != null) {
                    events.add(event);
                }
            }
        };

        try (NotificationSubscription subscription = harness.client().subscribeSessionNotifications(id)) {
            String messageId = harness.client().sessionPrompt(id, contentBlocks, null, subscription);

            boolean received = false;
            while (true) {
                Notification notification = subscription.next();
                if (!received) {
                    if (!SessionSupport.isInboxReceipt(notification, id, messageId)) {
                        continue;
                    }
                    received = true;
                }
                collect.accept(notification);
                if ("session.status".equals(notification.method())
                        && id.equals(notification.payload().get("sessionId"))
                        && "idle".equals(notification.payload().get("status"))) {
                    break;
                }
            }
        }

        return new RunResult(
                id,
                SessionSupport.finalResponse(events),
                SessionSupport.finishReason(events),
                events,
                collectedNotifications,
                harness.config().sessionRoot());
    }
}
