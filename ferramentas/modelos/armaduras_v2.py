"""Armaduras tematicas v2 (09/11): menos "caixa", mais armadura de verdade.

Mesmas 59+ receitas do armaduras_tematicas.py (material, emblema, elmo, detalhe, cores), mas a pintura muda:
  * recortes (alpha) para quebrar a caixa: elmo aberto nos estilos de capuz/chapeu, peitoral afinando na cintura,
    laterais do peito em cota de malha vazada, ombreira em 3 laminas + bracadeira (meio do braco aparece a manga),
    botas so do joelho para baixo, calca com coxote, joelheira e faldas, fundo das caixas sem tampa;
  * relevo: placas com chanfro (luz em cima/esquerda, sombra embaixo/direita), contorno escuro na borda dos recortes,
    degrade de luz de cima para baixo, reflexo diagonal nos metais, rebites com brilho, emblema com sombra;
  * camada de baixo: cota de malha (metais, pedra, osso...) com furinhos, ou acolchoado (tecido, pelo, couro...).
A textura continua 256x128 (4x) e o mesmo arquivo serve ao Java (equipment) e ao Bedrock (textures/models/armor).

Uso: python3 -I armaduras_v2.py [ids...] [--so-previa]   (sem ids = todas)
"""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import armaduras_tematicas as A  # noqa: E402
from base import fbm, mix, C, ss, hash2  # noqa: E402

K = A.K
PARTES = A.PARTES
PREV = '/tmp/claude-0/modelos/armaduras_v2'
os.makedirs(PREV, exist_ok=True)

MACIOS = {'tecido', 'pelo', 'pelo_claro', 'faixas', 'gosma', 'cogumelo', 'couro'}
METAIS = {'metal', 'metal_claro', 'metal_escuro', 'ouro', 'cobre', 'bronze', 'gelo', 'cristal', 'obsidiana', 'escamas', 'quitina'}
ABERTOS = {'capuz', 'chapeu', 'nemes', 'mitra', 'kabuto', 'tricornio', 'lanterna'}


def esc(c, f):
    return np.clip(np.asarray(c) * f, 0, 1)


def clr(c, f):
    c = np.asarray(c)
    return np.clip(c + (1 - c) * f, 0, 1)


def deslocar(m, dy, dx, fora):
    """Desloca a matriz (sem dar a volta); o que entra pela borda vale 'fora'."""
    out = np.full_like(m, fora)
    h, w = m.shape
    ys0, ys1 = max(0, -dy), min(h, h - dy)
    xs0, xs1 = max(0, -dx), min(w, w - dx)
    out[ys0 + dy:ys1 + dy, xs0 + dx:xs1 + dx] = m[ys0:ys1, xs0:xs1]
    return out


def relevo(c, P, a, metal):
    """Chanfro por placa (P = id da placa, 0 = camada de baixo) e contorno nos recortes."""
    acima = deslocar(P, 1, 0, -1)
    abaixo = deslocar(P, -1, 0, -1)
    esq = deslocar(P, 0, 1, -1)
    dir_ = deslocar(P, 0, -1, -1)
    placa = P > 0
    luz = placa & ((acima != P) | (esq != P))
    sombra = placa & ((abaixo != P) | (dir_ != P))
    luz2 = placa & ~luz & ((deslocar(P, 2, 0, -1) != P) | (deslocar(P, 0, 2, -1) != P))
    sombra2 = placa & ~sombra & ((deslocar(P, -2, 0, -1) != P) | (deslocar(P, 0, -2, -1) != P))
    c = np.where(luz[..., None], clr(c, 0.38 if metal else 0.22), c)
    c = np.where(luz2[..., None], clr(c, 0.16 if metal else 0.08), c)
    c = np.where(sombra[..., None], esc(c, 0.45), c)
    c = np.where(sombra2[..., None], esc(c, 0.75), c)
    # contorno escuro onde o opaco encosta no vazado
    vaz = a < 0.5
    viz = deslocar(vaz, 1, 0, False) | deslocar(vaz, -1, 0, False) | deslocar(vaz, 0, 1, False) | deslocar(vaz, 0, -1, False)
    borda = (~vaz) & viz
    c = np.where(borda[..., None], esc(c, 0.45), c)
    return c


def malha(c, X, Y, metal_cor):
    """Cota de malha: aneis de 2 texels com furinho (alpha) — fica vazada como a malha vanilla."""
    anel = ((np.floor(X / 2) + np.floor(Y / 2)) % 2) == 0
    furo = ((X % 2) < 1) & ((Y % 2) < 1) & ~anel
    base = rampa_malha(metal_cor, X, Y)
    a = np.where(furo, 0.0, 1.0)
    return base, a


def rampa_malha(cor, X, Y):
    n = (hash2(np.floor(X).astype(int), np.floor(Y).astype(int), 5) * 0.35 + 0.65)
    anel = ((np.floor(X / 2) + np.floor(Y / 2)) % 2) == 0
    c = np.asarray(cor) * n[..., None]
    return np.where(anel[..., None], clr(c, 0.15), esc(c, 0.75))


def acolchoado(cor, X, Y, sem):
    """Gibao acolchoado: losangos costurados."""
    n = fbm(X * 0.4, Y * 0.4, 0 * X, 2, 1, sem + 51)
    c = np.asarray(cor) * (0.85 + 0.25 * n)[..., None]
    costura = ((np.abs(((X + Y) % 8) - 4) < 0.6) | (np.abs(((X - Y) % 8) - 4) < 0.6))
    return np.where(costura[..., None], esc(c, 0.6), c)


def reflexo(c, S, T, P, forca):
    """Faixa de brilho diagonal em cada placa (metal polido)."""
    d = (S - 0.15) - (1 - T) * 0.45
    hl = np.exp(-(d ** 2) / 0.006) * forca
    return np.clip(c + (1 - c) * (hl * (P > 0))[..., None], 0, 1)


def pintar_face(rec, t, parte, face, camada, x0, y0, x1, y1, sem):
    mat, emb, elm, det, acab, brilho = rec
    tema_cor = np.array(t['rgb'], float) / 255
    ac = C(acab)
    W, H = (x1 - x0) * K, (y1 - y0) * K
    s = (np.arange(W) + 0.5) / W
    tt = (np.arange(H) + 0.5) / H
    S, T = np.meshgrid(s, tt)
    X = x0 * K + S * W
    Y = y0 * K + T * H
    Z = np.full_like(S, {'north': 0, 'south': 7, 'east': 13, 'west': 3, 'up': 17, 'down': 23}[face])
    macio = mat in MACIOS
    metal = mat in METAIS
    base = A.material(mat, tema_cor, X / 2, Y / 2, Z / 2, sem)
    # camada de baixo (aparece nos vaos)
    if macio:
        baixo = acolchoado(esc(tema_cor, 0.45) if mat != 'couro' else C('#3a2412'), X, Y, sem)
        a_baixo = np.ones(S.shape)
    else:
        baixo, a_baixo = malha(base, X, Y, esc(mix(C('#8a9098'), tema_cor, 0.2), 0.95))
    P = np.zeros(S.shape, int)   # id da placa (0 = camada de baixo)
    a = np.zeros(S.shape)        # alpha da placa
    lateral = face in ('east', 'west')
    frente = face == 'south'
    costas = face == 'north'

    def faixa(t0, t1, pid, s0=0.0, s1=1.0):
        m = (T >= t0) & (T < t1) & (S >= s0) & (S < s1)
        P[m] = pid
        a[m] = 1.0
        return m

    usa_baixo = np.zeros(S.shape, bool)   # onde aparece a camada de baixo
    macio_aqui = False
    if camada == 1 and parte == 'head':
        if face == 'down':
            pass
        elif face == 'up':
            faixa(0, 1, 1)
            if not macio:
                crista = np.abs(S - 0.5) < 0.07
                P[crista] = 2
        else:
            faixa(0, 0.2, 1)
            faixa(0.2, 0.88, 2)
            faixa(0.88, 1.0, 3)
            if lateral:
                # recorte da orelha / bochecha: a borda de baixo sobe no meio
                corte = (T > 0.8) & (np.abs(S - 0.55) < 0.22)
                a[corte] = 0
                P[corte] = 0
            if costas:
                nuca = (T > 0.9) & (np.abs(S - 0.5) < 0.3)
                a[nuca] = 0
                P[nuca] = 0
            if frente and elm in ABERTOS:
                rosto = (T > 0.36) & (T < 0.93) & (np.abs(S - 0.5) < 0.31)
                a[rosto] = 0
                P[rosto] = 0
    elif camada == 1 and parte == 'body':
        if face == 'down':
            pass
        elif face == 'up':
            faixa(0, 1, 1)
        elif frente or costas:
            faixa(0, 0.12, 1)                 # gorjal
            faixa(0.12, 0.56, 2)              # peito
            faixa(0.56, 0.65, 3)
            faixa(0.65, 0.73, 4)              # laminas da barriga
            faixa(0.73, 0.81, 5)
            faixa(0.81, 0.9, 6)               # cinto
            faixa(0.9, 1.0, 7)                # faldas
            cintura = (T > 0.6) & (np.abs(S - 0.5) > 0.5 - (T - 0.6) * 0.45)
            a[cintura] = 0
            P[cintura] = 0
            usa_baixo |= cintura
        else:
            # laterais: so as bordas das placas da frente e de tras; o meio e malha/acolchoado
            borda = (S < 0.2) | (S > 0.8)
            faixa(0, 0.12, 1)
            faixa(0.81, 0.9, 6)
            m = borda & (T >= 0.12) & (T < 0.81)
            P[m] = 2
            a[m] = 1
            usa_baixo |= (~borda) & (T >= 0.12) & (T < 0.81)
    elif camada == 1 and parte == 'rarm':
        if face == 'down':
            pass
        elif face == 'up':
            faixa(0, 1, 1)
        else:
            faixa(0, 0.14, 1)                 # ombreira: 3 laminas
            faixa(0.14, 0.26, 2)
            faixa(0.26, 0.37, 3)
            faixa(0.66, 0.75, 4)              # bracadeira
            faixa(0.75, 1.0, 5)
            usa_baixo |= (T >= 0.37) & (T < 0.66)
    elif camada == 1 and parte == 'rleg':
        if face == 'down':
            faixa(0, 1, 3)
        elif face == 'up':
            pass
        else:
            faixa(0.56, 0.66, 1)              # cano da bota (com friso)
            faixa(0.66, 0.9, 2)
            faixa(0.9, 1.0, 3)                # pe
            if frente:
                bico = (T >= 0.86) & (np.abs(S - 0.5) < 0.38)
                P[bico] = 4
                a[bico] = 1
    elif camada == 2 and parte == 'body':
        if face in ('up', 'down'):
            if face == 'down':
                faixa(0, 1, 1)
        else:
            faixa(0.6, 0.72, 1)               # cinto
            if frente:
                faixa(0.72, 1.0, 2, 0.0, 0.47)   # faldas da frente (duas)
                faixa(0.72, 1.0, 3, 0.53, 1.0)
            else:
                usa_baixo |= (T >= 0.72)
    elif camada == 2 and parte == 'rleg':
        if face == 'up':
            pass
        elif face == 'down':
            faixa(0, 1, 1)
        else:
            if frente:
                faixa(0, 0.4, 1)              # coxote
                faixa(0.4, 0.56, 2)           # joelheira
                faixa(0.56, 1.0, 3)           # caneleira (fica embaixo da bota)
            elif lateral:
                faixa(0.4, 0.56, 2, 0.0, 0.5)
                usa_baixo |= ~((T >= 0.4) & (T < 0.56) & (S < 0.5))
            else:
                usa_baixo |= np.ones(S.shape, bool)
                macio_aqui = True
    # ---------------------------------------------------------------- cor
    c = base.copy()
    # cada placa com um tom um pouco diferente (laminas mais escuras para baixo)
    for pid in range(1, 8):
        m = P == pid
        if m.any():
            f = 1.0 + 0.06 * ((pid * 37 + sem) % 5 - 2) / 2
            c = np.where(m[..., None], esc(c, f), c)
    # frisos de acabamento: gorjal, cinto, ombreira de baixo, cano da bota, joelheira
    friso = np.zeros(S.shape, bool)
    if parte == 'body' and camada == 1:
        friso |= (P == 1) & (T > 0.08)
        cinto = P == 6
        c = np.where(cinto[..., None], esc(mix(C('#3a2412'), ac, 0.25), 1.0), c)
        if frente:
            fivela = cinto & (np.abs(S - 0.5) < 0.09)
            c = np.where(fivela[..., None], ac * np.ones_like(c), c)
    if parte == 'rarm' and camada == 1:
        friso |= ((P == 3) & (T > 0.33)) | ((P == 4) & (T < 0.69))
    if parte == 'rleg' and camada == 1:
        friso |= (P == 1) & (T < 0.6)
        c = np.where((P == 4)[..., None], mix(c, ac, 0.55), c)
        c = np.where((P == 3)[..., None], esc(c, 0.7), c)
    if parte == 'rleg' and camada == 2:
        friso |= (P == 2) & ((T < 0.43) | (T > 0.53))
        boss = (P == 2) & (np.hypot((S - 0.5) * W, (T - 0.48) * H) < 2.2) & frente
        c = np.where(boss[..., None], clr(ac, 0.2), c)
    if parte == 'body' and camada == 2:
        c = np.where((P == 1)[..., None], esc(mix(C('#3a2412'), ac, 0.25), 1.0), c)
        friso |= (P == 1) & (T > 0.69)
    if parte == 'head' and camada == 1:
        friso |= (P == 3) | ((P == 1) & (T > 0.16))
    c = np.where(friso[..., None], ac * np.ones_like(c), c)
    # detalhe do tema (rebites, runas, rachaduras...) so nas placas
    c_det = A.detalhe(c.copy(), det, X, Y, S, T, W, H, tema_cor, brilho, sem)
    c = np.where((P > 0)[..., None] & ~friso[..., None], c_det, c)
    # elmo (viseira, olhos, coroa...) e emblema
    if parte == 'head' and camada == 1:
        c = A.elmo(c, elm, face, S, T, W, H, tema_cor, acab, brilho, mat)
    if parte == 'body' and camada == 1 and (frente or costas):
        tam = 0.6 if frente else 0.42
        sombra = A.emblema(np.zeros_like(c) + 2, emb, S - 1.0 / W, T - 1.0 / H, W, H, tema_cor, acab, brilho, cy=0.34, tam=tam)
        tem_sombra = (sombra < 1.5).any(axis=-1)
        c = np.where(tem_sombra[..., None], esc(c, 0.55), c)
        c = A.emblema(c, emb, S, T, W, H, tema_cor, acab, brilho, cy=0.34, tam=tam)
    # ---------------------------------------------------------------- volume
    if metal:
        c = reflexo(c, S, T, P, 0.22)
    luz = 1.12 - 0.26 * T
    if face == 'up':
        luz = np.full_like(T, 1.15)
    if face == 'down':
        luz = np.full_like(T, 0.65)
    if lateral:
        luz = luz * 0.88
    if costas:
        luz = luz * 0.8
    c = np.clip(c * luz[..., None], 0, 1)
    c = relevo(c, P, a, metal)
    # camada de baixo nos vaos
    if usa_baixo.any():
        if macio_aqui and not macio:
            baixo = acolchoado(esc(mix(tema_cor, C('#3a3f4a'), 0.5), 0.6), X, Y, sem)
            a_baixo = np.ones(S.shape)
        bc = np.clip(baixo * (luz * (0.85 if not macio else 0.9))[..., None], 0, 1)
        c = np.where(usa_baixo[..., None], bc, c)
        a = np.where(usa_baixo, a_baixo, a)
    return np.clip(c, 0, 1), a


def textura(id_, t, camada):
    rec = A.R.get(id_, ('metal', 'escudo', 'viseira', 'rebites', '#c0c0c0', None))
    img = np.zeros((32 * K, 64 * K, 4))
    sem = (sum(ord(ch) for ch in id_) * 7 + camada) % 997
    for parte, (u, v, w, h, d) in PARTES.items():
        for face, (x0, y0, x1, y1) in A.faces_uv(u, v, w, h, d).items():
            rgb, a = pintar_face(rec, t, parte, face, camada, x0, y0, x1, y1, sem)
            img[y0 * K:y1 * K, x0 * K:x1 * K, :3] = rgb
            img[y0 * K:y1 * K, x0 * K:x1 * K, 3] = a
    return (np.clip(img, 0, 1) * 255 + 0.5).astype(np.uint8)


# ------------------------------------------------------------------ previa (boneco de frente, de lado e de costas)

def _manequim():
    """Pele neutra (original): cabeca e maos claras, camisa cinza, calca escura."""
    img = np.zeros((64, 64, 4), np.uint8)
    def pinta(x0, y0, x1, y1, cor):
        img[y0:y1, x0:x1] = list(cor) + [255]
    pinta(0, 0, 32, 16, (196, 164, 132))      # cabeca
    pinta(16, 16, 40, 32, (110, 116, 128))    # corpo
    pinta(40, 16, 56, 32, (110, 116, 128))    # braco
    pinta(40, 28, 56, 32, (196, 164, 132))    # mao
    pinta(0, 16, 16, 32, (52, 56, 70))        # perna
    pinta(0, 28, 16, 32, (40, 30, 24))        # pe
    # olhos
    img[12, 9:11] = [40, 50, 70, 255]
    img[12, 13:15] = [40, 50, 70, 255]
    return Image.fromarray(img, 'RGBA').resize((256, 256), Image.NEAREST)


def previa(id_, t):
    c1 = Image.fromarray(textura(id_, t, 1), 'RGBA')
    c2 = Image.fromarray(textura(id_, t, 2), 'RGBA')
    pele = _manequim()
    E = 4   # texels por pixel do modelo nas texturas 4x

    def face(img, u, v, w, h, d, f, escala=K):
        x0, y0, x1, y1 = A.faces_uv(u, v, w, h, d)[f]
        return img.crop((x0 * escala, y0 * escala, x1 * escala, y1 * escala))

    def boneco(f):
        out = Image.new('RGBA', (24 * E, 36 * E), (38, 42, 50, 255))
        lado = f in ('east', 'west')
        # posicoes (em pixels do modelo) do que aparece nessa vista
        cab = (8, 2) if not lado else (8, 2)
        def cola(im, x, y, w, h, inflar=0.0):
            im = im.resize((int(round((w + 2 * inflar) * E)), int(round((h + 2 * inflar) * E))), Image.NEAREST)
            out.alpha_composite(im, (int(round((x - inflar) * E)), int(round((y - inflar) * E))))
        if not lado:
            espelho = f == 'north'
            # pele
            cola(face(pele, 0, 0, 8, 8, 8, f), 8, 2, 8, 8)
            cola(face(pele, 16, 16, 8, 12, 4, f), 8, 10, 8, 12)
            for bx in (4, 16):
                cola(face(pele, 40, 16, 4, 12, 4, f), bx, 10, 4, 12)
            for lx in (8, 12):
                cola(face(pele, 0, 16, 4, 12, 4, f), lx, 22, 4, 12)
            # calca (camada 2, inflada 0.5)
            cola(face(c2, 16, 16, 8, 12, 4, f), 8, 10, 8, 12, 0.5)
            for lx in (8, 12):
                cola(face(c2, 0, 16, 4, 12, 4, f), lx, 22, 4, 12, 0.5)
            # camada 1 (inflada 1)
            for lx in (8, 12):
                cola(face(c1, 0, 16, 4, 12, 4, f), lx, 22, 4, 12, 1)
            cola(face(c1, 16, 16, 8, 12, 4, f), 8, 10, 8, 12, 1)
            for bx in (4, 16):
                cola(face(c1, 40, 16, 4, 12, 4, f), bx, 10, 4, 12, 1)
            cola(face(c1, 0, 0, 8, 8, 8, f), 8, 2, 8, 8, 1)
            if espelho:
                out = out.transpose(Image.FLIP_LEFT_RIGHT)
        else:
            cola(face(pele, 0, 0, 8, 8, 8, f), 8, 2, 8, 8)
            cola(face(pele, 0, 16, 4, 12, 4, f), 10, 22, 4, 12)
            cola(face(c2, 0, 16, 4, 12, 4, f), 10, 22, 4, 12, 0.5)
            cola(face(c1, 0, 16, 4, 12, 4, f), 10, 22, 4, 12, 1)
            cola(face(pele, 16, 16, 8, 12, 4, f), 10, 10, 4, 12)
            cola(face(c2, 16, 16, 8, 12, 4, f), 10, 10, 4, 12, 0.5)
            cola(face(c1, 16, 16, 8, 12, 4, f), 10, 10, 4, 12, 1)
            cola(face(pele, 40, 16, 4, 12, 4, f), 10, 10, 4, 12)
            cola(face(c1, 40, 16, 4, 12, 4, f), 10, 10, 4, 12, 1)
            cola(face(c1, 0, 0, 8, 8, 8, f), 8, 2, 8, 8, 1)
        return out

    vistas = [boneco('south'), boneco('west'), boneco('north')]
    W = sum(v.width for v in vistas) + 2 * 8
    img = Image.new('RGBA', (W, vistas[0].height), (30, 33, 40, 255))
    x = 0
    for v in vistas:
        img.alpha_composite(v, (x, 0))
        x += v.width + 8
    return img.resize((img.width * 2, img.height * 2), Image.NEAREST)


def gerar(id_, t, escrever=True):
    tema = id_.lower()
    if escrever:
        for camada, pasta in ((1, 'humanoid'), (2, 'humanoid_leggings')):
            d = os.path.join(A.JAVA, 'textures', 'entity', 'equipment', pasta)
            os.makedirs(d, exist_ok=True)
            Image.fromarray(textura(id_, t, camada), 'RGBA').save(os.path.join(d, f'{tema}.png'), optimize=True)
    previa(id_, t).save(os.path.join(PREV, f'{tema}.png'))


if __name__ == '__main__':
    args = [x for x in sys.argv[1:] if not x.startswith('--')]
    escrever = '--so-previa' not in sys.argv
    so = [x.upper() for x in args]
    n = 0
    for id_, t in A.TEMAS.items():
        if so and id_ not in so:
            continue
        gerar(id_, t, escrever)
        n += 1
    print(n, 'temas (v2)', '(previas em ' + PREV + ')')
