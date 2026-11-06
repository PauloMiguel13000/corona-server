@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "C:\Users\server1\Documents\minecraft-server\otimizacao\comando.ps1" "tps"
timeout /t 2 /nobreak >nul
powershell -NoProfile -ExecutionPolicy Bypass -File "C:\Users\server1\Documents\minecraft-server\otimizacao\comando.ps1" "spark health"
(goto) 2>nul & del "%~f0"
