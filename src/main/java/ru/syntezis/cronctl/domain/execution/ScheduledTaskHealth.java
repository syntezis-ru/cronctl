package ru.syntezis.cronctl.domain.execution;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import ru.syntezis.cronctl.enums.TaskExecutionStatus;

import java.time.Instant;

/**
 * Health summary derived only from automatic scheduled executions.
 */
@Getter
@AllArgsConstructor
public class ScheduledTaskHealth {

    public static final ScheduledTaskHealth EMPTY = new ScheduledTaskHealth(null, null, null, 0);

    @Nullable
    private final Instant lastExecutionAt;

    @Nullable
    private final TaskExecutionStatus lastExecutionStatus;

    @Nullable
    private final Instant lastSuccessAt;

    private final int consecutiveFailures;

}
