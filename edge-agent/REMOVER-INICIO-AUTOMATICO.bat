@echo off
set FILE=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\SMART24-Connector.cmd
if exist "%FILE%" del "%FILE%"
echo Inicio automatico do SMART24 removido.
pause
