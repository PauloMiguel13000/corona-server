# Corona Server: passagem de 17/11/2026

Este arquivo é para o Claude que vai trabalhar no Corona Server esta semana. Leia inteiro antes de agir. Ele junta o que já foi feito, como trabalhar e tudo o que o Paulo pediu e ainda falta fazer.

---

## 1. Como trabalhar com o Paulo

- Responda **sempre em português**, curto e em tom de conversa.
- **Faça tudo você mesmo**, de ponta a ponta: mexer no PC, instalar, reiniciar, testar. Não mande passo a passo para ele executar.
- **Uma melhoria pequena por vez, testada e entregue.** Ele prefere isso a um pacote grande pela metade.
- **Teste antes de dizer que está pronto.** "Sem bugs, teste tudo." Diga claramente o que não deu para testar.
- **Gaste pouco do limite dele:**
  - respostas curtas
  - poucas capturas de tela, e pequenas
  - passos previsíveis agrupados
  - nada de subagentes nem pesquisa longa sem ele pedir
- **O limite de uso:** você não vê a porcentagem. Em trabalho longo, pergunte. Perto de 94%, pare, deixe tudo em estado seguro, escreva `PASSAGEM-CORONA-SERVER-<data>.md` na pasta do projeto no PC e entregue no chat.
- **Ao final de cada rodada**, mande o arquivo de novidades do dia (`NOVIDADES-<data>.md`), que ele repassa para outro chat.
- Quando ele manda trabalhar no servidor, ele quer que você faça, teste e atualize sem ficar mandando resumo no meio.

## 2. Proibições (não negociáveis)

- **Nunca mexa na máquina `NBTSJ-TI-01`.** Confira o nome do PC antes de agir. O PC certo é o **DESKTOP-JLG1NKU** (usuário `server1`).
- Não digite senhas, não crie contas, não compre nada. O e-mail dele é privado.
- **Nunca leia, mostre ou guarde** nenhum destes:
  - `plugins/CoronaLoja/config.yml`
  - `plugins/Extras/config.yml` de produção
  - pastas `SECRETO-*`
  - arquivos `.key` e `.pem`
  - tokens
- **Backup antes de qualquer mudança.**
- **No servidor, uma mudança só sobe com 0 jogadores online.** A outra opção é o aviso na tela antes de reiniciar, se ele pedir.
- **Discord:** só poste com o OK dele, exceto o post diário das 18:00 em #atualizações. Sempre confira se nada foi postado repetido.
- Se o sistema bloquear uma ação, não insista: diga a ele qual clique falta.
- **Ao terminar no PC:**
  - feche tudo o que abriu (Minecraft, janelas extras) e deixe só Claude, AnyDesk, Chrome Remote Desktop e Syncthing
  - não interaja com a janela do app do Claude
  - não entre em servidores de outras pessoas
- **Sites:** use o navegador embutido do app. O Chrome dele só serve para resolver captcha.
- Não mude os horários das tarefas agendadas dele.
- Toda mudança tem que funcionar no **Java e no Bedrock**.
- Nada de design com direitos autorais do Warface.

## 3. Onde as coisas estão

- **Servidor:** Linux 143.14.79.58 (`corona-server.duckdns.org`), Paper 26.3 com Geyser/Floodgate. Java na porta 25565, Bedrock na 19132. A memória do Java está em 7 GB, por decisão do Paulo.
- **Repositório privado:** `PauloMiguel13000/corona-server`. Pastas importantes:
  - `plugins/` — código dos plugins
  - `ferramentas/` — ferramentas e documentos
  - `docs/PROXIMA-ATUALIZACAO.md` — a mesma lista desta passagem
- **PC ponte:** `C:\Users\server1\Documents\CoronaServer`. Para mandar comandos ao servidor:
  1. grave o script em `passo.sh` (device_commit_files)
  2. dê duplo clique no atalho **RODAR** da área de trabalho
  3. leia o `saida.txt` da mesma pasta
  4. se houver um `depois.ps1` na pasta, ele roda no PC no fim e se apaga sozinho
  5. no fim, volte o `passo.sh` ao `ferramentas/instalar/passo-padrao.sh`
  
  O RODAR fica no meio da área de trabalho, então o Minecraft não pode estar cobrindo a tela.
- **Instalar jars:** `python3 ferramentas/instalar/mkpasso.py NUM saida.sh /caminho/X.jar:X.jar:NomeDoPlugin ...` gera o instalador.
  - **Servidor vazio:** corte o script em `for i in $(seq 1 36)` e ele instala sozinho quando o servidor ficar vazio.
  - **Com aviso na tela:** use o final de `ferramentas/instalar/final_reinicio_seguro.sh`.
  - **Agendamento:** uma instalação nova cancela qualquer instalação que ainda estava agendada.
- **Compilar:** `ferramentas/compilar.sh`. É Java com `--release 21` e o classpath do Paper.
- **Testar:** use um servidor de teste local com o plugin `ferramentas/testebot`. Comandos `fake`:
  - `entrar`, `sair`, `ir`, `olhar`, `agachar`
  - `bater` (golpe na entidade mais próxima)
  - `socar x y z` (clique esquerdo num bloco)
  - `quebrar`, `pisar`, `chat`, `cmd`
  - `vida N`, `info`, `fatia` (corte de blocos)

  Ele também registra o dano que os bots levam, nas linhas `DANOBOT`. Os bots não têm física: não andam com empurrão nem tiram efeitos de poção.
- **Forçar mecânicas no teste:**
  - mecânicas novas dos chefes: `/masmorras admin mecanica <nome[:estilo|:bloco]>`
  - descida do gelo: `/masmorras admin descida <id>`
  - reiniciar uma masmorra: `/masmorras admin reset <id>`
- **Arquivos de dados no servidor** (pasta `plugins/`):
  - `Masmorras/missoes.yml` — progresso do nível de masmorra
  - `Poderes/qualidade.yml` — estrelas dos pets
  - `Extras/boss-mundial.txt` — próximo boss mundial
  - `SetHome/homes.yml` — casas dos jogadores

## 3.1 Como conectar no servidor Linux

Não existe acesso direto da nuvem do Claude ao servidor: a rede da sessão bloqueia SSH e a chave **não pode sair do PC**. Tudo passa pelo PC DESKTOP-JLG1NKU.

1. No PC DESKTOP-JLG1NKU (usuário `server1`), o app Claude para desktop precisa estar aberto e logado, com **Uso do computador** ligado em Configurações → Este computador.
2. A conversa precisa estar **ligada a esse PC**: abrir a conversa pelo app nesse PC e escolher "Vincular a este computador", ou escolher o PC em Dispositivos. Libere as pastas `C:\Users\server1\Documents\CoronaServer` e `C:\Users\server1\Desktop`.
3. Para rodar algo no servidor:
   - grave os comandos em `Documents\CoronaServer\passo.sh` (device_commit_files)
   - peça acesso ao "Explorador de Arquivos" (computer_resolve_access e depois computer_request_access)
   - dê duplo clique em **RODAR-SERVIDOR** na área de trabalho
   - leia o `saida.txt` (device_stage_files)
4. O RODAR chama `Documents\CoronaServer\rodar.ps1`, que faz SSH em `root@143.14.79.58` com a chave do próprio PC (`%USERPROFILE%\.ssh\corona_pc`). Nunca leia, copie ou mostre essa chave.
5. Se o SSH falhar por chave, existe o `1-CONFIGURAR-SSH.bat` na área de trabalho. Se ele pedir senha, quem digita é o Paulo.
6. O RODAR fica no meio da área de trabalho. Se o Minecraft ou outra janela estiver cobrindo, clique em "Mostrar área de trabalho", o cantinho direito da barra de tarefas.

## 4. O que já foi feito e instalado (até a instalação 347, em 17/11 às 02:09, sem erros)

- **Nível de Masmorra próprio**, separado do XP:
  - é preciso fazer todas as masmorras do nível 1 para liberar o nível 2, e assim por diante (Nether no 3, End no 4, até o 5)
  - o placar na tela mostra quantas masmorras faltam e quais
  - quem não tem o nível não entra na área nem no portal
  - os portais levam a uma das masmorras que faltam
- **Ordem das salas:** a próxima sala, e a arena do chefe, só abre com a anterior limpa. Os monstros que sobram brilham, vêm até a sala depois de 2,5 min e somem depois de 5 min.
- **Casas no mundo das masmorras:**
  - não dá para marcar `/sethome` a menos de 64 blocos de uma masmorra
  - no máximo 2 casas (VIP: 3)
- **Pets com estrelas** de 1 a 5, aparecendo também no `/pets`.
- **Comandos escondidos** (com ":", plugins e afins) para quem não é admin.
- **Boss mundial** volta a aparecer depois de reiniciar.
- **Torretas do Engenheiro** ficam mais fortes com o nível. As habilidades **não** ficam mais fortes conforme a arma; o Paulo não quer isso.
- **Rei do Gelo:** descida de barco na pista de gelo, desviando de bolas de neve, e depois a fase 2 numa arena de gelo com estalactites. Vídeo gravado: `rei_do_gelo.mp4`.
- **Mecânicas próprias para os chefes:** 15 tipos, no arquivo `MecanicasChefes.java`:
  - sombras, prisão, chão marcado, silêncio, olhar
  - chuva, investida, ondas, lâmina, escudo
  - armadilhas, bomba, redemoinho, elo, rastro

  Nível 1 e 2 têm uma mecânica; do nível 3 para cima, duas que se revezam. Gelo, Trovão e Herobrine continuam com a fase própria.
- **NPC JOGAR** no spawn, no caminho do leste.
- **Molde de melhoria de netherite** aparece a partir do nível 4.

## 5. O que falta fazer esta semana

Faça nesta ordem, um item de cada vez: testado no servidor de teste, com backup e instalado com o servidor vazio.

### Bugs
1. **Pet vaca cogumelo (antigravidade):** no nível 9 o jogador fica flutuando, não consegue pular e às vezes fica preso. Relato do Sui no Discord.
2. **Torretas ativando efeitos da arma:** os tiros do Engenheiro ativam o "Eco" da relíquia Cidade Ancestral (dano alto e escuridão) e outros efeitos de golpe, multiplicando o dano. Torreta não pode ativar efeito de arma.

### Balanceamento
3. **Boss mundial** (Colosso da Coroa Partida): aumentar **um pouco o dano** e **bastante a vida**, porque um jogador sozinho está matando com facilidade.
4. **Engenheiro:** enfraquecer um pouco. **Não mexa** na quantidade de torres, no tempo de recarga nem no dano.
   - A ideia do Paulo é diminuir a vida do próprio Engenheiro.
   - Se precisar, pode aumentar **só um pouco** o tempo para invocar as torretas.
   - Mostre a ele como ficou antes de instalar.

### Chefes: ideias do Paulo
5. **Rainha Aranha:** a cada ~20 s ela sobe no teto, joga 2 ovos que viram aranhas pequenas, desce e luta junto com elas. Isso substitui a prisão de teia dela, que passa para a Viúva Negra.
6. **Gosma (slime):** engole o jogador, que fica preso dentro dela e tem que ficar batendo para sair. Se tiver uma ideia melhor, pode trocar.
7. **Boss do vazio** (Senhor do Vazio, se ele não indicar outro): um buraco negro que puxa o jogador por um tempo. O jogador consegue correr um pouco, mas continua sendo puxado. Se cair dentro, o buraco explode e dá dano.
8. **Bispo como chefe falso:** vem de um vídeo que ele mandou, "um dos melhores boses do demon souls", no estilo do Ídolo do Tolo. É um chefe **como** o falso, e não "matar um chefe falso".
   - O Bispo da arena é falso.
   - Uma sacerdotisa escondida em outra sala reza por ele.
   - Enquanto ela vive, ele "morre", volta com a vida cheia e cria cópias.
   - O grupo tem que achar e matar a sacerdotisa, guiado por um rastro ou um sino. Só depois ele morre de verdade.

### Chefes: ideias que o Paulo aprovou ("amei suas ideias")
9. **Capitão Caveira (Naufrágio):** o navio afunda, a água sobe e a luta termina nos mastros.
10. **Bibliotecário:** puxa todos para dentro de um livro, com parkour pelas páginas de papel. Quem chega ao fim volta com bônus; quem cai volta fraco.
11. **Lorde Vampiro:** só leva dano no sol. O grupo quebra os vitrais para a luz entrar.
12. **Minotauro:** a arena vira um labirinto com paredes que mudam a cada 10 s. Ele só fica vulnerável quando bate na parede.
13. **Arquimago (Torre):** a gravidade inverte e a luta continua no teto.
14. **Senhor dos Ventos:** o chão some e todos ficam em ilhas flutuantes, pulando entre elas com rajadas de vento.
15. **Ceifador de Almas:** quem morre vira alma e pode atrapalhar o chefe. Se os amigos pegarem a alma, ele revive.
16. **Relojoeiro:** para o tempo e só um jogador se mexe. Ele tem 5 s para quebrar a engrenagem que solta os outros.

### Missão (modo estilo Warface)
17. Melhorar mais o mapa e deixar os jogadores **entrarem nas casas** para se esconder e atirar nos inimigos.
18. **Juggernaut:** aumentar a área da luta para os jogadores terem onde se esconder. Hoje fica difícil demais.
19. **Cara do escudo:** hoje nenhum tiro dá dano, nem na cabeça nem pelas costas. Deixe **pequenas brechas** para derrubá-lo: costas, cabeça por cima do escudo, pernas.
20. **Começo de helicóptero:** os jogadores descem ou pulam do helicóptero para começar a partida.

### Pendentes antigos
- **Sites de votação:** o Paulo precisa fazer o login neles; isso é com ele.
- **Agenda:** o Paulo pediu para tudo isto rodar numa tarefa agendada para domingo 22/11 às 04:00, quando o limite dele reinicia. Pelo app, a criação foi bloqueada pelo modo automático. Se este Claude conseguir, crie a tarefa. Se não, avise que ele precisa liberar a permissão ou criar a tarefa no app.

## 6. Ao terminar cada item

- Faça commit e push no repositório.
- Atualize `docs/PROXIMA-ATUALIZACAO.md`, riscando o que foi feito.
- No fim da rodada, mande `NOVIDADES-<data>.md` ao Paulo.
- Feche no PC tudo o que abriu.
