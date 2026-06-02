package ru.syntezis.cronctl.presentation.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import ru.syntezis.cronctl.domain.scheduled.ScheduledMethodDetails;
import ru.syntezis.cronctl.presentation.dto.ScheduledMethodDetailsDto;

/** MapStruct mapper converting {@link ru.syntezis.cronctl.domain.scheduled.ScheduledMethodDetails} to {@link ru.syntezis.cronctl.presentation.dto.ScheduledMethodDetailsDto}. */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR, componentModel = MappingConstants.ComponentModel.SPRING)
public interface ScheduledMethodDetailsMapper {

    /** Converts scheduled method details domain object to DTO. */
    ScheduledMethodDetailsDto toDto(ScheduledMethodDetails scheduledMethodDetails);

}