# VALIDAÇÃO FINAL — CHAVES E VALORES REAIS

## Base usada

- ZIP real anexado: `SMART24-main.zip`.
- Atualização funcional: leitor de QR, metadados Yoosee e loja 3D.
- O ZIP real foi tratado como fonte dos valores já publicados.

## Valores preservados

O arquivo operacional `firebase-config.js` foi copiado da base real e contém o projeto Firebase `smart24-fusion`, incluindo `databaseURL`.

O UID administrativo que já estava em `firebase/database.seed.example.json` também foi preservado, e o caminho `integrations` foi acrescentado para a atualização Yoosee.

O arquivo `edge-agent/.env.example` recebeu somente a URL pública real do Realtime Database. Usuário, senha, RTSP, conta de serviço e outras credenciais privadas continuam propositalmente fora do ZIP.

## O que não foi inventado

- senha da câmera;
- usuário da câmera;
- RTSP;
- ONVIF;
- senha Yoosee;
- token ou InviteCode;
- arquivo `service-account.json`.

## Publicação

Ao subir esta versão, substitua os arquivos do repositório e publique também as regras de `firebase/database.rules.json` no Realtime Database. Não importe o arquivo seed sobre um banco que já possui dados.
