@echo off
setlocal
set "S=C:\Users\server1\Documents\minecraft-server"
set "L=C:\Users\server1\Documents\minecraft-server\otimizacao\geyser.log"
cd /d "%S%"
echo ==== %date% %time% geyser 2.12.1 b1276 (Bedrock 26.60) >> "%L%"
rem pega o Geyser que foi baixado na pasta Downloads (so se for exatamente o build oficial 1276, conferido pelo SHA-256)
powershell -NoProfile -Command "$h='D747D9F0B74370FAE40E1C329193CF178BCB3FC3B2ADC51CBE618AD4CFAF8945'; $d='C:\Users\server1\Documents\minecraft-server\otimizacao\geyser\plugins'; $f=Get-ChildItem 'C:\Users\server1\Downloads' -File | Where-Object { $_.Length -eq 52144430 } | Where-Object { (Get-FileHash $_.FullName -Algorithm SHA256).Hash -eq $h } | Select-Object -First 1; if ($f) { New-Item -ItemType Directory -Force $d | Out-Null; Copy-Item $f.FullName (Join-Path $d 'Geyser-Spigot.jar') -Force; exit 0 } else { exit 1 }"
if errorlevel 1 (echo nao achei o Geyser 1276 na pasta Downloads: nada foi feito >> "%L%" & goto :fim)
if not exist "otimizacao\geyser\plugins\Geyser-Spigot.jar" (echo faltam os arquivos >> "%L%" & goto :fim)
echo geyser 1276 conferido e copiado >> "%L%"
call :rodando
if errorlevel 1 goto :desligado
powershell -NoProfile -ExecutionPolicy Bypass -File "%S%\otimizacao\comando.ps1" stop
set /a N=0
:esperar
timeout /t 3 /nobreak >nul
call :rodando
if errorlevel 1 goto :desligado
set /a N+=1
if %N% lss 200 goto :esperar
echo o servidor nao fechou em 10 minutos: nada foi instalado >> "%L%"
goto :fim
:desligado
echo %time% servidor fechado >> "%L%"
taskkill /f /fi "WINDOWTITLE eq Servidor de Minecraft*" >> "%L%" 2>&1
timeout /t 2 /nobreak >nul
call :rodando
if not errorlevel 1 (echo o servidor ja tinha reiniciado sozinho: nada foi instalado >> "%L%" & goto :fim)
rem copia de seguranca do que vai ser trocado
if not exist "otimizacao\antes-geyser1276" mkdir "otimizacao\antes-geyser1276"
copy /y "plugins\Geyser-Spigot.jar" "otimizacao\antes-geyser1276\Geyser-Spigot.jar" >> "%L%" 2>&1
copy /y "otimizacao\geyser\plugins\Geyser-Spigot.jar" "plugins\Geyser-Spigot.jar" >> "%L%" 2>&1
start "Servidor de Minecraft" /D "%S%" "%S%\iniciar-servidor.bat"
echo %time% servidor ligado de novo >> "%L%"
goto :fim
:rodando
powershell -NoProfile -Command "if (Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.Path -like '*\minecraft-server\runtime\*' }) { exit 0 } else { exit 1 }"
exit /b %errorlevel%
:fim
(goto) 2>nul & del "%~f0"
