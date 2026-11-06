@echo off
title Teste
powershell -NoProfile -Command "Get-StartApps | Where-Object { $_.Name -like '*Claude*' -or $_.Name -like '*playit*' } | Format-List Name,AppID" > "C:\Users\server1\Documents\minecraft-server\teste-apps.txt" 2>&1
(goto) 2>nul & del "%~f0"
