package ru.syntezis.cronctl.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.syntezis.cronctl.enums.TaskExecutionStatus;

import java.time.Instant;
import java.util.UUID;

/** Response DTO returned when an async task execution is accepted ({@code 202 Accepted}). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Confirmation of an async execution submission")
public class ExecutionSubmittedDto {

    @JsonProperty("execution_id")
    @Schema(description = "Unique ID of this execution")
    private UUID executionId;

    @JsonProperty("task_id")
    @Schema(description = "ID of the submitted task")
    private UUID taskId;

    @JsonProperty("status")
    @Schema(description = "Initial status — always PENDING")
    private TaskExecutionStatus status;

    @JsonProperty("submitted_at")
    @Schema(description = "Timestamp when the execution was submitted")
    private Instant submittedAt;

}
