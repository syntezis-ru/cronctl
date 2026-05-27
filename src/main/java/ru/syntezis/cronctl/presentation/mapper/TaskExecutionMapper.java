package ru.syntezis.cronctl.presentation.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import ru.syntezis.cronctl.domain.execution.TaskExecution;
import ru.syntezis.cronctl.presentation.dto.ExecutionStatusDto;
import ru.syntezis.cronctl.presentation.dto.ExecutionSubmittedDto;

@Mapper(
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        componentModel = MappingConstants.ComponentModel.SPRING,
        uses = {
                FailDetailsMapper.class
        }
)
public interface TaskExecutionMapper {

    @Mapping(source = "state", target = "status")
    ExecutionSubmittedDto toSubmittedDto(TaskExecution execution);

    @Mapping(source = "state", target = "status")
    @Mapping(source = "result.executionDurationMills", target = "executionDurationMills")
    @Mapping(source = "result.failDetails", target = "failDetails")
    ExecutionStatusDto toStatusDto(TaskExecution execution);

}
