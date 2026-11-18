"""Rei Esqueleto (chefe da Cripta dos Mortos) esculpido em 3D, 3 opcoes para o Paulo escolher. Desenho original.
uso: PYTHONPATH=plugins/coronapack:ferramentas/modelos python3 reiesq3d.py <saida>"""
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
import hotel3d as H
A = H.A
C = A.C
LUZ = dict(emite=15)


def esqueleto(cs, susto, ossos_luz=False):
    # pernas finas de osso, joelho saliente, pe comprido
    for cx in (5.9, 10.1):
        cs.append(C('femur', cx - 0.7, cx + 0.7, -9.2, -3.0, 7.3, 8.7, 'pano'))
        cs.append(C('joelho', cx - 1.0, cx + 1.0, -10.4, -8.8, 6.8, 9.0, 'pano'))
        cs.append(C('canela', cx - 0.55, cx + 0.55, -14.8, -10.2, 7.45, 8.55, 'pano'))
        cs.append(C('pe', cx - 1.0, cx + 1.0, -16.0, -14.6, 5.4, 9.0, 'pano'))
    cs.append(C('bacia', 4.6, 11.4, -3.8, -1.8, 6.4, 9.6, 'pano'))
    cs.append(C('coluna', 7.4, 8.6, -2.0, 7.0, 8.8, 9.9, 'pano'))
    # caixa de costelas aberta
    for k in range(4):
        y = 0.6 + k * 1.45
        enc = k * 0.25
        cs.append(C('costela', 4.4 + enc, 7.3, y, y + 0.7, 5.4, 6.1, 'pano', so=('north', 'up', 'down', 'west')))
        cs.append(C('costela', 8.7, 11.6 - enc, y, y + 0.7, 5.4, 6.1, 'pano', so=('north', 'up', 'down', 'east')))
        cs.append(C('costela_l', 4.2 + enc, 4.9 + enc, y, y + 0.7, 5.4, 9.6, 'pano'))
        cs.append(C('costela_l', 11.1 - enc, 11.8 - enc, y, y + 0.7, 5.4, 9.6, 'pano'))
    cs.append(C('esterno', 7.45, 8.55, 0.4, 6.6, 5.2, 6.0, 'pano'))
    if ossos_luz:
        cs.append(C('alma', 6.6, 9.4, 1.6, 5.0, 6.4, 8.6, 'pupila', **LUZ))
    cs.append(C('clavicula', 3.2, 12.8, 6.4, 7.4, 6.6, 8.6, 'pano'))
    # bracos de osso
    for lado in (-1, 1):
        cx = 8 + lado * 5.2
        rot = ('x', 45.0 if susto else 0.0, (cx, 6.8, 8.0)) if susto else None
        cs.append(C('umero', cx - 0.6, cx + 0.6, 1.0, 6.8, 7.4, 8.6, 'pano', rot))
        cs.append(C('cotovelo', cx - 0.85, cx + 0.85, 0.2, 1.4, 7.15, 8.85, 'pano', rot))
        cs.append(C('radio', cx - 0.5, cx + 0.5, -4.8, 0.4, 7.5, 8.5, 'pano', rot))
        cs.append(C('mao', cx - 0.9, cx + 0.9, -6.2, -4.6, 7.1, 8.9, 'pano', rot))
        for d in (-0.6, 0.0, 0.6):
            cs.append(C('dedo', cx + d - 0.2, cx + d + 0.2, -7.6, -6.0, 7.2, 7.7, 'pano', rot))
    # cranio
    cs.append(C('pescoco', 7.2, 8.8, 7.2, 8.4, 7.6, 9.2, 'pano'))
    cs.append(C('cranio', 4.8, 11.2, 9.6, 15.6, 5.0, 11.2, 'pano'))
    cs.append(C('testa', 5.2, 10.8, 13.6, 15.0, 4.6, 5.1, 'pano'))
    for cx in (6.5, 9.5):
        cs.append(C('orbita', cx - 1.0, cx + 1.0, 11.8, 13.4, 4.85, 5.05, 'boca', so=('north',)))
        cs.append(C('olho', cx - 0.45, cx + 0.45, 12.2, 13.0, 4.75, 4.9, 'pupila', so=('north',), **LUZ))
    cs.append(C('nariz', 7.6, 8.4, 11.0, 11.8, 4.85, 5.05, 'boca', so=('north',)))
    mand = ('x', -22.5, (8.0, 9.6, 6.0)) if susto else None
    cs.append(C('goela', 5.8, 10.2, 9.0, 10.2, 5.2, 5.6, 'boca', so=('north',)))
    A.dentes(cs, 5.6, 10.4, 9.7, 10.4, 4.85, 5.15, passo=0.7, larg=0.45)
    cs.append(C('mandibula', 5.4, 10.6, 8.2, 9.4, 5.0, 9.8, 'pano', mand))
    A.dentes(cs, 5.8, 10.2, 9.3, 9.8, 4.9, 5.2, passo=0.7, larg=0.45, rot=mand, nome='dente_b')


def coroa(cs, y0, alta=True, mat='ouro', joia=True):
    cs.append(C('coroa', 4.5, 11.5, y0, y0 + 1.4, 4.7, 11.5, mat))
    for cx in (5.0, 8.0, 11.0):
        for cz in (5.2, 11.0):
            h = (3.4 if cx == 8.0 else 2.2) if alta else 1.4
            cs.append(C('coroa_ponta', cx - 0.5, cx + 0.5, y0 + 1.3, y0 + 1.3 + h, cz - 0.5, cz + 0.5, mat))
    if joia:
        cs.append(C('joia', 7.3, 8.7, y0 + 0.2, y0 + 1.2, 4.5, 4.75, 'pupila', so=('north',), **LUZ))


def arco(cs, susto, mat='pele', corda='corda', luz=False):
    cx = 8 - 5.2
    rot = ('x', 45.0, (cx, 6.8, 8.0)) if susto else None
    # arco grande de osso na mao esquerda, em 7 gomos curvos
    gomos = [(-14.0, -10.0, 2.2), (-10.0, -6.8, 1.0), (-6.8, -4.0, 0.4), (-4.0, -1.2, 0.4), (-1.2, 2.0, 1.0), (2.0, 5.6, 2.2), (5.6, 8.6, 3.6)]
    for y0, y1, dz in gomos:
        cs.append(C('arco', cx - 0.5, cx + 0.5, y0 - 1.0, y1 - 1.0, 6.2 - dz - 0.6, 6.2 - dz + 0.6, mat, rot))
    cs.append(C('arco_ponta', cx - 0.7, cx + 0.7, 7.4, 9.2, 1.6, 3.4, 'ouro', rot))
    cs.append(C('arco_ponta', cx - 0.7, cx + 0.7, -16.0, -14.4, 3.0, 4.6, 'ouro', rot))
    cs.append(C('corda_arco', cx - 0.12, cx + 0.12, -14.6, 7.8, 6.4, 6.6, 'pupila' if luz else corda, rot, **(LUZ if luz else {})))


# ---------------------------------------------------------------- A) Rei de Ossos: ouro, manto roxo de arminho, capa vermelha
def rei_ossos(susto):
    cs = []
    esqueleto(cs, susto)
    coroa(cs, 15.2)
    # manto curto sobre os ombros (roxo com borda de arminho) e capa vermelha longa
    cs.append(C('manto', 2.4, 13.6, 5.2, 8.2, 5.8, 11.2, 'tecido'))
    cs.append(C('arminho', 2.2, 13.8, 4.4, 5.4, 5.6, 11.4, 'pano2' if False else 'sapato'))
    cs.append(C('broche', 7.2, 8.8, 6.0, 7.6, 5.5, 5.85, 'ouro'))
    cs.append(C('capa', 3.0, 13.0, -14.0, 7.6, 10.8, 11.6, 'corda', ('x', -22.5, (8.0, 7.6, 11.2))))
    cs.append(C('capa_barra', 3.0, 13.0, -14.8, -13.6, 11.0, 11.7, 'ouro', ('x', -22.5, (8.0, 7.6, 11.2))))
    cs.append(C('cinto', 4.4, 11.6, -2.2, -1.2, 6.2, 9.8, 'ouro'))
    cs.append(C('saia', 4.6, 11.4, -7.0, -2.0, 6.6, 9.6, 'tecido', so=('north', 'south', 'east', 'west', 'down')))
    cs.append(C('saia_fio', 4.5, 11.5, -7.4, -6.8, 6.5, 9.7, 'ouro'))
    for lado in (-1, 1):
        cx = 8 + lado * 5.2
        rot = ('x', 45.0, (cx, 6.8, 8.0)) if susto else None
        cs.append(C('bracelete', cx - 0.9, cx + 0.9, -2.0, -0.8, 7.1, 8.9, 'ouro', rot))
    arco(cs, susto)
    return cs


# ---------------------------------------------------------------- B) Rei Sombrio: ossos negros, fogo de alma azul, manto rasgado
def rei_sombrio(susto):
    cs = []
    esqueleto(cs, susto, ossos_luz=True)
    # coroa de ferro com espinhos altos e chamas azuis
    cs.append(C('coroa', 4.5, 11.5, 15.2, 16.4, 4.7, 11.5, 'ouro'))
    for i, cx in enumerate((4.9, 6.4, 8.0, 9.6, 11.1)):
        h = (2.0, 3.0, 4.6, 3.0, 2.0)[i]
        cs.append(C('espinho', cx - 0.4, cx + 0.4, 16.3, 16.3 + h, 4.8, 5.6, 'ouro'))
        cs.append(C('chama', cx - 0.25, cx + 0.25, 16.3 + h, 17.2 + h, 4.9, 5.5, 'pupila', **LUZ))
    # capuz/manto rasgado em tiras atras e nos ombros
    cs.append(C('ombro', 2.2, 13.8, 5.4, 8.4, 6.0, 11.0, 'tecido'))
    for lado in (-1, 1):
        cx = 8 + lado * 5.6
        cs.append(C('ombreira', cx - 1.8, cx + 1.8, 7.2, 9.6, 6.2, 10.4, 'ouro'))
        cs.append(C('ombreira_esp', cx - 0.4 + lado * 0.8, cx + 0.4 + lado * 0.8, 9.4, 11.8, 7.8, 8.6, 'ouro'))
    for xa, xb, fim in ((2.6, 5.4, -12.0), (5.6, 8.0, -15.0), (8.2, 10.6, -13.0), (10.8, 13.4, -10.0)):
        cs.append(C('trapo', xa, xb, fim, 7.0, 10.6, 11.3, 'tecido2', ('x', -22.5, (8.0, 7.0, 11.0))))
    cs.append(C('cinto', 4.4, 11.6, -2.4, -1.2, 6.2, 9.8, 'tecido2'))
    for xa, xb, fim in ((4.6, 6.6, -9.0), (6.9, 9.1, -11.0), (9.4, 11.4, -8.0)):
        cs.append(C('trapo_f', xa, xb, fim, -1.6, 6.0, 6.5, 'tecido2'))
    cs.append(C('runa', 7.6, 8.4, -6.0, -2.6, 5.9, 6.05, 'pupila', so=('north',), **LUZ))
    arco(cs, susto, mat='ouro', luz=True)
    return cs


# ---------------------------------------------------------------- C) Lich Coroado: tunica vermelha de capuz, cajado de caveira
def lich(susto):
    cs = []
    esqueleto(cs, susto)
    # tunica longa ate o chao (esconde as pernas), capuz aberto, coroa fina por cima do capuz
    cs.append(C('tunica', 4.0, 12.0, -16.0, -2.0, 5.6, 10.6, 'tecido', so=('north', 'south', 'east', 'west', 'up')))
    cs.append(C('tunica_barra', 3.8, 12.2, -16.0, -14.8, 5.4, 10.8, 'ouro'))
    cs.append(C('tunica_meio', 7.3, 8.7, -14.8, -2.0, 5.45, 5.65, 'ouro', so=('north',)))
    cs.append(C('cinto', 4.0, 12.0, -2.6, -1.0, 5.4, 10.8, 'ouro'))
    cs.append(C('ombros', 2.6, 13.4, 5.0, 8.0, 5.8, 10.8, 'tecido'))
    cs.append(C('capuz', 4.2, 11.8, 9.0, 16.6, 10.4, 12.2, 'tecido2'))
    cs.append(C('capuz_l', 4.0, 4.7, 9.0, 16.4, 6.0, 12.0, 'tecido2'))
    cs.append(C('capuz_l', 11.3, 12.0, 9.0, 16.4, 6.0, 12.0, 'tecido2'))
    cs.append(C('capuz_c', 4.0, 12.0, 15.6, 16.8, 5.4, 12.2, 'tecido2'))
    coroa(cs, 16.6, alta=False)
    cs.append(C('capa', 3.4, 12.6, -15.6, 7.6, 10.6, 11.4, 'corda'))
    # cajado na mao direita com caveira e orbe aceso
    cx = 8 + 5.2
    rot = ('x', 45.0, (cx, 6.8, 8.0)) if susto else None
    cs.append(C('cajado', cx - 0.45, cx + 0.45, -16.0, 14.0, 6.0, 6.9, 'pele', rot))
    cs.append(C('cajado_aro', cx - 0.7, cx + 0.7, 13.6, 14.4, 5.75, 7.15, 'ouro', rot))
    cs.append(C('caveirinha', cx - 1.4, cx + 1.4, 14.4, 17.0, 5.0, 7.8, 'pano', rot))
    cs.append(C('caveira_olho', cx - 1.0, cx - 0.2, 15.6, 16.2, 4.9, 5.05, 'pupila', rot, so=('north',), **LUZ))
    cs.append(C('caveira_olho', cx + 0.2, cx + 1.0, 15.6, 16.2, 4.9, 5.05, 'pupila', rot, so=('north',), **LUZ))
    cs.append(C('orbe', cx - 0.8, cx + 0.8, 17.2, 18.8, 5.6, 7.2, 'pupila', rot, **LUZ))
    return cs


A.PALETAS['rei_a'] = dict(pupila=(1.0, 0.78, 0.18))
A.PALETAS['rei_b'] = dict(pupila=(0.25, 0.92, 1.0))
A.PALETAS['rei_c'] = dict(pupila=(1.0, 0.20, 0.12))
H.MATS['rei_a'] = {'tecido': ((0.36, 0.13, 0.52), 0.15, 12, 0.0), 'tecido2': ((0.22, 0.07, 0.32), 0.12, 10, 0.0),
                   'ouro': ((0.98, 0.76, 0.20), 0.95, 70, 0.95), 'pano': ((0.90, 0.87, 0.78), 0.2, 14, 0.0),
                   'pele': ((0.84, 0.80, 0.70), 0.2, 14, 0.0), 'corda': ((0.55, 0.06, 0.09), 0.1, 8, 0.0),
                   'sapato': ((0.95, 0.94, 0.90), 0.1, 8, 0.0)}
H.MATS['rei_b'] = {'tecido': ((0.10, 0.11, 0.14), 0.2, 16, 0.0), 'tecido2': ((0.05, 0.06, 0.08), 0.15, 12, 0.0),
                   'ouro': ((0.30, 0.32, 0.36), 0.9, 60, 0.9), 'pano': ((0.20, 0.20, 0.23), 0.5, 30, 0.2),
                   'pele': ((0.15, 0.15, 0.17), 0.3, 20, 0.0), 'corda': ((0.08, 0.09, 0.11), 0.1, 8, 0.0),
                   'sapato': ((0.04, 0.04, 0.05), 0.7, 50, 0.0)}
H.MATS['rei_c'] = {'tecido': ((0.50, 0.07, 0.09), 0.15, 12, 0.0), 'tecido2': ((0.30, 0.04, 0.06), 0.12, 10, 0.0),
                   'ouro': ((0.86, 0.66, 0.24), 0.9, 60, 0.9), 'pano': ((0.80, 0.74, 0.58), 0.15, 12, 0.0),
                   'pele': ((0.30, 0.20, 0.12), 0.1, 8, 0.0), 'corda': ((0.12, 0.04, 0.05), 0.1, 8, 0.0),
                   'sapato': ((0.04, 0.04, 0.05), 0.7, 50, 0.0)}
H.CORPOS['rei_a'] = rei_ossos
H.CORPOS['rei_b'] = rei_sombrio
H.CORPOS['rei_c'] = lich

if __name__ == '__main__':
    A.gerar(A.SAIDA, ['rei_a', 'rei_b', 'rei_c'])
