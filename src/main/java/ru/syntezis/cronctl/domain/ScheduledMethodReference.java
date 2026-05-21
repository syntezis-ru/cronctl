package ru.syntezis.cronctl.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.lang.reflect.Method;

/**
 * Holds the runtime reference needed to invoke a {@code @Scheduled} method:
 * the Spring bean instance and the reflective {@link java.lang.reflect.Method} handle.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduledMethodReference {

    private String beanName;
    private Object bean;
    private Method method;

}
