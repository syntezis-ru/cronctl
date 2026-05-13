package ru.syntezis.cronctl.config.swagger;

import lombok.RequiredArgsConstructor;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import ru.syntezis.cronctl.properties.CronctlProperties;

@RequiredArgsConstructor
public class CronctlSwaggerConfiguration {

    private final CronctlProperties properties;

    @Bean
    @ConditionalOnClass(name = "org.springdoc.core.models.GroupedOpenApi")
    public GroupedOpenApi cronctlApi() {
        CronctlProperties.Swagger swagger = properties.getSwagger();
        return GroupedOpenApi.builder()
                .group(swagger.getGroup())
                .pathsToMatch(swagger.getPathsToMatch())
                .packagesToScan("ru.syntezis.cronctl.presentation.controller")
                .build();
    }
}
