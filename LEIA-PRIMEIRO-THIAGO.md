# LEIA PRIMEIRO — SMART24 PILOTO REAL V4

Esta versão usa o sistema real atual do GitHub e acrescenta um piloto Android para analisar a imagem verdadeira da sua câmera Yoosee.

## O que você fará

1. Subirá todo o conteúdo desta pasta no repositório `tsvalencio-IA/SMART24`.
2. Publicará as novas regras do Realtime Database.
3. O GitHub Actions gerará o APK `SMART24 Vision Pilot`.
4. Você instalará o APK em um Android que também tenha o Yoosee.
5. O APK pedirá autorização para capturar a tela.
6. Você abrirá o vídeo ao vivo no Yoosee.
7. O piloto processará os quadros reais, reconhecerá etiquetas SMART24 visíveis e publicará eventos/carrinhos no Firebase.

## Verdade técnica

- É um teste real com a sua câmera remota.
- O vídeo continua sendo transportado pelo aplicativo oficial Yoosee.
- O SMART24 não recebe nem salva a senha Yoosee.
- O Android que iniciou a captura precisa permanecer ligado e com o vídeo sendo exibido durante o piloto.
- A implantação final das seis lojas precisará de ingestão contínua em nuvem ou câmeras com fluxo/API compatível.


## CORREÇÃO V4.2 — MESMO CELULAR

Não tente apontar a câmera para um QR exibido no próprio aparelho. O APK possui as opções **Usar conta Yoosee que já tem a câmera**, **Colar link copiado** e **Escolher print ou imagem do QR**. Se a câmera já aparece no Yoosee, o QR não é necessário.
