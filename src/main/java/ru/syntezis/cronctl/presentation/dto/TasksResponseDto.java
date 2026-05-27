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
@Schema(description = "List of all registered @Scheduled tasks")
public class TasksResponseDto {

    @JsonProperty("tasks")
    @Builder.Default
    @Schema(description = "Registered tasks")
    private List<TaskResponseDto> tasks = new ArrayList<>();

    /**
     * Returns the number of tasks in this response.
     */
    @JsonProperty("total")
    @Schema(description = "Total number of registered tasks", example = "3")
    public int getTotal() {
        return tasks.size();
    }
}
