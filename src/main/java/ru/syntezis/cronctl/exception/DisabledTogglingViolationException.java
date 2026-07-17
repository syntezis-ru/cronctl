package ru.syntezis.cronctl.exception;

import lombok.experimental.StandardException;

/**
 * Thrown when a scheduled task is disabled and its state is being toggled.
 */
@StandardException
public class DisabledTogglingViolationException extends StateTogglerException {

}
