"""A Vovo em 3D (personagem ORIGINAL do minigame Casa da Vovo): modelo de cubos para o pacote (Java + Bedrock).

Velhinha corcunda de camisola lilas florida, avental sujo, xale de trico cor de vinho, coque grisalho, olhos fundos com
a pupila que brilha no escuro, chinelos e uma frigideira de ferro na mao. Quadros: idle, grito (frigideira para a
frente e a boca escancarada) e um ciclo de andar de 16 quadros (pernas, bracos, cabeca balancando e o corpo subindo e
descendo a cada passo).

Modelo Java em coordenadas de item (16 = 1 bloco), centro x=8 z=8, pes em y=-16, FRENTE = -z (norte), ~1,9 bloco.
Mostrado por ItemDisplay (leather_helmet + item_model corona:props/vovo_<quadro>) 1,5 bloco acima dos pes; no Bedrock
a extensao GeyserDisplayEntity mostra o mesmo item (corona:prop_vovo_<quadro>).

uso: python3 -I vovo3d.py <saida>
"""
import math
import os
import sys

import numpy as np
from PIL import Image

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)
SAIDA = sys.argv[1] if len(sys.argv) > 1 else '/tmp/claude-0/vovo3d'
import trajes_hd as TH  # noqa: E402
import kit  # noqa: E402


class _Todos:
    def __contains__(self, a):
        return -45.0 <= a <= 45.0


kit.ANGULOS = _Todos()      # desde a 1.21.6 o Java aceita qualquer angulo entre -45 e 45 nos elementos
N_ANDAR = 16
PUPILA = np.array([1.0, 0.93, 0.62])

TH.MAT.update({
    'camisola': ((0.52, 0.48, 0.62), 0.08, 10, 0.0), 'babado': ((0.60, 0.57, 0.68), 0.08, 10, 0.0),
    'avental': ((0.80, 0.78, 0.70), 0.06, 8, 0.0), 'xale': ((0.24, 0.09, 0.19), 0.06, 8, 0.0),
    'pele_v': ((0.74, 0.70, 0.62), 0.18, 16, 0.0), 'cabelo_v': ((0.80, 0.80, 0.83), 0.25, 20, 0.0),
    'meia': ((0.60, 0.53, 0.44), 0.06, 8, 0.0), 'chinelo': ((0.78, 0.42, 0.52), 0.05, 8, 0.0),
    'ferro': ((0.09, 0.09, 0.10), 0.9, 34, 0.7), 'madeira_v': ((0.36, 0.23, 0.12), 0.12, 12, 0.0),
    'boca_v': ((0.10, 0.01, 0.02), 0.1, 8, 0.0), 'escuro_v': ((0.02, 0.02, 0.02), 0.2, 20, 0.0),
    'sangue_v': ((0.30, 0.04, 0.03), 0.3, 30, 0.0), 'dente_v': ((0.85, 0.80, 0.62), 0.4, 30, 0.0),
})

# nome, (x0, x1, y0, y1, z0, z1), material, grupo, luz propria, faces
PECAS = [
    ('perna_d', (5.9, 7.1, -15.0, -5.0, 7.3, 8.7), 'meia', 'perna_d', 0, None),
    ('chinelo_d', (5.5, 7.5, -16.0, -14.7, 5.0, 8.9), 'chinelo', 'perna_d', 0, None),
    ('perna_e', (8.9, 10.1, -15.0, -5.0, 7.3, 8.7), 'meia', 'perna_e', 0, None),
    ('chinelo_e', (8.5, 10.5, -16.0, -14.7, 5.0, 8.9), 'chinelo', 'perna_e', 0, None),
    ('saia', (4.5, 11.5, -13.4, -3.0, 5.6, 10.4), 'saia', 'corpo', 0, ('north', 'south', 'east', 'west')),
    ('babado', (4.2, 11.8, -14.4, -13.2, 5.3, 10.7), 'babado', 'corpo', 0, ('north', 'south', 'east', 'west', 'up')),
    ('tronco', (4.9, 11.1, -3.0, 6.8, 6.1, 9.9), 'camisola', 'corpo', 0, None),
    ('avental', (5.5, 10.5, -11.6, 4.2, 5.22, 5.62), 'avental', 'corpo', 0, ('north', 'east', 'west', 'up', 'down')),
    ('laco', (6.9, 9.1, -3.7, -1.9, 10.35, 10.95), 'avental', 'corpo', 0, None),
    ('xale', (4.3, 11.7, 3.6, 8.4, 5.7, 10.6), 'xale', 'corpo', 0, None),
    ('xale_ponta', (4.3, 6.5, 0.6, 3.7, 5.45, 6.1), 'franja', 'corpo', 0, None),
    ('xale_ponta', (9.5, 11.7, 0.6, 3.7, 5.45, 6.1), 'franja', 'corpo', 0, None),
    ('corcunda', (5.3, 10.7, 6.2, 9.6, 9.2, 11.5), 'xale', 'corpo', 0, None),
    ('pescoco', (7.1, 8.9, 7.6, 9.2, 6.2, 8.0), 'pele', 'corpo', 0, None),
    ('manga_d', (2.9, 4.9, 0.2, 7.0, 6.6, 8.6), 'camisola', 'braco_d', 0, None),
    ('mao_d', (3.1, 4.7, -2.2, 0.3, 6.8, 8.4), 'pele', 'braco_d', 0, None),
    ('cabo', (3.55, 4.25, -2.0, -1.3, 2.4, 7.0), 'madeira', 'braco_d', 0, None),
    ('panela', (1.7, 6.1, -2.4, -1.5, -2.0, 2.5), 'ferro', 'braco_d', 0, None),
    ('panela_borda', (1.7, 6.1, -1.5, -1.0, -2.0, -1.6), 'ferro', 'braco_d', 0, None),
    ('manga_e', (11.1, 13.1, 0.2, 7.0, 6.6, 8.6), 'camisola', 'braco_e', 0, None),
    ('mao_e', (11.3, 12.9, -2.2, 0.3, 6.8, 8.4), 'pele', 'braco_e', 0, None),
    ('cabeca', (5.1, 10.9, 8.6, 14.0, 3.2, 8.8), 'rosto', 'cabeca', 0, None),
    ('boca', (6.2, 9.8, 8.2, 9.3, 3.42, 6.0), 'boca', 'cabeca', 0, ('north', 'down')),
    ('queixo', (5.9, 10.1, 8.3, 9.5, 3.28, 6.3), 'queixo', 'queixo', 0, None),
    ('cabelo_topo', (4.8, 11.2, 13.2, 14.6, 3.3, 9.2), 'cabelo', 'cabeca', 0, None),
    ('cabelo_tras', (4.8, 11.2, 9.4, 13.4, 8.6, 9.5), 'cabelo', 'cabeca', 0, None),
    ('cabelo_lado', (4.65, 5.15, 10.2, 13.6, 4.6, 9.0), 'cabelo', 'cabeca', 0, None),
    ('cabelo_lado', (10.85, 11.35, 10.2, 13.6, 4.6, 9.0), 'cabelo', 'cabeca', 0, None),
    ('coque', (6.8, 9.2, 12.2, 14.7, 9.3, 11.3), 'cabelo', 'cabeca', 0, None),
    ('nariz', (7.5, 8.5, 10.5, 11.8, 2.6, 3.3), 'pele', 'cabeca', 0, None),
    ('pupila', (6.15, 6.75, 11.95, 12.55, 3.12, 3.22), 'pupila', 'cabeca', 12, ('north',)),
    ('pupila', (9.25, 9.85, 11.95, 12.55, 3.12, 3.22), 'pupila', 'cabeca', 12, ('north',)),
]
OLHOS_UV = [(10.9 - 6.45, 14.0 - 12.25), (10.9 - 9.55, 14.0 - 12.25)]     # (u, v) na face da frente da cabeca


def pose(quadro):
    """grupo -> (eixo, angulo, pivo); 'dy' = sobe/desce do corpo todo (menos as pernas)."""
    if quadro == 'idle':
        return {'braco_d': ('x', 8.0, (3.9, 6.6, 7.6)), 'cabeca': ('z', 5.0, (8.0, 8.6, 6.5)), 'queixo': ('z', 5.0, (8.0, 8.6, 6.5))}, 0.0
    if quadro == 'grito':
        return {'braco_d': ('x', 45.0, (3.9, 6.6, 7.6)), 'braco_e': ('x', 28.0, (12.1, 6.6, 7.6)),
                'queixo': ('x', -30.0, (8.0, 9.5, 6.3))}, 0.0
    k = int(quadro.split('_')[1])
    f = 2 * math.pi * k / N_ANDAR
    s = math.sin(f)
    cab = ('z', round(4.5 * s, 2), (8.0, 8.6, 6.5))
    return {'perna_d': ('x', round(14.0 * s, 2), (6.5, -5.0, 8.0)), 'perna_e': ('x', round(-14.0 * s, 2), (9.5, -5.0, 8.0)),
            'braco_e': ('x', round(16.0 * s, 2), (12.1, 6.6, 7.6)), 'braco_d': ('x', round(9.0 - 6.0 * s, 2), (3.9, 6.6, 7.6)),
            'cabeca': cab, 'queixo': cab}, round(0.28 * math.cos(2 * f), 3)


QUADROS = ['idle', 'grito'] + [f'andar_{k:02d}' for k in range(N_ANDAR)]


def cubos(quadro='idle'):
    rots, dy = pose(quadro)
    cs = []
    for nome, (x0, x1, y0, y1, z0, z1), mat, grupo, emite, so in PECAS:
        sobe = 0.0 if grupo.startswith('perna') else dy
        rot = rots.get(grupo)
        if rot and rot[1] != 0:
            e, ang, (px, py, pz) = rot
            rot = (e, float(ang), (px, py + sobe, pz))
        else:
            rot = None
        c = kit.Cubo(nome, (x0, y0 + sobe, z0), (x1, y1 + sobe, z1), mat, rot=rot, emite=emite)
        c.so = so
        c.grupo = grupo
        cs.append(c)
    return cs


# ------------------------------------------------------------------ pintura
def _linha(F, pts):
    d = np.full(F.x.shape, 9.0)
    for (ax, ay), (bx, by) in zip(pts[:-1], pts[1:]):
        vx, vy = bx - ax, by - ay
        t = np.clip(((F.x - ax) * vx + (F.y - ay) * vy) / (vx * vx + vy * vy + 1e-9), 0, 1)
        d = np.minimum(d, np.hypot(F.x - (ax + t * vx), F.y - (ay + t * vy)))
    return d


def pano(F, c, f, w, h, sem, mat):
    tudo = F.ret(0, 0, w, h)
    F.pintar(tudo, mat, alt=0.12, bisel=0.12, ruido=0.14, escala_ruido=1.6)
    dob = TH.fbm(F.x * 0.5 + sem * 0.3, F.y * 0.1, 1.3, oct=3, seed=sem) - 0.5
    F.alt += 0.6 * dob
    F.alb *= (0.82 + 0.4 * (dob[..., None] + 0.5))
    yw = np.full(F.x.shape, float(c.de[1])) if f in ('up', 'down') else c.ate[1] - F.y
    suj = np.clip((-8.0 - yw) / 6.0, 0, 1) * 0.5
    F.alb *= (1 - suj)[..., None]


def flores(F, w, h, sem):
    """Estampa de florzinhas miudas (rosa desbotado com miolo amarelo)."""
    passo = 1.15
    gx, gy = np.floor(F.x / passo), np.floor(F.y / passo)
    ox = ((gy % 2) * 0.5 + 0.5) * passo
    cx, cy = gx * passo + ox, (gy + 0.5) * passo
    d = np.hypot(F.x - cx, F.y - cy)
    ang = np.arctan2(F.y - cy, F.x - cx)
    petala = d < 0.20 + 0.07 * np.cos(ang * 5)
    miolo = d < 0.07
    F.alb[petala] = F.alb[petala] * 0.35 + np.array([0.80, 0.52, 0.60]) * 0.65
    F.alb[miolo] = np.array([0.85, 0.72, 0.30])
    F.alt += 0.04 * petala


def pele(F, c, f, w, h, sem, rugas=True):
    tudo = F.ret(0, 0, w, h)
    F.pintar(tudo, 'pele_v', alt=0.22, bisel=0.2, ruido=0.07, escala_ruido=2.0)
    manch = np.clip((TH.fbm(F.x * 2.2, F.y * 2.2, 0.7, oct=3, seed=sem + 2) - 0.62) * 5, 0, 1)
    F.alb = F.alb * (1 - manch[..., None] * 0.45) + np.array([0.42, 0.32, 0.24])[None, None, :] * manch[..., None] * 0.45
    veia = np.abs(TH.fbm(F.x * 1.4, F.y * 1.4, 4.2, oct=3, seed=sem + 5) - 0.5)
    k = np.clip(1 - veia / 0.018, 0, 1) * 0.35
    F.alb = F.alb * (1 - k[..., None]) + np.array([0.40, 0.42, 0.55])[None, None, :] * k[..., None]
    if rugas and min(w, h) >= 1.2:
        r = np.abs(np.sin(F.y * 7.0 + np.sin(F.x * 2.3 + sem) * 1.2))
        k = np.clip(1 - r / 0.10, 0, 1)
        F.alt -= 0.10 * k
        F.alb *= (1 - 0.16 * k)[..., None]


def pintar_face(c, f, wpx, hpx, sem, esc):
    if wpx < 4 or hpx < 4:
        img, b = pintar_face(c, f, max(wpx, 4), max(hpx, 4), sem, esc)
        img = np.array(Image.fromarray(img, 'RGBA').resize((wpx, hpx), Image.BOX))
        b = np.array(Image.fromarray((b * 255).astype(np.uint8), 'L').resize((wpx, hpx), Image.BOX)) / 255.0
        return img, b
    w, h = wpx / esc, hpx / esc
    F = TH.Face(w, h, sem)
    tudo = F.ret(0, 0, w, h)
    rng = np.random.default_rng(sem)
    mat = c.pele
    lado = f in ('north', 'south', 'east', 'west')
    if mat in ('camisola', 'saia'):
        pano(F, c, f, w, h, sem, 'camisola')
        if lado:
            flores(F, w, h, sem)
        if mat == 'saia' and lado:
            # manchas velhas subindo da barra
            m = np.clip((TH.fbm(F.x * 0.9, F.y * 0.9, 2.2, oct=3, seed=sem + 8) - 0.5) * 4, 0, 1) * np.clip((F.y - h * 0.45) / (h * 0.5), 0, 1)
            F.alb = F.alb * (1 - m[..., None] * 0.55) + np.array([0.22, 0.17, 0.12])[None, None, :] * m[..., None] * 0.55
    elif mat == 'babado':
        pano(F, c, f, w, h, sem, 'babado')
        if lado:
            onda = 0.25 + 0.3 * np.abs(np.sin(F.x * math.pi / 0.9))
            F.a[F.y > h - onda + 0.2] = 0
            F.alt += 0.2 * np.sin(F.x * math.pi / 0.45)
    elif mat == 'avental':
        pano(F, c, f, w, h, sem, 'avental')
        if f == 'north':
            F.cavar(F.ret(0.25, 0.25, w - 0.25, h - 0.25, 0.2), 0.08, 0.05)                # costura da borda
            F.cavar(F.ret(w * 0.5 - 1.2, h * 0.46, w * 0.5 + 1.2, h * 0.66, 0.15), 0.10, 0.05)      # bolso
            # sangue seco: respingos e escorridos
            for _ in range(7):
                cx, cy, r = rng.uniform(0.5, w - 0.5), rng.uniform(h * 0.15, h * 0.9), rng.uniform(0.25, 0.75)
                borda = TH.fbm(F.x * 3, F.y * 3, 0.4, oct=2, seed=sem + 11) * 0.5
                d = np.hypot(F.x - cx, (F.y - cy) * 0.9) - r * (0.7 + borda)
                F.pintar(d, 'sangue_v', alt=0.03, bisel=0.05, ruido=0.25, escala_ruido=3)
                comp = rng.uniform(0.6, 2.6)
                esc_d = np.abs(F.x - cx - 0.05 * np.sin(F.y * 4)) - 0.07
                esc_d = np.where((F.y > cy) & (F.y < cy + r + comp), esc_d, 1.0)
                F.pintar(esc_d, 'sangue_v', alt=0.02, bisel=0.02)
            # marca de mao
            mx, my = w * 0.3, h * 0.36
            F.pintar(F.ret(mx - 0.4, my - 0.3, mx + 0.4, my + 0.5, 0.2), 'sangue_v', alt=0.02)
            for k in range(4):
                fx = mx - 0.36 + k * 0.24
                F.pintar(F.ret(fx - 0.08, my - 0.95 + abs(k - 1.5) * 0.12, fx + 0.08, my - 0.25, 0.06), 'sangue_v', alt=0.02)
    elif mat in ('xale', 'franja'):
        F.pintar(tudo, 'xale', alt=0.2, bisel=0.2, ruido=0.12, escala_ruido=2.0)
        u, v = (F.x + F.y) / 0.42, (F.x - F.y) / 0.42
        trico = np.sin(u * math.pi) * np.sin(v * math.pi)
        F.alt += 0.22 * trico
        F.alb *= (0.8 + 0.35 * (trico[..., None] > 0))
        if mat == 'franja' and lado:
            fio = (np.floor(F.x / 0.22) % 2) == 0
            F.a[(F.y > h - 0.9) & ~fio] = 0
    elif mat in ('pele', 'queixo'):
        pele(F, c, f, w, h, sem)
        if mat == 'queixo' and f == 'north':
            # labio fino e torto com dois dentes tortos
            lin = _linha(F, [(0.3, 0.42), (1.2, 0.30), (2.1, 0.36), (3.0, 0.28), (3.9, 0.40)])
            F.pintar(lin - 0.07, 'boca_v', alt=-0.1)
            for dx in (1.25, 2.75):
                F.pintar(F.ret(dx - 0.16, 0.0, dx + 0.16, 0.34, 0.04), 'dente_v', alt=0.15, bisel=0.08)
    elif mat == 'rosto':
        pele(F, c, f, w, h, sem, rugas=f != 'north')
        if f == 'north':
            # rugas da testa, pes de galinha, bolsas debaixo dos olhos e o bigode chines
            for vy, amp in ((0.45, 0.06), (0.75, 0.05), (1.02, 0.04)):
                d = np.abs(F.y - vy - amp * np.sin(F.x * 2.4)) - 0.035
                d = np.where((F.x > 0.6) & (F.x < w - 0.6), d, 1.0)
                F.cavar(d, 0.12, 0.05)
                F.alb *= (1 - np.clip(1 - np.abs(d) / 0.06, 0, 1) * 0.22)[..., None]
            for (u, v) in OLHOS_UV:
                orb = np.hypot((F.x - u) / 0.95, (F.y - v) / 0.80)
                F.alb *= (1 - 0.55 * np.clip(1 - orb, 0, 1) ** 0.8)[..., None]
                F.alt -= 0.35 * np.clip(1 - orb, 0, 1)
                olho = (np.hypot((F.x - u) / 0.62, (F.y - v) / 0.40) - 1) * 0.4
                F.pintar(olho, 'escuro_v')
                F.luz(F.circ(u, v + 0.02, 0.17), PUPILA, PUPILA * 0.5, forca=1.1, largura=0.0, halo=0.25)
                bolsa = np.abs(np.hypot((F.x - u) / 0.8, (F.y - v - 0.25) / 0.55) - 1) * 0.5
                bolsa = np.where(F.y > v + 0.25, bolsa, 1.0)
                F.cavar(bolsa - 0.01, 0.12, 0.05)
                F.alb *= (1 - np.clip(1 - bolsa / 0.06, 0, 1) * 0.25)[..., None]
                for k in range(3):                                    # pes de galinha para fora
                    sx = -1 if u < w / 2 else 1
                    lin = _linha(F, [(u + sx * 0.65, v), (u + sx * 1.15, v - 0.25 + k * 0.25)])
                    F.cavar(lin - 0.015, 0.1, 0.04)
            for sx in (-1, 1):
                lin = _linha(F, [(w / 2 + sx * 0.7, 2.6), (w / 2 + sx * 1.25, 3.6), (w / 2 + sx * 1.45, 4.5)])
                F.cavar(lin - 0.02, 0.16, 0.06)
                F.alb *= (1 - np.clip(1 - lin / 0.08, 0, 1) * 0.3)[..., None]
            # sobrancelhas ralas e brancas, caidas
            for (u, v) in OLHOS_UV:
                sx = -1 if u < w / 2 else 1
                lin = _linha(F, [(u - sx * 0.6, v - 0.62), (u + sx * 0.7, v - 0.42)])
                F.pintar(lin - 0.07, 'cabelo_v', alt=0.08, bisel=0.04)
    elif mat == 'cabelo':
        F.pintar(tudo, 'cabelo_v', alt=0.25, bisel=0.2, ruido=0.08)
        fio = TH.fbm(F.x * 7.0, F.y * 0.5, sem * 0.2, oct=2, seed=sem) - 0.5
        if c.nome == 'coque':
            fio = TH.fbm(np.hypot(F.x - w / 2, F.y - h / 2) * 6.0, np.arctan2(F.y - h / 2, F.x - w / 2), 0.3, oct=2, seed=sem) - 0.5
        F.alt += 0.4 * fio
        F.alb *= (0.74 + 0.5 * (fio[..., None] + 0.5))
        if c.nome == 'cabelo_tras' and lado:
            F.a[F.y > h - 0.25 - 0.3 * np.abs(np.sin(F.x * 5.0))] = 0
    elif mat == 'meia':
        F.pintar(tudo, 'meia', alt=0.15, bisel=0.15, ruido=0.1)
        F.alt += 0.06 * np.sin(F.y * 9.0)
        F.alb *= (0.9 + 0.1 * np.sin(F.y * 9.0)[..., None])
    elif mat == 'chinelo':
        F.pintar(tudo, 'chinelo', alt=0.25, bisel=0.25, ruido=0.3, escala_ruido=5.0)
        F.alt += 0.2 * (TH.fbm(F.x * 6, F.y * 6, 0.5, oct=2, seed=sem) - 0.5)
        F.alb *= (0.75 + 0.35 * TH.fbm(F.x * 1.2, F.y * 1.2, 2.5, oct=2, seed=sem + 3)[..., None])
    elif mat == 'ferro':
        F.pintar(tudo, 'ferro', alt=0.3, bisel=0.25, ruido=0.2, escala_ruido=1.2)
        if f == 'up' and c.nome == 'panela':
            cx, cy = w / 2, h / 2
            F.cavar(F.circ(cx, cy, min(w, h) / 2 - 0.45), 0.25, 0.12)
            F.alt -= 0.25 * (F.circ(cx, cy, min(w, h) / 2 - 0.45) < 0)
            m = np.clip((TH.fbm(F.x * 1.8, F.y * 1.8, 0.9, oct=3, seed=sem) - 0.52) * 5, 0, 1) * (F.circ(cx, cy, 1.6) < 0)
            F.alb = F.alb * (1 - m[..., None]) + np.array([0.30, 0.04, 0.03])[None, None, :] * m[..., None]
        ferr = np.clip((TH.fbm(F.x * 2.5, F.y * 2.5, 3.1, oct=2, seed=sem + 7) - 0.66) * 4, 0, 1)
        F.alb = F.alb * (1 - ferr[..., None] * 0.6) + np.array([0.35, 0.18, 0.08])[None, None, :] * ferr[..., None] * 0.6
    elif mat == 'madeira':
        F.pintar(tudo, 'madeira_v', alt=0.2, bisel=0.15, ruido=0.1)
        veio = TH.fbm(F.x * 0.6, F.y * 6.0, 0.4, oct=2, seed=sem) - 0.5
        F.alb *= (0.8 + 0.4 * (veio[..., None] + 0.5))
    elif mat == 'boca':
        F.pintar(tudo, 'boca_v')
        if f == 'north':
            for dx in (0.5, 1.5, 2.9):
                F.pintar(F.ret(dx - 0.15, 0.0, dx + 0.15, 0.4, 0.05), 'dente_v', alt=0.12, bisel=0.06)
            F.luz(F.ret(0.6, 0.55, w - 0.6, h - 0.1, 0.2), np.array([0.45, 0.04, 0.03]), np.array([0.2, 0.0, 0.0]), forca=0.3, largura=0.0, halo=0.4)
    elif mat == 'pupila':
        F.pintar(tudo, 'escuro_v')
        F.luz(tudo + 0.05, PUPILA, PUPILA * 0.6, forca=1.7, largura=0.0, halo=0.3)
    else:
        F.pintar(tudo, 'escuro_v', alt=0.2)
    img = F.render()
    brilho = np.clip(np.max(F.emis, axis=2), 0, 1.2) / 1.2
    return img, brilho


def montar_atlas(cs, larg=512, alt_max=512):
    for esc in (8, 7, 6, 5, 4):
        if kit.arrumar_uv(cs, larg, esc) <= alt_max:
            break
    TH.S = esc
    img = np.zeros((alt_max, larg, 4), np.uint8)
    bri = np.zeros((alt_max, larg))
    for i, c in enumerate(cs):
        for f in c.faces():
            x0, y0, x1, y1 = c.uv[f]
            rgba, b = pintar_face(c, f, x1 - x0, y1 - y0, 700 + i * 7 + len(f), esc)
            img[y0:y1, x0:x1] = rgba[:y1 - y0, :x1 - x0]
            bri[y0:y1, x0:x1] = b[:y1 - y0, :x1 - x0]
    return img, bri, esc


def alfa_binario(rgba):
    out = rgba.copy()
    out[..., 3] = np.where(out[..., 3] >= 128, 255, 0)
    return out


def textura_bedrock(rgba, bri, forca=200):
    """entity_emissive_alpha: alfa 255 = normal, menor = brilha; alfa 0 TAMBEM brilha, entao nada fica com alfa 0."""
    out = alfa_binario(rgba)
    vis = out[..., 3] > 0
    out[..., 3] = np.where(vis, np.clip(255 - np.round(np.clip(bri, 0, 1) * forca), 40, 255), 255).astype(np.uint8)
    out[~vis, 0:3] = (30, 26, 34)
    return out


def gerar(saida):
    sys.path.insert(0, os.path.join(AQUI, '..', 'coronapack'))
    import bedrock_v2 as BV
    J = os.path.join(saida, 'java', 'assets', 'corona')
    B = os.path.join(saida, 'bedrock')
    base = cubos('idle')
    img, bri, esc = montar_atlas(base)
    os.makedirs(os.path.join(J, 'textures', 'item', 'props'), exist_ok=True)
    Image.fromarray(alfa_binario(img), 'RGBA').save(os.path.join(J, 'textures', 'item', 'props', 'vovo.png'), optimize=True)
    tex_bed = 'textures/entity/corona_v2/item_props_vovo'
    os.makedirs(os.path.join(B, os.path.dirname(tex_bed)), exist_ok=True)
    Image.fromarray(textura_bedrock(img, bri), 'RGBA').save(os.path.join(B, tex_bed + '.png'), optimize=True)
    mapeamento, item_tex, previas = [], {}, []
    for q in QUADROS:
        cs = cubos(q)
        for a, b in zip(cs, base):
            a.uv = dict(b.uv)
        nome = f'vovo_{q}'
        modelo = kit.modelo_java(cs, 'corona:item/props/vovo', 512, 512, display={})
        modelo['credit'] = 'Corona Server - a Vovo (personagem original)'
        kit.salvar_json(os.path.join(J, 'models', 'props', nome + '.json'), modelo)
        kit.salvar_json(os.path.join(J, 'items', 'props', nome + '.json'), kit.item_java(f'corona:props/{nome}'))
        geo_id = f'geometry.corona.prop_{nome}'
        kit.salvar_json(os.path.join(B, 'models', 'entity', 'corona_v2', f'prop_{nome}.geo.json'), BV.geometria_de_modelo(geo_id, modelo))
        at = BV.attachable_prop(f'corona:prop_{nome}', tex_bed, geo_id)
        at['minecraft:attachable']['description']['materials'] = {'default': 'entity_emissive_alpha', 'enchanted': 'entity_emissive_alpha'}
        kit.salvar_json(os.path.join(B, 'attachables', f'prop_{nome}.json'), at)
        if q in ('idle', 'grito', 'andar_04', 'andar_12'):
            for yaw in ((180, 140, 90, 0) if q == 'idle' else (150,) if q == 'grito' else (90,)):
                previas.append(kit.previa(cs, alfa_binario(img), yaw, 6, escala=9, fundo=(150, 158, 168)))
        if q == 'idle':
            pv = kit.previa(cs, alfa_binario(img), 180, 0, escala=5, fundo=(28, 30, 36))
            arr = np.array(pv.convert('RGBA'))
            fundo = (np.abs(arr[..., 0].astype(int) - 28) < 3) & (np.abs(arr[..., 1].astype(int) - 30) < 3) & (np.abs(arr[..., 2].astype(int) - 36) < 3)
            arr[fundo, 3] = 0
            ic = Image.fromarray(arr, 'RGBA')
            ic.thumbnail((32, 32), Image.LANCZOS)
            icone = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
            icone.paste(ic, ((32 - ic.width) // 2, (32 - ic.height) // 2))
        os.makedirs(os.path.join(B, 'textures', 'items', 'corona', 'v2'), exist_ok=True)
        icone.save(os.path.join(B, 'textures', 'items', 'corona', 'v2', f'corona_prop_{nome}.png'))
        item_tex[f'corona_prop_{nome}'] = {'textures': f'textures/items/corona/v2/corona_prop_{nome}'}
        mapeamento.append({'type': 'definition', 'model': f'corona:props/{nome}', 'bedrock_identifier': f'corona:prop_{nome}',
                           'display_name': f'Prop {nome}', 'bedrock_options': {'icon': f'corona_prop_{nome}', 'creative_category': 'equipment',
                                                                              'protection_value': 1}})
    kit.salvar_json(os.path.join(saida, 'corona-vovo.json'), {'format_version': 2, 'items': {'minecraft:leather_helmet': mapeamento}})
    kit.salvar_json(os.path.join(saida, 'item_texture_extra.json'), item_tex)
    kit.folha(previas, (150, 158, 168)).convert('RGB').save(os.path.join(saida, 'previa_vovo.png'))
    print('vovo: cubos', len(base), 'escala', esc, 'quadros', len(QUADROS))


if __name__ == '__main__':
    gerar(SAIDA)
