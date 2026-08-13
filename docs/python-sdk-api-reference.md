# DeepSeek Harness Python SDK —— 公开 API 与线上协议参考（可作实现规格）

[English](python-sdk-api-reference.en.md) | 中文

本文件把上游 `deepseek-harness` **Python SDK 的 100% 功能**沉淀为**可实现的公开规格**：SDK 公开 API、JSON-RPC 线上协议（方法/通知/事件/结果语义）、环境变量、生命周期，以及 web / cli / sdk / acp 等通道的关系。任何项目（Java、Go、Rust、JS……）都可以据此**从零实现一个与 Python SDK 等价的客户端**；`deepseek-harness4j` 的 Java SDK 即以此规格为蓝本（另见 [port-coverage.md](port-coverage.md) 与 [java-migration-notes.md](java-migration-notes.md)）。

## 1. 一句话模型

> **SDK = 子进程管理 + stdio JSON-RPC 2.0 客户端 + 会话/通知编排。**

- SDK 以子进程方式拉起运行时（`dsh-jsonrpc-agent`），通过 **stdin/stdout 上的按行分隔 JSON-RPC 2.0** 通信，stderr 只读诊断。
- 运行时是"服务端"：处理 `initialize` / `session/prompt` / `shutdown` 等请求，并主动推送**通知**（会话事件、状态、子代理生命周期）。
- SDK 不直接接触模型；模型调用发生在运行时内部，其结果以会话事件的形式流回 SDK。

## 2. 传输与帧格式（协议层）

| 项 | 值 |
|---|---|
| 传输 | 子进程 stdin/stdout/stderr（stdout 仅协议消息） |
| 帧 | **每行一条 JSON**（UTF-8，`\n` 结尾，`json.dumps(separators=(",",":"))` 紧凑格式） |
| 协议 | JSON-RPC 2.0 |
| stdout 非 JSON 行 | 必须忽略（运行时可能打印 Node 警告） |
| stderr | 非协议；SDK 滚动保留最近 400 行用于错误诊断 |

**消息结构**（三类）：

```jsonc
// 请求（client → runtime）
{"jsonrpc":"2.0","id":"<uuid>","method":"<method>","params":{...}}
// 响应（runtime → client）
{"jsonrpc":"2.0","id":"<uuid>","result":{...}}
{"jsonrpc":"2.0","id":"<uuid>","error":{"code":<int>,"message":"...","data":<any?>}}
// 通知（runtime → client，无 id）
{"jsonrpc":"2.0","method":"<method>","params":{...}}
// 入站请求（runtime → client，带 id + method，client 必须响应）
{"jsonrpc":"2.0","id":"bridge-req-1","method":"llm.request","params":{...}}
```

- **响应按 `id` 路由**：请求发出时登记 `id → 等待队列`，返回时弹出。
- **入站请求**（带 `id` 且带 `method`）进入"待应答请求"队列，调用方用 `respond(id, result)` / `respond_error(id, code, message, data)` 应答。

## 3. 请求方法（client → runtime）

### 3.1 `initialize`

```jsonc
{"id":"...","method":"initialize","params":{
  "cwd": "<绝对路径，agent 工作目录>",
  "provider": "deepseek-official",
  "model": "deepseek-v4-flash",
  "maxTokens": 49152            // 可选
}}
// → result: {"serverInfo": {"name": "deepseek-harness-sdk-runtime", "version": "..."}}
```

语义：握手。失败时 SDK 会回收已启动的子进程再抛出。

### 3.2 `session/prompt`

```jsonc
{"id":"...","method":"session/prompt","params":{
  "sessionId": "example-001",
  "contentBlocks": [{"type":"text","text":"Inspect the repo and fix the failing tests."}]
}}
// → result: {"messageId": "<已排队的消息 id>"}
```

语义：把内容块排队到指定会话，**立即**返回 `messageId`（不等 agent 空闲）。`messageId` 用于在下游事件中确认该提示词已被持久 inbox 接收。

### 3.3 `shutdown`

```jsonc
{"id":"...","method":"shutdown","params":null}
// → result: {}
```

语义：优雅关闭运行时；`shutdown_timeout_seconds` 超时后强杀。

## 4. 入站请求（runtime → client，bridge）

| method | params | 说明 |
|---|---|---|
| `llm.request` | `{requestId, sessionId, model, messages}` | 运行时向 SDK 侧桥接的模型请求（演示桥）；SDK 经 `respond` 回填 |

（实际产品组合通常由运行时内部处理模型请求；此方法只在桥接/演示配置中出现。）

## 5. 通知（runtime → client）

### 5.1 会话事件 `session.event`

```jsonc
{"method":"session.event","params":{"sessionId":"example-001","event":{...}}}
```

`event` 是一个判别联合，`event.type` 为标签。**事件类型（来自真实会话日志）：**

| `type` | 说明 |
|---|---|
| `agent/inbox/spliced` | 提示词已被持久 inbox 接收：`data.inserted` 数组含 `{id: <messageId>}`（**inbox 回执**） |
| `assistant/message` | 助手完整消息：`data.message.content` 或 `data.content` 为内容块列表（`{type:"text",text:...}`） |
| `turn/end` | 轮次结束：`data.reason.kind` ∈ `completed` / `max-tokens` / `error` …（缺该字段=协议违约） |
| `turn/start` / `step/start` / `step/end` | 轮次/步骤边界 |
| `request/header` / `request/context` | 模型请求元信息 |
| `user/message` | 用户消息 |
| `assistant/chunk` / `text-chunks` / `reasoning-chunks` / `tool-call-chunks` | 流式分片 |
| `tool/call` / `tool/result` | 工具调用与结果 |
| `session` / `session/title` | 会话与标题 |

### 5.2 会话状态 `session.status`

```jsonc
{"method":"session.status","params":{"sessionId":"example-001","status":"running"}}
// status ∈ running / idle / ...
```

`Session.run()` 的**活动区间终点**：同会话且 `status == "idle"`。

### 5.3 子代理生命周期 `subagent.started` / `subagent.finished`

```jsonc
{"method":"subagent.started", "params":{"parentSessionId":"main","childSessionId":"child"}}
{"method":"subagent.finished","params":{"parentSessionId":"main","childSessionId":"child",
                                       "status":"ok","stopReason":"completed"}}
```

语义：用于构建 **subagent 谱系**。客户端在进程生命周期内维护 `child → parent` 映射，从而把"某会话及其所有后代"的通知过滤给订阅者。根会话事件与后代事件共存于 `notifications`，但 `events` 只收根会话（后代消息不得覆盖根回复）。

### 5.4 其他（演示/测试）

`llm/request`、`response/seen`、`tick` 等出现在桥接演示与测试假运行时中。

## 6. SDK 公开 API（Python 签名 → Java 等价）

> 这是"Python 100% 功能"的 API 面。Java 侧逐行等价（见 `port-coverage.md` §6）。

### 6.1 高层 API

```python
class DeepSeekHarnessConfig:            # Java: DeepSeekHarnessConfig (builder)
    provider: str = "deepseek-official"
    model: str = "deepseek-v4-flash"
    max_tokens: int | None = None
    cwd: str | None = None               # 转绝对路径（含符号链接解析）
    runtime_cwd: str | None = None
    session_root: str | None = None      # 设 DSH_SESSION_ROOT
    cordis: str | None = None            # 设 DSH_CORDIS_CONFIG
    env: dict[str, str] = {}             # 额外环境变量
    runtime_bin: str | None = None
    launch_args_override: tuple | None = None
    request_timeout_seconds: float | None = None
    shutdown_timeout_seconds: float = 1.0
    base_url: str | None = None          # 设 DEEPSEEK_BASE_URL
    api_key: str | None = None           # 设 DEEPSEEK_API_KEY

class DeepSeekHarness:                   # Java: DeepSeekHarness (AutoCloseable)
    client                            # 低层客户端（Java: client()）
    start() / close()                 # 幂等；close 总是回收子进程
    start_session(session_id=None) -> Session   # 未给则生成 "session-<32hex>"
    run(input, session_id=None, on_notification=None) -> RunResult

class Session:                         # Java: Session (record harness, id)
    run(input, on_notification=None) -> RunResult
    # input: str | list[contentBlocks]

class RunResult:                       # Java: RunResult (record)
    session_id: str
    final_response: str                # 区间内最后提交的根会话助手文本
    finish_reason: str | None          # 最后一个根会话 turn/end 的 reason.kind；无轮次结束为 None
    events: list[JsonObject]           # 仅根会话事件
    notifications: list[Notification]  # 根会话 + 全部已知后代，按线上顺序
    session_root: str | None
```

### 6.2 低层客户端 API

```python
class HarnessConfig:                   # Java: HarnessConfig
    runtime_bin / bridge_bin / launch_args_override / cwd / env /
    request_timeout_seconds / shutdown_timeout_seconds

class HarnessClient:                   # Java: HarnessClient
    start() / close()
    initialize(*, cwd, provider, model, max_tokens=None) -> InitializeResponse
    session_prompt(session_id, content_blocks, *, on_notification=None,
                   notification_subscription=None) -> str   # 返回 messageId
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

### 6.3 模型

```python
JsonValue = str | int | float | bool | None | dict[str, JsonValue] | list[JsonValue]
Notification(method, payload)          # Java: record Notification
IncomingRequest(id, method, payload)   # Java: record IncomingRequest
ServerInfo(name=None, version=None)    # Java: POJO
InitializeResponse(serverInfo=None)    # Java: POJO
```

### 6.4 异常

`HarnessError` → `TransportClosedError`（运行时退出/stdout 关闭）、`SdkProtocolError`（协议违约）、`JsonRpcError`（错误响应，含 code/message/data）;以及 `TimeoutError`（超时，Java: `HarnessTimeoutException`）、`FileNotFoundError`（缺运行时，Java: `MissingRuntimeException`）。

## 7. 生命周期与结果语义（实现时必须一致）

1. **启动**：`start()` 拉子进程（`launch_args_override` > `runtime_bin`/`bridge_bin` > 内置解析），注入环境变量，`initialize` 握手。
2. **零配置注入**：当启动解析到内置运行时、且调用方未给 `cordis` 也未设非空 `DSH_CORDIS_CONFIG` 时，SDK 把内置默认配置路径注入 `DSH_CORDIS_CONFIG`（空串视为未设置；显式 `runtime_bin`/`bridge_bin`/`launch_args_override` 禁用注入）。
3. **一次轮次（`Session.run`）**：
   - 订阅"会话及其后代"通知（按 subagent 谱系过滤）；
   - `session/prompt` 排队 → 拿到 `messageId`；
   - 循环 `subscription.next()`，直到收到"含本 `messageId` 的 `agent/inbox/spliced` 回执"（此后才算"已接收"，之前的通知跳过）；
   - 继续收集到"同会话 `session.status == idle`"为止；
   - 期间：`notifications` 全量收集（可透传 `on_notification`）；根会话 `session.event` 的 `event` 收集进 `events`。
4. **结果**：`final_response` = `events` 中**最后一个** `assistant/message` 的文本块拼接；`finish_reason` = 最后一个 `turn/end` 的 `reason.kind`（缺字符串 `kind` → `SdkProtocolError`）。
5. **关闭**：先发 `shutdown`（超时容忍），关 stdin，SIGTERM，超时再 SIGKILL；唤醒所有等待者并注入诊断（退出码 + stderr 尾部）。

## 8. 环境变量（SDK 直接应用的）

| 变量 | 用途 |
|---|---|
| `DEEPSEEK_API_KEY` | 必填主密钥（deepseek 适配器/默认组合） |
| `DEEPSEEK_BASE_URL` | 可选；指向 OpenAI 兼容代理/自建网关 |
| `DSH_MODEL` | 默认模型 id（默认 `deepseek-v4-flash`） |
| `DSH_CONTEXT_WINDOW` | 覆盖模型上下文窗口 |
| `DSH_SYSTEM_PROMPT` | 覆盖系统提示词（persona） |
| `DSH_CWD` | agent 工作目录（SDK 自动设） |
| `DSH_SESSION_ROOT` | 会话落盘根目录（`session_root` 设置） |
| `DSH_HOME` | Harness 用户目录（默认 `~/.dsh`） |
| `DSH_CORDIS_CONFIG` | 注入自定义 cordis 配置 |
| `DSH_RUNTIME_MODE` | 运行时载体模式（`exe`/`node`，`RuntimeResolver` 读取） |

## 9. 各通道关系（web / cli / sdk / acp）

| 通道 | 形态 | 与 SDK 的关系 |
|---|---|---|
| **Web UI** | 浏览器前端（默认 `http://127.0.0.1:3080`） | 直接驱动同一 agent 内核；SDK 不经由它 |
| **CLI（headless）** | `pnpm dsh --profile headless "task"` | 单任务无头执行；SDK 的 `run()` 语义与之对应（一任务=一轮次区间） |
| **SDK（本参考）** | Python/Java 子进程 JSON-RPC 客户端 | 本规格所描述 |
| **ACP** | Agent Client Protocol 自动化服务器 | 另一种客户端协议；与 SDK 共用内核与事件流 |
| **JSON-RPC server** | `@deepseek-ai/dsh-sdk-jsonrpc-server` 插件 | SDK 与之对话（上文协议） |
| **hooks（Claude Code/Codex）** | 钩子桥接 | 消费 `hooks.json`/settings,与 SDK 无关 |

**实现任意通道 = 实现第 2~5 节的线上协议 + 第 7 节生命周期**。SDK 独有的增值：子进程管理、通知订阅过滤（subagent 谱系）、结果推导、诊断收集——这些在 Java SDK 中一一实现（见 `port-coverage.md` §6）。

## 10. 版本与稳定性

- 协议为开发者预览：**接口与格式可能随时变化**；以官方 README 与 `docs/config-catalog.md` 为准。
- 会话日志格式有 `SESSION_FORMAT_VERSION`;结构变更才会升版本。
- 本规格对应上游 `master`（2026-08-13 核对）。
