package com.deepseek.harness4j.build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Port of the isolated-build-hook behavior of {@code python/sdk-runtime/hatch_build.py}:
 * host platform detection and runtime-payload validation.
 */
class RuntimeBuildHookTest {

    @TempDir
    Path tmp;

    @Test
    void test_host_platform_tag_resolves_from_the_manifest() {
        String tag = PlatformManifest.hostPlatformTag();
        assertTrue(PlatformManifest.PLATFORMS.values().stream().anyMatch(pair -> pair.get(0).equals(tag)),
                "host tag " + tag + " must be in the platform manifest");
    }

    @Test
    void test_explicit_tag_wins_over_host() {
        assertEquals("macosx_14_0_arm64",
                RuntimeBuildHook.resolvePlatformTag("macosx_14_0_arm64"));
        assertEquals(PlatformManifest.hostPlatformTag(), RuntimeBuildHook.resolvePlatformTag(null));
        assertEquals(PlatformManifest.hostPlatformTag(), RuntimeBuildHook.resolvePlatformTag("  "));
    }

    @Test
    void test_unknown_platform_tag_fails_loud() {
        assertThrows(IllegalArgumentException.class,
                () -> PlatformManifest.expectedRuntimeFiles("bogus"));
    }

    @Test
    void test_distribution_requires_a_platform() {
        assertThrows(IllegalStateException.class,
                () -> RuntimeBuildHook.rejectDistributionWithoutPlatform("sdist", true));
        assertThrows(IllegalStateException.class,
                () -> RuntimeBuildHook.rejectDistributionWithoutPlatform("jar", false));
    }

    @Test
    void test_payload_validation_accepts_exactly_the_expected_files() throws Exception {
        Path runtimeDir = tmp.resolve("runtime");
        Files.createDirectories(runtimeDir);
        String executable = PlatformManifest.expectedRuntimeFiles("macosx_14_0_arm64").get(0);
        Path exe = runtimeDir.resolve(executable);
        Files.writeString(exe, "runtime");
        setExecutable(exe);
        if (executable.contains("-macos-")) {
            Path helper = runtimeDir.resolve(executable + "-spawn-helper");
            Files.writeString(helper, "helper");
            setExecutable(helper);
        }

        assertEquals(PlatformManifest.expectedRuntimeFiles("macosx_14_0_arm64"),
                RuntimeBuildHook.validateRuntimePayload(runtimeDir, "macosx_14_0_arm64"));
    }

    @Test
    void test_payload_validation_rejects_a_missing_macos_spawn_helper() throws Exception {
        Path runtimeDir = tmp.resolve("runtime");
        Files.createDirectories(runtimeDir);
        Path exe = runtimeDir.resolve("dsh-jsonrpc-agent-pkg-macos-arm64");
        Files.writeString(exe, "runtime");
        setExecutable(exe);

        IllegalStateException exc = assertThrows(IllegalStateException.class,
                () -> RuntimeBuildHook.validateRuntimePayload(runtimeDir, "macosx_14_0_arm64"));
        assertTrue(exc.getMessage().contains("must be"));
    }

    @Test
    void test_payload_validation_rejects_a_non_executable_runtime() throws Exception {
        Path runtimeDir = tmp.resolve("runtime");
        Files.createDirectories(runtimeDir);
        Path exe = runtimeDir.resolve("dsh-jsonrpc-agent-pkg-linux-x64");
        Files.writeString(exe, "runtime");
        // deliberately not executable

        IllegalStateException exc = assertThrows(IllegalStateException.class,
                () -> RuntimeBuildHook.validateRuntimePayload(runtimeDir, "manylinux_2_28_x86_64"));
        assertTrue(exc.getMessage().contains("is not executable"));
    }

    private static void setExecutable(Path path) throws Exception {
        Files.setPosixFilePermissions(path, EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE,
                PosixFilePermission.GROUP_READ,
                PosixFilePermission.GROUP_EXECUTE,
                PosixFilePermission.OTHERS_READ,
                PosixFilePermission.OTHERS_EXECUTE));
    }
}
