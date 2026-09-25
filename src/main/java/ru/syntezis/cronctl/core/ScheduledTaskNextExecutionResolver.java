package ru.syntezis.cronctl.core;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Resolves {@link ScheduledTask} execution time on Spring Framework 6.0 and newer. */
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Slf4j
public class ScheduledTaskNextExecutionResolver {

    private final Clock clock;

    @Nullable
    private final Method nextExecutionMethod;

    @Nullable
    private final Field futureField;

    public ScheduledTaskNextExecutionResolver(Clock clock) {
        this(
                clock,
                ReflectionUtils.findMethod(ScheduledTask.class, "nextExecution"),
                ReflectionUtils.findField(ScheduledTask.class, "future")
        );
    }

    /** Returns the next execution instant, or {@code null} when Spring cannot provide one. */
    public @Nullable Instant resolve(ScheduledTask scheduledTask) {
        Method method = nextExecutionMethod;
        if (method != null) {
            return invokeNextExecution(method, scheduledTask);
        }
        return resolveFromFuture(scheduledTask);
    }

    private @Nullable Instant invokeNextExecution(Method method, ScheduledTask scheduledTask) {
        try {
            return (Instant) ReflectionUtils.invokeMethod(method, scheduledTask);
        } catch (RuntimeException e) {
            log.debug("Cannot resolve next execution through ScheduledTask.nextExecution()", e);
            return null;
        }
    }

    private @Nullable Instant resolveFromFuture(ScheduledTask scheduledTask) {
        Field field = futureField;
        if (field == null) {
            return null;
        }

        try {
            ReflectionUtils.makeAccessible(field);
            Object value = ReflectionUtils.getField(field, scheduledTask);
            if (!(value instanceof ScheduledFuture<?> future) || future.isCancelled() || future.isDone()) {
                return null;
            }
            long delayMillis = future.getDelay(TimeUnit.MILLISECONDS);
            return delayMillis < 0 ? null : Instant.now(clock).plusMillis(delayMillis);
        } catch (RuntimeException e) {
            log.debug("Cannot resolve next execution from ScheduledTask future", e);
            return null;
        }
    }

}
