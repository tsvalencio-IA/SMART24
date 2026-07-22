import { subscribeData } from "./database.js";
import { escapeHtml, formatDate } from "./utils.js";

let unsubscribe = null;

function flattenCarts(value) {
  const rows = [];
  Object.entries(value || {}).forEach(([storeId, sessions]) => {
    Object.entries(sessions || {}).forEach(([sessionId, people]) => {
      Object.entries(people || {}).forEach(([personId, items]) => {
        const normalized = Object.entries(items || {}).filter(([, item]) => item && typeof item === "object").map(([serial, item]) => ({ serial, ...item }));
        rows.push({ storeId, sessionId, personId, items: normalized });
      });
    });
  });
  return rows;
}

function render(value) {
  const grid = document.getElementById("cartsGrid");
  const empty = document.getElementById("cartsEmpty");
  const carts = flattenCarts(value).filter(cart => cart.items.length);
  grid.innerHTML = carts.map(cart => `<article class="occurrence-card"><div class="panel-header"><div><span class="eyebrow">${escapeHtml(cart.storeId)}</span><h2>${escapeHtml(cart.personId)}</h2></div><span class="status-badge status-badge--warning">${cart.items.length} item(ns)</span></div><p class="muted">Sessão: ${escapeHtml(cart.sessionId)}</p><div class="compact-list">${cart.items.map(item => `<div class="compact-item"><div><strong>${escapeHtml(item.productName || item.sku || item.serial)}</strong><small>${escapeHtml(item.serial)} · ${escapeHtml(item.zoneId || "fora da zona")}</small></div><span class="status-badge status-badge--ok">${escapeHtml(item.status || "NO_CARRINHO")}</span></div>`).join("")}</div></article>`).join("");
  empty.classList.toggle("is-hidden", carts.length > 0);
}

export function initializeCarts() {}
export function startCartsSubscription() {
  unsubscribe?.();
  unsubscribe = subscribeData("carts", render);
}
