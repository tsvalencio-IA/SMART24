# Passo a passo da V5

1. Suba todo o conteúdo desta pasta para a raiz do repositório SMART24.
2. Publique novamente `firebase/database.rules.json` no Realtime Database.
3. No GitHub, abra Actions e execute `Build SMART24 Vision APK`.
4. Desinstale a versão antiga do Vision Pilot e instale o novo `app-debug.apk`.
5. Entre no APK com o usuário do Firebase que tenha função `admin` ou `operator`.
6. Autorize a captura da tela inteira.
7. Abra a câmera no Yoosee e deixe o vídeo ao vivo visível.
8. Em outro celular ou computador, abra o SMART24 e toque em **Ao vivo**.
9. O quadro deve aparecer em até alguns segundos com o estado `VIDEO_VISIBLE`.
10. Mostre uma etiqueta SMART24 impressa para a câmera. O contador de etiquetas deve mudar e o serial aparecer sobre a imagem.
11. Calibre uma zona de prateleira e teste retirada/devolução.

Estados:
- `WAITING_VIDEO`: captura autorizada, mas ainda sem vídeo confirmado.
- `VIDEO_VISIBLE`: quadro válido analisado e publicado.
- `NO_IMAGE`: tela preta ou vídeo bloqueado.
- `DEGRADED`: falha de análise ou publicação.
- `STOPPED`: análise encerrada.
