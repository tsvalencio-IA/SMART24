# SMART24 Vision Pilot V4.2 — fluxo no mesmo celular

## Problema corrigido

O mesmo celular não consegue apontar a câmera para um QR exibido em sua própria tela. O QR também não deve ser obrigatório quando o Yoosee já está conectado à conta que possui a câmera.

## Novas opções

1. **Usar conta Yoosee que já tem a câmera** — abre diretamente o aplicativo; basta tocar na câmera e abrir o vídeo ao vivo.
2. **Colar link copiado** — recebe um link de compartilhamento Yoosee pela área de transferência.
3. **Escolher print ou imagem do QR** — lê o QR diretamente da galeria usando ML Kit.
4. **Abrir convite no Yoosee** — tenta o deep link `yoosee://share`, depois o link HTTPS e, por último, o navegador.

O link, QR completo e InviteCode não são gravados no Firebase nem nas preferências do Android. O campo é apagado após a tentativa de abertura.

## Fluxo recomendado para o piloto

Se a câmera já aparece no Yoosee deste celular, não use QR: abra a conta existente, confirme o vídeo, volte ao SMART24, entre no Firebase, autorize a análise e abra novamente o vídeo ao vivo.

Se a câmera está em outra conta, gere um link novo, copie-o ou salve um print do QR, entre no Yoosee com a conta que receberá o compartilhamento e use uma das opções de importação do SMART24.
