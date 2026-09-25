package ru.syntezis.cronctl.domain.task;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import ru.syntezis.cronctl.enums.RetryBackoff;

import java.time.Duration;
import java.util.List;

/** Immutable task-level retry policy resolved from {@code @CronctlTask}. */
@Value
@Builder
public class RetryPolicy {

    public static final Duration DEFAULT_DELAY = Duration.ofSeconds(1);
    public static final Duration DEFAULT_MAX_DELAY = Duration.ofMinutes(5);

    @Builder.Default
    int retries = 0;

    @Builder.Default
    Duration delay = DEFAULT_DELAY;

    @Builder.Default
    RetryBackoff backoff = RetryBackoff.FIXED;

    @Builder.Default
    Duration maxDelay = DEFAULT_MAX_DELAY;

    @Builder.Default
    double jitter = 0.0;

    @Singular("retryOnType")
    List<Class<? extends Throwable>> retryOn;

    @Singular("nonRetryableOnType")
    List<Class<? extends Throwable>> nonRetryableOn;

    public static RetryPolicy disabled() {
        return RetryPolicy.builder().build();
    }

}
