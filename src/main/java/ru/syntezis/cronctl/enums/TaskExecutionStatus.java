package ru.syntezis.cronctl.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Execution status of a scheduled task")
public enum TaskExecutionStatus {

    @Schema(description = "Task is created but not yet started")
    PENDING,

    @Schema(description = "Task is currently executing")
    RUNNING,

    @Schema(description = "Task completed without errors")
    SUCCEEDED,

    @Schema(description = "Task completed with an exception")
    FAILED

}
