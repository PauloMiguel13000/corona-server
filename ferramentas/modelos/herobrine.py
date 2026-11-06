"""Conjunto cosmetico "de Herobrine" (Corona Server): armadura de obsidiana rachada com brilho branco-azulado,
olhos brancos no elmo e pontas de coroa; espada preta com fio brilhante. Desenho original, em pixel art 2x
(128x64 na armadura, 32x32 nos icones). Gera as texturas do Java e do Bedrock + previas.
Uso: python3 herobrine.py <pasta-saida>"""
import os, random, sys
from PIL import Image

OUT = sys.argv[1] if len(sys.argv) > 1 else '/tmp/claude-0/herobrine'
os.makedirs(OUT, exist_ok=True)
S = 2  # 2 pixels por "pixel" da textura padrao (64x32 -> 128x64)

OBS = [(9, 8, 14), (18, 16, 27), (28, 25, 40), (40, 36, 57), (58, 53, 80), (80, 74, 108)]
G_CORE, G1, G2, G3 = (250, 255, 255), (196, 246, 255), (112, 214, 245), (46, 120, 160)
TRANSP = (0, 0, 0, 0)


def rgba(c, a=255):
    return (c[0], c[1], c[2], a)


class Tela:
    def __init__(self, w, h):
        self.im = Image.new('RGBA', (w, h), TRANSP)
        self.px = self.im.load()
        self.w, self.h = w, h

    def put(self, x, y, c, a=255):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[x, y] = rgba(c, a)

    def get(self, x, y):
        return self.px[x, y]


def obsidiana(t, x0, y0, w, h, rnd, base=1, borda=True):
    """Preenche um retangulo (em pixels reais) com obsidiana: ruido + chanfro + veios."""
    for y in range(h):
        for x in range(w):
            r = rnd.random()
            i = base + (1 if r > 0.78 else 0) - (1 if r < 0.12 else 0)
            if rnd.random() < 0.025:
                i = 3
            t.put(x0 + x, y0 + y, OBS[max(0, min(3, i))])
    if borda:
        for x in range(w):
            t.put(x0 + x, y0, OBS[4])
            t.put(x0 + x, y0 + h - 1, OBS[0])
        for y in range(h):
            t.put(x0, y0 + y, OBS[3])
            t.put(x0 + w - 1, y0 + y, OBS[0])


def placa(t, x0, y0, w, h, rnd, base=2):
    """Placa saliente: mais clara, borda de cima/esq brilhante e de baixo/dir escura."""
    for y in range(h):
        for x in range(w):
            r = rnd.random()
            i = base + (1 if r > 0.85 else 0) - (1 if r < 0.1 else 0)
            t.put(x0 + x, y0 + y, OBS[max(0, min(4, i))])
    for x in range(w):
        t.put(x0 + x, y0, OBS[5])
        t.put(x0 + x, y0 + h - 1, OBS[0])
    for y in range(1, h - 1):
        t.put(x0, y0 + y, OBS[4])
        t.put(x0 + w - 1, y0 + y, OBS[1])


def brilho(t, x, y, nivel=1):
    """Ponto de brilho com halo azul."""
    core = [G_CORE, G1, G2][min(2, nivel - 1)]
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        p = t.get(x + dx, y + dy) if 0 <= x + dx < t.w and 0 <= y + dy < t.h else None
        if p and p[3] and p[:3] in [o for o in OBS]:
            t.put(x + dx, y + dy, G3)
    t.put(x, y, core)


def rachadura(t, x0, y0, x1, y1, rnd, caixa, passos=None, galhos=1):
    """Rachadura luminosa (passeio aleatorio de (x0,y0) ate perto de (x1,y1)), presa dentro da caixa."""
    bx0, by0, bx1, by1 = caixa
    x, y = x0, y0
    n = passos or (abs(x1 - x0) + abs(y1 - y0)) + 2
    pontos = []
    for i in range(n):
        pontos.append((x, y))
        dx = (1 if x1 > x else -1 if x1 < x else 0)
        dy = (1 if y1 > y else -1 if y1 < y else 0)
        if rnd.random() < 0.35:
            dx, dy = (dx, 0) if rnd.random() < 0.5 else (0, dy)
        if rnd.random() < 0.15:
            dx = rnd.choice((-1, 0, 1))
        x = max(bx0, min(bx1, x + dx))
        y = max(by0, min(by1, y + dy))
        if (x, y) == (x1, y1):
            pontos.append((x, y))
            break
    for i, (px, py) in enumerate(pontos):
        meio = 0.25 < i / max(1, len(pontos)) < 0.75
        brilho(t, px, py, 1 if meio else 2)
    if galhos and len(pontos) > 6:
        for _ in range(galhos):
            sx, sy = pontos[rnd.randrange(2, len(pontos) - 2)]
            ex = max(bx0, min(bx1, sx + rnd.choice((-4, -3, 3, 4))))
            ey = max(by0, min(by1, sy + rnd.choice((-3, 2, 3))))
            rachadura(t, sx, sy, ex, ey, rnd, caixa, galhos=0)


# ---------------------------------------------------------------- camada 1 (elmo, peitoral, bracos, botas)
def camada1():
    t = Tela(64 * S, 32 * S)
    rnd = random.Random(7)
    # ---- cabeca (0,0)-(32,16): topo, baixo, dir, frente, esq, tras
    for (u, v, w, h) in ((8, 0, 8, 8), (16, 0, 8, 8), (0, 8, 8, 8), (8, 8, 8, 8), (16, 8, 8, 8), (24, 8, 8, 8)):
        obsidiana(t, u * S, v * S, w * S, h * S, rnd, base=1)
    # frente do elmo: crista central, viseira, olhos brancos, grade da boca
    fx, fy = 8 * S, 8 * S
    placa(t, fx + 1, fy + 1, 14, 5, rnd)                     # testa
    for x in range(fx + 2, fx + 14):                          # pontas da coroa na testa (zigue-zague)
        k = (x - fx - 2) % 4
        top = fy + 1 + (0 if k == 0 else 1 if k in (1, 3) else 2)
        for y in range(fy, top):
            t.put(x, y, OBS[0])
    for x in (fx + 3, fx + 7, fx + 8, fx + 12):               # joias da coroa
        brilho(t, x, fy + 3, 2)
    for y in range(fy + 6, fy + 10):                          # viseira (faixa funda)
        for x in range(fx + 1, fx + 15):
            t.put(x, y, OBS[0])
    for (x0, x1) in ((fx + 2, fx + 6), (fx + 10, fx + 14)):  # OLHOS (brancos, com brilho)
        for x in range(x0, x1):
            t.put(x, fy + 7, G_CORE)
            t.put(x, fy + 8, G_CORE)
            t.put(x, fy + 6, G2)
            t.put(x, fy + 9, G2)
        for y in (fy + 7, fy + 8):
            t.put(x0 - 1, y, G1)
            t.put(x1, y, G1)
        t.put(x0 - 1, fy + 6, G3)
        t.put(x1, fy + 6, G3)
    for x in (fx + 3, fx + 12):                               # "lagrimas" de luz escorrendo dos olhos
        for y in range(fy + 10, fy + 14):
            t.put(x, y, G2 if y < fy + 12 else G3)
    for x in range(fx + 7, fx + 9):                            # nariz/crista
        for y in range(fy + 6, fy + 13):
            t.put(x, y, OBS[3] if x == fx + 7 else OBS[2])
    placa(t, fx + 1, fy + 10, 6, 5, rnd)
    placa(t, fx + 9, fy + 10, 6, 5, rnd)
    for x in range(fx + 3, fx + 14, 2):                       # grade da boca
        if fx + 6 < x < fx + 9:
            continue
        for y in range(fy + 12, fy + 14):
            t.put(x, y, OBS[0])
    # laterais do elmo: placa de orelha com runa
    for (sx, lado) in ((0, -1), (16, 1)):
        x0, y0 = sx * S, 8 * S
        placa(t, x0 + 4, y0 + 5, 9, 8, rnd)
        brilho(t, x0 + 8, y0 + 8, 1)
        brilho(t, x0 + 8, y0 + 9, 2)
        rachadura(t, x0 + 8, y0 + 9, x0 + 3, y0 + 14, rnd, (x0, y0, x0 + 15, y0 + 15), galhos=0)
    # topo: anel da coroa (pontas) + rachadura no meio
    tx, ty = 8 * S, 0
    for x in range(tx, tx + 16):
        for y in (ty, ty + 15):
            t.put(x, y, OBS[0])
    for x in range(tx + 1, tx + 15, 3):
        t.put(x, ty + 1, OBS[5])
        t.put(x, ty + 14, OBS[5])
    rachadura(t, tx + 3, ty + 4, tx + 12, ty + 11, rnd, (tx + 2, ty + 2, tx + 13, ty + 13), galhos=1)
    # nuca
    bx, by = 24 * S, 8 * S
    placa(t, bx + 2, by + 2, 12, 11, rnd)
    rachadura(t, bx + 8, by + 3, bx + 6, by + 12, rnd, (bx + 3, by + 3, bx + 12, by + 12), galhos=1)

    # ---- tronco (16,16)-(40,32): topo(20,16) baixo(28,16) dir(16,20) frente(20,20) esq(28,20) tras(32,20)
    for (u, v, w, h) in ((20, 16, 8, 4), (28, 16, 8, 4), (16, 20, 4, 12), (20, 20, 8, 12), (28, 20, 4, 12), (32, 20, 8, 12)):
        obsidiana(t, u * S, v * S, w * S, h * S, rnd, base=1)
    cx0, cy0 = 20 * S, 20 * S  # frente 16x24
    placa(t, cx0 + 1, cy0 + 1, 7, 8, rnd)                     # peitorais
    placa(t, cx0 + 8, cy0 + 1, 7, 8, rnd)
    for y in range(cy0 + 10, cy0 + 19, 3):                    # abdomen em faixas
        placa(t, cx0 + 2, y, 12, 3, rnd, base=2)
    for y in range(cy0 + 19, cy0 + 24):                       # cinto
        for x in range(cx0, cx0 + 16):
            t.put(x, y, OBS[0] if y in (cy0 + 19, cy0 + 23) else OBS[1])
    for x in range(cx0 + 6, cx0 + 10):                        # fivela
        for y in range(cy0 + 20, cy0 + 23):
            t.put(x, y, OBS[4])
    brilho(t, cx0 + 7, cy0 + 21, 1)
    brilho(t, cx0 + 8, cy0 + 21, 1)
    # nucleo: losango brilhante no peito com rachaduras saindo
    mx, my = cx0 + 7, cy0 + 7
    for dy in range(-3, 4):
        for dx in range(-3, 5):
            d = abs(dx - 0.5) + abs(dy)
            if d <= 1.5:
                t.put(mx + dx, my + dy, G_CORE)
            elif d <= 2.5:
                t.put(mx + dx, my + dy, G1)
            elif d <= 3.2:
                t.put(mx + dx, my + dy, G3)
    caixa = (cx0 + 1, cy0 + 1, cx0 + 14, cy0 + 18)
    rachadura(t, mx - 3, my + 1, cx0 + 1, cy0 + 16, rnd, caixa, galhos=1)
    rachadura(t, mx + 4, my + 1, cx0 + 14, cy0 + 17, rnd, caixa, galhos=1)
    rachadura(t, mx, my - 3, cx0 + 3, cy0 + 1, rnd, caixa, galhos=0)
    rachadura(t, mx + 1, my - 3, cx0 + 13, cy0 + 1, rnd, caixa, galhos=0)
    # costas: placas e "coluna" de pontos brilhantes
    kx, ky = 32 * S, 20 * S
    placa(t, kx + 1, ky + 1, 14, 9, rnd)
    for y in range(ky + 11, ky + 19, 4):
        placa(t, kx + 2, y, 12, 4, rnd)
    for y in range(ky + 2, ky + 19, 3):
        brilho(t, kx + 7, y, 1)
        brilho(t, kx + 8, y, 2)
    for y in range(ky + 19, ky + 24):
        for x in range(kx, kx + 16):
            t.put(x, y, OBS[0] if y in (ky + 19, ky + 23) else OBS[1])

    # ---- braco (40,16)-(56,32): topo(44,16) baixo(48,16) dir(40,20) frente(44,20) esq(48,20) tras(52,20)
    for (u, v, w, h) in ((44, 16, 4, 4), (48, 16, 4, 4), (40, 20, 4, 12), (44, 20, 4, 12), (48, 20, 4, 12), (52, 20, 4, 12)):
        obsidiana(t, u * S, v * S, w * S, h * S, rnd, base=1)
    # ombreira: as 4 faces de cima do braco ganham uma placa com borda brilhante
    for u in (40, 44, 48, 52):
        x0, y0 = u * S, 20 * S
        placa(t, x0, y0, 8, 7, rnd, base=2)
        for x in range(x0, x0 + 8):
            t.put(x, y0 + 6, G2 if (x - x0) % 3 else G1)
        rachadura(t, x0 + 2, y0 + 9, x0 + 5, y0 + 21, rnd, (x0 + 1, y0 + 8, x0 + 6, y0 + 22), galhos=0)
    ox, oy = 44 * S, 16 * S  # topo do ombro
    placa(t, ox, oy, 8, 8, rnd, base=3)
    brilho(t, ox + 3, oy + 3, 1)
    brilho(t, ox + 4, oy + 4, 1)
    for u in (40, 44, 48, 52):                                # punho
        x0, y0 = u * S, 20 * S
        for x in range(x0, x0 + 8):
            t.put(x, y0 + 21, OBS[4])
            t.put(x, y0 + 22, G2 if (x - x0) % 2 == 0 else G3)

    # ---- pernas (botas) (0,16)-(16,32): so a parte de baixo (botas) importa; o resto fica igual (coberto pela calca)
    for (u, v, w, h) in ((4, 16, 4, 4), (8, 16, 4, 4), (0, 20, 4, 12), (4, 20, 4, 12), (8, 20, 4, 12), (12, 20, 4, 12)):
        obsidiana(t, u * S, v * S, w * S, h * S, rnd, base=1)
    for u in (0, 4, 8, 12):
        x0, y0 = u * S, 20 * S
        placa(t, x0, y0 + 14, 8, 4, rnd, base=2)              # cano da bota
        for x in range(x0, x0 + 8):
            t.put(x, y0 + 14, G2 if (x - x0) % 2 else G1)      # anel brilhante no tornozelo
        placa(t, x0, y0 + 18, 8, 6, rnd, base=2)
        if u == 4:                                            # bico (frente) com runa
            for x in range(x0 + 2, x0 + 6):
                t.put(x, y0 + 22, G1)
            brilho(t, x0 + 3, y0 + 20, 1)
            brilho(t, x0 + 4, y0 + 20, 1)
    return t.im


# ---------------------------------------------------------------- camada 2 (calca)
def camada2():
    t = Tela(64 * S, 32 * S)
    rnd = random.Random(11)
    # cintura: tronco (16,16)-(40,32) so a parte de baixo
    for (u, v, w, h) in ((20, 16, 8, 4), (28, 16, 8, 4), (16, 20, 4, 12), (20, 20, 8, 12), (28, 20, 4, 12), (32, 20, 8, 12)):
        obsidiana(t, u * S, v * S, w * S, h * S, rnd, base=1)
    for (u, w) in ((16, 4), (20, 8), (28, 4), (32, 8)):
        x0, y0 = u * S, 20 * S
        for x in range(x0, x0 + w * S):
            for y in range(y0 + 16, y0 + 20):
                t.put(x, y, OBS[0] if y in (y0 + 16, y0 + 19) else OBS[2])
            if (x - x0) % 4 == 1:
                t.put(x, y0 + 17, G2)
    # pernas (0,16)-(16,32)
    for (u, v, w, h) in ((4, 16, 4, 4), (8, 16, 4, 4), (0, 20, 4, 12), (4, 20, 4, 12), (8, 20, 4, 12), (12, 20, 4, 12)):
        obsidiana(t, u * S, v * S, w * S, h * S, rnd, base=1)
    for u in (0, 4, 8, 12):
        x0, y0 = u * S, 20 * S
        placa(t, x0 + 1, y0 + 1, 6, 8, rnd)                   # coxa
        if u in (4, 12):                                      # runa na coxa (frente e tras)
            for (dx, dy) in ((3, 3), (4, 3), (2, 4), (5, 4), (3, 5), (4, 5), (3, 6), (4, 6)):
                t.put(x0 + dx, y0 + dy, G1 if dy in (3, 6) else G2)
        placa(t, x0 + 1, y0 + 10, 6, 5, rnd, base=3)          # joelheira
        brilho(t, x0 + 3, y0 + 12, 1)
        brilho(t, x0 + 4, y0 + 12, 1)
        brilho(t, x0 + 3, y0 + 13, 2)
        brilho(t, x0 + 4, y0 + 13, 2)
        if u in (0, 8):                                       # costura brilhante na lateral
            for y in range(y0 + 1, y0 + 22):
                if y % 3:
                    t.put(x0 + 3, y, G2)
                    t.put(x0 + 4, y, G3)
    return t.im


# ---------------------------------------------------------------- icones 32x32
def icone(desenho, semente):
    t = Tela(32, 32)
    rnd = random.Random(semente)
    desenho(t, rnd)
    # contorno escuro
    alfa = [[t.get(x, y)[3] for x in range(32)] for y in range(32)]
    for y in range(32):
        for x in range(32):
            if alfa[y][x]:
                continue
            if any(0 <= x + dx < 32 and 0 <= y + dy < 32 and alfa[y + dy][x + dx] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                t.put(x, y, (5, 4, 8))
    return t.im


def forma(t, rnd, linhas, x0, y0, base=2):
    for dy, (a, b) in enumerate(linhas):
        for x in range(a, b):
            r = rnd.random()
            i = base + (1 if r > 0.8 else 0) - (1 if r < 0.15 else 0)
            t.put(x0 + x, y0 + dy, OBS[max(0, min(4, i))])
        t.put(x0 + a, y0 + dy, OBS[4])
        t.put(x0 + b - 1, y0 + dy, OBS[0])


def i_elmo(t, rnd):
    linhas = [(9, 23), (7, 25), (6, 26), (5, 27), (5, 27), (4, 28), (4, 28), (4, 28), (4, 28), (4, 28), (4, 28), (4, 28),
              (4, 11), (4, 11), (4, 10), (4, 10), (5, 10)]
    linhas = [(a, b) for (a, b) in linhas]
    forma(t, rnd, linhas, 0, 6)
    for i, (a, b) in enumerate(linhas[12:]):                  # lado direito espelhado
        for x in range(32 - b, 32 - a):
            t.put(x, 6 + 12 + i, OBS[2])
        t.put(32 - b, 6 + 12 + i, OBS[4])
    for x in range(9, 23, 3):                                 # pontas da coroa
        t.put(x, 5, OBS[4])
        t.put(x + 1, 4, OBS[5])
    brilho(t, 16, 8, 1)
    for x in range(6, 26):                                    # viseira
        t.put(x, 14, OBS[0])
        t.put(x, 17, OBS[0])
    for x0 in (7, 18):                                        # olhos
        for x in range(x0, x0 + 7):
            t.put(x, 15, G_CORE)
            t.put(x, 16, G1)
    rachadura(t, 22, 9, 26, 13, rnd, (5, 7, 27, 13), galhos=0)


def i_peitoral(t, rnd):
    linhas = [(3, 11), (2, 12), (2, 30), (2, 30), (3, 29), (3, 29), (8, 24), (8, 24), (8, 24), (8, 24), (8, 24), (8, 24),
              (8, 24), (8, 24), (8, 24), (8, 24), (8, 24), (8, 24), (9, 23), (9, 23)]
    forma(t, rnd, linhas, 0, 5)
    for i, (a, b) in enumerate(linhas[:2]):                   # ombro direito
        for x in range(32 - b, 32 - a):
            t.put(x, 5 + i, OBS[3])
    for x in range(8, 24):                                    # cinto
        t.put(x, 22, OBS[0])
        t.put(x, 23, OBS[1])
    for dy in range(-3, 4):
        for dx in range(-3, 4):
            d = abs(dx) + abs(dy)
            if d <= 1:
                t.put(16 + dx, 13 + dy, G_CORE)
            elif d <= 2:
                t.put(16 + dx, 13 + dy, G1)
            elif d <= 3:
                t.put(16 + dx, 13 + dy, G3)
    rachadura(t, 13, 15, 9, 21, rnd, (8, 11, 23, 21), galhos=0)
    rachadura(t, 19, 15, 22, 21, rnd, (8, 11, 23, 21), galhos=0)
    for x in range(3, 12, 2):
        t.put(x, 9, G2)
        t.put(31 - x, 9, G2)


def i_calca(t, rnd):
    linhas = [(7, 25)] * 4 + [(7, 15), (7, 15)] * 0
    forma(t, rnd, [(7, 25)] * 4, 0, 5)
    for i in range(18):
        for (a, b) in ((7, 15), (17, 25)):
            for x in range(a, b):
                r = rnd.random()
                t.put(x, 9 + i, OBS[2 + (1 if r > 0.85 else 0) - (1 if r < 0.12 else 0)])
            t.put(a, 9 + i, OBS[4])
            t.put(b - 1, 9 + i, OBS[0])
    for x in range(7, 25):
        t.put(x, 5, OBS[0])
        t.put(x, 6, G2 if x % 3 else G1)
    for (a, b) in ((7, 15), (17, 25)):
        for y in range(16, 20):
            for x in range(a + 1, b - 1):
                t.put(x, y, OBS[3])
        brilho(t, (a + b) // 2, 18, 1)
        brilho(t, (a + b) // 2, 11, 1)
        brilho(t, (a + b) // 2, 12, 2)
        for y in range(9, 26, 2):
            t.put(a + 1, y, G2)
        for x in range(a, b):
            t.put(x, 26, G2 if x % 2 else G1)


def i_botas(t, rnd):
    for (x0, flip) in ((4, False), (17, False)):
        linhas = [(0, 7)] * 9 + [(0, 11), (0, 11), (0, 11)]
        forma(t, rnd, linhas, x0, 13)
        for x in range(x0, x0 + 7):
            t.put(x, 15, G2 if x % 2 else G1)
        for x in range(x0 + 6, x0 + 10):
            t.put(x, 23, G1)
        for x in range(x0 + 1, x0 + 10):
            t.put(x, 24, G2 if x % 2 else G3)
        brilho(t, x0 + 3, 19, 1)
        brilho(t, x0 + 3, 20, 2)


def i_espada(t, rnd):
    """Espada larga: lamina de obsidiana com rachadura brilhante no meio, fio claro, guarda de coroa e olho branco."""
    import math
    # lamina: da guarda (9,22) ate a ponta (28,3), 5 px de largura na diagonal
    for i in range(0, 21):
        cx, cy = 9 + i, 22 - i
        larg = 2 if i < 17 else (1 if i < 19 else 0)
        for k in range(-larg, larg + 1):
            x, y = cx + k, cy + k
            if k == -larg:
                c = G1 if (i % 4) else G_CORE                  # fio de cima brilhando
            elif k == larg:
                c = OBS[0]
            else:
                r = rnd.random()
                c = OBS[3] if r > 0.8 else OBS[2] if r > 0.3 else OBS[1]
            t.put(x, y, c)
        if 2 < i < 17:                                        # rachadura brilhante no meio da lamina
            t.put(cx, cy, G2 if i % 3 else G_CORE)
    for (x, y) in ((28, 3), (29, 2), (28, 2)):
        t.put(x, y, G_CORE)
    # guarda em forma de coroa (perpendicular a lamina)
    guarda = [(-5, 0), (-4, 0), (-3, 0), (-2, 0), (-1, 0), (0, 0), (1, 0), (2, 0), (3, 0), (4, 0), (5, 0),
              (-5, -1), (-3, -1), (0, -1), (3, -1), (5, -1), (-5, -2), (0, -2), (5, -2)]
    for (a2, b2) in guarda:
        x = 8 + a2 + b2 * -1
        y = 23 - a2 * -1 + b2 * -1
        x, y = 8 + a2 - b2, 23 + a2 - b2
        x, y = 8 + a2 + b2, 23 + a2 - b2
        t.put(x, y, OBS[4] if b2 == 0 else OBS[3])
    for (x, y) in ((3, 18), (13, 28)):                        # pontas da guarda brilhando
        brilho(t, x, y, 1)
    # olho branco no centro da guarda
    for (x, y) in ((8, 23), (9, 23), (8, 24)):
        t.put(x, y, G_CORE)
    t.put(7, 23, G1)
    t.put(9, 24, G1)
    # cabo enrolado e pomo brilhante
    for i in range(1, 6):
        x, y = 8 - i, 24 + i
        t.put(x, y, OBS[3] if i % 2 else OBS[1])
        t.put(x + 1, y, OBS[2] if i % 2 else OBS[0])
    for (x, y) in ((1, 30), (2, 30), (1, 29), (2, 29)):
        t.put(x, y, G1)
    t.put(1, 30, G_CORE)
    # faiscas em volta
    for (x, y) in ((24, 10), (18, 9), (26, 15), (13, 13)):
        t.put(x, y, G2)


def previa_corpo(c1, c2, out):
    """Vista de frente aproximada (para conferir), 10x."""
    E = 10
    pele = (40, 34, 30, 255)
    img = Image.new('RGBA', (16 * E + 2 * 4 * E + 40, 32 * E + 40), (225, 225, 225, 255))
    def face(src, u, v, w, h, dx, dy, mirror=False):
        reg = src.crop((u * S, v * S, (u + w) * S, (v + h) * S)).resize((w * E, h * E), Image.NEAREST)
        if mirror:
            reg = reg.transpose(Image.FLIP_LEFT_RIGHT)
        base = Image.new('RGBA', reg.size, pele)
        base.alpha_composite(reg)
        img.alpha_composite(base, (dx, dy))
    ox = 20 + 4 * E
    face(c1, 8, 8, 8, 8, ox, 20)                        # cabeca
    face(c1, 20, 20, 8, 12, ox, 20 + 8 * E)             # tronco
    face(c1, 44, 20, 4, 12, ox - 4 * E, 20 + 8 * E)     # braco dir
    face(c1, 44, 20, 4, 12, ox + 8 * E, 20 + 8 * E, True)
    # pernas: calca em cima das botas
    for (dx, m) in ((0, False), (4 * E, True)):
        reg2 = c2.crop((4 * S, 20 * S, 8 * S, 32 * S)).resize((4 * E, 12 * E), Image.NEAREST)
        reg1 = c1.crop((4 * S, 20 * S, 8 * S, 32 * S)).resize((4 * E, 12 * E), Image.NEAREST)
        base = Image.new('RGBA', reg1.size, pele)
        base.alpha_composite(reg2)
        bot = reg1.crop((0, 7 * E, 4 * E, 12 * E))
        base.alpha_composite(bot, (0, 7 * E))
        if m:
            base = base.transpose(Image.FLIP_LEFT_RIGHT)
        img.alpha_composite(base, (ox + dx, 20 + 20 * E))
    img.save(out)


if __name__ == '__main__':
    c1, c2 = camada1(), camada2()
    c1.save(f'{OUT}/herobrine_layer1.png')
    c2.save(f'{OUT}/herobrine_layer2.png')
    icones = {'elmo': icone(i_elmo, 1), 'peitoral': icone(i_peitoral, 2), 'calca': icone(i_calca, 3), 'botas': icone(i_botas, 4), 'espada': icone(i_espada, 5)}
    for k, im in icones.items():
        im.save(f'{OUT}/herobrine_{k}.png')
    previa_corpo(c1, c2, f'{OUT}/previa_corpo.png')
    folha = Image.new('RGBA', (5 * 32 * 6 + 60, 32 * 6 + 20), (200, 200, 200, 255))
    for i, k in enumerate(icones):
        folha.alpha_composite(icones[k].resize((192, 192), Image.NEAREST), (10 + i * 202, 10))
    folha.save(f'{OUT}/previa_icones.png')
    big = Image.new('RGBA', (c1.width * 5, c1.height * 10 + 20), (120, 120, 120, 255))
    big.alpha_composite(c1.resize((c1.width * 5, c1.height * 5), Image.NEAREST), (0, 0))
    big.alpha_composite(c2.resize((c2.width * 5, c2.height * 5), Image.NEAREST), (0, c1.height * 5 + 20))
    big.save(f'{OUT}/previa_texturas.png')
    print('ok', OUT)
