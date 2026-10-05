# Primeira câmera da oficina: diagnóstico e acesso remoto

Em 05/10/2026, Thiago informou que a câmera que exibiu vídeo no SMART24 3.2 estava **em casa**, com o campo de IP `192.168.15.8`. A câmera **Sala** fica **na oficina**; não confundir os equipamentos, suas senhas ou seus IPs. O print posterior de `.8` mostra falha de TCP em todas as portas, e não recusa de autenticação naquele instante. Os dois avisos de fechamento não contêm stack trace.

Thiago terá acesso a um computador conectado à rede da oficina. A existência do computador permite considerar uma ponte de rede; sua permanência ligada ainda não foi confirmada.

## Consulta no Windows

1. Pressione **Win + R**, digite **cmd** e pressione Enter.
2. Execute `ipconfig`.
3. Copie a seção do adaptador conectado: **IPv4**, **Máscara de Sub-rede** e **Gateway Padrão**.
4. Se quiser consultar os vizinhos que o computador já conhece, execute `arp -a` e procure o MAC **38-7a-cc-3a-4d-8e**, informado para a Sala. Ausência nessa lista não significa câmera desligada. Entradas antigas não comprovam que o IP permanece o mesmo.
5. Confirme o IP da Sala na tela de informações atual do equipamento. A foto anterior mostrava `.5`; o diagnóstico anterior usou `.7`. `.8` pertence ao teste de casa, sem confirmação de identidade para a oficina.

Alternativa com dois cliques: [DIAGNOSTICO-REDE-SMART24.cmd](../tools/windows/DIAGNOSTICO-REDE-SMART24.cmd). Só usa `ipconfig` e `arp -a`; não configura firewall, não varre endereços e não coleta senhas. Foi revisado, mas não executado no computador de Thiago.

## Caminho possível com o computador ligado

Uma VPN com roteamento de sub-rede permite que o Android alcance uma câmera que não instala aplicativos. Tailscale oferece isso em Windows; é uma opção para o primeiro teste, não uma integração já provisionada pelo SMART24.

- Instalar o cliente oficial no computador da oficina e no Android; autenticar ambos na rede privada administrada pelo responsável.
- No computador, anunciar **apenas o IP confirmado da câmera (/32)**. Não anunciar toda a oficina sem necessidade. O comando a preencher, depois de confirmar o endereço, é `tailscale set --advertise-routes=IP_CONFIRMADO/32`.
- Autorizar essa rota no painel administrativo e restringir o acesso à câmera/portas necessárias. A aprovação de rota e a política de acesso são etapas diferentes.
- Ativar execução desacompanhada no Windows se o computador for a ponte permanente. O computador precisa continuar ligado, conectado à rede local e sem suspensão.
- No telefone, ativar a VPN, desligar o Wi-Fi e abrir o SMART24 no 4G. Informar o IP da câmera alcançado pela rota e suas credenciais NVR/RTSP.
- Confirmar **quadros atuais em movimento**, fazer o teste de sair/voltar e observar reconexão. Só esse teste valida o acesso remoto real.

Não há configuração automática de VPN, conta Tailscale criada, autorização de rota, servidor de retransmissão ou porta pública aberta pelo projeto. Não basta passar o IP local ou consultar o IP público. Não foi confirmado se o computador ficará ligado. A expansão comercial a seis lojas precisa rever plano do serviço, acessos e sub-redes repetidas; não está incluída nesta validação inicial.

O motor ainda processa com o aplicativo Android aberto. VPN resolve transporte, não torna a análise permanente nem envia vídeo ao site. O site recebe os eventos sincronizados após login.

## Fontes técnicas consultadas

- Microsoft, ipconfig: https://learn.microsoft.com/pt-br/windows-server/administration/windows-commands/ipconfig
- Microsoft, arp: https://learn.microsoft.com/pt-br/windows-server/administration/windows-commands/arp
- Tailscale, configuração Windows: https://tailscale.com/docs/features/subnet-routers/how-to/setup?tab=windows
- Tailscale, conceitos e controle de acesso: https://tailscale.com/docs/features/subnet-routers
- Tailscale, execução desacompanhada: https://tailscale.com/docs/features/unattended

Powered by thIAguinho Soluções Digitais
