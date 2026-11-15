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
    'ztanque': dict(pele=(176, 124, 112), calca=(52, 56, 68), olho=(255, 230, 170)),
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
        if c.nome == 'cabeca' and f == 'north':
            for cx in (0.27, 0.73):
                t.mult(Z.elipse(t.U, t.V, cx, 0.4, 0.2, 0.15, 0.6), 0.12)
            t.mult(Z.elipse(t.U, t.V, 0.5, 0.58, 0.08, 0.1, 0.6), 0.35)
            t.cor(Z.elipse(t.U, t.V, 0.5, 0.9, 0.34, 0.14, 0.3), cor['boca'])
            den = (np.mod(t.U * 7, 1.0) > 0.25) * (t.V > 0.8) * (t.V < 0.9) * (np.abs(t.U - 0.5) < 0.3)
            t.cor(den, cor['dente'], 0.95)
            t.sangue(0.5, 0.96, 0.2, 0.1, 1)
        t.borda(0.62, 0.22)
    elif mat == 'calca':
        t.base(cor['calca'], 0.22)
        t.dobras(0.32)
        if grande:
            t.rasgo(0.5, 0.5, 0.25, 0.08, tuple(int(v * 0.8) for v in cor['pele']), sem)
            t.sangue(0.4, 0.2, 0.2, 0.3, sem)
        t.sujeira(0.45, 0.55)
        t.borda(0.66, 0.2)
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


CORPOS = dict(ztanque=ztanque)
for k in CORPOS:
    A.PALETAS[k] = {}
A.cubos = lambda ident, quadro=0: CORPOS[ident](quadro == 1)
A.registrar = lambda pal: None
A.pintar_face = pintar_face

if __name__ == '__main__':
    A.gerar(A.SAIDA, sys.argv[2:] or list(CORPOS))
    os.replace(os.path.join(A.SAIDA, 'corona-animatronicos.json'), os.path.join(A.SAIDA, 'corona-tiro-modelos.json'))
    os.replace(os.path.join(A.SAIDA, 'previa_animatronicos.png'), os.path.join(A.SAIDA, 'previa_modelos.png'))
