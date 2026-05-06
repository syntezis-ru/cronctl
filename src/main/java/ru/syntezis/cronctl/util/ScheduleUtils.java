package ru.syntezis.cronctl.util;

import lombok.experimental.UtilityClass;
import org.springframework.scheduling.annotation.Scheduled;
import ru.syntezis.cronctl.domain.ScheduleDetails;

@UtilityClass
public class ScheduleUtils {

    public static ScheduleDetails assembleScheduleDetails(Scheduled schedule) {
        return ScheduleDetails.builder()
                .scheduler(schedule.scheduler())
                .cron(schedule.cron())
                .fixedDelay(schedule.fixedDelay())
                .fixedDelayString(schedule.fixedDelayString())
                .fixedRate(schedule.fixedRate())
                .fixedRateString(schedule.fixedRateString())
                .initialDelay(schedule.initialDelay())
                .initialDelayString(schedule.initialDelayString())
                .timeUnit(schedule.timeUnit())
                .zone(schedule.zone())
                .build();
    }
}
