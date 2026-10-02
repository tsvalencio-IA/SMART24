# SMART24 Vigilante Celular 3.2 — autenticação RTSP na mesma conexão

## Diagnóstico físico recebido

O usuário executou o SMART24 3.1 no celular. O diagnóstico mostra Wi-Fi como rede padrão, câmera `192.168.15.7`, porta RTSP 554 acessível e resposta `AUTH_REJECTED (401)` em `/onvif1`. As portas 5000 e 8554 não aceitaram conexão TCP. O ONVIF recusou acesso.

Isso confirma que o celular alcançou um serviço RTSP. Não confirma se usuário/senha estão incorretos ou se a troca de autenticação do aplicativo é incompatível com aquele equipamento. As imagens anteriores mostravam `192.168.15.5`; é necessário confirmar o IP atual da mesma câmera ao testar.

## Defeito reproduzido e correção

A versão 3.1 fecha a conexão que recebe o desafio e abre outra para enviar o cabeçalho de autenticação. Um servidor Digest que associa o nonce à conexão rejeita esse cabeçalho mesmo quando as credenciais são válidas.

Um teste com servidor TCP real e nonce por conexão reproduziu `AUTH_REJECTED` na implementação antiga com credenciais corretas. Outro teste reproduziu rejeição ao expirar um nonce. Esses testes não representam prova da causa exata do erro na câmera física do usuário.

A versão 3.2 mantém o socket e o leitor durante o desafio e a resposta, incrementa CSeq, permite uma única atualização `stale=true` por conexão e uma única conexão de recuperação se o servidor fechar ou interromper a conexão. Credenciais rejeitadas continuam com tentativas limitadas e não são repetidas em todos os caminhos.

A mensagem de erro passa a informar recusa de autenticação, sem afirmar que o usuário ou a senha estejam necessariamente errados. Usuário, senha e parâmetros de URL continuam ocultos no diagnóstico.

Versão Android `3.2.0-mobile-vigilante`, código 9, mesmo pacote e Android mínimo 8. Player, layout, calibração, motor de eventos e Firebase permanecem preservados.

## Validação

- Antes da correção: os quatro primeiros testes de regressão falharam; o teste com nonce por conexão recebeu `AUTH_REJECTED` em vez de `VIDEO`.
- Após a correção: teste local inicial de 15 casos aprovado, incluindo os quatro de regressão.
- Validação local final: 22 testes aprovados, incluindo conexão encerrada com recuperação e desafio novo, nonce por conexão, nonce expirado, rejeição limitada e os seis testes de URLs existentes.
- Novo teste Android verifica DESCRIBE Digest no servidor de teste que mantém o nonce por conexão. Esse servidor anuncia SDP, mas não transmite quadros; o teste de vídeo decodificado continua separado, com a fonte H.264 sintética.
- Build, unitários, lint e seis testes Android: pendentes antes da publicação.

## Próximo teste físico

1. Confirme no Yoosee o IP atual da câmera escolhida. Use aquele IP no SMART24, porta 554.
2. Confirme as credenciais na configuração NVR/RTSP do próprio equipamento. A senha necessária é a definida para esse serviço.
3. Instale a versão 3.2 e conecte. Só há confirmação de funcionamento quando houver imagem e `VÍDEO CONFIRMADO`.
4. Se houver novo 401, envie o diagnóstico e a tela de configuração NVR/RTSP com qualquer senha coberta. Não envie a senha pelo chat.

O objetivo completo continua pendente até validar imagem da câmera real, calibração, eventos e Firebase no aparelho do usuário. Os testes sintéticos não comprovam essa integração física.
