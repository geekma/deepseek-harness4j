package com.deepseek.harness4j.test;

import com.deepseek.harness4j.client.HarnessConfig;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Test support for launching the {@link FakeRuntime} subprocess.
 *
 * <p>Python tests pass {@code (sys.executable, script_path)} as the launch override and inline
 * the fake-runtime script per test; the Java port selects the behavior via the
 * {@code FR_SCENARIO} environment variable.
 */
public final class TestRuntimes {

    private TestRuntimes() {
    }

    /** @return the argv that runs {@link FakeRuntime} in a fresh JVM on the test classpath. */
    public static String[] fakeRuntimeCommand() {
        String javaHome = System.getProperty("java.home");
        String java = Path.of(javaHome, "bin", "java").toString();
        return new String[]{java, "-cp", System.getProperty("java.class.path"),
                FakeRuntime.class.getName()};
    }

    /** @return a {@link HarnessConfig} that launches the fake runtime for a scenario. */
    public static HarnessConfig fakeConfig(String scenario, Path cwd, Map<String, String> extraEnv) {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("FR_SCENARIO", scenario);
        if (extraEnv != null) {
            env.putAll(extraEnv);
        }
        return HarnessConfig.builder()
                .launchArgsOverride(fakeRuntimeCommand())
                .cwd(cwd == null ? null : cwd.toString())
                .env(env)
                .build();
    }

    /** @return a {@link HarnessConfig} that launches the fake runtime for a scenario. */
    public static HarnessConfig fakeConfig(String scenario, Path cwd) {
        return fakeConfig(scenario, cwd, null);
    }
}
