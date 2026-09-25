package ru.syntezis.cronctl.domain.execution;

import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import ru.syntezis.cronctl.enums.ExecutionSource;
import ru.syntezis.cronctl.enums.TaskExecutionStatus;

import java.time.Instant;
import java.util.UUID;

/** Filters and pagination parameters for execution history queries. */
@Getter
@Builder
public class ExecutionQuery {

    @Nullable
    private final String taskKey;
    @Nullable
    private final TaskExecutionStatus status;
    @Nullable
    private final ExecutionSource source;
    @Nullable
    private final String nodeId;
    @Nullable
    private final UUID parentExecutionId;
    @Nullable
    private final Instant from;
    @Nullable
    private final Instant to;
    @Builder.Default
    private final int page = 0;
    @Builder.Default
    private final int size = 50;

}
