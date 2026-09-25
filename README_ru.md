<div style="text-align: center;">
  <img src="docs/logo.svg" alt="cronctl" height="96"/>
  <h1>cronctl-spring-boot-starter</h1>
</div>

[English](README.md) | **Русский**

[![Maven Central](https://img.shields.io/maven-central/v/ru.syntezis/cronctl-spring-boot-starter)](https://central.sonatype.com/artifact/ru.syntezis/cronctl-spring-boot-starter)
[![Javadoc](https://javadoc.io/badge2/ru.syntezis/cronctl-spring-boot-starter/javadoc.svg)](https://javadoc.io/doc/ru.syntezis/cronctl-spring-boot-starter)
[![GitHub Release](https://img.shields.io/github/v/release/syntezis-ru/cronctl)](https://github.com/syntezis-ru/cronctl/releases/latest)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

[![Java](https://img.shields.io/badge/java-17%2B-orange.svg)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/spring--boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)

[![CI](https://github.com/syntezis-ru/cronctl/actions/workflows/ci.yml/badge.svg)](https://github.com/syntezis-ru/cronctl/actions/workflows/ci.yml)
[![Qodana](https://github.com/syntezis-ru/cronctl/actions/workflows/qodana_code_quality.yml/badge.svg)](https://github.com/syntezis-ru/cronctl/actions/workflows/qodana_code_quality.yml)

[![Last Commit](https://img.shields.io/github/last-commit/syntezis-ru/cronctl)](https://github.com/syntezis-ru/cronctl/commits/master)
[![GitHub Issues](https://img.shields.io/github/issues/syntezis-ru/cronctl?style=flat-square)](https://github.com/syntezis-ru/cronctl/issues)

Spring Boot starter, предоставляющий операторский UI и REST API для наблюдения,
управления и ручного запуска методов, помеченных аннотацией `@Scheduled`.

Добавьте зависимость в проект — cronctl автоматически настроится, найдёт все бины
с `@Scheduled` и предоставит HTTP-эндпоинты для просмотра, запуска и мониторинга задач.

## Требования

| Зависимость | Версия |
|-------------|--------|
| Java        | 17+    |
| Spring Boot | 3.0.5+ (3.x) |

На Spring Framework 6.0 (Spring Boot 3.0/3.1) поддерживаются обнаружение задач,
REST-операции, история ручных запусков, расчёт следующего запуска и pause/resume.
История автоматических запусков через штатные Spring observations требует Spring Framework 6.1+;
на более старых версиях задача возвращает `automatic_tracking_status: UNAVAILABLE`
с поясняющим сообщением.

## Установка

cronctl опубликован в [Maven Central](https://central.sonatype.com/artifact/ru.syntezis/cronctl-spring-boot-starter).
Дополнительная настройка репозиториев не требуется.

**Maven:**

```xml
<dependency>
    <groupId>ru.syntezis</groupId>
    <artifactId>cronctl-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

**Gradle:**

```groovy
implementation 'ru.syntezis:cronctl-spring-boot-starter:0.1.0'
```

Дополнительная конфигурация не требуется. cronctl регистрируется через механизм
автоконфигурации Spring Boot, как только зависимость появляется в classpath.

> **Swagger UI**
> Добавьте `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0`, чтобы включить
> документацию OpenAPI. После этого cronctl добавит собственную группу в существующий
> Swagger UI по адресу `/swagger-ui/index.html`. Зависимость опциональна и не навязывается
> приложениям-потребителям.

> **Операторский UI**
> Добавьте Thymeleaf, чтобы включить панель управления задачами по адресу `/api/cronctl/ui`:
>
> ```xml
> <dependency>
>     <groupId>org.springframework.boot</groupId>
>     <artifactId>spring-boot-starter-thymeleaf</artifactId>
> </dependency>
> ```
>
> Gradle: `implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'`.
> UI опционален: его отсутствие не влияет на REST API и Swagger.

## Быстрый старт

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
            id = "integration.sync-data",
            label = "Sync Data",
            description = "Pulls updates from the remote source",
            group = "integration",
            tags = {"sync", "critical"},
            timeout = 30,
            togglingEnabled = true
    )
    @Scheduled(fixedRate = 60_000)
    public void syncData() {
        // ...
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void generateReport() {
        // Регистрируется автоматически со значениями по умолчанию: label = "generateReport"
    }

    @CronctlTask.Exclude
    @Scheduled(fixedRate = 60_000)
    public void internalJob() {
        // Никогда не появится в API
    }
}
```

После запуска все методы с `@Scheduled` регистрируются автоматически.

## Аннотация @CronctlTask

Аннотация `@CronctlTask` необязательна. Без неё cronctl регистрирует метод с разумными
значениями по умолчанию. Используйте аннотацию, чтобы дополнить API человекочитаемыми
метаданными и настроить поведение при выполнении.

| Атрибут                   | Значение по умолчанию       | Описание                                                                                     |
|---------------------------|-----------------------------|----------------------------------------------------------------------------------------------|
| `id`                      | вычисляемый стабильный ключ | Стабильный ключ задачи, возвращаемый в `task_key`                                            |
| `label`                   | имя метода                  | Отображаемое имя в ответе API                                                                |
| `description`             | `ClassName.methodName`      | Человекочитаемое описание                                                                    |
| `group`                   | `"default"`                 | Логическая группа                                                                            |
| `tags`                    | `[]`                        | Произвольные теги для фильтрации                                                             |
| `timeout`                 | `USE_GLOBAL_TIMEOUT` (`0`)  | `0` наследует глобальный таймаут, `-1` отключает его, положительное число переопределяет его |
| `timeUnit`                | `SECONDS`                   | Единица измерения положительного `timeout`                                                   |
| `togglingEnabled`         | `false`                     | Разрешает приостанавливать и возобновлять автоматические запуски через API                   |
| `concurrency`             | `ALLOW`                     | Локальная политика пересечения автоматических и ручных запусков                              |
| `maxConcurrentExecutions` | `1`                         | Положительный лимит для `SKIP`, `QUEUE` и `CANCEL_PREVIOUS`                                  |
| `retries`                 | `0`                         | Число автоматических повторов после первого сбоя; по умолчанию отключены                     |
| `retryDelay`              | `"PT1S"`                    | Базовая ISO-8601-задержка между автоматическими повторами                                    |
| `retryBackoff`            | `FIXED`                     | Стратегия задержки `FIXED` или `EXPONENTIAL`                                                 |
| `maxRetryDelay`           | `"PT5M"`                    | Максимальная задержка после применения backoff и jitter                                      |
| `retryJitter`             | `0.0`                       | Симметричное случайное отклонение задержки от `0.0` до `1.0`                                 |
| `retryOn`                 | `[]`                        | Типы исключений для повтора; пустой список означает все типы `Exception`                     |
| `nonRetryableOn`          | `[]`                        | Типы исключений, которые повторять нельзя; имеют приоритет                                   |

Используйте именованные константы, чтобы явно обозначить назначение таймаута:

```java
@CronctlTask(timeout = CronctlTask.USE_GLOBAL_TIMEOUT) // значение по умолчанию
@CronctlTask(timeout = CronctlTask.NO_TIMEOUT)
@CronctlTask(timeout = 30, timeUnit = TimeUnit.SECONDS)
```

### Политики конкурентного выполнения

Политики конкурентного выполнения предотвращают случайное пересечение автоматических
и ручных запусков задачи с одним стабильным ключом внутри одного процесса приложения:

```java
@CronctlTask(
        id = "catalog.sync",
        concurrency = ConcurrencyPolicy.SKIP,
        maxConcurrentExecutions = 1
)
@Scheduled(cron = "0 */5 * * * *")
public void synchronizeCatalog() {
    // ...
}
```

| Политика          | Поведение при достижении лимита                                                      |
|-------------------|--------------------------------------------------------------------------------------|
| `ALLOW`           | Сохраняет прежнее поведение и не применяет настроенный лимит                         |
| `SKIP`            | Записывает `SKIPPED` с `status_reason=CONCURRENT_EXECUTION`                          |
| `QUEUE`           | Оставляет новый запуск в `QUEUED`, пока справедливый семафор не освободит разрешение |
| `CANCEL_PREVIOUS` | Прерывает самый старый запуск и ждёт, пока тот освободит разрешение                  |

Один лимит применяется к запускам `SCHEDULED`, `MANUAL_SYNC` и `MANUAL_ASYNC`.
Отмена выполняется кооперативно через `Thread.interrupt()`: код задачи должен корректно
реагировать на прерывание. Замещающий запуск никогда не превышает настроенный лимит.
Если предыдущая задача игнорирует прерывание, новый запуск остаётся в очереди.

Первая реализация работает локально внутри одного контекста приложения и не координирует
несколько экземпляров. В будущем распределённую реализацию можно интегрировать с таким
провайдером, как ShedLock. В этой версии cronctl не добавляет ShedLock и зависимости от
баз данных, Redis или MongoDB.

### Политики повторных запусков

Автоматические повторы включаются явно, поскольку методы по расписанию не обязательно
являются идемпотентными:

```java
@CronctlTask(
        id = "catalog.sync",
        retries = 3,
        retryDelay = "PT10S",
        retryBackoff = RetryBackoff.EXPONENTIAL,
        maxRetryDelay = "PT5M",
        retryJitter = 0.2,
        retryOn = {SocketTimeoutException.class, ConnectException.class},
        nonRetryableOn = IllegalArgumentException.class
)
```

`retries = 3` означает исходный запуск и не более трёх автоматических запусков `RETRY`.
Фильтры исключений проверяют всю цепочку причин; `nonRetryableOn` имеет приоритет.
Если `retryOn` пуст, разрешены все подклассы `Exception`, а `Error` не повторяется
неявно. Политика запускает повторы только для `FAILED`. Запуски `TIMED_OUT` оператор
по-прежнему может повторить вручную.

Каждый повтор имеет собственный UUID и lifecycle. Поля `parent_execution_id`,
`root_execution_id`, `retry_series_id`, `attempt` и `retry_trigger` сохраняют связи между
запусками. Автоматические повторы продолжают текущую серию, а ручной Retry начинает новую
серию и получает новый бюджет автоматических повторов. Для `RETRY` применяются тот же
таймаут, ограниченный executor и политика конкурентного выполнения, что и для других источников.

Отложенные повторы из персистентного хранилища восстанавливаются после перезапуска приложения.
Если ключ задачи больше не зарегистрирован, повтор получает статус `SKIPPED` с
`status_reason=TASK_NOT_REGISTERED`.

Используйте `@CronctlTask.Exclude`, чтобы полностью скрыть метод из API. Эта аннотация
учитывается в режимах сканирования `AUTO` и `PACKAGE`.

### Стабильные ключи задач

Ключи задач не меняются после перезапуска приложения. Для интеграций, дашбордов и
автоматизации указывайте ключ явно:

```java

@CronctlTask(
        id = "billing.reconciliation",
        label = "Billing reconciliation"
)
@Scheduled(cron = "0 0 2 * * *")
public void reconcileBilling() {
    // ...
}
```

Если `id` пуст, cronctl формирует ключ следующим образом:

```text
spring.application.name + "." + beanName + "." + methodSignature
```

Например, `billing-service.reconciliationScheduler.reconcileBilling()`.
Если `spring.application.name` не настроен, используется `application`. Явные ID также
поддерживают Spring placeholders. Дублирующиеся ключи приводят к ошибке регистрации при запуске.

Ключи задач возвращаются строками в `task_key`. ID запусков — отдельные случайные UUID,
которые создаются для каждого автоматического или ручного выполнения.

## Режимы сканирования

Параметр `cronctl.scan.type` определяет, какие методы с `@Scheduled` регистрируются:

| Режим       | Поведение                                                                             |
|-------------|---------------------------------------------------------------------------------------|
| `AUTO`      | Все методы с `@Scheduled`, кроме помеченных `@CronctlTask.Exclude` **(по умолчанию)** |
| `ANNOTATED` | Только методы, явно помеченные `@CronctlTask`                                         |
| `PACKAGE`   | Все методы с `@Scheduled` в указанных пакетах, кроме помеченных `@Exclude`            |

**Режим ANNOTATED** — регистрируются только явно включённые методы:

```yaml
cronctl:
  scan:
    type: ANNOTATED
```

**Режим PACKAGE** — регистрируются методы только из заданных пакетов:

```yaml
cronctl:
  scan:
    type: PACKAGE
    base-packages:
      - ru.example.billing
      - ru.example.reporting
```

## Операторский UI

Если в classpath присутствует Thymeleaf, cronctl предоставляет операторский дашборд:

```text
http://localhost:8080/api/cronctl/ui
```

[Открыть интерактивную демоверсию операторского UI](https://syntezis-ru.github.io/cronctl/).
Демоверсия использует симулятор планировщика в браузере, поэтому действия pause, resume,
run и cancel не запускают серверные задачи.

[![Операторский UI cronctl](docs/images/cronctl-operator-ui.png)](https://syntezis-ru.github.io/cronctl/)

Путь следует за `cronctl.api.base-path`: при base path `/internal/scheduler` дашборд будет
доступен по адресу `/internal/scheduler/ui`. UI использует ту же политику доступа, что и
REST API (`cronctl.api.public-access`).

Дашборд предоставляет:

- временную шкалу ближайших запусков и фильтры задач;
- параметры расписания, текущее состояние enabled и индикаторы ограничивающих concurrency policies;
- pause с подтверждением, опциональным флагом `interrupt` и немедленный resume;
- ручной асинхронный запуск с подтверждением;
- подробную историю автоматических и ручных запусков, включая источник, экземпляр приложения,
  плановое и фактическое время старта, отклонение, длительность и детали ошибки;
- серверную фильтрацию и пагинацию истории, а также отмену ручных асинхронных запусков;
- состояние задач по расписанию: последний автоматический статус, последний успех и число
  последовательных ошибок.

Задачи обновляются каждые 10 секунд. Запуски обновляются каждые 2 секунды, пока работа ожидает
начала или выполняется, и каждые 10 секунд в остальных случаях. В скрытой вкладке браузера
опрос приостанавливается.

Отключить только операторский UI, сохранив API:

```yaml
cronctl:
  ui:
    enabled: false
```

Swagger UI остаётся доступным независимо как документация API.

## API

Базовый путь: `/api/cronctl` (настраивается через `cronctl.api.base-path`)

### GET /api/cronctl/tasks

Возвращает зарегистрированные задачи с `@Scheduled`. Поддерживает опциональную фильтрацию
по `group` и `tag`. Если переданы оба параметра, возвращаются только задачи, соответствующие
обоим условиям.

| Параметр | Тип    | Обязательный | Описание                                     |
|----------|--------|--------------|----------------------------------------------|
| `group`  | string | нет          | Возвращать только задачи из указанной группы |
| `tag`    | string | нет          | Возвращать только задачи с указанным тегом   |

```bash
# Все задачи
curl http://localhost:8080/api/cronctl/tasks

# Фильтрация по группе
curl http://localhost:8080/api/cronctl/tasks?group=integration

# Фильтрация по тегу
curl http://localhost:8080/api/cronctl/tasks?tag=critical

# Фильтрация по обоим условиям (AND)
curl "http://localhost:8080/api/cronctl/tasks?group=integration&tag=critical"
```

```json
{
  "tasks": [
    {
      "task_key": "integration.sync-data",
      "label": "Sync Data",
      "description": "Pulls updates from the remote source",
      "group": "integration",
      "tags": [
        "sync",
        "critical"
      ],
      "enabled": true,
      "toggling_enabled": true,
      "timeout_seconds": 30,
      "concurrency_policy": "SKIP",
      "max_concurrent_executions": 1,
      "next_execution_at": "2024-05-01T12:01:00Z",
      "automatic_tracking_status": "ACTIVE",
      "automatic_tracking_message": null,
      "scheduled_health": {
        "last_execution_at": "2024-05-01T12:00:00.050Z",
        "last_execution_status": "SUCCEEDED",
        "last_success_at": "2024-05-01T12:00:00.050Z",
        "consecutive_failures": 0
      },
      "details": {
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

В `schedule` присутствуют только фактически настроенные поля. Незаданные поля (`cron`,
`fixed_delay` и другие) в ответ не включаются.

`next_execution_at` — метка времени следующего запуска в формате ISO-8601 UTC. Значение может
быть `null`, пока Spring не предоставил следующий слот, задача выполняется или просрочена либо
находится на паузе.

`scheduled_health` рассчитывается исключительно по запускам `SCHEDULED`. Ручные запуски не
сбрасывают счётчик ошибок и не меняют время последнего успешного выполнения.
`automatic_tracking_status` имеет значение `AMBIGUOUS`, если несколько экземпляров бинов
предоставляют один и тот же объявляющий класс и метод. В этом случае cronctl намеренно не
привязывает автоматические наблюдения к потенциально неверному стабильному ключу.

### GET /api/cronctl/tasks/{taskKey}/next-execution

Возвращает время следующего запуска одной задачи.

```bash
curl http://localhost:8080/api/cronctl/tasks/integration.sync-data/next-execution
```

```json
{
  "task_key": "integration.sync-data",
  "next_execution_at": "2024-05-01T12:01:00Z"
}
```

Для неизвестного ключа задачи возвращается `404`. Поле `next_execution_at` равно `null`,
если Spring в данный момент не предоставляет будущий запуск.

### POST /api/cronctl/tasks/{taskKey}/disable

Приостанавливает автоматические запуски по расписанию. Задача остаётся зарегистрированной
и по-прежнему может быть запущена вручную. Отключать можно только задачи, объявленные с
`@CronctlTask(togglingEnabled = true)`.

```bash
curl -X POST "http://localhost:8080/api/cronctl/tasks/integration.sync-data/disable?interrupt=false"
```

По умолчанию `interrupt=false`. Успешный запрос возвращает обновлённую задачу с
`enabled=false`. Для неизвестной задачи возвращается `404`, а если переключение запрещено — `409`.

### POST /api/cronctl/tasks/{taskKey}/enable

Возобновляет автоматические запуски с исходной конфигурацией cron, fixed-rate или fixed-delay.

```bash
curl -X POST http://localhost:8080/api/cronctl/tasks/integration.sync-data/enable
```

Успешный запрос возвращает обновлённую задачу с `enabled=true`. Повторные запросы enable
и disable идемпотентны.

### История и lifecycle запусков

cronctl записывает нативные вызовы Spring `@Scheduled` и оба режима ручного выполнения
в единую модель. Каждая запись содержит источник запуска:

| Источник       | Значение                                          |
|----------------|---------------------------------------------------|
| `SCHEDULED`    | Автоматический запуск планировщиком Spring        |
| `MANUAL_SYNC`  | Запуск через блокирующий HTTP-эндпоинт            |
| `MANUAL_ASYNC` | Отправка в ограниченный executor cronctl          |
| `RETRY`        | Автоматический или запрошенный оператором повтор  |
| `CATCH_UP`     | Зарезервирован для будущей политики наверстывания |

Lifecycle имеет вид `CREATED → QUEUED → RUNNING`, после чего устанавливается один
из терминальных статусов:

| Статус      | Описание                                                      |
|-------------|---------------------------------------------------------------|
| `CREATED`   | Запись о запуске создана                                      |
| `QUEUED`    | Запуск принят и ожидает начала                                |
| `RUNNING`   | Метод выполняется                                             |
| `SUCCEEDED` | Метод завершился штатно                                       |
| `FAILED`    | Метод выбросил исключение; подробности находятся в `error`    |
| `CANCELLED` | Запрошена отмена или прерывание запуска по расписанию         |
| `TIMED_OUT` | Асинхронный или повторный запуск превысил эффективный таймаут |
| `SKIPPED`   | Запуск не начался; причина находится в `status_reason`        |

Для автоматических запусков `planned_at` и `start_delay_ms` заполняются, когда Spring
предоставляет точный плановый слот. Если определить его невозможно, поля равны `null`.
`node_id` идентифицирует экземпляр приложения, который зафиксировал запуск.

#### POST /api/cronctl/tasks/{taskKey}/execute

Выполняет задачу синхронно и возвращает итоговую унифицированную запись. HTTP-ответ имеет
статус `200`, даже если метод завершился ошибкой; проверяйте `status` и `error`. Отклонение
политикой конкурентного выполнения возвращает `429` с `status=SKIPPED` и
`status_reason=CONCURRENT_EXECUTION`.

```bash
curl -X POST http://localhost:8080/api/cronctl/tasks/integration.sync-data/execute
```

#### POST /api/cronctl/tasks/{taskKey}/execute-async

Ставит ручной асинхронный запуск в очередь и возвращает `202 Accepted`. При переполнении
очереди возвращается `429` и сохраняется запись `SKIPPED` с `status_reason=QUEUE_REJECTED`.
Если worker достигает лимита политики `SKIP`, принятая запись переходит в `SKIPPED` с
`status_reason=CONCURRENT_EXECUTION`.

```bash
curl -X POST http://localhost:8080/api/cronctl/tasks/integration.sync-data/execute-async
```

`POST /tasks/{taskKey}/executions` сохранён как устаревший псевдоним.

#### Унифицированный ответ о запуске

Эндпоинты синхронного запуска, асинхронной отправки, статуса и истории используют
одно представление:

```json
{
  "execution_id": "a1b2c3d4-0000-0000-0000-000000000001",
  "task_key": "integration.sync-data",
  "source": "SCHEDULED",
  "status": "SUCCEEDED",
  "node_id": "billing-service-1",
  "created_at": "2024-05-01T12:00:00Z",
  "queued_at": "2024-05-01T12:00:00Z",
  "planned_at": "2024-05-01T12:00:00Z",
  "started_at": "2024-05-01T12:00:00.050Z",
  "finished_at": "2024-05-01T12:00:00.173Z",
  "start_delay_ms": 50,
  "duration_ms": 123,
  "status_reason": null,
  "parent_execution_id": null,
  "root_execution_id": "a1b2c3d4-0000-0000-0000-000000000001",
  "retry_series_id": "a1b2c3d4-0000-0000-0000-000000000001",
  "attempt": 1,
  "retry_trigger": null,
  "retryable": false,
  "error": null
}
```

#### Ручной повтор

`POST /api/cronctl/executions/{executionId}/retry` немедленно создаёт повтор для конечного
запуска `FAILED` или `TIMED_OUT` и возвращает `202`. Ручной повтор доступен даже при
`retries=0`; явное действие оператора начинает новую серию. Повторный запрос для запуска,
у которого уже есть дочерняя запись, возвращает `409`.

`POST /api/cronctl/executions/retry-all-failed` повторяет не более одного последнего
подходящего сбоя для каждой зарегистрированной задачи. UI предоставляет оба действия
и запрашивает подтверждение перед Retry all.

При ошибке поле `error` содержит тип исключения и сообщение.

#### GET /api/cronctl/executions/{executionId}

Возвращает любой автоматический или ручной запуск по UUID либо `404`, если запуск неизвестен
или уже удалён по политике хранения.

#### DELETE /api/cronctl/executions/{executionId}

Запрашивает прерывание активного запуска `MANUAL_ASYNC` или `RETRY`. При принятии запроса
возвращается `204`, для другого источника или терминального запуска — `409`, для неизвестного
UUID — `404`.

#### GET /api/cronctl/executions

Возвращает историю от новых записей к старым с серверной пагинацией.

| Параметр  | Тип     | По умолчанию | Описание                                           |
|-----------|---------|--------------|----------------------------------------------------|
| `taskKey` | string  | —            | Точный стабильный ключ задачи                      |
| `status`  | enum    | —            | Статус запуска                                     |
| `source`  | enum    | —            | Источник запуска                                   |
| `nodeId`  | string  | —            | Точный идентификатор экземпляра приложения         |
| `from`    | instant | —            | Включать записи, созданные в этот момент или позже |
| `to`      | instant | —            | Включать записи, созданные раньше этого момента    |
| `page`    | integer | `0`          | Номер страницы с нуля                              |
| `size`    | integer | `50`         | Размер страницы, не более `200`                    |

```bash
curl "http://localhost:8080/api/cronctl/executions?source=SCHEDULED&status=FAILED&page=0&size=50"
```

```json
{
  "executions": [],
  "total": 0,
  "page": 0,
  "size": 50,
  "has_next": false
}
```

Стандартный `InMemoryExecutionStore` хранит не более 1 000 терминальных записей. Лимит
применяется сразу, а записи старше семи дней удаляются каждые десять минут. Активные запуски
никогда не вытесняются. История локальна для процесса и теряется после перезапуска.
Состояние задач по расписанию остаётся доступным до завершения процесса, даже если отдельные
записи уже удалены.

Объявите собственный бин `ExecutionStore`, чтобы заменить стандартное хранилище без изменения
lifecycle или API. Пользовательское хранилище должно реализовать `deleteExpired(Instant threshold)`;
cronctl вызывает этот метод по тому же расписанию хранения. Пока cronctl не обнаруживает запуски,
пропущенные во время остановки приложения; `CATCH_UP` зарезервирован для будущей политики.

Маркеры паузы задач хранятся через отдельный SPI `TaskStateStore`. Стандартный
`InMemoryTaskStateStore` потокобезопасен, но локален для процесса, поэтому паузы не переживают
перезапуск. Объявите собственный бин `TaskStateStore` с персистентным хранилищем, чтобы восстанавливать задачи
на паузе при старте. Неизвестные сохранённые ключи остаются в хранилище, а маркеры задач,
которые больше нельзя переключать, удаляются. Ручные синхронные и асинхронные запуски доступны,
пока задача находится на паузе. Реализации для JDBC, Redis и MongoDB в эту версию не включены.

## Программная конфигурация

Вместо `application.yml` cronctl можно настроить, объявив бин `CronctlConfiguration`.
Применяются только явно заданные поля builder; остальные значения по-прежнему берутся
из `application.yml` и встроенных настроек cronctl.

```java
@Bean
public CronctlConfiguration cronctlConfiguration() {
    return CronctlConfiguration.builder()
            .basePath("/internal/scheduler")
            .scanType(ScanType.ANNOTATED)
            .apiPublicAccess(false)
            .uiEnabled(true)
            .executorThreadPoolSize(8)
            .executorTimeoutSeconds(120)
            .historyMaxEntries(20_000)
            .historyRetention(Duration.ofDays(14))
            .historyCleanupInterval(Duration.ofMinutes(10))
            .historyNodeId("billing-service-1")
            .build();
}
```

Поля builder напрямую соответствуют параметрам в `application.yml`:

| Поле builder             | Соответствующее свойство            |
|--------------------------|-------------------------------------|
| `basePath`               | `cronctl.api.base-path`             |
| `apiPublicAccess`        | `cronctl.api.public-access`         |
| `swaggerPublicAccess`    | `cronctl.swagger.public-access`     |
| `swaggerGroup`           | `cronctl.swagger.group`             |
| `swaggerPathsToMatch`    | `cronctl.swagger.paths-to-match`    |
| `uiEnabled`              | `cronctl.ui.enabled`                |
| `scanType`               | `cronctl.scan.type`                 |
| `scanBasePackages`       | `cronctl.scan.base-packages`        |
| `executorThreadPoolSize` | `cronctl.executor.thread-pool-size` |
| `executorQueueCapacity`  | `cronctl.executor.queue-capacity`   |
| `executorTimeoutSeconds` | `cronctl.executor.timeout-seconds`  |
| `historyMaxEntries`      | `cronctl.history.max-entries`       |
| `historyRetention`       | `cronctl.history.retention`         |
| `historyCleanupInterval` | `cronctl.history.cleanup-interval`  |
| `historyNodeId`          | `cronctl.history.node-id`           |

> **Приоритет:** программно объявленный бин имеет приоритет над `application.yml`,
> а `application.yml` — над встроенными значениями cronctl.

## Конфигурация

Все свойства опциональны. Значения по умолчанию подходят для запуска без дополнительной настройки.

| Свойство                            | По умолчанию      | Описание                                                                                  |
|-------------------------------------|-------------------|-------------------------------------------------------------------------------------------|
| `cronctl.enabled`                   | `true`            | `false` полностью отключает библиотеку: бины и эндпоинты не создаются                     |
| `cronctl.api.base-path`             | `/api/cronctl`    | Базовый путь всех REST-эндпоинтов cronctl                                                 |
| `cronctl.api.public-access`         | `true`            | При `false` для вызова API требуется аутентификация                                       |
| `cronctl.swagger.public-access`     | `true`            | При `false` для доступа к Swagger UI требуется аутентификация                             |
| `cronctl.swagger.group`             | `cronctl`         | Имя группы в Swagger UI                                                                   |
| `cronctl.swagger.paths-to-match`    | `/api/cronctl/**` | Шаблон путей, включаемых в группу cronctl в Swagger                                       |
| `cronctl.ui.enabled`                | `true`            | Включает операторский UI при наличии Thymeleaf                                            |
| `cronctl.scan.type`                 | `AUTO`            | Режим `AUTO`, `ANNOTATED` или `PACKAGE` (см. [Режимы сканирования](#режимы-сканирования)) |
| `cronctl.scan.base-packages`        | `[]`              | Пакеты для сканирования в режиме `PACKAGE`                                                |
| `cronctl.executor.thread-pool-size` | `4`               | Число потоков в пуле асинхронного выполнения                                              |
| `cronctl.executor.queue-capacity`   | `100`             | Максимальное число задач в очереди                                                        |
| `cronctl.executor.timeout-seconds`  | `60`              | Глобальный таймаут асинхронного запуска в секундах; `0` отключает таймаут                 |
| `cronctl.history.max-entries`       | `1000`            | Максимум терминальных записей в стандартном in-memory хранилище                           |
| `cronctl.history.retention`         | `7d`              | Максимальный возраст терминальных записей в стандартном in-memory хранилище               |
| `cronctl.history.cleanup-interval`  | `10m`             | Интервал фонового удаления устаревших терминальных записей                                |
| `cronctl.history.node-id`           | вычисляется       | ID экземпляра: instance ID, `HOSTNAME` либо имя приложения со случайным UUID процесса     |

Готовые примеры для пользовательских путей, защищённого API, production и других сценариев
находятся в каталоге [`docs/config-examples/`](docs/config-examples/).

## Безопасность

По умолчанию все эндпоинты cronctl доступны без аутентификации, чтобы упростить первый запуск.

**Чтобы требовать аутентификацию для API:**

```yaml
cronctl:
  api:
    public-access: false
```

**Чтобы требовать аутентификацию для Swagger UI:**

```yaml
cronctl:
  swagger:
    public-access: false
```

Аутентификация делегируется существующей конфигурации Spring Security приложения.
Операторский UI наследует `cronctl.api.public-access`. cronctl регистрирует собственный
`SecurityFilterChain`, ограниченный его эндпоинтами, и не вмешивается в безопасность
остальной части приложения.

## Отключение cronctl

Чтобы отключить cronctl в отдельном окружении, например production, не удаляя зависимость:

```yaml
# application-prod.yml
cronctl:
  enabled: false
```

## Лицензия

Проект распространяется по лицензии [Apache License 2.0](LICENSE).

---

<div style="text-align: center;">
  <a href="https://syntezis.ru">
    <img src="https://syntezis.ru/img/logo.svg" alt="Syntezis" height="40"/>
  </a>
  <br/>
  <sub>Разработано и поддерживается <a href="https://syntezis.ru">Syntezis</a></sub>
</div>
