(() => {
    "use strict";

    const storageKey = "cronctl-demo-state-v1";
    const activeStatuses = new Set(["PENDING", "RUNNING"]);
    const taskIds = {
        internalSync: "00000000-0000-4000-8000-000000000001",
        cleanup: "00000000-0000-4000-8000-000000000002",
        heavy: "00000000-0000-4000-8000-000000000003",
        simple: "00000000-0000-4000-8000-000000000004",
        untagged: "00000000-0000-4000-8000-000000000005",
        daily: "00000000-0000-4000-8000-000000000006",
        notifications: "00000000-0000-4000-8000-000000000007",
        weekly: "00000000-0000-4000-8000-000000000008"
    };
    const scheduleMetadata = new Map([
        [taskIds.internalSync, {kind: "interval", interval: 300_000, initialOffset: 70_000}],
        [taskIds.cleanup, {kind: "interval", interval: 900_000, initialOffset: 680_000}],
        [taskIds.heavy, {kind: "interval", interval: 600_000, initialOffset: 470_000}],
        [taskIds.simple, {kind: "five-minute-cron"}],
        [taskIds.untagged, {kind: "interval", interval: 30_000, initialOffset: 18_000}],
        [taskIds.daily, {kind: "daily", hour: 8}],
        [taskIds.notifications, {kind: "interval", interval: 60_000, initialOffset: 42_000}],
        [taskIds.weekly, {kind: "weekly", day: 1, hour: 9}]
    ]);

    const taskFixtures = [
        task({
            id: taskIds.internalSync,
            label: "internalSync",
            description: "AnnotatedScheduler.internalSync",
            methodName: "internalSync",
            schedule: {fixed_rate: 5, time_unit: "MINUTES"}
        }),
        task({
            id: taskIds.cleanup,
            label: "Cleanup Expired Sessions",
            description: "AnnotatedScheduler.cleanupExpiredSessions",
            group: "maintenance",
            methodName: "cleanupExpiredSessions",
            schedule: {fixed_rate: 15, time_unit: "MINUTES"}
        }),
        task({
            id: taskIds.heavy,
            label: "Heavy Job",
            description: "Long-running job, sleeps 6 minutes to simulate work",
            group: "processing",
            tags: ["processing", "heavy", "critical"],
            timeoutSeconds: -1,
            methodName: "runHeavyJob",
            schedule: {fixed_rate: 10, time_unit: "MINUTES"}
        }),
        task({
            id: taskIds.simple,
            label: "Simple Job",
            description: "Runs every 5 minutes, reads cron from properties",
            group: "reporting",
            tags: ["reporting", "scheduled"],
            togglingEnabled: true,
            methodName: "runSimpleJob",
            schedule: {cron: "0 */5 * * * *"}
        }),
        task({
            id: taskIds.untagged,
            label: "runUntaggedJob",
            description: "Scheduler.runUntaggedJob",
            methodName: "runUntaggedJob",
            schedule: {fixed_rate: 30, time_unit: "SECONDS"}
        }),
        task({
            id: taskIds.daily,
            label: "Daily Report",
            description: "Generates and sends the daily summary report",
            group: "reports",
            methodName: "generateDailyReport",
            schedule: {cron: "0 0 8 * * *"}
        }),
        task({
            id: taskIds.notifications,
            label: "Send Notifications",
            description: "Dispatches pending notifications to users",
            group: "notifications",
            methodName: "sendNotification",
            schedule: {fixed_rate: 1, time_unit: "MINUTES"}
        }),
        task({
            id: taskIds.weekly,
            label: "Weekly Report",
            description: "Generates the weekly analytics report every Monday at 9:00",
            group: "reports",
            togglingEnabled: true,
            methodName: "generateWeeklyReport",
            schedule: {cron: "0 0 9 * * MON"}
        })
    ];

    const state = loadState();

    window.cronctlTransport = Object.freeze({
        connectionLabel: "Demo",
        request
    });

    bindReset();

    class DemoApiError extends Error {
        constructor(status) {
            super(`Demo API request failed with status ${status}`);
            this.name = "DemoApiError";
            this.status = status;
        }
    }

    function task({
        id,
        label,
        description,
        group = "default",
        tags = [],
        togglingEnabled = false,
        timeoutSeconds = 0,
        methodName,
        schedule
    }) {
        return {
            label,
            description,
            group,
            tags,
            enabled: true,
            toggling_enabled: togglingEnabled,
            timeout_seconds: timeoutSeconds,
            details: {
                id,
                method_name: methodName,
                schedule
            },
            next_execution_at: null
        };
    }

    function createInitialState() {
        const now = Date.now();
        return {
            tasks: clone(taskFixtures),
            executions: [
                {
                    execution_id: "10000000-0000-4000-8000-000000000001",
                    task_id: taskIds.simple,
                    status: "SUCCEEDED",
                    submitted_at: new Date(now - 360_000).toISOString(),
                    started_at: new Date(now - 359_200).toISOString(),
                    finished_at: new Date(now - 357_780).toISOString(),
                    execution_duration_mills: 1_420,
                    fail_details: null
                },
                {
                    execution_id: "10000000-0000-4000-8000-000000000002",
                    task_id: taskIds.notifications,
                    status: "FAILED",
                    submitted_at: new Date(now - 1_080_000).toISOString(),
                    started_at: new Date(now - 1_079_100).toISOString(),
                    finished_at: new Date(now - 1_076_640).toISOString(),
                    execution_duration_mills: 2_460,
                    fail_details: {message: "Notification gateway returned HTTP 503"}
                }
            ]
        };
    }

    function loadState() {
        try {
            const storedState = JSON.parse(sessionStorage.getItem(storageKey));
            if (Array.isArray(storedState?.tasks) && Array.isArray(storedState?.executions)) {
                return storedState;
            }
        } catch (error) {
            // Start from fixtures when browser storage is unavailable or invalid.
        }
        return createInitialState();
    }

    function saveState() {
        try {
            sessionStorage.setItem(storageKey, JSON.stringify(state));
        } catch (error) {
            // The demo remains usable when browser storage is unavailable.
        }
    }

    async function request(path, options = {}) {
        const method = String(options.method || "GET").toUpperCase();
        const url = new URL(path, window.location.origin);
        await delay(method === "GET" ? 80 : 240);
        advanceExecutions();

        if (method === "GET" && url.pathname === "/tasks") {
            refreshNextExecutions();
            saveState();
            return {tasks: clone(state.tasks), total: state.tasks.length};
        }

        if (method === "GET" && url.pathname === "/executions") {
            return {
                executions: state.executions.map(publicExecution),
                total: state.executions.length
            };
        }

        const taskActionMatch = url.pathname.match(/^\/tasks\/([^/]+)\/(enable|disable|execute-async)$/);
        if (taskActionMatch) {
            const taskId = decodeURIComponent(taskActionMatch[1]);
            const action = taskActionMatch[2];
            if (method !== "POST") {
                throw new DemoApiError(404);
            }
            if (action === "execute-async") {
                return submitExecution(taskId);
            }
            return toggleTask(taskId, action === "enable");
        }

        const executionMatch = url.pathname.match(/^\/executions\/([^/]+)$/);
        if (method === "DELETE" && executionMatch) {
            cancelExecution(decodeURIComponent(executionMatch[1]));
            return null;
        }

        throw new DemoApiError(404);
    }

    function toggleTask(taskId, enabled) {
        const scheduledTask = findTask(taskId);
        if (!scheduledTask.toggling_enabled) {
            throw new DemoApiError(409);
        }

        scheduledTask.enabled = enabled;
        refreshNextExecution(scheduledTask, true);
        saveState();
        return clone(scheduledTask);
    }

    function submitExecution(taskId) {
        const scheduledTask = findTask(taskId);
        const submittedAt = Date.now();
        const executionId = createId();
        const completeAfterMillis = taskId === taskIds.heavy ? 14_000 : 5_200;
        const execution = {
            execution_id: executionId,
            task_id: taskId,
            status: "PENDING",
            submitted_at: new Date(submittedAt).toISOString(),
            started_at: null,
            finished_at: null,
            execution_duration_mills: null,
            fail_details: null,
            demo_started_after_millis: 800,
            demo_complete_after_millis: completeAfterMillis
        };

        state.executions.unshift(execution);
        state.executions = state.executions.slice(0, 100);
        saveState();
        return {
            execution_id: executionId,
            task_id: taskId,
            status: execution.status,
            submitted_at: execution.submitted_at,
            label: scheduledTask.label
        };
    }

    function cancelExecution(executionId) {
        const execution = state.executions.find(candidate => candidate.execution_id === executionId);
        if (!execution) {
            throw new DemoApiError(404);
        }
        if (!activeStatuses.has(execution.status)) {
            throw new DemoApiError(409);
        }

        const finishedAt = Date.now();
        execution.status = "CANCELLED";
        execution.finished_at = new Date(finishedAt).toISOString();
        execution.execution_duration_mills = execution.started_at
            ? Math.max(0, finishedAt - Date.parse(execution.started_at))
            : 0;
        delete execution.demo_started_after_millis;
        delete execution.demo_complete_after_millis;
        saveState();
    }

    function advanceExecutions() {
        const now = Date.now();
        let changed = false;
        state.executions.forEach(execution => {
            if (!activeStatuses.has(execution.status)) {
                return;
            }

            const submittedAt = Date.parse(execution.submitted_at);
            const elapsed = now - submittedAt;
            const startedAfter = execution.demo_started_after_millis || 800;
            const completeAfter = execution.demo_complete_after_millis || 5_200;
            if (elapsed >= completeAfter) {
                execution.status = "SUCCEEDED";
                execution.started_at ||= new Date(submittedAt + startedAfter).toISOString();
                execution.finished_at = new Date(submittedAt + completeAfter).toISOString();
                execution.execution_duration_mills = completeAfter - startedAfter;
                delete execution.demo_started_after_millis;
                delete execution.demo_complete_after_millis;
                changed = true;
            } else if (elapsed >= startedAfter && execution.status === "PENDING") {
                execution.status = "RUNNING";
                execution.started_at = new Date(submittedAt + startedAfter).toISOString();
                changed = true;
            }
        });

        if (changed) {
            saveState();
        }
    }

    function refreshNextExecutions() {
        state.tasks.forEach(scheduledTask => refreshNextExecution(scheduledTask));
    }

    function refreshNextExecution(scheduledTask, force = false) {
        if (!scheduledTask.enabled) {
            scheduledTask.next_execution_at = null;
            return;
        }

        const taskId = scheduledTask.details?.id;
        const metadata = scheduleMetadata.get(taskId);
        const now = Date.now();
        if (!metadata) {
            scheduledTask.next_execution_at = null;
            return;
        }

        if (metadata.kind === "interval") {
            const currentNext = Date.parse(scheduledTask.next_execution_at || "");
            if (force || !Number.isFinite(currentNext) || currentNext <= now) {
                const offset = force || Number.isFinite(currentNext)
                    ? metadata.interval
                    : metadata.initialOffset;
                scheduledTask.next_execution_at = new Date(now + offset).toISOString();
            }
            return;
        }

        if (metadata.kind === "five-minute-cron") {
            scheduledTask.next_execution_at = nextFiveMinuteBoundary(now).toISOString();
            return;
        }
        if (metadata.kind === "daily") {
            scheduledTask.next_execution_at = nextDailyExecution(now, metadata.hour).toISOString();
            return;
        }
        if (metadata.kind === "weekly") {
            scheduledTask.next_execution_at = nextWeeklyExecution(now, metadata.day, metadata.hour).toISOString();
        }
    }

    function nextFiveMinuteBoundary(now) {
        const next = new Date(now);
        next.setSeconds(0, 0);
        next.setMinutes(Math.floor(next.getMinutes() / 5) * 5 + 5);
        return next;
    }

    function nextDailyExecution(now, hour) {
        const next = new Date(now);
        next.setHours(hour, 0, 0, 0);
        if (next.getTime() <= now) {
            next.setDate(next.getDate() + 1);
        }
        return next;
    }

    function nextWeeklyExecution(now, day, hour) {
        const next = new Date(now);
        next.setHours(hour, 0, 0, 0);
        const daysAhead = (day - next.getDay() + 7) % 7;
        next.setDate(next.getDate() + daysAhead);
        if (next.getTime() <= now) {
            next.setDate(next.getDate() + 7);
        }
        return next;
    }

    function findTask(taskId) {
        const scheduledTask = state.tasks.find(candidate => candidate.details?.id === taskId);
        if (!scheduledTask) {
            throw new DemoApiError(404);
        }
        return scheduledTask;
    }

    function publicExecution(execution) {
        const result = clone(execution);
        delete result.demo_started_after_millis;
        delete result.demo_complete_after_millis;
        return result;
    }

    function createId() {
        if (typeof globalThis.crypto?.randomUUID === "function") {
            return globalThis.crypto.randomUUID();
        }
        return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, character => {
            const random = Math.floor(Math.random() * 16);
            const value = character === "x" ? random : (random & 0x3) | 0x8;
            return value.toString(16);
        });
    }

    function clone(value) {
        return JSON.parse(JSON.stringify(value));
    }

    function delay(milliseconds) {
        return new Promise(resolve => setTimeout(resolve, milliseconds));
    }

    function bindReset() {
        const resetButton = document.querySelector("#demo-reset");
        resetButton?.addEventListener("click", () => {
            sessionStorage.removeItem(storageKey);
            window.location.reload();
        });
    }
})();
