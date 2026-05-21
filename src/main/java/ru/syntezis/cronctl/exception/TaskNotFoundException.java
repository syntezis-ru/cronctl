package ru.syntezis.cronctl.exception;

import lombok.experimental.StandardException;
import ru.syntezis.cronctl.core.TaskRegistry;

/**
 * Thrown when a task with the requested UUID is not found in {@link TaskRegistry}.
 */
@StandardException
public class TaskNotFoundException extends CronctlException {

}
