"""Chefes 15-20 REFEITOS (18/11, Paulo: "15 pra frente ta ruim"): silhueta propria, mais camadas e detalhe, no nivel do
Carcereiro e do Farao. Substitui os desenhos do chefes3d_b (mesmos ids, mesmas juntas).
uso: PYTHONPATH=plugins/coronapack:ferramentas/modelos python3 chefes3d_c.py <saida> [ids]"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import chefes3d_b as B  # noqa: E402

K, H, A, C, LUZ = B.K, B.H, B.A, B.C, B.LUZ
mat, mats, olhos, rot_d = K.mat, K.mats, K.olhos, B.rot_d


def z45(x, y, z, s):
    return ('z', s, (x, y, z))


# ------------------------------------------------------------ 15) Rei Afogado
def afogado(susto):
    cs = []
    # pernas magras com algas enroladas e grevas de ouro corroido
    for x in (5.4, 10.6):
        cs.append(C('perna', x - 1.3, x + 1.3, -13.0, -2.0, 6.7, 9.3, 'pele'))
        for y in (-11.0, -7.4, -4.0):
            cs.append(C('alga_perna', x - 1.5, x + 1.5, y, y + 1.0, 6.5, 9.5, 'corda'))
        cs.append(C('bota', x - 1.8, x + 1.8, -16.0, -12.6, 5.4, 9.8, 'tecido'))
        cs.append(C('joelheira', x - 1.7, x + 1.7, -9.4, -7.4, 6.2, 6.8, 'tecido'))
        cs.append(C('craca', x - 0.4, x + 0.4, -8.8, -8.0, 6.0, 6.3, 'pano'))
    # manto real rasgado aberto na frente, com barra de ouro
    for lado in (-1, 1):
        x0, x1 = (3.0, 7.0) if lado < 0 else (9.0, 13.0)
        cs.append(C('manto_frente', x0, x1, -12.0, 8.0, 4.6, 5.4, 'tecido2'))
        cs.append(C('manto_barra', x0, x1, -12.6, -11.4, 4.5, 5.5, 'tecido'))
        xb = x1 - 0.7 if lado < 0 else x0
        cs.append(C('manto_fio', xb, xb + 0.7, -12.0, 8.0, 4.45, 4.6, 'tecido', so=('north',)))
        for i, y in enumerate((-12.6, -11.0)):
            cs.append(C('manto_rasgo', x0 + 0.6 + i * 1.6, x0 + 1.6 + i * 1.6, y - 1.8, y, 4.7, 5.3, 'tecido2'))
    cs.append(C('cinto', 3.4, 12.6, -2.4, -0.8, 5.0, 11.0, 'tecido'))
    for x in (5.0, 7.0, 9.0, 11.0):
        cs.append(C('corrente', x - 0.5, x + 0.5, -3.6, -2.2, 4.8, 5.2, 'tecido'))
    # peito aberto: costelas e o coracao de perola aceso
    cs.append(C('tronco', 4.4, 11.6, -1.0, 8.4, 6.0, 10.6, 'pele'))
    for k in range(4):
        y = 0.6 + k * 1.6
        cs.append(C('costela', 4.6, 7.4, y, y + 0.7, 5.6, 6.2, 'pano'))
        cs.append(C('costela', 8.6, 11.4, y, y + 0.7, 5.6, 6.2, 'pano'))
    cs.append(C('esterno', 7.5, 8.5, 0.4, 7.4, 5.4, 6.1, 'pano'))
    cs.append(C('coracao', 6.6, 9.4, 2.2, 4.8, 6.0, 8.6, 'pupila', **LUZ))
    # ombreiras enormes de ouro corroido com cracas e coral roxo
    for lado in (-1, 1):
        cx = 8 + lado * 6.6
        rot = rot_d(susto and lado == 1, cx, 8.0)
        cs.append(C('ombreira', cx - 3.0, cx + 3.0, 6.4, 10.4, 4.6, 11.4, 'tecido', rot))
        cs.append(C('ombreira2', cx - 2.4 + lado * 0.8, cx + 2.4 + lado * 0.8, 10.2, 11.4, 5.4, 10.6, 'tecido', rot))
        for dz, h in ((5.6, 3.0), (7.6, 4.6), (9.6, 3.4)):
            cs.append(C('coral_ombro', cx + lado * 1.6 - 0.5, cx + lado * 1.6 + 0.5, 11.2, 11.2 + h, dz - 0.5, dz + 0.5, 'ouro', rot))
            cs.append(C('coral_ombro_ramo', cx + lado * 1.6 - 1.2, cx + lado * 1.6 + 1.2, 11.2 + h * 0.6, 11.2 + h * 0.6 + 0.6, dz - 0.4, dz + 0.4, 'ouro', rot))
        for dx, y in ((-1.6, 7.2), (1.0, 8.6), (2.2, 7.0)):
            cs.append(C('craca_ombro', cx + dx - 0.55, cx + dx + 0.55, y, y + 1.1, 4.3, 4.7, 'pano', rot))
        # braco comprido ate o joelho, mao de garra
        cs.append(C('braco', cx - 1.3, cx + 1.3, -3.0, 7.0, 6.7, 9.3, 'pele', rot))
        cs.append(C('alga_braco', cx - 1.45, cx + 1.45, 2.0, 3.0, 6.55, 9.45, 'corda', rot))
        cs.append(C('bracelete', cx - 1.6, cx + 1.6, -3.4, -1.8, 6.4, 9.6, 'tecido', rot))
        cs.append(C('mao', cx - 1.4, cx + 1.4, -5.8, -3.2, 6.6, 9.4, 'pele', rot))
        for d in (-0.8, 0.0, 0.8):
            cs.append(C('garra', cx + d - 0.25, cx + d + 0.25, -7.8, -5.6, 6.7, 7.4, 'pano', rot))
    # tridente grande de ouro corroido, pontas farpadas acesas e corrente enrolada
    cx = 8 + 6.6
    rot = rot_d(susto, cx, 8.0)
    cs.append(C('cabo', cx - 0.5, cx + 0.5, -16.0, 14.0, 4.6, 5.6, 'tecido', rot))
    for y in (-10.0, -4.0, 2.0, 8.0):
        cs.append(C('cabo_aro', cx - 0.7, cx + 0.7, y, y + 0.9, 4.4, 5.8, 'ouro', rot))
    cs.append(C('tridente_base', cx - 3.0, cx + 3.0, 13.6, 15.0, 4.5, 5.7, 'tecido', rot))
    for d, h in ((-2.6, 4.0), (0.0, 5.6), (2.6, 4.0)):
        cs.append(C('ponta', cx + d - 0.45, cx + d + 0.45, 14.8, 14.8 + h, 4.6, 5.6, 'tecido', rot))
        cs.append(C('farpa', cx + d - 1.0, cx + d + 1.0, 14.8 + h - 1.6, 14.8 + h - 0.9, 4.7, 5.5, 'tecido', rot))
        cs.append(C('ponta_luz', cx + d - 0.25, cx + d + 0.25, 14.8 + h - 0.6, 14.8 + h + 0.6, 4.55, 5.65, 'pupila', rot, **LUZ))
    # cabeca meio caveira, cabelo de algas comprido, coroa corroida com coral e perolas
    cs.append(C('cabeca', 5.0, 11.0, 8.2, 14.4, 4.8, 10.8, 'pele'))
    cs.append(C('meia_caveira', 8.2, 11.1, 8.2, 13.0, 4.6, 6.4, 'pano'))
    for x in (6.5, 9.6):
        cs.append(C('olho_fundo', x - 1.0, x + 1.0, 10.8, 12.4, 4.4, 4.62, 'boca', so=('north',)))
        cs.append(C('olho', x - 0.5, x + 0.5, 11.2, 12.0, 4.3, 4.45, 'pupila', so=('north',), **LUZ))
    cs.append(C('boca', 6.0, 10.0, 8.8, 9.8, 4.45, 4.62, 'boca', so=('north',)))
    A.dentes(cs, 6.2, 9.8, 9.0, 9.8, 4.3, 4.5, passo=0.7, larg=0.45)
    cs.append(C('barba', 5.2, 8.2, 5.6, 9.0, 4.2, 5.0, 'corda'))
    for x in (5.6, 6.8):
        cs.append(C('barba_ponta', x - 0.4, x + 0.4, 3.4, 5.8, 4.3, 4.9, 'corda'))
    cs.append(C('cabelo', 4.4, 11.6, 1.0, 14.0, 9.8, 11.6, 'corda'))
    for x in (4.2, 11.0):
        cs.append(C('cabelo_lado', x - 0.7, x + 0.7, 2.0, 13.4, 6.0, 10.6, 'corda'))
    cs.append(C('coroa', 4.4, 11.6, 14.0, 15.6, 4.4, 11.2, 'tecido'))
    for i, (x, h) in enumerate(((4.9, 2.8), (6.4, 3.8), (8.0, 5.0), (9.6, 3.8), (11.1, 2.8))):
        cs.append(C('coroa_ponta', x - 0.45, x + 0.45, 15.4, 15.4 + h, 4.5, 5.4, 'tecido'))
        if i % 2 == 0:
            cs.append(C('perola', x - 0.45, x + 0.45, 15.4 + h - 0.1, 15.4 + h + 0.8, 4.5, 5.4, 'pupila', **LUZ))
    for x, h in ((6.0, 3.0), (10.4, 2.4)):
        cs.append(C('coral', x - 0.4, x + 0.4, 15.4, 15.4 + h, 9.4, 10.4, 'ouro'))
        cs.append(C('coral_ramo', x - 1.1, x + 1.1, 15.4 + h * 0.6, 15.4 + h * 0.6 + 0.6, 9.5, 10.3, 'ouro'))
    # capa real rasgada em tiras de tamanhos diferentes
    cs.append(C('capa', 3.0, 13.0, -6.0, 8.6, 11.0, 11.8, 'tecido2', ('x', -22.5, (8.0, 8.6, 11.4))))
    for i, x in enumerate((3.0, 5.0, 7.0, 9.0, 11.0)):
        fim = (-15.0, -11.0, -14.2, -9.6, -13.4)[i]
        cs.append(C('capa_tira', x, x + 2.0, fim, -5.8, 11.0, 11.8, 'tecido2', ('x', -22.5, (8.0, 8.6, 11.4))))
    cs.append(C('capa_gola', 3.0, 13.0, 7.6, 9.8, 9.6, 11.8, 'tecido'))
    return cs


# ------------------------------------------------------------ 16) Xama da Selva
def xama(susto):
    cs = []
    # pernas com perneiras de pelo, pes descalcos
    for x in (6.0, 10.0):
        cs.append(C('perna', x - 1.3, x + 1.3, -14.6, -2.0, 6.7, 9.3, 'pele'))
        cs.append(C('perneira', x - 1.6, x + 1.6, -11.6, -7.0, 6.4, 9.6, 'tecido2'))
        cs.append(C('pintura', x - 1.45, x + 1.45, -5.0, -4.3, 6.55, 9.45, 'pupila', **LUZ))
        cs.append(C('pe', x - 1.4, x + 1.4, -16.0, -14.4, 5.2, 9.4, 'pele'))
    # saiote de folhas e penduricalhos de osso
    for i, y in enumerate((-7.0, -4.6, -2.2)):
        cs.append(C('folhas', 3.6 + i * 0.3, 12.4 - i * 0.3, y, y + 2.6, 5.6 - i * 0.2, 10.4 + i * 0.2, 'tecido'))
    for i, x in enumerate((4.0, 5.6, 7.2, 8.8, 10.4, 12.0)):
        cs.append(C('folha_ponta', x - 0.6, x + 0.6, -9.6 + (i % 2) * 0.8, -7.0, 5.4, 5.8, 'tecido', so=('north', 'south', 'east', 'west', 'down')))
    for x in (5.0, 11.0):
        cs.append(C('osso_pendurado', x - 0.3, x + 0.3, -8.0, -4.6, 5.2, 5.6, 'pano'))
    cs.append(C('cinto', 3.6, 12.4, -0.4, 0.8, 5.6, 10.4, 'corda'))
    # tronco magro curvado com pinturas acesas
    cs.append(C('tronco', 4.6, 11.4, 0.4, 7.0, 6.4, 9.8, 'pele'))
    for y in (1.6, 3.4):
        cs.append(C('pintura', 4.5, 11.5, y, y + 0.55, 6.3, 9.9, 'pupila', so=('north',), **LUZ))
    # manto de pelo e folhas sobre os ombros, caindo dos lados
    cs.append(C('manto', 3.0, 13.0, 4.6, 8.2, 5.6, 11.0, 'tecido2'))
    for lado in (-1, 1):
        x0 = 8 + lado * 5.0
        for k, y in enumerate((2.4, 0.4, -1.8)):
            cs.append(C('manto_folha', x0 - 0.8 + lado * k * 0.3, x0 + 0.8 + lado * k * 0.3, y, y + 2.4, 5.4, 10.6, 'tecido'))
    cs.append(C('manto_costas', 4.0, 12.0, -8.0, 7.0, 10.6, 11.4, 'tecido2'))
    # colar de dentes e o medalhao
    cs.append(C('colar', 4.4, 11.6, 6.0, 6.9, 5.8, 10.0, 'corda'))
    for x in (5.0, 6.2, 7.4, 8.6, 9.8, 11.0):
        cs.append(C('dente_colar', x - 0.25, x + 0.25, 4.6, 6.2, 5.8, 6.2, 'pano'))
    cs.append(C('medalhao', 6.8, 9.2, 2.6, 5.0, 5.6, 6.2, 'ouro'))
    cs.append(C('medalhao_luz', 7.4, 8.6, 3.2, 4.4, 5.5, 5.65, 'pupila', so=('north',), **LUZ))
    for lado in (-1, 1):
        cx = 8 + lado * 5.2
        rot = rot_d(susto and lado == 1, cx, 7.0)
        cs.append(C('braco', cx - 1.1, cx + 1.1, -1.4, 7.0, 7.1, 9.3, 'pele', rot))
        cs.append(C('bracelete', cx - 1.3, cx + 1.3, -1.8, -0.4, 6.9, 9.5, 'corda', rot))
        cs.append(C('pintura_braco', cx - 1.2, cx + 1.2, 3.4, 4.0, 7.0, 9.4, 'pupila', rot, **LUZ))
        cs.append(C('mao', cx - 1.0, cx + 1.0, -3.8, -1.4, 7.2, 9.2, 'pele', rot))
        for d in (-0.5, 0.0, 0.5):
            cs.append(C('dedo', cx + d - 0.2, cx + d + 0.2, -5.4, -3.6, 7.3, 7.8, 'pele', rot))
    # cajado retorcido com caveira, orbe aceso, penas e ossos pendurados
    cx = 8 + 5.2
    rot = rot_d(susto, cx, 7.0)
    for i, y in enumerate(range(-16, 12, 4)):
        dz = (0.0, 0.4, 0.0, -0.4, 0.0, 0.4, 0.0)[i]
        cs.append(C('cajado', cx - 0.5, cx + 0.5, y, y + 4.0, 5.4 + dz, 6.4 + dz, 'corda', rot))
    cs.append(C('caveirinha', cx - 1.5, cx + 1.5, 12.0, 14.8, 4.6, 7.4, 'pano', rot))
    cs.append(C('caveira_olho', cx - 1.0, cx - 0.3, 13.2, 13.9, 4.5, 4.65, 'pupila', rot, so=('north',), **LUZ))
    cs.append(C('caveira_olho', cx + 0.3, cx + 1.0, 13.2, 13.9, 4.5, 4.65, 'pupila', rot, so=('north',), **LUZ))
    cs.append(C('orbe', cx - 1.1, cx + 1.1, 14.8, 17.0, 4.9, 7.1, 'pupila', rot, **LUZ))
    for d, c in ((-1.8, 'ouro'), (1.8, 'tecido')):
        cs.append(C('pena_cajado', cx + d - 0.3, cx + d + 0.3, 8.0, 12.6, 5.7, 6.1, c, rot))
    cs.append(C('osso_cajado', cx - 1.4, cx - 1.0, 9.0, 11.6, 6.3, 6.7, 'pano', rot))
    # mascara de madeira enorme com chifres de galhada e leque de penas atras
    cs.append(C('cabeca', 5.4, 10.6, 7.0, 12.4, 5.8, 10.6, 'pele'))
    cs.append(C('mascara', 4.2, 11.8, 6.0, 14.6, 4.2, 5.8, 'corda'))
    cs.append(C('mascara_testa', 4.6, 11.4, 13.0, 15.4, 4.0, 5.0, 'corda'))
    for x in (6.2, 9.8):
        cs.append(C('olho_fundo', x - 1.1, x + 1.1, 10.4, 12.2, 4.05, 4.25, 'boca', so=('north',)))
        cs.append(C('olho', x - 0.5, x + 0.5, 10.9, 11.7, 3.95, 4.1, 'pupila', so=('north',), **LUZ))
        cs.append(C('sobrancelha_masc', x - 1.3, x + 1.3, 12.2, 13.0, 3.7, 4.3, 'corda'))
    cs.append(C('nariz_masc', 7.3, 8.7, 8.4, 11.0, 3.2, 4.3, 'corda'))
    cs.append(C('boca_masc', 5.6, 10.4, 6.6, 8.0, 4.05, 4.25, 'boca', so=('north',)))
    A.dentes(cs, 5.8, 10.2, 7.0, 8.0, 3.9, 4.1, passo=0.8, larg=0.45)
    A.dentes(cs, 6.2, 9.8, 6.6, 7.2, 3.9, 4.1, passo=0.8, larg=0.45, nome='dente_masc')
    for x in (4.4, 11.0):
        cs.append(C('pintura_masc', x, x + 0.6, 7.0, 13.6, 4.05, 4.2, 'pupila', so=('north',), **LUZ))
    cs.append(C('pintura_masc', 6.4, 9.6, 13.6, 14.2, 3.85, 4.0, 'pupila', so=('north',), **LUZ))
    # galhada (chifres de galho) dos dois lados
    for lado in (-1, 1):
        x0 = 8 + lado * 3.0
        cs.append(C('chifre_masc', x0 - 0.5, x0 + 0.5, 15.0, 18.6, 6.6, 7.6, 'pano'))
        cs.append(C('chifre_masc', min(x0, x0 + lado * 3.4), max(x0, x0 + lado * 3.4), 17.6, 18.4, 6.7, 7.5, 'pano'))
        cs.append(C('chifre_masc', x0 + lado * 3.0 - 0.4, x0 + lado * 3.0 + 0.4, 18.2, 21.0, 6.7, 7.5, 'pano'))
        cs.append(C('chifre_masc', x0 + lado * 1.4 - 0.35, x0 + lado * 1.4 + 0.35, 18.2, 20.0, 6.8, 7.4, 'pano'))
    # leque de penas coloridas atras da mascara (abertas em leque)
    cores = ('ouro', 'tecido', 'pupila', 'ouro', 'pupila', 'tecido', 'ouro')
    for i in range(7):
        ang = (i - 3) * 22.5 if abs(i - 3) <= 2 else (45.0 if i > 3 else -45.0)
        cs.append(C('pena', 7.4, 8.6, 13.0, 22.0 - abs(i - 3) * 0.8, 9.0 + i * 0.12, 9.6 + i * 0.12, cores[i], ('z', -ang, (8.0, 12.0, 9.3)),
                    **(LUZ if cores[i] == 'pupila' else {})))
    cs.append(C('faixa_cocar', 4.4, 11.6, 12.6, 14.0, 5.8, 10.8, 'ouro'))
    return cs


# ------------------------------------------------------------ 17) Carrasco
def carrasco(susto):
    cs = []
    for x in (5.2, 10.8):
        cs.append(C('perna', x - 2.0, x + 2.0, -13.0, -2.0, 6.0, 10.0, 'tecido2'))
        cs.append(C('bota', x - 2.4, x + 2.4, -16.0, -11.6, 5.0, 10.4, 'sapato'))
        cs.append(C('bota_tira', x - 2.5, x + 2.5, -13.4, -12.6, 4.9, 10.5, 'corda'))
        cs.append(C('bota_fivela', x - 0.6, x + 0.6, -13.6, -12.4, 4.7, 5.0, 'ouro'))
    # barriga grande, avental de couro manchado e a corda de forca pendurada
    cs.append(C('barriga', 2.4, 13.6, -2.6, 4.0, 4.4, 11.4, 'pele'))
    cs.append(C('avental', 3.6, 12.4, -12.0, 3.0, 4.0, 4.5, 'corda', so=('north', 'east', 'west', 'down')))
    for x, y, h in ((5.0, -6.0, 3.0), (9.4, -9.0, 2.4), (7.2, -2.0, 2.0)):
        cs.append(C('mancha', x, x + 1.6, y, y + h, 3.85, 4.05, 'tecido', so=('north',)))
    cs.append(C('cinto', 2.2, 13.8, -2.8, -0.8, 4.2, 11.6, 'corda'))
    cs.append(C('fivela', 6.8, 9.2, -3.2, -0.4, 3.8, 4.3, 'ouro'))
    cs.append(C('forca_corda', 2.2, 3.0, -9.0, -1.0, 7.4, 8.2, 'tecido2'))
    cs.append(C('forca_laco', 1.6, 3.6, -11.4, -9.0, 7.0, 8.6, 'tecido2'))
    cs.append(C('gancho_cinto', 13.2, 14.0, -8.0, -1.0, 7.4, 8.2, 'tecido'))
    # peito largo e nu, cicatrizes e tiras em X com argola
    cs.append(C('peito', 1.8, 14.2, 3.6, 10.2, 4.6, 11.4, 'pele'))
    cs.append(C('peito_frente', 2.6, 13.4, 6.4, 9.8, 4.2, 5.0, 'pele'))
    cs.append(C('tira', 1.8, 14.2, 5.2, 6.2, 4.0, 4.4, 'corda', ('z', 22.5, (8.0, 5.7, 4.2)), so=('north',)))
    cs.append(C('tira', 1.8, 14.2, 5.2, 6.2, 4.0, 4.4, 'corda', ('z', -22.5, (8.0, 5.7, 4.2)), so=('north',)))
    cs.append(C('argola', 7.0, 9.0, 4.8, 6.6, 3.9, 4.2, 'ouro'))
    for x, y in ((4.4, 8.0), (11.0, 7.0)):
        cs.append(C('cicatriz', x - 0.2, x + 0.2, y - 1.4, y + 1.4, 4.15, 4.3, 'tecido', so=('north',)))
    # gola de correntes
    for i in range(7):
        cs.append(C('elo', 3.0 + i * 1.5, 4.2 + i * 1.5, 9.6, 10.6, 4.0 + (i % 2) * 0.2, 4.6 + (i % 2) * 0.2, 'tecido'))
    # ombreira grande so de um lado (esquerdo), braco direito nu com tiras
    for lado in (-1, 1):
        cx = 8 + lado * 7.4
        rot = ('x', 45.0, (cx, 8.8, 8.0)) if susto else None
        if lado < 0:
            cs.append(C('ombreira', cx - 3.2, cx + 3.2, 6.6, 11.6, 4.4, 11.6, 'tecido', rot))
            cs.append(C('ombreira2', cx - 2.6, cx + 2.6, 4.6, 6.8, 5.0, 11.0, 'tecido', rot))
            for dz, h in ((5.4, 2.4), (8.0, 4.0), (10.6, 2.4)):
                cs.append(C('espeto', cx - 1.4, cx - 0.6, 11.4, 11.4 + h, dz - 0.4, dz + 0.4, 'pano', rot))
        else:
            cs.append(C('ombro', cx - 2.4, cx + 2.4, 6.4, 10.4, 5.4, 10.6, 'pele', rot))
            cs.append(C('tira_ombro', cx - 2.5, cx + 2.5, 8.0, 8.8, 5.3, 10.7, 'corda', rot))
        cs.append(C('braco', cx - 2.0, cx + 2.0, -1.6, 7.2, 6.0, 10.0, 'pele', rot))
        cs.append(C('bracadeira', cx - 2.3, cx + 2.3, -3.8, 0.8, 5.7, 10.3, 'corda', rot))
        for y in (-3.0, -1.4, 0.2):
            cs.append(C('cravo', cx + lado * 2.2 - 0.35, cx + lado * 2.2 + 0.35, y, y + 0.7, 7.65, 8.35, 'pano', rot))
        cs.append(C('mao', cx - 1.7, cx + 1.7, -6.2, -3.6, 6.3, 9.7, 'pele', rot))
    # machado de carrasco: lamina em meia-lua dos dois lados, cabo enrolado
    cx = 8 + 7.4
    rot = ('x', 45.0, (cx, 8.8, 8.0)) if susto else None
    cs.append(C('cabo', cx - 0.65, cx + 0.65, -14.0, 17.0, 2.6, 3.9, 'corda', rot))
    for y in (-13.0, -8.0, -3.0):
        cs.append(C('cabo_aro', cx - 0.85, cx + 0.85, y, y + 1.2, 2.4, 4.1, 'tecido2', rot))
    cs.append(C('cabo_ponta', cx - 0.9, cx + 0.9, 16.6, 18.4, 2.3, 4.2, 'ouro', rot))
    # lamina da frente (grande, meia-lua) e a de tras (menor)
    for i, (h0, h1, z0) in enumerate(((8.0, 16.0, 1.4), (7.0, 17.0, -0.6), (6.0, 18.0, -2.6), (6.6, 17.4, -4.4), (8.2, 15.8, -5.8))):
        cs.append(C('machado', cx - 0.45, cx + 0.45, h0, h1, z0, z0 + 2.0, 'tecido', rot))
    cs.append(C('fio', cx - 0.3, cx + 0.3, 8.6, 15.4, -6.6, -5.6, 'pano', rot))
    cs.append(C('fio_ponta', cx - 0.3, cx + 0.3, 5.6, 7.0, -4.6, -2.6, 'pano', rot))
    cs.append(C('fio_ponta', cx - 0.3, cx + 0.3, 17.0, 18.4, -4.6, -2.6, 'pano', rot))
    for i, (h0, h1, z0) in enumerate(((10.0, 14.0, 3.6), (10.8, 13.2, 5.4))):
        cs.append(C('machado2', cx - 0.45, cx + 0.45, h0, h1, z0, z0 + 1.8, 'tecido', rot))
    cs.append(C('sangue', cx - 0.55, cx + 0.55, 9.0, 12.4, -5.9, -3.2, 'corda', rot))
    cs.append(C('sangue2', cx - 0.55, cx + 0.55, 12.4, 13.4, -4.6, -3.6, 'corda', rot))
    # cabeca com capuz negro costurado, ponta caida, furos com olhos acesos
    cs.append(C('cabeca', 5.2, 10.8, 9.6, 15.6, 4.8, 10.8, 'tecido2'))
    cs.append(C('capuz', 4.6, 11.4, 10.6, 16.8, 4.4, 11.6, 'tecido2'))
    cs.append(C('capuz_ponta', 6.0, 10.0, 16.4, 19.0, 7.0, 11.4, 'tecido2', ('x', -45.0, (8.0, 16.6, 9.2))))
    cs.append(C('capuz_ponta2', 7.0, 9.0, 16.4, 20.4, 9.0, 10.8, 'tecido2', ('x', -45.0, (8.0, 16.6, 9.2))))
    cs.append(C('capuz_manto', 3.6, 12.4, 8.6, 11.4, 4.2, 11.8, 'tecido2'))
    for x in (6.4, 9.6):
        cs.append(C('furo', x - 1.0, x + 1.0, 12.8, 14.2, 4.25, 4.45, 'boca', so=('north',)))
        cs.append(C('olho', x - 0.5, x + 0.5, 13.1, 13.9, 4.15, 4.3, 'pupila', so=('north',), **LUZ))
    for y in (10.8, 12.0, 15.0, 16.0):
        cs.append(C('costura', 7.4, 8.6, y, y + 0.3, 4.3, 4.45, 'corda', so=('north',)))
    cs.append(C('costura_v', 7.85, 8.15, 10.6, 16.6, 4.3, 4.45, 'corda', so=('north',)))
    cs.append(C('capa', 3.2, 12.8, -12.0, 9.8, 11.2, 12.0, 'tecido2', ('x', -22.5, (8.0, 9.8, 11.6))))
    return cs


# ------------------------------------------------------------ 18) Besta Zoglin
def zoglin(susto):
    cs = []
    # corpo com corcunda alta na frente e traseiro baixo, carne podre e rachaduras verdes acesas
    cs.append(C('corpo', 1.6, 14.4, -8.4, 1.0, 5.0, 16.0, 'pele'))
    cs.append(C('traseiro', 2.6, 13.4, -8.0, -1.0, 15.6, 25.0, 'pele'))
    cs.append(C('corcunda', 2.4, 13.6, 0.6, 4.6, 5.4, 14.0, 'pele'))
    cs.append(C('corcunda2', 3.4, 12.6, 4.4, 6.2, 6.6, 11.6, 'tecido2'))
    for x0, so in ((1.4, 'west'), (14.4, 'east')):
        for z in (8.0, 10.2, 12.4):
            cs.append(C('costela', x0 - 0.3, x0 + 0.3, -6.4, -1.4, z, z + 0.9, 'pano'))
        cs.append(C('carne', x0 - 0.25, x0 + 0.25, -6.0, -1.8, 8.8, 13.6, 'tecido2', so=(so,)))
        cs.append(C('racha', x0 - 0.35, x0 + 0.35, -3.0, 0.2, 16.6, 17.2, 'pupila', so=(so,), **LUZ))
        cs.append(C('racha', x0 - 0.35, x0 + 0.35, -7.0, -4.0, 19.6, 20.2, 'pupila', so=(so,), **LUZ))
    # crina de espinhos de osso (os da frente maiores)
    for i, z in enumerate((6.0, 8.4, 10.8, 13.2, 15.6, 18.4, 21.2)):
        h = (5.0, 7.0, 7.6, 6.6, 4.8, 3.6, 2.6)[i]
        y0 = 5.6 if i < 4 else 2.0 - (i - 4) * 0.6
        cs.append(C('espinho', 7.2, 8.8, y0, y0 + h, z, z + 1.6, 'pano', ('x', -22.5, (8.0, y0, z + 0.8))))
        if i < 4:
            for lado in (-1, 1):
                cs.append(C('espinho_lado', 8 + lado * 3.0 - 0.6, 8 + lado * 3.0 + 0.6, y0 - 1.4, y0 - 1.4 + h * 0.6, z + 0.2, z + 1.4, 'pano',
                            ('z', -22.5 * lado, (8 + lado * 3.0, y0 - 1.4, z + 0.8))))
    cs.append(C('cauda', 7.0, 9.0, -3.4, 0.4, 24.6, 28.0, 'pele', ('x', 22.5, (8.0, 0.0, 25.0))))
    # patas: as da frente mais grossas, cascos, algemas quebradas
    for x, z, g in ((4.0, 9.0, 2.3), (12.0, 9.0, 2.3), (4.4, 22.0, 1.8), (11.6, 22.0, 1.8)):
        cs.append(C('pata', x - g, x + g, -14.4, -6.0, z - g, z + g, 'pele'))
        cs.append(C('casco', x - g - 0.3, x + g + 0.3, -16.0, -14.0, z - g - 0.3, z + g + 0.3, 'tecido2'))
        cs.append(C('corrente', x - g - 0.3, x + g + 0.3, -12.4, -11.2, z - g - 0.3, z + g + 0.3, 'tecido'))
        cs.append(C('corrente_elo', x - 0.4, x + 0.4, -14.0, -12.4, z - g - 0.7, z - g - 0.2, 'tecido'))
    # cabeca grande com mascara de osso, presas enormes curvadas para cima
    mand = ('x', -22.5, (8.0, -8.0, 2.0)) if susto else None
    cs.append(C('cabeca', 2.2, 13.8, -9.6, 1.0, -5.6, 6.0, 'pele'))
    cs.append(C('mascara_osso', 2.8, 13.2, -4.0, 2.0, -6.2, 0.6, 'pano'))
    cs.append(C('mascara_racha', 7.6, 8.4, -4.0, 2.0, -6.35, -6.15, 'tecido2', so=('north',)))
    cs.append(C('focinho', 4.0, 12.0, -11.0, -4.0, -8.6, -5.4, 'pele'))
    cs.append(C('focinho_ponta', 4.6, 11.4, -10.6, -5.6, -9.4, -8.4, 'tecido2'))
    for x in (6.2, 9.8):
        cs.append(C('narina', x - 0.7, x + 0.7, -8.6, -7.0, -9.55, -9.35, 'boca', so=('north',)))
    for x in (4.4, 11.6):
        cs.append(C('olho_fundo', x - 1.0, x + 1.0, -2.8, -1.0, -6.35, -6.15, 'boca', so=('north',)))
        cs.append(C('olho', x - 0.5, x + 0.5, -2.4, -1.4, -6.45, -6.3, 'pupila', so=('north',), **LUZ))
    cs.append(C('mandibula', 3.4, 12.6, -12.6, -9.4, -7.4, 4.0, 'pele', mand))
    A.dentes(cs, 3.8, 12.2, -9.6, -8.8, -7.65, -7.35, passo=0.9, larg=0.55, rot=mand, nome='dente_b')
    for lado in (-1, 1):
        x0 = 8 + lado * 3.6
        cs.append(C('presa', x0 - 0.8, x0 + 0.8, -11.4, -6.0, -8.4, -6.8, 'pano', mand))
        cs.append(C('presa_ponta', x0 - 0.6 + lado * 0.7, x0 + 0.6 + lado * 0.7, -6.2, -3.0, -8.8, -7.4, 'pano', mand))
        cs.append(C('presa_ponta2', x0 - 0.4 + lado * 1.4, x0 + 0.4 + lado * 1.4, -3.2, -1.2, -9.4, -8.2, 'pano', mand))
        cs.append(C('orelha', (0.6 if lado < 0 else 13.4), (2.6 if lado < 0 else 15.4), -0.4, 2.8, 1.0, 3.6, 'pele', ('z', 22.5 * lado, (8 + lado * 5.8, -0.4, 2.3))))
        cs.append(C('orelha_rasgo', (0.6 if lado < 0 else 13.4), (1.4 if lado < 0 else 14.6), 1.6, 2.8, 0.9, 1.6, 'tecido2', ('z', 22.5 * lado, (8 + lado * 5.8, -0.4, 2.3))))
    return cs


# ------------------------------------------------------------ 19) O Coveiro
def coveiro(susto):
    cs = []
    for x in (6.0, 10.0):
        cs.append(C('perna', x - 1.2, x + 1.2, -14.0, -2.0, 6.8, 9.2, 'tecido2'))
        cs.append(C('bota', x - 1.5, x + 1.5, -16.0, -13.0, 5.6, 9.5, 'sapato'))
        cs.append(C('bota_cano', x - 1.6, x + 1.6, -13.4, -12.4, 6.6, 9.6, 'corda'))
    # sobretudo longo com rabos abertos atras, remendos e cinto com caveira e chaves
    cs.append(C('casaco', 3.8, 12.2, -6.0, 7.6, 5.8, 10.6, 'tecido'))
    for lado in (-1, 1):
        x0, x1 = (3.6, 7.6) if lado < 0 else (8.4, 12.4)
        cs.append(C('casaco_frente', x0, x1, -13.0, -5.8, 5.6, 6.2, 'tecido'))
        cs.append(C('casaco_rabo', x0, x1, -14.4, -5.8, 9.8, 10.8, 'tecido', ('x', -22.5, (8.0, -5.8, 10.3))))
        cs.append(C('lapela', (x1 - 1.4) if lado < 0 else x0, (x1) if lado < 0 else (x0 + 1.4), 2.0, 7.6, 5.4, 5.8, 'tecido2'))
    for y in (-3.0, 0.0, 3.0):
        cs.append(C('botao', 6.6, 7.4, y, y + 0.8, 5.55, 5.8, 'ouro'))
    cs.append(C('remendo', 9.2, 11.6, -10.4, -8.0, 5.5, 5.7, 'corda', so=('north',)))
    cs.append(C('remendo', 4.4, 6.2, 3.0, 4.8, 5.65, 5.85, 'tecido2', so=('north',)))
    cs.append(C('cinto', 3.6, 12.4, -5.6, -4.4, 5.5, 10.9, 'tecido2'))
    cs.append(C('caveira_cinto', 7.0, 9.0, -6.4, -4.0, 5.1, 5.7, 'pano'))
    for x in (10.4, 11.0, 11.6):
        cs.append(C('chave', x - 0.2, x + 0.2, -8.4, -5.6, 5.3, 5.6, 'ouro'))
    # capinha nos ombros (capa curta em camadas) e cachecol comprido voando
    cs.append(C('capinha', 2.8, 13.2, 4.0, 8.2, 5.2, 11.2, 'tecido2'))
    cs.append(C('capinha_borda', 2.6, 13.4, 3.6, 4.4, 5.0, 11.4, 'tecido'))
    cs.append(C('cachecol', 4.0, 12.0, 6.6, 8.8, 4.8, 10.8, 'corda'))
    cs.append(C('cachecol_ponta', 4.2, 6.0, -1.0, 6.8, 4.4, 5.0, 'corda'))
    cs.append(C('cachecol_ponta2', 9.4, 11.0, 2.4, 6.8, 10.6, 11.4, 'corda', ('x', -45.0, (10.2, 6.8, 11.0))))
    for lado in (-1, 1):
        cx = 8 + lado * 5.4
        rot = rot_d(susto and lado == 1, cx, 7.6)
        cs.append(C('manga', cx - 1.4, cx + 1.4, -2.2, 7.6, 6.7, 9.5, 'tecido', rot))
        cs.append(C('punho', cx - 1.65, cx + 1.65, -3.0, -1.6, 6.45, 9.75, 'tecido2', rot))
        cs.append(C('mao', cx - 1.0, cx + 1.0, -5.0, -2.8, 7.1, 9.1, 'pele', rot))
        for d in (-0.55, 0.0, 0.55):
            cs.append(C('dedo', cx + d - 0.2, cx + d + 0.2, -7.0, -4.8, 7.2, 7.7, 'pele', rot))
    # pa grande com rebites e terra na direita
    cx = 8 + 5.4
    rot = rot_d(susto, cx, 7.6)
    cs.append(C('cabo', cx - 0.45, cx + 0.45, -8.0, 13.0, 5.0, 5.9, 'ouro', rot))
    cs.append(C('cabo_alca', cx - 1.4, cx + 1.4, 12.6, 13.8, 5.0, 5.9, 'ouro', rot))
    cs.append(C('pa_gola', cx - 1.0, cx + 1.0, -9.4, -7.6, 4.9, 6.0, 'sapato', rot))
    cs.append(C('pa', cx - 2.4, cx + 2.4, -15.4, -9.0, 5.0, 6.0, 'sapato', rot))
    cs.append(C('pa_ponta', cx - 1.6, cx + 1.6, -16.0, -15.2, 5.1, 5.9, 'sapato', rot))
    for d in (-1.6, 1.6):
        cs.append(C('rebite', cx + d - 0.3, cx + d + 0.3, -9.8, -9.2, 4.85, 5.05, 'ouro', rot))
    cs.append(C('pa_terra', cx - 2.2, cx + 2.2, -14.6, -12.0, 4.7, 5.05, 'corda', rot))
    # lampiao com chama de fantasma verde na esquerda
    cx = 8 - 5.4
    cs.append(C('lampiao_alca', cx - 0.3, cx + 0.3, -8.6, -6.4, 7.8, 8.4, 'tecido2'))
    cs.append(C('lampiao_topo', cx - 1.0, cx + 1.0, -9.4, -8.6, 7.1, 9.1, 'tecido2'))
    cs.append(C('lampiao', cx - 1.4, cx + 1.4, -13.0, -9.4, 6.7, 9.5, 'tecido2', so=('up', 'down')))
    for dx, dz in ((-1.2, -1.2), (1.0, -1.2), (-1.2, 1.0), (1.0, 1.0)):
        cs.append(C('lampiao_barra', cx + dx, cx + dx + 0.3, -13.0, -9.4, 8.1 + dz, 8.4 + dz, 'tecido2'))
    cs.append(C('lampiao_luz', cx - 0.9, cx + 0.9, -12.6, -9.8, 7.2, 9.0, 'pupila', **LUZ))
    # cabeca magra projetada, olhos fundos acesos, boca costurada, nariz comprido
    cs.append(C('pescoco', 7.0, 9.0, 7.6, 9.2, 6.6, 8.6, 'pele'))
    cs.append(C('cabeca', 5.4, 10.6, 8.8, 14.2, 4.8, 10.0, 'pele'))
    for x in (6.6, 9.4):
        cs.append(C('olho_fundo', x - 1.0, x + 1.0, 11.0, 12.6, 4.65, 4.85, 'boca', so=('north',)))
        cs.append(C('olho', x - 0.4, x + 0.4, 11.4, 12.2, 4.55, 4.7, 'pupila', so=('north',), **LUZ))
    cs.append(C('olheira', 5.4, 10.6, 10.4, 11.0, 4.7, 4.85, 'tecido2', so=('north',)))
    cs.append(C('nariz', 7.5, 8.5, 9.4, 11.4, 3.6, 4.9, 'pele'))
    mand = ('x', -22.5, (8.8, 9.0, 6.0)) if susto else None
    cs.append(C('boca', 6.2, 9.8, 9.0, 9.6, 4.65, 4.85, 'boca', so=('north',)))
    for x in (6.6, 7.4, 8.2, 9.0):
        cs.append(C('ponto', x - 0.1, x + 0.1, 8.7, 9.9, 4.55, 4.7, 'corda', so=('north',)))
    A.dentes(cs, 6.4, 9.6, 8.9, 9.4, 4.55, 4.75, passo=0.9, larg=0.4, rot=mand, nome='dente_b')
    cs.append(C('cabelo', 5.0, 11.0, 9.4, 13.4, 9.6, 10.6, 'tecido2'))
    # cartola alta e amassada com fita e vela acesa
    cs.append(C('aba', 2.8, 13.2, 13.8, 14.6, 2.6, 12.2, 'tecido2'))
    cs.append(C('cartola', 4.8, 11.2, 14.4, 19.4, 4.2, 10.6, 'tecido2'))
    cs.append(C('fita', 4.7, 11.3, 14.6, 15.8, 4.1, 10.7, 'corda'))
    cs.append(C('cartola_topo', 5.2, 10.8, 19.0, 22.0, 4.6, 10.2, 'tecido2', ('z', 22.5, (8.0, 19.0, 7.4))))
    cs.append(C('cartola_remendo', 9.4, 10.8, 17.0, 18.6, 4.05, 4.25, 'tecido', so=('north',)))
    cs.append(C('vela', 4.4, 5.4, 15.6, 18.4, 6.4, 7.4, 'pano'))
    cs.append(C('cera', 4.2, 5.6, 15.4, 16.0, 6.2, 7.6, 'pano'))
    cs.append(C('chama_vela', 4.6, 5.2, 18.4, 19.6, 6.6, 7.2, 'pupila', **LUZ))
    return cs


# ------------------------------------------------------------ 20) Gosma Primordial
def gosma(susto):
    cs = []
    abre = 2.4 if susto else 0.0
    # bolha arredondada em camadas (larga no meio), escorrendo pelo chao
    cs.append(C('base', 0.0, 16.0, -16.0, -14.6, 0.0, 16.0, 'tecido2'))
    cs.append(C('corpo', -1.4, 17.4, -14.8, -6.0, -1.4, 17.4, 'tecido'))
    cs.append(C('corpo_meio', -0.6, 16.6, -6.2, -1.0, -0.6, 16.6, 'tecido'))
    cs.append(C('corpo_cima', 1.0, 15.0, -1.2, 3.4, 1.0, 15.0, 'tecido'))
    cs.append(C('topo', 3.4, 12.6, 3.2, 5.6, 3.4, 12.6, 'tecido'))
    cs.append(C('topo2', 5.6, 10.4, 5.4, 6.6, 5.6, 10.4, 'tecido'))
    for x, z, h in ((-2.4, 3.0, 5.0), (17.4, 6.0, 6.4), (4.0, -2.4, 7.0), (11.4, -2.4, 4.4), (2.6, 17.4, 5.4), (12.4, 17.4, 6.6), (-2.4, 12.0, 3.6), (17.4, 13.0, 4.0)):
        cs.append(C('pingo', x, x + 1.8, -16.0, -16.0 + h, z, z + 1.8, 'tecido2'))
        cs.append(C('pingo_gota', x + 0.3, x + 1.5, -16.0, -15.2, z + 0.3 - (0.6 if z < 0 else 0), z + 1.5 + (0.6 if z > 16 else 0), 'pano'))
    for x, y, z in ((1.4, -9.0, -1.9), (14.0, -12.0, -1.9), (3.0, -3.0, -1.1), (16.9, -8.0, 9.0), (-1.9, -11.0, 11.0), (12.0, 1.6, 0.5), (9.0, 4.0, 3.0)):
        cs.append(C('bolha', x, x + 1.6, y, y + 1.6, z, z + 1.6, 'pano'))
    # janela da gosma: um "buraco" mais claro na frente com o nucleo aceso e uma caveira la dentro
    cs.append(C('janela', 5.6, 10.4, -6.6, -2.2, -0.9, -0.5, 'pano', so=('north',)))
    cs.append(C('nucleo', 7.0, 9.0, -5.6, -3.4, -1.0, -0.8, 'pupila', so=('north',), **LUZ))
    cs.append(C('caveira', 1.6, 4.6, -13.6, -10.8, -1.8, -1.0, 'pano'))
    cs.append(C('caveira_olho', 2.2, 2.9, -12.6, -12.0, -1.9, -1.75, 'boca', so=('north',)))
    cs.append(C('caveira_olho', 3.3, 4.0, -12.6, -12.0, -1.9, -1.75, 'boca', so=('north',)))
    # o que ela engoliu: espada fincada, escudo, ossos
    cs.append(C('espada', 14.6, 15.4, 0.0, 11.0, 6.0, 6.8, 'sapato', ('z', -22.5, (15.0, 0.0, 6.4))))
    cs.append(C('espada_guarda', 13.0, 17.0, -0.6, 0.2, 5.8, 7.0, 'ouro', ('z', -22.5, (15.0, 0.0, 6.4))))
    cs.append(C('escudo', -2.0, -1.2, -10.0, -5.0, 9.0, 14.0, 'ouro'))
    cs.append(C('escudo_borda', -2.2, -1.0, -10.4, -9.6, 8.8, 14.2, 'sapato'))
    cs.append(C('osso', 9.0, 14.0, -15.0, -14.2, -2.0, -1.4, 'pano', ('y', 22.5, (11.5, -14.6, -1.7))))
    # olhos esbugalhados em cima (em hastes) e boca enorme cheia de dentes
    for x in (4.4, 11.6):
        cs.append(C('haste_olho', x - 0.8, x + 0.8, 5.0, 8.0, 4.0, 5.6, 'tecido'))
        cs.append(C('olho_globo', x - 1.9, x + 1.9, 7.6, 11.4, 2.4, 6.2, 'pano'))
        cs.append(C('olho', x - 1.0, x + 1.0, 8.6, 10.4, 2.25, 2.45, 'pupila', so=('north',), **LUZ))
        cs.append(C('pupila_olho', x - 0.4, x + 0.4, 9.2, 9.8, 2.15, 2.3, 'boca', so=('north',)))
    cs.append(C('boca', 1.6, 14.4, -12.0 - abre, -7.0, -1.65, -1.35, 'boca', so=('north',)))
    cs.append(C('lingua', 5.0, 11.0, -12.0 - abre, -10.6 - abre, -1.8, -1.5, 'ouro', so=('north',)))
    A.dentes(cs, 2.0, 14.0, -8.2, -7.0, -1.8, -1.45, passo=1.2, larg=0.75)
    A.dentes(cs, 2.4, 13.6, -12.0 - abre, -10.8 - abre, -1.8, -1.45, passo=1.2, larg=0.75, nome='dente_b')
    # coroa afundando torta na gosma
    cs.append(C('coroa', 3.6, 12.4, 5.4, 6.8, 3.6, 12.4, 'ouro', ('z', 22.5, (8.0, 6.0, 8.0))))
    for x, h in ((4.2, 2.2), (8.0, 3.4), (11.8, 2.2)):
        cs.append(C('coroa_ponta', x - 0.6, x + 0.6, 6.6, 6.6 + h, 3.8, 5.0, 'ouro', ('z', 22.5, (8.0, 6.0, 8.0))))
    cs.append(C('joia', 7.3, 8.7, 5.6, 6.6, 3.4, 3.65, 'pupila', ('z', 22.5, (8.0, 6.0, 8.0)), so=('north',), **LUZ))
    return cs


H.CORPOS.update({'c_afogado': afogado, 'c_xama': xama, 'c_carrasco': carrasco, 'c_zoglin': zoglin, 'c_coveiro': coveiro, 'c_gosma': gosma})
H.MATS['c_afogado'] = mats(pele=mat((0.24, 0.40, 0.38)), tecido=mat((0.52, 0.50, 0.26), 0.8, 50, 0.6), tecido2=mat((0.10, 0.16, 0.26)),
                           corda=mat((0.14, 0.36, 0.18)), pano=mat((0.82, 0.80, 0.72)), ouro=mat((0.60, 0.28, 0.70), 0.3, 30, 0.1))
H.MATS['c_xama'] = mats(pele=mat((0.40, 0.27, 0.19)), tecido=mat((0.20, 0.50, 0.18)), tecido2=mat((0.36, 0.26, 0.17)),
                        corda=mat((0.50, 0.33, 0.18)), pano=mat((0.90, 0.86, 0.76)), ouro=mat((0.92, 0.26, 0.14), 0.3, 20, 0.1))
H.MATS['c_carrasco'] = mats(pele=mat((0.66, 0.48, 0.40)), tecido=mat((0.28, 0.28, 0.31), 0.85, 60, 0.7), tecido2=mat((0.05, 0.05, 0.06)),
                            corda=mat((0.36, 0.20, 0.11)), pano=mat((0.86, 0.86, 0.90), 0.9, 70, 0.8), ouro=mat((0.66, 0.54, 0.26), 0.8, 50, 0.6),
                            sapato=mat((0.10, 0.08, 0.07), 0.5, 40, 0.3))
H.MATS['c_zoglin'] = mats(pele=mat((0.58, 0.44, 0.46)), tecido=mat((0.30, 0.30, 0.32), 0.85, 60, 0.6), tecido2=mat((0.30, 0.14, 0.14)),
                          pano=mat((0.90, 0.87, 0.78)))
H.MATS['c_coveiro'] = mats(pele=mat((0.64, 0.68, 0.62)), tecido=mat((0.14, 0.13, 0.16)), tecido2=mat((0.06, 0.06, 0.07)),
                           corda=mat((0.46, 0.10, 0.10)), pano=mat((0.86, 0.84, 0.76)), ouro=mat((0.42, 0.28, 0.15), 0.1, 10, 0.0),
                           sapato=mat((0.42, 0.42, 0.46), 0.85, 60, 0.6))
H.MATS['c_gosma'] = mats(tecido=mat((0.34, 0.74, 0.22), 0.3, 60, 0.5), tecido2=mat((0.22, 0.54, 0.14), 0.3, 60, 0.5),
                         pano=mat((0.70, 0.96, 0.52), 0.4, 70, 0.6), ouro=mat((0.94, 0.80, 0.22), 0.9, 60, 0.8), sapato=mat((0.52, 0.54, 0.58), 0.85, 60, 0.6))
A.PALETAS['c_afogado'] = dict(pupila=(0.30, 1.0, 0.95))
A.PALETAS['c_xama'] = dict(pupila=(0.40, 1.0, 0.30))
A.PALETAS['c_carrasco'] = dict(pupila=(1.0, 0.12, 0.08))
A.PALETAS['c_zoglin'] = dict(pupila=(0.55, 1.0, 0.20))
A.PALETAS['c_coveiro'] = dict(pupila=(0.40, 1.0, 0.60))
A.PALETAS['c_gosma'] = dict(pupila=(1.0, 0.90, 0.20))
K.LISO.update({'c_afogado': {'pele', 'tecido', 'ouro', 'pano'}, 'c_xama': {'pele', 'pano', 'corda', 'ouro'}, 'c_carrasco': {'pele', 'tecido', 'pano', 'ouro', 'sapato'},
               'c_zoglin': {'pele', 'tecido', 'pano', 'tecido2'}, 'c_coveiro': {'pele', 'pano', 'sapato', 'ouro'}, 'c_gosma': {'tecido', 'tecido2', 'pano', 'ouro', 'sapato'}})



# ------------------------------------------------------------ 20) Rainha da Gosma (v3, 18/11: "mais feminino, sem ser monstrengo")
def gosma_rainha(susto):
    cs = []
    # vestido longo de gosma que se espalha no chao em ondas (6 camadas finas, barra ondulada e babados rosa)
    for i in range(6):
        a = 0.2 + i * 1.05
        y0 = -16.0 + i * 2.25
        cs.append(C('vestido' if i == 0 else f'vestido_{i}', a, 16 - a, y0, y0 + 2.45, a, 16 - a, 'tecido' if i % 2 else 'tecido2'))
    for k, x in enumerate((0.6, 3.0, 5.4, 7.8, 10.2, 12.6)):
        cs.append(C('vestido_onda', x, x + 2.0, -16.0, -14.6 + (k % 2) * 0.8, -0.4, 0.4, 'pano'))
    for y, a in ((-11.5, 2.3), (-7.0, 4.4), (-4.7, 5.45)):
        cs.append(C('vestido_babado', a - 0.2, 16 - a + 0.2, y - 0.35, y + 0.35, a - 0.25, 16 - a + 0.25, 'corda'))
    for i, (x, z) in enumerate(((0.4, 3.0), (14.6, 5.0), (2.6, 13.6), (13.0, 12.0))):
        cs.append(C('vestido_pingo', x, x + 1.2, -16.0, -13.4 - (i % 2), z, z + 1.2, 'pano'))
    for x, y, z in ((3.0, -12.4, 1.8), (12.0, -9.6, 3.0), (6.4, -5.4, 4.4)):
        cs.append(C('vestido_bolha', x, x + 1.0, y, y + 1.0, z - 0.3, z + 0.7, 'pano'))
    # cintura fina com laco rosa e o coracao aceso no peito
    cs.append(C('cintura', 5.6, 10.4, -3.0, 0.6, 6.2, 9.8, 'tecido'))
    cs.append(C('faixa', 5.4, 10.6, -1.4, -0.4, 6.0, 10.0, 'corda'))
    cs.append(C('laco', 6.6, 9.4, -2.0, 0.2, 5.6, 6.1, 'corda'))
    cs.append(C('laco_ponta', 7.4, 8.6, -4.4, -1.8, 5.7, 6.0, 'corda'))
    cs.append(C('tronco', 5.2, 10.8, 0.4, 6.4, 6.2, 9.8, 'tecido'))
    cs.append(C('corpete', 5.0, 11.0, 0.4, 4.0, 6.0, 10.0, 'tecido2'))
    cs.append(C('corpete_fio', 7.7, 8.3, 0.4, 4.0, 5.9, 6.05, 'ouro', so=('north',)))
    cs.append(C('coracao', 7.2, 8.8, 4.0, 5.4, 5.95, 6.15, 'pupila', so=('north',), **LUZ))
    cs.append(C('gola', 4.8, 11.2, 5.8, 6.8, 5.8, 10.2, 'pano'))
    # bracos finos com mangas bufantes; a mao direita segura uma esfera de gosma acesa
    for lado in (-1, 1):
        cx = 8 + lado * 3.9
        rot = ('x', 45.0, (cx, 6.2, 8.0)) if susto and lado == 1 else None
        cs.append(C('manga', cx - 1.3, cx + 1.3, 3.4, 6.8, 6.7, 9.3, 'pano', rot))
        cs.append(C('manga_fita', cx - 1.35, cx + 1.35, 3.2, 3.8, 6.65, 9.35, 'corda', rot))
        cs.append(C('braco', cx - 0.7, cx + 0.7, -2.0, 3.6, 7.3, 8.7, 'tecido', rot))
        cs.append(C('bracelete', cx - 0.85, cx + 0.85, -1.8, -1.2, 7.15, 8.85, 'ouro', rot))
        cs.append(C('mao', cx - 0.65, cx + 0.65, -3.4, -1.8, 7.35, 8.65, 'tecido', rot))
    cx = 8 + 3.9
    rot = ('x', 45.0, (cx, 6.2, 8.0)) if susto else None
    cs.append(C('orbe', cx - 1.2, cx + 1.2, -6.0, -3.6, 5.4, 7.8, 'pupila', rot, **LUZ))
    # cabeca de rosto claro e gentil: olhos grandes com cilios e brilho, bochechas rosadas, sorriso
    cs.append(C('pescoco', 7.3, 8.7, 6.4, 7.8, 7.3, 8.7, 'pano'))
    cs.append(C('cabeca', 5.3, 10.7, 7.6, 13.0, 5.6, 10.6, 'pano'))
    for x in (6.6, 9.4):
        cs.append(C('olho', x - 0.85, x + 0.85, 9.6, 11.4, 5.45, 5.6, 'pupila', so=('north',), **LUZ))
        cs.append(C('pupila_olho', x - 0.4, x + 0.4, 9.8, 10.8, 5.38, 5.46, 'sapato', so=('north',)))
        cs.append(C('brilho_olho', x - 0.6, x - 0.2, 10.8, 11.2, 5.32, 5.38, 'pano', so=('north',)))
        cs.append(C('cilio', x - 1.0, x + 1.0, 11.3, 11.7, 5.35, 5.55, 'sapato', so=('north', 'up')))
        bx = x - 0.5 if x < 8 else x + 0.5
        cs.append(C('bochecha', bx - 0.6, bx + 0.6, 8.8, 9.3, 5.45, 5.58, 'corda', so=('north',)))
    cs.append(C('boca_s', 7.4, 8.6, 8.4, 8.8, 5.45, 5.58, 'corda', so=('north',)))
    # cabelo longo e escuro de gosma: franja, mechas dos lados (fora do rosto) e a parte de tras ate o vestido
    cs.append(C('cabelo', 4.9, 11.1, 12.4, 14.2, 5.2, 11.0, 'tecido2'))
    cs.append(C('franja', 5.2, 10.8, 11.8, 12.8, 5.1, 5.7, 'tecido2'))
    cs.append(C('franja_ponta', 5.4, 7.0, 11.2, 12.0, 5.15, 5.65, 'tecido2'))
    for lado in (-1, 1):
        x0, x1 = (4.4, 5.4) if lado < 0 else (10.6, 11.6)
        cs.append(C('mecha', x0, x1, 3.4, 13.4, 5.8, 9.6, 'tecido2'))
        cs.append(C('mecha_ponta', x0 + 0.15, x1 - 0.15, 2.0, 3.6, 6.4, 9.0, 'pano'))
    cs.append(C('laco_cabelo', 9.6, 12.4, 12.8, 14.6, 8.4, 9.6, 'corda'))
    cs.append(C('capa_cabelo', 4.9, 11.1, -6.0, 13.0, 10.2, 11.6, 'tecido2', ('x', -22.5, (8.0, 13.0, 10.9))))
    cs.append(C('capa_cabelo_ponta', 5.8, 10.2, -8.0, -5.8, 10.4, 11.4, 'pano', ('x', -22.5, (8.0, 13.0, 10.9))))
    # tiara-coroa delicada de ouro com gema acesa
    cs.append(C('coroa', 5.6, 10.4, 14.0, 14.8, 5.6, 9.6, 'ouro'))
    for x, h in ((6.2, 1.2), (8.0, 2.2), (9.8, 1.2)):
        cs.append(C('coroa_ponta', x - 0.35, x + 0.35, 14.6, 14.6 + h, 5.7, 6.3, 'ouro'))
    cs.append(C('joia', 7.5, 8.5, 14.1, 14.7, 5.45, 5.65, 'pupila', so=('north',), **LUZ))
    # gotinhas de gosma flutuando em volta dela
    for x, y, z in ((-2.0, 2.0, 6.0), (17.0, 6.0, 9.0), (1.0, 11.0, 13.0), (15.0, -2.0, 3.0)):
        cs.append(C('gota_voa', x, x + 1.3, y, y + 1.6, z, z + 1.3, 'pano'))
        cs.append(C('gota_voa_luz', x + 0.35, x + 0.95, y + 0.5, y + 1.1, z - 0.05, z + 1.35, 'pupila', **LUZ))
    return cs


H.CORPOS['c_gosma'] = gosma_rainha
H.MATS['c_gosma'] = mats(tecido=mat((0.42, 0.80, 0.34), 0.3, 60, 0.5), tecido2=mat((0.26, 0.62, 0.24), 0.3, 60, 0.5),
                         pano=mat((0.80, 0.98, 0.74), 0.3, 60, 0.4), ouro=mat((0.96, 0.80, 0.26), 0.95, 70, 0.95),
                         corda=mat((0.98, 0.58, 0.70), 0.1, 10, 0.0), sapato=mat((0.12, 0.24, 0.14), 0.2, 20, 0.0))
A.PALETAS['c_gosma'] = dict(pupila=(0.95, 1.0, 0.45))


if __name__ == '__main__':
    so = sys.argv[2:] or ['c_afogado', 'c_xama', 'c_carrasco', 'c_zoglin', 'c_coveiro', 'c_gosma']
    A.gerar(A.SAIDA, so)
