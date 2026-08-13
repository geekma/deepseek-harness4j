package com.deepseek.harness4j.build;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Port of the version-handling functions of {@code scripts/build-python-release.py}:
 * {@code repository_version}, {@code validate_release_tag}, and {@code pep440_version}.
 *
 * <p>The Python release pipeline reads the repository version from the root {@code package.json};
 * the Java port reads the root Maven {@code pom.xml} {@code <version>} instead. The PEP 440
 * spelling helper is kept verbatim because the Java runtime-bin distribution may still need to
 * mirror Python wheel naming for the bundled runtime carriers.
 */
public final class ReleaseVersion {

    private static final Pattern VERSION_PATTERN =
            Pattern.compile("\\d+\\.\\d+\\.\\d+(?:-[0-9A-Za-z.]+)?");
    private static final Pattern PRERELEASE_PATTERN =
            Pattern.compile("(a|b|c|rc|alpha|beta|pre|preview)\\.?(\\d+)");
    private static final Pattern POM_VERSION_PATTERN =
            Pattern.compile("<version>([^<]+)</version>");

    private ReleaseVersion() {
    }

    /**
     * The repository version declared by the root {@code pom.xml} {@code <version>} element.
     *
     * @param pom the repository root {@code pom.xml}
     * @return the version, which must be {@code X.Y.Z} with an optional prerelease segment
     * @throws IllegalArgumentException when the pom is unreadable or the version is malformed
     */
    public static String repositoryVersion(Path pom) {
        String text;
        try {
            text = Files.readString(pom);
        } catch (Exception error) {
            throw new IllegalArgumentException("could not read repository version from " + pom, error);
        }
        Matcher matcher = POM_VERSION_PATTERN.matcher(text);
        if (!matcher.find()) {
            throw new IllegalArgumentException(
                    pom + " must declare a <version> element, got: " + pom.getFileName());
        }
        String version = matcher.group(1).trim();
        if (!VERSION_PATTERN.matcher(version).matches()) {
            throw new IllegalArgumentException(
                    pom + " version must be X.Y.Z with an optional prerelease segment, got '" + version + "'");
        }
        return version;
    }

    /**
     * The Python spelling of a repository version.
     *
     * <p>A release candidate is {@code 0.0.1-rc.1} in the repository and {@code 0.0.1rc1} under
     * PEP 440. Build backends normalize to the latter, so the wheel filename and metadata carry
     * it: comparing them against the repository spelling would reject every prerelease build.
     *
     * @param version the repository version
     * @return the PEP 440 spelling
     * @throws IllegalArgumentException when the prerelease segment has no PEP 440 spelling
     */
    public static String pep440Version(String version) {
        int separatorIndex = version.indexOf('-');
        if (separatorIndex < 0) {
            return version;
        }
        String stable = version.substring(0, separatorIndex);
        String prerelease = version.substring(separatorIndex + 1);
        Matcher match = PRERELEASE_PATTERN.matcher(prerelease);
        if (!match.matches()) {
            throw new IllegalArgumentException(
                    "prerelease segment '" + prerelease
                            + "' has no PEP 440 spelling; use rc.N, alpha.N, or beta.N");
        }
        String identifier = switch (match.group(1)) {
            case "alpha" -> "a";
            case "beta" -> "b";
            case "c", "pre", "preview" -> "rc";
            default -> match.group(1);
        };
        return stable + identifier + match.group(2);
    }

    /**
     * Validate an optional release tag against the repository version.
     *
     * @param tag     the release tag, or {@code null} for a non-release build
     * @param version the repository version
     * @throws IllegalArgumentException when the tag does not equal {@code python-v<version>}
     */
    public static void validateReleaseTag(String tag, String version) {
        if (tag == null) {
            return;
        }
        String expected = "python-v" + version;
        if (!expected.equals(tag)) {
            throw new IllegalArgumentException(
                    "release tag must match repository version: expected '" + expected
                            + "', got '" + tag + "'");
        }
    }
}
