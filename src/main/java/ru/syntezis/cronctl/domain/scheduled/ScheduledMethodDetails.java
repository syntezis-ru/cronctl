package ru.syntezis.cronctl.domain.scheduled;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Immutable metadata for a registered {@code @Scheduled} method:
 * its unique id, name, and resolved schedule configuration.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduledMethodDetails {

    private UUID id;
    private String methodName;
    private ScheduleDetails schedule;

}
