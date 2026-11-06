"""Outras armas das reliquias (alem da espada de reliquias.py), por tema: machado, picareta, maca, tridente, arco (4 quadros)
e besta (5 estados). Mesmo "espaco da espada" (y ao longo do cabo, +x = lado de baixo-direita no inventario) e mesmos
pintores, com peles novas: madeira, corda, flecha, pena, fio.
Saida: python3 -I reliquias_armas.py [ids...]   (tambem chamado por gerar_reliquias.sh)
"""
import json, math, os, sys
import numpy as np
from PIL import Image
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import reliquias as R  # noqa: E402
import kit  # noqa: E402
from reliquias import sw, TEMAS, JAVA, BEDROCK, PREV, ESTILO  # noqa: E402
from base import fbm, rampa, mix, C, ss  # noqa: E402

ARMAS = ['machado', 'picareta', 'maca', 'tridente', 'arco', 'besta']
# material Java -> nome da arma (para o item_model e o mapeamento Geyser)
MATERIAIS = {
    'machado': ['wooden_axe', 'stone_axe', 'iron_axe', 'golden_axe', 'diamond_axe', 'netherite_axe'],
    'picareta': ['wooden_pickaxe', 'stone_pickaxe', 'iron_pickaxe', 'golden_pickaxe', 'diamond_pickaxe', 'netherite_pickaxe'],
}

DISPLAY_ARCO = {
    'thirdperson_righthand': {'rotation': [-80, 260, -40], 'translation': [-1, -2, 2.5], 'scale': [0.9, 0.9, 0.9]},
    'thirdperson_lefthand': {'rotation': [-80, -280, 40], 'translation': [-1, -2, 2.5], 'scale': [0.9, 0.9, 0.9]},
    'firstperson_righthand': {'rotation': [0, -90, 25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
    'firstperson_lefthand': {'rotation': [0, 90, -25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
    'ground': R.DISPLAY['ground'], 'head': R.DISPLAY['head'], 'gui': R.DISPLAY['gui'], 'fixed': R.DISPLAY['fixed'],
}
DISPLAY_BESTA = {
    'thirdperson_righthand': {'rotation': [-90, 0, -60], 'translation': [2, 0.1, -3], 'scale': [0.9, 0.9, 0.9]},
    'thirdperson_lefthand': {'rotation': [-90, 0, 30], 'translation': [2, 0.1, -3], 'scale': [0.9, 0.9, 0.9]},
    'firstperson_righthand': {'rotation': [-90, 0, -55], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
    'firstperson_lefthand': {'rotation': [-90, 0, 35], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
    'ground': R.DISPLAY['ground'], 'head': R.DISPLAY['head'], 'gui': R.DISPLAY['gui'], 'fixed': R.DISPLAY['fixed'],
}


def linha(prefixo, x0, y0, x1, y1, pele, grossura=0.45, esp=0.3, passo=0.55, zc=8.0):
    """Linha em 'escadinha' de cubinhos (o Java so gira em multiplos de 22.5): corda puxada."""
    d = math.hypot(x1 - x0, y1 - y0)
    n = max(1, int(math.ceil(d / passo)))
    cs = []
    for i in range(n + 1):
        f = i / n
        cx, cy = x0 + (x1 - x0) * f, y0 + (y1 - y0) * f
        cs.append(sw(f'{prefixo}{i}', cx - grossura / 2, cy - grossura / 2, cx + grossura / 2, cy + grossura / 2, pele, esp, zc))
    return cs


def cabo(cs, y0, y1, esp=1.4):
    cs.append(sw('pomo', -1.0, y0 - 1.0, 1.0, y0, 'pomo', esp + 0.2))
    cs.append(sw('cabo', -0.7, y0, 0.7, y1, 'cabo', esp))
    for k, yy in enumerate(np.linspace(y0 + 2.5, y1 - 2.5, 2)):
        cs.append(sw(f'anel{k}', -0.9, yy, 0.9, yy + 0.8, 'guarda', esp + 0.2))


# ------------------------------------------------------------------ geometria

def machado_cubos(estilo, sem):
    cs = []
    cabo(cs, 1.3, 16.6)
    esp = {'pesada': 1.0, 'cutelo': 1.0, 'fantasma': 0.5}.get(estilo, 0.8)
    cs.append(sw('olho', -1.4, 11.4, 1.4, 16.9, 'guarda', 1.8))
    cs.append(sw('topo', -0.6, 16.9, 0.6, 17.8, 'guarda', 1.3))
    degraus = [(1.4, 2.7, 11.3, 17.0), (2.7, 3.9, 10.6, 17.7), (3.9, 5.0, 9.9, 18.1)]
    duplo = estilo in ('pesada', 'larga', 'cutelo', 'ouro', 'wither')
    lados = (1, -1) if duplo else (1,)
    for sinal in lados:
        s = 'a' if sinal > 0 else 'b'
        for k, (xa, xb, ya, yb) in enumerate(degraus):
            cs.append(sw(f'lamina{s}{k}', min(sinal * xa, sinal * xb), ya, max(sinal * xa, sinal * xb), yb, 'lamina', esp))
        cs.append(sw(f'fio{s}', min(sinal * 5.0, sinal * 5.5), 9.6, max(sinal * 5.0, sinal * 5.5), 18.4, 'fio', esp * 0.8))
        cs.append(sw(f'runa{s}', min(sinal * 1.6, sinal * 3.6), 12.6, max(sinal * 1.6, sinal * 3.6), 15.8, 'runa', esp + 0.12, emite=15))
        if estilo in ('osso', 'veneno', 'sculk', 'cutelo'):
            for j in range(3):
                yy = 10.6 + j * 2.8
                cs.append(sw(f'dente{s}{j}', min(sinal * 5.5, sinal * 6.3), yy, max(sinal * 5.5, sinal * 6.3), yy + 0.9, 'dente', esp * 0.7))
        if estilo in ('lava', 'wither', 'luz', 'vazio', 'arcano', 'cristal'):
            for j in range(3):
                yy = 11.0 + j * 2.4
                xa = 2.6 + j * 0.7
                cs.append(sw(f'chama{s}{j}', min(sinal * xa, sinal * (xa + 0.9)), yy + (j % 2) * 0.5, max(sinal * xa, sinal * (xa + 0.9)), yy + 1.7, 'aura', 0.3, emite=15))
    if not duplo:
        cs.append(sw('espigao', -3.3, 13.4, -1.4, 15.0, 'lamina', esp * 0.9))
        cs.append(sw('espigao_p', -4.3, 13.8, -3.3, 14.6, 'fio', esp * 0.7))
    if estilo == 'gelo':
        for j in range(2):
            cs.append(sw(f'cristal{j}', 1.9 + j * 1.7, 17.8 + j * 0.3, 2.8 + j * 1.7, 19.2 - j * 0.2, 'lamina', 0.6))
    if estilo == 'ouro':
        cs.append(sw('gema', -0.5, 13.6, 0.5, 14.6, 'gema', 2.1, emite=10))
    return cs


def picareta_cubos(estilo, sem):
    cs = []
    cabo(cs, 1.3, 16.0)
    cs.append(sw('centro', -1.3, 14.0, 1.3, 17.4, 'guarda', 1.8))
    cs.append(sw('runa', -0.8, 14.6, 0.8, 16.8, 'runa', 1.95, emite=15))
    esp = 0.9
    segs = [(1.3, 3.2, 14.6, 16.8), (3.2, 4.8, 14.1, 16.2), (4.8, 6.1, 13.6, 15.5)]
    for sinal in (1, -1):
        s = 'a' if sinal > 0 else 'b'
        for k, (xa, xb, ya, yb) in enumerate(segs):
            cs.append(sw(f'bico{s}{k}', min(sinal * xa, sinal * xb), ya, max(sinal * xa, sinal * xb), yb, 'lamina', esp - 0.1 * k))
        cs.append(sw(f'fio{s}', min(sinal * 6.1, sinal * 6.9), 13.4, max(sinal * 6.1, sinal * 6.9), 14.6, 'fio', 0.6))
        if estilo in ('lava', 'wither', 'luz', 'vazio', 'arcano', 'cristal'):
            cs.append(sw(f'chama{s}', min(sinal * 2.4, sinal * 3.4), 16.6, max(sinal * 2.4, sinal * 3.4), 17.8, 'aura', 0.3, emite=15))
    if estilo == 'ouro':
        cs.append(sw('gema', -0.5, 15.2, 0.5, 16.2, 'gema', 2.1, emite=10))
    return cs


def maca_cubos(estilo, sem):
    cs = []
    cabo(cs, 1.3, 11.8, 1.5)
    cs.append(sw('cabeca', -2.4, 11.8, 2.4, 17.4, 'lamina', 3.2))
    cs.append(sw('cinta1', -2.6, 12.8, 2.6, 13.6, 'guarda', 3.4))
    cs.append(sw('cinta2', -2.6, 15.6, 2.6, 16.4, 'guarda', 3.4))
    cs.append(sw('runa', -1.6, 13.8, 1.6, 15.4, 'runa', 3.35, emite=15))
    cs.append(sw('runa_l', -2.55, 13.8, 2.55, 15.4, 'runa', 2.0, emite=15))
    # espigoes
    for sinal in (1, -1):
        s = 'a' if sinal > 0 else 'b'
        cs.append(sw(f'espigao{s}', min(sinal * 2.4, sinal * 3.6), 13.9, max(sinal * 2.4, sinal * 3.6), 15.3, 'fio', 1.2))
    cs.append(sw('espigao_t', -0.7, 17.4, 0.7, 18.7, 'fio', 1.4))
    cs.append(sw('espigao_f', -0.6, 13.9, 0.6, 15.3, 'fio', 1.2, zc=8 + 2.2))
    if estilo in ('osso', 'veneno', 'sculk', 'cutelo'):
        for j in range(2):
            cs.append(sw(f'dente{j}', -1.8 + j * 2.6, 17.4, -0.9 + j * 2.6, 18.2, 'dente', 1.0))
    if estilo in ('lava', 'wither', 'luz', 'vazio', 'arcano', 'cristal'):
        for j in range(3):
            cs.append(sw(f'chama{j}', -2.0 + j * 1.5, 17.6 + (j % 2) * 0.4, -1.2 + j * 1.5, 18.9, 'aura', 0.3, emite=15))
    if estilo == 'ouro':
        cs.append(sw('gema', -0.6, 14.0, 0.6, 15.2, 'gema', 3.6, emite=10))
    return cs


def tridente_cubos(estilo, sem):
    cs = []
    cs.append(sw('pomo', -0.9, 0.2, 0.9, 1.2, 'pomo', 1.4))
    cs.append(sw('cabo', -0.55, 1.2, 0.55, 14.8, 'cabo', 1.1))
    for k, yy in enumerate((4.0, 8.0, 12.0)):
        cs.append(sw(f'anel{k}', -0.75, yy, 0.75, yy + 0.7, 'guarda', 1.3))
    cs.append(sw('base', -2.6, 14.8, 2.6, 16.2, 'guarda', 1.6))
    cs.append(sw('runa', -1.8, 15.0, 1.8, 16.0, 'runa', 1.75, emite=15))
    esp = 0.8
    cs.append(sw('dente_c', -0.55, 16.2, 0.55, 20.6, 'lamina', esp))
    cs.append(sw('ponta_c', -0.3, 20.6, 0.3, 21.6, 'fio', esp * 0.8))
    cs.append(sw('runa_c', -0.3, 16.8, 0.3, 19.8, 'runa', esp + 0.12, emite=15))
    for sinal in (1, -1):
        s = 'a' if sinal > 0 else 'b'
        cs.append(sw(f'dente{s}', min(sinal * 1.6, sinal * 2.6), 16.2, max(sinal * 1.6, sinal * 2.6), 19.6, 'lamina', esp))
        cs.append(sw(f'ponta{s}', min(sinal * 1.8, sinal * 2.4), 19.6, max(sinal * 1.8, sinal * 2.4), 20.6, 'fio', esp * 0.8))
        cs.append(sw(f'farpa{s}', min(sinal * 2.6, sinal * 3.4), 18.2, max(sinal * 2.6, sinal * 3.4), 18.9, 'fio', esp * 0.7))
    if estilo in ('lava', 'wither', 'luz', 'vazio', 'arcano', 'cristal'):
        for j in range(2):
            cs.append(sw(f'chama{j}', -1.3 + j * 1.6, 17.0 + j * 1.2, -0.7 + j * 1.6, 18.4 + j * 1.2, 'aura', 0.3, emite=15))
    if estilo == 'ouro':
        cs.append(sw('gema', -0.5, 15.0, 0.5, 16.0, 'gema', 1.9, emite=10))
    return cs


def arco_cubos(estilo, sem, puxar=None):
    """puxar: None (parado) ou 0, 1, 2 (quadros bow_pulling)."""
    prof = {None: 3.0, 0: 3.6, 1: 4.3, 2: 5.0}[puxar]
    y0, y1, n = 2.0, 20.0, 8
    cs = []
    for k in range(n):
        ya, yb = y0 + (y1 - y0) * k / n, y0 + (y1 - y0) * (k + 1) / n
        f = math.sin(math.pi * (k + 0.5) / n)
        xc, w = -prof * f, 0.8 + 0.6 * f
        cs.append(sw(f'arco{k}', xc - w / 2, ya, xc + w / 2, yb, 'madeira', 0.8 + 0.3 * f))
    cs.append(sw('empunhadura', -prof - 0.95, 9.4, -prof + 0.95, 12.6, 'cabo', 1.35))
    cs.append(sw('ponta_a', -0.9, y0 - 0.8, 0.5, y0 + 0.5, 'guarda', 0.9))
    cs.append(sw('ponta_b', -0.9, y1 - 0.5, 0.5, y1 + 0.8, 'guarda', 0.9))
    xr = -prof * math.sin(math.pi * 0.3)
    cs.append(sw('runa_a', xr - 0.3, 5.0, xr + 0.3, 8.2, 'runa', 1.25, emite=15))
    cs.append(sw('runa_b', xr - 0.3, 13.8, xr + 0.3, 17.0, 'runa', 1.25, emite=15))
    if estilo in ('lava', 'wither', 'luz', 'vazio', 'arcano', 'cristal'):
        for j in range(3):
            yy = 4.5 + j * 5.0
            f = math.sin(math.pi * (yy - y0) / (y1 - y0))
            xa = -prof * f - 1.4
            cs.append(sw(f'chama{j}', xa - 0.8, yy, xa, yy + 1.6, 'aura', 0.3, emite=15))
    xs = 0.2
    if puxar is None:
        cs.append(sw('corda', xs - 0.15, y0, xs + 0.15, y1, 'corda', 0.3))
    else:
        px, py = xs + (2.2, 3.6, 5.0)[puxar], 11.0
        cs += linha('corda_a', xs, y0, px, py, 'corda', 0.4, 0.3, 0.55)
        cs += linha('corda_b', xs, y1, px, py, 'corda', 0.4, 0.3, 0.55)
        cs.append(sw('flecha', px - 10.6, py - 0.3, px, py + 0.3, 'flecha', 0.6, zc=8.9))
        cs.append(sw('flecha_ponta', px - 12.2, py - 0.55, px - 10.6, py + 0.55, 'fio', 0.6, zc=8.9))
        cs.append(sw('pena1', px - 1.8, py + 0.3, px - 0.3, py + 1.1, 'pena', 0.3, zc=8.9))
        cs.append(sw('pena2', px - 1.8, py - 1.1, px - 0.3, py - 0.3, 'pena', 0.3, zc=8.9))
    return cs


def besta_cubos(estilo, sem, estado='standby'):
    """estado: standby, p0, p1, p2 (crossbow_pulling), arrow (carregada)."""
    cs = []
    cs.append(sw('coronha', -1.0, 0.8, 1.0, 6.0, 'madeira', 1.9))
    cs.append(sw('corpo', -0.8, 6.0, 0.8, 16.6, 'madeira', 1.6))
    cs.append(sw('gatilho', -0.35, 5.6, 0.35, 6.6, 'guarda', 0.5, zc=6.9))
    cs.append(sw('noz', -1.1, 9.3, 1.1, 10.4, 'guarda', 1.9))
    cs.append(sw('ferragem', -1.1, 13.4, 1.1, 14.6, 'guarda', 1.9))
    cs.append(sw('runa', -0.4, 6.8, 0.4, 9.0, 'runa', 1.75, emite=15))
    cs.append(sw('runa2', -0.4, 11.0, 0.4, 13.0, 'runa', 1.75, emite=15))
    yl = 14.2
    for sinal in (1, -1):
        s = 'a' if sinal > 0 else 'b'
        for k, (xa, xb, dy) in enumerate(((1.0, 3.0, 0.0), (3.0, 4.8, -0.4), (4.8, 6.2, -1.0))):
            cs.append(sw(f'braco{s}{k}', min(sinal * xa, sinal * xb), yl + dy, max(sinal * xa, sinal * xb), yl + dy + 1.1 - 0.1 * k, 'madeira', 0.9 - 0.1 * k))
        cs.append(sw(f'ponta{s}', min(sinal * 6.2, sinal * 6.8), yl - 1.5, max(sinal * 6.2, sinal * 6.8), yl - 0.1, 'guarda', 0.6))
        if estilo in ('lava', 'wither', 'luz', 'vazio', 'arcano', 'cristal'):
            cs.append(sw(f'chama{s}', min(sinal * 3.4, sinal * 4.4), yl + 1.0, max(sinal * 3.4, sinal * 4.4), yl + 2.2, 'aura', 0.3, emite=15))
    yc = yl - 0.9
    if estado == 'standby':
        cs.append(sw('corda', -6.5, yc - 0.15, 6.5, yc + 0.15, 'corda', 0.3, zc=8.9))
    else:
        puxo = {'p0': 1.6, 'p1': 3.0, 'p2': 4.0, 'arrow': 4.0}[estado]
        cs += linha('corda_a', -6.5, yc, 0.0, yc - puxo, 'corda', 0.4, 0.3, 0.55, zc=8.9)
        cs += linha('corda_b', 6.5, yc, 0.0, yc - puxo, 'corda', 0.4, 0.3, 0.55, zc=8.9)
    if estado == 'arrow':
        cs.append(sw('flecha', -0.3, 9.2, 0.3, 19.0, 'flecha', 0.6, zc=9.3))
        cs.append(sw('flecha_ponta', -0.55, 19.0, 0.55, 20.4, 'fio', 0.6, zc=9.3))
        cs.append(sw('pena1', 0.3, 9.4, 1.1, 10.9, 'pena', 0.3, zc=9.3))
        cs.append(sw('pena2', -1.1, 9.4, -0.3, 10.9, 'pena', 0.3, zc=9.3))
    return cs


# ------------------------------------------------------------------ pintura

def pintor_arma(t, estilo):
    base = R.pintor_espada(t, estilo)
    cor = R.cor_tema(t)
    sem = sum(ord(ch) for ch in t['de']) % 997

    def p(P):
        pele = P['pele']
        x, y, z, s, tt = P['x'], P['y'], P['z'], P['s'], P['t']
        n = fbm(x * 2.2, y * 2.2, z * 2.2, 3, 1, sem)
        if pele == 'madeira':
            veio = 0.5 + 0.5 * np.sin(y * 4.0 + n * 6.0 + x * 0.7)
            if estilo in ('osso',):
                c = rampa(veio, [(0, '#b0a48c'), (0.5, '#d5cbb4'), (1, '#efe8d6')])
            elif estilo in ('wither', 'vazio', 'sangue'):
                c = rampa(veio, [(0, '#15121a'), (0.5, '#2b2533'), (1, '#443a4c')])
            elif estilo in ('gelo', 'cristal', 'luz'):
                c = rampa(veio, [(0, R.escurecer(cor, 0.45)), (0.5, R.escurecer(cor, 0.8)), (1, R.clarear(cor, 0.35))])
            else:
                c = rampa(veio, [(0, '#3b2614'), (0.5, '#6b4524'), (1, '#8d6136')])
            faixa = ((y + 1.0) % 4.0) < 0.75
            c = mix(c, R.escurecer(cor, 0.85), faixa * 0.85)
            return c * (0.85 + 0.3 * n)[..., None], 1
        if pele == 'corda':
            tw = 0.5 + 0.5 * np.sin((y + x) * 9.0)
            return rampa(tw, [(0, '#c3bba8'), (1, '#f4f0e4')]), 1
        if pele == 'flecha':
            return rampa(n, [(0, '#8a6a3a'), (1, '#cfae72')]), 1
        if pele == 'pena':
            listras = (((s * 5.0) % 1.0) < 0.5).astype(float)
            return rampa(listras, [(0, cor), (1, R.clarear(cor, 0.65))]), 1
        if pele == 'fio':
            if estilo in ('ouro', 'khopesh'):
                return rampa(n, [(0, '#e0b53a'), (1, '#fff6c8')]), 1
            if estilo == 'osso':
                return rampa(n, [(0, '#e4dbc4'), (1, '#fbf7ec')]), 1
            if estilo in ('wither', 'vazio'):
                return rampa(n, [(0, '#3a3a4c'), (1, R.clarear(cor, 0.5))]), 1
            return rampa(n, [(0, '#c8d0d8'), (1, '#ffffff')]), 1
        return base(P)
    return p


# ------------------------------------------------------------------ gerar

def _modelo(nome, cs, t, estilo, display, icone=False):
    reps = list(cs)
    largura = 128
    altura = kit.arrumar_uv(reps, largura, 4)
    H = 16
    while H < altura:
        H *= 2
    atlas = kit.pintar_atlas(reps, largura, H, pintor_arma(t, estilo), 4)
    tex_dir = os.path.join(JAVA, 'textures', 'item', 'reliquias')
    os.makedirs(tex_dir, exist_ok=True)
    kit.salvar_png_leve(atlas, os.path.join(tex_dir, f'{nome}.png'))
    modelo = kit.modelo_java(cs, f'corona:item/reliquias/{nome}', largura, H, display=display)
    kit.salvar_json(os.path.join(JAVA, 'models', 'item', 'reliquias', f'{nome}.json'), modelo)
    pv = kit.previa(cs, atlas, 0, 0, escala=10)
    os.makedirs(PREV, exist_ok=True)
    pv.save(os.path.join(PREV, f'{nome}.png'))
    if icone:
        arr = np.array(pv.convert('RGBA'))
        fundo = (np.abs(arr[..., 0].astype(int) - 28) < 3) & (np.abs(arr[..., 1].astype(int) - 30) < 3) & (np.abs(arr[..., 2].astype(int) - 36) < 3)
        arr[fundo, 3] = 0
        ic = Image.fromarray(arr, 'RGBA')
        ic.thumbnail((32, 32), Image.LANCZOS)
        quad = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
        quad.paste(ic, ((32 - ic.width) // 2, (32 - ic.height) // 2))
        bd = os.path.join(BEDROCK, 'textures', 'items', 'reliquias')
        os.makedirs(bd, exist_ok=True)
        quad.save(os.path.join(bd, f'{nome}.png'))
    return pv


def _m(nome):
    return {'type': 'minecraft:model', 'model': f'corona:item/reliquias/{nome}'}


def item_arco(tema):
    return {'model': {'type': 'minecraft:condition', 'property': 'minecraft:using_item',
                      'on_false': _m(f'arco_{tema}'),
                      'on_true': {'type': 'minecraft:range_dispatch', 'property': 'minecraft:use_duration', 'scale': 0.05,
                                  'entries': [{'threshold': 0.65, 'model': _m(f'arco_{tema}_p1')}, {'threshold': 0.9, 'model': _m(f'arco_{tema}_p2')}],
                                  'fallback': _m(f'arco_{tema}_p0')}}}


def item_besta(tema):
    return {'model': {'type': 'minecraft:select', 'property': 'minecraft:charge_type',
                      'cases': [{'when': 'arrow', 'model': _m(f'besta_{tema}_arrow')}, {'when': 'rocket', 'model': _m(f'besta_{tema}_arrow')}],
                      'fallback': {'type': 'minecraft:condition', 'property': 'minecraft:using_item',
                                   'on_false': _m(f'besta_{tema}'),
                                   'on_true': {'type': 'minecraft:range_dispatch', 'property': 'minecraft:crossbow/pull',
                                               'entries': [{'threshold': 0.58, 'model': _m(f'besta_{tema}_p1')}, {'threshold': 1.0, 'model': _m(f'besta_{tema}_p2')}],
                                               'fallback': _m(f'besta_{tema}_p0')}}}}


def gerar(id_, t):
    estilo = ESTILO.get(t['golpe'], 'aco')
    tema = id_.lower()
    sem = sum(ord(ch) for ch in id_)
    prevs = {}
    prevs['machado'] = _modelo(f'machado_{tema}', machado_cubos(estilo, sem), t, estilo, R.DISPLAY, icone=True)
    prevs['picareta'] = _modelo(f'picareta_{tema}', picareta_cubos(estilo, sem), t, estilo, R.DISPLAY, icone=True)
    prevs['maca'] = _modelo(f'maca_{tema}', maca_cubos(estilo, sem), t, estilo, R.DISPLAY)
    prevs['tridente'] = _modelo(f'tridente_{tema}', tridente_cubos(estilo, sem), t, estilo, R.DISPLAY)
    prevs['arco'] = _modelo(f'arco_{tema}', arco_cubos(estilo, sem), t, estilo, DISPLAY_ARCO)
    for k in range(3):
        _modelo(f'arco_{tema}_p{k}', arco_cubos(estilo, sem, k), t, estilo, DISPLAY_ARCO)
    prevs['besta'] = _modelo(f'besta_{tema}', besta_cubos(estilo, sem), t, estilo, DISPLAY_BESTA)
    for est in ('p0', 'p1', 'p2', 'arrow'):
        _modelo(f'besta_{tema}_{est}', besta_cubos(estilo, sem, est), t, estilo, DISPLAY_BESTA)
    for a in ('machado', 'picareta', 'maca', 'tridente'):
        kit.salvar_json(os.path.join(JAVA, 'items', 'item', 'reliquias', f'{a}_{tema}.json'), kit.item_java(f'corona:item/reliquias/{a}_{tema}'))
    kit.salvar_json(os.path.join(JAVA, 'items', 'item', 'reliquias', f'arco_{tema}.json'), item_arco(tema))
    kit.salvar_json(os.path.join(JAVA, 'items', 'item', 'reliquias', f'besta_{tema}.json'), item_besta(tema))
    return estilo, prevs


def geyser_extra():
    """Entradas extras do mapeamento Geyser (machado e picareta) e icones; usadas por reliquias.geyser_mapeamento."""
    itens, tex = {}, {}
    for id_, t in TEMAS.items():
        tema = id_.lower()
        for arma, mats in MATERIAIS.items():
            tex[f'corona_{arma}_{tema}'] = {'textures': f'textures/items/reliquias/{arma}_{tema}'}
            nome = {'machado': 'Machado', 'picareta': 'Picareta'}[arma]
            for mat in mats:
                itens.setdefault(f'minecraft:{mat}', []).append({
                    'type': 'definition',
                    'model': f'corona:item/reliquias/{arma}_{tema}',
                    'bedrock_identifier': f'corona:{arma}_{tema}_{mat.split("_")[0]}',
                    'display_name': f'{nome} {t["de"]}',
                    'bedrock_options': {'display_handheld': True, 'icon': f'corona_{arma}_{tema}', 'allow_offhand': True},
                })
    return itens, tex


if __name__ == '__main__':
    so = [s.upper() for s in sys.argv[1:]]
    folha = []
    for id_, t in TEMAS.items():
        if so and id_ not in so:
            continue
        est, prevs = gerar(id_, t)
        print('ok', id_, est)
        if len(folha) < 6:
            folha.append(kit.folha(list(prevs.values())))
    if folha:
        w = max(f.width for f in folha)
        h = sum(f.height for f in folha)
        img = Image.new('RGB', (w, h), (28, 30, 36))
        y = 0
        for f in folha:
            img.paste(f, (0, y))
            y += f.height
        img.save(os.path.join(PREV, '..', 'armas.png'))
        print('previa:', os.path.join(PREV, '..', 'armas.png'))
