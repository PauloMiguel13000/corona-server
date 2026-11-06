"""Reliquias das masmorras: espada 3D e conjunto de armadura por tema (59 temas), gerados por familia de golpe + cor do tema.

Espada: cubos no "espaco da espada" (lamina ao longo de +y, x centrado em 0, z em volta de 8), todos girados -45 em z na
origem (0,0,8) para ficarem na diagonal do item do Minecraft (cabo embaixo-esquerda, ponta em cima-direita), com os
"display" do item/handheld. Runas em cubo proprio com light_emission.
Armadura: texturas 64x32 da camada 1 (elmo, peitoral, botas) e camada 2 (calcas) para o equipment asset corona:<tema>.
Saida: python3 -I reliquias.py [ids...]
"""
import json, math, os, sys
import numpy as np
from PIL import Image
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'skins', 'monstros'))
from base import fbm, ridged, rampa, mix, C, ss, elipse, dentro, vnoise, hash2  # noqa: E402
import kit  # noqa: E402
from kit import Cubo  # noqa: E402

AQUI = os.path.dirname(os.path.abspath(__file__))
JAVA = os.path.normpath(os.path.join(AQUI, '..', 'coronapack', 'pacote', 'java', 'assets', 'corona'))
BEDROCK = os.path.normpath(os.path.join(AQUI, '..', 'coronapack', 'pacote', 'bedrock'))
PREV = '/tmp/claude-0/modelos/reliquias'
os.makedirs(PREV, exist_ok=True)
TEMAS = json.load(open(os.path.join(AQUI, 'temas_reliquias.json'), encoding='utf-8'))

DISPLAY = {   # item/handheld + item/generated do Minecraft
    'thirdperson_righthand': {'rotation': [0, -90, 55], 'translation': [0, 4.0, 0.5], 'scale': [0.85, 0.85, 0.85]},
    'thirdperson_lefthand': {'rotation': [0, 90, -55], 'translation': [0, 4.0, 0.5], 'scale': [0.85, 0.85, 0.85]},
    'firstperson_righthand': {'rotation': [0, -90, 25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
    'firstperson_lefthand': {'rotation': [0, 90, -25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
    'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
    'head': {'rotation': [0, 180, 0], 'translation': [0, 13, 7], 'scale': [1, 1, 1]},
    'gui': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]},
    'fixed': {'rotation': [0, 180, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]},
}

# familia de golpe -> estilo (material da lamina, forma)
ESTILO = {
    'OSSOS': 'osso', 'VENENO': 'veneno', 'ESMAGAR': 'pesada', 'MALDICAO': 'torta', 'CONGELAR': 'gelo', 'AREIA': 'khopesh',
    'CORRENTEZA': 'agua', 'ARCANO': 'cristal', 'INVESTIDA': 'larga', 'ILUSAO': 'fantasma', 'VENTANIA': 'vento', 'SANGRAR': 'sangue',
    'CHAMAS': 'lava', 'GANANCIA': 'ouro', 'MURCHAR': 'wither', 'ESTOURO': 'cutelo', 'VAZIO': 'vazio', 'SONICO': 'sculk', 'HEROBRINE': 'luz',
}


def cor_tema(t):
    return np.array(t['rgb'], float) / 255.0


def escurecer(c, f):
    return np.clip(np.asarray(c) * f, 0, 1)


def clarear(c, f):
    c = np.asarray(c)
    return np.clip(c + (1 - c) * f, 0, 1)


# ------------------------------------------------------------------ geometria da espada

def sw(nome, x0, y0, x1, y1, pele, esp=0.7, zc=8.0, emite=0, so=None):
    """Cubo no espaco da espada (x centrado, y ao longo da lamina), girado -45 em z na origem."""
    return Cubo(nome, (x0, y0, zc - esp / 2), (x1, y1, zc + esp / 2), pele, rot=('z', -45.0, (0, 0, zc)), emite=emite, so=so)


def espada_cubos(estilo, sem):
    cs = []
    rng = np.random.default_rng(sem)
    # cabo e pomo
    cs.append(sw('pomo', -1.1, 0.4, 1.1, 1.5, 'pomo', 1.6))
    cs.append(sw('cabo', -0.75, 1.5, 0.75, 5.6, 'cabo', 1.5))
    # guarda por estilo
    if estilo in ('pesada', 'larga', 'cutelo'):
        cs.append(sw('guarda', -4.2, 5.6, 4.2, 7.0, 'guarda', 1.9))
    elif estilo in ('cristal', 'arcano', 'vazio', 'luz'):
        cs.append(sw('guarda', -3.0, 5.6, 3.0, 6.8, 'guarda', 1.7))
        cs.append(sw('guarda_p1', -3.6, 6.4, -2.4, 8.4, 'guarda', 1.2))
        cs.append(sw('guarda_p2', 2.4, 6.4, 3.6, 8.4, 'guarda', 1.2))
    elif estilo == 'osso':
        cs.append(sw('guarda', -3.4, 5.6, 3.4, 6.9, 'guarda', 1.8))
        cs.append(sw('guarda_d1', -3.6, 6.9, -2.6, 8.6, 'dente', 1.0))
        cs.append(sw('guarda_d2', 2.6, 6.9, 3.6, 8.6, 'dente', 1.0))
    elif estilo == 'khopesh':
        cs.append(sw('guarda', -2.6, 5.6, 2.6, 6.8, 'guarda', 1.7))
    else:
        cs.append(sw('guarda', -3.4, 5.6, 3.4, 6.9, 'guarda', 1.8))
        if estilo in ('wither', 'sangue', 'veneno', 'lava'):
            cs.append(sw('guarda_c1', -3.6, 4.4, -2.6, 5.6, 'guarda', 1.2))
            cs.append(sw('guarda_c2', 2.6, 4.4, 3.6, 5.6, 'guarda', 1.2))
    # lamina por estilo: largura, segmentos e deslocamentos
    larg = {'pesada': 3.4, 'larga': 3.0, 'cutelo': 3.2, 'vento': 1.8, 'fantasma': 2.0, 'cristal': 2.2}.get(estilo, 2.4)
    esp = {'pesada': 0.9, 'cutelo': 0.9, 'fantasma': 0.5, 'vento': 0.5, 'gelo': 0.8, 'cristal': 0.8}.get(estilo, 0.7)
    y = 7.0
    segs = [(5.0, 1.0), (4.5, 0.86), (3.5, 0.66)]
    off = 0.0
    for k, (h, f) in enumerate(segs):
        w = larg * f / 2
        dx = 0.0
        if estilo == 'torta':
            dx = (rng.random() - 0.5) * 1.2
        if estilo == 'khopesh':
            dx = k * 0.9
        cs.append(sw(f'lamina{k}', -w + dx, y, w + dx, y + h, 'lamina', esp))
        cs.append(sw(f'runa{k}', -w * 0.55 + dx, y + 0.3, w * 0.55 + dx, y + h - 0.3, 'runa', esp + 0.12, emite=15))
        if estilo in ('osso', 'cutelo', 'veneno', 'sculk'):
            # dentes/serrilha do lado de fora (direita no espaco da espada)
            for j in range(2):
                yy = y + 0.8 + j * h / 2
                cs.append(sw(f'serra{k}{j}', w + dx - 0.1, yy, w + dx + 0.9, yy + 1.0, 'dente', esp * 0.8))
        y += h
        off = dx
    # ponta
    cs.append(sw('ponta', -0.55 + off, y, 0.55 + off, y + 1.6, 'lamina', esp * 0.8))
    cs.append(sw('ponta_r', -0.25 + off, y, 0.25 + off, y + 1.2, 'runa', esp * 0.9, emite=15))
    # enfeites por estilo
    if estilo in ('lava', 'wither', 'luz', 'vazio', 'arcano', 'cristal'):
        for j in range(3):
            yy = 8.5 + j * 4.2
            cs.append(sw(f'chama{j}', -larg / 2 - 1.1, yy, -larg / 2 - 0.2, yy + 1.8, 'aura', 0.3, emite=15))
            cs.append(sw(f'chamb{j}', larg / 2 + 0.2, yy + 1.5, larg / 2 + 1.1, yy + 3.0, 'aura', 0.3, emite=15))
    if estilo == 'gelo':
        for j in range(3):
            yy = 9 + j * 3.5
            cs.append(sw(f'cristal{j}', larg / 2 - 0.2, yy, larg / 2 + 1.2, yy + 1.0, 'lamina', 0.6))
    if estilo == 'ouro':
        for j in range(2):
            cs.append(sw(f'gema{j}', -0.5, 9 + j * 5, 0.5, 10 + j * 5, 'gema', esp + 0.3, emite=10))
    if estilo == 'agua':
        for j in range(3):
            yy = 8.5 + j * 4
            cs.append(sw(f'onda{j}', larg / 2 - 0.1, yy, larg / 2 + 0.8, yy + 1.4, 'espuma', 0.5))
    return cs


# ------------------------------------------------------------------ pintura da espada

def pintor_espada(t, estilo):
    cor = cor_tema(t)
    sem = sum(ord(ch) for ch in t['de']) % 997

    def p(P):
        x, y, z, s, tt = P['x'], P['y'], P['z'], P['s'], P['t']
        pele = P['pele']
        n = fbm(x * 2.2, y * 2.2, z * 2.2, 3, 1, sem)
        if pele == 'cabo':
            # couro enrolado: faixas diagonais escuras com a cor do tema nos sulcos
            faixa = 0.5 + 0.5 * np.sin((y * 3.2 + s * 2.0) * 1.0)
            c = rampa(faixa, [(0, '#2a1b12'), (0.5, '#4a3222'), (1, '#6b4a33')])
            c = mix(c, escurecer(cor, 0.6), ss(0.75, 0.95, faixa) * 0.6)
            return c * (0.85 + 0.3 * n)[..., None], 1
        if pele == 'pomo':
            c = rampa(n, [(0, escurecer(cor, 0.5)), (0.5, cor), (1, clarear(cor, 0.5))])
            d = elipse(s, tt, 0.5, 0.5, 0.35, 0.35)
            c = mix(c, clarear(cor, 0.8), dentro(d, 0.3) * 0.8)
            return c, 1
        if pele == 'guarda':
            if estilo in ('ouro', 'khopesh'):
                base = rampa(n, [(0, '#8a6a12'), (0.5, '#e0b53a'), (1, '#fff0a0')])
            elif estilo == 'osso':
                base = rampa(n, [(0, '#b9ad96'), (0.5, '#ded4bf'), (1, '#f1ead9')])
            elif estilo in ('wither', 'vazio', 'sangue'):
                base = rampa(n, [(0, '#0e0e14'), (0.5, '#2a2a36'), (1, '#4a4a5c')])
            else:
                base = rampa(n, [(0, '#3a3f48'), (0.5, '#8a929e'), (1, '#d8dde4')])
            borda = np.minimum(np.minimum(s, 1 - s), np.minimum(tt, 1 - tt))
            base = mix(base, cor, (borda < 0.12) * 0.7)
            return base, 1
        if pele == 'dente':
            return rampa(n, [(0, '#8a7a50'), (0.5, '#d8cba6'), (1, '#f6efd8')]), 1
        if pele == 'gema':
            return rampa(n, [(0, escurecer(cor, 0.4)), (0.6, cor), (1, '#ffffff')]), 1
        if pele == 'espuma':
            return rampa(n, [(0, '#bfe9ff'), (1, '#ffffff')]), np.where(n > 0.4, 1.0, 0.0)
        if pele == 'aura':
            c = rampa(n, [(0, cor), (1, clarear(cor, 0.7))])
            a = np.where(fbm(x * 3, y * 1.5, z * 3, 2, 1, sem + 3) > 0.48, 0.9, 0.0)
            return c, a
        if pele == 'runa':
            # runas: simbolos recortados (alpha) com a cor do tema brilhando
            col = np.floor(y * 1.3).astype(np.int64)
            sim = hash2(col, 0, sem + 7)
            forma = ((np.abs(s - 0.5) < 0.12) & (np.abs((y * 1.3) % 1 - 0.5) < 0.38)) | ((np.abs((y * 1.3) % 1 - 0.5) < 0.09) & (np.abs(s - 0.5) < 0.38))
            forma = forma & (sim > 0.25)
            pulso = 0.6 + 0.4 * np.sin(y * 2.0)
            c = rampa(pulso, [(0, cor), (1, clarear(cor, 0.75))])
            if estilo == 'luz':
                c = np.ones_like(c) * 0.98
            return c, np.where(forma, 1.0, 0.0)
        # lamina por material
        if estilo == 'osso':
            c = rampa(n, [(0, '#b0a48c'), (0.5, '#dfd6c2'), (1, '#f4eee0')])
            c = c * (1 - 0.5 * ss(0.8, 0.95, ridged(x * 3, y * 3, z * 3, 2, 1, sem + 1)))[..., None]
        elif estilo == 'gelo':
            c = rampa(n, [(0, '#5aa6e6'), (0.5, '#b4e2ff'), (1, '#f2fbff')])
            c = mix(c, C('#ffffff'), ss(0.82, 0.95, ridged(x * 3, y * 3, z * 3, 2, 1, sem + 1)) * 0.8)
        elif estilo in ('ouro', 'khopesh'):
            c = rampa(n, [(0, '#9a7a1a'), (0.5, '#e8c04a'), (1, '#fff3b0')])
        elif estilo == 'lava':
            c = rampa(n, [(0, '#15110f'), (0.5, '#2e2622'), (1, '#4a3c34')])
            c = mix(c, rampa(fbm(x * 4, y * 4, z * 4, 2, 1, sem + 2), [(0, '#ff4a00'), (1, '#ffd05a')]), ss(0.8, 0.94, ridged(x * 2, y * 2, z * 2, 3, 1, sem + 1)))
        elif estilo in ('wither', 'vazio'):
            c = rampa(n, [(0, '#07070b'), (0.5, '#16151f'), (1, '#2c2a3a')])
            if estilo == 'vazio':
                estrela = (hash2(np.floor(s * 20), np.floor(y * 6), sem + 4) > 0.95)
                c = np.where(estrela[..., None], clarear(cor, 0.6) * np.ones_like(c), c)
        elif estilo == 'sangue':
            c = rampa(n, [(0, '#2a2c33'), (0.5, '#5a5f6a'), (1, '#9aa1ad')])
            gota = ss(0.85, 0.95, ridged(x * 2.5, y * 0.4, z * 2.5, 2, 1, sem + 2))
            c = mix(c, C('#6a0a0f'), gota * 0.9)
        elif estilo == 'veneno':
            c = rampa(n, [(0, '#1c2a16'), (0.5, '#3b5a2a'), (1, '#6c8f45')])
            c = mix(c, cor, ss(0.75, 0.95, fbm(x * 3, y * 1.2, z * 3, 2, 1, sem + 2)) * 0.8)
        elif estilo == 'agua':
            c = rampa(n, [(0, '#0f4f8f'), (0.5, '#2a8ad8'), (1, '#9adcff')])
        elif estilo == 'fantasma':
            c = rampa(n, [(0, escurecer(cor, 0.6)), (0.5, cor), (1, clarear(cor, 0.6))])
            return c, np.where(fbm(x * 2, y * 2, z * 2, 2, 1, sem + 5) > 0.35, 0.85, 0.0)
        elif estilo == 'vento':
            c = rampa(n, [(0, '#c8d4e0'), (0.5, '#eef4fa'), (1, '#ffffff')])
        elif estilo == 'sculk':
            c = rampa(n, [(0, '#0b2a30'), (0.5, '#1a5a62'), (1, '#2f8f98')])
            c = mix(c, C('#7fe9ff'), ss(0.85, 0.95, np.abs(np.sin(y * 2.5)) + 0.3 * (n - 0.5)) * 0.9)
        elif estilo == 'luz':
            c = rampa(n, [(0, '#dfe8f0'), (0.5, '#f4f8fb'), (1, '#ffffff')])
        elif estilo == 'cristal':
            c = rampa(n, [(0, escurecer(cor, 0.5)), (0.5, clarear(cor, 0.2)), (1, clarear(cor, 0.8))])
        else:   # aco (pesada, larga, cutelo, torta, arcano)
            c = rampa(n, [(0, '#4a5058'), (0.5, '#9aa3ad'), (1, '#e6ebf0')])
            c = mix(c, cor, ss(0.7, 0.95, fbm(x * 1.5, y * 1.5, z * 1.5, 2, 1, sem + 2)) * 0.35)
        # fio claro nas bordas e sulco central escuro
        c = mix(c, clarear(c, 0.6), (np.minimum(s, 1 - s) < 0.12) * 0.6)
        c = c * (1 - 0.3 * np.exp(-((s - 0.5) / 0.08) ** 2))[..., None]
        return c, 1
    return p


# ------------------------------------------------------------------ armadura (texturas de camada)

# layout 64x32 da camada de armadura (igual a skin antiga): parte -> (u, v, w, h, d)
PARTES = {'head': (0, 0, 8, 8, 8), 'body': (16, 16, 8, 12, 4), 'rarm': (40, 16, 4, 12, 4), 'rleg': (0, 16, 4, 12, 4)}


def faces_uv(u, v, w, h, d):
    """Retangulos (face -> (x0,y0,x1,y1)) no layout classico."""
    return {'up': (u + d, v, u + d + w, v + d), 'down': (u + d + w, v, u + d + 2 * w, v + d),
            'west': (u, v + d, u + d, v + d + h), 'south': (u + d, v + d, u + d + w, v + d + h),
            'east': (u + d + w, v + d, u + 2 * d + w, v + d + h), 'north': (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h)}


def pintor_armadura(t, estilo, camada):
    cor = cor_tema(t)
    sem = sum(ord(ch) for ch in t['de']) % 991 + camada

    def material(x, y, z, s, tt, parte, face):
        n = fbm(x * 0.5, y * 0.5, z * 0.5, 3, 1, sem)
        if estilo == 'osso':
            c = rampa(n, [(0, '#a89c86'), (0.5, '#d9d0bb'), (1, '#f1ead9')])
        elif estilo == 'gelo':
            c = rampa(n, [(0, '#5aa6e6'), (0.5, '#a8dcff'), (1, '#eefaff')])
        elif estilo in ('ouro', 'khopesh'):
            c = rampa(n, [(0, '#8a6a12'), (0.5, '#dcb43a'), (1, '#fff0a0')])
        elif estilo in ('wither', 'vazio', 'sculk'):
            c = rampa(n, [(0, '#0a0a10'), (0.5, '#1e1e2a'), (1, '#3a3a4c')])
        elif estilo == 'lava':
            c = rampa(n, [(0, '#1a1412'), (0.5, '#3a2c26'), (1, '#5a463c')])
        elif estilo == 'veneno':
            c = rampa(n, [(0, '#1e2e18'), (0.5, '#3e5e2c'), (1, '#6a8a48')])
        elif estilo == 'luz':
            c = rampa(n, [(0, '#d8e2ea'), (0.5, '#f2f6f9'), (1, '#ffffff')])
        elif estilo in ('fantasma', 'vento'):
            c = rampa(n, [(0, '#8a96a8'), (0.5, '#c4ccd8'), (1, '#f0f4f8')])
        else:
            c = rampa(n, [(0, '#3c424a'), (0.5, '#8a929e'), (1, '#d8dde4')])
        # tinta do tema nas placas (faixas), rebites e borda clara
        placa = ss(0.55, 0.75, fbm(x * 1.2, y * 0.35, z * 1.2, 2, 1, sem + 1))
        c = mix(c, cor, placa * 0.45)
        rebite = (np.abs((x * 2) % 2 - 1) < 0.35) & (np.abs((y * 2 + 1) % 4 - 2) < 0.35)
        c = np.where(rebite[..., None], clarear(c, 0.35), c)
        borda = np.minimum(np.minimum(s, 1 - s), np.minimum(tt, 1 - tt))
        c = mix(c, clarear(cor, 0.5), (borda < 0.1) * 0.6)
        return c

    def p(parte, face, s, tt, x, y, z):
        c = material(x, y, z, s, tt, parte, face)
        a = np.ones(s.shape)
        if parte == 'head':
            if face == 'south':
                # viseira: fenda escura com brilho da cor do tema (olhos)
                fenda = (np.abs(tt - 0.45) < 0.09) & (s > 0.1) & (s < 0.9)
                c = np.where(fenda[..., None], escurecer(cor, 0.15) * np.ones_like(c), c)
                olho = ((np.abs(s - 0.3) < 0.08) | (np.abs(s - 0.7) < 0.08)) & (np.abs(tt - 0.45) < 0.06)
                c = np.where(olho[..., None], clarear(cor, 0.7) * np.ones_like(c), c)
                if camada == 1 and estilo in ('osso', 'wither', 'sangue'):
                    dente = (tt > 0.78) & (np.abs((s * 8) % 1 - 0.5) < 0.3)
                    c = np.where(dente[..., None], C('#efe6cf') * np.ones_like(c), c)
            if face == 'up' and estilo in ('ouro', 'khopesh', 'luz', 'cristal'):
                crista = np.abs(s - 0.5) < 0.1
                c = np.where(crista[..., None], clarear(cor, 0.4) * np.ones_like(c), c)
        if parte == 'body' and face == 'south':
            # emblema no peito: diamante com a cor do tema e nucleo claro
            d = np.abs(s - 0.5) / 0.22 + np.abs(tt - 0.42) / 0.26
            c = mix(c, cor, dentro(d, 0.15))
            c = mix(c, clarear(cor, 0.8), dentro(d / 0.45, 0.2))
        if parte in ('rleg',) and camada == 1:
            # camada 1 nas pernas = botas: so a parte de baixo existe
            a = np.where(tt > 0.6, 1.0, 0.0)
            c = mix(c, clarear(cor, 0.4), (np.abs(tt - 0.62) < 0.04) * 0.8)
        if parte == 'body' and camada == 2:
            # calcas: so a parte de baixo do corpo (cinto) + pernas inteiras
            a = np.where(tt > 0.6, 1.0, 0.0)
            c = mix(c, clarear(cor, 0.3), (np.abs(tt - 0.66) < 0.05) * 0.8)
        if parte == 'rarm' and camada == 2:
            a = np.zeros(s.shape)
        if parte == 'head' and camada == 2:
            a = np.zeros(s.shape)
        return c, a
    return p


def textura_armadura(t, estilo, camada):
    img = np.zeros((32, 64, 4))
    pint = pintor_armadura(t, estilo, camada)
    for parte, (u, v, w, h, d) in PARTES.items():
        for face, (x0, y0, x1, y1) in faces_uv(u, v, w, h, d).items():
            W, H = x1 - x0, y1 - y0
            s = (np.arange(W) + 0.5) / W
            tt = (np.arange(H) + 0.5) / H
            S, T = np.meshgrid(s, tt)
            # coordenadas 3D aproximadas para o ruido ser continuo
            X = x0 + S * W
            Y = y0 + T * H
            Z = np.full_like(S, {'north': 0, 'south': d, 'east': w, 'west': 0, 'up': 0, 'down': h}[face])
            rgb, a = pint(parte, face, S, T, X, Y, Z)
            rgb = np.clip(np.asarray(rgb, float), 0, 1)
            a = np.clip(np.broadcast_to(np.asarray(a, float), rgb.shape[:2]), 0, 1)
            img[y0:y1, x0:x1, :3] = rgb
            img[y0:y1, x0:x1, 3] = a
    # a textura de armadura e 64x32; o jogo aceita multiplos (128x64) - usamos 4x para detalhe
    out = np.kron(img, np.ones((4, 4, 1)))
    return (np.clip(out, 0, 1) * 255 + 0.5).astype(np.uint8)


# ------------------------------------------------------------------ gerar

def gerar(id_, t):
    estilo = ESTILO.get(t['golpe'], 'aco')
    tema = id_.lower()
    sem = sum(ord(ch) for ch in id_)
    cs = espada_cubos(estilo, sem)
    reps = list(cs)
    largura = 128
    altura = kit.arrumar_uv(reps, largura, 4)
    H = 16
    while H < altura:
        H *= 2
    atlas = kit.pintar_atlas(reps, largura, H, pintor_espada(t, estilo), 4)
    tex_dir = os.path.join(JAVA, 'textures', 'item', 'reliquias')
    os.makedirs(tex_dir, exist_ok=True)
    kit.salvar_png_leve(atlas, os.path.join(tex_dir, f'espada_{tema}.png'))
    modelo = kit.modelo_java(cs, f'corona:item/reliquias/espada_{tema}', largura, H, display=DISPLAY)
    kit.salvar_json(os.path.join(JAVA, 'models', 'item', 'reliquias', f'espada_{tema}.json'), modelo)
    kit.salvar_json(os.path.join(JAVA, 'items', 'item', 'reliquias', f'espada_{tema}.json'), kit.item_java(f'corona:item/reliquias/espada_{tema}'))
    # armadura: equipment asset + texturas das duas camadas
    kit.salvar_json(os.path.join(JAVA, 'equipment', f'{tema}.json'),
                    {'layers': {'humanoid': [{'texture': f'corona:{tema}'}], 'humanoid_leggings': [{'texture': f'corona:{tema}'}]}})
    for camada, pasta in ((1, 'humanoid'), (2, 'humanoid_leggings')):
        d = os.path.join(JAVA, 'textures', 'entity', 'equipment', pasta)
        os.makedirs(d, exist_ok=True)
        Image.fromarray(textura_armadura(t, estilo, camada), 'RGBA').save(os.path.join(d, f'{tema}.png'), optimize=True)
    # previa da espada (vista de frente = como aparece no inventario) e icone 2D para o Bedrock
    pv = kit.previa(cs, atlas, 0, 0, escala=10)
    pv.save(os.path.join(PREV, f'espada_{tema}.png'))
    icone = pv.convert('RGBA')
    # fundo transparente: o previa pinta fundo cinza; recorta por cor
    arr = np.array(icone)
    fundo = (np.abs(arr[..., 0].astype(int) - 28) < 3) & (np.abs(arr[..., 1].astype(int) - 30) < 3) & (np.abs(arr[..., 2].astype(int) - 36) < 3)
    arr[fundo, 3] = 0
    ic = Image.fromarray(arr, 'RGBA')
    ic.thumbnail((32, 32), Image.LANCZOS)
    quad = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    quad.paste(ic, ((32 - ic.width) // 2, (32 - ic.height) // 2))
    bd = os.path.join(BEDROCK, 'textures', 'items', 'reliquias')
    os.makedirs(bd, exist_ok=True)
    quad.save(os.path.join(bd, f'espada_{tema}.png'))
    return estilo


if __name__ == '__main__':
    so = [s.upper() for s in sys.argv[1:]]
    feitos = []
    for id_, t in TEMAS.items():
        if so and id_ not in so:
            continue
        est = gerar(id_, t)
        feitos.append((id_, est))
        print('ok', id_, est, t['de'])
    print(len(feitos), 'temas')


# ------------------------------------------------------------------ Bedrock (Geyser custom items v2): icones 2D das espadas

ESPADAS_JAVA = ['wooden_sword', 'stone_sword', 'iron_sword', 'golden_sword', 'diamond_sword', 'netherite_sword']


def geyser_mapeamento(saida_json, saida_tex):
    """custom_mappings/corona-reliquias.json (Geyser) + textures/item_texture.json do mcpack (icones em textures/items/reliquias)."""
    itens = {}
    tex = {'resource_pack_name': 'corona', 'texture_name': 'atlas.items', 'texture_data': {}}
    for id_, t in TEMAS.items():
        tema = id_.lower()
        tex['texture_data'][f'corona_espada_{tema}'] = {'textures': f'textures/items/reliquias/espada_{tema}'}
        for mat in ESPADAS_JAVA:
            itens.setdefault(f'minecraft:{mat}', []).append({
                'type': 'definition',
                'model': f'corona:item/reliquias/espada_{tema}',
                'bedrock_identifier': f'corona:espada_{tema}_{mat.split("_")[0]}',
                'display_name': f'Espada {t["de"]}',
                'bedrock_options': {'display_handheld': True, 'icon': f'corona_espada_{tema}', 'allow_offhand': True},
            })
    try:
        import reliquias_armas
        extra_itens, extra_tex = reliquias_armas.geyser_extra()
        for k, v in extra_itens.items():
            itens.setdefault(k, []).extend(v)
        tex['texture_data'].update(extra_tex)
    except ImportError:
        pass
    os.makedirs(os.path.dirname(saida_json), exist_ok=True)
    with open(saida_json, 'w', encoding='utf-8') as f:
        json.dump({'format_version': 2, 'items': itens}, f, ensure_ascii=False, indent=1)
    os.makedirs(os.path.dirname(saida_tex), exist_ok=True)
    with open(saida_tex, 'w', encoding='utf-8') as f:
        json.dump(tex, f, ensure_ascii=False, indent=1)
    print('geyser:', sum(len(v) for v in itens.values()), 'definicoes;', len(tex['texture_data']), 'icones')
