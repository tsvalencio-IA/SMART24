import { appendAudit, subscribeData, updateData } from "./database.js";
import { canWrite, getCurrentSession } from "./auth.js";
import { EVENT_LABELS, confidenceLabel, escapeHtml, eventLabel, formatDate, now, objectEntries, setMessage, toast } from "./utils.js";

let events = [];
let occurrences = [];
let unsubscribeEvents = null;
let unsubscribeOccurrences = null;

function renderEventFilters() {
  const select = document.getElementById("eventTypeFilter");
  if (select.options.length > 1) return;
  Object.entries(EVENT_LABELS).forEach(([value, label]) => {
    const option = document.createElement("option");
    option.value = value;
    option.textContent = label;
    select.appendChild(option);
  });
}

function renderEvents() {
  const body = document.getElementById("eventsTableBody");
  const empty = document.getElementById("eventsEmpty");
  const type = document.getElementById("eventTypeFilter")?.value || "";
  const term = (document.getElementById("eventSearch")?.value || "").toLowerCase().trim();
  const visible = [...events]
    .filter(item => !type || item.type === type)
    .filter(item => [item.sessionId, item.personId, item.cameraId, item.productId, item.productName, item.tagId, item.sku, item.type]
      .some(value => String(value || "").toLowerCase().includes(term)))
    .sort((a, b) => Number(b.createdAt || 0) - Number(a.createdAt || 0));

  body.innerHTML = visible.map(item => `
    <tr>
      <td>${formatDate(item.createdAt, true)}</td>
      <td><strong>${escapeHtml(eventLabel(item.type))}</strong><small>${escapeHtml(item.type)}</small></td>
      <td>${escapeHtml(item.sessionId || "—")}</td>
      <td>${escapeHtml(item.personId || "—")}</td>
      <td>${escapeHtml(item.cameraId || "—")}</td>
      <td>${escapeHtml(item.productName || item.sku || item.productId || "—")}<small>${escapeHtml(item.tagId || "")}</small></td>
      <td>${escapeHtml(item.quantity ?? "—")}</td>
      <td>${confidenceLabel(item.confidence)}</td>
    </tr>
  `).join("");
  empty.classList.toggle("is-hidden", visible.length > 0);
  body.closest(".table-wrap").classList.toggle("is-hidden", visible.length === 0);
}

function occurrenceStatusBadge(status) {
  if (status === "reviewed") return "status-badge--ok";
  if (status === "dismissed") return "status-badge--neutral";
  return "status-badge--warning";
}

function renderOccurrences() {
  const grid = document.getElementById("occurrencesGrid");
  const empty = document.getElementById("occurrencesEmpty");
  const sorted = [...occurrences].sort((a, b) => Number(b.createdAt || 0) - Number(a.createdAt || 0));
  grid.innerHTML = sorted.map(item => `
    <article class="occurrence-card">
      <div class="occurrence-head">
        <div><span class="eyebrow">${escapeHtml(item.storeId || "LOJA")}</span><h3>${escapeHtml(item.productName || item.sku || "Produto para revisão")}</h3></div>
        <span class="status-badge ${occurrenceStatusBadge(item.status)}">${item.status === "reviewed" ? "Revisada" : item.status === "dismissed" ? "Encerrada" : "Pendente"}</span>
      </div>
      <div class="occurrence-grid">
        <div class="occurrence-stat"><span>Retirado</span><strong>${escapeHtml(item.pickedUp ?? 0)}</strong></div>
        <div class="occurrence-stat"><span>Devolvido</span><strong>${escapeHtml(item.returned ?? 0)}</strong></div>
        <div class="occurrence-stat"><span>Esperado</span><strong>${escapeHtml(item.expected ?? 0)}</strong></div>
        <div class="occurrence-stat"><span>Registrado</span><strong>${escapeHtml(item.registered ?? 0)}</strong></div>
        <div class="occurrence-stat"><span>Pago</span><strong>${escapeHtml(item.paid ?? 0)}</strong></div>
        <div class="occurrence-stat"><span>Diferença</span><strong>${escapeHtml(item.difference ?? 0)}</strong></div>
      </div>
      <div class="occurrence-detail">
        <span><strong>Sessão:</strong> ${escapeHtml(item.sessionId || "—")}</span>
        <span><strong>Pessoa:</strong> ${escapeHtml(item.personId || "—")}</span>
        <span><strong>Confiança estimada:</strong> ${confidenceLabel(item.confidence)}</span>
        <span><strong>Motivo:</strong> ${escapeHtml(item.reason || "Possível divergência por SKU")}</span>
        <span><strong>Criada em:</strong> ${formatDate(item.createdAt, true)}</span>
      </div>
      <form class="review-form" data-review-id="${escapeHtml(item.id)}">
        <label class="field"><span>Classificação humana</span><select name="classification">
          <option value="">Selecione</option>
          <option value="confirmada" ${item.classification === "confirmada" ? "selected" : ""}>Confirmada após revisão</option>
          <option value="falso_alerta" ${item.classification === "falso_alerta" ? "selected" : ""}>Falso alerta</option>
          <option value="produto_devolvido_outro_local" ${item.classification === "produto_devolvido_outro_local" ? "selected" : ""}>Produto devolvido em outro local</option>
          <option value="erro_cadastro" ${item.classification === "erro_cadastro" ? "selected" : ""}>Erro de cadastro</option>
          <option value="erro_quantidade" ${item.classification === "erro_quantidade" ? "selected" : ""}>Erro de quantidade</option>
          <option value="imagem_insuficiente" ${item.classification === "imagem_insuficiente" ? "selected" : ""}>Imagem insuficiente</option>
          <option value="outro" ${item.classification === "outro" ? "selected" : ""}>Outro</option>
        </select></label>
        <label class="field"><span>Observação</span><textarea name="reviewNotes" rows="2" maxlength="500" placeholder="Registre somente o que foi verificado.">${escapeHtml(item.reviewNotes || "")}</textarea></label>
        <button class="btn btn--secondary" type="submit">Salvar revisão</button>
        <p class="form-message" role="status"></p>
      </form>
    </article>
  `).join("");
  empty.classList.toggle("is-hidden", sorted.length > 0);
  grid.querySelectorAll("[data-review-id]").forEach(form => form.addEventListener("submit", saveReview));
}

async function saveReview(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const message = form.querySelector(".form-message");
  if (!canWrite("occurrences")) {
    setMessage(message, "Sua função não pode revisar ocorrências.", "error");
    return;
  }
  const classification = form.elements.classification.value;
  const reviewNotes = form.elements.reviewNotes.value.trim();
  if (!classification) {
    setMessage(message, "Selecione a classificação humana.", "error");
    return;
  }
  const session = getCurrentSession();
  const id = form.dataset.reviewId;
  const button = form.querySelector("button");
  button.disabled = true;
  try {
    await updateData(`occurrences/${id}`, {
      status: classification === "falso_alerta" ? "dismissed" : "reviewed",
      classification,
      reviewNotes,
      reviewedAt: now(),
      reviewedBy: session.uid,
      reviewedByEmail: session.email
    });
    await appendAudit("OCCURRENCE_REVIEWED", { occurrenceId: id, classification }, session);
    setMessage(message, "Revisão salva.", "success");
    toast("Classificação humana registrada.", "success");
  } catch (error) {
    setMessage(message, `Falha ao salvar revisão: ${error.message}`, "error");
  } finally {
    button.disabled = false;
  }
}

export function initializeEvents() {
  renderEventFilters();
  document.getElementById("eventTypeFilter").addEventListener("change", renderEvents);
  document.getElementById("eventSearch").addEventListener("input", renderEvents);
}

export function startEventsSubscriptions() {
  unsubscribeEvents?.();
  unsubscribeOccurrences?.();
  unsubscribeEvents = subscribeData("events", value => {
    events = objectEntries(value);
    renderEvents();
    document.dispatchEvent(new CustomEvent("smart24:events", { detail: events }));
  }, error => toast(`Não foi possível carregar eventos: ${error.message}`, "error"));
  if (getCurrentSession()?.role === "operator") {
    occurrences = [];
    renderOccurrences();
    document.dispatchEvent(new CustomEvent("smart24:occurrences", { detail: occurrences }));
  } else {
    unsubscribeOccurrences = subscribeData("occurrences", value => {
      occurrences = objectEntries(value);
      renderOccurrences();
      document.dispatchEvent(new CustomEvent("smart24:occurrences", { detail: occurrences }));
    }, error => toast(`Não foi possível carregar ocorrências: ${error.message}`, "error"));
  }
}

export function getEvents() { return events; }
export function getOccurrences() { return occurrences; }
