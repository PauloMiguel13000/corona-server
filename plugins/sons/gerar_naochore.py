"""Sons originais do Nao Chore (sintetizados, sem nada de terceiros). Gera OGG em /tmp/claude-0/sons/ogg3."""
import numpy as np, subprocess, os, wave
from scipy import signal
SR = 44100
OUT = '/tmp/claude-0/sons/ogg3'
os.makedirs(OUT, exist_ok=True)
rng = np.random.default_rng(11)


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
def fase(f): return 2 * np.pi * np.cumsum(f) / SR
def saw(f, tt=None):
    return signal.sawtooth(fase(f) if np.ndim(f) else 2 * np.pi * f * tt)
def formante(x, fs):
    return sum(bp(x, f * 0.85, f * 1.15, 2) * g for f, g in fs)
def coloca(dst, src, pos):
    p = int(round(pos * SR)); n = min(len(src), len(dst) - p)
    if n > 0: dst[p:p + n] += src[:n]
def salvar(nome, x, vol=0.92, fade=0.02):
    x = np.asarray(x, float); x -= np.mean(x); x /= np.max(np.abs(x)) + 1e-9; x *= vol
    nf = int(round(fade * SR)); x[:nf] *= np.linspace(0, 1, nf); x[-nf:] *= np.linspace(1, 0, nf)
    w = OUT + '/' + nome + '.wav'
    with wave.open(w, 'wb') as f:
        f.setnchannels(1); f.setsampwidth(2); f.setframerate(SR); f.writeframes((x * 32767).astype(np.int16).tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', w, '-c:a', 'libvorbis', '-q:a', '5', OUT + '/' + nome + '.ogg'], check=True)
    os.remove(w)
    print('ok', nome, round(len(x) / SR, 2), 's')


def voz(f0, d, vib=5.0, prof=0.03, fs=((600, 1.0), (1100, 0.6), (2500, 0.35)), rouca=0.3):
    """Voz sintetica: trem de pulsos (glote) com vibrato, formantes e rouquidao (ruido modulado)."""
    tt = t(d)
    f = f0 * (1 + prof * np.sin(2 * np.pi * vib * tt + rng.random() * 6))
    ph = fase(f) / (2 * np.pi)
    pulso = np.exp(-((ph % 1) / 0.08) ** 2)
    g = pulso * (1 + rouca * ruido(d) * (0.5 + 0.5 * np.sin(2 * np.pi * 31 * tt)))
    return formante(g, fs) + 0.15 * lp(g, 400)


# ---------------------------------------------------------------- respiracao (perto do ouvido, umida)
d = 4.0; tt = t(d); n = len(tt)
ciclo = 0.5 - 0.5 * np.cos(2 * np.pi * 0.45 * tt)            # inspira/expira
inspira = np.clip(np.sin(2 * np.pi * 0.45 * tt), 0, 1) ** 2
expira = np.clip(-np.sin(2 * np.pi * 0.45 * tt), 0, 1) ** 1.5
ar = bp(ruido(d), 300, 2600, 2) * inspira * 0.7 + bp(ruido(d), 150, 1200, 2) * expira
umido = bp(ruido(d), 1800, 5000, 2) * (rng.random(n) > 0.985) * expira * 2.5
tremor = 1 + 0.25 * np.sin(2 * np.pi * 13 * tt) * expira
x = (ar * tremor + umido) * (0.6 + 0.4 * ciclo)
salvar('respiracao', lp(x, 5000), 0.8, 0.1)

# ---------------------------------------------------------------- choro (solucos, mulher longe)
d = 4.5; x = np.zeros(len(t(d)))
pos = 0.0
for k in range(7):
    dur = 0.25 + 0.25 * rng.random()
    f0 = 330 + 120 * rng.random() + (60 if k % 3 == 0 else 0)
    v = voz(f0, dur, vib=6.5, prof=0.05, fs=((500, 1.0), (900, 0.7), (2600, 0.3)), rouca=0.5)
    e = env(len(v), 0.06, 0.12)
    e *= (1 - 0.5 * np.clip(np.sin(2 * np.pi * 9 * t(dur)), 0, 1))     # solucos
    coloca(x, v * e, pos)
    pos += dur + 0.12 + 0.3 * rng.random()
    if k == 3: pos += 0.5
funga = bp(ruido(d), 1500, 4000, 2) * (np.exp(-((t(d) - 2.3) / 0.08) ** 2) + np.exp(-((t(d) - 4.1) / 0.1) ** 2)) * 0.7
salvar('choro', reverb(x + funga, 2.2, 0.45, 3500), 0.85, 0.05)

# ---------------------------------------------------------------- grito do Vigia (grave, rasgado, cai de tom)
d = 1.8; tt = t(d); n = len(tt)
f0 = 210 * np.exp(-tt * 0.9) + 70 + 8 * np.sin(2 * np.pi * 11 * tt)
pulso = np.exp(-((fase(f0) / (2 * np.pi) % 1) / 0.07) ** 2)
g = pulso * (1 + 0.8 * ruido(d))
v = formante(g, ((420, 1.0), (1300, 0.8), (2900, 0.5), (4200, 0.3)))
sub = np.sin(fase(f0 / 2)) * 0.5
x = sat(v * 3 + sub, 5) * env(n, 0.03, 0.5)
x += bp(ruido(d), 2000, 7000, 2) * env(n, 0.01, 0.4) * 0.35
salvar('grito_vigia', reverb(x, 1.6, 0.3, 4000), 0.95)

# ---------------------------------------------------------------- grito da Chorona (agudo, sobe e quebra)
d = 2.2; tt = t(d); n = len(tt)
f0 = 520 + 420 * np.clip(tt / 0.6, 0, 1) - 150 * np.clip((tt - 1.3) / 0.6, 0, 1) + 20 * np.sin(2 * np.pi * 7 * tt)
quebra = (np.sin(2 * np.pi * 27 * tt) > 0.3) & (tt > 1.2)
f0 = np.where(quebra, f0 * 1.5, f0)
pulso = np.exp(-((fase(f0) / (2 * np.pi) % 1) / 0.1) ** 2)
g = pulso * (1 + 0.45 * ruido(d))
v = formante(g, ((800, 1.0), (1500, 0.7), (3100, 0.6), (5000, 0.3)))
x = sat(v * 2.5, 4) * env(n, 0.05, 0.6)
x += bp(ruido(d), 3000, 9000, 2) * env(n, 0.02, 0.5) * 0.25
salvar('grito_chorona', reverb(x, 2.4, 0.4, 6000), 0.95)

# ---------------------------------------------------------------- rosnado do Faminto (guturral, com dentes)
d = 2.4; tt = t(d); n = len(tt)
f0 = 38 + 14 * np.sin(2 * np.pi * 0.6 * tt) + 5 * np.sin(2 * np.pi * 4.3 * tt)
pulso = np.exp(-((fase(f0) / (2 * np.pi) % 1) / 0.05) ** 2)
g = pulso * (1 + 1.2 * ruido(d))
v = formante(g, ((250, 1.0), (700, 0.7), (1800, 0.4)))
x = sat(v * 4, 6) * env(n, 0.2, 0.5) * (0.7 + 0.3 * np.sin(2 * np.pi * 2.1 * tt))
dente = bp(ruido(d), 4000, 9000, 2) * (rng.random(n) > 0.995) * 3 * env(n, 0.3, 0.3)
salvar('rosnado', reverb(x + dente, 1.2, 0.25, 3000), 0.9, 0.05)

# ---------------------------------------------------------------- arranhao (unhas na madeira/vidro)
d = 1.6; tt = t(d); n = len(tt)
x = np.zeros(n)
for k in range(4):
    p0 = 0.1 + k * 0.36
    dur = 0.28
    m = np.exp(-((tt - p0 - dur / 2) / (dur / 2.2)) ** 2)
    f = 1800 + 900 * k % 3 + 600 * (tt - p0) / dur
    chio = np.sin(fase(f * (1 + 0.02 * np.sin(2 * np.pi * 120 * tt)))) * 0.4 + bp(ruido(d), 1200, 6000, 2) * 0.8
    x += chio * m * (rng.random(n) > 0.3)
salvar('arranhao', reverb(hp(x, 600), 0.9, 0.2, 7000), 0.8)

# ---------------------------------------------------------------- risada (crianca, longe, errada)
d = 2.6; x = np.zeros(len(t(d)))
pos = 0.15
for k in range(6):
    dur = 0.11 + 0.05 * rng.random()
    f0 = 620 + 40 * k + 30 * rng.random()
    v = voz(f0, dur, vib=9, prof=0.02, fs=((900, 1.0), (1700, 0.7), (3200, 0.5)), rouca=0.15)
    coloca(x, v * env(len(v), 0.02, 0.05), pos)
    pos += dur + 0.06 + (0.25 if k == 2 else 0)
x = reverb(x, 2.6, 0.55, 5000)
# desafina no fim (mais grave e lento)
y = np.interp(np.linspace(0, len(x) - 1, int(len(x) * 1.15)), np.arange(len(x)), x)
salvar('risada', y, 0.75, 0.05)

# ---------------------------------------------------------------- batidas na porta (3, pesadas)
d = 1.4; tt = t(d); n = len(tt)
x = np.zeros(n)
for p0 in (0.05, 0.42, 0.79):
    m = np.exp(-(np.clip(tt - p0, 0, None)) * 28) * (tt >= p0)
    x += (np.sin(fase(90 * np.exp(-(tt - p0) * 9) + 40)) * 1.2 + lp(ruido(d), 500) * 1.5) * m
salvar('batida_porta', reverb(x, 1.0, 0.3, 2500), 0.95, 0.01)

# ---------------------------------------------------------------- sussurro do nome ("nao chore") bem perto
d = 1.9; tt = t(d); n = len(tt)
ar = bp(ruido(d), 900, 4500, 2)
silabas = np.zeros(n)
for p0, dur, fs in ((0.15, 0.22, ((700, 1), (1200, 0.6))), (0.42, 0.18, ((400, 1), (2200, 0.5))), (0.75, 0.22, ((500, 1), (1800, 0.6))), (1.05, 0.35, ((350, 1), (2500, 0.4)))):
    m = np.exp(-((tt - p0 - dur / 2) / (dur / 2.4)) ** 2)
    silabas += formante(ar, fs) * m
x = silabas * 3 + ar * 0.08 * env(n, 0.1, 0.5)
salvar('sussurro_nome', lp(x, 6000), 0.8, 0.05)

# ---------------------------------------------------------------- estalo de galho + passos correndo (dia 5)
d = 1.5; tt = t(d); n = len(tt)
x = np.zeros(n)
for k, p0 in enumerate((0.0, 0.18, 0.34, 0.48, 0.6, 0.7, 0.78)):
    m = np.exp(-(np.clip(tt - p0, 0, None)) * 60) * (tt >= p0)
    x += (lp(ruido(d), 900) * 2 + bp(ruido(d), 2000, 5000, 2) * 0.6) * m * (1 + 0.15 * k)
estalo = bp(ruido(d), 1500, 7000, 2) * np.exp(-(np.clip(tt - 0.9, 0, None)) * 90) * (tt >= 0.9) * 3
salvar('correndo', x + estalo, 0.9, 0.01)

# ---------------------------------------------------------------- batimento cardiaco (1 ciclo, 0.9 s; o plugin repete e muda o tom)
d = 0.9; tt = t(d); n = len(tt)
x = np.zeros(n)
for p0, g in ((0.0, 1.0), (0.16, 0.7)):
    m = np.exp(-(np.clip(tt - p0, 0, None)) * 22) * (tt >= p0)
    x += np.sin(fase(55 * np.exp(-(tt - p0) * 6) + 35)) * m * g + lp(ruido(d), 180) * m * 0.6 * g
salvar('batimento', lp(x, 400), 0.95, 0.005)

# ---------------------------------------------------------------- zumbido grave da noite (10 s, loop suave)
d = 10.0; tt = t(d); n = len(tt)
base = sum(np.sin(fase(f * (1 + 0.004 * np.sin(2 * np.pi * (0.07 + k * 0.03) * tt)))) / (k + 1) for k, f in enumerate((41, 55, 61.5, 82)))
sopro = lp(ruido(d), 500) * (0.5 + 0.5 * np.sin(2 * np.pi * 0.11 * tt))
coro = sum(np.sin(fase(f + 2 * np.sin(2 * np.pi * 0.21 * tt + k))) for k, f in enumerate((164, 196, 247))) * 0.08 * (0.5 + 0.5 * np.sin(2 * np.pi * 0.05 * tt))
x = base * 0.6 + sopro * 0.5 + bp(coro, 120, 400, 2)
janela = np.minimum(1, np.minimum(tt / 1.5, (d - tt) / 1.5))
salvar('zumbido_noite', lp(x, 900) * janela, 0.7, 0.3)

# ---------------------------------------------------------------- caixinha de musica desafinada (nina), 9 s
d = 9.0; x = np.zeros(len(t(d)))
notas = [659, 784, 880, 784, 659, 523, 587, 659, 523, 494, 523, 440, 392, 440, 494, 523, 587, 659]
pos = 0.2
for k, f in enumerate(notas):
    desafina = 1 + (0.012 * np.sin(k * 1.3)) * (1 + k / 6) + (0.05 if k > 12 else 0)
    dur = 0.45 if k != 7 else 0.9
    tn = t(1.4)
    nota = (np.sin(2 * np.pi * f * desafina * tn) + 0.35 * np.sin(2 * np.pi * f * 2.0 * desafina * tn) + 0.12 * np.sin(2 * np.pi * f * 3.01 * tn)) * np.exp(-tn * 3.2)
    coloca(x, nota * (0.8 if k % 2 else 1.0), pos)
    pos += dur * (1 + 0.04 * k)      # vai ficando lento
# engasga no fim
x = x * (1 - 0.6 * np.clip((t(d) - 7.5) / 1.5, 0, 1) * (np.sin(2 * np.pi * 7 * t(d)) > 0))
salvar('nina', reverb(x, 2.5, 0.35, 4000), 0.75, 0.05)

# ---------------------------------------------------------------- estatica (radio quebrado), 1.1 s
d = 1.1; tt = t(d); n = len(tt)
x = bp(ruido(d), 400, 8000, 2) * (0.6 + 0.4 * (rng.random(n) > 0.3)) * env(n, 0.005, 0.25)
x += np.sign(np.sin(2 * np.pi * 50 * tt)) * 0.15 * env(n, 0.01, 0.3)
salvar('estatica', x, 0.9, 0.005)

# ---------------------------------------------------------------- maos agarrando (terra se mexendo + unhas), 1.3 s
d = 1.3; tt = t(d); n = len(tt)
terra = lp(ruido(d), 350) * np.exp(-tt * 3) * (1 + np.sin(2 * np.pi * 14 * tt))
unhas = bp(ruido(d), 1500, 5000, 2) * (rng.random(n) > 0.93) * env(n, 0.05, 0.4) * 2
grunhido = formante(np.exp(-((fase(np.full(n, 90.0)) / (2 * np.pi) % 1) / 0.08) ** 2) * (1 + ruido(d)), ((300, 1), (900, 0.5))) * env(n, 0.1, 0.5) * np.clip((tt - 0.3) / 0.3, 0, 1)
salvar('maos', reverb(terra * 2 + unhas + grunhido * 2, 0.8, 0.2, 3000), 0.95, 0.01)

# ---------------------------------------------------------------- porta rangendo e abrindo devagar, 2 s
d = 2.0; tt = t(d); n = len(tt)
f = 280 + 180 * np.sin(2 * np.pi * 0.7 * tt) + 40 * np.sin(2 * np.pi * 11 * tt)
rangido = (saw(f) * 0.35 + np.sin(fase(f * 2.01)) * 0.3) * (0.4 + 0.6 * (rng.random(n) > 0.15))
rangido = bp(rangido, 200, 3000, 2) * env(n, 0.15, 0.6)
tranco = lp(ruido(d), 300) * np.exp(-(np.clip(tt - 1.75, 0, None)) * 40) * (tt >= 1.75) * 3
salvar('porta_rangendo', reverb(rangido + tranco, 1.2, 0.3, 3500), 0.85, 0.02)

# ---------------------------------------------------------------- voce chorou (morte): coro grave + sino rachado, 3.5 s
d = 3.5; tt = t(d); n = len(tt)
coro = sum(np.sin(fase(f * (1 + 0.006 * np.sin(2 * np.pi * (0.5 + k * 0.2) * tt)))) for k, f in enumerate((82, 98, 110, 123, 165)))
sino = (np.sin(2 * np.pi * 523 * tt) + 0.6 * np.sin(2 * np.pi * 1047 * 1.01 * tt) + 0.3 * np.sin(2 * np.pi * 1572 * tt)) * np.exp(-tt * 1.6)
x = sat(coro * 0.35, 3) * env(n, 0.4, 1.2) + sino * 0.6 + lp(ruido(d), 200) * env(n, 0.2, 2.0) * 0.4
salvar('voce_chorou', reverb(x, 3.0, 0.45, 3000), 0.95, 0.05)

# ---------------------------------------------------------------- grito do Duplo (voz de gente invertida, dobrada, estala no fim)
d = 2.0; tt = t(d); n = len(tt)
f0 = 150 + 90 * np.clip(tt / 0.4, 0, 1) + 14 * np.sin(2 * np.pi * 9 * tt)
pulso = np.exp(-((fase(f0) / (2 * np.pi) % 1) / 0.09) ** 2)
g = pulso * (1 + 0.5 * ruido(d))
v1 = formante(g, ((500, 1.0), (1200, 0.7), (2600, 0.5), (3600, 0.3)))
# segunda voz uma oitava acima, levemente fora de tempo (duas gargantas)
pulso2 = np.exp(-((fase(f0 * 2.02) / (2 * np.pi) % 1) / 0.09) ** 2)
v2 = formante(pulso2 * (1 + 0.5 * ruido(d)), ((700, 1.0), (1700, 0.6), (3100, 0.5)))
x = sat(v1 * 2.6 + v2 * 1.4, 4) * env(n, 0.04, 0.55)
# invertido no comeco (a voz "entra ao contrario") e normal no fim
meio = int(0.6 * SR)
x[:meio] = x[:meio][::-1] * np.linspace(0.2, 1, meio)
# estalos de osso (pescoco) no fim
for k in range(5):
    p = int((1.3 + 0.1 * k) * SR)
    est = bp(ruido(0.04), 900, 4000, 2) * env(int(0.04 * SR), 0.002, 0.03)
    coloca(x, est * 1.6, p)
x += bp(ruido(d), 2500, 8000, 2) * env(n, 0.01, 0.45) * 0.3
salvar('grito_duplo', reverb(x, 2.0, 0.35, 5000), 0.95)
