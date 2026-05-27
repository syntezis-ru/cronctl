package ru.syntezis.cronctl.presentation.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
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
    TaskResponseDto toDto(Task task);

}