#!/bin/bash
# t.sh "<comando>" [espera] -> manda comando e mostra as linhas novas do log
L=/tmp/claude-0/teste/out.log
n=$(wc -l < $L)
echo "$1" > /tmp/claude-0/teste/in.fifo
sleep ${2:-1}
tail -n +$((n+1)) $L | sed 's/\x1b\[[0-9;]*m//g' | grep -v "^\s*at \|LANCOU\|PROJETIL\|DANO zumbi\|INTERACT acao" | cut -c1-330
