package ru.syntezis.cronctl.annotation;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * Marks a {@code @Scheduled} method for registration in the cronctl REST API.
 *
 * <p>All attributes are optional. When absent, cronctl derives defaults:
 * method name as label, {@code ClassName.methodName} as description, {@code "default"} as group.
 *
 * <p>In {@code AUTO} and {@code PACKAGE} scan modes this annotation is not required.
 * In {@code ANNOTATED} mode only methods carrying this annotation are registered.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CronctlTask {

    /** Display label shown in the API response. Defaults to the method name when blank. */
    @AliasFor("label")
    String value() default "";

    /** Display label shown in the API response. Defaults to the method name when blank. */
    @AliasFor("value")
    String label() default "";

    /** Human-readable description. Defaults to {@code ClassName.methodName} when blank. */
    String description() default "";

    /** Logical group for categorisation. Defaults to {@code "default"} when blank. */
    String group() default "default";

    /** Arbitrary tags for filtering or categorisation. */
    String[] tags() default {};

    /**
     * Task-level execution timeout. {@code 0} means use the global
     * {@code cronctl.executor.timeout-seconds} setting.
     */
    long timeout() default 0;

    /** Time unit for {@link #timeout()}. */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * Prevents the annotated {@code @Scheduled} method from being registered in cronctl.
     *
     * <p>Effective in {@code AUTO} and {@code PACKAGE} scan modes.
     * In {@code ANNOTATED} mode, {@code @CronctlTask} takes precedence over this annotation.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @interface Exclude {

    }
}
