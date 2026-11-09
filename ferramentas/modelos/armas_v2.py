"""Armas das reliquias v2: espada propria para cada tema (mesmo esquema das armaduras v3).

Antes: 19 estilos pelo tipo de golpe -> varios temas com a MESMA espada so trocando a cor.
Agora: cada tema ganha a arma da "cara" da sua armadura (cavaleiro = espada longa, samurai = katana, farao = khopesh,
pirata = sabre, coveiro = foice, mago = cajado, rei = espada real, inseto = lamina de quitina, lobo = presa, barbaro =
cutelo, tribal = macuahuitl, mecanico = serra de engrenagem, magma = obsidiana com lava, nave = lamina de energia...),
com as cores e o acabamento da armadura do tema (A.R: material, acabamento, brilho).

Saidas (mesmos nomes de antes, para nao mexer no plugin nem no Geyser):
  Java   : models/item/reliquias/espada_<tema>.json + textures/item/reliquias/espada_<tema>.png (3D, cubos a -45 em z)
  Bedrock: icone 2D (textures/items/corona/v2/corona_espada_<tema>.png) e ESPADA 3D NA MAO: attachable por material
           (corona:espada_<tema>_<mat>), geometria do mesmo modelo e animacoes de 1a/3a pessoa a partir do "display" Java.
uso: python3 -I armas_v2.py <saida> [TEMA ...]   (escreve <saida>/java/assets/corona/... e <saida>/bedrock/...)
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)
sys.path.insert(0, os.path.join(AQUI, '..', 'skins', 'monstros'))
import kit  # noqa: E402
from kit import Cubo  # noqa: E402
import armaduras_tematicas as A  # noqa: E402
from base import fbm, ridged, rampa, mix, C, ss, hash2  # noqa: E402
from reliquias import DISPLAY  # noqa: E402

TEMAS = A.TEMAS
MATERIAIS = ('wooden', 'stone', 'iron', 'golden', 'diamond', 'netherite')
ESCALA = 4
LARGURA = 128

# tema -> (arquetipo, variante)
ARMA = {
    'QUARTEL': ('longa', ''), 'TROVAO': ('longa', 'raio'), 'FORJA': ('longa', 'brasa'), 'BIBLIOTECA': ('longa', 'runa'),
    'CASTELO': ('carrasco', ''), 'PRISAO': ('carrasco', 'sculk'),
    'JARDIM': ('katana', ''), 'SECRETA': ('ninjato', ''),
    'COLISEU': ('gladio', ''), 'DELTA': ('gladio', 'spatha'),
    'DESERTO': ('khopesh', ''), 'MAUSOLEU': ('khopesh', 'bronze'),
    'NAUFRAGIO': ('sabre', ''), 'FAROL': ('sabre', 'oficial'),
    'CEMITERIO': ('foice', ''), 'ALMAS': ('foice', 'alma'), 'ABISMO': ('foice', 'vazio'),
    'TORRE': ('cajado', ''), 'PANTANO': ('cajado', 'bruxa'), 'OBSERVATORIO': ('cajado', 'astral'),
    'SANGUE': ('rapieira', ''), 'VIUVA': ('rapieira', 'viuva'),
    'TESOURO': ('real', ''), 'CRIPTA_REAL': ('real', 'morto'), 'CATEDRAL': ('real', 'santa'),
    'CRIPTA': ('osso', ''), 'WITHER': ('osso', 'wither'),
    'NINHO': ('quitina', ''),
    'COVIL_LOBOS': ('presa', ''), 'COVIL': ('presa', 'sangue'), 'GELEIRA': ('presa', 'gelo'), 'URSO': ('garra', ''),
    'LABIRINTO': ('cutelo', ''), 'CARMESIM': ('cutelo', 'sangue'), 'BASTIAO': ('cutelo', 'ouro'), 'TRONO': ('cutelo', 'demonio'),
    'SELVA': ('macuahuitl', ''), 'BOSQUE_PALIDO': ('macuahuitl', 'palido'), 'CORO': ('macuahuitl', 'coral'),
    'FUNGOS': ('cogumelo', ''), 'DISTORCIDO': ('cogumelo', 'distorcido'),
    'CAMARA_TESTES': ('serra', ''), 'RELOGIO': ('serra', 'relogio'), 'MINA': ('serra', 'mina'),
    'VULCAO': ('magma', ''), 'CRATERA': ('magma', 'cratera'), 'OBSIDIANA': ('magma', 'roxo'),
    'AFOGADA': ('coral', ''), 'ESGOTO': ('coral', 'gosma'),
    'VENTOS': ('pena', ''), 'ILHAS': ('pena', 'ilhas'),
    'ACAMPAMENTO': ('facao', ''), 'MOINHO': ('facao', 'moinho'), 'FAZENDA': ('foicinha', ''),
    'GELO': ('gelo', ''), 'CIDADELA': ('vazio', ''), 'CIDADE_ANTIGA': ('sculk', ''),
    'NAVE': ('energia', ''), 'LABORATORIO': ('energia', 'verde'),
}


# ------------------------------------------------------------------ geometria (espaco da espada: y ao longo, x de lado)

def sw(nome, x0, y0, x1, y1, pele, esp=0.7, zc=8.0, emite=0):
    return Cubo(nome, (min(x0, x1), min(y0, y1), zc - esp / 2), (max(x0, x1), max(y0, y1), zc + esp / 2), pele,
                rot=('z', -45.0, (0, 0, 8.0)), emite=emite)


class Espada:
    def __init__(self):
        self.cs = []
        self.n = 0

    def c(self, x0, y0, x1, y1, pele, esp=0.7, emite=0, zc=8.0):
        self.n += 1
        self.cs.append(sw(f'{pele}{self.n}', x0, y0, x1, y1, pele, esp, zc, emite))

    def lamina(self, y0, y1, w0, w1, esp, pele, n=5, dx=None, ponta=1.6, emite=0, ponta_dx=0.0):
        """Lamina em degraus de y0 a y1 (largura w0 -> w1), dx(f) desloca o centro (curva), e ponta afinando."""
        dx = dx or (lambda f: 0.0)
        for i in range(n):
            f0, f1 = i / n, (i + 1) / n
            w = w0 + (w1 - w0) * (f0 + f1) / 2
            cx = dx((f0 + f1) / 2)
            self.c(cx - w / 2, y0 + (y1 - y0) * f0, cx + w / 2, y0 + (y1 - y0) * f1, pele, esp, emite)
        cx = dx(1.0) + ponta_dx
        if ponta > 0:
            self.c(cx - w1 * 0.3, y1, cx + w1 * 0.3, y1 + ponta * 0.6, pele, esp * 0.85, emite)
            self.c(cx - w1 * 0.12 + ponta_dx * 0.5, y1 + ponta * 0.6, cx + w1 * 0.12 + ponta_dx * 0.5, y1 + ponta, pele, esp * 0.7, emite)

    def cabo(self, y0, y1, w=1.5, pele='couro', esp=1.5):
        self.c(-w / 2, y0, w / 2, y1, pele, esp)


def construir(arq, var, sem):
    E = Espada()
    rng = np.random.default_rng(sem)
    if arq == 'longa':
        E.c(-1.2, 0.2, 1.2, 1.6, 'pomo', 1.8)
        E.cabo(1.6, 6.0, pele='couro')
        E.c(-4.4, 6.0, 4.4, 7.2, 'guarda', 1.9)
        E.c(-5.2, 5.6, -4.2, 7.8, 'guarda', 1.5)
        E.c(4.2, 5.6, 5.2, 7.8, 'guarda', 1.5)
        E.c(-0.6, 6.2, 0.6, 7.6, 'gema', 2.1, emite=8)
        emite = 12 if var in ('raio', 'runa', 'brasa') else 0
        E.lamina(7.2, 20.2, 2.7, 2.0, 0.75, 'lamina', n=5, ponta=2.0)
        if var:
            E.c(-0.35, 8.0, 0.35, 19.0, 'runa', 0.9, emite=emite)
    elif arq == 'carrasco':
        E.c(-1.0, 0.2, 1.0, 1.2, 'pomo', 1.6)
        E.cabo(1.2, 7.2, pele='couro')
        E.c(-2.8, 7.2, 2.8, 8.2, 'guarda', 1.8)
        E.lamina(8.2, 18.4, 3.6, 3.9, 0.8, 'lamina', n=4, ponta=0)
        E.c(-1.95, 18.4, -0.55, 20.6, 'lamina', 0.8)
        E.c(0.55, 18.4, 1.95, 20.6, 'lamina', 0.8)
        E.c(-1.95, 20.6, 1.95, 21.6, 'lamina', 0.8)
        if var == 'sculk':
            E.c(-0.3, 9.0, 0.3, 17.5, 'runa', 0.95, emite=12)
    elif arq in ('katana', 'ninjato'):
        E.c(-0.8, 0.3, 0.8, 1.0, 'guarda', 1.6)
        E.cabo(1.0, 7.4, pele='tsuka', w=1.4)
        if arq == 'katana':
            E.c(-2.0, 7.4, 2.0, 8.0, 'guarda', 2.2)
        else:
            E.c(-1.9, 7.4, 1.9, 8.0, 'guarda', 1.9)
        E.c(-0.75, 8.0, 0.85, 8.9, 'ouro', 1.0)
        curva = (lambda f: 0.9 * f ** 1.6) if arq == 'katana' else None
        E.lamina(8.9, 21.0, 1.35, 1.2, 0.55, 'katana', n=7, dx=curva, ponta=1.6, ponta_dx=0.25 if arq == 'katana' else 0)
    elif arq == 'gladio':
        longo = var == 'spatha'
        E.c(-1.4, 0.0, 1.4, 1.7, 'pomo', 2.0)
        E.cabo(1.7, 5.2, pele='madeira_cabo', w=1.4)
        E.c(-2.4, 5.2, 2.4, 6.4, 'guarda', 2.0)
        if longo:
            E.lamina(6.4, 18.5, 2.3, 2.1, 0.7, 'lamina', n=4, ponta=2.0)
        else:
            for y0, y1, w in ((6.4, 8.6, 2.2), (8.6, 10.8, 2.5), (10.8, 13.0, 2.8), (13.0, 14.8, 2.5)):
                E.c(-w / 2, y0, w / 2, y1, 'lamina', 0.75)
            E.lamina(14.8, 16.2, 2.0, 1.3, 0.7, 'lamina', n=1, ponta=1.8)
    elif arq == 'khopesh':
        E.c(-1.0, 0.2, 1.0, 1.2, 'ouro', 1.6)
        E.cabo(1.2, 6.0, pele='fio_ouro', w=1.3)
        E.c(-2.2, 6.0, 2.2, 7.0, 'ouro', 1.7)
        pele = 'bronze' if var == 'bronze' else 'ouro_lamina'
        E.c(-0.75, 7.0, 0.75, 11.5, pele, 0.7)
        for x0, y0, x1, y1 in ((-0.6, 11.5, 1.6, 13.3), (0.6, 13.0, 3.0, 15.0), (1.9, 14.6, 4.0, 16.4),
                               (2.8, 16.1, 4.6, 17.8), (2.3, 17.6, 4.0, 19.0), (1.6, 18.7, 3.0, 19.6)):
            E.c(x0, y0, x1, y1, pele, 0.7)
    elif arq == 'sabre':
        E.c(-0.9, 0.4, 2.4, 1.3, 'ouro' if var == 'oficial' else 'guarda', 1.6)
        E.c(1.6, 1.3, 2.4, 6.0, 'ouro' if var == 'oficial' else 'guarda', 1.4)
        E.cabo(1.3, 6.0, pele='couro', w=1.4)
        E.c(-2.4, 6.0, 2.4, 7.0, 'ouro' if var == 'oficial' else 'guarda', 1.8)
        E.lamina(7.0, 19.6, 1.8, 2.5, 0.65, 'lamina', n=6, dx=lambda f: -1.3 * f ** 1.5, ponta=1.8, ponta_dx=-0.3)
    elif arq == 'foice':
        E.c(-0.6, 0.0, 0.6, 1.0, 'guarda', 1.3)
        E.cabo(1.0, 13.2, pele='madeira_cabo', w=1.1, esp=1.1)
        E.c(-0.9, 13.0, 0.9, 14.2, 'guarda', 1.4)
        pele = 'lamina_escura'
        emite = 10 if var in ('alma', 'vazio') else 0
        for x0, y0, x1, y1 in ((-1.2, 13.6, 1.0, 16.0), (-3.4, 14.4, -0.8, 16.6), (-5.8, 15.2, -3.0, 17.2),
                               (-8.0, 15.9, -5.4, 17.6), (-9.8, 16.4, -7.6, 17.6), (-11.0, 16.8, -9.4, 17.4)):
            E.c(x0, y0, x1, y1, pele, 0.6)
        E.c(-10.0, 15.9, -1.2, 16.4, 'runa', 0.7, emite=emite)
    elif arq == 'cajado':
        E.c(-0.7, 0.0, 0.7, 1.2, 'guarda', 1.3)
        torto = var == 'bruxa'
        for i in range(8):
            dxx = (0.35 if i % 2 else -0.35) if torto else 0.0
            E.c(-0.55 + dxx, 1.2 + i * 2.0, 0.55 + dxx, 3.2 + i * 2.0, 'madeira_cabo', 1.1)
        E.c(-0.9, 6.0, 0.9, 6.8, 'guarda', 1.4)
        E.c(-0.9, 12.0, 0.9, 12.8, 'guarda', 1.4)
        for x in (-1.6, 1.0):
            E.c(x, 16.8, x + 0.6, 20.2, 'guarda', 0.6)
        E.c(-1.6, 17.2, 1.6, 20.2, 'gema', 2.6, emite=15)
        if var == 'astral':
            for x0, y0 in ((-2.8, 21.0), (2.0, 19.4), (-0.4, 21.8)):
                E.c(x0, y0, x0 + 0.7, y0 + 0.7, 'gema', 0.7, emite=15)
    elif arq == 'rapieira':
        E.c(-0.9, 0.0, 0.9, 1.6, 'pomo', 1.4)
        E.cabo(1.6, 5.2, pele='fio_prata' if var != 'viuva' else 'couro', w=1.2)
        E.c(-3.8, 5.2, 3.8, 5.8, 'guarda', 0.9)
        E.c(-4.2, 4.0, -3.4, 5.8, 'guarda', 0.8)
        E.c(3.4, 5.8, 4.2, 7.4, 'guarda', 0.8)
        E.c(-1.7, 5.8, 1.7, 6.8, 'guarda', 2.6)
        E.c(1.2, 1.4, 1.9, 5.2, 'guarda', 0.7)
        E.lamina(6.8, 22.0, 0.95, 0.75, 0.55, 'lamina', n=5, ponta=1.4)
    elif arq == 'real':
        E.c(-1.3, 0.0, 1.3, 1.8, 'ouro', 1.9)
        E.c(-0.55, 0.4, 0.55, 1.4, 'gema', 2.1, emite=10)
        E.cabo(1.8, 6.0, pele='fio_ouro', w=1.5)
        E.c(-3.0, 6.0, 3.0, 7.4, 'ouro', 2.0)
        for s in (-1, 1):
            E.c(s * 3.0, 6.4, s * 4.8, 7.8, 'ouro', 1.6)
            E.c(s * 4.4, 7.4, s * 5.4, 9.0, 'ouro', 1.3)
        E.c(-0.8, 6.3, 0.8, 7.9, 'gema', 2.3, emite=12)
        E.lamina(7.4, 20.6, 3.1, 2.2, 0.8, 'lamina', n=5, ponta=2.2)
        E.c(-0.4, 8.2, 0.4, 18.8, 'ouro', 0.9)
    elif arq == 'osso':
        E.c(-1.5, 0.0, 1.5, 2.0, 'osso', 2.2)
        for i in range(4):
            w = 1.6 if i % 2 == 0 else 1.2
            E.c(-w / 2, 2.0 + i * 1.1, w / 2, 3.1 + i * 1.1, 'osso', w)
        E.c(-3.4, 6.4, 3.4, 7.4, 'osso', 1.8)
        E.c(-3.6, 5.0, -2.8, 6.4, 'dente', 1.0)
        E.c(2.8, 5.0, 3.6, 6.4, 'dente', 1.0)
        y = 7.4
        for i, (w, h) in enumerate(((2.6, 2.6), (2.2, 2.2), (2.8, 2.4), (2.2, 2.2), (2.5, 2.2), (1.8, 2.0))):
            dxx = 0.25 if i % 2 else -0.25
            E.c(dxx - w / 2, y, dxx + w / 2, y + h, 'osso', 0.85)
            E.c(dxx + w / 2 - 0.1, y + 0.4, dxx + w / 2 + 0.8, y + 1.2, 'dente', 0.6)
            y += h
        E.c(-0.5, y, 0.5, y + 1.6, 'dente', 0.7)
    elif arq == 'quitina':
        for i in range(5):
            E.c(-0.7, 0.2 + i * 1.2, 0.7, 1.3 + i * 1.2, 'quitina', 1.4 - 0.1 * (i % 2))
        E.c(-2.6, 6.2, 2.6, 7.2, 'quitina', 1.7)
        E.c(-3.2, 7.0, -2.2, 9.2, 'quitina', 1.0)
        E.c(2.2, 7.0, 3.2, 9.2, 'quitina', 1.0)
        E.lamina(7.2, 19.4, 2.4, 1.6, 0.6, 'quitina_lamina', n=6, dx=lambda f: 1.6 * f ** 2, ponta=2.0, ponta_dx=0.5)
        for i in range(5):
            yy = 8.4 + i * 2.2
            ff = (yy - 7.2) / 12.2
            cx = 1.6 * ff ** 2
            E.c(cx - 1.2 - 0.7, yy, cx - 1.2 + 0.1, yy + 0.8, 'dente', 0.5)
    elif arq in ('presa', 'garra'):
        E.c(-1.2, 0.0, 1.2, 1.4, 'pelo', 1.8)
        E.cabo(1.4, 6.2, pele='pelo', w=1.6, esp=1.6)
        E.c(-2.6, 6.2, 2.6, 7.4, 'couro', 1.9)
        pele = 'gelo' if var == 'gelo' else 'dente'
        if arq == 'presa':
            for y0, y1, w, dxx in ((7.4, 10.0, 3.0, 0.0), (10.0, 12.6, 2.6, 0.4), (12.6, 15.0, 2.1, 0.9), (15.0, 17.0, 1.5, 1.4),
                                   (17.0, 18.6, 0.9, 1.8), (18.6, 19.6, 0.45, 2.0)):
                E.c(dxx - w / 2, y0, dxx + w / 2, y1, pele, 1.0 if y0 < 12 else 0.8)
            if var == 'sangue':
                E.c(-0.2, 9.0, 0.5, 14.0, 'sangue', 1.05)
        else:
            for x in (-1.5, 0.0, 1.5):
                E.lamina(7.4, 15.5 + (0.8 if x == 0 else 0), 0.7, 0.5, 0.5, 'dente', n=3, dx=lambda f, x=x: x + 0.6 * f, ponta=1.2, ponta_dx=0.2)
    elif arq == 'cutelo':
        E.c(-1.3, 0.0, 1.3, 1.3, 'guarda', 1.8)
        E.c(-0.4, -0.8, 0.4, 0.0, 'guarda', 0.8)
        E.cabo(1.3, 7.0, pele='couro', w=1.6)
        E.c(-2.8, 7.0, 2.8, 8.4, 'guarda', 2.1)
        E.c(-3.4, 7.4, -2.8, 9.0, 'guarda', 1.2)
        E.c(2.8, 7.4, 3.4, 9.0, 'guarda', 1.2)
        pele = 'ouro_lamina' if var == 'ouro' else 'lamina_escura'
        E.c(-1.4, 8.4, 4.4, 17.6, pele, 0.9)
        E.c(-1.4, 17.6, 3.2, 19.0, pele, 0.85)
        E.c(-1.4, 19.0, 1.6, 20.0, pele, 0.8)
        # entalhes no fio
        for yy in (11.0, 14.6):
            E.c(4.4, yy, 5.0, yy + 1.2, pele, 0.7)
        if var in ('demonio', 'sangue'):
            E.c(0.6, 9.4, 1.4, 17.0, 'runa', 1.0, emite=13)
    elif arq == 'macuahuitl':
        E.c(-0.9, 0.0, 0.9, 1.2, 'madeira', 1.4)
        E.cabo(1.2, 6.0, pele='couro', w=1.3)
        E.c(-1.6, 6.0, 1.6, 20.0, 'madeira', 0.9)
        E.c(-1.0, 20.0, 1.0, 21.0, 'madeira', 0.85)
        dente = 'osso' if var == 'palido' else ('coral_dente' if var == 'coral' else 'obsidiana_dente')
        for i in range(8):
            yy = 7.0 + i * 1.65
            E.c(-2.3, yy, -1.6, yy + 1.1, dente, 0.6)
            E.c(1.6, yy + 0.8, 2.3, yy + 1.9, dente, 0.6)
        for x in (-0.9, 0.0, 0.9):
            E.c(x - 0.3, -1.8, x + 0.3, 0.0, 'pena', 0.3)
    elif arq == 'cogumelo':
        E.cabo(0.0, 6.4, pele='cogumelo_talo', w=1.6, esp=1.6)
        E.c(-3.8, 6.4, 3.8, 7.8, 'cogumelo_chapeu', 3.2)
        E.c(-2.6, 7.8, 2.6, 8.6, 'cogumelo_chapeu', 2.4)
        E.lamina(8.6, 19.4, 2.2, 1.8, 0.7, 'esporo', n=5, ponta=1.8, emite=7)
    elif arq == 'serra':
        E.c(-1.1, 0.0, 1.1, 1.4, 'cobre', 1.6)
        E.cabo(1.4, 6.0, pele='couro', w=1.4)
        E.c(-2.6, 6.0, 2.6, 8.0, 'engrenagem', 2.0)
        for x0, y0 in ((-3.2, 6.6), (2.6, 6.6), (-0.4, 5.4), (-0.4, 8.0)):
            E.c(x0, y0, x0 + 0.6 if x0 in (-3.2, 2.6) else x0 + 0.8, y0 + 0.8 if x0 in (-3.2, 2.6) else y0 + 0.6, 'engrenagem', 1.4)
        E.c(-1.5, 8.0, 1.5, 19.0, 'lamina', 0.7)
        for i in range(9):
            yy = 8.2 + i * 1.2
            E.c(1.5, yy, 2.2, yy + 0.7, 'lamina', 0.6)
        if var == 'mina':
            E.c(-3.6, 17.4, 1.5, 18.6, 'lamina', 0.8)
            E.c(-4.4, 16.6, -3.4, 17.6, 'lamina', 0.7)
        elif var == 'relogio':
            E.c(-1.1, 6.4, 1.1, 7.6, 'gema', 2.2, emite=10)
        else:
            E.c(-0.3, 9.0, 0.3, 18.0, 'runa', 0.8, emite=10)
    elif arq == 'magma':
        E.c(-1.3, 0.0, 1.3, 1.6, 'rocha', 1.8)
        E.cabo(1.6, 6.0, pele='couro', w=1.4)
        for x0, y0, x1, y1, e in ((-3.0, 6.0, 3.0, 7.4, 2.0), (-3.6, 6.6, -2.4, 8.4, 1.4), (2.2, 6.4, 3.4, 8.0, 1.6)):
            E.c(x0, y0, x1, y1, 'rocha', e)
        y = 7.4
        for i, (w, h) in enumerate(((3.0, 2.8), (2.6, 2.6), (2.9, 2.4), (2.3, 2.4), (2.0, 2.0))):
            dxx = (rng.random() - 0.5) * 0.5
            E.c(dxx - w / 2, y, dxx + w / 2, y + h, 'magma', 0.9)
            y += h
        E.c(-0.5, y, 0.5, y + 1.8, 'magma', 0.7)
        E.c(-0.35, 8.0, 0.35, y - 0.4, 'lava', 1.0, emite=15)
    elif arq == 'coral':
        E.c(-1.0, 0.0, 1.0, 1.2, 'concha', 1.6)
        for i in range(4):
            E.c(-0.7, 1.2 + i * 1.2, 0.7, 2.0 + i * 1.2, 'osso', 1.3)
            E.c(-1.3, 2.0 + i * 1.2, 1.3, 2.4 + i * 1.2, 'osso', 0.5)
        E.c(-2.8, 6.0, 2.8, 7.6, 'concha', 2.2)
        pele = 'gosma' if var == 'gosma' else 'coral'
        E.lamina(7.6, 19.0, 2.0, 1.4, 0.8, pele, n=5, ponta=1.8)
        for i in range(4):
            yy = 9.0 + i * 2.6
            s = -1 if i % 2 else 1
            E.c(s * 1.0, yy, s * 2.6, yy + 0.7, pele, 0.6)
            E.c(s * 2.2, yy + 0.7, s * 2.8, yy + 1.8, pele, 0.5)
    elif arq == 'pena':
        E.c(-0.8, 0.0, 0.8, 1.2, 'guarda', 1.4)
        E.cabo(1.2, 6.0, pele='fio_prata', w=1.3)
        for s in (-1, 1):
            E.c(s * 0.8, 5.6, s * 2.6, 7.0, 'pena', 0.6)
            E.c(s * 2.2, 6.4, s * 3.8, 8.4, 'pena', 0.5)
            E.c(s * 3.2, 7.6, s * 4.4, 9.6, 'pena', 0.4)
        E.c(-0.3, 7.0, 0.3, 21.0, 'guarda', 0.8)
        for i, w in enumerate((2.2, 2.6, 2.8, 2.7, 2.4, 2.0, 1.5, 0.9)):
            yy = 7.0 + i * 1.75
            E.c(-w / 2, yy, w / 2, yy + 1.75, 'pena', 0.45)
    elif arq in ('facao', 'foicinha'):
        E.c(-0.9, 0.0, 0.9, 1.0, 'madeira_cabo', 1.5)
        E.cabo(1.0, 6.4, pele='madeira_cabo', w=1.5)
        for yy in (2.2, 4.6):
            E.c(-0.8, yy, 0.8, yy + 0.5, 'guarda', 1.6)
        if arq == 'facao':
            E.lamina(6.4, 17.6, 1.7, 2.9, 0.6, 'lamina', n=5, dx=lambda f: 0.6 * f, ponta=1.3, ponta_dx=-0.6)
        else:
            for x0, y0, x1, y1 in ((-0.7, 6.4, 0.9, 9.6), (-0.4, 9.4, 1.8, 12.0), (0.6, 11.8, 2.8, 14.0), (1.4, 13.8, 3.2, 15.6),
                                   (0.8, 15.4, 2.8, 16.8), (-0.8, 16.4, 1.4, 17.4), (-2.2, 16.8, -0.4, 17.4)):
                E.c(x0, y0, x1, y1, 'lamina', 0.55)
    elif arq == 'gelo':
        E.c(-1.2, 0.0, 1.2, 1.4, 'gelo', 1.8)
        E.cabo(1.4, 6.0, pele='fio_prata', w=1.4)
        for x0, y0, x1, y1, e in ((-3.2, 6.0, 3.2, 7.2, 1.8), (-4.0, 6.6, -3.0, 8.8, 1.2), (3.0, 6.6, 4.0, 8.8, 1.2)):
            E.c(x0, y0, x1, y1, 'gelo', e)
        E.lamina(7.2, 19.6, 2.6, 1.8, 0.85, 'gelo', n=5, ponta=2.2)
        for i in range(4):
            yy = 8.6 + i * 2.6
            s = 1 if i % 2 else -1
            E.c(s * 1.1, yy, s * 2.5, yy + 1.4, 'gelo', 0.65)
    elif arq == 'vazio':
        E.c(-1.2, 0.0, 1.2, 1.6, 'vazio', 1.8)
        E.cabo(1.6, 6.0, pele='fio_ouro', w=1.4)
        E.c(-3.0, 6.0, 3.0, 7.2, 'guarda', 1.9)
        E.c(-0.7, 6.2, 0.7, 7.6, 'gema', 2.1, emite=15)
        E.lamina(7.2, 19.8, 2.5, 1.9, 0.7, 'vazio', n=5, ponta=2.0, emite=6)
        for x0, y0 in ((-3.0, 12.0), (2.2, 15.6), (-2.6, 18.4), (2.0, 10.0)):
            E.c(x0, y0, x0 + 0.7, y0 + 0.9, 'gema', 0.6, emite=15)
    elif arq == 'sculk':
        E.c(-1.2, 0.0, 1.2, 1.4, 'sculk', 1.8)
        E.cabo(1.4, 6.0, pele='couro', w=1.4)
        E.c(-2.8, 6.0, 2.8, 7.2, 'sculk', 2.0)
        for s in (-1, 1):
            E.c(s * 2.4, 7.0, s * 3.1, 9.2, 'sculk', 0.9)
            E.c(s * 2.7, 9.0, s * 3.2, 10.0, 'gema', 0.6, emite=15)
        E.lamina(7.2, 20.0, 2.6, 2.0, 0.75, 'sculk', n=5, ponta=2.0)
        E.c(-0.3, 8.0, 0.3, 19.0, 'runa', 0.85, emite=13)
    elif arq == 'energia':
        E.c(-1.0, 0.0, 1.0, 1.0, 'metal_hi', 1.6)
        E.cabo(1.0, 6.4, pele='metal_hi', w=1.5)
        E.c(-1.4, 6.4, 1.4, 7.4, 'metal_hi', 1.9)
        E.c(0.75, 2.6, 1.1, 3.4, 'gema', 0.6, emite=15)
        E.lamina(7.4, 20.6, 1.5, 1.3, 0.6, 'energia', n=4, ponta=1.2, emite=15)
    return E.cs


# ------------------------------------------------------------------ pintura

def cores(id_, t):
    rec = A.R.get(id_)
    tema = np.array(t['rgb'], float) / 255.0
    acab = C(rec[4]) if rec else tema
    bri = C(rec[5]) if rec and rec[5] else np.clip(tema + (1 - tema) * 0.6, 0, 1)
    return tema, np.asarray(acab, float), np.asarray(bri, float)


def clr(c, f):
    c = np.asarray(c, float)
    return np.clip(c + (1 - c) * f, 0, 1)


def esc(c, f):
    return np.clip(np.asarray(c, float) * f, 0, 1)


METAL_LAMINA = {   # arquetipo/variante -> rampa da lamina
    'aco': ['#3c424b', '#8e98a4', '#e3e9ef'],
    'aco_escuro': ['#17181d', '#3d3f48', '#7c808c'],
    'ouro': ['#8a6a12', '#e0b53a', '#fff0a0'],
    'bronze': ['#5e3a18', '#b07a3a', '#f0c27a'],
    'cobre': ['#5a2a14', '#b8642e', '#f2a26a'],
}


def pintor(id_, t, arq, var):
    tema, acab, bri = cores(id_, t)
    sem = sum(ord(ch) for ch in id_) % 997
    metal_guarda = acab if np.mean(acab) > 0.25 else clr(acab, 0.35)

    def metal(n, chave):
        r = METAL_LAMINA[chave]
        return rampa(n, [(0, r[0]), (0.5, r[1]), (1, r[2])])

    def p(P):
        x, y, z, s, tt, face, pele = P['x'], P['y'], P['z'], P['s'], P['t'], P['face'], P['pele']
        n = fbm(x * 2.2, y * 2.2, z * 2.2, 3, 1, sem)
        plana = face in ('north', 'south')
        if pele in ('lamina', 'katana', 'lamina_escura', 'ouro_lamina', 'bronze'):
            chave = {'lamina_escura': 'aco_escuro', 'ouro_lamina': 'ouro', 'bronze': 'bronze'}.get(pele, 'aco')
            if pele == 'lamina' and var in ('sangue',):
                chave = 'aco_escuro'
            c = metal(n, chave)
            if pele == 'lamina' and arq in ('longa', 'real', 'gladio', 'carrasco'):
                c = mix(c, tema, 0.18)
            if var == 'brasa':
                c = mix(c, C('#ff6a1a'), ss(0.65, 1.0, (np.abs(s - 0.5) * 2)) * 0.7)
            if pele == 'lamina_escura' and var in ('alma', 'vazio', 'demonio'):
                c = mix(c, bri, ss(0.85, 0.98, ridged(x * 2, y * 2, z * 2, 2, 1, sem + 3)) * 0.9)
            if pele == 'katana' and plana:
                onda = 0.62 + 0.08 * np.sin(y * 3.1 + sem)
                c = np.where((s > onda)[..., None], clr(c, 0.55), c)
            if plana:
                c = mix(c, clr(c, 0.55), (np.minimum(s, 1 - s) < 0.13) * 0.65)          # fio
                if arq in ('longa', 'real', 'gladio', 'rapieira', 'gelo', 'vazio', 'sculk'):
                    c = c * (1 - 0.32 * np.exp(-((s - 0.5) / 0.09) ** 2))[..., None]   # sulco
                risco = ss(0.86, 0.97, ridged(x * 5, y * 0.8, z * 5, 2, 1, sem + 9))
                c = mix(c, clr(c, 0.35), risco * 0.4)
            else:
                c = clr(c, 0.3)
            return c, 1
        if pele == 'guarda':
            c = rampa(n, [(0, esc(metal_guarda, 0.45)), (0.5, metal_guarda), (1, clr(metal_guarda, 0.55))])
            borda = np.minimum(np.minimum(s, 1 - s), np.minimum(tt, 1 - tt))
            c = mix(c, clr(c, 0.4), (borda < 0.12) * 0.6)
            return c, 1
        if pele in ('ouro',):
            return metal(n, 'ouro'), 1
        if pele == 'cobre':
            return metal(n, 'cobre'), 1
        if pele == 'pomo':
            c = rampa(n, [(0, esc(metal_guarda, 0.5)), (0.5, metal_guarda), (1, clr(metal_guarda, 0.6))])
            d = np.hypot(s - 0.5, tt - 0.5)
            c = mix(c, bri, (d < 0.22) * 0.85)
            return c, 1
        if pele == 'gema':
            d = np.hypot(s - 0.4, tt - 0.35)
            c = rampa(n, [(0, esc(bri, 0.45)), (0.6, bri), (1, clr(bri, 0.7))])
            c = mix(c, np.ones(3), (d < 0.15) * 0.7)
            return c, 1
        if pele == 'runa':
            col = np.floor(y * 1.3).astype(np.int64)
            sim = hash2(col, 0, sem + 7)
            forma = ((np.abs(s - 0.5) < 0.16) & (np.abs((y * 1.3) % 1 - 0.5) < 0.38)) | ((np.abs((y * 1.3) % 1 - 0.5) < 0.1) & (np.abs(s - 0.5) < 0.4))
            if var == 'raio':
                forma = np.abs(((y * 1.6) % 1) - 0.5 - (s - 0.5) * 0.8) < 0.18
                sim = np.ones_like(sim)
            forma = forma & (sim > 0.2)
            c = rampa(0.6 + 0.4 * np.sin(y * 2.0), [(0, bri), (1, clr(bri, 0.75))])
            return c, np.where(forma, 1.0, 0.0)
        if pele in ('couro', 'tsuka', 'fio_ouro', 'fio_prata', 'pelo', 'madeira_cabo'):
            if pele == 'couro':
                faixa = 0.5 + 0.5 * np.sin((y * 3.2 + s * 2.0))
                c = rampa(faixa, [(0, '#2a1b12'), (0.5, '#4a3222'), (1, '#6b4a33')])
                c = mix(c, esc(tema, 0.6), ss(0.75, 0.95, faixa) * 0.5)
            elif pele == 'tsuka':
                dia = (np.abs(((y * 1.4 + s) % 1) - 0.5) < 0.18) | (np.abs(((y * 1.4 - s) % 1) - 0.5) < 0.18)
                fio = C('#1a1a22') if arq == 'katana' else C('#0c0c10')
                pele_raia = C('#e8e2d2') if arq == 'katana' else C('#3a3a44')
                c = np.where(dia[..., None], fio * np.ones(s.shape + (3,)), pele_raia * np.ones(s.shape + (3,)))
                c = mix(c, tema, 0.12)
            elif pele in ('fio_ouro', 'fio_prata'):
                anel = (np.floor(y * 2.4) % 2) == 0
                base = METAL_LAMINA['ouro'] if pele == 'fio_ouro' else METAL_LAMINA['aco']
                c = rampa(n, [(0, base[0]), (0.5, base[1]), (1, base[2])])
                c = np.where(anel[..., None], esc(c, 0.7), c)
            elif pele == 'pelo':
                fio = 0.5 + 0.5 * np.sin(y * 7 + s * 3 + n * 6)
                c = rampa(fio, [(0, esc(mix(tema, C('#6a5a48'), 0.5), 0.6)), (1, mix(tema, C('#c8b8a0'), 0.4))])
            else:
                c = rampa(0.5 + 0.5 * np.sin(y * 1.2 + n * 8), [(0, '#3a2414'), (0.5, '#6a4426'), (1, '#8a5e36')])
                if var == 'palido':
                    c = clr(c, 0.45)
            return c * (0.85 + 0.3 * n)[..., None], 1
        if pele in ('madeira',):
            veio = 0.5 + 0.5 * np.sin(y * 1.6 + n * 7)
            base = [(0, '#4a2c16'), (0.5, '#7a4e2a'), (1, '#a06a3c')] if var != 'palido' else [(0, '#8c8a7e'), (0.5, '#c4c0b0'), (1, '#e6e2d4')]
            c = rampa(veio, base)
            if var != 'palido':
                c = mix(c, tema, 0.15)
            # pintura tribal
            faixa = (np.abs(((y * 0.5) % 1) - 0.5) < 0.08) & plana
            c = np.where(faixa[..., None], acab * np.ones_like(c), c)
            return c, 1
        if pele in ('osso', 'dente', 'obsidiana_dente', 'coral_dente', 'concha'):
            if pele == 'obsidiana_dente':
                c = rampa(n, [(0, '#0a0810'), (0.6, '#2a2238'), (1, '#6a5a88')])
            elif pele == 'coral_dente':
                c = rampa(n, [(0, esc(tema, 0.6)), (0.6, tema), (1, clr(tema, 0.6))])
            elif pele == 'concha':
                c = rampa(0.5 + 0.5 * np.sin(s * 9 + y), [(0, '#c89a8a'), (0.5, '#f0d4c4'), (1, '#fff4ec')])
                c = mix(c, tema, 0.2)
            elif var == 'wither':
                c = rampa(n, [(0, '#121214'), (0.5, '#2c2c30'), (1, '#55555c')])
            else:
                c = rampa(n, [(0, '#a89c84'), (0.5, '#dcd2bc'), (1, '#f4eee0')])
                c = c * (1 - 0.45 * ss(0.8, 0.95, ridged(x * 3, y * 3, z * 3, 2, 1, sem + 1)))[..., None]
            return c, 1
        if pele in ('quitina', 'quitina_lamina'):
            c = rampa(n, [(0, esc(tema, 0.2)), (0.55, esc(tema, 0.55)), (1, clr(tema, 0.15))])
            if plana:
                c = mix(c, clr(c, 0.6), (np.minimum(s, 1 - s) < 0.1) * 0.6)
                c = mix(c, clr(c, 0.25), (np.abs(((y * 0.9) % 1) - 0.5) > 0.44) * 0.5)
            return c, 1
        if pele == 'sangue':
            return rampa(n, [(0, '#3a0408'), (1, '#9a1018')]), np.where(fbm(x * 3, y * 0.6, z * 3, 2, 1, sem + 4) > 0.45, 1.0, 0.0)
        if pele == 'gelo':
            c = rampa(n, [(0, '#4a8ad0'), (0.5, '#a8dcff'), (1, '#f4fcff')])
            c = mix(c, C('#ffffff'), ss(0.82, 0.95, ridged(x * 3, y * 3, z * 3, 2, 1, sem + 1)) * 0.8)
            return c, 1
        if pele == 'vazio':
            c = rampa(n, [(0, '#07070b'), (0.5, '#17142a'), (1, '#2c2648')])
            estrela = hash2(np.floor(s * 10 + x * 3).astype(np.int64), np.floor(y * 4).astype(np.int64), sem + 4) > 0.93
            c = np.where(estrela[..., None], clr(bri, 0.6) * np.ones_like(c), c)
            return c, 1
        if pele == 'sculk':
            c = rampa(n, [(0, '#05181c'), (0.5, '#0f3a40'), (1, '#1f6a72')])
            veia = ss(0.9, 0.97, ridged(x * 2.2, y * 2.2, z * 2.2, 2, 1, sem + 2))
            c = mix(c, C('#5ff0ff'), veia * 0.9)
            return c, 1
        if pele == 'energia':
            nucleo = np.exp(-((s - 0.5) / 0.28) ** 2)
            c = mix(bri, np.ones(3), nucleo * 0.75 + 0.1 * np.sin(y * 4))
            if var == 'verde':
                c = mix(C('#5aff6a'), np.ones(3), nucleo * 0.7)
            return c, 1
        if pele == 'metal_hi':
            c = rampa(n, [(0, '#2a2e36'), (0.5, '#7a828e'), (1, '#d0d6de')])
            c = np.where(((np.floor(y * 2) % 3) == 0)[..., None], esc(c, 0.6), c)
            return c, 1
        if pele in ('magma', 'rocha'):
            c = rampa(n, [(0, '#0e0b0a'), (0.5, '#262022'), (1, '#4a3e3a')]) if var != 'roxo' else rampa(n, [(0, '#0a0610'), (0.5, '#221838'), (1, '#4a3a68')])
            if pele == 'magma':
                fenda = ss(0.8, 0.94, ridged(x * 2, y * 2, z * 2, 3, 1, sem + 1))
                lava = C('#ff5a10') if var != 'roxo' else C('#c050ff')
                c = mix(c, lava, fenda)
            return c, 1
        if pele == 'lava':
            c = rampa(fbm(x * 4, y * 4, z * 4, 2, 1, sem + 2), [(0, '#ff3a00'), (1, '#ffe07a')])
            if var == 'roxo':
                c = rampa(fbm(x * 4, y * 4, z * 4, 2, 1, sem + 2), [(0, '#9a20ff'), (1, '#f0b0ff')])
            return c, 1
        if pele in ('coral', 'gosma'):
            if pele == 'gosma':
                c = rampa(n, [(0, '#3a2a18'), (0.5, '#6a4a2a'), (1, '#9a7a4a')])
                pinga = ss(0.6, 0.85, fbm(x * 2, y * 0.5, z * 2, 2, 1, sem + 5))
                c = mix(c, C('#7aff4a'), pinga * 0.85)
            else:
                c = rampa(n, [(0, esc(tema, 0.45)), (0.5, tema), (1, clr(tema, 0.55))])
                poro = hash2(np.floor(s * 6 + x * 2).astype(np.int64), np.floor(y * 3).astype(np.int64), sem + 6) > 0.8
                c = np.where(poro[..., None], esc(c, 0.5), c)
            return c, 1
        if pele == 'pena':
            haste = np.abs(s - 0.5) < 0.07
            barba = ((s * 7 + y * 2.0) % 1) < 0.55
            base = clr(mix(tema, np.ones(3), 0.55), 0.1)
            if var == 'ilhas':
                base = mix(base, C('#9ad8ff'), 0.35)
            c = np.where(barba[..., None], base * 0.92, base * np.ones(s.shape + (3,)))
            c = np.where(haste[..., None], base * 0.7, c)
            if arq == 'macuahuitl':
                c = mix(c, bri, 0.6)
            return c, 1
        if pele in ('cogumelo_chapeu', 'cogumelo_talo', 'esporo'):
            if pele == 'cogumelo_talo':
                c = rampa(n, [(0, '#b8ac94'), (0.5, '#e6dcc4'), (1, '#fff6e4')])
                if var == 'distorcido':
                    c = mix(c, C('#2aa89a'), 0.35)
                return c, 1
            c = rampa(n, [(0, esc(tema, 0.5)), (0.5, tema), (1, clr(tema, 0.4))])
            pinta = hash2(np.floor(s * 5 + x).astype(np.int64), np.floor(tt * 5 + y).astype(np.int64), sem + 8) > 0.72
            c = np.where(pinta[..., None], (C('#f4ecd8') if pele == 'cogumelo_chapeu' else clr(bri, 0.5)) * np.ones_like(c), c)
            return c, 1
        if pele == 'engrenagem':
            c = metal(n, 'cobre' if var != 'relogio' else 'ouro')
            dente = (np.floor(s * 6) % 2 == 0) & ((tt < 0.15) | (tt > 0.85))
            c = np.where(dente[..., None], esc(c, 0.6), c)
            return c, 1
        if pele in ('metal_guarda',):
            return rampa(n, [(0, esc(metal_guarda, 0.5)), (1, clr(metal_guarda, 0.5))]), 1
        return rampa(n, [(0, esc(tema, 0.5)), (1, clr(tema, 0.4))]), 1
    return p


# ------------------------------------------------------------------ Bedrock: arma 3D na mao (attachable)

def _r(v):
    return round(float(v), 4)


def geometria_mao(ident, cubos, W, H):
    """Geometria de item segurado: osso raiz preso ao slot (mao/cabeca), 3 ossos para o giro do 'display' e um osso
    girado com os cubos (a espada toda gira -45 em z). Java -> Bedrock: x espelhado em volta de 8, z - 8."""
    raiz = [{'name': 'corona_arma', 'binding': "c.item_slot == 'head' ? 'head' : q.item_slot_to_bone_name(c.item_slot)", 'pivot': [0, 8, 0]},
            {'name': 'corona_arma_x', 'parent': 'corona_arma', 'pivot': [0, 8, 0]},
            {'name': 'corona_arma_y', 'parent': 'corona_arma_x', 'pivot': [0, 8, 0]},
            {'name': 'corona_arma_z', 'parent': 'corona_arma_y', 'pivot': [0, 8, 0]}]
    grupos = {}
    for c in cubos:
        chave = c.rot if (c.rot and c.rot[1] != 0) else None
        grupos.setdefault(chave, []).append(c)
    ossos = []
    for i, (rot, lista) in enumerate(grupos.items()):
        cubes = []
        for c in lista:
            de, ate = c.de, c.ate
            cube = {'origin': [_r(-ate[0] + 8), _r(de[1]), _r(de[2] - 8)], 'size': [_r(ate[0] - de[0]), _r(ate[1] - de[1]), _r(ate[2] - de[2])], 'uv': {}}
            for f in c.faces():
                x0, y0, x1, y1 = c.uv[f]
                if f in ('up', 'down'):
                    cube['uv'][f] = {'uv': [x1, y1], 'uv_size': [x0 - x1, y0 - y1]}
                else:
                    cube['uv'][f] = {'uv': [x0, y0], 'uv_size': [x1 - x0, y1 - y0]}
            cubes.append(cube)
        osso = {'name': f'corona_arma_g{i}', 'parent': 'corona_arma_z', 'pivot': [0, 8, 0], 'cubes': cubes}
        if rot:
            eixo, ang, orig = rot
            osso['pivot'] = [_r(-orig[0] + 8), _r(orig[1]), _r(orig[2] - 8)]
            osso['rotation'] = [-ang if eixo == 'x' else 0, -ang if eixo == 'y' else 0, ang if eixo == 'z' else 0]
        ossos.append(osso)
    return {'format_version': '1.16.0', 'minecraft:geometry': [{
        'description': {'identifier': ident, 'texture_width': W, 'texture_height': H,
                        'visible_bounds_width': 4, 'visible_bounds_height': 4.5, 'visible_bounds_offset': [0, 0.75, 0]},
        'bones': raiz + ossos}]}


def animacoes_mao(nome, display):
    """Animacoes de 1a/3a pessoa a partir do 'display' do modelo Java (convencao do Geyser para itens 3D)."""
    def d(k):
        return display.get(k, {})

    def xyz(k, mao_esq=False, primeira=False):
        r = d(k).get('rotation', [0, 0, 0])
        t = d(k).get('translation', [0, 0, 0])
        s = d(k).get('scale', [1, 1, 1])
        tx = t[0] if mao_esq else -t[0]
        tz = -t[2] if primeira else t[2]
        return {
            'corona_arma_x': {'rotation': [-r[0], 0, 0], 'position': [tx, t[1], tz], 'scale': s},
            'corona_arma_y': {'rotation': [0, -r[1], 0]},
            'corona_arma_z': {'rotation': [0, 0, r[2]]},
        }
    an = {}
    for nome_an, k, esq, prim in (('thirdperson_main_hand', 'thirdperson_righthand', False, False),
                                  ('thirdperson_off_hand', 'thirdperson_lefthand', True, False),
                                  ('firstperson_main_hand', 'firstperson_righthand', False, True),
                                  ('firstperson_off_hand', 'firstperson_lefthand', True, True)):
        b = xyz(k, esq, prim)
        if prim:
            b['corona_arma'] = {'rotation': [90, 60, -40], 'position': [4, 10, 4], 'scale': 1.5}
        else:
            b['corona_arma'] = {'rotation': [90, 0, 0], 'position': [0, 13, -3]}
        an[f'animation.corona.{nome}.{nome_an}'] = {'loop': True, 'bones': b}
    hd = d('head')
    r = hd.get('rotation', [0, 0, 0])
    t = hd.get('translation', [0, 0, 0])
    s = hd.get('scale', [1, 1, 1])
    an[f'animation.corona.{nome}.head'] = {'loop': True, 'bones': {
        'corona_arma_x': {'rotation': [-r[0], 0, 0], 'position': [-t[0] * 0.625, t[1] * 0.625, t[2] * 0.625], 'scale': [v * 0.625 for v in s]},
        'corona_arma_y': {'rotation': [0, -r[1], 0]}, 'corona_arma_z': {'rotation': [0, 0, r[2]]},
        'corona_arma': {'position': [0, 19.9, 0]}}}
    an[f'animation.corona.{nome}.esconder'] = {'loop': True, 'override_previous_animation': True, 'bones': {'corona_arma': {'scale': 0}}}
    return {'format_version': '1.8.0', 'animations': an}


def attachable_mao(ident, textura, geo, nome_an):
    return {'format_version': '1.10.0', 'minecraft:attachable': {'description': {
        'identifier': ident,
        'materials': {'default': 'entity_emissive_alpha', 'enchanted': 'entity_emissive_alpha'},
        'textures': {'default': textura, 'enchanted': 'textures/misc/enchanted_item_glint'},
        'geometry': {'default': geo},
        'scripts': {'pre_animation': ["v.main_hand = c.item_slot == 'main_hand';", "v.off_hand = c.item_slot == 'off_hand';", "v.head = c.item_slot == 'head';"],
                    'animate': [{'tp_main': 'v.main_hand && !c.is_first_person'}, {'tp_off': 'v.off_hand && !c.is_first_person'},
                                {'tp_head': 'v.head && !c.is_first_person'}, {'fp_main': 'v.main_hand && c.is_first_person'},
                                {'fp_off': 'v.off_hand && c.is_first_person'}, {'fp_head': 'c.is_first_person && v.head'}]},
        'animations': {'tp_main': f'animation.corona.{nome_an}.thirdperson_main_hand', 'tp_off': f'animation.corona.{nome_an}.thirdperson_off_hand',
                       'tp_head': f'animation.corona.{nome_an}.head', 'fp_main': f'animation.corona.{nome_an}.firstperson_main_hand',
                       'fp_off': f'animation.corona.{nome_an}.firstperson_off_hand', 'fp_head': f'animation.corona.{nome_an}.esconder'},
        'render_controllers': ['controller.render.item_default']}}}


# ------------------------------------------------------------------ geracao

def gerar_tema(id_, t, saida):
    tema = id_.lower()
    arq, var = ARMA.get(id_, ('longa', ''))
    sem = sum(ord(ch) for ch in id_)
    cs = construir(arq, var, sem)
    altura = kit.arrumar_uv(cs, LARGURA, ESCALA)
    H = 16
    while H < altura:
        H *= 2
    atlas = kit.pintar_atlas(cs, LARGURA, H, pintor(id_, t, arq, var), ESCALA)
    J = os.path.join(saida, 'java', 'assets', 'corona')
    B = os.path.join(saida, 'bedrock')
    os.makedirs(os.path.join(J, 'textures', 'item', 'reliquias'), exist_ok=True)
    kit.salvar_png_leve(atlas, os.path.join(J, 'textures', 'item', 'reliquias', f'espada_{tema}.png'))
    modelo = kit.modelo_java(cs, f'corona:item/reliquias/espada_{tema}', LARGURA, H, display=DISPLAY)
    modelo['credit'] = 'Corona Server - arma original'
    kit.salvar_json(os.path.join(J, 'models', 'item', 'reliquias', f'espada_{tema}.json'), modelo)
    # icone 2D do Bedrock (inventario)
    pv = kit.previa(cs, atlas, 0, 0, escala=10)
    arr = np.array(pv.convert('RGBA'))
    fundo = (np.abs(arr[..., 0].astype(int) - 28) < 3) & (np.abs(arr[..., 1].astype(int) - 30) < 3) & (np.abs(arr[..., 2].astype(int) - 36) < 3)
    arr[fundo, 3] = 0
    ic = Image.fromarray(arr, 'RGBA')
    ic.thumbnail((32, 32), Image.LANCZOS)
    quad = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    quad.paste(ic, ((32 - ic.width) // 2, (32 - ic.height) // 2))
    os.makedirs(os.path.join(B, 'textures', 'items', 'corona', 'v2'), exist_ok=True)
    quad.save(os.path.join(B, 'textures', 'items', 'corona', 'v2', f'corona_espada_{tema}.png'))
    # 3D na mao (Bedrock)
    os.makedirs(os.path.join(B, 'textures', 'item_3d'), exist_ok=True)
    # Bedrock: as partes com luz propria (emite) brilham no escuro (entity_emissive_alpha: alpha baixo = brilho)
    tb = np.array(Image.open(os.path.join(J, 'textures', 'item', 'reliquias', f'espada_{tema}.png')).convert('RGBA'))
    tb[..., 3] = np.where(tb[..., 3] >= 128, 255, 0)
    for c in cs:
        if getattr(c, 'emite', 0):
            a = int(max(60, 255 - c.emite * 13))
            for f, (x0, y0, x1, y1) in c.uv.items():
                reg = tb[y0:y1, x0:x1, 3]
                reg[reg > 0] = a
    Image.fromarray(tb, 'RGBA').save(os.path.join(B, 'textures', 'item_3d', f'espada_{tema}.png'), optimize=True)
    geo = f'geometry.corona.espada_{tema}'
    kit.salvar_json(os.path.join(B, 'models', 'entity', 'armas', f'espada_{tema}.geo.json'), geometria_mao(geo, cs, LARGURA, H))
    for mat in MATERIAIS:
        kit.salvar_json(os.path.join(B, 'attachables', f'espada_{tema}_{mat}.json'),
                        attachable_mao(f'corona:espada_{tema}_{mat}', f'textures/item_3d/espada_{tema}', geo, 'espada'))
    return arq, var, pv


def gerar(saida, so=None):
    feitos = []
    for id_, t in TEMAS.items():
        if so and id_ not in so:
            continue
        arq, var, pv = gerar_tema(id_, t, saida)
        feitos.append((id_, arq, var, pv))
    kit.salvar_json(os.path.join(saida, 'bedrock', 'animations', 'corona_armas.animation.json'), animacoes_mao('espada', DISPLAY))
    return feitos


if __name__ == '__main__':
    saida = sys.argv[1]
    so = [x.upper() for x in sys.argv[2:]] or None
    feitos = gerar(saida, so)
    # folha de previas
    pvs = [f[3] for f in feitos]
    if pvs:
        cols = 10
        w = max(p.width for p in pvs)
        h = max(p.height for p in pvs)
        folha = Image.new('RGBA', (cols * w, ((len(pvs) + cols - 1) // cols) * h), (28, 30, 36, 255))
        for i, p in enumerate(pvs):
            folha.alpha_composite(p, ((i % cols) * w + (w - p.width) // 2, (i // cols) * h + (h - p.height) // 2))
        folha.save(os.path.join(saida, 'previa_armas.png'))
    print(len(feitos), 'armas em', saida)
