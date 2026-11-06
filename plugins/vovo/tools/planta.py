#!/usr/bin/env python3
"""Desenha a planta da casa da Vovo a partir dos arquivos de regiao (para conferir a construcao)."""
import sys, os
sys.path.insert(0, '/tmp/claude-0/extras')
from achar_blocos import chunks, nome_bloco
from PIL import Image, ImageDraw

pasta = sys.argv[1]
saida = sys.argv[2]
X0, X1, Z0, Z1, Y0, Y1 = -3, 40, -3, 36, 56, 86
B = {}
for arq in sorted(os.listdir(pasta)):
    if not arq.endswith('.mca'): continue
    for c in chunks(os.path.join(pasta, arq)):
        cx = c.get('xPos'); cz = c.get('zPos')
        if cx is None: continue
        if cx * 16 + 15 < X0 or cx * 16 > X1 or cz * 16 + 15 < Z0 or cz * 16 > Z1: continue
        for s in c.get('sections', []):
            bs = s.get('block_states')
            if not bs: continue
            sy = s['Y']
            if sy * 16 + 15 < Y0 or sy * 16 > Y1: continue
            pal = [nome_bloco(p) for p in bs.get('palette', [])]
            if len(pal) == 1:
                if pal[0] == 'minecraft:air': continue
                for i in range(4096):
                    B[(cx*16+(i&15), sy*16+(i>>8), cz*16+((i>>4)&15))] = pal[0]
                continue
            bits = max(4, (len(pal)-1).bit_length()); pl = 64 // bits; m = (1 << bits) - 1
            d = bs['data']
            for i in range(4096):
                v = (d[i // pl] & 0xFFFFFFFFFFFFFFFF) >> ((i % pl) * bits) & m
                n = pal[v]
                if n != 'minecraft:air':
                    B[(cx*16+(i&15), sy*16+(i>>8), cz*16+((i>>4)&15))] = n

def cor(n):
    n = n.replace('minecraft:', '')
    tab = [('pale_oak', (225, 215, 205)), ('dark_oak_planks', (70, 45, 25)), ('spruce_planks', (115, 85, 50)), ('stripped_spruce', (150, 110, 70)),
           ('stripped_dark_oak', (95, 60, 35)), ('terracotta', (200, 200, 200)), ('deepslate', (60, 60, 65)), ('cobble', (110, 110, 110)),
           ('glass', (160, 210, 230)), ('door', (200, 200, 210)), ('barrel', (140, 95, 50)), ('stairs', (160, 120, 80)), ('bed', (200, 40, 40)),
           ('lantern', (255, 220, 120)), ('candle', (255, 240, 180)), ('campfire', (80, 200, 255)), ('carpet', (170, 60, 60)), ('bookshelf', (150, 100, 60)),
           ('concrete', (230, 230, 230)), ('grass', (60, 110, 50)), ('gravel', (140, 135, 130)), ('fence', (60, 40, 20)), ('log', (60, 40, 25)),
           ('stone', (130, 130, 130)), ('brick', (150, 70, 50)), ('iron', (200, 200, 200)), ('cauldron', (60, 60, 60)), ('plate', (255, 0, 255)),
           ('cobweb', (240, 240, 240)), ('froglight', (255, 230, 120)), ('lamp', (150, 90, 50)), ('crafter', (90, 90, 90))]
    for k, c in tab:
        if k in n: return c
    return (255, 0, 0)

S = 12
niveis = [('PORAO y59', 59), ('TERREO y64', 64), ('ANDAR y69', 69)]
W = (X1 - X0 + 1) * S
H = (Z1 - Z0 + 1) * S
img = Image.new('RGB', (W * len(niveis) + 20 * len(niveis), H + 30), (30, 30, 30))
d = ImageDraw.Draw(img)
for k, (nome, y) in enumerate(niveis):
    ox = k * (W + 20)
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            px, pz = ox + (x - X0) * S, 25 + (z - Z0) * S
            a = B.get((x, y + 1, z)); b = B.get((x, y, z)); c0 = B.get((x, y - 1, z))
            if a and 'pane' not in a and 'door' not in a:
                d.rectangle([px, pz, px + S - 1, pz + S - 1], fill=cor(a))
            elif b:
                cc = cor(b)
                d.rectangle([px, pz, px + S - 1, pz + S - 1], fill=tuple(int(v * 0.85) for v in cc))
                if a and ('pane' in a or 'door' in a): d.rectangle([px + 3, pz + 3, px + S - 4, pz + S - 4], fill=cor(a))
            elif c0:
                cc = cor(c0)
                d.rectangle([px, pz, px + S - 1, pz + S - 1], fill=tuple(int(v * 0.45) for v in cc))
            if x % 5 == 0 and z == Z0: d.text((px, 12), str(x), fill=(255, 255, 255))
            if z % 5 == 0 and x == X0: d.text((ox, pz), str(z), fill=(255, 255, 0))
    d.text((ox + 30, 2), nome, fill=(255, 255, 255))
img.save(saida)
print('blocos lidos:', len(B))
