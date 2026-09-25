package ru.syntezis.cronctl.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/** Defines how cronctl handles overlapping executions of the same task. */
@Schema(description = "Policy applied when a task reaches its concurrent execution limit")
public enum ConcurrencyPolicy {

    @Schema(description = "Allow overlapping executions without applying the configured limit")
    ALLOW,

    @Schema(description = "Skip a new execution when the configured limit is reached")
    SKIP,

    @Schema(description = "Queue a new execution until a concurrency permit becomes available")
    QUEUE,

    @Schema(description = "Cancel the oldest execution and queue the replacement until its permit is released")
    CANCEL_PREVIOUS

}
