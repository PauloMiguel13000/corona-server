"""Gritos novos do monstro das Backrooms (sintetizados, originais) para o Paulo avaliar.
Gera OGG (para o pacote) e MP3 (para ouvir no celular) em /tmp/claude-0/sons/gritos."""
import numpy as np, subprocess, os, wave
from scipy import signal
SR = 44100
OUT = '/tmp/claude-0/sons/gritos'
os.makedirs(OUT, exist_ok=True)
rng = np.random.default_rng(23)


def t(d): return np.arange(int(round(d * SR))) / SR
def ruido(d): return rng.standard_normal(int(round(d * SR)))
def bp(x, lo, hi, o=2):
    b, a = signal.butter(o, [lo / (SR / 2), hi / (SR / 2)], 'band'); return signal.lfilter(b, a, x)
def lp(x, f, o=4):
    b, a = signal.butter(o, f / (SR / 2), 'low'); return signal.lfilter(b, a, x)
def hp(x, f, o=2):
    b, a = signal.butter(o, f / (SR / 2), 'high'); return signal.lfilter(b, a, x)
def fase(f): return 2 * np.pi * np.cumsum(f) / SR
def sat(x, k=3.0): return np.tanh(k * x) / np.tanh(k)


def env(tt, pontos):
    """envelope por pontos (tempo, nivel)"""
    ts, vs = zip(*pontos)
    return np.interp(tt, ts, vs)


def curva(tt, pontos):
    return env(tt, pontos)


def glote(f0, tt, jitter=0.02):
    """fonte de voz: dente de serra rica com tremida irregular"""
    j = 1 + jitter * lp(rng.standard_normal(len(tt)), 30) * 8
    return signal.sawtooth(fase(f0 * j))


def formantes(x, fs, larg=1.0):
    """filtro de vogal: soma de ressonadores"""
    y = np.zeros_like(x)
    for f, g, bw in fs:
        y += g * bp(x, max(40, f - bw * larg / 2), min(SR / 2 - 100, f + bw * larg / 2), 2)
    return y


def reverb(x, dur=1.4, mix=0.3, corte=6000):
    n = int(round(dur * SR)); ir = rng.standard_normal(n) * np.exp(-np.linspace(0, 6, n)); ir = lp(ir, corte, 2)
    y = np.concatenate([x, np.zeros(n)])
    w = signal.fftconvolve(x, ir)[:len(y)]
    if len(w) < len(y): w = np.concatenate([w, np.zeros(len(y) - len(w))])
    y /= np.max(np.abs(y)) + 1e-9; w /= np.max(np.abs(w)) + 1e-9
    return (1 - mix) * y + mix * w


def salvar(nome, x, vol=0.95):
    x = np.asarray(x, float); x -= np.mean(x); x /= np.max(np.abs(x)) + 1e-9; x *= vol
    nf = int(0.01 * SR); x[:nf] *= np.linspace(0, 1, nf); x[-nf * 4:] *= np.linspace(1, 0, nf * 4)
    w = OUT + '/' + nome + '.wav'
    with wave.open(w, 'wb') as f:
        f.setnchannels(1); f.setsampwidth(2); f.setframerate(SR); f.writeframes((x * 32767).astype(np.int16).tobytes())
    # normaliza a altura (todos com o mesmo "peso") e gera ogg + mp3
    filtro = 'loudnorm=I=-11:TP=-0.8:LRA=9'
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', w, '-af', filtro, '-ar', '44100', '-c:a', 'libvorbis', '-q:a', '6', OUT + '/' + nome + '.ogg'], check=True)
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', w, '-af', filtro, '-ar', '44100', '-c:a', 'libmp3lame', '-b:a', '192k', OUT + '/' + nome + '.mp3'], check=True)
    os.remove(w)


VOGAL_A = [(800, 1.0, 160), (1250, 0.8, 200), (2600, 0.5, 300), (3500, 0.3, 400)]
VOGAL_E = [(550, 0.8, 140), (1900, 1.0, 220), (2700, 0.6, 300), (3800, 0.3, 400)]
VOGAL_O = [(480, 1.0, 120), (850, 0.8, 160), (2400, 0.3, 300)]

# ---------------------------------------------------------------- A: rugido que vira grito
d = 2.8; tt = t(d)
f_rug = curva(tt, [(0, 70), (0.5, 95), (1.0, 85), (d, 60)])
rug = glote(f_rug, tt, 0.04) + 0.6 * glote(f_rug * 0.5, tt, 0.05)
rug = formantes(rug, VOGAL_O) * (1 + 0.7 * np.sin(fase(28 + 6 * np.sin(fase(np.full(len(tt), 1.3))))))  # chocalho na garganta
rug += 0.6 * lp(ruido(d), 900) * (1 + 0.8 * np.sin(fase(np.full(len(tt), 31))))
rug = sat(rug * curva(tt, [(0, 0), (0.08, 1), (0.7, 1), (1.3, 0.45), (d, 0)]), 3)
f_gri = curva(tt, [(0, 420), (0.35, 520), (0.75, 1350), (1.5, 1150), (2.2, 760), (d, 600)])
f_gri = f_gri * (1 + 0.025 * np.sin(fase(np.full(len(tt), 7.5))))
gri = sum(glote(f_gri * (1 + dt), tt, 0.03) / (1 + 0.3 * i) for i, dt in enumerate((0, 0.011, -0.013)))
gri = formantes(gri, VOGAL_A, 1.6) + 0.35 * bp(ruido(d), 1500, 6000)
gri = sat(gri * curva(tt, [(0, 0), (0.3, 0), (0.6, 1), (1.9, 0.9), (d, 0)]), 5)
salvar('grito_A_rugido_que_vira_grito', reverb(0.9 * rug + gri, 1.6, 0.28))

# ---------------------------------------------------------------- B: guincho rasgado (metalico, nada humano)
d = 2.2; tt = t(d)
f0 = curva(tt, [(0, 900), (0.12, 1500), (0.5, 1700), (1.2, 1300), (d, 820)])
vozes = sum(glote(f0 * (1 + dt) * (1 + 0.04 * np.sin(fase(np.full(len(tt), 5 + 3 * i)))), tt, 0.04)
            for i, dt in enumerate((0, 0.017, -0.021, 0.034)))
vozes = formantes(vozes, VOGAL_E, 1.8)
vozes *= np.sin(fase(np.full(len(tt), 87)))  # modulacao em anel: soa "inumano"
vozes += 0.5 * bp(ruido(d), 2500, 9000) * curva(tt, [(0, 1), (0.3, 0.5), (d, 0.2)])
x = sat(vozes * curva(tt, [(0, 0), (0.03, 1), (1.4, 0.85), (d, 0)]), 7)
x = np.round(x * 24) / 24  # esfarelado (bitcrush leve)
salvar('grito_B_guincho_rasgado', reverb(x, 1.2, 0.25))

# ---------------------------------------------------------------- C: urro gutural (grave, enorme)
d = 3.2; tt = t(d)
f0 = curva(tt, [(0, 62), (0.25, 78), (1.6, 66), (d, 44)])
fonte = glote(f0, tt, 0.06) + 0.8 * glote(f0 * 0.5, tt, 0.07) + 0.4 * glote(f0 * 2.01, tt, 0.05)
x = formantes(fonte, VOGAL_O, 1.4)
x *= 1 + 0.9 * np.sin(fase(curva(tt, [(0, 22), (d, 14)])))  # rosnado
x += 0.8 * lp(ruido(d), 1400) * (1 + np.sin(fase(np.full(len(tt), 19))))
agudo = formantes(glote(f0 * 14, tt, 0.03), VOGAL_A, 1.2) * curva(tt, [(0, 0), (0.2, 0.55), (1.8, 0.4), (d, 0)])
x = sat((x + agudo) * curva(tt, [(0, 0), (0.12, 1), (2.2, 0.9), (d, 0)]), 3.5)
x = lp(x, 7000) + 0.25 * hp(x, 80)
salvar('grito_C_urro_gutural', reverb(x, 2.0, 0.32, 4000))

# ---------------------------------------------------------------- D: grito curto para tocar enquanto corre
d = 1.0; tt = t(d)
f0 = curva(tt, [(0, 700), (0.08, 1250), (0.45, 1050), (d, 650)])
v = sum(glote(f0 * (1 + dt), tt, 0.04) for dt in (0, 0.015, -0.018))
v = formantes(v, VOGAL_A, 1.7) + 0.4 * bp(ruido(d), 1800, 7000)
r = formantes(glote(curva(tt, [(0, 90), (d, 70)]), tt, 0.05), VOGAL_O) * 0.8
x = sat((v + r) * curva(tt, [(0, 0), (0.02, 1), (0.55, 0.8), (d, 0)]), 6)
salvar('grito_D_curto_correndo', reverb(x, 0.9, 0.22))
print('ok')

# ---------------------------------------------------------------- C curto: o mesmo urro, curto, para repetir enquanto corre
d = 1.5; tt = t(d)
f0 = curva(tt, [(0, 66), (0.2, 80), (0.9, 70), (d, 52)])
fonte = glote(f0, tt, 0.06) + 0.8 * glote(f0 * 0.5, tt, 0.07) + 0.4 * glote(f0 * 2.01, tt, 0.05)
x = formantes(fonte, VOGAL_O, 1.4)
x *= 1 + 0.9 * np.sin(fase(curva(tt, [(0, 24), (d, 16)])))
x += 0.8 * lp(ruido(d), 1400) * (1 + np.sin(fase(np.full(len(tt), 19))))
agudo = formantes(glote(f0 * 14, tt, 0.03), VOGAL_A, 1.2) * curva(tt, [(0, 0), (0.12, 0.6), (1.0, 0.4), (d, 0)])
x = sat((x + agudo) * curva(tt, [(0, 0), (0.06, 1), (1.0, 0.9), (d, 0)]), 3.5)
x = lp(x, 7000) + 0.25 * hp(x, 80)
salvar('grito_C_curto_correndo', reverb(x, 1.0, 0.28, 4000))
print('ok curto')
