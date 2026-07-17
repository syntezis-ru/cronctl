package ru.syntezis.cronctl.presentation.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import ru.syntezis.cronctl.core.NextExecutionTimeResolver;
import ru.syntezis.cronctl.domain.task.Task;
import ru.syntezis.cronctl.presentation.dto.TaskResponseDto;

@Mapper(
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        uses = {
                ScheduleDetailsMapper.class,
                ScheduledMethodDetailsMapper.class
        },
        componentModel = MappingConstants.ComponentModel.SPRING
)
public interface TaskMapper {

    @Mapping(target = "details.id", source = "task.id")
    @Mapping(target = "details.methodName", source = "task.details.methodName")
    @Mapping(target = "details.schedule", source = "task.details.schedule")
    @Mapping(target = "timeoutSeconds", source = "task.timeoutSeconds")
    @Mapping(target = "nextExecutionAt", ignore = true)
    TaskResponseDto toDto(Task task);

    default TaskResponseDto toDto(Task task, NextExecutionTimeResolver resolver) {
        TaskResponseDto dto = toDto(task);
        dto.setNextExecutionAt(resolver.computeNextExecutionAt(task));
        return dto;
    }
}