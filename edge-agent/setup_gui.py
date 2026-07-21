"""Assistente gráfico do conector SMART24 para Windows."""
from __future__ import annotations

import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tkinter as tk
from tkinter import filedialog, messagebox, ttk

from dotenv import set_key

from discovery import discover_camera, scan_subnet, mask_url

BASE = Path(__file__).resolve().parent
ENV_PATH = BASE / ".env"
PRIVATE_DIR = BASE / "private"
SERVICE_ACCOUNT = PRIVATE_DIR / "service-account.json"
CONFIG_PATH = BASE / "connector-config.json"

DATABASE_URL = "https://smart24-fusion-default-rtdb.firebaseio.com/"


class SetupApp(tk.Tk):
    def __init__(self) -> None:
        super().__init__()
        self.title("SMART24 — Configurar câmera real")
        self.geometry("720x720")
        self.minsize(620, 620)
        self.stream_url = ""
        self.service_source = ""
        self._build()

    def _build(self) -> None:
        root = ttk.Frame(self, padding=18)
        root.pack(fill="both", expand=True)
        ttk.Label(root, text="Conector local SMART24", font=("Segoe UI", 18, "bold")).pack(anchor="w")
        ttk.Label(root, text="A senha fica somente neste computador. Ela não vai para o GitHub nem para o Firebase.", wraplength=650).pack(anchor="w", pady=(3, 16))

        form = ttk.Frame(root)
        form.pack(fill="x")
        self.vars = {
            "camera_id": tk.StringVar(value="CAM-01"),
            "store_id": tk.StringVar(value="loja-01"),
            "bridge_id": tk.StringVar(value="bridge-loja-01"),
            "host": tk.StringVar(),
            "port": tk.StringVar(value="5000"),
            "username": tk.StringVar(value="administrator"),
            "password": tk.StringVar(),
        }
        fields = [
            ("ID público da câmera", "camera_id"),
            ("Loja", "store_id"),
            ("Conector", "bridge_id"),
            ("IP local da câmera", "host"),
            ("Porta NVR", "port"),
            ("Usuário", "username"),
            ("Senha criada em Yoosee → Conexão NVR", "password"),
        ]
        for row, (label, key) in enumerate(fields):
            ttk.Label(form, text=label).grid(row=row, column=0, sticky="w", pady=5)
            entry = ttk.Entry(form, textvariable=self.vars[key], show="•" if key == "password" else "")
            entry.grid(row=row, column=1, sticky="ew", padx=(12, 0), pady=5)
        form.columnconfigure(1, weight=1)

        actions = ttk.Frame(root)
        actions.pack(fill="x", pady=14)
        ttk.Button(actions, text="Procurar câmeras na rede", command=self.scan).pack(side="left")
        ttk.Button(actions, text="Testar conexão de vídeo", command=self.test).pack(side="left", padx=8)
        ttk.Button(actions, text="Selecionar conta de serviço Firebase", command=self.choose_service).pack(side="left")

        self.service_label = ttk.Label(root, text="Conta de serviço ainda não selecionada.", wraplength=650)
        self.service_label.pack(anchor="w", pady=(0, 12))

        ttk.Label(root, text="Resultado técnico").pack(anchor="w")
        self.log = tk.Text(root, height=12, wrap="word", state="disabled")
        self.log.pack(fill="both", expand=True, pady=(5, 12))

        footer = ttk.Frame(root)
        footer.pack(fill="x")
        ttk.Button(footer, text="Salvar e ativar conector", command=self.save).pack(side="right")
        ttk.Button(footer, text="Fechar", command=self.destroy).pack(side="right", padx=8)

    def write(self, text: str) -> None:
        self.log.configure(state="normal")
        self.log.insert("end", text + "\n")
        self.log.see("end")
        self.log.configure(state="disabled")
        self.update_idletasks()

    def scan(self) -> None:
        self.write("Procurando dispositivos com porta 5000 na rede local...")
        self.update()
        hosts = scan_subnet(5000)
        if not hosts:
            self.write("Nenhum dispositivo respondeu na porta 5000.")
            messagebox.showinfo("Busca concluída", "Nenhuma câmera foi encontrada. Ative Conexão NVR no Yoosee e confirme que o PC está no mesmo roteador.")
            return
        self.write("Encontrados: " + ", ".join(hosts))
        self.vars["host"].set(hosts[0])
        if len(hosts) > 1:
            messagebox.showinfo("Câmeras encontradas", "Foram encontrados:\n" + "\n".join(hosts) + "\n\nO primeiro IP foi preenchido. Teste cada um se necessário.")

    def test(self) -> None:
        if not self.vars["password"].get():
            messagebox.showwarning("Senha necessária", "Crie e informe a senha em Yoosee → Configurações → Conexão NVR.")
            return
        try:
            port = int(self.vars["port"].get())
        except ValueError:
            messagebox.showerror("Porta inválida", "Informe uma porta numérica.")
            return
        self.write(f"Testando {self.vars['host'].get()}:{port}...")
        self.update()
        result = discover_camera(self.vars["host"].get(), self.vars["password"].get(), port, self.vars["username"].get())
        if result.ok:
            self.stream_url = result.stream_url
            self.write(f"SUCESSO: vídeo confirmado por {result.method}: {mask_url(result.stream_url)} — {result.message}")
            messagebox.showinfo("Câmera conectada", "O conector recebeu um frame real da câmera.")
        else:
            self.stream_url = ""
            self.write("FALHA: " + result.message)
            messagebox.showerror("Vídeo não confirmado", result.message)

    def choose_service(self) -> None:
        path = filedialog.askopenfilename(title="Selecione service-account.json", filetypes=[("JSON", "*.json")])
        if not path:
            return
        try:
            data = json.loads(Path(path).read_text(encoding="utf-8"))
            if data.get("type") != "service_account" or not data.get("project_id"):
                raise ValueError("Arquivo não é uma conta de serviço Firebase.")
            if data.get("project_id") != "smart24-fusion":
                if not messagebox.askyesno("Projeto diferente", f"O arquivo pertence a {data.get('project_id')}, não a smart24-fusion. Continuar mesmo assim?"):
                    return
            self.service_source = path
            self.service_label.config(text=f"Conta selecionada: {Path(path).name} — projeto {data.get('project_id')}")
            self.write("Conta de serviço validada localmente.")
        except Exception as exc:
            messagebox.showerror("Arquivo inválido", str(exc))

    def save(self) -> None:
        if not self.stream_url:
            messagebox.showwarning("Teste obrigatório", "Clique em Testar conexão de vídeo e confirme que um frame real foi recebido.")
            return
        if not self.service_source and not SERVICE_ACCOUNT.exists():
            messagebox.showwarning("Conta de serviço", "Selecione a conta de serviço do projeto Firebase.")
            return
        PRIVATE_DIR.mkdir(exist_ok=True)
        if self.service_source:
            shutil.copy2(self.service_source, SERVICE_ACCOUNT)
        ENV_PATH.touch(exist_ok=True)
        values = {
            "CAMERA_RTSP_URL": self.stream_url,
            "CAMERA_ID": self.vars["camera_id"].get().strip().upper(),
            "CAMERA_RECORD_ID": "",
            "STORE_ID": self.vars["store_id"].get().strip(),
            "BRIDGE_ID": self.vars["bridge_id"].get().strip(),
            "DRY_RUN": "false",
            "FIREBASE_DATABASE_URL": DATABASE_URL,
            "GOOGLE_APPLICATION_CREDENTIALS": str(SERVICE_ACCOUNT),
            "HEARTBEAT_SECONDS": "15",
            "RECONNECT_SECONDS": "5",
            "SAVE_DEBUG_FRAME": "true",
            "DEBUG_FRAME_PATH": "debug/latest.jpg",
            "LOG_LEVEL": "INFO",
        }
        for key, value in values.items():
            set_key(str(ENV_PATH), key, value)
        CONFIG_PATH.write_text(json.dumps({k: v for k, v in values.items() if k != "CAMERA_RTSP_URL"}, ensure_ascii=False, indent=2), encoding="utf-8")
        self.write("Configuração privada salva. Ativando início automático...")
        try:
            subprocess.run([sys.executable, str(BASE / "install_startup.py")], check=True)
            messagebox.showinfo("Conector configurado", "O conector foi configurado e será iniciado agora. O painel deverá mostrar ONLINE após o primeiro heartbeat.")
            subprocess.Popen([str(BASE / "INICIAR-CONNECTOR-SMART24.bat")], cwd=BASE, shell=True)
        except Exception as exc:
            messagebox.showwarning("Configuração salva", f"A câmera foi configurada, mas o início automático falhou: {exc}\nExecute INICIAR-CONNECTOR-SMART24.bat manualmente.")


if __name__ == "__main__":
    SetupApp().mainloop()
