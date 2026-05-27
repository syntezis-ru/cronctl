package ru.syntezis.cronctl.presentation.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import ru.syntezis.cronctl.domain.scheduled.ScheduleDetails;
import ru.syntezis.cronctl.presentation.dto.ScheduleDetailsDto;

@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR, componentModel = MappingConstants.ComponentModel.SPRING)
public interface ScheduleDetailsMapper {

    @Mapping(target = "cron",               expression = "java(emptyToNull(s.getCron()))")
    @Mapping(target = "zone",               expression = "java(emptyToNull(s.getZone()))")
    @Mapping(target = "fixedRate",          expression = "java(negOneToNull(s.getFixedRate()))")
    @Mapping(target = "fixedRateString",    expression = "java(emptyToNull(s.getFixedRateString()))")
    @Mapping(target = "fixedDelay",         expression = "java(negOneToNull(s.getFixedDelay()))")
    @Mapping(target = "fixedDelayString",   expression = "java(emptyToNull(s.getFixedDelayString()))")
    @Mapping(target = "initialDelay",       expression = "java(negOneToNull(s.getInitialDelay()))")
    @Mapping(target = "initialDelayString", expression = "java(emptyToNull(s.getInitialDelayString()))")
    @Mapping(target = "scheduler",          expression = "java(emptyToNull(s.getScheduler()))")
    ScheduleDetailsDto toDto(ScheduleDetails s);

    default String emptyToNull(String value) {
        return (value == null || value.isEmpty()) ? null : value;
    }

    default Long negOneToNull(long value) {
        return value == -1L ? null : value;
    }

}