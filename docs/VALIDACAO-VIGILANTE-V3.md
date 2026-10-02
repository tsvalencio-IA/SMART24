# SMART24 Vigilante Celular 3.0.0 — validação

Validação concluída em 01/10/2026, às 23:15 (São Paulo).

## Código e execução

- Integração v3: `f8ab6ca5c1551d0fca0aa6438058f030e62d0add`.
- Código Android final testado: `398af9d7e10c27c5731664e3c7022c15f34e7598`.
- [GitHub Actions aprovado](https://github.com/tsvalencio-IA/SMART24/actions/runs/36954287927).
- Build Android, testes e lint: **aprovados**.
- 12 testes unitários: 6 de configuração/credenciais RTSP, 4 de inferência visual e 2 de fila persistente; zero falhas.
- 2 testes no Android 10/API 29 x86_64: instalação/abertura e recepção de imagem RTSP por LibVLC; zero falhas ou testes ignorados.
- Servidor MediaMTX 1.12.2 com fonte sintética H.264/640×360/10 fps. Log registrou cliente lendo `testcam` por TCP durante o teste Android.
- XML Android, referências de IDs e diff: conferidos.
- Parser QR web: 4 verificações aprovadas. Agente Python: 4 testes aprovados.
- GitHub Pages associado ao commit passou no deploy.

O lint não encontrou erros impeditivos; existem avisos de internacionalização, estilos, APIs antigas e fontes legadas. A validação não afirma zero avisos.

## APK entregue

- Nome: `SMART24-Vigilante-Celular-v3.apk`.
- Pacote: `br.com.thiaguinhosolucoes.smart24vision`.
- Versão: `3.0.0-mobile-vigilante`; versionCode `7`.
- Android mínimo: 8/API 26; target SDK 35.
- APK universal com arm64-v8a, armeabi-v7a, x86 e x86_64: **393.609.070 bytes**, aproximadamente **375,4 MiB**.
- SHA-256: `906842b89760dc6cea7eaf2227f288cf1429834743d96a64c86346147851dbfc`.
- [Artefato original do Actions](https://github.com/tsvalencio-IA/SMART24/actions/runs/36954287927/artifacts/11205301291).
- O workflow de publicação reutiliza esse artefato aprovado e confere o SHA-256 antes de criar o download APK da pré-versão `v3.0.0-vigilante-celular`.

## Alterações entregues

- Aplicação do ZIP v3 com tela móvel e LibVLC RTSP independente do aplicativo Yoosee.
- Descoberta de IP por ONVIF, calibração local por loja/câmera/zona/SKU e eventos prováveis no painel existente.
- Confirmação de vídeo por quadro visível, credenciais protegidas, tentativas isoladas e reconexão após queda.
- Fila persistente e idempotente; sessão Firebase renovada em memória e falhas de sincronização visíveis.
- Arquivos antigos preservados fora da compilação; código web e Firebase existentes preservados.
- Testes Android e regressões automatizadas, documentação de continuidade e publicação do APK validado.

## O que ainda depende do ambiente físico

A câmera `192.168.15.5` **não foi acessada** por este ambiente. A confirmação acima usa um fluxo RTSP de teste; não é uma confirmação da câmera de Thiago. Usuário/senha NVR devem ser informados no próprio Android, que precisa estar na mesma rede da câmera.

Login, permissões e regras Firebase efetivamente publicadas precisam ser exercitados com a conta real. Não foram utilizadas credenciais de produção nem foram escritos eventos de teste no Firebase real.

O motor continua usando uma pose principal e atribui SKU pela posição calibrada. Eventos são hipóteses para revisão, não comprovação de furto, venda, pagamento ou quantidade exata. O aplicativo precisa ficar aberto; não é um serviço multipessoa permanente para seis lojas simultâneas.

## Primeiro uso

1. Baixe e instale o APK. Se o instalador apontar conflito de assinatura, a versão anterior pode precisar ser desinstalada; essa operação apaga os dados locais daquele APK.
2. Conecte o celular ao Wi-Fi da câmera e abra SMART24 Vigilante.
3. Confira IP `192.168.15.5`, porta `554` e digite usuário/senha NVR/RTSP.
4. Toque em **CONECTAR DIRETO NA CÂMERA** e aguarde imagem + **VÍDEO CONFIRMADO**.
5. Toque em **CAPTURAR QUADRO E CALIBRAR PRODUTOS**. Marque dois cantos por SKU e informe zona, produto e SKU.
6. Volte, deixe a prateleira desobstruída e toque em **INICIAR VIGILANTE**. Após uma retirada/devolução, afaste mão e corpo da zona para permitir avaliar a imagem.
7. Para enviar eventos ao painel, entre no Firebase com usuário existente admin/operator e confira a fila de sincronização e a aba Eventos.

Powered by thIAguinho Soluções Digitais
