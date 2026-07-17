package ru.syntezis.cronctl.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import ru.syntezis.cronctl.exception.DisabledTogglingViolationException;
import ru.syntezis.cronctl.exception.StateTogglerException;
import ru.syntezis.cronctl.exception.TaskNotFoundException;
import ru.syntezis.cronctl.presentation.controller.CronctlExceptionResolver;

import static org.assertj.core.api.Assertions.assertThat;

class CronctlExceptionResolverTest {

    private final CronctlExceptionResolver underTest = new CronctlExceptionResolver();

    @Test
    void resolveException_TaskNotFoundException_Returns404() {
        // Given
        final int expected = HttpStatus.NOT_FOUND.value();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        TaskNotFoundException exception = new TaskNotFoundException();

        // When
        underTest.resolveException(request, response, null, exception);
        final int actual = response.getStatus();

        // Then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void resolveException_DisabledTogglingViolationException_Returns409() {
        // Given
        final int expected = HttpStatus.CONFLICT.value();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        DisabledTogglingViolationException exception = new DisabledTogglingViolationException();

        // When
        underTest.resolveException(request, response, null, exception);
        final int actual = response.getStatus();

        // Then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void resolveException_StateTogglerException_Returns500() {
        // Given
        final int expected = HttpStatus.INTERNAL_SERVER_ERROR.value();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        StateTogglerException exception = new StateTogglerException();

        // When
        underTest.resolveException(request, response, null, exception);
        final int actual = response.getStatus();

        // Then
        assertThat(actual).isEqualTo(expected);
    }

}
