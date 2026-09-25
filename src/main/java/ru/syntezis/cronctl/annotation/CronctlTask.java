package ru.syntezis.cronctl.annotation;

import org.springframework.core.annotation.AliasFor;
import ru.syntezis.cronctl.enums.ConcurrencyPolicy;
import ru.syntezis.cronctl.enums.RetryBackoff;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
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

    /** Disable the execution timeout for this task. */
    long NO_TIMEOUT = -1L;

    /** Inherit the global {@code cronctl.executor.timeout-seconds} setting. */
    long USE_GLOBAL_TIMEOUT = 0L;

    /**
     * Stable task identifier exposed as {@code task_key} in the REST API.
     *
     * <p>When blank, cronctl derives the key from the application name, Spring bean name,
     * and scheduled method signature.
     *
     * @return the stable task key, or empty string to derive one automatically
     */
    String id() default "";

    /**
     * Display label shown in the API response. Defaults to the method name when blank.
     *
     * @return the display label or empty string to use the method name
     */
    @AliasFor("label")
    String value() default "";

    /**
     * Display label shown in the API response. Defaults to the method name when blank.
     *
     * @return the display label or empty string to use the method name
     */
    @AliasFor("value")
    String label() default "";

    /**
     * Human-readable description. Defaults to {@code ClassName.methodName} when blank.
     *
     * @return the description, or empty string to use the class and method name
     */
    String description() default "";

    /**
     * Logical group for categorization. Defaults to {@code "default"} when blank.
     *
     * @return the group name
     */
    String group() default "default";

    /**
     * Arbitrary tags for filtering or categorization.
     *
     * @return array of tags
     */
    String[] tags() default {};

    /**
     * Task-level execution timeout.
     *
     * <p>{@link #USE_GLOBAL_TIMEOUT} ({@code 0}) inherits
     * {@code cronctl.executor.timeout-seconds}, {@link #NO_TIMEOUT} ({@code -1}) disables
     * the timeout, and a positive value defines a task-specific timeout.
     *
     * @return the timeout value, {@link #USE_GLOBAL_TIMEOUT}, or {@link #NO_TIMEOUT}
     */
    long timeout() default USE_GLOBAL_TIMEOUT;

    /**
     * Time unit for {@link #timeout()}.
     *
     * @return the time unit for the timeout value
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * Allows toggling the task on and off.
     *
     * @return true if toggling is enabled
     */
    boolean togglingEnabled() default false;

    /**
     * Policy applied when executions of this task overlap.
     *
     * <p>The policy is process-local and applies jointly to automatic and manual executions.
     *
     * @return the concurrency policy
     */
    ConcurrencyPolicy concurrency() default ConcurrencyPolicy.ALLOW;

    /**
     * Maximum number of executions admitted at the same time.
     *
     * <p>The value must be positive. It is exposed but not enforced when
     * {@link #concurrency()} is {@link ConcurrencyPolicy#ALLOW}.
     *
     * @return the positive concurrent execution limit
     */
    int maxConcurrentExecutions() default 1;

    /** Number of automatic retries after the original failed execution. */
    int retries() default 0;

    /** Base ISO-8601 delay used for automatic retries. */
    String retryDelay() default "PT1S";

    /** Backoff strategy used to calculate subsequent retry delays. */
    RetryBackoff retryBackoff() default RetryBackoff.FIXED;

    /** Maximum ISO-8601 delay after backoff and jitter are applied. */
    String maxRetryDelay() default "PT5M";

    /** Symmetric delay jitter from {@code 0.0} to {@code 1.0}. */
    double retryJitter() default 0.0;

    /** Exception types eligible for retry. An empty list means every {@link Exception}. */
    Class<? extends Throwable>[] retryOn() default {};

    /** Exception types that must never be retried. These take precedence over {@link #retryOn()}. */
    Class<? extends Throwable>[] nonRetryableOn() default {};

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
