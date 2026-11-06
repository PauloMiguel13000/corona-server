"""Pinturas HD (layout de skin 64x64) do Vigia, da Chorona e do Faminto para o Bedrock (manequim de reserva),
pintadas com os mesmos pintores dos modelos 3D. Saida: 128x128 para a extensao do Geyser + previa."""
import os, sys
import numpy as np
from PIL import Image
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'modelos'))
import base  # noqa: E402
import naochore as nc  # noqa: E402

FACE = {'front': 'south', 'back': 'north', 'left': 'east', 'right': 'west', 'top': 'up', 'bottom': 'down'}
OUT = sys.argv[1] if len(sys.argv) > 1 else '/tmp/claude-0/skins2'
os.makedirs(OUT, exist_ok=True)


def adaptar(P, camada, peles, nome_cabeca='cabeca'):
    """P do base.py (skin) -> P do pintor 3D. peles: parte -> pele (camada base), 'over' usa cabelo na cabeca."""
    parte, face = P['parte'], P['face']
    f = FACE[face]
    # coordenadas do modelo: a skin tem a cabeca em y 24..32; os modelos tem cabeca ~ y 21..30 (vigia): mesma escala
    if camada == 'over':
        if parte != 'head':
            return None
        pele = 'cabelo'
    else:
        pele = peles.get(parte, 'pele')
    Q = dict(x=P['x'], y=P['y'], z=P['z'], s=P['s'], t=P['t'], n=P['n'], face=f, pele=pele, nome=nome_cabeca if parte == 'head' else parte,
             grupo=parte, w=P['w'], h=P['h'], d=P['d'], de=np.array([0, 0, 0]), ate=np.array([8, 8, 8]))
    return Q


def vigia(P, camada):
    Q = adaptar(P, camada, {'head': 'rosto', 'body': 'casaco', 'rarm': 'braco', 'larm': 'braco', 'rleg': 'calca', 'lleg': 'calca'})
    if Q is None:
        return np.zeros(P['s'].shape + (3,)), 0
    if Q['pele'] == 'cabelo':
        Q['y'] = Q['y'] - 3   # a cabeca da skin (24..32) fica onde a do modelo (21..30)
        c, a = nc.cabelo_longo(Q, 21, '#050408', y_fim=13, dens=0.8, frente=(0.16, 0.12), topo_y=28.6, cheio_y=22)
        return c, a
    Q['y'] = Q['y'] - 3 if Q['grupo'] == 'head' else Q['y']
    return nc.vigia_pintor(Q)


def chorona(P, camada):
    Q = adaptar(P, camada, {'head': 'rosto', 'body': 'vestido', 'rarm': 'manga', 'larm': 'manga', 'rleg': 'vestido', 'lleg': 'vestido'})
    if Q is None:
        return np.zeros(P['s'].shape + (3,)), 0
    if Q['pele'] == 'cabelo':
        Q['y'] = Q['y'] - 10
        return nc.cabelo_longo(Q, 51, '#040407', y_fim=3, dens=0.95, frente=(0.2, 0.42), topo_y=20.5, cheio_y=14)
    if Q['grupo'] == 'head':
        Q['y'] = Q['y'] - 10
    # maos: parte de baixo dos bracos (mistura manga em cima e mao embaixo)
    if Q['grupo'] in ('rarm', 'larm') and Q['face'] != 'up':
        c1, a1 = nc.chorona_pintor(dict(Q, pele='manga'))
        c2, a2 = nc.chorona_pintor(dict(Q, pele='mao'))
        m = (Q['y'] < 15.0)
        return np.where(m[..., None], c2, c1), 1
    return nc.chorona_pintor(Q)


def faminto(P, camada):
    Q = adaptar(P, camada, {'head': 'rosto', 'body': 'costela', 'rarm': 'couro', 'larm': 'couro', 'rleg': 'couro', 'lleg': 'couro'})
    if Q is None:
        return np.zeros(P['s'].shape + (3,)), 0
    if Q['pele'] == 'cabelo':
        return np.zeros(P['s'].shape + (3,)), 0
    if Q['grupo'] == 'head':
        # a cabeca do modelo e um focinho (z 11..21): leva as coordenadas para la
        Q['z'] = Q['z'] + 15
        Q['y'] = Q['y'] - 14
    if Q['grupo'] == 'body':
        # costelas do modelo sao pelos lados (east/west); na skin, a frente do peito mostra as costelas
        if Q['face'] == 'south':
            Q['face'] = 'east'
            Q['z'] = Q['y'] - 16 - 5   # mapeia a altura do peito no eixo z do modelo (costelas em z -7..6)
    return nc.faminto_pintor(Q)


if __name__ == '__main__':
    for nome, f in (('vigia', vigia), ('chorona', chorona), ('faminto', faminto)):
        img = base.montar(f, nome)
        Image.fromarray(base.reduzir(img, 2, corte=True), 'RGBA').save(f'{OUT}/{nome}.png', optimize=True)
        base.previa(img).save(f'{OUT}/prev_skin_{nome}.png')
        print('ok', nome)
