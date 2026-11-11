import sys
sys.path.insert(0, '/tmp/claude-0/novo/fontes/sons')
from gerar_trilhas import *   # noqa

def chefe():
    bpm, cps = 162.0, 28
    b = 60 / bpm
    D = cps * 4 * b + 3
    x = zeros(D)
    prog = ['A2', 'A2', 'F2', 'G2', 'A2', 'A2', 'D3', 'E3']
    riff = ['A2', 'A2', 'A3', 'A2', 'C3', 'A2', 'E3', 'D3']
    tema = [('A4', .5), ('C5', .5), ('E5', 1), ('D5', .5), ('C5', .5), ('B4', 1),
            ('A4', .5), ('B4', .5), ('C5', .5), ('E5', .5), ('D5', 1), ('A4', 1),
            ('F4', .5), ('G4', .5), ('A4', 1), ('E5', .5), ('D5', .5), ('C5', 2)]
    for c in range(cps):
        t0 = c * 4 * b
        nb = nf(prog[c % len(prog)])
        # baixo picotado em semicolcheias
        for k in range(16):
            f = nf(riff[(c * 4 + k) % len(riff)]) if k % 2 == 0 else nb
            por(x, baixo(f, b * 0.22, 700), t0 + k * b / 4, 0.46 if k % 4 == 0 else 0.26)
        # metais socando
        for k in (0, 1.5, 2, 3.5):
            por(x, metal(nb * 4, b * 0.42, 0.016, 200, 3600), t0 + k * b, 0.4)
        # bateria pesada e rapida
        por(x, bumbo(0.34, 180, 42), t0, 1.15)
        por(x, bumbo(0.3, 170, 44), t0 + 0.75 * b, 0.8)
        por(x, bumbo(0.3, 170, 44), t0 + 2 * b, 0.95)
        por(x, bumbo(0.28, 165, 44), t0 + 2.75 * b, 0.7)
        por(x, caixa(0.24, 180), t0 + b, 1.0)
        por(x, caixa(0.24, 180), t0 + 3 * b, 1.0)
        for k in range(16):
            por(x, chimbal(0.06, k == 15), t0 + k * b / 4, 0.38 if k % 4 == 0 else 0.16)
        if c % 4 == 3:
            for k in range(8):
                por(x, tom(215 - k * 15, 0.18), t0 + 3 * b + k * b / 8, 0.45 + k * 0.07)
        if c % 8 == 0:
            por(x, coro([nf('A3'), nf('C4'), nf('E4')], b * 8), t0, 0.42)
    q = 4 * 4 * b
    while q < D - 3:
        for n2, dur in tema:
            if q >= D - 3: break
            por(x, metal(nf(n2), b * dur * 1.1, 0.018, 260, 4200), q, 0.5)
            q += b * dur
    return emendar(x, 2.2)

salvar('chefe', chefe(), vol=0.88, q='1')
