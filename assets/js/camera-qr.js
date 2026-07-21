import { toast } from "./utils.js";

let mediaStream = null;
let animationFrame = null;
let onDecodedCallback = () => {};

function getElements() {
  return {
    modal: document.getElementById("cameraQrModal"),
    video: document.getElementById("cameraQrVideo"),
    canvas: document.getElementById("cameraQrCanvas"),
    status: document.getElementById("cameraQrStatus"),
    openButton: document.getElementById("openCameraQrButton"),
    closeButton: document.getElementById("closeCameraQrButton"),
    startButton: document.getElementById("startCameraQrButton"),
    fileInput: document.getElementById("cameraQrFileInput"),
    chooseFileButton: document.getElementById("chooseCameraQrFileButton")
  };
}

function setStatus(message, type = "") {
  const { status } = getElements();
  if (!status) return;
  status.textContent = message;
  status.classList.toggle("is-error", type === "error");
  status.classList.toggle("is-success", type === "success");
}

function stopScanner() {
  if (animationFrame) cancelAnimationFrame(animationFrame);
  animationFrame = null;
  if (mediaStream) mediaStream.getTracks().forEach(track => track.stop());
  mediaStream = null;
  const { video } = getElements();
  if (video) {
    video.pause();
    video.srcObject = null;
  }
}

function closeModal() {
  stopScanner();
  const { modal } = getElements();
  modal?.classList.add("is-hidden");
}

function openModal() {
  const { modal } = getElements();
  modal?.classList.remove("is-hidden");
  setStatus("Aponte para o QR da etiqueta física ou para o QR/link de compartilhamento Yoosee.");
}

function normalizeDeviceId(value = "") {
  const match = String(value).match(/\d{6,16}/);
  return match ? match[0] : "";
}

export function parseCameraQrPayload(rawValue = "") {
  const raw = String(rawValue || "").trim();
  if (!raw) throw new Error("O QR Code está vazio.");

  const result = {
    vendor: "A_CONFIRMAR",
    vendorDeviceId: "",
    sourceType: "QR_CODE",
    shareExpiresAt: 0,
    protocol: "A_CONFIRMAR",
    safeSummary: "QR lido. Confirme os dados antes de salvar."
  };

  try {
    const url = new URL(raw);
    const host = url.hostname.toLowerCase();
    const deviceId = normalizeDeviceId(url.searchParams.get("DeviceID") || url.searchParams.get("deviceId") || "");

    if (host.includes("yoosee")) {
      result.vendor = "YOOSEE";
      result.vendorDeviceId = deviceId;
      const expireSeconds = Number(url.searchParams.get("ExpireTime") || 0);
      result.shareExpiresAt = Number.isFinite(expireSeconds) && expireSeconds > 0 ? expireSeconds * 1000 : 0;
      result.safeSummary = deviceId
        ? `QR Yoosee reconhecido. Dispositivo ${deviceId}. Código de convite e token foram descartados.`
        : "QR Yoosee reconhecido. Código de convite e token foram descartados.";
      return result;
    }
  } catch (_) {
    // O QR pode ser texto simples ou JSON.
  }

  try {
    const parsed = JSON.parse(raw);
    const deviceId = normalizeDeviceId(parsed.DeviceID || parsed.deviceId || parsed.id || "");
    if (deviceId) {
      result.vendor = String(parsed.vendor || parsed.platform || "A_CONFIRMAR").toUpperCase();
      result.vendorDeviceId = deviceId;
      result.safeSummary = `QR de dispositivo reconhecido. ID ${deviceId}. Nenhuma senha foi armazenada.`;
      return result;
    }
  } catch (_) {
    // Não é JSON.
  }

  const parameterMatch = raw.match(/(?:DeviceID|deviceId|ID)\s*[:=]\s*(\d{6,16})/i);
  const numericOnly = /^\d{6,16}$/.test(raw) ? raw : "";
  const deviceId = normalizeDeviceId(parameterMatch?.[1] || numericOnly);
  if (deviceId) {
    result.vendorDeviceId = deviceId;
    result.safeSummary = `ID de dispositivo ${deviceId} reconhecido. Plataforma ainda precisa ser confirmada.`;
    return result;
  }

  throw new Error("QR lido, mas o formato não contém um ID de câmera reconhecível.");
}

function handleDecoded(rawValue) {
  try {
    const parsed = parseCameraQrPayload(rawValue);
    stopScanner();
    setStatus(parsed.safeSummary, "success");
    onDecodedCallback(parsed);
    window.setTimeout(closeModal, 850);
  } catch (error) {
    setStatus(error.message, "error");
  }
}

function decodeCanvas(canvas, context) {
  if (typeof window.jsQR !== "function") throw new Error("Leitor QR não carregou. Verifique a internet e tente novamente.");
  const imageData = context.getImageData(0, 0, canvas.width, canvas.height);
  const code = window.jsQR(imageData.data, imageData.width, imageData.height, { inversionAttempts: "attemptBoth" });
  return code?.data || "";
}

async function startLiveScanner() {
  if (!navigator.mediaDevices?.getUserMedia) {
    setStatus("Este navegador não liberou acesso à câmera. Use a opção Escolher imagem.", "error");
    return;
  }
  stopScanner();
  const { video, canvas } = getElements();
  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: { ideal: "environment" }, width: { ideal: 1280 }, height: { ideal: 720 } },
      audio: false
    });
    video.srcObject = mediaStream;
    await video.play();
    setStatus("Câmera aberta. Centralize o QR dentro da imagem.");
    const context = canvas.getContext("2d", { willReadFrequently: true });

    const scanFrame = () => {
      if (!mediaStream) return;
      if (video.readyState >= HTMLMediaElement.HAVE_CURRENT_DATA) {
        canvas.width = video.videoWidth || 640;
        canvas.height = video.videoHeight || 480;
        context.drawImage(video, 0, 0, canvas.width, canvas.height);
        const decoded = decodeCanvas(canvas, context);
        if (decoded) {
          handleDecoded(decoded);
          return;
        }
      }
      animationFrame = requestAnimationFrame(scanFrame);
    };
    scanFrame();
  } catch (error) {
    stopScanner();
    const message = error.name === "NotAllowedError"
      ? "Permissão da câmera negada. Autorize no navegador ou escolha uma imagem do QR."
      : `Não foi possível abrir a câmera: ${error.message}`;
    setStatus(message, "error");
  }
}

function decodeFile(file) {
  if (!file) return;
  const { canvas } = getElements();
  const reader = new FileReader();
  reader.onerror = () => setStatus("Não foi possível ler a imagem selecionada.", "error");
  reader.onload = () => {
    const image = new Image();
    image.onerror = () => setStatus("A imagem não pôde ser aberta.", "error");
    image.onload = () => {
      try {
        const maxSide = 1800;
        const ratio = Math.min(1, maxSide / Math.max(image.naturalWidth, image.naturalHeight));
        canvas.width = Math.max(1, Math.round(image.naturalWidth * ratio));
        canvas.height = Math.max(1, Math.round(image.naturalHeight * ratio));
        const context = canvas.getContext("2d", { willReadFrequently: true });
        context.drawImage(image, 0, 0, canvas.width, canvas.height);
        const decoded = decodeCanvas(canvas, context);
        if (!decoded) throw new Error("Nenhum QR Code foi encontrado nessa imagem.");
        handleDecoded(decoded);
      } catch (error) {
        setStatus(error.message, "error");
      }
    };
    image.src = reader.result;
  };
  reader.readAsDataURL(file);
}

export function initializeCameraQr(onDecoded) {
  onDecodedCallback = typeof onDecoded === "function" ? onDecoded : () => {};
  const { openButton, closeButton, startButton, fileInput, chooseFileButton, modal } = getElements();
  openButton?.addEventListener("click", openModal);
  closeButton?.addEventListener("click", closeModal);
  startButton?.addEventListener("click", startLiveScanner);
  chooseFileButton?.addEventListener("click", () => fileInput?.click());
  fileInput?.addEventListener("change", event => {
    decodeFile(event.target.files?.[0]);
    event.target.value = "";
  });
  modal?.addEventListener("click", event => {
    if (event.target === modal) closeModal();
  });
  window.addEventListener("beforeunload", stopScanner);

  if (typeof window.jsQR !== "function") {
    toast("Leitor QR será carregado pela internet quando necessário.");
  }
}
