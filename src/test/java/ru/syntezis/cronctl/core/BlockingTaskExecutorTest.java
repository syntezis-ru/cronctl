package ru.syntezis.cronctl.core;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.scheduling.annotation.Scheduled;
import ru.syntezis.cronctl.core.sync.BlockingTaskExecutor;
import ru.syntezis.cronctl.core.sync.TaskInvocationResult;
import ru.syntezis.cronctl.domain.scheduled.ScheduledMethodDetails;
import ru.syntezis.cronctl.domain.scheduled.ScheduledMethodReference;
import ru.syntezis.cronctl.domain.task.Task;
import ru.syntezis.cronctl.util.ScheduleUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static ru.syntezis.cronctl.util.generator.BeanGenerator.randomString;

@ExtendWith(MockitoExtension.class)
class BlockingTaskExecutorTest {

    @Spy
    private final BlockingTaskExecutor underTest = new BlockingTaskExecutor();

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 10, 100})
    void invoke_TaskProvided_TaskInvokedSuccessfully(int invocations) throws NoSuchMethodException {
        // Given
        final int expected = invocations;
        SampleScheduledClass bean = new SampleScheduledClass();
        Task task = buildTask(bean, "incrementCounter");

        // When
        IntStream.range(0, invocations)
                .forEach(ignored -> underTest.invoke(task, UUID.randomUUID()));
        final int actual = bean.getCounter();

        // Then
        verify(underTest, times(invocations)).invoke(eq(task), any(UUID.class));
        assertThat(actual).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("exceptionsSource")
    void invoke_ThrowingTask_FailureReturned(String exceptionMessage,
                                             Class<? extends Exception> exceptionClass) throws NoSuchMethodException {
        // Given
        SampleScheduledThrowingClass bean = new SampleScheduledThrowingClass(exceptionMessage, exceptionClass);
        Task task = buildTask(bean, "throwingJob");

        // When
        final TaskInvocationResult actual = underTest.invoke(task, UUID.randomUUID());

        // Then
        assertThat(actual.isSucceeded()).isFalse();
        assertThat(actual.getFailure())
                .isExactlyInstanceOf(exceptionClass)
                .hasMessage(exceptionMessage);
    }

    @Test
    void invoke_SuccessfulTask_SuccessReturned() throws NoSuchMethodException {
        // Given
        Task task = buildTask(new SampleScheduledClass(), "incrementCounter");

        // When
        final TaskInvocationResult actual = underTest.invoke(task, UUID.randomUUID());

        // Then
        assertThat(actual.isSucceeded()).isTrue();
        assertThat(actual.getFailure()).isNull();
    }

    private Task buildTask(Object bean, String methodName) throws NoSuchMethodException {
        Method method = bean.getClass().getDeclaredMethod(methodName);
        return Task.builder()
                .details(ScheduledMethodDetails.builder()
                        .taskKey("test." + methodName)
                        .methodName(methodName)
                        .schedule(ScheduleUtils.assembleScheduleDetails(method.getAnnotation(Scheduled.class)))
                        .build())
                .reference(ScheduledMethodReference.builder()
                        .beanName("testBean")
                        .bean(bean)
                        .method(method)
                        .build())
                .build();
    }

    private static Stream<Arguments> exceptionsSource() {
        return Stream.of(
                Arguments.of(randomString(), RuntimeException.class),
                Arguments.of(randomString(), UnsupportedOperationException.class),
                Arguments.of(randomString(), ConcurrentModificationException.class),
                Arguments.of(randomString(), NullPointerException.class),
                Arguments.of(randomString(), BeanCreationException.class)
        );
    }

    @Getter
    private static class SampleScheduledClass {

        private int counter;

        @Scheduled(fixedRate = 1000L)
        private void incrementCounter() {
            counter++;
        }
    }

    @RequiredArgsConstructor
    private static class SampleScheduledThrowingClass {

        private final String message;
        private final Class<? extends Exception> exception;

        @Scheduled(fixedRate = 1000L)
        @SuppressWarnings({"OptionalGetWithoutIsPresent", "unchecked"})
        private void throwingJob() throws Exception {
            Constructor<? extends Exception> constructor = Arrays.stream(exception.getDeclaredConstructors())
                    .filter(candidate -> candidate.getParameterCount() == 1)
                    .filter(candidate -> candidate.getParameterTypes()[0] == String.class)
                    .map(candidate -> (Constructor<? extends Exception>) candidate)
                    .findFirst()
                    .get();
            throw constructor.newInstance(message);
        }
    }
}
