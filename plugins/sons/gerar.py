"""Sons originais das Backrooms (sintetizados). Gera WAV e converte para OGG."""
import numpy as np, subprocess, os
from scipy import signal
SR = 44100
OUT = '/tmp/claude-0/sons/ogg'
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

# 1 zumbido das lampadas fluorescentes (loop de 3 s)
d = 3.0; tt = t(d)
z = sum((1 / (k ** 1.1)) * np.sin(2 * np.pi * 120 * k * tt + rng.uniform(0, 6)) for k in range(1, 14))
z += 0.25 * signal.square(2 * np.pi * 120 * tt) * 0.3
z *= 1 + 0.08 * np.sin(2 * np.pi * 0.7 * tt) + 0.04 * np.sin(2 * np.pi * 3.1 * tt)
z += 0.05 * bp(ruido(d), 2000, 6000)
salvar('zumbido', lp(z, 3500), 0.7, fade=0.005)

# 2 lampada piscando: estalos eletricos
d = 0.6; x = np.zeros(int(round(d * SR)))
for i in range(9):
    p = int(rng.uniform(0, 0.45) * SR); n = int(rng.uniform(0.004, 0.03) * SR)
    x[p:p + n] += bp(ruido(n / SR), 1500, 9000)[:len(x[p:p + n])] * rng.uniform(0.4, 1)
x += 0.5 * np.sin(2 * np.pi * 120 * t(d)) * env(len(x), 0.01, 0.3) * (rng.uniform(size=len(x)) > 0.3)
salvar('lampada', x, 0.8)

# 3 grito da Bacteria: rangido agudo distorcido que desce, com tremolo
d = 2.4; tt = t(d); n = len(tt)
f0 = 950 * np.exp(-tt * 0.55) + 180
voz = sum(saw(f0 * m * (1 + 0.012 * rng.standard_normal()), tt) / m for m in (1, 1.5, 2.02, 2.97))
voz = bp(voz, 300, 4200) + 0.6 * bp(voz, 2200, 3400)
voz *= 1 + 0.6 * np.sin(fase(18 + 10 * tt))
voz += 0.4 * bp(ruido(d), 800, 5000)
x = sat(voz * env(n, 0.03, 0.8), 4)
salvar('bacteria_grito', reverb(x, 1.2, 0.3))

# 4 estalos da Bacteria (ossos/estalidos)
d = 1.6; x = np.zeros(int(round(d * SR)))
for i in range(14):
    p = int(rng.uniform(0, 1.35) * SR); n = int(round(0.06 * SR))
    seg = bp(ruido(0.06), rng.uniform(400, 1200), rng.uniform(2500, 6000)) * np.exp(-np.linspace(0, 12, n))
    x[p:p + n] += seg[:len(x[p:p + n])] * rng.uniform(0.3, 1)
salvar('bacteria_estalo', reverb(x, 0.8, 0.25), 0.85)

# 5 grito do Sorridente: guincho muito agudo com vibrato e subida
d = 1.9; tt = t(d); n = len(tt)
f0 = 1400 + 900 * (1 - np.exp(-tt * 3)) + 60 * np.sin(2 * np.pi * 9 * tt)
g = sum(np.sin(fase(f0 * k * 1.003 ** k)) / k for k in range(1, 7)) + 0.5 * saw(f0 * 0.5, tt)
g = sat(g * (1 + 0.35 * np.sin(fase(23 + 0 * tt))), 5)
pre = np.concatenate([np.zeros(int(round(0.35 * SR))), np.zeros(0)])
sw = bp(ruido(0.35), 3000, 9000) * np.linspace(0, 1, int(round(0.35 * SR))) ** 3
x = np.concatenate([sw, g * env(n, 0.005, 0.6)])
salvar('sorridente_grito', reverb(x, 1.0, 0.25, 8000))

# 6 risada do Sorridente: pulsos curtos "hi-hi-hi" descendo
d = 1.7; x = np.zeros(int(round(d * SR)))
for i in range(7):
    p = int((0.08 + i * 0.2) * SR); n = int(round(0.13 * SR)); tt2 = t(0.13)
    f = 620 - i * 45
    som = (np.sin(2 * np.pi * f * tt2) + 0.5 * np.sin(2 * np.pi * 2 * f * tt2) + 0.3 * bp(ruido(0.13), 2500, 4500))
    som *= np.sin(np.pi * np.linspace(0, 1, n)) ** 2
    x[p:p + n] += som[:len(x[p:p + n])]
x = bp(x, 250, 6000)
salvar('sorridente_risada', reverb(x, 1.4, 0.4), 0.8)

# 7 uivo do Uivador: grave que sobe e desce, rouco
d = 3.2; tt = t(d); n = len(tt)
f0 = 140 + 260 * np.sin(np.pi * np.clip(tt / 2.6, 0, 1)) ** 1.5 + 8 * np.sin(2 * np.pi * 6 * tt)
u = sum(saw(f0 * k, tt) / (k ** 1.3) for k in (1, 2, 3, 4))
u = bp(u, 120, 2400) + 0.5 * bp(ruido(d) * (1 + np.sin(fase(f0))), 300, 1800)
x = sat(u * env(n, 0.25, 1.0), 3)
salvar('uivador_uivo', reverb(x, 2.2, 0.45, 3000))

# 8 estalidos do Uivador (ele "escuta")
d = 1.0; x = np.zeros(int(round(d * SR)))
for i in range(6):
    p = int((0.05 + i * 0.12 + rng.uniform(0, 0.04)) * SR); n = int(round(0.025 * SR))
    seg = np.sin(2 * np.pi * rng.uniform(1800, 2600) * t(0.025)) * np.exp(-np.linspace(0, 9, n))
    x[p:p + n] += seg[:len(x[p:p + n])]
salvar('uivador_estalo', reverb(x, 0.6, 0.3), 0.8)

# 9 alarme da corrida (Nivel !): sirene de dois tons
d = 2.0; tt = t(d)
f = np.where((tt * 2.5) % 1 < 0.5, 880, 660)
a = signal.square(fase(f)) * 0.6 + np.sin(fase(f * 2)) * 0.3
salvar('corrida_alarme', lp(a, 4000) * (0.85 + 0.15 * np.sin(2 * np.pi * 5 * tt)), 0.75, fade=0.005)

# 10 rugido do perseguidor: grave enorme distorcido
d = 2.8; tt = t(d); n = len(tt)
f0 = 75 + 35 * np.sin(np.pi * np.clip(tt / 2.2, 0, 1)) + 4 * np.sin(2 * np.pi * 11 * tt)
r = sum(saw(f0 * k * (1 + 0.007 * k), tt) / k for k in range(1, 9))
r = sat(lp(r, 1800) * 1.5, 4) + 0.6 * lp(ruido(d), 600) * env(n, 0.05, 1.5)
x = r * env(n, 0.08, 1.0)
salvar('corrida_rugido', reverb(x, 1.8, 0.35, 2500))

# 11 pego (susto): estouro + acorde dissonante + guincho
d = 1.6; tt = t(d); n = len(tt)
golpe = lp(ruido(d), 900) * np.exp(-tt * 9) * 2
acorde = sum(saw(f, tt) for f in (233, 247, 262, 349, 370)) * np.exp(-tt * 1.6)
guincho = np.sin(fase(2600 - 900 * tt + 80 * np.sin(2 * np.pi * 13 * tt))) * env(n, 0.01, 0.9) * 0.7
x = sat(golpe + 0.5 * bp(acorde, 150, 5000) + guincho, 3)
salvar('pego', reverb(x, 1.3, 0.3))

# 12 sussurros (sanidade baixa)
d = 3.5; x = np.zeros(int(round(d * SR)))
for i in range(16):
    p = int(rng.uniform(0, 3.1) * SR); dur = rng.uniform(0.08, 0.3); n = int(round(dur * SR))
    s = bp(ruido(dur), rng.uniform(900, 1800), rng.uniform(2800, 5500)) * np.sin(np.pi * np.linspace(0, 1, n)) ** 2
    x[p:p + n] += s[:len(x[p:p + n])] * rng.uniform(0.2, 0.9)
salvar('sussurros', reverb(x, 1.8, 0.5, 6000), 0.6)

# 13 grito distante (eco)
d = 2.0; tt = t(d); n = len(tt)
f0 = 700 + 300 * np.sin(np.pi * tt / 2) - 250 * tt
gr = sum(saw(f0 * k, tt) / k for k in (1, 2, 3)) * env(n, 0.1, 0.9)
x = lp(bp(gr, 300, 3000), 1500)
salvar('distante', reverb(np.concatenate([x, np.zeros(SR)]), 3.0, 0.7, 1800), 0.55)

# 14 saida abrindo: baque grave + acorde estranho subindo
d = 2.8; tt = t(d); n = len(tt)
baque = lp(ruido(d), 250) * np.exp(-tt * 6) * 3 + np.sin(2 * np.pi * 45 * tt) * np.exp(-tt * 4)
ac = sum(np.sin(fase(f * (1 + 0.15 * tt))) for f in (220, 277, 330, 415)) * np.sin(np.pi * np.clip(tt / d, 0, 1)) * 0.5
salvar('saida', reverb(baque + ac, 2.0, 0.4), 0.85)

# 15 batidas nas paredes (longe)
d = 2.2; x = np.zeros(int(round(d * SR)))
for i, p0 in enumerate((0.1, 0.55, 0.95, 1.6)):
    p = int(round(p0 * SR)); n = int(round(0.35 * SR))
    seg = lp(ruido(0.35), 180) * np.exp(-np.linspace(0, 10, n)) * 3 + np.sin(2 * np.pi * 60 * t(0.35)) * np.exp(-np.linspace(0, 8, n))
    x[p:p + n] += seg[:len(x[p:p + n])]
salvar('batidas', reverb(x, 1.6, 0.45, 1200), 0.8)
print('ok', sorted(os.listdir(OUT)))
