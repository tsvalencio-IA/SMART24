# Auditoria do GAME-main para uso no SMART24

## O que o jogo realmente possui

O jogo usa a câmera do navegador com `navigator.mediaDevices.getUserMedia`, processa os quadros em um loop com `requestAnimationFrame` e carrega TensorFlow.js com o modelo MoveNet `SINGLEPOSE_LIGHTNING`.

A pose detectada é convertida em coordenadas de tela e usada pelos jogos para interpretar punhos, nariz, cotovelos, direção e velocidade dos movimentos. O módulo AR também usa COCO-SSD para objetos genéricos e desenha a câmera no canvas.

## O que foi reaproveitado na V5

- leitura contínua de quadros;
- transformação de posição da câmera em coordenadas normalizadas;
- suavização de movimento;
- manutenção de uma trilha recente;
- representação visual da pessoa sobre a imagem;
- uso de pose corporal para a pessoa principal;
- separação entre captura, análise, lógica e desenho.

No Android, o detector corporal foi implementado com ML Kit Pose Detection porque o quadro chega como `Bitmap` capturado do vídeo Yoosee. O comportamento é equivalente à parte MoveNet do jogo, mas integrado ao pipeline Android existente.

## Limites comprovados do jogo

- O MoveNet configurado no jogo é `SINGLEPOSE_LIGHTNING`: acompanha uma pessoa principal por vez.
- Não há reconhecimento facial de identidade.
- Não há persistência confiável de identidade de várias pessoas.
- Não há leitura de QR SMART24.
- Não há ligação com câmera Yoosee, RTSP ou Cloudlink.
- Não há motor de carrinho ou associação produto/pessoa.

A V5 combina a ideia do jogo com rastreamento de faces/objetos, etiquetas, zonas e carrinhos existentes no SMART24. Multiusuário robusto ainda exigirá um detector multipessoa apropriado e teste com as câmeras reais posicionadas na loja.
