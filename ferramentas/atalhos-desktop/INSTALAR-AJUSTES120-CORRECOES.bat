@echo off
setlocal
rem ajustes120: correcoes da varredura de bugs: itens voltam para a barra ao sair da arena (Poderes), aura VIP desligada
rem   nos jogos de terror (Extras), susto do Turno na altura dos olhos, pets em portugues (BabyPets lang_pt_BR) e
rem   ClearLag sem apagar itens dos minigames nem avisar em ingles la dentro.
rem   Avisa 2 min e reinicia MESMO com gente online.
set "S=C:\Users\server1\Documents\minecraft-server"
set "O=%S%\otimizacao"
set "L=%O%\ajustes120.log"
set "C=powershell -NoProfile -ExecutionPolicy Bypass -File "%O%\comando.ps1""
echo %date% %time% aviso de reinicio (2 min) >> "%L%"
%C% "title @a times 10 100 20"
%C% "title @a subtitle {text:'Correcoes e pets em portugues! Voltem em 2 minutos',color:yellow}"
%C% "title @a title {text:'REINICIANDO EM 2 MINUTOS',color:red,bold:true}"
%C% "say REINICIANDO EM 2 MINUTOS - atualizacao rapida, voltem logo em seguida"
timeout /t 60 /nobreak >nul
%C% "title @a title {text:'REINICIANDO EM 1 MINUTO',color:red,bold:true}"
%C% "say REINICIANDO EM 1 MINUTO"
timeout /t 30 /nobreak >nul
%C% "title @a title {text:'REINICIANDO EM 30s',color:red,bold:true}"
%C% "say REINICIANDO EM 30 SEGUNDOS"
timeout /t 20 /nobreak >nul
%C% "title @a title {text:'REINICIANDO EM 10s',color:red,bold:true}"
timeout /t 10 /nobreak >nul
powershell -NoProfile -ExecutionPolicy Bypass -File "%O%\instalar-pacote.ps1" -Nome ajustes120 -Forcar
echo %date% %time% instalador terminou com codigo %errorlevel% >> "%L%"
timeout /t 3 /nobreak >nul
powershell -NoProfile -Command "if (Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.Path -like '*\minecraft-server\runtime\*' }) { exit 0 } else { exit 1 }"
if errorlevel 1 start "Servidor de Minecraft" /D "%S%" "%S%\iniciar-servidor.bat"
call "C:\Users\server1\Desktop\MEDIDOR-REDE.bat"
echo %date% %time% fim >> "%L%"
