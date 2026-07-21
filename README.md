# SMART24 Fusion — MVP Fase 1

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

- Login com Firebase Authentication, quando configurado.
- Realtime Database com regras bloqueadas por padrão.
- Modo demonstrativo local quando `firebase-config.js` ainda contém `COLE_AQUI`.
- Dashboard.
- Cadastro e edição de produtos.
- Geração, salvamento, visualização e impressão de etiquetas com serial e QR Code.
- Cadastro seguro de metadados de câmeras.
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
- sistema real do cliente.

## Fotos e HTMLs anteriores

Na sessão de geração deste pacote, os HTMLs anteriores e as dez fotos citadas não estavam fisicamente disponíveis. Por isso:

- nenhum arquivo visual anterior foi substituído de forma silenciosa;
- nenhuma medida foi inventada;
- o simulador usa uma representação demonstrativa;
- a fidelização visual às fotos permanece pendente até os arquivos originais serem anexados.

## Como começar

Abra `PASSO-A-PASSO-THIAGO.md` e execute somente o **Passo 1**.

## Modo demonstração

Enquanto o Firebase não estiver configurado, o login mostra o botão **Abrir demonstração local**. Os dados são salvos apenas no `localStorage` do navegador e não são enviados para servidor.

Quando todos os campos obrigatórios de `firebase-config.js` forem preenchidos, o botão de demonstração desaparece e o login real do Firebase passa a ser exigido.

## Estrutura

```text
smart24-fusion-mvp/
├── index.html
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
