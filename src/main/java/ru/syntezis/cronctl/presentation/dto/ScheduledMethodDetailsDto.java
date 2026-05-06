package ru.syntezis.cronctl.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.syntezis.cronctl.domain.ScheduledMethodDetails;

import java.util.UUID;

/**
 * DTO for {@link ScheduledMethodDetails}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ScheduledMethodDetailsDto {

    private UUID id;
    private String methodName;
    private ScheduleDetailsDto schedule;

}