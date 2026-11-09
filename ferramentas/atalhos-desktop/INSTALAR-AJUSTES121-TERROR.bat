@echo off
setlocal
rem ajustes121: quem recusou o pacote ve o boneco com skin em vez de cubo roxo (Slender, Turno); monstros 3D do Nao Chore
rem   e das Backrooms no lugar certo (estavam meio bloco fora); Chorona no Bedrock deixa de usar a mula dos pets
rem   (pacote 1.13.2).
rem   Avisa 2 min e reinicia MESMO com gente online.
set "S=C:\Users\server1\Documents\minecraft-server"
set "O=%S%\otimizacao"
set "L=%O%\ajustes121.log"
set "C=powershell -NoProfile -ExecutionPolicy Bypass -File "%O%\comando.ps1""
echo %date% %time% aviso de reinicio (2 min) >> "%L%"
%C% "title @a times 10 100 20"
%C% "title @a subtitle {text:'Correcoes nos jogos de terror! Voltem em 2 minutos',color:yellow}"
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
powershell -NoProfile -ExecutionPolicy Bypass -File "%O%\instalar-pacote.ps1" -Nome ajustes121 -Forcar
echo %date% %time% instalador terminou com codigo %errorlevel% >> "%L%"
timeout /t 3 /nobreak >nul
powershell -NoProfile -Command "if (Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.Path -like '*\minecraft-server\runtime\*' }) { exit 0 } else { exit 1 }"
if errorlevel 1 start "Servidor de Minecraft" /D "%S%" "%S%\iniciar-servidor.bat"
call "C:\Users\server1\Desktop\MEDIDOR-REDE.bat"
echo %date% %time% fim >> "%L%"
