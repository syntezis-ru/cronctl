package ru.syntezis.cronctl.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Result of retrying the latest failed execution for every eligible task. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Batch retry submission result")
public class RetryExecutionsResponseDto {

    @JsonProperty("executions")
    @Builder.Default
    private List<ExecutionStatusDto> executions = new ArrayList<>();

    @JsonProperty("submitted")
    private int submitted;

}
