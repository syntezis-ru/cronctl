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
    const disabledTask = await transport.request(
        `/tasks/${initialTask.details.id}/disable?interrupt=false`,
        {method: "POST"}
    );
    const enabledTask = await transport.request(
        `/tasks/${initialTask.details.id}/enable`,
        {method: "POST"}
    );
    const submittedExecution = await transport.request(
        `/tasks/${initialTask.details.id}/execute-async`,
        {method: "POST"}
    );
    await transport.request(
        `/executions/${submittedExecution.execution_id}`,
        {method: "DELETE"}
    );
    const executionsResponse = await transport.request("/executions");
    const cancelledExecution = executionsResponse.executions.find(
        execution => execution.execution_id === submittedExecution.execution_id
    );

    // Then
    assert.equal(transport.connectionLabel, "Demo");
    assert.equal(initialResponse.tasks.length, 8);
    assert.ok(initialTask.next_execution_at);
    assert.equal(disabledTask.enabled, false);
    assert.equal(disabledTask.next_execution_at, null);
    assert.equal(enabledTask.enabled, true);
    assert.equal(submittedExecution.status, "PENDING");
    assert.equal(cancelledExecution.status, "CANCELLED");
    assert.equal(Object.hasOwn(cancelledExecution, "demo_complete_after_millis"), false);
});
