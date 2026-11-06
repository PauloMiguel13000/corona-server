@echo off
title Ativando inicio automatico do Corona Server
set "DEST=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\Corona Server.bat"
> "%DEST%" echo @echo off
>> "%DEST%" echo start "Corona Server" /min "C:\Users\server1\Documents\minecraft-server\iniciar-com-windows.bat"
echo.
if exist "%DEST%" (echo OK: inicio automatico ativado.) else (echo ERRO: nao consegui criar o arquivo.)
echo Arquivo: %DEST%
type "%DEST%"
echo.
tasklist /fi "imagename eq playit.exe" 2>nul | find /i "playit.exe" >nul && echo Teste: Playit detectado - ok
tasklist /fi "imagename eq java.exe" 2>nul | find /i "java.exe" >nul && echo Teste: Servidor detectado - ok
echo.
timeout /t 20
(goto) 2>nul & del "%~f0"
