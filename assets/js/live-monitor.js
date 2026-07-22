import { subscribeData } from "./database.js";
import { escapeHtml, formatDate, objectEntries } from "./utils.js";

let unsubscribe = null;
let streams = [];
let selectedKey = "";

function flatten(value) {
  const rows = [];
  Object.entries(value || {}).forEach(([storeId, cameras]) => {
    Object.entries(cameras || {}).forEach(([cameraId, camera]) => {
      rows.push({ ...(camera || {}), storeId: camera?.storeId || storeId, cameraId: camera?.cameraId || cameraId, key: `${storeId}/${cameraId}` });
    });
  });
  return rows.sort((a, b) => String(a.key).localeCompare(String(b.key), "pt-BR"));
}

function statusClass(status) {
  const value = String(status || "").toUpperCase();
  if (["VIDEO_VISIBLE", "ONLINE"].includes(value)) return "status-badge--ok";
  if (["WAITING_VIDEO", "DEGRADED"].includes(value)) return "status-badge--warning";
  return "status-badge--danger";
}

function stale(item) {
  return !item.updatedAt || Date.now() - Number(item.updatedAt) > 12000;
}

function render() {
  const grid = document.getElementById("liveCameraGrid");
  const empty = document.getElementById("liveCameraEmpty");
  const detail = document.getElementById("liveCameraDetail");
  if (!grid || !empty || !detail) return;

  if (!streams.length) {
    grid.innerHTML = "";
    grid.classList.add("is-hidden");
    empty.classList.remove("is-hidden");
    detail.classList.add("is-hidden");
    return;
  }

  grid.classList.remove("is-hidden");
  empty.classList.add("is-hidden");
  if (!selectedKey || !streams.some(item => item.key === selectedKey)) selectedKey = streams[0].key;

  grid.innerHTML = streams.map(item => {
    const isStale = stale(item);
    const status = isStale ? "SEM ATUALIZAÇÃO" : (item.status || "SEM ESTADO");
    const image = item.frameDataUrl
      ? `<img src="${item.frameDataUrl}" alt="Imagem analisada da câmera ${escapeHtml(item.cameraId)}">`
      : `<div class="live-camera-placeholder"><strong>Sem quadro publicado</strong><span>Abra o vídeo no Yoosee e inicie o Vision Pilot.</span></div>`;
    return `<button class="live-camera-card ${item.key === selectedKey ? "is-selected" : ""}" type="button" data-live-key="${escapeHtml(item.key)}">
      <div class="live-camera-frame">${image}<span class="live-camera-timestamp">${item.updatedAt ? formatDate(item.updatedAt) : "nunca"}</span></div>
      <div class="live-camera-card-copy">
        <div><strong>${escapeHtml(item.cameraId)}</strong><small>${escapeHtml(item.storeId)}</small></div>
        <span class="status-badge ${isStale ? "status-badge--danger" : statusClass(item.status)}">${escapeHtml(status)}</span>
      </div>
      <div class="live-camera-metrics"><span>👤 ${Number(item.personsDetected || 0)}</span><span>⌗ ${Number(item.tagsDetected || 0)}</span></div>
    </button>`;
  }).join("");

  document.querySelectorAll("[data-live-key]").forEach(button => button.addEventListener("click", () => {
    selectedKey = button.dataset.liveKey;
    render();
  }));

  const selected = streams.find(item => item.key === selectedKey);
  if (!selected) return;
  detail.classList.remove("is-hidden");
  const people = objectEntries(selected.persons || {});
  const tags = objectEntries(selected.tags || {});
  detail.innerHTML = `
    <div class="panel-header"><div><span class="eyebrow">IMAGEM ANALISADA</span><h2>${escapeHtml(selected.cameraId)} · ${escapeHtml(selected.storeId)}</h2></div><span class="status-badge ${stale(selected) ? "status-badge--danger" : statusClass(selected.status)}">${escapeHtml(stale(selected) ? "SEM ATUALIZAÇÃO" : selected.status || "—")}</span></div>
    <div class="live-detail-layout">
      <div class="live-detail-image">${selected.frameDataUrl ? `<img src="${selected.frameDataUrl}" alt="Imagem ampliada da câmera ${escapeHtml(selected.cameraId)}">` : `<div class="live-camera-placeholder"><strong>Sem imagem</strong><span>Nenhum quadro foi publicado.</span></div>`}</div>
      <div class="live-detail-side">
        <div class="metric-strip"><div><span>Pessoas</span><strong>${people.length}</strong></div><div><span>Etiquetas</span><strong>${tags.length}</strong></div><div><span>Último quadro</span><strong>${selected.updatedAt ? formatDate(selected.updatedAt) : "—"}</strong></div></div>
        <div class="live-detection-list"><h3>Usuários acompanhados</h3>${people.length ? people.map(person => `<div><strong>${escapeHtml(person.personId || person.id)}</strong><span>${Math.round(Number(person.confidence || 0) * 100)}% · ${escapeHtml(person.source || "rastreamento")}</span></div>`).join("") : `<p>Nenhuma pessoa reconhecida no quadro atual.</p>`}</div>
        <div class="live-detection-list"><h3>Etiquetas visíveis</h3>${tags.length ? tags.map(tag => `<div><strong>${escapeHtml(tag.productName || tag.sku || "Produto")}</strong><span>${escapeHtml(tag.serial || tag.id)}</span></div>`).join("") : `<p>Nenhuma etiqueta SMART24 visível no quadro atual.</p>`}</div>
      </div>
    </div>`;
}

export function initializeLiveMonitor() {
  document.getElementById("refreshLiveMonitor")?.addEventListener("click", () => startLiveMonitorSubscription());
}

export function startLiveMonitorSubscription() {
  unsubscribe?.();
  unsubscribe = subscribeData("cameraLive", value => {
    streams = flatten(value);
    render();
  }, () => {
    streams = [];
    render();
  });
}
