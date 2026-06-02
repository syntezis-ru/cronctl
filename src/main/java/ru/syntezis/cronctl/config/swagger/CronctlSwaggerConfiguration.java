package ru.syntezis.cronctl.config.swagger;

import lombok.RequiredArgsConstructor;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import ru.syntezis.cronctl.properties.CronctlProperties;

/**
 * Registers a springdoc {@link GroupedOpenApi} group for the cronctl controller,
 * using paths and group name configured in {@link CronctlProperties.Swagger}.
 *
 * <p>Active only when springdoc is on the classpath.
 */
@RequiredArgsConstructor
public class CronctlSwaggerConfiguration {

    private final CronctlProperties properties;

    /**
     * Exposes the cronctl controller paths under a named OpenAPI group.
     *
     * @return the configured {@link GroupedOpenApi} bean
     */
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
