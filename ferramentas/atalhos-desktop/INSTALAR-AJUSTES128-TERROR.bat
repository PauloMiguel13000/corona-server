@echo off
setlocal
rem ajustes128: Lobby do Terror - coroa sombria flutuando sobre o obelisco, ilhotas mortas em volta e estatuas 3D dos
rem   monstros (Criatura, Vovo, capivara e onca do Turno) ao lado dos portoes, so para quem tem o pacote.
rem   Avisa 2 min e reinicia MESMO com gente online.
set "S=C:\Users\server1\Documents\minecraft-server"
set "O=%S%\otimizacao"
set "L=%O%\ajustes128.log"
set "C=powershell -NoProfile -ExecutionPolicy Bypass -File "%O%\comando.ps1""
echo %date% %time% aviso de reinicio (2 min) >> "%L%"
%C% "title @a times 10 100 20"
%C% "title @a subtitle {text:'Lobby do Terror novo! Voltem em 2 minutos',color:yellow}"
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
powershell -NoProfile -ExecutionPolicy Bypass -File "%O%\instalar-pacote.ps1" -Nome ajustes128 -Forcar
echo %date% %time% instalador terminou com codigo %errorlevel% >> "%L%"
timeout /t 3 /nobreak >nul
powershell -NoProfile -Command "if (Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.Path -like '*\minecraft-server\runtime\*' }) { exit 0 } else { exit 1 }"
if errorlevel 1 start "Servidor de Minecraft" /D "%S%" "%S%\iniciar-servidor.bat"
call "C:\Users\server1\Desktop\MEDIDOR-REDE.bat"
echo %date% %time% fim >> "%L%"
