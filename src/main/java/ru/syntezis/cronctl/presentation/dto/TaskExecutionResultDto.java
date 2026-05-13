package ru.syntezis.cronctl.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ru.syntezis.cronctl.enums.TaskExecutionStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Result of a manual task execution")
public class TaskExecutionResultDto {

    @JsonProperty("status")
    @Schema(description = "Final execution status", example = "SUCCEEDED")
    private TaskExecutionStatus status;

    @JsonProperty("execution_start_mills")
    @Schema(description = "Execution start time in epoch milliseconds", example = "1715000000000")
    private long executionStartMills;

    @JsonProperty("execution_end_mills")
    @Schema(description = "Execution end time in epoch milliseconds", example = "1715000000123")
    private long executionEndMills;

    @JsonProperty("execution_duration_mills")
    @Schema(description = "Total execution duration in milliseconds", example = "123")
    private long executionDurationMills;

    @JsonProperty("fail_details")
    @Schema(description = "Populated when status is FAILED, null otherwise")
    @Nullable
    private FailDetailsDto failDetails;

}
