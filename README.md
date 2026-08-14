# deepseek-harness4j

<p align="center">
  <strong>Enterprise-Grade Java SDK & Agent Runtime Middleware for DeepSeek Harness</strong>
</p>

<p align="center">
  <a href="https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html"><img src="https://img.shields.io/badge/Java-17%2B-blue?logo=openjdk" alt="Java 17+" /></a>
  <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring%20Boot-3.x-green?logo=springboot" alt="Spring Boot 3.x" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License: MIT" /></a>
  <a href="https://deepseek.com"><img src="https://img.shields.io/badge/DeepSeek-R1%20%2F%20V3-blueviolet" alt="DeepSeek R1/V3" /></a>
  <a href="https://github.com/geekma/deepseek-harness4j/actions"><img src="https://img.shields.io/badge/Tests-116%2F116%20Passing-brightgreen" alt="Tests 116/116" /></a>
</p>

<p align="center">
  <a href="#key-features">Key Features</a> •
  <a href="#quick-start">Quick Start</a> •
  <a href="#core-capabilities">Core Capabilities</a> •
  <a href="#architecture">Architecture</a> •
  <a href="#spring-boot-integration">Spring Boot</a> •
  <a href="#documentation">Documentation</a>
</p>

---

[English](README.md) | [中文说明](README.zh.md)

**deepseek-harness4j** is the official-aligned, enterprise-grade Java SDK and runtime harness for [DeepSeek Harness (`dsh`)](https://github.com/deepseek-ai/deepseek-harness) from DeepSeek AI. It drives the native agent loop via high-performance, newline-delimited JSON-RPC 2.0 over stdio with sub-millisecond process IPC latency.

It brings first-class Java 17+ and Spring Boot 3+ support to DeepSeek-R1 / V3 reasoning models, offline Zstd execution log analytics, reactive real-time token streaming, Java native tool bridging (`@HarnessTool`), and industrial OpenTelemetry / Langfuse observability.

---

## 🌟 Key Features

- ⚡ **Zero-Overhead Agent Runtime Client**: Talks newline-delimited JSON-RPC 2.0 over stdio with sub-millisecond overhead to the native agent daemon (`dsh-jsonrpc-agent`).
- 🧠 **DeepSeek-R1 CoT & Token Transparency**: Native extraction of Chain-of-Thought reasoning (`reasoningContent()`), granular Token metrics (`TokenUsage`), and structured Tool Call execution trees.
- 🌊 **Reactive & Non-Blocking Streaming**: Full support for Java 9+ `Flow.Publisher<StreamChunk>` (ready for Spring WebFlux `Flux` & SSE) and `CompletableFuture<RunResult>` (`runAsync` / `resumeAsync`).
- 💾 **Offline SessionLog Engine (Zstd + JSONL)**: Pure Java offline log reader, multi-session full-text search (`searchAll`), deterministic event replay, and lineage-preserving session branching (`fork`).
- 🛡️ **Type-Safe Cordis DSL & Minimal Benchmark**: Fluent builders for `CordisConfig` and `SandboxPolicy`, plus `DeepSeekHarness.createMinimal()` for one-line SWE-bench / agent benchmarking.
- 🔌 **Java Native Tool Registry**: Register any Spring Service or Java method as an LLM tool in seconds using `@HarnessTool` and `@Param`.
- 📊 **Enterprise Observability**: Out-of-the-box exporters for OpenTelemetry GenAI Semantic Conventions and Langfuse APM.
- 🍃 **Spring Boot 3 Starter**: Seamless auto-configuration, configuration properties binding, and high-level `DeepSeekHarnessTemplate`.

---

## 📦 Installation

### Maven

Add the SDK dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.deepseek-ai</groupId>
    <artifactId>deepseek-harness4j-sdk</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

Or for Spring Boot 3 applications:

```xml
<dependency>
    <groupId>com.deepseek-ai</groupId>
    <artifactId>deepseek-harness4j-spring-boot-starter</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

---

## 🚀 Quick Start

### 1. Basic Agent Execution

```java
import com.deepseek.harness4j.DeepSeekHarness;
import com.deepseek.harness4j.DeepSeekHarnessConfig;
import com.deepseek.harness4j.RunResult;

public class QuickStart {
    public static void main(String[] args) throws Exception {
        DeepSeekHarnessConfig config = DeepSeekHarnessConfig.builder()
                .provider("deepseek-official")
                .model("deepseek-reasoner") // DeepSeek-R1
                .cwd("/path/to/workspace")
                .build();

        try (DeepSeekHarness harness = new DeepSeekHarness(config)) {
            RunResult result = harness.run("Analyze the codebase and summarize project structure.");
            
            // Access DeepSeek-R1 Chain-of-Thought reasoning
            System.out.println("=== Reasoning Chain (CoT) ===");
            System.out.println(result.reasoningContent());

            // Access Final Answer
            System.out.println("=== Final Response ===");
            System.out.println(result.finalResponse());

            // Token Usage details
            System.out.println("Prompt Tokens: " + result.tokenUsage().promptTokens());
            System.out.println("Reasoning Tokens: " + result.tokenUsage().reasoningTokens());
        }
    }
}
```

### 2. Reactive Streaming (SSE / WebFlux)

```java
// Non-blocking Reactive Streams (Java 9 Flow.Publisher)
Flow.Publisher<StreamChunk> publisher = harness.stream("Refactor the auth module");

// Direct callback streaming
harness.stream("Explain quantum computing", chunk -> {
    switch (chunk.type()) {
        case "reasoning" -> System.out.print("[Thinking] " + chunk.text());
        case "content"   -> System.out.print(chunk.text());
        case "tool_call" -> System.out.println("\n[Tool Call] " + chunk.name());
    }
});
```

### 3. One-Line Minimal Harness for Benchmark (SWE-bench)

```java
// Create a minimal benchmark harness with only Bash & Editor
try (DeepSeekHarness harness = DeepSeekHarness.createMinimal("/workspace/swe-bench-task")) {
    RunResult result = harness.run("Fix the bug described in issue #1024");
    System.out.println("Finished: " + result.finishReason());
}
```

---

## 🛠️ Core Capabilities

### 1. Java Native Tool Bridge (`@HarnessTool`)

Expose native Java business logic and Spring Beans to the Agent without writing TypeScript plugins:

```java
public class DatabaseTools {

    @HarnessTool(name = "query_user", description = "Query user profile from database by ID")
    public String queryUser(@Param(name = "userId", description = "Target user ID") Long userId) {
        return "{\"id\":" + userId + ",\"name\":\"Alice\",\"role\":\"Admin\"}";
    }
}

// Register with ToolRegistry
ToolRegistry registry = new ToolRegistry();
registry.register(new DatabaseTools());

// Tool execution is handled with automatic JSON Schema extraction and reflection
```

### 2. Offline SessionLog Engine (Zero-Runtime Zstd Parser)

Analyze, replay, or fork agent trajectories without starting the agent process:

```java
Path sessionRoot = Path.of("/path/to/.sessions");

// 1. List all historical sessions (O(1) memory, reads header only)
List<SessionLog.Header> sessions = SessionLog.list(sessionRoot);

// 2. Global full-text search across all session archives
List<SessionLog.SearchHit> hits = SessionLog.searchAll(sessionRoot, "NullPointerException");

// 3. Lineage-preserving session branching (Fork)
SessionLog.Header forked = SessionLog.fork(sessionRoot, "session-v1", "session-v1-branch");

// 4. Resume the forked session live
RunResult resumed = harness.resume("Continue fixing with alternative approach", "session-v1-branch");
```

### 3. Type-Safe Cordis DSL

Configure the Cordis plugin runtime with a fluent, type-safe builder instead of error-prone YAML:

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

### 4. Enterprise Observability (OpenTelemetry & Langfuse)

```java
RunResult result = harness.run("Audit security vulnerabilities");

// Export to OpenTelemetry GenAI Semantic Conventions
Map<String, Object> spanAttributes = OtelTraceExporter.exportGenAiSpan(result, "deepseek-reasoner");

// Export to Langfuse APM format
Map<String, Object> langfuseTrace = LangfuseExporter.exportTrace(result, "security-audit-trace");
```

---

## 🍃 Spring Boot Integration

Add `deepseek-harness4j-spring-boot-starter` to your Spring Boot 3.x project:

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

### SSE Chat Controller

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

## 🏛️ Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       Java SDK (deepseek-harness4j)                          │
│                                                                             │
│  [Java Client & Governance Layer]                                           │
│  ├── Subprocess Lifecycle Guardian (ProcessBuilder + Circular Stderr Ring)  │
│  ├── stdio JSON-RPC 2.0 Async Frame Codec (Jackson, Compact UTF-8)          │
│  ├── Monotonic Clock Request-Response Matching (UUID -> BlockingQueue)      │
│  ├── Tree Notification Router (Hierarchical Sessions, Subagents)           │
│  ├── Reactive Event Streaming (Flow.Publisher<StreamChunk> / SseEmitter)    │
│  ├── Enhanced RunResult (DeepSeek-R1 CoT Extraction, Token Metering)        │
│  ├── Pure Java Offline SessionLog Engine (Zstd Stream Decoding, Fork, Search)│
│  ├── Java Native Tool Registry (@HarnessTool / Reflection Invoker)          │
│  ├── Type-Safe Cordis DSL (CordisConfig, SandboxPolicy, createMinimal)      │
│  └── Enterprise Integrations (Spring Boot 3, OpenTelemetry, Langfuse)       │
│                                                                             │
│                     │ stdio (newline-delimited JSON-RPC 2.0)                │
│                     ▼                                                       │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │  Bundled Agent Runtime Subprocess (dsh-jsonrpc-agent)                 │  │
│  │                                                                       │  │
│  │  Cordis Plugin Kernel (TypeScript / Fiber Lifecycle / DI)             │  │
│  │  ├── agent-spine (LLM reasoning loop, context assembly, token meter)  │  │
│  │  ├── llm-deepseek / llm-pi-ai (Official & multi-provider adapters)    │  │
│  │  ├── persistent-bash / str-replace-editor / tool-fs (Sandbox tools)   │  │
│  │  ├── session-persistence-jsonl (Append-only Zstd/JSONL disk storage)  │  │
│  │  └── subagent / compaction (In-process delegation & window compaction)│  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 📚 Documentation

For exhaustive architecture breakdowns, runtime configuration catalogs, and troubleshooting:

- 📖 **[DeepSeek Harness User Guide (English)](deepseek-harness4j-user-guide.en.md)**: Deep dive on runtime architecture, custom model endpoints, and session persistence.
- 📖 **[DeepSeek Harness 使用指南 (中文)](deepseek-harness4j-使用指南.md)**: 完整架构剖析、自定义模型接入与生产级配置。
- 📊 **[Five Core Features Review Report](docs/review-five-features.md)**: In-depth parity review vs upstream Python SDK and benchmark specs.
- 🛠️ **[Development & Contribution Guide](development.md)**: Building, testing, and native runtime bundling.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
