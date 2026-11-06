"""Props 3D das habilidades (Poderes): raio do Kamehameha, orbes de ki, bola de fogo, raio, punho de energia, onda,
shuriken, arco de lamina, redemoinho, raiz, cristais de gelo, aura, meteoro, flor carnivora.

Convencao: o prop fica centrado na origem e "aponta" para +z (sul); o plugin gira o display para a direcao que quiser.
Quadros de animacao: listas de cubos por quadro (mesmas pecas, pele com sufixo de fase quando a textura muda).
"""
import math, os, sys
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'skins', 'monstros'))
from base import fbm, ridged, rampa, mix, C, ss, elipse, dentro, dentes, vnoise, hash2  # noqa: E402
from kit import Cubo, girado  # noqa: E402

PROPS = {}


def fase_de(pele):
    """'energia2' -> ('energia', 2)."""
    i = len(pele)
    while i > 0 and pele[i - 1].isdigit():
        i -= 1
    return pele[:i], int(pele[i:] or 0)


def com_fase(cubos, n):
    """Copia da lista com a fase n no nome da pele (so para peles animadas: terminam com '#')."""
    out = []
    for c in cubos:
        if c.pele.endswith('#'):
            out.append(c.com(pele=c.pele[:-1] + str(n)))
        else:
            out.append(c)
    return out


def quadros_fase(cubos, n):
    return {f'f{k}': com_fase(cubos, k) for k in range(n)}


def _dist_eixo(P, eixo='z'):
    x, y, z = P['x'], P['y'], P['z']
    if eixo == 'z':
        return np.sqrt(x * x + y * y)
    if eixo == 'y':
        return np.sqrt(x * x + z * z)
    return np.sqrt(y * y + z * z)


# ------------------------------------------------------------------ pintores genericos

def energia(P, centro, meio, borda, fase, sem, eixo='z', raio=4.0, giro=0.35, vel=0.25):
    """Energia que corre ao longo de um eixo: miolo claro, borda colorida, listras em espiral que andam com a fase."""
    x, y, z = P['x'], P['y'], P['z']
    along = {'z': z, 'y': y, 'x': x}[eixo]
    d = _dist_eixo(P, eixo) / raio
    ang = np.arctan2(y if eixo == 'z' else z, x) if eixo != 'x' else np.arctan2(z, y)
    espiral = 0.5 + 0.5 * np.sin(along * giro - fase * vel * 2 * np.pi + ang * 2)
    n = fbm(x * 0.8, y * 0.8, along * 0.25 - fase * 0.9, 3, 1, sem)
    v = np.clip(1 - d, 0, 1) * (0.75 + 0.25 * espiral) + 0.35 * (n - 0.5)
    c = rampa(np.clip(v, 0, 1), [(0, borda), (0.45, meio), (1, centro)])
    return c


def fogo(P, fase, sem, cauda=None):
    """Fogo: amarelo no miolo, laranja, vermelho e preto nas pontas; linguas que sobem/escapam com a fase.
    cauda = eixo em que o fogo se desfaz (alpha vai sumindo)."""
    x, y, z = P['x'], P['y'], P['z']
    n = fbm(x * 0.9, y * 0.9 - fase * 1.3, z * 0.9, 3, 1, sem)
    lingua = ridged(x * 0.6, y * 0.6 - fase * 2.0, z * 0.6, 3, 1, sem + 1)
    v = n * 0.7 + lingua * 0.5
    c = rampa(np.clip(v, 0, 1), [(0, '#2a0600'), (0.3, '#9a1a00'), (0.55, '#ff6a00'), (0.8, '#ffc23a'), (1, '#fff6c8')])
    a = np.ones(x.shape)
    if cauda is not None:
        eixo, de, ate = cauda
        t = (P[eixo] - de) / (ate - de)
        a = np.where(np.clip(t, 0, 1) + 0.6 * (lingua - 0.5) > 0.35, 1.0, 0.0)
    return c, a


def metal(P, base='#9aa0a8', escuro='#3a3f48', claro='#e8ecf0', sem=0):
    x, y, z, s, t = P['x'], P['y'], P['z'], P['s'], P['t']
    n = fbm(x * 1.5, y * 1.5, z * 1.5, 3, 1, sem)
    c = rampa(n, [(0, escuro), (0.5, base), (1, claro)])
    risco = ss(0.85, 0.97, ridged(x * 3, y * 0.4, z * 3, 2, 1, sem + 1))
    c = mix(c, C(claro), risco * 0.5)
    borda_m = np.minimum(np.minimum(s, 1 - s), np.minimum(t, 1 - t))
    c = mix(c, C(claro), (borda_m < 0.08) * 0.35)
    return c


def gelo(P, fase=0, sem=0):
    x, y, z = P['x'], P['y'], P['z']
    n = fbm(x * 1.2, y * 1.2, z * 1.2, 3, 1, sem)
    c = rampa(n, [(0, '#6fb4e8'), (0.5, '#b9e4ff'), (1, '#f4fcff')])
    rach = ss(0.82, 0.95, ridged(x * 2.2, y * 2.2, z * 2.2, 2, 1, sem + 1))
    c = mix(c, C('#ffffff'), rach * 0.8)
    return c


def agua(P, fase, sem=0, espuma_y=None):
    x, y, z = P['x'], P['y'], P['z']
    n = fbm(x * 0.9, y * 0.9, z * 0.9 - fase * 0.8, 3, 1, sem)
    c = rampa(n, [(0, '#0b3f8a'), (0.5, '#1f7fd6'), (0.85, '#6ec5ff'), (1, '#cfefff')])
    if espuma_y is not None:
        esp = ss(espuma_y - 1.5, espuma_y + 1.0, y + 1.5 * (n - 0.5))
        c = mix(c, C('#f7fdff'), esp * 0.9)
    return c


def madeira(P, sem=0, base='#5a3a1e', escuro='#2e1b0c', claro='#8a5a2e'):
    x, y, z = P['x'], P['y'], P['z']
    n = fbm(x * 0.6, y * 3.0, z * 0.6, 3, 1, sem)
    c = rampa(n, [(0, escuro), (0.5, base), (1, claro)])
    return c


# ------------------------------------------------------------------ KAMEHAMEHA

def kame_raio_cubos():
    cs = [Cubo('miolo', (-1.5, -1.5, -8), (1.5, 1.5, 8), 'kcentro#', emite=15),
          Cubo('meio', (-2.5, -2.5, -8), (2.5, 2.5, 8), 'kmeio#', emite=15),
          Cubo('casca', (-3.5, -3.5, -8), (3.5, 3.5, 8), 'kcasca#', emite=15, rot=('z', 45.0, (0, 0, 0)))]
    return cs


def kame_pintor(P):
    pele, fase = fase_de(P['pele'])
    x, y, z, s, t = P['x'], P['y'], P['z'], P['s'], P['t']
    if pele == 'kcentro':
        return energia(P, '#ffffff', '#dffbff', '#9eeeff', fase, 201, raio=2.5), 1
    if pele == 'kmeio':
        c = energia(P, '#e6fbff', '#78dcff', '#2aa8f0', fase, 202, raio=3.5)
        a = np.where(0.5 + 0.5 * np.sin(z * 0.9 - fase * 1.6 + np.arctan2(y, x) * 3) + 0.4 * (fbm(x, y, z * 0.3 - fase, 2, 1, 203) - 0.5) > 0.45, 1.0, 0.0)
        return c, a
    if pele == 'kcasca':
        c = energia(P, '#bff2ff', '#49b8ff', '#1060c0', fase, 204, raio=5.0)
        a = np.where(0.5 + 0.5 * np.sin(z * 0.6 + fase * 1.6 - np.arctan2(y, x) * 2) + 0.5 * (fbm(x * 0.7, y * 0.7, z * 0.25 + fase, 2, 1, 205) - 0.5) > 0.6, 0.85, 0.0)
        return c, a
    if pele == 'kbolha':
        # ponta/bolha de energia: esfera clara com borda azul
        d = np.sqrt(x * x + y * y + z * z) / 7.0
        c = rampa(np.clip(1 - d, 0, 1) + 0.25 * (fbm(x, y, z, 3, 1, 206 + fase) - 0.5), [(0, '#1a7ad8'), (0.4, '#6cd4ff'), (0.75, '#e8fbff'), (1, '#ffffff')])
        return c, 1
    if pele == 'kcarga':
        d = np.sqrt(x * x + y * y + z * z) / 4.0
        c = rampa(np.clip(1 - d, 0, 1) + 0.3 * (fbm(x * 2, y * 2, z * 2, 3, 1, 207 + fase) - 0.5), [(0, '#2a8cf0'), (0.5, '#9ae6ff'), (1, '#ffffff')])
        return c, 1
    return C('#ffffff') * np.ones(s.shape + (3,)), 1


PROPS['kame_raio'] = dict(cubos=kame_raio_cubos, quadros=lambda cs: quadros_fase(cs, 4), pintor=kame_pintor, tex=192, esc=2)


def kame_ponta_cubos():
    cs = [Cubo('b1', (-6, -6, -6), (6, 6, 6), 'kbolha#', emite=15),
          Cubo('b2', (-6, -6, -6), (6, 6, 6), 'kbolha#', emite=15, rot=('y', 45.0, (0, 0, 0))),
          Cubo('b3', (-6, -6, -6), (6, 6, 6), 'kbolha#', emite=15, rot=('x', 45.0, (0, 0, 0)))]
    return cs


PROPS['kame_ponta'] = dict(cubos=kame_ponta_cubos, quadros=lambda cs: quadros_fase(cs, 2), pintor=kame_pintor, tex=192, esc=2)


def kame_carga_cubos():
    cs = [Cubo('c1', (-3.5, -3.5, -3.5), (3.5, 3.5, 3.5), 'kcarga#', emite=15),
          Cubo('c2', (-3.5, -3.5, -3.5), (3.5, 3.5, 3.5), 'kcarga#', emite=15, rot=('y', 45.0, (0, 0, 0))),
          Cubo('c3', (-3.5, -3.5, -3.5), (3.5, 3.5, 3.5), 'kcarga#', emite=15, rot=('z', 45.0, (0, 0, 0)))]
    return cs


PROPS['kame_carga'] = dict(cubos=kame_carga_cubos, quadros=lambda cs: quadros_fase(cs, 3), pintor=kame_pintor, tex=128)


# ------------------------------------------------------------------ KI (orbe amarelo)

def ki_orbe_cubos():
    return [Cubo('o1', (-3, -3, -3), (3, 3, 3), 'ki#', emite=15),
            Cubo('o2', (-3, -3, -3), (3, 3, 3), 'ki#', emite=15, rot=('y', 45.0, (0, 0, 0))),
            Cubo('o3', (-3, -3, -3), (3, 3, 3), 'ki#', emite=15, rot=('x', 45.0, (0, 0, 0)))]


def ki_pintor(P):
    pele, fase = fase_de(P['pele'])
    x, y, z = P['x'], P['y'], P['z']
    d = np.sqrt(x * x + y * y + z * z) / 4.0
    v = np.clip(1 - d, 0, 1) + 0.35 * (fbm(x * 2, y * 2, z * 2 - fase, 3, 1, 211) - 0.5)
    c = rampa(np.clip(v, 0, 1), [(0, '#e07a00'), (0.4, '#ffc400'), (0.75, '#fff3a0'), (1, '#ffffff')])
    return c, 1


PROPS['ki_orbe'] = dict(cubos=ki_orbe_cubos, quadros=lambda cs: quadros_fase(cs, 2), pintor=ki_pintor, tex=96)


# ------------------------------------------------------------------ BOLA DE FOGO e METEORO

def fogo_bola_cubos():
    cs = [Cubo('nucleo', (-3, -3, -3), (3, 3, 3), 'fnucleo#', emite=15),
          Cubo('chama1', (-4.5, -4.5, -4.5), (4.5, 4.5, 4.5), 'fchama#', emite=15, rot=('y', 45.0, (0, 0, 0))),
          Cubo('chama2', (-4.5, -4.5, -4.5), (4.5, 4.5, 4.5), 'fchama#', emite=15, rot=('x', 45.0, (0, 0, 0))),
          Cubo('cauda', (-3, -3, -16), (3, 3, -3), 'fcauda#', emite=15, rot=('z', 45.0, (0, 0, -9)))]
    return cs


def fogo_pintor(P):
    pele, fase = fase_de(P['pele'])
    x, y, z = P['x'], P['y'], P['z']
    if pele == 'fnucleo':
        d = np.sqrt(x * x + y * y + z * z) / 4.5
        c = rampa(np.clip(1 - d, 0, 1) + 0.3 * (fbm(x * 2, y * 2, z * 2 - fase, 2, 1, 221) - 0.5), [(0, '#ff7a00'), (0.5, '#ffd040'), (1, '#fffbe0')])
        return c, 1
    if pele == 'fchama':
        c, _ = fogo(P, fase, 222)
        a = np.where(fbm(x * 1.4, y * 1.4, z * 1.4 - fase * 1.5, 3, 1, 223) > 0.46, 1.0, 0.0)
        return c, a
    if pele == 'fcauda':
        c, a = fogo(P, fase, 224, cauda=('z', -16, -3))
        return c, a
    if pele == 'rocha':
        n = fbm(x * 1.3, y * 1.3, z * 1.3, 3, 1, 225)
        c = rampa(n, [(0, '#1b1a1c'), (0.5, '#3a383c'), (1, '#5a575c')])
        lava = ss(0.8, 0.94, ridged(x * 1.2, y * 1.2, z * 1.2, 3, 1, 226))
        c = mix(c, rampa(fbm(x * 3, y * 3, z * 3 - fase, 2, 1, 227), [(0, '#ff4a00'), (1, '#ffd05a')]), lava * 0.95)
        return c, 1
    return C('#ff8800') * np.ones(x.shape + (3,)), 1


PROPS['fogo_bola'] = dict(cubos=fogo_bola_cubos, quadros=lambda cs: quadros_fase(cs, 3), pintor=fogo_pintor, tex=160, esc=2)


def meteoro_cubos():
    cs = [Cubo('rocha1', (-5, -5, -5), (5, 5, 5), 'rocha#'),
          Cubo('rocha2', (-5, -5, -5), (5, 5, 5), 'rocha#', rot=('y', 45.0, (0, 0, 0))),
          Cubo('fogo1', (-6.5, -6.5, -6.5), (6.5, 6.5, 6.5), 'fchama#', emite=15, rot=('x', 45.0, (0, 0, 0))),
          Cubo('cauda', (-5, -5, -16), (5, 5, -5), 'fcauda#', emite=15, rot=('z', 45.0, (0, 0, -10)))]
    return cs


PROPS['meteoro'] = dict(cubos=meteoro_cubos, quadros=lambda cs: quadros_fase(cs, 2), pintor=fogo_pintor, tex=192, esc=2)


# ------------------------------------------------------------------ RAIO (relampago vertical, 3 blocos)

def raio_cubos():
    # ziguezague de cima (y=32) para baixo (y=-16): segmentos finos girados em z, miolo branco + halo azul
    cs = []
    pontos = [(0, 31), (2.5, 22), (-2, 13), (1.5, 4), (-1.5, -6), (0.5, -15)]
    for k in range(len(pontos) - 1):
        (x0, y0), (x1, y1) = pontos[k], pontos[k + 1]
        h = y0 - y1
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
        ang = math.degrees(math.atan2(x1 - x0, -(h)))
        ang = max(-45, min(45, round(ang / 22.5) * 22.5))
        cs.append(Cubo(f'm{k}', (cx - 0.5, cy - h / 2 - 0.8, -0.5), (cx + 0.5, cy + h / 2 + 0.8, 0.5), 'rmiolo#', emite=15, rot=('z', ang, (cx, cy, 0))))
        cs.append(Cubo(f'h{k}', (cx - 1.4, cy - h / 2 - 0.8, -1.4), (cx + 1.4, cy + h / 2 + 0.8, 1.4), 'rhalo#', emite=15, rot=('z', ang, (cx, cy, 0))))
    return cs


def raio_pintor(P):
    pele, fase = fase_de(P['pele'])
    x, y, z, s, t = P['x'], P['y'], P['z'], P['s'], P['t']
    if pele == 'rmiolo':
        return rampa(fbm(x * 2, y * 2 + fase * 7, z * 2, 2, 1, 231), [(0, '#dff4ff'), (1, '#ffffff')]), 1
    n = fbm(x * 1.5, y * 1.2 + fase * 5, z * 1.5, 3, 1, 232)
    c = rampa(n, [(0, '#2a6cff'), (0.5, '#7fc4ff'), (1, '#e0f4ff')])
    a = np.where(n + 0.3 * (0.5 - np.abs(s - 0.5)) > 0.55, 0.9, 0.0)
    return c, a


PROPS['raio'] = dict(cubos=raio_cubos, quadros=lambda cs: quadros_fase(cs, 2), pintor=raio_pintor, tex=192)


# ------------------------------------------------------------------ PUNHO de energia (Lutador)

def punho_cubos():
    # punho fechado apontando para +z: palma, 4 dedos dobrados na frente, polegar do lado
    cs = [Cubo('palma', (-5, -4, -6), (5, 4, 2), 'penergia#', emite=15),
          Cubo('pulso', (-3.5, -3, -12), (3.5, 3, -6), 'penergia#', emite=15)]
    for k in range(4):
        x0 = -5 + k * 2.5
        cs.append(Cubo(f'dedo{k}', (x0 + 0.1, -0.5, 2), (x0 + 2.4, 4, 6.5), 'pdedo#', emite=15))
        cs.append(Cubo(f'falange{k}', (x0 + 0.1, -4, 3.5), (x0 + 2.4, -0.5, 6.5), 'pdedo#', emite=15))
    cs.append(Cubo('polegar', (-7.2, -2, -3), (-5, 1.5, 3), 'pdedo#', emite=15))
    cs.append(Cubo('brilho', (-6.5, -5.5, -8), (6.5, 5.5, 7.5), 'pbrilho#', emite=15, rot=('z', 22.5, (0, 0, 0))))
    return cs


def punho_pintor(P):
    pele, fase = fase_de(P['pele'])
    x, y, z, s, t = P['x'], P['y'], P['z'], P['s'], P['t']
    n = fbm(x * 1.2, y * 1.2, z * 1.2 - fase * 1.2, 3, 1, 241)
    if pele == 'pbrilho':
        a = np.where(fbm(x * 1.5, y * 1.5, z * 1.5 - fase * 2, 3, 1, 242) > 0.56, 0.8, 0.0)
        return rampa(n, [(0, '#ff6a00'), (1, '#ffe08a')]), a
    base = rampa(n, [(0, '#ff5a00'), (0.45, '#ff9a1a'), (0.8, '#ffd45a'), (1, '#fff5c0')])
    if pele == 'pdedo':
        base = mix(base, C('#ffffff'), ss(0.0, 0.25, 1 - t) * 0.4 * (P['face'] == 'south'))
    linhas = ss(0.85, 0.95, ridged(x * 1.5, y * 1.5, z * 1.5 + fase, 2, 1, 243))
    return mix(base, C('#ffffff'), linhas * 0.7), 1


PROPS['punho'] = dict(cubos=punho_cubos, quadros=lambda cs: quadros_fase(cs, 2), pintor=punho_pintor, tex=192, esc=2)


# ------------------------------------------------------------------ ONDA (Tridente)

def onda_cubos():
    # crista de onda de 16 de largura (x), enrolando para +z; base na agua (y=-8), topo ~ y=10
    cs = [Cubo('base', (-8, -8, -4), (8, -1, 4), 'agua#'),
          Cubo('meio', (-8, -1, -3), (8, 6, 3), 'agua#', rot=('x', -22.5, (0, -1, 0))),
          Cubo('crista', (-8, 5, -2), (8, 11, 3), 'espuma#', rot=('x', -45.0, (0, 5, 0))),
          Cubo('respingo', (-9, 9, -1), (9, 13, 5), 'respingo#', rot=('x', -45.0, (0, 9, 0)))]
    return cs


def onda_pintor(P):
    pele, fase = fase_de(P['pele'])
    x, y, z, s, t = P['x'], P['y'], P['z'], P['s'], P['t']
    if pele == 'agua':
        return agua(P, fase, 251, espuma_y=5.5), 1
    if pele == 'espuma':
        c = agua(P, fase, 252, espuma_y=6.0)
        c = mix(c, C('#ffffff'), ss(0.5, 0.75, fbm(x * 1.2, y * 1.2, z * 1.2 - fase, 3, 1, 253)) * 0.9)
        return c, 1
    n = fbm(x * 1.8, y * 1.8, z * 1.8 - fase * 1.5, 3, 1, 254)
    return rampa(n, [(0, '#bfe9ff'), (1, '#ffffff')]), np.where(n > 0.52, 0.9, 0.0)


PROPS['onda'] = dict(cubos=onda_cubos, quadros=lambda cs: quadros_fase(cs, 3), pintor=onda_pintor, tex=192, esc=2)


# ------------------------------------------------------------------ SHURIKEN (Ninja)

def shuriken_cubos():
    cs = [Cubo('centro', (-2, -2, -0.5), (2, 2, 0.5), 'aco', rot=('z', 45.0, (0, 0, 0)))]
    for k, (dx, dy) in enumerate(((1, 0), (-1, 0), (0, 1), (0, -1))):
        if dx:
            cs.append(Cubo(f'l{k}', (min(0, dx * 7), -1.2, -0.4), (max(0, dx * 7), 1.2, 0.4), 'lamina'))
        else:
            cs.append(Cubo(f'l{k}', (-1.2, min(0, dy * 7), -0.4), (1.2, max(0, dy * 7), 0.4), 'lamina'))
    cs.append(Cubo('furo', (-1, -1, -0.6), (1, 1, 0.6), 'furo'))
    return cs


def shuriken_pintor(P):
    pele = P['pele']
    x, y, z, s, t = P['x'], P['y'], P['z'], P['s'], P['t']
    if pele == 'furo':
        return C('#101014') * np.ones(s.shape + (3,)), 1
    c = metal(P, '#8d949e', '#2e3238', '#eef2f6', 261)
    if pele == 'lamina':
        r = np.maximum(np.abs(x), np.abs(y)) / 7.0
        c = mix(c, C('#f6f8fa'), ss(0.6, 1.0, r) * 0.6)   # fio afiado na ponta
        c = mix(c, C('#4a0a0a'), ss(0.8, 0.95, fbm(x * 2, y * 2, z, 2, 1, 262)) * 0.5 * ss(0.5, 1.0, r))
    return c, 1


PROPS['shuriken'] = dict(cubos=shuriken_cubos, quadros=lambda cs: {'f0': cs}, pintor=shuriken_pintor, tex=96)


# ------------------------------------------------------------------ ARCO DE LAMINA (corte)

def lamina_arco_cubos():
    cs = []
    for k, ang in enumerate((-45, -22.5, 0, 22.5, 45)):
        cs.append(Cubo(f'p{k}', (-3.2, -0.6 - 2.5 * (1 - abs(k - 2) / 2), 9), (3.2, 0.6 + 2.5 * (1 - abs(k - 2) / 2), 11), 'corte#', emite=15, rot=('y', ang, (0, 0, 0))))
    return cs


def lamina_pintor(P):
    pele, fase = fase_de(P['pele'])
    x, y, z, s, t = P['x'], P['y'], P['z'], P['s'], P['t']
    n = fbm(x * 1.5, y * 3, z * 1.5 - fase * 2, 2, 1, 271)
    c = rampa(n, [(0, '#9ad8ff'), (0.6, '#e6f8ff'), (1, '#ffffff')])
    a = np.where(n + 0.5 * (0.5 - np.abs(t - 0.5)) > 0.55, 0.9, 0.0)
    return c, a


PROPS['lamina_arco'] = dict(cubos=lamina_arco_cubos, quadros=lambda cs: quadros_fase(cs, 2), pintor=lamina_pintor, tex=128)


# ------------------------------------------------------------------ REDEMOINHO (Tridente)

def redemoinho_cubos():
    cs = []
    for k in range(4):
        r = 3 + k * 2.6
        y0 = -16 + k * 9
        for j, ang in enumerate((0, 45)):
            cs.append(Cubo(f'anel{k}{j}', (-r, y0, -r), (r, y0 + 7, r), 'vortice#', rot=('y', ang, (0, 0, 0))))
    return cs


def redemoinho_pintor(P):
    pele, fase = fase_de(P['pele'])
    x, y, z, s, t = P['x'], P['y'], P['z'], P['s'], P['t']
    ang = np.arctan2(z, x)
    faixa = 0.5 + 0.5 * np.sin(ang * 3 + y * 0.5 - fase * 2.1)
    n = fbm(x * 0.8, y * 0.5, z * 0.8 - fase, 3, 1, 281)
    c = rampa(np.clip(0.5 * faixa + 0.5 * n, 0, 1), [(0, '#0a3f8f'), (0.5, '#2a8ad8'), (0.85, '#8fd8ff'), (1, '#ffffff')])
    a = np.where(faixa + 0.6 * (n - 0.5) > 0.5, 0.9, 0.0)
    return c, a


PROPS['redemoinho'] = dict(cubos=redemoinho_cubos, quadros=lambda cs: quadros_fase(cs, 3), pintor=redemoinho_pintor, tex=192, esc=1.5)


# ------------------------------------------------------------------ RAIZ (Druida)

def raiz_cubos():
    cs = []
    y = -16
    for k, (ang, h) in enumerate(((0, 8), (22.5, 7), (45, 6), (45, 5))):
        w = 2.2 - k * 0.4
        cs.append(Cubo(f'seg{k}', (-w, y, -w), (w, y + h, w), 'raiz#', rot=('x', ang, (0, y, 0))))
        y += h - 1
    cs.append(Cubo('ponta', (-0.8, y - 1, -0.8), (0.8, y + 4, 0.8), 'raiz#', rot=('x', 45.0, (0, y - 1, 0))))
    return cs


def raiz_pintor(P):
    pele, fase = fase_de(P['pele'])
    x, y, z = P['x'], P['y'], P['z']
    c = madeira(P, 291)
    musgo = ss(0.6, 0.85, fbm(x * 1.5, y * 1.5, z * 1.5, 3, 1, 292))
    c = mix(c, C('#3f7a2a'), musgo * 0.6)
    return c, 1


PROPS['raiz'] = dict(cubos=raiz_cubos, quadros=lambda cs: {'f0': cs, 'f1': [girado(c, 'z', 22.5, (0, -16, 0)) for c in cs]}, pintor=raiz_pintor, tex=96)


# ------------------------------------------------------------------ CRISTAL DE GELO (Mago)

def cristal_gelo_cubos():
    return [Cubo('c0', (-2, -16, -2), (2, 2, 2), 'gelo'),
            Cubo('c1', (-1.5, -16, -1.5), (1.5, -2, 1.5), 'gelo', rot=('z', 22.5, (0, -16, 0))),
            Cubo('c2', (-1.5, -16, -1.5), (1.5, -4, 1.5), 'gelo', rot=('x', -22.5, (0, -16, 0))),
            Cubo('c3', (-1.2, -16, -1.2), (1.2, -6, 1.2), 'gelo', rot=('z', -45.0, (0, -16, 0))),
            Cubo('ponta', (-1, 0, -1), (1, 6, 1), 'gelo', rot=('y', 45.0, (0, 0, 0)))]


def cristal_pintor(P):
    return gelo(P, 0, 301), 1


PROPS['cristal_gelo'] = dict(cubos=cristal_gelo_cubos, quadros=lambda cs: {'f0': cs}, pintor=cristal_pintor, tex=96)


# ------------------------------------------------------------------ AURA (Super Saiyajin): casca de fogo em volta do jogador

def aura_cubos():
    return [Cubo('casca', (-7, -16, -7), (7, 20, 7), 'aura#', emite=15),
            Cubo('casca2', (-7, -16, -7), (7, 20, 7), 'aura#', emite=15, rot=('y', 45.0, (0, 0, 0)))]


def aura_pintor(P):
    pele, fase = fase_de(P['pele'])
    x, y, z, s, t = P['x'], P['y'], P['z'], P['s'], P['t']
    n = fbm(x * 0.7, y * 0.35 - fase * 1.4, z * 0.7, 3, 1, 311)
    lingua = ridged(x * 0.5, y * 0.25 - fase * 2.2, z * 0.5, 3, 1, 312)
    v = 0.6 * n + 0.6 * lingua
    c = rampa(np.clip(v, 0, 1), [(0, '#ff8a00'), (0.4, '#ffc62a'), (0.8, '#fff2a0'), (1, '#ffffff')])
    alt = (y + 16) / 36
    a = np.where(v - 0.35 * alt > 0.45, 1.0, 0.0) * np.where(P['face'] in ('up', 'down'), 0, 1)
    return c, a


PROPS['aura_ssj'] = dict(cubos=aura_cubos, quadros=lambda cs: quadros_fase(cs, 3), pintor=aura_pintor, tex=192, esc=1.5)


# ------------------------------------------------------------------ FLOR CARNIVORA (Cipo)

def flor_cubos(aberta):
    cs = [Cubo('caule', (-1.5, -16, -1.5), (1.5, -2, 1.5), 'caule'),
          Cubo('folha1', (-9, -15, -2), (-1, -13, 6), 'folha', rot=('y', 22.5, (0, -14, 0))),
          Cubo('folha2', (1, -15, -6), (9, -13, 2), 'folha', rot=('y', 22.5, (0, -14, 0))),
          Cubo('boca_baixo', (-5, -4, -5), (5, -1, 5), 'boca_fora'),
          Cubo('boca_cima', (-5, -1, -5), (5, 3, 5), 'boca_fora', rot=('x', -45.0 if aberta else 0.0, (0, -1, -5))),
          Cubo('dentes_b', (-4.5, -1, -4.5), (4.5, 0.5, 4.5), 'dentes', so=('up',)),
          Cubo('dentes_c', (-4.5, -1.5, -4.5), (4.5, -1, 4.5), 'dentes', so=('down',), rot=('x', -45.0 if aberta else 0.0, (0, -1, -5)))]
    return cs


def flor_pintor(P):
    pele = P['pele']
    x, y, z, s, t = P['x'], P['y'], P['z'], P['s'], P['t']
    n = fbm(x * 1.2, y * 1.2, z * 1.2, 3, 1, 321)
    if pele == 'caule':
        return rampa(n, [(0, '#2d5a1c'), (0.5, '#4f8c2e'), (1, '#7ab54a')]), 1
    if pele == 'folha':
        c = rampa(n, [(0, '#1f4a14'), (0.5, '#3f8a28'), (1, '#8ad060')])
        return mix(c, C('#d8f080'), ss(0.85, 0.95, ridged(x * 2, y, z * 2, 2, 1, 322)) * 0.6), 1
    if pele == 'boca_fora':
        c = rampa(n, [(0, '#7a1040'), (0.5, '#c02a6a'), (1, '#ff6aa8')])
        c = mix(c, C('#ffe0f0'), ss(0.8, 0.95, fbm(x * 3, y * 3, z * 3, 2, 1, 323)) * 0.6)
        return c, 1
    if pele == 'dentes':
        m, p, _ = dentes(s, np.abs(t - 0.5), 0.3, 0.09, afiado=1.5)
        m = m & (np.minimum(s, 1 - s) < 0.15) | (m & (np.minimum(t, 1 - t) < 0.15))
        c = np.where(m[..., None], rampa(p, [(0, '#8a7a50'), (1, '#f4ecd0')]), C('#3a0612') * np.ones(s.shape + (3,)))
        return c, 1
    return C('#ffffff') * np.ones(s.shape + (3,)), 1


PROPS['flor'] = dict(cubos=lambda: flor_cubos(False), quadros=lambda cs: {'f0': cs, 'f1': flor_cubos(True)}, pintor=flor_pintor, tex=128)
