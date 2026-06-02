package ru.syntezis.cronctl.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/** Response DTO carrying the next scheduled execution time for a single task. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Next scheduled execution time for a task")
public class NextExecutionDto {

    @JsonProperty("task_id")
    @Schema(description = "Task identifier")
    private UUID taskId;

    @JsonProperty("next_execution_at")
    @Nullable
    @Schema(description = "Next execution timestamp in UTC; null for fixedRate/fixedDelay tasks")
    private Instant nextExecutionAt;

}
