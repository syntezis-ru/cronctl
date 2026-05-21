package ru.syntezis.cronctl.util;

import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.StringValueResolver;
import ru.syntezis.cronctl.domain.ScheduleDetails;

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

    private static String resolve(@Nullable StringValueResolver resolver, String value) {
        if (resolver == null || value.isEmpty()) {
            return value;
        }
        String resolved = resolver.resolveStringValue(value);
        return resolved != null ? resolved : value;
    }
}
