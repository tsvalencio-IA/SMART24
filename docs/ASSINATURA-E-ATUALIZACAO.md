# SMART24 — assinatura permanente e atualização interna

## Objetivo

A partir da versão `3.3.2-auto-update` (versionCode 12), os APKs de produção devem usar sempre a mesma chave privada.

Certificado público esperado (SHA-256):

`7B2B93AFB05B660423AC1DC29E34905F5E2A2BE77ED226BCA9BE112605387E0D`

A chave privada **não deve ser enviada ao repositório**.

## Secrets necessários no GitHub Actions

Criar em **Settings → Secrets and variables → Actions → New repository secret**:

- `SMART24_KEYSTORE_B64`: conteúdo Base64 do arquivo `smart24-release.jks`.
- `SMART24_KEYSTORE_PASSWORD`: senha da chave/keystore.

O alias é fixo: `smart24`.

## Fluxo automático

1. O workflow normal compila, roda testes unitários, lint e Android smoke.
2. Somente se build e smoke tests passarem, o job `publish-signed` é liberado.
3. Se os dois secrets ainda não existirem, o job termina sem publicar APK de produção.
4. Com os secrets configurados, o workflow:
   - restaura a chave somente no runner temporário;
   - gera `app-release.apk`;
   - verifica o certificado SHA-256 acima;
   - valida package/versionCode/versionName;
   - publica uma GitHub Release;
   - atualiza `smart24-update.json` com URL e SHA-256 do APK.
5. O aplicativo de produção consulta `smart24-update.json` ao abrir.
6. Quando existir versionCode maior:
   - baixa o APK oficial;
   - verifica SHA-256;
   - solicita, apenas na primeira vez, permissão do Android para instalar apps desta fonte;
   - abre a tela oficial de instalação da atualização.

O Android comum exige confirmação do usuário na tela de instalação. Instalação 100% silenciosa exige gerenciamento corporativo/device-owner, app de sistema ou root.

## Migração

As versões de teste anteriores foram produzidas com chaves debug variáveis. Por isso, a primeira instalação da versão assinada definitivamente poderá exigir remover a versão antiga. Depois de instalar a base definitiva, versões futuras não devem exigir desinstalação, desde que a chave permanente seja preservada.
