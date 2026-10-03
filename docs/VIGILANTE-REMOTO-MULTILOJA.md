# SMART24 — acesso remoto às seis lojas

## Requisito confirmado em 03/10/2026

Thiago definiu que o dono dos seis mercadinhos acompanhará o SMART24 no próprio celular, usando 4G, sem precisar estar no Wi-Fi de nenhuma loja. O acesso local é uma etapa de diagnóstico da câmera, não uma condição para o uso final do sistema.

O objetivo existente de funcionar com diferentes marcas por RTSP/ONVIF permanece. O painel deve reunir as lojas, câmeras, estados de conexão, eventos para revisão e vídeo ao vivo autorizado. O processamento de vigilância deve continuar quando o dono fechar o aplicativo, bloquear o telefone ou ficar sem conexão.

Este documento registra o escopo e a diferença para o código atual. **Não representa implantação do acesso remoto ou conclusão da vigilância das seis lojas.**

## O que o código atual faz

| Componente | Estado confirmado no repositório |
| --- | --- |
| APK 3.2, `MobileVigilanteActivity` | Abre RTSP para uma câmera por vez. O diagnóstico prefere uma rede Wi-Fi disponível. Não provisiona VPN, ponte remota ou servidor de vídeo. Um endereço remoto só pode funcionar se já existir uma rota alcançável fora do aplicativo. |
| Análise no APK | Processa quadros enquanto a tela está ativa; `onPause` interrompe a análise. Não é um serviço de vigilância permanente das seis lojas. |
| Firebase no fluxo móvel | Recebe eventos e estados autenticados. Não transforma automaticamente o IP privado da câmera em vídeo acessível pela internet. |
| `edge-agent/app.py` | Base de conector para RTSP, disponibilidade, heartbeat e reconexão. Ainda não executa a IA de retirada/devolução nem distribui vídeo remoto. |
| Instalação 3.2 | APK público exato instalado e aberto em emuladores Android 8 e 15. Isso não valida câmera física, 4G ou operação simultânea das lojas. |

O diagnóstico físico disponível continua sendo o 401 em `192.168.15.7:554` no SMART24 3.1. O IP anterior informado foi `192.168.15.5`. Ainda não houve confirmação de imagem real na 3.2.

## Conexão necessária

`192.168.15.7` pertence a uma faixa privada. Informar esse IP em um telefone no 4G não cria uma rota para a loja. É necessária uma ligação remota configurada entre a rede da loja e o sistema, por exemplo uma VPN ou um conector que estabeleça uma conexão autenticada de saída.

A câmera continua acessível por RTSP/ONVIF dentro da loja. A ligação remota pode aproveitar um roteador compatível ou um equipamento capaz de executar o conector. Um NVR ou computador existente só pode assumir essa função após verificar suas capacidades. Não foi confirmado que as lojas possuem algum desses recursos.

O endereço deve identificar loja e câmera. Duas lojas podem usar o mesmo IP local, por isso não se deve cadastrar apenas `192.168.15.7` como identidade global. A solução deve preservar `storeId` e `cameraId` e isolar as rotas ou os fluxos de cada loja.

## Operação pretendida

- **Rede de cada loja:** disponibilizar as câmeras autorizadas ao serviço SMART24 por uma ligação remota protegida. As credenciais NVR/RTSP não serão publicadas no site, nos logs ou no GitHub.
- **Processamento permanente:** executar a análise em equipamento adequado na loja ou em servidor que receba os fluxos. A escolha depende dos equipamentos existentes, quantidade de câmeras e capacidade das conexões. O telefone do dono não será o único processo responsável pela vigilância.
- **Vídeo remoto:** entregar ao aplicativo uma sessão de visualização autenticada e compatível com rede móvel. O serviço de vídeo, suas rotas e a travessia de NAT ainda não estão implantados.
- **Firebase e painel:** manter autenticação, permissão por loja, produtos/SKUs, estados, eventos, auditoria e revisão humana. Vídeo contínuo requer um serviço de mídia separado do banco de eventos.
- **Celular do dono:** selecionar lojas/câmeras, acompanhar estados e alertas, revisar eventos e abrir vídeo sob demanda pelo 4G. Falta implementar e validar esse fluxo remoto completo.

A IA continuará tratando retiradas/devoluções como eventos prováveis para revisão. A exigência de acesso remoto não altera as regras de evidência, não autoriza bloqueio automático de saída e não comprova reconhecimento exato de todos os produtos.

## Informação necessária para executar a implantação

Confirmar o que permanece ligado em cada loja: somente câmera e roteador, ou também NVR, computador ou outro equipamento. Obter os modelos sem solicitar senhas pelo chat. Essa informação determina se é possível aproveitar a infraestrutura atual e onde instalar o conector.

Depois será necessário configurar o ponto remoto/servidor e a conexão autorizada de cada loja. Ainda não existem neste trabalho um servidor remoto provisionado nem acesso administrativo confirmado aos equipamentos das lojas. Não afirmar que um novo APK, sozinho, concluiu essa ligação.

Antes de distribuir outra versão Android, configurar a chave de assinatura persistente e testar o arquivo exato. A publicação atual bloqueia uma assinatura diferente da esperada; não voltar a entregar atualização assinada com outra chave temporária sem explicitar a incompatibilidade.

## Critérios para confirmar funcionamento

1. No telefone do dono, desligar o Wi-Fi e receber quadros reais da primeira loja pelo 4G; indicar vídeo confirmado somente após receber imagem.
2. Repetir nas seis lojas, mantendo identidade, permissão e eventos separados, inclusive quando os IPs internos forem iguais.
3. Fechar o aplicativo do dono e confirmar que o processamento continua no equipamento/servidor responsável.
4. Interromper e restabelecer a conexão de uma loja: informar a queda corretamente, recuperar o fluxo e preservar eventos pendentes sem duplicação.
5. Validar calibração, produto/SKU, eventos para revisão e sincronização Firebase com cenas reais. Heartbeat, SDP, simulação ou captura de tela não substituem essa validação.

## Referências técnicas

- [RFC 1918 — endereços privados e ligação entre redes](https://www.rfc-editor.org/rfc/rfc1918).
- [Documentação de roteadores de sub-rede — exemplo de acesso remoto a câmeras sem cliente na câmera](https://tailscale.com/docs/features/subnet-routers). Exemplo de mecanismo; nenhum fornecedor ou plano foi contratado/configurado.
- [Implementação atual do aplicativo](../android-vision-pilot/app/src/main/java/br/com/thiaguinhosolucoes/smart24vision/MobileVigilanteActivity.kt).
- [Implementação atual do conector](../edge-agent/app.py).
- [Teste do APK público exato](https://github.com/tsvalencio-IA/SMART24/actions/runs/37078546881).
