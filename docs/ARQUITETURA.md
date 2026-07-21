# Arquitetura — SMART24 Fusion

## Visão geral

```text
Acesso facial autorizado
        ↓
Sessão de compra
        ↓
Câmeras IP → computador local → conector/visão computacional → eventos
        ↓                                              ↓
Checkout autorizado ─────────────────────────────→ Firebase Realtime Database
                                                        ↓
                                                  Painel SMART24
                                                        ↓
                                              Revisão humana privada
```

## Frontend

O frontend é estático e pode ser publicado no GitHub Pages. Ele usa módulos ES e Firebase via CDN, sem processo de build.

Módulos principais:

- autenticação;
- banco de dados;
- produtos;
- etiquetas;
- câmeras;
- eventos;
- ocorrências;
- simulador.

## Firebase

O Firebase é usado para autenticação, perfis, cadastros, eventos, sessões, ocorrências e atualização em tempo real.

O Firebase **não** é usado para transmitir vídeo contínuo. O vídeo deve ser processado preferencialmente no local.

## Agente local

A pasta `edge-agent` contém uma base para:

- abrir RTSP lido do `.env`;
- testar disponibilidade;
- atualizar heartbeat;
- publicar estados `ONLINE`, `OFFLINE`, `RECONNECTING` e `STOPPED`;
- gerar imagem local de depuração, se autorizado;
- reconectar automaticamente.

O agente ainda não possui detecção de pessoa, mãos, produto ou retirada real.

## Integração de checkout

A integração definitiva deve usar API, evento, banco autorizado ou mecanismo oficial do fornecedor. Câmera apontada para a tela não é considerada integração definitiva.

## Evolução RFID

RAIN RFID/UHF passivo é uma evolução recomendada, sujeita a testes com líquidos, metais, corpo humano, empilhamento, interferência, posição e custo.


## Cadastro por QR Code

```text
Câmera do celular ou imagem local
        ↓
jsQR executado no navegador
        ↓
Sanitização do conteúdo
        ↓
Fabricante + ID do dispositivo
        ↓
Confirmação humana
        ↓
Firebase /cameras
```

Para links Yoosee, `InviteCode`, token, nome do compartilhador, URL completa e QR bruto são descartados antes do cadastro. O leitor não aceita o convite e não abre o vídeo.

## Conta operacional Yoosee

O sistema armazena somente o endereço de e-mail e o estado da implantação em `/integrations/yoosee`. A senha permanece exclusivamente sob controle do responsável e nunca é salva no Firebase, GitHub ou frontend. A câmera deve ser compartilhada com essa conta pelo aplicativo Yoosee.

## Loja 3D

O arquivo `simulator-3d.html` é incorporado ao painel por `iframe` no módulo Simulador. Ele permanece isolado do Firebase e claramente identificado como demonstração. A simulação operacional anterior continua separada para publicar sessões, eventos e ocorrências.
