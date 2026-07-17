package ru.syntezis.cronctl.domain.task;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.syntezis.cronctl.annotation.CronctlTask;
import ru.syntezis.cronctl.domain.scheduled.ScheduledMethodDetails;
import ru.syntezis.cronctl.domain.scheduled.ScheduledMethodReference;

import java.util.List;
import java.util.UUID;

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

    /**
     * Task execution details.
     */
    private ScheduledMethodDetails details;

    /**
     * Task execution reference.
     */
    private ScheduledMethodReference reference;

    /**
     * Returns the task's unique identifier, which is the UUID of its {@link ScheduledMethodDetails}.
     *
     * @return the task's unique identifier
     */
    public UUID getId() {
        return details.getId();
    }
}
