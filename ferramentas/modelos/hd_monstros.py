"""Monstros 3D (Nao Chore e Backrooms) em alta: textura 2x e acabamento de relevo nas bordas (bisel + sombra).
uso: python3 -I hd_monstros.py <saida> [escala] [nomes...]"""
import os, sys
import numpy as np
M_DIR = '/tmp/claude-0/novo/fontes/modelos'
sys.path.insert(0, M_DIR)
SAIDA = sys.argv[1]
ESC = int(sys.argv[2]) if len(sys.argv) > 2 else 6
SO = sys.argv[3:]
import gerar as G
import kit
G.JAVA = os.path.join(SAIDA, 'java', 'assets', 'corona')
G.BEDROCK = os.path.join(SAIDA, 'bedrock')
G.PREV = os.path.join(SAIDA, 'previas')
os.makedirs(G.PREV, exist_ok=True)
G.ESCALA_TEX = ESC
G.LARGURA = 64 * ESC if ESC <= 4 else 512

_pintar = kit.pintar_atlas


def acabamento(P, rgb, a):
    """Bisel: quina de cima/esquerda clareia, de baixo/direita escurece; sombra suave perto das bordas."""
    f = P['face']
    w = P['d'] if f in ('east', 'west') else P['w']
    h = P['d'] if f in ('up', 'down') else P['h']
    s, t = P['s'], P['t']
    du = np.minimum(s * w, (1 - s) * w)
    dv = np.minimum(t * h, (1 - t) * h)
    larg = 0.32
    cima = np.clip(1 - (t * h) / larg, 0, 1)
    esq = np.clip(1 - (s * w) / larg, 0, 1)
    baixo = np.clip(1 - ((1 - t) * h) / larg, 0, 1)
    dir_ = np.clip(1 - ((1 - s) * w) / larg, 0, 1)
    k = 1 + 0.16 * np.maximum(cima, esq * 0.6) - 0.20 * np.maximum(baixo, dir_ * 0.7)
    perto = np.clip(1 - np.minimum(du, dv) / 0.9, 0, 1)
    k = k * (1 - 0.10 * perto ** 2)
    if f == 'up':
        k = k * 1.06
    elif f == 'down':
        k = k * 0.8
    rgb = np.clip(np.asarray(rgb, float) * k[..., None], 0, 1)
    return rgb, a


def pintar_atlas(cubos, largura, altura, pintor, escala=1):
    def p2(P):
        rgb, a = pintor(P)
        rgb = np.broadcast_to(np.asarray(rgb, float), P['s'].shape + (3,))
        return acabamento(P, rgb, a)
    return _pintar(cubos, largura, altura, p2, escala)


kit.pintar_atlas = pintar_atlas

# Os monstros sao desenhados com o centro em x=0, z=0, mas o Java gira/posiciona o item pelo ponto (8, 8, 8): sem
# corrigir, o modelo aparece MEIO BLOCO fora do lugar (para o lado e para tras do bicho de verdade). O modelo Java e
# escrito 8 unidades para +x e +z; a geometria do Bedrock (entidade) continua centrada em 0.
_modelo_java = kit.modelo_java


def modelo_java_centrado(cubos, *a, **k):
    movidos = []
    for c in cubos:
        n = c.com()
        n.de = c.de + np.array([8.0, 0.0, 8.0])
        n.ate = c.ate + np.array([8.0, 0.0, 8.0])
        if c.rot:
            e, ang, o = c.rot
            n.rot = (e, ang, (o[0] + 8.0, o[1], o[2] + 8.0))
        movidos.append(n)
    return _modelo_java(movidos, *a, **k)


kit.modelo_java = modelo_java_centrado
G.kit.modelo_java = modelo_java_centrado
G.kit.pintar_atlas = pintar_atlas
for conjunto, (ms, _) in G.CONJUNTOS.items():
    for nome, M in ms.items():
        if SO and nome not in SO:
            continue
        M = dict(M)
        M['rosto'] = None
        G.gerar(nome, M, conjunto)

if not SO:
    import andar_java
    andar_java.main()
