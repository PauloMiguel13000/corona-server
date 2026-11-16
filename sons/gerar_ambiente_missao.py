"""Som ambiente da Missao (16/11, Paulo: "vamos por um som ambiente para o jogador ouvir durante o jogo").

Tudo sintetizado aqui (original do Corona Server):
  amb_cidade.ogg              fundo em loop (40 s): vento, zumbido da cidade ao longe, cigarras e passarinhos
  amb_tiroteio1..3.ogg        tiroteio distante (rajadas com eco entre os predios)
  amb_explosao1..2.ogg        explosao longe (estrondo grave que rola)
  amb_helicoptero.ogg         helicoptero passando (pas batendo, chega e vai embora)
  amb_radio1..2.ogg           radio da equipe: chiado, bipes e o "clique" de fechar (sem voz)
  amb_sirene.ogg              sirene ao longe
Rodar: python3 gerar_ambiente_missao.py  -> /tmp/claude-0/sons/tiro/amb_*.ogg
"""
import os
import numpy as np
from scipy import signal
import gerar_trilhas_v3 as G

G.OUT = '/tmp/claude-0/sons/tiro'
os.makedirs(G.OUT, exist_ok=True)
SR = G.SR
T, Z, lp, hp, bp, env, por = G.T, G.Z, G.lp, G.hp, G.bp, G.env, G.por
rng = np.random.default_rng(1618)


def ruido(n):
    return rng.standard_normal(n)


def filtro_variavel(x, cortes, tipo='low', blk=512):
    y = np.zeros_like(x)
    for i in range(0, len(x), blk):
        fc = float(cortes[min(i, len(cortes) - 1)])
        seg = x[max(0, i - 128):i + blk]
        z = lp(seg, fc, 2) if tipo == 'low' else hp(seg, fc, 2)
        y[i:i + blk] = z[-len(x[i:i + blk]):]
    return y


def eco_predios(x, atrasos=(.11, .23, .41, .63), ganho=.45):
    y = x.copy()
    for k, a in enumerate(atrasos):
        n = int(a * SR)
        z = np.concatenate([np.zeros(n), lp(x, 2500 - 400 * k, 2)])[:len(x)]
        y += z * ganho * (.75 ** k)
    return y


# ------------------------------------------------------------------ fundo em loop
def cidade(D=40.0):
    n = int((D + 3) * SR)
    tt = np.arange(n) / SR
    vento = lp(ruido(n), 500, 2) * (.55 + .45 * np.sin(2 * np.pi * tt / 13.3) * np.sin(2 * np.pi * tt / 7.1 + 1))
    vento = vento + .4 * bp(ruido(n), 900, 2200) * (.3 + .3 * np.sin(2 * np.pi * tt / 9.7) ** 2)
    zumbido = lp(ruido(n), 140, 2) * 1.6 + .25 * np.sin(2 * np.pi * 60 * tt) * (.5 + .5 * np.sin(2 * np.pi * tt / 11))
    cig_env = (.5 + .5 * np.sin(2 * np.pi * 38 * tt)) ** 3 * (.4 + .6 * (np.sin(2 * np.pi * tt / 17) > -.2))
    cigarras = bp(ruido(n), 4200, 6200) * cig_env * .35
    x = vento * .9 + zumbido * .55 + cigarras
    # passarinhos (assobios curtos que sobem e descem)
    for _ in range(14):
        q = rng.uniform(0, D - 2)
        f0 = rng.uniform(2400, 3600)
        for k in range(rng.integers(2, 5)):
            d = rng.uniform(.06, .14)
            t2 = T(d)
            f = f0 * (1 + .25 * np.sin(np.pi * t2 / d) * (1 if k % 2 else -1))
            ch = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.sin(np.pi * t2 / d) ** 2 * .22
            por(x, ch, q + k * (d + .05))
    # carro/moto passando bem longe, de vez em quando
    for q in (6.0, 23.0, 34.0):
        d = 5.0
        t2 = T(d)
        motor = lp(signal.sawtooth(2 * np.pi * (55 + 10 * t2 / d) * t2) + .4 * ruido(len(t2)), 380, 2)
        por(x, motor * np.sin(np.pi * t2 / d) ** 2 * .35, q)
    x = G.reverb(x, 1.8, .25)
    return x, D


# ------------------------------------------------------------------ eventos soltos
def disparo(forte=1.0):
    tt = T(.35)
    estalo = hp(ruido(len(tt)), 900, 2) * np.exp(-tt * 60)
    corpo = lp(ruido(len(tt)), 700, 2) * np.exp(-tt * 18) * 1.4
    return (estalo * .5 + corpo) * forte


def tiroteio(seed):
    r = np.random.default_rng(seed)
    D = 4.0
    x = Z(D + 1)
    q = .1
    while q < D - .6:
        rajada = r.integers(1, 7)
        cad = r.uniform(.07, .12)
        for k in range(rajada):
            por(x, disparo(r.uniform(.6, 1.0)), q + k * cad)
        q += rajada * cad + r.uniform(.25, .9)
    x = eco_predios(lp(x, 2600, 2), ganho=.55)
    return x[:int((D + .8) * SR)] * env(D + .8, .005, .1, 1, .6)


def explosao(seed):
    r = np.random.default_rng(seed)
    D = 5.0
    tt = T(D)
    estouro = lp(ruido(len(tt)), 900, 2) * np.exp(-tt * 9) * 1.6
    rolo = lp(ruido(len(tt)), 160, 2) * np.exp(-tt * 1.1) * (1 + .5 * np.sin(2 * np.pi * r.uniform(2, 4) * tt)) * 2.2
    sub = np.sin(2 * np.pi * np.cumsum(30 + 40 * np.exp(-tt * 5)) / SR) * np.exp(-tt * 2) * 1.2
    destrocos = Z(D)
    for _ in range(int(r.integers(6, 14))):
        por(destrocos, hp(ruido(int(.05 * SR)), 1500, 2) * np.exp(-np.arange(int(.05 * SR)) / SR * 70) * .25, r.uniform(.4, 2.2))
    x = estouro + rolo + sub + destrocos
    return eco_predios(x, (.18, .37, .7), .35) * env(D, .003, .2, 1, 1.5)


def helicoptero():
    D = 12.0
    tt = T(D)
    perto = np.sin(np.pi * tt / D) ** 2                                   # chega e vai embora
    f_pas = 11.5 * (1 + .04 * np.cos(np.pi * tt / D))                        # efeito doppler leve
    fase = 2 * np.pi * np.cumsum(f_pas) / SR
    batida = (.5 + .5 * np.cos(fase)) ** 6                                    # "tup tup tup" das pas
    corpo = lp(ruido(len(tt)), 300, 2) * batida * 3
    turbina = bp(ruido(len(tt)), 1800, 3200) * .25 + .15 * np.sin(2 * np.pi * 1450 * (1 + .03 * np.cos(np.pi * tt / D)) * tt)
    cortes = 500 + 2500 * perto
    x = filtro_variavel(corpo + turbina * perto, cortes)
    return G.reverb(x * (.15 + .85 * perto), 1.2, .2)


def radio(seed):
    r = np.random.default_rng(seed)
    D = 2.6
    x = Z(D)
    clique = hp(ruido(int(.02 * SR)), 2000, 2) * np.exp(-np.arange(int(.02 * SR)) / SR * 200)
    por(x, clique, .02, .9)
    chiado = bp(ruido(int(1.9 * SR)), 800, 3800) * .22 * (0.6 + .4 * np.sin(2 * np.pi * 3 * T(1.9)) ** 2)
    por(x, chiado, .08)
    q = .25
    for _ in range(int(r.integers(3, 7))):                                     # bipes de codigo (sem voz)
        d = r.choice([.06, .06, .16])
        tb = T(d)
        por(x, np.sin(2 * np.pi * 1250 * tb) * env(d, .004, .01, .9, .01) * .35, q)
        q += d + r.uniform(.05, .12)
    por(x, clique * .8, 2.05)
    por(x, bp(ruido(int(.3 * SR)), 1000, 4000) * np.exp(-np.arange(int(.3 * SR)) / SR * 14) * .35, 2.08)
    return bp(x, 300, 4200, 2)                                                 # som de radio (sem graves)


def sirene():
    D = 8.0
    tt = T(D)
    f = 650 + 350 * (.5 - .5 * np.cos(2 * np.pi * tt / 3.2))
    x = np.sin(2 * np.pi * np.cumsum(f) / SR) + .35 * np.sin(2 * np.pi * np.cumsum(2 * f) / SR)
    x = lp(x, 1600, 2) * np.sin(np.pi * tt / D) ** 1.5 * .5
    return eco_predios(x, (.2, .45), .4)


def salvar(nome, x, vol=.8):
    G.salvar(nome, x, vol=vol, q='1')


if __name__ == '__main__':
    x, D = cidade()
    salvar('amb_cidade', G.emendar(x, D), .55)
    for i in range(3):
        salvar(f'amb_tiroteio{i + 1}', tiroteio(10 + i), .8)
    for i in range(2):
        salvar(f'amb_explosao{i + 1}', explosao(20 + i), .9)
    salvar('amb_helicoptero', helicoptero(), .8)
    for i in range(2):
        salvar(f'amb_radio{i + 1}', radio(30 + i), .6)
    salvar('amb_sirene', sirene(), .6)
