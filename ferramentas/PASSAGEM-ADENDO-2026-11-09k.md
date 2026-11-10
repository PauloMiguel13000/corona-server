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

## ajustes136 (10/11 10:30) - Hotel e Fabrica de volta aos bonecos de antes

- O Paulo NAO quis os modelos 3D novos (Figura, Perseguidor, Tutu, Bolota): "deixa do jeito que tava, nao mude nada".
  `modelo-3d: false` em `plugins/Hotel/config.yml` e `plugins/FabricaAlegria/config.yml`. O codigo e os arquivos do pacote
  continuam la, desligados. NAO religar sem ele pedir. Ele pediu os monstros dos jogos originais; isso nao se faz
  (personagens protegidos) e foi explicado a ele.
- O post das 18:00 NAO deve anunciar monstros 3D do Hotel/Fabrica.

## ajustes137 (10/11 10:38) - Missoes do dia

- Pedido do Paulo (10:33): "Continue melhorando; se for melhorar o hotel, voce so mexe para melhorar as mecanicas que ja
  existem iguais ao jogo" (nada de monstro/visual novo no Hotel).
- Extras `MissoesDiarias.java`: `/missoes` (aliases missao, diarias, diaria): 3 missoes por dia, iguais para todos (sorteio
  pela data, meia-noite de Brasilia), de 12 tipos (monstros, masmorra, minerios, pescar, colher, comer, encantar, andar,
  tempo, minigames). Premio em coronas no clique; bonus das 3 (60 + 15 por dia seguido, ate 7). `missoes-diarias.yml`.
  Dono: `/missoes completar <nick>` (extras.admin) termina as 3 de hoje para testar. Config `missoes-diarias.*`.
  Testado no servidor de teste com bot (menu, completar, pegar premios, sem pagar em dobro).

## ajustes138 (10/11 12:03) - Guia do servidor (/ajuda)

- Extras `Ajuda.java`: `/ajuda` (aliases guia, comandos, tutorial, comecar) abre um bau de 45 com 18 atalhos (masmorras,
  classes, minigames, terror, pets, rtp, ec, missoes, coronas, votar, vip, loja, galeria, reviver, grupo, indicar, discord,
  ip); clicar fecha o bau e roda o comando 1 tick depois. Quem entra pela 1a vez recebe o lembrete do /ajuda (20 s).
  Testado no servidor de teste com bot (abrir, clicar em Terror -> foi para o lobby do terror).
- Regra do Paulo (10/11 10:27-10:41): NAO criar/alterar monstro no Hotel; ele pediu os monstros do jogo original (com
  outro nome/cor) e isso foi recusado (personagem protegido). No Hotel, so mecanicas.

## ajustes139 (10/11 12:26) - dicas automaticas das novidades

- `Divulgacao.DICAS_EXTRAS` (4 dicas: /missoes, /ajuda, /turno coop, /terror) entram na roda das dicas do chat junto com
  as do config (`divulgacao.dicas-extras: []` desliga). Testado no servidor de teste (apareceu na 5a rodada).
- 10/11 12:16: o Paulo mandou `REGRAS-LIMITE-DE-USO.md` (gastar pouco, melhorias pequenas, passagem perto de 94%, nao
  usar a extensao do Chrome se ela estiver na NBTSJ). Seguir. Copia em /mnt/user-data/attachments (pedir a ele de novo).

## ajustes140 (10/11 12:32) - Turno coop: convite pelo menu

- `/turno` -> item "Jogar em grupo" (kMenu=100) abre `menuConvite`: cabecas de quem esta online e fora de um turno
  (kMenu=200 + kConvite=nick); clicar chama `coop(p, {"coop", nick})` com a noite mais alta do anfitriao.
  Testado no servidor de teste com 2 bots (clique -> convite -> aceitar -> os dois na sala).

## ajustes141 (10/11 12:40) - Turno coop com fila

- Pedido do Paulo: "O coop do fnaf, voce faz as pessoas esperarem na fila que nem os outros minigames, e ai entra todo
  mundo no coop". `/turno fila` (ou o item "Jogar em grupo" do menu): o 1o abre a contagem (`coop.fila-segundos`, 30) e
  avisa todo mundo com [ENTRAR NA FILA]; quem entra joga junto; com 3 comeca em 3 s; sozinho, vai sozinho. A noite e a
  mais alta que TODOS liberaram (ate 5). `/turno sair` sai da fila. `/turno coop <nick>` (convite) continua existindo;
  o `menuConvite` ficou no codigo sem uso. Testado com 3 bots (fila cheia, fila de 1, sair da fila).

## 10/11 13:55 - pedidos novos do Paulo (fila) e ajustes141 instalado

- ajustes141 (fila do Turno coop) instalado 13:54, log ok. Proximo numero: ajustes142.
- Pedidos (palavras dele): "Boss mundial ... nao tem nao, mas voce podia criar, com uma resource foda"; "Colocar musica
  dentro das dungeons, efeitos de som das espadas e armas, poderes"; "Melhorar os efeitos tanto PC quanto celular dos
  poderes, e otimizar para celular para nao ter lag"; "Otimizar o servidor para celular"; jogo de terror so nosso
  "proximo domingo as 5 da manha" com o Porteiro e a Sombra (hotel3d.py) "e pode fazer outros tambem" (originais);
  depois mapa survival de apocalipse zumbi com armas de fogo por plugin (item que atira, municao).
- Boss mundial: modelo ORIGINAL pronto so como previa (`modelos/colosso3d.py`, "Colosso da Coroa Partida",
  `anim_colosso_f0|f1`), NAO esta no pacote nem instalado. Esperando o Paulo aprovar o visual antes de fazer o evento.
- "O boss entrando no chao durante a luta": ele nao disse qual chefe; perguntar de novo se voltar a falar.

## ajustes142 (10/11 14:12) - Boss mundial (Colosso da Coroa Partida)

- Visual aprovado pelo Paulo na 3a previa ("pronto"): `modelos/colosso3d.py` (desenho proprio), `anim_colosso_f0|f1`,
  pacote Java + mcpack 1.13.6 (publicado antes: corona-9450b77944.zip), `custom_mappings/corona-colosso.json`.
- Extras `BossMundial.java`: esqueleto wither invisivel escala 3 + ItemDisplay (escala 3,2) seguindo; quem nao tem o
  pacote ve a armadura gigante. Vida propria (600 + 350 por jogador a mais), 4 golpes (esmagar com anel de aviso,
  investida, ajudantes, chuva de magma), barra de vida para quem esta a 48 blocos, some em 20 min. Nasce a cada 120 min
  a 150-350 blocos do spawn do `world` (config `boss-mundial.*`). `/boss` leva ate ele; admin: `/boss invocar|remover|status`.
  Premio: 150 coronas para quem bateu (>=10 de dano) + 200/120/60 para o top 3.
- Testado no servidor de teste com bot (nascer, ficar no chao, golpes sem erro, morrer, premio). NAO visto em cliente real.
- device_commit_files recusou o jar de 26 MB (HTTP 400): mandar pelo SendUserFile e usar o fileUuid.
