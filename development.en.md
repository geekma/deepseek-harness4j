# DeepSeek Harness Java SDK contributor workflows

[中文](development.md) | English

Follow the workflow for the contributor outcome you need: build runtime artifacts, validate the SDK, run against source, or build distributions. Package behavior belongs in the [SDK reference](sdk/README.en.md) and [runtime carrier reference](sdk-runtime/README.en.md). This file is the Java port of the upstream `python/development.md`: the Python-side build/test/distribution tooling (pnpm, uv, pytest, wheels) is replaced by the Java ecosystem tooling (Maven, JUnit), with each workflow's semantics preserved.

## Build runtime artifacts

> **This section is for SDK contributors and release engineers only.** Regular Java SDK users should grab a pre-built carrier binary and follow [sdk-runtime/README.en.md](sdk-runtime/README.en.md). The steps below require the **upstream toolchain** (Node.js 22+ / pnpm), which is intentionally **not** a dependency of deepseek-harness4j.

Platform executables are build artifacts and are not checked into git (they are injected into the runtime carrier by the upstream deepseek-harness build). Run the build from the **upstream repository root**:

```sh
# Upstream build — requires Node.js 22+ and pnpm (not a Java SDK dependency)
corepack enable
pnpm install
pnpm exec tsx scripts/build-exe-for-python-sdk.ts
```

Use `--skip-build` when the required `lib/` artifacts already exist, or `--targets=node24-linux-x64,node24-linux-arm64,node24-macos-arm64` to select platforms. Products land in the upstream `dist-exe/` and the script syncs the selected carriers into the matching runtime carrier. macOS builds also sync the matching spawn helper required by `node-pty`.

After building, place the produced executables into this repository's runtime-carrier directory (or install the `deepseek-harness-runtime.json` metadata plus `runtime/` content in an explicit runtime directory supported by `RuntimeResolver`) so `deepseek-harness4j-sdk` can launch them zero-config.

## Validate the SDK

```sh
cd deepseek-harness4j
mvn install          # first install sdk and spring-boot-starter into the local repo (for example)
mvn test             # run the sdk module's JUnit 5 suite
```

`BundledRuntimeBootTest` (the port of Python's `test_bundled_runtime.py`) exercises available bundled carriers and **independently skips** a carrier whose artifact has not been built or installed, exactly like Python.

An interactive smoke test needs `DEEPSEEK_API_KEY` in the environment:

```java
import com.deepseek.harness4j.DeepSeekHarness;

try (DeepSeekHarness harness = new DeepSeekHarness()) {
    System.out.println(harness.run("say hi").finalResponse());
}
```

## Run against Node source

Repository contributors can select either development carrier:

- Set `DSH_RUNTIME_MODE=node` to use the built Node carrier on system Node `>=22.19`. The build script refreshes this carrier, but distributions never include or auto-select it.
- With the upstream repository root as `cwd`, set `launchArgsOverride` to `("node", "--import", "tsx", "packages/examples/jsonrpc-demo/src/bin.ts")` to run unbuilt TypeScript source. Supply `cordis=...` when the default configuration is not suitable.

In Java:

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

A complete source-mode invocation is covered by the `FakeRuntime`-based tests under `sdk/src/test` (the port of `manual_sdk_agent_smoke.py`).

## Build distributions

The Python pipeline — root `package.json` as the authoritative version, wheel builds (a pure SDK wheel plus one runtime wheel per native platform), PyPI publishing — is replaced by **Maven** on the Java side:

```sh
cd deepseek-harness4j
mvn clean install     # build and install deepseek-harness4j-sdk / -spring-boot-starter / -spring-boot-example
mvn deploy            # publish to your Maven repository (Nexus / Artifactory, etc.)
```

- The version is authoritative in the root `pom.xml` `<version>`; release tags are validated by `ReleaseVersion.validateReleaseTag` (the `python-v<version>` form, kept for scenarios that must align with upstream wheel tags).
- The runtime distribution publishes platform payloads only (Linux x64 / arm64, macOS 14+ on arm64), matching the upstream wheel platform matrix; the fixed tags and executable names in `PlatformManifest` stay unchanged.
- Use `ReleaseVersion.pep440Version` when you must align with the upstream PEP 440 version spelling (for example, to mirror runtime wheel filenames).

## Validate a release candidate

- Run `mvn verify` locally first (tests included), then `mvn release:prepare` / `mvn release:perform` (after configuring `scm` in the pom).
- Before a release, validate with `MacOsDeploymentTarget.ensureCompatible` that a macOS runtime executable's deployment target is not newer than its distribution tag claims (the port of upstream `check-macos-deployment-target.py`).
- Releases run only from the configured repository at a matching `python-v<version>` (or Maven `release`) tag, without disclosing publisher credentials.
