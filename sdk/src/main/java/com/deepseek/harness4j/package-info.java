/**
 * DeepSeek Harness Java SDK.
 *
 * <p>Line-by-line Java port of the Python {@code deepseek_harness} package
 * ({@code python/sdk/src/deepseek_harness/}). The SDK drives DeepSeek Harness as a
 * subprocess over newline-delimited JSON-RPC 2.0 on stdio.
 *
 * <p>Public API surface (mirroring the Python {@code __all__}):
 * <ul>
 *   <li>{@link com.deepseek.harness4j.DeepSeekHarness} — high-level turns API</li>
 *   <li>{@link com.deepseek.harness4j.DeepSeekHarnessConfig} — high-level configuration</li>
 *   <li>{@link com.deepseek.harness4j.Session} — a runnable session</li>
 *   <li>{@link com.deepseek.harness4j.RunResult} — one run interval's result</li>
 *   <li>{@link com.deepseek.harness4j.client.HarnessClient} — low-level JSON-RPC client</li>
 *   <li>{@link com.deepseek.harness4j.client.HarnessConfig} — low-level configuration</li>
 *   <li>{@link com.deepseek.harness4j.error.SdkProtocolException} — protocol violations</li>
 *   <li>{@link com.deepseek.harness4j.model.IncomingRequest},
 *       {@link com.deepseek.harness4j.model.InitializeResponse},
 *       {@link com.deepseek.harness4j.model.Notification},
 *       {@link com.deepseek.harness4j.model.ServerInfo} — wire models</li>
 * </ul>
 */
package com.deepseek.harness4j;
