@echo off
rem Atalho para ligar o servidor de Minecraft
powershell -NoProfile -Command "if (Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.Path -like '*\minecraft-server\runtime\*' }) { exit 0 } else { exit 1 }"
if not errorlevel 1 goto :ja_ligado
tasklist /fi "imagename eq playit.exe" 2>nul | find /i "playit.exe" >nul
if errorlevel 1 if exist "C:\Program Files\playit_gg\bin\playit.exe" start "Playit.gg" "C:\Program Files\playit_gg\bin\playit.exe"
start "Servidor de Minecraft" /D "C:\Users\server1\Documents\minecraft-server" "C:\Users\server1\Documents\minecraft-server\iniciar-servidor.bat"
exit /b 0

:ja_ligado
echo O servidor ja esta ligado (a janela "Servidor de Minecraft" ja esta aberta).
timeout /t 6 >nul
exit /b 0
