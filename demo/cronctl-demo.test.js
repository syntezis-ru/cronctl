const assert = require("node:assert/strict");
const test = require("node:test");

test("request_TaskActions_StateTransitions", async () => {
    // Given
    const storage = new Map();
    global.sessionStorage = {
        getItem: key => storage.has(key) ? storage.get(key) : null,
        setItem: (key, value) => storage.set(key, value),
        removeItem: key => storage.delete(key)
    };
    global.document = {querySelector: () => null};
    global.window = {
        location: {
            origin: "https://syntezis-ru.github.io",
            reload: () => {}
        }
    };
    require("./cronctl-demo.js");
    const transport = window.cronctlTransport;

    // When
    const initialResponse = await transport.request("/tasks");
    const initialTask = initialResponse.tasks.find(task => task.label === "Simple Job");
    const heavyTask = initialResponse.tasks.find(task => task.label === "Heavy Job");
    const notificationsTask = initialResponse.tasks.find(task => task.label === "Send Notifications");
    const disabledTask = await transport.request(
        `/tasks/${initialTask.task_key}/disable?interrupt=false`,
        {method: "POST"}
    );
    const enabledTask = await transport.request(
        `/tasks/${initialTask.task_key}/enable`,
        {method: "POST"}
    );
    const submittedExecution = await transport.request(
        `/tasks/${initialTask.task_key}/execute-async`,
        {method: "POST"}
    );
    await transport.request(
        `/executions/${submittedExecution.execution_id}`,
        {method: "DELETE"}
    );
    const executionsResponse = await transport.request("/executions?page=0&size=2&source=MANUAL_ASYNC");
    const cancelledExecution = executionsResponse.executions.find(
        execution => execution.execution_id === submittedExecution.execution_id
    );
    const firstHeavyExecution = await transport.request(
        `/tasks/${heavyTask.task_key}/execute-async`,
        {method: "POST"}
    );
    await new Promise(resolve => setTimeout(resolve, 900));
    await transport.request("/executions");
    const secondHeavyExecution = await transport.request(
        `/tasks/${heavyTask.task_key}/execute-async`,
        {method: "POST"}
    );
    await new Promise(resolve => setTimeout(resolve, 900));
    const heavyExecutionsResponse = await transport.request(
        `/executions?taskKey=${encodeURIComponent(heavyTask.task_key)}`
    );
    const skippedHeavyExecution = heavyExecutionsResponse.executions.find(
        execution => execution.execution_id === secondHeavyExecution.execution_id
    );
    const failedExecutions = await transport.request("/executions?status=FAILED");
    const failedNotification = failedExecutions.executions.find(
        execution => execution.task_key === notificationsTask.task_key
    );
    const retryExecution = await transport.request(
        `/executions/${failedNotification.execution_id}/retry`,
        {method: "POST"}
    );

    // Then
    assert.equal(transport.connectionLabel, "Demo");
    assert.equal(initialResponse.tasks.length, 8);
    assert.ok(initialTask.next_execution_at);
    assert.equal(heavyTask.concurrency_policy, "SKIP");
    assert.equal(heavyTask.max_concurrent_executions, 1);
    assert.equal(notificationsTask.retries, 2);
    assert.equal(notificationsTask.retry_backoff, "EXPONENTIAL");
    assert.equal(disabledTask.enabled, false);
    assert.equal(disabledTask.next_execution_at, null);
    assert.equal(enabledTask.enabled, true);
    assert.equal(submittedExecution.status, "QUEUED");
    assert.equal(submittedExecution.source, "MANUAL_ASYNC");
    assert.equal(cancelledExecution.status, "CANCELLED");
    assert.equal(firstHeavyExecution.status, "QUEUED");
    assert.equal(skippedHeavyExecution.status, "SKIPPED");
    assert.equal(skippedHeavyExecution.status_reason, "CONCURRENT_EXECUTION");
    assert.equal(retryExecution.source, "RETRY");
    assert.equal(retryExecution.retry_trigger, "MANUAL");
    assert.equal(retryExecution.parent_execution_id, failedNotification.execution_id);
    assert.equal(retryExecution.attempt, 1);
    assert.equal(executionsResponse.page, 0);
    assert.equal(executionsResponse.size, 2);
    assert.equal(Object.hasOwn(cancelledExecution, "demo_complete_after_millis"), false);
});
