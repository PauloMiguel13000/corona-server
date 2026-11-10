"""Boss mundial ORIGINAL do Corona Server: o Colosso da Coroa Partida - um rei-guerreiro gigante de armadura escura,
coroa quebrada, capa vermelha rasgada, nucleo que brilha no peito e uma espada enorme. 2 quadros: f0 de guarda (espada
apoiada no chao), f1 atacando (bracos e espada para a frente). Itens: corona:props/anim_colosso_f0|f1.

uso: python3 -I colosso3d.py <saida>
"""
import os
import sys

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)
import hotel3d as H  # noqa: E402

A = H.A
C = A.C

A.PALETAS['colosso'] = dict(pupila=(1.0, 0.50, 0.10))
H.MATS['colosso'] = {
    'tecido': ((0.13, 0.12, 0.15), 0.8, 55, 0.85), 'tecido2': ((0.055, 0.05, 0.07), 0.85, 65, 0.9),
    'ouro': ((0.98, 0.76, 0.20), 0.95, 70, 0.95), 'pano': ((0.80, 0.77, 0.68), 0.3, 20, 0.0),
    'pele': ((0.62, 0.64, 0.70), 0.95, 80, 0.95), 'corda': ((0.46, 0.03, 0.05), 0.1, 8, 0.0),
    'sapato': ((0.04, 0.04, 0.05), 0.7, 50, 0.6),
}
LUZ = dict(emite=15)


def colosso(susto):
    cs = []
    # pernas largas com rachaduras de magma
    for xa, xb in ((3.6, 7.3), (8.7, 12.4)):
        cs.append(C('perna', xa, xb, -13.6, -3.0, 5.8, 10.2, 'tecido'))
        cs.append(C('racha_perna', (xa + xb) / 2 - 0.3, (xa + xb) / 2 + 0.3, -13.0, -9.6, 5.6, 5.85, 'pupila', so=('north',), **LUZ))
        cs.append(C('joelho', xa - 0.4, xb + 0.4, -9.6, -6.8, 5.0, 7.0, 'ouro'))
        cs.append(C('joelho_ponta', (xa + xb) / 2 - 0.5, (xa + xb) / 2 + 0.5, -7.0, -4.6, 4.6, 5.6, 'ouro'))
        cs.append(C('bota', xa - 0.6, xb + 0.6, -16.0, -13.2, 4.0, 10.6, 'sapato'))
        cs.append(C('bota_bico', xa - 0.3, xb + 0.3, -16.0, -14.6, 2.8, 4.1, 'ouro'))
    # saiote de placas e cinto com a caveira
    cs.append(C('saiote', 3.0, 13.0, -6.4, -2.6, 5.2, 10.8, 'tecido2'))
    cs.append(C('placa_frente', 6.2, 9.8, -9.6, -2.8, 4.6, 5.3, 'tecido'))
    cs.append(C('placa_borda', 6.0, 10.0, -10.0, -9.3, 4.5, 5.4, 'ouro'))
    cs.append(C('cinto', 2.8, 13.2, -3.2, -1.6, 5.0, 11.0, 'ouro'))
    cs.append(C('fivela', 6.9, 9.1, -3.8, -1.2, 4.3, 5.1, 'pano'))
    # tronco em V: cintura fina, peito enorme, nucleo de magma com costelas de ouro
    cs.append(C('cintura', 4.0, 12.0, -2.0, 3.0, 5.6, 10.4, 'tecido2'))
    cs.append(C('tronco', 2.8, 13.2, 2.6, 6.6, 5.0, 11.0, 'tecido'))
    cs.append(C('peito', 1.6, 14.4, 6.2, 11.4, 4.4, 11.6, 'tecido'))
    cs.append(C('nucleo', 6.4, 9.6, 6.6, 10.0, 4.05, 4.42, 'pupila', so=('north',), **LUZ))
    cs.append(C('nucleo_aro_c', 6.0, 10.0, 10.0, 10.7, 3.9, 4.5, 'ouro'))
    cs.append(C('nucleo_aro_b', 6.0, 10.0, 5.9, 6.6, 3.9, 4.5, 'ouro'))
    for lado in (-1, 1):
        for k in range(3):
            xa = 8 + lado * 2.2
            xb = 8 + lado * (5.6 - k * 0.5)
            cs.append(C('costela', min(xa, xb), max(xa, xb), 9.2 - k * 1.5, 9.8 - k * 1.5, 4.15, 4.5, 'ouro', so=('north', 'up', 'down')))
        cs.append(C('racha_peito', 8 + lado * 4.6 - 0.25, 8 + lado * 4.6 + 0.25, 2.8, 6.0, 4.75, 5.05, 'pupila', so=('north',), **LUZ))
    cs.append(C('veia', 7.6, 8.4, -1.6, 5.9, 5.3, 5.62, 'pupila', so=('north',), **LUZ))
    # capa rasgada em tres tiras, mais larga que o corpo
    for xa, xb, fim in ((1.4, 5.6, -13.0), (5.9, 10.1, -15.0), (10.4, 14.6, -10.5)):
        cs.append(C('capa', xa, xb, fim, 11.0, 11.6, 12.4, 'corda', ('x', -22.5, (8.0, 11.0, 11.8))))
    cs.append(C('capa_gola', 2.4, 13.6, 10.6, 12.6, 9.6, 12.2, 'corda'))
    # ombreiras em camadas com tres espinhos
    for lado in (-1, 1):
        cx = 8 + lado * 7.6
        cs.append(C('ombreira', cx - 3.0, cx + 3.0, 9.2, 12.6, 4.2, 11.8, 'tecido2'))
        cs.append(C('ombreira2', cx - 2.2 + lado * 1.2, cx + 2.2 + lado * 1.2, 7.0, 9.4, 4.8, 11.2, 'tecido'))
        cs.append(C('ombreira_borda', cx - 3.1, cx + 3.1, 9.0, 9.7, 4.1, 11.9, 'ouro', so=('north', 'south', 'east', 'west')))
        for dz, alto in ((5.6, 2.6), (8.0, 4.4), (10.4, 2.6)):
            cs.append(C('espinho', cx - 0.55 + lado * 1.4, cx + 0.55 + lado * 1.4, 12.4, 12.4 + alto, dz - 0.55, dz + 0.55, 'ouro'))
        cs.append(C('ombro_luz', cx - 1.2, cx + 1.2, 10.2, 11.4, 3.95, 4.25, 'pupila', so=('north',), **LUZ))
    # bracos grossos; o direito ergue a espada de duas maos, o esquerdo fecha o punho
    for lado in (-1, 1):
        cx = 8 + lado * 7.4
        rot = ('x', 45.0, (cx, 9.0, 8.0)) if susto else None
        cs.append(C('braco', cx - 1.6, cx + 1.6, 0.4, 9.0, 6.2, 9.8, 'tecido', rot))
        cs.append(C('racha_braco', cx - 0.25, cx + 0.25, 1.6, 6.4, 5.95, 6.25, 'pupila', rot, so=('north',), **LUZ))
        cs.append(C('manopla', cx - 2.0, cx + 2.0, -5.0, 0.8, 5.8, 10.2, 'tecido2', rot))
        cs.append(C('manopla_aro', cx - 2.15, cx + 2.15, -0.2, 0.9, 5.65, 10.35, 'ouro', rot, so=('north', 'south', 'east', 'west')))
        cs.append(C('garra', cx - 1.6, cx + 1.6, -6.6, -4.8, 5.9, 7.0, 'ouro', rot))
        if lado == 1:
            # espada colossal apontada para cima: cabo, pomo, guarda em asa, lamina larga com a alma de magma
            cs.append(C('cabo', cx - 0.5, cx + 0.5, -9.0, -1.0, 3.6, 4.6, 'corda', rot))
            cs.append(C('pomo', cx - 0.9, cx + 0.9, -10.6, -8.8, 3.2, 5.0, 'ouro', rot))
            cs.append(C('guarda', cx - 3.4, cx + 3.4, -1.2, 0.4, 3.3, 4.9, 'ouro', rot))
            cs.append(C('guarda_ponta', cx - 4.2, cx - 3.2, -0.4, 2.0, 3.5, 4.7, 'ouro', rot))
            cs.append(C('guarda_ponta', cx + 3.2, cx + 4.2, -0.4, 2.0, 3.5, 4.7, 'ouro', rot))
            cs.append(C('lamina', cx - 1.9, cx + 1.9, 0.3, 21.0, 3.75, 4.45, 'pele', rot))
            cs.append(C('alma', cx - 0.45, cx + 0.45, 1.2, 19.6, 3.6, 4.6, 'pupila', rot, so=('north', 'south'), **LUZ))
            cs.append(C('ponta', cx - 1.1, cx + 1.1, 20.9, 23.2, 3.8, 4.4, 'pele', rot))
            cs.append(C('ponta2', cx - 0.45, cx + 0.45, 23.1, 24.8, 3.85, 4.35, 'pele', rot))
    # elmo-caveira com chifres, olhos e boca de magma, e a coroa partida flutuando em pedacos
    cs.append(C('pescoco', 6.2, 9.8, 11.0, 12.2, 6.2, 9.8, 'tecido2'))
    cs.append(C('elmo', 4.8, 11.2, 12.0, 18.4, 4.6, 11.4, 'tecido2'))
    cs.append(C('caveira', 5.2, 10.8, 13.4, 18.0, 4.1, 4.75, 'pano', so=('north', 'east', 'west', 'up', 'down')))
    for cx in (6.5, 9.5):
        cs.append(C('orbita', cx - 0.95, cx + 0.95, 15.5, 17.0, 3.95, 4.12, 'boca', so=('north',)))
        cs.append(C('olho_luz', cx - 0.6, cx + 0.6, 15.8, 16.7, 3.85, 3.96, 'pupila', so=('north',), **LUZ))
    cs.append(C('nariz', 7.6, 8.4, 14.4, 15.4, 3.95, 4.12, 'boca', so=('north',)))
    mand = ('x', -22.5, (8.0, 13.4, 5.0)) if susto else None
    cs.append(C('goela', 5.8, 10.2, 12.1, 13.5, 4.3, 4.7, 'pupila', so=('north',), **LUZ))
    A.dentes(cs, 5.6, 10.4, 12.9, 13.6, 3.9, 4.2, passo=0.7, larg=0.45)
    cs.append(C('queixo', 5.4, 10.6, 11.4, 12.6, 4.0, 5.6, 'pano', mand))
    A.dentes(cs, 5.8, 10.2, 12.5, 13.1, 3.95, 4.25, passo=0.7, larg=0.45, rot=mand, nome='dente_b')
    for lado in (-1, 1):
        cx = 8 + lado * 3.6
        cs.append(C('chifre_a', min(cx, cx + lado * 3.2), max(cx, cx + lado * 3.2), 15.6, 17.4, 7.0, 8.8, 'pano'))
        cs.append(C('chifre_b', cx + lado * 3.4 - 0.75, cx + lado * 3.4 + 0.75, 17.0, 20.6, 7.15, 8.65, 'pano'))
        cs.append(C('chifre_c', cx + lado * 3.4 - 0.45, cx + lado * 3.4 + 0.45, 20.4, 22.6, 7.45, 8.35, 'ouro'))
    cs.append(C('coroa', 4.6, 11.4, 18.2, 19.3, 4.4, 11.6, 'ouro'))
    for cx, alto in ((5.4, 2.2), (8.0, 3.2), (10.6, 0.8)):
        cs.append(C('coroa_ponta', cx - 0.6, cx + 0.6, 19.2, 19.2 + alto, 4.5, 5.7, 'ouro'))
    for cx, alto in ((5.4, 1.8), (10.6, 2.2)):
        cs.append(C('coroa_ponta', cx - 0.6, cx + 0.6, 19.2, 19.2 + alto, 10.3, 11.5, 'ouro'))
    cs.append(C('joia', 7.3, 8.7, 18.3, 19.2, 4.2, 4.45, 'pupila', so=('north',), **LUZ))
    # o pedaco que falta da coroa flutua ao lado, aceso
    cs.append(C('estilhaco', 12.4, 13.4, 21.4, 23.8, 4.8, 5.8, 'ouro', ('z', -22.5, (12.9, 22.6, 5.3))))
    cs.append(C('estilhaco_luz', 12.6, 13.2, 20.4, 21.2, 5.0, 5.6, 'pupila', **LUZ))
    return cs


H.CORPOS['colosso'] = colosso

if __name__ == '__main__':
    A.gerar(A.SAIDA, ['colosso'])
    os.replace(os.path.join(A.SAIDA, 'corona-animatronicos.json'), os.path.join(A.SAIDA, 'corona-colosso.json'))
    os.replace(os.path.join(A.SAIDA, 'previa_animatronicos.png'), os.path.join(A.SAIDA, 'previa_colosso.png'))
