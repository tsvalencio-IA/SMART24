@echo off
chcp 65001 >nul
title SMART24 - Dados da rede (somente consulta)
echo SMART24 - DIAGNOSTICO LOCAL
echo Este comando nao instala programas nem altera a rede.
echo.
echo === IP DO COMPUTADOR, MASCARA E GATEWAY ===
ipconfig
echo.
echo === VIZINHOS JA CONHECIDOS NA REDE ===
arp -a
echo.
echo O ARP nao e uma lista completa de cameras; entradas podem estar antigas.
echo A camera Sala informada tem MAC 38-7a-cc-3a-4d-8e.
echo Procure esse MAC para obter um candidato a IP, depois confirme no video.
echo Os IPs locais nao habilitam acesso por 4G sozinhos.
echo Envie somente a parte do adaptador conectado e a linha do MAC da camera.
echo Nao envie senhas, QR codes ou chaves de acesso.
echo.
pause
