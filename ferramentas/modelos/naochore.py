"""Monstros 3D do Nao Chore: Vigia, Chorona e Faminto. Modelos proprios (cubos) + texturas pintadas por ruido 3D.

Cada monstro: cubos (idle), quadros de animacao (walk1, walk2, grito), olhos (modelo separado, brilha no escuro),
pintor da textura e o rosto do susto (imagem grande para a fonte do pacote).
"""
import math, os, sys
import numpy as np
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'skins', 'monstros'))
from base import fbm, ridged, rampa, mix, C, ss, elipse, dentro, dentes, vnoise, hash2  # noqa: E402
from kit import Cubo, girado  # noqa: E402

MONSTROS = {}


def luz(P, forca=0.2):
    """Luz de cima e sombra em baixo (o Java nao sombreia por face em item display)."""
    y = P['y']
    n = P['n']
    v = 1 - forca + forca * np.clip((y + 16) / 46, 0, 1)
    v = v + (0.08 if n[1] > 0 else (-0.14 if n[1] < 0 else 0)) + (0.03 if n[2] > 0 else (-0.04 if n[2] < 0 else 0))
    return v[..., None]


def borda(P, forca=0.3, larg=0.12):
    s, t = P['s'], P['t']
    e = np.minimum(np.minimum(s, 1 - s), np.minimum(t, 1 - t))
    return (1 - forca * (1 - ss(0, larg, e)))[..., None]


def sangue(P, sem=0):
    x, y, z = P['x'], P['y'], P['z']
    return rampa(fbm(x * 3, y * 3, z * 3, 2, 1, 95 + sem), [(0, '#1a0000'), (0.6, '#520404'), (1, '#8a1010')])


def respingo(P, lista, sem=0):
    x, y, z = P['x'], P['y'], P['z']
    m = np.zeros(x.shape)
    rug = fbm(x * 2, y * 2, z * 2, 3, 1, 90 + sem) - 0.5
    for k, (cx, cy, cz, r) in enumerate(lista):
        d = np.sqrt((x - cx) ** 2 + ((y - cy) * 0.9) ** 2 + (z - cz) ** 2) / r + rug * 0.9
        m = np.maximum(m, 1 - ss(0.85, 1.0, d))
        col = np.floor((x + z) * 4).astype(np.int64)
        comp = r * (0.8 + 2.5 * hash2(col, k, 91 + sem))
        esc = (hash2(col, k, 92 + sem) > 0.55) & (y < cy) & (y > cy - comp) & (np.abs(x - cx) + np.abs(z - cz) < r * 0.9)
        m = np.maximum(m, esc * 1.0)
    return m


def dente_cor(prof, base='#efe6cf'):
    return rampa(1 - np.clip(prof, 0, 1), [(0, '#5a4a32'), (0.35, '#c9b994'), (1, base)])


def boca_fundo(d):
    return rampa(np.clip(d, 0, 1), [(0, '#000000'), (0.5, '#140102'), (0.85, '#3c0508'), (1, '#64100f')])


def pano_rasgado(P, cor_a, cor_b, sem, hem=None, dens=1.0):
    """Tecido escuro gasto, com fiapos; hem = (y_min, altura) deixa a barra rasgada (alpha)."""
    x, y, z = P['x'], P['y'], P['z']
    tr = fbm(x * 2.5, y * 0.5, z * 2.5, 3, 1, sem)
    c = rampa(tr, [(0, cor_a), (1, cor_b)])
    fio = ss(0.6, 0.9, ridged(x * 0.8, y * 0.2, z * 0.8, 3, 1, sem + 1)) * 0.35
    c = c * (1 - fio)[..., None]
    a = np.ones(x.shape)
    if hem is not None:
        y0, h = hem
        corte = y0 + h * (0.3 + 0.7 * vnoise(x * 1.3, 0, z * 1.3, sem + 2)) * dens
        a = np.where(y < corte, 0.0, 1.0)
        a = np.where((y >= corte) & (y < corte + 0.6), np.where(hash2(np.floor(x * 3), np.floor(z * 3), sem + 3) > 0.4, 1.0, 0.0), a)
    return c, a


def escorrido(s, t, pontos, sem=0, larg=0.012):
    """Filetes escorrendo para baixo a partir de pontos (s0, t0, comprimento) no espaco da face."""
    m = np.zeros(s.shape)
    for k, (s0, t0, comp) in enumerate(pontos):
        wob = 0.01 * np.sin(t * 60 + k * 2.1) + 0.006 * np.sin(t * 23 + k)
        fino = larg * (1 - 0.6 * ss(t0, t0 + comp, t))
        m = np.maximum(m, ((np.abs(s - s0 - wob) < fino) & (t >= t0) & (t < t0 + comp)) * 1.0)
        m = np.maximum(m, dentro(elipse(s, t, s0, t0 + comp, larg * 1.6, larg * 2.2), 0.3) * 0.9)
    return m


def cabelo_longo(P, sem, cor='#07060a', y_fim=None, dens=0.85, frente=(0.22, 0.35), topo_y=None, cheio_y=None):
    """Fios pretos compridos (camada inflada): alpha so onde tem fio. Denso atras e dos lados; na frente cobre
    as laterais (fracao frente[0] de cada lado) e deixa so algumas mechas no meio (fracao frente[1])."""
    x, y, z, s, t = P['x'], P['y'], P['z'], P['s'], P['t']
    f = P['face']
    col = np.floor(s * 22 + 0.4 * np.sin(y * 1.9)).astype(np.int64)
    base_h = hash2(col, 0, sem)
    comp = (y_fim if y_fim is not None else -99) + 9 * hash2(col, 1, sem + 1) ** 2
    fio = (base_h < dens) & (y > comp)
    mecha = ss(0.2, 0.6, vnoise(s * 9, y * 0.5, 0, sem + 2))
    a = np.where(fio & (mecha > 0.18), 1.0, 0.0)
    if topo_y is not None:
        a = np.where(y > topo_y, 1.0, a)   # couro cabeludo todo coberto
    if cheio_y is not None and f != 'south':
        a = np.where(y > cheio_y, 1.0, a)  # atras e dos lados: cabeca toda coberta
    if f == 'up':
        a = np.where(hash2(np.floor(s * 12), np.floor(t * 12), sem + 4) < 0.97, 1.0, 0.0)
    if f == 'down':
        a = np.zeros_like(a)
    if f == 'south':
        lat = (s < frente[0]) | (s > 1 - frente[0])
        meio = hash2(col, 3, sem + 5) < frente[1]
        a = a * np.where(lat, 1.0, np.where(meio, 1.0, 0.0))
        if topo_y is not None:
            a = np.where(y > topo_y, 1.0, a)
    brilho = 0.7 + 0.5 * ridged(s * 30, y * 0.3, 0, 2, 1, sem + 6)
    c = C(cor) * brilho[..., None] + 0.03
    return c, a


def olhos_brilho(P, cor='#ffffff', centro='#ffffff'):
    s, t = P['s'], P['t']
    d = elipse(s, t, 0.5, 0.5, 0.5, 0.5)
    c = rampa(1 - np.clip(d, 0, 1), [(0, cor), (0.6, cor), (1, centro)])
    return c, np.where(d < 1, 1.0, 0.0)


# ====================================================================== VIGIA

def vigia_cubos():
    cs = []
    # pernas compridas e finas
    cs.append(Cubo('perna_d', (-5, -16, -2), (-1, 2, 2), 'calca', grupo='perna_d'))
    cs.append(Cubo('perna_e', (1, -16, -2), (5, 2, 2), 'calca', grupo='perna_e'))
    cs.append(Cubo('pe_d', (-5.5, -16, -3), (-0.5, -14, 3.5), 'pele', grupo='perna_d'))
    cs.append(Cubo('pe_e', (0.5, -16, -3), (5.5, -14, 3.5), 'pele', grupo='perna_e'))
    # tronco curvado e ombros ossudos
    cs.append(Cubo('tronco', (-5, 2, -2.5), (5, 18, 2.5), 'casaco', grupo='tronco'))
    cs.append(Cubo('ombros', (-7.5, 15, -3), (7.5, 18.5, 3), 'casaco', grupo='tronco'))
    cs.append(Cubo('peito', (-4, 8, 2.5), (4, 15, 3.2), 'pele', so=('south',), grupo='tronco'))
    # bracos muito compridos (passam do joelho)
    cs.append(Cubo('braco_d', (-10.5, -7, -1.5), (-7.5, 17, 1.5), 'braco', grupo='braco_d'))
    cs.append(Cubo('braco_e', (7.5, -7, -1.5), (10.5, 17, 1.5), 'braco', grupo='braco_e'))
    for k, dx in enumerate((-10.3, -9.3, -8.3)):
        cs.append(Cubo(f'dedo_d{k}', (dx, -13 - k % 2, -0.4), (dx + 0.8, -7, 0.4), 'dedo', grupo='braco_d'))
    for k, dx in enumerate((7.5, 8.5, 9.5)):
        cs.append(Cubo(f'dedo_e{k}', (dx, -13 - k % 2, -0.4), (dx + 0.8, -7, 0.4), 'dedo', grupo='braco_e'))
    cs.append(Cubo('pescoco', (-1.5, 18, -1.5), (1.5, 21.5, 1.5), 'pele', grupo='cabeca'))
    cs.append(Cubo('cabeca', (-4, 21, -4), (4, 30, 4), 'rosto', grupo='cabeca'))
    cs.append(Cubo('cabelo', (-4.6, 13, -4.6), (4.6, 30.6, 4.6), 'cabelo', grupo='cabeca'))
    return cs


def vigia_tilt(cs, ang=22.5):
    """Cabeca torta (observando)."""
    out = []
    for c in cs:
        if c.grupo == 'cabeca':
            out.append(girado(c, 'z', ang, (0, 20, 0)))
        else:
            out.append(c)
    return out


def pele_detalhe(P, c, sem, forca=1.0):
    """Detalhe fino de pele: poros, manchas pequenas e sujeira (deixa menos 'liso' de perto)."""
    x, y, z = P['x'], P['y'], P['z']
    poro = fbm(x * 6, y * 6, z * 6, 2, 1, sem) - 0.5
    mancha = ss(0.68, 0.85, fbm(x * 1.8, y * 1.8, z * 1.8, 3, 1, sem + 1))
    sujo = ss(0.6, 0.9, fbm(x * 0.9, y * 2.5, z * 0.9, 3, 1, sem + 2))
    c = c * (1 + 0.12 * forca * poro)[..., None]
    c = mix(c, c * 0.55, mancha * 0.5 * forca)
    c = mix(c, C('#2a2320'), sujo * 0.35 * forca)
    return c


def vigia_quadros(cs):
    def anda(sinal):
        out = []
        for c in cs:
            g = c.grupo
            if g == 'perna_d':
                out.append(girado(c, 'x', 22.5 * sinal, (0, 2, 0)))
            elif g == 'perna_e':
                out.append(girado(c, 'x', -22.5 * sinal, (0, 2, 0)))
            elif g == 'braco_d':
                out.append(girado(c, 'x', -22.5 * sinal, (0, 16, 0)))
            elif g == 'braco_e':
                out.append(girado(c, 'x', 22.5 * sinal, (0, 16, 0)))
            elif g == 'cabeca':
                out.append(girado(c, 'z', 22.5, (0, 20, 0)))
            else:
                out.append(c)
        return out

    def grito():
        out = []
        for c in cs:
            g = c.grupo
            if g in ('braco_d', 'braco_e'):
                out.append(girado(c, 'x', -45, (0, 16, 0)))  # bracos para a frente/cima
            elif c.nome == 'cabeca':
                out.append(c.com(pele='rosto_grito'))
            elif g == 'cabeca':
                out.append(c)
            else:
                out.append(c)
        return out
    return {'idle': vigia_tilt(cs), 'walk1': anda(1), 'walk2': anda(-1), 'grito': grito()}


def vigia_olhos(cs_idle):
    """Dois olhos brancos (modelo separado, brilho maximo). Acompanham a inclinacao da cabeca no idle."""
    o = [Cubo('olho_d', (-2.6, 25.4, 4.0), (-1.0, 26.8, 4.6), 'olho', so=('south', 'up', 'down', 'east', 'west')),
         Cubo('olho_e', (1.0, 25.4, 4.0), (2.6, 26.8, 4.6), 'olho', so=('south', 'up', 'down', 'east', 'west'))]
    return {'idle': [girado(c, 'z', 22.5, (0, 20, 0)) for c in o], 'grito': o}


def vigia_pintor(P):
    x, y, z, s, t = P['x'], P['y'], P['z'], P['s'], P['t']
    pele, face = P['pele'], P['face']
    if pele == 'olho':
        return olhos_brilho(P, '#ffffff', '#fff6d0')
    if pele == 'cabelo':
        return cabelo_longo(P, 21, '#050408', y_fim=13, dens=0.8, frente=(0.16, 0.12), topo_y=28.6, cheio_y=22)
    if pele in ('calca', 'casaco'):
        c, a = pano_rasgado(P, '#0b0a0d', '#23212a', 31 if pele == 'calca' else 32, hem=(-16, 4) if pele == 'calca' else None)
        # costuras e rasgos mostrando pele cinza
        rasgo = ss(0.78, 0.92, ridged(x * 1.1, y * 0.5, z * 1.1, 3, 0.9, 33))
        c = mix(c, C('#8d8b86'), rasgo * 0.9)
        return c * luz(P, 0.3) * borda(P, 0.25), a
    # pele: cinza-palida, veias escuras, manchas roxas
    n = fbm(x, y, z, 4, 0.5, 41)
    c = rampa(n, [(0, '#5d5c5e'), (0.45, '#8e8c8a'), (0.75, '#b8b5ae'), (1, '#d6d2c8')])
    veia = ss(0.6, 0.9, ridged(x * 0.9, y * 0.45, z * 0.9, 3, 0.8, 42))
    c = mix(c, C('#2e2a3b'), veia * 0.55)
    mancha = ss(0.62, 0.8, fbm(x * 0.6, y * 0.6, z * 0.6, 3, 1, 43))
    c = mix(c, C('#4b3a55'), mancha * 0.45)
    c = pele_detalhe(P, c, 47)
    if pele == 'dedo':
        c = mix(c, C('#1a1618'), ss(0.0, 1.0, t) * 0.9)  # dedos escurecendo ate a ponta preta
        return c * luz(P), 1
    if pele in ('rosto', 'rosto_grito') and face == 'south':
        c = np.clip(c * 1.12, 0, 1)
        # bochechas fundas e rachaduras na pele
        bochecha = ss(0.5, 0.85, t) * (1 - ss(0.5, 0.85, t)) * 4 * ss(0.3, 0.0, np.abs(s - 0.5)) * 0
        covas = (ss(0.55, 0.72, t) * (1 - ss(0.72, 0.92, t))) * (ss(0.12, 0.3, np.abs(s - 0.5)))
        c = c * (1 - 0.35 * covas)[..., None]
        rach = ss(0.78, 0.95, ridged(x * 1.6, y * 1.6, z * 1.6, 3, 1, 45))
        c = c * (1 - 0.55 * rach)[..., None]
        # orbitas enormes, fundas e irregulares; pupila branca pequena fora do centro (assimetrica)
        for cx, off, rx, ry in ((0.3, -0.012, 0.15, 0.21), (0.705, 0.02, 0.165, 0.235)):
            wob = (fbm(s * 6 + cx * 10, t * 6, 0, 2, 1, 44) - 0.5) * 0.2
            d = elipse(s, t, cx, 0.44 + (0.015 if cx > 0.5 else 0), rx, ry) + wob
            c = c * (1 - 0.75 * dentro(d / 1.55, 0.5))[..., None]
            c = mix(c, C('#3a2a46'), (dentro(d / 1.25, 0.25) - dentro(d, 0.05)).clip(0, 1) * 0.6)
            c = mix(c, C('#050409'), dentro(d, 0.05))
            pup = elipse(s, t, cx + off, 0.49, 0.03, 0.036)
            c = mix(c, C('#fdfbf0'), dentro(pup, 0.35))
            c = mix(c, C('#8a8a9a'), (dentro(pup / 0.75, 0.3)) * 0.0)
        for cx in (0.46, 0.54):
            nd = elipse(s, t, cx, 0.66, 0.02, 0.045)
            c = mix(c, C('#15100f'), dentro(nd, 0.3))
        if pele == 'rosto':
            # boca costurada: fio claro grosso em X, buracos escuros, pele repuxada
            yb = 0.83 + 0.025 * np.sin(s * 9)
            lin = (np.abs(t - yb) < 0.016) & (s > 0.17) & (s < 0.83)
            c = np.where(lin[..., None], C('#1b0608') * np.ones_like(c), c)
            for k in range(7):
                sx = 0.2 + k * 0.1
                xx = ((np.abs((s - sx) - (t - yb) * 0.9) < 0.014) | (np.abs((s - sx) + (t - yb) * 0.9) < 0.014)) & (np.abs(t - yb) < 0.06)
                c = np.where(xx[..., None], C('#cdbf9c') * np.ones_like(c), c)
                fur = dentro(elipse(s, t, sx, yb - 0.055, 0.012, 0.014), 0.4) + dentro(elipse(s, t, sx, yb + 0.055, 0.012, 0.014), 0.4)
                c = mix(c, C('#2a0a0c'), fur.clip(0, 1))
            c = mix(c, sangue(P, 5), 0.85 * escorrido(s, t, [(0.17, 0.84, 0.16), (0.83, 0.84, 0.1), (0.5, 0.86, 0.08)], 5).clip(0, 1))
        else:
            # boca rasgada: a costura arrebentou; buraco irregular, duas fileiras de dentes, fios de linha soltos
            wob = (fbm(s * 5, t * 5, 1, 2, 1, 46) - 0.5) * 0.22
            d = elipse(s, t, 0.5, 0.8, 0.27, 0.165) + wob
            c = c * (1 - 0.55 * dentro(d / 1.25, 0.3))[..., None]
            c = mix(c, boca_fundo(1 - d), dentro(d, 0.04))
            ang = np.arctan2((t - 0.8) / 0.165, (s - 0.5) / 0.27)
            m, p, _ = dentes(ang, 1 - d, 0.34, 0.36, afiado=1.6)
            m &= d < 1
            c = np.where(m[..., None], dente_cor(p, '#d9cfb5'), c)
            for k in range(7):
                sx = 0.2 + k * 0.1
                fio = (np.abs(s - sx - 0.02 * np.sin(t * 40 + k)) < 0.01) & (d > 0.9) & (d < 1.45) & (t > 0.62)
                c = np.where(fio[..., None], C('#cdbf9c') * np.ones_like(c), c)
            c = mix(c, sangue(P, 6), 0.9 * escorrido(s, t, [(0.24, 0.9, 0.12), (0.3, 0.93, 0.08), (0.74, 0.9, 0.1), (0.68, 0.94, 0.06), (0.5, 0.96, 0.05)], 6).clip(0, 1))
        return c * luz(P, 0.15) * borda(P, 0.15), 1
    if pele in ('rosto', 'rosto_grito'):
        # resto da cabeca: couro cabeludo com falhas, orelhas nao
        return c * luz(P) * borda(P, 0.25), 1
    # peito (pele a mostra pelo rasgo do casaco): costelas marcadas
    if P['nome'] == 'peito':
        for k in range(4):
            ty = 0.2 + k * 0.2
            q = (s - 0.5) / 0.5
            curva = ty + 0.08 * q ** 2
            sulco = np.exp(-((t - curva) / 0.03) ** 2) * (np.abs(q) > 0.1)
            c = c * (1 - 0.5 * sulco)[..., None]
    return c * luz(P) * borda(P, 0.25), 1


def vigia_rosto(P):
    """Rosto do susto (imagem grande): o rosto do Vigia com a boca arrebentada, de perto."""
    return vigia_pintor(dict(P, pele='rosto_grito', face='south', nome='cabeca'))


MONSTROS['vigia'] = dict(cubos=vigia_cubos, quadros=vigia_quadros, olhos=vigia_olhos, pintor=vigia_pintor, rosto=vigia_rosto,
                         escala=1.25, altura_pe=16, rosto_cubo=('cabeca', (-4, 21, -4), (4, 30, 4)))


# ====================================================================== CHORONA

def chorona_cubos():
    cs = []
    cs.append(Cubo('saia', (-6, -16, -4), (6, -2, 4), 'vestido', grupo='saia'))
    cs.append(Cubo('tronco', (-4, -2, -2.5), (4, 12, 2.5), 'vestido', grupo='tronco'))
    cs.append(Cubo('braco_d', (-7, -6, -1.5), (-4, 11, 1.5), 'manga', grupo='braco_d'))
    cs.append(Cubo('braco_e', (4, -6, -1.5), (7, 11, 1.5), 'manga', grupo='braco_e'))
    cs.append(Cubo('mao_d', (-7, -10, -1.5), (-4, -6, 1.5), 'mao', grupo='braco_d'))
    cs.append(Cubo('mao_e', (4, -10, -1.5), (7, -6, 1.5), 'mao', grupo='braco_e'))
    cs.append(Cubo('pescoco', (-1.5, 12, -1.5), (1.5, 14, 1.5), 'pele', grupo='cabeca'))
    cs.append(Cubo('cabeca', (-4, 13.5, -4), (4, 22, 4), 'rosto', grupo='cabeca'))
    cs.append(Cubo('cabelo', (-5, 3, -5), (5, 22.8, 5), 'cabelo', grupo='cabeca'))
    return cs


def chorona_quadros(cs):
    def inclina(ang):
        return [girado(c, 'x', ang, (0, 13.5, 0)) if c.grupo == 'cabeca' else c for c in cs]

    def grito():
        out = []
        for c in cs:
            if c.nome == 'cabeca':
                out.append(girado(c.com(pele='rosto_grito'), 'x', -22.5, (0, 13.5, 0)))
            elif c.nome == 'cabelo':
                out.append(girado(c.com(pele='cabelo_atras'), 'x', -22.5, (0, 13.5, 0)))
            elif c.grupo == 'cabeca':
                out.append(girado(c, 'x', -22.5, (0, 13.5, 0)))
            elif c.grupo in ('braco_d', 'braco_e'):
                out.append(girado(c, 'x', -45, (0, 10, 0)))
            else:
                out.append(c)
        return out
    # cabeca baixa olhando para o chao (idle); nos quadros de "andar" ela flutua: so balanca os bracos
    return {'idle': inclina(22.5), 'walk1': [girado(c, 'z', 22.5, (0, 10, 0)) if c.grupo == 'braco_e' else c for c in inclina(22.5)],
            'walk2': [girado(c, 'z', -22.5, (0, 10, 0)) if c.grupo == 'braco_d' else c for c in inclina(22.5)], 'grito': grito()}


def chorona_olhos(cs):
    o = [Cubo('olho_d', (-2.5, 17.6, 4.0), (-1.1, 18.8, 4.6), 'olho', so=('south', 'up', 'down', 'east', 'west')),
         Cubo('olho_e', (1.1, 17.6, 4.0), (2.5, 18.8, 4.6), 'olho', so=('south', 'up', 'down', 'east', 'west'))]
    return {'idle': [girado(c, 'x', 22.5, (0, 13.5, 0)) for c in o], 'grito': [girado(c, 'x', -22.5, (0, 13.5, 0)) for c in o]}


def chorona_pintor(P):
    x, y, z, s, t = P['x'], P['y'], P['z'], P['s'], P['t']
    pele, face = P['pele'], P['face']
    if pele == 'olho':
        return olhos_brilho(P, '#d8e8ff', '#ffffff')
    if pele == 'cabelo':
        return cabelo_longo(P, 51, '#040407', y_fim=3, dens=0.95, frente=(0.2, 0.42), topo_y=20.5, cheio_y=14)
    if pele == 'cabelo_atras':
        c, a = cabelo_longo(P, 51, '#040407', y_fim=3, dens=0.95, frente=(0.2, 0.42), topo_y=20.5, cheio_y=14)
        if face == 'south':
            a = a * np.where(s < 0.2, 1.0, np.where(s > 0.8, 1.0, 0.0))  # cabelo jogado para tras: rosto todo a mostra
        return c, a
    if pele in ('vestido', 'manga'):
        n = fbm(x * 0.8, y * 0.8, z * 0.8, 4, 0.7, 52)
        c = rampa(n, [(0, '#8c8d8a'), (0.4, '#c9cac3'), (0.75, '#e4e4de'), (1, '#f1f0ea')])
        sujo = ss(0.5, 0.85, fbm(x * 0.5, y * 0.35, z * 0.5, 3, 1, 53))
        c = mix(c, C('#4a4742'), sujo * 0.55)
        dobra = ss(0.7, 0.95, ridged(x * 0.5, y * 0.15, z * 0.5, 3, 0.9, 54))
        c = c * (1 - 0.35 * dobra)[..., None]
        # lagrimas pretas escorrendo pelo vestido e manchas de lama/sangue na barra
        gota = ss(0.86, 0.95, ridged(x * 2.2, y * 0.08, z * 2.2, 2, 1, 55)) * ss(-2, 8, y)
        c = mix(c, C('#0b0a0c'), gota * 0.85)
        barra = ss(-9, -16, y) if pele == 'vestido' else 0
        c = mix(c, rampa(fbm(x * 2, y * 2, z * 2, 2, 1, 56), [(0, '#241812'), (1, '#4a2c22')]), barra * 0.7)
        a = np.ones(x.shape)
        if P['nome'] == 'saia' and face != 'up':
            corte = -16 + 4.5 * (0.2 + 0.8 * vnoise(x * 1.1, 0, z * 1.1, 57))
            a = np.where(y < corte, 0.0, 1.0)
        return c * luz(P, 0.25) * borda(P, 0.2), a
    # pele azulada de afogada
    n = fbm(x, y, z, 4, 0.5, 61)
    c = rampa(n, [(0, '#5c6b78'), (0.45, '#8f9ca6'), (0.8, '#c2cbd0'), (1, '#e3e8ea')])
    veia = ss(0.62, 0.9, ridged(x * 0.9, y * 0.5, z * 0.9, 3, 0.8, 62))
    c = mix(c, C('#2b3550'), veia * 0.5)
    c = pele_detalhe(P, c, 67, 0.8)
    if pele == 'mao':
        c = mix(c, C('#1c1a22'), ss(0.3, 1.0, t) * 0.85)  # dedos pretos
        return c * luz(P), 1
    if pele in ('rosto', 'rosto_grito') and face == 'south':
        for cx in (0.31, 0.69):
            d = elipse(s, t, cx, 0.42, 0.13, 0.15)
            c = c * (1 - 0.55 * dentro(d / 1.5, 0.5))[..., None]
            c = mix(c, C('#f2f0ea'), dentro(d, 0.08))               # olho todo branco (sem iris)
            c = mix(c, C('#7a1a24'), (dentro(d / 0.92, 0.1) - dentro(d / 1.1, 0.1)).clip(0, 1) * 0.0)
            # borda vermelha inflamada
            anel = (dentro(d / 1.12, 0.08) - dentro(d, 0.08)).clip(0, 1)
            c = mix(c, C('#8b1e26'), anel * 0.85)
            # lagrimas pretas: varios filetes finos e tortos descendo ate o queixo, uns mais longos
            for k, dx in enumerate((-0.085, -0.05, -0.015, 0.02, 0.055, 0.09)):
                comp = 0.25 + 0.6 * hash2(k, int(cx * 100), 63)
                lag = (np.abs(s - (cx + dx) - 0.009 * np.sin(t * 31 + k * 1.7) - 0.004 * np.sin(t * 77)) < 0.0055 + 0.0025 * (k % 3)) & (t > 0.5) & (t < 0.5 + comp)
                c = np.where(lag[..., None], C('#070608') * np.ones_like(c), c)
            # borrao escuro embaixo dos olhos
            c = mix(c, C('#1a1016'), (ss(0.5, 0.62, t) * (1 - ss(0.62, 0.8, t)) * dentro(elipse(s, t, cx, 0.6, 0.16, 0.3), 0.5)) * 0.35)
        for cx in (0.47, 0.53):
            nd = elipse(s, t, cx, 0.64, 0.018, 0.035)
            c = mix(c, C('#1a1216'), dentro(nd, 0.3))
        if pele == 'rosto':
            # boca pequena entreaberta, tremendo
            d = elipse(s, t, 0.5, 0.82, 0.11, 0.045)
            c = mix(c, boca_fundo(1 - d), dentro(d, 0.1))
        else:
            # berro: boca enorme, escancarada, dentes tortos
            d = elipse(s, t, 0.5, 0.8, 0.22, 0.2)
            c = c * (1 - 0.5 * dentro(d / 1.2, 0.3))[..., None]
            c = mix(c, boca_fundo(1 - d), dentro(d, 0.04))
            ang = np.arctan2((t - 0.8) / 0.2, (s - 0.5) / 0.22)
            m, p, _ = dentes(ang, 1 - d, 0.25, 0.5, afiado=1.4)
            m &= d < 1
            c = np.where(m[..., None], dente_cor(p, '#cfc2a6'), c)
        return c * luz(P, 0.15) * borda(P, 0.2), 1
    return c * luz(P) * borda(P, 0.25), 1


def chorona_rosto(P):
    c, a = chorona_pintor(dict(P, pele='rosto_grito', face='south', nome='cabeca'))
    # cabelo caindo pelos lados do rosto (so na imagem do susto)
    s, t = P['s'], P['t']
    col = np.floor(s * 40).astype(np.int64)
    lat = ss(0.0, 0.2, np.abs(s - 0.5) - 0.18)
    fio = (hash2(col, 0, 58) < 0.75) & (t < 0.1 + 0.9 * hash2(col, 1, 59)) & (vnoise(s * 30, t * 4, 0, 60) > 0.35)
    m = np.where(fio, lat, 0) * np.where(t < 0.1, 1.0, 1.0)
    c = mix(c, C('#040407'), m)
    return c, a


MONSTROS['chorona'] = dict(cubos=chorona_cubos, quadros=chorona_quadros, olhos=chorona_olhos, pintor=chorona_pintor, rosto=chorona_rosto,
                           escala=1.1, altura_pe=16, rosto_cubo=('cabeca', (-4, 13.5, -4), (4, 22, 4)))


# ====================================================================== FAMINTO

def faminto_cubos():
    cs = []
    cs.append(Cubo('tronco', (-4, 2, -9), (4, 10, 7), 'costela', grupo='tronco'))
    cs.append(Cubo('quadril', (-3.5, 2.5, -13), (3.5, 9, -9), 'couro', grupo='tronco'))
    for k in range(6):
        zz = -7 + k * 2.6
        cs.append(Cubo(f'espinha{k}', (-1, 10, zz), (1, 12 - 0.15 * k, zz + 1.6), 'osso', grupo='tronco'))
    cs.append(Cubo('pescoco', (-2.5, 6, 7), (2.5, 11, 11), 'couro', grupo='cabeca'))
    cs.append(Cubo('cabeca', (-5, 6, 11), (5, 15, 21), 'rosto', grupo='cabeca'))
    cs.append(Cubo('queixo', (-4.5, 3.5, 12), (4.5, 6, 21), 'queixo', grupo='queixo'))
    for k, dx in enumerate((-3.6, -1.4, 0.8, 3.0)):
        cs.append(Cubo(f'presa{k}', (dx, 3.6 + 0.3 * (k % 2), 20.2), (dx + 0.7, 6.0, 20.9), 'presa', grupo='cabeca'))
    # bracos/pernas da frente compridos
    cs.append(Cubo('braco_d', (-6.5, -16, 2.5), (-3.5, 6, 5.5), 'couro', grupo='braco_d'))
    cs.append(Cubo('braco_e', (3.5, -16, 2.5), (6.5, 6, 5.5), 'couro', grupo='braco_e'))
    for k, dx in enumerate((-6.5, -5.4, -4.3)):
        cs.append(Cubo(f'garra_d{k}', (dx, -16, 5.5), (dx + 0.8, -14.5, 9), 'garra', grupo='braco_d'))
    for k, dx in enumerate((3.5, 4.6, 5.7)):
        cs.append(Cubo(f'garra_e{k}', (dx, -16, 5.5), (dx + 0.8, -14.5, 9), 'garra', grupo='braco_e'))
    # pernas de tras dobradas
    cs.append(Cubo('coxa_d', (-6.5, -3, -13), (-3.5, 5, -8), 'couro', grupo='perna_d'))
    cs.append(Cubo('coxa_e', (3.5, -3, -13), (6.5, 5, -8), 'couro', grupo='perna_e'))
    cs.append(Cubo('canela_d', (-6.3, -16, -14), (-3.7, -3, -11), 'couro', grupo='perna_d'))
    cs.append(Cubo('canela_e', (3.7, -16, -14), (6.3, -3, -11), 'couro', grupo='perna_e'))
    return cs


def faminto_quadros(cs):
    def anda(sinal):
        out = []
        for c in cs:
            g = c.grupo
            if g == 'braco_d':
                out.append(girado(c, 'x', 22.5 * sinal, (0, 6, 4)))
            elif g == 'braco_e':
                out.append(girado(c, 'x', -22.5 * sinal, (0, 6, 4)))
            elif g == 'perna_d':
                out.append(girado(c, 'x', -22.5 * sinal, (0, 5, -10)))
            elif g == 'perna_e':
                out.append(girado(c, 'x', 22.5 * sinal, (0, 5, -10)))
            elif g == 'queixo':
                out.append(girado(c, 'x', 22.5, (0, 6, 12)))
            else:
                out.append(c)
        return out

    def grito():
        out = []
        for c in cs:
            if c.grupo == 'queixo':
                out.append(girado(c, 'x', 45, (0, 6, 12)))
            elif c.grupo == 'cabeca':
                out.append(girado(c, 'x', -22.5, (0, 8, 9)))
            else:
                out.append(c)
        return out
    return {'idle': [girado(c, 'x', 22.5, (0, 6, 12)) if c.grupo == 'queixo' else c for c in cs], 'walk1': anda(1), 'walk2': anda(-1), 'grito': grito()}


def faminto_olhos(cs):
    o = [Cubo('olho_d', (-3.6, 11.2, 21.0), (-1.8, 12.6, 21.6), 'olho', so=('south', 'up', 'down', 'east', 'west')),
         Cubo('olho_e', (1.8, 11.2, 21.0), (3.6, 12.6, 21.6), 'olho', so=('south', 'up', 'down', 'east', 'west'))]
    return {'idle': o, 'grito': [girado(c, 'x', -22.5, (0, 8, 9)) for c in o]}


def faminto_pintor(P):
    x, y, z, s, t = P['x'], P['y'], P['z'], P['s'], P['t']
    pele, face = P['pele'], P['face']
    if pele == 'olho':
        return olhos_brilho(P, '#ffd23a', '#fff4b0')
    if pele in ('osso', 'presa', 'garra'):
        n = fbm(x * 2, y * 2, z * 2, 3, 1, 71)
        c = rampa(n, [(0, '#7a6a52'), (0.5, '#c8b792'), (1, '#efe3c5')])
        if pele != 'osso':
            c = mix(c, C('#3a2a20'), ss(0.0, 1.0, t if pele == 'garra' else 1 - t) * 0.0)
            c = mix(c, sangue(P, 8), ss(0.55, 1.0, t if pele == 'presa' else 1 - t) * 0.6)
        return c * luz(P, 0.15), 1
    # couro cinza-marrom esticado sobre osso
    n = fbm(x, y, z, 4, 0.45, 72)
    c = rampa(n, [(0, '#2f2621'), (0.4, '#5a4c40'), (0.75, '#8a7a67'), (1, '#b3a48f')])
    rug = ss(0.55, 0.9, ridged(x * 1.2, y * 1.2, z * 1.2, 3, 0.9, 73))
    c = c * (1 - 0.45 * rug)[..., None]
    veia = ss(0.65, 0.9, ridged(x * 0.6, y * 0.6, z * 0.6, 2, 0.8, 74))
    c = mix(c, C('#1e1a2a'), veia * 0.4)
    if pele == 'costela' and face in ('east', 'west', 'up'):
        # costelas saltadas
        for k in range(6):
            zz = -7 + k * 2.6
            q = np.abs(z - zz - 0.8)
            osso = np.exp(-(q / 0.55) ** 2)
            c = mix(c, rampa(1 - q, [(0, '#8d7d64'), (1, '#e9dcbd')]), osso * 0.85)
            c = c * (1 - 0.35 * np.exp(-((q - 1.3) / 0.35) ** 2))[..., None]
    if pele == 'costela' and face == 'down':
        # barriga encolhida, pele colada
        c = c * (0.6 + 0.2 * ss(0, 1, np.abs(s - 0.5)))[..., None]
    if pele == 'queixo':
        if face == 'up':
            # dentro da boca de baixo: gengiva preta e fileira de dentes finos nas bordas
            c = boca_fundo(np.zeros_like(s) + 0.3)
            m, p, _ = dentes(s * 9, 0.12 - np.minimum(t, 1 - t) * 0 + (0.13 - t) * 0 + np.minimum(t, 1 - t), 0.0, 1.0)
            fileira = (np.minimum(t, 1 - t) < 0.14) | (s > 0.86)
            m2, p2, _ = dentes(np.where(np.minimum(t, 1 - t) < 0.14, s * 9, t * 9), 0.14 - np.minimum(np.minimum(t, 1 - t), 1 - s), 0.12, 1.0, afiado=2.0)
            dm = fileira & (hash2(np.floor(s * 9), np.floor(t * 2), 75) > 0.1) & (np.minimum(np.minimum(t, 1 - t), 1 - s) < 0.12)
            c = np.where(dm[..., None], dente_cor(p2, '#e6dcc0'), c)
            c = mix(c, sangue(P, 9), 0.5 * ss(0.3, 0.9, fbm(x * 2, y, z * 2, 2, 1, 76)))
            return c, 1
        c = mix(c, sangue(P, 10), respingo(P, [(0, 4, 20, 3.0), (-3, 5, 18, 1.5), (3, 4.5, 19, 1.5)], 11) * 0.95)
        return c * luz(P, 0.1) * borda(P, 0.2), 1
    if pele == 'rosto':
        if face == 'down':
            # ceu da boca: preto com dentes de cima
            c = boca_fundo(np.zeros_like(s) + 0.25)
            m, p, _ = dentes(s * 9, np.minimum(t, 1 - t), 0.14, 1.0, afiado=2.0)
            dm = (np.minimum(t, 1 - t) < 0.14) & (hash2(np.floor(s * 9), np.floor(t * 2), 77) > 0.1)
            c = np.where(dm[..., None], dente_cor(p, '#e6dcc0'), c)
            return c, 1
        if face == 'south':
            # focinho: narinas rasgadas, olhos fundos amarelos (o brilho e o modelo dos olhos)
            for cx in (0.27, 0.73):
                d = elipse(s, t, cx, 0.33, 0.14, 0.13)
                c = c * (1 - 0.7 * dentro(d / 1.5, 0.5))[..., None]
                c = mix(c, C('#0a0806'), dentro(d, 0.06))
                c = mix(c, C('#9a7a1a'), dentro(elipse(s, t, cx, 0.35, 0.05, 0.05), 0.3) * 0.9)
            for cx in (0.42, 0.58):
                nd = elipse(s, t, cx, 0.62, 0.035, 0.06, 0.4 if cx < 0.5 else -0.4)
                c = mix(c, C('#120a08'), dentro(nd, 0.25))
            # labio de cima repuxado mostrando gengiva e dentes
            gengiva = t > 0.82
            c = np.where(gengiva[..., None], rampa(fbm(x * 3, y * 3, z * 3, 2, 1, 78), [(0, '#3a0a10'), (1, '#6b1a22')]), c)
            m, p, _ = dentes(s * 9, t - 0.84, 0.16, 1.0, afiado=2.0)
            c = np.where((m & gengiva)[..., None], dente_cor(p, '#e6dcc0'), c)
            c = mix(c, sangue(P, 12), respingo(P, [(-3, 8, 21, 1.8), (2.5, 7.5, 21, 2.2)], 13) * 0.9)
            return c * luz(P, 0.1) * borda(P, 0.15), 1
        # resto da cabeca: couro com cicatrizes
        cic = ss(0.8, 0.95, ridged(x * 0.7, y * 0.9, z * 0.7, 2, 1, 79))
        c = mix(c, C('#4e2a28'), cic * 0.8)
    return c * luz(P, 0.22) * borda(P, 0.22), 1


def faminto_rosto(P):
    return faminto_pintor(dict(P, pele='rosto', face='south', nome='cabeca'))


MONSTROS['faminto'] = dict(cubos=faminto_cubos, quadros=faminto_quadros, olhos=faminto_olhos, pintor=faminto_pintor, rosto=faminto_rosto,
                           escala=1.2, altura_pe=16, rosto_cubo=('cabeca', (-5, 6, 11), (5, 15, 21)))


# ====================================================================== MAOS (saem do chao e agarram o pe)

def maos_cubos():
    """Duas maos saindo da terra (o modelo fica com a base em y=-16 = chao): palma, 4 dedos compridos e polegar."""
    cs = []
    for lado, sx in (('d', -1), ('e', 1)):
        cx = sx * 4.5
        cs.append(Cubo(f'palma_{lado}', (cx - 2, -16, -1.5), (cx + 2, -13.5, 1.5), 'maos', grupo=f'mao_{lado}'))
        for k, dz in enumerate((-1.4, -0.45, 0.5, 1.45)):
            alt = 5.5 - abs(k - 1.5) * 1.2
            fx = cx + sx * 1.2
            cs.append(Cubo(f'dedo_{lado}{k}', (fx - 0.45, -13.5, dz - 0.4), (fx + 0.45, -13.5 + alt, dz + 0.4), 'dedo', grupo=f'mao_{lado}'))
        px = cx - sx * 2.4
        cs.append(Cubo(f'polegar_{lado}', (px - 0.45, -14.5, 0.3), (px + 0.45, -10.5, 1.1), 'dedo', grupo=f'mao_{lado}'))
    return cs


def maos_quadros(cs):
    def curva(ang):
        out = []
        for c in cs:
            if c.nome.startswith('dedo') or c.nome.startswith('polegar'):
                sx = -1 if c.grupo.endswith('_d') else 1
                out.append(girado(c, 'z', -ang * sx, (c.de[0] + (c.ate[0] - c.de[0]) / 2, -13.5, 0)))
            else:
                out.append(c)
        return out
    return {'idle': curva(0), 'walk1': curva(22.5), 'walk2': curva(0), 'grito': curva(45)}


def maos_olhos(cs):
    return {'idle': [], 'grito': []}


def maos_pintor(P):
    x, y, z, s, t = P['x'], P['y'], P['z'], P['s'], P['t']
    n = fbm(x, y, z, 4, 0.6, 81)
    c = rampa(n, [(0, '#2a2522'), (0.4, '#575049'), (0.75, '#8a8178'), (1, '#b2a99c')])
    terra = ss(0.45, 0.8, fbm(x * 1.4, y * 1.4, z * 1.4, 3, 1, 82)) * ss(-12, -16, y)
    c = mix(c, rampa(fbm(x * 3, y * 3, z * 3, 2, 1, 83), [(0, '#1e140c'), (1, '#4a3321')]), terra * 0.9)
    veia = ss(0.65, 0.9, ridged(x * 1.2, y * 0.6, z * 1.2, 2, 0.8, 84))
    c = mix(c, C('#1f1a28'), veia * 0.5)
    if P['pele'] == 'dedo':
        c = mix(c, C('#15110f'), ss(0.0, 1.0, 1 - t) * 0.7 * 0)
        unha = (1 - t) > 0.8
        c = np.where(unha[..., None], rampa(fbm(x * 4, y * 4, z * 4, 2, 1, 85), [(0, '#6e6250'), (1, '#c9bda3')]), c)
    return c * luz(P, 0.1), 1


MONSTROS['maos'] = dict(cubos=maos_cubos, quadros=maos_quadros, olhos=maos_olhos, pintor=maos_pintor, rosto=None, escala=1.0, altura_pe=16, rosto_cubo=None)


# ====================================================================== DUPLO (o imitador: parece gente, nao e)

def duplo_cubos():
    """Corpo de jogador (mesmas proporcoes) com tudo um pouco errado: bracos ate o joelho, pescoco a mostra,
    cabeca de cera com o rosto costurado e uma mandibula que abre."""
    cs = []
    cs.append(Cubo('perna_d', (-4, -16, -2), (0, -4, 2), 'calca', grupo='perna_d'))
    cs.append(Cubo('perna_e', (0, -16, -2), (4, -4, 2), 'calca', grupo='perna_e'))
    cs.append(Cubo('tronco', (-4, -4, -2), (4, 8, 2), 'camisa', grupo='tronco'))
    cs.append(Cubo('braco_d', (-8, -9, -2), (-4, 8, 2), 'braco', grupo='braco_d'))
    cs.append(Cubo('braco_e', (4, -9, -2), (8, 8, 2), 'braco', grupo='braco_e'))
    cs.append(Cubo('pescoco', (-1.6, 8, -1.6), (1.6, 10.6, 1.6), 'pele', grupo='cabeca'))
    cs.append(Cubo('cabeca', (-4, 10.5, -4), (4, 18.5, 4), 'rosto', grupo='cabeca'))
    cs.append(Cubo('queixo', (-3.6, 10.2, -2.5), (3.6, 13.1, 4.35), 'queixo', grupo='queixo'))
    cs.append(Cubo('cabelo', (-4.5, 13.8, -4.5), (4.5, 19, 4.5), 'cabelo', grupo='cabeca'))
    return cs


def duplo_quadros(cs):
    def tilt(lista, ang):
        return [girado(c, 'z', ang, (0, 10.5, 0)) if c.grupo in ('cabeca', 'queixo') else c for c in lista]

    def grito():
        out = []
        for c in cs:
            if c.nome == 'cabeca':
                out.append(c.com(pele='rosto_grito'))
            elif c.nome == 'queixo':
                out.append(girado(c, 'x', 45, (0, 13.1, -2.5)))   # mandibula cai (dobradica atras)
            elif c.grupo in ('braco_d', 'braco_e'):
                out.append(girado(c, 'x', -45, (0, 6, 0)))        # bracos levantam para a frente
            else:
                out.append(c)
        return out
    # idle: parado, cabeca um nadinha torta (errado o bastante para incomodar); walk: a cabeca treme de um lado para o outro
    return {'idle': tilt(cs, 0.0), 'walk1': tilt(cs, 22.5), 'walk2': tilt(cs, -22.5), 'grito': grito()}


def duplo_olhos(cs):
    o = [Cubo('olho_d', (-2.7, 15.0, 4.0), (-1.1, 16.2, 4.6), 'olho', so=('south', 'up', 'down', 'east', 'west')),
         Cubo('olho_e', (1.1, 15.0, 4.0), (2.7, 16.2, 4.6), 'olho', so=('south', 'up', 'down', 'east', 'west'))]
    return {'idle': o, 'grito': [c.com(pele='olho_grito') for c in o]}


def duplo_pintor(P):
    x, y, z, s, t = P['x'], P['y'], P['z'], P['s'], P['t']
    pele, face, nome = P['pele'], P['face'], P['nome']
    if pele == 'olho':
        # ponto branco pequeno no meio de um olho escuro (brilha de longe: "tem alguem ali")
        d = elipse(s, t, 0.5, 0.5, 0.5, 0.5)
        c = rampa(1 - np.clip(d, 0, 1), [(0, '#0a0a10'), (0.55, '#0a0a10'), (0.7, '#f4f1e6'), (1, '#ffffff')])
        return c, np.where(d < 1, 1.0, 0.0)
    if pele == 'olho_grito':
        return olhos_brilho(P, '#ff2a1a', '#fff0d0')
    if pele == 'cabelo':
        # cabelo curto, escuro, despenteado: franja falhada na frente, pontas irregulares embaixo
        col = np.floor(s * 18).astype(np.int64)
        base_h = hash2(col, 0, 71)
        fim = 14.0 + 1.6 * hash2(col, 1, 72)
        a = np.where(y > fim, 1.0, 0.0)
        if face == 'south':
            a = a * np.where((hash2(col, 2, 73) < 0.55) | (y > 17.4), 1.0, 0.0)
        if face == 'down':
            a = np.zeros_like(a)
        if face == 'up':
            a = np.where(hash2(np.floor(s * 10), np.floor(t * 10), 74) < 0.96, 1.0, 0.0)
        brilho = 0.6 + 0.6 * ridged(s * 26, y * 0.4, 0, 2, 1, 75)
        c = C('#15110f') * brilho[..., None]
        return c, a
    if pele in ('calca', 'camisa'):
        # roupa de gente comum, so que desbotada, suja de terra e com manchas escuras que escorrem
        if pele == 'camisa':
            c = rampa(fbm(x * 0.9, y * 0.9, z * 0.9, 3, 1, 76), [(0, '#15302f'), (0.5, '#24504c'), (1, '#2f6560')])
            gola = ss(6.2, 7.2, y) * (1 - ss(0.1, 0.25, np.abs(s - 0.5))) * (1.0 if face == 'south' else 0.0)
            c = mix(c, C('#c9c0ae'), gola * 0.9)
        else:
            c = rampa(fbm(x * 0.9, y * 0.9, z * 0.9, 3, 1, 77), [(0, '#1b2030'), (0.5, '#2b3348'), (1, '#39445c')])
        dobra = ss(0.72, 0.95, ridged(x * 0.6, y * 0.2, z * 0.6, 3, 0.9, 78))
        c = c * (1 - 0.3 * dobra)[..., None]
        sujo = ss(0.55, 0.85, fbm(x * 0.5, y * 0.4, z * 0.5, 3, 1, 79))
        c = mix(c, C('#3a2d1e'), sujo * 0.5)
        esc = ss(0.86, 0.95, ridged(x * 2.4, y * 0.07, z * 2.4, 2, 1, 80)) * ss(-4, 6, y)
        c = mix(c, C('#0c0608'), esc * 0.8)
        a = np.ones(x.shape)
        if pele == 'calca' and face != 'up':
            corte = -16 + 2.5 * (0.3 + 0.7 * vnoise(x * 1.3, 0, z * 1.3, 81))
            a = np.where(y < corte, 0.0, 1.0)
        return c * luz(P, 0.3) * borda(P, 0.25), a
    # pele de cera: palida, amarelada, lisa demais, com veias finas escuras e manchas roxas nas juntas
    n = fbm(x, y, z, 4, 0.5, 82)
    c = rampa(n, [(0, '#8a7f70'), (0.4, '#c9bca7'), (0.75, '#e4d9c6'), (1, '#f1e9da')])
    veia = ss(0.66, 0.92, ridged(x * 1.1, y * 0.5, z * 1.1, 3, 0.8, 83))
    c = mix(c, C('#3a3046'), veia * 0.4)
    c = pele_detalhe(P, c, 84, 0.6)
    if pele == 'braco':
        # manga da camisa ate o cotovelo; antebraco e mao de cera; dedos escurecidos
        manga = ss(0.5, 1.5, y)
        cam = rampa(fbm(x * 0.9, y * 0.9, z * 0.9, 3, 1, 76), [(0, '#15302f'), (0.5, '#24504c'), (1, '#2f6560')])
        c = mix(c, cam, manga)
        c = mix(c, C('#2a1d22'), ss(-5, -9, y) * 0.8)
        junta = ss(-7.5, -8.2, y) * (1 - ss(-8.2, -9, y)) * 0
        c = c * (1 - junta)[..., None]
        return c * luz(P) * borda(P, 0.25), 1
    if pele == 'queixo':
        if face == 'south':
            # queixo: pele de cera com o labio de baixo e a costura do rosto seguindo pelos lados
            labio = ss(0.0, 0.08, t) * (1 - ss(0.08, 0.3, t))
            c = mix(c, C('#7a3f44'), labio * 0.8)
            cost = (s < 0.07) | (s > 0.93)
            c = np.where(cost[..., None], C('#3a1c1c') * np.ones_like(c), c)
            for k in range(3):
                ty = 0.25 + k * 0.28
                xx = ((np.abs(s - 0.035) < 0.05) | (np.abs(s - 0.965) < 0.05)) & (np.abs(t - ty) < 0.035)
                c = np.where(xx[..., None], C('#d6c9a6') * np.ones_like(c), c)
            c = mix(c, sangue(P, 7), 0.8 * escorrido(s, t, [(0.32, 0.0, 0.5), (0.7, 0.0, 0.35)], 7, 0.02).clip(0, 1))
            return c * luz(P, 0.15) * borda(P, 0.15), 1
        if face == 'up':
            # dentro da boca (so aparece quando a mandibula abre): lingua escura e dentes de baixo na frente
            d = elipse(s, t, 0.5, 0.45, 0.42, 0.42)
            c = boca_fundo(1 - d * 0.6)
            lingua = elipse(s, t, 0.5, 0.5, 0.26, 0.34)
            c = mix(c, rampa(fbm(s * 8, t * 8, 0, 2, 1, 85), [(0, '#3a0d12'), (1, '#6b1b22')]), dentro(lingua, 0.2))
            m, p, _ = dentes(s, 1 - t, 0.3, 0.1, afiado=1.1)
            m &= (s > 0.1) & (s < 0.9)
            c = np.where(m[..., None], dente_cor(p, '#e6dcc4'), c)
            return c, 1
        return c * luz(P) * borda(P, 0.25), 1
    if pele in ('rosto', 'rosto_grito') and face == 'south':
        grito = pele == 'rosto_grito'
        glifo = nome == 'glifo'
        # a boca (parte de baixo do rosto) fica atras do queixo quando fechada; no glifo o rosto inteiro aparece
        boca_t = 0.69
        c = np.clip(c * 1.08, 0, 1)
        # costura em volta do rosto inteiro: a cara e uma mascara de pele pregada na cabeca
        borda_m = (s < 0.06) | (s > 0.94) | (t < 0.05)
        c = np.where(borda_m[..., None], mix(c, C('#3a1c1c'), 0.85), c)
        for k in range(9):
            ty = 0.08 + k * 0.1
            xx = ((np.abs(s - 0.03) < 0.045) | (np.abs(s - 0.97) < 0.045)) & (np.abs(t - ty) < 0.02)
            c = np.where(xx[..., None], C('#d6c9a6') * np.ones_like(c), c)
        for k in range(8):
            sx = 0.12 + k * 0.11
            xx = (np.abs(s - sx) < 0.02) & (t < 0.09)
            c = np.where(xx[..., None], C('#d6c9a6') * np.ones_like(c), c)
        # olhos: grandes demais, arregalados, iris minuscula; no grito viram buracos pretos sangrando
        for cx in (0.3, 0.7):
            d = elipse(s, t, cx, 0.37, 0.14, 0.16)
            c = c * (1 - 0.4 * dentro(d / 1.5, 0.5))[..., None]
            if not grito:
                c = mix(c, C('#f3f1ea'), dentro(d, 0.06))
                iris = elipse(s, t, cx + (0.03 if cx < 0.5 else -0.01), 0.39, 0.045, 0.05)
                c = mix(c, C('#2b2a30'), dentro(iris, 0.3))
                c = mix(c, C('#050508'), dentro(iris / 0.5, 0.3))
                anel = (dentro(d / 1.1, 0.06) - dentro(d, 0.06)).clip(0, 1)
                c = mix(c, C('#7a1a24'), anel * 0.7)
            else:
                c = mix(c, C('#050308'), dentro(d, 0.05))
                c = mix(c, sangue(P, 8), 0.9 * escorrido(s, t, [(cx - 0.05, 0.5, 0.3), (cx + 0.04, 0.5, 0.22)], 8).clip(0, 1))
        for cx in (0.465, 0.535):
            nd = elipse(s, t, cx, 0.6, 0.017, 0.03)
            c = mix(c, C('#2a1c1c'), dentro(nd, 0.3))
        if not grito:
            # sorriso largo demais, de orelha a orelha, dentes de gente certinhos (o que da o incomodo)
            yb = 0.78 - 0.22 * (s - 0.5) ** 2 * 4
            lin = np.abs(t - yb) < 0.012
            c = np.where(lin[..., None], C('#2a0c0e') * np.ones_like(c), c)
            dent = (t > yb - 0.045) & (t < yb) & (s > 0.2) & (s < 0.8)
            col = np.floor(s * 14)
            sep = np.abs(s * 14 - col - 0.5) > 0.42
            c = np.where((dent & ~sep)[..., None], C('#e9e1cc') * np.ones_like(c), c)
            c = np.where((dent & sep)[..., None], C('#8a7a60') * np.ones_like(c), c)
        else:
            # boca escancarada ate o queixo: por tras do queixo caido, fundo preto e dentes de cima
            d = elipse(s, t, 0.5, 0.95, 0.3, 0.32)
            c = c * (1 - 0.5 * dentro(d / 1.2, 0.3))[..., None]
            c = mix(c, boca_fundo(1 - d), dentro(d, 0.04))
            ang = np.arctan2((t - 0.95) / 0.32, (s - 0.5) / 0.3)
            m, p, _ = dentes(ang, 1 - d, 0.28, 0.33, afiado=1.3)
            m &= (d < 1) & (t < 0.95)
            c = np.where(m[..., None], dente_cor(p, '#e0d6bd'), c)
            if glifo:
                # no glifo: o queixo caido aparece embaixo (pele + labio) e a boca e um buraco
                q = (t > 0.93)
                c = np.where(q[..., None], mix(c, C('#7a3f44'), 0.6), c)
        return c * luz(P, 0.15) * borda(P, 0.12), 1
    if pele in ('rosto', 'rosto_grito'):
        return c * luz(P) * borda(P, 0.25), 1
    return c * luz(P) * borda(P, 0.25), 1


def duplo_rosto(P):
    return duplo_pintor(dict(P, pele='rosto_grito', face='south', nome='glifo'))


MONSTROS['duplo'] = dict(cubos=duplo_cubos, quadros=duplo_quadros, olhos=duplo_olhos, pintor=duplo_pintor, rosto=duplo_rosto,
                         escala=1.08, altura_pe=16, rosto_cubo=('cabeca', (-4, 10.5, -4), (4, 18.5, 4)))
