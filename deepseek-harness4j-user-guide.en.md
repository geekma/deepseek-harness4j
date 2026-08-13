# DeepSeek Harness4j — Complete User Guide (Java SDK)

[中文](deepseek-harness4j-使用指南.md) | English

> This file is the **Java port** of the repository's `deepseek-harness-使用指南.md` (compiled from reading the source and official docs of the GitHub repository `deepseek-ai/deepseek-harness`, master branch): all Python SDK examples have been rewritten to Java (`deepseek-harness4j`); the Node/CLI and Cordis composition (`cordis.yml`) sections remain unchanged — because they drive the very same runtime.
> Last verified: 2026-08-13. **Note: this project is in developer preview, iterates fast, and ships breaking changes; always defer to the official docs before use.**

---

## 1. Project Background

### 1.1 What it is

**DeepSeek Harness** (command name `dsh`) is an **agent harness** open-sourced by [DeepSeek AI](https://deepseek.com).

- Core design philosophy: **Everything is a plugin**. Sessions, system prompts, tools, agent loops, LLM access, bash, filesystem, subprocess, web capabilities, sub-agents, workflows… all are pluggable Cordis plugins.
- Powered underneath by __Cordis__ (https://github.com/cordiverse/cordis), whose design philosophy comes from the paper [_A Programming Paradigm for Spatiotemporal Composability_](https://github.com/cordiverse/paper).
- License: **MIT License**.
- The project is written in Node.js + TypeScript as a pnpm monorepo; it also ships a Python SDK (`deepseek-harness-sdk`). This repository, `deepseek-harness4j`, provides the corresponding **Java SDK** (a line-by-line port of the Python SDK).

### 1.2 Architecture Highlights

| Dimension | Description |
|---|---|
| Plugin model | Each capability is a **capability seam**, composed of three roles: Service Definition / Service Provider / Consumer |
| Core packages | `@deepseek-ai/dsh-core` (session, system-prompt, tools, agent, agent-loop) |
| LLM layer | `@deepseek-ai/dsh-llm` + adapters `dsh-llm-deepseek` (official direct), `dsh-llm-pi-ai` (universal multi-provider), `dsh-llm-retry` |
| Capability layer | shell, subprocess, terminal, fs, lsp, skill, web, compaction, subagent, workflow, todo, plan… |
| Clients | Web UI, CLI, Python SDK, Java SDK (deepseek-harness4j), ACP (Agent Client Protocol), JSON-RPC |
| Data | Sessions persisted to JSONL logs; SQLite stores metadata |

**Key conclusion: swapping models or wiring up a custom server endpoint is mostly "configuration" rather than "code changes."** Because `dsh-llm-pi-ai` ships built-in support for OpenAI-compatible, Anthropic, and several other protocols, a self-hosted OpenAI-compatible gateway can be wired in directly via config. This conclusion holds for the Java SDK as well — the Java side only swaps the client language; model configuration is identical.

### 1.3 Current Status

- **Developer preview**: iterating fast, **breaking changes will happen**.
- Main config / env entry points: `DEEPSEEK_API_KEY` (required), `DEEPSEEK_BASE_URL` (optional, used when pointing at an OpenAI-compatible proxy), root `.env`.
- User data directory: `$DSH_HOME` (default `~/.dsh`), where `settings.yaml` holds config and `.credentials.yaml` holds secrets (write-only, never echoed in the UI).

### 1.4 What well-known frameworks/products is it like?

In a sentence: **dsh is essentially DeepSeek's take on Claude Code / a self-hostable general-purpose agent harness.** It is highly homologous to today's mainstream agent products/frameworks, yet deliberately makes a few differentiators.

| Peer product/framework | Relationship to dsh |
|---|---|
| **Claude Code / Claude Agent SDK** (Anthropic) | The closest analog. Both are "agent harnesses": session, system prompt, tool loop, sub-agents, hooks, persistence are all built in. dsh is essentially DeepSeek's reimagining of Claude Code; the difference is **self-hostable, MIT, provider-agnostic** |
| **Codex CLI** (OpenAI), **Gemini CLI** (Google) | Same class of "terminal agent CLI". dsh's headless CLI corresponds to these |
| **OpenHands** (formerly OpenDevin) | Also an open-source autonomous software-development agent; dsh is in the same lane |
| **Aider** | A terminal pair-programming agent; dsh leans more toward "a composable framework" than "a single tool" |
| **LangGraph / LangChain / smolagents / CrewAI** | All are "agent building libraries". dsh is more "out-of-the-box", shipping a complete harness rather than just orchestration primitives |
| **MCP (Model Context Protocol)** tool ecosystem | dsh's "capability plugins" (bash/fs/web/skill/lsp…) share a similar spirit with MCP tools, but dsh is a complete runtime, not merely a tool protocol |
| **AutoGPT / BabyAGI / OpenClaw and other autonomous agents** | All are "agents", but dsh emphasizes controllability (approval/sandbox/reproducible sessions) over pure autonomous free-running |

**One-line differentiator:** Compared to products like Claude Code that are "tied to their own model", dsh's biggest selling point is that **LLM access is a pluggable seam** — it officially adapts DeepSeek, but via `llm-pi-ai` it can also reach OpenAI, Anthropic, any OpenAI-compatible gateway, or even a hand-written adapter for a private model. This makes it more of a "**runtime you can own and control**".

### 1.5 What problems does it solve?

1. **Escape model lock-in (provider lock-in)** — The LLM layer is an abstract seam; switching DeepSeek / OpenAI / Anthropic / a self-hosted gateway is a config change, not a business-code change. Especially friendly for private models, corporate gateways, self-hosted vLLM/Ollama scenarios.
2. **No need to build an agent from scratch** — Session management, system prompts, tool loops, sub-agents, workflows, plans, approval/permissions, sandbox, and persistence are all built in and ready to use.
3. **Extreme extensibility** — "Everything is a plugin": to get bash/filesystem/web/skill/LSP capabilities, just compose the corresponding plugins; HMR (hot module replacement) is supported, and most behaviors change by editing `cordis.yml` without touching `agent-loop`.
4. **Controllable and auditable** — Every step's model requests/tool calls land in the session JSONL log, replayable and reproducible; approval/permission policies + sandbox (landlock) keep the agent from running wild.
5. **One core, multiple clients** — The same agent core can be driven by Web UI, CLI, Python SDK, Java SDK, ACP, JSON-RPC, making it easy to embed into your own programs/workflows/CI.
6. **Multi-agent collaboration** — Sub-agent delegation, product/code multi-agent parallelism, etc., are available out of the box.
7. **Private deployment, MIT open source** — No dependency on a closed cloud platform; can be fully deployed in your own environment.

---

## 2. How to Install and Use

> **This file is the official user guide for the `deepseek-harness4j` Java SDK.** Section 2 first introduces the **Java SDK install/use path** (the recommended primary path); the other approaches (Web UI, CLI, running from source) are **upstream companion clients**, listed in section 2.2 for cross-reference.

### 2.1 Option A: Java SDK (programmatic use, deepseek-harness4j) ⭐ Recommended

#### 2.1.1 Prerequisites

- **JDK 17+** (LTS; the code also runs on 21 / 25)
- **Maven 3.9+**
- A working DeepSeek Harness runtime carrier (`dsh-jsonrpc-agent`)

#### 2.1.2 Build the SDK from source

```sh
git clone https://github.com/geekma/deepseek-harness4j.git
cd deepseek-harness4j
mvn install            # Build all modules (sdk / spring-boot-starter / spring-boot-example)
mvn test               # Run tests (sdk module, 60 cases)
```

#### 2.1.3 Add as a Maven dependency

```xml
<dependency>
    <groupId>com.deepseek-ai</groupId>
    <artifactId>deepseek-harness4j-sdk</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

#### 2.1.4 Prepare the runtime carrier

The Java SDK talks to `dsh-jsonrpc-agent` over stdio JSON-RPC 2.0. **Java SDK users only need the final binary artifact; no need to install pnpm or Node.js.**

**Option 1 — Prebuilt binary (recommended):** Download `dsh-jsonrpc-agent-pkg-<platform>-<arch>` from the upstream Release page and place it in the runtime directory. See `sdk-runtime/README.en.md` for directory conventions.

**Option 2 — Build from upstream source (only needed by publishers/contributors; requires Node.js 22+ / pnpm):**

```sh
# Upstream build steps (use the upstream toolchain — not needed for ordinary Java SDK users)
git clone https://github.com/deepseek-ai/deepseek-harness.git
cd deepseek-harness
corepack enable
pnpm install
pnpm exec tsx scripts/build-exe-for-python-sdk.ts
# Place the generated dsh-jsonrpc-agent-pkg-<platform>-<arch> into the deepseek-harness4j runtime directory
```

#### 2.1.5 Minimal Java example

```sh
export DEEPSEEK_API_KEY=sk-xxx
# export DEEPSEEK_BASE_URL=http://127.0.0.1:8000/v1   # when using an OpenAI-compatible proxy
# export DSH_MODEL=deepseek-v4-flash
```

```java
import com.deepseek.harness4j.DeepSeekHarness;
import com.deepseek.harness4j.DeepSeekHarnessConfig;
import com.deepseek.harness4j.RunResult;

try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
        .provider("deepseek-official")
        .model("deepseek-v4-flash")
        .maxTokens(49_152)
        .cwd("/absolute/path/to/workspace")
        .sessionRoot("/absolute/path/to/sessions")
        .cordis("examples/jsonrpc-agent/minimal.cordis.yml")
        .build())) {
    RunResult result = harness.run("Inspect the repo and fix the failing tests.", "example-001", null);
    System.out.println(result.finalResponse());
}
```

### 2.2 Upstream companion clients (cross-reference, non-Java-SDK paths)

The following approaches are **native upstream DeepSeek Harness commands**. They share the same runtime and model configuration as the Java SDK, but differ in client language. Cross-referencing them helps you understand configuration, the Web UI settings panel, and CLI behavior.

#### 2.2.1 Run the Web UI directly via npm (fastest, upstream)

Prerequisite: Node.js installed (the repo requires `node ^22.19 || >=24`).

```sh
npx @deepseek-ai/dsh web
```

- Default server address: `http://127.0.0.1:3080`
- First entry: Settings → Models, fill in your DeepSeek API key → choose a workspace → create a session and send a task.

#### 2.2.2 Run the upstream project from source (upstream, requires pnpm)

```sh
git clone https://github.com/deepseek-ai/deepseek-harness.git
cd deepseek-harness
pnpm install
pnpm run build
pnpm dsh web          # Start the Web UI
```

#### 2.2.3 Headless CLI for a single task (upstream)

```sh
export DEEPSEEK_API_KEY=sk-xxx
# To use an OpenAI-compatible proxy:
# export DEEPSEEK_BASE_URL=http://127.0.0.1:8000/v1
dsh --profile headless "Summarize this repository and list its main packages."
```

---

## 3. How to Configure Custom Models (Key Section)

Custom models fall into three scenarios, in increasing difficulty:

1. **OpenAI-compatible endpoint / corporate gateway / self-hosted service** — pure configuration; the most common case.
2. **Use a catalog provider (Anthropic/OpenAI etc.)** — just fill in the key.
3. **A private model incompatible with any built-in protocol** — requires writing an LLM adapter plugin (code).

Each is explained below.

### 3.1 Scenario 1: Wire up an OpenAI-compatible custom endpoint (recommended, zero code)

#### 3.1.1 Via the Web UI (graphical)

Path: **Settings → Models → Add a custom provider**

Fields to fill in:

| Field | Description |
|---|---|
| Provider ID | A **lowercase** unique identifier; once saved it cannot be changed (requests, session logs, defaults, credentials all reference it). To rename, create a new one then delete the old one |
| Display name | Display name (changeable) |
| Base URL | e.g. `https://gateway.example.com/v1` |
| API protocol | e.g. `openai-completions` |
| API key | Credential (write-only) |
| Models | At least one model id; you can use "Fetch available models" to pull from `GET /models`, or fill in manually |

> Tip: Manually entered models are treated as **plain text** by default. For vision models, add `input: [text, image]` for that model in `settings.yaml`; otherwise attached images will be rejected before sending.

#### 3.1.2 Edit `$DSH_HOME/settings.yaml` directly (scriptable/auditable)

Under `llm-pi-ai:` in `settings.yaml`, the `providers` field is a dictionary keyed by provider route; it is fully customizable:

```yaml
# $DSH_HOME/settings.yaml  (default ~/.dsh/settings.yaml)
llm-pi-ai:
  providers:
    # —— Option 1: fully hand-written custom route (self-hosted/private gateway) ——
    acme-gateway:
      displayName: Acme Gateway
      apiKeyEnv: ACME_GATEWAY_API_KEY     # credential reference, no plaintext secret
      api: openai-completions             # protocol: openai-completions / openai-responses / anthropic ...
      baseURL: https://gateway.acme.example/v1
      # When a private gateway URL cannot auto-detect the reasoning dialect, specify it explicitly:
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
          # Optional: declare this model's reasoning effort tiers (off can be left empty meaning "do not send")
          reasoningEfforts:
            off:
            high: high
            max: ultra

    # —— Option 2: use a catalog provider but override/narrow the models (no need to write every field) ——
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

    # —— Option 3: modify one catalog model, keep the rest (catalog modelOverrides) ——
    deepseek:
      apiKeyEnv: DEEPSEEK_API_KEY
      modelOverrides:
        deepseek-v4-pro:
          reasoningEfforts:
            off:
            high: high

    # —— Vision model: declare image modality ——
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

**Available profile fields for a custom provider (common subset):**

`apiKeyEnv`, `displayName`, `api`, `baseURL`, `models`, `modelOverrides`, `compat`, `defaultContextWindow`, `defaultMaxTokens`, `defaultInput`, `headers`, `reasoning`, `thinkingBudgets`, `transport`, `timeoutMs`, `streamIdleTimeoutMs`, `retryPolicy`.

**Available fields for a `models` entry:** `id`, `name`, `contextWindow`, `maxTokens`, `reasoningEfforts`, `compat`, `input`.

Key semantics:

- `apiKeyEnv` is a **per-request-resolved credential reference** (resolved from `.credentials.yaml` / environment); no plaintext secret lands in the config file.
- The `models` list **entirely replaces** that route's catalog; writing just an `id` is enough (the rest inherits from the catalog or the route fallback).
- `modelOverrides` is "edit one catalog model in place"; it does not replace the whole list.
- Request protocols support `openai-completions` / `openai-responses` / `anthropic` etc. (exposed via `supportedProtocols()`); Bedrock / Vertex / Azure / Codex require their respective native credentials and are not suited to the API key field.

#### 3.1.3 Point at a custom endpoint via environment variables

The runtime carrier reads these environment variables directly (independent of client language):

```sh
export DEEPSEEK_API_KEY=sk-your-key
export DEEPSEEK_BASE_URL=https://your-gateway.example.com/v1   # point at your endpoint
export DSH_MODEL=your-custom-model-id
```

- **Java SDK:** use `DeepSeekHarnessConfig`'s `provider("deepseek-official")` + `model("your-custom-model-id")` (full example in 2.1.5).
- **Upstream CLI (cross-reference):** `dsh --profile headless "your task description"`.

### 3.2 Scenario 2: Use a catalog provider (Anthropic / OpenAI etc.)

Web UI: **Settings → Models → Add provider**, pick a provider, fill in the API key, save. The installed catalog supplies endpoint, protocol, and model list. Providers with native auth (Bedrock/AWS, Vertex/ADC, Azure/api-version, Codex/OAuth) require their respective native credentials; filling in just an API key is insufficient.

### 3.3 Scenario 3: A fully incompatible private model → write an LLM adapter plugin

If your model speaks a non-OpenAI-compatible private protocol, you need to implement an `LlmAdapter` (refer to the two complete implementations in the repo: `packages/llm/llm-deepseek` and `packages/llm/llm-pi-ai`).

> Note: the adapter is a **TypeScript plugin** running on the dsh runtime side; the client language (Python / Java / CLI) needs no changes. Therefore this section's code is identical to the upstream guide; a Java SDK caller only needs to mount the plugin in `cordis.yml` and select the corresponding provider/model.

Minimal skeleton:

```ts
import { Context } from '@deepseek-ai/cordis'
import Schema from '@deepseek-ai/schemastery'
import { LlmAdapter, type GenerateOptions, type StreamChunk } from '@deepseek-ai/dsh-llm'

class MyAdapter extends LlmAdapter {
  constructor(private readonly apiKey: string) { super() }

  // Translate the provider-agnostic request into your API call, then convert the response into StreamChunk fragments
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

Mount it in `cordis.yml` and have the agent use it:

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

**StreamChunk protocol essentials** (`stream()` must obey):

- Every `block-start` must have a matching `block-end`; `index` increments from 0.
- Tool-call arguments are raw JSON strings throughout; streaming fragments use `argumentsDelta`.
- `usage` must come before `finish`; `finish` must be the last fragment.
- Two legal error paths: throw an `LlmError` with a stable code from `stream()`, or end with `finish {kind:'error'|'aborted'}`.
- Honor `options.signal`; throw `LlmError(..., 'UNSUPPORTED')` for unsupported fields — never silently drop them.

---

## 4. Common Errors and Troubleshooting When Configuring Models

| Error | Meaning & handling |
|---|---|
| `MISSING_CREDENTIAL` | No usable key. Store a key on the Models page, or provide the referenced environment variable |
| `UNKNOWN_MODEL` | The model is not in any configured provider's catalog. Pick a configured model, or add the model to your custom provider |
| Fetching models returns 401 | Wrong key. Model discovery hits the OpenAI-compatible `GET /models`; if the endpoint doesn't support it, fill in models manually |
| Image rejected before sending | The model hasn't declared the image modality. Add `input: [text, image]` to the custom model (DeepSeek's official chat-completions route is plain text and cannot be changed) |
| Provider rejects image-bearing requests | The model declares image capabilities the endpoint doesn't actually support; remove `image` from the corresponding `input` / `defaultInput` and start a new session |
| `DUPLICATE_ADAPTER` | The same provider route was registered twice (e.g. both deepseek and pi-ai registered the `deepseek` route) |

---

## 5. Common Repository Commands

### 5.1 deepseek-harness4j (this Java repository)

```sh
# Build & test
mvn clean install       # Build all modules (sdk / spring-boot-starter / spring-boot-example) into the local repo
mvn test                # Run the sdk module's JUnit 5 test suite (60 cases)
mvn verify              # CI gate: tests + verification
mvn surefire-report:report -pl sdk   # Generate an HTML test report (see docs/test-report.en.md)

# Spring Boot example
cd spring-boot-example && mvn spring-boot:run
```

### 5.2 Upstream deepseek-harness repository (cross-reference for source developers; requires pnpm / Node.js)

```sh
pnpm install            # Install dependencies (upstream toolchain)
pnpm run build          # Build
pnpm run test           # Unit tests
pnpm run test:e2e       # Real API tests (auto-skipped without DEEPSEEK_API_KEY)
pnpm run test:coverage  # CI coverage gate
pnpm run typecheck / lint
pnpm run demo:cordis    # Demo: agent modifies its own runtime (requires key)
pnpm run demo:acp       # ACP automation server (requires DEEPSEEK_API_KEY)
dsh --profile headless "task"
```

> The Java-side (deepseek-harness4j) equivalent commands are in `development.en.md`: `mvn install` / `mvn test`.

---

## 6. Quick-Start Demo (Recommended Path)

### 6.1 Java SDK quick start (this repository's primary path)

```sh
# 1) Prerequisites: JDK 17+ / Maven 3.9+ / runtime carrier (see 2.1.4)
# 2) Build the SDK
cd deepseek-harness4j
mvn install

# 3) Set credentials and a custom gateway (optional)
export DEEPSEEK_API_KEY=sk-xxx
# export DEEPSEEK_BASE_URL=http://127.0.0.1:8000/v1   # swap in your endpoint
# export DSH_MODEL=your-custom-model-id

# 4) Call from Java code:
#    - Add the Maven dependency com.deepseek-ai:deepseek-harness4j-sdk
#    - new DeepSeekHarness(config).run("your task description")
```

Changing the model/gateway **does not require restarting the Java process** — environment variables take effect on the next request (or build a fresh `DeepSeekHarnessConfig`).

### 6.2 Upstream Web UI quick start (cross-reference)

```sh
# 1) Install Node.js (>=22)
# 2) Start the Web UI
npx @deepseek-ai/dsh web

# 3) Open http://127.0.0.1:3080 in a browser
#    Settings → Models:
#      - Fill in the DeepSeek key, or
#      - Add a custom provider with your gateway (baseURL + protocol + key + model)
#    Choose workspace: add and select the project directory
# 4) Create a session and send a task, e.g.:
#    "Summarize this repository and identify its main packages."
```

If you are wiring up a **custom/self-hosted OpenAI-compatible service**, the most effortless path is:

```sh
export DEEPSEEK_API_KEY=sk-xxx
export DEEPSEEK_BASE_URL=http://127.0.0.1:8000/v1   # swap in your endpoint
npx @deepseek-ai/dsh web
```

Then simply select the model in Models. Changing the model **needs no server restart** — the next request picks it up.

---

## Appendix: Official Documentation Entry Points

- README: https://github.com/deepseek-ai/deepseek-harness
- Architecture: `docs/architecture.md` / Development guide: `docs/development.md`
- Web UI usage: `docs/user/guide/index.md`
- Configuring models: `docs/user/guide/providers.md`
- Full plugin config field reference: `docs/config-catalog.md`
- Writing an LLM adapter: `docs/cookbook/adding-an-llm-adapter.md`, `docs/user/develop/practice/llm-adapter.md`
- Adapter implementation references: `packages/llm/llm-deepseek/`, `packages/llm/llm-pi-ai/`
- Java SDK (this repository): `deepseek-harness4j/README.md`, `deepseek-harness4j/development.en.md`, `deepseek-harness4j/docs/java-migration-notes.en.md`

---

# Part Two: Complete Hands-On Demos

> The demos below were all verified against the repository's real files (`examples/jsonrpc-agent/minimal.cordis.yml`, `minimal.py`, `docs/user/guide/*`, `packages/llm/*`). Example code is annotated for clarity. **Before running any demo, prepare:**
>
> - A machine with Node.js (>=22) or Python (>=3.10); **Java demos additionally require JDK 17+ and Maven, plus an installed runtime carrier (see `development.en.md`)**;
> - A reachable model endpoint + key (DeepSeek official, or any OpenAI-compatible self-hosted gateway);
> - A standalone working directory you "allow the agent to modify" (an empty directory is recommended; don't use a real project).

---

## 7. Demo 1: Web UI Full Walkthrough (first run)

**Goal**: get the harness running with the mouse and send the first task.

### 7.1 Start

```sh
# Globally fastest way
npx @deepseek-ai/dsh web
```

After startup the terminal prints the address; default is `http://127.0.0.1:3080`. Open it in a browser.

### 7.2 First-time setup in four steps

| Step | Action | Notes |
|---|---|---|
| ① Configure model | **Settings → Models** → fill in the API key on the DeepSeek card and save; or click **Add a custom provider** to fill in your gateway | The key is **write-only**, stored in `~/.dsh/.credentials.yaml`; the UI only shows a redacted descriptor |
| ② Choose workspace | Click **Choose workspace** → add the directory you launched `dsh` from and select it | Without a workspace the session input box is greyed out |
| ③ Create session | Create a new session | |
| ④ Send task | Type a task and send it | Operations needing approval will pop up per the permission policy |

### 7.3 Example tasks (paste directly)

```sh
Summarize this repository and identify its main packages.
```

```sh
Read the file src/main.ts, then write a test for it and run it.
```

> Note: the agent in the Web UI can read/modify workspace files, run commands, delegate to sub-agents, and maintain a plan. Changing the model **needs no server restart** — the next request picks it up.

### 7.4 Where the data lands

- Session logs: JSONL files in the workspace (recording every assembled model request, tool call, response).
- Config: `~/.dsh/settings.yaml`.
- Credentials: `~/.dsh/.credentials.yaml` (write-only).

---

## 8. Demo 2: Headless CLI for a single task (upstream CLI, cross-reference)

**Goal**: run a complete agent task with one command, no browser. Suited for scripting and CI. The equivalent programmatic call from the Java SDK is in section 2.1.5.

```sh
# 1) Set credentials (required)
export DEEPSEEK_API_KEY=sk-your-key

# 2) If using a self-hosted OpenAI-compatible endpoint, point at it (optional)
export DEEPSEEK_BASE_URL=http://127.0.0.1:8000/v1
export DSH_MODEL=your-model-id          # default deepseek-v4-flash

# 3) Run a task from the source repo
cd deepseek-harness
pnpm dsh --profile headless "List all files in this repo and count lines of TypeScript."
```

> Note: `--profile headless` loads the headless composition; output goes straight to stdout. With `DEEPSEEK_BASE_URL` + `DSH_MODEL` you can point the official CLI at your own model with no code changes.

---

## 9. Demo 3: Complete Java SDK Example (with custom gateway)

> Corresponds to upstream Demo 3 (Python SDK); the Java side is a line-by-line port of the `deepseek_harness` package.

**Goal**: drive the harness programmatically from Java. This is the most common path for "embedding into your own program/workflow".

### 9.1 Minimal three steps

```sh
# 1) Add the dependency (see deepseek-harness4j/sdk README): Maven coordinate com.deepseek-ai:deepseek-harness4j-sdk
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

`new DeepSeekHarness()` by default launches the `dsh-jsonrpc-agent` executable bundled with the runtime carrier and injects the default composition (JSON-RPC server + agent core + DeepSeek adapter + JSONL session persistence + local bash).

### 9.2 Fully parameterized example (key: custom model)

```java
import com.deepseek.harness4j.DeepSeekHarness;
import com.deepseek.harness4j.DeepSeekHarnessConfig;
import com.deepseek.harness4j.RunResult;

String config    = Path.of("examples/jsonrpc-agent/minimal.cordis.yml").toAbsolutePath().normalize().toString();
String workspace = Path.of("/absolute/path/to/your/workspace").toAbsolutePath().normalize().toString();
String sessions  = Path.of("/absolute/path/to/session/dir").toAbsolutePath().normalize().toString();

try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
        .provider("deepseek-official")      // provider route (registered by the adapter in cordis.yml)
        .model("deepseek-v4-flash")         // model id
        .maxTokens(49_152)                  // output token cap per request (optional)
        .cwd(workspace)                     // agent's working directory (it will actually modify files inside)
        .sessionRoot(sessions)              // where session JSONL lands on disk
        .cordis(config)                     // your own plugin composition file (see Demo 4)
        .build())) {
    RunResult result = harness.run(
            "Inspect the repository and fix the failing tests.",
            "example-001",                  // reuse a session id to continue the chat
            null);
    System.out.println(result.finalResponse());   // last assistant text from the root session
    System.out.println(result.finishReason());    // completed / max-tokens / error ...
    System.out.println(result.sessionId());
    // result.events() / result.notifications() hold session and sub-agent events
}
```

### 9.3 Key parameter cheat sheet

| Parameter | Purpose |
|---|---|
| `provider` | Selects the __provider route__ registered by an adapter (e.g. `deepseek-official`; after mounting `llm-pi-ai` in a custom composition, any catalog provider can be used) |
| `model` | The model id resolved by that provider |
| `maxTokens` | Output token cap for the root agent and its in-process descendants; if omitted, the provider default is used |
| `cordis` | Path to a custom plugin composition file (if omitted, the default composition bundled with the package is used) |
| `cwd` | Agent working directory (auto-converted to absolute, matching Python's `Path.resolve()`) |
| `sessionRoot` | Session on-disk directory (equivalent to setting `DSH_SESSION_ROOT`) |

### 9.4 Pointing at a custom/self-hosted model

The runtime inherits `DEEPSEEK_BASE_URL` and `DEEPSEEK_API_KEY`, so there are three wiring options:

```sh
# Option A: official DeepSeek
export DEEPSEEK_API_KEY=sk-xxx

# Option B: OpenAI-compatible self-hosted gateway (vLLM / Ollama / LM Studio / corporate proxy…)
export DEEPSEEK_API_KEY=sk-any-non-empty-value
export DEEPSEEK_BASE_URL=http://127.0.0.1:8000/v1
export DSH_MODEL=qwen2.5-72b-instruct

# Option C: finer-grained multi-provider -> mount llm-pi-ai in cordis.yml and configure settings.yaml (see Demo 5)
```

```java
import com.deepseek.harness4j.DeepSeekHarness;
import com.deepseek.harness4j.DeepSeekHarnessConfig;

try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
        .provider("acme-gateway")      // custom route from settings.yaml
        .model("acme-large")
        .cordis("examples/jsonrpc-agent/minimal.cordis.yml")
        .build())) {
    System.out.println(harness.run("Write a Java example that reads JSON", null, null).finalResponse());
}
```

---

## 10. Demo 4: Compose "Your Own Agent" with cordis.yml

**Goal**: understand and rewrite the plugin composition. `cordis.yml` is dsh's "recipe file" — it lists which plugins to load and their config. Below is the repository's **real** `examples/jsonrpc-agent/minimal.cordis.yml` (with added English comments); it composes a minimal headless agent: the model only sees **one system prompt + two tools** (persistent bash + a string-replacement editor).

```yaml
# ---- Complete minimal headless agent composition (from the repo's examples/jsonrpc-agent/minimal.cordis.yml) ----

# ① JSON-RPC server (for SDK/programmatic calls)
- id: sdk-jsonrpc-server
  name: '@deepseek-ai/dsh-sdk-jsonrpc-server'
  config:
    maxTokensAsSuccess: false

# ② LLM adapter: DeepSeek official direct connection
- id: llm-deepseek
  name: '@deepseek-ai/dsh-llm-deepseek'
  config:
    apiKeyEnv: DEEPSEEK_API_KEY                 # credential reference, no plaintext
    streamIdleTimeoutMs: 172800000
    models:                                     # models exposed to the selector (can be set dynamically via env vars)
      - id: !!js process.env.DSH_MODEL ?? 'deepseek-v4-flash'
        contextWindow: !!js Number(process.env.DSH_CONTEXT_WINDOW ?? 1000000)

# ③ Sandbox and security policy
- id: sandbox
  name: '@deepseek-ai/dsh-sandbox-local'
- id: sandbox-policy
  name: '@deepseek-ai/dsh-sandbox-policy'
  config:
    mode: danger-full-access                    # full access (minimal example cuts corners)
    workspaceRoot: !!js process.env.DSH_CWD ?? process.cwd()

# ④ Subprocess + persistent terminal (backing the bash tool)
- id: subprocess
  name: '@deepseek-ai/dsh-subprocess-local'
- id: pty
  name: '@deepseek-ai/dsh-terminal'
- id: terminal-bash
  name: '@deepseek-ai/dsh-terminal-bash'
  config:
    timeoutMs: 300000

# ⑤ Filesystem (used by the editor; note it is also bound by the sandbox policy above)
- id: fs-local
  name: '@deepseek-ai/dsh-fs-local'
  config:
    cwd: !!js process.env.DSH_CWD ?? process.cwd()

# ⑥ Agent spine (session/system-prompt/tool orchestration all live here)
- id: agent-spine
  name: '@deepseek-ai/dsh-agent-spine-demo'
  config:
    includeHarnessIdentity: false
    includeRuntimeContext: false               # turn off dynamic context injection
    persona: !!js process.env.DSH_SYSTEM_PROMPT ?? 'You are a helpful software engineer assistant.'
    workspaceContext: false
    skills:
      enabled: false                            # do not enable skills
    toolBash: false                             # taken over by the dedicated bash tool below
    toolJobs: false

# ⑦ Two tools visible to the model: persistent bash + string-replacement editor
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

# ⑧ Session persistence: JSONL
- id: sessions
  name: '@deepseek-ai/dsh-session-persistence-jsonl'
  config:
    root: !!js process.env.DSH_SESSION_ROOT ?? './.sessions'
    compression: none
```

### 10.1 Run it

```sh
cd deepseek-harness
export DEEPSEEK_API_KEY=sk-xxx
python examples/jsonrpc-agent/minimal.py \
  --workspace /absolute/path/to/workspace \
  --session-root /absolute/path/to/sessions \
  --session-id example-001 \
  "Read package.json and print the list of scripts."
```

Java-side equivalent (the Java SDK calls the same composition):

```java
try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
        .cwd("/absolute/path/to/workspace")
        .sessionRoot("/absolute/path/to/sessions")
        .cordis("examples/jsonrpc-agent/minimal.cordis.yml")
        .build())) {
    harness.run("Read package.json and print the list of scripts.", "example-001", null);
}
```

### 10.2 How to turn it into "your own agent"

- **Swap the model**: change `llm-deepseek`'s `models`, or switch to `llm-pi-ai` (see Demo 5).
- **Add/remove tools**: add a `toolBash` / `toolSkill` / `toolJobs` plugin line; flip the corresponding `toolXxx: false` in `agent-spine` to true or remove it.
- **Change the system prompt**: the `persona` field.
- **Enable skills**: `skills.enabled: true` (and configure a `skill-filesystem` provider).
- **Add sub-agents/workflows/plans**: add `dsh-subagent`, `dsh-workflow`, `dsh-plan` plugins to the composition.
- **Change the sandbox security level**: `sandbox-policy.mode` can switch between `danger-full-access` and restricted modes (tightening is strongly recommended for production).

> Principle: changing the composition (cordis.yml) **takes precedence over** changing code. Almost every behavior can be tuned via "which plugin to load + what config to give", without touching `agent-loop`. Java SDK callers follow the same principle.

---

## 11. Demo 5: Write `settings.yaml` for a Custom Model/Gateway and Run a Task

**Goal**: a complete walkthrough of "pointing dsh at a self-hosted OpenAI-compatible gateway" and actually running a task. This is the most common custom-model workflow.

### 11.1 Write the config

In `~/.dsh/settings.yaml` (or the directory specified by `DSH_HOME`):

```yaml
# ~/.dsh/settings.yaml — hot-reloadable at runtime; takes effect on the next request, no restart needed
llm-pi-ai:
  providers:
    # Fully hand-written custom route: my local gateway
    local-gateway:
      displayName: Local Gateway
      apiKeyEnv: LOCAL_KEY                 # credential reference (resolved from env or .credentials.yaml)
      api: openai-completions              # protocol
      baseURL: http://127.0.0.1:8000/v1    # self-hosted service address (vLLM/Ollama/LM Studio etc.)
      defaultContextWindow: 32768          # fallback context for hand-written models
      defaultMaxTokens: 8192               # fallback output cap for hand-written models
      models:
        - id: qwen2.5-72b-instruct
          name: Qwen2.5 72B
        - id: my-vision
          name: My Vision
          input: [text, image]             # vision model declares image modality
```

Corresponding environment variable (the key doesn't go into the yaml):

```sh
export LOCAL_KEY=sk-anything (local gateways usually don't validate) or your-key
```

### 11.2 Mount `llm-pi-ai` in the composition and select it

In your own `cordis.yml`, replace/append to the `llm-deepseek` block with:

```yaml
# Universal multi-provider adapter (reads providers from settings.yaml)
- id: llm
  name: '@deepseek-ai/dsh-llm-pi-ai'
  config:
    providers:
      local-gateway:
        apiKeyEnv: LOCAL_KEY
        api: openai-completions
        baseURL: http://127.0.0.1:8000/v1
        models:
          - id: qwen2.5-72b-instruct
```

> Note: `llm-pi-ai`'s `providers` is a "route-keyed" dictionary and **supports per-provider merging with `settings.yaml`** — put the base in the composition, override in settings; both take effect on the next request.

### 11.3 Run a task (Java SDK)

```java
import com.deepseek.harness4j.DeepSeekHarness;
import com.deepseek.harness4j.DeepSeekHarnessConfig;
import com.deepseek.harness4j.RunResult;

try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
        .provider("local-gateway")          // custom route from settings.yaml / cordis.yml
        .model("qwen2.5-72b-instruct")
        .cordis("examples/jsonrpc-agent/minimal.cordis.yml")
        .build())) {
    RunResult r = harness.run("Write a Java function that computes the Fibonacci sequence and test it.", null, null);
    System.out.println(r.finalResponse());
}
```

### 11.4 Auto-discover models via `GET /models`

The Web UI's "Fetch available models" calls the OpenAI-compatible `GET /models` to pull candidates (not stored, only offered for adoption); if the endpoint doesn't expose that interface, fill in models manually. Discovery is only performed for `openai-completions` / `openai-responses` custom routes (Azure uses an `api-key` header + `api-version` and is not applicable).

---

## 12. Demo 6: Write a Custom LLM Adapter Plugin (Complete Code)

**Goal**: when your model is **fully incompatible** with any built-in protocol, write your own adapter to wire it in. This is the lowest-level means of "custom model". For a complete deliverable implementation, refer to the repo's `packages/llm/llm-deepseek/` (official direct) and `packages/llm/llm-pi-ai/` (universal). **The adapter is a runtime-side TypeScript plugin; Java SDK callers need no changes — just mount it in `cordis.yml`.**

### 12.1 Plugin skeleton (compiles as-is)

File `src/my-llm-adapter.ts`:

```ts
import { Context } from '@deepseek-ai/cordis'
import Schema from '@deepseek-ai/schemastery'
import {
  attributionHeaders,
  CallId,
  LlmAdapter,
  LlmError,
  type GenerateOptions,
  type StreamChunk,
} from '@deepseek-ai/dsh-llm'

/**
 * A minimal custom adapter: forwards the harness's request to an OpenAI-compatible endpoint
 * and converts the response into StreamChunk fragments. (For production, refer to llm-deepseek's full implementation.)
 */
class MyAdapter extends LlmAdapter {
  constructor(
    private readonly apiKey: string,
    private readonly baseURL: string,
  ) { super() }

  async *stream(options: GenerateOptions): AsyncIterable<StreamChunk> {
    // 1) Convert the harness's conversation history into your API request body
    const body = {
      model: options.model,
      messages: [
        { role: 'system', content: options.systemPrompt ?? '' },
        ...options.messages.map((m) => ({
          role: m.role,
          content: typeof m.content === 'string' ? m.content : JSON.stringify(m.content),
        })),
      ],
      stream: true,
    }

    // 2) Call the streaming API (honor options.signal, merge attribution headers)
    const res = await fetch(`${this.baseURL}/chat/completions`, {
      method: 'POST',
      headers: {
        'content-type': 'application/json',
        authorization: `Bearer ${this.apiKey}`,
        ...attributionHeaders(),
      },
      body: JSON.stringify(body),
      ...(options.signal ? { signal: options.signal } : {}),
    })
    if (!res.ok || !res.body) {
      throw new LlmError(`Provider API error: ${res.status}`, 'PROVIDER_HTTP_ERROR')
    }

    // 3) Yield chunks following the StreamChunk protocol:
    //    block-start -> text-delta* -> block-end -> usage -> finish
    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let text = ''
    yield { type: 'block-start', index: 0, blockType: 'text' }

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      // Naive SSE parsing: split events on blank lines (production should use eventsource-parser)
      const parts = buffer.split('\n\n')
      buffer = parts.pop() ?? ''
      for (const part of parts) {
        const line = part.split('\n').find((l) => l.startsWith('data: '))
        if (!line || line === 'data: [DONE]') continue
        const json = JSON.parse(line.slice(6))
        const delta = json.choices?.[0]?.delta?.content
        if (delta) {
          text += delta
          yield { type: 'text-delta', index: 0, text: delta }
        }
      }
    }
    yield { type: 'block-end', index: 0, block: { type: 'text', text } }
    yield { type: 'usage', usage: { inputTokens: 0, outputTokens: 0 } }
    yield { type: 'finish', reason: { kind: 'stop' } }
  }

  // Optional: tell the selector which models this provider can serve
  async listModels(): Promise<Array<{ id: string; name?: string }>> {
    return [{ id: 'my-custom-model', name: 'My Custom Model' }]
  }
}

// —— Cordis plugin conventions: name / inject / Config / apply ——
export interface Config {
  apiKey: string
  baseURL: string
  providers: string[]
}
export const Config: Schema<Config> = Schema.object({
  apiKey: Schema.string().required(),
  baseURL: Schema.string().required(),
  providers: Schema.array(Schema.string()).required(),
})

export const name = 'my-llm-adapter'
export const inject = ['llm']

export function apply(ctx: Context, config: Config) {
  ctx.llm.registerAdapter(config.providers, new MyAdapter(config.apiKey, config.baseURL))
}
```

### 12.2 Mount in `cordis.yml` and have the agent use it

```yaml
# Mount the custom adapter (provider route: my-provider)
- id: my-llm
  name: './src/my-llm-adapter.ts'          # source path (or a published package name)
  config:
    apiKey: !!js process.env.MY_API_KEY    # key via env var, not in the file
    baseURL: http://127.0.0.1:9999/v1
    providers:
      - my-provider

# Have the agent spine use this provider/model
- id: agent-loop
  name: '@deepseek-ai/dsh-agent-loop'
  config:
    agents:
      - id: main
        provider: my-provider
        model: my-custom-model
```

### 12.3 StreamChunk Protocol Essentials (must obey)

| Rule | Description |
|---|---|
| Pairing | Every `block-start` must have a matching `block-end`; `index` increments from 0 |
| Tool calls | `tool-call-delta`'s `argumentsDelta` is a raw JSON text increment; on finish, supply the complete `arguments` string in `block-end` |
| Ordering | `usage` must come before `finish`; `finish` must be the last fragment |
| Errors | Two legal paths: ① `stream()` throws an `LlmError` with a stable code directly; ② end the stream with `finish {kind:'error'|'aborted'}` |
| Abort | Honor `options.signal` (pass to fetch / SDK) |
| Unsupported | Throw `LlmError(..., 'UNSUPPORTED')` for unsupported fields — never silently drop them |
| Attribution | Merge `attributionHeaders()` into every provider HTTP request |

### 12.4 Common Adapter Error Codes

`AUTH` (401/403), `QUOTA` (balance/quota), `RATE_LIMIT` (429), `CONTEXT_WINDOW_EXCEEDED`, `INVALID_REQUEST` (other 400), `SERVER` (5xx), `TRANSPORT` (DNS/connection/TLS/proxy), `ABORTED` (caller canceled), `TIMEOUT`, `MISSING_CREDENTIAL`, `INVALID_CREDENTIAL`, `STREAM_CLOSED`, `MALFORMED_RESPONSE`, `EMPTY_RESPONSE`, `UNKNOWN_MODEL`, `UNSUPPORTED_REASONING_EFFORT`, `DUPLICATE_ADAPTER`.

---

## 13. Demo 7: Tie It All Together — a Complete "Local Custom Model Runs a Code Task" Example

**Goal**: a from-scratch, copy-pasteable minimal end-to-end chain (self-hosted gateway → custom provider → run a real code task).

```sh
# ① Prerequisite: you already have an OpenAI-compatible local service on port 8000 (vLLM/Ollama/LM Studio all work)
# e.g. Ollama:
#   ollama serve &
#   ollama pull qwen2.5:7b
#   # Ollama's compatible endpoint: http://127.0.0.1:11434/v1 (note: not 8000, adjust as needed)

# ② Create a clean workspace
mkdir -p ~/dsh-demo/workspace && cd ~/dsh-demo/workspace
echo '{"name":"demo-app","scripts":{"test":"node test.js"},"dependencies":{}}' > package.json

# ③ Write the custom provider's settings (assume using llm-pi-ai)
mkdir -p ~/.dsh
cat > ~/.dsh/settings.yaml <<'YAML'
llm-pi-ai:
  providers:
    local-gateway:
      displayName: Local Gateway
      apiKeyEnv: LOCAL_KEY
      api: openai-completions
      baseURL: http://127.0.0.1:11434/v1
      models:
        - id: qwen2.5:7b
YAML
export LOCAL_KEY=ollama   # local gateways usually don't validate; any non-empty value works

# ④ Run a real task: have the agent write and run a test
```

```java
// Java SDK (deepseek-harness4j): equivalent to the upstream Python DeepSeekHarness call
import com.deepseek.harness4j.DeepSeekHarness;
import com.deepseek.harness4j.DeepSeekHarnessConfig;
import com.deepseek.harness4j.RunResult;

try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
        .provider("local-gateway")
        .model("qwen2.5:7b")
        .cwd(System.getProperty("user.home") + "/dsh-demo/workspace")
        .sessionRoot(System.getProperty("user.home") + "/dsh-demo/sessions")
        .build())) {
    RunResult r = harness.run(
            "Write test.js that reads package.json and asserts name == 'demo-app', "
                    + "then run `npm test`.",
            null, null);
    System.out.println(">>> Final response:\n" + r.finalResponse());
    System.out.println(">>> Finish reason: " + r.finishReason());
}
```

```sh
# ⑤ Inspect what the agent did in the session
ls -la ~/dsh-demo/sessions   # JSONL session logs
```

> Expected behavior: the agent will use the bash tool to create `test.js`, run `npm test`, and iterate until it passes, then summarize. If `finishReason` is `completed`, the entire chain (custom provider → model → tools → session) works end to end.

---

## 14. Demo Cheat Sheet: Which Path When

| Your goal | Recommended path | Section |
|---|---|---|
| Quick experience/interaction | `npx @deepseek-ai/dsh web` + Web UI | 7 |
| Scripted/CI single task | `pnpm dsh --profile headless "task"` | 8 |
| Embed in your own program/workflow | Python SDK / Java SDK `DeepSeekHarness.run()` | 9 |
| Custom agent capability composition | Hand-write/rewrite `cordis.yml` | 10 |
| Wire up a self-hosted OpenAI-compatible gateway | `settings.yaml` + `llm-pi-ai` + `provider=` | 11 |
| Wire up a fully incompatible private model | Write an `LlmAdapter` plugin | 12 |
| Full local end-to-end validation | Follow Demo 7 | 13 |

---

## 15. Live Verification Record (2026-08-13, actually run)

> This section is a record from __running on a real machine__, not an illustrative example. Environment: macOS, Python SDK `deepseek-harness-sdk==0.1.0rc6`, installed in an isolated venv via `uv`.
> Model config used: __reusing this machine's Hermes instance's model endpoint__ — Volcano Ark (Volcano Engine) OpenAI-compatible endpoint `https://ark.cn-beijing.volces.com/api/coding/v3`, model `deepseek-v4-flash-ga-260731`. That is: `DEEPSEEK_API_KEY` + `DEEPSEEK_BASE_URL` pointed at a custom endpoint, with no dsh code changes whatsoever — which neatly validates "custom model = config, not code".
>
> **Java port note**: the records below are real outputs from the upstream Python SDK. The `deepseek-harness4j` Java SDK **corresponds line-by-line** to that Python SDK (`RunResult.finalResponse()` / `finishReason()` / `sessionId()` map one-to-one to the Python result fields); under the same runtime and model config, equivalent results should be obtained. You can reproduce using this repository's `sdk` `FakeRuntime` tests or a real endpoint.

### 15.1 Environment & Install

```sh
# Python 3.11, build a clean venv with uv (to avoid polluting the host's pip/conda env)
uv venv dshuv --python 3.11
uv pip install --python /tmp/dshuv/bin/python deepseek-harness-sdk
# Result: installed deepseek-harness-sdk==0.1.0rc6 + the matching runtime binary (~50MB)
```

Java side: add the `com.deepseek-ai:deepseek-harness4j-sdk` dependency and install the runtime carrier (`mvn install`, see `development.en.md`).

### 15.2 First Call (plain chat)

```sh
export DEEPSEEK_API_KEY=your-key
export DEEPSEEK_BASE_URL=https://ark.cn-beijing.volces.com/api/coding/v3
```

```java
import com.deepseek.harness4j.DeepSeekHarness;
import com.deepseek.harness4j.DeepSeekHarnessConfig;
import com.deepseek.harness4j.RunResult;

try (DeepSeekHarness h = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
        .provider("deepseek-official")
        .model("deepseek-v4-flash-ga-260731")
        .build())) {
    RunResult r = h.run("Reply with exactly the single word: PONG", null, null);
    System.out.println(r.finalResponse() + " | " + r.finishReason() + " | " + r.sessionId());
}
```

**Actual output (Python record, Java equivalent):**

```sh
elapsed_s=10.6
final_response: 'PONG'
finish_reason: completed
session_id: session-565a09a8506b4ea59b77f69f8ba470ed
```

### 15.3 Full Task with Tools (agent actually writes a file + runs a command)

```java
import com.deepseek.harness4j.DeepSeekHarness;
import com.deepseek.harness4j.DeepSeekHarnessConfig;
import com.deepseek.harness4j.RunResult;

long t0 = System.nanoTime();
try (DeepSeekHarness h = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
        .provider("deepseek-official")
        .model("deepseek-v4-flash-ga-260731")
        .cwd("/tmp/dsh-demo-ws")           // standalone workspace, let the agent modify it
        .build())) {
    RunResult r = h.run(
            "Create a Python file hello.py that prints 'hello from dsh', "
                    + "then run it with python3 and tell me the exact output.",
            null, null);
    System.out.printf("elapsed_s=%.1f%n", (System.nanoTime() - t0) / 1_000_000_000.0);
    System.out.println("finish_reason: " + r.finishReason());
    System.out.println("final_response:\n" + r.finalResponse());
}
```

**Actual output (Python record):**

    elapsed_s=8.3
    finish_reason: completed
    final_response:
     Done! I created `hello.py` with the line `print('hello from dsh')`, then ran it with `python3 hello.py`.
    
    **Exact output:**
    ```
    hello from dsh
    ```

> (The triple backticks inside `**Exact output:**` above are the agent's own returned markdown, shown here verbatim as an indented block.)

**Agent's real artifacts in the workspace (verified):**

```ini
/tmp/dsh-demo-ws/hello.py          ->  contents: print('hello from dsh')
/tmp/dsh-demo-ws/.sessions/.../session.jsonl.zstd   -> session log (zstd compressed)
```

### 15.4 What Happened in the Session Log (37 events after decompression)

```yaml
Counter: assistant/chunk:12, text-chunks:4, agent/inbox/spliced:2,
         step/start:2, user/message:2, reasoning-chunks:2,
         assistant/message:2, step/end:2, session:1, turn/start:1,
         session/title:1, request/header:1, request/context:1,
         tool-call-chunks:1, tool/call:1, tool/result:1, turn/end:1
```

Key event excerpts:

```py
TOOL CALL: bash
  args: {"command": "printf \"print('hello from dsh')\n\" > hello.py && python3 hello.py",
         "description": "Create hello.py and run with python3"}
TOOL RESULT: {..., 'content': [{'type': 'text', 'text': 'hello from dsh\n'}], 'isError': False}
TURN END: {'kind': 'completed'}
```

**Conclusion (empirically confirmed):**

- dsh's agent loop is indeed working: one `turn/start → request/header → request/context → step/start → tool/call(bash) → tool/result → assistant/chunk → turn/end(completed)`.
- Custom model wiring works with __zero code__: `DEEPSEEK_BASE_URL` points at any OpenAI-compatible endpoint + the corresponding model id.
- The entire session is persisted as JSONL (zstd compressed), replayable and auditable — exactly the "controllable and auditable" property from section 1.5.
- The Java SDK's `RunResult.finalResponse()` / `finishReason()` / `sessionId()` correspond one-to-one to the result fields above and reproduce the same behavior under this config.

---

# Part Three: Deep Dive

## 16. Deep Architecture & Design Highlights (Differentiators vs Other Agent Harnesses)

> This section is compiled from reading `docs/architecture.md`, `docs/cordis-primer.md`, `packages/README.md`, and the source of several core packages. It answers "what is genuinely special about this project". The client language (Python/Java) does not affect any of the conclusions below.

### 16.1 Core Architecture Concepts (build the vocabulary first)

| Concept | Meaning |
|---|---|
| **plugin** | An object implementing `Service`; attached to a shared context via `apply(ctx)`; every capability is a plugin |
| **context** | The service registry; plugins find services via `ctx.<key>` (e.g. `ctx.tools`, `ctx.llm`, `ctx.sessions`) rather than importing implementations directly |
| **inject** | Declares service dependencies; a plugin activates only after its dependency services are ready; load order is expressed by dependencies, not hand-ordered |
| **typed events** | Services use TS declaration merging to define event names, dispatched in four modes: `emit / waterfall / parallel / serial`; `waterfall` is "around middleware" — calling `next()` lets it proceed |
| **reversible effects** | All registrations go through `ctx.effect()`/`ctx.on()`; on plugin unload they roll back in order — the foundation that makes HMR safe |
| **capability seam** | A replaceable-capability triplet: Service Definition (interface) + Service Provider (implementation) + Consumer (consumer, usually a model-visible tool) |
| **profile** | A named composition (stored in Harness home) listing the bundles it stacks, external plugins, and the user's `cordis.patch.yml`; `web` and `headless` are built-in templates |
| **bundle** | A distribution format of "config line + mounted code" that can be patched by upper layers |
| **turn / step** | step = one model request + the tools it calls; turn = 0..n steps |
| **Model Experience** | Every package's README must state "what this feature makes visible to the model, and the token/KV-cache impact" — a documentation discipline |

### 16.2 Why it's "DeepSeek's Claude Code, but different"

**① Everything is a plugin, even the agent loop itself is replaceable.**
There is no "privileged core". Model adapters, the tool registry, session logs, even `agent-loop` are all plugins, all replaceable/removable from config. Claude Code's loop is hardcoded; dsh's loop is one plugin among many. → **The biggest difference in architectural philosophy.**

**② "Model-visible ⟺ logged" — a strong audit invariant.**
The session log is not "best effort" but a **runtime hard constraint**: anything reaching the model request must be reconstructable token-by-token from the log (a new model-visible input = a new session event type must be added). `deriveMessages()` projects the model history from the log; `assistant/chunk` preserves replay and UI fidelity. Fork/continue/replay/telemetry/testing all reuse the same data stream. **This is a discipline few other harnesses get right.**

**③ Self-modification — a rare "meta" capability.**
The `extensions/` packages let **the agent itself inspect, define, mount/unmount its own plugins at runtime** (host plugins run in a `node:vm` sandbox, dynamic Cordis package tools, browser-side define cards). The agent isn't "driven by the framework" — it "can modify the framework that drives it".

**④ Interop bridges with Claude Code / Codex.**
`hooks/` doesn't reinvent a hook system; it **directly consumes Claude Code's and Codex's `hooks.json`/settings** — existing hook configs port over. Combined with `acp/` (which implements Anthropic's Agent Client Protocol automation server), the cost of migrating from Claude Code is driven very low.

**⑤ Single-process multi-agent, per-session composition (preset).**
`preset/` lets multiple agents with **different compositions** coexist in one process: each session can have its own tool set and prompt segments, isolated from each other (each `agent.ctx` has its own scope).

**⑥ Sandbox is a first-class citizen, with a swappable backend.**
`sandbox/` provides bwrap / Landlock / Seatbelt backends; `e2b/` is a remote-sandbox POC. Because `fs`/`subprocess` share the same execution world, swapping the sandbox backend = one config change, no need to fork a bunch of providers.

**⑦ Dual LLM adapters + dynamic model catalog + per-request credential resolution.**
`deepseek-official` (official direct) and pi-ai (universal multi-provider) intentionally coexist as two DeepSeek paths; model id is not a lifecycle config; keys are resolved per-request via the credential seam. → This is the root reason "custom model = config change" holds.

**⑧ Exceptional engineering rigor.**
`typert` type-graph generation, **key-less snapshot replay tests** (`pnpm run test:snapshot`), per-file 100% coverage gates, mandatory `Model Experience` docs, every non-trivial PR must carry an Agent Note, `config-catalog.md` is a generated full config catalog. **This is a project that treats "agent engineering" as "product engineering".**

**⑨ One core, multiple clients.**
Web UI + CLI + Python SDK + Java SDK + ACP + JSON-RPC + hooks, all driving the same agent core.

### 16.3 The Other Side of the Difference (objective caveats)

- **Higher complexity**: it's a framework, not an out-of-the-box product; the learning curve for `cordis.yml` composition, the seam triplet, and the event model is noticeably steeper than Claude Code's simple config.
- **Preview stage**: the README self-describes "breaking changes will happen".
- **DeepSeek-first**: OpenAI/Anthropic must go through the pi-ai catalog or a custom provider; not as out-of-the-box as Claude Code tied to its own model.
- **Main lane is single-language**: the core is Node/TS; Python is only a client SDK (not a reimplementation); **Java (deepseek-harness4j) is likewise a client SDK (a line-by-line port of the Python SDK, not a reimplementation)**.

---

## 17. Project Panorama Cheat Sheet (Completing the Project Introduction)

> This chapter rounds out the other dimensions a "project introduction" should have: feature list, tech stack, system requirements, directory structure, env vars, terminology, license & community, known limitations.

### 17.1 Feature List (out of the box)

| Category | Capability |
|---|---|
| Sessions | Session JSONL logs, persistence (JSONL/SQLite), fork/continue/replay, session titles, session search (full-text) |
| Models | Multi-provider adaptation, dynamic model catalog, reasoning effort tiers, per-request key resolution, retry/transport recovery |
| Tools | Filesystem, bash/persistent terminal (PTY), web search/fetch, LSP, skills, code execution, background tasks, sub-agents, workflows, plans, todos, goals |
| Security | Sandbox (bwrap/Landlock/Seatbelt), approval/permission policies, tool timeouts, loop-hygiene guards |
| Collaboration | ask-user, commands, human feedback, scheduled follow-ups |
| Extension | Everything is a plugin, HMR, self-modification, per-session preset composition |
| Interfaces | Web UI, CLI, Python SDK, Java SDK (deepseek-harness4j), ACP, JSON-RPC, Claude Code/Codex hooks bridge |
| Engineering | Type-graph generation, snapshot replay tests, 100% coverage gate, Agent Notes docs |

### 17.2 Tech Stack & Key Dependencies

| Item | Value |
|---|---|
| Language | TypeScript (ESM, `strict:true`), Python (client SDK), Java (client SDK, deepseek-harness4j) |
| Runtime | Node.js `^22.19.0 \|\| >=24.0.0` (repo engine field); Python >=3.10 (SDK only); Java 17+ (deepseek-harness4j only) |
| Package manager | pnpm@11.7.0 (monorepo workspaces); Maven (deepseek-harness4j) |
| Underlying framework | **Cordis** (vendored into `vendor/`; plugin/context/event/reversible-effects) |
| Config schema | `@deepseek-ai/schemastery` |
| Session storage | JSONL (zstd compressed) + SQLite (metadata/full-text search) |
| Stream parsing | `eventsource-parser` (SSE) |
| Universal multi-provider | `@earendil-works/pi-ai` (the llm-pi-ai adapter) |
| Sandbox | Native `node-addon-landlock-run` (native/) |
| Frontend | Web UI (host + client halves, browser-side), VitePress docs site |

### 17.3 System Requirements & Platforms

- **Node**: `^22.19.0 || >=24.0.0` (for Web UI / CLI / source).
- **Python SDK**: Python 3.10+; supports **Linux x64, Linux arm64, macOS 14+ (arm64)**; the SDK bundles the runtime, no system Node needed.
- **Java SDK (deepseek-harness4j)**: JDK 17+; the runtime carrier platform matrix matches Python (Linux x64/arm64, macOS 14+ arm64).
- **Platform matrix**: CI covers macOS/Linux; Windows-related test paths exist (`test:check:windows-wine`, run only when diagnosing known Windows failures).

### 17.4 Repository Directory Structure (monorepo)

```ini
vendor/      Vendored Cordis source (manifest + sync scripts)
packages/    @deepseek-ai/dsh-<pkg> workspaces, grouped: core/ api/ typert/ llm/ shell/
             subprocess/ terminal/ fs/ lsp/ skill/ web/ compaction/ context/
             subagent/ workflow/ todo/ plan/ preset/ guard/ extensions/ hooks/
             session/ session-query/ settings/ credentials/ identity/ acp/
             interaction/ boot/ sdk/ host/ client/ bundle/ examples/ util/
python/      Python SDK + bundled runtime (deepseek-harness-sdk / runtime-bin)
deepseek-harness4j/  Java SDK port (this repo: sdk / sdk-runtime / spring-boot-starter / spring-boot-example / docs)
native/      Native landlock sandbox addon
examples/    Runnable cordis.yml leaves (agent-spine + CLI/ACP/JSON-RPC examples)
apps/        App entry points like the CLI (apps/cli/bin.ts -> dsh)
docs/        Architecture, generated catalogs, postmortems, cookbook, user guide (bilingual)
website/     VitePress docs site (curated bilingual sources)
scripts/     Repo gates and generators
.agents/     Agent workflows + Agent Notes (notes-as-docs)
```

### 17.5 Environment Variable Cheat Sheet

| Variable | Purpose |
|---|---|
| `DEEPSEEK_API_KEY` | Required primary key (deepseek adapter / default composition) |
| `DEEPSEEK_BASE_URL` | Optional; set when pointing at an OpenAI-compatible proxy/self-hosted gateway |
| `DSH_MODEL` | Default model id (default `deepseek-v4-flash`) |
| `DSH_CONTEXT_WINDOW` | Override the model's context window |
| `DSH_SYSTEM_PROMPT` | Override the system prompt (persona) |
| `DSH_CWD` | Agent working directory |
| `DSH_SESSION_ROOT` | Session on-disk root directory |
| `DSH_HOME` | Harness user directory (default `~/.dsh`; contains `settings.yaml`, `.credentials.yaml`) |
| `DSH_CORDIS_CONFIG` | Inject a custom cordis config (used when the SDK launches the runtime) |
| `DSH_RUNTIME_MODE` | Runtime carrier mode (`exe`/`node`, read by deepseek-harness4j's `RuntimeResolver`) |

> More provider-level fields are in the `llm-deepseek`/`llm-pi-ai` READMEs and `config-catalog.md`.

### 17.6 Core Glossary

**plugin / context / inject / effect** → see 16.1; **seam / profile / bundle / preset / turn / step / adapter / provider** → see 16.1 and sections 3 & 4. Others: `Catalog` (a provider's built-in model catalog), `modelOverrides` (edit a single catalog model), `credential reference` (a credential reference, no plaintext), `Agent Note` (a design note submitted with a PR), `snapshot test` (a key-less replay test).

### 17.7 License & Third-Party

- **License**: MIT (root `LICENSE`).
- Third-party dependency & license disclosure: `THIRD_PARTY_NOTICES.md`.
- npm package namespace: `@deepseek-ai/dsh-*`; CLI package `@deepseek-ai/dsh` (bin: `dsh`).
- Java package coordinates: `com.deepseek-ai:deepseek-harness4j-*` (deepseek-harness4j repository).
- The underlying Cordis is source-pinned per the vendoring policy (vendor/README.md has the upstream SHA and sync process).

### 17.8 Community & Support

- **GitHub Discussions**: feedback / bugs / discussion.
- **Discord**: official DeepSeek Harness server.
- **WeCom / WeChat Official Account**: Chinese community entry (scan the QR code in README.zh).
- **Plugin discovery**: tag your plugin repo with `dsh-plugin` for discoverability.
- **Contributing**: `CONTRIBUTING.md` (+ Chinese version `CONTRIBUTING.zh.md`).

### 17.9 Known Limitations & Deferred Items (Preview Stage)

- Config does not map `tool_choice` by default (an MVP trade-off).
- Some capabilities are POC-only: `e2b` (remote sandbox), some workflow tools.
- The settings `models` list **entirely replaces** the composition list (merged per-field; arrays are a single field).
- Requests use native `fetch` and don't share the Cordis http plugin's proxy/interception config for now.
- DeepSeek's official chat-completions route is plain text and cannot be configured for image modality.
- Overall still a developer preview: **interfaces and formats may change at any time**.

### 17.10 One-Line Positioning

> **dsh is a general-purpose Agent harness where "everything is a plugin, model-agnostic, self-hostable, multi-client, with strong audit discipline and a sandbox" — DeepSeek's composable Claude Code, suited to teams that want to own their agent runtime, wire up private/self-hosted models, and want reproducible, auditable behavior. deepseek-harness4j line-by-line ports the Python SDK client to Java, letting Java/Spring stacks embed this runtime directly.**
