package ru.syntezis.cronctl.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ru.syntezis.cronctl.enums.TaskExecutionStatus;

import java.time.Instant;

/**
 * Health summary based exclusively on automatic scheduled executions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledTaskHealthDto {

    @JsonProperty("last_execution_at")
    @Nullable
    private Instant lastExecutionAt;

    @JsonProperty("last_execution_status")
    @Nullable
    private TaskExecutionStatus lastExecutionStatus;

    @JsonProperty("last_success_at")
    @Nullable
    private Instant lastSuccessAt;

    @JsonProperty("consecutive_failures")
    private int consecutiveFailures;

}
