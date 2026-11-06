@echo off
title Baixando o AnyDesk
set "LOG=C:\Users\server1\Documents\minecraft-server\anydesk-download.log"
curl.exe -L --fail -sS -o "C:\Users\server1\Downloads\AnyDesk.exe" https://download.anydesk.com/AnyDesk.exe > "%LOG%" 2>&1
echo codigo=%errorlevel% >> "%LOG%"
(goto) 2>nul & del "%~f0"
