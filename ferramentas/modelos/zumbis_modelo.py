"""Infectados esculpidos (15/11, 3a versao): modelo proprio por tipo, com silhueta de monstro (nao o boneco do jogo).
Comeca pelo TANQUE: corcunda, tronco enorme, cabeca pequena afundada nos ombros, bracos gigantes ate o chao.
Desenho original do Corona Server. Itens corona:props/anim_z<tipo>_f0|f1 (formato dos animatronicos).
uso: PYTHONPATH=plugins/coronapack python3 zumbis_modelo.py <saida> [tipos]
"""
import os
import sys

import numpy as np

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)
import animatronicos3d as A  # noqa: E402
import zumbis_hd as Z  # noqa: E402

C = A.C

COR = {
    'zcomum': dict(pele=(128, 142, 118), camisa=(72, 98, 132), calca=(62, 54, 48), olho=(236, 228, 170), cabelo=(40, 32, 26)),
    'zcorredor': dict(pele=(124, 110, 106), camisa=(44, 52, 62), calca=(50, 46, 40), faixa=(152, 148, 134), olho=(255, 96, 40)),
    'ztanque': dict(pele=(176, 124, 112), calca=(52, 56, 68), olho=(255, 230, 170)),
    'zcuspidor': dict(pele=(152, 162, 122), camisa=(178, 170, 118), calca=(40, 48, 38), bolha=(150, 240, 60), olho=(222, 255, 120), cabelo=(30, 24, 22)),
    'zexplosivo': dict(pele=(152, 142, 112), camisa=(44, 56, 76), calca=(42, 46, 38), bolha=(214, 200, 120), olho=(255, 204, 90)),
    'zsoldado': dict(pele=(196, 150, 120), camisa=(64, 74, 92), calca=(56, 62, 76), couro=(30, 32, 34), metal=(60, 66, 70), faixa=(24, 24, 26), olho=(232, 228, 220), bolha=(255, 220, 110), lente=(96, 128, 156)),
    'zescopeta': dict(pele=(176, 130, 100), camisa=(150, 154, 158), calca=(52, 56, 64), couro=(32, 34, 36), metal=(66, 70, 72), faixa=(26, 26, 28), olho=(232, 228, 220), bolha=(255, 220, 110), lente=(104, 130, 152)),
    'zatirador': dict(pele=(190, 146, 116), camisa=(74, 90, 60), calca=(62, 76, 50), couro=(86, 100, 60), metal=(48, 50, 46), faixa=(40, 46, 34), olho=(232, 228, 220), bolha=(255, 60, 40), lente=(40, 60, 70)),
    'zescudo': dict(pele=(186, 140, 112), camisa=(38, 42, 52), calca=(32, 36, 44), couro=(22, 24, 28), metal=(88, 94, 106), faixa=(20, 20, 24), olho=(232, 228, 220), bolha=(255, 220, 110), lente=(60, 90, 120)),
    'zpesado': dict(pele=(180, 136, 108), camisa=(60, 62, 66), calca=(50, 52, 56), couro=(34, 36, 40), metal=(72, 74, 80), faixa=(26, 26, 28), olho=(255, 170, 60), bolha=(255, 200, 90), lente=(40, 44, 50)),
    # 16/11: chefe pesado proprio do Corona (blindagem grafite, faixas laranja de alerta, visor e nucleo da mochila
    # brilhando laranja). Inspirado no "soldado blindado de metralhadora" generico, nao no visual de nenhum jogo.
    'zjuggernaut': dict(pele=(52, 50, 48), camisa=(52, 50, 48), calca=(46, 44, 42), couro=(36, 37, 40), metal=(86, 92, 100), faixa=(232, 122, 30), olho=(255, 150, 40), bolha=(255, 220, 120)),
    'zchefe': dict(pele=(152, 62, 60), couro=(98, 72, 48), metal=(106, 100, 94), faixa=(142, 130, 110), calca=(142, 130, 110), olho=(255, 44, 30)),
}
FIXO = dict(sapato=(30, 26, 24), carne=(132, 20, 22), osso=(216, 206, 178), boca=(26, 6, 8), dente=(224, 214, 182))


class Tela(Z.Tela):
    def __init__(self, wpx, hpx, esc, seed):
        self.w, self.h, self.seed, self.esc = wpx / esc, hpx / esc, seed, esc
        yy, xx = np.mgrid[0:hpx, 0:wpx]
        self.X, self.Y = (xx + 0.5) / esc, (yy + 0.5) / esc
        self.U, self.V = self.X / self.w, self.Y / self.h
        self.rgb = np.zeros((hpx, wpx, 3))
        self.a = np.ones((hpx, wpx))

    def img(self):
        out = np.zeros(self.rgb.shape[:2] + (4,), np.uint8)
        out[..., :3] = (np.clip(self.rgb, 0, 1) * 255 + 0.5).astype(np.uint8)
        out[..., 3] = 255
        return out


def pintar_face(c, f, wpx, hpx, sem, ident, esc):
    mat = c.pele
    cor = dict(FIXO)
    cor.update(COR[ident])
    t = Tela(wpx, hpx, esc, sem)
    grande = min(t.w, t.h) >= 3.0
    bri = np.zeros((hpx, wpx))
    if ident == 'zjuggernaut':
        return pintar_blindado(c, f, t, cor, mat, sem, bri)
    if ident in TATICOS:
        return pintar_tatico(c, f, t, cor, mat, sem, bri)
    if mat in ('pele', 'pele2'):
        p = cor['pele'] if mat == 'pele' else tuple(int(v * 0.8) for v in cor['pele'])
        t.base(p, 0.34, 0.55)
        t.manchas(tuple(int(v * 0.5) for v in p), 0.58, 0.5, 0.55)
        t.manchas((150, 60, 70), 0.66, 0.35, 0.4, 3)                      # vermelhidao
        # fibras do musculo ao longo do lado maior
        if t.h >= t.w:
            fib = Z.fbm(t.X * 2.6, t.Y * 0.35, sem + 5, 3)
        else:
            fib = Z.fbm(t.X * 0.35, t.Y * 2.6, sem + 5, 3)
        t.rgb *= (0.8 + 0.4 * fib)[..., None]
        v = np.abs(Z.fbm(t.X * 0.8, t.Y * 0.8, sem + 13, 3) - 0.5) < 0.02
        t.cor(v, (84, 40, 70), 0.45)
        if grande:
            # feridas: so em uma parte das faces, cada uma num lugar e tamanho (antes repetia igual em todas)
            n = (sem * 2654435761 >> 7) % 100
            if n < 30:
                cx, cy = 0.2 + (n % 7) * 0.09, 0.25 + (n % 5) * 0.12
                t.rasgo(cx, cy, 0.07 + (n % 4) * 0.03, 0.04 + (n % 3) * 0.025, cor['carne'], sem)
                if n % 2 == 0:
                    t.sangue(cx, cy + 0.05, 0.07 + (n % 3) * 0.03, 0.12 + (n % 4) * 0.08, sem)
            elif n < 45:
                t.manchas((96, 14, 16), 0.63, 0.9, 0.55, sem % 9)
            if c.nome.startswith('peito') and f == 'north':
                t.mult(Z.elipse(t.U, t.V, 0.5, 0.92, 0.5, 0.1, 1.0), 0.55)
            if c.nome == 'abdomen' and f == 'north':
                t.mult(np.clip(1 - np.abs(t.U - 0.5) * 26, 0, 1), 0.55)
                for k in range(3):
                    t.mult(np.clip(1 - np.abs(t.V - (0.25 + k * 0.27)) * 26, 0, 1) * (np.abs(t.U - 0.5) < 0.36), 0.62)
        if c.nome.startswith('punho'):
            t.cor(np.clip((t.V - 0.35) * 2.2, 0, 1) * (0.5 + Z.fbm(t.X, t.Y, sem + 50)), (96, 10, 12), 0.8)
            if f in ('north', 'down'):
                t.mult((np.mod(t.U * 4, 1) < 0.1), 0.45)
        if c.nome == 'cabeca' and f == 'north' and not getattr(c, 'sem_rosto', False):
            for cx in (0.27, 0.73):
                t.mult(Z.elipse(t.U, t.V, cx, 0.4, 0.2, 0.15, 0.6), 0.12)
            t.mult(Z.elipse(t.U, t.V, 0.5, 0.58, 0.08, 0.1, 0.6), 0.35)
            t.cor(Z.elipse(t.U, t.V, 0.5, 0.9, 0.34, 0.14, 0.3), cor['boca'])
            den = (np.mod(t.U * 7, 1.0) > 0.25) * (t.V > 0.8) * (t.V < 0.9) * (np.abs(t.U - 0.5) < 0.3)
            t.cor(den, cor['dente'], 0.95)
            t.sangue(0.5, 0.96, 0.2, 0.1, 1)
        t.borda(0.7, 0.3)
    elif mat in ('calca', 'camisa', 'couro', 'faixa'):
        t.base(cor[mat], 0.22)
        t.dobras(0.32)
        if mat == 'faixa':
            t.mult(np.mod(t.Y * 0.9 + t.X * 0.25, 1.0) < 0.14, 0.6)
            t.manchas((110, 20, 20), 0.6, 0.8, 0.6)
        elif grande:
            n = (sem * 2654435761 >> 7) % 100
            if n < 35:
                t.rasgo(0.25 + (n % 6) * 0.1, 0.3 + (n % 5) * 0.12, 0.1 + (n % 4) * 0.04, 0.04 + (n % 3) * 0.02, tuple(int(v * 0.8) for v in cor['pele']), sem)
            if 20 <= n < 55 or mat == 'couro':
                t.sangue(0.2 + (n % 7) * 0.1, 0.15 + (n % 4) * 0.15, 0.08 + (n % 5) * 0.03, 0.1 + (n % 3) * 0.1, sem)
            t.manchas(tuple(int(v * 0.45) for v in cor[mat]), 0.6, 0.7, 0.5, sem % 7)
        t.sujeira(0.45, 0.55)
        t.borda(0.74, 0.28)
    elif mat == 'bolha':
        t.base(cor['bolha'], 0.25)
        t.cor(Z.elipse(t.U, t.V, 0.35, 0.3, 0.3, 0.3, 0.8), (255, 255, 220), 0.6)
        t.borda(0.6, 0.3)
        bri[:] = 0.55
    elif mat == 'metal':
        t.base(cor['metal'], 0.3)
        t.manchas((120, 62, 30), 0.52, 0.9, 0.75)
        t.manchas((96, 10, 12), 0.66, 0.6, 0.8, 5)
        t.borda(0.6, 0.18)
    elif mat == 'cabelo':
        t.base(cor['cabelo'], 0.2)
        t.rgb *= (0.6 + 0.7 * Z.ruido(t.X * 7, t.Y * 0.8, sem + 31))[..., None]
    elif mat == 'olho':
        t.base(cor['olho'], 0.05)
        t.cor(Z.elipse(t.U, t.V, 0.5, 0.5, 0.22, 0.3, 0.5), (20, 8, 8))
        bri[:] = 1.0
    else:
        t.base(cor[mat], 0.3)
        if mat == 'carne':
            t.manchas((70, 6, 8), 0.5, 1.2, 0.7)
        t.borda(0.7, 0.2)
    return t.img(), bri


TATICOS = ('zsoldado', 'zescopeta', 'zatirador', 'zescudo', 'zpesado')


def pintar_tatico(c, f, t, cor, mat, sem, bri):
    """16/11 (Paulo mandou fotos dos inimigos do jogo de tiro): soldado de verdade, limpo: farda camuflada digital,
    colete com fitas MOLLE e bolsas, balaclava e luvas pretas, capacete com bisel, olhos de gente (sem brilho)."""
    U, V, X, Y = t.U, t.V, t.X, t.Y
    nome = c.nome
    if mat in ('camisa', 'calca'):
        base = np.array(cor[mat], float)
        t.base(cor[mat], 0.08, 0.6)
        n1 = Z.fbm(X * 0.32, Y * 0.32, sem % 5 + 11)                          # camuflagem de manchas grandes e suaves
        n2 = Z.fbm(X * 0.32 + 9.1, Y * 0.32, sem % 5 + 23)
        t.cor(np.clip((n1 - 0.55) * 6, 0, 1), tuple(base * 0.78), 0.8)
        t.cor(np.clip((n2 - 0.6) * 6, 0, 1), tuple(np.minimum(base * 1.15, 255)), 0.7)
        t.dobras(0.1, 0.8)
        t.borda(0.78, 0.18)
    elif mat == 'couro' and nome.startswith('ghillie'):                # capuz de folhas (atirador)
        t.base(cor['couro'], 0.2, 1.2)
        for k, tom in enumerate(((52, 66, 36), (110, 120, 70), (78, 70, 44))):
            n = Z.fbm(X * 1.4 + k * 5.3, Y * 1.4, sem + 70 + k)
            t.cor((n > 0.57).astype(float), tom, 0.9)
        t.mult((Z.ruido(X * 4, Y * 1.2, sem + 90) > 0.8).astype(float), 0.6)
    elif mat == 'couro':
        t.base(cor['couro'], 0.1, 0.7)
        if min(t.w, t.h) >= 3.0 and nome == 'colete':
            molle = (np.mod(Y, 1.5) < 0.22) * (np.mod(X, 2.0) > 0.3)
            t.mult(molle.astype(float), 0.8)
        if nome.startswith(('bolsa', 'radio')) and f == 'north':
            t.mult((V < 0.3).astype(float), 0.78)                               # aba da bolsa
        t.borda(0.62, 0.2)
    elif mat == 'faixa':
        t.base(cor['faixa'], 0.14, 1.4)
        t.rgb *= (0.9 + 0.2 * Z.ruido(X * 5, Y * 5, sem + 4))[..., None]
        t.borda(0.75, 0.2)
    elif mat == 'metal':
        t.base(cor['metal'], 0.1, 0.6)
        t.cor(np.clip(1 - V * t.h / 0.4, 0, 1), tuple(min(255, v + 70) for v in cor['metal']), 0.35)
        t.mult(np.clip(1 - (1 - V) * t.h / 0.4, 0, 1), 0.62)
        risco = (Z.ruido(X * 7.0, Y * 1.1, sem + 61) > 0.88) * (Z.ruido(X * 1.3, Y * 9.0, sem + 62) > 0.5)
        t.cor(risco.astype(float), tuple(min(255, v + 60) for v in cor['metal']), 0.5)
        t.borda(0.7, 0.18)
    elif mat in ('pele', 'pele2'):
        p = cor['pele'] if mat == 'pele' else tuple(int(v * 0.85) for v in cor['pele'])
        t.base(p, 0.12, 0.8)
        t.borda(0.85, 0.25)
    elif mat == 'olho':
        if nome.startswith('olho'):                                        # olho de gente: branco e iris escura
            t.base((226, 222, 214), 0.05)
            t.cor(Z.elipse(U, V, 0.5, 0.5, 0.3, 0.45, 0.5), (46, 36, 28))
        else:                                                              # visor, luneta, visor do escudo
            top = np.array(cor['lente'], float) / 255 * 1.6
            bot = np.array(cor['lente'], float) / 255 * 0.7
            t.rgb[:] = np.clip(top * (1 - V[..., None]) + bot * V[..., None], 0, 1)
            t.cor((np.abs((U * 0.6 - V) + 0.05) < 0.07).astype(float), (230, 236, 240), 0.45)
            if nome.startswith('visor'):
                t.rgb[:] = np.clip(t.rgb * 0.8 + np.array(cor['olho'], float) / 255 * 0.12, 0, 1)
                bri[:] = 0.3
        t.borda(0.8, 0.15)
    elif mat == 'faixa' and nome == 'filtro_boca':
        t.base((30, 30, 32), 0.05)
        t.cor(((np.mod(X * 3, 1) < 0.35) * (np.mod(Y * 3, 1) < 0.35)).astype(float), (8, 8, 10), 0.9)
    elif mat == 'lente':
        top = np.array(cor['lente'], float) / 255 * 1.7
        bot = np.array(cor['lente'], float) / 255 * 0.6
        t.rgb[:] = np.clip(top * (1 - V[..., None]) + bot * V[..., None], 0, 1)
        t.cor((np.abs((U * 0.5 - V) + 0.1) < 0.09).astype(float), (240, 240, 230), 0.5)
        t.borda(0.55, 0.22)
    elif mat == 'bolha':
        t.base(cor['bolha'], 0.2)
        t.cor(Z.elipse(U, V, 0.5, 0.5, 0.3, 0.3, 0.8), (255, 255, 230), 0.7)
        bri[:] = 0.6
    else:
        t.base(cor.get(mat, FIXO.get(mat, (40, 38, 36))), 0.15)
        t.borda(0.7, 0.2)
    return t.img(), bri


def pintar_blindado(c, f, t, cor, mat, sem, bri):
    """Pintura limpa de armadura militar: placas com bisel, linhas de painel, rebites e riscos; tecido acolchoado;
    borracha com nervuras; faixas laranja com listras de alerta; visor/nucleo com degrade e reflexo."""
    U, V, X, Y = t.U, t.V, t.X, t.Y
    if mat == 'metal':
        t.base(cor['metal'], 0.1, 0.6)
        if t.w >= 7:
            t.mult(np.abs(U - 0.5) * t.w < 0.1, 0.66)                        # uma linha de painel so nas placas grandes
        t.cor(np.clip(1 - V * t.h / 0.45, 0, 1), (200, 208, 216), 0.35)          # bisel claro em cima
        t.mult(np.clip(1 - (1 - V) * t.h / 0.45, 0, 1), 0.6)                     # sombra embaixo
        t.mult(np.clip(1 - np.minimum(U, 1 - U) * t.w / 0.35, 0, 1), 0.78)
        risco = (Z.ruido(X * 7.0, Y * 1.1, sem + 61) > 0.86) * (Z.ruido(X * 1.3, Y * 9.0, sem + 62) > 0.5)
        t.cor(risco.astype(float), (176, 182, 188), 0.55)
        if t.w >= 3 and t.h >= 3:
            for cx in (0.12, 0.88):
                for cy in (0.14, 0.86):
                    m = (np.abs(U - cx) * t.w < 0.22) * (np.abs(V - cy) * t.h < 0.22)
                    t.cor(m.astype(float), (40, 42, 46), 0.8)
        t.sujeira(0.75, 0.25)
    elif mat == 'faixa':
        t.base(cor['faixa'], 0.12, 0.8)
        if c.nome.startswith(('ombreira_faixa', 'elmo_crista', 'anel')) and max(t.w, t.h) >= 2.5:
            lst = np.mod((X + Y) * 0.55, 1.0) < 0.5
            t.cor(lst.astype(float), (28, 26, 24), 0.85)
        t.mult(Z.ruido(X * 6, Y * 6, sem + 3) > 0.9, 0.7)
        t.borda(0.55, 0.2)
    elif mat in ('camisa', 'calca', 'pele', 'pele2'):
        base = cor['camisa'] if mat in ('camisa', 'pele', 'pele2') else cor['calca']
        t.base(base, 0.16, 0.9)
        q = (np.abs(np.mod((X + Y) * 0.7, 1.0) - 0.5) < 0.06) | (np.abs(np.mod((X - Y) * 0.7, 1.0) - 0.5) < 0.06)
        t.mult(q.astype(float), 0.62)                                            # costura acolchoada em losango
        t.rgb *= (0.92 + 0.16 * Z.fbm(X * 0.9, Y * 0.9, sem + 8))[..., None]
        t.borda(0.7, 0.22)
    elif mat in ('couro', 'sapato'):
        t.base(cor['couro'] if mat == 'couro' else (30, 29, 28), 0.12)
        nerv = np.mod(Y * 1.6 if t.h >= t.w else X * 1.6, 1.0) < 0.22
        t.mult(nerv.astype(float), 0.7)
        t.borda(0.6, 0.25)
    elif mat == 'olho':
        top = np.array((255, 214, 120), float) / 255
        bot = np.array((214, 84, 18), float) / 255
        t.rgb[:] = top * (1 - V[..., None]) + bot * V[..., None]
        gl = (np.abs((U * 0.6 - V) + 0.05) < 0.06) * (U < 0.55)
        t.cor(gl.astype(float), (255, 246, 220), 0.5)
        t.borda(0.75, 0.12)
        bri[:] = 1.0
    elif mat == 'bolha':
        t.base(cor['bolha'], 0.2)
        t.cor(Z.elipse(U, V, 0.5, 0.5, 0.3, 0.3, 0.8), (255, 255, 230), 0.7)
        bri[:] = 0.6
    else:
        t.base(cor.get(mat, (60, 60, 60)), 0.2)
        t.borda(0.7, 0.2)
    return t.img(), bri


def esp(cs):
    """Espelha para o lado esquerdo tudo que tem nome terminado em _d."""
    out = list(cs)
    for c in cs:
        if not c.nome.endswith('_d'):
            continue
        rot = c.rot
        if rot:
            rot = (rot[0], rot[1], (16 - rot[2][0], rot[2][1], rot[2][2]))
        out.append(C(c.nome[:-2] + '_e', 16 - c.ate[0], 16 - c.de[0], c.de[1], c.ate[1], c.de[2], c.ate[2], c.pele, rot, c.emite, c.so))
    return out


def ztanque(q):
    at = q == 1
    cs = []
    inc = ('x', -22.5, (8.0, -3.0, 9.0))                                # tronco curvado para a frente
    # pernas curtas e grossas
    pernas(cs, q, afast=2.7, larg=4.0, topo=-4.0, zc=8.6, comp=12.0)
    cs += [C('quadril', 3.0, 13.0, -5.6, -2.0, 5.6, 11.6, 'calca')]
    # tronco: abdomen, peito largo, costas e corcova
    cs += [C('abdomen', 3.0, 13.0, -2.6, 3.4, 4.8, 11.6, 'pele', inc),
           C('peito', 0.6, 15.4, 2.6, 10.6, 3.2, 12.6, 'pele', inc),
           C('peito_placa_d', 1.4, 7.7, 5.0, 9.8, 2.3, 3.6, 'pele', inc),
           C('costas', 1.6, 14.4, 4.0, 11.6, 12.2, 14.2, 'pele2', inc),
           C('corcova', 2.6, 13.4, 10.2, 13.4, 5.4, 13.4, 'pele', inc),
           C('corcova2', 4.6, 11.4, 13.0, 14.8, 7.6, 12.6, 'pele2', inc),
           C('costela_d', 2.2, 4.0, -1.6, 2.6, 4.2, 5.2, 'pele2', inc),
           C('tumor_d', 1.6, 4.4, 11.0, 13.6, 4.2, 7.0, 'pele2', inc),
           C('ferida_peito', 9.0, 12.2, 5.6, 8.2, 2.9, 3.3, 'carne', inc, so=('north',))]
    for k in range(3):
        cs.append(C('espinha', 7.2, 8.8, 5.4 + k * 2.6, 7.0 + k * 2.6, 14.0, 15.2 - k * 0.2, 'osso', inc))
    # cabeca pequena, afundada na frente do peito
    boca = ('x', 22.5, (8.0, 9.4, -1.0)) if at else None
    cs += [C('pescoco', 5.6, 10.4, 7.4, 10.4, 0.2, 3.6, 'pele2'),
           C('cabeca', 5.5, 10.5, 8.6, 13.6, -3.2, 1.8, 'pele'),
           C('testa', 5.3, 10.7, 11.9, 12.9, -3.7, -2.8, 'pele2'),
           C('queixo', 5.9, 10.1, 7.2, 8.9, -3.8, 0.2, 'pele2', boca),
           C('olho_d', 6.1, 7.2, 10.9, 11.6, -3.36, -3.2, 'olho', emite=13, so=('north',)),
           C('cranio', 6.4, 9.6, 13.4, 14.0, -2.4, 1.2, 'pele2')]
    for k in range(4):
        x = 6.3 + k * 0.95
        cs.append(C('dente', x, x + 0.55, 8.9, 9.6, -3.7, -3.3, 'dente', boca))
    # bracos gigantes: ombro, braco, antebraco e punho no chao
    sob = ('x', 45.0, (0.0, 8.0, 3.0)) if at else None
    cs += [C('ombro_d', -4.6, 2.4, 4.6, 12.4, -1.6, 7.0, 'pele', sob),
           C('ombro_calo_d', -3.6, 0.6, 11.8, 13.6, 0.2, 5.0, 'pele2', sob),
           C('braco_d', -4.2, 1.6, -3.4, 5.2, -1.0, 5.6, 'pele', sob),
           C('cotovelo_d', -4.8, -3.4, -2.6, 0.6, 1.0, 6.4, 'pele2', sob),
           C('antebraco_d', -5.2, 2.0, -12.6, -2.8, -4.0, 4.2, 'pele', sob),
           C('veia_d', -5.6, -5.0, -10.0, -4.0, -2.0, 0.0, 'carne', sob),
           C('punho_d', -5.6, 2.6, -16.0, -12.0, -6.0, 2.6, 'pele2', sob),
           C('dedao_d', 2.4, 3.8, -15.4, -12.6, -4.6, -1.6, 'pele2', sob)]
    return esp(cs)


def pernas(cs, q, afast=1.9, larg=3.0, topo=-4.0, mat='calca', pe='sapato', zc=8.5, comp=12.0, joelho=None):
    """Duas pernas que alternam o passo: q=0 passo A, q=2 passo B, q=1 (ataque) paradas."""
    for sgn, lado in ((1, 'r'), (-1, 'l')):
        cx = 8 - sgn * afast
        ang = 22.5 * sgn if q == 0 else (-22.5 * sgn if q == 2 else 0.0)
        rot = ('x', ang, (cx, topo, zc)) if ang else None
        h = larg / 2
        cs += [C('coxa_' + lado, cx - h, cx + h, topo - comp * 0.5, topo, zc - h - 0.3, zc + h + 0.3, mat, rot),
               C('canela_' + lado, cx - h + 0.3, cx + h - 0.3, topo - comp + 1.6, topo - comp * 0.5 + 0.4, zc - h, zc + h, mat, rot),
               C('pe_' + lado, cx - h, cx + h, topo - comp, topo - comp + 1.8, zc - h - 2.0, zc + h, pe, rot)]
        if joelho:
            ky = topo - comp * 0.5
            cs.append(C('joelho_' + lado, cx - h + 0.2, cx + h - 0.2, ky - 1.0, ky + 0.9, zc - h - 0.6, zc - h + 0.4, joelho, rot))


def cabeca(cs, y0, z0, at, s=4.8, cx=8.0, mat='pele', dentes=True):
    h = s / 2
    boca = ('x', 22.5, (cx, y0 + 0.8, z0 + 1.5)) if at else None
    cs += [C('cabeca', cx - h, cx + h, y0, y0 + s, z0, z0 + s, mat),
           C('testa', cx - h - 0.2, cx + h + 0.2, y0 + s * 0.66, y0 + s * 0.86, z0 - 0.5, z0 + 0.4, 'pele2'),
           C('queixo', cx - h + 0.4, cx + h - 0.4, y0 - 1.3, y0 + 0.4, z0 - 0.6, z0 + 2.6, 'pele2', boca),
           C('olho_r', cx - 1.75, cx - 0.75, y0 + s * 0.5, y0 + s * 0.5 + 0.7, z0 - 0.16, z0, 'olho', emite=13, so=('north',)),
           C('olho_l', cx + 0.75, cx + 1.75, y0 + s * 0.5, y0 + s * 0.5 + 0.7, z0 - 0.16, z0, 'olho', emite=13, so=('north',))]
    if dentes:
        for k in range(4):
            x = cx - 1.7 + k * 0.95
            cs.append(C('dente', x, x + 0.55, y0 + 0.4, y0 + 1.0, z0 - 0.5, z0 - 0.1, 'dente', boca))


def zcomum(q):
    at = q == 1
    cs = []
    inc = ('x', -22.5, (8.0, -4.0, 8.5))
    pernas(cs, q)
    cs += [C('quadril', 4.4, 11.6, -5.6, -3.0, 6.4, 10.6, 'calca'),
           C('tronco', 4.2, 11.8, -3.4, 7.6, 6.0, 11.0, 'camisa', inc),
           C('ombros', 3.2, 12.8, 6.2, 8.4, 6.2, 10.8, 'camisa', inc),
           C('ferida', 4.8, 8.2, -1.2, 3.8, 5.6, 6.1, 'carne', inc, so=('north',)),
           C('tripa', 5.4, 7.4, -3.4, -0.8, 5.0, 6.0, 'carne', inc),
           C('pescoco', 6.8, 9.2, 6.4, 9.4, 2.4, 5.2, 'pele2')]
    for k in range(3):
        cs.append(C('costela', 5.0, 8.0, 0.2 + k * 1.2, 0.8 + k * 1.2, 5.3, 5.7, 'osso', inc))
    cabeca(cs, 8.4, -0.8, at)
    cs += [C('cabelo_t', 5.4, 10.6, 12.8, 13.8, 0.0, 4.2, 'cabelo'), C('cabelo_l', 5.2, 6.0, 10.2, 13.2, 1.2, 4.2, 'cabelo'),
           C('cabelo_n', 5.6, 10.4, 9.6, 13.2, 3.8, 4.6, 'cabelo'), C('cranio', 8.6, 10.4, 13.0, 13.7, -0.4, 1.2, 'carne')]
    # braco direito esticado para a frente; o esquerdo pendurado balancando
    ar = ('x', 22.5, (2.8, 5.8, 3.0)) if at else (('x', -22.5, (2.8, 5.8, 3.0)) if q == 2 else None)
    cs += [C('manga_r', 1.4, 4.2, 4.4, 7.2, 0.4, 4.4, 'camisa', ar), C('braco_r', 1.7, 3.9, 4.7, 6.9, -6.6, 0.6, 'pele', ar),
           C('mao_r', 1.5, 4.1, 4.3, 7.3, -8.6, -6.4, 'pele2', ar), C('dedos_r', 1.7, 3.9, 4.4, 5.0, -10.0, -8.4, 'pele2', ar)]
    al = ('x', 45.0, (13.2, 6.0, 4.0)) if at else ('x', 22.5 if q == 0 else -22.5, (13.2, 6.0, 4.0))
    cs += [C('manga_l', 11.8, 14.6, 2.0, 6.6, 2.4, 5.6, 'camisa', al), C('braco_l', 12.1, 14.3, -4.0, 2.2, 2.8, 5.2, 'pele', al),
           C('osso_l', 12.7, 13.7, -1.4, 0.4, 2.2, 3.0, 'osso', al), C('mao_l', 11.9, 14.5, -6.4, -3.8, 2.4, 5.4, 'pele2', al)]
    return cs


def zcorredor(q):
    at = q == 1
    cs = []
    pernas(cs, q, afast=2.2, larg=3.0, topo=-4.0, zc=9.5, comp=12.0, pe='pele2')
    inc = ('x', -45.0, (8.0, -3.0, 10.0))
    cs += [C('quadril', 4.0, 12.0, -6.0, -2.6, 7.4, 12.0, 'calca'),
           C('tronco', 4.0, 12.0, -3.0, 7.0, 7.0, 11.6, 'camisa', inc),
           C('bolso', 5.2, 10.8, -2.0, 1.4, 6.4, 7.2, 'camisa', inc),
           C('costas', 4.6, 11.4, 0.0, 6.4, 11.4, 12.6, 'camisa', inc),
           C('cabeca', 5.6, 10.4, 2.6, 7.4, -4.4, 0.4, 'pele'),
           C('queixo', 6.0, 10.0, 1.4, 3.0, -4.9, -2.0, 'pele2', ('x', 22.5, (8.0, 3.0, -2.0)) if at else None),
           C('olho_r', 6.2, 7.2, 5.0, 5.7, -4.56, -4.4, 'olho', emite=13, so=('north',)),
           C('olho_l', 8.8, 9.8, 5.0, 5.7, -4.56, -4.4, 'olho', emite=13, so=('north',)),
           C('capuz_t', 5.0, 11.0, 7.2, 8.6, -4.6, 1.8, 'camisa'), C('capuz_f', 5.0, 11.0, 6.4, 7.6, -5.4, -4.4, 'camisa'),
           C('capuz_n', 5.0, 11.0, 2.2, 8.2, 0.4, 2.6, 'camisa'), C('capuz_p', 6.4, 9.6, 4.6, 7.6, 2.6, 3.8, 'camisa'),
           C('capuz_d', 4.4, 5.6, 2.0, 8.0, -4.2, 1.8, 'camisa')]
    for k in range(4):
        x = 6.3 + k * 0.95
        cs.append(C('dente', x, x + 0.55, 3.0, 3.6, -4.8, -4.4, 'dente'))
    sob = ('x', 45.0, (2.6, 3.0, 1.0)) if at else None
    cs += [C('ombro_d', 1.0, 4.2, 1.6, 5.2, -1.4, 3.0, 'camisa', sob), C('braco_d', 1.3, 3.9, -5.6, 2.2, -2.4, 1.0, 'camisa', sob),
           C('faixa_d', 1.1, 4.1, -10.6, -5.4, -3.4, 0.4, 'faixa', sob), C('mao_d', 1.0, 4.2, -13.6, -10.4, -4.6, -0.4, 'pele2', sob),
           C('garra1_d', 1.2, 1.9, -16.0, -13.4, -4.8, -4.1, 'osso', sob), C('garra2_d', 2.3, 3.0, -16.0, -13.4, -5.0, -4.3, 'osso', sob),
           C('garra3_d', 3.4, 4.1, -16.0, -13.4, -4.8, -4.1, 'osso', sob), C('joelheira_d', 4.0, 7.6, -10.6, -8.4, 7.4, 8.4, 'faixa')]
    return esp(cs)


def zcuspidor(q):
    at = q == 1
    cs = []
    pernas(cs, q, afast=1.6, larg=2.4, topo=-3.0, comp=13.0, pe='pele2')
    inc = ('x', -22.5, (8.0, -2.0, 8.5))
    cs += [C('quadril', 5.0, 11.0, -5.0, -2.2, 6.6, 10.4, 'calca'),
           C('barriga', 5.0, 11.0, -2.6, 2.0, 5.8, 10.4, 'pele'),
           C('tronco', 5.0, 11.0, 1.4, 7.4, 6.2, 10.2, 'camisa', inc),
           C('ombros', 4.0, 12.0, 6.2, 7.8, 6.6, 9.8, 'pele2', inc),
           C('pescoco', 6.8, 9.2, 6.0, 13.6, 1.8, 4.2, 'pele', ('x', -22.5, (8.0, 6.0, 4.0))),
           C('papo', 6.4, 9.6, 7.0, 10.4, 0.4, 2.4, 'bolha', ('x', -22.5, (8.0, 6.0, 4.0)), emite=9)]
    c = C('cabeca', 5.6, 10.4, 12.4, 17.2, -4.6, 0.2, 'pele')
    c.sem_rosto = True
    cs += [c, C('testa', 5.4, 10.6, 15.4, 16.4, -5.1, -4.2, 'pele2'),
           C('olho_r', 6.0, 7.0, 15.6 - 1.0, 15.3, -4.76, -4.6, 'olho', emite=13, so=('north',)),
           C('olho_l', 9.0, 10.0, 14.6, 15.3, -4.76, -4.6, 'olho', emite=13, so=('north',)),
           C('goela', 6.6, 9.4, 9.8 if at else 10.6, 14.2, -5.0, -4.5, 'carne'),
           C('mandibula', 6.2, 9.8, 8.6 if at else 9.6, 10.4 if at else 11.2, -5.2, -1.2, 'carne'),
           C('baba', 7.4, 8.6, 4.6, 9.8, -5.1, -4.7, 'bolha', emite=13),
           C('baba2', 6.6, 7.2, 7.2, 9.6, -4.9, -4.5, 'bolha', emite=13),
           C('cabelo_t', 5.4, 10.6, 17.0, 17.9, -4.0, 0.6, 'cabelo'), C('cabelo_n', 5.4, 10.6, 12.6, 17.4, 0.2, 1.0, 'cabelo'),
           C('rabo', 7.0, 9.0, 7.0, 14.0, 1.0, 2.2, 'cabelo'), C('cabelo_d', 5.0, 5.6, 13.6, 17.4, -3.0, 0.6, 'cabelo')]
    for k in range(3):
        x = 6.8 + k * 0.9
        cs.append(C('dente', x, x + 0.5, 13.6, 14.4, -5.3, -4.9, 'dente'))
    sob = ('x', 22.5, (3.7, 6.2, 6.0)) if at else None
    cs += [C('braco_d', 2.9, 4.5, -0.4, 6.8, 4.8, 6.8, 'pele', sob), C('ante_d', 2.9, 4.4, -1.4, 0.4, -2.6, 5.2, 'pele2', sob),
           C('mao_d', 2.7, 4.6, -1.8, 0.6, -4.4, -2.4, 'pele2', sob),
           C('garra1_d', 2.8, 3.2, -1.2, -0.6, -6.8, -4.2, 'osso', sob), C('garra2_d', 3.5, 3.9, -0.8, -0.2, -7.2, -4.2, 'osso', sob),
           C('garra3_d', 4.1, 4.5, -1.2, -0.6, -6.6, -4.2, 'osso', sob)]
    return esp(cs)


def zexplosivo(q):
    at = q == 1
    cs = []
    pernas(cs, q, afast=3.0, larg=3.8, topo=-7.0, comp=9.0)
    g45 = lambda cx, cy, cz: ('z', 45.0, (cx, cy, cz))
    cs += [C('barriga', 1.2, 14.8, -7.0, 5.0, 2.6, 13.0, 'pele'),
           C('barriga_f', 2.6, 13.4, -5.8, 3.6, 0.6, 2.8, 'pele'),
           C('barriga_ff', 4.4, 11.6, -4.4, 1.8, -0.6, 0.8, 'pele2'),
           C('barriga_b', 3.0, 13.0, -9.0, -6.6, 3.2, 11.4, 'pele2'),
           C('camisa', 1.6, 14.4, 4.6, 10.2, 4.0, 12.4, 'camisa'),
           C('camisa_f', 3.0, 13.0, 3.2, 8.6, 2.4, 4.2, 'camisa'),
           C('ferida', 9.0, 12.0, -3.4, -0.6, 0.3, 0.7, 'carne', so=('north',)),
           C('bolha1', 3.6, 5.8, -2.6, -0.4, -0.2, 1.0, 'bolha', emite=8), C('bolha2', 7.0, 9.4, -4.2, -1.8, -1.4, -0.4, 'bolha', emite=8),
           C('bolha3', 10.4, 12.2, 0.4, 2.2, 0.0, 1.0, 'bolha', emite=8), C('bolha4', 5.4, 7.0, 0.8, 2.4, -0.4, 0.8, 'bolha', emite=8),
           C('bolha5', 0.6, 1.6, -3.0, -0.6, 5.0, 7.4, 'bolha', emite=8), C('bolha6', 14.4, 15.4, -5.0, -2.8, 6.0, 8.2, 'bolha', emite=8),
           C('bolha7', 6.0, 9.0, -1.0, 2.0, 13.0, 14.0, 'bolha', emite=8),
           C('papada', 4.8, 11.2, 9.0, 11.4, 1.8, 6.6, 'pele2'),
           C('tumor', 8.8, 11.2, 13.6, 15.8, 3.2, 5.8, 'bolha', emite=8), C('tumor2', 4.6, 5.8, 11.6, 13.4, 3.4, 5.4, 'bolha', emite=8)]
    cabeca(cs, 10.2, 2.6, at, s=4.8)
    sob = ('x', 45.0, (0.3, 8.0, 7.5)) if at else None
    cs += [C('ombro_d', -1.8, 2.2, 4.0, 9.8, 5.0, 10.4, 'camisa', sob), C('braco_d', -2.0, 1.8, -2.6, 4.4, 4.6, 9.2, 'pele', sob),
           C('mao_d', -1.8, 1.6, -5.4, -2.4, 4.8, 8.6, 'pele2', sob), C('bolha_b_d', -2.6, -1.8, 0.0, 2.0, 5.6, 7.6, 'bolha', sob, emite=8)]
    del g45
    return esp(cs)


def zchefe(q):
    at = q == 1
    cs = []
    pernas(cs, q, afast=2.4, larg=3.4, topo=-3.0, comp=13.0, mat='faixa', pe='pele2')
    inc = ('x', -22.5, (8.0, -3.0, 8.5))
    cs += [C('quadril', 3.6, 12.4, -5.0, -2.0, 5.8, 11.2, 'couro'),
           C('tronco', 2.8, 13.2, -3.0, 9.4, 5.0, 11.6, 'pele', inc),
           C('avental', 3.8, 12.2, -11.0, 3.4, 3.6, 4.6, 'couro'),
           C('avental_p', 4.6, 11.4, 2.6, 6.6, 2.4, 3.2, 'couro', inc),
           C('alca_d', 4.8, 6.0, 6.0, 10.4, 4.6, 11.8, 'couro', inc),
           C('cinto', 2.6, 13.4, -3.4, -1.6, 4.4, 11.8, 'metal'),
           C('gancho', 12.6, 13.6, -8.0, -3.0, 4.0, 4.8, 'metal'), C('osso_cinto', 2.2, 3.2, -8.6, -3.0, 5.0, 6.0, 'osso'),
           C('corcova', 4.0, 12.0, 8.6, 11.0, 6.0, 12.0, 'pele2', inc),
           C('pescoco', 6.4, 9.6, 8.0, 10.8, 1.6, 5.0, 'pele2')]
    cabeca(cs, 10.2, -1.2, at, s=5.2, mat='pele')
    cs += [C('gaiola_h', 5.0, 11.0, 13.2, 13.8, -1.8, 4.4, 'metal'), C('gaiola_h2', 5.0, 11.0, 10.4, 11.0, -1.8, 4.4, 'metal'),
           C('gaiola_t', 7.6, 8.4, 15.2, 16.0, -1.6, 4.2, 'metal'), C('gaiola_v1', 5.6, 6.1, 10.2, 15.6, -1.8, -1.3, 'metal'),
           C('gaiola_v2', 7.75, 8.25, 10.2, 15.8, -1.8, -1.3, 'metal'), C('gaiola_v3', 9.9, 10.4, 10.2, 15.6, -1.8, -1.3, 'metal'),
           C('espeto1', 6.2, 7.0, 15.4, 17.4, 0.6, 1.4, 'osso'), C('espeto2', 9.0, 9.8, 15.4, 18.0, 1.8, 2.6, 'osso')]
    # ombreiras com espinhos
    cs += [C('ombreira_d', -1.0, 4.4, 6.6, 10.6, 2.6, 9.6, 'metal'), C('espinho1_d', 0.2, 1.2, 10.4, 13.2, 4.0, 5.0, 'osso'),
           C('espinho2_d', 2.2, 3.2, 10.4, 12.4, 6.6, 7.6, 'osso')]
    cs = esp(cs)
    # braco direito com o cutelo; o esquerdo de garras
    ar = ('x', 45.0, (1.6, 7.0, 6.0)) if at else (('x', 22.5, (1.6, 7.0, 6.0)) if q == 2 else None)
    cs += [C('braco_r', -0.4, 3.4, -1.6, 7.0, 4.0, 8.0, 'pele', ar), C('ante_r', -0.2, 3.2, -8.0, -1.2, 2.6, 6.8, 'faixa', ar),
           C('mao_r', 0.0, 3.0, -10.6, -7.6, 1.4, 5.2, 'pele2', ar), C('cabo', 1.0, 2.0, -10.2, -8.6, -3.6, 1.6, 'couro', ar),
           C('lamina', 1.2, 1.8, -15.0, -6.4, -11.4, -3.2, 'metal', ar), C('fio', 1.25, 1.75, -15.6, -14.8, -11.4, -3.2, 'carne', ar),
           C('arame', 0.9, 2.1, -12.0, -11.2, -9.0, -5.0, 'osso', ar)]
    al = ('x', 45.0, (14.4, 7.0, 6.0)) if at else ('x', 22.5 if q == 0 else -22.5, (14.4, 7.0, 6.0))
    cs += [C('braco_l', 12.6, 16.4, -1.6, 7.0, 4.0, 8.0, 'pele', al), C('ante_l', 12.8, 16.2, -8.0, -1.2, 4.2, 7.8, 'faixa', al),
           C('mao_l', 12.6, 16.4, -10.8, -7.6, 3.8, 8.0, 'pele2', al), C('garra1_l', 12.8, 13.5, -14.0, -10.4, 3.8, 4.5, 'osso', al),
           C('garra2_l', 14.2, 14.9, -14.4, -10.4, 3.6, 4.3, 'osso', al), C('garra3_l', 15.6, 16.3, -14.0, -10.4, 3.8, 4.5, 'osso', al)]
    return cs


CORPOS_SOLDADOS = {'z' + t: (lambda q, t=t: soldado2(q, t)) for t in ('soldado', 'escopeta', 'atirador', 'escudo', 'pesado')}
CORPOS_SOLDADOS['zjuggernaut'] = lambda q: soldado(q, 'juggernaut')
CORPOS = dict(zcomum=zcomum, zcorredor=zcorredor, ztanque=ztanque, zcuspidor=zcuspidor, zexplosivo=zexplosivo, zchefe=zchefe)
CORPOS.update(CORPOS_SOLDADOS)
for k in CORPOS:
    A.PALETAS[k] = {}


def soldado2(q, tipo):
    """16/11 (2a versao, Paulo: "caolhos", "textura de merda"): soldado com proporcao de gente, segurando a arma com as
    duas maos na pose de mira (nao os bracos esticados de zumbi), capacete arredondado com abafador, oculos escuros
    (sem olho pintado), colete com bolsas, joelheiras, luvas e botas. Quadro 1 = tiro (coice e clarao)."""
    at = q == 1
    pes = tipo == 'pesado'
    L = 1.12 if pes else 1.0
    cs = []
    pernas(cs, q, afast=1.75 * L, larg=3.1 * L, topo=-3.0, comp=13.0, pe='faixa', zc=8.2, joelho='couro')
    cx = 8.0
    w = 3.8 * L
    cs += [C('quadril', cx - 3.6 * L, cx + 3.6 * L, -3.4, -0.4, 6.2, 10.2, 'calca'),
           C('cinto', cx - 3.9 * L, cx + 3.9 * L, -1.0, 0.4, 5.9, 10.5, 'faixa'),
           C('tronco', cx - w, cx + w, -0.4, 8.6, 6.0, 10.4, 'camisa'),
           C('colete', cx - w - 0.35, cx + w + 0.35, 0.6, 8.0, 5.4, 11.0, 'couro'),
           C('bolsa1', cx - 3.0 * L, cx - 1.3 * L, 1.0, 3.6, 4.7, 5.4, 'couro'), C('bolsa2', cx - 0.85, cx + 0.85, 1.0, 3.6, 4.7, 5.4, 'couro'),
           C('bolsa3', cx + 1.3 * L, cx + 3.0 * L, 1.0, 3.6, 4.7, 5.4, 'couro'),
           C('radio', cx + 2.2 * L, cx + 3.3 * L, 4.6, 7.2, 4.9, 5.5, 'faixa'),
           C('pescoco', cx - 1.2, cx + 1.2, 8.4, 9.8, 7.0, 9.4, 'faixa'),
           C('cabeca', cx - 2.4, cx + 2.4, 9.4, 13.9, 5.8, 10.6, 'faixa'),
           # mascara de gas (como os inimigos das fotos): placa de borracha, duas lentes e o filtro na frente da boca
           C('mascara', cx - 2.2, cx + 2.2, 9.7, 12.8, 5.3, 5.85, 'couro'),
           C('lente_dir', cx - 1.85, cx - 0.35, 11.0, 12.4, 5.1, 5.35, 'lente'), C('lente_esq', cx + 0.35, cx + 1.85, 11.0, 12.4, 5.1, 5.35, 'lente'),
           C('filtro', cx - 0.8, cx + 0.8, 9.3, 10.7, 4.4, 5.3, 'metal'), C('filtro_boca', cx - 0.55, cx + 0.55, 9.55, 10.45, 4.25, 4.4, 'faixa', so=('north',)),
           C('capacete', cx - 3.0, cx + 3.0, 12.5, 14.5, 5.2, 11.2, 'metal'),
           C('capacete_topo', cx - 2.4, cx + 2.4, 14.5, 15.1, 5.8, 10.6, 'metal'),
           C('capacete_aba', cx - 2.8, cx + 2.8, 12.3, 12.8, 4.9, 5.4, 'metal'),
           C('abafador_dir', cx - 3.25, cx - 2.55, 10.4, 12.6, 7.0, 9.6, 'couro'), C('abafador_esq', cx + 2.55, cx + 3.25, 10.4, 12.6, 7.0, 9.6, 'couro'),
           C('suporte', cx - 0.6, cx + 0.6, 13.0, 14.0, 4.8, 5.2, 'faixa')]
    if tipo == 'atirador':               # capuz de folhas por cima do capacete e nos ombros
        cs += [C('ghillie', cx - 3.4, cx + 3.4, 12.0, 15.4, 5.6, 11.8, 'couro'), C('ghillie_o', cx - w - 0.9, cx + w + 0.9, 5.4, 8.8, 6.6, 11.6, 'couro')]
    elif pes:                           # capacete pesado com viseira e mochila de municao
        cs += [C('visor', cx - 2.6, cx + 2.6, 10.0, 12.6, 5.0, 5.35, 'olho', emite=6, so=('north',)),
               C('mochila', cx - 2.8, cx + 2.8, 0.4, 7.6, 11.0, 13.8, 'metal'), C('ombreira_dir', cx - w - 1.4, cx - w + 0.4, 6.0, 8.8, 5.6, 10.6, 'metal'),
               C('ombreira_esq', cx + w - 0.4, cx + w + 1.4, 6.0, 8.8, 5.6, 10.6, 'metal')]
    else:
        cs += [C('hidratacao', cx - 2.4, cx + 2.4, 1.0, 7.0, 11.0, 12.4, 'couro')]
    # bracos: ombro, braco caindo e antebraco para a frente (mao direita no punho, esquerda no guarda-mao)
    rec = 0.6 if at else 0.0                                               # coice
    ox_d, ox_e = cx - w - 1.6, cx + w
    if tipo == 'escudo':
        cs += [C('braco_dir', ox_d, ox_d + 1.8, 4.4, 8.4, 6.6, 9.0, 'camisa'), C('ante_dir', ox_d, ox_d + 1.8, 4.0, 5.8, 2.4 + rec, 7.0, 'camisa'),
               C('luva_dir', ox_d + 0.1, ox_d + 1.7, 3.9, 5.7, 1.0 + rec, 2.6 + rec, 'faixa'),
               C('pistola', ox_d + 0.4, ox_d + 1.4, 4.8, 6.0, -2.6 + rec, 1.2 + rec, 'metal'),
               C('braco_esq', ox_e, ox_e + 1.8, 4.4, 8.4, 6.6, 9.0, 'camisa'), C('ante_esq', ox_e, ox_e + 1.8, 3.4, 5.2, 1.0, 7.0, 'camisa'),
               C('escudo', cx - 4.6, cx + 5.6, -12.0, 9.2, -1.4, -0.6, 'metal'),
               C('escudo_visor', cx - 1.8, cx + 2.8, 5.2, 7.2, -1.6, -1.4, 'olho', so=('north',)),
               C('escudo_faixa', cx - 4.6, cx + 5.6, -1.6, -0.8, -1.55, -1.4, 'faixa', so=('north',))]
        boca = (ox_d + 0.9, 5.5, -3.2 + rec)
    else:
        gx = cx - 1.2                                                      # linha da arma (lado direito do peito)
        cs += [C('braco_dir', ox_d, ox_d + 1.8, 4.4, 8.4, 6.6, 9.4, 'camisa'),
               C('ante_dir', ox_d + 0.2, ox_d + 2.0, 3.8, 5.6, 2.8 + rec, 7.2, 'camisa'),
               C('luva_dir', gx - 1.6, gx - 0.1, 3.8, 5.4, 1.6 + rec, 3.2 + rec, 'faixa'),
               C('braco_esq', ox_e, ox_e + 1.8, 4.4, 8.4, 6.6, 9.4, 'camisa'),
               C('cotovelo_esq', gx + 1.6, ox_e + 1.8, 4.0, 5.6, 5.6, 7.6, 'camisa'),
               C('ante_esq', gx + 0.6, gx + 2.4, 4.0, 5.6, -3.0 + rec, 7.0, 'camisa'),
               C('luva_esq', gx - 0.4, gx + 1.4, 3.8, 5.4, -4.6 + rec, -2.8 + rec, 'faixa')]
        if pes:                         # metralhadora pesada com cano grosso e caixa de municao
            cs += [C('arma', gx - 1.2, gx + 1.2, 4.2, 6.8, -3.0 + rec, 7.6 + rec, 'metal'), C('canos', gx - 0.7, gx + 0.7, 4.8, 6.2, -10.4 + rec, -3.0 + rec, 'faixa'),
                   C('caixa', gx - 1.0, gx + 1.0, 1.6, 4.2, 0.0 + rec, 3.0 + rec, 'couro'), C('mira', gx - 0.4, gx + 0.4, 6.8, 7.6, 0.0 + rec, 2.4 + rec, 'faixa')]
            boca = (gx, 5.5, -10.8 + rec)
        elif tipo == 'escopeta':        # espingarda: cano curto e grosso, bomba de madeira
            cs += [C('coronha', gx - 0.6, gx + 0.6, 4.4, 6.6, 6.0 + rec, 9.0 + rec, 'couro'), C('arma', gx - 0.7, gx + 0.7, 4.8, 6.6, -1.2 + rec, 6.0 + rec, 'metal'),
                   C('cano', gx - 0.45, gx + 0.45, 5.6, 6.5, -8.0 + rec, -1.2 + rec, 'metal'), C('bomba', gx - 0.65, gx + 0.65, 4.6, 5.6, -6.2 + rec, -2.8 + rec, 'couro')]
            boca = (gx, 6.05, -8.6 + rec)
        elif tipo == 'atirador':        # fuzil de precisao comprido com luneta
            cs += [C('coronha', gx - 0.6, gx + 0.6, 4.2, 6.6, 6.0 + rec, 9.2 + rec, 'couro'), C('arma', gx - 0.6, gx + 0.6, 4.8, 6.4, -2.0 + rec, 6.0 + rec, 'metal'),
                   C('cano', gx - 0.3, gx + 0.3, 5.4, 6.0, -12.6 + rec, -2.0 + rec, 'metal'), C('luneta', gx - 0.55, gx + 0.55, 6.4, 7.5, -1.8 + rec, 3.6 + rec, 'faixa'),
                   C('lente', gx - 0.45, gx + 0.45, 6.5, 7.4, -1.95 + rec, -1.8 + rec, 'olho', so=('north',))]
            boca = (gx, 5.7, -13.2 + rec)
        else:                           # fuzil de assalto: coronha, pente, guarda-mao, cano e mira
            cs += [C('coronha', gx - 0.6, gx + 0.6, 4.4, 6.6, 6.0 + rec, 8.8 + rec, 'faixa'), C('arma', gx - 0.7, gx + 0.7, 4.6, 6.6, -1.0 + rec, 6.0 + rec, 'metal'),
                   C('pente', gx - 0.45, gx + 0.45, 2.4, 4.6, 0.6 + rec, 2.2 + rec, 'faixa'), C('guarda', gx - 0.7, gx + 0.7, 4.9, 6.4, -5.4 + rec, -1.0 + rec, 'faixa'),
                   C('cano', gx - 0.3, gx + 0.3, 5.4, 6.0, -8.4 + rec, -5.4 + rec, 'metal'), C('mira', gx - 0.5, gx + 0.5, 6.6, 7.6, 0.6 + rec, 2.8 + rec, 'faixa')]
            boca = (gx, 5.7, -9.0 + rec)
    t = 1.3 if at else 0.3
    bz = boca[2] - (1.3 if at else -2.4)
    cs.append(C('clarao', boca[0] - t, boca[0] + t, boca[1] - t, boca[1] + t, bz - 0.5, bz + 0.5, 'bolha', emite=15))
    return cs


def soldado(q, tipo):
    """Soldado inimigo das missoes (gente, nao infectado): capacete, balaclava, colete, arma apontada. Quadros 1 e 4 = atirando
    (clarao na boca do cano e coice)."""
    at = q == 1
    grande = tipo in ('pesado', 'juggernaut')
    L = 1.25 if tipo == 'juggernaut' else 1.12 if tipo == 'pesado' else 1.0          # largura
    cs = []
    pernas(cs, q, afast=1.9 * L, larg=3.0 * L, topo=-4.0, comp=12.0, pe='faixa', joelho=None if tipo == 'juggernaut' else 'couro')
    cs += [C('quadril', 8 - 3.7 * L, 8 + 3.7 * L, -5.4, -2.6, 6.2, 10.8, 'calca'),
           C('tronco', 8 - 3.9 * L, 8 + 3.9 * L, -3.0, 7.6, 5.8, 11.0, 'camisa'),
           C('colete', 8 - 4.1 * L, 8 + 4.1 * L, 0.2, 7.0, 5.2, 11.6, 'couro'),
           C('bolso_d', 8 - 3.4 * L, 8 - 0.6, 0.6, 3.2, 4.4, 5.3, 'faixa'),
           C('cinto', 8 - 4.0 * L, 8 + 4.0 * L, -3.2, -1.8, 5.6, 11.2, 'faixa'),
           C('pescoco', 6.9, 9.1, 7.4, 9.0, 7.0, 9.6, 'faixa'),
           C('cabeca', 5.6, 10.4, 8.6, 13.4, 5.8, 10.6, 'faixa'),
           C('rosto', 6.2, 9.8, 10.4, 12.0, 5.5, 5.9, 'pele', so=('north',)),
           C('olho_r', 6.6, 7.5, 10.9, 11.5, 5.36, 5.5, 'olho', so=('north',)),
           C('olho_l', 8.5, 9.4, 10.9, 11.5, 5.36, 5.5, 'olho', so=('north',)),
           C('capacete', 5.2, 10.8, 12.4, 14.6, 5.4, 11.0, 'metal' if grande or tipo == 'escudo' else 'couro'),
           C('capacete_aba', 5.2, 10.8, 12.2, 12.9, 4.6, 5.6, 'metal' if grande or tipo == 'escudo' else 'couro')]
    for c in cs:
        if c.nome == 'cabeca':
            c.sem_rosto = True
    if tipo != 'juggernaut':          # 16/11: equipamento de soldado de verdade
        cs[:] = [c for c in cs if c.nome != 'bolso_d']
        cs += [C('oculos', 5.8, 10.2, 12.9, 13.8, 4.9, 5.5, 'lente'), C('fone_d', 4.7, 5.4, 9.8, 11.8, 7.0, 9.4, 'couro'),
               C('bolsa1', 8 - 3.0 * L, 8 - 1.2 * L, 0.6, 3.2, 4.5, 5.2, 'couro'), C('bolsa2', 7.1, 8.9, 0.6, 3.2, 4.5, 5.2, 'couro'),
               C('bolsa3', 8 + 1.2 * L, 8 + 3.0 * L, 0.6, 3.2, 4.5, 5.2, 'couro'), C('radio', 8 + 2.0 * L, 8 + 3.0 * L, 4.0, 6.8, 4.7, 5.3, 'couro')]
        if tipo != 'pesado':
            cs.append(C('hidratacao', 5.6, 10.4, 0.6, 6.4, 11.0, 12.4, 'couro'))
    if tipo == 'atirador':            # capuz de camuflagem
        cs += [C('ghillie', 4.8, 11.2, 9.0, 15.0, 9.6, 11.6, 'couro'), C('ghillie_o', 3.0, 13.0, 5.6, 8.6, 5.4, 11.4, 'couro')]
    elif tipo == 'pesado':
        cs += [C('mochila', 4.6, 11.4, -1.0, 8.0, 11.0, 14.6, 'metal'), C('ombreira_d', 2.0, 5.0, 6.0, 9.0, 5.6, 11.2, 'metal'),
               C('visor', 5.9, 10.1, 10.2, 11.8, 5.2, 5.6, 'olho', emite=8, so=('north',))]
    elif tipo == 'juggernaut':
        cs[:] = [c for c in cs if c.nome not in ('capacete', 'capacete_aba', 'rosto', 'olho_r', 'olho_l')]   # capacete fechado
        cs += [C('placa', 3.0, 13.0, -1.4, 7.8, 4.0, 5.4, 'metal'), C('placa_c', 3.2, 12.8, -1.0, 8.0, 11.4, 12.6, 'metal'),
               C('emblema', 7.0, 9.0, 3.6, 5.8, 3.85, 4.0, 'faixa', so=('north',)),
               C('colar', 4.0, 12.0, 7.0, 8.8, 4.2, 12.0, 'metal'),
               C('elmo', 4.6, 11.4, 8.4, 15.0, 4.6, 11.6, 'metal'), C('elmo_crista', 7.2, 8.8, 14.8, 15.8, 5.4, 11.2, 'faixa'),
               C('elmo_topo', 5.4, 10.6, 15.0, 15.6, 5.4, 10.8, 'metal'),
               C('visor_aro', 4.9, 11.1, 9.9, 13.1, 4.35, 4.6, 'couro', so=('north',)),
               C('visor', 5.4, 10.6, 10.4, 12.6, 4.15, 4.35, 'olho', emite=12, so=('north',)),
               C('respirador', 6.6, 9.4, 8.6, 10.0, 4.0, 4.6, 'couro'),
               C('ombreira_d', 0.2, 4.8, 5.0, 10.4, 4.4, 12.2, 'metal'), C('ombreira_faixa_d', 0.0, 4.8, 9.2, 10.6, 4.3, 12.3, 'faixa'),
               C('coxal_d', 3.0, 7.6, -6.8, -3.2, 4.3, 5.2, 'metal'), C('bolsa_d', 2.6, 4.8, -3.6, -0.8, 5.2, 7.6, 'couro'),
               C('tanque', 4.2, 11.8, -1.2, 8.0, 12.6, 16.6, 'couro'), C('tanque_tampa', 4.0, 12.0, 8.0, 9.0, 12.4, 16.8, 'metal'),
               C('cilindro_d', 3.4, 5.6, -0.4, 7.4, 16.6, 17.8, 'metal'),
               C('nucleo', 6.4, 9.6, 1.2, 6.2, 16.6, 17.0, 'olho', emite=15, so=('south',)),
               C('tubo_d', 2.6, 3.8, 2.6, 7.6, 10.6, 12.8, 'couro')]
    else:
        cs += [C('ombreira_d', 2.6, 4.6, 6.2, 8.2, 6.0, 10.8, 'faixa')]
    # bracos para a frente segurando a arma (o cano aponta para o norte)
    bx = 3.9 * L + 1.2
    cs += [C('braco_d', 8 - bx - 1.2, 8 - bx + 1.2, 3.6, 6.4, 1.0, 8.6, 'camisa'), C('mao_d', 8 - bx - 1.0, 8 - bx + 1.2, 3.4, 6.0, -0.8, 1.2, 'faixa')]
    if tipo == 'escudo':
        cs += [C('escudo', 2.6, 13.4, -12.0, 9.6, -2.6, -1.6, 'metal'), C('escudo_visor', 5.6, 10.4, 5.4, 7.6, -2.8, -2.6, 'olho', emite=4, so=('north',)),
               C('escudo_faixa', 2.6, 13.4, -2.0, -0.8, -2.75, -2.6, 'faixa', so=('north',)), C('pistola', 12.6, 13.6, 3.8, 5.4, -4.6, -1.0, 'faixa')]
        boca = (13.1, 4.8, -5.2)
    elif tipo == 'juggernaut':      # metralhadora giratoria grande, com aneis laranja e caixa de municao
        cs += [C('arma', 5.8, 10.2, 0.0, 4.0, -4.0, 6.0, 'couro'), C('canos', 6.6, 9.4, 0.8, 3.2, -11.6, -4.0, 'metal'),
               C('anel1', 6.2, 9.8, 0.4, 3.6, -11.0, -10.2, 'faixa'), C('anel2', 6.2, 9.8, 0.4, 3.6, -7.0, -6.2, 'faixa'),
               C('caixa', 10.2, 13.0, -1.4, 3.4, 0.4, 5.4, 'metal'), C('fita', 10.6, 12.6, 3.4, 4.0, -0.6, 4.6, 'faixa')]
        boca = (8.0, 2.0, -12.4)
    elif grande:                    # metralhadora giratoria
        cs += [C('arma', 6.4, 9.6, 0.4, 3.6, -3.0, 6.0, 'metal'), C('canos', 7.0, 9.0, 1.0, 3.0, -9.0, -3.0, 'faixa'),
               C('cano2', 6.6, 9.4, 0.6, 3.4, -6.4, -5.6, 'metal'), C('caixa', 9.6, 12.0, -0.6, 3.0, 1.0, 5.0, 'couro')]
        boca = (8.0, 2.0, -9.8)
    elif tipo == 'atirador':
        cs += [C('arma', 7.3, 8.7, 3.6, 5.6, -1.0, 7.0, 'couro'), C('cano', 7.6, 8.4, 4.4, 5.2, -12.0, -1.0, 'metal'),
               C('luneta', 7.4, 8.6, 5.6, 6.8, 0.0, 4.6, 'faixa'), C('lente', 7.5, 8.5, 5.7, 6.7, -0.2, 0.0, 'olho', emite=13, so=('north',))]
        boca = (8.0, 4.8, -12.8)
    elif tipo == 'escopeta':
        cs += [C('arma', 7.2, 8.8, 3.4, 5.4, -1.0, 6.4, 'couro'), C('cano', 7.5, 8.5, 4.2, 5.2, -7.0, -1.0, 'metal'), C('bomba', 7.2, 8.8, 3.2, 4.2, -5.0, -2.0, 'couro')]
        boca = (8.0, 4.7, -7.8)
    else:
        cs += [C('arma', 7.3, 8.7, 3.4, 5.6, -1.6, 6.4, 'metal'), C('cano', 7.6, 8.4, 4.4, 5.2, -7.6, -1.6, 'faixa'),
               C('pente', 7.5, 8.5, 0.8, 3.6, 0.6, 2.4, 'faixa'), C('mira', 7.7, 8.3, 5.6, 6.4, 0.0, 2.0, 'faixa')]
        boca = (8.0, 4.8, -8.4)
    # clarao do tiro: so aparece no quadro 1 (nos outros fica escondido dentro da arma, do mesmo tamanho)
    t = 1.3 if at else 0.3
    bz = boca[2] - (1.3 if at else -2.4)
    cs.append(C('clarao', boca[0] - t, boca[0] + t, boca[1] - t, boca[1] + t, bz - 0.5, bz + 0.5, 'bolha', emite=15))
    if tipo == 'juggernaut':           # luvas, cinto e botas escuros/blindados (o laranja fica so nos detalhes)
        for c in cs:
            n0 = c.nome.split('_')[0]
            if n0 in ('mao', 'cinto', 'pescoco', 'cabeca', 'bolso'):
                c.pele = 'couro'
            elif n0 == 'pe':
                c.pele = 'metal'
    return esp(cs)


def _mover(cs, dy=0.0, dz=0.0, pernas_tambem=True):
    for c in cs:
        if not pernas_tambem and c.nome.split('_')[0] in ('coxa', 'canela', 'pe', 'quadril'):
            continue
        c.de[1] += dy
        c.ate[1] += dy
        c.de[2] += dz
        c.ate[2] += dz
        if c.rot:
            c.rot = (c.rot[0], c.rot[1], (c.rot[2][0], c.rot[2][1] + dy, c.rot[2][2] + dz))
    return cs


def cubos(ident, quadro=0):
    """0 passo A, 1 ataque (bracos erguidos), 2 passo B, 3 passagem (pernas juntas, corpo sobe), 4 golpe (corpo vai para a frente)."""
    cs = CORPOS[ident](quadro)
    if quadro == 3:
        _mover(cs, dy=0.7)
    elif quadro == 4:
        _mover(cs, dz=-2.2, pernas_tambem=False)
    return cs


A.cubos = cubos
A.registrar = lambda pal: None
A.pintar_face = pintar_face
QUADROS = (0, 1, 2, 3, 4)


def gerar(saida, ids):
    """Como A.gerar, com 3 quadros (f0 passo A, f1 ataque, f2 passo B)."""
    from PIL import Image
    kit = A.kit
    import bedrock_v2 as BV
    J = os.path.join(saida, 'java', 'assets', 'corona')
    B = os.path.join(saida, 'bedrock')
    mapeamento, item_tex, folhas = [], {}, []
    for ident in ids:
        base = A.cubos(ident, 0)
        img, bri, esc = A.montar_atlas(base, ident)
        tex = f'anim_{ident}'
        os.makedirs(os.path.join(J, 'textures', 'item', 'props'), exist_ok=True)
        Image.fromarray(A.alfa_binario(img), 'RGBA').save(os.path.join(J, 'textures', 'item', 'props', tex + '.png'), optimize=True)
        tex_bed = f'textures/entity/corona_v2/item_props_{tex}'
        os.makedirs(os.path.join(B, os.path.dirname(tex_bed)), exist_ok=True)
        Image.fromarray(A.textura_bedrock(img, bri), 'RGBA').save(os.path.join(B, tex_bed + '.png'), optimize=True)
        previas = []
        for q in QUADROS:
            cs = A.cubos(ident, q)
            assert len(cs) == len(base), (ident, q, len(cs), len(base))
            for a, b in zip(cs, base):
                assert a.nome == b.nome, (a.nome, b.nome)
                a.uv = dict(b.uv)
            nome = f'anim_{ident}_f{q}'
            modelo = kit.modelo_java(cs, f'corona:item/props/{tex}', 512, 512, display={})
            modelo['credit'] = f'Corona Server - infectado original {ident}'
            kit.salvar_json(os.path.join(J, 'models', 'props', nome + '.json'), modelo)
            kit.salvar_json(os.path.join(J, 'items', 'props', nome + '.json'), kit.item_java(f'corona:props/{nome}'))
            geo_id = f'geometry.corona.prop_{nome}'
            kit.salvar_json(os.path.join(B, 'models', 'entity', 'corona_v2', f'prop_{nome}.geo.json'), BV.geometria_de_modelo(geo_id, modelo))
            at = BV.attachable_prop(f'corona:prop_{nome}', tex_bed, geo_id)
            at['minecraft:attachable']['description']['materials'] = {'default': 'entity_emissive_alpha', 'enchanted': 'entity_emissive_alpha'}
            kit.salvar_json(os.path.join(B, 'attachables', f'prop_{nome}.json'), at)
            previas.append(kit.previa(cs, A.alfa_binario(img), 150 if q != 1 else 160, 8, escala=6, fundo=(150, 158, 168)))
            ic = Image.new('RGBA', (16, 16), (90, 30, 30, 255))
            os.makedirs(os.path.join(B, 'textures', 'items', 'corona', 'v2'), exist_ok=True)
            ic.save(os.path.join(B, 'textures', 'items', 'corona', 'v2', f'corona_prop_{nome}.png'))
            item_tex[f'corona_prop_{nome}'] = {'textures': f'textures/items/corona/v2/corona_prop_{nome}'}
            mapeamento.append({'type': 'definition', 'model': f'corona:props/{nome}', 'bedrock_identifier': f'corona:prop_{nome}',
                               'display_name': f'Prop {nome}', 'bedrock_options': {'icon': f'corona_prop_{nome}', 'creative_category': 'equipment',
                                                                                  'protection_value': 1}})
        folhas.append(kit.folha(previas, (150, 158, 168)))
        print(ident, 'cubos', len(base), 'escala', esc)
    kit.salvar_json(os.path.join(saida, 'corona-tiro-modelos.json'), {'format_version': 2, 'items': {'minecraft:leather_helmet': mapeamento}})
    kit.salvar_json(os.path.join(saida, 'item_texture_modelos.json'), item_tex)
    for i, f in enumerate(folhas):
        f.convert('RGB').save(os.path.join(saida, f'previa_{ids[i]}.png'))


if __name__ == '__main__':
    sys.path.insert(0, os.path.join(AQUI, '..', '..', 'plugins', 'coronapack'))
    gerar(A.SAIDA, sys.argv[2:] or list(CORPOS))
