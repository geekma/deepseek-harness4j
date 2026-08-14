package com.deepseek.harness4j.tool;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Java method as an invokable agent tool accessible by DeepSeek Harness.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface HarnessTool {

    /** The unique tool name exposed to the LLM (e.g. {@code "query_user"}). Defaults to method name. */
    String name() default "";

    /** Human and LLM readable description of what the tool does. */
    String description() default "";
}
