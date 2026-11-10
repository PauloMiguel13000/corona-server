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

A.PALETAS['colosso'] = dict(pupila=(0.35, 0.95, 1.0))
H.MATS['colosso'] = {
    'tecido': ((0.16, 0.17, 0.21), 0.75, 50, 0.85), 'tecido2': ((0.08, 0.085, 0.11), 0.8, 60, 0.9),
    'ouro': ((0.95, 0.74, 0.20), 0.9, 60, 0.9), 'pano': ((0.20, 0.21, 0.25), 0.7, 45, 0.8),
    'pele': ((0.72, 0.76, 0.80), 0.9, 70, 0.9), 'corda': ((0.50, 0.05, 0.07), 0.1, 8, 0.0),
    'sapato': ((0.05, 0.05, 0.06), 0.7, 50, 0.6),
}


def colosso(susto):
    cs = []
    for xa, xb in ((4.2, 7.4), (8.6, 11.8)):
        cs.append(C('perna', xa, xb, -13.6, -3.0, 6.2, 9.8, 'tecido'))
        cs.append(C('joelho', xa - 0.3, xb + 0.3, -9.4, -7.2, 5.6, 7.2, 'ouro'))
        cs.append(C('bota', xa - 0.4, xb + 0.4, -16.0, -13.4, 4.6, 10.2, 'sapato'))
    cs.append(C('saiote', 3.6, 12.4, -5.6, -2.6, 5.6, 10.4, 'tecido2'))
    cs.append(C('cinto', 3.4, 12.6, -3.2, -1.8, 5.4, 10.6, 'ouro'))
    cs.append(C('tronco', 3.2, 12.8, -2.0, 6.0, 5.4, 10.6, 'tecido'))
    cs.append(C('peito', 2.6, 13.4, 5.6, 10.4, 5.0, 11.0, 'tecido'))
    cs.append(C('nucleo_aro', 6.4, 9.6, 5.8, 9.0, 4.7, 5.1, 'ouro', so=('north',)))
    cs.append(C('nucleo', 7.0, 9.0, 6.4, 8.4, 4.5, 4.75, 'pupila', emite=15, so=('north',)))
    cs.append(C('veia', 7.7, 8.3, 0.0, 5.8, 5.15, 5.42, 'pupila', emite=12, so=('north',)))
    # capa vermelha rasgada
    cs.append(C('capa', 3.4, 7.8, -12.0, 10.0, 10.6, 11.4, 'corda', ('x', -22.5, (8.0, 10.0, 10.8))))
    cs.append(C('capa', 8.2, 12.6, -9.0, 10.0, 10.6, 11.4, 'corda', ('x', -22.5, (8.0, 10.0, 10.8))))
    # ombreiras com espinhos
    for lado in (-1, 1):
        cx = 8 + lado * 6.6
        cs.append(C('ombreira', cx - 2.2, cx + 2.2, 8.4, 12.0, 5.2, 10.8, 'tecido2'))
        cs.append(C('ombreira_borda', cx - 2.3, cx + 2.3, 8.2, 8.9, 5.1, 10.9, 'ouro', so=('north', 'south', 'east', 'west')))
        cs.append(C('espinho', cx - 0.5 + lado * 0.9, cx + 0.5 + lado * 0.9, 11.8, 14.6, 7.5, 8.5, 'ouro'))
    # bracos: o esquerdo solto, o direito segura a espada
    for lado in (-1, 1):
        cx = 8 + lado * 6.4
        if susto:
            rot = ('x', 45.0, (cx, 8.6, 8.0))
        else:
            rot = None
        cs.append(C('braco', cx - 1.3, cx + 1.3, -1.0, 8.6, 6.7, 9.3, 'tecido', rot))
        cs.append(C('manopla', cx - 1.6, cx + 1.6, -4.6, -0.8, 6.4, 9.6, 'tecido2', rot))
        cs.append(C('manopla_aro', cx - 1.7, cx + 1.7, -1.4, -0.7, 6.3, 9.7, 'ouro', rot, so=('north', 'south', 'east', 'west')))
        if lado == 1:
            # a espada enorme: cabo, guarda e lamina com o fio que brilha
            cs.append(C('cabo', cx - 0.4, cx + 0.4, -4.4, -2.2, 3.0, 6.6, 'corda', rot))
            cs.append(C('guarda', cx - 2.2, cx + 2.2, -4.2, -2.4, 2.2, 3.1, 'ouro', rot))
            cs.append(C('lamina', cx - 1.1, cx + 1.1, -3.9, -2.7, -11.5, 2.3, 'pele', rot))
            cs.append(C('fio', cx - 0.25, cx + 0.25, -2.72, -2.5, -11.0, 2.0, 'pupila', rot, emite=12, so=('up',)))
            cs.append(C('ponta', cx - 0.6, cx + 0.6, -3.7, -2.9, -13.2, -11.4, 'pele', rot))
    # elmo fechado com a fenda dos olhos acesa e a coroa quebrada (falta uma ponta)
    cs.append(C('pescoco', 6.4, 9.6, 10.2, 11.2, 6.6, 9.4, 'tecido2'))
    cs.append(C('elmo', 5.0, 11.0, 11.0, 17.2, 5.0, 11.0, 'tecido2'))
    cs.append(C('viseira', 5.4, 10.6, 12.6, 16.0, 4.6, 5.1, 'pano', so=('north', 'east', 'west', 'up', 'down')))
    cs.append(C('fenda', 5.8, 10.2, 14.3, 15.1, 4.45, 4.62, 'boca', so=('north',)))
    for cx in (6.7, 9.3):
        cs.append(C('olho_luz', cx - 0.7, cx + 0.7, 14.4, 15.0, 4.35, 4.46, 'pupila', emite=15, so=('north',)))
    cs.append(C('crista', 7.5, 8.5, 11.4, 13.4, 4.4, 4.7, 'ouro', so=('north',)))
    cs.append(C('coroa', 4.8, 11.2, 17.0, 18.2, 4.8, 11.2, 'ouro'))
    for cx, alto in ((5.6, 2.6), (8.0, 3.4), (10.4, 1.0)):
        cs.append(C('coroa_ponta', cx - 0.6, cx + 0.6, 18.1, 18.1 + alto, 4.9, 6.1, 'ouro'))
    for cx, alto in ((5.6, 2.2), (10.4, 2.6)):
        cs.append(C('coroa_ponta', cx - 0.6, cx + 0.6, 18.1, 18.1 + alto, 9.9, 11.1, 'ouro'))
    cs.append(C('joia', 7.4, 8.6, 17.2, 18.0, 4.6, 4.85, 'pupila', emite=14, so=('north',)))
    return cs


H.CORPOS['colosso'] = colosso

if __name__ == '__main__':
    A.gerar(A.SAIDA, ['colosso'])
    os.replace(os.path.join(A.SAIDA, 'corona-animatronicos.json'), os.path.join(A.SAIDA, 'corona-colosso.json'))
    os.replace(os.path.join(A.SAIDA, 'previa_animatronicos.png'), os.path.join(A.SAIDA, 'previa_colosso.png'))
