"""Musica de CHEFE para cada tema de masmorra (14/11, Paulo: "eu quero pros outros bosses tambem").

Mesma receita da luta do Herobrine / chefe geral (gerar_herobrine.py), mas cada tema usa a SUA musica original
(melodia e acordes do tema, gerar_trilhas_v3.ORIG/VAR) e os instrumentos dele, em versao de luta de chefe:
introducao curta, A (tema no instrumento do lugar + metais), B (tema uma oitava acima nos violinos + coro),
virada (raio/energia), C (tudo junto). Mono 32 kHz q1 (para o pacote nao pesar), sai como chefe_<tema>.ogg.
"""
import numpy as np, subprocess, os, wave, sys
from scipy import signal
import gerar_trilhas_v3 as g
import gerar_lobby as gl

SR = 32000
g.SR = SR
OUT = '/tmp/claude-0/sons/v3'

def faixa(tema):
    rng = np.random.default_rng(sum(map(ord, tema)) * 31)
    g.R.g = rng
    mus, bpm, tr, (instAc, estAc), lead, oit, fundo, tipoBaixo = g.VAR[tema]
    cfg = g.TEMAS[tema]
    mel, prog = g.ORIG[mus]
    BPM = min(168, bpm + 26); b = 60 / BPM; BAR = 4 * b
    INTRO, A, B, PAUSA, C = 2, 8, 8, 1, 8
    TOTAL = INTRO + A + B + PAUSA + C; D = TOTAL * BAR
    x = g.Z(D + 5); bat = g.Z(D + 5)
    por = g.por
    hz = g.hz
    for c in range(TOTAL):
        t0 = c * BAR; nb, tri = prog[c % 4]
        fr = [hz(n, tr) for n in tri]; fb = hz(nb, tr)
        sec = 'intro' if c < INTRO else 'A' if c < INTRO + A else 'B' if c < INTRO + A + B else 'pausa' if c < INTRO + A + B + PAUSA else 'C'
        por(x, g.lp(g.pad([fb, fb * 1.5], BAR + .5, 420, .004), 380) * 1.4, t0, .6)
        if sec == 'intro':
            por(bat, g.timpano(fb * 2 if fb * 2 > 45 else fb * 4, 1.4), t0, .9)
            por(bat, g.timpano(fb * 2 if fb * 2 > 45 else fb * 4, 1.0), t0 + 2 * b, .7)
            if c == INTRO - 1: por(x, g.Z(.01), t0, 0)
            continue
        if sec == 'pausa':
            por(x, g.trovao(2.5), t0, .6)
            tt = g.T(BAR); k = (tt / tt[-1]) ** 3
            por(x, np.sin(2 * np.pi * np.cumsum(150 + 1800 * k) / SR) * k * .4, t0, .7)
            continue
        forte = sec == 'C'
        # acordes do tema (instrumento do lugar) + cordas em colcheia
        for i, f in enumerate(fr): por(x, instAc(f, b * 1.1), t0 + i * b, .25)
        for k in range(8):
            for f in fr[:2]: por(x, g.cordas_st(f * 2, b / 2), t0 + k * b / 2, .2 if k % 2 == 0 else .13)
        if c % 2 == 0: por(x, g.coro(fr, 2 * BAR + .5), t0, .45 if forte else .35)
        # baixo de luta
        for k in range(8):
            f = fb * (2 if k in (3, 6) else 1); tt = g.T(b * .45)
            y = g.lp(.55 * np.sin(2 * np.pi * f * tt) + .65 * signal.sawtooth(2 * np.pi * f * tt), 560) * g.env(b * .45, .004, .05, .6, .05)
            por(x, y, t0 + k * b / 2, .5 if k % 2 == 0 else .32)
        # golpe de metais no 1
        por(x, g.metais(fr[0], b * .7, 2200), t0, .4)
        # bateria de chefe: taikos, caixa, chimbal; pratos no C
        for k, gg in ((0, 1.0), (1.5, .55), (2, .85), (3, .6), (3.5, .45)): por(bat, g.taiko(), t0 + k * b, gg)
        por(bat, g.caixa(180), t0 + b, .5); por(bat, g.caixa(180), t0 + 3 * b, .55)
        for k in range(8): por(bat, g.chimbal(), t0 + k * b / 2, .3 if k % 2 == 0 else .18)
        if forte and c % 4 == 0: por(bat, g.prato(1.6), t0, .55)
        if c % 4 == 3: por(bat, g.rufo(BAR / 2, 2.4), t0 + BAR / 2, .55)
    # melodias: A no instrumento do tema + metais; B uma oitava acima nos violinos; C tudo
    def tocar(t, compassos, inst, ganho, desloc):
        q = t; fim = t + compassos * BAR
        while q < fim - .05:
            for n, d in mel:
                if q >= fim - .05: break
                if n: por(x, inst(hz(n, tr + desloc), b * d * 1.1), q, ganho)
                q += b * d
    t = INTRO * BAR
    tocar(t, A, lead, .5, 12 * oit); tocar(t, A, lambda f, d: g.metais(f, d, 1800), .28, 12 * oit - 12)
    t += A * BAR
    tocar(t, B, g.violino, .5, 12 * oit + 12); tocar(t, B, lambda f, d: g.coro([f], d, 900), .3, 12 * oit)
    t += (B + PAUSA) * BAR
    tocar(t, C, lead, .45, 12 * oit); tocar(t, C, g.violino, .35, 12 * oit + 12); tocar(t, C, lambda f, d: g.metais(f, d, 2000), .3, 12 * oit - 12)
    for ex in cfg.get('extras', []): ex(x, b, BAR, TOTAL, D, True)
    x = g.reverb(x, min(2.2, cfg.get('sala', 1.6)), .2)
    x = x / (np.max(np.abs(x)) + 1e-9) + g.reverb(bat, .7, .08) / (np.max(np.abs(bat)) + 1e-9) * .5
    return g.emendar(x[:int((D + 5) * SR)], D)

if __name__ == '__main__':
    quais = sys.argv[1:] or [t for t in g.TEMAS if t in g.VAR]
    with open(f'{OUT}/duracoes.txt', 'a') as f:
        for t in quais:
            d = g.salvar('chefe_' + t, faixa(t), .88, q='1')
            f.write(f'chefe_{t} {d:.2f}\n')
