"""Musicas do NIVEL 6 (20/11, Paulo: "faltou as musicas novas dos bosses novos, alem da de luta").

As 10 masmorras do nivel 6 (Terras Devastadas) ganham uma musica propria cada, com 3 versoes que emendam em loop:
  n6_<tema>.ogg        explorando
  n6_<tema>_luta.ogg   com monstro por perto
  chefe_n6_<tema>.ogg  luta do chefe
A melodia caracteristica do nivel 6 e NOVA (M6, "Ancestral": La menor harmonico, sobe ate o Mi maior e cai), e cada
masmorra arranja do seu jeito (andamento, tom, instrumentos, levada, ambiente e um efeito so dela).
Tudo sintetizado aqui (numpy/scipy/ffmpeg), nada de terceiros. Rodar: python3 gerar_nivel6.py [tema ...]
"""
import os
import sys

import numpy as np
from scipy import signal

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gerar_chefes as gc  # noqa: E402  (importa o gerar_trilhas_v3 e deixa 32 kHz)
import gerar_trilhas_v3 as g  # noqa: E402

T, Z, por, lp, hp, bp, env, mf = g.T, g.Z, g.por, g.lp, g.hp, g.bp, g.env, g.mf

# ------------------------------------------------------------------ a melodia nova do nivel 6
g.ORIG['M6'] = (
    [('A4', 1), ('E5', 1), ('D5', .5), ('C5', .5), ('B4', 1), ('C5', .5), ('D5', .5), ('E5', 2),
     ('G#4', 1), ('B4', .5), ('D5', .5), ('C5', 1), ('B4', 1), ('A4', 2), (None, 2)],
    [('A2', ['A3', 'C4', 'E4']), ('F2', ['F3', 'A3', 'C4']), ('D2', ['D3', 'F3', 'A3']), ('E2', ['E3', 'G#3', 'B3'])])


# ------------------------------------------------------------------ efeitos proprios de cada masmorra
def ex_brasas(x, b, barra, comps, D, luta):
    q = .2
    while q < D:
        tt = T(.05)
        por(x, hp(g.R.g.standard_normal(len(tt)), 3000) * np.exp(-tt * 80) * .5, q, .35)
        q += .08 + g.R.g.random() * .5


def ex_correntes(x, b, barra, comps, D, luta):
    for c in range(1, comps, 3):
        for k in range(3):
            tt = T(.18)
            y = sum(np.sin(2 * np.pi * f * tt) for f in (1800, 2650, 3900)) * np.exp(-tt * 22) * .15
            por(x, y, c * barra + 2 * b + k * .11, .5)


def ex_tesla(x, b, barra, comps, D, luta):
    for c in range(0, comps, 2):
        tt = T(.6)
        y = signal.square(2 * np.pi * 60 * tt) * (g.R.g.random(len(tt)) > .7) * np.exp(-tt * 4) * .2
        por(x, bp(y, 800, 5000), c * barra + 3 * b, .5)


def ex_sino_sangue(x, b, barra, comps, D, luta):
    for c in range(0, comps, 4):
        por(x, g.sino(mf(49), 4) * .8, c * barra, .4)


def ex_ressonancia(x, b, barra, comps, D, luta):
    for c in range(0, comps, 2):
        tt = T(barra * 2)
        y = np.sin(2 * np.pi * 55 * tt) * (0.5 + 0.5 * np.sin(2 * np.pi * 1.5 * tt)) * env(barra * 2, .8, .2, .7, 1) * .3
        por(x, y, c * barra, .6)


def ex_esporos(x, b, barra, comps, D, luta):
    q = .5
    while q < D:
        tt = T(.4)
        f = 180 + g.R.g.random() * 120
        por(x, lp(np.sin(2 * np.pi * f * (1 - .5 * tt) * tt) * np.exp(-tt * 6), 900) * .4, q, .4)
        q += .6 + g.R.g.random() * 1.6


def ex_cristal(x, b, barra, comps, D, luta):
    for c in range(comps):
        if c % 2 == 0:
            for k, s in enumerate((88, 95, 91, 100)):
                por(x, g.fm(mf(s), 1.2, 1.1, 3.5) * .5, c * barra + k * b * .75, .12)


def ex_baleia(x, b, barra, comps, D, luta):
    for c in range(2, comps, 6):
        tt = T(3.0)
        f = 140 + 90 * np.sin(np.pi * tt / 3.0) ** 2
        y = bp(np.sin(2 * np.pi * np.cumsum(f) / g.SR), 80, 900) * env(3.0, .8, .3, .7, 1.2) * .5
        por(x, y, c * barra, .6)


def ex_maquinas(x, b, barra, comps, D, luta):
    for c in range(comps):
        por(x, g.bigorna() * .7, c * barra, .4)
        tt = T(b)
        por(x, lp(g.R.g.standard_normal(len(tt)), 400) * env(b, .05, .2, .5, .3) * .4, c * barra + 2 * b, .5)


def ex_fenda(x, b, barra, comps, D, luta):
    for c in range(1, comps, 4):
        n = int(barra * g.SR)
        tt = np.arange(n) / g.SR
        k = (1 - tt / tt[-1]) ** 2
        y = np.sin(2 * np.pi * np.cumsum(60 + 1600 * k) / g.SR) * (1 - k) * .3   # zumbido ao contrario
        por(x, y, c * barra, .55)


def luta_pesada():
    return [(g.BUM(), 0, 1), (g.BUM(), .5, .6), (g.CX(170), 1, .9), (g.BUM(), 2, .9), (g.BUM(), 2.75, .5), (g.CX(170), 3, .9)] + [(g.HH(), k / 2, .25) for k in range(8)]


def base(**kw):
    d = dict(beats=4, bateria=[(g.BUM(.7), 0, .6), (g.CX(), 2, .4)], luta=g.luta44(), extras=[], sala=1.8, mixRev=.26)
    d.update(kw)
    return d


TEMAS = {
    # Cidadela das Cinzas (Imperador): marcha de forja, cordas staccato e metais rasgados, brasas estalando
    'n6_cinzas': (base(bateria=[(g.TAI(), 0, .8), (g.BUM(), 1.5, .5), (g.TAI(), 2, .7), (g.CX(160), 3, .5)], luta=luta_pesada(),
                       extras=[ex_brasas, g.ex_bigorna], sala=1.6),
                  ('M6', 138, -2, (g.cordas_st, 'staccato'), lambda f, d: lp(np.tanh(3.5 * g.metais(f, d, 2600)), 3200) * .6, 0, 'coro', 'normal')),
    # Catacumba Eterna (Lich): valsa fantasma de caixinha e orgao, correntes balancando
    'n6_catacumba': (base(bateria=[(g.TIM(55), 0, .35), (g.MAD(600), 2, .25)], extras=[ex_correntes, g.ex_sinos], sala=3.0, mixRev=.38),
                     ('M6', 84, 1, (g.caixinha, 'arpejo'), lambda f, d: g.orgao([f], d, 2400), 0, 'orgao', 'drone')),
    # Pinaculo da Tempestade: arpejo eletrico rapido, violino, faiscas de tesla e trovoes
    'n6_pinaculo': (base(bateria=[(g.BUM(), 0, .8), (g.CX(), 1, .6), (g.BUM(), 2.5, .6), (g.CX(), 3, .6)] + [(g.HH(), k / 2, .25) for k in range(8)],
                         luta=g.luta44([(g.BUM(), .75, .6), (g.BUM(), 3.5, .6)]), extras=[ex_tesla, g.ex_trovao], sala=1.8),
                    ('M6', 156, 2, (lambda f, d: g.quadrado(f, d * .5, .3), 'arpejo'), g.violino, 0, None, 'normal')),
    # Fortaleza Rubra (Conde Rubro): cravo e cordas de baile gotico, coracao batendo, sino grave
    'n6_rubra': (base(bateria=[(g.BUM(.7), 0, .6), (g.CX(), 2, .45), (g.MAD(1400), 3.5, .25)], extras=[g.ex_coracao, ex_sino_sangue], sala=2.2, mixRev=.3),
                 ('M6', 110, -1, (g.cravo, 'arpejo'), g.violino, 0, 'cordas', 'normal')),
    # Nucleo do Eco (Colosso do Eco): pulsos graves de sculk, sinos abafados, ressonancia
    'n6_eco': (base(bateria=[], luta=[(g.TAI(), 0, 1), (g.TAI(), 2.5, .8), (g.TIM(50), 3, .7)], extras=[g.ex_eco_warden, ex_ressonancia], sala=4.0, mixRev=.48),
               ('M6', 72, -5, (lambda f, d: lp(g.sino(f, d), 1700), 'arpejo'), lambda f, d: lp(g.fm(f, d, 1.4, 2), 1600), 0, 'pad', 'drone')),
    # Jardim Putrido (Mae Putrida): fagote gosmento, pizzicato em bloco, esporos estourando
    'n6_putrido': (base(bateria=[(g.BUM(.7), 0, .6), (g.MAD(500), 1.5, .5), (g.BUM(.7), 2.5, .5), (g.MAD(700), 3.5, .5)], extras=[ex_esporos, g.ex_bolhas], sala=1.7),
                   ('M6', 94, 3, (lambda f, d: g.pluck(f, d, 1400, .99), 'bloco'), g.oboe, -1, 'pad', 'normal')),
    # Santuario Glaciar (Rainha do Inverno): celesta e sinos de gelo, cristais tilintando, eco
    'n6_glaciar': (base(bateria=[(g.CHO(), 1, .2), (g.CHO(), 3, .2)], luta=[(g.BUM(), 0, 1), (g.CX(300), 1, .6), (g.BUM(), 2, .8), (g.CX(300), 3, .6)] + [(g.HH(True), 2.5, .3)],
                        extras=[ex_cristal, g.ex_vento], sala=3.0, mixRev=.38, eco=True),
                   ('M6', 90, 4, (lambda f, d: g.caixinha(f * 2, d), 'arpejo'), lambda f, d: g.sino(f, d), 0, 'pad', 'drone')),
    # Fossa Abissal (Leviata): submerso, sinos FM, canto de baleia, sonar e bolhas
    'n6_abissal': (base(bateria=[(g.TIM(50), 0, .4)], luta=[(g.TIM(50), 0, 1), (g.TAI(), 2, .8), (g.TIM(65), 3, .6)] + [(g.MAD(400), k, .3) for k in range(4)],
                        extras=[ex_baleia, g.ex_sonar, g.ex_bolhas], sala=3.6, mixRev=.45),
                   ('M6', 76, -3, (lambda f, d: lp(g.fm(f, d, 1.2, 2), 1500), 'arpejo'), lambda f, d: lp(g.fm(f, d, 1.6, 2), 1400), 0, 'pad', 'drone')),
    # Forja dos Titas (Tita de Ferro): metais e tuba, tambores de guerra, maquinas batendo
    'n6_titas': (base(bateria=[(g.TAI(), 0, .9), (g.TAI(), 1.5, .5), (g.TAI(), 2, .7), (g.TOM(90), 3, .6)],
                      luta=[(g.TAI(), 0, 1), (g.TAI(), .5, .6), (g.TAI(), 1, .8), (g.CX(), 1, .7), (g.TAI(), 2, 1), (g.CX(), 3, .8), (g.TAI(), 3.5, .7)] + [(g.HH(), k / 2, .25) for k in range(8)],
                      extras=[ex_maquinas], sala=2.2, mixRev=.3),
                 ('M6', 106, -4, (lambda f, d: g.metais(f, d * .8, 1400), 'bloco'), g.metais, 0, 'coro', 'tuba')),
    # Trono do Caos (Arauto do Caos): espacial e torto, arpejo FM, zumbidos ao contrario, eco
    'n6_caos': (base(bateria=[(g.BUM(.6), 0, .5), (g.HH(True), 2, .2)], luta=[(g.BUM(), 0, 1), (g.CX(260), 1, .7), (g.BUM(), 2.5, .8), (g.CX(260), 3, .7)] + [(g.HH(), k / 4, .18) for k in range(16)],
                     extras=[ex_fenda, g.ex_brilhos], sala=3.0, mixRev=.35, eco=True),
                ('M6', 126, 6, (lambda f, d: g.fm(f, d, 1.2, 3.01) * .6, 'arpejo'), lambda f, d: g.quadrado(f, d, .12), 0, 'pad', 'drone')),
}

for nome, (cfg, var) in TEMAS.items():
    g.TEMAS[nome] = cfg
    g.VAR[nome] = var


if __name__ == '__main__':
    quais = ['n6_' + q.lower().removeprefix('n6_') for q in sys.argv[1:]] or list(TEMAS)
    dur = {}
    for nome in quais:
        dur[nome] = g.salvar(nome, g.variacao(nome, False))
        dur[nome + '_luta'] = g.salvar(nome + '_luta', g.variacao(nome, True), q='1')
        dur['chefe_' + nome] = g.salvar('chefe_' + nome, gc.faixa(nome), .88, q='1')
    with open(os.path.join(g.OUT, 'duracoes_n6.txt'), 'w') as f:
        for k, v in dur.items():
            f.write(f'{k} {v:.2f}\n')
