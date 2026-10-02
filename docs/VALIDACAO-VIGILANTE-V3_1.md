# SMART24 Vigilante Celular 3.1 — conexão e travamento

## Evidência do teste físico recebido

O vídeo de Thiago de 02/10/2026 mostra a tela sem imagem, tentativas RTSP 7/27 até 10/27, a mensagem final de que nenhum fluxo entregou imagem e o aviso Android **SMART24 Vigilante não está respondendo**. A tela também exibe Firebase autenticado; isso não confirma a conexão da câmera.

O campo de usuário NVR/RTSP aparece vazio. A senha da câmera é limpa pelo aplicativo após conectar; o vídeo não permite concluir se ela foi digitada. Não se afirma que credenciais sejam a causa sem receber uma resposta da câmera.

Sem o stacktrace do aparelho, o vídeo não identifica sozinho a chamada que causou o ANR. O código tinha `stop()` e `release()` nativos na thread da tela; essas chamadas podem aguardar a rede/decoder e foram retiradas dela.

## Alterações da versão 3.1

- Parada, reprodução, pausa e liberação nativas executadas em fila própria, mantendo a tela disponível durante falhas de câmera.
- Consulta ONVIF de relógio, serviços, perfis e **GetStreamUri**, com Media e Media2. Autenticação WS-Security PasswordDigest e HTTP Digest/Basic quando solicitada. Nenhuma configuração do equipamento é escrita.
- Endpoints ONVIF e URI recebidos precisam apontar à câmera selecionada; redirecionamentos HTTP e endpoints de outro host não recebem credenciais. IP `0.0.0.0`/`::` anunciado pelo dispositivo é substituído pelo IP selecionado.
- Verificação RTSP DESCRIBE diferencia porta inacessível, serviço incompatível, vídeo ausente, caminho inexistente e autenticação exigida/recusada.
- Apenas fluxos que responderam chegam ao player. Fallback de decodificação por software e UDP após TCP; primeira confirmação exige uma nova atualização da textura e quadro visível.
- Diagnóstico copiável com IP, portas e códigos, ocultando usuário/senha e parâmetros de URL.
- Botão Parar Vigilante só habilitado quando a análise está ativa. Calibração e IA continuam dependentes de imagem real.
- Mesma identidade Android, layout, ícone, calibrações, motor de eventos, fila e Firebase. Versão `3.1.0-mobile-vigilante`, código 8, Android mínimo 8.
- HTTP local permitido para ONVIF. URLs ONVIF são limitadas ao host escolhido; as URLs Firebase continuam HTTPS.

## Validação

- Compilação local dos módulos de protocolo com Kotlin 1.9.24.
- 17 testes JVM locais aprovados: 11 novos de protocolo e 6 de URLs existentes.
- Serviço de teste ONVIF real em HTTP: HTTP Digest + WS-Security, relógio atrasado em uma hora, consulta de perfis e dois GetStreamUri: **aprovados**. Nenhuma credencial real utilizada.
- XML Android, sintaxe Python e workflow: conferidos.
- Build completo, 23 testes unitários, lint e 5 testes Android: **aprovados** em 02/10/2026 às 11:13 (São Paulo).
- [Actions aprovado](https://github.com/tsvalencio-IA/SMART24/actions/runs/37017543792), código Android `c13216cb1a083d3a06dc16d569e846e4e767ee67`.
- O log Android registra 5 testes iniciados e 5 concluídos, com BUILD SUCCESSFUL. Inclui timeout/retry/parada nativos e autenticação ONVIF com diferença de relógio.

Os testes Android incluem abertura, vídeo RTSP decodificado, ONVIF autenticado com relógio diferente, solicitação de credenciais e resposta da tela durante timeout/retry/parada nativos. O servidor H.264 é MediaMTX com fonte sintética; o teste não representa a câmera física.

## APK

- [Baixar SMART24-Vigilante-Celular-v3.1.apk](https://github.com/tsvalencio-IA/SMART24/releases/download/v3.1.0-vigilante-celular/SMART24-Vigilante-Celular-v3.1.apk).
- APK universal do build aprovado; versão `3.1.0-mobile-vigilante`, código 8, Android mínimo 8.
- O arquivo ZIP do Actions tem SHA-256 `a8a80c2b4dde13ef9953cd19b3b296c820b747217a55a7a7663eab7c96ba9175`.
- A publicação confere o SHA-256 do ZIP, a versão/pacote do APK e a assinatura, e fornece `SHA256SUMS.txt` junto ao APK.

## Teste no celular de Thiago

1. Instale a versão 3.1. Se houver conflito de assinatura com o APK de teste anterior, a desinstalação daquela versão pode ser necessária e apaga seus dados locais. Não desinstale se a atualização normal for aceita.
2. Use o Wi-Fi da câmera. Mantenha o IP `192.168.15.5` e confira a porta RTSP/NVR configurada no equipamento.
3. Preencha **Usuário RTSP/NVR** e **Senha RTSP/NVR** com as credenciais da câmera. O login Firebase serve para o painel/eventos.
4. Toque em **CONECTAR DIRETO NA CÂMERA**. A consulta ONVIF e a verificação RTSP acontecem antes da reprodução; a tela deve continuar respondendo.
5. Se houver falha, toque em **Copiar diagnóstico** e envie o texto para continuar com o código/status efetivamente recebido. Nenhuma senha entra nesse diagnóstico.
6. Somente após **VÍDEO CONFIRMADO**, capture o quadro, calibre zonas/SKUs e inicie o vigilante.

O aplicativo continua em primeiro plano e usa uma pose principal. SKU vem da zona calibrada; retirada/devolução continuam hipóteses para revisão. Câmera física, regras Firebase e múltiplas lojas/pessoas não são considerados validados pelos testes sintéticos.

Powered by thIAguinho Soluções Digitais
