package ru.syntezis.cronctl.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.lang.reflect.Method;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduledMethodReference {

    private Method method;
    private Object bean;

}
