package ru.syntezis.cronctl.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Delegate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "List of all registered @Scheduled tasks")
public class TasksResponseDto implements Collection<TaskResponseDto> {

    @JsonProperty("tasks")
    @Delegate
    @Builder.Default
    @Schema(description = "Registered tasks")
    private final List<TaskResponseDto> tasks = new ArrayList<>();

    @JsonProperty("total")
    @Schema(description = "Total number of registered tasks", example = "3")
    public int getTotal() {
        return tasks.size();
    }
}
