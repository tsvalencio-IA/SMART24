import { setMessage } from "./utils.js";

let stream = null;
let scanning = false;
let callback = () => {};

function elements() {
  return {
    modal: document.getElementById("tagQrModal"),
    video: document.getElementById("tagQrVideo"),
    canvas: document.getElementById("tagQrCanvas"),
    status: document.getElementById("tagQrStatus"),
    input: document.getElementById("tagQrFileInput")
  };
}

function parseTagPayload(raw) {
  const text = String(raw || "").trim();
  if (!text) throw new Error("QR vazio.");
  try {
    const data = JSON.parse(text);
    if (data?.system === "SMART24" && data?.serial) return { serial: String(data.serial), payload: data };
  } catch {}
  if (/^TAG-[A-Z0-9_-]+$/i.test(text)) return { serial: text.toUpperCase(), payload: { serial: text.toUpperCase() } };
  throw new Error("Este QR não é uma etiqueta SMART24 reconhecida.");
}

function stop() {
  scanning = false;
  stream?.getTracks().forEach(track => track.stop());
  stream = null;
  const { video } = elements();
  if (video) video.srcObject = null;
}

function close() {
  stop();
  elements().modal?.classList.add("is-hidden");
}

async function decodeCanvas(canvas) {
  const context = canvas.getContext("2d", { willReadFrequently: true });
  const image = context.getImageData(0, 0, canvas.width, canvas.height);
  if (typeof window.jsQR !== "function") throw new Error("Leitor QR não carregado. Atualize a página com internet.");
  const result = window.jsQR(image.data, image.width, image.height, { inversionAttempts: "attemptBoth" });
  return result?.data || "";
}

async function scanLoop() {
  const { video, canvas, status } = elements();
  if (!scanning || !stream || !video?.videoWidth) {
    if (scanning) requestAnimationFrame(scanLoop);
    return;
  }
  const maxWidth = 960;
  const scale = Math.min(1, maxWidth / video.videoWidth);
  canvas.width = Math.round(video.videoWidth * scale);
  canvas.height = Math.round(video.videoHeight * scale);
  canvas.getContext("2d").drawImage(video, 0, 0, canvas.width, canvas.height);
  try {
    const raw = await decodeCanvas(canvas);
    if (raw) {
      const parsed = parseTagPayload(raw);
      callback(parsed);
      setMessage(status, `Etiqueta ${parsed.serial} reconhecida.`, "success");
      window.setTimeout(close, 500);
      return;
    }
  } catch (error) {
    setMessage(status, error.message, "error");
  }
  window.setTimeout(scanLoop, 140);
}

async function startCamera() {
  const { video, status, modal } = elements();
  modal.classList.remove("is-hidden");
  stop();
  try {
    stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: "environment" }, width: { ideal: 1920 }, height: { ideal: 1080 } }, audio: false });
    video.srcObject = stream;
    await video.play();
    scanning = true;
    setMessage(status, "Aponte para o QR da etiqueta SMART24.");
    scanLoop();
  } catch (error) {
    setMessage(status, `Não foi possível abrir a câmera: ${error.message}`, "error");
  }
}

async function readFile(file) {
  const { canvas, status, modal } = elements();
  if (!file) return;
  modal.classList.remove("is-hidden");
  try {
    const bitmap = await createImageBitmap(file);
    const scale = Math.min(1, 1600 / bitmap.width);
    canvas.width = Math.round(bitmap.width * scale);
    canvas.height = Math.round(bitmap.height * scale);
    canvas.getContext("2d").drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    const raw = await decodeCanvas(canvas);
    if (!raw) throw new Error("Nenhum QR legível foi encontrado na imagem.");
    const parsed = parseTagPayload(raw);
    callback(parsed);
    setMessage(status, `Etiqueta ${parsed.serial} reconhecida.`, "success");
    window.setTimeout(close, 500);
  } catch (error) {
    setMessage(status, error.message, "error");
  }
}

export function initializeTagScanner(onRead) {
  callback = onRead || (() => {});
  document.getElementById("scanTagButton")?.addEventListener("click", () => elements().modal.classList.remove("is-hidden"));
  document.getElementById("startTagQrButton")?.addEventListener("click", startCamera);
  document.getElementById("closeTagQrButton")?.addEventListener("click", close);
  document.getElementById("chooseTagQrFileButton")?.addEventListener("click", () => elements().input.click());
  elements().input?.addEventListener("change", event => readFile(event.target.files?.[0]));
  elements().modal?.addEventListener("click", event => { if (event.target === elements().modal) close(); });
}
