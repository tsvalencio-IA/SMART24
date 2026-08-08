# SMART24 V2.3.0 — pessoa, mão e objeto

Este pacote contém o portal SMART24 completo, incluindo a loja 3D e o cadastro
de produtos, o aplicativo Android, as regras do Firebase e o fluxo de build do
GitHub Actions.

## O que esta versão faz

- captura a área do vídeo ao vivo exibida pelo Yoosee;
- detecta pessoas, pontos das mãos e objetos genéricos;
- mantém identificadores temporários para pessoas e objetos;
- associa geometricamente um objeto à mão e mostra `NA MÃO` quando a evidência
  permanece estável por vários quadros;
- mantém a decisão da demonstração sob controle do operador, com os botões
  `PEGOU`, `DEVOLVEU`, `ESCONDEU`, `ALERTA` e `FINALIZAR`;
- envia o evento e a evidência ao Firebase Realtime Database;
- salva uma amostra recortada do objeto em `visionSamples` quando `PEGOU` é
  confirmado com associação estável.

## Limite honesto desta etapa

Esta versão ainda não reconhece automaticamente o SKU exato. Ela encontra um
objeto genérico próximo à mão. O reconhecimento fiel do produto será a próxima
etapa, treinada e validada com imagens reais da câmera, iluminação, prateleira e
embalagens da loja.

## Instalação rápida

1. Publique `firebase/database.rules.json` no Firebase Realtime Database.
2. Envie o conteúdo completo deste pacote ao repositório, preservando a pasta
   oculta `.github`.
3. Aguarde o workflow **Build SMART24 APK Completo**. O artefato gerado se chama
   `SMART24-APK-COMPLETO-V2.3.0`.
4. Ou instale diretamente o APK incluído na pasta `APK-PRONTO`.
5. No celular, abra primeiro a câmera no Yoosee e confirme o vídeo ao vivo.
6. No SMART24, entre no Firebase e autorize a captura da tela.
7. Em tela dividida, deixe desmarcada a opção de controles flutuantes.
8. Delimite apenas o retângulo do vídeo da câmera; menus do Yoosee e a própria
   tela do SMART24 não devem entrar nessa área.
9. Calibre a zona da prateleira dentro do recorte da câmera.
10. Faça uma retirada real e confirme `PEGOU` no módulo Android.

## Se aparecer imagem preta ou nenhuma detecção

- confirme que o Android autorizou a captura da tela;
- confirme que o Yoosee continua exibindo o vídeo ao vivo;
- refaça a delimitação do vídeo;
- teste o Yoosee sem tela cheia e em tela dividida;
- alguns aparelhos ou versões do Yoosee podem bloquear captura por segurança.
  Nesse caso, a solução robusta será receber o vídeo diretamente por RTSP no
  servidor.

O plano detalhado está em `android-vision-pilot/README-THIAGO.md` e
`android-vision-pilot/PROTOTIPO-DEMO-ASSISTIDA.md`.
