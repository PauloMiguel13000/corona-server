ls $K
systemctl stop corona-instalar 2>/dev/null; systemctl reset-failed corona-instalar 2>/dev/null
L=$S/logs/latest.log; C=/srv/corona/bin/comando.sh
# 16/11 (jogador: "o aviso de reinicio aparece a cada ~10 min e nao da tempo de terminar a masmorra"):
# com gente online so reinicia com aviso se o ultimo reinicio foi ha mais de 2 h e ninguem estiver nas masmorras;
# senao instala quando o servidor ficar vazio.
n0=$(wc -l < $L); $C list; sleep 3
J=$(tail -n +$((n0+1)) $L | grep -a -o 'There are [0-9]* of a max' | tail -1 | grep -o '[0-9]*' | head -1)
MASM=0
for w in masmorras_mundo masmorras_nether masmorras_end masmorras_spawn herobrine_inferno; do
  n1=$(wc -l < $L); $C "execute in minecraft:$w if entity @a[distance=0..]"; sleep 1
  c=$(tail -n +$((n1+1)) $L | grep -a -o 'count: [0-9]*' | tail -1 | grep -o '[0-9]*'); MASM=$((MASM + ${c:-0}))
done
MIN=$(python3 - <<'PY'
import re, datetime
ult = None
for l in open('/srv/corona/instalacoes.log', encoding='utf-8', errors='replace'):
    m = re.match(r'(\d\d)/(\d\d) (\d\d):(\d\d) \S+: (ligou ok|vazio, reiniciando)', l)
    if m: ult = m
if not ult: print(9999)
else:
    agora = datetime.datetime.now()
    d = datetime.datetime(agora.year, int(ult.group(2)), int(ult.group(1)), int(ult.group(3)), int(ult.group(4)))
    print(int((agora - d).total_seconds() // 60))
PY
)
echo "online: ${J:-?} | nas masmorras: $MASM | ultimo reinicio ha $MIN min"
aviso() {
  $C "title @a times 10 80 20"
  $C "title @a subtitle \"$2\""
  $C "title @a title \"$1\""
  $C "say [Servidor] Reinicio para atualizacao em $3. Volta em 1 minuto!"
}
if [ "${J:-1}" != 0 ] && { [ "$MASM" -gt 0 ] || [ "$MIN" -lt 120 ]; }; then
  systemd-run --unit corona-instalar --description 'Corona: instalar quando vazio' /srv/corona/bin/instalar-quando-vazio.sh $K NUM >/dev/null 2>&1 && echo "agendado: instala o NUM quando o servidor ficar vazio (ninguem e derrubado)"
  exit 0
fi
if [ "${J:-1}" != 0 ]; then
aviso "Reinicio em 2 minutos" "Atualizacao rapida: volta em 1 minuto" "2 minutos"; sleep 60
aviso "Reinicio em 1 minuto" "Guarde suas coisas" "1 minuto"; sleep 30
aviso "Reinicio em 30 segundos" "Volta em 1 minuto" "30 segundos"; sleep 20
aviso "Reinicio em 10 segundos" "Ja volta!" "10 segundos"; sleep 10
fi
sed 's/if \[ "\$J" = 0 \]; then/if true; then/' /srv/corona/bin/instalar-quando-vazio.sh > /root/instalar-agora.sh
chmod 700 /root/instalar-agora.sh
systemctl reset-failed corona-instalar-agora 2>/dev/null; systemd-run --unit corona-instalar-agora --description 'Corona: instalar com aviso' /root/instalar-agora.sh $K NUM >/dev/null 2>&1 && echo "instalando o NUM"
for i in $(seq 1 40); do sleep 5; tail -n 1 /srv/corona/instalacoes.log | grep -q "NUM: \(ligou\|voltado\)" && break; done
tail -n 3 /srv/corona/instalacoes.log
sleep 55
echo "ERRORs: $(grep -ac 'ERROR' $L)"; grep -a 'ERROR' $L | head -5 | cut -c1-220
grep -a "Jogos de tiro prontos\|Masmorras\] Enabl\|CoronaPack\] Pacote pronto\|Done (" $L | cut -c1-200 | tail -5
exit 0
