# VALIDAÇÃO — SMART24 PILOTO REAL V4

## Validado no pacote

- estrutura completa do site preservada;
- configuração Firebase real preservada;
- menu mobile agora contém Câmeras e Carrinhos, com menu Mais;
- cadastro de preço e tipo/embalagem do produto;
- reposição em lote por uma etiqueta sequencial;
- carrinhos em tempo real no painel;
- regras JSON válidas;
- HTML sem IDs duplicados;
- imports JavaScript existentes;
- sintaxe de todos os módulos JavaScript aprovada;
- projeto Android completo;
- manifestos e layouts XML válidos;
- workflow GitHub Actions incluído;
- senha Firebase não é persistida pelo app;
- senha, token e InviteCode Yoosee não são gravados no Firebase.

## Não validado neste ambiente

- compilação final do APK pelo Android SDK;
- captura visual da superfície específica da versão instalada do Yoosee;
- taxa real de leitura das etiquetas na câmera residencial;
- rastreamento com três pessoas reais;
- funcionamento 24 horas;
- ingestão direta em nuvem de várias câmeras.

A primeira execução do GitHub Actions é a validação de compilação. O primeiro teste físico determinará se o Yoosee permite MediaProjection do vídeo ou retorna tela preta.
