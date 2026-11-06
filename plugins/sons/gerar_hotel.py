"""Sons originais das Backrooms (sintetizados). Gera WAV e converte para OGG."""
import numpy as np, subprocess, os
from scipy import signal
SR = 44100
OUT = '/tmp/claude-0/sons/ogg2'
os.makedirs(OUT, exist_ok=True)
rng = np.random.default_rng(7)

def t(d): return np.arange(int(round(d * SR))) / SR
def ruido(d): return rng.standard_normal(int(round(d * SR)))
def bp(x, lo, hi, o=4):
    b, a = signal.butter(o, [lo / (SR / 2), hi / (SR / 2)], 'band'); return signal.lfilter(b, a, x)
def lp(x, f, o=4):
    b, a = signal.butter(o, f / (SR / 2), 'low'); return signal.lfilter(b, a, x)
def hp(x, f, o=2):
    b, a = signal.butter(o, f / (SR / 2), 'high'); return signal.lfilter(b, a, x)
def env(n, a, r, s=1.0):
    e = np.ones(n) * s; na = max(1, int(round(a * SR))); nr = max(1, int(round(r * SR)))
    e[:na] = np.linspace(0, s, na); e[-nr:] *= np.linspace(1, 0, nr) ** 1.5; return e
def sat(x, k=3.0): return np.tanh(k * x) / np.tanh(k)
def reverb(x, dur=1.6, mix=0.35, corte=5000):
    n = int(round(dur * SR)); ir = rng.standard_normal(n) * np.exp(-np.linspace(0, 7, n)); ir = lp(ir, corte, 2)
    y = np.concatenate([x, np.zeros(n)]); y /= np.max(np.abs(y)) + 1e-9
    w = signal.fftconvolve(x, ir)[:len(y)]
    if len(w) < len(y): w = np.concatenate([w, np.zeros(len(y) - len(w))])
    w /= np.max(np.abs(w)) + 1e-9
    return (1 - mix) * y + mix * w
def saw(f, tt):
    ph = 2 * np.pi * np.cumsum(f) / SR if np.ndim(f) else 2 * np.pi * f * tt
    return signal.sawtooth(ph)
def fase(f): return 2 * np.pi * np.cumsum(f) / SR
def salvar(nome, x, vol=0.92, fade=0.02):
    x = np.asarray(x, float); x -= np.mean(x); x /= np.max(np.abs(x)) + 1e-9; x *= vol
    nf = int(round(fade * SR)); x[:nf] *= np.linspace(0, 1, nf); x[-nf:] *= np.linspace(1, 0, nf)
    w = OUT + '/' + nome + '.wav'
    import wave
    with wave.open(w, 'wb') as f:
        f.setnchannels(1); f.setsampwidth(2); f.setframerate(SR); f.writeframes((x * 32767).astype(np.int16).tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', w, '-c:a', 'libvorbis', '-q:a', '5', OUT + '/' + nome + '.ogg'], check=True)
    os.remove(w)

def bitcrush(x, passos=8, red=6):
    y = np.round(x * passos) / passos
    return np.repeat(y[::red], red)[:len(x)]
def formante(x, fs):
    return sum(bp(x, f * 0.85, f * 1.15, 2) * g for f, g in fs)
def coloca(dst, src, pos):
    p = int(round(pos * SR)); n = min(len(src), len(dst) - p)
    if n > 0: dst[p:p + n] += src[:n]

# ===== HOTEL
# estrondo (Rush): rugido de fumaca que chega, passa e vai, com zumbido eletrico de lampada
d = 3.6; tt = t(d); n = len(tt)
chega = np.exp(-((tt - 1.6) / 0.9) ** 2)
ronco = lp(ruido(d), 260) * 3 + lp(ruido(d), 900) * chega
f0 = 48 + 30 * chega + 6 * np.sin(2 * np.pi * 7 * tt)
corpo = sum(saw(f0 * k * (1 + 0.011 * k), tt) / k for k in range(1, 10))
grito = sum(np.sin(fase(f * (1 + 0.25 * chega) + 25 * np.sin(2 * np.pi * 9 * tt))) for f in (520, 610, 790)) * chega ** 2
zum = np.sign(np.sin(2 * np.pi * 60 * tt)) * 0.25 * (rng.random(n) > 0.4)
x = sat(lp(corpo, 1500) * 0.9 + ronco * 0.8, 3) * (0.25 + chega) + 0.35 * bp(grito, 300, 4000) + 0.15 * lp(zum, 3000)
salvar('estrondo', reverb(x, 2.0, 0.3, 3000))

# retorno (Ambush): gritos que falham (bitcrush), varias vozes, voltando
d = 3.2; tt = t(d); n = len(tt)
v = np.zeros(n)
for f, k in ((330, 1.0), (415, 0.8), (500, 0.7), (180, 1.2)):
    fp = f * (1 + 0.5 * np.sin(2 * np.pi * 0.45 * tt + f)) + 40 * np.sin(2 * np.pi * 17 * tt)
    v += saw(fp, tt) * k
falha = (np.sin(2 * np.pi * 9 * tt) > -0.3) | (rng.random(n) > 0.995)
x = bitcrush(sat(bp(v, 200, 5000) * 0.6, 4), 6, 5) * falha + 0.6 * lp(ruido(d), 500) * env(n, 0.2, 1.0)
x *= np.clip(0.4 + np.sin(np.pi * tt / d), 0, 1)
salvar('retorno', reverb(x, 1.6, 0.35, 4000))

# figura: respiracao funda + rosnado + estalos de dentes
d = 2.8; tt = t(d); n = len(tt)
resp = bp(ruido(d), 150, 900) * (0.5 + 0.5 * np.sin(2 * np.pi * 0.6 * tt - 1.2)) ** 2 * 1.4
f0 = 52 + 8 * np.sin(2 * np.pi * 0.7 * tt)
ros = formante(sum(saw(f0 * k, tt) / k for k in range(1, 12)), [(320, 1), (780, 0.7), (2400, 0.25)]) * (0.3 + 0.7 * np.clip(np.sin(np.pi * tt / d), 0, 1))
x = resp + sat(ros * 1.6, 3)
for p0 in (0.4, 0.55, 0.62, 1.7, 1.78, 2.3):
    clk = hp(ruido(0.03), 1500) * np.exp(-np.linspace(0, 8, int(0.03 * SR)))
    coloca(x, clk * 1.4, p0)
salvar('figura', reverb(x, 1.8, 0.35, 3500))

# perseguidor (Seek): gosma borbulhando + rugido molhado
d = 2.6; tt = t(d); n = len(tt)
bolha = np.zeros(n)
for i in range(70):
    dur = rng.uniform(0.02, 0.07); m = int(dur * SR); fb = rng.uniform(250, 900)
    b = np.sin(fase(np.linspace(fb, fb * 1.8, m))) * np.sin(np.pi * np.linspace(0, 1, m)) ** 2
    coloca(bolha, b * rng.uniform(0.3, 1), rng.uniform(0, d - 0.1))
f0 = 90 + 40 * np.sin(np.pi * np.clip(tt / 2, 0, 1))
rug = sat(formante(sum(saw(f0 * k, tt) / k for k in range(1, 9)), [(500, 1), (1100, 0.6)]) * 1.5, 3) * env(n, 0.3, 1.0)
x = 0.8 * bolha + rug + 0.5 * lp(ruido(d), 400) * env(n, 0.1, 1.2)
salvar('perseguidor', reverb(x, 1.6, 0.35, 3000))

# psst
d = 0.75; x = np.zeros(int(d * SR))
coloca(x, lp(ruido(0.025), 2000) * np.exp(-np.linspace(0, 6, int(0.025 * SR))), 0.02)
s_ = bp(ruido(0.38), 3800, 9500) * env(int(0.38 * SR), 0.06, 0.12)
coloca(x, s_ * 0.9, 0.06)
coloca(x, hp(ruido(0.02), 3000) * np.exp(-np.linspace(0, 8, int(0.02 * SR))) * 0.6, 0.47)
salvar('psst', reverb(x, 0.5, 0.15, 9000), 0.8)

# sussurro_ataque (Screech): guincho agudo + mordida
d = 1.3; tt = t(d); n = len(tt)
gu = sum(np.sin(fase(f + 400 * np.sin(2 * np.pi * 6 * tt) + 900 * tt)) for f in (2300, 2900, 3700)) * env(n, 0.005, 0.7)
mord = lp(ruido(d), 1500) * np.exp(-np.maximum(tt - 0.55, 0) * 12) * (tt > 0.55) * 2.5
x = sat(gu * 0.6 + mord, 3)
salvar('sussurro_ataque', reverb(x, 0.9, 0.25))

# passos (madeira rangendo, alguem andando)
d = 2.2; x = np.zeros(int(d * SR))
for p0 in (0.1, 0.65, 1.2, 1.75):
    m = int(0.25 * SR); tt2 = t(0.25)
    passo = lp(ruido(0.25), 300) * np.exp(-tt2 * 18) * 2 + np.sin(2 * np.pi * 70 * tt2) * np.exp(-tt2 * 20)
    coloca(x, passo, p0)
    cr = saw(380 + 160 * np.sin(np.pi * tt2 / 0.25) + 30 * rng.standard_normal(m).cumsum() / 200, tt2) * np.sin(np.pi * tt2 / 0.25) * 0.12
    coloca(x, bp(cr, 400, 2500), p0 + 0.05)
salvar('passos', reverb(x, 1.2, 0.3, 2500), 0.8)

# ===== BACKROOMS novos
# cao_rosnado
d = 2.2; tt = t(d); n = len(tt)
f0 = 78 + 10 * np.sin(2 * np.pi * 0.5 * tt)
am = 0.55 + 0.45 * np.sin(2 * np.pi * 23 * tt) ** 2
ros = formante(sum(saw(f0 * k, tt) / k for k in range(1, 14)), [(350, 1), (850, 0.8), (2200, 0.3)]) * am
x = sat(ros * 2, 3) * env(n, 0.15, 0.6) + 0.3 * bp(ruido(d), 200, 1200) * am
salvar('cao_rosnado', reverb(x, 1.0, 0.25, 3000))

# cao_ataque: uivo-grito que sobe
d = 1.8; tt = t(d); n = len(tt)
f0 = 260 + 700 * np.sin(np.pi * np.clip(tt / 1.3, 0, 1)) + 30 * np.sin(2 * np.pi * 11 * tt)
x = sat(formante(sum(saw(f0 * k, tt) / k for k in range(1, 8)), [(900, 1), (2600, 0.6)]) * 2, 4) * env(n, 0.03, 0.6)
x += 0.4 * bp(ruido(d), 1500, 6000) * env(n, 0.01, 1.0)
salvar('cao_ataque', reverb(x, 1.4, 0.3))

# festeiro_risada: risadinhas "ha-ha" que descem + lingua de sogra desafinada no fim
d = 3.0; tt = t(d); n = len(tt); x = np.zeros(n)
for i in range(9):
    p0 = 0.08 + i * 0.16 + rng.uniform(-0.02, 0.02); m = int(0.13 * SR); tt2 = t(0.13)
    f0 = 330 - i * 14 + 40 * np.sin(np.pi * tt2 / 0.13)
    sil = formante(saw(f0, tt2) + 0.3 * ruido(0.13), [(850, 1), (1250, 0.8), (2700, 0.3)]) * np.sin(np.pi * tt2 / 0.13) ** 1.5
    coloca(x, sil * (1 - i * 0.06), p0)
tt3 = t(0.9); m = len(tt3)
buz = sum(saw(f * (1 - 0.18 * tt3 / 0.9), tt3) for f in (440, 452, 466)) * (np.sign(np.sin(2 * np.pi * 70 * tt3)) * 0.5 + 0.5) * env(m, 0.03, 0.4)
coloca(x, bp(buz, 300, 3500) * 0.35, 1.85)
salvar('festeiro_risada', reverb(x, 1.6, 0.4, 5000))
print('ok', sorted(os.listdir(OUT)))
