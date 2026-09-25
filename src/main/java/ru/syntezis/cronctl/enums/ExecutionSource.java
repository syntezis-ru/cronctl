package ru.syntezis.cronctl.enums;

/** Identifies what initiated a task execution. */
public enum ExecutionSource {

    SCHEDULED,
    MANUAL_SYNC,
    MANUAL_ASYNC,
    RETRY,
    CATCH_UP

}
