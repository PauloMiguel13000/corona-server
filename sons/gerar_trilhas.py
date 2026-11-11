"""Trilhas originais das masmorras, uma por familia de masmorra (sintetizadas aqui, nada de terceiros).

Cada faixa tem andamento, escala, levada e instrumentos proprios, fecha em loop sem emenda e sai em Ogg
mono 32 kHz para nao pesar no celular.
"""
import numpy as np, subprocess, os, wave
from scipy import signal

SR = 44100
OUT = '/tmp/claude-0/sons/masmorras'
os.makedirs(OUT, exist_ok=True)
rng = np.random.default_rng(4242)

def t(d): return np.arange(int(round(d * SR))) / SR
def zeros(d): return np.zeros(int(round(d * SR)))
def lp(x, f, o=4):
    b, a = signal.butter(o, min(f, SR / 2 - 200) / (SR / 2), 'low'); return signal.lfilter(b, a, x)
def hp(x, f, o=2):
    b, a = signal.butter(o, max(20, f) / (SR / 2), 'high'); return signal.lfilter(b, a, x)
def bp(x, lo, hi, o=4):
    b, a = signal.butter(o, [lo / (SR / 2), min(hi, SR / 2 - 200) / (SR / 2)], 'band'); return signal.lfilter(b, a, x)

def env(dur, a, d, s, r):
    n = int(round(dur * SR))
    na, nd, nr = [max(1, int(round(v * SR))) for v in (a, d, r)]
    ns = max(1, n - na - nd - nr)
    e = np.concatenate([np.linspace(0, 1, na), np.linspace(1, s, nd), np.full(ns, s), np.linspace(s, 0, nr)])
    return e[:n] if len(e) >= n else np.concatenate([e, np.zeros(n - len(e))])

def por(buf, x, q, g=1.0):
    i = int(round(q * SR))
    if i >= len(buf) or i < 0: return
    j = min(len(buf), i + len(x))
    buf[i:j] += x[:j - i] * g

def nf(nome):
    base = {'C': 0, 'D': 2, 'E': 4, 'F': 5, 'G': 7, 'A': 9, 'B': 11}
    n = base[nome[0]]; i = 1
    if nome[1] in '#b':
        n += 1 if nome[1] == '#' else -1; i = 2
    return 440.0 * 2 ** ((n - 9) / 12 + (int(nome[i:]) - 4))

# ---------------------------------------------------------------- instrumentos
def corda(f, dur, brilho=4000, decai=0.995):
    """Corda dedilhada (Karplus-Strong como filtro IIR: rapido)."""
    n = int(round(dur * SR))
    L = max(2, int(round(SR / f)))
    x = lp(rng.standard_normal(min(n, L)), brilho, 2)
    x = np.concatenate([x, np.zeros(max(0, n - len(x)))])
    a = np.zeros(L + 2); a[0] = 1.0; a[L] = -decai / 2; a[L + 1] = -decai / 2
    y = signal.lfilter([1.0], a, x)
    return y * env(dur, 0.002, 0.04, 0.6, min(0.35, dur * 0.5)) * 0.5

def baixo(f, dur, corte=520, forma='mix'):
    tt = t(dur)
    x = np.sin(2 * np.pi * f * tt)
    if forma != 'puro': x = 0.55 * x + 0.65 * signal.sawtooth(2 * np.pi * f * tt)
    return lp(x, corte) * env(dur, 0.004, 0.06, 0.6, 0.06)

def metal(f, dur, det=0.012, lo=150, hi=3000):
    tt = t(dur)
    x = sum(signal.sawtooth(2 * np.pi * f * (1 + d) * tt + rng.random()) for d in (-det, 0, det))
    return bp(x, lo, hi) * env(dur, 0.02, 0.1, 0.65, 0.18) * 0.4

def flauta(f, dur):
    tt = t(dur)
    vib = 1 + 0.006 * np.sin(2 * np.pi * 5.2 * tt)
    x = np.sin(2 * np.pi * f * np.cumsum(vib) / SR) + 0.18 * np.sin(4 * np.pi * f * tt)
    x += lp(rng.standard_normal(len(tt)), 2500) * 0.05
    return x * env(dur, 0.06, 0.12, 0.75, 0.2) * 0.5

def sino(f, dur=2.4):
    tt = t(dur); x = np.zeros_like(tt)
    for k, g in ((1, 1.0), (2.76, 0.5), (5.4, 0.22), (8.9, 0.1)):
        x += g * np.sin(2 * np.pi * f * k * tt) * np.exp(-tt * (1.2 + k * 0.45))
    return x * 0.45

def orgao(freqs, dur, corte=1600):
    tt = t(dur); x = np.zeros(len(tt))
    for f in freqs:
        for k, g in ((1, 1.0), (2, 0.5), (3, 0.3), (4, 0.2)):
            x += g * np.sin(2 * np.pi * f * k * tt + rng.random())
    return lp(x, corte) * env(dur, 0.12, 0.2, 0.8, 0.3) / (len(freqs) * 2)

def coro(freqs, dur):
    tt = t(dur); x = np.zeros(len(tt))
    for f in freqs:
        vib = 1 + 0.003 * np.sin(2 * np.pi * 5 * tt + rng.random() * 6)
        x += np.sin(2 * np.pi * f * np.cumsum(vib) / SR)
    return lp(x, 1700) * env(dur, 0.3, 0.35, 0.7, 0.5) / len(freqs)

def pad(freqs, dur, corte=1500, det=0.005):
    tt = t(dur); x = np.zeros(len(tt))
    for f in freqs:
        for d in (-det, 0, det):
            x += signal.sawtooth(2 * np.pi * f * (1 + d) * tt + rng.random() * 6)
    return lp(x, corte) * 0.3 / len(freqs) * (1 + 0.08 * np.sin(2 * np.pi * 0.09 * tt))

def bumbo(dur=0.42, f0=150, f1=44, forca=1.0):
    tt = t(dur)
    f = f1 + (f0 - f1) * np.exp(-tt * 22)
    x = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-tt * 9)
    x += lp(rng.standard_normal(len(tt)), 700) * np.exp(-tt * 40) * 0.4
    return x * forca

def caixa(dur=0.22, corpo=190):
    tt = t(dur)
    x = bp(rng.standard_normal(len(tt)), 900, 7000) * np.exp(-tt * 26)
    x += np.sin(2 * np.pi * corpo * tt) * np.exp(-tt * 34) * 0.5
    return x * 0.8

def chimbal(dur=0.1, aberto=False):
    tt = t(dur * (3 if aberto else 1))
    return hp(rng.standard_normal(len(tt)), 7000) * np.exp(-tt * (11 if aberto else 45)) * 0.3

def tom(f=150, dur=0.28):
    tt = t(dur)
    return np.sin(2 * np.pi * (f * (1 + 0.5 * np.exp(-tt * 14))) * tt) * np.exp(-tt * 10) * 0.8

def mao(dur=0.18, f=320):
    tt = t(dur)
    x = np.sin(2 * np.pi * f * np.exp(-tt * 9) * tt) * np.exp(-tt * 16)
    x += bp(rng.standard_normal(len(tt)), 1200, 5000) * np.exp(-tt * 34) * 0.4
    return x * 0.7

def bigorna(dur=0.3):
    tt = t(dur)
    x = sum(np.sin(2 * np.pi * f * tt) * np.exp(-tt * (6 + f / 400)) for f in (520, 1180, 2310, 3570))
    return x * 0.25

def agua(dur, forca=0.2):
    tt = t(dur)
    n = lp(rng.standard_normal(len(tt)), 900)
    return n * forca * (1 + 0.5 * np.sin(2 * np.pi * 0.13 * tt))

def emendar(x, cross=2.2):
    n = int(round(cross * SR))
    y = x[:-n].copy()
    y[:n] = y[:n] * np.linspace(0, 1, n) + x[-n:] * np.linspace(1, 0, n)
    return y

def salvar(nome, x, vol=0.84, q='1'):
    x = np.asarray(x, float); x -= np.mean(x)
    x = np.tanh(x / (np.percentile(np.abs(x), 99.5) + 1e-9) * 0.9)
    x /= np.max(np.abs(x)) + 1e-9; x *= vol
    w = f'{OUT}/{nome}.wav'
    with wave.open(w, 'wb') as f:
        f.setnchannels(1); f.setsampwidth(2); f.setframerate(SR)
        f.writeframes((x * 32767).astype(np.int16).tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', w, '-c:a', 'libvorbis', '-q:a', q,
                    '-ar', '32000', f'{OUT}/{nome}.ogg'], check=True)
    os.remove(w)
    print(f'{nome:10s} {os.path.getsize(f"{OUT}/{nome}.ogg")//1024:4d} KB  {len(x)/SR:5.1f} s')

# ---------------------------------------------------------------- faixas
def base_ritmo(x, b, cps, estilo):
    """Levada de percussao comum, com variacao por estilo."""
    for c in range(cps):
        t0 = c * 4 * b
        if estilo == 'marcha':
            por(x, bumbo(0.44, 160, 46), t0, 1.0)
            por(x, bumbo(0.38, 150, 46), t0 + 2 * b, 0.8)
            por(x, caixa(), t0 + b, 0.7); por(x, caixa(), t0 + 3 * b, 0.7)
            for k in range(8): por(x, chimbal(), t0 + k * b / 2, 0.4 if k % 2 == 0 else 0.22)
        elif estilo == 'corrida':
            por(x, bumbo(0.4, 165, 46), t0, 1.0)
            por(x, bumbo(0.34, 155, 46), t0 + 1.5 * b, 0.7)
            por(x, bumbo(0.34, 155, 46), t0 + 2.75 * b, 0.6)
            por(x, caixa(), t0 + b, 0.85); por(x, caixa(), t0 + 3 * b, 0.85)
            for k in range(16): por(x, chimbal(0.07), t0 + k * b / 4, 0.4 if k % 4 == 0 else 0.18)
        elif estilo == 'mao':
            for k, g in ((0, .9), (0.75, .5), (1.5, .7), (2.25, .45), (3, .8), (3.5, .5)):
                por(x, mao(), t0 + k * b, g)
            por(x, bumbo(0.4, 150, 48), t0, 0.8); por(x, bumbo(0.36, 140, 48), t0 + 2.5 * b, 0.6)
            por(x, chimbal(0.12, True), t0 + 3.5 * b, 0.5)
        elif estilo == 'leve':
            por(x, bumbo(0.42, 145, 45), t0, 0.75)
            por(x, bumbo(0.36, 140, 45), t0 + 2.5 * b, 0.5)
            por(x, caixa(0.18, 220), t0 + 2 * b, 0.4)
            for k in range(8): por(x, chimbal(0.08), t0 + k * b / 2, 0.3 if k % 2 == 0 else 0.15)
        elif estilo == 'pesada':
            por(x, bumbo(0.5, 175, 40), t0, 1.15)
            por(x, bumbo(0.44, 165, 42), t0 + 1.5 * b, 0.8)
            por(x, bumbo(0.44, 165, 42), t0 + 3 * b, 0.75)
            por(x, caixa(0.26, 170), t0 + 2 * b, 0.95)
            for k in range(8): por(x, chimbal(0.09, k == 7), t0 + k * b / 2, 0.33)
        if c % 4 == 3 and estilo != 'leve':
            for k in range(4): por(x, tom(195 - k * 22, 0.24), t0 + 3 * b + k * b / 4, 0.5 + k * 0.1)

def faixa(bpm, cps, acordes, melodia, estilo, lead, extra=None, oitava=1.0, cross=2.2):
    b = 60 / bpm
    D = cps * 4 * b + 3
    x = zeros(D)
    for c in range(cps):
        t0 = c * 4 * b
        nb, tri = acordes[c % len(acordes)]
        for k in range(8):
            f = nf(nb) * (2 if k in (3, 6) else 1)
            por(x, baixo(f, b * 0.44), t0 + k * b / 2, 0.44 if k % 2 == 0 else 0.26)
        for k, n2 in enumerate(tri):
            por(x, corda(nf(n2) * oitava, b * 1.1), t0 + k * b, 0.26)
    base_ritmo(x, b, cps, estilo)
    q = 4 * 4 * b
    volta = 0
    while q < D - 3:
        for n2, dur in melodia:
            if n2 and q < D - 3:
                por(x, lead(nf(n2) * oitava, b * dur * 1.15), q, 0.46)
            q += b * dur
        volta += 1
        if volta > 6: break
    if extra: extra(x, b, cps, D)
    return emendar(x, cross)

AM = [('A2', ['A3', 'C4', 'E4']), ('F2', ['F3', 'A3', 'C4']), ('C2', ['C3', 'E3', 'G3']), ('G2', ['G2', 'B3', 'D4'])]
EM = [('E2', ['E3', 'G3', 'B3']), ('C2', ['C3', 'E3', 'G3']), ('G2', ['G3', 'B3', 'D4']), ('D2', ['D3', 'F#3', 'A3'])]
DM = [('D2', ['D3', 'F3', 'A3']), ('A2', ['A3', 'C4', 'E4']), ('B2', ['B2', 'D3', 'F3']), ('G2', ['G2', 'A#3', 'D4'])]
EF = [('E2', ['E3', 'F3', 'B3']), ('F2', ['F3', 'A3', 'C4']), ('E2', ['E3', 'G3', 'B3']), ('D2', ['D3', 'F3', 'A3'])]
BM = [('B2', ['B3', 'D4', 'F#4']), ('G2', ['G3', 'B3', 'D4']), ('A2', ['A3', 'C#4', 'E4']), ('F#2', ['F#3', 'A3', 'C#4'])]

M1 = [('E4', 1), ('A4', 1), ('C5', .5), ('B4', .5), ('A4', 1), ('G4', 1), ('E4', 1), (None, 1),
      ('F4', 1), ('A4', .5), ('C5', .5), ('A4', 1), ('G4', 1), ('E4', 1), ('D4', 1), (None, 1)]
M2 = [('B4', .5), ('E5', .5), ('D5', 1), ('B4', 1), ('G4', 1), ('A4', .5), ('B4', .5), ('G4', 1),
      ('E4', 1), ('D4', 1), ('E4', 2)]
M3 = [('D5', .5), ('C5', .5), ('A4', 1), ('F4', 1), ('D4', 1), ('A4', .5), ('C5', .5), ('D5', 1), ('A4', 2)]
M4 = [('E5', .5), ('F5', .5), ('E5', .5), ('D5', .5), ('C5', 1), ('B4', 1), ('E4', .5), ('F4', .5),
      ('G4', 1), ('E4', 2)]
M5 = [('F#4', 1), ('B4', 1), ('C#5', .5), ('B4', .5), ('A4', 1), ('F#4', 1), ('D4', 1), ('E4', 1), ('B4', 2)]

def ecoSino(x, b, cps, D):
    q = 0.0
    while q < D - 3:
        por(x, sino(nf(['A4', 'C5', 'E5'][rng.integers(3)]), 2.6), q, 0.22)
        q += 4 * b * 2

def marteladas(x, b, cps, D):
    for c in range(cps):
        t0 = c * 4 * b
        por(x, bigorna(), t0 + 1.5 * b, 0.55)
        if c % 2 == 1: por(x, bigorna(0.26), t0 + 3.25 * b, 0.4)

def ondas(x, b, cps, D):
    por(x, agua(D, 0.16), 0, 1.0)

def orgaoFundo(x, b, cps, D):
    for c in range(0, cps, 2):
        por(x, orgao([nf('A2'), nf('E3'), nf('A3')], b * 8), c * 4 * b, 0.4)

def coroFundo(x, b, cps, D):
    for c in range(0, cps, 4):
        por(x, coro([nf('B3'), nf('D4'), nf('F#4')], b * 16), c * 4 * b, 0.45)

TRILHAS = {
    # nome        bpm  cps acordes melodia estilo    lead    extra        oitava
    'cripta':    (92,  16, AM, M1, 'marcha', corda,  orgaoFundo, 1.0),
    'mina':      (112, 16, DM, M3, 'marcha', corda,  marteladas, 1.0),
    'floresta':  (116, 16, EM, M2, 'mao',    flauta, None,       1.0),
    'gelo':      (104, 16, BM, M5, 'leve',   sino,   ecoSino,    1.0),
    'mar':       (100, 16, DM, M3, 'leve',   flauta, ondas,      1.0),
    'fogo':      (150, 20, EF, M4, 'corrida', metal, None,       1.0),
    'vazio':     (124, 16, BM, M5, 'leve',   sino,   coroFundo,  1.0),
    'arcano':    (128, 18, AM, M1, 'corrida', metal, coroFundo,  1.0),
}

if __name__ == '__main__':
    import sys
    quais = sys.argv[1:] or list(TRILHAS)
    for nome in quais:
        bpm, cps, ac, mel, est, lead, extra, oit = TRILHAS[nome]
        salvar(nome, faixa(bpm, cps, ac, mel, est, lead, extra, oit), vol=0.84, q='1')
