package ru.syntezis.cronctl.util;

import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.StringValueResolver;
import ru.syntezis.cronctl.domain.ScheduleDetails;

@UtilityClass
public class ScheduleUtils {

    public static ScheduleDetails assembleScheduleDetails(Scheduled annotation) {
        return assembleScheduleDetails(annotation, null);
    }

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

    private static String resolve(StringValueResolver resolver, String value) {
        if (resolver == null || value == null || value.isEmpty()) {
            return value;
        }

        return resolver.resolveStringValue(value);
    }
}
