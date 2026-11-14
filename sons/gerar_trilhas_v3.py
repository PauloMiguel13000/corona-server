"""Trilhas das masmorras v3 (14/11, Paulo: "diferenciar a musica de todas, esta muito igual").

Cada TEMA agora e uma musica propria: escala, compasso (3/4, 6/8, 7/8, 5/4...), andamento, instrumentos, levada
e melodia composta (frase A A' B A) sao diferentes. Cada tema sai em duas versoes que emendam em loop:
  <tema>.ogg       explorando (a musica do lugar)
  <tema>_luta.ogg  com monstro por perto (a MESMA musica, com bateria e baixo de luta)
e o Herobrine ganhou uma trilha so dele (herobrine.ogg): coro sombrio, poderes magicos (raios, brilhos, zumbidos
ao contrario), coracao e tambores pesados. Tudo sintetizado aqui (numpy/scipy/ffmpeg), nada de terceiros.

Rodar: python3 gerar_trilhas_v3.py [tema ...]   -> /tmp/claude-0/sons/v3/*.ogg
"""
import numpy as np, subprocess, os, wave, sys
from scipy import signal

SR = 32000
OUT = '/tmp/claude-0/sons/v3'
os.makedirs(OUT, exist_ok=True)

def T(d): return np.arange(int(round(d * SR))) / SR
def Z(d): return np.zeros(int(round(d * SR)))
def lp(x, f, o=2):
    b, a = signal.butter(o, min(f, SR / 2 - 300) / (SR / 2), 'low'); return signal.lfilter(b, a, x)
def hp(x, f, o=2):
    b, a = signal.butter(o, max(20, f) / (SR / 2), 'high'); return signal.lfilter(b, a, x)
def bp(x, lo, hi, o=2):
    b, a = signal.butter(o, [lo / (SR / 2), min(hi, SR / 2 - 300) / (SR / 2)], 'band'); return signal.lfilter(b, a, x)

def env(dur, a, d, s, r):
    n = int(round(dur * SR)); na, nd, nr = [max(1, int(round(v * SR))) for v in (a, d, r)]
    ns = max(1, n - na - nd - nr)
    e = np.concatenate([np.linspace(0, 1, na), np.linspace(1, s, nd), np.full(ns, s), np.linspace(s, 0, nr)])
    return e[:n] if len(e) >= n else np.concatenate([e, np.zeros(n - len(e))])

def por(buf, x, q, g=1.0):
    i = int(round(q * SR))
    if i >= len(buf) or i < 0: return
    j = min(len(buf), i + len(x)); buf[i:j] += x[:j - i] * g

def mf(m): return 440.0 * 2 ** ((m - 69) / 12)   # midi -> Hz

# ------------------------------------------------------------------ instrumentos
class R:  # gerador aleatorio do tema (cada tema tem a sua semente)
    g = np.random.default_rng(1)

def pluck(f, d, brilho=3500, dec=0.996):
    n = int(round(d * SR)); L = max(2, int(round(SR / f)))
    x = lp(R.g.standard_normal(min(n, L)), brilho, 2); x = np.concatenate([x, np.zeros(max(0, n - len(x)))])
    a = np.zeros(L + 2); a[0] = 1; a[L] = -dec / 2; a[L + 1] = -dec / 2
    return signal.lfilter([1.0], a, x) * env(d, .002, .03, .7, min(.3, d * .4)) * .55

def harpa(f, d): return pluck(f, max(d, 1.2), 5000, 0.998)
def koto(f, d):
    x = pluck(f, max(d, 0.9), 6000, 0.994); tt = T(len(x) / SR)
    return x * (1 + 0.15 * np.sin(2 * np.pi * 5.5 * tt) * (tt > 0.25))
def oud(f, d): return pluck(f, max(d, .5), 2600, 0.990) * 1.1
def banjo(f, d): return hp(pluck(f, max(d, .4), 7000, 0.985), 300) * 1.2

def caixinha(f, d):  # caixinha de musica / celesta
    tt = T(max(d, 1.4))
    x = np.sin(2 * np.pi * f * tt) * np.exp(-tt * 3) + .35 * np.sin(2 * np.pi * f * 4.02 * tt) * np.exp(-tt * 9)
    return x * .45

def sino(f, d):
    tt = T(max(d, 2.5)); x = np.zeros_like(tt)
    for k, g in ((1, 1), (2.76, .5), (5.4, .25), (8.93, .12)): x += g * np.sin(2 * np.pi * f * k * tt) * np.exp(-tt * (1 + k * .5))
    return x * .4

def marimba(f, d):
    tt = T(max(d, .6))
    return (np.sin(2 * np.pi * f * tt) * np.exp(-tt * 6) + .3 * np.sin(2 * np.pi * f * 3.9 * tt) * np.exp(-tt * 18)) * .6

def flauta(f, d):
    tt = T(d); vib = 1 + .006 * np.sin(2 * np.pi * 5.3 * tt) * np.clip(tt * 3, 0, 1)
    x = np.sin(2 * np.pi * f * np.cumsum(vib) / SR) + .15 * np.sin(4 * np.pi * f * tt)
    x += lp(R.g.standard_normal(len(tt)), 3000) * .04
    return x * env(d, .05, .1, .8, min(.15, d * .3)) * .5

def oboe(f, d):  # palheta (fagote/oboe) – nasal
    tt = T(d); vib = 1 + .004 * np.sin(2 * np.pi * 5 * tt)
    ph = 2 * np.pi * f * np.cumsum(vib) / SR
    x = signal.square(ph, .25) * .5 + signal.sawtooth(ph) * .5
    return bp(x, f * .8, 2600) * env(d, .03, .1, .8, min(.12, d * .3)) * .5

def metais(f, d, abre=2400):  # trompete/trompa
    tt = T(d); x = sum(signal.sawtooth(2 * np.pi * f * (1 + dt) * tt + R.g.random() * 6) for dt in (-.004, 0, .004)) / 3
    y = lp(x, abre) * np.clip(tt / .05, 0, 1)
    return y * env(d, .04, .12, .8, min(.18, d * .3)) * .55

def cordas_st(f, d):  # cordas staccato
    tt = T(min(d, .22)); x = sum(signal.sawtooth(2 * np.pi * f * (1 + dt) * tt) for dt in (-.006, 0, .006)) / 3
    return lp(x, 2600) * env(len(tt) / SR, .005, .05, .5, .08) * .5

def violino(f, d):
    tt = T(d); vib = 1 + .007 * np.sin(2 * np.pi * 5.8 * tt) * np.clip(tt * 2, 0, 1)
    x = signal.sawtooth(2 * np.pi * f * np.cumsum(vib) / SR)
    return bp(x, 300, 3800) * env(d, .08, .1, .85, min(.2, d * .3)) * .42

def cravo(f, d):  # cravo (harpsichord)
    x = pluck(f, max(d, .7), 9000, 0.993) + .5 * pluck(f * 2, max(d, .7), 9000, 0.99)
    return hp(x, 200) * .8

def quadrado(f, d, pw=.5):  # synth 8-bit / arpejo
    tt = T(d); return lp(signal.square(2 * np.pi * f * tt, pw), 3500) * env(d, .003, .05, .6, min(.06, d * .3)) * .3

def fm(f, d, idx=3, razao=1.41):  # sino FM magico
    tt = T(max(d, 1.2)); e = np.exp(-tt * 2.2)
    return np.sin(2 * np.pi * f * tt + idx * e * np.sin(2 * np.pi * f * razao * tt)) * e * .45

def acordeao(f, d):
    tt = T(d); x = np.zeros_like(tt)
    for dt in (-.003, .003): x += signal.square(2 * np.pi * f * (1 + dt) * tt, .4)
    return bp(x, 250, 3000) * (1 + .1 * np.sin(2 * np.pi * 6 * tt)) * env(d, .03, .05, .85, .06) * .28

def pad(freqs, d, corte=1400, det=.006, forma='saw'):
    tt = T(d); x = np.zeros(len(tt))
    for f in freqs:
        for dt in (-det, 0, det):
            ph = 2 * np.pi * f * (1 + dt) * tt + R.g.random() * 6
            x += signal.sawtooth(ph) if forma == 'saw' else np.sin(ph) + .3 * np.sin(2 * ph)
    return lp(x, corte) * env(d, .5, .3, .9, .6) * .25 / len(freqs)

def orgao(freqs, d, corte=1800):
    tt = T(d); x = np.zeros(len(tt))
    for f in freqs:
        for k, g in ((.5, .6), (1, 1), (2, .5), (3, .3), (4, .2)): x += g * np.sin(2 * np.pi * f * k * tt + R.g.random())
    return lp(x, corte) * env(d, .08, .2, .85, .3) / (len(freqs) * 2.2)

def coro(freqs, d, vog=800):
    tt = T(d); x = np.zeros(len(tt))
    for f in freqs:
        for v in range(3):
            vib = 1 + .004 * np.sin(2 * np.pi * (4.6 + v * .4) * tt + R.g.random() * 6) + (v - 1) * .003
            x += signal.sawtooth(2 * np.pi * f * np.cumsum(vib) / SR)
    x = bp(x, vog * .6, vog * 1.6) + .5 * bp(x, 2300, 3200)
    return x * env(d, .4, .3, .85, .6) * .35 / len(freqs)

# percussao
def bumbo(f0=150, f1=45, d=.45, g=1.0):
    tt = T(d); f = f1 + (f0 - f1) * np.exp(-tt * 25)
    return (np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-tt * 8) + lp(R.g.standard_normal(len(tt)), 900) * np.exp(-tt * 50) * .3) * g
def caixa(corpo=190, d=.22):
    tt = T(d); return (bp(R.g.standard_normal(len(tt)), 1200, 8000) * np.exp(-tt * 24) + .5 * np.sin(2 * np.pi * corpo * tt) * np.exp(-tt * 30)) * .7
def chimbal(d=.07, aberto=False):
    tt = T(d * (4 if aberto else 1)); return hp(R.g.standard_normal(len(tt)), 7000) * np.exp(-tt * (10 if aberto else 60)) * .16
def tom(f=120, d=.4):
    tt = T(d); return np.sin(2 * np.pi * f * (1 + .6 * np.exp(-tt * 12)) * tt) * np.exp(-tt * 7) * .8
def taiko(d=.7):
    tt = T(d); f = 60 + 70 * np.exp(-tt * 18)
    return (np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-tt * 5) + lp(R.g.standard_normal(len(tt)), 400) * np.exp(-tt * 25) * .5) * 1.1
def darbuka(agudo=False):
    tt = T(.2); f = 420 if agudo else 210
    return (np.sin(2 * np.pi * f * tt) * np.exp(-tt * (30 if agudo else 18)) + bp(R.g.standard_normal(len(tt)), 2000, 6000) * np.exp(-tt * 60) * .4) * .6
def madeira(f=1100):
    tt = T(.12); return np.sin(2 * np.pi * f * tt) * np.exp(-tt * 55) * .5
def tique(): return hp(madeira(2600), 1500) * .8
def bigorna():
    tt = T(.6); return sum(np.sin(2 * np.pi * f * tt) * np.exp(-tt * (5 + f / 500)) for f in (530, 1190, 2330, 3590)) * .22
def chocalho():
    tt = T(.12); return hp(R.g.standard_normal(len(tt)), 4000) * np.exp(-tt * 30) * .25
def timpano(f=70, d=1.0):
    tt = T(d); return (np.sin(2 * np.pi * f * tt) + .4 * np.sin(2 * np.pi * f * 1.5 * tt)) * np.exp(-tt * 3) * .9
def prato(d=1.6):
    tt = T(d); return hp(R.g.standard_normal(len(tt)), 3500) * np.exp(-tt * 2.2) * .22
def trovao(d=3.0):
    tt = T(d); x = lp(R.g.standard_normal(len(tt)), 220) * (np.exp(-tt * 1.2) * (1 + .6 * np.sin(2 * np.pi * 3 * tt)))
    return (x + hp(R.g.standard_normal(len(tt)), 2000) * np.exp(-tt * 18) * .5) * 1.4
def rufo(d, f=2.0):
    x = Z(d); q = 0.0
    while q < d: por(x, caixa(230, .08), q, .25 + .5 * q / d); q += 1 / (14 * f)
    return x

# ------------------------------------------------------------------ efeitos (reverb, eco)
def reverb(x, seg=1.6, mix=.25, cor=3500):
    n = int(seg * SR); tt = np.arange(n) / SR
    ir = lp(R.g.standard_normal(n), cor) * np.exp(-tt * 6.9 / seg); ir[0] = 0
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
    w = signal.fftconvolve(x, ir)[:len(x)]
    return x * (1 - mix) + w * mix * 1.3

def eco(x, t=.375, fb=.35, n=4):
    y = x.copy(); k = int(t * SR)
    for i in range(1, n + 1):
        if k * i < len(x): y[k * i:] += x[:-k * i] * fb ** i
    return y

# ------------------------------------------------------------------ teoria
MODOS = {'eolio': [0, 2, 3, 5, 7, 8, 10], 'dorico': [0, 2, 3, 5, 7, 9, 10], 'frigio': [0, 1, 3, 5, 7, 8, 10],
         'harm': [0, 2, 3, 5, 7, 8, 11], 'frigdom': [0, 1, 4, 5, 7, 8, 10], 'jonio': [0, 2, 4, 5, 7, 9, 11],
         'mixo': [0, 2, 4, 5, 7, 9, 10], 'lidio': [0, 2, 4, 6, 7, 9, 11], 'melod': [0, 2, 3, 5, 7, 9, 11],
         'penta': [0, 3, 5, 7, 10], 'japones': [0, 1, 5, 7, 8], 'inteiro': [0, 2, 4, 6, 8, 10], 'dim': [0, 2, 3, 5, 6, 8, 9, 11]}

def grau(raiz, modo, g, oit=0):
    m = MODOS[modo]; n = len(m)
    return raiz + m[g % n] + 12 * (g // n + oit)

def triade(raiz, modo, g):
    return [grau(raiz, modo, g), grau(raiz, modo, g + 2), grau(raiz, modo, g + 4)]

def compor(cfg, rng):
    """Melodia de 8 compassos (A A' B A''), em graus da escala, com ritmos do tema."""
    bpc = cfg['beats']; ritmos = cfg['ritmos']; prog = cfg['prog']
    def frase(base, alto, fim):
        notas = []; pos = 0.0; g = base
        rit = ritmos[rng.integers(len(ritmos))]
        for c in range(2):
            for dur in rit:
                if pos >= (c + 1) * bpc: break
                acorde = prog[(c) % len(prog)]
                passo = rng.choice([-2, -1, -1, 1, 1, 2, 0, 3, -3], p=[.08, .2, .1, .2, .14, .1, .06, .06, .06])
                g = int(np.clip(g + passo, alto - 4, alto + 4))
                if rng.random() < .35: g = acorde + rng.choice([0, 2, 4]) + (7 if alto > 7 else 0)
                notas.append((g, dur)); pos += dur
            while pos < (c + 1) * bpc: notas.append((None, (c + 1) * bpc - pos)); pos = (c + 1) * bpc
        if fim is not None:
            g2, d2 = notas[-1]; notas[-1] = (fim, d2)
        return notas
    A = frase(cfg.get('inicio', 4), 5, None)
    A2 = A[:-2] + [(A[-2][0], A[-2][1]), (0 if rng.random() < .5 else 2, A[-1][1])]
    B = frase(7, 9, 4)
    A3 = A[:-1] + [(0, A[-1][1])]
    return A + A2 + B + A3


# ------------------------------------------------------------------ motivos das trilhas antigas (v1, as que ja tocavam no jogo)
# 14/11 (Paulo: "as primeiras musicas que voce fez, consegue fazer algo associado?"): cada tema novo comeca a frase
# com a MELODIA da trilha antiga da familia dele (adaptada a escala, ao compasso e aos instrumentos do tema novo),
# e responde com uma parte nova. Assim a trilha toda tem a mesma "cara" do Corona, mas cada masmorra soa diferente.
def _midi(n):
    base = {'C': 0, 'D': 2, 'E': 4, 'F': 5, 'G': 7, 'A': 9, 'B': 11}
    k = base[n[0]]; i = 1
    if n[1] in '#b': k += 1 if n[1] == '#' else -1; i = 2
    return 12 * (int(n[i:]) + 1) + k
M1 = [('E4', 1), ('A4', 1), ('C5', .5), ('B4', .5), ('A4', 1), ('G4', 1), ('E4', 1), (None, 1),
      ('F4', 1), ('A4', .5), ('C5', .5), ('A4', 1), ('G4', 1), ('E4', 1), ('D4', 1), (None, 1)]
M2 = [('B4', .5), ('E5', .5), ('D5', 1), ('B4', 1), ('G4', 1), ('A4', .5), ('B4', .5), ('G4', 1),
      ('E4', 1), ('D4', 1), ('E4', 2), (None, 4)]
M3 = [('D5', .5), ('C5', .5), ('A4', 1), ('F4', 1), ('D4', 1), ('A4', .5), ('C5', .5), ('D5', 1), ('A4', 2), (None, 1), ('D5', 1), ('F5', 1), ('E5', 1), ('C5', 1), ('D5', 3)]
M4 = [('E5', .5), ('F5', .5), ('E5', .5), ('D5', .5), ('C5', 1), ('B4', 1), ('E4', .5), ('F4', .5),
      ('G4', 1), ('E4', 2), ('F4', .5), ('E4', .5), ('G4', 1), ('B4', 1), ('C5', 1), ('B4', 1), ('E4', 2)]
M5 = [('F#4', 1), ('B4', 1), ('C#5', .5), ('B4', .5), ('A4', 1), ('F#4', 1), ('D4', 1), ('E4', 1), ('B4', 2),
      (None, 1), ('D5', 1), ('C#5', 1), ('B4', 1), ('A4', 1), ('B4', 3)]
ANTIGAS = {'M1': (M1, 69, 'eolio'), 'M2': (M2, 64, 'eolio'), 'M3': (M3, 62, 'eolio'), 'M4': (M4, 64, 'frigio'), 'M5': (M5, 71, 'eolio')}

def motivo(nome_m, cfg):
    """Converte a melodia antiga em graus da escala do tema novo e estica para caber em 4 compassos."""
    mel, raiz, modo = ANTIGAS[nome_m]
    esc = MODOS[modo]; total = sum(d for _, d in mel)
    alvo = 4 * cfg['beats']; k = alvo / total
    out = []
    for n, d in mel:
        if n is None: out.append((None, d * k)); continue
        rel = _midi(n) - raiz; o, r = divmod(rel, 12)
        i = min(range(len(esc)), key=lambda j: abs(esc[j] - r))
        out.append((i + o * len(MODOS[cfg['modo']]) if len(MODOS[cfg['modo']]) == len(esc) else i + o * len(MODOS[cfg['modo']]), d * k))
    return out

# ------------------------------------------------------------------ montagem de uma faixa
def montar(nome, cfg, luta=False):
    R.g = np.random.default_rng(abs(hash(nome)) % 2**31)
    rng = np.random.default_rng(sum(map(ord, nome)) * 7)
    bpm, bpc, raiz, modo = cfg['bpm'], cfg['beats'], cfg['raiz'], cfg['modo']
    b = 60 / bpm; barra = bpc * b
    comps = cfg.get('compassos', 16)
    D = comps * barra; x = Z(D + 4)
    prog = cfg['prog']
    mel = compor(cfg, rng)
    if cfg.get('motivo'):
        # metade: a melodia antiga (adaptada); outra metade: a resposta nova do tema
        meio = 0.0; corte = 0
        for i, (_, d) in enumerate(mel):
            meio += d
            if meio >= 4 * bpc - 1e-6: corte = i + 1; break
        mel = motivo(cfg['motivo'], cfg) + mel[corte:]
    # ---- harmonia
    for c in range(comps):
        t0 = c * barra; g = prog[c % len(prog)]
        ac = [mf(n) for n in triade(raiz + 12 * cfg.get('oitHarm', 0), modo, g)]
        h = cfg['harm']
        if h == 'pad': por(x, pad(ac, barra + .4, cfg.get('corteHarm', 1300)), t0, .7)
        elif h == 'orgao': por(x, orgao(ac, barra + .2), t0, .75)
        elif h == 'coro':
            if c % 2 == 0: por(x, coro(ac, 2 * barra + .5), t0, .8)
        elif h in ('arpejo', 'arpejo_rapido'):
            passos = bpc * (4 if h == 'arpejo_rapido' else 2); inst = cfg.get('instArp', harpa)
            seq = ac + [a * 2 for a in ac]
            ordem = cfg.get('ordemArp', [0, 1, 2, 3, 4, 3, 2, 1])
            for k in range(int(passos)):
                por(x, inst(seq[ordem[k % len(ordem)] % len(seq)], b / (passos / bpc) * 1.5), t0 + k * barra / passos, .32)
            if cfg.get('padJunto'): por(x, pad(ac, barra + .3, 900), t0, .45)
        elif h == 'acordes':
            inst = cfg.get('instAc', pluck)
            for k in cfg.get('batidas', [0, 2]):
                for i, f in enumerate(ac): por(x, inst(f, b * 1.2), t0 + k * b + i * .012, .22)
        elif h == 'staccato':
            for k in range(int(bpc * 2)):
                for f in ac[:2]: por(x, cordas_st(f, b / 2), t0 + k * b / 2, .3 if k % 2 == 0 else .2)
        elif h == 'oom':  # valsa: baixo no 1, acorde no 2 e 3
            inst = cfg.get('instAc', pluck)
            for k in range(1, bpc):
                for f in ac: por(x, inst(f * 2, b * .9), t0 + k * b, .17)
    # ---- baixo
    bs = cfg['baixo']
    for c in range(comps):
        t0 = c * barra; g = prog[c % len(prog)]; f = mf(grau(raiz - 24, modo, g))
        if bs == 'drone':
            if c % 4 == 0: por(x, lp(pad([f, f * 1.5], 4 * barra + .5, 500, .003), 400) * 1.6, t0, 1)
        elif bs == 'pulso' or (luta and bs != 'nenhum'):
            n = int(bpc * 2)
            for k in range(n):
                ff = f * (2 if (luta and k % 4 == 3) else 1)
                tt = T(b / 2 * .9); y = lp(.6 * np.sin(2 * np.pi * ff * tt) + .5 * signal.sawtooth(2 * np.pi * ff * tt), 600) * env(len(tt) / SR, .004, .05, .6, .04)
                por(x, y, t0 + k * b / 2, .5 if k % 2 == 0 else .35)
        elif bs == 'valsa':
            tt = T(b * .95); por(x, lp(np.sin(2 * np.pi * f * tt) + .4 * signal.sawtooth(2 * np.pi * f * tt), 500) * env(b * .95, .005, .1, .6, .1), t0, .6)
        elif bs == 'caminhando':
            for k in range(bpc):
                gg = g + [0, 2, 4, 5, 4, 2][k % 6]; ff = mf(grau(raiz - 24, modo, gg))
                tt = T(b * .8); por(x, lp(np.sin(2 * np.pi * ff * tt) + .3 * signal.sawtooth(2 * np.pi * ff * tt), 700) * env(b * .8, .005, .08, .6, .08), t0 + k * b, .5)
        elif bs == 'tuba':
            for k in (0, bpc // 2 if bpc > 3 else 2):
                por(x, metais(f, b * .9, 700) * 1.3, t0 + k * b, .55)
    # ---- percussao
    padrao = cfg['luta'] if luta else cfg['bateria']
    bat = Z(D + 4)
    for c in range(comps):
        t0 = c * barra
        for (inst, pos, g) in padrao:
            if pos < bpc: por(bat, inst(), t0 + pos * b, g)
        if luta and c % 4 == 3:
            por(bat, rufo(barra / 2), t0 + barra / 2, .6)
        if luta and c % 8 == 0: por(bat, prato(1.0), t0, .5)
    # ---- melodia (entra depois de 4 compassos de introducao em quem tem intro)
    lead = cfg['lead']; oit = cfg.get('oitLead', 0)
    q = (4 * barra) if cfg.get('intro', True) and not luta else 0.0
    volta = 0
    while q < D - .2:
        for (g, d) in mel:
            if g is not None and q < D - .2:
                n = grau(raiz + 12 + 12 * oit, modo, g)
                por(x, lead(mf(n), b * d * 1.05), q, .5)
                if luta and cfg.get('dobraLuta'): por(x, lead(mf(n) / 2, b * d), q, .25)
            q += b * d
        volta += 1
        if volta > 8: break
    # contracanto na segunda metade
    if cfg.get('contra'):
        inst = cfg['contra']; q = D / 2
        for (g, d) in mel[::2]:
            if g is not None and q < D - .3: por(x, inst(mf(grau(raiz + 12, modo, g + 2)), b * d * 2), q, .25)
            q += b * d * 2
    # ---- extras
    for ex in cfg.get('extras', []): ex(x, b, barra, comps, D, luta)
    x = reverb(x, cfg.get('sala', 1.6), cfg.get('mixRev', .22))
    if cfg.get('eco'): x = eco(x, b * .75, .3, 3)
    # bateria em canal separado: pouca reverberacao e mais baixa (senao vira chiado por cima da musica)
    x = x / (np.max(np.abs(x)) + 1e-9) + reverb(bat, .7, .08) / (np.max(np.abs(bat)) + 1e-9) * (.55 if luta else .4)
    return emendar(x[:int((D + 4) * SR)], D)

def emendar(x, D):
    """Fecha o loop: a cauda que passa do fim volta por cima do comeco."""
    n = int(round(D * SR)); y = x[:n].copy(); cauda = x[n:]
    y[:len(cauda)] += cauda
    f = int(.02 * SR); y[:f] *= np.linspace(.85, 1, f)
    return y

def salvar(nome, x, vol=.85, q='0'):
    x = np.asarray(x, float); x -= np.mean(x)
    x = np.tanh(x / (np.percentile(np.abs(x), 99.7) + 1e-9) * .95); x /= np.max(np.abs(x)) + 1e-9; x *= vol
    w = f'{OUT}/{nome}.wav'
    with wave.open(w, 'wb') as f:
        f.setnchannels(1); f.setsampwidth(2); f.setframerate(SR); f.writeframes((x * 32767).astype(np.int16).tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', w, '-c:a', 'libvorbis', '-q:a', q, f'{OUT}/{nome}.ogg'], check=True)
    os.remove(w)
    print(f'{nome:18s} {os.path.getsize(f"{OUT}/{nome}.ogg") // 1024:4d} KB {len(x) / SR:5.1f} s', flush=True)
    return len(x) / SR

# ------------------------------------------------------------------ extras por tema
def ex_sinos(x, b, barra, comps, D, luta):
    for c in range(0, comps, 4): por(x, sino(mf(62), 3), c * barra, .35)
def ex_relogio(x, b, barra, comps, D, luta):
    q = 0.0
    while q < D: por(x, tique(), q, .35); q += b / 2
def ex_bigorna(x, b, barra, comps, D, luta):
    for c in range(comps): por(x, bigorna(), c * barra + 2 * b, .45)
def ex_ondas(x, b, barra, comps, D, luta):
    tt = T(D); por(x, lp(R.g.standard_normal(len(tt)), 700) * (.12 + .1 * np.sin(2 * np.pi * tt / 6)), 0, 1)
def ex_bolhas(x, b, barra, comps, D, luta):
    q = .3
    while q < D:
        tt = T(.12); f = 300 + R.g.random() * 500
        por(x, np.sin(2 * np.pi * f * (1 + 3 * tt) * tt) * np.exp(-tt * 30) * .3, q, .5); q += .2 + R.g.random() * .9
def ex_trovao(x, b, barra, comps, D, luta):
    for c in range(2, comps, 6): por(x, trovao(), c * barra + b, .55)
def ex_vento(x, b, barra, comps, D, luta):
    tt = T(D); n = R.g.standard_normal(len(tt))
    por(x, bp(n, 500, 1800) * (.06 + .05 * np.sin(2 * np.pi * tt / 5)), 0, 1)
def ex_coracao(x, b, barra, comps, D, luta):
    for c in range(comps):
        por(x, bumbo(80, 40, .3, .6), c * barra, .7); por(x, bumbo(80, 40, .3, .45), c * barra + .28, .6)
def ex_brilhos(x, b, barra, comps, D, luta):
    for c in range(comps):
        if c % 2 == 1:
            for k in range(4): por(x, fm(mf(84 + [0, 4, 7, 11][k]), .8), c * barra + k * b / 2, .12)
def ex_lava(x, b, barra, comps, D, luta):
    tt = T(D); por(x, lp(R.g.standard_normal(len(tt)), 150) * .35, 0, 1)
def ex_sonar(x, b, barra, comps, D, luta):
    for c in range(0, comps, 2):
        tt = T(2.5); por(x, np.sin(2 * np.pi * 220 * tt) * np.exp(-tt * 2) * .35, c * barra, .5)
def ex_eco_warden(x, b, barra, comps, D, luta):
    for c in range(comps):
        if c % 2 == 0: por(x, bumbo(60, 30, .5, .9), c * barra, .8); por(x, bumbo(60, 30, .5, .7), c * barra + .32, .7)
def ex_uivo(x, b, barra, comps, D, luta):
    for c in range(3, comps, 8):
        tt = T(2.2); f = 420 + 160 * np.sin(np.pi * np.clip(tt / 2.2, 0, 1))
        por(x, bp(np.sin(2 * np.pi * np.cumsum(f) / SR) + .3 * signal.sawtooth(2 * np.pi * np.cumsum(f) / SR), 300, 2000) * env(2.2, .4, .3, .7, .8) * .3, c * barra, .6)
def ex_corvos(x, b, barra, comps, D, luta):
    for c in range(1, comps, 5):
        for k in range(2):
            tt = T(.25); por(x, bp(signal.sawtooth(2 * np.pi * (700 - 300 * tt) * tt), 600, 2500) * np.exp(-tt * 9) * .25, c * barra + k * .3, .6)

# ------------------------------------------------------------------ temas
def P(*itens): return list(itens)
BUM = lambda g=1.0: (lambda: bumbo(g=g))
CX = lambda c=190: (lambda: caixa(c))
HH = lambda a=False: (lambda: chimbal(aberto=a))
TOM = lambda f=120: (lambda: tom(f))
TAI = lambda: taiko
DAR = lambda a=False: (lambda: darbuka(a))
MAD = lambda f=1100: (lambda: madeira(f))
CHO = lambda: chocalho
TIM = lambda f=70: (lambda: timpano(f))

def luta44(base=None):
    p = [(BUM(), 0, 1), (CX(), 1, .8), (BUM(), 1.5, .7), (CX(), 3, .8), (BUM(), 2, .9)]
    p += [(HH(), k / 2, .4 if k % 2 == 0 else .22) for k in range(8)]
    return p + (base or [])

TEMAS = {
    # Cripta / cemiterio: valsa macabra 3/4, caixinha de musica + orgao, re menor harmonico
    'cripta': dict(bpm=96, beats=3, raiz=62, modo='harm', prog=[0, 3, 4, 0, 5, 3, 4, 4], harm='oom', instAc=pluck, baixo='valsa',
                   lead=caixinha, oitLead=1, ritmos=[[1, .5, .5, 1], [1.5, .5, 1], [.5, .5, .5, .5, 1]],
                   bateria=[(TIM(55), 0, .25)], luta=[(BUM(), 0, 1), (CX(), 1, .6), (CX(), 2, .6)] + [(HH(), k / 2, .3) for k in range(6)],
                   extras=[ex_sinos, ex_corvos], contra=lambda f, d: orgao([f / 2], d), sala=2.2, mixRev=.3),
    # Catedral / mausoleu: coro e orgao, lento, frigio
    'catedral': dict(bpm=66, beats=4, raiz=57, modo='frigio', prog=[0, 1, 0, 6, 5, 1, 0, 0], harm='coro', baixo='drone',
                     lead=lambda f, d: orgao([f], d, 2600), ritmos=[[2, 1, 1], [1, 1, 2], [3, 1]],
                     bateria=[(TIM(55), 0, .5)], luta=[(TIM(55), 0, 1), (TIM(73), 2, .9), (TAI(), 3, .7)] + [(CX(), 1, .4), (CX(), 3, .4)],
                     extras=[ex_sinos], sala=3.2, mixRev=.4),
    # Vampiro / castelo: cravo + cordas staccato, 6/8 gotico
    'vampiro': dict(bpm=120, beats=6, raiz=61, modo='harm', prog=[0, 0, 5, 4, 3, 4, 0, 4], harm='staccato', baixo='pulso',
                    lead=cravo, oitLead=0, ritmos=[[.5, .5, .5, 1.5, 1, 2], [1.5, .5, 1, 3], [1, .5, .5, 1, 3]], contra=violino,
                    bateria=[(BUM(.8), 0, .7), (CX(), 3, .5)], luta=[(BUM(), 0, 1), (BUM(), 1.5, .7), (CX(), 3, .9), (BUM(), 4.5, .7)] + [(HH(), k, .3) for k in range(6)],
                    sala=2.0, mixRev=.28),
    # Aranhas: pizzicato cromatico em 7/8
    'aranha': dict(bpm=150, beats=7, raiz=52, modo='dim', prog=[0, 0, 1, 0, 3, 2, 1, 0], harm='arpejo', instArp=lambda f, d: pluck(f, .25, 2000, .98),
                   ordemArp=[0, 1, 0, 2, 0, 1, 3], baixo='pulso', lead=violino, ritmos=[[2, 2, 3], [1, 1, 2, 3], [3, 2, 2]],
                   bateria=[(MAD(900), 0, .5), (MAD(1300), 2, .4), (MAD(900), 4, .4), (CHO(), 5, .5)],
                   luta=[(BUM(), 0, 1), (CX(), 2, .8), (BUM(), 4, .8), (CX(), 5, .8)] + [(HH(), k, .3) for k in range(7)], sala=1.4),
    # Mina / tesouro: canto de trabalho, banjo + bigorna, dorico
    'mina': dict(bpm=112, beats=4, raiz=50, modo='dorico', prog=[0, 3, 0, 4, 0, 3, 6, 4], harm='acordes', instAc=banjo, batidas=[0, 1, 2, 3],
                 baixo='caminhando', lead=oboe, ritmos=[[1, .5, .5, 1, 1], [.5, .5, 1, 2], [1.5, .5, 1, 1]],
                 bateria=[(BUM(.8), 0, .7), (MAD(800), 1, .4), (BUM(.8), 2, .6), (MAD(800), 3, .4)], luta=luta44(), extras=[ex_bigorna], sala=1.2),
    # Deserto (Farao): frigio dominante, oud + darbuka
    'deserto': dict(bpm=104, beats=4, raiz=52, modo='frigdom', prog=[0, 1, 0, 6, 0, 1, 2, 0], harm='pad', corteHarm=900, baixo='drone',
                    lead=oud, ritmos=[[.5, .5, .5, .5, 2], [.75, .25, .5, .5, 1, 1], [1, .5, .5, 2]], contra=oboe,
                    bateria=[(DAR(), 0, .8), (DAR(True), .75, .5), (DAR(True), 1.5, .5), (DAR(), 2, .7), (DAR(True), 3, .5), (DAR(True), 3.5, .4)],
                    luta=[(BUM(), 0, 1), (DAR(), 1, .7), (BUM(), 2, .9), (DAR(True), 2.5, .6), (CX(), 3, .8)] + [(DAR(True), k / 2, .3) for k in range(8)], sala=1.8),
    # Pantano / esgoto / fungos: balanco gosmento, fagote, bolhas
    'pantano': dict(bpm=92, beats=4, raiz=55, modo='dim', prog=[0, 2, 0, 5, 0, 2, 3, 1], harm='pad', corteHarm=700, baixo='caminhando',
                    lead=oboe, oitLead=-1, ritmos=[[1.5, .5, 1.5, .5], [.75, .25, 1, 2], [1, 1, .5, .5, 1]],
                    bateria=[(BUM(.7), 0, .6), (MAD(500), 1.5, .5), (BUM(.7), 2.5, .5), (MAD(700), 3.5, .5)], luta=luta44([(MAD(500), 1.5, .5)]),
                    extras=[ex_bolhas], sala=1.6),
    # Floresta assombrada / moinho / fazenda: folk 6/8 menor com flauta
    'floresta': dict(bpm=108, beats=6, raiz=57, modo='eolio', prog=[0, 5, 2, 6, 0, 5, 3, 4], harm='acordes', instAc=pluck, batidas=[0, 3],
                     baixo='valsa', lead=flauta, ritmos=[[1, .5, 1.5, 1, 2], [1.5, 1.5, 1, 2], [.5, .5, 1, 1, 3]],
                     bateria=[(BUM(.6), 0, .5), (MAD(1500), 3, .3), (CHO(), 1.5, .25), (CHO(), 4.5, .25)],
                     luta=[(BUM(), 0, 1), (CX(), 3, .8), (BUM(), 4.5, .6)] + [(HH(), k, .3) for k in range(6)], extras=[ex_corvos, ex_vento], sala=1.8),
    # Jardim das cerejeiras: escala japonesa, koto + taiko
    'jardim': dict(bpm=84, beats=4, raiz=57, modo='japones', prog=[0, 0, 3, 2, 0, 4, 3, 0], harm='pad', corteHarm=1100, baixo='drone',
                   lead=koto, ritmos=[[1, 1, 2], [.5, .5, 1, 2], [2, .5, .5, 1]], contra=flauta,
                   bateria=[(TAI(), 0, .6), (MAD(1700), 2.5, .3)], luta=[(TAI(), 0, 1), (TAI(), 1.5, .8), (TAI(), 2, .9), (MAD(1700), 3, .6), (TAI(), 3.5, .7)],
                   sala=2.4, mixRev=.3),
    # Selva / lobos / covil: tribal, marimba, pentatonica
    'selva': dict(bpm=126, beats=4, raiz=52, modo='penta', prog=[0, 0, 3, 2, 0, 0, 4, 3], harm='arpejo', instArp=marimba, ordemArp=[0, 2, 1, 2, 3, 2, 1, 2],
                  baixo='pulso', lead=flauta, ritmos=[[.5, .5, 1, .5, .5, 1], [1, .5, .5, 2], [.5, 1, .5, 2]],
                  bateria=[(TOM(110), 0, .8), (TOM(160), .75, .5), (TOM(130), 1.5, .6), (TOM(110), 2, .7), (TOM(190), 3, .5), (TOM(160), 3.5, .5), (CHO(), .5, .4), (CHO(), 2.5, .4)],
                  luta=[(TAI(), 0, 1), (TOM(160), .5, .7), (TOM(110), 1, .8), (TAI(), 2, .9), (TOM(190), 2.5, .7), (TOM(130), 3, .8), (TOM(160), 3.5, .7)] + [(CHO(), k / 2, .4) for k in range(8)],
                  extras=[ex_uivo], sala=1.5),
    # Saqueadores / quartel: marcha militar, metais, caixa
    'quartel': dict(bpm=116, beats=4, raiz=50, modo='eolio', prog=[0, 5, 6, 0, 3, 4, 5, 4], harm='acordes', instAc=lambda f, d: metais(f, d * .7, 1500), batidas=[0, 2],
                    baixo='tuba', lead=metais, ritmos=[[.75, .25, 1, 1, 1], [1, .5, .5, 2], [.75, .25, .75, .25, 2]],
                    bateria=[(BUM(), 0, .8), (CX(240), .5, .3), (CX(240), .75, .3), (CX(240), 1, .6), (BUM(), 2, .7), (CX(240), 3, .6), (CX(240), 3.5, .3)],
                    luta=luta44([(CX(240), .75, .4), (CX(240), 2.5, .4)]), dobraLuta=True, sala=1.5),
    # Gelo / urso / geleira: celesta brilhante em 3/4, lidio
    'gelo': dict(bpm=78, beats=3, raiz=59, modo='lidio', prog=[0, 1, 0, 4, 3, 1, 5, 4], harm='arpejo', instArp=lambda f, d: caixinha(f * 2, d),
                 ordemArp=[0, 2, 4, 1, 3, 5], baixo='drone', lead=lambda f, d: sino(f, d), ritmos=[[2, 1], [1, 1, 1], [3]], padJunto=True,
                 bateria=[(CHO(), 0, .2)], luta=[(BUM(), 0, 1), (CX(300), 1, .6), (CX(300), 2, .6), (HH(True), 2.5, .3)], extras=[ex_vento], sala=3.0, mixRev=.38, eco=True),
    # Mar / farol / naufragio: shanty 6/8 com acordeao
    'mar': dict(bpm=126, beats=6, raiz=50, modo='dorico', prog=[0, 0, 6, 6, 3, 3, 4, 0], harm='acordes', instAc=acordeao, batidas=[0, 3], baixo='valsa',
                lead=acordeao, ritmos=[[1, .5, .5, 1, 1, 2], [1.5, 1.5, 1.5, 1.5], [1, 2, 1, 2]], contra=flauta,
                bateria=[(BUM(.7), 0, .6), (MAD(900), 3, .4), (CHO(), 1.5, .3), (CHO(), 4.5, .3)], luta=[(BUM(), 0, 1), (CX(), 3, .8), (BUM(), 4, .6)] + [(HH(), k, .3) for k in range(6)],
                extras=[ex_ondas], sala=1.6),
    # Catacumba afogada: submerso, drone, sonar, bolhas
    'afogada': dict(bpm=70, beats=4, raiz=49, modo='eolio', prog=[0, 5, 0, 3], harm='pad', corteHarm=600, baixo='drone', lead=lambda f, d: lp(fm(f, d, 1.5, 2), 1500),
                    ritmos=[[3, 1], [2, 2], [4]], bateria=[(TIM(50), 0, .4)], luta=[(TIM(50), 0, 1), (TAI(), 2, .8), (TIM(65), 3, .6)] + [(MAD(400), k, .3) for k in range(4)],
                    extras=[ex_bolhas, ex_sonar], sala=3.5, mixRev=.45),
    # Laboratorio / testes / relogio: mecanico 7/8, arpejo 8-bit, relogio
    'relogio': dict(bpm=132, beats=7, raiz=57, modo='melod', prog=[0, 3, 4, 0, 1, 4, 5, 4], harm='arpejo_rapido', instArp=lambda f, d: quadrado(f, d * .6, .25),
                    ordemArp=[0, 1, 2, 3, 2, 1, 4], baixo='pulso', lead=lambda f, d: quadrado(f, d, .5), ritmos=[[1, 1, 1, 2, 2], [2, 2, 3], [.5, .5, 1, 2, 3]],
                    bateria=[(MAD(1200), 0, .5), (MAD(800), 2, .4), (MAD(1200), 4, .4), (MAD(800), 5.5, .3)],
                    luta=[(BUM(), 0, 1), (CX(), 2, .8), (BUM(), 4, .8), (CX(), 5, .8), (CX(), 6, .5)] + [(HH(), k / 2, .25) for k in range(14)],
                    extras=[ex_relogio], sala=1.2),
    # Arcano / biblioteca / torre / observatorio: harpa + coro, menor melodico, brilhos magicos
    'arcano': dict(bpm=92, beats=3, raiz=60, modo='melod', prog=[0, 3, 1, 4, 5, 3, 1, 4], harm='arpejo', instArp=harpa, ordemArp=[0, 1, 2, 4, 2, 1], padJunto=True,
                   baixo='drone', lead=fm, ritmos=[[1, 1, 1], [1.5, .5, 1], [2, 1]], contra=lambda f, d: coro([f], d),
                   bateria=[(CHO(), 1, .2)], luta=[(BUM(), 0, 1), (CX(), 1, .6), (TOM(150), 2, .6), (TOM(110), 2.5, .6)], extras=[ex_brilhos], sala=2.8, mixRev=.35, eco=True),
    # Coliseu / labirinto: epico, metais e tambores de guerra
    'coliseu': dict(bpm=100, beats=4, raiz=50, modo='mixo', prog=[0, 6, 3, 0, 4, 6, 3, 4], harm='pad', corteHarm=1800, baixo='tuba',
                    lead=metais, ritmos=[[1, .5, .5, 2], [1.5, .5, 1, 1], [.5, .5, .5, .5, 2]], contra=lambda f, d: coro([f], d),
                    bateria=[(TAI(), 0, .9), (TAI(), 1.5, .5), (TAI(), 2, .7), (TOM(90), 3, .6), (TOM(90), 3.5, .5)],
                    luta=[(TAI(), 0, 1), (TAI(), .5, .6), (TAI(), 1, .8), (CX(), 1, .7), (TAI(), 2, 1), (TAI(), 2.5, .6), (CX(), 3, .8), (TAI(), 3.5, .7)] + [(HH(), k / 2, .25) for k in range(8)],
                    extras=[], sala=2.2, mixRev=.3),
    # Trovao / ventos: tempestade rapida, arpejo eletrico, trovoes
    'trovao': dict(bpm=158, beats=4, raiz=54, modo='eolio', prog=[0, 5, 6, 4, 0, 5, 3, 4], harm='arpejo_rapido', instArp=lambda f, d: quadrado(f, d * .5, .3),
                   ordemArp=[0, 2, 4, 2, 1, 3, 4, 3], baixo='pulso', lead=violino, ritmos=[[1, 1, .5, .5, 1], [2, 1, 1], [.5, .5, .5, .5, 2]],
                   bateria=[(BUM(), 0, .8), (CX(), 1, .6), (BUM(), 2.5, .6), (CX(), 3, .6)] + [(HH(), k / 2, .25) for k in range(8)],
                   luta=luta44([(BUM(), .75, .6), (BUM(), 3.5, .6)]), extras=[ex_trovao, ex_vento], sala=1.8),
    # Fogo / vulcao / cratera / forja: pesado, riffs metal, frigio
    'fogo': dict(bpm=146, beats=4, raiz=52, modo='frigio', prog=[0, 0, 1, 0, 5, 6, 1, 0], harm='staccato', baixo='pulso',
                 lead=lambda f, d: lp(np.tanh(3 * metais(f, d, 3000)), 3500) * .6, oitLead=-1, ritmos=[[.5, .5, .5, .5, 1, 1], [1, 1, .5, .5, 1], [.75, .75, .5, 2]],
                 bateria=[(BUM(), 0, 1), (BUM(), .5, .6), (CX(170), 1, .8), (BUM(), 2, .9), (BUM(), 2.5, .6), (CX(170), 3, .8)] + [(HH(), k / 2, .25) for k in range(8)],
                 luta=[(BUM(), k / 2, .8) for k in range(8)] + [(CX(170), 1, 1), (CX(170), 3, 1)] + [(HH(), k / 4, .2) for k in range(16)], extras=[ex_lava], sala=1.4, dobraLuta=True),
    # Nether / bastiao / wither / almas: 5/4 frigio dominante, metais graves
    'nether': dict(bpm=132, beats=5, raiz=50, modo='frigdom', prog=[0, 1, 0, 6, 0, 1, 2, 1], harm='pad', corteHarm=1000, baixo='pulso',
                   lead=lambda f, d: metais(f, d, 1800), oitLead=-1, ritmos=[[1, 1, 1, 2], [1.5, 1.5, 2], [.5, .5, 1, 1, 2]], contra=lambda f, d: coro([f], d, 600),
                   bateria=[(TAI(), 0, .9), (CX(150), 1.5, .5), (TAI(), 2, .7), (TAI(), 3, .6), (CX(150), 4, .6)],
                   luta=[(BUM(), 0, 1), (TAI(), 1, .8), (CX(150), 1.5, .8), (BUM(), 2, .9), (TAI(), 3, .8), (CX(150), 4, .9), (BUM(), 4.5, .6)] + [(HH(), k / 2, .25) for k in range(10)],
                   extras=[ex_lava], sala=2.0),
    # Vazio / End: espacial, arpejos com eco, sinos FM
    'vazio': dict(bpm=118, beats=4, raiz=59, modo='lidio', prog=[0, 4, 5, 1, 0, 3, 4, 1], harm='arpejo_rapido', instArp=lambda f, d: fm(f, d, 1.2, 3.01) * .6,
                  ordemArp=[0, 1, 2, 5, 3, 4, 2, 1], padJunto=True, baixo='drone', lead=lambda f, d: quadrado(f, d, .12), ritmos=[[2, 1, 1], [1, 1, 2], [3, 1]],
                  bateria=[(BUM(.6), 0, .5), (HH(True), 2, .2)], luta=[(BUM(), 0, 1), (CX(260), 1, .7), (BUM(), 2.5, .8), (CX(260), 3, .7)] + [(HH(), k / 4, .18) for k in range(16)],
                  sala=3.0, mixRev=.35, eco=True),
    # Cidade Ancestral (warden): ambiente sombrio, pulsos graves, eco de coracao
    'antiga': dict(bpm=64, beats=4, raiz=46, modo='eolio', prog=[0, 0, 5, 1], harm='pad', corteHarm=500, baixo='drone', lead=lambda f, d: lp(sino(f, d), 1800),
                   ritmos=[[4], [3, 1], [2, 2]], bateria=[], luta=[(TAI(), 0, 1), (TAI(), 2.5, .8), (TIM(50), 3, .7)], extras=[ex_eco_warden], sala=4.0, mixRev=.5),
}
for _t, _m in {'cripta': 'M1', 'catedral': 'M1', 'vampiro': 'M1', 'aranha': 'M1', 'mina': 'M3', 'deserto': 'M1', 'pantano': 'M2', 'floresta': 'M2', 'jardim': 'M2', 'selva': 'M2', 'quartel': 'M2', 'gelo': 'M5', 'mar': 'M3', 'afogada': 'M3', 'relogio': 'M3', 'arcano': 'M1', 'coliseu': 'M3', 'trovao': 'M1', 'fogo': 'M4', 'nether': 'M4', 'vazio': 'M5', 'antiga': 'M3'}.items(): TEMAS[_t]['motivo'] = _m
# explorar e o tema "padrao" (masmorras sem tema): andanca de aventura
TEMAS['explorar'] = dict(TEMAS['mina'], bpm=104, raiz=55, modo='dorico', lead=flauta, instAc=pluck, extras=[], harm='acordes', batidas=[0, 2], motivo='M2')

# ------------------------------------------------------------------ Herobrine
def herobrine():
    """Herobrine: 7/8 a 150 bpm, re menor com tritono. Coro sombrio, poderes magicos (raios, brilhos que sobem,
    zumbido ao contrario), coracao, taikos e um riff grave de metais. ~44 s, fecha em loop."""
    R.g = np.random.default_rng(666)
    bpm, bpc = 150, 7; b = 60 / bpm; barra = bpc * b; comps = 16; D = comps * barra
    x = Z(D + 5); bat = Z(D + 5); raiz = 50
    tri = [0, 1, 6, 1, 0, 3, 6, 5]   # graus em semitons (tritono = 6)
    for c in range(comps):
        t0 = c * barra; r = raiz + tri[c % len(tri)]
        # coro dissonante a cada 2 compassos
        if c % 2 == 0: por(x, coro([mf(r), mf(r + 3), mf(r + 6), mf(r + 12)], 2 * barra + .6, 650), t0, .55)
        # riff grave (metais distorcidos) no 7/8: 2+2+3
        for k, d in ((0, 2), (2, 2), (4, 3)):
            por(x, np.tanh(2.5 * metais(mf(r - 12), b * d * .9, 900)) * .7, t0 + k * b, .7)
        # baixo pulsante
        for k in range(7): por(x, lp(signal.sawtooth(2 * np.pi * mf(r - 24) * T(b * .9)), 300) * env(b * .9, .003, .05, .7, .05), t0 + k * b, .45)
        # bateria pesada: taikos + caixa no 3 e no 5, aumenta depois do compasso 8
        forte = c >= 4
        por(bat, taiko(), t0, 1.0); por(bat, taiko(), t0 + 2 * b, .7); por(bat, taiko(), t0 + 4 * b, .9)
        por(bat, caixa(160), t0 + 2 * b, .6 if forte else .3); por(bat, caixa(160), t0 + 5 * b, .7 if forte else .35)
        if forte:
            for k in range(14): por(bat, chimbal(), t0 + k * b / 2, .22)
            if c % 4 == 0: por(bat, prato(1.0), t0, .45)
        if c % 4 == 3: por(bat, rufo(barra / 2, 2.5), t0 + barra / 2, .9)
        # coracao nos primeiros compassos
        if c < 4:
            por(x, bumbo(70, 35, .3, .8), t0, .8); por(x, bumbo(70, 35, .3, .6), t0 + .27, .7)
    # PODERES MAGICOS
    for c in range(comps):
        t0 = c * barra
        if c % 4 == 1:   # brilhos magicos subindo (tons inteiros)
            for k in range(8): por(x, fm(mf(74 + 2 * k), .9, 4, 1.41), t0 + k * b / 2, .16)
        if c % 6 == 5:   # raio: estalo + trovao
            tt = T(.35); z = hp(R.g.standard_normal(len(tt)), 3000) * np.exp(-tt * 14)
            por(x, z * 1.2, t0 + 3 * b, .9); por(x, trovao(2.5), t0 + 3 * b, .7)
        if c % 8 == 7:   # zumbido ao contrario (suga para dentro) antes da volta
            n = int(barra * SR); tt = np.arange(n) / SR
            z = bp(R.g.standard_normal(n), 400, 5000) * (tt / tt[-1]) ** 3
            sw = np.sin(2 * np.pi * np.cumsum(200 + 1800 * (tt / tt[-1]) ** 2) / SR) * (tt / tt[-1]) ** 3
            por(x, (z * .35 + sw * .4), t0, .6)
        if c % 4 == 2:   # risada/sussurro grave (vocal formante descendo)
            tt = T(1.4); f = 140 - 50 * tt
            v = bp(signal.sawtooth(2 * np.pi * np.cumsum(f) / SR), 300, 900) * env(1.4, .1, .3, .6, .5)
            por(x, v * .5, t0 + b, .6)
    # melodia do Herobrine (violino agudo e cortante), entra no compasso 4
    mel = [(12, 2), (13, 2), (18, 3), (17, 2), (13, 2), (12, 3), (15, 2), (13, 2), (18, 2), (19, 1), (18, 2), (13, 2), (12, 3)]
    q = 4 * barra
    while q < D - .2:
        for s, d in mel:
            if q < D - .2: por(x, violino(mf(raiz + s), b * d * 1.05) * 1.2, q, .5); por(x, violino(mf(raiz + s + 12), b * d), q, .18)
            q += b * d
    x = reverb(x, 2.6, .28)
    x = x / (np.max(np.abs(x)) + 1e-9) + reverb(bat, .9, .1) / (np.max(np.abs(bat)) + 1e-9) * .5
    return emendar(x[:int((D + 5) * SR)], D)

# ------------------------------------------------------------------ v3b: VARIACOES da musica caracteristica
# 14/11 (Paulo: "ficou um pouco feia"; "aquela musica agora e a caracteristica das dungeons"). Cada tema agora e a
# MUSICA ORIGINAL da familia dele (mesma melodia, mesmos acordes, mesmo compasso 4/4, como no jogo desde o comeco),
# re-arranjada: instrumentos, levada, andamento, tom, ambiente e enfeite proprios de cada masmorra.
ORIG = {'M1': ([('E4', 1), ('A4', 1), ('C5', .5), ('B4', .5), ('A4', 1), ('G4', 1), ('E4', 1), (None, 1),
                ('F4', 1), ('A4', .5), ('C5', .5), ('A4', 1), ('G4', 1), ('E4', 1), ('D4', 1), (None, 1)],
               [('A2', ['A3', 'C4', 'E4']), ('F2', ['F3', 'A3', 'C4']), ('C2', ['C3', 'E3', 'G3']), ('G2', ['G2', 'B3', 'D4'])]),
        'M2': ([('B4', .5), ('E5', .5), ('D5', 1), ('B4', 1), ('G4', 1), ('A4', .5), ('B4', .5), ('G4', 1), ('E4', 1), ('D4', 1), ('E4', 2)],
               [('E2', ['E3', 'G3', 'B3']), ('C2', ['C3', 'E3', 'G3']), ('G2', ['G3', 'B3', 'D4']), ('D2', ['D3', 'F#3', 'A3'])]),
        'M3': ([('D5', .5), ('C5', .5), ('A4', 1), ('F4', 1), ('D4', 1), ('A4', .5), ('C5', .5), ('D5', 1), ('A4', 2)],
               [('D2', ['D3', 'F3', 'A3']), ('A2', ['A3', 'C4', 'E4']), ('B2', ['B2', 'D3', 'F3']), ('G2', ['G2', 'A#3', 'D4'])]),
        'M4': ([('E5', .5), ('F5', .5), ('E5', .5), ('D5', .5), ('C5', 1), ('B4', 1), ('E4', .5), ('F4', .5), ('G4', 1), ('E4', 2)],
               [('E2', ['E3', 'F3', 'B3']), ('F2', ['F3', 'A3', 'C4']), ('E2', ['E3', 'G3', 'B3']), ('D2', ['D3', 'F3', 'A3'])]),
        'M5': ([('F#4', 1), ('B4', 1), ('C#5', .5), ('B4', .5), ('A4', 1), ('F#4', 1), ('D4', 1), ('E4', 1), ('B4', 2)],
               [('B2', ['B3', 'D4', 'F#4']), ('G2', ['G3', 'B3', 'D4']), ('A2', ['A3', 'C#4', 'E4']), ('F#2', ['F#3', 'A3', 'C#4'])])}

def hz(n, tr=0): return mf(_midi(n) + tr)

# levadas em 4/4 para os temas que tinham outro compasso
P44 = {
    'cripta': ([(TIM(55), 0, .35), (MAD(700), 2, .25)], None),
    'vampiro': ([(BUM(.7), 0, .6), (CX(), 2, .45), (MAD(1400), 3.5, .25)], None),
    'aranha': ([(MAD(900), 0, .5), (MAD(1300), .75, .35), (MAD(900), 1.5, .4), (MAD(1300), 2.5, .35), (CHO(), 3, .45)], None),
    'floresta': ([(BUM(.6), 0, .5), (MAD(1500), 1.5, .3), (BUM(.6), 2.5, .4), (CHO(), 3, .3)], None),
    'gelo': ([(CHO(), 1, .2), (CHO(), 3, .2)], None),
    'mar': ([(BUM(.7), 0, .6), (MAD(900), 1.5, .35), (BUM(.7), 2, .5), (CHO(), 3, .3), (CHO(), 3.5, .25)], None),
    'relogio': ([(MAD(1200), 0, .5), (MAD(800), 1, .35), (MAD(1200), 2, .45), (MAD(800), 3, .35)], None),
    'arcano': ([(CHO(), 1, .2), (TOM(150), 3.5, .25)], None),
    'nether': ([(TAI(), 0, .9), (CX(150), 1, .5), (TAI(), 2, .7), (TAI(), 2.5, .5), (CX(150), 3, .6)], None),
}

VAR = {
    # tema:     (musica, bpm, transp, acordes(inst, estilo), lead, oitLead, fundo, baixo)
    'cripta':   ('M1', 92, 0,  (caixinha, 'arpejo'), caixinha, 1, 'orgao', 'normal'),
    'catedral': ('M1', 72, -2, (sino, 'bloco'), lambda f, d: orgao([f], d, 2600), 0, 'coro', 'drone'),
    'vampiro':  ('M1', 104, 1, (cravo, 'arpejo'), violino, 0, 'cordas', 'normal'),
    'aranha':   ('M1', 112, -1, (lambda f, d: pluck(f, .3, 2000, .98), 'arpejo'), violino, 0, None, 'normal'),
    'deserto':  ('M1', 100, -5, (oud, 'arpejo'), oboe, 0, 'pad', 'drone'),
    'arcano':   ('M1', 96, 3, (harpa, 'arpejo'), fm, 0, 'coro', 'normal'),
    'trovao':   ('M1', 150, -3, (lambda f, d: quadrado(f, d * .5, .3), 'arpejo'), violino, 0, None, 'normal'),
    'pantano':  ('M2', 92, 3, (lambda f, d: pluck(f, d, 1500, .99), 'bloco'), oboe, -1, 'pad', 'normal'),
    'floresta': ('M2', 108, 0, (pluck, 'arpejo'), flauta, 0, None, 'normal'),
    'jardim':   ('M2', 86, 5, (koto, 'arpejo'), koto, 0, 'pad', 'drone'),
    'selva':    ('M2', 124, 0, (marimba, 'arpejo'), flauta, 0, None, 'normal'),
    'quartel':  ('M2', 116, -2, (lambda f, d: metais(f, d * .7, 1500), 'bloco'), metais, 0, None, 'tuba'),
    'explorar': ('M2', 106, -1, (pluck, 'arpejo'), flauta, 0, None, 'normal'),
    'mina':     ('M3', 112, 0, (banjo, 'arpejo'), oboe, 0, None, 'normal'),
    'mar':      ('M3', 120, 0, (acordeao, 'bloco'), acordeao, 0, None, 'normal'),
    'afogada':  ('M3', 76, -1, (lambda f, d: lp(fm(f, d, 1.2, 2), 1500), 'arpejo'), lambda f, d: lp(fm(f, d, 1.5, 2), 1500), 0, 'pad', 'drone'),
    'relogio':  ('M3', 126, 7, (lambda f, d: quadrado(f, d * .6, .25), 'arpejo'), lambda f, d: quadrado(f, d, .5), 0, None, 'normal'),
    'coliseu':  ('M3', 100, -2, (lambda f, d: metais(f, d * .8, 1500), 'bloco'), metais, 0, 'coro', 'tuba'),
    'antiga':   ('M3', 70, -4, (lambda f, d: lp(sino(f, d), 1800), 'arpejo'), lambda f, d: lp(sino(f, d), 1800), 0, 'pad', 'drone'),
    'fogo':     ('M4', 146, 0, (cordas_st, 'staccato'), lambda f, d: lp(np.tanh(3 * metais(f, d, 3000)), 3500) * .6, 0, None, 'normal'),
    'nether':   ('M4', 128, -2, (lambda f, d: metais(f, d * .8, 1200), 'bloco'), lambda f, d: metais(f, d, 1800), 0, 'coro', 'normal'),
    'gelo':     ('M5', 96, 0, (lambda f, d: caixinha(f * 2, d), 'arpejo'), lambda f, d: sino(f, d), 0, 'pad', 'drone'),
    'vazio':    ('M5', 118, 0, (lambda f, d: fm(f, d, 1.2, 3.01) * .6, 'arpejo'), lambda f, d: quadrado(f, d, .12), 0, 'pad', 'drone'),
}

def variacao(nome, luta=False):
    R.g = np.random.default_rng(sum(map(ord, nome)) * 13)
    cfg = TEMAS[nome]
    mus, bpm, tr, (instAc, estAc), lead, oit, fundo, tipoBaixo = VAR[nome]
    mel, prog = ORIG[mus]
    b = 60 / bpm; barra = 4 * b; comps = 16; D = comps * barra
    x = Z(D + 4); bat = Z(D + 4)
    for c in range(comps):
        t0 = c * barra; nb, tri = prog[c % 4]
        fr = [hz(n, tr) for n in tri]; fb = hz(nb, tr)
        # baixo (como na original: colcheias na fundamental, oitava no 4 e no 7)
        if tipoBaixo == 'drone':
            if c % 2 == 0: por(x, lp(pad([fb, fb * 1.5], 2 * barra + .5, 500, .003), 400) * 1.5, t0, 1)
            if luta:
                for k in range(8): por(x, lp(np.sin(2 * np.pi * fb * T(b * .45)) + .4 * signal.sawtooth(2 * np.pi * fb * T(b * .45)), 600) * env(b * .45, .004, .05, .6, .04), t0 + k * b / 2, .4)
        elif tipoBaixo == 'tuba':
            for k in (0, 2): por(x, metais(fb, b * .9, 700) * 1.3, t0 + k * b, .55)
            if luta:
                for k in (1, 3): por(x, metais(fb, b * .5, 700) * 1.3, t0 + k * b, .35)
        else:
            for k in range(8):
                f = fb * (2 if k in (3, 6) else 1); tt = T(b * .44)
                y = lp(.55 * np.sin(2 * np.pi * f * tt) + .65 * signal.sawtooth(2 * np.pi * f * tt), 520) * env(b * .44, .004, .06, .6, .06)
                por(x, y, t0 + k * b / 2, .44 if k % 2 == 0 else .26)
        # acordes
        if estAc == 'arpejo':
            for k, f in enumerate(fr): por(x, instAc(f, b * 1.1), t0 + k * b, .3)
            if c % 2 == 1: por(x, instAc(fr[1], b * 1.1), t0 + 3 * b, .22)
        elif estAc == 'bloco':
            for k in (0, 2):
                for i, f in enumerate(fr): por(x, instAc(f, b * 1.2), t0 + k * b + i * .012, .2)
        elif estAc == 'staccato':
            for k in range(8):
                for f in fr[:2]: por(x, instAc(f, b / 2), t0 + k * b / 2, .3 if k % 2 == 0 else .2)
        # LUTA: cordas em colcheia (tensao), golpe de metais no 1 e coro a cada 2 compassos (14/11: lutas mais fodas)
        if luta:
            for k in range(8):
                for f in fr[:2]: por(x, cordas_st(f * 2, b / 2), t0 + k * b / 2, .2 if k % 2 == 0 else .14)
            por(x, metais(fr[0], b * .6, 2200), t0, .35)
            if c % 2 == 0: por(x, coro(fr, 2 * barra + .4), t0, .35)
        # fundo (pad, orgao, coro, cordas)
        if fundo == 'pad': por(x, pad(fr, barra + .4, 1100), t0, .5)
        elif fundo == 'orgao' and c % 2 == 0: por(x, orgao([fb * 2, fr[1], fr[2]], 2 * barra + .2), t0, .45)
        elif fundo == 'coro' and c % 2 == 0: por(x, coro(fr, 2 * barra + .5), t0, .55)
        elif fundo == 'cordas': por(x, pad(fr, barra + .4, 2000, .004, 'sin'), t0, .4)
    # bateria: a do tema (em 4/4) ou a de luta
    if nome in P44 and not luta: padrao = P44[nome][0]
    elif luta: padrao = cfg['luta'] if cfg['beats'] == 4 else luta44()
    else: padrao = cfg['bateria']
    for c in range(comps):
        t0 = c * barra
        for (inst, pos, g) in padrao:
            if pos < 4: por(bat, inst(), t0 + pos * b, g)
        if luta and c % 4 == 3: por(bat, rufo(barra / 2), t0 + barra / 2, .6)
        if luta and c % 4 == 0: por(bat, prato(1.2), t0, .5)
        if luta: por(bat, taiko(), t0, .7); por(bat, taiko(), t0 + 2 * b, .55)
        if c % 4 == 3 and not luta and nome in ('mina', 'quartel', 'coliseu', 'fogo', 'trovao', 'selva', 'explorar'):
            for k in range(4): por(bat, tom(195 - k * 22, .24), t0 + 3 * b + k * b / 4, .4 + k * .08)
    # melodia original (entra depois de 4 compassos, como no jogo; na luta entra ja)
    q = 0.0 if luta else 4 * barra; volta = 0
    while q < D - .1:
        for n, d in mel:
            if n and q < D - .1:
                por(x, lead(hz(n, tr + 12 * oit), b * d * 1.15), q, .5)
                if luta: por(x, lead(hz(n, tr + 12 * oit - 12), b * d), q, .2)
            q += b * d
        volta += 1
        if volta > 12: break
    for ex in cfg.get('extras', []): ex(x, b, barra, comps, D, luta)
    x = reverb(x, cfg.get('sala', 1.6), cfg.get('mixRev', .22))
    if cfg.get('eco'): x = eco(x, b * .75, .28, 3)
    x = x / (np.max(np.abs(x)) + 1e-9) + reverb(bat, .7, .08) / (np.max(np.abs(bat)) + 1e-9) * (.55 if luta else .38)
    return emendar(x[:int((D + 4) * SR)], D)

if __name__ == '__main__':
    quais = sys.argv[1:] or [t for t in TEMAS if t in VAR]
    dur = {}
    for nome in quais:
        if nome == 'herobrine':
            dur['herobrine'] = salvar('herobrine', herobrine(), .9)
            continue
        dur[nome] = salvar(nome, variacao(nome, False))
        dur[nome + '_luta'] = salvar(nome + '_luta', variacao(nome, True), q='1')
    with open(f'{OUT}/duracoes.txt', 'a') as f:
        for k, v in dur.items(): f.write(f'{k} {v:.2f}\n')
