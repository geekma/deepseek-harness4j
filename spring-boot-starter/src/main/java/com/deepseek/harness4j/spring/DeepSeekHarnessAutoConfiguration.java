package com.deepseek.harness4j.spring;

import com.deepseek.harness4j.DeepSeekHarness;
import com.deepseek.harness4j.DeepSeekHarnessConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for the DeepSeek Harness Java SDK.
 *
 * <p>Exposes a {@link DeepSeekHarness} bean (lazy, {@code destroyMethod="close"}) and a
 * {@link DeepSeekHarnessTemplate} wrapper, configured from the {@code deepseek.harness.*}
 * properties. The runtime subprocess starts lazily on first use and is reaped when the
 * application context closes. Disable with {@code deepseek.harness.enabled=false}.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "deepseek.harness", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(DeepSeekHarnessProperties.class)
public class DeepSeekHarnessAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DeepSeekHarness deepSeekHarness(DeepSeekHarnessProperties properties) {
        DeepSeekHarnessConfig config = properties.toSdkConfig();
        return new DeepSeekHarness(config);
    }

    @Bean
    @ConditionalOnMissingBean
    public DeepSeekHarnessTemplate deepSeekHarnessTemplate(DeepSeekHarness harness) {
        return new DeepSeekHarnessTemplate(harness);
    }
}
