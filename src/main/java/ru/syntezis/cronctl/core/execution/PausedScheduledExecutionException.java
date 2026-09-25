package ru.syntezis.cronctl.core.execution;

import lombok.RequiredArgsConstructor;

/** Stops a Spring scheduled invocation guarded by a persisted pause marker. */
@RequiredArgsConstructor
class PausedScheduledExecutionException extends RuntimeException {

    private final String taskKey;

    @Override
    public String getMessage() {
        return "Scheduled task " + taskKey + " is paused";
    }

}
