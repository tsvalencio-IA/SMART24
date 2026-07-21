@echo off
chcp 65001 >nul
cd /d "%~dp0"
if not exist ".venv\Scripts\python.exe" (
  echo O conector ainda nao foi instalado.
  call INSTALAR-CONNECTOR-SMART24.bat
  exit /b %errorlevel%
)
if not exist ".env" (
  echo A configuracao ainda nao foi concluida.
  ".venv\Scripts\python.exe" setup_gui.py
  exit /b %errorlevel%
)
title SMART24 - Conector de camera
".venv\Scripts\python.exe" app.py
