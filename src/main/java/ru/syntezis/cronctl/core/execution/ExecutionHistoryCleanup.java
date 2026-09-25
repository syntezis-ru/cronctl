package ru.syntezis.cronctl.core.execution;

import jakarta.annotation.PreDestroy;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.InitializingBean;
import ru.syntezis.cronctl.properties.CronctlProperties;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Periodically removes expired terminal execution records from the configured store. */
@Slf4j
@RequiredArgsConstructor
public class ExecutionHistoryCleanup implements InitializingBean {

    private final ExecutionStore executionStore;
    private final CronctlProperties.History properties;

    @Setter(AccessLevel.PACKAGE)
    private Clock clock = Clock.systemUTC();

    @Setter(AccessLevel.PACKAGE)
    private ScheduledExecutorService scheduler = newCleanupScheduler();

    @Override
    public void afterPropertiesSet() {
        validateProperties();
        cleanupSafely();
        scheduler.scheduleWithFixedDelay(
                this::cleanupSafely,
                properties.getCleanupInterval().toMillis(),
                properties.getCleanupInterval().toMillis(),
                TimeUnit.MILLISECONDS
        );
    }

    void cleanupNow() {
        Instant threshold = clock.instant().minus(properties.getRetention());
        executionStore.deleteExpired(threshold);
    }

    private void cleanupSafely() {
        try {
            cleanupNow();
        } catch (RuntimeException e) {
            log.warn("Failed to clean up expired cronctl execution history", e);
        }
    }

    private void validateProperties() {
        if (properties.getMaxEntries() < 1) {
            throw new IllegalArgumentException("cronctl.history.max-entries must be greater than zero");
        }
        validateDuration("cronctl.history.retention", properties.getRetention());
        validateDuration("cronctl.history.cleanup-interval", properties.getCleanupInterval());
    }

    private void validateDuration(String propertyName, @Nullable Duration value) {
        if (value == null || value.isZero() || value.isNegative() || value.toMillis() < 1) {
            throw new IllegalArgumentException(propertyName + " must be at least 1 millisecond");
        }
    }

    private static ScheduledExecutorService newCleanupScheduler() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "cronctl-history-cleanup");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Stops the dedicated cleanup scheduler. */
    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }

}
