"""Gera modelos, texturas, itens, fonte do susto (Java) e geometrias (Bedrock) dos monstros do Nao Chore.

Uso: python3 -I gerar.py [nomes...]
Saida: pacote Java em ../coronapack/pacote/java/assets/corona/..., Bedrock em ../coronapack/pacote/bedrock/..., previas em /tmp/claude-0/modelos.
"""
import math, os, sys
import numpy as np
from PIL import Image, ImageFilter
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import kit  # noqa: E402
from naochore import MONSTROS  # noqa: E402
from backrooms import MONSTROS as MONSTROS_BR  # noqa: E402

# conjunto -> (monstros, primeiro caractere da fonte do susto)
CONJUNTOS = {'naochore': (MONSTROS, 0xE000), 'backrooms': (MONSTROS_BR, 0xE010)}
from base import ss, elipse  # noqa: E402

AQUI = os.path.dirname(os.path.abspath(__file__))
JAVA = os.path.normpath(os.path.join(AQUI, '..', 'coronapack', 'pacote', 'java', 'assets', 'corona'))
BEDROCK = os.path.normpath(os.path.join(AQUI, '..', 'coronapack', 'pacote', 'bedrock_3d'))
PREV = '/tmp/claude-0/modelos'
ESCALA_TEX = 3          # pixels por unidade de modelo na textura
LARGURA = 192
os.makedirs(PREV, exist_ok=True)


def pot2(n):
    p = 16
    while p < n:
        p *= 2
    return p


def gerar(nome, M, conjunto='naochore'):
    base = M['cubos']()
    quadros = M['quadros'](base)
    olhos = M['olhos'](base)
    # UV: uma regiao por (nome do cubo, pele); todos os quadros usam as mesmas
    chave = lambda c: (c.nome, c.pele, c.so)
    reps = {}
    for lista in list(quadros.values()) + list(olhos.values()):
        for c in lista:
            reps.setdefault(chave(c), c)
    reps_l = list(reps.values())
    altura = kit.arrumar_uv(reps_l, LARGURA, ESCALA_TEX)
    H = pot2(altura)
    W = LARGURA if LARGURA & (LARGURA - 1) == 0 else pot2(LARGURA)
    for lista in list(quadros.values()) + list(olhos.values()):
        for c in lista:
            c.uv = dict(reps[chave(c)].uv)
    atlas = kit.pintar_atlas(reps_l, W, H, M['pintor'], ESCALA_TEX)
    # Java: textura, modelos por quadro, itens
    tex_dir = os.path.join(JAVA, 'textures', 'item', conjunto)  # so textures/item e textures/block entram no atlas do Java
    os.makedirs(tex_dir, exist_ok=True)
    Image.fromarray(atlas, 'RGBA').save(os.path.join(tex_dir, f'{nome}.png'), optimize=True)
    tex_key = f'corona:item/{conjunto}/{nome}'
    for q, lista in quadros.items():
        kit.salvar_json(os.path.join(JAVA, 'models', 'entity', conjunto, f'{nome}_{q}.json'), kit.modelo_java(lista, tex_key, W, H))
        kit.salvar_json(os.path.join(JAVA, 'items', conjunto, f'{nome}_{q}.json'), kit.item_java(f'corona:entity/{conjunto}/{nome}_{q}'))
    for q, lista in olhos.items():
        if not lista:
            continue
        kit.salvar_json(os.path.join(JAVA, 'models', 'entity', conjunto, f'{nome}_olhos_{q}.json'), kit.modelo_java(lista, tex_key, W, H))
        kit.salvar_json(os.path.join(JAVA, 'items', conjunto, f'{nome}_olhos_{q}.json'), kit.item_java(f'corona:entity/{conjunto}/{nome}_olhos_{q}'))
    # Bedrock: entidade (geometria por ossos + animacoes) e textura
    geo, anim = kit.entidade_bedrock(nome, quadros, olhos, W, H)
    os.makedirs(os.path.join(BEDROCK, 'models', 'entity'), exist_ok=True)
    os.makedirs(os.path.join(BEDROCK, 'animations'), exist_ok=True)
    os.makedirs(os.path.join(BEDROCK, 'textures', 'entity', 'corona'), exist_ok=True)
    kit.salvar_json(os.path.join(BEDROCK, 'models', 'entity', f'corona_{nome}.geo.json'), geo)
    kit.salvar_json(os.path.join(BEDROCK, 'animations', f'corona_{nome}.animation.json'), anim)
    Image.fromarray(atlas, 'RGBA').save(os.path.join(BEDROCK, 'textures', 'entity', 'corona', f'{nome}.png'), optimize=True)
    # Rosto do susto (fonte): 512x256, rosto no meio com vinheta preta
    if M.get('rosto'):
        rosto(nome, M)
    # previas
    oi, og = olhos.get('idle', []), olhos.get('grito', [])
    ps = [kit.previa(quadros['idle'] + oi, atlas, 0, 0), kit.previa(quadros['idle'] + oi, atlas, 38, 12),
          kit.previa(quadros['walk1'] + oi, atlas, 180, 0), kit.previa(quadros['grito'] + og, atlas, 20, 8)]
    kit.folha(ps).save(os.path.join(PREV, f'prev_{nome}.png'))
    Image.fromarray(atlas, 'RGBA').resize((W * 2, H * 2), Image.NEAREST).save(os.path.join(PREV, f'atlas_{nome}.png'))
    print('ok', nome, 'atlas', W, 'x', H, 'cubos', len(base), 'quadros', list(quadros))


def rosto(nome, M):
    """Imagem do susto: o rosto grande (plano da frente da cabeca) + vinheta. 256x256: o Minecraft so aceita letra
    de fonte ate 256x256 px (maior que isso vira a caixinha branca). As laterais pretas da tela vem de outra letra."""
    cn, de, ate = M['rosto_cubo']
    W, H = 256, 256
    s = (np.arange(W) + 0.5) / W
    t = (np.arange(H) + 0.5) / H
    S, T = np.meshgrid(s, t)
    # rosto ocupa o centro (altura toda, largura = altura * proporcao do rosto)
    w, h = ate[0] - de[0], ate[1] - de[1]
    larg = (w / h) * H / W          # largura do rosto em fracao da imagem (altura toda = 1)
    cx = 0.5
    sl = (S - cx) / larg + 0.5      # 0..1 dentro do rosto
    dentro_r = (sl >= 0) & (sl <= 1)
    P = dict(x=de[0] + np.clip(sl, 0, 1) * w, y=ate[1] - T * h, z=np.full_like(S, ate[2]), s=np.clip(sl, 0, 1), t=T, n=(0, 0, 1),
             face='south', pele='rosto', nome=cn, grupo=cn, w=w, h=h, d=ate[2] - de[2], de=np.array(de), ate=np.array(ate))
    rgb, _ = M['rosto'](P)
    rgb = np.clip(np.asarray(rgb, float), 0, 1)
    # fora do rosto: preto; vinheta escura nas bordas; leve brilho nos olhos ja vem da textura
    vin = ss(0.6, 1.05, elipse(S, T, 0.5, 0.5, 0.5 * larg * 1.15 + 0.08, 0.75))
    rgb = np.clip(rgb * 1.3, 0, 1) * (1 - vin)[..., None]
    rgb = np.where(dentro_r[..., None], rgb, 0)
    # contraste, granulado de filme e riscos (imagem de susto)
    rgb = np.clip((rgb - 0.5) * 1.25 + 0.5, 0, 1)
    rng = np.random.default_rng(1 + len(nome))
    grao = rng.normal(0, 0.045, S.shape)
    risco = (rng.random(S.shape) < 0.0006) * 1.0
    rgb = np.clip(rgb + grao[..., None] - risco[..., None] * 0.5, 0, 1)
    img = Image.fromarray((np.dstack([rgb, np.ones(S.shape)]) * 255 + 0.5).astype(np.uint8), 'RGBA')
    img = img.filter(ImageFilter.GaussianBlur(0.5))
    d = os.path.join(JAVA, 'textures', 'font')
    os.makedirs(d, exist_ok=True)
    img.save(os.path.join(d, f'susto_{nome}.png'), optimize=True)
    img.save(os.path.join(PREV, f'rosto_{nome}.png'))


def fonte():
    """Tres fontes com o mesmo rosto em tamanhos diferentes (susto em 3 tempos: longe, perto, na cara).
    Cada letra tem no maximo 256x256 px (limite do Minecraft). \uE0F0 e um quadrado preto para tapar o resto da tela
    e \uF801 e um espaco negativo de 1 px (tira a fresta entre as letras). Nao Chore: \uE000..; Backrooms: \uE010.."""
    d = os.path.join(JAVA, 'textures', 'font')
    os.makedirs(d, exist_ok=True)
    Image.new('RGBA', (16, 16), (0, 0, 0, 255)).save(os.path.join(d, 'preto.png'))
    for nomef, alt in (('susto_p', 44), ('susto_m', 84), ('susto', 124)):
        prov = [{'type': 'space', 'advances': {'\uf801': -1}}]
        for nome, ch in glifos().items():
            prov.append({'type': 'bitmap', 'file': f'corona:font/susto_{nome}.png', 'ascent': alt // 2, 'height': alt, 'chars': [ch]})
        prov.append({'type': 'bitmap', 'file': 'corona:font/preto.png', 'ascent': alt // 2, 'height': alt, 'chars': ['\ue0f0']})
        kit.salvar_json(os.path.join(JAVA, 'font', f'{nomef}.json'), {'providers': prov})


def glifos():
    """Tabela nome -> caractere da fonte (so quem tem rosto do susto; a ordem e a do dicionario). Bate com Partida.glifo no Java."""
    out = {}
    for conjunto, (ms, base) in CONJUNTOS.items():
        k = 0
        for nome, M in ms.items():
            if M.get('rosto'):
                out[nome] = chr(base + k)
                k += 1
    return out


# entidade vanilla que o Bedrock "empresta" para cada monstro (nunca nascem sozinhas nos mundos do servidor)
EMPRESTADAS = {'vigia': 'zombie_horse', 'chorona': 'mule', 'faminto': 'sniffer', 'duplo': 'skeleton_horse', 'bacteria': 'happy_ghast', 'uivador': 'zoglin',
               'cao': 'panda', 'festeiro': 'ocelot', 'sombra': 'mooshroom', 'corredor': 'camel', 'afogado': 'axolotl'}


def entidades_bedrock():
    """Client entities, render controllers e manifest do pacote 3D do Bedrock."""
    rcs = {'format_version': '1.8.0', 'render_controllers': {}}
    todos = {}
    for ms, _ in CONJUNTOS.values():
        todos.update(ms)
    for nome, ent in EMPRESTADAS.items():
        M = todos[nome]
        rcs['render_controllers'][f'controller.render.corona_{nome}'] = {'geometry': 'geometry.default', 'materials': [{'*': 'material.default'}], 'textures': ['texture.default']}
        ce = {'format_version': '1.10.0', 'minecraft:client_entity': {'description': {
            'identifier': f'minecraft:{ent}',
            'min_engine_version': '1.16.0',
            'materials': {'default': 'entity_alphatest'},
            'textures': {'default': f'textures/entity/corona/{nome}'},
            'geometry': {'default': f'geometry.corona.{nome}'},
            'animations': {'idle': f'animation.corona.{nome}.idle', 'walk': f'animation.corona.{nome}.walk', 'grito': f'animation.corona.{nome}.grito', 'respira': f'animation.corona.{nome}.respira'},
            'scripts': {'scale': str(M['escala']), 'animate': ['respira', {'idle': '!q.is_baby'}, 'walk', {'grito': 'q.is_baby'}]},
            'render_controllers': [f'controller.render.corona_{nome}'],
            'spawn_egg': {'texture': 'spawn_egg', 'texture_index': 0},
        }}}
        kit.salvar_json(os.path.join(BEDROCK, 'entity', f'{ent}.entity.json'), ce)
    kit.salvar_json(os.path.join(BEDROCK, 'render_controllers', 'corona.render_controllers.json'), rcs)


if __name__ == '__main__':
    so = sys.argv[1:]
    for conjunto, (ms, _) in CONJUNTOS.items():
        for nome, M in ms.items():
            if so and nome not in so:
                continue
            gerar(nome, M, conjunto)
    fonte()
    entidades_bedrock()
    print('glifos', {n: '\\u%04X' % ord(c) for n, c in glifos().items()})
