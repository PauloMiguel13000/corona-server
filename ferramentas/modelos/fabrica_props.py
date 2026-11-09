"""Props 3D do minigame Fabrica Alegria (originais): mao azul e mao vermelha da Luva de Gancho (mao aberta num punho
de metal com o cabo), plugue de energia e fita de video. Entram no mesmo pipeline dos props das habilidades
(gerar_props.gerar -> models/props + items/props + textures/item/props; o bedrock_v2.py converte para o celular).
Uso: python3 -I fabrica_props.py"""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'skins', 'monstros'))
from base import fbm, rampa, mix, C, ss  # noqa: E402
from kit import Cubo  # noqa: E402
import gerar_props  # noqa: E402

FAB = {}


def mao_cubos():
    # mao aberta apontando para +z: punho de metal (atras), palma, 4 dedos esticados, polegar, cabo saindo do punho
    cs = [Cubo('punho', (-4, -4, -11), (4, 4, -4), 'metal#'),
          Cubo('anel', (-4.6, -4.6, -6), (4.6, 4.6, -4.8), 'anel#'),
          Cubo('cabo', (-1.2, -1.2, -16), (1.2, 1.2, -11), 'cabo#'),
          Cubo('palma', (-4.5, -1.5, -4), (4.5, 1.5, 3), 'pele#')]
    for k in range(4):
        x0 = -4.5 + k * 2.3
        cs.append(Cubo(f'dedo{k}', (x0 + 0.15, -1.2, 3), (x0 + 2.1, 1.2, 8.5 - abs(k - 1.5) * 0.9), 'dedo#'))
    cs.append(Cubo('polegar', (-7, -1.2, -2.5), (-4.5, 1.2, 2.5), 'dedo#', rot=('y', -22.5, (-4.5, 0, 0))))
    return cs


def mao_pintor_de(cor, clara):
    def pintor(P):
        pele = P['pele'].rstrip('#')
        x, y, z, t = P['x'], P['y'], P['z'], P['t']
        n = fbm(x * 1.4, y * 1.4, z * 1.4, 3, 1, 611)
        if pele == 'metal':
            base = rampa(n, [(0, '#3a3f48'), (0.6, '#8a919c'), (1, '#c8ced6')])
            parafuso = ss(0.9, 0.97, fbm(x * 4, y * 4, z * 4, 1, 1, 612))
            return mix(base, C('#2a2d33'), parafuso * 0.8), 1
        if pele == 'anel':
            return rampa(n, [(0, '#c89a20'), (1, '#ffe07a')]), 1
        if pele == 'cabo':
            return rampa(n, [(0, '#151515'), (1, '#3a3a3a')]), 1
        base = rampa(n, [(0, cor), (0.7, clara), (1, '#ffffff')])
        if pele == 'dedo':
            # pontas mais claras (borracha gasta)
            base = mix(base, C('#ffffff'), ss(0.6, 1.0, t) * 0.25)
        # costuras escuras de luva
        cost = ss(0.92, 0.98, fbm(x * 3, y * 3, z * 3, 2, 1, 613))
        return mix(base, C('#101820'), cost * 0.5), 1
    return pintor


def plugue_cubos():
    return [Cubo('corpo', (-4, -3, -5), (4, 3, 3), 'plug#'),
            Cubo('faixa', (-4.3, -3.3, -1), (4.3, 3.3, 0.2), 'faixa#'),
            Cubo('pino1', (-2.5, -0.6, 3), (-1.3, 0.6, 7.5), 'pino#'),
            Cubo('pino2', (1.3, -0.6, 3), (2.5, 0.6, 7.5), 'pino#'),
            Cubo('cabo', (-1.4, -1.4, -11), (1.4, 1.4, -5), 'cabo#')]


def plugue_pintor(P):
    pele = P['pele'].rstrip('#')
    x, y, z = P['x'], P['y'], P['z']
    n = fbm(x * 1.5, y * 1.5, z * 1.5, 3, 1, 621)
    if pele == 'plug':
        return rampa(n, [(0, '#c8b020'), (0.7, '#ffd83a'), (1, '#fff3a0')]), 1
    if pele == 'faixa':
        return rampa(n, [(0, '#101010'), (1, '#2a2a2a')]), 1
    if pele == 'pino':
        return rampa(n, [(0, '#8a6a20'), (1, '#f0d070')]), 1
    return rampa(n, [(0, '#151515'), (1, '#3a3a3a')]), 1


def fita_cubos():
    return [Cubo('caixa', (-7, -1.5, -4), (7, 1.5, 4), 'fita#'),
            Cubo('etiqueta', (-5, 1.5, -2.5), (5, 1.6, 2.5), 'etq#'),
            Cubo('janela', (-3, 1.5, -1.2), (3, 1.62, 1.2), 'jan#')]


def fita_pintor(P):
    pele = P['pele'].rstrip('#')
    x, y, z = P['x'], P['y'], P['z']
    n = fbm(x * 2, y * 2, z * 2, 2, 1, 631)
    if pele == 'etq':
        base = rampa(n, [(0, '#e8e0c8'), (1, '#fffaf0')])
        listra = ss(0.45, 0.5, np.abs(np.sin(z * 2.2)))
        return mix(base, C('#d04020'), listra * 0.7), 1
    if pele == 'jan':
        return rampa(n, [(0, '#202020'), (1, '#5a4030')]), 1
    return rampa(n, [(0, '#0e0e10'), (1, '#2a2a30')]), 1


FAB['fab_mao_azul'] = dict(cubos=mao_cubos, quadros=lambda cs: {'f0': cs}, pintor=mao_pintor_de('#1f5fd8', '#5aa0ff'), tex=128, esc=3)
FAB['fab_mao_vermelha'] = dict(cubos=mao_cubos, quadros=lambda cs: {'f0': cs}, pintor=mao_pintor_de('#c81e1e', '#ff6a5a'), tex=128, esc=3)
FAB['fab_plugue'] = dict(cubos=plugue_cubos, quadros=lambda cs: {'f0': cs}, pintor=plugue_pintor, tex=64, esc=3)
FAB['fab_fita'] = dict(cubos=fita_cubos, quadros=lambda cs: {'f0': cs}, pintor=fita_pintor, tex=64, esc=3)

if __name__ == '__main__':
    for nome, M in FAB.items():
        gerar_props.gerar(nome, M)
