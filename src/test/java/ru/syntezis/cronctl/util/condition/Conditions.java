package ru.syntezis.cronctl.util.condition;

import io.vavr.control.Try;
import lombok.experimental.UtilityClass;
import org.assertj.core.api.Condition;
import org.springframework.scheduling.annotation.Scheduled;
import ru.syntezis.cronctl.domain.scheduled.ScheduleDetails;
import ru.syntezis.cronctl.domain.scheduled.ScheduledMethodDetails;
import ru.syntezis.cronctl.domain.scheduled.ScheduledMethodReference;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import static ru.syntezis.cronctl.util.condition.Limits.FIXED_RATE_MAX;
import static ru.syntezis.cronctl.util.condition.Limits.FIXED_RATE_MIN;
import static ru.syntezis.cronctl.util.condition.Limits.RANDOM_STRING_LENGTH;
import static ru.syntezis.cronctl.util.condition.Limits.UUID_LENGTH;

@UtilityClass
public class Conditions {

    private static final Predicate<Long> fixedRateValidator = fr -> fr >= FIXED_RATE_MIN.getValue() && fr <= FIXED_RATE_MAX.getValue();
    private static final Predicate<String> randomStringLengthValidator = s -> s.length() == RANDOM_STRING_LENGTH.getValue();

    public static class ScheduledMethodCondition extends Condition<ScheduledMethodReference> {

        @Override
        public boolean matches(ScheduledMethodReference value) {
            return Try.of(() -> {
                        Method method = Objects.requireNonNull(value.getMethod());
                        Scheduled annotation = Objects.requireNonNull(
                                method.getAnnotation(Scheduled.class)
                        );

                        return randomStringLengthValidator.test(method.getName())
                                && fixedRateValidator.test(annotation.fixedRate());
                    })
                    .getOrElse(false);
        }
    }

    public static class FixedRateCondition extends Condition<ScheduleDetails> {

        @Override
        public boolean matches(ScheduleDetails value) {
            return fixedRateValidator.test(value.getFixedRate());
        }
    }

    public static class RandomGeneratedStringCondition extends Condition<ScheduledMethodDetails> {

        @Override
        public boolean matches(ScheduledMethodDetails value) {
            return randomStringLengthValidator.test(value.getMethodName());
        }
    }

    public static class ScheduledMethodDetailsTaskKeyCondition extends Condition<ScheduledMethodDetails> {

        @Override
        public boolean matches(ScheduledMethodDetails value) {
            return Optional.ofNullable(value.getTaskKey())
                    .filter(taskKey -> !taskKey.isBlank())
                    .isPresent();
        }
    }

    public static class UUIDCondition extends Condition<UUID> {

        @Override
        public boolean matches(UUID value) {
            return Optional.ofNullable(value)
                    .map(UUID::toString)
                    .filter(s -> s.length() == (int) UUID_LENGTH.getValue())
                    .isPresent();
        }
    }
}
