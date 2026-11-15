"""Pintura em alta resolucao (8x = 512x256) das peles dos infectados: pele podre com sombra, olho fundo, boca rasgada
com sangue escorrendo, roupa rasgada e suja. Usado por zumbis_skin.py (camadas_hd). Desenho original do Corona Server.
"""
import numpy as np

K = 8


def _hash(ix, iy, seed):
    n = (ix * 374761393 + iy * 668265263 + seed * 1442695041) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def ruido(x, y, seed):
    ix, iy = np.floor(x).astype(np.int64), np.floor(y).astype(np.int64)
    fx, fy = x - ix, y - iy
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a, b = _hash(ix, iy, seed), _hash(ix + 1, iy, seed)
    c, d = _hash(ix, iy + 1, seed), _hash(ix + 1, iy + 1, seed)
    return (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy


def fbm(x, y, seed, oit=4):
    t, amp, tot = 0.0, 1.0, 0.0
    for o in range(oit):
        t = t + ruido(x * (2 ** o), y * (2 ** o), seed + o * 17) * amp
        tot += amp
        amp *= 0.5
    return t / tot


def elipse(U, V, cx, cy, rx, ry, suave=0.35):
    d = np.sqrt(((U - cx) / rx) ** 2 + ((V - cy) / ry) ** 2)
    return np.clip((1 - d) / suave, 0, 1)


class Tela:
    """Uma face (w x h em pixels do jogo) pintada em w*K x h*K. U, V em 0..1; X, Y em pixels do jogo."""

    def __init__(self, w, h, seed):
        self.w, self.h, self.seed = w, h, seed
        yy, xx = np.mgrid[0:h * K, 0:w * K]
        self.X, self.Y = (xx + 0.5) / K, (yy + 0.5) / K
        self.U, self.V = self.X / w, self.Y / h
        self.rgb = np.zeros((h * K, w * K, 3))
        self.a = np.ones((h * K, w * K))

    def base(self, cor, var=0.22, esc=0.9):
        n = fbm(self.X * esc, self.Y * esc, self.seed)
        fino = ruido(self.X * 5.0, self.Y * 5.0, self.seed + 91)
        self.rgb[:] = np.array(cor, float) / 255.0
        self.rgb *= (1 - var / 2 + var * n)[..., None] * (0.95 + 0.1 * fino)[..., None]
        return self

    def mult(self, mask, f):
        self.rgb *= (1 - mask * (1 - f))[..., None]

    def cor(self, mask, cor, forca=1.0):
        m = np.clip(mask * forca, 0, 1)[..., None]
        self.rgb = self.rgb * (1 - m) + np.array(cor, float) / 255.0 * m

    def borda(self, f=0.72, larg=0.16):
        e = np.minimum(np.minimum(self.U, 1 - self.U) * self.w, np.minimum(self.V, 1 - self.V) * self.h) / (larg * min(self.w, self.h))
        self.mult(np.clip(1 - e, 0, 1), f)

    def manchas(self, cor, limiar=0.58, esc=0.7, forca=0.85, seed=0):
        n = fbm(self.X * esc, self.Y * esc, self.seed + 300 + seed)
        self.cor(np.clip((n - limiar) * 9, 0, 1), cor, forca)

    def sangue(self, cx, cy, r, escorre=0.5, seed=0, cor=(118, 10, 12)):
        """Mancha de sangue com respingos e fios escorrendo para baixo (cx, cy, r em fracao da face)."""
        n = fbm(self.X * 1.6, self.Y * 1.6, self.seed + 500 + seed)
        m = elipse(self.U, self.V, cx, cy, r * (0.7 + 0.8 * n), r * (0.7 + 0.8 * n) * self.w / self.h, 0.5)
        col = ruido(self.X * 2.4, self.Y * 0.0 + 3.3, self.seed + 700 + seed)
        fio = (col > 0.55) * (np.abs(self.U - cx) < r * 0.9) * (self.V > cy) * (self.V < cy + escorre * (0.4 + col))
        m = np.maximum(m, fio * 0.9)
        self.cor(m, cor, 0.92)
        self.cor(m * (n > 0.55), (60, 4, 6), 0.7)

    def rasgo(self, cx, cy, rx, ry, dentro, seed=0):
        """Buraco na roupa/pele de borda irregular mostrando 'dentro' (cor), com sombra na borda."""
        n = fbm(self.X * 2.2, self.Y * 2.2, self.seed + 900 + seed)
        d = np.sqrt(((self.U - cx) / rx) ** 2 + ((self.V - cy) / ry) ** 2) + (n - 0.5) * 0.9
        self.mult(np.clip((1.25 - d) * 4, 0, 1) * (d > 1.0), 0.5)
        m = (d < 1.0).astype(float)
        cor = np.array(dentro, float) / 255.0 * (0.75 + 0.5 * n)[..., None]
        self.rgb = self.rgb * (1 - m[..., None]) + cor * m[..., None]
        return m

    def dobras(self, forca=0.22, esc=1.3):
        n = fbm(self.X * esc * 1.8, self.Y * esc * 0.45, self.seed + 40)
        trama = 0.97 + 0.06 * (np.sin(self.X * K * 1.9) * np.sin(self.Y * K * 1.9) > 0)
        self.rgb *= ((1 - forca / 2 + forca * n) * trama)[..., None]

    def sujeira(self, de=0.6, forca=0.5):
        n = fbm(self.X * 1.1, self.Y * 1.1, self.seed + 77)
        self.mult(np.clip((self.V - de) / (1 - de), 0, 1) * (0.5 + n), 1 - forca)

    def img(self):
        out = np.zeros((self.h * K, self.w * K, 4), np.uint8)
        out[..., :3] = (np.clip(self.rgb, 0, 1) * 255 + 0.5).astype(np.uint8)
        out[..., 3] = np.where(self.a > 0.5, 255, 0)
        return out


def rosto(t, seed):
    """t: dict do tipo (pele, olho, cabelo, boca aberta 0..1, capuz cor ou None, ...)."""
    f = Tela(8, 8, seed).base(t['pele'], 0.3)
    U, V = f.U, f.V
    f.manchas(tuple(int(c * 0.55) for c in t['pele']), 0.6, 0.8, 0.6)
    # macas do rosto fundas, tempora, queixo
    f.mult(elipse(U, V, 0.16, 0.66, 0.16, 0.2, 0.9), 0.6)
    f.mult(elipse(U, V, 0.84, 0.66, 0.16, 0.2, 0.9), 0.6)
    f.mult(np.clip((V - 0.3) * -6 + 0.6, 0, 1) * 0.0 + elipse(U, V, 0.5, 0.36, 0.5, 0.05, 1.0), 0.72)       # sobrancelha
    # orbitas fundas
    for cx in (0.29, 0.71):
        f.mult(elipse(U, V, cx, 0.47, 0.17, 0.115, 0.6), 0.16)
        olho = elipse(U, V, cx, 0.475, 0.075, 0.05, 0.4)
        f.cor(olho, t['olho'])
        f.cor(elipse(U, V, cx + (0.012 if cx < 0.5 else -0.012), 0.478, 0.022, 0.022, 0.5), (10, 6, 6))
        f.cor(elipse(U, V, cx, 0.56, 0.12, 0.03, 1.0), (70, 20, 30), 0.45)                                # olheira
    # nariz comido
    f.mult(elipse(U, V, 0.5, 0.6, 0.07, 0.07, 0.6), 0.3)
    f.mult(elipse(U, V, 0.5, 0.5, 0.035, 0.1, 1.0), 0.75)
    # boca: buraco escuro, dentes em cima e embaixo, labio rasgado para um lado
    ab = t.get('boca', 0.5)
    my, ry = 0.80, 0.045 + 0.06 * ab
    boca = elipse(U, V, 0.5, my, 0.27, ry, 0.25) + elipse(U, V, 0.74, my - 0.03, 0.12, ry * 0.8, 0.3) * t.get('rasgada', 1)
    boca = np.clip(boca, 0, 1)
    f.cor(boca, (20, 4, 6))
    dn = fbm(f.X * 6, f.Y * 0 + 1.5, seed + 5, 2)
    dente = (np.mod(U * 8 * 1.6, 1.0) > 0.22) * (dn > 0.3)
    f.cor(boca * dente * (V < my - ry * 0.25), (224, 212, 176), 0.95)
    f.cor(boca * dente * (V > my + ry * 0.45), (198, 186, 150), 0.9)
    f.sangue(0.5, my + 0.03, 0.2, 0.3, 1, t.get('baba', (118, 10, 12)))
    # ferida na testa / bochecha
    f.rasgo(0.78, 0.2, 0.1, 0.07, (120, 22, 24), 2)
    f.rasgo(0.14, 0.58, 0.07, 0.09, (110, 18, 22), 3)
    # veias
    v = np.abs(fbm(f.X * 1.7, f.Y * 1.7, seed + 13, 3) - 0.5) < 0.012
    f.cor(v * (V < 0.72), (58, 30, 62), 0.28)
    # cabelo ou capuz
    n = fbm(f.X * 2.5, f.Y * 0.8, seed + 21)
    if t.get('capuz'):
        aro = np.clip(np.maximum(np.maximum(0.2 - V + n * 0.08, 0.14 - U + n * 0.05), U - 0.86 + n * 0.05) * 14, 0, 1)
        f.mult(np.clip((0.5 - V) * 2.4, 0, 1), 0.35)                    # sombra do capuz no rosto
        cap = Tela(8, 8, seed + 1).base(t['capuz'], 0.25)
        cap.dobras(0.3)
        f.rgb = f.rgb * (1 - aro[..., None]) + cap.rgb * aro[..., None]
        f.mult((aro > 0) * (aro < 1), 0.6)
    else:
        cab = np.clip((0.17 + n * 0.14 - V) * 16, 0, 1) * t.get('cabelo_qtd', 1)
        fios = 0.7 + 0.5 * ruido(f.X * 9, f.Y * 1.2, seed + 31)
        f.rgb = f.rgb * (1 - cab[..., None]) + (np.array(t['cabelo'], float) / 255.0)[None, None, :] * fios[..., None] * cab[..., None]
    f.borda(0.8, 0.1)
    return f.img()


def cabeca_lado(t, seed, nuca=False, topo=False):
    f = Tela(8, 8, seed).base(t['capuz'] if t.get('capuz') else t['pele'], 0.3)
    U, V = f.U, f.V
    if t.get('capuz'):
        f.dobras(0.35)
        f.borda(0.7, 0.14)
        return f.img()
    f.manchas(tuple(int(c * 0.55) for c in t['pele']), 0.6, 0.8, 0.6)
    n = fbm(f.X * 2.5, f.Y * 0.8, seed + 21)
    lim = 1.2 if topo else (0.5 if nuca else 0.26)
    cab = np.clip((lim + n * 0.2 - V) * 12, 0, 1) * t.get('cabelo_qtd', 1)
    if topo:
        cab *= 1 - elipse(U, V, 0.68, 0.3, 0.2, 0.16, 0.5) * 0.0
    fios = 0.7 + 0.5 * ruido(f.X * 9, f.Y * 1.2, seed + 31)
    f.rgb = f.rgb * (1 - cab[..., None]) + (np.array(t['cabelo'], float) / 255.0)[None, None, :] * fios[..., None] * cab[..., None]
    if topo:
        f.rasgo(0.68, 0.3, 0.17, 0.13, (130, 24, 26), 6)
        f.cor(elipse(U, V, 0.68, 0.3, 0.07, 0.05, 0.6), (206, 196, 168), 0.8)
    elif not nuca:
        f.mult(elipse(U, V, 0.5, 0.5, 0.1, 0.13, 0.5), 0.5)             # orelha
        f.sangue(0.5, 0.62, 0.1, 0.25, 4)
    else:
        f.rasgo(0.4, 0.72, 0.14, 0.1, (120, 22, 24), 5)
        f.sangue(0.4, 0.8, 0.12, 0.2, 5)
    f.borda(0.78, 0.12)
    return f.img()


def tronco(t, seed, frente=True, w=8):
    """Camisa (ou torso nu) do tipo. frente=False: costas/lados (sem os detalhes do peito)."""
    nu = t.get('torso_nu')
    f = Tela(w, 12, seed).base(t['pele'] if nu else t['camisa'], 0.3 if nu else 0.2)
    U, V = f.U, f.V
    pele_esc = tuple(int(c * 0.6) for c in t['pele'])
    if nu:
        f.manchas(pele_esc, 0.6, 0.8, 0.55)
        if frente and w == 8:
            for cx in (0.28, 0.72):                                      # peitoral
                f.mult(elipse(U, V, cx, 0.36, 0.26, 0.05, 1.0), 0.6)
                f.cor(elipse(U, V, cx, 0.2, 0.2, 0.12, 1.0), tuple(min(255, int(c * 1.25)) for c in t['pele']), 0.5)
            f.mult(np.clip(1 - np.abs(U - 0.5) * 30, 0, 1) * (V > 0.1) * (V < 0.9), 0.6)
            for k in range(3):                                           # abdomen
                f.mult(np.clip(1 - np.abs(V - (0.5 + k * 0.13)) * 60, 0, 1) * (np.abs(U - 0.5) < 0.3), 0.68)
        elif w == 8:
            f.mult(np.clip(1 - np.abs(U - 0.5) * 22, 0, 1), 0.6)         # espinha
            for cx in (0.25, 0.75):
                f.mult(elipse(U, V, cx, 0.3, 0.2, 0.16, 1.0) * 0.5, 0.7)
        v = np.abs(fbm(f.X * 1.5, f.Y * 1.5, seed + 13, 3) - 0.5) < 0.014
        f.cor(v, (70, 34, 70), 0.3)
        for k, (cx, cy) in enumerate(((0.3, 0.62), (0.7, 0.3), (0.55, 0.85))):
            if w == 8:
                f.rasgo(cx, cy, 0.09, 0.035, (126, 20, 22), 20 + k)
    else:
        f.dobras(0.26)
        if t.get('listras'):
            lis = (np.mod(V * 12 / 2.4 + 0.3, 1.0) < 0.3)
            f.cor(lis, t['listras'], 0.85)
        if frente and w == 8:
            if t.get('ziper'):
                f.cor(np.clip(1 - np.abs(U - 0.5) * 34, 0, 1), (30, 30, 34), 0.9)
                f.cor((np.abs(U - 0.5) < 0.012) * (np.mod(V * 40, 1) < 0.5), (150, 150, 150), 0.9)
                f.mult(elipse(U, V, 0.5, 0.72, 0.4, 0.07, 1.0) * 0.6, 0.7)          # bolso canguru
            else:
                gola = np.clip((0.2 - V - np.abs(U - 0.5) * 0.9) * 14, 0, 1)        # gola em V com a pele
                sk = Tela(w, 12, seed + 2).base(t['pele'], 0.3)
                f.rgb = f.rgb * (1 - gola[..., None]) + sk.rgb * gola[..., None]
                f.mult((gola > 0) * (gola < 1), 0.6)
                if t.get('botoes'):
                    for k in range(4):
                        f.cor(elipse(U, V, 0.5, 0.3 + k * 0.17, 0.03, 0.02, 0.5), (210, 210, 200), 0.9)
            if t.get('barriga'):                                                     # barriga inchada para fora da camisa
                m = f.rasgo(0.5, 0.68, 0.42, 0.26, t['pele'], 30)
                f.mult(m * elipse(U, V, 0.5, 0.98, 0.5, 0.18, 1.0), 0.6)
                f.cor(m * elipse(U, V, 0.4, 0.6, 0.22, 0.1, 1.0), tuple(min(255, int(c * 1.2)) for c in t['pele']), 0.5)
                for k, (cx, cy) in enumerate(((0.3, 0.62), (0.62, 0.74), (0.5, 0.52), (0.72, 0.58))):
                    f.cor(elipse(U, V, cx, cy, 0.06, 0.04, 0.5), t['bolha'], 0.95)
                    f.cor(elipse(U, V, cx - 0.012, cy - 0.01, 0.02, 0.013, 0.6), (240, 255, 200), 0.8)
            elif t.get('dinamite'):
                for k in range(5):
                    cx = 0.14 + k * 0.18
                    cil = (np.abs(U - cx) < 0.07) * (V > 0.3) * (V < 0.74)
                    luz = 0.6 + 0.6 * np.clip(1 - np.abs(U - cx + 0.02) * 22, 0, 1)
                    f.rgb = np.where(cil[..., None], (np.array((176, 30, 26)) / 255.0)[None, None, :] * luz[..., None], f.rgb)
                    f.cor((np.abs(U - cx) < 0.07) * (np.abs(V - 0.32) < 0.012), (90, 14, 14), 1)
                    f.cor((np.abs(U - cx) < 0.012) * (V > 0.2) * (V < 0.3), (40, 40, 40), 1)
                for cy in (0.4, 0.64):
                    f.cor((np.abs(V - cy) < 0.03), (36, 36, 40), 0.95)
                f.cor(elipse(U, V, 0.5, 0.52, 0.09, 0.05, 0.3), (24, 24, 26), 1)                  # detonador
                f.cor(elipse(U, V, 0.5, 0.52, 0.03, 0.02, 0.5), (255, 60, 30), 1)
            else:
                m = f.rasgo(0.36, 0.56, 0.2, 0.2, (122, 22, 24), 31)                               # barriga aberta
                for k in range(4):
                    f.cor(m * (np.abs(V - (0.44 + k * 0.075)) < 0.014), (214, 204, 176), 0.95)
                f.sangue(0.4, 0.74, 0.22, 0.25, 7)
            f.sangue(0.5, 0.14, 0.2, 0.4, 8, t.get('baba', (118, 10, 12)))                          # o que escorreu da boca
        elif w == 8:
            f.rasgo(0.62, 0.36, 0.12, 0.08, pele_esc, 32)
            f.sangue(0.3, 0.6, 0.14, 0.3, 9)
        f.rasgo(0.2, 0.97, 0.16, 0.06, pele_esc, 33)
        f.rasgo(0.75, 1.0, 0.14, 0.08, pele_esc, 34)
    if t.get('correntes') and w == 8:
        for s in (1, -1):
            d = np.abs((U - 0.5) * s - (V - 0.45) * 0.9)
            elo = np.mod((U + V) * 14, 1) < 0.6
            f.cor((d < 0.035) * elo, (150, 150, 158), 0.95)
            f.cor((d < 0.035) * ~elo, (70, 70, 78), 0.95)
    if t.get('costelas') and frente and w == 8:
        m = f.rasgo(0.5, 0.45, 0.3, 0.3, (60, 8, 10), 35)
        for k in range(5):
            f.cor(m * (np.abs(V - (0.24 + k * 0.1) - np.abs(U - 0.5) * 0.25) < 0.017) * (np.abs(U - 0.5) > 0.03), (220, 210, 184), 0.97)
        f.cor(m * (np.abs(U - 0.5) < 0.025), (200, 190, 164), 0.9)
    f.sujeira(0.55, 0.4)
    f.borda(0.74, 0.14)
    return f.img()


def braco(t, seed, frente=True):
    f = Tela(4, 12, seed).base(t['pele'], 0.3)
    U, V = f.U, f.V
    f.manchas(tuple(int(c * 0.55) for c in t['pele']), 0.6, 0.9, 0.55)
    v = np.abs(fbm(f.X * 1.6, f.Y * 1.2, seed + 13, 3) - 0.5) < 0.016
    f.cor(v, (62, 32, 66), 0.28)
    f.rasgo(0.55, 0.62, 0.22, 0.05, (124, 20, 22), 40)
    manga = t.get('manga', 0.35)
    if manga > 0 and not t.get('torso_nu'):
        n = fbm(f.X * 2.2, f.Y * 1.0, seed + 44)
        m = np.clip((manga + (n - 0.5) * 0.14 - V) * 14, 0, 1)
        r = Tela(4, 12, seed + 3).base(t['camisa'], 0.2)
        r.dobras(0.28)
        if t.get('listras'):
            r.cor((np.mod(V * 12 / 2.4 + 0.3, 1.0) < 0.3), t['listras'], 0.85)
        f.rgb = f.rgb * (1 - m[..., None]) + r.rgb * m[..., None]
        f.mult((m > 0) * (m < 1), 0.55)
    if t.get('faixas'):
        for cy in (0.5, 0.78):
            f.cor(np.abs(V - cy) < 0.035, t['faixas'], 0.92)
            f.mult((np.abs(V - cy) < 0.045) * (np.abs(V - cy) > 0.03), 0.6)
    # mao: dedos e sangue
    f.cor(np.clip((V - 0.82) * 7, 0, 1) * (0.6 + 0.6 * fbm(f.X * 2, f.Y * 2, seed + 50)), (104, 10, 12), 0.85)
    f.mult((V > 0.9) * (np.mod(U * 4, 1) < 0.14), 0.4)
    f.borda(0.72, 0.16)
    return f.img()


def perna(t, seed):
    f = Tela(4, 12, seed).base(t['calca'], 0.2)
    U, V = f.U, f.V
    f.dobras(0.3)
    f.rasgo(0.5, 0.45, 0.3, 0.07, tuple(int(c * 0.8) for c in t['pele']), 60)
    f.rasgo(0.25, 0.8, 0.18, 0.04, tuple(int(c * 0.7) for c in t['pele']), 61)
    f.sangue(0.6, 0.2, 0.25, 0.3, 11)
    if t.get('faixas'):
        f.cor(np.abs(V - 0.62) < 0.035, t['faixas'], 0.92)
    f.sujeira(0.5, 0.55)
    f.borda(0.72, 0.16)
    return f.img()


def sapato(seed):
    f = Tela(4, 3, seed).base((44, 36, 30), 0.3)
    f.cor(f.V > 0.72, (16, 14, 14), 0.95)
    f.cor((np.abs(f.V - 0.3) < 0.07) * (np.mod(f.U * 4, 1) < 0.5), (120, 110, 96), 0.7)
    f.sujeira(0.2, 0.4)
    return f.img()


def liso(w, h, cor, seed, f=1.0):
    t = Tela(w, h, seed).base(tuple(int(c * f) for c in cor), 0.25)
    t.borda(0.75, 0.2)
    return t.img()
