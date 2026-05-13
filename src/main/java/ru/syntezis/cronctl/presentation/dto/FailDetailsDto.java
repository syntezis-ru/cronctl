package ru.syntezis.cronctl.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Details of a task execution failure")
public class FailDetailsDto {

    @JsonProperty("message")
    @Schema(description = "Error message from the exception", example = "Connection refused")
    private String message;

}
