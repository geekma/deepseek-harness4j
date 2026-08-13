package com.deepseek.harness4j.client;

import com.deepseek.harness4j.model.Notification;

/**
 * Predicate deciding whether a notification belongs to a subscription.
 *
 * <p>Python type alias:
 * <pre>{@code
 * NotificationFilter: TypeAlias = Callable[[Notification], bool]
 * }</pre>
 *
 * <p>Filters may throw; the client contains the failure to the offending
 * subscription (see {@code HarnessClient}).
 */
@FunctionalInterface
public interface NotificationFilter {

    /**
     * @param notification the notification to test
     * @return {@code true} when the notification should be delivered
     *         to this subscription
     */
    boolean test(Notification notification);
}
