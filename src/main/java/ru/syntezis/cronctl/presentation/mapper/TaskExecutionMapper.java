package ru.syntezis.cronctl.presentation.mapper;

import org.springframework.stereotype.Component;
import ru.syntezis.cronctl.domain.execution.TaskExecution;
import ru.syntezis.cronctl.presentation.dto.ExecutionStatusDto;
import ru.syntezis.cronctl.presentation.dto.FailDetailsDto;

/** Maps unified execution domain objects to REST DTOs. */
@Component
public class TaskExecutionMapper {

    public ExecutionStatusDto toDto(TaskExecution execution) {
        FailDetailsDto error = execution.getErrorType() == null
                ? null
                : FailDetailsDto.builder()
                        .type(execution.getErrorType())
                        .message(execution.getErrorMessage())
                        .build();
        return ExecutionStatusDto.builder()
                .executionId(execution.getExecutionId())
                .taskKey(execution.getTaskKey())
                .source(execution.getSource())
                .status(execution.getStatus())
                .nodeId(execution.getNodeId())
                .createdAt(execution.getCreatedAt())
                .queuedAt(execution.getQueuedAt())
                .plannedAt(execution.getPlannedAt())
                .startedAt(execution.getStartedAt())
                .finishedAt(execution.getFinishedAt())
                .startDelayMs(execution.getStartDelayMillis())
                .durationMs(execution.getDurationMillis())
                .statusReason(execution.getStatusReason())
                .parentExecutionId(execution.getParentExecutionId())
                .rootExecutionId(execution.getRootExecutionId())
                .retrySeriesId(execution.getRetrySeriesId())
                .attempt(execution.getAttempt())
                .retryTrigger(execution.getRetryTrigger())
                .error(error)
                .build();
    }

    public ExecutionStatusDto toDto(TaskExecution execution, boolean retryable) {
        ExecutionStatusDto dto = toDto(execution);
        dto.setRetryable(retryable);
        return dto;
    }

}
