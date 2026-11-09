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
- Lobby do Terror e lobby de login ainda sem a parte nova de enfeites.
- A fila de comandos (`LIGAR-FILA-TESTE.bat`) venceu às 18:00.
