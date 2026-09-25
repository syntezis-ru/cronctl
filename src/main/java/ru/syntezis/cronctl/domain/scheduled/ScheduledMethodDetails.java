package ru.syntezis.cronctl.domain.scheduled;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Immutable metadata for a registered {@code @Scheduled} method:
 * its stable task key, name, and resolved schedule configuration.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduledMethodDetails {

    private String taskKey;
    private String methodName;
    private ScheduleDetails schedule;

}
