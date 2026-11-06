@echo off
set "O=C:\Users\server1\Documents\minecraft-server\diag.txt"
echo ==== %date% %time% > "%O%"
tasklist /fi "imagename eq java.exe" >> "%O%" 2>&1
echo ---- servicos >> "%O%"
sc query chromoting >> "%O%" 2>&1
sc query AnyDesk >> "%O%" 2>&1
sc queryex type= service state= all | findstr /i "SERVICE_NAME" | findstr /i "remot chrom anydesk playit" >> "%O%" 2>&1
echo ---- pastas >> "%O%"
dir /b "C:\Program Files (x86)\Google" >> "%O%" 2>&1
dir /b "C:\Program Files\Google" >> "%O%" 2>&1
echo ---- energia e boot >> "%O%"
powershell -NoProfile -Command "(Get-CimInstance Win32_OperatingSystem).LastBootUpTime; Get-WinEvent -FilterHashtable @{LogName='System'; Id=1074,6006,6008,42,107,41,1} -MaxEvents 15 | Format-Table TimeCreated,Id,ProviderName -AutoSize | Out-String -Width 220; powercfg /query SCHEME_CURRENT SUB_SLEEP STANDBYIDLE | Out-String; powercfg /query SCHEME_CURRENT SUB_SLEEP HIBERNATEIDLE | Out-String; Get-NetAdapter | Format-Table Name,InterfaceDescription,Status,MediaType,MacAddress -AutoSize | Out-String -Width 220; Get-CimInstance Win32_BaseBoard | Format-List Manufacturer,Product" >> "%O%" 2>&1
dir "%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup" >> "%O%" 2>&1
(goto) 2>nul & del "%~f0"
