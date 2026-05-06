package ru.syntezis.cronctl.presentation.dto;

import lombok.Builder;
import lombok.Data;
import ru.syntezis.cronctl.domain.Task;

import java.util.List;

@Data
@Builder
public class TasksResponseDto {

    private List<Task> tasks;

}
