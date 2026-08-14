package com.deepseek.harness4j.cordis;

import com.deepseek.harness4j.DeepSeekHarness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CordisDslTest {

    @TempDir
    Path tmp;

    @Test
    void test_cordis_config_builder_generates_valid_yaml() throws IOException {
        CordisConfig config = CordisConfig.builder()
                .model("deepseek-v4-flash")
                .sessionRoot("/data/sessions")
                .compression(CordisConfig.CompressionMode.NONE)
                .sandboxPolicy(SandboxPolicy.builder()
                        .mode(SandboxPolicy.MODE_RESTRICTED)
                        .allowNetwork(false)
                        .build())
                .customPersona("You are a test agent")
                .build();

        String yaml = config.toYaml();
        assertNotNull(yaml);
        assertTrue(yaml.contains("sdk-jsonrpc-server"));
        assertTrue(yaml.contains("llm-deepseek"));
        assertTrue(yaml.contains("deepseek-v4-flash"));
        assertTrue(yaml.contains("restricted"));
        assertTrue(yaml.contains("compression: none"));
        assertTrue(yaml.contains("/data/sessions"));
        assertTrue(yaml.contains("You are a test agent"));

        Path tempFile = config.toTempFile();
        assertTrue(Files.exists(tempFile));
        String read = Files.readString(tempFile);
        assertTrue(read.contains("sdk-jsonrpc-server"));
    }

    @Test
    void test_cordis_minimal_factory() {
        CordisConfig minimal = CordisConfig.minimal();
        String yaml = minimal.toYaml();
        assertTrue(yaml.contains("persistent-bash"));
        assertTrue(yaml.contains("str-replace-editor"));
        assertTrue(yaml.contains("compression: none"));
    }

    @Test
    void test_deepseek_harness_create_minimal_factory() {
        try (DeepSeekHarness harness = DeepSeekHarness.createMinimal(tmp)) {
            assertNotNull(harness);
            assertTrue(harness.cwd().contains(tmp.getFileName().toString()));
            assertNotNull(harness.config().cordis());
        }
    }
}
