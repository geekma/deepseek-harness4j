# Python → Java line-by-line migration notes (deepseek-harness4j)

[中文](java-migration-notes.md) | English

This document records the syntax, runtime, and ecosystem differences encountered while porting the upstream `deepseek-harness` Python SDK (`python/sdk` + `python/sdk-runtime`) **line by line** into Java, and how the Spring Boot / Spring Cloud / Spring MVC integration works. The port's goal is **semantic equivalence**: the same runtime, the same JSON-RPC protocol, the same environment-variable contract — only the client language changes.

## 1. Top-level mapping

| Python | Java | Notes |
|---|---|---|
| `deepseek_harness` package | `com.deepseek.harness4j` | package and public API surface kept identical |
| `DeepSeekHarness` / `Session` / `RunResult` | same-named class / `record` | API names and fields map 1:1 |
| pydantic `BaseModel` | Jackson POJO / `record` | `model_validate()` → `ObjectMapper.convertValue()` |
| `dict[str, JsonValue]` | `Map<String, Object>` | JSON objects are `Map`s |
| `queue.Queue` | `BlockingQueue` (`LinkedBlockingQueue`) | same queue semantics |
| `threading.Lock` | `synchronized` / `ReentrantLock` | same mutual-exclusion semantics (Java's is reentrant) |
| `subprocess.Popen` | `ProcessBuilder` + `Process` | same subprocess/pipe semantics |
| context manager `with` | `try`-with-resources (`AutoCloseable`) | **note**: Java's try-with-resources only calls `close()`, not `start()` like Python's `__enter__`; call `start()` explicitly (§4) |
| dataclass | constructor + builder | keyword args → fluent builder |
| `uuid4().hex` | `UUID.randomUUID().toString().replace("-","")` | 32-hex session ids |

## 2. Syntax differences at a glance

- **Type aliases / union types**: Python's `JsonValue: TypeAlias = str | int | float | bool | None | dict | list` is expressed in Java as `Object` plus typed helpers (`JsonValues`). Python's `isinstance(x, int)` → `JsonValues.isIntegral(x)`.
- **Optional fields**: Python dataclass `field: X | None = None` → Java getters returning `null`; `record` compact constructors enforce non-null (`Notification`, `IncomingRequest`, `RunResult`).
- **Defaults**: Python keyword defaults (e.g. `provider="deepseek-official"`, `shutdown_timeout_seconds=1.0`) → Java builders apply defaults in `build()`.
- **Mutable defaults**: Python `env: dict = field(default_factory=dict)` → Java defensive copy at construction.
- **`pathlib.Path.resolve()`**: Python resolves symlinks; Java `Path.toAbsolutePath().normalize()` does **not**. The ported `DeepSeekHarness.resolve()` uses `toRealPath()` (when it exists) with a fallback, preserving Python's "absolute path" semantics (this is exactly why `/var` → `/private/var` differs on macOS).
- **f-strings** → `String.format` / concatenation / `StringBuilder`.
- **Generators/async**: the Python SDK is synchronous and blocking with no generators; Java is likewise synchronous (thread model in §4).
- **`with` + `except`** → `try`-with-resources with a catch block.

## 3. Exception mapping (Python → Java)

| Python | Java | Notes |
|---|---|---|
| `HarnessError` | `HarnessException` (`RuntimeException`) | base class |
| `TransportClosedError` | `TransportClosedException` | runtime exited / stdout closed |
| `SdkProtocolError` | `SdkProtocolException` | protocol violation (e.g. `turn/end` missing `reason.kind`) |
| `JsonRpcError` | `JsonRpcException` | JSON-RPC error response (with `code` / `message` / `data`) |
| `TimeoutError` | `HarnessTimeoutException` | Java's `TimeoutException` is checked, so a runtime exception was introduced |
| `FileNotFoundError` | `MissingRuntimeException` | Python exceptions are unchecked; Java keeps the API clean with a runtime exception |
| `ValueError` | `IllegalArgumentException` | invalid arguments (unknown runtime mode, missing pydantic required field) |
| `TypeError` | `IllegalArgumentException` | response is not a JSON object |
| `RuntimeError` | `IllegalStateException` / `RuntimeException` | platform validation failures |

## 4. Process / thread / lifecycle differences

- **Subprocess**: `Popen(stdin/stdout/stderr=PIPE, text=True, encoding=utf-8)` → `ProcessBuilder` + `BufferedReader/BufferedWriter(UTF_8)`. `stdin` uses a persistent `BufferedWriter` (equivalent to Python's line-buffered stream).
- **Reading stdout / stderr**: two daemon threads (`dsh-runtime-reader` / `dsh-runtime-stderr`); a rolling 400-line stderr tail feeds timeout/close diagnostics.
- **Request waiting**: Python `queue.Queue(maxsize=1)` + `time.monotonic()` → `LinkedBlockingQueue(1)` + `System.nanoTime()` (both monotonic).
- **Locks**: `self._lock` / `self._write_lock` → `synchronized(lock)` / `synchronized(writeLock)`. Writes are serialized exactly as in Python (covered by the concurrent-writes test).
- **Context managers**: Python `with HarnessClient(...)` **auto-starts**; Java try-with-resources only `close()`s. Every "enter-to-start" call site therefore calls `start()` explicitly (both `HarnessClient.start()` and `DeepSeekHarness.start()` are idempotent).
- **Graceful shutdown**: `terminate()` (SIGTERM) → `process.destroy()`; after the timeout `kill()` → `destroyForcibly()` (SIGKILL). `shutdown_timeout_seconds=null` means wait forever.

## 5. JSON and serialization

- Serialization: `json.dumps(msg, separators=(",",":"))` (compact, no spaces) → Jackson `writeValueAsString` (equally compact by default).
- Deserialization: pydantic `model_validate` → Jackson `convertValue`; configured with `FAIL_ON_UNKNOWN_PROPERTIES=false` (pydantic ignores extra fields by default).
- **Required-field validation**: pydantic raises `ValueError` for the required `_SessionPromptResponse.messageId`; Java validates explicitly in `sessionPrompt()` and throws `IllegalArgumentException` to keep behavior equivalent.
- **Numbers**: Jackson emits `Integer/Long/Double` by default; `JsonValues.asIntOrNull` mirrors Python's `isinstance(x, int)` check.

## 6. Runtime-carrier (sdk-runtime) port

- `deepseek_harness_runtime/__init__.py` → `runtime/RuntimeResolver`.
- **Carriers**: exe (production single-file `dsh-jsonrpc-agent-pkg-<platform>-<arch>`) and node (dev-only), identical to Python; macOS requires `-spawn-helper`.
- **"Package data root" resolution**: Python uses the module directory; Java tries the system property `dsh4j.runtime.dir` / env var `DSH4J_RUNTIME_DIR`, then the directory containing the metadata classpath resource. Resources inside a jar are materialized to a cached temp file when a subprocess needs them by path (the default `cordis.yml`).
- **Mode selection**: explicit argument > `DSH_RUNTIME_MODE` (`exe`|`node`) > automatic (exe only). Tests reproduce Python's `monkeypatch.setenv` via `RuntimeResolver.runtimeModeEnvOverride`.
- **Zero-config design preserved**: the runtime always requires an explicit config; `HarnessClient.start()` injects the bundled default `cordis.yml` path (`DSH_CORDIS_CONFIG`) when no explicit config channel is used, treating an empty value as absent.

## 7. JDK version differences

- **Baseline**: **Java 17 LTS** (`maven.compiler.release=17`). Why 17:
  - Spring Boot 3.x minimum;
  - the LTS most teams and enterprises deploy;
  - the `record`, pattern-matching `instanceof`, and `switch` expressions introduced in the Java 16/17 era are used throughout this port.
- **Optional upgrades**: the code also compiles and runs on Java 21 (LTS; virtual threads, `SequencedCollection`, etc.) and Java 25; this repository was verified on JDK 25.
- **Language features requiring JDK 17+** (used here): `record` (16+), pattern-matching `instanceof` (16+), text blocks (15+), `List.of`/`Map.of` (9+), `Stream.toList()` (16+). To target Java 11 you would have to rewrite these as traditional POJOs/collections.
- **Gotchas**:
  - Array fields like `@ValueSource(strings = MODES)` are **not** compile-time constants (JLS only recognizes String/primitive constant variables), so annotation arrays must be inlined.
  - On modern JDKs, `Path.of(".")` resolves against the OS working directory and does **not** follow the `user.dir` system property; tests use real relative paths rather than mutating `user.dir`.
  - `Map.of`/`Set.of` **reject null values**; representing JSON `content: null` requires `HashMap`/`LinkedHashMap`.

## 8. Build and distribution (Python → Maven)

| Python | Java |
|---|---|
| `pyproject.toml` + hatchling | `pom.xml` + Maven |
| `uv` / `pip` | Maven (`mvn install`) |
| `pytest` | JUnit 5 (surefire) |
| wheels (pure SDK + per-platform runtime wheels) | Maven artifacts (sdk / starter / example) |
| PyPI | your Maven repository |
| `platforms.json` (platform tags / executable names) | `PlatformManifest` + `sdk/src/main/resources/platforms.json` |
| `check-macos-deployment-target.py` | `build.MacOsDeploymentTarget.ensureCompatible` |
| `build-python-release.py` version functions | `build.ReleaseVersion` (reads the root `pom.xml` version; `pep440Version` kept verbatim) |

## 9. Spring Boot / Spring Cloud / Spring MVC integration

> The Python SDK has no framework integration; the Java port adds a thin Spring layer so the SDK is usable directly in the Java stack.

### 9.1 Spring Boot (starter module `deepseek-harness4j-spring-boot-starter`)

- `@AutoConfiguration` + `@ConditionalOnProperty(deepseek.harness.enabled, matchIfMissing=true)`.
- `@ConfigurationProperties(prefix = "deepseek.harness")` → `DeepSeekHarnessProperties`, mirroring `DeepSeekHarnessConfig` (`provider`/`model`/`maxTokens`/`cwd`/`sessionRoot`/`cordis`/`runtimeBin`/`requestTimeoutSeconds`/`shutdownTimeoutSeconds`/`baseUrl`/`apiKey`/`env`, etc.).
- Two beans:
  - `DeepSeekHarness` (`@Bean(destroyMethod="close")`, lazy start, subprocess reaped on context close);
  - `DeepSeekHarnessTemplate` (Spring-friendly wrapper exposing `run(...)` and `close()`).

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

- When `deepseek.harness.*` properties are supplied by a **Spring Cloud Config** server, `@ConfigurationProperties` picks them up directly — **no Java code changes** (consistent with the "custom models = configuration" philosophy).
- Keep secrets out of the config server: continue to inject `DEEPSEEK_API_KEY` via environment or a secret manager — the SDK inherits the parent process environment by default.
- For multi-instance/route scenarios, make `provider`/`model` runtime-context variables overridable by downstream configuration.

### 9.3 Spring MVC (example module `deepseek-harness4j-spring-boot-example`)

- Runnable example: `DeepSeekHarness4jExampleApplication` + `HarnessController`.
- `@PostMapping("/api/harness/run")` accepts `{input, sessionId}` → calls `template.run(input, sessionId, null)` → returns `RunResult` as JSON.
- One HTTP request = one `Session.run()` turn; long tasks can become async (`@Async`/WebFlux) or stream notifications via WebSocket (the `onNotification` callback).

```sh
curl -X POST localhost:8080/api/harness/run \
  -H 'content-type: application/json' \
  -d '{"sessionId":"example-001","input":"Say hi."}'
```

### 9.4 Threading model advice

- `HarnessClient` uses blocking queues and daemon threads internally and is **thread-safe** for the Spring container (`run()` can be called concurrently; writes are already serialized).
- Each `DeepSeekHarness` instance owns **one** runtime subprocess; for high-concurrency multi-turn workloads, create instances on demand (or add a pool) rather than bottlenecking on a single instance.
- `requestTimeoutSeconds` defaults to infinite; in a server set it explicitly to avoid hanging requests.

## 10. Not ported / semantic replacements

| Python side | Handling |
|---|---|
| `scripts/smoke-python-runtime.py` (970-line smoke mock server) | only its tested behavior is ported: `SmokeCompletions` (test scope, `completion_chunks`/`text_chunks`) |
| `scripts/build-python-release.py` wheel staging/build | replaced by Maven; pure functions ported as `ReleaseVersion` / `PlatformManifest` |
| `scripts/check-macos-deployment-target.py` | ported as `MacOsDeploymentTarget` (pure functions) |
| `test_bundled_runtime.py` real-carrier boot | ported as-is; carriers skip independently when absent, exactly like Python |
| `manual_sdk_agent_smoke.py` | its call paths are covered by the `FakeRuntime` tests |
| inline fake-runtime scripts (test-embedded Python) | a single Java `FakeRuntime` with `FR_SCENARIO`-selected behavior, launched by `TestRuntimes` |
