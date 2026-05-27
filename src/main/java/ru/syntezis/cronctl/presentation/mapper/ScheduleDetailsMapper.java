package ru.syntezis.cronctl.presentation.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import ru.syntezis.cronctl.domain.scheduled.ScheduleDetails;
import ru.syntezis.cronctl.presentation.dto.ScheduleDetailsDto;

@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR, componentModel = MappingConstants.ComponentModel.SPRING)
public interface ScheduleDetailsMapper {

    ScheduleDetailsDto toDto(ScheduleDetails scheduleDetails);

}