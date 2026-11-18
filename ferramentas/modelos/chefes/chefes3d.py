"""Chefes das masmorras 1-10 esculpidos em 3D (desenho original do Corona Server), para aprovacao.
uso: PYTHONPATH=plugins/coronapack:ferramentas/modelos python3 chefes3d.py <saida>"""
import os, sys
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
import reiesq3d as R
H, A, C, LUZ = R.H, R.A, R.C, R.LUZ


def mat(cor, met=0.15, br=12, esp=0.0):
    return (cor, met, br, esp)


def mats(**kw):
    base = {'tecido': mat((0.3, 0.3, 0.3)), 'tecido2': mat((0.2, 0.2, 0.2)), 'ouro': mat((0.98, 0.76, 0.2), 0.95, 70, 0.95),
            'pano': mat((0.85, 0.82, 0.74), 0.2, 14), 'pele': mat((0.6, 0.6, 0.6), 0.2, 14), 'corda': mat((0.3, 0.2, 0.1), 0.1, 8),
            'sapato': mat((0.05, 0.05, 0.06), 0.7, 50)}
    base.update(kw)
    return base


def olhos(cs, y, z, cx=(6.5, 9.5), w=0.5, h=0.45):
    for x in cx:
        cs.append(C('olho', x - w, x + w, y - h, y + h, z - 0.12, z, 'pupila', so=('north',), **LUZ))


# ------------------------------------------------------------ 2) Rainha Aranha
def rainha(susto):
    cs = []
    cs.append(C('abdomen', 1.0, 15.0, -11.0, -1.0, 11.0, 25.0, 'tecido'))
    cs.append(C('abdomen_topo', 2.0, 14.0, -1.2, 0.8, 12.0, 24.0, 'tecido'))
    cs.append(C('ampulheta', 6.0, 10.0, -1.0, 1.0, 15.0, 17.0, 'corda', so=('up',)))
    cs.append(C('ampulheta2', 7.0, 9.0, -1.0, 1.0, 17.0, 20.0, 'corda', so=('up',)))
    cs.append(C('ampulheta3', 6.0, 10.0, -1.0, 1.0, 20.0, 22.0, 'corda', so=('up',)))
    for k in range(4):
        cs.append(C('listra', 1.0, 15.0, -10.0 + k * 2.4, -9.2 + k * 2.4, 24.9, 25.2, 'corda', so=('south',)))
    cs.append(C('fiandeira', 6.5, 9.5, -8.0, -5.0, 25.0, 26.4, 'tecido2'))
    cs.append(C('cintura', 5.6, 10.4, -9.0, -4.0, 9.0, 11.5, 'tecido2'))
    cs.append(C('cefalo', 3.0, 13.0, -10.6, -3.4, 1.0, 9.6, 'tecido'))
    cs.append(C('cefalo_topo', 4.0, 12.0, -3.6, -2.2, 2.0, 9.0, 'tecido2'))
    # 8 olhos acesos e a coroa
    for x, y in ((5.4, -5.4), (7.0, -5.0), (9.0, -5.0), (10.6, -5.4), (6.0, -7.0), (10.0, -7.0), (7.4, -6.6), (8.6, -6.6)):
        cs.append(C('olho', x - 0.45, x + 0.45, y - 0.45, y + 0.45, 0.85, 1.0, 'pupila', so=('north',), **LUZ))
    cs.append(C('coroa', 4.4, 11.6, -2.4, -1.0, 2.0, 8.0, 'ouro'))
    for x in (4.9, 6.6, 8.0, 9.4, 11.1):
        cs.append(C('coroa_ponta', x - 0.45, x + 0.45, -1.1, 0.9 if x == 8.0 else 0.2, 2.1, 3.0, 'ouro'))
    cs.append(C('joia', 7.4, 8.6, -2.2, -1.2, 1.85, 2.05, 'corda', so=('north',)))
    rot = ('x', -22.5, (8.0, -10.0, 1.0)) if susto else None
    for x in (5.6, 10.4):
        cs.append(C('quelicera', x - 0.9, x + 0.9, -13.4, -9.8, 0.4, 2.2, 'tecido2', rot))
        cs.append(C('presa', x - 0.4, x + 0.4, -15.6, -13.2, 0.6, 1.6, 'pano', rot))
    # 8 patas: coxa sobe para fora, canela desce ate o chao
    for lado in (-1, 1):
        for i, z in enumerate((2.2, 4.8, 7.4, 9.6)):
            x0 = 8 + lado * 5.0
            lev = susto and i == 0
            rz = ('x', -45.0, (x0, -6.0, z)) if lev else None
            xa, xb = sorted((x0, x0 + lado * 7.5))
            cs.append(C('coxa', xa, xb, -6.4 + i * 0.2, -4.8 + i * 0.2, z - 0.7, z + 0.7, 'tecido', rz))
            xe = x0 + lado * 7.5
            cs.append(C('joelho', xe - 0.9, xe + 0.9, -6.8, -3.8, z - 0.9, z + 0.9, 'tecido2', rz))
            rc = rz or ('z', 22.5 * lado, (xe, -5.4, z))
            cs.append(C('canela', xe - 0.6, xe + 0.6, -15.4 + (0 if not lev else 6), -5.0, z - 0.6, z + 0.6, 'pele', rc))
            cs.append(C('pelo', xe - 0.15, xe + 0.15, -4.0, -2.6, z - 0.15, z + 0.15, 'pano', rz))
    return cs


# ------------------------------------------------------------ 3) Mineiro Gigante
def mineiro(susto):
    cs = []
    for cx in (5.2, 10.8):
        cs.append(C('perna', cx - 2.0, cx + 2.0, -13.6, -2.0, 6.0, 10.0, 'tecido'))
        cs.append(C('bota', cx - 2.3, cx + 2.3, -16.0, -13.4, 4.8, 10.4, 'sapato'))
        cs.append(C('joelheira', cx - 2.2, cx + 2.2, -9.0, -7.0, 5.7, 6.2, 'corda'))
    cs.append(C('cinto', 2.6, 13.4, -2.6, -1.0, 5.4, 10.8, 'corda'))
    cs.append(C('fivela', 7.0, 9.0, -2.8, -0.8, 5.1, 5.5, 'ouro'))
    cs.append(C('lanterna', 11.6, 13.4, -5.6, -2.4, 4.6, 6.4, 'sapato'))
    cs.append(C('lanterna_luz', 11.9, 13.1, -5.0, -3.0, 4.5, 6.5, 'pupila', **LUZ))
    # tronco curvado e largo (corcunda), macacao com alcas
    cs.append(C('barriga', 2.4, 13.6, -1.2, 5.0, 5.0, 11.0, 'tecido'))
    cs.append(C('peito', 1.6, 14.4, 4.8, 10.0, 5.6, 12.0, 'tecido2'))
    cs.append(C('corcunda', 3.0, 13.0, 8.0, 11.4, 9.0, 13.0, 'tecido2'))
    for x in (4.6, 11.4):
        cs.append(C('alca', x - 0.8, x + 0.8, 4.8, 10.0, 5.3, 5.65, 'tecido', so=('north',)))
    # cristais de minerio presos nas costas (acesos)
    for x, y, h in ((5.0, 9.6, 3.0), (8.0, 10.4, 4.2), (11.0, 9.4, 2.6)):
        cs.append(C('cristal', x - 0.8, x + 0.8, y, y + h, 12.4, 14.0, 'pupila', ('z', 22.5 if x > 8 else -22.5 if x < 8 else 0.0, (x, y, 13.2)) if x != 8 else None, **LUZ))
    # bracos enormes ate o joelho
    for lado in (-1, 1):
        cx = 8 + lado * 8.6
        rot = ('x', 45.0, (cx, 9.0, 8.0)) if susto else None
        cs.append(C('ombro', cx - 2.6, cx + 2.6, 6.4, 11.0, 5.4, 10.6, 'tecido2', rot))
        cs.append(C('braco', cx - 2.0, cx + 2.0, -1.0, 6.6, 6.0, 10.0, 'pele', rot))
        cs.append(C('luva', cx - 2.4, cx + 2.4, -6.0, -0.8, 5.6, 10.4, 'corda', rot))
        if lado == 1:
            # picareta gigante
            cs.append(C('cabo', cx - 0.6, cx + 0.6, -6.4, 14.0, 3.4, 4.6, 'corda', rot))
            cs.append(C('picareta', cx - 6.0, cx + 6.0, 12.6, 14.6, 3.2, 4.8, 'sapato', rot))
            cs.append(C('ponta', cx - 7.6, cx - 5.8, 10.6, 13.2, 3.4, 4.6, 'sapato', rot))
            cs.append(C('ponta', cx + 5.8, cx + 7.6, 10.6, 13.2, 3.4, 4.6, 'sapato', rot))
    # cabeca pequena afundada, capacete com lampada, barba
    cs.append(C('cabeca', 5.0, 11.0, 9.4, 15.0, 4.0, 10.0, 'pele'))
    olhos(cs, 12.6, 4.0)
    cs.append(C('barba', 5.0, 11.0, 8.6, 11.6, 3.4, 4.2, 'tecido2'))
    A.dentes(cs, 6.0, 10.0, 11.4, 12.0, 3.85, 4.1, passo=0.8, larg=0.5)
    cs.append(C('capacete', 4.4, 11.6, 14.2, 16.8, 3.6, 10.6, 'ouro'))
    cs.append(C('aba', 3.8, 12.2, 14.0, 14.6, 3.0, 11.2, 'ouro'))
    cs.append(C('lampada', 6.8, 9.2, 14.8, 16.4, 3.0, 3.7, 'pupila', **LUZ))
    return cs


# ------------------------------------------------------------ 4) Bruxa Ancia
def bruxa(susto):
    cs = []
    cs.append(C('vestido', 3.4, 12.6, -16.0, -4.0, 5.0, 11.0, 'tecido'))
    cs.append(C('vestido_barra', 3.0, 13.0, -16.0, -14.2, 4.6, 11.4, 'tecido2'))
    for x in (4.4, 7.0, 10.0):
        cs.append(C('remendo', x, x + 1.8, -12.0 + x * 0.3, -10.0 + x * 0.3, 4.85, 5.05, 'corda', so=('north',)))
    cs.append(C('avental', 5.0, 11.0, -13.0, -3.0, 4.7, 5.05, 'pano', so=('north', 'east', 'west', 'down')))
    cs.append(C('cinto', 3.2, 12.8, -4.4, -3.0, 4.8, 11.2, 'corda'))
    for x in (4.2, 11.0):
        cs.append(C('frasco', x - 0.7, x + 0.7, -7.0, -4.4, 4.2, 5.0, 'pupila', **LUZ))
    cs.append(C('tronco', 4.0, 12.0, -3.2, 5.2, 6.0, 10.6, 'tecido'))
    cs.append(C('corcunda', 4.6, 11.4, 2.0, 6.6, 9.4, 12.0, 'tecido'))
    cs.append(C('xale', 3.2, 12.8, 3.4, 6.6, 5.6, 11.4, 'tecido2'))
    # vassoura nas costas
    cs.append(C('vassoura', 7.4, 8.6, -12.0, 12.0, 12.2, 13.2, 'corda', ('z', 22.5, (8.0, 0.0, 12.7))))
    cs.append(C('palha', 6.2, 9.8, -16.0, -10.4, 11.6, 13.8, 'ouro', ('z', 22.5, (8.0, 0.0, 12.7))))
    for lado in (-1, 1):
        cx = 8 + lado * 5.2
        rot = ('x', 45.0, (cx, 5.6, 8.0)) if susto else None
        cs.append(C('manga', cx - 1.4, cx + 1.4, -1.6, 5.6, 6.6, 9.4, 'tecido', rot))
        cs.append(C('mao', cx - 1.0, cx + 1.0, -4.4, -1.4, 7.0, 9.0, 'pele', rot))
        cs.append(C('unha', cx - 1.0, cx + 1.0, -5.6, -4.4, 7.0, 7.6, 'sapato', rot))
        if lado == 1:
            cs.append(C('pocao', cx - 1.0, cx + 1.0, -5.2, -2.2, 5.0, 7.0, 'pupila', rot, **LUZ))
            cs.append(C('rolha', cx - 0.4, cx + 0.4, -2.4, -1.4, 5.6, 6.4, 'corda', rot))
    cs.append(C('cabeca', 5.0, 11.0, 5.6, 11.6, 4.4, 10.4, 'pele'))
    olhos(cs, 9.4, 4.4)
    cs.append(C('nariz', 7.2, 8.8, 6.8, 9.2, 2.0, 4.6, 'pele', ('x', 22.5, (8.0, 8.0, 4.4))))
    cs.append(C('verruga', 7.0, 7.6, 7.2, 7.8, 2.4, 3.0, 'tecido2'))
    A.dentes(cs, 6.0, 10.0, 6.4, 7.0, 4.25, 4.45, passo=1.2, larg=0.5)
    cs.append(C('cabelo', 4.4, 11.6, 4.0, 10.4, 9.6, 11.2, 'pano'))
    # chapeu alto e torto
    cs.append(C('aba', 2.0, 14.0, 11.2, 12.0, 2.2, 12.6, 'tecido2'))
    cs.append(C('chapeu1', 4.4, 11.6, 11.8, 15.6, 4.6, 10.2, 'tecido2'))
    cs.append(C('fita', 4.3, 11.7, 12.0, 13.0, 4.5, 10.3, 'ouro'))
    cs.append(C('chapeu2', 5.4, 10.6, 15.4, 19.0, 5.6, 9.6, 'tecido2', ('z', -22.5, (8.0, 15.4, 7.6))))
    cs.append(C('chapeu3', 6.8, 9.6, 18.4, 21.6, 6.4, 8.8, 'tecido2', ('z', -45.0, (8.0, 18.4, 7.6))))
    return cs


# ------------------------------------------------------------ 5) Rei do Gelo
def rei_gelo(susto):
    cs = []
    R.esqueleto(cs, susto)
    # armadura de gelo e manto de neve, coroa de estalactites, barba de gelo, espada de gelo
    cs.append(C('peitoral', 3.8, 12.2, -0.6, 7.6, 4.8, 10.6, 'tecido'))
    cs.append(C('peito_runa', 7.0, 9.0, 2.0, 5.0, 4.65, 4.85, 'pupila', so=('north',), **LUZ))
    cs.append(C('saiote', 4.2, 11.8, -6.0, -1.0, 5.6, 10.4, 'tecido'))
    for lado in (-1, 1):
        cx = 8 + lado * 5.6
        cs.append(C('ombreira', cx - 2.0, cx + 2.0, 6.4, 9.2, 5.4, 10.6, 'tecido'))
        for dz, h in ((6.4, 2.4), (8.2, 3.6), (9.8, 2.0)):
            cs.append(C('cristal', cx - 0.5 + lado * 0.6, cx + 0.5 + lado * 0.6, 9.0, 9.0 + h, dz - 0.5, dz + 0.5, 'pano'))
        cs.append(C('caneleira', cx - 0.4 - lado * 1.4, cx + 0.4 - lado * 1.4, -14.6, -9.0, 6.6, 7.0, 'tecido'))
    cs.append(C('manto', 3.0, 13.0, -15.0, 7.8, 10.8, 11.8, 'tecido2', ('x', -22.5, (8.0, 7.8, 11.3))))
    cs.append(C('gola', 2.6, 13.4, 6.8, 9.6, 9.4, 12.0, 'sapato'))
    cs.append(C('barba', 5.6, 10.4, 6.0, 8.4, 4.4, 5.2, 'pano'))
    for x in (6.2, 7.4, 8.6, 9.8):
        cs.append(C('pingente', x - 0.3, x + 0.3, 4.6, 6.2, 4.5, 5.1, 'pano'))
    cs.append(C('coroa', 4.5, 11.5, 15.2, 16.2, 4.7, 11.5, 'pano'))
    for i, x in enumerate((4.9, 6.4, 8.0, 9.6, 11.1)):
        h = (2.4, 3.4, 5.0, 3.4, 2.4)[i]
        cs.append(C('estalactite', x - 0.55, x + 0.55, 16.1, 16.1 + h, 4.8, 5.9, 'pano'))
    cs.append(C('joia', 7.4, 8.6, 15.4, 16.0, 4.5, 4.75, 'pupila', so=('north',), **LUZ))
    cx = 8 + 5.2
    rot = ('x', 45.0, (cx, 6.8, 8.0)) if susto else None
    cs.append(C('cabo', cx - 0.4, cx + 0.4, -8.6, -4.6, 7.6, 8.4, 'sapato', rot))
    cs.append(C('guarda', cx - 2.2, cx + 2.2, -4.8, -3.8, 7.4, 8.6, 'tecido', rot))
    cs.append(C('lamina', cx - 1.2, cx + 1.2, -3.8, 14.0, 7.7, 8.3, 'pano', rot))
    cs.append(C('lamina_luz', cx - 0.3, cx + 0.3, -3.0, 12.8, 7.6, 8.4, 'pupila', rot, so=('north', 'south'), **LUZ))
    return cs


# ------------------------------------------------------------ 6) Arquimago
def arquimago(susto):
    cs = []
    cs.append(C('tunica', 3.6, 12.4, -16.0, -1.0, 5.0, 11.0, 'tecido'))
    cs.append(C('barra', 3.4, 12.6, -16.0, -14.6, 4.8, 11.2, 'ouro'))
    cs.append(C('faixa', 7.2, 8.8, -14.6, 6.0, 4.85, 5.05, 'tecido2', so=('north',)))
    for y in (-12.0, -8.0, -4.0, 0.0, 4.0):
        cs.append(C('runa', 7.6, 8.4, y, y + 1.2, 4.75, 4.9, 'pupila', so=('north',), **LUZ))
    cs.append(C('cinto', 3.6, 12.4, -2.0, -0.6, 4.8, 11.2, 'ouro'))
    cs.append(C('tronco', 4.0, 12.0, -1.0, 7.0, 5.4, 10.6, 'tecido'))
    cs.append(C('gola_alta', 3.0, 13.0, 6.4, 11.6, 9.8, 11.4, 'tecido2'))
    cs.append(C('capa', 3.2, 12.8, -15.0, 7.0, 10.8, 11.6, 'tecido2', ('x', -22.5, (8.0, 7.0, 11.2))))
    for lado in (-1, 1):
        cx = 8 + lado * 5.4
        rot = ('x', 45.0 if lado == 1 else -45.0, (cx, 6.4, 8.0)) if susto else None
        cs.append(C('manga', cx - 1.6, cx + 1.6, -1.0, 6.8, 6.4, 9.6, 'tecido', rot))
        cs.append(C('punho', cx - 1.8, cx + 1.8, -2.2, -0.8, 6.2, 9.8, 'ouro', rot))
        cs.append(C('mao', cx - 1.0, cx + 1.0, -4.0, -2.0, 7.0, 9.0, 'pele', rot))
    # cajado com cristal e livro flutuando
    cx = 8 + 5.4
    rot = ('x', 45.0, (cx, 6.4, 8.0)) if susto else None
    cs.append(C('cajado', cx - 0.45, cx + 0.45, -16.0, 15.0, 5.4, 6.3, 'corda', rot))
    cs.append(C('garra', cx - 1.4, cx + 1.4, 14.4, 15.6, 4.8, 6.9, 'ouro', rot))
    cs.append(C('cristal', cx - 0.9, cx + 0.9, 15.4, 19.0, 5.0, 6.8, 'pupila', rot, **LUZ))
    cs.append(C('livro', 0.0, 3.6, 3.0, 4.0, 2.0, 6.0, 'tecido2', ('z', 22.5, (1.8, 3.5, 4.0))))
    cs.append(C('paginas', 0.3, 3.3, 3.9, 4.3, 2.3, 5.7, 'pano', ('z', 22.5, (1.8, 3.5, 4.0))))
    cs.append(C('cabeca', 5.0, 11.0, 7.0, 13.6, 4.4, 10.4, 'pele'))
    olhos(cs, 11.2, 4.4)
    cs.append(C('sobrancelha', 5.0, 11.0, 11.8, 12.6, 4.1, 4.5, 'tecido2'))
    cs.append(C('nariz', 7.2, 8.8, 8.6, 11.6, 3.0, 4.5, 'pele'))
    cs.append(C('barba', 5.4, 10.6, 3.0, 8.6, 3.8, 4.6, 'pano'))
    cs.append(C('barba_ponta', 6.6, 9.4, 0.4, 3.2, 3.9, 4.5, 'pano'))
    cs.append(C('chapeu', 4.2, 11.8, 13.4, 16.4, 4.0, 10.8, 'tecido'))
    cs.append(C('chapeu_aba', 3.0, 13.0, 13.2, 14.0, 3.0, 11.8, 'tecido'))
    cs.append(C('chapeu_topo', 5.6, 10.4, 16.2, 19.6, 5.4, 9.4, 'tecido', ('x', -22.5, (8.0, 16.2, 7.4))))
    cs.append(C('chapeu_ponta', 6.8, 9.2, 19.0, 22.4, 6.0, 8.4, 'tecido', ('x', -45.0, (8.0, 19.0, 7.2))))
    cs.append(C('estrela', 7.4, 8.6, 14.4, 15.6, 3.85, 4.05, 'pupila', so=('north',), **LUZ))
    return cs


# ------------------------------------------------------------ 7) Senhor das Chamas
def chamas(susto):
    cs = []
    # sem pernas: coluna de fogo e varetas girando em volta
    cs.append(C('nucleo', 5.6, 10.4, -8.0, 4.0, 5.6, 10.4, 'tecido'))
    cs.append(C('nucleo_luz', 6.4, 9.6, -6.0, 2.0, 5.4, 10.6, 'pupila', so=('north', 'south', 'east', 'west'), **LUZ))
    for i in range(3):
        cs.append(C('fogo', 6.6 - i * 0.4, 9.4 + i * 0.4, -14.0 + i * 2.0, -12.0 + i * 2.0, 6.6 - i * 0.4, 9.4 + i * 0.4, 'pupila', **LUZ))
    cs.append(C('fogo_ponta', 7.4, 8.6, -16.0, -14.0, 7.4, 8.6, 'pupila', **LUZ))
    for k, (x, z) in enumerate(((1.6, 8.0), (14.4, 8.0), (8.0, 1.6), (8.0, 14.4), (3.4, 3.4), (12.6, 12.6), (3.4, 12.6), (12.6, 3.4))):
        y0 = -12.0 if k < 4 else -4.0
        y0 += 3.0 if susto else 0.0
        cs.append(C('vareta', x - 0.8, x + 0.8, y0, y0 + 7.0, z - 0.8, z + 0.8, 'ouro'))
        cs.append(C('vareta_luz', x - 0.5, x + 0.5, y0 + 6.8, y0 + 7.8, z - 0.5, z + 0.5, 'pupila', **LUZ))
    # armadura de magma no peito e ombros
    cs.append(C('peito', 3.0, 13.0, 3.6, 10.0, 4.6, 11.4, 'tecido2'))
    cs.append(C('racha', 7.6, 8.4, 4.0, 9.6, 4.45, 4.65, 'pupila', so=('north',), **LUZ))
    for lado in (-1, 1):
        cx = 8 + lado * 6.6
        rot = ('x', 45.0, (cx, 9.0, 8.0)) if susto else None
        cs.append(C('ombro', cx - 2.2, cx + 2.2, 7.4, 11.0, 5.2, 10.8, 'tecido', rot))
        cs.append(C('braco', cx - 1.4, cx + 1.4, 0.6, 7.6, 6.6, 9.4, 'tecido2', rot))
        cs.append(C('punho', cx - 1.8, cx + 1.8, -2.6, 0.8, 6.2, 9.8, 'tecido', rot))
        cs.append(C('chama_mao', cx - 1.0, cx + 1.0, -4.6, -2.4, 7.0, 9.0, 'pupila', rot, **LUZ))
    cs.append(C('cabeca', 4.6, 11.4, 10.4, 16.8, 4.6, 11.4, 'tecido'))
    olhos(cs, 13.8, 4.6, w=0.8, h=0.4)
    cs.append(C('boca', 6.0, 10.0, 11.4, 12.2, 4.45, 4.65, 'pupila', so=('north',), **LUZ))
    for i, x in enumerate((5.0, 6.6, 8.0, 9.4, 11.0)):
        h = (2.6, 4.0, 5.6, 4.0, 2.6)[i]
        cs.append(C('coroa_fogo', x - 0.6, x + 0.6, 16.6, 16.6 + h, 6.0, 10.0, 'pupila', **LUZ))
    cs.append(C('chifre', 3.0, 4.8, 14.4, 18.0, 7.0, 8.8, 'ouro'))
    cs.append(C('chifre', 11.2, 13.0, 14.4, 18.0, 7.0, 8.8, 'ouro'))
    return cs


# ------------------------------------------------------------ 8) Chefe Piglin
def piglin(susto):
    cs = []
    for cx in (5.4, 10.6):
        cs.append(C('perna', cx - 1.8, cx + 1.8, -13.4, -3.0, 6.0, 10.0, 'pele'))
        cs.append(C('bota', cx - 2.1, cx + 2.1, -16.0, -13.2, 5.0, 10.4, 'sapato'))
        cs.append(C('joelheira', cx - 2.0, cx + 2.0, -9.6, -7.2, 5.6, 6.2, 'ouro'))
    cs.append(C('tanga', 3.2, 12.8, -6.0, -1.6, 5.4, 10.6, 'corda'))
    cs.append(C('cinto', 2.8, 13.2, -2.4, -0.6, 5.2, 10.8, 'ouro'))
    cs.append(C('fivela', 6.6, 9.4, -2.8, -0.2, 4.8, 5.3, 'ouro'))
    cs.append(C('tronco', 2.6, 13.4, -0.8, 9.0, 5.0, 11.0, 'pele'))
    cs.append(C('peitoral', 3.0, 13.0, 3.0, 9.4, 4.6, 8.0, 'ouro'))
    cs.append(C('cicatriz', 5.0, 6.0, 0.0, 2.6, 4.85, 5.05, 'tecido2', so=('north',)))
    cs.append(C('pele_capa', 2.0, 14.0, -8.0, 10.4, 10.6, 12.2, 'tecido', ('x', -22.5, (8.0, 10.4, 11.4))))
    for lado in (-1, 1):
        cx = 8 + lado * 7.6
        rot = ('x', 45.0, (cx, 8.6, 8.0)) if susto else None
        cs.append(C('ombreira', cx - 2.6, cx + 2.6, 7.2, 10.8, 5.0, 11.0, 'ouro', rot))
        cs.append(C('presa_ombro', cx - 0.5 + lado * 1.4, cx + 0.5 + lado * 1.4, 10.6, 13.0, 7.4, 8.6, 'pano', rot))
        cs.append(C('braco', cx - 1.8, cx + 1.8, -1.0, 7.4, 6.2, 9.8, 'pele', rot))
        cs.append(C('bracadeira', cx - 2.1, cx + 2.1, -3.6, -0.6, 5.9, 10.1, 'ouro', rot))
        if lado == 1:
            cs.append(C('cabo', cx - 0.6, cx + 0.6, -10.0, 12.0, 3.4, 4.6, 'corda', rot))
            cs.append(C('machado', cx - 0.4, cx + 0.4, 6.0, 13.0, 4.6, 11.0, 'ouro', rot))
            cs.append(C('machado2', cx - 0.4, cx + 0.4, 6.0, 13.0, -3.0, 3.4, 'ouro', rot))
            cs.append(C('fio', cx - 0.3, cx + 0.3, 5.4, 13.6, 10.6, 11.6, 'pano', rot))
            cs.append(C('fio', cx - 0.3, cx + 0.3, 5.4, 13.6, -3.6, -2.6, 'pano', rot))
    cs.append(C('cabeca', 4.0, 12.0, 9.0, 17.0, 4.6, 12.0, 'pele'))
    olhos(cs, 14.6, 4.6, cx=(5.8, 10.2), w=0.7)
    cs.append(C('focinho', 6.0, 10.0, 10.4, 13.4, 2.6, 4.7, 'pele'))
    for x in (7.0, 9.0):
        cs.append(C('narina', x - 0.4, x + 0.4, 11.6, 12.4, 2.45, 2.65, 'tecido2', so=('north',)))
    for x in (5.6, 10.4):
        cs.append(C('presa', x - 0.5, x + 0.5, 10.0, 13.0, 3.2, 4.2, 'pano'))
    for lado in (-1, 1):
        cs.append(C('orelha', (2.0 if lado < 0 else 12.0), (4.2 if lado < 0 else 14.0), 12.0, 16.0, 7.0, 9.4, 'pele', ('z', 22.5 * lado, (8 + lado * 4.0, 16.0, 8.0))))
    cs.append(C('coroa', 4.4, 11.6, 16.8, 18.0, 5.0, 11.6, 'ouro'))
    for x in (5.0, 8.0, 11.0):
        cs.append(C('coroa_ponta', x - 0.6, x + 0.6, 17.9, 20.4 if x == 8.0 else 19.4, 5.1, 6.3, 'ouro'))
    cs.append(C('joia', 7.4, 8.6, 17.0, 17.8, 4.8, 5.05, 'pupila', so=('north',), **LUZ))
    return cs


# ------------------------------------------------------------ 9) General Wither
def general(susto):
    cs = []
    R.esqueleto(cs, susto)
    cs.append(C('peitoral', 3.6, 12.4, -0.4, 7.8, 4.8, 10.8, 'tecido'))
    cs.append(C('peito_cav', 6.6, 9.4, 2.6, 5.4, 4.6, 4.85, 'pano', so=('north',)))
    olhos(cs, 4.4, 4.6, cx=(7.4, 8.6), w=0.3, h=0.3)
    cs.append(C('saiote', 3.8, 12.2, -7.0, -1.0, 5.4, 10.6, 'tecido'))
    cs.append(C('saiote_fio', 3.7, 12.3, -7.4, -6.6, 5.3, 10.7, 'ouro'))
    cs.append(C('estandarte', 4.0, 12.0, -10.0, 9.0, 11.2, 11.8, 'tecido2', ('x', -22.5, (8.0, 9.0, 11.5))))
    cs.append(C('mastro', 3.4, 12.6, 9.0, 9.8, 11.0, 12.0, 'ouro'))
    for lado in (-1, 1):
        cx = 8 + lado * 5.8
        cs.append(C('ombreira', cx - 2.4, cx + 2.4, 6.2, 9.4, 5.0, 11.0, 'tecido'))
        # mini cranio de wither em cada ombro (o chefe tem tres cabecas)
        cs.append(C('cranio_ombro', cx - 1.6, cx + 1.6, 9.2, 12.2, 6.2, 9.4, 'pano'))
        olhos(cs, 11.0, 6.2, cx=(cx - 0.7, cx + 0.7), w=0.3, h=0.3)
    cs.append(C('elmo', 4.6, 11.4, 13.6, 16.6, 4.6, 11.4, 'tecido'))
    cs.append(C('elmo_crista', 7.4, 8.6, 16.4, 19.8, 5.0, 11.6, 'ouro'))
    cs.append(C('viseira', 4.4, 11.6, 13.4, 14.2, 4.4, 5.0, 'ouro'))
    cx = 8 + 5.2
    rot = ('x', 45.0, (cx, 6.8, 8.0)) if susto else None
    cs.append(C('cabo', cx - 0.45, cx + 0.45, -9.6, -4.6, 7.55, 8.45, 'ouro', rot))
    cs.append(C('guarda', cx - 2.6, cx + 2.6, -4.8, -3.6, 7.3, 8.7, 'ouro', rot))
    cs.append(C('lamina', cx - 1.3, cx + 1.3, -3.6, 15.6, 7.7, 8.3, 'tecido2', rot))
    cs.append(C('lamina_luz', cx - 0.25, cx + 0.25, -2.8, 14.6, 7.6, 8.4, 'pupila', rot, so=('north', 'south'), **LUZ))
    return cs


# ------------------------------------------------------------ 10) Senhor do Vazio
def vazio(susto):
    cs = []
    # bem alto e fino, membros compridos
    for cx in (6.4, 9.6):
        cs.append(C('perna', cx - 0.8, cx + 0.8, -16.0, 0.0, 7.2, 8.8, 'tecido'))
        cs.append(C('joelho', cx - 1.0, cx + 1.0, -8.6, -7.0, 7.0, 9.0, 'tecido2'))
    cs.append(C('quadril', 5.0, 11.0, -0.4, 1.8, 6.4, 9.6, 'tecido2'))
    cs.append(C('tronco', 5.0, 11.0, 1.6, 12.0, 6.4, 9.6, 'tecido'))
    cs.append(C('fenda', 7.6, 8.4, 3.0, 10.4, 6.25, 6.45, 'pupila', so=('north',), **LUZ))
    cs.append(C('manto', 4.0, 12.0, -11.6, 12.0, 9.6, 10.4, 'tecido2', ('x', -22.5, (8.0, 12.0, 10.0))))
    cs.append(C('gola', 3.6, 12.4, 10.6, 13.6, 6.0, 10.6, 'tecido2'))
    for lado in (-1, 1):
        cx = 8 + lado * 4.0
        rot = ('x', 45.0, (cx, 11.6, 8.0)) if susto else None
        cs.append(C('braco', cx - 0.7, cx + 0.7, -6.0, 12.0, 7.3, 8.7, 'tecido', rot))
        cs.append(C('garra', cx - 1.0, cx + 1.0, -8.0, -6.0, 7.0, 9.0, 'tecido2', rot))
    cs.append(C('cabeca', 4.4, 11.6, 13.4, 20.6, 4.4, 11.6, 'tecido'))
    olhos(cs, 16.6, 4.4, cx=(6.0, 10.0), w=1.0, h=0.4)
    cs.append(C('boca', 5.6, 10.4, 13.6, 15.6 if susto else 14.0, 4.3, 4.5, 'pupila', so=('north',), **LUZ))
    # coroa de cristais do End e estilhacos de obsidiana flutuando
    for i, x in enumerate((5.0, 8.0, 11.0)):
        cs.append(C('cristal', x - 0.8, x + 0.8, 20.6, 23.6 + (1.6 if i == 1 else 0), 7.2, 8.8, 'pupila', **LUZ))
    cs.append(C('aro', 4.2, 11.8, 20.4, 21.2, 4.2, 11.8, 'ouro'))
    for x, y, z in ((-2.0, 8.0, 6.0), (17.0, 4.0, 9.0), (-1.0, -4.0, 11.0), (16.0, 14.0, 4.0)):
        cs.append(C('estilhaco', x, x + 2.0, y, y + 3.0, z, z + 2.0, 'sapato', ('y', 45.0, (x + 1.0, y + 1.5, z + 1.0))))
        cs.append(C('estilhaco_luz', x + 0.6, x + 1.4, y + 0.6, y + 2.4, z - 0.1, z + 2.1, 'pupila', ('y', 45.0, (x + 1.0, y + 1.5, z + 1.0)), **LUZ))
    return cs


CHEFES = [
    ('rei_b', None, None, 'Rei Esqueleto', 'Cripta dos Mortos'),
    ('c_rainha', rainha, ((0.55, 0.02, 1.0), dict(tecido=mat((0.10, 0.06, 0.12), 0.4, 30, 0.3), tecido2=mat((0.05, 0.03, 0.06), 0.3, 20), corda=mat((0.75, 0.05, 0.06), 0.2, 14), pele=mat((0.14, 0.09, 0.14), 0.2, 12), pano=mat((0.92, 0.88, 0.80)))), 'Rainha Aranha', 'Ninho da Aranha'),
    ('c_mineiro', mineiro, ((1.0, 0.85, 0.45), dict(tecido=mat((0.16, 0.26, 0.48)), tecido2=mat((0.30, 0.22, 0.14)), pele=mat((0.36, 0.52, 0.30)), corda=mat((0.36, 0.24, 0.12)), ouro=mat((0.92, 0.72, 0.10), 0.5, 40, 0.4), sapato=mat((0.32, 0.33, 0.36), 0.8, 60, 0.6))), 'Mineiro Gigante', 'Mina Abandonada'),
    ('c_bruxa', bruxa, ((0.45, 1.0, 0.30), dict(tecido=mat((0.22, 0.12, 0.30)), tecido2=mat((0.08, 0.07, 0.10)), pele=mat((0.56, 0.62, 0.42)), pano=mat((0.78, 0.76, 0.70)), corda=mat((0.30, 0.20, 0.12)), ouro=mat((0.70, 0.56, 0.24), 0.4, 30, 0.3))), 'Bruxa Anciã', 'Covil da Bruxa'),
    ('c_gelo', rei_gelo, ((0.55, 0.95, 1.0), dict(tecido=mat((0.55, 0.78, 0.92), 0.6, 60, 0.5), tecido2=mat((0.20, 0.36, 0.58)), pano=mat((0.84, 0.94, 1.0), 0.5, 60, 0.6), sapato=mat((0.92, 0.94, 0.96), 0.1, 8))), 'Rei do Gelo', 'Fortaleza de Gelo'),
    ('c_mago', arquimago, ((0.75, 0.35, 1.0), dict(tecido=mat((0.14, 0.16, 0.42)), tecido2=mat((0.36, 0.10, 0.16)), pele=mat((0.62, 0.62, 0.58)), pano=mat((0.86, 0.86, 0.84)), corda=mat((0.30, 0.20, 0.12)))), 'Arquimago', 'Torre do Arquimago'),
    ('c_chamas', chamas, ((1.0, 0.55, 0.10), dict(tecido=mat((0.22, 0.12, 0.08), 0.4, 30, 0.2), tecido2=mat((0.12, 0.06, 0.05), 0.4, 30, 0.2), ouro=mat((0.95, 0.62, 0.12), 0.9, 60, 0.9))), 'Senhor das Chamas', 'Forja Infernal'),
    ('c_piglin', piglin, ((1.0, 0.25, 0.15), dict(pele=mat((0.82, 0.52, 0.50)), tecido=mat((0.34, 0.22, 0.14)), tecido2=mat((0.50, 0.24, 0.26)), corda=mat((0.30, 0.18, 0.10)), pano=mat((0.94, 0.90, 0.80)))), 'Chefe Piglin', 'Bastião Perdido'),
    ('c_general', general, ((0.90, 0.85, 1.0), dict(tecido=mat((0.16, 0.15, 0.18), 0.85, 60, 0.7), tecido2=mat((0.28, 0.10, 0.36)), pano=mat((0.24, 0.24, 0.26), 0.4, 30, 0.2), ouro=mat((0.48, 0.24, 0.62), 0.8, 60, 0.7))), 'General Wither', 'Fortaleza Wither'),
    ('c_vazio', vazio, ((0.90, 0.30, 1.0), dict(tecido=mat((0.06, 0.05, 0.08), 0.3, 20), tecido2=mat((0.14, 0.06, 0.20)), ouro=mat((0.30, 0.12, 0.40), 0.8, 50, 0.6), sapato=mat((0.06, 0.04, 0.10), 0.7, 60, 0.6))), 'Senhor do Vazio', 'Cidadela do Vazio'),
]
for ident, f, cfg, _, _ in CHEFES:
    if f is None:
        continue
    A.PALETAS[ident] = dict(pupila=cfg[0])
    H.MATS[ident] = mats(**cfg[1])
    H.CORPOS[ident] = f

if __name__ == '__main__':
    so = sys.argv[2:] or [c[0] for c in CHEFES]
    A.gerar(A.SAIDA, so)


# ------------------------------------------------------------ acabamento (18/11, Sui: "parecem feitos de la")
# a trama de tecido do hotel3d ia em tudo (armadura, pele, quitina, gelo). Aqui: o que nao e pano fica liso (bisel e
# manchas grandes), e o pano tem trama bem fina. O Rei Esqueleto (rei_b) fica como esta.
LISO = {
    'c_rainha': {'tecido', 'tecido2', 'pele', 'corda', 'pano'}, 'c_mineiro': {'pele', 'corda', 'sapato', 'ouro'},
    'bruxa_a': {'pele', 'pano', 'sapato'}, 'c_gelo': {'tecido', 'pano', 'sapato'}, 'c_mago': {'pele', 'pano', 'corda'},
    'c_chamas': {'tecido', 'tecido2', 'ouro'}, 'c_piglin': {'pele', 'pano', 'corda'}, 'c_general': {'tecido', 'pano', 'ouro'},
    'c_vazio': {'tecido', 'sapato', 'ouro'},
}
BASE_DE = {}
_pintar_hotel = A.pintar_face
TH = A.TH


def _pintar(c, f, wpx, hpx, sem, ident, esc):
    base = BASE_DE.get(ident, ident)
    mat = c.pele
    if base not in LISO or mat not in H.MATS['figura'] or wpx < 4 or hpx < 4:
        return _pintar_hotel(c, f, wpx, hpx, sem, ident, esc)
    w, h = wpx / esc, hpx / esc
    F = TH.Face(w, h, sem)
    tudo = F.ret(0, 0, w, h)
    if mat in LISO[base]:
        F.pintar(tudo, mat, alt=0.25, bisel=0.35, ruido=0.07, escala_ruido=0.9)
        mancha = TH.fbm(F.x * 0.35, F.y * 0.35, sem * 0.13, oct=3, seed=sem + 7) - 0.5
        F.alb = np.clip(F.alb * (1 + 0.22 * mancha[..., None]), 0, 1)
        F.alt += 0.12 * mancha
    else:
        F.pintar(tudo, mat, alt=0.12, bisel=0.18, ruido=0.08, escala_ruido=1.6)
        trama = np.sin(F.x * 16.0) * np.sin(F.y * 16.0) * 0.012
        dobra = TH.fbm(F.x * 0.45, F.y * 1.8, sem * 0.21, oct=3, seed=sem) - 0.5
        F.alt += trama + 0.14 * dobra
        F.alb = np.clip(F.alb * (1 + trama[..., None] + 0.28 * dobra[..., None]), 0, 1)
    yw = np.full(F.x.shape, float(c.de[1])) if f in ('up', 'down') else c.ate[1] - F.y
    F.alb *= (1 - np.clip((-9.0 - yw) / 8.0, 0, 1) * 0.35)[..., None]
    img = F.render()
    brilho = np.clip(np.max(F.emis, axis=2), 0, 1.2) / 1.2
    return img, brilho


A.pintar_face = _pintar
