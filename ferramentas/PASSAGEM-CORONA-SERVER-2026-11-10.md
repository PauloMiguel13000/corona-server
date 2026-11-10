# PASSAGEM — Corona Server — 2026-11-10 (12:30)

Detalhes completos: `PASSAGEM-ADENDO-2026-11-09k.md` (mesma pasta) e os adendos anteriores. Este é o resumo de 1 página.

## Estado atual
- No ar: até o **ajustes142** (instalador lançado 14:12; conferir `otimizacao\ajustes142.log` = "pronto: tudo conferido"
  e `logs\latest.log` = "Done ("). Próximo número: **ajustes143**.
- GitHub: `PauloMiguel13000/corona-server` (privado) em dia; `corona-pacotes` (público) com o mcpack 1.13.5.
- Nada pela metade. Pode ter ficado 1 janela do Explorador de Arquivos aberta no PC do servidor (fechar).

## Como conectar
- PC `desktop-jlg1nku` (NÃO é a NBTSJ), pasta `C:\Users\server1\Documents\minecraft-server`.
- Acesso ao computador: pedir "Explorador de Arquivos" (expira em 30 min sem uso).
- Código: no contêiner `/tmp/claude-0/novo/fontes` (some com a sessão) e no repo privado (`plugins/<nome>`).

## Receita que funciona
- Pacote em `otimizacao\ajustesNNN\` (espelha as pastas do servidor) + `.bat` na Área de Trabalho copiado do anterior
  (CRLF, cp1252). Executar com 2 cliques no Explorador: avisa 2 min e reinicia.
- Explorador: abrir, clicar "Área de Trabalho" (58,80), rolar 10 para baixo; os instaladores ficam em ordem
  (linhas de 13 px; o 139 estava em y≈461). Fechar a janela depois: X em (1554,8) com ela maximizada.
- Esperas: `sleep` em segundo plano (~185 s por reinício). Testar antes no servidor de teste do contêiner com TesteBot.
- Pacote Bedrock novo: publicar `corona-<10 do sha1>.zip` em `corona-pacotes` ANTES de instalar.

## Feito hoje (10/11)
- 131 `/darmoedareviver` · 132 `/dararmadura` e `/darespada` · 133 Turno da Noite coop (`/turno coop <nick>`, até 3)
- 134/135 monstros 3D da Fábrica e do Hotel → **desligados** no 136 a pedido do Paulo (`modelo-3d: false`)
- 140/141 Turno coop com fila (`/turno fila`, entra todo mundo junto) · 137 missões do dia (`/missoes`) · 138 guia (`/ajuda`) · 139 dicas automáticas das novidades
- Conferido que o Paulo não perdeu itens (mochilas survival e masmorras inteiras).

## Pendências (palavras do Paulo)
- "continue melhorando tudo, eu quero esse servidor foda" — fila: efeitos dos poderes das classes, pets, acabamento do
  spawn, mapa das masmorras. Melhorias pequenas, uma por vez.
- Post das 18:00 no Discord (#atualizações) com tudo de hoje + lembrete do Traje do Rei: lembrete agendado para 17:50.
  NÃO anunciar monstro 3D do Hotel/Fábrica. A extensão do Chrome só pode ser usada se NÃO estiver na NBTSJ.
- Coop do Turno e os menus novos não foram vistos em cliente real (só bots no servidor de teste).

## Proibições
- Nunca mexer na NBTSJ (`nbtsj-ti-01`); conferir o nome do PC antes. Não usar a extensão do Chrome se estiver nela.
- Não digitar senhas, criar contas nem comprar. E-mail do Paulo é privado. Não mudar horário das tarefas agendadas.
- Não ler/mostrar `plugins/CoronaLoja/config.yml`, tokens, chaves, pastas `*-SECRETO`. Não mudar `prettyshore.wdn.gg`.
- Sem jar de terceiros sem o Paulo pedir. Não clicar na janela "Servidor de Minecraft". Apagar arquivo só com permissão.
- Monstros: não recriar personagem de outro jogo (nem com outro nome/cor). **Hotel: só mecânicas**, sem monstro novo.
- Fechar clientes de Minecraft, launchers e janelas do Explorador que abrir. Reinício só com aviso de 2 min.
