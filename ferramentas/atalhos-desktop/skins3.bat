@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "C:\Users\server1\Documents\minecraft-server\skins3.ps1"
(goto) 2>nul & del "%~f0"
