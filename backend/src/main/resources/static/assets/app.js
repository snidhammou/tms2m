'use strict';

/* Console d'administration TMS — SPA vanilla JS sur l'API /api/admin/v1 */

const API = '/api/admin/v1';
const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

let auth = null;
try { auth = sessionStorage.getItem('tms.auth'); } catch (e) { /* stockage indisponible */ }
let meta = null;
let currentView = 'dashboard';
let refreshTimer = null;

// ------------------------------------------------------------------ utils

function esc(v) {
    return v === null || v === undefined ? '' : String(v)
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function fmtDate(v) {
    if (!v) return '—';
    return new Date(v).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'medium' });
}

function fmtSize(b) {
    if (b == null) return '';
    if (b < 1024) return b + ' o';
    if (b < 1048576) return (b / 1024).toFixed(1) + ' Ko';
    return (b / 1048576).toFixed(1) + ' Mo';
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

// ------------------------------------------------------------------ Tableau de bord

views.dashboard = {
    async render() {
        setTitle('Tableau de bord');
        const d = await guarded(() => api('/dashboard'));
        if (!d) return;
        const bars = (map) => {
            const entries = Object.entries(map);
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
                <div class="card kpi"><div class="value">${d.apps}</div><div class="label">Versions d'APK</div></div>
                <div class="card kpi"><div class="value">${d.merchants}</div><div class="label">Marchands</div></div>
            </div>
            <div class="grid two" style="margin-top:16px">
                <div class="card"><h3>Parc par constructeur</h3>${bars(d.byManufacturer)}</div>
                <div class="card"><h3>Parc par statut</h3>${bars(d.byStatus)}</div>
                <div class="card"><h3>Tâches par statut</h3>${bars(d.tasksByStatus)}</div>
            </div>`;
    }
};

// ------------------------------------------------------------------ Terminaux

const terminalFilters = { manufacturer: '', status: '', groupId: '', merchantId: '', q: '' };

views.terminals = {
    async render() {
        setTitle('Terminaux', `<div class="actions">
            <button class="btn" id="bulkDeploy">Déployer…</button>
            <button class="btn primary" id="addTerminal">+ Pré-enregistrer</button></div>`);
        $('#addTerminal').onclick = () => terminalCreateModal();
        $('#bulkDeploy').onclick = () => deployModal({});

        const [groups, merchants] = await Promise.all([api('/groups'), api('/merchants')]).catch(e => { toast(e.message, true); return [[], []]; });
        const qs = new URLSearchParams(Object.entries(terminalFilters).filter(([, v]) => v)).toString();
        const list = await guarded(() => api('/terminals' + (qs ? '?' + qs : '')));
        if (!list) return;

        const rows = list.map(t => `<tr class="clickable" data-id="${t.id}">
            <td class="mono">${esc(t.serialNumber)}</td>
            <td><span class="badge">${esc(t.manufacturer)}</span></td>
            <td>${esc(t.model)}</td>
            <td class="mono">${esc(t.tid)}</td>
            <td>${esc(t.merchantName)}</td>
            <td>${esc(t.groupName)}</td>
            <td>${statusBadge(t.status)}</td>
            <td><span class="dot ${t.online ? 'on' : ''}"></span>${t.online ? 'En ligne' : 'Hors ligne'}</td>
            <td>${fmtDate(t.lastSeenAt)}</td>
            <td>${t.batteryLevel != null ? t.batteryLevel + ' %' : '—'}</td>
            <td class="mono">${esc(t.agentVersion)}</td></tr>`);

        $('#view').innerHTML = `
            <form class="filters" id="termFilters">
                <input name="q" placeholder="N° série, TID, modèle…" value="${esc(terminalFilters.q)}">
                <select name="manufacturer">${options(meta.manufacturers, terminalFilters.manufacturer, { empty: 'Tous constructeurs' })}</select>
                <select name="status">${options(meta.terminalStatuses, terminalFilters.status, { empty: 'Tous statuts' })}</select>
                <select name="groupId">${options(groups, terminalFilters.groupId, { value: g => g.id, label: g => g.name, empty: 'Tous groupes' })}</select>
                <select name="merchantId">${options(merchants, terminalFilters.merchantId, { value: m => m.id, label: m => m.name, empty: 'Tous marchands' })}</select>
            </form>
            <p class="muted">${list.length} terminal(aux)</p>
            ${table(['N° série', 'Constructeur', 'Modèle', 'TID', 'Marchand', 'Groupe', 'Statut', 'Connexion', 'Dernier contact', 'Batterie', 'Agent'], rows, 'Aucun terminal. Installez l\'agent Android ou pré-enregistrez un terminal.')}`;

        const f = $('#termFilters');
        f.addEventListener('change', () => { Object.assign(terminalFilters, formData(f)); this.render(); });
        f.addEventListener('submit', e => { e.preventDefault(); Object.assign(terminalFilters, formData(f)); this.render(); });
        $$('tr[data-id]').forEach(tr => tr.addEventListener('click', () => terminalDetail(tr.dataset.id)));
    }
};

async function terminalCreateModal() {
    const [groups, merchants] = await Promise.all([api('/groups'), api('/merchants')]);
    const body = openModal('Pré-enregistrer un terminal', `
        <form class="form" id="createTerm">
            <div class="form-row">
                <label>N° de série *<input name="serialNumber" required></label>
                <label>Constructeur<select name="manufacturer">${options(meta.manufacturers, 'OTHER')}</select></label>
                <label>Modèle<input name="model" placeholder="A920, N910, P2…"></label>
            </div>
            <div class="form-row">
                <label>TID<input name="tid"></label>
                <label>Marchand<select name="merchantId">${options(merchants, '', { value: m => m.id, label: m => m.name, empty: '—' })}</select></label>
                <label>Groupe<select name="groupId">${options(groups, '', { value: g => g.id, label: g => g.name, empty: '—' })}</select></label>
            </div>
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

async function terminalDetail(id) {
    const [t, tasks, groups, merchants, apps] = await Promise.all([
        api('/terminals/' + id), api('/tasks?terminalId=' + id), api('/groups'), api('/merchants'), api('/apps')
    ]).catch(e => { toast(e.message, true); return []; });
    if (!t) return;

    const appRows = (t.installedApps || []).map(a => `<tr><td class="mono">${esc(a.packageName)}</td><td>${esc(a.versionName)}</td><td>${a.versionCode}</td></tr>`);
    const taskRows = tasks.map(k => `<tr><td>${k.id}</td><td>${esc(k.type)}</td><td class="mono">${esc(k.payload.packageName || '')}</td>
        <td>${statusBadge(k.status)}</td><td>${esc(k.message)}</td><td>${fmtDate(k.updatedAt)}</td></tr>`);

    const body = openModal(`${t.manufacturer} · ${t.serialNumber}`, `
        <div class="kv">
            <div><span>Modèle</span>${esc(t.model) || '—'}</div>
            <div><span>Statut</span>${statusBadge(t.status)}</div>
            <div><span>Connexion</span><span class="dot ${t.online ? 'on' : ''}"></span>${t.online ? 'En ligne' : 'Hors ligne'}</div>
            <div><span>Dernier contact</span>${fmtDate(t.lastSeenAt)}</div>
            <div><span>Android</span>${esc(t.osVersion) || '—'}</div>
            <div><span>Firmware</span>${esc(t.firmwareVersion) || '—'}</div>
            <div><span>Agent</span>${esc(t.agentVersion) || '—'}</div>
            <div><span>Batterie</span>${t.batteryLevel != null ? t.batteryLevel + ' %' : '—'}</div>
            <div><span>IP</span>${esc(t.ipAddress) || '—'}</div>
            <div><span>Position</span>${t.latitude != null ? `${t.latitude.toFixed(5)}, ${t.longitude.toFixed(5)}` : '—'}</div>
            <div><span>Enrôlé le</span>${fmtDate(t.enrolledAt)}</div>
        </div>
        <form class="form" id="editTerm">
            <h4>Affectation</h4>
            <div class="form-row">
                <label>TID<input name="tid" value="${esc(t.tid)}"></label>
                <label>Marchand<select name="merchantId">${options(merchants, t.merchantId, { value: m => m.id, label: m => m.name, empty: '—' })}</select></label>
                <label>Groupe<select name="groupId">${options(groups, t.groupId, { value: g => g.id, label: g => g.name, empty: '—' })}</select></label>
                <label>Statut<select name="status">${options(meta.terminalStatuses, t.status)}</select></label>
            </div>
            <div class="actions">
                <button class="btn primary">Enregistrer</button>
                <button class="btn" type="button" id="actReboot">Redémarrer</button>
                <button class="btn" type="button" id="actInstall">Installer une app…</button>
                <button class="btn" type="button" id="actParams">Pousser paramètres…</button>
                <button class="btn danger" type="button" id="actDelete">Supprimer</button>
            </div>
        </form>
        <h4>Applications installées (${appRows.length})</h4>
        ${table(['Package', 'Version', 'versionCode'], appRows, 'Inventaire non encore remonté')}
        <h4>Historique des tâches</h4>
        ${table(['#', 'Type', 'Package', 'Statut', 'Message', 'Mis à jour'], taskRows, 'Aucune tâche')}`);

    $('#editTerm', body).addEventListener('submit', async e => {
        e.preventDefault();
        const d = formData(e.target);
        const ok = await guarded(() => api('/terminals/' + id, {
            method: 'PUT', body: { tid: orNull(d.tid), merchantId: numOrNull(d.merchantId), groupId: numOrNull(d.groupId), status: d.status }
        }));
        if (ok) { toast('Terminal mis à jour'); terminalDetail(id); }
    });
    $('#actReboot', body).onclick = () => quickDeploy({ type: 'REBOOT' }, id);
    $('#actInstall', body).onclick = () => deployModal({ type: 'INSTALL_APP', terminalIds: [Number(id)], apps });
    $('#actParams', body).onclick = () => deployModal({ type: 'PUSH_PARAMS', terminalIds: [Number(id)] });
    $('#actDelete', body).onclick = async () => {
        if (!confirm(`Supprimer définitivement le terminal ${t.serialNumber} et son historique ?`)) return;
        const ok = await guarded(() => api('/terminals/' + id, { method: 'DELETE' }));
        if (ok !== undefined) { closeModal(); toast('Terminal supprimé'); views.terminals.render(); }
    };
}

async function quickDeploy(req, terminalId) {
    const res = await guarded(() => api('/deployments', { method: 'POST', body: { ...req, target: { terminalIds: [Number(terminalId)] } } }));
    if (res) { toast(`${req.type} planifié`); terminalDetail(terminalId); }
}

// ------------------------------------------------------------------ Déploiement (modal générique)

async function deployModal({ type = 'INSTALL_APP', terminalIds = null, appId = null, apps = null }) {
    const [groups, merchants, appList] = await Promise.all([api('/groups'), api('/merchants'), apps ? apps : api('/apps')]);
    const fixedTargets = terminalIds && terminalIds.length;
    const body = openModal('Nouveau déploiement', `
        <form class="form" id="deployForm">
            <div class="form-row">
                <label>Action<select name="type">${options(meta.taskTypes, type)}</select></label>
                <label data-for="INSTALL_APP">Application<select name="appId">${options(appList, appId,
                    { value: a => a.id, label: a => `${a.label || a.packageName} ${a.versionName || ''} (${a.versionCode})` })}</select></label>
                <label data-for="UNINSTALL_APP PUSH_PARAMS">Package<input name="packageName" placeholder="com.acme.payment"></label>
            </div>
            ${fixedTargets ? `<p class="muted">Cible : ${terminalIds.length} terminal(aux) sélectionné(s)</p>` : `
            <h4>Cible (filtres combinés)</h4>
            <div class="form-row">
                <label>Constructeur<select name="manufacturer">${options(meta.manufacturers, '', { empty: 'Tous' })}</select></label>
                <label>Groupe<select name="groupId">${options(groups, '', { value: g => g.id, label: g => g.name, empty: 'Tous' })}</select></label>
                <label>Marchand<select name="merchantId">${options(merchants, '', { value: m => m.id, label: m => m.name, empty: 'Tous' })}</select></label>
            </div>`}
            <div class="actions"><button class="btn primary">Lancer le déploiement</button></div>
        </form>`);

    const form = $('#deployForm', body);
    const sync = () => {
        const t = form.type.value;
        $$('[data-for]', form).forEach(el => el.classList.toggle('hidden', !el.dataset.for.split(' ').includes(t)));
    };
    form.type.addEventListener('change', sync);
    sync();

    form.addEventListener('submit', async e => {
        e.preventDefault();
        const d = formData(form);
        const target = fixedTargets ? { terminalIds } : {
            manufacturer: orNull(d.manufacturer), groupId: numOrNull(d.groupId), merchantId: numOrNull(d.merchantId)
        };
        if (!fixedTargets && !target.manufacturer && !target.groupId && !target.merchantId) {
            if (!confirm('Aucun filtre : cibler TOUT le parc ?')) return;
            target.all = true;
        }
        const res = await guarded(() => api('/deployments', { method: 'POST', body: buildDeploy(d, target) }));
        if (res) {
            closeModal();
            toast(`${res.taskCount} tâche(s) créée(s)`);
            if (currentView === 'tasks') views.tasks.render();
        }
    });
}

function buildDeploy(d, target) {
    return { type: d.type, appId: d.type === 'INSTALL_APP' ? numOrNull(d.appId) : null, packageName: orNull(d.packageName), target };
}

// ------------------------------------------------------------------ Applications

views.apps = {
    async render() {
        setTitle('Applications');
        const apps = await guarded(() => api('/apps'));
        if (!apps) return;
        const rows = apps.map(a => `<tr>
            <td>${esc(a.label)}</td><td class="mono">${esc(a.packageName)}</td><td>${esc(a.versionName)}</td><td>${a.versionCode}</td>
            <td>${fmtSize(a.sizeBytes)}</td><td class="mono" title="${esc(a.sha256)}">${esc(a.sha256.substring(0, 12))}…</td>
            <td>${fmtDate(a.uploadedAt)}</td>
            <td class="actions"><button class="btn sm" data-deploy="${a.id}">Déployer</button>
                <a class="btn sm" href="#" data-dl="${a.id}">Télécharger</a>
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
            ${table(['Nom', 'Package', 'Version', 'Code', 'Taille', 'SHA-256', 'Publié le', ''], rows, 'Aucun APK publié')}`;

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
        $$('[data-dl]').forEach(a => a.onclick = async e => {
            e.preventDefault();
            const res = await fetch(`${API}/apps/${a.dataset.dl}/download`, { headers: { Authorization: 'Basic ' + auth } });
            if (!res.ok) { toast('Téléchargement impossible', true); return; }
            const blob = await res.blob();
            const link = document.createElement('a');
            link.href = URL.createObjectURL(blob);
            link.download = (res.headers.get('Content-Disposition') || '').split('filename="')[1]?.replace('"', '') || 'app.apk';
            link.click();
            URL.revokeObjectURL(link.href);
        });
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
                <p class="muted">Résolution : GLOBAL → GROUPE → TERMINAL (le plus spécifique l'emporte). Les applications de paiement
                    les récupèrent via le service AIDL de l'agent, après une tâche PUSH_PARAMS.</p>
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

        const form = $('#paramForm');
        const sync = () => $$('[data-scope]', form).forEach(el => el.classList.toggle('hidden', el.dataset.scope !== form.scope.value));
        form.scope.addEventListener('change', sync);

        const save = async () => {
            if (!form.reportValidity()) return null;
            const d = formData(form);
            const values = {};
            for (const line of d.values.split('\n')) {
                const i = line.indexOf('=');
                if (i > 0) values[line.slice(0, i).trim()] = line.slice(i + 1).trim();
            }
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
                : r.d.scope === 'TERMINAL' ? { terminalIds: [r.scopeRef] } : null;
            if (target) {
                const dep = await guarded(() => api('/deployments', { method: 'POST', body: { type: 'PUSH_PARAMS', packageName: r.d.packageName, target } }));
                if (dep) toast(`Paramètres enregistrés, ${dep.taskCount} terminal(aux) notifié(s)`);
            } else {
                deployModal({ type: 'PUSH_PARAMS' });
                setTimeout(() => { const p = $('#deployForm [name=packageName]'); if (p) p.value = r.d.packageName; }, 300);
            }
        };
        $$('[data-del]').forEach(b => b.onclick = async () => {
            const ok = await guarded(() => api('/parameters/' + b.dataset.del, { method: 'DELETE' }));
            if (ok !== undefined) this.render();
        });
    }
};

// ------------------------------------------------------------------ Tâches

let taskFilter = '';

views.tasks = {
    async render() {
        setTitle('Tâches', `<button class="btn primary" id="newDeploy">+ Déploiement</button>`);
        $('#newDeploy').onclick = () => deployModal({});
        const list = await guarded(() => api('/tasks' + (taskFilter ? '?status=' + taskFilter : '')));
        if (!list) return;
        const rows = list.map(t => `<tr>
            <td>${t.id}</td><td class="mono">${esc(t.serialNumber)}</td><td><span class="badge">${esc(t.manufacturer)}</span></td>
            <td>${esc(t.type)}</td><td class="mono">${esc(t.payload.packageName || '')} ${esc(t.payload.versionName || '')}</td>
            <td>${statusBadge(t.status)}</td><td>${esc(t.message)}</td><td>${fmtDate(t.createdAt)}</td><td>${fmtDate(t.updatedAt)}</td>
            <td>${['PENDING', 'SENT'].includes(t.status) ? `<button class="btn sm danger" data-cancel="${t.id}">Annuler</button>` : ''}</td></tr>`);
        $('#view').innerHTML = `
            <div class="filters"><select id="taskStatus">${options(meta.taskStatuses, taskFilter, { empty: 'Tous statuts' })}</select></div>
            ${table(['#', 'Terminal', 'Constructeur', 'Type', 'Package', 'Statut', 'Message', 'Créée', 'Mise à jour', ''], rows, 'Aucune tâche')}
            <p class="muted">200 dernières tâches · rafraîchissement automatique toutes les 15 s</p>`;
        $('#taskStatus').onchange = e => { taskFilter = e.target.value; this.render(); };
        $$('[data-cancel]').forEach(b => b.onclick = async () => {
            const ok = await guarded(() => api(`/tasks/${b.dataset.cancel}/cancel`, { method: 'POST' }));
            if (ok) this.render();
        });
    }
};

// ------------------------------------------------------------------ Marchands & groupes (CRUD simple)

function crudView({ title, path, columns, fields, row }) {
    return {
        async render() {
            setTitle(title, `<button class="btn primary" id="addItem">+ Ajouter</button>`);
            $('#addItem').onclick = () => this.edit(null);
            const list = await guarded(() => api(path));
            if (!list) return;
            this.list = list;
            $('#view').innerHTML = table([...columns, ''], list.map(item => `<tr>${row(item)}
                <td class="actions"><button class="btn sm" data-edit="${item.id}">Modifier</button>
                <button class="btn sm danger" data-del="${item.id}">Supprimer</button></td></tr>`));
            $$('[data-edit]').forEach(b => b.onclick = () => this.edit(this.list.find(x => String(x.id) === b.dataset.edit)));
            $$('[data-del]').forEach(b => b.onclick = async () => {
                if (!confirm('Confirmer la suppression ?')) return;
                const ok = await guarded(() => api(`${path}/${b.dataset.del}`, { method: 'DELETE' }));
                if (ok !== undefined) this.render();
            });
        },
        edit(item) {
            const body = openModal(item ? 'Modifier' : 'Ajouter', `<form class="form" id="crudForm">
                <div class="form-row">${fields.map(([k, label, req]) =>
                    `<label>${esc(label)}${req ? ' *' : ''}<input name="${k}" value="${esc(item?.[k])}" ${req ? 'required' : ''}></label>`).join('')}</div>
                <div class="actions"><button class="btn primary">Enregistrer</button></div></form>`);
            $('#crudForm', body).addEventListener('submit', async e => {
                e.preventDefault();
                const d = formData(e.target);
                Object.keys(d).forEach(k => { d[k] = orNull(d[k]); });
                const ok = await guarded(() => api(item ? `${path}/${item.id}` : path, { method: item ? 'PUT' : 'POST', body: d }));
                if (ok) { closeModal(); toast('Enregistré'); this.render(); }
            });
        }
    };
}

views.merchants = crudView({
    title: 'Marchands', path: '/merchants',
    columns: ['Code', 'Nom', 'Ville', 'Adresse'],
    fields: [['code', 'Code', true], ['name', 'Nom', true], ['city', 'Ville'], ['address', 'Adresse']],
    row: m => `<td class="mono">${esc(m.code)}</td><td>${esc(m.name)}</td><td>${esc(m.city)}</td><td>${esc(m.address)}</td>`
});

views.groups = crudView({
    title: 'Groupes de terminaux', path: '/groups',
    columns: ['Nom', 'Description'],
    fields: [['name', 'Nom', true], ['description', 'Description']],
    row: g => `<td>${esc(g.name)}</td><td>${esc(g.description)}</td>`
});

// ------------------------------------------------------------------ boot

if (auth) start(); else showLogin();
