# DeepSeek Harness Java SDK

[English](README.en.md) | 中文

通过 JSON-RPC stdio 驱动 DeepSeek Harness 的 Java 子进程 SDK（`deepseek-harness4j-sdk`）。这是上游 `deepseek-harness` 仓库中 Python SDK（`python/sdk`，模块名 `deepseek_harness`）的逐行 Java 移植。运行时继承常规的 DeepSeek Harness 环境变量（如 `DEEPSEEK_BASE_URL` 与 `DEEPSEEK_API_KEY`），调用方可以直接使用真实模型端点，也可以把这些变量指向本地代理。

## 安装

从 Maven 中央仓库引入 `deepseek-harness4j-sdk` 依赖（Java 17+）：

```xml
<dependency>
  <groupId>com.deepseek-ai</groupId>
  <artifactId>deepseek-harness4j-sdk</artifactId>
  <version>0.0.1-SNAPSHOT</version>
</dependency>
```

或 Gradle：

```groovy
implementation 'com.deepseek-ai:deepseek-harness4j-sdk:0.0.1-SNAPSHOT'
```

使用前请先为运行时安装一个可用载体（见 [sdk-runtime README](../sdk-runtime/README.md)）：生产环境的单文件 `dsh-jsonrpc-agent` 可执行程序，或开发模式的 `node` 闭包；也可以通过 `HarnessConfig.runtimeBin` / `launchArgsOverride` 显式指定通道。常规入口因此不需要传可执行文件参数：

```java
import com.deepseek.harness4j.DeepSeekHarness;
import com.deepseek.harness4j.RunResult;

try (DeepSeekHarness harness = new DeepSeekHarness()) {
    RunResult result = harness.run("Say hi.");
    System.out.println(result.finalResponse());
}
```

`DeepSeekHarness` 会保留其按需启动的运行时子进程，以便在多次调用之间复用。请像上例一样使用 `try`-with-resources（对应 Python 的上下文管理器），或在使用完毕后显式调用 `close()`，这样子进程总会被回收。

默认情况下，SDK 会启动内置的单文件可执行程序 `dsh-jsonrpc-agent`，并通过 `DSH_CORDIS_CONFIG` 注入默认配置，其中包括 stdio JSON-RPC 服务器、agent core（智能体核心）、预载的 DeepSeek 适配器、采用显式组合语义检查点策略的 JSONL 会话持久化，以及本地 bash。要运行自己的插件组合，请在配置中保留 `@deepseek-ai/dsh-sdk-jsonrpc-server` 配置项，并传入 Cordis 配置文件路径。

```java
import com.deepseek.harness4j.DeepSeekHarness;
import com.deepseek.harness4j.DeepSeekHarnessConfig;
import com.deepseek.harness4j.RunResult;

try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
        .provider("deepseek-official")
        .model("deepseek-v4-flash")
        .maxTokens(49_152)
        .cordis("examples/jsonrpc-agent/cordis.yml")
        .build())) {
    RunResult result = harness.run("Make the requested code change.");
}
```

`provider` 选择指定 Cordis 组合所注册的提供方路由；`model` 是该适配器解析出的模型 ID。`max_tokens` 是一个可选的正整数，用于限制根 agent 及其进程内后代在每次请求中输出的 token 数量；省略该参数时，由提供方的默认行为决定输出上限。压缩摘要继续使用压缩插件单独配置的上限。内置默认组合注册 `deepseek-official`。自定义组合可以挂载 `llm-pi-ai`，在其中配置各提供方专属的凭据和端点，并选择 pi-ai 已安装 catalog 中存在的任意提供方/模型组合。

`Session.run()` 的活动区间从其提示词被持久 inbox 接收时开始，到整个 agent 下一次进入空闲状态时结束，并返回 `RunResult(sessionId, finalResponse, finishReason, events, notifications, sessionRoot)`。`finalResponse` 是该区间内根会话最后提交的助手文本。`finishReason` 是该区间内根会话最后一个 `turn/end` 的 `kind`，例如 `completed`、`max-tokens` 或 `error`；没有轮次结束时为 `null`。缺少字符串 `data.reason.kind` 的 `turn/end` 违反运行时协议，并会抛出 `SdkProtocolException`。这两个结果字段描述的是 `Session.run()` 所界定的活动区间，并不表示某项输出或结束原因在因果上归属于该提示词。steering（中途引导）、注入的上下文和其他排队工作，也可能在 agent 进入空闲状态前参与这段活动。

`HarnessClient` 会在运行时进程的整个生命周期内保留已发现的 subagent 谱系。每次执行 `Session.run()` 时，`RunResult.notifications` 与 `onNotification` 回调会按协议传输顺序收到根会话及所有已知后代的通知，其中包括嵌套 subagent 的生命周期事件与会话事件。`RunResult.events` 只包含根会话事件，因此后代消息不会覆盖根会话回复。底层 `sessionPrompt()` 会立即返回已排队消息的 `messageId`；绕过 `Session.run()` 的调用方必须自行负责后续的活动边界。

也可以通过 `DSH_CORDIS_CONFIG` 为运行时子进程指定配置。注入逻辑位于 `HarnessClient.start()`，因此底层客户端按默认方式启动时也具有该行为：如果启动方式最终解析为内置运行时，且既没有设置 `cordis`，也没有设置非空的 `DSH_CORDIS_CONFIG`（运行时将空值视为未设置，注入检查也是如此），系统就会使用内置默认配置；显式指定 `runtimeBin`、`bridgeBin` 或 `launchArgsOverride` 时，则会完全禁用该注入。运行时载体及其获取方式见 [sdk-runtime README](../sdk-runtime/README.md)。

`cwd` 与 `runtimeCwd` 会在启动子进程、注入环境变量和协议握手前解析为绝对路径（与 Python 的 `Path.resolve()` 一致，会解析符号链接）。公开 API 只暴露由 SDK 直接应用的选项：部署 persona 和持久化配置应在 `cordis.yml` 中定义；`sessionRoot` 则保留为设置 `DSH_SESSION_ROOT` 的高层便捷参数。
