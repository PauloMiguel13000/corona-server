"""Trilha da Missao v2 (16/11, Paulo: "a musica, faca algo proximo ao que voce ouviu nos videos").

Estilo de jogo de tiro moderno (acao militar hibrida): baixo de sintetizador pulsando em semicolcheias com
"bombeamento" a cada bumbo, cordas staccato, guitarra distorcida em power chords, metais graves ("braam"),
bateria pesada com toms, subida de ruido (riser) e impacto no fim do loop. Composicao ORIGINAL do Corona Server
(nenhuma melodia de jogo nenhum), tudo sintetizado aqui.

  missao.ogg       na base e entre os setores: tensao (mesmo tom e andamento, sem guitarra, bateria leve)
  missao_luta.ogg  setor ativo: a luta (tudo junto)

Rodar: python3 gerar_missao_v2.py  -> /tmp/claude-0/sons/tiro/missao*.ogg (e imprime as duracoes)
"""
import os
import numpy as np
from scipy import signal
import gerar_trilhas_v3 as G

G.OUT = '/tmp/claude-0/sons/tiro'
os.makedirs(G.OUT, exist_ok=True)
SR = G.SR
T, Z, lp, hp, bp, env, por, mf = G.T, G.Z, G.lp, G.hp, G.bp, G.env, G.por, G.mf
G.R.g = np.random.default_rng(1617)

BPM = 124
B = 60 / BPM                 # um tempo
BAR = 4 * B
COMP = 16                    # compassos
D = COMP * BAR               # duracao do loop
# re menor: Dm - Bb - C - Am (2 compassos cada), duas vezes
ACORDES = [(38, 'm'), (34, 'M'), (36, 'M'), (33, 'm')]


def acorde_do(compasso):
    return ACORDES[(compasso // 2) % 4]


def notas(raiz, tipo):
    return [raiz, raiz + (3 if tipo == 'm' else 4), raiz + 7]


# ------------------------------------------------------------------ instrumentos novos
def baixo_synth(f, d):
    tt = T(d)
    x = .7 * signal.sawtooth(2 * np.pi * f * tt) + .5 * signal.sawtooth(2 * np.pi * f * 1.005 * tt) + .35 * np.sin(2 * np.pi * f * .5 * tt)
    corte = 300 + 1700 * np.exp(-tt * 18)                                  # filtro abre e fecha a cada nota
    y = np.zeros_like(x)
    # filtro variavel em blocos curtos (barato e soa como o "pluck" de baixo de trailer)
    blk = 256
    for i in range(0, len(x), blk):
        y[i:i + blk] = lp(x[max(0, i - 64):i + blk], corte[min(i, len(corte) - 1)], 2)[-len(x[i:i + blk]):]
    return y * env(d, .003, .06, .55, min(.04, d * .3)) * .5


def guitarra(raiz_midi, d, abafada=True):
    f = mf(raiz_midi)
    tt = T(d)
    x = np.zeros_like(tt)
    for k, g in ((1, 1), (1.5, .8), (2, .6)):                                # power chord: raiz, quinta, oitava
        for dt in (-.004, .004):
            x += g * signal.sawtooth(2 * np.pi * f * k * (1 + dt) * tt + G.R.g.random() * 6)
    x = np.tanh(x * 3.2)                                                    # distorcao
    x = bp(x, 90, 3800, 2)
    e = env(d, .002, .05 if abafada else .2, .25 if abafada else .7, .03)
    return x * e * .32


def braam(raiz_midi, d=3.2):
    tt = T(d)
    x = np.zeros_like(tt)
    for k in (0, 12, 7 + 12):
        f = mf(raiz_midi + k)
        for dt in (-.006, 0, .006):
            x += signal.sawtooth(2 * np.pi * f * (1 + dt) * tt + G.R.g.random() * 6)
    x = np.tanh(x * 1.5)
    corte = 250 + 1600 * np.clip(tt / .35, 0, 1) * np.exp(-np.clip(tt - .35, 0, None) * 1.2)
    y = np.zeros_like(x)
    for i in range(0, len(x), 512):
        y[i:i + 512] = lp(x[max(0, i - 128):i + 512], corte[i], 2)[-len(x[i:i + 512]):]
    return y * env(d, .12, .5, .6, 1.2) * .5


def bumbo_seco(g=1.0):
    tt = T(.32)
    f = 48 + 140 * np.exp(-tt * 38)
    clique = hp(G.R.g.standard_normal(len(tt)), 2500) * np.exp(-tt * 180) * .35
    return (np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-tt * 10) + clique) * g


def palma_caixa(g=1.0):
    tt = T(.3)
    ruido = bp(G.R.g.standard_normal(len(tt)), 900, 7000)
    palma = ruido * (np.exp(-tt * 40) + .5 * np.exp(-np.abs(tt - .012) * 300) + .4 * np.exp(-np.abs(tt - .024) * 300))
    corpo = np.sin(2 * np.pi * 185 * tt) * np.exp(-tt * 28) * .6
    return (palma * .8 + corpo) * g


def riser(d):
    tt = T(d)
    x = G.R.g.standard_normal(len(tt))
    y = np.zeros_like(x)
    for i in range(0, len(x), 512):
        fc = 300 + 5500 * (i / len(x)) ** 2
        y[i:i + 512] = bp(x[max(0, i - 128):i + 512], fc * .7, fc * 1.3, 2)[-len(x[i:i + 512]):]
    return y * (tt / d) ** 2 * .45


def impacto():
    tt = T(2.2)
    f = 30 + 90 * np.exp(-tt * 6)
    return (np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-tt * 2.2) + lp(G.R.g.standard_normal(len(tt)), 600) * np.exp(-tt * 5) * .5) * 1.1


def sonar(f=1320):
    tt = T(1.6)
    return np.sin(2 * np.pi * f * tt) * np.exp(-tt * 3.5) * .25


# lead: motivo original de 4 compassos (graus em re menor, em semitons acima de D4=62), tocado nos compassos 9-16
MOTIVO = [  # (inicio em tempos, duracao em tempos, nota midi)
    (0, 1.5, 62), (1.5, .5, 65), (2, 1, 64), (3, 1, 62),
    (4, 1.5, 60), (5.5, .5, 62), (6, 2, 58),
    (8, 1.5, 60), (9.5, .5, 64), (10, 1, 65), (11, 1, 67),
    (12, 2, 69), (14, 1, 67), (15, 1, 64),
]


def montar(luta):
    mus = Z(D + 4)
    bat = Z(D + 4)
    sub = Z(D + 4)                                  # baixo (leva o "bombeamento")
    kicks = []
    for c in range(COMP):
        t0 = c * BAR
        raiz, tipo = acorde_do(c)
        ns = notas(raiz, tipo)
        # baixo: semicolcheias (luta) ou colcheias (base), com o desenho raiz-raiz-raiz-terca
        passo = B / 4 if luta else B / 2
        desenho = [0, 0, 0, 0, 0, 0, 12, 0, 0, 0, 0, 0, 0, 7, 0, 3] if luta else [0, 0, 0, 12, 0, 0, 0, 7]
        for k, iv in enumerate(desenho):
            por(sub, baixo_synth(mf(raiz + iv), passo * .9), t0 + k * passo, .9 if k % 4 == 0 else .7)
        # cordas staccato em colcheias nas notas do acorde (uma oitava e meia acima)
        if luta or c >= 8:
            for k in range(8):
                n = ns[[0, 1, 2, 1, 0, 2, 1, 2][k]] + 24
                por(mus, G.cordas_st(mf(n), B / 2), t0 + k * B / 2, .55 if k % 2 == 0 else .4)
        # pad grave sustentando o acorde (so na base fica mais presente)
        if c % 2 == 0:
            por(mus, G.pad([mf(n + 12) for n in ns], 2 * BAR, corte=900 if luta else 1300), t0, .5 if luta else .9)
        # guitarra: palhetada abafada em colcheias, acento no 1 e no "e" do 3 (so na luta, a partir do compasso 5)
        if luta and c >= 4:
            ritmo = [0, .5, 1, 1.5, 2.5, 3, 3.5] if c % 4 != 3 else [0, .5, 1, 1.5, 2, 2.5, 3, 3.25, 3.5, 3.75]
            for q in ritmo:
                abre = q in (0, 2.5)
                por(mus, guitarra(raiz + 12, B * (1.4 if abre else .45), abafada=not abre), t0 + q * B, 1.0 if abre else .7)
        # braam a cada 4 compassos
        if c % 4 == 0:
            por(mus, braam(raiz - 12 if raiz > 35 else raiz, BAR * (1.0 if luta else 1.6)), t0, .85 if luta else .6)
        # motivo de metais nos compassos 9-16 (luta) / so a primeira metade, baixinho, na base
        if c in (8, 12) and (luta or c == 8):
            for (q, dd, n) in MOTIVO:
                por(mus, G.metais(mf(n), dd * B * .95, abre=2600 if luta else 1500), t0 + q * B, .75 if luta else .45)
        # sonar na base (clima de operacao)
        if not luta and c % 4 == 2:
            por(mus, sonar(), t0 + 2 * B, .8)
        # ---------------- bateria
        if luta:
            for q in (0, 1.75, 2, 2.75) if c % 2 == 0 else (0, .75, 2, 2.5, 3.25):
                por(bat, bumbo_seco(), t0 + q * B, 1.0)
                kicks.append(t0 + q * B)
            for q in (1, 3):
                por(bat, palma_caixa(), t0 + q * B, .9)
            for k in range(16):
                por(bat, G.chimbal(.05), t0 + k * B / 4, .9 if k % 2 == 0 else .5)
            if c % 4 == 3:                                                          # virada de toms
                for k, f in enumerate((180, 150, 120, 95)):
                    por(bat, G.tom(f, .35), t0 + 3 * B + k * B / 4, .9)
            if c % 8 == 0:
                por(bat, G.prato(2.2), t0, .8)
        else:
            por(bat, bumbo_seco(.8), t0, 1.0)
            kicks.append(t0)
            por(bat, bumbo_seco(.6), t0 + 2.5 * B, 1.0)
            kicks.append(t0 + 2.5 * B)
            por(bat, G.timpano(mf(raiz - 12 if raiz > 35 else raiz), 1.2), t0 + 2 * B, .45)
            for k in range(8):
                por(bat, G.chimbal(.04), t0 + k * B / 2, .35 if k % 2 else .2)
            if c % 2 == 1:
                por(bat, G.caixa(200, .18), t0 + 3.5 * B, .35)
    # riser no ultimo compasso e impacto no comeco (o loop emenda no impacto)
    por(mus, riser(BAR), D - BAR, .9 if luta else .5)
    if luta:
        por(bat, G.rufo(BAR / 2, 2.0), D - BAR / 2, 1.0)
    por(bat, impacto(), 0, .8 if luta else .5)
    # bombeamento: o baixo e o pad abaixam um pouco logo depois de cada bumbo
    pump = np.ones(len(sub))
    for k in kicks:
        i = int(k * SR)
        n = int(.18 * SR)
        if i < len(pump):
            j = min(len(pump), i + n)
            pump[i:j] = np.minimum(pump[i:j], .35 + .65 * (np.arange(j - i) / n))
    sub *= pump
    mus = G.reverb(mus, 1.4, .18) * (.6 + .4 * pump)
    bat = G.reverb(bat, .5, .06)
    # mixagem por volume medio (RMS) de cada grupo, nao por pico: senao o braam e o impacto abafam o resto.
    # O baixo ganha harmonicos (saturacao) e perde o subgrave puro: em celular abaixo de ~120 Hz nao se ouve nada.
    sub = hp(np.tanh(sub / (np.max(np.abs(sub)) + 1e-9) * 2.2), 38, 2)

    def rms(v):
        return np.sqrt(np.mean(v ** 2)) + 1e-9
    x = sub / rms(sub) * (.07 if luta else .06) + mus / rms(mus) * (.18 if luta else .17) + bat / rms(bat) * (.12 if luta else .07)
    x = hp(x, 32, 2)
    x = x + .45 * hp(x, 2200, 2)                                        # brilho (cordas, chimbal, guitarra)
    return G.emendar(x[:int((D + 4) * SR)], D)


if __name__ == '__main__':
    for nome, luta in (('missao', False), ('missao_luta', True)):
        d = G.salvar(nome, montar(luta), vol=.85, q='1')
        print(nome, round(d, 2))
