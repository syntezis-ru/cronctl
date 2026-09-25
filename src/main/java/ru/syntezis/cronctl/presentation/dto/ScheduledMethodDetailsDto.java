package ru.syntezis.cronctl.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.syntezis.cronctl.domain.scheduled.ScheduledMethodDetails;

/**
 * DTO for {@link ScheduledMethodDetails}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Details of a registered @Scheduled method")
public class ScheduledMethodDetailsDto {

    @JsonProperty("method_name")
    @Schema(description = "Name of the @Scheduled method", example = "processPayments")
    private String methodName;

    @JsonProperty("schedule")
    @Schema(description = "Schedule configuration extracted from the @Scheduled annotation")
    private ScheduleDetailsDto schedule;

}
