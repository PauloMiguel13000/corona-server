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
            n = (sem * 7) % 5
            if n < 3:
                t.rasgo(0.3 + 0.1 * n, 0.4 + 0.12 * n, 0.14, 0.08, cor['carne'], sem)
                t.sangue(0.3 + 0.1 * n, 0.46 + 0.12 * n, 0.12, 0.3, sem)
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
        t.borda(0.62, 0.22)
    elif mat in ('calca', 'camisa', 'couro', 'faixa'):
        t.base(cor[mat], 0.22)
        t.dobras(0.32)
        if mat == 'faixa':
            t.mult(np.mod(t.Y * 0.9 + t.X * 0.25, 1.0) < 0.14, 0.6)
            t.manchas((110, 20, 20), 0.6, 0.8, 0.6)
        elif grande:
            t.rasgo(0.5, 0.5, 0.25, 0.08, tuple(int(v * 0.8) for v in cor['pele']), sem)
            t.sangue(0.4, 0.2, 0.2, 0.3, sem)
            if mat == 'couro':
                t.sangue(0.6, 0.55, 0.3, 0.4, sem + 1)
        t.sujeira(0.45, 0.55)
        t.borda(0.66, 0.2)
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


def ztanque(at):
    cs = []
    inc = ('x', -22.5, (8.0, -3.0, 9.0))                                # tronco curvado para a frente
    # pernas curtas e grossas
    cs += [C('coxa_d', 3.2, 7.4, -11.0, -4.0, 6.2, 11.0, 'calca'), C('canela_d', 3.6, 7.0, -14.6, -10.6, 6.6, 10.4, 'calca'),
           C('pe_d', 3.2, 7.4, -16.0, -14.2, 4.4, 10.8, 'sapato'), C('joelho_d', 3.9, 6.7, -11.6, -9.6, 5.6, 6.8, 'calca')]
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


def pernas(cs, q, afast=1.9, larg=3.0, topo=-4.0, mat='calca', pe='sapato', zc=8.5, comp=12.0):
    """Duas pernas que alternam o passo: q=0 passo A, q=2 passo B, q=1 (ataque) paradas."""
    for sgn, lado in ((1, 'r'), (-1, 'l')):
        cx = 8 - sgn * afast
        ang = 22.5 * sgn if q == 0 else (-22.5 * sgn if q == 2 else 0.0)
        rot = ('x', ang, (cx, topo, zc)) if ang else None
        h = larg / 2
        cs += [C('coxa_' + lado, cx - h, cx + h, topo - comp * 0.5, topo, zc - h - 0.3, zc + h + 0.3, mat, rot),
               C('canela_' + lado, cx - h + 0.3, cx + h - 0.3, topo - comp + 1.6, topo - comp * 0.5 + 0.4, zc - h, zc + h, mat, rot),
               C('pe_' + lado, cx - h, cx + h, topo - comp, topo - comp + 1.8, zc - h - 2.0, zc + h, pe, rot)]


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


def _tanque3(q):
    return ztanque(q == 1)


CORPOS = dict(zcomum=zcomum, zcorredor=zcorredor, ztanque=_tanque3, zcuspidor=zcuspidor, zexplosivo=zexplosivo, zchefe=zchefe)
for k in CORPOS:
    A.PALETAS[k] = {}
A.cubos = lambda ident, quadro=0: CORPOS[ident](quadro)
A.registrar = lambda pal: None
A.pintar_face = pintar_face
QUADROS = (0, 1, 2)


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
            previas.append(kit.previa(cs, A.alfa_binario(img), 150 if q != 1 else 160, 8, escala=9, fundo=(150, 158, 168)))
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
