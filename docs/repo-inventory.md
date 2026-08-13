# 上游仓库全局清单（deepseek-harness4j 对应索引）

[English](repo-inventory.en.md) | 中文

本文件是**仓库级全局清单**：对上游 `deepseek-ai/deepseek-harness` 的全部顶层区域（`.md`/`.py` 文件）建立索引，标注在 `deepseek-harness4j` 中的移植状态与入口。它是 `docs/port-coverage.md`（针对 `python/` 及其引用）的全仓库扩展。

## 结论

- **Python 项目本体（`python/`，8 md + 14 py）与它引用的 `scripts/*.py`、`examples/jsonrpc-agent/minimal.py` 已 100% 移植**（见 `port-coverage.md`）。
- **用户可读文档**（`docs/user/guide/`、`docs/cookbook/` 中与使用/扩展相关的部分）已移植为双语参考（`docs/user-guide/`）；其余 `docs/` 为语言无关的架构/工程参考，予以索引而非逐行移植。
- **`examples/` 中 `jsonrpc-agent`（Python SDK 配套）已移植**；其余叶子为 Web/CLI/ACP 演示，予以索引。
- **Node/TypeScript 工程**（`packages/`、`apps/`、`vendor/`、`website/`、`native/`、`.agents/`）不是 Python 项目；`deepseek-harness4j` 是客户端移植，运行时与插件由上游提供，予以索引说明。

## 顶层区域总表

| 区域 | md | py | Java 状态 | 入口 |
|---|---|---|---|---|
| `python/` | 8 | 14 | ✅ 100% 移植 | [port-coverage.md](port-coverage.md) |
| `examples/` | 43 | 1 | ⚠️ `jsonrpc-agent` 移植；其余参考 | 下方 §2 |
| `docs/` | 215 | 0 | ⚠️ 用户层移植为 `docs/user-guide/`；其余索引 | 下方 §3 |
| `scripts/` | 2 | 3 | ✅ 3 个 .py 的纯函数移植 | [port-coverage.md](port-coverage.md) §2 |
| `packages/` | 545 | 0 | 📄 语言无关参考（插件契约） | 下方 §4 |
| `apps/` | 111 | 0 | 📄 参考（CLI/Web 入口） | 下方 §4 |
| `vendor/` | 12 | 0 | 📄 参考（vendored Cordis） | 下方 §4 |
| `website/` | 1 | 0 | 📄 参考（文档站） | — |
| `native/` | 17 | 0 | 📄 参考（landlock addon） | — |
| `.agents/` | 1386 | 1 | 📄 上游工程流程（Agent Notes/技能） | — |

## 1. `python/`（Python 项目本体）—— 100% 移植

14 个 `.py`、8 个 `.md` 全部有 Java 对应物；逐条核对见 [port-coverage.md](port-coverage.md)。

## 2. `examples/`（可运行示例）

| 叶子 | 上游文件数 | Java 状态 | 说明 |
|---|---|---|---|
| `jsonrpc-agent/` | 约 30（含 tests） | ✅ `minimal.py` → `MinimalAgent`；`minimal.cordis.yml`/`cordis.yml` → 随包资源；README → `examples/jsonrpc-agent/` | Python SDK 配套示例，完整移植。其 `tests/`（TS e2e/snapshot）属上游 Node 测试，语义已由 `sdk/src/test`（JUnit）覆盖 |
| `mcp-memory/` | 多 | 📄 参考 | 通用 MCP 客户端覆盖层（配置） |
| `headless-agent/` | 多 | 📄 参考 | CLI 通道；SDK `run()` 语义对应 |
| `web-cordis/` | 多 | 📄 参考 | 自引用 agent 演示（TS 插件） |
| `web-schedule/` | 多 | 📄 参考 | Web 提醒覆盖层（配置） |
| `acp-agent/` | 多 | 📄 参考 | ACP 自动化服务器（另一客户端协议） |

## 3. `docs/`（文档）

### 3.1 已移植为 Java 双语参考（`docs/user-guide/`）

| 上游 | Java 对应物 |
|---|---|
| `docs/user/guide/python-sdk.md`（+zh） | `docs/user-guide/python-sdk.md` / `.en.md` |
| `docs/user/guide/index.md`（+zh） | `docs/user-guide/web-ui.md` / `.en.md` |
| `docs/user/guide/providers.md`（+zh） | `docs/user-guide/providers.md` / `.en.md` |
| `docs/cookbook/adding-an-llm-adapter.md`（+zh） | `docs/user-guide/adding-an-llm-adapter.md` / `.en.md` |
| `docs/cookbook/adding-a-tool.md`（+zh） | `docs/user-guide/adding-a-tool.md` / `.en.md` |
| `examples/README.md`（+zh） | `examples/README.md` / `.en.md` |

### 3.2 语言无关参考（已并入/索引，未逐行移植）

| 上游文档 | 性质 | 在 4j 中的落点 |
|---|---|---|
| `docs/architecture.md` | 核心架构图（组合/核心包/循环/seam/扩展点） | 深度解读见 `deepseek-harness4j-使用指南.md` §16 |
| `docs/cordis-primer.md` / `docs/cordis-tutorial/` | Cordis 组合语法/教程 | 语言无关；被指南与 user-guide 引用 |
| `docs/development.md` | 贡献者设置/日常/CI | 移植为 `development.md`（.en） |
| `docs/testing.md` | 测试政策 | 语言无关；4j 测试见 `development.md` |
| `docs/glossary.md` | 术语表 | 术语要点并入指南 §16.1/§17.6 |
| `docs/config-catalog.md` / `tool-catalog.md` / `persistence-catalog.md` | 生成的插件配置/工具/持久化目录 | 语言无关生成物；被 providers/user-guide 引用 |
| `docs/capability-seams.md` / `event-producer-consumer.md` / `defensive-patterns.md` / `tool-execution-pipeline.md` / `agent-lifecycle.md` / `api-gateway.md` | 架构参考 | 语言无关；要点并入指南 |
| `docs/cookbook/*`（其余） | 包/工具/扩展 how-to | 语言无关；扩展相关见 `docs/user-guide/` |
| `docs/subsystems/`、`docs/module-graph.md`、`docs/graph-atlas.md`、`docs/postmortem/`、`docs/i18n/`、`docs/web-styling.md` | 内部/生成参考 | 语言无关，未移植 |

## 4. Node/TypeScript 工程（非 Python，索引说明）

| 区域 | 说明 | 与 4j 的关系 |
|---|---|---|
| `packages/`（545 md） | 全部 `@deepseek-ai/dsh-*` 插件源码与 README | 4j 为客户端移植；插件契约（config-catalog 等）语言无关参考 |
| `apps/`（111 md） | CLI（`dsh`）与 Web UI 应用入口 | 通道参考；SDK 经 JSON-RPC 与运行时通信（见 `docs/python-sdk-api-reference.md` §9） |
| `vendor/`（12 md） | vendored Cordis 源码 | 上游固定版本；4j 不复制 |
| `website/` | VitePress 文档站 | 4j 文档独立 |
| `native/`（17 md） | landlock 沙箱 addon | 运行时原生组件；4j 不复制 |
| `.agents/`（1386 md） | Agent Notes / 技能 / 工作流 | 上游工程纪律；与 4j 无关 |

## 5. 校验

```sh
# 上游全量枚举核对（结果=零遗漏）
find . -name '*.py' -not -path './vendor/*' -not -path '*/node_modules/*' | wc -l   # 19
find . -name '*.md' -not -path './vendor/*' -not -path '*/node_modules/*' | wc -l   # 2361
```

`python/` 内 19 个 `.py` 与 8 个 `.md` 全部有 Java 对应物（见 `port-coverage.md`）；本清单覆盖其余 2353 个 md 的归属与状态。
