# SMART24 Vision Pilot — teste real com a câmera Yoosee

Este aplicativo Android é a ponte de **piloto controlado** para testar a sua câmera residencial sem computador na loja e sem colocar senha Yoosee no GitHub/Firebase.

## O que ele faz de verdade

1. você entra no SMART24 Vision com um usuário Firebase `admin` ou `operator`;
2. o Android pede autorização de captura de tela;
3. o aplicativo Yoosee é aberto;
4. você abre o vídeo ao vivo da câmera e deixa em tela cheia;
5. o SMART24 processa digitalmente os quadros mostrados pelo Yoosee;
6. reconhece QR das etiquetas SMART24;
7. detecta faces/objetos e cria IDs temporários `PERSON-XX`;
8. calibra retângulos de geladeira/prateleira;
9. registra `PRODUCT_PICKUP`, `PRODUCT_RETURN` ou `AMBIGUOUS_INTERACTION`;
10. atualiza carrinhos, heartbeat e eventos no Firebase.

## O que não faz ainda

- não realiza reconhecimento facial nominal do morador;
- não substitui uma conexão direta em nuvem com dezenas de câmeras;
- não afirma que uma etiqueta escondida continua visível;
- não conecta ao caixa automaticamente;
- não transforma um teste de uma câmera em implantação final das seis lojas.

## Como gerar o APK sem terminal

1. envie a pasta completa do projeto para o repositório SMART24;
2. confirme que apareceu `.github/workflows/build-smart24-vision.yml`;
3. no GitHub, abra **Actions**;
4. abra **Build SMART24 Vision APK**;
5. toque em **Run workflow**;
6. ao terminar, abra a execução e baixe o artefato `SMART24-Vision-Pilot-APK`;
7. extraia o ZIP do artefato e instale `app-debug.apk` no Android.

## Ordem do primeiro teste

1. publique as regras novas de `web/firebase/database.rules.json`;
2. gere 3 etiquetas de um produto de teste;
3. use Reposição para associá-las à zona `GELADEIRA-01-P01`;
4. instale o APK;
5. entre com o mesmo usuário Firebase do painel;
6. toque em **Autorizar captura e abrir Yoosee**;
7. abra o vídeo da câmera;
8. deixe 3 produtos etiquetados visíveis;
9. volte ao app e calibre a zona;
10. retorne ao Yoosee, retire uma unidade e aguarde;
11. confira **Eventos** e **Carrinhos** no painel SMART24 em outro celular.

## Observação técnica honesta

O piloto usa a sessão do aplicativo oficial Yoosee para transportar o vídeo remoto. A análise é executada no Android que autorizou a captura. A versão final para seis lojas exigirá ingestão contínua em nuvem ou câmeras com fluxo/API compatível, porque nenhum código roda 24 horas no GitHub Pages quando o navegador está fechado.
