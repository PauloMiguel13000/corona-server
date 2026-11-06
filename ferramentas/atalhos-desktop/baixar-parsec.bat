@echo off
set "LOG=C:\Users\server1\Documents\minecraft-server\parsec-download.log"
curl.exe -L --fail -sS -o "C:\Users\server1\Downloads\parsec-windows.exe" https://builds.parsec.app/package/parsec-windows.exe > "%LOG%" 2>&1
echo codigo=%errorlevel% >> "%LOG%"
powershell -NoProfile -Command "Get-CimInstance Win32_VideoController | Format-List Name,DriverVersion; Get-CimInstance Win32_Processor | Format-List Name" >> "%LOG%" 2>&1
(goto) 2>nul & del "%~f0"
