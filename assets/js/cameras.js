import { appendAudit, pushData, subscribeData, updateData } from "./database.js";
import { initializeCameraQr } from "./camera-qr.js";
import { canWrite, getCurrentSession } from "./auth.js";
import { cameraStatusClass, escapeHtml, formatDate, now, objectEntries, setMessage, toast } from "./utils.js";

let cameras = [];
let unsubscribe = null;

function renderCameras() {
  const body = document.getElementById("camerasTableBody");
  const empty = document.getElementById("camerasEmpty");
  const sorted = [...cameras].sort((a, b) => String(a.cameraId).localeCompare(String(b.cameraId), "pt-BR"));
  body.innerHTML = sorted.map(camera => `
    <tr>
      <td><strong>${escapeHtml(camera.cameraId)}</strong><small>${escapeHtml(camera.name)}</small></td>
      <td>${escapeHtml(camera.area)}<small>${escapeHtml(camera.storeId)}</small></td>
      <td>${escapeHtml(camera.vendor || "A_CONFIRMAR")}<small>${escapeHtml(camera.vendorDeviceId || "sem ID do fabricante")}</small></td>
      <td>${escapeHtml(camera.protocol || "A_CONFIRMAR")}</td>
      <td>${escapeHtml(camera.bridgeId || "—")}</td>
      <td><span class="status-badge ${cameraStatusClass(camera.status)}">${escapeHtml(camera.status || "UNCONFIGURED")}</span></td>
      <td>${formatDate(camera.lastSeenAt)}</td>
      <td><div class="table-actions"><button class="table-action" data-edit-camera="${escapeHtml(camera.id)}" title="Editar metadados">✎</button></div></td>
    </tr>
  `).join("");
  empty.classList.toggle("is-hidden", sorted.length > 0);
  body.closest(".table-wrap").classList.toggle("is-hidden", sorted.length === 0);
  document.querySelectorAll("[data-edit-camera]").forEach(button => button.addEventListener("click", () => editCamera(button.dataset.editCamera)));
}

function editCamera(id) {
  const camera = cameras.find(item => item.id === id);
  if (!camera) return;
  document.getElementById("cameraRecordId").value = camera.id;
  document.getElementById("cameraId").value = camera.cameraId || "";
  document.getElementById("cameraName").value = camera.name || "";
  document.getElementById("cameraStore").value = camera.storeId || "loja-01";
  document.getElementById("cameraArea").value = camera.area || "";
  document.getElementById("cameraVendor").value = camera.vendor || "A_CONFIRMAR";
  document.getElementById("cameraVendorDeviceId").value = camera.vendorDeviceId || "";
  document.getElementById("cameraProtocol").value = camera.protocol || "A_CONFIRMAR";
  document.getElementById("cameraBridge").value = camera.bridgeId || "";
  document.getElementById("cameraNotes").value = camera.notes || "";
  document.getElementById("cameraFormTitle").textContent = "Editar câmera";
  document.getElementById("cancelCameraEdit").classList.remove("is-hidden");
}

function resetForm() {
  document.getElementById("cameraForm").reset();
  document.getElementById("cameraRecordId").value = "";
  document.getElementById("cameraStore").value = "loja-01";
  document.getElementById("cameraVendor").value = "A_CONFIRMAR";
  document.getElementById("cameraVendorDeviceId").value = "";
  document.getElementById("cameraProtocol").value = "A_CONFIRMAR";
  document.getElementById("cameraFormTitle").textContent = "Cadastrar câmera";
  document.getElementById("cancelCameraEdit").classList.add("is-hidden");
  setMessage(document.getElementById("cameraMessage"), "");
}

function containsForbiddenSecret(text = "") {
  const value = String(text).toLowerCase();
  return ["rtsp://", "senha=", "password=", "token=", "apikey=", "api_key="].some(term => value.includes(term));
}

export function initializeCameras() {
  const form = document.getElementById("cameraForm");
  const message = document.getElementById("cameraMessage");
  form.addEventListener("submit", async event => {
    event.preventDefault();
    if (!canWrite("cameras")) {
      setMessage(message, "Somente administrador pode alterar câmeras.", "error");
      return;
    }
    const session = getCurrentSession();
    const id = document.getElementById("cameraRecordId").value;
    const payload = {
      cameraId: document.getElementById("cameraId").value.trim().toUpperCase(),
      name: document.getElementById("cameraName").value.trim(),
      storeId: document.getElementById("cameraStore").value.trim(),
      area: document.getElementById("cameraArea").value.trim(),
      vendor: document.getElementById("cameraVendor").value,
      vendorDeviceId: document.getElementById("cameraVendorDeviceId").value.trim(),
      protocol: document.getElementById("cameraProtocol").value,
      bridgeId: document.getElementById("cameraBridge").value.trim(),
      notes: document.getElementById("cameraNotes").value.trim(),
      updatedAt: now(),
      updatedBy: session.uid
    };
    if (!payload.cameraId || !payload.name || !payload.storeId || !payload.area) {
      setMessage(message, "Preencha ID, nome, loja e área.", "error");
      return;
    }
    if (containsForbiddenSecret(Object.values(payload).join(" "))) {
      setMessage(message, "Não salve RTSP, senha, token ou chave neste formulário.", "error");
      return;
    }
    if (payload.vendorDeviceId && !/^\d{6,16}$/.test(payload.vendorDeviceId)) {
      setMessage(message, "O ID do fabricante deve conter somente 6 a 16 números.", "error");
      return;
    }
    if (cameras.some(camera => camera.cameraId === payload.cameraId && camera.id !== id)) {
      setMessage(message, "Já existe uma câmera com este ID público.", "error");
      return;
    }
    try {
      if (id) {
        await updateData(`cameras/${id}`, payload);
        await appendAudit("CAMERA_UPDATED", { cameraRecordId: id, cameraId: payload.cameraId }, session);
      } else {
        payload.status = "UNCONFIGURED";
        payload.lastSeenAt = 0;
        payload.createdAt = now();
        payload.createdBy = session.uid;
        const cameraRecordId = await pushData("cameras", payload);
        await appendAudit("CAMERA_CREATED", { cameraRecordId, cameraId: payload.cameraId }, session);
      }
      resetForm();
      toast("Metadados da câmera salvos.", "success");
    } catch (error) {
      setMessage(message, `Falha ao salvar câmera: ${error.message}`, "error");
    }
  });
  document.getElementById("cancelCameraEdit").addEventListener("click", resetForm);

  initializeCameraQr(parsed => {
    const nextNumber = cameras.reduce((highest, camera) => {
      const match = String(camera.cameraId || "").match(/^CAM-(\d+)$/i);
      return Math.max(highest, Number(match?.[1] || 0));
    }, 0) + 1;
    const publicId = `CAM-${String(nextNumber).padStart(2, "0")}`;
    document.getElementById("cameraRecordId").value = "";
    document.getElementById("cameraId").value = publicId;
    document.getElementById("cameraName").value = parsed.vendor === "YOOSEE"
      ? `Câmera Yoosee ${parsed.vendorDeviceId ? parsed.vendorDeviceId.slice(-4) : publicId}`
      : `Câmera ${publicId}`;
    document.getElementById("cameraVendor").value = ["YOOSEE", "A_CONFIRMAR"].includes(parsed.vendor) ? parsed.vendor : "OUTRO";
    document.getElementById("cameraVendorDeviceId").value = parsed.vendorDeviceId || "";
    document.getElementById("cameraProtocol").value = parsed.protocol || "A_CONFIRMAR";
    document.getElementById("cameraBridge").value = document.getElementById("cameraBridge").value || "bridge-loja-01";
    document.getElementById("cameraNotes").value = parsed.vendor === "YOOSEE"
      ? "Câmera identificada por QR Yoosee. Protocolo RTSP/ONVIF ainda precisa ser confirmado. Código de convite não foi salvo."
      : "Câmera identificada por QR. Plataforma e protocolo ainda precisam ser confirmados.";
    document.getElementById("cameraFormTitle").textContent = "Confirmar câmera lida por QR";
    document.getElementById("cameraArea").focus();
    setMessage(message, `${parsed.safeSummary} Informe a área e clique em Salvar câmera.`, "success");
  });
}

export function startCamerasSubscription() {
  unsubscribe?.();
  unsubscribe = subscribeData("cameras", value => {
    cameras = objectEntries(value);
    renderCameras();
    document.dispatchEvent(new CustomEvent("smart24:cameras", { detail: cameras }));
  }, error => toast(`Não foi possível carregar câmeras: ${error.message}`, "error"));
}
