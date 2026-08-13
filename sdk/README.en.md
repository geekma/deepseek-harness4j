# DeepSeek Harness Java SDK

[中文](README.md) | English

Java subprocess SDK (`deepseek-harness4j-sdk`) for driving DeepSeek Harness over JSON-RPC stdio. This is a line-by-line Java port of the upstream DeepSeek Harness Python SDK (`python/sdk`, import module `deepseek_harness`). The runtime inherits normal DeepSeek Harness environment variables such as `DEEPSEEK_BASE_URL` and `DEEPSEEK_API_KEY`, so callers can use real model endpoints directly or point those variables at a local proxy.

## Installation

Add the `deepseek-harness4j-sdk` dependency from Maven Central (Java 17+):

```xml
<dependency>
  <groupId>com.deepseek-ai</groupId>
  <artifactId>deepseek-harness4j-sdk</artifactId>
  <version>0.0.1-SNAPSHOT</version>
</dependency>
```

or Gradle:

```groovy
implementation 'com.deepseek-ai:deepseek-harness4j-sdk:0.0.1-SNAPSHOT'
```

Before first use, install an available runtime carrier (see the [sdk-runtime README](../sdk-runtime/README.en.md)): the production single-file `dsh-jsonrpc-agent` executable, or the dev-only `node` closure; you can also select an explicit channel via `HarnessConfig.runtimeBin` / `launchArgsOverride`. The normal entry point therefore needs no executable argument:

```java
import com.deepseek.harness4j.DeepSeekHarness;
import com.deepseek.harness4j.RunResult;

try (DeepSeekHarness harness = new DeepSeekHarness()) {
    RunResult result = harness.run("Say hi.");
    System.out.println(result.finalResponse());
}
```

`DeepSeekHarness` keeps its lazily started runtime subprocess for reuse across calls. Use it with `try`-with-resources (the port of the Python context manager), as above, or call `close()` explicitly when finished.

By default, the SDK launches the bundled single-file `dsh-jsonrpc-agent` executable and injects its default configuration via `DSH_CORDIS_CONFIG` (the stdio JSON-RPC server, agent core, preloaded DeepSeek adapter, JSONL session persistence with an explicitly composed semantic checkpoint policy, local bash). To run a plugin composition of your own, keep the `@deepseek-ai/dsh-sdk-jsonrpc-server` entry in the config and pass the Cordis config path.

```java
import com.deepseek.harness4j.DeepSeekHarness;
import com.deepseek.harness4j.DeepSeekHarnessConfig;
import com.deepseek.harness4j.RunResult;

try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
        .provider("deepseek-official")
        .model("deepseek-v4-flash")
        .maxTokens(49_152)
        .cordis("examples/jsonrpc-agent/cordis.yml")
        .build())) {
    RunResult result = harness.run("Make the requested code change.");
}
```

`provider` selects a provider route registered by the chosen Cordis composition; `model` is the model id resolved by that adapter. `maxTokens` is an optional positive per-request output-token cap for the root agent and its in-process descendants; omission leaves the provider default in control. Compaction summaries keep the separate limit configured by their compaction plugin. The bundled default composition registers `deepseek-official`. A custom composition can mount `llm-pi-ai`, configure provider-specific credentials/endpoints there, and select any provider/model present in pi-ai's installed catalog.

`Session.run()` owns an activity interval from its prompt's durable inbox receipt through the next whole-agent idle and returns `RunResult(sessionId, finalResponse, finishReason, events, notifications, sessionRoot)`. `finalResponse` is the last committed root-session assistant text in the interval. `finishReason` is the `kind` of the last root-session `turn/end` in the interval, such as `completed`, `max-tokens`, or `error`, and is `null` when no turn ended. A `turn/end` without a string `data.reason.kind` violates the runtime protocol and raises `SdkProtocolException`. Both result fields describe the owned interval rather than an output or ending causally assigned to the prompt. Steering, injected context, and other queued work may contribute before idle.

`HarnessClient` retains discovered subagent ancestry for the lifetime of the runtime process. During each `Session.run()`, `RunResult.notifications` and the `onNotification` callback receive the root session and all known descendant notifications in wire order, including nested subagent lifecycle and session events. `RunResult.events` contains root-session events only, so descendant messages cannot replace the root response. The low-level `sessionPrompt()` returns the queued `messageId` immediately; callers that bypass `Session.run()` own any later activity boundary themselves.

The same behavior can be selected for the runtime subprocess with `DSH_CORDIS_CONFIG`. The injection lives in `HarnessClient.start()`, so the low-level client's default launch gets it too: when the launch resolves to the bundled runtime and neither `cordis` nor a non-empty `DSH_CORDIS_CONFIG` is set (the runtime treats an empty value as absent, and so does the injection check), the bundled default configuration is used; an explicit `runtimeBin`, `bridgeBin`, or `launchArgsOverride` disables the injection entirely. See the [sdk-runtime README](../sdk-runtime/README.en.md) for the runtime carriers and how to obtain them.

`cwd` and `runtimeCwd` are resolved to absolute paths before subprocess launch, environment injection, and the wire handshake (matching Python's `Path.resolve()`, which also resolves symlinks). The public API exposes only applied options: deployment persona and persistence belong in `cordis.yml`, while `sessionRoot` remains the high-level convenience that sets `DSH_SESSION_ROOT`.
