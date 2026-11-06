@echo off
set "O=C:\Users\server1\Documents\minecraft-server\otimizacao\limpar-inicio.log"
echo ==== %date% %time% >> "%O%"
rem Desliga a inicializacao automatica (igual ao botao Desabilitar do Gerenciador de Tarefas; da para religar por la)
set "K=HKCU\Software\Microsoft\Windows\CurrentVersion\Explorer\StartupApproved\Run"
for %%n in (OneDrive RobloxPlayerBeta MicrosoftEdgeAutoLaunch_B267FB6D2439403FBD9D13EA8ED509B2) do reg add "%K%" /v %%n /t REG_BINARY /d 030000000000000000000000 /f >> "%O%" 2>&1
set "A=HKCU\Software\Classes\Local Settings\Software\Microsoft\Windows\CurrentVersion\AppModel\SystemAppData"
reg add "%A%\AppUp.IntelGraphicsExperience_8j3eq9eme6ctt\GCPStartupId" /v State /t REG_DWORD /d 1 /f >> "%O%" 2>&1
reg add "%A%\Microsoft.MicrosoftOfficeHub_8wekyb3d8bbwe\WebViewHostStartupId" /v State /t REG_DWORD /d 1 /f >> "%O%" 2>&1
reg add "%A%\Microsoft.YourPhone_8wekyb3d8bbwe\YourPhone.Start" /v State /t REG_DWORD /d 1 /f >> "%O%" 2>&1
reg add "%A%\Microsoft.Copilot_8wekyb3d8bbwe\Copilot.StartupTaskId" /v State /t REG_DWORD /d 1 /f >> "%O%" 2>&1
schtasks /change /disable /tn "OneDrive Startup Task-S-1-5-21-3223412495-3063273564-1405323716-1001" >> "%O%" 2>&1
rem Fecha agora o que nao precisa ficar aberto
for %%p in (OneDrive.exe FileCoAuth.exe M365Copilot.exe PhoneExperienceHost.exe RobloxPlayerBeta.exe msedge.exe) do taskkill /f /im %%p >> "%O%" 2>&1
echo ---- como ficou >> "%O%"
reg query "%K%" >> "%O%" 2>&1
reg query "%A%" /s /v State >> "%O%" 2>&1
schtasks /query /tn "OneDrive Startup Task-S-1-5-21-3223412495-3063273564-1405323716-1001" >> "%O%" 2>&1
(goto) 2>nul & del "%~f0"
