# PASSAGEM — Corona Server — 10/11/2026 (19:40)

Resumo de 1 página. Detalhes antigos: `PASSAGEM-ADENDO-2026-11-09k.md` (mesma pasta).

## Estado atual
- No ar: até o **ajustes154**. Próximo número: **ajustes155**.
- Conferir sempre `otimizacao\ajustesNNN.log` ("pronto: tudo conferido") e `logs\latest.log` ("Done (").
- GitHub: `PauloMiguel13000/corona-server` (privado) em dia até o 150; `corona-pacotes` (público) com o mcpack
  **1.14.1** = `corona-158b2a55fa.zip`.
- Fica aberta 1 janela do Explorador de Arquivos no PC do servidor (**fechar**) e o atendedor de comandos
  (`LIGAR-FILA-TESTE`, ligado 19:28, dura ~2 h): escrever `.txt` em `otimizacao\fila\` e ler a resposta em `logs\latest.log`.

## Como conectar
- PC `desktop-jlg1nku` (NÃO é a NBTSJ), pasta `C:\Users\server1\Documents\minecraft-server`.
- Acesso: `computer_resolve_access` + `computer_request_access` para "Explorador de Arquivos" (expira em 30 min sem uso).
- Arquivos do PC: `device_stage_files` (ler) e `device_commit_files` (escrever). Jar grande (>25 MB) só com
  `SendUserFile` primeiro e depois `fileUuid`.
- Código: `/tmp/claude-0/novo/fontes` (some com a sessão) e no repo privado (`plugins/<nome>`).

## Receita que funciona
- Pacote em `otimizacao\ajustesNNN\` (espelha as pastas do servidor) + `.bat` na Área de Trabalho copiado do anterior
  (cp1252, CRLF; trocar `ajustesNNN` e o bloco `rem ajustesNNN:` até `rem   Avisa 2 min`). Rodar com 2 cliques.
- Explorador: "Área de Trabalho" (58,80); para a lista atualizar, clicar em Downloads (58,98) e voltar; rolar 10 para
  baixo; sempre clicar 1x e conferir com zoom antes do duplo clique (as linhas têm ~13 px).
- Pacote Bedrock novo: publicar `corona-<10 do sha1>.zip` em `corona-pacotes` **antes** de instalar; mcpack gerado com
  `fontes/coronapack/bedrock_v2.py` + `tools/mesclar_pacote.py <base> <pasta> <saida> <versao>`; conferir com
  `tools/conferir_pacote.py`.

## Feito hoje (10/11), do 131 ao 151
131 `/darmoedareviver` · 132 `/dararmadura` e `/darespada` · 133/140/141 Turno coop com fila (`/turno fila`)
134/135 monstros 3D Fábrica/Hotel → **desligados** no 136 (`modelo-3d: false`, não religar) · 137 missões do dia
138 `/ajuda` · 139 dicas automáticas · 142/143 **boss mundial** (Colosso da Coroa Partida, 8 peças animadas)
144 asa VIP virou bichinho voando · 145 boss só no mundo das masmorras · 146 Traje do Rei nas masmorras
147 **drops do boss**: armadura e Montante do Colosso · 148 **bússola do Colosso** + **golpe de montante**
149 **efeitos dos poderes no celular** (2ª camada por categoria, pacote 1.13.9) · 150 **portais dos níveis 2 a 5** no
lobby das masmorras (trava pela média do equipamento) · 151 `command-suggestions: false` no Geyser (comando com
argumento ficava vermelho no celular).

## Feito à noite (10/11)
152 corredor na frente dos portais (tinham nascido atrás das paredes do lobby) · 153 **trilha sonora das masmorras**:
11 músicas originais sintetizadas (`sons/gerar_trilhas.py` e `sons/gerar_masmorras.py` no repo), uma por família de
masmorra, com troca automática para `combate` (2+ monstros perto) e `chefe` (na arena, chefe vivo) e `/musica` para
ligar/desligar · 154 música do chefe refeita mais agitada (162 bpm).
Validado em cliente Java real: a legenda "Trilha das masmorras" apareceu na tela, e o portal de nível 2 funciona
andando. No Bedrock só foi conferido o lado do servidor (pacote 1.14.1 publicado e servido) — falta ouvir no celular.

## Backup do mundo (feito em 11/11 00:00)
`otimizacao\backup-mundo.ps1` + `BACKUP-MUNDO-AGORA.bat` e `AGENDAR-BACKUP-DO-MUNDO.bat` na Área de Trabalho.
Manda `save-off`/`save-all flush`, copia `world` com robocopy para uma pasta temporária (robocopy lida com arquivo
em uso; tar direto na pasta viva falha), compacta a cópia, devolve `save-on` e guarda os 5 últimos em
`backup-corona-server\mundos`. Tarefa "Corona - backup do mundo" agendada todo dia às 05:40 (logon interativo).
**Testado:** `mundo-2026-11-10_2357.zip`, 1,5 GB em 2m39s, com o servidor no ar.

## Jogo de terror de domingo (15/11, 05:00) — desenho aprovado, NÃO construído
"**O Apagão**": 2 a 8 pessoas no escuro, lanterna com bateria, religar **5 geradores** para abrir o portão.
A criatura **caça por som** (correr e falar fazem barulho; agachado é seguro). Armários para esconder, batimento
cardíaco subindo quando ela chega perto. **Uma vida só**: quem morre vira espectador e tem direito a **um aviso**.
Mapa **novo**, construído do zero (não encostar no Hotel nem na Fábrica). Monstro **original**, com prévia antes.
Paulo aprovou o desenho ("PERFEITO") e mandou **não começar a construir** para não gastar limite.

## Vínculo de computador (problema aberto)
Esta conversa nasceu amarrada ao PC de casa e **não dá para reapontar** — a documentação confirma que não existe
seletor e que uma tarefa criada não troca de computador. Para trabalhar na VPS é preciso **abrir conversa nova no
app do Claude que roda dentro dela**. Máquinas: `DESKTOP-JLG1NKU` (servidor atual), `EH-S25-25280` (VPS),
`DESKTOP-0QEB1M0` (PC pessoal do Paulo), `NBTSJ-TI-01` (**nunca tocar**).

## Migração para VPS (plano do Paulo, 10/11 22:37)
Ele cogita uma VPS da EletricHost (R$65 com desconto; 10 GB, 5 vCPU Xeon Gold 6138, 100 GB, 1 Gbps, Brasil).
Conclusão combinada: o ganho é **rede e uptime**, não desempenho — o i3 de 10ª dele ganha do Xeon por núcleo, e o
servidor já roda com `XMX=10` (está no `memoria.bat`, que sobrepõe o `iniciar-servidor.bat`). Quando migrar:
guardar o token, deixar no endereço antigo do playit um aviso com o IP novo, atualizar os sites, e ficar com
**um IP para Java e outro para Bedrock**.

## Pendências (palavras do Paulo)
- **Texturas bugadas no celular**: print mostra um boneco preto/rosa (textura faltando) e o item da mão quebrado, em
  2,82,-26. O pacote está íntegro (`conferir_pacote.py` OK). Falta ele dizer **que boneco é aquele**.
- **Post das 18:00 no Discord** (#atualizações, canal 1569137434065633411) — atrasado, texto pronto na conversa.
  Precisa saber se o Chrome conectado é o da NBTSJ (se for, não usar).
- Testar em cliente real: portais (o bot não anda, então o `PlayerMoveEvent` não dá para testar com bot), bússola do
  boss, armadura e montante do Colosso, efeitos novos no celular.
- Fila: música nas dungeons e sons de espada/poderes; otimizar o servidor para celular; jogo de terror próprio
  (domingo 15/11 05:00, monstros originais, combinar as regras antes); mapa survival apocalipse zumbi com armas.

## Proibições
- Nunca mexer na NBTSJ (`nbtsj-ti-01`); conferir o nome do PC. Não usar a extensão do Chrome se estiver nela.
- Não digitar senhas, criar contas nem comprar. E-mail do Paulo é privado. Não mudar horário das tarefas agendadas.
- Não ler/mostrar `plugins/CoronaLoja/config.yml`, tokens, chaves, pastas `*-SECRETO`. Não mudar `prettyshore.wdn.gg`.
- Sem jar de terceiros sem ele pedir. Não clicar na janela "Servidor de Minecraft". Apagar arquivo só com permissão.
- Monstros: não recriar personagem de outro jogo (nem com outro nome/cor). **Hotel: só mecânicas**.
- Fechar clientes de Minecraft, launchers e janelas do Explorador que abrir. Reinício só com aviso de 2 min.
