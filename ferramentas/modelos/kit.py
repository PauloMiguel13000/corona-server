"""Kit de modelos 3D para o pacote de recursos (monstros proprios, sem boneco de jogador).

Um monstro e uma lista de Cubos (unidades de modelo: 16 = 1 bloco; x direita, y cima, z frente/sul).
A partir da mesma lista saem:
  - o modelo Java (JSON de item com "elements"), um arquivo por quadro de animacao;
  - a geometria Bedrock (geo.json, format 1.16.0) com os mesmos cubos;
  - a textura (atlas) pintada por uma funcao que recebe pontos 3D da superficie (ruido continuo entre as faces);
  - previas (frente, lado, costas, 3/4) para conferir.
Limites do Java: cada cubo entre -16 e 32 em cada eixo; rotacao so num eixo, angulos -45, -22.5, 0, 22.5, 45.
"""
import json, math, os
import numpy as np
from PIL import Image, ImageDraw

ANGULOS = (-45.0, -22.5, 0.0, 22.5, 45.0)
# face -> (normal, eixos (u, v) no espaco do modelo); v cresce para baixo na textura
FACES = {
    'north': ((0, 0, -1), ((-1, 0, 0), (0, -1, 0))),
    'south': ((0, 0, 1), ((1, 0, 0), (0, -1, 0))),
    'east': ((1, 0, 0), ((0, 0, -1), (0, -1, 0))),
    'west': ((-1, 0, 0), ((0, 0, 1), (0, -1, 0))),
    'up': ((0, 1, 0), ((1, 0, 0), (0, 0, 1))),
    'down': ((0, -1, 0), ((1, 0, 0), (0, 0, -1))),
}


class Cubo:
    def __init__(self, nome, de, ate, pele='pele', rot=None, inflar=0.0, so=None, grupo=None, emite=0):
        """de/ate: cantos (x,y,z). rot: (eixo 'x'|'y'|'z', angulo, origem (x,y,z)). pele: nome do material para a pintura.
        inflar: cresce o cubo em todas as direcoes (camada por cima: cabelo, roupa). so: faces a desenhar (None = todas).
        emite: luz propria do elemento no Java (0..15, "light_emission")."""
        self.nome, self.pele, self.rot, self.so, self.emite = nome, pele, rot, so, emite
        self.de = np.array([a - inflar for a in de], float)
        self.ate = np.array([a + inflar for a in ate], float)
        self.grupo = grupo or nome
        self.uv = {}

    def tam(self):
        return self.ate - self.de

    def faces(self):
        return [f for f in FACES if (self.so is None or f in self.so)]

    def com(self, **k):
        """Copia com mudancas (para quadros de animacao)."""
        c = Cubo.__new__(Cubo)
        c.__dict__.update(self.__dict__)
        c.de, c.ate, c.uv = self.de.copy(), self.ate.copy(), dict(self.uv)
        for a, v in k.items():
            setattr(c, a, v)
        return c


def girado(c, eixo, ang, origem):
    return c.com(rot=(eixo, float(ang), tuple(origem)))


# ------------------------------------------------------------------ atlas (UV)

def arrumar_uv(cubos, largura, escala=1):
    """Coloca as faces de cada cubo no atlas (prateleiras). escala = pixels por unidade de modelo.
    Retorna a altura usada. As UVs ficam em pixels do atlas."""
    itens = []
    for c in cubos:
        w, h, d = c.tam()
        for f in c.faces():
            if f in ('north', 'south'):
                fw, fh = w, h
            elif f in ('east', 'west'):
                fw, fh = d, h
            else:
                fw, fh = w, d
            itens.append((c, f, max(1, int(math.ceil(fw * escala))), max(1, int(math.ceil(fh * escala)))))
    itens.sort(key=lambda i: -i[3])
    x = y = linha = 0
    for c, f, fw, fh in itens:
        if x + fw > largura:
            x, y, linha = 0, y + linha + 1, 0
        c.uv[f] = (x, y, x + fw, y + fh)
        x += fw + 1
        linha = max(linha, fh)
    return y + linha


def pontos_face(c, f, uv, escala):
    """Para cada pixel da face: ponto 3D (x,y,z), normal e coordenadas locais (s,t em 0..1)."""
    n, (ua, va) = FACES[f]
    x0, y0, x1, y1 = uv
    W, H = x1 - x0, y1 - y0
    s = (np.arange(W) + 0.5) / W
    t = (np.arange(H) + 0.5) / H
    S, T = np.meshgrid(s, t)
    de, ate = c.de, c.ate
    tam = ate - de
    centro = (de + ate) / 2
    # extensao da face nos eixos u e v
    ua, va, n = np.array(ua, float), np.array(va, float), np.array(n, float)
    eu = abs(ua @ tam)
    ev = abs(va @ tam)
    p = centro + n * (np.abs(n) @ tam) / 2
    P = p[None, None, :] + (S - 0.5)[..., None] * eu * ua[None, None, :] + (T - 0.5)[..., None] * ev * va[None, None, :]
    return dict(x=P[..., 0], y=P[..., 1], z=P[..., 2], s=S, t=T, n=tuple(n), face=f, cubo=c, pele=c.pele, nome=c.nome, grupo=c.grupo,
                w=tam[0], h=tam[1], d=tam[2], de=de, ate=ate)


def pintar_atlas(cubos, largura, altura, pintor, escala=1):
    """pintor(P) -> (rgb array (H,W,3) 0..1, alpha (H,W) 0..1)."""
    img = np.zeros((altura, largura, 4))
    for c in cubos:
        for f in c.faces():
            uv = c.uv[f]
            P = pontos_face(c, f, uv, escala)
            rgb, a = pintor(P)
            rgb = np.clip(np.asarray(rgb, float), 0, 1)
            a = np.clip(np.broadcast_to(np.asarray(a, float), rgb.shape[:2]), 0, 1)
            x0, y0, x1, y1 = uv
            img[y0:y1, x0:x1, :3] = rgb
            img[y0:y1, x0:x1, 3] = a
    return (img * 255 + 0.5).astype(np.uint8)


# ------------------------------------------------------------------ Java

def modelo_java(cubos, textura, largura, altura, nome_textura='0', display=None):
    els = []
    for c in cubos:
        faces = {}
        for f in c.faces():
            x0, y0, x1, y1 = c.uv[f]
            # o Java sempre usa UV de 0 a 16 (texture_size e so do Blockbench)
            faces[f] = {'uv': [round(x0 * 16 / largura, 4), round(y0 * 16 / altura, 4), round(x1 * 16 / largura, 4), round(y1 * 16 / altura, 4)],
                        'texture': '#' + nome_textura}
        e = {'from': [round(float(v), 3) for v in c.de], 'to': [round(float(v), 3) for v in c.ate], 'faces': faces}
        if getattr(c, 'emite', 0):
            e['light_emission'] = int(max(0, min(15, c.emite)))
        for k in ('from', 'to'):
            for v in e[k]:
                if v < -16 or v > 32:
                    raise ValueError(f'{c.nome}: {k} {v} fora de -16..32')
        if c.rot:
            eixo, ang, orig = c.rot
            if ang not in ANGULOS:
                raise ValueError(f'{c.nome}: angulo {ang} nao permitido')
            if ang != 0:
                e['rotation'] = {'origin': [round(float(v), 3) for v in orig], 'axis': eixo, 'angle': ang}
        els.append(e)
    return {
        'credit': 'Corona Server - monstro original',
        'texture_size': [largura, altura],
        'textures': {nome_textura: textura, 'particle': textura},
        'elements': els,
        'display': display if display is not None else {'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]}},
    }


def item_java(modelo):
    return {'model': {'type': 'minecraft:model', 'model': modelo}}


# ------------------------------------------------------------------ Bedrock

def geometria_bedrock(ident, cubos, largura, altura, visivel=(2, 3, 2)):
    """Geometria com um osso por grupo (origem/pivo no centro do grupo). Rotacoes de quadro nao entram aqui
    (a animacao do Bedrock e feita com animation json, nao com cubos girados)."""
    grupos = {}
    for c in cubos:
        grupos.setdefault(c.grupo, []).append(c)
    bones = []
    for g, lista in grupos.items():
        de = np.min([c.de for c in lista], axis=0)
        ate = np.max([c.ate for c in lista], axis=0)
        piv = ((de + ate) / 2).tolist()
        cubes = []
        for c in lista:
            w, h, d = c.tam()
            # Bedrock: origem = canto (-x fica a direita de quem olha: o modelo e espelhado em x)
            cube = {'origin': [round(float(-c.ate[0]), 3), round(float(c.de[1]), 3), round(float(c.de[2]), 3)],
                    'size': [round(float(w), 3), round(float(h), 3), round(float(d), 3)],
                    'uv': {}}
            for f in c.faces():
                x0, y0, x1, y1 = c.uv[f]
                cube['uv'][f] = {'uv': [x0, y0], 'uv_size': [x1 - x0, y1 - y0]}
            if c.rot and c.rot[1] != 0:
                eixo, ang, orig = c.rot
                r = [0.0, 0.0, 0.0]
                r['xyz'.index(eixo)] = -ang if eixo in 'xy' else ang
                cube['pivot'] = [round(float(-orig[0]), 3), round(float(orig[1]), 3), round(float(orig[2]), 3)]
                cube['rotation'] = r
            cubes.append(cube)
        bones.append({'name': g, 'pivot': [round(-piv[0], 3), round(piv[1], 3), round(piv[2], 3)], 'cubes': cubes})
    return {
        'format_version': '1.16.0',
        'minecraft:geometry': [{
            'description': {'identifier': ident, 'texture_width': largura, 'texture_height': altura,
                            'visible_bounds_width': visivel[0], 'visible_bounds_height': visivel[1],
                            'visible_bounds_offset': [0, visivel[1] / 2, 0]},
            'bones': bones,
        }],
    }


# ------------------------------------------------------------------ previa

def _rot(p, eixo, ang, orig):
    a = math.radians(ang)
    ca, sa = math.cos(a), math.sin(a)
    x, y, z = p[0] - orig[0], p[1] - orig[1], p[2] - orig[2]
    if eixo == 'x':
        y, z = y * ca - z * sa, y * sa + z * ca
    elif eixo == 'y':
        x, z = x * ca + z * sa, -x * sa + z * ca
    else:
        x, y = x * ca - y * sa, x * sa + y * ca
    return (x + orig[0], y + orig[1], z + orig[2])


def previa(cubos, atlas, yaw=0.0, pitch=0.0, escala=6, fundo=(28, 30, 36)):
    """Render ortografico simples: cada face e um quadrilatero com a cor media da textura e luz por normal;
    as faces de frente (quase sem rotacao) sao coladas com a textura. Suficiente para conferir forma e cores."""
    H, W = atlas.shape[:2]
    yaw_r, pit_r = math.radians(yaw), math.radians(pitch)
    quads = []
    for c in cubos:
        for f in c.faces():
            n, (ua, va) = FACES[f]
            de, ate = c.de, c.ate
            # cantos da face em 3D (ordem: (0,0),(1,0),(1,1),(0,1) em s,t)
            tam = ate - de
            centro = (de + ate) / 2
            ua_, va_, n_ = np.array(ua, float), np.array(va, float), np.array(n, float)
            eu, ev = abs(ua_ @ tam), abs(va_ @ tam)
            p0 = centro + n_ * (np.abs(n_) @ tam) / 2
            cantos = [p0 + (s - 0.5) * eu * ua_ + (t - 0.5) * ev * va_ for s, t in ((0, 0), (1, 0), (1, 1), (0, 1))]
            if c.rot and c.rot[1] != 0:
                cantos = [np.array(_rot(p, *c.rot)) for p in cantos]
                nn = np.array(_rot(n_ + np.array(c.rot[2]), *c.rot)) - np.array(c.rot[2])
            else:
                nn = n_
            proj = []
            for p in cantos:
                x, y, z = p
                # yaw em torno de y, depois pitch em torno de x
                x, z = x * math.cos(yaw_r) + z * math.sin(yaw_r), -x * math.sin(yaw_r) + z * math.cos(yaw_r)
                y, z = y * math.cos(pit_r) - z * math.sin(pit_r), y * math.sin(pit_r) + z * math.cos(pit_r)
                proj.append((x, y, z))
            nx, nz = nn[0] * math.cos(yaw_r) + nn[2] * math.sin(yaw_r), -nn[0] * math.sin(yaw_r) + nn[2] * math.cos(yaw_r)
            ny, nz = nn[1] * math.cos(pit_r) - nz * math.sin(pit_r), nn[1] * math.sin(pit_r) + nz * math.cos(pit_r)
            if nz <= 0.001:
                continue  # de costas
            x0, y0, x1, y1 = c.uv[f]
            tex = atlas[y0:y1, x0:x1]
            a = tex[..., 3] / 255.0
            if a.mean() < 0.02:
                continue
            luz = 0.55 + 0.45 * nz + 0.15 * max(0.0, ny)
            quads.append((np.mean([p[2] for p in proj]), proj, tex, luz, f, c))
    quads.sort(key=lambda q: q[0])
    xs = [p[0] for q in quads for p in q[1]]
    ys = [p[1] for q in quads for p in q[1]]
    mx, Mx, my, My = min(xs), max(xs), min(ys), max(ys)
    IW, IH = int((Mx - mx) * escala) + 20, int((My - my) * escala) + 20
    img = Image.new('RGBA', (IW, IH), fundo + (255,))
    for z, proj, tex, luz, f, c in quads:
        pts = [((p[0] - mx) * escala + 10, (My - p[1]) * escala + 10) for p in proj]
        # textura colada por transformacao afim (quad -> retangulo da textura)
        th, tw = tex.shape[:2]
        if tw < 1 or th < 1:
            continue
        pil = Image.fromarray((np.dstack([tex[..., :3] * luz, tex[..., 3:4]])).clip(0, 255).astype(np.uint8), 'RGBA')
        # tamanho alvo
        bw = max(1, int(max(abs(pts[1][0] - pts[0][0]), abs(pts[2][0] - pts[3][0]), 1)))
        bh = max(1, int(max(abs(pts[3][1] - pts[0][1]), abs(pts[2][1] - pts[1][1]), 1)))
        pil = pil.resize((max(bw, 1), max(bh, 1)), Image.NEAREST)
        # afim: mapeia o quad destino -> textura (usa 3 pontos)
        (x0, y0), (x1, y1), (x2, y2), (x3, y3) = pts
        minx, miny = int(min(x0, x1, x2, x3)), int(min(y0, y1, y2, y3))
        maxx, maxy = int(max(x0, x1, x2, x3)) + 1, int(max(y0, y1, y2, y3)) + 1
        bw2, bh2 = max(1, maxx - minx), max(1, maxy - miny)
        # matriz inversa (destino -> origem) pelos 3 primeiros pontos
        sx, sy = pil.size
        A = np.array([[x1 - x0, x3 - x0], [y1 - y0, y3 - y0]], float)
        if abs(np.linalg.det(A)) < 1e-6:
            continue
        Ai = np.linalg.inv(A)
        a, b = Ai[0] * sx
        d, e = Ai[1] * sy
        cx = -(a * x0 + b * y0) + 0
        cy = -(d * x0 + e * y0) + 0
        # PIL transform: (a,b,c,d,e,f) mapeia saida (x,y) -> entrada (ax+by+c, dx+ey+f)
        warped = pil.transform((bw2, bh2), Image.AFFINE, (a, b, a * minx + b * miny + cx, d, e, d * minx + e * miny + cy), Image.NEAREST)
        # mascara pelo poligono
        mask = Image.new('L', (bw2, bh2), 0)
        ImageDraw.Draw(mask).polygon([(x - minx, y - miny) for x, y in pts], fill=255)
        wa = np.array(warped)
        wa[..., 3] = (wa[..., 3].astype(int) * np.array(mask) // 255).astype(np.uint8)
        img.alpha_composite(Image.fromarray(wa, 'RGBA'), (minx, miny))
    return img


def folha(previas, fundo=(28, 30, 36)):
    W = sum(p.size[0] for p in previas) + 12 * (len(previas) + 1)
    H = max(p.size[1] for p in previas) + 24
    f = Image.new('RGBA', (W, H), fundo + (255,))
    x = 12
    for p in previas:
        f.alpha_composite(p, (x, 12))
        x += p.size[0] + 12
    return f


def salvar_json(caminho, obj):
    os.makedirs(os.path.dirname(caminho), exist_ok=True)
    with open(caminho, 'w', encoding='utf-8') as f:
        json.dump(obj, f, ensure_ascii=False, separators=(',', ':'))


# ------------------------------------------------------------------ Bedrock: entidade (geometria por ossos + animacoes)

def _rot_grupo(lista, grupo):
    """Rotacao (eixo, ang, origem) do grupo num quadro (a primeira que achar), ou None."""
    for c in lista:
        if c.grupo == grupo and c.rot and c.rot[1] != 0:
            return c.rot
    return None


def _ang_bedrock(eixo, ang):
    """Java (modelo virado para +z, item display) -> entidade Bedrock (virada para -z): giro de 180 em y e depois
    a conversao Java->Bedrock (x e y trocam de sinal). Resultado: x mantem, y e z invertem."""
    return [ang if eixo == 'x' else 0.0, -ang if eixo == 'y' else 0.0, -ang if eixo == 'z' else 0.0]


def entidade_bedrock(nome, quadros, olhos, largura, altura, escala=1.0, dy=16.0):
    """Geometria com um osso por grupo (pivo = origem da rotacao usada nos quadros) e animacoes idle/walk/grito.
    Os cubos sao os do quadro idle SEM as rotacoes de quadro (elas viram animacao). Os olhos entram no osso da cabeca."""
    idle = quadros['idle']
    # cubos "neutros": a versao sem rotacao de quadro (a rotacao de pose fixa, ex. cabeca torta, vira animacao idle)
    grupos = {}
    for c in idle + olhos.get('idle', []):
        grupos.setdefault(c.grupo if c.pele != 'olho' else 'cabeca', []).append(c)
    pivos = {}
    for q in ('idle', 'walk1', 'grito'):
        for c in quadros.get(q, []) + olhos.get(q if q == 'grito' else 'idle', []):
            g = c.grupo if c.pele != 'olho' else 'cabeca'
            if c.rot and c.rot[1] != 0 and g not in pivos:
                pivos[g] = c.rot[2]
    bones = []
    for g, lista in grupos.items():
        if g in pivos:
            ox, oy, oz = pivos[g]
        else:
            de = np.min([c.de for c in lista], axis=0)
            ate = np.max([c.ate for c in lista], axis=0)
            ox, oy, oz = (de[0] + ate[0]) / 2, de[1], (de[2] + ate[2]) / 2
        cubes = []
        for c in lista:
            w, h, d = c.tam()
            # giro de 180 em y: x' = -x, z' = -z; depois Bedrock espelha x: origem = (-x'max, ymin, z'min) = (ate.x? ...)
            # x' em [-ate.x, -de.x] -> origem Bedrock x = -(-de.x) = de.x ; z' em [-ate.z, -de.z]
            cube = {'origin': [round(float(c.de[0]), 3), round(float(c.de[1] + dy), 3), round(float(-c.ate[2]), 3)],
                    'size': [round(float(w), 3), round(float(h), 3), round(float(d), 3)], 'uv': {}}
            troca = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east', 'up': 'up', 'down': 'down'}
            for f in c.faces():
                x0, y0, x1, y1 = c.uv[f]
                cube['uv'][troca[f]] = {'uv': [x0, y0], 'uv_size': [x1 - x0, y1 - y0]}
            if c.rot and c.rot[1] != 0 and c.pele != 'olho' and g not in pivos:
                eixo, ang, orig = c.rot
                cube['pivot'] = [round(float(orig[0]), 3), round(float(orig[1] + dy), 3), round(float(-orig[2]), 3)]
                cube['rotation'] = _ang_bedrock(eixo, ang)
            cubes.append(cube)
        bones.append({'name': g, 'pivot': [round(float(ox), 3), round(float(oy + dy), 3), round(float(-oz), 3)], 'cubes': cubes})
    geo = {
        'format_version': '1.16.0',
        'minecraft:geometry': [{
            'description': {'identifier': f'geometry.corona.{nome}', 'texture_width': largura, 'texture_height': altura,
                            'visible_bounds_width': 4, 'visible_bounds_height': 4.5, 'visible_bounds_offset': [0, 1.5, 0]},
            'bones': bones,
        }],
    }
    # animacoes: por grupo, a rotacao de cada quadro (idle = pose fixa; walk = oscila entre walk1 e walk2; grito = pose)
    def pose(q):
        out = {}
        for g in grupos:
            r = _rot_grupo(quadros[q], g)
            if r:
                out[g] = _ang_bedrock(r[0], r[1])
        return out
    p_idle, p_walk, p_grito = pose('idle'), pose('walk1'), pose('grito')
    anim = {'format_version': '1.8.0', 'animations': {}}
    bidle = {g: {'rotation': v} for g, v in p_idle.items()}
    anim['animations'][f'animation.corona.{nome}.idle'] = {'loop': True, 'bones': bidle}
    bwalk = {}
    for g, v in p_walk.items():
        base_rot = p_idle.get(g, [0, 0, 0])
        osc = []
        for k in range(3):
            amp = v[k] - base_rot[k]
            # as animacoes do Bedrock somam: aqui so a oscilacao (a pose fica na animacao idle)
            osc.append(f'math.sin(q.modified_distance_moved * 40) * {round(amp, 2)}' if abs(amp) > 0.01 else 0.0)
        bwalk[g] = {'rotation': osc}
    anim['animations'][f'animation.corona.{nome}.walk'] = {'loop': True, 'bones': bwalk}
    bgrito = {g: {'rotation': v} for g, v in p_grito.items()}
    anim['animations'][f'animation.corona.{nome}.grito'] = {'loop': True, 'bones': bgrito}
    # respiracao: escala sutil do tronco
    anim['animations'][f'animation.corona.{nome}.respira'] = {'loop': True, 'animation_length': 3.0,
        'bones': {g: {'scale': {'0.0': [1, 1, 1], '1.5': [1.02, 1.03, 1.02], '3.0': [1, 1, 1]}} for g in grupos if g in ('tronco', 'saia')}}
    return geo, anim


def salvar_png_leve(arr, caminho, cores=256):
    """PNG em paleta (ate 256 cores): ~4x menor que RGBA para texturas com ruido. Pixels transparentes viram preto
    antes (nao gastam cores)."""
    a = np.array(arr, dtype=np.uint8, copy=True)
    if a.ndim == 3 and a.shape[2] == 4:
        a[a[..., 3] == 0] = 0
    im = Image.fromarray(a, 'RGBA' if a.ndim == 3 and a.shape[2] == 4 else None)
    q = im.quantize(colors=cores, method=Image.Quantize.FASTOCTREE)
    q.save(caminho, optimize=True)
