# deepseek-harness4j 移植覆盖清单（Port Coverage Checklist）

[English](port-coverage.en.md) | 中文

本文件是**全局核对清单**：把上游 `deepseek-harness` 中**每一个 `.py` 文件、每一个 `.md` 文件**（以及相关的非代码文件）与 `deepseek-harness4j` 的 Java 对应物一一核对，确保**无遗漏**。核对方法：对上游做 `find . -name '*.py' -name '*.md'` 全量枚举，逐条比对本仓库文件；功能层面对照每个 Python 类/函数与 Java 方法。

结论：**`python/` 目录（Python 项目本体）内 19 个 `.py` 文件与 8 个 `.md` 文件全部有 Java 对应物**；引用的仓库脚本（`scripts/*.py`）与 `examples/jsonrpc-agent/minimal.py` 一并移植。例外（如 `uv.lock`、`*.i18n.yaml`、`sdk-runtime/package.json`）为元数据/构建清单文件，已在表内标注"无 Java 等价物/由 Maven 取代"并说明理由。

## 1. 核对基线（上游全量枚举）

```sh
# 上游 python/ 内 .py
python/sdk/src/deepseek_harness/{__init__,api,client,models,errors}.py
python/sdk/tests/{test_client,test_runtime_resolution,test_bundled_runtime,
                 test_smoke_model,test_release_version,test_macos_deployment_target,
                 manual_sdk_agent_smoke}.py
python/sdk-runtime/hatch_build.py
python/sdk-runtime/src/deepseek_harness_runtime/__init__.py

# 上游 python/ 内 .md（8 个）
python/README.md  python/README.zh.md
python/development.md  python/development.zh.md
python/sdk/README.md  python/sdk/README.zh.md
python/sdk-runtime/README.md  python/sdk-runtime/README.zh.md

# 上游引用/配套 Python（本项目一并移植）
scripts/build-python-release.py
scripts/check-macos-deployment-target.py
scripts/smoke-python-runtime.py
examples/jsonrpc-agent/minimal.py
```

## 2. Python 代码文件 → Java 代码文件

| # | Python 文件 | Java 对应物 | 状态 |
|---|---|---|---|
| 1 | `sdk/src/deepseek_harness/__init__.py` | `com.deepseek.harness4j` 包 + `package-info.java`（公开 API 面与 `__all__` 一致） | ✅ 完整 |
| 2 | `sdk/src/deepseek_harness/api.py` | `DeepSeekHarness.java` `DeepSeekHarnessConfig.java` `Session.java` `RunResult.java` `SessionSupport.java` | ✅ 完整 |
| 3 | `sdk/src/deepseek_harness/client.py` | `client/HarnessClient.java` `client/HarnessConfig.java` `client/NotificationSubscription.java` `client/NotificationFilter.java` | ✅ 完整 |
| 4 | `sdk/src/deepseek_harness/models.py` | `model/Notification.java` `IncomingRequest.java` `ServerInfo.java` `InitializeResponse.java` `JsonValues.java` | ✅ 完整 |
| 5 | `sdk/src/deepseek_harness/errors.py` | `error/HarnessException.java` `TransportClosedException.java` `SdkProtocolException.java` `JsonRpcException.java` `HarnessTimeoutException.java` `MissingRuntimeException.java` | ✅ 完整（含新增异常映射，见迁移笔记 §3） |
| 6 | `sdk/tests/test_client.py` | `client/ClientLevelTest.java` `client/SubscriptionRoutingTest.java` `HighLevelApiTest.java` | ✅ 完整（27 个用例全覆盖） |
| 7 | `sdk/tests/test_runtime_resolution.py` | `runtime/RuntimeResolverTest.java` | ✅ 完整 |
| 8 | `sdk/tests/test_bundled_runtime.py` | `BundledRuntimeBootTest.java`（载体缺失独立跳过，与 Python 一致） | ✅ 完整 |
| 9 | `sdk/tests/test_smoke_model.py` | `build/SmokeCompletionsTest.java` | ✅ 完整 |
| 10 | `sdk/tests/test_release_version.py` | `build/ReleaseVersionTest.java` | ✅ 完整 |
| 11 | `sdk/tests/test_macos_deployment_target.py` | `build/MacOsDeploymentTargetTest.java` | ✅ 完整 |
| 12 | `sdk/tests/manual_sdk_agent_smoke.py` | `examples/ManualSdkAgentSmoke.java` | ✅ 完整 |
| 13 | `sdk-runtime/hatch_build.py` | `build/RuntimeBuildHook.java` + `build/PlatformManifest.java` + `build/RuntimeBuildHookTest.java` | ✅ 完整 |
| 14 | `sdk-runtime/src/deepseek_harness_runtime/__init__.py` | `runtime/RuntimeResolver.java` | ✅ 完整 |
| 15 | `scripts/build-python-release.py` | `build/ReleaseVersion.java` `build/PlatformManifest.java` | ⚠️ 部分：纯函数移植；wheel 暂存/构建由 Maven 取代（见迁移笔记 §8） |
| 16 | `scripts/check-macos-deployment-target.py` | `build/MacOsDeploymentTarget.java` | ✅ 完整 |
| 17 | `scripts/smoke-python-runtime.py` | `test/SmokeCompletions.java`（测试用） | ⚠️ 部分：仅移植被测行为（`completion_chunks`/`text_chunks`/`message_text` + 两个快照 prompt），970 行冒烟服务器本体无 Java 等价物（属上游演示脚本） |
| 18 | `examples/jsonrpc-agent/minimal.py` | `examples/MinimalAgent.java` + 资源 `examples/jsonrpc-agent/minimal.cordis.yml` | ✅ 完整 |
| 19 | （测试内嵌假运行时脚本） | `test/FakeRuntime.java`（`FR_SCENARIO` 选择行为）+ `test/TestRuntimes.java` | ✅ 完整 |

## 3. 文档文件 → Java 文档

| # | Python/上游 .md | Java 对应物 | 状态 |
|---|---|---|---|
| 1 | `python/README.md` / `README.zh.md` | 项目根 `README.md`（英文，主）/ `README.zh.md`（中文） | ✅ 完整（含移植映射、Spring、文档索引） |
| 2 | `python/development.md` / `development.zh.md` | `development.md`（中文）/ `development.en.md`（英文） | ✅ 完整 |
| 3 | `python/sdk/README.md` / `README.zh.md` | `sdk/README.md` / `sdk/README.en.md` | ✅ 完整 |
| 4 | `python/sdk-runtime/README.md` / `README.zh.md` | `sdk-runtime/README.md` / `sdk-runtime/README.en.md` | ✅ 完整 |
| 5 | 根 `deepseek-harness-使用指南.md` | `deepseek-harness4j-使用指南.md` / `deepseek-harness4j-user-guide.en.md`（中英双语） | ✅ 完整（一~十七节全部保留） |
| 6 | `examples/jsonrpc-agent/README.md` / `README.zh.md` | `examples/jsonrpc-agent/README.md` / `README.en.md` | ✅ 完整 |
| 7 | `docs/user/guide/python-sdk.md` / `python-sdk.zh.md` | `docs/user-guide/python-sdk.md` / `.en.md`（Java 教程） | ✅ 完整 |
| 8 | `docs/user/guide/index.md` / `index.zh.md` | `docs/user-guide/web-ui.md` / `.en.md` | ✅ 完整 |
| 9 | `docs/user/guide/providers.md` / `providers.zh.md` | `docs/user-guide/providers.md` / `.en.md` | ✅ 完整 |
| 10 | `docs/cookbook/adding-an-llm-adapter.md` / `.zh.md` | `docs/user-guide/adding-an-llm-adapter.md` / `.en.md` | ✅ 完整 |
| 11 | `docs/cookbook/adding-a-tool.md` / `.zh.md` | `docs/user-guide/adding-a-tool.md` / `.en.md` | ✅ 完整 |
| 12 | `examples/README.md` / `README.zh.md` | `examples/README.md` / `examples/README.en.md` | ✅ 完整 |
| 13 | 迁移/核对补充 | `docs/java-migration-notes.md` / `.en.md`、`docs/port-coverage.md` / `.en.md`、`docs/python-sdk-api-reference.md` / `.en.md`、`docs/repo-inventory.md` / `.en.md`（全仓库索引） | ✅ 新增 |

## 4. 非代码文件 → Java 对应物

| # | 上游文件 | Java 对应物 | 状态 |
|---|---|---|---|
| 1 | `sdk/pyproject.toml` | `sdk/pom.xml` | ✅ 取代 |
| 2 | `sdk-runtime/pyproject.toml` | 运行时载体说明 `sdk-runtime/README.md`（wheel-only 语义保留于 `RuntimeBuildHook`） | ✅ 取代 |
| 3 | `sdk/uv.lock` | 无——Maven 无锁文件；可复现构建用 `dependency:go-offline` / `-o` | 📄 N/A（说明） |
| 4 | `sdk/README.i18n.yaml` | 无——doc-sync 的 blob hash 一致性记录；Java 双语对等文件手工维护 | 📄 N/A（说明） |
| 5 | `python/README.i18n.yaml`、`development.i18n.yaml` | 同上 | 📄 N/A（说明） |
| 6 | `sdk-runtime/README.i18n.yaml` | 同上 | 📄 N/A（说明） |
| 7 | `sdk-runtime/package.json` | 无——上游 Node 部署根（依赖闭包 manifest），由上游 `build-exe-for-python-sdk.ts` 消费；Java SDK 消费其构建产物 | 📄 N/A（说明） |
| 8 | `sdk-runtime/platforms.json` | `sdk/src/main/resources/platforms.json` + `build/PlatformManifest` | ✅ 完整 |
| 9 | `sdk-runtime/src/.../deepseek-harness-runtime.json` | `sdk/src/main/resources/deepseek-harness-runtime.json` | ✅ 完整 |
| 10 | `sdk-runtime/src/.../runtime/cordis.yml` | `sdk/src/main/resources/runtime/cordis.yml` | ✅ 完整 |
| 11 | `examples/jsonrpc-agent/minimal.cordis.yml` | `sdk/src/main/resources/examples/jsonrpc-agent/minimal.cordis.yml` | ✅ 完整 |

## 5. 范围外（非 Python 项目，不移植）

| 文件 | 理由 |
|---|---|
| `.agents/skills/record-browser-gif/scripts/encode_gif.py` | 上游 Agent 技能脚本，不属于 Python SDK 项目 |
| `scripts/build-exe-for-python-sdk.ts` | TypeScript 构建脚本，属上游 Node 工程（Python 侧只消费其产物） |
| `vendor/`、`packages/` 等 Node/TS 源码 | 非 Python；4j 是客户端移植，运行时仍由上游提供 |

## 6. 功能层核对（Python 函数/方法 → Java）

### 6.1 `client.py`（`HarnessClient` / `HarnessConfig` / `NotificationSubscription`）

| Python | Java | ✅ |
|---|---|---|
| `HarnessConfig`（7 字段） | `client/HarnessConfig`（builder） | ✅ |
| `start()` | `HarnessClient.start()` | ✅ |
| `close()` | `HarnessClient.close()`（幂等） | ✅ |
| `initialize(cwd, provider, model, max_tokens)` | `HarnessClient.initialize(...)` | ✅ |
| `session_prompt(session_id, content_blocks, ...)` | `HarnessClient.sessionPrompt(...)` | ✅ |
| `request(method, params, response_model, ...)` | `HarnessClient.request(...)`（泛型） | ✅ |
| `notify(method, params)` | `HarnessClient.notify(...)` | ✅ |
| `next_notification()` | `HarnessClient.nextNotification()` | ✅ |
| `subscribe_notifications(filter)` | `HarnessClient.subscribeNotifications(...)` | ✅ |
| `subscribe_session_notifications(session_id)` | `HarnessClient.subscribeSessionNotifications(...)` | ✅ |
| `next_request()` / `respond()` / `respond_error()` | 同名方法 | ✅ |
| `_request_raw` / `_write_message` / `_reader_loop` / `_stderr_loop` / `_handle_message` | `requestRaw` / `writeMessage` / `readerLoop` / `stderrLoop` / `handleMessage` | ✅ |
| `_fail_waiters` / `_runtime_diagnostics` / `_runtime_closed_error` | 同名私有方法 | ✅ |
| `_default_launch_args` / `_inject_bundled_default_config` | 同名私有方法 | ✅ |
| `_record_session_relationship_locked` / `_notification_belongs_to_session_tree` / `_session_is_descendant_of` | 同名私有方法（subagent 谱系） | ✅ |
| `NotificationSubscription.close/next/drain` | 同名方法 | ✅ |
| `_SessionPromptResponse` / `_ShutdownResponse` / `_int_or_none` | `SessionPromptResponse` / `ShutdownResponse` / `JsonValues.asIntOrNull` | ✅ |

### 6.2 `api.py`

| Python | Java | ✅ |
|---|---|---|
| `DeepSeekHarnessConfig`（14 字段） | `DeepSeekHarnessConfig`（builder） | ✅ |
| `RunResult(session_id, final_response, finish_reason, events, notifications, session_root)` | `RunResult`（record，6 字段同名） | ✅ |
| `DeepSeekHarness.__init__ / client 属性 / start / close / start_session / run` | 同名方法 | ✅ |
| `Session.run(input, on_notification)` | `Session.run(input, onNotification)` | ✅ |
| `_is_inbox_receipt` / `normalize_input` / `final_response` / `finish_reason` | `SessionSupport.isInboxReceipt / normalizeInput / finalResponse / finishReason` | ✅ |

### 6.3 `models.py` / `errors.py` / `__init__.py`

| Python | Java | ✅ |
|---|---|---|
| `Notification` / `IncomingRequest` | `record Notification` / `record IncomingRequest` | ✅ |
| `ServerInfo` / `InitializeResponse`（pydantic） | Jackson POJO | ✅ |
| `JsonScalar/JsonValue/JsonObject`（TypeAlias） | `model/JsonValues`（`Object` + 类型判断） | ✅ |
| `HarnessError` 及其 4 个子类 | `error/*`（6 个异常类，含映射新增 2 个） | ✅ |
| `__all__` 导出清单 | `package-info.java` 公开 API 面 | ✅ |

### 6.4 `sdk-runtime/__init__.py` / `hatch_build.py`

| Python | Java | ✅ |
|---|---|---|
| `PACKAGE_METADATA_FILENAME` / `RUNTIME_MODE_ENV_VAR` | `RuntimeResolver` 常量 | ✅ |
| `bundled_package_dir()` / `bundled_default_config_path()` / `bundled_runtime_path()` | `RuntimeResolver` 同名方法 | ✅ |
| `resolve_bundled_launch_args(mode)` | `RuntimeResolver.resolveBundledLaunchArgs(mode)` | ✅ |
| `_current_platform_tag()` / `_node_launch_args()` | `RuntimeResolver` 私有方法 | ✅ |
| `_load_platforms()` / `_host_platform_tag()` / `RuntimeBuildHook.initialize()` | `PlatformManifest.loadPlatforms/hostPlatformTag/expectedRuntimeFiles` + `RuntimeBuildHook.*` | ✅ |

## 7. 验证方式

```sh
cd deepseek-harness4j
mvn clean install     # 全 reactor（sdk / spring-boot-starter / spring-boot-example）构建 + 测试
mvn -pl sdk test      # sdk 单元测试（含本次新增 RuntimeBuildHookTest）
```

- 测试统计：上游 7 个测试文件 → Java 10 个测试类；真实载体启动用例在无载体时按 Python 语义独立跳过。
- Spring MVC 示例已验证 Tomcat 启动成功（见根 README）。

## 8. 公开引用与"100% 功能实现"入口

其他项目（任意语言）若要对齐 Python SDK 的 100% 能力，使用：

1. **[python-sdk-api-reference.md](python-sdk-api-reference.md)** —— SDK 公开 API、JSON-RPC 线上协议（方法/通知/事件/结果语义）、环境变量、生命周期与 web/cli/sdk/acp 通道关系，逐条可作为实现规格。
2. **[java-migration-notes.md](java-migration-notes.md)** —— Python→Java 差异与替代语义。
3. **用户层文档（Java 版）**：`docs/user-guide/`（SDK 教程 / Web UI / 模型配置 / LLM 适配器 / 工具编写）——上游 `docs/user/guide/` 与 `docs/cookbook/` 的移植。
4. **全仓库索引**：`docs/repo-inventory.md` —— 上游所有区域的 md/py 归属与状态。
5. `deepseek-harness4j-使用指南.md`、`sdk/README.md`、`sdk-runtime/README.md` —— 使用与运行时载体说明。
