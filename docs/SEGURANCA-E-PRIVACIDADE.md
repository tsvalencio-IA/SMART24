# Segurança e privacidade

## Regras obrigatórias

- Não bloquear automaticamente a saída.
- Não acusar automaticamente.
- Manter revisão humana.
- Proteger biometria.
- Limitar acesso por função.
- Registrar consultas e alterações relevantes.
- Definir retenção de eventos e imagens.
- Processar vídeo preferencialmente no local.
- Não publicar imagens de moradores.
- Não enviar ocorrências para grupos.
- Não expor segredos.
- Não criar cobranças automáticas.
- Não alterar cadastro de morador sem decisão humana.
- Não declarar conformidade jurídica definitiva sem validação profissional.

## Credenciais

Ficam fora do GitHub:

- `.env`;
- `service-account.json`;
- senha da câmera;
- URL RTSP com credenciais;
- token;
- chave privada.

## Biometria

O MVP não implementa novo reconhecimento facial. A integração futura deverá receber apenas um evento autorizado do sistema de acesso já existente ou outra integração formalmente autorizada.


## QR Code de câmera

- processamento da imagem no navegador;
- nenhum upload da fotografia para o Firebase;
- descarte do link completo Yoosee;
- descarte de InviteCode e token;
- não salvar QR bruto;
- confirmação humana antes de cadastrar;
- ID do dispositivo somente em área autenticada.

## Conta Yoosee

O SMART24 armazena somente o e-mail operacional. Não existe campo de senha. A criação da conta e o compartilhamento da câmera são realizados manualmente no aplicativo oficial.
