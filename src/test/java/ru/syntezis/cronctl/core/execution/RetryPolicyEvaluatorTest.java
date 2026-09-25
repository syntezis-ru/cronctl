package ru.syntezis.cronctl.core.execution;

import org.junit.jupiter.api.Test;
import ru.syntezis.cronctl.domain.task.RetryPolicy;
import ru.syntezis.cronctl.enums.RetryBackoff;

import java.io.IOException;
import java.net.ConnectException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyEvaluatorTest {

    private final RetryPolicyEvaluator underTest = new RetryPolicyEvaluator(() -> 0.75);

    @Test
    void calculateDelay_FixedBackoff_BaseDelayReturned() {
        // Given
        RetryPolicy policy = policy(RetryBackoff.FIXED, 0.0);

        // When
        final Duration actual = underTest.calculateDelay(policy, 3);

        // Then
        final Duration expected = Duration.ofSeconds(10);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void calculateDelay_ExponentialBackoff_MaxDelayApplied() {
        // Given
        RetryPolicy policy = policy(RetryBackoff.EXPONENTIAL, 0.0);

        // When
        final Duration actual = underTest.calculateDelay(policy, 5);

        // Then
        final Duration expected = Duration.ofSeconds(60);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void calculateDelay_JitterConfigured_SymmetricJitterApplied() {
        // Given
        RetryPolicy policy = policy(RetryBackoff.FIXED, 0.2);

        // When
        final Duration actual = underTest.calculateDelay(policy, 1);

        // Then
        final Duration expected = Duration.ofSeconds(11);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void isRetryable_EmptyAllowListAndException_ExceptionRetryable() {
        // Given
        RetryPolicy policy = policy(RetryBackoff.FIXED, 0.0);

        // When
        final boolean actual = underTest.isRetryable(policy, new IOException("temporary"));

        // Then
        assertThat(actual).isTrue();
    }

    @Test
    void isRetryable_EmptyAllowListAndError_ErrorNotRetryable() {
        // Given
        RetryPolicy policy = policy(RetryBackoff.FIXED, 0.0);

        // When
        final boolean actual = underTest.isRetryable(policy, new AssertionError("fatal"));

        // Then
        assertThat(actual).isFalse();
    }

    @Test
    void isRetryable_NonRetryableCauseMatches_DenyListWins() {
        // Given
        RetryPolicy policy = RetryPolicy.builder()
                .retries(2)
                .retryOn(List.of(IOException.class))
                .nonRetryableOn(List.of(ConnectException.class))
                .build();
        IOException failure = new IOException("wrapper", new ConnectException("denied"));

        // When
        final boolean actual = underTest.isRetryable(policy, failure);

        // Then
        assertThat(actual).isFalse();
    }

    private RetryPolicy policy(RetryBackoff backoff, double jitter) {
        return RetryPolicy.builder()
                .retries(5)
                .delay(Duration.ofSeconds(10))
                .backoff(backoff)
                .maxDelay(Duration.ofSeconds(60))
                .jitter(jitter)
                .build();
    }

}
