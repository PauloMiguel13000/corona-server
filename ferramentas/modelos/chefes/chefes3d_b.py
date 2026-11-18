"""Chefes das masmorras 11-20 esculpidos em 3D (desenho original do Corona Server), para aprovacao.
11 Carcereiro, 12 Farao, 13 Devastador, 14 Senhor dos Ventos, 15 Rei Afogado, 16 Xama da Selva, 17 Carrasco,
18 Besta Zoglin, 19 Coveiro, 20 Gosma Primordial.
uso: PYTHONPATH=plugins/coronapack:ferramentas/modelos python3 chefes3d_b.py <saida> [ids]"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import chefes3d as K  # noqa: E402

H, A, C, LUZ = K.H, K.A, K.C, K.LUZ
mat, mats, olhos = K.mat, K.mats, K.olhos


# ------------------------------------------------------------ pecas comuns de humanoide
def pernas(cs, cx=(5.4, 10.6), larg=1.8, mat_p='tecido', mat_b='sapato', joelho=None, alto=-2.0):
    for x in cx:
        cs.append(C('perna', x - larg, x + larg, -13.4, alto, 8.0 - larg, 8.0 + larg, mat_p))
        cs.append(C('bota', x - larg - 0.3, x + larg + 0.3, -16.0, -13.2, 8.0 - larg - 1.0, 8.0 + larg + 0.3, mat_b))
        if joelho:
            cs.append(C('joelheira', x - larg - 0.2, x + larg + 0.2, -9.4, -7.4, 8.0 - larg - 0.5, 8.0 - larg + 0.2, joelho))


def bracos(cs, susto, ombro_dx, ombro_y, larg, mat_b, mat_m, comp=8.0, pivo_z=8.0, punho=None, so_lado=None):
    for lado in (-1, 1):
        if so_lado and lado != so_lado:
            continue
        cx = 8 + lado * ombro_dx
        rot = ('x', 45.0, (cx, ombro_y, pivo_z)) if susto else None
        cs.append(C('braco', cx - larg, cx + larg, ombro_y - comp, ombro_y, pivo_z - larg, pivo_z + larg, mat_b, rot))
        cs.append(C('mao', cx - larg * 0.9, cx + larg * 0.9, ombro_y - comp - 2.2, ombro_y - comp + 0.2, pivo_z - larg * 0.9, pivo_z + larg * 0.9, mat_m, rot))
        if punho:
            cs.append(C('punho', cx - larg - 0.25, cx + larg + 0.25, ombro_y - comp + 0.2, ombro_y - comp + 1.8, pivo_z - larg - 0.25, pivo_z + larg + 0.25, punho, rot))


def rot_d(susto, cx, y, z=8.0):
    return ('x', 45.0, (cx, y, z)) if susto else None


# ------------------------------------------------------------ 11) O Carcereiro (Prisao Abissal)
def carcereiro(susto):
    cs = []
    pernas(cs, (5.0, 11.0), 2.2, 'pele', 'sapato', 'ouro')
    cs.append(C('cinto', 2.0, 14.0, -2.8, -0.8, 4.6, 11.4, 'corda'))
    cs.append(C('fivela', 6.8, 9.2, -3.2, -0.4, 4.2, 4.7, 'ouro'))
    # argola de chaves pendurada
    cs.append(C('argola', 12.6, 14.4, -6.0, -3.0, 4.2, 4.7, 'ouro'))
    for k, (x, y) in enumerate(((12.8, -8.4), (13.6, -8.8), (14.2, -8.0))):
        cs.append(C('chave', x - 0.25, x + 0.25, y, y + 2.6, 4.3, 4.6, 'ouro'))
    cs.append(C('avental', 4.6, 11.4, -9.0, -1.0, 4.3, 4.7, 'corda', so=('north', 'east', 'west', 'down')))
    # tronco enorme, curvado, com rachaduras de alma acesas
    cs.append(C('barriga', 2.4, 13.6, -1.0, 5.4, 4.8, 11.2, 'pele'))
    cs.append(C('peito', 1.0, 15.0, 5.0, 11.4, 4.4, 12.4, 'pele'))
    cs.append(C('corcunda', 2.6, 13.4, 9.6, 13.0, 8.4, 12.8, 'pele'))
    for x, y0, y1 in ((5.0, 6.2, 10.6), (7.6, 2.0, 9.4), (10.8, 5.0, 10.0), (6.2, -0.4, 3.0)):
        cs.append(C('racha', x - 0.35, x + 0.35, y0, y1, 4.25, 4.45, 'pupila', so=('north',), **LUZ))
    cs.append(C('nucleo', 7.0, 9.0, 7.4, 9.4, 4.2, 4.4, 'pupila', so=('north',), **LUZ))
    # correntes cruzadas no peito
    for i in range(6):
        cs.append(C('elo', 2.2 + i * 2.0, 3.6 + i * 2.0, 10.0 - i * 1.5, 11.0 - i * 1.5, 4.1, 4.5, 'tecido2'))
    # bracos grossos ate o joelho, algemas e correntes
    for lado in (-1, 1):
        cx = 8 + lado * 8.8
        rot = rot_d(susto and lado == 1, cx, 10.4)
        cs.append(C('ombro', cx - 2.8, cx + 2.8, 7.6, 12.6, 5.0, 11.0, 'pele', rot))
        cs.append(C('ombreira', cx - 3.0, cx + 3.0, 10.6, 13.0, 4.8, 11.2, 'tecido', rot))
        cs.append(C('braco', cx - 2.2, cx + 2.2, -2.0, 8.0, 5.8, 10.2, 'pele', rot))
        cs.append(C('algema', cx - 2.5, cx + 2.5, -1.2, 1.2, 5.5, 10.5, 'tecido', rot))
        cs.append(C('mao', cx - 2.3, cx + 2.3, -6.0, -2.0, 5.7, 10.3, 'pele', rot))
        for d in (-1.4, 0.0, 1.4):
            cs.append(C('garra', cx + d - 0.35, cx + d + 0.35, -7.4, -5.8, 5.8, 6.6, 'tecido2', rot))
        for k in range(4):
            cs.append(C('corrente', cx - 0.45 + lado * 0.6, cx + 0.45 + lado * 0.6, -3.0 - k * 1.6, -1.8 - k * 1.6, 7.6, 8.4, 'tecido2', rot))
    # bola de ferro da corrente na mao direita
    cxd = 8 + 8.8
    rot = rot_d(susto, cxd, 10.4)
    cs.append(C('bola', cxd - 2.4, cxd + 2.4, -14.0, -9.4, 5.6, 10.4, 'tecido', rot))
    for x, y, z in ((cxd, -9.4, 8.0), (cxd - 2.4, -11.7, 8.0), (cxd + 2.4, -11.7, 8.0), (cxd, -11.7, 5.6)):
        cs.append(C('cravo', x - 0.5, x + 0.5, y - 0.5, y + 0.5, z - 0.5, z + 0.5, 'ouro', rot))
    # cabeca pequena afundada nos ombros, sem olhos, com a grade de ferro no rosto
    cs.append(C('cabeca', 5.0, 11.0, 11.0, 16.6, 3.6, 9.6, 'pele'))
    cs.append(C('antena', 3.2, 5.0, 14.6, 18.2, 5.4, 7.6, 'pele', ('z', 22.5, (5.0, 14.6, 6.5))))
    cs.append(C('antena', 11.0, 12.8, 14.6, 18.2, 5.4, 7.6, 'pele', ('z', -22.5, (11.0, 14.6, 6.5))))
    cs.append(C('antena_luz', 3.6, 4.6, 16.0, 17.6, 5.2, 5.5, 'pupila', ('z', 22.5, (5.0, 14.6, 6.5)), **LUZ))
    cs.append(C('antena_luz', 11.4, 12.4, 16.0, 17.6, 5.2, 5.5, 'pupila', ('z', -22.5, (11.0, 14.6, 6.5)), **LUZ))
    for x in (5.6, 7.2, 8.8, 10.4):
        cs.append(C('grade', x - 0.3, x + 0.3, 11.2, 16.4, 3.25, 3.65, 'tecido'))
    cs.append(C('grade_h', 4.8, 11.2, 13.6, 14.2, 3.2, 3.7, 'tecido'))
    cs.append(C('boca_luz', 6.0, 10.0, 11.6, 12.6, 3.45, 3.6, 'pupila', so=('north',), **LUZ))
    mand = ('x', -22.5, (8.0, 11.0, 6.0)) if susto else None
    cs.append(C('mandibula', 5.4, 10.6, 9.6, 11.4, 3.8, 9.0, 'pele', mand))
    A.dentes(cs, 5.8, 10.2, 11.2, 11.8, 3.6, 3.9, passo=0.8, larg=0.5, rot=mand, nome='dente_b')
    return cs


# ------------------------------------------------------------ 12) Farao (Tumba do Farao)
def farao(susto):
    cs = []
    pernas(cs, (6.0, 10.0), 1.5, 'pele', 'ouro')
    for x in (6.0, 10.0):
        for y in (-12.0, -9.0, -6.0):
            cs.append(C('faixa_perna', x - 1.7, x + 1.7, y, y + 0.8, 6.3, 9.7, 'pano'))
    cs.append(C('saiote', 3.8, 12.2, -7.0, -1.0, 5.6, 10.4, 'pano'))
    for x in (4.6, 6.2, 7.8, 9.4, 11.0):
        cs.append(C('prega', x - 0.25, x + 0.25, -7.0, -1.2, 5.45, 5.65, 'tecido2', so=('north',)))
    cs.append(C('cinto', 3.6, 12.4, -2.0, -0.4, 5.4, 10.6, 'ouro'))
    cs.append(C('avental', 6.6, 9.4, -6.4, -0.8, 5.2, 5.5, 'tecido', so=('north', 'east', 'west', 'down')))
    cs.append(C('tronco', 4.0, 12.0, -0.6, 7.4, 6.0, 10.4, 'pele'))
    for y in (0.4, 2.2, 4.0):
        cs.append(C('faixa', 3.9, 12.1, y, y + 0.8, 5.9, 10.5, 'pano'))
    # colar largo de ouro e lapis (usekh) e o escaravelho
    cs.append(C('colar', 3.0, 13.0, 5.2, 8.0, 5.6, 10.8, 'ouro'))
    cs.append(C('colar2', 3.6, 12.4, 5.6, 6.6, 5.45, 5.7, 'tecido', so=('north',)))
    cs.append(C('escaravelho', 7.0, 9.0, 1.6, 3.6, 5.6, 6.0, 'tecido'))
    cs.append(C('escaravelho_luz', 7.5, 8.5, 2.1, 3.1, 5.5, 5.65, 'pupila', so=('north',), **LUZ))
    for lado in (-1, 1):
        cx = 8 + lado * 5.4
        rot = rot_d(susto and lado == 1, cx, 7.0)
        cs.append(C('braco', cx - 1.3, cx + 1.3, -1.2, 7.0, 6.9, 9.5, 'pele', rot))
        for y in (0.6, 3.2):
            cs.append(C('faixa_braco', cx - 1.45, cx + 1.45, y, y + 0.7, 6.75, 9.65, 'pano', rot))
        cs.append(C('bracelete', cx - 1.5, cx + 1.5, -1.8, -0.4, 6.7, 9.7, 'ouro', rot))
        cs.append(C('mao', cx - 1.1, cx + 1.1, -3.8, -1.6, 7.1, 9.3, 'pele', rot))
    # cajado de cajado-gancho (heka) na mao direita
    cx = 8 + 5.4
    rot = rot_d(susto, cx, 7.0)
    cs.append(C('cajado', cx - 0.4, cx + 0.4, -14.0, 9.0, 5.4, 6.2, 'tecido', rot))
    for y in range(-12, 8, 3):
        cs.append(C('cajado_aro', cx - 0.55, cx + 0.55, y, y + 1.0, 5.25, 6.35, 'ouro', rot))
    cs.append(C('gancho', cx - 0.4, cx + 0.4, 8.6, 9.6, 2.4, 6.2, 'tecido', rot))
    cs.append(C('gancho2', cx - 0.4, cx + 0.4, 6.8, 9.6, 2.4, 3.2, 'tecido', rot))
    # cabeca de mumia com o nemes listrado azul e ouro e a naja na testa
    cs.append(C('cabeca', 5.0, 11.0, 7.4, 13.6, 4.8, 10.8, 'pele'))
    for y in (8.4, 10.0, 11.6):
        cs.append(C('faixa_rosto', 4.9, 11.1, y, y + 0.7, 4.7, 10.9, 'pano'))
    olhos(cs, 11.0, 4.7, w=0.6, h=0.35)
    cs.append(C('barba', 7.3, 8.7, 5.0, 7.6, 4.8, 5.8, 'ouro'))
    cs.append(C('nemes', 4.2, 11.8, 12.4, 15.0, 4.4, 11.4, 'ouro'))
    for lado in (-1, 1):
        x0 = 8 + lado * 3.9
        cs.append(C('nemes_lado', x0 - 0.9, x0 + 0.9, 5.0, 13.0, 4.6, 7.6, 'ouro'))
        for y in (5.6, 7.6, 9.6, 11.6):
            cs.append(C('listra', x0 - 0.95, x0 + 0.95, y, y + 0.8, 4.55, 7.65, 'tecido'))
    for x in (5.0, 7.0, 9.0, 11.0):
        cs.append(C('listra_topo', x - 0.5, x + 0.5, 12.4, 15.05, 4.35, 11.45, 'tecido', so=('north', 'up')))
    cs.append(C('nemes_trás', 5.4, 10.6, 4.0, 13.0, 10.8, 12.0, 'ouro'))
    cs.append(C('naja', 7.4, 8.6, 13.4, 16.4, 3.8, 4.6, 'ouro'))
    cs.append(C('naja_olho', 7.6, 8.4, 15.4, 16.0, 3.65, 3.8, 'pupila', so=('north',), **LUZ))
    # capa curta de linho atras
    cs.append(C('capa', 4.0, 12.0, -9.0, 7.0, 10.6, 11.4, 'pano', ('x', -22.5, (8.0, 7.0, 11.0))))
    return cs


# ------------------------------------------------------------ 13) Devastador (Quartel dos Saqueadores) - quadrupede
def devastador(susto):
    cs = []
    # corpo enorme e largo, com placas de guerra e a sela com estandartes
    cs.append(C('corpo', 1.0, 15.0, -8.0, 2.0, 6.0, 28.0, 'pele'))
    cs.append(C('lombo', 2.0, 14.0, 1.8, 4.6, 8.0, 26.0, 'pele'))
    cs.append(C('barriga', 2.4, 13.6, -9.0, -7.8, 8.0, 26.0, 'tecido2'))
    for z in (9.0, 13.0, 17.0, 21.0, 25.0):
        cs.append(C('placa', 0.6, 15.4, -3.0, 4.8, z, z + 2.4, 'tecido'))
        cs.append(C('espinho', 7.4, 8.6, 4.6, 7.8, z + 0.6, z + 1.8, 'ouro'))
    cs.append(C('sela', 3.0, 13.0, 4.4, 6.4, 13.0, 20.0, 'corda'))
    for x in (2.4, 13.6):
        cs.append(C('mastro', x - 0.4, x + 0.4, 4.4, 16.0, 19.0, 19.8, 'corda'))
        cs.append(C('estandarte', x - 0.2, x + 0.2, 8.0, 15.4, 19.8, 24.8, 'tecido2'))
        cs.append(C('estandarte_simbolo', x - 0.3, x + 0.3, 10.6, 12.8, 21.2, 23.4, 'pano'))
    cs.append(C('cauda', 6.6, 9.4, -4.0, 0.6, 27.8, 31.4, 'pele', ('x', 22.5, (8.0, 0.0, 28.0))))
    # 4 patas grossas com casco
    for x, z in ((3.4, 9.4), (12.6, 9.4), (3.4, 24.2), (12.6, 24.2)):
        cs.append(C('pata', x - 2.4, x + 2.4, -14.0, -6.0, z - 2.4, z + 2.4, 'pele'))
        cs.append(C('casco', x - 2.7, x + 2.7, -16.0, -13.6, z - 2.7, z + 2.7, 'tecido2'))
        cs.append(C('joelheira', x - 2.6, x + 2.6, -10.0, -8.6, z - 2.6, z - 2.2, 'tecido'))
    # pescoco e cabeca baixa com chifres enormes e capacete de ferro
    cs.append(C('pescoco', 4.0, 12.0, -6.0, 2.4, 1.6, 7.0, 'pele'))
    mand = ('x', -22.5, (8.0, -8.0, 0.0)) if susto else None
    cs.append(C('cabeca', 3.4, 12.6, -10.0, 0.0, -6.0, 3.0, 'pele'))
    cs.append(C('capacete', 3.0, 13.0, -2.0, 1.4, -6.4, 3.4, 'tecido'))
    cs.append(C('focinho', 4.6, 11.4, -11.0, -6.0, -8.6, -5.8, 'pele'))
    for x in (6.0, 10.0):
        cs.append(C('narina', x - 0.6, x + 0.6, -8.2, -7.0, -8.75, -8.55, 'tecido2', so=('north',)))
    olhos(cs, -3.6, -6.0, cx=(4.6, 11.4), w=0.7, h=0.5)
    cs.append(C('mandibula', 4.2, 11.8, -13.0, -10.0, -7.6, 1.0, 'pele', mand))
    A.dentes(cs, 4.6, 11.4, -10.2, -9.4, -7.85, -7.55, passo=0.9, larg=0.5, rot=mand, nome='dente_b')
    for lado in (-1, 1):
        x0 = 8 + lado * 4.6
        xa, xb = sorted((x0, x0 + lado * 3.6))
        cs.append(C('chifre', xa, xb, -2.0, 0.6, -2.0, 0.6, 'pano'))
        cs.append(C('chifre2', x0 + lado * 3.2 - 0.9, x0 + lado * 3.2 + 0.9, -1.0, 4.0, -3.0, -1.0, 'pano'))
        cs.append(C('chifre3', x0 + lado * 3.2 - 0.6, x0 + lado * 3.2 + 0.6, 3.6, 6.4, -5.0, -2.6, 'pano'))
        cs.append(C('presa', x0 - lado * 1.4 - 0.5, x0 - lado * 1.4 + 0.5, -14.0, -11.6, -8.0, -7.0, 'pano', mand))
    return cs


# ------------------------------------------------------------ 14) Senhor dos Ventos (Santuario dos Ventos) - flutua
def ventos(susto):
    cs = []
    # tornado no lugar das pernas (fogo = coluna que gira) e aneis de pedra girando
    for i in range(4):
        r = 1.6 + i * 0.9
        cs.append(C('fogo', 8 - r, 8 + r, -15.0 + i * 2.2, -13.2 + i * 2.2, 8 - r, 8 + r, 'pano'))
    cs.append(C('fogo_ponta', 7.2, 8.8, -16.0, -14.8, 7.2, 8.8, 'pano'))
    for k, (x, z) in enumerate(((1.4, 8.0), (14.6, 8.0), (8.0, 1.4), (8.0, 14.6), (3.2, 3.2), (12.8, 12.8), (3.2, 12.8), (12.8, 3.2))):
        y0 = -12.0 if k < 4 else -5.0
        cs.append(C('vareta', x - 0.9, x + 0.9, y0, y0 + 6.0, z - 0.9, z + 0.9, 'tecido'))
        cs.append(C('vareta_luz', x - 0.55, x + 0.55, y0 + 5.8, y0 + 6.8, z - 0.55, z + 0.55, 'pupila', **LUZ))
    # tronco de placas de pedra com o vento passando por dentro
    cs.append(C('nucleo', 5.4, 10.6, -6.0, 4.0, 5.4, 10.6, 'tecido2'))
    cs.append(C('nucleo_luz', 6.4, 9.6, -4.0, 2.0, 5.2, 10.8, 'pupila', so=('north', 'south', 'east', 'west'), **LUZ))
    cs.append(C('peito', 3.0, 13.0, 2.6, 9.4, 4.8, 11.2, 'tecido'))
    for y in (3.4, 5.4, 7.4):
        cs.append(C('corrente_vento', 3.4, 12.6, y, y + 0.6, 4.6, 4.85, 'pano', so=('north',)))
    for lado in (-1, 1):
        cx = 8 + lado * 6.6
        rot = rot_d(susto, cx, 8.6)
        cs.append(C('ombro', cx - 2.2, cx + 2.2, 6.6, 10.4, 5.4, 10.6, 'tecido', rot))
        cs.append(C('braco', cx - 1.2, cx + 1.2, 0.4, 7.0, 6.8, 9.2, 'pano', rot))
        cs.append(C('punho', cx - 1.8, cx + 1.8, -2.8, 0.6, 6.2, 9.8, 'tecido', rot))
        cs.append(C('chama_mao', cx - 1.0, cx + 1.0, -5.0, -2.6, 7.0, 9.0, 'pupila', rot, **LUZ))
    # cabeca de pedra com capuz de vento e olhos acesos; tres pontas de vento atras
    cs.append(C('cabeca', 4.8, 11.2, 9.8, 16.0, 4.8, 11.2, 'tecido'))
    olhos(cs, 13.0, 4.8, w=0.9, h=0.45)
    cs.append(C('boca', 6.2, 9.8, 10.8, 11.6, 4.65, 4.8, 'pupila', so=('north',), **LUZ))
    cs.append(C('capuz', 4.2, 11.8, 15.6, 17.4, 4.4, 11.6, 'pano'))
    for i, (x, h) in enumerate(((5.0, 4.0), (8.0, 6.0), (11.0, 4.0))):
        cs.append(C('coroa_fogo', x - 0.8, x + 0.8, 16.6, 16.6 + h, 9.0, 11.6, 'pano', ('x', -22.5, (x, 16.6, 10.3))))
    cs.append(C('chifre', 2.8, 4.8, 13.0, 15.0, 7.0, 9.0, 'tecido2'))
    cs.append(C('chifre', 11.2, 13.2, 13.0, 15.0, 7.0, 9.0, 'tecido2'))
    return cs


# ------------------------------------------------------------ 15) Rei Afogado (Catacumba Afogada)
def afogado(susto):
    cs = []
    pernas(cs, (5.4, 10.6), 1.7, 'pele', 'tecido2', 'tecido')
    cs.append(C('saiote', 3.2, 12.8, -7.0, -1.0, 5.4, 10.6, 'tecido'))
    for x in (3.8, 6.0, 8.4, 10.8):
        cs.append(C('alga', x, x + 1.2, -10.4 - (x % 3), -6.6, 5.2, 5.5, 'corda', so=('north', 'south', 'east', 'west', 'down')))
    cs.append(C('cinto', 3.0, 13.0, -2.2, -0.6, 5.2, 10.8, 'ouro'))
    cs.append(C('tronco', 3.4, 12.6, -0.8, 8.4, 5.4, 10.6, 'pele'))
    cs.append(C('peitoral', 3.0, 13.0, 3.0, 8.8, 5.0, 8.4, 'tecido'))
    for x, y in ((4.4, 1.0), (11.0, 2.2), (9.4, 0.2), (5.2, 6.6), (12.2, 7.2)):
        cs.append(C('craca', x - 0.5, x + 0.5, y - 0.5, y + 0.5, 4.7, 5.3, 'pano'))
    cs.append(C('concha', 7.0, 9.0, 4.4, 6.6, 4.7, 5.1, 'ouro'))
    cs.append(C('perola', 7.6, 8.4, 5.0, 5.8, 4.6, 4.75, 'pupila', so=('north',), **LUZ))
    for lado in (-1, 1):
        cx = 8 + lado * 6.6
        rot = rot_d(susto and lado == 1, cx, 8.0)
        cs.append(C('ombreira', cx - 2.4, cx + 2.4, 6.8, 9.8, 5.0, 11.0, 'tecido', rot))
        for dz in (6.0, 8.0, 10.0):
            cs.append(C('coral_ombro', cx - 0.5 + lado * 1.0, cx + 0.5 + lado * 1.0, 9.6, 12.0 - abs(dz - 8) * 0.5, dz - 0.5, dz + 0.5, 'ouro', rot))
        cs.append(C('braco', cx - 1.6, cx + 1.6, -1.2, 7.0, 6.4, 9.6, 'pele', rot))
        cs.append(C('bracelete', cx - 1.8, cx + 1.8, -1.8, -0.4, 6.2, 9.8, 'ouro', rot))
        cs.append(C('mao', cx - 1.3, cx + 1.3, -4.0, -1.6, 6.7, 9.3, 'pele', rot))
    # tridente grande na mao direita
    cx = 8 + 6.6
    rot = rot_d(susto, cx, 8.0)
    cs.append(C('cabo', cx - 0.45, cx + 0.45, -15.0, 13.0, 5.0, 5.9, 'tecido', rot))
    cs.append(C('tridente_base', cx - 2.6, cx + 2.6, 12.6, 13.8, 4.9, 6.0, 'tecido', rot))
    for d, h in ((-2.2, 3.0), (0.0, 4.4), (2.2, 3.0)):
        cs.append(C('ponta', cx + d - 0.4, cx + d + 0.4, 13.6, 13.6 + h, 5.0, 5.9, 'tecido', rot))
        cs.append(C('ponta_luz', cx + d - 0.2, cx + d + 0.2, 13.6 + h - 0.8, 13.6 + h + 0.4, 4.95, 5.95, 'pupila', rot, **LUZ))
    # cabeca com barba de algas e coroa de coral
    cs.append(C('cabeca', 5.0, 11.0, 8.4, 14.6, 4.8, 10.8, 'pele'))
    olhos(cs, 12.0, 4.8, w=0.6)
    cs.append(C('barba', 5.2, 10.8, 5.4, 10.0, 4.3, 5.0, 'corda'))
    for x in (5.6, 7.0, 8.4, 9.8):
        cs.append(C('barba_ponta', x - 0.4, x + 0.4, 3.6 - (x % 2), 5.6, 4.4, 4.9, 'corda'))
    cs.append(C('cabelo', 4.6, 11.4, 6.0, 14.4, 9.8, 11.4, 'corda'))
    cs.append(C('coroa', 4.6, 11.4, 14.4, 15.6, 4.6, 11.0, 'ouro'))
    for x, h in ((5.0, 2.4), (6.8, 3.6), (8.0, 4.6), (9.2, 3.6), (11.0, 2.4)):
        cs.append(C('coral', x - 0.45, x + 0.45, 15.4, 15.4 + h, 4.8, 5.8, 'ouro'))
        cs.append(C('coral_ramo', x - 1.0, x + 1.0, 15.4 + h * 0.6, 15.4 + h * 0.6 + 0.7, 4.9, 5.7, 'ouro'))
    cs.append(C('capa', 3.0, 13.0, -15.0, 8.6, 10.8, 11.6, 'tecido2', ('x', -22.5, (8.0, 8.6, 11.2))))
    for x in (3.4, 6.2, 9.0, 11.8):
        cs.append(C('capa_rasgo', x, x + 1.6, -15.4, -13.0, 11.0, 11.5, 'corda', ('x', -22.5, (8.0, 8.6, 11.2))))
    return cs


# ------------------------------------------------------------ 16) Xama da Selva (Templo da Selva)
def xama(susto):
    cs = []
    pernas(cs, (6.0, 10.0), 1.4, 'pele', 'corda')
    for x in (6.0, 10.0):
        cs.append(C('pintura', x - 1.45, x + 1.45, -9.0, -8.2, 6.55, 9.45, 'pupila', **LUZ))
    # saiote de folhas em camadas
    for i, y in enumerate((-7.6, -5.0, -2.6)):
        cs.append(C('folhas', 3.4 + i * 0.3, 12.6 - i * 0.3, y, y + 3.0, 5.4 - i * 0.2, 10.6 + i * 0.2, 'tecido'))
    for x in (4.0, 6.0, 8.0, 10.0, 12.0):
        cs.append(C('folha_ponta', x - 0.6, x + 0.6, -9.4, -7.4, 5.2, 5.6, 'tecido', so=('north', 'south', 'east', 'west', 'down')))
    cs.append(C('cinto', 3.6, 12.4, -0.6, 0.8, 5.6, 10.4, 'corda'))
    cs.append(C('tronco', 4.4, 11.6, 0.4, 7.4, 6.2, 9.8, 'pele'))
    for y in (2.0, 4.0):
        cs.append(C('pintura', 4.3, 11.7, y, y + 0.6, 6.1, 9.9, 'pupila', so=('north',), **LUZ))
    # colar de ossos e dentes
    cs.append(C('colar', 4.0, 12.0, 6.4, 7.4, 5.6, 10.4, 'corda'))
    for x in (5.0, 6.6, 8.0, 9.4, 11.0):
        cs.append(C('osso_colar', x - 0.35, x + 0.35, 4.8, 6.6, 5.6, 6.0, 'pano'))
    cs.append(C('caveira_colar', 7.0, 9.0, 3.6, 5.4, 5.5, 6.2, 'pano'))
    for lado in (-1, 1):
        cx = 8 + lado * 5.2
        rot = rot_d(susto and lado == 1, cx, 7.0)
        cs.append(C('braco', cx - 1.2, cx + 1.2, -1.0, 7.2, 7.0, 9.4, 'pele', rot))
        cs.append(C('bracelete', cx - 1.35, cx + 1.35, 2.0, 3.2, 6.85, 9.55, 'corda', rot))
        cs.append(C('mao', cx - 1.0, cx + 1.0, -3.4, -1.0, 7.2, 9.2, 'pele', rot))
        cs.append(C('ombreira', cx - 1.8, cx + 1.8, 6.0, 8.0, 6.2, 10.2, 'tecido', rot))
    # cajado com orbe aceso e penas
    cx = 8 + 5.2
    rot = rot_d(susto, cx, 7.0)
    cs.append(C('cajado', cx - 0.45, cx + 0.45, -16.0, 13.0, 5.6, 6.5, 'corda', rot))
    cs.append(C('cajado_aro', cx - 1.2, cx + 1.2, 12.6, 13.6, 5.0, 7.1, 'pano', rot))
    cs.append(C('orbe', cx - 1.0, cx + 1.0, 13.6, 15.6, 5.1, 7.1, 'pupila', rot, **LUZ))
    for d in (-1.4, 1.4):
        cs.append(C('pena_cajado', cx + d - 0.3, cx + d + 0.3, 9.0, 12.8, 5.8, 6.3, 'ouro', rot))
    # mascara de madeira grande com cocar de penas coloridas
    cs.append(C('cabeca', 5.4, 10.6, 7.4, 12.6, 5.6, 10.6, 'pele'))
    cs.append(C('mascara', 4.4, 11.6, 6.6, 14.0, 4.4, 5.6, 'corda'))
    for x in (6.4, 9.6):
        cs.append(C('olho_fundo', x - 0.9, x + 0.9, 10.6, 11.8, 4.25, 4.45, 'boca', so=('north',)))
        cs.append(C('olho', x - 0.45, x + 0.45, 10.9, 11.5, 4.15, 4.3, 'pupila', so=('north',), **LUZ))
    cs.append(C('nariz_masc', 7.4, 8.6, 8.4, 10.6, 3.6, 4.5, 'corda'))
    cs.append(C('boca_masc', 6.0, 10.0, 7.2, 8.2, 4.25, 4.45, 'boca', so=('north',)))
    A.dentes(cs, 6.2, 9.8, 7.4, 8.0, 4.15, 4.35, passo=0.7, larg=0.4)
    cs.append(C('pintura_masc', 4.5, 5.3, 8.0, 13.0, 4.3, 4.45, 'pupila', so=('north',), **LUZ))
    cs.append(C('pintura_masc', 10.7, 11.5, 8.0, 13.0, 4.3, 4.45, 'pupila', so=('north',), **LUZ))
    cs.append(C('faixa_cocar', 4.2, 11.8, 12.8, 14.2, 4.8, 10.8, 'pano'))
    cores = ('ouro', 'pupila', 'tecido', 'ouro', 'tecido2', 'pupila', 'ouro')
    for i, x in enumerate((3.6, 5.0, 6.4, 8.0, 9.6, 11.0, 12.4)):
        h = 3.0 + (3 - abs(i - 3)) * 1.3
        cs.append(C('pena', x - 0.45, x + 0.45, 14.0, 14.0 + h, 6.4, 7.2, cores[i], ('z', (i - 3) * 7.5 if False else 0.0, (x, 14.0, 6.8)) if False else None,
                    **(LUZ if cores[i] == 'pupila' else {})))
    # arco de osso nas costas
    cs.append(C('arco', 3.0, 13.0, 1.0, 2.0, 10.6, 11.4, 'pano', ('z', 45.0, (8.0, 1.5, 11.0))))
    cs.append(C('corda_arco', 3.4, 12.6, 1.3, 1.6, 11.4, 11.6, 'tecido2', ('z', 45.0, (8.0, 1.5, 11.0))))
    return cs


# ------------------------------------------------------------ 17) Carrasco (Castelo do Carrasco)
def carrasco(susto):
    cs = []
    pernas(cs, (5.4, 10.6), 1.9, 'tecido2', 'sapato')
    cs.append(C('avental', 4.4, 11.6, -11.0, -0.6, 4.4, 4.9, 'corda', so=('north', 'east', 'west', 'down')))
    cs.append(C('cinto', 2.8, 13.2, -2.0, -0.2, 5.0, 11.0, 'corda'))
    cs.append(C('fivela', 7.0, 9.0, -2.4, 0.2, 4.6, 5.1, 'ouro'))
    # torso nu e forte com tiras de couro em X
    cs.append(C('tronco', 2.6, 13.4, -0.4, 9.6, 5.0, 11.0, 'pele'))
    cs.append(C('peito', 3.0, 13.0, 5.4, 9.4, 4.6, 6.0, 'pele'))
    cs.append(C('tira', 2.6, 13.4, 3.4, 4.4, 4.4, 4.75, 'corda', ('z', 22.5, (8.0, 4.0, 4.6)), so=('north',)))
    cs.append(C('tira', 2.6, 13.4, 3.4, 4.4, 4.4, 4.75, 'corda', ('z', -22.5, (8.0, 4.0, 4.6)), so=('north',)))
    cs.append(C('argola', 7.2, 8.8, 3.2, 4.8, 4.3, 4.55, 'ouro'))
    for y in (0.4, 1.8):
        cs.append(C('musculo', 5.4, 10.6, y, y + 0.4, 4.85, 5.05, 'tecido2', so=('north',)))
    for lado in (-1, 1):
        cx = 8 + lado * 7.2
        rot = ('x', 45.0, (cx, 8.6, 8.0)) if susto else None
        cs.append(C('ombreira', cx - 2.6, cx + 2.6, 7.0, 10.6, 5.0, 11.0, 'tecido', rot))
        for dz in (6.0, 8.0, 10.0):
            cs.append(C('espeto', cx - 0.4 + lado * 1.6, cx + 0.4 + lado * 1.6, 10.4, 12.4, dz - 0.4, dz + 0.4, 'ouro', rot))
        cs.append(C('braco', cx - 1.9, cx + 1.9, -1.2, 7.2, 6.1, 9.9, 'pele', rot))
        cs.append(C('bracadeira', cx - 2.2, cx + 2.2, -3.2, 0.6, 5.8, 10.2, 'corda', rot))
        for y in (-2.6, -1.2, 0.2):
            cs.append(C('cravo', cx + lado * 2.1 - 0.3, cx + lado * 2.1 + 0.3, y, y + 0.6, 7.7, 8.3, 'ouro', rot))
        cs.append(C('mao', cx - 1.6, cx + 1.6, -5.4, -3.0, 6.4, 9.6, 'pele', rot))
    # machado de carrasco gigante (duas maos)
    cx = 8 + 7.2
    rot = ('x', 45.0, (cx, 8.6, 8.0)) if susto else None
    cs.append(C('cabo', cx - 0.6, cx + 0.6, -12.0, 15.0, 3.0, 4.2, 'corda', rot))
    cs.append(C('cabo_aro', cx - 0.8, cx + 0.8, -12.6, -11.0, 2.8, 4.4, 'ouro', rot))
    cs.append(C('machado', cx - 0.5, cx + 0.5, 7.0, 16.0, -5.0, 3.0, 'tecido', rot))
    cs.append(C('machado_ponta', cx - 0.5, cx + 0.5, 5.4, 17.6, -6.6, -4.8, 'tecido', rot))
    cs.append(C('fio', cx - 0.3, cx + 0.3, 5.0, 18.0, -7.2, -6.4, 'pano', rot))
    cs.append(C('sangue', cx - 0.55, cx + 0.55, 8.0, 10.4, -6.2, -3.6, 'corda', rot))
    # capuz negro com furos dos olhos acesos
    cs.append(C('cabeca', 5.0, 11.0, 9.4, 15.6, 4.8, 10.8, 'tecido2'))
    cs.append(C('capuz', 4.6, 11.4, 11.0, 17.2, 4.4, 11.4, 'tecido2'))
    cs.append(C('capuz_ponta', 6.2, 9.8, 16.8, 19.6, 6.6, 10.4, 'tecido2', ('x', -22.5, (8.0, 16.8, 8.5))))
    for x in (6.4, 9.6):
        cs.append(C('furo', x - 0.9, x + 0.9, 13.0, 14.2, 4.25, 4.45, 'boca', so=('north',)))
        cs.append(C('olho', x - 0.5, x + 0.5, 13.2, 14.0, 4.15, 4.3, 'pupila', so=('north',), **LUZ))
    cs.append(C('costura', 7.85, 8.15, 9.6, 17.0, 4.3, 4.45, 'corda', so=('north',)))
    cs.append(C('capa', 3.6, 12.4, -10.0, 9.4, 10.8, 11.6, 'tecido2', ('x', -22.5, (8.0, 9.4, 11.2))))
    return cs


# ------------------------------------------------------------ 18) Besta Zoglin (Covil da Besta) - quadrupede
def zoglin(susto):
    cs = []
    cs.append(C('corpo', 2.0, 14.0, -8.0, 1.0, 6.0, 25.0, 'pele'))
    cs.append(C('lombo', 3.0, 13.0, 0.6, 3.6, 7.0, 22.0, 'pele'))
    # costelas expostas e carne podre
    for z in (10.0, 12.6, 15.2):
        cs.append(C('costela', 1.6, 14.4, -6.0, -0.6, z, z + 1.0, 'pano'))
    cs.append(C('ferida', 1.8, 2.1, -5.6, -1.4, 9.6, 16.6, 'corda', so=('west',)))
    cs.append(C('ferida', 13.9, 14.2, -5.6, -1.4, 9.6, 16.6, 'corda', so=('east',)))
    # espinha de ossos saindo das costas
    for i, z in enumerate((7.0, 10.0, 13.0, 16.0, 19.0, 22.0)):
        h = (4.0, 5.6, 6.4, 5.6, 4.4, 3.0)[i]
        cs.append(C('espinho', 7.3, 8.7, 3.4, 3.4 + h, z, z + 1.4, 'pano', ('x', -22.5, (8.0, 3.4, z + 0.7))))
    cs.append(C('cauda', 7.0, 9.0, -3.0, 0.6, 24.6, 28.0, 'pele', ('x', 22.5, (8.0, 0.0, 25.0))))
    for x, z in ((4.2, 9.0), (11.8, 9.0), (4.2, 22.0), (11.8, 22.0)):
        cs.append(C('pata', x - 1.9, x + 1.9, -14.4, -6.0, z - 1.9, z + 1.9, 'pele'))
        cs.append(C('casco', x - 2.1, x + 2.1, -16.0, -14.0, z - 2.1, z + 2.1, 'tecido2'))
        cs.append(C('corrente', x - 2.2, x + 2.2, -12.0, -11.0, z - 2.2, z + 2.2, 'tecido'))
    # cabeca baixa, larga, com presas enormes e olhos acesos
    mand = ('x', -22.5, (8.0, -8.0, 2.0)) if susto else None
    cs.append(C('cabeca', 2.6, 13.4, -9.0, 0.6, -5.0, 6.4, 'pele'))
    cs.append(C('testa_osso', 3.4, 12.6, -1.4, 1.2, -5.4, 2.0, 'pano'))
    cs.append(C('focinho', 4.4, 11.6, -10.4, -4.0, -7.6, -4.8, 'pele'))
    for x in (6.0, 10.0):
        cs.append(C('narina', x - 0.6, x + 0.6, -7.6, -6.4, -7.75, -7.55, 'tecido2', so=('north',)))
    olhos(cs, -2.6, -5.0, cx=(4.4, 11.6), w=0.7, h=0.5)
    cs.append(C('mandibula', 3.6, 12.4, -12.0, -9.0, -6.4, 3.6, 'pele', mand))
    for lado in (-1, 1):
        x0 = 8 + lado * 3.4
        cs.append(C('presa', x0 - 0.7, x0 + 0.7, -11.0, -4.6, -7.4, -6.0, 'pano', mand))
        cs.append(C('presa_ponta', x0 - 0.45 + lado * 0.6, x0 + 0.45 + lado * 0.6, -4.8, -2.6, -7.2, -6.2, 'pano', mand))
        cs.append(C('orelha', (1.0 if lado < 0 else 13.0), (3.0 if lado < 0 else 15.0), -1.6, 1.6, 0.6, 3.0, 'pele', ('z', 22.5 * lado, (8 + lado * 5.4, -1.6, 1.8))))
    return cs


# ------------------------------------------------------------ 19) O Coveiro (Cemiterio Esquecido)
def coveiro(susto):
    cs = []
    pernas(cs, (6.0, 10.0), 1.3, 'tecido2', 'sapato')
    # sobretudo longo aberto atras, cachecol, colete
    cs.append(C('casaco', 3.6, 12.4, -11.0, 8.4, 5.6, 10.6, 'tecido'))
    cs.append(C('abertura', 7.3, 8.7, -11.0, 0.0, 5.45, 5.65, 'tecido2', so=('north',)))
    for y in (-4.0, -1.0, 2.0, 5.0):
        cs.append(C('botao', 6.4, 7.2, y, y + 0.8, 5.4, 5.6, 'ouro'))
    cs.append(C('bolso', 9.2, 11.6, -5.0, -2.6, 5.4, 5.65, 'tecido2', so=('north',)))
    cs.append(C('caveira_bolso', 9.6, 11.2, -3.0, -1.4, 5.0, 5.8, 'pano'))
    cs.append(C('cachecol', 3.4, 12.6, 6.4, 8.6, 5.2, 11.0, 'corda'))
    cs.append(C('cachecol_ponta', 4.4, 6.2, 1.0, 6.6, 4.8, 5.4, 'corda'))
    for lado in (-1, 1):
        cx = 8 + lado * 5.4
        rot = rot_d(susto and lado == 1, cx, 7.6)
        cs.append(C('manga', cx - 1.5, cx + 1.5, -1.4, 7.6, 6.6, 9.6, 'tecido', rot))
        cs.append(C('punho', cx - 1.7, cx + 1.7, -2.2, -0.8, 6.4, 9.8, 'tecido2', rot))
        cs.append(C('mao', cx - 1.0, cx + 1.0, -4.4, -2.0, 7.1, 9.1, 'pele', rot))
        for d in (-0.5, 0.0, 0.5):
            cs.append(C('dedo', cx + d - 0.2, cx + d + 0.2, -6.0, -4.2, 7.2, 7.7, 'pele', rot))
    # pa grande na direita e lampiao na esquerda
    cx = 8 + 5.4
    rot = rot_d(susto, cx, 7.6)
    cs.append(C('cabo', cx - 0.4, cx + 0.4, -6.0, 12.0, 5.2, 6.0, 'ouro', rot))
    cs.append(C('cabo_alca', cx - 1.2, cx + 1.2, 11.6, 12.6, 5.2, 6.0, 'ouro', rot))
    cs.append(C('pa', cx - 2.0, cx + 2.0, -12.4, -6.0, 5.0, 6.2, 'sapato', rot))
    cs.append(C('pa_terra', cx - 1.8, cx + 1.8, -12.0, -9.6, 4.8, 5.1, 'corda', rot))
    cx = 8 - 5.4
    cs.append(C('lampiao_alca', cx - 0.3, cx + 0.3, -6.4, -4.4, 7.7, 8.3, 'tecido2'))
    cs.append(C('lampiao', cx - 1.2, cx + 1.2, -9.6, -6.4, 6.8, 9.2, 'tecido2'))
    cs.append(C('lampiao_luz', cx - 0.8, cx + 0.8, -9.0, -7.0, 6.6, 9.4, 'pupila', **LUZ))
    # cabeca palida e magra, olhos fundos, cartola alta e amassada
    cs.append(C('cabeca', 5.4, 10.6, 8.4, 14.0, 5.4, 10.6, 'pele'))
    for x in (6.6, 9.4):
        cs.append(C('olho_fundo', x - 0.9, x + 0.9, 11.0, 12.4, 5.25, 5.45, 'boca', so=('north',)))
        cs.append(C('olho', x - 0.4, x + 0.4, 11.3, 12.1, 5.15, 5.3, 'pupila', so=('north',), **LUZ))
    cs.append(C('nariz', 7.5, 8.5, 9.6, 11.2, 4.6, 5.5, 'pele'))
    mand = ('x', -22.5, (8.0, 8.8, 6.0)) if susto else None
    cs.append(C('boca', 6.4, 9.6, 8.8, 9.4, 5.25, 5.45, 'boca', so=('north',)))
    A.dentes(cs, 6.6, 9.4, 8.9, 9.4, 5.15, 5.35, passo=0.9, larg=0.45, rot=mand, nome='dente_b')
    cs.append(C('aba', 3.0, 13.0, 13.8, 14.6, 3.0, 13.0, 'tecido2'))
    cs.append(C('cartola', 4.8, 11.2, 14.4, 19.6, 4.8, 11.2, 'tecido2'))
    cs.append(C('fita', 4.7, 11.3, 14.6, 15.8, 4.7, 11.3, 'corda'))
    cs.append(C('cartola_topo', 5.2, 10.8, 19.4, 21.0, 5.2, 10.8, 'tecido2', ('z', 22.5, (8.0, 19.4, 8.0))))
    cs.append(C('vela', 9.4, 10.4, 15.6, 17.4, 4.5, 5.0, 'pano'))
    cs.append(C('chama_vela', 9.6, 10.2, 17.4, 18.4, 4.55, 4.95, 'pupila', **LUZ))
    return cs


# ------------------------------------------------------------ 20) Gosma Primordial (Esgoto Gosmento) - bolha
def gosma(susto):
    cs = []
    abre = 2.0 if susto else 0.0
    # corpo de gosma em camadas (mais largo embaixo), escorrendo
    cs.append(C('corpo', -1.0, 17.0, -16.0, -4.0, -1.0, 17.0, 'tecido'))
    cs.append(C('corpo_cima', 0.6, 15.4, -4.2, 4.0, 0.6, 15.4, 'tecido'))
    cs.append(C('topo', 2.6, 13.4, 3.8, 6.6, 2.6, 13.4, 'tecido'))
    for x, z, h in ((-1.2, 3.0, 4.0), (16.0, 6.0, 5.0), (4.0, -1.2, 6.0), (11.0, -1.2, 3.6), (3.0, 16.2, 4.6), (12.0, 16.2, 5.4)):
        cs.append(C('pingo', x, x + 2.0, -16.0, -16.0 + h, z, z + 1.4 if z < 0 or z > 15 else z + 2.0, 'tecido2'))
    # bolhas na superficie
    for x, y, z in ((2.0, -8.0, -1.4), (13.0, -11.0, -1.4), (5.0, 1.0, 0.2), (15.6, -6.0, 9.0), (-1.4, -10.0, 12.0)):
        cs.append(C('bolha', x, x + 1.6, y, y + 1.6, z, z + 1.6, 'pano'))
    # o que ela engoliu: espada, capacete e ossos saindo
    cs.append(C('espada', 14.0, 14.8, -2.0, 9.0, 4.0, 4.8, 'sapato', ('z', -22.5, (14.4, -2.0, 4.4))))
    cs.append(C('espada_guarda', 12.4, 16.4, -2.6, -1.8, 3.8, 5.0, 'ouro', ('z', -22.5, (14.4, -2.0, 4.4))))
    cs.append(C('capacete', 1.0, 4.6, -15.0, -12.4, -1.6, 2.0, 'sapato'))
    cs.append(C('osso', 9.0, 13.0, -14.6, -13.8, -1.4, -0.8, 'pano'))
    cs.append(C('caveira', 4.0, 6.6, 0.4, 3.0, -0.6, 1.0, 'pano'))
    # rosto: olhos grandes acesos e boca enorme
    for x in (4.6, 11.4):
        cs.append(C('olho_fundo', x - 2.0, x + 2.0, -3.6, 0.8, -1.25, -1.0, 'boca', so=('north',)))
        cs.append(C('olho', x - 1.2, x + 1.2, -2.8, 0.0, -1.35, -1.2, 'pupila', so=('north',), **LUZ))
    cs.append(C('boca', 3.0, 13.0, -12.0 - abre, -7.0, -1.25, -1.0, 'boca', so=('north',)))
    A.dentes(cs, 3.4, 12.6, -8.2, -7.2, -1.4, -1.1, passo=1.3, larg=0.7)
    A.dentes(cs, 3.8, 12.2, -12.0 - abre, -11.0 - abre, -1.4, -1.1, passo=1.3, larg=0.7, nome='dente_b')
    # coroa de gosma escorrendo
    cs.append(C('coroa', 3.0, 13.0, 6.4, 7.8, 3.0, 13.0, 'ouro'))
    for x, h in ((3.6, 2.6), (8.0, 3.8), (12.4, 2.6)):
        cs.append(C('coroa_ponta', x - 0.6, x + 0.6, 7.6, 7.6 + h, 3.2, 4.4, 'ouro'))
    cs.append(C('joia', 7.3, 8.7, 6.6, 7.6, 2.8, 3.05, 'pupila', so=('north',), **LUZ))
    return cs


CHEFES_B = [
    ('c_carcereiro', carcereiro, ((0.25, 0.95, 1.0), dict(pele=mat((0.07, 0.14, 0.17), 0.3, 24), tecido=mat((0.24, 0.25, 0.28), 0.85, 60, 0.7),
     tecido2=mat((0.14, 0.14, 0.16), 0.85, 60, 0.6), corda=mat((0.26, 0.17, 0.10)), ouro=mat((0.55, 0.45, 0.20), 0.85, 60, 0.6))), 'O Carcereiro', 'Prisão Abissal · nível 5'),
    ('c_farao', farao, ((0.35, 1.0, 0.45), dict(pele=mat((0.62, 0.55, 0.40)), pano=mat((0.84, 0.78, 0.62)), tecido=mat((0.12, 0.22, 0.62), 0.5, 40, 0.3),
     tecido2=mat((0.50, 0.42, 0.28)), ouro=mat((0.98, 0.78, 0.22), 0.95, 70, 0.95))), 'Faraó', 'Tumba do Faraó · nível 2'),
    ('c_devastador', devastador, ((1.0, 0.25, 0.15), dict(pele=mat((0.30, 0.30, 0.33), 0.2, 16), tecido=mat((0.36, 0.37, 0.40), 0.85, 60, 0.7),
     tecido2=mat((0.40, 0.08, 0.10)), pano=mat((0.86, 0.82, 0.70)), corda=mat((0.34, 0.22, 0.12)), ouro=mat((0.62, 0.62, 0.66), 0.9, 60, 0.8))), 'Devastador', 'Quartel dos Saqueadores · nível 3'),
    ('c_ventos', ventos, ((0.70, 1.0, 1.0), dict(tecido=mat((0.55, 0.62, 0.78), 0.4, 30, 0.2), tecido2=mat((0.30, 0.36, 0.52), 0.4, 30, 0.2),
     pano=mat((0.86, 0.94, 1.0), 0.2, 20))), 'Senhor dos Ventos', 'Santuário dos Ventos · nível 4'),
    ('c_afogado', afogado, ((0.35, 1.0, 0.90), dict(pele=mat((0.30, 0.52, 0.48)), tecido=mat((0.26, 0.56, 0.52), 0.6, 50, 0.5), tecido2=mat((0.10, 0.22, 0.30)),
     corda=mat((0.16, 0.40, 0.20)), pano=mat((0.80, 0.78, 0.70)), ouro=mat((0.95, 0.45, 0.50), 0.4, 30, 0.2))), 'Rei Afogado', 'Catacumba Afogada · nível 2'),
    ('c_xama', xama, ((0.45, 1.0, 0.35), dict(pele=mat((0.52, 0.36, 0.26)), tecido=mat((0.16, 0.44, 0.16)), tecido2=mat((0.10, 0.30, 0.62)),
     corda=mat((0.46, 0.30, 0.16)), pano=mat((0.88, 0.85, 0.76)), ouro=mat((0.95, 0.30, 0.18), 0.3, 20, 0.1))), 'Xamã da Selva', 'Templo da Selva · nível 3'),
    ('c_carrasco', carrasco, ((1.0, 0.15, 0.10), dict(pele=mat((0.62, 0.46, 0.38)), tecido=mat((0.26, 0.26, 0.29), 0.85, 60, 0.7), tecido2=mat((0.06, 0.06, 0.07)),
     corda=mat((0.34, 0.20, 0.12)), pano=mat((0.80, 0.80, 0.84), 0.9, 70, 0.8))), 'Carrasco', 'Castelo do Carrasco · nível 4'),
    ('c_zoglin', zoglin, ((1.0, 0.35, 0.20), dict(pele=mat((0.62, 0.48, 0.50)), tecido=mat((0.30, 0.30, 0.32), 0.85, 60, 0.6), tecido2=mat((0.24, 0.18, 0.16)),
     pano=mat((0.90, 0.88, 0.80)), corda=mat((0.42, 0.62, 0.30)))), 'Besta Zoglin', 'Covil da Besta · nível 4'),
    ('c_coveiro', coveiro, ((1.0, 0.75, 0.30), dict(pele=mat((0.62, 0.66, 0.60)), tecido=mat((0.12, 0.12, 0.14)), tecido2=mat((0.06, 0.06, 0.07)),
     corda=mat((0.44, 0.10, 0.10)), pano=mat((0.86, 0.84, 0.76)), ouro=mat((0.42, 0.28, 0.15), 0.1, 10, 0.0), sapato=mat((0.40, 0.40, 0.44), 0.85, 60, 0.6))), 'O Coveiro', 'Cemitério Esquecido · nível 1'),
    ('c_gosma', gosma, ((0.85, 1.0, 0.20), dict(tecido=mat((0.36, 0.72, 0.22), 0.3, 50, 0.4), tecido2=mat((0.26, 0.56, 0.16), 0.3, 50, 0.4),
     pano=mat((0.62, 0.92, 0.46), 0.4, 60, 0.5), ouro=mat((0.92, 0.80, 0.20), 0.9, 60, 0.8), sapato=mat((0.50, 0.52, 0.56), 0.85, 60, 0.6))), 'Gosma Primordial', 'Esgoto Gosmento · nível 1'),
]
K.LISO.update({'c_carcereiro': {'pele', 'tecido', 'tecido2', 'ouro'}, 'c_farao': {'ouro', 'tecido'}, 'c_devastador': {'pele', 'tecido', 'pano', 'ouro'},
               'c_ventos': {'tecido', 'tecido2', 'pano'}, 'c_afogado': {'pele', 'tecido', 'ouro', 'pano'}, 'c_xama': {'pele', 'pano', 'corda', 'ouro'},
               'c_carrasco': {'pele', 'tecido', 'pano'}, 'c_zoglin': {'pele', 'tecido', 'pano'}, 'c_coveiro': {'pele', 'pano', 'sapato', 'ouro'},
               'c_gosma': {'tecido', 'tecido2', 'pano', 'ouro', 'sapato'}})
for ident, f, cfg, _, _ in CHEFES_B:
    A.PALETAS[ident] = dict(pupila=cfg[0])
    H.MATS[ident] = mats(**cfg[1])
    H.CORPOS[ident] = f

if __name__ == '__main__':
    so = sys.argv[2:] or [c[0] for c in CHEFES_B]
    A.gerar(A.SAIDA, so)
