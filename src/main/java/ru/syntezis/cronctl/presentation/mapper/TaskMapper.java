package ru.syntezis.cronctl.presentation.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import ru.syntezis.cronctl.core.NextExecutionTimeResolver;
import ru.syntezis.cronctl.core.execution.ExecutionStore;
import ru.syntezis.cronctl.domain.execution.ScheduledTaskHealth;
import ru.syntezis.cronctl.domain.task.Task;
import ru.syntezis.cronctl.presentation.dto.ScheduledTaskHealthDto;
import ru.syntezis.cronctl.presentation.dto.TaskResponseDto;

import java.time.Duration;
import java.util.List;

@Mapper(
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        uses = {
                ScheduleDetailsMapper.class,
                ScheduledMethodDetailsMapper.class
        },
        componentModel = MappingConstants.ComponentModel.SPRING
)
public interface TaskMapper {

    @Mapping(target = "taskKey", source = "task.taskKey")
    @Mapping(target = "details.methodName", source = "task.details.methodName")
    @Mapping(target = "details.schedule", source = "task.details.schedule")
    @Mapping(target = "timeoutSeconds", source = "task.timeoutSeconds")
    @Mapping(target = "concurrencyPolicy", source = "task.concurrencyPolicy")
    @Mapping(target = "maxConcurrentExecutions", source = "task.maxConcurrentExecutions")
    @Mapping(target = "retries", source = "task.retryPolicy.retries")
    @Mapping(target = "retryDelay", source = "task.retryPolicy.delay")
    @Mapping(target = "retryBackoff", source = "task.retryPolicy.backoff")
    @Mapping(target = "maxRetryDelay", source = "task.retryPolicy.maxDelay")
    @Mapping(target = "retryJitter", source = "task.retryPolicy.jitter")
    @Mapping(target = "retryOn", source = "task.retryPolicy.retryOn")
    @Mapping(target = "nonRetryableOn", source = "task.retryPolicy.nonRetryableOn")
    @Mapping(target = "nextExecutionAt", ignore = true)
    @Mapping(target = "scheduledHealth", ignore = true)
    TaskResponseDto toDto(Task task);

    default String map(Duration duration) {
        return duration.toString();
    }

    default List<String> map(List<Class<? extends Throwable>> types) {
        return types.stream().map(Class::getName).toList();
    }

    default TaskResponseDto toDto(Task task, NextExecutionTimeResolver resolver, ExecutionStore executionStore) {
        TaskResponseDto dto = toDto(task);
        dto.setNextExecutionAt(resolver.computeNextExecutionAt(task));
        ScheduledTaskHealth health = executionStore.getScheduledHealth(task.getTaskKey());
        dto.setScheduledHealth(ScheduledTaskHealthDto.builder()
                .lastExecutionAt(health.getLastExecutionAt())
                .lastExecutionStatus(health.getLastExecutionStatus())
                .lastSuccessAt(health.getLastSuccessAt())
                .consecutiveFailures(health.getConsecutiveFailures())
                .build());
        return dto;
    }
}
