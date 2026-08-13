package com.deepseek.harness4j.build;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Port of {@code scripts/check-macos-deployment-target.py}: reject runtime executables that
 * require newer macOS than their distribution tag claims.
 */
public final class MacOsDeploymentTarget {

    private static final Pattern VERSION_PATTERN = Pattern.compile("\\d+(?:\\.\\d+)*");
    private static final Pattern MACOS_TAG_PATTERN = Pattern.compile("macosx_(\\d+)_(\\d+)_arm64");
    private static final Pattern MINOS_PATTERN = Pattern.compile("^\\s*minos\\s+(\\d+(?:\\.\\d+)*)\\s*$", Pattern.MULTILINE);

    private MacOsDeploymentTarget() {
    }

    /**
     * Parse a dot-separated numeric deployment version.
     *
     * @param value the version string
     * @return the numeric components, e.g. {@code "13.5"} → {@code (13, 5)}
     * @throws IllegalArgumentException when the value is not numeric
     */
    public static List<Integer> parseVersion(String value) {
        if (!VERSION_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid macOS deployment version: '" + value + "'");
        }
        List<Integer> parts = new java.util.ArrayList<>();
        for (String part : value.split("\\.")) {
            parts.add(Integer.parseInt(part));
        }
        return parts;
    }

    /**
     * The minimum macOS version encoded by a wheel platform tag.
     *
     * @param platformTag the tag, e.g. {@code macosx_14_0_arm64}
     * @return {@code (14, 0)}
     * @throws IllegalArgumentException when the tag is unsupported
     */
    public static List<Integer> claimedVersion(String platformTag) {
        Matcher match = MACOS_TAG_PATTERN.matcher(platformTag);
        if (!match.matches()) {
            throw new IllegalArgumentException(
                    "unsupported macOS wheel platform tag: '" + platformTag + "'");
        }
        return List.of(Integer.parseInt(match.group(1)), Integer.parseInt(match.group(2)));
    }

    /**
     * Return the newest deployment target from one or more Mach-O slices.
     *
     * <p>Port of {@code parse_otool_deployment_target(output)}: the input is the stdout of
     * {@code otool -l <executable>}.
     *
     * @param output the otool output
     * @return the newest {@code minos} version found
     * @throws IllegalArgumentException when the output contains no deployment target
     */
    public static List<Integer> parseOtoolDeploymentTarget(String output) {
        Matcher matcher = MINOS_PATTERN.matcher(output);
        List<List<Integer>> versions = new java.util.ArrayList<>();
        while (matcher.find()) {
            versions.add(parseVersion(matcher.group(1)));
        }
        if (versions.isEmpty()) {
            throw new IllegalArgumentException(
                    "otool output contains no LC_BUILD_VERSION deployment target");
        }
        List<Integer> newest = versions.get(0);
        for (List<Integer> version : versions) {
            if (compareVersion(version, newest) > 0) {
                newest = version;
            }
        }
        return newest;
    }

    /**
     * Reject an executable whose deployment target exceeds its distribution claim.
     *
     * <p>Port of {@code ensure_compatible(executable, actual, platform_tag)}.
     *
     * @param executable  the executable path, used only in the error message
     * @param actual      the measured deployment target
     * @param platformTag the distribution tag, e.g. {@code macosx_14_0_arm64}
     * @throws IllegalStateException when the executable requires a newer macOS
     */
    public static void ensureCompatible(Path executable, List<Integer> actual, String platformTag) {
        List<Integer> claimed = claimedVersion(platformTag);
        if (compareVersion(actual, claimed) > 0) {
            String rendered = String.join(".", actual.stream().map(String::valueOf).toList());
            throw new IllegalStateException(
                    executable + " requires macOS " + rendered
                            + " but the wheel claims " + platformTag);
        }
    }

    private static int compareVersion(List<Integer> left, List<Integer> right) {
        int width = Math.max(left.size(), right.size());
        for (int i = 0; i < width; i++) {
            int l = i < left.size() ? left.get(i) : 0;
            int r = i < right.size() ? right.get(i) : 0;
            if (l != r) {
                return Integer.compare(l, r);
            }
        }
        return 0;
    }
}
