package ru.syntezis.cronctl.domain.task;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ru.syntezis.cronctl.annotation.CronctlTask;
import ru.syntezis.cronctl.domain.scheduled.ScheduledMethodDetails;
import ru.syntezis.cronctl.domain.scheduled.ScheduledMethodReference;
import ru.syntezis.cronctl.enums.AutomaticTrackingStatus;
import ru.syntezis.cronctl.enums.ConcurrencyPolicy;

import java.util.List;

/**
 * Combines the metadata ({@link ScheduledMethodDetails}) and the execution reference
 * ({@link ScheduledMethodReference}) of a single {@code @Scheduled} method.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task {

    /**
     * Task label.
     */
    private String label;

    /**
     * Task description.
     */
    private String description;

    /**
     * Task group.
     */
    private String group;

    /**
     * Task tags.
     */
    private List<String> tags;

    /**
     * Task enabled state.
     */
    private volatile boolean enabled;

    /**
     * Task toggling state.
     */
    private boolean togglingEnabled;

    /**
     * Task-level timeout in seconds. {@code -1} disables the timeout,
     * {@code 0} inherits the global config, and a positive value overrides the global config.
     */
    @Builder.Default
    private long timeoutSeconds = CronctlTask.USE_GLOBAL_TIMEOUT;

    /** Process-local policy for overlapping automatic and manual executions. */
    @Builder.Default
    private ConcurrencyPolicy concurrencyPolicy = ConcurrencyPolicy.ALLOW;

    /** Positive execution limit used by restrictive concurrency policies. */
    @Builder.Default
    private int maxConcurrentExecutions = 1;

    /** Opt-in policy for automatic retries after failed executions. */
    @Builder.Default
    private RetryPolicy retryPolicy = RetryPolicy.disabled();

    /**
     * Task execution details.
     */
    private ScheduledMethodDetails details;

    /**
     * Task execution reference.
     */
    private ScheduledMethodReference reference;

    /** Whether Spring scheduled observations can be attributed to this task. */
    @Builder.Default
    private AutomaticTrackingStatus automaticTrackingStatus = AutomaticTrackingStatus.ACTIVE;

    /** Diagnostic message when automatic execution tracking is unavailable. */
    @Nullable
    private String automaticTrackingMessage;

    /**
     * Returns the task's stable key.
     *
     * @return the stable task key
     */
    public String getTaskKey() {
        return details.getTaskKey();
    }
}
