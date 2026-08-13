# DeepSeek Harness Java SDK 开发工作流

[English](development.en.md) | 中文

根据所需的贡献者成果选择工作流：构建运行时产物、验证 SDK、从源码运行或构建分发物。包行为分别见 [SDK 参考](sdk/README.md) 和[运行时载体参考](sdk-runtime/README.md)。本文件是上游 `python/development.md` 的 Java 移植：Python 侧的构建/测试/分发工具（pnpm、uv、pytest、wheel）替换为 Maven / JUnit 等 Java 生态工具，语义逐条保留。

## 构建运行时产物

> **本节仅面向 SDK 贡献者与发布工程师。** 普通 Java SDK 用户请下载预编译载体二进制，参考 [sdk-runtime/README.md](sdk-runtime/README.md)。以下步骤需要**上游工具链**（Node.js 22+ / pnpm），这故意**不是** deepseek-harness4j 的依赖。

各平台可执行文件是构建产物，不检入 git（由上游 deepseek-harness 的构建注入到运行时载体）。请在**上游仓库根目录**运行构建：

```sh
# 上游构建 — 需要 Node.js 22+ 与 pnpm（不属于 Java SDK 依赖）
corepack enable
pnpm install
pnpm exec tsx scripts/build-exe-for-python-sdk.ts
```

所需 `lib/` 产物已存在时使用 `--skip-build`；如需选择平台，请使用 `--targets=node24-linux-x64,node24-linux-arm64,node24-macos-arm64`。产物写入上游 `dist-exe/`，脚本会将所选载体同步到对应运行时载体。macOS 构建还会同步 `node-pty` 所需的配套 spawn 辅助程序。

构建完成后，把生成的可执行文件放入本仓库运行时载体目录（或在 `RuntimeResolver` 支持的显式运行时目录中安装 `deepseek-harness-runtime.json` 元数据 + `runtime/` 内容），即可让 `deepseek-harness4j-sdk` 以零配置方式启动它。

## 验证 SDK

```sh
cd deepseek-harness4j
mvn install          # 先安装 sdk 与 spring-boot-starter 到本地仓库（供 example 使用）
mvn test             # 运行 sdk 模块的 JUnit 5 测试套件
```

`BundledRuntimeBootTest`（对应 Python 的 `test_bundled_runtime.py`）会运行可用的内置载体；某个载体的产物尚未构建/安装时，会像 Python 一样**独立跳过**该载体。

交互式冒烟测试需要环境变量中存在 `DEEPSEEK_API_KEY`：

```java
import com.deepseek.harness4j.DeepSeekHarness;

try (DeepSeekHarness harness = new DeepSeekHarness()) {
    System.out.println(harness.run("say hi").finalResponse());
}
```

## 针对 Node 源码运行

仓库贡献者可以选择以下任一开发载体：

- 设置 `DSH_RUNTIME_MODE=node`，在系统 Node `>=22.19` 上使用已构建的 Node 载体。构建脚本会刷新该载体，但分发物绝不会包含或自动选择它。
- 将上游仓库根目录设为 `cwd`，并设置 `launchArgsOverride` 为 `("node", "--import", "tsx", "packages/examples/jsonrpc-demo/src/bin.ts")`，以运行未构建的 TypeScript 源码。默认配置不合适时，请提供 `cordis=...`。

对应 Java：

```java
try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
        .cwd("/path/to/deepseek-harness")
        .runtimeCwd("/path/to/deepseek-harness")
        .launchArgsOverride("node", "--import", "tsx",
                "packages/examples/jsonrpc-demo/src/bin.ts")
        .build())) {
    System.out.println(harness.run("say hi").finalResponse());
}
```

完整的源码模式调用见本仓库 `sdk/src/test` 中基于 `FakeRuntime` 的测试（对应 Python 的 `manual_sdk_agent_smoke.py`）。

## 构建分发物

Python 侧以 `package.json` 版本为权威、构建 wheel 包（纯 SDK + 三个平台运行时 wheel，PyPI 发布）的流水线，在 Java 侧由 **Maven** 取代：

```sh
cd deepseek-harness4j
mvn clean install     # 构建并安装 deepseek-harness4j-sdk / -spring-boot-starter / -spring-boot-example
mvn deploy            # 发布到你的 Maven 仓库（Nexus / Artifactory 等）
```

- 版本以根 `pom.xml` 的 `<version>` 为权威；发布标签按 `ReleaseVersion.validateReleaseTag` 校验（`python-v<version>` 形式，供需要与上游 wheel 标签对齐的场景）。
- 运行时分发仅发布平台产物（Linux x64 / arm64、macOS 14+ arm64），与上游 wheel 平台矩阵一致；`PlatformManifest` 中的固定标签与可执行文件名保持不变。
- 需要与上游 PEP 440 版本拼写对齐时（例如同步运行时 wheel 文件名），使用 `ReleaseVersion.pep440Version`。

## 验证候选发行版

- 本地先跑 `mvn verify`（含测试），再 `mvn release:prepare` / `mvn release:perform`（需在 pom 中配置 `scm`）。
- 发布前用 `MacOsDeploymentTarget.ensureCompatible` 校验 macOS 运行时可执行文件的部署目标不高于其分发标签声称的版本（对应上游 `check-macos-deployment-target.py`）。
- 发布只从配置的仓库与匹配的 `python-v<version>`（或 Maven `release`）标签运行；不向公开仓库泄露发布者凭据。
