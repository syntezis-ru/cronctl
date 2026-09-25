package ru.syntezis.cronctl.enums;

/** Backoff strategy applied between automatic retry attempts. */
public enum RetryBackoff {

    FIXED,
    EXPONENTIAL

}
