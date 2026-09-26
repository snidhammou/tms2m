'use strict';

/* Console d'administration TMS2M — SPA vanilla JS sur l'API /api/admin/v1 */

const API = '/api/admin/v1';
const AGENT_PACKAGE = 'com.tms.agent';
const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

let auth = null;
try { auth = sessionStorage.getItem('tms.auth'); } catch (e) { /* stockage indisponible */ }
let meta = null;
let currentView = 'dashboard';
let refreshTimer = null;

const TASK_LABELS = {
    INSTALL_APP: 'Installer une application', UNINSTALL_APP: 'Désinstaller une application',
    PUSH_PARAMS: 'Pousser les paramètres', REBOOT: 'Redémarrer', SET_AUTORUN: 'Démarrage automatique',
    SET_KIOSK: 'Mode kiosque', DIAGNOSE: 'Diagnostic à distance', EXTRACT_LOGS: 'Extraire les logs',
    EXTRACT_FILE: 'Extraire un fichier'
};
const AUDIT_LABELS = {
    ENROLLED: 'Enrôlement', APP_INSTALLED: 'Application installée', APP_UPDATED: 'Application mise à jour',
    APP_REMOVED: 'Application retirée', ZERO_TOUCH: 'Provisioning zéro contact', DEPLOYMENT: 'Déploiement',
    TASK_CANCELLED: 'Tâche annulée', TERMINAL_REGISTERED: 'Pré-enregistrement', BULK_REGISTER: 'Pré-enregistrement par lots',
    TERMINAL_STATUS: 'Changement de statut', TERMINAL_GROUP: 'Changement de groupe', TERMINAL_DELETED: 'Terminal supprimé',
    PARAMETERS_SAVED: 'Paramètres enregistrés', PARAM_TEMPLATE_APPLIED: 'Modèle de paramètres appliqué',
    GROUP_TEMPLATE: 'Modèle de groupe', FORCE_SYNC: 'Synchronisation forcée'
};

// ------------------------------------------------------------------ utils

function esc(v) {
    return v === null || v === undefined ? '' : String(v)
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function fmtDate(v) {
    if (!v) return '—';
    return new Date(v).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'medium' });
}

function fmtBytes(b) {
    if (b == null) return '—';
    const u = ['o', 'Ko', 'Mo', 'Go', 'To'];
    let i = 0;
    let n = Number(b);
    while (n >= 1024 && i < u.length - 1) { n /= 1024; i++; }
    return (i === 0 ? n : n.toFixed(1)) + ' ' + u[i];
}

function fmtDuration(sec) {
    if (sec == null) return '—';
    const d = Math.floor(sec / 86400), h = Math.floor(sec % 86400 / 3600), m = Math.floor(sec % 3600 / 60);
    return (d ? d + ' j ' : '') + (d || h ? h + ' h ' : '') + m + ' min';
}

function toast(msg, isError = false) {
    const t = $('#toast');
    t.textContent = msg;
    t.className = 'toast show' + (isError ? ' err' : '');
    clearTimeout(t._h);
    t._h = setTimeout(() => { t.className = 'toast'; }, 3500);
}

function options(list, selected, { value = x => x, label = x => x, empty = null } = {}) {
    let html = empty !== null ? `<option value="">${esc(empty)}</option>` : '';
    for (const item of list) {
        const v = value(item);
        html += `<option value="${esc(v)}" ${String(v) === String(selected ?? '') ? 'selected' : ''}>${esc(label(item))}</option>`;
    }
    return html;
}

function formData(form) {
    const data = {};
    for (const [k, v] of new FormData(form).entries()) data[k] = typeof v === 'string' ? v.trim() : v;
    return data;
}

const orNull = v => (v === '' || v === undefined ? null : v);
const numOrNull = v => (v === '' || v === undefined || v === null ? null : Number(v));
const lines = v => (v || '').split(/[\n,;]+/).map(s => s.trim()).filter(Boolean);

function statusBadge(s) {
    const cls = {
        ACTIVE: 'ok', SUCCESS: 'ok', REGISTERED: 'info', PENDING: 'info', SENT: 'info',
        IN_PROGRESS: 'warn', DISABLED: 'danger', FAILED: 'danger', CANCELLED: ''
    }[s] ?? '';
    return `<span class="badge ${cls}">${esc(s)}</span>`;
}

function table(headers, rows, emptyMsg = 'Aucune donnée') {
    if (!rows.length) return `<div class="table-wrap"><div class="empty">${esc(emptyMsg)}</div></div>`;
    return `<div class="table-wrap"><table><thead><tr>${headers.map(h => `<th>${esc(h)}</th>`).join('')}</tr></thead>
        <tbody>${rows.join('')}</tbody></table></div>`;
}

function meter(used, total) {
    if (!total) return '';
    const pct = Math.round(used * 100 / total);
    const cls = pct > 90 ? 'danger' : pct > 75 ? 'warn' : '';
    return `<div class="meter"><span class="${cls}" style="width:${pct}%"></span></div>`;
}

/** Petit graphique SVG multi-séries. series : [{label, cls, values:[{t, v}]}] */
function lineChart(series, { unit = '', max = null } = {}) {
    const all = series.flatMap(s => s.values);
    if (all.length < 2) return '<p class="muted">Pas encore assez de points (un point par heartbeat).</p>';
    const W = 600, H = 120, P = 28;
    const t0 = Math.min(...all.map(p => p.t)), t1 = Math.max(...all.map(p => p.t));
    const vmax = max ?? Math.max(1, ...all.map(p => p.v));
    const x = t => P + (W - P - 6) * (t1 === t0 ? 0 : (t - t0) / (t1 - t0));
    const y = v => H - 16 - (H - 26) * (v / vmax);
    const paths = series.map(s => s.values.length
        ? `<polyline class="line ${s.cls || ''}" points="${s.values.map(p => `${x(p.t).toFixed(1)},${y(p.v).toFixed(1)}`).join(' ')}"/>` : '').join('');
    const hhmm = t => new Date(t).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
    return `<svg class="chart" viewBox="0 0 ${W} ${H}" preserveAspectRatio="none">
        <line class="axis" x1="${P}" y1="${H - 16}" x2="${W}" y2="${H - 16}"/>
        <text x="2" y="12">${esc(Math.round(vmax) + ' ' + unit)}</text><text x="2" y="${H - 16}">0</text>
        <text x="${P}" y="${H - 2}">${hhmm(t0)}</text><text x="${W - 36}" y="${H - 2}">${hhmm(t1)}</text>
        ${paths}</svg>
        <div class="legend">${series.map(s => `<span><i class="${s.cls || ''}"></i>${esc(s.label)}</span>`).join('')}</div>`;
}

// ------------------------------------------------------------------ API

async function api(path, opts = {}) {
    const headers = { Authorization: 'Basic ' + auth, ...(opts.headers || {}) };
    let body = opts.body;
    if (body && !(body instanceof FormData)) {
        headers['Content-Type'] = 'application/json';
        body = JSON.stringify(body);
    }
    const res = await fetch(API + path, { ...opts, headers, body });
    if (res.status === 401) {
        logout();
        throw new Error('Session expirée, reconnectez-vous');
    }
    if (!res.ok) {
        let msg = res.status + ' ' + res.statusText;
        try { msg = (await res.json()).error || msg; } catch (e) { /* corps non JSON */ }
        throw new Error(msg);
    }
    return res.status === 204 ? null : res.json();
}

async function guarded(fn) {
    try { return await fn(); } catch (e) { toast(e.message, true); return undefined; }
}

async function download(path, fallbackName) {
    const res = await fetch(API + path, { headers: { Authorization: 'Basic ' + auth } });
    if (!res.ok) { toast('Téléchargement impossible', true); return; }
    const blob = await res.blob();
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = (res.headers.get('Content-Disposition') || '').split('filename="')[1]?.replace('"', '') || fallbackName;
    link.click();
    URL.revokeObjectURL(link.href);
}

// ------------------------------------------------------------------ auth

function showLogin() {
    $('#app').classList.add('hidden');
    $('#login').classList.remove('hidden');
}

function logout() {
    auth = null;
    try { sessionStorage.removeItem('tms.auth'); } catch (e) { /* ignore */ }
    clearInterval(refreshTimer);
    showLogin();
}

$('#loginForm').addEventListener('submit', async e => {
    e.preventDefault();
    const { username, password } = formData(e.target);
    auth = btoa(unescape(encodeURIComponent(username + ':' + password)));
    try {
        meta = await api('/meta');
        try { sessionStorage.setItem('tms.auth', auth); } catch (err) { /* ignore */ }
        $('#loginError').textContent = '';
        start();
    } catch (err) {
        $('#loginError').textContent = 'Identifiants invalides';
        showLogin();
    }
});

$('#logout').addEventListener('click', logout);

// ------------------------------------------------------------------ navigation & modal

const views = {};

function navigate(view) {
    currentView = view;
    $$('#nav a').forEach(a => a.classList.toggle('active', a.dataset.view === view));
    $('#viewActions').innerHTML = '';
    views[view].render();
}

$$('#nav a').forEach(a => a.addEventListener('click', () => navigate(a.dataset.view)));

function openModal(title, html) {
    openDetail = null; // la fiche terminal le repositionne après son propre openModal
    $('#modalTitle').textContent = title;
    $('#modalBody').innerHTML = html;
    const m = $('#modal');
    if (!m.open) m.showModal();
    return $('#modalBody');
}

function closeModal() { $('#modal').close(); }

$('#modal').addEventListener('click', e => {
    if (e.target.matches('[data-close]') || e.target === $('#modal')) closeModal();
});

function setTitle(title, actionsHtml = '') {
    $('#viewTitle').textContent = title;
    $('#viewActions').innerHTML = actionsHtml;
}

async function start() {
    $('#login').classList.add('hidden');
    $('#app').classList.remove('hidden');
    if (!meta) meta = await guarded(() => api('/meta'));
    if (!meta) return;
    navigate(currentView);
    clearInterval(refreshTimer);
    refreshTimer = setInterval(() => {
        if (!$('#modal').open && ['dashboard', 'terminals', 'tasks'].includes(currentView)) views[currentView].render();
    }, 15000);
}

async function refs() {
    const [groups, merchants, organizations] = await Promise.all([api('/groups'), api('/merchants'), api('/organizations')]);
    return { groups, merchants, organizations };
}

// ------------------------------------------------------------------ Tableau de bord

views.dashboard = {
    async render() {
        setTitle('Tableau de bord');
        const d = await guarded(() => api('/dashboard'));
        if (!d) return;
        const bars = (map) => {
            const entries = Object.entries(map || {});
            if (!entries.length) return '<p class="muted">Aucune donnée</p>';
            const max = Math.max(...entries.map(([, n]) => n));
            return entries.map(([k, n]) => `<div class="bar-row"><span>${esc(k)}</span>
                <div class="bar"><span style="width:${(n / max) * 100}%"></span></div><span class="n">${n}</span></div>`).join('');
        };
        $('#view').innerHTML = `
            <div class="grid kpis">
                <div class="card kpi"><div class="value">${d.terminals}</div><div class="label">Terminaux</div></div>
                <div class="card kpi"><div class="value" style="color:var(--ok)">${d.online}</div><div class="label">En ligne</div></div>
                <div class="card kpi"><div class="value">${d.offline}</div><div class="label">Hors ligne</div></div>
                <div class="card kpi ${d.lowBattery ? 'alert' : ''}"><div class="value">${d.lowBattery}</div><div class="label">Batterie &lt; 20 %</div></div>
                <div class="card kpi ${d.lowStorage ? 'alert' : ''}"><div class="value">${d.lowStorage}</div><div class="label">Stockage &lt; 10 %</div></div>
                <div class="card kpi"><div class="value">${d.deviceOwners}</div><div class="label">Device Owner</div></div>
            </div>
            <div class="grid kpis" style="margin-top:16px">
                <div class="card kpi"><div class="value">${d.organizations}</div><div class="label">Organisations</div></div>
                <div class="card kpi"><div class="value">${d.merchants}</div><div class="label">Marchands</div></div>
                <div class="card kpi"><div class="value">${d.groups}</div><div class="label">Groupes</div></div>
                <div class="card kpi"><div class="value">${d.apps}</div><div class="label">Versions d'APK</div></div>
            </div>
            <div class="grid two" style="margin-top:16px">
                <div class="card"><h3>Parc par constructeur</h3>${bars(d.byManufacturer)}</div>
                <div class="card"><h3>Connexion réseau</h3>${bars(d.byNetworkType)}</div>
                <div class="card"><h3>Parc par statut</h3>${bars(d.byStatus)}</div>
                <div class="card"><h3>Tâches par statut</h3>${bars(d.tasksByStatus)}</div>
            </div>`;
    }
};


// ------------------------------------------------------------------ Notifications (en bas à gauche)

/** kind : ok | err | info. Fermeture automatique (plus longue pour les erreurs) ou par ✕. */
function notify(title, detail = '', kind = 'info') {
    const box = document.createElement('div');
    box.className = 'notif ' + kind;
    box.innerHTML = `<span class="ico">${{ ok: '✅', err: '❌', info: 'ℹ️' }[kind] || 'ℹ️'}</span>
        <div class="txt"><b>${esc(title)}</b>${detail ? `<div>${esc(detail)}</div>` : ''}</div>
        <button class="close" title="Fermer">✕</button>`;
    const close = () => { box.classList.add('leaving'); setTimeout(() => box.remove(), 250); };
    box.querySelector('.close').onclick = close;
    $('#notifs').appendChild(box);
    setTimeout(close, kind === 'err' ? 20000 : 10000);
}

/** Terminal affiché dans la fiche ouverte (pour la rafraîchir quand une tâche se termine). */
let openDetail = null;

function refreshOpenDetail(terminalIds, delayMs = 0) {
    setTimeout(() => {
        if (openDetail && $('#modal').open && terminalIds.map(Number).includes(Number(openDetail.id))) {
            terminalDetail(openDetail.id, openDetail.tab);
        }
    }, delayMs);
}

const FINAL = ['SUCCESS', 'FAILED', 'CANCELLED'];

/** Suit un déploiement jusqu'à la fin de toutes ses tâches, puis affiche une notification. */
function watchDeployment(deploymentId, label) {
    const started = Date.now();
    const tick = async () => {
        let list;
        try { list = await api('/tasks?deploymentId=' + encodeURIComponent(deploymentId)); } catch (e) { list = null; }
        if (list && list.length && list.every(k => FINAL.includes(k.status))) {
            const ko = list.filter(k => k.status !== 'SUCCESS');
            if (list.length === 1) {
                const k = list[0];
                notify(`${label} — ${k.serialNumber}`, k.message || (k.status === 'SUCCESS' ? 'Terminé' : k.status),
                    k.status === 'SUCCESS' ? 'ok' : 'err');
            } else {
                notify(label, `${list.length - ko.length} réussie(s), ${ko.length} en échec sur ${list.length} terminal(aux)`,
                    ko.length ? 'err' : 'ok');
            }
            // L'inventaire arrive au heartbeat qui suit la tâche (≈ 3 s) : léger délai pour les applications
            const inventory = list.some(k => ['INSTALL_APP', 'UNINSTALL_APP'].includes(k.type));
            refreshOpenDetail(list.map(k => k.terminalId), inventory ? 5000 : 0);
            if (currentView === 'tasks') views.tasks.render();
            return;
        }
        // Terminaux hors ligne : on arrête de suivre au bout de 30 min
        if (Date.now() - started > 30 * 60000) {
            notify(label, 'Toujours en cours après 30 min — suivez-la dans « Tâches »', 'info');
            return;
        }
        setTimeout(tick, 3000);
    };
    setTimeout(tick, 1500);
}

/** Crée un déploiement et le suit (sauf s'il est planifié) ; renvoie la réponse ou undefined. */
async function deployAndWatch(req, label) {
    const res = await guarded(() => api('/deployments', { method: 'POST', body: req }));
    if (res && !req.schedule) {
        watchDeployment(res.deploymentId, label || TASK_LABELS[req.type] || req.type);
    }
    return res;
}

/** Synchronisation forcée : instantanée si le terminal a son canal temps réel ouvert. */
async function forceSync(t) {
    const before = t.lastSeenAt;
    const res = await guarded(() => api(`/terminals/${t.id}/sync`, { method: 'POST' }));
    if (!res) return;
    if (!res.delivered) {
        notify(`Synchronisation — ${t.serialNumber}`,
            `Terminal non joignable en temps réel : il se synchronisera à son prochain contact (≤ ${meta.pollIntervalSeconds} s).`, 'info');
        return;
    }
    toast('Synchronisation demandée');
    const started = Date.now();
    const tick = async () => {
        const cur = await api('/terminals/' + t.id).catch(() => null);
        if (cur && cur.lastSeenAt && cur.lastSeenAt !== before) {
            notify(`Terminal synchronisé — ${t.serialNumber}`, `Dernier contact ${fmtDate(cur.lastSeenAt)}`, 'ok');
            refreshOpenDetail([t.id]);
            if (currentView === 'terminals' && !$('#modal').open) views.terminals.render();
            return;
        }
        if (Date.now() - started > 60000) {
            notify(`Synchronisation — ${t.serialNumber}`, 'Pas de réponse du terminal après 60 s', 'err');
            return;
        }
        setTimeout(tick, 2000);
    };
    setTimeout(tick, 1500);
}

// ------------------------------------------------------------------ Icônes

const BRAND_COLORS = { NEWLAND: '#0b6fbf', PAX: '#d71e28', SUNMI: '#f08c00', OTHER: '#6a7280' };

/** Pictogramme de TPE (écran + clavier) aux couleurs du constructeur, pastille verte si en ligne. */
function terminalIcon(t) {
    const c = BRAND_COLORS[t.manufacturer] || BRAND_COLORS.OTHER;
    const keys = [0, 1, 2].map(r => [0, 1, 2].map(col =>
        `<rect x="${5 + col * 6}" y="${19 + r * 4.3}" width="4" height="2.8" rx=".8" fill="#fff" opacity=".75"/>`).join('')).join('');
    return `<svg class="term-ico ${t.online ? '' : 'off'}" viewBox="0 0 26 34">
        <title>${esc(t.manufacturer)} ${esc(t.model || '')} — ${t.online ? 'en ligne' : 'hors ligne'}</title>
        <rect x="1" y="1" width="24" height="32" rx="4" fill="${c}"/>
        <rect x="4" y="4" width="18" height="12" rx="1.5" fill="#fff" opacity=".92"/>${keys}
        ${t.online ? '<circle cx="21.5" cy="4.5" r="3" fill="#1a9d57" stroke="#fff" stroke-width="1"/>' : ''}
    </svg>`;
}

/** Icône d'application : pastille à initiale, remplacée par la vraie icône dès qu'elle est chargée. */
function appIcon(packageName, label) {
    let h = 0;
    for (const ch of packageName || '') h = (h * 31 + ch.charCodeAt(0)) >>> 0;
    const letter = ((label || '').trim() || (packageName || '?').split('.').pop() || '?').charAt(0).toUpperCase();
    return `<span class="app-icon" data-icon-pkg="${esc(packageName)}" style="background:hsl(${h % 360},55%,48%)">${esc(letter)}</span>`;
}

const iconCache = new Map(); // package → { url, at, promise }

function iconUrl(pkg) {
    const c = iconCache.get(pkg);
    // Icône absente : nouvel essai après 1 min (l'agent l'envoie à son prochain heartbeat)
    if (c && (c.url || c.promise || Date.now() - c.at < 60000)) return c.promise || Promise.resolve(c.url);
    const promise = fetch(`${API}/apps/icons/${encodeURIComponent(pkg)}`, { headers: { Authorization: 'Basic ' + auth } })
        .then(r => (r.ok ? r.blob() : null))
        .then(b => (b ? URL.createObjectURL(b) : null))
        .catch(() => null)
        .then(url => { iconCache.set(pkg, { url, at: Date.now() }); return url; });
    iconCache.set(pkg, { url: null, at: Date.now(), promise });
    return promise;
}

function loadIcons(root) {
    $$('[data-icon-pkg]:not([data-icon-done])', root).forEach(async el => {
        el.dataset.iconDone = '1';
        const url = await iconUrl(el.dataset.iconPkg);
        if (url) {
            el.classList.add('has-img');
            el.innerHTML = `<img src="${url}" alt="">`;
        }
    });
}

// Toute icône insérée dans la page (vues, fiches, modales) est chargée automatiquement
new MutationObserver(() => { if (auth) loadIcons(document); }).observe(document.body, { childList: true, subtree: true });

// ------------------------------------------------------------------ Terminaux

const terminalFilters = { manufacturer: '', status: '', organizationId: '', groupId: '', merchantId: '', q: '' };

views.terminals = {
    async render() {
        setTitle('Terminaux', `<div class="actions">
            <button class="btn" id="bulkDeploy">Déployer…</button>
            <button class="btn" id="bulkRegister">Pré-enregistrer par lots</button>
            <button class="btn primary" id="addTerminal">+ Pré-enregistrer</button></div>`);
        $('#addTerminal').onclick = () => terminalCreateModal();
        $('#bulkRegister').onclick = () => bulkRegisterModal();
        $('#bulkDeploy').onclick = () => deployModal({});

        const r = await guarded(refs);
        if (!r) return;
        const qs = new URLSearchParams(Object.entries(terminalFilters).filter(([, v]) => v)).toString();
        const list = await guarded(() => api('/terminals' + (qs ? '?' + qs : '')));
        if (!list) return;

        const rows = list.map(t => `<tr class="clickable" data-id="${t.id}">
            <td class="ico-cell">${terminalIcon(t)}</td>
            <td class="mono">${esc(t.serialNumber)}</td>
            <td><span class="badge">${esc(t.manufacturer)}</span></td>
            <td>${esc(t.model)}</td>
            <td>${esc(t.organizationName)}</td>
            <td>${esc(t.merchantName)}</td>
            <td>${esc(t.groupName)}</td>
            <td>${statusBadge(t.status)}</td>
            <td><span class="dot ${t.online ? 'on' : ''}"></span>${t.online ? 'En ligne' : 'Hors ligne'}${realtimeMark(t)}</td>
            <td>${esc(t.networkType) || '—'}</td>
            <td>${t.batteryLevel != null ? t.batteryLevel + ' %' : '—'}</td>
            <td>${t.deviceOwner ? '<span class="badge ok">DO</span>' : ''}</td>
            <td>${fmtDate(t.lastSeenAt)}</td>
            <td><button class="btn sm" data-sync="${t.id}" title="Forcer la synchronisation">⟳</button></td></tr>`);

        $('#view').innerHTML = `
            <form class="filters" id="termFilters">
                <input name="q" placeholder="N° série, TID, modèle…" value="${esc(terminalFilters.q)}">
                <select name="manufacturer">${options(meta.manufacturers, terminalFilters.manufacturer, { empty: 'Tous constructeurs' })}</select>
                <select name="status">${options(meta.terminalStatuses, terminalFilters.status, { empty: 'Tous statuts' })}</select>
                <select name="organizationId">${options(r.organizations, terminalFilters.organizationId, { value: o => o.id, label: o => o.name, empty: 'Toutes organisations' })}</select>
                <select name="groupId">${options(r.groups, terminalFilters.groupId, { value: g => g.id, label: g => g.name, empty: 'Tous groupes' })}</select>
                <select name="merchantId">${options(r.merchants, terminalFilters.merchantId, { value: m => m.id, label: m => m.name, empty: 'Tous marchands' })}</select>
            </form>
            <p class="muted">${list.length} terminal(aux)</p>
            ${table(['', 'N° série', 'Constructeur', 'Modèle', 'Organisation', 'Marchand', 'Groupe', 'Statut', 'Connexion', 'Réseau', 'Batterie', '', 'Dernier contact', ''], rows, 'Aucun terminal. Installez TMS2M Agent ou pré-enregistrez un terminal.')}`;

        const f = $('#termFilters');
        f.addEventListener('change', () => { Object.assign(terminalFilters, formData(f)); this.render(); });
        f.addEventListener('submit', e => { e.preventDefault(); Object.assign(terminalFilters, formData(f)); this.render(); });
        $$('tr[data-id]').forEach(tr => tr.addEventListener('click', () => terminalDetail(tr.dataset.id)));
        $$('[data-sync]').forEach(b => b.addEventListener('click', e => {
            e.stopPropagation(); // ne pas ouvrir la fiche
            forceSync(list.find(t => String(t.id) === b.dataset.sync));
        }));
    }
};

function realtimeMark(t) {
    return t.realtime ? '<span class="rt" title="Canal temps réel ouvert : synchronisation et tâches instantanées">⚡</span>' : '';
}

async function terminalCreateModal() {
    const r = await guarded(refs);
    if (!r) return;
    const body = openModal('Pré-enregistrer un terminal', `
        <form class="form" id="createTerm">
            <div class="form-row">
                <label>N° de série *<input name="serialNumber" required></label>
                <label>Constructeur<select name="manufacturer">${options(meta.manufacturers, 'OTHER')}</select></label>
                <label>Modèle<input name="model" placeholder="A920, N950S, P3…"></label>
            </div>
            <div class="form-row">
                <label>TID<input name="tid"></label>
                <label>Marchand<select name="merchantId">${options(r.merchants, '', { value: m => m.id, label: m => m.name, empty: '—' })}</select></label>
                <label>Groupe<select name="groupId">${options(r.groups, '', { value: g => g.id, label: g => g.name + (g.templateName ? ' (modèle : ' + g.templateName + ')' : ''), empty: '—' })}</select></label>
            </div>
            <p class="muted">Si le groupe a un modèle de déploiement, le terminal recevra automatiquement ses applications et réglages à l'enrôlement (zéro contact).</p>
            <div class="actions"><button class="btn primary">Enregistrer</button></div>
        </form>`);
    $('#createTerm', body).addEventListener('submit', async e => {
        e.preventDefault();
        const d = formData(e.target);
        const ok = await guarded(() => api('/terminals', {
            method: 'POST', body: {
                serialNumber: d.serialNumber, manufacturer: d.manufacturer, model: orNull(d.model),
                tid: orNull(d.tid), merchantId: numOrNull(d.merchantId), groupId: numOrNull(d.groupId)
            }
        }));
        if (ok) { closeModal(); toast('Terminal pré-enregistré'); views.terminals.render(); }
    });
}

async function bulkRegisterModal() {
    const r = await guarded(refs);
    if (!r) return;
    const body = openModal('Pré-enregistrement par lots', `
        <form class="form" id="bulkForm">
            <label>N° de série * (un par ligne, ou séparés par des virgules)
                <textarea name="serialNumbers" required placeholder="NEB900008006&#10;1170009928&#10;P365P54QJ0423"></textarea></label>
            <div class="form-row">
                <label>Constructeur<select name="manufacturer">${options(meta.manufacturers, 'OTHER')}</select></label>
                <label>Modèle<input name="model"></label>
                <label>Marchand<select name="merchantId">${options(r.merchants, '', { value: m => m.id, label: m => m.name, empty: '—' })}</select></label>
                <label>Groupe<select name="groupId">${options(r.groups, '', { value: g => g.id, label: g => g.name + (g.templateName ? ' (modèle : ' + g.templateName + ')' : ''), empty: '—' })}</select></label>
            </div>
            <div class="actions"><button class="btn primary">Pré-enregistrer</button></div>
        </form>`);
    $('#bulkForm', body).addEventListener('submit', async e => {
        e.preventDefault();
        const d = formData(e.target);
        const res = await guarded(() => api('/terminals/bulk', {
            method: 'POST', body: {
                serialNumbers: d.serialNumbers, manufacturer: d.manufacturer, model: orNull(d.model),
                merchantId: numOrNull(d.merchantId), groupId: numOrNull(d.groupId)
            }
        }));
        if (res) {
            closeModal();
            toast(`${res.created} terminal(aux) créé(s)` + (res.skipped.length ? `, ${res.skipped.length} déjà existant(s)` : ''));
            views.terminals.render();
        }
    });
}

async function terminalDetail(id, tab = 'supervision') {
    const [t, tasks, r, apps, metrics, history] = await Promise.all([
        api('/terminals/' + id), api('/tasks?terminalId=' + id), refs(), api('/apps'),
        api(`/terminals/${id}/metrics?hours=24`), api(`/terminals/${id}/history`)
    ]).catch(e => { toast(e.message, true); return []; });
    if (!t) return;

    const storageUsed = t.storageTotalBytes != null ? t.storageTotalBytes - t.storageFreeBytes : null;
    const ramUsed = t.ramTotalBytes != null ? t.ramTotalBytes - t.ramAvailBytes : null;
    const pts = metrics.map(m => ({ ...m, t: new Date(m.recordedAt).getTime() }));
    const traffic = { rx: [], tx: [] };
    for (let i = 1; i < pts.length; i++) {
        const drx = pts[i].rxBytes - pts[i - 1].rxBytes, dtx = pts[i].txBytes - pts[i - 1].txBytes;
        if (pts[i].rxBytes != null && pts[i - 1].rxBytes != null && drx >= 0) traffic.rx.push({ t: pts[i].t, v: drx / 1024 });
        if (pts[i].txBytes != null && pts[i - 1].txBytes != null && dtx >= 0) traffic.tx.push({ t: pts[i].t, v: dtx / 1024 });
    }

    const supervision = `
        <div class="kv">
            <div><span>Modèle</span>${esc(t.model) || '—'}</div>
            <div><span>Statut</span>${statusBadge(t.status)}</div>
            <div><span>Connexion</span><span class="dot ${t.online ? 'on' : ''}"></span>${t.online ? 'En ligne' : 'Hors ligne'}${realtimeMark(t)} · ${esc(t.networkType) || '—'}</div>
            <div><span>Dernier contact</span>${fmtDate(t.lastSeenAt)}</div>
            <div><span>Android / firmware</span>${esc(t.osVersion) || '—'} · ${esc(t.firmwareVersion) || '—'}</div>
            <div><span>Agent</span>${esc(t.agentVersion) || '—'}</div>
            <div><span>Device Owner</span>${t.deviceOwner ? '✅ oui' : 'non'}</div>
            <div><span>Batterie</span>${t.batteryLevel != null ? t.batteryLevel + ' %' : '—'}</div>
            <div><span>Stockage</span>${fmtBytes(storageUsed)} / ${fmtBytes(t.storageTotalBytes)}${meter(storageUsed, t.storageTotalBytes)}</div>
            <div><span>Mémoire</span>${fmtBytes(ramUsed)} / ${fmtBytes(t.ramTotalBytes)}${meter(ramUsed, t.ramTotalBytes)}</div>
            <div><span>Allumé depuis</span>${fmtDuration(t.uptimeSeconds)}</div>
            <div><span>IP</span>${esc(t.ipAddress) || '—'}</div>
            <div><span>Position</span>${t.latitude != null
                ? `<a href="https://www.openstreetmap.org/?mlat=${t.latitude}&mlon=${t.longitude}#map=16/${t.latitude}/${t.longitude}" target="_blank" rel="noopener">${t.latitude.toFixed(5)}, ${t.longitude.toFixed(5)}</a><br><span class="muted">${fmtDate(t.locationAt)}</span>`
                : '—'}</div>
            <div><span>Démarrage auto</span>${esc(t.autoRunPackage) || '—'}</div>
            <div><span>Kiosque</span>${t.kioskPackages && t.kioskPackages.length ? esc(t.kioskPackages.join(', ')) : '—'}</div>
            <div><span>Organisation</span>${esc(t.organizationName) || '—'}</div>
        </div>
        <div class="grid two">
            <div class="card"><h3>Batterie (24 h)</h3>${lineChart([{ label: 'Batterie %', values: pts.filter(p => p.batteryLevel != null).map(p => ({ t: p.t, v: p.batteryLevel })) }], { unit: '%', max: 100 })}</div>
            <div class="card"><h3>Trafic réseau par intervalle (24 h)</h3>${lineChart([{ label: 'Reçu (Ko)', values: traffic.rx }, { label: 'Envoyé (Ko)', cls: 'alt', values: traffic.tx }], { unit: 'Ko' })}</div>
            <div class="card"><h3>Stockage libre et mémoire disponible (24 h)</h3>${lineChart([
                { label: 'Stockage libre (Mo)', values: pts.filter(p => p.storageFreeBytes != null).map(p => ({ t: p.t, v: p.storageFreeBytes / 1048576 })) },
                { label: 'RAM disponible (Mo)', cls: 'alt', values: pts.filter(p => p.ramAvailBytes != null).map(p => ({ t: p.t, v: p.ramAvailBytes / 1048576 })) }], { unit: 'Mo' })}</div>
        </div>`;

    const labels = new Map(apps.map(a => [a.packageName, a.label]));
    const appRows = (t.installedApps || []).slice().sort((a, b) => a.packageName.localeCompare(b.packageName)).map(a => `<tr>
        <td class="ico-cell">${appIcon(a.packageName, labels.get(a.packageName))}</td>
        <td>${esc(labels.get(a.packageName) || '')}<div class="mono muted">${esc(a.packageName)}</div></td>
        <td>${esc(a.versionName)}</td><td>${a.versionCode}</td>
        <td>${a.packageName === AGENT_PACKAGE ? '<span class="muted">agent TMS</span>'
            : `<button class="btn sm danger" data-uninstall="${esc(a.packageName)}">Désinstaller</button>`}</td></tr>`);
    const taskRows = tasks.map(k => `<tr><td>${k.id}</td><td>${esc(TASK_LABELS[k.type] || k.type)}</td><td class="mono">${esc(k.payload.packageName || k.payload.path || '')}</td>
        <td>${statusBadge(k.status)}</td><td>${esc(k.message)}</td><td>${fmtDate(k.updatedAt)}</td><td>${taskResultButtons(k)}</td></tr>`);
    const histRows = history.map(h => `<tr><td>${fmtDate(h.occurredAt)}</td><td>${esc(AUDIT_LABELS[h.action] || h.action)}</td><td>${esc(h.actor)}</td><td>${esc(h.details)}</td></tr>`);

    const body = openModal(`${t.manufacturer} · ${t.serialNumber}`, `
        <div class="actions">
            <button class="btn primary" id="actSync" title="Le terminal envoie immédiatement son état et récupère ses tâches">⟳ Synchroniser</button>
            <button class="btn" data-act="REBOOT">Redémarrer</button>
            <button class="btn" data-act="DIAGNOSE">Diagnostic</button>
            <button class="btn" data-act="EXTRACT_LOGS">Logs</button>
            <button class="btn" data-act="EXTRACT_FILE">Extraire un fichier…</button>
            <button class="btn" data-act="INSTALL_APP">Installer…</button>
            <button class="btn" data-act="PUSH_PARAMS">Paramètres…</button>
            <button class="btn" data-act="SET_KIOSK">Kiosque…</button>
            <button class="btn" data-act="SET_AUTORUN">Démarrage auto…</button>
        </div>
        <div class="tabs">
            ${[['supervision', 'Supervision'], ['config', 'Affectation'], ['apps', `Applications (${appRows.length})`], ['tasks', 'Tâches'], ['history', 'Historique']]
                .map(([k, l]) => `<button data-tab="${k}" class="${k === tab ? 'active' : ''}">${l}</button>`).join('')}
        </div>
        <div data-pane="supervision">${supervision}</div>
        <div data-pane="config">
            <form class="form" id="editTerm">
                <div class="form-row">
                    <label>TID<input name="tid" value="${esc(t.tid)}"></label>
                    <label>Marchand<select name="merchantId">${options(r.merchants, t.merchantId, { value: m => m.id, label: m => m.name + (m.organizationName ? ' — ' + m.organizationName : ''), empty: '—' })}</select></label>
                    <label>Groupe<select name="groupId">${options(r.groups, t.groupId, { value: g => g.id, label: g => g.name + (g.templateName ? ' (modèle : ' + g.templateName + ')' : ''), empty: '—' })}</select></label>
                    <label>Statut<select name="status">${options(meta.terminalStatuses, t.status)}</select></label>
                </div>
                <p class="muted">Changer de groupe applique automatiquement le modèle de déploiement du nouveau groupe.</p>
                <div class="actions">
                    <button class="btn primary">Enregistrer</button>
                    <button class="btn danger" type="button" id="actDelete">Supprimer le terminal</button>
                </div>
            </form>
        </div>
        <div data-pane="apps">${table(['', 'Application', 'Version', 'versionCode', ''], appRows, 'Inventaire non encore remonté')}</div>
        <div data-pane="tasks">${table(['#', 'Action', 'Cible', 'Statut', 'Message', 'Mis à jour', ''], taskRows, 'Aucune tâche')}</div>
        <div data-pane="history">${table(['Date', 'Événement', 'Par', 'Détails'], histRows, 'Aucun événement')}</div>`);

    const showTab = k => {
        openDetail = { id: t.id, tab: k };
        $$('[data-tab]', body).forEach(b => b.classList.toggle('active', b.dataset.tab === k));
        $$('[data-pane]', body).forEach(p => p.classList.toggle('hidden', p.dataset.pane !== k));
    };
    $$('[data-tab]', body).forEach(b => b.onclick = () => showTab(b.dataset.tab));
    showTab(tab);
    bindTaskResultButtons(body);

    $('#editTerm', body).addEventListener('submit', async e => {
        e.preventDefault();
        const d = formData(e.target);
        const ok = await guarded(() => api('/terminals/' + id, {
            method: 'PUT', body: { tid: orNull(d.tid), merchantId: numOrNull(d.merchantId), groupId: numOrNull(d.groupId), status: d.status }
        }));
        if (ok) { toast('Terminal mis à jour'); terminalDetail(id, 'config'); }
    });
    $$('[data-act]', body).forEach(b => b.onclick = () => {
        const type = b.dataset.act;
        if (type === 'REBOOT' || type === 'DIAGNOSE') {
            quickDeploy({ type }, id);
        } else if (type === 'EXTRACT_LOGS') {
            logsModal(t);
        } else {
            deployModal({ type, terminalIds: [Number(id)], apps });
        }
    });
    $('#actSync', body).onclick = () => forceSync(t);
    $$('[data-uninstall]', body).forEach(b => b.onclick = async () => {
        const pkg = b.dataset.uninstall;
        const name = labels.get(pkg) || pkg;
        if (!confirm(`Désinstaller ${name} (${pkg}) du terminal ${t.serialNumber} ?`
            + (t.deviceOwner ? '' : '\n\nSans Device Owner, la désinstallation doit être confirmée sur l\'écran du terminal.'))) return;
        b.disabled = true;
        const res = await deployAndWatch({ type: 'UNINSTALL_APP', packageName: pkg, target: { terminalIds: [t.id] } },
            `Désinstallation de ${name}`);
        if (res) toast('Désinstallation demandée'); else b.disabled = false;
    });
    $('#actDelete', body).onclick = async () => {
        if (!confirm(`Supprimer définitivement le terminal ${t.serialNumber} et son historique ?`)) return;
        const ok = await guarded(() => api('/terminals/' + id, { method: 'DELETE' }));
        if (ok !== undefined) { closeModal(); toast('Terminal supprimé'); views.terminals.render(); }
    };
}

const SINCE_OPTIONS = [['5', '5 dernières minutes'], ['15', '15 dernières minutes'], ['30', '30 dernières minutes'],
    ['60', 'Dernière heure'], ['', 'Tout le journal']];

/** Extraction de logs d'un terminal, par application (liste des applis installées) et par période. */
function logsModal(t) {
    const apps = (t.installedApps || []).slice().sort((a, b) => a.packageName.localeCompare(b.packageName));
    const body = openModal(`Extraire les logs — ${t.serialNumber}`, `
        <form class="form" id="logsForm">
            <div class="form-row">
                <label>Application<select name="packageName">
                    <option value="">Toutes les applications</option>
                    ${apps.map(a => `<option value="${esc(a.packageName)}">${esc(a.packageName)} ${esc(a.versionName || '')}</option>`).join('')}
                </select></label>
                <label>Période<select name="since">${SINCE_OPTIONS.map(([v, l]) => `<option value="${v}" ${v === '15' ? 'selected' : ''}>${l}</option>`).join('')}
                    <option value="range">Plage personnalisée…</option></select></label>
                <label>Nombre de lignes max<input name="logLines" type="number" value="2000" min="100" max="200000"></label>
            </div>
            <div class="form-row hidden" id="logRange">
                <label>Du<input type="datetime-local" name="from" step="1"></label>
                <label>Au<input type="datetime-local" name="to" step="1"></label>
            </div>
            <p class="muted hidden" id="logRangeHelp">Heures de votre navigateur, converties automatiquement dans le fuseau du terminal.
                Le fichier (compressé en .gz) signale si le début de la plage n'est plus dans le journal du terminal.</p>
            <p class="muted">Choisir une application ne garde que ses logs (le bruit du système — GPS, modem… — est écarté) et parcourt tout le journal.
                Le journal Android est circulaire : extrayez les logs peu de temps après l'événement à analyser.</p>
            <p class="muted">Les logs des autres applications exigent la permission READ_LOGS sur le terminal
                (<span class="mono">adb shell pm grant com.tms.agent android.permission.READ_LOGS</span>).</p>
            <div class="actions"><button class="btn primary">Extraire</button></div>
        </form>`);
    const f = $('#logsForm', body);
    // Une application ou une plage choisie : 20 000 lignes par défaut (modifiable jusqu'à 200 000)
    const adjustLines = () => { f.logLines.value = f.packageName.value || f.since.value === 'range' ? 20000 : 2000; };
    f.packageName.addEventListener('change', adjustLines);
    f.since.addEventListener('change', () => {
        const range = f.since.value === 'range';
        $('#logRange', f).classList.toggle('hidden', !range);
        $('#logRangeHelp', f).classList.toggle('hidden', !range);
        if (range && !f.from.value) {
            // Pré-remplissage : l'heure écoulée
            const local = d => new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 19);
            f.to.value = local(new Date());
            f.from.value = local(new Date(Date.now() - 3600000));
        }
        adjustLines();
    });
    f.addEventListener('submit', async e => {
        e.preventDefault();
        const d = formData(f);
        const range = d.since === 'range';
        if (range && !d.from) { toast('Indiquez le début de la plage', true); return; }
        const res = await deployAndWatch({
            type: 'EXTRACT_LOGS', packageName: orNull(d.packageName), logLines: numOrNull(d.logLines),
            logSinceMinutes: range ? null : numOrNull(d.since),
            logFrom: range ? new Date(d.from).toISOString() : null,
            logTo: range && d.to ? new Date(d.to).toISOString() : null,
            target: { terminalIds: [t.id] }
        }, 'Extraction des logs');
        if (res) { toast('Extraction des logs demandée'); terminalDetail(t.id, 'tasks'); }
    });
}

async function quickDeploy(req, terminalId) {
    const res = await deployAndWatch({ ...req, target: { terminalIds: [Number(terminalId)] } });
    if (res) { toast(`${TASK_LABELS[req.type]} : tâche créée`); terminalDetail(terminalId, 'tasks'); }
}

const taskCache = new Map();

function taskResultButtons(k) {
    taskCache.set(String(k.id), k);
    let html = '';
    if (k.result) html += `<button class="btn sm" data-result="${k.id}">Rapport</button> `;
    if (k.artifactName) html += `<button class="btn sm" data-artifact="${k.id}" data-name="${esc(k.artifactName)}">Télécharger</button>`;
    if (['PENDING', 'SENT'].includes(k.status)) html += ` <button class="btn sm danger" data-cancel="${k.id}">Annuler</button>`;
    return html;
}

function bindTaskResultButtons(root, onChange) {
    $$('[data-result]', root).forEach(b => b.onclick = () => {
        const task = taskCache.get(b.dataset.result);
        const rows = task && task.result ? Object.entries(task.result).map(([k, v]) =>
            `<tr><td class="mono">${esc(k)}</td><td>${esc(v !== null && typeof v === 'object' ? JSON.stringify(v) : v)}</td></tr>`) : [];
        openModal(`Rapport de la tâche #${b.dataset.result} — ${esc(task?.serialNumber || '')}`,
            table(['Indicateur', 'Valeur'], rows, 'Rapport vide'));
    });
    $$('[data-artifact]', root).forEach(b => b.onclick = () => download(`/tasks/${b.dataset.artifact}/artifact`, b.dataset.name));
    $$('[data-cancel]', root).forEach(b => b.onclick = async () => {
        const ok = await guarded(() => api(`/tasks/${b.dataset.cancel}/cancel`, { method: 'POST' }));
        if (ok) { toast('Tâche annulée'); if (onChange) onChange(); }
    });
}

// ------------------------------------------------------------------ Déploiement (modal générique)

async function deployModal({ type = 'INSTALL_APP', terminalIds = null, appId = null, apps = null }) {
    const [r, appList] = await Promise.all([refs(), apps ? apps : api('/apps')]);
    const fixedTargets = terminalIds && terminalIds.length;
    const body = openModal('Nouveau déploiement', `
        <form class="form" id="deployForm">
            <div class="form-row">
                <label>Action<select name="type">${options(meta.taskTypes, type, { label: t => TASK_LABELS[t] || t })}</select></label>
                <label data-for="INSTALL_APP">Application<select name="appId">${options(appList, appId,
                    { value: a => a.id, label: a => `${a.label || a.packageName} ${a.versionName || ''} (${a.versionCode})` })}</select></label>
                <label data-for="UNINSTALL_APP PUSH_PARAMS SET_AUTORUN EXTRACT_LOGS">Package<input name="packageName" placeholder="com.acme.payment"></label>
                <label data-for="EXTRACT_LOGS">Nombre de lignes<input name="logLines" type="number" value="2000" min="100" max="200000"></label>
                <label data-for="EXTRACT_LOGS">Période<select name="logSince">${SINCE_OPTIONS.map(([v, l]) => `<option value="${v}" ${v === '15' ? 'selected' : ''}>${l}</option>`).join('')}</select></label>
                <label data-for="EXTRACT_FILE">Chemin du fichier sur le terminal<input name="filePath" placeholder="/sdcard/Download/journal.txt"></label>
            </div>
            <label data-for="SET_KIOSK">Applications autorisées en kiosque (une par ligne ; vide = désactiver le kiosque)
                <textarea name="kioskPackages" placeholder="com.acme.payment"></textarea></label>
            <p class="muted" data-for="SET_AUTORUN">Laisser vide pour désactiver le démarrage automatique.</p>
            <p class="muted" data-for="SET_KIOSK REBOOT">Nécessite TMS2M Agent en Device Owner (ou le SDK du constructeur pour le redémarrage).</p>
            <p class="muted" data-for="EXTRACT_LOGS">Package facultatif : ne garde que les logs de cette application (ex. ma.s2m.pos.neompay).
                Les logs des autres applications exigent la permission READ_LOGS sur le terminal
                (<span class="mono">adb shell pm grant com.tms.agent android.permission.READ_LOGS</span>) ; sinon seuls ceux de l'agent sont remontés.</p>
            ${fixedTargets ? `<p class="muted">Cible : ${terminalIds.length} terminal(aux) sélectionné(s)</p>` : `
            <h4>Cible (filtres combinés)</h4>
            <div class="form-row">
                <label>Organisation<select name="organizationId">${options(r.organizations, '', { value: o => o.id, label: o => o.name, empty: 'Toutes' })}</select></label>
                <label>Constructeur<select name="manufacturer">${options(meta.manufacturers, '', { empty: 'Tous' })}</select></label>
                <label>Groupe<select name="groupId">${options(r.groups, '', { value: g => g.id, label: g => g.name, empty: 'Tous' })}</select></label>
                <label>Marchand<select name="merchantId">${options(r.merchants, '', { value: m => m.id, label: m => m.name, empty: 'Tous' })}</select></label>
            </div>`}
            <label style="display:flex;gap:8px;align-items:center"><input type="checkbox" name="scheduled" style="width:auto"> Planifier (mise à jour planifiée)</label>
            <div class="form-row hidden" id="schedRow">
                <label>Pas avant le<input type="datetime-local" name="notBefore"></label>
                <label>Fenêtre : de<input type="time" name="windowStart" placeholder="22:00"></label>
                <label>à<input type="time" name="windowEnd" placeholder="06:00"></label>
            </div>
            <p class="muted hidden" id="schedHelp">Les tâches ne sont délivrées qu'à partir de la date indiquée et/ou dans la fenêtre quotidienne (heure du serveur : ${esc(meta.serverZone)}). Une fenêtre de nuit (ex. 22:00 → 06:00) est possible.</p>
            <div class="actions"><button class="btn primary">Lancer le déploiement</button></div>
        </form>`);

    const form = $('#deployForm', body);
    const sync = () => {
        const t = form.type.value;
        $$('[data-for]', form).forEach(el => el.classList.toggle('hidden', !el.dataset.for.split(' ').includes(t)));
    };
    form.type.addEventListener('change', sync);
    sync();
    form.scheduled.addEventListener('change', () => {
        $('#schedRow', form).classList.toggle('hidden', !form.scheduled.checked);
        $('#schedHelp', form).classList.toggle('hidden', !form.scheduled.checked);
    });

    form.addEventListener('submit', async e => {
        e.preventDefault();
        const d = formData(form);
        const target = fixedTargets ? { terminalIds } : {
            organizationId: numOrNull(d.organizationId), manufacturer: orNull(d.manufacturer),
            groupId: numOrNull(d.groupId), merchantId: numOrNull(d.merchantId)
        };
        if (!fixedTargets && !target.organizationId && !target.manufacturer && !target.groupId && !target.merchantId) {
            if (!confirm('Aucun filtre : cibler TOUT le parc ?')) return;
            target.all = true;
        }
        const schedule = form.scheduled.checked ? {
            notBefore: d.notBefore ? new Date(d.notBefore).toISOString() : null,
            windowStart: orNull(d.windowStart), windowEnd: orNull(d.windowEnd)
        } : null;
        const req = {
            type: d.type, appId: d.type === 'INSTALL_APP' ? numOrNull(d.appId) : null, packageName: orNull(d.packageName),
            kioskPackages: lines(d.kioskPackages), filePath: orNull(d.filePath), logLines: numOrNull(d.logLines),
            logSinceMinutes: d.type === 'EXTRACT_LOGS' ? numOrNull(d.logSince) : null,
            target, schedule
        };
        const res = await deployAndWatch(req);
        if (res) {
            closeModal();
            toast(`${res.taskCount} tâche(s) créée(s)` + (schedule ? ' (planifiées)' : ''));
            if (currentView === 'tasks') views.tasks.render();
        }
    });
}

// ------------------------------------------------------------------ Applications

views.apps = {
    async render() {
        setTitle('Applications');
        const apps = await guarded(() => api('/apps'));
        if (!apps) return;
        const rows = apps.map(a => `<tr>
            <td class="ico-cell">${appIcon(a.packageName, a.label)}</td><td>${esc(a.label)}</td><td class="mono">${esc(a.packageName)}</td><td>${esc(a.versionName)}</td><td>${a.versionCode}</td>
            <td>${fmtBytes(a.sizeBytes)}</td><td class="mono" title="${esc(a.sha256)}">${esc(a.sha256.substring(0, 12))}…</td>
            <td>${fmtDate(a.uploadedAt)}</td>
            <td class="actions"><button class="btn sm" data-deploy="${a.id}">Déployer</button>
                <button class="btn sm" data-dl="${a.id}">Télécharger</button>
                <button class="btn sm danger" data-del="${a.id}">Supprimer</button></td></tr>`);

        $('#view').innerHTML = `
            <div class="card" style="margin-bottom:16px">
                <h3>Publier un APK</h3>
                <form class="form" id="uploadForm">
                    <div class="form-row">
                        <label>Fichier APK *<input type="file" name="file" accept=".apk" required></label>
                        <label>Description<input name="description"></label>
                    </div>
                    <p class="muted">packageName et version sont lus dans le manifeste de l'APK, qui fait toujours foi.
                        Les champs ci-dessous ne servent que si l'APK est illisible.</p>
                    <div class="form-row">
                        <label>packageName<input name="packageName"></label>
                        <label>versionName<input name="versionName"></label>
                        <label>versionCode<input name="versionCode" type="number"></label>
                    </div>
                    <div class="actions"><button class="btn primary">Publier</button></div>
                </form>
            </div>
            ${table(['', 'Nom', 'Package', 'Version', 'Code', 'Taille', 'SHA-256', 'Publié le', ''], rows, 'Aucun APK publié')}`;

        $('#uploadForm').addEventListener('submit', async e => {
            e.preventDefault();
            const fd = new FormData(e.target);
            for (const k of ['packageName', 'versionName', 'versionCode', 'description']) if (!fd.get(k)) fd.delete(k);
            const res = await guarded(() => api('/apps', { method: 'POST', body: fd }));
            if (res) { toast(`${res.packageName} ${res.versionName || ''} publié`); this.render(); }
        });
        $$('[data-deploy]').forEach(b => b.onclick = () => deployModal({ type: 'INSTALL_APP', appId: b.dataset.deploy, apps }));
        $$('[data-del]').forEach(b => b.onclick = async () => {
            if (!confirm('Supprimer cet APK du dépôt ?')) return;
            const ok = await guarded(() => api('/apps/' + b.dataset.del, { method: 'DELETE' }));
            if (ok !== undefined) this.render();
        });
        $$('[data-dl]').forEach(b => b.onclick = () => download(`/apps/${b.dataset.dl}/download`, 'app.apk'));
    }
};

// ------------------------------------------------------------------ Paramètres

views.parameters = {
    async render() {
        setTitle('Paramètres applicatifs');
        const [params, groups, terminals] = await Promise.all([api('/parameters'), api('/groups'), api('/terminals')])
            .catch(e => { toast(e.message, true); return [null]; });
        if (!params) return;
        const refLabel = p => {
            if (p.scope === 'GROUP') return groups.find(g => g.id === p.scopeRef)?.name ?? '#' + p.scopeRef;
            if (p.scope === 'TERMINAL') return terminals.find(t => t.id === p.scopeRef)?.serialNumber ?? '#' + p.scopeRef;
            return '—';
        };
        const rows = params.map(p => `<tr><td class="mono">${esc(p.packageName)}</td><td>${statusBadge(p.scope)}</td><td>${esc(refLabel(p))}</td>
            <td class="mono">${esc(p.key)}</td><td class="mono">${esc(p.value)}</td><td>${fmtDate(p.updatedAt)}</td>
            <td><button class="btn sm danger" data-del="${p.id}">✕</button></td></tr>`);

        $('#view').innerHTML = `
            <div class="card" style="margin-bottom:16px">
                <h3>Définir des paramètres</h3>
                <p class="muted">Résolution : GLOBAL → GROUPE → TERMINAL (le plus spécifique l'emporte). Pour des jeux réutilisables,
                    utilisez les <a href="#" id="toTemplates">modèles de paramètres</a>.</p>
                <form class="form" id="paramForm">
                    <div class="form-row">
                        <label>Package *<input name="packageName" required placeholder="com.acme.payment"></label>
                        <label>Niveau<select name="scope">${options(meta.parameterScopes, 'GLOBAL')}</select></label>
                        <label class="hidden" data-scope="GROUP">Groupe<select name="groupRef">${options(groups, '', { value: g => g.id, label: g => g.name })}</select></label>
                        <label class="hidden" data-scope="TERMINAL">Terminal<select name="terminalRef">${options(terminals, '', { value: t => t.id, label: t => `${t.serialNumber} (${t.manufacturer})` })}</select></label>
                    </div>
                    <label>Valeurs (une par ligne, format clé=valeur)
                        <textarea name="values" placeholder="host=10.0.0.12&#10;port=5000&#10;currency=MAD"></textarea></label>
                    <label style="display:flex;gap:8px;align-items:center"><input type="checkbox" name="replace" style="width:auto">
                        Remplacer le jeu existant (supprime les clés absentes)</label>
                    <div class="actions">
                        <button class="btn primary">Enregistrer</button>
                        <button class="btn" type="button" id="saveAndPush">Enregistrer et pousser</button>
                    </div>
                </form>
            </div>
            ${table(['Package', 'Niveau', 'Cible', 'Clé', 'Valeur', 'Mis à jour', ''], rows, 'Aucun paramètre')}`;

        $('#toTemplates').onclick = e => { e.preventDefault(); navigate('templates'); };
        const form = $('#paramForm');
        const sync = () => $$('[data-scope]', form).forEach(el => el.classList.toggle('hidden', el.dataset.scope !== form.scope.value));
        form.scope.addEventListener('change', sync);

        const save = async () => {
            if (!form.reportValidity()) return null;
            const d = formData(form);
            const values = parseKeyValues(d.values);
            const scopeRef = d.scope === 'GROUP' ? numOrNull(d.groupRef) : d.scope === 'TERMINAL' ? numOrNull(d.terminalRef) : null;
            const res = await guarded(() => api('/parameters', {
                method: 'PUT', body: { scope: d.scope, scopeRef, packageName: d.packageName, values, replace: form.replace.checked }
            }));
            return res ? { d, scopeRef } : null;
        };

        form.addEventListener('submit', async e => {
            e.preventDefault();
            if (await save()) { toast('Paramètres enregistrés'); this.render(); }
        });
        $('#saveAndPush').onclick = async () => {
            const r = await save();
            if (!r) return;
            const target = r.d.scope === 'GROUP' ? { groupId: r.scopeRef }
                : r.d.scope === 'TERMINAL' ? { terminalIds: [r.scopeRef] } : { all: true };
            const dep = await guarded(() => api('/deployments', { method: 'POST', body: { type: 'PUSH_PARAMS', packageName: r.d.packageName, target } }));
            if (dep) { toast(`Paramètres enregistrés, ${dep.taskCount} terminal(aux) notifié(s)`); this.render(); }
        };
        $$('[data-del]').forEach(b => b.onclick = async () => {
            const ok = await guarded(() => api('/parameters/' + b.dataset.del, { method: 'DELETE' }));
            if (ok !== undefined) this.render();
        });
    }
};

function parseKeyValues(text) {
    const values = {};
    for (const line of (text || '').split('\n')) {
        const i = line.indexOf('=');
        if (i > 0) values[line.slice(0, i).trim()] = line.slice(i + 1).trim();
    }
    return values;
}

const kvText = obj => Object.entries(obj || {}).map(([k, v]) => `${k}=${v}`).join('\n');

// ------------------------------------------------------------------ Modèles (zéro contact + paramètres)

views.templates = {
    async render() {
        setTitle('Modèles', `<div class="actions">
            <button class="btn" id="newParamTpl">+ Modèle de paramètres</button>
            <button class="btn primary" id="newDeployTpl">+ Modèle de déploiement</button></div>`);
        const [deploys, params, apps, groups] = await Promise.all([api('/deployment-templates'), api('/parameter-templates'),
            api('/apps'), api('/groups')]).catch(e => { toast(e.message, true); return [null]; });
        if (!deploys) return;
        const appName = id => { const a = apps.find(x => x.id === id); return a ? `${a.label || a.packageName} ${a.versionName || ''}` : '#' + id; };
        const paramName = id => params.find(x => x.id === id)?.name ?? '#' + id;
        const usedBy = id => groups.filter(g => g.templateId === id).map(g => g.name).join(', ');

        const dRows = deploys.map(t => `<tr><td><b>${esc(t.name)}</b><br><span class="muted">${esc(t.description)}</span></td>
            <td>${esc(t.appIds.map(appName).join(', ')) || '—'}</td><td>${esc(t.parameterTemplateIds.map(paramName).join(', ')) || '—'}</td>
            <td class="mono">${esc(t.autoRunPackage) || '—'}</td><td class="mono">${esc(t.kioskPackages.join(', ')) || '—'}</td>
            <td>${esc(usedBy(t.id)) || '<span class="muted">aucun groupe</span>'}</td>
            <td class="actions"><button class="btn sm" data-edit-d="${t.id}">Modifier</button><button class="btn sm danger" data-del-d="${t.id}">Supprimer</button></td></tr>`);
        const pRows = params.map(t => `<tr><td><b>${esc(t.name)}</b></td><td class="mono">${esc(t.packageName)}</td>
            <td class="mono">${esc(Object.keys(t.values).length)} clé(s)</td><td>${fmtDate(t.updatedAt)}</td>
            <td class="actions"><button class="btn sm" data-apply-p="${t.id}">Appliquer…</button><button class="btn sm" data-edit-p="${t.id}">Modifier</button><button class="btn sm danger" data-del-p="${t.id}">Supprimer</button></td></tr>`);

        $('#view').innerHTML = `
            <div class="card" style="margin-bottom:16px">
                <h3>Modèles de déploiement (zéro contact)</h3>
                <p class="muted">Liez un modèle à un <b>groupe</b> : tout terminal qui rejoint ce groupe (pré-enregistrement, enrôlement ou changement de groupe)
                    reçoit automatiquement ses applications, ses paramètres, son application de démarrage et son kiosque.</p>
                ${table(['Modèle', 'Applications', 'Paramètres', 'Démarrage auto', 'Kiosque', 'Groupes', ''], dRows, 'Aucun modèle de déploiement')}
            </div>
            <div class="card">
                <h3>Modèles de paramètres</h3>
                ${table(['Modèle', 'Package', 'Valeurs', 'Mis à jour', ''], pRows, 'Aucun modèle de paramètres')}
            </div>`;

        $('#newDeployTpl').onclick = () => deployTemplateModal(null, apps, params);
        $('#newParamTpl').onclick = () => paramTemplateModal(null);
        $$('[data-edit-d]').forEach(b => b.onclick = () => deployTemplateModal(deploys.find(x => String(x.id) === b.dataset.editD), apps, params));
        $$('[data-edit-p]').forEach(b => b.onclick = () => paramTemplateModal(params.find(x => String(x.id) === b.dataset.editP)));
        $$('[data-apply-p]').forEach(b => b.onclick = () => applyParamTemplateModal(params.find(x => String(x.id) === b.dataset.applyP), groups));
        $$('[data-del-d]').forEach(b => b.onclick = async () => {
            if (!confirm('Supprimer ce modèle de déploiement ?')) return;
            if (await guarded(() => api('/deployment-templates/' + b.dataset.delD, { method: 'DELETE' })) !== undefined) this.render();
        });
        $$('[data-del-p]').forEach(b => b.onclick = async () => {
            if (!confirm('Supprimer ce modèle de paramètres ?')) return;
            if (await guarded(() => api('/parameter-templates/' + b.dataset.delP, { method: 'DELETE' })) !== undefined) this.render();
        });
    }
};

function deployTemplateModal(t, apps, params) {
    const checks = (list, name, selected, label) => list.length
        ? `<div class="checklist">${list.map(x => `<label><input type="checkbox" name="${name}" value="${x.id}" ${selected.includes(x.id) ? 'checked' : ''}> ${esc(label(x))}</label>`).join('')}</div>`
        : '<p class="muted">Aucun élément disponible.</p>';
    const body = openModal(t ? 'Modifier le modèle de déploiement' : 'Nouveau modèle de déploiement', `
        <form class="form" id="dtForm">
            <div class="form-row">
                <label>Nom *<input name="name" required value="${esc(t?.name)}"></label>
                <label>Description<input name="description" value="${esc(t?.description)}"></label>
            </div>
            <label>Applications à installer</label>
            ${checks(apps, 'appIds', t?.appIds || [], a => `${a.label || a.packageName} ${a.versionName || ''} (${a.packageName})`)}
            <label>Modèles de paramètres</label>
            ${checks(params, 'paramIds', t?.parameterTemplateIds || [], p => `${p.name} (${p.packageName})`)}
            <div class="form-row">
                <label>Démarrage automatique (package)<input name="autoRunPackage" value="${esc(t?.autoRunPackage)}" placeholder="com.acme.payment"></label>
            </div>
            <label>Kiosque : applications autorisées (une par ligne, vide = pas de kiosque)
                <textarea name="kioskPackages">${esc((t?.kioskPackages || []).join('\n'))}</textarea></label>
            <div class="actions"><button class="btn primary">Enregistrer</button></div>
        </form>`);
    $('#dtForm', body).addEventListener('submit', async e => {
        e.preventDefault();
        const f = e.target;
        const d = formData(f);
        const req = {
            name: d.name, description: orNull(d.description),
            appIds: $$('input[name=appIds]:checked', f).map(i => Number(i.value)),
            parameterTemplateIds: $$('input[name=paramIds]:checked', f).map(i => Number(i.value)),
            autoRunPackage: orNull(d.autoRunPackage), kioskPackages: lines(d.kioskPackages)
        };
        const ok = await guarded(() => api(t ? '/deployment-templates/' + t.id : '/deployment-templates', { method: t ? 'PUT' : 'POST', body: req }));
        if (ok) { closeModal(); toast('Modèle enregistré'); views.templates.render(); }
    });
}

function paramTemplateModal(t) {
    const body = openModal(t ? 'Modifier le modèle de paramètres' : 'Nouveau modèle de paramètres', `
        <form class="form" id="ptForm">
            <div class="form-row">
                <label>Nom *<input name="name" required value="${esc(t?.name)}"></label>
                <label>Package *<input name="packageName" required value="${esc(t?.packageName)}" placeholder="com.acme.payment"></label>
            </div>
            <label>Valeurs (une par ligne, format clé=valeur)<textarea name="values">${esc(kvText(t?.values))}</textarea></label>
            <div class="actions"><button class="btn primary">Enregistrer</button></div>
        </form>`);
    $('#ptForm', body).addEventListener('submit', async e => {
        e.preventDefault();
        const d = formData(e.target);
        const req = { name: d.name, packageName: d.packageName, values: parseKeyValues(d.values) };
        const ok = await guarded(() => api(t ? '/parameter-templates/' + t.id : '/parameter-templates', { method: t ? 'PUT' : 'POST', body: req }));
        if (ok) { closeModal(); toast('Modèle enregistré'); views.templates.render(); }
    });
}

async function applyParamTemplateModal(t, groups) {
    const terminals = await guarded(() => api('/terminals'));
    if (!terminals) return;
    const body = openModal(`Appliquer « ${t.name} »`, `
        <form class="form" id="apForm">
            <div class="form-row">
                <label>Niveau<select name="scope">${options(meta.parameterScopes, 'GROUP')}</select></label>
                <label data-scope="GROUP">Groupe<select name="groupRef">${options(groups, '', { value: g => g.id, label: g => g.name })}</select></label>
                <label class="hidden" data-scope="TERMINAL">Terminal<select name="terminalRef">${options(terminals, '', { value: x => x.id, label: x => x.serialNumber })}</select></label>
            </div>
            <label style="display:flex;gap:8px;align-items:center"><input type="checkbox" name="push" checked style="width:auto"> Pousser immédiatement aux terminaux concernés</label>
            <div class="actions"><button class="btn primary">Appliquer</button></div>
        </form>`);
    const f = $('#apForm', body);
    f.scope.addEventListener('change', () => $$('[data-scope]', f).forEach(el => el.classList.toggle('hidden', el.dataset.scope !== f.scope.value)));
    f.addEventListener('submit', async e => {
        e.preventDefault();
        const d = formData(f);
        const scopeRef = d.scope === 'GROUP' ? numOrNull(d.groupRef) : d.scope === 'TERMINAL' ? numOrNull(d.terminalRef) : null;
        const res = await guarded(() => api(`/parameter-templates/${t.id}/apply`, { method: 'POST', body: { scope: d.scope, scopeRef, push: f.push.checked } }));
        if (res) { closeModal(); toast(`Modèle appliqué` + (res.taskCount ? `, ${res.taskCount} terminal(aux) notifié(s)` : '')); }
    });
}

// ------------------------------------------------------------------ Tâches

let taskFilter = '';

views.tasks = {
    async render() {
        setTitle('Tâches', `<button class="btn primary" id="newDeploy">+ Déploiement</button>`);
        $('#newDeploy').onclick = () => deployModal({});
        const list = await guarded(() => api('/tasks' + (taskFilter ? '?status=' + taskFilter : '')));
        if (!list) return;
        const sched = t => [t.notBefore ? 'après ' + fmtDate(t.notBefore) : '', t.windowStart ? `${t.windowStart}–${t.windowEnd}` : ''].filter(Boolean).join(' · ');
        const rows = list.map(t => `<tr>
            <td>${t.id}</td><td class="mono">${esc(t.serialNumber)}</td><td><span class="badge">${esc(t.manufacturer)}</span></td>
            <td>${esc(TASK_LABELS[t.type] || t.type)}</td><td class="mono">${esc(t.payload.packageName || t.payload.path || '')} ${esc(t.payload.versionName || '')}</td>
            <td>${statusBadge(t.status)}</td><td>${esc(t.message)}</td><td class="muted">${esc(sched(t))}</td>
            <td>${fmtDate(t.createdAt)}</td><td>${fmtDate(t.updatedAt)}</td><td>${taskResultButtons(t)}</td></tr>`);
        $('#view').innerHTML = `
            <div class="filters"><select id="taskStatus">${options(meta.taskStatuses, taskFilter, { empty: 'Tous statuts' })}</select></div>
            ${table(['#', 'Terminal', 'Constructeur', 'Action', 'Cible', 'Statut', 'Message', 'Planification', 'Créée', 'Mise à jour', ''], rows, 'Aucune tâche')}
            <p class="muted">200 dernières tâches · rafraîchissement automatique toutes les 15 s</p>`;
        $('#taskStatus').onchange = e => { taskFilter = e.target.value; this.render(); };
        bindTaskResultButtons($('#view'), () => this.render());
    }
};

// ------------------------------------------------------------------ Historique

views.audit = {
    async render() {
        setTitle('Historique');
        const [events, terminals] = await Promise.all([api('/audit'), api('/terminals')]).catch(e => { toast(e.message, true); return [null]; });
        if (!events) return;
        const sn = id => terminals.find(t => t.id === id)?.serialNumber ?? (id ? '#' + id : '');
        const rows = events.map(e => `<tr><td>${fmtDate(e.occurredAt)}</td><td>${esc(AUDIT_LABELS[e.action] || e.action)}</td>
            <td>${esc(e.actor)}</td><td class="mono">${esc(sn(e.terminalId))}</td><td>${esc(e.details)}</td></tr>`);
        $('#view').innerHTML = table(['Date', 'Événement', 'Par', 'Terminal', 'Détails'], rows, 'Aucun événement')
            + '<p class="muted">300 derniers événements : actions des administrateurs, enrôlements, applications installées / retirées.</p>';
    }
};

// ------------------------------------------------------------------ Organisations, marchands, groupes (CRUD)

function crudView({ title, path, columns, fields, row, afterSave }) {
    return {
        async render() {
            setTitle(title, `<button class="btn primary" id="addItem">+ Ajouter</button>`);
            $('#addItem').onclick = () => this.edit(null);
            const list = await guarded(() => api(path));
            if (!list) return;
            this.list = list;
            $('#view').innerHTML = table([...columns, ''], list.map(item => `<tr>${row(item, list)}
                <td class="actions"><button class="btn sm" data-edit="${item.id}">Modifier</button>
                <button class="btn sm danger" data-del="${item.id}">Supprimer</button></td></tr>`));
            $$('[data-edit]').forEach(b => b.onclick = () => this.edit(this.list.find(x => String(x.id) === b.dataset.edit)));
            $$('[data-del]').forEach(b => b.onclick = async () => {
                if (!confirm('Confirmer la suppression ?')) return;
                const ok = await guarded(() => api(`${path}/${b.dataset.del}`, { method: 'DELETE' }));
                if (ok !== undefined) this.render();
            });
        },
        async edit(item) {
            const opts = {};
            for (const f of fields) if (f.options) opts[f.key] = await guarded(() => f.options(item));
            const input = f => {
                const v = item?.[f.key];
                if (f.type === 'select') return `<select name="${f.key}">${options(opts[f.key] || [], v, { value: o => o.value, label: o => o.label, empty: '—' })}</select>`;
                if (f.type === 'checkbox') return `<input type="checkbox" name="${f.key}" style="width:auto">`;
                return `<input name="${f.key}" value="${esc(v)}" ${f.required ? 'required' : ''}>`;
            };
            const body = openModal(item ? 'Modifier' : 'Ajouter', `<form class="form" id="crudForm">
                <div class="form-row">${fields.filter(f => f.type !== 'checkbox').map(f => `<label>${esc(f.label)}${f.required ? ' *' : ''}${input(f)}</label>`).join('')}</div>
                ${fields.filter(f => f.type === 'checkbox').map(f => `<label style="display:flex;gap:8px;align-items:center">${input(f)} ${esc(f.label)}</label>`).join('')}
                ${fields.filter(f => f.help).map(f => `<p class="muted">${esc(f.help)}</p>`).join('')}
                <div class="actions"><button class="btn primary">Enregistrer</button></div></form>`);
            $('#crudForm', body).addEventListener('submit', async e => {
                e.preventDefault();
                const d = {};
                for (const f of fields) {
                    const el = e.target.elements[f.key];
                    d[f.key] = f.type === 'checkbox' ? el.checked : f.type === 'select' ? numOrNull(el.value) : orNull(el.value.trim());
                }
                const ok = await guarded(() => api(item ? `${path}/${item.id}` : path, { method: item ? 'PUT' : 'POST', body: d }));
                if (ok) { closeModal(); toast('Enregistré'); this.render(); }
            });
        }
    };
}

views.organizations = crudView({
    title: 'Organisations', path: '/organizations',
    columns: ['Nom', 'Organisation parente', 'Description'],
    fields: [
        { key: 'name', label: 'Nom', required: true },
        { key: 'parentId', label: 'Organisation parente', type: 'select',
          options: async item => (await api('/organizations')).filter(o => !item || o.id !== item.id).map(o => ({ value: o.id, label: o.name })) },
        { key: 'description', label: 'Description' }
    ],
    row: o => `<td><b>${esc(o.name)}</b></td><td>${esc(o.parentName) || '<span class="muted">racine</span>'}</td><td>${esc(o.description)}</td>`
});

views.merchants = crudView({
    title: 'Marchands', path: '/merchants',
    columns: ['Code', 'Nom', 'Organisation', 'Ville', 'Adresse'],
    fields: [
        { key: 'code', label: 'Code', required: true }, { key: 'name', label: 'Nom', required: true },
        { key: 'organizationId', label: 'Organisation', type: 'select',
          options: async () => (await api('/organizations')).map(o => ({ value: o.id, label: o.name })) },
        { key: 'city', label: 'Ville' }, { key: 'address', label: 'Adresse' }
    ],
    row: m => `<td class="mono">${esc(m.code)}</td><td>${esc(m.name)}</td><td>${esc(m.organizationName)}</td><td>${esc(m.city)}</td><td>${esc(m.address)}</td>`
});

views.groups = crudView({
    title: 'Groupes de terminaux', path: '/groups',
    columns: ['Nom', 'Modèle de déploiement', 'Description'],
    fields: [
        { key: 'name', label: 'Nom', required: true },
        { key: 'templateId', label: 'Modèle de déploiement (zéro contact)', type: 'select',
          options: async () => (await api('/deployment-templates')).map(t => ({ value: t.id, label: t.name })) },
        { key: 'description', label: 'Description' },
        { key: 'applyToMembers', label: 'Appliquer aussi le modèle aux terminaux déjà dans le groupe', type: 'checkbox',
          help: 'Les nouveaux terminaux du groupe reçoivent toujours le modèle automatiquement.' }
    ],
    row: g => `<td>${esc(g.name)}</td><td>${esc(g.templateName) || '<span class="muted">aucun</span>'}</td><td>${esc(g.description)}</td>`
});

// ------------------------------------------------------------------ boot

if (auth) start(); else showLogin();
