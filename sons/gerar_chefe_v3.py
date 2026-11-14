"""Musica da LUTA DE CHEFE v2 (todos os chefes, menos o Herobrine que tem a dele)

Gerada a partir do gerar_herobrine.py, mas epica em vez de sinistra: harmonia original (La, Fa, Do, Sol).

Original: Musica da LUTA do Herobrine v2 (14/11, Paulo: "faltou a do Herobrine").

A melodia caracteristica das masmorras (mi-la-do-si-la-sol-mi) virada do avesso: La menor harmonico (sol#, o som
mais sinistro), acordes com tritono, coro sombrio, taikos, metais distorcidos, violinos cortantes e os PODERES dele
no meio da musica: raios com trovao, brilhos magicos subindo, energia sendo sugada ao contrario e a risada grave.
Estereo, 44,1 kHz, qualidade alta, fecha em loop. Mesmo arquivo de antes: masmorras.herobrine.
"""
import numpy as np, subprocess, os, wave
from scipy import signal
import gerar_lobby as gl
import gerar_trilhas_v3 as g

SR = 44100
rng = np.random.default_rng(777)
g.R.g = rng
T, Z, lp, hp, env, bp = g.T, g.Z, g.lp, g.hp, g.env, g.bp
nm = gl.nm
OUT = '/tmp/claude-0/sons/v3'

BPM = 150; b = 60 / BPM; BAR = 4 * b
# La menor harmonico: La, Fa, Re# diminuto (tritono), Mi maior (com sol#)
PROG = [('A1', ['A3', 'C4', 'E4']), ('F1', ['F3', 'A3', 'C4']), ('C2', ['C3', 'E3', 'G3']), ('G1', ['G3', 'B3', 'D4'])]
M1H = [('E4', 1), ('A4', 1), ('C5', .5), ('B4', .5), ('A4', 1), ('G4', 1), ('E4', 1), (None, 1),
       ('F4', 1), ('A4', .5), ('C5', .5), ('A4', 1), ('G4', 1), ('E4', 1), ('D4', 1), (None, 1)]
# resposta (sobe ate o tritono e cai)
MR = [('C5', .5), ('D5', .5), ('E5', 1), ('G5', 1), ('E5', 1), ('D5', .5), ('C5', .5), ('D5', 1), ('E5', 2),
      ('F5', 1), ('E5', .5), ('D5', .5), ('C5', 1), ('B4', 1), ('G4', 1), ('A4', 3)]
INTRO, A, B, PAUSA, C = 4, 8, 8, 2, 8
TOTAL = INTRO + A + B + PAUSA + C
D = TOTAL * BAR

def metal_dist(f, d):
    tt = T(d); x = sum(signal.sawtooth(2 * np.pi * f * (1 + dt) * tt + rng.random() * 6) for dt in (-.006, 0, .006)) / 3
    return lp(np.tanh(3 * lp(x, 1400)), 2400) * env(d, .02, .1, .85, min(.2, d * .3)) * .5

def sussurro_risada(d=1.6):
    tt = T(d); f = 150 - 55 * tt / d
    v = bp(signal.sawtooth(2 * np.pi * np.cumsum(f) / SR), 300, 1000) * (0.6 + 0.4 * (np.sin(2 * np.pi * 7 * tt) > 0))
    return v * env(d, .1, .3, .7, .5) * .55

def raio():
    tt = T(.4); z = hp(rng.standard_normal(len(tt)), 2500) * np.exp(-tt * 12)
    return z * 1.2

def suga(d):
    n = int(d * SR); tt = np.arange(n) / SR; k = (tt / tt[-1]) ** 3
    return bp(rng.standard_normal(n), 400, 6000) * k * .5 + np.sin(2 * np.pi * np.cumsum(150 + 2200 * k) / SR) * k * .35

def main():
    gl.b, gl.BAR = b, BAR   # a melodia (gl.melodia) usa o andamento DESTA musica, nao o do lobby
    gl.L = Z(D + 6); gl.Rr = Z(D + 6)
    por = gl.por
    for c in range(TOTAL):
        t0 = c * BAR
        nb, tri = PROG[c % 4]; fr = [nm(n) for n in tri]; fb = nm(nb)
        sec = 'intro' if c < INTRO else 'A' if c < INTRO + A else 'B' if c < INTRO + A + B else 'pausa' if c < INTRO + A + B + PAUSA else 'C'
        # drone grave sempre (tritono embaixo)
        por(lp(g.pad([fb, fb * 1.5], BAR + .6, 400, .004), 350) * 1.6, t0, .7 if sec != 'pausa' else .4, 0)
        if sec == 'intro':
            for k in (0, .45): por(g.bumbo(70, 35, .35, .9), t0 + k + (BAR / 2 if c % 2 else 0), .8, 0)   # coracao
            if c == 1: por(g.timpano(fb * 2, 1.5), t0, .7, 0)
            if c == 3: por(suga(BAR), t0, .7, 0)
            continue
        if sec == 'pausa':
            if c == INTRO + A + B: por(suga(BAR * 1.0), t0, .8, 0); por(g.prato(2.5), t0 + BAR * .5, .4, .5)
            else:
                por(raio(), t0, .9, -.3); por(g.trovao(3.0), t0, .8, .3)
            continue
        forte = sec == 'C'
        # coro dissonante (a cada 2 compassos)
        if c % 2 == 0: por(g.coro(fr, 2 * BAR + .6, 750), t0, .6 if forte else .5, -.25)
        # cordas staccato em colcheia (tensao)
        for k in range(8):
            for f in fr[:2]: por(g.cordas_st(f, b / 2), t0 + k * b / 2, .22, .35)
        # baixo de metal distorcido no ritmo 3+3+2
        for k, d in ((0, 1.5), (1.5, 1.5), (3, 1)):
            por(gl.trompa(fb * 2, b * d * .9) * 1.3, t0 + k * b, .55, 0)
        # bateria: taikos (+ caixa e pratos no C)
        for k, gg in ((0, 1.0), (1.5, .6), (2, .8), (3, .7), (3.5, .5)):
            por(g.taiko(), t0 + k * b, gg, (-.2 if k % 1 else .2))
        por(g.caixa(170, .22), t0 + 1 * b, .45 if forte else .3, .15); por(g.caixa(170, .22), t0 + 3 * b, .5 if forte else .3, .15)
        if sec in ('B', 'C'):
            for k in range(8): por(g.chimbal(.06), t0 + k * b / 2, .25, .5)
        if forte and c % 4 == 0: por(g.prato(2.0), t0, .35, -.5)
        if c % 4 == 3: por(g.rufo(BAR / 2, 2.5), t0 + BAR / 2, .55, 0)
        # PODERES
        if c % 4 == 1 and sec in ('B', 'C'):   # brilhos magicos subindo (tons inteiros)
            for k in range(8): por(g.fm(nm('E5') * 2 ** (2 * k / 12), .9, 4, 1.41), t0 + k * b / 2, .14, -.6 + k * .15)
        if c % 4 == 2 and sec in ('A', 'C'):   # raio + trovao
            por(raio(), t0 + 2 * b, .7, .4); por(g.trovao(2.5), t0 + 2 * b, .5, -.3)
        if c == INTRO + A - 1 or c == TOTAL - 1:   # suga antes da virada
            por(suga(BAR), t0, .55, 0)
    # melodias
    t = INTRO * BAR
    gl.melodia(M1H, t, A, gl.trompa, .65, -.1, oit=1.0)                 # A: tema nas trompas
    gl.melodia(M1H, t, A, gl.violino, .3, .3, oit=1.0)
    t += A * BAR
    gl.melodia(MR, t, B, gl.violino, .55, .2, oit=1.0)                  # B: violinos cortantes com a resposta
    gl.melodia(MR, t, B, gl.trompa, .3, -.3, oit=0.5)
    t += (B + PAUSA) * BAR
    gl.melodia(M1H, t, C, gl.violino, .45, .3, oit=2.0)                 # C: tudo junto, tema la em cima e embaixo
    gl.melodia(M1H, t, C, gl.trompa, .55, -.2, oit=1.0)
    gl.melodia(M1H, t, C, lambda f, d: g.coro([f], d, 900), .35, 0, oit=1.0)
    def rev(x, seg, mix, semente):
        rr = np.random.default_rng(semente); n = int(seg * SR); tt = np.arange(n) / SR
        ir = lp(rr.standard_normal(n), 5000) * np.exp(-tt * 6.9 / seg); ir[0] = 0; ir /= np.sqrt(np.sum(ir ** 2))
        return x * (1 - mix) + signal.fftconvolve(x, ir)[:len(x)] * mix * 1.3
    est = np.stack([rev(gl.L, 2.4, .26, 7), rev(gl.Rr, 2.4, .26, 8)])
    n = int(round(D * SR)); y = est[:, :n].copy(); cauda = est[:, n:]; y[:, :cauda.shape[1]] += cauda
    y -= y.mean(axis=1, keepdims=True)
    pk = np.percentile(np.abs(y), 99.7); y = np.tanh(y / pk * .95); y /= np.max(np.abs(y)); y *= .9
    w = f'{OUT}/chefe.wav'
    with wave.open(w, 'wb') as f:
        f.setnchannels(2); f.setsampwidth(2); f.setframerate(SR); f.writeframes((y.T * 32767).astype(np.int16).tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', w, '-c:a', 'libvorbis', '-q:a', '4', f'{OUT}/chefe.ogg'], check=True)
    os.remove(w)
    print('chefe', os.path.getsize(f'{OUT}/chefe.ogg') // 1024, 'KB', round(D, 2), 's')

if __name__ == '__main__':
    main()
