package ru.syntezis.cronctl.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.syntezis.cronctl.domain.ScheduleDetails;

import java.util.concurrent.TimeUnit;

/**
 * DTO for {@link ScheduleDetails}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ScheduleDetailsDto {

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