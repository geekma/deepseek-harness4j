# deepseek-harness4j

<p align="center">
  <strong>面向 DeepSeek Harness 的企业级 Java SDK 与 Agent 运行时治理中间件</strong>
</p>

<p align="center">
  <a href="https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html"><img src="https://img.shields.io/badge/Java-17%2B-blue?logo=openjdk" alt="Java 17+" /></a>
  <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring%20Boot-3.x-green?logo=springboot" alt="Spring Boot 3.x" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License: MIT" /></a>
  <a href="https://deepseek.com"><img src="https://img.shields.io/badge/DeepSeek-R1%20%2F%20V3-blueviolet" alt="DeepSeek R1/V3" /></a>
  <a href="https://github.com/geekma/deepseek-harness4j/actions"><img src="https://img.shields.io/badge/Tests-116%2F116%20Passing-brightgreen" alt="Tests 116/116" /></a>
</p>

<p align="center">
  <a href="#-核心特性">核心特性</a> •
  <a href="#-快速上手">快速上手</a> •
  <a href="#-核心能力与高阶范式">核心能力</a> •
  <a href="#-架构全景">架构全景</a> •
  <a href="#-spring-boot-集成">Spring Boot</a> •
  <a href="#-文档导航">文档导航</a>
</p>

---

[English](README.md) | [中文说明](README.zh.md)

**deepseek-harness4j** 是对标官方 [DeepSeek Harness (`dsh`)](https://github.com/deepseek-ai/deepseek-harness) 的企业级 Java SDK 与 Agent 运行时治理框架。它通过基于 stdio 的换行分隔 JSON-RPC 2.0 协议，以亚毫秒级进程 IPC 延迟无缝驱动原生 Agent 内核。

为 Java 17+ 和 Spring Boot 3+ 生态提供 DeepSeek-R1 / V3 思维链（CoT）推理提取、纯 Java 离线 Zstd 轨迹分析引擎、Reactive 响应式 Token 流式推流、Java 本地工具桥接（`@HarnessTool`）以及工业级 OpenTelemetry / Langfuse 可观测性导出。

---

## 🌟 核心特性

- ⚡ **零运行时损耗的 Agent 驱动中间件**: 通过高效的 stdio JSON-RPC 2.0 异步通道，直接操控随包原生二进制守护进程（`dsh-jsonrpc-agent`）。
- 🧠 **DeepSeek-R1 CoT 思维链透明化**: 自动提取并拼接推理过程（`reasoningContent()`）、细分计量 Token 消耗（`TokenUsage`）及结构化工具调用树（`ToolCallRecord`）。
- 🌊 **响应式与非阻塞流式推流**: 原生提供 Java 9+ `Flow.Publisher<StreamChunk>`（零依赖直连 Spring WebFlux `Flux` 与 SSE）及基于 `CompletableFuture<RunResult>` 的 `runAsync` / `resumeAsync`。
- 💾 **纯 Java 离线 SessionLog 引擎 (Zstd + JSONL)**: 免启动 Agent 进程即可实现日志秒级解析、跨会话全局检索（`searchAll`）、确定性事件重放及血统保留的会话分支派生（`fork`）。
- 🛡️ **类型安全 Cordis DSL 与 Minimal Benchmark 工厂**: 提供强类型 `CordisConfig` 与 `SandboxPolicy` 构造器，支持一行代码创建专用于 SWE-bench 的 `createMinimal()` 基准评测 Agent。
- 🔌 **Java 本地 Tool 扩展注册**: 无需编写 TypeScript 插件，使用 `@HarnessTool` 与 `@Param` 即可将 Spring Bean 或任意 Java 方法秒级注册为 Agent 工具。
- 📊 **工业级企业可观测性**: 原生内置 OpenTelemetry GenAI 语义约定与 Langfuse APM 结构导出器。
- 🍃 **开箱即用的 Spring Boot 3 Starter**: 自动装配、属性绑定与高阶 `DeepSeekHarnessTemplate` 门面代理。

---

## 📦 依赖引入

### Maven

在 `pom.xml` 中引入核心 SDK：

```xml
<dependency>
    <groupId>com.deepseek-ai</groupId>
    <artifactId>deepseek-harness4j-sdk</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

或在 Spring Boot 3 项目中引入 Starter：

```xml
<dependency>
    <groupId>com.deepseek-ai</groupId>
    <artifactId>deepseek-harness4j-spring-boot-starter</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

---

## 🚀 快速上手

### 1. 基础 Agent 调用与思维链提取

```java
import com.deepseek.harness4j.DeepSeekHarness;
import com.deepseek.harness4j.DeepSeekHarnessConfig;
import com.deepseek.harness4j.RunResult;

public class QuickStart {
    public static void main(String[] args) throws Exception {
        DeepSeekHarnessConfig config = DeepSeekHarnessConfig.builder()
                .provider("deepseek-official")
                .model("deepseek-reasoner") // DeepSeek-R1 推理模型
                .cwd("/path/to/workspace")
                .build();

        try (DeepSeekHarness harness = new DeepSeekHarness(config)) {
            RunResult result = harness.run("分析当前项目结构并总结主要模块职责。");
            
            // 提取 DeepSeek-R1 思维链 (CoT Reasoning)
            System.out.println("=== 思考过程 (CoT) ===");
            System.out.println(result.reasoningContent());

            // 提取最终回答
            System.out.println("=== 最终回答 ===");
            System.out.println(result.finalResponse());

            // 细分 Token 统计
            System.out.println("Prompt Tokens: " + result.tokenUsage().promptTokens());
            System.out.println("Reasoning Tokens: " + result.tokenUsage().reasoningTokens());
        }
    }
}
```

### 2. 响应式流式推流 (SSE / WebFlux)

```java
// 响应式流 (Java 9 Flow.Publisher，可无缝转为 Reactor Flux)
Flow.Publisher<StreamChunk> publisher = harness.stream("重构用户认证模块");

// 回调式实时推流
harness.stream("解释量子计算基本原理", chunk -> {
    switch (chunk.type()) {
        case "reasoning" -> System.out.print("[思考中] " + chunk.text());
        case "content"   -> System.out.print(chunk.text());
        case "tool_call" -> System.out.println("\n[工具调用] " + chunk.name());
    }
});
```

### 3. 一行代码创建 Benchmark 评测 Agent (SWE-bench)

```java
// 一行代码创建仅包含 Bash 和编辑器的极简评测 Harness
try (DeepSeekHarness harness = DeepSeekHarness.createMinimal("/workspace/swe-bench-task")) {
    RunResult result = harness.run("修复 issue #1024 描述的代码缺陷");
    System.out.println("执行状态: " + result.finishReason());
}
```

---

## 🛠️ 核心能力与高阶范式

### 1. Java 本地工具桥接 (`@HarnessTool`)

无需编写 TypeScript 插件，直接将 Spring Bean 或 Java 本地方法暴露给大模型：

```java
public class DatabaseTools {

    @HarnessTool(name = "query_user", description = "根据用户 ID 查询数据库中的用户信息")
    public String queryUser(@Param(name = "userId", description = "目标用户 ID") Long userId) {
        return "{\"id\":" + userId + ",\"name\":\"Alice\",\"role\":\"Admin\"}";
    }
}

// 注册工具并自动生成标准 JSON Schema
ToolRegistry registry = new ToolRegistry();
registry.register(new DatabaseTools());
```

### 2. 纯 Java 离线 SessionLog 引擎 (Zstd 逐帧流式解析)

零运行时依赖，秒级检索与回放历史智能体轨迹：

```java
Path sessionRoot = Path.of("/path/to/.sessions");

// 1. 扫描全部历史会话 (O(1) 内存，仅读首帧 Header)
List<SessionLog.Header> sessions = SessionLog.list(sessionRoot);

// 2. 跨会话全局全文检索
List<SessionLog.SearchHit> hits = SessionLog.searchAll(sessionRoot, "NullPointerException");

// 3. 保留血统的会话分支派生 (Fork)
SessionLog.Header forked = SessionLog.fork(sessionRoot, "session-v1", "session-v1-branch");

// 4. 对派生的新分支直接进行实时续跑
RunResult resumed = harness.resume("采用备选方案继续修复", "session-v1-branch");
```

### 3. 类型安全 Cordis DSL

使用强类型 Builder 动态组装 Cordis 配置，替代手写容易出错的 YAML：

```java
CordisConfig config = CordisConfig.builder()
        .provider(LlmProvider.DEEPSEEK_OFFICIAL)
        .model("deepseek-reasoner")
        .sandboxPolicy(SandboxPolicy.builder()
                .allowNetwork(false)
                .readOnlyRoot(true)
                .allowedCommands(List.of("git", "mvn", "pytest"))
                .build())
        .compression(CompressionMode.ZSTD)
        .build();

Path cordisYaml = config.toTempFile();
```

### 4. 工业级可观测性链路导出 (OpenTelemetry & Langfuse)

```java
RunResult result = harness.run("执行安全漏洞扫描");

// 导出为 OpenTelemetry GenAI Semantic Conventions 属性
Map<String, Object> spanAttributes = OtelTraceExporter.exportGenAiSpan(result, "deepseek-reasoner");

// 导出为 Langfuse 兼容 APM Trace 结构
Map<String, Object> langfuseTrace = LangfuseExporter.exportTrace(result, "security-audit-trace");
```

---

## 🍃 Spring Boot 集成

在 Spring Boot 3.x 项目中添加 `deepseek-harness4j-spring-boot-starter`：

### `application.yml`

```yaml
deepseek:
  harness:
    provider: deepseek-official
    model: deepseek-reasoner
    api-key: ${DEEPSEEK_API_KEY}
    cwd: ${user.dir}
    session-root: ${user.home}/.dsh/sessions
```

### SSE 实时流式控制器

```java
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    @Autowired
    private DeepSeekHarnessTemplate harnessTemplate;

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<StreamChunk>> chatStream(@RequestParam String prompt) {
        return Flux.from(harnessTemplate.stream(prompt))
                .map(chunk -> ServerSentEvent.<StreamChunk>builder()
                        .event(chunk.type())
                        .data(chunk)
                        .build());
    }

    @PostMapping("/chat/async")
    public CompletableFuture<RunResult> chatAsync(@RequestBody String prompt) {
        return harnessTemplate.runAsync(prompt);
    }
}
```

---

## 🏛️ 架构全景

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       Java SDK (deepseek-harness4j)                          │
│                                                                             │
│  [Java 客户端与治理层]                                                       │
│  ├── 子进程生命周期守护 (ProcessBuilder + 环形滚动 Stderr 缓冲区)             │
│  ├── stdio JSON-RPC 2.0 异步帧编解码 (Jackson 紧凑 UTF-8)                    │
│  ├── 单调时钟请求响应匹配 (UUID -> BlockingQueue)                            │
│  ├── 树状通知路由系统 (Session 树与 Subagent 父子追踪)                       │
│  ├── Reactive 响应式流 (Flow.Publisher<StreamChunk> / SSE)                   │
│  ├── 增强型 RunResult (DeepSeek-R1 CoT 思维链提取, 细分 Token 计量)          │
│  ├── 纯 Java 离线 SessionLog 引擎 (Zstd 逐帧流式解压, Fork 分支, 检索)       │
│  ├── Java 本地 Tool 扩展注册中心 (@HarnessTool / 反射执行)                   │
│  ├── 类型安全 Cordis DSL (CordisConfig, SandboxPolicy, createMinimal)        │
│  └── 企业级生态集成 (Spring Boot 3, OpenTelemetry, Langfuse)                 │
│                                                                             │
│                     │ stdio (换行符分隔 JSON-RPC 2.0)                        │
│                     ▼                                                       │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │  随包原生二进制守护进程 (dsh-jsonrpc-agent)                            │  │
│  │                                                                       │  │
│  │  Cordis 插件容器内核 (TypeScript / Fiber 生命周期 / 依赖注入)           │  │
│  │  ├── agent-spine (LLM 推理循环, 上下文装配, Token 消耗计量)             │  │
│  │  ├── llm-deepseek / llm-pi-ai (官方直连与多提供方适配器)                │  │
│  │  ├── persistent-bash / str-replace-editor / tool-fs (沙箱工具执行)      │  │
│  │  ├── session-persistence-jsonl (只增 Zstd/JSONL 磁盘持久化)             │  │
│  │  └── subagent / compaction (子智能体调度与上下文窗口滑动压缩)           │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 📚 文档导航

- 📖 **[DeepSeek Harness 使用指南 (中文)](deepseek-harness4j-使用指南.md)**: 完整架构剖析、自定义模型接入与生产级配置深度指南。
- 📖 **[DeepSeek Harness User Guide (English)](deepseek-harness4j-user-guide.en.md)**: Exhaustive runtime architecture and custom endpoint guide.
- 📊 **[五大核心特性 Review 与全景方案报告](docs/review-five-features.md)**: 逐行比对 Python SDK 与 4j 原生增强落地的深度评估报告。
- 🛠️ **[开发与构建指南](development.md)**: 源码构建、Native 二进制打包接入与测试执行。

---

## 📄 开源协议

本项目采用 [MIT 许可证](LICENSE)。