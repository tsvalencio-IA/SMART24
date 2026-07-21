# CONECTOR LOCAL SMART24 — WINDOWS

Este é o componente que coloca a câmera **verdadeiramente online** no painel.
O QR e o e-mail Yoosee apenas cadastram metadados; não transportam vídeo.

## Antes de executar

No aplicativo Yoosee da câmera:

1. abra os três pontos da câmera;
2. entre em **Configurações**;
3. entre em **Conexão NVR**;
4. crie uma senha RTSP/NVR;
5. mantenha o computador e a câmera no mesmo roteador.

A orientação oficial da Yoosee informa usuário `administrator` e porta `5000` para a conexão NVR. O assistente tenta obter o fluxo pelo ONVIF; quando o firmware não fornece o URI, realiza sondas locais de compatibilidade sem publicar senha ou URL.

## Instalação sem terminal

Dê dois cliques em:

`INSTALAR-CONNECTOR-SMART24.bat`

O assistente irá:

- procurar a câmera na rede;
- testar o vídeo real;
- pedir o arquivo `service-account.json` do Firebase;
- salvar as credenciais somente neste computador;
- criar início automático do Windows;
- publicar heartbeat no Firebase.

Quando funcionar, o painel passará de **0 online** para **1 online** e de **0 conectores** para **1 heartbeat ativo**.

## Segurança

Nunca envie ao GitHub:

- `.env`;
- pasta `private/`;
- `service-account.json`;
- senha NVR/RTSP;
- URL RTSP completa.

O vídeo contínuo não é enviado ao Firebase. A versão atual publica heartbeat e eventos de disponibilidade. A análise de retirada/devolução é uma etapa posterior e exige calibração física das câmeras e das zonas.
