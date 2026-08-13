package com.deepseek.harness4j.runtime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Port of the keyless runtime-resolution tests in
 * {@code python/sdk/tests/test_runtime_resolution.py}; launch coverage lives in
 * {@code BundledRuntimeBootTest}.
 */
class RuntimeResolverTest {

    @TempDir
    Path tmp;

    private String previousOsName;
    private String previousOsArch;
    private String previousRuntimeDir;

    @AfterEach
    void restorePlatform() {
        if (previousOsName != null) {
            System.setProperty("os.name", previousOsName);
        }
        if (previousOsArch != null) {
            System.setProperty("os.arch", previousOsArch);
        }
        if (previousRuntimeDir != null) {
            System.setProperty(RuntimeResolver.RUNTIME_DIR_PROPERTY, previousRuntimeDir);
        } else {
            System.clearProperty(RuntimeResolver.RUNTIME_DIR_PROPERTY);
        }
        RuntimeResolver.runtimeModeEnvOverride = null;
    }

    @Test
    void test_default_config_is_shipped_with_the_package() throws Exception {
        Path path = RuntimeResolver.bundledDefaultConfigPath();
        assertEquals(RuntimeResolver.bundledPackageDir().resolve("runtime").resolve("cordis.yml"), path);
        String config = Files.readString(path);
        assertTrue(config.contains("@deepseek-ai/dsh-agent-spine-demo"));
        assertTrue(config.contains("@deepseek-ai/dsh-session-persistence-jsonl"));
        assertTrue(config.contains("@deepseek-ai/dsh-session-checkpoint-policy"));
    }

    @Test
    void test_unknown_explicit_mode_fails_loud() {
        IllegalArgumentException exc = assertThrows(IllegalArgumentException.class,
                () -> RuntimeResolver.resolveBundledLaunchArgs("bogus"));
        assertTrue(exc.getMessage().contains("expected 'exe' or 'node'"));
    }

    @Test
    void test_unknown_env_mode_fails_loud() {
        RuntimeResolver.runtimeModeEnvOverride = "bogus";
        IllegalArgumentException exc = assertThrows(IllegalArgumentException.class,
                () -> RuntimeResolver.resolveBundledLaunchArgs(null));
        assertTrue(exc.getMessage().contains("expected 'exe' or 'node'"));
    }

    @Test
    void test_explicit_mode_wins_over_env_mode() {
        RuntimeResolver.runtimeModeEnvOverride = "bogus";
        try {
            String[] args = RuntimeResolver.resolveBundledLaunchArgs("exe");
            assertTrue(args[0].endsWith("-x64") || args[0].endsWith("-arm64"));
        } catch (com.deepseek.harness4j.error.MissingRuntimeException ignored) {
            // explicit 'exe' was honored; only the artifact is missing
        }
    }

    @Test
    void test_runtime_requires_spawn_helper_only_on_macos() throws Exception {
        Path runtimeDir = tmp.resolve("runtime");
        Files.createDirectories(runtimeDir);
        Path linux = runtimeDir.resolve("dsh-jsonrpc-agent-pkg-linux-x64");
        Files.createFile(linux);
        Files.createFile(runtimeDir.resolve("dsh-jsonrpc-agent-pkg-macos-arm64"));
        Files.writeString(tmp.resolve(RuntimeResolver.PACKAGE_METADATA_FILENAME),
                "{\"name\":\"deepseek-harness4j-runtime-bin\",\"version\":\"0.0.1\"}\n");
        previousRuntimeDir = System.setProperty(
                RuntimeResolver.RUNTIME_DIR_PROPERTY, tmp.toString());

        previousOsName = System.setProperty("os.name", "Mac OS X");
        previousOsArch = System.setProperty("os.arch", "aarch64");
        com.deepseek.harness4j.error.MissingRuntimeException exc =
                assertThrows(com.deepseek.harness4j.error.MissingRuntimeException.class,
                        RuntimeResolver::bundledRuntimePath);
        assertTrue(exc.getMessage().contains("node-pty spawn helper"));

        System.setProperty("os.name", "Linux");
        System.setProperty("os.arch", "x86_64");
        assertEquals(linux, RuntimeResolver.bundledRuntimePath());
    }
}
