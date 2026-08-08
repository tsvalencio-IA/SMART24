# SMART24 2.4.0 — câmera direta RTSP / ONVIF

Esta versão recebe o vídeo diretamente da câmera pela rede local. O Yoosee não
precisa permanecer aberto e o SMART24 não grava a tela do celular no modo
principal.

## Antes do teste

1. No Yoosee, mantenha **Conexão NVR** ativada.
2. Conecte a câmera e o celular ao mesmo roteador/Wi-Fi.
3. Tenha em mãos a senha criada em **Conexão NVR**. Não coloque essa senha no
   GitHub e não a envie para terceiros.
4. A assinatura de gravação em nuvem do Yoosee não é usada por esta conexão
   local.

## Teste no celular

1. Instale o APK 2.4.0. Se o Android disser que a atualização é incompatível,
   desinstale somente o APK de teste anterior e instale este novamente.
2. Abra o SMART24. A loja 3D e o painel continuam sendo a tela inicial.
3. Toque em **ABRIR CÂMERA DIRETA RTSP / ONVIF**.
4. Entre no Firebase com o usuário que já funciona no projeto.
5. Confira o IP local, porta `554`, usuário `admin` e fluxo `onvif1`.
6. Digite no próprio celular a senha NVR/RTSP e toque em
   **CONECTAR DIRETAMENTE À CÂMERA**.
7. Se necessário, use **LOCALIZAR CÂMERA ONVIF NO WI-FI** para atualizar o IP.
8. Quando o primeiro quadro aparecer, calibre a zona e faça a demonstração com
   **PEGOU**, **DEVOLVEU**, **ESCONDEU** ou **ALERTA**.

O aplicativo tenta automaticamente o fluxo principal e o secundário, além dos
nomes de usuário NVR compatíveis. A senha existe apenas em memória durante a
tela ao vivo: não é salva nas preferências, no Firebase nem no projeto.

## O que foi preservado

- loja 3D e painel completo no GitHub Pages;
- cadastro e leitura de produtos;
- Firebase Realtime Database e eventos já existentes;
- reconhecimento de pessoa, pose/pulsos, objeto e associação mão-objeto;
- demonstração assistida controlada pelo operador;
- modo antigo de captura de tela, acessível apenas como contingência.

## Upload pelo GitHub

O ZIP `SOMENTE-ARQUIVOS-ALTERADOS` contém somente arquivos reais, sem pastas
vazias. Extraia-o e envie seu conteúdo na raiz do repositório, mantendo os
caminhos. O workflow gera o artefato
`SMART24-APK-COMPLETO-V2.4.0-DIRECT-RTSP`.

## Limite desta entrega

O projeto e o APK foram compilados e verificados. A conexão física só pode ser
confirmada no celular que está na mesma rede da câmera. Se não abrir, anote a
mensagem exata mostrada pelo SMART24; ela distingue senha, IP, fluxo e perda de
rede.
