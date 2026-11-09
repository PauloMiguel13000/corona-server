"""Skins (64x64) dos animatronicos ORIGINAIS do minigame Turno da Noite: Capi (capivara), Tuca (tucano), Jaca (onca)
e Boto (boto-cor-de-rosa). Bichos de pelucia mecanicos: pelo/plumas gastos, olhos de botao, boca com dentes, juntas de
metal a mostra. Saida: skins/animatronicos/<id>.png + previas em /tmp/claude-0/skins-anim/."""
import os
import sys
import numpy as np
from PIL import Image

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), 'monstros'))
import base  # noqa: E402
from base import C, fbm, mix  # noqa: E402
from npcs import grade, sombra_lateral, tecido  # noqa: E402

R = base.R


def pelucia(P, cor, seed, forca=0.16):
    """Pelo de pelucia: ruido mais forte e manchas de desgaste (mais claras)."""
    rgb = tecido(P, cor, seed=seed, forca=forca)
    gasto = fbm(P['x'] * 0.7, P['y'] * 0.7, P['z'] * 0.7, oct=2, seed=seed + 50)
    m = np.clip((gasto - 0.62) * 6, 0, 1)
    return mix(rgb, np.clip(rgb * 1.35 + 0.08, 0, 1), m * 0.6)


def anim(P, camada, c):
    parte, face = P['parte'], P['face']
    col, lin = grade(P)
    shape = P['s'].shape
    zero = np.zeros(shape)
    um = np.ones(shape)
    metal = C('#6a6e72')
    metal_esc = C('#3a3d40')
    rgb = np.zeros(shape + (3,))
    alpha = zero.copy() if camada == 'over' else um.copy()

    if camada == 'base':
        rgb[:] = pelucia(P, c['pelo'], 3)
        if c.get('manchas'):
            sp = fbm(P['x'] * 3.2, P['y'] * 3.2, P['z'] * 3.2, oct=2, seed=21)
            mm = np.clip((sp - 0.60) * 10, 0, 1)
            rgb = mix(rgb, base.cor_cheia(shape, c['manchas']) * sombra_lateral(P), mm)
        if parte == 'head':
            if face == 'front':
                # focinho/bico (cor secundaria) nas linhas 4-7, colunas 2-5
                foc = (lin >= 4) & (lin <= 7) & (col >= 2) & (col <= 5)
                rgb[foc] = pelucia(P, c['focinho'], 4)[foc]
                # olhos de botao: linha 2-3, brancos grandes com pupila preta pequena (olhar fixo)
                olho = ((lin == 2) | (lin == 3)) & ((col == 1) | (col == 2) | (col == 5) | (col == 6))
                rgb[olho] = C('#f2f2f2')
                pup = (lin == 3) & ((col == 2) | (col == 5))
                rgb[pup] = C(c.get('pupila', '#101010'))
                if c.get('olho_brilho'):
                    rgb[pup] = C(c['olho_brilho'])
                # boca com dentes: linha 6 dentes, linha 7 escura
                boca = (lin == 7) & (col >= 2) & (col <= 5)
                rgb[boca] = C('#1a0a0a')
                dentes = (lin == 6) & (col >= 2) & (col <= 5)
                rgb[dentes] = C('#e8e8e0')
                rgb[(lin == 6) & ((col == 3) | (col == 4)) & c.get('bico', False)] = C(c['focinho'])
                # nariz
                rgb[(lin == 4) & ((col == 3) | (col == 4))] = C(c.get('nariz', '#201010'))
                # rasgo de metal na testa (endoesqueleto a mostra)
                if c.get('rasgo'):
                    rgb[(lin == 0) & ((col == 1) | (col == 2))] = metal
                    rgb[(lin == 1) & (col == 1)] = metal_esc
            if face in ('left', 'right') and c.get('rasgo'):
                rgb[(lin == 5) & ((col == 2) | (col == 3))] = metal
        elif parte == 'body':
            if c.get('peito'):
                m = ((lin >= 1) & (lin <= 9) & (col >= 2) & (col <= 5)).astype(float) if face == 'front' else zero
                rgb = mix(rgb, pelucia(P, c['peito'], 5), m)
            if face == 'front':
                if c.get('gravata'):
                    rgb[(lin == 1) & ((col == 2) | (col == 3) | (col == 4) | (col == 5))] = C(c['gravata'])
                    rgb[(lin == 2) & ((col == 3) | (col == 4))] = C(c['gravata'])
                if c.get('botoes'):
                    rgb[((lin == 4) | (lin == 6) | (lin == 8)) & (col == 3)] = C(c['botoes'])
                # costura aberta com metal
                rgb[(lin >= 9) & (lin <= 11) & (col == 6)] = metal
                rgb[(lin == 10) & (col == 5)] = metal_esc
            if face == 'back':
                rgb[(lin >= 3) & (lin <= 6) & (col == 3)] = metal
        elif parte in ('rarm', 'larm'):
            # maos de metal (endoesqueleto), pelo rasgado no cotovelo
            m = (lin >= 9).astype(float) if face != 'top' else zero
            if face == 'bottom':
                m = um
            rgb = mix(rgb, base.cor_cheia(shape, '#6a6e72') * sombra_lateral(P), m)
            rgb[(lin == 5) & (col == 1)] = metal_esc
        else:
            m = (lin >= 10).astype(float) if face != 'top' else zero
            if face == 'bottom':
                m = um
            rgb = mix(rgb, base.cor_cheia(shape, '#4a4e52') * sombra_lateral(P), m)
            if face == 'front':
                rgb[(lin == 4) & (col == 2)] = metal
        return np.clip(rgb, 0, 1), alpha

    # camada de cima: orelhas/crista/barbatana (topo e lados), bochechas
    if parte == 'head':
        tipo = c.get('orelhas')
        cor = pelucia(P, c.get('orelha_cor', c['pelo']), 7)
        if tipo == 'redondas':  # capivara / onca
            m = zero
            if face == 'top':
                m = (((col <= 1) | (col >= 6)) & (lin <= 2)).astype(float)
            if face in ('front', 'back'):
                m = ((lin <= 0) & ((col <= 1) | (col >= 6))).astype(float)
            rgb, alpha = cor, m
        elif tipo == 'crista':  # tucano: penacho em cima
            m = zero
            if face == 'top':
                m = ((col >= 3) & (col <= 4)).astype(float)
            if face in ('left', 'right', 'back', 'front'):
                m = ((lin == 0) & (col >= 3) & (col <= 4)).astype(float)
            rgb, alpha = cor, m
        elif tipo == 'barbatana':  # boto: barbatana no topo e "bico" longo
            m = zero
            if face == 'top':
                m = ((col >= 3) & (col <= 4) & (lin >= 2)).astype(float)
            if face in ('left', 'right'):
                m = ((lin <= 1) & (col >= 4) & (col <= 6)).astype(float)
            rgb, alpha = cor, m
        if face == 'front':
            # bico/focinho saliente na camada de cima (linhas 4-6)
            if c.get('bico'):
                bm = (lin >= 4) & (lin <= 6) & (col >= 2) & (col <= 5)
                rgb[bm] = pelucia(P, c['focinho'], 8)[bm]
                rgb[(lin == 6) & (col >= 2) & (col <= 5)] = C('#1a0a0a')
                alpha = np.maximum(alpha, bm.astype(float))
            if c.get('bochecha'):
                bc = (lin == 5) & ((col == 0) | (col == 7))
                rgb[bc] = C(c['bochecha'])
                alpha = np.maximum(alpha, bc.astype(float))
        return np.clip(rgb, 0, 1), alpha
    if parte == 'body' and c.get('colete'):
        cor = pelucia(P, c['colete'], 9)
        m = ((col <= 1) | (col >= 6)).astype(float) if face == 'front' else (um if face == 'back' else zero)
        if face in ('left', 'right'):
            m = um
        rgb, alpha = cor, m
        return np.clip(rgb, 0, 1), alpha
    return np.clip(rgb, 0, 1), alpha


ANIM = {
    'capi': dict(pelo='#8a6a3a', focinho='#c8a878', nariz='#2a1a10', orelhas='redondas', orelha_cor='#6a4a24', gravata='#2a2a8a',
                 botoes='#2a2a8a', peito='#b89060', rasgo=True),
    'tuca': dict(pelo='#1a1a1e', focinho='#f0a020', nariz='#102030', orelhas='crista', orelha_cor='#2a2a30', bico=True, peito='#f0e040',
                 bochecha='#3a90d0', rasgo=False, pupila='#ffffff', olho_brilho='#e8f0ff'),
    'jaca': dict(pelo='#d8a030', focinho='#f0e0b0', nariz='#2a1010', orelhas='redondas', orelha_cor='#b07a20', peito='#f0e8c8',
                 colete='#3a2a1a', rasgo=True, pupila='#80ff40', olho_brilho='#a0ff60', manchas='#3a2410'),
    'boto': dict(pelo='#e890b0', focinho='#f0b8c8', nariz='#4a2030', orelhas='barbatana', orelha_cor='#d070a0', bico=True, peito='#f8d0dc',
                 rasgo=True, pupila='#ffe040', olho_brilho='#ffee80'),
}


def gerar(saida, previas):
    os.makedirs(saida, exist_ok=True)
    os.makedirs(previas, exist_ok=True)
    for nome, c in ANIM.items():
        img = base.montar(lambda P, camada: anim(P, camada, c), nome)
        pequena = base.reduzir(img, R, corte=True)
        Image.fromarray(pequena).save(os.path.join(saida, nome + '.png'))
        base.previa(np.repeat(np.repeat(pequena, R, axis=0), R, axis=1)).save(os.path.join(previas, nome + '.png'))
        print('ok', nome)


if __name__ == '__main__':
    gerar(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'animatronicos'), '/tmp/claude-0/skins-anim')
