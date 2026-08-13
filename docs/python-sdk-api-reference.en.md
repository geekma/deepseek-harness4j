# DeepSeek Harness Python SDK — public API and wire-protocol reference (usable as an implementation spec)

[中文](python-sdk-api-reference.md) | English

This document condenses **100% of the upstream `deepseek-harness` Python SDK's capabilities** into an implementable public spec: the SDK public API, the JSON-RPC wire protocol (methods/notifications/events/result semantics), environment variables, lifecycle, and how the web / cli / sdk / acp channels relate. Any project (Java, Go, Rust, JS, …) can build a Python-SDK-equivalent client from scratch against this spec; `deepseek-harness4j`'s Java SDK follows exactly this spec (see also [port-coverage.md](port-coverage.en.md) and [java-migration-notes.md](java-migration-notes.en.md)).

## 1. One-line model

> **SDK = subprocess management + stdio JSON-RPC 2.0 client + session/notification orchestration.**

- The SDK launches the runtime (`dsh-jsonrpc-agent`) as a subprocess and talks **newline-delimited JSON-RPC 2.0 over stdin/stdout**; stderr is diagnostics only.
- The runtime is the "server": it serves `initialize` / `session/prompt` / `shutdown` requests and pushes **notifications** (session events, status, subagent lifecycle).
- The SDK never touches the model directly; model calls happen inside the runtime and stream back to the SDK as session events.

## 2. Transport and framing (protocol layer)

| Item | Value |
|---|---|
| Transport | subprocess stdin/stdout/stderr (stdout carries protocol messages only) |
| Framing | **one JSON message per line** (UTF-8, `\n`-terminated, compact `json.dumps(separators=(",",":"))`) |
| Protocol | JSON-RPC 2.0 |
| Non-JSON stdout lines | must be ignored (the runtime may print Node warnings) |
| stderr | not protocol; the SDK keeps a rolling 400-line tail for diagnostics |

**Message shapes** (three kinds):

```jsonc
// request (client → runtime)
{"jsonrpc":"2.0","id":"<uuid>","method":"<method>","params":{...}}
// response (runtime → client)
{"jsonrpc":"2.0","id":"<uuid>","result":{...}}
{"jsonrpc":"2.0","id":"<uuid>","error":{"code":<int>,"message":"...","data":<any?>}}
// notification (runtime → client, no id)
{"jsonrpc":"2.0","method":"<method>","params":{...}}
// inbound request (runtime → client, id + method; the client must respond)
{"jsonrpc":"2.0","id":"bridge-req-1","method":"llm.request","params":{...}}
```

- **Responses route by `id`**: a request registers `id → waiting queue` when sent and pops it on return.
- **Inbound requests** (with `id` and `method`) go to the "pending-request" queue; the caller answers with `respond(id, result)` / `respond_error(id, code, message, data)`.

## 3. Request methods (client → runtime)

### 3.1 `initialize`

```jsonc
{"id":"...","method":"initialize","params":{
  "cwd": "<absolute path, the agent workspace>",
  "provider": "deepseek-official",
  "model": "deepseek-v4-flash",
  "maxTokens": 49152            // optional
}}
// → result: {"serverInfo": {"name": "deepseek-harness-sdk-runtime", "version": "..."}}
```

Semantics: handshake. On failure the SDK reaps the started subprocess before rethrowing.

### 3.2 `session/prompt`

```jsonc
{"id":"...","method":"session/prompt","params":{
  "sessionId": "example-001",
  "contentBlocks": [{"type":"text","text":"Inspect the repo and fix the failing tests."}]
}}
// → result: {"messageId": "<queued message id>"}
```

Semantics: enqueue content blocks for the given session and return the `messageId` **immediately** (does not wait for agent idle). `messageId` is used to confirm in downstream events that the prompt was accepted by the durable inbox.

### 3.3 `shutdown`

```jsonc
{"id":"...","method":"shutdown","params":null}
// → result: {}
```

Semantics: graceful shutdown; force-kill after `shutdown_timeout_seconds`.

## 4. Inbound requests (runtime → client, bridge)

| method | params | notes |
|---|---|---|
| `llm.request` | `{requestId, sessionId, model, messages}` | a bridged model request from the runtime (demo bridge); the SDK answers via `respond` |

(In real product compositions the runtime usually handles model requests internally; this method appears only in bridge/demo configs.)

## 5. Notifications (runtime → client)

### 5.1 Session event `session.event`

```jsonc
{"method":"session.event","params":{"sessionId":"example-001","event":{...}}}
```

`event` is a discriminated union tagged by `event.type`. **Event types (from real session logs):**

| `type` | notes |
|---|---|
| `agent/inbox/spliced` | prompt accepted by the durable inbox: `data.inserted` contains `{id: <messageId>}` (**inbox receipt**) |
| `assistant/message` | full assistant message: `data.message.content` or `data.content` is a content-block list (`{type:"text",text:...}`) |
| `turn/end` | turn ended: `data.reason.kind` ∈ `completed` / `max-tokens` / `error` … (missing = protocol violation) |
| `turn/start` / `step/start` / `step/end` | turn/step boundaries |
| `request/header` / `request/context` | model-request metadata |
| `user/message` | user message |
| `assistant/chunk` / `text-chunks` / `reasoning-chunks` / `tool-call-chunks` | streaming deltas |
| `tool/call` / `tool/result` | tool invocation and result |
| `session` / `session/title` | session and title |

### 5.2 Session status `session.status`

```jsonc
{"method":"session.status","params":{"sessionId":"example-001","status":"running"}}
// status ∈ running / idle / ...
```

`Session.run()`'s **activity-interval end**: same session and `status == "idle"`.

### 5.3 Subagent lifecycle `subagent.started` / `subagent.finished`

```jsonc
{"method":"subagent.started", "params":{"parentSessionId":"main","childSessionId":"child"}}
{"method":"subagent.finished","params":{"parentSessionId":"main","childSessionId":"child",
                                       "status":"ok","stopReason":"completed"}}
```

Semantics: build the **subagent ancestry**. The client keeps a `child → parent` map for the process lifetime and uses it to deliver "a session and all its descendants" notifications to subscribers. Root-session and descendant events coexist in `notifications`, but `events` receives root-session events only (descendant messages must not replace the root reply).

### 5.4 Others (demos/tests)

`llm/request`, `response/seen`, `tick`, etc. appear in the bridge demo and the test fake runtime.

## 6. SDK public API (Python signature → Java equivalent)

> This is the API surface of "100% of the Python functionality". The Java side is line-for-line equivalent (see `port-coverage.md` §6).

### 6.1 High-level API

```python
class DeepSeekHarnessConfig:            # Java: DeepSeekHarnessConfig (builder)
    provider: str = "deepseek-official"
    model: str = "deepseek-v4-flash"
    max_tokens: int | None = None
    cwd: str | None = None               # resolved to absolute (symlinks resolved)
    runtime_cwd: str | None = None
    session_root: str | None = None      # sets DSH_SESSION_ROOT
    cordis: str | None = None            # sets DSH_CORDIS_CONFIG
    env: dict[str, str] = {}             # extra environment variables
    runtime_bin: str | None = None
    launch_args_override: tuple | None = None
    request_timeout_seconds: float | None = None
    shutdown_timeout_seconds: float = 1.0
    base_url: str | None = None          # sets DEEPSEEK_BASE_URL
    api_key: str | None = None           # sets DEEPSEEK_API_KEY

class DeepSeekHarness:                   # Java: DeepSeekHarness (AutoCloseable)
    client                            # low-level client (Java: client())
    start() / close()                 # idempotent; close always reaps the subprocess
    start_session(session_id=None) -> Session   # defaults to "session-<32hex>"
    run(input, session_id=None, on_notification=None) -> RunResult

class Session:                         # Java: Session (record harness, id)
    run(input, on_notification=None) -> RunResult
    # input: str | list[contentBlocks]

class RunResult:                       # Java: RunResult (record)
    session_id: str
    final_response: str                # last committed root-session assistant text in the interval
    finish_reason: str | None          # kind of the last root-session turn/end; None when none ended
    events: list[JsonObject]           # root-session events only
    notifications: list[Notification]  # root session + all known descendants, in wire order
    session_root: str | None
```

### 6.2 Low-level client API

```python
class HarnessConfig:                   # Java: HarnessConfig
    runtime_bin / bridge_bin / launch_args_override / cwd / env /
    request_timeout_seconds / shutdown_timeout_seconds

class HarnessClient:                   # Java: HarnessClient
    start() / close()
    initialize(*, cwd, provider, model, max_tokens=None) -> InitializeResponse
    session_prompt(session_id, content_blocks, *, on_notification=None,
                   notification_subscription=None) -> str   # returns messageId
    request(method, params, *, response_model, ...) -> T
    notify(method, params=None)
    next_notification() -> Notification
    subscribe_notifications(filter=None) -> NotificationSubscription
    subscribe_session_notifications(session_id) -> NotificationSubscription
    next_request() -> IncomingRequest
    respond(request_id, result) / respond_error(request_id, *, code, message, data=None)

class NotificationSubscription:        # Java: NotificationSubscription
    close() / next() -> Notification / drain(on_notification)
```

### 6.3 Models

```python
JsonValue = str | int | float | bool | None | dict[str, JsonValue] | list[JsonValue]
Notification(method, payload)          # Java: record Notification
IncomingRequest(id, method, payload)   # Java: record IncomingRequest
ServerInfo(name=None, version=None)    # Java: POJO
InitializeResponse(serverInfo=None)    # Java: POJO
```

### 6.4 Exceptions

`HarnessError` → `TransportClosedError` (runtime exited/stdout closed), `SdkProtocolError` (protocol violation), `JsonRpcError` (error response, with code/message/data); plus `TimeoutError` (timeout, Java: `HarnessTimeoutException`) and `FileNotFoundError` (missing runtime, Java: `MissingRuntimeException`).

## 7. Lifecycle and result semantics (must match when implementing)

1. **Start**: `start()` spawns the subprocess (`launch_args_override` > `runtime_bin`/`bridge_bin` > bundled resolution), injects environment variables, and performs the `initialize` handshake.
2. **Zero-config injection**: when the launch resolves to the bundled runtime and the caller sets neither `cordis` nor a non-empty `DSH_CORDIS_CONFIG`, the SDK injects the bundled default-config path into `DSH_CORDIS_CONFIG` (empty counts as absent; explicit `runtime_bin`/`bridge_bin`/`launch_args_override` disables injection).
3. **One turn (`Session.run`)**:
   - subscribe to "the session and its descendants" notifications (filtered by subagent ancestry);
   - `session/prompt` enqueue → obtain `messageId`;
   - loop `subscription.next()` until an `agent/inbox/spliced` receipt carrying this `messageId` is seen (earlier notifications are skipped);
   - keep collecting until the same session's `session.status == idle`;
   - meanwhile: `notifications` are collected in full (optionally forwarded via `on_notification`); root-session `session.event` payloads go into `events`.
4. **Result**: `final_response` = concatenated text blocks of the **last** `assistant/message` in `events`; `finish_reason` = the `reason.kind` of the last `turn/end` (a missing string `kind` → `SdkProtocolError`).
5. **Close**: send `shutdown` (timeout-tolerant), close stdin, SIGTERM, then SIGKILL on timeout; wake all waiters with diagnostics (exit code + stderr tail).

## 8. Environment variables (applied by the SDK)

| Variable | Purpose |
|---|---|
| `DEEPSEEK_API_KEY` | required master key (deepseek adapter / default composition) |
| `DEEPSEEK_BASE_URL` | optional; point at an OpenAI-compatible proxy/self-hosted gateway |
| `DSH_MODEL` | default model id (default `deepseek-v4-flash`) |
| `DSH_CONTEXT_WINDOW` | override the model context window |
| `DSH_SYSTEM_PROMPT` | override the system prompt (persona) |
| `DSH_CWD` | agent working directory (set by the SDK) |
| `DSH_SESSION_ROOT` | session persistence root (`session_root` sets it) |
| `DSH_HOME` | Harness user directory (default `~/.dsh`) |
| `DSH_CORDIS_CONFIG` | inject a custom cordis config |
| `DSH_RUNTIME_MODE` | runtime-carrier mode (`exe`/`node`, read by `RuntimeResolver`) |

## 9. Channel relationships (web / cli / sdk / acp)

| Channel | Shape | Relation to the SDK |
|---|---|---|
| **Web UI** | browser frontend (default `http://127.0.0.1:3080`) | drives the same agent core directly; the SDK does not go through it |
| **CLI (headless)** | `pnpm dsh --profile headless "task"` | single-task headless run; the SDK's `run()` has the same semantics (one task = one turn interval) |
| **SDK (this reference)** | Python/Java subprocess JSON-RPC client | what this spec describes |
| **ACP** | Agent Client Protocol automation server | another client protocol; shares the core and event stream with the SDK |
| **JSON-RPC server** | `@deepseek-ai/dsh-sdk-jsonrpc-server` plugin | what the SDK talks to (the protocol above) |
| **hooks (Claude Code/Codex)** | hook bridge | consumes `hooks.json`/settings; unrelated to the SDK |

**Implementing any channel = implementing the wire protocol in §2–§5 plus the lifecycle in §7.** The SDK's own added value — subprocess management, notification-subscription filtering (subagent ancestry), result derivation, diagnostics collection — is implemented one-to-one in the Java SDK (see `port-coverage.md` §6).

## 10. Version and stability

- The protocol is a developer preview: **interfaces and formats may change at any time**; defer to the official README and `docs/config-catalog.md`.
- The session-log format carries `SESSION_FORMAT_VERSION`; only structural changes bump it.
- This spec corresponds to upstream `master` (verified 2026-08-13).
