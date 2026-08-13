package com.deepseek.harness4j.client;

import com.deepseek.harness4j.error.HarnessException;
import com.deepseek.harness4j.model.Notification;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;

/**
 * A private notification subscription over a client, with optional filtering.
 *
 * <p>Line-by-line Java port of the Python {@code NotificationSubscription} class in
 * {@code client.py}. Closing unregisters the subscription from the client; it is idempotent
 * and is what {@code try-with-resources} invokes in place of Python's context manager.
 */
public final class NotificationSubscription implements AutoCloseable {

    private final HarnessClient client;
    private final String subscriptionId;
    private final BlockingQueue<Object> notifications;
    private boolean closed;

    NotificationSubscription(HarnessClient client, String subscriptionId,
                             BlockingQueue<Object> notifications) {
        this.client = client;
        this.subscriptionId = subscriptionId;
        this.notifications = notifications;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        client.unsubscribeNotifications(subscriptionId);
    }

    /**
     * Block for the next notification on this subscription.
     *
     * @return the next notification
     * @throws HarnessException re-raised when the runtime failed this subscription
     */
    public Notification next() {
        Object item;
        try {
            item = notifications.take();
        } catch (InterruptedException exc) {
            Thread.currentThread().interrupt();
            throw new HarnessException("Interrupted while waiting for notification", exc);
        }
        if (item instanceof Throwable throwable) {
            rethrow(throwable);
        }
        return (Notification) item;
    }

    /**
     * Deliver every currently queued notification to the callback (non-blocking).
     *
     * @param onNotification callback invoked per queued notification
     * @throws HarnessException re-raised when the runtime failed this subscription
     */
    public void drain(Consumer<Notification> onNotification) {
        Objects.requireNonNull(onNotification, "onNotification");
        while (true) {
            Object item = notifications.poll();
            if (item == null) {
                return;
            }
            if (item instanceof Throwable throwable) {
                rethrow(throwable);
            }
            onNotification.accept((Notification) item);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void rethrow(Throwable throwable) throws T {
        if (throwable instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (throwable instanceof Error error) {
            throw error;
        }
        throw (T) new HarnessException(throwable);
    }
}
