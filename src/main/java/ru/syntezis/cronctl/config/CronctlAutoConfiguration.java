package ru.syntezis.cronctl.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import ru.syntezis.cronctl.config.swagger.SwaggerCronctlSecurityConfiguration;
import ru.syntezis.cronctl.core.Cronctl;
import ru.syntezis.cronctl.config.swagger.CronctlSwaggerConfig;
import ru.syntezis.cronctl.core.TaskExecutor;
import ru.syntezis.cronctl.properties.CronctlProperties;
import ru.syntezis.cronctl.bpp.ScheduleAnnotationBeanPostProcessor;
import ru.syntezis.cronctl.scan.TaskRegistry;

@AutoConfiguration
@EnableConfigurationProperties(CronctlProperties.class)
@Configuration(proxyBeanMethods = false)
@Import(SwaggerCronctlSecurityConfiguration.class)
public class CronctlAutoConfiguration {

    @Bean
    public static ScheduleAnnotationBeanPostProcessor scheduleAnnotationBeanPostProcessor(TaskRegistry registry) {
        return new ScheduleAnnotationBeanPostProcessor(registry);
    }

    @Bean
    @ConditionalOnMissingBean
    public TaskRegistry scheduledRegistry() {
        return new TaskRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public TaskExecutor taskExecutor() {
        return new TaskExecutor();
    }

    @Bean
    public Cronctl cronctl(TaskRegistry registry, TaskExecutor executor) {
        return new Cronctl(registry, executor);
    }

    @Bean
    public SwaggerConfig swaggerConfig() {
        return new SwaggerConfig();
    }

    private static class SwaggerConfig {

        @Bean
        @ConditionalOnMissingBean
        public SwaggerCronctlSecurityConfiguration swaggerCronctlSecurityConfiguration() {
            return new SwaggerCronctlSecurityConfiguration();
        }

        @Bean
        public CronctlSwaggerConfig cronctlSwaggerConfig() {
            return new CronctlSwaggerConfig();
        }
    }
}
