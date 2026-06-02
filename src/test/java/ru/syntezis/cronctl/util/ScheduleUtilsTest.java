package ru.syntezis.cronctl.util;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.StringValueResolver;
import ru.syntezis.cronctl.domain.scheduled.ScheduleDetails;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class ScheduleUtilsTest {

    // ── Single-arg overload ───────────────────────────────────────────────────

    @Test
    void assembleScheduleDetails_NullResolverOverload_CronReturnedAsIs() throws NoSuchMethodException {
        // Given
        Scheduled annotation = SampleScheduledClass.class.getDeclaredMethod("withCron").getAnnotation(Scheduled.class);
        final String expected = "0 * * * * *";

        // When
        final ScheduleDetails actual = ScheduleUtils.assembleScheduleDetails(annotation);

        // Then
        assertThat(actual.getCron())
                .isEqualTo(expected);
    }

    @Test
    void assembleScheduleDetails_NullResolverOverload_AllNumericFieldsMapped() throws NoSuchMethodException {
        // Given
        Scheduled annotation = SampleScheduledClass.class.getDeclaredMethod("withFixedRateAndInitialDelay")
                .getAnnotation(Scheduled.class);

        // When
        final ScheduleDetails actual = ScheduleUtils.assembleScheduleDetails(annotation);

        // Then
        assertThat(actual.getFixedRate())
                .isEqualTo(5000L);
        assertThat(actual.getInitialDelay())
                .isEqualTo(1000L);
        assertThat(actual.getTimeUnit())
                .isEqualTo(TimeUnit.MILLISECONDS);
    }

    // ── Resolver = null ───────────────────────────────────────────────────────

    @Test
    void assembleScheduleDetails_ExplicitNullResolver_CronReturnedAsIs() throws NoSuchMethodException {
        // Given
        Scheduled annotation = SampleScheduledClass.class.getDeclaredMethod("withCronPlaceholder")
                .getAnnotation(Scheduled.class);
        final String expected = "${schedule.cron}";

        // When
        final ScheduleDetails actual = ScheduleUtils.assembleScheduleDetails(annotation, null);

        // Then
        assertThat(actual.getCron())
                .isEqualTo(expected);
    }

    // ── Empty value bypasses resolver ─────────────────────────────────────────

    @Test
    void assembleScheduleDetails_EmptyStringFields_ResolverNeverInvoked() throws NoSuchMethodException {
        // Given — withFixedRate has no string schedule fields, all are empty
        Scheduled annotation = SampleScheduledClass.class.getDeclaredMethod("withFixedRate")
                .getAnnotation(Scheduled.class);
        StringValueResolver throwingResolver = value -> {
            throw new AssertionError("resolver must not be called for empty string fields, but was called with: " + value);
        };

        // When + Then
        assertThatNoException().isThrownBy(() ->
                ScheduleUtils.assembleScheduleDetails(annotation, throwingResolver)
        );
    }

    // ── Resolver resolves value ───────────────────────────────────────────────

    @Test
    void assembleScheduleDetails_ResolverResolvesPlaceholder_ResolvedValueUsed() throws NoSuchMethodException {
        // Given
        Scheduled annotation = SampleScheduledClass.class.getDeclaredMethod("withCronPlaceholder")
                .getAnnotation(Scheduled.class);
        final String expected = "0 0 * * * *";
        StringValueResolver resolver = value -> expected;

        // When
        final ScheduleDetails actual = ScheduleUtils.assembleScheduleDetails(annotation, resolver);

        // Then
        assertThat(actual.getCron())
                .isEqualTo(expected);
    }

    @Test
    void assembleScheduleDetails_ResolverResolvesFixedRateString_ResolvedValueUsed() throws NoSuchMethodException {
        // Given
        Scheduled annotation = SampleScheduledClass.class.getDeclaredMethod("withFixedRateString")
                .getAnnotation(Scheduled.class);
        final String expected = "3000";
        StringValueResolver resolver = value -> value.equals("${schedule.rate}") ? expected : value;

        // When
        final ScheduleDetails actual = ScheduleUtils.assembleScheduleDetails(annotation, resolver);

        // Then
        assertThat(actual.getFixedRateString())
                .isEqualTo(expected);
    }

    // ── Resolver returns null → fallback to original ──────────────────────────

    @Test
    void assembleScheduleDetails_ResolverReturnsNull_OriginalValueKept() throws NoSuchMethodException {
        // Given
        Scheduled annotation = SampleScheduledClass.class.getDeclaredMethod("withCronPlaceholder")
                .getAnnotation(Scheduled.class);
        final String expected = "${schedule.cron}";
        StringValueResolver resolver = value -> null;

        // When
        final ScheduleDetails actual = ScheduleUtils.assembleScheduleDetails(annotation, resolver);

        // Then
        assertThat(actual.getCron())
                .isEqualTo(expected);
    }

    // ── Full field mapping ────────────────────────────────────────────────────

    @Test
    void assembleScheduleDetails_AnnotationWithZone_ZoneMapped() throws NoSuchMethodException {
        // Given
        Scheduled annotation = SampleScheduledClass.class.getDeclaredMethod("withZone")
                .getAnnotation(Scheduled.class);

        // When
        final ScheduleDetails actual = ScheduleUtils.assembleScheduleDetails(annotation);

        // Then
        assertThat(actual.getZone())
                .isEqualTo("Europe/Moscow");
        assertThat(actual.getCron())
                .isEqualTo("0 0 12 * * *");
    }

    // ── computeNextExecutionAt ────────────────────────────────────────────────

    @Test
    void computeNextExecutionAt_ValidCronSchedule_ReturnsInstantInFuture() {
        // Given
        final ScheduleDetails schedule = ScheduleDetails.builder()
                .cron("0 * * * * *")
                .build();

        // When
        final Instant actual = ScheduleUtils.computeNextExecutionAt(schedule);

        // Then
        assertThat(actual)
                .isNotNull()
                .isAfter(Instant.now());
    }

    @Test
    void computeNextExecutionAt_ValidCronScheduleWithZone_ReturnsInstantInFuture() {
        // Given
        final ScheduleDetails schedule = ScheduleDetails.builder()
                .cron("0 0 12 * * *")
                .zone("Europe/Moscow")
                .build();

        // When
        final Instant actual = ScheduleUtils.computeNextExecutionAt(schedule);

        // Then
        assertThat(actual)
                .isNotNull()
                .isAfter(Instant.now());
    }

    @Test
    void computeNextExecutionAt_NullCron_ReturnsNull() {
        // Given
        final ScheduleDetails schedule = ScheduleDetails.builder()
                .fixedRate(5000L)
                .build();

        // When
        final Instant actual = ScheduleUtils.computeNextExecutionAt(schedule);

        // Then
        assertThat(actual).isNull();
    }

    @Test
    void computeNextExecutionAt_EmptyCron_ReturnsNull() {
        // Given
        final ScheduleDetails schedule = ScheduleDetails.builder()
                .cron("")
                .fixedRate(5000L)
                .build();

        // When
        final Instant actual = ScheduleUtils.computeNextExecutionAt(schedule);

        // Then
        assertThat(actual).isNull();
    }

    @Test
    void computeNextExecutionAt_InvalidCronExpression_ReturnsNull() {
        // Given
        final ScheduleDetails schedule = ScheduleDetails.builder()
                .cron("not-a-cron")
                .build();

        // When
        final Instant actual = ScheduleUtils.computeNextExecutionAt(schedule);

        // Then
        assertThat(actual).isNull();
    }

    @Test
    void computeNextExecutionAt_FixedRateSchedule_ReturnsNull() {
        // Given
        final ScheduleDetails schedule = ScheduleDetails.builder()
                .cron("")
                .fixedRate(5000L)
                .build();

        // When
        final Instant actual = ScheduleUtils.computeNextExecutionAt(schedule);

        // Then
        assertThat(actual).isNull();
    }

    @Test
    void computeNextExecutionAt_FixedDelaySchedule_ReturnsNull() {
        // Given
        final ScheduleDetails schedule = ScheduleDetails.builder()
                .cron("")
                .fixedDelay(3000L)
                .build();

        // When
        final Instant actual = ScheduleUtils.computeNextExecutionAt(schedule);

        // Then
        assertThat(actual).isNull();
    }

    private static class SampleScheduledClass {

        @Scheduled(cron = "0 * * * * *")
        public void withCron() {}

        @Scheduled(fixedRate = 5000L)
        public void withFixedRate() {}

        @Scheduled(fixedRate = 5000L, initialDelay = 1000L)
        public void withFixedRateAndInitialDelay() {}

        @Scheduled(cron = "${schedule.cron}")
        public void withCronPlaceholder() {}

        @Scheduled(fixedRateString = "${schedule.rate}")
        public void withFixedRateString() {}

        @Scheduled(cron = "0 0 12 * * *", zone = "Europe/Moscow")
        public void withZone() {}

    }
}
