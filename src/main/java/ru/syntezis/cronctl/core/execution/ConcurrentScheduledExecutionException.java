package ru.syntezis.cronctl.core.execution;

import lombok.RequiredArgsConstructor;

/** Stops a Spring scheduled invocation rejected by its local concurrency policy. */
@RequiredArgsConstructor
class ConcurrentScheduledExecutionException extends RuntimeException {

    private final String taskKey;
    private final String reason;

    @Override
    public String getMessage() {
        return "Scheduled task " + taskKey + " was not started: " + reason;
    }

}
