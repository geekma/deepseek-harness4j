  1→# DeepSeek Harness Java SDK（deepseek-harness4j）
  2→
  3→[English](README.md) | 中文
  4→
  5→**deepseek-harness4j** 是 DeepSeek Harness Python SDK 的 Java 移植版，通过 stdio 上的 JSON-RPC 2.0 驱动同一个 agent 运行时。一切皆插件，MIT 授权，开发者预览阶段。
  6→
  7→Java SDK 通过 stdio 上的按行分隔 JSON-RPC 2.0 与内置运行时（`dsh-jsonrpc-agent`）通信，驱动真实的 agent 循环（会话、系统提示词、工具、子代理、持久化）。**运行时、Cordis 插件组合（`cordis.yml`）、模型配置与上游完全一致，只换客户端语言。**
  8→
  9→> 使用前建议先读 [DeepSeek Harness 完整使用指南（Java 版）](deepseek-harness4j-使用指南.md)，其中含自定义模型接入、`cordis.yml` 组合、常见报错与实测记录。

---

## DeepSeek Harness 是什么

### 它是什么

**DeepSeek Harness**（命令名 `dsh`）是 [DeepSeek AI](https://deepseek.com) 开源的一个 **agent harness（智能体框架）**。

- 核心设计理念：**一切皆插件（Everything is a plugin）**。会话、系统提示词、工具、agent 循环、LLM 接入、bash、文件系统、子进程、Web 能力、子代理、工作流……全部都是可插拔的 Cordis 插件。
- 底层由 [Cordis](https://github.com/cordiverse/cordis) 驱动，其设计理念来自论文 [_A Programming Paradigm for Spatiotemporal Composability_](https://github.com/cordiverse/paper)。
- 授权：**MIT License**。
- 上游项目用 Node.js + TypeScript 编写，是 pnpm monorepo；另附带一个 Python SDK。本仓库 `deepseek-harness4j` 提供对应的 **Java SDK**（逐行移植 Python SDK）。

### 架构特点

| 维度 | 说明 |
|---|---|
| 插件模型 | 每个能力是一条 **capability seam**，由 Service Definition / Service Provider / Consumer 三种角色组成 |
| 核心包 | `@deepseek-ai/dsh-core`（session、system-prompt、tools、agent、agent-loop） |
| LLM 层 | `@deepseek-ai/dsh-llm` + 适配器 `dsh-llm-deepseek`（官方直连）、`dsh-llm-pi-ai`（通用多提供方）、`dsh-llm-retry` |
| 能力层 | shell、subprocess、terminal、fs、lsp、skill、web、compaction、subagent、workflow、todo、plan…… |
| 客户端 | Web UI、CLI、Python SDK、Java SDK（deepseek-harness4j）、ACP（Agent Client Protocol）、JSON-RPC |
| 数据 | 会话持久化到 JSONL 日志，SQLite 存元数据 |

**核心结论：换模型、接自定义服务端，大多属于"配置"而非"改代码"。** 因为 `dsh-llm-pi-ai` 已内置 OpenAI 兼容、Anthropic 等多种协议，自建 OpenAI 兼容网关可直接通过配置文件接入。这条结论对 Java SDK 同样成立——Java 侧只是换了客户端语言，模型配置完全一致。

### 当前状态

- **开发者预览**：快速迭代中，**会有破坏兼容性的变更**。
- 配置/环境变量主入口：`DEEPSEEK_API_KEY`（必填）、`DEEPSEEK_BASE_URL`（可选，指向 OpenAI 兼容代理时用）、根目录 `.env`。
- 用户数据目录：`$DSH_HOME`（默认 `~/.dsh`），其中 `settings.yaml` 存配置、`.credentials.yaml` 存密钥（只写，界面不回显）。

### 类似框架

一句话：**dsh 本质上是 DeepSeek 版的 Claude Code / 可自托管的通用 agent harness**，它和当下主流 agent 产品/框架高度同源，但又刻意做了几个差异化。

| 同类产品/框架 | 与 dsh 的关系 |
|---|---|
| **Claude Code / Claude Agent SDK**（Anthropic） | 最接近的对标。同为"agent harness"：会话、系统提示词、工具循环、子代理、钩子（hook）、持久化都内置。dsh 几乎就是 DeepSeek 对 Claude Code 的复刻+改造，区别是**自托管、MIT、provider 无关** |
| **Codex CLI**（OpenAI）、**Gemini CLI**（Google） | 同一类"终端 agent CLI"。dsh 的 headless CLI 与之对应 |
| **OpenHands**（原 OpenDevin） | 同为开源自主软件开发 agent，dsh 与之同赛道 |
| **Aider** | 终端结对编程 agent；dsh 更偏"可组合的框架"而非"单一工具" |
| **LangGraph / LangChain / smolagents / CrewAI** | 都是"agent 构建库"。dsh 更"开箱即用"，内置完整 harness 而非只给编排原语 |
| **MCP（Model Context Protocol）** 工具生态 | dsh 的"能力插件"（bash/fs/web/skill/lsp…）与 MCP 工具思路相近，但 dsh 是完整运行时而非仅工具协议 |

**一句话差异点：** 相比 Claude Code 这类"绑定自家模型"的产品，dsh 的最大卖点是 **LLM 接入是一个可插拔 seam**——官方适配 DeepSeek，但通过 `llm-pi-ai` 也能接 OpenAI、Anthropic、任意 OpenAI 兼容网关，甚至自己写适配器接私有模型。这决定了它更像"**你可以自己掌控的 agent 运行时**"。

### 能解决什么问题

1. **摆脱模型锁定（provider lock-in）** —— LLM 层是抽象 seam，换 DeepSeek / OpenAI / Anthropic / 自建网关只改配置不改业务代码。对用私有模型、公司网关、自建 vLLM/Ollama 的场景尤其友好。
2. **不用从零造 agent** —— 会话管理、系统提示词、工具循环、子代理、工作流、计划、审批/权限、沙箱、持久化全部内置，拿来即用。
3. **极强的可扩展性** —— "一切皆插件"：要 bash/文件/网页/技能/LSP 能力就组合对应插件，支持热更新（HMR），多数行为改 `cordis.yml` 即可，不必改 `agent-loop`。
4. **可控与可审计** —— 每步的模型请求/工具调用都落到会话 JSONL 日志，可回放、可复现；审批/权限策略 + 沙箱（landlock）让 agent 不至于乱来。
5. **一套核心，多端驱动** —— 同一个 agent 内核可被 Web UI、CLI、Python SDK、Java SDK、ACP、JSON-RPC 调用，方便嵌入自己的程序/工作流/CI。
6. **多代理协作** —— 子代理委派、product/code 多 agent 并行等能力开箱即用。
7. **私有化部署、MIT 开源** —— 不依赖某个封闭云平台，可以完全部署在自己环境里。

---

## 安装与使用

### 前置条件

- **JDK 17+**（LTS；代码亦可跑在 21 / 25 上）
- **Maven 3.9+**
- DeepSeek Harness 运行时载体（见下方[运行时载体安装](#运行时载体安装)，或 [sdk-runtime README](sdk-runtime/README.md)）

### 从源码构建

```sh
git clone https://github.com/geekma/deepseek-harness4j.git
cd deepseek-harness4j
mvn install            # 构建全部模块（sdk / spring-boot-starter / spring-boot-example）
mvn test               # 运行测试（sdk 模块，60 个用例）
```

### 作为 Maven 依赖引入

在 `pom.xml` 中添加：

```xml
<dependency>
    <groupId>com.deepseek-ai</groupId>
    <artifactId>deepseek-harness4j-sdk</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

### 运行时载体安装

Java SDK 通过 stdio 上的 JSON-RPC 2.0 与 DeepSeek Harness 运行时（`dsh-jsonrpc-agent`）通信。运行时二进制文件是从[上游 DeepSeek Harness 仓库](https://github.com/deepseek-ai/deepseek-harness)用上游工具链（Node.js / pnpm）交叉构建出来的。**Java SDK 的使用者只需要最终产物——不需要安装 pnpm 或 Node.js 就能使用 deepseek-harness4j。**

**方案 A — 使用预编译二进制（推荐）：** 从上游 Release 页面下载 `dsh-jsonrpc-agent-pkg-<平台>-<架构>` 产物，放入运行时目录。目录约定与零配置解析规则见 [sdk-runtime/README.md](sdk-runtime/README.md)。

**方案 B — 从上游源码构建（需要 Node.js 22+ 与 pnpm）：**

```sh
# 上游构建步骤（使用上游工具链 — Java SDK 使用者不需要这一步）
git clone https://github.com/deepseek-ai/deepseek-harness.git
cd deepseek-harness
corepack enable            # 确保 pnpm 可用
pnpm install
pnpm exec tsx scripts/build-exe-for-python-sdk.ts
# 把生成的 dsh-jsonrpc-agent-pkg-<平台>-<arch> 放到
# deepseek-harness4j 的运行时目录（见 sdk-runtime/README）
```

产物放置位置与详细分发流程见 [sdk-runtime/README.md](sdk-runtime/README.md) 与 [development.md](development.md)。

### 上游配套客户端

上游 DeepSeek Harness 项目（非本 Java SDK）还提供以下客户端，它们与 Java SDK 共享同一个 agent 内核、模型配置和 `cordis.yml` 组合：

- **Web UI**（上游）：`npx @deepseek-ai/dsh web`
- **无头 CLI**（上游）：`dsh --profile headless "任务"`
- **Python SDK**（上游）：`pip install deepseek-harness-sdk`

本仓库的 Java SDK 使用与上述客户端**相同的运行时和配置**——只是客户端语言不同。

---

## 快速上手（Java SDK）

```

### 最小示例

```java
import com.deepseek.harness4j.DeepSeekHarness;
import com.deepseek.harness4j.RunResult;

try (DeepSeekHarness harness = new DeepSeekHarness()) {
    RunResult result = harness.run("Say hi.");
    System.out.println(result.finalResponse());
}
```

`new DeepSeekHarness()` 默认拉起随运行时载体附带的 `dsh-jsonrpc-agent` 可执行文件，并注入默认组合（JSON-RPC 服务器 + agent core + DeepSeek 适配器 + JSONL 会话持久化 + 本地 bash）。

### 完整参数化示例

```java
import com.deepseek.harness4j.DeepSeekHarness;
import com.deepseek.harness4j.DeepSeekHarnessConfig;
import com.deepseek.harness4j.RunResult;

String config    = Path.of("examples/jsonrpc-agent/minimal.cordis.yml").toAbsolutePath().normalize().toString();
String workspace = Path.of("/绝对/路径/到/你的/工作区").toAbsolutePath().normalize().toString();
String sessions  = Path.of("/绝对/路径/到/会话目录").toAbsolutePath().normalize().toString();

try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
        .provider("deepseek-official")      // 提供方路由（由 cordis.yml 里的适配器注册）
        .model("deepseek-v4-flash")         // 模型 id
        .maxTokens(49_152)                  // 每次请求的输出 token 上限（可选）
        .cwd(workspace)                     // agent 的工作目录（会真正改里面的文件）
        .sessionRoot(sessions)              // 会话 JSONL 落盘位置
        .cordis(config)                     // 你自己的插件组合文件
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

### DeepSeekHarnessConfig builder 字段

| 参数 | 作用 |
|---|---|
| `provider` | 选择由哪个适配器注册的__提供方路由__（如 `deepseek-official`；自定义组合挂 `llm-pi-ai` 后可写任意 catalog 提供方） |
| `model` | 该提供方解析的模型 id |
| `maxTokens` | 根 agent 及其进程内后代的输出 token 上限；省略则用提供方默认值 |
| `cordis` | 自定义插件组合文件路径（不传则用随包默认组合） |
| `cwd` | agent 工作目录（自动转绝对路径，与 Python `Path.resolve()` 一致） |
| `sessionRoot` | 会话落盘目录（等价于设 `DSH_SESSION_ROOT`） |

### RunResult API

| 方法 | 说明 |
|---|---|
| `finalResponse()` | 根会话最后的助手文本 |
| `finishReason()` | 结束原因：`completed` / `max-tokens` / `error` ... |
| `sessionId()` | 会话 id（复用可续聊） |
| `events()` | 会话事件列表（含模型请求、工具调用等） |
| `notifications()` | 子代理与运行时通知列表 |

---

## 自定义模型配置

自定义模型 = __配置而非改代码__：设置 `DEEPSEEK_BASE_URL` 指向任意 OpenAI 兼容端点即可。分三种场景，难度递增：

1. **OpenAI 兼容端点 / 公司网关 / 自建服务** —— 纯配置，最常用。
2. **用已支持协议的提供方（Anthropic/OpenAI 等）** —— 填 key 即可。
3. **完全不兼容任何内置协议的私有模型** —— 需要写一个 LLM 适配器插件（代码）。

### OpenAI 兼容端点（推荐，零代码）

#### 环境变量方式（最简）

不写 settings，直接通过环境变量让 DeepSeek 适配器走你的 OpenAI 兼容代理：

```sh
export DEEPSEEK_API_KEY=sk-your-key
export DEEPSEEK_BASE_URL=https://your-gateway.example.com/v1   # 指向你的端点
export DSH_MODEL=your-custom-model-id
```

Java SDK 运行时自动继承这些环境变量：

```java
try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
        .provider("deepseek-official")
        .model("your-custom-model-id")
        .build())) {
    System.out.println(harness.run("你的任务描述").finalResponse());
}
```

三种接法：

```sh
# 接法 A：官方 DeepSeek
export DEEPSEEK_API_KEY=sk-xxx

# 接法 B：OpenAI 兼容自建网关（vLLM / Ollama / LM Studio / 公司代理……）
export DEEPSEEK_API_KEY=sk-任意非空值
export DEEPSEEK_BASE_URL=http://127.0.0.1:8000/v1
export DSH_MODEL=qwen2.5-72b-instruct

# 接法 C：更精细的多提供方 -> 在 cordis.yml 挂 llm-pi-ai 并在 settings.yaml 配置（见下文）
```

Java SDK 指向自定义 provider：

```java
try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
        .provider("acme-gateway")      // settings.yaml 里自定义的路由
        .model("acme-large")
        .cordis("examples/jsonrpc-agent/minimal.cordis.yml")
        .build())) {
    System.out.println(harness.run("写一段 Java 读取 JSON 的示例", null, null).finalResponse());
}
```

#### settings.yaml 方式（可脚本化/可审计）

在 `$DSH_HOME/settings.yaml`（默认 `~/.dsh/settings.yaml`）里，`llm-pi-ai:` 下的 `providers` 是"按提供方路由为键"的字典，可以完全自定义：

```yaml
# ~/.dsh/settings.yaml（运行时可热更新，下一次请求生效，无需重启）
llm-pi-ai:
  providers:
    # 完全手写的自定义路由：自建/私有网关
    acme-gateway:
      displayName: Acme Gateway
      apiKeyEnv: ACME_GATEWAY_API_KEY     # 凭据引用，不写明文密钥
      api: openai-completions             # 协议：openai-completions / openai-responses / anthropic ...
      baseURL: https://gateway.acme.example/v1
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
          reasoningEfforts:
            off:
            high: high
            max: ultra

    # 用目录提供方但覆盖/收窄模型
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

    # 视觉模型：声明图片模态
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

关键语义：

- `apiKeyEnv` 是**按请求解析的凭据引用**（解析自 `.credentials.yaml` / 环境变量），配置文件里不落明文密钥。
- `models` 列表会**整体替换**该路由的 catalog；只写一个 `id` 也够（其余继承 catalog 或路由兜底值）。
- `modelOverrides` 是"就地改一个 catalog 模型"，不替换整个列表。
- 请求协议支持 `openai-completions` / `openai-responses` / `anthropic` 等。

### 目录提供方（Anthropic / OpenAI 等）

Web UI：**Settings -> Models -> Add provider**，选择提供方、填 API key、保存即可。已安装目录会提供端点、协议、模型列表。带原生认证的提供方（Bedrock/AWS、Vertex/ADC、Azure/api-version、Codex/OAuth）需填各自原生凭据，仅填 API key 无法完成配置。

### 自定义 LLM 适配器（完全不兼容的私有模型）

如果你的模型走的是非 OpenAI 兼容的私有协议，就需要实现一个 `LlmAdapter`（参考仓库内 `packages/llm/llm-deepseek` 与 `packages/llm/llm-pi-ai` 两个完整实现）。

> 适配器是 **TypeScript 插件**，运行在 dsh 运行时一侧；Java SDK 调用方无需改代码--只改 `cordis.yml` 挂载即可。

最小骨架：

```ts
import { Context } from '@deepseek-ai/cordis'
import Schema from '@deepseek-ai/schemastery'
import { LlmAdapter, type GenerateOptions, type StreamChunk } from '@deepseek-ai/dsh-llm'

class MyAdapter extends LlmAdapter {
  constructor(private readonly apiKey: string) { super() }

  async *stream(options: GenerateOptions): AsyncIterable<StreamChunk> {
    // 1) options.messages -> 你的协议请求
    // 2) 调用流式 API
    // 3) 把响应转成 StreamChunk 序列：
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

在 `cordis.yml` 里挂载并让 agent 使用它：

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

更完整的适配器实现与 StreamChunk 协议要点见[使用指南](deepseek-harness4j-使用指南.md)第十二章。

---

## cordis.yml 插件组合

`cordis.yml` 是 dsh 的"配方文件"——列出要加载哪些插件及其配置。下面这份是仓库真实的 `examples/jsonrpc-agent/minimal.cordis.yml`（附中文注释），它组合出一个无头最小 agent：模型只能看到**一个系统提示词 + 两个工具**（持久 bash + 字符串替换编辑器）。

```yaml
# ① JSON-RPC 服务器（给 SDK/程序调用用）
- id: sdk-jsonrpc-server
  name: '@deepseek-ai/dsh-sdk-jsonrpc-server'
  config:
    maxTokensAsSuccess: false

# ② LLM 适配器：DeepSeek 官方直连
- id: llm-deepseek
  name: '@deepseek-ai/dsh-llm-deepseek'
  config:
    apiKeyEnv: DEEPSEEK_API_KEY                 # 凭据引用，不落明文
    streamIdleTimeoutMs: 172800000
    models:                                     # 公布给选择器的模型（可用环境变量动态定）
      - id: !!js process.env.DSH_MODEL ?? 'deepseek-v4-flash'
        contextWindow: !!js Number(process.env.DSH_CONTEXT_WINDOW ?? 1000000)

# ③ 沙箱与安全策略
- id: sandbox
  name: '@deepseek-ai/dsh-sandbox-local'
- id: sandbox-policy
  name: '@deepseek-ai/dsh-sandbox-policy'
  config:
    mode: danger-full-access                    # 完全访问（最小示例图省事）
    workspaceRoot: !!js process.env.DSH_CWD ?? process.cwd()

# ④ 子进程 + 持久终端（bash 工具的后端）
- id: subprocess
  name: '@deepseek-ai/dsh-subprocess-local'
- id: pty
  name: '@deepseek-ai/dsh-terminal'
- id: terminal-bash
  name: '@deepseek-ai/dsh-terminal-bash'
  config:
    timeoutMs: 300000

# ⑤ 文件系统（编辑器用；注意也受上面的沙箱策略约束）
- id: fs-local
  name: '@deepseek-ai/dsh-fs-local'
  config:
    cwd: !!js process.env.DSH_CWD ?? process.cwd()

# ⑥ agent 主干（会话/系统提示词/工具编排都在这里）
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

# ⑦ 模型可见的两个工具：持久 bash + 字符串替换编辑器
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

# ⑧ 会话持久化：JSONL
- id: sessions
  name: '@deepseek-ai/dsh-session-persistence-jsonl'
  config:
    root: !!js process.env.DSH_SESSION_ROOT ?? './.sessions'
    compression: none
```

Java SDK 调用同一份组合：

```java
try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
        .cwd("/绝对/路径/工作区")
        .sessionRoot("/绝对/路径/会话")
        .cordis("examples/jsonrpc-agent/minimal.cordis.yml")
        .build())) {
    harness.run("Read package.json and print the list of scripts.", "example-001", null);
}
```

> 原则：改组合（cordis.yml）**优先于**改代码。几乎所有行为都能通过"加载哪个插件 + 给什么配置"来调整，不需要改 `agent-loop`。Java SDK 调用方同样遵循此原则。更多自定义方式见[使用指南](deepseek-harness4j-使用指南.md)第十章。

---

## Spring Boot 集成

本仓库额外提供了 Spring Boot 自动配置模块 `deepseek-harness4j-spring-boot-starter` 与可运行示例 `deepseek-harness4j-spring-boot-example`。在 `application.yml` 中：

```yaml
deepseek:
  harness:
    provider: deepseek-official
    model: deepseek-v4-flash
    cwd: /absolute/path/to/workspace
    sessionRoot: /absolute/path/to/sessions
    requestTimeoutSeconds: 300
```

应用内直接注入：

```java
@RestController
class HarnessController {
    private final DeepSeekHarnessTemplate template;   // 自动配置提供
    // ... 调用 template.run(input, sessionId, null)
}
```

- **Spring Boot**：`@AutoConfiguration` + `@ConfigurationProperties`（前缀 `deepseek.harness`），`destroyMethod="close"` 保证上下文关闭时回收子进程；`deepseek.harness.enabled=false` 可关闭。
- **Spring Cloud**：`deepseek.harness.*` 属性可由 Spring Cloud Config 配置中心下发，无需改代码。
- **Spring MVC**：`spring-boot-example` 里的 `@RestController` 演示把 HTTP 请求转成一个 `Session.run()` 轮次。
- 详细说明与限制见 [docs/java-migration-notes.md](docs/java-migration-notes.md)。

---

## Python -> Java 移植映射

| Python（上游 `python/`） | Java（本仓库 `deepseek-harness4j/`） |
|---|---|
| `sdk/src/deepseek_harness/__init__.py` | `com.deepseek.harness4j`（包入口 + 公开 API 面） |
| `sdk/src/deepseek_harness/api.py` | `DeepSeekHarness` / `DeepSeekHarnessConfig` / `Session` / `RunResult` |
| `sdk/src/deepseek_harness/client.py` | `client.HarnessClient` / `client.HarnessConfig` / `client.NotificationSubscription` |
| `sdk/src/deepseek_harness/models.py` | `model.*`（`Notification`、`IncomingRequest`、`ServerInfo`、`InitializeResponse`、`JsonValues`） |
| `sdk/src/deepseek_harness/errors.py` | `error.*`（`HarnessException`、`TransportClosedException`、`SdkProtocolException`、`JsonRpcException` 等） |
| `sdk-runtime/src/deepseek_harness_runtime/__init__.py` | `runtime.RuntimeResolver` |
| `sdk-runtime/src/.../runtime/cordis.yml` | `sdk/src/main/resources/runtime/cordis.yml`（classpath 资源） |
| `sdk/pyproject.toml` | `sdk/pom.xml` |
| `sdk/tests/*` | `sdk/src/test/java/**`（JUnit 5） |
| 无（客户端语言差异） | `spring-boot-starter/`、`spring-boot-example/`（Spring 集成） |

完整逐行迁移说明（Python->Java 语法差异、JDK 版本、Spring Boot/Cloud/MVC 集成）见 [docs/java-migration-notes.md](docs/java-migration-notes.md)（[English](docs/java-migration-notes.en.md)）。

---

## 目录结构

```ini
deepseek-harness4j/
├── README.md                 # 项目说明（英文）；README.zh.md（中文，本文件）
├── deepseek-harness4j-使用指南.md  # 完整使用指南（Java 版）
├── docs/
│   ├── port-coverage.md(.en.md)              # 移植覆盖清单：每个 .py/.md -> Java 对应物，零遗漏
│   ├── repo-inventory.md(.en.md)             # 全仓库索引：上游所有区域 md/py 归属与状态
│   ├── python-sdk-api-reference.md(.en.md)   # 公开规格：Python SDK 100% 功能（API+协议）
│   ├── user-guide/                           # 上游 docs/user/guide + cookbook 的 Java 双语移植
│   │   ├── python-sdk.md(.en.md)             #   Java SDK 快速上手
│   │   ├── web-ui.md(.en.md)                 #   使用 Web UI
│   │   ├── providers.md(.en.md)              #   配置模型
│   │   ├── adding-an-llm-adapter.md(.en.md)  #   添加 LLM 适配器
│   │   └── adding-a-tool.md(.en.md)          #   工具编写参考
│   └── java-migration-notes.md(.en.md)       # 移植笔记：语法差异 / JDK / Spring 集成
├── pom.xml                   # Maven 根工程（聚合）
├── sdk/                      # deepseek-harness4j-sdk：核心 JSON-RPC 客户端 + 高层轮次 API
│   ├── README.md(.en.md)     # SDK 使用说明（双语）
│   └── src/main/java/com/deepseek/harness4j/...
│       └── examples/         # MinimalAgent（minimal.py 移植）/ ManualSdkAgentSmoke（冒烟）
├── sdk-runtime/              # 运行时载体说明（RuntimeResolver 对应物；双语 README）
├── examples/jsonrpc-agent/   # jsonrpc-agent 示例说明（minimal.cordis.yml 的 Java 用法；双语）
└── spring-boot-starter/      # Spring Boot 自动配置（@ConfigurationProperties + 模板 bean）
    └── spring-boot-example/  # 可运行的 Spring Boot MVC 示例（REST 控制器）
```

---

## 环境变量速查

| 变量 | 作用 |
|---|---|
| `DEEPSEEK_API_KEY` | 必填主密钥（deepseek 适配器/默认组合） |
| `DEEPSEEK_BASE_URL` | 可选；指向 OpenAI 兼容代理/自建网关时设置 |
| `DSH_MODEL` | 默认模型 id（默认 `deepseek-v4-flash`） |
| `DSH_CONTEXT_WINDOW` | 覆盖模型上下文窗口 |
| `DSH_SYSTEM_PROMPT` | 覆盖系统提示词（persona） |
| `DSH_CWD` | agent 工作目录 |
| `DSH_SESSION_ROOT` | 会话落盘根目录 |
| `DSH_HOME` | Harness 用户目录（默认 `~/.dsh`；含 `settings.yaml`、`.credentials.yaml`） |
| `DSH_CORDIS_CONFIG` | 注入自定义 cordis 配置（SDK 启动运行时用） |
| `DSH_RUNTIME_MODE` | 运行时载体模式（`exe`/`node`，deepseek-harness4j 的 `RuntimeResolver` 读取） |

---

## 文档索引

| 文档 | 说明 |
|---|---|
| [README.md](README.md) | 英文版项目说明 |
| [deepseek-harness4j-使用指南.md](deepseek-harness4j-使用指南.md) | 完整使用指南（背景/安装/自定义模型/组合/Demo/实测/架构解读，Java 版） |
| [docs/port-coverage.md](docs/port-coverage.md) | **移植覆盖清单**：上游每个 .py/.md 与 Java 对应物的逐条核对，零遗漏证明 |
| [docs/repo-inventory.md](docs/repo-inventory.md) | **全仓库索引**：上游所有区域 md/py 归属与状态 |
| [docs/python-sdk-api-reference.md](docs/python-sdk-api-reference.md) | **公开规格**：Python SDK 100% 功能的 API 与 JSON-RPC 线上协议，供其他项目实现等价客户端 |
| [docs/user-guide/python-sdk.md](docs/user-guide/python-sdk.md) | Java SDK 快速上手；Web UI、模型配置、LLM 适配器、工具编写等用户文档同在 `docs/user-guide/` |
| [sdk/README.md](sdk/README.md) | SDK 使用说明（双语） |
| [sdk-runtime/README.md](sdk-runtime/README.md) | 运行时载体与零配置设计（双语） |
| [examples/jsonrpc-agent/README.md](examples/jsonrpc-agent/README.md) | 最小 agent 组合示例（minimal.py -> MinimalAgent） |
| [docs/java-migration-notes.md](docs/java-migration-notes.md) | Python->Java 逐行迁移笔记（语法/JDK/Spring） |
| [docs/test-report.md](docs/test-report.md) | **测试报告**：60 个用例结果 + Python->Java 测试一一映射清单 |
| [development.md](development.md) | 构建、测试与分发工作流（双语） |

---

## 许可证

[MIT](LICENSE)，与上游一致。

Maven 坐标：`com.deepseek-ai:deepseek-harness4j-sdk`