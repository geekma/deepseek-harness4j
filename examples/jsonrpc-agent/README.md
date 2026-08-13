# jsonrpc-agent（deepseek-harness4j Java 示例）

[English](README.en.md) | 中文

面向 Java SDK 内置 JSON-RPC 运行时的无人值守编码 agent（智能体）组合。它有意不加载终端 UI、控制台日志记录器、批准界面或用户交互工具，因为 stdout 属于 SDK 协议，轮次由 SDK 驱动。

> 本文件是上游 `examples/jsonrpc-agent/README.md` 的 Java 移植：`minimal.py` 由 `sdk/.../examples/MinimalAgent.java`（`com.deepseek.harness4j.examples.MinimalAgent`）取代，其余（`cordis.yml` 组合、环境变量、运行时语义）与上游完全一致。

面向模型的工具为：

- `bash`，仅前台
- `read`、`write` 和 `edit`
- `subagent`，使用一个在进程内以前台方式运行的 spawn 提供方
- `todo_write`

周边运行时还加载 JSONL 会话持久化和自动上下文压缩（context compaction）。`maxTokensAsSuccess` 将受 token 上限限制的模型轮次保留为已接受的评估结果，同时保留其 `max-tokens` 原因。

## 运行时环境

| 变量 | 用途 |
|---|---|
| `DEEPSEEK_API_KEY` | 传给 OpenAI 兼容宿主端点的凭据 |
| `DEEPSEEK_BASE_URL` | `dsh-llm-deepseek` 使用的宿主端点 |
| `DSH_CWD` | bash 和文件系统工具使用的 agent workspace |
| `DSH_CONTEXT_WINDOW` | 极简变体中为 `DSH_MODEL` 目录项记录的上下文容量 |
| `DSH_MAX_TOKENS_AS_SUCCESS` | `true`（默认）接受受 token 上限限制的结果；`false` 将其报告为错误 |
| `DSH_MODEL` | `MinimalAgent` 使用的默认模型；`--model` 优先 |
| `DSH_SESSION_ROOT` | JSONL 会话目录 |
| `DSH_SYSTEM_PROMPT` | 由部署提供的编码人格 |

通过 SDK 的 `cordis` 选项或 `DSH_CORDIS_CONFIG` 传入配置路径。内置可执行文件已携带此文件中指定的每个插件；目标机器无需 Node.js。

## 极简变体

`minimal.cordis.yml` 是 Web `minimal` preset 的完整独立版本。`DSH_SYSTEM_PROMPT` 选择它的系统提示词，未设置时使用 `You are a helpful software engineer assistant.`。它为新建会话抑制每个 system-prompt runtime-context 贡献，且不挂载上下文压缩插件。面向模型的工具严格只有：

- 所有者作用域内持久化的 `bash`
- 提供 `view`、`create`、`str_replace` 与 `insert` 的 `str_replace_editor`

它组合了内置运行时所需的本地 PTY、裸 `fs-local` 后端、供持久 Bash 使用的 danger-full-access 策略，以及未压缩的 JSONL 持久化。Bash 和编辑器绝对路径可以修改运行时进程有权访问的任何路径，因此只能针对可丢弃的 checkout 或容器运行该变体。持久 PTY 需要 POSIX 终端环境，因此不适用于 Windows agent 接口。

`MinimalAgent`（Java）通过 SDK 运行该组合，并把 `DSH_MODEL` 作为默认模型。运行方式（需先安装运行时载体，见 `development.md`）：```sh
cd deepseek-harness4j
export DEEPSEEK_API_KEY=sk-xxx
mvn -q -pl sdk exec:java \
  -Dexec.mainClass=com.deepseek.harness4j.examples.MinimalAgent \
  -Dexec.args="--workspace /绝对/路径/工作区 --session-root /绝对/路径/会话 --session-id example-001 'Read package.json and print the list of scripts.'"
```

或直接用已构建的 classpath：

```sh
mvn -q -pl sdk dependency:build-classpath -Dmdep.outputFile=/tmp/dsh4j-cp.txt
java -cp "sdk/target/classes:$(cat /tmp/dsh4j-cp.txt)" \
  com.deepseek.harness4j.examples.MinimalAgent \
  --workspace /绝对/路径/工作区 "你的任务"
```

SDK 的运行时生命周期与结果语义见 `sdk/README.md`；Java 移植说明见 `docs/java-migration-notes.md`。

## 完整组合（非极简 `cordis.yml`）

除 `minimal.cordis.yml` 外，上游该叶子还有完整的**无人值守编码 agent 部署**配置 `cordis.yml`（`sdk/src/main/resources/examples/jsonrpc-agent/cordis.yml` 随包携带）：stdout 保留给 JSON-RPC（不加载控制台日志/终端 UI），模型每会话经 JSON-RPC 到达（不在配置里钉死），加载 DeepSeek 适配器（thinking=enabled, reasoningEffort=max）、本地 bash（60s 超时）、agent-spine（禁用技能与后台 bash）、JSONL 会话（默认 zstd，快照模式为 none）、语义检查点策略、进程内 spawn subagent（`toolName: subagent`）、`todo_write`、文件系统 + 观察策略、token 计量与基础上下文压缩（thresholdRatio 0.8）。环境变量语义与上游一致（`DEEPSEEK_API_KEY`、`DEEPSEEK_BASE_URL`、`DSH_CWD`、`DSH_CONTEXT_WINDOW`、`DSH_MAX_TOKENS_AS_SUCCESS`、`DSH_MODEL`、`DSH_SESSION_ROOT`、`DSH_SYSTEM_PROMPT`）。
