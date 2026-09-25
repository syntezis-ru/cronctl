(() => {
    "use strict";

    const apiBasePath = (document.body.dataset.apiBasePath || "/api/cronctl").replace(/\/$/, "");
    const activeStatuses = new Set(["CREATED", "QUEUED", "RUNNING"]);
    const terminalFailureStatuses = new Set(["FAILED", "TIMED_OUT"]);
    const transport = window.cronctlTransport;
    const connectionLabel = transport?.connectionLabel || "Live";

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
        executionFilters: document.querySelector("#execution-filters"),
        executionFromFilter: document.querySelector("#execution-from-filter"),
        executionList: document.querySelector("#execution-list"),
        executionNext: document.querySelector("#execution-next"),
        executionNodeFilter: document.querySelector("#execution-node-filter"),
        executionPageLabel: document.querySelector("#execution-page-label"),
        executionPagination: document.querySelector("#execution-pagination"),
        executionPrevious: document.querySelector("#execution-previous"),
        executionSourceFilter: document.querySelector("#execution-source-filter"),
        executionTaskFilter: document.querySelector("#execution-task-filter"),
        executionToFilter: document.querySelector("#execution-to-filter"),
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
        retryAllFailed: document.querySelector("#retry-all-failed"),
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
        executionPage: 0,
        executionSize: 50,
        executionTotal: 0,
        executionHasNext: false,
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
        if (typeof transport?.request === "function") {
            return transport.request(path, options);
        }

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
        populateExecutionTasks();
        renderTasks();
        renderCadenceRail();
        renderMetrics();
    }

    async function loadExecutions(silent = false) {
        if (!silent && state.executions.length === 0) {
            elements.executionList.setAttribute("aria-busy", "true");
        }

        const parameters = new URLSearchParams({
            page: String(state.executionPage),
            size: String(state.executionSize)
        });
        addFilter(parameters, "taskKey", elements.executionTaskFilter.value);
        addFilter(parameters, "status", elements.executionFilter.value);
        addFilter(parameters, "source", elements.executionSourceFilter.value);
        addFilter(parameters, "nodeId", elements.executionNodeFilter.value);
        addDateFilter(parameters, "from", elements.executionFromFilter.value);
        addDateFilter(parameters, "to", elements.executionToFilter.value);

        const response = await request(`/executions?${parameters}`);
        state.executions = Array.isArray(response?.executions) ? response.executions : [];
        state.executionTotal = Number(response?.total || 0);
        state.executionPage = Number(response?.page || 0);
        state.executionSize = Number(response?.size || 50);
        state.executionHasNext = Boolean(response?.has_next);
        populateExecutionNodes();
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
            setConnection("online", connectionLabel);
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
                    setConnection("online", connectionLabel);
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
                    setConnection("online", connectionLabel);
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
                    setConnection("online", connectionLabel);
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
        const taskKey = task.task_key || "";
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
            ? `<button class="button ${toggleButtonClass}" type="button" data-action="${toggleAction}" data-task-key="${escapeHtml(taskKey)}">${toggleLabel}</button>`
            : "";
        const nextExecution = task.next_execution_at
            ? `<time datetime="${escapeHtml(task.next_execution_at)}" title="${escapeHtml(formatDate(task.next_execution_at))}">${escapeHtml(formatRelative(task.next_execution_at))}</time>`
            : `<span>${escapeHtml(nextExecutionUnavailableLabel(task))}</span>`;
        const health = task.scheduled_health || {};
        const healthStatus = health.last_execution_status
            ? `<span class="status-badge status-badge--${statusClass(health.last_execution_status)}">${escapeHtml(readableStatus(health.last_execution_status))}</span>`
            : `<span class="health-readout__empty">No runs yet</span>`;
        const failureCount = Number(health.consecutive_failures || 0);
        const concurrencyPolicy = task.concurrency_policy || "ALLOW";
        const concurrencyToken = concurrencyPolicy === "ALLOW"
            ? ""
            : `<span class="meta-token" title="Process-local concurrency policy">${escapeHtml(readableStatus(concurrencyPolicy))} · max ${escapeHtml(task.max_concurrent_executions || 1)}</span>`;
        const retryToken = Number(task.retries || 0) > 0
            ? `<span class="meta-token meta-token--retry" title="Automatic retry policy">Retry ${escapeHtml(task.retries)} · ${escapeHtml(readableStatus(task.retry_backoff || "FIXED"))} · ${escapeHtml(task.retry_delay || "PT1S")}</span>`
            : "";
        const trackingWarning = task.automatic_tracking_status === "AMBIGUOUS"
            ? `<p class="tracking-warning" title="${escapeHtml(task.automatic_tracking_message || "Automatic tracking is ambiguous")}">Automatic history unavailable: duplicate scheduled bean target</p>`
            : "";

        return `
            <article class="task-card ${enabled ? "" : "is-disabled"}" data-task-key="${escapeHtml(taskKey)}">
                <div class="task-card__identity">
                    <div class="task-card__title-row">
                        <h3 title="${escapeHtml(task.label || "Unnamed task")}">${escapeHtml(task.label || "Unnamed task")}</h3>
                        <span class="status-badge status-badge--${stateName}">${stateLabel}</span>
                    </div>
                    <p class="task-card__description">${escapeHtml(task.description || task.details?.method_name || "No description")}</p>
                    <div class="task-card__meta">
                        <span class="meta-token">${escapeHtml(task.group || "default")}</span>
                        ${tags}${remainingTags}${concurrencyToken}${retryToken}
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
                <div class="health-readout">
                    <span class="next-run__label">Last scheduled run</span>
                    <div>${healthStatus}</div>
                    <small>${health.last_success_at ? `Last success ${escapeHtml(formatRelativePast(health.last_success_at))}` : "No successful run recorded"}</small>
                    ${failureCount > 0 ? `<strong>${failureCount} consecutive failure${failureCount === 1 ? "" : "s"}</strong>` : ""}
                </div>
                <div class="task-actions">
                    ${toggleButton}
                    <button class="button button--primary" type="button" data-action="run" data-task-key="${escapeHtml(taskKey)}">Run async</button>
                </div>
                ${trackingWarning}
            </article>`;
    }

    function renderExecutions() {
        elements.executionList.setAttribute("aria-busy", "false");
        elements.executionsTabCount.textContent = String(state.executionTotal);

        if (state.executions.length === 0) {
            const hasFilters = Boolean(elements.executionTaskFilter.value
                || elements.executionFilter.value
                || elements.executionSourceFilter.value
                || elements.executionNodeFilter.value
                || elements.executionFromFilter.value
                || elements.executionToFilter.value);
            elements.executionList.innerHTML = emptyState(
                hasFilters ? "No executions match these filters" : "No executions recorded yet",
                hasFilters ? "Change a filter or widen the time range." : "Automatic and manual invocations will appear here."
            );
            renderExecutionPagination();
            return;
        }

        const taskLabels = new Map(state.tasks.map(task => [task.task_key, task.label]));
        elements.executionList.innerHTML = state.executions.map(execution => {
            const taskLabel = taskLabels.get(execution.task_key) || "Unknown task";
            const canCancel = (execution.source === "MANUAL_ASYNC" || execution.source === "RETRY")
                && activeStatuses.has(execution.status);
            const canRetry = Boolean(execution.retryable);
            const retryLineage = execution.source === "RETRY"
                ? `<span class="execution-card__attempt" title="Retry series ${escapeHtml(execution.retry_series_id || "")}">${escapeHtml(readableStatus(execution.retry_trigger || "AUTOMATIC"))} retry · attempt ${escapeHtml(execution.attempt || 1)}</span>`
                : "";
            const duration = execution.duration_ms == null
                ? (execution.status === "RUNNING" ? "In progress" : "—")
                : formatDuration(execution.duration_ms);
            const drift = execution.start_delay_ms == null
                ? "Unknown"
                : formatDrift(execution.start_delay_ms);
            const failure = execution.error?.message
                ? `<p class="failure-message"><strong>${escapeHtml(execution.error.type || "Execution failed")}</strong>${escapeHtml(execution.error.message)}</p>`
                : "";
            const reason = execution.status_reason
                ? `<p class="execution-reason">${escapeHtml(readableStatus(execution.status_reason))}</p>`
                : "";

            return `
                <article class="execution-card" data-execution-id="${escapeHtml(execution.execution_id)}">
                    <div class="execution-card__identity">
                        <h3 title="${escapeHtml(taskLabel)}">${escapeHtml(taskLabel)}</h3>
                        <span class="execution-card__id" title="${escapeHtml(execution.execution_id)}">${escapeHtml(shortId(execution.execution_id))}</span>
                        <div class="execution-card__origin">
                            <span class="source-badge source-badge--${statusClass(execution.source)}">${escapeHtml(readableStatus(execution.source))}</span>
                            <span title="${escapeHtml(execution.node_id)}">${escapeHtml(execution.node_id || "Unknown node")}</span>
                            ${retryLineage}
                        </div>
                    </div>
                    <div class="execution-card__state">
                        <span class="status-badge status-badge--${statusClass(execution.status)}">${escapeHtml(readableStatus(execution.status))}</span>
                        <span class="execution-card__duration">${escapeHtml(duration)}</span>
                        ${reason}
                    </div>
                    <div class="execution-card__timeline" aria-label="Planned and actual start">
                        <div>
                            <span>Planned</span>
                            <time class="execution-card__time" datetime="${escapeHtml(execution.planned_at || "")}">${escapeHtml(formatDate(execution.planned_at))}</time>
                        </div>
                        <div>
                            <span>Started</span>
                            <time class="execution-card__time" datetime="${escapeHtml(execution.started_at || "")}">${escapeHtml(formatDate(execution.started_at))}</time>
                        </div>
                    </div>
                    <div class="execution-card__drift">
                        <span>Start drift</span>
                        <strong class="${Number(execution.start_delay_ms) > 1000 ? "is-late" : ""}">${escapeHtml(drift)}</strong>
                        <small>Created ${escapeHtml(formatRelativePast(execution.created_at))}</small>
                    </div>
                    <div class="execution-actions">
                        ${canRetry ? `<button class="button button--quiet" type="button" data-action="retry-execution" data-execution-id="${escapeHtml(execution.execution_id)}">Retry</button>` : ""}
                        ${canCancel ? `<button class="button button--danger" type="button" data-action="cancel-execution" data-execution-id="${escapeHtml(execution.execution_id)}">Cancel</button>` : ""}
                    </div>
                    ${failure}
                </article>`;
        }).join("");
        renderExecutionPagination();
    }

    function renderExecutionPagination() {
        const pageCount = Math.max(1, Math.ceil(state.executionTotal / state.executionSize));
        elements.executionPagination.hidden = state.executionTotal <= state.executionSize;
        elements.executionPrevious.disabled = state.executionPage === 0;
        elements.executionNext.disabled = !state.executionHasNext;
        elements.executionPageLabel.textContent = `Page ${state.executionPage + 1} of ${pageCount} · ${state.executionTotal.toLocaleString()} executions`;
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
            terminalFailureStatuses.has(execution.status) && timestamp(execution.created_at) >= oneDayAgo
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

    function populateExecutionTasks() {
        const currentValue = elements.executionTaskFilter.value;
        const options = [...state.tasks]
            .sort((left, right) => String(left.label || left.task_key).localeCompare(String(right.label || right.task_key)))
            .map(task => `<option value="${escapeHtml(task.task_key)}">${escapeHtml(task.label || task.task_key)}</option>`)
            .join("");
        elements.executionTaskFilter.innerHTML = `<option value="">All tasks</option>${options}`;
        if (state.tasks.some(task => task.task_key === currentValue)) {
            elements.executionTaskFilter.value = currentValue;
        }
    }

    function populateExecutionNodes() {
        const currentValue = elements.executionNodeFilter.value;
        const nodes = [...new Set(state.executions.map(execution => execution.node_id).filter(Boolean))].sort();
        if (currentValue && !nodes.includes(currentValue)) {
            nodes.unshift(currentValue);
        }
        elements.executionNodeFilter.innerHTML = `<option value="">All nodes</option>${nodes
            .map(node => `<option value="${escapeHtml(node)}">${escapeHtml(node)}</option>`)
            .join("")}`;
        elements.executionNodeFilter.value = currentValue;
    }

    async function handleTaskAction(button) {
        const action = button.dataset.action;
        const taskKey = button.dataset.taskKey;
        const task = state.tasks.find(candidate => candidate.task_key === taskKey);
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
                const execution = await request(`/tasks/${encodeURIComponent(taskKey)}/execute-async`, {method: "POST"});
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
                    `/tasks/${encodeURIComponent(taskKey)}/disable?interrupt=${confirmation.interrupt}`,
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
                const updatedTask = await request(`/tasks/${encodeURIComponent(taskKey)}/enable`, {method: "POST"});
                replaceTask(updatedTask);
                showToast("Schedule resumed", `${task.label || "Task"} will run automatically again.`);
            });
        }
    }

    async function handleExecutionAction(button) {
        const action = button.dataset.action;
        const executionId = button.dataset.executionId;
        if (action === "retry-execution") {
            const confirmation = await confirmAction({
                title: `Retry ${shortId(executionId)}?`,
                message: "This creates a new tracked retry series and may repeat the task's side effects.",
                confirmLabel: "Retry execution"
            });
            if (!confirmation.confirmed) {
                return;
            }
            await withButtonBusy(button, async () => {
                const retry = await request(`/executions/${encodeURIComponent(executionId)}/retry`, {method: "POST"});
                showToast("Retry queued", `${shortId(retry.execution_id)} will run under the task's concurrency policy.`);
                await loadExecutions(true);
                scheduleExecutionPolling();
            });
            return;
        }
        if (action !== "cancel-execution") {
            return;
        }
        await withButtonBusy(button, async () => {
            await request(`/executions/${encodeURIComponent(executionId)}`, {method: "DELETE"});
            showToast("Cancellation requested", `${shortId(executionId)} will stop as soon as possible.`);
            await loadExecutions(true);
            scheduleExecutionPolling();
        });
    }

    function replaceTask(updatedTask) {
        const taskKey = updatedTask.task_key;
        state.tasks = state.tasks.map(task => task.task_key === taskKey ? updatedTask : task);
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

    function addFilter(parameters, name, value) {
        if (value) parameters.set(name, value);
    }

    function addDateFilter(parameters, name, value) {
        if (!value) return;
        const date = new Date(value);
        if (!Number.isNaN(date.getTime())) parameters.set(name, date.toISOString());
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

    function formatDrift(milliseconds) {
        const value = Number(milliseconds);
        if (!Number.isFinite(value)) return "Unknown";
        if (Math.abs(value) < 1) return "On time";
        const prefix = value > 0 ? "+" : "−";
        return `${prefix}${formatDuration(Math.abs(value))}`;
    }

    function formatRelativePast(value) {
        const difference = Date.now() - timestamp(value);
        if (!Number.isFinite(difference) || timestamp(value) === 0) return "not recorded";
        if (difference < 60_000) return "just now";
        if (difference < 3_600_000) return `${Math.max(1, Math.round(difference / 60_000))} min ago`;
        if (difference < 86_400_000) return `${Math.max(1, Math.round(difference / 3_600_000))} h ago`;
        return formatDate(value);
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
        elements.retryAllFailed.addEventListener("click", handleRetryAllFailed);
        elements.taskFilters.addEventListener("submit", event => event.preventDefault());
        elements.taskSearch.addEventListener("input", renderTasks);
        elements.groupFilter.addEventListener("change", renderTasks);
        elements.stateFilter.addEventListener("change", renderTasks);
        elements.executionFilters.addEventListener("submit", event => event.preventDefault());
        elements.executionFilters.addEventListener("change", () => {
            state.executionPage = 0;
            loadExecutions().catch(handleLoadError);
        });
        elements.executionPrevious.addEventListener("click", () => changeExecutionPage(-1));
        elements.executionNext.addEventListener("click", () => changeExecutionPage(1));
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

    async function handleRetryAllFailed() {
        const confirmation = await confirmAction({
            title: "Retry all failed tasks?",
            message: "The latest eligible failed or timed-out execution for each task will run again.",
            confirmLabel: "Retry all failed"
        });
        if (!confirmation.confirmed) {
            return;
        }
        await withButtonBusy(elements.retryAllFailed, async () => {
            const response = await request("/executions/retry-all-failed", {method: "POST"});
            const submitted = Number(response?.submitted || 0);
            showToast(
                submitted > 0 ? "Retries queued" : "No eligible failures",
                submitted > 0
                    ? `${submitted} task${submitted === 1 ? "" : "s"} queued for retry.`
                    : "Every latest failure already has a retry or is no longer available."
            );
            await loadExecutions(true);
            scheduleExecutionPolling();
        });
    }

    function changeExecutionPage(offset) {
        state.executionPage = Math.max(0, state.executionPage + offset);
        loadExecutions().catch(handleLoadError);
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
