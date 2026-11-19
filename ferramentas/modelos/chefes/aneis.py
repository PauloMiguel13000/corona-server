"""Aneis elementais das masmorras (19/11, Paulo: "adicione resource nos aneis").

O anel continua sendo o item normal (po de blaze, coracao do mar, esmeralda, vara de breeze, po de pedra luminosa,
fragmento ecoante) com custom_model_data "anel_<elemento>". No Java, o pacote das masmorras troca o desenho so desses
(fora das masmorras, sem o pacote, fica o item normal: nunca aparece o cubo roxo). No Bedrock, o Geyser mapeia pelo
mesmo custom_model_data.
"""
import json
import os

from PIL import Image

# elemento: (item base, cor da pedra (clara, media, escura), cor do aro (clara, media, escura))
OURO = ((255, 236, 140), (230, 184, 46), (150, 104, 20))
PRATA = ((240, 244, 250), (178, 188, 204), (104, 112, 130))
OBSID = ((120, 90, 150), (62, 40, 84), (28, 16, 40))
ANEIS = {
    'fogo': ('blaze_powder', ((255, 220, 120), (255, 110, 20), (170, 30, 10)), OURO),
    'agua': ('heart_of_the_sea', ((170, 230, 255), (40, 140, 255), (16, 60, 170)), PRATA),
    'terra': ('emerald', ((170, 255, 150), (60, 170, 50), (24, 90, 30)), OURO),
    'vento': ('breeze_rod', ((255, 255, 255), (190, 235, 245), (110, 170, 200)), PRATA),
    'raio': ('glowstone_dust', ((255, 255, 190), (255, 220, 40), (190, 130, 0)), OURO),
    'vazio': ('echo_shard', ((230, 170, 255), (150, 60, 220), (70, 20, 120)), OBSID),
}


def desenhar(pedra, aro):
    """Anel visto de frente-cima, 16x16: aro oval com brilho e uma pedra lapidada em cima."""
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()
    cl, cm, ce = aro
    # aro: elipse oca (centro 8, 10; raios 6 x 4.2), espessura ~1.6
    for y in range(16):
        for x in range(16):
            dx, dy = (x + 0.5 - 8) / 6.0, (y + 0.5 - 10.2) / 4.2
            d = (dx * dx + dy * dy) ** 0.5
            if 0.70 <= d <= 1.0:
                cor = cm
                if dy < -0.2 and dx < 0.1:
                    cor = cl
                if dy > 0.35 or dx > 0.55:
                    cor = ce
                px[x, y] = cor + (255,)
    # engaste
    for x, y in ((6, 6), (7, 6), (8, 6), (9, 6), (6, 7), (9, 7)):
        px[x, y] = ce + (255,)
    # pedra lapidada (losango 5 de largura)
    pl, pm, pe = pedra
    forma = {1: (7, 8), 2: (6, 9), 3: (5, 10), 4: (5, 10), 5: (6, 9), 6: (7, 8)}
    for y, (a, b) in forma.items():
        for x in range(a, b + 1):
            cor = pm
            if x <= 7 and y <= 3:
                cor = pl
            if x >= 9 or y >= 5:
                cor = pe
            px[x, y] = cor + (255,)
    px[6, 2] = (255, 255, 255, 255)   # brilho
    # contorno escuro de 1 px em volta de tudo (le bem no celular)
    base = im.copy()
    bp = base.load()
    for y in range(16):
        for x in range(16):
            if bp[x, y][3] == 0:
                for ax, ay in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + ax, y + ay
                    if 0 <= nx < 16 and 0 <= ny < 16 and bp[nx, ny][3] > 0:
                        px[x, y] = (20, 14, 10, 200)
                        break
    return im


def gerar(tmp, mapa, item_texture):
    """Poe os aneis nas pastas java/ e bedrock/ do empacotar e no mapa do Geyser."""
    j = os.path.join(tmp, 'java', 'assets')
    os.makedirs(os.path.join(j, 'corona', 'textures', 'item'), exist_ok=True)
    os.makedirs(os.path.join(j, 'corona', 'models', 'item'), exist_ok=True)
    os.makedirs(os.path.join(j, 'minecraft', 'items'), exist_ok=True)
    b = os.path.join(tmp, 'bedrock', 'textures', 'items', 'corona', 'masm')
    os.makedirs(b, exist_ok=True)
    for el, (base, pedra, aro) in ANEIS.items():
        nome = f'anel_{el}'
        im = desenhar(pedra, aro)
        im.save(os.path.join(j, 'corona', 'textures', 'item', nome + '.png'))
        im.save(os.path.join(b, 'masm_' + nome + '.png'))
        json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': f'corona:item/{nome}'}},
                  open(os.path.join(j, 'corona', 'models', 'item', nome + '.json'), 'w'))
        json.dump({'model': {'type': 'minecraft:select', 'property': 'minecraft:custom_model_data', 'index': 0,
                             'cases': [{'when': nome, 'model': {'type': 'minecraft:model', 'model': f'corona:item/{nome}'}}],
                             'fallback': {'type': 'minecraft:model', 'model': f'minecraft:item/{base}'}}},
                  open(os.path.join(j, 'minecraft', 'items', base + '.json'), 'w'))
        item_texture['masm_' + nome] = {'textures': 'textures/items/corona/masm/masm_' + nome}
        mapa['items'].setdefault(f'minecraft:{base}', []).append({
            'type': 'definition', 'model': f'minecraft:{base}', 'bedrock_identifier': f'corona:masm_{nome}',
            'display_name': 'Anel', 'predicate': {'type': 'match', 'property': 'custom_model_data', 'value': nome, 'index': 0},
            'bedrock_options': {'icon': 'masm_' + nome, 'allow_offhand': True}})


if __name__ == '__main__':
    import sys
    saida = sys.argv[1] if len(sys.argv) > 1 else '/tmp/aneis_previa.png'
    folha = Image.new('RGBA', (6 * 16 * 8 + 70, 16 * 8 + 20), (40, 40, 48, 255))
    for i, (el, (base, pedra, aro)) in enumerate(ANEIS.items()):
        folha.paste(desenhar(pedra, aro).resize((128, 128), Image.NEAREST), (10 + i * 140, 10))
    folha.save(saida)
