"""ajustar_cfg.py arquivo caminho.da.chave=valor ...  -> troca so o valor dessas chaves (mantem comentarios).
Se a chave nao existir, acrescenta no fim do bloco pai."""
import sys, re
arq = sys.argv[1]
mud = dict(a.split('=', 1) for a in sys.argv[2:])
linhas = open(arq, encoding='utf-8').read().split('\n')
pilha = []  # (indent, nome)
feitos = set()
fim_bloco = {}
for i, ln in enumerate(linhas):
    m = re.match(r'^(\s*)([A-Za-z0-9_\-]+):(.*)$', ln)
    if not m or ln.strip().startswith('#'):
        continue
    ind = len(m.group(1))
    while pilha and pilha[-1][0] >= ind:
        pilha.pop()
    caminho = '.'.join([p[1] for p in pilha] + [m.group(2)])
    for p in range(len(pilha)):
        fim_bloco['.'.join(x[1] for x in pilha[:p + 1])] = (i, pilha[p][0])
    if caminho in mud:
        resto = m.group(3)
        com = ''
        if '#' in resto:
            com = '  ' + resto[resto.index('#'):]
        linhas[i] = f"{m.group(1)}{m.group(2)}: {mud[caminho]}{com}"
        feitos.add(caminho)
    pilha.append((ind, m.group(2)))
faltam = [k for k in mud if k not in feitos]
for k in faltam:
    pai, nome = k.rsplit('.', 1)
    if pai not in fim_bloco:
        print('SEM PAI', k); sys.exit(1)
    i, indpai = fim_bloco[pai]
    linhas.insert(i + 1, ' ' * (indpai + 2) + f"{nome}: {mud[k]}")
    # recalcula de forma simples: nao suporta varias insercoes no mesmo bloco fora de ordem
    fim_bloco = {kk: ((ii + 1 if ii > i else ii), ind) for kk, (ii, ind) in fim_bloco.items()}
    fim_bloco[pai] = (i + 1, indpai)
open(arq, 'w', encoding='utf-8').write('\n'.join(linhas))
print('ok', len(feitos), 'trocadas,', len(faltam), 'novas')
