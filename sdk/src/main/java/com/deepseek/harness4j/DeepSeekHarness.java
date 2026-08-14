package com.deepseek.harness4j;

import com.deepseek.harness4j.client.HarnessClient;
import com.deepseek.harness4j.client.HarnessConfig;
import com.deepseek.harness4j.log.SessionLog;
import com.deepseek.harness4j.model.Notification;
import com.deepseek.harness4j.model.StreamChunk;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.function.Consumer;

/**
 * Reusable SDK for running DeepSeek Harness agent turns.
 *
 * <p>Extends the Python {@code DeepSeekHarness} API with Java async, reactive streaming,
 * offline session log inspection, and minimal benchmark execution.
 */
public final class DeepSeekHarness implements AutoCloseable {

    private static final String MINIMAL_CONFIG_RESOURCE = "examples/jsonrpc-agent/minimal.cordis.yml";

    private final DeepSeekHarnessConfig config;
    private final String cwd;
    private final HarnessClient client;
    private boolean initialized;

    public DeepSeekHarness() {
        this(DeepSeekHarnessConfig.builder().build());
    }

    public DeepSeekHarness(DeepSeekHarnessConfig config) {
        this.config = config == null ? DeepSeekHarnessConfig.builder().build() : config;
        this.cwd = resolve(config.cwd() != null
                ? config.cwd()
                : Path.of("").toAbsolutePath().toString());
        String runtimeCwd = config.runtimeCwd() != null ? resolve(config.runtimeCwd()) : cwd;
        Map<String, String> env = new LinkedHashMap<>(config.env());
        if (config.sessionRoot() != null) {
            env.put("DSH_SESSION_ROOT", config.sessionRoot());
        }
        if (config.cordis() != null) {
            env.put("DSH_CORDIS_CONFIG", config.cordis());
        }
        env.put("DSH_CWD", cwd);
        if (config.baseUrl() != null) {
            env.put("DEEPSEEK_BASE_URL", config.baseUrl());
        }
        if (config.apiKey() != null) {
            env.put("DEEPSEEK_API_KEY", config.apiKey());
        }
        this.client = new HarnessClient(HarnessConfig.builder()
                .runtimeBin(config.runtimeBin())
                .launchArgsOverride(config.launchArgsOverride())
                .cwd(runtimeCwd)
                .env(env)
                .requestTimeoutSeconds(config.requestTimeoutSeconds())
                .shutdownTimeoutSeconds(config.shutdownTimeoutSeconds())
                .build());
        this.initialized = false;
    }

    /**
     * Create a preconfigured {@link DeepSeekHarness} using the Minimal Agent composition
     * (matching upstream {@code minimal.cordis.yml}, ideal for benchmark evals).
     */
    public static DeepSeekHarness createMinimal(String workspace) {
        return createMinimal(Path.of(workspace));
    }

    /**
     * Create a preconfigured {@link DeepSeekHarness} using the Minimal Agent composition.
     */
    public static DeepSeekHarness createMinimal(Path workspace) {
        Path cordisConfig = resolveMinimalConfig();
        return new DeepSeekHarness(DeepSeekHarnessConfig.builder()
                .cwd(workspace.toAbsolutePath().normalize().toString())
                .cordis(cordisConfig.toString())
                .build());
    }

    private static Path resolveMinimalConfig() {
        URL resource = DeepSeekHarness.class.getClassLoader().getResource(MINIMAL_CONFIG_RESOURCE);
        if (resource == null) {
            throw new IllegalStateException(
                    "missing classpath resource " + MINIMAL_CONFIG_RESOURCE + " (minimal.cordis.yml)");
        }
        if ("file".equals(resource.getProtocol())) {
            return Path.of(URI.create(resource.toString()));
        }
        try {
            Path target = Files.createTempFile("deepseek-harness4j-minimal-", ".cordis.yml");
            try (InputStream in = resource.openStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            target.toFile().deleteOnExit();
            return target;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to materialize minimal.cordis.yml", e);
        }
    }

    /** @return the low-level JSON-RPC client backing this instance. */
    public HarnessClient client() {
        return client;
    }

    /** @return the resolved agent working directory. */
    public String cwd() {
        return cwd;
    }

    /** @return the configuration this instance was built with. */
    public DeepSeekHarnessConfig config() {
        return config;
    }

    /**
     * Start the runtime subprocess and complete the initialize handshake. Idempotent.
     */
    public void start() {
        if (initialized) {
            return;
        }
        client.start();
        client.initialize(cwd, config.provider(), config.model(), config.maxTokens());
        initialized = true;
    }

    @Override
    public void close() {
        client.close();
        initialized = false;
    }

    /**
     * Start (and initialize) a session, generating a fresh id when none is given.
     */
    public Session startSession(String sessionId) {
        start();
        String id = sessionId != null
                ? sessionId
                : "session-" + UUID.randomUUID().toString().replace("-", "");
        return new Session(this, id);
    }

    /**
     * Run one agent turn on a session.
     */
    public RunResult run(Object input, String sessionId, Consumer<Notification> onNotification) {
        return startSession(sessionId).run(input, onNotification);
    }

    /**
     * Run one agent turn on a freshly generated session.
     */
    public RunResult run(Object input) {
        return run(input, null, null);
    }

    /**
     * Run one agent turn asynchronously.
     */
    public CompletableFuture<RunResult> runAsync(Object input) {
        return startSession(null).runAsync(input);
    }

    /**
     * Run one agent turn asynchronously on a specific session.
     */
    public CompletableFuture<RunResult> runAsync(Object input, String sessionId) {
        return startSession(sessionId).runAsync(input);
    }

    /**
     * Run one agent turn asynchronously on a specific session with a notification listener.
     */
    public CompletableFuture<RunResult> runAsync(Object input, String sessionId, Consumer<Notification> onNotification) {
        return startSession(sessionId).runAsync(input, onNotification);
    }

    /**
     * Resume a session: run one more turn against the existing session id.
     */
    public RunResult resume(Object input, String sessionId) {
        return resume(input, sessionId, null);
    }

    /**
     * Resume a session with a notification listener.
     */
    public RunResult resume(Object input, String sessionId, Consumer<Notification> onNotification) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId is required to resume a session");
        }
        return startSession(sessionId).resume(input, onNotification);
    }

    /**
     * Resume a session asynchronously.
     */
    public CompletableFuture<RunResult> resumeAsync(Object input, String sessionId) {
        return resumeAsync(input, sessionId, null);
    }

    /**
     * Resume a session asynchronously with a notification listener.
     */
    public CompletableFuture<RunResult> resumeAsync(Object input, String sessionId, Consumer<Notification> onNotification) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId is required to resume a session");
        }
        return startSession(sessionId).resumeAsync(input, onNotification);
    }

    /**
     * Stream real-time tokens and events to a consumer.
     */
    public RunResult stream(Object input, String sessionId, Consumer<StreamChunk> onChunk) {
        return startSession(sessionId).stream(input, onChunk);
    }

    /**
     * Stream real-time tokens and events on a fresh session.
     */
    public RunResult stream(Object input, Consumer<StreamChunk> onChunk) {
        return stream(input, null, onChunk);
    }

    /**
     * Return a reactive {@link Flow.Publisher} for streaming chunks.
     */
    public Flow.Publisher<StreamChunk> stream(Object input, String sessionId) {
        return startSession(sessionId).stream(input);
    }

    /**
     * Return a reactive {@link Flow.Publisher} for streaming chunks on a fresh session.
     */
    public Flow.Publisher<StreamChunk> stream(Object input) {
        return stream(input, (String) null);
    }

    // ==========================================
    // Offline Session Log Operations
    // ==========================================

    private Path requireSessionRoot() {
        if (config.sessionRoot() == null) {
            throw new IllegalStateException("sessionRoot must be configured in DeepSeekHarnessConfig to inspect session logs");
        }
        return Path.of(config.sessionRoot());
    }

    /**
     * List headers of all persisted sessions.
     */
    public List<SessionLog.Header> listSessions() {
        return SessionLog.list(requireSessionRoot());
    }

    /**
     * Read all raw events from a persisted session log.
     */
    public List<Map<String, Object>> readSessionLog(String sessionId) {
        return SessionLog.read(requireSessionRoot(), sessionId);
    }

    /**
     * Return the replay view of a persisted session log (filtered to key turn & tool events).
     */
    public List<Map<String, Object>> replaySessionLog(String sessionId) {
        return SessionLog.replay(requireSessionRoot(), sessionId);
    }

    /**
     * Search events within a persisted session log.
     */
    public List<Map<String, Object>> searchSessionLog(String sessionId, SessionLog.Query query) {
        return SessionLog.search(requireSessionRoot(), sessionId, query);
    }

    /**
     * Search events across all persisted session logs.
     */
    public List<SessionLog.SearchHit> searchAllSessions(SessionLog.Query query) {
        return SessionLog.searchAll(requireSessionRoot(), query);
    }

    /**
     * Fork a persisted session log into a new session id.
     */
    public SessionLog.Header forkSession(String sourceId, String newId) {
        return SessionLog.fork(requireSessionRoot(), sourceId, newId);
    }

    /**
     * Fork a persisted session log and immediately return a runnable {@link Session} on the new id.
     */
    public Session forkAndStartSession(String sourceId, String newId) {
        String id = newId != null ? newId : "session-" + UUID.randomUUID().toString().replace("-", "");
        forkSession(sourceId, id);
        return startSession(id);
    }

    private static String resolve(String path) {
        Path absolute = Path.of(path).toAbsolutePath().normalize();
        try {
            return absolute.toRealPath().toString();
        } catch (java.io.IOException ignored) {
            return absolute.toString();
        }
    }
}
