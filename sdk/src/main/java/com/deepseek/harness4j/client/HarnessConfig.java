package com.deepseek.harness4j.client;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for launching the local DeepSeek Harness SDK runtime.
 *
 * <p>Python (dataclass with {@code slots=True}):
 * <pre>{@code
 * @dataclass(slots=True)
 * class HarnessConfig:
 *     runtime_bin: str | None = None
 *     bridge_bin: str | None = None
 *     launch_args_override: tuple[str, ...] | None = None
 *     cwd: str | None = None
 *     env: dict[str, str] | None = None
 *     request_timeout_seconds: float | None = None
 *     shutdown_timeout_seconds: float | None = 1.0
 * }</pre>
 *
 * <p>Python dataclasses accept positional-or-keyword construction with default
 * values; Java has no such built-in construct, so this class offers a
 * constructor with all defaults, a copy-with builder, and mutable setters.
 */
public final class HarnessConfig {

    private String runtimeBin;
    private String bridgeBin;
    private String[] launchArgsOverride;
    private String cwd;
    private Map<String, String> env;
    private Double requestTimeoutSeconds;
    private Double shutdownTimeoutSeconds;

    public HarnessConfig() {
    }

    private HarnessConfig(Builder builder) {
        this.runtimeBin = builder.runtimeBin;
        this.bridgeBin = builder.bridgeBin;
        this.launchArgsOverride = builder.launchArgsOverride;
        this.cwd = builder.cwd;
        this.env = builder.env == null ? null : new LinkedHashMap<>(builder.env);
        this.requestTimeoutSeconds = builder.requestTimeoutSeconds;
        this.shutdownTimeoutSeconds = builder.shutdownTimeoutSeconds == null
                ? 1.0
                : builder.shutdownTimeoutSeconds;
    }

    /** @return the explicit runtime executable path, or {@code null} when unset. */
    public String runtimeBin() {
        return runtimeBin;
    }

    /** @return the explicit bridge executable path, or {@code null} when unset. */
    public String bridgeBin() {
        return bridgeBin;
    }

    /** @return the explicit argv override, or {@code null} when unset. */
    public String[] launchArgsOverride() {
        return launchArgsOverride;
    }

    /** @return the subprocess working directory, or {@code null} to inherit. */
    public String cwd() {
        return cwd;
    }

    /** @return extra environment variables injected into the subprocess, or {@code null}. */
    public Map<String, String> env() {
        return env;
    }

    /** @return the per-request timeout in seconds, or {@code null} to wait indefinitely. */
    public Double requestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    /** @return the shutdown timeout in seconds; defaults to {@code 1.0}. */
    public Double shutdownTimeoutSeconds() {
        return shutdownTimeoutSeconds;
    }

    public void setRuntimeBin(String runtimeBin) {
        this.runtimeBin = runtimeBin;
    }

    public void setBridgeBin(String bridgeBin) {
        this.bridgeBin = bridgeBin;
    }

    public void setLaunchArgsOverride(String[] launchArgsOverride) {
        this.launchArgsOverride = launchArgsOverride;
    }

    public void setCwd(String cwd) {
        this.cwd = cwd;
    }

    public void setEnv(Map<String, String> env) {
        this.env = env == null ? null : new LinkedHashMap<>(env);
    }

    public void setRequestTimeoutSeconds(Double requestTimeoutSeconds) {
        this.requestTimeoutSeconds = requestTimeoutSeconds;
    }

    public void setShutdownTimeoutSeconds(Double shutdownTimeoutSeconds) {
        this.shutdownTimeoutSeconds = shutdownTimeoutSeconds;
    }

    /** @return a new builder seeded with the current values. */
    public Builder toBuilder() {
        return new Builder()
                .runtimeBin(runtimeBin)
                .bridgeBin(bridgeBin)
                .launchArgsOverride(launchArgsOverride)
                .cwd(cwd)
                .env(env)
                .requestTimeoutSeconds(requestTimeoutSeconds)
                .shutdownTimeoutSeconds(shutdownTimeoutSeconds);
    }

    /** @return a new empty builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** Mutable builder mirroring Python's keyword construction of the dataclass. */
    public static final class Builder {
        private String runtimeBin;
        private String bridgeBin;
        private String[] launchArgsOverride;
        private String cwd;
        private Map<String, String> env;
        private Double requestTimeoutSeconds;
        private Double shutdownTimeoutSeconds;

        public Builder runtimeBin(String runtimeBin) {
            this.runtimeBin = runtimeBin;
            return this;
        }

        public Builder bridgeBin(String bridgeBin) {
            this.bridgeBin = bridgeBin;
            return this;
        }

        public Builder launchArgsOverride(String... launchArgsOverride) {
            this.launchArgsOverride = launchArgsOverride;
            return this;
        }

        public Builder cwd(String cwd) {
            this.cwd = cwd;
            return this;
        }

        public Builder env(Map<String, String> env) {
            this.env = env;
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

        public HarnessConfig build() {
            return new HarnessConfig(this);
        }
    }

    @Override
    public String toString() {
        return "HarnessConfig{runtimeBin='" + runtimeBin + '\''
                + ", bridgeBin='" + bridgeBin + '\''
                + ", launchArgsOverride=" + java.util.Arrays.toString(launchArgsOverride)
                + ", cwd='" + cwd + '\''
                + ", env=" + (env == null ? null : env.keySet())
                + ", requestTimeoutSeconds=" + requestTimeoutSeconds
                + ", shutdownTimeoutSeconds=" + shutdownTimeoutSeconds + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HarnessConfig that)) {
            return false;
        }
        return Objects.equals(runtimeBin, that.runtimeBin)
                && Objects.equals(bridgeBin, that.bridgeBin)
                && java.util.Arrays.equals(launchArgsOverride, that.launchArgsOverride)
                && Objects.equals(cwd, that.cwd)
                && Objects.equals(env, that.env)
                && Objects.equals(requestTimeoutSeconds, that.requestTimeoutSeconds)
                && Objects.equals(shutdownTimeoutSeconds, that.shutdownTimeoutSeconds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(runtimeBin, bridgeBin, java.util.Arrays.hashCode(launchArgsOverride),
                cwd, env, requestTimeoutSeconds, shutdownTimeoutSeconds);
    }
}
