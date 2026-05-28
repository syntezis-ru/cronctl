<div style="text-align: center;">
  <img src="docs/logo.svg" alt="cronctl" height="96"/>
  <h1>cronctl-spring-boot-starter</h1>
</div>

[![Maven Central](https://img.shields.io/maven-central/v/ru.syntezis/cronctl-spring-boot-starter)](https://central.sonatype.com/artifact/ru.syntezis/cronctl-spring-boot-starter)
[![Javadoc](https://javadoc.io/badge2/ru.syntezis/cronctl-spring-boot-starter/javadoc.svg)](https://javadoc.io/doc/ru.syntezis/cronctl-spring-boot-starter)
[![GitHub Release](https://img.shields.io/github/v/release/syntezis-ru/cronctl)](https://github.com/syntezis-ru/cronctl/releases/latest)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

[![Java](https://img.shields.io/badge/java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/spring--boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)

[![CI](https://github.com/syntezis-ru/cronctl/actions/workflows/ci.yml/badge.svg)](https://github.com/syntezis-ru/cronctl/actions/workflows/ci.yml)
[![Qodana](https://github.com/syntezis-ru/cronctl/actions/workflows/qodana_code_quality.yml/badge.svg)](https://github.com/syntezis-ru/cronctl/actions/workflows/qodana_code_quality.yml)
[![codecov](https://codecov.io/gh/syntezis-ru/cronctl/branch/master/graph/badge.svg)](https://codecov.io/gh/syntezis-ru/cronctl)
[![Last Update](https://img.shields.io/maven-central/last-update/ru.syntezis/cronctl-spring-boot-starter)](https://central.sonatype.com/artifact/ru.syntezis/cronctl-spring-boot-starter)
[![Last Commit](https://img.shields.io/github/last-commit/syntezis-ru/cronctl)](https://github.com/syntezis-ru/cronctl/commits/master)

A Spring Boot starter that exposes a REST API for viewing and manually triggering
methods annotated with `@Scheduled`.

Add the dependency to your project — cronctl auto-configures itself, scans all
`@Scheduled` beans, and provides HTTP endpoints to inspect, execute, and monitor them on demand.

## Requirements

| Dependency  | Version |
|-------------|---------|
| Java        | 21+     |
| Spring Boot | 3.5.14  |

## Installation

cronctl is available on [Maven Central](https://central.sonatype.com/artifact/ru.syntezis/cronctl-spring-boot-starter).
No additional repository configuration is required.

**Maven:**

```xml
<dependency>
    <groupId>ru.syntezis</groupId>
    <artifactId>cronctl-spring-boot-starter</artifactId>
    <version>0.0.2</version>
</dependency>
```

**Gradle:**

```groovy
implementation 'ru.syntezis:cronctl-spring-boot-starter:0.0.1'
```

No additional configuration is required. cronctl registers itself via Spring Boot
auto-configuration as soon as the dependency is on the classpath.

> **Swagger UI**
> cronctl ships with `springdoc-openapi-starter-webmvc-ui` as a transitive dependency.
> If your project already includes springdoc, cronctl will add its own group to your
> existing Swagger UI. If not, a Swagger UI will be available at `/swagger-ui/index.html`.

## Quick Start

```java
@SpringBootApplication
@EnableScheduling
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

```java
@Component
public class MyScheduler {

    @CronctlTask(
            label = "Sync Data",
            description = "Pulls updates from the remote source",
            group = "integration",
            tags = {"sync", "critical"},
            timeout = 30
    )
    @Scheduled(fixedRate = 60_000)
    public void syncData() {
        // ...
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void generateReport() {
        // registered automatically with defaults: label = "generateReport"
    }

    @CronctlTask.Exclude
    @Scheduled(fixedRate = 60_000)
    public void internalJob() {
        // never appears in the API
    }
}
```

After startup, all `@Scheduled` methods are registered automatically.

## @CronctlTask Annotation

`@CronctlTask` is optional. Without it, cronctl registers the method with sensible defaults.
Use it to enrich the API response with human-readable metadata and control execution behaviour.

| Attribute     | Default                | Description                                                                 |
|---------------|------------------------|-----------------------------------------------------------------------------|
| `label`       | method name            | Display name shown in the API response                                      |
| `description` | `ClassName.methodName` | Human-readable description                                                  |
| `group`       | `"default"`            | Logical group for categorisation                                            |
| `tags`        | `[]`                   | Arbitrary tags for filtering                                                |
| `timeout`     | `0`                    | Per-task execution timeout; `0` means use `cronctl.executor.timeout-seconds`|
| `timeUnit`    | `SECONDS`              | Time unit for `timeout`                                                     |

Use `@CronctlTask.Exclude` to prevent a method from appearing in the API at all.
This annotation is respected in `AUTO` and `PACKAGE` scan modes.

## Scan Modes

Control which `@Scheduled` methods are registered via `cronctl.scan.type`:

| Mode        | Behaviour                                                                                  |
|-------------|--------------------------------------------------------------------------------------------|
| `AUTO`      | All `@Scheduled` methods, except those annotated with `@CronctlTask.Exclude` **(default)** |
| `ANNOTATED` | Only methods explicitly annotated with `@CronctlTask`                                      |
| `PACKAGE`   | All `@Scheduled` methods in the specified packages, except `@Exclude`                      |

**ANNOTATED mode** — only opt-in methods are registered:

```yaml
cronctl:
  scan:
    type: ANNOTATED
```

**PACKAGE mode** — register only methods in given packages:

```yaml
cronctl:
  scan:
    type: PACKAGE
    base-packages:
      - ru.example.billing
      - ru.example.reporting
```

## API

Base path: `/api/cronctl` (configurable via `cronctl.api.base-path`)

### GET /api/cronctl/tasks

Returns registered `@Scheduled` tasks. Supports optional filtering by `group` and `tag`.
When both parameters are provided, only tasks matching **both** conditions are returned.

| Parameter | Type   | Required | Description                         |
|-----------|--------|----------|-------------------------------------|
| `group`   | string | no       | Return only tasks in this group     |
| `tag`     | string | no       | Return only tasks carrying this tag |

```bash
# All tasks
curl http://localhost:8080/api/cronctl/tasks

# Filter by group
curl http://localhost:8080/api/cronctl/tasks?group=integration

# Filter by tag
curl http://localhost:8080/api/cronctl/tasks?tag=critical

# Filter by both (AND)
curl "http://localhost:8080/api/cronctl/tasks?group=integration&tag=critical"
```

```json
{
  "tasks": [
    {
      "label": "Sync Data",
      "description": "Pulls updates from the remote source",
      "group": "integration",
      "tags": ["sync", "critical"],
      "timeout_seconds": 30,
      "details": {
        "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        "method_name": "syncData",
        "schedule": {
          "fixed_rate": 60000,
          "time_unit": "MILLISECONDS"
        }
      }
    }
  ],
  "total": 1
}
```

Only fields that are actually configured appear in `schedule` — unset fields (`cron`, `fixed_delay`, etc.) are omitted from the response.

### POST /api/cronctl/tasks/{id}/execute

Manually triggers a registered task **synchronously** — blocks until the method returns.
Returns `200` regardless of whether the task succeeded or failed; check `status` in the body.

```bash
curl -X POST http://localhost:8080/api/cronctl/tasks/3fa85f64-5717-4562-b3fc-2c963f66afa6/execute
```

```json
{
  "status": "SUCCEEDED",
  "execution_start_mills": 1715000000000,
  "execution_end_mills": 1715000000123,
  "execution_duration_mills": 123,
  "fail_details": null
}
```

If the task throws an exception, `status` is `FAILED` and `fail_details.message` contains the error message.

### Async Execution

For long-running tasks, use the async execution API. A submission returns immediately
with an `execution_id` that you can use to poll status or request cancellation.

#### POST /api/cronctl/tasks/{taskId}/executions

Submit a task for asynchronous execution. Returns `202 Accepted` with the execution ID.

```bash
curl -X POST http://localhost:8080/api/cronctl/tasks/3fa85f64-5717-4562-b3fc-2c963f66afa6/executions
```

```json
{
  "execution_id": "a1b2c3d4-0000-0000-0000-000000000001",
  "task_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "status": "PENDING",
  "submitted_at": "2024-05-01T12:00:00Z"
}
```

Returns `404` if the task ID is unknown, `429` if the executor queue is full.

#### GET /api/cronctl/executions/{executionId}

Poll the current state of an execution.

```bash
curl http://localhost:8080/api/cronctl/executions/a1b2c3d4-0000-0000-0000-000000000001
```

```json
{
  "execution_id": "a1b2c3d4-0000-0000-0000-000000000001",
  "task_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "status": "SUCCEEDED",
  "submitted_at": "2024-05-01T12:00:00Z",
  "started_at": "2024-05-01T12:00:00.050Z",
  "finished_at": "2024-05-01T12:00:00.173Z",
  "execution_duration_mills": 123,
  "fail_details": null
}
```

Possible `status` values:

| Status      | Description                                           |
|-------------|-------------------------------------------------------|
| `PENDING`   | Submitted, waiting for a thread                       |
| `RUNNING`   | Currently executing                                   |
| `SUCCEEDED` | Finished successfully                                 |
| `FAILED`    | Method threw an exception; see `fail_details`         |
| `CANCELLED` | Cancelled before or during execution                  |

Returns `404` if the execution ID is unknown.

#### DELETE /api/cronctl/executions/{executionId}

Request cancellation of a running or pending execution.

```bash
curl -X DELETE http://localhost:8080/api/cronctl/executions/a1b2c3d4-0000-0000-0000-000000000001
```

| Response | Meaning                                              |
|----------|------------------------------------------------------|
| `204`    | Cancellation requested; the thread will be interrupted |
| `409`    | Execution is already in a terminal state             |
| `404`    | Execution ID not found                               |

#### GET /api/cronctl/executions

List all tracked executions, optionally filtered by status.

| Parameter | Type   | Required | Description                          |
|-----------|--------|----------|--------------------------------------|
| `status`  | string | no       | Filter by execution status (e.g. `RUNNING`) |

```bash
# All executions
curl http://localhost:8080/api/cronctl/executions

# Only running executions
curl "http://localhost:8080/api/cronctl/executions?status=RUNNING"
```

```json
{
  "executions": [
    {
      "execution_id": "a1b2c3d4-0000-0000-0000-000000000001",
      "task_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "status": "RUNNING",
      "submitted_at": "2024-05-01T12:00:00Z",
      "started_at": "2024-05-01T12:00:00.050Z"
    }
  ],
  "total": 1
}
```

> **Note:** cronctl keeps executions in memory for the lifetime of the application.
> There is currently no eviction policy — in high-throughput scenarios, consider
> restarting periodically or calling the list endpoint to monitor growth.

## Programmatic Configuration

As an alternative to `application.yml`, you can configure cronctl by declaring a
`CronctlConfiguration` bean. Only fields explicitly set on the builder take effect —
everything else continues to be resolved from `application.yml` and cronctl's built-in defaults.

```java
@Bean
public CronctlConfiguration cronctlConfiguration() {
    return CronctlConfiguration.builder()
            .basePath("/internal/scheduler")
            .scanType(ScanType.ANNOTATED)
            .apiPublicAccess(false)
            .executorThreadPoolSize(8)
            .executorTimeoutSeconds(120)
            .build();
}
```

All builder fields map directly to their `application.yml` counterparts:

| Builder field              | Equivalent property                    |
|----------------------------|----------------------------------------|
| `basePath`                 | `cronctl.api.base-path`                |
| `apiPublicAccess`          | `cronctl.api.public-access`            |
| `swaggerPublicAccess`      | `cronctl.swagger.public-access`        |
| `swaggerGroup`             | `cronctl.swagger.group`                |
| `swaggerPathsToMatch`      | `cronctl.swagger.paths-to-match`       |
| `scanType`                 | `cronctl.scan.type`                    |
| `scanBasePackages`         | `cronctl.scan.base-packages`           |
| `executorThreadPoolSize`   | `cronctl.executor.thread-pool-size`    |
| `executorQueueCapacity`    | `cronctl.executor.queue-capacity`      |
| `executorTimeoutSeconds`   | `cronctl.executor.timeout-seconds`     |

> **Priority**: the programmatic bean takes precedence over `application.yml`, which takes
> precedence over cronctl's built-in defaults.

## Configuration

All properties are optional. The defaults work out of the box.

| Property                              | Default           | Description                                                                                |
|---------------------------------------|-------------------|--------------------------------------------------------------------------------------------|
| `cronctl.enabled`                     | `true`            | Set to `false` to disable the library entirely (no beans registered, no endpoints created) |
| `cronctl.api.base-path`               | `/api/cronctl`    | Base path for all cronctl REST endpoints                                                   |
| `cronctl.api.public-access`           | `true`            | When `false`, authentication is required to call the API                                   |
| `cronctl.swagger.public-access`       | `true`            | When `false`, authentication is required to access Swagger UI                              |
| `cronctl.swagger.group`               | `cronctl`         | Group name shown in Swagger UI                                                             |
| `cronctl.swagger.paths-to-match`      | `/api/cronctl/**` | Path pattern used to include endpoints in the cronctl Swagger group                        |
| `cronctl.scan.type`                   | `AUTO`            | Scan mode: `AUTO`, `ANNOTATED`, or `PACKAGE` (see [Scan Modes](#scan-modes))               |
| `cronctl.scan.base-packages`          | `[]`              | Packages to scan in `PACKAGE` mode                                                         |
| `cronctl.executor.thread-pool-size`   | `4`               | Number of threads in the async execution pool                                              |
| `cronctl.executor.queue-capacity`     | `100`             | Maximum number of tasks waiting in the submission queue                                    |
| `cronctl.executor.timeout-seconds`    | `60`              | Default async execution timeout in seconds; `0` disables the timeout                      |

See [`docs/config-examples/`](docs/config-examples/) for ready-to-use configuration files covering
common scenarios: custom paths, secured API, production setup, and more.

## Security

By default, all cronctl endpoints are publicly accessible to simplify getting started.

**To require authentication for the API:**

```yaml
cronctl:
  api:
    public-access: false
```

**To require authentication for Swagger UI:**

```yaml
cronctl:
  swagger:
    public-access: false
```

Authentication is delegated to your application's existing Spring Security configuration.
cronctl registers its own `SecurityFilterChain` scoped to its endpoints — it does not
interfere with the rest of your application's security.

## Disabling cronctl

To disable cronctl in a specific environment (e.g. production) without removing the dependency:

```yaml
# application-prod.yml
cronctl:
  enabled: false
```

## License

This project is licensed under the [Apache License 2.0](LICENSE).

---

<div style="text-align: center;">
  <a href="https://syntezis.ru">
    <img src="https://syntezis.ru/img/logo.svg" alt="Syntezis" height="40"/>
  </a>
  <br/>
  <sub>Built and maintained by <a href="https://syntezis.ru">Syntezis</a></sub>
</div>
