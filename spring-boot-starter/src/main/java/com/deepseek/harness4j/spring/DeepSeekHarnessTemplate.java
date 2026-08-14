package com.deepseek.harness4j.spring;

import com.deepseek.harness4j.DeepSeekHarness;
import com.deepseek.harness4j.DeepSeekHarnessConfig;
import com.deepseek.harness4j.RunResult;
import com.deepseek.harness4j.Session;
import com.deepseek.harness4j.log.SessionLog;
import com.deepseek.harness4j.model.Notification;
import com.deepseek.harness4j.model.StreamChunk;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.function.Consumer;

/**
 * Spring-friendly wrapper around {@link DeepSeekHarness}.
 *
 * <p>Exposes the full turns, async, streaming, and offline log inspection APIs while keeping the
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

    /** Run one agent turn asynchronously. */
    public CompletableFuture<RunResult> runAsync(Object input) {
        return harness.runAsync(input);
    }

    /** Run one agent turn asynchronously on a specific session. */
    public CompletableFuture<RunResult> runAsync(Object input, String sessionId) {
        return harness.runAsync(input, sessionId);
    }

    /** Run one agent turn asynchronously on a specific session with a notification listener. */
    public CompletableFuture<RunResult> runAsync(Object input, String sessionId, Consumer<Notification> onNotification) {
        return harness.runAsync(input, sessionId, onNotification);
    }

    /** Resume a session: run one more turn against the existing session id. */
    public RunResult resume(Object input, String sessionId) {
        return harness.resume(input, sessionId);
    }

    /** Resume a session with a notification listener. */
    public RunResult resume(Object input, String sessionId, Consumer<Notification> onNotification) {
        return harness.resume(input, sessionId, onNotification);
    }

    /** Resume a session asynchronously. */
    public CompletableFuture<RunResult> resumeAsync(Object input, String sessionId) {
        return harness.resumeAsync(input, sessionId);
    }

    /** Resume a session asynchronously with a notification listener. */
    public CompletableFuture<RunResult> resumeAsync(Object input, String sessionId, Consumer<Notification> onNotification) {
        return harness.resumeAsync(input, sessionId, onNotification);
    }

    /** Stream real-time tokens and events to a consumer. */
    public RunResult stream(Object input, String sessionId, Consumer<StreamChunk> onChunk) {
        return harness.stream(input, sessionId, onChunk);
    }

    /** Stream real-time tokens and events on a fresh session. */
    public RunResult stream(Object input, Consumer<StreamChunk> onChunk) {
        return harness.stream(input, onChunk);
    }

    /** Return a reactive {@link Flow.Publisher} for streaming chunks. */
    public Flow.Publisher<StreamChunk> stream(Object input, String sessionId) {
        return harness.stream(input, sessionId);
    }

    /** Return a reactive {@link Flow.Publisher} for streaming chunks on a fresh session. */
    public Flow.Publisher<StreamChunk> stream(Object input) {
        return harness.stream(input);
    }

    // ==========================================
    // Offline Session Log Operations
    // ==========================================

    /** List headers of all persisted sessions. */
    public List<SessionLog.Header> listSessions() {
        return harness.listSessions();
    }

    /** Read all raw events from a persisted session log. */
    public List<Map<String, Object>> readSessionLog(String sessionId) {
        return harness.readSessionLog(sessionId);
    }

    /** Return the replay view of a persisted session log. */
    public List<Map<String, Object>> replaySessionLog(String sessionId) {
        return harness.replaySessionLog(sessionId);
    }

    /** Search events within a persisted session log. */
    public List<Map<String, Object>> searchSessionLog(String sessionId, SessionLog.Query query) {
        return harness.searchSessionLog(sessionId, query);
    }

    /** Search events across all persisted session logs. */
    public List<SessionLog.SearchHit> searchAllSessions(SessionLog.Query query) {
        return harness.searchAllSessions(query);
    }

    /** Fork a persisted session log into a new session id. */
    public SessionLog.Header forkSession(String sourceId, String newId) {
        return harness.forkSession(sourceId, newId);
    }

    /** Fork a persisted session log and immediately return a runnable {@link Session} on the new id. */
    public Session forkAndStartSession(String sourceId, String newId) {
        return harness.forkAndStartSession(sourceId, newId);
    }

    @Override
    public void close() {
        harness.close();
    }
}
