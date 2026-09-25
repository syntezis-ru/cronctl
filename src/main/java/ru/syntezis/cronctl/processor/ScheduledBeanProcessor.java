package ru.syntezis.cronctl.processor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.StringValueResolver;
import ru.syntezis.cronctl.annotation.CronctlTask;
import ru.syntezis.cronctl.core.execution.ScheduledExecutionObservationHandler;
import ru.syntezis.cronctl.domain.scheduled.ScheduledMethodDetails;
import ru.syntezis.cronctl.domain.scheduled.ScheduledMethodReference;
import ru.syntezis.cronctl.domain.task.RetryPolicy;
import ru.syntezis.cronctl.domain.task.Task;
import ru.syntezis.cronctl.enums.AutomaticTrackingStatus;
import ru.syntezis.cronctl.enums.ConcurrencyPolicy;
import ru.syntezis.cronctl.util.ScheduleUtils;

import java.lang.reflect.Method;
import java.time.DateTimeException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Builds a fully-populated {@link Task} from a discovered {@code @Scheduled} method.
 */
@RequiredArgsConstructor
@Slf4j
public class ScheduledBeanProcessor {

    private static final String DEFAULT_APPLICATION_NAME = "application";

    private final String applicationName;

    /**
     * Creates a {@link Task} capturing the method's identity, schedule configuration,
     * and a reference to the bean instance needed for execution.
     *
     * @param bean            the Spring bean that owns the method
     * @param beanName        name of the bean in the application context
     * @param scheduledMethod the reflective {@link Method} annotated with {@code @Scheduled}
     * @param resolver        resolver for property placeholders in annotation attributes; may be {@code null}
     * @return a fully-populated {@link Task}
     */
    public Task process(Object bean, String beanName, Method scheduledMethod, @Nullable StringValueResolver resolver) {
        String methodName = scheduledMethod.getName();
        @Nullable CronctlTask cronctlTaskAnnotation = scheduledMethod.getAnnotation(CronctlTask.class);
        Optional<CronctlTask> taskAnnotation = Optional.ofNullable(cronctlTaskAnnotation);
        String taskKey = resolveTaskKey(cronctlTaskAnnotation, beanName, scheduledMethod, resolver);

        log.debug("Found scheduled method: {}, in class: {}, with bean name: {}. Adding to registry with task key: {}",
                methodName, bean.getClass(), beanName, taskKey);

        String defaultDescription = scheduledMethod.getDeclaringClass().getSimpleName() + "." + methodName;

        Scheduled annotation = scheduledMethod.getAnnotation(Scheduled.class);
        boolean automaticTrackingSupported = ScheduledExecutionObservationHandler.isSupported();
        return Task.builder()
                .label(taskAnnotation.map(CronctlTask::label)
                        .filter(s -> !s.isEmpty())
                        .orElse(methodName)
                )
                .description(taskAnnotation.map(CronctlTask::description)
                        .filter(s -> !s.isEmpty())
                        .orElse(defaultDescription)
                )
                .group(taskAnnotation.map(CronctlTask::group)
                        .filter(s -> !s.isEmpty())
                        .orElse("default")
                )
                .tags(taskAnnotation.map(t -> Arrays.stream(t.tags()).toList()).orElse(List.of()))
                .enabled(true)
                .togglingEnabled(taskAnnotation.map(CronctlTask::togglingEnabled).orElse(false))
                .timeoutSeconds(taskAnnotation.map(this::resolveTimeoutSeconds)
                        .orElse(CronctlTask.USE_GLOBAL_TIMEOUT))
                .concurrencyPolicy(taskAnnotation.map(CronctlTask::concurrency)
                        .orElse(ConcurrencyPolicy.ALLOW))
                .maxConcurrentExecutions(taskAnnotation.map(this::resolveMaxConcurrentExecutions)
                        .orElse(1))
                .retryPolicy(taskAnnotation.map(cronctlTask -> resolveRetryPolicy(cronctlTask, resolver))
                        .orElseGet(RetryPolicy::disabled))
                .details(ScheduledMethodDetails.builder()
                        .taskKey(taskKey)
                        .schedule(ScheduleUtils.assembleScheduleDetails(annotation, resolver))
                        .methodName(methodName)
                        .build()
                )
                .reference(
                        ScheduledMethodReference.builder()
                                .beanName(beanName)
                                .bean(bean)
                                .method(scheduledMethod)
                                .build()
                )
                .automaticTrackingStatus(automaticTrackingSupported
                        ? AutomaticTrackingStatus.ACTIVE
                        : AutomaticTrackingStatus.UNAVAILABLE)
                .automaticTrackingMessage(automaticTrackingSupported
                        ? null
                        : "Automatic scheduled execution history requires Spring Framework 6.1 or later")
                .build();
    }

    private String resolveTaskKey(@Nullable CronctlTask taskAnnotation, String beanName,
                                  Method scheduledMethod, @Nullable StringValueResolver resolver) {
        Optional<String> explicitTaskKey = Optional.ofNullable(taskAnnotation)
                .map(CronctlTask::id)
                .map(id -> resolver == null ? id : resolver.resolveStringValue(id))
                .map(String::trim)
                .filter(id -> !id.isEmpty());
        if (explicitTaskKey.isPresent()) {
            return explicitTaskKey.get();
        }

        String trimmedApplicationName = applicationName.trim();
        String resolvedApplicationName = trimmedApplicationName.isEmpty()
                ? DEFAULT_APPLICATION_NAME
                : trimmedApplicationName;
        return resolvedApplicationName + "." + beanName + "." + methodSignature(scheduledMethod);
    }

    private String methodSignature(Method method) {
        String parameterTypes = Arrays.stream(method.getParameterTypes())
                .map(Class::getName)
                .collect(Collectors.joining(","));
        return method.getName() + "(" + parameterTypes + ")";
    }

    private long resolveTimeoutSeconds(CronctlTask taskAnnotation) {
        long timeout = taskAnnotation.timeout();
        if (timeout < CronctlTask.NO_TIMEOUT) {
            throw new IllegalArgumentException("Cronctl task timeout must be -1, 0, or a positive value");
        }

        if (timeout == CronctlTask.NO_TIMEOUT || timeout == CronctlTask.USE_GLOBAL_TIMEOUT) {
            return timeout;
        }

        long timeoutSeconds = taskAnnotation.timeUnit().toSeconds(timeout);
        if (timeoutSeconds == CronctlTask.USE_GLOBAL_TIMEOUT) {
            throw new IllegalArgumentException("Cronctl task timeout must be at least one second");
        }

        return timeoutSeconds;
    }

    private int resolveMaxConcurrentExecutions(CronctlTask taskAnnotation) {
        int maxConcurrentExecutions = taskAnnotation.maxConcurrentExecutions();
        if (maxConcurrentExecutions < 1) {
            throw new IllegalArgumentException("Cronctl task maxConcurrentExecutions must be positive");
        }
        return maxConcurrentExecutions;
    }

    private RetryPolicy resolveRetryPolicy(CronctlTask taskAnnotation, @Nullable StringValueResolver resolver) {
        int retries = taskAnnotation.retries();
        if (retries < 0) {
            throw new IllegalArgumentException("Cronctl task retries must not be negative");
        }

        Duration delay = parseDuration(taskAnnotation.retryDelay(), "retryDelay", resolver);
        Duration maxDelay = parseDuration(taskAnnotation.maxRetryDelay(), "maxRetryDelay", resolver);
        if (delay.isNegative()) {
            throw new IllegalArgumentException("Cronctl task retryDelay must not be negative");
        }
        if (maxDelay.isNegative() || maxDelay.isZero()) {
            throw new IllegalArgumentException("Cronctl task maxRetryDelay must be positive");
        }
        if (maxDelay.compareTo(delay) < 0) {
            throw new IllegalArgumentException("Cronctl task maxRetryDelay must not be shorter than retryDelay");
        }

        double jitter = taskAnnotation.retryJitter();
        if (jitter < 0.0 || jitter > 1.0) {
            throw new IllegalArgumentException("Cronctl task retryJitter must be between 0.0 and 1.0");
        }

        return RetryPolicy.builder()
                .retries(retries)
                .delay(delay)
                .backoff(taskAnnotation.retryBackoff())
                .maxDelay(maxDelay)
                .jitter(jitter)
                .retryOn(Arrays.asList(taskAnnotation.retryOn()))
                .nonRetryableOn(Arrays.asList(taskAnnotation.nonRetryableOn()))
                .build();
    }

    private Duration parseDuration(String value, String attributeName, @Nullable StringValueResolver resolver) {
        String resolved = resolver == null ? value : resolver.resolveStringValue(value);
        if (resolved == null) {
            throw new IllegalArgumentException("Cronctl task " + attributeName + " must resolve to an ISO-8601 duration");
        }
        try {
            return Duration.parse(resolved.trim());
        } catch (DateTimeException e) {
            throw new IllegalArgumentException(
                    "Cronctl task " + attributeName + " must be an ISO-8601 duration", e
            );
        }
    }
}
