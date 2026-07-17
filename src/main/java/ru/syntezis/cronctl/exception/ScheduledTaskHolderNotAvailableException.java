package ru.syntezis.cronctl.exception;

import lombok.experimental.StandardException;

/**
 * Thrown when the scheduled task holder is not available in {@link ru.syntezis.cronctl.core.StateToggler}.
 */
@StandardException
public class ScheduledTaskHolderNotAvailableException extends StateTogglerException {

}
