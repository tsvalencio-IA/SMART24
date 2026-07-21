@echo off
chcp 65001 >nul
cd /d "%~dp0"
title SMART24 - Instalação do conector

echo =====================================================
echo SMART24 - INSTALADOR DO CONECTOR DE CAMERA
echo =====================================================
echo.
where py >nul 2>nul
if errorlevel 1 (
  where python >nul 2>nul
  if errorlevel 1 (
    echo Python nao foi encontrado neste computador.
    echo Instale o Python 3.11 ou superior e marque "Add Python to PATH".
    start https://www.python.org/downloads/windows/
    pause
    exit /b 1
  )
  set PY=python
) else (
  set PY=py
)

if not exist ".venv\Scripts\python.exe" (
  echo Criando ambiente privado...
  %PY% -m venv .venv
  if errorlevel 1 goto :erro
)

echo Instalando componentes necessarios...
".venv\Scripts\python.exe" -m pip install --upgrade pip
".venv\Scripts\python.exe" -m pip install -r requirements.txt
if errorlevel 1 goto :erro

echo Abrindo assistente grafico...
".venv\Scripts\python.exe" setup_gui.py
exit /b 0

:erro
echo.
echo A instalacao falhou. Verifique a internet e tente novamente.
pause
exit /b 1
