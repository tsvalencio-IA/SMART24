export const EVENT_LABELS = {
  ACCESS_GRANTED: "Acesso autorizado",
  PERSON_ENTERED: "Pessoa entrou",
  PERSON_MOVED: "Pessoa em deslocamento",
  PRODUCT_PICKUP: "Produto retirado",
  PRODUCT_RETURN: "Produto devolvido",
  PRODUCT_TRANSFER: "Produto transferido",
  CHECKOUT_ITEM_REGISTERED: "Item registrado no caixa",
  CHECKOUT_ITEM_REMOVED: "Item removido do caixa",
  PAYMENT_APPROVED: "Pagamento aprovado",
  PAYMENT_REJECTED: "Pagamento rejeitado",
  PERSON_EXITED: "Pessoa saiu",
  CAMERA_OFFLINE: "Câmera indisponível",
  CAMERA_ONLINE: "Câmera disponível",
  AMBIGUOUS_INTERACTION: "Interação ambígua",
  IMAGE_INSUFFICIENT: "Imagem insuficiente",
  OCCURRENCE_CREATED: "Ocorrência criada"
};

export function escapeHtml(value = "") {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

export function safeText(value, fallback = "—") {
  const text = String(value ?? "").trim();
  return text || fallback;
}

export function formatDate(timestamp, includeSeconds = false) {
  if (!timestamp) return "—";
  const date = new Date(Number(timestamp));
  if (Number.isNaN(date.getTime())) return "—";
  return new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "short",
    timeStyle: includeSeconds ? "medium" : "short"
  }).format(date);
}

export function formatTime(timestamp) {
  if (!timestamp) return "—";
  const date = new Date(Number(timestamp));
  if (Number.isNaN(date.getTime())) return "—";
  return new Intl.DateTimeFormat("pt-BR", { hour: "2-digit", minute: "2-digit", second: "2-digit" }).format(date);
}

export function objectEntries(value) {
  if (!value || typeof value !== "object") return [];
  return Object.entries(value).map(([id, item]) => ({ id, ...(item || {}) }));
}

export function now() {
  return Date.now();
}

export function createId(prefix = "ID") {
  const time = Date.now().toString(36).toUpperCase();
  const random = Math.random().toString(36).slice(2, 7).toUpperCase();
  return `${prefix}-${time}-${random}`;
}

export function slug(value = "") {
  return String(value)
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-zA-Z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .toLowerCase();
}

export function normalizeStoreCode(value = "loja-01") {
  const cleaned = slug(value).toUpperCase().replaceAll("-", "");
  return cleaned || "LOJA01";
}

export function setMessage(element, message = "", type = "") {
  if (!element) return;
  element.textContent = message;
  element.classList.toggle("is-error", type === "error");
  element.classList.toggle("is-success", type === "success");
}

export function toast(message, type = "") {
  const region = document.getElementById("toastRegion");
  if (!region) return;
  const item = document.createElement("div");
  item.className = `toast ${type ? `is-${type}` : ""}`;
  item.textContent = message;
  region.appendChild(item);
  window.setTimeout(() => item.remove(), 4200);
}

export function debounce(fn, delay = 220) {
  let timer;
  return (...args) => {
    window.clearTimeout(timer);
    timer = window.setTimeout(() => fn(...args), delay);
  };
}

export function getInitials(email = "") {
  const base = String(email).split("@")[0].replace(/[^a-zA-Z0-9 ]/g, " ").trim();
  const parts = base.split(/\s+/).filter(Boolean);
  if (!parts.length) return "U";
  return parts.slice(0, 2).map(part => part[0]).join("").toUpperCase();
}

export function roleLabel(role = "") {
  return ({ admin: "Administrador", operator: "Operador", auditor: "Auditor", demo: "Demonstração" })[role] || "Sem função";
}

export function eventLabel(type = "") {
  return EVENT_LABELS[type] || safeText(type);
}

export function confidenceLabel(value) {
  const number = Number(value);
  if (!Number.isFinite(number)) return "—";
  return `${Math.round(number * 100)}%`;
}

export function cameraStatusClass(status = "") {
  const normalized = String(status).toUpperCase();
  if (normalized === "ONLINE") return "status-badge--ok";
  if (["OFFLINE", "STOPPED"].includes(normalized)) return "status-badge--danger";
  return "status-badge--warning";
}

export function downloadJson(filename, data) {
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}
