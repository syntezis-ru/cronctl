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

    @JsonProperty("enabled")
    @Schema(description = "Whether automatic scheduled execution is enabled", example = "true")
    private boolean enabled;

    @JsonProperty("toggling_enabled")
    @Schema(description = "Whether this task can be enabled and disabled through cronctl", example = "true")
    private boolean togglingEnabled;

    @JsonProperty("timeout_seconds")
    @Schema(description = "Task-level execution timeout in seconds: -1 disables the timeout, "
            + "0 inherits the global config, and a positive value overrides the global config", example = "30")
    private long timeoutSeconds;

    @JsonProperty("details")
    @Schema(description = "Method details including id, name and schedule configuration")
    private ScheduledMethodDetailsDto details;

    @JsonProperty("next_execution_at")
    @Nullable
    @Schema(description = "Next scheduled execution time in UTC; null for fixedRate/fixedDelay tasks")
    private Instant nextExecutionAt;

}
