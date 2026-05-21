package ru.syntezis.cronctl.exception;

import lombok.experimental.StandardException;
import ru.syntezis.cronctl.core.TaskRegistry;

/**
 * Thrown when a task with the same UUID is registered in {@link TaskRegistry} more than once.
 */
@StandardException
public class TaskAlreadyExistsInRegistryException extends CronctlException {

}
