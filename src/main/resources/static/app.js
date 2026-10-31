'use strict';

const POLL_MS = 3000;

const STATUS = {
    PENDING:          { label: 'Chờ bắt đầu' },
    IN_PROGRESS:      { label: 'Đang chạy', pulse: true },
    WAITING_APPROVAL: { label: 'Chờ duyệt', pulse: true },
    WAITING_RETRY:    { label: 'Chờ retry', pulse: true },
    COMPENSATING:     { label: 'Đang rollback', pulse: true },
    SUCCESS:          { label: 'Thành công' },
    FAILED:           { label: 'Thất bại' },
};
const WAITING_STATUSES = ['WAITING_APPROVAL', 'WAITING_RETRY'];

const STEP_LABEL = {
    VALIDATE_REQUEST: 'Kiểm tra yêu cầu',
    PROVISION_DATABASE: 'Tạo database',
    CREATE_ADMIN_USER: 'Tạo admin user',
    SETUP_BILLING: 'Thiết lập billing',
    WAIT_APPROVAL: 'Chờ admin duyệt',
    ACTIVATE_TENANT: 'Kích hoạt tenant',
    SEND_WELCOME_EMAIL: 'Gửi email chào mừng',
};
// Cac buoc co compensation (rollback) khi flow that bai
const COMPENSABLE = new Set(['PROVISION_DATABASE', 'CREATE_ADMIN_USER', 'SETUP_BILLING']);

const STEP_STATE_LABEL = {
    done: 'Hoàn thành',
    running: 'Đang chạy',
    waiting: 'Chờ trigger',
    retry: 'Lỗi, chờ trigger',
    failed: 'Thất bại tại đây',
    rollingback: 'Đang rollback',
    rolledback: 'Đã rollback',
    pending: '',
};

const state = {
    config: null,
    tenants: [],
    filter: 'ALL',
    search: '',
    selectedId: location.hash.slice(1) || null,
    detail: null,
    busy: false,
};

const $ = (sel) => document.querySelector(sel);

// ---------------------------------------------------------------- API

async function api(path, options = {}) {
    const res = await fetch(path, {
        headers: { 'Content-Type': 'application/json' },
        ...options,
    });
    const body = res.status === 204 ? null : await res.json().catch(() => null);
    if (!res.ok) {
        const msg = body?.detail || body?.message || body?.title || `HTTP ${res.status}`;
        throw new Error(msg);
    }
    return body;
}

async function refresh() {
    try {
        state.tenants = await api('/api/tenants/overview');
        if (state.selectedId) {
            state.detail = await api(`/api/tenants/${state.selectedId}/detail`).catch((e) => {
                if (/not found/i.test(e.message)) {
                    state.selectedId = null;
                    history.replaceState(null, '', location.pathname);
                }
                return null;
            });
        }
        $('#conn').classList.remove('down');
        $('#conn').title = 'Đã kết nối API';
    } catch (e) {
        $('#conn').classList.add('down');
        $('#conn').title = 'Mất kết nối API: ' + e.message;
    }
    render();
}

async function trigger(action) {
    const id = state.selectedId;
    const input = ($('#trigger-input')?.value || '').trim();
    const bodies = {
        approve: { actor: input || 'admin' },
        reject: { reason: input || 'Rejected from UI' },
        abort: { reason: input || 'Aborted from UI' },
        retry: null,
    };
    state.busy = true;
    render();
    try {
        const body = bodies[action];
        const res = await api(`/api/tenants/${id}/${action}`, {
            method: 'POST',
            body: body ? JSON.stringify(body) : undefined,
        });
        toast(`${action.toUpperCase()}: ${res?.result ?? 'OK'}`);
        const inputEl = $('#trigger-input');
        if (inputEl) inputEl.value = '';
    } catch (e) {
        toast(`${action.toUpperCase()} thất bại: ${e.message}`, true);
    } finally {
        state.busy = false;
    }
    // Workflow xu ly signal bat dong bo -> refresh ngay va them 1 lan sau
    await refresh();
    setTimeout(refresh, 1000);
}

// ---------------------------------------------------------------- Render

function render() {
    renderSummary();
    renderRows();
    renderDetail();
}

function renderSummary() {
    const counts = { ALL: state.tenants.length, WAITING: 0 };
    for (const s of Object.keys(STATUS)) counts[s] = 0;
    for (const t of state.tenants) {
        counts[t.status] = (counts[t.status] || 0) + 1;
        if (WAITING_STATUSES.includes(t.status)) counts.WAITING++;
    }
    const chips = [
        ['ALL', 'Tất cả', ''],
        ['WAITING', 'Đang chờ trigger', 'st-WAITING'],
        ...Object.entries(STATUS).map(([k, v]) => [k, v.label, `st-${k}`]),
    ];
    $('#summary').innerHTML = chips.map(([key, label, cls]) => `
        <button class="chip ${cls} ${state.filter === key ? 'active' : ''}" data-filter="${key}"
                title="${key === 'ALL' || key === 'WAITING' ? '' : key}">
            <span class="n">${counts[key] ?? 0}</span>
            <span class="l">${label}</span>
        </button>`).join('');
}

function filteredTenants() {
    const q = state.search.toLowerCase();
    return state.tenants.filter((t) => {
        if (state.filter === 'WAITING' && !WAITING_STATUSES.includes(t.status)) return false;
        if (state.filter !== 'ALL' && state.filter !== 'WAITING' && t.status !== state.filter) return false;
        if (!q) return true;
        return [t.name, t.id, t.workflowId, t.adminEmail].some((v) => (v || '').toLowerCase().includes(q));
    });
}

function renderRows() {
    const rows = filteredTenants();
    $('#empty').hidden = rows.length > 0;
    $('#empty').textContent = state.tenants.length
        ? 'Không có flow nào khớp bộ lọc.'
        : 'Chưa có tenant nào. Bấm “+ Tạo tenant” để bắt đầu.';
    $('#tenant-rows').innerHTML = rows.map((t) => `
        <tr data-id="${esc(t.id)}" class="${t.id === state.selectedId ? 'selected' : ''}">
            <td>
                <div class="t-name">${esc(t.name)}</div>
                <div class="t-id mono">${esc(t.workflowId)}</div>
            </td>
            <td>${statusBadge(t.status)}</td>
            <td>${t.currentStep ? esc(STEP_LABEL[t.currentStep] || t.currentStep) : '<span class="t-id">—</span>'}</td>
            <td>${temporalChip(t.temporalStatus)}</td>
            <td class="t-time" title="${esc(fmtDateTime(t.updatedAt))}">${fmtRelative(t.updatedAt)}</td>
        </tr>`).join('');
}

function renderDetail() {
    const el = $('#detail');
    const d = state.detail;
    if (!state.selectedId || !d) {
        el.innerHTML = '<p class="empty">Chọn một flow ở danh sách để xem chi tiết.</p>';
        return;
    }

    // Giu lai noi dung dang go trong o nhap khi re-render moi 3s
    const prevInput = $('#trigger-input');
    const typed = prevInput?.value ?? '';
    const hadFocus = document.activeElement === prevInput;

    const t = d.tenant;
    const ws = d.workflowState;
    const temporalUrl = workflowUrl(t.workflowId);

    el.innerHTML = `
        <div class="detail-head">
            <div class="detail-title">
                <h2>${esc(t.name)}</h2>
                <a class="btn ghost" href="${esc(temporalUrl)}" target="_blank" rel="noopener">Xem trên Temporal ↗</a>
            </div>
            <div class="status-compare">
                <div><span class="k">Business status</span>${statusBadge(t.status)}</div>
                <div><span class="k">Temporal execution</span>${temporalChip(t.temporalStatus)}</div>
            </div>
            ${t.temporalStatus === 'NOT_FOUND' ? `<p class="no-action">${esc(TEMPORAL_HINT.NOT_FOUND)}. Business status và timeline vẫn còn trong DB.</p>` : ''}
            ${t.message ? `<div class="message st-${t.status}">${esc(t.message)}</div>` : ''}
            <dl class="meta">
                <dt>Tenant ID</dt><dd class="mono">${esc(t.id)}</dd>
                <dt>Workflow ID</dt><dd class="mono">${esc(t.workflowId)}</dd>
                <dt>Email admin</dt><dd>${esc(t.adminEmail)}</dd>
                <dt>Gói</dt><dd>${esc(t.plan)}</dd>
                <dt>Bắt đầu</dt><dd>${esc(fmtDateTime(t.createdAt))}</dd>
            </dl>
        </div>

        <div class="section">
            <h3>Trigger</h3>
            ${renderActions(t, d)}
        </div>

        <div class="section">
            <h3>Các bước onboarding</h3>
            ${renderPipeline(t, ws, d.events)}
            ${d.queryError && t.temporalStatus !== 'NOT_FOUND' ? `<p class="no-action">Không query được workflow (${esc(d.queryError)}), tiến độ đang suy ra từ timeline.</p>` : ''}
        </div>

        <div class="section">
            <h3>Timeline (${d.events.length} sự kiện)</h3>
            <ol class="timeline">
                ${d.events.slice().reverse().map((e) => `
                    <li class="st-${e.status}">
                        <span class="time" title="${esc(fmtDateTime(e.createdAt))}">${fmtTime(e.createdAt)}</span>
                        <span class="bullet"></span>
                        <span class="what"><b>${esc(e.status)}</b>${esc(e.message || '')}</span>
                    </li>`).join('')}
            </ol>
        </div>`;

    const input = $('#trigger-input');
    if (input) {
        input.value = typed;
        if (hadFocus) input.focus();
    }
    updateCountdowns();
}

function renderActions(t, d) {
    const s = t.status;
    const busy = state.busy;
    const can = {
        approve: s === 'WAITING_APPROVAL',
        reject: s === 'WAITING_APPROVAL',
        retry: s === 'WAITING_RETRY',
        abort: s === 'WAITING_RETRY',
    };
    const deadline = d.waitingDeadline
        ? ` Hết hạn sau <span class="countdown" data-deadline="${esc(d.waitingDeadline)}"></span>, quá hạn flow sẽ chuyển sang <b>FAILED</b>.`
        : '';

    let intro;
    let placeholder = 'Người duyệt / lý do (tuỳ chọn)';
    if (s === 'WAITING_APPROVAL') {
        intro = `Flow đang <b>dừng chờ admin duyệt</b>. Approve để chạy tiếp, reject để rollback.${deadline}`;
        placeholder = 'Người duyệt (approve) hoặc lý do (reject)';
    } else if (s === 'WAITING_RETRY') {
        intro = `Bước <b>${esc(STEP_LABEL[t.currentStep] || t.currentStep)}</b> đã lỗi hết số lần tự retry. Retry để chạy lại bước này, abort để rollback.${deadline}`;
        placeholder = 'Lý do abort (tuỳ chọn)';
    } else if (s === 'SUCCESS' || s === 'FAILED') {
        intro = 'Flow đã kết thúc, không còn trigger nào.';
    } else {
        intro = 'Flow đang tự chạy, chưa cần trigger. Các nút sẽ bật khi flow dừng chờ.';
    }

    const waiting = WAITING_STATUSES.includes(s);
    return `
        <div class="trigger-box ${waiting ? `st-${s}` : ''}">
            <p>${intro}</p>
            <div class="actions">
                ${waiting ? `<input id="trigger-input" placeholder="${placeholder}">` : ''}
                ${actionBtn('approve', '✓ Approve', 'success', can.approve, busy, 'Chỉ dùng khi WAITING_APPROVAL')}
                ${actionBtn('reject', '✕ Reject', 'danger', can.reject, busy, 'Chỉ dùng khi WAITING_APPROVAL')}
                ${actionBtn('retry', '↻ Retry', 'warn', can.retry, busy, 'Chỉ dùng khi WAITING_RETRY')}
                ${actionBtn('abort', '■ Abort', 'danger', can.abort, busy, 'Chỉ dùng khi WAITING_RETRY')}
            </div>
        </div>`;
}

function actionBtn(action, label, cls, enabled, busy, hint) {
    return `<button class="btn ${cls}" data-action="${action}" ${enabled && !busy ? '' : 'disabled'}
                    title="${enabled ? `POST /api/tenants/{id}/${action}` : hint}">${label}</button>`;
}

function renderPipeline(t, ws, events) {
    const steps = state.config?.steps || Object.keys(STEP_LABEL);
    const completed = new Set(ws?.completedSteps || completedFromEvents(events));
    return `<ol class="pipeline">${steps.map((step, i) => {
        const st = stepState(step, t, completed);
        const icon = { done: '✓', failed: '✕', rolledback: '↺', rollingback: '↺' }[st] || (i + 1);
        return `
            <li class="${st}">
                <span class="dot">${icon}</span>
                <span><span class="step-name">${esc(STEP_LABEL[step] || step)}</span>
                      <span class="step-code mono">${esc(step)}</span></span>
                <span class="step-state">${STEP_STATE_LABEL[st]}</span>
            </li>`;
    }).join('')}</ol>`;
}

function stepState(step, t, completed) {
    const s = t.status;
    if (s === 'SUCCESS') return 'done';
    const ending = s === 'FAILED' || s === 'COMPENSATING';
    if (step === t.currentStep && !completed.has(step)) {
        if (s === 'IN_PROGRESS') return 'running';
        if (s === 'WAITING_APPROVAL') return 'waiting';
        if (s === 'WAITING_RETRY') return 'retry';
        if (ending) return 'failed';
    }
    if (completed.has(step)) {
        if (ending && COMPENSABLE.has(step)) return s === 'FAILED' ? 'rolledback' : 'rollingback';
        return 'done';
    }
    return 'pending';
}

// Du phong khi khong query duoc workflow: suy ra tu message cua timeline
function completedFromEvents(events) {
    const done = [];
    for (const e of events || []) {
        if (!e.step) continue;
        if (e.message === `${e.step} completed` || (e.step === 'WAIT_APPROVAL' && /^Approved by/.test(e.message || ''))) {
            done.push(e.step);
        }
    }
    return done;
}

function statusBadge(status) {
    const meta = STATUS[status] || { label: status };
    return `<span class="badge st-${esc(status)} ${meta.pulse ? 'pulse' : ''}" title="${esc(status)}">${esc(meta.label)}</span>`;
}

const TEMPORAL_HINT = {
    NOT_FOUND: 'Temporal đã xoá workflow này vì hết retention của namespace (mặc định 24h sau khi kết thúc)',
    UNKNOWN: 'Không kết nối được Temporal',
};

function temporalChip(status) {
    const label = status === 'NOT_FOUND' ? 'NOT FOUND' : status;
    const title = TEMPORAL_HINT[status] || 'Execution status trên Temporal';
    return `<span class="temporal" title="${esc(title)}">${esc(label)}</span>`;
}

function workflowUrl(workflowId) {
    const c = state.config;
    if (!c) return '#';
    return `${c.temporalUiUrl.replace(/\/$/, '')}/namespaces/${encodeURIComponent(c.temporalNamespace)}/workflows/${encodeURIComponent(workflowId)}`;
}

function updateCountdowns() {
    document.querySelectorAll('[data-deadline]').forEach((el) => {
        const ms = new Date(el.dataset.deadline) - Date.now();
        if (ms <= 0) {
            el.textContent = 'đã quá hạn';
            return;
        }
        const total = Math.floor(ms / 1000);
        const h = Math.floor(total / 3600);
        const m = Math.floor((total % 3600) / 60);
        const sec = total % 60;
        el.textContent = (h ? `${h}:` : '') + `${String(m).padStart(h ? 2 : 1, '0')}:${String(sec).padStart(2, '0')}`;
    });
}

// ---------------------------------------------------------------- Helpers

function esc(v) {
    return String(v ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

// Su kien hom nay chi hien gio, ngay khac hien them ngay/thang
function fmtTime(iso) {
    if (!iso) return '';
    const d = new Date(iso);
    const time = d.toLocaleTimeString('vi-VN', { hour12: false });
    if (d.toDateString() === new Date().toDateString()) return time;
    return `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')} ${time}`;
}

function fmtDateTime(iso) {
    return iso ? new Date(iso).toLocaleString('vi-VN', { hour12: false }) : '';
}

function fmtRelative(iso) {
    if (!iso) return '';
    const s = Math.max(0, Math.round((Date.now() - new Date(iso)) / 1000));
    if (s < 60) return `${s} giây trước`;
    if (s < 3600) return `${Math.floor(s / 60)} phút trước`;
    if (s < 86400) return `${Math.floor(s / 3600)} giờ trước`;
    return fmtDateTime(iso);
}

function toast(msg, isError = false) {
    const el = document.createElement('div');
    el.className = 'toast' + (isError ? ' error' : '');
    el.textContent = msg;
    $('#toasts').appendChild(el);
    setTimeout(() => el.remove(), isError ? 6000 : 3500);
}

function select(id) {
    state.selectedId = id;
    state.detail = null;
    history.replaceState(null, '', id ? `#${id}` : location.pathname);
    render();
    refresh();
}

// ---------------------------------------------------------------- Events

$('#summary').addEventListener('click', (e) => {
    const chip = e.target.closest('[data-filter]');
    if (!chip) return;
    state.filter = chip.dataset.filter;
    render();
});

$('#search').addEventListener('input', (e) => {
    state.search = e.target.value;
    renderRows();
});

$('#tenant-rows').addEventListener('click', (e) => {
    const row = e.target.closest('tr[data-id]');
    if (row) select(row.dataset.id);
});

$('#detail').addEventListener('click', (e) => {
    const btn = e.target.closest('[data-action]');
    if (btn && !btn.disabled) trigger(btn.dataset.action);
});

const dialog = $('#create-dialog');
$('#btn-new').addEventListener('click', () => {
    delete $('#create-form [name=adminEmail]').dataset.touched;
    dialog.showModal();
});
$('#create-cancel').addEventListener('click', () => dialog.close());

$('#create-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const form = new FormData(e.target);
    const body = {
        name: form.get('name').trim(),
        adminEmail: form.get('adminEmail').trim(),
        plan: form.get('plan'),
    };
    const rate = form.get('failureRate');
    if (rate !== '') body.failureRate = Number(rate);
    try {
        const tenant = await api('/api/tenants', { method: 'POST', body: JSON.stringify(body) });
        toast(`Đã tạo ${tenant.name}, workflow ${tenant.workflowId}`);
        dialog.close();
        e.target.reset();
        state.filter = 'ALL';
        select(tenant.id);
    } catch (err) {
        toast('Tạo tenant thất bại: ' + err.message, true);
    }
});

// Goi y email theo ten tenant
$('#create-form [name=name]').addEventListener('input', (e) => {
    const email = $('#create-form [name=adminEmail]');
    if (email.dataset.touched) return;
    const slug = e.target.value.normalize('NFD').replace(/\p{M}/gu, '').replace(/đ/gi, 'd')
        .toLowerCase().replace(/[^a-z0-9]+/g, '');
    email.value = slug ? `admin@${slug}.io` : '';
});
$('#create-form [name=adminEmail]').addEventListener('input', (e) => { e.target.dataset.touched = '1'; });

// ---------------------------------------------------------------- Start

(async function init() {
    try {
        state.config = await api('/api/config');
        $('#temporal-link').href = state.config.temporalUiUrl;
        $('#rate-hint').textContent = `(mặc định ${state.config.defaultFailureRate}, mỗi bước ${state.config.stepDelayMinSeconds}–${state.config.stepDelayMaxSeconds}s)`;
    } catch (e) {
        toast('Không tải được cấu hình: ' + e.message, true);
    }
    await refresh();
    setInterval(refresh, POLL_MS);
    setInterval(updateCountdowns, 1000);
})();
