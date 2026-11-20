"""Espadas do Arsenal Ancestral (nivel 6) - 20/11, Paulo: "voce ta imitando as anteriores, crie algo diferente".

Formato proprio por masmorra (nenhuma usa as laminas em 3 segmentos das reliquias antigas):
  CINZAS flamberge ondulada com brasas, CATACUMBA lamina vazada (duas hastes com travessas), PINACULO raio em
  zigue-zague, RUBRA rapieira fina com guarda em cesto, ECO diapasao (duas pontas com o canal aceso no meio),
  PUTRIDO lamina com bulbos e esporos, GLACIAR agulha de cristal em facetas, ABISSAL lamina com ganchos de ancora,
  TITAS montante largo com nucleos em colmeia, CAOS lamina partida com estilhacos flutuando.
Material comum do arsenal: metal quase preto do tema, frisos dourados, canal de energia aceso (emite luz).
Mesmos arquivos das reliquias (corona:item/reliquias/espada_<tema>) e icone 2D do Bedrock: o plugin nao muda.
Uso: python3 -I espadas_n6.py [ids...]
"""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import reliquias as R  # noqa: E402
import kit  # noqa: E402
from reliquias import sw, TEMAS, JAVA, BEDROCK, PREV  # noqa: E402
from base import fbm, rampa, mix, C, ss, hash2  # noqa: E402
from armaduras_n6 import N6  # noqa: E402


def base_comum(cs, guarda='padrao'):
    cs.append(sw('pomo', -1.2, 0.3, 1.2, 1.5, 'pomo', 1.7, emite=8))
    cs.append(sw('cabo', -0.7, 1.5, 0.7, 5.4, 'cabo', 1.4))
    if guarda == 'cesto':
        cs.append(sw('guarda', -2.0, 5.4, 2.0, 6.4, 'guarda', 1.6))
        cs.append(sw('cesto1', -3.0, 2.2, -2.2, 6.0, 'friso', 1.0))
        cs.append(sw('cesto2', 2.2, 2.2, 3.0, 6.0, 'friso', 1.0))
        cs.append(sw('cesto3', -3.0, 1.6, 3.0, 2.3, 'friso', 1.0))
    elif guarda == 'pistao':
        cs.append(sw('guarda', -4.6, 5.4, 4.6, 7.0, 'guarda', 2.1))
        cs.append(sw('pist1', -4.6, 3.6, -3.4, 5.4, 'friso', 1.4))
        cs.append(sw('pist2', 3.4, 3.6, 4.6, 5.4, 'friso', 1.4))
        cs.append(sw('nucleo', -0.8, 5.6, 0.8, 6.8, 'gema', 2.3, emite=15))
    elif guarda == 'chifre':
        cs.append(sw('guarda', -2.6, 5.4, 2.6, 6.6, 'guarda', 1.7))
        cs.append(sw('chif1', -3.8, 6.0, -2.6, 7.0, 'guarda', 1.3))
        cs.append(sw('chif2', -4.4, 7.0, -3.4, 8.6, 'friso', 1.1))
        cs.append(sw('chif3', 2.6, 6.0, 3.8, 7.0, 'guarda', 1.3))
        cs.append(sw('chif4', 3.4, 7.0, 4.4, 8.6, 'friso', 1.1))
    else:
        cs.append(sw('guarda', -3.2, 5.4, 3.2, 6.6, 'guarda', 1.8))
        cs.append(sw('guarda_f', -3.4, 5.9, 3.4, 6.1, 'friso', 1.9))
    cs.append(sw('gema', -0.6, 5.6, 0.6, 6.4, 'gema', 2.0, emite=15))


def espada_cubos(id_):
    cs = []
    if id_ == 'CINZAS':          # flamberge: lamina ondulada, brasas soltas
        base_comum(cs)
        y = 6.6
        for k in range(8):
            dx = 0.55 if k % 2 == 0 else -0.55
            w = 1.25 - k * 0.06
            cs.append(sw(f'l{k}', -w + dx, y, w + dx, y + 1.8, 'lamina', 0.8))
            cs.append(sw(f'v{k}', -0.28 + dx, y, 0.28 + dx, y + 1.8, 'veio', 0.92, emite=15))
            y += 1.8
        cs.append(sw('ponta', -0.5, y, 0.5, y + 1.4, 'lamina', 0.7))
        for j, (xx, yy) in enumerate(((-2.6, 10.0), (2.4, 13.5), (-2.3, 17.0), (2.2, 19.6))):
            cs.append(sw(f'brasa{j}', xx - 0.4, yy, xx + 0.4, yy + 0.8, 'veio', 0.4, emite=15))
    elif id_ == 'CATACUMBA':     # vazada: duas hastes com travessas e runas no vao
        base_comum(cs, 'chifre')
        cs.append(sw('h1', -1.6, 6.6, -0.9, 20.0, 'lamina', 0.8))
        cs.append(sw('h2', 0.9, 6.6, 1.6, 20.0, 'lamina', 0.8))
        for k in range(5):
            yy = 8.2 + k * 2.6
            cs.append(sw(f't{k}', -0.9, yy, 0.9, yy + 0.5, 'friso', 0.7))
            cs.append(sw(f'r{k}', -0.45, yy + 0.8, 0.45, yy + 1.8, 'veio', 0.4, emite=15))
        cs.append(sw('ponta', -1.6, 20.0, 1.6, 21.0, 'lamina', 0.8))
        cs.append(sw('ponta2', -0.6, 21.0, 0.6, 22.4, 'lamina', 0.7))
    elif id_ == 'PINACULO':      # raio em zigue-zague
        base_comum(cs)
        segs = [(0.0, 3.0), (1.1, 2.6), (-0.4, 2.6), (1.4, 2.4), (0.0, 2.4), (1.2, 2.0)]
        y = 6.6
        for k, (dx, h) in enumerate(segs):
            cs.append(sw(f'l{k}', -1.0 + dx, y, 1.0 + dx, y + h, 'lamina', 0.75))
            cs.append(sw(f'v{k}', -0.3 + dx, y, 0.3 + dx, y + h, 'veio', 0.88, emite=15))
            y += h - 0.2
        cs.append(sw('ponta', 0.8, y, 1.6, y + 1.6, 'veio', 0.6, emite=15))
        cs.append(sw('vara1', -3.6, 6.0, -3.0, 9.4, 'friso', 0.8))
        cs.append(sw('vara2', 3.0, 6.0, 3.6, 9.4, 'friso', 0.8))
    elif id_ == 'RUBRA':         # rapieira: lamina fina e comprida, guarda em cesto
        base_comum(cs, 'cesto')
        cs.append(sw('l', -0.55, 6.4, 0.55, 21.6, 'lamina', 0.6))
        cs.append(sw('v', -0.18, 7.0, 0.18, 20.6, 'veio', 0.7, emite=15))
        cs.append(sw('ponta', -0.3, 21.6, 0.3, 23.0, 'lamina', 0.5))
        for k in range(3):
            yy = 9 + k * 4
            cs.append(sw(f'gota{k}', 0.55, yy, 1.0, yy + 0.9, 'veio', 0.4, emite=15))
    elif id_ == 'ECO':           # diapasao: duas pontas e o canal aceso no meio
        base_comum(cs)
        cs.append(sw('raiz', -1.8, 6.6, 1.8, 9.0, 'lamina', 0.9))
        cs.append(sw('p1', -1.8, 9.0, -0.8, 20.4, 'lamina', 0.85))
        cs.append(sw('p2', 0.8, 9.0, 1.8, 20.4, 'lamina', 0.85))
        cs.append(sw('canal', -0.35, 8.0, 0.35, 19.6, 'veio', 0.5, emite=15))
        for k in range(4):
            yy = 10.5 + k * 2.4
            cs.append(sw(f'onda{k}', -0.8, yy, 0.8, yy + 0.35, 'veio', 0.3, emite=15))
        cs.append(sw('t1', -1.6, 20.4, -1.0, 21.6, 'friso', 0.7))
        cs.append(sw('t2', 1.0, 20.4, 1.6, 21.6, 'friso', 0.7))
    elif id_ == 'PUTRIDO':       # bulbos e esporos
        base_comum(cs, 'chifre')
        y = 6.6
        for k, (w, h) in enumerate(((1.3, 3.0), (1.1, 3.0), (1.25, 3.0), (1.0, 3.0), (0.8, 2.4))):
            cs.append(sw(f'l{k}', -w, y, w, y + h, 'lamina', 0.8))
            y += h
        cs.append(sw('v', -0.3, 7.0, 0.3, y - 0.6, 'veio', 0.9, emite=15))
        cs.append(sw('ponta', -0.45, y, 0.45, y + 1.4, 'lamina', 0.7))
        for j, (xx, yy, r) in enumerate(((-1.9, 8.6, 0.7), (1.8, 11.2, 0.9), (-1.6, 14.4, 0.6), (1.5, 16.8, 0.55))):
            cs.append(sw(f'bulbo{j}', xx - r, yy, xx + r, yy + r * 1.8, 'gema', 1.2, emite=12))
    elif id_ == 'GLACIAR':       # agulha de cristal em facetas
        base_comum(cs)
        y = 6.6
        for k in range(7):
            w = 1.7 - k * 0.2
            cs.append(sw(f'l{k}', -w, y, w, y + 2.0, 'cristal', 1.0 - k * 0.06))
            cs.append(sw(f'f{k}', -w * 0.4, y + 0.3, w * 0.4, y + 1.7, 'veio', 1.1 - k * 0.06, emite=12))
            y += 2.0
        cs.append(sw('ponta', -0.3, y, 0.3, y + 1.6, 'cristal', 0.5))
        for j, (xx, yy) in enumerate(((-2.4, 9.0), (2.2, 11.6), (-1.9, 14.6))):
            cs.append(sw(f'estilhaco{j}', xx - 0.35, yy, xx + 0.35, yy + 1.4, 'cristal', 0.5))
    elif id_ == 'ABISSAL':       # ganchos de ancora num lado, ponta curva
        base_comum(cs)
        cs.append(sw('l', -1.2, 6.6, 1.2, 19.2, 'lamina', 0.8))
        cs.append(sw('v', -0.3, 7.0, 0.3, 18.8, 'veio', 0.92, emite=15))
        for k in range(3):
            yy = 9.0 + k * 3.6
            cs.append(sw(f'gancho{k}', -2.4, yy, -1.2, yy + 0.8, 'lamina', 0.7))
            cs.append(sw(f'farpa{k}', -2.4, yy - 1.0, -1.7, yy, 'friso', 0.6))
        cs.append(sw('curva1', -0.6, 19.2, 1.6, 20.6, 'lamina', 0.75))
        cs.append(sw('curva2', 1.0, 20.6, 2.2, 22.0, 'lamina', 0.7))
        cs.append(sw('bolha', 1.4, 14.0, 2.0, 14.6, 'veio', 0.4, emite=15))
    elif id_ == 'TITAS':         # montante largo com nucleos em colmeia
        base_comum(cs, 'pistao')
        cs.append(sw('l', -2.2, 7.0, 2.2, 19.0, 'lamina', 1.0))
        cs.append(sw('borda1', -2.5, 7.0, -2.2, 19.0, 'friso', 1.05))
        cs.append(sw('borda2', 2.2, 7.0, 2.5, 19.0, 'friso', 1.05))
        for k, (xx, yy) in enumerate(((0, 8.4), (-1.0, 10.4), (1.0, 10.4), (0, 12.4), (-1.0, 14.4), (1.0, 14.4), (0, 16.4))):
            cs.append(sw(f'hex{k}', xx - 0.55, yy, xx + 0.55, yy + 1.1, 'veio', 1.12, emite=15))
        cs.append(sw('ponta', -1.6, 19.0, 1.6, 20.4, 'lamina', 0.9))
        cs.append(sw('ponta2', -0.7, 20.4, 0.7, 21.6, 'lamina', 0.8))
    else:                        # CAOS: lamina partida com estilhacos flutuando
        base_comum(cs, 'chifre')
        cs.append(sw('a1', -1.4, 6.6, -0.25, 15.0, 'lamina', 0.8))
        cs.append(sw('a2', 0.25, 6.6, 1.4, 13.4, 'lamina', 0.8))
        cs.append(sw('vao', -0.25, 7.0, 0.25, 14.4, 'veio', 0.5, emite=15))
        for k, (xx, yy, w) in enumerate(((0.9, 14.4, 0.6), (-0.6, 16.0, 0.7), (0.5, 17.6, 0.55), (-0.2, 19.4, 0.45), (0.3, 21.0, 0.3))):
            cs.append(sw(f'caco{k}', xx - w, yy, xx + w, yy + 1.0, 'lamina', 0.7))
            cs.append(sw(f'brilho{k}', xx - w * 0.4, yy + 0.2, xx + w * 0.4, yy + 0.8, 'veio', 0.8, emite=15))
    return cs


def pintor(id_):
    padrao, _, base, veio, friso = N6[id_]
    b, v, f = np.asarray(C(base)), np.asarray(C(veio)), np.asarray(C(friso))
    sem = sum(ord(ch) for ch in id_) % 997

    def p(P):
        x, y, z, s, tt = P['x'], P['y'], P['z'], P['s'], P['t']
        pele = P['pele']
        n = fbm(x * 2.2, y * 2.2, z * 2.2, 3, 1, sem)
        if pele == 'cabo':
            faixa = 0.5 + 0.5 * np.sin(y * 3.2 + s * 2.0)
            c = rampa(faixa, [(0, R.escurecer(b, 0.8)), (1, R.clarear(b, 0.15))])
            c = mix(c, v, ss(0.85, 0.97, faixa) * 0.8)
            return c, 1
        if pele == 'pomo':
            d = np.hypot(s - 0.5, tt - 0.5)
            c = mix(b * 1.6, f, ss(0.3, 0.45, d))
            return mix(c, R.clarear(v, 0.4), ss(0.22, 0.0, d)), 1
        if pele in ('guarda', 'friso'):
            if pele == 'friso':
                return rampa(n, [(0, R.escurecer(f, 0.6)), (0.6, f), (1, R.clarear(f, 0.5))]), 1
            c = rampa(n, [(0, R.escurecer(b, 0.7)), (1, R.clarear(b, 0.25))]) * 1.5
            borda = np.minimum(np.minimum(s, 1 - s), np.minimum(tt, 1 - tt))
            return np.clip(mix(c, f, (borda < 0.14) * 0.9), 0, 1), 1
        if pele == 'gema':
            d = np.hypot(s - 0.5, tt - 0.5)
            return np.clip(mix(R.clarear(v, 0.6), v, ss(0.0, 0.5, d)), 0, 1), 1
        if pele == 'veio':
            pulso = 0.5 + 0.5 * np.sin(y * 1.7 + n * 3)
            return np.clip(mix(v, R.clarear(v, 0.7), pulso * 0.7), 0, 1), 1
        if pele == 'cristal':
            c = rampa(n, [(0, mix(b, v, 0.3) * 1.6), (0.6, mix(v, C('#ffffff'), 0.3)), (1, C('#ffffff'))])
            faceta = (np.abs(s - tt) < 0.06) | (np.abs(s + tt - 1) < 0.06)
            return np.clip(np.where(faceta[..., None], np.ones_like(c), c), 0, 1), 1
        # lamina: metal escuro do tema, fio claro dourado e veios finos do padrao
        c = rampa(n, [(0, b * 1.4), (0.5, b * 2.4), (1, R.clarear(b * 2.4, 0.3))])
        fio = np.minimum(s, 1 - s) < 0.1
        c = np.where(fio[..., None], mix(c, f, 0.75), c)
        trinca = ss(0.86, 0.95, fbm(x * 3, y * 1.2, z * 3, 2, 1, sem + 9))
        c = mix(c, v, trinca * 0.85)
        return np.clip(c, 0, 1), 1
    return p


def gerar(id_):
    tema = id_.lower()
    cs = espada_cubos(id_)
    reps = list(cs)
    largura = 128
    altura = kit.arrumar_uv(reps, largura, 4)
    H = 16
    while H < altura:
        H *= 2
    atlas = kit.pintar_atlas(reps, largura, H, pintor(id_), 4)
    tex_dir = os.path.join(JAVA, 'textures', 'item', 'reliquias')
    os.makedirs(tex_dir, exist_ok=True)
    kit.salvar_png_leve(atlas, os.path.join(tex_dir, f'espada_{tema}.png'))
    modelo = kit.modelo_java(cs, f'corona:item/reliquias/espada_{tema}', largura, H, display=R.DISPLAY)
    kit.salvar_json(os.path.join(JAVA, 'models', 'item', 'reliquias', f'espada_{tema}.json'), modelo)
    kit.salvar_json(os.path.join(JAVA, 'items', 'item', 'reliquias', f'espada_{tema}.json'), kit.item_java(f'corona:item/reliquias/espada_{tema}'))
    pv = kit.previa(cs, atlas, 0, 0, escala=10)
    pv.save(os.path.join(PREV, f'espada_{tema}.png'))
    arr = np.array(pv.convert('RGBA'))
    fundo = (np.abs(arr[..., 0].astype(int) - 28) < 3) & (np.abs(arr[..., 1].astype(int) - 30) < 3) & (np.abs(arr[..., 2].astype(int) - 36) < 3)
    arr[fundo, 3] = 0
    ic = Image.fromarray(arr, 'RGBA')
    ic.thumbnail((32, 32), Image.LANCZOS)
    quad = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    quad.paste(ic, ((32 - ic.width) // 2, (32 - ic.height) // 2))
    bd = os.path.join(BEDROCK, 'textures', 'items', 'reliquias')
    os.makedirs(bd, exist_ok=True)
    quad.save(os.path.join(bd, f'espada_{tema}.png'))
    return pv


if __name__ == '__main__':
    so = [s.upper() for s in sys.argv[1:]] or list(N6)
    for id_ in so:
        gerar(id_)
    print(len(so), 'espadas do Arsenal Ancestral; previas em', PREV)
