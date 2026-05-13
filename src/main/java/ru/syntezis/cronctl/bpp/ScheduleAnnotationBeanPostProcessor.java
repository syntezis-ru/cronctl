package ru.syntezis.cronctl.bpp;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.util.StringValueResolver;
import ru.syntezis.cronctl.domain.ScheduledMethod;
import ru.syntezis.cronctl.filter.ScheduledMethodsFilter;
import ru.syntezis.cronctl.processor.ScheduledBeanProcessor;
import ru.syntezis.cronctl.scan.TaskRegistry;

import java.lang.reflect.Method;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class ScheduleAnnotationBeanPostProcessor implements BeanPostProcessor, EmbeddedValueResolverAware {

    private final TaskRegistry registry;
    private final ScheduledMethodsFilter filter;
    private final ScheduledBeanProcessor processor;

    @Setter(onMethod_ = @Override)
    private StringValueResolver embeddedValueResolver;

    @Override
    public @Nullable Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        log.debug("Processing bean with name: {}", beanName);

        List<Method> methods = filter.filter(List.of(bean.getClass().getDeclaredMethods()));

        if (methods.isEmpty()) {
            log.debug("No scheduled methods found for bean: {}", beanName);
            return bean;
        }

        methods.forEach(m -> {
            ScheduledMethod method = processor.process(bean, beanName, m, embeddedValueResolver);
            registry.add(method.getId(), method);
            log.info("Scheduled method: {} has been registered with id: {}", method.getDetails().getMethodName(), method.getId());
        });

        log.debug("Finished processing bean: {}", beanName);
        return bean;
    }
}
