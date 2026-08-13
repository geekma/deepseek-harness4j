# DeepSeek Harness (`dsh`) 完整使用指南（deepseek-harness4j Java 版）

> 本文件是仓库 `deepseek-harness-使用指南.md`（基于 GitHub 仓库 `deepseek-ai/deepseek-harness`(master 分支) 的源码与官方文档阅读整理）的 **Java 移植**：所有 Python SDK 示例改写为 Java（`deepseek-harness4j`），Node/CLI 与 Cordis 组合（`cordis.yml`）部分保持不变——因为它们驱动的是同一套运行时。
> 最后核对时间:2026-08-13。**注意:该项目处于开发者预览(developer preview)阶段,迭代很快,存在破坏兼容性的变更,使用前请以官方文档为准。**

---

## 一、项目背景

### 1.1 它是什么

**DeepSeek Harness**(命令名 `dsh`)是 [DeepSeek AI](https://deepseek.com) 开源的一个 **agent harness(智能体框架)**。

- 核心设计理念:**一切皆插件(Everything is a plugin)**。会话、系统提示词、工具、agent 循环、LLM 接入、bash、文件系统、子进程、Web 能力、子代理、工作流……全部都是可插拔的 Cordis 插件。
- 底层由 __Cordis__(https://github.com/cordiverse/cordis) 驱动,其设计理念来自论文 [_A Programming Paradigm for Spatiotemporal Composability_](https://github.com/cordiverse/paper)。
- 授权:**MIT License**。
- 项目用 Node.js + TypeScript 编写,是 pnpm monorepo;另附带一个 Python SDK(`deepseek-harness-sdk`)。本仓库 `deepseek-harness4j` 提供对应的 **Java SDK**(逐行移植 Python SDK)。

### 1.2 架构特点

| 维度 | 说明 |
|---|---|
| 插件模型 | 每个能力是一条 **capability seam**,由 Service Definition / Service Provider / Consumer 三种角色组成 |
| 核心包 | `@deepseek-ai/dsh-core`(session、system-prompt、tools、agent、agent-loop) |
| LLM 层 | `@deepseek-ai/dsh-llm` + 适配器 `dsh-llm-deepseek`(官方直连)、`dsh-llm-pi-ai`(通用多提供方)、`dsh-llm-retry` |
| 能力层 | shell、subprocess、terminal、fs、lsp、skill、web、compaction、subagent、workflow、todo、plan…… |
| 客户端 | Web UI、CLI、Python SDK、Java SDK(deepseek-harness4j)、ACP(Agent Client Protocol)、JSON-RPC |
| 数据 | 会话持久化到 JSONL 日志,SQlite 存元数据 |

**核心结论:换模型、接自定义服务端,大多属于"配置"而非"改代码"。** 因为 `dsh-llm-pi-ai` 已内置 OpenAI 兼容、Anthropic 等多种协议,自建 OpenAI 兼容网关可直接通过配置文件接入。这条结论对 Java SDK 同样成立——Java 侧只是换了客户端语言,模型配置完全一致。

### 1.3 当前状态

- **开发者预览**:快速迭代中,**会有破坏兼容性的变更**。
- 配置/环境变量主入口:`DEEPSEEK_API_KEY`(必填)、`DEEPSEEK_BASE_URL`(可选,指向 OpenAI 兼容代理时用)、根目录 `.env`。
- 用户数据目录:`$DSH_HOME`(默认 `~/.dsh`),其中 `settings.yaml` 存配置、`.credentials.yaml` 存密钥(只写,界面不回显)。

### 1.4 它类似什么著名的框架/产品?

一句话:**dsh 本质上是 DeepSeek 版的 Claude Code / 可自托管的通用 agent harness**,它和当下主流 agent 产品/框架高度同源,但又刻意做了几个差异化。

| 同类产品/框架 | 与 dsh 的关系 |
|---|---|
| **Claude Code / Claude Agent SDK**(Anthropic) | 最接近的对标。同为"agent harness":会话、系统提示词、工具循环、子代理、钩子(hook)、持久化都内置。dsh 几乎就是 DeepSeek 对 Claude Code 的复刻+改造,区别是**自托管、MIT、provider 无关** |
| **Codex CLI**(OpenAI)、**Gemini CLI**(Google) | 同一类"终端 agent CLI"。dsh 的 headless CLI 与之对应 |
| **OpenHands**(原 OpenDevin) | 同为开源自主软件开发 agent,dsh 与之同赛道 |
| **Aider** | 终端结对编程 agent;dsh 更偏"可组合的框架"而非"单一工具" |
| **LangGraph / LangChain / smolagents / CrewAI** | 都是"agent 构建库"。dsh 更"开箱即用",内置完整 harness 而非只给编排原语 |
| **MCP(Model Context Protocol)** 工具生态 | dsh 的"能力插件"(bash/fs/web/skill/lsp…)与 MCP 工具思路相近,但 dsh 是完整运行时而非仅工具协议 |
| **AutoGPT / BabyAGI / OpenClaw 等自主 agent** | 同属"agent",但 dsh 强调可控(审批/沙箱/会话可复现),而非纯自主狂跑 |

**一句话差异点:** 相比 Claude Code 这类"绑定自家模型"的产品,dsh 的最大卖点是 **LLM 接入是一个可插拔 seam**——官方适配 DeepSeek,但通过 `llm-pi-ai` 也能接 OpenAI、Anthropic、任意 OpenAI 兼容网关,甚至自己写适配器接私有模型。这决定了它更像"**你可以自己掌控的 agent 运行时**"。

### 1.5 它能解决什么问题?

1. **摆脱模型锁定(provider lock-in)** —— LLM 层是抽象 seam,换 DeepSeek / OpenAI / Anthropic / 自建网关只改配置不改业务代码。对用私有模型、公司网关、自建 vLLM/Ollama 的场景尤其友好。
2. **不用从零造 agent** —— 会话管理、系统提示词、工具循环、子代理、工作流、计划、审批/权限、沙箱、持久化全部内置,拿来即用。
3. **极强的可扩展性** —— "一切皆插件":要 bash/文件/网页/技能/LSP 能力就组合对应插件,支持热更新(HMR),多数行为改 `cordis.yml` 即可,不必改 `agent-loop`。
4. **可控与可审计** —— 每步的模型请求/工具调用都落到会话 JSONL 日志,可回放、可复现;审批/权限策略 + 沙箱(landlock)让 agent 不至于乱来。
5. **一套核心,多端驱动** —— 同一个 agent 内核可被 Web UI、CLI、Python SDK、Java SDK、ACP、JSON-RPC 调用,方便嵌入自己的程序/工作流/CI。
6. **多代理协作** —— 子代理委派、product/code 多 agent 并行等能力开箱即用。
7. **私有化部署、MIT 开源** —— 不依赖某个封闭云平台,可以完全部署在自己环境里。

---

## 二、如何安装与使用

### 2.1 方式 A:通过 npm 直接跑 Web UI(最快)

前提:已安装 Node.js(仓库要求 `node ^22.19 || >=24`)。

```sh
npx @deepseek-ai/dsh web
```

- 默认服务地址:`http://127.0.0.1:3080`
- 首次进入:Settings → Models 填入 DeepSeek API key → 选择工作区 → 新建会话发任务。

### 2.2 方式 B:从源码运行

```sh
git clone https://github.com/deepseek-ai/deepseek-harness.git
cd deepseek-harness
pnpm install
pnpm run build
pnpm dsh web          # 启动 Web UI
```

### 2.3 方式 C:无头(Headless)CLI 跑单任务

```sh
export DEEPSEEK_API_KEY=sk-xxx
# 若要走 OpenAI 兼容代理:
# export DEEPSEEK_BASE_URL=http://127.0.0.1:8000/v1
pnpm dsh --profile headless "Summarize this repository and list its main packages."
```

### 2.4 方式 D:Java SDK(编程调用,deepseek-harness4j)

> 对应上游 Python SDK 方式 D;Java 侧使用 Maven 引入 SDK,并需要一个运行时载体(见 `deepseek-harness4j/sdk-runtime` README)。

```sh
git clone https://github.com/deepseek-ai/deepseek-harness.git
cd deepseek-harness
# 1) 构建运行时载体(或从运行时分发安装)
pnpm install
pnpm exec tsx scripts/build-exe-for-python-sdk.ts
# 2) 把生成的 dsh-jsonrpc-agent-pkg-<平台>-<arch> 放到 deepseek-harness4j 的运行时目录(见 development.md)
```

```sh
export DEEPSEEK_API_KEY=sk-xxx
# export DEEPSEEK_BASE_URL=http://127.0.0.1:8000/v1   # OpenAI 兼容代理时
# export DSH_MODEL=deepseek-v4-flash
```

```java
// deepseek-harness4j/sdk
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

---

## 三、如何配置自定义模型(重点)

自定义模型分三种场景,难度递增:

1. **OpenAI 兼容端点 / 公司网关 / 自建服务** —— 纯配置,最常用。
2. **用已支持协议的提供方(Anthropic/OpenAI 等)** —— 填 key 即可。
3. **完全不兼容任何内置协议的私有模型** —— 需要写一个 LLM 适配器插件(代码)。

下面分别说明。

### 3.1 场景一:接入 OpenAI 兼容的自定义端点(推荐,零代码)

#### 3.1.1 通过 Web UI(图形化)

路径:**Settings → Models → Add a custom provider**

需要填写:

| 字段 | 说明 |
|---|---|
| Provider ID | **小写**唯一标识,一旦保存不可改(请求、会话日志、默认值、凭据都引用它)。要改名就新建一个再删旧的 |
| Display name | 显示名(可改) |
| Base URL | 例如 `https://gateway.example.com/v1` |
| API protocol | 例如 `openai-completions` |
| API key | 凭据(只写) |
| Models | 至少一个模型 id,可用 "Fetch available models" 从 `GET /models` 拉取,或手填 |

> 提示:手动填写的模型默认按**纯文本**处理。视觉模型需在 `settings.yaml` 里给该模型加 `input: [text, image]`,否则附加图片会在发送前被拒绝。

#### 3.1.2 直接改 `$DSH_HOME/settings.yaml`(可脚本化/可审计)

`settings.yaml` 里 `llm-pi-ai:` 下的 `providers` 是"按提供方路由为键"的字典,可以完全自定义:

```yaml
# $DSH_HOME/settings.yaml  (默认 ~/.dsh/settings.yaml)
llm-pi-ai:
  providers:
    # —— 方式 1:完全手写的自定义路由(自建/私有网关)——
    acme-gateway:
      displayName: Acme Gateway
      apiKeyEnv: ACME_GATEWAY_API_KEY     # 凭据引用,不写明文密钥
      api: openai-completions             # 协议:openai-completions / openai-responses / anthropic ...
      baseURL: https://gateway.acme.example/v1
      # 私有网关 URL 无法自动识别推理方言时,显式指定:
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
          # 可选:声明该模型的推理档位(off 可留空表示"不发送")
          reasoningEfforts:
            off:
            high: high
            max: ultra

    # —— 方式 2:用目录提供方但覆盖/收窄模型(不用写全部字段)——
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

    # —— 方式 3:改一个目录模型、其余保留(catalog 的 modelOverrides)——
    deepseek:
      apiKeyEnv: DEEPSEEK_API_KEY
      modelOverrides:
        deepseek-v4-pro:
          reasoningEfforts:
            off:
            high: high

    # —— 视觉模型:声明图片模态 ——
    vision-gateway:
      apiKeyEnv: GATEWAY_API_KEY
      api: openai-completions
      baseURL: https://vision.example/v1
      defaultInput: [text, image]      # 该路由所有手填模型的兜底模态
      models:
        - id: first-model
        - id: vision-preview
          input: [text, image]         # 仅此模型支持图片
```

**自定义 provider 可用的 profile 字段(常用子集):**

`apiKeyEnv`、`displayName`、`api`、`baseURL`、`models`、`modelOverrides`、`compat`、`defaultContextWindow`、`defaultMaxTokens`、`defaultInput`、`headers`、`reasoning`、`thinkingBudgets`、`transport`、`timeoutMs`、`streamIdleTimeoutMs`、`retryPolicy`。

**models 条目可用字段:**`id`、`name`、`contextWindow`、`maxTokens`、`reasoningEfforts`、`compat`、`input`。

关键语义:

- `apiKeyEnv` 是**按请求解析的凭据引用**(解析自 `.credentials.yaml` / 环境变量),配置文件里不落明文密钥。
- `models` 列表会**整体替换**该路由的 catalog;只写一个 `id` 也够(其余继承 catalog 或路由兜底值)。
- `modelOverrides` 是"就地改一个 catalog 模型",不替换整个列表。
- 请求协议支持 `openai-completions` / `openai-responses` / `anthropic` 等(通过 `supportedProtocols()` 暴露);Bedrock / Vertex / Azure / Codex 需要各自原生凭据,不适合用 API key 字段。

#### 3.1.3 无头/CLI 场景:直接用环境变量指向自定义端点

不写 settings,直接通过环境变量让官方 DeepSeek 适配器走你的 OpenAI 兼容代理:

```sh
export DEEPSEEK_API_KEY=sk-your-key
export DEEPSEEK_BASE_URL=https://your-gateway.example.com/v1   # 指向你的端点
export DSH_MODEL=your-custom-model-id
pnpm dsh --profile headless "你的任务描述"
```

在 Java SDK 里同样用 `DEEPSEEK_BASE_URL` + `DEEPSEEK_API_KEY`(运行时继承这两个变量)。

### 3.2 场景二:使用目录提供方(Anthropic / OpenAI 等)

Web UI:**Settings → Models → Add provider**,选择提供方、填 API key、保存即可。已安装目录会提供端点、协议、模型列表。带原生认证的提供方(Bedrock/AWS、Vertex/ADC、Azure/api-version、Codex/OAuth)需填各自原生凭据,仅填 API key 无法完成配置。

### 3.3 场景三:完全不兼容的私有模型 → 写一个 LLM 适配器插件

如果你的模型走的是非 OpenAI 兼容的私有协议,就需要实现一个 `LlmAdapter`(参考仓库内 `packages/llm/llm-deepseek` 与 `packages/llm/llm-pi-ai` 两个完整实现)。

> 说明:适配器是 **TypeScript 插件**,运行在 dsh 运行时一侧;客户端语言(Python / Java / CLI)无需改动。因此本节代码与上游指南完全一致,Java SDK 调用方只需在 `cordis.yml` 里挂载该插件并选择对应 provider/model。

最小骨架:

```ts
import { Context } from '@deepseek-ai/cordis'
import Schema from '@deepseek-ai/schemastery'
import { LlmAdapter, type GenerateOptions, type StreamChunk } from '@deepseek-ai/dsh-llm'

class MyAdapter extends LlmAdapter {
  constructor(private readonly apiKey: string) { super() }

  // 把提供方无关的请求转成你的 API 调用,再把响应转成 StreamChunk 分片
  async *stream(options: GenerateOptions): AsyncIterable<StreamChunk> {
    // 1) options.messages -> 你的协议请求
    // 2) 调用流式 API
    // 3) 把响应转成 StreamChunk 序列:
    //    block-start -> text-delta* -> block-end
    //    (工具调用: block-start -> tool-call-delta* -> block-end)
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

在 `cordis.yml` 里挂载并让 agent 使用它:

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

**StreamChunk 协议要点**(`stream()` 必须遵守):

- 每个 `block-start` 必须有对应的 `block-end`;`index` 从 0 递增。
- 工具调用参数全程是原始 JSON 字符串,流式片段用 `argumentsDelta`。
- `usage` 必须在 `finish` 之前;`finish` 必须是最后一个分片。
- 错误两条合法路径:从 `stream()` 抛出带稳定 code 的 `LlmError`,或以 `finish {kind:'error'|'aborted'}` 结束。
- 遵守 `options.signal`;无法支持的字段抛 `LlmError(..., 'UNSUPPORTED')`,不要静默丢弃。

---

## 四、配置模型的常见报错与排错

| 报错 | 含义与处理 |
|---|---|
| `MISSING_CREDENTIAL` | 没有可用的密钥。去 Models 页存 key,或提供被引用的环境变量 |
| `UNKNOWN_MODEL` | 模型不在已配置提供方的 catalog 里。选一个已配置模型,或给自定义提供方补上该模型 |
| 拉取模型返回 401 | key 不对。模型发现走 OpenAI 兼容 `GET /models`,端点不支持就手动填模型 |
| 图片发送前被拒绝 | 该模型没声明图片模态。给自定义模型加 `input: [text, image]`(DeepSeek 官方 chat-completions 路由是纯文本,无法改) |
| 提供方拒绝带图请求 | 模型声明了端点实际不支持的图片能力,从对应 `input` / `defaultInput` 里移除 `image` 后开新会话 |
| `DUPLICATE_ADAPTER` | 同一个 provider 路由被注册了两次(如同时装了 deepseek 与 pi-ai 的 `deepseek` 路由) |

---

## 五、仓库常用命令(源码开发者)

```sh
pnpm install            # 安装依赖
pnpm run build          # 构建
pnpm run test           # 单测
pnpm run test:e2e       # 真实 API 测试(无 DEEPSEEK_API_KEY 会自动跳过)
pnpm run test:coverage  # CI 覆盖率门禁
pnpm run typecheck / lint
pnpm run demo:cordis    # 演示 agent 修改自身运行时(需要 key)
pnpm run demo:acp       # ACP 自动化服务器(需要 DEEPSEEK_API_KEY)
pnpm dsh --profile headless "task"
```

> Java 侧(deepseek-harness4j)对应命令见 `development.md`:`mvn install` / `mvn test`。

---

## 六、快速上手 Demo(推荐路径)

```sh
# 1) 装好 Node.js(>=22)
# 2) 起 Web UI
npx @deepseek-ai/dsh web

# 3) 浏览器打开 http://127.0.0.1:3080
#    Settings → Models:
#      - 填 DeepSeek key,或
#      - Add a custom provider 填你的网关(baseURL + protocol + key + model)
#    Choose workspace: 添加并选中项目目录
# 4) 新建会话,发送任务,例如:
#    "Summarize this repository and identify its main packages."
```

如果你要接的是**自定义/自建 OpenAI 兼容服务**,最省事的一条路就是:

```sh
export DEEPSEEK_API_KEY=sk-xxx
export DEEPSEEK_BASE_URL=http://127.0.0.1:8000/v1   # 换成你的端点
npx @deepseek-ai/dsh web
```

然后在 Models 里选中模型即可。改模型**无需重启服务器**,下一次请求即生效。

---

## 附:官方文档入口

- README: https://github.com/deepseek-ai/deepseek-harness
- 架构: `docs/architecture.md` / 开发指南: `docs/development.md`
- Web UI 使用: `docs/user/guide/index.md`
- 配置模型: `docs/user/guide/providers.md`
- 插件配置全量字段: `docs/config-catalog.md`
- 写 LLM 适配器: `docs/cookbook/adding-an-llm-adapter.md`、`docs/user/develop/practice/llm-adapter.md`
- 适配器实现参考:`packages/llm/llm-deepseek/`、`packages/llm/llm-pi-ai/`
- Java SDK(本仓库): `deepseek-harness4j/README.md`、`deepseek-harness4j/development.md`、`deepseek-harness4j/docs/java-migration-notes.md`

---

# 第二部分:完整实战 Demo

> 以下 Demo 全部基于仓库真实文件核对(`examples/jsonrpc-agent/minimal.cordis.yml`、`minimal.py`、`docs/user/guide/*`、`packages/llm/*`)。示例代码做了注释以便理解。**运行任何 Demo 前,请先准备:**
>
> - 一台装了 Node.js(>=22) 或 Python(>=3.10) 的机器;**Java Demo 另需 JDK 17+ 与 Maven,以及安装好的运行时载体(见 `development.md`)**;
> - 一个可用的模型端点 + 密钥(DeepSeek 官方,或任意 OpenAI 兼容的自建网关);
> - 一个"允许 agent 修改"的独立工作目录(推荐用空目录,别拿真项目试)。

---

## 七、Demo 1:Web UI 全流程(第一次跑通)

**目标**:用鼠标把 harness 跑起来,发第一个任务。

### 7.1 启动

```sh
# 全局最快方式
npx @deepseek-ai/dsh web
```

启动后终端会打印地址,默认 `http://127.0.0.1:3080`,浏览器打开它。

### 7.2 首次配置四步走

| 步骤 | 操作 | 说明 |
|---|---|---|
| ① 配模型 | **Settings → Models** → 在 DeepSeek 卡片填 API key 保存;或点 **Add a custom provider** 填你的网关 | 密钥是**只写**的,存进 `~/.dsh/.credentials.yaml`,界面只显示脱敏描述符 |
| ② 选工作区 | 点 **Choose workspace** → 添加你启动 `dsh` 时所在的目录并选中 | 不选工作区,会话输入框是灰的 |
| ③ 建会话 | 新建一个 session | |
| ④ 发任务 | 输入任务并发送 | 涉及批准的操作会按权限策略弹窗询问 |

### 7.3 示例任务(可直接粘贴)

```sh
Summarize this repository and identify its main packages.
```

```sh
Read the file src/main.ts, then write a test for it and run it.
```

> 说明:Web UI 里的 agent 能读/改工作区文件、跑命令、委派子代理、维护 plan。改模型**不需要重启服务器**,下一次请求即生效。

### 7.4 数据落在哪

- 会话日志:工作区的 JSONL 文件(记录每次组装好的模型请求、工具调用、响应)。
- 配置:`~/.dsh/settings.yaml`。
- 密钥:`~/.dsh/.credentials.yaml`(只写)。

---

## 八、Demo 2:无头 CLI(Headless)跑单任务

**目标**:不打开浏览器,一条命令跑完一个 agent 任务。适合脚本化、CI。

```sh
# 1) 设凭据(必填)
export DEEPSEEK_API_KEY=sk-你的密钥

# 2) 如果用自建 OpenAI 兼容端点,再指一下(可选)
export DEEPSEEK_BASE_URL=http://127.0.0.1:8000/v1
export DSH_MODEL=你的模型id          # 默认 deepseek-v4-flash

# 3) 从源码仓库跑一个任务
cd deepseek-harness
pnpm dsh --profile headless "List all files in this repo and count lines of TypeScript."
```

> 说明:`--profile headless` 加载无头组合;输出直接打到 stdout。配合 `DEEPSEEK_BASE_URL` + `DSH_MODEL` 就能把官方 CLI 指向你自己的模型,无需改代码。

---

## 九、Demo 3:Java SDK 完整示例(含自定义网关)

> 对应上游 Demo 3(Python SDK);Java 侧逐行移植了 `deepseek_harness` 包。

**目标**:用 Java 编程驱动 harness。这是"接入自己程序/工作流"最常用的一路。

### 9.1 最小三步

```sh
# 1) 引入依赖(见 deepseek-harness4j/sdk README):Maven 坐标 com.deepseek-ai:deepseek-harness4j-sdk
# 2) 设凭据
export DEEPSEEK_API_KEY=sk-你的密钥
# 3) 用
```

```java
import com.deepseek.harness4j.DeepSeekHarness;
import com.deepseek.harness4j.RunResult;

try (DeepSeekHarness harness = new DeepSeekHarness()) {
    RunResult result = harness.run("Say hi.");
    System.out.println(result.finalResponse());
}
```

`new DeepSeekHarness()` 默认拉起随运行时载体附带的 `dsh-jsonrpc-agent` 可执行文件,并注入默认组合(JSON-RPC 服务器 + agent core + DeepSeek 适配器 + JSONL 会话持久化 + 本地 bash)。

### 9.2 完整参数化示例(重点:自定义模型)

```java
import com.deepseek.harness4j.DeepSeekHarness;
import com.deepseek.harness4j.DeepSeekHarnessConfig;
import com.deepseek.harness4j.RunResult;

String config    = Path.of("examples/jsonrpc-agent/minimal.cordis.yml").toAbsolutePath().normalize().toString();
String workspace = Path.of("/绝对/路径/到/你的/工作区").toAbsolutePath().normalize().toString();
String sessions  = Path.of("/绝对/路径/到/会话目录").toAbsolutePath().normalize().toString();

try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
        .provider("deepseek-official")      // 提供方路由(由 cordis.yml 里的适配器注册)
        .model("deepseek-v4-flash")         // 模型 id
        .maxTokens(49_152)                  // 每次请求的输出 token 上限(可选)
        .cwd(workspace)                     // agent 的工作目录(会真正改里面的文件)
        .sessionRoot(sessions)              // 会话 JSONL 落盘位置
        .cordis(config)                     // 你自己的插件组合文件(见 Demo 4)
        .build())) {
    RunResult result = harness.run(
            "Inspect the repository and fix the failing tests.",
            "example-001",                  // 复用会话 id 可续聊
            null);
    System.out.println(result.finalResponse());   // 根会话最后的助手文本
    System.out.println(result.finishReason());    // completed / max-tokens / error ...
    System.out.println(result.sessionId());
    // result.events() / result.notifications() 里是会话与子代理事件
}
```

### 9.3 关键参数速查

| 参数 | 作用 |
|---|---|
| `provider` | 选择由哪个适配器注册的__提供方路由__(如 `deepseek-official`;自定义组合挂 `llm-pi-ai` 后可写任意 catalog 提供方) |
| `model` | 该提供方解析的模型 id |
| `maxTokens` | 根 agent 及其进程内后代的输出 token 上限;省略则用提供方默认值 |
| `cordis` | 自定义插件组合文件路径(不传则用随包默认组合) |
| `cwd` | agent 工作目录(自动转绝对路径,与 Python `Path.resolve()` 一致) |
| `sessionRoot` | 会话落盘目录(等价于设 `DSH_SESSION_ROOT`) |

### 9.4 指向自定义/自建模型

运行时继承 `DEEPSEEK_BASE_URL` 与 `DEEPSEEK_API_KEY`,所以三种接法:

```sh
# 接法 A:官方 DeepSeek
export DEEPSEEK_API_KEY=sk-xxx

# 接法 B:OpenAI 兼容自建网关(vLLM / Ollama / LM Studio / 公司代理……)
export DEEPSEEK_API_KEY=sk-任意非空值
export DEEPSEEK_BASE_URL=http://127.0.0.1:8000/v1
export DSH_MODEL=qwen2.5-72b-instruct

# 接法 C:更精细的多提供方 -> 在 cordis.yml 挂 llm-pi-ai 并在 settings.yaml 配置(见 Demo 5)
```

```java
import com.deepseek.harness4j.DeepSeekHarness;
import com.deepseek.harness4j.DeepSeekHarnessConfig;

try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
        .provider("acme-gateway")      // settings.yaml 里自定义的路由
        .model("acme-large")
        .cordis("examples/jsonrpc-agent/minimal.cordis.yml")
        .build())) {
    System.out.println(harness.run("写一段 Java 读取 JSON 的示例", null, null).finalResponse());
}
```

---

## 十、Demo 4:用 cordis.yml 组合"你自己的 agent"

**目标**:理解并改写插件组合。`cordis.yml` 是 dsh 的"配方文件"——列出要加载哪些插件及其配置。下面这份是仓库**真实**的 `examples/jsonrpc-agent/minimal.cordis.yml`(我加了中文注释),它组合出一个无头最小 agent:模型只能看到**一个系统提示词 + 两个工具**(持久 bash + 字符串替换编辑器)。

```yaml
# ---- 完整无头最小 agent 组合(来自仓库 examples/jsonrpc-agent/minimal.cordis.yml)----

# ① JSON-RPC 服务器(给 SDK/程序调用用)
- id: sdk-jsonrpc-server
  name: '@deepseek-ai/dsh-sdk-jsonrpc-server'
  config:
    maxTokensAsSuccess: false

# ② LLM 适配器:DeepSeek 官方直连
- id: llm-deepseek
  name: '@deepseek-ai/dsh-llm-deepseek'
  config:
    apiKeyEnv: DEEPSEEK_API_KEY                 # 凭据引用,不落明文
    streamIdleTimeoutMs: 172800000
    models:                                     # 公布给选择器的模型(可用环境变量动态定)
      - id: !!js process.env.DSH_MODEL ?? 'deepseek-v4-flash'
        contextWindow: !!js Number(process.env.DSH_CONTEXT_WINDOW ?? 1000000)

# ③ 沙箱与安全策略
- id: sandbox
  name: '@deepseek-ai/dsh-sandbox-local'
- id: sandbox-policy
  name: '@deepseek-ai/dsh-sandbox-policy'
  config:
    mode: danger-full-access                    # 完全访问(最小示例图省事)
    workspaceRoot: !!js process.env.DSH_CWD ?? process.cwd()

# ④ 子进程 + 持久终端(bash 工具的后端)
- id: subprocess
  name: '@deepseek-ai/dsh-subprocess-local'
- id: pty
  name: '@deepseek-ai/dsh-terminal'
- id: terminal-bash
  name: '@deepseek-ai/dsh-terminal-bash'
  config:
    timeoutMs: 300000

# ⑤ 文件系统(编辑器用;注意也受上面的沙箱策略约束)
- id: fs-local
  name: '@deepseek-ai/dsh-fs-local'
  config:
    cwd: !!js process.env.DSH_CWD ?? process.cwd()

# ⑥ agent 主干(会话/系统提示词/工具编排都在这里)
- id: agent-spine
  name: '@deepseek-ai/dsh-agent-spine-demo'
  config:
    includeHarnessIdentity: false
    includeRuntimeContext: false               # 关闭动态上下文注入
    persona: !!js process.env.DSH_SYSTEM_PROMPT ?? 'You are a helpful software engineer assistant.'
    workspaceContext: false
    skills:
      enabled: false                            # 不启用技能
    toolBash: false                             # 由下面的专用 bash 工具接管
    toolJobs: false

# ⑦ 模型可见的两个工具:持久 bash + 字符串替换编辑器
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

# ⑧ 会话持久化:JSONL
- id: sessions
  name: '@deepseek-ai/dsh-session-persistence-jsonl'
  config:
    root: !!js process.env.DSH_SESSION_ROOT ?? './.sessions'
    compression: none
```

### 10.1 把它跑起来

```sh
cd deepseek-harness
export DEEPSEEK_API_KEY=sk-xxx
python examples/jsonrpc-agent/minimal.py \
  --workspace /绝对/路径/工作区 \
  --session-root /绝对/路径/会话 \
  --session-id example-001 \
  "Read package.json and print the list of scripts."
```

Java 侧等价写法(Java SDK 调用同一份组合):

```java
try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
        .cwd("/绝对/路径/工作区")
        .sessionRoot("/绝对/路径/会话")
        .cordis("examples/jsonrpc-agent/minimal.cordis.yml")
        .build())) {
    harness.run("Read package.json and print the list of scripts.", "example-001", null);
}
```

### 10.2 如何改成"你自己的 agent"

- **换模型**:改 `llm-deepseek` 的 `models`,或换成 `llm-pi-ai`(见 Demo 5)。
- **加/减工具**:加一行 `toolBash` / `toolSkill` / `toolJobs` 之类的插件;把 `agent-spine` 里对应 `toolXxx: false` 改成 true 或去掉。
- **改系统提示词**:`persona` 字段。
- **启用技能**:`skills.enabled: true`(并配 `skill-filesystem` 提供方)。
- **加子代理/工作流/计划**:在组合里加 `dsh-subagent`、`dsh-workflow`、`dsh-plan` 插件。
- **改沙箱安全级别**:`sandbox-policy.mode` 可在 `danger-full-access` / 受限模式之间切换(生产环境强烈建议收紧)。

> 原则:改组合(cordis.yml)**优先于**改代码。几乎所有行为都能通过"加载哪个插件 + 给什么配置"来调整,不需要改 `agent-loop`。Java SDK 调用方同样遵循此原则。

---

## 十一、Demo 5:给自定义模型/网关写 `settings.yaml` 并跑任务

**目标**:完整演示"把 dsh 指向一个自建 OpenAI 兼容网关"并真正跑任务。这是日常最常用的自定义模型姿势。

### 11.1 写配置

在 `~/.dsh/settings.yaml`(或由 `DSH_HOME` 指定的目录)里:

```yaml
# ~/.dsh/settings.yaml —— 运行时可热更新,下一次请求生效,无需重启
llm-pi-ai:
  providers:
    # 完全手写的自定义路由:我的本地网关
    local-gateway:
      displayName: Local Gateway
      apiKeyEnv: LOCAL_KEY                 # 凭据引用(从环境或 .credentials.yaml 解析)
      api: openai-completions              # 协议
      baseURL: http://127.0.0.1:8000/v1    # 自建服务地址(vLLM/Ollama/LM Studio 等)
      defaultContextWindow: 32768          # 手填模型的兜底上下文
      defaultMaxTokens: 8192               # 手填模型的兜底输出上限
      models:
        - id: qwen2.5-72b-instruct
          name: Qwen2.5 72B
        - id: my-vision
          name: My Vision
          input: [text, image]             # 视觉模型声明图片模态
```

对应环境变量(密钥不进 yaml):

```sh
export LOCAL_KEY=sk-随便填(本地网关通常不校验)或你的key
```

### 11.2 在组合里挂 `llm-pi-ai` 并选中它

在你自己的 `cordis.yml` 里,把 `llm-deepseek` 那一块替换/追加为:

```yaml
# 通用多提供方适配器(能读 settings.yaml 里的 providers)
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

> 说明:`llm-pi-ai` 的 `providers` 是"以路由为键"的字典,并且**支持与 `settings.yaml` 按提供方合并**——组合里放 base,settings 里改覆盖,都能在下次请求生效。

### 11.3 跑任务(Java SDK)

```java
import com.deepseek.harness4j.DeepSeekHarness;
import com.deepseek.harness4j.DeepSeekHarnessConfig;
import com.deepseek.harness4j.RunResult;

try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
        .provider("local-gateway")          // settings.yaml / cordis.yml 里自定义的路由
        .model("qwen2.5-72b-instruct")
        .cordis("examples/jsonrpc-agent/minimal.cordis.yml")
        .build())) {
    RunResult r = harness.run("写一个计算斐波那契数列的 Java 函数并测试它。", null, null);
    System.out.println(r.finalResponse());
}
```

### 11.4 用 `GET /models` 自动发现模型

Web UI 的 "Fetch available models" 会调用 OpenAI 兼容的 `GET /models` 拉候选(不存储,只供采纳);端点不提供该接口就手填。自定义路由只对 `openai-completions` / `openai-responses` 做发现(Azure 用 `api-key` 头 + `api-version`,不适用)。

---

## 十二、Demo 6:写一个自定义 LLM 适配器插件(完整代码)

**目标**:当你的模型**完全不兼容**任何内置协议时,自己写一个适配器接入。这是"自定义模型"的最底层手段。完整可交付实现参考仓库 `packages/llm/llm-deepseek/`(官方直连)与 `packages/llm/llm-pi-ai/`(通用)。**适配器是运行时侧的 TypeScript 插件,Java SDK 调用方无需改动;只改 `cordis.yml` 挂载即可。**

### 12.1 插件骨架(完整可编译)

文件 `src/my-llm-adapter.ts`:

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
 * 一个极简自定义适配器:把 harness 的请求转发到一个 OpenAI 兼容端点,
 * 并把响应转成 StreamChunk 分片。(生产请参考 llm-deepseek 的完整实现。)
 */
class MyAdapter extends LlmAdapter {
  constructor(
    private readonly apiKey: string,
    private readonly baseURL: string,
  ) { super() }

  async *stream(options: GenerateOptions): AsyncIterable<StreamChunk> {
    // 1) 把 harness 的对话历史转成你的 API 请求体
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

    // 2) 调用流式 API(遵守 options.signal,合并归因头)
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

    // 3) 按 StreamChunk 协议逐块产出:
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
      // 简易 SSE 解析:按空行切分事件(生产用 eventsource-parser)
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

  // 可选:告诉选择器这个提供方能服务哪些模型
  async listModels(): Promise<Array<{ id: string; name?: string }>> {
    return [{ id: 'my-custom-model', name: 'My Custom Model' }]
  }
}

// —— Cordis 插件约定:name / inject / Config / apply ——
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

### 12.2 在 `cordis.yml` 挂载并让 agent 使用

```yaml
# 挂载自定义适配器(provider 路由: my-provider)
- id: my-llm
  name: './src/my-llm-adapter.ts'          # 源码路径(或已发布包名)
  config:
    apiKey: !!js process.env.MY_API_KEY    # 密钥走环境变量,不进文件
    baseURL: http://127.0.0.1:9999/v1
    providers:
      - my-provider

# 让 agent 主干用这个 provider/model
- id: agent-loop
  name: '@deepseek-ai/dsh-agent-loop'
  config:
    agents:
      - id: main
        provider: my-provider
        model: my-custom-model
```

### 12.3 StreamChunk 协议要点(必须遵守)

| 规则 | 说明 |
|---|---|
| 成对 | 每个 `block-start` 必须有对应 `block-end`;`index` 从 0 递增 |
| 工具调用 | `tool-call-delta` 的 `argumentsDelta` 是原始 JSON 文本增量,结束时在 `block-end` 里给完整 `arguments` 字符串 |
| 顺序 | `usage` 必须在 `finish` 之前;`finish` 必须是最后一个分片 |
| 错误 | 两条合法路径:① `stream()` 直接抛带稳定 code 的 `LlmError`;② 以 `finish {kind:'error'|'aborted'}` 结束流 |
| 中止 | 遵守 `options.signal`(传给 fetch / SDK) |
| 不支持 | 字段无法支持时抛 `LlmError(..., 'UNSUPPORTED')`,不要静默丢弃 |
| 归因 | 每个提供方 HTTP 请求合并 `attributionHeaders()` |

### 12.4 常见适配器错误 code

`AUTH`(401/403)、`QUOTA`(余额/配额)、`RATE_LIMIT`(429)、`CONTEXT_WINDOW_EXCEEDED`、`INVALID_REQUEST`(其他 400)、`SERVER`(5xx)、`TRANSPORT`(DNS/连接/TLS/代理)、`ABORTED`(调用方取消)、`TIMEOUT`、`MISSING_CREDENTIAL`、`INVALID_CREDENTIAL`、`STREAM_CLOSED`、`MALFORMED_RESPONSE`、`EMPTY_RESPONSE`、`UNKNOWN_MODEL`、`UNSUPPORTED_REASONING_EFFORT`、`DUPLICATE_ADAPTER`。

---

## 十三、Demo 7:把 Demo 串起来——一个"本地自定义模型跑代码任务"的完整例子

**目标**:一个从零到一、能照抄的最小完整链路(自建网关 → 自定义 provider → 跑一个真实代码任务)。

```sh
# ① 前置:你已有一个 OpenAI 兼容的本地服务在 8000 端口(vLLM/Ollama/LM Studio 均可)
# 例如 Ollama:
#   ollama serve &
#   ollama pull qwen2.5:7b
#   # Ollama 兼容端点: http://127.0.0.1:11434/v1 (注意不是 8000,按实际改)

# ② 建一个干净工作区
mkdir -p ~/dsh-demo/workspace && cd ~/dsh-demo/workspace
echo '{"name":"demo-app","scripts":{"test":"node test.js"},"dependencies":{}}' > package.json

# ③ 写自定义 provider 的 settings(假设用 llm-pi-ai)
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
export LOCAL_KEY=ollama   # 本地网关通常不校验,给个非空值即可

# ④ 跑一个真实任务:让 agent 写并运行一个测试
```

```java
// Java SDK(deepseek-harness4j):等价于上游 Python 的 DeepSeekHarness 调用
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
    System.out.println(">>> 最终回复:\n" + r.finalResponse());
    System.out.println(">>> 结束原因:" + r.finishReason());
}
```

```sh
# ⑤ 查看 agent 在会话里都干了什么
ls -la ~/dsh-demo/sessions   # JSONL 会话日志
```

> 预期行为:agent 会用 bash 工具创建 `test.js`、跑 `npm test`、根据结果修到通过,最后给出总结。若 `finishReason` 为 `completed`,说明整条链路(自定义 provider → 模型 → 工具 → 会话)已跑通。

---

## 十四、Demo 速查:什么时候用哪条路

| 你的目标 | 推荐路线 | 章节 |
|---|---|---|
| 快速体验/交互 | `npx @deepseek-ai/dsh web` + Web UI | 七 |
| 脚本化/CI 跑单任务 | `pnpm dsh --profile headless "任务"` | 八 |
| 接入自己程序/工作流 | Python SDK / Java SDK `DeepSeekHarness.run()` | 九 |
| 自定义 agent 能力组合 | 手写/改写 `cordis.yml` | 十 |
| 接自建 OpenAI 兼容网关 | `settings.yaml` + `llm-pi-ai` + `provider=` | 十一 |
| 接完全不兼容的私有模型 | 写 `LlmAdapter` 插件 | 十二 |
| 本地全链路验证 | 照着 Demo 7 跑一遍 | 十三 |

---

## 十五、实测验证记录(2026-08-13,真实跑通)

> 本节是__在本机真实运行__得到的记录,不是示例说明。环境:macOS,Python SDK `deepseek-harness-sdk==0.1.0rc6`,通过 `uv` 装入独立 venv。
> 使用的模型配置:__复用本机 Hermes 实例的模型端点__ —— 火山方舟(Volcano Ark)OpenAI 兼容端点 `https://ark.cn-beijing.volces.com/api/coding/v3`,模型 `deepseek-v4-flash-ga-260731`。即:用 `DEEPSEEK_API_KEY` + `DEEPSEEK_BASE_URL` 指向自定义端点,完全没改任何 dsh 代码——这正好验证了"自定义模型=配置而非改代码"。
>
> **Java 移植注**:以下记录为上游 Python SDK 的真实输出。`deepseek-harness4j` 的 Java SDK 与该 Python SDK **逐行对应**(`RunResult.finalResponse()` / `finishReason()` / `sessionId()` 与 Python 结果字段一一对应),在相同的运行时与模型配置下应得到等价结果;可用本仓库 `sdk` 的 `FakeRuntime` 测试或真实端点自行复现。

### 15.1 环境与安装

```sh
# Python 3.11,用 uv 建干净 venv(避免本机 pip/conda 环境被污染)
uv venv dshuv --python 3.11
uv pip install --python /tmp/dshuv/bin/python deepseek-harness-sdk
# 结果:安装 deepseek-harness-sdk==0.1.0rc6 + 同版本运行时二进制(约 50MB)
```

Java 侧:引入 `com.deepseek-ai:deepseek-harness4j-sdk` 依赖并安装运行时载体(`mvn install`,见 `development.md`)。

### 15.2 第一次调用(纯对话)

```sh
export DEEPSEEK_API_KEY=你的密钥
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

**真实输出(Python 记录,Java 等价):**

```sh
elapsed_s=10.6
final_response: 'PONG'
finish_reason: completed
session_id: session-565a09a8506b4ea59b77f69f8ba470ed
```

### 15.3 带工具的完整任务(agent 真实写文件 + 执行命令)

```java
import com.deepseek.harness4j.DeepSeekHarness;
import com.deepseek.harness4j.DeepSeekHarnessConfig;
import com.deepseek.harness4j.RunResult;

long t0 = System.nanoTime();
try (DeepSeekHarness h = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
        .provider("deepseek-official")
        .model("deepseek-v4-flash-ga-260731")
        .cwd("/tmp/dsh-demo-ws")           // 独立工作区,让 agent 改
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

**真实输出(Python 记录):**

    elapsed_s=8.3
    finish_reason: completed
    final_response:
     Done! I created `hello.py` with the line `print('hello from dsh')`, then ran it with `python3 hello.py`.
    
    **Exact output:**
    ```
    hello from dsh
    ```

> (上面 `**Exact output:**` 里的三反引号是 agent 自己返回的 markdown 原文,此处用缩进块原样展示。)

**agent 在工作区的真实产物(实查):**

```ini
/tmp/dsh-demo-ws/hello.py          ->  内容: print('hello from dsh')
/tmp/dsh-demo-ws/.sessions/.../session.jsonl.zstd   -> 会话日志(zstd 压缩)
```

### 15.4 会话日志里发生了什么(解压后 37 个事件)

```yaml
Counter: assistant/chunk:12, text-chunks:4, agent/inbox/spliced:2,
         step/start:2, user/message:2, reasoning-chunks:2,
         assistant/message:2, step/end:2, session:1, turn/start:1,
         session/title:1, request/header:1, request/context:1,
         tool-call-chunks:1, tool/call:1, tool/result:1, turn/end:1
```

关键事件摘录:

```py
TOOL CALL: bash
  args: {"command": "printf \"print('hello from dsh')\n\" > hello.py && python3 hello.py",
         "description": "Create hello.py and run with python3"}
TOOL RESULT: {..., 'content': [{'type': 'text', 'text': 'hello from dsh\n'}], 'isError': False}
TURN END: {'kind': 'completed'}
```

**结论(实测印证):**

- dsh 的 agent 循环确实在工作:一次 `turn/start → request/header → request/context → step/start → tool/call(bash) → tool/result → assistant/chunk → turn/end(completed)`。
- 自定义模型接入__零代码__生效:`DEEPSEEK_BASE_URL` 指向任意 OpenAI 兼容端点 + 对应模型 id 即可。
- 会话全程落盘为 JSONL(zstd 压缩),可回放、可审计——正是 1.5 节所说的"可控与可审计"。
- Java SDK 的 `RunResult.finalResponse()` / `finishReason()` / `sessionId()` 与上述结果字段一一对应,可在此配置下复现同样行为。

---

# 第三部分:深度解读

## 十六、深度架构与设计亮点(与其他 Agent harness 的差异化)

> 本节基于对 `docs/architecture.md`、`docs/cordis-primer.md`、`packages/README.md` 以及多个核心包源码的阅读整理。它回答了"这个项目真正特别在哪"。客户端语言(Python/Java)不影响以下全部结论。

### 16.1 核心架构概念(先建立词汇)

| 概念 | 含义 |
|---|---|
| **插件(plugin)** | 一个实现了 `Service` 的对象;通过 `apply(ctx)` 挂到共享 context,一切能力都是插件 |
| **上下文(context)** | 服务的仓库;插件通过 `ctx.<key>`(如 `ctx.tools`、`ctx.llm`、`ctx.sessions`)找服务,而非直接 import 实现 |
| **inject** | 声明服务依赖;插件等依赖服务就绪后才激活,加载顺序由依赖表达而非手工排序 |
| **类型化事件** | 服务用 TS 声明合并定义事件名,按 `emit / waterfall / parallel / serial` 四种模式分派;`waterfall` 是"环绕中间件",调 `next()` 才放行 |
| **可逆副作用(effect)** | 所有注册都经 `ctx.effect()`/`ctx.on()`,插件卸载时按序回卷——这是热更新(HMR)能安全工作的根基 |
| **capability seam** | 一个可替换能力的三件套:Service Definition(接口)+ Service Provider(实现)+ Consumer(消费,通常是模型可见工具) |
| **profile** | 命名组合(存于 Harness home),列出它叠加的 bundles、外置插件与用户 `cordis.patch.yml`;`web`、`headless` 是自带模板 |
| **bundle** | 一种"配置行+所挂载代码"的分发格式,可被上层 patch |
| **turn / step** | step=一次模型请求+它调用的工具;turn=0..n 个 step |
| **Model Experience** | 每个包的 README 必须写清"这个功能对模型可见的内容、token/KV-cache 影响"——文档纪律的体现 |

### 16.2 为什么说它是"DeepSeek 版的 Claude Code,但又不一样"

**① 一切皆插件,连 agent 循环本身都可替换。**
没有"特权核心"。模型适配器、工具注册表、会话日志、甚至 `agent-loop` 都是插件,都能从配置替换/卸载。Claude Code 的循环是写死的;dsh 的循环是插件之一。→ **架构哲学上最大的差异**。

**② "模型可见 ⟺ 已记录"的审计强不变量。**
会话日志不是"尽力而为",而是**运行时强约束**:任何到达模型请求的内容必须能从日志逐 token 重建(新模型可见输入 = 必须新增会话事件类型)。`deriveMessages()` 从日志投影出模型历史,`assistant/chunk` 保留回放与 UI 保真度。Fork/续聊/回放/遥测/测试全部复用同一条数据流。**这是别家 harness 很少做到位的纪律**。

**③ 自修改(self-modification)—— 罕见的"元"能力。**
`extensions/` 包让 **agent 自己在运行时检视、定义、挂载/卸载自己的插件**(经 `node:vm` 沙箱跑宿主插件、动态 Cordis 包工具、浏览器侧 define card)。agent 不是"被框架驱动",而是"能改驱动自己的框架"。

**④ 与 Claude Code / Codex 的互操作桥接。**
`hooks/` 不是另造一套钩子,而是**直接消费 Claude Code 和 Codex 的 `hooks.json`/settings**——已有钩子配置可平移。加上 `acp/`(实现 Anthropic 的 Agent Client Protocol 自动化服务器),从 Claude Code 迁移的成本被压得很低。

**⑤ 单进程多 agent、按会话组合(preset)。**
`preset/` 让一个进程里并存多个**组成不同**的 agent:每个会话可有自己的工具集与提示词段,互不干扰(每个 `agent.ctx` 有独立作用域)。

**⑥ 沙箱是一等公民,且可整体换后端。**
`sandbox/` 提供 bwrap / Landlock / Seatbelt 后端;`e2b/` 是远程沙箱 POC。因 `fs`/`subprocess` 共享同一执行世界,换沙箱后端=一次配置,不 fork 一堆 provider。

**⑦ 双 LLM 适配器 + 动态模型目录 + 按请求解析凭据。**
`deepseek-official`(官方直连)与 pi-ai(通用多提供方)两条 DeepSeek 路径刻意并存;模型 id 不是生命周期配置;密钥按每次请求经 credential seam 解析。→ 这就是"自定义模型=改配置"能成立的根本原因。

**⑧ 工程严谨度极高。**
`typert` 类型图生成、**无密钥的 snapshot 重放测试**(`pnpm run test:snapshot`)、per-file 100% 覆盖率门禁、`Model Experience` 文档强制、每篇非平凡 PR 必须带 Agent Note、`config-catalog.md` 是生成的全量配置目录。**这是把"agent 工程"当"产品工程"做的项目。**

**⑨ 一套内核,多端驱动。**
Web UI + CLI + Python SDK + Java SDK + ACP + JSON-RPC + hooks,全部驱动同一 agent 内核。

### 16.3 差异的另一面(客观提醒)

- **复杂度更高**:框架而非开箱产品,`cordis.yml` 组合、seam 三件套、事件模型的学习曲线明显高于 Claude Code 的简单配置。
- **预览期**:README 自述"会有破坏兼容性的变更"。
- **DeepSeek 优先**:OpenAI/Anthropic 需走 pi-ai catalog 或自定义 provider,不如 Claude Code 绑定自家模型那么开箱即用。
- **主链路单语言**:核心是 Node/TS,Python 只是客户端 SDK(非重实现);**Java(deepseek-harness4j)同样是客户端 SDK(逐行移植 Python SDK,非重实现)**。

---

## 十七、项目全景速查(完整项目介绍补全)

> 这一章补齐一个"项目介绍"该有的其余维度:功能清单、技术栈、系统要求、目录结构、环境变量、术语、许可证与社区、已知限制。

### 17.1 功能特性清单(开箱即用)

| 类别 | 能力 |
|---|---|
| 会话 | 会话 JSONL 日志、持久化(JSONL/SQLite)、Fork/续聊/回放、会话标题、会话检索(全文搜索) |
| 模型 | 多提供方适配、动态模型目录、推理档位(reasoning)、按请求解析密钥、重试/传输恢复 |
| 工具 | 文件系统、bash/持久终端(PTY)、Web 搜索/抓取、LSP、技能(skill)、代码执行、后台任务、子代理、工作流、计划、todo、目标(goal) |
| 安全 | 沙箱(bwrap/Landlock/Seatbelt)、审批/权限策略、工具超时、循环卫生守卫 |
| 协作 | ask-user、命令(commands)、人工反馈、定时跟进(schedule) |
| 扩展 | 一切皆插件、热更新、自修改、preset 按会话组合 |
| 接口 | Web UI、CLI、Python SDK、Java SDK(deepseek-harness4j)、ACP、JSON-RPC、Claude Code/Codex hooks 桥接 |
| 工程 | 类型图生成、快照重放测试、100% 覆盖率门禁、Agent Notes 文档 |

### 17.2 技术栈与关键依赖

| 项 | 值 |
|---|---|
| 语言 | TypeScript(ESM,`strict:true`)、Python(客户端 SDK)、Java(客户端 SDK,deepseek-harness4j) |
| 运行时 | Node.js `^22.19.0 \|\| >=24.0.0`(仓库 engine 字段);Python >=3.10(仅 SDK);Java 17+(仅 deepseek-harness4j) |
| 包管理 | pnpm@11.7.0(monorepo workspaces);Maven(deepseek-harness4j) |
| 底层框架 | **Cordis**(vendored 进 `vendor/`;插件/context/事件/可逆副作用) |
| 配置 schema | `@deepseek-ai/schemastery` |
| 会话存储 | JSONL(zstd 压缩)+ SQLite(元数据/全文检索) |
| 流式解析 | `eventsource-parser`(SSE) |
| 通用多提供方 | `@earendil-works/pi-ai`(llm-pi-ai 适配器) |
| 沙箱 | 原生 `node-addon-landlock-run`(native/) |
| 前端 | Web UI(host + client 双半,浏览器侧),VitePress 文档站 |

### 17.3 系统要求与平台

- **Node**:`^22.19.0 || >=24.0.0`(跑 Web UI / CLI / 源码)。
- **Python SDK**:Python 3.10+;支持 **Linux x64、Linux arm64、macOS 14+(arm64)**;SDK 自带打包运行时,无需系统 Node。
- **Java SDK(deepseek-harness4j)**:JDK 17+;运行时载体平台矩阵与 Python 一致(Linux x64/arm64、macOS 14+ arm64)。
- **平台矩阵**:CI 覆盖 macOS/Linux;存在 Windows 相关测试路径(`test:check:windows-wine`,仅在诊断已知 Windows 故障时跑)。

### 17.4 仓库目录结构(monorepo)

```ini
vendor/      Vendored Cordis 源码(manifest + 同步脚本)
packages/    @deepseek-ai/dsh-<pkg> 工作区,按组:core/ api/ typert/ llm/ shell/
             subprocess/ terminal/ fs/ lsp/ skill/ web/ compaction/ context/
             subagent/ workflow/ todo/ plan/ preset/ guard/ extensions/ hooks/
             session/ session-query/ settings/ credentials/ identity/ acp/
             interaction/ boot/ sdk/ host/ client/ bundle/ examples/ util/
python/      Python SDK + 打包运行时(deepseek-harness-sdk / runtime-bin)
deepseek-harness4j/  Java SDK 移植(本仓库:sdk / sdk-runtime / spring-boot-starter / spring-boot-example / docs)
native/      原生 landlock 沙箱 addon
examples/    可运行的 cordis.yml 叶子(agent-spine + CLI/ACP/JSON-RPC 示例)
apps/        CLI 等应用入口(apps/cli/bin.ts -> dsh)
docs/        架构、生成目录、postmortem、cookbook、用户指南(双语)
website/     VitePress 文档站(精选双语源)
scripts/     仓库门禁与生成器
.agents/     Agent 工作流 + Agent Notes(笔记即文档)
```

### 17.5 环境变量速查

| 变量 | 作用 |
|---|---|
| `DEEPSEEK_API_KEY` | 必填主密钥(deepseek 适配器/默认组合) |
| `DEEPSEEK_BASE_URL` | 可选;指向 OpenAI 兼容代理/自建网关时设置 |
| `DSH_MODEL` | 默认模型 id(默认 `deepseek-v4-flash`) |
| `DSH_CONTEXT_WINDOW` | 覆盖模型上下文窗口 |
| `DSH_SYSTEM_PROMPT` | 覆盖系统提示词(persona) |
| `DSH_CWD` | agent 工作目录 |
| `DSH_SESSION_ROOT` | 会话落盘根目录 |
| `DSH_HOME` | Harness 用户目录(默认 `~/.dsh`;含 `settings.yaml`、`.credentials.yaml`) |
| `DSH_CORDIS_CONFIG` | 注入自定义 cordis 配置(SDK 启动运行时用) |
| `DSH_RUNTIME_MODE` | 运行时载体模式(`exe`/`node`,deepseek-harness4j 的 `RuntimeResolver` 读取) |

> 更多 provider 级字段见 `llm-deepseek`/`llm-pi-ai` README 与 `config-catalog.md`。

### 17.6 核心术语表

**plugin / context / inject / effect** → 见 16.1;**seam / profile / bundle / preset / turn / step / adapter / provider** → 见 16.1 与第三、四章。其余:`Catalog`(提供方内置模型目录)、`modelOverrides`(改单个目录模型)、`credential reference`(凭据引用,不落明文)、`Agent Note`(随 PR 提交的设计笔记)、`snapshot test`(无密钥重放测试)。

### 17.7 许可证与第三方

- **License**:MIT(根 `LICENSE`)。
- 第三方依赖及其许可证披露:`THIRD_PARTY_NOTICES.md`。
- npm 包命名空间:`@deepseek-ai/dsh-*`;CLI 包 `@deepseek-ai/dsh`(bin: `dsh`)。
- Java 包坐标:`com.deepseek-ai:deepseek-harness4j-*`(deepseek-harness4j 仓库)。
- 底层 Cordis 按 vendoring 政策做源码固定(vendor/README.md 有上游 SHA 与同步流程)。

### 17.8 社区与支持

- **GitHub Discussions**:反馈 / bug / 讨论。
- **Discord**:DeepSeek Harness 官方服务器。
- **企微 / 微信公众号**:中文社区入口(扫码见 README.zh)。
- **插件发现**:给插件仓库打 `dsh-plugin` topic 便于被发现。
- **贡献**:`CONTRIBUTING.md`(+ 中文版 `CONTRIBUTING.zh.md`)。

### 17.9 已知限制与暂缓事项(预览期)

- 配置默认未映射 `tool_choice`(MVP 取舍)。
- 部分能力仅 POC 状态:`e2b`(远程沙箱)、部分 workflow 工具。
- settings 的 `models` 列表会**整体替换**组合列表(按字段合并、数组是单字段)。
- 请求用原生 `fetch`,暂不共享 Cordis http 插件的 proxy/拦截配置。
- DeepSeek 官方 chat-completions 路由为纯文本,无法配置成图片模态。
- 总体仍是开发者预览:**接口与格式可能随时变化**。

### 17.10 一句话定位

> **dsh 是一个"一切皆插件、模型无关、可自托管、多端驱动、带强审计纪律与沙箱"的通用 Agent harness——DeepSeek 版的可组合 Claude Code,适合想自己掌控 agent 运行时、接私有/自建模型、并希望行为可复现可审计的团队。deepseek-harness4j 把其中的 Python SDK 客户端逐行移植为 Java,让 Java/Spring 技术栈也能直接嵌入这套运行时。**
