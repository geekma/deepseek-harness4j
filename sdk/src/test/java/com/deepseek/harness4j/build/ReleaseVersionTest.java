package com.deepseek.harness4j.build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Port of {@code python/sdk/tests/test_release_version.py}: repository-owned release versions
 * and the platform manifest.
 */
class ReleaseVersionTest {

    @TempDir
    Path tmp;

    @Test
    void test_repository_version_matches_root_pom() throws Exception {
        Path pom = tmp.resolve("pom.xml");
        Files.writeString(pom,
                "<project><modelVersion>4.0.0</modelVersion>"
                        + "<groupId>g</groupId><artifactId>a</artifactId>"
                        + "<version>1.2.3</version></project>\n");
        assertEquals("1.2.3", ReleaseVersion.repositoryVersion(pom));
    }

    @Test
    void test_release_tag_is_optional_for_non_release_builds() {
        ReleaseVersion.validateReleaseTag(null, "1.2.3");
    }

    @Test
    void test_release_tag_must_match_repository_version() {
        ReleaseVersion.validateReleaseTag("python-v1.2.3", "1.2.3");

        IllegalArgumentException exc = assertThrows(IllegalArgumentException.class,
                () -> ReleaseVersion.validateReleaseTag("python-v1.2.4", "1.2.3"));
        assertTrue(exc.getMessage().contains("expected 'python-v1.2.3'"));
    }

    @Test
    void test_repository_version_accepts_a_prerelease() throws Exception {
        Path pom = tmp.resolve("pom.xml");
        Files.writeString(pom,
                "<project><modelVersion>4.0.0</modelVersion>"
                        + "<groupId>g</groupId><artifactId>a</artifactId>"
                        + "<version>1.2.3-rc.1</version></project>\n");
        assertEquals("1.2.3-rc.1", ReleaseVersion.repositoryVersion(pom));
    }

    @Test
    void test_repository_version_rejects_malformed_versions() throws Exception {
        Path pom = tmp.resolve("pom.xml");
        Files.writeString(pom,
                "<project><modelVersion>4.0.0</modelVersion>"
                        + "<groupId>g</groupId><artifactId>a</artifactId>"
                        + "<version>v1.2</version></project>\n");
        IllegalArgumentException exc = assertThrows(IllegalArgumentException.class,
                () -> ReleaseVersion.repositoryVersion(pom));
        assertTrue(exc.getMessage().contains("must be X.Y.Z"));
    }

    @Test
    void test_pep440_version_spells_a_prerelease_the_python_way() {
        assertEquals("1.2.3", ReleaseVersion.pep440Version("1.2.3"));
        assertEquals("1.2.3rc1", ReleaseVersion.pep440Version("1.2.3-rc.1"));
        assertEquals("1.2.3a2", ReleaseVersion.pep440Version("1.2.3-alpha.2"));
        assertEquals("1.2.3b10", ReleaseVersion.pep440Version("1.2.3-beta.10"));

        IllegalArgumentException exc = assertThrows(IllegalArgumentException.class,
                () -> ReleaseVersion.pep440Version("1.2.3-nightly"));
        assertTrue(exc.getMessage().contains("no PEP 440 spelling"));
    }

    @Test
    void test_macos_wheel_tag_does_not_claim_unsupported_node_platforms() {
        assertEquals("macosx_14_0_arm64", PlatformManifest.PLATFORMS.get("macos-arm64").get(0));
    }

    @Test
    void test_platform_manifest_rejects_incomplete_entries() throws Exception {
        Path manifest = tmp.resolve("platforms.json");
        Files.writeString(manifest, "{\"macos-arm64\":{\"tag\":\"macosx_14_0_arm64\"}}\n");
        IllegalArgumentException exc = assertThrows(IllegalArgumentException.class,
                () -> PlatformManifest.loadPlatforms(manifest));
        assertTrue(exc.getMessage().contains("tag and executable fields"));
    }

    @Test
    void test_runtime_suffixes_match_platform_payloads() {
        assertEquals(List.of("", "-spawn-helper"),
                PlatformManifest.runtimeSuffixes("dsh-jsonrpc-agent-pkg-macos-arm64"));
        assertEquals(List.of(""),
                PlatformManifest.runtimeSuffixes("dsh-jsonrpc-agent-pkg-linux-x64"));
    }
}
