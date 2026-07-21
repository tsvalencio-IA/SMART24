# CONHECIMENTO DE CONTINUIDADE — SMART24 FUSION

## 1. FINALIDADE DESTE DOCUMENTO

Este documento transfere o contexto técnico, funcional e operacional do projeto
SMART24 Fusion para uma nova conversa do ChatGPT com capacidade de criar e
entregar arquivos completos para download.

A nova conversa deve continuar exatamente do ponto atual.

Não reiniciar o levantamento.
Não substituir decisões confirmadas.
Não simplificar a arquitetura.
Não fingir integrações que ainda não existem.
Não criar dados de câmera, Firebase ou equipamentos sem confirmação.

Thiago não é programador. A entrega deve ser composta por arquivos prontos,
pastas organizadas, ZIP para download e instruções sem presumir terminal.

---

# 2. PROJETO

Nome:

SMART24 Fusion

Responsável pela solução:

thIAguinho Soluções Digitais

Cliente:

Proprietário de seis mercadinhos autônomos instalados em contêineres dentro de
condomínios.

Problema:

Existem perdas e furtos. O proprietário possui câmeras, mas assistir horas de
gravação é inviável.

Objetivo:

Criar um sistema inteligente que reconstrua cada sessão de compra, compare o que
foi retirado das prateleiras com o que foi devolvido, registrado e pago, e mostre
ao proprietário somente as divergências que precisam de revisão.

---

# 3. FLUXO ATUAL DO MERCADINHO

Fluxo confirmado:

1. morador entra por reconhecimento facial;
2. sistema cria uma sessão;
3. morador circula pelo mercadinho;
4. retira produtos;
5. pode devolver produtos;
6. registra produtos no autoatendimento;
7. informa quantidades ou realiza vários bipes;
8. realiza o pagamento;
9. sai normalmente.

Regra absoluta do projeto:

A IA não bloqueia automaticamente a saída.

Mesmo havendo uma possível divergência:

- a pessoa sai;
- a sessão é salva;
- os eventos relevantes são preservados;
- o proprietário recebe uma ocorrência privada;
- uma pessoa revisa antes de qualquer providência.

O sistema não pode usar automaticamente:

- furto confirmado;
- ladrão;
- culpado;
- crime detectado;
- criminoso.

Termos permitidos:

- possível divergência;
- evento para revisão;
- confiança estimada;
- imagem insuficiente;
- necessita revisão humana.

---

# 4. FÓRMULA CENTRAL

Para cada produto individualmente:

quantidade esperada no caixa =
retiradas − devoluções

Depois:

divergência =
quantidade esperada − quantidade paga

Exemplo regular:

10 retirados
1 devolvido
9 pagos
divergência 0

Exemplo para revisão:

10 retirados
0 devolvidos
9 pagos
possível divergência de 1 unidade

A comparação precisa acontecer por SKU/produto, não somente pelo total geral.

Exemplo:

Produto A:
retirado 2
pago 1
diferença 1

Produto B:
retirado 1
pago 2
diferença -1

Embora o total seja igual, os produtos não correspondem. O sistema deve mostrar
a inconsistência.

---

# 5. DECISÃO SOBRE RASTREAMENTO

Uma etiqueta visual, QR Code ou marcador não pode ser rastreado por câmera quando
está completamente escondido por:

- corpo;
- mão;
- bolso;
- bolsa;
- sacola;
- outro produto;
- porta da geladeira;
- reflexos ou baixa resolução.

Portanto, a arquitetura recomendada é híbrida.

Câmeras:

- acompanham pessoas;
- identificam corpo;
- acompanham trajetória;
- observam braços e mãos;
- detectam interação com zonas;
- ajudam a associar a retirada ao usuário.

Etiquetas visuais e QR Code:

- cadastro;
- reposição;
- manutenção;
- conferência;
- teste inicial;
- identificação quando visíveis.

Evolução recomendada:

RAIN RFID / UHF passivo.

Nesse modelo:

- a câmera acompanha a pessoa;
- o RFID identifica a unidade do produto;
- o sistema combina os eventos;
- não é necessário enxergar a etiqueta durante toda a compra.

Não afirmar que o RFID já está implementado.

---

# 6. EXEMPLO DO FLUXO HÍBRIDO

Entrada:

Facial identifica o cadastro.

Sistema cria:

SESSION-4091
PERSON-01

Retirada:

CAM-04 observa PERSON-01 na zona da prateleira.
A mão entra na zona.
Um produto deixa a zona.
O sistema registra provável retirada.

Produto:

TAG-001 ou EPC-001

Associação:

TAG-001 → SESSION-4091 → PERSON-01

Depois, mesmo que o produto fique escondido:

- a câmera continua acompanhando PERSON-01;
- o sistema mantém o produto associado à sessão;
- o produto só muda de estado com nova evidência.

Estados previstos:

RECEBIDO
CADASTRADO
NA_PRATELEIRA
RETIRADO
ATRIBUIDO_A_SESSAO
DEVOLVIDO
REGISTRADO_NO_CAIXA
PAGO
SAIU
PENDENTE
EM_REVISAO

---

# 7. TRANSFERÊNCIA ENTRE PESSOAS

Quando uma pessoa entrega um produto a outra:

Caso claramente visível:

- registrar a transferência;
- atualizar a sessão responsável.

Caso parcialmente visível:

- marcar responsável provável;
- reduzir confiança;
- preservar o evento.

Caso totalmente escondido:

- não inventar transferência;
- manter o último responsável confirmado;
- usar checkout, RFID, saída e revisão para complementar.

Com três pessoas:

PERSON-01 — verde
PERSON-02 — azul
PERSON-03 — roxo

Cada uma deve ter:

- sessão;
- trajetória;
- carrinho;
- eventos;
- produtos;
- pagamento;
- resultado separados.

---

# 8. CÂMERAS IP

O proprietário informou que deseja usar câmeras IP por serem mais acessíveis.

Foi enviado um endereço semelhante a:

https://us.wps.com/cms/docs/d/...

Esse endereço é um compartilhamento WPS e não deve ser tratado como fluxo direto
de câmera.

A integração real depende de confirmação de:

- marca;
- modelo;
- IP local;
- RTSP;
- ONVIF;
- SDK oficial;
- aplicativo;
- NVR ou DVR;
- resolução;
- taxa de quadros;
- lente;
- sincronização de horário.

Nunca solicitar que Thiago publique:

- senha;
- token;
- chave;
- QR Code privado;
- RTSP com credenciais;
- conta de serviço.

Credenciais devem ficar fora do GitHub.

---

# 9. FUNÇÕES PROPOSTAS PARA AS CÂMERAS

As posições são demonstrativas e precisam ser validadas no local.

CAM-01 — entrada e ligação com facial

CAM-02 — corredor frontal

CAM-03 — corredor traseiro

CAM-04 — estante lateral

CAM-05 — geladeiras

CAM-06 — prateleiras do fundo

CAM-07 — caixa e saída

A solução real deve começar com um piloto menor, usando apenas as vistas
necessárias para uma área de teste.

Não declarar que sete câmeras são obrigatórias sem teste de cobertura.

---

# 10. FOTOS REAIS

Foram enviadas dez fotografias do mercadinho:

1000821653.jpg
1000821654.jpg
1000821655.jpg
1000821656.jpg
1000821657.jpg
1000821658.jpg
1000821659.jpg
1000821660.jpg
1000821661.jpg
1000821662.jpg

As fotos mostram:

- contêiner estreito e comprido;
- entrada de vidro;
- controle de acesso;
- corredor central;
- estante longa em uma lateral;
- geladeiras de vidro na lateral oposta;
- prateleiras no fundo;
- produtos mistos;
- freezer frontal;
- autoatendimento;
- máquina de pagamento;
- porta e saída;
- teto escuro;
- iluminação de trilho;
- reflexos nas geladeiras.

As fotos não confirmam medidas exatas.

O pseudo-3D deve ser apresentado como:

“Representação demonstrativa baseada nas fotografias reais. Medidas físicas
ainda não aferidas no local.”

Não apresentar dimensões inventadas como reais.

Caso as fotos não estejam anexadas na nova conversa, pedir o envio antes de
alterar o pseudo-3D.

---

# 11. HTML PSEUDO-3D

Foram geradas duas versões de HTML.

Decisão confirmada:

Usar a segunda versão como base principal porque possui melhor experiência no
celular.

A segunda versão deverá ser:

- convertida totalmente para PT-BR;
- preservada visualmente;
- integrada ao novo sistema;
- mantida como módulo “Simulador”;
- corrigida tecnicamente;
- ampliada com variáveis reais.

Cenários obrigatórios:

1. compra regular;
2. produto devolvido;
3. item não registrado;
4. três usuários;
5. câmera indisponível;
6. produto diferente pago;
7. pagamento cancelado;
8. interação ambígua.

O simulador não pode fingir conexão real.

Exibir:

“Demonstração conceitual. Câmeras, reconhecimento facial, caixa e porta ainda
não estão conectados.”

---

# 12. PROBLEMAS IDENTIFICADOS NOS HTMLS ANTERIORES

Evitar:

- preencher o resultado antes da animação;
- alterar números fixos sem gerar eventos;
- comparar apenas o total;
- simular três usuários apenas com números;
- iniciar todos os Tweens simultaneamente;
- criar divergência apenas mudando a cor de uma borda;
- dizer que a câmera rastreia etiqueta escondida;
- afirmar posições definitivas de câmera.

O fluxo deve ocorrer progressivamente:

selecionar cenário
→ zerar sessão
→ entrada
→ trajetória
→ retirada
→ devolução quando houver
→ registro no caixa
→ pagamento
→ reconciliação
→ saída
→ ocorrência quando necessária

---

# 13. FIREBASE

Decisão confirmada:

Usar:

- Firebase Authentication;
- Firebase Realtime Database.

Firebase será usado para:

- login;
- perfis e funções;
- produtos;
- etiquetas;
- câmeras;
- conectores;
- sessões;
- eventos;
- ocorrências;
- atualização em tempo real.

Firebase não será usado para transmitir vídeo contínuo.

Estrutura prevista:

roles/
stores/
products/
tags/
cameras/
cameraBridges/
sessions/
events/
occurrences/
auditLogs/

Funções:

admin
operator
auditor

Permissões:

admin:
- administrar;
- cadastrar;
- editar;
- configurar câmeras;
- visualizar;
- revisar.

operator:
- cadastrar produtos;
- gerar etiquetas;
- visualizar eventos;
- trabalhar com reposição.

auditor:
- visualizar;
- revisar ocorrências;
- não alterar configuração crítica.

O banco deve começar bloqueado.

Nenhuma leitura ou escrita pública.

---

# 14. GITHUB

O GitHub será usado para:

- guardar código;
- versionar;
- publicar o painel estático quando aplicável;
- organizar releases.

Não afirmar que arquivos foram enviados ao GitHub sem integração autorizada.

Nunca publicar:

.env
service-account.json
senhas
RTSP com senha
tokens
segredos
arquivos privados

---

# 15. ETIQUETAS DA PRIMEIRA VERSÃO

Primeira versão:

- serial único;
- QR Code;
- produto;
- SKU;
- código de barras;
- zona;
- status;
- campo RFID EPC vazio.

Exemplo:

TAG-LOJA01-000001

Dados:

productId
productName
sku
barcode
zoneId
status
qrPayload
rfidEpc
createdAt
createdBy

Funções:

- cadastrar produto;
- selecionar produto;
- informar quantidade;
- gerar várias etiquetas;
- salvar no Firebase;
- visualizar;
- imprimir.

A etiqueta inicial não deve ser vendida como rastreamento oculto.

---

# 16. RFID FUTURO

A evolução técnica indicada é RAIN RFID/UHF passivo.

Possível arquitetura:

- etiqueta RFID por unidade;
- leitor ou antena em área crítica da prateleira;
- leitura controlada no checkout;
- leitura direcional na saída;
- fusão com as câmeras.

Limitações a validar:

- líquidos;
- metal;
- corpo humano;
- empilhamento;
- interferência entre prateleiras;
- direção da passagem;
- remoção da etiqueta;
- custo por unidade;
- posição da etiqueta.

Antes da implantação completa:

- bancada de teste;
- cinco tipos de produtos;
- aproximadamente cem unidades;
- uma prateleira;
- uma câmera;
- leitor RFID;
- antenas;
- checkout;
- saída em modo auditoria.

---

# 17. CONECTOR LOCAL

A câmera não deve se conectar diretamente ao GitHub Pages.

Arquitetura:

Câmeras IP
→ computador local da loja
→ conector local
→ OpenCV e visão computacional
→ eventos
→ Firebase

Primeira versão do conector:

- ler RTSP pelo `.env`;
- nunca exibir a senha no log;
- atualizar heartbeat;
- informar ONLINE;
- informar OFFLINE;
- informar RECONNECTING;
- informar STOPPED;
- gerar imagem local de depuração;
- não enviar vídeo completo ao Firebase;
- reconectar automaticamente.

Arquivos privados:

.env
service-account.json

Ambos no `.gitignore`.

---

# 18. VISÃO COMPUTACIONAL FUTURA

Bibliotecas inicialmente consideradas:

- OpenCV;
- MediaPipe para pose e mãos;
- rastreamento multicâmera;
- possibilidade futura de NVIDIA DeepStream, dependendo do equipamento.

Funções futuras:

- detectar pessoas;
- atribuir identificadores;
- acompanhar trajetória;
- detectar braços e mãos;
- mapear zonas;
- detectar retirada;
- detectar devolução;
- realizar handoff entre câmeras;
- detectar saída;
- publicar somente eventos.

Não implementar reconhecimento facial novo sem necessidade.

O acesso facial atual deverá fornecer um evento de sessão ou integração
autorizada.

---

# 19. CAIXA

O caixa precisa fornecer:

- sessão;
- código do produto;
- quantidade;
- cancelamento;
- horário;
- pagamento aprovado;
- pagamento rejeitado.

Um bip com quantidade 10 equivale a dez unidades.

Dez bipes individuais também equivalem a dez unidades.

Não usar câmera olhando para a tela como integração definitiva.

A integração verdadeira precisa ser API, evento, banco autorizado ou mecanismo
oficial do fornecedor.

Dados aguardando confirmação:

- nome do sistema;
- empresa;
- API;
- formato da venda;
- identificação da sessão;
- cancelamentos;
- pagamento.

---

# 20. EVENTOS PREVISTOS

ACCESS_GRANTED
PERSON_ENTERED
PERSON_MOVED
PRODUCT_PICKUP
PRODUCT_RETURN
PRODUCT_TRANSFER
CHECKOUT_ITEM_REGISTERED
CHECKOUT_ITEM_REMOVED
PAYMENT_APPROVED
PAYMENT_REJECTED
PERSON_EXITED
CAMERA_OFFLINE
CAMERA_ONLINE
AMBIGUOUS_INTERACTION
IMAGE_INSUFFICIENT
OCCURRENCE_CREATED

Exemplo:

{
  "type": "PRODUCT_PICKUP",
  "storeId": "loja-01",
  "cameraId": "CAM-04",
  "sessionId": "SESSION-001",
  "personId": "PERSON-01",
  "tagId": "TAG-001",
  "productId": "PRODUCT-001",
  "quantity": 1,
  "confidence": 0.94,
  "createdAt": 0
}

---

# 21. OCORRÊNCIAS

Cada ocorrência deve conter:

- ID;
- loja;
- sessão;
- pessoa;
- produto;
- retirado;
- devolvido;
- esperado;
- registrado;
- pago;
- diferença;
- confiança;
- câmeras;
- horários;
- trechos sugeridos;
- motivo;
- status;
- revisão humana;
- classificação final.

Classificações:

- confirmada;
- falso alerta;
- produto devolvido em outro local;
- erro de cadastro;
- erro de quantidade;
- imagem insuficiente;
- outro.

A ocorrência não é uma acusação.

---

# 22. SEGURANÇA E PRIVACIDADE

Regras obrigatórias:

- não bloquear automaticamente a saída;
- não acusar automaticamente;
- manter revisão humana;
- proteger biometria;
- limitar acesso;
- registrar consultas;
- definir retenção;
- processar vídeo preferencialmente no local;
- não publicar imagens de moradores;
- não mandar ocorrências para grupos;
- não expor segredos;
- não criar cobranças automáticas;
- não alterar cadastro do morador sem decisão humana;
- não declarar conformidade jurídica definitiva sem validação profissional.

---

# 23. ESTRUTURA DE ARQUIVOS A SER GERADA

Gerar um ZIP completo:

smart24-fusion-mvp.zip

Estrutura:

smart24-fusion-mvp/
│
├── index.html
├── firebase-config.js
├── firebase-config.example.js
├── README.md
├── PASSO-A-PASSO-THIAGO.md
├── .gitignore
│
├── assets/
│   ├── css/
│   │   └── app.css
│   ├── js/
│   │   ├── app.js
│   │   ├── auth.js
│   │   ├── database.js
│   │   ├── products.js
│   │   ├── labels.js
│   │   ├── cameras.js
│   │   ├── events.js
│   │   ├── simulator.js
│   │   └── utils.js
│   └── images/
│       └── README-FOTOS.txt
│
├── firebase/
│   ├── database.rules.json
│   ├── database.seed.example.json
│   └── FIREBASE-CONFIGURACAO.md
│
├── edge-agent/
│   ├── camera_probe.py
│   ├── event_publisher.py
│   ├── app.py
│   ├── requirements.txt
│   ├── .env.example
│   ├── README-EDGE-AGENT.md
│   └── tests/
│       └── test_event_logic.py
│
└── docs/
    ├── ARQUITETURA.md
    ├── FLUXO-REAL.md
    ├── MODELO-DE-DADOS.md
    ├── SEGURANCA-E-PRIVACIDADE.md
    ├── PLANO-DE-TESTES.md
    └── PROXIMAS-ETAPAS.md

---

# 24. REQUISITOS DO FRONTEND

O frontend deve funcionar sem terminal quando publicado.

Módulos:

- Login;
- Dashboard;
- Produtos;
- Etiquetas;
- Câmeras;
- Eventos;
- Ocorrências;
- Simulador;
- Configurações.

Login:

Firebase Authentication com e-mail e senha.

Dashboard:

- produtos;
- etiquetas;
- câmeras;
- conectores;
- sessões;
- eventos;
- ocorrências.

Produtos:

- nome;
- SKU;
- código de barras;
- categoria;
- status;
- data.

Etiquetas:

- produto;
- quantidade;
- zona;
- serial;
- QR Code;
- impressão;
- RFID EPC aguardando.

Câmeras:

- ID;
- nome;
- loja;
- área;
- protocolo;
- bridge;
- status;
- último contato;
- observação.

Não cadastrar no frontend:

- usuário da câmera;
- senha;
- URL RTSP completa;
- token;
- chave.

Eventos:

- tipo;
- sessão;
- pessoa;
- câmera;
- produto;
- tag;
- quantidade;
- confiança;
- horário.

Ocorrências:

- dados completos da divergência;
- revisão humana;
- classificação.

---

# 25. FIREBASE CONFIG

Arquivo:

firebase-config.js

Conteúdo com campos vazios:

export const firebaseConfig = {
  apiKey: "COLE_AQUI",
  authDomain: "COLE_AQUI",
  databaseURL: "COLE_AQUI",
  projectId: "COLE_AQUI",
  storageBucket: "COLE_AQUI",
  messagingSenderId: "COLE_AQUI",
  appId: "COLE_AQUI"
};

Não inventar dados.

---

# 26. INSTRUÇÕES PARA THIAGO

Criar:

PASSO-A-PASSO-THIAGO.md

Explicar sem presumir terminal:

1. criar projeto Firebase;
2. criar app Web;
3. copiar configuração;
4. ativar Authentication;
5. ativar e-mail/senha;
6. criar Realtime Database bloqueado;
7. publicar regras;
8. criar usuário;
9. copiar UID;
10. criar roles/UID = admin;
11. subir arquivos no GitHub;
12. ativar GitHub Pages;
13. autorizar domínio no Firebase;
14. testar login;
15. cadastrar produto;
16. gerar etiqueta;
17. imprimir;
18. cadastrar câmera;
19. verificar painel.

O agente local pode exigir instalação posterior. Explicar em documento separado.

---

# 27. VALIDAÇÃO OBRIGATÓRIA

Antes da entrega:

- abrir todos os arquivos;
- conferir imports;
- conferir caminhos;
- validar JSON;
- validar regras;
- verificar que o painel não quebra vazio;
- verificar login não configurado;
- verificar produtos;
- verificar etiquetas;
- verificar QR;
- verificar impressão;
- verificar câmeras;
- verificar eventos vazios;
- verificar ocorrências vazias;
- verificar simulador;
- verificar PT-BR;
- verificar responsividade;
- verificar `.gitignore`;
- verificar ausência de segredos;
- verificar Python;
- verificar testes;
- gerar ZIP;
- confirmar que o ZIP abre.

Não dizer “funciona 100%” sem teste.

---

# 28. ORDEM DE IMPLEMENTAÇÃO

FASE 1 — agora:

- estrutura do projeto;
- Firebase;
- autenticação;
- produtos;
- etiquetas;
- câmeras;
- eventos;
- ocorrências;
- simulador;
- documentos;
- ZIP.

FASE 2:

- identificar câmera real;
- confirmar RTSP/ONVIF/SDK;
- instalar agente local;
- receber heartbeat;
- testar vídeo;
- testar reconexão.

FASE 3:

- pessoa única;
- pose;
- mãos;
- zonas;
- uma prateleira;
- eventos de retirada e devolução.

FASE 4:

- múltiplas câmeras;
- múltiplas pessoas;
- handoff;
- caixa;
- divergências.

FASE 5:

- bancada RFID;
- etiquetas;
- leitores;
- checkout;
- saída;
- fusão câmera + RFID.

---

# 29. TAREFA IMEDIATA DA NOVA CONVERSA

Não faça novo levantamento.

Leia:

1. este documento;
2. o link compartilhado da conversa original;
3. os HTMLs anexados;
4. as fotos anexadas;
5. qualquer arquivo já existente.

Depois:

1. confirme a base;
2. relate conflitos encontrados;
3. gere todos os arquivos completos;
4. teste a estrutura;
5. compacte em smart24-fusion-mvp.zip;
6. disponibilize o ZIP para download;
7. disponibilize PASSO-A-PASSO-THIAGO.md separadamente;
8. diga o que funciona;
9. diga o que ainda é simulação;
10. dê apenas o primeiro passo que Thiago deverá executar.

Não responder apenas com código no chat.
Não pedir para Thiago criar os arquivos manualmente.
Não fingir que publicou no GitHub.
Não fingir que conectou a câmera.
Não inventar dados técnicos.