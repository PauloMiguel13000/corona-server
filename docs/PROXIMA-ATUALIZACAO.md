# Próxima atualização (combinado com o Paulo em 17/11/2026)

Ordem: primeiro o bug, depois os chefes. Um de cada vez, testado no servidor de teste, instalado com o servidor vazio.

## Feito na instalação 348 (17/11 13:33, com aviso na tela, 0 erros)
- ~~Pet vaca cogumelo (antigravidade)~~: BabyPets `pets.yml`, gravidade de -0.09 para **-0.03 por nível** (nível 10 = 30% menos gravidade). Backup em `/root/backups-claude/` no servidor.
- ~~Torretas ativando efeitos da arma~~: `Reliquias.acertou` ignora dano causado pelo plugin Poderes (campo `emDanoPoder`, lido por reflexão). Vale para torres, magias e explosões.
- ~~Boss mundial~~: vida ×2 e dano ×1,2 sobre a config (`MULT_VIDA` e `MULT_DANO` em `BossMundial.java`; a config de produção não foi lida).
- ~~Engenheiro~~: Paulo decidiu (13:21) no máximo **3 torres ativas ao mesmo tempo**; quando uma some, pode colocar outra (`Torretas.MAX_ATIVAS`). Vida do Engenheiro e recarga sem mudança.
- Novo: **vasos decorados da masmorra** não guardam mais itens (o do Sui "sugou" a espada). Clicar com a mão vazia, ou tentar quebrar, devolve o que estiver dentro.
- Novo: **portais nunca levam à secreta (???)** nem a menos de 128 blocos dela (Paulo: "deixe que achem").

## Instalação 349 (17/11 14:21, 0 erros)
- ~~Prêmio do chefe no nível 3 com as 4 peças iguais~~: **desfeito na 351**, a pedido do Paulo. O prêmio voltou a ser como era.
- Trilha: os monstros dão só **calça e botas**, sem elmo, no mesmo nível de antes (`Missoes.PECAS_RESTO`).

## Instalação 350 (17/11 14:27, 0 erros)
- **Engenheiro com metade da vida máxima** (modificador `poderes:engenheiro_vida` de -0.5, `Poderes.vidaEngenheiro`), fora dos mundos sem poderes. Conferido no ViboXact: vida máxima de 24 para 12.

## Instalação 352 (17/11 14:42, 0 erros): torres do Engenheiro
- Torre **igual** = reposiciona: a nova herda o tempo que faltava na antiga, sem renovar (ideia do Sui; a regra do tempo herdado é do Paulo).
- **4ª torre** de outro tipo = sai a mais antiga (fila). Máximo de 3 ao mesmo tempo.

## Instalação 353 (17/11): chefes novos e sala trancada
- **Sala do chefe trancada:** quem entrou não sai andando nem por pérola ou chorus enquanto o chefe estiver vivo (`MecanicasChefes.fugir`; config `chefe.sala-trancada`).
- Mecânicas novas em `MecanicasChefes.java` (teste com `/masmorras admin mecanica <nome>`):
  - ~~Rainha Aranha~~ `ninhada`: sobe no teto, joga 2 ovos que viram aranhas e desce. A prisão de teia ficou só com a Viúva Negra.
  - ~~Gosma~~ `engolir`: engole um jogador, que precisa bater 3 + nível vezes para sair (ou é cuspido em 8 s).
  - ~~Senhor do Vazio~~ `buraco`: buraco negro que puxa; quem cai dentro faz ele explodir.
  - ~~Bispo~~ (passiva da CATEDRAL): sacerdotisa numa sala anterior, com sino e rastro dourado. Enquanto ela vive, ele "morre", volta com a vida cheia e cria 2 cópias.
  - ~~Capitão Caveira~~ `mare`: a água sobe 3 blocos e aparecem mastros com escada.
  - ~~Bibliotecário~~ `livro`: parkour de páginas dentro da arena. Quem chega ganha Força; quem cai, Fraqueza.
  - ~~Lorde Vampiro~~ `vitrais`: quebre os vitrais e o sol entra (10 s com +50% de dano e fogo). Fora do sol ele leva só 50% de dano.
  - ~~Minotauro~~ `muralhas`: labirinto que muda a cada 10 s, com investidas; só leva dano tonto (depois de bater na parede).
  - ~~Arquimago~~ `gravidade`: todos flutuam até o teto por 14 s.
  - ~~Senhor dos Ventos~~ `ilhas`: o chão some e ficam ilhas, com rajadas de vento e pulo melhor.
  - ~~Ceifador de Almas~~ (passiva de ALMAS): quem morreria vira alma por 30 s; um amigo passa no corpo para trazer de volta, e a alma deixa o chefe lento e fraco.
  - ~~Relojoeiro~~ `tempo`: só um jogador se mexe e tem 5 s para quebrar a engrenagem.

## PRONTO NO CÓDIGO, AINDA NÃO INSTALADO (Paulo: "corrigimos mais tarde")
- Torres do Gelo e da Tesla não deixam mais os chefes (das masmorras e o mundial) lentos nem congelados (`Torretas.chefe`). O dano das torres continua igual. Motivo: com Tesla + Gelo + Arqueira, o chefe nem se mexia.
- Endermen das masmorras não se teleportam (`Masmorras.endermanNaoFoge`): eles fugiam para fora da masmorra.
- O instalador pronto é o `passo-25` (Poderes + Masmorras, número 355). É só gerar de novo e rodar.

## Testes feitos jogando (conta coronadooscar, Minecraft Java no PC ponte)
- Limite de 3 torres: OK. A 4ª é recusada e entra depois que uma some.
- Login automático: o serviço `corona-login-pc` no servidor faz `authme forcelogin coronadooscar` quando a conta entra pelo IP do PC ponte (177.8.228.129). Se o IP do PC mudar, rode de novo o script de instalação dele (o mesmo da passagem 12, que pega o IP do `SSH_CLIENT`).
- Vaso: o teste dentro da masmorra ficou para o Sui (ViboXact) fazer, clicando no vaso dele com a mão vazia.
- Chefes (17/11, 15h): **Ilhas** tinha bug (quem caía ficava preso dentro do chão quando ele voltava); corrigido e instalado na 354. **Muralhas** e **Livro** começaram certos, o Livro levou o jogador para as páginas e quem caiu ganhou Fraqueza e Lentidão. Os outros 9 ainda não foram testados jogando.
- `depois.ps1` no PC: a cada RODAR, apaga do `.minecraft\command_history.txt` as linhas de `/login`, `/register` e `/changepassword`, a pedido do Paulo, para a senha não ficar no histórico.

## Ideias do Paulo
- Rainha Aranha: sobe no teto, joga 2 ovos que viram aranhas pequenas, desce e luta junto com elas; repete a cada ~20 s (entra no lugar da prisão de teia, que vai para a Viúva Negra).
- Gosma (slime): engole o jogador, que fica preso dentro dela e tem que bater para sair.
- Boss do vazio (Senhor do Vazio, salvo se o Paulo indicar outro): buraco negro que vai puxando o jogador por um tempo; quem cair dentro faz ele explodir e leva dano.

## Ideias aprovadas ("amei suas ideias, vamos adicionar na próxima atualização")
1. Capitão Caveira (Naufrágio): o navio afunda, a água sobe e a luta termina nos mastros.
2. Bibliotecário: puxa todos para dentro de um livro (parkour pelas páginas de papel); quem chega volta com bônus, quem cai volta fraco.
3. Lorde Vampiro: só leva dano no sol; o grupo quebra os vitrais para a luz entrar.
4. Minotauro: a arena vira um labirinto com paredes que mudam a cada 10 s; ele só fica vulnerável quando bate na parede.
5. Arquimago (Torre): a gravidade inverte e a luta continua no teto.
6. Senhor dos Ventos: o chão some e todos ficam em ilhas flutuantes, pulando entre elas com rajadas de vento.
7. Ceifador de Almas: quem morre vira alma e pode atrapalhar o chefe; se os amigos pegarem a alma, ele revive.
8. Relojoeiro: para o tempo e só um jogador se mexe; ele tem 5 s para quebrar a engrenagem que solta os outros.

## Ideia do Paulo (17/11, vídeo "um dos melhores boses do demon souls" — estilo Ídolo do Tolo)
- Bispo (Catedral): o chefe da arena é falso. Uma sacerdotisa escondida em outra sala da masmorra reza por ele: enquanto ela vive, o Bispo "morre" e volta com a vida cheia, e cria cópias dele. O grupo tem que achar e matar a sacerdotisa (ela tem guardas e um rastro de partículas/sino que leva até ela); depois disso o Bispo de verdade fica vulnerável e não revive mais.

## Missão (Warface) — pedidos do Paulo (17/11)
- Melhorar mais o mapa e deixar os jogadores entrarem nas casas para se esconder e atirar nos inimigos.
- Juggernaut: aumentar a área da luta para os jogadores terem onde se esconder (hoje fica muito difícil).
- Cara do escudo: hoje nenhum tiro dá dano (nem na cabeça, nem pelas costas). Deixar pequenas brechas para derrubar ele (costas, cabeça por cima do escudo, pernas...).
- Começo da partida de helicóptero: os jogadores descem/pulam do helicóptero para começar a jogatina.

## Como trabalhar (estado de 17/11, 07:20)
- Leia antes `ferramentas/COMECAR-AQUI-CORONA-SERVER.md` (regras do Paulo e proibições). Ele está desatualizado em alguns pontos: hoje o servidor roda no Linux 143.14.79.58 (corona-server.duckdns.org), e o PC "server1" (DESKTOP-JLG1NKU) só faz backup e serve de ponte.
- **Mandar comandos ao servidor:** grave o script em `C:\Users\server1\Documents\CoronaServer\passo.sh` (device_commit_files), dê duplo clique no atalho RODAR da área de trabalho e leia `saida.txt` na mesma pasta. Se existir `depois.ps1` ali, ele roda no PC no fim e se apaga sozinho. No fim, volte o `passo.sh` ao `ferramentas/instalar/passo-padrao.sh`.
- **Instalar jars:** `python3 ferramentas/instalar/mkpasso.py NUM saida.sh jar:Nome.jar:Plugin ...` gera o instalador. Com gente online, troque o final pelo de `final_reinicio_seguro.sh` (avisa na tela e reinicia) ou corte em `for i in $(seq 1 36)` para instalar só quando o servidor ficar vazio. Última instalação: **350** (17/11 14:27). Dá para compilar no próprio servidor: lá tem javac 25, e o classpath sai de `libraries`, `versions`, `cache` e `plugins/*.jar` (o Extras precisa do jar do Paper por causa das classes internas).
- **Testar:** monte um servidor de teste local (Paper 26.3 com os plugins do repositório) e use o plugin `ferramentas/testebot` (`fake entrar/ir/bater/socar/agachar/olhar/vida/info`, com registro de dano dos bots). Para forçar as mecânicas novas dos chefes: `/masmorras admin mecanica <nome>`; para a descida do gelo: `/masmorras admin descida <id>`.
- O código das mecânicas dos chefes está em `plugins/masmorras/src/br/servidor/masmorras/MecanicasChefes.java`, e a descida do gelo em `DescidaGelo.java`.

## 17/11 18:55 — Vaca cogumelo (BabyPets) e Discord
- Bug: pet mooshroom somava gravidade -0.03 por nível (ADD_NUMBER sobre a base 0.08), então a partir do nível 3 a gravidade ficava negativa e o jogador grudava no teto.
- Correção no servidor: `plugins/BabyPets/pets.yml`, GRAVITY `value_per_level: -0.0050` (nível 10 = -0.05 → gravidade 0.03, sempre positiva). Backup `pets.yml.bak-vaca-*`. Servidor reiniciado com 0 jogadores, BabyPets carregou 22 pets, 0 ERROR.
- Discord: cargo 🧪 Tester agora aparece separado na lista de membros e fica acima de 🎮 Jogador.

## 17/11 19:02 — Instalação 357 (o que era a "355")
- Poderes + Masmorras compilados das fontes atuais e instalados com 0 jogadores: chefes imunes a lento/congelamento das torres (Gelo e Tesla), endermen das masmorras sem teleporte, tag de cargo no TAB.
- Log: "ligou ok", 0 ERROR. O instalador avisou "linha da mochila nao bateu (nao mexi)": não mexeu em nada, só não aplicou aquele ajuste opcional.

## 17/11 19:10 — Redes
- Facebook: links sociais (Instagram, TikTok, YouTube) e Discord como segundo site.
- TikTok: capa nova no "A NOITE CHEGOU" (o speedrun já tinha capa).
- Story com o depoimento do Sui publicado no Facebook e no Instagram (link do Discord só aparece no Facebook).
- Lembretes nesta conversa: 20h20 hoje (reel do speedrun + bump) e todo dia às 19h20 até 26/11 para as capas do TikTok, mais 27/11 às 12h20.
