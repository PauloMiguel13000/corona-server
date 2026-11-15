"""Infectados 3D ORIGINAIS dos jogos de tiro (Apocalipse e Operacao): comum, corredor, tanque, cuspidor, explosivo e
chefe de horda. 2 quadros: f0 andando (bracos esticados para a frente), f1 atacando (bracos erguidos, boca aberta).
Itens: corona:props/anim_z<tipo>_f0|f1 (mesmo formato dos animatronicos: pes em y=-16, frente = norte).

uso: PYTHONPATH=plugins/coronapack python3 zumbis3d.py <saida>
"""
import os
import sys

import numpy as np

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)
import hotel3d as H  # noqa: E402  (reaproveita o registro de materiais por id)

A = H.A
C = A.C
TH = A.TH

# pele, camisa (tecido), calca (tecido2), carne/ferida (ouro), detalhe (pano), sapato, corda
def mats(pele, camisa, calca, carne=(0.42, 0.05, 0.05), pano=(0.30, 0.28, 0.24), corda=(0.20, 0.16, 0.10), metal=False):
    return {
        'pele': (pele, 0.2, 14, 0.0), 'tecido': (camisa, 0.12, 10, 0.0), 'tecido2': (calca, 0.12, 10, 0.0),
        'ouro': (carne, 0.5, 30, 0.0), 'pano': (pano, 0.75 if metal else 0.1, 50 if metal else 8, 0.8 if metal else 0.0),
        'corda': (corda, 0.1, 8, 0.0), 'sapato': ((0.07, 0.06, 0.05), 0.4, 30, 0.0),
    }


TIPOS = {
    'zcomum': dict(pupila=(1.0, 0.85, 0.35), m=mats((0.36, 0.47, 0.30), (0.20, 0.33, 0.47), (0.24, 0.20, 0.15))),
    'zcorredor': dict(pupila=(1.0, 0.25, 0.15), m=mats((0.52, 0.50, 0.42), (0.55, 0.10, 0.10), (0.13, 0.13, 0.16))),
    'ztanque': dict(pupila=(1.0, 0.55, 0.15), m=mats((0.33, 0.40, 0.30), (0.26, 0.22, 0.17), (0.17, 0.15, 0.12), pano=(0.45, 0.46, 0.48), metal=True)),
    'zcuspidor': dict(pupila=(0.55, 1.0, 0.25), m=mats((0.44, 0.58, 0.24), (0.36, 0.32, 0.20), (0.20, 0.22, 0.14), carne=(0.35, 0.75, 0.12))),
    'zexplosivo': dict(pupila=(1.0, 0.60, 0.10), m=mats((0.58, 0.50, 0.28), (0.78, 0.66, 0.12), (0.28, 0.26, 0.20), carne=(0.72, 0.10, 0.06), pano=(0.16, 0.16, 0.17))),
    'zchefe': dict(pupila=(1.0, 0.12, 0.08), m=mats((0.26, 0.30, 0.25), (0.12, 0.11, 0.13), (0.09, 0.08, 0.09), carne=(0.50, 0.04, 0.04), pano=(0.80, 0.77, 0.66))),
}
for k, v in TIPOS.items():
    A.PALETAS[k] = dict(pupila=v['pupila'])
    H.MATS[k] = v['m']


def pernas(cs, larg=2.2, afast=1.6, rasgada=True):
    for i, lado in enumerate((-1, 1)):
        cx = 8 + lado * afast
        cs.append(C('perna', cx - larg / 2, cx + larg / 2, -14.4, -2.0, 6.9, 9.1, 'tecido2'))
        # barra rasgada: canela de fora numa perna
        y1 = -9.0 if (rasgada and i == 0) else -13.0
        cs.append(C('canela', cx - larg / 2 + 0.25, cx + larg / 2 - 0.25, -14.6, y1, 7.1, 8.9, 'pele'))
        cs.append(C('sapato', cx - larg / 2 - 0.2, cx + larg / 2 + 0.2, -16.0, -14.3, 5.4, 9.5, 'sapato'))


def bracos(cs, ataque, ombro_y, afast, gross=1.7, comp=9.0, manga=4.0, garras=False, pend=False):
    """f0: bracos esticados para a frente; f1: erguidos 22,5 (ou 45) graus. pend = bracos caidos (tanque)."""
    for lado in (-1, 1):
        cx = 8 + lado * afast
        g = gross / 2
        if pend:
            rot = ('x', 45.0, (cx, ombro_y, 8.0)) if ataque else ('x', 22.5, (cx, ombro_y, 8.0))
            cs.append(C('ombro', cx - g - 0.3, cx + g + 0.3, ombro_y - 1.6, ombro_y + 1.4, 6.4, 9.6, 'pano', rot))
            cs.append(C('manga', cx - g, cx + g, ombro_y - manga, ombro_y - 1.0, 6.8, 9.2, 'tecido', rot))
            cs.append(C('braco', cx - g + 0.15, cx + g - 0.15, ombro_y - comp, ombro_y - manga + 0.2, 7.0, 9.0, 'pele', rot))
            cs.append(C('mao', cx - g - 0.2, cx + g + 0.2, ombro_y - comp - 2.6, ombro_y - comp + 0.2, 6.6, 9.4, 'pele', rot))
            for dx in (-0.7, 0.0, 0.7):
                cs.append(C('dedo', cx + dx - 0.22, cx + dx + 0.22, ombro_y - comp - 4.2, ombro_y - comp - 2.5, 7.4, 8.1, 'pano' if garras else 'pele', rot))
            continue
        rot = ('x', 45.0 if garras else 22.5, (cx, ombro_y, 8.0)) if ataque else None
        cs.append(C('ombro', cx - g - 0.2, cx + g + 0.2, ombro_y - g - 0.2, ombro_y + g + 0.2, 6.6, 9.2, 'tecido', rot))
        cs.append(C('manga', cx - g, cx + g, ombro_y - g, ombro_y + g, 8.0 - manga, 7.0, 'tecido', rot))
        cs.append(C('braco', cx - g + 0.15, cx + g - 0.15, ombro_y - g + 0.15, ombro_y + g - 0.15, 8.0 - comp, 8.2 - manga, 'pele', rot))
        cs.append(C('mao', cx - g, cx + g, ombro_y - g - 0.1, ombro_y + g + 0.1, 6.4 - comp, 8.2 - comp, 'pele', rot))
        for dx in (-0.55, 0.0, 0.55):
            cs.append(C('dedo', cx + dx - 0.18, cx + dx + 0.18, ombro_y - 0.9, ombro_y - 0.35, 4.6 - comp - (0.8 if garras else 0), 6.5 - comp, 'pano' if garras else 'pele', rot))


def cabeca(cs, ataque, y0=11.2, larg=6.2, olho_luz=15, cx=8.0, z0=4.9):
    h = larg / 2
    cs.append(C('pescoco', cx - 1.0, cx + 1.0, y0 - 1.4, y0 + 0.2, 7.0, 9.0, 'pele'))
    cs.append(C('cabeca', cx - h, cx + h, y0, y0 + larg, z0, z0 + larg, 'pele'))
    # cabelo ralo, um pedaco do cranio de fora
    cs.append(C('cabelo', cx - h - 0.1, cx + h + 0.1, y0 + larg - 1.3, y0 + larg + 0.25, z0 + 1.6, z0 + larg + 0.1, 'corda'))
    cs.append(C('ferida_c', cx + 0.6, cx + h - 0.4, y0 + larg - 0.1, y0 + larg + 0.3, z0 + 0.4, z0 + 2.2, 'ouro'))
    # olheiras fundas com a pupila acesa
    for dx in (-1.55, 1.55):
        cs.append(C('orbita', cx + dx - 1.0, cx + dx + 1.0, y0 + larg * 0.52, y0 + larg * 0.52 + 1.5, z0 - 0.12, z0 + 0.1, 'boca', so=('north',)))
        cs.append(C('olho_luz', cx + dx - 0.45, cx + dx + 0.45, y0 + larg * 0.52 + 0.35, y0 + larg * 0.52 + 1.15, z0 - 0.2, z0 - 0.05, 'pupila', emite=olho_luz, so=('north',)))
    mand = ('x', -22.5, (cx, y0 + 1.9, z0 + 1.0)) if ataque else None
    cs.append(C('boca', cx - 1.9, cx + 1.9, y0 + 0.9, y0 + 2.5, z0 - 0.1, z0 + 0.15, 'boca', so=('north',)))
    A.dentes(cs, cx - 1.8, cx + 1.8, y0 + 1.95, y0 + 2.5, z0 - 0.3, z0 - 0.05, passo=0.62, larg=0.4)
    cs.append(C('queixo', cx - 2.1, cx + 2.1, y0 - 0.2, y0 + 1.0, z0 - 0.35, z0 + 1.2, 'pele', mand))
    A.dentes(cs, cx - 1.7, cx + 1.7, y0 + 0.95, y0 + 1.45, z0 - 0.3, z0 - 0.05, passo=0.62, larg=0.4, rot=mand, nome='dente_b')


def tronco(cs, larg=6.6, y1=9.8, prof=(6.1, 9.9), costela=True):
    h = larg / 2
    cs.append(C('tronco', 8 - h, 8 + h, -2.4, y1, prof[0], prof[1], 'tecido'))
    cs.append(C('cinto', 8 - h - 0.15, 8 + h + 0.15, -3.0, -1.8, prof[0] - 0.15, prof[1] + 0.15, 'corda', so=('north', 'south', 'east', 'west')))
    if costela:
        # camisa rasgada: a barriga aberta com as costelas
        cs.append(C('ferida', 8 - h + 0.6, 8 - h + 3.4, 0.6, 5.0, prof[0] - 0.2, prof[0] + 0.05, 'ouro', so=('north',)))
        for k in range(3):
            cs.append(C('costela', 8 - h + 0.8, 8 - h + 3.2, 1.2 + k * 1.3, 1.7 + k * 1.3, prof[0] - 0.32, prof[0] - 0.15, 'pano', so=('north',)))
    cs.append(C('gola', 8 - 1.8, 8 + 1.8, y1 - 0.2, y1 + 0.5, 6.6, 9.4, 'tecido'))


def zcomum(at):
    cs = []
    pernas(cs)
    tronco(cs)
    bracos(cs, at, 8.4, 4.2)
    cabeca(cs, at)
    return cs


def zcorredor(at):
    cs = []
    pernas(cs, larg=1.9, afast=1.5)
    tronco(cs, larg=5.6, prof=(6.4, 9.6))
    # capuz rasgado nas costas
    cs.append(C('capuz', 5.6, 10.4, 9.0, 12.6, 9.4, 11.0, 'tecido'))
    bracos(cs, at, 8.5, 3.7, gross=1.4, comp=10.0, manga=2.0)
    cabeca(cs, at, larg=5.8, z0=4.4)
    return cs


def ztanque(at):
    cs = []
    pernas(cs, larg=3.4, afast=2.4, rasgada=False)
    tronco(cs, larg=11.0, y1=11.4, prof=(4.8, 11.2), costela=False)
    cs.append(C('barriga', 3.2, 12.8, -1.6, 5.4, 4.0, 5.0, 'tecido'))
    # placas de ferro presas com corrente no peito e nas costas
    cs.append(C('placa', 3.6, 12.4, 5.6, 10.8, 4.3, 4.85, 'pano'))
    cs.append(C('placa_c', 3.6, 12.4, 2.0, 10.8, 11.15, 11.7, 'pano'))
    cs.append(C('corrente', 2.3, 13.7, 7.4, 8.2, 4.6, 11.4, 'corda', so=('north', 'south', 'east', 'west', 'up')))
    bracos(cs, at, 10.6, 7.4, gross=3.4, comp=11.0, manga=4.2, pend=True)
    cabeca(cs, at, y0=12.6, larg=5.2, z0=5.2)
    cs.append(C('elmo', 5.2, 10.8, 16.0, 18.4, 5.0, 10.6, 'pano'))
    return cs


def zcuspidor(at):
    cs = []
    pernas(cs)
    tronco(cs, larg=6.8, costela=False)
    # barriga inchada e o papo de acido que brilha
    cs.append(C('pança', 4.2, 11.8, -2.0, 6.0, 3.6, 6.4, 'pele'))
    cs.append(C('bolha', 5.6, 8.0, 1.0, 3.6, 3.3, 3.7, 'ouro', emite=10, so=('north',)))
    cs.append(C('bolha2', 9.0, 10.6, 3.4, 5.0, 3.3, 3.7, 'ouro', emite=10, so=('north',)))
    cs.append(C('papo', 5.4, 10.6, 8.4, 11.4, 4.0, 7.2, 'ouro', emite=12))
    bracos(cs, at, 8.4, 4.3, gross=1.5, comp=7.5, manga=2.5)
    cabeca(cs, at, y0=11.6)
    cs.append(C('baba', 7.3, 8.7, 9.6 if not at else 8.6, 12.6, 4.5, 4.75, 'ouro', emite=12, so=('north',)))
    return cs


def zexplosivo(at):
    cs = []
    pernas(cs, rasgada=False)
    tronco(cs, larg=7.0, costela=False)
    # bananas de dinamite presas no peito, fio e o pavio aceso
    for k in range(4):
        x = 4.9 + k * 1.6
        cs.append(C('dinamite', x, x + 1.3, 1.6, 7.4, 4.6, 6.0, 'ouro'))
        cs.append(C('pavio', x + 0.45, x + 0.85, 7.4, 8.3, 5.1, 5.5, 'pano'))
    cs.append(C('faixa', 4.3, 11.7, 3.6, 4.6, 4.45, 10.1, 'pano', so=('north', 'south', 'east', 'west')))
    cs.append(C('brasa', 6.9, 7.6, 8.2, 8.9, 5.0, 5.6, 'pupila', emite=15))
    cs.append(C('caroco', 10.6, 12.2, 9.0, 10.6, 6.6, 8.6, 'ouro', emite=8))
    bracos(cs, at, 8.4, 4.4, gross=1.7, comp=8.5, manga=5.0)
    cabeca(cs, at)
    cs.append(C('caroco2', 4.4, 5.6, 14.6, 16.2, 6.6, 8.2, 'ouro', emite=8))
    return cs


def zchefe(at):
    cs = []
    pernas(cs, larg=3.0, afast=2.2, rasgada=False)
    tronco(cs, larg=9.6, y1=11.0, prof=(5.2, 10.8))
    # ombreiras e espinhos de osso nas costas
    for lado in (-1, 1):
        cx = 8 + lado * 5.6
        cs.append(C('ombreira', cx - 2.0, cx + 2.0, 9.6, 12.4, 5.6, 10.4, 'pano'))
        cs.append(C('espinho_o', cx - 0.5, cx + 0.5, 12.2, 14.8, 7.5, 8.5, 'pano'))
    for k in range(3):
        cs.append(C('espinho', 7.4, 8.6, 3.0 + k * 3.2, 4.4 + k * 3.2, 10.6, 13.2 - k * 0.4, 'pano'))
    bracos(cs, at, 9.6, 6.3, gross=2.6, comp=11.0, manga=3.0, garras=True)
    cabeca(cs, at, y0=12.4, larg=6.4)
    # coroa de ossos
    for k, x in enumerate((5.0, 6.6, 8.2, 9.8)):
        cs.append(C('osso', x, x + 0.9, 18.6, 20.4 + (k % 2) * 1.0, 5.4, 6.2, 'pano'))
    return cs


CORPOS = dict(zcomum=zcomum, zcorredor=zcorredor, ztanque=ztanque, zcuspidor=zcuspidor, zexplosivo=zexplosivo, zchefe=zchefe)
H.CORPOS.update(CORPOS)


def pintar_face(c, f, wpx, hpx, sem, ident, esc):
    mat = c.pele
    if mat not in H.MATS['figura'] or wpx < 4 or hpx < 4:
        return H._pintar(c, f, wpx, hpx, sem, ident, esc)
    w, h = wpx / esc, hpx / esc
    F = TH.Face(w, h, sem)
    tudo = F.ret(0, 0, w, h)
    metal = ident == 'ztanque' and mat == 'pano'
    if metal:
        F.pintar(tudo, mat, alt=0.3, bisel=0.3, ruido=0.10)
        ferr = np.clip(TH.fbm(F.x * 0.8, F.y * 0.8, 2.2, oct=3, seed=sem + 9) - 0.52, 0, 1) * 2.4
        F.alb = F.alb * (1 - ferr[..., None] * 0.7) + np.array([0.36, 0.17, 0.07])[None, None, :] * ferr[..., None] * 0.7
    elif mat == 'sapato':
        F.pintar(tudo, mat, alt=0.2, bisel=0.25, ruido=0.08)
    elif mat == 'pele':
        F.pintar(tudo, mat, alt=0.2, bisel=0.3, ruido=0.22, escala_ruido=3.0)
        # pele podre: manchas escuras e veias
        podre = np.clip(TH.fbm(F.x * 1.1, F.y * 1.1, 7.7, oct=3, seed=sem + 5) - 0.5, 0, 1) * 2.0
        F.alb = F.alb * (1 - podre[..., None] * 0.45)
        veia = np.abs(TH.fbm(F.x * 1.6, F.y * 1.6, 3.3, oct=2, seed=sem + 11) - 0.5) < 0.02
        F.alb[veia] = F.alb[veia] * 0.55 + np.array([0.18, 0.05, 0.12]) * 0.45
    elif mat == 'ouro':
        F.pintar(tudo, mat, alt=0.25, bisel=0.2, ruido=0.25, escala_ruido=3.5)
    else:
        F.pintar(tudo, mat, alt=0.12, bisel=0.14, ruido=0.14, escala_ruido=2.6)
        trama = np.sin(F.x * 14.0) * np.sin(F.y * 14.0) * 0.05
        dobra = TH.fbm(F.x * 0.5, F.y * 2.2, sem * 0.21, oct=3, seed=sem) - 0.5
        F.alt += trama + 0.25 * dobra
        F.alb = np.clip(F.alb * (1 + trama[..., None] + 0.5 * dobra[..., None]), 0, 1)
        if mat in ('tecido', 'tecido2'):
            # sangue seco e rasgos
            manch = np.clip(TH.fbm(F.x * 0.9, F.y * 0.9, 4.2, oct=3, seed=sem + 3) - 0.56, 0, 1) * 2.4
            F.alb = F.alb * (1 - manch[..., None] * 0.7) + np.array([0.28, 0.04, 0.03])[None, None, :] * manch[..., None] * 0.7
    if not metal:
        yw = np.full(F.x.shape, float(c.de[1])) if f in ('up', 'down') else c.ate[1] - F.y
        suj = np.clip((-8.0 - yw) / 7.0, 0, 1) * 0.45
        F.alb *= (1 - suj)[..., None]
    img = F.render()
    brilho = np.clip(np.max(F.emis, axis=2), 0, 1.2) / 1.2
    if c.emite:
        brilho = np.maximum(brilho, c.emite / 15.0)
    return img, brilho


A.pintar_face = pintar_face

if __name__ == '__main__':
    A.gerar(A.SAIDA, list(CORPOS))
    os.replace(os.path.join(A.SAIDA, 'corona-animatronicos.json'), os.path.join(A.SAIDA, 'corona-tiro-zumbis.json'))
    os.replace(os.path.join(A.SAIDA, 'previa_animatronicos.png'), os.path.join(A.SAIDA, 'previa_zumbis.png'))
