# PASSO A PASSO — THIAGO

## SMART24 Fusion — configuração sem terminal

Este arquivo foi escrito para você executar tudo pelo navegador, sem precisar usar terminal, npm ou Node.

> **FAÇA SOMENTE O PASSO 1 AGORA.** Depois de concluí-lo, confirme que o projeto Firebase foi criado antes de avançar.

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

1. Extraia o ZIP `smart24-fusion-mvp.zip`.
2. Abra o arquivo `firebase-config.js` em um editor de texto.
3. Você verá:

```js
export const firebaseConfig = {
  apiKey: "COLE_AQUI",
  authDomain: "COLE_AQUI",
  databaseURL: "COLE_AQUI",
  projectId: "COLE_AQUI",
  storageBucket: "COLE_AQUI",
  messagingSenderId: "COLE_AQUI",
  appId: "COLE_AQUI"
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
- `sessions`
- `events`
- `occurrences`
- `auditLogs`

O sistema cria automaticamente os registros dentro desses caminhos. O arquivo `firebase/database.seed.example.json` mostra a estrutura, mas não deve ser importado sem substituir o UID de exemplo.

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
2. Abra a pasta extraída `smart24-fusion-mvp`.
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

# PASSO 18 — Cadastrar metadados de câmera

1. Abra **Câmeras**.
2. Cadastre somente:
   - ID público;
   - nome;
   - loja;
   - área;
   - protocolo conhecido ou “A confirmar”;
   - nome do bridge/conector;
   - observação sem segredo.
3. Não coloque usuário, senha, token nem URL RTSP completa.

As posições sugeridas no simulador ainda não são definitivas.

---

# PASSO 19 — Verificar o painel

Confirme:

- Dashboard abre sem erro.
- Produto aparece.
- Etiqueta aparece com QR Code.
- Câmera aparece como `UNCONFIGURED` até o agente local existir.
- Eventos e ocorrências podem estar vazios.
- Simulador executa os oito cenários progressivamente.
- Nenhuma ocorrência é tratada como acusação automática.

---

# SOBRE O AGENTE LOCAL

A pasta `edge-agent` é uma base técnica para a próxima etapa. Ela exige instalação no computador da loja e dados reais da câmera, como marca, modelo, RTSP/ONVIF e credenciais privadas.

Não tente configurar o agente antes de confirmar:

- marca e modelo da câmera;
- funcionamento do RTSP ou ONVIF;
- computador que ficará ligado na loja;
- acesso autorizado ao Firebase;
- política de retenção e privacidade.

Leia `edge-agent/README-EDGE-AGENT.md` somente quando a Fase 2 começar.
