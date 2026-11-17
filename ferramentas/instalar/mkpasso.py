# uso: python3 mkpasso.py NUM saida.sh jar1:nome1:Plugin1 [jar2:nome2:Plugin2 ...] [-- "comando de checagem"]
import sys,base64,hashlib
num=sys.argv[1]; out=sys.argv[2]; args=sys.argv[3:]
chk=''
if '--' in args: i=args.index('--'); chk=args[i+1]; args=args[:i]
jars=[a.split(':') for a in args]
o=['set -u','S=/srv/corona/minecraft-server',f'K=/root/pacote{num}; rm -rf $K; mkdir -p $K']
for src,nome,_ in jars:
    o+= [f"base64 -d > $K/{nome} <<'B64FIM'", base64.encodebytes(open(src,'rb').read()).decode().rstrip('\n'), 'B64FIM']
o.append("cd $K && sha256sum -c --quiet <<'SHA' && echo 'jars conferidos' || { echo 'ERRO: jar diferente'; exit 0; }")
for src,nome,_ in jars: o.append(hashlib.sha256(open(src,'rb').read()).hexdigest()+'  '+nome)
o.append('SHA')
for src,nome,plug in jars:
    o.append(f'''N=$(python3 - "$S/plugins" <<'PY2'
import sys, zipfile, glob, os
for f in sorted(glob.glob(sys.argv[1] + '/*.jar')):
    try:
        y = zipfile.ZipFile(f).read('plugin.yml').decode('utf-8', 'replace')
    except Exception:
        continue
    if any(l.strip() == 'name: {plug}' for l in y.splitlines()):
        print(os.path.basename(f)); break
PY2
)
echo "{plug} no servidor: ${{N:-nenhum}}"
[ -n "$N" ] && [ "$N" != {nome} ] && mv $K/{nome} "$K/$N"''')
o.append(f'''ls $K
systemctl stop corona-instalar 2>/dev/null; systemctl reset-failed corona-instalar 2>/dev/null
systemd-run --unit corona-instalar --description 'Corona: instalar quando vazio' /srv/corona/bin/instalar-quando-vazio.sh $K {num} >/dev/null 2>&1 && echo "agendado: instala o {num} quando o servidor ficar vazio (ate 8h)"
for i in $(seq 1 36); do grep -aq '{num}: \\(ligou\\|voltado\\|PROBLEMA\\)' /srv/corona/instalacoes.log && break; sleep 5; done
sleep 5
L=$S/logs/latest.log
for i in $(seq 1 40); do grep -aq 'Done (' $L && break; sleep 5; done
sleep 25
echo "== instalacao"; tail -n 4 /srv/corona/instalacoes.log
echo "== partida: $(grep -a 'Done (' $L | tail -1 | cut -c1-60)"
echo "ERRORs: $(grep -ac 'ERROR' $L)"
grep -a 'ERROR' $L | head -8 | cut -c1-220
n0=$(wc -l < $L)
{('/srv/corona/bin/comando.sh "'+chk+'"') if chk else ':'}
/srv/corona/bin/comando.sh list; /srv/corona/bin/comando.sh tps; sleep 4; tail -n +$((n0+1)) $L | cut -c1-200 | head -12
exit 0''')
open(out,'w').write('\n'.join(o)+'\n')
for src,nome,_ in jars: print(nome, hashlib.sha256(open(src,'rb').read()).hexdigest()[:12])
