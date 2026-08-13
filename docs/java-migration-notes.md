# Python → Java 逐行迁移笔记（deepseek-harness4j）

[English](java-migration-notes.en.md) | 中文

本文记录把上游 `deepseek-harness` 的 Python SDK（`python/sdk` + `python/sdk-runtime`）**逐行移植为 Java** 时遇到的语法、运行时与生态差异，以及 Spring Boot / Spring Cloud / Spring MVC 的集成方式。迁移目标是**语义等价**：同样的运行时、同样的 JSON-RPC 协议、同样的环境变量契约，只有客户端语言不同。

## 1. 总体对应关系

| Python | Java | 说明 |
|---|---|---|
| `deepseek_harness` 包 | `com.deepseek.harness4j` | 包名与公开 API 面保持一致 |
| `DeepSeekHarness` / `Session` / `RunResult` | 同名类 / `record` | API 名称与字段一一对应 |
| pydantic `BaseModel` | Jackson POJO / `record` | `model_validate()` → `ObjectMapper.convertValue()` |
| `dict[str, JsonValue]` | `Map<String, Object>` | JSON 对象用 `Map` 表示 |
| `queue.Queue` | `BlockingQueue`（`LinkedBlockingQueue`） | 队列语义一致 |
| `threading.Lock` | `synchronized` / `ReentrantLock` | 互斥语义一致（Java 的可重入更宽松） |
| `subprocess.Popen` | `ProcessBuilder` + `Process` | 子进程/管道一致 |
| 上下文管理器 `with` | `try`-with-resources（`AutoCloseable`） | **注意**：Java 的 try-with-resources 只调 `close()`，不会像 Python `__enter__` 那样自动 `start()`；需要显式调用 `start()`（见 §4） |
| dataclass | 构造器 + builder | 关键字参数 → builder 链式调用 |
| `uuid4().hex` | `UUID.randomUUID().toString().replace("-","")` | 生成 32 位十六进制会话 id |

## 2. 语法差异速查

- **类型别名/联合类型**：Python 的 `JsonValue: TypeAlias = str | int | float | bool | None | dict | list` 在 Java 用 `Object` + 辅助类型判断（`JsonValues`）表达。Python 的 `isinstance(x, int)` → `JsonValues.isIntegral(x)`。
- **可选字段**：Python dataclass 的 `field: X | None = None` → Java 返回 `null` 的 getter；`record` 的紧凑构造器做非空校验（`Notification`、`IncomingRequest`、`RunResult`）。
- **默认值**：Python 关键字默认值（如 `provider="deepseek-official"`、`shutdown_timeout_seconds=1.0`）→ Java builder 在 `build()` 里落默认值。
- **可变默认值**：Python `env: dict = field(default_factory=dict)` → Java 在构造时复制（防御性拷贝）。
- **`pathlib.Path.resolve()`**：Python 会解析符号链接；Java `Path.toAbsolutePath().normalize()` **不会**。移植的 `DeepSeekHarness.resolve()` 用 `toRealPath()`（存在时）+ 回退,以保持与 Python 一致的"绝对路径"语义（macOS `/var`→`/private/var` 差异即由此而来）。
- **f-string** → `String.format` / `+` 拼接 / `StringBuilder`。
- **生成器/异步**：Python SDK 是同步阻塞式,无生成器;Java 同样同步,线程模型见 §4。
- **`with` + `except`** → `try`-with-resources 内 catch。

## 3. 异常映射（Python → Java）

| Python | Java | 说明 |
|---|---|---|
| `HarnessError` | `HarnessException`（`RuntimeException`） | 基类 |
| `TransportClosedError` | `TransportClosedException` | 运行时退出/stdout 关闭 |
| `SdkProtocolError` | `SdkProtocolException` | 协议违约（如 `turn/end` 缺 `reason.kind`） |
| `JsonRpcError` | `JsonRpcException` | JSON-RPC 错误响应（含 `code` / `message` / `data`） |
| `TimeoutError` | `HarnessTimeoutException` | Java 的 `TimeoutException` 是受检异常,故新增运行时异常 |
| `FileNotFoundError` | `MissingRuntimeException` | Python 异常无受检概念,Java 用运行时异常保持 API 清爽 |
| `ValueError` | `IllegalArgumentException` | 非法参数（如未知运行时模式、pydantic 必填字段缺失） |
| `TypeError` | `IllegalArgumentException` | 响应非 JSON 对象 |
| `RuntimeError` | `IllegalStateException` / `RuntimeException` | 平台校验失败 |

## 4. 进程 / 线程 / 生命周期差异

- **子进程**：`Popen(stdin/stdout/stderr=PIPE, text=True, encoding=utf-8)` → `ProcessBuilder` + `BufferedReader/BufferedWriter(UTF_8)`。`stdin` 使用持久的 `BufferedWriter`（等价于 Python 的行缓冲流）。
- **读 stdout / stderr**：两个守护线程(`dsh-runtime-reader` / `dsh-runtime-stderr`)；stderr 尾 400 行滚动缓冲,用于超时/关闭诊断。
- **请求等待**：Python `queue.Queue(maxsize=1)` + `time.monotonic()` → `LinkedBlockingQueue(1)` + `System.nanoTime()`（同为单调时钟）。
- **锁**：`self._lock` / `self._write_lock` → `synchronized(lock)` / `synchronized(writeLock)`。写消息按 Python 语义串行化（并发写测试覆盖）。
- **上下文管理器**：Python `with HarnessClient(...)` 会 **自动 `start()`**；Java 的 try-with-resources 只 `close()`。因此所有"进入即启动"的调用点在 Java 中显式调用 `start()`（`HarnessClient` 与 `DeepSeekHarness` 的 `start()` 均幂等）。
- **优雅关闭**：`terminate()`(SIGTERM) → `process.destroy()`;超时后 `kill()` → `destroyForcibly()`(SIGKILL)。`shutdown_timeout_seconds=null` 表示无限等待。

## 5. JSON 与序列化

- 序列化：`json.dumps(msg, separators=(",",":"))`（紧凑、无空格）→ Jackson `writeValueAsString`（默认同样无多余空格）。
- 反序列化：pydantic `model_validate` → Jackson `convertValue`;配置 `FAIL_ON_UNKNOWN_PROPERTIES=false`（pydantic 默认忽略多余字段）。
- **必填字段校验**：pydantic 对 `_SessionPromptResponse.messageId` 必填抛 `ValueError`;Java 在 `sessionPrompt()` 里显式校验并抛 `IllegalArgumentException`,以保持行为等价。
- 数字类型：Jackson 默认产出 `Integer/Long/Double`;`JsonValues.asIntOrNull` 对应 Python 的 `isinstance(x, int)` 检查。

## 6. 运行时载体（sdk-runtime）移植

- `deepseek_harness_runtime/__init__.py` → `runtime/RuntimeResolver`。
- **载体**：exe（生产单文件 `dsh-jsonrpc-agent-pkg-<platform>-<arch>`）与 node（仅开发）两种,与 Python 完全一致;macOS 需 `-spawn-helper`。
- **"包数据根目录"解析**：Python 是模块目录;Java 顺序为 系统属性 `dsh4j.runtime.dir` / 环境变量 `DSH4J_RUNTIME_DIR` > 元数据 classpath 资源所在目录。jar 内资源在需要按路径交给子进程时（默认 `cordis.yml`）物化到缓存临时文件。
- **模式选择**：显式参数 > `DSH_RUNTIME_MODE`（`exe`|`node`）> 自动(只找 exe)。测试通过 `RuntimeResolver.runtimeModeEnvOverride` 复现 Python 的 `monkeypatch.setenv`。
- **零配置设计保留**：运行时始终要求显式配置;`HarnessClient.start()` 在无显式配置通道时注入内置默认 `cordis.yml` 路径(`DSH_CORDIS_CONFIG`),空串视为未设置。

## 7. JDK 版本差异

- **基线**:**Java 17 LTS**(`maven.compiler.release=17`)。选 17 的理由:
  - Spring Boot 3.x 的最低要求;
  - 团队/企业普遍部署的 LTS;
  - 与 JDK 17 同时代引入的 `record`、模式匹配 `instanceof`、`switch` 表达式等语法在本移植中广泛使用。
- **可选升级**:代码在 Java 21(LTS,含虚拟线程、`SequencedCollection` 等)、Java 25 上亦可编译运行;本仓库在 JDK 25 上验证。
- **需要 JDK 17+ 的语言特性**（本项目使用）:`record`(16+)、模式匹配 `instanceof`(16+)、文本块(15+)、`List.of`/`Map.of`(9+)、`Stream.toList()`(16+)。若须支持 Java 11,需改写为传统 POJO/集合。
- **注意**:
  - `@ValueSource(strings = MODES)` 中的数组字段**不是**编译期常量(JLS 只认 String/primitive 常量变量),注解里必须内联数组。
  - 现代 JDK 中 `Path.of(".")` 解析的是 OS 工作目录,**不随** `user.dir` 系统属性变化;测试用真实相对路径而非改 `user.dir`。
  - `Map.of`/`Set.of` **拒绝 null 值**;表示 JSON `content: null` 需用 `HashMap/LinkedHashMap`。

## 8. 构建与分发（Python → Maven）

| Python | Java |
|---|---|
| `pyproject.toml` + hatchling | `pom.xml` + Maven |
| `uv` / `pip` | Maven（`mvn install`） |
| `pytest` | JUnit 5（surefire） |
| wheel（纯 SDK + 平台运行时 wheel） | Maven artifact（sdk / starter / example） |
| PyPI | 你的 Maven 仓库 |
| `platforms.json`（平台标签/可执行名） | `PlatformManifest` + `sdk/src/main/resources/platforms.json` |
| `check-macos-deployment-target.py` | `build.MacOsDeploymentTarget.ensureCompatible` |
| `build-python-release.py` 版本函数 | `build.ReleaseVersion`（读根 `pom.xml` 版本;`pep440Version` 原样保留） |

## 9. Spring Boot / Spring Cloud / Spring MVC 集成

> Python SDK 无框架集成;Java 移植新增了薄薄的 Spring 层,方便在 Java 技术栈直接使用。

### 9.1 Spring Boot（starter 模块 `deepseek-harness4j-spring-boot-starter`）

- `@AutoConfiguration` + `@ConditionalOnProperty(deepseek.harness.enabled, matchIfMissing=true)`。
- `@ConfigurationProperties(prefix = "deepseek.harness")` → `DeepSeekHarnessProperties`,字段镜像 `DeepSeekHarnessConfig`(`provider`/`model`/`maxTokens`/`cwd`/`sessionRoot`/`cordis`/`runtimeBin`/`requestTimeoutSeconds`/`shutdownTimeoutSeconds`/`baseUrl`/`apiKey`/`env` 等)。
- 提供两个 bean：
  - `DeepSeekHarness`(`@Bean(destroyMethod="close")`,懒启动、上下文关闭时回收子进程);
  - `DeepSeekHarnessTemplate`(Spring 友好包装,暴露 `run(...)` / `run(...)` / `close()`)。

```yaml
deepseek:
  harness:
    provider: deepseek-official
    model: deepseek-v4-flash
    cwd: /workspace
    sessionRoot: /sessions
    requestTimeoutSeconds: 300
```

```java
@Service
public class MyService {
    private final DeepSeekHarnessTemplate harness;
    public MyService(DeepSeekHarnessTemplate harness) { this.harness = harness; }
    public RunResult ask(String task) { return harness.run(task); }
}
```

### 9.2 Spring Cloud

- `deepseek.harness.*` 属性由 **Spring Cloud Config** 配置中心下发时,`@ConfigurationProperties` 直接生效,**无需改任何 Java 代码**（与"自定义模型=配置"的哲学一致）。
- 密钥建议不进配置中心,继续用环境变量(`DEEPSEEK_API_KEY`)或密钥管理服务注入运行时——SDK 默认继承父进程环境。
- 若需多实例/路由,可把 `provider`/`model` 做成运行时上下文变量由下游配置覆盖。

### 9.3 Spring MVC（example 模块 `deepseek-harness4j-spring-boot-example`）

- 可运行示例:`DeepSeekHarness4jExampleApplication` + `HarnessController`。
- `@PostMapping("/api/harness/run")` 接收 `{input, sessionId}` → 调 `template.run(input, sessionId, null)` → 返回 `RunResult`(JSON)。
- 一个 HTTP 请求 = 一个 `Session.run()` 轮次;长任务可改为异步(`@Async`/`WebFlux`)或配合 WebSocket 推送通知(`onNotification` 回调)。

```sh
curl -X POST localhost:8080/api/harness/run \
  -H 'content-type: application/json' \
  -d '{"sessionId":"example-001","input":"Say hi."}'
```

### 9.4 线程模型建议

- `HarnessClient` 内部使用阻塞队列 + 守护线程,对 Spring 容器**线程安全**(`run()` 可并发调用;写消息已串行化)。
- 每个 `DeepSeekHarness` 实例持有**一个**运行时子进程;高并发多轮次建议按需创建多个实例（或引入连接池）,而不是让单实例成为瓶颈。
- 默认 `requestTimeoutSeconds` 未设=无限等待;在服务端请显式设置,避免请求挂死。

## 10. 未移植 / 语义替代清单

| Python 侧 | 处理 |
|---|---|
| `scripts/smoke-python-runtime.py`（970 行冒烟 mock 服务器） | 仅移植其被测行为:`SmokeCompletions`(测试用,`completion_chunks`/`text_chunks`) |
| `scripts/build-python-release.py` wheel 暂存/构建 | Maven 取代;纯函数移植为 `ReleaseVersion` / `PlatformManifest` |
| `scripts/check-macos-deployment-target.py` | 移植为 `MacOsDeploymentTarget`(纯函数) |
| `test_bundled_runtime.py` 真实载体启动 | 原样移植;载体缺失时像 Python 一样独立跳过 |
| `manual_sdk_agent_smoke.py` | `FakeRuntime` 测试覆盖其调用路径 |
| 假运行时脚本(测试内嵌 Python) | 单一 Java `FakeRuntime` + `FR_SCENARIO` 行为选择,由 `TestRuntimes` 启动 |
