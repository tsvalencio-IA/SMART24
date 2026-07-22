import { initializeBackend, getBackendState, subscribeData } from "./database.js";
import { initializeAuth } from "./auth.js";
import { initializeProducts, startProductsSubscription } from "./products.js";
import { initializeLabels, startLabelsSubscription } from "./labels.js";
import { initializeCameras, startCamerasSubscription } from "./cameras.js";
import { initializeEvents, startEventsSubscriptions } from "./events.js";
import { initializeSimulator } from "./simulator.js";
import { initializeSimulator3D } from "./simulator-3d.js";
import { initializeYooseeIntegration, startYooseeSubscription } from "./yoosee.js";
import { initializeReplenishment, startReplenishmentSubscriptions } from "./replenishment.js";
import { initializeCarts, startCartsSubscription } from "./carts.js";
import { initializeLiveMonitor, startLiveMonitorSubscription } from "./live-monitor.js";
import { cameraStatusClass, escapeHtml, eventLabel, formatDate, formatTime, objectEntries, roleLabel, toast } from "./utils.js";

let uiInitialized = false;
let dashboardUnsubscribers = [];
const dashboardState = {
  products: [], labels: [], cameras: [], bridges: [], sessions: [], events: [], occurrences: []
};

function setMetric(id, value) {
  const element = document.getElementById(id);
  if (element) element.textContent = String(value ?? 0);
}

function renderDashboard() {
  setMetric("metricProducts", dashboardState.products.length);
  setMetric("metricLabels", dashboardState.labels.length);
  setMetric("metricCameras", dashboardState.cameras.length);
  setMetric("metricBridges", dashboardState.bridges.filter(item => ["ONLINE", "VIDEO_VISIBLE"].includes(String(item.status).toUpperCase())).length);
  setMetric("metricSessions", dashboardState.sessions.length);
  const pending = dashboardState.occurrences.filter(item => !["reviewed", "dismissed"].includes(item.status)).length;
  setMetric("metricOccurrences", pending);
  const onlineCameras = dashboardState.cameras.filter(item => String(item.status).toUpperCase() === "ONLINE").length;
  document.getElementById("metricCamerasDetail").textContent = dashboardState.cameras.length ? `${onlineCameras} online` : "sem dados";
  document.getElementById("metricOccurrencesDetail").textContent = pending === 1 ? "aguardando revisão" : "aguardando revisão";

  const eventsContainer = document.getElementById("dashboardEvents");
  const recentEvents = [...dashboardState.events].sort((a,b) => Number(b.createdAt || 0) - Number(a.createdAt || 0)).slice(0, 8);
  if (!recentEvents.length) {
    eventsContainer.className = "timeline empty-state";
    eventsContainer.innerHTML = "<strong>Nenhum evento</strong><span>Os eventos publicados pelo simulador ou pelo agente local aparecerão aqui.</span>";
  } else {
    eventsContainer.className = "timeline";
    eventsContainer.innerHTML = recentEvents.map(item => `<div class="timeline-item"><span class="timeline-dot"></span><div class="timeline-copy"><strong>${escapeHtml(eventLabel(item.type))}</strong><span>${escapeHtml(item.sessionId || "sem sessão")} · ${escapeHtml(item.personId || "sistema")}</span></div><time>${formatTime(item.createdAt)}</time></div>`).join("");
  }

  const occurrencesContainer = document.getElementById("dashboardOccurrences");
  const pendingOccurrences = [...dashboardState.occurrences]
    .filter(item => !["reviewed", "dismissed"].includes(item.status))
    .sort((a,b) => Number(b.createdAt || 0) - Number(a.createdAt || 0)).slice(0, 5);
  if (!pendingOccurrences.length) {
    occurrencesContainer.className = "compact-list empty-state";
    occurrencesContainer.innerHTML = "<strong>Fila vazia</strong><span>Nenhuma possível divergência aguarda análise.</span>";
  } else {
    occurrencesContainer.className = "compact-list";
    occurrencesContainer.innerHTML = pendingOccurrences.map(item => `<div class="compact-item"><div><strong>${escapeHtml(item.productName || item.sku || "Evento")}</strong><small>${escapeHtml(item.sessionId || "—")} · diferença ${escapeHtml(item.difference ?? "?")}</small></div><span class="status-badge status-badge--warning">Revisar</span></div>`).join("");
  }

  const camerasContainer = document.getElementById("dashboardCameras");
  const cameraList = [...dashboardState.cameras].sort((a,b) => String(a.cameraId).localeCompare(String(b.cameraId), "pt-BR")).slice(0, 6);
  if (!cameraList.length) {
    camerasContainer.className = "compact-list empty-state";
    camerasContainer.innerHTML = "<strong>Sem câmeras</strong><span>Cadastre somente metadados seguros. Credenciais ficam no agente local.</span>";
  } else {
    camerasContainer.className = "compact-list";
    camerasContainer.innerHTML = cameraList.map(item => `<div class="compact-item"><div><strong>${escapeHtml(item.cameraId)} · ${escapeHtml(item.area)}</strong><small>${item.lastSeenAt ? `Contato ${formatDate(item.lastSeenAt)}` : "Sem heartbeat"}</small></div><span class="status-badge ${cameraStatusClass(item.status)}">${escapeHtml(item.status || "UNCONFIGURED")}</span></div>`).join("");
  }
}

function bindDashboardEvents() {
  const bindings = {
    "smart24:products": "products",
    "smart24:labels": "labels",
    "smart24:cameras": "cameras",
    "smart24:events": "events",
    "smart24:occurrences": "occurrences"
  };
  Object.entries(bindings).forEach(([eventName, key]) => document.addEventListener(eventName, event => {
    dashboardState[key] = event.detail || [];
    renderDashboard();
  }));
}

function startDashboardSubscriptions() {
  dashboardUnsubscribers.forEach(unsubscribe => unsubscribe?.());
  dashboardUnsubscribers = [
    subscribeData("cameraBridges", value => { dashboardState.bridges = objectEntries(value); renderDashboard(); }),
    subscribeData("sessions", value => { dashboardState.sessions = objectEntries(value); renderDashboard(); })
  ];
}

function showView(viewName) {
  const target = document.getElementById(`view-${viewName}`);
  if (!target) return;
  document.querySelectorAll(".view").forEach(view => view.classList.toggle("is-active", view === target));
  document.querySelectorAll("[data-view]").forEach(button => button.classList.toggle("is-active", button.dataset.view === viewName));
  document.getElementById("pageTitle").textContent = target.dataset.title || "SMART24";
  document.getElementById("pageEyebrow").textContent = target.dataset.eyebrow || "PAINEL";
  window.scrollTo({ top: 0, behavior: "smooth" });
  history.replaceState(null, "", `#${viewName}`);
}

function initializeNavigation() {
  const moreSheet = document.getElementById("mobileMoreSheet");
  document.querySelectorAll("[data-view]").forEach(button => button.addEventListener("click", () => {
    showView(button.dataset.view);
    moreSheet?.classList.add("is-hidden");
  }));
  document.querySelectorAll("[data-go]").forEach(button => button.addEventListener("click", () => showView(button.dataset.go)));
  document.getElementById("mobileMoreButton")?.addEventListener("click", () => moreSheet?.classList.toggle("is-hidden"));
  document.getElementById("closeMobileMore")?.addEventListener("click", () => moreSheet?.classList.add("is-hidden"));
  const initial = location.hash.replace("#", "");
  if (document.getElementById(`view-${initial}`)) showView(initial);
  document.getElementById("refreshButton").addEventListener("click", () => {
    startDashboardSubscriptions();
    startProductsSubscription();
    startLabelsSubscription();
    startCamerasSubscription();
    startEventsSubscriptions();
    startReplenishmentSubscriptions();
    startCartsSubscription();
    startLiveMonitorSubscription();
    toast("Dados sincronizados novamente.", "success");
  });
}

function renderBackendStatus(session = null) {
  const state = getBackendState();
  const badge = document.getElementById("modeBadge");
  const settings = document.getElementById("firebaseSettingsStatus");
  if (state.mode === "firebase") {
    badge.className = "status-badge status-badge--ok";
    badge.textContent = "Firebase conectado";
  } else if (state.mode === "demo") {
    badge.className = "status-badge status-badge--warning";
    badge.textContent = "Demonstração local";
  } else {
    badge.className = "status-badge status-badge--danger";
    badge.textContent = "Backend indisponível";
  }
  settings.innerHTML = `
    <div><span>Modo</span><strong>${state.mode === "firebase" ? "Firebase Realtime Database" : state.mode === "demo" ? "Armazenamento local demonstrativo" : "Erro"}</strong></div>
    <div><span>Autenticação</span><strong>${state.mode === "firebase" ? "E-mail e senha" : "Não configurada"}</strong></div>
    <div><span>Usuário</span><strong>${escapeHtml(session?.email || "—")}</strong></div>
    <div><span>Função</span><strong>${escapeHtml(roleLabel(session?.role))}</strong></div>
    <div><span>Imagem do piloto</span><strong>Quadros reduzidos em cameraLive</strong></div>`;
}

function initializeUi() {
  if (uiInitialized) return;
  uiInitialized = true;
  initializeNavigation();
  initializeProducts();
  initializeLabels();
  initializeCameras();
  initializeEvents();
  initializeSimulator();
  initializeSimulator3D();
  initializeYooseeIntegration();
  initializeReplenishment();
  initializeCarts();
  initializeLiveMonitor();
  bindDashboardEvents();
}

function applyRoleVisibility(session) {
  const occurrenceButtons = document.querySelectorAll('[data-view="occurrences"]');
  occurrenceButtons.forEach(button => button.classList.toggle("is-hidden", session.role === "operator"));
  if (session.role === "operator" && location.hash === "#occurrences") showView("dashboard");
}

function startAuthenticatedData(session) {
  if (!session) return;
  initializeUi();
  applyRoleVisibility(session);
  renderBackendStatus(session);
  startProductsSubscription();
  startLabelsSubscription();
  startCamerasSubscription();
  startEventsSubscriptions();
  startYooseeSubscription();
  startReplenishmentSubscriptions();
  startCartsSubscription();
  startLiveMonitorSubscription();
  startDashboardSubscriptions();
}

async function boot() {
  const bootScreen = document.getElementById("bootScreen");
  try {
    await initializeBackend();
    initializeAuth(startAuthenticatedData);
  } catch (error) {
    console.error(error);
    toast(`Falha ao iniciar: ${error.message}`, "error");
  } finally {
    bootScreen.style.opacity = "0";
    window.setTimeout(() => bootScreen.classList.add("is-hidden"), 250);
  }
}

boot();
