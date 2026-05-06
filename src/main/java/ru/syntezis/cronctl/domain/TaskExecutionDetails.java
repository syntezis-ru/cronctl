package ru.syntezis.cronctl.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ru.syntezis.cronctl.enums.TaskExecutionStatus;

@NoArgsConstructor
public class TaskExecutionDetails {

    @Getter
    private long preparationStartMills;

    @Getter
    private long preparationEndMills;

    @Getter
    private long methodExecutionStartMills;

    @Getter
    private long methodExecutionEndMills;

    @Getter
    private TaskExecutionStatus status;

    @Getter
    private FailDetails failDetails;

    public static TaskExecutionDetails prepare() {
        TaskExecutionDetails details = new TaskExecutionDetails();
        details.preparationStartMills = System.currentTimeMillis();
        details.status = TaskExecutionStatus.PENDING;
        return details;
    }

    public void execute() {
        this.preparationEndMills = System.currentTimeMillis();
        this.status = TaskExecutionStatus.RUNNING;
        this.methodExecutionStartMills = System.currentTimeMillis();
    }

    public TaskExecutionDetails failed(Throwable throwable, String errorMessage) {
        this.methodExecutionEndMills = System.currentTimeMillis();
        this.status = TaskExecutionStatus.FAILED;
        this.failDetails = new FailDetails(throwable, errorMessage);
        return this;
    }

    public TaskExecutionDetails succeeded() {
        this.methodExecutionEndMills = System.currentTimeMillis();
        this.status = TaskExecutionStatus.SUCCEEDED;
        return this;
    }

    @Getter
    @AllArgsConstructor
    public static class FailDetails {

        private final Throwable throwable;
        private final String message;

    }
}
