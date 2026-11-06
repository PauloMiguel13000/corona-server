"""Asas de hussardo (cosmetico do Traje do Rei: MVP e LENDA), iguais no Java e no Bedrock.

Desenho: duas asinhas presas atras dos ombros. Haste dourada que sobe ate mais ou menos a altura da cabeca, com
uma curvinha no alto; penas marrom-escuras em leque abrindo para fora. Vistas de cima, as asas abrem em "V"
(45 graus para fora das costas). Desenho original, inspirado nas armaduras de hussardo (sec. XVI-XVII).

Uma lista de pecas no quadro LOCAL da asa (L, em pixels de modelo):
  x = para fora (no plano da asa), y = para cima, z = espessura. Raiz (onde prende nas costas) = (0, 0, 0).
A partir dela saem:
  - Java: modelo de item com "elements" (corona:item/coroa/asa) + textura propria; o plugin mostra 2 ItemDisplay
    (so no cliente, por pacote) montados no jogador, um por asa.
  - Bedrock: geometria do peitoral (attachable corona:coroa_peitoral) com os ossos body/rightArm/leftArm
    da armadura e dois ossos de asa filhos de "body" (acompanham o corpo, inclusive agachado).
  - Previa (render com z-buffer) do jogador com o traje e as asas, de varios lados.

Uso: python3 -I asas_hussardo.py <pasta-saida> [<pasta-trajes com coroa_layer1/2.png>]
"""
import json, math, os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)
OUT = sys.argv[1] if len(sys.argv) > 1 else '/tmp/claude-0/asas'
TRAJES = sys.argv[2] if len(sys.argv) > 2 else '/tmp/claude-0/trajes'
os.makedirs(OUT, exist_ok=True)
import trajes_hd as T  # noqa: E402  (Face, materiais, S=8)

S = T.S
T.MAT['pena'] = ((0.25, 0.135, 0.060), 0.22, 16, 0.0)
T.MAT['pena2'] = ((0.255, 0.140, 0.062), 0.24, 18, 0.0)
T.MAT['pena3'] = ((0.185, 0.098, 0.046), 0.20, 14, 0.0)
T.MAT['raque'] = ((0.60, 0.47, 0.30), 0.30, 24, 0.0)

# ------------------------------------------------------------------ medidas (pixels de modelo do jogador)
ESCALA_JOGADOR = 0.9375          # o jogador e desenhado em 0.9375 (Java e Bedrock)
RAIZ_X, RAIZ_Y, RAIZ_Z = 5.0, 20.0, -3.3     # raiz da asa direita = (-RAIZ_X, RAIZ_Y, RAIZ_Z); costas = -z
ABERTURA = 45.0                  # graus para fora das costas, vista de cima
# quadro K (do jogador): frente = +z, direita do jogador = -x, cima = +y, pes em y=0 (igual ao modelo Java)
# asa direita: L(+x) aponta para tras-direita -> giro em y de 135 graus; esquerda: 45 graus
BETA = {'direita': 180.0 - ABERTURA, 'esquerda': ABERTURA}
RAIZ = {'direita': (-RAIZ_X, RAIZ_Y, RAIZ_Z), 'esquerda': (RAIZ_X, RAIZ_Y, RAIZ_Z)}

# ------------------------------------------------------------------ atlas de amostras (8 x 16 unidades, 8 px/unid)
AW, AH = 8, 16
AMOSTRA = {
    'pena1': (0, 0, 6, 2), 'pena2': (0, 2, 6, 4), 'pena3': (0, 4, 6, 6),
    'haste1': (6, 0, 7, 8), 'haste2': (7, 0, 8, 8),
    'tampa': (6, 8, 7, 9), 'rubi': (7, 8, 8, 9),
    'ponta': (0, 6, 2, 8), 'base': (2, 6, 4, 8), 'base_lado': (4, 6, 6, 8),
}


def ss(a, b, x):
    return T.ss(a, b, x)


def pintar_pena(var):
    """Pena 6x2 unidades: base (cano) a esquerda, ponta fina a direita, curvinha para cima no fim."""
    f = T.Face(6, 2, 700 + var * 13)
    larg_max = {1: 1.62, 2: 1.55, 3: 1.66}[var]
    xs = np.linspace(0.0, 6.0, 120)
    w = 0.30 + (larg_max - 0.30) * ss(0.05, 1.35, xs)
    w = w * np.clip((5.97 - xs) / 2.5, 0, 1) ** 0.75          # afina ate a ponta
    yc = 1.06 - 0.17 * (xs / 6.0) ** 2                       # a ponta sobe um pouco
    cima = [(x, c - 0.38 * ww) for x, c, ww in zip(xs, yc, w)]       # barba da frente, mais estreita
    baixo = [(x, c + 0.62 * ww) for x, c, ww in zip(xs, yc, w)]
    rasgos = {1: [(3.3, 'b')], 2: [(2.7, 'c'), (4.2, 'b')], 3: [(3.0, 'b'), (4.4, 'c')]}[var]

    def rasgar(pts, lado):
        out = []
        for x, y in pts:
            for rx, l in rasgos:
                if l == lado and abs(x - rx) < 0.16:
                    k = 1 - abs(x - rx) / 0.16
                    c = 1.06 - 0.17 * (x / 6.0) ** 2
                    y = y + (c - y) * 0.5 * k
            out.append((x, y))
        return out
    pts = rasgar(cima, 'c') + rasgar(baixo, 'b')[::-1]
    d = f.poli(pts)
    base = {1: 'pena', 2: 'pena', 3: 'pena3'}[var]
    f.pintar(d, base, alt=0.28, bisel=0.22, ruido=0.12, escala_ruido=3.0)
    ycf = 1.06 - 0.17 * (f.x / 6.0) ** 2
    dy = f.y - ycf
    dr = np.abs(dy)
    dentro = (d < 0)
    # cor: mais quente perto do cano, borda escura, meio do comprimento um pouco mais claro
    perto = np.exp(-dr / 0.30)
    meio = np.exp(-((f.x - 3.0) / 1.8) ** 2)
    k = 1 + 0.45 * perto + 0.22 * meio
    f.alb = np.where(dentro[..., None], f.alb * k[..., None], f.alb)
    borda = ss(-0.16, 0.0, d)
    f.alb = f.alb * (1 - 0.40 * borda[..., None])
    # barbas: listras finas inclinadas do cano para a ponta (cor + relevo)
    fase = (f.x - dr * 1.25) / 0.27
    lis = 0.5 + 0.5 * np.cos(2 * math.pi * fase)
    f.alb = np.where(dentro[..., None], f.alb * (0.86 + 0.24 * lis)[..., None], f.alb)
    f.alt += 0.05 * lis * dentro
    f.alt += 0.08 * np.clip(1 - dr / 0.55, 0, 1) * dentro       # mais alta no meio (curva da pena)
    # cano (raque) claro, afinando
    raque = dr - 0.07 * (1 - f.x / 6.4)
    f.pintar(np.where(f.x < 5.6, raque, 1.0), 'raque', alt=0.42, bisel=0.05, ruido=0.05)
    # bocal de ouro onde a pena entra na haste
    boc = f.ret(0.0, 0.66, 0.58, 1.42, 0.12)
    f.pintar(boc, 'ouro', alt=0.55, bisel=0.12)
    f.cavar(f.ret(0.40, 0.64, 0.44, 1.44), 0.08, 0.03)
    return f.render()


def pintar_haste(var):
    f = T.Face(1, 8, 760 + var)
    tudo = f.tudo()
    f.pintar(tudo, 'ouro', alt=0.4)
    # aneis gravados e rebites
    for y in np.arange(0.5, 8.0, 1.0):
        f.cavar(f.ret(-1, y - 0.03, 2, y + 0.03), 0.10, 0.04)
    if var == 1:
        for y in np.arange(1.0, 8.0, 2.0):
            f.joia(0.5, y, 0.20, 'rubi', brilho=0.35)
    else:
        # escamas de ouro (como a haste de verdade, forrada de metal)
        for y in np.arange(0.0, 8.0, 0.5):
            f.cavar(np.abs(np.hypot(f.x - 0.5, f.y - y) - 0.35), 0.06, 0.04)
        for y in np.arange(1.0, 8.0, 2.0):
            f.rebite(0.5, y, 0.18)
    return f.render()


def pintar_quadrado(tipo, w=1, h=1):
    f = T.Face(w, h, 790 + len(tipo))
    tudo = f.tudo()
    if tipo == 'tampa':
        f.pintar(tudo, 'ouro2', alt=0.3)
        f.rebite(0.5, 0.5, 0.26)
    elif tipo == 'rubi':
        f.pintar(tudo, 'ouro')
        f.joia(0.5, 0.5, 0.33, 'rubi', brilho=0.5)
    elif tipo == 'ponta':
        f.pintar(tudo, 'ouro', alt=0.3)
        f.friso(f.ret(0, 0, w, h, 0.2), 'ouro2', 0.25, 0.2)
        f.joia(1.0, 1.0, 0.45, 'rubi', brilho=0.5)
    elif tipo == 'base':
        f.pintar(tudo, 'ouro', alt=0.3)
        f.filigrana(f.ret(0, 0, w, h, 0.2), margem=0.25, escala=0.5, fase=0.6)
        f.friso(f.ret(0, 0, w, h, 0.15), 'ouro2', 0.22, 0.22)
        for cx, cy in ((0.4, 0.4), (1.6, 0.4), (0.4, 1.6), (1.6, 1.6)):
            f.rebite(cx, cy, 0.18)
        f.joia(1.0, 1.0, 0.32, 'rubi', brilho=0.4)
    elif tipo == 'base_lado':
        f.pintar(tudo, 'ouro2', alt=0.3)
        f.friso(f.ret(0, 0, w, h, 0.15), 'ouro', 0.25, 0.2)
        f.rebite(1.0, 1.0, 0.22)
    return f.render()


def montar_atlas():
    atlas = np.zeros((AH * S, AW * S, 4), np.uint8)

    def colar(nome, img):
        x0, y0, x1, y1 = AMOSTRA[nome]
        atlas[y0 * S:y0 * S + img.shape[0], x0 * S:x0 * S + img.shape[1]] = img
    for v in (1, 2, 3):
        colar(f'pena{v}', pintar_pena(v))
    colar('haste1', pintar_haste(1))
    colar('haste2', pintar_haste(2))
    colar('tampa', pintar_quadrado('tampa'))
    colar('rubi', pintar_quadrado('rubi'))
    colar('ponta', pintar_quadrado('ponta', 2, 2))
    colar('base', pintar_quadrado('base', 2, 2))
    colar('base_lado', pintar_quadrado('base_lado', 2, 2))
    return atlas


# ------------------------------------------------------------------ pecas da asa (quadro L)
FACES = {   # igual ao kit: normal, eixo u, eixo v (v cresce para baixo na textura)
    'north': ((0, 0, -1), (-1, 0, 0), (0, -1, 0)),
    'south': ((0, 0, 1), (1, 0, 0), (0, -1, 0)),
    'east': ((1, 0, 0), (0, 0, -1), (0, -1, 0)),
    'west': ((-1, 0, 0), (0, 0, 1), (0, -1, 0)),
    'up': ((0, 1, 0), (1, 0, 0), (0, 0, 1)),
    'down': ((0, -1, 0), (1, 0, 0), (0, 0, -1)),
}


class Peca:
    def __init__(self, nome, de, ate, uv, rot=None):
        """uv: face -> (x0, y0, x1, y1) em UNIDADES do atlas (pode vir invertido para espelhar).
        rot: (angulo em z, origem) no quadro L."""
        self.nome, self.de, self.ate, self.uv, self.rot = nome, np.array(de, float), np.array(ate, float), uv, rot


def sub(nome, a=0.0, b=1.0, flip=False, vert=False):
    """Pedaco da amostra: fracao a..b no comprimento (u, ou v se vert)."""
    x0, y0, x1, y1 = AMOSTRA[nome]
    if vert:
        y0, y1 = y0 + (y1 - y0) * a, y0 + (y1 - y0) * b
    else:
        x0, x1 = x0 + (x1 - x0) * a, x0 + (x1 - x0) * b
    return (x1, y0, x0, y1) if flip else (x0, y0, x1, y1)


SEGMENTOS = [(4.8, -24.0), (4.2, -10.0), (3.0, 6.0)]   # (comprimento, giro em z) da haste, de baixo para cima
INICIO = np.array([0.3, -0.2])


def caminho_haste():
    """Pontos (x, y) e direcoes ao longo da haste: lista de (inicio, direcao, comprimento, angulo)."""
    p = INICIO.copy()
    out = []
    for L, a in SEGMENTOS:
        r = math.radians(a)
        d = np.array([-math.sin(r), math.cos(r)])
        out.append((p.copy(), d, L, a))
        p = p + L * d
    return out, p


def ponto_haste(s):
    segs, fim = caminho_haste()
    for p, d, L, a in segs:
        if s <= L:
            return p + s * d, a
        s -= L
    return fim, segs[-1][3]


def pecas_asa():
    pecas = []
    segs, fim = caminho_haste()
    total = sum(L for _, _, L, _ in segs)
    acum = 0.0
    for i, (p, d, L, a) in enumerate(segs):
        ext = 0.28
        lados = sub('haste1' if i != 1 else 'haste2', acum / total * 0.0, min(1.0, (L + 2 * ext) / 8.0), vert=True)
        uv = {f: lados for f in ('north', 'south', 'east', 'west')}
        uv['up'] = uv['down'] = sub('tampa')
        pecas.append(Peca(f'haste{i}', (p[0] - 0.5, p[1] - ext, -0.5), (p[0] + 0.5, p[1] + L + ext, 0.5), uv,
                          rot=(a, (p[0], p[1], 0.0))))
        acum += L
    # encaixe (base) que prende nas costas
    bx, by = INICIO
    uvb = {'north': sub('base'), 'south': sub('base'), 'east': sub('base_lado'), 'west': sub('base_lado'),
           'up': sub('tampa'), 'down': sub('tampa')}
    pecas.append(Peca('base', (bx - 0.85, by - 1.2, -0.8), (bx + 0.85, by + 0.7, 0.8), uvb))
    # ponteira no alto (com rubi)
    a3 = SEGMENTOS[-1][1]
    uvp = {f: sub('ponta') for f in ('north', 'south', 'east', 'west')}
    uvp['up'] = sub('rubi')
    uvp['down'] = sub('tampa')
    pecas.append(Peca('ponta', (fim[0] - 0.65, fim[1] - 0.2, -0.65), (fim[0] + 0.65, fim[1] + 1.1, 0.65), uvp,
                      rot=(a3, (fim[0], fim[1], 0.0))))
    # penas principais: leque abrindo para fora (de -16 graus embaixo ate +42 em cima)
    N = 8
    comp = [3.4, 3.9, 4.3, 4.6, 4.8, 4.8, 4.5, 4.0]
    larg = [1.75, 1.85, 1.95, 2.0, 2.0, 2.0, 1.95, 1.75]
    for i in range(N):
        s = 1.0 + (total - 1.6) * i / (N - 1)
        P, _ = ponto_haste(s)
        th = -20.0 + 54.0 * i / (N - 1)
        z = 0.12 if i % 2 == 0 else -0.12
        var = 1 if i % 2 == 0 else 2
        uv = {'south': sub(f'pena{var}'), 'north': sub(f'pena{var}', flip=True)}
        x0 = P[0] + 0.1
        pecas.append(Peca(f'pena{i}', (x0, P[1] - larg[i] / 2, z), (x0 + comp[i], P[1] + larg[i] / 2, z), uv,
                          rot=(round(th, 2), (P[0], P[1], z))))
    # segunda camada (penas de baixo, mais curtas e escuras, entre as principais): da volume
    for j in range(N - 1):
        s = 1.0 + (total - 1.6) * (j + 0.5) / (N - 1)
        P, _ = ponto_haste(s)
        th = -20.0 + 54.0 * (j + 0.5) / (N - 1) + 4.0
        z = 0.38
        L = (comp[j] + comp[j + 1]) / 2 * 0.80
        W = (larg[j] + larg[j + 1]) / 2 * 0.92
        uv = {'south': sub('pena3'), 'north': sub('pena3', flip=True)}
        x0 = P[0] + 0.1
        pecas.append(Peca(f'pena_b{j}', (x0, P[1] - W / 2, z), (x0 + L, P[1] + W / 2, z), uv,
                          rot=(round(th, 2), (P[0], P[1], z))))
    for pc in pecas:
        if pc.rot and not (-45.0 <= pc.rot[0] <= 45.0):
            raise ValueError(f'{pc.nome}: giro {pc.rot[0]} fora de -45..45 (Java)')
    return pecas


def faces_da_peca(pc):
    """Faces desenhaveis (planos de espessura 0 so tem norte/sul)."""
    t = pc.ate - pc.de
    out = []
    for f, (n, ua, va) in FACES.items():
        if f not in pc.uv:
            continue
        n = np.array(n, float)
        if abs(n @ t) == 0 and np.count_nonzero(t) == 3:
            pass
        ua, va = np.array(ua, float), np.array(va, float)
        eu, ev = abs(ua @ t), abs(va @ t)
        if eu < 1e-6 or ev < 1e-6:
            continue
        out.append(f)
    return out


def cantos_face(pc, f):
    n, ua, va = (np.array(v, float) for v in FACES[f])
    t = pc.ate - pc.de
    c = (pc.de + pc.ate) / 2
    eu, ev = abs(ua @ t), abs(va @ t)
    p0 = c + n * (np.abs(n) @ t) / 2
    return [p0 + (s - 0.5) * eu * ua + (tt - 0.5) * ev * va for s, tt in ((0, 0), (1, 0), (1, 1), (0, 1))], n


def rot_z(p, ang, orig):
    a = math.radians(ang)
    x, y = p[0] - orig[0], p[1] - orig[1]
    return np.array([x * math.cos(a) - y * math.sin(a) + orig[0], x * math.sin(a) + y * math.cos(a) + orig[1], p[2]])


def rot_y(p, ang):
    a = math.radians(ang)
    return np.array([p[0] * math.cos(a) + p[2] * math.sin(a), p[1], -p[0] * math.sin(a) + p[2] * math.cos(a)])


# ------------------------------------------------------------------ Java
CENTRO = np.array([8.0, 8.0, 8.0])     # raiz da asa no meio do modelo (o display gira em volta dela)


def modelo_java(pecas):
    els = []
    for pc in pecas:
        faces = {}
        for f in faces_da_peca(pc):
            x0, y0, x1, y1 = pc.uv[f]
            faces[f] = {'uv': [round(x0 * 16 / AW, 4), round(y0 * 16 / AH, 4), round(x1 * 16 / AW, 4), round(y1 * 16 / AH, 4)],
                        'texture': '#0'}
        de, ate = pc.de + CENTRO, pc.ate + CENTRO
        e = {'from': [round(float(v), 3) for v in de], 'to': [round(float(v), 3) for v in ate], 'faces': faces}
        for v in list(de) + list(ate):
            if v < -16 or v > 32:
                raise ValueError(f'{pc.nome}: {v} fora de -16..32')
        if pc.rot and pc.rot[0] != 0:
            o = np.array(pc.rot[1]) + CENTRO
            e['rotation'] = {'origin': [round(float(v), 3) for v in o], 'axis': 'z', 'angle': float(pc.rot[0])}
        els.append(e)
    return {'credit': 'Corona Server - asas de hussardo (desenho original)', 'texture_size': [AW * S, AH * S],
            'textures': {'0': 'corona:item/coroa/asa', 'particle': 'corona:item/coroa/asa'}, 'elements': els}


# ------------------------------------------------------------------ Bedrock
UV_BASE = (56, 16)    # onde o atlas entra na textura da armadura (64x32)
TROCA = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east', 'up': 'up', 'down': 'down'}


def bedrock_cubos(pecas, raiz):
    """Pecas do quadro L transladadas para a raiz (sem o giro da asa, que fica no osso), no formato Bedrock.
    K -> arquivo Bedrock: x igual, z trocado de sinal (igual ao kit, entidade_bedrock)."""
    rx, ry, rz = raiz
    cubos = []
    for pc in pecas:
        de = pc.de + np.array([rx, ry, rz])
        ate = pc.ate + np.array([rx, ry, rz])
        t = ate - de
        cube = {'origin': [round(float(de[0]), 4), round(float(de[1]), 4), round(float(-ate[2]), 4)],
                'size': [round(float(v), 4) for v in t], 'uv': {}}
        for f in faces_da_peca(pc):
            x0, y0, x1, y1 = pc.uv[f]
            cube['uv'][TROCA[f]] = {'uv': [round(x0 + UV_BASE[0], 4), round(y0 + UV_BASE[1], 4)],
                                    'uv_size': [round(x1 - x0, 4), round(y1 - y0, 4)]}
        if pc.rot and pc.rot[0] != 0:
            o = np.array(pc.rot[1]) + np.array([rx, ry, rz])
            cube['pivot'] = [round(float(o[0]), 4), round(float(o[1]), 4), round(float(-o[2]), 4)]
            cube['rotation'] = [0.0, 0.0, -float(pc.rot[0])]
        cubos.append(cube)
    return cubos


def geometria_peitoral(pecas):
    bones = [
        {'name': 'body', 'pivot': [0, 24, 0], 'cubes': [{'origin': [-4, 12, -2], 'size': [8, 12, 4], 'uv': [16, 16], 'inflate': 1.01}]},
        {'name': 'rightArm', 'pivot': [-5, 22, 0], 'cubes': [{'origin': [-8, 12, -2], 'size': [4, 12, 4], 'uv': [40, 16], 'inflate': 1.0}]},
        {'name': 'leftArm', 'pivot': [5, 22, 0], 'cubes': [{'origin': [4, 12, -2], 'size': [4, 12, 4], 'uv': [40, 16], 'inflate': 1.0, 'mirror': True}]},
    ]
    for lado in ('direita', 'esquerda'):
        r = RAIZ[lado]
        bones.append({'name': f'asa_{lado}', 'parent': 'body', 'pivot': [r[0], r[1], -r[2]],
                      'rotation': [0.0, -BETA[lado], 0.0], 'cubes': bedrock_cubos(pecas, r)})
    return {'format_version': '1.16.0', 'minecraft:geometry': [{
        'description': {'identifier': 'geometry.corona.peitoral_asas', 'texture_width': 64, 'texture_height': 32,
                        'visible_bounds_width': 3, 'visible_bounds_height': 3.5, 'visible_bounds_offset': [0, 1.5, 0]},
        'bones': bones}]}


# ------------------------------------------------------------------ conferencias (Java e Bedrock dao o mesmo lugar)
def vertices_k(pecas, lado):
    """Vertices de cada face no quadro K (pixels do modelo do jogador), direto do desenho."""
    out = []
    for pc in pecas:
        for f in faces_da_peca(pc):
            cs, _ = cantos_face(pc, f)
            for p in cs:
                if pc.rot and pc.rot[0] != 0:
                    p = rot_z(p, pc.rot[0], pc.rot[1])
                out.append(rot_y(p, BETA[lado]) + np.array(RAIZ[lado]))
    return np.array(out)


def ry_mat(deg):
    a = math.radians(deg)
    return np.array([[math.cos(a), 0, math.sin(a)], [0, 1, 0], [-math.sin(a), 0, math.cos(a)]])


def rx_mat(rad):
    return np.array([[1, 0, 0], [0, math.cos(rad), -math.sin(rad)], [0, math.sin(rad), math.cos(rad)]])


def matriz_display(lado, agachado=False):
    """Matriz 4x4 (blocos) que o plugin poe no ItemDisplay montado no jogador. O ponto de montaria do jogador fica
    no alto da caixa (1.8 em pe, 1.5 agachado). O ItemDisplay ja gira o modelo 180 graus em y depois desta matriz."""
    s = ESCALA_JOGADOR / 16.0
    raiz = np.array(RAIZ[lado])
    R = ry_mat(BETA[lado] - 180.0) * ESCALA_JOGADOR
    M = np.eye(4)
    if not agachado:
        M[:3, :3] = R
        M[:3, 3] = raiz * s - np.array([0, 1.8, 0])
    else:
        pesc_px = np.array([0, 24.0, 0])
        pesc_w = np.array([0, (24.0 - 3.2) * s - 0.125, 0])
        Rx = rx_mat(0.5)
        M[:3, :3] = Rx @ R
        M[:3, 3] = pesc_w - np.array([0, 1.5, 0]) + Rx @ ((raiz - pesc_px) * s)
    return M


def vertices_java(pecas, lado):
    """O mesmo, mas pelo caminho do Java: modelo (0..16) -> -0.5 -> giro 180 em y do ItemDisplay -> matriz -> +1.8."""
    mod = modelo_java(pecas)
    M = matriz_display(lado)
    giro = ry_mat(180.0)
    out = []
    for e, pc in zip(mod['elements'], pecas):
        de, ate = np.array(e['from']), np.array(e['to'])
        fake = Peca('x', de, ate, pc.uv, rot=(e['rotation']['angle'], e['rotation']['origin']) if 'rotation' in e else None)
        for f in faces_da_peca(fake):
            cs, _ = cantos_face(fake, f)
            for p in cs:
                if fake.rot:
                    p = rot_z(p, fake.rot[0], fake.rot[1])
                q = giro @ (p / 16.0 - 0.5)
                q = M[:3, :3] @ q + M[:3, 3] + np.array([0, 1.8, 0])
                out.append(q / (ESCALA_JOGADOR / 16.0))
    return np.array(out)


def vertices_bedrock(geo, lado):
    """Le a geometria Bedrock gerada e volta para o quadro K (convencao do kit, ja testada nos monstros)."""
    bone = [b for b in geo['minecraft:geometry'][0]['bones'] if b['name'] == f'asa_{lado}'][0]
    piv = np.array(bone['pivot'], float)
    ry = bone['rotation'][1]
    out = []
    for c in bone['cubes']:
        o, t = np.array(c['origin'], float), np.array(c['size'], float)
        # arquivo -> K: x igual, z troca de sinal
        de = np.array([o[0], o[1], -(o[2] + t[2])])
        ate = de + t
        rot = None
        if 'rotation' in c:
            pv = np.array(c['pivot'], float)
            rot = (-c['rotation'][2], (pv[0], pv[1], -pv[2]))
        uvk = {TROCA[f]: (0, 0, 1, 1) for f in c['uv']}
        fake = Peca('x', de, ate, uvk, rot=rot)
        for f in faces_da_peca(fake):
            cs, _ = cantos_face(fake, f)
            for p in cs:
                if rot:
                    p = rot_z(p, rot[0], rot[1])
                pk = np.array([piv[0], piv[1], -piv[2]])
                p = rot_y(p - pk, -ry) + pk
                out.append(p)
    return np.array(out)


# ------------------------------------------------------------------ previa: rasterizador com z-buffer
class Cena:
    def __init__(self):
        self.quads = []   # (cantos 4x3 em K, uv 4x2 em pixels da textura, textura RGBA float, brilho)

    def quad(self, cantos, uvs, tex):
        self.quads.append((np.array(cantos, float), np.array(uvs, float), tex))

    def caixa(self, de, ate, uv0, tam, tex, inflar=0.0, espelho=False, escala_tex=S):
        """Caixa com UV padrao de armadura/pele (u, v em unidades 64x32; tam = (w, h, d))."""
        u, v = uv0
        w, h, d = tam
        x0, y0, z0 = (np.array(de) - inflar)
        x1, y1, z1 = (np.array(ate) + inflar)
        # espelho: monta como o membro direito (de/ate do lado direito) e no fim espelha em x
        R = lambda a, b, c, e: [(a * escala_tex, b * escala_tex), ((a + c) * escala_tex, b * escala_tex),
                                ((a + c) * escala_tex, (b + e) * escala_tex), (a * escala_tex, (b + e) * escala_tex)]
        faces = [
            ([(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)], R(u + d, v + d, w, h)),           # frente
            ([(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)], R(u + 2 * d + w, v + d, w, h)),   # costas
            ([(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)], R(u, v + d, d, h)),               # lado direito
            ([(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)], R(u + d + w, v + d, d, h)),       # lado esquerdo
            ([(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)], R(u + d, v, w, d)),               # cima
            ([(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)], R(u + d + w, v, w, d)),           # baixo
        ]
        for cs, uvs in faces:
            if espelho:
                cs = [(-a, b, c) for a, b, c in cs]
            self.quad(cs, uvs, tex)

    def asa(self, pecas, lado, atlas):
        for pc in pecas:
            for f in faces_da_peca(pc):
                cs, _ = cantos_face(pc, f)
                pts = []
                for p in cs:
                    if pc.rot and pc.rot[0] != 0:
                        p = rot_z(p, pc.rot[0], pc.rot[1])
                    pts.append(rot_y(p, BETA[lado]) + np.array(RAIZ[lado]))
                x0, y0, x1, y1 = pc.uv[f]
                uvs = [(x0 * S, y0 * S), (x1 * S, y0 * S), (x1 * S, y1 * S), (x0 * S, y1 * S)]
                self.quad(pts, uvs, atlas)

    def render(self, yaw, pitch, escala=14, ss_=2, fundo=(40, 44, 54), tam=None, centro=(0, 17)):
        """Ortografico. yaw: giro da camera em volta do jogador (0 = de frente; 180 = de costas)."""
        e = escala * ss_
        cy_, sy_ = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
        cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

        def ver(P):
            x, y, z = P[:, 0], P[:, 1] - centro[1], P[:, 2]
            # camera olha para -z da vista; yaw gira o mundo em volta de y
            xv = x * cy_ - z * sy_
            zv = x * sy_ + z * cy_
            yv = y * cp - zv * sp
            zv2 = y * sp + zv * cp
            return np.stack([xv, yv, zv2], 1)
        W, H = (tam or (40, 44))
        W, H = int(W * e), int(H * e)
        cor = np.zeros((H, W, 3))
        cor[:] = np.array(fundo) / 255.0
        z = np.full((H, W), -1e9)
        luz = np.array([-0.35, 0.80, 0.50])
        luz /= np.linalg.norm(luz)
        for cs, uvs, tex in self.quads:
            v = ver(cs)
            sx = v[:, 0] * e + W / 2
            sy = -v[:, 1] * e + H / 2
            n = np.cross(cs[1] - cs[0], cs[3] - cs[0])
            nn = np.linalg.norm(n)
            if nn < 1e-9:
                continue
            n /= nn
            nv = np.cross(v[1] - v[0], v[3] - v[0])
            if nv[2] < 0:
                n = -n          # face dupla: luz do lado que aparece
            sombra = 0.58 + 0.42 * max(0.0, float(n @ luz)) + 0.10 * max(0.0, n[1])
            th, tw = tex.shape[:2]
            for tri in ((0, 1, 2), (0, 2, 3)):
                ax, ay = sx[list(tri)], sy[list(tri)]
                az = v[list(tri), 2]
                tu, tv = uvs[list(tri), 0], uvs[list(tri), 1]
                xmin, xmax = int(max(0, math.floor(ax.min()))), int(min(W - 1, math.ceil(ax.max())))
                ymin, ymax = int(max(0, math.floor(ay.min()))), int(min(H - 1, math.ceil(ay.max())))
                if xmin > xmax or ymin > ymax:
                    continue
                den = (ay[1] - ay[2]) * (ax[0] - ax[2]) + (ax[2] - ax[1]) * (ay[0] - ay[2])
                if abs(den) < 1e-9:
                    continue
                yy, xx = np.mgrid[ymin:ymax + 1, xmin:xmax + 1]
                px, py = xx + 0.5, yy + 0.5
                l0 = ((ay[1] - ay[2]) * (px - ax[2]) + (ax[2] - ax[1]) * (py - ay[2])) / den
                l1 = ((ay[2] - ay[0]) * (px - ax[2]) + (ax[0] - ax[2]) * (py - ay[2])) / den
                l2 = 1 - l0 - l1
                dentro = (l0 >= -1e-4) & (l1 >= -1e-4) & (l2 >= -1e-4)
                if not dentro.any():
                    continue
                zz = l0 * az[0] + l1 * az[1] + l2 * az[2]
                u = l0 * tu[0] + l1 * tu[1] + l2 * tu[2]
                vv = l0 * tv[0] + l1 * tv[1] + l2 * tv[2]
                iu = np.clip(np.floor(u).astype(int), 0, tw - 1)
                iv = np.clip(np.floor(vv).astype(int), 0, th - 1)
                texel = tex[iv, iu]
                ok = dentro & (texel[..., 3] > 0.5) & (zz > z[ymin:ymax + 1, xmin:xmax + 1])
                if not ok.any():
                    continue
                zsub = z[ymin:ymax + 1, xmin:xmax + 1]
                csub = cor[ymin:ymax + 1, xmin:xmax + 1]
                zsub[ok] = zz[ok]
                csub[ok] = texel[ok][:, :3] * sombra
        img = Image.fromarray((np.clip(cor, 0, 1) * 255).astype(np.uint8), 'RGB')
        return img.resize((W // ss_, H // ss_), Image.LANCZOS)


def cena_jogador(pecas, atlas_f, l1, l2, com_asas=True):
    c = Cena()
    pele = np.zeros((32 * S, 64 * S, 4))
    pele[...] = (0.30, 0.24, 0.21, 1.0)
    # corpo (pele) por baixo
    c.caixa((-4, 24, -4), (4, 32, 4), (0, 0), (8, 8, 8), pele)
    c.caixa((-4, 12, -2), (4, 24, 2), (16, 16), (8, 12, 4), pele)
    c.caixa((-8, 12, -2), (-4, 24, 2), (40, 16), (4, 12, 4), pele)
    c.caixa((-8, 12, -2), (-4, 24, 2), (40, 16), (4, 12, 4), pele, espelho=True)
    c.caixa((-4, 0, -2), (0, 12, 2), (0, 16), (4, 12, 4), pele)
    c.caixa((-4, 0, -2), (0, 12, 2), (0, 16), (4, 12, 4), pele, espelho=True)
    # calca (camada 2, 0.5)
    c.caixa((-4, 12, -2), (4, 24, 2), (16, 16), (8, 12, 4), l2, inflar=0.5)
    c.caixa((-4, 0, -2), (0, 12, 2), (0, 16), (4, 12, 4), l2, inflar=0.5)
    c.caixa((-4, 0, -2), (0, 12, 2), (0, 16), (4, 12, 4), l2, inflar=0.5, espelho=True)
    # camada 1 (1.0): elmo, coroa (1.5), peitoral, bracos, botas
    c.caixa((-4, 24, -4), (4, 32, 4), (0, 0), (8, 8, 8), l1, inflar=1.0)
    c.caixa((-4, 24, -4), (4, 32, 4), (32, 0), (8, 8, 8), l1, inflar=1.5)
    c.caixa((-4, 12, -2), (4, 24, 2), (16, 16), (8, 12, 4), l1, inflar=1.01)
    c.caixa((-8, 12, -2), (-4, 24, 2), (40, 16), (4, 12, 4), l1, inflar=1.0)
    c.caixa((-8, 12, -2), (-4, 24, 2), (40, 16), (4, 12, 4), l1, inflar=1.0, espelho=True)
    c.caixa((-4, 0, -2), (0, 12, 2), (0, 16), (4, 12, 4), l1, inflar=1.0)
    c.caixa((-4, 0, -2), (0, 12, 2), (0, 16), (4, 12, 4), l1, inflar=1.0, espelho=True)
    if com_asas:
        for lado in ('direita', 'esquerda'):
            c.asa(pecas, lado, atlas_f)
    return c


def fonte(tam):
    for f in ('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', '/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf'):
        if os.path.exists(f):
            return ImageFont.truetype(f, tam)
    return ImageFont.load_default()


def previas(pecas, atlas):
    atlas_f = atlas.astype(float) / 255.0
    l1 = np.array(Image.open(f'{TRAJES}/coroa_layer1.png').convert('RGBA'), float) / 255.0
    l2 = np.array(Image.open(f'{TRAJES}/coroa_layer2.png').convert('RGBA'), float) / 255.0
    cena = cena_jogador(pecas, atlas_f, l1, l2)
    vistas = [('Costas (3/4)', 215, 14), ('Costas', 180, 6), ('Lado', 90, 4), ('Frente (3/4)', 35, 8), ('De cima', 180, 86)]
    imgs = []
    for nome, yaw, pit in vistas:
        im = cena.render(yaw, pit, escala=13, tam=(36, 42))
        imgs.append((nome, im))
    pad, topo = 18, 46
    W = sum(i.size[0] for _, i in imgs) + pad * (len(imgs) + 1)
    H = max(i.size[1] for _, i in imgs) + topo + pad
    folha = Image.new('RGB', (W, H), (30, 33, 41))
    d = ImageDraw.Draw(folha)
    ft = fonte(22)
    x = pad
    for nome, im in imgs:
        folha.paste(im, (x, topo))
        d.text((x + im.size[0] // 2, 14), nome, fill=(236, 226, 200), font=ft, anchor='mt')
        x += im.size[0] + pad
    folha.save(f'{OUT}/previa_asas.png', optimize=True)
    # close das asas (costas 3/4, maior)
    big = cena.render(205, 18, escala=30, tam=(30, 22), centro=(0, 25))
    big.save(f'{OUT}/previa_asas_zoom.png', optimize=True)
    return folha


if __name__ == '__main__':
    pecas = pecas_asa()
    atlas = montar_atlas()
    Image.fromarray(atlas, 'RGBA').save(f'{OUT}/asa.png', optimize=True)
    Image.fromarray(atlas, 'RGBA').resize((AW * S * 4, AH * S * 4), Image.NEAREST).save(f'{OUT}/atlas_x4.png')
    mod = modelo_java(pecas)
    with open(f'{OUT}/asa_modelo.json', 'w') as f:
        json.dump(mod, f, separators=(',', ':'))
    geo = geometria_peitoral(pecas)
    with open(f'{OUT}/peitoral_asas.geo.json', 'w') as f:
        json.dump(geo, f, separators=(',', ':'))
    # textura da armadura (Bedrock) com o atlas no canto livre (56..64, 16..32)
    l1 = np.array(Image.open(f'{TRAJES}/coroa_layer1.png').convert('RGBA'))
    reg = l1[UV_BASE[1] * S:(UV_BASE[1] + AH) * S, UV_BASE[0] * S:(UV_BASE[0] + AW) * S]
    if reg[..., 3].max() > 0:
        raise SystemExit('regiao da textura nao esta livre')
    l1[UV_BASE[1] * S:(UV_BASE[1] + AH) * S, UV_BASE[0] * S:(UV_BASE[0] + AW) * S] = atlas
    Image.fromarray(l1, 'RGBA').save(f'{OUT}/corona_coroa_1.png', optimize=True)
    # conferencias
    for lado in ('direita', 'esquerda'):
        a = vertices_k(pecas, lado)
        b = vertices_java(pecas, lado)
        c = vertices_bedrock(geo, lado)
        print(lado, 'java x desenho: %.5f' % np.abs(a - b).max(), '| bedrock x desenho: %.5f' % np.abs(a - c).max(),
              '| altura max (px): %.2f' % a[:, 1].max(), '| x: %.1f..%.1f z: %.1f..%.1f' % (a[:, 0].min(), a[:, 0].max(), a[:, 2].min(), a[:, 2].max()))
    for lado in ('direita', 'esquerda'):
        for ag in (False, True):
            M = matriz_display(lado, ag)
            print(lado, 'agachado' if ag else 'em pe', np.round(M, 5).tolist())
    previas(pecas, atlas)
    print('ok', OUT, len(pecas), 'pecas')
