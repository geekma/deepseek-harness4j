# Examples (deepseek-harness4j examples)

[中文](README.md) | English

> This file is the port of the upstream `examples/README.md` (+ `README.zh.md`). The upstream `examples/` has several leaves (`mcp-memory`, `headless-agent`, `jsonrpc-agent`, `web-cordis`, `web-schedule`, `acp-agent`); of these, **`jsonrpc-agent` is the Python SDK's companion example** and is ported to Java here; the remaining leaves are Web/CLI/ACP demos (TypeScript/config, language-neutral references — see `docs/repo-inventory.md`).

Runnable demonstrations of the main DeepSeek Harness interfaces and extension points. Each child directory owns its configuration, prerequisites, commands, and detailed behavior.

## jsonrpc-agent (ported to Java)

An unattended coding agent driven through the SDK and JSON-RPC. Upstream it is `minimal.py` (Python); this repository's counterpart is `MinimalAgent` (`com.deepseek.harness4j.examples.MinimalAgent`). Example reference: [jsonrpc-agent/README.md](jsonrpc-agent/README.md).

```sh
cd deepseek-harness4j
mvn -q -pl sdk exec:java \
  -Dexec.mainClass=com.deepseek.harness4j.examples.MinimalAgent \
  -Dexec.args="--workspace /absolute/path/workspace --session-root /absolute/path/sessions --session-id example-001 'your task'"
```

## Remaining upstream examples (language-neutral references, code not ported)

| Leaf | Description | Status in the Java project |
|---|---|---|
| `mcp-memory` | Optional overlays that connect supported third-party memory servers through the generic MCP client | reference (`examples/mcp-memory/`, upstream) |
| `headless-agent` | A non-interactive agent that accepts one task, runs it, and emits a selected machine-readable or human-readable output format | reference (CLI channel; the SDK `run()` semantics correspond) |
| `web-cordis` | A self-referential agent that can inspect and change its in-memory Cordis plugin tree | reference |
| `web-schedule` | An opt-in Web overlay for durable, Session-local reminders | reference |
| `acp-agent` | An Agent Client Protocol automation server for programmatic clients, with session, permission, and cancellation support | reference (another client protocol) |

The Java side additionally ships Spring ecosystem examples: `spring-boot-starter` (auto-configuration) and `spring-boot-example` (Spring MVC REST controller); see the root README and `docs/java-migration-notes.en.md`.
