package ru.syntezis.cronctl.config;

import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import ru.syntezis.cronctl.properties.CronctlProperties;

/** Maps packaged cronctl UI assets under the configured API base path. */
@RequiredArgsConstructor
public class CronctlUiWebMvcConfigurer implements WebMvcConfigurer {

    private static final String RESOURCE_LOCATION = "classpath:/META-INF/cronctl/ui/";

    private final CronctlProperties properties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (!properties.getUi().isEnabled()) {
            return;
        }

        registry.addResourceHandler(properties.getApi().getBasePath() + "/ui/assets/**")
                .addResourceLocations(RESOURCE_LOCATION)
                .setCachePeriod(3600);
    }
}
