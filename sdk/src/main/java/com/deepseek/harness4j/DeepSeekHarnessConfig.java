package com.deepseek.harness4j;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for launching the local DeepSeek Harness SDK runtime.
 *
 * <p>Line-by-line Java port of the Python {@code DeepSeekHarnessConfig} dataclass in
 * {@code api.py}:
 * <pre>{@code
 * @dataclass(slots=True)
 * class DeepSeekHarnessConfig:
 *     provider: str = "deepseek-official"
 *     model: str = "deepseek-v4-flash"
 *     max_tokens: int | None = None
 *     cwd: str | None = None
 *     runtime_cwd: str | None = None
 *     session_root: str | None = None
 *     cordis: str | None = None
 *     env: dict[str, str] = field(default_factory=dict)
 *     runtime_bin: str | None = None
 *     launch_args_override: tuple[str, ...] | None = None
 *     request_timeout_seconds: float | None = None
 *     shutdown_timeout_seconds: float | None = 1.0
 *     base_url: str | None = None
 *     api_key: str | None = None
 * }</pre>
 *
 * <p>The runtime inherits the caller's environment by default, so existing
 * {@code DEEPSEEK_API_KEY} and {@code DEEPSEEK_BASE_URL} settings keep working. Use
 * {@link #env()} to intentionally override or inject variables for a subprocess.
 */
public final class DeepSeekHarnessConfig {

    private final String provider;
    private final String model;
    private final Integer maxTokens;
    private final String cwd;
    private final String runtimeCwd;
    private final String sessionRoot;
    private final String cordis;
    private final Map<String, String> env;
    private final String runtimeBin;
    private final String[] launchArgsOverride;
    private final Double requestTimeoutSeconds;
    private final Double shutdownTimeoutSeconds;
    private final String baseUrl;
    private final String apiKey;

    private DeepSeekHarnessConfig(Builder builder) {
        this.provider = builder.provider == null ? "deepseek-official" : builder.provider;
        this.model = builder.model == null ? "deepseek-v4-flash" : builder.model;
        this.maxTokens = builder.maxTokens;
        this.cwd = builder.cwd;
        this.runtimeCwd = builder.runtimeCwd;
        this.sessionRoot = builder.sessionRoot;
        this.cordis = builder.cordis;
        this.env = builder.env == null ? new LinkedHashMap<>() : new LinkedHashMap<>(builder.env);
        this.runtimeBin = builder.runtimeBin;
        this.launchArgsOverride = builder.launchArgsOverride;
        this.requestTimeoutSeconds = builder.requestTimeoutSeconds;
        this.shutdownTimeoutSeconds = builder.shutdownTimeoutSeconds == null
                ? 1.0
                : builder.shutdownTimeoutSeconds;
        this.baseUrl = builder.baseUrl;
        this.apiKey = builder.apiKey;
    }

    /** @return the provider route selected by the Cordis composition; defaults to {@code deepseek-official}. */
    public String provider() {
        return provider;
    }

    /** @return the model id resolved by the adapter; defaults to {@code deepseek-v4-flash}. */
    public String model() {
        return model;
    }

    /** @return the per-request output-token cap, or {@code null} for the provider default. */
    public Integer maxTokens() {
        return maxTokens;
    }

    /** @return the agent working directory, or {@code null} for the process cwd. */
    public String cwd() {
        return cwd;
    }

    /** @return the runtime subprocess working directory, or {@code null} to reuse {@link #cwd()}. */
    public String runtimeCwd() {
        return runtimeCwd;
    }

    /** @return the session persistence root (sets {@code DSH_SESSION_ROOT}), or {@code null}. */
    public String sessionRoot() {
        return sessionRoot;
    }

    /** @return the Cordis composition file path (sets {@code DSH_CORDIS_CONFIG}), or {@code null}. */
    public String cordis() {
        return cordis;
    }

    /** @return extra environment variables injected into the runtime subprocess. */
    public Map<String, String> env() {
        return env;
    }

    /** @return an explicit runtime executable path, or {@code null} to resolve the bundle. */
    public String runtimeBin() {
        return runtimeBin;
    }

    /** @return an explicit argv override for the runtime subprocess, or {@code null}. */
    public String[] launchArgsOverride() {
        return launchArgsOverride;
    }

    /** @return the per-request timeout in seconds, or {@code null} to wait indefinitely. */
    public Double requestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    /** @return the shutdown timeout in seconds; defaults to {@code 1.0}. */
    public Double shutdownTimeoutSeconds() {
        return shutdownTimeoutSeconds;
    }

    /** @return the base URL override (sets {@code DEEPSEEK_BASE_URL}), or {@code null}. */
    public String baseUrl() {
        return baseUrl;
    }

    /** @return the API key override (sets {@code DEEPSEEK_API_KEY}), or {@code null}. */
    public String apiKey() {
        return apiKey;
    }

    /** @return a new empty builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** Mutable builder mirroring Python's keyword construction of the dataclass. */
    public static final class Builder {
        private String provider;
        private String model;
        private Integer maxTokens;
        private String cwd;
        private String runtimeCwd;
        private String sessionRoot;
        private String cordis;
        private Map<String, String> env;
        private String runtimeBin;
        private String[] launchArgsOverride;
        private Double requestTimeoutSeconds;
        private Double shutdownTimeoutSeconds;
        private String baseUrl;
        private String apiKey;

        public Builder provider(String provider) {
            this.provider = provider;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder cwd(String cwd) {
            this.cwd = cwd;
            return this;
        }

        public Builder runtimeCwd(String runtimeCwd) {
            this.runtimeCwd = runtimeCwd;
            return this;
        }

        public Builder sessionRoot(String sessionRoot) {
            this.sessionRoot = sessionRoot;
            return this;
        }

        public Builder cordis(String cordis) {
            this.cordis = cordis;
            return this;
        }

        public Builder env(Map<String, String> env) {
            this.env = env;
            return this;
        }

        public Builder runtimeBin(String runtimeBin) {
            this.runtimeBin = runtimeBin;
            return this;
        }

        public Builder launchArgsOverride(String... launchArgsOverride) {
            this.launchArgsOverride = launchArgsOverride;
            return this;
        }

        public Builder requestTimeoutSeconds(Double requestTimeoutSeconds) {
            this.requestTimeoutSeconds = requestTimeoutSeconds;
            return this;
        }

        public Builder shutdownTimeoutSeconds(Double shutdownTimeoutSeconds) {
            this.shutdownTimeoutSeconds = shutdownTimeoutSeconds;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public DeepSeekHarnessConfig build() {
            return new DeepSeekHarnessConfig(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DeepSeekHarnessConfig that)) {
            return false;
        }
        return Objects.equals(provider, that.provider)
                && Objects.equals(model, that.model)
                && Objects.equals(maxTokens, that.maxTokens)
                && Objects.equals(cwd, that.cwd)
                && Objects.equals(runtimeCwd, that.runtimeCwd)
                && Objects.equals(sessionRoot, that.sessionRoot)
                && Objects.equals(cordis, that.cordis)
                && Objects.equals(env, that.env)
                && Objects.equals(runtimeBin, that.runtimeBin)
                && java.util.Arrays.equals(launchArgsOverride, that.launchArgsOverride)
                && Objects.equals(requestTimeoutSeconds, that.requestTimeoutSeconds)
                && Objects.equals(shutdownTimeoutSeconds, that.shutdownTimeoutSeconds)
                && Objects.equals(baseUrl, that.baseUrl)
                && Objects.equals(apiKey, that.apiKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, model, maxTokens, cwd, runtimeCwd, sessionRoot, cordis,
                env, runtimeBin, java.util.Arrays.hashCode(launchArgsOverride),
                requestTimeoutSeconds, shutdownTimeoutSeconds, baseUrl, apiKey);
    }
}
