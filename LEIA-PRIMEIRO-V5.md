# SMART24 V5 — Live IA Pilot

Esta versão corrige a ausência de imagem no painel.

## O que funciona no piloto

1. O Android captura, com autorização, a tela onde o vídeo Yoosee está aberto.
2. O Vision Pilot analisa pessoas, pose principal, etiquetas SMART24 e zonas.
3. O aplicativo desenha caixas, trajetórias, etiquetas e zonas sobre o quadro.
4. Um quadro reduzido é publicado no Realtime Database aproximadamente a cada 2,5 segundos.
5. A tela **Ao vivo** do SMART24 exibe a imagem e os contadores.
6. Retiradas e devoluções continuam alimentando eventos e carrinhos.

## O que não deve ser chamado de final

- A fonte ainda é a captura autorizada do aplicativo Yoosee.
- O celular Android do piloto precisa continuar recebendo a câmera.
- O quadro publicado não é vídeo fluido; é monitoramento quase em tempo real.
- O modelo corporal derivado do jogo acompanha prioritariamente uma pessoa. Outros usuários dependem de face/objeto e precisam de validação.
- Reconhecimento facial de identidade do morador ainda não foi implementado.

## Uso responsável do Firebase

O quadro é reduzido para largura máxima de 480 px e qualidade JPEG moderada. Isso foi criado para teste curto. Não mantenha a publicação de quadros durante dias inteiros nesta configuração, pois o tráfego do Realtime Database pode crescer muito.
