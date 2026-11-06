@echo off
setlocal
set "S=C:\Users\server1\Documents\minecraft-server"
set "L=C:\Users\server1\Documents\minecraft-server\otimizacao\lobby.log"
set "R=C:\Users\server1\Documents\minecraft-server\world\dimensions\minecraft\overworld\region"
cd /d "%S%"
echo ==== %date% %time% lobby de login + spawn enfeitado >> "%L%"
if not exist "otimizacao\lobby\plugins\LoginLobby-1.0.jar" (echo faltam os arquivos >> "%L%" & goto :fim)
call :rodando
if errorlevel 1 goto :desligado
powershell -NoProfile -ExecutionPolicy Bypass -File "%S%\otimizacao\comando.ps1" stop
set /a N=0
:esperar
timeout /t 3 /nobreak >nul
call :rodando
if errorlevel 1 goto :desligado
set /a N+=1
if %N% lss 80 goto :esperar
echo o servidor nao fechou em 4 minutos: nada foi instalado >> "%L%"
goto :fim
:desligado
echo %time% servidor fechado >> "%L%"
taskkill /f /fi "WINDOWTITLE eq Servidor de Minecraft*" >> "%L%" 2>&1
timeout /t 2 /nobreak >nul
call :rodando
if not errorlevel 1 (echo o servidor ja tinha reiniciado sozinho: nada foi instalado >> "%L%" & goto :fim)
rem copia de seguranca do spawn (regioes em volta do 0,0) e do ponto de nascer, com o servidor desligado
if not exist "otimizacao\antes-spawn" mkdir "otimizacao\antes-spawn"
for %%F in (r.0.-1.mca r.-1.-1.mca r.0.0.mca r.-1.0.mca) do copy /y "%R%\%%F" "otimizacao\antes-spawn\%%F" >> "%L%" 2>&1
copy /y "plugins\Spawn\location.yml" "otimizacao\antes-spawn\location.yml" >> "%L%" 2>&1
xcopy /y /e /i "otimizacao\lobby\plugins\*" "%S%\plugins\" >> "%L%" 2>&1
start "Servidor de Minecraft" /D "%S%" "%S%\iniciar-servidor.bat"
echo %time% servidor ligado de novo >> "%L%"
goto :fim
:rodando
powershell -NoProfile -Command "if (Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.Path -like '*\minecraft-server\runtime\*' }) { exit 0 } else { exit 1 }"
exit /b %errorlevel%
:fim
(goto) 2>nul & del "%~f0"
