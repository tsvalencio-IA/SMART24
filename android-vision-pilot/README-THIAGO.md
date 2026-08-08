# SMART24 Android completo — painel, loja 3D e câmera Yoosee

Ao abrir o APK, a primeira tela agora é o SMART24 completo hospedado no GitHub Pages: dashboard, cadastro de produtos, etiquetas, reposição, câmeras, eventos, carrinhos e loja 3D. O botão **ABRIR MÓDULO CÂMERA YOOSEE** entra na ponte de demonstração entre:

- o vídeo real aberto no aplicativo Yoosee;
- a análise local de pessoas, pulsos, objetos genéricos e etiquetas;
- o Firebase Realtime Database;
- o painel SMART24 hospedado no GitHub Pages.

## O que esta versão faz de verdade

1. entra no Firebase com usuário `admin` ou `operator`;
2. captura, com autorização, a tela do vídeo aberto no Yoosee;
3. obriga o operador a delimitar somente os pixels do vídeo, excluindo menus, teclado, SMART24 e miniaturas recursivas;
4. detecta pessoas e cria identificadores temporários, sem reconhecimento civil ou facial de identidade;
5. localiza pulsos, polegares, indicadores e mindinhos quando a pose principal está visível;
6. detecta objetos genéricos e mantém IDs visuais mesmo quando o ML Kit ainda não devolve trackingId;
7. separa `objeto visível`, `próximo da mão` e `objeto na mão estável`, exigindo vários quadros para o último estado;
8. salva, no toque em PEGOU, um recorte rotulado para revisão e futura formação do conjunto de imagens dos produtos;
9. permite operar PEGOU, DEVOLVEU, ESCONDEU e ALERTA dentro do módulo em tela dividida; o painel flutuante ficou opcional;
10. publica eventos, carrinho, imagem e ocorrências no Firebase;
11. mostra a demonstração no painel web em tempo real.

O módulo da câmera atualiza o estado mesmo em tela dividida. Quando o primeiro quadro válido chega, ele mostra **Imagem recebida** e libera a calibração automaticamente. Se nenhum quadro chegar ou a captura vier preta, a mensagem informa o erro na própria tela.

A assinatura de gravação em nuvem do Yoosee não é usada pela captura do SMART24. Na autorização do Android, escolha **Tela inteira**.

A versão 2.2.1 inclui o ícone oficial S24 em alta resolução, versões normal e circular e recursos adaptativos para os formatos aplicados pelo Android.

A versão 2.2.2 corrige a validação do retorno da autorização de captura. No Android, `RESULT_OK` vale `-1`; agora esse valor é reconhecido corretamente como autorização aceita, em vez de ser tratado como erro.

A versão 2.3.0 adiciona o recorte obrigatório da área real da câmera, mãos detalhadas, rastreamento estável de objetos, associação mão/objeto com evidência temporal e coleta controlada de recortes para o futuro modelo dos produtos.

## Controles da demonstração

Em tela dividida, use os controles que ficam dentro do próprio módulo nativo. Marque **Mostrar botões flutuantes** somente quando quiser operar com o Yoosee ocupando a tela toda; deixar desmarcado evita que o painel cubra pixels importantes da câmera.

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

1. publique no Firebase as regras atualizadas de `firebase/database.rules.json`;
2. envie a pasta completa ao repositório;
3. abra **Actions** no GitHub;
4. execute **Build SMART24 APK Completo**;
5. baixe `SMART24-APK-COMPLETO-V2.3.0`;
6. extraia e instale `app-debug.apk`.

## Manual completo

Leia:

[PROTOTIPO-DEMO-ASSISTIDA.md](./PROTOTIPO-DEMO-ASSISTIDA.md)

## Observação técnica

O SKU demonstrado é informado previamente pelo operador. A visão só chama de `HELD_STABLE` uma associação mão/objeto que permaneça geometricamente coerente em mais de um quadro. Quando essa evidência não existe, o painel informa `HAND_NEAR_OBJECT`, objeto genérico ou pulso de referência, sem fingir que reconheceu o produto.

Para reconhecer o SKU visualmente com fidelidade, ainda será necessário fotografar cada produto real em vários ângulos, distâncias, iluminações e níveis de oclusão. Os recortes confirmados na V2.3.0 iniciam essa base, mas não substituem treinamento e validação separados.

Esse comportamento é proposital para uma apresentação verdadeira: mostramos uma demonstração assistida agora e deixamos a automação completa para a etapa do servidor.
