# DeepSeek Harness 运行时载体（Java）

[English](README.en.md) | 中文

DeepSeek Harness Java SDK 的运行时载体模块：`RuntimeResolver`（`com.deepseek.harness4j.runtime`，对应上游 Python 包 `deepseek_harness_runtime`）定位 `deepseek-harness4j-sdk` 客户端要 spawn 的内置运行时二进制，并附带支撑零配置运行的默认配置。

## 运行时载体

两种载体并存于 `runtime/` 之下，均由上游 deepseek-harness 的 `scripts/build-exe-for-python-sdk.ts` 构建注入，且均不被检入 git：

- **exe（生产）**——单文件 Node 可执行程序 `dsh-jsonrpc-agent-pkg-<platform>-<arch>`（platform：`linux`/`macos`；arch：`x64`/`arm64`）。macOS 构建还会随附 `node-pty` 在该平台使用的原生 `-spawn-helper` 伴随文件。目标机器无需安装 Node。这是分发物中唯一随包提供的载体。
- **node（仅限开发）**——`runtime/node/` 下的完整部署闭包（`package.json` + `node_modules/`），在系统 Node >= 22.19 上以 `node runtime/node/node_modules/@deepseek-ai/dsh-sdk-jsonrpc-demo/lib/packaged-bin.js` 执行。它是当前检出的源码构建，仅用于仓库本地的开发与验证；不会被自动选中，也不进入分发物。

Java 移植中，`deepseek-harness4j-sdk` 直接随包携带 `deepseek-harness-runtime.json` 元数据与 `runtime/cordis.yml` 默认配置（作为 classpath 资源）；`RuntimeResolver.bundledPackageDir()` 的解析顺序是：显式运行时目录（系统属性 `dsh4j.runtime.dir` 或环境变量 `DSH4J_RUNTIME_DIR`）> 元数据 classpath 资源所在目录。资源在 jar 内部时，默认 `cordis.yml` 会被物化到缓存的临时文件，以便按路径交给子进程。

exe 缺失时抛出 `MissingRuntimeException`（对应 Python 的 `FileNotFoundError`），并写明两种获取途径：在 deepseek-harness 检出中经 `scripts/build-exe-for-python-sdk.ts` 构建，或安装匹配平台的运行时分发。仅限开发的 node 载体缺失时只提示构建脚本这一条途径。获取策略与查找接口刻意分离，之后可以换成按需下载而不改动任何调用方。

每个运行时只包含一个可执行文件。macOS 上的可执行文件还需要其匹配的原生 spawn helper；缺少伴随文件意味着安装不完整，并会在启动时硬失败，即使所选 Cordis 组合不使用 PTY 工具也是如此。Linux 不包含 spawn helper，因为 `node-pty` 直接使用暂存的 `pty.node` 原生插件。固定平台标签在 `platforms.json` 中定义（`manylinux_2_28_x86_64`、`manylinux_2_28_aarch64`、`macosx_14_0_arm64`）。

## 解析 API

- `RuntimeResolver.resolveBundledLaunchArgs(mode)` —— 启动内置运行时的 argv 数组：exe 模式下为 `(exe_path)`，node 模式下为 `(node_path, bin_js_path)`。模式选择：显式参数 > `DSH_RUNTIME_MODE` 环境变量（`exe` | `node`）> 自动。自动解析只找生产 exe——仅限开发的 node 载体必须显式选用，从而生产部署绝不会悄悄跑在源码构建上。
- `RuntimeResolver.bundledRuntimePath()` —— 平台 exe 路径（仅 exe 载体，并会在 macOS 上校验必要的 `-spawn-helper` 伴随文件也已安装）。
- `RuntimeResolver.bundledDefaultConfigPath()` —— 随包默认配置路径（见下文）。
- `RuntimeResolver.bundledPackageDir()` —— 已安装包的数据根目录。

## 零配置设计

运行时二进制始终要求显式配置（`$DSH_CORDIS_CONFIG`，或作为 argv 位置参数的配置路径），缺了就报错退出——这一强制语义是运行时设计的一部分，Java 移植不会弱化它。bin（`dsh-jsonrpc-agent`）只启动配置里列出的插件；对外服务接口（stdio JSON-RPC 服务器）也是其中一个条目（`@deepseek-ai/dsh-sdk-jsonrpc-server`），缺了它，启动出的 agent（智能体）就没有对外通道。随包默认 `runtime/cordis.yml` 包含 JSON-RPC 服务条目、agent 核心、预载的 DeepSeek 适配器、JSONL 持久化、显式组合的语义检查点策略、本地 bash，以及用于有界加载工作区指令的本地文件系统提供方。DeepSeek 适配器读取 `DEEPSEEK_API_KEY` 与 `DEEPSEEK_BASE_URL`，持久化、bash 和文件系统提供方则使用 `DSH_SESSION_ROOT` 和 `DSH_CWD`，并为手动运行提供回退值。调用方未使用任何显式配置通道时，`HarnessClient` 把该文件路径注入 `DSH_CORDIS_CONFIG`（注入条件见 [sdk README](../sdk/README.md)）。因此，零配置是包装层中一次显式、可见的参数传递，而不是运行时中的隐藏回退。
