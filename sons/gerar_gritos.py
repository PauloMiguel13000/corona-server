"""Gritos do Porteiro e da Sombra (15/11, Paulo: "o grito esta uma merda"). Voz sintetizada de verdade (pulso glotal
aspero + formantes de boca aberta), tres gargantas desafinadas, estouro grave na entrada e cauda de corredor.
  terror.grito_porteiro  rugido rasgado, grave, que sobe e quebra (2,2 s)
  terror.grito_sombra    guincho agudo que escorrega para baixo, com sussurro por cima (2,0 s)
Rodar: python3 gerar_gritos.py -> /tmp/claude-0/sons/gritos
"""
import os, subprocess, wave
import numpy as np
from scipy import signal

SR = 44100
OUT = '/tmp/claude-0/sons/gritos'
os.makedirs(OUT, exist_ok=True)


def bp(x, f, q):
    b, a = signal.iirpeak(f / (SR / 2), q)
    return signal.lfilter(b, a, x)


def lp(x, f):
    b, a = signal.butter(2, f / (SR / 2)); return signal.lfilter(b, a, x)


def hp(x, f):
    b, a = signal.butter(2, f / (SR / 2), 'high'); return signal.lfilter(b, a, x)


def garganta(D, f0, formantes, rng, aspereza, sub=0.0):
    n = int(D * SR); t = np.arange(n) / SR
    f = np.interp(t, [p[0] * D for p in f0], [p[1] for p in f0])
    # tremor e quebra da voz: ruido lento no tom + saltos
    jit = lp(rng.standard_normal(n), 35) * 14 * aspereza
    f = f * (1 + 0.05 * np.sin(2 * np.pi * 6.5 * t)) + jit * f / 300
    ph = 2 * np.pi * np.cumsum(f) / SR
    pulso = (signal.sawtooth(ph) * 0.7 + np.sign(np.sin(ph)) * 0.3)
    if sub: pulso += sub * signal.sawtooth(ph / 2)            # sub-harmonico: o "rasgado" do grito
    pulso *= 1 + aspereza * lp(rng.standard_normal(n), 900) * 0.5
    sopro = hp(rng.standard_normal(n), 1500) * 0.10 * aspereza
    x = pulso + sopro
    y = np.zeros(n)
    for fc, q, g in formantes: y += bp(x, fc, q) * g
    return np.tanh(y * 1.2)


def salvar(nome, y):
    y = y / np.max(np.abs(y)) * 0.95
    w = f'{OUT}/{nome}.wav'
    with wave.open(w, 'wb') as f:
        f.setnchannels(1); f.setsampwidth(2); f.setframerate(SR); f.writeframes((y * 32767).astype(np.int16).tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', w, '-c:a', 'libvorbis', '-q:a', '4', f'{OUT}/{nome}.ogg'], check=True)
    os.remove(w); print(nome, os.path.getsize(f'{OUT}/{nome}.ogg') // 1024, 'KB', round(len(y) / SR, 2), 's')


def cauda(x, seg=1.3, mix=0.28, semente=1):
    r = np.random.default_rng(semente); n = int(seg * SR); t = np.arange(n) / SR
    ir = lp(r.standard_normal(n), 3500) * np.exp(-t * 6.9 / seg); ir /= np.sqrt(np.sum(ir ** 2))
    return np.concatenate([x, np.zeros(n - 1)]) * (1 - mix) + signal.fftconvolve(x, ir) [:len(x) + n] * mix * 1.4


def estouro(D):
    n = int(D * SR); t = np.arange(n) / SR
    return np.sin(2 * np.pi * (95 * np.exp(-t * 9) + 38) * t) * np.exp(-t * 7) + lp(np.random.default_rng(5).standard_normal(n), 400) * np.exp(-t * 18) * 0.8


def porteiro():
    D = 2.2; rng = np.random.default_rng(11); n = int(D * SR); t = np.arange(n) / SR
    A_ = [(700, 5, 1.0), (1150, 6, 0.8), (2600, 7, 0.5), (3400, 8, 0.3)]          # "AAA" de boca escancarada
    y = np.zeros(n)
    for k, (des, amp) in enumerate(((1.0, 1.0), (0.985, 0.8), (1.52, 0.45))):
        f0 = [(0, 150 * des), (0.08, 330 * des), (0.45, 410 * des), (0.7, 300 * des), (0.74, 520 * des), (0.86, 360 * des), (1, 170 * des)]
        y += garganta(D, f0, A_, np.random.default_rng(20 + k), 1.0, sub=0.6) * amp
    env = np.interp(t, [0, 0.03, 0.2, 1.5, 1.85, D], [0, 1, 0.9, 1.0, 0.5, 0])
    y = y * env
    e = estouro(0.6); y[:len(e)] += e * 1.6
    return cauda(np.tanh(y * 1.4))


def sombra():
    D = 1.9; rng = np.random.default_rng(31); n = int(D * SR); t = np.arange(n) / SR
    I_ = [(900, 6, 0.9), (1900, 7, 1.0), (3100, 8, 0.8), (4200, 9, 0.4)]            # guincho "IÉÉ"
    y = np.zeros(n)
    for k, (des, amp) in enumerate(((1.0, 1.0), (1.012, 0.8), (0.5, 0.5))):
        f0 = [(0, 500 * des), (0.06, 1250 * des), (0.3, 1380 * des), (0.5, 1100 * des), (0.55, 1500 * des), (0.8, 900 * des), (1, 380 * des)]
        y += garganta(D, f0, I_, np.random.default_rng(40 + k), 0.8, sub=0.35) * amp
    env = np.interp(t, [0, 0.02, 1.3, 1.6, D], [0, 1, 1, 0.5, 0])
    sus = bp(rng.standard_normal(n), 2600, 3) * np.interp(t, [0, 0.4, D], [0.2, 0.8, 0.3]) * (0.6 + 0.4 * np.sin(2 * np.pi * 13 * t))
    y = y * env + sus * 0.35 * env
    e = estouro(0.5); y[:len(e)] += e * 1.2
    return cauda(np.tanh(y * 1.3), 1.6, 0.34, 2)


salvar('grito_porteiro', porteiro())
salvar('grito_sombra', sombra())
