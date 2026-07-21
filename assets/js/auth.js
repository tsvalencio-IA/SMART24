import { getBackendState, observeAuth, readData, signIn, signOutBackend } from "./database.js";
import { getInitials, roleLabel, setMessage, toast } from "./utils.js";

let currentSession = null;
let sessionCallback = () => {};

const allowedRoles = new Set(["admin", "operator", "auditor"]);

function showLogin() {
  document.getElementById("loginScreen")?.classList.remove("is-hidden");
  document.getElementById("appShell")?.classList.add("is-hidden");
}

function showApp() {
  document.getElementById("loginScreen")?.classList.add("is-hidden");
  document.getElementById("appShell")?.classList.remove("is-hidden");
}

function renderUser(session) {
  document.getElementById("userEmail").textContent = session.email;
  document.getElementById("userRole").textContent = roleLabel(session.role);
  document.getElementById("userInitials").textContent = getInitials(session.email);
}

async function resolveFirebaseUser(user) {
  if (!user) {
    currentSession = null;
    sessionCallback(null);
    showLogin();
    return;
  }
  const role = await readData(`roles/${user.uid}`);
  if (!allowedRoles.has(role)) {
    await signOutBackend();
    setMessage(document.getElementById("loginMessage"), "Usuário autenticado, mas sem função autorizada em roles/UID.", "error");
    showLogin();
    return;
  }
  currentSession = { uid: user.uid, email: user.email || "usuário", role, isDemo: false };
  renderUser(currentSession);
  showApp();
  sessionCallback(currentSession);
}

export function getCurrentSession() {
  return currentSession;
}

export function canWrite(resource = "") {
  if (!currentSession) return false;
  if (currentSession.role === "demo") return true;
  if (currentSession.role === "admin") return true;
  if (currentSession.role === "operator") return ["products", "labels", "events"].includes(resource);
  if (currentSession.role === "auditor") return resource === "occurrences";
  return false;
}

export function initializeAuth(onSession) {
  sessionCallback = onSession || (() => {});
  const state = getBackendState();
  const form = document.getElementById("loginForm");
  const demoButton = document.getElementById("demoLoginButton");
  const message = document.getElementById("loginMessage");
  const backendStatus = document.getElementById("backendStatus");

  if (state.mode === "demo") {
    backendStatus.textContent = "Firebase ainda não configurado. A demonstração salva somente neste navegador.";
    demoButton.classList.remove("is-hidden");
  } else if (state.mode === "firebase") {
    backendStatus.textContent = "Firebase configurado. Use um usuário autorizado.";
  } else {
    backendStatus.textContent = "Não foi possível carregar o Firebase. Confira a configuração e a internet.";
    setMessage(message, state.error?.message || "Falha de inicialização.", "error");
  }

  form.addEventListener("submit", async event => {
    event.preventDefault();
    setMessage(message, "");
    const email = document.getElementById("loginEmail").value.trim();
    const password = document.getElementById("loginPassword").value;
    const button = form.querySelector("button[type='submit']");
    if (state.mode !== "firebase") {
      setMessage(message, "Preencha firebase-config.js para usar autenticação real.", "error");
      return;
    }
    button.disabled = true;
    try {
      await signIn(email, password);
    } catch (error) {
      const friendly = {
        "auth/invalid-credential": "E-mail ou senha inválidos.",
        "auth/too-many-requests": "Muitas tentativas. Aguarde e tente novamente.",
        "auth/network-request-failed": "Falha de rede. Verifique sua conexão."
      }[error.code] || `Não foi possível entrar: ${error.message}`;
      setMessage(message, friendly, "error");
    } finally {
      button.disabled = false;
    }
  });

  demoButton.addEventListener("click", () => {
    currentSession = { uid: "demo-user", email: "demonstracao@smart24.local", role: "demo", isDemo: true };
    renderUser(currentSession);
    showApp();
    sessionCallback(currentSession);
    toast("Demonstração local aberta. Nenhum dado foi enviado ao Firebase.", "success");
  });

  document.getElementById("logoutButton").addEventListener("click", async () => {
    if (currentSession?.isDemo) {
      currentSession = null;
      sessionCallback(null);
      showLogin();
      return;
    }
    await signOutBackend();
  });

  if (state.mode === "firebase") observeAuth(resolveFirebaseUser);
  else showLogin();
}
