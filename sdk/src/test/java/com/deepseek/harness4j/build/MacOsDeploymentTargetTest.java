package com.deepseek.harness4j.build;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Port of {@code python/sdk/tests/test_macos_deployment_target.py} against the
 * {@link MacOsDeploymentTarget} helper.
 */
class MacOsDeploymentTargetTest {

    @Test
    void test_otool_parser_uses_the_newest_macho_slice() {
        String output = """
                  cmd LC_BUILD_VERSION
                minos 11.0
                  cmd LC_BUILD_VERSION
                minos 13.5
                """;
        assertEquals(List.of(13, 5), MacOsDeploymentTarget.parseOtoolDeploymentTarget(output));
    }

    @Test
    void test_otool_parser_requires_a_deployment_target() {
        IllegalArgumentException exc = assertThrows(IllegalArgumentException.class,
                () -> MacOsDeploymentTarget.parseOtoolDeploymentTarget("Load command 0\n"));
        assertTrue(exc.getMessage().contains("contains no LC_BUILD_VERSION"));
    }

    @Test
    void test_wheel_tag_rejects_a_newer_executable_target() {
        MacOsDeploymentTarget.ensureCompatible(Path.of("runtime"), List.of(13, 5), "macosx_14_0_arm64");

        IllegalStateException exc = assertThrows(IllegalStateException.class,
                () -> MacOsDeploymentTarget.ensureCompatible(
                        Path.of("spawn-helper"), List.of(14, 1), "macosx_14_0_arm64"));
        assertTrue(exc.getMessage().contains("requires macOS 14.1"));
    }
}
