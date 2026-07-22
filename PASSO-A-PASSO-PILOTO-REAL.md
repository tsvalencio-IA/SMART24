# PASSO A PASSO — PILOTO REAL COM A CÂMERA YOOSEE

## Parte A — publicar o sistema

1. Abra o repositório `tsvalencio-IA/SMART24` no GitHub.
2. Envie todos os arquivos e pastas deste pacote para a raiz do repositório.
3. Aceite substituir os arquivos com o mesmo nome.
4. Não apague `firebase-config.js`: ele já contém a configuração pública real do projeto.
5. Aguarde o GitHub Pages atualizar.

## Parte B — publicar as regras

1. Abra o Firebase.
2. Entre no projeto `smart24-fusion`.
3. Abra **Realtime Database → Regras**.
4. Copie todo o conteúdo de `firebase/database.rules.json`.
5. Substitua as regras atuais e toque em **Publicar**.

As regras novas liberam, somente para usuários autenticados e autorizados:

- `zones`;
- `carts`;
- `visionPilots`;
- `tagStates`;
- heartbeat do `cameraBridges` para `admin` e `operator`.

## Parte C — gerar o APK sem computador e sem terminal

1. No GitHub, abra a aba **Actions**.
2. Abra **Build SMART24 Vision APK**.
3. Toque em **Run workflow**.
4. Aguarde a execução ficar verde.
5. Abra a execução concluída.
6. Em **Artifacts**, baixe `SMART24-Vision-Pilot-APK`.
7. Extraia o arquivo baixado.
8. Instale `app-debug.apk` no Android.

## Parte D — preparar três produtos

1. No SMART24, cadastre um produto de teste, por exemplo `Spaten Long Neck 355 ml`.
2. Informe SKU, tipo/embalagem e preço.
3. Gere inicialmente **3 etiquetas**, não 24.
4. Imprima em tamanho suficiente para o QR ocupar boa parte da etiqueta.
5. Cole uma etiqueta em cada unidade, voltada para a câmera.
6. Abra **Reposição**.
7. Bipe uma etiqueta, informe quantidade `3`, loja `loja-01`, câmera `CAM-01` e zona `GELADEIRA-01-P01`.
8. Salve o lote na prateleira.

## Parte E — iniciar a câmera real

1. No Android do piloto, confirme que o Yoosee abre normalmente a câmera da sua casa.
2. Abra `SMART24 Vision Pilot`.
3. Entre com o mesmo usuário e senha do Firebase usados no painel.
4. Toque em **Autorizar captura e abrir Yoosee**.
5. Autorize a captura de tela do Android.
6. No Yoosee, abra a câmera e deixe o vídeo em tela cheia.
7. Espere aproximadamente 10 segundos.

## Parte F — calibrar a prateleira

1. Volte ao SMART24 Vision Pilot.
2. Toque em **Calibrar prateleira na imagem**.
3. Na imagem capturada, toque no canto superior esquerdo da área dos produtos.
4. Toque no canto inferior direito.
5. Informe `GELADEIRA-01-P01`.
6. Salve.
7. Volte ao Yoosee e deixe o vídeo aberto novamente.

## Parte G — teste de retirada

1. Deixe as três etiquetas visíveis por alguns segundos.
2. Retire uma unidade da zona.
3. Aguarde de 3 a 8 segundos.
4. Em outro celular, abra o SMART24.
5. Confira **Eventos** e **Carrinhos**.
6. Devolva a mesma unidade para dentro da zona.
7. Confira se apareceu `Produto devolvido` e se o item saiu do carrinho.

## Resultado honesto esperado

- heartbeat real do piloto;
- quantidade de etiquetas detectadas;
- pessoas/objetos acompanhados com IDs temporários;
- evento de retirada quando uma etiqueta sai da zona e existe pessoa próxima;
- evento de devolução quando a etiqueta reaparece dentro da zona;
- evento ambíguo quando não existe associação visual suficiente.

O resultado depende de resolução, compressão do vídeo Yoosee, tamanho da etiqueta, reflexo, distância, iluminação e se o Android permite capturar a superfície de vídeo do Yoosee. Se a imagem vier preta, o painel mostrará `NO_IMAGE` em vez de fingir que está funcionando.


## CORREÇÃO V4.2 — MESMO CELULAR

Não tente apontar a câmera para um QR exibido no próprio aparelho. O APK possui as opções **Usar conta Yoosee que já tem a câmera**, **Colar link copiado** e **Escolher print ou imagem do QR**. Se a câmera já aparece no Yoosee, o QR não é necessário.
