# PASSO A PASSO — THIAGO

## SMART24 Fusion — configuração sem terminal

Este arquivo foi escrito para você executar tudo pelo navegador, sem precisar usar terminal, npm ou Node.

> **ESTADO DESTA ENTREGA:** o arquivo operacional `firebase-config.js` já contém exatamente a configuração pública real encontrada no ZIP atual do GitHub. O UID administrativo real que já estava no arquivo de estrutura também foi preservado. Não substitua esses valores por `COLE_AQUI`.

> **SE O PAINEL JÁ MOSTRA “FIREBASE CONECTADO”, NÃO REFAÇA OS PASSOS 1 A 17.** Para esta atualização, siga diretamente os Passos 18 a 22.

---

# PASSO 1 — Criar o projeto no Firebase

1. Abra o site do **Firebase Console** no navegador.
2. Entre com a conta Google que será responsável pelo SMART24 Fusion.
3. Clique em **Criar um projeto**.
4. No nome do projeto, use algo fácil de identificar, por exemplo:
   - `smart24-fusion`
5. O Google Analytics é opcional para esta primeira versão. Pode deixar desativado.
6. Clique em **Criar projeto**.
7. Espere o Firebase mostrar que o projeto está pronto.
8. Pare aqui e confirme que o projeto foi criado.

Não publique nenhuma senha, token, chave privada, RTSP ou conta de serviço.

---

# PASSO 2 — Criar o aplicativo Web

1. Dentro do projeto Firebase, clique no ícone **Web** (`</>`).
2. Nome do app: `SMART24 Fusion Painel`.
3. Não marque Firebase Hosting, porque o painel será publicado pelo GitHub Pages.
4. Clique em **Registrar app**.
5. O Firebase mostrará um bloco chamado `firebaseConfig`.
6. Mantenha essa tela aberta.

---

# PASSO 3 — Preencher o arquivo firebase-config.js

1. Extraia o ZIP final `SMART24-pronto-QR-Yoosee-3D-chaves-reais.zip`.
2. Abra o arquivo `firebase-config.js` em um editor de texto.
3. Você verá:

```js
export const firebaseConfig = {
  apiKey: "AIzaSyDBFXRrgb7KwNVZArx_Du4DSLEOrKN5Vbw",
  authDomain: "smart24-fusion.firebaseapp.com",
  databaseURL: "https://smart24-fusion-default-rtdb.firebaseio.com/",
  projectId: "smart24-fusion",
  storageBucket: "smart24-fusion.firebasestorage.app",
  messagingSenderId: "894689077131",
  appId: "1:894689077131:web:4524b6f3ae199b2b00718e"
};
```

4. Copie os valores exibidos pelo Firebase para os campos correspondentes.
5. O campo `databaseURL` poderá ser preenchido depois que o Realtime Database for criado.
6. Não altere os nomes dos campos.
7. Salve o arquivo.

A configuração do aplicativo Web não é a mesma coisa que uma conta de serviço. Nunca envie `service-account.json` ao GitHub.

---

# PASSO 4 — Ativar login por e-mail e senha

1. No menu do Firebase, abra **Authentication**.
2. Clique em **Começar**.
3. Abra a aba **Método de login**.
4. Clique em **E-mail/senha**.
5. Ative a primeira opção de e-mail e senha.
6. Não precisa ativar link por e-mail nesta fase.
7. Clique em **Salvar**.

---

# PASSO 5 — Criar o primeiro usuário

1. Ainda em **Authentication**, abra a aba **Usuários**.
2. Clique em **Adicionar usuário**.
3. Informe o e-mail do administrador.
4. Crie uma senha segura com pelo menos seis caracteres.
5. Clique em **Adicionar usuário**.
6. Copie o **UID** mostrado na lista de usuários.
7. Guarde esse UID. Ele será usado no Passo 8.

Não use senha de câmera como senha do painel.

---

# PASSO 6 — Criar o Realtime Database bloqueado

1. No menu do Firebase, abra **Realtime Database**.
2. Clique em **Criar banco de dados**.
3. Escolha a região mais adequada apresentada pelo Firebase.
4. Selecione **Iniciar no modo bloqueado**.
5. Clique em **Ativar**.
6. Copie a URL exibida do banco. Ela costuma terminar em `firebasedatabase.app` ou `firebaseio.com`.
7. Volte ao arquivo `firebase-config.js`.
8. Cole essa URL no campo `databaseURL`.
9. Salve o arquivo.

---

# PASSO 7 — Publicar as regras seguras

1. Abra o arquivo `firebase/database.rules.json` do pacote.
2. Selecione e copie todo o conteúdo.
3. No Firebase, abra **Realtime Database > Regras**.
4. Apague as regras que estiverem na tela.
5. Cole o conteúdo completo do arquivo.
6. Clique em **Publicar**.

Essas regras começam bloqueadas e exigem usuário autenticado com função autorizada.

---

# PASSO 8 — Dar função de administrador ao primeiro usuário

1. No Realtime Database, abra a aba **Dados**.
2. Clique no botão de adicionar um filho na raiz do banco.
3. Nome da chave: `roles`.
4. Dentro de `roles`, adicione outra chave.
5. O nome dessa nova chave deve ser o **UID exato** copiado no Passo 5.
6. O valor deve ser:

```text
admin
```

O resultado será semelhante a:

```text
roles
  UID_REAL_DO_USUARIO: admin
```

Não escreva o e-mail no lugar do UID.

---

# PASSO 9 — Criar a estrutura inicial do banco

Na raiz do Realtime Database, crie os seguintes caminhos vazios conforme forem usados pelo sistema:

- `stores`
- `products`
- `tags`
- `cameras`
- `cameraBridges`
- `integrations`
- `sessions`
- `events`
- `occurrences`
- `auditLogs`

O sistema cria automaticamente os registros dentro desses caminhos. O arquivo `firebase/database.seed.example.json` preserva o UID administrativo que já estava no ZIP real do GitHub. Não importe esse arquivo por cima de um banco que já possui dados; ele é somente uma referência de estrutura.

---

# PASSO 10 — Criar o repositório no GitHub

1. Entre no GitHub.
2. Clique em **New repository**.
3. Nome sugerido: `smart24-fusion-mvp`.
4. Escolha **Private** durante a configuração inicial, se preferir revisar antes de publicar.
5. Não marque criação automática de README, porque o pacote já possui um.
6. Clique em **Create repository**.

---

# PASSO 11 — Enviar os arquivos sem terminal

1. Dentro do repositório vazio, clique em **uploading an existing file** ou **Add file > Upload files**.
2. Abra a pasta extraída `SMART24-main`.
3. Selecione todos os arquivos e pastas internos.
4. Arraste para a área de upload do GitHub.
5. Confira que as pastas `assets`, `firebase`, `edge-agent` e `docs` foram incluídas.
6. No campo de mensagem, escreva: `Versão inicial SMART24 Fusion MVP`.
7. Clique em **Commit changes**.

Não envie `.env` nem `service-account.json`. O `.gitignore` já ajuda a bloquear esses arquivos, mas você também deve conferir visualmente.

---

# PASSO 12 — Ativar o GitHub Pages

1. No repositório, abra **Settings**.
2. No menu lateral, clique em **Pages**.
3. Em **Build and deployment**, escolha **Deploy from a branch**.
4. Em Branch, escolha `main`.
5. Em pasta, escolha `/ (root)`.
6. Clique em **Save**.
7. Aguarde o GitHub mostrar a URL publicada.

---

# PASSO 13 — Autorizar o domínio no Firebase

1. Copie somente o domínio da URL do GitHub Pages.
2. No Firebase, abra **Authentication > Settings**.
3. Procure **Domínios autorizados**.
4. Clique em **Adicionar domínio**.
5. Cole o domínio do GitHub Pages sem `https://` e sem o caminho do repositório.
6. Salve.

Exemplo de domínio:

```text
seuusuario.github.io
```

---

# PASSO 14 — Testar o login real

1. Abra a URL do GitHub Pages.
2. O selo superior deve mostrar **Firebase conectado**.
3. Entre com o e-mail e a senha criados no Passo 5.
4. O rodapé lateral deve mostrar a função **Administrador**.
5. Se aparecer “sem função autorizada”, confira o UID em `roles`.

---

# PASSO 15 — Cadastrar o primeiro produto

1. Abra **Produtos**.
2. Preencha:
   - nome;
   - SKU único;
   - código de barras, se existir;
   - categoria;
   - status ativo.
3. Clique em **Salvar produto**.
4. Confirme que o produto aparece na tabela.

---

# PASSO 16 — Gerar a primeira etiqueta

1. Abra **Etiquetas**.
2. Selecione o produto.
3. Informe a quantidade.
4. Confirme a loja, por exemplo `loja-01`.
5. Informe uma zona provisória, sem inventar medida ou posição definitiva.
6. Clique em **Gerar e salvar**.
7. Confirme que cada unidade recebeu um serial único.

O QR Code é para identificação quando visível. Ele não rastreia produto escondido.

---

# PASSO 17 — Imprimir etiquetas

1. Marque as etiquetas desejadas.
2. Clique em **Imprimir visíveis**.
3. Na tela de impressão, escolha a impressora e o tamanho adequados.
4. Antes de imprimir em quantidade, faça um teste físico com uma folha.

---

# PASSO 18 — Atualizar os arquivos no GitHub

1. Faça uma cópia de segurança do repositório atual.
2. Extraia o ZIP atualizado.
3. No GitHub, envie todos os arquivos e pastas internos da pasta `SMART24-main`.
4. Permita a substituição dos arquivos com o mesmo nome.
5. Confirme principalmente estes arquivos novos:
   - `simulator-3d.html`;
   - `assets/js/camera-qr.js`;
   - `assets/js/yoosee.js`;
   - `assets/js/simulator-3d.js`.
6. A configuração Firebase já existente foi preservada no pacote gerado a partir do repositório publicado.
7. Aguarde o GitHub Pages concluir a publicação.
8. Atualize a página com `Ctrl + F5` no computador ou limpe o cache do site no celular.

---

# PASSO 19 — Republicar as regras do Firebase

Esta atualização cria o caminho seguro `integrations/yoosee` e bloqueia explicitamente o salvamento de senha, token, InviteCode, link de compartilhamento e QR bruto.

1. Abra `firebase/database.rules.json` do ZIP atualizado.
2. Copie todo o conteúdo.
3. No Firebase, abra **Realtime Database > Regras**.
4. Substitua as regras antigas pelas novas.
5. Clique em **Publicar**.

Sem esse passo, o e-mail operacional Yoosee pode não ser salvo.

---

# PASSO 20 — Criar a conta operacional Yoosee

O SMART24 não cria uma conta externa sozinho e não armazena a senha.

1. Crie um e-mail dedicado exclusivamente às câmeras, por exemplo:

```text
cameras.smart24@seudominio.com
```

2. No aplicativo Yoosee, toque em **Registro rápido**.
3. Cadastre a nova conta usando esse e-mail.
4. Crie uma senha forte e guarde-a fora do GitHub e fora do Firebase.
5. Entre novamente na conta principal do proprietário da câmera.
6. Compartilhe a câmera com a nova conta operacional.
7. Entre na conta operacional e confirme que a câmera aparece no aplicativo Yoosee.
8. No SMART24, abra **Câmeras > Yoosee do SMART24**.
9. Informe apenas o e-mail e marque a etapa atual.
10. Clique em **Salvar somente o e-mail**.

Nunca digite a senha Yoosee no SMART24.

---

# PASSO 21 — Cadastrar uma câmera pelo QR Code

1. Abra **Câmeras**.
2. Clique em **Ler QR Code**.
3. Escolha uma opção:
   - **Usar câmera deste aparelho**, para apontar ao QR físico;
   - **Escolher foto do QR**, para selecionar uma captura de tela ou fotografia.
4. O navegador processa a imagem localmente.
5. Para QR/link Yoosee, o sistema aproveita apenas:
   - plataforma `YOOSEE`;
   - ID do dispositivo;
   - informação de que veio de QR.
6. O sistema descarta:
   - `InviteCode`;
   - token;
   - nome da conta que compartilhou;
   - link completo;
   - QR bruto.
7. Confirme ou preencha:
   - ID público;
   - nome;
   - loja;
   - área física onde a câmera será instalada;
   - protocolo como **A confirmar**;
   - bridge/conector.
8. Clique em **Salvar câmera**.

Importante: ler o QR no SMART24 cadastra os metadados. Isso não aceita o convite dentro do Yoosee e não conecta o vídeo. O convite deve ser aceito no aplicativo Yoosee pela conta operacional.

---

# PASSO 22 — Abrir a loja em 3D

1. Abra **Simulador**.
2. O modo inicial será **Loja 3D completa**.
3. Use o mouse ou toque para movimentar a visualização.
4. Clique em **Abrir 3D em tela inteira** quando desejar uma área maior.
5. Para gerar eventos no Firebase, mude para **Simulação operacional**.
6. A loja 3D é demonstrativa. Medidas e posições de câmeras ainda precisam ser conferidas no local.

---

# VERIFICAÇÃO FINAL

Confirme:

- Dashboard continua mostrando **Firebase conectado**.
- Login e funções continuam funcionando.
- Produtos e etiquetas anteriores continuam aparecendo.
- O botão **Ler QR Code** abre o leitor.
- Uma foto com QR Yoosee preenche plataforma e ID do dispositivo.
- InviteCode e link completo não aparecem no Firebase.
- O e-mail Yoosee é salvo sem campo de senha.
- A câmera aparece como `UNCONFIGURED` até existir agente local real.
- O modo **Loja 3D completa** abre dentro do sistema.
- O modo **Simulação operacional** ainda executa os oito cenários.
- Nenhuma ocorrência é tratada como acusação automática.

---

# SOBRE O AGENTE LOCAL

A pasta `edge-agent` continua sendo a base técnica para a próxima etapa. Ela exige instalação no computador da loja e confirmação real de RTSP, ONVIF ou mecanismo autorizado.

A conta Yoosee e o leitor QR não substituem essa integração. Eles organizam o cadastro e permitem que uma conta operacional visualize a câmera pelos mecanismos oficiais do Yoosee.

Não tente configurar o agente antes de confirmar:

- compatibilidade real da câmera com RTSP, ONVIF, NVR, CMS ou SDK;
- computador que ficará ligado na loja;
- acesso autorizado ao Firebase;
- política de retenção e privacidade.
