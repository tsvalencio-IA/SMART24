# SMART24 Vigilante Celular v3

Esta versão não depende do aplicativo Yoosee para operar.

## Fluxo

Celular Android -> RTSP direto da câmera -> LibVLC -> TextureView -> VisionEngine -> pessoa/pulso -> zona/SKU -> evento.

## Primeira câmera

O IP inicial da tela é `192.168.15.5`, baseado no teste atual.

O usuário e a senha RTSP/NVR não são presumidos. Digite os valores configurados na câmera.

O aplicativo testa automaticamente portas e caminhos comuns:

- porta informada;
- 554;
- 5000;
- 8554;
- `/onvif1`;
- `/onvif2`;
- `/live/ch00_0`;
- `/live/ch00_1`;
- Hikvision Channels;
- Dahua/Intelbras realmonitor.

Também aceita uma URL RTSP completa.

## Uso

1. Celular na mesma rede da câmera.
2. Abra SMART24 Vigilante.
3. Informe usuário/senha RTSP/NVR.
4. Toque em CONECTAR DIRETO NA CÂMERA.
5. Quando aparecer CÂMERA CONECTADA DIRETO, toque em CAPTURAR QUADRO E CALIBRAR PRODUTOS.
6. Marque um retângulo por SKU.
7. Volte e toque em INICIAR VIGILANTE.

## Reconhecimento de item

O protótipo identifica o item pela posição física cadastrada:

pessoa + pulso + zona/SKU + mudança visual da prateleira.

Ele gera:

- `ITEM_PICKED_PROBABLE`;
- `ITEM_RETURNED_PROBABLE`;
- `SHELF_INTERACTION`.

Não acusa furto automaticamente.

## Limite atual

O `VisionEngine` existente usa ML Kit Pose para uma pose principal. Portanto esta versão móvel é adequada para provar a câmera + vigilante com uma pessoa principal por vez.

Para produção com várias pessoas simultâneas, o próximo motor deverá ser multipose.

## Firebase

Firebase é opcional para o teste local.

Se fizer login dentro do aplicativo com usuário `admin` ou `operator`, os eventos são publicados no Realtime Database.

## Segurança

- a senha da câmera não é salva;
- a senha Firebase não é salva;
- nenhuma senha deve ser enviada ao GitHub;
- o app mostra a URL RTSP mascarando credenciais.

## Integração e validação

Consulte [CONTINUIDADE-VIGILANTE.md](CONTINUIDADE-VIGILANTE.md) para correções, comportamento da fila local, testes e limites da versão integrada.
