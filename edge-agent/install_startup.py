from pathlib import Path
import os

base = Path(__file__).resolve().parent
startup = Path(os.environ["APPDATA"]) / "Microsoft" / "Windows" / "Start Menu" / "Programs" / "Startup"
cmd = startup / "SMART24-Connector.cmd"
launcher = base / "INICIAR-CONNECTOR-SMART24.bat"
cmd.write_text(f'@echo off\r\ncd /d "{base}"\r\ncall "{launcher}"\r\n', encoding="utf-8")
print(f"Início automático criado: {cmd}")
