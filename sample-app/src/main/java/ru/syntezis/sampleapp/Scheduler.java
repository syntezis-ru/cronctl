package ru.syntezis.sampleapp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.syntezis.cronctl.annotation.CronctlTask;

import java.util.concurrent.TimeUnit;

/**
 * Demonstrates AUTO scan mode (default).
 * <p>
 * All @Scheduled methods are registered except those marked with @CronctlTask.Exclude.
 * {@code @CronctlTask} is optional — without it, label/description/group get sensible defaults.
 */
@Slf4j
@Component
public class Scheduler {

    @CronctlTask(
            label = "Simple Job",
            description = "Runs every 5 minutes, reads cron from properties",
            group = "reporting",
            tags = {"reporting", "scheduled"}
    )
    @Scheduled(cron = "${schedule.cron.job-a}")
    public void runSimpleJob() {
        log.info("Simple job running");
    }

    @CronctlTask(
            label = "Heavy Job",
            description = "Long-running job, sleeps 5 seconds to simulate work",
            group = "processing",
            tags = {"processing", "heavy", "critical"}
    )
    @Scheduled(fixedRate = 10L, timeUnit = TimeUnit.MINUTES)
    public void runHeavyJob() throws InterruptedException {
        log.info("Heavy job started");
        Thread.sleep(5000L);
        log.info("Heavy job finished");
    }

    // No @CronctlTask — label defaults to "runUntaggedJob",
    // description to "Scheduler.runUntaggedJob", group to "default"
    @Scheduled(fixedRate = 30L, timeUnit = TimeUnit.SECONDS)
    public void runUntaggedJob() {
        log.info("Untagged job running");
    }

    // Excluded from cronctl in AUTO and PACKAGE modes.
    // Will never appear in the REST API.
    @CronctlTask.Exclude
    @Scheduled(initialDelay = 3L, fixedRate = 10L, timeUnit = TimeUnit.MINUTES)
    public void runInternalJob() {
        throw new RuntimeException("Internal job — not exposed via cronctl");
    }
}
