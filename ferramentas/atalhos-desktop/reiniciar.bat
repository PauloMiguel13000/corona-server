@echo off
title Reiniciando o PC
echo O PC vai reiniciar em 20 segundos (para destravar o Playit).
shutdown /r /t 20 /c "Reiniciando para destravar o Playit. O servidor volta sozinho."
(goto) 2>nul & del "%~f0"
