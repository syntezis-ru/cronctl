package ru.syntezis.cronctl.core.execution;

import lombok.RequiredArgsConstructor;
import ru.syntezis.cronctl.domain.task.RetryPolicy;
import ru.syntezis.cronctl.enums.RetryBackoff;

import java.time.Duration;
import java.util.List;
import java.util.function.DoubleSupplier;

/** Evaluates retryable failures and calculates bounded retry delays. */
@RequiredArgsConstructor
public class RetryPolicyEvaluator {

    private final DoubleSupplier random;

    public boolean isRetryable(RetryPolicy policy, Throwable failure) {
        if (policy.getRetries() == 0 || matchesAny(failure, policy.getNonRetryableOn())) {
            return false;
        }
        if (policy.getRetryOn().isEmpty()) {
            return failure instanceof Exception;
        }
        return matchesAny(failure, policy.getRetryOn());
    }

    public Duration calculateDelay(RetryPolicy policy, int retryNumber) {
        if (retryNumber < 1) {
            throw new IllegalArgumentException("retryNumber must be positive");
        }

        Duration delay = policy.getDelay();
        if (policy.getBackoff() == RetryBackoff.EXPONENTIAL) {
            for (int exponent = 1; exponent < retryNumber && delay.compareTo(policy.getMaxDelay()) < 0; exponent++) {
                delay = doubleAndCap(delay, policy.getMaxDelay());
            }
        }
        delay = minimum(delay, policy.getMaxDelay());
        if (delay.isZero() || policy.getJitter() == 0.0) {
            return delay;
        }

        long delayNanos;
        long maxDelayNanos;
        try {
            delayNanos = delay.toNanos();
            maxDelayNanos = policy.getMaxDelay().toNanos();
        } catch (ArithmeticException e) {
            return delay;
        }
        double factor = 1.0 - policy.getJitter() + 2.0 * policy.getJitter() * random.getAsDouble();
        long jitteredNanos = Math.min(maxDelayNanos, Math.max(0L, Math.round(delayNanos * factor)));
        return Duration.ofNanos(jitteredNanos);
    }

    private Duration doubleAndCap(Duration delay, Duration maximum) {
        try {
            return minimum(delay.multipliedBy(2L), maximum);
        } catch (ArithmeticException e) {
            return maximum;
        }
    }

    private Duration minimum(Duration first, Duration second) {
        return first.compareTo(second) <= 0 ? first : second;
    }

    private boolean matchesAny(Throwable failure, List<Class<? extends Throwable>> configuredTypes) {
        Throwable current = failure;
        while (current != null) {
            Class<?> actualType = current.getClass();
            if (configuredTypes.stream().anyMatch(configuredType -> configuredType.isAssignableFrom(actualType))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

}
