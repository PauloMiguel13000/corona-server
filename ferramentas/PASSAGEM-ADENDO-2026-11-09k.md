# Passagem - adendo 2026-11-09k (ajustes123 a 125, Discord, site, GitHub)

## Instalado no servidor
- **ajustes123** (17:44): Casa da Vovó - fuga pelo esgoto consertada (`Casa.noEsgoto` cobre o vão da grade); aranha do esgoto com
  ronda sem vai-e-volta, mais rápida, ouve quem corre/anda (agachado não), vem quando a grade cai. Migração `ajustes-aranha: 3`.
- **ajustes124** (18:36): Extras - convite do Discord no chat a cada 50 min (`divulgacao.discord.*`, padrão no código: link
  https://discord.gg/uhEaWb9sJA) e comando `/discord` (`/dc`); CoronaLoja - site novo (seção "O que tem no servidor" com
  fotos `foto-*.jpg`, lista de modos, bloco do Discord, `servidor.discord` na API, tipo `image/jpeg`); `plugins/Vovo/config.yml`
  com o recorde verdadeiro (311 s) de volta.
- **ajustes125** (19:0x): Extras - classe nova `Ceu.java` (torres com braseiro na borda, coroa de ouro flutuando sobre o farol,
  7 ilhotas em volta) no spawn principal e no lobby dos minigames; roda uma vez (marcas `spawn-principal-ceu-v1.txt` e
  `lobby-minigames-ceu-v1.txt` em `plugins/Extras/`), só põe bloco onde há ar/luz invisível, desliga com `ceu-dos-lobbies: false`.
  LoginLobby - **Bedrock entra sem senha** (`bedrock-sem-senha: true`): 2 s depois de entrar, `AuthMeApi.forceLogin` (conta
  existente) ou `forceRegister` com senha aleatória (conta nova). Pedido do Paulo às 18:47 depois que `.Mona7375` desistiu no
  `/register`. Letreiro do lobby de login com exemplo de `/register`. CoronaLoja - `overflow-x: clip` no site.
- **ajustes126** (19:0x): LoginLobby - lista `bedrock-com-senha` (padrão no código: `.BalmierGorgon43`): a conta do dono no
  Bedrock continua com `/login` porque é a conta admin; o resto do Bedrock entra direto; Java sempre com `/login`. Pedido do
  Paulo às 19:03. Testado no contêiner com o stub (B1 entra direto, B2 da lista fica no lobby de login). Com o AuthMe de
  verdade só dá para confirmar quando o próximo jogador de Bedrock entrar: procurar `entrou sem senha` no log.
- **ajustes127** (19:4x): Turno da Noite - o Paulo ficou preso nas câmeras (só saía agachando e isso só aparecia no texto do
  tablet). Agora sai com Shift, Espaço, S (para trás), Q ou escolhendo outro espaço da barra (botão SAIR no espaço 9, para o
  celular); aviso fixo na barra de ação. `PlayerInputEvent` + `PlayerItemHeldEvent`. TesteBot ganhou `agachar`, `slot N`, `tecla`.
- **ajustes128** (20:0x): Extras - Lobby do Terror com `Ceu.TERROR` (coroa de obsidiana que chora sobre o obelisco, 7 ilhotas
  mortas; sem torres; marca `lobby-terror-ceu-v1.txt`) e `Estatuas.java`: ItemDisplay não salvos dos modelos 3D
  (`props/porcelana_f0`, `vovo_idle`, `anim_capi_f0`, `anim_jaca_f0`) ao lado dos portões, só para quem tem o pacote ou
  está no Bedrock; nascem quando chega alguém no mundo `terror`. Desliga com `lobby-terror.estatuas: false`.
- **ajustes129** (09/11 20:17): Turno - `JUMP_STRENGTH` 0 durante a noite (volta em `Guarda.limpar`) e `PlayerMoveEvent` que
  segura subida/passo. Conferido no Java de verdade em 10/11: pulo, W, A não tiram da cadeira; Espaço, S, Shift e Q saem das câmeras.
- **ajustes130** (10/11 09:16): Turno - `Noite.amarrar` (a cada tick volta para a cadeira/câmera se o cliente saiu do lugar; pensado
  para o Bedrock) e a dica de saída foi para a barra de cima (a barra de ação é tapada pela vida/mana do AuraSkills).
  Masmorras - `AlaMonstros.java`: `/warp armaduras` e `/warp monstros` (masmorras.admin); passarela em x 60..121 do mundo
  `galeria` com 21 modelos 3D em pedestais (props + naochore/backrooms do pacote Java), pedaços sempre carregados.
- **ajustes131** (10/11 09:2x): Masmorras - `/darmoedareviver <nick> [quantidade]` (atalho de `/reviver dar`; offline fica
  guardado; `todos` dá para quem está online). Extras - sem partículas de VIP no mundo `galeria`.

## Discord (servidor Corona Server, conta coronadooscar no Chrome)
- Convite permanente: https://discord.gg/uhEaWb9sJA
- Regra fixa: todo dia às 18:00 postar em #atualizações tudo o que mudou no dia, com imagens e o lembrete do Traje do Rei (VIP).
- Imagens: `file_upload` não aceita arquivos; subir em `corona-pacotes/img/` e colar o link raw (o Discord mostra a imagem).
- Texto com várias linhas: `ClipboardEvent('paste')` no `[role="textbox"][data-slate-editor="true"]`, conferir, Enter.
- Perfil do Paulo: avatar do Rei (pixel art desenhada por canvas na própria página e posta no `input[type=file]`), faixa
  #8a1016, bio, status e widget "Jogo favorito: Minecraft". Efeito, moldura, banner com imagem e nome estilizado são do Nitro.
- Call **🎵 Música** (id 1569458483655344258) com o bot **Jockie Music** (permissões só de voz e texto, sem administrador).
  Comandos `m!play nome`, `m!skip`, `m!pause`, `m!resume`, `m!queue`, `m!leave`. YouTube: atividade Watch Together.
- AutoMod "Bloquear links": apaga, mostra aviso, castigo de 60 s e alerta em #logs. Ban é manual (AutoMod não bane).

## GitHub
- `PauloMiguel13000/corona-server` (privado): fontes de todos os plugins, pacote e ferramentas - enviado em 09/11 18:3x.
- `PauloMiguel13000/corona-pacotes` (público): só pacotes Bedrock e imagens (`img/`).

## Teste no contêiner
- O AuthMe de verdade não carrega no contêiner (baixa bibliotecas do Maven, bloqueado); usar `AuthMeStub.jar`, que agora tem
  `forceLogin` e `forceRegister`. Bedrock de mentira: `-Dlobby.teste.bedrock=B1,B2` no `run.sh`.
- `vista iso <mundo> <cx> <cz> <raio> <y0> <y1> <arquivo> [giro]`: desenho isométrico para conferir construção sem abrir o jogo.

## Pendências
- Conferir no cliente Bedrock: entrada sem senha com AuthMe de verdade, Chorona (piglin_brute), Vovó 3D, aranha no celular.
- A fila de comandos (`LIGAR-FILA-TESTE.bat`) venceu às 18:00.

## ajustes132 (10/11 09:36) - /dararmadura e /darespada

- Masmorras `DarItens.java`: `/dararmadura <nick> <nome>` e `/darespada <nick> <nome>` (permissao `masmorras.admin`).
  Nome = conjunto: `herobrine` (cosmetico da loja, netherite), `rei esqueleto`, `farao`, `bruxa`... (sem acento, espaco ou `_`,
  aceita "armadura do ..." e o id da masmorra, ex. CRIPTA). Sem nome: lista todos. Nome ambiguo ("rei"): lista as opcoes.
  `todas` = tudo em caixas de shulker (60 espadas em 3 caixas; 240 pecas em 9 caixas).
  `/dararmadura <nick> rei` = Traje do Rei pelo `/vip traje dar` (cortesia; fica na lista ate `/vip traje tirar`).
  Reliquias saem em diamante com o poder do tema (`reliquias.aplicar`). Testado no servidor de teste (console + bot).
- 10/11: conferido o save do Paulo (coronadooscar) depois da queda no teste: nao morreu, mochilas survival e masmorras inteiras.

## ajustes133 (10/11 09:50) - Turno da Noite em grupo (coop)

- `Noite.java`: agora tem `List<Vigia>` (camera, cameraDesde, ancora, lugar e BossBar por jogador). Energia, portas, luzes e
  bichos sao da sala. Lugares lado a lado (1,6 bloco; 2 vigias = x -0.3 e 1.3; 3 = -1.1, 0.5, 2.1). Capi so anda se NINGUEM
  esta nas cameras; Boto para se ALGUEM olha a Cortina; cada tablet aberto conta no uso. Bicho dentro: susto para todos.
  `remover(p)`: quem sai/cai deixa os outros jogando.
- `Turno.java`: `/turno coop <nick> [nick2] [noite]` (ate 3 vigias, noite do anfitriao), `/turno aceitar|recusar` (60 s, clique
  no [ACEITAR]); comeca quando todos respondem. IA +2 por vigia a mais (`coop.ia-a-mais-por-vigia`). Todos ganham as coronas;
  o progresso so avanca para quem estava naquela noite. Item "Jogar em grupo" no menu.
- Testado no servidor de teste com bots (2 e 3 vigias, cameras separadas, um saindo, um caindo, vitoria, susto, solo).
  NAO testado com cliente real (Java/Bedrock) no coop.

## ajustes134 (10/11 10:06) - Fabrica da Alegria: Tutu e Dona Bolota em 3D

- `modelos/fabrica3d.py` (usa o corpo/pintura de `animatronicos3d.py`): `corona:props/anim_tutu_f0|f1` e `anim_bolota_f0|f1`.
  Mesclado em `coronapack/res/pack` (Java + mcpack 1.13.4, item_texture.json somado) e `custom_mappings/corona-fabrica.json`.
- Fabrica `Boneco3d.java`: ItemDisplay que segue o Mannequin a cada tick (posicao, lado, escala = escala do boneco / 1,25,
  quadro 1 enquanto `cacando`); so quem tem o pacote/Bedrock ve o modelo. Config `modelo-3d`.
- Conferido so no servidor de teste (entidade criada com o item certo) e na previa; NAO visto em cliente real.
- ATENCAO (pacote Bedrock novo): ANTES de instalar um CoronaPack com mcpack novo, publicar o arquivo em `corona-pacotes`
  como `corona-<10 primeiros do sha1>.zip` (o plugin procura no jsDelivr ao ligar). No ajustes134 publiquei DEPOIS
  (corona-78cbbc4d2e.zip), entao ate o proximo reinicio o Bedrock recebe o pacote pelo Geyser (modo pasta), nao pelo link.

## ajustes135 (10/11 10:17) - Hotel: a Figura e o Perseguidor em 3D

- `modelos/hotel3d.py`: desenhos ORIGINAIS (Figura = porteiro cego de uniforme vinho com a cabeca num saco de pano;
  Perseguidor = sombra de sobretudo e cartola com olhos/sorriso que brilham). Itens `corona:props/anim_figura_f0|f1`,
  `anim_perseguidor_f0|f1`. mcpack 1.13.5 publicado ANTES em corona-pacotes (corona-326746167a.zip); `corona-hotel.json`.
- Hotel `Visual.java`: para skins em `MODELOS` o Par ganha um ItemDisplay que segue o Mannequin (escala/1,25); quem tem o
  pacote ve o modelo, o boneco pintado fica escondido; `Visual.quadro(corpo, 0|1)` (Figura cacando = 1; Perseguidor = 1).
  Config `modelo-3d`. Testado no servidor de teste so a Figura (entidade criada e seguindo); nao visto em cliente real.
