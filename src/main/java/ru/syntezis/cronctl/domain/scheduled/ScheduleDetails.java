package ru.syntezis.cronctl.domain.scheduled;

import lombok.Builder;
import lombok.Data;

import java.util.concurrent.TimeUnit;

/**
 * Raw schedule parameters extracted from a {@code @Scheduled} annotation.
 *
 * <p>Numeric fields ({@code fixedRate}, {@code fixedDelay}, {@code initialDelay}) hold
 * {@code -1} when not set, matching the annotation defaults.
 * String variants ({@code fixedRateString}, etc.) hold an empty string when not set.
 * Property placeholders in string fields are resolved before this object is created.
 */
@Data
@Builder
public class ScheduleDetails {

    private String cron;
    private String zone;
    private long fixedRate;
    private String fixedRateString;
    private long fixedDelay;
    private String fixedDelayString;
    private long initialDelay;
    private String initialDelayString;
    private TimeUnit timeUnit;
    private String scheduler;

}
