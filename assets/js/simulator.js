import { appendAudit, pushData, setData } from "./database.js";
import { canWrite, getCurrentSession } from "./auth.js";
import { confidenceLabel, createId, escapeHtml, eventLabel, formatTime, now, toast } from "./utils.js";

let running = false;
let generatedEvents = [];

const pause = ms => new Promise(resolve => window.setTimeout(resolve, ms));

const base = {
  storeId: "loja-01",
  cameraId: "CAM-04",
  sessionId: "SESSION-DEMO",
  personId: "PERSON-01",
  productId: "PRODUCT-DEMO-A",
  productName: "Produto A",
  sku: "SKU-A",
  tagId: "TAG-DEMO-A-000001",
  quantity: 1,
  confidence: .94,
  simulation: true
};

function step(type, overrides = {}, movement = null, delay = 680) {
  return { type, overrides, movement, delay };
}

const scenarios = {
  regular: {
    title: "Compra regular",
    steps: [
      step("ACCESS_GRANTED", { cameraId: "CAM-01", confidence: .99 }, "entry"),
      step("PERSON_ENTERED", { cameraId: "CAM-01", confidence: .98 }, "entrance"),
      step("PERSON_MOVED", { cameraId: "CAM-04", confidence: .95 }, "shelf"),
      step("PRODUCT_PICKUP", {}, "pickup"),
      step("PERSON_MOVED", { cameraId: "CAM-07" }, "checkout"),
      step("CHECKOUT_ITEM_REGISTERED", { cameraId: "CAM-07", confidence: 1 }, "register"),
      step("PAYMENT_APPROVED", { cameraId: "CAM-07", confidence: 1 }, "paid"),
      step("PERSON_EXITED", { cameraId: "CAM-07", confidence: .97 }, "exit")
    ],
    items: [{ sku: "SKU-A", name: "Produto A", pickedUp: 1, returned: 0, registered: 1, paid: 1 }]
  },
  returned: {
    title: "Produto devolvido",
    steps: [
      step("ACCESS_GRANTED", { cameraId: "CAM-01" }, "entry"),
      step("PERSON_ENTERED", { cameraId: "CAM-01" }, "entrance"),
      step("PERSON_MOVED", {}, "shelf"),
      step("PRODUCT_PICKUP", {}, "pickup"),
      step("PERSON_MOVED", {}, "middle"),
      step("PRODUCT_RETURN", { confidence: .91 }, "return"),
      step("PERSON_MOVED", { cameraId: "CAM-07" }, "checkout"),
      step("PAYMENT_APPROVED", { cameraId: "CAM-07", productId: "", productName: "", sku: "", tagId: "", quantity: 0, confidence: 1 }, "paid"),
      step("PERSON_EXITED", { cameraId: "CAM-07" }, "exit")
    ],
    items: [{ sku: "SKU-A", name: "Produto A", pickedUp: 1, returned: 1, registered: 0, paid: 0 }]
  },
  missing: {
    title: "Item não registrado",
    steps: [
      step("ACCESS_GRANTED", { cameraId: "CAM-01" }, "entry"),
      step("PERSON_ENTERED", { cameraId: "CAM-01" }, "entrance"),
      step("PERSON_MOVED", {}, "shelf"),
      step("PRODUCT_PICKUP", {}, "pickup"),
      step("PERSON_MOVED", { cameraId: "CAM-07" }, "checkout"),
      step("PAYMENT_APPROVED", { cameraId: "CAM-07", productId: "", productName: "", sku: "", tagId: "", quantity: 0, confidence: 1 }, "paid"),
      step("PERSON_EXITED", { cameraId: "CAM-07" }, "exit")
    ],
    items: [{ sku: "SKU-A", name: "Produto A", pickedUp: 1, returned: 0, registered: 0, paid: 0 }]
  },
  threePeople: {
    title: "Três usuários",
    people: 3,
    steps: [
      step("PERSON_ENTERED", { personId: "PERSON-01", sessionId: "SESSION-DEMO-P1", cameraId: "CAM-01" }, "p1-entry"),
      step("PERSON_ENTERED", { personId: "PERSON-02", sessionId: "SESSION-DEMO-P2", cameraId: "CAM-01" }, "p2-entry"),
      step("PERSON_ENTERED", { personId: "PERSON-03", sessionId: "SESSION-DEMO-P3", cameraId: "CAM-01" }, "p3-entry"),
      step("PRODUCT_PICKUP", { personId: "PERSON-01", sessionId: "SESSION-DEMO-P1" }, "p1-pickup"),
      step("PRODUCT_PICKUP", { personId: "PERSON-02", sessionId: "SESSION-DEMO-P2", productId: "PRODUCT-DEMO-B", productName: "Produto B", sku: "SKU-B", tagId: "TAG-DEMO-B-000001", confidence: .9 }, "p2-pickup"),
      step("PRODUCT_TRANSFER", { personId: "PERSON-03", fromPersonId: "PERSON-02", sessionId: "SESSION-DEMO-P3", productId: "PRODUCT-DEMO-B", productName: "Produto B", sku: "SKU-B", tagId: "TAG-DEMO-B-000001", confidence: .82 }, "transfer"),
      step("CHECKOUT_ITEM_REGISTERED", { personId: "PERSON-01", sessionId: "SESSION-DEMO-P1", cameraId: "CAM-07", confidence: 1 }, "p1-checkout"),
      step("CHECKOUT_ITEM_REGISTERED", { personId: "PERSON-03", sessionId: "SESSION-DEMO-P3", cameraId: "CAM-07", productId: "PRODUCT-DEMO-B", productName: "Produto B", sku: "SKU-B", tagId: "TAG-DEMO-B-000001", confidence: 1 }, "p3-checkout"),
      step("PAYMENT_APPROVED", { personId: "PERSON-01", sessionId: "SESSION-DEMO-P1", cameraId: "CAM-07", confidence: 1 }, "p1-paid"),
      step("PAYMENT_APPROVED", { personId: "PERSON-03", sessionId: "SESSION-DEMO-P3", cameraId: "CAM-07", productId: "PRODUCT-DEMO-B", productName: "Produto B", sku: "SKU-B", tagId: "TAG-DEMO-B-000001", confidence: 1 }, "p3-paid"),
      step("PERSON_EXITED", { personId: "PERSON-01", sessionId: "SESSION-DEMO-P1", cameraId: "CAM-07" }, "all-exit"),
      step("PERSON_EXITED", { personId: "PERSON-02", sessionId: "SESSION-DEMO-P2", cameraId: "CAM-07", productId: "", productName: "", sku: "", tagId: "", quantity: 0 }, null, 250),
      step("PERSON_EXITED", { personId: "PERSON-03", sessionId: "SESSION-DEMO-P3", cameraId: "CAM-07", productId: "", productName: "", sku: "", tagId: "", quantity: 0 }, null, 250)
    ],
    items: [
      { sku: "SKU-A", name: "Produto A / Sessão P1", pickedUp: 1, returned: 0, registered: 1, paid: 1 },
      { sku: "SKU-B", name: "Produto B / transferido P2→P3", pickedUp: 1, returned: 0, registered: 1, paid: 1 }
    ]
  },
  cameraOffline: {
    title: "Câmera indisponível",
    steps: [
      step("ACCESS_GRANTED", { cameraId: "CAM-01" }, "entry"),
      step("PERSON_ENTERED", { cameraId: "CAM-01" }, "entrance"),
      step("CAMERA_OFFLINE", { cameraId: "CAM-04", productId: "", productName: "", sku: "", tagId: "", quantity: 0, confidence: 0 }, "offline"),
      step("IMAGE_INSUFFICIENT", { cameraId: "CAM-04", confidence: .22 }, "ambiguous"),
      step("CHECKOUT_ITEM_REGISTERED", { cameraId: "CAM-07", confidence: 1 }, "register"),
      step("PAYMENT_APPROVED", { cameraId: "CAM-07", confidence: 1 }, "paid"),
      step("CAMERA_ONLINE", { cameraId: "CAM-04", productId: "", productName: "", sku: "", tagId: "", quantity: 0, confidence: 1 }, "online"),
      step("PERSON_EXITED", { cameraId: "CAM-07" }, "exit")
    ],
    items: [{ sku: "SKU-A", name: "Produto A — imagem insuficiente", pickedUp: "?", returned: "?", registered: 1, paid: 1, insufficient: true }]
  },
  wrongProduct: {
    title: "Produto diferente pago",
    steps: [
      step("ACCESS_GRANTED", { cameraId: "CAM-01" }, "entry"),
      step("PERSON_ENTERED", { cameraId: "CAM-01" }, "entrance"),
      step("PRODUCT_PICKUP", {}, "pickup"),
      step("CHECKOUT_ITEM_REGISTERED", { cameraId: "CAM-07", productId: "PRODUCT-DEMO-B", productName: "Produto B", sku: "SKU-B", tagId: "", confidence: 1 }, "register"),
      step("PAYMENT_APPROVED", { cameraId: "CAM-07", productId: "PRODUCT-DEMO-B", productName: "Produto B", sku: "SKU-B", tagId: "", confidence: 1 }, "paid"),
      step("PERSON_EXITED", { cameraId: "CAM-07" }, "exit")
    ],
    items: [
      { sku: "SKU-A", name: "Produto A", pickedUp: 1, returned: 0, registered: 0, paid: 0 },
      { sku: "SKU-B", name: "Produto B", pickedUp: 0, returned: 0, registered: 1, paid: 1 }
    ]
  },
  paymentRejected: {
    title: "Pagamento cancelado",
    steps: [
      step("ACCESS_GRANTED", { cameraId: "CAM-01" }, "entry"),
      step("PERSON_ENTERED", { cameraId: "CAM-01" }, "entrance"),
      step("PRODUCT_PICKUP", {}, "pickup"),
      step("CHECKOUT_ITEM_REGISTERED", { cameraId: "CAM-07", confidence: 1 }, "register"),
      step("PAYMENT_REJECTED", { cameraId: "CAM-07", confidence: 1 }, "rejected"),
      step("PERSON_EXITED", { cameraId: "CAM-07" }, "exit")
    ],
    items: [{ sku: "SKU-A", name: "Produto A", pickedUp: 1, returned: 0, registered: 1, paid: 0 }]
  },
  ambiguous: {
    title: "Interação ambígua",
    steps: [
      step("ACCESS_GRANTED", { cameraId: "CAM-01" }, "entry"),
      step("PERSON_ENTERED", { cameraId: "CAM-01" }, "entrance"),
      step("PERSON_MOVED", {}, "shelf"),
      step("AMBIGUOUS_INTERACTION", { confidence: .41 }, "ambiguous"),
      step("IMAGE_INSUFFICIENT", { confidence: .28 }, "ambiguous"),
      step("CHECKOUT_ITEM_REGISTERED", { cameraId: "CAM-07", confidence: 1 }, "register"),
      step("PAYMENT_APPROVED", { cameraId: "CAM-07", confidence: 1 }, "paid"),
      step("PERSON_EXITED", { cameraId: "CAM-07" }, "exit")
    ],
    items: [{ sku: "SKU-A", name: "Produto A — interação ambígua", pickedUp: "?", returned: "?", registered: 1, paid: 1, insufficient: true }]
  }
};

function people() {
  return [1, 2, 3].map(number => document.getElementById(`simPerson${number}`));
}

function resetScene() {
  generatedEvents = [];
  people().forEach(person => {
    person.classList.remove("is-visible");
    person.style.left = "46%";
    person.style.top = "auto";
    person.style.bottom = "-15%";
    person.style.opacity = "1";
  });
  const product = document.getElementById("simProduct");
  product.classList.remove("is-visible");
  product.style.left = "15%";
  product.style.top = "46%";
  document.getElementById("simTimeline").className = "timeline empty-state";
  document.getElementById("simTimeline").innerHTML = "<strong>Sessão zerada</strong><span>Nenhum evento foi gerado.</span>";
  document.getElementById("simResult").className = "empty-state";
  document.getElementById("simResult").innerHTML = "<strong>Aguardando reconciliação</strong><span>O resultado só será preenchido depois dos eventos do cenário.</span>";
  document.getElementById("simEventCount").textContent = "0";
  setStatus("Aguardando", "Selecione um cenário e pressione executar.", false);
}

function setStatus(title, text, active = false) {
  document.getElementById("simStatusTitle").textContent = title;
  document.getElementById("simStatusText").textContent = text;
  document.getElementById("simStatusDot").classList.toggle("is-running", active);
}

function move(name) {
  const [p1, p2, p3] = people();
  const product = document.getElementById("simProduct");
  const show = (person, left, top) => {
    person.classList.add("is-visible"); person.style.bottom = "auto"; person.style.left = left; person.style.top = top;
  };
  switch (name) {
    case "entry": case "entrance": show(p1, "47%", "84%"); break;
    case "shelf": show(p1, "24%", "48%"); break;
    case "pickup": show(p1, "24%", "48%"); product.classList.add("is-visible"); product.style.left = "28%"; product.style.top = "49%"; break;
    case "middle": show(p1, "47%", "48%"); product.style.left = "51%"; product.style.top = "49%"; break;
    case "return": show(p1, "24%", "55%"); product.style.left = "15%"; product.style.top = "55%"; window.setTimeout(() => product.classList.remove("is-visible"), 550); break;
    case "checkout": case "register": case "paid": case "rejected": show(p1, "65%", "70%"); product.style.left = "70%"; product.style.top = "70%"; break;
    case "exit": show(p1, "47%", "88%"); window.setTimeout(() => { p1.style.opacity = "0"; product.classList.remove("is-visible"); }, 450); break;
    case "offline": document.querySelector(".camera-4").style.background = "#6a2430"; break;
    case "online": document.querySelector(".camera-4").style.background = "#1d3d5c"; break;
    case "ambiguous": show(p1, "28%", "50%"); break;
    case "p1-entry": show(p1, "42%", "84%"); break;
    case "p2-entry": show(p2, "49%", "84%"); break;
    case "p3-entry": show(p3, "56%", "84%"); break;
    case "p1-pickup": show(p1, "24%", "40%"); product.classList.add("is-visible"); product.style.left = "29%"; product.style.top = "41%"; break;
    case "p2-pickup": show(p2, "76%", "35%"); break;
    case "transfer": show(p2, "48%", "48%"); show(p3, "55%", "48%"); break;
    case "p1-checkout": show(p1, "63%", "70%"); break;
    case "p3-checkout": show(p3, "71%", "70%"); break;
    case "p1-paid": show(p1, "63%", "73%"); break;
    case "p3-paid": show(p3, "71%", "73%"); break;
    case "all-exit": [p1,p2,p3].forEach((person,index) => show(person, `${42 + index*7}%`, "88%")); break;
  }
}

function addTimeline(event) {
  const timeline = document.getElementById("simTimeline");
  if (timeline.classList.contains("empty-state")) {
    timeline.className = "timeline";
    timeline.innerHTML = "";
  }
  const item = document.createElement("div");
  item.className = "timeline-item";
  item.innerHTML = `<span class="timeline-dot"></span><div class="timeline-copy"><strong>${escapeHtml(eventLabel(event.type))}</strong><span>${escapeHtml(event.personId || "Sistema")} · ${escapeHtml(event.cameraId || "sem câmera")} · confiança ${confidenceLabel(event.confidence)}</span></div><time>${formatTime(event.createdAt)}</time>`;
  timeline.appendChild(item);
  timeline.scrollTop = timeline.scrollHeight;
  document.getElementById("simEventCount").textContent = String(generatedEvents.length);
}

function reconcile(items) {
  return items.map(item => {
    if (item.insufficient) return { ...item, expected: "?", difference: "?", review: true, reason: "Imagem insuficiente ou interação ambígua" };
    const expected = Number(item.pickedUp) - Number(item.returned);
    const difference = expected - Number(item.paid);
    return { ...item, expected, difference, review: difference !== 0, reason: difference !== 0 ? "Quantidade esperada diferente da quantidade paga" : "Sem divergência" };
  });
}

function renderResult(results) {
  const result = document.getElementById("simResult");
  const needsReview = results.some(item => item.review);
  result.className = "";
  result.innerHTML = `
    <div class="table-wrap"><table class="reconciliation-table"><thead><tr><th>Produto / SKU</th><th>Retirado</th><th>Devolvido</th><th>Esperado</th><th>Registrado</th><th>Pago</th><th>Diferença</th></tr></thead><tbody>
      ${results.map(item => `<tr><td><strong>${escapeHtml(item.name)}</strong><small>${escapeHtml(item.sku)}</small></td><td>${escapeHtml(item.pickedUp)}</td><td>${escapeHtml(item.returned)}</td><td>${escapeHtml(item.expected)}</td><td>${escapeHtml(item.registered)}</td><td>${escapeHtml(item.paid)}</td><td><strong>${escapeHtml(item.difference)}</strong></td></tr>`).join("")}
    </tbody></table></div>
    <div class="reconciliation-summary"><div><span class="eyebrow">RESULTADO</span><strong>${needsReview ? "Evento para revisão humana" : "Sessão sem divergência por SKU"}</strong></div><span class="status-badge ${needsReview ? "status-badge--warning" : "status-badge--ok"}">${needsReview ? "Revisar" : "Regular"}</span></div>`;
}

async function persistEvent(payload) {
  if (!canWrite("events")) return null;
  return pushData("events", payload);
}

async function persistOccurrences(results, scenarioTitle, sessionId) {
  if (!canWrite("events")) return;
  const session = getCurrentSession();
  for (const item of results.filter(result => result.review)) {
    const occurrence = {
      storeId: "loja-01",
      sessionId,
      personId: "PERSON-01",
      productId: item.sku,
      productName: item.name,
      sku: item.sku,
      pickedUp: item.pickedUp,
      returned: item.returned,
      expected: item.expected,
      registered: item.registered,
      paid: item.paid,
      difference: item.difference,
      confidence: item.insufficient ? .32 : .92,
      cameras: ["CAM-04", "CAM-07"],
      reason: item.reason,
      status: "pending",
      reviewRequired: true,
      classification: "",
      simulation: true,
      scenario: scenarioTitle,
      createdAt: now(),
      createdBy: session.uid
    };
    const occurrenceId = await pushData("occurrences", occurrence);
    await pushData("events", { ...base, type: "OCCURRENCE_CREATED", sessionId, productName: item.name, sku: item.sku, occurrenceId, confidence: occurrence.confidence, createdAt: now() });
  }
}

async function runScenario() {
  if (running) return;
  const key = document.getElementById("scenarioSelect").value;
  const scenario = scenarios[key];
  if (!scenario) return;
  running = true;
  resetScene();
  document.getElementById("runScenarioButton").disabled = true;
  const sessionId = `${key === "threePeople" ? "SESSION-GROUP" : "SESSION"}-${Date.now().toString().slice(-7)}`;
  setStatus("Executando", scenario.title, true);
  try {
    await setData(`sessions/${sessionId}`, {
      storeId: "loja-01",
      status: "SIMULATION_RUNNING",
      scenario: key,
      simulation: true,
      startedAt: now(),
      createdBy: getCurrentSession()?.uid || "local"
    }).catch(() => null);

    for (const item of scenario.steps) {
      if (item.movement) move(item.movement);
      const payload = { ...base, ...item.overrides, sessionId: item.overrides.sessionId || sessionId, type: item.type, createdAt: now() };
      generatedEvents.push(payload);
      addTimeline(payload);
      await persistEvent(payload);
      setStatus(eventLabel(item.type), `${payload.personId || "Sistema"} · ${payload.cameraId || "sem câmera"}`, true);
      await pause(item.delay);
    }

    setStatus("Reconciliando", "Comparando retiradas, devoluções, registro e pagamento por SKU.", true);
    await pause(700);
    const results = reconcile(scenario.items);
    renderResult(results);
    await persistOccurrences(results, scenario.title, sessionId);
    await setData(`sessions/${sessionId}/status`, results.some(item => item.review) ? "REVIEW_REQUIRED" : "RECONCILED").catch(() => null);
    await setData(`sessions/${sessionId}/endedAt`, now()).catch(() => null);
    await appendAudit("SIMULATION_COMPLETED", { scenario: key, sessionId, eventCount: generatedEvents.length }, getCurrentSession()).catch(() => null);
    setStatus("Concluído", results.some(item => item.review) ? "Possível divergência encaminhada para revisão humana." : "Sessão reconciliada sem divergência.", false);
    toast("Simulação concluída.", "success");
  } catch (error) {
    setStatus("Falha", error.message, false);
    toast(`Falha na simulação: ${error.message}`, "error");
  } finally {
    running = false;
    document.getElementById("runScenarioButton").disabled = false;
  }
}

export function initializeSimulator() {
  document.getElementById("runScenarioButton").addEventListener("click", runScenario);
  document.getElementById("resetScenarioButton").addEventListener("click", () => {
    if (!running) resetScene();
  });
  resetScene();
}
