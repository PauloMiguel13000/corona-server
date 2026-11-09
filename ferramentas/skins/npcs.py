"""Skins dos NPCs das masmorras (64x64, layout classico/Steve), desenhadas por codigo em cima do base.py.

Cada NPC e um dicionario de cores/opcoes; humano() pinta pele, rosto, cabelo, roupa e a camada de cima (chapeu, capuz,
avental, ombreiras...). Saida: skins/npcs/<id>.png (64x64) e uma folha de previa por NPC em /tmp/claude-0/skins-npcs/.
Uso: python3 npcs.py [pasta_saida]
"""
import os
import sys
import numpy as np
from PIL import Image

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), 'monstros'))
import base  # noqa: E402
from base import C, fbm, hash2, mix, ss  # noqa: E402

R = base.R


def grade(P):
    """Coordenadas inteiras de pixel de skin (coluna/linha dentro da face) e inteiros 3D."""
    s, t, w, h = P['s'], P['t'], P['w'], P['h']
    if P['face'] in ('top', 'bottom'):
        h = P['d']
    if P['face'] in ('right', 'left'):
        w = P['d']
    col = np.floor(s * w).astype(int)
    lin = np.floor(t * h).astype(int)
    return col, lin


def ruido_pele(P, forca=0.06, seed=1):
    return 1 + forca * (fbm(P['x'] * 0.9, P['y'] * 0.9, P['z'] * 0.9, oct=3, seed=seed) - 0.5) * 2


def sombra_lateral(P):
    """Lados e costas um pouco mais escuros, como luz vindo da frente/cima."""
    f = P['face']
    return {'front': 1.0, 'top': 1.05, 'left': 0.9, 'right': 0.9, 'back': 0.82, 'bottom': 0.75}[f]


def tecido(P, cor, seed=3, forca=0.08):
    rgb = base.cor_cheia(P['s'].shape, cor)
    n = fbm(P['x'] * 1.6, P['y'] * 1.6, P['z'] * 1.6, oct=3, seed=seed)
    rgb = rgb * (1 + forca * (n - 0.5) * 2)[..., None]
    return rgb * sombra_lateral(P)


def humano(P, camada, c):
    """Pinta uma face de uma parte. Devolve (rgb, alpha)."""
    parte, face = P['parte'], P['face']
    x, y, z = P['x'], P['y'], P['z']
    col, lin = grade(P)
    shape = P['s'].shape
    zero = np.zeros(shape)
    um = np.ones(shape)

    pele = C(c['pele'])
    cabelo = C(c['cabelo'])
    rgb = np.zeros(shape + (3,))
    alpha = zero.copy() if camada == 'over' else um.copy()

    if camada == 'base':
        # ---- pele em tudo
        rgb[:] = pele
        rgb *= ruido_pele(P)[..., None] * sombra_lateral(P)
        if parte == 'head':
            # cabelo: topo e linha do cabelo (frente ate a linha 'franja', lados/costas mais para baixo)
            if face == 'top':
                m = um if not c.get('careca') else zero
            elif face == 'front':
                m = (lin < c.get('franja', 2)).astype(float) if not c.get('careca') else zero
            elif face == 'back':
                m = (lin < c.get('nuca', 5)).astype(float) if not c.get('careca') else zero
            elif face in ('left', 'right'):
                m = (lin < c.get('lados', 3)).astype(float) if not c.get('careca') else zero
                # costeleta
                if not c.get('careca'):
                    m = np.maximum(m, ((lin < 5) & ((col >= 6) if face == 'right' else (col <= 1))).astype(float))
            else:
                m = zero
            hair = base.cor_cheia(shape, c['cabelo']) * (1 + 0.14 * (fbm(x * 2.2, y * 2.2, z * 2.2, oct=3, seed=9) - 0.5) * 2)[..., None]
            rgb = mix(rgb, hair * sombra_lateral(P), m)
            if face == 'front':
                # olhos (linha 4): branco nas colunas 2 e 5, iris nas 3 e 4
                olho_l = lin == 4
                branco = olho_l & ((col == 2) | (col == 5))
                iris = olho_l & ((col == 3) | (col == 4))
                rgb[branco] = C('#f4f4f4')
                rgb[iris] = C(c['olhos'])
                if c.get('olhos_brilho'):
                    rgb[iris] = np.clip(C(c['olhos']) * 1.35, 0, 1)
                # sobrancelha (linha 3) um pouco mais escura que o cabelo
                sob = (lin == 3) & ((col == 2) | (col == 3) | (col == 4) | (col == 5))
                rgb[sob] = np.clip(cabelo * 0.75, 0, 1)
                # nariz (linha 5, colunas 3-4) um pouco mais escuro
                nariz = (lin == 5) & ((col == 3) | (col == 4))
                rgb[nariz] = np.clip(pele * 0.82, 0, 1)
                # boca (linha 6, colunas 3-4)
                boca = (lin == 6) & ((col == 3) | (col == 4))
                rgb[boca] = C(c.get('boca', '#9a5a50'))
                if c.get('bigode'):
                    big = (lin == 6) & (col >= 2) & (col <= 5)
                    rgb[big] = cabelo
                    rgb[boca] = C(c.get('boca', '#9a5a50'))
                    rgb[(lin == 6) & ((col == 2) | (col == 5))] = cabelo
                if c.get('barba'):
                    barba = (lin == 7) & (col >= 1) & (col <= 6)
                    rgb[barba] = cabelo * 0.95
                    rgb[(lin == 6) & ((col == 1) | (col == 6))] = cabelo
                if c.get('cicatriz'):
                    cic = ((lin == 3) | (lin == 4) | (lin == 5)) & (col == 6)
                    rgb[cic] = np.clip(pele * 0.7 + C('#c06a60') * 0.3, 0, 1)
                if c.get('pintura'):
                    pin = (lin == 5) & ((col == 1) | (col == 2) | (col == 5) | (col == 6))
                    rgb[pin] = C(c['pintura'])
            if face in ('left', 'right') and c.get('barba'):
                rgb[(lin == 7) & (((col >= 4) if face == 'right' else (col <= 3)))] = cabelo * 0.95
            if face == 'bottom' and c.get('barba'):
                rgb[(lin >= 4)] = cabelo * 0.95
        elif parte == 'body':
            rgb = tecido(P, c['camisa'], seed=4)
            if c.get('cinto'):
                cinto = (lin == 8) | (lin == 9) if face in ('front', 'back', 'left', 'right') else np.zeros(shape, bool)
                rgb[cinto] = C(c['cinto'])
                if face == 'front':
                    rgb[(lin == 8) & ((col == 3) | (col == 4))] = C(c.get('fivela', '#d8b23c'))
            if c.get('gola') and face == 'front':
                rgb[(lin == 0) & (col >= 2) & (col <= 5)] = C(c['gola'])
                rgb[(lin == 1) & ((col == 2) | (col == 5))] = C(c['gola'])
            if c.get('listra_peito') and face == 'front':
                rgb[(lin >= 2) & (lin <= 7) & ((col == 3) | (col == 4))] = C(c['listra_peito'])
        elif parte in ('rarm', 'larm'):
            manga = c.get('manga', 9)  # linhas de manga (12 = manga comprida)
            cor_m = c.get('mangas', c['camisa'])
            rgb_m = tecido(P, cor_m, seed=5)
            m = (lin < manga).astype(float) if face not in ('top', 'bottom') else (um if face == 'top' else zero)
            rgb = mix(rgb, rgb_m, m)
            if c.get('luvas') and face != 'top':
                lm = (lin >= 9).astype(float) if face != 'bottom' else um
                rgb = mix(rgb, tecido(P, c['luvas'], seed=6), lm)
            if c.get('bracelete') and face not in ('top', 'bottom'):
                rgb[lin == manga] = C(c['bracelete'])
        else:  # pernas
            rgb = tecido(P, c['calca'], seed=7)
            bota = c.get('bota_alt', 3)
            if face != 'top':
                bm = (lin >= 12 - bota).astype(float) if face != 'bottom' else um
                rgb = mix(rgb, tecido(P, c['botas'], seed=8), bm)
        return np.clip(rgb, 0, 1), alpha

    # ---- camada de cima (transparente por padrao)
    if parte == 'head':
        tipo = c.get('chapeu')
        if tipo == 'capuz':
            cor = tecido(P, c['chapeu_cor'], seed=11)
            if face == 'top':
                m = um
            elif face == 'front':
                m = ((lin <= 1) | ((lin <= 7) & ((col == 0) | (col == 7)))).astype(float)
            elif face == 'back':
                m = um
            else:
                m = ((lin <= 7) & ~((col >= 5) if face == 'right' else (col <= 2)) | (lin <= 2)).astype(float)
            rgb, alpha = cor, m
        elif tipo == 'cabelo_volume':
            cor = tecido(P, c['cabelo'], seed=12, forca=0.14)
            if face == 'top':
                m = um
            elif face == 'front':
                m = (lin <= c.get('franja', 2) - 1).astype(float)
            elif face == 'back':
                m = (lin <= c.get('nuca_over', 7)).astype(float)
            else:
                m = (lin <= c.get('lados_over', 3)).astype(float)
            if c.get('coque') and face in ('top', 'back', 'left', 'right'):
                if face == 'back':
                    m = np.maximum(m, ((lin <= 2) & (col >= 2) & (col <= 5)).astype(float))
            rgb, alpha = cor, m
        elif tipo == 'chapeu':
            cor = tecido(P, c['chapeu_cor'], seed=13)
            if face == 'top':
                m = um
            else:
                m = (lin <= 1).astype(float)
                if face == 'front':
                    m = np.maximum(m, (lin == 2).astype(float))  # aba na frente
            rgb, alpha = cor, m
            if face == 'front':
                rgb[(lin == 1) & (col >= 1) & (col <= 6)] = C(c.get('chapeu_faixa', '#2a1d14'))
        elif tipo == 'turbante':
            cor = tecido(P, c['chapeu_cor'], seed=14)
            m = um if face == 'top' else (lin <= 2).astype(float)
            if face == 'front':
                m = np.maximum(m, ((lin == 3) & ((col == 0) | (col == 7))).astype(float))
            rgb, alpha = cor, m
            if face == 'front':
                rgb[(lin == 1) & ((col == 3) | (col == 4))] = C(c.get('joia', '#e6c040'))
        elif tipo == 'bandana':
            cor = tecido(P, c['chapeu_cor'], seed=15)
            m = (lin <= 1).astype(float) if face != 'top' else um
            if face == 'bottom':
                m = zero
            rgb, alpha = cor, m
        elif tipo == 'tiara':
            cor = base.cor_cheia(shape, c['chapeu_cor'])
            m = (lin == 2).astype(float) if face in ('front', 'back', 'left', 'right') else zero
            rgb, alpha = cor, m
            if face == 'front':
                rgb[(lin == 2) & ((col == 3) | (col == 4))] = C(c.get('joia', '#60e0ff'))
        if c.get('oculos') and face == 'front':
            oc = (lin == 4) & ((col == 1) | (col == 6))
            arm = (lin == 3) & (col >= 1) & (col <= 6)
            ponte = (lin == 4) & ((col == 3) | (col == 4))
            rgb[oc | ponte | arm] = C(c.get('oculos_cor', '#c8a04a'))
            alpha = np.maximum(alpha, (oc | ponte | arm).astype(float))
        if c.get('mascara') and face == 'front':
            ms = (lin >= 3) & (lin <= 5) & ~((lin == 4) & ((col == 2) | (col == 3) | (col == 4) | (col == 5)))
            rgb[ms] = C(c['mascara'])
            alpha = np.maximum(alpha, ms.astype(float))
        if c.get('barba_volume') and face in ('front',):
            bv = (lin == 7) & (col >= 1) & (col <= 6)
            rgb[bv] = cabelo * 0.9
            alpha = np.maximum(alpha, bv.astype(float))
        return np.clip(rgb, 0, 1), alpha

    if parte == 'body':
        if c.get('avental'):
            cor = tecido(P, c['avental'], seed=16)
            m = ((lin >= 2) & (col >= 1) & (col <= 6)).astype(float) if face == 'front' else zero
            if face == 'front':
                cor[(lin >= 2) & (lin <= 3) & (col >= 1) & (col <= 6)] = C(c['avental']) * 0.85
                m = np.maximum(m, ((lin >= 0) & ((col == 2) | (col == 5))).astype(float))  # alcas
            rgb, alpha = cor, m
        elif c.get('capa'):
            cor = tecido(P, c['capa'], seed=17)
            if face == 'back':
                m = um
            elif face in ('left', 'right'):
                m = (lin <= 11).astype(float) * 0.0
                m = ((col == 3) if face == 'left' else (col == 0)).astype(float)
            elif face == 'front':
                m = ((lin <= 1) | (col == 0) | (col == 7)).astype(float)
            else:
                m = zero
            rgb, alpha = cor, m
            if c.get('broche') and face == 'front':
                rgb[(lin == 1) & ((col == 3) | (col == 4))] = C(c['broche'])
        elif c.get('colete'):
            cor = tecido(P, c['colete'], seed=18)
            m = (((col <= 1) | (col >= 6)) & (lin <= 10)).astype(float) if face == 'front' else (um if face == 'back' else (lin <= 10).astype(float))
            if face == 'top':
                m = um
            rgb, alpha = cor, m
        if c.get('ombreiras') and face == 'top':
            rgb[(col <= 1) | (col >= 6)] = C(c['ombreiras'])
            alpha = np.maximum(alpha, ((col <= 1) | (col >= 6)).astype(float))
        if c.get('faixa_peito') and face == 'front':
            fx = (lin + col) % 9 == 4
            fx = fx & (lin >= 1) & (lin <= 9)
            rgb[fx] = C(c['faixa_peito'])
            alpha = np.maximum(alpha, fx.astype(float))
        if c.get('colar') and face == 'front':
            cl = (lin == 1) & (col >= 2) & (col <= 5)
            rgb[cl] = C(c['colar'])
            alpha = np.maximum(alpha, cl.astype(float))
        return np.clip(rgb, 0, 1), alpha

    if parte in ('rarm', 'larm'):
        if c.get('ombreiras') and face not in ('bottom',):
            m = (lin <= 1).astype(float) if face != 'top' else um
            cor = tecido(P, c['ombreiras'], seed=19)
            rgb, alpha = cor, m
        if c.get('luva_over'):
            m2 = (lin >= 9).astype(float) if face not in ('top', 'bottom') else (um if face == 'bottom' else zero)
            cor2 = tecido(P, c['luva_over'], seed=20)
            rgb = mix(rgb, cor2, m2)
            alpha = np.maximum(alpha, m2)
        return np.clip(rgb, 0, 1), alpha

    # pernas: saia/robe ou bota alta
    if c.get('robe'):
        cor = tecido(P, c['robe'], seed=21)
        m = (lin <= c.get('robe_alt', 8)).astype(float) if face not in ('top', 'bottom') else (um if face == 'top' else zero)
        rgb, alpha = cor, m
    if c.get('bota_over'):
        m2 = (lin >= 12 - c.get('bota_alt', 3) - 1).astype(float) if face not in ('top', 'bottom') else zero
        rgb = mix(rgb, tecido(P, c['bota_over'], seed=22), m2)
        alpha = np.maximum(alpha, m2)
    return np.clip(rgb, 0, 1), alpha


NPCS = {
    'ferreiro': dict(pele='#5a3a26', cabelo='#8a8078', olhos='#4a2a16', careca=True, barba=True, bigode=True, barba_volume=True,
                     camisa='#6e6a66', mangas='#6e6a66', manga=4, calca='#3a3430', botas='#2a211a', cinto='#4a3220',
                     avental='#7a4a28', luvas='#3a2a1e', chapeu='bandana', chapeu_cor='#a8302a'),
    'armeira': dict(pele='#c89a78', cabelo='#1e1a1a', olhos='#2a5a8a', franja=2, chapeu='cabelo_volume', coque=True, nuca_over=3,
                    camisa='#2c4f7a', mangas='#2c4f7a', manga=12, calca='#3a3a44', botas='#26221e', cinto='#3a2a1a',
                    ombreiras='#a8aeb8', luva_over='#8a8e96', bracelete='#a8aeb8'),
    'cacadora': dict(pele='#9a6a46', cabelo='#3a2414', olhos='#4a9a3a', franja=1, pintura='#2a5a2a',
                     camisa='#5a3a22', mangas='#2e5a2e', manga=12, calca='#3a4a2a', botas='#2e2218', cinto='#2a1a10',
                     chapeu='capuz', chapeu_cor='#2e5a2e', capa='#2e5a2e', faixa_peito='#4a3220', luva_over='#4a3220'),
    'alquimista': dict(pele='#e8c8b0', cabelo='#d8d4e8', olhos='#7a3aa8', franja=2, chapeu='cabelo_volume', nuca_over=7, lados_over=5,
                       camisa='#4a2a7a', mangas='#4a2a7a', manga=12, calca='#2e1a4a', botas='#1e1226', cinto='#c8a040',
                       oculos=True, oculos_cor='#c8a04a', robe='#4a2a7a', robe_alt=7, colar='#c8a040', listra_peito='#c8a040'),
    'guia': dict(pele='#c08a62', cabelo='#4a2e18', olhos='#6a4a2a', franja=2, barba=False,
                 camisa='#c89a38', mangas='#6a4a2e', manga=12, calca='#5a4a3a', botas='#3a2a1a', cinto='#3a2a1a',
                 chapeu='chapeu', chapeu_cor='#5a3a22', chapeu_faixa='#c89a38', faixa_peito='#3a2a1a', gola='#e8c060'),
    'mestre': dict(pele='#d8b89a', cabelo='#e8e8f0', olhos='#40c8e8', olhos_brilho=True, franja=2, barba=True, bigode=True, barba_volume=True,
                   camisa='#1e4a7a', mangas='#1e4a7a', manga=12, calca='#1a2e5a', botas='#1a1a2a', cinto='#60c8e8',
                   chapeu='tiara', chapeu_cor='#60c8e8', joia='#ffffff', robe='#1e4a7a', robe_alt=8, listra_peito='#60c8e8'),
    'vendedor': dict(pele='#a8724a', cabelo='#2a1a10', olhos='#3a2a1a', franja=2, bigode=True,
                     camisa='#d87a20', mangas='#f0e0c0', manga=12, calca='#5a3a28', botas='#3a2a1a', cinto='#e6c040', fivela='#ffffff',
                     chapeu='turbante', chapeu_cor='#e8e0d0', joia='#e02020', colete='#8a2a1a', colar='#e6c040'),
    'mercador': dict(pele='#c89a78', cabelo='#6a4a2a', olhos='#5a3a1a', franja=2,
                     camisa='#8a7a5a', mangas='#8a7a5a', manga=9, calca='#4a3a2a', botas='#2a1e14', cinto='#3a2a1a',
                     chapeu='capuz', chapeu_cor='#6a4a2a', capa='#6a4a2a', faixa_peito='#3a2a1a'),
    'veterano': dict(pele='#b88a68', cabelo='#9a9a9a', olhos='#4a4a4a', franja=2, cicatriz=True, barba=True,
                     camisa='#3a5a3a', mangas='#3a5a3a', manga=12, calca='#2e2e2e', botas='#1e1a16', cinto='#5a3a1a', fivela='#c0c0c0',
                     capa='#2e4a2e', broche='#c0c0c0', ombreiras='#6a6a6a'),
    'abismo': dict(pele='#9ab0c8', cabelo='#1a1e2e', olhos='#ffffff', olhos_brilho=True, franja=3,
                   camisa='#141828', mangas='#141828', manga=12, calca='#0e1020', botas='#080810', cinto='#2a3a6a',
                   chapeu='capuz', chapeu_cor='#141828', capa='#141828', luva_over='#1e2238', colar='#4a6ad8'),
    'lendario': dict(pele='#e0c0a0', cabelo='#f0f0f8', olhos='#e6c040', olhos_brilho=True, franja=2, barba=True,
                     camisa='#5a1a7a', mangas='#5a1a7a', manga=12, calca='#3a1050', botas='#1a0a26', cinto='#e6c040', fivela='#ffffff',
                     chapeu='tiara', chapeu_cor='#e6c040', joia='#ff40a0', robe='#5a1a7a', robe_alt=9, listra_peito='#e6c040',
                     ombreiras='#e6c040', colar='#e6c040'),
    'guia_nether': dict(pele='#8a4a3a', cabelo='#1a0e0a', olhos='#ff6020', olhos_brilho=True, franja=2, cicatriz=True,
                        camisa='#2a1410', mangas='#2a1410', manga=12, calca='#1e0e0a', botas='#0e0604', cinto='#ff6020',
                        chapeu='capuz', chapeu_cor='#3a1a12', capa='#3a1a12', luva_over='#4a2a1a', colar='#ff6020'),
    'guia_fim': dict(pele='#d8c8e8', cabelo='#f0f0f0', olhos='#c040ff', olhos_brilho=True, franja=2,
                     camisa='#1e1030', mangas='#1e1030', manga=12, calca='#160c26', botas='#0a0612', cinto='#c040ff',
                     chapeu='capuz', chapeu_cor='#2a1848', capa='#2a1848', luva_over='#3a2460', colar='#c040ff', faixa_peito='#5a3a9a'),
}


def gerar(saida, previas):
    os.makedirs(saida, exist_ok=True)
    os.makedirs(previas, exist_ok=True)
    for nome, c in NPCS.items():
        img = base.montar(lambda P, camada: humano(P, camada, c), nome)
        pequena = base.reduzir(img, R, corte=True)
        Image.fromarray(pequena).save(os.path.join(saida, nome + '.png'))
        folha = base.previa(np.repeat(np.repeat(pequena, R, axis=0), R, axis=1))
        folha.save(os.path.join(previas, nome + '.png'))
        print('ok', nome)


if __name__ == '__main__':
    saida = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), 'npcs')
    gerar(saida, '/tmp/claude-0/skins-npcs')
