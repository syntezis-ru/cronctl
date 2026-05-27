package ru.syntezis.cronctl.config.security;

import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import ru.syntezis.cronctl.properties.CronctlProperties;

/**
 * Configures Spring Security for the cronctl REST API endpoints.
 *
 * <p>Registers a {@link SecurityFilterChain} scoped to {@code {base-path}/**}.
 * The access policy is determined at bean instantiation time from
 * {@link CronctlProperties.Api#isPublicAccess()}:
 * <ul>
 *   <li>{@code true} (default) — all requests permitted without authentication</li>
 *   <li>{@code false} — all requests require authentication</li>
 * </ul>
 *
 * <p>Override the default by declaring a custom {@code CronctlSecurityConfiguration} bean
 * in the application context.
 */
public class CronctlSecurityConfiguration {

    private final String apiSecurityMatcherPattern;
    private final boolean publicAccess;

    public CronctlSecurityConfiguration(CronctlProperties properties) {
        apiSecurityMatcherPattern = properties.getApi().getBasePath() + "/**";
        publicAccess = properties.getApi().isPublicAccess();
    }

    /**
     * Registers a security filter chain for the cronctl API.
     * Permits all requests when {@code cronctl.api.public-access=true}; requires
     * authentication otherwise.
     */
    @Bean(name = "cronctlApiSecurityFilterChain")
    @Order(SecurityProperties.BASIC_AUTH_ORDER - 2)
    public SecurityFilterChain cronctlApiSecurityFilterChain(HttpSecurity http) throws Exception {
        HttpSecurity matcher = http
                .securityMatcher(apiSecurityMatcherPattern)
                .csrf(AbstractHttpConfigurer::disable);
        if (publicAccess) {
            return matcher.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll()).build();
        }
        return matcher.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated()).build();
    }

}
