# Examples（deepseek-harness4j 示例）

[English](README.en.md) | 中文

> 本文件是上游 `examples/README.md`（+`README.zh.md`）的移植。上游的 `examples/` 有多个叶子（`mcp-memory`、`headless-agent`、`jsonrpc-agent`、`web-cordis`、`web-schedule`、`acp-agent`），其中 **`jsonrpc-agent` 是 Python SDK 的配套示例**，已在本仓库移植为 Java；其余叶子为 Web/CLI/ACP 演示（TypeScript/配置，语言无关参考，见 `docs/repo-inventory.md`）。

DeepSeek Harness 主要接口与扩展点的可运行演示。每个子目录拥有自己的配置、前置条件、命令与详细行为。

## jsonrpc-agent（已移植为 Java）

一个通过 SDK 与 JSON-RPC 驱动的无人值守编码 agent。上游是 `minimal.py`（Python）；本仓库对应物是 `MinimalAgent`（`com.deepseek.harness4j.examples.MinimalAgent`）。示例参考见 [jsonrpc-agent/README.md](jsonrpc-agent/README.md)。

```sh
cd deepseek-harness4j
mvn -q -pl sdk exec:java \
  -Dexec.mainClass=com.deepseek.harness4j.examples.MinimalAgent \
  -Dexec.args="--workspace /绝对/路径/工作区 --session-root /绝对/路径/会话 --session-id example-001 '你的任务'"
```

## 其余上游示例（语言无关参考，未移植代码）

| 叶子 | 说明 | 在 Java 项目中的状态 |
|---|---|---|
| `mcp-memory` | 通过通用 MCP 客户端连接受支持第三方内存服务器的可选覆盖层 | 参考（`examples/mcp-memory/`，上游） |
| `headless-agent` | 接受一个任务、运行并以机器可读或人类可读格式输出的非交互 agent | 参考（CLI 通道；SDK `run()` 语义对应） |
| `web-cordis` | 可检视并修改其内存中 Cordis 插件树的自引用 agent | 参考 |
| `web-schedule` | 可选的 Web 覆盖层，提供持久的 Session 本地提醒 | 参考 |
| `acp-agent` | 面向程序化客户端的 Agent Client Protocol 自动化服务器，支持会话、权限与取消 | 参考（另一客户端协议） |

Java 侧另有 Spring 生态示例：`spring-boot-starter`（自动配置）与 `spring-boot-example`（Spring MVC REST 控制器），见根 README 与 `docs/java-migration-notes.md`。
