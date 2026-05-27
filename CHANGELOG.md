# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.0.2] — 2026-05-28

### Added

- **Async Execution API** — submit tasks without blocking the caller; track and cancel executions by ID:
    - `POST /api/cronctl/tasks/{taskId}/executions` — enqueue a task, returns `202` with `execution_id`
    - `GET /api/cronctl/executions/{executionId}` — poll current state (`PENDING → RUNNING → SUCCEEDED | FAILED | CANCELLED`)
    - `DELETE /api/cronctl/executions/{executionId}` — request cancellation (`204`); returns `409` if already terminal
    - `GET /api/cronctl/executions?status=` — list all tracked executions, optionally filtered by status
- **`@CronctlTask` annotation** — optionally enrich a `@Scheduled` method with human-readable metadata
  (`label`, `description`, `group`, `tags`) and a per-task execution timeout (`timeout`, `timeUnit`)
- **`@CronctlTask.Exclude`** — prevents a method from appearing in the API; respected in `AUTO` and `PACKAGE` modes
- **Scan modes** (`cronctl.scan.type`):
    - `AUTO` (default) — all `@Scheduled` methods except `@CronctlTask.Exclude`
    - `ANNOTATED` — only methods explicitly annotated with `@CronctlTask`
    - `PACKAGE` — all `@Scheduled` methods in `cronctl.scan.base-packages`, except `@Exclude`
- **Programmatic configuration** via `CronctlConfiguration` bean — an alternative to `application.yml`;
  builder fields take precedence over properties and cronctl's built-in defaults
- **Task filtering** — `GET /api/cronctl/tasks` now accepts `?group=` and `?tag=` query parameters (combinable with AND)

### Changed

- `POST /api/cronctl/execute/{id}` renamed to `POST /api/cronctl/tasks/{id}/execute`
- `ScheduleDetailsDto` — unset `@Scheduled` fields (numeric `-1` and empty strings) are now omitted from the JSON response
- `TaskResponseDto` — added `timeout_seconds` field reflecting the task-level timeout override

### Fixed

- `ScheduleAnnotationBeanPostProcessor` now resolves annotations from the target class via `AopUtils.getTargetClass`,
  ensuring correct discovery when beans are wrapped by CGLIB or JDK proxies (e.g. when `@Transactional` is present)
- Race condition in `AsyncTaskExecutor` where a cancellation request arriving in the narrow window between
  `threadPool.submit()` and `execution.setFuture()` was silently dropped; the interrupt is now delivered after `setFuture`
- GitHub Actions workflow versions corrected: `actions/checkout@v6→v4`, `actions/upload-artifact@v7→v4`
- Four `@NullMarked` violations flagged by Qodana (constant null-check in `ScheduleDetailsMapper`,
  unannotated nullable return types, missing `@Nullable` on optional `?status` request parameter)
- `sample-app`: `commons-lang3` updated 3.18.0 → 3.20.0 (CVE-2025-48924)

### Configuration

New properties introduced in this release:

| Property                              | Default | Description                                                                    |
|---------------------------------------|---------|--------------------------------------------------------------------------------|
| `cronctl.scan.type`                   | `AUTO`  | Scan mode: `AUTO`, `ANNOTATED`, or `PACKAGE`                                   |
| `cronctl.scan.base-packages`          | `[]`    | Packages to scan in `PACKAGE` mode                                             |
| `cronctl.executor.thread-pool-size`   | `4`     | Number of threads in the async execution pool                                  |
| `cronctl.executor.queue-capacity`     | `100`   | Maximum number of tasks waiting in the submission queue                        |
| `cronctl.executor.timeout-seconds`    | `60`    | Default async execution timeout in seconds; `0` disables the timeout           |

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

[0.0.2]: https://github.com/syntezis-ru/cronctl/releases/tag/v0.0.2
[0.0.1]: https://github.com/syntezis-ru/cronctl/releases/tag/v0.0.1
