@echo off
title Reiniciando o Playit
set "LOG=C:\Users\server1\Documents\minecraft-server\reiniciar-playit.log"
echo ==== antes > "%LOG%"
tasklist /v /fi "imagename eq playit*" >> "%LOG%" 2>&1
taskkill /f /t /im playit* >> "%LOG%" 2>&1
timeout /t 4 /nobreak >nul
echo ==== depois de fechar >> "%LOG%"
tasklist /v /fi "imagename eq playit*" >> "%LOG%" 2>&1
start "" "C:\Program Files\playit_gg\bin\playit.exe"
timeout /t 8 /nobreak >nul
echo ==== depois de abrir >> "%LOG%"
tasklist /v /fi "imagename eq playit*" >> "%LOG%" 2>&1
(goto) 2>nul & del "%~f0"
