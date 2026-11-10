"""Monstros 3D ORIGINAIS do Hotel: a Figura (um porteiro cego e comprido, de uniforme vinho com botoes dourados, a cabeca
num saco de pano costurado, so a boca de fora) e o Perseguidor (uma sombra de sobretudo e cartola, olhos e sorriso
que brilham). 2 quadros: f0 andando/parado, f1 atacando (bracos para a frente, boca aberta).
Itens: corona:props/anim_figura_f0|f1 e anim_perseguidor_f0|f1 (mesmo formato dos animatronicos: 2,25 blocos, pes em y=-16).

uso: python3 -I hotel3d.py <saida>
"""
import os
import sys

import numpy as np

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)
import animatronicos3d as A  # noqa: E402

C = A.C
TH = A.TH

A.PALETAS['figura'] = dict(pupila=(1.0, 0.25, 0.15))
A.PALETAS['perseguidor'] = dict(pupila=(1.0, 1.0, 0.96))

MATS = {
    'figura': {
        'tecido': ((0.42, 0.07, 0.10), 0.15, 12, 0.0), 'tecido2': ((0.10, 0.09, 0.11), 0.12, 10, 0.0),
        'ouro': ((0.92, 0.72, 0.22), 0.9, 60, 0.9), 'pano': ((0.62, 0.57, 0.47), 0.05, 6, 0.0),
        'pele': ((0.55, 0.52, 0.50), 0.2, 14, 0.0), 'corda': ((0.36, 0.26, 0.14), 0.1, 8, 0.0),
        'sapato': ((0.05, 0.05, 0.05), 0.7, 50, 0.0),
    },
    'perseguidor': {
        'tecido': ((0.05, 0.05, 0.07), 0.25, 20, 0.0), 'tecido2': ((0.025, 0.025, 0.035), 0.3, 26, 0.0),
        'ouro': ((0.45, 0.05, 0.07), 0.4, 30, 0.0), 'pano': ((0.035, 0.035, 0.045), 0.2, 16, 0.0),
        'pele': ((0.03, 0.03, 0.04), 0.5, 40, 0.0), 'corda': ((0.02, 0.02, 0.02), 0.1, 8, 0.0),
        'sapato': ((0.02, 0.02, 0.02), 0.7, 50, 0.0),
    },
}


def bracos(cs, susto, comp, mat_mao):
    for lado in (-1, 1):
        cx = 8 + lado * 4.6
        rot = ('x', 45.0, (cx, 8.6, 8.0)) if susto else ('x', 0.0, (cx, 8.6, 8.0))
        rot = rot if susto else None
        cs.append(C('ombro', cx - 1.1, cx + 1.1, 7.6, 9.8, 6.8, 9.2, 'tecido', rot))
        cs.append(C('braco', cx - 0.85, cx + 0.85, 9.0 - comp, 7.8, 7.1, 8.9, 'tecido', rot))
        cs.append(C('punho', cx - 1.0, cx + 1.0, 8.4 - comp, 9.4 - comp, 7.0, 9.0, 'ouro', rot))
        cs.append(C('mao', cx - 0.8, cx + 0.8, 6.6 - comp, 8.5 - comp, 7.3, 8.7, mat_mao, rot))
        for dx in (-0.6, 0.0, 0.6):
            cs.append(C('dedo', cx + dx - 0.17, cx + dx + 0.17, 3.2 - comp + abs(dx), 6.7 - comp, 7.7, 8.2, mat_mao, rot))


def pernas(cs):
    for xa, xb in ((5.3, 7.5), (8.5, 10.7)):
        cs.append(C('perna', xa, xb, -14.4, -2.0, 6.9, 9.1, 'tecido2'))
        cs.append(C('sapato', xa - 0.2, xb + 0.2, -16.0, -14.3, 5.2, 9.5, 'sapato'))


def figura(susto):
    cs = []
    pernas(cs)
    cs.append(C('tronco', 4.6, 11.4, -2.4, 9.8, 6.2, 9.8, 'tecido'))
    cs.append(C('barra', 4.4, 11.6, -3.4, -2.2, 6.0, 10.0, 'ouro'))
    for k in range(5):
        y = -0.6 + k * 2.0
        cs.append(C('botao', 6.2, 6.9, y, y + 0.7, 5.95, 6.25, 'ouro', so=('north',)))
        cs.append(C('botao', 9.1, 9.8, y, y + 0.7, 5.95, 6.25, 'ouro', so=('north',)))
    cs.append(C('dragona', 2.9, 5.0, 9.6, 10.3, 6.4, 9.6, 'ouro'))
    cs.append(C('dragona', 11.0, 13.1, 9.6, 10.3, 6.4, 9.6, 'ouro'))
    cs.append(C('gola', 6.2, 9.8, 9.7, 10.9, 6.6, 9.4, 'tecido2'))
    bracos(cs, susto, 17.0, 'pele')
    # a cabeca num saco de pano amarrado no pescoco; so a boca aparece, costurada nos cantos
    cs.append(C('pescoco', 7.0, 9.0, 10.6, 11.8, 7.0, 9.0, 'pele'))
    cs.append(C('saco', 5.0, 11.0, 11.6, 18.6, 5.0, 11.0, 'pano'))
    cs.append(C('saco_topo', 5.8, 10.2, 18.5, 19.3, 5.8, 10.2, 'pano'))
    cs.append(C('corda', 4.8, 11.2, 11.5, 12.2, 4.8, 11.2, 'corda', so=('north', 'south', 'east', 'west')))
    mand = ('x', -45.0, (8.0, 13.6, 5.0)) if susto else None
    cs.append(C('boca', 5.9, 10.1, 12.6, 14.6, 4.75, 5.05, 'boca', so=('north',)))
    A.dentes(cs, 6.0, 10.0, 13.9, 14.6, 4.5, 4.8, passo=0.62, larg=0.42)
    cs.append(C('queixo', 5.8, 10.2, 12.3, 13.3, 4.4, 5.6, 'pano', mand))
    A.dentes(cs, 6.1, 9.9, 13.25, 13.9, 4.45, 4.75, passo=0.62, larg=0.42, rot=mand, nome='dente_b')
    # bone de porteiro torto em cima do saco
    cs.append(C('bone', 5.6, 10.4, 19.2, 20.8, 5.6, 10.4, 'tecido'))
    cs.append(C('bone_faixa', 5.5, 10.5, 19.2, 19.8, 5.5, 10.5, 'ouro', so=('north', 'south', 'east', 'west')))
    cs.append(C('bone_botao', 7.5, 8.5, 20.7, 21.1, 7.5, 8.5, 'ouro'))
    return cs


def perseguidor(susto):
    cs = []
    pernas(cs)
    cs.append(C('tronco', 4.4, 11.6, -3.0, 9.8, 6.0, 10.0, 'tecido'))
    cs.append(C('lapela', 5.4, 7.4, 4.0, 9.6, 5.75, 6.05, 'tecido2', so=('north',)))
    cs.append(C('lapela', 8.6, 10.6, 4.0, 9.6, 5.75, 6.05, 'tecido2', so=('north',)))
    cs.append(C('gravata', 7.5, 8.5, 5.0, 9.4, 5.7, 6.0, 'ouro', so=('north',)))
    # as abas do sobretudo, rasgadas
    cs.append(C('aba', 4.2, 7.6, -11.0, -2.8, 9.2, 10.4, 'tecido2', ('x', -22.5, (8.0, -2.8, 9.8))))
    cs.append(C('aba', 8.4, 11.8, -12.6, -2.8, 9.2, 10.4, 'tecido2', ('x', -22.5, (8.0, -2.8, 9.8))))
    cs.append(C('aba_f', 4.2, 6.4, -9.0, -2.8, 5.8, 6.4, 'tecido2'))
    cs.append(C('aba_f', 9.6, 11.8, -10.2, -2.8, 5.8, 6.4, 'tecido2'))
    cs.append(C('gola', 5.6, 10.4, 9.7, 11.4, 6.2, 9.8, 'tecido2'))
    bracos(cs, susto, 16.0, 'pele')
    cs.append(C('cabeca', 4.8, 11.2, 11.2, 17.8, 4.9, 11.1, 'pano'))
    # olhos e sorriso que brilham no escuro
    for cx in (6.3, 9.7):
        cs.append(C('olho_luz', cx - 0.9, cx + 0.9, 15.0, 16.2, 4.7, 4.95, 'pupila', emite=15, so=('north',)))
    larg = 5.2 if susto else 4.4
    y0 = 11.9 if susto else 12.8
    cs.append(C('sorriso', 8 - larg / 2, 8 + larg / 2, y0, 13.9, 4.72, 4.95, 'boca', so=('north',)))
    x = 8 - 2.1
    for k in range(6):
        alto = 0.9 if susto else 0.55
        cs.append(C('dente_luz', x + k * 0.72, x + k * 0.72 + 0.5, 13.9 - alto, 13.9, 4.6, 4.75, 'pupila', emite=15, so=('north',)))
        cs.append(C('dente_luz_b', x + k * 0.72, x + k * 0.72 + 0.5, y0, y0 + (0.8 if susto else 0.3), 4.6, 4.75, 'pupila', emite=15, so=('north',)))
    # cartola
    cs.append(C('aba_chapeu', 3.4, 12.6, 17.7, 18.3, 3.6, 12.4, 'tecido2'))
    cs.append(C('copa', 5.0, 11.0, 18.2, 22.4, 5.0, 11.0, 'tecido2'))
    cs.append(C('fita', 4.95, 11.05, 18.3, 19.2, 4.95, 11.05, 'ouro', so=('north', 'south', 'east', 'west')))
    return cs


CORPOS = {'figura': figura, 'perseguidor': perseguidor}
A.cubos = lambda ident, quadro=0: CORPOS[ident](quadro == 1)
_reg, _pintar = A.registrar, A.pintar_face
ATUAL = {'id': None}


def registrar(pal):
    ident = [k for k, v in A.PALETAS.items() if v is pal][0]
    ATUAL['id'] = ident
    base = dict(pelo=(0, 0, 0), pelo2=(0, 0, 0), barriga=(0, 0, 0), nariz=(0, 0, 0), extra=(0, 0, 0))
    base.update(pal)
    _reg(base)
    TH.MAT.update(MATS[ident])


def pintar_face(c, f, wpx, hpx, sem, ident, esc):
    mat = c.pele
    if mat not in MATS['figura'] or wpx < 4 or hpx < 4:
        return _pintar(c, f, wpx, hpx, sem, ident, esc)
    w, h = wpx / esc, hpx / esc
    F = TH.Face(w, h, sem)
    tudo = F.ret(0, 0, w, h)
    if mat == 'ouro':
        F.pintar(tudo, mat, alt=0.3, bisel=0.3, ruido=0.06)
    elif mat == 'sapato':
        F.pintar(tudo, mat, alt=0.2, bisel=0.25, ruido=0.05)
    elif mat == 'pele':
        F.pintar(tudo, mat, alt=0.2, bisel=0.3, ruido=0.18, escala_ruido=3.0)
    else:
        F.pintar(tudo, mat, alt=0.12, bisel=0.14, ruido=0.14, escala_ruido=2.6)
        # trama do tecido e dobras; o saco de pano tem remendos e manchas
        trama = np.sin(F.x * 14.0) * np.sin(F.y * 14.0) * 0.05
        dobra = TH.fbm(F.x * 0.5, F.y * 2.2, sem * 0.21, oct=3, seed=sem) - 0.5
        F.alt += trama + 0.25 * dobra
        F.alb = np.clip(F.alb * (1 + trama[..., None] + 0.5 * dobra[..., None]), 0, 1)
        if mat == 'pano':
            manch = np.clip(TH.fbm(F.x * 0.9, F.y * 0.9, 4.2, oct=3, seed=sem + 3) - 0.55, 0, 1) * 2.2
            cor = np.array([0.30, 0.10, 0.07]) if ident == 'figura' else np.array([0.0, 0.0, 0.0])
            F.alb = F.alb * (1 - manch[..., None] * 0.6) + cor[None, None, :] * manch[..., None] * 0.6
            if ident == 'figura' and min(w, h) >= 3.0 and f in ('north', 'east', 'west', 'south'):
                # costura em X descendo pelo saco
                cost = (np.abs(((F.x - w * 0.3) + (F.y % 1.2) - 0.6)) < 0.07) | (np.abs(((F.x - w * 0.3) - (F.y % 1.2) + 0.6)) < 0.07)
                cost &= np.abs(F.x - w * 0.3) < 0.6
                F.alb[cost] = np.array([0.12, 0.10, 0.08])
        # sujeira embaixo
        yw = np.full(F.x.shape, float(c.de[1])) if f in ('up', 'down') else c.ate[1] - F.y
        suj = np.clip((-8.0 - yw) / 7.0, 0, 1) * 0.5
        F.alb *= (1 - suj)[..., None]
    img = F.render()
    brilho = np.clip(np.max(F.emis, axis=2), 0, 1.2) / 1.2
    return img, brilho


A.registrar = registrar
A.pintar_face = pintar_face

if __name__ == '__main__':
    A.gerar(A.SAIDA, ['figura', 'perseguidor'])
    os.replace(os.path.join(A.SAIDA, 'corona-animatronicos.json'), os.path.join(A.SAIDA, 'corona-hotel.json'))
    os.replace(os.path.join(A.SAIDA, 'previa_animatronicos.png'), os.path.join(A.SAIDA, 'previa_hotel.png'))
