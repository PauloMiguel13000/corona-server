# PASSAGEM — Corona Server — 10/11/2026 (19:40)

Resumo de 1 página. Detalhes antigos: `PASSAGEM-ADENDO-2026-11-09k.md` (mesma pasta).

## Estado atual
- No ar: até o **ajustes151**. Próximo número: **ajustes152**.
- Conferir sempre `otimizacao\ajustesNNN.log` ("pronto: tudo conferido") e `logs\latest.log` ("Done (").
- GitHub: `PauloMiguel13000/corona-server` (privado) em dia até o 150; `corona-pacotes` (público) com o mcpack
  **1.13.9** = `corona-27f7d6e240.zip`.
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
