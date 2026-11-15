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


HD = {
    'comum': dict(pele=(126, 140, 116), olho=(228, 222, 170), cabelo=(40, 32, 26), camisa=(72, 98, 132), botoes=True, calca=(62, 54, 48), manga=0.36, boca=0.55),
    'corredor': dict(pele=(152, 148, 140), olho=(255, 96, 40), cabelo=(30, 26, 24), capuz=(46, 58, 68), camisa=(46, 58, 68), ziper=True, calca=(74, 62, 50),
                     manga=0.86, faixas=(168, 164, 150), boca=0.85),
    'tanque': dict(pele=(170, 122, 112), olho=(244, 232, 200), cabelo=(40, 30, 26), cabelo_qtd=0.0, torso_nu=True, camisa=(60, 60, 60), calca=(54, 62, 78), manga=0.0, boca=0.7),
    'cuspidor': dict(pele=(140, 152, 98), olho=(222, 255, 120), cabelo=(52, 48, 30), cabelo_qtd=0.6, camisa=(152, 142, 122), barriga=True, bolha=(192, 222, 84),
                     baba=(112, 164, 30), calca=(70, 74, 56), manga=0.3, boca=1.0),
    'explosivo': dict(pele=(152, 134, 106), olho=(255, 172, 60), cabelo=(34, 28, 24), camisa=(196, 156, 44), listras=(34, 32, 30), dinamite=True, calca=(58, 58, 62),
                      manga=0.5, boca=0.6),
    'chefe': dict(pele=(74, 80, 74), olho=(255, 44, 30), cabelo=(20, 18, 18), cabelo_qtd=0.0, camisa=(36, 32, 38), costelas=True, correntes=True, calca=(30, 28, 34),
                  manga=0.9, faixas=(120, 30, 30), boca=0.95),
}


def camadas_hd(tipo):
    import zumbis_hd as Z
    t = HD[tipo]
    sd = sum(ord(c) * (i + 3) for i, c in enumerate(tipo))
    k = Z.K
    c1 = np.zeros((32 * k, 64 * k, 4), np.uint8)
    c2 = np.zeros((32 * k, 64 * k, 4), np.uint8)

    def por(c, img, x, y, f=1.0):
        h, w = img.shape[:2]
        im = img.copy()
        im[..., :3] = np.clip(im[..., :3] * f, 0, 255)
        c[y * k:y * k + h, x * k:x * k + w] = im

    por(c1, Z.rosto(t, sd), 8, 8)
    por(c1, Z.cabeca_lado(t, sd + 1), 0, 8, 0.9)
    por(c1, Z.cabeca_lado(t, sd + 2)[:, ::-1], 16, 8, 0.9)
    por(c1, Z.cabeca_lado(t, sd + 3, nuca=True), 24, 8, 0.85)
    por(c1, Z.cabeca_lado(t, sd + 4, topo=True), 8, 0)
    por(c1, Z.liso(8, 8, t['pele'], sd + 5, 0.6), 16, 0)
    por(c1, Z.tronco(t, sd + 6), 20, 20)
    por(c1, Z.tronco(t, sd + 7, frente=False), 32, 20, 0.9)
    por(c1, Z.tronco(t, sd + 8, frente=False, w=4), 16, 20, 0.84)
    por(c1, Z.tronco(t, sd + 9, frente=False, w=4), 28, 20, 0.84)
    por(c1, Z.liso(8, 4, t['pele'] if t.get('torso_nu') else t['camisa'], sd + 10), 20, 16)
    por(c1, Z.liso(8, 4, t['calca'], sd + 11, 0.7), 28, 16)
    por(c1, Z.braco(t, sd + 12), 44, 20)
    por(c1, Z.braco(t, sd + 13)[:, ::-1], 52, 20, 0.88)
    por(c1, Z.braco(t, sd + 14), 40, 20, 0.84)
    por(c1, Z.braco(t, sd + 15), 48, 20, 0.84)
    por(c1, Z.liso(4, 4, t['pele'] if t.get('torso_nu') or t.get('manga', 0.35) <= 0 else t['camisa'], sd + 16), 44, 16)
    por(c1, Z.liso(4, 4, (96, 12, 14), sd + 17, 0.8), 48, 16)
    for i, x in enumerate((0, 4, 8, 12)):
        por(c1, Z.sapato(sd + 20 + i), x, 29, 1.0 if x == 4 else 0.85)
    por(c1, Z.liso(4, 4, (16, 14, 14), sd + 25), 8, 16)
    por(c2, Z.perna(t, sd + 30), 4, 20)
    por(c2, Z.perna(t, sd + 31)[:, ::-1], 12, 20, 0.88)
    por(c2, Z.perna(t, sd + 32), 0, 20, 0.84)
    por(c2, Z.perna(t, sd + 33), 8, 20, 0.84)
    por(c2, Z.liso(4, 4, t['calca'], sd + 34), 4, 16)
    por(c2, Z.liso(4, 4, t['calca'], sd + 35, 0.6), 8, 16)
    cint = Z.liso(8, 3, t['calca'], sd + 36)
    cint[k:2 * k, :, :3] = (26, 22, 20)
    cint[k:2 * k, 3 * k + k // 2:4 * k + k // 2, :3] = (150, 140, 110)
    por(c2, cint, 20, 29)
    por(c2, cint[:, ::-1], 32, 29, 0.9)
    por(c2, cint[:, :4 * k], 16, 29, 0.85)
    por(c2, cint[:, :4 * k], 28, 29, 0.85)
    import zumbis_relevo as R
    cs = R.cores(t)
    for i, nome in enumerate(R.AMOSTRAS):
        x, y = R.pos(nome)
        am = Z.liso(4, 4, cs[nome], sd + 60 + i)
        am[..., :3] = np.clip(am[..., :3].astype(float) * 1.18, 0, 255)
        por(c1, am, x, y)
    return c1, c2


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
        g1, g2 = camadas_hd(tipo)
        for camada, pasta, g in ((1, 'humanoid', g1), (2, 'humanoid_leggings', g2)):
            for p in (os.path.join(J, 'textures', 'entity', 'equipment', pasta, f'z{tipo}.png'), os.path.join(B, 'textures', 'models', 'armor', f'corona_z{tipo}_{camada}.png')):
                os.makedirs(os.path.dirname(p), exist_ok=True)
                Image.fromarray(g, 'RGBA').save(p, optimize=True)
        salvar(os.path.join(J, 'equipment', f'z{tipo}.json'), {'layers': {'humanoid': [{'texture': f'corona:z{tipo}'}], 'humanoid_leggings': [{'texture': f'corona:z{tipo}'}]}})
        import zumbis_relevo as R
        salvar(os.path.join(J, 'models', 'item', 'zumbi3d', f'{tipo}.json'), R.modelo_java(tipo, f'corona:entity/equipment/humanoid/z{tipo}'))
        salvar(os.path.join(B, 'models', 'entity', f'corona_zumbi_{tipo}_elmo.geo.json'), R.geo_elmo(tipo))
        salvar(os.path.join(B, 'models', 'entity', f'corona_zumbi_{tipo}_peitoral.geo.json'), R.geo_peitoral(tipo))
        for peca, (pt, camada, var, prot) in PECAS.items():
            if peca == 'helmet':
                salvar(os.path.join(J, 'items', 'zumbi', f'{tipo}_{pt}.json'), {'model': {'type': 'minecraft:select', 'property': 'minecraft:display_context',
                       'cases': [{'when': 'head', 'model': {'type': 'minecraft:model', 'model': f'corona:item/zumbi3d/{tipo}'}}],
                       'fallback': {'type': 'minecraft:model', 'model': 'minecraft:item/leather_helmet'}}})
            else:
                salvar(os.path.join(J, 'items', 'zumbi', f'{tipo}_{pt}.json'), {'model': {'type': 'minecraft:model', 'model': f'minecraft:item/leather_{peca}'}})
            geo = {'helmet': f'geometry.corona.zumbi.{tipo}.elmo', 'chestplate': f'geometry.corona.zumbi.{tipo}.peitoral'}.get(peca, f'geometry.player.armor.{peca}')
            bid = f'corona:zumbi_{tipo}_{pt}'
            salvar(os.path.join(B, 'attachables', f'zumbi_{tipo}_{pt}.json'), {"format_version": "1.10.0", "minecraft:attachable": {"description": {
                "identifier": bid, "materials": {"default": "armor", "enchanted": "armor_enchanted"},
                "textures": {"default": f'textures/models/armor/corona_z{tipo}_{camada}', "enchanted": "textures/misc/enchanted_actor_glint"},
                "geometry": {"default": geo}, "scripts": {"parent_setup": f"variable.{var} = 0.0;"},
                "render_controllers": ["controller.render.armor"]}}})
            mapa.setdefault(f'minecraft:leather_{peca}', []).append({
                'type': 'definition', 'model': f'corona:zumbi/{tipo}_{pt}', 'bedrock_identifier': bid, 'display_name': f'Infectado {tipo} {pt}',
                'bedrock_options': {'icon': 'corona_zumbi_pele', 'creative_category': 'equipment', 'protection_value': prot}})
        for yaw in (30, -35):
            r = RA.Render(400, 600, 13.0, yaw, 12)
            k = g1.shape[1] // 64
            r.caixa(g2, k, (16, 16), (-4, 12, -2), (8, 12, 4), 0.5)
            r.caixa(g2, k, (0, 16), (-4, 0, -2), (4, 12, 4), 0.5)
            r.caixa(g2, k, (0, 16), (0, 0, -2), (4, 12, 4), 0.5, espelho=True)
            r.caixa(g1, k, (0, 16), (-4, 0, -2), (4, 12, 4), 1.0)
            r.caixa(g1, k, (0, 16), (0, 0, -2), (4, 12, 4), 1.0, espelho=True)
            r.caixa(g1, k, (16, 16), (-4, 12, -2), (8, 12, 4), 1.0)
            r.caixa(g1, k, (40, 16), (-8, 12, -2), (4, 12, 4), 1.0)
            r.caixa(g1, k, (40, 16), (4, 12, -2), (4, 12, 4), 1.0, espelho=True)
            r.caixa(g1, k, (0, 0), (-4, 24, -4), (8, 8, 8), 1.0)
            for geo in (R.geo_elmo(tipo), R.geo_peitoral(tipo)):
                for bn in geo['minecraft:geometry'][0]['bones']:
                    for c in bn['cubes']:
                        if isinstance(c.get('uv'), dict):
                            RA.cubo_bedrock(r, g1, k, c)
            previas.append(r.imagem())
    salvar(os.path.join(SAIDA, 'corona-tiro-zumbis.json'), {'format_version': 2, 'items': mapa})
    salvar(os.path.join(SAIDA, 'item_texture_zumbis.json'), item_tex)
    folha = Image.new('RGB', (400 * 4, 1800), (38, 40, 48))
    for i, pv in enumerate(previas):
        folha.paste(pv, ((i % 4) * 400, (i // 4) * 600))
    folha.save(os.path.join(SAIDA, 'previa_zumbis.png'))
    print('skins:', len(T))
