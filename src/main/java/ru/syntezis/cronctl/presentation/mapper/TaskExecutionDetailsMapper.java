package ru.syntezis.cronctl.presentation.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import ru.syntezis.cronctl.domain.TaskExecutionDetails;
import ru.syntezis.cronctl.presentation.dto.TaskExecutionResultDto;

@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR, componentModel = MappingConstants.ComponentModel.SPRING)
public interface TaskExecutionDetailsMapper {

    TaskExecutionResultDto toDto(TaskExecutionDetails taskExecutionDetails);

}