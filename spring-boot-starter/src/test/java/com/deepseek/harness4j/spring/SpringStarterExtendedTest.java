package com.deepseek.harness4j.spring;

import com.deepseek.harness4j.DeepSeekHarness;
import com.deepseek.harness4j.DeepSeekHarnessConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SpringStarterExtendedTest {

    @TempDir
    Path tmp;

    @Test
    void test_template_offline_log_delegation() throws Exception {
        Path sessionRoot = tmp.resolve(".sessions");
        Path sessDir = sessionRoot.resolve("proj").resolve("sess-spring");
        Files.createDirectories(sessDir);

        String jsonl = "{\"type\":\"session\",\"version\":1,\"id\":\"sess-spring\",\"cwd\":\"/w\",\"parentSession\":null,\"createdAt\":1000}\n"
                + "{\"type\":\"user/message\",\"time\":1010,\"data\":{\"content\":[{\"type\":\"text\",\"text\":\"hello spring\"}]}}\n"
                + "{\"type\":\"assistant/message\",\"time\":1020,\"data\":{\"content\":[{\"type\":\"text\",\"text\":\"hello world\"}]}}\n";

        Files.writeString(sessDir.resolve("session.jsonl"), jsonl, StandardCharsets.UTF_8);

        DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
                .cwd(tmp.toString())
                .sessionRoot(sessionRoot.toString())
                .build());

        try (DeepSeekHarnessTemplate template = new DeepSeekHarnessTemplate(harness)) {
            assertThat(template.listSessions()).hasSize(1);
            assertThat(template.readSessionLog("sess-spring")).hasSize(2);
            assertThat(template.replaySessionLog("sess-spring")).hasSize(2);
            assertThat(template.forkSession("sess-spring", "sess-spring-fork").id()).isEqualTo("sess-spring-fork");
        }
    }
}
