package com.deepseek.harness4j.spring;

import com.deepseek.harness4j.DeepSeekHarness;
import com.deepseek.harness4j.DeepSeekHarnessConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the README "Spring Boot Integration" example: {@code application.yml} values
 * ({@code deepseek.harness.*}) bind to {@link DeepSeekHarnessProperties} and flow through
 * {@link DeepSeekHarnessProperties#toSdkConfig()} into the auto-configured
 * {@link DeepSeekHarness} / {@link DeepSeekHarnessTemplate} beans.
 */
class SpringStarterTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DeepSeekHarnessAutoConfiguration.class));

    @Test
    void test_application_yml_binds_to_properties() {
        contextRunner
                .withPropertyValues(
                        "deepseek.harness.provider=deepseek-official",
                        "deepseek.harness.model=deepseek-v4-flash",
                        "deepseek.harness.cwd=/absolute/path/to/workspace",
                        "deepseek.harness.sessionRoot=/absolute/path/to/sessions",
                        "deepseek.harness.requestTimeoutSeconds=300",
                        "deepseek.harness.apiKey=spring-key")
                .run(context -> {
                    DeepSeekHarnessProperties properties =
                            context.getBean(DeepSeekHarnessProperties.class);
                    assertThat(properties.getProvider()).isEqualTo("deepseek-official");
                    assertThat(properties.getModel()).isEqualTo("deepseek-v4-flash");
                    assertThat(properties.getCwd()).isEqualTo("/absolute/path/to/workspace");
                    assertThat(properties.getSessionRoot()).isEqualTo("/absolute/path/to/sessions");
                    assertThat(properties.getRequestTimeoutSeconds()).isEqualTo(300.0);
                    assertThat(properties.getApiKey()).isEqualTo("spring-key");
                });
    }

    @Test
    void test_properties_map_into_sdk_config() {
        DeepSeekHarnessProperties properties = new DeepSeekHarnessProperties();
        properties.setProvider("deepseek-official");
        properties.setModel("deepseek-v4-flash");
        properties.setCwd("/workspace");
        properties.setSessionRoot("/sessions");
        properties.setRequestTimeoutSeconds(300.0);
        properties.setApiKey("spring-key");

        DeepSeekHarnessConfig config = properties.toSdkConfig();
        assertThat(config.provider()).isEqualTo("deepseek-official");
        assertThat(config.model()).isEqualTo("deepseek-v4-flash");
        assertThat(config.cwd()).isEqualTo("/workspace");
        assertThat(config.sessionRoot()).isEqualTo("/sessions");
        assertThat(config.requestTimeoutSeconds()).isEqualTo(300.0);
        assertThat(config.apiKey()).isEqualTo("spring-key");
        assertThat(config.shutdownTimeoutSeconds()).isEqualTo(1.0);
    }

    @Test
    void test_defaults_match_readme() {
        DeepSeekHarnessConfig config = new DeepSeekHarnessProperties().toSdkConfig();
        assertThat(config.provider()).isEqualTo("deepseek-official");
        assertThat(config.model()).isEqualTo("deepseek-v4-flash");
        assertThat(config.shutdownTimeoutSeconds()).isEqualTo(1.0);
    }

    @Test
    void test_auto_configuration_exposes_template_bean() {
        contextRunner
                .withPropertyValues(
                        "deepseek.harness.cwd=./workspace",
                        "deepseek.harness.sessionRoot=./sessions")
                .run(context -> {
                    assertThat(context).hasSingleBean(DeepSeekHarness.class);
                    assertThat(context).hasSingleBean(DeepSeekHarnessTemplate.class);
                    assertThat(context.getBean(DeepSeekHarness.class).config().cwd()).isEqualTo("./workspace");
                });
    }

    @Test
    void test_disabled_flag_suppresses_beans() {
        contextRunner
                .withPropertyValues("deepseek.harness.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(DeepSeekHarness.class);
                    assertThat(context).doesNotHaveBean(DeepSeekHarnessTemplate.class);
                });
    }
}