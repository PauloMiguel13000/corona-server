"""Renderiza as armaduras temáticas em 3D (vista 3/4) A PARTIR DOS PACOTES PUBLICADOS, do jeito que o jogo monta:
Java  : assets/corona/equipment/<tema>.json -> camadas humanoid / humanoid_leggings -> modelo de armadura vanilla
Bedrock: attachables/arm_<tema>_<peca>_<material>.json -> textura + geometry.player.armor.* (mesma caixa vanilla)
e confere: todas as peças/materiais existem, a textura que o Bedrock usa é a mesma do Java, sem alpha "meio
transparente" (o material armor do Bedrock e o cutout do Java só aceitam 0 ou 255), tamanho 64x32 * k.

uso: python3 -I render_armaduras.py <corona-pack.zip> <corona.mcpack> <saida.png> [TEMA ...]
"""
import io
import json
import sys
import zipfile

import numpy as np
from PIL import Image, ImageDraw

PECAS = ('elmo', 'peitoral', 'calca', 'botas')
MATS = ('leather', 'chainmail', 'iron', 'golden', 'diamond', 'netherite')
GEO = {'elmo': 'geometry.player.armor.helmet', 'peitoral': 'geometry.player.armor.chestplate',
       'calca': 'geometry.player.armor.leggings', 'botas': 'geometry.player.armor.boots'}


def ler_img(z, nome):
    return Image.open(io.BytesIO(z.read(nome))).convert('RGBA')


def manequim():
    """Pele simples (cinza azulado, rosto com olhos) 64x64."""
    p = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(p)
    d.rectangle((0, 0, 31, 15), fill=(196, 152, 120, 255))     # cabeca
    d.rectangle((8, 0, 15, 7), fill=(70, 50, 30, 255))          # cabelo topo
    d.rectangle((8, 8, 15, 9), fill=(70, 50, 30, 255))
    d.rectangle((9, 12, 10, 12), fill=(255, 255, 255, 255))
    d.rectangle((13, 12, 14, 12), fill=(255, 255, 255, 255))
    d.point((10, 12), fill=(60, 60, 140, 255))
    d.point((13, 12), fill=(60, 60, 140, 255))
    d.rectangle((16, 16, 39, 31), fill=(70, 130, 170, 255))     # corpo
    d.rectangle((40, 16, 55, 31), fill=(196, 152, 120, 255))    # braco
    d.rectangle((0, 16, 15, 31), fill=(60, 60, 140, 255))       # perna
    return p


class Render:
    def __init__(self, W=200, H=300, esc=7.0, yaw=32, pitch=18):
        self.W, self.H, self.esc = W, H, esc
        a, b = np.radians(yaw), np.radians(pitch)
        # camera: gira em y (yaw) e depois inclina (pitch)
        ry = np.array([[np.cos(a), 0, np.sin(a)], [0, 1, 0], [-np.sin(a), 0, np.cos(a)]])
        rx = np.array([[1, 0, 0], [0, np.cos(b), -np.sin(b)], [0, np.sin(b), np.cos(b)]])
        self.R = rx @ ry
        self.cor = np.zeros((H, W, 3))
        self.cor[:] = (0.15, 0.16, 0.19)
        self.z = np.full((H, W), -1e9)
        yy, xx = np.mgrid[0:H, 0:W]
        self.px, self.py = xx + 0.5, yy + 0.5

    def proj(self, P):
        q = self.R @ np.asarray(P, float)
        return np.array([self.W / 2 + q[0] * self.esc, self.H * 0.92 - q[1] * self.esc]), q[2]

    def face(self, tex, rect, P0, eU, eV, luz):
        """Desenha um quadrilatero P0 + s*eU + t*eV com o recorte rect (x0,y0,x1,y1 em pixels da textura)."""
        p0, z0 = self.proj(P0)
        pu, zu = self.proj(np.add(P0, eU))
        pv, zv = self.proj(np.add(P0, eV))
        a, b = pu - p0, pv - p0
        det = a[0] * b[1] - a[1] * b[0]
        if abs(det) < 1e-6:
            return
        dx, dy = self.px - p0[0], self.py - p0[1]
        s = (dx * b[1] - dy * b[0]) / det
        t = (a[0] * dy - a[1] * dx) / det
        m = (s >= 0) & (s < 1) & (t >= 0) & (t < 1)
        if not m.any():
            return
        x0, y0, x1, y1 = (int(round(v)) for v in rect)
        tx = np.clip((x0 + s * (x1 - x0)).astype(int), x0, x1 - 1)
        ty = np.clip((y0 + t * (y1 - y0)).astype(int), y0, y1 - 1)
        amostra = tex[ty, tx]
        z = z0 + s * (zu - z0) + t * (zv - z0)
        ok = m & (amostra[..., 3] > 127) & (z >= self.z - 1e-4)
        self.cor[ok] = amostra[ok][:, :3] / 255.0 * luz
        self.z[ok] = z[ok]

    def caixa(self, tex, k, uv, org, tam, inflar=0.0, espelho=False):
        """Caixa do modelo (unidades = pixels do modelo). uv=(u,v) na textura 64x32 (k = escala da textura)."""
        u, v = uv
        w, h, d = tam
        x0, y0, z0 = (org[0] - inflar, org[1] - inflar, org[2] - inflar)
        W, H, D = w + 2 * inflar, h + 2 * inflar, d + 2 * inflar
        r = lambda a, b, c, e: (a * k, b * k, c * k, e * k)  # noqa: E731
        frente = r(u + d, v + d, u + d + w, v + d + h)
        topo = r(u + d, v, u + d + w, v + d)
        lado_dir = r(u, v + d, u + d, v + d + h)            # lado direito do boneco (-x)
        lado_esq = r(u + d + w, v + d, u + 2 * d + w, v + d + h)
        if espelho:
            frente = (frente[2], frente[1], frente[0], frente[3])
            topo = (topo[2], topo[1], topo[0], topo[3])
            lado_dir = (lado_esq[2], lado_esq[1], lado_esq[0], lado_esq[3])
        fix = lambda rc: (min(rc[0], rc[2]), rc[1], max(rc[0], rc[2]), rc[3]) if rc[0] <= rc[2] else rc  # noqa: E731
        # frente (+z), olhando para o boneco; s=0 no lado direito do boneco (-x)
        self._face_rc(tex, frente, (x0, y0 + H, z0 + D), (W, 0, 0), (0, -H, 0), 1.0)
        self._face_rc(tex, topo, (x0, y0 + H, z0), (W, 0, 0), (0, 0, D), 1.12)
        self._face_rc(tex, lado_dir, (x0, y0 + H, z0), (0, 0, D), (0, -H, 0), 0.78)
        del fix

    def _face_rc(self, tex, rc, P0, eU, eV, luz):
        x0, y0, x1, y1 = rc
        if x1 < x0:   # espelhado: inverte s
            P0 = np.add(P0, eU)
            eU = tuple(-c for c in eU)
            x0, x1 = x1, x0
        self.face(tex, (x0, y0, x1, y1), P0, eU, eV, luz)

    def imagem(self):
        return Image.fromarray((np.clip(self.cor, 0, 1) * 255).astype(np.uint8), 'RGB')


def boneco(c1, c2, pele):
    """c1/c2: arrays RGBA das camadas (None = sem a peca)."""
    r = Render()
    P = np.asarray(pele)
    # pele (64x64, k=1)
    r.caixa(P, 1, (0, 0), (-4, 24, -4), (8, 8, 8))
    r.caixa(P, 1, (16, 16), (-4, 12, -2), (8, 12, 4))
    r.caixa(P, 1, (40, 16), (-8, 12, -2), (4, 12, 4))
    r.caixa(P, 1, (40, 16), (4, 12, -2), (4, 12, 4), espelho=True)
    r.caixa(P, 1, (0, 16), (-4, 0, -2), (4, 12, 4))
    r.caixa(P, 1, (0, 16), (0, 0, -2), (4, 12, 4), espelho=True)
    if c2 is not None:
        k = c2.shape[1] // 64
        r.caixa(c2, k, (16, 16), (-4, 12, -2), (8, 12, 4), 0.5)
        r.caixa(c2, k, (0, 16), (-4, 0, -2), (4, 12, 4), 0.5)
        r.caixa(c2, k, (0, 16), (0, 0, -2), (4, 12, 4), 0.5, espelho=True)
    if c1 is not None:
        k = c1.shape[1] // 64
        r.caixa(c1, k, (0, 16), (-4, 0, -2), (4, 12, 4), 1.0)                 # botas
        r.caixa(c1, k, (0, 16), (0, 0, -2), (4, 12, 4), 1.0, espelho=True)
        r.caixa(c1, k, (16, 16), (-4, 12, -2), (8, 12, 4), 1.0)               # peitoral
        r.caixa(c1, k, (40, 16), (-8, 12, -2), (4, 12, 4), 1.0)
        r.caixa(c1, k, (40, 16), (4, 12, -2), (4, 12, 4), 1.0, espelho=True)
        r.caixa(c1, k, (0, 0), (-4, 24, -4), (8, 8, 8), 1.0)                  # elmo
    return r.imagem()


def main():
    jz, bz, saida = zipfile.ZipFile(sys.argv[1]), zipfile.ZipFile(sys.argv[2]), sys.argv[3]
    so = [t.lower() for t in sys.argv[4:]]
    jn, bn = set(jz.namelist()), set(bz.namelist())
    temas = sorted(n.split('/')[-1][:-5] for n in jn if n.startswith('assets/corona/equipment/') and n.endswith('.json'))
    problemas = []
    cels = []
    for tema in temas:
        eq = json.loads(jz.read(f'assets/corona/equipment/{tema}.json'))
        camadas = {}
        for cam in ('humanoid', 'humanoid_leggings'):
            lst = eq.get('layers', {}).get(cam, [])
            if not lst:
                continue
            ns, nome = lst[0]['texture'].split(':')
            arq = f'assets/{ns}/textures/entity/equipment/{cam}/{nome}.png'
            if arq not in jn:
                problemas.append(f'{tema}: Java sem {arq}')
                continue
            camadas[cam] = ler_img(jz, arq)
        # Bedrock: todas as pecas x materiais
        btex = {}
        tem_bedrock = any(n.startswith(f'attachables/arm_{tema}_') for n in bn)
        if tem_bedrock:
            for peca in PECAS:
                for mat in MATS:
                    a = f'attachables/arm_{tema}_{peca}_{mat}.json'
                    if a not in bn:
                        problemas.append(f'{tema}: Bedrock sem {a}')
                        continue
                    d = json.loads(bz.read(a))['minecraft:attachable']['description']
                    tx = d['textures']['default'] + '.png'
                    if tx not in bn:
                        problemas.append(f'{tema}: Bedrock {a} aponta para textura inexistente {tx}')
                        continue
                    geo = d['geometry']['default']
                    if geo == f'geometry.corona.elmo3d.{tema}' and peca == 'elmo':
                        if f'models/entity/corona_elmo3d_{tema}.geo.json' not in bn:
                            problemas.append(f'{tema}: {a} usa {geo}, mas falta models/entity/corona_elmo3d_{tema}.geo.json')
                    elif geo != GEO[peca]:
                        problemas.append(f'{tema}: {a} com geometria {geo}')
                    btex[(peca, mat)] = tx
        # comparar texturas Java x Bedrock e alpha
        for cam, n in (('humanoid', 1), ('humanoid_leggings', 2)):
            if cam not in camadas:
                continue
            im = np.asarray(camadas[cam])
            if im.shape[1] % 64 or im.shape[0] * 2 != im.shape[1]:
                problemas.append(f'{tema}: {cam} com tamanho {im.shape[1]}x{im.shape[0]}')
            meio = ((im[..., 3] > 0) & (im[..., 3] < 255)).sum()
            if meio:
                problemas.append(f'{tema}: {cam} tem {meio} pixels meio transparentes')
            if tem_bedrock:
                peca = 'calca' if n == 2 else 'elmo'
                tx = btex.get((peca, 'iron'))
                if tx and not np.array_equal(np.asarray(ler_img(bz, tx)), im):
                    problemas.append(f'{tema}: textura Bedrock {tx} diferente da Java ({cam})')
        if so and tema not in so:
            continue
        c1 = np.asarray(camadas['humanoid']) if 'humanoid' in camadas else None
        c2 = np.asarray(camadas['humanoid_leggings']) if 'humanoid_leggings' in camadas else None
        jimg = boneco(c1, c2, manequim())
        if tem_bedrock and ('elmo', 'iron') in btex:
            b1 = np.asarray(ler_img(bz, btex[('elmo', 'iron')]))
            b2 = np.asarray(ler_img(bz, btex[('calca', 'iron')])) if ('calca', 'iron') in btex else None
            bimg = boneco(b1, b2, manequim())
        else:
            bimg = Image.new('RGB', jimg.size, (60, 20, 20))
        cel = Image.new('RGB', (jimg.width * 2 + 6, jimg.height + 16), (20, 21, 25))
        cel.paste(jimg, (0, 0))
        cel.paste(bimg, (jimg.width + 6, 0))
        ImageDraw.Draw(cel).text((4, jimg.height + 2), f'{tema}  Java | Bedrock', fill=(230, 230, 230))
        cels.append(cel)
    if cels:
        cols = min(6, len(cels))
        lin = (len(cels) + cols - 1) // cols
        w, h = cels[0].size
        out = Image.new('RGB', (cols * w, lin * h), (20, 21, 25))
        for i, c in enumerate(cels):
            out.paste(c, ((i % cols) * w, (i // cols) * h))
        out.save(saida)
    print(f'{len(temas)} temas no Java; {len(cels)} desenhados em {saida}')
    for p in problemas:
        print('PROBLEMA:', p)
    print('OK: Java e Bedrock iguais, sem problema' if not problemas else f'{len(problemas)} problema(s)')
    return 1 if problemas else 0


if __name__ == '__main__':
    sys.exit(main())


# ------------------------------------------------------------------ elmos 3D (geometria Bedrock)
def cubo_bedrock(r, tex, k, cubo):
    """Desenha um cubo de geometria Bedrock (frente -z) no Render (frente +z)."""
    ox, oy, oz = cubo['origin']
    w, h, d = cubo['size']
    inf = cubo.get('inflate', 0.0)
    uv = cubo['uv']
    # Bedrock (x, y, z) -> render (x, y, -z): a caixa vai de -(oz+d) a -oz
    org = (ox, oy, -(oz + d))
    if isinstance(uv, list):
        r.caixa(tex, k, tuple(uv), org, (w, h, d), inf)
        return
    x0, y0, z0 = org[0] - inf, org[1] - inf, org[2] - inf
    W, H, D = w + 2 * inf, h + 2 * inf, d + 2 * inf

    def rc(nome):
        f = uv[nome]
        u, v = f['uv']
        su, sv = f['uv_size']
        return (u * k, v * k, (u + su) * k, (v + sv) * k)
    r._face_rc(tex, rc('north'), (x0, y0 + H, z0 + D), (W, 0, 0), (0, -H, 0), 1.0)
    r._face_rc(tex, rc('up'), (x0, y0 + H, z0), (W, 0, 0), (0, 0, D), 1.12)
    r._face_rc(tex, rc('west'), (x0, y0 + H, z0), (0, 0, D), (0, -H, 0), 0.78)


def boneco_elmo3d(geo, tex, pele, c1=None, c2=None):
    r = Render(W=220, H=200, esc=7.0)
    P = np.asarray(pele)
    # so cabeca e ombros
    desloc = -18

    def org(o):
        return (o[0], o[1] + desloc, o[2])
    r.caixa(P, 1, (0, 0), org((-4, 24, -4)), (8, 8, 8))
    r.caixa(P, 1, (16, 16), org((-4, 12, -2)), (8, 12, 4))
    r.caixa(P, 1, (40, 16), org((-8, 12, -2)), (4, 12, 4))
    r.caixa(P, 1, (40, 16), org((4, 12, -2)), (4, 12, 4), espelho=True)
    if c1 is not None:
        k = c1.shape[1] // 64
        r.caixa(c1, k, (16, 16), org((-4, 12, -2)), (8, 12, 4), 1.0)
        r.caixa(c1, k, (40, 16), org((-8, 12, -2)), (4, 12, 4), 1.0)
        r.caixa(c1, k, (40, 16), org((4, 12, -2)), (4, 12, 4), 1.0, espelho=True)
    k = tex.shape[1] // 64
    for b in geo['minecraft:geometry'][0]['bones']:
        for c in b.get('cubes', []):
            c = dict(c)
            c['origin'] = (c['origin'][0], c['origin'][1] + desloc, c['origin'][2])
            cubo_bedrock(r, tex, k, c)
    return r.imagem()
