package com.deepseek.harness4j.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Port of the isolated build hook in {@code python/sdk-runtime/hatch_build.py}
 * ({@code RuntimeBuildHook}): assign the native distribution tag and reject incomplete or
 * mixed-platform runtime payloads.
 *
 * <p>The Python hook runs inside a wheel build and writes {@code build_data["tag"]}; the Java
 * port exposes the same validation as static functions so a Maven build (or any caller) can
 * run the identical checks before packaging the runtime payload.
 */
public final class RuntimeBuildHook {

    /** Environment variable that overrides the platform tag during a build. */
    public static final String RUNTIME_PLATFORM_TAG_ENV = "DSH_RUNTIME_PLATFORM_TAG";

    private RuntimeBuildHook() {
    }

    /**
     * Reject sdist-style builds: the runtime distribution is distribution-only, mirroring the
     * Python "wheel-only; build and publish platform distributions only" hard error.
     *
     * @param targetName the build target name (e.g. {@code "jar"}, {@code "sdist"})
     * @param platformSpecified whether a concrete platform was requested
     * @throws IllegalStateException when a distribution is attempted without a platform
     */
    public static void rejectDistributionWithoutPlatform(String targetName, boolean platformSpecified) {
        if ("sdist".equals(targetName) || !platformSpecified) {
            throw new IllegalStateException(
                    "deepseek-harness4j-runtime-bin is distribution-only; build and publish "
                            + "platform distributions only (target=" + targetName + ")");
        }
    }

    /**
     * Resolve the distribution tag: an explicit {@code DSH_RUNTIME_PLATFORM_TAG} wins, else the
     * build-host platform tag (port of {@code RuntimeBuildHook.initialize}'s tag selection).
     *
     * @param envOverride the raw value of {@link #RUNTIME_PLATFORM_TAG_ENV}, or {@code null}
     * @return the resolved platform tag
     */
    public static String resolvePlatformTag(String envOverride) {
        return envOverride != null && !envOverride.isBlank()
                ? envOverride
                : PlatformManifest.hostPlatformTag();
    }

    /**
     * Validate that a runtime payload directory holds exactly the expected executable(s) for the
     * platform tag, each executable (port of the payload checks in
     * {@code RuntimeBuildHook.initialize}).
     *
     * @param runtimeDir  the directory holding {@code dsh-jsonrpc-agent-pkg-*} files
     * @param platformTag the resolved platform tag
     * @return the expected file names that must be present
     * @throws IllegalStateException when the payload is incomplete, mixed-platform, or not executable
     */
    public static List<String> validateRuntimePayload(Path runtimeDir, String platformTag) {
        List<String> expected = PlatformManifest.expectedRuntimeFiles(platformTag);
        List<String> found = new ArrayList<>();
        if (Files.isDirectory(runtimeDir)) {
            try (Stream<Path> stream = Files.list(runtimeDir)) {
                found = stream
                        .map(path -> path.getFileName().toString())
                        .filter(name -> name.startsWith("dsh-jsonrpc-agent-pkg-"))
                        .sorted()
                        .toList();
            } catch (IOException error) {
                throw new IllegalStateException("could not list runtime payload: " + runtimeDir, error);
            }
        }
        if (!found.equals(expected)) {
            throw new IllegalStateException(
                    "runtime distribution " + platformTag + " payload must be " + expected
                            + "; found " + found);
        }
        for (String name : expected) {
            Path executable = runtimeDir.resolve(name);
            if (!Files.isExecutable(executable)) {
                throw new IllegalStateException("runtime executable is not executable: " + executable);
            }
        }
        return expected;
    }
}
