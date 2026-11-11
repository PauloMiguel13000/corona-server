"""Trilha original das masmorras (sintetizada aqui, nada de terceiros) - versao agitada.

explorar: aventura com pulso e melodia de alaude.  combate: rapida, bateria firme e riff.
chefe: epica, metais e coro.  Tudo mono, loop sem emenda, Ogg bem comprimido.
"""
import numpy as np, subprocess, os, wave
from scipy import signal

SR = 44100
OUT = '/tmp/claude-0/sons/masmorras'
os.makedirs(OUT, exist_ok=True)
rng = np.random.default_rng(99)

def t(d): return np.arange(int(round(d * SR))) / SR
def zeros(d): return np.zeros(int(round(d * SR)))
def lp(x, f, o=4):
    b, a = signal.butter(o, min(f, SR / 2 - 200) / (SR / 2), 'low'); return signal.lfilter(b, a, x)
def hp(x, f, o=2):
    b, a = signal.butter(o, f / (SR / 2), 'high'); return signal.lfilter(b, a, x)
def bp(x, lo, hi, o=4):
    b, a = signal.butter(o, [lo / (SR / 2), min(hi, SR / 2 - 200) / (SR / 2)], 'band'); return signal.lfilter(b, a, x)

def env(dur, a, d, s, r):
    n = int(round(dur * SR))
    na, nd, nr = [max(1, int(round(v * SR))) for v in (a, d, r)]
    ns = max(1, n - na - nd - nr)
    e = np.concatenate([np.linspace(0, 1, na), np.linspace(1, s, nd), np.full(ns, s), np.linspace(s, 0, nr)])
    return e[:n] if len(e) >= n else np.concatenate([e, np.zeros(n - len(e))])

def por(buf, x, quando, g=1.0):
    i = int(round(quando * SR))
    if i >= len(buf): return
    j = min(len(buf), i + len(x))
    buf[i:j] += x[:j - i] * g

# ---------- instrumentos
def alaude(f, dur):
    """Corda dedilhada (Karplus-Strong): som de alaude/harpa."""
    n = int(round(dur * SR))
    L = max(2, int(SR / f))
    buf = rng.standard_normal(L)
    buf = lp(buf, 4000, 2)
    y = np.zeros(n)
    for i in range(n):
        v = buf[i % L]
        y[i] = v
        buf[i % L] = 0.5 * (v + buf[(i + 1) % L]) * 0.994
    return y * env(dur, 0.002, 0.05, 0.55, min(0.3, dur * 0.5))

def baixo(f, dur, corte=500):
    tt = t(dur)
    x = signal.sawtooth(2 * np.pi * f * tt) * 0.6 + np.sin(2 * np.pi * f * tt) * 0.6
    return lp(x, corte) * env(dur, 0.004, 0.06, 0.6, 0.06)

def metal(f, dur, det=0.012):
    tt = t(dur)
    x = sum(signal.sawtooth(2 * np.pi * f * (1 + d) * tt + rng.random()) for d in (-det, 0, det))
    return bp(x, 150, 3000) * env(dur, 0.02, 0.1, 0.65, 0.18) * 0.4

def coro(freqs, dur):
    tt = t(dur)
    x = np.zeros(len(tt))
    for f in freqs:
        vib = 1 + 0.003 * np.sin(2 * np.pi * 5.0 * tt + rng.random() * 6)
        x += np.sin(2 * np.pi * f * np.cumsum(vib) / SR)
    return lp(x, 1700) * env(dur, 0.35, 0.4, 0.7, 0.6) / len(freqs)

def bumbo(dur=0.42, f0=150, f1=44, forca=1.0):
    tt = t(dur)
    f = f1 + (f0 - f1) * np.exp(-tt * 22)
    x = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-tt * 9)
    x += lp(rng.standard_normal(len(tt)), 700) * np.exp(-tt * 40) * 0.4
    return x * forca

def caixa(dur=0.22):
    tt = t(dur)
    x = bp(rng.standard_normal(len(tt)), 900, 7000) * np.exp(-tt * 26)
    x += np.sin(2 * np.pi * 190 * tt) * np.exp(-tt * 34) * 0.5
    return x * 0.8

def chimbal(dur=0.1, aberto=False):
    tt = t(dur if not aberto else dur * 3)
    return hp(rng.standard_normal(len(tt)), 7000) * np.exp(-tt * (45 if not aberto else 11)) * 0.3

def tom(f=130, dur=0.3):
    tt = t(dur)
    return np.sin(2 * np.pi * (f * (1 + 0.5 * np.exp(-tt * 14))) * tt) * np.exp(-tt * 10) * 0.8

def emendar(x, cross=2.0):
    n = int(round(cross * SR))
    y = x[:-n].copy()
    y[:n] = y[:n] * np.linspace(0, 1, n) + x[-n:] * np.linspace(1, 0, n)
    return y

def salvar(nome, x, vol=0.84, q='2'):
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
    print(nome, os.path.getsize(f'{OUT}/{nome}.ogg') // 1024, 'KB', round(len(x) / SR, 1), 's')

def nf(nome):
    base = {'C': 0, 'D': 2, 'E': 4, 'F': 5, 'G': 7, 'A': 9, 'B': 11}
    n = base[nome[0]]
    i = 1
    if nome[1] in '#b':
        n += 1 if nome[1] == '#' else -1; i = 2
    return 440.0 * 2 ** ((n - 9) / 12 + (int(nome[i:]) - 4))

# ------------------------------------------------------------------ explorar (aventura, 104 bpm)
def explorar():
    bpm, cps = 104.0, 16
    b = 60 / bpm
    D = cps * 4 * b + 3
    x = zeros(D)
    acordes = [('A2', ['A3', 'C4', 'E4']), ('F2', ['F3', 'A3', 'C4']),
               ('C2', ['C3', 'E3', 'G3']), ('G2', ['G2', 'B3', 'D4'])]
    melodia = [('E4', 1), ('A4', 1), ('C5', 0.5), ('B4', 0.5), ('A4', 1),
               ('G4', 1), ('E4', 1), (None, 1), ('F4', 1),
               ('A4', 0.5), ('C5', 0.5), ('A4', 1), ('G4', 1), ('E4', 1), ('D4', 1), (None, 1)]
    for c in range(cps):
        t0 = c * 4 * b
        nb, tri = acordes[c % 4]
        for k in range(8):                       # baixo em colcheias saltitantes
            f = nf(nb) * (2 if k in (3, 6) else 1)
            por(x, baixo(f, b * 0.42), t0 + k * b / 2, 0.42 if k % 2 == 0 else 0.26)
        for k, nt in enumerate(tri):             # arpejo de alaude
            por(x, alaude(nf(nt), b * 1.1), t0 + k * b, 0.3)
            por(x, alaude(nf(nt) * 2, b * 0.7), t0 + k * b + b / 2, 0.14)
        por(x, bumbo(), t0, 0.8)
        por(x, bumbo(0.36, 140, 46), t0 + 2.5 * b, 0.5)
        por(x, caixa(), t0 + 2 * b, 0.35)
        for k in range(8):
            por(x, chimbal(), t0 + k * b / 2, 0.5 if k % 2 == 0 else 0.28)
        if c % 4 == 3:
            por(x, tom(150, 0.26), t0 + 3 * b, 0.5)
            por(x, tom(120, 0.26), t0 + 3.5 * b, 0.6)
    # melodia por cima a partir do 5o compasso
    q = 4 * 4 * b
    for nt, dur in melodia * 2:
        if nt: por(x, alaude(nf(nt), b * dur * 1.3), q, 0.42)
        q += b * dur
    return emendar(x, 2.4)

# ------------------------------------------------------------------ combate (rapida, 146 bpm)
def combate():
    bpm, cps = 146.0, 24
    b = 60 / bpm
    D = cps * 4 * b + 3
    x = zeros(D)
    riff = ['A2', 'A2', 'C3', 'A2', 'D3', 'A2', 'C3', 'E3']
    lead = [('A4', .5), ('C5', .5), ('B4', .5), ('A4', .5), ('G4', 1), ('E4', 1),
            ('F4', .5), ('G4', .5), ('A4', 1), ('E4', .5), ('D4', .5), ('C4', 1)]
    for c in range(cps):
        t0 = c * 4 * b
        for k in range(8):
            f = nf(riff[(c * 8 + k) % len(riff)])
            por(x, baixo(f, b * 0.46, 620), t0 + k * b / 2, 0.5)
        por(x, bumbo(0.4, 160, 46), t0, 1.0)
        por(x, bumbo(0.34, 150, 46), t0 + 1.5 * b, 0.7)
        por(x, bumbo(0.34, 150, 46), t0 + 2.75 * b, 0.6)
        por(x, caixa(), t0 + b, 0.8)
        por(x, caixa(), t0 + 3 * b, 0.8)
        for k in range(16):                      # chimbal em semicolcheias
            por(x, chimbal(0.07), t0 + k * b / 4, 0.42 if k % 4 == 0 else 0.2)
        if c % 4 == 3:
            for k in range(4):
                por(x, tom(190 - k * 25, 0.22), t0 + 3 * b + k * b / 4, 0.55 + k * 0.1)
    q = 8 * 4 * b                                 # lead entra no 9o compasso
    for nt, dur in lead * 3:
        por(x, metal(nf(nt), b * dur * 1.15), q, 0.5)
        q += b * dur
    return emendar(x, 2.2)

# ------------------------------------------------------------------ chefe (epica, 132 bpm)
def chefe():
    bpm, cps = 132.0, 24
    b = 60 / bpm
    D = cps * 4 * b + 3
    x = zeros(D)
    prog = ['A2', 'A2', 'F2', 'G2']
    tema = [('A4', 1), ('E4', .5), ('F4', .5), ('G4', 1), ('E4', 1),
            ('F4', 1), ('C5', .5), ('B4', .5), ('A4', 2)]
    for c in range(cps):
        t0 = c * 4 * b
        nb = nf(prog[c % 4])
        for k in range(8):
            por(x, baixo(nb, b * 0.45, 560), t0 + k * b / 2, 0.5 if k % 2 == 0 else 0.3)
        for k in (0, 1.5, 2.5):                   # estocadas de metais
            por(x, metal(nb * 4, b * 0.55), t0 + k * b, 0.34)
        por(x, bumbo(0.5, 170, 42), t0, 1.1)
        por(x, bumbo(0.42, 160, 44), t0 + 1.5 * b, 0.75)
        por(x, bumbo(0.42, 160, 44), t0 + 3 * b, 0.7)
        por(x, caixa(), t0 + 2 * b, 0.9)
        for k in range(8):
            por(x, chimbal(0.09, aberto=(k == 7)), t0 + k * b / 2, 0.35)
        if c % 8 == 7:
            for k in range(8):
                por(x, tom(210 - k * 16, 0.2), t0 + 2 * b + k * b / 4, 0.4 + k * 0.08)
        if c % 4 == 0:
            por(x, coro([nf('A3'), nf('C4'), nf('E4')], b * 4), t0, 0.5)
    q = 8 * 4 * b
    for nt, dur in tema * 2:
        por(x, metal(nf(nt), b * dur * 1.1, 0.016), q, 0.46)
        por(x, coro([nf(nt)], b * dur), q, 0.2)
        q += b * dur
    return emendar(x, 2.4)

salvar('explorar', explorar(), vol=0.80, q='2')
salvar('combate', combate(), vol=0.86, q='2')
salvar('chefe', chefe(), vol=0.86, q='2')
