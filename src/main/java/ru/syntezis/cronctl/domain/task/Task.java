package ru.syntezis.cronctl.domain.task;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
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

    private String label;
    private String description;
    private String group;
    private List<String> tags;

    /** Task-level timeout in seconds. {@code 0} means use the global config. */
    private long timeoutSeconds;

    private ScheduledMethodDetails details;
    private ScheduledMethodReference reference;

    /** Returns the task's unique identifier, which is the UUID of its {@link ScheduledMethodDetails}. */
    public UUID getId() {
        return details.getId();
    }
}
