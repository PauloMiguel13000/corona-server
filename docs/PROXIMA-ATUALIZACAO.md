# Próxima atualização (combinado com o Paulo em 17/11/2026)

Ordem: primeiro o bug, depois os chefes. Um de cada vez, testado no servidor de teste, instalado com o servidor vazio.

## Bugs
- Pet vaca cogumelo (antigravidade): no nível 9 o jogador fica flutuando, não consegue pular e às vezes fica preso (relato do Sui no Discord).

## Relato do Sui (17/11, 12:26)
- Bug: os tiros das torretas (Engenheiro) ativam os efeitos da arma, por exemplo o "Eco" da relíquia Cidade Ancestral (dano alto + escuridão com chance a cada golpe). Torreta não pode ativar efeito de arma; o Paulo já disse que as habilidades não ficam fortes conforme a arma.
- Boss mundial (Colosso da Coroa Partida): está fácil para jogador forte (Sui matava com 3 torres, antes do buff de dano). Paulo decidiu (12:27): aumentar um pouco o dano dele e aumentar bastante a vida (HP), porque um jogador sozinho está matando muito fácil.

- Classe Engenheiro (torres): está fazendo qualquer boss com facilidade. Paulo decidiu (12:28) enfraquecer um pouco, SEM mexer na quantidade de torres, no tempo de recarga nem no dano. Sugestão dele: diminuir a vida (HP) do Engenheiro; ou outra fraqueza parecida (ex.: torres com vida e os monstros/chefe mirando nelas, chefe resistindo um pouco ao dano de torre). Apresentar a escolha a ele antes de instalar.

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
- **Instalar jars:** `python3 ferramentas/instalar/mkpasso.py NUM saida.sh jar:Nome.jar:Plugin ...` gera o instalador. Com gente online, troque o final pelo de `final_reinicio_seguro.sh` (avisa na tela e reinicia) ou corte em `for i in $(seq 1 36)` para instalar só quando o servidor ficar vazio. Última instalação: **347** (17/11 02:09).
- **Testar:** monte um servidor de teste local (Paper 26.3 com os plugins do repositório) e use o plugin `ferramentas/testebot` (`fake entrar/ir/bater/socar/agachar/olhar/vida/info`, com registro de dano dos bots). Para forçar as mecânicas novas dos chefes: `/masmorras admin mecanica <nome>`; para a descida do gelo: `/masmorras admin descida <id>`.
- O código das mecânicas dos chefes está em `plugins/masmorras/src/br/servidor/masmorras/MecanicasChefes.java`, e a descida do gelo em `DescidaGelo.java`.
