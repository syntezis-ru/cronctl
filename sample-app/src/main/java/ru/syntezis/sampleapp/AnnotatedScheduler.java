package ru.syntezis.sampleapp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.syntezis.cronctl.annotation.CronctlTask;

import java.util.concurrent.TimeUnit;

/**
 * Demonstrates ANNOTATED scan mode.
 * <p>
 * Enable with:
 * <pre>
 * cronctl:
 *   scan:
 *     type: ANNOTATED
 * </pre>
 *
 * Only methods explicitly annotated with @CronctlTask are registered.
 * sendNotification() is registered; internalSync() is invisible to cronctl.
 */
@Slf4j
@Component
public class AnnotatedScheduler {

    @CronctlTask(
            label = "Send Notifications",
            description = "Dispatches pending notifications to users",
            group = "notifications"
    )
    @Scheduled(fixedRate = 1L, timeUnit = TimeUnit.MINUTES)
    public void sendNotification() {
        log.info("Sending notifications");
    }

    @CronctlTask(label = "Cleanup Expired Sessions", group = "maintenance")
    @Scheduled(fixedRate = 15L, timeUnit = TimeUnit.MINUTES)
    public void cleanupExpiredSessions() {
        log.info("Cleaning up expired sessions");
    }

    // No @CronctlTask — invisible to cronctl in ANNOTATED mode.
    // In AUTO or PACKAGE mode this would be registered with default metadata.
    @Scheduled(fixedRate = 5L, timeUnit = TimeUnit.MINUTES)
    public void internalSync() {
        log.info("Internal sync running");
    }
}
