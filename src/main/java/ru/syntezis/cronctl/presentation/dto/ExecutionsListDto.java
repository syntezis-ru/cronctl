package ru.syntezis.cronctl.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "List of async task executions")
public class ExecutionsListDto {

    @JsonProperty("executions")
    @Builder.Default
    @Schema(description = "Executions matching the requested filter")
    private List<ExecutionStatusDto> executions = new ArrayList<>();

    /**
     * Returns the number of executions in this response.
     */
    @JsonProperty("total")
    @Schema(description = "Total number of executions in the response", example = "2")
    public int getTotal() {
        return executions.size();
    }
}
