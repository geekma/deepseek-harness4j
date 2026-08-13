# deepseek-harness4j port coverage checklist

[中文](port-coverage.md) | English

This file is the **global checklist**: every `.py` file and every `.md` file in the upstream
`deepseek-harness` (plus related non-code files) is cross-checked against its Java counterpart
in `deepseek-harness4j`, proving **nothing is omitted**. Method: enumerate all `.py` / `.md`
files upstream with `find`, then verify each against this repository's files; at the
function level, cross-check every Python class/function against its Java method.

Conclusion: **all 19 `.py` files and all 8 `.md` files inside `python/` (the Python project
itself) have Java counterparts**; the referenced repository scripts (`scripts/*.py`) and
`examples/jsonrpc-agent/minimal.py` are ported too. Exceptions (e.g. `uv.lock`, `*.i18n.yaml`,
`sdk-runtime/package.json`) are metadata/build-manifest files, marked "no Java equivalent /
replaced by Maven" with a reason.

## 1. Baseline (full upstream enumeration)

```sh
# .py inside upstream python/
python/sdk/src/deepseek_harness/{__init__,api,client,models,errors}.py
python/sdk/tests/{test_client,test_runtime_resolution,test_bundled_runtime,
                 test_smoke_model,test_release_version,test_macos_deployment_target,
                 manual_sdk_agent_smoke}.py
python/sdk-runtime/hatch_build.py
python/sdk-runtime/src/deepseek_harness_runtime/__init__.py

# .md inside upstream python/ (8 files)
python/README.md  python/README.zh.md
python/development.md  python/development.zh.md
python/sdk/README.md  python/sdk/README.zh.md
python/sdk-runtime/README.md  python/sdk-runtime/README.zh.md

# referenced / companion Python (ported here too)
scripts/build-python-release.py
scripts/check-macos-deployment-target.py
scripts/smoke-python-runtime.py
examples/jsonrpc-agent/minimal.py
```

## 2. Python code files → Java code files

| # | Python file | Java counterpart | Status |
|---|---|---|---|
| 1 | `sdk/src/deepseek_harness/__init__.py` | `com.deepseek.harness4j` package + `package-info.java` (public API surface matches `__all__`) | ✅ complete |
| 2 | `sdk/src/deepseek_harness/api.py` | `DeepSeekHarness.java` `DeepSeekHarnessConfig.java` `Session.java` `RunResult.java` `SessionSupport.java` | ✅ complete |
| 3 | `sdk/src/deepseek_harness/client.py` | `client/HarnessClient.java` `client/HarnessConfig.java` `client/NotificationSubscription.java` `client/NotificationFilter.java` | ✅ complete |
| 4 | `sdk/src/deepseek_harness/models.py` | `model/Notification.java` `IncomingRequest.java` `ServerInfo.java` `InitializeResponse.java` `JsonValues.java` | ✅ complete |
| 5 | `sdk/src/deepseek_harness/errors.py` | `error/HarnessException.java` `TransportClosedException.java` `SdkProtocolException.java` `JsonRpcException.java` `HarnessTimeoutException.java` `MissingRuntimeException.java` | ✅ complete (exception mapping added; migration notes §3) |
| 6 | `sdk/tests/test_client.py` | `client/ClientLevelTest.java` `client/SubscriptionRoutingTest.java` `HighLevelApiTest.java` | ✅ complete (all 27 cases) |
| 7 | `sdk/tests/test_runtime_resolution.py` | `runtime/RuntimeResolverTest.java` | ✅ complete |
| 8 | `sdk/tests/test_bundled_runtime.py` | `BundledRuntimeBootTest.java` (carriers skip independently when absent, like Python) | ✅ complete |
| 9 | `sdk/tests/test_smoke_model.py` | `build/SmokeCompletionsTest.java` | ✅ complete |
| 10 | `sdk/tests/test_release_version.py` | `build/ReleaseVersionTest.java` | ✅ complete |
| 11 | `sdk/tests/test_macos_deployment_target.py` | `build/MacOsDeploymentTargetTest.java` | ✅ complete |
| 12 | `sdk/tests/manual_sdk_agent_smoke.py` | `examples/ManualSdkAgentSmoke.java` | ✅ complete |
| 13 | `sdk-runtime/hatch_build.py` | `build/RuntimeBuildHook.java` + `build/PlatformManifest.java` + `build/RuntimeBuildHookTest.java` | ✅ complete |
| 14 | `sdk-runtime/src/deepseek_harness_runtime/__init__.py` | `runtime/RuntimeResolver.java` | ✅ complete |
| 15 | `scripts/build-python-release.py` | `build/ReleaseVersion.java` `build/PlatformManifest.java` | ⚠️ partial: pure functions ported; wheel staging/build replaced by Maven (migration notes §8) |
| 16 | `scripts/check-macos-deployment-target.py` | `build/MacOsDeploymentTarget.java` | ✅ complete |
| 17 | `scripts/smoke-python-runtime.py` | `test/SmokeCompletions.java` (test scope) | ⚠️ partial: only the tested behavior is ported (`completion_chunks`/`text_chunks`/`message_text` + two snapshot prompts); the 970-line smoke server itself has no Java equivalent (an upstream demo script) |
| 18 | `examples/jsonrpc-agent/minimal.py` | `examples/MinimalAgent.java` + resource `examples/jsonrpc-agent/minimal.cordis.yml` | ✅ complete |
| 19 | (test-embedded fake-runtime scripts) | `test/FakeRuntime.java` (`FR_SCENARIO`-selected behavior) + `test/TestRuntimes.java` | ✅ complete |

## 3. Documentation files → Java documentation

| # | Python/upstream .md | Java counterpart | Status |
|---|---|---|---|
| 1 | `python/README.md` / `README.zh.md` | root `README.md` (English, main) / `README.zh.md` (Chinese) | ✅ complete (port mapping, Spring, doc index) |
| 2 | `python/development.md` / `development.zh.md` | `development.md` (Chinese) / `development.en.md` (English) | ✅ complete |
| 3 | `python/sdk/README.md` / `README.zh.md` | `sdk/README.md` / `sdk/README.en.md` | ✅ complete |
| 4 | `python/sdk-runtime/README.md` / `README.zh.md` | `sdk-runtime/README.md` / `sdk-runtime/README.en.md` | ✅ complete |
| 5 | root `deepseek-harness-使用指南.md` | `deepseek-harness4j-使用指南.md` (Java edition) | ✅ complete (sections 一–十七 all preserved) |
| 6 | `examples/jsonrpc-agent/README.md` / `README.zh.md` | `examples/jsonrpc-agent/README.md` / `README.en.md` | ✅ complete |
| 7 | `docs/user/guide/python-sdk.md` (official SDK tutorial) | §4 API reference here + `sdk/README.md` (tutorial content folded in) | ✅ complete (key points folded in) |
| 8 | migration/audit additions | `docs/java-migration-notes.md` / `.en.md`, `docs/port-coverage.md` / `.en.md`, `docs/python-sdk-api-reference.md` / `.en.md` | ✅ added |

## 4. Non-code files → Java counterparts

| # | Upstream file | Java counterpart | Status |
|---|---|---|---|
| 1 | `sdk/pyproject.toml` | `sdk/pom.xml` | ✅ replaced |
| 2 | `sdk-runtime/pyproject.toml` | runtime-carrier docs `sdk-runtime/README.md` (wheel-only semantics preserved in `RuntimeBuildHook`) | ✅ replaced |
| 3 | `sdk/uv.lock` | none — Maven has no lockfile; use `dependency:go-offline` / `-o` for reproducible builds | 📄 N/A (explained) |
| 4 | `sdk/README.i18n.yaml` | none — doc-sync blob-hash consistency record; Java bilingual pairs are maintained manually | 📄 N/A (explained) |
| 5 | `python/README.i18n.yaml`, `development.i18n.yaml` | same as above | 📄 N/A (explained) |
| 6 | `sdk-runtime/README.i18n.yaml` | same as above | 📄 N/A (explained) |
| 7 | `sdk-runtime/package.json` | none — upstream Node deploy root (dependency-closure manifest) consumed by upstream `build-exe-for-python-sdk.ts`; the Java SDK consumes its build products | 📄 N/A (explained) |
| 8 | `sdk-runtime/platforms.json` | `sdk/src/main/resources/platforms.json` + `build/PlatformManifest` | ✅ complete |
| 9 | `sdk-runtime/src/.../deepseek-harness-runtime.json` | `sdk/src/main/resources/deepseek-harness-runtime.json` | ✅ complete |
| 10 | `sdk-runtime/src/.../runtime/cordis.yml` | `sdk/src/main/resources/runtime/cordis.yml` | ✅ complete |
| 11 | `examples/jsonrpc-agent/minimal.cordis.yml` | `sdk/src/main/resources/examples/jsonrpc-agent/minimal.cordis.yml` | ✅ complete |

## 5. Out of scope (not the Python project, not ported)

| File | Reason |
|---|---|
| `.agents/skills/record-browser-gif/scripts/encode_gif.py` | upstream agent-skill script, not part of the Python SDK project |
| `scripts/build-exe-for-python-sdk.ts` | TypeScript build script, part of the upstream Node project (the Python side only consumes its output) |
| `vendor/`, `packages/` etc. Node/TS sources | not Python; 4j is a client port, the runtime is still provided upstream |

## 6. Function-level audit (Python function/method → Java)

### 6.1 `client.py` (`HarnessClient` / `HarnessConfig` / `NotificationSubscription`)

| Python | Java | ✅ |
|---|---|---|
| `HarnessConfig` (7 fields) | `client/HarnessConfig` (builder) | ✅ |
| `start()` | `HarnessClient.start()` | ✅ |
| `close()` | `HarnessClient.close()` (idempotent) | ✅ |
| `initialize(cwd, provider, model, max_tokens)` | `HarnessClient.initialize(...)` | ✅ |
| `session_prompt(session_id, content_blocks, ...)` | `HarnessClient.sessionPrompt(...)` | ✅ |
| `request(method, params, response_model, ...)` | `HarnessClient.request(...)` (generic) | ✅ |
| `notify(method, params)` | `HarnessClient.notify(...)` | ✅ |
| `next_notification()` | `HarnessClient.nextNotification()` | ✅ |
| `subscribe_notifications(filter)` | `HarnessClient.subscribeNotifications(...)` | ✅ |
| `subscribe_session_notifications(session_id)` | `HarnessClient.subscribeSessionNotifications(...)` | ✅ |
| `next_request()` / `respond()` / `respond_error()` | same-named methods | ✅ |
| `_request_raw` / `_write_message` / `_reader_loop` / `_stderr_loop` / `_handle_message` | `requestRaw` / `writeMessage` / `readerLoop` / `stderrLoop` / `handleMessage` | ✅ |
| `_fail_waiters` / `_runtime_diagnostics` / `_runtime_closed_error` | same-named private methods | ✅ |
| `_default_launch_args` / `_inject_bundled_default_config` | same-named private methods | ✅ |
| `_record_session_relationship_locked` / `_notification_belongs_to_session_tree` / `_session_is_descendant_of` | same-named private methods (subagent ancestry) | ✅ |
| `NotificationSubscription.close/next/drain` | same-named methods | ✅ |
| `_SessionPromptResponse` / `_ShutdownResponse` / `_int_or_none` | `SessionPromptResponse` / `ShutdownResponse` / `JsonValues.asIntOrNull` | ✅ |

### 6.2 `api.py`

| Python | Java | ✅ |
|---|---|---|
| `DeepSeekHarnessConfig` (14 fields) | `DeepSeekHarnessConfig` (builder) | ✅ |
| `RunResult(session_id, final_response, finish_reason, events, notifications, session_root)` | `RunResult` (record, 6 same-named fields) | ✅ |
| `DeepSeekHarness.__init__ / client property / start / close / start_session / run` | same-named methods | ✅ |
| `Session.run(input, on_notification)` | `Session.run(input, onNotification)` | ✅ |
| `_is_inbox_receipt` / `normalize_input` / `final_response` / `finish_reason` | `SessionSupport.isInboxReceipt / normalizeInput / finalResponse / finishReason` | ✅ |

### 6.3 `models.py` / `errors.py` / `__init__.py`

| Python | Java | ✅ |
|---|---|---|
| `Notification` / `IncomingRequest` | `record Notification` / `record IncomingRequest` | ✅ |
| `ServerInfo` / `InitializeResponse` (pydantic) | Jackson POJOs | ✅ |
| `JsonScalar/JsonValue/JsonObject` (TypeAlias) | `model/JsonValues` (`Object` + type checks) | ✅ |
| `HarnessError` and its 4 subclasses | `error/*` (6 exception classes, 2 added by the mapping) | ✅ |
| `__all__` export list | `package-info.java` public API surface | ✅ |

### 6.4 `sdk-runtime/__init__.py` / `hatch_build.py`

| Python | Java | ✅ |
|---|---|---|
| `PACKAGE_METADATA_FILENAME` / `RUNTIME_MODE_ENV_VAR` | `RuntimeResolver` constants | ✅ |
| `bundled_package_dir()` / `bundled_default_config_path()` / `bundled_runtime_path()` | `RuntimeResolver` same-named methods | ✅ |
| `resolve_bundled_launch_args(mode)` | `RuntimeResolver.resolveBundledLaunchArgs(mode)` | ✅ |
| `_current_platform_tag()` / `_node_launch_args()` | `RuntimeResolver` private methods | ✅ |
| `_load_platforms()` / `_host_platform_tag()` / `RuntimeBuildHook.initialize()` | `PlatformManifest.loadPlatforms/hostPlatformTag/expectedRuntimeFiles` + `RuntimeBuildHook.*` | ✅ |

## 7. Verification

```sh
cd deepseek-harness4j
mvn clean install     # full reactor (sdk / spring-boot-starter / spring-boot-example) build + tests
mvn -pl sdk test      # sdk unit tests (including the new RuntimeBuildHookTest)
```

- Test tally: 7 upstream test files → 10 Java test classes; real-carrier boot cases skip
  independently when no carrier is present, per Python semantics.
- The Spring MVC example has been verified to boot Tomcat (see root README).

## 8. Public reference and "100% feature" entry points

For any project (in any language) to align with 100% of the Python SDK's capabilities, use:

1. **[python-sdk-api-reference.md](python-sdk-api-reference.md)** — SDK public API, JSON-RPC
   wire protocol (methods/notifications/events/result semantics), environment variables,
   lifecycle, and how the web/cli/sdk/acp channels relate; item by item, usable as an
   implementation spec.
2. **[java-migration-notes.md](java-migration-notes.md)** — Python→Java differences and
   substitute semantics.
3. `deepseek-harness4j-使用指南.md`, `sdk/README.en.md`, `sdk-runtime/README.en.md` — usage and
   runtime-carrier documentation.
