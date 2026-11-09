"""O Porcelana em 3D (criatura ORIGINAL do minigame da floresta): modelo de cubos para o pacote (Java + Bedrock).

Alto e magro (~2,45 blocos), manto escuro comprido e rasgado com musgo, braços compridos com maos de porcelana e dedos
pretos longos, pescoco fino e a mascara de porcelana rachada com SETE olhos fundos — as pupilas vermelhas BRILHAM no
escuro (Java: light_emission; Bedrock: entity_emissive_alpha). Dois quadros: f0 parado, f1 susto (cabeca torta e bracos
para a frente).

Modelo Java em coordenadas de item (16 = 1 bloco), centro x=8 z=8, pes em y=-16, FRENTE = -z (norte).
Mostrado por ItemDisplay (item leather_helmet + item_model corona:props/porcelana_f<n>) a 1,5 bloco acima dos pes.
Bedrock: a extensao GeyserDisplayEntity mostra o mesmo item (attachable corona:prop_porcelana_f<n>, osso geyser_z).

uso: python3 -I porcelana3d.py <saida>   (gera <saida>/java/..., <saida>/bedrock/..., <saida>/corona-porcelana.json)
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)
SAIDA = sys.argv[1] if len(sys.argv) > 1 else '/tmp/claude-0/porcelana3d'
import trajes_hd as TH  # noqa: E402
import kit  # noqa: E402

S = TH.S
TH.MAT.update({
    'porc': ((0.93, 0.93, 0.94), 0.6, 45, 0.0),
    'porc2': ((0.80, 0.80, 0.82), 0.35, 30, 0.0),
    'manto': ((0.04, 0.041, 0.047), 0.16, 12, 0.0),
    'manto2': ((0.055, 0.056, 0.064), 0.16, 12, 0.0),
    'capuz': ((0.03, 0.031, 0.036), 0.12, 10, 0.0),
    'calca': ((0.035, 0.035, 0.04), 0.18, 14, 0.0),
    'jaleco': ((0.045, 0.046, 0.052), 0.2, 14, 0.0),
    'musgo': ((0.27, 0.33, 0.15), 0.05, 8, 0.0),
    'preto': ((0.02, 0.02, 0.025), 0.40, 40, 0.0),
    'buraco': ((0.01, 0.01, 0.012), 0.0, 4, 0.0),
    'corda': ((0.36, 0.27, 0.17), 0.08, 10, 0.0),
})
VERMELHO = np.array([1.0, 0.12, 0.10])
VERMELHO2 = np.array([0.55, 0.02, 0.02])


def C(nome, x0, x1, y0, y1, z0, z1, mat, rot=None, emite=0):
    return kit.Cubo(nome, (min(x0, x1), min(y0, y1), min(z0, z1)), (max(x0, x1), max(y0, y1), max(z0, z1)), mat, rot=rot, emite=emite)


# rosto da mascara (oval alongada 4,8 x 6,8): (u, v, raio) na face da frente, u da esquerda de quem olha, v de cima
OLHOS = [(1.6, 2.9, 0.7), (3.8, 2.9, 0.7)]
MASC = (5.3, 10.7, 12.4, 19.7, 4.0, 4.6)       # x0, x1, y0, y1, z0, z1 da mascara (antes de ir para a frente)
TOPO = 21.5                                     # 37,5 unidades = 2,34 blocos (a mesma altura do monstro de antes)
HZ = -1.0                                       # cabeca projetada para a frente (corcunda)
KX, KZ = 0.7, 0.78                              # corpo bem fino
KC, KCZ = 0.8, 0.88                             # cabeca um pouco mais estreita


def cubos(quadro=0):
    cs = []
    susto = quadro == 1

    def add(nome, x0, x1, y0, y1, z0, z1, mat, rot=None, emite=0, so=None):
        c = C(nome, x0, x1, y0, y1, z0, z1, mat, rot, emite)
        c.so = so
        cs.append(c)
        return c

    # calca preta e sapatos finos
    for x0 in (6.55, 8.35):
        add('calca', x0, x0 + 1.1, -15.0, 1.0, 7.45, 8.55, 'calca', so=('north', 'south', 'east', 'west'))
        add('sapato', x0 - 0.05, x0 + 1.15, -16, -15.0, 6.3, 8.65, 'preto')
    # jaleco preto comprido, justo no corpo e aberto na frente (as pernas finas aparecem no meio)
    add('jaleco_costas', 5.2, 10.8, -10.6, 1.4, 8.9, 9.5, 'jaleco')
    add('jaleco_lado', 5.0, 5.6, -10.6, 1.4, 6.6, 9.5, 'jaleco')
    add('jaleco_lado', 10.4, 11.0, -10.6, 1.4, 6.6, 9.5, 'jaleco')
    add('jaleco_frente', 5.2, 6.5, -10.2, 1.4, 6.3, 6.85, 'jaleco')
    add('jaleco_frente', 9.5, 10.8, -10.2, 1.4, 6.3, 6.85, 'jaleco')
    # tronco magro, cinto de corda com o no pendurado, medalhao com um olho
    add('tronco', 5.3, 10.7, 1.0, 8.8, 6.6, 9.4, 'jaleco', so=('north', 'south', 'east', 'west'))
    # ombros caidos (inclinados) e gola alta
    add('peito', 6.0, 10.0, 8.4, 10.4, 6.6, 9.4, 'manto2')
    add('ombro', 3.6, 7.0, 8.6, 10.2, 6.6, 9.4, 'manto2', ('z', 22.5, (7.0, 10.2, 8.0)))
    add('ombro', 9.0, 12.4, 8.6, 10.2, 6.6, 9.4, 'manto2', ('z', -22.5, (9.0, 10.2, 8.0)))
    add('gola', 6.0, 10.0, 10.2, 12.4, 6.2, 9.8, 'jaleco')
    # capinha do capuz caindo sobre os ombros (rasgada embaixo)
    add('capinha', 5.6, 10.4, 6.6, 11.6, 5.7, 10.5, 'trapo', so=('north', 'south', 'up'))
    add('capinha', 2.4, 5.8, 6.6, 11.6, 5.7, 10.5, 'trapo', ('z', 22.5, (5.8, 11.6, 8.0)), so=('north', 'south', 'up', 'west'))
    add('capinha', 10.2, 13.6, 6.6, 11.6, 5.7, 10.5, 'trapo', ('z', -22.5, (10.2, 11.6, 8.0)), so=('north', 'south', 'up', 'east'))
    # bracos compridissimos: braco, cotovelo, antebraco, punho rasgado, mao de porcelana e dedos longos
    for lado in (-1, 1):
        cx = 8 + lado * 5.25
        rot = ('x', 45.0, (cx, 9.2, 8.0)) if susto else None
        add('braco', cx - 0.7, cx + 0.7, 1.4, 9.2, 7.3, 8.7, 'manto', rot, so=('north', 'south', 'east', 'west'))
        add('cotovelo', cx - 0.78, cx + 0.78, 0.6, 1.7, 7.22, 8.78, 'trapo', rot)
        add('antebraco', cx - 0.6, cx + 0.6, -6.8, 1.0, 7.4, 8.6, 'manto', rot, so=('north', 'south', 'east', 'west'))
        add('punho', cx - 0.85, cx + 0.85, -7.4, -5.6, 7.15, 8.85, 'trapo', rot)
        add('mao', cx - 0.75, cx + 0.75, -9.4, -7.2, 7.3, 8.7, 'porc', rot)
        for k, (dx, fim) in enumerate(((-0.56, -11.6), (-0.19, -12.3), (0.19, -12.1), (0.56, -11.3))):
            fx = cx + dx * 1.0
            add('dedo', fx - 0.17, fx + 0.17, fim, -9.3, 7.83, 8.17, 'porc', rot)
        dedao = cx - lado * 0.82
        add('dedao', dedao - 0.17, dedao + 0.17, -10.6, -8.6, 7.45, 7.79, 'porc', rot)
    # pescoco fino inclinado para a frente
    add('pescoco', 7.3, 8.7, 9.6, 13.0, 7.0, 8.4, 'capuz', ('x', -22.5, (8.0, 9.6, 7.7)))
    # cabeca (toda deslocada HZ para a frente); no susto fica torta e o queixo cai mostrando a goela vermelha
    rc = ('z', 22.5, (8.0, 12.8, 7.0)) if susto else None

    def cab(nome, x0, x1, y0, y1, z0, z1, mat, rot=rc, emite=0, so=None):
        c = add(nome, x0, x1, y0, y1, z0 + HZ, z1 + HZ, mat, rot, emite, so)
        c.cabeca = True
        return c

    x0, x1, y0, y1, z0, z1 = MASC
    cab('cranio', 5.3, 10.7, 12.4, 19.6, 4.6, 9.6, 'capuz', so=('north', 'down'))
    cab('mascara', x0, x1, y0, y1, z0, z1, 'mascara', emite=4, so=('north',))
    # capuz fino e justo, colado na cabeca (a cara oval fica bem a mostra)
    cab('capuz_t', 5.0, 11.0, 12.0, 19.8, 9.6, 10.3, 'capuz')
    cab('capuz_d', 4.75, 5.3, 11.8, 19.6, 3.65, 10.3, 'capuz')
    cab('capuz_e', 10.7, 11.25, 11.8, 19.6, 3.65, 10.3, 'capuz')
    cab('capuz_m', 5.0, 11.0, 19.5, 20.6, 3.55, 10.1, 'capuz')
    cab('capuz_c', 6.1, 9.9, 20.5, TOPO, 3.9, 9.6, 'capuz')
    cab('capuz_rabo', 6.6, 9.4, 11.6, 18.4, 10.2, 10.8, 'capuz')
    # pupilas que brilham (cubinhos na frente da mascara)
    for u, v, r in OLHOS:
        cx, cy = x1 - u, y1 - v
        p = 0.13
        cab('pupila', cx - p, cx + p, cy - p, cy + p, z0 - 0.06, z0 + 0.02, 'olho', emite=13)
    for c in cs:
        afinar(c, *((KC, KCZ) if getattr(c, 'cabeca', False) else (KX, KZ)))
    return cs


def afinar(c, kx, kz):
    """Deixa o monstro mais fino: encolhe x e z em torno do centro (8, 8)."""
    if c.nome == 'pupila':                # o olho nao encolhe, so acompanha a posicao
        cx, cz = (c.de[0] + c.ate[0]) / 2, (c.de[2] + c.ate[2]) / 2
        dx, dz = 8 + (cx - 8) * kx - cx, 8 + (cz - 8) * kz - cz
        c.de = c.de + np.array([dx, 0, dz])
        c.ate = c.ate + np.array([dx, 0, dz])
    else:
        for v in (c.de, c.ate):
            v[0] = 8 + (v[0] - 8) * kx
            v[2] = 8 + (v[2] - 8) * kz
    if c.rot:
        e, ang, (ox, oy, oz) = c.rot
        c.rot = (e, ang, (8 + (ox - 8) * kx, oy, 8 + (oz - 8) * kz))


# ------------------------------------------------------------------ pintura
def _linha(F, pts):
    """Distancia ate uma polilinha (pontos em unidades da face)."""
    d = np.full(F.x.shape, 9.0)
    for (ax, ay), (bx, by) in zip(pts[:-1], pts[1:]):
        vx, vy = bx - ax, by - ay
        t = np.clip(((F.x - ax) * vx + (F.y - ay) * vy) / (vx * vx + vy * vy + 1e-9), 0, 1)
        d = np.minimum(d, np.hypot(F.x - (ax + t * vx), F.y - (ay + t * vy)))
    return d


def _racha(F, rng, x0, y0, x1, y1, passos=14, trem=0.12):
    t = np.linspace(0, 1, passos)
    xs = x0 + (x1 - x0) * t + np.concatenate([[0], np.cumsum(rng.normal(0, trem, passos - 1))])
    ys = y0 + (y1 - y0) * t + np.concatenate([[0], np.cumsum(rng.normal(0, trem * 0.5, passos - 1))])
    return _linha(F, list(zip(xs, ys)))


def tecido(F, c, f, w, h, sem, mat, rng):
    base = {'manto2': 'manto2', 'capuz': 'capuz', 'calca': 'calca', 'jaleco': 'jaleco'}.get(mat, 'manto')
    F.pintar(F.ret(0, 0, w, h), base, alt=0.12, bisel=0.12, ruido=0.22, escala_ruido=1.2)
    # trama do pano e dobras suaves que descem
    F.alt += 0.025 * (TH.fbm(F.x * 9, F.y * 9, 0.3, oct=2, seed=sem + 3) - 0.5)
    dob = TH.fbm(F.x * 0.45 + sem * 0.3, F.y * 0.12, 1.7, oct=3, seed=sem) - 0.5
    F.alt += 0.9 * dob
    F.alb *= (0.75 + 0.5 * (dob[..., None] + 0.5))
    # pano gasto: mais claro nas quinas
    borda = np.minimum(np.minimum(F.x, w - F.x), np.minimum(F.y, h - F.y))
    F.alb *= (1 + 0.2 * np.clip(1 - borda / 0.3, 0, 1))[..., None]
    # musgo e terra so perto do chao
    yw = np.full(F.x.shape, float(c.de[1])) if f in ('up', 'down') else c.ate[1] - F.y
    limiar = 0.08 + np.clip((yw + 13.0) / 8.0, 0, 1) * 0.6
    musgo = TH.fbm(F.x * 0.8 + sem, F.y * 0.8, sem * 0.1, oct=3, seed=sem) - 0.5
    terra = np.clip((-12.5 - yw) / 3.0, 0, 1)
    F.alb = F.alb * (1 - 0.5 * terra[..., None]) + np.array([0.10, 0.08, 0.05])[None, None, :] * 0.5 * terra[..., None]
    if mat == 'jaleco' and f in ('north', 'south', 'east', 'west') and c.de[1] < -5:
        serra = 0.15 + 0.3 * np.abs(np.sin(F.x * 3.1 + sem)) * (TH.fbm(F.x * 2, 0.5, 0.1, oct=2, seed=sem) > 0.45)
        F.a[F.y > h - serra] = 0
    if mat == 'trapo' and f in ('north', 'south', 'east', 'west'):
        serra = 0.3 + 0.45 * np.abs(np.sin(F.x * 2.3 + sem)) + 0.25 * np.abs(np.sin(F.x * 7.7 + sem * 0.5))
        F.a[F.y > h - serra] = 0
        n_furos = int(w * h / 18)
        for _ in range(n_furos):
            cx, cy = rng.uniform(0.3, max(0.31, w - 0.3)), rng.uniform(h * 0.35, h * 0.9)
            F.a[np.hypot((F.x - cx) / 0.22, (F.y - cy) / 0.45) < 1] = 0


def pintar_face(c, f, wpx, hpx, sem):
    if wpx < 4 or hpx < 4:
        img, b = pintar_face(c, f, max(wpx, 4), max(hpx, 4), sem)
        img = np.array(Image.fromarray(img, 'RGBA').resize((wpx, hpx), Image.BOX))
        b = np.array(Image.fromarray((b * 255).astype(np.uint8), 'L').resize((wpx, hpx), Image.BOX)) / 255.0
        return img, b
    w, h = wpx / S, hpx / S
    F = TH.Face(w, h, sem)
    tudo = F.ret(0, 0, w, h)
    rng = np.random.default_rng(sem)
    mat = c.pele
    brilho_base = 0.0
    if mat in ('manto', 'manto2', 'capuz', 'trapo', 'calca', 'jaleco'):
        tecido(F, c, f, w, h, sem, mat, rng)
    elif mat in ('porc', 'porc2'):
        F.pintar(tudo, mat, alt=0.25, bisel=0.2, ruido=0.05)
        if min(w, h) >= 1.0 and c.nome not in ('bochecha', 'queixo', 'cranio'):
            d = _racha(F, rng, rng.uniform(0, w), 0, rng.uniform(0, w), h, trem=0.08)
            F.cavar(d - 0.02, 0.18, 0.05)
            F.alb *= (1 - np.clip(1 - d / 0.05, 0, 1) * 0.7)[..., None]
        if c.nome in ('bochecha', 'queixo'):
            brilho_base = 0.12
    elif mat == 'mascara':
        F.pintar(tudo, 'porc', alt=0.35, bisel=0.45, ruido=0.04)
        brilho_base = 0.12
        if f == 'north':
            # oval: mais escuro e mais baixo perto das bordas, verniz em cima
            ov = np.hypot((F.x - w / 2) / (w / 2), (F.y - h * 0.47) / (h * 0.55))
            F.alt -= 0.5 * np.clip(ov - 0.6, 0, 1)
            F.alb *= (1 - 0.35 * np.clip(ov - 0.75, 0, 1))[..., None]
            oval = np.hypot((F.x - w / 2) / (w / 2), (F.y - h * 0.46) / (h * 0.54))
            F.a[oval > 1.0] = 0
            # cara lisa e palida: so duas orbitas fundas com um pontinho vermelho
            for bx in (OLHOS[0][0] * KC, OLHOS[1][0] * KC):
                k = np.clip(1 - np.hypot((F.x - bx) / (1.0 * KC), (F.y - 2.95) / 1.15), 0, 1) ** 1.6
                F.alb *= (1 - 0.18 * k)[..., None]
                F.alt -= 0.2 * k
            for u, v, r in OLHOS:
                u = u * KC
                buraco = (np.hypot((F.x - u) / (r * 0.78 * KC), (F.y - v) / (r * 1.0)) - 1) * r
                F.pintar(buraco, 'buraco')
                F.alt[buraco < 0] = -0.7
                F.luz(F.circ(u, v + 0.05, r * 0.22), VERMELHO, VERMELHO2, forca=1.2, largura=0.0, halo=0.25)
    elif mat == 'goela':
        F.pintar(tudo, 'buraco')
        F.luz(tudo + 0.08, VERMELHO * 0.8, VERMELHO2, forca=0.9, largura=0.0, halo=0.4)
    elif mat == 'corda':
        F.pintar(tudo, 'corda', alt=0.1, bisel=0.1, ruido=0.2)
        torc = np.sin((F.x * 2.2 + F.y * 2.2) * math.pi)
        F.alt += 0.18 * torc
        F.alb *= (0.8 + 0.2 * (torc[..., None] > 0))
    elif mat == 'medalhao':
        F.pintar(tudo, 'porc2', alt=0.1)
        if f == 'north':
            cx, cy = w / 2, h / 2
            F.pintar(F.circ(cx, cy, min(w, h) / 2 - 0.05), 'porc', alt=0.3, bisel=0.2)
            F.cavar(F.circ(cx, cy, min(w, h) / 2 - 0.25), 0.15, 0.05)
            F.pintar(F.circ(cx, cy, 0.42), 'buraco')
            F.luz(F.circ(cx, cy, 0.14), VERMELHO, VERMELHO2, forca=1.2, largura=0.0, halo=0.3)
    elif mat == 'olho':
        F.pintar(tudo, 'buraco')
        F.luz(tudo + 0.05, VERMELHO, VERMELHO2, forca=1.8, largura=0.0, halo=0.3)
    else:   # preto
        F.pintar(tudo, 'preto', alt=0.25, bisel=0.2, ruido=0.08)
    img = F.render()
    brilho = np.maximum(np.clip(np.max(F.emis, axis=2), 0, 1.2) / 1.2, brilho_base * (F.a > 0.5))
    return img, brilho


def montar_atlas(cs, larg, escala):
    alt = kit.arrumar_uv(cs, larg, escala)
    H = 16
    while H < alt:
        H *= 2
    img = np.zeros((H, larg, 4), np.uint8)
    bri = np.zeros((H, larg))
    for i, c in enumerate(cs):
        for f in c.faces():
            x0, y0, x1, y1 = c.uv[f]
            rgba, b = pintar_face(c, f, x1 - x0, y1 - y0, 300 + i * 7 + len(f))
            img[y0:y1, x0:x1] = rgba[:y1 - y0, :x1 - x0]
            bri[y0:y1, x0:x1] = b[:y1 - y0, :x1 - x0]
    return img, bri, H


def alfa_binario(rgba):
    out = rgba.copy()
    out[..., 3] = np.where(out[..., 3] >= 128, 255, 0)
    return out


def alfa_brilho(rgba, bri, forca=200):
    out = alfa_binario(rgba)
    vis = out[..., 3] > 0
    out[..., 3] = np.where(vis, np.clip(255 - np.round(np.clip(bri, 0, 1) * forca), 40, 255), 0).astype(np.uint8)
    # no Bedrock (entity_emissive_alpha) alfa 0 BRILHA em vez de sumir: o que era buraco vira pano preto opaco
    buraco = ~vis
    out[buraco, 0:3] = (9, 9, 11)
    out[buraco, 3] = 255
    return out


def gerar(saida):
    sys.path.insert(0, os.path.join(AQUI, '..', 'coronapack'))
    import bedrock_v2 as BV
    J = os.path.join(saida, 'java', 'assets', 'corona')
    B = os.path.join(saida, 'bedrock')
    LARG, ESC = 512, S
    base = cubos(0)
    # UV: todos os quadros usam as regioes do quadro 0 (mesmos nomes/peles por posicao na lista)
    img, bri, H = montar_atlas(base, LARG, ESC)
    os.makedirs(os.path.join(J, 'textures', 'item', 'props'), exist_ok=True)
    Image.fromarray(alfa_binario(img), 'RGBA').save(os.path.join(J, 'textures', 'item', 'props', 'porcelana.png'), optimize=True)
    tex_bed = 'textures/entity/corona_v2/item_props_porcelana'
    os.makedirs(os.path.join(B, os.path.dirname(tex_bed)), exist_ok=True)
    Image.fromarray(alfa_brilho(img, bri), 'RGBA').save(os.path.join(B, tex_bed + '.png'), optimize=True)
    mapeamento = []
    item_tex = {}
    previas = []
    for q in (0, 1):
        cs = cubos(q)
        for a, b in zip(cs, base):
            a.uv = dict(b.uv)
        nome = f'porcelana_f{q}'
        modelo = kit.modelo_java(cs, 'corona:item/props/porcelana', LARG, H, display={})
        modelo['credit'] = 'Corona Server - O Porcelana (criatura original)'
        kit.salvar_json(os.path.join(J, 'models', 'props', nome + '.json'), modelo)
        kit.salvar_json(os.path.join(J, 'items', 'props', nome + '.json'), kit.item_java(f'corona:props/{nome}'))
        geo_id = f'geometry.corona.prop_{nome}'
        kit.salvar_json(os.path.join(B, 'models', 'entity', 'corona_v2', f'prop_{nome}.geo.json'), BV.geometria_de_modelo(geo_id, modelo))
        at = BV.attachable_prop(f'corona:prop_{nome}', tex_bed, geo_id)
        at['minecraft:attachable']['description']['materials'] = {'default': 'entity_emissive_alpha', 'enchanted': 'entity_emissive_alpha'}
        kit.salvar_json(os.path.join(B, 'attachables', f'prop_{nome}.json'), at)
        pv = kit.previa(cs, alfa_binario(img), 180 if q == 0 else 200, 8, escala=7)
        previas.append(pv)
        ic = pv.convert('RGBA')
        arr = np.array(ic)
        fundo = (np.abs(arr[..., 0].astype(int) - 28) < 3) & (np.abs(arr[..., 1].astype(int) - 30) < 3) & (np.abs(arr[..., 2].astype(int) - 36) < 3)
        arr[fundo, 3] = 0
        ic = Image.fromarray(arr, 'RGBA')
        ic.thumbnail((32, 32), Image.LANCZOS)
        quad = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
        quad.paste(ic, ((32 - ic.width) // 2, (32 - ic.height) // 2))
        os.makedirs(os.path.join(B, 'textures', 'items', 'corona', 'v2'), exist_ok=True)
        quad.save(os.path.join(B, 'textures', 'items', 'corona', 'v2', f'corona_prop_{nome}.png'))
        item_tex[f'corona_prop_{nome}'] = {'textures': f'textures/items/corona/v2/corona_prop_{nome}'}
        mapeamento.append({'type': 'definition', 'model': f'corona:props/{nome}', 'bedrock_identifier': f'corona:prop_{nome}',
                           'display_name': f'Prop {nome}', 'bedrock_options': {'icon': f'corona_prop_{nome}', 'creative_category': 'equipment',
                                                                              'protection_value': 1}})
    kit.salvar_json(os.path.join(saida, 'corona-porcelana.json'), {'format_version': 2, 'items': {'minecraft:leather_helmet': mapeamento}})
    kit.salvar_json(os.path.join(saida, 'item_texture_extra.json'), item_tex)
    folha = Image.new('RGB', (sum(p.width for p in previas) + 30, max(p.height for p in previas) + 20), (28, 30, 36))
    x = 10
    for p in previas:
        folha.paste(p, (x, 10))
        x += p.width + 10
    folha.save(os.path.join(saida, 'previa_porcelana.png'))
    print('porcelana: cubos', len(base), 'textura', LARG, 'x', H)


if __name__ == '__main__':
    gerar(SAIDA)
