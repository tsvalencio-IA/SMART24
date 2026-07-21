# Configuração do Firebase — SMART24 Fusion

Este projeto usa **Firebase Authentication com e-mail/senha** e **Firebase Realtime Database**. Ele não usa o Firebase para transmitir vídeo contínuo.

## Arquivos envolvidos

- `firebase-config.js`: recebe a configuração do aplicativo Web.
- `firebase/database.rules.json`: regras de acesso bloqueadas por padrão.
- `firebase/database.seed.example.json`: exemplo da estrutura inicial.

## Segurança obrigatória

Nunca coloque no GitHub:

- senha de câmera;
- URL RTSP com credenciais;
- token;
- chave privada;
- `service-account.json`;
- `.env`.

A configuração pública do aplicativo Web do Firebase é diferente de uma conta de serviço. Mesmo assim, as regras do banco e a autenticação precisam estar corretas.

## Funções permitidas

- `admin`: administra, cadastra, configura e revisa.
- `operator`: cadastra produtos, gera etiquetas e trabalha com eventos operacionais.
- `auditor`: visualiza e revisa ocorrências, sem alterar configuração crítica.

## Como cadastrar a primeira função

1. Crie o usuário no Firebase Authentication.
2. Copie o UID desse usuário.
3. No Realtime Database, crie o caminho `roles`.
4. Dentro de `roles`, crie uma chave com o UID exato.
5. Defina o valor como `admin`.

Exemplo:

```json
{
  "roles": {
    "UID_REAL_DO_USUARIO": "admin"
  }
}
```

## Publicação das regras

No console do Firebase:

1. Abra **Realtime Database**.
2. Entre em **Regras**.
3. Apague o conteúdo de teste.
4. Cole o conteúdo completo de `firebase/database.rules.json`.
5. Clique em **Publicar**.

As regras não permitem leitura ou escrita pública. O agente local futuro utilizará uma conta de serviço fora do GitHub e, por usar o Firebase Admin SDK, não depende das regras do cliente.
