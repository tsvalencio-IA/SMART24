# PASSO A PASSO — COLOCAR A CÂMERA YOOSEE ONLINE DE VERDADE

## O que já está funcionando

- login e Firebase;
- cadastro de produtos e etiquetas;
- leitura do QR da câmera;
- cadastro da câmera Yoosee;
- loja 3D;
- painel de heartbeat e eventos;
- menu mobile completo, incluindo Câmeras e Configurações.

## O que o QR faz

O QR reconhece o fabricante e o ID do equipamento e preenche o cadastro. Ele não contém, de forma segura e suficiente, o fluxo de vídeo local. Por isso o vídeo é conectado pelo computador da loja.

## Etapa 1 — habilitar a conexão NVR no Yoosee

1. Abra o Yoosee.
2. Na câmera, toque nos três pontos.
3. Abra **Configurações**.
4. Abra **Conexão NVR**.
5. Crie uma senha RTSP/NVR e guarde-a.
6. Não publique essa senha e não a coloque no painel web.

## Etapa 2 — baixar a conta de serviço do Firebase

1. Abra o Console Firebase do projeto `smart24-fusion`.
2. Clique na engrenagem → **Configurações do projeto**.
3. Abra **Contas de serviço**.
4. Clique em **Gerar nova chave privada**.
5. Guarde o JSON no computador da loja.
6. Nunca envie esse arquivo ao GitHub ou WhatsApp.

## Etapa 3 — instalar o conector no computador da loja

1. Extraia este ZIP em uma pasta fixa, por exemplo `C:\SMART24`.
2. Abra `edge-agent`.
3. Dê dois cliques em `INSTALAR-CONNECTOR-SMART24.bat`.
4. Caso o Windows peça Python, instale o Python 3.11 ou superior e marque **Add Python to PATH**.
5. No assistente, clique em **Procurar câmeras na rede**.
6. Informe a senha criada em Conexão NVR.
7. Clique em **Testar conexão de vídeo**.
8. Só prossiga quando aparecer **frame recebido**.
9. Selecione a conta de serviço JSON.
10. Clique em **Salvar e ativar conector**.

## Resultado esperado

Em até aproximadamente 30 segundos, no Dashboard:

- Câmeras: `1`;
- detalhe: `1 online`;
- Conectores: `1`;
- Eventos recentes: `Câmera online`.

## Limite real desta versão

Esta versão conecta e testa o vídeo, mantém heartbeat e informa ONLINE/OFFLINE. Ela ainda não afirma que reconhece produto retirado, devolvido ou não pago. Para isso, a próxima calibração precisa mapear fisicamente as zonas e validar ângulos, iluminação e o fluxo do caixa.
