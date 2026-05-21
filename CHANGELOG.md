# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.0.1] — 2026-05-21

### Added

- REST API for managing `@Scheduled` methods:
    - `GET /api/cronctl/tasks` — list all registered tasks with schedule details
    - `POST /api/cronctl/execute/{id}` — manually trigger a task by UUID and receive execution result
- Spring Boot auto-configuration — no manual setup required, activated as soon as the dependency is on the classpath
- `ScheduleAnnotationBeanPostProcessor` — scans all Spring beans at startup and registers `@Scheduled` methods
  automatically; resolves property placeholders (e.g. `cron = "${my.cron}"`)
- Spring Security integration with dedicated `SecurityFilterChain` beans for API and Swagger UI, configurable
  independently via properties
- Swagger UI integration via `springdoc-openapi-starter-webmvc-ui` — cronctl endpoints appear as a named group alongside
  existing application API groups
- `CronctlProperties` — type-safe configuration via `cronctl.*` namespace with sensible defaults and full IDE
  autocomplete support
- `TaskExecutionDetails` — captures per-execution id, start/end timestamps (millis + nanos), duration, status, and
  failure details
- OpenAPI `@Schema` annotations on all DTO classes and `@Operation`/`@ApiResponse` on `CronctlAPI`
- `@NullMarked` on all packages via JSpecify for IDE null-safety analysis
- Javadoc on all public classes and non-trivial methods
- `config-examples/` — ready-to-use configuration snippets for common scenarios (custom path, secured access, production
  setup, disabled mode)

### Configuration

| Property                         | Default           | Description                                                                                           |
|----------------------------------|-------------------|-------------------------------------------------------------------------------------------------------|
| `cronctl.enabled`                | `true`            | Set to `false` to disable the library entirely — no beans are registered and no endpoints are created |
| `cronctl.api.base-path`          | `/api/cronctl`    | Base path for all cronctl REST endpoints                                                              |
| `cronctl.api.public-access`      | `true`            | When `false`, authentication is required to access the API                                            |
| `cronctl.swagger.public-access`  | `true`            | When `false`, authentication is required to access Swagger UI                                         |
| `cronctl.swagger.group`          | `cronctl`         | Group name shown in Swagger UI                                                                        |
| `cronctl.swagger.paths-to-match` | `/api/cronctl/**` | Path pattern used to include endpoints in the cronctl Swagger group                                   |

### Requirements

- Java 21+
- Spring Boot 3.x

[0.0.1]: https://github.com/syntezis-ru/cronctl/releases/tag/v0.0.1
