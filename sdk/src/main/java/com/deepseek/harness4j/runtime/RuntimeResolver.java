package com.deepseek.harness4j.runtime;

import com.deepseek.harness4j.error.MissingRuntimeException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Locate the bundled DeepSeek Harness SDK runtime shipped with this package.
 *
 * <p>Line-by-line Java port of {@code python/sdk-runtime/src/deepseek_harness_runtime/__init__.py}.
 *
 * <p>Two runtime carriers coexist under {@code runtime/}, both injected by the
 * repository's build (neither is checked into git):
 *
 * <ul>
 *   <li><b>exe (production)</b>: single-file Node executables named
 *       {@code dsh-jsonrpc-agent-pkg-<platform>-<arch>} (platform in {linux, macos}, arch in
 *       {x64, arm64}); macOS also uses a sibling {@code -spawn-helper}. The target machine
 *       needs no Node installation.</li>
 *   <li><b>node (dev-only)</b>: the full deploy closure under {@code runtime/node/}
 *       ({@code package.json} + {@code node_modules/}), executed as {@code node
 *       runtime/node/node_modules/@deepseek-ai/dsh-sdk-jsonrpc-demo/lib/packaged-bin.js} on a
 *       system Node &gt;= 22.19. It is the current checkout's source build, never
 *       selected automatically, and excluded from distributions.</li>
 * </ul>
 *
 * <p>{@code runtime/cordis.yml} IS shipped (as a classpath resource): it is the default agent
 * configuration the client SDK injects via {@code $DSH_CORDIS_CONFIG} for zero-config runs —
 * the runtime itself always requires an explicit config and has no built-in fallback.
 *
 * <p>In the Python package the "installed package data root" is the module directory. In
 * Java it is resolved in this order: an explicit runtime directory (system property
 * {@code dsh4j.runtime.dir} or environment variable {@code DSH4J_RUNTIME_DIR}), else the
 * directory that contains the {@code deepseek-harness-runtime.json} classpath resource.
 * When that resource lives inside a jar, the default {@code cordis.yml} is materialized to a
 * cached temporary file so it can be handed to a subprocess by path.
 */
public final class RuntimeResolver {

    /** Name of the metadata file that marks an installed runtime package root. */
    public static final String PACKAGE_METADATA_FILENAME = "deepseek-harness-runtime.json";

    /** Environment variable selecting the runtime mode ({@code exe} | {@code node}). */
    public static final String RUNTIME_MODE_ENV_VAR = "DSH_RUNTIME_MODE";

    /** System property (and {@code DSH4J_RUNTIME_DIR} env var) for an explicit runtime dir. */
    public static final String RUNTIME_DIR_PROPERTY = "dsh4j.runtime.dir";
    public static final String RUNTIME_DIR_ENV_VAR = "DSH4J_RUNTIME_DIR";

    private static final Map<String, String> PLATFORM_TAGS = Map.of(
            "linux", "linux",
            "darwin", "macos",
            "mac", "macos");

    private static final Map<String, String> ARCH_TAGS = Map.of(
            "x86_64", "x64",
            "amd64", "x64",
            "arm64", "arm64",
            "aarch64", "arm64");

    private static final String EXE_ACQUISITION_HINT =
            "Two ways to get the executable: build the DeepSeek Harness SDK runtime in a "
                    + "deepseek-harness checkout, or install the matching "
                    + "`deepseek-harness4j-runtime-bin` platform distribution. For local "
                    + "development against a repository source build, explicitly select the "
                    + "dev-only node carrier with DSH_RUNTIME_MODE=node (or "
                    + "resolveBundledLaunchArgs(\"node\")).";

    private static volatile Path materializedDefaultConfig;

    /**
     * Test hook mirroring Python's {@code monkeypatch.setenv(DSH_RUNTIME_MODE, ...)}: when set,
     * {@link #resolveBundledLaunchArgs(String)} prefers it over the real environment variable.
     * Intended for the runtime-resolution tests.
     */
    public static volatile String runtimeModeEnvOverride;

    private RuntimeResolver() {
    }

    /**
     * Root directory of the installed runtime package data.
     *
     * <p>Port of {@code bundled_package_dir()}. Raises {@link MissingRuntimeException} when
     * the package metadata file is absent from every resolution channel.
     */
    public static Path bundledPackageDir() {
        Path explicit = explicitRuntimeDir();
        if (explicit != null) {
            Path metadata = explicit.resolve(PACKAGE_METADATA_FILENAME);
            if (!Files.isRegularFile(metadata)) {
                throw new MissingRuntimeException(
                        "deepseek-harness4j-runtime is missing " + metadata);
            }
            return explicit;
        }
        URL resource = resource(PACKAGE_METADATA_FILENAME);
        if (resource != null && "file".equals(resource.getProtocol())) {
            try {
                return Path.of(resource.toURI()).getParent();
            } catch (URISyntaxException exc) {
                throw new MissingRuntimeException(
                        "could not resolve runtime metadata resource location", exc);
            }
        }
        throw new MissingRuntimeException(
                "Unable to locate the bundled DeepSeek Harness SDK runtime. "
                        + "Install deepseek-harness4j-runtime-bin or set dsh4j.runtime.dir / "
                        + "DSH4J_RUNTIME_DIR to an installed runtime package root.");
    }

    /**
     * Path of the default runtime configuration ({@code runtime/cordis.yml}).
     *
     * <p>Port of {@code bundled_default_config_path()}. The client SDK injects this path via
     * {@code $DSH_CORDIS_CONFIG} when the caller supplies no config and the launch resolves to
     * the bundled runtime — the runtime binary itself always demands an explicit config.
     */
    public static Path bundledDefaultConfigPath() {
        Path packageDir;
        try {
            packageDir = bundledPackageDir();
            Path path = packageDir.resolve("runtime").resolve("cordis.yml");
            if (Files.isRegularFile(path)) {
                return path;
            }
        } catch (MissingRuntimeException ignored) {
            // Fall through to classpath materialization when no installed root exists.
        }
        Path materialized = materializedDefaultConfig;
        if (materialized != null && Files.isRegularFile(materialized)) {
            return materialized;
        }
        URL resource = resource("runtime/cordis.yml");
        if (resource == null) {
            throw new MissingRuntimeException(
                    "deepseek-harness4j-runtime is missing the default runtime config at "
                            + "runtime/cordis.yml");
        }
        try {
            Path target = Files.createTempFile("deepseek-harness4j-default-", ".cordis.yml");
            try (InputStream in = resource.openStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            materializedDefaultConfig = target;
            target.toFile().deleteOnExit();
            return target;
        } catch (IOException exc) {
            throw new MissingRuntimeException(
                    "could not materialize the default runtime config from the classpath", exc);
        }
    }

    /**
     * Absolute path of the bundled single-file runtime executable for the current platform.
     *
     * <p>Port of {@code bundled_runtime_path()}. Raises {@link MissingRuntimeException} when the
     * platform is unsupported, the executable has not been placed into this package, or the
     * required macOS spawn helper is missing; the message names the acquisition routes
     * (acquisition strategy is deliberately separate from this lookup interface, so an on-demand
     * download can replace it without touching callers).
     */
    public static Path bundledRuntimePath() {
        String tag = currentPlatformTag();
        Path path = bundledPackageDir().resolve("runtime").resolve("dsh-jsonrpc-agent-pkg-" + tag);
        if (!Files.isRegularFile(path)) {
            throw new MissingRuntimeException(
                    "deepseek-harness4j-runtime is missing the runtime executable at " + path + ". "
                            + EXE_ACQUISITION_HINT);
        }
        if (tag.startsWith("macos-")) {
            Path helper = Path.of(path + "-spawn-helper");
            if (!Files.isRegularFile(helper)) {
                throw new MissingRuntimeException(
                        "deepseek-harness4j-runtime is missing the node-pty spawn helper at "
                                + helper + ". " + EXE_ACQUISITION_HINT);
            }
        }
        return path;
    }

    /**
     * The argv tuple that launches the bundled runtime.
     *
     * <p>Port of {@code resolve_bundled_launch_args()}. Mode selection: the explicit
     * {@code mode} argument wins, then the {@code DSH_RUNTIME_MODE} environment variable
     * ({@code exe} | {@code node}), then automatic resolution. Automatic resolution finds the
     * production exe ONLY — the dev-only node carrier must be selected explicitly so a
     * production deployment can never silently ride on a source build. Returns
     * {@code (exe_path)} in exe mode and {@code (node_path, bin_js_path)} in node mode; raises
     * {@link MissingRuntimeException} when the selected carrier is unavailable and
     * {@link IllegalArgumentException} (port of Python's {@code ValueError}) for an unknown mode.
     */
    public static String[] resolveBundledLaunchArgs(String mode) {
        String selected = mode != null ? mode
                : runtimeModeEnvOverride != null ? runtimeModeEnvOverride
                : System.getenv(RUNTIME_MODE_ENV_VAR);
        if (selected == null || "exe".equals(selected)) {
            return new String[]{bundledRuntimePath().toString()};
        }
        if ("node".equals(selected)) {
            return nodeLaunchArgs();
        }
        throw new IllegalArgumentException(
                "unsupported DeepSeek Harness runtime mode '" + selected
                        + "': expected 'exe' or 'node' "
                        + "(explicit argument or $" + RUNTIME_MODE_ENV_VAR + ")");
    }

    private static String currentPlatformTag() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        String platform = null;
        for (Map.Entry<String, String> entry : PLATFORM_TAGS.entrySet()) {
            if (osName.contains(entry.getKey())) {
                platform = entry.getValue();
                break;
            }
        }
        String machine = System.getProperty("os.arch", "").toLowerCase();
        String arch = ARCH_TAGS.get(machine);
        if (platform == null || arch == null) {
            throw new MissingRuntimeException(
                    "no bundled dsh-jsonrpc-agent executable exists for this platform "
                            + "(os.name=" + osName + ", os.arch=" + machine + "); supported: "
                            + "linux/macos on x64/arm64. " + EXE_ACQUISITION_HINT);
        }
        return platform + "-" + arch;
    }

    private static String[] nodeLaunchArgs() {
        Path packageDir = bundledPackageDir();
        Path binJs = packageDir
                .resolve("runtime").resolve("node")
                .resolve("node_modules").resolve("@deepseek-ai")
                .resolve("dsh-sdk-jsonrpc-demo")
                .resolve("lib").resolve("packaged-bin.js");
        if (!Files.isRegularFile(binJs)) {
            throw new MissingRuntimeException(
                    "the dev-only node runtime closure is missing at "
                            + packageDir.resolve("runtime").resolve("node")
                            + " (no " + binJs + "); build the DeepSeek Harness SDK runtime, "
                            + "which builds and copies the deploy closure here. The node carrier "
                            + "is for repo-local development only — production uses the single-file exe.");
        }
        String node = findOnPath("node");
        if (node == null) {
            throw new MissingRuntimeException(
                    "the node runtime mode needs a system `node` (>=22.19) on PATH; "
                            + "install Node.js or use the exe mode");
        }
        return new String[]{node, binJs.toString()};
    }

    private static Path explicitRuntimeDir() {
        String value = System.getProperty(RUNTIME_DIR_PROPERTY);
        if (value == null || value.isBlank()) {
            value = System.getenv(RUNTIME_DIR_ENV_VAR);
        }
        if (value == null || value.isBlank()) {
            return null;
        }
        return Path.of(value).toAbsolutePath();
    }

    private static URL resource(String name) {
        return RuntimeResolver.class.getClassLoader().getResource(name);
    }

    private static String findOnPath(String executable) {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) {
            return null;
        }
        for (String dir : path.split(java.io.File.pathSeparator)) {
            if (dir.isBlank()) {
                continue;
            }
            Path candidate = Path.of(dir).resolve(executable);
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                return candidate.toString();
            }
        }
        return null;
    }

    /** Re-exported executable-bit helper used by the build hook parity tests. */
    static void requireExecutable(Path path) {
        if (!Files.isExecutable(path)) {
            throw new MissingRuntimeException("runtime executable is not executable: " + path);
        }
    }

    static Set<PosixFilePermission> executablePermissions() {
        return EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE,
                PosixFilePermission.GROUP_READ,
                PosixFilePermission.GROUP_EXECUTE,
                PosixFilePermission.OTHERS_READ,
                PosixFilePermission.OTHERS_EXECUTE);
    }

    static Set<String> supportedPlatformTags() {
        return new HashSet<>(Set.of(
                "linux-x64", "linux-arm64", "macos-arm64"));
    }
}
