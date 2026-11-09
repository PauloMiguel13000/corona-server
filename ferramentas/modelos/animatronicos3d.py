"""Animatronicos 3D ORIGINAIS do minigame Turno da Noite (bichos brasileiros de pelucia mecanica):
Capi (capivara), Tuca (tucano), Jaca (onca-pintada) e Boto (boto-cor-de-rosa, com o chapeu branco da lenda).

Corpo de pelucia gasto com as juntas de metal (endoesqueleto) a mostra nos ombros, cotovelos, joelhos e maos,
olhos de vidro com a pupila que BRILHA (Java: light_emission; Bedrock: entity_emissive_alpha), dentes/bico.
Dois quadros por bicho: f0 parado e f1 susto (bracos para a frente e a boca/bico escancarado).

Modelo Java em coordenadas de item (16 = 1 bloco), centro x=8 z=8, pes em y=-16, FRENTE = -z (norte), 2,25 blocos
(a mesma altura do boneco antigo com escala 1,25). Mostrado por ItemDisplay (leather_helmet + item_model
corona:props/anim_<id>_f<n>); no Bedrock a extensao GeyserDisplayEntity mostra o mesmo item (corona:prop_anim_<id>_f<n>).

uso: python3 -I animatronicos3d.py <saida> [ids...]
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)
SAIDA = sys.argv[1] if len(sys.argv) > 1 else '/tmp/claude-0/anim3d'
import trajes_hd as TH  # noqa: E402
import kit  # noqa: E402

PALETAS = {
    'capi': dict(pelo=(0.47, 0.33, 0.20), pelo2=(0.33, 0.22, 0.13), barriga=(0.68, 0.53, 0.36), nariz=(0.13, 0.08, 0.06),
                 pupila=(1.0, 0.92, 0.65), extra=(0.72, 0.10, 0.10)),
    'tuca': dict(pelo=(0.07, 0.07, 0.08), pelo2=(0.12, 0.12, 0.14), barriga=(0.96, 0.88, 0.55), nariz=(0.10, 0.10, 0.10),
                 pupila=(0.65, 0.90, 1.0), extra=(0.20, 0.55, 0.95)),
    'jaca': dict(pelo=(0.86, 0.60, 0.20), pelo2=(0.62, 0.40, 0.12), barriga=(0.93, 0.89, 0.80), nariz=(0.40, 0.18, 0.18),
                 pupila=(1.0, 0.72, 0.25), extra=(0.08, 0.06, 0.05)),
    'boto': dict(pelo=(0.92, 0.58, 0.66), pelo2=(0.98, 0.74, 0.79), barriga=(0.99, 0.85, 0.87), nariz=(0.35, 0.18, 0.22),
                 pupila=(1.0, 0.45, 0.55), extra=(0.95, 0.94, 0.90)),
}
IDS = list(PALETAS)


def C(nome, x0, x1, y0, y1, z0, z1, mat, rot=None, emite=0, so=None):
    c = kit.Cubo(nome, (min(x0, x1), min(y0, y1), min(z0, z1)), (max(x0, x1), max(y0, y1), max(z0, z1)), mat, rot=rot, emite=emite)
    c.so = so
    return c


# ------------------------------------------------------------------ corpo comum
def corpo(susto):
    cs = []
    for xa, xb in ((4.4, 7.3), (8.7, 11.6)):
        cs.append(C('coxa', xa, xb, -6.2, -1.2, 6.0, 10.0, 'pelo'))
        cs.append(C('joelho', xa + 0.6, xb - 0.6, -8.0, -6.1, 6.6, 9.4, 'metal'))
        cs.append(C('canela', xa + 0.1, xb - 0.1, -14.0, -7.9, 6.2, 9.8, 'pelo'))
        cs.append(C('pe', xa - 0.3, xb + 0.3, -16.0, -13.9, 4.4, 10.4, 'pelo2'))
    cs.append(C('quadril', 4.0, 12.0, -2.4, -1.0, 5.2, 10.8, 'pelo2'))
    cs.append(C('tronco', 3.6, 12.4, -1.2, 9.8, 4.8, 11.2, 'pelo'))
    cs.append(C('barriga', 5.2, 10.8, -0.6, 8.2, 4.5, 4.85, 'barriga', so=('north',)))
    cs.append(C('rasgo', 9.6, 11.2, 5.6, 7.6, 4.62, 4.82, 'metal', so=('north',)))
    cs.append(C('pescoco', 7.0, 9.0, 9.7, 11.7, 7.0, 9.0, 'metal'))
    for lado in (-1, 1):
        cx = 8 + lado * 6.05
        rot = ('x', 45.0, (cx, 9.0, 8.0)) if susto else None
        cs.append(C('ombro', cx - 0.9, cx + 0.9, 8.0, 9.9, 7.0, 9.0, 'metal', rot))
        cs.append(C('braco', cx - 1.3, cx + 1.3, 2.6, 8.4, 6.6, 9.4, 'pelo', rot))
        cs.append(C('cotovelo', cx - 0.8, cx + 0.8, 1.5, 2.7, 7.2, 8.8, 'metal', rot))
        cs.append(C('antebraco', cx - 1.2, cx + 1.2, -3.8, 1.6, 6.7, 9.3, 'pelo', rot))
        cs.append(C('mao', cx - 1.0, cx + 1.0, -5.9, -3.7, 7.0, 9.0, 'metal', rot))
        for k, dx in enumerate((-0.68, 0.0, 0.68)):
            cs.append(C('dedo', cx + dx - 0.2, cx + dx + 0.2, -8.2 + abs(dx) * 0.6, -5.8, 7.6, 8.4, 'metal2', rot))
        dedao = cx - lado * 1.1
        cs.append(C('dedao', dedao - 0.2, dedao + 0.2, -6.9, -4.8, 7.0, 7.5, 'metal2', rot))
    return cs


def olhos(cs, cx_lista, y0, y1, z, larg, pupila_y=None, extra_anel=False):
    for cx in cx_lista:
        if extra_anel:
            cs.append(C('anel', cx - larg / 2 - 0.45, cx + larg / 2 + 0.45, y0 - 0.45, y1 + 0.45, z + 0.1, z + 0.35, 'extra', so=('north',)))
        cs.append(C('olho', cx - larg / 2, cx + larg / 2, y0, y1, z - 0.15, z + 0.2, 'olho'))
        py = (y0 + y1) / 2 if pupila_y is None else pupila_y
        cs.append(C('pupila', cx - 0.32, cx + 0.32, py - 0.32, py + 0.32, z - 0.25, z - 0.14, 'pupila', emite=11, so=('north',)))


def dentes(cs, x0, x1, y0, y1, z0, z1, passo=0.9, larg=0.5, rot=None, nome='dente'):
    x = x0
    while x + larg <= x1 + 1e-6:
        cs.append(C(nome, x, x + larg, y0, y1, z0, z1, 'dente', rot))
        x += passo


# ------------------------------------------------------------------ cabecas
def cabeca_capi(cs, susto):
    mand = ('x', -45.0, (8.0, 13.6, 5.0)) if susto else None
    cs.append(C('cabeca', 3.8, 12.2, 11.6, 19.4, 5.0, 12.0, 'pelo'))
    cs.append(C('testa', 4.6, 11.4, 19.3, 20.0, 5.6, 11.4, 'pelo'))
    cs.append(C('focinho', 4.8, 11.2, 13.6, 16.8, 1.8, 5.1, 'pelo'))
    cs.append(C('nariz', 5.8, 10.2, 15.7, 16.95, 1.6, 2.5, 'nariz'))
    cs.append(C('boca', 5.4, 10.6, 12.4, 13.7, 2.4, 5.0, 'boca'))
    dentes(cs, 5.3, 10.7, 13.0, 13.65, 1.75, 2.25)
    cs.append(C('mandibula', 5.0, 11.0, 11.8, 13.6, 2.0, 5.0, 'barriga', mand))
    dentes(cs, 5.6, 10.4, 13.55, 14.05, 2.2, 2.6, rot=mand, nome='dente_b')
    olhos(cs, (5.5, 10.5), 17.2, 18.9, 4.85, 1.8)
    cs.append(C('orelha', 4.0, 5.4, 19.0, 20.4, 8.6, 10.0, 'pelo2'))
    cs.append(C('orelha', 10.6, 12.0, 19.0, 20.4, 8.6, 10.0, 'pelo2'))
    # bochechas fofas e um tufo de pelo no alto da cabeca
    cs.append(C('bochecha', 3.5, 4.3, 12.4, 15.6, 5.4, 9.6, 'pelo2'))
    cs.append(C('bochecha', 11.7, 12.5, 12.4, 15.6, 5.4, 9.6, 'pelo2'))
    cs.append(C('tufo', 6.8, 9.2, 19.9, 20.6, 7.0, 9.6, 'pelo2'))


def cabeca_tuca(cs, susto):
    cima = ('x', -22.5, (8.0, 15.0, 5.1))                       # bico grande caido para a frente
    mand = ('x', -45.0 if susto else -22.5, (8.0, 13.4, 5.1))
    cs.append(C('cabeca', 4.2, 11.8, 11.6, 19.0, 5.0, 11.6, 'pelo'))
    for k, (x0, ang) in enumerate(((6.4, 22.5), (7.4, 22.5), (8.4, 22.5))):
        cs.append(C('penacho', x0, x0 + 1.0, 18.8, 20.0 + (0.4 if k == 1 else 0), 7.0, 10.6, 'pelo2', ('x', -ang, (x0 + 0.5, 18.8, 8.8))))
    olhos(cs, (5.6, 10.4), 16.4, 18.0, 4.85, 1.5, extra_anel=True)
    cs.append(C('bico', 5.8, 10.2, 13.6, 16.6, -3.4, 5.1, 'bico', cima))
    cs.append(C('bico_ponta', 6.2, 9.8, 13.6, 15.9, -4.3, -3.3, 'nariz', cima))
    cs.append(C('boca', 6.2, 9.8, 12.6, 13.7, -2.2, 5.0, 'boca', cima))
    dentes(cs, 6.0, 10.0, 13.2, 13.65, -3.0, -2.6, passo=0.85, larg=0.45, rot=cima)
    cs.append(C('bico_baixo', 6.0, 10.0, 12.0, 13.4, -2.8, 5.0, 'bico', mand))
    dentes(cs, 6.2, 9.8, 13.35, 13.8, -2.5, -2.1, passo=0.9, larg=0.45, rot=mand, nome='dente_b')
    cs.append(C('garganta', 5.0, 11.0, 11.0, 13.2, 4.6, 6.0, 'barriga'))


def cabeca_jaca(cs, susto):
    mand = ('x', -45.0, (8.0, 12.8, 4.8)) if susto else None
    cs.append(C('cabeca', 3.8, 12.2, 11.6, 19.2, 4.8, 11.8, 'pelo'))
    cs.append(C('focinho', 5.2, 10.8, 12.6, 16.0, 2.4, 4.9, 'barriga'))
    cs.append(C('nariz', 6.8, 9.2, 15.0, 16.2, 2.1, 2.6, 'nariz'))
    cs.append(C('boca', 5.6, 10.4, 11.8, 12.9, 2.8, 4.8, 'boca'))
    cs.append(C('presa', 5.9, 6.5, 11.4, 12.7, 2.5, 3.0, 'dente'))
    cs.append(C('presa', 9.5, 10.1, 11.4, 12.7, 2.5, 3.0, 'dente'))
    dentes(cs, 6.8, 9.3, 12.1, 12.65, 2.5, 2.9, passo=0.8, larg=0.45)
    cs.append(C('mandibula', 5.6, 10.4, 11.4, 12.8, 2.6, 4.8, 'barriga', mand))
    dentes(cs, 6.0, 10.0, 12.75, 13.2, 2.8, 3.2, passo=0.85, larg=0.45, rot=mand, nome='dente_b')
    olhos(cs, (5.7, 10.3), 16.0, 17.7, 4.65, 2.0)
    cs.append(C('orelha', 3.8, 5.6, 18.8, 20.3, 7.6, 9.0, 'pelo2'))
    cs.append(C('orelha', 10.4, 12.2, 18.8, 20.3, 7.6, 9.0, 'pelo2'))
    cs.append(C('cauda', 7.2, 8.8, -1.8, -0.2, 11.0, 17.5, 'pelo', ('x', -22.5, (8.0, -1.0, 11.0))))


def cabeca_boto(cs, susto):
    mand = ('x', -22.5, (8.0, 13.0, 5.0)) if susto else None
    cs.append(C('cabeca', 4.2, 11.8, 11.6, 18.4, 5.0, 12.0, 'pelo'))
    cs.append(C('melao', 5.0, 11.0, 15.4, 18.6, 3.6, 5.2, 'pelo'))
    cs.append(C('rostro', 6.6, 9.4, 13.0, 14.6, -3.8, 5.1, 'pelo2'))
    cs.append(C('boca', 6.9, 9.1, 12.2, 13.1, -3.0, 5.0, 'boca'))
    for zz in (-3.1, -1.9, -0.7, 0.5, 1.7, 2.9):
        cs.append(C('dente', 6.75, 7.2, 12.65, 13.05, zz, zz + 0.45, 'dente'))
        cs.append(C('dente', 8.8, 9.25, 12.65, 13.05, zz, zz + 0.45, 'dente'))
    cs.append(C('mandibula', 6.8, 9.2, 12.0, 13.0, -3.4, 5.0, 'pelo2', mand))
    olhos(cs, (5.9, 10.1), 14.7, 15.9, 4.9, 1.5)
    # chapeu branco da lenda do boto
    cs.append(C('aba', 3.0, 13.0, 18.3, 18.8, 4.0, 13.0, 'extra'))
    cs.append(C('copa', 4.7, 11.3, 18.7, 21.0, 5.6, 11.4, 'extra'))
    cs.append(C('fita', 4.65, 11.35, 18.75, 19.45, 5.55, 11.45, 'nariz', so=('north', 'south', 'east', 'west')))
    cs.append(C('nadadeira', 7.1, 8.9, 4.0, 9.0, 11.0, 13.2, 'pelo', ('x', -22.5, (8.0, 4.0, 11.2))))


CABECAS = {'capi': cabeca_capi, 'tuca': cabeca_tuca, 'jaca': cabeca_jaca, 'boto': cabeca_boto}


def cubos(ident, quadro=0):
    susto = quadro == 1
    cs = corpo(susto)
    CABECAS[ident](cs, susto)
    return cs


# ------------------------------------------------------------------ pintura
def registrar(pal):
    TH.MAT.update({
        'pelo': (pal['pelo'], 0.10, 10, 0.0), 'pelo2': (pal['pelo2'], 0.10, 10, 0.0), 'barriga': (pal['barriga'], 0.10, 10, 0.0),
        'nariz': (pal['nariz'], 0.5, 40, 0.0), 'extra': (pal['extra'], 0.25, 18, 0.0),
        'metal': ((0.47, 0.49, 0.52), 0.9, 30, 0.8), 'metal2': ((0.30, 0.31, 0.34), 0.8, 26, 0.8),
        'ferrugem': ((0.42, 0.22, 0.10), 0.1, 8, 0.0), 'dente': ((0.90, 0.87, 0.76), 0.45, 30, 0.0),
        'boca': ((0.05, 0.02, 0.02), 0.1, 8, 0.0), 'vidro': ((0.93, 0.93, 0.90), 1.2, 90, 0.0),
        'preto': ((0.02, 0.02, 0.02), 0.6, 60, 0.0),
        'bico': ((1.0, 0.55, 0.10), 0.6, 40, 0.0),
    })


def pelucia(F, c, f, w, h, sem, mat, ident):
    tudo = F.ret(0, 0, w, h)
    F.pintar(tudo, mat, alt=0.18, bisel=0.18, ruido=0.20, escala_ruido=2.2)
    # fios do pelo (tufos) e desgaste mais claro; sujeira embaixo
    fios = TH.fbm(F.x * 5.0, F.y * 1.4, sem * 0.13, oct=3, seed=sem) - 0.5
    F.alt += 0.22 * fios
    F.alb *= (0.86 + 0.28 * (fios[..., None] + 0.5))
    gasto = TH.fbm(F.x * 0.6 + 3, F.y * 0.6, sem * 0.2, oct=3, seed=sem + 9) - 0.5
    F.alb = np.clip(F.alb * (1 + 0.35 * np.clip(gasto - 0.12, 0, 1)[..., None] * 3), 0, 1)
    yw = np.full(F.x.shape, float(c.de[1])) if f in ('up', 'down') else c.ate[1] - F.y
    suj = np.clip((-9.0 - yw) / 6.0, 0, 1) * 0.45 + np.clip(TH.fbm(F.x * 1.3, F.y * 1.3, 5.1, oct=2, seed=sem + 4) - 0.62, 0, 1) * 1.2
    F.alb *= (1 - 0.5 * np.clip(suj, 0, 0.8))[..., None]
    if ident == 'jaca' and mat == 'pelo':
        # rosetas da onca: aneis escuros com o miolo um pouco mais escuro
        cel = 1.35
        gx, gy = np.floor(F.x / cel + 0.5 * (np.floor(F.y / cel) % 2)), np.floor(F.y / cel)
        rng = np.random.default_rng(sem)
        jx = (np.sin(gx * 12.9898 + gy * 78.233 + sem) * 43758.5453) % 1
        jy = (np.sin(gx * 39.346 + gy * 11.135 + sem) * 23421.631) % 1
        cx = (gx - 0.5 * (np.floor(F.y / cel) % 2) + 0.3 + 0.4 * jx) * cel
        cy = (gy + 0.3 + 0.4 * jy) * cel
        d = np.hypot(F.x - cx, (F.y - cy) * 1.1)
        anel = np.clip(1 - np.abs(d - 0.36) / 0.11, 0, 1) * (1 - 0.5 * (TH.fbm(F.x * 4, F.y * 4, 2.0, oct=2, seed=7) > 0.6))
        miolo = (d < 0.27) * 0.25
        F.alb *= (1 - np.clip(anel * 0.85 + miolo, 0, 0.9))[..., None]
    if ident == 'tuca' and mat == 'pelo':
        # penas: escamas sobrepostas com brilho azulado
        pe = np.abs(np.sin(F.x * 3.4) * 0.5 + ((F.y * 1.6) % 1.0) - 0.5)
        F.alt += 0.12 * (1 - pe)
        F.alb += (np.clip(1 - pe * 2, 0, 1) * 0.05)[..., None] * np.array([0.3, 0.45, 0.9])[None, None, :]
    # costura nas bordas da face (pelucia costurada)
    if min(w, h) >= 3.0 and f in ('north', 'south'):
        borda = np.minimum(np.minimum(F.x, w - F.x), np.minimum(F.y, h - F.y))
        ponto = (np.abs(borda - 0.3) < 0.045) & ((np.floor((F.x + F.y) * 2.6) % 2) == 0)
        F.alb[ponto] *= 0.75
        F.alt[ponto] -= 0.05


def metal(F, c, f, w, h, sem, mat):
    tudo = F.ret(0, 0, w, h)
    F.pintar(tudo, mat, alt=0.3, bisel=0.22, ruido=0.08, escala_ruido=1.0)
    risco = TH.fbm(F.x * 0.4, F.y * 9.0, sem * 0.3, oct=2, seed=sem) - 0.5
    F.alb = np.clip(F.alb * (1 + 0.25 * risco[..., None]), 0, 1)
    ferr = np.clip((TH.fbm(F.x * 1.6, F.y * 1.6, 3.3, oct=3, seed=sem + 2) - 0.6) * 3, 0, 1)
    F.alb = F.alb * (1 - ferr[..., None] * 0.7) + np.array(TH.MAT['ferrugem'][0])[None, None, :] * ferr[..., None] * 0.7
    F.esp *= (1 - ferr)
    if min(w, h) >= 1.4:
        for cx, cy in ((0.35, 0.35), (w - 0.35, 0.35), (0.35, h - 0.35), (w - 0.35, h - 0.35)):
            F.rebite(cx, cy, 0.17, mat)


def pintar_face(c, f, wpx, hpx, sem, ident, esc):
    if wpx < 4 or hpx < 4:
        img, b = pintar_face(c, f, max(wpx, 4), max(hpx, 4), sem, ident, esc)
        img = np.array(Image.fromarray(img, 'RGBA').resize((wpx, hpx), Image.BOX))
        b = np.array(Image.fromarray((b * 255).astype(np.uint8), 'L').resize((wpx, hpx), Image.BOX)) / 255.0
        return img, b
    w, h = wpx / esc, hpx / esc
    F = TH.Face(w, h, sem)
    tudo = F.ret(0, 0, w, h)
    mat = c.pele
    pal = PALETAS[ident]
    if mat in ('pelo', 'pelo2', 'barriga'):
        pelucia(F, c, f, w, h, sem, mat, ident)
    elif mat in ('metal', 'metal2'):
        metal(F, c, f, w, h, sem, mat)
    elif mat == 'extra':
        if ident == 'boto':                       # chapeu de palha branco
            F.pintar(tudo, 'extra', alt=0.15, bisel=0.1, ruido=0.05)
            trama = (np.sin(F.x * 9.0) * np.sin(F.y * 9.0)) * 0.06
            F.alt += trama
            F.alb *= (0.94 + trama[..., None])
        elif ident == 'capi':                     # lenco vermelho com bolinhas
            F.pintar(tudo, 'extra', alt=0.08, bisel=0.08, ruido=0.12)
            bol = np.hypot((F.x % 0.9) - 0.45, (F.y % 0.9) - 0.45) < 0.13
            F.alb[bol] = np.array([0.95, 0.92, 0.85])
        else:                                     # anel azul do olho do tucano
            F.pintar(tudo, 'extra', alt=0.1, bisel=0.1, ruido=0.1)
    elif mat == 'bico':
        F.pintar(tudo, 'bico', alt=0.25, bisel=0.2, ruido=0.05)
        t = np.clip((F.x if f in ('east', 'west') else F.y) / max(w if f in ('east', 'west') else h, 0.5), 0, 1)
        grad = np.array([0.98, 0.78, 0.15])[None, None, :] * (1 - t[..., None]) + np.array([0.95, 0.35, 0.05])[None, None, :] * t[..., None]
        F.alb = np.clip(grad * (0.92 + 0.12 * (TH.fbm(F.x * 3, F.y * 3, 1.0, oct=2, seed=sem) - 0.5)[..., None]), 0, 1)
        risco = np.abs(np.sin(F.x * 2.2 + F.y * 0.4)) < 0.05
        F.alb[risco] *= 0.75
    elif mat == 'nariz':
        F.pintar(tudo, 'nariz', alt=0.3, bisel=0.3, ruido=0.08)
        if ident == 'capi' and f == 'north':
            for cx in (w * 0.3, w * 0.7):
                F.pintar(F.circ(cx, h * 0.45, min(w, h) * 0.17), 'preto', alt=-0.2)
    elif mat == 'olho':
        F.pintar(tudo, 'vidro', alt=0.4, bisel=0.4, ruido=0.04)
        if f == 'north':
            cx, cy = w / 2, h / 2
            r = min(w, h) * 0.42
            F.pintar(F.circ(cx, cy, r), 'preto', alt=0.2)
            F.luz(F.circ(cx, cy, r * 0.55), np.array(pal['pupila']), np.array(pal['pupila']) * 0.5, forca=0.6, largura=0.0, halo=0.25)
            F.pintar(F.circ(cx - r * 0.5, cy - r * 0.5, r * 0.18), 'vidro', alt=0.3)
        # veias / sujeira no vidro
        F.alb *= (0.9 + 0.1 * (TH.fbm(F.x * 5, F.y * 5, 3.0, oct=2, seed=sem) > 0.5))[..., None]
    elif mat == 'pupila':
        F.pintar(tudo, 'preto')
        F.luz(tudo + 0.05, np.array(pal['pupila']), np.array(pal['pupila']) * 0.6, forca=1.7, largura=0.0, halo=0.3)
    elif mat == 'dente':
        F.pintar(tudo, 'dente', alt=0.3, bisel=0.3, ruido=0.1)
        yy = F.y / max(h, 0.1)
        F.alb *= (1 - 0.35 * yy)[..., None]
        manch = np.clip(TH.fbm(F.x * 4, F.y * 4, 1.5, oct=2, seed=sem) - 0.55, 0, 1) * 2
        F.alb = F.alb * (1 - manch[..., None] * 0.6) + np.array([0.45, 0.06, 0.05])[None, None, :] * manch[..., None] * 0.6
    elif mat == 'boca':
        F.pintar(tudo, 'boca', alt=0.0)
        F.luz(tudo + 0.3, np.array([0.5, 0.05, 0.03]), np.array([0.25, 0.0, 0.0]), forca=0.25, largura=0.0, halo=0.6)
    else:
        F.pintar(tudo, 'preto', alt=0.2)
    img = F.render()
    brilho = np.clip(np.max(F.emis, axis=2), 0, 1.2) / 1.2
    return img, brilho


def montar_atlas(cs, ident, larg=512, alt_max=512):
    for esc in (8, 7, 6, 5, 4):
        alt = kit.arrumar_uv(cs, larg, esc)
        if alt <= alt_max:
            break
    TH.S = esc
    img = np.zeros((alt_max, larg, 4), np.uint8)
    bri = np.zeros((alt_max, larg))
    for i, c in enumerate(cs):
        for f in c.faces():
            x0, y0, x1, y1 = c.uv[f]
            rgba, b = pintar_face(c, f, x1 - x0, y1 - y0, 500 + i * 7 + len(f), ident, esc)
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
    out[~vis, 0:3] = (8, 8, 10)
    return out


def gerar(saida, ids):
    sys.path.insert(0, os.path.join(AQUI, '..', 'coronapack'))
    import bedrock_v2 as BV
    J = os.path.join(saida, 'java', 'assets', 'corona')
    B = os.path.join(saida, 'bedrock')
    mapeamento, item_tex, folhas = [], {}, []
    for ident in ids:
        registrar(PALETAS[ident])
        base = cubos(ident, 0)
        img, bri, esc = montar_atlas(base, ident)
        nome_tex = f'anim_{ident}'
        os.makedirs(os.path.join(J, 'textures', 'item', 'props'), exist_ok=True)
        Image.fromarray(alfa_binario(img), 'RGBA').save(os.path.join(J, 'textures', 'item', 'props', nome_tex + '.png'), optimize=True)
        tex_bed = f'textures/entity/corona_v2/item_props_{nome_tex}'
        os.makedirs(os.path.join(B, os.path.dirname(tex_bed)), exist_ok=True)
        Image.fromarray(textura_bedrock(img, bri), 'RGBA').save(os.path.join(B, tex_bed + '.png'), optimize=True)
        previas = []
        for q in (0, 1):
            cs = cubos(ident, q)
            assert len(cs) == len(base)
            for a, b in zip(cs, base):
                a.uv = dict(b.uv)
            nome = f'anim_{ident}_f{q}'
            modelo = kit.modelo_java(cs, f'corona:item/props/{nome_tex}', 512, 512, display={})
            modelo['credit'] = f'Corona Server - animatronico original {ident}'
            kit.salvar_json(os.path.join(J, 'models', 'props', nome + '.json'), modelo)
            kit.salvar_json(os.path.join(J, 'items', 'props', nome + '.json'), kit.item_java(f'corona:props/{nome}'))
            geo_id = f'geometry.corona.prop_{nome}'
            kit.salvar_json(os.path.join(B, 'models', 'entity', 'corona_v2', f'prop_{nome}.geo.json'), BV.geometria_de_modelo(geo_id, modelo))
            at = BV.attachable_prop(f'corona:prop_{nome}', tex_bed, geo_id)
            at['minecraft:attachable']['description']['materials'] = {'default': 'entity_emissive_alpha', 'enchanted': 'entity_emissive_alpha'}
            kit.salvar_json(os.path.join(B, 'attachables', f'prop_{nome}.json'), at)
            for yaw in ((180, 145) if q == 0 else (160,)):
                previas.append(kit.previa(cs, alfa_binario(img), yaw, 6, escala=7, fundo=(150, 158, 168)))
            pv = kit.previa(cs, alfa_binario(img), 180, 0, escala=5, fundo=(28, 30, 36))
            arr = np.array(pv.convert('RGBA'))
            fundo = (np.abs(arr[..., 0].astype(int) - 28) < 3) & (np.abs(arr[..., 1].astype(int) - 30) < 3) & (np.abs(arr[..., 2].astype(int) - 36) < 3)
            arr[fundo, 3] = 0
            ic = Image.fromarray(arr, 'RGBA')
            ic.thumbnail((32, 32), Image.LANCZOS)
            quad = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
            quad.paste(ic, ((32 - ic.width) // 2, (32 - ic.height) // 2))
            os.makedirs(os.path.join(B, 'textures', 'items', 'corona', 'v2'), exist_ok=True)
            quad.save(os.path.join(B, 'textures', 'items', 'corona', 'v2', f'corona_prop_{nome}.png'))
            item_tex[f'corona_prop_{nome}'] = {'textures': f'textures/items/corona/v2/corona_prop_{nome}'}
            mapeamento.append({'type': 'definition', 'model': f'corona:props/{nome}', 'bedrock_identifier': f'corona:prop_{nome}',
                               'display_name': f'Prop {nome}', 'bedrock_options': {'icon': f'corona_prop_{nome}', 'creative_category': 'equipment',
                                                                                  'protection_value': 1}})
        folhas.append(kit.folha(previas, (150, 158, 168)))
        print(ident, 'cubos', len(base), 'escala', esc)
    kit.salvar_json(os.path.join(saida, 'corona-animatronicos.json'), {'format_version': 2, 'items': {'minecraft:leather_helmet': mapeamento}})
    kit.salvar_json(os.path.join(saida, 'item_texture_extra.json'), item_tex)
    W = max(f.width for f in folhas)
    tudo = Image.new('RGB', (W, sum(f.height for f in folhas)), (150, 158, 168))
    y = 0
    for f in folhas:
        tudo.paste(f.convert('RGB'), (0, y))
        y += f.height
    tudo.save(os.path.join(saida, 'previa_animatronicos.png'))


if __name__ == '__main__':
    gerar(SAIDA, sys.argv[2:] or IDS)
