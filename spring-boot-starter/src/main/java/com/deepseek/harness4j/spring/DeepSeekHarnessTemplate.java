package com.deepseek.harness4j.spring;

import com.deepseek.harness4j.DeepSeekHarness;
import com.deepseek.harness4j.DeepSeekHarnessConfig;
import com.deepseek.harness4j.RunResult;
import com.deepseek.harness4j.model.Notification;

import java.util.function.Consumer;

/**
 * Spring-friendly wrapper around {@link DeepSeekHarness}.
 *
 * <p>Exposes the same turns API as the Python {@code DeepSeekHarness} while keeping the
 * underlying instance lifecycle owned by the Spring context: {@link #close()} reaps the
 * runtime subprocess when the application context shuts down.
 */
public class DeepSeekHarnessTemplate implements AutoCloseable {

    private final DeepSeekHarness harness;

    public DeepSeekHarnessTemplate(DeepSeekHarness harness) {
        this.harness = harness;
    }

    public DeepSeekHarnessTemplate(DeepSeekHarnessConfig config) {
        this.harness = new DeepSeekHarness(config);
    }

    /** @return the underlying SDK harness. */
    public DeepSeekHarness harness() {
        return harness;
    }

    /** Run one agent turn on a session, with an optional per-notification callback. */
    public RunResult run(Object input, String sessionId, Consumer<Notification> onNotification) {
        return harness.run(input, sessionId, onNotification);
    }

    /** Run one agent turn on a freshly generated session. */
    public RunResult run(Object input) {
        return harness.run(input);
    }

    @Override
    public void close() {
        harness.close();
    }
}
