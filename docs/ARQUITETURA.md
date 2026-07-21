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
