package ru.syntezis.cronctl.core.async;

import org.junit.jupiter.api.Test;
import ru.syntezis.cronctl.domain.execution.TaskExecution;
import ru.syntezis.cronctl.enums.TaskExecutionStatus;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionRegistryTest {

    private final ExecutionRegistry underTest = new ExecutionRegistry();

    @Test
    void register_NewExecution_ExecutionRetrievableById() {
        // Given
        TaskExecution execution = new TaskExecution(UUID.randomUUID());

        // When
        underTest.register(execution);

        // Then
        final Optional<TaskExecution> actual = underTest.getById(execution.getExecutionId());
        assertThat(actual)
                .isPresent()
                .get()
                .isEqualTo(execution);
    }

    @Test
    void getById_UnknownId_ReturnsEmpty() {
        // When
        final Optional<TaskExecution> actual = underTest.getById(UUID.randomUUID());

        // Then
        assertThat(actual).isEmpty();
    }

    @Test
    void cancel_PendingExecution_ReturnsPresentTrueAndStateIsCancelled() {
        // Given
        TaskExecution execution = new TaskExecution(UUID.randomUUID());
        underTest.register(execution);

        // When
        final Optional<Boolean> actual = underTest.cancel(execution.getExecutionId());

        // Then
        assertThat(actual)
                .isPresent()
                .get()
                .isEqualTo(true);

        assertThat(execution.getState())
                .isEqualTo(TaskExecutionStatus.CANCELLED);
    }

    @Test
    void cancel_TerminalExecution_ReturnsPresentFalse() {
        // Given
        TaskExecution execution = new TaskExecution(UUID.randomUUID());
        underTest.register(execution);
        execution.requestCancellation(false);

        // When
        final Optional<Boolean> actual = underTest.cancel(execution.getExecutionId());

        // Then
        assertThat(actual)
                .isPresent()
                .get()
                .isEqualTo(false);
    }

    @Test
    void cancel_UnknownId_ReturnsEmpty() {
        // When
        final Optional<Boolean> actual = underTest.cancel(UUID.randomUUID());

        // Then
        assertThat(actual)
                .isEmpty();
    }
}
