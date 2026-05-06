package ru.syntezis.cronctl.util;

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

import static ru.syntezis.cronctl.util.Limits.*;

public class BeanGenerator {

    private static final Random random = new Random();

    @SneakyThrows
    @SuppressWarnings("resource")
    public GeneratedBeanDetails generate(BeanCreationRequest request) {
        String beanName = randomString();

        DynamicType.Builder<Object> builder = new ByteBuddy()
                .subclass(Object.class);

        generateScheduledMethods(request.getScheduledMethodsCount(), builder);
        generateSimpleMethods(request.getSimpleMethodsCount(), builder);

        Class<?> assembled = builder
                .make()
                .load(ClassLoader.getSystemClassLoader())
                .getLoaded();

        Object bean = assembled.getDeclaredConstructor().newInstance();

        return GeneratedBeanDetails.builder()
                .beanName(beanName)
                .bean(bean)
                .build();
    }

    private static void generateScheduledMethods(int count, DynamicType.Builder<Object> builder) {
        for (int i = 0; i < count; i++) {
            builder
                    .defineMethod(randomString(), void.class, Modifier.PRIVATE)
                    .intercept(StubMethod.INSTANCE)
                    .annotateMethod(AnnotationDescription.Builder
                            .ofType(Scheduled.class)
                            .define("fixedRate", random.nextLong(FIXED_RATE_MIN.getValue(), FIXED_RATE_MAX.getValue()))
                            .build()
                    );
        }
    }

    private static void generateSimpleMethods(int count, DynamicType.Builder<Object> builder) {
        for (int i = 0; i < count; i++) {
            builder
                    .defineMethod(randomString(), String.class, Modifier.PRIVATE)
                    .intercept(FixedValue.value(randomString()));
        }
    }

    private static String randomString() {
        return RandomStringUtils.insecure().next((int) RANDOM_STRING_LENGTH.getValue(), true, false);
    }
}
