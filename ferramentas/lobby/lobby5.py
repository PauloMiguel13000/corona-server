"""Lobby v5 do Mundo das Masmorras (desenho aprovado pelo Paulo em 18/11: "TA PERFEITO").

Gera plugins/masmorras/res/lobby5.txt.gz (paleta + blocos relativos ao centro do lobby, y 0 = piso) e uma previa PNG.
O plugin (LobbyMundo.construirV5) limpa a praca, firma o chao e coloca exatamente estes blocos.
A area das Chocadeiras (centro -16,16, raio 7) fica de fora: os ovos que estao chocando nao sao mexidos.
"""
import gzip, math, random, sys, os

R = 44
CHOC = (-16, 16, 7.0)          # Chocadeiras.CX, CZ e raio protegido
rnd = random.Random(20261118)
B = {}


def choc(x, z):
    return math.hypot(x - CHOC[0], z - CHOC[1]) <= CHOC[2]


def put(x, y, z, m):
    if choc(x, z):
        return
    B[(x, y, z)] = m


def cil(cx, cz, r, y0, y1, m, oco=False):
    for x in range(int(cx - r) - 1, int(cx + r) + 2):
        for z in range(int(cz - r) - 1, int(cz + r) + 2):
            d = math.hypot(x - cx, z - cz)
            if d <= r and (not oco or d > r - 1.2):
                for y in range(y0, y1 + 1):
                    put(x, y, z, m)


# ---------------------------------------------------------------- piso
for x in range(-R - 4, R + 5):
    for z in range(-R - 4, R + 5):
        d = math.hypot(x, z)
        if d > R + 4:
            continue
        if d > R:   # faixa de fora do muro: grama (estrada continua de pedra)
            put(x, 0, z, 'estrada_fora' if (abs(x) <= 2 or abs(z) <= 2) else 'grama_chao')
            continue
        m = 'piso' if (x + z) % 2 == 0 else 'piso2'
        if abs(d - 12) < 0.6 or abs(d - 22) < 0.6 or abs(d - 34) < 0.6:
            m = 'ouro'
        if (abs(x) <= 2 or abs(z) <= 2) and d > 9:
            m = 'verm' if (abs(x) <= 1 or abs(z) <= 1) else 'verm2'
        if 22 < d < 34 and abs(abs(x) - abs(z)) <= 1 and d > 24:
            m = 'quartzo_chao'
        put(x, 0, z, m)

# ---------------------------------------------------------------- canteiros (grama de verdade + flores + topiaria)
for i in range(8):
    a = math.radians(i * 45 + 22.5)
    cx, cz = math.cos(a) * 17, math.sin(a) * 17
    cil(cx, cz, 3.2, 1, 1, 'grama')
    for x in range(int(cx) - 4, int(cx) + 5):
        for z in range(int(cz) - 4, int(cz) + 5):
            if B.get((x, 1, z)) == 'grama' and rnd.random() < 0.35:
                put(x, 2, z, 'capim')
    for k in range(6):
        b = a + k * 1.05
        put(round(cx + math.cos(b) * 2), 2, round(cz + math.sin(b) * 2), 'flor')
    for y in (2, 3, 4):
        put(round(cx), y, round(cz), 'folha')

# ---------------------------------------------------------------- estrado central, fonte e coroa
cil(0, 0, 10, 1, 1, 'pedra_lisa')
cil(0, 0, 8, 2, 2, 'quartzo')
cil(0, 0, 7, 2, 2, 'agua')
cil(0, 0, 7.6, 2, 2, 'ouro', oco=True)
cil(0, 0, 3, 2, 8, 'escura')
cil(0, 0, 3.6, 3, 3, 'ouro', oco=True)
cil(0, 0, 3.6, 8, 8, 'ouro', oco=True)
cil(0, 0, 4.5, 9, 10, 'ouro', oco=True)
for i in range(8):
    a = math.radians(i * 45)
    x, z = round(math.cos(a) * 4.3), round(math.sin(a) * 4.3)
    for y in range(11, 14 if i % 2 == 0 else 12):
        put(x, y, z, 'ouro')
    if i % 2 == 0:
        put(x, 14, z, 'gema')
cil(0, 0, 1.2, 9, 12, 'verm')
put(0, 13, 0, 'gema')
put(0, 14, 0, 'gema')
put(0, 15, 0, 'luz_mar')

# ---------------------------------------------------------------- 8 pilares com lanterna (anel de 12)
for i in range(8):
    a = math.radians(i * 45 + 22.5)
    x, z = round(math.cos(a) * 12.5), round(math.sin(a) * 12.5)
    for y in range(1, 6):
        put(x, y, z, 'pilar' if y < 5 else 'pilar_topo')
    put(x, 6, z, 'lanterna')

# ---------------------------------------------------------------- pavilhoes dos NPCs nas diagonais (o de sudoeste e a area das Chocadeiras)
PAVILHOES = [(20, -20), (20, 20), (-20, -20)]
for cx, cz in PAVILHOES:
    cil(cx, cz, 4, 1, 1, 'madeira')
    for dx, dz in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
        for y in range(2, 6):
            put(cx + dx, y, cz + dz, 'tronco')
    for k in range(4):
        cil(cx, cz, 4.6 - k * 1.2, 6 + k, 6 + k, 'telha')
    put(cx, 10, cz, 'ouro')
    put(cx, 5, cz, 'lanterna_pend')

# ---------------------------------------------------------------- portais (moldura + vao de portal)
PORTAIS = []   # (x, z, eixoX, nivel/"nether"/"end")


def portal(x, z, eixoX, moldura, detalhe, dentro, qual):
    """Vao 3 x 4 (como os portais antigos). eixoX: o vao se estende no x (atravessa no z)."""
    PORTAIS.append((x, z, eixoX, qual))
    for w in range(-2, 3):
        for dy in range(0, 6):
            px, pz = (x + w, z) if eixoX else (x, z + w)
            borda = abs(w) == 2 or dy == 0 or dy == 5
            if not borda:
                b = dentro
            elif abs(w) == 2 and (dy in (0, 5) or dy % 2 == 1):
                b = detalhe
            else:
                b = moldura
            put(px, dy, pz, b)
    put(x, 6, z, 'lanterna_alma')


NIVEL_VIS = {2: ('mossy_stone_bricks', 'oxidized_copper'), 3: ('deepslate_bricks', 'gold_block'),
             4: ('blackstone', 'diamond_block'), 5: ('obsidian', 'netherite_block')}

# 4 portoes nos eixos (N verde = nivel 1 aberto, L amarelo = 2, S laranja = 3, O vermelho = 4)
for (dx, dz), cor, nivel in (((0, -1), 'n1', 1), ((1, 0), 'n2', 2), ((0, 1), 'n3', 3), ((-1, 0), 'n4', 4)):
    px, pz = dx * 38, dz * 38
    lado = (1, 0) if dx == 0 else (0, 1)
    for s in (-1, 1):
        bx, bz = px + lado[0] * s * 4, pz + lado[1] * s * 4
        for y in range(1, 11):
            for t in (0, 1):
                put(bx + lado[0] * s * t, y, bz + lado[1] * s * t, 'escura' if y < 10 else 'ouro')
        put(bx, 11, bz, 'lanterna')
    for k in range(-4, 5):
        put(px + lado[0] * k, 10, pz + lado[1] * k, 'ouro')
        put(px + lado[0] * k, 11, pz + lado[1] * k, cor)
    for k in (-2, 0, 2):
        for y in (7, 8, 9):
            put(px + lado[0] * k, y, pz + lado[1] * k, cor)
    if nivel >= 2:
        mol, det = NIVEL_VIS[nivel]
        portal(px, pz, dx == 0, mol, det, 'portal_x' if dx == 0 else 'portal_z', nivel)

# portais avulsos na faixa de fora: nivel 5 (oeste, junto do 4), Nether e End (leste, junto do 2)
mol, det = NIVEL_VIS[5]
portal(-37, -13, False, mol, det, 'portal_z', 5)
portal(37, -13, False, 'obsidian', 'crying_obsidian', 'portal_z', 'nether')
portal(37, 13, False, 'obsidian', 'end_stone_bricks', 'portal_z', 'end')
# degrau colorido na frente de cada portal avulso (lado do centro)
for (x, z, ex, q), pis in zip(PORTAIS[-3:], ('crying_obsidian', 'magma_block', 'purpur_block')):
    sx = 1 if x < 0 else -1
    for w in range(-2, 3):
        for k in (1, 2):
            put(x + sx * k, 0, z + w, pis)

# volta ao spawn principal: estrado de ouro com esmeralda no meio, com arco (oeste-sul, simetrico ao nivel 5)
EX, EZ = -37, 13
for dx in (-1, 0, 1):
    for dz in (-1, 0, 1):
        put(EX + dx, 0, EZ + dz, 'esmeralda' if dx == 0 and dz == 0 else 'ouro')
for dz in (-2, 2):
    for y in range(1, 5):
        put(EX, y, EZ + dz, 'escura')
    put(EX, 5, EZ + dz, 'lanterna')
for dz in range(-2, 3):
    put(EX, 5 if abs(dz) < 2 else 4, EZ + dz, 'escura' if abs(dz) == 2 else 'n1')

# ---------------------------------------------------------------- placares (norte, dos dois lados da estrada)
for cx, cz in ((11, -26), (-11, -26)):
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            put(cx + dx, 1, cz + dz, 'escura' if dx or dz else 'pedestal')
    put(cx - 1, 2, cz - 1, 'lanterna')
    put(cx + 1, 2, cz + 1, 'lanterna')

# ---------------------------------------------------------------- campo de treino (leste-sul)
TX0, TX1, TZ0, TZ1 = 22, 30, 6, 15
for x in range(TX0, TX1 + 1):
    for z in range(TZ0, TZ1 + 1):
        borda = x in (TX0, TX1) or z in (TZ0, TZ1)
        put(x, 0, z, 'treino_borda' if borda else ('terra' if (x + z) % 2 == 0 else 'lama'))
        entrada = (x == TX0 and 9 <= z <= 12) or (z == TZ1 and 25 <= x <= 27)
        if borda and not entrada:
            put(x, 1, z, 'cerca')
BONECOS = [(24, 8), (28, 8), (24, 12), (28, 12)]
for x, z in BONECOS:
    put(x, 0, z, 'feno')

# ---------------------------------------------------------------- tapetes dos NPCs de missao (diagonais, r 15)
MISSAO = [(11, -11), (-11, -11), (-11, 11), (11, 11)]
for x, z in MISSAO:
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            put(x + dx, 0, z + dz, 'tapete' if dx or dz else 'tapete_meio')

# ---------------------------------------------------------------- muralha com ameias e torres nas diagonais
for i in range(1440):
    a = math.radians(i * 0.25)
    x, z = round(math.cos(a) * R), round(math.sin(a) * R)
    if abs(x) <= 5 and abs(z) > 30 or abs(z) <= 5 and abs(x) > 30:
        continue
    for y in range(1, 4):
        put(x, y, z, 'muro')
    if (i // 16) % 2 == 0:
        put(x, 4, z, 'muro')
for i in range(4):
    a = math.radians(45 + i * 90)
    cx, cz = math.cos(a) * R, math.sin(a) * R
    cil(cx, cz, 3.4, 1, 9, 'muro', oco=True)
    cil(cx, cz, 3.8, 10, 10, 'escura')
    cil(cx, cz, 2.6, 11, 11, 'telha')
    cil(cx, cz, 1.4, 12, 12, 'telha')
    put(round(cx), 13, round(cz), 'n5')
    put(round(cx), 14, round(cz), 'lanterna_alma')

# ---------------------------------------------------------------- luz invisivel nas noites (so onde e ar)
for x in range(-R + 3, R - 2, 7):
    for z in range(-R + 3, R - 2, 7):
        if math.hypot(x, z) < R - 2 and (x, 4, z) not in B and (x, 3, z) not in B and not choc(x, z):
            B[(x, 4, z)] = 'luz'

# ---------------------------------------------------------------- blocos de verdade
FLORES = ['poppy', 'allium', 'pink_tulip', 'cornflower', 'oxeye_daisy', 'azure_bluet', 'red_tulip']
MAPA = {
    'piso': 'stone_bricks', 'piso2': 'polished_andesite', 'ouro': 'gold_block', 'verm': 'red_nether_bricks',
    'verm2': 'nether_bricks', 'quartzo_chao': 'quartz_bricks', 'quartzo': 'smooth_quartz', 'pedra_lisa': 'smooth_stone',
    'escura': 'polished_deepslate', 'madeira': 'dark_oak_planks', 'tronco': 'dark_oak_log', 'telha': 'red_terracotta',
    'grama': 'grass_block', 'grama_chao': 'grass_block', 'estrada_fora': 'stone_bricks', 'capim': 'short_grass',
    'folha': 'flowering_azalea_leaves[persistent=true]', 'agua': 'water', 'gema': 'redstone_block', 'luz_mar': 'sea_lantern',
    'lanterna': 'lantern', 'lanterna_pend': 'lantern[hanging=true]', 'lanterna_alma': 'soul_lantern',
    'pilar': 'stone_bricks', 'pilar_topo': 'chiseled_stone_bricks', 'muro': 'stone_bricks',
    'n1': 'lime_concrete', 'n2': 'yellow_concrete', 'n3': 'orange_concrete', 'n4': 'red_concrete', 'n5': 'amethyst_block',
    'portal_x': 'nether_portal[axis=x]', 'portal_z': 'nether_portal[axis=z]', 'esmeralda': 'emerald_block',
    'pedestal': 'polished_blackstone_bricks', 'treino_borda': 'stripped_spruce_wood', 'terra': 'coarse_dirt',
    'lama': 'packed_mud', 'cerca': 'spruce_fence', 'feno': 'hay_block', 'tapete': 'polished_blackstone',
    'tapete_meio': 'polished_blackstone_bricks', 'luz': 'light[level=15]',
}


def bloco(m):
    if m == 'flor':
        return rnd.choice(FLORES)
    if m in MAPA:
        return MAPA[m]
    return m   # materiais diretos (moldura dos portais etc.)


if __name__ == '__main__':
    raiz = sys.argv[1] if len(sys.argv) > 1 else '.'
    pal, linhas = {}, []
    for (x, y, z), m in sorted(B.items(), key=lambda kv: (kv[0][1], kv[0][0], kv[0][2])):
        b = 'minecraft:' + bloco(m)
        if b not in pal:
            pal[b] = len(pal)
        linhas.append(f'{x} {y} {z} {pal[b]}')
    txt = '# lobby v5 do Mundo das Masmorras: P <indice> <bloco> / <x> <y> <z> <indice>\n'
    txt += ''.join(f'P {i} {b}\n' for b, i in pal.items()) + '\n'.join(linhas) + '\n'
    with gzip.open(os.path.join(raiz, 'plugins/masmorras/res/lobby5.txt.gz'), 'wt') as f:
        f.write(txt)
    print('blocos', len(linhas), 'paleta', len(pal), 'portais', PORTAIS)
