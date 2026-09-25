# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.1.0] — 2026-07-21

### Added

- Stable string task keys via `@CronctlTask(id = "...")`; blank IDs are derived from the
  application name, bean name, and method signature. Duplicate task keys fail registration.
- Task enable/disable endpoints with the `interrupt` option and per-task `togglingEnabled` policy.
- Optional Thymeleaf operator UI and an interactive GitHub Pages demo.
- Unified history for native Spring scheduled invocations and manual sync/async invocations:
    - sources: `SCHEDULED`, `MANUAL_SYNC`, `MANUAL_ASYNC`, and `RETRY`, with `CATCH_UP` reserved;
    - lifecycle: `CREATED → QUEUED → RUNNING → SUCCEEDED | FAILED | CANCELLED | TIMED_OUT | SKIPPED`;
    - planned time, actual start, start drift, duration, exception type/message, and node ID;
    - server-side filters and pagination on `GET /api/cronctl/executions`;
    - scheduled-only health with last status, last success, and consecutive failure count.
- Pluggable `ExecutionStore` SPI with a bounded default in-memory implementation and periodic
  retention cleanup through `deleteExpired(Instant)`.
- History configuration: `cronctl.history.max-entries`, `cronctl.history.retention`,
  `cronctl.history.cleanup-interval`, and `cronctl.history.node-id`.
- Pluggable `TaskStateStore` SPI with process-local in-memory storage by default and startup
  restoration for persistent implementations supplied by applications.
- A persisted startup pause guard that records any raced automatic invocation as
  `SCHEDULED/SKIPPED` with `status_reason=PAUSED` before user code can run.
- Process-local concurrency policies for automatic and manual executions through
  `@CronctlTask(concurrency = ..., maxConcurrentExecutions = ...)`: `ALLOW`, `SKIP`, `QUEUE`,
  and cooperative `CANCEL_PREVIOUS`.
- Opt-in task retry policies with fixed/exponential backoff, maximum delay, jitter, retryable and
  non-retryable exception filters, persistent queued-retry restoration, execution lineage, and
  automatic `ExecutionSource.RETRY` history.
- Manual `Retry` and `Retry all failed` REST/UI actions with leaf-execution duplicate protection.

### Changed

- Manual sync, manual async, and automatic executions now use the same response model.
- Async status `PENDING` was replaced by the explicit `CREATED` and `QUEUED` states.
- Execution timestamps are ISO-8601 instants and duration/drift fields use the `_ms` suffix.
- `@CronctlTask(timeout = 0)` explicitly means “inherit the global timeout”; use
  `CronctlTask.NO_TIMEOUT` (`-1`) to disable the timeout for one task.
- `POST /tasks/{taskKey}/execute-async` is the primary async endpoint; the older
  `POST /tasks/{taskKey}/executions` route remains as a deprecated alias.
- The default terminal history limit is now 1,000 entries; maximum size is enforced on save,
  while age-based retention runs at startup and every `cronctl.history.cleanup-interval`.

### Fixed

- Added runtime compatibility with Spring Boot 3.0.5 / Spring Framework 6.0 while preserving
  newer scheduler routing and observation support on Spring Framework 6.1+.
- Automatic scheduled history now reports `UNAVAILABLE` on Spring Framework 6.0 instead of
  incorrectly reporting active tracking.
- Swagger configuration no longer loads when springdoc is absent from the consumer classpath.
- Fixed-rate and fixed-delay next-execution values now use live scheduler futures.
- Duplicate bean instances exposing the same scheduled class and method are reported as
  `AMBIGUOUS` instead of attributing automatic executions to the wrong task.
- Task state changes use per-task `java.util.concurrent.locks.Lock` instances.

---

## [0.0.4] — 2026-07-17

### Added

- Optional Thymeleaf operator UI for inspecting schedules and triggering tasks.
- Task enable/disable endpoints with opt-in `@CronctlTask(togglingEnabled = true)` support.
- Per-task timeout constants that distinguish the global timeout from no timeout.
- `POST /tasks/{id}/execute-async` as the primary asynchronous execution endpoint.

### Changed

- Consolidated task operations and async execution routes under `CronctlAPI`.
- Improved next-execution resolution for cron, fixed-rate, and fixed-delay schedules.

---

## [0.0.3] — 2026-06-02

### Added

- **Next execution time** — every task in the API now includes a `next_execution_at` field (ISO-8601 UTC instant):
    - `GET /api/cronctl/tasks` — each task object now contains `next_execution_at`
    - `GET /api/cronctl/tasks/{id}/next-execution` — returns the next execution time for a single task by ID
- `next_execution_at` is resolved via Spring's `ScheduledTaskHolder` for all schedule types
  (cron, fixedRate, fixedDelay). When `ScheduledTaskHolder` is unavailable, cron tasks fall back
  to `CronExpression` arithmetic. For fixedRate / fixedDelay tasks without a live future the field is `null`.

### Changed

- Bytecode target lowered from Java 21 to Java 17; cronctl now works in any Java 17+ application.
  The library continues to be built with JDK 21 — no language features or APIs are affected.

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

[0.1.0]: https://github.com/syntezis-ru/cronctl/releases/tag/v0.1.0
[0.0.4]: https://github.com/syntezis-ru/cronctl/releases/tag/v0.0.4
[0.0.3]: https://github.com/syntezis-ru/cronctl/releases/tag/v0.0.3
[0.0.2]: https://github.com/syntezis-ru/cronctl/releases/tag/v0.0.2
[0.0.1]: https://github.com/syntezis-ru/cronctl/releases/tag/v0.0.1
