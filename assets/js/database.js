import { firebaseConfig } from "../../firebase-config.js";
import { createId, now } from "./utils.js";

const FIREBASE_VERSION = "10.12.5";
const DEMO_STORAGE_KEY = "smart24_fusion_demo_v1";
const listeners = new Map();

const backend = {
  mode: "pending",
  app: null,
  auth: null,
  db: null,
  appApi: null,
  authApi: null,
  dbApi: null,
  error: null
};

function isPlaceholder(value) {
  return !value || String(value).trim() === "COLE_AQUI";
}

export function isFirebaseConfigured() {
  return ["apiKey", "authDomain", "databaseURL", "projectId", "appId"]
    .every(key => !isPlaceholder(firebaseConfig[key]));
}

function initialDemoData() {
  const timestamp = now();
  return {
    roles: { "demo-user": "demo" },
    stores: { "loja-01": { name: "Loja demonstrativa", status: "pilot", createdAt: timestamp } },
    products: {
      "product-demo-water": {
        name: "Água mineral 500 ml",
        sku: "AGUA-500",
        barcode: "7890000000000",
        category: "Bebidas",
        status: "active",
        createdAt: timestamp,
        createdBy: "demo-user"
      }
    },
    tags: {},
    cameras: {
      "camera-demo-01": {
        cameraId: "CAM-01",
        name: "Entrada e acesso",
        storeId: "loja-01",
        area: "Entrada",
        protocol: "A_CONFIRMAR",
        bridgeId: "bridge-loja-01",
        status: "UNCONFIGURED",
        lastSeenAt: 0,
        notes: "Posição demonstrativa. Marca e modelo ainda não confirmados.",
        createdAt: timestamp,
        createdBy: "demo-user"
      }
    },
    cameraBridges: {},
    sessions: {},
    events: {},
    occurrences: {},
    auditLogs: {}
  };
}

function loadDemoStore() {
  try {
    const saved = localStorage.getItem(DEMO_STORAGE_KEY);
    if (saved) return JSON.parse(saved);
  } catch (error) {
    console.warn("Não foi possível ler o armazenamento local.", error);
  }
  const seed = initialDemoData();
  localStorage.setItem(DEMO_STORAGE_KEY, JSON.stringify(seed));
  return seed;
}

let demoStore = null;

function saveDemoStore() {
  localStorage.setItem(DEMO_STORAGE_KEY, JSON.stringify(demoStore));
}

function splitPath(path = "") {
  return String(path).split("/").filter(Boolean);
}

function demoRead(path = "") {
  const parts = splitPath(path);
  let current = demoStore;
  for (const part of parts) {
    if (!current || typeof current !== "object") return null;
    current = current[part];
  }
  return current ?? null;
}

function demoWrite(path, value) {
  const parts = splitPath(path);
  if (!parts.length) throw new Error("Caminho de gravação inválido.");
  let current = demoStore;
  parts.slice(0, -1).forEach(part => {
    if (!current[part] || typeof current[part] !== "object") current[part] = {};
    current = current[part];
  });
  const key = parts.at(-1);
  if (value === null) delete current[key];
  else current[key] = value;
  saveDemoStore();
  notifyDemo(path);
}

function pathsRelated(a, b) {
  return a === b || a.startsWith(`${b}/`) || b.startsWith(`${a}/`);
}

function notifyDemo(changedPath) {
  for (const [path, callbacks] of listeners.entries()) {
    if (!pathsRelated(path, changedPath)) continue;
    const value = structuredClone(demoRead(path));
    callbacks.forEach(callback => callback(value));
  }
}

export async function initializeBackend() {
  if (backend.mode !== "pending") return backend;
  if (!isFirebaseConfigured()) {
    demoStore = loadDemoStore();
    backend.mode = "demo";
    return backend;
  }

  try {
    const [appApi, authApi, dbApi] = await Promise.all([
      import(`https://www.gstatic.com/firebasejs/${FIREBASE_VERSION}/firebase-app.js`),
      import(`https://www.gstatic.com/firebasejs/${FIREBASE_VERSION}/firebase-auth.js`),
      import(`https://www.gstatic.com/firebasejs/${FIREBASE_VERSION}/firebase-database.js`)
    ]);
    backend.appApi = appApi;
    backend.authApi = authApi;
    backend.dbApi = dbApi;
    backend.app = appApi.initializeApp(firebaseConfig);
    backend.auth = authApi.getAuth(backend.app);
    backend.db = dbApi.getDatabase(backend.app);
    backend.mode = "firebase";
  } catch (error) {
    backend.error = error;
    backend.mode = "error";
    console.error("Falha ao inicializar Firebase", error);
  }
  return backend;
}

export function getBackendState() {
  return backend;
}

export async function signIn(email, password) {
  if (backend.mode !== "firebase") throw new Error("Firebase não está configurado.");
  return backend.authApi.signInWithEmailAndPassword(backend.auth, email, password);
}

export async function signOutBackend() {
  if (backend.mode === "firebase") return backend.authApi.signOut(backend.auth);
}

export function observeAuth(callback) {
  if (backend.mode === "firebase") return backend.authApi.onAuthStateChanged(backend.auth, callback);
  callback(null);
  return () => {};
}

export async function readData(path) {
  if (backend.mode === "demo") return structuredClone(demoRead(path));
  if (backend.mode !== "firebase") throw new Error("Backend indisponível.");
  const snapshot = await backend.dbApi.get(backend.dbApi.ref(backend.db, path));
  return snapshot.exists() ? snapshot.val() : null;
}

export async function setData(path, value) {
  if (backend.mode === "demo") {
    demoWrite(path, structuredClone(value));
    return value;
  }
  if (backend.mode !== "firebase") throw new Error("Backend indisponível.");
  await backend.dbApi.set(backend.dbApi.ref(backend.db, path), value);
  return value;
}

export async function updateData(path, value) {
  if (backend.mode === "demo") {
    const current = demoRead(path) || {};
    demoWrite(path, { ...current, ...structuredClone(value) });
    return value;
  }
  if (backend.mode !== "firebase") throw new Error("Backend indisponível.");
  await backend.dbApi.update(backend.dbApi.ref(backend.db, path), value);
  return value;
}

export async function pushData(path, value) {
  if (backend.mode === "demo") {
    const key = createId("REC");
    demoWrite(`${path}/${key}`, structuredClone(value));
    return key;
  }
  if (backend.mode !== "firebase") throw new Error("Backend indisponível.");
  const reference = backend.dbApi.push(backend.dbApi.ref(backend.db, path));
  await backend.dbApi.set(reference, value);
  return reference.key;
}

export async function removeData(path) {
  return setData(path, null);
}

export function subscribeData(path, callback, errorCallback = console.error) {
  if (backend.mode === "demo") {
    if (!listeners.has(path)) listeners.set(path, new Set());
    listeners.get(path).add(callback);
    callback(structuredClone(demoRead(path)));
    return () => listeners.get(path)?.delete(callback);
  }
  if (backend.mode !== "firebase") {
    errorCallback(new Error("Backend indisponível."));
    return () => {};
  }
  const reference = backend.dbApi.ref(backend.db, path);
  return backend.dbApi.onValue(reference, snapshot => callback(snapshot.exists() ? snapshot.val() : null), errorCallback);
}

export async function appendAudit(action, details, actor = {}) {
  const payload = {
    action,
    details: details || {},
    actorUid: actor.uid || "unknown",
    actorEmail: actor.email || "unknown",
    createdAt: now()
  };
  try {
    return await pushData("auditLogs", payload);
  } catch (error) {
    console.warn("Auditoria não registrada pelo cliente.", error);
    return null;
  }
}

export function resetDemoData() {
  if (backend.mode !== "demo") return;
  demoStore = initialDemoData();
  saveDemoStore();
  notifyDemo("");
}
