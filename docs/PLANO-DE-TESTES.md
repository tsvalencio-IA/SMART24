# Plano de testes

## Frontend sem Firebase

- Abrir `index.html` por servidor HTTP ou GitHub Pages.
- Confirmar mensagem de Firebase não configurado.
- Abrir demonstração local.
- Confirmar Dashboard sem quebra.
- Cadastrar e editar produto.
- Gerar várias etiquetas.
- Confirmar serial único.
- Confirmar QR Code e fallback visual.
- Selecionar etiquetas e abrir impressão.
- Cadastrar câmera sem segredo.
- Bloquear texto contendo `rtsp://`, `password=` ou `token=`.
- Executar os oito cenários do simulador.
- Confirmar que o resultado só aparece ao final.
- Confirmar comparação por SKU.
- Confirmar três pessoas separadas no cenário correspondente.

## Frontend com Firebase

- Confirmar login obrigatório.
- Confirmar usuário sem `roles/UID` rejeitado.
- Confirmar administrador.
- Confirmar operador sem permissão de câmera.
- Confirmar auditor sem permissão de produto.
- Confirmar atualização em tempo real.
- Confirmar regras sem leitura pública.

## Agente local

- Executar testes unitários.
- Testar `DRY_RUN=true` sem Firebase.
- Testar URL RTSP inválida sem expor senha no log.
- Testar estados de reconexão.
- Testar heartbeat.
- Confirmar que vídeo não é enviado ao Firebase.

## Testes de campo pendentes

- cobertura real das câmeras;
- reflexos das geladeiras;
- oclusão por corpo e mão;
- sincronização de relógio;
- API real do checkout;
- evento real do acesso facial;
- bancada RFID.
