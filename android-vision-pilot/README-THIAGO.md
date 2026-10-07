# SMART24 — celular da loja + câmera no mesmo Wi‑Fi

Esta é a arquitetura operacional adotada para os mercadinhos:

```text
CÂMERA LOCAL ──RTSP/ONVIF──> CELULAR SMART24 DA LOJA
                                  │
                                  │ Internet / Firebase
                                  ▼
                           CENTRAL SMART24
```

A câmera não precisa ser aberta remotamente pela Central. O celular que fica fisicamente no mercadinho acessa a câmera pela própria rede Wi‑Fi da loja. A Central conversa com esse celular pelo Firebase.

## O que o celular da loja faz

1. fica conectado no mesmo Wi‑Fi da câmera;
2. conecta diretamente ao RTSP/ONVIF local da câmera;
3. mantém o vídeo aberto no SMART24;
4. executa a análise visual no próprio Android;
5. mantém heartbeat no Firebase a cada poucos segundos;
6. publica quadros reduzidos para o mosaico da Central;
7. envia eventos detectados;
8. recebe comandos da Central, como iniciar/parar o vigilante, reconectar o vídeo e atualizar o quadro.

A senha RTSP/NVR e a URL completa da câmera não são publicadas no Firebase. Durante a sessão, os dados de conexão permanecem no celular da loja.

## O que a Central SMART24 faz

Na tela **Ao vivo**, a Central mostra as lojas/câmeras que estão publicando quadros e o estado do celular local. Para cada celular identificado, a Central pode enviar:

- **Iniciar vigilante**
- **Parar vigilante**
- **Reconectar câmera**
- **Atualizar quadro**

O comando é gravado no registro do próprio nó `visionPilots/{pilotId}`. O celular consulta esse registro, executa localmente e devolve o resultado.

## Primeiro teste — oficina / câmera Sala

Dados já conhecidos do teste:

- câmera: **SALA**
- IP local: **192.168.15.5**
- porta RTSP: **554**
- ID do dispositivo: **5646988473**
- MAC: **38:7A:CC:3A:4D:8E**

Ordem:

1. deixe o celular no mesmo Wi‑Fi da câmera;
2. abra o SMART24;
3. confirme o IP `192.168.15.5` e porta `554`;
4. informe a senha NVR/RTSP somente no celular;
5. toque em **Conectar câmera**;
6. aguarde **VÍDEO LOCAL CONFIRMADO**;
7. entre no Firebase dentro do APK;
8. deixe o SMART24 aberto;
9. abra a Central SMART24 em outro aparelho;
10. entre em **Ao vivo** e confirme o quadro da câmera;
11. use os botões da Central para testar o comando do celular da loja.

## Escala para os mercadinhos

Cada loja terá seu próprio identificador e seu próprio celular Android. O Firebase separa os dados por `storeId`, `cameraId` e `pilotId`.

A primeira etapa valida uma câmera ativa por celular. Depois de estabilizar a câmera Sala, o mesmo nó local será evoluído para cadastrar e alternar/monitorar várias câmeras da mesma loja sem mudar a arquitetura da Central.

## Regra operacional

O celular da loja deve permanecer ligado, com alimentação contínua, Wi‑Fi estável e o SMART24 aberto. Se a internet externa cair, a câmera local continua sendo acessível pelo Wi‑Fi; eventos que não puderem sincronizar permanecem na fila local e são reenviados quando a conexão voltar.

Powered by **thIAguinho Soluções Digitais**.
