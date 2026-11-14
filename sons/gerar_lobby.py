"""Musica do LOBBY das Masmorras (14/11, Paulo: "no lobby masmorra, coloque a musica caracteristica, mas com mais
qualidade, como uma musica de lobby mesmo").

A melodia caracteristica das masmorras (a da primeira trilha, Cripta/Arcano: mi-la-do-si-la-sol-mi) com a mesma
harmonia (La menor: La, Fa, Do, Sol), agora arranjada como musica de lobby: estereo, 44,1 kHz, qualidade alta,
com introducao, parte A (flauta e piano), parte B (violinos, coro e harpa, sobe) e parte A' (trompas e orquestra
cheia), voltando calma para emendar o loop. ~76 s.
"""
import numpy as np, subprocess, os, wave
from scipy import signal
import gerar_trilhas_v3 as g

SR = g.SR = 44100
OUT = '/tmp/claude-0/sons/v3'
os.makedirs(OUT, exist_ok=True)
g.R.g = np.random.default_rng(2026)
rng = g.R.g
T, Z, lp, hp, env = g.T, g.Z, g.lp, g.hp, g.env

def nm(n):
    base = {'C': 0, 'D': 2, 'E': 4, 'F': 5, 'G': 7, 'A': 9, 'B': 11}
    k = base[n[0]]; i = 1
    if n[1] in '#b': k += 1 if n[1] == '#' else -1; i = 2
    return 440.0 * 2 ** ((12 * (int(n[i:]) + 1) + k - 69) / 12)

# ---------------------------------------------------------------- estereo
L = None; Rr = None
def por(sinal, t, ganho=1.0, pan=0.0):
    """pan -1 (esquerda) .. 1 (direita), lei de potencia constante."""
    a = (pan + 1) * np.pi / 4
    g.por(L, sinal, t, ganho * np.cos(a)); g.por(Rr, sinal, t, ganho * np.sin(a))

# ---------------------------------------------------------------- instrumentos de mais qualidade
def piano(f, d):
    dur = max(d, 1.8); tt = T(dur); x = np.zeros_like(tt)
    for k in range(1, 9):
        fk = f * k * np.sqrt(1 + 0.0004 * k * k)          # leve inarmonicidade de corda de piano
        x += (1 / k ** 1.3) * np.sin(2 * np.pi * fk * tt + rng.random()) * np.exp(-tt * (0.9 + 0.55 * k) * (1 + f / 900))
    x += hp(rng.standard_normal(len(tt)), 2000) * np.exp(-tt * 90) * 0.15   # martelo
    return x * np.clip(tt / 0.004, 0, 1) * 0.35

def cordas(freqs, d, corte=2200):
    tt = T(d); x = np.zeros(len(tt))
    for f in freqs:
        for v in range(5):
            dt = (v - 2) * 0.0035
            vib = 1 + 0.004 * np.sin(2 * np.pi * (5 + v * 0.3) * tt + rng.random() * 6)
            x += signal.sawtooth(2 * np.pi * f * (1 + dt) * np.cumsum(vib) / SR)
    return lp(lp(x, corte), corte * 1.4) * env(d, 0.6, 0.3, 0.9, 0.8) * 0.16 / len(freqs)

def violino(f, d):
    tt = T(d); vib = 1 + 0.006 * np.sin(2 * np.pi * 5.6 * tt) * np.clip((tt - 0.15) * 3, 0, 1)
    x = sum(signal.sawtooth(2 * np.pi * f * (1 + dt) * np.cumsum(vib) / SR) for dt in (-0.002, 0, 0.002)) / 3
    return g.bp(x, 280, 4200) * env(d, 0.12, 0.1, 0.85, min(0.3, d * 0.4)) * 0.45

def flauta(f, d):
    tt = T(d); vib = 1 + 0.005 * np.sin(2 * np.pi * 5.0 * tt) * np.clip((tt - 0.2) * 3, 0, 1)
    x = np.sin(2 * np.pi * f * np.cumsum(vib) / SR) + 0.12 * np.sin(4 * np.pi * f * tt) + 0.05 * np.sin(6 * np.pi * f * tt)
    x += lp(rng.standard_normal(len(tt)), 4000) * 0.03 * env(d, 0.02, 0.1, 0.4, 0.1)
    return x * env(d, 0.07, 0.1, 0.8, min(0.25, d * 0.4)) * 0.5

def trompa(f, d):
    tt = T(d)
    x = sum(signal.sawtooth(2 * np.pi * f * (1 + dt) * tt + rng.random() * 6) for dt in (-0.003, 0, 0.003)) / 3
    return lp(lp(x, 1100), 1500) * env(d, 0.09, 0.15, 0.85, min(0.3, d * 0.4)) * 0.6

def harpa(f, d): return g.pluck(f, max(d, 1.8), 5500, 0.998) * 0.9

def baixo(f, d):
    tt = T(d); x = np.sin(2 * np.pi * f * tt) + 0.25 * np.sin(4 * np.pi * f * tt) + 0.2 * lp(signal.sawtooth(2 * np.pi * f * tt), 400)
    return x * env(d, 0.01, 0.2, 0.7, 0.2) * 0.55

# ---------------------------------------------------------------- musica
BPM = 88; b = 60 / BPM; BAR = 4 * b
AM = [('A2', ['A3', 'C4', 'E4']), ('F2', ['F3', 'A3', 'C4']), ('C2', ['C3', 'E3', 'G3']), ('G2', ['G2', 'B3', 'D4'])]
M1 = [('E4', 1), ('A4', 1), ('C5', .5), ('B4', .5), ('A4', 1), ('G4', 1), ('E4', 1), (None, 1),
      ('F4', 1), ('A4', .5), ('C5', .5), ('A4', 1), ('G4', 1), ('E4', 1), ('D4', 1), (None, 1)]
# resposta da parte B (mesma familia de notas, sobe e volta para o tema)
MB = [('C5', 1.5), ('B4', .5), ('A4', 1), ('E5', 1), ('D5', 1.5), ('C5', .5), ('B4', 2),
      ('A4', 1), ('C5', 1), ('E5', 1), ('G5', 1), ('F5', 1.5), ('E5', .5), ('D5', 1), ('E5', 1)]

INTRO, A, B, A2, FIM = 4, 8, 8, 8, 2
TOTAL = INTRO + A + B + A2 + FIM
D = TOTAL * BAR

def melodia(mel, t0, compassos, inst, ganho, pan, oit=1.0, harmonia=None):
    q = t0; fim = t0 + compassos * BAR
    while q < fim - 0.05:
        for n, d in mel:
            if q >= fim - 0.05: break
            if n:
                por(inst(nm(n) * oit, b * d * 1.05), q, ganho, pan)
                if harmonia: por(harmonia(nm(n) * oit * 2 ** (-4 / 12) if n[0] in 'CFG' else nm(n) * oit * 2 ** (-3 / 12), b * d), q, ganho * 0.45, -pan)
            q += b * d

def main():
    global L, Rr
    L = Z(D + 6); Rr = Z(D + 6)
    for c in range(TOTAL):
        t0 = c * BAR
        nb, tri = AM[c % 4]
        fr = [nm(n) for n in tri]
        sec = 'intro' if c < INTRO else 'A' if c < INTRO + A else 'B' if c < INTRO + A + B else 'A2' if c < INTRO + A + B + A2 else 'fim'
        # cordas de fundo sempre (mais fortes no B e no A')
        gc = {'intro': .55, 'A': .6, 'B': .85, 'A2': 1.0, 'fim': .5}[sec]
        por(cordas(fr, BAR + 0.8), t0, gc, -0.3); por(cordas([f * 2 for f in fr], BAR + 0.8, 2600), t0, gc * 0.45, 0.35)
        # harpa em arpejo (8 colcheias), menos no fim
        if sec != 'fim':
            seq = fr + [f * 2 for f in fr]; ordem = [0, 1, 2, 3, 4, 3, 2, 1]
            for k in range(8): por(harpa(seq[ordem[k]], b), t0 + k * b / 2, .32 if sec != 'A2' else .26, 0.45)
        # baixo
        if sec in ('A', 'B', 'A2'):
            for k in (0, 2, 2.5):
                por(baixo(nm(nb), b * (1.9 if k == 0 else .45)), t0 + k * b, .7 if k == 0 else .45, 0)
        elif sec == 'intro':
            por(baixo(nm(nb), BAR), t0, .5, 0)
        # piano na parte A (acordes no 1 e no 3)
        if sec == 'A':
            for k in (0, 2):
                for i, f in enumerate(fr): por(piano(f, b * 1.5), t0 + k * b + i * 0.01, .22, -0.2)
        # coro no B e no A'
        if sec in ('B', 'A2') and c % 2 == 0:
            por(g.coro(fr, 2 * BAR + 0.6, 750), t0, .55 if sec == 'B' else .7, 0.1)
        # percussao de orquestra: timpano no 1, bumbo leve; no A' caixa suave e pratos
        if sec in ('B', 'A2'):
            por(g.timpano(nm(nb) / 2 if nm(nb) / 2 > 45 else nm(nb), 1.2), t0, .55, 0)
            por(g.bumbo(110, 45, .4, .5), t0 + 2 * b, .5, 0)
        if sec == 'A2':
            por(g.caixa(220, .18), t0 + b, .18, 0.2); por(g.caixa(220, .18), t0 + 3 * b, .18, 0.2)
            if c % 4 == 0: por(g.prato(2.0), t0, .25, -0.4)
        if c in (INTRO + A + B - 1,):   # rufo de timpano antes do A'
            for k in range(12): por(g.timpano(nm('A2') * 1.0, .3), t0 + 2 * b + k * b / 6, .2 + k * .03, 0)
        if c % 8 == 0: por(g.sino(nm('E5'), 3.5), t0, .22 if sec != 'fim' else .3, 0.5)
    # melodias
    t = INTRO * BAR
    melodia(M1, t, A // 2, flauta, .55, -0.1)                         # A: flauta
    melodia(M1, t + 4 * BAR, A // 2, flauta, .5, -0.1, harmonia=None)
    melodia(M1, t + 4 * BAR, A // 2, piano, .25, 0.25, oit=2.0)        # piano dobra uma oitava acima
    t += A * BAR
    melodia(MB, t, B, violino, .55, 0.15)                              # B: violinos com a resposta
    melodia(MB, t, B, violino, .25, -0.3, oit=0.5)
    t += B * BAR
    melodia(M1, t, A2, trompa, .6, -0.05, oit=1.0)                     # A': tema nas trompas
    melodia(M1, t, A2, violino, .35, 0.3, oit=2.0)                     # violinos uma oitava acima
    melodia(M1, t + (A2 - 2) * BAR, 2, flauta, .3, 0)                  # flauta despede
    # reverb diferente em cada lado (sala larga)
    def rev(x, seg, mix, semente):
        rr = np.random.default_rng(semente); n = int(seg * SR); tt = np.arange(n) / SR
        ir = lp(rr.standard_normal(n), 5000) * np.exp(-tt * 6.9 / seg); ir[0] = 0; ir /= np.sqrt(np.sum(ir ** 2))
        return x * (1 - mix) + signal.fftconvolve(x, ir)[:len(x)] * mix * 1.3
    L2 = rev(L, 2.8, .3, 1); R2 = rev(Rr, 2.8, .3, 2)
    n = int(round(D * SR))
    est = np.stack([L2, R2])
    y = est[:, :n].copy(); cauda = est[:, n:]; y[:, :cauda.shape[1]] += cauda   # loop sem emenda
    y -= y.mean(axis=1, keepdims=True)
    pk = np.percentile(np.abs(y), 99.8); y = np.tanh(y / pk * .9); y /= np.max(np.abs(y)); y *= .88
    w = f'{OUT}/lobby.wav'
    with wave.open(w, 'wb') as f:
        f.setnchannels(2); f.setsampwidth(2); f.setframerate(SR)
        f.writeframes((y.T * 32767).astype(np.int16).tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', w, '-c:a', 'libvorbis', '-q:a', '4', f'{OUT}/lobby.ogg'], check=True)
    os.remove(w)
    print('lobby', os.path.getsize(f'{OUT}/lobby.ogg') // 1024, 'KB', round(D, 2), 's')

if __name__ == '__main__':
    main()
