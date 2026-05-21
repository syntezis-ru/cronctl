package ru.syntezis.cronctl.exception;

import lombok.experimental.StandardException;

/**
 * Base exception for all cronctl runtime errors.
 *
 * <p>All exceptions thrown by cronctl extend this class,
 * allowing callers to catch cronctl-specific errors with a single handler.
 */
@StandardException
public class CronctlException extends RuntimeException {

}
