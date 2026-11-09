"""Mescla arquivos novos num pacote (zip/mcpack) existente: python3 -I mesclar_pacote.py <base.zip> <pasta_novos> <saida.zip> [versao_mcpack x.y.z]"""
import json
import os
import sys
import zipfile

base, pasta, saida = sys.argv[1:4]
versao = [int(x) for x in sys.argv[4].split('.')] if len(sys.argv) > 4 else None
novos = {}
for raiz, _, arqs in os.walk(pasta):
    for a in arqs:
        p = os.path.join(raiz, a)
        novos[os.path.relpath(p, pasta).replace(os.sep, '/')] = open(p, 'rb').read()
zi = zipfile.ZipFile(base)
zo = zipfile.ZipFile(saida, 'w', zipfile.ZIP_DEFLATED, compresslevel=9)
trocados = acrescentados = 0
data = None
for info in zi.infolist():
    dado = zi.read(info.filename)
    data = data or info.date_time
    if info.filename in novos:
        if novos[info.filename] != dado:
            trocados += 1
        dado = novos.pop(info.filename)
    if versao and info.filename == 'manifest.json':
        m = json.loads(dado)
        m['header']['version'] = versao
        for mod in m['modules']:
            mod['version'] = versao
        dado = json.dumps(m, separators=(',', ':')).encode()
    ni = zipfile.ZipInfo(info.filename, date_time=info.date_time)
    ni.compress_type = zipfile.ZIP_DEFLATED
    ni.external_attr = info.external_attr
    zo.writestr(ni, dado)
for nome in sorted(novos):
    ni = zipfile.ZipInfo(nome, date_time=data or (2026, 11, 9, 0, 0, 0))
    ni.compress_type = zipfile.ZIP_DEFLATED
    ni.external_attr = 0o644 << 16
    zo.writestr(ni, novos[nome])
    acrescentados += 1
zo.close()
print(f'{saida}: {trocados} trocados, {acrescentados} novos')
