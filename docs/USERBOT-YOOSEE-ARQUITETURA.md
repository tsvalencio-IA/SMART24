# SMART24 — Arquitetura Usuário Robô Yoosee

## Objetivo

Fazer o SMART24 operar como uma conta autorizada do ecossistema Yoosee/Gwell:

1. Proprietário compartilha a câmera com uma conta Yoosee exclusiva do SMART24.
2. SMART24 autentica essa conta por SDK/API oficial.
3. SMART24 lista apenas câmeras autorizadas/compartilhadas.
4. SMART24 abre o vídeo remotamente por P2P/cloud do fabricante.
5. Frames seguem para o motor de visão.
6. Motor de visão gera eventos e alertas no Firebase.

## O que este pacote já cria

- contrato `YooseeGateway`;
- modelos de dispositivo e frame;
- tela `UserBotActivity`;
- verificação de credenciais/SDK;
- separação entre P2P e inteligência visual;
- injeção segura de AppId/AppToken pelo GitHub Actions;
- bloqueio explícito para qualquer recurso não implementado.

## O que NÃO está implementado ainda

Não temos informação/artefato suficiente para implementar honestamente:

- login real da conta Yoosee dentro do SMART24;
- consulta real dos dispositivos compartilhados;
- abertura real do stream P2P;
- callback real de frames do SDK.

Esses quatro pontos dependem do SDK atual/compatível da Yoosee/Gwell e de AppId/AppToken emitidos para o pacote do SMART24.

## Evidência técnica pública

Há projetos oficiais/públicos da GWTimes para integração de monitoramento/P2P. A documentação histórica informa que AppId, AppToken e AppVersion são fornecidos/registrados pelo fabricante e vinculados ao aplicativo. O repositório Android IoTVideo é apresentado como SDK Demo de monitoramento, gravação, playback, controle, comunicação e alarmes.

Isso confirma que a arquitetura é legítima, mas NÃO confirma que chaves antigas/teste funcionem no Yoosee atual.

## Marco 1 — Conta Robô

Antes de IA:

- criar conta Yoosee exclusiva SMART24;
- compartilhar uma câmera de teste para ela;
- entrar nessa conta no aplicativo oficial, usando outra rede (4G/5G);
- confirmar que o vídeo remoto aparece.

## Marco 2 — SDK

Solicitar à Yoosee/Gwell:

- SDK Android atual;
- AppId;
- AppToken;
- regra de AppVersion;
- documentação de login por e-mail;
- API de lista de dispositivos compartilhados;
- API de status online;
- API de live view P2P;
- forma de receber frames/raw video para IA;
- permissões de conta convidada/compartilhada;
- requisitos para Android 14/15 e arm64.

## Marco 3 — Primeiro vídeo dentro do SMART24

Critério de sucesso:

- Yoosee oficial fechado;
- SMART24 logado com conta própria;
- dispositivo compartilhado aparece;
- vídeo remoto aparece dentro do SMART24;
- teste realizado fora da LAN da câmera.

## Marco 4 — Inteligência

Somente depois:

`frame -> pessoa -> pose/pulsos -> objeto -> evento -> Firebase`

## Segurança

- não versionar AppToken;
- não salvar senha Yoosee em texto puro;
- não enviar senha Yoosee ao Firebase;
- usar conta exclusiva por ambiente;
- registrar consentimento e finalidade para biometria/identificação de pessoas.
