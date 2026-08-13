# Java SDK 快速上手

[English](python-sdk.en.md) | 中文

> 本文件是上游 `docs/user/guide/python-sdk.md`（+`python-sdk.zh.md`）的 Java 移植：安装、运行检入示例、在自有程序里调用 SDK 的教程路径，全部改写为 `deepseek-harness4j`。Web UI 的等价教程见 [使用 Web UI](web-ui.md)；模型配置见 [配置模型](providers.md)。

本教程是 Web UI 的编程替代路径。它安装已发布的 Java SDK、运行一个检入的 agent 组合，并演示如何在自有程序中调用同一套 API。

## 前置条件

- JDK 17 或更新
- Maven 3.9+
- Linux x64、Linux arm64 或 macOS 14 或更新（arm64）上的运行时载体（见 `development.md` 的"构建运行时产物"）
- 一个 DeepSeek 兼容的 API 端点与凭据
- 一个允许 agent 修改的隔离工作区

## 安装 SDK

克隆上游仓库获取其可运行示例，然后引入 SDK 依赖（构建运行时载体见 `development.md`）：

```sh
git clone https://github.com/deepseek-ai/deepseek-harness.git
cd deepseek-harness
pnpm install
pnpm exec tsx scripts/build-exe-for-python-sdk.ts   # 构建运行时载体，产物放入 deepseek-harness4j 运行时目录
```

```xml
<dependency>
  <groupId>com.deepseek-ai</groupId>
  <artifactId>deepseek-harness4j-sdk</artifactId>
  <version>0.0.1-SNAPSHOT</version>
</dependency>
```

已安装的运行时无需系统 Node.js。需要从源码构建运行时或分发的仓库贡献者，参见 [开发工作流](../../development.md)。

## 运行检入示例

在环境中设置凭据。当模型由 OpenAI 兼容代理而非默认 DeepSeek 端点提供时，同时设置 `DEEPSEEK_BASE_URL`。

```sh
export DEEPSEEK_API_KEY=sk-your-key-here
# export DEEPSEEK_BASE_URL=http://127.0.0.1:8000/v1
# export DSH_MODEL=deepseek-v4-flash
# export DSH_SYSTEM_PROMPT='You are a helpful software engineer assistant.'
```

针对隔离工作区与会话目录运行一个任务（示例入口为 `com.deepseek.harness4j.examples.MinimalAgent`，对应上游 `examples/jsonrpc-agent/minimal.py`）：

```sh
cd deepseek-harness4j
mvn -q -pl sdk exec:java \
  -Dexec.mainClass=com.deepseek.harness4j.examples.MinimalAgent \
  -Dexec.args="--workspace /absolute/path/to/workspace --session-root /absolute/path/to/sessions --session-id example-001 'Inspect the repository and fix the failing tests.'"
```

脚本打印最终助手回复。会话目录收到一份 JSONL 日志，其中包含组装好的模型请求与工具调用。

## 在自有程序中使用 SDK

检入示例是对下面这个 SDK 调用的薄包装：

```java
import com.deepseek.harness4j.DeepSeekHarness;
import com.deepseek.harness4j.DeepSeekHarnessConfig;
import com.deepseek.harness4j.RunResult;

import java.nio.file.Path;

public class Example {
    public static void main(String[] args) {
        String config = Path.of("examples/jsonrpc-agent/minimal.cordis.yml").toAbsolutePath().normalize().toString();
        String workspace = Path.of("/absolute/path/to/workspace").toAbsolutePath().normalize().toString();
        String sessions = Path.of("/absolute/path/to/sessions").toAbsolutePath().normalize().toString();

        try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
                .provider("deepseek-official")
                .model("deepseek-v4-flash")
                .maxTokens(49_152)
                .cwd(workspace)
                .sessionRoot(sessions)
                .cordis(config)
                .build())) {
            RunResult result = harness.run(
                    "Inspect the repository and fix the failing tests.",
                    "example-001",
                    null);
            System.out.println(result.finalResponse());
        }
    }
}
```

`DeepSeekHarness` 按需启动内置运行时，并在上下文退出前复用（`try`-with-resources 对应 Python 的上下文管理器）。复用同一 harness 与会话 id 会保留会话拥有的 Bash 进程，包括其工作目录、导出的变量与 shell 函数。独立任务请用全新会话 id；仅当下次调用应继续同一段持久对话时才复用 id。

## 理解示例组合

| 属性 | 值 |
|---|---|
| 系统提示词 | `DSH_SYSTEM_PROMPT`，未设置时回退到 `You are a helpful software engineer assistant.` |
| `MinimalAgent` 的模型 | `--model`，其次 `DSH_MODEL`，再其次 `deepseek-v4-flash` |
| 模型可见工具 | 仅持久 `bash` 与 `str_replace_editor` |
| Bash 超时 | 300 秒 |
| 编辑器输出上限 | 16,000 字符 |
| 上下文压缩 | 禁用 |
| 文件系统 | 裸本地后端；编辑器绝对路径可指向运行时进程可见的任何路径 |
| 会话持久化 | `DSH_SESSION_ROOT` 下未压缩 JSONL |

该组合省略了 harness 身份、工作区提示词文本、技能、一次性 Bash、任务工具、压缩以及所有其他模型可见插件。sandbox-policy 事实以运行时用户上下文记录，而非追加到系统提示词。

## 选择工作区与会话 id

`cwd` 选择 agent 可用的工作区，`session_root` 存储会话日志与状态。独立任务请用全新会话 id；仅当下次调用应继续同一对话与持久 shell 状态时才复用 id。

该组合使用 `danger-full-access`。只能在可丢弃的 checkout 或容器中运行：Bash 与编辑器可修改运行时进程允许的任何路径。持久 PTY 后端需要 POSIX 终端底层，因此该组合不支持 Windows agent。

`jsonrpc-agent` 示例参考（`examples/jsonrpc-agent/README.md`）拥有精确组合；SDK 参考（`sdk/README.md`）覆盖生命周期、结果、通知、运行时选择与配置；Cordis 组合语法见上游 `docs/cordis-primer.md`（语言无关参考）。
