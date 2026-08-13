# DeepSeek Harness Java SDK (deepseek-harness4j)

English | [中文](README.zh.md)

**deepseek-harness4j** is the Java port of [DeepSeek Harness](https://github.com/deepseek-ai/deepseek-harness)'s Python SDK, driving the same agent runtime via newline-delimited JSON-RPC 2.0 over stdio. Everything is a plugin, MIT-licensed, developer preview.

DeepSeek Harness (command `dsh`) is an **agent harness** whose core design principle is **everything is a plugin**: sessions, system prompts, tools, the agent loop, LLM access, bash, the filesystem, subprocesses, web capabilities, subagents, and workflows are all pluggable [Cordis](https://github.com/cordiverse/cordis) plugins. Upstream ships a Web UI, CLI, Python SDK, ACP, and JSON-RPC clients; this repository ports the **Python SDK channel** into a Java SDK. A Java process talks newline-delimited JSON-RPC 2.0 over stdio to the bundled runtime (`dsh-jsonrpc-agent`) and drives the real agent loop (sessions, system prompts, tools, subagents, persistence). **The runtime, the Cordis plugin composition (`cordis.yml`), and the model configuration are identical to upstream - only the client language changes.**

> This project is in **developer preview**: it iterates fast and may ship breaking changes. Always check the official docs before relying on it.

Before using the SDK, read the [DeepSeek Harness complete guide (Java edition)](deepseek-harness4j-user-guide.en.md), which covers custom-model integration, `cordis.yml` composition, common errors, and verified run records.

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

### Upstream vs deepseek-harness4j — complete feature comparison

The following matrix contrasts the **upstream** `deepseek-ai/deepseek-harness` (Node.js/TypeScript runtime + Web UI + CLI + Python SDK + ACP) with **deepseek-harness4j** (this Java SDK port). The dividing principle is simple: **deepseek-harness4j ships the same upstream runtime (`dsh-jsonrpc-agent`) and reuses every runtime-side Cordis plugin as-is, so all "runtime" rows are ✅. Only "client-channel" rows that the Python SDK does not expose are marked as intentionally out of scope for 4j.**

#### 1. Client channels (language bindings / UI entry points)

| Channel | Upstream (`deepseek-harness`) | deepseek-harness4j | Note if not in 4j |
|---|---|---|---|
| **Python SDK** (`python/sdk`, module `deepseek_harness`) | ✅ shipped as `deepseek-harness-sdk` (Python package) | ✅ **line-by-line Java port** (`com.deepseek.harness4j`, module `deepseek-harness4j-sdk`) | — |
| **Java SDK** (this repo) | — (no Java SDK upstream) | ✅ the whole point of this repo | Upstream has no Java SDK, so 4j is the Java equivalent. |
| **Web UI** (`apps/web`, browser app `http://127.0.0.1:3080`) | ✅ full React + Cordis browser UI, settings, models catalog, session browser, in-session edit, agent-inspector | 📄 **not shipped — use the upstream runtime binary or upstream repo** | 4j is a programmatic SDK channel, not a browser app. The Web UI is an upstream runtime feature; run it from the upstream monorepo (`pnpm dsh web` or `npx @deepseek-ai/dsh web`) — the same `settings.yaml`, models catalog, session store, and `cordis.yml` compositions are 100% shared. |
| **Headless CLI** (`apps/cli`, command `dsh --profile headless`) | ✅ one-shot non-interactive agent; prints `final_response`; configurable machine-readable or human output | 📄 **semantics provided by SDK `DeepSeekHarness.run()`** — no standalone CLI wrapper in 4j | The CLI is just "another client" of the same runtime; `RunResult.finalResponse()` / `RunResult.finishReason()` / `RunResult.events()` return exactly what `dsh --profile headless` emits. For CI pipelines call `run()` from a small Java `main` (see `MinimalAgent`), which keeps credentials and args under native Java control instead of shell parsing. |
| **ACP server** (`apps/acp-agent`, Agent Client Protocol) | ✅ programmatic automation server with session/permission/cancellation over a separate wire protocol | 📄 **out of scope — language-neutral channel** | ACP is a client protocol independent of 4j; keep using the upstream ACP binary if you need ACP semantics. 4j's JSON-RPC SDK covers the same functional surface (run task, stream events, cancel, permission callbacks) via its own wire. |
| **Spring Boot starter** (auto-config + properties binding) | — (no Spring support upstream) | ✅ `spring-boot-starter` module ships `DeepSeekHarnessProperties` + auto-configures a `DeepSeekHarness` bean (lifecycle managed by Spring) | **4j-exclusive addition**: idiomatic Spring integration. The Python SDK ships raw classes only; 4j adds a `spring-boot-example` with a Spring MVC REST controller wrapping `run()`. |

#### 2. Cordis plugin framework & runtime agent capabilities (powered by the same upstream runtime binary in both projects — ✅ across the board)

> "Everything is a plugin" is the core principle. **4j does not reimplement these plugins in Java — it loads the exact same upstream TypeScript Cordis plugins inside the runtime subprocess.** That is why the columns are identical for every runtime-side feature: the Java client talks newline-delimited JSON-RPC 2.0 over stdio to `dsh-jsonrpc-agent`, which composes the same Cordis plugin tree described by `cordis.yml`.

| Capability / Cordis plugin | Upstream runtime | deepseek-harness4j (via same runtime) | Notes |
|---|---|---|---|
| **Session management** (inbox, durable receipt, turn boundaries, subagent ancestry) | ✅ `@deepseek-ai/dsh-core/session` | ✅ same | 4j receives every session notification over JSON-RPC; `Session.run()` boundaries match `turn/end` exactly. |
| **System prompts** (composition, deployment persona, runtime-context contributions, suppression) | ✅ `@deepseek-ai/dsh-core/system-prompt` | ✅ same | Set via `DSH_SYSTEM_PROMPT` or `cordis.yml`; no Java-side code needed. |
| **Tools** (register, schema, typed args, output schema + render, `presentationMeta`, HMR-safe unregister) | ✅ `@deepseek-ai/dsh-core/tools` + `defineTool()` | ✅ same runtime plugins, same contracts | Adding a new tool is done by writing a TypeScript Cordis plugin (the runtime side — identical to upstream) and mounting it via `cordis.yml`. 4j does not ship a "Java tool interface" because tools live in the runtime, not the client; see [adding-a-tool.en.md](docs/user-guide/adding-a-tool.en.md). |
| **Agent loop** (plan, observe, tool call, steering, subagent delegation, idle detection) | ✅ `@deepseek-ai/dsh-core/agent` + `@deepseek-ai/dsh-agent-loop` | ✅ same | The `agent-loop` is a pure runtime concern. 4j only consumes `RunResult` events, never controls loop internals directly. |
| **LLM access & adapters** (streaming, usage, tool-call `arguments` JSON string, error code, replay state, `resolveModel()`) | ✅ `@deepseek-ai/dsh-llm` + adapters `dsh-llm-deepseek` / `dsh-llm-pi-ai` / `dsh-llm-retry` | ✅ same runtime adapters, same catalog semantics, same model config | Configured via `cordis.yml` / `settings.yaml` / env vars (`DEEPSEEK_API_KEY`, `DEEPSEEK_BASE_URL`, `DSH_MODEL`). Adding a custom adapter is a runtime-side TypeScript Cordis plugin identical to upstream; see [adding-an-llm-adapter.en.md](docs/user-guide/adding-an-llm-adapter.en.md). |
| **Bash / shell** (persistent PTY, owner-scoped, danger-full-access strategy, timeout, `todo_write`) | ✅ `@deepseek-ai/dsh-shell` / `@deepseek-ai/dsh-tool-bash` | ✅ same | Uses the runtime's spawned native shell. Java client never sees PTY bytes directly — tool-call results arrive as structured `RunResult` events. |
| **Filesystem** (view / create / str_replace / insert editor; `fs-local` backend; compaction) | ✅ `@deepseek-ai/dsh-tool-str-replace-editor`, `@deepseek-ai/dsh-fs-local`, compaction plugins | ✅ same | Works on the runtime-side `DSH_CWD`. Mount your workspace via `DeepSeekHarnessConfig.cwd()` (it becomes `DSH_CWD`). |
| **Subprocess spawning** (foreground, in-process subagent spawn provider, signal propagation) | ✅ `@deepseek-ai/dsh-subprocess`, spawn plugin, subagent plugin | ✅ same | Subagent events arrive as nested notifications; `RunResult.notifications` preserves them in wire order and `HarnessClient` retains subagent ancestry per Python SDK semantics. |
| **Web / network capabilities** (fetch, browse, web-schedule overlay) | ✅ `@deepseek-ai/dsh-web` + web-reminder config layers | ✅ same (if mounted in `cordis.yml`) | Pure runtime-side; no Java plumbing required. |
| **Sub-agent orchestration** (parallel agents, `toolName: subagent`, ancestry tracking) | ✅ subagent, plan, todo plugins | ✅ same | Subagent lifecycle events flow through 4j's `onNotification` callback exactly as in Python SDK — covered by `SubscriptionRoutingTest` and `ClientLevelTest`. |
| **Workflows / plan / todo-write** (structured mid-run planning) | ✅ plan workflow plugin, `todo_write` tool | ✅ same | Cordis-composable, language-neutral. |
| **Persistence** (JSONL sessions, semantic checkpoints, zstd default, compaction) | ✅ `@deepseek-ai/dsh-persistence-*`, context compaction plugin | ✅ same | Uses `DSH_SESSION_ROOT` / `sessionRoot` from config. Zero Java-side persistence code. |
| **Approval / permission** (policies, approval UI prompt, danger-full-access override) | ✅ policy + strategy plugins | ✅ same | Runtime-side, language-neutral. |
| **Sandbox / native hardening** (landlock on Linux, `node-pty` spawn helper on macOS) | ✅ upstream `native/` addon, spawn helpers | ✅ same (bundled runtime carries them) | 4j copies no native code; the compiled `dsh-jsonrpc-agent` binary ships it for the matching platform/arch. |

**Why 4j does not port runtime plugins to Java:** plugins are Cordis effect-based (register/unregister tied to fiber lifecycle), schema-typed, and speak a shared runtime event bus. Rewriting them to Java would be "rewriting the upstream engine in a new language" — an N× effort with zero functional gain. Instead, 4j ships the same verified `dsh-jsonrpc-agent` binary and uses JSON-RPC to drive it, guaranteeing **exactly identical** semantics, config surface, and model outputs to Python SDK callers.

#### 3. Developer tooling, SDK surface, and ecosystem integrations

| Layer | Upstream (`deepseek-harness`) | deepseek-harness4j | Notes if not 1:1 |
|---|---|---|---|
| **SDK public API** (`DeepSeekHarness`, `Session`, `RunResult`, `HarnessClient`, `Notification`, `IncomingRequest`, exception types) | ✅ Python package `deepseek_harness` | ✅ **100% semantic port** — same class names, same method signatures, same error class hierarchy | Mapped line-by-line (see [java-migration-notes.en.md](docs/java-migration-notes.en.md)). Differences are only syntactic (builder vs kwargs, `AutoCloseable` vs context manager, `record` vs pydantic). |
| **SDK low-level JSON-RPC client** (`initialize`, `session/prompt`, `shutdown`, routing by `id`, subscriptions, incoming `llm.request` bridge) | ✅ Python `HarnessClient` + JSON-RPC stdio line framing | ✅ **100% ported** to Java — same transport rules, same stderr ring buffer, same per-call semantics | Covered by `ClientLevelTest` (15 cases) + `SubscriptionRoutingTest` (3 cases). |
| **Runtime-carrier resolution** (packaged `dsh-jsonrpc-agent-<platform>-<arch>`, dev node mode, macOS spawn helper validation) | ✅ Python package `deepseek_harness_runtime` | ✅ Java `com.deepseek.harness4j.runtime.RuntimeResolver` (line-by-line port) | See [sdk-runtime/README.en.md](sdk-runtime/README.en.md). Same dual carrier, same mode selection, same error messages (`MissingRuntimeException` → Python `FileNotFoundError`). |
| **SDK tests** (transport, routing, boot, build hooks, smoke completions, release version, macOS deployment target, runtime resolution) | ✅ `python/sdk/tests` pytest suite | ✅ **equivalent JUnit 5 suite** (60 cases, 0 failures, 7 skips when no local runtime carrier) | Coverage + semantics verified against Python originals; see [test-report.en.md](docs/test-report.en.md). |
| **Build tool** (SDK packaging + distribution) | Python: `hatch` + `uv` (`pyproject.toml`, `hatch_build.py`) → wheels + sdist | Java: **Maven 3.9+** (`pom.xml` multi-module) → jars (sdk / spring-boot-starter / spring-boot-example) | Ecosystem substitution, not semantic gap. The 3 pure functions of `scripts/*.py` (build-python-release hooks, smoke runtime, macOS deployment-target check) are ported to Java under `com.deepseek.harness4j.build.*`. |
| **Model Experience / Agent Notes / `.agents/`** discipline | ✅ 1386 files in upstream `.agents/` — internal engineering process, not SDK code | 📄 **not copied** — language-independent engineering process | These are upstream development artifacts (Agent Notes per PR, skill files, experience write-ups). Not runtime code; not SDK code. No Java equivalent needed or possible. |
| **Documentation sites / i18n / website** (`website/`, VitePress, `docs/i18n/`) | ✅ VitePress docs station + Chinese/English i18n layers in upstream docs | ✅ **Bilingual Markdown files hosted directly in this repo** (`.md` / `.en.md` pairs, mutual cross-references) | 4j does not run a separate VitePress site. All Java-specific documentation lives in the 17 bilingual `.md` pairs in this repository. |
| **Spring Boot 3.x integration** (auto-configuration, properties binding, MVC REST example) | Not available upstream (Python ecosystem has Flask/FastAPI equivalents but none shipped in the Python SDK path) | ✅ `spring-boot-starter` + `spring-boot-example` | **4j-exclusive addition** (§7 of [java-migration-notes.en.md](docs/java-migration-notes.en.md)). Typical Spring enterprises can drop in `deepseek-harness4j-spring-boot-starter` and start using the SDK without manual lifecycle management. |

#### 4. Summary: what 4j is (and what it deliberately does not do)

- ✅ **100% of the Python SDK surface is ported.** Public API, JSON-RPC protocol, exceptions, runtime-carrier resolution, zero-config default `cordis.yml`, session semantics, notification ordering, subagent ancestry — all line-by-line equivalent.
- ✅ **100% of runtime Cordis plugin capabilities are reused.** Sessions, system prompts, tools, agent loop, LLM adapters, bash, filesystem, subprocess, web, subagents, workflows, persistence, approval, sandbox — identical to upstream because **they run inside the same upstream `dsh-jsonrpc-agent` binary**, not inside the Java process.
- 📄 **Client channels not ported (by design):** Web UI, headless CLI, ACP server — these are "other clients" of the same runtime, not SDK concerns. 4j exposes the same functional surface through its programmatic API; use the upstream binaries for interactive browser UI / CLI one-shot / ACP automation if you need them.
- 📄 **Upstream TypeScript sources (`packages/`, `apps/`, `vendor/`, `native/`) are not rewritten to Java.** They compose the runtime binary 4j ships and consumes. Writing them in Java would be a full engine rewrite with no compatibility benefit. 4j's approach guarantees the same behavior as any other client.

---

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

### Prerequisites

- **JDK 17+** (LTS; the code also runs on 21 / 25)
- **Maven 3.9+**
- A DeepSeek Harness runtime carrier (see [Runtime carrier setup](#runtime-carrier-setup) below, or [sdk-runtime README](sdk-runtime/README.en.md))

### Build from source

```sh
git clone https://github.com/geekma/deepseek-harness4j.git
cd deepseek-harness4j
mvn install            # build all modules (sdk, spring-boot-starter, spring-boot-example)
mvn test               # run tests (sdk module, 60 tests)
```

### Use as a Maven dependency

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>com.deepseek-ai</groupId>
    <artifactId>deepseek-harness4j-sdk</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

### Runtime carrier setup

The Java SDK communicates with the DeepSeek Harness runtime (`dsh-jsonrpc-agent`) over stdio JSON-RPC 2.0. The runtime binary is cross-built from the [upstream DeepSeek Harness repository](https://github.com/deepseek-ai/deepseek-harness) using the upstream toolchain (Node.js / pnpm). **Java SDK users only need the resulting binary — you do not need pnpm or Node.js to use deepseek-harness4j.**

**Option A — Use a pre-built binary (recommended):** Download the `dsh-jsonrpc-agent-pkg-<platform>-<arch>` artifact from the upstream release page and place it in the runtime directory. See [sdk-runtime/README.en.md](sdk-runtime/README.en.md) for directory conventions and zero-config resolution.

**Option B — Build from upstream source (requires Node.js 22+ and pnpm):**

```sh
# Upstream build steps (uses upstream toolchain — not required for Java SDK consumers)
git clone https://github.com/deepseek-ai/deepseek-harness.git
cd deepseek-harness
corepack enable            # ensures pnpm is available
pnpm install
pnpm exec tsx scripts/build-exe-for-python-sdk.ts
# Place the generated dsh-jsonrpc-agent-pkg-<platform>-<arch> into the
# deepseek-harness4j runtime directory (see sdk-runtime/README)
```

See [sdk-runtime/README.en.md](sdk-runtime/README.en.md) and [development.en.md](development.en.md) for detailed binary placement and distribution workflows.

### Upstream companion clients

The upstream DeepSeek Harness project (not this Java SDK) also ships additional clients that share the same agent core, model configuration, and `cordis.yml` composition:

- **Web UI** (upstream): `npx @deepseek-ai/dsh web`
- **Headless CLI** (upstream): `dsh --profile headless "task"`
- **Python SDK** (upstream): `pip install deepseek-harness-sdk`

The Java SDK in this repository uses the **same runtime and configuration** as the clients above — only the client language differs.

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

#### Environment variables: point at a custom endpoint

Skip `settings.yaml` and point the official DeepSeek adapter at your OpenAI-compatible proxy through env vars. These are read by the runtime carrier regardless of which client language you use:

```sh
export DEEPSEEK_API_KEY=sk-your-key
export DEEPSEEK_BASE_URL=https://your-gateway.example.com/v1   # point at your endpoint
export DSH_MODEL=your-custom-model-id
```

The Java SDK runtime inherits these environment variables automatically. A complete runnable Java example is in [Quick Start: Pointing at a custom / self-hosted model](#pointing-at-a-custom--self-hosted-model).

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
├── deepseek-harness4j-使用指南.md  # complete guide, Chinese (Java edition) — see deepseek-harness4j-user-guide.en.md for English
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
| [deepseek-harness4j-使用指南.md](deepseek-harness4j-使用指南.md) | Complete usage guide, Chinese (background/install/custom models/composition/Demo/verification/architecture; Java edition) |
| [deepseek-harness4j-user-guide.en.md](deepseek-harness4j-user-guide.en.md) | Complete usage guide, English (bilingual counterpart, mutually linked) |
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
