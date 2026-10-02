# SMART24 Vigilante Celular — continuidade

Base do repositório: `263ea1b5ec2b8555e87b40b30d736c7fcfc90432`.
Pacote aplicado: `SMART24-Vigilante-Celular-v3-pronto (1).zip`.
SHA-256 do pacote: `c9516282a1e4df3530a6021ec66bf6c343bfdbd77438f77e22e9876d1009df64`.

## Aplicação atual

- Android `br.com.thiaguinhosolucoes.smart24vision`, versão 3.0.0, código 7.
- Tela inicial: `MobileVigilanteActivity`; vídeo RTSP direto com LibVLC 3.7.0.
- Primeira câmera informada por Thiago: `192.168.15.5`; senha e usuário NVR são digitados somente no celular.
- Procura ONVIF para localizar IPs. Reprodução usa RTSP, caminhos comuns ou URL completa informada. Não faz chamadas autenticadas ONVIF GetStreamUri.
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

Esta revisão está preparada para validação pelo GitHub Actions. Os resultados concluídos devem ser registrados em `VALIDACAO-VIGILANTE-V3.md` após o workflow.

Nenhum frame da câmera física `192.168.15.5` foi recebido neste ambiente. A rede privada da câmera não é acessível daqui. Usuário/senha NVR não foram fornecidos e não devem ser publicados no repositório.

## Teste físico pelo celular

1. Instale o APK 3.0.0. Se o Android recusar por assinatura diferente, o APK anterior pode exigir desinstalação; ela apaga dados locais daquele aplicativo. O Firebase é separado.
2. Conecte celular e câmera ao mesmo roteador. A câmera precisa oferecer RTSP/NVR, e o Wi-Fi não pode isolar dispositivos.
3. Confira `192.168.15.5`, porta `554` e digite o usuário/senha NVR configurados no equipamento. Toque em CONECTAR DIRETO NA CÂMERA.
4. Aguarde imagem real e **VÍDEO CONFIRMADO**. São testadas até 36 combinações. URL RTSP completa, quando informada, usa somente aquele endereço.
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
