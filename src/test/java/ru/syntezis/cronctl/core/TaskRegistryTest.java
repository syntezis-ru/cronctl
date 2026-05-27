package ru.syntezis.cronctl.core;

import org.apache.commons.lang3.tuple.Pair;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ru.syntezis.cronctl.domain.scheduled.ScheduledMethodDetails;
import ru.syntezis.cronctl.domain.task.Task;
import ru.syntezis.cronctl.exception.TaskAlreadyExistsInRegistryException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskRegistryTest {

    private final TaskRegistry underTest = new TaskRegistry();

    @AfterEach
    void cleanup() {
        underTest.clear();
    }

    @Test
    void add_NewTask_TaskRegistered() {
        // Given
        UUID id = UUID.randomUUID();
        Task task = buildTask(id, "myMethod");

        // When
        underTest.add(id, task);

        // Then
        assertThat(underTest.contains(id))
                .isTrue();
    }

    @Test
    void add_DuplicateId_ThrowsTaskAlreadyExistsInRegistryException() {
        // Given
        UUID id = UUID.randomUUID();
        Task task = buildTask(id, "myMethod");
        underTest.add(id, task);

        // When
        ThrowingCallable invoke = () -> underTest.add(id, task);

        // Then
        assertThatThrownBy(invoke)
                .isInstanceOf(TaskAlreadyExistsInRegistryException.class);
    }

    @Test
    void getById_ExistingId_ReturnsTask() {
        // Given
        UUID id = UUID.randomUUID();
        final Task expected = buildTask(id, "myMethod");
        underTest.add(id, expected);

        // When
        final Optional<Task> actual = underTest.getById(id);

        // Then
        assertThat(actual)
                .isPresent()
                .get()
                .isEqualTo(expected);
    }

    @Test
    void getById_UnknownId_ReturnsEmpty() {
        // Given
        UUID id = UUID.randomUUID();

        // When
        final Optional<Task> actual = underTest.getById(id);

        // Then
        assertThat(actual)
                .isEmpty();
    }

    @Test
    void contains_ExistingId_ReturnsTrue() {
        // Given
        UUID id = UUID.randomUUID();
        underTest.add(id, buildTask(id, "myMethod"));

        // When
        final boolean actual = underTest.contains(id);

        // Then
        assertThat(actual)
                .isTrue();
    }

    @Test
    void contains_UnknownId_ReturnsFalse() {
        // Given
        UUID id = UUID.randomUUID();

        // When
        final boolean actual = underTest.contains(id);

        // Then
        assertThat(actual)
                .isFalse();
    }

    @Test
    void getAll_MultipleTasksAdded_ReturnsAllTasks() {
        // Given
        Task task1 = buildTask(UUID.randomUUID(), "method1");
        Task task2 = buildTask(UUID.randomUUID(), "method2");
        underTest.add(task1.getId(), task1);
        underTest.add(task2.getId(), task2);

        // When
        final List<Task> actual = underTest.getAll();

        // Then
        assertThat(actual)
                .containsExactlyInAnyOrder(task1, task2);
    }

    @Test
    void getAll_NoTasksAdded_ReturnsEmptyList() {
        // When
        final List<Task> actual = underTest.getAll();

        // Then
        assertThat(actual)
                .isEmpty();
    }

    @Test
    void addAll_ListOfTasks_AllTasksRegistered() {
        // Given
        Task task1 = buildTask(UUID.randomUUID(), "method1");
        Task task2 = buildTask(UUID.randomUUID(), "method2");
        List<Pair<UUID, Task>> entries = List.of(
                Pair.of(task1.getId(), task1),
                Pair.of(task2.getId(), task2)
        );

        // When
        underTest.addAll(entries);

        // Then
        assertThat(underTest.contains(task1.getId()))
                .isTrue();
        assertThat(underTest.contains(task2.getId()))
                .isTrue();
    }

    @Test
    void addAll_DuplicateEntry_ThrowsTaskAlreadyExistsInRegistryException() {
        // Given
        UUID id = UUID.randomUUID();
        Task task = buildTask(id, "myMethod");
        underTest.add(id, task);

        // When
        ThrowingCallable invoke = () -> underTest.addAll(List.of(Pair.of(id, task)));

        // Then
        assertThatThrownBy(invoke)
                .isInstanceOf(TaskAlreadyExistsInRegistryException.class);
    }

    @Test
    void clear_TasksPresent_RegistryEmptied() {
        // Given
        UUID id = UUID.randomUUID();
        underTest.add(id, buildTask(id, "myMethod"));

        // When
        underTest.clear();

        // Then
        assertThat(underTest.getAll())
                .isEmpty();
        assertThat(underTest.contains(id))
                .isFalse();
    }

    private Task buildTask(UUID id, String methodName) {
        return Task.builder()
                .label(methodName)
                .description("description")
                .group("default")
                .tags(List.of())
                .details(ScheduledMethodDetails.builder()
                        .id(id)
                        .methodName(methodName)
                        .build()
                )
                .build();
    }
}
