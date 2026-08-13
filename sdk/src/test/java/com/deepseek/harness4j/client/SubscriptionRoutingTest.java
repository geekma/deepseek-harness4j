package com.deepseek.harness4j.client;

import com.deepseek.harness4j.model.Notification;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Port of the client-level subscription tests in {@code python/sdk/tests/test_client.py} that
 * feed messages directly into {@code HarnessClient._handle_message} without a subprocess.
 */
class SubscriptionRoutingTest {

    private static HarnessClient newClient() {
        return new HarnessClient();
    }

    @Test
    void test_client_keeps_unmatched_notifications_available_globally_while_subscribed() {
        HarnessClient client = newClient();
        try (NotificationSubscription ignored = client.subscribeSessionNotifications("main")) {
            client.handleMessage(Map.of(
                    "jsonrpc", "2.0",
                    "method", "session.event",
                    "params", Map.of("sessionId", "other",
                            "event", Map.of("type", "assistant/message"))));
            assertEquals(1, client.notificationsQueue().size());
            Notification notification = (Notification) client.notificationsQueue().poll();
            assertEquals("session.event", notification.method());
            assertEquals("other", notification.payload().get("sessionId"));
        }
    }

    @Test
    void test_session_subscription_keeps_descendant_relationships_across_subscriptions() {
        HarnessClient client = newClient();
        try (NotificationSubscription first = client.subscribeSessionNotifications("main")) {
            client.handleMessage(Map.of(
                    "jsonrpc", "2.0",
                    "method", "subagent.started",
                    "params", Map.of("parentSessionId", "main", "childSessionId", "child")));
            assertEquals("child", first.next().payload().get("childSessionId"));
        }

        try (NotificationSubscription second = client.subscribeSessionNotifications("main")) {
            client.handleMessage(Map.of(
                    "jsonrpc", "2.0",
                    "method", "subagent.started",
                    "params", Map.of("parentSessionId", "child", "childSessionId", "grandchild")));
            client.handleMessage(Map.of(
                    "jsonrpc", "2.0",
                    "method", "session.event",
                    "params", Map.of("sessionId", "grandchild",
                            "event", Map.of("type", "assistant/message"))));
            assertEquals("grandchild", second.next().payload().get("childSessionId"));
            assertEquals("grandchild", second.next().payload().get("sessionId"));
        }

        assertEquals(0, client.notificationsQueue().size());
    }

    @Test
    void test_session_subscription_preserves_reused_child_ancestry_after_late_finish() {
        HarnessClient client = newClient();
        List<String> oldSeen = new ArrayList<>();
        List<String> newSeen = new ArrayList<>();
        try (NotificationSubscription oldSubscription = client.subscribeSessionNotifications("old-parent");
             NotificationSubscription newSubscription = client.subscribeSessionNotifications("new-parent")) {
            client.handleMessage(Map.of(
                    "jsonrpc", "2.0",
                    "method", "subagent.started",
                    "params", Map.of("parentSessionId", "old-parent", "childSessionId", "reused-child")));
            oldSubscription.drain(n -> oldSeen.add(n.method()));
            newSubscription.drain(n -> newSeen.add(n.method()));
            assertEquals(List.of("subagent.started"), oldSeen);
            assertEquals(List.of(), newSeen);

            client.handleMessage(Map.of(
                    "jsonrpc", "2.0",
                    "method", "subagent.started",
                    "params", Map.of("parentSessionId", "new-parent", "childSessionId", "reused-child")));
            oldSubscription.drain(n -> oldSeen.add(n.method()));
            newSubscription.drain(n -> newSeen.add(n.method()));
            assertEquals(List.of("subagent.started"), newSeen);

            client.handleMessage(Map.of(
                    "jsonrpc", "2.0",
                    "method", "subagent.finished",
                    "params", Map.of("parentSessionId", "old-parent", "childSessionId", "reused-child")));
            oldSubscription.drain(n -> oldSeen.add(n.method()));
            newSubscription.drain(n -> newSeen.add(n.method()));
            assertEquals(List.of("subagent.started", "subagent.finished"), oldSeen);
            assertEquals(List.of("subagent.started"), newSeen);

            client.handleMessage(Map.of(
                    "jsonrpc", "2.0",
                    "method", "session.event",
                    "params", Map.of("sessionId", "reused-child",
                            "event", Map.of("type", "assistant/message"))));
            oldSubscription.drain(n -> oldSeen.add(n.method()));
            newSubscription.drain(n -> newSeen.add(n.method()));
        }

        assertEquals(List.of("subagent.started", "subagent.finished"), oldSeen);
        assertEquals(List.of("subagent.started", "session.event"), newSeen);
        assertEquals(0, client.notificationsQueue().size());
    }
}
