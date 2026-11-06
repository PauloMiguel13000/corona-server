@echo off
setlocal
set "S=C:\Users\server1\Documents\minecraft-server"
set "L=C:\Users\server1\Documents\minecraft-server\otimizacao\sons-skins.log"
cd /d "%S%"
echo ==== %date% %time% sons e skins: espera o servidor fechar ate 20 minutos >> "%L%"
if not exist "otimizacao\sons-skins\plugins\CoronaPack-1.0.jar" (echo faltam os arquivos >> "%L%" & goto :fim)
call :rodando
if errorlevel 1 goto :desligado
powershell -NoProfile -ExecutionPolicy Bypass -File "%S%\otimizacao\comando.ps1" stop
set /a N=0
:esperar
timeout /t 3 /nobreak >nul
call :rodando
if errorlevel 1 goto :desligado
set /a N+=1
if %N% lss 400 goto :esperar
echo o servidor nao fechou em 20 minutos: nada foi instalado >> "%L%"
goto :fim
:desligado
echo %time% servidor fechado >> "%L%"
taskkill /f /fi "WINDOWTITLE eq Servidor de Minecraft*" >> "%L%" 2>&1
timeout /t 2 /nobreak >nul
call :rodando
if not errorlevel 1 (echo o servidor ja tinha reiniciado sozinho: nada foi instalado >> "%L%" & goto :fim)
rem copia de seguranca do que vai ser trocado
if not exist "otimizacao\antes-sons-skins" mkdir "otimizacao\antes-sons-skins"
for %%F in (Backrooms-1.0.jar DefesaTorre-1.0.jar) do copy /y "plugins\%%F" "otimizacao\antes-sons-skins\%%F" >> "%L%" 2>&1
xcopy /y /e /i "otimizacao\sons-skins\plugins\*" "%S%\plugins\" >> "%L%" 2>&1
start "Servidor de Minecraft" /D "%S%" "%S%\iniciar-servidor.bat"
echo %time% servidor ligado de novo >> "%L%"
goto :fim
:rodando
powershell -NoProfile -Command "if (Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.Path -like '*\minecraft-server\runtime\*' }) { exit 0 } else { exit 1 }"
exit /b %errorlevel%
:fim
(goto) 2>nul & del "%~f0"
