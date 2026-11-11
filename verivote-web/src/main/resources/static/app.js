/**
 * MATDAAN — NATIONAL ELECTRONIC VOTING SYSTEM
 * Sovereign Civic Frontend Engine
 * All data is served by the Java backend (DB + ledger); the browser stores only session tokens.
 * strict electoral validation, one-vote enforcement, and admin custody.
 */

// Registered Parties in strict alphabetical order with NOTA last
const PARTIES = [
  {
    code: 'AAP',
    name: 'Aam Aadmi Party',
    border: '#0F766E',
    symbolSvg: `<svg class="candidate-symbol-svg" viewBox="0 0 40 40" fill="none" aria-label="Broom symbol">
      <path d="M8 32 L22 18" stroke="#101B36" stroke-width="3" stroke-linecap="round"/>
      <path d="M20 20 L34 6 L36 8 L22 22 Z" fill="#8B5A2B"/>
      <path d="M8 32 C6 30 5 35 7 36 C9 37 14 36 12 34 Z" fill="#0F766E"/>
      <path d="M12 28 L6 34 M14 26 L8 36 M16 24 L11 37" stroke="#0F766E" stroke-width="2" stroke-linecap="round"/>
    </svg>`
  },
  {
    code: 'BJP',
    name: 'Bharatiya Janata Party',
    border: '#C2410C',
    symbolSvg: `<svg class="candidate-symbol-svg" viewBox="0 0 40 40" fill="none" aria-label="Lotus symbol">
      <path d="M20 8 C18 16 16 24 20 28 C24 24 22 16 20 8 Z" fill="#C2410C"/>
      <path d="M20 18 C14 18 10 23 12 28 C15 28 18 26 20 24 Z" fill="#F28C28"/>
      <path d="M20 18 C26 18 30 23 28 28 C25 28 22 26 20 24 Z" fill="#F28C28"/>
      <path d="M12 28 C8 26 6 30 10 32 C14 32 17 30 20 28 Z" fill="#C2410C"/>
      <path d="M28 28 C32 26 34 30 30 32 C26 32 23 30 20 28 Z" fill="#C2410C"/>
      <path d="M20 28 L20 36" stroke="#138A59" stroke-width="2.5" stroke-linecap="round"/>
    </svg>`
  },
  {
    code: 'BSP',
    name: 'Bahujan Samaj Party',
    border: '#1D4ED8',
    symbolSvg: `<svg class="candidate-symbol-svg" viewBox="0 0 40 40" fill="none" aria-label="Elephant symbol">
      <path d="M8 26 C8 20 12 14 20 14 C26 14 30 18 32 22 C34 26 31 30 29 30 L29 34 L26 34 L26 28 L22 28 L22 34 L19 34 L19 28 L14 28 L14 34 L11 34 L11 26 Z" fill="#1D4ED8"/>
      <circle cx="26" cy="18" r="1.5" fill="#FFFFFF"/>
      <path d="M31 22 C33 22 35 24 35 28" stroke="#1D4ED8" stroke-width="2" stroke-linecap="round"/>
    </svg>`
  },
  {
    code: 'CPI(M)',
    name: 'Communist Party of India (Marxist)',
    border: '#B91C1C',
    symbolSvg: `<svg class="candidate-symbol-svg" viewBox="0 0 40 40" fill="none" aria-label="Hammer and Sickle symbol">
      <path d="M20 6 L21.5 10.5 L26 10.5 L22.5 13 L24 17.5 L20 15 L16 17.5 L17.5 13 L14 10.5 L18.5 10.5 Z" fill="#B91C1C"/>
      <path d="M12 32 C12 22 20 16 28 18 C28 22 24 28 16 30" stroke="#B91C1C" stroke-width="3" stroke-linecap="round" fill="none"/>
      <line x1="14" y1="20" x2="28" y2="34" stroke="#101B36" stroke-width="3" stroke-linecap="round"/>
      <rect x="22" y="16" width="10" height="5" rx="1" transform="rotate(45 22 16)" fill="#B91C1C"/>
    </svg>`
  },
  {
    code: 'INC',
    name: 'Indian National Congress',
    border: '#047857',
    symbolSvg: `<svg class="candidate-symbol-svg" viewBox="0 0 40 40" fill="none" aria-label="Hand symbol">
      <path d="M14 20 V12 C14 10.5 16 10.5 16 12 V18 M16 12 V8 C16 6.5 18 6.5 18 8 V18 M18 8 V10 C18 8.5 20 8.5 20 10 V18 M20 10 V14 C20 12.5 22 12.5 22 14 V24 C22 28 19 32 15 32 C12 32 11 30 11 27 V23 C11 21 14 20 14 20 Z" stroke="#047857" stroke-width="2.2" stroke-linejoin="round" fill="#E8F5E9"/>
    </svg>`
  },
  {
    code: 'TMC',
    name: 'All India Trinamool Congress',
    border: '#0284C7',
    symbolSvg: `<svg class="candidate-symbol-svg" viewBox="0 0 40 40" fill="none" aria-label="Twin Flowers symbol">
      <circle cx="15" cy="14" r="5" fill="#0284C7"/>
      <circle cx="15" cy="14" r="2" fill="#FFFFFF"/>
      <circle cx="25" cy="12" r="5" fill="#0284C7"/>
      <circle cx="25" cy="12" r="2" fill="#FFFFFF"/>
      <path d="M15 19 C15 24 17 28 20 32 M25 17 C25 23 23 28 20 32" stroke="#047857" stroke-width="2" stroke-linecap="round"/>
      <path d="M12 34 C16 31 24 31 28 34" stroke="#047857" stroke-width="3" stroke-linecap="round"/>
    </svg>`
  },
  {
    code: 'NOTA',
    name: 'None of the Above',
    border: '#6B7280',
    symbolSvg: `<svg class="candidate-symbol-svg" viewBox="0 0 40 40" fill="none" aria-label="NOTA symbol">
      <rect x="8" y="10" width="24" height="20" rx="3" fill="#F8F6F0" stroke="#6B7280" stroke-width="2"/>
      <line x1="12" y1="14" x2="28" y2="26" stroke="#B3261E" stroke-width="3" stroke-linecap="round"/>
      <line x1="28" y1="14" x2="12" y2="26" stroke="#B3261E" stroke-width="3" stroke-linecap="round"/>
    </svg>`
  }
];

// ==========================================================================
// PARTY SYMBOL LOOKUP
// The ballot itself comes from GET /api/candidates (database). PARTIES above
// only supplies artwork; parties without bespoke artwork get a generic badge.
// ==========================================================================

const normCode = (code) => String(code || '').replace(/[^a-z0-9]/gi, '').toLowerCase();
const PARTY_ART = Object.fromEntries(PARTIES.map(p => [normCode(p.code), p]));
const FALLBACK_COLORS = { tdp: '#CA8A04', ysrcp: '#1E40AF', jdu: '#15803D' };

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, ch => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  })[ch]);
}

function partyBorder(candidate) {
  const art = PARTY_ART[normCode(candidate.code)] || PARTY_ART[normCode(candidate.shortName)];
  return art ? art.border : (FALLBACK_COLORS[normCode(candidate.code)] || '#475569');
}

function partySymbolHtml(candidate) {
  const art = PARTY_ART[normCode(candidate.code)] || PARTY_ART[normCode(candidate.shortName)];
  if (art) return art.symbolSvg;
  const label = escapeHtml(String(candidate.shortName || '?').slice(0, 5));
  return `<span class="candidate-symbol-generic" style="background-color:${partyBorder(candidate)}" aria-hidden="true">${label}</span>`;
}

// ==========================================================================
// SESSION STATE (browser keeps only session tokens; all data lives in the DB)
// ==========================================================================

const SESSION_KEYS = {
  VOTER_TOKEN: 'matdaan_voter_session',
  ADMIN_TOKEN: 'matdaan_admin_session',
  RECEIPTS: 'matdaan_receipts'
};

function storageGet(key) {
  try { return localStorage.getItem(key); } catch (err) { return null; }
}

function storageSet(key, value) {
  try {
    if (value === null || value === undefined) localStorage.removeItem(key);
    else localStorage.setItem(key, value);
  } catch (err) { /* storage unavailable (private mode) — session lasts for this page only */ }
}

const memorySession = { voterToken: null, adminToken: null };

function getVoterToken() { return memorySession.voterToken || storageGet(SESSION_KEYS.VOTER_TOKEN); }
function setVoterToken(t) { memorySession.voterToken = t; storageSet(SESSION_KEYS.VOTER_TOKEN, t); }
function getAdminToken() { return memorySession.adminToken || storageGet(SESSION_KEYS.ADMIN_TOKEN); }
function setAdminToken(t) { memorySession.adminToken = t; storageSet(SESSION_KEYS.ADMIN_TOKEN, t); }

// A voter's own receipt is kept on this device so the dashboard can show it again.
function getSavedReceipt(voterId) {
  try {
    const all = JSON.parse(storageGet(SESSION_KEYS.RECEIPTS) || '{}');
    return all[voterId] || null;
  } catch (err) { return null; }
}

function saveReceipt(voterId, receipt) {
  try {
    const all = JSON.parse(storageGet(SESSION_KEYS.RECEIPTS) || '{}');
    all[voterId] = receipt;
    storageSet(SESSION_KEYS.RECEIPTS, JSON.stringify(all));
  } catch (err) { /* ignore */ }
}

let currentVoter = null;       // profile from /api/login or /api/me
let ballotCandidates = [];     // from /api/candidates
let pendingCandidate = null;   // selected on the ballot, awaiting confirmation
let lastAnimatedStats = { registered: -1, votes: -1 };
let adminElectionStatus = null;
let lastReceiptHash = null;

// ==========================================================================
// API CLIENT
// ==========================================================================

class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.status = status;
  }
}

async function api(path, { method = 'GET', body, token } = {}) {
  const headers = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers['Authorization'] = `Bearer ${token}`;

  let resp;
  try {
    resp = await fetch(path, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined
    });
  } catch (netErr) {
    throw new ApiError('Cannot reach the Matdaan server. Check that the backend is running and try again.', 0);
  }

  const contentType = resp.headers.get('content-type') || '';
  const data = contentType.includes('application/json')
    ? await resp.json().catch(() => ({}))
    : await resp.text();

  if (!resp.ok) {
    const msg = (data && data.error) || (typeof data === 'string' && data) || `Request failed (${resp.status}).`;
    throw new ApiError(msg, resp.status);
  }
  return data;
}

// Prevent double submits while a request is in flight.
async function withBusy(button, fn) {
  if (button) {
    if (button.disabled) return;
    button.disabled = true;
    button.setAttribute('aria-busy', 'true');
  }
  try {
    return await fn();
  } finally {
    if (button) {
      button.disabled = false;
      button.removeAttribute('aria-busy');
    }
  }
}

// ==========================================================================
// UI HELPERS
// ==========================================================================

function formatDisplayDateTime(date) {
  return new Intl.DateTimeFormat('en-IN', {
    day: '2-digit', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
    hour12: true
  }).format(date);
}

function showInline(elementId, message) {
  const el = document.getElementById(elementId);
  if (!el) return;
  el.textContent = message;
  el.classList.remove('hidden');
}

function hideInline(elementId) {
  const el = document.getElementById(elementId);
  if (!el) return;
  el.textContent = '';
  el.classList.add('hidden');
}

function flashInline(elementId, message, ms = 5000) {
  showInline(elementId, message);
  clearTimeout(flashInline.timers[elementId]);
  flashInline.timers[elementId] = setTimeout(() => hideInline(elementId), ms);
}
flashInline.timers = {};

let toastTimer = null;
function toast(message, isError = false) {
  const el = document.getElementById('toast');
  if (!el) return;
  el.textContent = message;
  el.classList.toggle('error', isError);
  el.classList.remove('hidden');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.add('hidden'), 4500);
}

function setText(id, value) {
  const el = document.getElementById(id);
  if (el) el.textContent = value;
}

function animateNumber(element, startVal, endVal, duration = 800) {
  if (!element) return;
  if (startVal === endVal || typeof requestAnimationFrame === 'undefined') {
    element.textContent = endVal.toLocaleString('en-IN');
    return;
  }
  const startTime = performance.now();
  function update(now) {
    const progress = Math.min((now - startTime) / duration, 1);
    const eased = 1 - Math.pow(1 - progress, 3);
    element.textContent = Math.floor(startVal + (endVal - startVal) * eased).toLocaleString('en-IN');
    if (progress < 1) requestAnimationFrame(update);
    else element.textContent = endVal.toLocaleString('en-IN');
  }
  requestAnimationFrame(update);
}

// ==========================================================================
// ROUTING
// ==========================================================================

const VALID_VIEWS = ['home', 'register', 'login', 'dashboard', 'vote', 'verify', 'results', 'admin'];
const HOME_ANCHORS = ['how-it-works', 'security-trust', 'election-stats'];

async function switchView(targetView) {
  let viewName = HOME_ANCHORS.includes(targetView) || !VALID_VIEWS.includes(targetView) ? 'home' : targetView;

  // Voter-only views require a live server session.
  if (viewName === 'dashboard' || viewName === 'vote') {
    const voter = await refreshCurrentVoter();
    if (!voter) {
      if (window.location.hash !== '#login') window.location.hash = '#login';
      return;
    }
  }

  VALID_VIEWS.forEach(v => {
    const section = document.getElementById(`view-${v}`);
    const navLink = document.getElementById(`nav-${v}`);
    if (section) section.classList.toggle('active-view', v === viewName);
    if (navLink) {
      navLink.classList.toggle('active', v === viewName);
      if (v === viewName) navLink.setAttribute('aria-current', 'page');
      else navLink.removeAttribute('aria-current');
    }
  });

  if (HOME_ANCHORS.includes(targetView)) {
    setTimeout(() => {
      const anchorEl = document.getElementById(targetView);
      if (anchorEl) anchorEl.scrollIntoView({ behavior: 'smooth' });
    }, 100);
  } else {
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  updateNavForSession();

  if (viewName === 'home') updateHomeMetrics();
  else if (viewName === 'dashboard') renderVoterDashboard();
  else if (viewName === 'vote') renderVotingBooth();
  else if (viewName === 'admin') renderAdminPortal();
  else if (viewName === 'verify') prefillVerify();
  else if (viewName === 'results') renderResultsView({ autoCelebrate: true });
}

function handleHashChange() {
  const rawHash = window.location.hash.replace('#', '').trim();
  switchView(rawHash);

  const drawer = document.getElementById('mobile-drawer');
  const toggle = document.getElementById('btn-mobile-menu');
  if (drawer && drawer.classList.contains('open')) {
    drawer.classList.remove('open');
    drawer.setAttribute('aria-hidden', 'true');
    if (toggle) toggle.setAttribute('aria-expanded', 'false');
  }
}

// Header "Sign In" becomes "My Dashboard" once a voter is signed in.
function updateNavForSession() {
  const loginLink = document.getElementById('nav-login');
  if (!loginLink) return;
  if (currentVoter) {
    loginLink.textContent = 'My Dashboard';
    loginLink.setAttribute('href', '#dashboard');
  } else {
    loginLink.textContent = 'Sign In';
    loginLink.setAttribute('href', '#login');
  }
}

// ==========================================================================
// VOTER SESSION
// ==========================================================================

async function refreshCurrentVoter() {
  const token = getVoterToken();
  if (!token) {
    currentVoter = null;
    return null;
  }
  try {
    const me = await api('/api/me', { token });
    currentVoter = { ...me, sessionToken: token };
    return currentVoter;
  } catch (err) {
    if (err.status === 401) {
      setVoterToken(null);
      currentVoter = null;
      toast('Your session has ended. Please sign in again.');
      return null;
    }
    // Server unreachable: keep the cached profile but surface the problem.
    toast(err.message, true);
    return currentVoter;
  }
}

async function signOutVoter() {
  const token = getVoterToken();
  setVoterToken(null);
  currentVoter = null;
  updateNavForSession();
  if (token) {
    try { await api('/api/logout', { method: 'POST', token }); } catch (err) { /* already signed out locally */ }
  }
}

// ==========================================================================
// VIEW: HOME METRICS
// ==========================================================================

async function updateHomeMetrics() {
  try {
    const data = await api('/api/metrics');
    const registered = Number(data.registered) || 0;
    const votes = Number(data.votes) || 0;

    const regEl = document.getElementById('home-registered-count');
    const votesEl = document.getElementById('home-votes-count');
    animateNumber(regEl, Math.max(lastAnimatedStats.registered, 0), registered);
    animateNumber(votesEl, Math.max(lastAnimatedStats.votes, 0), votes);
    lastAnimatedStats = { registered, votes };
    setText('home-turnout-pct', `${Number(data.turnout || 0).toFixed(1)}%`);
  } catch (err) {
    console.warn('Metrics unavailable:', err.message);
  }
}

// ==========================================================================
// VIEW: REGISTRATION
// ==========================================================================

function initRegisterView() {
  const form = document.getElementById('registration-form');
  if (form) form.addEventListener('submit', handleRegistrationSubmit);

  const proceedBtn = document.getElementById('btn-proceed-to-login');
  if (proceedBtn) proceedBtn.addEventListener('click', () => { window.location.hash = '#login'; });

  const anotherBtn = document.getElementById('btn-register-another');
  if (anotherBtn) anotherBtn.addEventListener('click', resetRegistrationForm);
}

async function handleRegistrationSubmit(e) {
  e.preventDefault();
  hideInline('register-error');

  const nameInput = document.getElementById('reg-name');
  const ageInput = document.getElementById('reg-age');
  const stateInput = document.getElementById('reg-state');
  const pinInput = document.getElementById('reg-pin');

  const fullName = nameInput.value.trim();
  const age = parseInt(ageInput.value.trim(), 10);
  const state = stateInput.value.trim();
  const pin = pinInput.value.trim();

  if (fullName.length < 2) {
    showInline('register-error', 'Please enter your full legal name as it appears on official records.');
    return nameInput.focus();
  }
  if (isNaN(age)) {
    showInline('register-error', 'Please enter your age in years.');
    return ageInput.focus();
  }
  if (age < 18) {
    showInline('register-error', 'Registration declined: You must be at least 18 years of age to register as an elector (Article 326 of the Constitution of India).');
    return ageInput.focus();
  }
  if (age > 120) {
    showInline('register-error', 'Please enter a valid age between 18 and 120.');
    return ageInput.focus();
  }
  if (!state) {
    showInline('register-error', 'Please select your State or Union Territory from the list.');
    return stateInput.focus();
  }
  if (!/^\d{4}$/.test(pin)) {
    showInline('register-error', 'PIN must be exactly 4 numeric digits (e.g. 4821).');
    return pinInput.focus();
  }

  const submitBtn = e.target.querySelector('button[type="submit"]');
  await withBusy(submitBtn, async () => {
    try {
      const data = await api('/api/register', {
        method: 'POST',
        body: { name: fullName, age, state, pin }
      });

      setText('success-voter-id', data.voterId);
      setText('success-voter-name', data.name);
      setText('success-voter-age', `${data.age} years`);
      setText('success-voter-state', data.state);

      document.getElementById('register-form-container').classList.add('hidden');
      document.getElementById('register-success-card').classList.remove('hidden');

      const loginIdInput = document.getElementById('login-voter-id');
      if (loginIdInput) loginIdInput.value = data.voterId;
      updateHomeMetrics();
    } catch (err) {
      showInline('register-error', err.message);
    }
  });
}

function resetRegistrationForm() {
  const form = document.getElementById('registration-form');
  if (form) form.reset();
  hideInline('register-error');
  document.getElementById('register-success-card').classList.add('hidden');
  document.getElementById('register-form-container').classList.remove('hidden');
  const nameInput = document.getElementById('reg-name');
  if (nameInput && document.getElementById('view-register').classList.contains('active-view')) nameInput.focus();
}

// ==========================================================================
// VIEW: VOTER LOGIN
// ==========================================================================

function initLoginView() {
  const form = document.getElementById('voter-login-form');
  if (!form) return;

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    hideInline('voter-login-error');

    const idInput = document.getElementById('login-voter-id');
    const pinInput = document.getElementById('login-pin');
    const voterId = idInput.value.trim().toUpperCase();
    const pin = pinInput.value.trim();

    if (!voterId) {
      showInline('voter-login-error', 'Please enter your Voter ID (e.g. VTR482910).');
      return idInput.focus();
    }
    if (!/^\d{4}$/.test(pin)) {
      showInline('voter-login-error', 'Please enter your 4-digit security PIN.');
      return pinInput.focus();
    }

    const submitBtn = form.querySelector('button[type="submit"]');
    await withBusy(submitBtn, async () => {
      try {
        const data = await api('/api/login', { method: 'POST', body: { voterId, pin } });
        setVoterToken(data.sessionToken);
        currentVoter = data;
        form.reset();
        window.location.hash = '#dashboard';
      } catch (err) {
        showInline('voter-login-error', err.message);
        pinInput.focus();
      }
    });
  });
}

// ==========================================================================
// VIEW: VOTER DASHBOARD
// ==========================================================================

function renderVoterDashboard() {
  const voter = currentVoter;
  if (!voter) return;

  const name = voter.name || 'Elector';
  const initials = name.split(/\s+/).filter(Boolean).map(n => n[0]).slice(0, 2).join('').toUpperCase();
  setText('profile-avatar-initials', initials || 'VI');
  setText('profile-name', name);
  setText('profile-voter-id', voter.voterId);
  setText('profile-age', voter.age ? `${voter.age} years` : '—');
  setText('profile-state', voter.state || '—');

  const statusBadge = document.getElementById('profile-status-badge');
  const voteStatusText = document.getElementById('profile-vote-status');
  const unvotedBox = document.getElementById('dash-unvoted-actions');
  const votedBox = document.getElementById('dash-voted-notice');

  if (voter.hasVoted) {
    statusBadge.style.backgroundColor = 'var(--color-emerald-soft)';
    statusBadge.style.color = 'var(--color-emerald-deep)';
    statusBadge.textContent = 'Ballot Cast (Voted)';
    voteStatusText.textContent = 'Completed (One vote recorded)';
    unvotedBox.classList.add('hidden');
    votedBox.classList.remove('hidden');

    const receipt = getSavedReceipt(voter.voterId);
    setText('dash-voted-timestamp', receipt && receipt.timestamp
      ? `Vote committed on: ${receipt.timestamp}`
      : 'Your vote is recorded on the electoral ledger.');
    const receiptLine = document.getElementById('dash-receipt-line');
    if (receipt && receipt.receiptHash) {
      setText('dash-receipt-hash', receipt.receiptHash);
      receiptLine.classList.remove('hidden');
      lastReceiptHash = receipt.receiptHash;
    } else {
      receiptLine.classList.add('hidden');
    }
  } else {
    statusBadge.style.backgroundColor = 'var(--color-saffron-soft)';
    statusBadge.style.color = 'var(--color-saffron-deep)';
    statusBadge.textContent = 'Eligible to Vote';
    voteStatusText.textContent = 'Eligible (Uncast)';
    unvotedBox.classList.remove('hidden');
    votedBox.classList.add('hidden');
  }
}

function initVoterDashboard() {
  const logoutBtn = document.getElementById('btn-voter-logout');
  if (logoutBtn) {
    logoutBtn.addEventListener('click', async () => {
      await signOutVoter();
      toast('You have been signed out.');
      window.location.hash = '#login';
    });
  }
}

// ==========================================================================
// VIEW: VOTING BOOTH
// ==========================================================================

function initVotingBooth() {
  const container = document.getElementById('candidate-options-list');
  if (container) {
    container.addEventListener('change', (e) => {
      if (e.target && e.target.name === 'selectedParty') {
        container.querySelectorAll('.candidate-card').forEach(c => c.classList.remove('selected'));
        const card = e.target.closest('.candidate-card');
        if (card) card.classList.add('selected');
        hideInline('ballot-error');
      }
    });
  }

  const ballotForm = document.getElementById('ballot-form');
  if (ballotForm) ballotForm.addEventListener('submit', handleBallotSubmit);

  const confirmBtn = document.getElementById('btn-final-cast-vote');
  if (confirmBtn) confirmBtn.addEventListener('click', () => withBusy(confirmBtn, handleConfirmCastVote));

  const backBtn = document.getElementById('btn-back-to-ballot');
  if (backBtn) {
    backBtn.addEventListener('click', () => {
      document.getElementById('vote-confirm-container').classList.add('hidden');
      document.getElementById('ballot-card-container').classList.remove('hidden');
    });
  }

  const copyBtn = document.getElementById('btn-copy-receipt');
  if (copyBtn) {
    copyBtn.addEventListener('click', async () => {
      if (!lastReceiptHash) return;
      try {
        await navigator.clipboard.writeText(lastReceiptHash);
        toast('Receipt copied to clipboard.');
      } catch (err) {
        toast('Copy failed — select the receipt text and copy it manually.', true);
      }
    });
  }

  const verifyBtn = document.getElementById('btn-verify-my-receipt');
  if (verifyBtn) verifyBtn.addEventListener('click', () => { window.location.hash = '#verify'; });
}

async function renderVotingBooth() {
  const voter = currentVoter;
  if (!voter) return;

  // Keep the receipt screen visible right after voting.
  const successBox = document.getElementById('vote-success-container');
  if (voter.hasVoted && successBox && !successBox.classList.contains('hidden')) return;

  if (voter.hasVoted) {
    toast('You have already voted. Only one vote per registered elector is permitted.');
    window.location.hash = '#dashboard';
    return;
  }

  setText('booth-voter-name', voter.name);
  setText('booth-voter-id', voter.voterId);
  setText('booth-voter-state', voter.state || '—');
  hideInline('ballot-error');

  document.getElementById('ballot-card-container').classList.remove('hidden');
  document.getElementById('vote-confirm-container').classList.add('hidden');
  successBox.classList.add('hidden');
  pendingCandidate = null;

  try {
    const data = await api('/api/candidates');
    ballotCandidates = data.candidates || [];
    renderBallotCards();
    if (data.electionStatus && data.electionStatus !== 'OPEN') {
      showInline('ballot-error', 'Polling is currently CLOSED by the Returning Officer. Ballots cannot be cast right now.');
    }
  } catch (err) {
    ballotCandidates = [];
    renderBallotCards();
    showInline('ballot-error', `Could not load the ballot: ${err.message}`);
  }
}

function renderBallotCards() {
  const container = document.getElementById('candidate-options-list');
  if (!container) return;
  container.innerHTML = '';

  ballotCandidates.forEach(c => {
    const card = document.createElement('label');
    card.className = 'candidate-card';
    card.style.borderLeft = `6px solid ${partyBorder(c)}`;
    card.setAttribute('for', `party-opt-${c.id}`);

    const radio = document.createElement('input');
    radio.type = 'radio';
    radio.id = `party-opt-${c.id}`;
    radio.name = 'selectedParty';
    radio.value = String(c.id);
    radio.className = 'candidate-radio';

    const symbolBox = document.createElement('div');
    symbolBox.className = 'candidate-symbol-box';
    symbolBox.innerHTML = partySymbolHtml(c);

    const info = document.createElement('div');
    info.className = 'candidate-info';
    const codeSpan = document.createElement('span');
    codeSpan.className = 'candidate-code';
    codeSpan.textContent = c.shortName;
    const nameSpan = document.createElement('span');
    nameSpan.className = 'candidate-name';
    nameSpan.textContent = c.party;
    info.append(codeSpan, nameSpan);

    card.append(radio, symbolBox, info);
    container.appendChild(card);
  });
}

function handleBallotSubmit(e) {
  e.preventDefault();
  hideInline('ballot-error');

  const selected = document.querySelector('input[name="selectedParty"]:checked');
  if (!selected) {
    showInline('ballot-error', 'Please select a candidate or NOTA before tapping "Cast Vote".');
    return;
  }
  const candidate = ballotCandidates.find(c => String(c.id) === selected.value);
  if (!candidate) return;
  pendingCandidate = candidate;

  setText('confirm-party-code', candidate.shortName);
  setText('confirm-party-name', candidate.party);
  document.getElementById('confirm-party-symbol').innerHTML = partySymbolHtml(candidate);

  document.getElementById('ballot-card-container').classList.add('hidden');
  document.getElementById('vote-confirm-container').classList.remove('hidden');
}

async function handleConfirmCastVote() {
  if (!currentVoter || !pendingCandidate) {
    window.location.hash = '#login';
    return;
  }

  try {
    const data = await api('/api/vote', {
      method: 'POST',
      token: getVoterToken(),
      body: { candidateId: pendingCandidate.id }
    });

    currentVoter.hasVoted = true;
    lastReceiptHash = data.receiptHash;
    saveReceipt(currentVoter.voterId, {
      receiptHash: data.receiptHash,
      blockHeight: data.blockHeight,
      timestamp: data.timestamp
    });

    setText('receipt-voter-id', currentVoter.voterId);
    setText('receipt-timestamp', data.timestamp || formatDisplayDateTime(new Date()));
    setText('receipt-hash', data.receiptHash);
    setText('receipt-block-height', data.blockHeight);

    document.getElementById('vote-confirm-container').classList.add('hidden');
    document.getElementById('vote-success-container').classList.remove('hidden');
    pendingCandidate = null;
    updateHomeMetrics();
  } catch (err) {
    if (err.status === 401) {
      await signOutVoter();
      toast(err.message, true);
      window.location.hash = '#login';
      return;
    }
    document.getElementById('vote-confirm-container').classList.add('hidden');
    document.getElementById('ballot-card-container').classList.remove('hidden');
    showInline('ballot-error', err.message);
    if (err.status === 409) {
      await refreshCurrentVoter();
    }
  }
}

// ==========================================================================
// VIEW: RECEIPT VERIFICATION (public bulletin board)
// ==========================================================================

function initVerifyView() {
  const form = document.getElementById('verify-form');
  if (!form) return;

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    hideInline('verify-error');
    const input = document.getElementById('verify-receipt-input');
    const receipt = input.value.trim().toLowerCase();

    if (!/^[0-9a-f]{64}$/.test(receipt)) {
      document.getElementById('verify-result').classList.add('hidden');
      showInline('verify-error', 'A receipt is 64 hexadecimal characters (0–9, a–f). Copy it exactly from your vote confirmation.');
      return input.focus();
    }

    const submitBtn = form.querySelector('button[type="submit"]');
    await withBusy(submitBtn, async () => {
      let data;
      try {
        data = await api(`/bb/verify/${receipt}`);
      } catch (err) {
        if (err.status === 404) data = { status: 'NOT_FOUND' };
        else {
          showInline('verify-error', err.message);
          return;
        }
      }
      renderVerifyResult(data);
    });
  });
}

function renderVerifyResult(data) {
  const box = document.getElementById('verify-result');
  const badge = document.getElementById('verify-status-badge');
  const states = {
    OK: ['voted', 'Included', 'Receipt found and included in a signed checkpoint',
      'Your encrypted ballot is in the append-only ledger, and the Merkle inclusion proof below ties it to a published checkpoint.'],
    PENDING_INCLUSION: ['eligible', 'Pending', 'Receipt found — awaiting next checkpoint',
      'Your ballot is in the ledger. It will be covered by the next signed checkpoint within a few seconds; check again shortly.'],
    NOT_FOUND: ['eligible', 'Not found', 'No ballot with this receipt',
      'Check that you copied the full receipt. If the demo was reset, earlier receipts are no longer on the bulletin board.']
  };
  const [cls, label, title, desc] = states[data.status] || states.NOT_FOUND;
  badge.className = `badge-table ${cls}`;
  badge.textContent = label;
  setText('verify-status-title', title);
  setText('verify-status-desc', desc);
  setText('verify-block', data.index ?? '—');
  setText('verify-tree', data.treeSize ?? '—');
  setText('verify-leaf', data.leafData || '—');
  setText('verify-proof-count', Array.isArray(data.proof) && data.status === 'OK' ? `${data.proof.length} sibling hashes` : '—');
  box.classList.remove('hidden');
}

function prefillVerify() {
  const input = document.getElementById('verify-receipt-input');
  if (input && !input.value && lastReceiptHash) input.value = lastReceiptHash;
}

// ==========================================================================
// VIEW: OFFICER (ADMIN) PORTAL
// ==========================================================================

function adminFail(err) {
  if (err.status === 403 || err.status === 401) {
    setAdminToken(null);
    toast('Officer session expired. Please sign in again.', true);
    renderAdminPortal();
    return true;
  }
  showInline('admin-action-error', err.message);
  setTimeout(() => hideInline('admin-action-error'), 6000);
  return false;
}

function initAdminPortal() {
  const loginForm = document.getElementById('admin-login-form');
  if (loginForm) {
    loginForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      hideInline('admin-login-error');
      const username = document.getElementById('admin-username').value.trim();
      const password = document.getElementById('admin-password').value;
      if (!username || !password) {
        showInline('admin-login-error', 'Enter the officer username and passcode.');
        return;
      }
      const submitBtn = loginForm.querySelector('button[type="submit"]');
      await withBusy(submitBtn, async () => {
        try {
          const data = await api('/api/admin/login', { method: 'POST', body: { username, password } });
          setAdminToken(data.token);
          loginForm.reset();
          renderAdminPortal();
        } catch (err) {
          showInline('admin-login-error', err.status === 401
            ? 'Invalid officer credentials. (Classroom demo: username "admin", password "admin123".)'
            : err.message);
        }
      });
    });
  }

  const logoutBtn = document.getElementById('btn-admin-logout');
  if (logoutBtn) {
    logoutBtn.addEventListener('click', async () => {
      const token = getAdminToken();
      setAdminToken(null);
      renderAdminPortal();
      if (token) {
        try { await api('/api/admin/logout', { method: 'POST', token }); } catch (err) { /* ignore */ }
      }
    });
  }

  const sampleBtn = document.getElementById('btn-load-sample-votes');
  if (sampleBtn) {
    sampleBtn.addEventListener('click', () => withBusy(sampleBtn, async () => {
      sampleBtn.textContent = 'Encrypting & casting…';
      try {
        const data = await api('/api/admin/sample', { method: 'POST', token: getAdminToken() });
        flashInline('admin-sample-message', data.message);
        await loadAdminData();
        updateHomeMetrics();
      } catch (err) {
        adminFail(err);
      } finally {
        sampleBtn.textContent = 'Load 14 Sample Votes';
      }
    }));
  }

  const toggleBtn = document.getElementById('btn-toggle-election');
  if (toggleBtn) {
    toggleBtn.addEventListener('click', () => withBusy(toggleBtn, async () => {
      const next = adminElectionStatus === 'OPEN' ? 'CLOSED' : 'OPEN';
      const verb = next === 'CLOSED'
        ? 'close polling? Voters will no longer be able to cast ballots.'
        : adminElectionStatus === 'TALLIED'
          ? 'reopen polling? This withdraws the published result until you publish again.'
          : 'reopen polling for voters?';
      if (!window.confirm(`Are you sure you want to ${verb}`)) return;
      try {
        const data = await api('/api/admin/election-status', {
          method: 'POST', token: getAdminToken(), body: { status: next }
        });
        flashInline('admin-action-message', `Election is now ${data.electionStatus}.`);
        await loadAdminData();
      } catch (err) {
        adminFail(err);
      }
    }));
  }

  const exportBtn = document.getElementById('btn-export-votes');
  if (exportBtn) {
    exportBtn.addEventListener('click', () => withBusy(exportBtn, async () => {
      try {
        const csv = await api('/api/admin/export/votes', { token: getAdminToken() });
        const blob = new Blob([csv], { type: 'text/csv' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `matdaan-ledger-${new Date().toISOString().slice(0, 10)}.csv`;
        document.body.appendChild(a);
        a.click();
        a.remove();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
        flashInline('admin-action-message', 'Ledger CSV downloaded (receipt and block hashes only — no voter identities).');
      } catch (err) {
        adminFail(err);
      }
    }));
  }

  const publishBtn = document.getElementById('btn-publish-results');
  if (publishBtn) {
    publishBtn.addEventListener('click', () => withBusy(publishBtn, async () => {
      try {
        let results;
        if (adminElectionStatus === 'TALLIED') {
          results = await api('/api/results');
        } else {
          if (!window.confirm('Publish the result now? Polling will close and the decrypted tally becomes public.')) return;
          results = await api('/api/admin/publish', { method: 'POST', token: getAdminToken() });
          flashInline('admin-action-message', 'Result published. It is now visible to everyone on the Results page.');
        }
        await loadAdminData();
        playCelebration(results);
      } catch (err) {
        adminFail(err);
      }
    }));
  }

  const refreshBtn = document.getElementById('btn-admin-refresh');
  if (refreshBtn) refreshBtn.addEventListener('click', () => withBusy(refreshBtn, loadAdminData));

  const resetBtn = document.getElementById('btn-admin-reset');
  if (resetBtn) resetBtn.addEventListener('click', () => withBusy(resetBtn, resetDemoData));
}

function renderAdminPortal() {
  const signedIn = Boolean(getAdminToken());
  document.getElementById('admin-login-container').classList.toggle('hidden', signedIn);
  document.getElementById('admin-dashboard-container').classList.toggle('hidden', !signedIn);
  const pill = document.getElementById('admin-status-pill');
  if (pill) pill.classList.toggle('hidden', !signedIn);
  if (signedIn) loadAdminData();
}

let adminLoadInFlight = false;
async function loadAdminData() {
  const token = getAdminToken();
  if (!token || adminLoadInFlight) return;
  adminLoadInFlight = true;
  try {
    const data = await api('/api/admin/tally', { token });
    renderAdminData(data);
  } catch (err) {
    adminFail(err);
  } finally {
    adminLoadInFlight = false;
  }
}

function renderAdminData(data) {
  const registered = Number(data.registered) || 0;
  const votes = Number(data.votes) || 0;
  setText('admin-registered-count', registered.toLocaleString('en-IN'));
  setText('admin-votes-count', votes.toLocaleString('en-IN'));
  setText('admin-turnout-pct', `${Number(data.turnout || 0).toFixed(1)}%`);

  adminElectionStatus = data.electionStatus;
  const chip = document.getElementById('admin-election-status');
  chip.textContent = data.electionStatus || '—';
  const statusClass = { OPEN: 'open', TALLIED: 'tallied' }[data.electionStatus] || 'closed';
  chip.className = `election-status-chip ${statusClass}`;
  if (data.electionStatus === 'TALLIED') chip.textContent = 'RESULT PUBLISHED';
  setText('btn-toggle-election', data.electionStatus === 'OPEN' ? 'Close Polling' : 'Reopen Polling');
  setText('btn-publish-results', data.electionStatus === 'TALLIED' ? 'Replay Result' : 'Publish Result');

  const undecryptable = Number(data.undecryptable) || 0;
  if (undecryptable > 0) {
    showInline('admin-undecryptable-note', `${undecryptable} ballot(s) were encrypted under an earlier tally key and cannot be counted. Reset the election data to start a clean count.`);
  } else {
    hideInline('admin-undecryptable-note');
  }

  const tally = Array.isArray(data.tally) ? data.tally : [];
  const counted = tally.reduce((sum, row) => sum + (Number(row.votes) || 0), 0);
  const bars = document.getElementById('admin-results-bars-container');
  bars.innerHTML = '';
  tally.forEach(row => {
    const partyVotes = Number(row.votes) || 0;
    const pct = counted > 0 ? ((partyVotes / counted) * 100).toFixed(1) : '0.0';
    const el = document.createElement('div');
    el.className = 'result-row';
    el.innerHTML = `
      <div class="result-row-header">
        <div class="result-party-info">
          <div class="result-party-symbol">${partySymbolHtml(row)}</div>
          <div>
            <span class="result-party-code">${escapeHtml(row.shortName)}</span>
            <span class="result-party-name">${escapeHtml(row.party)}</span>
          </div>
        </div>
        <span class="result-tally">${partyVotes} ${partyVotes === 1 ? 'vote' : 'votes'} (${pct}%)</span>
      </div>
      <div class="result-bar-track">
        <div class="result-bar-fill" style="width: ${pct}%; background: ${partyBorder(row)};"
             role="progressbar" aria-valuenow="${pct}" aria-valuemin="0" aria-valuemax="100"
             aria-label="${escapeHtml(row.shortName)} vote share"></div>
      </div>`;
    bars.appendChild(el);
  });

  const tbody = document.getElementById('admin-voters-tbody');
  const voters = Array.isArray(data.voters) ? data.voters : [];
  tbody.innerHTML = '';
  if (voters.length === 0) {
    tbody.innerHTML = '<tr><td colspan="6" style="text-align: center; color: var(--color-ink-muted); padding: 24px;">No electors enrolled on the register yet.</td></tr>';
    return;
  }
  voters.forEach(v => {
    const tr = document.createElement('tr');
    const reg = v.registeredAt ? formatDisplayDateTime(new Date(v.registeredAt)) : '—';
    tr.innerHTML = `
      <td><strong class="font-mono">${escapeHtml(v.id)}</strong></td>
      <td>${escapeHtml(v.name)}</td>
      <td>${escapeHtml(v.age ?? '—')}</td>
      <td>${escapeHtml(v.state ?? '—')}</td>
      <td>${escapeHtml(reg)}</td>
      <td><span class="badge-table ${v.hasVoted ? 'voted' : 'eligible'}">${v.hasVoted ? 'Voted' : 'Eligible'}</span></td>`;
    tbody.appendChild(tr);
  });
}

// ==========================================================================
// VIEW: PUBLISHED RESULTS
// ==========================================================================

const isLotusParty = (row) => row && normCode(row.code) === 'bjp';

function resultHeadline(results) {
  const w = results.winner;
  const total = Number(results.totalVotes) || 0;
  if (w) {
    const pct = total > 0 ? ((Number(w.votes) / total) * 100).toFixed(1) : '0.0';
    return {
      kicker: 'RESULT DECLARED • WINNER',
      title: `${w.party} (${w.shortName}) wins`,
      sub: `${Number(w.votes).toLocaleString('en-IN')} of ${total.toLocaleString('en-IN')} votes • ${pct}% vote share`
    };
  }
  if (results.tie) {
    return { kicker: 'RESULT DECLARED', title: 'The contest is tied', sub: 'Two or more parties received the same highest number of votes.' };
  }
  return { kicker: 'RESULT DECLARED', title: 'No winner declared', sub: 'No valid votes were cast for any party.' };
}

let resultsCelebratedFor = null;

async function renderResultsView({ autoCelebrate = false } = {}) {
  let results;
  try {
    results = await api('/api/results');
  } catch (err) {
    setText('results-pending-desc', err.message);
    return;
  }

  const pending = document.getElementById('results-pending');
  const published = document.getElementById('results-published');
  if (!results.published) {
    pending.classList.remove('hidden');
    published.classList.add('hidden');
    setText('results-pending-desc', results.electionStatus === 'OPEN'
      ? 'Polling is open. Candidate tallies stay sealed until the Returning Officer publishes the result.'
      : 'Polling has closed. The Returning Officer has not published the result yet.');
    return;
  }

  pending.classList.add('hidden');
  published.classList.remove('hidden');

  const head = resultHeadline(results);
  const w = results.winner;
  setText('results-winner-tag', w ? 'WINNER' : 'RESULT');
  setText('results-winner-name', head.title);
  setText('results-winner-meta', head.sub);
  const card = document.getElementById('results-winner-card');
  card.style.setProperty('--winner-color', w ? partyBorder(w) : 'var(--color-ink-muted)');
  const symbol = document.getElementById('results-winner-symbol');
  symbol.innerHTML = w
    ? (isLotusParty(w) ? '<img src="images/modi.jpg" alt="">' : partySymbolHtml(w))
    : '';
  document.getElementById('btn-replay-celebration').classList.toggle('hidden', !w);

  const total = Number(results.totalVotes) || 0;
  const bars = document.getElementById('results-bars-container');
  bars.innerHTML = '';
  [...results.results]
    .sort((a, b) => Number(b.votes) - Number(a.votes))
    .forEach(row => {
      const votes = Number(row.votes) || 0;
      const pct = total > 0 ? ((votes / total) * 100).toFixed(1) : '0.0';
      const el = document.createElement('div');
      el.className = `result-row${w && row.id === w.id ? ' is-winner' : ''}`;
      el.innerHTML = `
        <div class="result-row-header">
          <div class="result-party-info">
            <div class="result-party-symbol">${partySymbolHtml(row)}</div>
            <div>
              <span class="result-party-code">${escapeHtml(row.shortName)}</span>
              <span class="result-party-name">${escapeHtml(row.party)}</span>
            </div>
          </div>
          <span class="result-tally">${votes.toLocaleString('en-IN')} (${pct}%)</span>
        </div>
        <div class="result-bar-track">
          <div class="result-bar-fill" style="width: ${pct}%; background: ${partyBorder(row)};"
               role="progressbar" aria-valuenow="${pct}" aria-valuemin="0" aria-valuemax="100"
               aria-label="${escapeHtml(row.shortName)} vote share"></div>
        </div>`;
      bars.appendChild(el);
    });

  const when = results.publishedAt ? formatDisplayDateTime(new Date(results.publishedAt)) : null;
  setText('results-footnote', `${total.toLocaleString('en-IN')} valid votes counted from encrypted ballots` +
    (when ? ` • Published ${when}` : '') + ' • NOTA is shown but cannot win.');

  // Celebrate once per publication when a visitor opens the Results page.
  if (autoCelebrate && w && resultsCelebratedFor !== results.publishedAt) {
    resultsCelebratedFor = results.publishedAt;
    playCelebration(results);
  }
  lastPublishedResults = results;
}

let lastPublishedResults = null;

// ==========================================================================
// RESULT CELEBRATION: lotus blooms -> portrait rises -> audio plays
// ==========================================================================

const celebrationTimers = [];

function closeCelebration() {
  const overlay = document.getElementById('celebration');
  const audio = document.getElementById('celebration-audio');
  celebrationTimers.splice(0).forEach(clearTimeout);
  overlay.classList.add('hidden');
  overlay.classList.remove('is-revealed');
  if (audio) {
    audio.pause();
    audio.currentTime = 0;
  }
  document.removeEventListener('keydown', celebrationKeydown);
}

function celebrationKeydown(e) {
  if (e.key === 'Escape') closeCelebration();
}

function playCelebrationAudio() {
  const audio = document.getElementById('celebration-audio');
  const soundBtn = document.getElementById('btn-celebration-sound');
  if (!audio) return;
  audio.currentTime = 0;
  const attempt = audio.play();
  if (attempt && typeof attempt.catch === 'function') {
    attempt.then(() => soundBtn.classList.add('hidden'))
      .catch(() => soundBtn.classList.remove('hidden')); // autoplay blocked: offer a tap
  }
}

function playCelebration(results) {
  const overlay = document.getElementById('celebration');
  const lotus = document.getElementById('celebration-lotus');
  const portrait = document.getElementById('celebration-portrait');
  const symbol = document.getElementById('celebration-symbol');
  const soundBtn = document.getElementById('btn-celebration-sound');
  if (!overlay) return;

  closeCelebration();
  const head = resultHeadline(results);
  setText('celebration-kicker', head.kicker);
  setText('celebration-title', head.title);
  setText('celebration-sub', head.sub);
  soundBtn.classList.add('hidden');

  const lotusWinner = isLotusParty(results.winner);
  lotus.classList.toggle('hidden', !lotusWinner);
  portrait.classList.toggle('hidden', !lotusWinner);
  symbol.classList.toggle('hidden', lotusWinner || !results.winner);
  symbol.innerHTML = !lotusWinner && results.winner ? partySymbolHtml(results.winner) : '';

  // Restart the SVG petal animations by re-inserting the lotus node.
  if (lotusWinner) lotus.replaceWith(lotus.cloneNode(true));

  overlay.classList.remove('hidden');
  document.addEventListener('keydown', celebrationKeydown);
  document.getElementById('btn-close-celebration').focus();

  const reduced = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  // Lotus petals finish unfolding ~1.7s in; then the portrait rises and the audio starts.
  const revealDelay = lotusWinner && !reduced ? 1700 : 200;
  celebrationTimers.push(setTimeout(() => {
    overlay.classList.add('is-revealed');
    if (lotusWinner) playCelebrationAudio();
  }, revealDelay));
}

function initCelebration() {
  document.getElementById('btn-close-celebration').addEventListener('click', closeCelebration);
  document.getElementById('celebration').addEventListener('click', (e) => {
    if (e.target.id === 'celebration') closeCelebration();
  });
  document.getElementById('btn-celebration-sound').addEventListener('click', playCelebrationAudio);
  document.getElementById('btn-replay-celebration').addEventListener('click', () => {
    if (lastPublishedResults) playCelebration(lastPublishedResults);
  });
}

// ==========================================================================
// RESET DEMO (officer-only on the server)
// ==========================================================================

async function resetDemoData() {
  const token = getAdminToken();
  if (!token) {
    toast('Only a signed-in Election Officer can reset demonstration data.', true);
    window.location.hash = '#admin';
    return;
  }
  if (!window.confirm('Reset all demo data? This deletes every registered elector and recorded ballot from the database.')) {
    return;
  }
  try {
    const data = await api('/api/admin/reset', { method: 'POST', token });
    setVoterToken(null);
    currentVoter = null;
    lastReceiptHash = null;
    storageSet(SESSION_KEYS.RECEIPTS, null);
    resetRegistrationForm();
    lastAnimatedStats = { registered: -1, votes: -1 };
    updateNavForSession();
    updateHomeMetrics();
    toast(data.message);
    if (window.location.hash === '#admin') loadAdminData();
  } catch (err) {
    adminFail(err);
  }
}

function initResetDemo() {
  const resetBtn = document.getElementById('btn-reset-demo');
  if (resetBtn) resetBtn.addEventListener('click', () => withBusy(resetBtn, resetDemoData));
}

// ==========================================================================
// MOBILE MENU
// ==========================================================================

function initMobileMenu() {
  const toggleBtn = document.getElementById('btn-mobile-menu');
  const drawer = document.getElementById('mobile-drawer');
  if (!toggleBtn || !drawer) return;

  toggleBtn.addEventListener('click', () => {
    const isOpen = drawer.classList.toggle('open');
    toggleBtn.setAttribute('aria-expanded', String(isOpen));
    drawer.setAttribute('aria-hidden', String(!isOpen));
  });

  drawer.querySelectorAll('.drawer-link').forEach(link => {
    link.addEventListener('click', () => {
      drawer.classList.remove('open');
      drawer.setAttribute('aria-hidden', 'true');
      toggleBtn.setAttribute('aria-expanded', 'false');
    });
  });
}

// ==========================================================================
// INITIALIZATION
// ==========================================================================

document.addEventListener('DOMContentLoaded', async () => {
  initRegisterView();
  initLoginView();
  initVoterDashboard();
  initVotingBooth();
  initVerifyView();
  initAdminPortal();
  initResetDemo();
  initMobileMenu();
  initCelebration();

  // Restore a voter session silently (no toast) before routing.
  if (getVoterToken()) {
    try {
      const me = await api('/api/me', { token: getVoterToken() });
      currentVoter = { ...me, sessionToken: getVoterToken() };
    } catch (err) {
      if (err.status === 401) setVoterToken(null);
    }
  }

  window.addEventListener('hashchange', handleHashChange);
  handleHashChange();

  // Live refresh of public metrics and the officer dashboard.
  setInterval(() => {
    if (document.hidden) return;
    const hash = window.location.hash.replace('#', '').trim();
    if (hash === '' || hash === 'home' || HOME_ANCHORS.includes(hash)) updateHomeMetrics();
    else if (hash === 'admin' && getAdminToken()) loadAdminData();
  }, 4000);
});
