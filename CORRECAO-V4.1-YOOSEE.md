# SMART24 V4.1 — CORREÇÃO DE ABERTURA DO YOOSEE

## Defeito encontrado

O Android 11 ou superior pode esconder aplicativos instalados de outros aplicativos.
A V4 procurava o pacote `com.yoosee`, mas não o declarava em `<queries>` no manifesto.
Por isso o SMART24 poderia informar que o Yoosee não estava instalado ou simplesmente não abri-lo.

## Correção aplicada

- pacote `com.yoosee` declarado em `<queries>`;
- tentativa principal de abertura pelo pacote oficial;
- tentativa alternativa pelo launcher do Android;
- mensagem clara quando o usuário precisa abrir o Yoosee manualmente;
- a captura permanece ativa em segundo plano mesmo no fallback manual.

## Limite verdadeiro

Esta correção permite que o SMART24 abra o Yoosee e analise a tela autorizada.
Ela não transforma o QR, o e-mail ou o ID da câmera em uma API direta da Yoosee.
O vídeo precisa estar aberto no aplicativo Yoosee no mesmo Android durante este piloto.

## Estados corrigidos

- `WAITING_VIDEO`: a captura de tela foi autorizada, mas o vídeo ainda não foi confirmado;
- `NO_IMAGE`: o Android entregou tela preta ou sem imagem;
- `VIDEO_VISIBLE`: existe imagem visível sendo processada, mas isso ainda não prova uma API direta com a câmera;
- `STOPPED`: captura encerrada.

A V4 anterior publicava `ONLINE` assim que a autorização de captura era concedida. Isso foi corrigido para não confundir autorização de tela com câmera realmente conectada.
