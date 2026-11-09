# Kit de teste do Corona Server (contêiner)

## Montar (sessão nova)
1. No PC: `C:\Users\server1\Documents\minecraft-server\otimizacao\claude\kit-teste\` → as partes
   `kit-teste-AAAA-MM-DD.tar.gz.parteNN` da data MAIS NOVA (16 partes de 18 MB; device_stage_files aceita todas numa
   chamada) e, em `otimizacao\claude\`, o `fontes-corona-*.zip` MAIS NOVO (pela data de modificação).
2. `cat kit-teste-*.tar.gz.parte* > /tmp/kit.tar.gz && sudo tar -xzf /tmp/kit.tar.gz -C /` (cria `/tmp/claude-0/teste`, `/tmp/claude-0/patch`, `/tmp/claude-0/cmp86`,
   `/tmp/claude-0/poderes/cp.txt` e `/mnt/user-data/uploads/minecraft-server/...` com os jars do classpath).
3. `cd /tmp/claude-0 && unzip -oq fontes-corona-*.zip` (cria `novo/fontes/<plugin>/{src,res}` e `novo/compilar2.sh`).
4. Java 25: `/usr/lib/jvm/java-25-openjdk-amd64/bin/java -version`; se faltar: `sudo apt-get update && sudo apt-get install -y openjdk-25-jdk-headless`.
   Python: `pip install --break-system-packages pillow numpy` (geradores de modelo/skin).
5. `cd /tmp/claude-0/teste && mkfifo in.fifo 2>/dev/null; (bash run.sh &)` → espera "Done (" no `out.log`.
   Mundos não vêm no kit: lobbies, spawn e jogos se montam sozinhos na 1ª vez (as masmorras geram aos poucos).

## Compilar
`EXTRA_CP="$(cat /tmp/claude-0/cmp86/extra_cp.txt)" bash /tmp/claude-0/novo/compilar2.sh <pasta> /caminho/ABSOLUTO/Plugin-1.0.jar`
(masmorras, turno, fabrica: acrescentar `:/tmp/claude-0/teste/libraries/com/google/code/gson/gson/2.14.0/gson-2.14.0.jar` no EXTRA_CP).
Nomes dos jars = os do PC (Extras-1.0.jar, Masmorras-1.0.jar, Poderes-1.0.jar, Hotel-1.0.jar ...).

## Testar
- Console: `./c.sh <comando>` (escreve no in.fifo); saída em `out.log`.
- Jogadores falsos (plugin TesteBot): `fake entrar NOME` (Java), `fake entrarb .NOME` (Bedrock/Floodgate),
  `fake cmd NOME register s1 s1` e `fake cmd NOME login s1` (AuthMe de mentira), `fake cmd NOME <comando>`,
  `fake ir NOME x y z` (teleporte), `fake andar NOME x y z` (dispara PlayerMoveEvent: portais), `fake interagir NOME`
  (clica na entidade mais perto: totens), `fake info|inv|ver|mao NOME`, `fake sair NOME`.
  Linhas do bot no log: `[BOT]`. Teleporte entre mundos é assíncrono: espere 1–2 s antes do `info`.
- Foto de cima/lado (plugin Vista): `vista <mundo> <cx> <cz> <raio> <ymin> <ymax> <arquivo.png> [lado]`.
- Pacote de recursos (textura roxa/preta, item invisível): SEMPRE antes de instalar
  `python3 -I novo/fontes/tools/conferir_pacote.py <corona-pack.zip> <corona.mcpack> novo/fontes` → tem que dar
  "OK: nenhum problema".
- Rede do contêiner: sites externos (MineSkin, Mojang) dão 403 — normal no teste.

## Instalar no servidor de verdade (PC desktop-jlg1nku)
- Pacote: `otimizacao\ajustesNNN\plugins\*.jar` (número seguinte ao maior que existir) + `.bat` na Área de Trabalho
  copiado do último `INSTALAR-AJUSTES*.bat` (CRLF, cp1252; avisa 2 min na tela, roda `instalar-pacote.ps1 -Nome ajustesNNN -Forcar`,
  religa o servidor e o MEDIDOR-REDE).
- Rodar: computer use → `computer_resolve_access(["File Explorer"])` + `computer_request_access` (nome no PC:
  "Explorador de Arquivos", nível click) → `computer_open_application("Explorador de Arquivos")` → "Área de Trabalho"
  no painel esquerdo → zoom para achar a linha → duplo clique. NUNCA clicar na janela do console "Servidor de Minecraft"
  nem na janela preta do instalador. A lista reordena quando arquivos mudam: tire foto antes de clicar.
- Conferir: `otimizacao\ajustesNNN.log` ("pronto: tudo conferido") e `logs\latest.log` ("Done (").

## Gravar no PC (device_commit_files)
- Arquivo maior que ~19 MB falha ("HTTP 400"): dividir com `split -b 18m -d -a 2 arq arq.parte` e gravar as partes.
- Às vezes o PC fica com a versão ANTIGA de um arquivo de mesmo nome: use nome novo a cada vez (data/letra) e confira
  o tamanho com device_list_dir (ou traga de volta e compare o sha256).
