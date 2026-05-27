package ru.syntezis.cronctl.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ru.syntezis.cronctl.enums.TaskExecutionStatus;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Current state of an async task execution")
public class ExecutionStatusDto {

    @JsonProperty("execution_id")
    @Schema(description = "Unique ID of this execution")
    private UUID executionId;

    @JsonProperty("task_id")
    @Schema(description = "ID of the executed task")
    private UUID taskId;

    @JsonProperty("status")
    @Schema(description = "Current execution status")
    private TaskExecutionStatus status;

    @JsonProperty("submitted_at")
    @Schema(description = "Timestamp when the execution was submitted")
    private Instant submittedAt;

    @JsonProperty("started_at")
    @Nullable
    @Schema(description = "Timestamp when execution started; null if still pending")
    private Instant startedAt;

    @JsonProperty("finished_at")
    @Nullable
    @Schema(description = "Timestamp when execution finished; null if still running")
    private Instant finishedAt;

    @JsonProperty("execution_duration_mills")
    @Nullable
    @Schema(description = "Execution duration in milliseconds; null if not yet finished")
    private Long executionDurationMills;

    @JsonProperty("fail_details")
    @Nullable
    @Schema(description = "Failure details; present only when status is FAILED")
    private FailDetailsDto failDetails;

}
