#!/bin/bash
# para o servidor de teste, copia o jar novo e espera ligar de novo (o run.sh e iniciado a parte)
cd /tmp/claude-0/teste
./c.sh stop
for i in $(seq 1 60); do tail -2 out.log | grep -q "Awaiting termination of I/O\|Closing Server" && sleep 3 && break; sleep 1; done
cp /tmp/claude-0/vovo/Vovo-1.0.jar plugins/
echo parado-e-copiado
