# cronctl-spring-boot-starter

A Spring Boot starter that exposes a REST API for viewing and manually triggering
methods annotated with `@Scheduled`.

Add the dependency to your project — cronctl auto-configures itself, scans all
`@Scheduled` beans, and provides HTTP endpoints to inspect and execute them on demand.

## Requirements

| Dependency  | Version |
|-------------|---------|
| Java        | 21+     |
| Spring Boot | 4.0.x   |

## Installation

**Maven:**

```xml
<dependency>
    <groupId>ru.syntezis</groupId>
    <artifactId>cronctl-spring-boot-starter</artifactId>
    <version>0.0.1</version>
</dependency>
```

**Gradle:**

```groovy
implementation 'ru.syntezis:cronctl-spring-boot-starter:0.0.1'
```

No additional configuration is required. cronctl registers itself via Spring Boot
autoconfiguration as soon as the dependency is on the classpath.

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

    @Scheduled(fixedRate = 60_000)
    public void syncData() {
        // ...
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void generateReport() {
        // ...
    }
}
```

After startup, all `@Scheduled` methods are registered automatically.

## API

Base path: `/api/cronctl` (configurable via `cronctl.api.base-path`)

### GET /api/cronctl/tasks

Returns all registered `@Scheduled` tasks.

```bash
curl http://localhost:8080/api/cronctl/tasks
```

```json
{
  "tasks": [
    {
      "details": {
        "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        "method_name": "syncData",
        "schedule": {
          "fixed_rate": 60000,
          "cron": "",
          "time_unit": "MILLISECONDS"
        }
      }
    }
  ],
  "total": 1
}
```

### POST /api/cronctl/execute/{id}

Manually triggers a registered task by its UUID.

```bash
curl -X POST http://localhost:8080/api/cronctl/execute/3fa85f64-5717-4562-b3fc-2c963f66afa6
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

## Configuration

All properties are optional. The defaults work out of the box.

| Property                         | Default           | Description                                                                                |
|----------------------------------|-------------------|--------------------------------------------------------------------------------------------|
| `cronctl.enabled`                | `true`            | Set to `false` to disable the library entirely (no beans registered, no endpoints created) |
| `cronctl.api.base-path`          | `/api/cronctl`    | Base path for all cronctl REST endpoints                                                   |
| `cronctl.api.public-access`      | `true`            | When `false`, authentication is required to call the API                                   |
| `cronctl.swagger.public-access`  | `true`            | When `false`, authentication is required to access Swagger UI                              |
| `cronctl.swagger.group`          | `cronctl`         | Group name shown in Swagger UI                                                             |
| `cronctl.swagger.paths-to-match` | `/api/cronctl/**` | Path pattern used to include endpoints in the cronctl Swagger group                        |

See [`config-examples/`](config-examples/) for ready-to-use configuration files covering
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
