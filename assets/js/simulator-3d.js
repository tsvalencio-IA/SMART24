function loadFrame() {
  const frame = document.getElementById("store3dFrame");
  if (frame && !frame.getAttribute("src")) frame.setAttribute("src", frame.dataset.src);
}

function activate(mode) {
  const threeD = document.getElementById("simulator3dPanel");
  const operational = document.getElementById("operationalSimulatorPanel");
  document.querySelectorAll("[data-simulator-mode]").forEach(button => {
    button.classList.toggle("is-active", button.dataset.simulatorMode === mode);
  });
  threeD?.classList.toggle("is-hidden", mode !== "3d");
  operational?.classList.toggle("is-hidden", mode !== "operational");
  if (mode === "3d") loadFrame();
}

export function initializeSimulator3D() {
  document.querySelectorAll("[data-simulator-mode]").forEach(button => {
    button.addEventListener("click", () => activate(button.dataset.simulatorMode));
  });
  document.getElementById("openStore3dWindow")?.addEventListener("click", () => {
    window.open("./simulator-3d.html", "_blank", "noopener,noreferrer");
  });
  activate("3d");
}
