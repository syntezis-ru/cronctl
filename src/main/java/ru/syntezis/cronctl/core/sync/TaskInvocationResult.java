package ru.syntezis.cronctl.core.sync;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

/**
 * Result of invoking a task method without execution lifecycle metadata.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TaskInvocationResult {

    private final boolean succeeded;

    @Nullable
    private final Throwable failure;

    public static TaskInvocationResult succeeded() {
        return new TaskInvocationResult(true, null);
    }

    public static TaskInvocationResult failed(Throwable throwable) {
        return new TaskInvocationResult(false, throwable);
    }
}
