package ru.syntezis.cronctl.config.security;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import ru.syntezis.cronctl.properties.CronctlProperties;

/**
 * Configures Spring Security for the Swagger UI and OpenAPI spec endpoints.
 *
 * <p>The access policy is determined at bean instantiation time from
 * {@link CronctlProperties.Swagger} ({@code cronctl.swagger.public-access}):
 * <ul>
 *   <li>{@code true} (default) — Swagger UI is publicly accessible</li>
 *   <li>{@code false} — Swagger UI requires authentication</li>
 * </ul>
 */
@RequiredArgsConstructor
public class CronctlSwaggerSecurityConfiguration {

    private final CronctlProperties properties;

    /**
     * Registers a security filter chain for Swagger UI and {@code /v3/api-docs/**}.
     * Permits all requests when {@code cronctl.swagger.public-access=true}; requires
     * authentication otherwise.
     *
     * @param http the {@link HttpSecurity} to configure
     * @return the configured {@link SecurityFilterChain}
     * @throws Exception if the security configuration fails
     */
    @Bean(name = "cronctlSwaggerSecurityFilterChain")
    @Order(SecurityProperties.BASIC_AUTH_ORDER - 1)
    public SecurityFilterChain cronctlSwaggerSecurityFilterChain(HttpSecurity http) throws Exception {
        HttpSecurity matcher = http.securityMatcher("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**");
        if (properties.getSwagger().isPublicAccess()) {
            return matcher.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll()).build();
        }
        return matcher.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated()).build();
    }

}
