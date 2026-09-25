package ru.syntezis.cronctl.domain.execution;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/** One page of execution history sorted from newest to oldest. */
@Getter
@AllArgsConstructor
public class ExecutionPage {

    private final List<TaskExecution> executions;
    private final long total;
    private final int page;
    private final int size;

    public boolean hasNext() {
        return (long) (page + 1) * size < total;
    }

}
