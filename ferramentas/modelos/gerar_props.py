"""Gera os props 3D das habilidades (Java): modelos por quadro, itens e texturas em item/props/.
Uso: python3 -I gerar_props.py [nomes...]"""
import os, sys
import numpy as np
from PIL import Image
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import kit  # noqa: E402
from props import PROPS  # noqa: E402

AQUI = os.path.dirname(os.path.abspath(__file__))
JAVA = os.path.normpath(os.path.join(AQUI, '..', 'coronapack', 'pacote', 'java', 'assets', 'corona'))
PREV = '/tmp/claude-0/modelos/props'
ESCALA_TEX = 3
os.makedirs(PREV, exist_ok=True)


def pot2(n):
    p = 16
    while p < n:
        p *= 2
    return p


def gerar(nome, M):
    base = M['cubos']()
    quadros = M['quadros'](base)
    largura = M.get('tex', 128)
    esc = M.get('esc', ESCALA_TEX)
    chave = lambda c: (c.nome, c.pele, c.so)
    reps = {}
    for lista in quadros.values():
        for c in lista:
            reps.setdefault(chave(c), c)
    reps_l = list(reps.values())
    altura = kit.arrumar_uv(reps_l, largura, esc)
    H = pot2(altura)
    W = pot2(largura)
    for lista in quadros.values():
        for c in lista:
            c.uv = dict(reps[chave(c)].uv)
    atlas = kit.pintar_atlas(reps_l, W, H, M['pintor'], esc)
    tex_dir = os.path.join(JAVA, 'textures', 'item', 'props')
    os.makedirs(tex_dir, exist_ok=True)
    Image.fromarray(atlas, 'RGBA').save(os.path.join(tex_dir, f'{nome}.png'), optimize=True)
    tex_key = f'corona:item/props/{nome}'
    for q, lista in quadros.items():
        kit.salvar_json(os.path.join(JAVA, 'models', 'props', f'{nome}_{q}.json'), kit.modelo_java(lista, tex_key, W, H))
        kit.salvar_json(os.path.join(JAVA, 'items', 'props', f'{nome}_{q}.json'), kit.item_java(f'corona:props/{nome}_{q}'))
    qs = list(quadros)
    ps = [kit.previa(quadros[qs[0]], atlas, 30, 15, escala=8), kit.previa(quadros[qs[0]], atlas, 90, 0, escala=8)]
    if len(qs) > 1:
        ps.append(kit.previa(quadros[qs[-1]], atlas, 30, 15, escala=8))
    kit.folha(ps).save(os.path.join(PREV, f'prev_{nome}.png'))
    print('ok', nome, 'atlas', W, 'x', H, 'cubos', len(base), 'quadros', qs)


if __name__ == '__main__':
    so = sys.argv[1:]
    for nome, M in PROPS.items():
        if so and nome not in so:
            continue
        gerar(nome, M)
