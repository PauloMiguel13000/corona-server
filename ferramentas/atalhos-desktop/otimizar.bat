@echo off
setlocal
set "S=C:\Users\server1\Documents\minecraft-server"
set "L=C:\Users\server1\Documents\minecraft-server\otimizacao\trocar.log"
cd /d "%S%"
echo ==== %date% %time% >> "%L%"
if not exist "otimizacao\novos\iniciar-servidor.bat" (echo faltam os arquivos novos >> "%L%" & goto :fim)
rem 1. copia de seguranca dos arquivos atuais
if not exist "otimizacao\antes\config" mkdir "otimizacao\antes\config"
for %%f in (iniciar-servidor.bat memoria.bat server.properties bukkit.yml spigot.yml) do copy /y "%%f" "otimizacao\antes\%%f" >> "%L%" 2>&1
copy /y "config\paper-world-defaults.yml" "otimizacao\antes\config\paper-world-defaults.yml" >> "%L%" 2>&1
rem 2. desliga o servidor do jeito certo (comando stop no console)
tasklist /fi "imagename eq java.exe" | find /i "java.exe" >nul
if errorlevel 1 goto :desligado
powershell -NoProfile -ExecutionPolicy Bypass -File "%S%\otimizacao\comando.ps1" stop >> "%L%" 2>&1
set /a N=0
:esperar
timeout /t 3 /nobreak >nul
tasklist /fi "imagename eq java.exe" | find /i "java.exe" >nul
if errorlevel 1 goto :desligado
set /a N+=1
if %N% lss 80 goto :esperar
echo o servidor nao fechou em 4 minutos: nada foi trocado >> "%L%"
goto :fim
:desligado
echo %time% servidor fechado >> "%L%"
rem 3. fecha a janela antiga antes que ela reinicie sozinha
taskkill /f /fi "WINDOWTITLE eq Servidor de Minecraft*" >> "%L%" 2>&1
timeout /t 2 /nobreak >nul
tasklist /fi "imagename eq java.exe" | find /i "java.exe" >nul
if not errorlevel 1 (echo o servidor ja tinha reiniciado sozinho: nada foi trocado >> "%L%" & goto :fim)
rem 4. coloca os arquivos novos
xcopy /y /e /i "otimizacao\novos\*" "%S%\" >> "%L%" 2>&1
rem 5. liga de novo
start "Servidor de Minecraft" /D "%S%" "%S%\iniciar-servidor.bat"
echo %time% servidor ligado de novo >> "%L%"
:fim
(goto) 2>nul & del "%~f0"
