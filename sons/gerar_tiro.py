"""Trilhas dos jogos de tiro (15/11): Modo Horda (calma entre ondas, luta, chefe) e Missao (estilo militar: marcha e
luta). Usa os instrumentos e a montagem do gerar_trilhas_v3. Rodar: python3 gerar_tiro.py  -> /tmp/claude-0/sons/tiro"""
import os, sys
import gerar_trilhas_v3 as G
G.OUT = '/tmp/claude-0/sons/tiro'
os.makedirs(G.OUT, exist_ok=True)
TIM, BUM, CX, HH, TAI = G.TIM, G.BUM, G.CX, G.HH, G.TAI
T = {
    'horda': dict(bpm=84, beats=4, raiz=50, modo='frigio', prog=[0, 0, 5, 1, 0, 3, 1, 0], harm='pad', corteHarm=900, baixo='drone',
                  lead=G.oboe, ritmos=[[2, 1, 1], [1.5, .5, 2], [3, 1]], bateria=[(TIM(50), 0, .5), (TAI(), 2.5, .3)],
                  luta=[(BUM(), 0, 1), (BUM(), 1.5, .7), (CX(), 2, .8), (BUM(), 2.5, .6), (CX(), 3.5, .5)] + [(HH(), k / 2, .28) for k in range(8)],
                  sala=2.4, mixRev=.3),
    'horda_chefe': dict(bpm=138, beats=4, raiz=45, modo='harm', prog=[0, 5, 3, 4, 0, 5, 1, 4], harm='staccato', baixo='pulso',
                        lead=G.metais, ritmos=[[1, .5, .5, 1, 1], [.5, .5, 1, 2], [1.5, .5, 1, 1]], bateria=[(BUM(), 0, 1), (CX(), 2, .8)],
                        luta=[(BUM(), 0, 1), (BUM(), .75, .7), (CX(), 1, .9), (BUM(), 2, 1), (BUM(), 2.5, .7), (CX(), 3, .9), (CX(), 3.75, .5)] + [(HH(), k / 2, .3) for k in range(8)],
                        sala=1.4),
    # 15/11 (Paulo: "faca uma musica de boss para os modos de tiro"): mais pesada e mais longa que a horda_chefe (16 compassos)
    'chefe': dict(bpm=152, beats=4, compassos=32, raiz=43, modo='harm', prog=[0, 0, 5, 4, 0, 3, 1, 4, 5, 5, 3, 3, 1, 1, 4, 4, 0, 5, 3, 4, 0, 5, 1, 4, 3, 3, 1, 1, 4, 4, 4, 4], harm='staccato', baixo='pulso',
                  lead=G.metais, ritmos=[[.5, .5, 1, .5, .5, 1], [1, .5, .5, 2], [1.5, .5, .5, .5, 1], [.5, .5, .5, .5, 2]], bateria=[(BUM(), 0, 1), (CX(), 2, .8)],
                  luta=[(BUM(), 0, 1), (BUM(), .5, .8), (CX(), 1, 1), (BUM(), 1.75, .7), (BUM(), 2, 1), (BUM(), 2.5, .8), (CX(), 3, 1), (CX(), 3.5, .6), (CX(), 3.75, .6)] + [(HH(), k / 4, .22) for k in range(16)],
                  sala=1.5),
    'missao': dict(bpm=108, beats=4, raiz=52, modo='dorico', prog=[0, 0, 3, 4, 0, 6, 3, 4], harm='staccato', baixo='pulso',
                   lead=G.metais, oitLead=0, ritmos=[[1, 1, 2], [.5, .5, 1, 1, 1], [2, .5, .5, 1]],
                   bateria=[(CX(), 0, .5), (CX(), .5, .3), (CX(), .75, .3), (CX(), 1, .5), (CX(), 2, .5), (CX(), 2.5, .3), (CX(), 3, .5), (CX(), 3.5, .4), (BUM(.8), 0, .7), (BUM(.8), 2, .6)],
                   luta=[(BUM(), 0, 1), (CX(), 1, .9), (BUM(), 1.5, .7), (BUM(), 2, .9), (CX(), 3, .9), (CX(), 3.5, .5)] + [(HH(), k / 2, .3) for k in range(8)],
                   sala=1.3),
}
dur = {}
import sys
SO = sys.argv[1:]
for nome, lutas in (('horda', (False, True)), ('horda_chefe', (True,)), ('missao', (False, True)), ('chefe', (True,))):
    if SO and nome not in SO: continue
    G.TEMAS[nome] = T[nome]
    for l in lutas:
        n = nome + ('_luta' if l and len(lutas) > 1 else '')
        dur[n] = G.salvar(n, G.montar(nome, T[nome], luta=l), q='1')
        print(n, round(dur[n], 2))
open(G.OUT + '/duracoes.txt', 'a' if SO else 'w').write(''.join(f'{k} {v:.2f}\n' for k, v in dur.items()))
