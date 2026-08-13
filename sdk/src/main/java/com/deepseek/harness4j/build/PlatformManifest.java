package com.deepseek.harness4j.build;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Port of the platform-manifest handling in {@code scripts/build-python-release.py}
 * ({@code load_platforms}, {@code PLATFORMS}, {@code runtime_suffixes}) and
 * {@code hatch_build.py} ({@code _load_platforms}). The manifest pairs each supported
 * platform with its wheel tag and runtime executable name.
 */
public final class PlatformManifest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Platform key to (wheel tag, executable name) pairs, loaded at class-load time. */
    public static final Map<String, List<String>> PLATFORMS = loadPlatforms(defaultManifest());

    private PlatformManifest() {
    }

    /**
     * Load the release platform tag and executable pairs from a build manifest.
     *
     * @param path the manifest path (defaults to the packaged {@code platforms.json})
     * @return a map of platform key to {@code (tag, executable)} pairs
     * @throws IllegalArgumentException when the manifest is unreadable or malformed
     */
    public static Map<String, List<String>> loadPlatforms(Path path) {
        Map<String, Object> payload;
        try {
            payload = MAPPER.readValue(Files.readAllBytes(path),
                    new TypeReference<Map<String, Object>>() {
                    });
        } catch (Exception error) {
            throw new IllegalArgumentException(
                    "could not read runtime platform manifest from " + path, error);
        }
        if (payload == null || payload.isEmpty()) {
            throw new IllegalArgumentException(
                    path + " must contain a non-empty platform object");
        }
        Map<String, List<String>> platforms = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            String name = entry.getKey();
            Object raw = entry.getValue();
            if (!(raw instanceof Map) || !((Map<?, ?>) raw).keySet().equals(java.util.Set.of("tag", "executable"))) {
                throw new IllegalArgumentException(
                        path + " platform entries must contain string tag and executable fields");
            }
            Map<String, Object> fields = (Map<String, Object>) raw;
            Object tag = fields.get("tag");
            Object executable = fields.get("executable");
            if (!(tag instanceof String) || !(executable instanceof String)) {
                throw new IllegalArgumentException(
                        path + " platform entries must contain string tag and executable fields");
            }
            platforms.put(name, List.of((String) tag, (String) executable));
        }
        return platforms;
    }

    /**
     * The extra payload file names for a runtime executable (port of {@code runtime_suffixes}):
     * a macOS executable also ships its {@code -spawn-helper}.
     */
    public static List<String> runtimeSuffixes(String executableName) {
        return executableName.contains("-macos-")
                ? List.of("", "-spawn-helper")
                : List.of("");
    }

    /**
     * The expected runtime payload file names for a platform tag (the executable plus its
     * macOS spawn helper).
     */
    public static List<String> expectedRuntimeFiles(String platformTag) {
        String executable = null;
        for (List<String> pair : PLATFORMS.values()) {
            if (pair.get(0).equals(platformTag)) {
                executable = pair.get(1);
                break;
            }
        }
        if (executable == null) {
            throw new IllegalArgumentException(
                    "unsupported platform tag '" + platformTag + "'; expected one of " + PLATFORMS.values());
        }
        List<String> files = new java.util.ArrayList<>();
        for (String suffix : runtimeSuffixes(executable)) {
            files.add(executable + suffix);
        }
        return files;
    }

    /**
     * The build-host platform key, port of {@code hatch_build._host_platform_tag()}: the
     * current OS/architecture mapped to a {@code <system>-<arch>} platform key.
     */
    public static String hostPlatformKey() {
        String machine = System.getProperty("os.arch", "").toLowerCase();
        String arch;
        if (machine.equals("arm64") || machine.equals("aarch64")) {
            arch = "arm64";
        } else if (machine.equals("x86_64") || machine.equals("amd64")) {
            arch = "x64";
        } else {
            arch = machine;
        }
        String system = System.getProperty("os.name", "").toLowerCase();
        String key;
        if (system.contains("mac") || system.contains("darwin")) {
            key = "macos-" + arch;
        } else if (system.contains("linux")) {
            key = "linux-" + arch;
        } else {
            key = system;
        }
        return key;
    }

    /**
     * The build-host platform tag (port of {@code hatch_build._host_platform_tag()}): the wheel
     * tag for {@link #hostPlatformKey()}, or an error for an unsupported host.
     */
    public static String hostPlatformTag() {
        String key = hostPlatformKey();
        List<String> pair = PLATFORMS.get(key);
        if (pair == null) {
            throw new IllegalArgumentException(
                    "unsupported deepseek-harness4j-runtime-bin build platform: " + key);
        }
        return pair.get(0);
    }

    private static Path defaultManifest() {
        try {
            return Path.of(PlatformManifest.class.getResource("/platforms.json").toURI());
        } catch (Exception error) {
            throw new IllegalStateException("missing packaged platforms.json", error);
        }
    }
}
