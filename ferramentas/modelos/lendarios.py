"""Conjuntos lendarios em 3D (Java + Bedrock): Traje do Rei (MVP/LENDA) e Armadura do Herobrine (+ Lamina do Vazio).

- Casco: as camadas HD de trajes_hd.py (64x32 * 8 = 512x256).
- Pecas em volume (cubos alinhados, iguais no Java e no Bedrock) presas aos ossos do jogador:
  Rei: coroa real com arminho, pontas, arcos, veludo, orbe e cruz; ombreiras de ouro e esmalte com franja; manto
       de veludo com arminho (balanca e abre quando corre); fivelas, cinto, joelheiras, botas com punho de arminho.
  Herobrine: coroa de estilhacos + aureola de cristais girando, olhos de luz, laminas de cristal nas temporas;
       ombreiras com espinhos, nucleo de luz no peito, espinha de estilhacos e capa rasgada do vazio; cinto,
       joelheiras e botas com espinhos.
- Brilho: Java -> "light_emission" nos cubos de luz (elmo e espada); Bedrock -> material entity_emissive_alpha
  (alpha baixo = brilha no escuro, alpha 0 = vazio).
- Manto/capa no Java: camada "wings" do equipamento (o modelo do elitro), desenhada como manto.
- Bedrock: texturas 512x512 (UV 64x64): metade de cima = armadura padrao; metade de baixo = atlas das pecas 3D.
- Coordenadas das pecas: espaco do jogador no Bedrock (px), frente = -z, lado direito do jogador = -x.

uso: python3 -I lendarios.py <saida> [coroa|herobrine ...]
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)
SAIDA = sys.argv[1] if len(sys.argv) > 1 else '/tmp/claude-0/lendarios'
import trajes_hd as TH  # noqa: E402  (le sys.argv[1] como pasta de saida: so cria a pasta)
import kit  # noqa: E402

S = TH.S                     # 8 px HD por pixel padrao
OLHO = TH.OLHO
LUZ_HB = TH.LUZ_HB
TH.MAT.update({
    'arminho': ((0.95, 0.94, 0.90), 0.08, 10, 0.0),
    'pelo_preto': ((0.05, 0.05, 0.06), 0.10, 10, 0.0),
    'perola': ((0.93, 0.92, 0.88), 0.9, 60, 0.0),
    'esmeralda': ((0.05, 0.62, 0.30), 1.30, 90, 0.3),
    'forro': ((0.30, 0.02, 0.05), 0.25, 20, 0.0),
    'pano_hb': ((0.055, 0.045, 0.080), 0.15, 14, 0.0),
})

PECAS = ('elmo', 'peitoral', 'calca', 'botas')
PIVO = {'head': [0, 24, 0], 'body': [0, 24, 0], 'rightArm': [-5, 22, 0], 'leftArm': [5, 22, 0],
        'rightLeg': [-1.9, 12, 0], 'leftLeg': [1.9, 12, 0]}
ESPELHO_OSSO = {'rightArm': 'leftArm', 'leftArm': 'rightArm', 'rightLeg': 'leftLeg', 'leftLeg': 'rightLeg'}
# ossos extras: nome -> (pai, pivo)
EXTRAS = {'halo': ('head', [0, 40.0, 0]), 'manto': ('body', [0, 24.6, 3.6])}
# casco de cada peca (igual ao geometry.player.armor.* do jogo)
CASCO = {
    'elmo': [('head', {'origin': [-4, 24, -4], 'size': [8, 8, 8], 'uv': [0, 0], 'inflate': 1.0})],
    'peitoral': [('body', {'origin': [-4, 12, -2], 'size': [8, 12, 4], 'uv': [16, 16], 'inflate': 1.01}),
                 ('rightArm', {'origin': [-8, 12, -2], 'size': [4, 12, 4], 'uv': [40, 16], 'inflate': 1.0}),
                 ('leftArm', {'origin': [4, 12, -2], 'size': [4, 12, 4], 'uv': [40, 16], 'inflate': 1.0, 'mirror': True})],
    'calca': [('body', {'origin': [-4, 12, -2], 'size': [8, 12, 4], 'uv': [16, 16], 'inflate': 0.5}),
              ('rightLeg', {'origin': [-3.9, 0, -2], 'size': [4, 12, 4], 'uv': [0, 16], 'inflate': 0.5}),
              ('leftLeg', {'origin': [-0.1, 0, -2], 'size': [4, 12, 4], 'uv': [0, 16], 'inflate': 0.5, 'mirror': True})],
    'botas': [('rightLeg', {'origin': [-3.9, 0, -2], 'size': [4, 12, 4], 'uv': [0, 16], 'inflate': 1.0}),
              ('leftLeg', {'origin': [-0.1, 0, -2], 'size': [4, 12, 4], 'uv': [0, 16], 'inflate': 1.0, 'mirror': True})],
}
CAMADA = {'elmo': 'elmo', 'peitoral': '1', 'calca': '2', 'botas': '1'}
VISIVEL = {'elmo': 'helmet', 'peitoral': 'chest', 'calca': 'leg', 'botas': 'boot'}


# ================================================================== pecas
def C(osso, x0, x1, y0, y1, z0, z1, mat, brilho=0, nome=None):
    return kit.Cubo(nome or f'{osso}_{mat}', (min(x0, x1), min(y0, y1), min(z0, z1)), (max(x0, x1), max(y0, y1), max(z0, z1)),
                    mat, grupo=osso, emite=brilho)


def espelho(lista):
    out = []
    for c in lista:
        out.append(c)
        m = c.com()
        m.de = np.array([-c.ate[0], c.de[1], c.de[2]])
        m.ate = np.array([-c.de[0], c.ate[1], c.ate[2]])
        m.grupo = ESPELHO_OSSO.get(c.grupo, c.grupo)
        m.nome = c.nome + '_e'
        m.uv = {}
        out.append(m)
    return out


def estilhaco(osso, cx, cz, y0, altura, larg, mat='obs', ponta='cristal', brilho=12, inclina=(0.0, 0.0), n=3, fim=0.42):
    """Estilhaco: prismas empilhados afinando; a ponta brilha. altura < 0 = pendurado para baixo."""
    cs = []
    h = altura / n
    for i in range(n):
        f0 = i / n
        w = larg * (1 - (1 - fim) * f0)
        ya, yb = y0 + h * i, y0 + h * (i + 1)
        ox = cx + inclina[0] * (ya - y0)
        oz = cz + inclina[1] * (ya - y0)
        m, b = (ponta, brilho) if i == n - 1 else (mat, 0)
        cs.append(C(osso, ox - w / 2, ox + w / 2, ya, yb, oz - w / 2, oz + w / 2, m, b))
    return cs


def anel(osso, y0, y1, r_dentro, r_fora, mat, brilho=0):
    """Aro quadrado em volta da cabeca (frente, tras e lados)."""
    return [C(osso, -r_fora, r_fora, y0, y1, -r_fora, -r_dentro, mat, brilho),
            C(osso, -r_fora, r_fora, y0, y1, r_dentro, r_fora, mat, brilho),
            C(osso, -r_fora, -r_dentro, y0, y1, -r_dentro, r_dentro, mat, brilho),
            C(osso, r_dentro, r_fora, y0, y1, -r_dentro, r_dentro, mat, brilho)]


def pecas_herobrine(peca):
    P = []
    if peca == 'elmo':
        P.append(C('head', -5.5, 5.5, 29.2, 30.0, -5.65, -5.0, 'obs2'))                       # sobrancelha
        P += espelho([C('head', -4.0, -0.7, 27.45, 29.15, -5.4, -5.0, 'olho', 15),              # olhos de luz
                      C('head', -5.65, -5.0, 23.4, 28.4, -5.0, -1.4, 'obs'),                   # protetor da face
                      C('head', -6.2, -5.0, 28.0, 30.0, -1.2, 1.4, 'obs2'),                    # laminas das temporas
                      C('head', -6.8, -5.6, 29.6, 31.8, 1.0, 3.6, 'obs2'),
                      C('head', -7.2, -6.2, 31.4, 33.6, 3.0, 5.4, 'obs'),
                      C('head', -7.5, -6.7, 33.2, 35.4, 4.8, 6.8, 'cristal', 12),
                      C('head', -7.6, -7.0, 35.0, 36.6, 6.4, 7.6, 'luz', 15)])
        # coroa de estilhacos na borda de cima (inclinados para fora)
        for cx, cz, h, w in ((0.0, -4.55, 6.2, 1.9), (2.7, -4.5, 4.3, 1.45), (-2.7, -4.5, 4.3, 1.45), (4.4, -3.9, 3.4, 1.3),
                             (-4.4, -3.9, 3.4, 1.3), (4.6, -0.9, 3.0, 1.25), (-4.6, -0.9, 3.0, 1.25), (4.5, 2.1, 2.5, 1.15),
                             (-4.5, 2.1, 2.5, 1.15), (2.6, 4.5, 2.4, 1.15), (-2.6, 4.5, 2.4, 1.15), (0.0, 4.6, 2.9, 1.3)):
            P += estilhaco('head', cx, cz, 32.6, h, w, inclina=(cx * 0.035, cz * 0.035), brilho=12)
        # aureola: cristais flutuando (giram no Bedrock)
        for k in range(6):
            a = k * math.pi / 3 + 0.3
            x, z = math.cos(a) * 3.0, math.sin(a) * 3.0
            alto = 1.8 if k % 2 == 0 else 1.2
            P.append(C('halo', x - 0.45, x + 0.45, 39.4, 39.4 + alto, z - 0.45, z + 0.45, 'cristal', 14, nome=f'halo{k}'))
            P.append(C('halo', x - 0.25, x + 0.25, 39.4 + alto, 40.0 + alto, z - 0.25, z + 0.25, 'luz', 15, nome=f'halo{k}p'))
    elif peca == 'peitoral':
        omb = [C('rightArm', -9.8, -3.4, 23.1, 25.8, -3.7, 3.7, 'obs2'),
               C('rightArm', -10.0, -3.6, 22.8, 23.1, -3.75, 3.75, 'luz', 13, nome='costura'),
               C('rightArm', -10.3, -6.0, 21.0, 22.8, -3.5, 3.5, 'obs'),
               C('rightArm', -10.5, -7.6, 19.3, 21.0, -3.2, 3.2, 'fenda')]
        omb += estilhaco('rightArm', -8.0, -1.5, 25.8, 4.6, 1.6, inclina=(-0.22, 0.0))
        omb += estilhaco('rightArm', -6.4, 0.7, 25.8, 3.5, 1.3, inclina=(-0.12, 0.05))
        omb += estilhaco('rightArm', -7.7, 2.6, 25.8, 2.7, 1.1, inclina=(-0.18, 0.1))
        P += espelho(omb)
        # nucleo de luz no peito
        P += [C('body', -2.1, 2.1, 17.5, 21.7, -3.5, -3.0, 'obs2'),
              C('body', -1.25, 1.25, 18.35, 20.85, -3.95, -3.45, 'luz', 15, nome='nucleo'),
              C('body', -0.5, 0.5, 21.7, 23.2, -3.6, -3.1, 'cristal', 10),
              C('body', -0.5, 0.5, 16.0, 17.5, -3.6, -3.1, 'cristal', 10)]
        P += espelho([C('body', 2.1, 4.6, 19.45, 19.85, -3.25, -3.0, 'luz', 12, nome='veia')])
        # espinha de estilhacos nas costas
        P += estilhaco('body', 0.0, 3.5, 20.6, 5.4, 1.9, inclina=(0.0, 0.42), n=4, brilho=12)
        P += espelho(estilhaco('body', -2.3, 3.4, 18.8, 3.8, 1.35, inclina=(-0.18, 0.4), brilho=11))
        P += estilhaco('body', 0.0, 3.4, 15.4, 3.2, 1.35, inclina=(0.0, 0.5), brilho=11)
        # capa rasgada do vazio (balanca no Bedrock): painel inteiro + tiras rasgadas embaixo
        P.append(C('manto', -4.3, 4.3, 23.2, 25.2, 2.4, 4.3, 'obs2', nome='gola'))
        P.append(C('manto', -4.2, 4.2, 10.6, 23.4, 3.7, 4.15, 'pano_hb', nome='capa'))
        fundo = (7.2, 5.4, 8.4, 6.2, 4.8, 7.6)
        larg = 8.4 / len(fundo)
        for k, y_fim in enumerate(fundo):
            x0 = -4.2 + k * larg
            P.append(C('manto', x0 + 0.08, x0 + larg - 0.08, y_fim, 10.7, 3.72, 4.13, 'pano_hb', nome=f'tira{k}'))
    elif peca == 'calca':
        P += [C('body', -4.75, 4.75, 11.3, 13.0, -2.85, 2.85, 'obs2', nome='cinto'),
              C('body', -0.85, 0.85, 11.5, 12.8, -3.2, -2.85, 'luz', 14, nome='fivela')]
        P += espelho([C('rightLeg', -4.3, -0.5, 8.0, 11.4, -2.95, -2.55, 'fenda'),
                      C('rightLeg', -4.8, -4.4, 7.4, 11.4, -2.2, 2.2, 'obs'),
                      C('rightLeg', -3.5, -0.8, 5.0, 7.5, -3.0, -2.55, 'obs2'),
                      C('rightLeg', -2.65, -1.65, 5.75, 6.75, -3.35, -3.0, 'luz', 13)])
    elif peca == 'botas':
        bota = [C('rightLeg', -4.25, 0.45, -1.0, 1.6, -3.75, -2.95, 'obs2'),
                C('rightLeg', -2.5, -1.3, -0.7, 0.9, -4.7, -3.75, 'cristal', 10),
                C('rightLeg', -2.4, -1.3, 0.0, 1.5, 3.0, 4.3, 'obs')]
        bota += estilhaco('rightLeg', -5.3, 0.2, 2.0, 3.8, 1.25, inclina=(-0.12, 0.18), brilho=11)
        P += espelho(bota)
    return P


def pecas_rei(peca):
    P = []
    if peca == 'elmo':
        P += anel('head', 32.0, 33.5, 4.85, 5.65, 'arminho')                                   # arminho
        P += anel('head', 33.5, 34.9, 4.8, 5.5, 'ouro')                                        # aro de ouro
        P += [C('head', -0.9, 0.9, 33.65, 34.75, -5.9, -5.45, 'rubi', 9, nome='rubi_f'),
              C('head', -0.9, 0.9, 33.65, 34.75, 5.45, 5.9, 'rubi', 9, nome='rubi_t')]
        P += espelho([C('head', -5.9, -5.45, 33.65, 34.75, -0.9, 0.9, 'esmeralda', 9, nome='esm'),
                      C('head', -3.3, -2.3, 33.8, 34.6, -5.75, -5.45, 'diamante', 8, nome='diam'),
                      C('head', -3.3, -2.3, 33.8, 34.6, 5.45, 5.75, 'diamante', 8, nome='diam_t')])
        # pontas: flor-de-lis (altas) e perolas (baixas)
        def flor(x, z, folhas=True):
            cs = [C('head', x - 0.8, x + 0.8, 34.9, 36.3, z - 0.4, z + 0.4, 'ouro'),
                  C('head', x - 0.45, x + 0.45, 36.3, 37.4, z - 0.3, z + 0.3, 'ouro'),
                  C('head', x - 0.45, x + 0.45, 37.4, 38.2, z - 0.45, z + 0.45, 'perola')]
            if folhas:
                dx, dz = (1.2, 0) if abs(z) > abs(x) else (0, 1.2)
                for s in (-1, 1):
                    cx, cz = x + s * dx, z + s * dz
                    cs.append(C('head', cx - 0.4, cx + 0.4, 35.4, 36.6, cz - 0.3 if dz == 0 else cz - 0.4,
                                cz + 0.3 if dz == 0 else cz + 0.4, 'ouro2'))
            return cs

        def perola(x, z):
            return [C('head', x - 0.3, x + 0.3, 34.9, 35.9, z - 0.25, z + 0.25, 'ouro2'),
                    C('head', x - 0.45, x + 0.45, 35.9, 36.7, z - 0.45, z + 0.45, 'perola')]
        for x, z in ((0, -5.15), (0, 5.15), (-5.15, 0), (5.15, 0)):
            P += flor(x, z)
        for x, z in ((-5.1, -5.1), (5.1, -5.1), (-5.1, 5.1), (5.1, 5.1)):
            P += flor(x, z, folhas=False)
        for x, z in ((-2.7, -5.15), (2.7, -5.15), (-2.7, 5.15), (2.7, 5.15), (-5.15, -2.7), (5.15, -2.7)):
            P += perola(x, z)
        # asas de ouro fixas nos lados da coroa (varrendo para tras e para cima)
        asa = [C('head', -6.35, -5.45, 31.4, 34.9, 1.9, 3.5, 'ouro', nome='asa_raiz'),
               C('head', -6.55, -6.15, 32.6, 33.8, 2.3, 3.1, 'rubi', 8, nome='asa_rubi')]
        for k in range(4):
            asa.append(C('head', -6.25 + k * 0.05, -5.65 - k * 0.05, 33.0 + k * 0.65, 35.7 + k * 1.25, 2.5 + k * 0.85, 4.6 + k * 1.35,
                         'pena_ouro', nome=f'pena{k}'))
        P += espelho(asa)
        # barrete de veludo e os dois arcos cruzados
        P += [C('head', -4.6, 4.6, 33.4, 36.8, -4.6, 4.6, 'veludo', nome='barrete'),
              C('head', -3.5, 3.5, 36.8, 38.1, -3.5, 3.5, 'veludo', nome='barrete2')]
        for a, b, y0, y1 in ((4.2, 5.25, 34.9, 37.3), (3.0, 4.5, 37.0, 38.4), (1.4, 3.3, 38.1, 39.2), (0, 1.6, 38.9, 39.7)):
            for s in (-1, 1):
                z0, z1 = sorted((s * a, s * b))
                P.append(C('head', -0.55, 0.55, y0, y1, z0, z1, 'ouro', nome='arco_z'))
                P.append(C('head', z0, z1, y0, y1, -0.55, 0.55, 'ouro', nome='arco_x'))
        P += [C('head', -0.95, 0.95, 39.7, 41.1, -0.95, 0.95, 'ouro', nome='orbe'),
              C('head', -0.45, 0.45, 40.0, 40.8, -1.2, -0.95, 'rubi', 9, nome='orbe_rubi'),
              C('head', -0.32, 0.32, 41.1, 42.9, -0.32, 0.32, 'ouro', nome='cruz'),
              C('head', -0.95, 0.95, 41.85, 42.35, -0.28, 0.28, 'ouro', nome='cruz2'),
              C('head', -0.22, 0.22, 41.9, 42.3, -0.42, -0.28, 'diamante', 10, nome='cruz_diam')]
    elif peca == 'peitoral':
        omb = [C('rightArm', -9.2, -4.0, 25.4, 26.3, -2.9, 2.9, 'branco', nome='omb_topo'),     # cupula em degraus
               C('rightArm', -9.8, -3.4, 23.6, 25.4, -3.6, 3.6, 'ouro', nome='omb1'),
               C('rightArm', -7.2, -5.8, 26.3, 26.9, -3.1, 3.1, 'ouro2', nome='omb_crista'),   # crista
               C('rightArm', -10.15, -3.5, 23.0, 23.6, -3.75, 3.75, 'ouro2', nome='omb_aro'),
               C('rightArm', -10.3, -6.0, 21.3, 23.0, -3.45, 3.45, 'branco', nome='omb2'),
               C('rightArm', -10.5, -7.4, 19.7, 21.3, -3.15, 3.15, 'ouro', nome='omb3'),
               C('rightArm', -10.75, -10.3, 21.6, 22.7, -0.6, 0.6, 'rubi', 9, nome='rubi_omb'),
               C('rightArm', -7.0, -6.0, 25.6, 26.6, -3.25, -2.9, 'rubi', 8, nome='rubi_omb_f')]
        for z in (-2.4, -0.8, 0.8, 2.4):
            omb.append(C('rightArm', -10.35, -9.85, 18.0, 19.6, z - 0.28, z + 0.28, 'ouro2', nome='franja'))
        P += espelho(omb)
        P += espelho([C('body', -4.4, -2.6, 22.1, 23.9, -3.5, -3.0, 'ouro', nome='fivela'),
                      C('body', -3.9, -3.1, 22.6, 23.4, -3.8, -3.5, 'rubi', 8, nome='fivela_rubi')])
        P.append(C('body', -2.6, 2.6, 22.85, 23.25, -3.3, -3.05, 'ouro2', nome='corrente'))
        # manto real (entre as asas)
        P += [C('manto', -4.1, 4.1, 23.2, 25.3, 2.3, 4.6, 'arminho', nome='gola'),
              C('manto', -4.1, 4.1, 6.0, 23.4, 3.6, 4.2, 'manto_rei', nome='manto'),
              C('manto', -4.3, 4.3, 4.5, 6.2, 3.4, 4.45, 'arminho', nome='barra')]
    elif peca == 'calca':
        P += [C('body', -4.75, 4.75, 11.3, 13.0, -2.85, 2.85, 'ouro', nome='cinto'),
              C('body', -1.0, 1.0, 11.45, 12.85, -3.2, -2.85, 'rubi', 8, nome='fivela')]
        P += espelho([C('rightLeg', -4.3, -0.5, 7.6, 11.4, -2.95, -2.55, 'branco'),
                      C('rightLeg', -4.8, -4.4, 8.0, 11.4, -2.0, 2.0, 'ouro2'),
                      C('rightLeg', -3.5, -0.8, 5.0, 7.3, -3.0, -2.55, 'ouro')])
    elif peca == 'botas':
        P += espelho([C('rightLeg', -4.3, 0.5, -1.0, 1.4, -3.65, -2.95, 'ouro'),
                      C('rightLeg', -5.0, 1.2, 4.0, 5.2, -3.15, 3.15, 'arminho', nome='punho'),
                      C('rightLeg', -2.05, -1.15, 4.15, 5.05, -3.45, -3.15, 'rubi', 8, nome='punho_rubi'),
                      C('rightLeg', -1.95, -1.35, 0.55, 1.15, 3.0, 4.0, 'ouro2', nome='espora'),
                      C('rightLeg', -2.1, -1.2, 0.35, 1.35, 3.95, 4.55, 'ouro', nome='roseta')])
    return P


PECAS_DE = {'coroa': pecas_rei, 'herobrine': pecas_herobrine}


# ================================================================== pintura das faces (HD, com relevo)
def pintar_face(c, f, wpx, hpx, sem):
    """Pinta uma face (wpx x hpx pixels) do cubo c no material c.pele. Devolve (rgba uint8, brilho 0..1)."""
    if wpx < 4 or hpx < 4:            # face muito fina: pinta maior e reduz
        img, b = pintar_face(c, f, max(wpx, 4), max(hpx, 4), sem)
        img = np.array(Image.fromarray(img, 'RGBA').resize((wpx, hpx), Image.BOX))
        b = np.array(Image.fromarray((b * 255).astype(np.uint8), 'L').resize((wpx, hpx), Image.BOX)) / 255.0
        return img, b
    w, h = wpx / S, hpx / S
    F = TH.Face(w, h, sem)
    tudo = F.ret(0, 0, w, h)
    mat = c.pele
    rng = np.random.default_rng(sem)
    grande = min(w, h) >= 1.6
    if mat in ('obs', 'obs2', 'fenda'):
        TH.obsidiana(F, tudo, sem, base='obs2' if mat == 'obs2' else 'obs', alt=0.35, bisel=0.22, celula=0.8)
        if mat == 'fenda' or (grande and rng.random() < 0.35):
            if w >= h:
                TH.rachadura(F, 0.2, h * rng.uniform(0.3, 0.7), w - 0.2, h * rng.uniform(0.3, 0.7), rng, (0.1, 0.1, w - 0.1, h - 0.1),
                             galhos=1, forca=0.9, larg=0.06)
            else:
                TH.rachadura(F, w * rng.uniform(0.3, 0.7), 0.2, w * rng.uniform(0.3, 0.7), h - 0.2, rng, (0.1, 0.1, w - 0.1, h - 0.1),
                             galhos=1, forca=0.9, larg=0.06)
    elif mat == 'cristal':
        TH.obsidiana(F, tudo, sem, base='obs2', alt=0.25, bisel=0.18, celula=0.6)
        k = np.clip(1 - F.y / max(h, 0.5), 0, 1) ** 1.6
        F.emis += (k * 0.85)[..., None] * LUZ_HB[1][None, None, :] + (k ** 3 * 0.6)[..., None] * LUZ_HB[0][None, None, :]
        F.luz(np.abs(F.x - w * 0.5) - 0.04, LUZ_HB[0], LUZ_HB[1], forca=0.5, largura=0.0, halo=0.3)
    elif mat == 'luz':
        F.pintar(tudo, 'obs2', alt=0.2, bisel=0.1)
        F.luz(tudo + 0.12, LUZ_HB[0], LUZ_HB[1], forca=1.4, largura=0.0, halo=0.4)
    elif mat == 'olho':
        F.pintar(tudo, 'obs0')
        F.luz(tudo + 0.08, OLHO, LUZ_HB[1], forca=2.0, largura=0.0, halo=0.3)
    elif mat == 'pano_hb':
        F.pintar(tudo, 'pano_hb', alt=0.0, ruido=0.14, escala_ruido=1.2)
        F.alb *= (1.0 + 0.9 * TH.fbm(F.x * 0.5, F.y * 0.35, sem * 0.2, oct=3, seed=sem)[..., None])
        F.alt += 0.22 * np.sin(F.x * 2.4 + np.sin(F.y * 0.5) * 0.9)              # dobras
        if f in ('north', 'south') and w > 1.0:
            for _ in range(2 if w > 4 else 1):
                TH.rachadura(F, w * rng.uniform(0.2, 0.8), 0.3, w * rng.uniform(0.2, 0.8), h - 0.4, rng, (0.05, 0.05, w - 0.05, h - 0.05),
                             galhos=2, forca=0.7, larg=0.045)
        if c.nome.startswith('tira') and f in ('north', 'south'):
            serra = 0.45 + 0.4 * np.abs(np.sin(F.x * 5.3 + sem)) + 0.2 * np.sin(F.x * 11.7)
            F.luz(np.abs(F.y - (h - serra)) - 0.06, LUZ_HB[0], LUZ_HB[1], forca=0.8, largura=0.0, halo=0.4)
            F.a[F.y > h - serra] = 0
    elif mat == 'pena_ouro':
        F.pintar(tudo, 'ouro', alt=0.45, bisel=0.25)
        haste = np.abs(F.y - (h * 0.55 - (F.x - w / 2) * 0.15)) < 0.07
        barbas = (((F.x * 1.7 + F.y * 1.1) % 0.9) < 0.18) & ~haste
        F.cavar(np.where(barbas, -0.05, 0.3), 0.06, 0.05)
        F.alb[haste] = np.array(TH.MAT['ouro2'][0])
        borda = F.ret(0, 0, w, h)
        F.friso(borda, 'branco', min(0.3, min(w, h) * 0.18), 0.15)
    elif mat in ('ouro', 'ouro2'):
        F.pintar(tudo, mat, alt=0.5, bisel=0.22)
        if grande:
            F.cavar(F.ret(0.35, 0.35, w - 0.35, h - 0.35, 0.15), 0.08, 0.04)
            F.filigrana(F.ret(0.4, 0.4, w - 0.4, h - 0.4, 0.2), margem=0.15, escala=0.7, fase=sem * 0.3)
    elif mat in ('branco', 'branco2'):
        F.pintar(tudo, mat, alt=0.4, bisel=0.3)
        if grande:
            F.friso(tudo, 'ouro', min(0.45, min(w, h) * 0.2), 0.25)
            F.filigrana(F.ret(0.5, 0.5, w - 0.5, h - 0.5, 0.2), margem=0.2, escala=0.8, fase=sem * 0.5)
    elif mat == 'veludo':
        F.pintar(tudo, 'veludo', alt=0.1, bisel=0.1, ruido=0.08)
        F.bordado(tudo, passo=1.2)
    elif mat == 'arminho':
        F.pintar(tudo, 'arminho', alt=0.3, bisel=0.4, ruido=0.18, escala_ruido=6.0)
        F.alt += 0.12 * (TH.fbm(F.x * 5, F.y * 5, sem * 0.1, oct=3, seed=sem) - 0.5)
        # rabinhos pretos do arminho
        passo = 1.6
        cx = (np.floor(F.x / passo) + 0.5) * passo + (np.floor(F.y / passo) % 2) * passo * 0.5
        cy = (np.floor(F.y / passo) + 0.5) * passo
        d = np.hypot((F.x - cx) / 0.22, (F.y - cy) / 0.42) - 1
        F.pintar(d, 'pelo_preto', alt=0.05, bisel=0.05)
    elif mat in ('rubi', 'diamante', 'esmeralda'):
        F.pintar(tudo, 'ouro', alt=0.3, bisel=0.15)
        r = min(w, h) * 0.42
        F.joia(w / 2, h / 2, r, mat, brilho=0.9)
    elif mat == 'perola':
        F.pintar(tudo, 'perola', alt=0.6, bisel=min(w, h) * 0.45)
        dome = np.clip(1 - ((F.x - w * 0.4) ** 2 + (F.y - h * 0.35) ** 2) / (min(w, h) * 0.25) ** 2, 0, 1)
        F.emis += (dome * 0.35)[..., None] * np.ones(3)[None, None, :]
    elif mat == 'manto_rei':
        if f == 'south':                      # lado de fora: veludo bordado, moldura de ouro e perolas, brasao
            F.pintar(tudo, 'veludo', alt=0.05, bisel=0.05, ruido=0.07)
            F.alt += 0.10 * np.sin(F.x * 2.2 + 0.6 * np.sin(F.y * 0.5))       # dobras do tecido
            F.bordado(F.ret(0.9, 0.9, w - 0.9, h - 0.9), passo=1.35)
            F.friso(tudo, 'ouro', 0.55, 0.28)
            F.perolas(F.ret(0.55, 0.55, w - 0.55, h - 0.55), 0.4, passo=0.6)
            # brasao: coroa de ouro com rubi
            cx, cy = w / 2, 3.6
            coroa = F.poli([(cx - 2.0, cy + 1.2), (cx + 2.0, cy + 1.2), (cx + 2.3, cy - 1.2), (cx + 1.1, cy - 0.2), (cx, cy - 1.7),
                            (cx - 1.1, cy - 0.2), (cx - 2.3, cy - 1.2)])
            F.pintar(coroa + 0.12, 'ouro2', alt=0.5, bisel=0.12)
            F.pintar(coroa, 'ouro', alt=0.75, bisel=0.2)
            F.joia(cx, cy + 0.5, 0.42, 'rubi', brilho=0.5)
            for dx in (-1.25, 1.25):
                F.joia(cx + dx, cy + 0.6, 0.24, 'diamante', brilho=0.35)
            for k in range(3):                # flores-de-lis de ouro descendo
                F.pintar(F.circ(cx, 7.5 + k * 3.4, 0.45), 'ouro', alt=0.4, bisel=0.2)
                F.pintar(F.circ(cx - 0.55, 7.2 + k * 3.4, 0.3), 'ouro', alt=0.4, bisel=0.2)
                F.pintar(F.circ(cx + 0.55, 7.2 + k * 3.4, 0.3), 'ouro', alt=0.4, bisel=0.2)
        elif f == 'north':                    # forro
            F.pintar(tudo, 'forro', alt=0.05, ruido=0.05)
            F.alt += 0.08 * np.sin(F.x * 2.2)
        else:
            F.pintar(tudo, 'ouro', alt=0.3, bisel=0.1)
    else:
        F.pintar(tudo, mat if mat in TH.MAT else 'obs', alt=0.3, bisel=0.2)
    img = F.render()
    brilho = np.clip(np.max(F.emis, axis=2), 0, 1.2) / 1.2
    if getattr(c, 'emite', 0):
        brilho = np.maximum(brilho, 0.25)
    return img, brilho


def montar_atlas(cubos, largura_px, y0_px, altura_max_px, escala=S, semente=0):
    """Distribui as faces no atlas (prateleiras) a partir de y0_px. Devolve (rgba, brilho, escala usada)."""
    for e in (escala, 7, 6, 5, 4, 3):
        h = kit.arrumar_uv(cubos, largura_px, e)
        if h <= altura_max_px:
            break
    else:
        raise RuntimeError('pecas nao cabem no atlas')
    img = np.zeros((altura_max_px, largura_px, 4), np.uint8)
    bri = np.zeros((altura_max_px, largura_px))
    for i, c in enumerate(cubos):
        for f in c.faces():
            x0, y0, x1, y1 = c.uv[f]
            rgba, b = pintar_face(c, f, x1 - x0, y1 - y0, semente + i * 7 + len(f))
            img[y0:y1, x0:x1] = rgba[:y1 - y0, :x1 - x0]
            bri[y0:y1, x0:x1] = b[:y1 - y0, :x1 - x0]
    for c in cubos:
        c.uv = {f: (x0, y0 + y0_px, x1, y1 + y0_px) for f, (x0, y0, x1, y1) in c.uv.items()}
    return img, bri, e


def alfa_binario(rgba):
    out = rgba.copy()
    out[..., 3] = np.where(out[..., 3] >= 128, 255, 0)
    return out


def alfa_brilho(rgba, brilho, forca=200):
    """Bedrock entity_emissive_alpha: alpha 255 = normal, mais baixo = brilha, 0 = vazio."""
    out = alfa_binario(rgba)
    vis = out[..., 3] > 0
    a = 255 - np.round(np.clip(brilho, 0, 1) * forca)
    out[..., 3] = np.where(vis, np.clip(a, 40, 255), 0).astype(np.uint8)
    return out


# ================================================================== camadas HD (casco) com mapa de brilho
def camadas(conj):
    """(camada1, camada2, brilho1, brilho2): RGBA 512x256 e brilho 0..1 por pixel (do 'emis' das faces)."""
    orig = TH.Camada.colar

    def colar(self, f, u, v):
        orig(self, f, u, v)
        if not hasattr(self, 'brilho'):
            self.brilho = np.zeros(self.im.shape[:2])
        y0, x0 = int(v * S), int(u * S)
        b = np.clip(np.max(f.emis, axis=2), 0, 1.2) / 1.2
        self.brilho[y0:y0 + b.shape[0], x0:x0 + b.shape[1]] = b
    TH.Camada.colar = colar
    try:
        c1, c2 = TH.montar(conj)
    finally:
        TH.Camada.colar = orig
    return c1.im, c2.im, c1.brilho, c2.brilho


# ================================================================== manto do Java (camada "wings" = modelo do elitro)
def manto_java(conj):
    """Textura 64x32*8 do elitro pintada como manto (cada asa do elitro = meia capa, a outra e espelhada)."""
    im = np.zeros((32 * S, 64 * S, 4), np.uint8)
    rng = np.random.default_rng(9 if conj == 'coroa' else 13)

    def face(u, v, w, h, quem, sem):
        F = TH.Face(w, h, sem)
        tudo = F.ret(0, 0, w, h)
        if conj == 'coroa':
            if quem == 'fora':
                F.pintar(tudo, 'veludo', alt=0.05, bisel=0.05, ruido=0.07)
                F.alt += 0.12 * np.sin(F.x * 1.9 + 0.5 * np.sin(F.y * 0.45))
                F.bordado(F.ret(0.9, 0.9, w - 0.9, h - 2.6), passo=1.35)
                F.friso(F.ret(0, 0, w, h - 2.2), 'ouro', 0.55, 0.28)
                F.perolas(F.ret(0.55, 0.55, w - 0.55, h - 2.75), 0.4, passo=0.6)
                cx, cy = w / 2, 4.2
                coroa = F.poli([(cx - 2.2, cy + 1.3), (cx + 2.2, cy + 1.3), (cx + 2.5, cy - 1.3), (cx + 1.2, cy - 0.2), (cx, cy - 1.8),
                                (cx - 1.2, cy - 0.2), (cx - 2.5, cy - 1.3)])
                F.pintar(coroa + 0.12, 'ouro2', alt=0.5, bisel=0.12)
                F.pintar(coroa, 'ouro', alt=0.75, bisel=0.2)
                F.joia(cx, cy + 0.5, 0.45, 'rubi', brilho=0.5)
                for k in range(3):
                    yy = 8.6 + k * 3.2
                    for dx, r in ((0, 0.45), (-0.55, 0.3), (0.55, 0.3)):
                        F.pintar(F.circ(cx + dx, yy - (0.3 if dx else 0), r), 'ouro', alt=0.4, bisel=0.2)
                barra = F.ret(-1, h - 2.2, w + 1, h + 1)               # barra de arminho embaixo
                F.pintar(barra, 'arminho', alt=0.35, bisel=0.3, ruido=0.18, escala_ruido=6.0)
                passo = 1.6
                gx = (np.floor(F.x / passo) + 0.5) * passo + (np.floor(F.y / passo) % 2) * passo * 0.5
                gy = (np.floor(F.y / passo) + 0.5) * passo
                d = np.maximum(np.hypot((F.x - gx) / 0.22, (F.y - gy) / 0.42) - 1, barra)
                F.pintar(d, 'pelo_preto', alt=0.05, bisel=0.05)
            elif quem == 'dentro':
                F.pintar(tudo, 'forro', alt=0.05, ruido=0.05)
                F.pintar(F.ret(-1, h - 2.2, w + 1, h + 1), 'arminho', alt=0.3, bisel=0.3, ruido=0.18, escala_ruido=6.0)
            else:
                F.pintar(tudo, 'ouro', alt=0.3, bisel=0.1)
        else:
            F.pintar(tudo, 'pano_hb', alt=0.0, ruido=0.12)
            F.alt += 0.2 * np.sin(F.x * 2.6 + np.sin(F.y * 0.6) * 0.9)
            if quem in ('fora', 'dentro'):
                for _ in range(3 if quem == 'fora' else 1):
                    TH.rachadura(F, w * rng.uniform(0.15, 0.85), 0.4, w * rng.uniform(0.1, 0.9), h * rng.uniform(0.6, 0.95), rng,
                                 (0.05, 0.05, w - 0.05, h - 0.05), galhos=2, forca=0.75, larg=0.05)
                F.friso(F.ret(0, 0, w, h + 5), 'obs2', 0.35, 0.2)
                serra = 1.2 + 1.1 * np.abs(np.sin(F.x * 1.7 + 0.4)) + 0.6 * np.abs(np.sin(F.x * 4.9))
                F.a[F.y > h - serra] = 0
                for k in range(4):                                  # furos de rasgo
                    cx, cy = rng.uniform(1, w - 1), rng.uniform(h * 0.45, h - 3)
                    F.a[np.hypot((F.x - cx) / 0.5, (F.y - cy) / 0.8) < 1] = 0
        img = F.render()
        y0, x0 = int(v * S), int(u * S)
        im[y0:y0 + img.shape[0], x0:x0 + img.shape[1]] = img

    # caixa da asa do elitro: texOffs(22,0), 10x20x2
    u, v, w, h, d = 22, 0, 10, 20, 2
    face(u + d, v + d, w, h, 'fora', 31)          # norte
    face(u + 2 * d + w, v + d, w, h, 'fora', 32)  # sul
    face(u, v + d, d, h, 'borda', 33)
    face(u + d + w, v + d, d, h, 'borda', 34)
    face(u + d, v, w, d, 'borda', 35)
    face(u + d + w, v, w, d, 'borda', 36)
    return alfa_binario(im)


# ================================================================== Java: elmo 3D (modelo do item no contexto "head")
def _r(v):
    return round(float(v), 4)


CASCO_JAVA = {
    'north': (8, 8, 16, 16), 'east': (0, 8, 8, 16), 'south': (24, 8, 32, 16), 'west': (16, 8, 24, 16),
    'up': (16, 8, 8, 0), 'down': (16, 0, 24, 8),
}
FACE_JAVA = {'north': 'north', 'south': 'south', 'up': 'up', 'down': 'down', 'east': 'west', 'west': 'east'}


def elmo_java(cubos, textura, larg_px, alt_px):
    """Modelo Java do elmo: casco 0..16 = elmo inflado (px 64x32 da textura) + pecas. Textura larg_px x alt_px
    (UV 64 x alt_px/S)."""
    ku, kv = 16 / (larg_px / S), 16 / (alt_px / S)
    els = [{'from': [0, 0, 0], 'to': [16, 16, 16],
            'faces': {f: {'uv': [_r(a * ku), _r(b * kv), _r(c * ku), _r(d * kv)], 'texture': '#t'} for f, (a, b, c, d) in CASCO_JAVA.items()}}]
    for c in cubos:
        (x0, y0, z0), (x1, y1, z1) = c.de, c.ate
        a = [_r((5 - x1) * 1.6), _r((y0 - 23) * 1.6), _r((z0 + 5) * 1.6)]
        b = [_r((5 - x0) * 1.6), _r((y1 - 23) * 1.6), _r((z1 + 5) * 1.6)]
        for k in range(3):
            if a[k] < -16 or b[k] > 32:
                raise ValueError(f'{c.nome}: fora do limite do Java {a} {b}')
        faces = {}
        for f, (px0, py0, px1, py1) in c.uv.items():
            faces[FACE_JAVA[f]] = {'uv': [_r(px0 * 16 / larg_px), _r(py0 * 16 / alt_px), _r(px1 * 16 / larg_px), _r(py1 * 16 / alt_px)],
                                   'texture': '#t'}
        e = {'from': a, 'to': b, 'faces': faces}
        if c.emite:
            e['light_emission'] = int(min(15, c.emite))
        els.append(e)
    return {'credit': 'Corona Server - conjunto lendario original', 'texture_size': [larg_px, alt_px],
            'textures': {'t': textura, 'particle': textura}, 'elements': els,
            'display': {'head': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]}}}


# ================================================================== Bedrock
def geometria(conj, peca, cubos, escala_px, extras_ossos=None, tex_h=64):
    ident = f'geometry.corona.lend.{conj}.{peca}'
    ossos = {}
    ordem = []

    def osso(nome):
        if nome not in ossos:
            if nome in EXTRAS:
                pai, piv = EXTRAS[nome]
                osso(pai)
                ossos[nome] = {'name': nome, 'parent': pai, 'pivot': piv, 'cubes': []}
            else:
                ossos[nome] = {'name': nome, 'pivot': PIVO[nome], 'cubes': []}
            ordem.append(nome)
        return ossos[nome]
    for nome, cubo in CASCO[peca]:
        osso(nome)['cubes'].append(dict(cubo))
    k = 1.0 / escala_px            # px do atlas -> unidades de UV (64 de largura)
    for c in cubos:
        (x0, y0, z0), (x1, y1, z1) = c.de, c.ate
        cube = {'origin': [_r(x0), _r(y0), _r(z0)], 'size': [_r(x1 - x0), _r(y1 - y0), _r(z1 - z0)], 'uv': {}}
        for f, (a, b, cc, d) in c.uv.items():
            cube['uv'][f] = {'uv': [_r(a * k), _r(b * k)], 'uv_size': [_r((cc - a) * k), _r((d - b) * k)]}
        osso(c.grupo)['cubes'].append(cube)
    bones = [ossos[n] for n in ordem]
    if extras_ossos:
        bones += extras_ossos
    for b in bones:
        if not b.get('cubes'):
            b.pop('cubes', None)
    return {'format_version': '1.16.0', 'minecraft:geometry': [{
        'description': {'identifier': ident, 'texture_width': 64, 'texture_height': tex_h,
                        'visible_bounds_width': 3.5, 'visible_bounds_height': 4, 'visible_bounds_offset': [0, 1.5, 0]},
        'bones': bones}]}


ANIM = {
    'format_version': '1.8.0',
    'animations': {
        'animation.corona.lend.halo': {'loop': True, 'animation_length': 6.0,
                                       'bones': {'halo': {'rotation': [0, 'q.anim_time * 60', 0],
                                                          'position': [0, 'math.sin(q.anim_time * 120) * 0.35', 0]}}},
        'animation.corona.lend.manto': {'loop': True, 'animation_length': 3.0,
                                        'bones': {'manto': {'rotation': [
                                            '5 + math.clamp(q.modified_move_speed, 0, 1) * 40 + (q.is_sneaking ? 22 : 0) + math.sin(q.anim_time * 120) * 1.5',
                                            0, 0]}}},
    }
}

RENDER = {
    'format_version': '1.8.0',
    'render_controllers': {
        'controller.render.corona_lendario': {
            'geometry': 'Geometry.default',
            'materials': [{'*': 'Material.default'}],
            'textures': ['Texture.default'],
        }
    }
}


def attachable(conj, peca):
    d = {'identifier': f'corona:{conj}_{peca}',
         'materials': {'default': 'entity_emissive_alpha', 'enchanted': 'entity_emissive_alpha'},
         'textures': {'default': f'textures/models/armor/corona_{conj}_{CAMADA[peca]}', 'enchanted': 'textures/misc/enchanted_actor_glint'},
         'geometry': {'default': f'geometry.corona.lend.{conj}.{peca}'},
         'scripts': {'parent_setup': f'variable.{VISIVEL[peca]}_layer_visible = 0.0;'},
         'render_controllers': ['controller.render.corona_lendario']}
    anims = {}
    if peca == 'elmo' and conj == 'herobrine':
        anims['halo'] = 'animation.corona.lend.halo'
    if peca == 'peitoral':
        anims['manto'] = 'animation.corona.lend.manto'
    if anims:
        d['animations'] = anims
        d['scripts']['animate'] = list(anims)
    return {'format_version': '1.10.0', 'minecraft:attachable': {'description': d}}


# ================================================================== previa (renderizador simples com z-buffer)
class Tela:
    def __init__(self, W, H, esc, yaw, pitch=12, cy=16):
        self.W, self.H, self.esc, self.cy = W, H, esc, cy
        a, b = np.radians(yaw), np.radians(pitch)
        ry = np.array([[np.cos(a), 0, np.sin(a)], [0, 1, 0], [-np.sin(a), 0, np.cos(a)]])
        rx = np.array([[1, 0, 0], [0, np.cos(b), -np.sin(b)], [0, np.sin(b), np.cos(b)]])
        self.R = rx @ ry
        self.cor = np.zeros((H, W, 3))
        self.cor[:] = (0.13, 0.14, 0.17)
        self.z = np.full((H, W), -1e9)
        yy, xx = np.mgrid[0:H, 0:W]
        self.px, self.py = xx + 0.5, yy + 0.5

    def proj(self, P):
        # Bedrock (frente -z) -> tela olhando a frente: vira z
        q = self.R @ np.array([P[0], P[1] - self.cy, -P[2]], float)
        return np.array([self.W / 2 + q[0] * self.esc, self.H / 2 - q[1] * self.esc]), q[2]

    def quad(self, tex, rect, P0, eU, eV, luz):
        p0, z0 = self.proj(P0)
        pu, zu = self.proj(np.add(P0, eU))
        pv, zv = self.proj(np.add(P0, eV))
        a, b = pu - p0, pv - p0
        det = a[0] * b[1] - a[1] * b[0]
        if abs(det) < 1e-6:
            return
        xs = [p0[0], pu[0], pv[0], pu[0] + b[0]]
        ys = [p0[1], pu[1], pv[1], pu[1] + b[1]]
        i0, i1 = max(0, int(min(xs)) - 1), min(self.W, int(max(xs)) + 2)
        j0, j1 = max(0, int(min(ys)) - 1), min(self.H, int(max(ys)) + 2)
        if i0 >= i1 or j0 >= j1:
            return
        dx, dy = self.px[j0:j1, i0:i1] - p0[0], self.py[j0:j1, i0:i1] - p0[1]
        s = (dx * b[1] - dy * b[0]) / det
        t = (a[0] * dy - a[1] * dx) / det
        m = (s >= 0) & (s < 1) & (t >= 0) & (t < 1)
        if not m.any():
            return
        x0, y0, x1, y1 = rect
        tx = np.clip(np.floor(x0 + s * (x1 - x0)).astype(int), min(x0, x1), max(x0, x1) - 1)
        ty = np.clip(np.floor(y0 + t * (y1 - y0)).astype(int), min(y0, y1), max(y0, y1) - 1)
        tx = np.clip(tx, 0, tex.shape[1] - 1).astype(int)
        ty = np.clip(ty, 0, tex.shape[0] - 1).astype(int)
        am = tex[ty, tx]
        z = z0 + s * (zu - z0) + t * (zv - z0)
        zb = self.z[j0:j1, i0:i1]
        ok = m & (am[..., 3] > 0) & (z >= zb - 1e-4)
        brilho = (255 - am[..., 3].astype(float)) / 215.0
        cor = am[..., :3] / 255.0 * np.clip(luz + brilho[..., None] * 0.9, 0, 1.3)
        cb = self.cor[j0:j1, i0:i1]
        cb[ok] = cor[ok]
        zb[ok] = z[ok]

    def cubo(self, tex, k, origin, size, uvs, inflar=0.0):
        """uvs: face -> (x0,y0,x1,y1) em px da textura (x1<x0 = espelhado)."""
        x0, y0, z0 = (origin[0] - inflar, origin[1] - inflar, origin[2] - inflar)
        W, H, D = size[0] + 2 * inflar, size[1] + 2 * inflar, size[2] + 2 * inflar
        x1, y1, z1 = x0 + W, y0 + H, z0 + D
        F = {'north': ((x0, y1, z0), (W, 0, 0), (0, -H, 0), 0.92), 'south': ((x1, y1, z1), (-W, 0, 0), (0, -H, 0), 0.82),
             'west': ((x0, y1, z1), (0, 0, -D), (0, -H, 0), 0.74), 'east': ((x1, y1, z0), (0, 0, D), (0, -H, 0), 0.74),
             'up': ((x0, y1, z1), (W, 0, 0), (0, 0, -D), 1.0), 'down': ((x0, y0, z0), (W, 0, 0), (0, 0, D), 0.55)}
        for f, rect in uvs.items():
            P0, eU, eV, luz = F[f]
            r = tuple(v * k for v in rect)
            if r[2] < r[0]:
                P0 = np.add(P0, eU)
                eU = tuple(-c for c in eU)
                r = (r[2], r[1], r[0], r[3])
            self.quad(tex, r, P0, eU, eV, luz)

    def imagem(self):
        return Image.fromarray((np.clip(self.cor, 0, 1) * 255).astype(np.uint8), 'RGB')


def uv_caixa(u, v, w, h, d, espelhado=False):
    r = {'up': (u + d, v, u + d + w, v + d), 'down': (u + d + w, v, u + d + 2 * w, v + d),
         'west': (u, v + d, u + d, v + d + h), 'north': (u + d, v + d, u + d + w, v + d + h),
         'east': (u + d + w, v + d, u + 2 * d + w, v + d + h), 'south': (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h)}
    if espelhado:
        r = {f: (x1, y0, x0, y1) for f, (x0, y0, x1, y1) in r.items()}
        r['west'], r['east'] = r['east'], r['west']
    return r


def desenhar_geo(tela, geo, tex):
    """Desenha uma geometria (pose parada: ossos sem giro, so os extras com 'rotation' estatica ignorada)."""
    k = tex.shape[1] / 64
    for b in geo['minecraft:geometry'][0]['bones']:
        for c in b.get('cubes', []):
            uv = c['uv']
            if isinstance(uv, list):
                rects = uv_caixa(uv[0], uv[1], *c['size'], espelhado=c.get('mirror', False))
            else:
                rects = {f: (v['uv'][0], v['uv'][1], v['uv'][0] + v['uv_size'][0], v['uv'][1] + v['uv_size'][1]) for f, v in uv.items()}
            if b.get('rotation') and any(b['rotation']):
                continue      # (asas: giradas, ficam fora da previa)
            tela.cubo(tex, k, c['origin'], c['size'], rects, c.get('inflate', 0.0))


def pele_manequim():
    p = np.zeros((64, 64, 4), np.uint8)
    p[...] = (150, 118, 96, 255)
    p[16:32, 16:40] = (60, 90, 140, 255)
    p[16:32, 0:16] = (50, 50, 80, 255)
    return p


def previa(geos, texs, saida):
    pele = pele_manequim()
    vistas = []
    for yaw in (0, 35, 180, 215):
        t = Tela(260, 380, 8.0, yaw)
        for org, tam, uv in (((-4, 24, -4), (8, 8, 8), (0, 0)), ((-4, 12, -2), (8, 12, 4), (16, 16)), ((-8, 12, -2), (4, 12, 4), (40, 16)),
                             ((4, 12, -2), (4, 12, 4), (40, 16)), ((-3.9, 0, -2), (4, 12, 4), (0, 16)), ((-0.1, 0, -2), (4, 12, 4), (0, 16))):
            t.cubo(pele, 1, org, tam, uv_caixa(uv[0], uv[1], *tam))
        for g, tx in zip(geos, texs):
            desenhar_geo(t, g, tx)
        vistas.append(t.imagem())
    folha = Image.new('RGB', (sum(v.width for v in vistas) + 10 * (len(vistas) + 1), vistas[0].height + 20), (24, 25, 30))
    x = 10
    for v in vistas:
        folha.paste(v, (x, 10))
        x += v.width + 10
    folha.save(saida)


# ================================================================== Lamina do Vazio (espada do Herobrine)
def espada_herobrine():
    import armas_v2 as AV
    E = AV.Espada()
    E.c(-0.75, 0.0, 0.75, 5.4, 'obs', 1.25)                       # cabo
    for y in (1.0, 2.4, 3.8):
        E.c(-0.85, y, 0.85, y + 0.35, 'luz', 1.4, emite=12)       # aneis de luz
    E.c(-1.0, -1.6, 1.0, 0.0, 'cristal', 1.7, emite=12)           # pomo de cristal
    E.c(-0.45, -2.3, 0.45, -1.6, 'luz', 1.0, emite=15)
    E.c(-4.4, 5.3, 4.4, 6.6, 'obs2', 1.9)                         # guarda
    E.c(-4.4, 6.6, -3.3, 8.4, 'obs', 1.5)
    E.c(3.3, 6.6, 4.4, 8.4, 'obs', 1.5)
    E.c(-4.2, 8.4, -3.5, 9.5, 'cristal', 1.1, emite=12)
    E.c(3.5, 8.4, 4.2, 9.5, 'cristal', 1.1, emite=12)
    E.c(-3.9, 4.1, -3.1, 5.3, 'obs', 1.3)
    E.c(3.1, 4.1, 3.9, 5.3, 'obs', 1.3)
    E.c(-0.9, 5.0, 0.9, 6.9, 'olho', 2.3, emite=15)               # olho de luz na guarda
    # lamina larga de obsidiana com dentes e fio de luz
    y = 6.6
    larguras = (3.6, 3.5, 3.3, 3.2, 3.0, 2.8, 2.6, 2.3)
    passo = 2.0
    for i, w in enumerate(larguras):
        E.c(-w / 2, y, w / 2, y + passo, 'obs' if i % 2 else 'obs2', 1.0)
        if i % 2 == 0:
            E.c(w / 2 - 0.1, y + 0.3, w / 2 + 0.55, y + 1.3, 'cristal', 0.8, emite=10)
            E.c(-w / 2 - 0.55, y + 0.9, -w / 2 + 0.1, y + 1.9, 'cristal', 0.8, emite=10)
        y += passo
    E.c(-1.6, y, 1.6, y + 1.4, 'obs2', 0.9)
    E.c(-0.9, y + 1.4, 0.9, y + 2.6, 'cristal', 0.8, emite=12)
    E.c(-0.35, y + 2.6, 0.35, y + 3.5, 'luz', 0.6, emite=15)
    E.c(-0.28, 7.2, 0.28, y + 1.0, 'luz', 1.15, emite=13)         # fio de luz no meio
    return E.cs


def gerar_espada(saida):
    import armas_v2 as AV
    cs = espada_herobrine()
    LARG = 256
    img, bri, esc = montar_atlas(cs, LARG, 0, 256, escala=6, semente=4242)
    alt = int(max(c.uv[f][3] for c in cs for f in c.uv))
    H = 16
    while H < alt:
        H *= 2
    img, bri = img[:H], bri[:H]
    J = os.path.join(saida, 'java', 'assets', 'corona')
    B = os.path.join(saida, 'bedrock')
    os.makedirs(os.path.join(J, 'textures', 'item', 'herobrine'), exist_ok=True)
    Image.fromarray(alfa_binario(img), 'RGBA').save(os.path.join(J, 'textures', 'item', 'herobrine', 'espada3d.png'), optimize=True)
    modelo = kit.modelo_java(cs, 'corona:item/herobrine/espada3d', LARG, H, display=AV.DISPLAY)
    modelo['credit'] = 'Corona Server - Lamina do Vazio (original)'
    kit.salvar_json(os.path.join(J, 'models', 'item', 'herobrine', 'espada.json'), modelo)
    os.makedirs(os.path.join(B, 'textures', 'item_3d'), exist_ok=True)
    Image.fromarray(alfa_brilho(img, bri), 'RGBA').save(os.path.join(B, 'textures', 'item_3d', 'herobrine_espada.png'), optimize=True)
    geo = 'geometry.corona.herobrine_espada'
    kit.salvar_json(os.path.join(B, 'models', 'entity', 'armas', 'herobrine_espada.geo.json'), AV.geometria_mao(geo, cs, LARG, H))
    at = AV.attachable_mao('corona:herobrine_espada', 'textures/item_3d/herobrine_espada', geo, 'espada')
    at['minecraft:attachable']['description']['materials'] = {'default': 'entity_emissive_alpha', 'enchanted': 'entity_emissive_alpha'}
    kit.salvar_json(os.path.join(B, 'attachables', 'herobrine_espada.json'), at)
    pv = kit.previa(cs, alfa_binario(img), 0, 0, escala=10)
    pv.save(os.path.join(saida, 'previa_espada_herobrine.png'))
    return len(cs)


# ================================================================== geracao
def geometria_asas():
    """Ossos das asas de hussardo do peitoral do Rei (de asas_hussardo.py, guardados em recursos/)."""
    return json.load(open(os.path.join(AQUI, 'recursos', 'asas_hussardo_ossos.json'), encoding='utf-8'))


def gerar_conjunto(conj, saida):
    J = os.path.join(saida, 'java', 'assets', 'corona')
    B = os.path.join(saida, 'bedrock')
    c1, c2, b1, b2 = camadas(conj)
    if conj == 'coroa':
        # penas das asas de hussardo (pintadas por asas_hussardo.py na area livre x 56..64, y 16..32 da camada 1)
        penas = np.array(Image.open(os.path.join(AQUI, 'recursos', 'coroa_asas_penas.png')).convert('RGBA'))
        if True:
            c1 = c1.copy()
            c1[16 * S:32 * S, 56 * S:64 * S] = penas
            b1 = b1.copy()
            b1[16 * S:32 * S, 56 * S:64 * S] = 0
    # Java: camadas HD (alpha 0/255)
    for pasta, im in (('humanoid', c1), ('humanoid_leggings', c2)):
        p = os.path.join(J, 'textures', 'entity', 'equipment', pasta)
        os.makedirs(p, exist_ok=True)
        Image.fromarray(alfa_binario(im), 'RGBA').save(os.path.join(p, f'{conj}.png'), optimize=True)
    p = os.path.join(J, 'textures', 'entity', 'equipment', 'wings')
    os.makedirs(p, exist_ok=True)
    Image.fromarray(manto_java(conj), 'RGBA').save(os.path.join(p, f'{conj}_manto.png'), optimize=True)
    kit.salvar_json(os.path.join(J, 'equipment', f'{conj}.json'), {'layers': {
        'humanoid': [{'texture': f'corona:{conj}'}], 'humanoid_leggings': [{'texture': f'corona:{conj}'}],
        'wings': [{'texture': f'corona:{conj}_manto'}]}})
    # pecas 3D: atlas na metade de baixo das texturas do Bedrock (camada 1: elmo+peitoral+botas; camada 2: calca)
    pecas = {pc: PECAS_DE[conj](pc) for pc in PECAS}
    forca = 120 if conj == 'coroa' else 205      # o rei brilha so nas joias (alpha mais alto = brilho mais fraco)
    # texturas do Bedrock (512x512, UV 64x64): casco em cima + atlas das pecas embaixo, uma por grupo de pecas
    grupos = {'elmo': (c1, b1, pecas['elmo']), '1': (c1, b1, pecas['peitoral'] + pecas['botas']), '2': (c2, b2, pecas['calca'])}
    texs, escalas = {}, {}
    os.makedirs(os.path.join(B, 'textures', 'models', 'armor'), exist_ok=True)
    for i, (nome, (casco, bcasco, lista)) in enumerate(grupos.items()):
        tex = np.zeros((64 * S, 64 * S, 4), np.uint8)
        bri = np.zeros((64 * S, 64 * S))
        tex[:32 * S], bri[:32 * S] = casco, bcasco
        a, ab, e = montar_atlas(lista, 64 * S, 32 * S, 32 * S, semente=(100 if conj == 'coroa' else 500) + i * 300)
        tex[32 * S:], bri[32 * S:] = a, ab
        escalas[nome] = e
        texs[nome] = (tex, alfa_brilho(tex, bri, forca))
        Image.fromarray(texs[nome][1], 'RGBA').save(os.path.join(B, 'textures', 'models', 'armor', f'corona_{conj}_{nome}.png'), optimize=True)
    # Java: elmo 3D (textura propria 512x512 com o casco + atlas)
    p = os.path.join(J, 'textures', 'item', 'lendarios')
    os.makedirs(p, exist_ok=True)
    Image.fromarray(alfa_binario(texs['elmo'][0]), 'RGBA').save(os.path.join(p, f'{conj}_elmo3d.png'), optimize=True)
    kit.salvar_json(os.path.join(J, 'models', 'item', 'lendarios', f'{conj}_elmo3d.json'),
                    elmo_java(pecas['elmo'], f'corona:item/lendarios/{conj}_elmo3d', 64 * S, 64 * S))
    kit.salvar_json(os.path.join(J, 'items', 'item', conj, 'elmo.json'), {'model': {
        'type': 'minecraft:select', 'property': 'minecraft:display_context',
        'cases': [{'when': 'head', 'model': {'type': 'minecraft:model', 'model': f'corona:item/lendarios/{conj}_elmo3d'}}],
        'fallback': {'type': 'minecraft:model', 'model': f'corona:item/{conj}/elmo'}}})
    # Bedrock: geometrias + attachables
    geos = {}
    for pc in PECAS:
        extras = geometria_asas() if (conj == 'coroa' and pc == 'peitoral') else None
        g = geometria(conj, pc, pecas[pc], S, extras)
        geos[pc] = g
        kit.salvar_json(os.path.join(B, 'models', 'entity', f'corona_lend_{conj}_{pc}.geo.json'), g)
        kit.salvar_json(os.path.join(B, 'attachables', f'corona_{conj}_{pc}.json'), attachable(conj, pc))
    kit.salvar_json(os.path.join(B, 'animations', 'corona_lendarios.animation.json'), ANIM)
    kit.salvar_json(os.path.join(B, 'render_controllers', 'corona_lendarios.render_controllers.json'), RENDER)
    previa([geos['calca'], geos['botas'], geos['peitoral'], geos['elmo']], [texs['2'][1], texs['1'][1], texs['1'][1], texs['elmo'][1]],
           os.path.join(saida, f'previa_{conj}.png'))
    n = {pc: len(pecas[pc]) for pc in PECAS}
    print(conj, 'cubos por peca', n, 'escala atlas', escalas)
    return n


if __name__ == '__main__':
    so = sys.argv[2:] or ['coroa', 'herobrine']
    for conj in so:
        gerar_conjunto(conj, SAIDA)
    if 'herobrine' in so:
        print('espada herobrine: cubos', gerar_espada(SAIDA))
