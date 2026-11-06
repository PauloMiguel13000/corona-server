"""Os desenhos de cada monstro. Cada funcao recebe P (coordenadas de uma face) e a camada ('base' ou 'over')."""
import numpy as np
from base import (C, ss, rampa, mix, elipse, dentro, dentes, fbm, ridged, vnoise, hash2, ao_borda, cor_cheia)

MONSTROS = {}


def monstro(nome, cabeca=False):
    def reg(f):
        MONSTROS[nome] = (f, cabeca)
        return f
    return reg


def luz_cima(P, forca=0.18):
    """Luz vinda de cima: o topo mais claro, o chao mais escuro."""
    y = P['y']
    v = 1 - forca + forca * np.clip(y / 32, 0, 1) + (0.08 if P['n'][1] > 0 else (-0.12 if P['n'][1] < 0 else 0))
    return v[..., None]


def dente_cor(m, prof, base='#efe6cf'):
    """Cor de dente com sombra perto da gengiva e ponta clara."""
    c = rampa(1 - np.clip(prof, 0, 1), [(0, '#6b5a40'), (0.35, '#c9b994'), (1, base)])
    return c


def boca_interior(dist, extra=None):
    """Interior escuro de boca: vermelho escuro na borda, preto no fundo."""
    return rampa(np.clip(dist, 0, 1), [(0, '#000000'), (0.55, '#1a0204'), (0.85, '#4a070b'), (1, '#6e1015')])


# ====================================================================== HOTEL

@monstro('estrondo', cabeca=True)
def estrondo(P, camada):
    """O Estrondo (Rush): rosto cinza de fumaca, olhos ocos enormes e sorriso largo."""
    x, y, z, s, t = P['x'], P['y'], P['z'], P['s'], P['t']
    sh = s.shape
    fumo = fbm(x * 0.9 + 3, y * 0.9, z * 0.9, 5, 0.45, 1)
    fumo2 = fbm(x * 0.6, y * 1.4 + 7, z * 0.6, 4, 0.6, 2)
    if camada == 'over':
        # fumaca em volta: densa nas laterais/topo, rala na frente (deixa o rosto aparecer)
        dens = ss(0.45, 0.8, fumo2 * 0.7 + fumo * 0.5)
        if P['face'] == 'front':
            cen = 1 - dentro(elipse(s, t, 0.5, 0.52, 0.5, 0.46), 0.35)
            dens = dens * cen * 1.1
        rgb = rampa(fumo, [(0, '#030303'), (0.6, '#151515'), (1, '#3a3a3a')])
        return rgb, np.clip(dens, 0, 0.92)
    base = rampa(fumo * 0.8 + fumo2 * 0.3, [(0, '#020202'), (0.45, '#0c0c0c'), (0.7, '#232323'), (1, '#474747')])
    if P['face'] != 'front':
        return base * ao_borda(P, 0.3), 1
    # rosto: placa cinza que some na fumaca
    rosto = rampa(fumo * 0.9 + 0.1, [(0, '#2e2e2e'), (0.45, '#5a5955'), (0.8, '#8a8780'), (1, '#a9a69d')])
    vin = dentro(elipse(s, t, 0.5, 0.5, 0.52, 0.5), 0.45)
    c = mix(base, rosto, vin)
    # rugas / fissuras de fumaca
    rach = ridged(x * 1.3, y * 1.3, z, 3, 0.8, 9)
    c = c * (1 - 0.45 * ss(0.35, 0.8, rach) * vin)[..., None]
    # olhos: ovais enormes inclinados (bravos), afundados
    for cx, ang in ((0.29, 0.35), (0.71, -0.35)):
        d = elipse(s, t, cx, 0.38, 0.185, 0.15, ang)
        sombra = 1 - 0.75 * dentro(d / 1.45, 0.4)
        c = c * sombra[..., None]
        oco = dentro(d, 0.06)
        fundo = rampa(d, [(0, '#000000'), (0.8, '#030303'), (1, '#141414')])
        c = mix(c, fundo, oco)
        # pupila: um pontinho branco frio que parece te seguir
        pd = elipse(s, t, cx + (0.02 if cx < 0.5 else -0.02), 0.42, 0.028, 0.034)
        brilho = dentro(pd, 0.25)
        halo = (1 - ss(0.0, 2.6, pd)) * 0.35
        c = mix(c, np.array([0.92, 0.94, 0.9]), np.clip(brilho + halo * oco, 0, 1))
    # sorriso enorme: cantos sobem
    q = (s - 0.5) / 0.36
    sup = 0.665 - 0.12 * q ** 2
    inf = 0.835 - 0.205 * q ** 2
    na_boca = (np.abs(q) <= 1) & (t >= sup) & (t <= inf)
    alt = np.maximum(inf - sup, 1e-3)
    prof = np.clip((t - sup) / alt, 0, 1)
    interior = rampa(1 - np.abs(prof - 0.5) * 2, [(0, '#1b0505'), (0.5, '#060101'), (1, '#000000')])
    c = np.where(na_boca[..., None], interior, c)
    lab = (np.abs(q) <= 1.06) & (np.abs(t - sup) < 0.018) | (np.abs(q) <= 1.06) & (np.abs(t - inf) < 0.018)
    c = np.where(lab[..., None], c * 0.25, c)
    # dentes de cima e de baixo
    m1, p1, _ = dentes(s, t - sup, 0.1 * (1 - 0.5 * q ** 2), 0.058)
    m2, p2, _ = dentes(s + 0.029, inf - t, 0.085 * (1 - 0.5 * q ** 2), 0.058)
    m1 &= na_boca
    m2 &= na_boca
    c = np.where(m1[..., None], dente_cor(m1, p1, '#d8d6cc'), c)
    c = np.where(m2[..., None], dente_cor(m2, p2, '#cfcdc3'), c)
    return c * ao_borda(P, 0.45, 0.12), 1


@monstro('retorno', cabeca=True)
def retorno(P, camada):
    """O Retorno (Ambush): rosto verde e podre, olhos verdes que brilham e uma boca escancarada."""
    x, y, z, s, t = P['x'], P['y'], P['z'], P['s'], P['t']
    n1 = fbm(x, y, z, 5, 0.5, 11)
    veia = ridged(x, y, z, 4, 0.55, 12)
    if camada == 'over':
        neb = fbm(x * 0.7, y * 1.3, z * 0.7, 4, 0.5, 14)
        dens = ss(0.55, 0.85, neb)
        if P['face'] == 'front':
            dens = dens * (1 - dentro(elipse(s, t, 0.5, 0.55, 0.5, 0.47), 0.3))
        rgb = rampa(neb, [(0, '#0a1a08'), (0.6, '#2f6b1d'), (1, '#8dff5a')])
        return rgb, np.clip(dens * 0.8, 0, 0.8)
    pele = rampa(n1, [(0, '#0d140b'), (0.35, '#26331f'), (0.6, '#4d5e3d'), (0.85, '#7d8c62'), (1, '#9fae80')])
    pele = pele * (1 - 0.55 * ss(0.3, 0.75, veia))[..., None]
    # rachaduras que brilham verde
    rach = ridged(x * 1.6 + 4, y * 1.6, z * 1.6, 3, 0.7, 13)
    pele = mix(pele, np.array([0.45, 1.0, 0.25]), ss(0.55, 0.85, rach) * 0.9)
    if P['face'] != 'front':
        return pele * ao_borda(P, 0.35), 1
    c = pele
    for cx in (0.3, 0.7):
        d = elipse(s, t, cx, 0.33, 0.135, 0.115, 0.25 if cx < 0.5 else -0.25)
        c = c * (1 - 0.8 * dentro(d / 1.5, 0.45))[..., None]
        c = mix(c, rampa(d, [(0, '#000000'), (1, '#0b0f08')]), dentro(d, 0.07))
        pd = elipse(s, t, cx, 0.34, 0.05, 0.05)
        c = mix(c, rampa(pd, [(0, '#f4ffe0'), (0.45, '#9dff57'), (1, '#2d7a12')]), dentro(pd, 0.25))
        glow = (1 - ss(0.9, 2.4, pd)) * 0.6 * dentro(d, 0.1)
        c = c + np.array([0.2, 0.55, 0.1]) * glow[..., None]
    # boca escancarada
    d = elipse(s, t, 0.5, 0.735, 0.31, 0.215)
    dentro_b = dentro(d, 0.03)
    c = mix(c, boca_interior(1 - d), dentro_b)
    c = c * (1 - 0.6 * (dentro(d / 1.18, 0.2) - dentro_b))[..., None]
    ang = np.arctan2((t - 0.735) / 0.215, (s - 0.5) / 0.31)
    m, p, _ = dentes(ang, 1 - d, 0.42, 0.33)
    m &= d < 1
    c = np.where(m[..., None], dente_cor(m, p, '#d9d3a8'), c)
    return c * ao_borda(P, 0.4, 0.12), 1


@monstro('olhos', cabeca=True)
def olhos(P, camada):
    """Os Olhos: uma massa de carne roxa coberta de olhos que brilham."""
    x, y, z, s, t = P['x'], P['y'], P['z'], P['s'], P['t']
    face = P['face']
    sem = {'front': 1, 'back': 2, 'left': 3, 'right': 4, 'top': 5, 'bottom': 6}[face]
    carne = rampa(fbm(x, y, z, 5, 0.55, 21), [(0, '#08010b'), (0.4, '#1d0624'), (0.7, '#3a0e40'), (1, '#5e1a5c')])
    carne = carne * (1 - 0.5 * ss(0.3, 0.8, ridged(x, y, z, 3, 0.6, 22)))[..., None]
    # lista de olhos desta face
    lista = []
    if face == 'front':
        lista.append((0.5, 0.5, 0.2))
    g = 3
    for a in range(g):
        for b in range(g):
            if face == 'front' and a == 1 and b == 1:
                continue
            h1, h2, h3 = hash2(a + sem * 5, b, 1), hash2(a + sem * 5, b, 2), hash2(a + sem * 5, b, 3)
            lista.append(((a + 0.25 + 0.5 * h1) / g, (b + 0.25 + 0.5 * h2) / g, 0.075 + 0.06 * h3))
    rgb = carne.copy()
    alfa = np.zeros(s.shape)
    for k, (cx, cy, r) in enumerate(lista):
        d = np.sqrt((s - cx) ** 2 + (t - cy) ** 2) / r
        if camada == 'over':
            halo = (ss(1.05, 1.25, d) - ss(1.3, 2.2, d))
            rgb = mix(rgb, np.array([0.75, 0.3, 1.0]), np.clip(halo, 0, 1))
            alfa = np.maximum(alfa, halo * 0.55)
            continue
        palpebra = dentro(d / 1.28, 0.12)
        rgb = mix(rgb, np.array([0.05, 0.0, 0.06]), palpebra)
        escl = dentro(d, 0.1)
        vasos = ss(0.5, 0.9, ridged(x * 3 + k, y * 3, z * 3, 2, 0.9, 30 + k)) * ss(0.35, 0.95, d)
        bran = mix(np.array([0.93, 0.89, 0.86]) * (1 - 0.35 * d ** 2)[..., None], np.array([0.75, 0.08, 0.1]), vasos * 0.85)
        rgb = mix(rgb, bran, escl)
        ang = hash2(k, sem, 9) * 6.28
        ix, iy = cx + np.cos(ang) * r * 0.15, cy + np.sin(ang) * r * 0.15
        di = np.sqrt((s - ix) ** 2 + (t - iy) ** 2) / (r * 0.6)
        iris = rampa(di, [(0, '#ffffff'), (0.25, '#e6b8ff'), (0.6, '#a23cff'), (0.95, '#3c0870'), (1, '#140220')])
        rgb = mix(rgb, iris, dentro(di, 0.08) * escl)
        pu = elipse(s, t, ix, iy, r * 0.12, r * 0.42)
        rgb = mix(rgb, np.array([0.0, 0.0, 0.0]), dentro(pu, 0.15) * escl)
        br = np.sqrt((s - (ix - r * 0.25)) ** 2 + (t - (iy - r * 0.28)) ** 2) / (r * 0.12)
        rgb = mix(rgb, np.array([1.0, 1.0, 1.0]), dentro(br, 0.3) * escl)
    if camada == 'over':
        return rgb, alfa
    return rgb * ao_borda(P, 0.3), 1


@monstro('grito', cabeca=True)
def grito(P, camada):
    """O Sussurro (Screech): bolha de tinta preta, olhinhos brancos e uma bocarra cheia de agulhas."""
    x, y, z, s, t = P['x'], P['y'], P['z'], P['s'], P['t']
    tinta = fbm(x * 1.4, y * 0.35, z * 1.4, 4, 0.8, 41)
    if camada == 'over':
        fio = vnoise(x * 2.2 + z * 2.2, 0, 0, 42)
        dens = ss(0.6, 0.8, fio) * ss(0.25, 0.9, t if P['face'] not in ('top', 'bottom') else 0 * t + 0.4)
        if P['face'] == 'front':
            dens = dens * (1 - dentro(elipse(s, t, 0.5, 0.6, 0.48, 0.5), 0.2))
        rgb = rampa(tinta, [(0, '#000000'), (1, '#1c1f26')])
        return rgb, dens * 0.95
    base = rampa(tinta, [(0, '#000000'), (0.55, '#07080a'), (0.85, '#171a21'), (1, '#2b303b')])
    if P['face'] != 'front':
        return base * ao_borda(P, 0.3), 1
    c = base
    for cx in (0.33, 0.67):
        d = elipse(s, t, cx, 0.2, 0.07, 0.055, 0.3 if cx < 0.5 else -0.3)
        c = mix(c, np.array([0.97, 0.97, 0.94]), dentro(d, 0.2))
        glow = (1 - ss(0.8, 2.6, d)) * 0.5 * (1 - dentro(d, 0.2))
        c = c + np.array([0.35, 0.35, 0.33]) * glow[..., None]
        pd = elipse(s, t, cx, 0.205, 0.016, 0.022)
        c = mix(c, np.array([0, 0, 0]), dentro(pd, 0.3))
    d = elipse(s, t, 0.5, 0.63, 0.4, 0.33)
    gengiva = dentro(d, 0.03)
    c = mix(c, rampa(d, [(0, '#000000'), (0.45, '#14010a'), (0.75, '#4a0616'), (0.92, '#8c1826'), (1, '#b03040')]), gengiva)
    ang = np.arctan2((t - 0.63) / 0.33, (s - 0.5) / 0.4)
    for anel, comp, per, fase in ((1.0, 0.36, 0.22, 0), (0.78, 0.32, 0.2, 0.5), (0.58, 0.26, 0.19, 0.25)):
        m, p, _ = dentes(ang, anel - d, comp, per, fase=fase, afiado=3.0)
        m &= d < anel
        c = np.where(m[..., None], dente_cor(m, p, '#f2efe6'), c)
    return c * ao_borda(P, 0.35, 0.12), 1


def musculo(P, sem, escuro='#250304', medio='#7d1812', claro='#c24a3b'):
    """Carne viva: manchas, fibras onduladas (dominio torcido), sulcos escuros e tendoes claros."""
    x, y, z = P['x'], P['y'], P['z']
    warp = fbm(x * 0.35, y * 0.35, z * 0.35, 3, 1, sem + 3) * 7
    mancha = fbm(x, y, z, 4, 0.35, sem)
    if P['parte'] == 'body':
        fib = 0.5 + 0.5 * np.sin((x * 1.7 + y * 0.6 + warp) * 2.4)
    elif P['parte'] == 'head':
        fib = 0.5 + 0.5 * np.sin((x * 1.2 + y * 1.4 + z + warp) * 2.2)
    else:
        fib = 0.5 + 0.5 * np.sin(((x + z) * 2.2 + y * 0.25 + warp) * 2.2)
    det = fbm(x, y, z, 3, 1.6, sem + 4)
    patch = ss(0.45, 0.65, fbm(x * 0.5, y * 0.5, z * 0.5, 2, 1, sem + 7))
    v = 0.6 * mancha + 0.14 * fib * patch + 0.26 * det + 0.05
    c = rampa(v, [(0, escuro), (0.35, '#4f0b0a'), (0.55, medio), (0.78, claro), (1, '#e48c78')])
    sulco = ss(0.45, 0.9, ridged(x * 0.6 + warp * 0.2, y * 0.3, z * 0.6, 3, 0.8, sem + 2))
    c = c * (1 - 0.6 * sulco)[..., None]
    tendao = ss(0.82, 0.9, ridged(x * 0.4, y * 0.12, z * 0.4, 2, 1.0, sem + 5)) * ss(0.4, 0.7, mancha)
    c = mix(c, C('#d9a68e'), tendao * 0.6)
    brilho = ss(0.8, 0.9, fbm(x * 2.5, y * 2.5, z * 2.5, 2, 1, sem + 6))
    return c + (brilho * 0.25)[..., None] * np.array([1, 0.8, 0.75])


def respingos(P, lista, sem=0):
    """Sangue em 3D: lista de (x, y, z, raio). Bordas irregulares e escorridos para baixo."""
    x, y, z = P['x'], P['y'], P['z']
    m = np.zeros(x.shape)
    rug = fbm(x * 2, y * 2, z * 2, 3, 1, 90 + sem) - 0.5
    for k, (cx, cy, cz, r) in enumerate(lista):
        d = np.sqrt((x - cx) ** 2 + ((y - cy) * 0.9) ** 2 + (z - cz) ** 2) / r + rug * 0.9
        m = np.maximum(m, 1 - ss(0.85, 1.0, d))
        # escorridos: colunas abaixo do respingo
        col = np.floor((x + z) * 4).astype(np.int64)
        comp = r * (0.8 + 2.5 * hash2(col, k, 91 + sem))
        esc = (hash2(col, k, 92 + sem) > 0.55) & (y < cy) & (y > cy - comp) & (np.abs(x - cx) + np.abs(z - cz) < r * 0.9)
        m = np.maximum(m, esc * 1.0)
    return m


def sangue_cor(P, sem=0):
    x, y, z = P['x'], P['y'], P['z']
    return rampa(fbm(x * 3, y * 3, z * 3, 2, 1, 95 + sem), [(0, '#1e0000'), (0.6, '#5a0606'), (1, '#8e1414')])


@monstro('figura')
def figura(P, camada):
    """A Figura (Figure): carne viva, sem olhos, a cabeca e uma boca vertical de dentes, costelas abertas."""
    x, y, z, s, t = P['x'], P['y'], P['z'], P['s'], P['t']
    parte, face = P['parte'], P['face']
    c = musculo(P, 51) * luz_cima(P)
    if camada == 'over':
        rgb = sangue_cor(P, 1)
        a = np.zeros(s.shape)
        if parte == 'head' and face == 'front':
            # dentes da frente saltando para fora da boca
            larg = 0.36 * np.sin(np.pi * np.clip((t - 0.06) / 0.9, 0, 1)) ** 0.7
            q = np.abs(s - 0.5)
            m, p, _ = dentes(t + 0.03, larg - q + 0.02, 0.1, 0.11, afiado=2.0)
            m &= (q < larg + 0.02) & (t > 0.12) & (t < 0.92)
            rgb = np.where(m[..., None], dente_cor(m, p, '#f3e8cc'), rgb)
            a = np.where(m, 1.0, a)
        if parte in ('body',) and face == 'front':
            baba = respingos(P, [(0, 23.5, 2, 1.2)], 3) * (P['y'] > 17)
            a = np.maximum(a, baba * 0.95)
        return rgb, a
    if parte == 'head':
        dobra = ss(0.45, 0.9, ridged(x * 1.4, y * 1.4, z * 1.4, 3, 0.6, 52))
        c = c * (1 - 0.45 * dobra)[..., None]
        if face == 'front':
            larg = 0.36 * np.sin(np.pi * np.clip((t - 0.06) / 0.9, 0, 1)) ** 0.7
            q = np.abs(s - 0.5)
            na = q < larg
            prof = np.clip(q / np.maximum(larg, 1e-3), 0, 1)
            c = np.where(na[..., None], boca_interior(prof), c)
            labio = (q > larg - 0.005) & (q < larg + 0.05) & (larg > 0.01)
            c = np.where(labio[..., None], c * 0.45 + np.array([0.12, 0.0, 0.0]), c)
            m, p, _ = dentes(t, larg - q, 0.14, 0.07, afiado=2.2)
            m &= na
            c = np.where(m[..., None], dente_cor(m, p, '#efe2c4'), c)
        elif face == 'top':
            fenda = (np.abs(s - 0.5) < 0.08 * np.sin(np.pi * t)) & (t > 0.5)
            c = np.where(fenda[..., None], boca_interior(np.abs(s - 0.5) / 0.08), c)
        return c * ao_borda(P, 0.3), 1
    if parte == 'body' and face == 'front':
        reg = elipse(s, t, 0.5, 0.36, 0.34, 0.31)
        cav = dentro(reg, 0.06)
        pulmao = rampa(fbm(x * 2, y * 2, z, 3, 1, 53) * (1 - reg * 0.6), [(0, '#000000'), (0.5, '#16000a'), (1, '#4a0a1e')])
        c = mix(c, pulmao, cav)
        for k, ty in enumerate((0.14, 0.235, 0.33, 0.425, 0.52)):
            q = (s - 0.5) / 0.34
            curva = ty + 0.09 * np.abs(q) ** 1.5 - 0.02 * k * np.abs(q)
            esp = 0.026 * (1 - 0.4 * np.abs(q))
            osso = (np.abs(t - curva) < esp) & (reg < 1.08) & (np.abs(s - 0.5) > 0.045)
            sh = 1 - np.abs(t - curva) / np.maximum(esp, 1e-3)
            cor = rampa(sh, [(0, '#6e5a44'), (0.6, '#cbb894'), (1, '#efe3c6')])
            c = np.where(osso[..., None], cor * (1 - 0.35 * ss(0.5, 1, np.abs(q)))[..., None], c)
        esterno = (np.abs(s - 0.5) < 0.05) & (t > 0.07) & (t < 0.6)
        c = np.where(esterno[..., None], rampa(1 - np.abs(s - 0.5) / 0.05, [(0, '#7a6650'), (1, '#e3d4b2')]), c)
        ang = np.arctan2((t - 0.36) / 0.31, (s - 0.5) / 0.34)
        m, p, _ = dentes(ang, 1 - reg, 0.2, 0.26, afiado=2.5)
        m &= reg < 1
        c = np.where(m[..., None], dente_cor(m, p, '#e9dcbd'), c)
    if parte == 'body' and face == 'back':
        for k in range(10):
            vy = 0.05 + k * 0.095
            v = elipse(s, t, 0.5, vy, 0.075, 0.034)
            c = mix(c, rampa(1 - np.clip(v, 0, 1), [(0, '#5c4632'), (1, '#e0cfa8')]), dentro(v, 0.25))
    if parte in ('rarm', 'larm'):
        mao = ss(0.7, 0.8, t) if face != 'top' else 0 * t
        escura = rampa(fbm(x * 2, y * 2, z * 2, 3, 1, 59), [(0, '#0d0101'), (1, '#40100b')])
        c = mix(c, escura, mao)
        if face in ('front', 'back', 'left', 'right'):
            dedo = (np.abs(((s * 4) % 1) - 0.5) > 0.42) & (t > 0.8)
            c = np.where(dedo[..., None], c * 0.25, c)
        if face == 'bottom':
            unha = dentro(elipse((s * 2) % 1, (t * 2) % 1, 0.5, 0.5, 0.3, 0.3), 0.2)
            c = mix(c, C('#d8cbb0'), unha)
    if parte in ('rleg', 'lleg'):
        c = c * (0.9 - 0.3 * ss(0.65, 1, t))[..., None]
    return c * ao_borda(P, 0.25), 1


@monstro('perseguidor')
def perseguidor(P, camada):
    """O Perseguidor (Seek): gosma preta e brilhante, um olho enorme e olhinhos pelo corpo."""
    x, y, z, s, t = P['x'], P['y'], P['z'], P['s'], P['t']
    parte, face = P['parte'], P['face']
    g = fbm(x * 0.7, y * 0.18, z * 0.7, 4, 0.7, 61)
    brilho = ss(0.6, 0.78, g) + 0.5 * ss(0.7, 0.9, fbm(x * 2, y * 0.5, z * 2, 3, 0.8, 62))
    c = np.broadcast_to(np.array([0.012, 0.012, 0.016]), s.shape + (3,)).copy()
    c = c + (np.array([0.2, 0.22, 0.27]) * brilho[..., None] * 0.9)
    if camada == 'over':
        if face in ('top', 'bottom'):
            m = ss(0.55, 0.7, fbm(x * 2, y, z * 2, 3, 0.8, 63))
            return c, m * 0.95
        col = np.floor(s * (P['w'] if face in ('front', 'back') else P['d']) * 4)
        semc = {'front': 1, 'back': 2, 'left': 3, 'right': 4}[face] + {'head': 0, 'body': 10, 'rarm': 20, 'larm': 30, 'rleg': 40, 'lleg': 50}[parte]
        comp = 0.08 + 0.5 * hash2(col.astype(np.int64), semc, 64) ** 2
        tem = hash2(col.astype(np.int64), semc, 65) > 0.45
        # pingos descendo do alto de cada parte + gota na ponta
        ponta = np.abs(t - comp) < 0.03
        m = tem & (t < comp)
        a = np.where(m | (ponta & tem), 1.0, 0.0)
        if parte == 'head' and face == 'front':
            a = a * (1 - dentro(elipse(s, t, 0.5, 0.48, 0.34, 0.28), 0.1))
        return c * 1.3, a
    def olho(c, cx, cy, rx, ry, k):
        d = elipse(s, t, cx, cy, rx, ry)
        c = c * (1 - 0.7 * dentro(d / 1.35, 0.3))[..., None]
        esc = dentro(d, 0.08)
        vasos = ss(0.55, 0.95, ridged(x * 4 + k, y * 4, z * 4, 2, 0.9, 66 + k)) * ss(0.4, 1, d)
        bran = mix(np.array([0.96, 0.95, 0.92]) * (1 - 0.4 * d ** 3)[..., None], np.array([0.6, 0.05, 0.05]), vasos)
        c = mix(c, bran, esc)
        pd = elipse(s, t, cx, cy + ry * 0.05, rx * 0.36, ry * 0.46)
        c = mix(c, np.array([0.0, 0.0, 0.0]), dentro(pd, 0.08) * esc)
        hl = elipse(s, t, cx - rx * 0.18, cy - ry * 0.22, rx * 0.1, ry * 0.12)
        c = mix(c, np.array([1, 1, 1]), dentro(hl, 0.3) * esc)
        return c
    if parte == 'head' and face == 'front':
        c = olho(c, 0.5, 0.47, 0.3, 0.24, 0)
    else:
        semc = {'front': 1, 'back': 2, 'left': 3, 'right': 4, 'top': 5, 'bottom': 6}[face] * 7 + len(parte)
        for k in range(2):
            if k == 0 and hash2(semc, k, 67) > (0.72 if parte != 'head' else 0.3):
                cx, cy = 0.25 + 0.5 * hash2(semc, k, 68), 0.2 + 0.6 * hash2(semc, k, 69)
                r = 0.1 + 0.05 * hash2(semc, k, 70)
                c = olho(c, cx, cy, r, r * (P['w'] / P['h'] if face in ('front', 'back') else P['d'] / P['h']) * 0.9 if face not in ('top', 'bottom') else r, k)
    return c * ao_borda(P, 0.2), 1


# ====================================================================== BACKROOMS

@monstro('festeiro')
def festeiro(P, camada):
    """Festeiro (Partygoer): pele amarela lisa de balao, olhos pretos vazios, sorriso de orelha a orelha, maos de sangue."""
    x, y, z, s, t = P['x'], P['y'], P['z'], P['s'], P['t']
    parte, face = P['parte'], P['face']
    n = fbm(x, y, z, 3, 0.22, 71) * 0.75 + fbm(x, y, z, 2, 1.2, 76) * 0.25
    pele = rampa(n, [(0, '#9c6a00'), (0.35, '#d9a300'), (0.62, '#f5c81d'), (0.85, '#ffe15a'), (1, '#fff1a6')]) * luz_cima(P, 0.28)
    pele = pele * ao_borda(P, 0.35, 0.22)
    lista = [(-6, 10, 0, 3.2), (6, 10.5, 0, 3.0), (-1.2, 18.5, 2, 1.6), (1.8, 20.5, 2, 1.0), (0, 27.5, 4, 0.9)]
    sang = respingos(P, lista, 7)
    if parte in ('rarm', 'larm'):
        sang = np.maximum(sang, 1 - ss(13.0, 14.6, y + fbm(x * 2, y, z * 2, 3, 1, 77) * 3))
    if camada == 'over':
        rgb = sangue_cor(P, 2)
        a = np.zeros(s.shape)
        if parte == 'head' and face == 'front':
            col = np.floor(s * 32).astype(np.int64)
            comp = 0.8 + 0.18 * hash2(col, 5, 74)
            pinga = (hash2(col, 6, 75) > 0.5) & (t > 0.76) & (t < comp) & (np.abs(s - 0.5) < 0.4)
            a = np.where(pinga, 1.0, a)
        if parte in ('rarm', 'larm'):
            a = np.maximum(a, (1 - ss(12.0, 13.0, y + fbm(x * 2, y, z * 2, 3, 1, 78) * 2.5)) * (hash2(np.floor((x + z) * 4).astype(np.int64), 1, 79) > 0.35))
        return rgb, a
    c = mix(pele, sangue_cor(P, 2), sang * 0.95)
    if parte == 'head' and face == 'front':
        for cx in (0.31, 0.69):
            d = elipse(s, t, cx, 0.35, 0.1, 0.15, 0.12 if cx < 0.5 else -0.12)
            c = c * (1 - 0.55 * dentro(d / 1.6, 0.5))[..., None]
            c = mix(c, C('#000000'), dentro(d, 0.05))
            hl = elipse(s, t, cx - 0.025, 0.29, 0.016, 0.022)
            c = mix(c, C('#7a7a7a'), dentro(hl, 0.4) * 0.7)
        q = (s - 0.5) / 0.47
        sup = 0.585 - 0.11 * q ** 2
        inf = 0.79 - 0.15 * q ** 2
        na = (np.abs(q) <= 1) & (t >= sup) & (t <= inf)
        c = np.where(na[..., None], C('#050000'), c)
        meio = (sup + inf) / 2 + 0.004
        colk = np.floor((s - 0.03) / 0.047)
        fr = (s - 0.03) / 0.047 - colk
        dente = na & (fr > 0.1) & (fr < 0.93) & (np.abs(t - meio) > 0.01)
        meia = np.maximum((inf - sup) / 2, 1e-3)
        sh = np.clip(np.abs(t - meio) / meia, 0, 1)
        corden = rampa(sh, [(0, '#8d8676'), (0.35, '#e9e3d0'), (0.8, '#fbf8ee'), (1, '#b7ad95')])
        corden = corden * (1 - 0.35 * ss(0.6, 1, np.abs(q)))[..., None]
        c = np.where(dente[..., None], corden, c)
        cont = (np.abs(q) <= 1.04) & ((np.abs(t - sup) < 0.018) | (np.abs(t - inf) < 0.018))
        c = np.where(cont[..., None], C('#170800'), c)
        for cx in (0.03, 0.97):
            d = elipse(s, t, cx, 0.53, 0.035, 0.08)
            c = mix(c, c * 0.35, dentro(d, 0.4))
    return c, 1


@monstro('bacteria')
def bacteria(P, camada):
    """Bacteria: alto, preto como carvao, rachaduras vermelhas por dentro, olhos brancos rasgados e boca vertical."""
    x, y, z, s, t = P['x'], P['y'], P['z'], P['s'], P['t']
    parte, face = P['parte'], P['face']
    n = fbm(x * 0.8, y * 0.5, z * 0.8, 4, 0.6, 101)
    c = rampa(n, [(0, '#000000'), (0.5, '#060505'), (0.8, '#141011'), (1, '#262022')])
    rach = ridged(x * 0.9, y * 0.6, z * 0.9, 4, 0.6, 102)
    brasa = ss(0.5, 0.85, rach)
    c = mix(c, rampa(rach, [(0, '#2b0000'), (0.7, '#8a0e02'), (1, '#ff4a12')]), brasa * 0.85)
    # membros finos: as quinas ficam pretas como se o corpo fosse mais magro
    if parte != 'head' and face in ('front', 'back', 'left', 'right'):
        c = c * (1 - 0.9 * (1 - ss(0.0, 0.22, np.minimum(s, 1 - s))))[..., None]
    if camada == 'over':
        fio = ss(0.62, 0.8, fbm(x * 1.5, y * 0.3, z * 1.5, 3, 1, 103))
        return C('#000000') * np.ones(s.shape + (3,)), fio * 0.85 * (0 if face == 'front' and parte == 'head' else 1)
    if parte == 'head' and face == 'front':
        for cx, ang in ((0.3, 0.45), (0.7, -0.45)):
            d = elipse(s, t, cx, 0.36, 0.13, 0.035, ang)
            glow = (1 - ss(0.6, 3.0, d)) * 0.45
            c = c + C('#ffffff') * glow[..., None] * 0.6
            c = mix(c, C('#ffffff'), dentro(d, 0.25))
        d = elipse(s, t, 0.5, 0.72, 0.09, 0.24)
        c = mix(c, boca_interior(1 - d), dentro(d, 0.05))
        ang = np.arctan2((t - 0.72) / 0.24, (s - 0.5) / 0.09)
        m, p, _ = dentes(ang, 1 - d, 0.55, 0.42, afiado=2.5)
        m &= d < 1
        c = np.where(m[..., None], dente_cor(m, p, '#e8e4dc'), c)
    return c * ao_borda(P, 0.3), 1


@monstro('uivador')
def uivador(P, camada):
    """Uivador: pele palida e magra, ossos marcados, olhos pretos chorando sangue e um grito enorme."""
    x, y, z, s, t = P['x'], P['y'], P['z'], P['s'], P['t']
    parte, face = P['parte'], P['face']
    n = fbm(x, y, z, 4, 0.4, 111)
    c = rampa(n, [(0, '#6d6863'), (0.4, '#a29c94'), (0.7, '#cbc5bb'), (1, '#e6e1d7')]) * luz_cima(P, 0.25)
    hema = ss(0.6, 0.8, fbm(x * 0.7, y * 0.7, z * 0.7, 3, 0.8, 112))
    c = mix(c, C('#5b4060'), hema * 0.5)
    veia = ss(0.55, 0.85, ridged(x, y * 0.6, z, 3, 0.7, 113))
    c = mix(c, C('#4a5a78'), veia * 0.45)
    if parte == 'body' and face in ('front', 'back'):
        for k in range(6):
            ty = 0.1 + k * 0.085
            q = (s - 0.5) / 0.45
            curva = ty + 0.07 * q ** 2
            sulco = np.exp(-((t - curva) / 0.018) ** 2) * (np.abs(q) > 0.12) * (np.abs(q) < 1)
            c = c * (1 - 0.45 * sulco)[..., None]
            luz = np.exp(-((t - curva + 0.025) / 0.012) ** 2) * (np.abs(q) > 0.12) * (np.abs(q) < 1)
            c = c + (0.12 * luz)[..., None]
    sang = respingos(P, [(0, 26, 4, 0.8), (-6, 10, 0, 2.6), (6, 10, 0, 2.6)], 11)
    if parte in ('rarm', 'larm'):
        sang = np.maximum(sang, 1 - ss(12.5, 14, y + fbm(x * 2, y, z * 2, 3, 1, 114) * 3))
    c = mix(c, sangue_cor(P, 3), sang * 0.95)
    if camada == 'over':
        cabelo = (parte == 'head') & (face in ('top', 'back', 'left', 'right'))
        fio = ss(0.45, 0.6, fbm(x * 3, y * 0.4, z * 3, 3, 1, 115)) * (y > 27.5 + 2 * vnoise(x * 2, 0, z * 2, 116))
        return C('#0f0d0c') * np.ones(s.shape + (3,)), np.where(cabelo, fio, 0)
    if parte == 'head' and face == 'front':
        for cx in (0.31, 0.69):
            d = elipse(s, t, cx, 0.32, 0.105, 0.09)
            c = c * (1 - 0.6 * dentro(d / 1.7, 0.5))[..., None]
            c = mix(c, C('#000000'), dentro(d, 0.08))
            lag = (np.abs(s - cx - 0.01 * np.sin(t * 30)) < 0.022) & (t > 0.32) & (t < 0.95)
            c = np.where(lag[..., None], sangue_cor(P, 4) * 1.2, c)
        d = elipse(s, t, 0.5, 0.7, 0.17, 0.29)
        c = c * (1 - 0.5 * dentro(d / 1.25, 0.3))[..., None]
        c = mix(c, boca_interior(1 - d), dentro(d, 0.04))
        ang = np.arctan2((t - 0.7) / 0.29, (s - 0.5) / 0.17)
        m, p, _ = dentes(ang, 1 - d, 0.18, 0.32, afiado=1.8)
        m &= d < 1
        c = np.where(m[..., None], dente_cor(m, p, '#d8cfb4'), c)
    return c * ao_borda(P, 0.25), 1


@monstro('corredor')
def corredor(P, camada):
    """Corredor (Wendigo / ladrao de pele): couro escuro rasgado, costelas para fora, cranio de osso com olhos em brasa."""
    x, y, z, s, t = P['x'], P['y'], P['z'], P['s'], P['t']
    parte, face = P['parte'], P['face']
    n = fbm(x, y * 0.6, z, 4, 0.7, 121)
    pelo = fbm(x * 4, y * 0.8, z * 4, 2, 1, 122)
    c = rampa(n * 0.7 + pelo * 0.3, [(0, '#0b0806'), (0.4, '#2a211b'), (0.7, '#4a3b30'), (1, '#6e5a49')])
    rasgo = ss(0.66, 0.72, fbm(x * 0.9, y * 0.9, z * 0.9, 4, 0.5, 123))
    carne = musculo(P, 124, '#1c0202', '#5e100c', '#9a3326')
    c = mix(c, carne, rasgo)
    if parte == 'head':
        osso_n = fbm(x * 1.2, y * 1.2, z * 1.2, 4, 0.6, 125)
        c = rampa(osso_n, [(0, '#6d6250'), (0.4, '#b2a585'), (0.75, '#ddd2b4'), (1, '#f1e9d3')])
        c = c * (1 - 0.6 * ss(0.5, 0.85, ridged(x * 1.3, y * 1.3, z * 1.3, 3, 0.6, 126)))[..., None]
        if face == 'front':
            for cx in (0.3, 0.7):
                d = elipse(s, t, cx, 0.38, 0.15, 0.16, 0.2 if cx < 0.5 else -0.2)
                c = c * (1 - 0.6 * dentro(d / 1.4, 0.4))[..., None]
                c = mix(c, C('#000000'), dentro(d, 0.08))
                pd = elipse(s, t, cx + (0.02 if cx < 0.5 else -0.02), 0.41, 0.03, 0.03)
                c = mix(c, C('#ff3012'), dentro(pd, 0.4))
                c = c + C('#ff2a00') * ((1 - ss(0.0, 3.0, pd)) * 0.35 * dentro(d, 0.1))[..., None]
            nariz = elipse(s, t, 0.5, 0.6, 0.06, 0.07)
            c = mix(c, C('#050302'), dentro(nariz, 0.2))
            boca = (t > 0.72) & (t < 0.94) & (np.abs(s - 0.5) < 0.36)
            dent = boca & (np.abs(((s - 0.14) / 0.048) % 1 - 0.5) < 0.38)
            c = np.where(boca[..., None], C('#070403'), c)
            sh = np.clip((t - 0.72) / 0.22, 0, 1)
            c = np.where(dent[..., None], rampa(1 - np.abs(sh - 0.5) * 2, [(0, '#5a4b33'), (1, '#e6d9b8')]), c)
        return c * ao_borda(P, 0.3), 1
    if camada == 'over':
        a = ss(0.62, 0.75, pelo) * (face != 'front' or parte != 'body')
        return c * 0.8, a * 0.9
    if parte == 'body' and face == 'front':
        for k in range(5):
            ty = 0.12 + k * 0.1
            q = (s - 0.5) / 0.42
            curva = ty + 0.08 * q ** 2
            osso = (np.abs(t - curva) < 0.03) & (np.abs(q) > 0.1) & (np.abs(q) < 1)
            sh = 1 - np.abs(t - curva) / 0.03
            c = np.where(osso[..., None], rampa(sh, [(0, '#5e4f3a'), (1, '#e5d8b6')]), np.where(((np.abs(t - curva) < 0.06) & (np.abs(q) < 1))[..., None], c * 0.3, c))
    if parte in ('rarm', 'larm', 'rleg', 'lleg') and face == 'bottom':
        unha = dentro(elipse((s * 2) % 1, (t * 2) % 1, 0.5, 0.5, 0.32, 0.32), 0.2)
        c = mix(c, C('#1a1612'), unha)
    return c * ao_borda(P, 0.25), 1


@monstro('cao')
def cao(P, camada):
    """Cao (Hound): pele palida e suja, cabelo preto comprido cobrindo o rosto, olhos pretos e boca rasgada."""
    x, y, z, s, t = P['x'], P['y'], P['z'], P['s'], P['t']
    parte, face = P['parte'], P['face']
    n = fbm(x, y, z, 4, 0.45, 131)
    c = rampa(n, [(0, '#4a4c50'), (0.4, '#7c8085'), (0.7, '#a7abaf'), (1, '#c9ccce')]) * luz_cima(P, 0.2)
    suj = ss(0.55, 0.8, fbm(x * 1.4, y * 1.4, z * 1.4, 4, 0.7, 132))
    c = mix(c, C('#2b241c'), suj * 0.6)
    if parte in ('rarm', 'larm', 'rleg', 'lleg'):
        c = mix(c, C('#1d1813'), (1 - ss(1.5, 5, y if parte in ('rleg', 'lleg') else y - 12)) * 0.8)
    if parte == 'body' and face in ('front', 'back'):
        for k in range(6):
            ty = 0.12 + k * 0.08
            q = (s - 0.5) / 0.45
            sulco = np.exp(-((t - ty - 0.06 * q ** 2) / 0.016) ** 2) * (np.abs(q) > 0.12) * (np.abs(q) < 1)
            c = c * (1 - 0.5 * sulco)[..., None]
    cab_n = fbm(x * 5 + z * 5, y * 0.25, 0, 2, 1, 133)
    if camada == 'over':
        rgb = rampa(cab_n, [(0, '#000000'), (0.7, '#0b0a0a'), (1, '#24211f')])
        if parte == 'head':
            if face == 'front':
                col = np.floor(s * 22).astype(np.int64)
                comp = 0.15 + 0.85 * hash2(col, 1, 134) ** 1.5
                a = (t < comp) & (hash2(col, 2, 135) > 0.25)
                a = a & ~((np.abs(s - 0.32) < 0.07) & (t > 0.28)) & ~((np.abs(s - 0.68) < 0.08) & (t > 0.3))
                return rgb, a * 1.0
            return rgb, ss(0.2, 0.3, cab_n) * 1.0
        if parte == 'body' and face == 'back':
            col = np.floor(s * 22).astype(np.int64)
            comp = 0.3 + 0.5 * hash2(col, 3, 136)
            return rgb, ((t < comp) & (hash2(col, 4, 137) > 0.3)) * 1.0
        return rgb, np.zeros(s.shape)
    if parte == 'head':
        if face != 'front':
            return rampa(cab_n, [(0, '#000000'), (1, '#1a1817')]) * np.ones(s.shape + (1,)), 1
        for cx in (0.32, 0.68):
            d = elipse(s, t, cx, 0.4, 0.09, 0.11)
            c = c * (1 - 0.55 * dentro(d / 1.6, 0.4))[..., None]
            c = mix(c, C('#000000'), dentro(d, 0.06))
            pd = elipse(s, t, cx, 0.41, 0.014, 0.014)
            c = mix(c, C('#ffffff'), dentro(pd, 0.4))
        q = (s - 0.5) / 0.44
        sup = 0.66 - 0.06 * q ** 2
        inf = 0.86 - 0.12 * q ** 2
        na = (np.abs(q) <= 1) & (t > sup) & (t < inf)
        c = np.where(na[..., None], boca_interior(1 - np.abs((t - (sup + inf) / 2) / np.maximum((inf - sup) / 2, 1e-3))), c)
        m1, p1, _ = dentes(s, t - sup, 0.075, 0.05, afiado=2.2)
        m2, p2, _ = dentes(s + 0.025, inf - t, 0.07, 0.05, afiado=2.2)
        c = np.where((m1 & na)[..., None], dente_cor(m1, p1, '#d6d0bc'), c)
        c = np.where((m2 & na)[..., None], dente_cor(m2, p2, '#cfc8b2'), c)
        c = np.where(((np.abs(q) <= 1.05) & ((np.abs(t - sup) < 0.012) | (np.abs(t - inf) < 0.012)))[..., None], C('#3a0a0a'), c)
        cab = (t < 0.18 + 0.12 * vnoise(s * 18, 0, 0, 138))
        c = np.where(cab[..., None], rampa(cab_n, [(0, '#000000'), (1, '#1a1817')]), c)
    return c * ao_borda(P, 0.25), 1
