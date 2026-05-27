package ru.syntezis.cronctl.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.syntezis.cronctl.domain.task.Task;

import java.util.List;

/**
 * DTO for {@link Task}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Registered @Scheduled task")
public class TaskResponseDto {

    @JsonProperty("label")
    @Schema(description = "Task label", example = "Process reports")
    private String label;

    @JsonProperty("description")
    @Schema(description = "Task description", example = "Execute report export preparation")
    private String description;

    @JsonProperty("group")
    @Schema(description = "Group name", example = "default")
    private String group;

    @JsonProperty("tags")
    @Schema(description = "Tags for categorization and filtering", example = "[\"billing\", \"critical\"]")
    private List<String> tags;

    @JsonProperty("details")
    @Schema(description = "Method details including id, name and schedule configuration")
    private ScheduledMethodDetailsDto details;

}
