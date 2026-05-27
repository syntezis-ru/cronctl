package ru.syntezis.cronctl;

import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import ru.syntezis.cronctl.enums.ScanType;

import java.util.List;

/**
 * Programmatic configuration for cronctl.
 *
 * <p>Declare a bean of this type in your application context to override specific cronctl
 * properties without editing {@code application.yml}. Only fields explicitly set via the
 * builder contribute to the configuration; all others continue to be resolved from
 * {@code application.yml} and cronctl's built-in defaults.
 *
 * <p>Example — custom base path and annotated-only scan mode:
 *
 * <pre>{@code
 * @Bean
 * public CronctlConfiguration cronctlConfiguration() {
 *     return CronctlConfiguration.builder()
 *             .basePath("/internal/scheduler")
 *             .scanType(ScanType.ANNOTATED)
 *             .build();
 * }
 * }</pre>
 *
 * <p>This bean is processed by {@code CronctlAutoConfiguration} before any cronctl bean
 * is instantiated, so all properties — including security and scan settings — are
 * applied correctly.
 */
@Getter
@Builder
public class CronctlConfiguration {

    /** Overrides {@code cronctl.api.base-path}. */
    @Nullable
    private final String basePath;

    /** Overrides {@code cronctl.api.public-access}. */
    @Nullable
    private final Boolean apiPublicAccess;

    /** Overrides {@code cronctl.swagger.public-access}. */
    @Nullable
    private final Boolean swaggerPublicAccess;

    /** Overrides {@code cronctl.swagger.group}. */
    @Nullable
    private final String swaggerGroup;

    /** Overrides {@code cronctl.swagger.paths-to-match}. */
    @Nullable
    private final String swaggerPathsToMatch;

    /** Overrides {@code cronctl.scan.type}. */
    @Nullable
    private final ScanType scanType;

    /** Overrides {@code cronctl.scan.base-packages}. */
    @Nullable
    private final List<String> scanBasePackages;

}
