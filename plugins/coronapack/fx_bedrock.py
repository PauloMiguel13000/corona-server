"""Efeitos das habilidades no Bedrock (pacote v2, 08/11): em vez de centenas de particulas pela rede, o servidor poe um
"emissor" (ItemDisplay invisivel com o item corona:fx_<categoria>) e o celular desenha sozinho uma animacao rica, feita no
sistema de particulas do Bedrock (emissores com forma, cor, movimento e vida proprios). Cada categoria tem a versao normal e
a grande (_g). Este modulo gera: o atlas de texturas, as particulas (particles/), os attachables invisiveis que disparam
as particulas, a animacao que as dispara, os icones e o modelo Java vazio (quem joga no Java nao ve o emissor)."""
import json
import math
import os

import numpy as np
from PIL import Image

# ------------------------------------------------------------------ atlas 128x128, celulas 16x16 (8 x 8)
CELULAS = ['brilho', 'faisca', 'chama', 'floco', 'fumaca', 'gota', 'coracao', 'petala', 'lasca', 'anel', 'raio', 'caveira', 'bolha',
           'quadrado', 'cruz', 'pena', 'estrela', 'folha']


def _grade(n=16):
    y, x = np.mgrid[0:n, 0:n].astype(float)
    cx = cy = (n - 1) / 2
    return x - cx, y - cy, np.hypot(x - cx, y - cy)


def _celula(nome):
    n = 16
    x, y, r = _grade(n)
    a = np.zeros((n, n))
    rgb = np.ones((n, n, 3))
    if nome == 'brilho':
        a = np.clip(1 - r / 7.5, 0, 1) ** 1.6
    elif nome == 'faisca':
        a = np.clip(1 - (np.abs(x) + np.abs(y)) / 7, 0, 1) ** 1.2
        a = np.maximum(a, np.clip(1 - r / 2.5, 0, 1))
    elif nome == 'chama':
        yy = (y + 7.5) / 15  # 0 topo .. 1 base
        larg = 2 + 5 * np.sin(np.clip(yy, 0, 1) * math.pi) ** 0.8 * (0.4 + 0.6 * yy)
        a = np.clip(1 - np.abs(x) / np.maximum(larg, 0.1), 0, 1) ** 1.3 * (yy > 0.05)
        rgb[..., 1] = np.clip(1 - yy * 0.6, 0, 1)
        rgb[..., 2] = np.clip(0.6 - yy, 0, 1)
    elif nome == 'floco':
        for ang in (0, 60, 120):
            t = math.radians(ang)
            u = x * math.cos(t) + y * math.sin(t)
            v = -x * math.sin(t) + y * math.cos(t)
            a = np.maximum(a, (np.abs(v) < 0.8) * (np.abs(u) < 7))
            a = np.maximum(a, ((np.abs(np.abs(u) - 4) < 0.7) & (np.abs(v) < 2.5)) * 1.0)
        a = a.astype(float)
    elif nome == 'fumaca':
        rng = np.random.default_rng(3)
        a = np.clip(1 - r / 7, 0, 1)
        a = np.clip(a + (rng.random((n, n)) - 0.5) * 0.35, 0, 1) * (r < 7.5)
    elif nome == 'gota':
        yy = y + 2
        a = ((x ** 2) / 9 + (yy ** 2) / 20 < 1) * 1.0
        a = np.maximum(a, (np.abs(x) < np.clip((y + 7.5) / 2.2, 0, 3)) * (y < -2) * (y > -7.5))
        rgb[..., 0] = 0.75
        rgb[..., 1] = 0.9
    elif nome == 'coracao':
        xx, yy = x / 6.5, -(y / 6.5) + 0.3
        a = ((xx ** 2 + yy ** 2 - 1) ** 3 - xx ** 2 * yy ** 3 < 0) * 1.0
    elif nome == 'petala':
        t = math.radians(35)
        u = x * math.cos(t) + y * math.sin(t)
        v = -x * math.sin(t) + y * math.cos(t)
        a = ((u ** 2) / 36 + (v ** 2) / 9 < 1) * 1.0
    elif nome == 'lasca':
        a = (np.abs(x) < np.clip(3 - np.abs(y) * 0.4, 0.3, 3)) * (np.abs(y) < 7) * 1.0
        rgb[..., :] = [0.85, 0.97, 1.0]
    elif nome == 'anel':
        a = np.clip(1 - np.abs(r - 5.5) / 1.6, 0, 1)
    elif nome == 'raio':
        pts = [(-1, -7), (2, -3), (-1, -2), (3, 2), (0, 3), (2, 7)]
        for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
            for t in np.linspace(0, 1, 20):
                px, py = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
                a = np.maximum(a, np.clip(1 - np.hypot(x - px, y - py) / 1.3, 0, 1))
    elif nome == 'caveira':
        a = (r < 5.5) * 1.0
        a[(np.abs(x) > 1.2) & (np.abs(x) < 3.5) & (y > -2.5) & (y < 0)] = 0
        a[(np.abs(x) < 3.5) & (y > 2) & (y < 5.5) & (((x + 8) % 2) < 1)] = 0
        a[(y > 1) & (y < 3) & (np.abs(x) < 0.8)] = 0
    elif nome == 'bolha':
        a = np.clip(1 - np.abs(r - 5.5) / 1.3, 0, 1) * 0.9
        a = np.maximum(a, np.clip(1 - np.hypot(x + 2.5, y + 2.5) / 1.6, 0, 1))
        a = np.maximum(a, (r < 5.5) * 0.18)
    elif nome == 'quadrado':
        a = ((np.abs(x) < 5) & (np.abs(y) < 5)) * 1.0
        a[(np.abs(x) > 4) | (np.abs(y) > 4)] = 0.6
    elif nome == 'cruz':
        a = ((np.abs(x) < 1.2) | (np.abs(y) < 1.2)) * (r < 7) * 1.0
        a = np.maximum(a, np.clip(1 - r / 3, 0, 1))
    elif nome == 'pena':
        a = ((x ** 2) / 6 + ((y) ** 2) / 45 < 1) * 1.0
        a[(np.abs(x) < 0.5)] = 0.5
    elif nome == 'estrela':
        ang = np.arctan2(y, x)
        rr = 3 + 3.5 * (np.cos(5 * ang) * 0.5 + 0.5)
        a = np.clip(1 - (r - rr), 0, 1) * (r < 7.5)
    elif nome == 'folha':
        t = math.radians(-40)
        u = x * math.cos(t) + y * math.sin(t)
        v = -x * math.sin(t) + y * math.cos(t)
        a = ((u ** 2) / 42 + (v ** 2) / 7 < 1) * 1.0
        a[np.abs(v) < 0.4] = 0.55
    img = np.zeros((n, n, 4))
    img[..., :3] = rgb
    img[..., 3] = np.clip(a, 0, 1)
    return img


def atlas():
    img = np.zeros((128, 128, 4))
    for i, nome in enumerate(CELULAS):
        cx, cy = (i % 8) * 16, (i // 8) * 16
        img[cy:cy + 16, cx:cx + 16] = _celula(nome)
    return Image.fromarray((img * 255).astype(np.uint8), 'RGBA')


def uv(nome):
    i = CELULAS.index(nome)
    return [(i % 8) * 16, (i // 8) * 16]


# ------------------------------------------------------------------ as categorias
# cor: lista de [r,g,b,a] (0..1) ao longo da vida (gradiente); add: material aditivo (brilha)
CORES_POEIRA = {
    'vermelho': (0.95, 0.15, 0.15), 'laranja': (1.0, 0.55, 0.1), 'amarelo': (1.0, 0.9, 0.2), 'verde': (0.3, 0.9, 0.3),
    'azul': (0.25, 0.45, 1.0), 'ciano': (0.3, 0.9, 1.0), 'roxo': (0.6, 0.25, 0.9), 'rosa': (1.0, 0.5, 0.8),
    'branco': (1.0, 1.0, 1.0), 'preto': (0.12, 0.1, 0.14), 'marrom': (0.55, 0.35, 0.15), 'dourado': (1.0, 0.8, 0.3),
}


def _grad(*cores):
    """Gradiente de cor ao longo da vida: lista [r,g,b,a] por ponto."""
    n = len(cores)
    return {f'{i / (n - 1):.2f}': [round(c, 3) for c in cor] for i, cor in enumerate(cores)}


def categorias():
    cats = {}

    def cat(nome, textura, cores, material, vida, tam, vel, forma, taxa, acel, arrasto, extra=None, instante=False, rotacao=None):
        cats[nome] = dict(textura=textura, cores=cores, material=material, vida=vida, tam=tam, vel=vel, forma=forma, taxa=taxa,
                          acel=acel, arrasto=arrasto, extra=extra or {}, instante=instante, rotacao=rotacao)

    cat('fogo', 'chama', _grad([1, 0.95, 0.5, 1], [1, 0.55, 0.1, 1], [0.8, 0.15, 0.05, 0.9], [0.2, 0.05, 0.02, 0]), 'particles_add',
        (0.5, 1.0), (0.25, 0.4), (0.4, 1.4), ('esfera', 0.45, 'outwards'), 26, [0, 2.6, 0], 1.2)
    cat('alma', 'chama', _grad([0.7, 1, 1, 1], [0.2, 0.8, 1, 1], [0.1, 0.3, 0.9, 0.8], [0.05, 0.05, 0.4, 0]), 'particles_add',
        (0.6, 1.2), (0.25, 0.4), (0.3, 1.2), ('esfera', 0.45, 'outwards'), 22, [0, 2.2, 0], 1.2)
    cat('gelo', 'floco', _grad([1, 1, 1, 1], [0.75, 0.95, 1, 1], [0.5, 0.8, 1, 0]), 'particles_alpha',
        (0.8, 1.6), (0.12, 0.3), (0.6, 2.0), ('esfera', 0.7, 'outwards'), 22, [0, -1.5, 0], 1.6, rotacao=(0, 360))
    cat('raio', 'faisca', _grad([1, 1, 1, 1], [1, 1, 0.5, 1], [0.6, 0.8, 1, 0]), 'particles_add',
        (0.15, 0.4), (0.15, 0.35), (3, 7), ('esfera', 0.6, 'outwards'), 40, [0, 0, 0], 3.0)
    cat('luz', 'brilho', _grad([1, 1, 0.9, 1], [1, 0.95, 0.6, 0.9], [1, 0.8, 0.4, 0]), 'particles_add',
        (0.8, 1.6), (0.15, 0.5), (0.2, 0.7), ('esfera', 0.6, 'outwards'), 20, [0, 0.8, 0], 0.8)
    cat('fumaca', 'fumaca', _grad([0.9, 0.9, 0.9, 0.7], [0.6, 0.6, 0.6, 0.5], [0.4, 0.4, 0.4, 0]), 'particles_blend',
        (1.0, 2.0), (0.35, 0.9), (0.3, 1.0), ('esfera', 0.5, 'outwards'), 14, [0, 0.9, 0], 1.0, rotacao=(-60, 60))
    cat('sombra', 'fumaca', _grad([0.35, 0.1, 0.45, 0.9], [0.15, 0.03, 0.2, 0.8], [0, 0, 0, 0]), 'particles_blend',
        (0.8, 1.6), (0.35, 0.9), (0.6, 1.6), ('esfera', 1.0, 'inwards'), 18, [0, 0.4, 0], 1.2, rotacao=(-90, 90))
    cat('cura', 'coracao', _grad([1, 0.5, 0.7, 1], [0.6, 1, 0.5, 1], [0.6, 1, 0.5, 0]), 'particles_alpha',
        (0.8, 1.4), (0.2, 0.3), (0.6, 1.4), ('esfera', 0.6, 'outwards'), 16, [0, 1.2, 0], 1.5)
    cat('agua', 'gota', _grad([0.6, 0.85, 1, 1], [0.3, 0.6, 1, 1], [0.2, 0.4, 0.9, 0]), 'particles_alpha',
        (0.5, 1.0), (0.15, 0.3), (2, 4.5), ('esfera', 0.3, 'outwards'), 30, [0, -9, 0], 0.4)
    cat('golpe', 'cruz', _grad([1, 1, 1, 1], [1, 0.9, 0.5, 1], [1, 0.6, 0.2, 0]), 'particles_add',
        (0.2, 0.45), (0.2, 0.45), (4, 8), ('esfera', 0.4, 'outwards'), 34, [0, -2, 0], 2.5, instante=True)
    cat('explosao', 'brilho', _grad([1, 1, 0.8, 1], [1, 0.6, 0.2, 1], [0.6, 0.2, 0.05, 0.8], [0.2, 0.1, 0.1, 0]), 'particles_add',
        (0.4, 0.9), (0.5, 1.2), (3, 7), ('esfera', 0.4, 'outwards'), 40, [0, 1, 0], 2.8, instante=True)
    cat('natureza', 'petala', _grad([1, 0.7, 0.85, 1], [1, 0.6, 0.8, 1], [0.9, 0.5, 0.7, 0]), 'particles_alpha',
        (1.2, 2.4), (0.15, 0.3), (0.3, 1.0), ('esfera', 0.8, 'outwards'), 14, [0, -0.8, 0], 1.0, rotacao=(0, 360))
    cat('veneno', 'bolha', _grad([0.5, 1, 0.3, 1], [0.3, 0.8, 0.2, 0.9], [0.2, 0.5, 0.1, 0]), 'particles_blend',
        (0.8, 1.6), (0.15, 0.4), (0.3, 1.0), ('esfera', 0.6, 'outwards'), 18, [0, 1.0, 0], 1.0)
    cat('magia', 'estrela', _grad([1, 0.8, 1, 1], [0.8, 0.4, 1, 1], [0.5, 0.2, 0.9, 0]), 'particles_add',
        (0.6, 1.2), (0.15, 0.35), (0.5, 1.5), ('esfera', 0.7, 'outwards'), 24, [0, 1.5, 0], 1.0, rotacao=(0, 360))
    cat('morte', 'caveira', _grad([0.9, 0.9, 0.95, 1], [0.5, 0.5, 0.6, 0.9], [0.2, 0.2, 0.3, 0]), 'particles_alpha',
        (1.0, 1.8), (0.25, 0.4), (0.3, 0.8), ('esfera', 0.7, 'outwards'), 12, [0, 0.8, 0], 1.0)
    cat('terra', 'quadrado', _grad([0.55, 0.38, 0.2, 1], [0.45, 0.3, 0.15, 1], [0.3, 0.2, 0.1, 0]), 'particles_alpha',
        (0.6, 1.2), (0.15, 0.35), (2, 5), ('esfera', 0.5, 'outwards'), 26, [0, -12, 0], 0.5, rotacao=(0, 360))
    cat('vento', 'pena', _grad([1, 1, 1, 0.9], [0.9, 0.95, 1, 0.7], [0.8, 0.9, 1, 0]), 'particles_blend',
        (0.6, 1.2), (0.2, 0.5), (3, 6), ('esfera', 0.5, 'outwards'), 26, [0, 2, 0], 1.5, rotacao=(0, 720))
    cat('sangue', 'gota', _grad([0.8, 0.05, 0.05, 1], [0.55, 0.02, 0.02, 1], [0.3, 0, 0, 0]), 'particles_alpha',
        (0.4, 0.9), (0.12, 0.25), (2, 4), ('esfera', 0.3, 'outwards'), 24, [0, -10, 0], 0.5, instante=True)
    for nome, (r, g, b) in CORES_POEIRA.items():
        cat('poeira_' + nome, 'quadrado', _grad([r, g, b, 1], [r, g, b, 1], [r, g, b, 0]), 'particles_blend' if nome != 'preto' else 'particles_alpha',
            (0.5, 1.1), (0.14, 0.26), (0.1, 0.5), ('esfera', 0.45, 'outwards'), 24, [0, 0.15, 0], 2.0, rotacao=(0, 90))
    return cats


def _particula(ident, c, grande):
    textura = 'textures/particle/corona_fx'
    u = uv(c['textura'])
    raio = c['forma'][1] * (2.4 if grande else 1.0)
    taxa = int(c['taxa'] * (2.2 if grande else 1.0))
    tam0 = c['tam'][0] * (1.5 if grande else 1.0)
    tam1 = c['tam'][1] * (1.5 if grande else 1.0)
    comps = {
        'minecraft:emitter_initialization': {'creation_expression': 'variable.giro = Math.random(-1,1);'},
        'minecraft:emitter_shape_sphere': {'radius': round(raio, 3), 'direction': c['forma'][2], 'surface_only': False},
        'minecraft:particle_lifetime_expression': {'max_lifetime': f'Math.random({c["vida"][0]},{c["vida"][1]})'},
        'minecraft:particle_initial_speed': f'Math.random({c["vel"][0]},{c["vel"][1]})',
        'minecraft:particle_motion_dynamic': {'linear_acceleration': c['acel'], 'linear_drag_coefficient': c['arrasto']},
        'minecraft:particle_appearance_billboard': {
            'size': [f'Math.lerp({tam0},{tam1},variable.particle_age/variable.particle_lifetime)'] * 2,
            'facing_camera_mode': 'rotate_xyz' if c['rotacao'] else 'lookat_xyz',
            'uv': {'texture_width': 128, 'texture_height': 128, 'uv': u, 'uv_size': [16, 16]},
        },
        'minecraft:particle_appearance_tinting': {'color': {'gradient': c['cores'], 'interpolant': 'variable.particle_age/variable.particle_lifetime'}},
    }
    if c['instante']:
        comps['minecraft:emitter_rate_instant'] = {'num_particles': taxa}
        comps['minecraft:emitter_lifetime_once'] = {'active_time': 0.05}
    else:
        comps['minecraft:emitter_rate_steady'] = {'spawn_rate': taxa, 'max_particles': taxa * 3}
        comps['minecraft:emitter_lifetime_once'] = {'active_time': 1.0}
    if c['rotacao']:
        a0, a1 = c['rotacao']
        comps['minecraft:particle_initial_spin'] = {'rotation': f'Math.random({a0},{a1})', 'rotation_rate': 'variable.giro*180'}
    return {'format_version': '1.10.0', 'particle_effect': {
        'description': {'identifier': ident, 'basic_render_parameters': {'material': c['material'], 'texture': textura}},
        'components': comps}}


def _attachable(ident, particula):
    return {'format_version': '1.10.0', 'minecraft:attachable': {'description': {
        'identifier': ident,
        'materials': {'default': 'entity_alphatest', 'enchanted': 'entity_alphatest_glint'},
        'textures': {'default': 'textures/entity/corona_v2/fx_vazio', 'enchanted': 'textures/misc/enchanted_actor_glint'},
        'geometry': {'default': 'geometry.corona.fx_vazio'},
        'particle_effects': {'fx': particula},
        'animations': {'emitir': 'animation.corona.fx_emitir'},
        'scripts': {'animate': ['emitir'], 'parent_setup': 'variable.chest_layer_visible = 0.0; variable.helmet_layer_visible = 0.0;'},
        'render_controllers': ['controller.render.armor']}}}


def gerar(BED, JOUT, escrever_json, item_tex, definicao):
    """Escreve tudo no pacote Bedrock (BED) e no Java (JOUT); registra os itens no mapeamento do Geyser via 'definicao'."""
    os.makedirs(os.path.join(BED, 'textures', 'particle'), exist_ok=True)
    atlas().save(os.path.join(BED, 'textures', 'particle', 'corona_fx.png'))
    os.makedirs(os.path.join(BED, 'textures', 'entity', 'corona_v2'), exist_ok=True)
    Image.fromarray(np.zeros((16, 16, 4), np.uint8), 'RGBA').save(os.path.join(BED, 'textures', 'entity', 'corona_v2', 'fx_vazio.png'))
    escrever_json(os.path.join(BED, 'models', 'entity', 'corona_v2', 'fx_vazio.geo.json'), {
        'format_version': '1.16.0', 'minecraft:geometry': [{'description': {'identifier': 'geometry.corona.fx_vazio', 'texture_width': 16, 'texture_height': 16},
                                                              'bones': [{'name': 'geyser_z', 'pivot': [0, 8, 0]}]}]})
    escrever_json(os.path.join(BED, 'animations', 'corona_fx.animation.json'), {
        'format_version': '1.8.0', 'animations': {'animation.corona.fx_emitir': {'loop': True, 'animation_length': 1.0, 'particle_effects': {'0.0': {'effect': 'fx'}}}}})
    # icone (quase invisivel no inventario: ninguem segura isso)
    ic = np.zeros((16, 16, 4), np.uint8)
    ic[7:9, 7:9] = (255, 255, 255, 40)
    p = os.path.join(BED, 'textures', 'items', 'corona', 'v2', 'fx.png')
    Image.fromarray(ic, 'RGBA').save(p)
    item_tex['texture_data']['corona_fx'] = {'textures': 'textures/items/corona/v2/fx'}
    # Java: modelo vazio
    escrever_json(os.path.join(JOUT, 'models', 'item', 'fx', 'vazio.json'), {'textures': {'particle': 'minecraft:item/leather_helmet'}, 'elements': []})
    n = 0
    for nome, c in categorias().items():
        for grande in (False, True):
            suf = '_g' if grande else ''
            bid = f'corona:fx_{nome}{suf}'
            pid = f'corona:fxp_{nome}{suf}'
            escrever_json(os.path.join(BED, 'particles', f'corona_fx_{nome}{suf}.json'), _particula(pid, c, grande))
            escrever_json(os.path.join(BED, 'attachables', f'fx_{nome}{suf}.json'), _attachable(bid, pid))
            escrever_json(os.path.join(JOUT, 'items', 'fx', f'{nome}{suf}.json'), {'model': {'type': 'minecraft:model', 'model': 'corona:item/fx/vazio'}})
            definicao('minecraft:leather_helmet', f'corona:fx/{nome}{suf}', bid, f'FX {nome}{suf}', 'corona_fx', 1)
            n += 1
    return n


if __name__ == '__main__':
    atlas().resize((512, 512), Image.NEAREST).save('/tmp/claude-0/fx_atlas_previa.png')
    print(len(categorias()), 'categorias')
