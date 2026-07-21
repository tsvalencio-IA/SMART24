import { appendAudit, pushData, subscribeData } from "./database.js";
import { canWrite, getCurrentSession } from "./auth.js";
import { escapeHtml, formatDate, normalizeStoreCode, now, objectEntries, setMessage, toast } from "./utils.js";

let products = [];
let labels = [];
let unsubscribe = null;

function renderProductOptions() {
  const select = document.getElementById("labelProduct");
  const current = select.value;
  select.innerHTML = `<option value="">Selecione um produto</option>${products
    .filter(product => product.status !== "inactive")
    .sort((a, b) => String(a.name).localeCompare(String(b.name), "pt-BR"))
    .map(product => `<option value="${escapeHtml(product.id)}">${escapeHtml(product.name)} — ${escapeHtml(product.sku)}</option>`).join("")}`;
  if (products.some(product => product.id === current)) select.value = current;
}

function fallbackQr(element) {
  element.innerHTML = `<span class="label-fallback">${Array.from({ length: 49 }, () => "<i></i>").join("")}</span>`;
}

function buildQr(element, text) {
  element.innerHTML = "";
  if (typeof window.QRCode !== "function") {
    fallbackQr(element);
    return;
  }
  try {
    new window.QRCode(element, { text, width: 82, height: 82, correctLevel: window.QRCode.CorrectLevel.M });
  } catch {
    fallbackQr(element);
  }
}

function renderLabels() {
  const grid = document.getElementById("labelsGrid");
  const empty = document.getElementById("labelsEmpty");
  const sorted = [...labels].sort((a, b) => Number(b.createdAt || 0) - Number(a.createdAt || 0));
  grid.innerHTML = sorted.map(label => `
    <article class="label-card is-selected" data-label-card="${escapeHtml(label.id)}">
      <input type="checkbox" checked aria-label="Selecionar etiqueta para impressão">
      <div class="label-qr" id="qr-${escapeHtml(label.id)}"></div>
      <div class="label-details">
        <strong>${escapeHtml(label.productName)}</strong>
        <code>${escapeHtml(label.serial)}</code>
        <span>SKU: ${escapeHtml(label.sku)}</span>
        <span>Zona: ${escapeHtml(label.zoneId)}</span>
        <span>RFID: ${escapeHtml(label.rfidEpc || "AGUARDANDO")}</span>
        <span>${formatDate(label.createdAt)}</span>
      </div>
    </article>
  `).join("");
  sorted.forEach(label => buildQr(document.getElementById(`qr-${label.id}`), label.qrPayload));
  grid.querySelectorAll(".label-card input").forEach(input => input.addEventListener("change", () => input.closest(".label-card").classList.toggle("is-selected", input.checked)));
  empty.classList.toggle("is-hidden", sorted.length > 0);
  document.getElementById("labelCountBadge").textContent = String(sorted.length);
}

function createSerial(store, batchCode, sequence) {
  return `TAG-${normalizeStoreCode(store)}-${batchCode}-${String(sequence).padStart(3, "0")}`;
}

export function initializeLabels() {
  document.addEventListener("smart24:products", event => {
    products = event.detail || [];
    renderProductOptions();
  });

  const form = document.getElementById("labelForm");
  const message = document.getElementById("labelMessage");
  form.addEventListener("submit", async event => {
    event.preventDefault();
    if (!canWrite("labels")) {
      setMessage(message, "Sua função não pode gerar etiquetas.", "error");
      return;
    }
    const session = getCurrentSession();
    const productId = document.getElementById("labelProduct").value;
    const product = products.find(item => item.id === productId);
    const quantity = Number(document.getElementById("labelQuantity").value);
    const storeId = document.getElementById("labelStore").value.trim();
    const zoneId = document.getElementById("labelZone").value.trim();
    if (!product || !Number.isInteger(quantity) || quantity < 1 || quantity > 200 || !storeId || !zoneId) {
      setMessage(message, "Selecione o produto e informe quantidade, loja e zona válidas.", "error");
      return;
    }
    const button = form.querySelector("button[type='submit']");
    button.disabled = true;
    setMessage(message, `Gerando ${quantity} etiqueta(s)…`);
    try {
      const batchCode = new Date().toISOString().replace(/\D/g, "").slice(2, 14);
      for (let index = 0; index < quantity; index += 1) {
        const serial = createSerial(storeId, batchCode, index + 1);
        const qrPayload = JSON.stringify({ v: 1, system: "SMART24", serial, productId, sku: product.sku, storeId, zoneId });
        await pushData("tags", {
          serial,
          productId,
          productName: product.name,
          sku: product.sku,
          barcode: product.barcode || "",
          storeId,
          zoneId,
          status: "CADASTRADO",
          qrPayload,
          rfidEpc: "",
          createdAt: now(),
          createdBy: session.uid
        });
      }
      await appendAudit("LABELS_CREATED", { productId, quantity, storeId, zoneId }, session);
      setMessage(message, `${quantity} etiqueta(s) gerada(s) e salva(s).`, "success");
      toast("Etiquetas geradas com sucesso.", "success");
    } catch (error) {
      setMessage(message, `Falha ao gerar etiquetas: ${error.message}`, "error");
    } finally {
      button.disabled = false;
    }
  });

  document.getElementById("printLabelsButton").addEventListener("click", () => {
    const selected = document.querySelectorAll(".label-card.is-selected").length;
    if (!selected) {
      toast("Selecione ao menos uma etiqueta para imprimir.", "error");
      return;
    }
    window.print();
  });
}

export function startLabelsSubscription() {
  unsubscribe?.();
  unsubscribe = subscribeData("tags", value => {
    labels = objectEntries(value);
    renderLabels();
    document.dispatchEvent(new CustomEvent("smart24:labels", { detail: labels }));
  }, error => toast(`Não foi possível carregar etiquetas: ${error.message}`, "error"));
}
