package com.deepseek.harness4j;

import com.deepseek.harness4j.client.HarnessClient;
import com.deepseek.harness4j.client.HarnessConfig;
import com.deepseek.harness4j.model.Notification;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Reusable synchronous SDK for running DeepSeek Harness agent turns.
 *
 * <p>Line-by-line Java port of the Python {@code DeepSeekHarness} class in {@code api.py}.
 *
 * <p>The runtime subprocess starts lazily and remains owned by this instance across calls to
 * {@link #run}. Use the instance with {@code try}-with-resources (the port of the Python
 * context manager), or call {@link #close()} explicitly when finished, so the subprocess is
 * always reaped.
 */
public final class DeepSeekHarness implements AutoCloseable {

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
     * Start the runtime subprocess and complete the initialize handshake. Idempotent; called
     * implicitly by {@link #startSession} and {@link #run}.
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
     *
     * @param sessionId a durable session id, or {@code null} to generate {@code session-<hex>}
     * @return the new session
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
     *
     * @param input          a plain text prompt, or a list of content blocks
     * @param sessionId      a durable session id, or {@code null} to generate one
     * @param onNotification optional callback invoked per received notification
     * @return the run result
     */
    public RunResult run(Object input, String sessionId, Consumer<Notification> onNotification) {
        return startSession(sessionId).run(input, onNotification);
    }

    /**
     * Run one agent turn on a freshly generated session.
     *
     * @param input a plain text prompt, or a list of content blocks
     * @return the run result
     */
    public RunResult run(Object input) {
        return run(input, null, null);
    }

    /**
     * Resolve a path to an absolute, normalized, symlink-resolved path.
     *
     * <p>Python's {@code Path.resolve()} resolves symlinks; Java's {@code toAbsolutePath()}
     * does not, so this uses {@code toRealPath()} when the path exists and falls back to
     * {@code toAbsolutePath().normalize()} otherwise.
     */
    private static String resolve(String path) {
        Path absolute = Path.of(path).toAbsolutePath().normalize();
        try {
            return absolute.toRealPath().toString();
        } catch (java.io.IOException ignored) {
            return absolute.toString();
        }
    }
}
