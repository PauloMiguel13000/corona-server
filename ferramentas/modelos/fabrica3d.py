"""Brinquedos 3D ORIGINAIS da Fabrica da Alegria: Tutu (tamandua de pelucia laranja, focinho comprido, colete preto e
rabo peludo) e Dona Bolota (porquinha rosa de laco roxo). Mesmo corpo e mesma pintura dos animatronicos do Turno
(animatronicos3d.py): 2 quadros (f0 parado, f1 susto). Itens: corona:props/anim_tutu_f0/f1 e anim_bolota_f0/f1.

uso: python3 -I fabrica3d.py <saida>
"""
import os
import sys

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)
import animatronicos3d as A  # noqa: E402

C = A.C

A.PALETAS['tutu'] = dict(pelo=(0.86, 0.47, 0.14), pelo2=(0.62, 0.30, 0.08), barriga=(0.97, 0.86, 0.66), nariz=(0.10, 0.07, 0.06),
                         pupila=(1.0, 0.35, 0.20), extra=(0.09, 0.07, 0.06))
A.PALETAS['bolota'] = dict(pelo=(0.96, 0.62, 0.74), pelo2=(0.86, 0.44, 0.60), barriga=(0.99, 0.84, 0.88), nariz=(0.80, 0.36, 0.50),
                           pupila=(0.85, 0.55, 1.0), extra=(0.45, 0.22, 0.70))


def cabeca_tutu(cs, susto):
    # cabeca pequena e o focinho comprido de tamandua, que desce um pouco; no susto a boca abre e a lingua sai
    cs.append(C('cabeca', 4.6, 11.4, 11.6, 18.2, 5.4, 12.0, 'pelo'))
    cs.append(C('testa', 5.4, 10.6, 18.1, 18.8, 6.0, 11.4, 'pelo'))
    cs.append(C('focinho_a', 5.8, 10.2, 13.0, 16.6, 1.6, 5.5, 'pelo'))
    cs.append(C('focinho_b', 6.5, 9.5, 12.6, 15.2, -2.6, 1.7, 'pelo', ('x', 22.5, (8.0, 14.0, 1.7))))
    cs.append(C('focinho_c', 7.0, 9.0, 11.6, 13.6, -6.0, -2.4, 'barriga', ('x', 45.0, (8.0, 13.4, -2.0))))
    cs.append(C('nariz', 7.2, 8.8, 10.2, 11.4, -7.4, -6.6, 'nariz', ('x', 45.0, (8.0, 13.4, -2.0))))
    mand = ('x', -22.5, (8.0, 12.9, 1.6)) if susto else None
    cs.append(C('boca', 6.9, 9.1, 12.2, 13.0, -1.8, 5.0, 'boca'))
    cs.append(C('mandibula', 6.8, 9.2, 11.6, 12.5, -2.2, 5.0, 'barriga', mand))
    A.dentes(cs, 6.9, 9.1, 12.4, 12.9, -1.6, -1.2, passo=0.75, larg=0.4, rot=mand, nome='dente_b')
    if susto:
        cs.append(C('lingua', 7.5, 8.5, 6.2, 12.2, -3.4, -2.8, 'lingua', ('x', -22.5, (8.0, 12.2, -3.0))))
    else:
        cs.append(C('lingua', 7.6, 8.4, 11.2, 11.9, -4.6, -2.2, 'lingua', ('x', 45.0, (8.0, 13.4, -2.0))))
    A.olhos(cs, (5.6, 10.4), 15.6, 17.2, 5.25, 1.6, extra_anel=True)
    cs.append(C('orelha', 4.4, 5.8, 17.8, 19.6, 9.0, 10.2, 'pelo2'))
    cs.append(C('orelha', 10.2, 11.6, 17.8, 19.6, 9.0, 10.2, 'pelo2'))
    # colete preto (a faixa do tamandua-bandeira) e o rabo peludo
    cs.append(C('colete', 3.5, 12.5, 4.6, 9.9, 4.7, 11.3, 'extra', so=('north', 'south', 'east', 'west')))
    cs.append(C('colete_v', 6.6, 9.4, 1.8, 4.7, 4.4, 4.7, 'extra', so=('north',)))
    cs.append(C('rabo_a', 5.6, 10.4, -1.0, 6.0, 11.0, 14.6, 'pelo2', ('x', -22.5, (8.0, 0.0, 11.2))))
    cs.append(C('rabo_b', 5.0, 11.0, 3.0, 11.0, 14.0, 18.6, 'pelo', ('x', -22.5, (8.0, 0.0, 11.2))))
    cs.append(C('rabo_c', 6.0, 10.0, 10.6, 14.0, 15.0, 18.0, 'pelo2', ('x', -22.5, (8.0, 0.0, 11.2))))
    # garras compridas (o que agarra dentro do duto)
    for lado in (-1, 1):
        cx = 8 + lado * 6.05
        rot = ('x', 45.0, (cx, 9.0, 8.0)) if susto else None
        for dx in (-0.7, 0.0, 0.7):
            cs.append(C('garra', cx + dx - 0.22, cx + dx + 0.22, -10.6, -8.0, 7.5, 8.1, 'dente', rot))


def cabeca_bolota(cs, susto):
    mand = ('x', -45.0, (8.0, 13.2, 5.0)) if susto else None
    cs.append(C('cabeca', 3.4, 12.6, 11.6, 19.6, 4.8, 12.2, 'pelo'))
    cs.append(C('bochecha', 2.9, 3.6, 12.2, 15.8, 5.6, 10.0, 'pelo2'))
    cs.append(C('bochecha', 12.4, 13.1, 12.2, 15.8, 5.6, 10.0, 'pelo2'))
    cs.append(C('focinho', 5.6, 10.4, 13.4, 16.4, 3.2, 4.9, 'pelo2'))
    cs.append(C('narina', 6.4, 7.3, 14.3, 15.6, 3.05, 3.25, 'nariz', so=('north',)))
    cs.append(C('narina', 8.7, 9.6, 14.3, 15.6, 3.05, 3.25, 'nariz', so=('north',)))
    cs.append(C('boca', 5.6, 10.4, 12.2, 13.5, 3.6, 5.0, 'boca'))
    A.dentes(cs, 5.6, 10.4, 12.9, 13.45, 3.3, 3.7, passo=0.8, larg=0.45)
    cs.append(C('mandibula', 5.4, 10.6, 11.7, 13.2, 3.4, 5.0, 'barriga', mand))
    A.dentes(cs, 5.8, 10.2, 13.15, 13.65, 3.6, 4.0, passo=0.8, larg=0.45, rot=mand, nome='dente_b')
    A.olhos(cs, (5.4, 10.6), 16.8, 18.6, 4.65, 1.9)
    cs.append(C('orelha', 3.4, 6.0, 19.2, 21.6, 7.0, 8.0, 'pelo2', ('z', 22.5, (4.7, 19.4, 7.5))))
    cs.append(C('orelha', 10.0, 12.6, 19.2, 21.6, 7.0, 8.0, 'pelo2', ('z', -22.5, (11.3, 19.4, 7.5))))
    # laco roxo na cabeca e avental
    cs.append(C('laco', 5.2, 7.6, 19.5, 21.5, 8.6, 9.6, 'extra', ('z', 22.5, (7.6, 20.5, 9.1))))
    cs.append(C('laco', 8.4, 10.8, 19.5, 21.5, 8.6, 9.6, 'extra', ('z', -22.5, (8.4, 20.5, 9.1))))
    cs.append(C('laco_no', 7.4, 8.6, 19.8, 21.0, 8.4, 9.8, 'nariz'))
    cs.append(C('avental', 4.6, 11.4, -1.0, 7.4, 4.4, 4.75, 'extra', so=('north',)))
    cs.append(C('rabinho', 7.4, 8.6, 0.6, 1.8, 11.1, 12.6, 'pelo2'))


A.CABECAS['tutu'] = cabeca_tutu
A.CABECAS['bolota'] = cabeca_bolota
_reg = A.registrar


def registrar(pal):
    _reg(pal)
    A.TH.MAT['lingua'] = ((0.85, 0.25, 0.35), 0.5, 30, 0.0)


A.registrar = registrar

if __name__ == '__main__':
    A.gerar(A.SAIDA, ['tutu', 'bolota'])
    os.replace(os.path.join(A.SAIDA, 'corona-animatronicos.json'), os.path.join(A.SAIDA, 'corona-fabrica.json'))
    os.replace(os.path.join(A.SAIDA, 'previa_animatronicos.png'), os.path.join(A.SAIDA, 'previa_fabrica.png'))
