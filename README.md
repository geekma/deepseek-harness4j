# DeepSeek Harness Java SDK (deepseek-harness4j)

English | [中文](README.zh.md)

**deepseek-harness4j** is the Java port of [DeepSeek Harness](https://github.com/deepseek-ai/deepseek-harness)'s Python SDK, driving the same agent runtime via newline-delimited JSON-RPC 2.0 over stdio. Everything is a plugin, MIT-licensed, developer preview.

DeepSeek Harness (command `dsh`) is an **agent harness** whose core design principle is **everything is a plugin**: sessions, system prompts, tools, the agent loop, LLM access, bash, the filesystem, subprocesses, web capabilities, subagents, and workflows are all pluggable [Cordis](https://github.com/cordiverse/cordis) plugins. Upstream ships a Web UI, CLI, Python SDK, ACP, and JSON-RPC clients; this repository ports the **Python SDK channel** into a Java SDK. A Java process talks newline-delimited JSON-RPC 2.0 over stdio to the bundled runtime (`dsh-jsonrpc-agent`) and drives the real agent loop (sessions, system prompts, tools, subagents, persistence). **The runtime, the Cordis plugin composition (`cordis.yml`), and the model configuration are identical to upstream - only the client language changes.**

> This project is in **developer preview**: it iterates fast and may ship breaking changes. Always check the official docs before relying on it.

Before using the SDK, read the [DeepSeek Harness complete guide (Java edition)](deepseek-harness4j-使用指南.md), which covers custom-model integration, `cordis.yml` composition, common errors, and verified run records.

---

## What is DeepSeek Harness?

### What it is

**DeepSeek Harness** (`dsh`) is an open-source **agent harness** from [DeepSeek AI](https://deepseek.com).

- **Core principle: everything is a plugin.** Sessions, system prompts, tools, the agent loop, LLM access, bash, the filesystem, subprocesses, web capabilities, subagents, and workflows are all pluggable Cordis plugins.
- Powered by [Cordis](https://github.com/cordiverse/cordis), whose design draws on the paper [_A Programming Paradigm for Spatiotemporal Composability_](https://github.com/cordiverse/paper).
- **MIT License.**
- The upstream project is written in Node.js + TypeScript as a pnpm monorepo and also ships a Python SDK (`deepseek-harness-sdk`). This repository provides the matching **Java SDK** - a line-by-line port of the Python SDK.

### Architecture at a glance

| Dimension | Description |
|---|---|
| Plugin model | Every capability is a **capability seam** made of three roles: Service Definition, Service Provider, and Consumer |
| Core packages | `@deepseek-ai/dsh-core` (session, system-prompt, tools, agent, agent-loop) |
| LLM layer | `@deepseek-ai/dsh-llm` plus adapters `dsh-llm-deepseek` (official direct), `dsh-llm-pi-ai` (universal multi-provider), `dsh-llm-retry` |
| Capability layer | shell, subprocess, terminal, fs, lsp, skill, web, compaction, subagent, workflow, todo, plan, ... |
| Clients | Web UI, CLI, Python SDK, Java SDK (deepseek-harness4j), ACP (Agent Client Protocol), JSON-RPC |
| Data | Sessions persist to JSONL logs; SQLite stores metadata |

**Key conclusion: switching models or wiring a custom endpoint is mostly configuration, not code.** Because `dsh-llm-pi-ai` already speaks OpenAI-compatible, Anthropic, and other protocols, a self-hosted OpenAI-compatible gateway can be plugged in through config alone. This conclusion holds for the Java SDK too - only the client language changes; the model configuration is identical.

### Current status

- **Developer preview**: fast iteration; **breaking changes may occur**.
- Config / env entry points: `DEEPSEEK_API_KEY` (required), `DEEPSEEK_BASE_URL` (optional, for OpenAI-compatible proxies), and a root `.env`.
- User data directory: `$DSH_HOME` (default `~/.dsh`), where `settings.yaml` stores config and `.credentials.yaml` stores write-only secrets.

### How it compares to similar frameworks

In short: **dsh is essentially DeepSeek's take on Claude Code - a self-hostable, provider-agnostic agent harness** that shares DNA with mainstream agent products but makes a few deliberate differentiations.

| Similar product / framework | Relationship to dsh |
|---|---|
| **Claude Code / Claude Agent SDK** (Anthropic) | Closest peer. Both are agent harnesses with sessions, system prompts, tool loops, subagents, hooks, and persistence built in. dsh is roughly DeepSeek's reimagining of Claude Code, but **self-hosted, MIT, provider-agnostic** |
| **Codex CLI** (OpenAI), **Gemini CLI** (Google) | Same class of terminal agent CLI; dsh's headless CLI corresponds to them |
| **OpenHands** (formerly OpenDevin) | Fellow open-source autonomous software-development agent; same track |
| **Aider** | Terminal pair-programming agent; dsh leans more toward a composable framework than a single tool |
| **LangGraph / LangChain / smolagents / CrewAI** | All are agent-building libraries. dsh is more batteries-included - a complete harness, not just orchestration primitives |
| **MCP** (Model Context Protocol) tool ecosystem | dsh's capability plugins (bash/fs/web/skill/lsp...) are conceptually similar to MCP tools, but dsh is a full runtime, not only a tool protocol |
| **AutoGPT / BabyAGI** | Both are autonomous agents; dsh emphasizes control (approval/sandbox/reproducible sessions) over unchecked autonomy |

**The differentiator:** unlike products that lock you to one vendor's model, dsh's biggest selling point is that **LLM access is a pluggable seam** - the official adapter targets DeepSeek, but through `llm-pi-ai` you can also reach OpenAI, Anthropic, any OpenAI-compatible gateway, or write your own adapter for a private model. It is best understood as "an agent runtime you can fully control."

### What problems does it solve?

1. **Escape provider lock-in** - the LLM layer is an abstract seam; switching between DeepSeek / OpenAI / Anthropic / a self-hosted gateway is a config change, not a code change. Great for private models, company gateways, or self-hosted vLLM/Ollama.
2. **No need to build an agent from scratch** - session management, system prompts, tool loops, subagents, workflows, plans, approval/permission, sandboxing, and persistence are all built in and ready to use.
3. **Strong extensibility** - "everything is a plugin": compose the capabilities you need (bash/file/web/skill/LSP), with HMR support; most behavior changes live in `cordis.yml` without touching `agent-loop`.
4. **Controllable and auditable** - every model request and tool call lands in the session JSONL log, replayable and reproducible; approval/permission policies plus a sandbox (landlock) keep the agent in check.
5. **One core, many clients** - the same agent kernel is driven by Web UI, CLI, Python SDK, Java SDK, ACP, and JSON-RPC, so it embeds easily into your own programs, workflows, and CI.
6. **Multi-agent collaboration** - subagent delegation and parallel product/code agents ship out of the box.
7. **Private deployment, MIT open source** - no dependency on a closed cloud platform; deploy entirely in your own environment.

---

## Install & Use

### A. Run the Web UI via npm (fastest)

Prerequisite: Node.js is installed (the repo requires `node ^22.19 || >=24`).

```sh
npx @deepseek-ai/dsh web
```

- Default address: `http://127.0.0.1:3080`
- First run: Settings -> Models, fill in your DeepSeek API key -> choose a workspace -> create a session and send a task.

### B. Build from source

```sh
git clone https://github.com/deepseek-ai/deepseek-harness.git
cd deepseek-harness
pnpm install
pnpm run build
pnpm dsh web          # start the Web UI
```

### C. Headless CLI for a single task

```sh
export DEEPSEEK_API_KEY=sk-xxx
# To route through an OpenAI-compatible proxy:
# export DEEPSEEK_BASE_URL=http://127.0.0.1:8000/v1
pnpm dsh --profile headless "Summarize this repository and list its main packages."
```

### D. Java SDK (programmatic, deepseek-harness4j)

This corresponds to upstream option D (Python SDK). On the Java side you pull in the SDK via Maven and need a runtime carrier (see the [sdk-runtime README](sdk-runtime/README.en.md)).

```sh
git clone https://github.com/deepseek-ai/deepseek-harness.git
cd deepseek-harness
# 1) Build the runtime carrier (or install from a runtime distribution)
pnpm install
pnpm exec tsx scripts/build-exe-for-python-sdk.ts
# 2) Place the generated dsh-jsonrpc-agent-pkg-<platform>-<arch> into the
#    deepseek-harness4j runtime directory (see development.md)
```

```sh
export DEEPSEEK_API_KEY=sk-xxx
# export DEEPSEEK_BASE_URL=http://127.0.0.1:8000/v1   # OpenAI-compatible proxy
# export DSH_MODEL=deepseek-v4-flash
```

Maven coordinates: `com.deepseek-ai:deepseek-harness4j-sdk`.

Requirements: **JDK 17+** (LTS; the code also runs on 21 / 25), **Maven 3.9+**, and an available runtime carrier or an explicit channel.

```sh
cd deepseek-harness4j
mvn install            # build all modules (including spring-boot-starter / example)
mvn test               # run tests (sdk module)
```

---

## Quick Start (Java SDK)

### Minimal three steps

```sh
# 1) Add the dependency (Maven coordinates: com.deepseek-ai:deepseek-harness4j-sdk)
# 2) Set credentials
export DEEPSEEK_API_KEY=sk-your-key
# 3) Use it
```

```java
import com.deepseek.harness4j.DeepSeekHarness;
import com.deepseek.harness4j.RunResult;

try (DeepSeekHarness harness = new DeepSeekHarness()) {
    RunResult result = harness.run("Say hi.");
    System.out.println(result.finalResponse());
}
```

`new DeepSeekHarness()` launches the `dsh-jsonrpc-agent` executable bundled with the runtime carrier and injects the default composition (JSON-RPC server + agent core + DeepSeek adapter + JSONL session persistence + local bash).

### Full parameterized example

```java
import com.deepseek.harness4j.DeepSeekHarness;
import com.deepseek.harness4j.DeepSeekHarnessConfig;
import com.deepseek.harness4j.RunResult;

String config    = Path.of("examples/jsonrpc-agent/minimal.cordis.yml").toAbsolutePath().normalize().toString();
String workspace = Path.of("/absolute/path/to/workspace").toAbsolutePath().normalize().toString();
String sessions  = Path.of("/absolute/path/to/sessions").toAbsolutePath().normalize().toString();

try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
        .provider("deepseek-official")      // provider route (registered by an adapter in cordis.yml)
        .model("deepseek-v4-flash")         // model id
        .maxTokens(49_152)                  // per-request output token cap (optional)
        .cwd(workspace)                     // agent working directory (it really edits files here)
        .sessionRoot(sessions)              // where session JSONL is persisted
        .cordis(config)                     // your own plugin composition file (see cordis.yml Composition)
        .build())) {
    RunResult result = harness.run(
            "Inspect the repository and fix the failing tests.",
            "example-001",                  // reuse a session id to continue the conversation
            null);
    System.out.println(result.finalResponse());   // last assistant text from the root session
    System.out.println(result.finishReason());    // completed / max-tokens / error ...
    System.out.println(result.sessionId());
    // result.events() / result.notifications() hold session and subagent events
}
```

### `DeepSeekHarnessConfig` builder fields

| Field | Purpose |
|---|---|
| `provider` | Provider route registered by an adapter (e.g. `deepseek-official`; with `llm-pi-ai` mounted you can use any catalog provider) |
| `model` | Model id resolved by that provider |
| `maxTokens` | Output token cap for the root agent and its in-process descendants; omit to use the provider default |
| `cordis` | Path to a custom plugin composition file (omit to use the bundled default) |
| `cwd` | Agent working directory (auto-resolved to an absolute path, matching Python's `Path.resolve()`) |
| `sessionRoot` | Session persistence directory (equivalent to setting `DSH_SESSION_ROOT`) |

### `RunResult` API

| Method | Returns |
|---|---|
| `finalResponse()` | The last committed assistant text from the root session |
| `finishReason()` | `completed` / `max-tokens` / `error` / ... |
| `sessionId()` | The session id (reuse it to continue a conversation) |
| `events()` | Session and subagent events captured during the run |
| `notifications()` | Notifications emitted during the run |

### Pointing at a custom / self-hosted model

The runtime inherits `DEEPSEEK_BASE_URL` and `DEEPSEEK_API_KEY`, so there are three ways to wire a model:

```sh
# Option A: official DeepSeek
export DEEPSEEK_API_KEY=sk-xxx

# Option B: OpenAI-compatible self-hosted gateway (vLLM / Ollama / LM Studio / company proxy ...)
export DEEPSEEK_API_KEY=sk-any-non-empty-value
export DEEPSEEK_BASE_URL=http://127.0.0.1:8000/v1
export DSH_MODEL=qwen2.5-72b-instruct

# Option C: finer-grained multi-provider -> mount llm-pi-ai in cordis.yml and
#           configure providers in settings.yaml (see Custom Model Configuration)
```

```java
import com.deepseek.harness4j.DeepSeekHarness;
import com.deepseek.harness4j.DeepSeekHarnessConfig;

try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
        .provider("acme-gateway")      // custom route defined in settings.yaml
        .model("acme-large")
        .cordis("examples/jsonrpc-agent/minimal.cordis.yml")
        .build())) {
    System.out.println(harness.run("Write a Java snippet that reads JSON.", null, null).finalResponse());
}
```

---

## Custom Model Configuration

Custom models fall into three scenarios of increasing difficulty:

1. **OpenAI-compatible endpoint / company gateway / self-hosted service** - pure config, the most common path.
2. **Use a catalog provider (Anthropic / OpenAI etc.)** - just supply a key.
3. **A private model that speaks no built-in protocol** - write an LLM adapter plugin (code).

### Scenario 1: OpenAI-compatible custom endpoint (recommended, zero code)

#### Via the Web UI (graphical)

Path: **Settings -> Models -> Add a custom provider**

| Field | Description |
|---|---|
| Provider ID | **Lowercase** unique id; immutable once saved (requests, session logs, defaults, and credentials all reference it). To rename, create a new one and delete the old. |
| Display name | Display name (mutable) |
| Base URL | e.g. `https://gateway.example.com/v1` |
| API protocol | e.g. `openai-completions` |
| API key | Credentials (write-only) |
| Models | At least one model id; use "Fetch available models" to pull from `GET /models`, or fill in manually |

> Tip: manually entered models default to **text-only**. Vision models need `input: [text, image]` in `settings.yaml`, otherwise attached images are rejected before sending.

#### By editing `$DSH_HOME/settings.yaml` (scriptable / auditable)

Under `llm-pi-ai:` the `providers` key is a dictionary keyed by provider route and fully customizable:

```yaml
# $DSH_HOME/settings.yaml  (default ~/.dsh/settings.yaml)
llm-pi-ai:
  providers:
    # -- Option 1: fully hand-written custom route (self-hosted / private gateway) --
    acme-gateway:
      displayName: Acme Gateway
      apiKeyEnv: ACME_GATEWAY_API_KEY     # credential reference, no plaintext secret
      api: openai-completions             # protocol: openai-completions / openai-responses / anthropic ...
      baseURL: https://gateway.acme.example/v1
      # When a private gateway URL cannot auto-detect the reasoning dialect, set it explicitly:
      compat:
        thinkingFormat: deepseek
      models:
        - id: acme-large
          name: Acme Large
          contextWindow: 65536
          maxTokens: 4096
        - id: acme-think
          name: Acme Think
          contextWindow: 262144
          maxTokens: 32768
          # Optional: declare reasoning efforts for this model (off can be empty = "do not send")
          reasoningEfforts:
            off:
            high: high
            max: ultra

    # -- Option 2: use a catalog provider but override/narrow models (no need to write every field) --
    openai:
      apiKeyEnv: OPENAI_API_KEY
      baseURL: https://proxy.example.com:8443
      reasoning: high
      retryPolicy:
        mode: normal
        maxRetries: 3
        backoff:
          initialDelayMs: 500
          maxDelayMs: 10000
          jitterRatio: 0.1

    # -- Option 3: tweak one catalog model, keep the rest (modelOverrides) --
    deepseek:
      apiKeyEnv: DEEPSEEK_API_KEY
      modelOverrides:
        deepseek-v4-pro:
          reasoningEfforts:
            off:
            high: high

    # -- Vision model: declare image modality --
    vision-gateway:
      apiKeyEnv: GATEWAY_API_KEY
      api: openai-completions
      baseURL: https://vision.example/v1
      defaultInput: [text, image]      # fallback modality for all hand-written models on this route
      models:
        - id: first-model
        - id: vision-preview
          input: [text, image]         # only this model supports images
```

**Common provider profile fields (subset):**

`apiKeyEnv`, `displayName`, `api`, `baseURL`, `models`, `modelOverrides`, `compat`, `defaultContextWindow`, `defaultMaxTokens`, `defaultInput`, `headers`, `reasoning`, `thinkingBudgets`, `transport`, `timeoutMs`, `streamIdleTimeoutMs`, `retryPolicy`.

**Model entry fields:** `id`, `name`, `contextWindow`, `maxTokens`, `reasoningEfforts`, `compat`, `input`.

Key semantics:

- `apiKeyEnv` is a **per-request credential reference** (resolved from `.credentials.yaml` / env); no plaintext secret lands in the config file.
- The `models` list **wholly replaces** that route's catalog; writing a single `id` is enough (the rest inherits from the catalog or route fallbacks).
- `modelOverrides` patches one catalog model in place without replacing the whole list.
- Supported request protocols include `openai-completions` / `openai-responses` / `anthropic` (exposed via `supportedProtocols()`); Bedrock / Vertex / Azure / Codex need their own native credentials and do not fit the API key field.

#### Headless / CLI: use env vars to point at a custom endpoint

Skip `settings.yaml` and point the official DeepSeek adapter at your OpenAI-compatible proxy through env vars:

```sh
export DEEPSEEK_API_KEY=sk-your-key
export DEEPSEEK_BASE_URL=https://your-gateway.example.com/v1   # point at your endpoint
export DSH_MODEL=your-custom-model-id
pnpm dsh --profile headless "your task description"
```

In the Java SDK, use `DEEPSEEK_BASE_URL` + `DEEPSEEK_API_KEY` the same way (the runtime inherits both).

### Scenario 2: Catalog providers (Anthropic / OpenAI etc.)

Web UI: **Settings -> Models -> Add provider**, pick a provider, fill in the API key, save. The installed catalog supplies the endpoint, protocol, and model list. Providers with native auth (Bedrock/AWS, Vertex/ADC, Azure/api-version, Codex/OAuth) require their own native credentials - an API key alone is not enough.

### Scenario 3: Fully incompatible private model -> write an LLM adapter plugin

If your model speaks a non-OpenAI-compatible private protocol, implement an `LlmAdapter` (see the two complete implementations in `packages/llm/llm-deepseek` and `packages/llm/llm-pi-ai`).

> Note: the adapter is a **TypeScript plugin** that runs on the dsh runtime side; the client language (Python / Java / CLI) needs no changes. The code below is therefore identical to the upstream guide - a Java SDK caller only mounts the plugin in `cordis.yml` and selects the corresponding provider/model.

Minimal skeleton:

```ts
import { Context } from '@deepseek-ai/cordis'
import Schema from '@deepseek-ai/schemastery'
import { LlmAdapter, type GenerateOptions, type StreamChunk } from '@deepseek-ai/dsh-llm'

class MyAdapter extends LlmAdapter {
  constructor(private readonly apiKey: string) { super() }

  // Convert provider-agnostic requests into your API call, then yield StreamChunk fragments
  async *stream(options: GenerateOptions): AsyncIterable<StreamChunk> {
    // 1) options.messages -> your protocol request
    // 2) call the streaming API
    // 3) convert the response into a StreamChunk sequence:
    //    block-start -> text-delta* -> block-end
    //    (tool calls: block-start -> tool-call-delta* -> block-end)
    //    usage -> finish
  }
}

export interface Config { apiKey: string; providers: string[] }
export const Config: Schema<Config> = Schema.object({
  apiKey: Schema.string(),
  providers: Schema.array(Schema.string()).required(),
})

export const name = 'my-llm-adapter'
export const inject = ['llm']

export function apply(ctx: Context, config: Config) {
  ctx.llm.registerAdapter(config.providers, new MyAdapter(config.apiKey))
}
```

Mount it in `cordis.yml` and let the agent use it:

```yaml
- id: my-llm
  name: './src/my-llm-adapter.ts'
  config:
    apiKey: !!js process.env.MY_API_KEY
    providers:
      - my-provider

- id: agent-loop
  name: '@deepseek-ai/dsh-agent-loop'
  config:
    agents:
      - id: main
        provider: my-provider
        model: my-model-v1
```

**StreamChunk protocol rules** (`stream()` must obey them):

- Every `block-start` must have a matching `block-end`; `index` increments from 0.
- Tool-call arguments are raw JSON strings throughout; streaming fragments use `argumentsDelta`.
- `usage` must come before `finish`; `finish` must be the last fragment.
- Two legal error paths: throw an `LlmError` with a stable code from `stream()`, or end the stream with `finish {kind:'error'|'aborted'}`.
- Respect `options.signal`; for unsupported fields throw `LlmError(..., 'UNSUPPORTED')` - never silently drop them.

---

## cordis.yml Composition

`cordis.yml` is dsh's "recipe file" - it lists which plugins to load and their configuration. Below is the repo's real `examples/jsonrpc-agent/minimal.cordis.yml` (annotated). It composes a headless minimal agent: the model sees **one system prompt + two tools** (persistent bash + a string-replace editor).

```yaml
# ---- Full headless minimal-agent composition (from examples/jsonrpc-agent/minimal.cordis.yml) ----

# 1) JSON-RPC server (for SDK / programmatic calls)
- id: sdk-jsonrpc-server
  name: '@deepseek-ai/dsh-sdk-jsonrpc-server'
  config:
    maxTokensAsSuccess: false

# 2) LLM adapter: official DeepSeek direct connection
- id: llm-deepseek
  name: '@deepseek-ai/dsh-llm-deepseek'
  config:
    apiKeyEnv: DEEPSEEK_API_KEY                 # credential reference, no plaintext
    streamIdleTimeoutMs: 172800000
    models:                                     # models exposed to the selector (env-driven)
      - id: !!js process.env.DSH_MODEL ?? 'deepseek-v4-flash'
        contextWindow: !!js Number(process.env.DSH_CONTEXT_WINDOW ?? 1000000)

# 3) Sandbox and security policy
- id: sandbox
  name: '@deepseek-ai/dsh-sandbox-local'
- id: sandbox-policy
  name: '@deepseek-ai/dsh-sandbox-policy'
  config:
    mode: danger-full-access                    # full access (kept simple for the minimal example)
    workspaceRoot: !!js process.env.DSH_CWD ?? process.cwd()

# 4) Subprocess + persistent terminal (backing the bash tool)
- id: subprocess
  name: '@deepseek-ai/dsh-subprocess-local'
- id: pty
  name: '@deepseek-ai/dsh-terminal'
- id: terminal-bash
  name: '@deepseek-ai/dsh-terminal-bash'
  config:
    timeoutMs: 300000

# 5) Filesystem (used by the editor; also governed by the sandbox policy above)
- id: fs-local
  name: '@deepseek-ai/dsh-fs-local'
  config:
    cwd: !!js process.env.DSH_CWD ?? process.cwd()

# 6) Agent spine (session / system prompt / tool orchestration live here)
- id: agent-spine
  name: '@deepseek-ai/dsh-agent-spine-demo'
  config:
    includeHarnessIdentity: false
    includeRuntimeContext: false               # disable dynamic context injection
    persona: !!js process.env.DSH_SYSTEM_PROMPT ?? 'You are a helpful software engineer assistant.'
    workspaceContext: false
    skills:
      enabled: false                            # do not enable skills
    toolBash: false                             # handled by the dedicated bash tool below
    toolJobs: false

# 7) Two tools visible to the model: persistent bash + string-replace editor
- id: persistent-bash
  name: '@deepseek-ai/dsh-tool-bash-persistent'
  config:
    timeoutMs: 300000
    description: |-
      Run commands in a bash shell
      * State is persistent across command calls...
      * Please avoid commands that may produce a very large amount of output.

- id: str-replace-editor
  name: '@deepseek-ai/dsh-tool-str-replace-editor'
  config:
    maxOutputChars: 16000

# 8) Session persistence: JSONL
- id: sessions
  name: '@deepseek-ai/dsh-session-persistence-jsonl'
  config:
    root: !!js process.env.DSH_SESSION_ROOT ?? './.sessions'
    compression: none
```

### Run it from Java

```java
try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
        .cwd("/absolute/path/to/workspace")
        .sessionRoot("/absolute/path/to/sessions")
        .cordis("examples/jsonrpc-agent/minimal.cordis.yml")
        .build())) {
    harness.run("Read package.json and print the list of scripts.", "example-001", null);
}
```

### How to turn it into "your own agent"

- **Swap the model**: change `llm-deepseek`'s `models`, or switch to `llm-pi-ai`.
- **Add/remove tools**: add a `toolBash` / `toolSkill` / `toolJobs` plugin line; flip the matching `toolXxx: false` in `agent-spine` to `true` or remove it.
- **Change the system prompt**: the `persona` field.
- **Enable skills**: `skills.enabled: true` (and configure a `skill-filesystem` provider).
- **Add subagents / workflows / plans**: add `dsh-subagent`, `dsh-workflow`, `dsh-plan` plugins to the composition.
- **Change the sandbox level**: toggle `sandbox-policy.mode` between `danger-full-access` and restricted modes (tightening is strongly recommended for production).

> Principle: changing the composition (`cordis.yml`) **takes precedence over** changing code. Almost any behavior can be adjusted by "which plugin to load + what config to give it" without touching `agent-loop`. Java SDK callers follow the same principle.

---

## Spring Boot Integration

This repository adds a Spring Boot auto-configuration module `deepseek-harness4j-spring-boot-starter` and a runnable example `deepseek-harness4j-spring-boot-example`. In `application.yml`:

```yaml
deepseek:
  harness:
    provider: deepseek-official
    model: deepseek-v4-flash
    cwd: /absolute/path/to/workspace
    sessionRoot: /absolute/path/to/sessions
    requestTimeoutSeconds: 300
```

Inject it anywhere in your application:

```java
@RestController
class HarnessController {
    private final DeepSeekHarnessTemplate template;   // provided by auto-configuration
    // ... call template.run(input, sessionId, null)
}
```

- **Spring Boot**: `@AutoConfiguration` + `@ConfigurationProperties` (prefix `deepseek.harness`); `destroyMethod="close"` reaps the subprocess on context close; `deepseek.harness.enabled=false` disables it.
- **Spring Cloud**: the `deepseek.harness.*` properties can be supplied by a Spring Cloud Config server without code changes.
- **Spring MVC**: the `@RestController` in `spring-boot-example` demonstrates turning an HTTP request into one `Session.run()` turn.
- Details and limitations: [docs/java-migration-notes.md](docs/java-migration-notes.md).

---

## Port Mapping (Python -> Java)

| Python (upstream `python/`) | Java (this repo `deepseek-harness4j/`) |
|---|---|
| `sdk/src/deepseek_harness/__init__.py` | `com.deepseek.harness4j` (package entry + public API surface) |
| `sdk/src/deepseek_harness/api.py` | `DeepSeekHarness` / `DeepSeekHarnessConfig` / `Session` / `RunResult` |
| `sdk/src/deepseek_harness/client.py` | `client.HarnessClient` / `client.HarnessConfig` / `client.NotificationSubscription` |
| `sdk/src/deepseek_harness/models.py` | `model.*` (`Notification`, `IncomingRequest`, `ServerInfo`, `InitializeResponse`, `JsonValues`) |
| `sdk/src/deepseek_harness/errors.py` | `error.*` (`HarnessException`, `TransportClosedException`, `SdkProtocolException`, `JsonRpcException`, etc.) |
| `sdk-runtime/src/deepseek_harness_runtime/__init__.py` | `runtime.RuntimeResolver` |
| `sdk-runtime/src/.../runtime/cordis.yml` | `sdk/src/main/resources/runtime/cordis.yml` (classpath resource) |
| `sdk/pyproject.toml` | `sdk/pom.xml` |
| `sdk/tests/*` | `sdk/src/test/java/**` (JUnit 5) |
| none (client-language difference) | `spring-boot-starter/`, `spring-boot-example/` (Spring integration) |

The full line-by-line migration notes (Python->Java syntax differences, JDK versions, Spring Boot/Cloud/MVC integration) live in [docs/java-migration-notes.en.md](docs/java-migration-notes.en.md) ([中文](docs/java-migration-notes.md)).

---

## Project Layout

```ini
deepseek-harness4j/
├── README.md                 # this file (English); README.zh.md (Chinese) - mutually linked
├── deepseek-harness4j-使用指南.md  # Java edition of the upstream deepseek-harness-使用指南.md
├── docs/
│   ├── port-coverage.md(.en.md)        # [checklist] every .py/.md -> Java counterpart, zero omissions
│   ├── repo-inventory.md(.en.md)       # [repo-wide index] ownership/status of every upstream area
│   ├── python-sdk-api-reference.md(.en.md)  # [public spec] 100% of the Python SDK (API+protocol)
│   ├── user-guide/                     # Java bilingual port of upstream docs/user/guide + cookbook
│   │   ├── python-sdk.md(.en.md)       #   Get started with the Java SDK
│   │   ├── web-ui.md(.en.md)           #   Use the Web UI
│   │   ├── providers.md(.en.md)        #   Configure models
│   │   ├── adding-an-llm-adapter.md(.en.md)  #   Add an LLM adapter
│   │   └── adding-a-tool.md(.en.md)    #   Tool authoring reference
│   └── java-migration-notes.md(.en.md)  # migration notes: syntax / JDK / Spring integration
├── pom.xml                   # Maven root reactor
├── sdk/                      # deepseek-harness4j-sdk: core JSON-RPC client + high-level turns API
│   ├── README.md(.en.md)     # SDK usage (bilingual)
│   └── src/main/java/com/deepseek/harness4j/...
│       └── examples/         # MinimalAgent (port of minimal.py) / ManualSdkAgentSmoke (smoke)
├── sdk-runtime/              # runtime-carrier documentation (RuntimeResolver counterpart; bilingual README)
├── examples/jsonrpc-agent/   # jsonrpc-agent example notes (Java usage of minimal.cordis.yml; bilingual)
└── spring-boot-starter/      # Spring Boot auto-configuration (@ConfigurationProperties + template bean)
    └── spring-boot-example/  # runnable Spring Boot MVC example (REST controller)
```

---

## Environment Variables

| Variable | Purpose |
|---|---|
| `DEEPSEEK_API_KEY` | Required primary API key (deepseek adapter / default composition) |
| `DEEPSEEK_BASE_URL` | Optional; set when pointing at an OpenAI-compatible proxy / self-hosted gateway |
| `DSH_MODEL` | Default model id (default `deepseek-v4-flash`) |
| `DSH_CONTEXT_WINDOW` | Override the model context window |
| `DSH_SYSTEM_PROMPT` | Override the system prompt (persona) |
| `DSH_CWD` | Agent working directory |
| `DSH_SESSION_ROOT` | Session persistence root directory |
| `DSH_HOME` | Harness user directory (default `~/.dsh`; contains `settings.yaml`, `.credentials.yaml`) |
| `DSH_CORDIS_CONFIG` | Inject a custom cordis config (used by the SDK when starting the runtime) |
| `DSH_RUNTIME_MODE` | Runtime carrier mode (`exe` / `node`; read by deepseek-harness4j's `RuntimeResolver`) |

> For provider-level fields, see the `llm-deepseek` / `llm-pi-ai` READMEs and `config-catalog.md`.

---

## Documentation Index

| Document | Description |
|---|---|
| [deepseek-harness4j-使用指南.md](deepseek-harness4j-使用指南.md) | Complete usage guide (background/install/custom models/composition/Demo/verification/architecture; Java edition) |
| [docs/port-coverage.en.md](docs/port-coverage.en.md) | **Port coverage checklist**: every upstream .py/.md vs its Java counterpart, zero-omission proof |
| [docs/java-migration-notes.en.md](docs/java-migration-notes.en.md) | Line-by-line migration notes (syntax/JDK/Spring) |
| [docs/python-sdk-api-reference.en.md](docs/python-sdk-api-reference.en.md) | **Public spec**: the API and JSON-RPC wire protocol of 100% of the Python SDK, so other projects (any language) can build an equivalent client |
| [docs/test-report.en.md](docs/test-report.en.md) | **Test report**: results for all cases + the Python->Java one-to-one test mapping (HTML via `mvn -pl sdk surefire-report:report`) |
| [docs/repo-inventory.en.md](docs/repo-inventory.en.md) | **Repo-wide index**: ownership/status of every upstream area's md/py files |
| [docs/user-guide/python-sdk.en.md](docs/user-guide/python-sdk.en.md) | Get started with the Java SDK; Web UI, model configuration, LLM adapter, and tool authoring docs live alongside it in `docs/user-guide/` |
| [sdk/README.en.md](sdk/README.en.md) | SDK usage (bilingual) |
| [sdk-runtime/README.en.md](sdk-runtime/README.en.md) | Runtime carriers and zero-config design (bilingual) |
| [examples/jsonrpc-agent/README.en.md](examples/jsonrpc-agent/README.en.md) | Minimal-agent composition example (minimal.py -> MinimalAgent) |
| [development.md](development.md) | Build, test, and distribution workflows (bilingual) |

---

## License

[MIT](LICENSE), same as upstream.
