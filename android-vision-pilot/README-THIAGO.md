# SMART24 Android completo — painel, loja 3D e câmera Yoosee

Ao abrir o APK, a primeira tela agora é o SMART24 completo hospedado no GitHub Pages: dashboard, cadastro de produtos, etiquetas, reposição, câmeras, eventos, carrinhos e loja 3D. O botão **ABRIR MÓDULO CÂMERA YOOSEE** entra na ponte de demonstração entre:

- o vídeo real aberto no aplicativo Yoosee;
- a análise local de pessoas, pulsos, objetos genéricos e etiquetas;
- o Firebase Realtime Database;
- o painel SMART24 hospedado no GitHub Pages.

## O que esta versão faz de verdade

1. entra no Firebase com usuário `admin` ou `operator`;
2. captura, com autorização, a tela do vídeo aberto no Yoosee;
3. detecta pessoas e cria identificadores temporários;
4. localiza pulsos quando a pose está visível;
5. detecta objetos genéricos e tenta manter o ID visual;
6. permite operar um painel flutuante sobre o Yoosee;
7. publica eventos, carrinho, imagem e ocorrências no Firebase;
8. mostra a demonstração no painel web em tempo real.

O módulo da câmera atualiza o estado mesmo em tela dividida. Quando o primeiro quadro válido chega, ele mostra **Imagem recebida** e libera a calibração automaticamente. Se nenhum quadro chegar ou a captura vier preta, a mensagem informa o erro na própria tela.

A assinatura de gravação em nuvem do Yoosee não é usada pela captura do SMART24. Na autorização do Android, escolha **Tela inteira**.

A versão 2.2.1 inclui o ícone oficial S24 em alta resolução, versões normal e circular e recursos adaptativos para os formatos aplicados pelo Android.

## Controles da demonstração

- **PEGOU:** o operador confirma a retirada.
- **DEVOLVEU:** o operador confirma a devolução.
- **ESCONDEU:** cria possível ocorrência para revisão.
- **ALERTA:** envia um alerta manual.
- **FINALIZAR:** encerra o acompanhamento sem conclusão.

## O que não faz ainda

- não reconhece automaticamente qualquer SKU;
- não identifica civilmente o morador;
- não garante que um objeto oculto continue rastreável;
- não compara com o checkout;
- não controla a porta;
- não funciona como servidor 24 horas;
- não deve ser implantado nas seis lojas como solução final.

## Como gerar o APK

1. envie a pasta completa ao repositório;
2. abra **Actions** no GitHub;
3. execute **Build SMART24 APK Completo**;
4. baixe `SMART24-APK-COMPLETO-V2.2.1`;
5. extraia e instale `app-debug.apk`.

## Manual completo

Leia:

[PROTOTIPO-DEMO-ASSISTIDA.md](./PROTOTIPO-DEMO-ASSISTIDA.md)

## Observação técnica

O SKU demonstrado é informado previamente pelo operador. A visão associa a confirmação manual à pessoa, ao pulso e ao objeto genérico mais próximos. Quando o objeto não é detectado de forma estável, o painel informa que está usando o pulso como referência.

Esse comportamento é proposital para uma apresentação verdadeira: mostramos uma demonstração assistida agora e deixamos a automação completa para a etapa do servidor.
