"""Trajes em alta resolucao (8x: 512x256 por camada, icones 64x64) para o Java e o Bedrock:
- "herobrine": obsidiana cristalina com rachaduras de luz branco-azulada, olhos brancos brilhando, coroa de estilhacos.
- "coroa" (Traje do Rei, so MVP e LENDA): esmalte branco, ouro, rubis e diamantes, veludo vermelho e aco preto.
Pintura procedural com relevo de verdade (altura -> normal -> luz, brilho especular, sombra nas frestas, brilho
proprio com halo). Desenho original. Uso: python3 -I trajes_hd.py <pasta-saida>
"""
import math, os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from scipy import ndimage

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'skins', 'monstros'))
from base import fbm, ridged, vnoise  # noqa: E402

OUT = sys.argv[1] if len(sys.argv) > 1 else '/tmp/claude-0/trajes'
os.makedirs(OUT, exist_ok=True)
S = 8                      # pixels HD por pixel da textura padrao (64x32 -> 512x256)
LUZ = np.array([-0.42, -0.58, 0.70])
LUZ = LUZ / np.linalg.norm(LUZ)
MEIO = (LUZ + np.array([0, 0, 1.0]))
MEIO = MEIO / np.linalg.norm(MEIO)

# material: albedo, especular, brilho (expoente), metal (o reflexo pega a cor do material)
MAT = {
    # Traje do Rei
    'branco': ((0.90, 0.89, 0.86), 0.42, 38, 0.0),
    'branco2': ((0.80, 0.80, 0.79), 0.30, 30, 0.0),
    'ouro': ((0.98, 0.72, 0.22), 1.05, 22, 1.0),
    'ouro2': ((0.70, 0.45, 0.10), 0.80, 18, 1.0),
    'rubi': ((0.78, 0.04, 0.10), 1.30, 90, 0.3),
    'veludo': ((0.50, 0.03, 0.07), 0.06, 8, 0.0),
    'preto': ((0.07, 0.07, 0.09), 0.55, 46, 0.0),
    'malha': ((0.20, 0.20, 0.23), 0.70, 30, 0.6),
    'diamante': ((0.55, 0.96, 0.96), 1.40, 110, 0.4),
    # Herobrine
    'obs': ((0.105, 0.085, 0.150), 0.55, 90, 0.0),
    'obs2': ((0.20, 0.17, 0.29), 0.85, 60, 0.0),
    'obs0': ((0.045, 0.035, 0.065), 0.50, 50, 0.0),
}
LUZ_HB = (np.array([0.80, 0.97, 1.00]), np.array([0.25, 0.75, 1.00]))   # miolo, halo
OLHO = np.array([1.0, 1.0, 1.0])


def ss(a, b, x):
    t = np.clip((x - a) / (b - a + 1e-9), 0, 1)
    return t * t * (3 - 2 * t)


class Face:
    """Uma face (em pixels padrao w x h) pintada em HD. Coordenadas x,y em pixels padrao, y para baixo."""

    def __init__(self, w, h, semente=0):
        self.w, self.h = w, h
        H, W = int(round(h * S)), int(round(w * S))
        self.H, self.W = H, W
        yy, xx = np.mgrid[0:H, 0:W]
        self.x = (xx + 0.5) / S
        self.y = (yy + 0.5) / S
        self.alb = np.zeros((H, W, 3))
        self.alt = np.zeros((H, W))
        self.esp = np.zeros((H, W))
        self.bri = np.ones((H, W)) * 20
        self.metal = np.zeros((H, W))
        self.emis = np.zeros((H, W, 3))
        self.a = np.zeros((H, W))
        self.sem = semente

    # ---- formas (distancia com sinal, em pixels padrao; negativo dentro)
    def ret(self, x0, y0, x1, y1, r=0.0):
        cx, cy, hx, hy = (x0 + x1) / 2, (y0 + y1) / 2, (x1 - x0) / 2 - r, (y1 - y0) / 2 - r
        qx, qy = np.abs(self.x - cx) - hx, np.abs(self.y - cy) - hy
        return np.hypot(np.maximum(qx, 0), np.maximum(qy, 0)) + np.minimum(np.maximum(qx, qy), 0) - r

    def circ(self, cx, cy, r):
        return np.hypot(self.x - cx, self.y - cy) - r

    def poli(self, pts):
        """Distancia com sinal ate um poligono (pontos em pixels padrao)."""
        im = Image.new('L', (self.W, self.H), 0)
        ImageDraw.Draw(im).polygon([(px * S, py * S) for px, py in pts], fill=255)
        m = np.array(im) > 127
        dentro = ndimage.distance_transform_edt(m) / S
        fora = ndimage.distance_transform_edt(~m) / S
        return np.where(m, -dentro, fora)

    def tudo(self):
        return self.x * 0 - 1

    # ---- pintura
    def pintar(self, d, mat, alt=0.0, bisel=0.35, ruido=0.06, escala_ruido=2.0, so_alt=False, borda=0.0):
        """Pinta o material onde d<0. alt: quanto sobe (com bisel nas bordas)."""
        m = ss(borda + 0.5 / S, borda - 0.5 / S, d) if borda == 0 else ss(0.5 / S, -0.5 / S, d)
        if not so_alt:
            alb, esp, bri, metal = MAT[mat]
            n = fbm(self.x * escala_ruido, self.y * escala_ruido, self.sem * 0.37, oct=3, seed=self.sem + len(mat)) - 0.5
            cor = np.clip(np.array(alb)[None, None, :] * (1 + ruido * 2 * n[..., None]), 0, 1)
            if mat in ('ouro', 'ouro2'):
                # ouro escovado: riscos finos na horizontal e um brilho quente que corre na diagonal
                risco = fbm(self.x * 0.6, self.y * 14.0, 3.1, oct=2, seed=41) - 0.5
                faixa = np.exp(-((((self.x + self.y * 0.6) % 6.0) - 3.0) ** 2) / 0.9) * 0.10
                cor = np.clip(cor * (1 + 0.16 * risco[..., None]) + faixa[..., None] * np.array([1.0, 0.85, 0.45])[None, None, :], 0, 1)
            elif mat in ('branco', 'branco2'):
                # esmalte perolado: mais claro em cima, um toque frio embaixo e reflexo leitoso
                t = np.clip(self.y / max(1.0, self.h), 0, 1)[..., None]
                perola = np.array([1.03, 1.02, 1.0])[None, None, :] * (1 - t) + np.array([0.90, 0.93, 1.0])[None, None, :] * t
                reflexo = np.exp(-((((self.x - self.y * 0.5) % 7.0) - 3.5) ** 2) / 0.6) * 0.06
                cor = np.clip(cor * perola + reflexo[..., None], 0, 1)
            self.alb = self.alb * (1 - m[..., None]) + cor * m[..., None]
            self.esp = self.esp * (1 - m) + esp * m
            self.bri = self.bri * (1 - m) + bri * m
            self.metal = self.metal * (1 - m) + metal * m
            self.a = np.maximum(self.a, m)
        if alt:
            perfil = ss(0, bisel, -d) if bisel > 0 else (d < 0) * 1.0
            self.alt = self.alt + alt * perfil * (d < bisel)
        return m

    def cavar(self, d, prof=0.25, largura=0.12):
        """Sulco (linha funda) ao longo de d=0."""
        k = np.clip(1 - np.abs(d) / largura, 0, 1)
        self.alt -= prof * k
        return k

    def friso(self, d, mat, larg=0.45, alt=0.22):
        """Faixa ao longo da borda de dentro de uma forma (moldura): entre d=-larg e d=0."""
        k = (d < 0) & (d > -larg)
        self.pintar(np.where(k, -1.0, 1.0), mat)
        self.alt += alt * np.where(k, ss(0, 0.12, np.minimum(-d, larg + d)), 0)
        if mat == 'ouro' and larg >= 0.3:
            self.cavar(d + larg * 0.5, 0.05, 0.03)        # risco no meio da moldura


    def filigrana(self, d, margem=0.35, escala=1.0, mat='ouro', fase=0.0):
        """Arabescos de ouro incrustados dentro de uma forma (linhas finas em curva)."""
        x, y = self.x / escala, self.y / escala
        onda = np.sin(x * 2.4 + np.sin(y * 1.7 + fase) * 1.6 + fase)
        onda2 = np.sin(y * 2.4 + np.sin(x * 1.9 - fase) * 1.4)
        linha = np.minimum(np.abs(onda), np.abs(onda2) + 0.35)
        k = np.clip(1 - linha / 0.16, 0, 1) * (d < -margem)
        cor = np.array(MAT[mat][0])
        self.alb = self.alb * (1 - k[..., None]) + cor[None, None, :] * k[..., None]
        self.metal = np.maximum(self.metal, k)
        self.esp = self.esp * (1 - k) + MAT[mat][1] * k
        self.bri = self.bri * (1 - k) + MAT[mat][2] * k
        self.alt -= 0.07 * k

    def perolas(self, d, larg=0.42, passo=0.55, r=0.16, mat='branco'):
        """Fileira de perolinhas dentro da faixa da borda (entre d=-larg e 0)."""
        cx = (np.floor(self.x / passo) + 0.5) * passo
        cy = (np.floor(self.y / passo) + 0.5) * passo
        dd = np.hypot(self.x - cx, self.y - cy)
        faixa = (d < -0.08) & (d > -larg + 0.08)
        k = np.clip(1 - dd / r, 0, 1) * faixa
        cor = np.array(MAT[mat][0])
        m = k > 0
        self.alb[m] = self.alb[m] * 0.2 + cor * 0.8
        self.esp[m] = 1.0
        self.bri[m] = 70
        self.alt += 0.18 * np.sqrt(k)

    def bordado(self, d, passo=1.1, mat='ouro'):
        """Bordado de ouro em losangos sobre o veludo."""
        u = (self.x + self.y) / passo
        v = (self.x - self.y) / passo
        lu = np.abs(u - np.round(u))
        lv = np.abs(v - np.round(v))
        k = np.clip(1 - np.minimum(lu, lv) / 0.05, 0, 1) * (d < -0.15)
        cruz = (np.abs(u - np.floor(u) - 0.5) < 0.09) & (np.abs(v - np.floor(v) - 0.5) < 0.09) & (d < -0.15)
        cor = np.array(MAT[mat][0])
        kk = np.maximum(k * 0.85, cruz * 1.0)
        self.alb = self.alb * (1 - kk[..., None]) + cor[None, None, :] * kk[..., None]
        self.metal = np.maximum(self.metal, kk)
        self.esp = np.maximum(self.esp, kk * 0.8)
        self.alt += 0.05 * kk
        # textura do tecido
        tec = (np.sin(self.x * S * 1.7) * np.sin(self.y * S * 1.7)) * 0.03
        self.alt += tec * (d < 0)

    def joia(self, cx, cy, r, mat='rubi', brilho=0.0, cor_brilho=None):
        d = self.circ(cx, cy, r)
        self.pintar(self.circ(cx, cy, r + 0.18), 'ouro', alt=0.25, bisel=0.12)
        m = self.pintar(d, mat)
        dome = np.clip(1 - ((self.x - cx) ** 2 + (self.y - cy) ** 2) / (r * r), 0, 1)
        self.alt = self.alt * (1 - m) + (0.25 + 0.55 * np.sqrt(dome)) * m
        # facetas: setores com pequenas diferencas de altura
        ang = np.arctan2(self.y - cy, self.x - cx)
        fac = (np.floor((ang + math.pi) / (math.pi / 4)) % 2) * 0.06
        self.alt += fac * m
        if brilho:
            c = np.array(cor_brilho if cor_brilho is not None else MAT[mat][0])
            self.emis += brilho * c[None, None, :] * (m * (0.4 + 0.6 * dome))[..., None]
            # estrelinha de brilho (cruz fina) no canto de cima da joia
            sx, sy = cx - r * 0.35, cy - r * 0.35
            cruz = np.maximum(np.clip(1 - np.abs(self.y - sy) / 0.035, 0, 1) * np.clip(1 - np.abs(self.x - sx) / (r * 0.9), 0, 1),
                              np.clip(1 - np.abs(self.x - sx) / 0.035, 0, 1) * np.clip(1 - np.abs(self.y - sy) / (r * 0.9), 0, 1))
            self.emis += (cruz * 0.9)[..., None] * np.array([1.0, 1.0, 0.95])[None, None, :]

    def rebite(self, cx, cy, r=0.28, mat='ouro'):
        d = self.circ(cx, cy, r)
        m = self.pintar(d, mat)
        dome = np.clip(1 - ((self.x - cx) ** 2 + (self.y - cy) ** 2) / (r * r), 0, 1)
        self.alt = self.alt * (1 - m) + (self.alt + 0.35 * np.sqrt(dome)) * m

    def luz(self, d, cor_miolo, cor_halo, forca=1.0, largura=0.10, halo=0.7):
        """Linha/forma brilhante: miolo onde d<largura, halo que espalha."""
        miolo = ss(largura + 0.06, largura - 0.06, d)
        hal = np.exp(-np.maximum(d, 0) / max(0.05, halo * 0.35)) * 0.55
        self.emis += forca * (miolo[..., None] * cor_miolo[None, None, :] + (hal * (1 - miolo))[..., None] * cor_halo[None, None, :])
        self.a = np.maximum(self.a, miolo)
        self.alt -= 0.12 * miolo

    def render(self):
        """Luz: normal pela altura, difusa + especular + sombra nas frestas + brilho proprio. Devolve RGBA uint8."""
        a = self.alt
        gy, gx = np.gradient(a * S * 1.5)
        n = np.dstack([-gx, -gy, np.ones_like(a)])
        n /= np.linalg.norm(n, axis=2, keepdims=True)
        dif = np.clip(n @ LUZ, 0, 1)
        esp = np.clip(n @ MEIO, 0, 1) ** self.bri * self.esp
        cav = ndimage.gaussian_filter(a, 1.2 * S / 8) - a
        ao = np.clip(1 - np.maximum(cav, 0) * 2.8, 0.3, 1)
        borda = ndimage.gaussian_filter(a, 0.6 * S / 8)
        aresta = np.clip((a - borda) * 6, -0.25, 0.35)       # quina de cima clareia, de baixo escurece
        amb = 0.38
        cor = self.alb * (amb + 0.78 * dif)[..., None] * ao[..., None]
        cor += aresta[..., None] * 0.35 * (0.4 + self.alb)
        tint = self.metal[..., None] * self.alb + (1 - self.metal[..., None]) * np.ones(3)[None, None, :]
        cor += esp[..., None] * tint
        em = self.emis
        bloom = np.dstack([ndimage.gaussian_filter(em[..., k], 1.4 * S / 8) for k in range(3)])
        cor = cor + em + bloom * 0.6
        cor = 1 - np.exp(-cor * 1.15)                         # tonemap suave
        cor = np.clip(cor ** (1 / 1.05), 0, 1)
        alfa = np.clip(np.maximum(self.a, np.max(bloom, axis=2) * 0.0), 0, 1)
        out = np.nan_to_num(np.dstack([cor, alfa]), nan=0.0)
        return (np.clip(out, 0, 1) * 255 + 0.5).astype(np.uint8)


class Camada:
    """Textura de armadura 64x32 (padrao) em HD."""

    def __init__(self):
        self.im = np.zeros((32 * S, 64 * S, 4), np.uint8)

    def colar(self, f, u, v):
        img = f.render()
        y0, x0 = int(v * S), int(u * S)
        self.im[y0:y0 + img.shape[0], x0:x0 + img.shape[1]] = img

    def salvar(self, caminho):
        Image.fromarray(self.im, 'RGBA').save(caminho, optimize=True)


# caixas do modelo de armadura: (u, v, w, h, d) -> faces (nome, u, v, largura, altura)
def faces_caixa(u, v, w, h, d):
    return {'cima': (u + d, v, w, d), 'baixo': (u + d + w, v, w, d), 'dir': (u, v + d, d, h), 'frente': (u + d, v + d, w, h),
            'esq': (u + d + w, v + d, d, h), 'tras': (u + 2 * d + w, v + d, w, h)}


CABECA = faces_caixa(0, 0, 8, 8, 8)
CHAPEU = faces_caixa(32, 0, 8, 8, 8)
TRONCO = faces_caixa(16, 16, 8, 12, 4)
BRACO = faces_caixa(40, 16, 4, 12, 4)
PERNA = faces_caixa(0, 16, 4, 12, 4)


# ============================================================== TRAJE DO REI (coroa)

def asa_ouro(f, k, ox, oy, frente_direita, w):
    """Asa de ouro estilo valquiria (desenhada num quadro 8x8 com a frente a esquerda), na escala k e deslocada
    (ox, oy). frente_direita: espelha (a frente do corpo fica a direita da face)."""
    def T(x, y):
        xx, yy = ox + x * k, oy + y * k
        return (w - xx if frente_direita else xx), yy
    borda = []
    for t in np.linspace(0, 1, 14):
        x = (1 - t) ** 2 * 2.0 + 2 * (1 - t) * t * 2.6 + t * t * 7.6
        y = (1 - t) ** 2 * 6.0 + 2 * (1 - t) * t * 1.6 + t * t * 0.55
        borda.append((x, y))
    pontas = [(6.55, 1.95), (7.45, 2.35), (6.25, 3.15), (6.95, 3.85), (5.55, 4.25), (5.95, 5.05), (4.55, 5.25), (4.75, 6.1), (2.6, 6.6)]
    asa = f.poli([T(x, y) for x, y in borda + pontas])
    f.pintar(asa + 0.12 * k, 'ouro2', alt=0.55, bisel=0.1 * k)
    f.pintar(asa, 'ouro', alt=0.7, bisel=0.22 * k)
    ax, ay = T(2.6, 5.9)
    for tx, ty in ((7.6, 0.55), (7.45, 2.35), (6.95, 3.85), (5.95, 5.05), (4.75, 6.1)):
        bx, by = T(tx, ty)
        vx, vy = bx - ax, by - ay
        tt = np.clip(((f.x - ax) * vx + (f.y - ay) * vy) / (vx * vx + vy * vy), 0.15, 0.95)
        f.cavar(np.hypot(f.x - (ax + tt * vx), f.y - (ay + tt * vy)) - 0.015, 0.12, 0.05 * max(k, 0.6))
    jx, jy = T(2.4, 5.9)
    f.joia(jx, jy, 0.55 * max(k, 0.5), 'rubi', brilho=0.45)
    return asa


def rei_cabeca(nome, w, h, sem):
    f = Face(w, h, sem)
    tudo = f.tudo()
    if nome == 'baixo':
        f.pintar(tudo, 'preto')
        return f
    f.pintar(tudo, 'branco', alt=0.0)
    f.pintar(f.ret(0.05, 0.05, w - 0.05, h - 0.05, 0.4), 'branco', alt=0.45, bisel=0.5)
    f.filigrana(f.ret(0.05, 0.05, w - 0.05, h - 0.05, 0.4), margem=0.6, escala=0.9, fase=sem * 0.7)
    if nome == 'cima':
        f.friso(f.ret(0, 0, w, h, 0.2), 'ouro', 0.5, 0.25)
        for d in (f.ret(3.55, 0.6, 4.45, 7.4, 0.2), f.ret(0.6, 3.55, 7.4, 4.45, 0.2)):
            f.pintar(d, 'ouro', alt=0.65, bisel=0.2)
        f.joia(4, 4, 0.75, 'rubi', brilho=0.25)
        for (cx, cy) in ((2, 2), (6, 2), (2, 6), (6, 6)):
            f.rebite(cx, cy, 0.3)
        return f
    f.friso(f.ret(0, 0, w, h, 0.15), 'ouro', 0.42, 0.25)
    f.perolas(f.ret(0, 0, w, h, 0.15), 0.42)
    f.pintar(f.ret(0.0, 6.9, w, 8.0), 'ouro', alt=0.55, bisel=0.15)            # faixa do queixo
    if nome == 'frente':
        # viseira em T, funda e preta, com brasa vermelha la dentro
        visor = np.minimum(f.ret(0.9, 3.25, 7.1, 4.35, 0.35), f.ret(3.5, 3.6, 4.5, 6.6, 0.3))
        f.pintar(visor + 0.12, 'ouro', alt=0.15, bisel=0.1)
        f.pintar(visor, 'preto')
        f.alt[visor < 0] = -0.35
        brasa = f.ret(1.4, 3.6, 6.6, 4.0, 0.2)
        f.luz(brasa, np.array([1.0, 0.25, 0.15]), np.array([0.6, 0.02, 0.02]), forca=0.55, largura=0.0, halo=0.5)
        # placas das bochechas com arabesco gravado
        for lado in (0, 1):
            x0 = 0.55 if lado == 0 else 4.85
            pl = f.ret(x0, 4.7, x0 + 2.6, 6.75, 0.5)
            f.pintar(pl, 'branco', alt=0.25, bisel=0.25)
            t = np.linspace(0, 1, 60)
            cx = x0 + 1.3
            curva = np.full(f.x.shape, 9.0)
            for k in range(60):
                px = cx + (0.9 if lado else -0.9) * math.sin(t[k] * math.pi * 1.6) * (1 - t[k] * 0.5)
                py = 4.95 + t[k] * 1.6
                curva = np.minimum(curva, np.hypot(f.x - px, f.y - py))
            k = np.clip(1 - curva / 0.1, 0, 1) * (pl < 0)
            f.alb = f.alb * (1 - k[..., None]) + np.array(MAT['ouro'][0])[None, None, :] * k[..., None]
            f.metal = np.maximum(f.metal, k)
            f.esp = np.maximum(f.esp, k * 0.9)
            f.alt -= 0.08 * k
        # testa: placa com tres diamantes (a coroa de verdade fica na camada de fora)
        f.pintar(f.ret(1.0, 0.75, 7.0, 2.6, 0.4), 'branco2', alt=0.2, bisel=0.2)
        for cx in (2.3, 4.0, 5.7):
            f.joia(cx, 1.7, 0.42 if cx != 4.0 else 0.55, 'diamante', brilho=0.25)
        for cx in (1.6, 6.4):
            f.rebite(cx, 6.0, 0.25)
    elif nome in ('dir', 'esq'):
        # protetor da orelha: medalhao de ouro com rubi e furinhos de respiro (a asa do elmo saiu: as asas agora
        # sao as de hussardo 3D nas costas)
        cx, cy = 4.0, 4.1
        f.pintar(f.circ(cx, cy, 2.05), 'ouro2', alt=0.45, bisel=0.2)
        f.pintar(f.circ(cx, cy, 1.8), 'ouro', alt=0.6, bisel=0.3)
        f.cavar(f.circ(cx, cy, 1.25), 0.10, 0.05)
        for k in range(8):
            a = k * math.pi / 4
            f.rebite(cx + math.cos(a) * 1.52, cy + math.sin(a) * 1.52, 0.13)
        f.joia(cx, cy, 0.62, 'rubi', brilho=0.35)
        fx = 1.15 if nome == 'esq' else w - 1.15        # respiros do lado da frente do rosto
        for y in (2.9, 3.7, 4.5, 5.3):
            f.pintar(f.ret(fx - 0.38, y, fx + 0.38, y + 0.22, 0.1), 'preto')
    elif nome == 'tras':
        f.pintar(f.ret(3.35, 0.3, 4.65, 6.9, 0.3), 'ouro', alt=0.5, bisel=0.2)     # crista
        f.pintar(f.ret(1.0, 2.0, 2.9, 6.2, 0.5), 'veludo', alt=0.15, bisel=0.2)
        f.pintar(f.ret(5.1, 2.0, 7.0, 6.2, 0.5), 'veludo', alt=0.15, bisel=0.2)
        for y in (1.2, 3.0, 4.8):
            f.rebite(4.0, y, 0.28, 'rubi')
    return f


def rei_coroa(nome, w, h, sem):
    """Camada de fora do elmo: a coroa (aro duplo com joias, cinco pontas com trevo e perola). Resto transparente."""
    f = Face(w, h, sem)
    if nome in ('cima', 'baixo'):
        return f
    aro = f.ret(0.0, 1.95, w, 3.25)
    pontas = [(0.8, 1.05), (2.4, 0.55), (4.0, 0.0), (5.6, 0.55), (7.2, 1.05)]
    forma = aro
    for cx, topo in pontas:
        forma = np.minimum(forma, f.poli([(cx - 0.72, 2.05), (cx - 0.12, topo + 0.45), (cx, topo + 0.25), (cx + 0.12, topo + 0.45), (cx + 0.72, 2.05)]))
    f.pintar(forma + 0.1, 'ouro2', alt=0.4, bisel=0.1)
    f.pintar(forma, 'ouro', alt=0.55, bisel=0.18)
    f.cavar(f.ret(0.0, 2.2, w, 2.25), 0.1, 0.05)
    f.cavar(f.ret(0.0, 3.0, w, 3.05), 0.1, 0.05)
    # trevo na ponta de cada uma
    for cx, topo in pontas:
        for ox, oy in ((-0.2, 0.1), (0.2, 0.1), (0.0, -0.12)):
            f.pintar(f.circ(cx + ox, topo + 0.35 + oy, 0.17), 'ouro', alt=0.7, bisel=0.12)
        f.joia(cx, topo + 0.38, 0.13 if cx != 4.0 else 0.16, 'diamante' if cx == 4.0 else 'rubi', brilho=0.5)
    # duas fileiras de joias no aro
    for k, cx in enumerate(np.linspace(0.45, w - 0.45, 9)):
        f.joia(cx, 2.62, 0.24 if k % 2 == 0 else 0.18, 'rubi' if k % 2 == 0 else 'diamante', brilho=0.45)
    f.perolas(f.ret(0.0, 1.95, w, 2.25), 0.3, passo=0.42, r=0.11)
    f.perolas(f.ret(0.0, 2.98, w, 3.28), 0.3, passo=0.42, r=0.11)
    return f


def rei_tronco(nome, w, h, sem, calca=False):
    f = Face(w, h, sem)
    if calca:
        if nome in ('cima', 'baixo'):
            return f
        # so a cintura: escarcelas brancas com friso de ouro
        y0 = 8.4
        f.pintar(f.ret(0, y0, w, h), 'preto', alt=0.1)
        n = 2 if w <= 4 else 3
        for k in range(n):
            x0 = k * w / n + 0.12
            pl = f.ret(x0, y0 + 0.3, x0 + w / n - 0.24, h - 0.1, 0.35)
            f.pintar(pl, 'branco', alt=0.45, bisel=0.3)
            f.friso(pl, 'ouro', 0.32, 0.2)
        return f
    tudo = f.tudo()
    if nome == 'baixo':
        f.pintar(tudo, 'preto')
        return f
    if nome == 'cima':
        f.pintar(tudo, 'branco', alt=0.2)
        f.friso(f.ret(0, 0, w, h, 0.2), 'ouro', 0.45, 0.25)
        f.pintar(f.ret(2.5, 0.0, 5.5, h), 'veludo', alt=0.1)
        return f
    f.pintar(tudo, 'malha')
    if nome == 'frente':
        peito = f.ret(0.2, 0.2, w - 0.2, 10.6, 0.9)
        f.pintar(peito, 'branco', alt=0.55, bisel=0.6)
        f.filigrana(peito, margem=0.7, escala=1.0, fase=1.3)
        f.friso(peito, 'ouro', 0.45, 0.25)
        f.perolas(peito, 0.45)
        f.pintar(f.ret(0.0, 0.0, w, 1.0, 0.3), 'ouro', alt=0.6, bisel=0.2)          # gola
        # tabardo de veludo no meio
        f.pintar(f.ret(3.0, 1.0, 5.0, 10.6), 'veludo', alt=0.6, bisel=0.05)
        f.bordado(f.ret(3.0, 1.0, 5.0, 10.6), passo=0.9)
        f.friso(f.ret(3.0, 1.0, 5.0, 10.6), 'ouro', 0.22, 0.18)
        # divisao dos peitorais e placas do abdomen
        f.cavar(f.poli([(0.4, 5.6), (3.0, 6.3), (3.0, 6.45), (0.4, 5.75)]), 0.18, 0.06)
        f.cavar(f.poli([(5.0, 6.3), (7.6, 5.6), (7.6, 5.75), (5.0, 6.45)]), 0.18, 0.06)
        for y in (7.4, 8.5, 9.6):
            f.cavar(f.ret(0.5, y, 2.9, y + 0.08), 0.18, 0.07)
            f.cavar(f.ret(5.1, y, 7.5, y + 0.08), 0.18, 0.07)
        # raios de ouro saindo do brasao (gravados no esmalte)
        ang = np.arctan2(f.y - 3.6, f.x - 4.0)
        r = np.hypot(f.x - 4.0, f.y - 3.6)
        raio = np.abs(((ang / (math.pi / 8)) % 1.0) - 0.5) < 0.07
        kk = raio & (r > 2.2) & (r < 4.6) & (peito < -0.6) & ((f.x < 2.85) | (f.x > 5.15))
        f.alb[kk] = np.array(MAT['ouro'][0])
        f.metal[kk] = 1.0
        f.esp[kk] = 0.9
        f.alt[kk] -= 0.05
        # corrente de joias dos ombros ate o brasao
        for lado in (-1, 1):
            for t in np.linspace(0.05, 0.95, 11):
                cx = 4.0 + lado * (3.4 * (1 - t))
                cy = 1.25 + 4.3 * t - 3.2 * t * t
                f.pintar(f.circ(cx, cy, 0.16), 'ouro', alt=0.55, bisel=0.1)
            for t in (0.3, 0.6):
                f.joia(4.0 + lado * (3.4 * (1 - t)), 1.25 + 4.3 * t - 3.2 * t * t, 0.2, 'rubi', brilho=0.4)
        # brasao: coroa grande de ouro com rubi e diamantes
        coroa = f.poli([(2.0, 5.2), (6.0, 5.2), (6.4, 2.4), (5.2, 3.7), (4.0, 1.7), (2.8, 3.7), (1.6, 2.4)])
        f.pintar(coroa + 0.12, 'ouro2', alt=0.8, bisel=0.1)
        f.pintar(coroa, 'ouro', alt=0.9, bisel=0.22)
        f.pintar(f.ret(1.9, 4.6, 6.1, 5.4, 0.15), 'ouro', alt=1.0, bisel=0.15)
        f.joia(4.0, 4.2, 0.62, 'rubi', brilho=0.6)
        for cx in (2.65, 5.35):
            f.joia(cx, 4.4, 0.3, 'diamante', brilho=0.3)
        for cx, cy in ((1.6, 2.4), (4.0, 1.7), (6.4, 2.4)):
            f.joia(cx, cy, 0.24, 'diamante', brilho=0.3)
        # cinto
        f.pintar(f.ret(0, 10.7, w, h), 'preto', alt=0.35, bisel=0.1)
        f.pintar(f.ret(3.0, 10.65, 5.0, h - 0.05, 0.25), 'ouro', alt=0.6, bisel=0.15)
        f.joia(4.0, 11.35, 0.3, 'rubi', brilho=0.2)
        for x in (1.0, 2.2, 5.8, 7.0):
            f.rebite(x, 11.35, 0.22)
    elif nome == 'tras':
        costas = f.ret(0.2, 0.2, w - 0.2, 10.6, 0.9)
        f.pintar(costas, 'branco', alt=0.5, bisel=0.6)
        f.filigrana(costas, margem=0.7, escala=1.0, fase=2.1)
        f.friso(costas, 'ouro', 0.45, 0.25)
        f.perolas(costas, 0.45)
        manto = f.poli([(1.4, 1.2), (6.6, 1.2), (6.9, 10.6), (4.0, 10.0), (1.1, 10.6)])
        f.pintar(manto, 'veludo', alt=0.6, bisel=0.2)
        f.bordado(manto, passo=1.0)
        f.friso(manto, 'ouro', 0.25, 0.2)
        coroa = f.poli([(2.7, 4.6), (5.3, 4.6), (5.6, 2.6), (4.8, 3.5), (4.0, 2.1), (3.2, 3.5), (2.4, 2.6)])
        f.pintar(coroa, 'ouro', alt=0.85, bisel=0.18)
        f.joia(4.0, 4.0, 0.36, 'rubi', brilho=0.3)
        for y in (6.0, 7.2, 8.4):
            f.cavar(f.ret(1.9, y, 6.1, y + 0.07), 0.12, 0.06)
        f.pintar(f.ret(0, 10.7, w, h), 'preto', alt=0.35, bisel=0.1)
        for x in (1.0, 2.6, 4.0, 5.4, 7.0):
            f.rebite(x, 11.35, 0.22)
    else:  # laterais 4x12
        lado = f.ret(0.2, 0.2, w - 0.2, 10.6, 0.5)
        f.pintar(lado, 'branco', alt=0.45, bisel=0.4)
        f.filigrana(lado, margem=0.5, escala=0.8, fase=0.4)
        f.friso(lado, 'ouro', 0.38, 0.22)
        f.pintar(f.ret(1.55, 0.9, 2.45, 10.0), 'veludo', alt=0.5, bisel=0.05)
        for y in (2.0, 4.5, 7.0):
            f.rebite(2.0, y, 0.26)
        f.pintar(f.ret(0, 10.7, w, h), 'preto', alt=0.35, bisel=0.1)
    return f


def rei_braco(nome, w, h, sem):
    f = Face(w, h, sem)
    tudo = f.tudo()
    if nome == 'baixo':
        f.pintar(tudo, 'ouro2')
        return f
    if nome == 'cima':
        f.pintar(tudo, 'branco', alt=0.3)
        f.friso(f.ret(0, 0, w, h, 0.4), 'ouro', 0.45, 0.25)
        f.joia(2.0, 2.0, 0.5, 'rubi', brilho=0.25)
        return f
    f.pintar(tudo, 'malha')
    # malha de aco no meio do braco (aneis)
    aneis = (np.sin(f.x * math.pi * 2.2) * np.sin(f.y * math.pi * 2.2))
    meio = (f.y > 5.2) & (f.y < 8.2)
    f.alt += np.where(meio, 0.06 * aneis, 0)
    # ombreira
    omb = f.ret(-0.2, 0.0, w + 0.2, 4.6, 0.6)
    f.pintar(omb, 'branco', alt=0.75, bisel=0.5)
    f.filigrana(omb, margem=0.45, escala=0.7, fase=0.9)
    f.pintar(f.ret(-1, 3.6, w + 1, 4.6), 'ouro', alt=0.85, bisel=0.15)
    for k in range(2):
        f.cavar(f.ret(0.3, 1.4 + k * 1.1, w - 0.3, 1.47 + k * 1.1), 0.12, 0.06)
    for cx in (0.7, 2.0, 3.3):
        f.joia(cx, 4.1, 0.2, 'rubi' if cx == 2.0 else 'diamante', brilho=0.4)
    for cx in np.arange(0.25, w, 0.5):
        f.pintar(f.circ(cx, 0.15, 0.22), 'ouro', alt=0.85, bisel=0.12)
    # (as asinhas pintadas na ombreira viraram as asas de hussardo 3D: asas_hussardo.py)
    f.pintar(f.ret(-1, 4.6, w + 1, 5.25), 'veludo', alt=0.3, bisel=0.05)
    # manopla
    man = f.ret(-0.2, 8.1, w + 0.2, 12.0, 0.4)
    f.pintar(man, 'ouro', alt=0.6, bisel=0.35)
    f.pintar(f.ret(0.35, 8.6, w - 0.35, 10.9, 0.3), 'branco', alt=0.7, bisel=0.25)
    f.pintar(f.ret(-1, 11.3, w + 1, 12), 'ouro2', alt=0.55, bisel=0.1)
    if nome == 'frente':
        f.joia(2.0, 9.7, 0.38, 'diamante', brilho=0.3)
    else:
        f.rebite(2.0, 9.7, 0.25)
    return f


def rei_perna(nome, w, h, sem, parte):
    """parte='calca' (camada 2) ou 'bota' (camada 1, so a parte de baixo)."""
    f = Face(w, h, sem)
    tudo = f.tudo()
    if parte == 'calca':
        if nome in ('cima', 'baixo'):
            f.pintar(tudo, 'preto')
            return f
        f.pintar(tudo, 'preto', alt=0.0)
        cox = f.ret(0.15, 0.2, w - 0.15, 5.1, 0.45)
        f.pintar(cox, 'branco', alt=0.5, bisel=0.4)
        f.filigrana(cox, margem=0.45, escala=0.7, fase=1.7)
        f.friso(cox, 'ouro', 0.32, 0.2)
        if nome in ('dir', 'esq'):
            f.pintar(f.ret(1.6, 0.6, 2.4, 4.7), 'veludo', alt=0.55, bisel=0.05)
        joe = f.ret(0.3, 5.3, w - 0.3, 7.5, 0.6)
        f.pintar(joe, 'ouro', alt=0.65, bisel=0.4)
        if nome == 'frente':
            f.joia(2.0, 6.4, 0.42, 'rubi', brilho=0.25)
        f.pintar(f.ret(0.2, 7.7, w - 0.2, 12, 0.3), 'branco2', alt=0.3, bisel=0.2)
        return f
    # bota: so de y=7 para baixo
    if nome == 'cima':
        return f
    if nome == 'baixo':
        f.pintar(tudo, 'preto')
        return f
    f.pintar(f.ret(-1, 7.0, w + 1, 12.5), 'branco', alt=0.45, bisel=0.3)
    f.filigrana(f.ret(-1, 7.9, w + 1, 9.9), margem=0.15, escala=0.6, fase=2.5)
    f.pintar(f.ret(-1, 7.0, w + 1, 7.85), 'ouro', alt=0.65, bisel=0.15)
    f.joia(2.0, 7.43, 0.28, 'rubi', brilho=0.2)
    f.cavar(f.ret(0.3, 9.0, w - 0.3, 9.06), 0.12, 0.06)
    if nome == 'frente':
        f.pintar(f.ret(0.2, 9.9, w - 0.2, 11.6, 0.5), 'ouro', alt=0.7, bisel=0.3)     # biqueira
        f.joia(2.0, 10.6, 0.3, 'diamante', brilho=0.25)
    else:
        f.pintar(f.ret(-1, 10.2, w + 1, 11.6), 'ouro2', alt=0.5, bisel=0.15)
    f.pintar(f.ret(-1, 11.55, w + 1, 12.1), 'preto', alt=0.3, bisel=0.05)
    return f


# ============================================================== HEROBRINE

def obsidiana(f, d, sem, base='obs', alt=0.3, bisel=0.3, celula=1.15):
    """Obsidiana cristalina: celulas (Voronoi) com tom e inclinacao proprios."""
    m = f.pintar(d, base, alt=alt, bisel=bisel, ruido=0.04)
    rng = np.random.default_rng(sem)
    nx, ny = int(f.w / celula) + 3, int(f.h / celula) + 3
    px = (np.arange(nx)[None, :] + rng.random((ny, nx))) * celula - celula
    py = (np.arange(ny)[:, None] + rng.random((ny, nx))) * celula - celula
    pts = np.stack([px.ravel(), py.ravel()], 1)
    tom = rng.random(len(pts))
    ix = rng.normal(0, 0.06, len(pts))
    iy = rng.normal(0, 0.06, len(pts))
    from scipy.spatial import cKDTree
    arv = cKDTree(pts)
    q = np.stack([f.x.ravel(), f.y.ravel()], 1)
    dist, idx = arv.query(q, k=2)
    idx0 = idx[:, 0].reshape(f.x.shape)
    borda = (dist[:, 1] - dist[:, 0]).reshape(f.x.shape)
    c = tom[idx0]
    claro = np.array(MAT['obs2'][0])
    escuro = np.array(MAT['obs0'][0])
    cor = escuro[None, None, :] * (1 - c[..., None]) + claro[None, None, :] * c[..., None]
    cor = cor * 0.55 + np.array(MAT[base][0])[None, None, :] * 0.45
    f.alb = f.alb * (1 - m[..., None]) + cor * m[..., None]
    plano = (f.x - pts[idx0, 0]) * ix[idx0] + (f.y - pts[idx0, 1]) * iy[idx0]
    f.alt += plano * m
    f.alt -= np.clip(1 - borda / 0.07, 0, 1) * 0.06 * m       # junta entre os cristais
    return m


def rachadura(f, x0, y0, x1, y1, rng, caixa=None, galhos=2, forca=1.0, larg=0.07):
    """Rachadura de luz: caminho tremido de (x0,y0) ate (x1,y1)."""
    n = 40
    t = np.linspace(0, 1, n)
    xs = x0 + (x1 - x0) * t
    ys = y0 + (y1 - y0) * t
    comp = math.hypot(x1 - x0, y1 - y0)
    px, py = -(y1 - y0) / (comp + 1e-9), (x1 - x0) / (comp + 1e-9)
    desv = np.cumsum(rng.normal(0, 0.16, n))
    desv -= np.linspace(desv[0], desv[-1], n)
    xs, ys = xs + px * desv, ys + py * desv
    if caixa:
        xs = np.clip(xs, caixa[0], caixa[2])
        ys = np.clip(ys, caixa[1], caixa[3])
    d = np.full(f.x.shape, 99.0)
    for k in range(n - 1):
        ax, ay, bx, by = xs[k], ys[k], xs[k + 1], ys[k + 1]
        vx, vy = bx - ax, by - ay
        L2 = vx * vx + vy * vy + 1e-9
        tt = np.clip(((f.x - ax) * vx + (f.y - ay) * vy) / L2, 0, 1)
        dk = np.hypot(f.x - (ax + tt * vx), f.y - (ay + tt * vy))
        afina = larg * (1.0 - 0.55 * abs(k / n - 0.5) * 2)
        d = np.minimum(d, dk - afina)
    f.luz(d, LUZ_HB[0], LUZ_HB[1], forca=forca, largura=0.0, halo=0.55)
    for _ in range(galhos):
        k = int(rng.integers(8, n - 8))
        ang = math.atan2(ys[-1] - ys[0], xs[-1] - xs[0]) + rng.choice([-1, 1]) * rng.uniform(0.6, 1.2)
        L = rng.uniform(0.8, 1.6)
        rachadura(f, xs[k], ys[k], xs[k] + math.cos(ang) * L, ys[k] + math.sin(ang) * L, rng, caixa, galhos=0, forca=forca * 0.8, larg=larg * 0.7)


def hb_cabeca(nome, w, h, sem):
    f = Face(w, h, sem)
    rng = np.random.default_rng(sem)
    obsidiana(f, f.tudo(), sem, alt=0.0)
    if nome == 'baixo':
        return f
    pl = f.ret(0.1, 0.1, w - 0.1, h - 0.1, 0.5)
    obsidiana(f, pl, sem + 1, base='obs', alt=0.4, bisel=0.45)
    if nome == 'frente':
        # pontas da coroa gravadas na testa
        for cx in (1.0, 2.5, 4.0, 5.5, 7.0):
            alto = 0.35 if cx == 4.0 else 0.75
            f.pintar(f.poli([(cx - 0.55, 2.1), (cx, alto), (cx + 0.55, 2.1)]), 'obs0', alt=0.0)
            f.alt -= 0.25 * (f.poli([(cx - 0.55, 2.1), (cx, alto), (cx + 0.55, 2.1)]) < 0)
        f.pintar(f.ret(0.5, 2.1, 7.5, 2.45), 'obs0')
        # viseira funda + OLHOS brancos
        vis = f.ret(0.6, 3.15, 7.4, 4.75, 0.35)
        f.pintar(vis, 'obs0')
        f.alt[vis < 0] -= 0.45
        for x0 in (1.25, 4.75):
            olho = f.ret(x0, 3.55, x0 + 2.0, 4.35, 0.32)
            f.luz(olho, OLHO, LUZ_HB[1], forca=1.6, largura=0.0, halo=0.9)
        # lagrimas de luz escorrendo
        rachadura(f, 1.9, 4.6, 1.6, 7.6, rng, (0.3, 4.5, 7.7, 7.9), galhos=1, forca=0.9)
        rachadura(f, 6.1, 4.6, 6.5, 7.5, rng, (0.3, 4.5, 7.7, 7.9), galhos=1, forca=0.9)
        # crista do nariz e grade da boca
        f.pintar(f.ret(3.7, 4.75, 4.3, 6.9, 0.2), 'obs2', alt=0.55, bisel=0.15)
        for x in (2.5, 3.1, 4.9, 5.5):
            f.cavar(f.ret(x, 6.0, x + 0.12, 7.2), 0.25, 0.08)
    elif nome in ('dir', 'esq'):
        f.pintar(f.circ(4.0, 4.6, 1.8), 'obs2', alt=0.55, bisel=0.3)
        # runa brilhando
        for (a, b, c, d2) in ((4.0, 3.5, 4.0, 5.7), (3.2, 4.2, 4.8, 5.0), (4.8, 4.2, 3.2, 5.0)):
            seg = np.hypot(f.x - np.clip(f.x, min(a, c), max(a, c)), 0)
            vx, vy = c - a, d2 - b
            tt = np.clip(((f.x - a) * vx + (f.y - b) * vy) / (vx * vx + vy * vy), 0, 1)
            seg = np.hypot(f.x - (a + tt * vx), f.y - (b + tt * vy)) - 0.08
            f.luz(seg, LUZ_HB[0], LUZ_HB[1], forca=1.0, largura=0.0, halo=0.6)
        rachadura(f, 4.0, 6.4, 1.0, 7.8, rng, (0.2, 0.2, 7.8, 7.8), galhos=1)
    elif nome == 'tras':
        rachadura(f, 4.0, 0.6, 3.6, 7.4, rng, (0.3, 0.3, 7.7, 7.7), galhos=3, forca=1.0)
    elif nome == 'cima':
        rachadura(f, 1.2, 1.5, 6.8, 6.5, rng, (0.3, 0.3, 7.7, 7.7), galhos=2)
        rachadura(f, 6.5, 1.2, 2.0, 6.8, rng, (0.3, 0.3, 7.7, 7.7), galhos=1, forca=0.8)
    return f


def hb_coroa(nome, w, h, sem):
    """Coroa de estilhacos de obsidiana flutuando em volta do elmo, com pontas de luz."""
    f = Face(w, h, sem)
    if nome in ('cima', 'baixo'):
        return f
    rng = np.random.default_rng(sem)
    pts = [(0.0, 2.6)]
    xs = np.linspace(0.0, w, 8)
    for i in range(len(xs) - 1):
        a, b = xs[i], xs[i + 1]
        meio = (a + b) / 2
        pts += [(a + 0.15, 2.0), (meio, 0.25 + rng.uniform(0, 0.9) if i not in (3,) else 0.05), (b - 0.15, 2.0)]
    pts += [(w, 2.6), (w, 3.0), (0.0, 3.0)]
    forma = f.poli(pts)
    obsidiana(f, forma, sem, base='obs', alt=0.5, bisel=0.15, celula=0.7)
    for i in range(len(xs) - 1):
        meio = (xs[i] + xs[i + 1]) / 2
        f.luz(f.circ(meio, 1.25, 0.08), LUZ_HB[0], LUZ_HB[1], forca=0.9, largura=0.0, halo=0.5)
    rachadura(f, 0.2, 2.65, w - 0.2, 2.55, rng, (0, 2.3, w, 2.95), galhos=0, forca=0.9, larg=0.05)
    return f


def hb_tronco(nome, w, h, sem, calca=False):
    f = Face(w, h, sem)
    rng = np.random.default_rng(sem)
    if calca:
        if nome in ('cima', 'baixo'):
            return f
        obsidiana(f, f.ret(0, 8.4, w, h), sem, alt=0.25)
        for x in np.arange(0.6, w, 1.2):
            f.luz(f.circ(x, 10.2, 0.1), LUZ_HB[0], LUZ_HB[1], forca=0.7, largura=0.0, halo=0.4)
        return f
    obsidiana(f, f.tudo(), sem, alt=0.0)
    if nome == 'baixo':
        return f
    if nome == 'cima':
        obsidiana(f, f.ret(0.2, 0.2, w - 0.2, h - 0.2, 0.4), sem + 3, alt=0.35)
        rachadura(f, 0.8, 2.0, 7.2, 2.0, rng, None, galhos=1, forca=0.8)
        return f
    if nome == 'frente':
        for (x0, x1) in ((0.3, 3.85), (4.15, 7.7)):
            obsidiana(f, f.ret(x0, 0.3, x1, 5.7, 0.6), sem + int(x0 * 10), base='obs', alt=0.55, bisel=0.45)
        for k, y in enumerate((6.1, 7.5, 8.9)):
            for (x0, x1) in ((1.0, 3.85), (4.15, 7.0)):
                obsidiana(f, f.ret(x0, y, x1, y + 1.2, 0.35), sem + 20 + k, base='obs2', alt=0.4, bisel=0.3)
        # nucleo: losango de luz com raios saindo
        lz = f.poli([(4.0, 1.4), (5.4, 3.0), (4.0, 4.6), (2.6, 3.0)])
        f.pintar(lz + 0.25, 'obs0', alt=0.0)
        f.luz(lz, OLHO, LUZ_HB[1], forca=1.4, largura=0.0, halo=1.1)
        for ang in (-2.6, -0.55, 0.55, 2.6, 1.57):
            rachadura(f, 4.0 + math.cos(ang) * 1.3, 3.0 + math.sin(ang) * 1.3, 4.0 + math.cos(ang) * 4.2, 3.0 + math.sin(ang) * 5.0,
                      rng, (0.3, 0.3, 7.7, 10.4), galhos=1, forca=0.95)
        # cinto com runas
        obsidiana(f, f.ret(0, 10.55, w, h), sem + 40, base='obs0', alt=0.3, bisel=0.1)
        f.pintar(f.ret(3.1, 10.5, 4.9, h - 0.05, 0.3), 'obs2', alt=0.5, bisel=0.2)
        f.luz(f.circ(4.0, 11.25, 0.32), OLHO, LUZ_HB[1], forca=1.2, largura=0.0, halo=0.6)
        for x in (1.0, 2.0, 6.0, 7.0):
            f.luz(f.ret(x - 0.08, 10.95, x + 0.08, 11.6), LUZ_HB[0], LUZ_HB[1], forca=0.8, largura=0.0, halo=0.4)
    elif nome == 'tras':
        obsidiana(f, f.ret(0.3, 0.3, w - 0.3, 5.0, 0.6), sem + 5, alt=0.5, bisel=0.4)
        for k, y in enumerate((5.4, 7.0, 8.6)):
            obsidiana(f, f.ret(0.8, y, w - 0.8, y + 1.4, 0.4), sem + 50 + k, base='obs2', alt=0.4, bisel=0.3)
        for y in np.arange(0.8, 10.2, 1.15):          # coluna de nos de luz
            f.luz(f.circ(4.0, y, 0.2), OLHO, LUZ_HB[1], forca=1.0, largura=0.0, halo=0.6)
        rachadura(f, 4.0, 1.0, 1.2, 4.2, rng, (0.3, 0.3, 7.7, 10.3), galhos=1)
        rachadura(f, 4.0, 1.0, 6.8, 4.2, rng, (0.3, 0.3, 7.7, 10.3), galhos=1)
        obsidiana(f, f.ret(0, 10.55, w, h), sem + 41, base='obs0', alt=0.3, bisel=0.1)
    else:
        obsidiana(f, f.ret(0.3, 0.3, w - 0.3, 10.3, 0.4), sem + 7, alt=0.4, bisel=0.35)
        rachadura(f, 2.0, 1.0, 1.8, 9.6, rng, (0.4, 0.4, 3.6, 10.2), galhos=2, forca=0.9)
        obsidiana(f, f.ret(0, 10.55, w, h), sem + 42, base='obs0', alt=0.3, bisel=0.1)
    return f


def hb_braco(nome, w, h, sem):
    f = Face(w, h, sem)
    rng = np.random.default_rng(sem)
    obsidiana(f, f.tudo(), sem, alt=0.0)
    if nome == 'baixo':
        return f
    if nome == 'cima':
        obsidiana(f, f.ret(0.2, 0.2, w - 0.2, h - 0.2, 0.5), sem + 1, base='obs2', alt=0.5)
        f.luz(f.circ(2.0, 2.0, 0.35), OLHO, LUZ_HB[1], forca=1.0, largura=0.0, halo=0.6)
        return f
    # ombreira com pontas
    omb = f.poli([(-0.2, 4.4), (-0.2, 0.6), (0.7, 0.0), (1.4, 0.7), (2.0, 0.0), (2.6, 0.7), (3.3, 0.0), (4.2, 0.6), (4.2, 4.4)])
    obsidiana(f, omb, sem + 2, base='obs2', alt=0.7, bisel=0.45, celula=0.8)
    f.luz(f.ret(-1, 4.25, w + 1, 4.45), LUZ_HB[0], LUZ_HB[1], forca=0.9, largura=0.0, halo=0.6)
    # runa no braco
    if nome in ('dir', 'frente'):
        for (a, b, c, d2) in ((2.0, 5.4, 2.0, 7.6), (1.2, 6.1, 2.8, 6.1), (1.3, 7.2, 2.7, 7.2)):
            vx, vy = c - a, d2 - b
            tt = np.clip(((f.x - a) * vx + (f.y - b) * vy) / (vx * vx + vy * vy + 1e-9), 0, 1)
            seg = np.hypot(f.x - (a + tt * vx), f.y - (b + tt * vy)) - 0.08
            f.luz(seg, LUZ_HB[0], LUZ_HB[1], forca=1.0, largura=0.0, halo=0.55)
    else:
        rachadura(f, 2.0, 4.8, 2.2, 8.0, rng, (0.3, 4.6, 3.7, 8.2), galhos=1, forca=0.8)
    # bracelete
    obsidiana(f, f.ret(-0.2, 8.4, w + 0.2, 11.6, 0.3), sem + 3, base='obs', alt=0.55, bisel=0.3)
    f.luz(f.ret(-1, 9.85, w + 1, 10.05), OLHO, LUZ_HB[1], forca=1.1, largura=0.0, halo=0.7)
    return f


def hb_perna(nome, w, h, sem, parte):
    f = Face(w, h, sem)
    rng = np.random.default_rng(sem)
    if parte == 'bota':
        if nome == 'cima':
            return f
        obsidiana(f, f.ret(-1, 7.0, w + 1, 12.5), sem, alt=0.4, bisel=0.3)
        if nome == 'baixo':
            obsidiana(f, f.tudo(), sem, base='obs0', alt=0.0)
            return f
        f.luz(f.ret(-1, 7.35, w + 1, 7.55), OLHO, LUZ_HB[1], forca=1.1, largura=0.0, halo=0.7)
        obsidiana(f, f.ret(0.2, 8.0, w - 0.2, 9.8, 0.35), sem + 1, base='obs2', alt=0.55, bisel=0.3)
        if nome == 'frente':
            f.luz(f.poli([(2.0, 10.1), (2.6, 10.8), (2.0, 11.5), (1.4, 10.8)]), OLHO, LUZ_HB[1], forca=1.1, largura=0.0, halo=0.6)
        else:
            rachadura(f, 0.5, 10.6, 3.5, 10.9, rng, (0.2, 9.9, 3.8, 11.5), galhos=0, forca=0.7, larg=0.05)
        obsidiana(f, f.ret(-1, 11.55, w + 1, 12.1), sem + 2, base='obs0', alt=0.3, bisel=0.05)
        return f
    obsidiana(f, f.tudo(), sem, alt=0.0)
    if nome in ('cima', 'baixo'):
        return f
    obsidiana(f, f.ret(0.2, 0.3, w - 0.2, 5.0, 0.5), sem + 1, alt=0.5, bisel=0.4)
    if nome in ('frente', 'tras'):
        for (a, b, c, d2) in ((2.0, 1.2, 2.0, 4.0), (1.1, 2.2, 2.9, 2.2), (1.3, 3.4, 2.7, 3.4)):
            vx, vy = c - a, d2 - b
            tt = np.clip(((f.x - a) * vx + (f.y - b) * vy) / (vx * vx + vy * vy + 1e-9), 0, 1)
            seg = np.hypot(f.x - (a + tt * vx), f.y - (b + tt * vy)) - 0.08
            f.luz(seg, LUZ_HB[0], LUZ_HB[1], forca=0.95, largura=0.0, halo=0.55)
    else:
        rachadura(f, 2.0, 0.8, 2.1, 9.8, rng, (0.4, 0.4, 3.6, 11.6), galhos=2, forca=0.85)
    obsidiana(f, f.ret(0.3, 5.3, w - 0.3, 7.6, 0.6), sem + 2, base='obs2', alt=0.6, bisel=0.4)
    f.luz(f.circ(2.0, 6.45, 0.38), OLHO, LUZ_HB[1], forca=1.1, largura=0.0, halo=0.7)
    obsidiana(f, f.ret(0.2, 7.8, w - 0.2, 12, 0.3), sem + 3, alt=0.3, bisel=0.25)
    return f


# ============================================================== montagem das camadas

def montar(conjunto):
    pc, pco, pt, pb, pp = {'coroa': (rei_cabeca, rei_coroa, rei_tronco, rei_braco, rei_perna),
                            'herobrine': (hb_cabeca, hb_coroa, hb_tronco, hb_braco, hb_perna)}[conjunto]
    c1, c2 = Camada(), Camada()
    sem = 100 if conjunto == 'coroa' else 500
    for i, (n, (u, v, w, h)) in enumerate(CABECA.items()):
        c1.colar(pc(n, w, h, sem + i), u, v)
    for i, (n, (u, v, w, h)) in enumerate(CHAPEU.items()):
        c1.colar(pco(n, w, h, sem + 10 + i), u, v)
    for i, (n, (u, v, w, h)) in enumerate(TRONCO.items()):
        c1.colar(pt(n, w, h, sem + 20 + i), u, v)
        c2.colar(pt(n, w, h, sem + 30 + i, calca=True), u, v)
    for i, (n, (u, v, w, h)) in enumerate(BRACO.items()):
        c1.colar(pb(n, w, h, sem + 40 + i), u, v)
    for i, (n, (u, v, w, h)) in enumerate(PERNA.items()):
        c1.colar(pp(n, w, h, sem + 50 + i, 'bota'), u, v)
        c2.colar(pp(n, w, h, sem + 60 + i, 'calca'), u, v)
    return c1, c2


# ============================================================== icones 64x64

def icone(conjunto, peca):
    f = Face(8, 8, 900 + hash(peca) % 97)
    rng = np.random.default_rng(7)
    rei = conjunto == 'coroa'
    base = 'branco' if rei else 'obs'
    def mat(d, m=None, alt=0.45, bisel=0.35):
        if rei:
            return f.pintar(d, m or base, alt=alt, bisel=bisel)
        return obsidiana(f, d, 900 + int(alt * 100), base=m or 'obs', alt=alt, bisel=bisel, celula=0.6)
    if peca == 'elmo':
        casco = f.poli([(1.0, 7.3), (0.8, 3.4), (1.6, 1.6), (4.0, 0.9), (6.4, 1.6), (7.2, 3.4), (7.0, 7.3), (5.3, 7.3), (5.2, 5.4), (2.8, 5.4), (2.7, 7.3)])
        mat(casco)
        if rei:
            f.friso(casco, 'ouro', 0.35, 0.2)
            coroa = f.poli([(1.3, 2.6), (6.7, 2.6), (6.9, 0.3), (5.5, 1.3), (4.0, 0.0), (2.5, 1.3), (1.1, 0.3)])
            f.pintar(coroa, 'ouro', alt=0.7, bisel=0.2)
            f.joia(4.0, 1.9, 0.42, 'rubi', brilho=0.3)
            for cx in (2.3, 5.7):
                f.joia(cx, 2.05, 0.25, 'diamante', brilho=0.25)
            vis = f.ret(1.8, 3.4, 6.2, 4.2, 0.3)
            f.pintar(vis, 'preto')
            f.alt[vis < 0] = -0.2
            f.luz(f.ret(2.2, 3.65, 5.8, 3.95, 0.1), np.array([1.0, 0.3, 0.2]), np.array([0.6, 0.05, 0.02]), forca=0.5, largura=0.0, halo=0.4)
        else:
            for cx in (2.0, 3.0, 4.0, 5.0, 6.0):
                f.alt -= 0.2 * (f.poli([(cx - 0.4, 2.4), (cx, 1.4 if cx != 4 else 1.0), (cx + 0.4, 2.4)]) < 0)
            vis = f.ret(1.5, 3.2, 6.5, 4.4, 0.3)
            f.pintar(vis, 'obs0')
            f.alt[vis < 0] -= 0.3
            for x0 in (1.9, 4.3):
                f.luz(f.ret(x0, 3.5, x0 + 1.8, 4.1, 0.25), OLHO, LUZ_HB[1], forca=1.6, largura=0.0, halo=0.8)
    elif peca == 'peitoral':
        corpo = f.poli([(0.4, 1.6), (2.3, 0.6), (3.0, 1.3), (5.0, 1.3), (5.7, 0.6), (7.6, 1.6), (7.3, 3.6), (6.3, 3.4), (6.2, 7.5), (1.8, 7.5), (1.7, 3.4), (0.7, 3.6)])
        mat(corpo)
        if rei:
            f.friso(corpo, 'ouro', 0.3, 0.2)
            f.pintar(f.ret(3.4, 1.3, 4.6, 7.5), 'veludo', alt=0.5, bisel=0.05)
            coroa = f.poli([(2.6, 4.4), (5.4, 4.4), (5.7, 2.4), (4.8, 3.3), (4.0, 1.9), (3.2, 3.3), (2.3, 2.4)])
            f.pintar(coroa, 'ouro', alt=0.8, bisel=0.18)
            f.joia(4.0, 3.75, 0.42, 'rubi', brilho=0.35)
            f.pintar(f.ret(1.8, 6.7, 6.2, 7.5), 'preto', alt=0.4)
            f.joia(4.0, 7.1, 0.25, 'rubi', brilho=0.2)
        else:
            lz = f.poli([(4.0, 2.2), (5.0, 3.4), (4.0, 4.6), (3.0, 3.4)])
            f.luz(lz, OLHO, LUZ_HB[1], forca=1.4, largura=0.0, halo=0.8)
            for ang in (-2.4, -0.7, 0.7, 2.4):
                rachadura(f, 4 + math.cos(ang), 3.4 + math.sin(ang), 4 + math.cos(ang) * 2.8, 3.4 + math.sin(ang) * 3.4, rng, (1.9, 1.2, 6.1, 7.3), galhos=0, forca=0.9, larg=0.06)
    elif peca == 'calca':
        corpo = f.poli([(1.4, 0.8), (6.6, 0.8), (6.9, 7.4), (4.8, 7.4), (4.0, 3.0), (3.2, 7.4), (1.1, 7.4)])
        mat(corpo)
        if rei:
            f.pintar(f.ret(1.4, 0.8, 6.6, 1.7), 'ouro', alt=0.55)
            for cx in (2.15, 5.85):
                f.pintar(f.circ(cx, 4.6, 0.75), 'ouro', alt=0.6, bisel=0.3)
                f.joia(cx, 4.6, 0.32, 'rubi', brilho=0.25)
        else:
            f.luz(f.ret(1.4, 1.15, 6.6, 1.35), LUZ_HB[0], LUZ_HB[1], forca=0.9, largura=0.0, halo=0.5)
            for cx in (2.15, 5.85):
                f.luz(f.circ(cx, 4.6, 0.35), OLHO, LUZ_HB[1], forca=1.1, largura=0.0, halo=0.6)
    elif peca == 'botas':
        for x0 in (0.7, 4.3):
            b = f.poli([(x0, 2.0), (x0 + 2.0, 2.0), (x0 + 2.1, 5.5), (x0 + 3.0, 6.0), (x0 + 3.0, 7.4), (x0 - 0.1, 7.4)])
            mat(b)
            if rei:
                f.pintar(f.ret(x0, 2.0, x0 + 2.0, 2.8), 'ouro', alt=0.6)
                f.pintar(f.ret(x0 - 0.1, 6.2, x0 + 3.0, 7.4, 0.3), 'ouro', alt=0.6, bisel=0.2)
                f.joia(x0 + 1.0, 2.4, 0.22, 'rubi', brilho=0.2)
            else:
                f.luz(f.ret(x0, 2.5, x0 + 2.0, 2.7), OLHO, LUZ_HB[1], forca=1.0, largura=0.0, halo=0.5)
                f.luz(f.circ(x0 + 2.0, 6.6, 0.22), OLHO, LUZ_HB[1], forca=1.0, largura=0.0, halo=0.5)
    elif peca == 'espada':
        lam = f.poli([(6.9, 0.3), (7.6, 0.4), (7.7, 1.1), (2.9, 5.6), (2.4, 5.1)])
        obsidiana(f, lam, 77, base='obs', alt=0.6, bisel=0.3, celula=0.5)
        # fio de luz
        a, b, c, d2 = 2.8, 5.2, 7.35, 0.65
        vx, vy = c - a, d2 - b
        tt = np.clip(((f.x - a) * vx + (f.y - b) * vy) / (vx * vx + vy * vy), 0, 1)
        f.luz(np.hypot(f.x - (a + tt * vx), f.y - (b + tt * vy)) - 0.07, OLHO, LUZ_HB[1], forca=1.3, largura=0.0, halo=0.7)
        guarda = f.poli([(1.2, 4.6), (2.0, 3.8), (4.2, 6.0), (3.4, 6.8)])
        obsidiana(f, guarda, 78, base='obs2', alt=0.7, bisel=0.2, celula=0.5)
        f.luz(f.circ(2.7, 5.3, 0.28), OLHO, LUZ_HB[1], forca=1.3, largura=0.0, halo=0.6)
        cabo = f.poli([(2.2, 5.9), (2.6, 6.3), (1.2, 7.7), (0.8, 7.3)])
        f.pintar(cabo, 'obs0', alt=0.5, bisel=0.15)
        for k in range(4):
            cx, cy = 2.2 - k * 0.35, 6.3 + k * 0.35
            f.cavar(f.ret(cx - 0.3, cy - 0.04, cx + 0.3, cy + 0.04), 0.1, 0.05)
        f.luz(f.circ(0.85, 7.6, 0.25), OLHO, LUZ_HB[1], forca=1.1, largura=0.0, halo=0.5)
    img = f.render()
    # contorno escuro de 1 px (fica bonito no inventario)
    a = img[..., 3] > 0
    viz = ndimage.binary_dilation(a, iterations=1) & ~a
    img[viz] = (12, 10, 18, 255) if not rei else (40, 26, 8, 255)
    return img


# ============================================================== previa (frente e costas, com a coroa por cima)

def previa(c1, c2, saida, fundo=(34, 36, 44)):
    def face(cam, u, v, w, h):
        return Image.fromarray(cam.im[int(v * S):int((v + h) * S), int(u * S):int((u + w) * S)], 'RGBA')

    E = 3  # escala da previa (pixels HD -> tela)
    larg, alt = 16 * S, 32 * S
    tela = Image.new('RGBA', (larg * 2 * E // 2 + 40, alt * E // 2 + 20), fundo + (255,))
    base = Image.new('RGBA', (larg, alt), (0, 0, 0, 0))
    def vista(lado):
        im = Image.new('RGBA', (larg + 2 * S, alt + 2 * S), (0, 0, 0, 0))
        pele = (150, 120, 100, 255)
        k = 'frente' if lado == 'f' else 'tras'
        espelho = (lambda x: x) if lado == 'f' else (lambda x: x.transpose(Image.FLIP_LEFT_RIGHT))
        pos = {'cab': (4, 0), 'tro': (4, 8), 'bd': (0, 8), 'be': (12, 8), 'pd': (4, 20), 'pe': (8, 20)}
        for nome, (x, y) in pos.items():
            pass
        def colar(img, x, y):
            im.alpha_composite(img, (int(x * S) + S, int(y * S) + S))
        # calca (camada 2) por baixo
        colar(face(c2, *TRONCO[k]), 4, 8)
        pd, pe = face(c2, *PERNA[k]), face(c2, *PERNA[k]).transpose(Image.FLIP_LEFT_RIGHT)
        colar(pd if lado == 'f' else pe, 4, 20)
        colar(pe if lado == 'f' else pd, 8, 20)
        # camada 1
        colar(face(c1, *CABECA[k]), 4, 0)
        colar(face(c1, *TRONCO[k]), 4, 8)
        bd, be = face(c1, *BRACO[k]), face(c1, *BRACO[k]).transpose(Image.FLIP_LEFT_RIGHT)
        colar(bd if lado == 'f' else be, 0, 8)
        colar(be if lado == 'f' else bd, 12, 8)
        bt = face(c1, *PERNA[k])
        colar(bt if lado == 'f' else bt.transpose(Image.FLIP_LEFT_RIGHT), 4, 20)
        colar(bt.transpose(Image.FLIP_LEFT_RIGHT) if lado == 'f' else bt, 8, 20)
        ch = face(c1, *CHAPEU[k]).resize((int(8.6 * S), int(8.6 * S)), Image.LANCZOS)
        im.alpha_composite(ch, (int(3.7 * S) + S, int(-0.3 * S) + S))
        return im
    f = vista('f').resize(((larg + 2 * S) * E // 2, (alt + 2 * S) * E // 2), Image.LANCZOS)
    t = vista('t').resize(((larg + 2 * S) * E // 2, (alt + 2 * S) * E // 2), Image.LANCZOS)
    tela = Image.new('RGBA', (f.size[0] * 2 + 30, f.size[1] + 20), fundo + (255,))
    tela.alpha_composite(f, (10, 10))
    tela.alpha_composite(t, (f.size[0] + 20, 10))
    tela.save(saida)


if __name__ == '__main__':
    so = sys.argv[2:] or ['coroa', 'herobrine']
    for conj in so:
        c1, c2 = montar(conj)
        c1.salvar(f'{OUT}/{conj}_layer1.png')
        c2.salvar(f'{OUT}/{conj}_layer2.png')
        previa(c1, c2, f'{OUT}/previa_{conj}.png')
        pecas = ['elmo', 'peitoral', 'calca', 'botas'] + (['espada'] if conj == 'herobrine' else [])
        ics = []
        for p in pecas:
            img = icone(conj, p)
            Image.fromarray(img, 'RGBA').save(f'{OUT}/{conj}_{p}.png', optimize=True)
            ics.append(Image.fromarray(img, 'RGBA').resize((256, 256), Image.NEAREST))
        folha = Image.new('RGBA', (len(ics) * 270 + 10, 276), (34, 36, 44, 255))
        for i, im in enumerate(ics):
            folha.alpha_composite(im, (10 + i * 270, 10))
        folha.save(f'{OUT}/icones_{conj}.png')
        print('ok', conj)
