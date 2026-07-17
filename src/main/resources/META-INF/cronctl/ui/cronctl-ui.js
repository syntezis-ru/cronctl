(() => {
    "use strict";

    const apiBasePath = (document.body.dataset.apiBasePath || "/api/cronctl").replace(/\/$/, "");
    const activeStatuses = new Set(["PENDING", "RUNNING"]);
    const terminalFailureStatuses = new Set(["FAILED", "TIMED_OUT"]);
    const maximumExecutions = 100;

    const elements = {
        cadenceRail: document.querySelector("#cadence-rail"),
        confirmDialog: document.querySelector("#confirm-dialog"),
        confirmMessage: document.querySelector("#confirm-message"),
        confirmSubmit: document.querySelector("#confirm-submit"),
        confirmTitle: document.querySelector("#confirm-title"),
        connectionLabel: document.querySelector("#connection-label"),
        connectionState: document.querySelector("#connection-state"),
        errorMessage: document.querySelector("#error-message"),
        errorNotice: document.querySelector("#error-notice"),
        errorRetry: document.querySelector("#error-retry"),
        errorTitle: document.querySelector("#error-title"),
        executionFilter: document.querySelector("#execution-filter"),
        executionList: document.querySelector("#execution-list"),
        executionsTabCount: document.querySelector("#executions-tab-count"),
        groupFilter: document.querySelector("#group-filter"),
        interruptCheckbox: document.querySelector("#interrupt-checkbox"),
        interruptOption: document.querySelector("#interrupt-option"),
        localClock: document.querySelector("#local-clock"),
        metricActive: document.querySelector("#metric-active"),
        metricEnabled: document.querySelector("#metric-enabled"),
        metricFailed: document.querySelector("#metric-failed"),
        metricRegistered: document.querySelector("#metric-registered"),
        refreshButton: document.querySelector("#refresh-button"),
        stateFilter: document.querySelector("#state-filter"),
        taskFilters: document.querySelector("#task-filters"),
        taskList: document.querySelector("#task-list"),
        taskSearch: document.querySelector("#task-search"),
        tasksTabCount: document.querySelector("#tasks-tab-count"),
        toastRegion: document.querySelector("#toast-region"),
        viewTabs: document.querySelector(".view-tabs")
    };

    const state = {
        activeView: "tasks",
        executions: [],
        executionTimer: null,
        pendingConfirmation: null,
        tasks: [],
        taskTimer: null
    };

    class ApiError extends Error {
        constructor(status) {
            super(errorCopy(status).message);
            this.name = "ApiError";
            this.status = status;
        }
    }

    async function request(path, options = {}) {
        const response = await fetch(apiBasePath + path, {
            credentials: "same-origin",
            headers: {"Accept": "application/json", ...(options.headers || {})},
            ...options
        });

        if (!response.ok) {
            throw new ApiError(response.status);
        }
        if (response.status === 204) {
            return null;
        }

        const contentType = response.headers.get("content-type") || "";
        return contentType.includes("application/json") ? response.json() : null;
    }

    async function loadTasks(silent = false) {
        if (!silent && state.tasks.length === 0) {
            elements.taskList.setAttribute("aria-busy", "true");
        }

        const response = await request("/tasks");
        state.tasks = Array.isArray(response?.tasks) ? response.tasks : [];
        populateGroups();
        renderTasks();
        renderCadenceRail();
        renderMetrics();
    }

    async function loadExecutions(silent = false) {
        if (!silent && state.executions.length === 0) {
            elements.executionList.setAttribute("aria-busy", "true");
        }

        const response = await request("/executions");
        state.executions = (Array.isArray(response?.executions) ? response.executions : [])
            .sort((left, right) => timestamp(right.submitted_at) - timestamp(left.submitted_at))
            .slice(0, maximumExecutions);
        renderExecutions();
        renderMetrics();
    }

    async function refreshAll({manual = false} = {}) {
        if (manual) {
            elements.refreshButton.classList.add("is-loading");
            elements.refreshButton.disabled = true;
        }

        try {
            const results = await Promise.allSettled([loadTasks(true), loadExecutions(true)]);
            const rejected = results.find(result => result.status === "rejected");
            if (rejected) {
                throw rejected.reason;
            }
            setConnection("online", "Live");
            hideError();
            if (manual) {
                showToast("Schedule refreshed", "Tasks and executions are up to date.");
            }
        } catch (error) {
            handleLoadError(error);
        } finally {
            elements.refreshButton.classList.remove("is-loading");
            elements.refreshButton.disabled = false;
        }
    }

    function schedulePolling() {
        clearTimeout(state.taskTimer);
        clearTimeout(state.executionTimer);

        state.taskTimer = setTimeout(async () => {
            if (!document.hidden) {
                try {
                    await loadTasks(true);
                    setConnection("online", "Live");
                } catch (error) {
                    handleLoadError(error);
                }
            }
            scheduleTaskPolling();
        }, 10_000);

        scheduleExecutionPolling();
    }

    function scheduleTaskPolling() {
        clearTimeout(state.taskTimer);
        state.taskTimer = setTimeout(async () => {
            if (!document.hidden) {
                try {
                    await loadTasks(true);
                    setConnection("online", "Live");
                } catch (error) {
                    handleLoadError(error);
                }
            }
            scheduleTaskPolling();
        }, 10_000);
    }

    function scheduleExecutionPolling() {
        clearTimeout(state.executionTimer);
        const hasActiveExecution = state.executions.some(execution => activeStatuses.has(execution.status));
        const delay = hasActiveExecution ? 2_000 : 10_000;

        state.executionTimer = setTimeout(async () => {
            if (!document.hidden) {
                try {
                    await loadExecutions(true);
                    setConnection("online", "Live");
                } catch (error) {
                    handleLoadError(error);
                }
            }
            scheduleExecutionPolling();
        }, delay);
    }

    function renderTasks() {
        elements.taskList.setAttribute("aria-busy", "false");
        const query = elements.taskSearch.value.trim().toLocaleLowerCase();
        const group = elements.groupFilter.value;
        const scheduleState = elements.stateFilter.value;

        const filteredTasks = state.tasks.filter(task => {
            const searchable = [
                task.label,
                task.description,
                task.group,
                task.details?.method_name,
                ...(task.tags || [])
            ].filter(Boolean).join(" ").toLocaleLowerCase();
            const matchesQuery = !query || searchable.includes(query);
            const matchesGroup = !group || task.group === group;
            const matchesState = !scheduleState
                || (scheduleState === "enabled" && task.enabled)
                || (scheduleState === "disabled" && !task.enabled);
            return matchesQuery && matchesGroup && matchesState;
        });

        elements.tasksTabCount.textContent = String(state.tasks.length);
        if (filteredTasks.length === 0) {
            const hasFilters = Boolean(query || group || scheduleState);
            elements.taskList.innerHTML = emptyState(
                hasFilters ? "No tasks match these filters" : "No scheduled tasks registered",
                hasFilters ? "Change or clear a filter to see more tasks." : "Registered @Scheduled methods will appear here."
            );
            return;
        }

        elements.taskList.innerHTML = filteredTasks.map(renderTaskCard).join("");
    }

    function renderTaskCard(task) {
        const taskId = task.details?.id || "";
        const enabled = Boolean(task.enabled);
        const stateName = enabled ? "enabled" : "disabled";
        const stateLabel = enabled ? "Scheduled" : "Paused";
        const toggleAction = enabled ? "disable" : "enable";
        const toggleLabel = enabled ? "Pause" : "Resume";
        const toggleButtonClass = enabled ? "button--warning" : "button--quiet";
        const tags = (task.tags || []).slice(0, 4)
            .map(tag => `<span class="tag">${escapeHtml(tag)}</span>`)
            .join("");
        const remainingTags = (task.tags || []).length > 4
            ? `<span class="meta-token">+${task.tags.length - 4}</span>`
            : "";
        const toggleButton = task.toggling_enabled
            ? `<button class="button ${toggleButtonClass}" type="button" data-action="${toggleAction}" data-task-id="${escapeHtml(taskId)}">${toggleLabel}</button>`
            : "";
        const nextExecution = task.next_execution_at
            ? `<time datetime="${escapeHtml(task.next_execution_at)}" title="${escapeHtml(formatDate(task.next_execution_at))}">${escapeHtml(formatRelative(task.next_execution_at))}</time>`
            : `<span>${escapeHtml(nextExecutionUnavailableLabel(task))}</span>`;

        return `
            <article class="task-card ${enabled ? "" : "is-disabled"}" data-task-id="${escapeHtml(taskId)}">
                <div class="task-card__identity">
                    <div class="task-card__title-row">
                        <h3 title="${escapeHtml(task.label || "Unnamed task")}">${escapeHtml(task.label || "Unnamed task")}</h3>
                        <span class="status-badge status-badge--${stateName}">${stateLabel}</span>
                    </div>
                    <p class="task-card__description">${escapeHtml(task.description || task.details?.method_name || "No description")}</p>
                    <div class="task-card__meta">
                        <span class="meta-token">${escapeHtml(task.group || "default")}</span>
                        ${tags}${remainingTags}
                    </div>
                </div>
                <div class="schedule-readout">
                    <span class="schedule-readout__type">${escapeHtml(scheduleType(task.details?.schedule))}</span>
                    <code title="${escapeHtml(scheduleValue(task.details?.schedule))}">${escapeHtml(scheduleValue(task.details?.schedule))}</code>
                </div>
                <div class="next-run">
                    <span class="next-run__label">Next execution</span>
                    ${nextExecution}
                </div>
                <div class="task-actions">
                    ${toggleButton}
                    <button class="button button--primary" type="button" data-action="run" data-task-id="${escapeHtml(taskId)}">Run async</button>
                </div>
            </article>`;
    }

    function renderExecutions() {
        elements.executionList.setAttribute("aria-busy", "false");
        const statusFilter = elements.executionFilter.value;
        const filteredExecutions = state.executions.filter(execution => !statusFilter || execution.status === statusFilter);
        elements.executionsTabCount.textContent = String(state.executions.length);

        if (filteredExecutions.length === 0) {
            elements.executionList.innerHTML = emptyState(
                statusFilter ? "No executions with this status" : "No tracked executions yet",
                statusFilter ? "Select another status to inspect the history." : "Run a task asynchronously to create a tracked execution."
            );
            return;
        }

        const taskLabels = new Map(state.tasks.map(task => [task.details?.id, task.label]));
        elements.executionList.innerHTML = filteredExecutions.map(execution => {
            const taskLabel = taskLabels.get(execution.task_id) || "Unknown task";
            const canCancel = activeStatuses.has(execution.status);
            const duration = execution.execution_duration_mills == null
                ? (execution.status === "RUNNING" ? "In progress" : "—")
                : formatDuration(execution.execution_duration_mills);
            const failure = execution.fail_details?.message
                ? `<p class="failure-message">${escapeHtml(execution.fail_details.message)}</p>`
                : "";

            return `
                <article class="execution-card" data-execution-id="${escapeHtml(execution.execution_id)}">
                    <div>
                        <h3 title="${escapeHtml(taskLabel)}">${escapeHtml(taskLabel)}</h3>
                        <span class="execution-card__id" title="${escapeHtml(execution.execution_id)}">${escapeHtml(shortId(execution.execution_id))}</span>
                    </div>
                    <div class="execution-card__state">
                        <span class="status-badge status-badge--${statusClass(execution.status)}">${escapeHtml(readableStatus(execution.status))}</span>
                        <span class="execution-card__duration">${escapeHtml(duration)}</span>
                    </div>
                    <div class="execution-card__timeline">
                        <div>
                            <span>Submitted</span>
                            <time class="execution-card__time" datetime="${escapeHtml(execution.submitted_at || "")}">${escapeHtml(formatDate(execution.submitted_at))}</time>
                        </div>
                        <div>
                            <span>Finished</span>
                            <time class="execution-card__time" datetime="${escapeHtml(execution.finished_at || "")}">${escapeHtml(formatDate(execution.finished_at))}</time>
                        </div>
                    </div>
                    <div class="execution-actions">
                        ${canCancel ? `<button class="button button--danger" type="button" data-action="cancel-execution" data-execution-id="${escapeHtml(execution.execution_id)}">Cancel</button>` : ""}
                    </div>
                    ${failure}
                </article>`;
        }).join("");
    }

    function renderCadenceRail() {
        const nextTasks = state.tasks
            .filter(task => task.enabled && task.next_execution_at)
            .sort((left, right) => timestamp(left.next_execution_at) - timestamp(right.next_execution_at))
            .slice(0, 6);

        if (nextTasks.length === 0) {
            elements.cadenceRail.innerHTML = `
                <div class="cadence-rail__line" aria-hidden="true"></div>
                <p class="empty-inline">No upcoming execution time is currently available.</p>`;
            return;
        }

        elements.cadenceRail.innerHTML = `
            <div class="cadence-rail__line" aria-hidden="true"></div>
            ${nextTasks.map(task => `
                <div class="cadence-item" title="${escapeHtml(formatDate(task.next_execution_at))}">
                    <time class="cadence-item__time" datetime="${escapeHtml(task.next_execution_at)}">${escapeHtml(formatClockTime(task.next_execution_at))}</time>
                    <span class="cadence-item__label">${escapeHtml(task.label || task.details?.method_name || "Unnamed task")}</span>
                </div>`).join("")}`;
    }

    function renderMetrics() {
        const enabled = state.tasks.filter(task => task.enabled).length;
        const active = state.executions.filter(execution => activeStatuses.has(execution.status)).length;
        const oneDayAgo = Date.now() - 86_400_000;
        const recentFailures = state.executions.filter(execution =>
            terminalFailureStatuses.has(execution.status) && timestamp(execution.submitted_at) >= oneDayAgo
        ).length;

        elements.metricRegistered.textContent = String(state.tasks.length);
        elements.metricEnabled.textContent = String(enabled);
        elements.metricActive.textContent = String(active);
        elements.metricFailed.textContent = String(recentFailures);
    }

    function populateGroups() {
        const currentValue = elements.groupFilter.value;
        const groups = [...new Set(state.tasks.map(task => task.group).filter(Boolean))].sort();
        elements.groupFilter.innerHTML = `<option value="">All groups</option>${groups
            .map(group => `<option value="${escapeHtml(group)}">${escapeHtml(group)}</option>`)
            .join("")}`;
        if (groups.includes(currentValue)) {
            elements.groupFilter.value = currentValue;
        }
    }

    async function handleTaskAction(button) {
        const action = button.dataset.action;
        const taskId = button.dataset.taskId;
        const task = state.tasks.find(candidate => candidate.details?.id === taskId);
        if (!task) {
            showToast("Task not found", "Refresh the page and try again.", true);
            return;
        }

        if (action === "run") {
            const confirmation = await confirmAction({
                title: `Run ${task.label || task.details?.method_name}?`,
                message: "This creates a tracked asynchronous execution immediately.",
                confirmLabel: "Run async"
            });
            if (!confirmation.confirmed) {
                return;
            }
            await withButtonBusy(button, async () => {
                const execution = await request(`/tasks/${encodeURIComponent(taskId)}/execute-async`, {method: "POST"});
                showToast("Execution submitted", `${task.label || "Task"} is queued as ${shortId(execution.execution_id)}.`);
                await loadExecutions(true);
                switchView("executions");
                scheduleExecutionPolling();
            });
            return;
        }

        if (action === "disable") {
            const confirmation = await confirmAction({
                title: `Pause ${task.label || task.details?.method_name}?`,
                message: "Automatic scheduled execution will stop. Manual async runs remain available.",
                confirmLabel: "Pause schedule",
                showInterrupt: true
            });
            if (!confirmation.confirmed) {
                return;
            }
            await withButtonBusy(button, async () => {
                const updatedTask = await request(
                    `/tasks/${encodeURIComponent(taskId)}/disable?interrupt=${confirmation.interrupt}`,
                    {method: "POST"}
                );
                replaceTask(updatedTask);
                showToast("Schedule paused", confirmation.interrupt
                    ? "Automatic runs stopped and interruption was requested."
                    : "Automatic runs stopped; a current invocation may finish.");
            });
            return;
        }

        if (action === "enable") {
            await withButtonBusy(button, async () => {
                const updatedTask = await request(`/tasks/${encodeURIComponent(taskId)}/enable`, {method: "POST"});
                replaceTask(updatedTask);
                showToast("Schedule resumed", `${task.label || "Task"} will run automatically again.`);
            });
        }
    }

    async function handleExecutionAction(button) {
        if (button.dataset.action !== "cancel-execution") {
            return;
        }
        const executionId = button.dataset.executionId;
        await withButtonBusy(button, async () => {
            await request(`/executions/${encodeURIComponent(executionId)}`, {method: "DELETE"});
            showToast("Cancellation requested", `${shortId(executionId)} will stop as soon as possible.`);
            await loadExecutions(true);
            scheduleExecutionPolling();
        });
    }

    function replaceTask(updatedTask) {
        const taskId = updatedTask.details?.id;
        state.tasks = state.tasks.map(task => task.details?.id === taskId ? updatedTask : task);
        renderTasks();
        renderCadenceRail();
        renderMetrics();
    }

    async function withButtonBusy(button, operation) {
        button.disabled = true;
        try {
            await operation();
            hideError();
        } catch (error) {
            const copy = errorCopy(error.status);
            showToast(copy.title, copy.message, true);
        } finally {
            button.disabled = false;
        }
    }

    function confirmAction({title, message, confirmLabel, showInterrupt = false}) {
        if (state.pendingConfirmation) {
            state.pendingConfirmation({confirmed: false, interrupt: false});
        }

        elements.confirmTitle.textContent = title;
        elements.confirmMessage.textContent = message;
        elements.confirmSubmit.textContent = confirmLabel;
        elements.interruptOption.hidden = !showInterrupt;
        elements.interruptCheckbox.checked = false;
        elements.confirmDialog.returnValue = "";
        elements.confirmDialog.showModal();

        return new Promise(resolve => {
            state.pendingConfirmation = resolve;
        });
    }

    function finishConfirmation() {
        if (!state.pendingConfirmation) {
            return;
        }
        const resolve = state.pendingConfirmation;
        state.pendingConfirmation = null;
        resolve({
            confirmed: elements.confirmDialog.returnValue === "confirm",
            interrupt: elements.interruptCheckbox.checked
        });
    }

    function switchView(view) {
        state.activeView = view;
        document.querySelectorAll(".view-tab").forEach(tab => {
            const active = tab.dataset.view === view;
            tab.classList.toggle("is-active", active);
            tab.setAttribute("aria-selected", String(active));
        });
        document.querySelector("#tasks-panel").hidden = view !== "tasks";
        document.querySelector("#executions-panel").hidden = view !== "executions";
    }

    function handleLoadError(error) {
        const copy = errorCopy(error.status);
        setConnection("error", "Unavailable");
        elements.errorTitle.textContent = copy.title;
        elements.errorMessage.textContent = copy.message;
        elements.errorNotice.hidden = false;
    }

    function hideError() {
        elements.errorNotice.hidden = true;
    }

    function setConnection(status, label) {
        elements.connectionState.classList.toggle("is-online", status === "online");
        elements.connectionState.classList.toggle("is-error", status === "error");
        elements.connectionLabel.textContent = label;
    }

    function showToast(title, message, error = false) {
        const toast = document.createElement("div");
        toast.className = `toast${error ? " is-error" : ""}`;
        toast.innerHTML = `<div><strong>${escapeHtml(title)}</strong><span>${escapeHtml(message)}</span></div>`;
        elements.toastRegion.append(toast);
        setTimeout(() => toast.remove(), 5_000);
    }

    function errorCopy(status) {
        const messages = {
            401: {title: "Authentication required", message: "Sign in to the application, then refresh this page."},
            403: {title: "Access denied", message: "Your account cannot use the cronctl API."},
            404: {title: "Resource not found", message: "The task or execution no longer exists. Refresh and try again."},
            409: {title: "State changed", message: "This action is not available in the current state. Refresh and try again."},
            429: {title: "Execution queue is full", message: "Wait for a running task to finish, then submit again."},
            500: {title: "Schedule operation failed", message: "The scheduler could not complete the request. Check application logs."}
        };
        return messages[status] || {title: "Could not reach cronctl", message: "Check the application connection and try again."};
    }

    function scheduleType(schedule) {
        if (schedule?.cron) return "Cron expression";
        if (schedule?.fixed_rate != null || schedule?.fixed_rate_string) return "Fixed rate";
        if (schedule?.fixed_delay != null || schedule?.fixed_delay_string) return "Fixed delay";
        return "Schedule";
    }

    function scheduleValue(schedule) {
        if (!schedule) return "Not available";
        if (schedule.cron) return `${schedule.cron}${schedule.zone ? ` · ${schedule.zone}` : ""}`;
        if (schedule.fixed_rate_string) return schedule.fixed_rate_string;
        if (schedule.fixed_rate != null) return `every ${formatInterval(schedule.fixed_rate, schedule.time_unit)}`;
        if (schedule.fixed_delay_string) return schedule.fixed_delay_string;
        if (schedule.fixed_delay != null) return `${formatInterval(schedule.fixed_delay, schedule.time_unit)} after finish`;
        return "Not available";
    }

    function nextExecutionUnavailableLabel(task) {
        if (!task.enabled) return "Paused";
        const schedule = task.details?.schedule;
        const intervalSchedule = schedule?.fixed_rate != null
            || Boolean(schedule?.fixed_rate_string)
            || schedule?.fixed_delay != null
            || Boolean(schedule?.fixed_delay_string);
        return intervalSchedule ? "Running or overdue" : "Not available";
    }

    function formatInterval(value, unit) {
        const labels = {
            NANOSECONDS: "ns", MICROSECONDS: "μs", MILLISECONDS: "ms", SECONDS: "s",
            MINUTES: "min", HOURS: "h", DAYS: "d"
        };
        return `${Number(value).toLocaleString()} ${labels[unit] || unit || "ms"}`;
    }

    function readableStatus(status) {
        return String(status || "unknown").toLocaleLowerCase().replaceAll("_", " ");
    }

    function statusClass(status) {
        return String(status || "unknown").toLocaleLowerCase().replace(/[^a-z_]/g, "");
    }

    function formatDuration(milliseconds) {
        const value = Number(milliseconds);
        if (!Number.isFinite(value)) return "—";
        if (value < 1_000) return `${value} ms`;
        if (value < 60_000) return `${(value / 1_000).toFixed(value < 10_000 ? 1 : 0)} s`;
        return `${Math.floor(value / 60_000)}m ${Math.floor((value % 60_000) / 1_000)}s`;
    }

    function formatDate(value) {
        if (!value) return "—";
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return "—";
        return new Intl.DateTimeFormat(undefined, {
            dateStyle: "medium",
            timeStyle: "medium"
        }).format(date);
    }

    function formatClockTime(value) {
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return "—";
        return new Intl.DateTimeFormat(undefined, {
            hour: "2-digit",
            minute: "2-digit",
            second: "2-digit"
        }).format(date);
    }

    function formatRelative(value) {
        const difference = timestamp(value) - Date.now();
        if (!Number.isFinite(difference)) return "Not available";
        if (difference <= 0) return "due now";
        const absolute = Math.abs(difference);
        if (absolute < 60_000) return "in under a minute";
        if (absolute < 3_600_000) return `in ${Math.max(1, Math.round(difference / 60_000))} min`;
        if (absolute < 86_400_000) return `in ${Math.max(1, Math.round(difference / 3_600_000))} h`;
        return formatDate(value);
    }

    function timestamp(value) {
        const result = Date.parse(value || "");
        return Number.isNaN(result) ? 0 : result;
    }

    function shortId(value) {
        const text = String(value || "");
        return text.length > 13 ? `${text.slice(0, 8)}…${text.slice(-4)}` : text || "—";
    }

    function emptyState(title, message) {
        return `<div class="empty-state"><strong>${escapeHtml(title)}</strong><p>${escapeHtml(message)}</p></div>`;
    }

    function escapeHtml(value) {
        return String(value ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#039;");
    }

    function updateClock() {
        elements.localClock.textContent = new Intl.DateTimeFormat(undefined, {
            hour: "2-digit",
            minute: "2-digit",
            second: "2-digit"
        }).format(new Date());
    }

    function bindEvents() {
        elements.refreshButton.addEventListener("click", () => refreshAll({manual: true}));
        elements.errorRetry.addEventListener("click", () => refreshAll({manual: true}));
        elements.taskFilters.addEventListener("submit", event => event.preventDefault());
        elements.taskSearch.addEventListener("input", renderTasks);
        elements.groupFilter.addEventListener("change", renderTasks);
        elements.stateFilter.addEventListener("change", renderTasks);
        elements.executionFilter.addEventListener("change", renderExecutions);
        elements.confirmDialog.addEventListener("close", finishConfirmation);

        elements.viewTabs.addEventListener("click", event => {
            const tab = event.target.closest("[data-view]");
            if (tab) switchView(tab.dataset.view);
        });

        elements.taskList.addEventListener("click", event => {
            const button = event.target.closest("button[data-action]");
            if (button) handleTaskAction(button);
        });

        elements.executionList.addEventListener("click", event => {
            const button = event.target.closest("button[data-action]");
            if (button) handleExecutionAction(button);
        });

        document.addEventListener("visibilitychange", () => {
            if (!document.hidden) {
                refreshAll();
                schedulePolling();
            }
        });
    }

    async function initialise() {
        bindEvents();
        updateClock();
        setInterval(updateClock, 1_000);
        await refreshAll();
        schedulePolling();
    }

    initialise();
})();
