package com.deepseek.harness4j.tool;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Metadata for a parameter of a {@link HarnessTool} method.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface Param {

    /** Name of the argument in JSON schema. */
    String value() default "";

    /** Name alias of the argument in JSON schema. */
    String name() default "";

    /** Description of the parameter. */
    String description() default "";

    /** Whether the parameter is required. Defaults to true. */
    boolean required() default true;
}
