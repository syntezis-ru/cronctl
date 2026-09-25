package ru.syntezis.cronctl.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ru.syntezis.cronctl.enums.ExecutionSource;
import ru.syntezis.cronctl.enums.RetryTrigger;
import ru.syntezis.cronctl.enums.TaskExecutionStatus;

import java.time.Instant;
import java.util.UUID;

/** Unified representation of an automatic or manual task execution. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Tracked task execution")
public class ExecutionStatusDto {

    @JsonProperty("execution_id")
    private UUID executionId;

    @JsonProperty("task_key")
    private String taskKey;

    @JsonProperty("source")
    private ExecutionSource source;

    @JsonProperty("status")
    private TaskExecutionStatus status;

    @JsonProperty("node_id")
    private String nodeId;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("queued_at")
    @Nullable
    private Instant queuedAt;

    @JsonProperty("planned_at")
    @Nullable
    private Instant plannedAt;

    @JsonProperty("started_at")
    @Nullable
    private Instant startedAt;

    @JsonProperty("finished_at")
    @Nullable
    private Instant finishedAt;

    @JsonProperty("start_delay_ms")
    @Nullable
    private Long startDelayMs;

    @JsonProperty("duration_ms")
    @Nullable
    private Long durationMs;

    @JsonProperty("status_reason")
    @Nullable
    private String statusReason;

    @JsonProperty("parent_execution_id")
    @Nullable
    private UUID parentExecutionId;

    @JsonProperty("root_execution_id")
    private UUID rootExecutionId;

    @JsonProperty("retry_series_id")
    private UUID retrySeriesId;

    @JsonProperty("attempt")
    private int attempt;

    @JsonProperty("retry_trigger")
    @Nullable
    private RetryTrigger retryTrigger;

    @JsonProperty("retryable")
    private boolean retryable;

    @JsonProperty("error")
    @Nullable
    private FailDetailsDto error;

}
