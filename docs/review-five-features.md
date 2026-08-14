# deepseek-harness4j 五大核心特性 Review 报告

> 审查日期: 2026-08-14  
> 审查范围: `/Users/mali/Documents/code/deepseek-harness4j` 全部源码、配置、测试、文档  
> 审查方法: 逐文件代码审计 + 运行时配置分析 + 架构边界判定

---

## 0. 核心结论（先说答案）

| # | 特性 | Java 代码实现 | 运行时（随包二进制）提供 | 4j 整体可用 | 评分 |
|---|------|:---:|:---:|:---:|:---:|
| 1 | 一切皆插件 | 0% | 100% | 是 | **B** |
| 2 | 每次运行可回放 | 0% | 100% | 是 | **B** |
| 3 | Cordis 内核（时空可组合性） | 0% | 100% | 是 | **B** |
| 4 | 全轨迹可追溯 | 10% (内存收集) | 100% | 是 | **B** |
| 5 | Cordis 动态生命周期/DI/逆向清理 | 0% | 100% | 是 | **B** |

**评分标准**:
- **A** = Java 源码直接实现了该特性的核心逻辑
- **B** = Java 代码未实现，但随包分发的上游运行时二进制完整提供，Java SDK 通过配置透传和 JSON-RPC 委托可完整使用
- **C** = 部分实现，存在功能缺口
- **D** = 未实现且无法使用

**一句话总结**: 五大特性在 Java 代码层面 **0% 实现**（特性 4 有 10% 的内存事件收集），但通过随包分发的上游 `dsh-jsonrpc-agent` 二进制运行时 **100% 提供**。4j 的定位是 **Java 客户端瘦封装**，不是 Java 重写运行时。

---

## 1. 架构边界（理解本报告的前提）

### 1.1 4j 的真实架构

```
┌──────────────────────────────────────────────────────────┐
│                  Java SDK (deepseek-harness4j)            │
│                                                          │
│  纯 Java 代码做的事:                                      │
│  ├── ProcessBuilder spawn 子进程                          │
│  ├── stdin/stdout 换行分隔 JSON-RPC 2.0 编解码             │
│  ├── 请求-响应 UUID 匹配 (BlockingQueue)                   │
│  ├── 通知路由与订阅 (session 父子树)                        │
│  ├── initialize 握手 / shutdown / session/prompt           │
│  ├── 运行时二进制定位 (RuntimeResolver)                    │
│  ├── 默认 cordis.yml 配置注入                              │
│  └── 内存事件收集 (RunResult.events/notifications)          │
│                                                          │
│  纯 Java 代码不做的事 (全部委托给子进程):                    │
│  ├── Cordis 插件加载/卸载/组合                              │
│  ├── LLM 调用 (HTTP 请求)                                 │
│  ├── 工具执行 (bash/fs/sandbox)                            │
│  ├── Session 持久化 (JSONL/Zstandard 落盘)                 │
│  ├── Session resume/fork/search/replay                    │
│  ├── Agent 推理循环                                        │
│  ├── 上下文压缩 / token 计量                               │
│  ├── 沙箱执行                                              │
│  └── 事件日志持久化                                        │
│                                                          │
│              │ spawn (stdio JSON-RPC)                     │
│              ▼                                            │
│  ┌──────────────────────────────────────────────────────┐ │
│  │  Node.js 运行时子进程 (dsh-jsonrpc-agent)             │ │
│  │  预编译单文件二进制, 不检入 git, 随包分发               │ │
│  │                                                      │ │
│  │  Cordis 插件框架 (TypeScript, 上游实现)                │ │
│  │  ├── sdk-jsonrpc-server (stdio 协议入口)              │ │
│  │  ├── agent-spine (agent 核心循环)                     │ │
│  │  ├── llm-deepseek (LLM 适配器)                        │ │
│  │  ├── bash / subprocess (执行器)                       │ │
│  │  ├── sandbox / sandbox-policy (沙箱)                  │ │
│  │  ├── sessions (JSONL 持久化/存储)                     │ │
│  │  ├── session-checkpoints (检查点)                     │ │
│  │  ├── subagent (子智能体)                              │ │
│  │  ├── compaction (上下文压缩)                          │ │
│  │  ├── token-meter (计量)                               │ │
│  │  └── fs-local (文件系统)                              │ │
│  └──────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────┘
```

### 1.2 关键证据

| 证据 | 来源 |
|------|------|
| "Line-by-line Java port of the Python `DeepSeekHarness` class" | [DeepSeekHarness.java](file:///Users/mali/Documents/code/deepseek-harness4j/sdk/src/main/java/com/deepseek/harness4j/DeepSeekHarness.java#L16) |
| "The SDK drives DeepSeek Harness as a subprocess over newline-delimited JSON-RPC 2.0 on stdio." | [package-info.java](file:///Users/mali/Documents/code/deepseek-harness4j/sdk/src/main/java/com/deepseek/harness4j/package-info.java) |
| 项目中不存在任何 `.ts` / `.js` 源文件 | Glob `**/*.ts` `**/*.js` 零匹配 |
| Java 代码中搜索 `plugin`/`Plugin`/`loadPlugin`/`registerPlugin` | 零匹配（仅测试方法名中出现） |
| Java 代码中搜索 `sandbox`/`scheduler`/`storage` | 零匹配 |
| Java 代码中搜索 `resume`/`fork`/`replay` | 仅 1 处测试方法名，非实现代码 |
| SDK 只调用 3 个 JSON-RPC 方法: `initialize`/`session/prompt`/`shutdown` | [HarnessClient.java](file:///Users/mali/Documents/code/deepseek-harness4j/sdk/src/main/java/com/deepseek/harness4j/client/HarnessClient.java) |

---

## 2. 特性 1: 一切皆插件

### 用户描述

> 模型、工具、skill、会话、沙箱、存储、主循环、调度，连 UI 都是插件，配置里全都可换，不动源码。四种运行模式里最有意思的是 Minimal -- 只留一个 bash 和一个文件编辑器，摆明了是给模型做裸机 benchmark 的。

### 审查结果

| 子项 | Java 实现 | 运行时提供 | 证据 |
|------|:---:|:---:|------|
| 模型(LLM)可插拔 | 否 | 是 | `cordis.yml` 中 `llm-deepseek` 插件可替换为 `llm-pi-ai` 等 |
| 工具可插拔 | 否 | 是 | `bash`/`tool-fs`/`tool-subagent`/`tool-todo`/`str-replace-editor` 均为独立插件 |
| skill 可插拔 | 否 | 是 | `agent-spine` 配置 `skills: false/true` |
| 会话可插拔 | 否 | 是 | `sessions` 插件 (`dsh-session-persistence-jsonl`) |
| 沙箱可插拔 | 否 | 是 | `sandbox` (`dsh-sandbox-local`) + `sandbox-policy` |
| 存储可插拔 | 否 | 是 | 会话存储后端通过插件选择 (JSONL/SQLite) |
| 主循环可插拔 | 否 | 是 | `agent-spine` (`dsh-agent-spine-demo`) 本身是插件 |
| 调度可插拔 | 否 | 是 | `subagent` + `subagent-spawn-in-process` |
| UI 可插拔 | 否 | 是 | 上游 Web UI / CLI 均为独立客户端通道 |
| Minimal 模式 | 否 | 是 | [minimal.cordis.yml](file:///Users/mali/Documents/code/deepseek-harness4j/sdk/src/main/resources/examples/jsonrpc-agent/minimal.cordis.yml): 只配 `persistent-bash` + `str-replace-editor` 两个模型可见工具 |

### 详细分析

**Java 端**: `DeepSeekHarnessConfig.cordis()` 字段接受一个 YAML 文件路径，通过 `DSH_CORDIS_CONFIG` 环境变量传给子进程。Java 代码不解析 YAML、不加载插件、不管理插件生命周期。

```java
// DeepSeekHarness.java 第 44-46 行 -- 唯一与 Cordis 相关的 Java 代码
if (config.cordis() != null) {
    env.put("DSH_CORDIS_CONFIG", config.cordis());
}
```

**运行时端**: 4j 随包分发 3 套 cordis.yml 配置，覆盖不同插件组合:

| 配置文件 | 插件数 | 定位 |
|----------|:---:|------|
| `runtime/cordis.yml` (默认) | 8 | 零配置快速启动: JSON-RPC + agent + LLM + bash + fs + sessions |
| `examples/jsonrpc-agent/cordis.yml` (完整) | 16 | 无人值守编码 agent: +子代理 +todo +压缩 +计量 |
| `examples/jsonrpc-agent/minimal.cordis.yml` (极简) | 12 | 裸机 benchmark: 只暴露 bash + str_replace_editor，含沙箱 |

**配置可换、不动源码**: 是的。用户写一个自定义 `cordis.yml` 传给 `DeepSeekHarnessConfig.builder().cordis(path)` 即可。但这个"可换"的能力完全来自上游运行时，Java SDK 只是传了个文件路径。

### 结论

| 维度 | 判定 |
|------|------|
| Java 代码是否实现了插件框架 | **否 (0%)** |
| 随包运行时是否提供完整插件能力 | **是 (100%)** |
| 用户能否通过配置切换插件组合 | **是** |
| Minimal 模式是否可用 | **是** |
| **综合评分** | **B** -- 功能完整可用，但核心实现不在 Java 代码中 |

---

## 3. 特性 2: 每次运行可回放

### 用户描述

> 系统提示、推理过程、工具调用与结果、子 agent 调度、所有 context 注入，全部进 append-only 的 session log, resume / fork / search / replay 在同一条事件流上。

### 审查结果

| 子项 | Java 实现 | 运行时提供 | 证据 |
|------|:---:|:---:|------|
| append-only session log | 否 | 是 | `dsh-session-persistence-jsonl` 插件写 `.jsonl.zstd` |
| 系统提示记录 | 否 | 是 | agent-spine 注入的 system prompt 进 session log |
| 推理过程记录 | 否 | 是 | `assistant/chunk` 事件保留推理 token |
| 工具调用与结果记录 | 否 | 是 | `tool/call` + `tool/result` 事件 |
| 子 agent 调度记录 | 否 | 是 | `subagent.started`/`subagent.finished` 通知 |
| context 注入记录 | 否 | 是 | `agent/inbox/spliced` 事件 |
| resume (续聊) | 部分 | 是 | Java: 同 sessionId 可多轮 `run()`; 运行时: 从 JSONL 恢复 |
| fork (分叉) | 否 | 是 | 运行时支持，Java SDK 无对应 API |
| search (搜索) | 否 | 是 | 运行时支持全文搜索，Java SDK 无对应 API |
| replay (重放) | 否 | 是 | 运行时支持，Java SDK 无对应 API |

### 详细分析

**Java 端的 session 管理极其简单**:

```java
// Session.java -- 整个 session 管理 80 行代码
// run() 方法: 发 prompt -> 收通知 -> 等 idle -> 返回 RunResult
// 同一个 sessionId 可以多次调 run() (多轮对话)，但这不是 "resume from log"
// 真正的 resume (从磁盘 JSONL 恢复) 由运行时子进程处理
```

Java `RunResult` 在内存中收集了事件和通知:

```java
// RunResult.java -- 6 个字段的 record
public record RunResult(
    String sessionId,           // 会话 ID
    String finalResponse,       // 最后一条助手文本
    String finishReason,        // 结束原因
    List<Map<String, Object>> events,        // 内存事件列表 (不持久化)
    List<Notification> notifications,        // 内存通知列表 (不持久化)
    String sessionRoot)         // 会话根目录路径 (仅透传配置值)
```

**运行时端**:
- `dsh-session-persistence-jsonl` 插件将所有事件写入 `.jsonl.zstd` 文件
- `dsh-session-checkpoint-policy` 插件控制持久化检查点（request/tool-dispatch/completed-step）
- Java SDK 通过 `DSH_SESSION_ROOT` 环境变量告诉运行时往哪写
- `ManualSdkAgentSmoke.java` 测试验证了运行时会写出 `.jsonl.zstd` 文件

**关键缺口**:
- Java SDK **不提供** `session/resume`、`session/fork`、`session/search`、`session/replay` 的 JSON-RPC 方法调用
- 虽然 SDK 有通用的 `request(method, params)` 方法可以手动发送任意 JSON-RPC 命令，但没有封装、没有文档、没有测试
- 用户要用这些功能，需要自己查阅上游协议文档并手写 JSON-RPC 调用

### 结论

| 维度 | 判定 |
|------|------|
| Java 代码是否实现了 session log 持久化 | **否 (0%)** |
| Java 代码是否实现了 resume/fork/search/replay API | **否 (0%)** |
| 运行时是否完整提供这些能力 | **是 (100%)** |
| 用户能否通过 Java SDK 使用这些能力 | **部分** -- resume 通过同 sessionId 多轮 run() 间接可用; fork/search/replay 需手动发 JSON-RPC |
| **综合评分** | **B** -- 底层能力完整，但 Java SDK 未封装高级 session 操作 API |

---

## 4. 特性 3: Cordis 内核 -- 时空可组合性

### 用户描述

> dsh 跑在 Cordis 上，它把插件系统拆成两个正交维度:
> - 时间可组合性: 卸载一个组件时副作用能完整回滚（每个 context 变换都带一个逆，运行时来追踪）
> - 空间可组合性: 依赖可声明、且 context 一变就反向通知组件
> 还给了一套动态组合的演算，证明这个性质能从单个组件传导到整个系统。

### 审查结果

| 子项 | Java 实现 | 运行时提供 | 证据 |
|------|:---:|:---:|------|
| Cordis 内核 (TypeScript) | 否 | 是 | 上游 `vendor/cordis/` 目录，预编译进二进制 |
| 时间可组合性 (reversible effects) | 否 | 是 | `ctx.effect()` / `ctx.on()` 可逆效果机制 |
| 空间可组合性 (dependency declaration) | 否 | 是 | `inject` 声明服务依赖 |
| context 变换通知 | 否 | 是 | context 变化时反向通知组件 |
| 动态组合演算 | 否 | 是 | 上游 Cordis 框架的数学性质证明 |
| HMR (热模块替换) 安全 | 否 | 是 | 可逆效果保证卸载时回滚 |

### 详细分析

**Java 端**: 完全不存在 Cordis 内核的任何实现。搜索 `cordis` 关键词在 Java 源码中仅作为:
- 配置字段名 (`cordis`)
- 环境变量名 (`DSH_CORDIS_CONFIG`)
- 测试方法名 (`test_cordis_plugin_names_exist_in_runtime_dependencies`)

不存在:
- 插件接口/抽象类
- context 对象
- effect/on 机制
- 依赖注入容器
- 生命周期管理器

**运行时端**: Cordis 是上游项目的 TypeScript 插件框架内核，预编译进 `dsh-jsonrpc-agent` 二进制。4j 随包分发的三套 `cordis.yml` 配置文件就是这个内核的消费者。所有时间/空间可组合性保证由这个二进制提供。

### 结论

| 维度 | 判定 |
|------|------|
| Java 代码是否实现了 Cordis 内核 | **否 (0%)** |
| 随包运行时是否包含完整 Cordis 内核 | **是 (100%)** |
| 用户能否受益于时空可组合性 | **是** -- 通过配置组合插件，运行时保证可组合性 |
| Java 端是否有任何可组合性抽象 | **否** |
| **综合评分** | **B** -- 能力完整可用，但完全依赖上游二进制 |

---

## 5. 特性 4: 全轨迹可追溯

### 用户描述

> 与一些闭源模型隐藏推理过程（CoT）不同，DSH 强调每一行运行轨迹都必须透明。
> - 记录一切: 系统提示词、推理链条、工具调用结果、子代理调度，全部记录在只增不减的会话日志中。
> - 开发者友好: 你可以随时回溯、分支、搜索或重放任何一段执行流。
> - 深度洞察: 这种透明度让开发者能看清模型是如何思考的，而不是面对一个加密的黑盒。

### 审查结果

| 子项 | Java 实现 | 运行时提供 | 证据 |
|------|:---:|:---:|------|
| 系统提示词记录 | 否 | 是 | agent-spine 注入的 system prompt 进 session log |
| 推理链条记录 | 否 | 是 | `assistant/chunk` 事件含 thinking tokens |
| 工具调用结果记录 | 否 | 是 | `tool/call` + `tool/result` 事件 |
| 子代理调度记录 | 部分 | 是 | Java: `subagent.started/finished` 通知收集到 RunResult |
| 事件不删减 (append-only) | 否 | 是 | JSONL 文件只追加 |
| 回溯 (回看历史) | 部分 | 是 | Java: RunResult.events() 含本次运行事件; 运行时: 完整 JSONL |
| 分支 (fork) | 否 | 是 | 运行时支持 |
| 搜索 (search) | 否 | 是 | 运行时支持全文搜索 |
| 重放 (replay) | 否 | 是 | 运行时支持 |
| `assistant/chunk` 保真度 | 否 | 是 | 运行时保留 token 级回放保真 |

### 详细分析

**Java 端做了什么** (这是 5 个特性中 Java 端唯一有部分实现的一个):

1. **内存事件收集**: `Session.run()` 中的 `collect` 回调从 `session.event` 通知中提取事件，存入 `List<Map<String, Object>> events`

```java
// Session.java 第 38-49 行
Consumer<Notification> collect = notification -> {
    collectedNotifications.add(notification);
    if (onNotification != null) {
        onNotification.accept(notification);
    }
    if ("session.event".equals(notification.method())
            && id.equals(notification.payload().get("sessionId"))) {
        Map<String, Object> event = JsonValues.asObject(notification.payload().get("event"));
        if (event != null) {
            events.add(event);
        }
    }
};
```

2. **通知收集**: 包括子代理的 `subagent.started`/`subagent.finished` 生命周期通知

3. **最终响应提取**: `SessionSupport.finalResponse()` 从事件列表逆序找最后一条 `assistant/message`

4. **结束原因提取**: `SessionSupport.finishReason()` 从事件列表逆序找最后一个 `turn/end` 的 `reason.kind`

**Java 端没做什么**:
- 不持久化任何事件到磁盘
- 不提供搜索历史事件的能力
- 不提供 fork/replay API
- `RunResult` 中的事件列表在 JVM 退出后即丢失

**运行时端**: 完整的轨迹持久化由 `dsh-session-persistence-jsonl` 插件处理，写入 `.jsonl.zstd` 文件。`ManualSdkAgentSmoke.java` 测试验证了文件落盘和 Zstandard magic bytes。

### 结论

| 维度 | 判定 |
|------|------|
| Java 代码是否实现了轨迹记录 | **部分 (10%)** -- 仅内存收集，不持久化 |
| 运行时是否完整提供轨迹透明性 | **是 (100%)** |
| 用户能否从 Java 侧获取本次运行轨迹 | **是** -- `RunResult.events()` + `RunResult.notifications()` |
| 用户能否搜索/回放历史轨迹 | **部分** -- 需手动读 JSONL 文件或手写 JSON-RPC 调用 |
| **综合评分** | **B** -- 透明度完整可用，Java 侧有基本的事件访问能力，但缺乏高级操作封装 |

---

## 6. 特性 5: Cordis 动态生命周期 / 依赖注入 / 逆向效应清理

### 用户描述

> - 动态生命周期: 支持插件的热加载与卸载，且具备销毁传播机制。
> - 依赖注入: 解决复杂系统中插件之间的耦合问题。
> - 逆向效应清理: 当一个插件被卸载时，它能自动清理产生的副作用。

### 审查结果

| 子项 | Java 实现 | 运行时提供 | 证据 |
|------|:---:|:---:|------|
| 插件热加载 | 否 | 是 | Cordis 框架支持运行时插件加载 |
| 插件热卸载 | 否 | 是 | Cordis 框架支持运行时插件卸载 |
| 销毁传播机制 | 否 | 是 | 卸载时按序回滚所有 effect |
| 依赖注入 (inject) | 否 | 是 | 插件通过 `inject` 声明服务依赖 |
| context 变化通知 | 否 | 是 | context 变化时反向通知依赖组件 |
| 逆向效应清理 (reversible effects) | 否 | 是 | `ctx.effect()` 每个变换带逆操作 |
| 不重启调整 agent 能力 | 否 | 是 | 运行时支持热调整 |

### 详细分析

**Java 端**: 完全不存在任何相关实现。Java SDK 没有:
- 插件生命周期管理器
- 依赖注入容器
- 销毁传播机制
- 逆向效应系统
- 热加载/卸载接口

Java SDK 对运行时子进程的唯一生命周期管理是:
- `start()` -- 启动子进程 + initialize 握手
- `close()` -- shutdown 请求 + `Process.destroyForcibly()`

这是进程级生命周期，不是插件级生命周期。

**运行时端**: Cordis 框架的 `ctx.effect()` / `ctx.on()` 机制保证了:
- 每个插件注册的 effect 都有对应的逆操作
- 卸载插件时按注册逆序执行回滚
- 依赖关系通过 `inject` 声明，context 变化时反向通知
- 这种保证从单个组件传导到整个系统（动态组合演算）

### 结论

| 维度 | 判定 |
|------|------|
| Java 代码是否实现了插件生命周期管理 | **否 (0%)** |
| 随包运行时是否提供完整动态生命周期 | **是 (100%)** |
| 用户能否从 Java 侧动态调整插件 | **否** -- 需重启子进程并传新 cordis.yml |
| **综合评分** | **B** -- 运行时能力完整，但 Java SDK 不暴露热加载/卸载接口 |

---

## 7. Java SDK 实现了什么（正面清单）

为避免报告过于负面，以下是 Java SDK **确实实现** 的能力:

| 能力 | 实现文件 | 说明 |
|------|----------|------|
| 子进程生命周期管理 | [HarnessClient.java](file:///Users/mali/Documents/code/deepseek-harness4j/sdk/src/main/java/com/deepseek/harness4j/client/HarnessClient.java) | ProcessBuilder 启动/destroy 关闭 |
| JSON-RPC 2.0 协议 | [HarnessClient.java](file:///Users/mali/Documents/code/deepseek-harness4j/sdk/src/main/java/com/deepseek/harness4j/client/HarnessClient.java) | Jackson 序列化，换行分隔，stdin/stdout |
| 请求-响应匹配 | [HarnessClient.java](file:///Users/mali/Documents/code/deepseek-harness4j/sdk/src/main/java/com/deepseek/harness4j/client/HarnessClient.java#L57) | UUID + BlockingQueue |
| 通知路由与订阅 | [HarnessClient.java](file:///Users/mali/Documents/code/deepseek-harness4j/sdk/src/main/java/com/deepseek/harness4j/client/HarnessClient.java) | 多订阅者 + 过滤器 + session 父子树 |
| Session 父子关系追踪 | [HarnessClient.java](file:///Users/mali/Documents/code/deepseek-harness4j/sdk/src/main/java/com/deepseek/harness4j/client/HarnessClient.java#L622) | 从 `subagent.started` 通知构建关系树 |
| Turn 执行状态机 | [Session.java](file:///Users/mali/Documents/code/deepseek-harness4j/sdk/src/main/java/com/deepseek/harness4j/Session.java) | inbox receipt -> 收集事件 -> 等 idle |
| 内存事件收集 | [Session.java](file:///Users/mali/Documents/code/deepseek-harness4j/sdk/src/main/java/com/deepseek/harness4j/Session.java#L38) | `session.event` 通知提取为事件列表 |
| 运行时二进制定位 | [RuntimeResolver.java](file:///Users/mali/Documents/code/deepseek-harness4j/sdk/src/main/java/com/deepseek/harness4j/runtime/RuntimeResolver.java) | 平台/架构检测，exe/node 模式 |
| 默认配置注入 | [HarnessClient.java](file:///Users/mali/Documents/code/deepseek-harness4j/sdk/src/main/java/com/deepseek/harness4j/client/HarnessClient.java) | 零配置时注入 `runtime/cordis.yml` |
| 高层 API 门面 | [DeepSeekHarness.java](file:///Users/mali/Documents/code/deepseek-harness4j/sdk/src/main/java/com/deepseek/harness4j/DeepSeekHarness.java) | `run(input)` 一行调用 |
| Builder 配置 | [DeepSeekHarnessConfig.java](file:///Users/mali/Documents/code/deepseek-harness4j/sdk/src/main/java/com/deepseek/harness4j/DeepSeekHarnessConfig.java) | 14 字段，含 provider/model/cwd/cordis 等 |
| Spring Boot 集成 | [DeepSeekHarnessAutoConfiguration.java](file:///Users/mali/Documents/code/deepseek-harness4j/spring-boot-starter/src/main/java/com/deepseek/harness4j/spring/DeepSeekHarnessAutoConfiguration.java) | 自动配置 + Properties + Template |
| 6 种异常体系 | [error/](file:///Users/mali/Documents/code/deepseek-harness4j/sdk/src/main/java/com/deepseek/harness4j/error) | HarnessException 基类 + 5 子类 |
| 构建工具链 | [build/](file:///Users/mali/Documents/code/deepseek-harness4j/sdk/src/main/java/com/deepseek/harness4j/build) | RuntimeBuildHook + PlatformManifest + ReleaseVersion |

---

## 8. Java 侧实现可行性分析与实现方案

### 8.1 上游 Python SDK 对比结论

通过研究上游仓库 `deepseek-ai/deepseek-harness`（master 分支）的 Python SDK 源码（`python/sdk/src/deepseek_harness/` 下 `api.py`、`client.py`、`models.py`、`errors.py`），确认:

**上游 Python SDK 同样不提供这些特性的客户端 API**:

| 能力 | Python SDK | Java SDK (4j) | 上游运行时 |
|------|:---:|:---:|:---:|
| session resume | 间接（同 sessionId 再 `run()`） | 间接（同 sessionId 再 `run()`） | `getOrCreateSession` + `prepare()` |
| session fork | 无 | 无 | 无 JSON-RPC 方法 |
| session search | 无 | 无 | 上游 Web UI 有，SDK 协议无 |
| session replay | 无 | 无 | 上游 Web UI 有，SDK 协议无 |
| JSONL 日志读取 | 无 | 无 | 运行时写 `.jsonl.zstd` |
| 插件热加载/卸载 | 无 | 无 | Cordis 运行时内部支持 |
| Cordis effect/dispose | 无 | 无 | TypeScript 运行时内核 |

**JSON-RPC 协议仅 3 个请求方法**: `initialize` / `session/prompt` / `shutdown`。上游 `dsh-sdk-jsonrpc-server` 插件（`server.ts`）不注册任何 session 管理方法。

### 8.2 可行性分类总览

| 缺口 | Java 侧可行性 | 依据 | 分类 |
|------|:---:|------|:---:|
| **SessionLog 读取 (list/read)** | ✅ 可行 | `.jsonl`（compression:none）为 UTF-8 逐行 JSONL，Java 直接 `Files.readAllLines` | **P0 纯 Java** |
| **SessionLog zstd 解码** | ✅ 可行 | `.jsonl.zstd` 为标准 Zstandard 帧序列，用 `zstd-jni` 逐帧解码 | **P1 加依赖** |
| **搜索 (search)** | ✅ 可行 | 扫日志目录，按事件 `type`/`data` 子字段过滤 | **P0 纯 Java** |
| **重放 (replay)** | ✅ 可行 | 读日志 + 按事件类型投影（等价 "replay 视图"） | **P0 纯 Java** |
| **续写 (resume)** | ✅ 已实现 | 同 sessionId 再 `run()`，运行时从日志恢复 | **已完成** |
| **日志级 fork** | ✅ 可行 | 复制 `session.jsonl` 到新目录 + 改 header 的 `id`/`lineage` | **P2 纯 Java** |
| **运行时续跑 fork** | ❌ 不可行 | JSON-RPC 无 `session/fork` 方法 | **需上游改** |
| **Cordis 时空组合性 (3)** | ❌ 不可行 | effect/dispose/逆变换是 TypeScript 运行时内核，协议层不可见 | **不可实现** |
| **热插拔/逆向清理 (5)** | ❌ 不可行 | 运行时 `cordis.yml` 启动时整体加载，无热增删 JSON-RPC 方法 | **不可实现** |

### 8.3 实现方案一: SessionLog 读取器 (P0)

**定位**: 新增 `sdk/src/main/java/com/deepseek/harness4j/log/SessionLog.java`（纯读取，零依赖运行时）。

**落盘格式**（已核实上游 `session-persistence-jsonl/src/index.ts`）:

- 路径: `<sessionRoot>/<projectDir(cwd 哈希化)>/<sessionId>/session.jsonl`（compression:none）或 `session.jsonl.zstd`（默认）
- `compression:none`: 每行一个 JSON 对象，UTF-8，可直接 `Files.readAllLines`
- `.jsonl.zstd`: 独立 Zstandard 帧序列，首帧恰为 1 行 header（`{formatVersion, id, cwd, lineage, createdAt, ...}`），后续每批 append 一帧；帧带 checksum。标准 zstd 格式，与 Node 内置 zstd 互操作
- 事件行结构: `SessionEvent` 判别联合，`type` 标签与实时通知流 `session.event` 的事件结构**完全一致**

**API 设计**:

```java
package com.deepseek.harness4j.log;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 读取运行时持久化的会话日志（JSONL / JSONL.zstd）。
 *
 * <p>日志由运行时的 dsh-session-persistence-jsonl 插件写入，
 * Java SDK 只负责读取——不与运行时子进程交互，可在 harness 关闭后独立使用。
 */
public final class SessionLog {

    /** 会话日志头部信息（每个 session.jsonl 的首行）。 */
    public record Header(
            String formatVersion,
            String id,
            String cwd,
            List<String> lineage,
            long createdAt) {}

    /** 搜索条件。 */
    public record Query(
            String eventType,        // 按事件 type 过滤（如 "tool/call"），null=全部
            String textContains,     // 按事件 data 中的文本模糊匹配，null=不过滤
            Long fromTimestamp,      // 起始时间戳（毫秒），null=不限制
            Long toTimestamp) {}     // 结束时间戳（毫秒），null=不限制

    /**
     * 枚举 sessionRoot 下全部已持久化的会话（读各 header 首行）。
     * @param sessionRoot DSH_SESSION_ROOT 配置的目录路径
     */
    public static List<Header> list(Path sessionRoot);

    /**
     * 按 sessionId 读取完整事件流（自动识别 jsonl / jsonl.zstd）。
     * @param sessionRoot DSH_SESSION_ROOT 目录
     * @param sessionId   会话 ID（如 "session-abc123"）
     * @return 事件列表，每个事件是 Map<String,Object>（含 type、data 等字段）
     */
    public static List<Map<String, Object>> read(Path sessionRoot, String sessionId);

    /**
     * 回放视图：同 read，但仅返回对回放有意义的事件类型子集
     * （assistant/message, tool/call, tool/result, turn/start, turn/end, user/message）。
     */
    public static List<Map<String, Object>> replay(Path sessionRoot, String sessionId);

    /**
     * 搜索：按事件 type 与 data 子字段过滤。
     */
    public static List<Map<String, Object>> search(
            Path sessionRoot, String sessionId, Query query);

    /**
     * 日志级 fork：复制源会话日志到新 ID，lineage 追加源 ID。
     * 产出"从源历史继续"的独立会话文件，可用新 sessionId 调 run() 续跑。
     * @param sessionRoot  DSH_SESSION_ROOT 目录
     * @param sourceId     源会话 ID
     * @param newId        新会话 ID（null 则自动生成）
     * @return 新会话的 Header
     */
    public static Header fork(Path sessionRoot, String sourceId, String newId);
}
```

**实现要点**:

1. **目录遍历**: `list()` 扫描 `<sessionRoot>/<projectDir>/<sessionId>/` 结构，读取每个目录下 `session.jsonl` 或 `session.jsonl.zstd` 的首行/header 帧
2. **JSONL 读取**（compression:none）: `Files.readAllLines(path, UTF_8)` -> 逐行 `ObjectMapper.readValue(line, Map.class)`
3. **Zstd 解码**（compression:zstd）: 引入 `com.github.luben:zstd-jni` 依赖，用 `ZstdInputStream` 逐帧解码 -> 每帧内逐行 JSON 反序列化
4. **自动识别**: 检查文件扩展名（`.jsonl` vs `.jsonl.zstd`）或 magic bytes（`28 b5 2f fd`）
5. **事件过滤**: `replay()` 过滤 `type` 为 `assistant/message|tool/call|tool/result|turn/start|turn/end|user/message` 的事件；`search()` 按 `Query` 条件过滤
6. **日志级 fork**: 复制目录 -> 解析 header -> 修改 `id` 字段为 newId -> `lineage` 追加 sourceId -> 写回 header 行 -> 其余事件行原样复制

**依赖变更**:

```xml
<!-- sdk/pom.xml 新增（仅 zstd 读取需要） -->
<dependency>
    <groupId>com.github.luben.zstd-jni</groupId>
    <artifactId>zstd-jni</artifactId>
    <version>1.5.6-1</version>
</dependency>
```

> P0 阶段先实现 `compression:none` 路径，零新依赖。P1 阶段加 zstd-jni 支持默认格式。

**测试策略**:

1. 复用 `FakeRuntime`（`sdk/src/test/java/.../test/FakeRuntime.java`）写真实日志文件到临时目录
2. 用 `minimal.cordis.yml`（已硬编码 `compression: none`）验证 `list/read/replay/search` 结果与运行时事件流一致
3. `fork` 测试: fork 后用新 sessionId 调 `harness.run()` 验证运行时能从 fork 日志恢复

### 8.4 实现方案二: Session 高级 API 封装 (P0)

**定位**: 扩展现有 [Session.java](file:///Users/mali/Documents/code/deepseek-harness4j/sdk/src/main/java/com/deepseek/harness4j/Session.java) 和 [DeepSeekHarness.java](file:///Users/mali/Documents/code/deepseek-harness4j/sdk/src/main/java/com/deepseek/harness4j/DeepSeekHarness.java)，增加便捷方法。

**新增 API**:

```java
// DeepSeekHarness.java 新增

/**
 * 读取一个已持久化的会话日志（不需要启动运行时子进程）。
 * @param sessionId 已存在的会话 ID
 * @return 会话日志的事件列表
 */
public List<Map<String, Object>> readSessionLog(String sessionId) {
    return SessionLog.read(Path.of(config.sessionRoot()), sessionId);
}

/**
 * 搜索会话日志中的事件。
 */
public List<Map<String, Object>> searchSessionLog(
        String sessionId, SessionLog.Query query) {
    return SessionLog.search(Path.of(config.sessionRoot()), sessionId, query);
}

/**
 * 回放一个会话的执行流（仅关键事件类型）。
 */
public List<Map<String, Object>> replaySessionLog(String sessionId) {
    return SessionLog.replay(Path.of(config.sessionRoot()), sessionId);
}

/**
 * 列出所有已持久化的会话。
 */
public List<SessionLog.Header> listSessions() {
    return SessionLog.list(Path.of(config.sessionRoot()));
}

/**
 * 从已有会话 fork 出一个新会话（日志级 fork）。
 * @param sourceId 源会话 ID
 * @return 新会话的 Session 对象（可用 run() 续跑）
 */
public Session forkSession(String sourceId) {
    String newId = "session-" + UUID.randomUUID().toString().replace("-", "");
    SessionLog.fork(Path.of(config.sessionRoot()), sourceId, newId);
    return startSession(newId);
}
```

**关键约束**:
- `readSessionLog` / `searchSessionLog` / `replaySessionLog` / `listSessions` / `forkSession` **不需要启动运行时子进程**，纯文件操作
- `forkSession` 返回的 `Session` 对象调 `run()` 时才启动子进程，运行时通过 sessionId 从日志恢复
- 所有方法要求 `config.sessionRoot()` 非 null，否则抛 `IllegalStateException`

### 8.5 实现方案三: 运行时级 session/fork 协议扩展 (P3，需上游配合)

**当前限制**: JSON-RPC 协议仅 `initialize` / `session/prompt` / `shutdown` 三个方法。要让 agent 从 fork 点真正在运行时内继续，需要上游 `dsh-sdk-jsonrpc-server` 新增方法。

**上游需新增的 JSON-RPC 方法**:

```json
{
  "method": "session/fork",
  "params": {
    "sourceId": "session-abc123",
    "newId": "session-def456",
    "atSeq": null
  },
  "result": {
    "sessionId": "session-def456"
  }
}
```

运行时内部实现: `persistence.prepare(sourceId)` -> 创建新 agent -> 注入历史事件 -> 注册新 sessionId。

**Java 侧封装**（上游合并后，4j 仅需 ~20 行）:

```java
// HarnessClient.java 新增
public String forkSession(String sourceId, String newId) {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("sourceId", sourceId);
    params.put("newId", newId);
    Map<String, Object> result = request("session/fork", params);
    return (String) result.get("sessionId");
}
```

**状态**: 需向上游提交 PR。当前不可用。

### 8.6 不可实现项分析: Cordis 内核 (特性 3、5)

**为什么不能在 Java 侧实现**:

1. **Cordis 是 TypeScript 插件框架内核**，预编译进 `dsh-jsonrpc-agent` 二进制。其核心机制（`ctx.effect()` / `ctx.on()` / `inject` / fiber 生命周期）是 TypeScript 运行时特性，Java 侧无法复刻
2. **JSON-RPC 协议不暴露内部机制**: 运行时启动时整体加载 `cordis.yml`，无"热增删单个插件"的 JSON-RPC 方法
3. **复刻等于重写整个运行时**: Cordis 的时空可组合性演算（逆变换回滚、依赖反向通知、动态组合证明）从单个组件传导到整个系统，这不是客户端能代理的

**可达的折中方案**:

| 折中方案 | 描述 | 可行性 |
|----------|------|:---:|
| **组合热切换** | `harness.close()` + 换 `cordis()` 路径重开子进程 | ✅ 已可用 |
| **协议代理** | 若上游新增插件管理 JSON-RPC 方法，用泛型 `request()` 薄封装 | ⚠️ 需上游改 |
| **Java 侧插件状态查询** | 若上游新增 `plugins/list` 方法，Java 侧封装查询 | ⚠️ 需上游改 |

> `HarnessClient.request()` 已是泛型化的（[HarnessClient.java#L209-213](file:///Users/mali/Documents/code/deepseek-harness4j/sdk/src/main/java/com/deepseek/harness4j/client/HarnessClient.java#L209)），上游任何新协议方法只需 ~20 行即可接入。

### 8.7 实现优先级与工作量估算

| 优先级 | 增量 | 新增/修改文件 | 新增依赖 | 价值 |
|:---:|------|:---:|:---:|------|
| **P0** | `SessionLog.list/read/replay/search`（compression:none） | +1 类 + 2 测试 | 无 | 补上"回放/搜索"叙事，纯客户端闭环 |
| **P0** | `DeepSeekHarness` 高级 API 封装（`readSessionLog` 等） | 改 1 类 | 无 | 用户一行调用即可读日志 |
| **P1** | `zstd-jni` 解码器支持 `.jsonl.zstd` | 改 1 类 | zstd-jni | 覆盖默认落盘格式 |
| **P2** | 日志级 fork（复制 + 改 lineage） | 改 1 类 + 1 测试 | 无 | 补"分支"叙事 |
| **P3** | 运行时 `session/fork` 协议方法 + Java 薄封装 | 改 1 类 | 无 | 真·运行时 fork（需上游合并） |

> **边界说明**: P0-P2 全部在 Java 客户端自足实现，不改运行时、不动上游。P3 才需要上游协议配合。Cordis 时空组合/热插拔（特性 3、5）**不列入**——属运行时内核，Java 侧保持"语言无关参考"定位。

### 8.8 实现后的特性评分变化预测

| 特性 | 当前评分 | P0-P2 完成后 | P3 完成后 |
|------|:---:|:---:|:---:|
| 1. 一切皆插件 | B | B（无变化，已通过配置可用） | B |
| 2. 每次运行可回放 | B | **A-**（list/read/replay/search/fork 全部 Java 可用） | **A**（运行时级 fork） |
| 3. Cordis 时空可组合性 | B | B（不可在 Java 实现） | B |
| 4. 全轨迹可追溯 | B | **A-**（Java 侧可读/搜索/回放完整轨迹） | A- |
| 5. 动态生命周期/DI/逆向清理 | B | B（不可在 Java 实现） | B |

---

## 9. 总结

### 4j 是什么

deepseek-harness4j 是上游 DeepSeek Harness 的 **Java 客户端瘦封装**。它:
- 逐行移植了 Python SDK (`deepseek_harness`) 的 API 接口
- 通过 stdio JSON-RPC 2.0 驱动预编译的上游运行时二进制 (`dsh-jsonrpc-agent`)
- 运行时二进制包含完整的 Cordis 插件框架和所有上游插件
- Java 代码不实现任何 agent 逻辑、插件管理、session 持久化

### 五大特性的实际状态

| 特性 | Java 代码 | 运行时二进制 | 用户可用 |
|------|:---:|:---:|:---:|
| 1. 一切皆插件 | 0% | 100% | 是 (通过配置) |
| 2. 每次运行可回放 | 0% | 100% | 部分 (resume 可用, fork/search/replay 未封装) |
| 3. Cordis 时空可组合性 | 0% | 100% | 是 (运行时保证) |
| 4. 全轨迹可追溯 | 10% | 100% | 是 (RunResult.events + JSONL 文件) |
| 5. 动态生命周期/DI/逆向清理 | 0% | 100% | 是 (运行时内部) |

### 是否 "100% 实现"

**如果 "实现" 指 Java 源码直接编写了这些特性的核心逻辑**: **否，0% 实现**（特性 4 有 10% 的内存事件收集）。

**如果 "实现" 指 4j 项目作为一个整体（含随包二进制）让用户可以使用这些特性**: **是，100% 可用**。所有特性都通过上游运行时二进制完整提供，Java SDK 通过配置透传和 JSON-RPC 委托让用户能够使用它们。

**但存在封装缺口**: session fork/search/replay、事件持久化读取、插件热加载/卸载等高级操作，Java SDK 未提供封装 API。

### 可补的增量（本章核心结论）

- **P0-P2（纯 Java，不改运行时）**: `SessionLog` 读取器 + `DeepSeekHarness` 高级 API 封装。实现后特性 2 和 4 的 Java 侧评分从 B 升至 A-，用户可一行调用读取/搜索/回放/分支会话日志
- **P3（需上游协议扩展）**: `session/fork` JSON-RPC 方法。上游合并后 4j 仅需 ~20 行接入
- **不可实现**: Cordis 内核（特性 3、5）是 TypeScript 运行时内部机制，Java 侧保持"语言无关参考"定位，不重写

---

## 10. 与 review-5-capabilities.md 的交叉校验与补充

> 本节对照已有文档 `docs/review-5-capabilities.md`（下称"旧版"）逐项校验，修正实现判断差异，补充遗漏方案。

### 10.1 实现判断修正

#### 差异 1: 特性 2/4 审查表中"运行时支持 fork/search/replay"表述不准确

**问题**: 本报告第 3 节（特性 2）和第 5 节（特性 4）的审查表中写道:

> | fork (分叉) | 否 | 是 | 运行时支持 |
> | search (搜索) | 否 | 是 | 运行时支持全文搜索 |
> | replay (重放) | 否 | 是 | 运行时支持 |

**修正**: 这三行"运行时提供=是"的判定**不准确**。旧版文档更精确:

- **fork**: 运行时 JSON-RPC 协议**无** `session/fork` 方法。日志级 fork 可在客户端做（复制文件），但"运行时内 fork 续跑"❌ 不可行
- **search**: 运行时 JSON-RPC 协议**无** `session/search` 方法。上游 Web UI 可能有此能力（通过 `packages/session-query/` 插件），但 SDK 协议不暴露
- **replay**: 运行时 JSON-RPC 协议**无** `session/replay` 方法。回放只能由客户端读 JSONL 文件自行实现

**正确表述**:

| 子项 | Java 实现 | 运行时 JSON-RPC 暴露 | 上游 Web UI/CLI 有 | 说明 |
|------|:---:|:---:|:---:|------|
| fork (分叉) | 否 | **否** | 是 | 日志级 fork 可在 Java 侧做；运行时续跑需上游协议扩展 |
| search (搜索) | 否 | **否** | 是 | 上游 `session-query` 插件供 Web UI 用，SDK 协议不暴露 |
| replay (重放) | 否 | **否** | 是 | 客户端读 JSONL 文件可自行回放 |

#### 差异 2: 特性 2 中 resume 机制缺少上游源码引用

**问题**: 本报告说"运行时: 从 JSONL 恢复"但未给出源码依据。

**补充**: 旧版文档引用了具体上游源码位置:
- 运行时 `getOrCreateSession` 复用 agent（`server.ts:203-215`）
- `prepare()` 加载既有日志恢复会话状态（`session-persistence-jsonl/src/index.ts:155-163`）
- 即: 同 sessionId 再 `run()` 时，运行时自动从磁盘 JSONL 恢复上下文，无需 SDK 显式调用 resume

### 10.2 遗漏的方案与建议

#### 遗漏 1: `Session.resume()` 显式别名（零成本改进）

**旧版建议**: 添加 `Session.resume(sessionId)` 作为 `startSession(sessionId)` 的语义别名，以呼应"回放"叙事。

**分析**: 当前 `DeepSeekHarness.startSession(sessionId)` 已实现续写功能，但方法名不体现"恢复"语义。添加一个别名方法零成本且提升 API 自文档化:

```java
// DeepSeekHarness.java 新增（零成本别名）
/**
 * 恢复一个已有会话（等价于 startSession(sessionId)，但语义更明确）。
 * 运行时会从 JSONL 日志自动加载该会话的完整历史。
 */
public Session resume(String sessionId) {
    return startSession(sessionId);
}
```

**优先级**: P0（与 SessionLog 同批，仅 5 行代码）

#### 遗漏 2: 压缩格式默认值策略

**旧版建议**: 不修改随包 `runtime/cordis.yml` 的默认 `compression: zstd`（与上游保持一致），但在文档中提供可选的 `compression: none` 变体，让用户在需要纯 Java 零依赖读取日志时自行选择。

**分析**: 这是一个重要的部署决策:
- 默认 `zstd` 压缩：节省磁盘空间，但 Java 侧读取需引入 `zstd-jni` 依赖
- 切换 `none`：Java 侧零依赖读取，但日志文件更大

**补充建议**: 在 `DeepSeekHarnessConfig` 中增加一个 `compression` 便捷字段，让用户无需手写 cordis.yml 即可切换:

```java
// DeepSeekHarnessConfig.Builder 新增
public Builder sessionCompression(String mode) {
    // "zstd" (默认) 或 "none"
    // 内部生成一个覆盖 sessions 插件 config 的临时 cordis.yml 片段
}
```

#### 遗漏 3: Spring Boot Starter 集成

**问题**: 第 8.4 节的 `DeepSeekHarness` 高级 API 封装未提及 Spring Boot Starter 的对应扩展。

**补充**: `DeepSeekHarnessTemplate` 和 `DeepSeekHarnessProperties` 也需要同步扩展:

```java
// DeepSeekHarnessTemplate.java 新增
public List<Map<String, Object>> readSessionLog(String sessionId) {
    return delegate.readSessionLog(sessionId);
}
public List<SessionLog.Header> listSessions() {
    return delegate.listSessions();
}
```

```yaml
# application.yml 新增可选配置
deepseek:
  harness:
    session-root: /data/sessions
    session-compression: none  # 可选，方便 Java 侧零依赖读取
```

#### 遗漏 4: 实时事件流回调（特性 4 已有能力）

**问题**: 特性 4 的分析聚焦于"事后回看"，遗漏了"实时透视"能力。

**补充**: `Session.run(input, onNotification)` 的 `onNotification` 回调已提供**实时事件流**:

```java
// 用户可在 agent 执行期间实时观察每一步
harness.run("Fix the bug", sessionId, notification -> {
    if ("session.event".equals(notification.method())) {
        Map<String, Object> event = JsonValues.asObject(
                notification.payload().get("event"));
        String type = (String) event.get("type");
        // 实时看到: assistant/chunk, tool/call, tool/result, reasoning-chunks ...
        System.out.printf("[%s] %s%n", type, event.get("data"));
    }
});
```

这意味着特性 4（全轨迹可追溯）在 Java 侧不仅有事后 `RunResult.events()`，还有**执行中的实时流**。实时流覆盖了 `assistant/chunk`（token 级流式）、`reasoning-chunks`（推理过程）、`tool/call`/`tool/result`（工具调用）等全部事件类型。

#### 遗漏 5: replay() 事件类型子集不够完整

**问题**: 第 8.3 节 `replay()` 方法的事件类型子集列了 6 种，遗漏了推理过程相关事件。

**修正**: 上游实测的 37 事件会话日志包含 17 种事件类型（见 `deepseek-harness4j-使用指南.md` 第 1181-1185 行）。`replay()` 应包含更完整的子集:

| 分类 | 事件 type | 含义 | 当前 replay() | 修正后 |
|------|----------|------|:---:|:---:|
| 用户输入 | `user/message` | 用户消息 | ✅ | ✅ |
| 助手输出 | `assistant/message` | 助手完整消息 | ✅ | ✅ |
| 助手流式 | `assistant/chunk` | token 级流式分片 | ❌ | ✅ |
| 推理过程 | `reasoning-chunks` | CoT 推理链分片 | ❌ | ✅ |
| 文本流式 | `text-chunks` | 文本输出分片 | ❌ | ✅ |
| 工具调用 | `tool/call` | 工具调用请求 | ✅ | ✅ |
| 工具结果 | `tool/result` | 工具调用结果 | ✅ | ✅ |
| 工具流式 | `tool-call-chunks` | 工具调用流式分片 | ❌ | ✅ |
| 轮次边界 | `turn/start` | 轮次开始 | ✅ | ✅ |
| 轮次边界 | `turn/end` | 轮次结束 | ✅ | ✅ |
| 步骤边界 | `step/start` | 步骤开始 | ❌ | ✅ |
| 步骤边界 | `step/end` | 步骤结束 | ❌ | ✅ |
| 请求元信息 | `request/header` | 模型请求头 | ❌ | ❌（仅诊断用） |
| 请求上下文 | `request/context` | 请求上下文注入 | ❌ | ❌（仅诊断用） |
| Inbox 回执 | `agent/inbox/spliced` | 提示词已被接收 | ❌ | ❌（基础设施） |
| 会话元信息 | `session` / `session/title` | 会话元数据 | ❌ | ❌（仅元信息） |

**修正后 replay() 事件类型集**: `user/message, assistant/message, assistant/chunk, reasoning-chunks, text-chunks, tool/call, tool/result, tool-call-chunks, turn/start, turn/end, step/start, step/end`（12 种）

#### 遗漏 6: 并发读取安全性

**问题**: `SessionLog.read()` 在运行时子进程正在追加写入同一文件时可能读到不完整的最后一行。

**补充实现要求**:
- JSONL（compression:none）: 最后一行可能被截断。`read()` 应捕获 `IOException` 并跳过最后一行不完整 JSON
- JSONL.zstd: 最后一帧可能不完整。`ZstdInputStream` 解码时可能抛异常，应捕获并跳过末尾不完整帧
- 文档应注明: `SessionLog.read()` 返回的是**调用时刻的快照**，不保证包含运行时正在追加的最新事件

#### 遗漏 7: 跨会话搜索

**问题**: 当前 `search()` 方法只搜单个 sessionId 的日志。用户可能需要"在所有会话中搜索包含某关键词的事件"。

**补充 API**:

```java
/**
 * 跨全部会话搜索事件。
 * @param sessionRoot DSH_SESSION_ROOT 目录
 * @param query       搜索条件
 * @return 匹配事件列表，每条含 sessionId + event
 */
public static List<SearchHit> searchAll(Path sessionRoot, Query query);

public record SearchHit(String sessionId, Map<String, Object> event) {}
```

#### 遗漏 8: `HarnessClient.request()` 逃生舱文档化

**问题**: 用户今天就需要调用 JSON-RPC 方法（包括上游未来可能新增的方法），但 `request()` 的通用能力未文档化。

**补充**: `HarnessClient` 已提供泛型 `request()` 方法（[HarnessClient.java#L209-213](file:///Users/mali/Documents/code/deepseek-harness4j/sdk/src/main/java/com/deepseek/harness4j/client/HarnessClient.java#L209)），用户可直接调用任何 JSON-RPC 方法:

```java
// 逃生舱: 直接发送任意 JSON-RPC 请求
Map<String, Object> params = Map.of("sessionId", "session-abc123");
Map<String, Object> result = harness.client().request("session/anything", params);
```

同理，`notify()` 和 `respond()` 方法也已暴露，支持发送通知和回复运行时的 incoming request。

#### 遗漏 9: `llm.request` 桥接模式与特性 1 的关联

**问题**: 特性 1 分析中说"LLM 可插拔"仅通过 cordis.yml 配置实现，遗漏了 SDK 层的桥接模式。

**补充**: JSON-RPC 协议定义了 `llm.request` 入站请求（运行时 -> 客户端，需响应）。在桥接/演示配置中，运行时会将 LLM 请求转发给 SDK 客户端，由客户端代为调用模型。这意味着:

- Java 代码**可以**参与 LLM 路由--通过实现 `llm.request` 的响应逻辑
- `HarnessClient` 已有 `respond()` 方法支持回复 incoming request
- 这是一种"客户端侧 LLM 适配器"模式，虽然不是标准用法，但为 Java 侧自定义 LLM 提供了一条路径

**与特性 1 的关联**: "模型可插拔"不仅通过 cordis.yml 换插件实现，还可以通过 `llm.request` 桥接让 Java 代码直接处理模型调用。

#### 遗漏 10: fork 的 `atSeq` 参数与 `lineage` 语义

**问题**: 第 8.5 节提到 `atSeq` 参数和 `lineage` 字段但未解释含义。

**补充**:
- **`lineage`**: 会话的血统链--从根会话到当前会话的 ID 列表。fork 时将源会话 ID 追加到 lineage 末尾，形成 `[rootId, ..., sourceId]`。这使得任何会话都能追溯到其完整的派生历史
- **`atSeq`**: fork 的事件序列号偏移量。`null` 表示从会话末尾 fork；指定数字则从第 N 个事件处 fork（部分 fork）。这允许"回到历史某个时间点重新分支"，而非只能从最新状态 fork

### 10.3 修订后的实现优先级表

| 优先级 | 增量 | 新增/修改文件 | 新增依赖 | 价值 | 与旧版差异 |
|:---:|------|:---:|:---:|------|------|
| **P0** | `Session.resume()` 显式别名 | 改 1 类（5 行） | 无 | API 自文档化，呼应"回放"叙事 | **新增**（旧版有建议，本报告遗漏） |
| **P0** | `SessionLog.list/read/replay/search`（compression:none） | +1 类 + 2 测试 | 无 | 回放/搜索纯客户端闭环 | replay 事件类型集扩展到 12 种 |
| **P0** | `DeepSeekHarness` 高级 API 封装 | 改 1 类 | 无 | 一行调用读日志 | -- |
| **P0** | `HarnessClient.request()` 逃生舱文档化 | 改文档 | 无 | 用户今天就能调用任意 JSON-RPC | **新增** |
| **P1** | `zstd-jni` 解码器 | 改 1 类 | zstd-jni | 覆盖默认落盘格式 | -- |
| **P1** | Spring Boot Starter 同步扩展 | 改 2 类 | 无 | Spring 用户也能用 | **新增** |
| **P1** | 跨会话搜索 `searchAll()` | 改 1 类 | 无 | 全局事件检索 | **新增** |
| **P1** | 并发读取安全处理 | 改 1 类 | 无 | 运行时写入时安全读取 | **新增** |
| **P2** | 日志级 fork + `lineage`/`atSeq` | 改 1 类 + 1 测试 | 无 | 补"分支"叙事 | 补充 atSeq 语义 |
| **P3** | 运行时 `session/fork` 协议方法 + Java 薄封装 | 改 1 类 | 无 | 真·运行时 fork | -- |

> **与旧版对比**: 新增 P0 的 `resume()` 别名和逃生舱文档化；P1 新增 Spring Boot 集成、跨会话搜索、并发安全；replay 事件类型集从 6 种扩展到 12 种。旧版的 P0-P3 骨架保留不变。

### 10.4 修订后的特性评分预测

| 特性 | 当前评分 | P0-P2 完成后 | P3 完成后 | 变化说明 |
|------|:---:|:---:|:---:|------|
| 1. 一切皆插件 | B | B | B | 补充 `llm.request` 桥接说明后，Java 侧 LLM 路由路径更清晰 |
| 2. 每次运行可回放 | B | **A-** | **A** | `resume()` 别名 + SessionLog 全套 + fork |
| 3. Cordis 时空可组合性 | B | B | B | 不可在 Java 实现，无变化 |
| 4. 全轨迹可追溯 | B | **A** | A | 补充实时流回调 + replay 12 种事件 + 跨会话搜索后升至 A |
| 5. 动态生命周期/DI/逆向清理 | B | B | B | 不可在 Java 实现，无变化 |

> 特性 4 评分从 A- 上调至 A: 因为补充了实时事件流回调（`onNotification`）和更完整的 replay 事件类型集后，Java 侧的轨迹透明度已非常全面--实时透视 + 事后回放 + 跨会话搜索三路齐备。

---

*本报告基于 2026-08-14 的代码状态生成，已核实上游 `deepseek-ai/deepseek-harness` master 分支（2026-08-13）的 Python SDK 源码和 JSON-RPC 协议定义。第 10 节交叉校验对照 `docs/review-5-capabilities.md`。*
