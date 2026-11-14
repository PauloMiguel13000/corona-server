"""Musica da LUTA do Herobrine v3 (14/11, Paulo: "melhore a musica do Herobrine, faca fodona").

A melodia caracteristica das masmorras virada do avesso (La menor harmonico com sol#, acordes com tritono) num
arranjo de chefe final de filme: introducao com trovao, coracao e coro sussurrando; parte A com o tema nos metais
graves e cordas em semicolcheia; parte B com bateria em tempo dobrado e o tema nos violinos; uma QUEBRA onde tudo
some e o tema toca numa caixinha de musica (sinistro) enquanto a energia e sugada; e o CLIMAX com tudo junto
(metais em 3 oitavas, violinos, coro cantando o tema, pratos, rufos de timpano). Os poderes dele aparecem na musica:
raios, brilhos magicos subindo, risada grave e o "suga" ao contrario. Estereo 44,1 kHz, fecha em loop.
"""
import numpy as np, subprocess, os, wave
from scipy import signal
import gerar_lobby as gl
import gerar_trilhas_v3 as g

SR = 44100
rng = np.random.default_rng(666)
g.R.g = rng
T, Z, lp, hp, env, bp = g.T, g.Z, g.lp, g.hp, g.env, g.bp
nm = gl.nm
OUT = '/tmp/claude-0/sons/v3'

BPM = 150; b = 60 / BPM; BAR = 4 * b
PROG = [('A1', ['A3', 'C4', 'E4']), ('F1', ['F3', 'A3', 'C4']), ('D#2', ['D#3', 'F#3', 'A3']), ('E1', ['E3', 'G#3', 'B3'])]
M1H = [('E4', 1), ('A4', 1), ('C5', .5), ('B4', .5), ('A4', 1), ('G#4', 1), ('E4', 1), (None, 1),
       ('F4', 1), ('A4', .5), ('C5', .5), ('A4', 1), ('G#4', 1), ('E4', 1), ('D#4', 1), (None, 1)]
MR = [('A5', .5), ('G#5', .5), ('E5', 1), ('D#5', 1), ('C5', 1), ('B4', .5), ('C5', .5), ('D#5', 1), ('E5', 2),
      ('F5', 1), ('E5', .5), ('D#5', .5), ('C5', 1), ('B4', 1), ('G#4', 1), ('A4', 3)]
INTRO, A, B, QUEBRA, C, FIM = 4, 8, 8, 2, 8, 2
TOTAL = INTRO + A + B + QUEBRA + C + FIM
D = TOTAL * BAR

def metal_dist(f, d):
    tt = T(d); x = sum(signal.sawtooth(2 * np.pi * f * (1 + dt) * tt + rng.random() * 6) for dt in (-.006, 0, .006)) / 3
    return lp(np.tanh(3 * lp(x, 1400)), 2400) * env(d, .02, .1, .85, min(.2, d * .3)) * .5

def metais_3(f, d):   # naipe de metais em 3 oitavas (trompas + trombones + tuba)
    return gl.trompa(f, d) * .6 + metal_dist(f / 2, d) * .5 + gl.trompa(f * 2, d) * .25

def golpe(f):   # "hit" de orquestra: metais + timpano + prato curto + sub
    tt = T(1.2)
    sub = np.sin(2 * np.pi * (f / 4) * tt * (1 + .4 * np.exp(-tt * 8))) * np.exp(-tt * 3) * .9
    x = sub + hp(rng.standard_normal(len(tt)), 3000) * np.exp(-tt * 9) * .2
    m = metais_3(f, .5) * 1.1; x[:len(m)] += m
    tp = g.timpano(f / 2 if f / 2 > 45 else f, 1.2) * .7; x[:len(tp)] += tp[:len(x)]
    return x

def risada(d=1.8):
    tt = T(d); f = 150 - 60 * tt / d
    v = bp(signal.sawtooth(2 * np.pi * np.cumsum(f) / SR), 300, 1000) * (0.55 + 0.45 * (np.sin(2 * np.pi * 6.5 * tt) > 0))
    return v * env(d, .1, .3, .7, .5) * .6

def raio():
    tt = T(.4); return hp(rng.standard_normal(len(tt)), 2500) * np.exp(-tt * 12) * 1.2

def suga(d):
    n = int(d * SR); tt = np.arange(n) / SR; k = (tt / tt[-1]) ** 3
    return bp(rng.standard_normal(n), 400, 6000) * k * .5 + np.sin(2 * np.pi * np.cumsum(150 + 2200 * k) / SR) * k * .35

def coro_sussurro(freqs, d):   # coro em "uuu" fechado, quase sussurrado
    return g.coro(freqs, d, 420) * .9 + bp(rng.standard_normal(len(T(d))), 2500, 6000) * env(d, .5, .3, .5, .6) * .04

def main():
    gl.b, gl.BAR = b, BAR
    gl.L = Z(D + 6); gl.Rr = Z(D + 6)
    por = gl.por
    iA = INTRO; iB = iA + A; iQ = iB + B; iC = iQ + QUEBRA; iF = iC + C
    for c in range(TOTAL):
        t0 = c * BAR
        nb, tri = PROG[c % 4]; fr = [nm(n) for n in tri]; fb = nm(nb)
        sec = 'intro' if c < iA else 'A' if c < iB else 'B' if c < iQ else 'Q' if c < iC else 'C' if c < iF else 'fim'
        # drone grave com tritono (sempre, menos na quebra)
        if sec != 'Q': por(lp(g.pad([fb, fb * 2 ** (6 / 12)], BAR + .6, 400, .004), 350) * 1.6, t0, .65, 0)
        if sec in ('intro', 'fim'):
            for k in (0, .42): por(g.bumbo(70, 35, .35, .9), t0 + k + (BAR / 2 if c % 2 else 0), .8, 0)   # coracao
            if c % 2 == 0: por(coro_sussurro(fr, 2 * BAR + .5), t0, .55, -.2)
            if c == 0: por(g.trovao(3.5), t0, .8, .3); por(raio(), t0, .6, -.4)
            if c == 2: por(risada(2.0), t0 + b, .55, .5)
            if c == iA - 1 or c == TOTAL - 1: por(suga(BAR), t0, .7, 0)
            continue
        if sec == 'Q':
            # quebra: so a caixinha de musica tocando o tema la em cima, eco e o suga
            if c == iQ: por(golpe(fb * 4), t0, .9, 0)
            if c == iC - 1: por(suga(BAR), t0, .8, 0); por(risada(1.6), t0 + .2, .5, -.5)
            continue
        forte = sec == 'C'
        # golpe de orquestra no 1 de cada 2 compassos
        if c % 2 == 0: por(golpe(fb * 4), t0, .55 if forte else .45, 0)
        # coro: acordes longos (B e C) / stabs (A)
        if sec == 'A':
            for k in (1.5, 3.5): por(g.coro(fr, b * .6, 700), t0 + k * b, .4, -.25)
        elif c % 2 == 0:
            por(g.coro(fr + [fb * 2 ** (6 / 12) * 4], 2 * BAR + .6, 700), t0, .6 if forte else .5, -.25)
        # cordas em semicolcheia (tensao maxima)
        for k in range(16):
            f = fr[[0, 1, 2, 1][k % 4]] * 2
            por(g.cordas_st(f, b / 4 * 1.6), t0 + k * b / 4, .18 if k % 4 == 0 else .12, .35 if k % 2 else -.35)
        # baixo de metal distorcido 3+3+2
        for k, d in ((0, 1.5), (1.5, 1.5), (3, 1)):
            por(metal_dist(fb * 2, b * d * .9), t0 + k * b, .5, 0)
        # bateria
        if sec == 'A':
            for k, gg in ((0, 1.0), (1.5, .6), (2, .8), (3, .7), (3.5, .5)): por(g.taiko(), t0 + k * b, gg, (-.2 if k % 1 else .2))
            por(g.caixa(170, .22), t0 + b, .3, .15); por(g.caixa(170, .22), t0 + 3 * b, .35, .15)
        else:   # B e C: tempo dobrado
            for k in range(8): por(g.taiko(), t0 + k * b / 2, .75 if k % 2 == 0 else .45, (-.2 if k % 2 else .2))
            for k in (1, 3): por(g.caixa(170, .22), t0 + k * b, .55, .15)
            for k in range(8): por(g.chimbal(.06), t0 + k * b / 2, .25, .5)
            if c % 4 == 3:
                for k in range(8): por(g.caixa(230, .1), t0 + 2 * b + k * b / 4, .2 + k * .05, -.1)   # rufo de caixa
        if forte and c % 2 == 0: por(g.prato(2.0), t0, .38, -.5)
        if c % 4 == 3: por(g.rufo(BAR / 2, 2.5), t0 + BAR / 2, .4, 0)
        # PODERES na musica
        if c % 4 == 1 and sec in ('B', 'C'):
            for k in range(8): por(g.fm(nm('E5') * 2 ** (2 * k / 12), .9, 4, 1.41), t0 + k * b / 2, .14, -.6 + k * .15)
        if c % 4 == 2:
            por(raio(), t0 + 2 * b, .6, .4); por(g.trovao(2.5), t0 + 2 * b, .45, -.3)
        if c == iB - 1 or c == iF - 1: por(suga(BAR), t0, .5, 0)
    # melodias
    t = iA * BAR
    gl.melodia(M1H, t, A, metais_3, .55, -.1, oit=0.5)                    # A: tema nos metais graves
    gl.melodia(M1H, t + 4 * BAR, 4, gl.violino, .25, .3, oit=1.0)
    t = iB * BAR
    gl.melodia(MR, t, B, gl.violino, .55, .2, oit=1.0)                    # B: violinos com a resposta
    gl.melodia(MR, t, B, gl.violino, .25, -.3, oit=2.0)
    gl.melodia(MR, t, B, metal_dist, .25, 0, oit=0.5)
    t = iQ * BAR
    gl.melodia(M1H, t, QUEBRA, lambda f, d: g.caixinha(f, d), .5, 0, oit=2.0)   # quebra: caixinha de musica
    t = iC * BAR
    gl.melodia(M1H, t, C, gl.violino, .45, .3, oit=2.0)                   # C: tudo junto
    gl.melodia(M1H, t, C, metais_3, .55, -.2, oit=1.0)
    gl.melodia(M1H, t, C, lambda f, d: g.coro([f], d, 900), .4, 0, oit=1.0)
    def rev(x, seg, mix, semente):
        rr = np.random.default_rng(semente); n = int(seg * SR); tt = np.arange(n) / SR
        ir = lp(rr.standard_normal(n), 5000) * np.exp(-tt * 6.9 / seg); ir[0] = 0; ir /= np.sqrt(np.sum(ir ** 2))
        return x * (1 - mix) + signal.fftconvolve(x, ir)[:len(x)] * mix * 1.3
    est = np.stack([rev(gl.L, 2.6, .27, 7), rev(gl.Rr, 2.6, .27, 8)])
    n = int(round(D * SR)); y = est[:, :n].copy(); cauda = est[:, n:]; y[:, :cauda.shape[1]] += cauda
    y -= y.mean(axis=1, keepdims=True)
    pk = np.percentile(np.abs(y), 99.7); y = np.tanh(y / pk * .95); y /= np.max(np.abs(y)); y *= .9
    w = f'{OUT}/herobrine.wav'
    with wave.open(w, 'wb') as f:
        f.setnchannels(2); f.setsampwidth(2); f.setframerate(SR); f.writeframes((y.T * 32767).astype(np.int16).tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', w, '-c:a', 'libvorbis', '-q:a', '4', f'{OUT}/herobrine.ogg'], check=True)
    os.remove(w)
    print('herobrine', os.path.getsize(f'{OUT}/herobrine.ogg') // 1024, 'KB', round(D, 2), 's')

if __name__ == '__main__':
    main()
