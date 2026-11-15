"""Roupas dos jogos de tiro (15/11, Paulo: "falta fazer roupas de exercito, entre outras de sobrevivencia para o modo de
zumbi, warface e horda"). Tres conjuntos de 4 pecas, pintados no molde de armadura do jogo (64x32, ampliado 4x):
  exercito      camuflado verde, capacete com faixa, colete tatico com bolsos, joelheira, coturno
  tatico        o mesmo corte em preto/cinza (forcas especiais), viseira escura
  sobrevivente  gorro, jaqueta de couro aberta sobre camiseta, mochila nas costas, jeans remendado, bota de trilha
Java   : equipment/u<conj>.json + textures/entity/equipment/humanoid[_leggings]/u<conj>.png + items/uniforme/<conj>_<peca>
Bedrock: textures/models/armor/corona_u<conj>_<1|2>.png + attachables + corona-tiro-uniformes.json (mapeamento do Geyser)
uso: python3 uniformes_tiro.py <saida>
"""
import json, os, sys
import numpy as np
from PIL import Image

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(AQUI, '..', 'tools'))
import zumbis_hd as Z  # noqa: E402  (ruido)

K = 4
PARTES = {'cabeca': (0, 0, 8, 8, 8), 'tronco': (16, 16, 8, 12, 4), 'braco': (40, 16, 4, 12, 4), 'perna': (0, 16, 4, 12, 4)}


def faces(u, v, w, h, d):
    return {'topo': (u + d, v, w, d), 'baixo': (u + d + w, v, w, d), 'dir': (u, v + d, d, h), 'frente': (u + d, v + d, w, h),
            'esq': (u + d + w, v + d, d, h), 'costas': (u + d + w + d, v + d, w, h)}


class Pano:
    """Uma face: grade em pixels do jogo (x, y de 0 a w/h), pintada em 4x."""

    def __init__(self, w, h, sem):
        self.w, self.h, self.sem = w, h, sem
        ys, xs = np.mgrid[0:h * K, 0:w * K]
        self.x, self.y = (xs + 0.5) / K, (ys + 0.5) / K
        self.rgb = np.zeros((h * K, w * K, 3)); self.a = np.zeros((h * K, w * K))

    def ret(self, x0, y0, x1, y1):
        return (self.x >= x0) & (self.x < x1) & (self.y >= y0) & (self.y < y1)

    def pintar(self, m, cor, grao=0.10):
        g = 1 + (Z.fbm(self.x * 2.3 + self.sem, self.y * 2.3, self.sem, 3) - 0.5) * 2 * grao
        self.rgb[m] = (np.array(cor)[None, :] * g[m][:, None]); self.a[m] = 1

    def camuflado(self, m, cores, esc=0.55):
        n = Z.fbm(self.x * esc + self.sem * 1.7, self.y * esc + 3.1, self.sem + 5, 2)
        n2 = Z.fbm(self.x * esc * 1.9 + 9.2, self.y * esc * 1.9 + self.sem, self.sem + 11, 2)
        idx = np.digitize(n * 0.7 + n2 * 0.3, np.linspace(0.36, 0.64, len(cores) - 1))
        for i, c in enumerate(cores): self.pintar(m & (idx == i), c, 0.06)

    def escurecer(self, m, f): self.rgb[m] *= f

    def apagar(self, m): self.a[m] = 0

    def borda(self, m, f=0.72):
        # contorno escuro de 1 subpixel em volta do desenho (da relevo)
        a = m.astype(float); viz = np.zeros_like(a)
        viz[1:, :] += a[:-1, :]; viz[:-1, :] += a[1:, :]; viz[:, 1:] += a[:, :-1]; viz[:, :-1] += a[:, 1:]
        self.rgb[m & (viz < 4)] *= f

    def img(self):
        o = np.zeros((self.h * K, self.w * K, 4), np.uint8)
        o[..., :3] = np.clip(self.rgb, 0, 255); o[..., 3] = (self.a > 0.5) * 255
        return o


CAMO = {'exercito': [(62, 78, 44), (84, 100, 56), (48, 58, 36), (104, 96, 62)], 'tatico': [(30, 32, 36), (44, 46, 52), (22, 24, 26), (58, 60, 66)]}
COLETE = {'exercito': (58, 62, 40), 'tatico': (20, 21, 24)}
BOLSO = {'exercito': (74, 78, 50), 'tatico': (38, 40, 46)}


def militar(conj, parte, face, w, h, camada, sem):
    f = Pano(w, h, sem); tudo = f.ret(0, 0, w, h); cam = CAMO[conj]; col = COLETE[conj]; bol = BOLSO[conj]
    preto = (16, 16, 18)
    if camada == 1 and parte == 'cabeca':
        if face == 'topo': f.camuflado(tudo, cam); f.pintar(f.ret(0, 3.5, w, 4.5), col)
        elif face == 'baixo': pass
        else:
            f.camuflado(f.ret(0, 0, w, 3.2 if face == 'frente' else 4.4), cam)
            f.pintar(f.ret(0, 2.4 if face == 'frente' else 3.6, w, 3.2 if face == 'frente' else 4.4), col)      # aba do capacete
            if face == 'frente' and conj == 'tatico':
                f.pintar(f.ret(1, 3.2, 7, 5.4), (24, 30, 38)); f.pintar(f.ret(1.5, 3.6, 4, 4.2), (70, 92, 110))        # viseira
            if face in ('dir', 'esq'): f.pintar(f.ret(3.4, 4.4, 4.4, 8), preto)                                    # jugular
    elif camada == 1 and parte == 'tronco':
        if face in ('topo', 'baixo'): f.camuflado(tudo, cam)
        else:
            f.camuflado(tudo, cam)
            if face in ('frente', 'costas'):
                c = f.ret(0.6, 1.2, w - 0.6, 9.4); f.pintar(c, col); f.borda(c)
                f.pintar(f.ret(1.2, 0, 2.6, 1.6), col); f.pintar(f.ret(w - 2.6, 0, w - 1.2, 1.6), col)             # alcas
                if face == 'frente':
                    for x0 in (1.0, 3.2, 5.4):
                        b = f.ret(x0, 5.6, x0 + 1.7, 8.6); f.pintar(b, bol); f.borda(b, 0.65); f.pintar(f.ret(x0, 5.6, x0 + 1.7, 6.2), col)
                    f.pintar(f.ret(1.2, 2.2, 3.4, 3.4), bol); f.pintar(f.ret(4.8, 2.0, 6.6, 2.5), (150, 150, 140))   # radio e nome
                else:
                    m = f.ret(1.6, 2.4, w - 1.6, 8.2); f.pintar(m, bol); f.borda(m, 0.65)                           # placa das costas
                f.pintar(f.ret(0, 10.2, w, 11.2), (40, 34, 24)); f.pintar(f.ret(3.4, 10.1, 4.6, 11.3), (150, 140, 90)) if face == 'frente' else None
            else: f.pintar(f.ret(0.6, 3, w - 0.6, 9.4), col)
    elif camada == 1 and parte == 'braco':
        if face == 'baixo': f.pintar(tudo, preto)
        else:
            f.camuflado(tudo, cam)
            if face not in ('topo',):
                c = f.ret(0, 5.2, w, 7.0); f.pintar(c, col); f.borda(c)                                              # cotoveleira
                f.pintar(f.ret(0, 10, w, 12), preto); f.pintar(f.ret(0, 9.6, w, 10), bol)                           # luva
                if face == 'dir': f.pintar(f.ret(0.8, 1.2, 3.2, 3.0), (150, 30, 30) if conj == 'exercito' else (200, 200, 200))
    elif camada == 1 and parte == 'perna':                                                                           # coturno
        if face == 'baixo': f.pintar(tudo, preto)
        elif face != 'topo':
            b = f.ret(0, 7.6, w, 12); f.pintar(b, (26, 24, 22)); f.borda(b)
            f.pintar(f.ret(0, 11.2, w, 12), (12, 12, 12))
            if face == 'frente':
                for yy in (8.3, 9.2, 10.1): f.pintar(f.ret(1.2, yy, 2.8, yy + 0.3), (120, 110, 90))
    elif camada == 2 and parte == 'perna':
        if face in ('topo', 'baixo'): f.camuflado(tudo, cam)
        else:
            f.camuflado(tudo, cam)
            if face == 'frente':
                j = f.ret(0.5, 4.6, w - 0.5, 6.8); f.pintar(j, col); f.borda(j)
            if face in ('dir', 'esq'):
                b = f.ret(0.6, 1.6, w - 0.6, 4.0); f.pintar(b, bol); f.borda(b, 0.65)                               # bolso cargo
    elif camada == 2 and parte == 'tronco':
        if face not in ('topo', 'baixo'):
            f.camuflado(f.ret(0, 7, w, 12), cam); f.pintar(f.ret(0, 9.6, w, 10.8), (40, 34, 24))
    return f.img()


def sobrevivente(parte, face, w, h, camada, sem):
    f = Pano(w, h, sem); tudo = f.ret(0, 0, w, h)
    couro, couro2, camisa, jeans, jeans2 = (96, 62, 36), (70, 44, 26), (150, 146, 132), (58, 78, 112), (44, 60, 90)
    lona, bota = (88, 96, 68), (74, 52, 34)
    if camada == 1 and parte == 'cabeca':
        gorro = (120, 40, 36)
        if face == 'topo': f.pintar(tudo, gorro, 0.16)
        elif face != 'baixo':
            f.pintar(f.ret(0, 0, w, 2.6), gorro, 0.16); f.pintar(f.ret(0, 2.0, w, 2.9), (150, 60, 52), 0.2)       # dobra do gorro
            if face != 'frente': f.pintar(f.ret(0, 6.6, w, 8), (70, 78, 60), 0.2)                                    # lenco no pescoco
            else: f.pintar(f.ret(0, 7.2, w, 8), (70, 78, 60), 0.2)
    elif camada == 1 and parte == 'tronco':
        if face in ('topo', 'baixo'): f.pintar(tudo, couro)
        elif face == 'frente':
            f.pintar(tudo, couro, 0.14)
            f.pintar(f.ret(2.8, 0, 5.2, 12), camisa, 0.12); f.pintar(f.ret(2.4, 0, 2.8, 12), couro2); f.pintar(f.ret(5.2, 0, 5.6, 12), couro2)
            f.pintar(f.ret(1.0, 0, 2.0, 12), lona); f.pintar(f.ret(6.0, 0, 7.0, 12), lona)                           # alcas da mochila
            f.pintar(f.ret(1.0, 5.4, 7.0, 6.2), lona); f.pintar(f.ret(3.6, 5.3, 4.4, 6.3), (60, 60, 60))             # tira do peito
            b = f.ret(0.6, 7.6, 2.4, 9.6); f.pintar(b, couro2); f.borda(b)
            f.pintar(f.ret(3.2, 3.0, 4.8, 4.6), (120, 26, 22), 0.3)                                                   # mancha na camiseta
        elif face == 'costas':
            f.pintar(tudo, couro, 0.14)
            m = f.ret(1.0, 1.0, 7.0, 10.0); f.pintar(m, lona, 0.14); f.borda(m, 0.62)
            b = f.ret(2.0, 5.6, 6.0, 9.0); f.pintar(b, (72, 80, 54)); f.borda(b, 0.7); f.pintar(f.ret(2.0, 5.6, 6.0, 6.3), (60, 60, 60))
            f.pintar(f.ret(1.6, 0.2, 6.4, 1.2), (140, 120, 90), 0.2)                                                  # saco de dormir enrolado
        else: f.pintar(tudo, couro, 0.14); f.pintar(f.ret(1.2, 2, 2.8, 10), lona)
    elif camada == 1 and parte == 'braco':
        if face == 'baixo': f.pintar(tudo, (50, 44, 38))
        else:
            f.pintar(tudo, couro, 0.14)
            if face != 'topo':
                f.pintar(f.ret(0, 9.4, w, 10.2), couro2)
                f.pintar(f.ret(0, 10.2, w, 12), (50, 44, 38), 0.2)                                                    # luva sem dedo
                if face in ('frente', 'esq'): f.pintar(f.ret(0, 4.4, w, 6.2), (214, 206, 186), 0.12); f.pintar(f.ret(1, 4.8, 2.4, 5.6), (150, 30, 26), 0.3)  # atadura
    elif camada == 1 and parte == 'perna':
        if face == 'baixo': f.pintar(tudo, (30, 24, 18))
        elif face != 'topo':
            b = f.ret(0, 8.2, w, 12); f.pintar(b, bota, 0.16); f.borda(b); f.pintar(f.ret(0, 11.2, w, 12), (30, 24, 18))
            f.pintar(f.ret(0, 8.2, w, 8.9), (120, 112, 96), 0.2)                                                      # meia aparecendo
    elif camada == 2 and parte == 'perna':
        if face in ('topo', 'baixo'): f.pintar(tudo, jeans)
        else:
            f.pintar(tudo, jeans, 0.16)
            f.escurecer(f.ret(0, 9, w, 12), 0.86)
            if face == 'frente':
                r = f.ret(0.8, 4.4, 3.0, 6.4); f.pintar(r, (88, 96, 68), 0.2); f.borda(r, 0.6)                        # remendo no joelho
            if face == 'dir':
                c = f.ret(0.6, 1.4, 3.4, 4.6); f.pintar(c, (60, 44, 30)); f.borda(c)                                  # coldre
    elif camada == 2 and parte == 'tronco':
        if face not in ('topo', 'baixo'):
            f.pintar(f.ret(0, 7.4, w, 12), jeans2); f.pintar(f.ret(0, 9.4, w, 10.6), (60, 44, 30)); f.pintar(f.ret(3.4, 9.3, 4.6, 10.7), (170, 160, 120)) if face == 'frente' else None
    return f.img()


def camada(conj, n):
    img = np.zeros((32 * K, 64 * K, 4), np.uint8)
    for i, (parte, (u, v, w, h, d)) in enumerate(PARTES.items()):
        for j, (face, (x, y, fw, fh)) in enumerate(faces(u, v, w, h, d).items()):
            sem = 17 + i * 13 + j * 5 + n * 3
            t = sobrevivente(parte, face, fw, fh, n, sem) if conj == 'sobrevivente' else militar(conj, parte, face, fw, fh, n, sem)
            img[y * K:(y + fh) * K, x * K:(x + fw) * K] = t
    return img


def salvar(caminho, obj):
    os.makedirs(os.path.dirname(caminho), exist_ok=True)
    json.dump(obj, open(caminho, 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))


CONJ = {'exercito': ('Exercito', 0x4E5F38), 'tatico': ('Tatico', 0x26282C), 'sobrevivente': ('Sobrevivente', 0x603E24)}
PECAS = {'helmet': ('elmo', 1, 'helmet_layer_visible', 2), 'chestplate': ('peitoral', 1, 'chest_layer_visible', 6),
         'leggings': ('calca', 2, 'leg_layer_visible', 5), 'boots': ('botas', 1, 'boot_layer_visible', 2)}
ICONE = {'helmet': ["..xxxxxxxxxx..", ".xxxxxxxxxxxx.", ".xxxxxxxxxxxx.", ".xxxxxxxxxxxx.", ".xxx......xxx.", ".xxx......xxx."],
         'chestplate': ["xxx.xxxxxx.xxx", "xxxxxxxxxxxxxx", "xxxxxxxxxxxxxx", ".xxxxxxxxxxxx.", "..xxxxxxxxxx..", "..xxxxxxxxxx..", "..xxxxxxxxxx..", "..xxxxxxxxxx.."],
         'leggings': ["..xxxxxxxxxx..", "..xxxxxxxxxx..", "..xxxx..xxxx..", "..xxxx..xxxx..", "..xxxx..xxxx..", "..xxxx..xxxx..", "..xxxx..xxxx.."],
         'boots': ["..xxx....xxx..", "..xxx....xxx..", "..xxx....xxx..", ".xxxx....xxxx.", "xxxxx....xxxxx"]}

if __name__ == '__main__':
    import render_armaduras as RA
    SAIDA = sys.argv[1]
    J = os.path.join(SAIDA, 'java', 'assets', 'corona'); B = os.path.join(SAIDA, 'bedrock')
    mapa, item_tex, previas = {}, {}, []
    for conj, (nome, cor) in CONJ.items():
        g1, g2 = camada(conj, 1), camada(conj, 2)
        for n, pasta, g in ((1, 'humanoid', g1), (2, 'humanoid_leggings', g2)):
            for p in (os.path.join(J, 'textures', 'entity', 'equipment', pasta, f'u{conj}.png'), os.path.join(B, 'textures', 'models', 'armor', f'corona_u{conj}_{n}.png')):
                os.makedirs(os.path.dirname(p), exist_ok=True); Image.fromarray(g, 'RGBA').save(p, optimize=True)
        salvar(os.path.join(J, 'equipment', f'u{conj}.json'), {'layers': {'humanoid': [{'texture': f'corona:u{conj}'}], 'humanoid_leggings': [{'texture': f'corona:u{conj}'}]}})
        rgb = ((cor >> 16) & 255, (cor >> 8) & 255, cor & 255)
        for peca, (pt, n, var, prot) in PECAS.items():
            salvar(os.path.join(J, 'items', 'uniforme', f'{conj}_{pt}.json'), {'model': {'type': 'minecraft:model', 'model': f'minecraft:item/leather_{peca}',
                   'tints': [{'type': 'minecraft:dye', 'default': cor - (1 << 24)}]}})
            ic = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); linhas = ICONE[peca]; y0 = (16 - len(linhas)) // 2
            for yy, l in enumerate(linhas):
                for xx, ch in enumerate(l):
                    if ch == 'x':
                        borda = yy == 0 or yy == len(linhas) - 1 or l[max(0, xx - 1)] == '.' or xx in (0, len(l) - 1) or l[min(len(l) - 1, xx + 1)] == '.'
                        ic.putpixel((xx + 1, yy + y0), tuple(int(c * (0.6 if borda else 1.0)) for c in rgb) + (255,))
            d = os.path.join(B, 'textures', 'items', 'corona', 'tiro'); os.makedirs(d, exist_ok=True)
            ic.save(os.path.join(d, f'corona_u{conj}_{pt}.png'))
            item_tex[f'corona_u{conj}_{pt}'] = {'textures': f'textures/items/corona/tiro/corona_u{conj}_{pt}'}
            bid = f'corona:uniforme_{conj}_{pt}'
            salvar(os.path.join(B, 'attachables', f'uniforme_{conj}_{pt}.json'), {"format_version": "1.10.0", "minecraft:attachable": {"description": {
                "identifier": bid, "materials": {"default": "armor", "enchanted": "armor_enchanted"},
                "textures": {"default": f'textures/models/armor/corona_u{conj}_{n}', "enchanted": "textures/misc/enchanted_actor_glint"},
                "geometry": {"default": f'geometry.player.armor.{peca}'}, "scripts": {"parent_setup": f"variable.{var} = 0.0;"},
                "render_controllers": ["controller.render.armor"]}}})
            mapa.setdefault(f'minecraft:leather_{peca}', []).append({'type': 'definition', 'model': f'corona:uniforme/{conj}_{pt}', 'bedrock_identifier': bid,
                'display_name': f'{nome} {pt}', 'bedrock_options': {'icon': f'corona_u{conj}_{pt}', 'creative_category': 'equipment', 'protection_value': prot}})
        for yaw in (30, 210):
            r = RA.Render(400, 600, 13.0, yaw, 12); k = K
            pele = RA.manequim()
            r.caixa(g2, k, (16, 16), (-4, 12, -2), (8, 12, 4), 0.5)
            r.caixa(g2, k, (0, 16), (-4, 0, -2), (4, 12, 4), 0.5); r.caixa(g2, k, (0, 16), (0, 0, -2), (4, 12, 4), 0.5, espelho=True)
            r.caixa(g1, k, (0, 16), (-4, 0, -2), (4, 12, 4), 1.0); r.caixa(g1, k, (0, 16), (0, 0, -2), (4, 12, 4), 1.0, espelho=True)
            r.caixa(g1, k, (16, 16), (-4, 12, -2), (8, 12, 4), 1.0)
            r.caixa(g1, k, (40, 16), (-8, 12, -2), (4, 12, 4), 1.0); r.caixa(g1, k, (40, 16), (4, 12, -2), (4, 12, 4), 1.0, espelho=True)
            r.caixa(g1, k, (0, 0), (-4, 24, -4), (8, 8, 8), 1.0)
            previas.append(r.imagem())
    salvar(os.path.join(SAIDA, 'corona-tiro-uniformes.json'), {'format_version': 2, 'items': mapa})
    salvar(os.path.join(SAIDA, 'item_texture_uniformes.json'), item_tex)
    folha = Image.new('RGB', (400 * 6, 600), (150, 158, 168))
    for i, pv in enumerate(previas): folha.paste(pv, (i * 400, 0))
    folha.save(os.path.join(SAIDA, 'previa_uniformes.png'))
    print('conjuntos:', len(CONJ))
