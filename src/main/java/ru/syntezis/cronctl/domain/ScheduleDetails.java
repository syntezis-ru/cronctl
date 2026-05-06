package ru.syntezis.cronctl.domain;

import lombok.Builder;
import lombok.Data;

import java.util.concurrent.TimeUnit;

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
