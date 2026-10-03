# SMART24 Vigilante Celular — continuidade

Base do repositório: `263ea1b5ec2b8555e87b40b30d736c7fcfc90432`.
Pacote aplicado: `SMART24-Vigilante-Celular-v3-pronto (1).zip`.
SHA-256 do pacote: `c9516282a1e4df3530a6021ec66bf6c343bfdbd77438f77e22e9876d1009df64`.

## Escopo obrigatório — dono no 4G, seis mercadinhos

Em 03/10/2026, Thiago reforçou que o dono deve acompanhar as seis lojas pelo celular usando 4G, sem precisar estar no Wi-Fi das câmeras. A orientação de mesma rede serve ao teste local; não é a arquitetura final. Consulte [VIGILANTE-REMOTO-MULTILOJA.md](VIGILANTE-REMOTO-MULTILOJA.md) para o estado do código, a conexão remota necessária e os critérios de validação.

O APK 3.2 não provisiona essa ligação remota e interrompe a IA quando sai da tela. O objetivo exige ligação autenticada de cada loja, processamento contínuo independente do telefone do dono, vídeo remoto autorizado e gestão por loja/câmera. Ainda é necessário identificar os equipamentos ligados nas lojas e configurar o ponto remoto. Não anunciar as seis lojas no 4G como prontas nem substituir essa etapa por outro APK de teste local.

## Aplicação atual

- Android `br.com.thiaguinhosolucoes.smart24vision`, versão 3.2.0, código 9.
- Tela inicial: `MobileVigilanteActivity`; vídeo RTSP direto com LibVLC 3.7.0.
- Primeira câmera informada por Thiago: `192.168.15.5`; senha e usuário NVR são digitados somente no celular.
- Procura IPs por WS-Discovery e consulta ONVIF Media/Media2 autenticado para obter GetStreamUri. RTSP DESCRIBE verifica o fluxo e explica autenticação, caminho, porta e ausência de vídeo antes do player; URL explícita usa somente seu endereço.
- Firebase do SMART24 preservado. Teste local funciona sem login; sincronização exige usuário existente admin ou operator.
- Site GitHub Pages, produtos, loja 3D, regras e funcionalidades web mantidos. Novos tipos de evento receberam rótulos em português.

## Correções sobre o pacote v3

1. Fontes incompatíveis de protótipos antigos preservadas em `android-vision-pilot/app/src/legacy` e teste antigo em `src/legacy-test`. Não há mais exclusão de fontes durante o Actions.
2. Primeiro quadro visível recebido por LibVLC libera calibração; o evento Playing sozinho não declara vídeo confirmado.
3. Novas tentativas descartam callbacks antigos. Queda após conexão inicia reconexão à mesma câmera.
4. Credenciais e parâmetros de URL ficam em memória. URL explícita não cai silenciosamente para outro IP. Campos de senha não entram no estado salvo do Android.
5. Análise limitada a quadros de até 960 px; não copia quadros quando já há análise em andamento. Aplicativo mantém tela acesa enquanto aberto, pausa ao sair e exige reiniciar a análise ao voltar.
6. Motor espera a mão e o corpo deixarem a zona, evita converter perda de rastreamento em retirada e compensa mudanças uniformes de brilho.
7. Eventos autenticados ficam em fila local persistente; PUT com UUID mantém a mesma chave nos retries. Eventos sem login permanecem locais e não são enviados automaticamente por outra conta.
8. Expiração do token Firebase usa renovação em memória. Falhas de login descartam a sessão parcial; falhas de sincronização ficam visíveis.
9. Calibração preserva produto, SKU e espaço de coordenadas. Falha na cópia Firebase é mostrada; a marcação local permanece.
10. Workflow compila APK, executa testes de URLs, inferência e persistência, executa lint e instala/abre em emulador Android.

## Estado de validação

Histórico 3.0: build, 12 testes unitários, lint sem erros impeditivos e 2 testes Android aprovados. O vídeo físico recebido em 02/10 mostrou câmera sem imagem e ANR; a versão 3.1 acrescenta diagnóstico/ONVIF e remove paradas nativas da thread da tela. Validação 3.1: build, 23 testes unitários, lint e 5 testes Android aprovados no [Actions](https://github.com/tsvalencio-IA/SMART24/actions/runs/37017543792). Consulte [VALIDACAO-VIGILANTE-V3_1.md](VALIDACAO-VIGILANTE-V3_1.md) para a revisão anterior e [VALIDACAO-VIGILANTE-V3.md](VALIDACAO-VIGILANTE-V3.md) para o APK anterior.

Novo diagnóstico físico: o SMART24 3.1 chegou ao RTSP em `192.168.15.7:554`, mas recebeu 401. Foi reproduzida uma falha do probe ao abrir outra conexão para responder ao desafio Digest. A versão 3.2 mantém a conexão, trata nonce expirado e limita recuperação. Validação local: 22 testes aprovados. Build, 28 testes unitários, lint e seis testes Android aprovados no [Actions](https://github.com/tsvalencio-IA/SMART24/actions/runs/37058313716). Consulte [VALIDACAO-VIGILANTE-V3_2.md](VALIDACAO-VIGILANTE-V3_2.md).

Após a entrega, Thiago relatou “pacote inválido”. Foi confirmado que 3.1 e 3.2 usam assinaturas diferentes, impedindo atualização normal. O [teste do APK público exato](https://github.com/tsvalencio-IA/SMART24/actions/runs/37078546881) passou em Android 8 e Android 15: integridade e assinatura verificadas, instalação limpa, hash instalado idêntico, atualização com a mesma assinatura e duas aberturas por ambiente. A tentativa 3.1 → 3.2 reproduziu `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. A publicação exige essa prova e o certificado esperado; ainda falta configurar uma chave privada persistente para futuras versões. Não distribuir um APK recompilado com outra chave de debug como atualização.

Nenhum frame da câmera física `192.168.15.5` foi recebido neste ambiente. A rede privada da câmera não é acessível daqui. Usuário/senha NVR não foram fornecidos e não devem ser publicados no repositório.

## Teste físico local da câmera pelo celular

Esta sequência verifica a câmera dentro da rede local. A validação do produto final deve ocorrer no 4G, sem Wi-Fi, conforme [VIGILANTE-REMOTO-MULTILOJA.md](VIGILANTE-REMOTO-MULTILOJA.md).

1. Baixe o `.apk` 3.2 completo da release (393.658.222 bytes, aproximadamente 394 MB). A atualização por cima da 3.1 é incompatível por assinatura: será necessária uma instalação limpa. Desinstalar apaga as configurações, zonas/SKUs e eventos que ainda estejam só no celular; registre o que precisa ser refeito antes de remover a versão anterior. O Firebase é separado. Se uma instalação limpa do arquivo completo ainda falhar, obter tela do erro, modelo e versão Android antes de atribuir a causa.
2. Conecte celular e câmera ao mesmo roteador. A câmera precisa oferecer RTSP/NVR, e o Wi-Fi não pode isolar dispositivos.
3. Confira o IP atual da câmera (foto anterior `192.168.15.5`, último diagnóstico `192.168.15.7`), porta `554` e digite o usuário/senha NVR configurados no equipamento. Toque em CONECTAR DIRETO NA CÂMERA.
4. Aguarde imagem real e **VÍDEO CONFIRMADO**. ONVIF obtém endereços; DESCRIBE confere os fluxos antes de até três modos de reprodução por endereço. URL RTSP completa usa somente aquele endereço. Em falha, copie o diagnóstico mostrado.
5. Capture quadro e calibre uma zona para cada SKU. Produto, ID de zona e SKU devem ser reais e correspondentes àquela posição física.
6. Volte e inicie o vigilante com prateleira desobstruída. Retire um item, afaste mão e corpo da zona e observe RETIRADA PROVÁVEL; depois devolva ao mesmo lugar e observe o evento correspondente.
7. Se houver login Firebase, confira a fila local e a aba Eventos no [painel](https://tsvalencio-ia.github.io/SMART24/). Sem login, o teste é local.
8. Confirme queda/reconexão, senha incorreta, alteração de IP e mais de um SKU. Registre a mensagem do aplicativo se não abrir.

## Limites que continuam válidos

- O motor ML Kit usa uma pose principal. Este piloto não constitui reconhecimento multipessoa nem operação simultânea de seis lojas.
- O SKU vem da zona cadastrada, não de classificação visual treinada de embalagens.
- Retirada/devolução são hipóteses por mudança visual e movimento; não confirmam venda, quantidade exata, pagamento ou furto.
- O processamento ocorre com o aplicativo aberto. Não foi implementado serviço permanente em segundo plano.
- Firebase recebe eventos e heartbeat, sem vídeo contínuo. O vídeo RTSP ao vivo aparece no Android.
- Publicação real das regras Firebase, acesso da conta e câmera física exigem teste no ambiente de Thiago.

Powered by thIAguinho Soluções Digitais
