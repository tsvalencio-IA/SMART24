# SMART24 Vigilante Celular 3.2 — autenticação RTSP na mesma conexão

## Instalação e assinatura: correção da entrega

As versões 3.1 e 3.2 foram assinadas com chaves de debug diferentes, geradas em jobs distintos. A assinatura da 3.1 é `a6a9a984611d97691fe36df598d69fb4210f8585f38cd10724db5dc8d489d8c7`; a da 3.2 é `65a8783f17b9196344ded1785b4c8e53e6e9c2ab19908a86663bee0a572ace01`. Isso impede atualizar o mesmo pacote da 3.1 para a 3.2. Os testes Android anteriores recompilavam o aplicativo; não comprovavam a instalação do arquivo exato entregue.

O APK público 3.2 foi baixado integralmente e passou na verificação de tamanho, SHA-256, CRC de todas as entradas ZIP e assinatura criptográfica. O novo workflow baixa os APKs das releases, sem recompilar ou assinar novamente, e testa a atualização e a instalação limpa. No Android 8 (API 26) e Android 15 (API 35), o [Actions de instalação aprovado](https://github.com/tsvalencio-IA/SMART24/actions/runs/37078546881) confirmou:

- Atualização da 3.1 para a 3.2 recusada com `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.
- Instalação limpa da 3.2 aprovada, com SHA-256 do `base.apk` instalado igual ao arquivo público.
- Reinstalação da mesma 3.2 com `adb install -r` aprovada, preservando a mesma assinatura.
- Duas aberturas por ambiente com `Status: ok`, processo em execução e sem falha do aplicativo no buffer de crashes. Capturas das telas e logs estão nos dois artefatos do run.

Isso confirma a instalação do binário entregue nos emuladores. Não comprova a instalação no telefone do usuário nem o vídeo da câmera física. O erro “pacote inválido” relatado ainda precisa ser relacionado ao conflito reproduzido ou ao arquivo/aparelho concreto.

Para substituir uma 3.1 instalada, é necessária uma instalação limpa da 3.2. **Desinstalar apaga login, configurações, zonas/SKUs e eventos ainda armazenados somente no aplicativo.** Registros que já chegaram ao Firebase não são apagados por isso. Antes de remover a versão anterior, registre as configurações e calibrações que precisarão ser refeitas. Não desinstale outros aplicativos de câmera.

Baixe o arquivo `.apk` completo pelo link desta release, aproximadamente 394 MB (393.658.222 bytes); não tente instalar o ZIP do código ou um download incompleto. O aparelho precisa ter Android 8 ou superior. Se a instalação limpa do arquivo completo ainda disser “pacote inválido”, o modelo do aparelho, a versão Android e a tela do erro são necessários para diagnosticar a recusa concreta do celular.

A publicação passa a exigir o relatório de instalação do hash exato nos dois ambientes e a assinatura esperada. Um build com outra assinatura fica bloqueado. Uma chave de assinatura persistente, privada e armazenada de forma protegida ainda precisa ser configurada para futuras versões; a chave privada de debug das releases antigas não foi preservada pelo workflow. Não foi alterado o APK já publicado, o pacote, o layout, o player ou o fluxo Firebase.

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
- Build, 28 testes unitários, lint sem erros impeditivos e seis testes Android: **aprovados** no [Actions](https://github.com/tsvalencio-IA/SMART24/actions/runs/37058313716).
- Código Android testado: `a3006da15c5ed6677c3295db4397947b191422d5`.
- Relatório unitário: 28 testes, zero falhas, zero erros e zero testes ignorados. Lint: zero erros impeditivos; 225 avisos.
- Log Android: seis testes iniciados e concluídos, BUILD SUCCESSFUL. O teste de Digest por conexão recebeu SDP 200; vídeo decodificado, ONVIF e resposta da interface continuam em testes separados.

## Próximo teste físico

1. Confirme no Yoosee o IP atual da câmera escolhida. Use aquele IP no SMART24, porta 554.
2. Confirme as credenciais na configuração NVR/RTSP do próprio equipamento. A senha necessária é a definida para esse serviço.
3. Instale a versão 3.2 e conecte. Só há confirmação de funcionamento quando houver imagem e `VÍDEO CONFIRMADO`.
4. Se houver novo 401, envie o diagnóstico e a tela de configuração NVR/RTSP com qualquer senha coberta. Não envie a senha pelo chat.

O objetivo completo continua pendente até validar imagem da câmera real, calibração, eventos e Firebase no aparelho do usuário. Os testes sintéticos não comprovam essa integração física.

## Download da versão aprovada

- [SMART24-Vigilante-Celular-v3.2.apk](https://github.com/tsvalencio-IA/SMART24/releases/download/v3.2.0-vigilante-celular/SMART24-Vigilante-Celular-v3.2.apk).
- A publicação verifica o run/commit, a igualdade dos arquivos Android, o hash do ZIP de artefato, o pacote, a versão 3.2.0/código 9 e a assinatura do APK. O checksum acompanha o download em `SHA256SUMS.txt`.
- O APK é do build do código aprovado. O teste Android recompila e instala o mesmo código em emulador; não representa instalação no aparelho do usuário nem conexão com a câmera física.
- Se o Android indicar conflito de assinatura com a versão de teste anterior, reinstalar apaga as configurações locais. Redigite a senha NVR/RTSP ao conectar: o aplicativo limpa o campo após cada tentativa.

## Publicação confirmada

- [Publicação aprovada no Actions](https://github.com/tsvalencio-IA/SMART24/actions/runs/37059318198).
- Release `v3.2.0-vigilante-celular` publicada; APK e checksum em estado `uploaded`.
- APK: 393.658.222 bytes; SHA-256 `a8359d87933d959e64d6a87d229f5e18276cb3e8b6df65790ef350c5fdd33de1`.
- O hash do asset publicado corresponde ao APK cuja versão e assinatura foram verificadas no workflow.
- Continua pendente a confirmação de imagem da câmera física e do fluxo completo no celular do usuário.
