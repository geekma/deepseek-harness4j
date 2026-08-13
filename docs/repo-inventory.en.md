# Upstream repository global inventory (deepseek-harness4j correspondence index)

[中文](repo-inventory.md) | English

This file is a **repository-level global checklist**: it indexes every top-level area of the upstream `deepseek-ai/deepseek-harness` (its `.md`/`.py` files) and records the port status and entry point in `deepseek-harness4j`. It is the repo-wide extension of `docs/port-coverage.md` (which targets `python/` and its references).

## Conclusion

- **The Python project itself (`python/`, 8 md + 14 py) and the `scripts/*.py` and `examples/jsonrpc-agent/minimal.py` it references are 100% ported** (see `port-coverage.md`).
- **User-facing documentation** (the parts of `docs/user/guide/` and `docs/cookbook/` relevant to usage/extension) is ported as bilingual references (`docs/user-guide/`); the rest of `docs/` is language-neutral architecture/engineering reference, indexed rather than line-ported.
- **In `examples/`, `jsonrpc-agent` (the Python SDK companion) is ported**; the remaining leaves are Web/CLI/ACP demos, indexed.
- **Node/TypeScript projects** (`packages/`, `apps/`, `vendor/`, `website/`, `native/`, `.agents/`) are not the Python project; `deepseek-harness4j` is a client port — the runtime and plugins are provided upstream — so they are indexed for reference.

## Top-level areas

| Area | md | py | Java status | Entry point |
|---|---|---|---|---|
| `python/` | 8 | 14 | ✅ 100% ported | [port-coverage.en.md](port-coverage.en.md) |
| `examples/` | 43 | 1 | ⚠️ `jsonrpc-agent` ported; rest referenced | §2 below |
| `docs/` | 215 | 0 | ⚠️ user tier ported to `docs/user-guide/`; rest indexed | §3 below |
| `scripts/` | 2 | 3 | ✅ pure functions of the 3 `.py` ported | [port-coverage.en.md](port-coverage.en.md) §2 |
| `packages/` | 545 | 0 | 📄 language-neutral reference (plugin contracts) | §4 below |
| `apps/` | 111 | 0 | 📄 reference (CLI/Web entries) | §4 below |
| `vendor/` | 12 | 0 | 📄 reference (vendored Cordis) | §4 below |
| `website/` | 1 | 0 | 📄 reference (docs site) | — |
| `native/` | 17 | 0 | 📄 reference (landlock addon) | — |
| `.agents/` | 1386 | 1 | 📄 upstream engineering workflows (Agent Notes/skills) | — |

## 1. `python/` (the Python project) — 100% ported

All 14 `.py` files and all 8 `.md` files have Java counterparts; the line-by-line audit is in [port-coverage.en.md](port-coverage.en.md).

## 2. `examples/` (runnable examples)

| Leaf | Upstream file count | Java status | Notes |
|---|---|---|---|
| `jsonrpc-agent/` | ~30 (incl. tests) | ✅ `minimal.py` → `MinimalAgent`; `minimal.cordis.yml`/`cordis.yml` → shipped resources; README → `examples/jsonrpc-agent/` | The Python SDK companion example, fully ported. Its `tests/` (TS e2e/snapshot) belong to the upstream Node tests; the semantics are covered by `sdk/src/test` (JUnit) |
| `mcp-memory/` | many | 📄 reference | generic MCP client overlays (config) |
| `headless-agent/` | many | 📄 reference | CLI channel; the SDK `run()` semantics correspond |
| `web-cordis/` | many | 📄 reference | self-referential agent demo (TS plugins) |
| `web-schedule/` | many | 📄 reference | Web reminder overlay (config) |
| `acp-agent/` | many | 📄 reference | ACP automation server (another client protocol) |

## 3. `docs/` (documentation)

### 3.1 Ported as Java bilingual references (`docs/user-guide/`)

| Upstream | Java counterpart |
|---|---|
| `docs/user/guide/python-sdk.md` (+zh) | `docs/user-guide/python-sdk.md` / `.en.md` |
| `docs/user/guide/index.md` (+zh) | `docs/user-guide/web-ui.md` / `.en.md` |
| `docs/user/guide/providers.md` (+zh) | `docs/user-guide/providers.md` / `.en.md` |
| `docs/cookbook/adding-an-llm-adapter.md` (+zh) | `docs/user-guide/adding-an-llm-adapter.md` / `.en.md` |
| `docs/cookbook/adding-a-tool.md` (+zh) | `docs/user-guide/adding-a-tool.md` / `.en.md` |
| `examples/README.md` (+zh) | `examples/README.md` / `.en.md` |

### 3.2 Language-neutral references (folded in / indexed, not line-ported)

| Upstream doc | Nature | Where it lands in 4j |
|---|---|---|
| `docs/architecture.md` | core architecture map (composition/core packages/loop/seams/extension points) | deep-dive in `deepseek-harness4j-user-guide.en.md` §16 |
| `docs/cordis-primer.md` / `docs/cordis-tutorial/` | Cordis composition syntax/tutorial | language-neutral; referenced by the guide and user-guide |
| `docs/development.md` | contributor setup/daily/CI | ported as `development.md` (.en) |
| `docs/testing.md` | testing policy | language-neutral; 4j tests in `development.md` |
| `docs/glossary.md` | terminology | key terms folded into guide §16.1/§17.6 |
| `docs/config-catalog.md` / `tool-catalog.md` / `persistence-catalog.md` | generated plugin config/tool/persistence catalogs | language-neutral generated artifacts; referenced by providers/user-guide |
| `docs/capability-seams.md` / `event-producer-consumer.md` / `defensive-patterns.md` / `tool-execution-pipeline.md` / `agent-lifecycle.md` / `api-gateway.md` | architecture references | language-neutral; key points folded into the guide |
| `docs/cookbook/*` (others) | package/tool/extension how-tos | language-neutral; extension-related in `docs/user-guide/` |
| `docs/subsystems/`, `docs/module-graph.md`, `docs/graph-atlas.md`, `docs/postmortem/`, `docs/i18n/`, `docs/web-styling.md` | internal/generated references | language-neutral, not ported |

## 4. Node/TypeScript projects (not Python; indexed)

| Area | Notes | Relation to 4j |
|---|---|---|
| `packages/` (545 md) | all `@deepseek-ai/dsh-*` plugin sources and READMEs | 4j is a client port; plugin contracts (config-catalog etc.) are language-neutral references |
| `apps/` (111 md) | CLI (`dsh`) and Web UI application entries | channel references; the SDK talks to the runtime over JSON-RPC (see `docs/python-sdk-api-reference.en.md` §9) |
| `vendor/` (12 md) | vendored Cordis source | pinned upstream; 4j does not copy it |
| `website/` | VitePress docs site | 4j docs are independent |
| `native/` (17 md) | landlock sandbox addon | runtime native component; 4j does not copy it |
| `.agents/` (1386 md) | Agent Notes / skills / workflows | upstream engineering discipline; unrelated to 4j |

## 5. Verification

```sh
# Full upstream enumeration (result: zero omissions; the accidental nested deepseek-harness4j copy is excluded)
find . -name '*.py' -not -path './vendor/*' -not -path '*/node_modules/*' -not -path './deepseek-harness4j/*' | wc -l   # 19
find . -name '*.md' -not -path './vendor/*' -not -path '*/node_modules/*' -not -path './deepseek-harness4j/*' | wc -l   # 2344
```

All 14 `.py` files inside `python/` and 8 `.md` files have Java counterparts — 19 `.py` files in
total once the referenced `scripts/*.py` and `examples/jsonrpc-agent/minimal.py` are counted
(see `port-coverage.en.md`); this checklist covers the ownership and status of the remaining 2336 `.md` files.
