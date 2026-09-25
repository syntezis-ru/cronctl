package ru.syntezis.cronctl.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ru.syntezis.cronctl.domain.task.Task;
import ru.syntezis.cronctl.enums.AutomaticTrackingStatus;
import ru.syntezis.cronctl.enums.ConcurrencyPolicy;
import ru.syntezis.cronctl.enums.RetryBackoff;

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

    @JsonProperty("task_key")
    @Schema(description = "Stable task identifier", example = "billing.reconciliation")
    private String taskKey;

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

    @JsonProperty("concurrency_policy")
    @Schema(description = "Process-local policy for overlapping automatic and manual executions")
    private ConcurrencyPolicy concurrencyPolicy;

    @JsonProperty("max_concurrent_executions")
    @Schema(description = "Maximum admitted executions for restrictive concurrency policies", example = "1")
    private int maxConcurrentExecutions;

    @JsonProperty("retries")
    @Schema(description = "Number of automatic retry attempts after an initial failure", example = "3")
    private int retries;

    @JsonProperty("retry_delay")
    @Schema(description = "Base ISO-8601 retry delay", example = "PT10S")
    private String retryDelay;

    @JsonProperty("retry_backoff")
    private RetryBackoff retryBackoff;

    @JsonProperty("max_retry_delay")
    @Schema(description = "Maximum ISO-8601 retry delay", example = "PT5M")
    private String maxRetryDelay;

    @JsonProperty("retry_jitter")
    @Schema(description = "Symmetric retry delay jitter from 0.0 to 1.0", example = "0.2")
    private double retryJitter;

    @JsonProperty("retry_on")
    private List<String> retryOn;

    @JsonProperty("non_retryable_on")
    private List<String> nonRetryableOn;

    @JsonProperty("details")
    @Schema(description = "Method name and schedule configuration")
    private ScheduledMethodDetailsDto details;

    @JsonProperty("next_execution_at")
    @Nullable
    @Schema(description = "Next scheduled execution time in UTC; null when no future run is currently exposed")
    private Instant nextExecutionAt;

    @JsonProperty("automatic_tracking_status")
    @Schema(description = "Whether automatic executions can be attributed to this task")
    private AutomaticTrackingStatus automaticTrackingStatus;

    @JsonProperty("automatic_tracking_message")
    @Nullable
    @Schema(description = "Diagnostic message when automatic tracking is unavailable")
    private String automaticTrackingMessage;

    @JsonProperty("scheduled_health")
    @Schema(description = "Health derived only from automatic scheduled executions")
    private ScheduledTaskHealthDto scheduledHealth;

}
