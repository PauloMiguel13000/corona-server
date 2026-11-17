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
- Prêmio do chefe no nível 3: só a calça vinha com Proteção III + Inquebrável II. Agora as 4 peças são iguais (ferro, P3 e Inq II) e a peça sai sorteada (`Premios.java`).
- Trilha: os monstros dão só **calça e botas**, sem elmo, no mesmo nível de antes (`Missoes.PECAS_RESTO`).

## Instalação 350 (17/11 14:27, 0 erros)
- **Engenheiro com metade da vida máxima** (modificador `poderes:engenheiro_vida` de -0.5, `Poderes.vidaEngenheiro`), fora dos mundos sem poderes. Conferido no ViboXact: vida máxima de 24 para 12.

## Testes feitos jogando (conta coronadooscar, Minecraft Java no PC ponte)
- Limite de 3 torres: OK. A 4ª é recusada e entra depois que uma some.
- Login automático: o serviço `corona-login-pc` no servidor faz `authme forcelogin coronadooscar` quando a conta entra pelo IP do PC ponte (177.8.228.129). Se o IP do PC mudar, rode de novo o script de instalação dele (o mesmo da passagem 12, que pega o IP do `SSH_CLIENT`).
- Vaso: o teste dentro da masmorra ficou para o Sui (ViboXact) fazer, clicando no vaso dele com a mão vazia.
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
