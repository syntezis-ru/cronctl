package ru.syntezis.cronctl.presentation.mapper;

import org.jspecify.annotations.Nullable;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import ru.syntezis.cronctl.domain.scheduled.ScheduleDetails;
import ru.syntezis.cronctl.presentation.dto.ScheduleDetailsDto;

/** MapStruct mapper converting {@link ru.syntezis.cronctl.domain.scheduled.ScheduleDetails} to {@link ru.syntezis.cronctl.presentation.dto.ScheduleDetailsDto}. */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR, componentModel = MappingConstants.ComponentModel.SPRING)
public interface ScheduleDetailsMapper {

    /** Converts schedule details domain object to DTO. */
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

    /** Returns {@code null} if the value is empty or blank, otherwise the value unchanged. */
    default @Nullable String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }

    /** Returns {@code null} if the value is {@code -1} (unset Spring annotation attribute), otherwise the value. */
    default @Nullable Long negOneToNull(long value) {
        return value == -1L ? null : value;
    }

}