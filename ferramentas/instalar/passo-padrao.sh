set -u
L=/srv/corona/minecraft-server/logs/latest.log
tail -n 3 /srv/corona/instalacoes.log
systemctl is-active corona-instalar
echo "ERRORs: $(grep -ac 'ERROR' $L)"; grep -a 'ERROR' $L | head -5 | cut -c1-220
grep -a "Apocalipse\|Abrigo\|Lobby dos Minigames v\|Spawn:" $L | cut -c1-230 | tail -8
/srv/corona/bin/comando.sh list; sleep 2; tail -n 1 $L | cut -c1-160
exit 0
