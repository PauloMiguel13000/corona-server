"""Armaduras tematicas v3 (09/11, pedido do Paulo: "estao repetidas e de outras cores, deixe menos quadradas").

Cada tema agora tem um MODELO de roupa/armadura proprio (21 modelos), nao so outra cor:
  placas (cavaleiro), lamelar (samurai), romano (couraca musculosa + tiras), farao (colar largo + saiote), carrasco
  (avental de couro, bracos nus), sobretudo (casaco comprido de botoes: pirata, coveiro, vampiro, jaleco), manto
  (tunica e capuz: bruxa, mago, monge, clerigo, espectro rasgado), real (placa com filigrana, gola de arminho),
  esqueleto (costelas vazadas), inseto (carapaca em segmentos com espinhos), pele (manto de pelo com franja),
  barbaro (correias cruzadas no peito nu, ombreira de espinhos), tribal (folhas e penas), fungo (chapeus de cogumelo
  nos ombros), bandagens (mumia, com vaos), mecanico (macacao, engrenagens, canos), magma (placas com rachaduras
  acesas), gelo (cristais com pontas), escamas, ventos (penas nas ombreiras) e couro batido (tachas e correias).
Bordas irregulares (franja, espinhos, barra rasgada, pontas de gelo) e vaos (costelas, tiras, malha) quebram a caixa.
Mesmo arquivo para o Java (equipment) e para o Bedrock (textures/models/armor): 256x128.

Uso: python3 -I armaduras_v3.py [ids...] [--so-previa]
"""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import armaduras_tematicas as A  # noqa: E402
import armaduras_v2 as V2  # noqa: E402
from base import fbm, mix, C, ss, hash2  # noqa: E402

K = A.K
PREV = '/tmp/claude-0/modelos/armaduras_v3'
os.makedirs(PREV, exist_ok=True)

MODELO = {
    'QUARTEL': 'placas', 'TROVAO': 'placas',
    'JARDIM': 'lamelar', 'DELTA': 'lamelar', 'COLISEU': 'romano', 'DESERTO': 'farao', 'CASTELO': 'carrasco', 'PRISAO': 'carrasco',
    'NAUFRAGIO': 'sobretudo', 'FAROL': 'sobretudo', 'CEMITERIO': 'sobretudo', 'SANGUE': 'sobretudo', 'LABORATORIO': 'sobretudo',
    'OBSERVATORIO': 'sobretudo',
    'PANTANO': 'manto', 'TORRE': 'manto', 'BIBLIOTECA': 'manto', 'CATEDRAL': 'manto', 'ALMAS': 'manto', 'ABISMO': 'manto',
    'VIUVA': 'manto',
    'TESOURO': 'real', 'CRIPTA_REAL': 'real', 'CIDADELA': 'real',
    'GELO': 'gelo', 'CRATERA': 'gelo', 'OBSIDIANA': 'gelo',
    'CRIPTA': 'esqueleto', 'WITHER': 'esqueleto',
    'NINHO': 'inseto', 'SECRETA': 'inseto', 'CIDADE_ANTIGA': 'inseto', 'NAVE': 'inseto',
    'COVIL_LOBOS': 'pele', 'URSO': 'pele', 'COVIL': 'pele', 'GELEIRA': 'pele',
    'LABIRINTO': 'barbaro', 'CARMESIM': 'barbaro', 'BASTIAO': 'barbaro', 'TRONO': 'barbaro',
    'SELVA': 'tribal', 'BOSQUE_PALIDO': 'tribal', 'CORO': 'tribal',
    'FUNGOS': 'fungo', 'DISTORCIDO': 'fungo', 'MAUSOLEU': 'bandagens',
    'CAMARA_TESTES': 'mecanico', 'RELOGIO': 'mecanico', 'MINA': 'mecanico', 'FORJA': 'mecanico',
    'VULCAO': 'magma', 'AFOGADA': 'escamas', 'ESGOTO': 'escamas', 'VENTOS': 'ventos', 'ILHAS': 'ventos',
    'ACAMPAMENTO': 'couro_batido', 'FAZENDA': 'couro_batido', 'MOINHO': 'couro_batido',
}
# variantes dentro do mesmo modelo
VARIANTE = {'SANGUE': 'vampiro', 'LABORATORIO': 'jaleco', 'CEMITERIO': 'coveiro', 'FAROL': 'marinha', 'NAUFRAGIO': 'pirata',
            'CATEDRAL': 'clerigo', 'ALMAS': 'espectro', 'ABISMO': 'espectro', 'TORRE': 'mago', 'BIBLIOTECA': 'monge',
            'PANTANO': 'bruxa', 'VIUVA': 'bruxa', 'CIDADELA': 'astral', 'MINA': 'mineiro', 'RELOGIO': 'relojoeiro',
            'FORJA': 'ferreiro', 'ESGOTO': 'gosma', 'TRONO': 'demonio', 'BASTIAO': 'ouro', 'FAZENDA': 'fazendeiro',
            'MOINHO': 'fazendeiro', 'CIDADE_ANTIGA': 'sculk', 'SECRETA': 'sombra', 'WITHER': 'wither', 'GELO': 'rei',
            'TROVAO': 'trovao', 'CRIPTA_REAL': 'morto'}

# tipos de pintura de cada regiao
VAZIO, PLACA, ACAB, PANO, PANO2, COURO, PELO, OSSO, MALHA, ESCURO, BRILHO, PELE_ARM, METAL2, FURO, VIDRO = range(15)


class Face:
    """Mapa de uma face: tipo (o que pintar), id de placa (para o chanfro) e alpha."""

    def __init__(self, parte, face, camada, x0, y0, x1, y1):
        self.parte, self.face, self.camada = parte, face, camada
        self.W, self.H = (x1 - x0) * K, (y1 - y0) * K
        s = (np.arange(self.W) + 0.5) / self.W
        t = (np.arange(self.H) + 0.5) / self.H
        self.S, self.T = np.meshgrid(s, t)
        self.X = x0 * K + self.S * self.W
        self.Y = y0 * K + self.T * self.H
        self.tipo = np.zeros(self.S.shape, int)
        self.pid = np.zeros(self.S.shape, int)
        self.prox = 1
        self.info = {}
        self.frente = face == 'south'
        self.costas = face == 'north'
        self.lado = face in ('east', 'west')
        self.cima = face == 'up'
        self.baixo = face == 'down'

    # ---- primitivas
    def m(self, s0=0.0, s1=1.0, t0=0.0, t1=1.0):
        S, T = self.S, self.T
        return (S >= s0) & (S < s1) & (T >= t0) & (T < t1)

    def pinta(self, mask, tipo, nova_placa=True):
        self.tipo[mask] = tipo
        if tipo in (VAZIO,):
            self.pid[mask] = 0
        elif nova_placa:
            self.pid[mask] = self.prox
            self.prox += 1
        return mask

    def ret(self, tipo, s0=0.0, s1=1.0, t0=0.0, t1=1.0):
        return self.pinta(self.m(s0, s1, t0, t1), tipo)

    def laminas(self, tipo, t0, t1, n, s0=0.0, s1=1.0):
        for i in range(n):
            a = t0 + (t1 - t0) * i / n
            b = t0 + (t1 - t0) * (i + 1) / n
            self.ret(tipo, s0, s1, a, b)

    def colunas(self, tipo, s0, s1, n, t0=0.0, t1=1.0):
        for i in range(n):
            a = s0 + (s1 - s0) * i / n
            b = s0 + (s1 - s0) * (i + 1) / n
            self.ret(tipo, a, b, t0, t1)

    def jitter(self, sal, passo=2):
        """Numero 0..1 por coluna de 'passo' texels (para bordas irregulares)."""
        col = np.floor(self.S * self.W / passo).astype(int)
        return hash2(col, np.zeros_like(col) + sal, sal + 7)

    def franja(self, t_base, amp, sal, passo=2, tipo=VAZIO):
        """Corta abaixo de uma linha irregular (franja, barra rasgada, pontas)."""
        lim = t_base + self.jitter(sal, passo) * amp
        return self.pinta(self.T > lim, tipo, nova_placa=False)

    def dentes(self, t_base, amp, periodo, para_baixo=True, tipo=VAZIO):
        """Corte em dente de serra (espinhos/pontas de gelo): periodo em fracao da largura."""
        fase = (self.S / periodo) % 1.0
        tri = 1 - np.abs(fase - 0.5) * 2
        lim = t_base + tri * amp
        m = (self.T > lim) if para_baixo else (self.T < t_base - tri * amp)
        return self.pinta(m, tipo, nova_placa=False)


# ------------------------------------------------------------------ modelos (cada um desenha a face atual)

def corpo_frente_padrao(f, gorjal=True):
    if gorjal:
        f.ret(ACAB, 0, 1, 0, 0.1)


def tpl_placas(f, var):
    P = f
    if f.parte == 'body' and f.camada == 1:
        if f.baixo:
            return
        if f.cima:
            P.ret(PLACA)
            return
        if f.lado:
            P.ret(MALHA, 0, 1, 0.1, 0.82)
            P.ret(PLACA, 0, 0.18, 0.1, 0.82)
            P.ret(PLACA, 0.82, 1, 0.1, 0.82)
            P.ret(ACAB, 0, 1, 0, 0.1)
            P.ret(COURO, 0, 1, 0.82, 0.9)
            return
        P.ret(ACAB, 0, 1, 0, 0.1)
        if var == 'trovao' and f.frente:
            P.ret(PLACA, 0, 0.5, 0.1, 0.56)
            P.ret(PLACA, 0.5, 1, 0.1, 0.56)
        else:
            P.ret(PLACA, 0, 1, 0.1, 0.56)
        P.laminas(PLACA, 0.56, 0.82, 3)
        P.ret(COURO, 0, 1, 0.82, 0.9)
        P.laminas(PLACA, 0.9, 1.0, 1)
        cint = (f.T > 0.6) & (np.abs(f.S - 0.5) > 0.5 - (f.T - 0.6) * 0.45)
        P.pinta(cint, MALHA, nova_placa=False)
    elif f.parte == 'rarm' and f.camada == 1:
        if f.baixo:
            return
        if f.cima:
            P.ret(PLACA)
            return
        P.laminas(PLACA, 0, 0.4, 3)
        P.ret(ACAB, 0, 1, 0.38, 0.42)
        P.ret(MALHA, 0, 1, 0.42, 0.66)
        P.ret(ACAB, 0, 1, 0.66, 0.7)
        P.ret(PLACA, 0, 1, 0.7, 1.0)
        if var == 'nave':
            P.dentes(0.4, 0.08, 0.5)
    elif f.parte == 'rleg' and f.camada == 1:
        botas(f, 0.56, PLACA)
    elif f.parte == 'body' and f.camada == 2:
        cinto_saia(f, PLACA, faldas=True)
    elif f.parte == 'rleg' and f.camada == 2:
        coxa(f, PLACA, MALHA)


def botas(f, inicio, tipo, cano=ACAB, franja_sal=None):
    if f.cima:
        return
    if f.baixo:
        f.ret(ESCURO)
        return
    f.ret(cano, 0, 1, inicio, inicio + 0.1)
    f.ret(tipo, 0, 1, inicio + 0.1, 0.9)
    f.ret(ESCURO, 0, 1, 0.9, 1.0)
    if f.frente:
        f.ret(tipo, 0.12, 0.88, 0.84, 1.0)
    f.pinta(f.T < inicio, VAZIO, nova_placa=False)
    if franja_sal is not None:
        lim = inicio + f.jitter(franja_sal) * 0.06
        f.pinta(f.T < lim, VAZIO, nova_placa=False)


def cinto_saia(f, tipo, faldas=True, saia_t=1.0, cor_cinto=COURO):
    if f.cima:
        return
    if f.baixo:
        f.ret(ESCURO)
        return
    f.ret(cor_cinto, 0, 1, 0.6, 0.72)
    if faldas and f.frente:
        f.ret(tipo, 0, 0.47, 0.72, saia_t)
        f.ret(tipo, 0.53, 1, 0.72, saia_t)
    elif faldas:
        f.ret(PANO2, 0, 1, 0.72, saia_t)
    f.pinta(f.T < 0.6, VAZIO, nova_placa=False)


def coxa(f, tipo, baixo=PANO2):
    if f.cima:
        return
    if f.baixo:
        f.ret(ESCURO)
        return
    f.ret(baixo)
    if f.frente:
        f.ret(tipo, 0, 1, 0, 0.4)
        f.ret(ACAB, 0, 1, 0.4, 0.56)
        f.ret(tipo, 0, 1, 0.56, 1.0)
    elif f.lado:
        f.ret(ACAB, 0, 0.5, 0.4, 0.56)


def tpl_lamelar(f, var):
    """Samurai: fileiras de laminas amarradas com cordao; ombreiras grandes (sode); saiote em 4 paineis."""
    if f.parte == 'body' and f.camada == 1:
        if f.baixo:
            return
        if f.cima:
            f.ret(PLACA)
            return
        f.ret(PANO, 0, 1, 0, 0.12)
        f.laminas(PLACA, 0.12, 0.8, 7)
        f.ret(PANO2, 0, 1, 0.8, 0.9)
        f.laminas(PLACA, 0.9, 1.0, 1)
    elif f.parte == 'rarm' and f.camada == 1:
        if f.baixo:
            return
        if f.cima:
            f.ret(PLACA)
            return
        f.laminas(PLACA, 0, 0.56, 5)
        f.ret(PANO2, 0, 1, 0.56, 0.78)
        f.ret(PLACA, 0, 1, 0.78, 1.0)
    elif f.parte == 'rleg' and f.camada == 1:
        botas(f, 0.6, PANO2, cano=PANO)
    elif f.parte == 'body' and f.camada == 2:
        if f.cima:
            return
        if f.baixo:
            f.ret(ESCURO)
            return
        f.ret(PANO, 0, 1, 0.6, 0.68)
        for i, (a, b) in enumerate(((0, 0.24), (0.26, 0.49), (0.51, 0.74), (0.76, 1.0))):
            f.laminas(PLACA, 0.68, 1.0, 3, a, b)
        f.pinta(f.T < 0.6, VAZIO, nova_placa=False)
    elif f.parte == 'rleg' and f.camada == 2:
        if f.cima:
            return
        f.ret(PANO2)
        if f.frente or f.lado:
            f.laminas(PLACA, 0, 0.5, 4)


def tpl_romano(f, var):
    """Couraca musculosa, tiras de couro (pteruges) no ombro e na cintura, grevas."""
    if f.parte == 'body' and f.camada == 1:
        if f.baixo:
            return
        if f.cima:
            f.ret(PLACA)
            return
        f.ret(PLACA, 0, 1, 0, 0.84)
        f.ret(ACAB, 0, 1, 0.0, 0.07)
        f.ret(COURO, 0, 1, 0.84, 0.92)
        # tiras penduradas
        for i in range(6):
            a, b = i / 6 + 0.01, (i + 1) / 6 - 0.01
            f.ret(COURO, a, b, 0.92, 1.0)
        if f.lado:
            f.ret(PANO2, 0.25, 0.75, 0.15, 0.84)
    elif f.parte == 'rarm' and f.camada == 1:
        if f.baixo or f.cima:
            if f.cima:
                f.ret(PLACA)
            return
        f.ret(PLACA, 0, 1, 0, 0.18)
        n = 3 if not f.lado else 2
        for i in range(n):
            a, b = i / n + 0.03, (i + 1) / n - 0.03
            f.ret(COURO, a, b, 0.18, 0.42)
        f.pinta(f.m(0, 1, 0.42, 0.72), VAZIO, nova_placa=False)
        f.ret(COURO, 0, 1, 0.72, 0.8)
        f.ret(ACAB, 0, 1, 0.8, 1.0)
        f.pinta(f.m(0, 1, 0.86, 1.0), VAZIO, nova_placa=False)
    elif f.parte == 'rleg' and f.camada == 1:
        if f.cima:
            return
        if f.baixo:
            f.ret(COURO)
            return
        f.ret(PLACA, 0, 1, 0.5, 0.86)     # greva
        f.ret(ACAB, 0, 1, 0.5, 0.56)
        # sandalia: tiras com vaos
        for k in range(3):
            f.ret(COURO, 0, 1, 0.86 + k * 0.045, 0.86 + k * 0.045 + 0.025)
        f.ret(COURO, 0, 1, 0.97, 1.0)
        f.pinta(f.T < 0.5, VAZIO, nova_placa=False)
        if not f.frente:
            f.pinta(f.m(0, 1, 0.56, 0.86) & (f.costas), VAZIO, nova_placa=False)
    elif f.parte == 'body' and f.camada == 2:
        if f.cima:
            return
        if f.baixo:
            f.ret(PANO)
            return
        f.ret(PANO, 0, 1, 0.6, 1.0)
        f.franja(0.92, 0.08, 31, passo=3)
        f.pinta(f.T < 0.6, VAZIO, nova_placa=False)
    elif f.parte == 'rleg' and f.camada == 2:
        if f.cima:
            return
        f.ret(PANO, 0, 1, 0, 0.3)
        f.franja(0.22, 0.08, 37, passo=3)


def tpl_farao(f, var):
    """Colar largo (usekh) em faixas sobre o peito, braceletes, saiote de linho com avental dourado, sandalias."""
    if f.parte == 'body' and f.camada == 1:
        if f.baixo:
            return
        if f.cima:
            f.ret(ACAB)
            return
        # colar: faixas que descem em arco
        d = np.abs(f.S - 0.5) * (1.6 if f.frente or f.costas else 0.4)
        for i, cor in enumerate((ACAB, PANO, ACAB, PANO2, ACAB)):
            m = (f.T >= i * 0.07 + d * 0.0) & (f.T < (i + 1) * 0.07 + 0.12 - d * 0.12)
            f.pinta(m & (f.tipo == VAZIO), cor)
        f.pinta((f.T > 0.48) & (f.T < 0.52) & (f.frente | f.costas), VAZIO, nova_placa=False)
        f.ret(ACAB, 0, 1, 0.84, 0.92)
        f.pinta(f.m(0, 1, 0.92, 1.0), PANO2)
    elif f.parte == 'rarm' and f.camada == 1:
        if f.cima:
            f.ret(ACAB)
            return
        if f.baixo:
            return
        f.ret(ACAB, 0, 1, 0, 0.16)
        f.ret(PANO, 0, 1, 0.16, 0.22)
        f.ret(ACAB, 0, 1, 0.3, 0.38)
        f.ret(ACAB, 0, 1, 0.74, 0.84)
        f.ret(PANO, 0, 1, 0.84, 0.88)
    elif f.parte == 'rleg' and f.camada == 1:
        if f.cima:
            return
        if f.baixo:
            f.ret(COURO)
            return
        f.ret(ACAB, 0, 1, 0.7, 0.76)
        f.ret(COURO, 0, 1, 0.92, 1.0)
        if f.frente:
            f.ret(COURO, 0.4, 0.6, 0.76, 0.92)
    elif f.parte == 'body' and f.camada == 2:
        if f.cima:
            return
        if f.baixo:
            f.ret(PANO2)
            return
        f.ret(ACAB, 0, 1, 0.6, 0.68)
        f.ret(PANO2, 0, 1, 0.68, 1.0)
        if f.frente:
            avental = (f.T > 0.68) & (np.abs(f.S - 0.5) < 0.12 + (f.T - 0.68) * 0.4)
            f.pinta(avental, ACAB)
        f.pinta(f.T < 0.6, VAZIO, nova_placa=False)
    elif f.parte == 'rleg' and f.camada == 2:
        if f.cima:
            return
        f.ret(PANO2, 0, 1, 0, 0.42)
        f.dentes(0.36, 0.08, 0.5)


def tpl_carrasco(f, var):
    """Avental de couro pesado com correias, bracos nus com faixas, calca escura, botas altas."""
    if f.parte == 'body' and f.camada == 1:
        if f.baixo:
            return
        if f.cima:
            f.ret(COURO, 0, 1, 0, 1)
            return
        if f.frente:
            f.ret(COURO, 0.15, 0.85, 0.12, 1.0)
            f.ret(PLACA, 0.25, 0.75, 0.2, 0.5)
            f.ret(ACAB, 0, 1, 0, 0.1)
        elif f.costas:
            # correias em X nas costas
            x = (np.abs((f.S - 0.5) - (f.T - 0.5) * 0.6) < 0.07) | (np.abs((f.S - 0.5) + (f.T - 0.5) * 0.6) < 0.07)
            f.pinta(x, COURO)
            f.ret(ACAB, 0, 1, 0, 0.1)
        else:
            f.ret(COURO, 0.35, 0.65, 0, 1)
        f.ret(COURO, 0, 1, 0.82, 0.9)
    elif f.parte == 'rarm' and f.camada == 1:
        if f.cima:
            f.ret(PLACA)
            return
        if f.baixo:
            return
        f.ret(PLACA, 0, 1, 0, 0.22)
        f.dentes(0.22, 0.06, 0.34)
        for k in range(3):
            f.ret(COURO, 0, 1, 0.66 + k * 0.1, 0.66 + k * 0.1 + 0.06)
    elif f.parte == 'rleg' and f.camada == 1:
        botas(f, 0.46, COURO, cano=PLACA)
    elif f.parte == 'body' and f.camada == 2:
        cinto_saia(f, COURO, faldas=False)
        if not (f.cima or f.baixo):
            f.ret(PANO2, 0, 1, 0.72, 1.0)
    elif f.parte == 'rleg' and f.camada == 2:
        if f.cima:
            return
        f.ret(PANO2)


def tpl_sobretudo(f, var):
    """Casaco comprido: lapelas, botoes, faixa na cintura, abas que descem ate o joelho, punhos dobrados."""
    claro = var == 'jaleco'
    if f.parte == 'body' and f.camada == 1:
        if f.baixo:
            return
        if f.cima:
            f.ret(PANO)
            return
        f.ret(PANO)
        if f.frente:
            f.ret(PANO2, 0.38, 0.62, 0.0, 1.0)            # camisa
            lap = (f.T < 0.45) & (np.abs(f.S - 0.5) < 0.12 + (0.45 - f.T) * 0.6) & (np.abs(f.S - 0.5) > 0.09)
            f.pinta(lap, ACAB if var in ('marinha', 'pirata', 'vampiro') else PANO)
            if var == 'vampiro':
                f.ret(ESCURO, 0.0, 0.2, 0, 0.18)
                f.ret(ESCURO, 0.8, 1.0, 0, 0.18)
        if f.costas and var == 'vampiro':
            f.ret(BRILHO, 0.1, 0.9, 0.05, 0.12)
        f.ret(PANO2 if not claro else COURO, 0, 1, 0.8, 0.88)   # faixa
    elif f.parte == 'rarm' and f.camada == 1:
        if f.cima:
            f.ret(PANO)
            return
        if f.baixo:
            return
        f.ret(PANO)
        f.ret(ACAB, 0, 1, 0, 0.08) if var in ('marinha',) else None
        f.ret(PANO2, 0, 1, 0.8, 0.94)                     # punho dobrado
        if var == 'pirata':
            f.franja(0.96, 0.04, 41)
    elif f.parte == 'rleg' and f.camada == 1:
        botas(f, 0.4, COURO, cano=COURO)
        if not (f.cima or f.baixo):
            f.ret(COURO, 0, 1, 0.4, 0.52)
            f.dentes(0.4, 0.05, 0.5, para_baixo=False)
    elif f.parte == 'body' and f.camada == 2:
        if f.cima:
            return
        if f.baixo:
            f.ret(PANO)
            return
        f.ret(PANO, 0, 1, 0.6, 1.0)
        if f.frente:
            f.pinta(f.m(0.47, 0.53, 0.6, 1.0), VAZIO, nova_placa=False)
        f.pinta(f.T < 0.6, VAZIO, nova_placa=False)
    elif f.parte == 'rleg' and f.camada == 2:
        if f.cima:
            return
        f.ret(ESCURO if not claro else PANO2)
        if not f.frente:
            f.ret(PANO, 0, 1, 0, 0.42)       # abas do casaco
            f.franja(0.4, 0.04, 43) if var in ('pirata', 'coveiro') else None


def tpl_manto(f, var):
    """Tunica e capuz: barra longa, cordao na cintura, mangas largas; espectro = barra rasgada e vaos."""
    rasgado = var == 'espectro'
    if f.parte == 'body' and f.camada == 1:
        if f.baixo:
            return
        if f.cima:
            f.ret(PANO)
            return
        f.ret(PANO)
        if f.frente:
            if var == 'clerigo':
                f.ret(PANO2, 0.28, 0.72, 0, 1)           # tabardo
                f.ret(ACAB, 0.26, 0.3, 0, 1)
                f.ret(ACAB, 0.7, 0.74, 0, 1)
            elif var == 'mago':
                f.ret(PANO2, 0.42, 0.58, 0, 1)
                f.ret(ACAB, 0.4, 0.43, 0, 1)
                f.ret(ACAB, 0.57, 0.6, 0, 1)
            elif var == 'monge':
                f.ret(PANO2, 0, 1, 0, 0.16)              # capuz caido
            elif var == 'bruxa':
                f.ret(PANO2, 0.3, 0.7, 0.18, 0.5)        # corpete
                for k in range(4):
                    f.ret(ACAB, 0.46, 0.54, 0.2 + k * 0.08, 0.23 + k * 0.08)
        if f.costas:
            f.ret(PANO2, 0.1, 0.9, 0, 0.22)               # capuz nas costas
        f.ret(COURO if var in ('monge', 'bruxa') else ACAB, 0, 1, 0.78, 0.84)
        if rasgado:
            f.pinta((hash2(np.floor(f.X / 3).astype(int), np.floor(f.Y / 3).astype(int), 61) > 0.86) & (f.T > 0.2), VAZIO, nova_placa=False)
    elif f.parte == 'rarm' and f.camada == 1:
        if f.cima:
            f.ret(PANO)
            return
        if f.baixo:
            return
        f.ret(PANO)
        f.ret(PANO2, 0, 1, 0.82, 1.0)                    # forro da manga larga
        f.ret(ACAB, 0, 1, 0.78, 0.82)
        if rasgado:
            f.franja(0.84, 0.16, 47)
    elif f.parte == 'rleg' and f.camada == 1:
        if f.cima:
            return
        if f.baixo:
            f.ret(ESCURO)
            return
        if rasgado:
            return
        f.ret(COURO, 0, 1, 0.82, 1.0)
        f.pinta(f.T < 0.82, VAZIO, nova_placa=False)
    elif f.parte == 'body' and f.camada == 2:
        if f.cima:
            return
        if f.baixo:
            f.ret(PANO)
            return
        f.ret(PANO, 0, 1, 0.6, 1.0)
        if f.frente and var == 'clerigo':
            f.ret(PANO2, 0.28, 0.72, 0.6, 1.0)
        f.pinta(f.T < 0.6, VAZIO, nova_placa=False)
    elif f.parte == 'rleg' and f.camada == 2:
        if f.cima:
            return
        if f.baixo:
            if not rasgado:
                f.ret(PANO)
            return
        f.ret(PANO)                                       # tunica ate o pe
        if f.frente and var == 'clerigo':
            f.ret(PANO2, 0, 1, 0, 1)
        if rasgado:
            f.franja(0.55, 0.4, 53, passo=3)
        else:
            f.ret(ACAB, 0, 1, 0.86, 0.9)


def tpl_real(f, var):
    """Placa com filigrana, gola de arminho nos ombros, capa nas costas, cinto de gemas."""
    if f.parte == 'body' and f.camada == 1:
        if f.baixo:
            return
        if f.cima:
            f.ret(PELO if var != 'astral' else PANO2)
            return
        if f.costas:
            f.ret(PANO2)                                  # capa
            f.ret(PELO if var != 'astral' else ACAB, 0, 1, 0, 0.14)
            if var == 'morto':
                f.franja(0.85, 0.15, 59)
            return
        f.ret(PLACA, 0, 1, 0.1, 0.8)
        f.ret(PELO if var != 'astral' else ACAB, 0, 1, 0, 0.12)
        f.ret(ACAB, 0, 1, 0.8, 0.88)
        f.ret(PLACA, 0, 1, 0.88, 1.0)
        if f.lado:
            f.ret(PANO2, 0.4, 1.0, 0.12, 1.0)
    elif f.parte == 'rarm' and f.camada == 1:
        if f.cima:
            f.ret(PELO if var != 'astral' else ACAB)
            return
        if f.baixo:
            return
        f.ret(PELO if var != 'astral' else ACAB, 0, 1, 0, 0.14)
        f.laminas(PLACA, 0.14, 0.42, 2)
        f.ret(ACAB, 0, 1, 0.42, 0.46)
        f.ret(MALHA, 0, 1, 0.46, 0.66)
        f.ret(PLACA, 0, 1, 0.66, 1.0)
        f.ret(ACAB, 0, 1, 0.66, 0.7)
        if var != 'astral':
            f.franja(0.12, 0.04, 67)
            f.pinta(f.m(0, 1, 0.12, 0.16) & (f.tipo == VAZIO), PLACA)
    elif f.parte == 'rleg' and f.camada == 1:
        botas(f, 0.5, PLACA)
    elif f.parte == 'body' and f.camada == 2:
        cinto_saia(f, PLACA, faldas=True, cor_cinto=ACAB)
    elif f.parte == 'rleg' and f.camada == 2:
        coxa(f, PLACA, PANO2)


def tpl_esqueleto(f, var):
    """Costelas com vaos, esterno, coluna nas costas, ossos nos bracos e pernas, bacia."""
    if f.parte == 'body' and f.camada == 1:
        if f.baixo or f.cima:
            if f.cima:
                f.ret(OSSO, 0, 1, 0.3, 0.7)
            return
        if f.frente or f.costas:
            if f.frente:
                f.ret(OSSO, 0.44, 0.56, 0.05, 0.62)      # esterno
            else:
                for k in range(9):                        # vertebras
                    f.ret(OSSO, 0.42, 0.58, 0.04 + k * 0.1, 0.04 + k * 0.1 + 0.07)
            for k in range(5):                            # costelas curvas
                t0 = 0.12 + k * 0.1
                curva = (f.S - 0.5) ** 2 * 0.25
                m = (f.T >= t0 + curva) & (f.T < t0 + 0.05 + curva) & (np.abs(f.S - 0.5) > 0.05)
                f.pinta(m, OSSO)
            f.ret(OSSO, 0, 1, 0.0, 0.05)                  # clavicula
        else:
            for k in range(5):
                f.ret(OSSO, 0, 1, 0.12 + k * 0.1, 0.17 + k * 0.1)
        f.ret(OSSO, 0.2, 0.8, 0.82, 0.92) if f.frente or f.costas else None
    elif f.parte == 'rarm' and f.camada == 1:
        if f.cima:
            f.ret(OSSO)
            return
        if f.baixo:
            return
        f.ret(OSSO, 0, 1, 0, 0.2)                         # ombro de cranio
        f.dentes(0.2, 0.06, 0.25)
        f.ret(OSSO, 0.35, 0.65, 0.26, 0.62)
        f.ret(OSSO, 0.2, 0.8, 0.6, 0.68)
        f.ret(OSSO, 0.3, 0.45, 0.68, 1.0)
        f.ret(OSSO, 0.55, 0.7, 0.68, 1.0)
    elif f.parte == 'rleg' and f.camada == 1:
        if f.cima or f.baixo:
            if f.baixo:
                f.ret(OSSO)
            return
        f.ret(OSSO, 0.3, 0.7, 0.5, 0.86)
        f.ret(OSSO, 0.15, 0.85, 0.86, 1.0)
    elif f.parte == 'body' and f.camada == 2:
        if f.cima or f.baixo:
            return
        f.ret(OSSO, 0.1, 0.9, 0.64, 0.76)                 # bacia
        f.ret(PANO2, 0, 1, 0.76, 1.0)
        f.franja(0.86, 0.14, 71)
    elif f.parte == 'rleg' and f.camada == 2:
        if f.cima or f.baixo:
            return
        f.ret(OSSO, 0.35, 0.65, 0, 0.42)
        f.ret(OSSO, 0.2, 0.8, 0.42, 0.52)                 # patela
        f.ret(OSSO, 0.35, 0.65, 0.52, 1.0)


def tpl_inseto(f, var):
    """Carapaca em segmentos curvos e brilhantes, espinhos nas bordas, olhinhos no peito."""
    if f.parte == 'body' and f.camada == 1:
        if f.baixo:
            return
        if f.cima:
            f.ret(PLACA)
            return
        for k in range(6):
            t0 = 0.02 + k * 0.155
            curva = (f.S - 0.5) ** 2 * 0.3 if (f.frente or f.costas) else 0
            m = (f.T >= t0 + curva) & (f.T < t0 + 0.15 + curva)
            f.pinta(m, PLACA)
        if f.lado:
            f.pinta(f.m(0.3, 0.7, 0.1, 0.9), MALHA if var != 'sombra' else PANO2, nova_placa=False)
        if f.frente and var != 'sombra':
            for (cx, cy) in ((0.38, 0.3), (0.62, 0.3), (0.3, 0.4), (0.7, 0.4)):
                olho = np.hypot((f.S - cx) * f.W, (f.T - cy) * f.H) < 1.6
                f.pinta(olho, BRILHO)
        if var == 'sombra' and (f.frente or f.costas):
            faixa = np.abs((f.S - 0.5) - (f.T - 0.4) * 0.9) < 0.1
            f.pinta(faixa, PANO2)
    elif f.parte == 'rarm' and f.camada == 1:
        if f.cima:
            f.ret(PLACA)
            return
        if f.baixo:
            return
        f.laminas(PLACA, 0, 0.36, 2)
        f.dentes(0.36, 0.1, 0.34)
        f.pinta(f.m(0, 1, 0.46, 0.62) & (f.tipo == VAZIO), MALHA if var != 'sombra' else PANO2)
        f.laminas(PLACA, 0.62, 1.0, 3)
        if f.lado:
            f.dentes(0.62, 0.08, 0.5, para_baixo=False, tipo=PLACA)
    elif f.parte == 'rleg' and f.camada == 1:
        botas(f, 0.5, PLACA)
        if not (f.cima or f.baixo):
            f.dentes(0.5, 0.07, 0.5, para_baixo=False)
    elif f.parte == 'body' and f.camada == 2:
        if f.cima or f.baixo:
            return
        f.laminas(PLACA, 0.6, 1.0, 3)
        f.dentes(0.94, 0.06, 0.25)
    elif f.parte == 'rleg' and f.camada == 2:
        if f.cima:
            return
        f.ret(PANO2)
        f.laminas(PLACA, 0, 0.5, 3)


def tpl_pele(f, var):
    """Manto de pelo nos ombros com franja, correias de couro, braçadeiras, botas de pelo."""
    if f.parte == 'body' and f.camada == 1:
        if f.baixo:
            return
        if f.cima:
            f.ret(PELO)
            return
        f.ret(PELO, 0, 1, 0, 0.42)
        f.franja(0.3, 0.16, 73, tipo=VAZIO)
        if f.frente or f.costas:
            x = (np.abs((f.S - 0.5) - (f.T - 0.6) * 0.8) < 0.07) & (f.T > 0.3)
            f.pinta(x, COURO)
            f.ret(COURO, 0, 1, 0.82, 0.9)
            f.ret(ACAB, 0.44, 0.56, 0.82, 0.9)
        else:
            f.ret(COURO, 0, 1, 0.82, 0.9)
    elif f.parte == 'rarm' and f.camada == 1:
        if f.cima:
            f.ret(PELO)
            return
        if f.baixo:
            return
        f.ret(PELO, 0, 1, 0, 0.34)
        f.franja(0.26, 0.12, 79)
        f.ret(COURO, 0, 1, 0.66, 1.0)
        f.ret(PELO, 0, 1, 0.62, 0.72)
    elif f.parte == 'rleg' and f.camada == 1:
        if f.cima:
            return
        if f.baixo:
            f.ret(COURO)
            return
        f.ret(COURO, 0, 1, 0.62, 1.0)
        f.ret(PELO, 0, 1, 0.5, 0.66)
        f.pinta(f.T < 0.5 + f.jitter(83) * 0.08, VAZIO, nova_placa=False)
    elif f.parte == 'body' and f.camada == 2:
        if f.cima or f.baixo:
            return
        f.ret(COURO, 0, 1, 0.6, 0.7)
        f.ret(PELO, 0, 1, 0.7, 1.0)
        f.franja(0.88, 0.12, 89)
        f.pinta(f.T < 0.6, VAZIO, nova_placa=False)
    elif f.parte == 'rleg' and f.camada == 2:
        if f.cima:
            return
        f.ret(COURO)


def tpl_barbaro(f, var):
    """Peito nu com correias em X, ombreira grande com espinhos, tanga de pelo, botas de pelo; demonio = placas escuras com espinhos."""
    demonio = var == 'demonio'
    if f.parte == 'body' and f.camada == 1:
        if f.baixo:
            return
        if f.cima:
            f.ret(COURO if not demonio else PLACA)
            return
        if demonio:
            f.ret(PLACA, 0, 1, 0.08, 0.5)
            f.laminas(PLACA, 0.5, 0.82, 3)
            f.ret(ACAB, 0, 1, 0, 0.08)
            f.ret(COURO, 0, 1, 0.82, 0.9)
            return
        x = (np.abs((f.S - 0.5) - (f.T - 0.45) * 0.9) < 0.08) | (np.abs((f.S - 0.5) + (f.T - 0.45) * 0.9) < 0.08)
        f.pinta(x & (f.T < 0.82), COURO)
        if f.frente:
            f.pinta(np.hypot((f.S - 0.5) * f.W, (f.T - 0.45) * f.H) < 3.2, ACAB)
        f.ret(COURO, 0, 1, 0.82, 0.92)
    elif f.parte == 'rarm' and f.camada == 1:
        if f.cima:
            f.ret(PLACA)
            return
        if f.baixo:
            return
        f.ret(PLACA, 0, 1, 0, 0.4)
        f.dentes(0.32, 0.12, 0.25)
        espinho = (f.T < 0.12) & (np.abs(((f.S * 3) % 1) - 0.5) < 0.18)
        f.pinta(espinho, OSSO)
        f.ret(COURO, 0, 1, 0.7, 1.0)
        if demonio:
            f.pinta(f.m(0, 1, 0.44, 0.7), PLACA)
            f.dentes(0.7, 0.08, 0.5, para_baixo=False, tipo=PLACA)
    elif f.parte == 'rleg' and f.camada == 1:
        if f.cima:
            return
        if f.baixo:
            f.ret(COURO)
            return
        f.ret(COURO if not demonio else PLACA, 0, 1, 0.6, 1.0)
        f.ret(PELO if not demonio else ACAB, 0, 1, 0.52, 0.64)
        f.pinta(f.T < 0.52 + f.jitter(97) * 0.06, VAZIO, nova_placa=False)
    elif f.parte == 'body' and f.camada == 2:
        if f.cima or f.baixo:
            return
        f.ret(COURO, 0, 1, 0.6, 0.7)
        if f.frente or f.costas:
            f.ret(PELO if not demonio else PLACA, 0.15, 0.85, 0.7, 1.0)
            f.franja(0.9, 0.1, 101)
        f.pinta(f.T < 0.6, VAZIO, nova_placa=False)
    elif f.parte == 'rleg' and f.camada == 2:
        if f.cima:
            return
        if demonio:
            f.ret(PLACA)
            f.ret(ACAB, 0, 1, 0.4, 0.5)


def tpl_tribal(f, var):
    """Folhas sobrepostas com bordas recortadas, colar de ossinhos, penas nos ombros, saia de folhas."""
    def folhas(t0, t1, n, sal):
        for k in range(n):
            a = t0 + (t1 - t0) * k / n
            f.pinta(f.m(0, 1, a, a + (t1 - t0) / n + 0.03) & (f.T < a + 0.05 + (t1 - t0) / n - (1 - np.abs(((f.S * 4 + k * 0.5) % 1) - 0.5) * 2) * 0.05), PANO)
    if f.parte == 'body' and f.camada == 1:
        if f.baixo:
            return
        if f.cima:
            f.ret(PANO)
            return
        folhas(0.1, 0.8, 5, 103)
        f.ret(COURO, 0, 1, 0, 0.08)
        if f.frente:
            for k in range(5):
                cx = 0.2 + k * 0.15
                dente = (np.abs(f.S - cx) < 0.04) & (f.T > 0.08) & (f.T < 0.18 + (k % 2) * 0.04)
                f.pinta(dente, OSSO)
        f.ret(COURO, 0, 1, 0.8, 0.88)
    elif f.parte == 'rarm' and f.camada == 1:
        if f.cima:
            f.ret(PANO2)
            return
        if f.baixo:
            return
        f.ret(PANO2, 0, 1, 0, 0.36)               # penas
        f.dentes(0.28, 0.14, 0.25)
        f.ret(COURO, 0, 1, 0.74, 0.86)
    elif f.parte == 'rleg' and f.camada == 1:
        if f.cima:
            return
        if f.baixo:
            f.ret(COURO)
            return
        for k in range(3):
            f.ret(COURO, 0, 1, 0.66 + k * 0.1, 0.71 + k * 0.1)
        f.ret(COURO, 0, 1, 0.94, 1.0)
    elif f.parte == 'body' and f.camada == 2:
        if f.cima or f.baixo:
            return
        f.ret(COURO, 0, 1, 0.6, 0.68)
        f.ret(PANO, 0, 1, 0.68, 1.0)
        f.dentes(0.86, 0.14, 0.2)
        f.pinta(f.T < 0.6, VAZIO, nova_placa=False)
    elif f.parte == 'rleg' and f.camada == 2:
        if f.cima:
            return
        f.ret(PANO, 0, 1, 0, 0.3)
        f.dentes(0.22, 0.1, 0.34)


def tpl_fungo(f, var):
    """Tunica de micelio, chapeus de cogumelo redondos nos ombros, lamelas, bolinhas de esporo."""
    if f.parte == 'body' and f.camada == 1:
        if f.baixo:
            return
        if f.cima:
            f.ret(PLACA)
            return
        f.ret(PANO2)
        if f.frente or f.costas:
            for k in range(4):
                f.ret(PANO, 0.05, 0.95, 0.15 + k * 0.17, 0.2 + k * 0.17)
        f.ret(PLACA, 0, 1, 0, 0.14)
        f.ret(COURO, 0, 1, 0.82, 0.9)
    elif f.parte == 'rarm' and f.camada == 1:
        if f.cima:
            f.ret(PLACA)
            return
        if f.baixo:
            return
        # chapeu de cogumelo: borda arredondada
        borda = 0.34 + np.sqrt(np.clip(1 - ((f.S - 0.5) * 2) ** 2, 0, 1)) * 0.1
        f.pinta(f.T < borda, PLACA)
        f.ret(PANO, 0, 1, 0.44, 0.5)               # lamelas embaixo do chapeu
        f.ret(PANO2, 0, 1, 0.76, 1.0)
    elif f.parte == 'rleg' and f.camada == 1:
        if f.cima:
            return
        if f.baixo:
            f.ret(PANO2)
            return
        f.ret(PANO2, 0, 1, 0.66, 1.0)
        borda = 0.62 + np.sqrt(np.clip(1 - ((f.S - 0.5) * 2) ** 2, 0, 1)) * 0.08
        f.pinta((f.T > 0.56) & (f.T < borda), PLACA)
    elif f.parte == 'body' and f.camada == 2:
        cinto_saia(f, PANO2, faldas=False)
        if not (f.cima or f.baixo):
            f.ret(PANO2, 0, 1, 0.72, 1.0)
            f.franja(0.92, 0.08, 107)
    elif f.parte == 'rleg' and f.camada == 2:
        if f.cima:
            return
        f.ret(PANO2)


def tpl_bandagens(f, var):
    """Faixas em diagonal com vaos escuros, pontas soltas, colar dourado."""
    def faixas(sal):
        d = ((f.X * 0.5 + f.Y) % 10)
        m = d < 7.0
        f.pinta(m, PANO)
        f.pinta(~m, ESCURO, nova_placa=False)
        buraco = hash2(np.floor(f.X / 4).astype(int), np.floor(f.Y / 4).astype(int), sal) > 0.85
        f.pinta(buraco & ~m, VAZIO, nova_placa=False)
    if f.parte == 'body' and f.camada == 1:
        if f.baixo:
            return
        if f.cima:
            f.ret(PANO)
            return
        faixas(109)
        f.ret(ACAB, 0, 1, 0, 0.1)
        if f.frente:
            f.ret(ACAB, 0.3, 0.7, 0.1, 0.16)
    elif f.parte == 'rarm' and f.camada == 1:
        if f.cima:
            f.ret(PANO)
            return
        if f.baixo:
            return
        faixas(113)
        f.ret(ACAB, 0, 1, 0.8, 0.86)
    elif f.parte == 'rleg' and f.camada == 1:
        if f.cima:
            return
        if f.baixo:
            f.ret(PANO)
            return
        faixas(127)
        f.pinta(f.T < 0.5, VAZIO, nova_placa=False)
    elif f.parte == 'body' and f.camada == 2:
        if f.cima or f.baixo:
            return
        faixas(131)
        f.ret(ACAB, 0, 1, 0.6, 0.66)
        f.pinta(f.T < 0.6, VAZIO, nova_placa=False)
        f.franja(0.92, 0.08, 137)
    elif f.parte == 'rleg' and f.camada == 2:
        if f.cima:
            return
        faixas(139)


def tpl_mecanico(f, var):
    """Macacao/avental de couro com fivelas de latao, engrenagem no peito, canos, luvas grossas."""
    if f.parte == 'body' and f.camada == 1:
        if f.baixo:
            return
        if f.cima:
            f.ret(COURO)
            return
        f.ret(PANO2)
        if f.frente:
            f.ret(COURO, 0.2, 0.8, 0.2, 1.0)            # avental
            f.ret(COURO, 0.2, 0.3, 0, 0.2)
            f.ret(COURO, 0.7, 0.8, 0, 0.2)
            f.ret(ACAB, 0.18, 0.32, 0.16, 0.22)
            f.ret(ACAB, 0.68, 0.82, 0.16, 0.22)
            if var == 'mineiro':
                f.ret(PLACA, 0.25, 0.75, 0.6, 0.78)     # bolso de ferramentas
        if f.costas:
            f.ret(PLACA, 0.15, 0.85, 0.12, 0.62)        # tanque/mochila
            f.ret(ACAB, 0.15, 0.85, 0.3, 0.34)
        if f.lado:
            f.ret(PLACA, 0.35, 0.65, 0.1, 0.9)          # cano
        f.ret(COURO, 0, 1, 0.82, 0.9)
    elif f.parte == 'rarm' and f.camada == 1:
        if f.cima:
            f.ret(PLACA)
            return
        if f.baixo:
            return
        f.ret(PLACA, 0, 1, 0, 0.24)
        f.ret(ACAB, 0, 1, 0.24, 0.28)
        f.ret(PANO2, 0, 1, 0.28, 0.6)
        f.ret(COURO, 0, 1, 0.6, 1.0)                    # luva grossa
        f.ret(ACAB, 0, 1, 0.6, 0.65)
    elif f.parte == 'rleg' and f.camada == 1:
        botas(f, 0.54, COURO, cano=PLACA)
    elif f.parte == 'body' and f.camada == 2:
        cinto_saia(f, COURO, faldas=False)
        if not (f.cima or f.baixo):
            f.ret(PANO2, 0, 1, 0.72, 1.0)
            if f.lado:
                f.ret(COURO, 0.1, 0.9, 0.72, 0.92)      # bolsa no cinto
    elif f.parte == 'rleg' and f.camada == 2:
        if f.cima:
            return
        f.ret(PANO2)
        if f.frente:
            f.ret(PLACA, 0.1, 0.9, 0.36, 0.54)          # joelheira


def tpl_magma(f, var):
    """Placas de rocha resfriada com rachaduras acesas largas, ombreiras pontudas como basalto."""
    if f.parte == 'body' and f.camada == 1:
        if f.baixo:
            return
        if f.cima:
            f.ret(PLACA)
            return
        f.colunas(PLACA, 0, 1, 3 if f.frente or f.costas else 1, 0.06, 0.8)
        f.ret(PLACA, 0, 1, 0, 0.06)
        f.ret(COURO, 0, 1, 0.8, 0.88)
        f.laminas(PLACA, 0.88, 1.0, 1)
        if var == 'forja' or True:
            pass
    elif f.parte == 'rarm' and f.camada == 1:
        if f.cima:
            f.ret(PLACA)
            return
        if f.baixo:
            return
        f.ret(PLACA, 0, 1, 0, 0.4)
        f.dentes(0.3, 0.14, 0.5)
        f.ret(ESCURO, 0, 1, 0.44, 0.64)
        f.ret(PLACA, 0, 1, 0.64, 1.0)
    elif f.parte == 'rleg' and f.camada == 1:
        botas(f, 0.52, PLACA, cano=PLACA)
    elif f.parte == 'body' and f.camada == 2:
        cinto_saia(f, PLACA, faldas=True)
        if not (f.cima or f.baixo):
            f.dentes(0.94, 0.06, 0.25)
    elif f.parte == 'rleg' and f.camada == 2:
        coxa(f, PLACA, ESCURO)


def tpl_gelo(f, var):
    """Cristais facetados, pontas de gelo penduradas (estalactites) nas bordas, gola alta (rei)."""
    rei = var == 'rei'
    if f.parte == 'body' and f.camada == 1:
        if f.baixo:
            return
        if f.cima:
            f.ret(PLACA)
            return
        if rei and f.costas:
            f.ret(PANO2)
            f.dentes(0.9, 0.1, 0.2)
            f.ret(PLACA, 0, 1, 0, 0.16)
            return
        # facetas em losango
        u = (f.S * f.W + f.T * f.H) / 10.0
        v = (f.S * f.W - f.T * f.H) / 10.0
        fac = (np.floor(u).astype(int) * 7 + np.floor(v).astype(int) * 13)
        m = f.m(0, 1, 0.0, 0.8)
        for k in np.unique(fac[m]):
            f.pinta(m & (fac == k), PLACA)
        f.ret(ACAB, 0, 1, 0.8, 0.86)
        f.ret(PLACA, 0, 1, 0.86, 1.0)
        f.dentes(0.94, 0.06, 0.2)
    elif f.parte == 'rarm' and f.camada == 1:
        if f.cima:
            f.ret(PLACA)
            return
        if f.baixo:
            return
        f.ret(PLACA, 0, 1, 0, 0.38)
        f.dentes(0.3, 0.16, 0.34)
        f.pinta(f.m(0, 1, 0.48, 0.66) & (f.tipo == VAZIO), PANO2)
        f.ret(PLACA, 0, 1, 0.66, 1.0)
    elif f.parte == 'rleg' and f.camada == 1:
        botas(f, 0.54, PLACA)
        if not (f.cima or f.baixo):
            f.dentes(0.54, 0.06, 0.34, para_baixo=False)
    elif f.parte == 'body' and f.camada == 2:
        cinto_saia(f, PLACA, faldas=True)
        if not (f.cima or f.baixo):
            f.dentes(0.9, 0.1, 0.2)
    elif f.parte == 'rleg' and f.camada == 2:
        coxa(f, PLACA, PANO2)


def tpl_escamas(f, var):
    """Escamas sobrepostas (cada uma com sombra), nadadeiras nas ombreiras; gosma = escorrendo."""
    if f.parte in ('body', 'rarm', 'rleg') and not (f.parte == 'rleg' and f.camada == 1):
        if f.baixo and f.parte != 'rleg':
            return
        if f.parte == 'body' and f.camada == 2:
            if f.cima or f.baixo:
                return
        if f.parte == 'rleg' and f.camada == 2 and f.cima:
            return
        # escamas: linhas deslocadas, cada escama e uma placa
        lin = np.floor(f.Y / 6).astype(int)
        col = np.floor((f.X + (lin % 2) * 3) / 6).astype(int)
        cod = lin * 101 + col
        m = np.ones(f.S.shape, bool)
        if f.parte == 'body' and f.camada == 2:
            m = f.T >= 0.6
        if f.parte == 'rarm':
            m = (f.T < 0.42) | (f.T > 0.66)
        for k in np.unique(cod[m]):
            f.pinta(m & (cod == k), PLACA)
        if f.parte == 'rarm' and not f.cima:
            f.pinta(f.m(0, 1, 0.42, 0.66), PANO2, nova_placa=False)
            f.dentes(0.3, 0.12, 0.25, tipo=VAZIO) if var != 'gosma' else None
        if f.parte == 'body' and f.camada == 1:
            f.ret(ACAB, 0, 1, 0, 0.08)
            f.ret(COURO, 0, 1, 0.82, 0.9)
        if var == 'gosma':
            gota = (f.T > 0.7 + f.jitter(149, 3) * 0.25) & (f.parte != 'rleg')
            f.pinta(gota, VAZIO, nova_placa=False)
        if f.parte == 'body' and f.camada == 2:
            f.pinta(f.T < 0.6, VAZIO, nova_placa=False)
    elif f.parte == 'rleg' and f.camada == 1:
        botas(f, 0.56, PLACA)


def tpl_ventos(f, var):
    """Placa clara com ombreiras de penas em camadas e saia de penas."""
    if f.parte == 'body' and f.camada == 1:
        if f.baixo:
            return
        if f.cima:
            f.ret(PANO2)
            return
        f.ret(PLACA, 0, 1, 0.08, 0.6)
        f.laminas(PLACA, 0.6, 0.82, 2)
        f.ret(ACAB, 0, 1, 0, 0.08)
        f.ret(COURO, 0, 1, 0.82, 0.9)
        if f.lado:
            f.ret(PANO2, 0.3, 0.7, 0.1, 0.82)
    elif f.parte == 'rarm' and f.camada == 1:
        if f.cima:
            f.ret(PANO2)
            return
        if f.baixo:
            return
        for k in range(3):
            a = k * 0.12
            f.ret(PANO2, 0, 1, a, a + 0.16)
        f.dentes(0.36, 0.14, 0.2)
        f.ret(PLACA, 0, 1, 0.68, 1.0)
        f.ret(ACAB, 0, 1, 0.68, 0.72)
    elif f.parte == 'rleg' and f.camada == 1:
        botas(f, 0.56, PLACA)
    elif f.parte == 'body' and f.camada == 2:
        if f.cima or f.baixo:
            return
        f.ret(COURO, 0, 1, 0.6, 0.68)
        f.ret(PANO2, 0, 1, 0.68, 1.0)
        f.dentes(0.86, 0.14, 0.17)
        f.pinta(f.T < 0.6, VAZIO, nova_placa=False)
    elif f.parte == 'rleg' and f.camada == 2:
        coxa(f, PLACA, PANO2)


def tpl_couro_batido(f, var):
    """Gibao de couro com tachas, correias e fivelas; fazendeiro = camisa xadrez e suspensorio."""
    faz = var == 'fazendeiro'
    if f.parte == 'body' and f.camada == 1:
        if f.baixo:
            return
        if f.cima:
            f.ret(COURO if not faz else PANO)
            return
        if faz:
            f.ret(PANO)
            if f.frente or f.costas:
                f.ret(COURO, 0.22, 0.3, 0, 0.9)
                f.ret(COURO, 0.7, 0.78, 0, 0.9)
            f.ret(COURO, 0, 1, 0.82, 0.9)
            return
        f.ret(COURO, 0, 1, 0.06, 0.82)
        f.ret(PANO2, 0, 1, 0, 0.06)
        for k in range(3):
            f.ret(COURO, 0, 1, 0.2 + k * 0.2, 0.24 + k * 0.2)
        f.ret(COURO, 0, 1, 0.82, 0.9)
    elif f.parte == 'rarm' and f.camada == 1:
        if f.cima:
            f.ret(COURO if not faz else PANO)
            return
        if f.baixo:
            return
        if faz:
            f.ret(PANO, 0, 1, 0, 0.5)
            f.ret(PANO2, 0, 1, 0.5, 0.56)               # manga dobrada
            return
        f.ret(COURO, 0, 1, 0, 0.36)
        f.ret(ACAB, 0, 1, 0.32, 0.36)
        f.ret(COURO, 0, 1, 0.68, 1.0)
        f.ret(ACAB, 0, 1, 0.72, 0.76)
    elif f.parte == 'rleg' and f.camada == 1:
        botas(f, 0.58, COURO, cano=COURO)
    elif f.parte == 'body' and f.camada == 2:
        cinto_saia(f, COURO, faldas=not faz)
        if faz and not (f.cima or f.baixo):
            f.ret(PANO2, 0, 1, 0.72, 1.0)
    elif f.parte == 'rleg' and f.camada == 2:
        if f.cima:
            return
        f.ret(PANO2 if faz else COURO)
        if not faz and f.frente:
            f.ret(COURO, 0, 1, 0.38, 0.54)


MODELOS = {'placas': tpl_placas, 'lamelar': tpl_lamelar, 'romano': tpl_romano, 'farao': tpl_farao, 'carrasco': tpl_carrasco,
           'sobretudo': tpl_sobretudo, 'manto': tpl_manto, 'real': tpl_real, 'esqueleto': tpl_esqueleto, 'inseto': tpl_inseto,
           'pele': tpl_pele, 'barbaro': tpl_barbaro, 'tribal': tpl_tribal, 'fungo': tpl_fungo, 'bandagens': tpl_bandagens,
           'mecanico': tpl_mecanico, 'magma': tpl_magma, 'gelo': tpl_gelo, 'escamas': tpl_escamas, 'ventos': tpl_ventos,
           'couro_batido': tpl_couro_batido}


# ------------------------------------------------------------------ elmos v3.1: rostos sem "olhinhos"
# Viseiras com fendas fundas, caveira com orbitas vazias, mascaras entalhadas, vidro de escafandro...
# Nada de olho aceso com pupila: o rosto e feito de recortes (FURO), vidro (VIDRO) e placas com chanfro.

NOVOS = ('viseira', 'coroa', 'asa', 'chifres', 'presas', 'gladiador', 'centuriao', 'inseto', 'sculk', 'vidro',
         'escafandro', 'caveira', 'lobo', 'urso', 'mascara', 'carrasco', 'mumia', 'cogumelo', 'ninja', 'veu')

# temas que trocam o estilo do elmo da receita por um desenho proprio
ESTILO_V3 = {'NINHO': 'inseto', 'SECRETA': 'ninja', 'VIUVA': 'veu', 'CIDADE_ANTIGA': 'sculk',
             'NAVE': 'vidro', 'CAMARA_TESTES': 'vidro', 'AFOGADA': 'escafandro'}


def _geo(f):
    """Coordenadas em texels: px/py, pm (espelhado: distancia da borda mais perto) e bx (lados: 0 = frente)."""
    px, py = f.S * f.W, f.T * f.H
    pm = np.minimum(px, f.W - px)
    bx = px if f.face == 'east' else f.W - px
    return px, py, pm, bx


def _bez(P, u):
    P = [np.asarray(p, float) for p in P]
    while len(P) > 1:
        P = [P[i] * (1 - u) + P[i + 1] * u for i in range(len(P) - 1)]
    return P[0]


def _curva(x, y, P, w0, w1, n=64):
    """Traco curvo (bezier) que afina de w0 ate w1. Devolve mascara, posicao ao longo (0..1) e distancia ao eixo."""
    melhor = np.full(x.shape, 1e9)
    uu = np.zeros(x.shape)
    eixo = np.full(x.shape, 1e9)
    for i in range(n + 1):
        u = i / n
        qx, qy = _bez(P, u)
        d = np.hypot(x - qx, y - qy)
        dd = d - (w0 + (w1 - w0) * u)
        m = dd < melhor
        melhor = np.where(m, dd, melhor)
        uu = np.where(m, u, uu)
        eixo = np.minimum(eixo, d)
    return melhor < 0, uu, eixo


def _seg(x, y, x0, y0, x1, y1):
    dx, dy = x1 - x0, y1 - y0
    u = np.clip(((x - x0) * dx + (y - y0) * dy) / (dx * dx + dy * dy), 0, 1)
    return np.hypot(x - (x0 + u * dx), y - (y0 + u * dy))


def _dilata(m, r):
    out = m.copy()
    R = int(np.ceil(r))
    for dy in range(-R, R + 1):
        for dx in range(-R, R + 1):
            if (dy or dx) and dx * dx + dy * dy <= r * r:
                out |= V2.deslocar(m, dy, dx, False)
    return out


def _arred(x, y, x0, x1, y0, y1, r):
    """Retangulo de cantos arredondados."""
    cx = np.clip(x, x0 + r, x1 - r)
    cy = np.clip(y, y0 + r, y1 - r)
    return (x >= x0) & (x < x1) & (y >= y0) & (y < y1) & (np.hypot(x - cx, y - cy) < r)


def _casco(f):
    f.ret(PLACA, 0, 1, 0, 0.2)
    f.ret(PLACA, 0, 1, 0.2, 0.88)
    f.ret(ACAB, 0, 1, 0.88, 1.0)
    if f.lado:
        f.pinta((f.T > 0.8) & (np.abs(f.S - 0.55) < 0.22), VAZIO, nova_placa=False)


def elmo_novo(f, estilo, modelo, var):
    px, py, pm, bx = _geo(f)
    W = f.W
    info = f.info
    reb = info.setdefault('rebites', [])

    def lado_px(b):
        return b if f.face == 'east' else W - b

    def chifre(P, w0, w1, tipo=OSSO):
        m, u, _ = _curva(bx, py, P, w0, w1)
        f.pinta(m, tipo)
        info['chifre_m'] = info.get('chifre_m', np.zeros(m.shape, bool)) | m
        info['chifre_u'] = np.where(m, u, info.get('chifre_u', np.zeros(u.shape)))
        return m

    # ---------------- pano, pelo, osso
    if estilo == 'caveira':
        f.ret(OSSO)
        if f.frente:
            orb = (np.abs(pm - 9.8) / 4.7) ** 2.6 + (np.abs(py - 13.8) / 4.3) ** 2.6 < 1
            f.pinta(orb, FURO)
            nariz = (py > 17.6) & (py < 22.4) & (np.abs(px - 16) < 0.5 + (py - 17.6) * 0.5)
            nariz &= ~((py > 20.8) & (np.abs(px - 16) < 0.7))
            f.pinta(nariz, FURO)
            for t0, t1 in ((23.5, 27.0), (28.0, 31.0)):
                f.pinta((py > t0) & (py < t1) & (px > 8) & (px < 24), FURO)
                for k in range(5):
                    x0 = 9 + 3 * k
                    f.pinta((py > t0) & (py < t1) & (px > x0) & (px < x0 + 2), OSSO)
            f.pinta((py > 27.0) & (py < 28.0) & (px > 8) & (px < 24), FURO)
        elif not f.cima:
            f.franja(0.8, 0.2, 167)
        return

    if estilo in ('lobo', 'urso'):
        lobo = estilo == 'lobo'
        f.ret(PELO)
        if f.frente:
            f.pinta((np.abs(px - 16) < 10.2) & (py > 16), VAZIO, nova_placa=False)
            f.pinta(_arred(px, py, 13.4, 18.6, 11.0, 15.2, 1.5), ESCURO)                   # nariz
            if lobo:
                dentes = [(7.4, 5.6, 1.5)] + [(x, 2.4, 1.0) for x in (9.9, 12.1, 14.3)]
            else:
                dentes = [(7.8, 3.8, 1.4)] + [(x, 1.6, 0.9) for x in (10.2, 12.6, 15.0)]
            for cx, L, hw in dentes:
                f.pinta((py > 16) & (py < 16 + L * (1 - np.abs(pm - cx) / hw)), OSSO)
            info['focinho'] = True
        if not f.cima:
            f.franja(0.84, 0.16, 157)
        return

    if estilo == 'mascara':
        if not f.frente:
            f.ret(PANO2)
            if not f.cima:
                f.franja(0.6, 0.3, 163)
            return
        f.ret(PLACA)
        zz = np.abs(((px / 4) % 1) - 0.5) * 4
        f.pinta((py > 2.4 + zz) & (py < 4.4 + zz), ACAB, nova_placa=False)                 # faixa pintada
        dx = pm - 10.2
        olho = (np.abs(dx) < 4.6) & (np.abs(py - (14.0 + 0.26 * dx)) < 2.3 * np.sqrt(np.clip(1 - (dx / 4.6) ** 2, 0, 1)))
        f.pinta(olho, FURO)
        for x in (7.8, 10.2, 12.6):
            f.pinta((np.abs(pm - x) < 0.6) & (py > 18.2) & (py < 22.4 - abs(x - 10.2) * 0.6), ACAB, nova_placa=False)
        f.pinta((np.abs(px - 16) < 1.7) & (py > 8.5) & (py < 21.5), PLACA)                   # nariz entalhado
        f.pinta((np.abs(pm - 14.5) < 0.6) & (py > 20.0) & (py < 21.0), FURO)
        f.pinta(_arred(px, py, 10.5, 21.5, 23.0, 27.0, 1.2), FURO)                          # boca
        f.pinta((py > 23.0) & (py < 24.6) & (px > 11) & (px < 21) & ((np.floor(px) % 2) == 0), OSSO, nova_placa=False)
        return

    if estilo == 'carrasco':
        f.ret(PANO2)
        if f.frente:
            dx = pm - 10.6
            buraco = (np.abs(dx) < 3.7) & (np.abs(py - (14.4 + 0.22 * dx)) < 2.2 * np.sqrt(np.clip(1 - (dx / 3.7) ** 2, 0, 1)))
            f.pinta(buraco, FURO)
            info['carrasco'] = True
        if not f.cima:
            f.franja(0.86, 0.14, 173)
        return

    if estilo == 'mumia':
        d = ((f.X * 0.4 + f.Y) % 8) < 5.5
        f.pinta(d, PANO)
        f.pinta(~d, ESCURO, nova_placa=False)
        if f.frente:
            j1, j2 = f.jitter(181, 2), f.jitter(191, 2)
            f.pinta((py > 12.2 + j1 * 1.4) & (py < 15.4 + j2 * 1.2) & (pm > 3.2), FURO)
            f.pinta((np.abs(px - 16 - (py - 14) * 0.45) < 2.4) & (py > 8) & (py < 20), PANO)
        return

    if estilo == 'cogumelo':
        if f.cima:
            f.ret(PLACA)
            return
        f.ret(PLACA, 0, 1, 0, 0.5)
        f.ret(PANO, 0, 1, 0.5, 0.56)
        f.pinta(f.T >= 0.56, VAZIO, nova_placa=False)
        return

    if estilo == 'ninja':
        f.ret(PANO)
        if not f.cima:
            f.pinta((py > 7) & (py < 11), PANO2)
        if f.frente:
            f.pinta(_arred(px, py, 10.5, 21.5, 5.6, 12.4, 1.0), PLACA)
            f.pinta((py > 14.2) & (py < 20.6) & (pm > 3.0), VAZIO, nova_placa=False)
            info['dobras'] = True
        if f.costas:
            f.pinta((np.abs(px - 14.2 + (py - 9) * 0.08) < 1.3) & (py > 9) & (py < 30.5 - f.jitter(201, 1) * 2), PANO2)
            f.pinta((np.abs(px - 17.8 - (py - 9) * 0.1) < 1.3) & (py > 9) & (py < 28.5 - f.jitter(203, 1) * 2), PANO2)
            f.pinta(_arred(px, py, 13.0, 19.0, 6.5, 11.5, 1.8), PANO2)
        return

    if estilo == 'veu':
        if f.cima:
            f.ret(PANO)
            return
        f.ret(PANO, 0, 1, 0, 8 / 32)
        f.ret(ACAB, 0, 1, 8 / 32, 10 / 32)
        borda = 27.2 + 2.0 * np.abs(np.sin(px * np.pi / 4))
        renda = (((px + py) % 4) < 1) | (((px - py) % 4) < 1)
        corpo = (py >= 10) & (py < borda)
        f.pinta(corpo & renda, PANO, nova_placa=False)
        f.pinta(corpo & ~renda, VAZIO, nova_placa=False)
        f.pinta((py >= borda - 1.0) & (py < borda), PANO, nova_placa=False)
        f.pinta(py >= borda, VAZIO, nova_placa=False)
        return

    # ---------------- cascos de metal
    _casco(f)

    if estilo == 'inseto':
        if f.frente:
            yc = 11.8 + (pm - 3.5) * 0.16                                           # viseira inteira em V
            vis = (pm > 3.5) & (np.abs(py - yc) < 2.7 * np.clip((pm - 3.0) / 2.2, 0, 1))
            f.pinta(_dilata(vis, 1.2) & ~vis, ACAB)
            f.pinta(vis, VIDRO)
            f.pinta((np.abs(px - 16) < 1.5) & (py > 2) & (py < 9.5), ACAB)          # crista de quitina
            f.laminas(PLACA, 0.64, 0.88, 2)                                        # queixo em gomos
        if f.cima:
            m, _, _ = _curva(pm, py, [(12.5, 32.5), (11.0, 21.0), (5.5, 10.0)], 0.75, 0.6)
            f.pinta(m | (np.hypot(pm - 5.5, py - 10.0) < 1.5), ACAB)
        if f.lado or f.costas:
            f.laminas(PLACA, 0.2, 0.88, 3)
        return

    if estilo == 'sculk':
        if f.frente:
            f.laminas(PLACA, 0.6, 0.88, 3)
            f.pinta(_seg(pm, py, 3.6, 10.2, 16.2, 15.0) < 0.95, FURO)
            f.pinta((np.abs(px - 16) < 0.55) & (py > 15) & (py < 28), FURO)
        if f.lado:
            m1, u1, _ = _curva(bx, py, [(11, 14), (7.5, 4.5), (15.5, 1.2)], 2.6, 0.6)
            m2, u2, _ = _curva(bx, py, [(19, 15), (20.5, 7.5), (27, 4.2)], 2.0, 0.5)
            f.pinta(m1, ACAB)
            f.pinta(m2, ACAB)
            f.pinta((m1 & (u1 > 0.78)) | (m2 & (u2 > 0.78)), BRILHO, nova_placa=False)
        info['veias'] = True
        return

    if estilo == 'vidro':
        if f.frente:
            vis = _arred(px, py, 4.0, 28.0, 8.5, 19.5, 3.6)
            f.pinta(_dilata(vis, 1.6) & ~vis, ACAB)
            f.pinta(vis, VIDRO)
            for y in (22.5, 24.5, 26.5):
                f.pinta((np.abs(py - y) < 0.55) & (np.abs(px - 16) < 5.6), FURO)
        if f.lado:
            r = np.hypot(bx - 15.5, py - 15.5)
            f.pinta(r < 5.2, ACAB)
            f.pinta(r < 3.2, METAL2)
        if f.cima:
            f.pinta(np.abs(px - 16) < 1.6, PLACA)
        return

    if estilo == 'escafandro':
        if f.frente:
            r = np.hypot(px - 16, py - 14.5)
            f.pinta(r < 11.6, ACAB)
            f.pinta(r < 8.6, VIDRO)
            f.pinta((r < 8.6) & ((np.abs(px - 16) < 0.6) | (np.abs(py - 14.5) < 0.6)), ACAB)
            for k in range(8):
                a = k * np.pi / 4 + np.pi / 8
                reb.append((16 + 10.1 * np.cos(a), 14.5 + 10.1 * np.sin(a)))
        if f.lado:
            r = np.hypot(bx - 15, py - 14)
            f.pinta(r < 6.6, ACAB)
            f.pinta(r < 4.6, VIDRO)
        return

    if estilo == 'viseira':
        if f.frente:
            f.pinta((np.abs(px - 16) < 1.6) & (py > 1.5) & (py < 28.2), PLACA)
            fenda = (pm > 4.0) & (pm < 14.4) & (np.abs(py - 13.5) < np.minimum(1.6, 0.6 + (pm - 4.0) * 0.8))
            f.pinta(fenda, FURO)
            for cy in (19.5, 22.5, 25.5):          # respiros: fendas curtas nas bochechas
                f.pinta((np.abs(py - cy) < 0.6) & (pm > 6.0) & (pm < 12.0), FURO)
            reb += [(4, 3), (9, 3), (23, 3), (28, 3)]
        if f.costas or f.cima:
            f.pinta(np.abs(px - 16) < 1.6, PLACA)
        if f.lado:
            reb.append((lado_px(8), 13))
    elif estilo == 'coroa':
        if not f.cima:
            f.pinta((py > 4.0) & (py < 7.0), ACAB)
            fx = np.abs((px % 8) - 4)
            f.pinta((py <= 4.0) & (fx < py * 0.85 + 0.6), ACAB, nova_placa=False)
            f.pinta((np.abs((px % 8) - 4) < 1.0) & (py > 4.0) & (py < 6.0), BRILHO)
        if f.frente:
            barra = (np.abs(py - 13.6) < 1.7) & (pm > 5.0)
            fenda = (np.abs(px - 16) < 2.0) & (py > 12.0) & (py < 24.5)
            tt = barra | fenda
            f.pinta(_dilata(tt, 1.5) & ~tt, ACAB)
            f.pinta(tt, FURO)
        if f.cima:
            r = np.hypot(px - 16, py - 16)
            f.pinta(np.abs(r - 12.5) < 1.6, ACAB)
            f.pinta(r < 2.0, BRILHO)
    elif estilo == 'asa':
        if f.frente:
            f.pinta((np.abs(px - 16) < 1.6) & (py > 1.5) & (py < 28.2), PLACA)
            f.pinta(_seg(pm, py, 4.6, 11.0, 14.0, 14.4) < 1.25, FURO)
            for k in range(3):
                f.pinta(_seg(pm, py, 14.0, 9.4 - k * 2.0, 5.0 + k * 1.6, 5.4 - k * 1.7) < 0.55, ACAB, nova_placa=False)
        if f.lado:
            eixo = np.zeros(px.shape, bool)
            piv = np.array((8.5, 19.0))
            for ang, L in ((14, 22), (32, 22), (50, 20), (68, 17), (86, 13)):
                a = np.radians(ang)
                d = np.array((np.cos(a), -np.sin(a)))
                nrm = np.array((np.sin(a), np.cos(a)))
                m, _, e = _curva(bx, py, [piv, piv + d * L * 0.5 + nrm * 1.6, piv + d * L], 2.8, 0.7)
                f.pinta(m, ACAB)
                eixo = (eixo & ~m) | (m & (e < 0.5))
            info['eixo_penas'] = eixo
        if f.costas or f.cima:
            f.pinta(np.abs(px - 16) < 1.6, PLACA)
    elif estilo == 'chifres':
        if f.frente:
            f.pinta((np.abs(px - 16) < 1.6) & (py > 7) & (py < 21), PLACA)
            f.pinta(_seg(pm, py, 3.6, 7.6, 14.2, 11.4) < 1.05, ACAB)
            f.pinta(_seg(pm, py, 4.6, 11.0, 13.8, 14.6) < 1.3, FURO)
            f.pinta((py > 21.0) & (py < 26.6) & (np.abs(px - 16) < 7.0) & ((np.floor(px) % 3) == 1), FURO)
        if f.lado:
            chifre([(11.0, 14.5), (8.0, 2.5), (24.5, 1.6)], 4.4, 0.7)
    elif estilo == 'presas':
        if f.frente:
            f.pinta(_seg(pm, py, 4.4, 12.0, 13.6, 14.4) < 1.3, FURO)
            f.pinta(_seg(pm, py, 3.8, 9.2, 14.0, 11.0) < 0.8, ACAB)
            f.pinta(_arred(px, py, 10.0, 22.0, 16.5, 26.5, 3.2), PLACA)
            for cy in (20.5, 23.5):
                f.pinta((np.abs(py - cy) < 0.6) & (np.abs(px - 16) < 3.6), FURO)
            m, u, _ = _curva(pm, py, [(9.4, 30.5), (4.0, 26.5), (5.4, 17.2)], 2.3, 0.5)
            f.pinta(m, OSSO)
            info['chifre_m'], info['chifre_u'] = m, u
        if f.lado:
            chifre([(11.5, 15.0), (10.5, 6.5), (20.0, 3.6)], 3.2, 0.6)
    elif estilo == 'gladiador':
        if not f.cima:
            f.pinta((py > 6.0) & (py < 9.0), ACAB)
        if f.frente:
            f.pinta((px > 4) & (px < 28) & (py > 9.5) & (py < 27.6), ACAB)
            f.pinta((px > 5.6) & (px < 26.4) & (py > 11.0) & (py < 26.2), PLACA)
            for i in range(6):
                for j in range(4):
                    f.pinta(np.hypot(px - (8.5 + 3 * i), py - (13.5 + 3 * j)) < 1.15, FURO)
    elif estilo == 'centuriao':
        if not f.cima:
            f.pinta((py > 5.5) & (py < 8.5), ACAB)
        if f.frente:
            aberto = (np.abs(px - 16) < 8.0) & (py > 8.5)
            queixo = (py > 23.5) & (pm > 8.0 - (py - 23.5) * 1.1)
            f.pinta(aberto | queixo | (py > 29.5), VAZIO, nova_placa=False)
            reb += [(4, 12), (28, 12), (4, 24), (28, 24)]
        if f.lado:
            f.pinta((py > 25.5) & (py < 28.2), ACAB)
    if estilo in ('centuriao', 'gladiador') and f.cima:
        f.ret(PANO2, 0.38, 0.62, 0, 1)    # crista
    if modelo == 'gelo':
        f.dentes(0.9, 0.1, 0.2)


def rosto(c, f, estilo, tema, ac, bri, sem):
    """Acabamento do rosto: fundo das fendas, vidro, chifres, rebites, pintas, veias..."""
    tp = f.tipo
    px, py, pm, bx = _geo(f)
    info = f.info
    branco = np.array([1.0, 1.0, 1.0])
    tema = np.asarray(tema, float)

    def um(m):
        return m[..., None]

    def mx(a, b, t):
        t = np.asarray(t, float)
        if t.ndim:
            t = t[..., None]
        return np.asarray(a, float) * (1 - t) + np.asarray(b, float) * t

    # fendas e buracos: fundo escuro, sombra da borda de cima, um fio de luz embaixo
    furo = tp == FURO
    if furo.any():
        fundo = mx(tema, C('#07080c'), 0.92)
        tem_acima = V2.deslocar(furo, 1, 0, False)
        tem_abaixo = V2.deslocar(furo, -1, 0, False)
        k = np.where(~tem_acima, 0.45, np.where(~tem_abaixo, 1.9, 1.0))
        c = np.where(um(furo), fundo * k[..., None], c)
    # vidro (viseira de nave, escafandro) e olhos compostos de inseto
    vid = tp == VIDRO
    if vid.any():
        if estilo == 'inseto':
            # viseira de quitina com facetas (favo), escura e envernizada
            y0, y1 = py[vid].min() - 0.5, py[vid].max() + 0.5
            ty = np.clip((py - y0) / (y1 - y0), 0, 1)
            g = mx(mx(tema, C('#040606'), 0.9), mx(tema, C('#0a100a'), 0.7), ty)
            lin = np.floor(py).astype(int)
            hexa = ((lin % 2 == 0) & (np.floor(px) % 3 == 0)) | ((lin % 2 == 1) & (np.floor(px + 1.5) % 3 == 0))
            g = np.where(um(hexa), g * 1.35, g)
            d = np.clip((px - px[vid].min()) / 32, 0, 1) * 0.6 + ty
            g = np.where(um(np.abs(d - 0.5) < 0.06), mx(g, branco, 0.18), g)
        else:
            y0, y1 = py[vid].min() - 0.5, py[vid].max() + 0.5
            x0, x1 = px[vid].min() - 0.5, px[vid].max() + 0.5
            ty = np.clip((py - y0) / (y1 - y0), 0, 1)
            sx = np.clip((px - x0) / (x1 - x0), 0, 1)
            g = mx(mx(tema, C('#030408'), 0.9), mx(tema, C('#0a0d14'), 0.5), ty)
            d = sx * 0.7 + ty
            g = np.where(um(np.abs(d - 0.45) < 0.08), mx(g, branco, 0.3), g)
            g = np.where(um(np.abs(d - 0.66) < 0.035), mx(g, branco, 0.18), g)
            borda = vid & ~V2.deslocar(vid, -1, 0, False)
            g = np.where(um(borda), mx(g, V2.clr(tema, 0.5), 0.35), g)
        c = np.where(um(vid), g, c)
    # chifres e presas: mais escuros na base, aneis
    if 'chifre_m' in info:
        m, u = info['chifre_m'], info['chifre_u']
        c = np.where(um(m), c * (0.68 + 0.45 * u)[..., None], c)
        anel = m & (np.abs(((u * 6.0) % 1) - 0.5) < 0.1) & (u > 0.08) & (u < 0.8)
        c = np.where(um(anel), c * 0.7, c)
    if 'eixo_penas' in info:
        c = np.where(um(info['eixo_penas']), V2.esc(c, 0.8), c)
    # rebites
    for x, y in info.get('rebites', []):
        x, y = round(x), round(y)
        m = (np.abs(px - x) < 1) & (np.abs(py - y) < 1) & (tp != VAZIO) & (tp != FURO) & (tp != VIDRO)
        c = np.where(um(m), V2.clr(c, 0.2), c)
        c = np.where(um(m & (px < x) & (py < y)), V2.clr(c, 0.45), c)
        c = np.where(um(m & (px > x) & (py > y)), V2.esc(c, 0.5), c)
    # joias (coroa, rosa do veu): ponto de luz
    joia = tp == BRILHO
    if joia.any():
        luz = joia & ~V2.deslocar(joia, 1, 0, False) & ~V2.deslocar(joia, 0, 1, False)
        c = np.where(um(luz), V2.clr(c, 0.55), c)
    # focinho do lobo/urso
    if info.get('focinho'):
        c = np.where(um((np.abs(px - 16) < 6.5) & (py > 7.5) & (py < 16) & (tp == PELO)), c * 0.78, c)
    # capuz de carrasco: aro costurado e costura no meio
    if estilo == 'carrasco':
        if furo.any():
            aro = _dilata(furo, 1.2) & ~furo & (tp == PANO2)
            c = np.where(um(aro), V2.esc(c, 0.6), c)
            c = np.where(um(aro & (((np.floor(px) + np.floor(py)) % 3) == 0)), V2.clr(c, 0.3), c)
        if f.frente:
            c = np.where(um((np.abs(px - 16) < 0.55) & (py < 10)), V2.esc(c, 0.7), c)
        if f.cima or f.costas:
            c = np.where(um(np.abs(px - 16) < 0.55), V2.esc(c, 0.7), c)
    # cogumelo: pintas claras no chapeu e lamelas embaixo
    if estilo == 'cogumelo':
        cap = tp == PLACA
        cel = 7.0
        gx, gy = np.floor(px / cel).astype(int), np.floor(py / cel).astype(int)
        z = {'north': 0, 'south': 7, 'east': 13, 'west': 3, 'up': 17, 'down': 23}[f.face]
        h1, h2, h3 = hash2(gx, gy, sem + 31 + z), hash2(gx, gy, sem + 37 + z), hash2(gx, gy, sem + 41 + z)
        cx, cy = (gx + 0.25 + 0.5 * h1) * cel, (gy + 0.25 + 0.5 * h2) * cel
        pinta = cap & (np.hypot(px - cx, py - cy) < 1.3 + 1.4 * h3) & (h3 > 0.2)
        c = np.where(um(pinta), mx(c, C('#f4ecd8'), 0.8), c)
        c = np.where(um((tp == PANO) & ((np.floor(px) % 2) == 0)), V2.esc(c, 0.7), c)
    # sculk: veias acesas finas
    if info.get('veias'):
        z = {'north': 0, 'south': 7, 'east': 13, 'west': 3, 'up': 17, 'down': 23}[f.face]
        n = fbm(px * 0.11, py * 0.11, np.full_like(px, z), 3, 1, sem + 211)
        veia = (np.abs(n - 0.5) < 0.012) & (tp == PLACA)
        halo = _dilata(veia, 1.0) & ~veia & (tp == PLACA)
        c = np.where(um(halo), mx(c, bri, 0.12), c)
        c = np.where(um(veia), mx(c, bri, 0.6), c)
    # ninja: pano escuro (sombra), dobras e simbolo na placa da testa
    if estilo == 'ninja':
        c = np.where(um(tp == PANO), c * 0.22 + mx(tema, C('#15151b'), 0.85) * 0.5, c)
    if info.get('dobras'):
        c = np.where(um((tp == PANO) & (py > 20.6) & (((px * 0.5 + py) % 4) < 0.9)), V2.esc(c, 0.78), c)
        sim = (np.abs(np.abs(px - 16) + np.abs(py - 9) - 2.2) < 0.5) & (tp == PLACA)
        c = np.where(um(sim), V2.esc(c, 0.45), c)
    return c


# ------------------------------------------------------------------ elmo (camada 1, cabeca) por estilo

def elmo_forma(f, estilo, modelo, var):
    if f.baixo:
        return
    if estilo in NOVOS:
        return elmo_novo(f, estilo, modelo, var)
    if estilo in ('capuz',):
        f.ret(PANO)
        if f.frente:
            f.pinta((np.abs(f.S - 0.5) < 0.3) & (f.T > 0.32), VAZIO, nova_placa=False)
            f.ret(PANO2, 0.18, 0.82, 0.24, 0.32)
        if var == 'espectro' and not f.cima:
            f.franja(0.86, 0.14, 151)
        return
    if estilo in ('chapeu',):
        f.ret(PANO, 0, 1, 0, 0.32)
        f.ret(COURO, 0, 1, 0.24, 0.32)
        if not f.cima:
            f.pinta(f.T >= 0.32, VAZIO, nova_placa=False)
        return
    if estilo == 'nemes':
        for k in range(8):
            f.ret(ACAB if k % 2 == 0 else PANO, 0, 1, k / 8, (k + 1) / 8)
        if f.frente:
            f.pinta((np.abs(f.S - 0.5) < 0.32) & (f.T > 0.3), VAZIO, nova_placa=False)
            f.ret(ACAB, 0.1, 0.9, 0.18, 0.3)
        return
    if estilo in ('mitra',):
        f.ret(PANO2)
        f.ret(ACAB, 0, 1, 0.22, 0.28)
        if f.frente:
            f.pinta((np.abs(f.S - 0.5) < 0.3) & (f.T > 0.34), VAZIO, nova_placa=False)
            f.ret(ACAB, 0.45, 0.55, 0.0, 0.22)
        return
    if estilo == 'kabuto':
        f.laminas(PLACA, 0, 0.5, 2)
        f.laminas(PLACA, 0.5, 1.0, 3)
        if f.frente:
            f.pinta((np.abs(f.S - 0.5) < 0.3) & (f.T > 0.38) & (f.T < 0.78), VAZIO, nova_placa=False)
            f.ret(ACAB, 0.1, 0.9, 0.28, 0.36)
            f.ret(PLACA, 0.2, 0.8, 0.7, 1.0)
        return
    if estilo == 'tricornio':
        f.ret(PANO, 0, 1, 0, 0.3)
        f.ret(ACAB, 0, 1, 0.24, 0.3)
        if not f.cima:
            f.pinta(f.T >= 0.3, VAZIO, nova_placa=False)
        return
    if estilo == 'lanterna':
        f.ret(PLACA, 0, 1, 0, 0.3)
        f.ret(COURO, 0, 1, 0.3, 0.38)
        if not f.cima:
            f.pinta(f.T >= 0.38, VAZIO, nova_placa=False)
        return
    if estilo == 'oculos':
        f.ret(COURO, 0, 1, 0, 0.36)
        f.ret(COURO, 0, 1, 0.36, 0.5) if not f.frente else None
        if f.frente:
            for cx in (0.3, 0.7):
                aro = np.hypot((f.S - cx) * f.W, (f.T - 0.42) * f.H) < 4.5
                f.pinta(aro, ACAB)
                lente = np.hypot((f.S - cx) * f.W, (f.T - 0.42) * f.H) < 3.0
                f.pinta(lente, BRILHO)
        if not f.cima:
            f.pinta((f.T >= 0.5) & (f.tipo != ACAB) & (f.tipo != BRILHO), VAZIO, nova_placa=False)
        return
    if estilo in ('lobo', 'urso'):
        f.ret(PELO)
        if f.frente:
            f.pinta((np.abs(f.S - 0.5) < 0.32) & (f.T > 0.5), VAZIO, nova_placa=False)
            f.dentes(0.5, 0.08, 0.16, tipo=VAZIO) if False else None
        if not f.cima:
            f.franja(0.84, 0.16, 157)
        return
    if estilo == 'mascara':
        f.ret(PANO2)
        if not f.frente and not f.cima:
            f.franja(0.6, 0.3, 163)
        return
    if estilo == 'cogumelo':
        f.ret(PLACA, 0, 1, 0, 0.5)
        f.ret(PANO, 0, 1, 0.5, 0.56)
        if not f.cima:
            f.pinta(f.T >= 0.56, VAZIO, nova_placa=False)
        return
    if estilo == 'mumia':
        d = ((f.X * 0.4 + f.Y) % 8) < 5.5
        f.pinta(d, PANO)
        f.pinta(~d, ESCURO, nova_placa=False)
        return
    if estilo == 'caveira':
        f.ret(OSSO)
        if not f.cima and not f.frente:
            f.franja(0.8, 0.2, 167)
        return
    if estilo == 'carrasco':
        f.ret(PANO2)
        if not f.cima:
            f.franja(0.86, 0.14, 173)
        return
    # elmos fechados de metal (viseira, coroa, chifres, presas, olhos, asa, centuriao, gladiador)
    f.ret(PLACA, 0, 1, 0, 0.2)
    f.ret(PLACA, 0, 1, 0.2, 0.88)
    f.ret(ACAB, 0, 1, 0.88, 1.0)
    if f.lado:
        f.pinta((f.T > 0.8) & (np.abs(f.S - 0.55) < 0.22), VAZIO, nova_placa=False)
    if estilo in ('centuriao', 'gladiador') and f.cima:
        f.ret(PANO2, 0.38, 0.62, 0, 1)    # crista
    if modelo == 'gelo':
        f.dentes(0.9, 0.1, 0.2)


# ------------------------------------------------------------------ cores e pintura final

def cores_do_tema(rec, t):
    mat, emb, elm, det, acab, brilho = rec
    tema = np.array(t['rgb'], float) / 255
    ac = C(acab)
    bri = C(brilho) if brilho else V2.clr(tema, 0.6)
    # pano principal e secundario derivados do tema (o secundario contrasta)
    pano = V2.esc(tema, 0.78)
    lum = tema.mean()
    pano2 = V2.esc(mix(tema, C('#1c1a22'), 0.55), 1.0) if lum > 0.35 else V2.clr(mix(tema, ac, 0.4), 0.15)
    return tema, ac, bri, pano, pano2


def colorir(f, rec, t, sem, id_=''):
    mat, emb, elm, det, acab, brilho = rec
    tema, ac, bri, pano, pano2 = cores_do_tema(rec, t)
    if VARIANTE.get(id_) == 'jaleco':
        pano, pano2 = C('#dfe2ea'), V2.esc(tema, 0.8)
    S, T, X, Y = f.S, f.T, f.X, f.Y
    Z = np.full_like(S, {'north': 0, 'south': 7, 'east': 13, 'west': 3, 'up': 17, 'down': 23}[f.face])
    base = A.material(mat, tema, X / 2, Y / 2, Z / 2, sem)
    n = fbm(X * 0.5, Y * 0.5, Z, 2, 1, sem + 77)
    c = np.zeros(S.shape + (3,))
    tp = f.tipo

    def onde(k):
        return (tp == k)[..., None]

    c = np.where(onde(PLACA), base, c)
    c = np.where(onde(METAL2), V2.esc(base, 0.8), c)
    m_ouro = ss(0.4, 0.9, n)[..., None]
    ouro = np.asarray(ac) * (1 - m_ouro) + np.asarray(V2.clr(ac, 0.5)) * m_ouro
    c = np.where(onde(ACAB), ouro, c)
    dobra = (np.sin(X * 0.7 + n * 3) * 0.5 + 0.5)[..., None]
    c = np.where(onde(PANO), np.asarray(pano) * (0.8 + 0.3 * dobra), c)
    c = np.where(onde(PANO2), np.asarray(pano2) * (0.82 + 0.25 * dobra), c)
    couro = np.asarray(C('#5a3a20')) * (0.8 + 0.35 * n)[..., None]
    costura = ((Y % 6) < 1)[..., None] & (np.abs(((X % 8) - 4)) < 0.6)[..., None]
    c = np.where(onde(COURO), np.where(costura, V2.esc(couro, 0.6), couro), c)
    fio = (np.sin(Y * 2.6 + n * 7 + X * 0.4) * 0.5 + 0.5)[..., None]
    pelo_cor = V2.clr(mix(tema, C('#8a7a66'), 0.5), 0.1) if mat not in ('pelo', 'pelo_claro') else A.material(mat, tema, X / 2, Y / 2, Z / 2, sem)
    if elm in ('coroa',) and mat != 'pelo':
        pelo_cor = np.ones(S.shape + (3,)) * C('#f2efe6')   # arminho
        pinta = (hash2(np.floor(X / 4).astype(int), np.floor(Y / 4).astype(int), sem + 5) > 0.82)[..., None]
        pelo_cor = np.where(pinta, C('#1a1a1a') * np.ones_like(pelo_cor), pelo_cor)
    c = np.where(onde(PELO), np.asarray(pelo_cor) * (0.75 + 0.35 * fio), c)
    osso = np.asarray(C('#e6dcc4') if mat != 'obsidiana' else C('#3a3640')) * (0.82 + 0.25 * n)[..., None]
    c = np.where(onde(OSSO), osso, c)
    mal, a_mal = V2.malha(base, X, Y, V2.esc(mix(C('#8a9098'), tema, 0.2), 0.95))
    c = np.where(onde(MALHA), mal, c)
    c = np.where(onde(ESCURO), np.asarray(V2.esc(mix(tema, C('#101014'), 0.7), 1.0)) * (0.8 + 0.3 * n)[..., None], c)
    c = np.where(onde(BRILHO), bri * np.ones_like(c), c)
    a = np.where(tp == VAZIO, 0.0, 1.0)
    a = np.where(tp == MALHA, a_mal, a)
    return c, a, tema, ac


def pintar_face(id_, rec, t, parte, face, camada, x0, y0, x1, y1, sem):
    mat, emb, elm, det, acab, brilho = rec
    modelo = MODELO.get(id_, 'placas')
    var = VARIANTE.get(id_, '')
    f = Face(parte, face, camada, x0, y0, x1, y1)
    elm_v = ESTILO_V3.get(id_, elm) if parte == 'head' else elm
    if parte == 'head':
        if camada == 1:
            elmo_forma(f, elm_v, modelo, var)
    elif parte == 'rarm' and camada == 2:
        pass
    else:
        MODELOS[modelo](f, var)
    c, a, tema, ac = colorir(f, rec, t, sem, id_)
    S, T, W, H = f.S, f.T, f.W, f.H
    metal = mat in V2.METAIS
    # detalhe do tema so nas placas
    if parte != 'head':
        c_det = A.detalhe(c.copy(), det, f.X, f.Y, S, T, W, H, tema, brilho, sem)
        c = np.where((f.tipo == PLACA)[..., None], c_det, c)
    # rosto do elmo (viseira, olhos, coroa...) nos elmos fechados
    if parte == 'head' and camada == 1 and elm_v in NOVOS:
        bri = cores_do_tema(rec, t)[2]
        c = rosto(c, f, elm_v, tema, ac, bri, sem)
    # emblema no peito (nos modelos com placa no peito)
    if parte == 'body' and camada == 1 and face in ('south', 'north') and modelo in ('placas', 'real', 'magma', 'gelo', 'ventos', 'inseto', 'lamelar', 'sobretudo', 'manto', 'romano'):
        tam = 0.5 if face == 'south' else 0.36
        cy = 0.34 if modelo not in ('sobretudo', 'manto') else 0.5
        if modelo in ('sobretudo',) and face == 'south':
            tam = 0
        if tam:
            sombra = A.emblema(np.zeros_like(c) + 2, emb, S - 1.0 / W, T - 1.0 / H, W, H, tema, acab, brilho, cy=cy, tam=tam)
            c = np.where((sombra < 1.5).any(axis=-1)[..., None] & (a > 0)[..., None], V2.esc(c, 0.55), c)
            c2 = A.emblema(c.copy(), emb, S, T, W, H, tema, acab, brilho, cy=cy, tam=tam)
            c = np.where((a > 0)[..., None], c2, c)
    # magma: rachaduras acesas largas
    if modelo == 'magma':
        r = A.ridged(f.X * 0.25, f.Y * 0.25, 0 * f.X, 3, 1, sem + 181) if hasattr(A, 'ridged') else None
    if modelo == 'magma' and parte != 'head':
        from base import ridged
        r = ridged(f.X * 0.22, f.Y * 0.22, 0 * f.X, 3, 1, sem + 181)
        lava = ss(0.58, 0.82, r) * (f.tipo == PLACA)
        alvo = (np.ones_like(c) * C('#ff7a1a')) * (0.8 + 0.4 * r[..., None])
        c = c * (1 - lava[..., None]) + alvo * lava[..., None]
    # botoes do sobretudo
    if modelo == 'sobretudo' and parte == 'body' and camada == 1 and face == 'south':
        for k in range(4):
            for cx in (0.3, 0.7):
                b = np.hypot((S - cx) * W, (T - (0.18 + k * 0.16)) * H) < 1.4
                c = np.where(b[..., None], ac * np.ones_like(c), c)
    # volume
    if metal:
        c = V2.reflexo(c, S, T, np.where(f.tipo == PLACA, f.pid, 0), 0.22)
    luz = 1.12 - 0.26 * T
    if face == 'up':
        luz = np.full_like(T, 1.15)
    if face == 'down':
        luz = np.full_like(T, 0.65)
    if f.lado:
        luz = luz * 0.88
    if f.costas:
        luz = luz * 0.82
    c = np.clip(c * luz[..., None], 0, 1)
    pid = np.where(np.isin(f.tipo, (PLACA, ACAB, OSSO, COURO, METAL2)), f.pid, np.where(np.isin(f.tipo, (VAZIO, FURO, VIDRO)), 0, f.pid + 5000))
    c = V2.relevo(c, pid, a, metal)
    return np.clip(c, 0, 1), a


def textura(id_, t, camada):
    rec = A.R.get(id_, ('metal', 'escudo', 'viseira', 'rebites', '#c0c0c0', None))
    img = np.zeros((32 * K, 64 * K, 4))
    sem = (sum(ord(ch) for ch in id_) * 7 + camada) % 997
    for parte, (u, v, w, h, d) in A.PARTES.items():
        for face, (x0, y0, x1, y1) in A.faces_uv(u, v, w, h, d).items():
            rgb, a = pintar_face(id_, rec, t, parte, face, camada, x0, y0, x1, y1, sem)
            img[y0 * K:y1 * K, x0 * K:x1 * K, :3] = rgb
            img[y0 * K:y1 * K, x0 * K:x1 * K, 3] = a
    return (np.clip(img, 0, 1) * 255 + 0.5).astype(np.uint8)


def previa(id_, t):
    orig = V2.textura
    V2.textura = textura
    try:
        return V2.previa(id_, t)
    finally:
        V2.textura = orig


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
    print(n, 'temas (v3)', '(previas em ' + PREV + ')')
