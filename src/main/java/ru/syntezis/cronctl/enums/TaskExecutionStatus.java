package ru.syntezis.cronctl.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Execution status of a scheduled task")
public enum TaskExecutionStatus {

    @Schema(description = "Execution history entry has been created")
    CREATED,

    @Schema(description = "Task has been accepted for execution")
    QUEUED,

    @Schema(description = "Task is currently executing")
    RUNNING,

    @Schema(description = "Task completed without errors")
    SUCCEEDED,

    @Schema(description = "Task completed with an exception")
    FAILED,

    @Schema(description = "Task was cancelled before completing")
    CANCELLED,

    @Schema(description = "Task was cancelled because it exceeded the execution timeout")
    TIMED_OUT,

    @Schema(description = "Task was deliberately not started")
    SKIPPED

}
