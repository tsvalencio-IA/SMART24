import { appendAudit, setData, subscribeData } from "./database.js";
import { canWrite, getCurrentSession } from "./auth.js";
import { escapeHtml, now, setMessage, toast } from "./utils.js";

let integration = null;
let unsubscribe = null;

function renderIntegration() {
  const email = document.getElementById("yooseeAccountEmail");
  const status = document.getElementById("yooseeAccountStatus");
  const summary = document.getElementById("yooseeAccountSummary");
  if (!email || !status || !summary) return;
  email.value = integration?.accountEmail || "";
  status.value = integration?.status || "A_CRIAR";
  summary.innerHTML = integration?.accountEmail
    ? `<strong>${escapeHtml(integration.accountEmail)}</strong><span>${integration.status === "CAMERA_COMPARTILHADA" ? "Conta criada e ao menos uma câmera compartilhada." : integration.status === "CONTA_CRIADA" ? "Conta criada no aplicativo Yoosee; compartilhamento ainda deve ser confirmado." : "E-mail reservado; conta ainda precisa ser criada manualmente no Yoosee."}</span>`
    : "<strong>Nenhuma conta informada</strong><span>Crie um e-mail dedicado, registre-o no aplicativo Yoosee e salve somente o endereço aqui.</span>";
}

export function initializeYooseeIntegration() {
  const form = document.getElementById("yooseeAccountForm");
  const message = document.getElementById("yooseeAccountMessage");
  form?.addEventListener("submit", async event => {
    event.preventDefault();
    if (!canWrite("cameras")) {
      setMessage(message, "Somente administrador pode alterar a integração Yoosee.", "error");
      return;
    }
    const session = getCurrentSession();
    const accountEmail = document.getElementById("yooseeAccountEmail").value.trim().toLowerCase();
    const status = document.getElementById("yooseeAccountStatus").value;
    if (!/^\S+@\S+\.\S+$/.test(accountEmail)) {
      setMessage(message, "Informe um e-mail válido criado exclusivamente para as câmeras.", "error");
      return;
    }
    const payload = {
      provider: "YOOSEE",
      accountEmail,
      status,
      passwordStored: false,
      updatedAt: now(),
      updatedBy: session.uid
    };
    try {
      await setData("integrations/yoosee", payload);
      await appendAudit("YOOSEE_ACCOUNT_METADATA_UPDATED", { accountEmail, status }, session);
      setMessage(message, "E-mail operacional salvo. Nenhuma senha foi armazenada.", "success");
      toast("Conta Yoosee registrada no SMART24 sem senha.", "success");
    } catch (error) {
      setMessage(message, `Falha ao salvar: ${error.message}`, "error");
    }
  });
}

export function startYooseeSubscription() {
  unsubscribe?.();
  unsubscribe = subscribeData("integrations/yoosee", value => {
    integration = value || null;
    renderIntegration();
  }, error => toast(`Não foi possível carregar a integração Yoosee: ${error.message}`, "error"));
}
