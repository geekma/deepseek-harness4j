# Cookbook：添加一个 LLM 适配器

[English](adding-an-llm-adapter.en.md) | 中文

> 本文件是上游 `docs/cookbook/adding-an-llm-adapter.md`（+`.zh.md`）的移植。LLM 适配器是**运行时侧的 TypeScript 插件**，与客户端语言无关——Java/Python SDK 调用方无需改动，只需在 `cordis.yml` 挂载并选择对应 provider/model（见 `deepseek-harness4j-使用指南.md` §十二）。

如何接入新的模型提供方。参考实现：`packages/llm/llm-deepseek`（直接 HTTP，SSE 由 `eventsource-parser` 分帧）与 `packages/llm/llm-pi-ai`（包装一个 LLM 库）。先读 `packages/llm/llm/src/types.ts` 中的 `StreamChunk` 文档——它记录了这两个适配器验证过的协议约定。

## 形态

```ts ignore-check
class MyAdapter extends LlmAdapter {
  async * stream(options: GenerateOptions): AsyncIterable<StreamChunk> { … }
}

export const name = 'llm-myprovider'
export const inject = ['llm']
export const Config: z<Config> = z.object({ apiKey: z.string(), … })

export function apply(ctx: Context, config: Config) {
  ctx.llm.registerAdapter(['my-provider'], new MyAdapter(…))
}
```

注册是基于 effect 的（HMR 安全）；每个 provider 路由一个适配器——重复注册会抛错，多路由注册是"全有或全无"。`options.provider` 选择适配器，`options.model` 是提供方模型 id，因此动态目录适配器无需生命周期重配置即可服务新模型。密钥是 cordis 原生的：schemastery Config 带环境变量回退，经 cordis.yml 的 `!!js process.env.MY_KEY` 注入。绝不要在代码里读临时 key 文件。

## 协议义务（两个实现验证过的契约）

- 在 `finish` **之前**发出 `usage`；`finish` 之后**不再发出任何东西**。稳妥做法：把 finish/usage 缓冲到提供方的流结束标记，再统一 flush（处理只发尾随 usage 分片的提供方）。
- 工具调用 `arguments` 全程是**原始 JSON 字符串**；流式片段用 `argumentsDelta`。如果提供方返回已解析对象，在 `block-end` 重新字符串化。
- 按"首见流顺序"分配 block `index`；同一 block 的每个 delta 复用该 index。
- 错误只有两条合法路径：从 `stream()` **抛出**（传输与协议失败——用带稳定 code 的 `LlmError`），或以 `finish {kind: 'error' | 'aborted'}` **结束流**（提供方带内失败）。消费者两者都处理；按失败类别选择并记录。
- 遵守 `options.signal`（传给 fetch / 你的 SDK）。
- 提供方无法兑现的 `GenerateOptions` 字段（例如没有 stop 序列的提供方收到 `stop` 列表）：抛 `LlmError(..., 'UNSUPPORTED')`，不要静默丢弃。
- 若提供方在后续调用需要响应 id、签名或其他原生元数据，把最小无损 JSON 投影作为 `finish.replayState` 发出。重建历史时校验它。`LlmRuntime` 只在历史 provider 路由与目标 provider 路由当前由**同一个**适配器实例持有时传递它；适配器自行决定同模型、跨模型还是跨提供方恢复合法。状态缺失时绝不要仅凭 provider/model 名称推断原生 replay。

提供方专属的 thinking 模式开关留在适配器的 Config 中。精确模型元数据使用一个 provider 中立的能力 seam：用 provider/model 身份与可选的 `context`、`reasoning` 字段实现 `resolveModel()`，仅在存在时声明已配置的 `defaultEffort`，并遵守解析器的可选 `AbortSignal`。推理档位是有序的不透明 id，由适配器映射到提供方请求。保留适配器权威的可选列表（支持时包含适配器自定义的 `off`），不暴露最终线上拼写，也不夹带不支持的取值；id 不必等于其线上表示。

## 实现结构

把线上类型、请求序列化、传输解析、分片翻译与适配器类拆分为独立职责；`llm-deepseek` 是参考布局。

## 验证

遵循仓库测试政策（上游 `docs/testing.md`），它拥有适配器覆盖、真实提供方检查与已发布条目要求。
