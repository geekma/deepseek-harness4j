package com.deepseek.harness4j.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Runnable Spring Boot MVC application over the DeepSeek Harness Java SDK.
 *
 * <p>Start with:
 * <pre>{@code
 * DEEPSEEK_API_KEY=sk-... mvn -pl spring-boot-example spring-boot:run
 * curl -X POST localhost:8080/api/harness/run \
 *   -H 'content-type: application/json' \
 *   -d '{"sessionId":"example-001","input":"Say hi."}'
 * }</pre>
 */
@SpringBootApplication
public class DeepSeekHarness4jExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeepSeekHarness4jExampleApplication.class, args);
    }
}
