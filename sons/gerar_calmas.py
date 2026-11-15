"""15/11 (Paulo: "a musica do lobby das masmorras esta muito alta e nao combinou", "a do Farol Naufragado esta ruim",
"quando repete fica chata"). Musicas LONGAS, com varias partes diferentes (nao um trecho de 30 s em loop):

  lobby2      ~3 min, calma e baixa: harpa, piano, flauta, violoncelo, caixinha; 6 partes, cada uma com a sua frase.
  farol       ~2 min, balanco de mar em 6/8: acordeao, violino e flauta, ondas e sino de boia; 5 partes.
  farol_luta  a mesma musica, mais rapida, com tambores.

Cada parte tem um motivo de 2 compassos que anda pelos acordes e muda no fim da frase.
Rodar: python3 gerar_calmas.py -> /tmp/claude-0/sons/calmas
"""
import os, subprocess, wave, sys
import numpy as np
from scipy import signal
import gerar_trilhas_v3 as g
import gerar_lobby as Lb

SR = g.SR = Lb.SR = 32000
OUT = '/tmp/claude-0/sons/calmas'
os.makedirs(OUT, exist_ok=True)
T, Z, lp, hp, env = g.T, g.Z, g.lp, g.hp, g.env
MODOS = {'dorico': [0, 2, 3, 5, 7, 9, 10], 'eolio': [0, 2, 3, 5, 7, 8, 10], 'maior': [0, 2, 4, 5, 7, 9, 11]}


def grau(raiz, modo, d):
    e = MODOS[modo]
    return g.mf(raiz + 12 * (d // 7) + e[d % 7])


def cello(f, d):
    return lp(Lb.violino(f, d), 1500) * 1.2


def celesta(f, d):
    return g.caixinha(f, max(d, 1.2)) * .8


def ondas(D, forca=1.0):
    n = int(D * SR); tt = np.arange(n) / SR
    r = np.random.default_rng(7).standard_normal(n)
    a = lp(r, 500) * (0.5 + 0.5 * np.sin(2 * np.pi * tt / 9.0 - 1)) ** 2
    b = g.bp(r, 900, 3000) * (0.5 + 0.5 * np.sin(2 * np.pi * tt / 9.0 - 2.2)) ** 6 * .35
    return (a + b) * forca


def montar(cfg, luta=False):
    rng = np.random.default_rng(cfg['semente'] + (1 if luta else 0))
    g.R.g = rng; Lb.rng = rng
    bpm = cfg['bpm'] * (cfg.get('acelera', 1.25) if luta else 1)
    tempos = cfg['tempos']                         # tempos por compasso (4 = 4/4; 2 = 6/8 contado em 2)
    sub = cfg.get('sub', 2)                        # divisoes do tempo (2 = colcheia; 3 = tercina do 6/8)
    b = 60 / bpm; BAR = tempos * b
    comps = sum(len(p['acordes']) for p in cfg['partes'])
    D = comps * BAR
    Lb.L = Z(D + 8); Lb.Rr = Z(D + 8)
    por = Lb.por
    raiz, modo = cfg['raiz'], cfg['modo']
    c0 = 0
    for ip, p in enumerate(cfg['partes']):
        ac = p['acordes']; n = len(ac)
        lead, glead, pan = p.get('lead'), p.get('glead', .5), p.get('pan', 0)
        for c in range(n):
            t0 = (c0 + c) * BAR; d = ac[c]
            tri = [grau(raiz, modo, d + k) for k in (0, 2, 4)]
            fb = grau(raiz - 24, modo, d)
            if fb < 50: fb *= 2
            # fundo
            for nome, gan in p.get('fundo', {}).items():
                if nome == 'cordas': por(Lb.cordas(tri, BAR + 1.0, 1700), t0, gan, -.3)
                elif nome == 'pad': por(g.pad(tri, BAR + 1.0, 900), t0, gan, .2)
                elif nome == 'coro' and c % 2 == 0: por(g.coro(tri, 2 * BAR + .6, 700), t0, gan, .1)
                elif nome == 'baixo': por(Lb.baixo(fb, BAR * .9), t0, gan, 0)
                elif nome == 'acordeao':
                    for k in range(tempos):
                        for f in tri: por(g.acordeao(f, b * .8), t0 + k * b, gan * (1 if k == 0 else .6), -.35)
            # arpejo
            arp = p.get('arpejo')
            if arp:
                inst, gan, ordem = arp
                seq = tri + [f * 2 for f in tri]
                passo = b / sub
                for k in range(tempos * sub):
                    o = ordem[k % len(ordem)]
                    if o is None: continue
                    por(inst(seq[o], passo * 2), t0 + k * passo + rng.normal(0, .004), gan * (1 if k % sub == 0 else .75), .4)
            # bateria (so na luta, ou se a parte pedir)
            if luta:
                por(g.taiko(), t0, .55, 0); por(g.bumbo(), t0 + b * tempos / 2, .5, 0)
                for k in range(tempos * sub):
                    if k % sub: por(g.chimbal(), t0 + k * b / sub, .1, .3)
                por(g.caixa(), t0 + b * (tempos - 1), .3, .15)
                if c % 4 == 3: por(g.rufo(BAR / 2), t0 + BAR / 2, .3, 0)
                for f in tri[:2]:
                    for k in range(tempos * sub): por(g.cordas_st(f, b / sub), t0 + k * b / sub, .13, -.2)
        # melodia: motivo de 2 compassos que anda pelos acordes; a ultima vez de cada frase usa o "fim"
        if lead:
            mot, fim = p['motivo'], p.get('fim', p['motivo'])
            for c in range(0, n, 2):
                if c < p.get('entra', 0): continue
                m = fim if (c // 2) % 2 == 1 else mot
                q = (c0 + c) * BAR; lim = q + 2 * BAR
                for dg, du in m:
                    if q >= lim - .01: break
                    if dg is not None:
                        d = ac[min(n - 1, c + int((q - (c0 + c) * BAR) / BAR + 1e-6))]
                        f = grau(raiz, modo, d + dg) * p.get('oit', 1)
                        por(lead(f, b * du * 1.1), q, glead, pan)
                        if p.get('dobra'): por(p['dobra'](f * p.get('oitDobra', 2), b * du), q, glead * .4, -pan + .2)
                    q += b * du
        c0 += n
    for ex in cfg.get('extras', []): ex(por, D, b, BAR, luta)
    L2 = Lb_rev(Lb.L, cfg.get('sala', 3.0), cfg.get('rev', .32), 1); R2 = Lb_rev(Lb.Rr, cfg.get('sala', 3.0), cfg.get('rev', .32), 2)
    est = np.stack([L2, R2]); n = int(round(D * SR))
    if cfg.get('emenda', True):
        y = est[:, :n].copy(); cauda = est[:, n:]; y[:, :cauda.shape[1]] += cauda
    else:                                          # termina de verdade (o jogo espera um pouco e toca de novo)
        y = est[:, :n + int(5 * SR)].copy(); f = int(5 * SR); y[:, -f:] *= np.linspace(1, 0, f) ** 2
    return y, y.shape[1] / SR


def Lb_rev(x, seg, mix, semente):
    rr = np.random.default_rng(semente); n = int(seg * SR); tt = np.arange(n) / SR
    ir = lp(rr.standard_normal(n), 4500) * np.exp(-tt * 6.9 / seg); ir[0] = 0; ir /= np.sqrt(np.sum(ir ** 2))
    return x * (1 - mix) + signal.fftconvolve(x, ir)[:len(x)] * mix * 1.3


def salvar(nome, y, vol, q='2'):
    y = y - y.mean(axis=1, keepdims=True)
    pk = np.percentile(np.abs(y), 99.9); y = np.tanh(y / pk * .85); y /= np.max(np.abs(y)); y *= vol
    w = f'{OUT}/{nome}.wav'
    with wave.open(w, 'wb') as f:
        f.setnchannels(2); f.setsampwidth(2); f.setframerate(SR); f.writeframes((y.T * 32767).astype(np.int16).tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', w, '-c:a', 'libvorbis', '-q:a', q, f'{OUT}/{nome}.ogg'], check=True)
    os.remove(w)
    rms = 20 * np.log10(np.sqrt(np.mean(y ** 2)) + 1e-9)
    print(f'{nome:12s} {os.path.getsize(f"{OUT}/{nome}.ogg") // 1024:5d} KB {y.shape[1] / SR:6.2f} s  rms {rms:.1f} dB', flush=True)


# ------------------------------------------------------------------ LOBBY: calmo, baixo, 6 partes
A1 = [0, 1, 2, 3, 4, 3, 2, 1]
A2 = [0, 2, 4, 2, 5, 4, 2, 1]
A3 = [0, None, 2, None, 4, None, 3, None]


def sinos_lobby(por, D, b, BAR, luta):
    r = np.random.default_rng(3)
    t = 6.0
    while t < D - 6:
        por(g.sino(g.mf(74 + int(r.choice([0, 5, 7, 12]))), 4.0), t, .10, float(r.uniform(-.7, .7)))
        t += float(r.uniform(14, 26))


LOBBY = dict(semente=61, bpm=62, tempos=4, raiz=62, modo='dorico', sala=3.4, rev=.36, emenda=False, extras=[sinos_lobby], partes=[
    # entrada: so a harpa e o colchao
    dict(acordes=[0, 0, 3, 3], fundo={'pad': .5}, arpejo=(Lb.harpa, .3, A3)),
    # A: piano conta a frase
    dict(acordes=[0, 3, 6, 4, 0, 3, 5, 0], fundo={'cordas': .42, 'baixo': .3}, arpejo=(Lb.harpa, .24, A1), lead=Lb.piano, glead=.5, pan=-.15, oit=2,
         motivo=[(4, 2), (2, 1), (4, 1), (7, 3), (None, 1)], fim=[(5, 1.5), (4, .5), (2, 2), (0, 3), (None, 1)]),
    # B: flauta responde, mais aberta
    dict(acordes=[5, 2, 3, 0, 5, 2, 4, 4], fundo={'cordas': .5, 'baixo': .32}, arpejo=(Lb.harpa, .26, A2), lead=Lb.flauta, glead=.36, pan=.2, oit=2,
         motivo=[(0, 1), (2, 1), (4, 2), (5, 1), (4, 1), (2, 2)], fim=[(4, 1), (5, 1), (7, 2), (4, 3), (None, 1)]),
    # C: violoncelo embaixo, caixinha em cima
    dict(acordes=[3, 0, 6, 2, 3, 0, 4, 0], fundo={'pad': .45, 'baixo': .3}, arpejo=(celesta, .16, A3), lead=cello, glead=.5, pan=-.25, oit=.5,
         motivo=[(0, 3), (2, 1), (4, 2), (2, 2)], fim=[(4, 2), (2, 1), (1, 1), (0, 4)]),
    # A': a frase do piano volta na flauta, com coro bem longe
    dict(acordes=[0, 3, 6, 4, 0, 3, 5, 0], fundo={'cordas': .5, 'coro': .22, 'baixo': .3}, arpejo=(Lb.harpa, .24, A1), lead=Lb.flauta, glead=.36, pan=.1, oit=2, dobra=Lb.piano, oitDobra=.5,
         motivo=[(4, 2), (2, 1), (4, 1), (7, 3), (None, 1)], fim=[(5, 1.5), (4, .5), (2, 2), (0, 3), (None, 1)]),
    # D: respiro (quase nada), piano solto
    dict(acordes=[5, 5, 3, 3, 0, 0], fundo={'pad': .5}, arpejo=(Lb.piano, .2, [0, None, None, 2, None, 4, None, None]), lead=celesta, glead=.2, pan=.3, oit=2,
         motivo=[(4, 4), (2, 4)], fim=[(1, 4), (0, 4)]),
    # saida
    dict(acordes=[0, 3, 0, 0], fundo={'pad': .45, 'cordas': .3}, arpejo=(Lb.harpa, .24, A3)),
])


# ------------------------------------------------------------------ FAROL: 6/8 de mar
S1 = [0, 1, 2, 3, 2, 1]
S2 = [0, 2, 4, 5, 4, 2]


def mar(por, D, b, BAR, luta):
    o = ondas(D + 4, .9 if not luta else .5)
    o = o / (np.max(np.abs(o)) + 1e-9)
    por(o, 0, .30, -.5); por(np.roll(o, SR * 2), 0, .30, .5)
    t = BAR * 2
    k = 0
    while t < D - 3:                                   # sino da boia
        por(g.sino(g.mf(64 if k % 2 == 0 else 71), 3.0), t, .16, .6)
        t += BAR * 4; k += 1


E = 1 / 3                                               # uma colcheia do 6/8 (tempo = seminima pontuada)
FAROL = dict(semente=88, bpm=54, tempos=2, sub=3, raiz=64, modo='eolio', sala=2.6, rev=.3, extras=[mar], acelera=1.3, partes=[
    # entrada: ondas, harpa e sino
    dict(acordes=[0, 0, 6, 0], fundo={'pad': .4}, arpejo=(Lb.harpa, .3, S1)),
    # A: acordeao canta
    dict(acordes=[0, 0, 6, 0, 2, 6, 4, 0], fundo={'cordas': .35, 'baixo': .4}, arpejo=(Lb.harpa, .24, S1), lead=g.acordeao, glead=.42, pan=-.1, oit=1,
         motivo=[(0, 2 * E), (2, E), (4, 2 * E), (4, E), (3, E), (2, E), (3, E), (4, 3 * E)],
         fim=[(4, 2 * E), (2, E), (0, 2 * E), (2, E), (0, 3 * E), (None, 3 * E)]),
    # B: violino sobe (relativo maior)
    dict(acordes=[2, 6, 0, 4, 2, 6, 4, 0], fundo={'cordas': .42, 'baixo': .4, 'acordeao': .1}, arpejo=(Lb.harpa, .22, S2), lead=Lb.violino, glead=.42, pan=.15, oit=2,
         motivo=[(4, 3 * E), (2, 2 * E), (4, E), (5, 3 * E), (4, 3 * E)],
         fim=[(4, 2 * E), (5, E), (4, 2 * E), (2, E), (0, 4 * E), (None, 2 * E)]),
    # C: fantasma (flauta sozinha sobre coro, sem baixo)
    dict(acordes=[5, 5, 3, 3, 0, 0, 4, 4], fundo={'pad': .4, 'coro': .3}, arpejo=(celesta, .14, [0, None, 2, None, 4, None]), lead=Lb.flauta, glead=.36, pan=.25, oit=2,
         motivo=[(0, 4 * E), (2, 2 * E), (4, 6 * E)], fim=[(2, 4 * E), (1, 2 * E), (0, 6 * E)]),
    # D: violoncelo e harpa, a dois
    dict(acordes=[3, 0, 3, 4, 3, 0, 6, 4], fundo={'pad': .35, 'baixo': .35}, arpejo=(Lb.harpa, .28, S2), lead=cello, glead=.5, pan=-.2, oit=.5,
         motivo=[(4, 3 * E), (2, 3 * E), (0, 2 * E), (2, E), (4, 3 * E)], fim=[(2, 3 * E), (4, 2 * E), (2, E), (0, 6 * E)]),
    # A': todos juntos
    dict(acordes=[0, 0, 6, 0, 2, 6, 4, 0], fundo={'cordas': .45, 'baixo': .42, 'acordeao': .1}, arpejo=(Lb.harpa, .24, S1), lead=g.acordeao, glead=.4, pan=-.1, oit=1, dobra=Lb.violino, oitDobra=2,
         motivo=[(0, 2 * E), (2, E), (4, 2 * E), (4, E), (3, E), (2, E), (3, E), (4, 3 * E)],
         fim=[(4, 2 * E), (2, E), (0, 2 * E), (2, E), (0, 3 * E), (None, 3 * E)]),
    # saida que cai no comeco
    dict(acordes=[3, 6, 0, 0], fundo={'pad': .4, 'cordas': .3}, arpejo=(Lb.harpa, .26, S2)),
])
# luta: a mesma musica sem as partes paradas
FAROL_LUTA = dict(FAROL, partes=[FAROL['partes'][1], FAROL['partes'][2], FAROL['partes'][4], FAROL['partes'][5]])

if __name__ == '__main__':
    quais = sys.argv[1:] or ['lobby2', 'farol', 'farol_luta']
    dur = {}
    for nome in quais:
        cfg, luta, vol = {'lobby2': (LOBBY, False, .5), 'farol': (FAROL, False, .7), 'farol_luta': (FAROL_LUTA, True, .78)}[nome]
        y, d = montar(cfg, luta)
        salvar(nome, y, vol, '1' if nome == 'lobby2' else '2')
        dur[nome] = d
    with open(f'{OUT}/duracoes.txt', 'a') as f:
        for k, v in dur.items(): f.write(f'{k} {v:.2f}\n')
