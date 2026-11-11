# Corona Server — comece por aqui

Arquivo para abrir uma conversa nova. Leia inteiro antes de agir. Estado em **11/11/2026, 00:45**.

---

## 1. Quem é o Paulo e como trabalhar com ele

- Paulo Miguel, de Salvador (BA). Dono do **Corona Server**, servidor público de Minecraft (Java + Bedrock).
- **Responda sempre em português**, em tom de conversa, direto e curto.
- **Faça tudo você mesmo**, de ponta a ponta: acessar o computador, instalar, reiniciar, testar. Ele não quer
  receber passo a passo para executar.
- **Uma melhoria pequena por vez, testada e entregue.** Ele prefere isso a um pacote grande pela metade.
- **Teste antes de dizer que está pronto.** Ele cobra isso: "sem bugs, teste tudo".
- **Mostre prévia antes de instalar qualquer coisa visual ou sonora.** Ele já rejeitou coisa instalada sem ver.
  (Rejeitou o boss duas vezes, a primeira trilha por ser lenta, e a música do chefe por estar fraca.)
- Quando algo for bloqueado pelo sistema, **não insista nem contorne**: diga a ele qual clique está faltando.

### Limite de uso (regra dele)
- Gaste pouco: respostas curtas, poucas capturas de tela e pequenas, agrupe passos previsíveis, não releia arquivos
  à toa, nada de subagentes ou pesquisa longa sem ele pedir.
- Você não enxerga a porcentagem; **pergunte a ele** em trabalhos longos.
- Perto de ~94% (ou no aviso de limite semanal): **pare**, deixe tudo em estado seguro e escreva a passagem do dia.
- Em 11/11 00:41 estava: **Fable 99%**, semanal 84%. Ambos reiniciam **domingo às 04:00**.

---

## 2. Proibições (não negociáveis)

- **Nunca mexer na máquina `NBTSJ-TI-01`.** Confira o nome do computador antes de agir.
- **Não digitar senhas, não criar contas, não fazer compras.** Essas ações são dele. O e-mail dele é privado.
- **Não ler nem mostrar** `plugins/CoronaLoja/config.yml` (token do Mercado Pago), tokens do Warden, chaves de API
  de sites de voto (`plugins/Extras/config.yml`), arquivos `.key`/`.pem`, nem as pastas `playit-config-SECRETO`
  e `anydesk-SECRETO`.
- **Não mudar o endereço do Bedrock** `prettyshore.wdn.gg` sem ele pedir.
- **Não clicar dentro da janela do console "Servidor de Minecraft".**
- **Apagar arquivo só com permissão dele.**
- **Não instalar jar de terceiros** sem ele pedir. Nada de mudanças de segurança/sistema no Windows.
- **Não mudar o horário das tarefas agendadas dele.**
- **Personagens de outros jogos: não.** Nem com outro nome, nem com outra cor, nem "só a skin". Vale para monstros,
  modelos 3D, texturas, ícones e imagens de divulgação. Monstro novo só se for **criado do zero**, com nome próprio
  e prévia antes. O que já está instalado fica como está.
- **Hotel e Fábrica: só mecânicas.** Nada de monstro novo nem de mudar o visual dos atuais. Os modelos 3D deles
  estão **desligados** (`modelo-3d: false`) por decisão dele — não religar.
- **Fechar o que abrir**: clientes de Minecraft, launchers, janelas do Explorador. Não fechar o Claude nem o AnyDesk.
- **Reinício do servidor só com aviso de 2 minutos** no jogo.

---

## 3. As máquinas

| Nome | O que é |
|---|---|
| `DESKTOP-JLG1NKU` | **PC do servidor** hoje. Pasta `C:\Users\server1\Documents\minecraft-server`. i3 10ª geração, 16 GB. |
| `EH-S25-25280` | **VPS nova** (EletricHost), Windows Server 2025, Xeon Gold 6138, 10 GB RAM, 100 GB. IP 79.182.4.169, acesso só por RDP. |
| `DESKTOP-0QEB1M0` | PC pessoal do Paulo. |
| `NBTSJ-TI-01` | Máquina do trabalho dele. **Nunca tocar.** |

**Atenção ao vínculo:** uma conversa nasce amarrada a um computador e **não dá para reapontar** (a documentação
confirma: não existe seletor de dispositivo para tarefa já criada). Para trabalhar numa máquina, a conversa precisa
ser aberta no app do Claude **daquela** máquina. Confirme sempre o nome antes de agir.

---

## 4. Como mexer no servidor (a receita que funciona)

O servidor roda em Windows. O jeito de instalar qualquer mudança é sempre o mesmo:

1. Monte o pacote em `otimizacao\ajustesNNN\` espelhando as pastas do servidor
   (ex.: `otimizacao\ajustes155\plugins\Extras-1.0.jar`).
2. Copie o `.bat` do ajuste anterior para a Área de Trabalho, troque `ajustesNNN` e o bloco de comentário
   `rem ajustesNNN:` até a linha `rem   Avisa 2 min`. **Codificação cp1252, quebras CRLF.**
3. Rode com **duplo clique pelo Explorador de Arquivos**. Ele avisa 2 minutos no jogo e reinicia.
4. Confira `otimizacao\ajustesNNN.log` (tem que terminar com "pronto: tudo conferido") e
   `logs\latest.log` ("Done (").

**Explorador de Arquivos**: peça acesso (`computer_resolve_access` + `computer_request_access`), ele vem no nível
"click" (só clique, sem digitar). Clique "Área de Trabalho" na lateral; para a lista atualizar depois de criar um
arquivo, clique em outra pasta e volte. **Sempre clique uma vez e confira com zoom antes do duplo clique** — as
linhas têm ~13 px e é fácil abrir o arquivo errado.

**Arquivos**: `device_stage_files` lê do PC, `device_commit_files` escreve. Jar grande (>25 MB) não vai por
`stagedPath`: mande antes com `SendUserFile` e depois use o `fileUuid`.

**Console do servidor**: escreva um `.txt` com o comando em `otimizacao\fila\`. Um atendedor
(`LIGAR-FILA-TESTE.bat` na Área de Trabalho) executa e a resposta sai em `logs\latest.log`. Ele dura ~2 h; se os
arquivos pararem de ser consumidos, religue com duplo clique.

**Servidor de teste**: existe um no contêiner em `/tmp/claude-0/teste` com bots (`fake entrar|cmd|inv|info|sair`).
Use antes de instalar. Cuidado: os bots **não andam**, então nada que dependa de `PlayerMoveEvent` pode ser testado
com eles — isso só em cliente real.

**Compilar**: `cd /tmp/claude-0/novo && bash compilar2.sh <pasta> <jar de saída>`, com `EXTRA_CP` montado a partir
de `/tmp/claude-0/teste/cp.txt`.

**Pacotes de textura**: as fontes são `fontes/coronapack/res/pack/corona-pack.zip` (Java) e `corona.mcpack`
(Bedrock). Depois de mudar o mcpack, **publique antes de instalar** no repositório público `corona-pacotes` como
`corona-<10 primeiros do sha1>.zip` — o plugin busca por link (jsDelivr) na inicialização. Versão atual: **1.14.1**
= `corona-158b2a55fa.zip`.

---

## 5. Onde está o código

- **Privado**: `github.com/PauloMiguel13000/corona-server` — `plugins/<nome>/`, `pacote/java`, `pacote/bedrock`,
  `sons/`, `vps/`, `ferramentas/`.
- **Público**: `github.com/PauloMiguel13000/corona-pacotes` — só os pacotes do Bedrock por link.
- No contêiner, o código vive em `/tmp/claude-0/novo/fontes` (some quando a sessão acaba).
- Commits terminam com as linhas de atribuição `Co-Authored-By:` e `Claude-Session:`.

---

## 6. O que está no ar (até o ajustes154)

O número do próximo ajuste é **155**.

**Hoje (10/11), do 131 ao 154:**
- `/darmoedareviver`, `/dararmadura`, `/darespada` (comandos de dono para testar itens)
- **Turno da Noite em grupo** com fila (`/turno fila`, `/turno coop`), até 3 pessoas
- **Missões do dia** (`/missoes`), **guia** (`/ajuda`), dicas automáticas
- **Boss mundial: o Colosso da Coroa Partida** — esqueleto invisível com corpo 3D de 8 peças animadas, vida própria
  (600 + 350 por jogador), barra de vida, 5 golpes (esmagar, investida, invocar guardas, chuva de magma e **golpe
  de montante**), nasce **só no mundo das masmorras**, nunca no survival nem no lobby de terror
- **Drops do boss**: peças da Armadura do Colosso e o **Montante do Colosso** (`/boss dar <nick> tudo` para testar)
- **Bússola do Colosso**: aparece quando ele nasce, aponta para ele, some quando morre ou vai embora
- **Asa do traje VIP** virou um bichinho voando em volta (Java); o Traje do Rei acompanha nas masmorras
- **Efeitos dos poderes no celular** refeitos com duas camadas de partículas
- **Portais de nível 2 a 5** no lado oeste do lobby das masmorras, travados pela média do equipamento
  (couro 1, pedra/ouro 2, ferro 3, diamante 4, netherite 5)
- **`command-suggestions: false` no Geyser** — era o que fazia comando com argumento ficar vermelho no celular
- **Trilha sonora das masmorras**: 11 músicas originais (`cripta`, `mina`, `floresta`, `gelo`, `mar`, `fogo`,
  `vazio`, `arcano`, mais `explorar`, `combate` e `chefe`), trocando sozinhas conforme a situação, com `/musica`
  para ligar e desligar. Geradas por `sons/gerar_trilhas.py` e `sons/gerar_masmorras.py` (numpy + scipy + ffmpeg).

**Backup do mundo** (11/11 00:00): `otimizacao\backup-mundo.ps1`, com `BACKUP-MUNDO-AGORA.bat` e
`AGENDAR-BACKUP-DO-MUNDO.bat` na Área de Trabalho. Manda `save-off`/`save-all flush`, copia `world` com robocopy
para uma pasta temporária, compacta a cópia, devolve `save-on` e guarda os 5 últimos em
`backup-corona-server\mundos`. Agendado todo dia às **05:40**. Testado: **1,5 GB em 2m39s** com o servidor no ar.

---

## 7. O que está pendente (palavras dele)

1. **Textura rosa/preta bugada** — ele reclamou do celular, mas **aparece no PC também**, perto dos pets e dos
   monstros invocados pelo boss. O pacote foi conferido inteiro e está íntegro, e o cliente baixa a versão certa.
   É alguma coisa que o servidor manda e o pacote não tem. **Falta ele mandar um print de perto** de um dos bichos.
2. **Ouvir a trilha no celular** — validado no Java (a legenda "Trilha das masmorras" apareceu na tela); no Bedrock
   só foi conferido o lado do servidor.
3. **Jogo de terror de domingo (15/11, 05:00)** — desenho aprovado, **não construído** (ele mandou não começar
   para não gastar limite). Ver seção 9.
4. **Mapa de survival apocalipse zumbi com armas por plugin** — depois do jogo de terror.
5. **Otimizar o servidor para celular**.
6. **Post das 18:00 no Discord**, todo dia, em #atualizações (canal `1569137434065633411`, servidor Corona Server),
   com tudo que foi feito no dia + lembrete do Traje do Rei (`/vip` e `/loja`). O do dia 10/11 já foi postado.

---

## 8. Migração para a VPS

**Situação:** a VPS está paga e ligada, com Windows Server 2025 (o plano anunciava Linux — vale cobrar).
Conclusão combinada com ele: o ganho é **rede e tempo no ar**, não desempenho — o i3 dele ganha do Xeon por núcleo,
e o servidor já pode usar 10 GB em casa (`memoria.bat` manda `XMX=10` e sobrepõe o `iniciar-servidor.bat`).

**Pendências de segurança (parte dele):** trocar a senha do Administrador (a que veio por mensagem está queimada),
criar usuário com outro nome e desativar o Administrador, e depois limitar o RDP (3389) ao IP dele.

**O kit está pronto em `vps/` no repositório privado:**
- `1-PREPARAR-VPS.ps1` — cria `C:\corona`, confere o Java, tira a máquina de suspensão, libera 25565/TCP e 19132/UDP
- `2-LIGAR-NO-BOOT.ps1` — faz o servidor subir junto com a VPS, como SYSTEM, sem depender de sessão RDP
- `3-BACKUP-NA-VPS.ps1` — backup diário às 05:40 lá também
- `LEIA-ME-MIGRACAO.md` — o roteiro completo

**Transferir os arquivos é parte dele** (o RDP já leva a área de transferência), porque nunca há duas máquinas
ligadas ao mesmo tempo. O mundo compactado dá ~1,5 GB.

**Ao sair do RDP: "Desconectar", nunca "Encerrar sessão"** — senão tudo que roda na sessão morre.

**Backup combinado:** zip diário na VPS + ele liga o PC de casa **toda sexta** e puxa os zips acumulados
(a casa está atrás de CGNAT, então quem puxa é o PC, não a VPS empurrando).

**Antes de decidir:** medir o ping para o endereço do playit e para `79.182.4.169`. A diferença é o que o túnel
custa hoje.

---

## 9. O jogo de terror — "O Apagão" (aprovado, não construído)

Evento único, domingo 15/11 às 05:00.

A energia do complexo cai. De **2 a 8 pessoas** entram juntas, no escuro total, com uma lanterna de bateria curta.
Para sair, o grupo precisa religar **5 geradores**. Cada gerador religado acende uma parte do lugar — e faz barulho.

- A criatura **caça por som**: correr e falar no chat fazem barulho, agachado é seguro.
- **Armários** para se esconder, com batimento cardíaco subindo quando ela chega perto.
- **Uma vida só**: quem morre vira espectador e tem direito a **um aviso** aos vivos.
- O **portão abre** com os 5 geradores. Quem sai leva coronas e um troféu exclusivo do evento.
- **Mapa novo**, construído do zero (não encostar no Hotel nem na Fábrica).
- **Monstro original**, criado do zero, com nome próprio e **prévia antes de instalar**.

Peças que já existem e dá para reaproveitar: câmeras e perseguição do Turno da Noite, props 3D, controle de luz,
sons sintetizados (`sons/`) e a animação de corpo do boss.

---

## 10. Primeira coisa a fazer numa conversa nova

1. Confirme o **nome do computador** ligado a você e diga em voz alta qual é.
2. Confirme que o servidor está no ar (`logs\latest.log` com "Done (").
3. Pergunte ao Paulo em quantos % está o limite antes de começar algo longo.
4. Só então comece — e uma coisa de cada vez.
