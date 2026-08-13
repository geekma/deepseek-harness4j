# DeepSeek Harness Runtime Carrier (Java)

[中文](README.md) | English

Runtime carrier module for the DeepSeek Harness Java SDK: `RuntimeResolver`
(`com.deepseek.harness4j.runtime`, the port of the upstream Python package
`deepseek_harness_runtime`) locates the bundled runtime binaries the
`deepseek-harness4j-sdk` client spawns, and ships the default configuration behind
zero-config runs.

## Runtime carriers

Two carriers coexist under `runtime/`, both injected by the upstream deepseek-harness
`scripts/build-exe-for-python-sdk.ts` build and both gitignored:

- **exe (production)** — a single-file Node executable `dsh-jsonrpc-agent-pkg-<platform>-<arch>`
  (platform: `linux`/`macos`; arch: `x64`/`arm64`). macOS builds also ship the native
  `-spawn-helper` sibling that `node-pty` uses there. No Node installation is needed on the
  target machine. This is the only carrier shipped in distributions.
- **node (dev-only)** — the full deploy closure under `runtime/node/` (`package.json` +
  `node_modules/`), executed as `node runtime/node/node_modules/@deepseek-ai/dsh-sdk-jsonrpc-demo/lib/packaged-bin.js`
  on a system Node >= 22.19. It is the current checkout's source build, meant for repo-local
  development and verification only; it is never selected automatically and is excluded from
  distributions.

In the Java port, `deepseek-harness4j-sdk` ships the `deepseek-harness-runtime.json` metadata
and the default `runtime/cordis.yml` directly as classpath resources. `RuntimeResolver.bundledPackageDir()`
resolves the installed runtime root in this order: an explicit runtime directory (system
property `dsh4j.runtime.dir` or environment variable `DSH4J_RUNTIME_DIR`), else the directory
that contains the metadata classpath resource. When that resource lives inside a jar, the
default `cordis.yml` is materialized to a cached temporary file so it can be handed to a
subprocess by path.

A missing exe raises `MissingRuntimeException` (the port of Python's `FileNotFoundError`)
naming both acquisition routes: build via `scripts/build-exe-for-python-sdk.ts` in a
deepseek-harness checkout, or install the matching platform runtime distribution. A missing
dev-only node carrier names its sole route, the build script. Acquisition strategy is
deliberately separate from the lookup interface, so an on-demand download can replace it later
without touching callers.

Each runtime contains exactly one runtime executable. On macOS that executable also requires
its matching native spawn helper; a missing sidecar makes that installation incomplete and is a
hard startup error, even for a selected Cordis composition that does not use PTY tools. Linux
contains no spawn helper because `node-pty` uses the staged `pty.node` addon directly. The
fixed platform tags live in `platforms.json`
(`manylinux_2_28_x86_64`, `manylinux_2_28_aarch64`, `macosx_14_0_arm64`).

## Resolution API

- `RuntimeResolver.resolveBundledLaunchArgs(mode)` — the argv array that launches the bundled
  runtime: `(exe_path)` in exe mode, `(node_path, bin_js_path)` in node mode. Mode selection:
  explicit argument > `DSH_RUNTIME_MODE` env var (`exe` | `node`) > automatic. Automatic
  resolution finds the production exe ONLY — the dev-only node carrier must be opted into
  explicitly so a production deployment can never silently ride on a source build.
- `RuntimeResolver.bundledRuntimePath()` — the platform exe path (exe carrier only; on macOS
  it validates that the required sibling `-spawn-helper` is also installed).
- `RuntimeResolver.bundledDefaultConfigPath()` — the shipped default config (see below).
- `RuntimeResolver.bundledPackageDir()` — the installed package data root.

## Zero-config design

The runtime binary always demands an explicit config (`$DSH_CORDIS_CONFIG`, or a config path
as an argv positional argument) and exits loudly without one — that hard semantic is part of
the runtime's design and the Java port does not soften it. The bin (`dsh-jsonrpc-agent`) boots
only the plugins the config lists; the serving interface (the stdio JSON-RPC server) is itself
one of its entries (`@deepseek-ai/dsh-sdk-jsonrpc-server`), and without it the booted agent has
no channel to the outside. The shipped `runtime/cordis.yml` carries the JSON-RPC serving entry,
agent core, a preloaded DeepSeek adapter, JSONL persistence, the explicitly composed semantic
checkpoint policy, local bash, and a local filesystem provider for bounded workspace-instruction
loading. The adapter reads `DEEPSEEK_API_KEY` and `DEEPSEEK_BASE_URL`, while persistence, bash,
and the filesystem provider use `DSH_SESSION_ROOT` and `DSH_CWD` with manual-run fallbacks.
When the caller uses no explicit config channel, `HarnessClient` injects that file's path via
`DSH_CORDIS_CONFIG` (injection conditions: [sdk README](../sdk/README.en.md)). Zero-config is
thus an explicit, visible parameter pass in the wrapper, not a hidden fallback in the runtime.
