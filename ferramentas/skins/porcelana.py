"""Skin 64x64 do "Porcelana": a criatura ORIGINAL do minigame da floresta (no lugar do personagem de terceiros).

Desenho proprio: mascara de porcelana rachada com SETE olhos (dois grandes e cinco pequenos, todos fundos e escuros com
um ponto vermelho), capuz e manto longos de pano escuro com musgo e liquen, cinto de corda, um medalhao de porcelana com
um olho no peito e maos de porcelana com dedos compridos e pretos. No jogo ele fica 1,3x mais alto (atributo de escala).
Uso: python3 -I porcelana.py <saida.png> [previa.png]
"""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), 'monstros'))
import base  # noqa: E402
from base import C, fbm, mix  # noqa: E402

R = base.R
PORC = C('#ebe6dc')
PORC2 = C('#cfc7b8')
PANO = C('#1d201b')
PANO2 = C('#2a2f25')
MUSGO = C('#4b5a2c')
LIQUEN = C('#8a9a5a')
CORDA = C('#6b5236')
PRETO = C('#07070a')
VERMELHO = C('#c0141c')


def luz(P):
    return {'front': 1.0, 'top': 1.06, 'left': 0.88, 'right': 0.88, 'back': 0.8, 'bottom': 0.7}[P['face']]


def px(P):
    """Coordenadas em pixels de skin dentro da face (u para a direita, v para baixo)."""
    w = P['d'] if P['face'] in ('left', 'right') else P['w']
    h = P['d'] if P['face'] in ('top', 'bottom') else P['h']
    return P['s'] * w, P['t'] * h, w, h


def rachaduras(P, seed, forca=1.0, escala=1.3):
    r = fbm(P['x'] * escala, P['y'] * escala, P['z'] * escala, oct=4, seed=seed)
    linha = np.clip(1 - np.abs(r - 0.5) / 0.035, 0, 1)
    return linha * forca


def pano(P, seed=3):
    u, v, w, h = px(P)
    n = fbm(P['x'] * 1.1, P['y'] * 0.6, P['z'] * 1.1, oct=4, seed=seed)
    dobras = 0.5 + 0.5 * np.sin(P['x'] * 2.2 + P['z'] * 2.2 + n * 3)
    cor = mix(PANO, PANO2, dobras * 0.6 + n * 0.4)
    musgo = fbm(P['x'] * 0.8 + 7, P['y'] * 0.8, P['z'] * 0.8, oct=3, seed=seed + 5)
    baixo = np.clip((14 - P['y']) / 14, 0, 1) * 0.35
    cor = mix(cor, MUSGO, np.clip((musgo - 0.62 + baixo) * 4, 0, 1) * 0.8)
    liq = fbm(P['x'] * 3, P['y'] * 3, P['z'] * 3, oct=2, seed=seed + 9)
    cor = mix(cor, LIQUEN, np.clip((liq - 0.78) * 6, 0, 1) * 0.7)
    return cor * luz(P)


def porcelana(P, seed=11):
    n = fbm(P['x'] * 2, P['y'] * 2, P['z'] * 2, oct=3, seed=seed)
    cor = mix(PORC, PORC2, n * 0.6)
    cor = mix(cor, C('#5a544c'), rachaduras(P, seed + 1, 0.9))
    return cor * luz(P)


def olho(u, v, cx, cy, r):
    d = np.hypot(u - cx, v - cy)
    buraco = d < r
    pupila = d < r * 0.38
    return buraco, pupila


def pintar(P, camada):
    parte, face = P['parte'], P['face']
    u, v, w, h = px(P)
    shape = P['s'].shape
    um = np.ones(shape)
    zero = np.zeros(shape)
    if camada == 'base':
        if parte == 'head':
            if face == 'front':
                cor = porcelana(P)
                # racha grande do topo ate o queixo
                tremida = 4.1 + 0.35 * np.sin(v * 2.3) + 0.2 * np.sin(v * 5.1)
                racha = np.clip(1 - np.abs(u - tremida) / 0.12, 0, 1) * (v < 7.4)
                cor = mix(cor, C('#3a342e'), racha)
                for cx, cy, r in ((2.6, 3.6, 0.95), (5.5, 3.5, 0.95), (1.4, 1.9, 0.5), (6.7, 2.0, 0.5), (4.1, 1.5, 0.45),
                                  (1.8, 5.7, 0.45), (6.2, 5.9, 0.5)):
                    b, p = olho(u, v, cx, cy, r)
                    cor = np.where(b[..., None], PRETO * um[..., None], cor)
                    cor = np.where(p[..., None], VERMELHO * um[..., None], cor)
                # fenda costurada no lugar da boca
                boca = (np.abs(v - 6.9) < 0.12) & (u > 2.8) & (u < 5.4)
                pontos = (np.abs(v - 6.9) < 0.45) & ((np.floor(u * 2) % 2) == 0) & (u > 3.0) & (u < 5.2)
                cor = np.where((boca | pontos)[..., None], C('#2a2420') * um[..., None], cor)
                return cor, um
            return pano(P, 4), um
        if parte == 'body':
            cor = pano(P, 6)
            # cinto de corda
            corda = np.abs(P['y'] - 17.2) < 0.55
            torc = (np.floor(P['x'] * 2 + P['y'] * 2) % 2) == 0
            cor = np.where(corda[..., None], (CORDA * np.where(torc, 1.0, 0.8)[..., None]) * luz(P), cor)
            if face == 'front':
                # medalhao de porcelana com um olho
                d = np.hypot(u - 4, v - 3.4)
                med = d < 1.5
                cor = np.where(med[..., None], porcelana(P, 21), cor)
                cor = np.where((np.abs(d - 1.5) < 0.18)[..., None], C('#2a2420') * um[..., None], cor)
                b, p = olho(u, v, 4, 3.4, 0.55)
                cor = np.where(b[..., None], PRETO * um[..., None], cor)
                cor = np.where(p[..., None], VERMELHO * um[..., None], cor)
            return cor, um
        if parte in ('rarm', 'larm'):
            cor = pano(P, 8)
            mao = P['y'] < 14.6
            cor = np.where(mao[..., None], porcelana(P, 31), cor)
            if face in ('front', 'back', 'left', 'right', 'bottom'):
                dedos = mao & (P['y'] < 13.4) & ((np.floor(u * 1.5) % 2) == 0)
                cor = np.where(dedos[..., None], PRETO * um[..., None], cor)
            return cor, um
        # pernas: manto por cima, pes de porcelana rachada
        cor = pano(P, 10)
        pe = P['y'] < 1.2
        cor = np.where(pe[..., None], porcelana(P, 41) * 0.8, cor)
        return cor, um
    # ---- camada de cima
    if parte == 'head':
        if face == 'front':
            borda = (u < 0.9) | (u > 7.1) | (v < 0.9)
            return pano(P, 5), borda * 1.0
        if face == 'bottom':
            return pano(P, 5), zero
        rasgo = fbm(P['x'] * 2, P['y'] * 2, P['z'] * 2, oct=2, seed=13) > 0.32
        return pano(P, 5) * 0.92, rasgo * 1.0
    if parte == 'body':
        if face in ('top', 'bottom'):
            return pano(P, 7), zero
        franja = v > h - (0.9 + 0.8 * np.abs(np.sin(u * 3.7 + 1)))
        musgoso = fbm(P['x'] * 1.5, P['y'] * 1.5, P['z'] * 1.5, oct=2, seed=17) > 0.55
        return pano(P, 7) * 0.95, (musgoso & ~franja) * 1.0
    if parte in ('rleg', 'lleg'):
        if face in ('top', 'bottom'):
            return pano(P, 9), zero
        # barra do manto rasgada cobrindo as pernas
        fim = 2.2 + 1.6 * np.abs(np.sin(u * 2.9 + (0 if parte == 'rleg' else 1.7)))
        return pano(P, 9) * 0.9, (P['y'] > fim) * 1.0
    # mangas compridas e rasgadas
    if face in ('top', 'bottom'):
        return pano(P, 11), zero
    manga = P['y'] > 15.0 + 0.8 * np.abs(np.sin(u * 4.1))
    return pano(P, 11) * 0.95, manga * 1.0


# rosto 8x8 desenhado pixel a pixel (sete olhos: 2 grandes com pupila vermelha e 5 pequenos)
ROSTO = ["PPPPPPPP",
         "PBPPCPBP",
         "PPPBCPPP",
         "PBBPCBBP",
         "PBRPpRBP",
         "pPPPCPPp",
         "PBPCPPBP",
         "pPkkkkPp"]
# medalhao no peito (colunas 2..5, linhas 2..5 da frente do corpo 8x12)
MEDALHAO = ["xppx",
            "pBRp",
            "pPBp",
            "xppx"]
CORES = {'P': '#ebe6dc', 'p': '#c9c1b2', 'B': '#07070a', 'R': '#b3121b', 'C': '#4a443c', 'k': '#2a2420'}


def detalhar(pequena):
    rng = np.random.default_rng(7)
    out = pequena.copy()
    for y, linha in enumerate(ROSTO):
        for x, ch in enumerate(linha):
            c = np.array(C(CORES[ch])) * (1 + (rng.random() - 0.5) * (0.08 if ch in 'Pp' else 0.0))
            out[8 + y, 8 + x, :3] = (np.clip(c, 0, 1) * 255).astype(np.uint8)
            out[8 + y, 8 + x, 3] = 255
    for y, linha in enumerate(MEDALHAO):
        for x, ch in enumerate(linha):
            if ch == 'x':
                continue
            c = np.array(C(CORES[ch]))
            out[20 + 2 + y, 20 + 2 + x, :3] = (c * 255).astype(np.uint8)
            out[20 + 2 + y, 20 + 2 + x, 3] = 255
    return out


def gerar(saida, previa=None):
    img = base.montar(pintar, 'porcelana')
    pequena = detalhar(base.reduzir(img, R, corte=True))
    os.makedirs(os.path.dirname(os.path.abspath(saida)), exist_ok=True)
    Image.fromarray(pequena).save(saida)
    if previa:
        base.previa(np.repeat(np.repeat(pequena, R, axis=0), R, axis=1)).save(previa)
    return pequena


if __name__ == '__main__':
    gerar(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else None)
    print('ok', sys.argv[1])
