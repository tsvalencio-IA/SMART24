import { appendAudit, pushData, subscribeData, updateData } from "./database.js";
import { canWrite, getCurrentSession } from "./auth.js";
import { debounce, escapeHtml, formatDate, now, objectEntries, setMessage, toast } from "./utils.js";

let products = [];
let unsubscribe = null;

function renderProducts(filter = "") {
  const body = document.getElementById("productsTableBody");
  const empty = document.getElementById("productsEmpty");
  const term = filter.toLowerCase().trim();
  const visible = products
    .filter(item => [item.name, item.sku, item.barcode, item.category].some(value => String(value || "").toLowerCase().includes(term)))
    .sort((a, b) => String(a.name).localeCompare(String(b.name), "pt-BR"));

  body.innerHTML = visible.map(item => `
    <tr>
      <td><strong>${escapeHtml(item.name)}</strong><small>${escapeHtml(item.barcode || "Sem código de barras")}</small></td>
      <td>${escapeHtml(item.sku)}</td>
      <td>${escapeHtml(item.category || "—")}</td>
      <td><span class="status-badge ${item.status === "active" ? "status-badge--ok" : "status-badge--neutral"}">${item.status === "active" ? "Ativo" : "Inativo"}</span></td>
      <td>${formatDate(item.createdAt)}</td>
      <td><div class="table-actions"><button class="table-action" data-edit-product="${escapeHtml(item.id)}" title="Editar">✎</button></div></td>
    </tr>
  `).join("");

  empty.classList.toggle("is-hidden", visible.length > 0);
  body.closest(".table-wrap").classList.toggle("is-hidden", visible.length === 0);
  document.querySelectorAll("[data-edit-product]").forEach(button => button.addEventListener("click", () => editProduct(button.dataset.editProduct)));
}

function editProduct(id) {
  const item = products.find(product => product.id === id);
  if (!item) return;
  document.getElementById("productId").value = item.id;
  document.getElementById("productName").value = item.name || "";
  document.getElementById("productSku").value = item.sku || "";
  document.getElementById("productBarcode").value = item.barcode || "";
  document.getElementById("productCategory").value = item.category || "";
  document.getElementById("productStatus").value = item.status || "active";
  document.getElementById("productFormTitle").textContent = "Editar produto";
  document.getElementById("cancelProductEdit").classList.remove("is-hidden");
  document.getElementById("productName").focus();
}

function resetForm() {
  document.getElementById("productForm").reset();
  document.getElementById("productId").value = "";
  document.getElementById("productStatus").value = "active";
  document.getElementById("productFormTitle").textContent = "Novo produto";
  document.getElementById("cancelProductEdit").classList.add("is-hidden");
  setMessage(document.getElementById("productMessage"), "");
}

export function initializeProducts() {
  const form = document.getElementById("productForm");
  const message = document.getElementById("productMessage");
  form.addEventListener("submit", async event => {
    event.preventDefault();
    if (!canWrite("products")) {
      setMessage(message, "Sua função não pode alterar produtos.", "error");
      return;
    }
    const session = getCurrentSession();
    const id = document.getElementById("productId").value;
    const payload = {
      name: document.getElementById("productName").value.trim(),
      sku: document.getElementById("productSku").value.trim().toUpperCase(),
      barcode: document.getElementById("productBarcode").value.trim(),
      category: document.getElementById("productCategory").value.trim(),
      status: document.getElementById("productStatus").value,
      updatedAt: now(),
      updatedBy: session.uid
    };
    if (!payload.name || !payload.sku) {
      setMessage(message, "Informe nome e SKU.", "error");
      return;
    }
    const duplicate = products.find(product => product.sku?.toUpperCase() === payload.sku && product.id !== id);
    if (duplicate) {
      setMessage(message, "Já existe um produto com este SKU.", "error");
      return;
    }
    const button = form.querySelector("button[type='submit']");
    button.disabled = true;
    try {
      if (id) {
        await updateData(`products/${id}`, payload);
        await appendAudit("PRODUCT_UPDATED", { productId: id, sku: payload.sku }, session);
      } else {
        payload.createdAt = now();
        payload.createdBy = session.uid;
        const productId = await pushData("products", payload);
        await appendAudit("PRODUCT_CREATED", { productId, sku: payload.sku }, session);
      }
      resetForm();
      toast(id ? "Produto atualizado." : "Produto cadastrado.", "success");
    } catch (error) {
      setMessage(message, `Falha ao salvar: ${error.message}`, "error");
    } finally {
      button.disabled = false;
    }
  });
  document.getElementById("cancelProductEdit").addEventListener("click", resetForm);
  document.getElementById("productSearch").addEventListener("input", debounce(event => renderProducts(event.target.value)));
}

export function startProductsSubscription() {
  unsubscribe?.();
  unsubscribe = subscribeData("products", value => {
    products = objectEntries(value);
    renderProducts(document.getElementById("productSearch")?.value || "");
    document.dispatchEvent(new CustomEvent("smart24:products", { detail: products }));
  }, error => toast(`Não foi possível carregar produtos: ${error.message}`, "error"));
}

export function getProducts() {
  return products;
}
