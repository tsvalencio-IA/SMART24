# Relatório de alterações — SMART24 V5 Live IA Pilot

## Base preservada

A V5 foi construída sobre `SMART24-PILOTO-REAL-V4.2-MESMO-CELULAR`, preservando Firebase, produtos, etiquetas, reposição, zonas, eventos, carrinhos, ocorrências e simulador 3D.

## Arquivos novos

- `assets/js/live-monitor.js`
- `android-vision-pilot/.../PersonTracker.kt`
- `android-vision-pilot/.../FrameAnnotator.kt`
- `docs/game-audit/AUDITORIA-GAME-PARA-SMART24.md`
- `LEIA-PRIMEIRO-V5.md`
- `PASSO-A-PASSO-V5.md`

## Arquivos alterados

- `index.html`: módulo Ao vivo e navegação.
- `assets/js/app.js`: inicialização e assinatura de `cameraLive`.
- `assets/css/app.css`: mosaico e imagem ampliada.
- `firebase/database.rules.json`: leitura e escrita protegida de `cameraLive`.
- `VisionEngine.kt`: pose corporal principal, fusão com face/objeto e IDs suavizados.
- `CaptureService.kt`: anotação, redução, publicação de quadro e estado.
- `Models.kt`: trilha e pontos corporais.
- `app/build.gradle.kts`: dependência de pose e versão do APK.

## Verdade técnica

Esta versão não transforma o ID Yoosee em uma API de vídeo. Ela publica no SMART24 os quadros que o Android realmente recebe por captura autorizada do vídeo aberto no Yoosee. O GAME-main foi usado para orientar pose, coordenadas e trajetória; não foi tratado como detector multipessoa nem como integração de câmera IP.
