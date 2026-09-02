# SMART24 — Protótipo Limpo (Demonstração Assistida)

## Objetivo desta versão

Esta versão foi reconstruída a partir do ZIP original enviado pelo proprietário da thIAguinho.

O objetivo é provar um único fluxo:

**câmera no Yoosee → imagem analisada no Android → pessoa/objeto genérico → ação confirmada pelo operador → Firebase Realtime Database → painel GitHub Pages.**

Esta versão NÃO promete reconhecer automaticamente o SKU de qualquer produto e NÃO acusa furto.

## Como funciona

1. O celular Android abre o Yoosee e mostra a câmera ao vivo.
2. O SMART24 recebe, com autorização do Android, a imagem da tela via MediaProjection.
3. O SMART24 roda localmente:
   - detecção de pose/pessoa;
   - pulsos;
   - detecção/rastreamento de objetos genéricos.
4. Um pequeno painel flutuante aparece sobre o Yoosee.
5. O operador usa:
   - **PEGOU** — confirma que houve retirada;
   - **DEVOLVEU** — confirma devolução;
   - **SUSPEITA** — envia uma ocorrência para revisão;
   - **FINALIZAR** — encerra o acompanhamento.
6. O painel web mostra câmera, pessoas, objetos e eventos em tempo real.

## Verdade técnica

A câmera ainda NÃO é acessada diretamente por RTSP nesta versão.

A fonte do vídeo é a imagem do Yoosee exibida no mesmo Android. Isso foi escolhido para a demonstração porque a câmera já funciona no aplicativo nativo e ainda não temos credenciais/URL RTSP confirmadas.

A versão de servidor poderá substituir esta origem por RTSP/ONVIF sem depender da tela do celular.

## O que foi removido do fluxo principal

Para diminuir pontos de falha, o teste principal não depende mais de:

- convite/QR do Yoosee;
- QR colado em produto;
- calibração de prateleira;
- carrinho automático;
- reconhecimento de SKU;
- bloqueio de porta.

Os arquivos antigos continuam no repositório apenas para não destruir funcionalidades anteriores, mas o protótipo limpo não precisa deles.

## Passo a passo do teste

### Antes de abrir o SMART24

No Android que fará o teste:

1. Instale e faça login no Yoosee.
2. Confirme que a câmera abre e mostra vídeo ao vivo.
3. Feche o Yoosee ou volte à tela inicial.

### No SMART24

1. Abra **SMART24 Vision Pilot**.
2. Toque em **Abrir Yoosee e testar câmera**.
3. Confirme que a câmera funciona.
4. Volte ao SMART24.
5. Preencha:
   - e-mail Firebase;
   - senha Firebase;
   - loja (ex.: `loja-01`);
   - câmera (ex.: `CAM-01`);
   - conector (ex.: `pilot-android-demo-01`).
6. Toque **1. Entrar no Firebase**.
7. Aguarde a mensagem de que entrou como `admin` ou `operator`.
8. Toque **2. Autorizar painel flutuante**.
9. No Android, permita **Exibir sobre outros apps** para o SMART24.
10. Volte ao SMART24.
11. Toque **3. Autorizar vídeo e iniciar teste**.
12. Na autorização de captura do Android, autorize a tela inteira.
13. O Yoosee será aberto.
14. Abra a câmera ao vivo em tela cheia.
15. Aguarde o painel flutuante **SMART24 • TESTE**.

### Primeiro teste

1. Uma pessoa entra no enquadramento.
2. Aguarde até o painel indicar pelo menos `Pessoas 1`.
3. A pessoa pega um produto e mantém na mão.
4. Somente depois disso, toque **PEGOU**.
5. O SMART24 tentará associar:
   - pessoa;
   - pulso;
   - objeto genérico próximo.
6. No painel GitHub Pages, abra **Ao vivo** e confirme a imagem/anotações.
7. Abra **Eventos** e procure `Retirada confirmada pelo operador`.

### Teste de devolução

1. A pessoa devolve o produto.
2. Toque **DEVOLVEU**.
3. Abra **Eventos** no painel e confirme a devolução.

### Teste de ocorrência

1. Marque **PEGOU**.
2. Faça uma situação que você queira demonstrar como suspeita.
3. Toque **SUSPEITA**.
4. Abra **Ocorrências** no painel.
5. Deve existir uma ocorrência `Pendente`, com imagem quando disponível.

## Como interpretar os modos

### OBJECT_NEAR_WRIST

O sistema encontrou um objeto genérico próximo de um dos pulsos da pessoa.

Isso é o melhor resultado desta demonstração, mas ainda NÃO significa que o sistema reconheceu o SKU.

### OBJECT_INSIDE_PERSON_REGION

O sistema encontrou um objeto dentro da região da pessoa, mas a associação com a mão foi menos forte.

### WRIST_OR_PERSON_ONLY

O sistema viu a pessoa/pulso, mas não isolou um objeto genérico com segurança.

O evento continua válido como ação confirmada pelo operador.

## O que devemos observar na demonstração

O protótipo é aprovado para continuar se conseguirmos provar:

- vídeo real aparece no painel;
- pessoa é detectada;
- em parte dos testes um objeto genérico é isolado;
- PEGOU gera evento;
- DEVOLVEU gera evento;
- SUSPEITA gera ocorrência;
- Firebase e GitHub Pages atualizam em tempo real.

Não precisamos obter 100% de reconhecimento de objetos nesta fase.

## Próxima versão

Depois desta prova:

1. substituir captura de tela por RTSP/ONVIF no computador local;
2. múltiplas câmeras;
3. zonas de prateleira;
4. histórico de objeto;
5. associação entre pessoas e mãos;
6. comparação com checkout;
7. alertas automáticos baseados em regras e confiança.

thIAguinho Soluções — tecnologia sob medida.
