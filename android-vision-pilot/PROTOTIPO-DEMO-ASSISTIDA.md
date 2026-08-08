# SMART24 — Protótipo de demonstração assistida

## Objetivo

Demonstrar ao proprietário, com uma câmera Yoosee real, que o SMART24 consegue:

- capturar o vídeo exibido pelo aplicativo nativo;
- isolar somente a imagem da câmera, removendo a interface capturada do celular;
- detectar pessoas anônimas, mãos e objetos genéricos;
- exigir persistência em vários quadros antes de informar `objeto na mão`;
- permitir que o operador confirme o momento em que alguém pegou, devolveu ou possivelmente ocultou um item;
- associar a confirmação à pessoa e ao objeto visual mais próximos;
- publicar o evento, o carrinho demonstrativo, a imagem e a ocorrência no Firebase;
- mostrar tudo em tempo real no painel hospedado no GitHub Pages.

## Verdade técnica

Nesta versão, o SKU é informado pelo operador antes da demonstração.

Os botões **PEGOU**, **DEVOLVEU**, **ESCONDEU** e **ALERTA** são controlados por uma pessoa. A visão computacional tenta acompanhar um objeto genérico ou, quando isso não for possível, usa o pulso da pessoa como referência.

Esta versão não deve ser apresentada como reconhecimento automático de qualquer produto.

## Fluxo da demonstração

```text
Câmera Yoosee
      ↓
Aplicativo Yoosee no Android
      ↓
Captura autorizada de tela
      ↓
SMART24 Vision Pilot
      ↓
Pessoa + pulso + objeto genérico
      ↓
Operador toca PEGOU / DEVOLVEU / ESCONDEU
      ↓
Firebase Realtime Database
      ↓
Painel GitHub Pages + alerta do navegador
```

## Preparação no GitHub

1. Envie esta versão do projeto ao repositório.
2. Abra a aba **Actions**.
3. Execute **Build SMART24 APK Completo**.
4. Baixe o artefato `SMART24-APK-COMPLETO-V2.3.0`.
5. Extraia e instale `app-debug.apk`.

O workflow usa Gradle no próprio GitHub. Não é necessário ter Android Studio no celular.

## Preparação do Firebase

O usuário usado no aplicativo Android deve ter função:

- `admin`; ou
- `operator`.

Para visualizar e revisar ocorrências no painel, use uma conta:

- `admin`; ou
- `auditor`.

Não coloque senha da câmera no Firebase, no GitHub ou no formulário.

## Preparação da câmera

1. Desative rastreamento automático, patrulha e rotação automática.
2. Deixe a câmera fixa.
3. Enquadre a pessoa e a área do produto.
4. Evite luz estourada e reflexos.
5. Abra o vídeo ao vivo no Yoosee e deixe em tela cheia.

## Configuração no SMART24 Vision Pilot

1. Abra o aplicativo SMART24.
2. Confirme que a câmera aparece no Yoosee.
3. Informe e-mail, senha, loja, câmera e conector Firebase.
4. Preencha:
   - nome do item demonstrado;
   - SKU demonstrativo;
   - zona ou prateleira.
5. Para tela dividida, deixe **Mostrar botões flutuantes** desmarcado. Para Yoosee em tela cheia, marque a opção e autorize **Exibir sobre outros apps**.
6. Toque em **Entrar no Firebase**.
7. Toque em **Autorizar análise e abrir Yoosee**.
8. Autorize a captura de tela.
9. Abra a câmera e deixe o vídeo ao vivo visível.
10. Volte ao SMART24 e toque em **3. Delimitar somente o vídeo da câmera**.
11. Marque os dois cantos da imagem ao vivo sem incluir menus, botões ou o painel flutuante.
12. Volte ao Yoosee e aguarde o SMART24 gerar o quadro recortado.
13. Use **4. Calibrar prateleira** sobre o vídeo já isolado.
14. Em tela dividida, opere PEGOU, DEVOLVEU, ESCONDEU e ALERTA na seção **D. Controle manual** do módulo.

## Controles flutuantes

Os mesmos comandos também ficam dentro do módulo nativo para uso em tela dividida. O painel flutuante é opcional e deve permanecer fora da área de vídeo delimitada.

### PEGOU

Use no momento em que a pessoa pega o item.

O sistema:

- usa a última pessoa detectada;
- prioriza uma associação mão/objeto já estável em vários quadros;
- se ainda não houver estabilidade, registra explicitamente a evidência inferior;
- cria um identificador demonstrativo;
- adiciona o item ao carrinho;
- guarda um recorte do objeto confirmado para revisão do futuro conjunto de treinamento;
- publica `DEMO_TRACK_STARTED`.

### DEVOLVEU

Use quando a pessoa devolver o item.

O sistema:

- publica `DEMO_ITEM_RETURNED`;
- remove o item do carrinho demonstrativo;
- encerra o acompanhamento.

### ESCONDEU

Use quando o item entrar no bolso, roupa ou bolsa.

O sistema:

- publica `DEMO_POSSIBLE_CONCEALMENT`;
- cria uma ocorrência pendente;
- envia a imagem mais recente;
- mantém o item no carrinho demonstrativo.

### ALERTA

Use para criar uma ocorrência manual sem afirmar ocultação.

O sistema publica `DEMO_ALERT_SENT` e envia o caso para revisão.

### FINALIZAR

Encerra o rastreio atual sem dizer que houve devolução ou irregularidade.

## Painel GitHub Pages

1. Entre no painel com uma conta autorizada.
2. Abra **Monitoramento ao vivo**.
3. Toque em **Ativar alertas**.
4. Autorize notificações no navegador.
5. Observe:
   - pessoas detectadas;
   - objetos genéricos;
   - objetos próximos das mãos;
   - objetos estáveis nas mãos;
   - item acompanhado;
   - modo visual;
   - eventos;
   - carrinho;
   - ocorrência com imagem.

## Roteiro de apresentação

1. Mostre o painel sem nenhum item acompanhado.
2. Uma pessoa entra no enquadramento.
3. Ela pega o produto.
4. O operador toca **PEGOU**.
5. Mostre o contorno do item/pulso e o carrinho no painel.
6. A pessoa devolve o item.
7. Toque **DEVOLVEU**.
8. Repita o teste e coloque o item no bolso.
9. Toque **ESCONDEU**.
10. Mostre a notificação e a ocorrência no painel.

## Limitações conhecidas

- a câmera deve ficar fixa;
- a captura depende do Yoosee permanecer visível;
- o sistema pode perder objetos cobertos pela mão ou pelo corpo;
- o detector genérico não reconhece necessariamente o SKU;
- nesta fase, o ML Kit de pose entrega mãos detalhadas apenas para a pessoa principal de maior confiança;
- o operador confirma o evento;
- não há integração com pagamento ou porta;
- não é operação 24 horas;
- não é prova de precisão para as seis lojas.

## Critério de sucesso do protótipo

O protótipo está aprovado para apresentação quando:

1. o painel recebe a imagem da câmera;
2. uma pessoa aparece com ID temporário;
3. o botão PEGOU cria o carrinho;
4. o painel mostra o item acompanhado;
5. DEVOLVEU remove o item;
6. ESCONDEU cria ocorrência com imagem;
7. o navegador recebe o alerta quando autorizado.

Depois disso, a próxima etapa será substituir gradualmente os botões por eventos automáticos no servidor.
