package ru.syntezis.cronctl.util.generator;

import lombok.SneakyThrows;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.implementation.StubMethod;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Modifier;
import java.util.Random;
import java.util.stream.IntStream;

import static ru.syntezis.cronctl.util.condition.Limits.*;

public class BeanGenerator {

    private static final Random random = new Random();

    @SneakyThrows
    @SuppressWarnings("resource")
    public GeneratedBeanDetails generate(BeanCreationRequest request) {
        String beanName = randomString();

        DynamicType.Builder<Object> builder = new ByteBuddy()
                .subclass(Object.class);

        builder = generateScheduledMethods(request.getScheduledMethodsCount(), builder);
        builder = generateSimpleMethods(request.getSimpleMethodsCount(), builder);

        Class<?> assembled = builder
                .make()
                .load(ClassLoader.getSystemClassLoader())
                .getLoaded();

        Object bean = assembled.getDeclaredConstructor().newInstance();

        return GeneratedBeanDetails.builder()
                .beanName(beanName)
                .bean(bean)
                .scheduledMethodsCount(request.getScheduledMethodsCount())
                .build();
    }

    private static DynamicType.Builder<Object> generateScheduledMethods(int count, DynamicType.Builder<Object> builder) {
        return IntStream.range(0, count)
                .boxed()
                .reduce(
                        builder,
                        (current, ignored) -> current
                                .defineMethod(randomString(), void.class, Modifier.PRIVATE)
                                .intercept(StubMethod.INSTANCE)
                                .annotateMethod(AnnotationDescription.Builder
                                        .ofType(Scheduled.class)
                                        .define("fixedRate", random.nextLong(FIXED_RATE_MIN.getValue(), FIXED_RATE_MAX.getValue()))
                                        .build()
                                ),
                        (b1, b2) -> b2
                );
    }

    private static DynamicType.Builder<Object> generateSimpleMethods(int count, DynamicType.Builder<Object> builder) {
        return IntStream.range(0, count)
                .boxed()
                .reduce(
                        builder,
                        (current, ignored) -> current
                                .defineMethod(randomString(), String.class, Modifier.PRIVATE)
                                .intercept(FixedValue.value(randomString())),
                        (b1, b2) -> b2
                );
    }

    public static String randomString() {
        return RandomStringUtils.insecure().next((int) RANDOM_STRING_LENGTH.getValue(), true, false);
    }
}
