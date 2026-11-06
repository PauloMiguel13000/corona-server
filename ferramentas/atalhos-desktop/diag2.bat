@echo off
set "O=C:\Users\server1\Documents\minecraft-server\diag2.txt"
echo ==== %date% %time% > "%O%"
echo ---- servicos >> "%O%"
sc query chromoting >> "%O%" 2>&1
sc query AnyDesk >> "%O%" 2>&1
echo ---- pastas google >> "%O%"
dir /b "C:\Program Files (x86)\Google" >> "%O%" 2>&1
powershell -NoProfile -Command "$ini=Get-Date '25/10/2026 12:55'; $fim=Get-Date '25/10/2026 18:10'; 'EVENTOS SISTEMA'; Get-WinEvent -FilterHashtable @{LogName='System'; StartTime=$ini; EndTime=$fim} -ErrorAction SilentlyContinue | Select-Object -First 60 | Format-Table TimeCreated,Id,LevelDisplayName,ProviderName -AutoSize | Out-String -Width 220; 'EVENTOS APLICATIVO'; Get-WinEvent -FilterHashtable @{LogName='Application'; StartTime=$ini; EndTime=$fim; Level=1,2,3} -ErrorAction SilentlyContinue | Select-Object -First 30 | Format-Table TimeCreated,Id,ProviderName -AutoSize | Out-String -Width 220; 'MEMORIA'; $os=Get-CimInstance Win32_OperatingSystem; 'total MB ' + [int]($os.TotalVisibleMemorySize/1024) + ' livre MB ' + [int]($os.FreePhysicalMemory/1024); Get-CimInstance Win32_PageFileUsage | Format-List Name,AllocatedBaseSize,CurrentUsage,PeakUsage; 'DISCOS'; Get-PhysicalDisk | Format-Table FriendlyName,MediaType,HealthStatus,Size -AutoSize | Out-String -Width 200; 'REDE'; Get-NetAdapter | Format-Table Name,InterfaceDescription,Status,MediaType,MacAddress -AutoSize | Out-String -Width 220; 'PLACA'; Get-CimInstance Win32_BaseBoard | Format-List Manufacturer,Product; 'PROCESSOS QUE MAIS USAM MEMORIA'; Get-Process | Sort-Object WorkingSet64 -Descending | Select-Object -First 12 Name,@{n='MB';e={[int]($_.WorkingSet64/1MB)}} | Format-Table -AutoSize | Out-String" >> "%O%" 2>&1
(goto) 2>nul & del "%~f0"
