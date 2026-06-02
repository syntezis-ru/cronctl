package ru.syntezis.cronctl.presentation.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import ru.syntezis.cronctl.domain.task.TaskExecutionDetails;
import ru.syntezis.cronctl.presentation.dto.FailDetailsDto;

/** MapStruct mapper converting {@link ru.syntezis.cronctl.domain.task.TaskExecutionDetails.FailDetails} to {@link ru.syntezis.cronctl.presentation.dto.FailDetailsDto}. */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR, componentModel = MappingConstants.ComponentModel.SPRING)
public interface FailDetailsMapper {

    /** Converts fail details domain object to DTO. */
    FailDetailsDto toDto(TaskExecutionDetails.FailDetails failDetails);

}