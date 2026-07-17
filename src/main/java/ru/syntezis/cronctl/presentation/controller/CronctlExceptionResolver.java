package ru.syntezis.cronctl.presentation.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.AbstractHandlerExceptionResolver;
import ru.syntezis.cronctl.exception.DisabledTogglingViolationException;
import ru.syntezis.cronctl.exception.StateTogglerException;
import ru.syntezis.cronctl.exception.TaskNotFoundException;

/**
 * Maps cronctl task-management exceptions to the public HTTP contract.
 */
public class CronctlExceptionResolver extends AbstractHandlerExceptionResolver {

    @Override
    @Nullable
    protected ModelAndView doResolveException(HttpServletRequest request, HttpServletResponse response,
                                              @Nullable Object handler, Exception exception) {
        HttpStatus status = resolveStatus(exception);
        if (status == null) {
            return null;
        }

        response.setStatus(status.value());
        return new ModelAndView();
    }

    @Nullable
    private HttpStatus resolveStatus(Exception exception) {
        if (exception instanceof TaskNotFoundException) {
            return HttpStatus.NOT_FOUND;
        }
        if (exception instanceof DisabledTogglingViolationException) {
            return HttpStatus.CONFLICT;
        }
        if (exception instanceof StateTogglerException) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return null;
    }
}
