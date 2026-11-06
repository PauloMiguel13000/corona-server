@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "C:\Users\server1\Documents\minecraft-server\otimizacao\comando.ps1" stop
(goto) 2>nul & del "%~f0"
