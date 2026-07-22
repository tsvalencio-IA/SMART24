# SMART24 Fusion — MVP Fase 1 + Cadastro QR/Yoosee + Loja 3D

Painel estático responsivo para estruturar a primeira fase do sistema de auditoria inteligente de mercadinhos autônomos.

Responsável pela solução: **thIAguinho Soluções Digitais**.

## Regra central

Para cada SKU:

```text
quantidade esperada = retiradas - devoluções
divergência = quantidade esperada - quantidade paga
```

A comparação é feita por produto/SKU. Totais gerais iguais não eliminam inconsistências entre produtos diferentes.

## Princípios obrigatórios

- A saída não é bloqueada automaticamente.
- O sistema não acusa automaticamente.
- Toda possível divergência exige revisão humana.
- Firebase recebe eventos estruturados, não vídeo contínuo.
- Credenciais de câmera ficam fora do GitHub.
- QR Code não é apresentado como rastreamento de item oculto.
- RFID é uma evolução futura, ainda não implementada.

## O que este pacote contém

- Login com Firebase Authentication já apontando para o projeto real `smart24-fusion`.
- Realtime Database com regras bloqueadas por padrão.
- Modo demonstrativo local preservado como contingência; nesta entrega o Firebase real já está configurado.
- Dashboard.
- Cadastro e edição de produtos.
- Geração, salvamento, visualização e impressão de etiquetas com serial e QR Code.
- Cadastro seguro de metadados de câmeras.
- Leitor de QR Code pelo navegador, com câmera ou imagem.
- Reconhecimento de QR/link Yoosee com descarte de InviteCode, token e link completo.
- Registro do e-mail operacional Yoosee sem armazenar senha.
- Loja 3D integrada pelo arquivo `simulator-3d.html`.
- Linha do tempo de eventos.
- Ocorrências com classificação humana.
- Simulador conceitual com oito cenários.
- Estrutura do agente local Python para a Fase 2.
- Documentação técnica e operacional.

## O que não está conectado

- câmeras IP reais;
- reconhecimento facial real;
- caixa/autoatendimento real;
- porta ou controle de saída;
- RFID;
- visão computacional de retirada/devolução;
- múltiplas câmeras reais;
- login automático do painel na conta Yoosee;
- aceitação automática de convite Yoosee;
- API oficial Yoosee para vídeo;
- sistema real do cliente.

## Loja 3D e fotografias

O HTML 3D enviado por Thiago foi incorporado integralmente como `simulator-3d.html` e aparece dentro do módulo **Simulador**. O simulador operacional anterior foi preservado em uma segunda aba.

As dez fotografias citadas não estavam anexadas fisicamente nesta atualização. O HTML 3D mantém a opção de selecionar fotografias locais e continua identificado como representação demonstrativa. Nenhuma medida foi inventada.

## Como começar

Esta entrega já contém os valores públicos reais do Firebase encontrados no ZIP atual do GitHub. Abra `PASSO-A-PASSO-THIAGO.md` e siga diretamente os **Passos 18 a 22**.

## Modo demonstração

Enquanto o Firebase não estiver configurado, o login mostra o botão **Abrir demonstração local**. Os dados são salvos apenas no `localStorage` do navegador e não são enviados para servidor.

Quando todos os campos obrigatórios de `firebase-config.js` forem preenchidos, o botão de demonstração desaparece e o login real do Firebase passa a ser exigido.

## Estrutura

```text
SMART24-main/
├── index.html
├── simulator-3d.html
├── firebase-config.js
├── firebase-config.example.js
├── README.md
├── PASSO-A-PASSO-THIAGO.md
├── .gitignore
├── assets/
├── firebase/
├── edge-agent/
└── docs/
```


## CORREÇÃO V4.2 — MESMO CELULAR

Não tente apontar a câmera para um QR exibido no próprio aparelho. O APK possui as opções **Usar conta Yoosee que já tem a câmera**, **Colar link copiado** e **Escolher print ou imagem do QR**. Se a câmera já aparece no Yoosee, o QR não é necessário.
