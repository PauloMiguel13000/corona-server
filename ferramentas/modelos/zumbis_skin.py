"""Skins dos infectados dos jogos de tiro (15/11, refeitas "com mais vontade"): o zumbi DE VERDADE do jogo (anda e
ataca animado) vestindo uma "armadura" de corpo inteiro que e a pele dele: rosto, roupa rasgada, feridas, sangue.
Pixel art desenhada a mao no tamanho do jogo (64x32) e ampliada 4x com granulado e sombra (256x128).
Camada 1 = cabeca (elmo), tronco e bracos (peitoral) e sapatos (botas); camada 2 = pernas e cintura (calca).

Java   : equipment/z<tipo>.json + textures/entity/equipment/humanoid[_leggings]/z<tipo>.png + items/zumbi/<tipo>_<peca>
Bedrock: textures/models/armor/corona_z<tipo>_<1|2>.png + attachables zumbi_<tipo>_<peca> + corona-tiro-zumbis.json
uso: python3 zumbis_skin.py <saida>
"""
import json
import os
import sys

import numpy as np
from PIL import Image

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(AQUI, '..', 'tools'))
SAIDA = sys.argv[1]
K = 4

BASE = {'k': (16, 12, 14), 't': (226, 218, 192), 'b': (112, 14, 16), 'B': (168, 28, 24), 'x': (216, 208, 180), 'X': (160, 152, 128),
        'r': (146, 38, 40), 'S': (34, 28, 26), 'z': (22, 18, 18), 'w': (240, 240, 232)}

T = {}

T['comum'] = dict(
    pal=dict(s=(96, 142, 74), d=(68, 106, 54), l=(122, 170, 94), h=(44, 34, 26), H=(30, 24, 18), e=(255, 216, 92), A=(56, 98, 146), a=(40, 72, 112),
             P=(78, 60, 44), p=(56, 42, 32), C=(92, 92, 96), c=(64, 64, 70)),
    rosto=["hHhhhHhh",
           "hhshHhsh",
           "slssssds",
           "dkkddkkd",
           "skesdkes",
           "sdsddsss",
           "dtktkttb",
           "ddkkkkbB"],
    corpo=["aAAslAAa",
           "AAAasAAA",
           "AaAAAAaA",
           "AArrbAAa",
           "AaxrxbAA",
           "AArxrBAa",
           "AaxrxbAA",
           "AAbrBaAA",
           "aAAAbAAA",
           "AAaAAbaA",
           "aAsAaAAa",
           "asdaAsda"],
    costas=["aAAAAAAa", "AAaAAAAA", "AAAAaAAA", "AaAAAAAa", "AAAbAAAA", "AAbBbAaA", "aAAbAAAA", "AAAAAAAA", "AAaAAAaA", "aAAAAAAA", "AAsAAaAa", "adAasdAa"],
    braco=["AAAa", "AAaA", "aAAA", "AaAa", "asAs", "ssds", "sdls", "sbss", "srBd", "dsss", "sdsd", "ddkd"],
    perna=["PPpP", "PpPP", "PPPp", "pPPP", "PPpP", "PksP", "PssP", "pPdp", "PPpP", "PpPP", "pPPb", "PPbP"],
    topo='h')

T['corredor'] = dict(
    pal=dict(s=(156, 150, 132), d=(116, 110, 98), l=(184, 178, 160), h=(140, 26, 28), H=(98, 16, 20), e=(255, 64, 40), A=(150, 28, 30), a=(104, 18, 22),
             P=(40, 40, 50), p=(26, 26, 34), C=(210, 210, 204), c=(60, 60, 66)),
    rosto=["HhhhhhhH",
           "hHHHHHHh",
           "hHddddHh",
           "hkkddkkh",
           "hkedskeh",
           "hdsddsdh",
           "HktttkkH",
           "hHkBkkHh"],
    corpo=["hAAddAAh",
           "AAaCcaAA",
           "AaACcAaA",
           "AAACcAAA",
           "AaACcAAa",
           "AAACcbAA",
           "AAaCcBaA",
           "AaACcbAA",
           "aaaCcaaa",
           "AaaAAaaA",
           "AAAaAAAa",
           "aAaAAaAa"],
    costas=["hHhhhhHh", "HhHHHHhH", "AhHHHHhA", "AAhhhhAA", "AaAAAAaA", "AAAAaAAA", "AAaAAAAA", "AAAbAAaA", "aAAAAAAa", "AAAaAAAA", "AaAAAaAA", "aAaAAaAa"],
    braco=["AAAa", "AAaA", "aAAA", "AaAA", "AAAa", "aAaA", "AAAA", "AaAa", "aAAa", "aaaa", "sdls", "dkkd"],
    perna=["PPpP", "PpPP", "PPPp", "pPPP", "PPpP", "PpPP", "PPPp", "pPPP", "PPpP", "PpPP", "CcCc", "PPpP"],
    topo='h', capuz=True)

T['tanque'] = dict(
    pal=dict(s=(84, 112, 80), d=(60, 82, 58), l=(106, 136, 98), h=(118, 120, 128), H=(84, 86, 94), e=(255, 150, 40), A=(88, 66, 46), a=(62, 46, 32),
             P=(60, 52, 44), p=(42, 36, 30), M=(124, 126, 134), m=(88, 90, 98), R=(138, 76, 38), C=(52, 52, 58), c=(36, 36, 42)),
    rosto=["HhhhhhhH",
           "hMhhhhMh",
           "hhRhhhhh",
           "mkkkkkkm",
           "hkekkekh",
           "mmmhhmmm",
           "hRhmhhmh",
           "sdtkktds"],
    corpo=["aAAssAAa",
           "CmMMMMmC",
           "mMMRMMMm",
           "mMcMMcRm",
           "mMMMMMMm",
           "mRMMmMMm",
           "CmmmmmmC",
           "AcAAaAcA",
           "AAcAAcAA",
           "aAAccAAa",
           "AAaAAaAA",
           "aAAaAAAa"],
    costas=["aAAAAAAa", "CmMMMMmC", "mMMMRMMm", "mMMMMMMm", "mRMmMMMm", "mMMMMRMm", "CmmmmmmC", "AAcAAcAA", "AAAccAAA", "aAAAAAAa", "AAaAAaAA", "aAAaAAAa"],
    braco=["mMMm", "MRMM", "mMMm", "CccC", "ssds", "sdls", "lsss", "ssdb", "CccC", "sdss", "ddsd", "dkkd"],
    perna=["PPpP", "PpPP", "PPPp", "pPPP", "mMMm", "MRMM", "mMMm", "PpPP", "PPpP", "PpPP", "pPPP", "PPpP"],
    topo='h', capuz=True)

T['cuspidor'] = dict(
    pal=dict(s=(124, 174, 62), d=(86, 130, 42), l=(164, 212, 86), h=(52, 60, 30), H=(36, 42, 20), e=(214, 255, 96), A=(102, 96, 60), a=(74, 70, 42),
             P=(62, 66, 44), p=(44, 48, 30), y=(206, 248, 92), Y=(150, 214, 60), g=(60, 150, 40)),
    rosto=["hHslsHhh",
           "hsssslsh",
           "slsysssd",
           "dkksdkkd",
           "skeddkes",
           "sdssssds",
           "dtkkkktd",
           "dkYyyYkd"],
    corpo=["aAAyYAAa",
           "AasYysaA",
           "AssssssA",
           "asyYsslA",
           "slYysssd",
           "sssssyYs",
           "dslssYys",
           "ssYssssd",
           "dsyYsdss",
           "AdsssdsA",
           "aAdssdAa",
           "AaAddAaA"],
    costas=["aAAAAAAa", "AAaAAAAA", "AAAsAAaA", "AasysAAA", "AAsYsAAA", "aAAsAAAa", "AAAAAyAA", "AaAAyYyA", "AAAAAyAA", "aAAaAAAa", "AAAAAaAA", "aAaAAAAa"],
    braco=["AAAa", "AaAA", "aAAa", "sdss", "ssls", "syYs", "sYys", "dsss", "ssds", "lssd", "sdss", "dYyd"],
    perna=["PPpP", "PpPP", "PPPp", "pPPP", "PPpP", "PYyP", "PpPP", "pPPp", "PPpP", "PpPP", "pPPP", "PPpP"],
    topo='h')

T['explosivo'] = dict(
    pal=dict(s=(180, 154, 88), d=(136, 114, 62), l=(208, 184, 114), h=(60, 44, 28), H=(40, 30, 20), e=(255, 140, 30), A=(222, 184, 40), a=(170, 136, 24),
             P=(70, 66, 56), p=(50, 46, 40), D=(188, 36, 30), n=(132, 20, 20), C=(40, 40, 44), c=(26, 26, 30), y=(255, 200, 60)),
    rosto=["hHhhhhHh",
           "hshhhssh",
           "ssslsrss",
           "dkkddkkd",
           "skesdkes",
           "sdsddsrs",
           "dtkttktd",
           "sdkkkkds"],
    corpo=["aAAssAAa",
           "AzAAAAzA",
           "AyzcczyA",
           "CDnDnDnC",
           "CDnDnDnC",
           "CCCCCCCC",
           "CDnDnDnC",
           "CDnDnDnC",
           "AzwzzwzA",
           "AzAzzAzA",
           "zAzAAzAz",
           "AzAzzAzA"],
    costas=["aAAAAAAa", "AAAAAAAA", "AzAAAAzA", "CCCCCCCC", "AzAAAAzA", "CCCCCCCC", "AzAAAAzA", "CCCCCCCC", "AAAAAAAA", "AzAzzAzA", "zAzAAzAz", "AzAzzAzA"],
    braco=["AAAa", "AaAA", "zzzz", "AAAa", "aAAA", "AAaA", "zzzz", "AaAA", "ssds", "srss", "sdss", "dkkd"],
    perna=["PPpP", "PpPP", "PPPp", "pPPP", "PPpP", "zzzz", "PPPp", "pPPP", "PPpP", "PpPP", "pPPP", "PPpP"],
    topo='h')

T['chefe'] = dict(
    pal=dict(s=(66, 80, 64), d=(44, 56, 44), l=(88, 104, 84), h=(34, 32, 40), H=(22, 20, 28), e=(255, 40, 30), A=(40, 38, 48), a=(24, 22, 30),
             P=(34, 32, 40), p=(22, 20, 28), M=(96, 30, 34), m=(64, 18, 24), G=(196, 156, 60)),
    rosto=["xHxhhxHx",
           "xhXhhXhx",
           "hGGGGGGh",
           "dkkddkkd",
           "skedskes",
           "sdsddsds",
           "xtxttxtx",
           "dxkBBkxd"],
    corpo=["aAGssGAa",
           "AaAGGAaA",
           "AxXkkXxA",
           "AxkxxkxA",
           "aXxkkxXa",
           "AaxttxaA",
           "AXxAAxXA",
           "aAXxxXAa",
           "AAaXXaAA",
           "GGGGGGGG",
           "MmMaAMmM",
           "mMmAamMm"],
    costas=["MAAAAAAM", "MmAaAAmM", "MMmAAmMM", "mMMmmMMm", "MmMMMMmM", "MMmMMmMM", "mMMMmMMm", "MmMMMMmM", "MMmMMMMm", "mMMMmMMM", "MmMmMMmM", "mMmMmmMm"],
    braco=["xXxX", "AXAa", "AaAA", "GGGG", "aAAa", "AAaA", "GGGG", "sdss", "ssds", "sbBs", "xsxs", "xkxk"],
    perna=["PPpP", "PpPP", "PPPp", "GGGG", "PPpP", "PxXP", "PXxP", "pPPP", "GGGG", "PpPP", "pPPP", "PPpP"],
    topo='h', capuz=True)


def rng_de(nome):
    return np.random.default_rng(abs(hash(nome)) % (2 ** 32) if False else sum(ord(c) * (i + 3) for i, c in enumerate(nome)))


def por(img, linhas, x, y, pal, escuro=1.0):
    for j, lin in enumerate(linhas):
        for i, ch in enumerate(lin):
            if ch == '.':
                continue
            c = pal[ch]
            img[y + j, x + i] = (int(c[0] * escuro), int(c[1] * escuro), int(c[2] * escuro), 255)


def lado_de(linhas, larg, rng, pal, troca=None):
    """Lado/costas a partir do desenho da frente: mesma linha, pixels embaralhados, sem os detalhes do meio."""
    out = []
    for lin in linhas:
        base = [ch for ch in lin if ch in (troca or 'AaPpsdlhHMmCc')] or list(lin)
        out.append(''.join(base[int(rng.integers(len(base)))] for _ in range(larg)))
    return out


def camadas(tipo):
    t = T[tipo]
    pal = dict(BASE)
    pal.update(t['pal'])
    rng = rng_de(tipo)
    c1 = np.zeros((32, 64, 4), np.uint8)
    c2 = np.zeros((32, 64, 4), np.uint8)
    # ---- cabeca: frente desenhada; lados e nuca com cabelo/capuz em cima e pele embaixo
    por(c1, t['rosto'], 8, 8, pal)
    if t.get('capuz'):
        lados = [''.join('hH'[int(rng.random() < 0.3)] for _ in range(8)) for _ in range(8)]
        nuca = lados
    else:
        def lateral(orelha):
            ls = []
            for j in range(8):
                if j < 2:
                    ls.append(''.join('hH'[int(rng.random() < 0.3)] for _ in range(8)))
                elif j == 2:
                    ls.append(''.join('hs'[int(rng.random() < 0.5)] for _ in range(8)))
                else:
                    lin = [('s', 'd', 'l')[int(rng.choice(3, p=(0.66, 0.22, 0.12)))] for _ in range(8)]
                    if j == 4:
                        lin[orelha] = 'd'
                        lin[orelha + 1] = 'k' if orelha < 4 else 'd'
                    if j == 6 and rng.random() < 0.8:
                        lin[int(rng.integers(1, 7))] = 'b'
                    ls.append(''.join(lin))
            return ls
        lados = lateral(3)
        nuca = [''.join('hH'[int(rng.random() < 0.3)] for _ in range(8)) for _ in range(4)] + lateral(5)[4:]
        nuca[5] = nuca[5][:3] + 'rb' + nuca[5][5:]
    por(c1, lados, 0, 8, pal, 0.9)
    por(c1, lados[::1], 16, 8, pal, 0.9)
    por(c1, nuca, 24, 8, pal, 0.85)
    topo = [''.join('hH'[int(rng.random() < 0.3)] for _ in range(8)) for _ in range(8)]
    if not t.get('capuz'):
        topo[2] = topo[2][:5] + 'rb' + topo[2][7:]      # cranio aberto
        topo[3] = topo[3][:5] + 'xr' + topo[3][7:]
    por(c1, topo, 8, 0, pal)
    por(c1, [''.join('sd'[int(rng.random() < 0.4)] for _ in range(8)) for _ in range(8)], 16, 0, pal, 0.7)
    # ---- tronco
    por(c1, t['corpo'], 20, 20, pal)
    por(c1, t['costas'], 32, 20, pal, 0.9)
    por(c1, lado_de(t['corpo'], 4, rng, pal), 16, 20, pal, 0.82)
    por(c1, lado_de(t['corpo'], 4, rng, pal), 28, 20, pal, 0.82)
    por(c1, lado_de(t['corpo'][:4], 8, rng, pal), 20, 16, pal)
    por(c1, lado_de(t['corpo'][-4:], 8, rng, pal), 28, 16, pal, 0.7)
    # ---- braco (o esquerdo e o espelho)
    por(c1, t['braco'], 44, 20, pal)
    por(c1, [lin[::-1] for lin in t['braco']], 52, 20, pal, 0.88)
    por(c1, lado_de(t['braco'], 4, rng, pal, 'AaPpsdlhHMmCcxXG'), 40, 20, pal, 0.84)
    por(c1, lado_de(t['braco'], 4, rng, pal, 'AaPpsdlhHMmCcxXG'), 48, 20, pal, 0.84)
    por(c1, lado_de(t['braco'][:4], 4, rng, pal), 44, 16, pal)
    por(c1, ['sdsd', 'dkdk', 'sdsd', 'dkdk'], 48, 16, pal, 0.8)      # mao/garras por baixo
    # ---- sapatos (botas: so a barra de baixo da regiao da perna)
    sap = ['SSzS', 'SzSS', 'zzzz']
    for x in (0, 4, 8, 12):
        por(c1, sap, x, 29, pal, 1.0 if x == 4 else 0.85)
    por(c1, ['zzzz'] * 4, 8, 16, pal)
    # ---- calca (camada 2): pernas inteiras + cintura
    por(c2, t['perna'], 4, 20, pal)
    por(c2, [lin[::-1] for lin in t['perna']], 12, 20, pal, 0.88)
    por(c2, lado_de(t['perna'], 4, rng, pal, 'PpCcMm'), 0, 20, pal, 0.84)
    por(c2, lado_de(t['perna'], 4, rng, pal, 'PpCcMm'), 8, 20, pal, 0.84)
    por(c2, ['PpPP'] * 4, 4, 16, pal)
    por(c2, ['pppp'] * 4, 8, 16, pal, 0.7)
    cint = ['PpPPPPpP', 'zzzGGzzz' if 'G' in pal else 'zzzCCzzz' if 'C' in pal else 'zzzzzzzz', 'PPpPPpPP']
    for x, w in ((20, 8), (32, 8)):
        por(c2, [lin[:w] for lin in cint], x, 29, pal)
    for x in (16, 28):
        por(c2, [lin[:4] for lin in cint], x, 29, pal, 0.85)
    return c1, c2


def ampliar(img, nome):
    """4x com granulado, luz no canto de cima de cada pixel e sombra no de baixo (so onde tem tinta)."""
    rng = rng_de(nome + 'g')
    g = np.repeat(np.repeat(img, K, axis=0), K, axis=1).astype(float)
    h, w = g.shape[:2]
    ru = 0.93 + rng.random((h, w, 1)) * 0.14
    yy, xx = np.mgrid[0:h, 0:w]
    bis = np.ones((h, w, 1))
    bis[(yy % K == 0) | (xx % K == 0)] = 1.06
    bis[(yy % K == K - 1) | (xx % K == K - 1)] = 0.92
    g[..., :3] = np.clip(g[..., :3] * ru * bis, 0, 255)
    return g.astype(np.uint8)


def salvar(caminho, obj):
    os.makedirs(os.path.dirname(caminho), exist_ok=True)
    with open(caminho, 'w', encoding='utf-8') as f:
        json.dump(obj, f, ensure_ascii=False, separators=(',', ':'))


PECAS = {'helmet': ('elmo', 1, 'helmet_layer_visible', 1), 'chestplate': ('peitoral', 1, 'chest_layer_visible', 3),
         'leggings': ('calca', 2, 'leg_layer_visible', 2), 'boots': ('botas', 1, 'boot_layer_visible', 1)}

if __name__ == '__main__':
    import render_armaduras as RA
    J = os.path.join(SAIDA, 'java', 'assets', 'corona')
    B = os.path.join(SAIDA, 'bedrock')
    mapa, item_tex, previas = {}, {}, []
    pele = RA.manequim()
    ic = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    ic.paste((96, 142, 74, 255), (4, 3, 12, 13))
    os.makedirs(os.path.join(B, 'textures', 'items', 'corona', 'tiro'), exist_ok=True)
    ic.save(os.path.join(B, 'textures', 'items', 'corona', 'tiro', 'corona_zumbi_pele.png'))
    item_tex['corona_zumbi_pele'] = {'textures': 'textures/items/corona/tiro/corona_zumbi_pele'}
    for tipo in T:
        c1, c2 = camadas(tipo)
        g1, g2 = ampliar(c1, tipo + '1'), ampliar(c2, tipo + '2')
        for camada, pasta, g in ((1, 'humanoid', g1), (2, 'humanoid_leggings', g2)):
            for p in (os.path.join(J, 'textures', 'entity', 'equipment', pasta, f'z{tipo}.png'), os.path.join(B, 'textures', 'models', 'armor', f'corona_z{tipo}_{camada}.png')):
                os.makedirs(os.path.dirname(p), exist_ok=True)
                Image.fromarray(g, 'RGBA').save(p, optimize=True)
        salvar(os.path.join(J, 'equipment', f'z{tipo}.json'), {'layers': {'humanoid': [{'texture': f'corona:z{tipo}'}], 'humanoid_leggings': [{'texture': f'corona:z{tipo}'}]}})
        for peca, (pt, camada, var, prot) in PECAS.items():
            salvar(os.path.join(J, 'items', 'zumbi', f'{tipo}_{pt}.json'), {'model': {'type': 'minecraft:model', 'model': f'minecraft:item/leather_{peca}'}})
            bid = f'corona:zumbi_{tipo}_{pt}'
            salvar(os.path.join(B, 'attachables', f'zumbi_{tipo}_{pt}.json'), {"format_version": "1.10.0", "minecraft:attachable": {"description": {
                "identifier": bid, "materials": {"default": "armor", "enchanted": "armor_enchanted"},
                "textures": {"default": f'textures/models/armor/corona_z{tipo}_{camada}', "enchanted": "textures/misc/enchanted_actor_glint"},
                "geometry": {"default": f'geometry.player.armor.{peca}'}, "scripts": {"parent_setup": f"variable.{var} = 0.0;"},
                "render_controllers": ["controller.render.armor"]}}})
            mapa.setdefault(f'minecraft:leather_{peca}', []).append({
                'type': 'definition', 'model': f'corona:zumbi/{tipo}_{pt}', 'bedrock_identifier': bid, 'display_name': f'Infectado {tipo} {pt}',
                'bedrock_options': {'icon': 'corona_zumbi_pele', 'creative_category': 'equipment', 'protection_value': prot}})
        for yaw in (32, -40):
            RA.Render.__init__.__defaults__ = (200, 300, 7.0, yaw, 14)
            previas.append(RA.boneco(g1, g2, pele))
    salvar(os.path.join(SAIDA, 'corona-tiro-zumbis.json'), {'format_version': 2, 'items': mapa})
    salvar(os.path.join(SAIDA, 'item_texture_zumbis.json'), item_tex)
    folha = Image.new('RGB', (200 * 6, 600), (38, 40, 48))
    for i, pv in enumerate(previas):
        folha.paste(pv, ((i // 2) * 200, (i % 2) * 300))
    folha.save(os.path.join(SAIDA, 'previa_zumbis.png'))
    print('skins:', len(T))
