import { appendAudit, readData, subscribeData, updateData } from "./database.js";
import { canWrite, getCurrentSession } from "./auth.js";
import { escapeHtml, formatDate, objectEntries, setMessage, toast, now } from "./utils.js";
import { initializeTagScanner } from "./tag-scan.js";

let tags = [];
let zones = [];
let unsubTags = null;
let unsubZones = null;

function batchPrefix(serial) {
  return String(serial || "").replace(/-\d{3}$/i, "");
}

function renderZones() {
  const container = document.getElementById("zonesList");
  if (!container) return;
  if (!zones.length) {
    container.className = "compact-list empty-state";
    container.innerHTML = "<strong>Nenhuma zona calibrada</strong><span>Use o SMART24 Vision Pilot para capturar uma imagem real e marcar a prateleira.</span>";
    return;
  }
  container.className = "compact-list";
  container.innerHTML = zones.map(zone => `<div class="compact-item"><div><strong>${escapeHtml(zone.zoneId || zone.id)}</strong><small>${escapeHtml(zone.storeId || "—")} · ${escapeHtml(zone.cameraId || "—")} · ${formatDate(zone.updatedAt)}</small></div><span class="status-badge status-badge--ok">CALIBRADA</span></div>`).join("");
}

export function initializeReplenishment() {
  initializeTagScanner(parsed => {
    document.getElementById("replenishmentSerial").value = parsed.serial;
    if (parsed.payload?.storeId) document.getElementById("replenishmentStore").value = parsed.payload.storeId;
    if (parsed.payload?.zoneId && parsed.payload.zoneId !== "zona-a-definir") document.getElementById("replenishmentZone").value = parsed.payload.zoneId;
  });
  const form = document.getElementById("replenishmentForm");
  const message = document.getElementById("replenishmentMessage");
  form?.addEventListener("submit", async event => {
    event.preventDefault();
    if (!canWrite("replenishment")) {
      setMessage(message, "Sua função não pode posicionar etiquetas.", "error");
      return;
    }
    const serial = document.getElementById("replenishmentSerial").value.trim().toUpperCase();
    const quantity = Number(document.getElementById("replenishmentQuantity").value);
    const storeId = document.getElementById("replenishmentStore").value.trim();
    const cameraId = document.getElementById("replenishmentCamera").value.trim().toUpperCase();
    const zoneId = document.getElementById("replenishmentZone").value.trim().toUpperCase();
    if (!serial || !Number.isInteger(quantity) || quantity < 1 || !storeId || !cameraId || !zoneId) {
      setMessage(message, "Informe etiqueta, quantidade, loja, câmera e zona.", "error");
      return;
    }
    const selected = tags.find(tag => String(tag.serial).toUpperCase() === serial);
    if (!selected) {
      setMessage(message, "A etiqueta não foi encontrada no Firebase.", "error");
      return;
    }
    const prefix = batchPrefix(serial);
    const candidates = tags
      .filter(tag => batchPrefix(String(tag.serial).toUpperCase()) === prefix)
      .sort((a, b) => String(a.serial).localeCompare(String(b.serial), "pt-BR"))
      .slice(0, quantity);
    if (candidates.length < quantity) {
      setMessage(message, `O lote possui somente ${candidates.length} etiqueta(s) encontrada(s).`, "error");
      return;
    }
    const session = getCurrentSession();
    const button = form.querySelector("button[type='submit']");
    button.disabled = true;
    try {
      const timestamp = now();
      await Promise.all(candidates.map(tag => updateData(`tags/${tag.id}`, {
        storeId, cameraId, zoneId, status: "NA_PRATELEIRA", shelvedAt: timestamp, shelvedBy: session.uid, updatedAt: timestamp, updatedBy: session.uid
      })));
      await appendAudit("TAG_BATCH_SHELVED", { serial, prefix, quantity, storeId, cameraId, zoneId }, session);
      setMessage(message, `${quantity} unidade(s) associada(s) a ${zoneId}.`, "success");
      toast("Lote posicionado na prateleira.", "success");
    } catch (error) {
      setMessage(message, `Falha ao posicionar lote: ${error.message}`, "error");
    } finally {
      button.disabled = false;
    }
  });
}

export function startReplenishmentSubscriptions() {
  unsubTags?.();
  unsubZones?.();
  unsubTags = subscribeData("tags", value => { tags = objectEntries(value); });
  unsubZones = subscribeData("zones", value => {
    zones = [];
    Object.entries(value || {}).forEach(([storeId, cameras]) => Object.entries(cameras || {}).forEach(([cameraId, items]) => Object.entries(items || {}).forEach(([id, zone]) => zones.push({ id, storeId, cameraId, ...zone }))));
    renderZones();
  });
}
