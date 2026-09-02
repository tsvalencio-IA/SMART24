# SMART24 2.2 — Fundação do Usuário Robô Yoosee

Esta versão muda o rumo do projeto para a arquitetura correta:

**conta compartilhada -> SDK P2P -> vídeo remoto -> visão -> Firebase**

## Para testar agora

1. Compile o APK normalmente pelo GitHub Actions.
2. Abra o SMART24.
3. Toque em `NOVO: Modo Usuário Robô Yoosee (P2P)`.
4. Toque em `1. Verificar SDK e credenciais`.

O resultado esperado NESTA fase é informar que faltam SDK/credenciais. Isso é intencional e verdadeiro.

5. Crie uma conta Yoosee exclusiva para o SMART24.
6. Compartilhe a câmera de teste com essa conta.
7. No mesmo aparelho, toque em `2. Validar essa conta no Yoosee oficial`.
8. Entre com a conta SMART24 e confirme que a câmera compartilhada aparece e abre remotamente.

Quando isso estiver confirmado, o próximo desenvolvimento é ligar `YooseeSdkGateway.kt` ao SDK oficial.

## Não coloque AppToken no GitHub

Quando a Yoosee/Gwell fornecer as chaves, crie Secrets no repositório:

- `YOOSEE_APP_ID`
- `YOOSEE_APP_TOKEN`
- `YOOSEE_APP_VERSION`

O workflow já está preparado para injetar esses valores na compilação.
