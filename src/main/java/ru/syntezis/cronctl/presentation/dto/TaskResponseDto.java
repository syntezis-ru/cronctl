package ru.syntezis.cronctl.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ru.syntezis.cronctl.domain.task.Task;

import java.time.Instant;
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

    @JsonProperty("timeout_seconds")
    @Schema(description = "Task-level execution timeout in seconds; 0 means use the global config", example = "30")
    private long timeoutSeconds;

    @JsonProperty("details")
    @Schema(description = "Method details including id, name and schedule configuration")
    private ScheduledMethodDetailsDto details;

    @JsonProperty("next_execution_at")
    @Nullable
    @Schema(description = "Next scheduled execution time in UTC; null for fixedRate/fixedDelay tasks")
    private Instant nextExecutionAt;

}
