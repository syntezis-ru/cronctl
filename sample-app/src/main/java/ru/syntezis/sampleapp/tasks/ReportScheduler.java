package ru.syntezis.sampleapp.tasks;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.syntezis.cronctl.annotation.CronctlTask;

import java.util.concurrent.TimeUnit;

/**
 * Demonstrates PACKAGE scan mode.
 *
 * Enable with:
 * <pre>
 * cronctl:
 *   scan:
 *     type: PACKAGE
 *     base-packages:
 *       - ru.syntezis.sampleapp.tasks
 * </pre>
 *
 * Only methods whose declaring class is inside base-packages are registered.
 * Schedulers in the root package (Scheduler, AnnotatedScheduler) are ignored.
 * {@code @CronctlTask.Exclude} still works in this mode.
 */
@Slf4j
@Component
public class ReportScheduler {

    @CronctlTask(
            label = "Daily Report",
            description = "Generates and sends the daily summary report",
            group = "reports"
    )
    @Scheduled(cron = "0 0 8 * * *")
    public void generateDailyReport() {
        log.info("Generating daily report");
    }

    @CronctlTask(
            label = "Weekly Report",
            description = "Generates the weekly analytics report every Monday at 9:00",
            group = "reports"
    )
    @Scheduled(cron = "0 0 9 * * MON")
    public void generateWeeklyReport() {
        log.info("Generating weekly report");
    }

    // @CronctlTask.Exclude works in PACKAGE mode too
    @CronctlTask.Exclude
    @Scheduled(fixedRate = 1L, timeUnit = TimeUnit.HOURS)
    public void archiveOldReports() {
        log.info("Archiving old reports");
    }
}
