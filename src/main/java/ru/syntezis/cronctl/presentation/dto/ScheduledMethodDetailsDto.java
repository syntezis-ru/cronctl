package ru.syntezis.cronctl.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.syntezis.cronctl.domain.scheduled.ScheduledMethodDetails;

import java.util.UUID;

/**
 * DTO for {@link ScheduledMethodDetails}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Details of a registered @Scheduled method")
public class ScheduledMethodDetailsDto {

    @JsonProperty("id")
    @Schema(description = "Unique identifier of the scheduled task", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID id;

    @JsonProperty("method_name")
    @Schema(description = "Name of the @Scheduled method", example = "processPayments")
    private String methodName;

    @JsonProperty("schedule")
    @Schema(description = "Schedule configuration extracted from the @Scheduled annotation")
    private ScheduleDetailsDto schedule;

}
