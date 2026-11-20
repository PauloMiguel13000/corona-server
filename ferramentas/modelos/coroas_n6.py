"""Coroas dos 10 chefes do nivel 6 no estilo do Arsenal Ancestral (20/11): em vez dos 14 moldes antigos, cada chefe
usa a peca 3D propria do elmo do nivel 6 (coroa de brasas, aureola, para-raios...) sobre um aro de metal escuro com
frisos dourados e gemas acesas. Mesmo caminho do item (corona:item/chefes/<tema>): o plugin nao muda.
Uso: python3 -I coroas_n6.py [ids...]
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import coroas_chefes as CC  # noqa: E402
import espadas_n6 as EN  # noqa: E402
import armaduras_n6 as N  # noqa: E402
import kit  # noqa: E402
from kit import Cubo  # noqa: E402

ESC = 1.3
PELE = {'placa': 'guarda', 'acab': 'friso', 'brilho': 'veio'}


def cubos(id_):
    cs = []
    CC.anel(cs, 0, 1.8, 5.6, 1.3, 'guarda')
    CC.anel(cs, 1.8, 2.3, 5.8, 0.5, 'friso')
    for k, (dx, dz) in enumerate(((0, -5.8), (5.8, 0), (0, 5.8), (-5.8, 0))):
        cs.append(Cubo(f'gema{k}', (8 + dx - 0.7, 0.5, 8 + dz - 0.7), (8 + dx + 0.7, 1.7, 8 + dz + 0.7), 'gema', emite=15))
    for k, c in enumerate(N.pecas_n6(N.N6[id_][1], id_)):
        (x0, y0, z0), (x1, y1, z1) = c['a'], c['b']
        if y1 <= 33.0:
            continue   # pecas que ficam do lado da cabeca (gola, guelras...) nao entram na coroa flutuante
        y0 = max(y0, 33.0)
        a = (8 + x0 * ESC, 2.0 + (y0 - 33.0) * ESC, 8 + z0 * ESC)
        b = (8 + x1 * ESC, 2.0 + (y1 - 33.0) * ESC, 8 + z1 * ESC)
        cs.append(Cubo(f'p{k}', a, b, PELE.get(c['mat'], 'guarda'), emite=15 if c['mat'] == 'brilho' else 0))
    if id_ == 'RUBRA':   # leque de pontas de morcego atras (a gola alta do elmo nao entra na coroa)
        for k, (x, h) in enumerate(((-5.0, 4.0), (-2.6, 6.0), (0.0, 7.5), (2.6, 6.0), (5.0, 4.0))):
            cs.append(Cubo(f'leque{k}', (8 + x - 0.8, 2.0, 12.6), (8 + x + 0.8, 2.0 + h, 13.6), 'guarda'))
            cs.append(Cubo(f'lequep{k}', (8 + x - 0.35, 2.0 + h, 12.8), (8 + x + 0.35, 3.2 + h, 13.4), 'veio', emite=15))
    return cs


def gerar(id_):
    tema = id_.lower()
    cs = cubos(id_)
    largura = 128
    altura = kit.arrumar_uv(cs, largura, 4)
    H = 16
    while H < altura:
        H *= 2
    atlas = kit.pintar_atlas(cs, largura, H, EN.pintor(id_), 4)
    J = CC.JAVA
    os.makedirs(os.path.join(J, 'textures', 'item', 'chefes'), exist_ok=True)
    kit.salvar_png_leve(atlas, os.path.join(J, 'textures', 'item', 'chefes', f'{tema}.png'))
    kit.salvar_json(os.path.join(J, 'models', 'item', 'chefes', f'{tema}.json'), kit.modelo_java(cs, f'corona:item/chefes/{tema}', largura, H, display=CC.DISPLAY))
    kit.salvar_json(os.path.join(J, 'items', 'item', 'chefes', f'{tema}.json'), kit.item_java(f'corona:item/chefes/{tema}'))
    pv = kit.previa(cs, atlas, 35, 20, escala=8)
    pv.save(os.path.join(CC.PREV, f'{tema}.png'))
    return pv


if __name__ == '__main__':
    so = [s.upper() for s in sys.argv[1:]] or list(N.N6)
    for id_ in so:
        gerar(id_)
    print(len(so), 'coroas do nivel 6 (Arsenal Ancestral)')
