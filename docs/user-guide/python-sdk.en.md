# Get started with the Java SDK

[中文](python-sdk.md) | English

> This file is the Java port of the upstream `docs/user/guide/python-sdk.md` (+ `python-sdk.zh.md`): the tutorial path — install, run the checked-in example, and call the SDK from your own program — is rewritten for `deepseek-harness4j`. The Web UI equivalent is [Use the Web UI](web-ui.en.md); model configuration is in [Configure models](providers.en.md).

This tutorial is the programmatic alternative to the Web UI. It installs the published Java SDK, runs a checked-in agent composition, and shows how to call the same API from your own program.

## Prerequisites

- JDK 17 or newer
- Maven 3.9+
- A runtime carrier for Linux x64, Linux arm64, or macOS 14 or newer on arm64 (see `development.en.md`, "Build runtime artifacts")
- A DeepSeek-compatible API endpoint and credential
- An isolated workspace that the agent may modify

## Install the SDK

Clone the repository for its runnable example, then add the SDK dependency (build the runtime carrier per `development.en.md`):

```sh
git clone https://github.com/deepseek-ai/deepseek-harness.git
cd deepseek-harness
pnpm install
pnpm exec tsx scripts/build-exe-for-python-sdk.ts   # build the runtime carrier; place the output in the deepseek-harness4j runtime directory
```

```xml
<dependency>
  <groupId>com.deepseek-ai</groupId>
  <artifactId>deepseek-harness4j-sdk</artifactId>
  <version>0.0.1-SNAPSHOT</version>
</dependency>
```

The installed runtime needs no system Node.js. Repository contributors who need to build the runtime or distributions from source should use the [contributor workflows](../../development.en.md).

## Run the checked-in example

Set the credential in the environment. Set `DEEPSEEK_BASE_URL` as well when the model is served by an OpenAI-compatible proxy rather than the default DeepSeek endpoint.

```sh
export DEEPSEEK_API_KEY=sk-your-key-here
# export DEEPSEEK_BASE_URL=http://127.0.0.1:8000/v1
# export DSH_MODEL=deepseek-v4-flash
# export DSH_SYSTEM_PROMPT='You are a helpful software engineer assistant.'
```

Run one task against an isolated workspace and session directory (the example entry is `com.deepseek.harness4j.examples.MinimalAgent`, the counterpart of upstream `examples/jsonrpc-agent/minimal.py`):

```sh
cd deepseek-harness4j
mvn -q -pl sdk exec:java \
  -Dexec.mainClass=com.deepseek.harness4j.examples.MinimalAgent \
  -Dexec.args="--workspace /absolute/path/to/workspace --session-root /absolute/path/to/sessions --session-id example-001 'Inspect the repository and fix the failing tests.'"
```

The script prints the final assistant response. The session directory receives a JSONL log containing the assembled model requests and tool calls.

## Use the SDK in your own program

The checked-in example is a thin wrapper around this SDK call:

```java
import com.deepseek.harness4j.DeepSeekHarness;
import com.deepseek.harness4j.DeepSeekHarnessConfig;
import com.deepseek.harness4j.RunResult;

import java.nio.file.Path;

public class Example {
    public static void main(String[] args) {
        String config = Path.of("examples/jsonrpc-agent/minimal.cordis.yml").toAbsolutePath().normalize().toString();
        String workspace = Path.of("/absolute/path/to/workspace").toAbsolutePath().normalize().toString();
        String sessions = Path.of("/absolute/path/to/sessions").toAbsolutePath().normalize().toString();

        try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
                .provider("deepseek-official")
                .model("deepseek-v4-flash")
                .maxTokens(49_152)
                .cwd(workspace)
                .sessionRoot(sessions)
                .cordis(config)
                .build())) {
            RunResult result = harness.run(
                    "Inspect the repository and fix the failing tests.",
                    "example-001",
                    null);
            System.out.println(result.finalResponse());
        }
    }
}
```

`DeepSeekHarness` starts the bundled runtime lazily and reuses it until the enclosing block exits (`try`-with-resources is the port of the Python context manager). Reusing the same harness and session id preserves the session-owned Bash process, including its working directory, exported variables, and shell functions. Use a fresh session id for an independent task; reuse an id only when the next call should continue the same durable conversation.

## Understand the example composition

| Property | Value |
|---|---|
| System prompt | `DSH_SYSTEM_PROMPT`, falling back to `You are a helpful software engineer assistant.` |
| Model in `MinimalAgent` | `--model`, then `DSH_MODEL`, then `deepseek-v4-flash` |
| Model-facing tools | Persistent `bash` and `str_replace_editor` only |
| Bash timeout | 300 seconds |
| Editor output limit | 16,000 characters |
| Context compaction | Disabled |
| Filesystem | Bare local backend; absolute editor paths may address any path visible to the runtime process |
| Session persistence | Uncompressed JSONL under `DSH_SESSION_ROOT` |

The composition omits harness identity, workspace prompt text, skills, one-shot Bash, task tools, compaction, and every other model-facing plugin. Sandbox-policy facts are logged as runtime user context rather than appended to the system prompt.

## Choose workspace and session IDs

`cwd` selects the workspace available to the agent, while `session_root` stores session logs and state. Use a fresh session id for an independent task; reuse an id only when the next call should continue the same conversation and persistent shell state.

The composition uses `danger-full-access`. Run it only inside a disposable checkout or container: Bash and the editor can modify any path allowed to the runtime process. The persistent PTY backend requires a POSIX terminal substrate, so this composition does not support Windows agents.

The `jsonrpc-agent` example reference (`examples/jsonrpc-agent/README.en.md`) owns the exact composition. The SDK reference (`sdk/README.en.md`) covers lifecycle, results, notifications, runtime selection, and configuration; the Cordis primer (upstream `docs/cordis-primer.md`) covers composition syntax (a language-neutral reference).
