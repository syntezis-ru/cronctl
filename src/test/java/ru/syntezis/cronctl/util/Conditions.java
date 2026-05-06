package ru.syntezis.cronctl.util;

import lombok.experimental.UtilityClass;
import org.assertj.core.api.Condition;
import org.springframework.scheduling.annotation.Scheduled;
import ru.syntezis.cronctl.domain.ScheduleDetails;
import ru.syntezis.cronctl.domain.ScheduledMethodDetails;
import ru.syntezis.cronctl.domain.ScheduledMethodReference;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Predicate;

import static ru.syntezis.cronctl.util.Limits.*;

@UtilityClass
public class Conditions {

    private static final Predicate<UUID> uuidLengthValidator = id -> id.toString().length() == UUID_LENGTH.getValue();
    private static final Predicate<Long> fixedRateValidator = fr -> fr >= FIXED_RATE_MIN.getValue() && fr <= FIXED_RATE_MAX.getValue();
    private static final Predicate<String> randomStringLengthValidator = s -> s.length() == RANDOM_STRING_LENGTH.getValue();

    public static class ScheduledMethodCondition extends Condition<ScheduledMethodReference> {

        @Override
        public boolean matches(ScheduledMethodReference value) {
            Method method = value.getMethod();
            String methodName = method.getName();
            Scheduled annotation = method.getAnnotation(Scheduled.class);
            long fixedRate = annotation.fixedRate();

            return randomStringLengthValidator.test(methodName) && fixedRateValidator.test(fixedRate);
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

    public static class UUIDCondition extends Condition<ScheduledMethodDetails> {

        @Override
        public boolean matches(ScheduledMethodDetails value) {
            return uuidLengthValidator.test(value.getId());
        }
    }
}
