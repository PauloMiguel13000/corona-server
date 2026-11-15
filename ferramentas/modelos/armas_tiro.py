"""Armas de fogo dos jogos de tiro (desenho 32x32 de perfil; o arquivo sai espelhado: cano para a esquerda, cabo embaixo a direita).
Java: item_model corona:armas/<id> (item/handheld). Bedrock: icone corona_arma_<id> (display_handheld).
uso: python3 armas_tiro.py <saida>   (escreve <saida>/java/assets/corona/..., <saida>/bedrock/..., corona-tiro-armas.json)
"""
import json
import os
import sys

import numpy as np
from PIL import Image

SAIDA = sys.argv[1]
ACO, ACO2, PRETO, MADEIRA, MADEIRA2, VERDE, VERDE2, OURO, VERM, VIDRO = (
    (92, 96, 104), (58, 61, 68), (30, 31, 35), (132, 86, 46), (98, 60, 30), (74, 92, 58), (52, 66, 42), (212, 168, 60), (176, 44, 36), (96, 190, 230))

# id -> (item do Java, nome, retangulos (x0, y0, x1, y1, cor) em ordem de pintura; y cresce para baixo)
ARMAS = {
    'pistola': ('wooden_hoe', 'Pistola 9mm', [
        (8, 12, 24, 15, ACO), (25, 13, 25, 14, PRETO), (9, 16, 21, 17, ACO2), (8, 18, 12, 24, MADEIRA), (7, 21, 11, 25, MADEIRA),
        (13, 18, 17, 18, PRETO), (17, 18, 17, 20, PRETO), (13, 20, 17, 20, PRETO), (14, 18, 14, 19, ACO2), (10, 11, 11, 11, PRETO),
        (23, 11, 23, 11, PRETO), (12, 13, 20, 13, ACO2)]),
    'smg': ('golden_hoe', 'Submetralhadora', [
        (1, 12, 6, 13, ACO2), (1, 12, 2, 17, ACO2), (6, 11, 22, 15, ACO), (23, 12, 29, 13, PRETO), (27, 11, 27, 11, PRETO),
        (7, 16, 10, 22, PRETO), (6, 20, 9, 23, PRETO), (14, 16, 16, 25, ACO2), (15, 16, 17, 25, ACO2), (11, 16, 13, 16, PRETO),
        (13, 16, 13, 18, PRETO), (10, 9, 12, 10, PRETO), (8, 12, 20, 12, OURO), (18, 14, 21, 14, ACO2)]),
    'escopeta': ('stone_hoe', 'Escopeta', [
        (0, 13, 6, 18, MADEIRA), (0, 15, 3, 20, MADEIRA), (4, 12, 8, 15, MADEIRA2), (7, 11, 13, 15, ACO), (13, 11, 30, 12, ACO),
        (13, 13, 27, 14, ACO2), (16, 14, 23, 16, MADEIRA), (17, 15, 22, 15, MADEIRA2), (8, 16, 10, 20, MADEIRA2), (11, 16, 13, 16, PRETO),
        (13, 16, 13, 18, PRETO), (30, 10, 30, 10, OURO), (9, 12, 11, 12, ACO2)]),
    'fuzil': ('iron_hoe', 'Fuzil de Assalto', [
        (0, 12, 6, 16, VERDE), (0, 14, 2, 18, VERDE), (6, 13, 8, 14, ACO2), (7, 11, 18, 15, ACO), (19, 11, 25, 14, VERDE),
        (26, 12, 31, 12, PRETO), (30, 11, 31, 13, PRETO), (13, 16, 16, 19, ACO2), (14, 19, 17, 23, ACO2), (8, 16, 10, 21, VERDE2),
        (7, 19, 9, 22, VERDE2), (11, 16, 12, 16, PRETO), (12, 16, 12, 18, PRETO), (9, 8, 11, 10, PRETO), (9, 9, 14, 10, PRETO),
        (25, 9, 25, 10, PRETO), (20, 12, 24, 12, VERDE2), (9, 12, 16, 12, ACO2)]),
    'sniper': ('diamond_hoe', 'Rifle de Precisao', [
        (0, 12, 5, 17, MADEIRA), (0, 14, 2, 19, MADEIRA), (2, 11, 5, 11, MADEIRA2), (5, 11, 15, 14, ACO), (15, 12, 31, 12, ACO2),
        (15, 13, 20, 13, MADEIRA), (30, 11, 31, 13, PRETO), (7, 6, 19, 9, PRETO), (6, 7, 6, 8, VIDRO), (20, 7, 20, 8, VIDRO),
        (9, 10, 10, 10, ACO2), (16, 10, 17, 10, ACO2), (8, 15, 10, 20, MADEIRA2), (11, 15, 13, 15, PRETO), (13, 15, 13, 17, PRETO),
        (12, 15, 14, 18, ACO2), (23, 13, 23, 18, PRETO), (22, 18, 22, 20, PRETO), (24, 18, 24, 20, PRETO), (8, 7, 18, 7, ACO2)]),
    'metralhadora': ('netherite_hoe', 'Metralhadora Pesada', [
        (0, 11, 4, 16, ACO2), (0, 13, 1, 19, ACO2), (4, 10, 18, 16, ACO), (19, 11, 31, 13, ACO2), (20, 12, 20, 12, PRETO),
        (22, 12, 22, 12, PRETO), (24, 12, 24, 12, PRETO), (26, 12, 26, 12, PRETO), (30, 10, 31, 14, PRETO), (9, 17, 16, 24, VERDE),
        (9, 17, 16, 18, VERDE2), (11, 20, 14, 21, OURO), (5, 17, 7, 22, PRETO), (10, 6, 16, 7, PRETO), (10, 8, 10, 9, PRETO),
        (16, 8, 16, 9, PRETO), (25, 14, 25, 19, PRETO), (24, 19, 24, 21, PRETO), (26, 19, 26, 21, PRETO), (6, 12, 16, 12, VERM),
        (17, 16, 18, 17, OURO), (17, 18, 18, 19, OURO)]),
}


def desenhar(rets):
    img = np.zeros((32, 32, 4), np.uint8)
    for x0, y0, x1, y1, cor in rets:
        img[y0:y1 + 1, x0:x1 + 1, :3] = cor
        img[y0:y1 + 1, x0:x1 + 1, 3] = 255
    cheio = img[..., 3] > 0
    rgb = img[..., :3].astype(float)
    # luz de cima, sombra embaixo e um pontilhado leve
    cima = np.zeros_like(cheio)
    cima[1:] = ~cheio[:-1]
    baixo = np.zeros_like(cheio)
    baixo[:-1] = ~cheio[1:]
    baixo[-1] = True
    rgb[cheio & cima] *= 1.28
    rgb[cheio & baixo] *= 0.72
    rng = np.random.default_rng(7)
    rgb *= (0.94 + rng.random((32, 32, 1)) * 0.12)
    img[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)
    # contorno escuro por fora
    viz = np.zeros_like(cheio)
    viz[1:] |= cheio[:-1]
    viz[:-1] |= cheio[1:]
    viz[:, 1:] |= cheio[:, :-1]
    viz[:, :-1] |= cheio[:, 1:]
    borda = viz & ~cheio
    img[borda] = (16, 16, 20, 255)
    return img


def salvar(caminho, obj):
    os.makedirs(os.path.dirname(caminho), exist_ok=True)
    with open(caminho, 'w', encoding='utf-8') as f:
        json.dump(obj, f, ensure_ascii=False, separators=(',', ':'))


J = os.path.join(SAIDA, 'java', 'assets', 'corona')
B = os.path.join(SAIDA, 'bedrock')
mapa, item_tex, folha = {}, {}, Image.new('RGBA', (32 * 6 * 6, 32 * 6), (150, 158, 168, 255))
for i, (ident, (item, nome, rets)) in enumerate(ARMAS.items()):
    # espelhado: no jogo (item/handheld) o lado ESQUERDO do desenho e o que aponta para a frente (15/11: estava ao contrario)
    img = Image.fromarray(np.ascontiguousarray(desenhar(rets)[:, ::-1]), 'RGBA')
    for pasta in (os.path.join(J, 'textures', 'item', 'armas'), os.path.join(B, 'textures', 'items', 'corona', 'tiro')):
        os.makedirs(pasta, exist_ok=True)
    img.save(os.path.join(J, 'textures', 'item', 'armas', ident + '.png'))
    img.save(os.path.join(B, 'textures', 'items', 'corona', 'tiro', f'corona_arma_{ident}.png'))
    salvar(os.path.join(J, 'models', 'item', 'armas', ident + '.json'), {'parent': 'minecraft:item/handheld', 'textures': {'layer0': f'corona:item/armas/{ident}'}})
    salvar(os.path.join(J, 'items', 'armas', ident + '.json'), {'model': {'type': 'minecraft:model', 'model': f'corona:item/armas/{ident}'}})
    item_tex[f'corona_arma_{ident}'] = {'textures': f'textures/items/corona/tiro/corona_arma_{ident}'}
    mapa.setdefault('minecraft:' + item, []).append({
        'type': 'definition', 'model': f'corona:armas/{ident}', 'bedrock_identifier': f'corona:arma_{ident}', 'display_name': nome,
        'bedrock_options': {'display_handheld': True, 'icon': f'corona_arma_{ident}', 'allow_offhand': True}})
    g = img.resize((192, 192), Image.NEAREST)
    folha.paste(g, (i * 192, 0), g)
salvar(os.path.join(SAIDA, 'corona-tiro-armas.json'), {'format_version': 2, 'items': mapa})
salvar(os.path.join(SAIDA, 'item_texture_armas.json'), item_tex)
folha.convert('RGB').save(os.path.join(SAIDA, 'previa_armas.png'))
print('armas:', len(ARMAS))
