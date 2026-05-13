package ru.syntezis.cronctl.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.syntezis.cronctl.domain.Task;

/**
 * DTO for {@link Task}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Registered @Scheduled task")
public class TaskResponseDto {

    @JsonProperty("details")
    @Schema(description = "Method details including id, name and schedule configuration")
    private ScheduledMethodDetailsDto details;

}
