@echo off
rem Instala o Corona Server nesta maquina a partir do zip que veio pelo Syncthing.
rem Rode ISTO NA VPS, nao na maquina antiga.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0instalar-na-vps.ps1"
