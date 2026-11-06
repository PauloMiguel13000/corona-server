@echo off
setlocal
set "S=C:\Users\server1\Documents\minecraft-server"
set "L=C:\Users\server1\Documents\minecraft-server\otimizacao\vovo3.log"
cd /d "%S%"
echo ==== %date% %time% casa da vovo maior + esgoto >> "%L%"
if not exist "otimizacao\vovo3\plugins\Vovo-1.0.jar" (echo faltam os arquivos >> "%L%" & goto :fim)
if not exist "otimizacao\vovo3\plugins\Vovo\config.yml" (echo falta o config >> "%L%" & goto :fim)
if not exist "otimizacao\antes-vovo3" mkdir "otimizacao\antes-vovo3"
copy /y "plugins\Vovo-1.0.jar" "otimizacao\antes-vovo3\Vovo-1.0.jar" >> "%L%" 2>&1
copy /y "plugins\Vovo\config.yml" "otimizacao\antes-vovo3\config.yml" >> "%L%" 2>&1
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
xcopy /y /e /i "otimizacao\vovo3\plugins\*" "%S%\plugins\" >> "%L%" 2>&1
start "Servidor de Minecraft" /D "%S%" "%S%\iniciar-servidor.bat"
echo %time% servidor ligado de novo >> "%L%"
goto :fim
:rodando
powershell -NoProfile -Command "if (Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.Path -like '*\minecraft-server\runtime\*' }) { exit 0 } else { exit 1 }"
exit /b %errorlevel%
:fim
(goto) 2>nul & del "%~f0"
