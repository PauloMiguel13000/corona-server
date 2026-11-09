"""Elmos 3D das armaduras tematicas (Java + Bedrock).

O casco continua sendo a textura do elmo (camada humanoid, 64x32 * 4), e cada estilo ganha pecas em volume
(chifres, cristas, coroa, asas, orelhas, abas de chapeu, antenas...) feitas de cubos alinhados (estilo voxel,
sem rotacao: fica igual no Java e no Bedrock).

Java : o elmo fica SEM asset_id no equippable -> o jogo desenha o modelo do item na cabeca (contexto "head").
       items/item/armaduras/<tema>_elmo.json escolhe pelo display_context: "head" -> modelo 3D; resto -> icone.
Bedrock: attachable do elmo usa geometry.corona.elmo3d.<tema> (osso "head" + casco inflado + pecas).
As pecas usam um atlas de "amostras" de material pintado na area livre da textura (x 32..64, y 0..16 em px 64x32).

Coordenadas das pecas: espaco da cabeca do Bedrock (px): cabeca x -4..4, y 24..32, z -4..4, frente = -z,
lado direito do jogador = -x. O casco inflado ocupa x/z -5..5 e y 23..33.

uso: python3 -I elmos3d.py <saida_dir> [TEMA ...]
     escreve <saida>/java/assets/corona/... e <saida>/bedrock/... (so os arquivos do elmo 3D).
"""
import json
import os
import sys

import numpy as np
from PIL import Image

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)
sys.path.insert(0, os.path.join(AQUI, '..', 'skins', 'monstros'))
import armaduras_tematicas as A  # noqa: E402
import armaduras_v2 as V2  # noqa: E402
import armaduras_v3 as V3  # noqa: E402
from base import C, mix  # noqa: E402

K = A.K
MATERIAIS = ('leather', 'chainmail', 'iron', 'golden', 'diamond', 'netherite')

# ------------------------------------------------------------------ atlas de amostras (4x4 px cada)
AMOSTRAS = ['placa', 'acab', 'osso', 'osso_ponta', 'pano', 'pano2', 'couro', 'pelo',
            'brilho', 'escuro', 'pena', 'vidro', 'palha', 'chapeu', 'cogumelo', 'metal2', 'pluma']
TAM = 4


def pos_amostra(nome):
    i = AMOSTRAS.index(nome)
    return 32 + TAM * (i % 8), TAM * (i // 8)


def pintar_amostras(img, id_, t):
    """Pinta as amostras de material do tema na area livre (y 0..16, x 32..64) da textura do elmo."""
    rec = A.R[id_]
    sem = (sum(ord(ch) for ch in id_) * 7 + 1) % 997
    tipos = {'placa': V3.PLACA, 'acab': V3.ACAB, 'osso': V3.OSSO, 'osso_ponta': V3.OSSO, 'pano': V3.PANO,
             'pano2': V3.PANO2, 'couro': V3.COURO, 'pelo': V3.PELO, 'brilho': V3.BRILHO, 'escuro': V3.ESCURO,
             'metal2': V3.METAL2}
    tema, ac, bri, pano, pano2 = V3.cores_do_tema(rec, t)
    for nome in AMOSTRAS:
        x0, y0 = pos_amostra(nome)
        f = V3.Face('head', 'south', 1, x0, y0, x0 + TAM, y0 + TAM)
        tp = tipos.get(nome, V3.PLACA)
        f.ret(tp)
        c, a, _, _ = V3.colorir(f, rec, t, sem, id_)
        S, T = f.S, f.T
        if nome == 'osso_ponta':
            c = V2.clr(c, 0.25)
        elif nome == 'pena':
            base = np.ones(S.shape + (3,)) * np.asarray(V2.clr(mix(np.asarray(ac, float), np.ones(3), 0.6), 0.1))
            haste = np.abs(S - 0.5) < 0.09
            barbas = ((S * 6 + T * 3) % 1) < 0.5
            c = np.where(barbas[..., None], base * 0.9, base)
            c = np.where(haste[..., None], base * 0.7, c)
        elif nome == 'vidro':
            g = np.asarray(mix(np.asarray(tema, float), C('#05070c'), 0.75))
            c = g * (0.7 + 0.6 * T)[..., None] * np.ones(S.shape + (3,))
            c = np.where((np.abs(S + T * 0.7 - 0.55) < 0.1)[..., None], V2.clr(c, 0.35), c)
        elif nome == 'palha':
            c = np.ones(S.shape + (3,)) * C('#d9b25a')
            c = np.where((((f.X + f.Y) % 4) < 1.2)[..., None], C('#a9822e') * np.ones_like(c), c)
        elif nome == 'pluma':
            base = np.ones(S.shape + (3,)) * C('#b3202a')
            fios = ((f.X * 0.9 + f.Y * 0.35) % 3) < 1.1
            c = np.where(fios[..., None], base * 0.78, base) * (0.85 + 0.3 * (1 - T))[..., None]
        elif nome == 'chapeu':
            c = np.ones(S.shape + (3,)) * C('#2a231d') * (0.85 + 0.3 * T)[..., None]
        elif nome == 'cogumelo':
            pinta = np.hypot((S - 0.35) * 4, (T - 0.4) * 4) < 0.9
            c = np.where(pinta[..., None], C('#f4ecd8') * np.ones_like(c), c)
        # chanfro: borda clara em cima/esquerda, escura embaixo/direita
        c = np.where(((f.T < 0.07) | (f.S < 0.07))[..., None], V2.clr(c, 0.18), c)
        c = np.where(((f.T > 0.93) | (f.S > 0.93))[..., None], V2.esc(c, 0.7), c)
        img[y0 * K:(y0 + TAM) * K, x0 * K:(x0 + TAM) * K, :3] = (np.clip(c, 0, 1) * 255 + 0.5).astype(np.uint8)
        img[y0 * K:(y0 + TAM) * K, x0 * K:(x0 + TAM) * K, 3] = 255
    return img


# ------------------------------------------------------------------ pecas por estilo
def cubo(x0, x1, y0, y1, z0, z1, mat):
    return {'a': (min(x0, x1), min(y0, y1), min(z0, z1)), 'b': (max(x0, x1), max(y0, y1), max(z0, z1)), 'mat': mat}


def par(lista):
    """Duplica espelhando em x (os dois lados)."""
    out = []
    for c in lista:
        out.append(c)
        (x0, y0, z0), (x1, y1, z1) = c['a'], c['b']
        out.append(cubo(-x1, -x0, y0, y1, z0, z1, c['mat']))
    return out


def pecas(estilo, id_):
    P = []
    if estilo == 'viseira':
        P.append(cubo(-0.4, 0.4, 33.0, 34.2, -4.6, 4.6, 'placa'))                      # crista baixa
        P += par([cubo(5.0, 5.5, 27.5, 28.5, -1.5, -0.5, 'acab')])                      # pinos da viseira
    elif estilo == 'coroa':
        for c in (-3.5, 0.0, 3.5):
            for lado in (-1, 1):
                z = 5.0 * lado
                P.append(cubo(c - 1.0, c + 1.0, 33.0, 34.5, z - 0.5, z + 0.5, 'acab'))
                P.append(cubo(c - 0.5, c + 0.5, 34.5, 35.8, z - 0.25, z + 0.25, 'acab'))
                P.append(cubo(5.0 * lado - 0.5, 5.0 * lado + 0.5, 33.0, 34.5, c - 1.0, c + 1.0, 'acab'))
                P.append(cubo(5.0 * lado - 0.25, 5.0 * lado + 0.25, 34.5, 35.8, c - 0.5, c + 0.5, 'acab'))
        for c in (-3.5, 0.0, 3.5):
            P.append(cubo(c - 0.4, c + 0.4, 33.3, 34.1, -5.7, -5.5, 'brilho'))
    elif estilo == 'asa':
        asa = [cubo(5.0, 6.0, 27.0, 33.4, -1.5, 1.5, 'acab')]
        for k in range(5):
            asa.append(cubo(5.0, 5.6, 27.0 + k * 1.3, 28.5 + k * 1.3, -1.0 + k * 0.5, 4.0 + k * 1.3, 'pena'))
        P += par(asa)
    elif estilo == 'chifres':
        P += par([cubo(5.0, 7.0, 29.0, 31.5, -1.5, 1.5, 'osso'),
                  cubo(6.5, 8.5, 30.5, 33.0, -1.25, 1.25, 'osso'),
                  cubo(7.5, 9.0, 32.5, 35.0, -1.0, 1.0, 'osso'),
                  cubo(8.0, 9.2, 34.5, 36.8, -0.7, 0.7, 'osso_ponta'),
                  cubo(8.4, 9.2, 36.5, 38.0, -0.45, 0.45, 'osso_ponta')])
    elif estilo == 'presas':
        P += par([cubo(5.0, 6.5, 30.0, 32.0, -1.0, 1.0, 'osso'),
                  cubo(6.0, 7.5, 31.5, 33.5, -0.8, 0.8, 'osso'),
                  cubo(6.6, 7.6, 33.2, 34.8, -0.6, 0.6, 'osso_ponta'),
                  cubo(2.4, 3.6, 23.5, 25.0, -6.5, -5.0, 'osso'),
                  cubo(2.6, 3.4, 25.0, 26.8, -6.6, -5.6, 'osso'),
                  cubo(2.75, 3.25, 26.6, 27.8, -6.5, -5.9, 'osso_ponta')])
    elif estilo == 'gladiador':
        P.append(cubo(-0.75, 0.75, 33.0, 36.5, -4.5, 5.5, 'pluma'))
        P.append(cubo(-0.75, 0.75, 33.0, 35.0, -5.8, -4.5, 'pluma'))
        P.append(cubo(-6.5, 6.5, 30.4, 31.0, -6.5, -5.0, 'acab'))
        P.append(cubo(-6.5, 6.5, 30.4, 31.0, 5.0, 6.5, 'acab'))
        P += par([cubo(5.0, 6.5, 30.4, 31.0, -5.0, 5.0, 'acab')])
    elif estilo == 'centuriao':
        P.append(cubo(-5.5, 5.5, 33.0, 36.5, -0.8, 0.8, 'pluma'))
        P.append(cubo(-0.6, 0.6, 33.0, 34.0, -1.0, 1.0, 'acab'))
    elif estilo == 'inseto':
        P += par([cubo(1.5, 2.3, 33.0, 35.0, -4.5, -3.7, 'acab'),
                  cubo(2.0, 2.8, 34.5, 36.5, -3.9, -2.5, 'acab'),
                  cubo(2.4, 3.2, 36.0, 37.5, -2.6, -0.6, 'acab'),
                  cubo(2.4, 3.4, 37.2, 38.2, -0.9, 0.3, 'escuro'),
                  cubo(1.0, 2.2, 23.2, 24.6, -6.0, -5.0, 'escuro')])
    elif estilo == 'sculk':
        P += par([cubo(5.0, 6.2, 30.0, 31.5, 0.0, 1.5, 'acab'),
                  cubo(5.6, 6.6, 31.2, 33.0, 1.2, 2.8, 'acab'),
                  cubo(5.9, 6.7, 32.7, 34.5, 2.6, 4.0, 'acab'),
                  cubo(6.0, 6.6, 34.3, 35.3, 3.8, 4.6, 'brilho')])
    elif estilo == 'vidro':
        P += par([cubo(5.0, 6.0, 26.5, 30.5, -2.0, 2.0, 'acab')])
        P.append(cubo(5.4, 5.9, 30.5, 34.5, 0.0, 0.5, 'acab'))
        P.append(cubo(5.3, 6.0, 34.5, 35.3, -0.1, 0.6, 'brilho'))
    elif estilo == 'escafandro':
        P.append(cubo(-2.6, 2.6, 31.3, 32.0, -5.6, -5.0, 'acab'))
        P.append(cubo(-2.6, 2.6, 24.9, 25.6, -5.6, -5.0, 'acab'))
        P += par([cubo(2.9, 3.6, 25.6, 31.3, -5.6, -5.0, 'acab')])
        P.append(cubo(-1.0, 1.0, 33.0, 34.0, -1.0, 1.0, 'acab'))
        P.append(cubo(-1.5, 1.5, 34.0, 34.6, -1.5, 1.5, 'acab'))
        P += par([cubo(5.0, 5.6, 27.0, 29.0, -1.0, 1.0, 'acab')])
    elif estilo in ('lobo', 'urso'):
        if estilo == 'lobo':
            P += par([cubo(2.0, 4.2, 33.0, 34.6, -2.6, -1.4, 'pelo'),
                      cubo(2.6, 3.8, 34.6, 36.0, -2.4, -1.6, 'pelo')])
            P.append(cubo(-1.6, 1.6, 29.2, 31.4, -7.2, -5.0, 'pelo'))
            P.append(cubo(-0.8, 0.8, 30.4, 31.6, -7.5, -7.1, 'escuro'))
        else:
            P += par([cubo(2.4, 4.4, 33.0, 34.6, -2.4, -1.2, 'pelo')])
            P.append(cubo(-2.0, 2.0, 29.5, 31.5, -6.6, -5.0, 'pelo'))
            P.append(cubo(-1.0, 1.0, 30.6, 31.6, -6.9, -6.5, 'escuro'))
    elif estilo == 'mascara':
        for x, h in ((-4.0, 36.0), (-2.0, 37.5), (0.0, 38.5), (2.0, 37.5), (4.0, 36.0)):
            P.append(cubo(x - 0.7, x + 0.7, 33.0, h, 2.5, 3.0, 'pena'))
    elif estilo == 'mumia':
        P.append(cubo(1.0, 2.2, 24.0, 29.0, 5.0, 5.5, 'pano'))
    elif estilo == 'cogumelo':
        P.append(cubo(-7.0, 7.0, 31.0, 34.5, -7.0, 7.0, 'cogumelo'))
        P.append(cubo(-5.0, 5.0, 34.5, 35.6, -5.0, 5.0, 'cogumelo'))
    elif estilo == 'ninja':
        P.append(cubo(-1.6, -0.4, 25.0, 30.5, 5.0, 5.6, 'pano2'))
        P.append(cubo(0.4, 1.6, 23.5, 30.0, 5.0, 5.6, 'pano2'))
        P.append(cubo(-1.2, 1.2, 29.8, 31.6, 5.0, 6.2, 'pano2'))
    elif estilo == 'veu':
        P.append(cubo(-2.6, 2.6, 33.0, 34.8, -2.6, 2.6, 'pano'))
        P.append(cubo(-2.7, 2.7, 33.0, 33.5, -2.7, 2.7, 'acab'))
        P.append(cubo(2.6, 3.2, 33.6, 34.4, -0.4, 0.4, 'brilho'))
    elif estilo == 'capuz':
        P.append(cubo(-1.5, 1.5, 31.0, 33.8, 5.0, 6.8, 'pano'))
        P.append(cubo(-0.8, 0.8, 29.0, 31.2, 6.0, 7.4, 'pano'))
    elif estilo == 'chapeu':
        P.append(cubo(-7.5, 7.5, 30.8, 31.6, -7.5, 7.5, 'palha'))
        P.append(cubo(-4.2, 4.2, 31.6, 34.6, -4.2, 4.2, 'palha'))
        P.append(cubo(-4.3, 4.3, 31.6, 32.4, -4.3, 4.3, 'couro'))
    elif estilo == 'tricornio':
        P.append(cubo(-6.0, 6.0, 31.0, 34.0, -6.2, -5.4, 'chapeu'))
        P.append(cubo(-6.0, 6.0, 31.0, 33.0, 5.4, 6.2, 'chapeu'))
        P += par([cubo(5.4, 6.2, 31.0, 33.0, -6.0, 6.0, 'chapeu')])
        P.append(cubo(-4.5, 4.5, 33.0, 35.0, -4.5, 4.5, 'chapeu'))
        P.append(cubo(-6.1, 6.1, 33.6, 34.2, -6.4, -6.0, 'acab'))
    elif estilo == 'lanterna':
        P.append(cubo(-5.5, 5.5, 29.6, 30.4, -6.8, -5.0, 'placa'))
        P.append(cubo(-1.5, 1.5, 30.5, 33.0, -6.8, -5.0, 'acab'))
        P.append(cubo(-1.1, 1.1, 30.9, 32.6, -7.0, -6.8, 'brilho'))
    elif estilo == 'oculos':
        P += par([cubo(1.0, 3.6, 28.6, 31.2, -6.2, -5.0, 'acab'),
                  cubo(1.4, 3.2, 29.0, 30.8, -6.4, -6.2, 'vidro')])
    elif estilo == 'nemes':
        P += par([cubo(3.4, 5.4, 17.5, 24.0, -4.5, -3.2, 'pano')])
        P.append(cubo(-0.5, 0.5, 31.0, 33.5, -6.0, -5.0, 'acab'))
    elif estilo == 'mitra':
        P.append(cubo(-3.5, 3.5, 33.0, 37.0, -2.0, 2.0, 'pano2'))
        P.append(cubo(-2.0, 2.0, 37.0, 38.5, -1.5, 1.5, 'pano2'))
        P.append(cubo(-0.4, 0.4, 33.0, 38.0, -2.2, -2.0, 'acab'))
    elif estilo == 'kabuto':
        P += par([cubo(2.5, 3.5, 32.0, 34.0, -6.0, -5.4, 'acab'),
                  cubo(3.2, 4.2, 34.0, 36.0, -6.0, -5.4, 'acab'),
                  cubo(3.9, 4.8, 36.0, 37.6, -6.0, -5.4, 'acab'),
                  cubo(5.0, 6.2, 23.0, 26.5, -3.0, 6.0, 'placa')])
        P.append(cubo(-1.0, 1.0, 31.0, 33.0, -6.2, -5.2, 'acab'))
        P.append(cubo(-6.0, 6.0, 23.0, 26.5, 5.0, 6.2, 'placa'))
    return P


def estilo_de(id_):
    return V3.ESTILO_V3.get(id_, A.R[id_][2])


# ------------------------------------------------------------------ Java
def _uv_amostra_java(mat):
    x0, y0 = pos_amostra(mat)
    m = 0.4
    return [round((x0 + m) / 4, 4), round((y0 + m) / 2, 4), round((x0 + TAM - m) / 4, 4), round((y0 + TAM - m) / 2, 4)]


def _r(v):
    return round(float(v), 4)


# casco: faces da textura (px 64x32) -> faces do modelo (norte = frente do jogador no contexto "head")
def _uv_px(x0, y0, x1, y1):
    return [_r(x0 / 4), _r(y0 / 2), _r(x1 / 4), _r(y1 / 2)]


CASCO_JAVA = {
    'north': {'uv': _uv_px(8, 8, 16, 16)},                  # frente
    'east': {'uv': _uv_px(0, 8, 8, 16)},                    # lado direito do jogador
    'south': {'uv': _uv_px(24, 8, 32, 16)},                 # nuca
    'west': {'uv': _uv_px(16, 8, 24, 16)},                  # lado esquerdo
    'up': {'uv': _uv_px(16, 8, 8, 0)},                      # topo (girado 180)
    'down': {'uv': _uv_px(16, 0, 24, 8)},
}


def modelo_java(id_, tema_tex):
    def mx(x):
        return _r((5 - x) * 1.6)

    def my(y):
        return _r((y - 23) * 1.6)

    def mz(z):
        return _r((z + 5) * 1.6)

    els = [{'from': [0, 0, 0], 'to': [16, 16, 16],
            'faces': {f: dict(v, texture='#t') for f, v in CASCO_JAVA.items()}}]
    for c in pecas(estilo_de(id_), id_):
        (x0, y0, z0), (x1, y1, z1) = c['a'], c['b']
        a = [min(mx(x0), mx(x1)), my(y0), mz(z0)]
        b = [max(mx(x0), mx(x1)), my(y1), mz(z1)]
        uv = _uv_amostra_java(c['mat'])
        els.append({'from': a, 'to': b, 'faces': {f: {'uv': uv, 'texture': '#t'} for f in ('north', 'east', 'south', 'west', 'up', 'down')}})
    return {'textures': {'t': tema_tex, 'particle': tema_tex}, 'elements': els,
            'display': {'head': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]}}}


def item_java(id_):
    tema = id_.lower()
    return {'model': {'type': 'minecraft:select', 'property': 'minecraft:display_context',
                      'cases': [{'when': 'head', 'model': {'type': 'minecraft:model', 'model': f'corona:item/elmo3d/{tema}'}}],
                      'fallback': {'type': 'minecraft:model', 'model': f'corona:item/armaduras/{tema}_elmo'}}}


# ------------------------------------------------------------------ Bedrock
def geometria_bedrock(id_):
    tema = id_.lower()
    cubos = [{'origin': [-4, 24, -4], 'size': [8, 8, 8], 'uv': [0, 0], 'inflate': 1.0}]
    for c in pecas(estilo_de(id_), id_):
        (x0, y0, z0), (x1, y1, z1) = c['a'], c['b']
        u, v = pos_amostra(c['mat'])
        m = 0.4
        face = {'uv': [_r(u + m), _r(v + m)], 'uv_size': [_r(TAM - 2 * m), _r(TAM - 2 * m)]}
        cubos.append({'origin': [_r(x0), _r(y0), _r(z0)], 'size': [_r(x1 - x0), _r(y1 - y0), _r(z1 - z0)],
                      'uv': {f: dict(face) for f in ('north', 'east', 'south', 'west', 'up', 'down')}})
    return {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': f'geometry.corona.elmo3d.{tema}', 'texture_width': 64, 'texture_height': 32,
                        'visible_bounds_width': 3, 'visible_bounds_height': 3.5, 'visible_bounds_offset': [0, 1.5, 0]},
        'bones': [{'name': 'head', 'pivot': [0, 24, 0], 'cubes': cubos}]}]}


def attachable_bedrock(id_, mat):
    tema = id_.lower()
    return {'format_version': '1.10.0', 'minecraft:attachable': {'description': {
        'identifier': f'corona:arm_{tema}_elmo_{mat}',
        'materials': {'default': 'armor', 'enchanted': 'armor_enchanted'},
        'textures': {'default': f'textures/models/armor/corona_{tema}_1', 'enchanted': 'textures/misc/enchanted_actor_glint'},
        'geometry': {'default': f'geometry.corona.elmo3d.{tema}'},
        'scripts': {'parent_setup': 'variable.helmet_layer_visible = 0.0;'},
        'render_controllers': ['controller.render.armor']}}}


def escrever(caminho, obj):
    os.makedirs(os.path.dirname(caminho), exist_ok=True)
    with open(caminho, 'w', encoding='utf-8') as f:
        json.dump(obj, f, separators=(',', ':'))


def gerar(saida, so=None):
    n = 0
    for id_, t in A.TEMAS.items():
        if so and id_ not in so:
            continue
        tema = id_.lower()
        img = V3.textura(id_, t, 1)
        img = pintar_amostras(img, id_, t)
        im = Image.fromarray(img, 'RGBA')
        jt = os.path.join(saida, 'java', 'assets', 'corona', 'textures')
        for rel in (f'entity/equipment/humanoid/{tema}.png', f'item/elmo3d/{tema}.png'):
            os.makedirs(os.path.dirname(os.path.join(jt, rel)), exist_ok=True)
            im.save(os.path.join(jt, rel), optimize=True)
        jb = os.path.join(saida, 'java', 'assets', 'corona')
        escrever(os.path.join(jb, 'models', 'item', 'elmo3d', f'{tema}.json'), modelo_java(id_, f'corona:item/elmo3d/{tema}'))
        escrever(os.path.join(jb, 'items', 'item', 'armaduras', f'{tema}_elmo.json'), item_java(id_))
        bd = os.path.join(saida, 'bedrock')
        os.makedirs(os.path.join(bd, 'textures', 'models', 'armor'), exist_ok=True)
        im.save(os.path.join(bd, 'textures', 'models', 'armor', f'corona_{tema}_1.png'), optimize=True)
        escrever(os.path.join(bd, 'models', 'entity', f'corona_elmo3d_{tema}.geo.json'), geometria_bedrock(id_))
        for mat in MATERIAIS:
            escrever(os.path.join(bd, 'attachables', f'arm_{tema}_elmo_{mat}.json'), attachable_bedrock(id_, mat))
        n += 1
    print(n, 'elmos 3D em', saida)


if __name__ == '__main__':
    gerar(sys.argv[1], [x.upper() for x in sys.argv[2:]] or None)
