"""Arsenal Ancestral do nivel 6 (20/11, Paulo: "voce ta imitando as anteriores, crie algo diferente").

Estilo proprio, que nenhuma armadura antiga usa:
  * placas angulosas de metal quase preto (tom do tema bem escuro), com recortes em V no peito, abdome em 3 laminas,
    ombreira em 3 camadas pontudas, faldas em ponta e joelheira com espinho;
  * VEIOS DE ENERGIA acesos na cor do tema correndo pelas placas, cada masmorra com um desenho proprio:
    rachas de lava (Cinzas), runas em grade (Catacumba), zigue-zague (Pinaculo), filigrana em espiral (Rubra),
    circuito (Eco), raizes (Putrido), fractal de gelo (Glaciar), ondas (Abissal), colmeia hexagonal (Titas) e fendas
    do vazio (Caos);
  * NUCLEO no peito (gema em losango que brilha, com aro dourado) e frisos finos dourados nas bordas;
  * ELMO 3D com pecas novas (coroa de brasas, aureola flutuante, para-raios, gola alta, antenas, cogumelos, coroa de
    cristais, barbatanas, crista de tita, chifres em espiral com estilhacos soltos).
Mesmo layout das armaduras v3 (256x128, Java equipment + Bedrock armor), entao o resto do pacote nao muda.
Uso: python3 -I armaduras_n6.py [ids...]  (sem ids = os 10 do nivel 6)
"""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'skins', 'monstros'))
import armaduras_tematicas as A  # noqa: E402
import armaduras_v3 as V3  # noqa: E402
import elmos3d as E  # noqa: E402
from base import C, fbm, hash2, mix, ss  # noqa: E402

N6 = {   # id: (veios, peca do elmo 3D, cor do metal base, cor do veio, cor do friso)
    'CINZAS': ('rachas', 'n6_coroa_brasa', '#2a1a14', '#ff7a1a', '#c08a40'),
    'CATACUMBA': ('runas', 'n6_aureola', '#141c22', '#60e8ff', '#b0b8c8'),
    'PINACULO': ('ziguezague', 'n6_para_raios', '#1c1a14', '#ffe040', '#e0c060'),
    'RUBRA': ('filigrana', 'n6_gola_alta', '#1e0c10', '#ff2a40', '#d0a050'),
    'ECO': ('circuito', 'n6_antenas', '#0c1618', '#30f0ff', '#5aa0a8'),
    'PUTRIDO': ('raizes', 'n6_cogumelos', '#141c10', '#9cff40', '#8a9a50'),
    'GLACIAR': ('fractal', 'n6_cristais', '#1a2430', '#c8f4ff', '#e8f4ff'),
    'ABISSAL': ('ondas', 'n6_barbatanas', '#0c1424', '#3aa0ff', '#60c0c0'),
    'TITAS': ('colmeia', 'n6_crista_tita', '#1c1c20', '#ff9a30', '#c8c8d0'),
    'CAOS': ('fendas', 'n6_chifres_espiral', '#140c1c', '#d050ff', '#a080d0'),
}

PLACA, ACAB, ESCURO, BRILHO, VAZIO, COURO, MALHA = V3.PLACA, V3.ACAB, V3.ESCURO, V3.BRILHO, V3.VAZIO, V3.COURO, V3.MALHA


# ------------------------------------------------------------------ silhueta (modelo 'ancestral')

def tpl_ancestral(f, var):
    if f.parte == 'body' and f.camada == 1:
        if f.baixo:
            return
        if f.cima:
            f.ret(PLACA)
            return
        if f.frente or f.costas:
            f.ret(PLACA, 0, 1, 0, 0.42)                      # couraca do peito
            f.laminas(PLACA, 0.42, 0.78, 3)                  # abdome em laminas
            f.ret(ESCURO, 0, 1, 0.78, 0.84)                  # cinta
            f.laminas(PLACA, 0.84, 1.0, 1)
            # gola em V (so na frente)
            if f.frente:
                f.pinta((np.abs(f.S - 0.5) < 0.22 - f.T * 0.9) & (f.T < 0.2), ESCURO, nova_placa=False)
            f.ret(ACAB, 0, 1, 0, 0.05)
            f.dentes(0.95, 0.05, 0.25)
        else:
            f.ret(MALHA, 0, 1, 0, 0.78)
            f.ret(PLACA, 0, 1, 0, 0.1)
            f.ret(ESCURO, 0, 1, 0.78, 0.84)
    elif f.parte == 'rarm' and f.camada == 1:
        if f.cima:
            f.ret(PLACA)
            return
        if f.baixo:
            return
        # ombreira em 3 camadas pontudas
        for k, (a, b) in enumerate(((0.0, 0.16), (0.16, 0.3), (0.3, 0.42))):
            f.ret(PLACA, 0, 1, a, b)
        f.dentes(0.38, 0.12, 0.34)
        f.ret(ESCURO, 0, 1, 0.42, 0.55)
        f.ret(PLACA, 0, 1, 0.55, 0.92)                       # bracal
        f.ret(ACAB, 0, 1, 0.92, 1.0)
    elif f.parte == 'rleg' and f.camada == 1:
        if f.cima:
            return
        f.pinta(f.T < 0.5, VAZIO, nova_placa=False)
        f.ret(PLACA, 0, 1, 0.5, 0.9)
        f.ret(ACAB, 0, 1, 0.9, 1.0)
        if f.frente:
            f.pinta((np.abs(f.S - 0.5) < 0.18 * (0.62 - f.T) / 0.12) & (f.T > 0.5) & (f.T < 0.62), PLACA, nova_placa=False)
    elif f.parte == 'body' and f.camada == 2:
        if f.cima or f.baixo:
            return
        f.ret(ESCURO, 0, 1, 0, 0.2)
        f.colunas(PLACA, 0, 1, 3 if (f.frente or f.costas) else 1, 0.2, 1.0)
        f.dentes(0.82, 0.18, 0.34)
    elif f.parte == 'rleg' and f.camada == 2:
        if f.cima or f.baixo:
            return
        f.ret(PLACA, 0, 1, 0, 0.45)
        f.ret(ESCURO, 0, 1, 0.45, 0.55)
        f.ret(PLACA, 0, 1, 0.55, 1.0)
        if f.frente:                                          # joelheira com espinho
            f.pinta((np.hypot((f.S - 0.5) * 1.6, (f.T - 0.5) * 3.2) < 0.32), ACAB, nova_placa=True)


# ------------------------------------------------------------------ veios de energia (por masmorra)

def _linha(d, w):
    return np.clip(1 - d / w, 0, 1)


def veios(padrao, X, Y, sem):
    """Intensidade 0..1 do veio aceso (X, Y em texels 4x)."""
    x, y = X / 4.0, Y / 4.0
    if padrao == 'rachas':
        from base import ridged
        r = ridged(x * 0.35, y * 0.35, 0 * x, 3, 1, sem + 11)
        return ss(0.62, 0.86, r)
    if padrao == 'runas':
        cx, cy = np.floor(x / 4), np.floor(y / 4)
        fx, fy = x / 4 - cx, y / 4 - cy
        h = hash2(cx.astype(int), cy.astype(int), sem + 3)
        traco = np.where(h < 0.33, np.abs(fx - 0.5) < 0.09, np.where(h < 0.66, np.abs(fy - 0.5) < 0.09, np.abs(fx - fy) < 0.1))
        moldura = (fx < 0.08) | (fy < 0.08)
        return np.where(traco & (h > 0.15), 1.0, 0.0) + np.where(moldura & (h > 0.7), 0.6, 0.0)
    if padrao == 'ziguezague':
        z = np.abs(((x * 0.5 + np.abs(((y * 0.5) % 2) - 1) * 2) % 4) - 2)
        return _linha(np.abs(z - 1), 0.28)
    if padrao == 'filigrana':
        a = np.arctan2(np.sin(y * 0.55), np.cos(x * 0.55))
        rr = np.hypot(np.sin(x * 0.55), np.cos(y * 0.55))
        return _linha(np.abs(np.sin(a * 2 + rr * 5)), 0.22) * ss(0.2, 0.5, rr)
    if padrao == 'circuito':
        gx, gy = np.floor(x / 3), np.floor(y / 3)
        h = hash2(gx.astype(int), gy.astype(int), sem + 5)
        fx, fy = x / 3 - gx, y / 3 - gy
        trilha = np.where(h < 0.5, np.abs(fy - 0.5) < 0.08, np.abs(fx - 0.5) < 0.08)
        no = np.hypot(fx - 0.5, fy - 0.5) < 0.18
        return np.where(trilha & (h > 0.12), 1.0, 0.0) + np.where(no & (h > 0.8), 1.0, 0.0)
    if padrao == 'raizes':
        n = fbm(x * 0.25, y * 0.18, 0 * x, 4, 1, sem + 7)
        return _linha(np.abs(n - 0.5), 0.05) * ss(0.2, 0.6, fbm(x * 0.1, y * 0.1, 0 * x, 2, 1, sem + 9))
    if padrao == 'fractal':
        v = 0
        for k, esc in enumerate((0.9, 0.45, 0.22)):
            ang = k * 1.047
            u = (x * np.cos(ang) + y * np.sin(ang)) * esc
            v = np.maximum(v, _linha(np.abs((u % 3) - 1.5), 0.16) * (0.5 + 0.5 * hash2(np.floor(u / 3).astype(int), 0 * x.astype(int) + k, sem)))
        return v
    if padrao == 'ondas':
        return _linha(np.abs(np.sin(y * 0.9 + np.sin(x * 0.5) * 2.2)), 0.18)
    if padrao == 'colmeia':
        q = x / 3.0
        r = y / 3.0 - q * 0.5
        rq, rr = np.round(q), np.round(r)
        dq, dr = q - rq, r - rr
        d = np.maximum(np.abs(dq), np.maximum(np.abs(dr), np.abs(dq + dr)))
        return _linha(np.abs(d - 0.5), 0.07)
    if padrao == 'fendas':
        n = fbm(x * 0.12, y * 0.4, 0 * x, 3, 1, sem + 13)
        return ss(0.66, 0.78, n) * (1 - ss(0.82, 0.95, n)) * 1.6
    return 0 * x


_orig_pintar = V3.pintar_face


def pintar_face_n6(id_, rec, t, parte, face, camada, x0, y0, x1, y1, sem):
    c, a = _orig_pintar(id_, rec, t, parte, face, camada, x0, y0, x1, y1, sem)
    if id_ not in N6:
        return c, a
    padrao, _, base, veio, friso = N6[id_]
    f = V3.Face(parte, face, camada, x0, y0, x1, y1)
    # refaz o mapa de tipos (o pintar original nao devolve) para saber onde ha placa
    if parte == 'head':
        if camada == 1:
            V3.elmo_forma(f, V3.ESTILO_V3.get(id_, rec[2]), 'ancestral', '')
    elif not (parte == 'rarm' and camada == 2):
        tpl_ancestral(f, '')
    placa = np.isin(f.tipo, (PLACA, V3.METAL2)) & (a > 0)
    # metal quase preto com o tom do tema (escurece a placa que veio do material)
    b = np.asarray(C(base))
    lum = c.mean(axis=-1, keepdims=True)
    escuro = b * (0.55 + 0.9 * lum)
    c = np.where(placa[..., None], escuro, c)
    # frisos dourados nas bordas das placas
    pid = f.pid
    borda = np.zeros(pid.shape, bool)
    borda[1:, :] |= pid[1:, :] != pid[:-1, :]
    borda[:, 1:] |= pid[:, 1:] != pid[:, :-1]
    c = np.where((borda & placa)[..., None], np.asarray(C(friso)) * np.ones_like(c), c)
    # veios acesos com brilho em volta
    v = np.clip(veios(padrao, f.X, f.Y, sem), 0, 1) * placa
    glow = np.clip(v * 0.0, 0, 1)
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1), (2, 0), (-2, 0), (0, 2), (0, -2)):
        glow = np.maximum(glow, np.roll(np.roll(v, dy, 0), dx, 1) * (0.45 if abs(dx) + abs(dy) == 1 else 0.22))
    cv = np.asarray(C(veio))
    branco = mix(cv, C('#ffffff'), 0.45)
    c = c * (1 - glow[..., None] * 0.6) + cv * glow[..., None] * 0.6
    c = np.where((v > 0.5)[..., None], branco * v[..., None] + cv * (1 - v[..., None]), c)
    # nucleo do peito (gema em losango)
    if parte == 'body' and camada == 1 and face == 'south':
        d = np.abs(f.S - 0.5) * f.W + np.abs(f.T - 0.3) * f.H
        aro = (d < 9) & (d >= 6.5)
        gema = d < 6.5
        c = np.where(aro[..., None], np.asarray(C(friso)) * np.ones_like(c), c)
        brilho = np.clip(1 - d / 6.5, 0, 1)[..., None]
        c = np.where(gema[..., None], cv * (0.7 + 0.3 * brilho) + (1 - cv) * brilho * 0.7, c)
        a = np.where(gema | aro, 1.0, a)
    return np.clip(c, 0, 1), a


V3.pintar_face = pintar_face_n6
V3.MODELOS['ancestral'] = tpl_ancestral


# ------------------------------------------------------------------ elmo 3D: pecas novas

_orig_pecas = E.pecas
_orig_estilo = E.estilo_de


def pecas_n6(estilo, id_):
    cubo, par = E.cubo, E.par
    P = []
    if estilo == 'n6_coroa_brasa':          # anel de estilhacos de brasa subindo em alturas diferentes
        for k in range(10):
            ang = k / 10 * 2 * np.pi
            x, z = 4.6 * np.cos(ang), 4.6 * np.sin(ang)
            h = 2.2 + (k % 3) * 1.3
            P.append(cubo(x - 0.5, x + 0.5, 33.0, 33.0 + h, z - 0.5, z + 0.5, 'placa'))
            P.append(cubo(x - 0.3, x + 0.3, 33.0 + h, 34.2 + h, z - 0.3, z + 0.3, 'brilho'))
    elif estilo == 'n6_aureola':            # aureola solta acima da cabeca + 2 lascas de alma nos lados
        for k in range(16):
            ang = k / 16 * 2 * np.pi
            x, z = 5.2 * np.cos(ang), 5.2 * np.sin(ang)
            P.append(cubo(x - 0.55, x + 0.55, 36.4, 37.0, z - 0.55, z + 0.55, 'brilho'))
        P += par([cubo(5.2, 6.0, 26.0, 31.5, -0.6, 0.6, 'placa'), cubo(5.6, 6.2, 31.5, 33.0, -0.3, 0.3, 'brilho')])
    elif estilo == 'n6_para_raios':         # para-raios central em zigue-zague e aletas laterais
        y = 33.0
        for k, dx in enumerate((0.0, 0.8, -0.4, 0.6, 0.0)):
            P.append(cubo(dx - 0.45, dx + 0.45, y, y + 1.4, -0.45, 0.45, 'acab' if k < 4 else 'brilho'))
            y += 1.4
        P += par([cubo(5.0, 5.6, 29.0, 32.0, -2.5, 2.5, 'placa'), cubo(5.6, 6.6, 31.0, 33.6, -0.4, 0.4, 'brilho')])
    elif estilo == 'n6_gola_alta':          # gola alta de vampiro atras da cabeca + pontas
        P.append(cubo(-5.6, 5.6, 22.0, 30.0, 4.6, 5.6, 'placa'))
        P += par([cubo(4.6, 5.6, 22.0, 31.0, 2.0, 5.6, 'placa'), cubo(4.8, 5.4, 31.0, 33.0, 4.6, 5.4, 'acab')])
        for x in (-2.5, 0.0, 2.5):
            P.append(cubo(x - 0.35, x + 0.35, 33.0, 34.6 if x == 0 else 34.0, -5.5, -5.0, 'brilho'))
    elif estilo == 'n6_antenas':            # antenas de sculk ramificadas para tras
        for lado in (-1, 1):
            x = 2.2 * lado
            P.append(cubo(x - 0.4, x + 0.4, 33.0, 37.0, 2.0, 2.8, 'placa'))
            P.append(cubo(x + 0.4 * lado, x + 1.8 * lado, 36.0, 36.8, 2.0, 2.8, 'placa'))
            P.append(cubo(x + 1.4 * lado, x + 2.0 * lado, 36.8, 38.6, 2.0, 2.8, 'brilho'))
            P.append(cubo(x - 0.3, x + 0.3, 37.0, 38.2, 2.1, 2.7, 'brilho'))
    elif estilo == 'n6_cogumelos':          # chapeus de cogumelo tortos (assimetrico)
        for (x, z, r, h) in ((-3.0, -1.0, 2.6, 34.5), (2.6, 1.5, 1.8, 35.4), (0.4, -3.2, 1.3, 33.9)):
            P.append(cubo(x - 0.4, x + 0.4, 33.0, h, z - 0.4, z + 0.4, 'placa'))
            P.append(cubo(x - r, x + r, h, h + 1.1, z - r, z + r, 'brilho'))
    elif estilo == 'n6_cristais':           # coroa de cristais de alturas diferentes, inclinada para um lado
        for k, (x, z, h) in enumerate(((-3.5, -3.5, 3.0), (-1.0, -4.0, 5.2), (1.8, -3.8, 3.6), (4.0, -1.5, 2.4), (-4.2, 0.5, 2.0), (3.6, 2.6, 1.6))):
            P.append(cubo(x - 0.7, x + 0.7, 33.0, 33.0 + h, z - 0.7, z + 0.7, 'placa'))
            P.append(cubo(x - 0.35, x + 0.35, 33.0 + h, 34.0 + h, z - 0.35, z + 0.35, 'brilho'))
    elif estilo == 'n6_barbatanas':         # barbatana dorsal e guelras laterais
        for k in range(6):
            P.append(cubo(-0.35, 0.35, 33.0, 33.6 + k * 0.7, -3.0 + k * 1.2, -1.8 + k * 1.2, 'placa' if k < 5 else 'brilho'))
        P += par([cubo(5.0, 5.4, 25.0, 30.0, 0.5, 1.0, 'brilho'), cubo(5.0, 6.8, 26.0, 29.5, -1.0, 3.5, 'placa')])
    elif estilo == 'n6_crista_tita':        # crista larga, protetor de rosto e parafusos
        P.append(cubo(-1.0, 1.0, 33.0, 36.5, -4.5, 4.5, 'placa'))
        P.append(cubo(-0.5, 0.5, 36.5, 37.2, -4.0, 4.0, 'brilho'))
        P.append(cubo(-4.0, 4.0, 23.0, 26.0, -5.9, -5.2, 'placa'))
        P += par([cubo(5.0, 6.0, 27.0, 29.0, -1.0, 1.0, 'acab'), cubo(6.0, 6.5, 27.5, 28.5, -0.5, 0.5, 'brilho')])
    elif estilo == 'n6_chifres_espiral':    # chifres em espiral e estilhacos soltos flutuando
        for lado in (-1, 1):
            x, y, z = 4.8 * lado, 30.0, 0.0
            for k in range(6):
                w = 1.1 - k * 0.12
                P.append(cubo(x - w, x + w, y, y + 1.5, z - w, z + w, 'placa' if k < 5 else 'brilho'))
                x += 0.9 * lado
                y += 1.2
                z += (0.9 if k % 2 == 0 else -0.6)
        for (x, y, z) in ((-2.0, 38.5, -1.0), (1.5, 39.4, 1.0), (0.0, 40.2, -2.0)):
            P.append(cubo(x - 0.4, x + 0.4, y, y + 0.8, z - 0.4, z + 0.4, 'brilho'))
    else:
        return _orig_pecas(estilo, id_)
    return P


def estilo_n6(id_):
    if id_ in N6:
        return N6[id_][1]
    return _orig_estilo(id_)


E.pecas = pecas_n6
E.estilo_de = estilo_n6

for id_ in N6:
    V3.MODELO[id_] = 'ancestral'
    V3.VARIANTE.pop(id_, None)


if __name__ == '__main__':
    so = [x.upper() for x in sys.argv[1:]] or list(N6)
    for id_ in so:
        V3.gerar(id_, A.TEMAS[id_], True)
    saida = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'coronapack', 'pacote'))
    E.gerar(saida, so)
    print(len(so), 'armaduras do nivel 6 (Arsenal Ancestral); previas em', V3.PREV)
