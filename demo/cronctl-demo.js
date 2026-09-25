(() => {
    "use strict";

    const storageKey = "cronctl-demo-state-v5";
    const activeStatuses = new Set(["CREATED", "QUEUED", "RUNNING"]);
    const terminalFailureStatuses = new Set(["FAILED", "TIMED_OUT"]);
    const demoNodeId = "pages-demo-1";
    const taskKeys = {
        internalSync: "sample-app.annotatedScheduler.internalSync()",
        cleanup: "sample-app.annotatedScheduler.cleanupExpiredSessions()",
        heavy: "sample-app.scheduler.runHeavyJob()",
        simple: "reporting.simple-job",
        untagged: "sample-app.scheduler.runUntaggedJob()",
        daily: "sample-app.reportScheduler.generateDailyReport()",
        notifications: "sample-app.annotatedScheduler.sendNotification()",
        weekly: "reports.weekly"
    };
    const scheduleMetadata = new Map([
        [taskKeys.internalSync, {kind: "interval", interval: 300_000, initialOffset: 70_000}],
        [taskKeys.cleanup, {kind: "interval", interval: 900_000, initialOffset: 680_000}],
        [taskKeys.heavy, {kind: "interval", interval: 600_000, initialOffset: 470_000}],
        [taskKeys.simple, {kind: "five-minute-cron"}],
        [taskKeys.untagged, {kind: "interval", interval: 30_000, initialOffset: 18_000}],
        [taskKeys.daily, {kind: "daily", hour: 8}],
        [taskKeys.notifications, {kind: "interval", interval: 60_000, initialOffset: 42_000}],
        [taskKeys.weekly, {kind: "weekly", day: 1, hour: 9}]
    ]);

    const taskFixtures = [
        task({
            taskKey: taskKeys.internalSync,
            label: "internalSync",
            description: "AnnotatedScheduler.internalSync",
            methodName: "internalSync",
            schedule: {fixed_rate: 5, time_unit: "MINUTES"}
        }),
        task({
            taskKey: taskKeys.cleanup,
            label: "Cleanup Expired Sessions",
            description: "AnnotatedScheduler.cleanupExpiredSessions",
            group: "maintenance",
            methodName: "cleanupExpiredSessions",
            schedule: {fixed_rate: 15, time_unit: "MINUTES"}
        }),
        task({
            taskKey: taskKeys.heavy,
            label: "Heavy Job",
            description: "Long-running job, sleeps 6 minutes to simulate work",
            group: "processing",
            tags: ["processing", "heavy", "critical"],
            timeoutSeconds: -1,
            concurrencyPolicy: "SKIP",
            maxConcurrentExecutions: 1,
            methodName: "runHeavyJob",
            schedule: {fixed_rate: 10, time_unit: "MINUTES"}
        }),
        task({
            taskKey: taskKeys.simple,
            label: "Simple Job",
            description: "Runs every 5 minutes, reads cron from properties",
            group: "reporting",
            tags: ["reporting", "scheduled"],
            togglingEnabled: true,
            methodName: "runSimpleJob",
            schedule: {cron: "0 */5 * * * *"}
        }),
        task({
            taskKey: taskKeys.untagged,
            label: "runUntaggedJob",
            description: "Scheduler.runUntaggedJob",
            methodName: "runUntaggedJob",
            schedule: {fixed_rate: 30, time_unit: "SECONDS"}
        }),
        task({
            taskKey: taskKeys.daily,
            label: "Daily Report",
            description: "Generates and sends the daily summary report",
            group: "reports",
            methodName: "generateDailyReport",
            schedule: {cron: "0 0 8 * * *"}
        }),
        task({
            taskKey: taskKeys.notifications,
            label: "Send Notifications",
            description: "Dispatches pending notifications to users",
            group: "notifications",
            retries: 2,
            retryDelay: "PT10S",
            retryBackoff: "EXPONENTIAL",
            methodName: "sendNotification",
            schedule: {fixed_rate: 1, time_unit: "MINUTES"}
        }),
        task({
            taskKey: taskKeys.weekly,
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
        taskKey,
        label,
        description,
        group = "default",
        tags = [],
        togglingEnabled = false,
        timeoutSeconds = 0,
        concurrencyPolicy = "ALLOW",
        maxConcurrentExecutions = 1,
        retries = 0,
        retryDelay = "PT1S",
        retryBackoff = "FIXED",
        maxRetryDelay = "PT5M",
        retryJitter = 0,
        methodName,
        schedule
    }) {
        return {
            task_key: taskKey,
            label,
            description,
            group,
            tags,
            enabled: true,
            toggling_enabled: togglingEnabled,
            timeout_seconds: timeoutSeconds,
            concurrency_policy: concurrencyPolicy,
            max_concurrent_executions: maxConcurrentExecutions,
            retries,
            retry_delay: retryDelay,
            retry_backoff: retryBackoff,
            max_retry_delay: maxRetryDelay,
            retry_jitter: retryJitter,
            retry_on: [],
            non_retryable_on: [],
            automatic_tracking_status: "ACTIVE",
            automatic_tracking_message: null,
            scheduled_health: {
                last_execution_at: null,
                last_execution_status: null,
                last_success_at: null,
                consecutive_failures: 0
            },
            details: {
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
                    task_key: taskKeys.simple,
                    source: "SCHEDULED",
                    status: "SUCCEEDED",
                    node_id: demoNodeId,
                    created_at: new Date(now - 361_000).toISOString(),
                    queued_at: null,
                    planned_at: new Date(now - 361_000).toISOString(),
                    started_at: new Date(now - 359_200).toISOString(),
                    finished_at: new Date(now - 357_780).toISOString(),
                    start_delay_ms: 1_800,
                    duration_ms: 1_420,
                    status_reason: null,
                    error: null
                },
                {
                    execution_id: "10000000-0000-4000-8000-000000000002",
                    task_key: taskKeys.notifications,
                    source: "SCHEDULED",
                    status: "FAILED",
                    node_id: demoNodeId,
                    created_at: new Date(now - 1_080_000).toISOString(),
                    queued_at: null,
                    planned_at: new Date(now - 1_080_000).toISOString(),
                    started_at: new Date(now - 1_079_100).toISOString(),
                    finished_at: new Date(now - 1_076_640).toISOString(),
                    start_delay_ms: 900,
                    duration_ms: 2_460,
                    status_reason: "INVOCATION_FAILED",
                    error: {
                        type: "java.lang.IllegalStateException",
                        message: "Notification gateway returned HTTP 503"
                    }
                },
                {
                    execution_id: "10000000-0000-4000-8000-000000000003",
                    task_key: taskKeys.daily,
                    source: "MANUAL_ASYNC",
                    status: "SUCCEEDED",
                    node_id: demoNodeId,
                    created_at: new Date(now - 600_000).toISOString(),
                    queued_at: new Date(now - 599_980).toISOString(),
                    planned_at: null,
                    started_at: new Date(now - 599_600).toISOString(),
                    finished_at: new Date(now - 596_900).toISOString(),
                    start_delay_ms: null,
                    duration_ms: 2_700,
                    status_reason: null,
                    error: null
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
            refreshScheduledHealth();
            saveState();
            return {tasks: clone(state.tasks), total: state.tasks.length};
        }

        if (method === "GET" && url.pathname === "/executions") {
            const page = nonNegativeInteger(url.searchParams.get("page"), 0);
            const size = positiveInteger(url.searchParams.get("size"), 50, 200);
            const filteredExecutions = filterExecutions(url.searchParams);
            const start = page * size;
            return {
                executions: filteredExecutions.slice(start, start + size).map(publicExecution),
                total: filteredExecutions.length,
                page,
                size,
                has_next: start + size < filteredExecutions.length
            };
        }

        const taskActionMatch = url.pathname.match(/^\/tasks\/([^/]+)\/(enable|disable|execute-async)$/);
        if (taskActionMatch) {
            const taskKey = decodeURIComponent(taskActionMatch[1]);
            const action = taskActionMatch[2];
            if (method !== "POST") {
                throw new DemoApiError(404);
            }
            if (action === "execute-async") {
                return submitExecution(taskKey);
            }
            return toggleTask(taskKey, action === "enable");
        }

        const executionMatch = url.pathname.match(/^\/executions\/([^/]+)$/);
        if (method === "DELETE" && executionMatch) {
            cancelExecution(decodeURIComponent(executionMatch[1]));
            return null;
        }

        const retryMatch = url.pathname.match(/^\/executions\/([^/]+)\/retry$/);
        if (method === "POST" && retryMatch) {
            return retryExecution(decodeURIComponent(retryMatch[1]));
        }

        if (method === "POST" && url.pathname === "/executions/retry-all-failed") {
            const retries = retryAllFailed();
            return {executions: retries.map(publicExecution), submitted: retries.length};
        }

        throw new DemoApiError(404);
    }

    function toggleTask(taskKey, enabled) {
        const scheduledTask = findTask(taskKey);
        if (!scheduledTask.toggling_enabled) {
            throw new DemoApiError(409);
        }

        scheduledTask.enabled = enabled;
        refreshNextExecution(scheduledTask, true);
        saveState();
        return clone(scheduledTask);
    }

    function submitExecution(taskKey) {
        findTask(taskKey);
        const createdAt = Date.now();
        const executionId = createId();
        const completeAfterMillis = taskKey === taskKeys.heavy ? 14_000 : 5_200;
        const execution = {
            execution_id: executionId,
            task_key: taskKey,
            source: "MANUAL_ASYNC",
            status: "QUEUED",
            node_id: demoNodeId,
            created_at: new Date(createdAt).toISOString(),
            queued_at: new Date(createdAt).toISOString(),
            planned_at: null,
            started_at: null,
            finished_at: null,
            start_delay_ms: null,
            duration_ms: null,
            status_reason: null,
            error: null,
            parent_execution_id: null,
            root_execution_id: executionId,
            retry_series_id: executionId,
            attempt: 1,
            retry_trigger: null,
            demo_started_after_millis: 800,
            demo_complete_after_millis: completeAfterMillis
        };

        state.executions.unshift(execution);
        state.executions = state.executions.slice(0, 100);
        saveState();
        return publicExecution(execution);
    }

    function retryExecution(executionId) {
        const parent = state.executions.find(candidate => candidate.execution_id === executionId);
        if (!parent) {
            throw new DemoApiError(404);
        }
        if (!terminalFailureStatuses.has(parent.status)
            || state.executions.some(candidate => candidate.parent_execution_id === executionId)) {
            throw new DemoApiError(409);
        }

        const createdAt = Date.now();
        const retryId = createId();
        const retry = {
            execution_id: retryId,
            task_key: parent.task_key,
            source: "RETRY",
            status: "QUEUED",
            node_id: demoNodeId,
            created_at: new Date(createdAt).toISOString(),
            queued_at: new Date(createdAt).toISOString(),
            planned_at: new Date(createdAt).toISOString(),
            started_at: null,
            finished_at: null,
            start_delay_ms: null,
            duration_ms: null,
            status_reason: null,
            error: null,
            parent_execution_id: parent.execution_id,
            root_execution_id: parent.root_execution_id || parent.execution_id,
            retry_series_id: retryId,
            attempt: 1,
            retry_trigger: "MANUAL",
            demo_started_after_millis: 800,
            demo_complete_after_millis: 5_200
        };
        state.executions.unshift(retry);
        state.executions = state.executions.slice(0, 100);
        saveState();
        return publicExecution(retry);
    }

    function retryAllFailed() {
        const retries = [];
        state.tasks.forEach(scheduledTask => {
            const latestFailure = state.executions
                .filter(execution => execution.task_key === scheduledTask.task_key
                    && terminalFailureStatuses.has(execution.status))
                .sort((left, right) => Date.parse(right.created_at) - Date.parse(left.created_at))[0];
            if (latestFailure
                && !state.executions.some(candidate => candidate.parent_execution_id === latestFailure.execution_id)) {
                retries.push(retryExecution(latestFailure.execution_id));
            }
        });
        return retries;
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
        execution.duration_ms = execution.started_at
            ? Math.max(0, finishedAt - Date.parse(execution.started_at))
            : 0;
        execution.status_reason = "CANCELLATION_REQUESTED";
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

            const createdAt = Date.parse(execution.created_at);
            const elapsed = now - createdAt;
            const startedAfter = execution.demo_started_after_millis || 800;
            const completeAfter = execution.demo_complete_after_millis || 5_200;
            if (elapsed >= completeAfter) {
                execution.status = "SUCCEEDED";
                execution.started_at ||= new Date(createdAt + startedAfter).toISOString();
                execution.finished_at = new Date(createdAt + completeAfter).toISOString();
                execution.duration_ms = completeAfter - startedAfter;
                delete execution.demo_started_after_millis;
                delete execution.demo_complete_after_millis;
                changed = true;
            } else if (elapsed >= startedAfter && (execution.status === "CREATED" || execution.status === "QUEUED")) {
                const scheduledTask = findTask(execution.task_key);
                const activeExecutions = state.executions.filter(candidate =>
                    candidate.execution_id !== execution.execution_id
                    && candidate.task_key === execution.task_key
                    && candidate.status === "RUNNING"
                ).length;
                if (scheduledTask.concurrency_policy === "SKIP"
                    && activeExecutions >= scheduledTask.max_concurrent_executions) {
                    execution.status = "SKIPPED";
                    execution.finished_at = new Date(now).toISOString();
                    execution.status_reason = "CONCURRENT_EXECUTION";
                    delete execution.demo_started_after_millis;
                    delete execution.demo_complete_after_millis;
                    changed = true;
                    return;
                }
                execution.status = "RUNNING";
                execution.started_at = new Date(createdAt + startedAfter).toISOString();
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

    function refreshScheduledHealth() {
        state.tasks.forEach(scheduledTask => {
            const scheduledExecutions = state.executions
                .filter(execution => execution.task_key === scheduledTask.task_key
                    && execution.source === "SCHEDULED"
                    && !activeStatuses.has(execution.status))
                .sort((left, right) => Date.parse(right.created_at) - Date.parse(left.created_at));
            const lastExecution = scheduledExecutions[0];
            const lastSuccess = scheduledExecutions.find(execution => execution.status === "SUCCEEDED");
            let consecutiveFailures = 0;
            for (const execution of scheduledExecutions) {
                if (execution.status === "SUCCEEDED") {
                    break;
                }
                if (terminalFailureStatuses.has(execution.status)) {
                    consecutiveFailures += 1;
                }
            }
            scheduledTask.scheduled_health = {
                last_execution_at: lastExecution?.started_at || lastExecution?.created_at || null,
                last_execution_status: lastExecution?.status || null,
                last_success_at: lastSuccess?.started_at || lastSuccess?.created_at || null,
                consecutive_failures: consecutiveFailures
            };
        });
    }

    function refreshNextExecution(scheduledTask, force = false) {
        if (!scheduledTask.enabled) {
            scheduledTask.next_execution_at = null;
            return;
        }

        const taskKey = scheduledTask.task_key;
        const metadata = scheduleMetadata.get(taskKey);
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

    function findTask(taskKey) {
        const scheduledTask = state.tasks.find(candidate => candidate.task_key === taskKey);
        if (!scheduledTask) {
            throw new DemoApiError(404);
        }
        return scheduledTask;
    }

    function filterExecutions(searchParameters) {
        const taskKey = searchParameters.get("taskKey");
        const status = searchParameters.get("status");
        const source = searchParameters.get("source");
        const nodeId = searchParameters.get("nodeId");
        const from = Date.parse(searchParameters.get("from") || "");
        const to = Date.parse(searchParameters.get("to") || "");
        return state.executions
            .filter(execution => !taskKey || execution.task_key === taskKey)
            .filter(execution => !status || execution.status === status)
            .filter(execution => !source || execution.source === source)
            .filter(execution => !nodeId || execution.node_id === nodeId)
            .filter(execution => !Number.isFinite(from) || Date.parse(execution.created_at) >= from)
            .filter(execution => !Number.isFinite(to) || Date.parse(execution.created_at) < to)
            .sort((left, right) => Date.parse(right.created_at) - Date.parse(left.created_at));
    }

    function nonNegativeInteger(value, fallback) {
        const parsed = Number.parseInt(value, 10);
        return Number.isInteger(parsed) && parsed >= 0 ? parsed : fallback;
    }

    function positiveInteger(value, fallback, maximum) {
        const parsed = Number.parseInt(value, 10);
        return Number.isInteger(parsed) && parsed > 0 ? Math.min(parsed, maximum) : fallback;
    }

    function publicExecution(execution) {
        const result = clone(execution);
        result.root_execution_id ||= result.execution_id;
        result.retry_series_id ||= result.execution_id;
        result.attempt ||= 1;
        result.retry_trigger ||= null;
        result.parent_execution_id ||= null;
        result.retryable = terminalFailureStatuses.has(result.status)
            && !state.executions.some(candidate => candidate.parent_execution_id === result.execution_id);
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
