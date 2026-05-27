package ru.syntezis.cronctl.enums;

/**
 * Defines how cronctl discovers {@code @Scheduled} methods to register.
 */
public enum ScanType {

    /**
     * Scan all {@code @Scheduled} methods in the application context.
     * Methods annotated with {@code @CronctlTask.Exclude} are skipped.
     */
    AUTO,

    /**
     * Scan only {@code @Scheduled} methods explicitly annotated with {@code @CronctlTask}.
     */
    ANNOTATED,

    /**
     * Scan only {@code @Scheduled} methods whose declaring class resides in one of the
     * packages listed under {@code cronctl.scan.base-packages}.
     */
    PACKAGE

}
