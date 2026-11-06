@echo off
set "O=C:\Users\server1\Documents\minecraft-server\otimizacao\inicio.txt"
echo ==== %date% %time% > "%O%"
echo ---- HKCU Run >> "%O%"
reg query "HKCU\Software\Microsoft\Windows\CurrentVersion\Run" >> "%O%" 2>&1
echo ---- HKCU StartupApproved Run >> "%O%"
reg query "HKCU\Software\Microsoft\Windows\CurrentVersion\Explorer\StartupApproved\Run" >> "%O%" 2>&1
echo ---- HKCU StartupApproved StartupFolder >> "%O%"
reg query "HKCU\Software\Microsoft\Windows\CurrentVersion\Explorer\StartupApproved\StartupFolder" >> "%O%" 2>&1
echo ---- HKLM Run >> "%O%"
reg query "HKLM\Software\Microsoft\Windows\CurrentVersion\Run" >> "%O%" 2>&1
reg query "HKLM\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Run" >> "%O%" 2>&1
echo ---- HKLM StartupApproved Run >> "%O%"
reg query "HKLM\Software\Microsoft\Windows\CurrentVersion\Explorer\StartupApproved\Run" >> "%O%" 2>&1
reg query "HKLM\Software\Microsoft\Windows\CurrentVersion\Explorer\StartupApproved\Run32" >> "%O%" 2>&1
echo ---- pasta Inicializar do usuario >> "%O%"
dir /b "%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup" >> "%O%" 2>&1
echo ---- pasta Inicializar de todos >> "%O%"
dir /b "%ProgramData%\Microsoft\Windows\Start Menu\Programs\StartUp" >> "%O%" 2>&1
echo ---- apps da loja com inicializacao >> "%O%"
reg query "HKCU\Software\Classes\Local Settings\Software\Microsoft\Windows\CurrentVersion\AppModel\SystemAppData" /s /v State >> "%O%" 2>&1
echo ---- tarefas agendadas no logon >> "%O%"
powershell -NoProfile -Command "Get-ScheduledTask | Where-Object { $_.State -ne 'Disabled' -and ($_.Triggers | Where-Object { $_.CimClass.CimClassName -match 'Logon|Boot' }) -and $_.TaskPath -notlike '\Microsoft\*' } | Format-Table TaskPath,TaskName,State -AutoSize | Out-String -Width 250" >> "%O%" 2>&1
echo ---- servicos automaticos que nao sao da Microsoft >> "%O%"
powershell -NoProfile -Command "Get-CimInstance Win32_Service | Where-Object { $_.StartMode -eq 'Auto' -and $_.PathName -notmatch '\\Windows\\' } | Format-Table Name,DisplayName,State,PathName -AutoSize | Out-String -Width 300" >> "%O%" 2>&1
echo ---- processos com janela >> "%O%"
powershell -NoProfile -Command "Get-Process | Where-Object { $_.Path -and $_.Path -notmatch '\\Windows\\' } | Sort-Object Name -Unique | Format-Table Name,Id,@{n='MB';e={[int]($_.WorkingSet64/1MB)}},Path -AutoSize | Out-String -Width 300" >> "%O%" 2>&1
(goto) 2>nul & del "%~f0"
