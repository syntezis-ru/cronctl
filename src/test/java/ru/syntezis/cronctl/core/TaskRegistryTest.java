package ru.syntezis.cronctl.core;

import org.apache.commons.lang3.tuple.Pair;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ru.syntezis.cronctl.domain.scheduled.ScheduledMethodDetails;
import ru.syntezis.cronctl.domain.scheduled.ScheduledMethodReference;
import ru.syntezis.cronctl.domain.task.Task;
import ru.syntezis.cronctl.enums.AutomaticTrackingStatus;
import ru.syntezis.cronctl.exception.TaskAlreadyExistsInRegistryException;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

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
        String taskKey = "test.myMethod";
        Task task = buildTask(taskKey, "myMethod");

        // When
        underTest.add(taskKey, task);

        // Then
        assertThat(underTest.contains(taskKey))
                .isTrue();
    }

    @Test
    void add_DuplicateTaskKey_ThrowsTaskAlreadyExistsInRegistryException() {
        // Given
        String taskKey = "test.myMethod";
        Task task = buildTask(taskKey, "myMethod");
        underTest.add(taskKey, task);

        // When
        ThrowingCallable invoke = () -> underTest.add(taskKey, task);

        // Then
        assertThatThrownBy(invoke)
                .isInstanceOf(TaskAlreadyExistsInRegistryException.class);
    }

    @Test
    void getById_ExistingTaskKey_ReturnsTask() {
        // Given
        String taskKey = "test.myMethod";
        final Task expected = buildTask(taskKey, "myMethod");
        underTest.add(taskKey, expected);

        // When
        final Optional<Task> actual = underTest.getById(taskKey);

        // Then
        assertThat(actual)
                .isPresent()
                .get()
                .isEqualTo(expected);
    }

    @Test
    void getById_UnknownTaskKey_ReturnsEmpty() {
        // Given
        String taskKey = "missing.task";

        // When
        final Optional<Task> actual = underTest.getById(taskKey);

        // Then
        assertThat(actual)
                .isEmpty();
    }

    @Test
    void contains_ExistingTaskKey_ReturnsTrue() {
        // Given
        String taskKey = "test.myMethod";
        underTest.add(taskKey, buildTask(taskKey, "myMethod"));

        // When
        final boolean actual = underTest.contains(taskKey);

        // Then
        assertThat(actual)
                .isTrue();
    }

    @Test
    void contains_UnknownTaskKey_ReturnsFalse() {
        // Given
        String taskKey = "missing.task";

        // When
        final boolean actual = underTest.contains(taskKey);

        // Then
        assertThat(actual)
                .isFalse();
    }

    @Test
    void getAll_MultipleTasksAdded_ReturnsAllTasks() {
        // Given
        Task task1 = buildTask("test.method1", "method1");
        Task task2 = buildTask("test.method2", "method2");
        underTest.add(task1.getTaskKey(), task1);
        underTest.add(task2.getTaskKey(), task2);

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
        Task task1 = buildTask("test.method1", "method1");
        Task task2 = buildTask("test.method2", "method2");
        List<Pair<String, Task>> entries = List.of(
                Pair.of(task1.getTaskKey(), task1),
                Pair.of(task2.getTaskKey(), task2)
        );

        // When
        underTest.addAll(entries);

        // Then
        assertThat(underTest.contains(task1.getTaskKey()))
                .isTrue();
        assertThat(underTest.contains(task2.getTaskKey()))
                .isTrue();
    }

    @Test
    void addAll_DuplicateEntry_ThrowsTaskAlreadyExistsInRegistryException() {
        // Given
        String taskKey = "test.myMethod";
        Task task = buildTask(taskKey, "myMethod");
        underTest.add(taskKey, task);

        // When
        ThrowingCallable invoke = () -> underTest.addAll(List.of(Pair.of(taskKey, task)));

        // Then
        assertThatThrownBy(invoke)
                .isInstanceOf(TaskAlreadyExistsInRegistryException.class);
    }

    @Test
    void getByTag_MatchingTasks_ReturnsOnlyMatchingTasks() {
        // Given
        String taskKey1 = "test.method1";
        String taskKey2 = "test.method2";
        String taskKey3 = "test.method3";
        Task taggedA = buildTask(taskKey1, "method1", List.of("alpha"));
        Task taggedB = buildTask(taskKey2, "method2", List.of("alpha", "beta"));
        Task other   = buildTask(taskKey3, "method3", List.of("gamma"));
        underTest.add(taskKey1, taggedA);
        underTest.add(taskKey2, taggedB);
        underTest.add(taskKey3, other);

        // When
        final List<Task> actual = underTest.getByTag("alpha");

        // Then
        assertThat(actual)
                .containsExactlyInAnyOrder(taggedA, taggedB);
    }

    @Test
    void getByTag_NoMatchingTasks_ReturnsEmptyList() {
        // Given
        String taskKey = "test.method1";
        underTest.add(taskKey, buildTask(taskKey, "method1", List.of("only")));

        // When
        final List<Task> actual = underTest.getByTag("nonexistent");

        // Then
        assertThat(actual).isEmpty();
    }

    @Test
    void getByGroup_MatchingTasks_ReturnsOnlyMatchingGroup() {
        // Given
        String taskKey1 = "test.method1";
        String taskKey2 = "test.method2";
        String taskKey3 = "test.method3";
        Task inGroup    = buildTaskWithGroup(taskKey1, "method1", "billing");
        Task alsoIn     = buildTaskWithGroup(taskKey2, "method2", "billing");
        Task otherGroup = buildTaskWithGroup(taskKey3, "method3", "reports");
        underTest.add(taskKey1, inGroup);
        underTest.add(taskKey2, alsoIn);
        underTest.add(taskKey3, otherGroup);

        // When
        final List<Task> actual = underTest.getByGroup("billing");

        // Then
        assertThat(actual)
                .containsExactlyInAnyOrder(inGroup, alsoIn);
    }

    @Test
    void getByGroup_NoMatchingTasks_ReturnsEmptyList() {
        // Given
        String taskKey = "test.method1";
        underTest.add(taskKey, buildTaskWithGroup(taskKey, "method1", "someGroup"));

        // When
        final List<Task> actual = underTest.getByGroup("nonexistent");

        // Then
        assertThat(actual).isEmpty();
    }

    @Test
    void clear_TasksPresent_RegistryEmptied() {
        // Given
        String taskKey = "test.myMethod";
        underTest.add(taskKey, buildTask(taskKey, "myMethod"));

        // When
        underTest.clear();

        // Then
        assertThat(underTest.getAll())
                .isEmpty();
        assertThat(underTest.contains(taskKey))
                .isFalse();
    }

    @Test
    void getByScheduledMethod_DuplicateBeanTargets_AmbiguousTasksNotResolved() throws NoSuchMethodException {
        // Given
        Method method = SampleScheduledBean.class.getDeclaredMethod("run");
        Task firstTask = buildReferencedTask("first.task", new SampleScheduledBean(), method);
        Task secondTask = buildReferencedTask("second.task", new SampleScheduledBean(), method);
        underTest.add(firstTask.getTaskKey(), firstTask);
        underTest.add(secondTask.getTaskKey(), secondTask);

        // When
        final Optional<Task> actual = underTest.getByScheduledMethod(SampleScheduledBean.class, method);

        // Then
        assertThat(actual).isEmpty();
        assertThat(firstTask.getAutomaticTrackingStatus()).isEqualTo(AutomaticTrackingStatus.AMBIGUOUS);
        assertThat(secondTask.getAutomaticTrackingStatus()).isEqualTo(AutomaticTrackingStatus.AMBIGUOUS);
        assertThat(firstTask.getAutomaticTrackingMessage()).isNotBlank();
    }

    @Test
    void getByScheduledMethod_SingleBeanTarget_TaskResolved() throws NoSuchMethodException {
        // Given
        Method method = SampleScheduledBean.class.getDeclaredMethod("run");
        final Task expected = buildReferencedTask("first.task", new SampleScheduledBean(), method);
        underTest.add(expected.getTaskKey(), expected);

        // When
        final Optional<Task> actual = underTest.getByScheduledMethod(SampleScheduledBean.class, method);

        // Then
        assertThat(actual).contains(expected);
    }

    private Task buildTask(String taskKey, String methodName) {
        return buildTask(taskKey, methodName, List.of());
    }

    private Task buildTask(String taskKey, String methodName, List<String> tags) {
        return Task.builder()
                .label(methodName)
                .description("description")
                .group("default")
                .tags(tags)
                .details(ScheduledMethodDetails.builder()
                        .taskKey(taskKey)
                        .methodName(methodName)
                        .build()
                )
                .build();
    }

    private Task buildTaskWithGroup(String taskKey, String methodName, String group) {
        return Task.builder()
                .label(methodName)
                .description("description")
                .group(group)
                .tags(List.of())
                .details(ScheduledMethodDetails.builder()
                        .taskKey(taskKey)
                        .methodName(methodName)
                        .build()
                )
                .build();
    }

    private Task buildReferencedTask(String taskKey, Object bean, Method method) {
        return Task.builder()
                .label(method.getName())
                .description("description")
                .group("default")
                .tags(List.of())
                .details(ScheduledMethodDetails.builder()
                        .taskKey(taskKey)
                        .methodName(method.getName())
                        .build())
                .reference(ScheduledMethodReference.builder()
                        .beanName(taskKey)
                        .bean(bean)
                        .method(method)
                        .build())
                .build();
    }

    private static class SampleScheduledBean {

        public void run() {
        }

    }

}
