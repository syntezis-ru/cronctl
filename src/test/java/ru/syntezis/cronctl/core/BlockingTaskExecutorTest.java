package ru.syntezis.cronctl.core;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
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
import ru.syntezis.cronctl.domain.scheduled.ScheduledMethodDetails;
import ru.syntezis.cronctl.domain.scheduled.ScheduledMethodReference;
import ru.syntezis.cronctl.domain.task.Task;
import ru.syntezis.cronctl.domain.task.TaskExecutionDetails;
import ru.syntezis.cronctl.enums.TaskExecutionStatus;
import ru.syntezis.cronctl.util.Repeats;
import ru.syntezis.cronctl.util.ScheduleUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static ru.syntezis.cronctl.util.generator.BeanGenerator.randomString;

@ExtendWith(MockitoExtension.class)
class BlockingTaskExecutorTest {

    @Spy
    private final BlockingTaskExecutor underTest = new BlockingTaskExecutor();

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 10, 100, 999, 9999})
    @SuppressWarnings("UnnecessaryLocalVariable")
    void executeTask_TaskProvided_TaskExecutedSuccessfully(int performs) throws NoSuchMethodException {
        // Given
        final int expected = performs;

        UUID id = UUID.randomUUID();
        Method method = SampleScheduledClass.class.getDeclaredMethod("incrementCounter");
        SampleScheduledClass bean = new SampleScheduledClass();

        Task task = Task.builder()
                .details(ScheduledMethodDetails.builder()
                        .id(id)
                        .methodName(method.getName())
                        .schedule(ScheduleUtils.assembleScheduleDetails(method.getAnnotation(Scheduled.class)))
                        .build()
                )
                .reference(ScheduledMethodReference.builder()
                        .beanName("")
                        .bean(bean)
                        .method(method)
                        .build()
                )
                .build();

        // When
        List<TaskExecutionDetails> executionDetails = Repeats.supplierRepeat(performs, () -> underTest.executeTask(task));
        final int actual = bean.getCounter();

        // Then
        verify(underTest, times(performs)).executeTask(task);

        assertThat(expected)
                .isEqualTo(actual);

        assertThat(executionDetails)
                .isNotNull()
                .hasSize(performs)
                .allSatisfy(ted ->
                        assertThat(ted)
                                .hasNoNullFieldsOrPropertiesExcept("failDetails")
                                .hasFieldOrPropertyWithValue("scheduledMethodId", id)
                                .hasFieldOrPropertyWithValue("status", TaskExecutionStatus.SUCCEEDED)
                                .satisfies(ignore ->
                                        assertThat(ted.getExecutionDurationNanos())
                                                .isEqualTo(ted.getExecutionEndNanos() - ted.getExecutionStartNanos())
                                )
                                .satisfies(ignore -> {
                                            assertThat(ted.getExecutionStartMills())
                                                    .isLessThanOrEqualTo(ted.getExecutionEndMills());

                                            assertThat(ted.getExecutionEndMills())
                                                    .isGreaterThanOrEqualTo(ted.getExecutionStartMills());
                                        }
                                )
                );
    }

    @ParameterizedTest
    @MethodSource("exceptionsSource")
    void executeTask_TaskProvided_TaskExecutedAndFailed(String exceptionMessage, Class<? extends Exception> exceptionClass) throws NoSuchMethodException {
        // Given
        UUID id = UUID.randomUUID();
        Method method = SampleScheduledThrowingClass.class.getDeclaredMethod("throwingJob");
        SampleScheduledThrowingClass bean = new SampleScheduledThrowingClass(exceptionMessage, exceptionClass);

        Task task = Task.builder()
                .details(ScheduledMethodDetails.builder()
                        .id(id)
                        .methodName(method.getName())
                        .schedule(ScheduleUtils.assembleScheduleDetails(method.getAnnotation(Scheduled.class)))
                        .build()
                )
                .reference(ScheduledMethodReference.builder()
                        .beanName("")
                        .bean(bean)
                        .method(method)
                        .build()
                )
                .build();

        // When
        TaskExecutionDetails executionDetails = underTest.executeTask(task);
        TaskExecutionDetails.FailDetails failDetails = executionDetails.getFailDetails();
        String message = failDetails.getMessage();
        Throwable throwable = failDetails.getThrowable();

        // Then
        verify(underTest).executeTask(task);

        assertThat(executionDetails)
                .hasNoNullFieldsOrProperties()
                .hasFieldOrPropertyWithValue("status", TaskExecutionStatus.FAILED);

        assertThat(throwable)
                .isExactlyInstanceOf(exceptionClass)
                .hasMessage(message);
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

        private int counter = 0;

        @Scheduled(fixedRate = 1000L)
        private void incrementCounter() {
            counter += 1;
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
                    .filter(c -> c.getParameterCount() == 1)
                    .filter(c -> c.getParameterTypes()[0] == String.class)
                    .map(c -> (Constructor<? extends Exception>) c)
                    .findFirst()
                    .get();

            throw constructor.newInstance(message);
        }
    }
}