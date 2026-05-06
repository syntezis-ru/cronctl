package ru.syntezis.cronctl.util;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeneratedBeanDetails {

    private String beanName;
    private Object bean;

    private int scheduledMethodsCount;

}
