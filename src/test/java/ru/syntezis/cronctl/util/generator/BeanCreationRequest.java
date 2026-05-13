package ru.syntezis.cronctl.util.generator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BeanCreationRequest {

    private int simpleMethodsCount;
    private int scheduledMethodsCount;

    public static BeanCreationRequest of(int simpleMethodsCount, int scheduledMethodsCount) {
        return new BeanCreationRequest(simpleMethodsCount, scheduledMethodsCount);
    }
}
