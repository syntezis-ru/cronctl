package ru.syntezis.cronctl.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.syntezis.cronctl.domain.scheduled.ScheduleDetails;

import java.util.concurrent.TimeUnit;

/**
 * DTO for {@link ScheduleDetails}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Schedule configuration of a @Scheduled method")
public class ScheduleDetailsDto {

    @JsonProperty("cron")
    @Schema(description = "Cron expression", example = "0 */5 * * * *")
    private String cron;

    @JsonProperty("zone")
    @Schema(description = "Time zone for cron expression", example = "Europe/Moscow")
    private String zone;

    @JsonProperty("fixed_rate")
    @Schema(description = "Fixed rate; unit is defined by time_unit field", example = "5000")
    private Long fixedRate;

    @JsonProperty("fixed_rate_string")
    @Schema(description = "Fixed rate as a string expression", example = "${my.rate}")
    private String fixedRateString;

    @JsonProperty("fixed_delay")
    @Schema(description = "Fixed delay; unit is defined by time_unit field", example = "3000")
    private Long fixedDelay;

    @JsonProperty("fixed_delay_string")
    @Schema(description = "Fixed delay as a string expression", example = "${my.delay}")
    private String fixedDelayString;

    @JsonProperty("initial_delay")
    @Schema(description = "Initial delay; unit is defined by time_unit field", example = "1000")
    private Long initialDelay;

    @JsonProperty("initial_delay_string")
    @Schema(description = "Initial delay as a string expression", example = "${my.initial-delay}")
    private String initialDelayString;

    @JsonProperty("time_unit")
    @Schema(description = "Time unit for fixed rate and delay values", example = "MILLISECONDS")
    private TimeUnit timeUnit;

    @JsonProperty("scheduler")
    @Schema(description = "Custom scheduler bean name", example = "myTaskScheduler")
    private String scheduler;

}
