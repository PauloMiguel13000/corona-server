@echo off
set "O=C:\Users\server1\Documents\minecraft-server\otimizacao\janelas.txt"
powershell -NoProfile -Command "Get-Process | Where-Object { $_.MainWindowTitle } | Format-Table Id,Name,MainWindowTitle -AutoSize | Out-String -Width 300" > "%O%" 2>&1
tasklist /v /fo csv | findstr /i "cmd.exe java.exe conhost WindowsTerminal OpenConsole" >> "%O%" 2>&1
(goto) 2>nul & del "%~f0"
