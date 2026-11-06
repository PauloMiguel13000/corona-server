"""Gera as skins: Java 256x256 (pacote), Bedrock 128x128 (extensao do Geyser) e previas para conferir."""
import os, sys
import numpy as np
from PIL import Image
import base
from monstros import MONSTROS

JAVA = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'coronapack', 'pacote', 'java', 'assets', 'corona', 'textures', 'entity', 'monstro')
BEDROCK = '/tmp/claude-0/skins_bedrock'
PREV = '/tmp/claude-0/skins'
for d in (JAVA, BEDROCK, PREV):
    os.makedirs(d, exist_ok=True)
so = sys.argv[1:]
for nome, (f, cabeca) in MONSTROS.items():
    if so and nome not in so:
        continue
    img = base.montar(f, nome)
    Image.fromarray(img, 'RGBA').save(f'{JAVA}/{nome}.png', optimize=True)
    Image.fromarray(base.reduzir(img, 2, corte=True), 'RGBA').save(f'{BEDROCK}/{nome}.png', optimize=True)
    base.previa(img, cabeca).save(f'{PREV}/prev_{nome}.png')
    # previa do Bedrock (128, opacidade cortada)
    b = np.array(Image.open(f'{BEDROCK}/{nome}.png').convert('RGBA'))
    b2 = np.kron(b, np.ones((2, 2, 1), dtype=np.uint8))
    base.previa(b2, cabeca).save(f'{PREV}/prevb_{nome}.png')
    print('ok', nome)
# folha unica com todas as previas (para conferir de uma vez)
from PIL import Image as _I
fs = [f for f in sorted(os.listdir(PREV)) if f.startswith('prev_')]
ims = [_I.open(f'{PREV}/{f}') for f in fs]
ims = [i.resize((i.size[0] * 2 // 3, i.size[1] * 2 // 3), _I.NEAREST) for i in ims]
W = max(i.size[0] for i in ims); H = sum(i.size[1] + 6 for i in ims)
folha = _I.new('RGBA', (W, H), (40, 44, 52, 255)); yy = 0
for i in ims:
    folha.alpha_composite(i, (0, yy)); yy += i.size[1] + 6
folha.save(f'{PREV}/TODAS.png')
