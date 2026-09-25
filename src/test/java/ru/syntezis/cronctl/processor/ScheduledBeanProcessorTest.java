package ru.syntezis.cronctl.processor;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.StringValueResolver;
import ru.syntezis.cronctl.annotation.CronctlTask;
import ru.syntezis.cronctl.domain.task.Task;
import ru.syntezis.cronctl.enums.ConcurrencyPolicy;
import ru.syntezis.cronctl.enums.RetryBackoff;

import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduledBeanProcessorTest {

    private final ScheduledBeanProcessor underTest = new ScheduledBeanProcessor("test-app");

    @Test
    void process_MethodWithFullCronctlTask_TaskPopulatedFromAnnotation() throws NoSuchMethodException {
        // Given
        SampleScheduledClass bean = new SampleScheduledClass();
        Method method = SampleScheduledClass.class.getDeclaredMethod("fullyCronctlTagged");

        // When
        final Task actual = underTest.process(bean, "testBean", method, null);

        // Then
        assertThat(actual.getLabel())
                .isEqualTo("My Label");
        assertThat(actual.getDescription())
                .isEqualTo("My Description");
        assertThat(actual.getGroup())
                .isEqualTo("my-group");
        assertThat(actual.getTags())
                .containsExactly("tag1", "tag2");
        assertThat(actual.isEnabled())
                .isTrue();
        assertThat(actual.isTogglingEnabled())
                .isTrue();
        assertThat(actual.getConcurrencyPolicy())
                .isEqualTo(ConcurrencyPolicy.SKIP);
        assertThat(actual.getMaxConcurrentExecutions())
                .isEqualTo(2);
        assertThat(actual.getRetryPolicy().getRetries()).isEqualTo(3);
        assertThat(actual.getRetryPolicy().getDelay()).isEqualTo(Duration.ofSeconds(10));
        assertThat(actual.getRetryPolicy().getBackoff()).isEqualTo(RetryBackoff.EXPONENTIAL);
        assertThat(actual.getRetryPolicy().getMaxDelay()).isEqualTo(Duration.ofMinutes(2));
        assertThat(actual.getRetryPolicy().getJitter()).isEqualTo(0.2);
        assertThat(actual.getRetryPolicy().getRetryOn()).containsExactly(IOException.class);
        assertThat(actual.getRetryPolicy().getNonRetryableOn()).containsExactly(IllegalArgumentException.class);
    }

    @Test
    void process_MethodWithoutCronctlTask_DefaultLabelIsMethodName() throws NoSuchMethodException {
        // Given
        SampleScheduledClass bean = new SampleScheduledClass();
        Method method = SampleScheduledClass.class.getDeclaredMethod("notTagged");

        // When
        final Task actual = underTest.process(bean, "testBean", method, null);

        // Then
        assertThat(actual.getLabel())
                .isEqualTo("notTagged");
    }

    @Test
    void process_MethodWithoutCronctlTask_DefaultDescriptionIsClassDotMethod() throws NoSuchMethodException {
        // Given
        SampleScheduledClass bean = new SampleScheduledClass();
        Method method = SampleScheduledClass.class.getDeclaredMethod("notTagged");

        // When
        final Task actual = underTest.process(bean, "testBean", method, null);

        // Then
        assertThat(actual.getDescription())
                .isEqualTo("SampleScheduledClass.notTagged");
    }

    @Test
    void process_MethodWithoutCronctlTask_DefaultGroupAndEmptyTags() throws NoSuchMethodException {
        // Given
        SampleScheduledClass bean = new SampleScheduledClass();
        Method method = SampleScheduledClass.class.getDeclaredMethod("notTagged");

        // When
        final Task actual = underTest.process(bean, "testBean", method, null);

        // Then
        assertThat(actual.getGroup())
                .isEqualTo("default");
        assertThat(actual.getTags())
                .isEmpty();
        assertThat(actual.isEnabled())
                .isTrue();
        assertThat(actual.isTogglingEnabled())
                .isFalse();
        assertThat(actual.getConcurrencyPolicy())
                .isEqualTo(ConcurrencyPolicy.ALLOW);
        assertThat(actual.getMaxConcurrentExecutions())
                .isEqualTo(1);
        assertThat(actual.getRetryPolicy().getRetries()).isZero();
    }

    @Test
    void process_MethodWithEmptyCronctlTaskFields_LabelFallsBackToMethodName() throws NoSuchMethodException {
        // Given
        SampleScheduledClass bean = new SampleScheduledClass();
        Method method = SampleScheduledClass.class.getDeclaredMethod("emptyCronctlTagged");

        // When
        final Task actual = underTest.process(bean, "testBean", method, null);

        // Then
        assertThat(actual.getLabel())
                .isEqualTo("emptyCronctlTagged");
    }

    @Test
    void process_MethodWithEmptyCronctlTaskFields_DescriptionFallsBackToDefault() throws NoSuchMethodException {
        // Given
        SampleScheduledClass bean = new SampleScheduledClass();
        Method method = SampleScheduledClass.class.getDeclaredMethod("emptyCronctlTagged");

        // When
        final Task actual = underTest.process(bean, "testBean", method, null);

        // Then
        assertThat(actual.getDescription())
                .isEqualTo("SampleScheduledClass.emptyCronctlTagged");
    }

    @Test
    void process_MethodWithCronctlTaskPartialFields_UnsetFieldsFallToDefaults() throws NoSuchMethodException {
        // Given
        SampleScheduledClass bean = new SampleScheduledClass();
        Method method = SampleScheduledClass.class.getDeclaredMethod("partialCronctlTagged");

        // When
        final Task actual = underTest.process(bean, "testBean", method, null);

        // Then
        assertThat(actual.getLabel())
                .isEqualTo("custom-label");
        assertThat(actual.getDescription())
                .isEqualTo("SampleScheduledClass.partialCronctlTagged");
        assertThat(actual.getGroup())
                .isEqualTo("default");
    }

    @Test
    void process_WithStringValueResolver_CronPlaceholderResolved() throws NoSuchMethodException {
        // Given
        SampleScheduledClass bean = new SampleScheduledClass();
        Method method = SampleScheduledClass.class.getDeclaredMethod("withCronPlaceholder");
        StringValueResolver resolver = value -> value.equals("${schedule.cron}") ? "0 0 * * * *" : value;

        // When
        final Task actual = underTest.process(bean, "testBean", method, resolver);

        // Then
        assertThat(actual.getDetails().getSchedule().getCron())
                .isEqualTo("0 0 * * * *");
    }

    @Test
    void process_SameMethodProcessedTwice_SameDerivedTaskKey() throws NoSuchMethodException {
        // Given
        final String expected = "test-app.testBean.notTagged()";
        SampleScheduledClass bean = new SampleScheduledClass();
        Method method = SampleScheduledClass.class.getDeclaredMethod("notTagged");

        // When
        final String firstActual = underTest.process(bean, "testBean", method, null).getTaskKey();
        final String secondActual = underTest.process(bean, "testBean", method, null).getTaskKey();

        // Then
        assertThat(firstActual)
                .isEqualTo(expected);
        assertThat(secondActual)
                .isEqualTo(expected);
    }

    @Test
    void process_MethodWithExplicitId_ExplicitTaskKey() throws NoSuchMethodException {
        // Given
        final String expected = "billing.reconciliation";
        SampleScheduledClass bean = new SampleScheduledClass();
        Method method = SampleScheduledClass.class.getDeclaredMethod("withExplicitId");

        // When
        final String actual = underTest.process(bean, "testBean", method, null).getTaskKey();

        // Then
        assertThat(actual)
                .isEqualTo(expected);
    }

    @Test
    void process_MethodWithIdPlaceholder_ResolvedTaskKey() throws NoSuchMethodException {
        // Given
        final String expected = "reports.archive";
        SampleScheduledClass bean = new SampleScheduledClass();
        Method method = SampleScheduledClass.class.getDeclaredMethod("withIdPlaceholder");
        StringValueResolver resolver = value -> value.equals("${task.id}") ? expected : value;

        // When
        final String actual = underTest.process(bean, "testBean", method, resolver).getTaskKey();

        // Then
        assertThat(actual)
                .isEqualTo(expected);
    }

    @Test
    void process_OverloadedMethodWithoutExplicitId_DerivedTaskKeyContainsParameterTypes() throws NoSuchMethodException {
        // Given
        final String expected = "test-app.testBean.overloaded(java.lang.String,long)";
        SampleScheduledClass bean = new SampleScheduledClass();
        Method method = SampleScheduledClass.class.getDeclaredMethod("overloaded", String.class, long.class);

        // When
        final String actual = underTest.process(bean, "testBean", method, null).getTaskKey();

        // Then
        assertThat(actual)
                .isEqualTo(expected);
    }

    @Test
    void process_TaskReferencePopulatedCorrectly() throws NoSuchMethodException {
        // Given
        SampleScheduledClass bean = new SampleScheduledClass();
        Method method = SampleScheduledClass.class.getDeclaredMethod("notTagged");

        // When
        final Task actual = underTest.process(bean, "myBean", method, null);

        // Then
        assertThat(actual.getReference().getBean())
                .isSameAs(bean);
        assertThat(actual.getReference().getBeanName())
                .isEqualTo("myBean");
        assertThat(actual.getReference().getMethod())
                .isEqualTo(method);
    }

    @Test
    void process_TaskDetailsMethodNameAndTaskKeyPopulated() throws NoSuchMethodException {
        // Given
        SampleScheduledClass bean = new SampleScheduledClass();
        Method method = SampleScheduledClass.class.getDeclaredMethod("notTagged");

        // When
        final Task actual = underTest.process(bean, "testBean", method, null);

        // Then
        assertThat(actual.getDetails().getMethodName())
                .isEqualTo("notTagged");
        assertThat(actual.getDetails().getTaskKey())
                .isEqualTo("test-app.testBean.notTagged()");
        assertThat(actual.getDetails().getSchedule())
                .isNotNull();
    }

    @Test
    void process_MethodWithoutCronctlTask_GlobalTimeoutInherited() throws NoSuchMethodException {
        // Given
        final long expected = CronctlTask.USE_GLOBAL_TIMEOUT;
        final SampleScheduledClass bean = new SampleScheduledClass();
        final Method method = SampleScheduledClass.class.getDeclaredMethod("notTagged");

        // When
        final Task task = underTest.process(bean, "testBean", method, null);
        final long actual = task.getTimeoutSeconds();

        // Then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void process_MethodWithDefaultCronctlTask_GlobalTimeoutInherited() throws NoSuchMethodException {
        // Given
        final long expected = CronctlTask.USE_GLOBAL_TIMEOUT;
        final SampleScheduledClass bean = new SampleScheduledClass();
        final Method method = SampleScheduledClass.class.getDeclaredMethod("emptyCronctlTagged");

        // When
        final Task task = underTest.process(bean, "testBean", method, null);
        final long actual = task.getTimeoutSeconds();

        // Then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void process_MethodWithNoTimeout_TimeoutDisabled() throws NoSuchMethodException {
        // Given
        final long expected = CronctlTask.NO_TIMEOUT;
        final SampleScheduledClass bean = new SampleScheduledClass();
        final Method method = SampleScheduledClass.class.getDeclaredMethod("withoutTimeout");

        // When
        final Task task = underTest.process(bean, "testBean", method, null);
        final long actual = task.getTimeoutSeconds();

        // Then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void process_MethodWithTaskTimeout_TimeoutConvertedToSeconds() throws NoSuchMethodException {
        // Given
        final long expected = 120L;
        final SampleScheduledClass bean = new SampleScheduledClass();
        final Method method = SampleScheduledClass.class.getDeclaredMethod("withTaskTimeout");

        // When
        final Task task = underTest.process(bean, "testBean", method, null);
        final long actual = task.getTimeoutSeconds();

        // Then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void process_MethodWithInvalidNegativeTimeout_IllegalArgumentException() throws NoSuchMethodException {
        // Given
        final SampleScheduledClass bean = new SampleScheduledClass();
        final Method method = SampleScheduledClass.class.getDeclaredMethod("withInvalidNegativeTimeout");

        // When
        final ThrowingCallable actual = () -> underTest.process(bean, "testBean", method, null);

        // Then
        assertThatThrownBy(actual)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cronctl task timeout must be -1, 0, or a positive value");
    }

    @Test
    void process_MethodWithInvalidMaxConcurrentExecutions_IllegalArgumentException() throws NoSuchMethodException {
        // Given
        SampleScheduledClass bean = new SampleScheduledClass();
        Method method = SampleScheduledClass.class.getDeclaredMethod("withInvalidMaxConcurrentExecutions");

        // When
        final ThrowingCallable actual = () -> underTest.process(bean, "testBean", method, null);

        // Then
        assertThatThrownBy(actual)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cronctl task maxConcurrentExecutions must be positive");
    }

    @Test
    void process_MethodWithInvalidRetries_IllegalArgumentException() throws NoSuchMethodException {
        // Given
        SampleScheduledClass bean = new SampleScheduledClass();
        Method method = SampleScheduledClass.class.getDeclaredMethod("withInvalidRetries");

        // When
        final ThrowingCallable actual = () -> underTest.process(bean, "testBean", method, null);

        // Then
        assertThatThrownBy(actual)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cronctl task retries must not be negative");
    }

    @Test
    void process_MethodWithInvalidRetryJitter_IllegalArgumentException() throws NoSuchMethodException {
        // Given
        SampleScheduledClass bean = new SampleScheduledClass();
        Method method = SampleScheduledClass.class.getDeclaredMethod("withInvalidRetryJitter");

        // When
        final ThrowingCallable actual = () -> underTest.process(bean, "testBean", method, null);

        // Then
        assertThatThrownBy(actual)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cronctl task retryJitter must be between 0.0 and 1.0");
    }

    private static class SampleScheduledClass {

        @CronctlTask(
                label = "My Label",
                description = "My Description",
                group = "my-group",
                tags = {"tag1", "tag2"},
                togglingEnabled = true,
                concurrency = ConcurrencyPolicy.SKIP,
                maxConcurrentExecutions = 2,
                retries = 3,
                retryDelay = "PT10S",
                retryBackoff = RetryBackoff.EXPONENTIAL,
                maxRetryDelay = "PT2M",
                retryJitter = 0.2,
                retryOn = IOException.class,
                nonRetryableOn = IllegalArgumentException.class
        )
        @Scheduled(fixedRate = 1000L)
        public void fullyCronctlTagged() {}

        @Scheduled(fixedRate = 1000L)
        public void notTagged() {}

        @CronctlTask
        @Scheduled(fixedRate = 1000L)
        public void emptyCronctlTagged() {}

        @Scheduled(cron = "${schedule.cron}")
        public void withCronPlaceholder() {}

        @CronctlTask(label = "custom-label")
        @Scheduled(fixedRate = 1000L)
        public void partialCronctlTagged() {}

        @CronctlTask(timeout = CronctlTask.NO_TIMEOUT)
        @Scheduled(fixedRate = 1000L)
        public void withoutTimeout() {}

        @CronctlTask(timeout = 2L, timeUnit = TimeUnit.MINUTES)
        @Scheduled(fixedRate = 1000L)
        public void withTaskTimeout() {}

        @CronctlTask(timeout = -2L)
        @Scheduled(fixedRate = 1000L)
        public void withInvalidNegativeTimeout() {}

        @CronctlTask(maxConcurrentExecutions = 0)
        @Scheduled(fixedRate = 1000L)
        public void withInvalidMaxConcurrentExecutions() {}

        @CronctlTask(retries = -1)
        @Scheduled(fixedRate = 1000L)
        public void withInvalidRetries() {}

        @CronctlTask(retryJitter = 1.1)
        @Scheduled(fixedRate = 1000L)
        public void withInvalidRetryJitter() {}

        @CronctlTask(id = "billing.reconciliation")
        @Scheduled(fixedRate = 1000L)
        public void withExplicitId() {}

        @CronctlTask(id = "${task.id}")
        @Scheduled(fixedRate = 1000L)
        public void withIdPlaceholder() {}

        @Scheduled(fixedRate = 1000L)
        @SuppressWarnings("unused")
        public void overloaded(String value, long attempt) {}

    }
}
