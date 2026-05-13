package ru.syntezis.cronctl.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import ru.syntezis.cronctl.bpp.ScheduleAnnotationBeanPostProcessor;
import ru.syntezis.cronctl.config.security.CronctlSecurityConfiguration;
import ru.syntezis.cronctl.config.security.CronctlSwaggerSecurityConfiguration;
import ru.syntezis.cronctl.config.swagger.CronctlSwaggerConfiguration;
import ru.syntezis.cronctl.core.Cronctl;
import ru.syntezis.cronctl.core.TaskExecutor;
import ru.syntezis.cronctl.filter.ScheduledMethodsFilter;
import ru.syntezis.cronctl.processor.ScheduledBeanProcessor;
import ru.syntezis.cronctl.properties.CronctlProperties;
import ru.syntezis.cronctl.scan.TaskRegistry;

@AutoConfiguration
@EnableConfigurationProperties(CronctlProperties.class)
@Import({
        CronctlSecurityConfiguration.class,
        CronctlSwaggerSecurityConfiguration.class,
        CronctlSwaggerConfiguration.class
})
@ComponentScan({
        "ru.syntezis.cronctl.presentation.mapper",
        "ru.syntezis.cronctl.presentation.controller"
})
@PropertySource("classpath:META-INF/cronctl/cronctl-defaults.properties")
@RequiredArgsConstructor
public class CronctlAutoConfiguration {

    @Bean
    public static ScheduleAnnotationBeanPostProcessor scheduleAnnotationBeanPostProcessor(
            TaskRegistry registry, ScheduledMethodsFilter filter, ScheduledBeanProcessor processor) {
        return new ScheduleAnnotationBeanPostProcessor(registry, filter, processor);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static ScheduledMethodsFilter cronctlScheduledMethodsFilter() {
        return new ScheduledMethodsFilter();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static ScheduledBeanProcessor cronctlScheduledBeanProcessor() {
        return new ScheduledBeanProcessor();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static TaskRegistry cronctlTaskRegistry() {
        return new TaskRegistry();
    }

    @Bean
    @Role(BeanDefinition.ROLE_SUPPORT)
    public TaskExecutor cronctlTaskExecutor() {
        return new TaskExecutor();
    }

    @Bean
    @Role(BeanDefinition.ROLE_APPLICATION)
    public Cronctl cronctl(TaskRegistry registry, TaskExecutor executor) {
        return new Cronctl(registry, executor);
    }
}
