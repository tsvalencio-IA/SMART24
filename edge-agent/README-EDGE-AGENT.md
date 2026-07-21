# Agente local — Fase 2

Este agente é uma base técnica. Ele **não conecta uma câmera real sem os dados corretos** e não faz detecção de retirada, devolução, rosto ou produto.

## O que ele faz nesta fase

- lê a URL RTSP privada do `.env`;
- mascara usuário e senha nos logs;
- tenta receber um frame;
- publica heartbeat;
- informa `ONLINE`, `OFFLINE`, `RECONNECTING` ou `STOPPED`;
- reconecta automaticamente;
- opcionalmente salva uma imagem de depuração somente no computador local;
- não envia vídeo contínuo ao Firebase.

## O que precisa ser confirmado antes

- marca e modelo da câmera;
- IP local;
- caminho RTSP correto ou suporte ONVIF;
- usuário e senha autorizados;
- computador que ficará ligado na loja;
- acesso Firebase por conta de serviço;
- política de retenção e privacidade.

## Arquivos privados

Nunca envie ao GitHub:

- `.env`;
- `service-account.json`.

O `.gitignore` do projeto já bloqueia esses arquivos, mas a conferência humana continua obrigatória.

## Instalação

Esta parte exige Python no computador local. Como Thiago não usa terminal, a execução deve ser feita com apoio técnico na Fase 2.

Com Python instalado, o técnico deverá:

```bash
python -m venv .venv
```

No Windows:

```bash
.venv\Scripts\activate
pip install -r requirements.txt
```

Depois:

1. copiar `.env.example` para `.env`;
2. preencher a URL RTSP real no `.env`;
3. manter `DRY_RUN=true` no primeiro teste;
4. executar:

```bash
python app.py
```

## Testes unitários

```bash
python -m unittest discover -s tests -v
```

## Ativação do Firebase Admin

Somente depois do teste local:

1. criar uma conta de serviço exclusiva e com acesso mínimo adequado;
2. baixar o JSON diretamente no computador da loja;
3. renomear localmente para `service-account.json`;
4. confirmar que ele não aparece no GitHub;
5. preencher `FIREBASE_DATABASE_URL`;
6. mudar `DRY_RUN=false`.

Não envie a conta de serviço por chat, mensagem ou repositório.
