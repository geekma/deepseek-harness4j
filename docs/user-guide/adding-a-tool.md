# 工具编写参考

[English](adding-a-tool.en.md) | 中文

> 本文件是上游 `docs/cookbook/adding-a-tool.md`（+`.zh.md`）的移植。模型可见工具是**运行时侧 TypeScript 插件**（`ctx.tools.register(defineTool(...))`），与客户端语言无关。Java/Python SDK 只负责发起 `session/prompt` 并消费会话事件；工具的契约、UI 渲染意图与验证要求对任何客户端语言都成立。

一个模型可见工具必须满足的契约参考。要按顺序编写第一个工具，请跟随上游 [Build a tool](../user/develop/basic/tool.md)。`packages/shell/tool-bash` 是生产级三包示例。

## 最小形态

```ts
import { readFile } from 'node:fs/promises'
import type { Context } from '@deepseek-ai/cordis'
import { defineTool } from '@deepseek-ai/dsh-tools'

export const name = 'my-tool'
export const inject = ['tools']

export function apply(ctx: Context) {
  ctx.tools.register(defineTool({
    name: 'read_file',
    description: 'Read a file from disk.',          // what the model sees
    parameters: {
      path: { type: 'string', required: true, description: 'Absolute path' },
      limit: { type: 'number' },                     // optional by default
    },
    output: {
      schema: { type: 'string' },
      render: (_args, value) => [{ type: 'text', text: value }],
    },
    async execute(args, exec) {
      // args is TYPED from the schema: { path: string; limit?: number }
      // exec carries immutable identity + token; signal is the operational field
      return readFile(args.path, { encoding: 'utf8', signal: exec.signal })
    },
  }))
}
```

注册基于 effect：释放插件 fiber 会注销工具。schema 自动流入系统提示词组装。

## `execute()` 契约规则

- **参数替你校验。** `defineTool` 在 `execute` 运行前按统一 `ParameterSchemaSpec` 校验模型生成的 `arguments`（类型、必填键、字面量约束、exact-one 联合与嵌套值），因此 `execute` 内 args 匹配 `InferArgs`。显式对象节点声明 `additionalProperties: true | false`；隐式参数根保持开放。你仍需手检 DSL 无法表达约束（非空字符串、正数、跨字段规则）。直接注册的原始 JSON-Schema 工具自管输入校验。
- **注册借用你的只读定义。** 类型化同进程贡献不是序列化边界；注册后不要修改其 schema 或替换回调。`schemas()` 只物化显式模型可见投影。要热替换工具，释放其所属 effect 再注册替代品；回调闭包内的可变状态仍是普通插件状态。
- **执行身份受保护。** 注册表用一次递归遍历把 `arguments` 物化为分离的无损 JSON，在策略开始前冻结该值，并分配不透明的 `exec.token`；`callId`、`name`、`arguments`、`agent`、`token`、必需且由调用方持有的 `signal`，以及可选的外层传输 `parent` token 在整个分派中不可变。`parent` 仅身份，不暴露任何存活的外层执行。把 `args` 当只读输入。只有 around-dispatch 包装器获得可变视图，且它可以替换并恢复必需的 `exec.signal` 以施加截止时间，但不能移除它。
- **声明并返回一个规范 JSON 值。** `output.schema` 使用 `ValueSchemaSpec`，根可为对象、数组、标量或 null。`execute` 只返回推断值；注册表将其快照为无损 JSON、校验、冻结，并传给 `output.render(args, value)`。不要从 body 返回内容块，也不要让调用方解析散文来取 id 与字段。
- **抛出或返回无效值即 `isError`。** 注册表在观察者运行前捕获抛错并收容 schema、渲染器、元数据投影器与无损 JSON 失败。基础设施失败用抛出。成功的领域结果用规范值表达，即使其 Native 渲染器解释非理想状态（如非零进程退出）。
- **遵守 `exec.signal`。** 触发时取消进行中的工作。
- **用 `presentationMeta` 投影持久卡片数据（可选）。** `output.presentationMeta(args, value)` 从同一规范值派生可重放 JSON。核心将其持久化到 `tool/result` 并交给 `presentResult`，因此需要结果时事实（如 `write`/`edit` 应用的 hunk）的卡片在重放中存续，而不必持久化规范值。嵌套 Code 分派跳过投影器，因为它们没有卡片。
- **用 `exec.agent` 做异步通知。** `agent.inject({ content, source: { kind: 'plugin', plugin: '<name>' } })` 追加持久上下文，下一次模型请求可见——它不是唤醒（空闲 agent 保持空闲）。防护已处置的 agent（try/catch）。

## 长时间运行工作

用生产者配置门控 `run_in_background`，再通过 `ctx.jobs.start({ kind, label, owner: exec.agent, run })` 注册。注册表在生产者 body 前拒绝已预中止的调用；运行时在 `run()` 开始工作前校验所有权与任务控制器可用性，然后提供 id、会话围栏、通用控制工具、通知与所有者清理。成功的后台分支返回类型化规范句柄（如 `{ kind: 'background', jobId }`）；其 Native 渲染器可保留人类散文（如 `started background job bash-1`），但 Code Mode 绝不能解析该散文来恢复 id。

生产者提供同步 `cancel`、资源清理后落定的不拒绝 `done`，以及带输出上限格式化的可选消费 `readOutput`。预中止调用是失败，因为不存在其成功输出 schema 可满足的 id 的任务。一旦 `ctx.jobs.start()` 发布 id，用任务自有取消信号而非 `exec.signal`：之后的外层调用取消只会停止等待该调用，不会杀死已发布的工作；`job_kill`、所有者处置与服务 teardown 拥有该生命周期。前台工作仍与 `exec.signal` 耦合。参见后台任务运行时 Agent Note 与 `dsh-tool-bash`（流生产者）。

## 执行策略与观察

优先不要把部署策略内建到工具里。用 `tools/pre-execute` 做可扩展 allow/deny/ask 策略，`ctx.tools.guard()` 做最终单调拒绝（后续监听者无法撤销），`tools/execute` 用截止时间/重试/指标收集包装分派，`tools/post-execute` 替换呈现内容或返回值、阻止结果或附加模型可见上下文，`tools/result` 观察不可变规范化结果。内容替换保留对 `value` 的程序化访问；保密策略阻止或替换值。沙箱实现也可运行在工具执行器实现内部；`dsh-tools` README 定义每个扩展点的输入、顺序、返回值与失败行为。

## Code Mode 免费触达你的工具

在 Code Mode 中，每个可见已注册工具都可作为 `await tools.<name>(args)` 使用，无需额外集成。生成的 `ToolArgsMap` 与 `ToolOutputMap` 从同一 schema 推导精确参数与规范返回类型，调用重入正常执行流水线。成功调用在策略之后解析为最终规范 JSON 值，而非渲染的 Native 内容。失败调用以真实 `ToolCallError` 拒绝；程序只能检视其 `name`、`toolName` 与人类可读 `message`，不能检视内部错误码或失败联合。

把 `output.schema` 设计成有用的程序化 API：直接返回句柄与字段，诚实时允许标量/数组/null 根，人类解释留在 `output.render`。中间值是执行局部的，不持久化、不提示词截断，无字节上限，因此生产者的真实采集边界与进程内存仍然重要。只有外层 `run_code` 日志/结果穿过可配置输出上限与模型可见 spill 流水线。

## 你的工具在 UI 中如何渲染

工具的 `output.render` 返回模型可见内容；它的 **UI 卡片**是另一码事，通过纯呈现投影与可选 `presentCall` / `presentResult` 方法声明。请与规范值一起设计。没有 UI 呈现的工具回退到通用卡片（标题=工具名，原始 args 为输入）。

两个方法都返回 **`card` 标签的渲染意图**——按工具行为选择卡片种类：

- `presentCall(args)` → 一个 `ToolCallView`（PENDING 卡片）：
  - `{ card: 'generic', title, kind?, rawInput?, content?, locations? }` —— 默认。为图标设 `kind`（`read`/`search`/…）；为工具触及的任何文件设 `locations: [{ path, line? }]`，让有能力的编辑器跟随/跳转。
  - `{ card: 'terminal', title, description?, cwd? }` —— 你的调用就是一条 shell 命令。`title` 是命令，`description` 渲染在终端卡片上方。（tool-bash。）
  - `{ card: 'diff', title, diffs, locations? }` —— 你的调用创建或修改文件。`diffs: [{ path, oldText, newText }]`（新文件 `oldText: null`）渲染为内联 diff 卡片。（tool-fs `write`/`edit`。）
- `presentResult(args, { content, isError, meta? })` → 完成卡片：
  - `generic` 提供可选标题与内容。
  - `terminal` 提供原始输出与可选退出元数据；每个 UI 渲染其有能力或回退的视图。
  - `diff` 提供已应用 hunk，常由 `output.presentationMeta` 推导并携带在持久化 `result.meta` 中，使重放可复现。变更工具保留 diff 结果，因为完成视图替换 PENDING 卡片。
  - `search` 提供从持久化 `result.meta` 重建的发现结果：按文件分组匹配（`shape: 'matches'`，grep）或扁平路径列表（`shape: 'paths'`，glob），外加 `truncated`/`total`，使 UI 绝不会把截断结果当完整呈现。视图不携带结果文本（没有 search 卡片的 UI 回退到原始结果内容），也没有 `search` 调用视图——发现调用的 PENDING 状态保持通用卡片，因为匹配只存在于 `execute` 之后。（tool-fs-search `grep`/`glob`。）
  - `web` 提供按 `kind: 'search' | 'fetch'` 判别的完成网页检索（结构化搜索源或抓取摘要），从 `result.meta` 推导；不携带正文副本，因此没有 `web` 能力的 UI 回退到原始结果内容。（tool-web `web_search`/`web_fetch`。）

硬规则（违反会咬人）：

- **纯度。** 这些在直播流**和**会话日志重放上都运行，因此必须是 `args`（+结果）的纯函数——无 I/O、无会话状态读取、无时钟/随机。diff 从 args 推导（`write` 用 `oldText: null`，因为调用时呈现器没有先前文件内容）；会话上下文由 UI 适配器而非工具提供。如果你发现自己想在 `presentCall` 里要文件的旧内容或工作目录——停下，那属于持久结果元数据或适配器，不属于呈现器。
- **仅 UI 的格式留在模型结果之外。** 围栏 ` ```console ` 块、diff、相对化路径——都不该为了服务 UI 而放进规范值或 Native 内容。`output.render` 拥有模型可见散文；`presentationMeta` 加卡片呈现器拥有可重放 UI 状态。`terminal` 结果视图携带原始输出，适配器添加任何回退框架。
- **`defineTool` 软校验显示路径。** 畸形或旧日志参数使包装器返回 `undefined`（通用回退）而非抛出——显示绝不能使重放崩溃。

中性词汇在 `dsh-tools` 中；工具绝不导入 UI 或传输类型。宿主/客户端运行时把每个 `card` 映射到自己的视图。设计与原因见渲染意图联合 Agent Note；`dsh-tool-fs`（generic/diff）与 `dsh-tool-bash`（terminal）是参考实现。

## 验证

遵循仓库测试政策与所属包测试文档。已发布的模型或 UI 可见变更需要那里指定的组装覆盖。
