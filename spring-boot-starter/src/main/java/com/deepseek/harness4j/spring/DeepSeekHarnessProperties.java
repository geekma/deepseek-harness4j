package com.deepseek.harness4j.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring Boot {@code @ConfigurationProperties} mapping for {@code deepseek.harness.*}.
 *
 * <p>This mirrors the fields of the Python {@code DeepSeekHarnessConfig} dataclass
 * ({@code python/sdk/src/deepseek_harness/api.py}), so the same configuration surface is
 * available through {@code application.yml} instead of Python keyword arguments. Spring Cloud
 * Config can supply these properties from a config server; the values flow into
 * {@link com.deepseek.harness4j.DeepSeekHarnessConfig} unchanged.
 */
@ConfigurationProperties(prefix = "deepseek.harness")
public class DeepSeekHarnessProperties {

    /** Provider route selected by the Cordis composition; defaults to {@code deepseek-official}. */
    private String provider = "deepseek-official";

    /** Model id resolved by the adapter; defaults to {@code deepseek-v4-flash}. */
    private String model = "deepseek-v4-flash";

    /** Optional per-request output-token cap; {@code null} leaves the provider default in control. */
    private Integer maxTokens;

    /** Agent working directory; defaults to the process cwd when unset. */
    private String cwd;

    /** Runtime subprocess working directory; defaults to {@link #cwd}. */
    private String runtimeCwd;

    /** Session persistence root (sets {@code DSH_SESSION_ROOT}). */
    private String sessionRoot;

    /** Cordis composition file path (sets {@code DSH_CORDIS_CONFIG}). */
    private String cordis;

    /** Extra environment variables injected into the runtime subprocess. */
    private Map<String, String> env = new LinkedHashMap<>();

    /** Explicit runtime executable path; resolves the bundle when unset. */
    private String runtimeBin;

    /** Per-request timeout in seconds; {@code null} waits indefinitely. */
    private Double requestTimeoutSeconds;

    /** Shutdown timeout in seconds; defaults to {@code 1.0}. */
    private Double shutdownTimeoutSeconds = 1.0;

    /** Base URL override (sets {@code DEEPSEEK_BASE_URL}). */
    private String baseUrl;

    /** API key override (sets {@code DEEPSEEK_API_KEY}); prefer environment credentials. */
    private String apiKey;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public String getCwd() {
        return cwd;
    }

    public void setCwd(String cwd) {
        this.cwd = cwd;
    }

    public String getRuntimeCwd() {
        return runtimeCwd;
    }

    public void setRuntimeCwd(String runtimeCwd) {
        this.runtimeCwd = runtimeCwd;
    }

    public String getSessionRoot() {
        return sessionRoot;
    }

    public void setSessionRoot(String sessionRoot) {
        this.sessionRoot = sessionRoot;
    }

    public String getCordis() {
        return cordis;
    }

    public void setCordis(String cordis) {
        this.cordis = cordis;
    }

    public Map<String, String> getEnv() {
        return env;
    }

    public void setEnv(Map<String, String> env) {
        this.env = env == null ? new LinkedHashMap<>() : new LinkedHashMap<>(env);
    }

    public String getRuntimeBin() {
        return runtimeBin;
    }

    public void setRuntimeBin(String runtimeBin) {
        this.runtimeBin = runtimeBin;
    }

    public Double getRequestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    public void setRequestTimeoutSeconds(Double requestTimeoutSeconds) {
        this.requestTimeoutSeconds = requestTimeoutSeconds;
    }

    public Double getShutdownTimeoutSeconds() {
        return shutdownTimeoutSeconds;
    }

    public void setShutdownTimeoutSeconds(Double shutdownTimeoutSeconds) {
        this.shutdownTimeoutSeconds = shutdownTimeoutSeconds;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /** @return a {@link com.deepseek.harness4j.DeepSeekHarnessConfig} built from these properties. */
    public com.deepseek.harness4j.DeepSeekHarnessConfig toSdkConfig() {
        return com.deepseek.harness4j.DeepSeekHarnessConfig.builder()
                .provider(provider)
                .model(model)
                .maxTokens(maxTokens)
                .cwd(cwd)
                .runtimeCwd(runtimeCwd)
                .sessionRoot(sessionRoot)
                .cordis(cordis)
                .env(env)
                .runtimeBin(runtimeBin)
                .requestTimeoutSeconds(requestTimeoutSeconds)
                .shutdownTimeoutSeconds(shutdownTimeoutSeconds)
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();
    }
}
