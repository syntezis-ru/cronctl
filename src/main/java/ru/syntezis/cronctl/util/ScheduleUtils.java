package ru.syntezis.cronctl.util;

import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.util.StringValueResolver;
import ru.syntezis.cronctl.domain.scheduled.ScheduleDetails;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Utility for extracting schedule configuration from a {@code @Scheduled} annotation.
 */
@UtilityClass
public class ScheduleUtils {

    /**
     * Extracts schedule parameters from the annotation without resolving property placeholders.
     *
     * @param annotation the {@code @Scheduled} annotation to read
     * @return a populated {@link ScheduleDetails} instance
     */
    public static ScheduleDetails assembleScheduleDetails(Scheduled annotation) {
        return assembleScheduleDetails(annotation, null);
    }

    /**
     * Extracts schedule parameters from the annotation, resolving any property placeholders
     * in string fields (e.g. {@code cron}, {@code fixedRateString}) using the provided resolver.
     *
     * @param annotation the {@code @Scheduled} annotation to read
     * @param resolver   resolver for {@code ${...}} placeholders; {@code null} means no resolution
     * @return a populated {@link ScheduleDetails} instance with resolved string values
     */
    public static ScheduleDetails assembleScheduleDetails(Scheduled annotation, @Nullable StringValueResolver resolver) {
        return ScheduleDetails.builder()
                .scheduler(resolve(resolver, annotation.scheduler()))
                .cron(resolve(resolver, annotation.cron()))
                .fixedDelay(annotation.fixedDelay())
                .fixedDelayString(resolve(resolver, annotation.fixedDelayString()))
                .fixedRate(annotation.fixedRate())
                .fixedRateString(resolve(resolver, annotation.fixedRateString()))
                .initialDelay(annotation.initialDelay())
                .initialDelayString(resolve(resolver, annotation.initialDelayString()))
                .timeUnit(annotation.timeUnit())
                .zone(resolve(resolver, annotation.zone()))
                .build();
    }

    /**
     * Computes the next scheduled execution time from a {@link ScheduleDetails}.
     * Only supported for cron expressions — returns {@code null} for fixedRate / fixedDelay tasks,
     * since those require knowing the last run time.
     *
     * @param schedule the schedule configuration to evaluate
     * @return next execution timestamp, or {@code null} if not computable
     */
    public static @Nullable Instant computeNextExecutionAt(ScheduleDetails schedule) {
        String cron = schedule.getCron();
        if (cron == null || cron.isEmpty()) {
            return null;
        }
        try {
            CronExpression expression = CronExpression.parse(cron);
            String zone = schedule.getZone();
            ZoneId zoneId = (zone != null && !zone.isEmpty()) ? ZoneId.of(zone) : ZoneId.systemDefault();
            ZonedDateTime next = expression.next(ZonedDateTime.now(zoneId));
            return next != null ? next.toInstant() : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String resolve(@Nullable StringValueResolver resolver, String value) {
        if (resolver == null || value.isEmpty()) {
            return value;
        }

        String resolved = resolver.resolveStringValue(value);
        return resolved != null ? resolved : value;
    }
}
