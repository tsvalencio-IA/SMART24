# Relatório de alterações — SMART24 2.1.0 Assisted Demo

## Entrega

Foi criada uma demonstração assistida para Android, integrada ao Firebase Realtime Database e ao painel GitHub Pages.

## Android

### Novo controlador

Arquivo:

`android-vision-pilot/app/src/main/java/br/com/thiaguinhosolucoes/smart24vision/AssistedDemoEngine.kt`

Responsabilidades:

- receber a confirmação humana de retirada, devolução e possível ocultação;
- associar a confirmação à pessoa, ao pulso e ao objeto genérico mais próximos;
- manter um acompanhamento visual provisório;
- publicar eventos;
- atualizar o carrinho demonstrativo;
- criar ocorrências para revisão.

### Controle flutuante

O `CaptureService` agora pode exibir um painel sobre o Yoosee com:

- PEGOU;
- DEVOLVEU;
- ESCONDEU;
- ALERTA;
- FINALIZAR.

Foi adicionada a permissão Android `SYSTEM_ALERT_WINDOW`. O usuário precisa autorizá-la manualmente.

### Visão

O `VisionEngine` agora publica:

- pessoas;
- pulsos esquerdo e direito;
- objetos genéricos;
- QR SMART24.

A classificação de objetos é genérica. O SKU demonstrado continua sendo informado pelo operador.

### Imagem analisada

O quadro enviado ao Firebase mostra:

- caixas das pessoas;
- pulsos;
- objetos genéricos;
- item demonstrativo acompanhado;
- estado do acompanhamento.

## Painel web

Foram acrescentados:

- quantidade de objetos genéricos;
- estado da demonstração assistida;
- pessoa associada;
- modo visual;
- observação;
- imagem da ocorrência;
- botão para ativar notificações do navegador;
- novos nomes de eventos demonstrativos.

## Eventos criados

- `DEMO_TRACK_STARTED`
- `DEMO_ITEM_RETURNED`
- `DEMO_POSSIBLE_CONCEALMENT`
- `DEMO_ALERT_SENT`
- `DEMO_TRACK_FINISHED`

## Testes executados

- 4 testes Python do agente local: aprovados;
- 4 testes JavaScript do leitor QR: aprovados;
- verificação de sintaxe de todos os arquivos JavaScript: aprovada;
- leitura de todos os XML Android: aprovada;
- leitura de todos os JSON: aprovada;
- conferência de IDs Android usados no Kotlin contra os layouts: aprovada;
- conferência de IDs usados no JavaScript contra o HTML: aprovada.

## Limitação de validação

O APK não foi compilado neste ambiente porque não há Gradle nem Android SDK instalados e não há acesso de rede para baixar essas ferramentas.

O repositório já contém o workflow:

`.github/workflows/build-smart24-vision.yml`

Ao publicar os arquivos no GitHub, o workflow deve executar a compilação real do APK. A compilação do GitHub Actions será a validação final das APIs Kotlin/Android.

## Promessa correta para a apresentação

> O SMART24 acompanha pessoas e objetos genéricos no vídeo da câmera. Durante esta demonstração, o operador confirma a ação e o sistema registra, acompanha e envia a ocorrência ao painel.

## Promessa incorreta

> O sistema já reconhece automaticamente qualquer produto e qualquer roubo.

Essa automação ficará para a etapa do servidor e deverá ser validada com vídeos reais e métricas.
