"""Desenha a pose REAL dos chefes 3D a partir do corpos-dump.json que o plugin grava (masmorras admin corpo-dump):
confere pivos, pais e o sentido dos giros sem precisar entrar no jogo.

uso: python3 ver_dump.py <corpos-dump.json> <pasta java/assets/corona> <saida.png> [titulo]
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

dump = json.load(open(sys.argv[1]))
JAVA = sys.argv[2]
SAIDA = sys.argv[3]
TITULO = sys.argv[4] if len(sys.argv) > 4 else ''


def quat(q):
    x, y, z, w = q
    return np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                     [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                     [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])


def rot_eixo(eixo, ang):
    a = math.radians(ang)
    c, s = math.cos(a), math.sin(a)
    return {'x': np.array([[1, 0, 0], [0, c, -s], [0, s, c]]), 'y': np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]]),
            'z': np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])}[eixo]


_tex = {}


def cor_face(modelo, face):
    tex = modelo['textures']['0'].split(':')[1]
    if tex not in _tex:
        _tex[tex] = np.asarray(Image.open(os.path.join(JAVA, 'textures', tex + '.png')).convert('RGBA')).astype(float)
    img = _tex[tex]
    k = img.shape[0] / 16
    u0, v0, u1, v1 = face['uv']
    a, b = sorted((u0, u1)), sorted((v0, v1))
    reg = img[int(b[0] * k):max(int(b[0] * k) + 1, int(b[1] * k)), int(a[0] * k):max(int(a[0] * k) + 1, int(a[1] * k))]
    vis = reg[..., 3] > 0
    if not vis.any():
        return None
    return reg[vis][:, :3].mean(0)


FACES = {'north': [(0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 1, 0)], 'south': [(0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1)],
         'west': [(0, 0, 0), (0, 0, 1), (0, 1, 1), (0, 1, 0)], 'east': [(1, 0, 0), (1, 0, 1), (1, 1, 1), (1, 1, 0)],
         'down': [(0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)], 'up': [(0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)]}


def poligonos(corpo, yaw_vista):
    out = []
    Ry = rot_eixo('y', yaw_vista)
    for p in corpo['pecas']:
        f = os.path.join(JAVA, 'models', 'masmorras', f"{corpo['modelo']}_{p['nome']}.json")
        if not os.path.exists(f):
            continue
        m = json.load(open(f))
        R = quat(p['q'])
        t = np.array(p['t'])
        s = p['s']
        for el in m['elements']:
            de, ate = np.array(el['from']), np.array(el['to'])
            er = el.get('rotation')
            for fn, cantos in FACES.items():
                if fn not in el.get('faces', {}):
                    continue
                cor = cor_face(m, el['faces'][fn])
                if cor is None:
                    continue
                pts = []
                for c in cantos:
                    v = de + (ate - de) * np.array(c)
                    if er:
                        o = np.array(er['origin'])
                        v = rot_eixo(er['axis'], er['angle']) @ (v - o) + o
                    v = (v - 8) / 16.0
                    v = np.array([-v[0], v[1], -v[2]])   # o item e desenhado girado 180 graus
                    v = R @ (v * s) + t
                    v = Ry @ v
                    pts.append(v)
                pts = np.array(pts)
                n = np.cross(pts[1] - pts[0], pts[3] - pts[0])
                nn = np.linalg.norm(n)
                luz = 0.75 if nn == 0 else 0.55 + 0.45 * abs(np.dot(n / nn, np.array([0.3, 0.8, 0.5]) / np.linalg.norm([0.3, 0.8, 0.5])))
                out.append((pts[:, 2].mean(), pts, np.clip(cor * luz, 0, 255)))
    return out


def desenhar(corpo, yaw_vista, W=260, H=330, esc=95):
    img = Image.new('RGB', (W, H), (150, 158, 168))
    d = ImageDraw.Draw(img)
    for _, pts, cor in sorted(poligonos(corpo, yaw_vista), key=lambda x: x[0]):
        d.polygon([(W / 2 + x * esc, H - 20 - y * esc) for x, y, _ in pts], fill=tuple(int(c) for c in cor))
    d.text((6, 4), f"{corpo['modelo']} {corpo['acao']}", fill=(20, 20, 30))
    return img


folhas = []
for c in dump:
    folhas.append([desenhar(c, 0), desenhar(c, -55), desenhar(c, 90)])
W = 260 * 3
H = 330 * len(folhas) + 30
tudo = Image.new('RGB', (W, H), (34, 36, 44))
ImageDraw.Draw(tudo).text((8, 8), TITULO, fill=(240, 240, 230))
for i, f in enumerate(folhas):
    for j, im in enumerate(f):
        tudo.paste(im, (j * 260, 30 + i * 330))
tudo.save(SAIDA)
print('ok', len(folhas))
