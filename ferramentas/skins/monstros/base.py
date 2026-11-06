"""Base para pintar skins de monstros (layout de skin 64x64 do Minecraft) em alta resolucao.

Cada pixel de cada face e mapeado para um ponto 3D do modelo (em unidades de skin), entao o ruido e continuo
entre as faces. Os desenhos de cada monstro recebem arrays (x, y, z, s, t, normal) e devolvem RGBA 0..1.
"""
import numpy as np
from PIL import Image

R = 4                 # pixels por unidade de skin (64 * 4 = 256)
N = 64 * R

# parte: (tamanho w,h,d), uv base, uv camada externa, origem 3D (canto x,y,z minimo)
PARTES = {
    'head': ((8, 8, 8), (0, 0), (32, 0), (-4, 24, -4)),
    'body': ((8, 12, 4), (16, 16), (16, 32), (-4, 12, -2)),
    'rarm': ((4, 12, 4), (40, 16), (40, 32), (-8, 12, -2)),
    'larm': ((4, 12, 4), (32, 48), (48, 48), (4, 12, -2)),
    'rleg': ((4, 12, 4), (0, 16), (0, 32), (-4, 0, -2)),
    'lleg': ((4, 12, 4), (16, 48), (0, 48), (0, 0, -2)),
}
FACES = ('top', 'bottom', 'right', 'front', 'left', 'back')


def ret_face(face, w, h, d, u, v):
    return {
        'top': (u + d, v, w, d),
        'bottom': (u + d + w, v, w, d),
        'right': (u, v + d, d, h),
        'front': (u + d, v + d, w, h),
        'left': (u + d + w, v + d, d, h),
        'back': (u + 2 * d + w, v + d, w, h),
    }[face]


def coords(parte, face):
    (w, h, d), uv, ov, (x0, y0, z0) = PARTES[parte]
    _, _, W, H = ret_face(face, w, h, d, 0, 0)
    i = (np.arange(W * R) + 0.5) / (W * R)
    j = (np.arange(H * R) + 0.5) / (H * R)
    s, t = np.meshgrid(i, j)
    if face == 'front':
        x, y, z, n = x0 + s * w, y0 + h - t * h, np.full_like(s, z0 + d), (0, 0, 1)
    elif face == 'back':
        x, y, z, n = x0 + w - s * w, y0 + h - t * h, np.full_like(s, z0), (0, 0, -1)
    elif face == 'right':
        x, y, z, n = np.full_like(s, x0), y0 + h - t * h, z0 + s * d, (-1, 0, 0)
    elif face == 'left':
        x, y, z, n = np.full_like(s, x0 + w), y0 + h - t * h, z0 + d - s * d, (1, 0, 0)
    elif face == 'top':
        x, y, z, n = x0 + s * w, np.full_like(s, y0 + h), z0 + t * d, (0, 1, 0)
    else:
        x, y, z, n = x0 + s * w, np.full_like(s, y0), z0 + d - t * d, (0, -1, 0)
    return dict(x=x, y=y, z=z, s=s, t=t, n=n, parte=parte, face=face, w=w, h=h, d=d)


# ------------------------------------------------------------------ ruido

_rng = np.random.RandomState(20261102)
_PERM = np.concatenate([_rng.permutation(256)] * 2)
_VAL = _rng.rand(256)


def _h(ix, iy, iz, seed):
    return _VAL[_PERM[(_PERM[(_PERM[(ix + seed * 31) & 255] + iy) & 255] + iz) & 255]]


def vnoise(x, y, z, seed=0):
    ix, iy, iz = np.floor(x).astype(np.int64), np.floor(y).astype(np.int64), np.floor(z).astype(np.int64)
    fx, fy, fz = x - ix, y - iy, z - iz
    ux, uy, uz = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy), fz * fz * (3 - 2 * fz)
    def L(a, b, t):
        return a + (b - a) * t
    c000, c100 = _h(ix, iy, iz, seed), _h(ix + 1, iy, iz, seed)
    c010, c110 = _h(ix, iy + 1, iz, seed), _h(ix + 1, iy + 1, iz, seed)
    c001, c101 = _h(ix, iy, iz + 1, seed), _h(ix + 1, iy, iz + 1, seed)
    c011, c111 = _h(ix, iy + 1, iz + 1, seed), _h(ix + 1, iy + 1, iz + 1, seed)
    return L(L(L(c000, c100, ux), L(c010, c110, ux), uy), L(L(c001, c101, ux), L(c011, c111, ux), uy), uz)


def fbm(x, y, z, oct=4, f=1.0, seed=0, gain=0.5):
    tot, amp, norm = 0.0, 1.0, 0.0
    for o in range(oct):
        tot = tot + amp * vnoise(x * f, y * f, z * f, seed + o * 7)
        norm += amp
        amp *= gain
        f *= 2.03
    return tot / norm


def ridged(x, y, z, oct=4, f=1.0, seed=0):
    """Linhas finas (rachaduras, veias): 1 na linha, 0 longe."""
    tot, amp, norm = 0.0, 1.0, 0.0
    for o in range(oct):
        n = 1 - np.abs(vnoise(x * f, y * f, z * f, seed + o * 13) * 2 - 1)
        tot = tot + amp * n ** 6
        norm += amp
        amp *= 0.55
        f *= 2.1
    return tot / norm


def hash2(a, b, seed=0):
    """Numero 0..1 estavel para pares inteiros."""
    a = np.asarray(a, dtype=np.int64)
    b = np.asarray(b, dtype=np.int64)
    return _VAL[_PERM[(_PERM[(a + seed * 17) & 255] + b) & 255]]


# ------------------------------------------------------------------ utilidades

def C(h):
    """Cor: '#rrggbb' ou um array/lista (r,g,b) em 0..1 (devolve como esta)."""
    if not isinstance(h, str):
        return np.asarray(h, float)
    h = h.lstrip('#')
    return np.array([int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)])


def ss(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def rampa(v, paradas):
    """paradas: lista de (posicao, cor hex). Retorna array (...,3)."""
    v = np.clip(v, 0, 1)
    pos = np.array([p for p, _ in paradas])
    cores = np.array([C(c) for _, c in paradas])
    out = np.zeros(v.shape + (3,))
    for k in range(3):
        out[..., k] = np.interp(v, pos, cores[:, k])
    return out


def mix(a, b, m):
    m = np.asarray(m)
    if m.ndim == a.ndim - 1 or (np.ndim(m) and m.shape != a.shape):
        m = m[..., None]
    return a * (1 - m) + b * m


def cor_cheia(shape, h):
    return np.broadcast_to(C(h), shape + (3,)).copy()


def elipse(s, t, cx, cy, rx, ry, ang=0.0):
    """Distancia normalizada (1 = borda) de uma elipse girada."""
    ca, sa = np.cos(ang), np.sin(ang)
    dx, dy = s - cx, t - cy
    u = (dx * ca + dy * sa) / rx
    w = (-dx * sa + dy * ca) / ry
    return np.sqrt(u * u + w * w)


def dentro(dist, borda=0.08):
    """Mascara suave de dentro (dist < 1)."""
    return 1 - ss(1 - borda, 1 + borda, dist)


def dentes(pos, borda, comprimento, periodo, sentido=1, afiado=1.6, fase=0.0, varia=None):
    """Mascara de dentes ao longo de uma borda.
    pos: coordenada ao longo da borda; borda: distancia (assinada) a partir da linha da gengiva para dentro da boca.
    Dentes triangulares de comprimento variavel."""
    f = (pos / periodo + fase)
    k = np.floor(f)
    fr = f - k
    tri = 1 - np.abs(fr - 0.5) * 2          # 0 nas pontas, 1 no meio
    comp = comprimento * (0.6 + 0.4 * hash2(k.astype(np.int64), 7)) if varia is None else comprimento
    alt = comp * tri ** (1 / afiado)
    m = (borda >= 0) & (borda <= alt)
    return m, (borda / np.maximum(alt, 1e-6)), fr


def ao_borda(P, forca=0.35, larg=0.18):
    s, t = P['s'], P['t']
    e = np.minimum(np.minimum(s, 1 - s), np.minimum(t, 1 - t))
    return (1 - forca * (1 - ss(0, larg, e)))[..., None]


# ------------------------------------------------------------------ montagem

def montar(pintar, nome):
    """pintar(P, camada) -> (rgb, alpha). Retorna imagem RGBA 256x256 uint8."""
    img = np.zeros((N, N, 4))
    for parte, ((w, h, d), uv, ov, _) in PARTES.items():
        for camada, base in (('base', uv), ('over', ov)):
            for face in FACES:
                u, v, W, H = ret_face(face, w, h, d, *base)
                P = coords(parte, face)
                rgb, a = pintar(P, camada)
                rgb = np.clip(rgb, 0, 1)
                a = np.clip(np.broadcast_to(a, rgb.shape[:2]), 0, 1)
                if camada == 'base':
                    a = np.ones_like(a)
                img[v * R:(v + H) * R, u * R:(u + W) * R, :3] = rgb
                img[v * R:(v + H) * R, u * R:(u + W) * R, 3] = a
    return (img * 255 + 0.5).astype(np.uint8)


def reduzir(arr, fator, corte=True):
    """Reduz a resolucao (media ponderada pela opacidade). corte=True deixa a opacidade 0 ou 255 (Bedrock)."""
    h, w = arr.shape[0] // fator, arr.shape[1] // fator
    a = arr.astype(np.float64).reshape(h, fator, w, fator, 4)
    al = a[..., 3:4] / 255
    rgb = (a[..., :3] * al).sum(axis=(1, 3)) / np.maximum(al.sum(axis=(1, 3)), 1e-6)
    alpha = al.mean(axis=(1, 3))[..., 0]
    vazio = alpha < 1e-6
    rgb[vazio] = 0
    if corte:
        alpha = np.where(alpha >= 0.42, 1.0, 0.0)
    out = np.dstack([rgb, alpha * 255])
    return (out + 0.5).clip(0, 255).astype(np.uint8)


# ------------------------------------------------------------------ previa

def _face_img(img, parte, face, camada):
    (w, h, d), uv, ov, _ = PARTES[parte]
    u, v, W, H = ret_face(face, w, h, d, *(uv if camada == 'base' else ov))
    return Image.fromarray(img[v * R:(v + H) * R, u * R:(u + W) * R])


def previa(img, cabeca=False, fundo=(40, 44, 52)):
    """Folha de conferencia: frente, costas e lado do corpo, mais a cabeca grande."""
    S = 3
    vistas = []
    if not cabeca:
        for vista in ('front', 'back', 'right'):
            can = Image.new('RGBA', (18 * R, 34 * R), fundo + (255,))
            for camada in ('base', 'over'):
                for parte, ((w, h, d), _, _, (x0, y0, z0)) in PARTES.items():
                    if vista == 'right' and parte in ('larm', 'lleg', 'body'):
                        continue
                    fi = _face_img(img, parte, vista, camada)
                    if vista == 'front':
                        sx = x0 + 9
                    elif vista == 'back':
                        sx = -(x0 + w) + 9
                    else:
                        sx = z0 + 9 if parte != 'head' else z0 + 9
                    sy = 33 - (y0 + h)
                    ex = 0.5 if (camada == 'over' and parte == 'head') else (0.25 if camada == 'over' else 0)
                    W = fi.size[0] / R
                    H = fi.size[1] / R
                    fi2 = fi.resize((int((W + 2 * ex) * R), int((H + 2 * ex) * R)), Image.NEAREST)
                    can.alpha_composite(fi2, (int((sx - ex) * R), int((sy - ex) * R)))
            vistas.append(can.resize((can.size[0] * S, can.size[1] * S), Image.NEAREST))
    # cabeca grande: frente, direita, esquerda, costas, topo
    for face in ('front', 'right', 'left', 'back', 'top'):
        can = Image.new('RGBA', (10 * R, 10 * R), fundo + (255,))
        can.alpha_composite(_face_img(img, 'head', face, 'base'), (R, R))
        ov = _face_img(img, 'head', face, 'over').resize((9 * R, 9 * R), Image.NEAREST)
        can.alpha_composite(ov, (int(0.5 * R), int(0.5 * R)))
        k = 6 if cabeca else 4
        vistas.append(can.resize((can.size[0] * k // 2, can.size[1] * k // 2), Image.NEAREST))
    W = sum(v.size[0] for v in vistas) + 10 * len(vistas)
    H = max(v.size[1] for v in vistas)
    folha = Image.new('RGBA', (W, H), fundo + (255,))
    x = 0
    for v in vistas:
        folha.alpha_composite(v, (x, 0))
        x += v.size[0] + 10
    return folha
