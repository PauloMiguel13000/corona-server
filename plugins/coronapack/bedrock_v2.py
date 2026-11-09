"""Pacote Bedrock v2 (08/11): tudo o que o Java ve, o celular tambem ve.

Gera, a partir do pacote Java (pacote/java/assets/corona):
  * armaduras tematicas: icones 16x16 (Java e Bedrock), item_model Java (corona:item/armaduras/<tema>_<peca>), texturas de
    armadura no Bedrock (textures/models/armor/corona_<tema>_1/2.png) e attachables por material (ferro/diamante/netherite);
  * espadas das reliquias: icone 2D no Bedrock (fica na mao como ferramenta);
  * props das habilidades (corona:props/*) e coroas dos chefes (corona:item/chefes/*): geometria Bedrock convertida do modelo
    Java + attachable, para a extensao GeyserDisplayEntity mostrar os item displays em 3D no celular;
  * custom_mappings/corona-v2.json do Geyser.
Saida: out_v2/corona.mcpack (a partir do mcpack atual), out_v2/java/ (arquivos a juntar no corona-pack.zip),
out_v2/corona-v2.json (vai em plugins/Geyser-Spigot/custom_mappings/).
"""
import io
import json
import os
import shutil
import sys
import zipfile

import numpy as np
from PIL import Image

AQUI = os.path.dirname(os.path.abspath(__file__))
JAVA = os.path.join(AQUI, 'pacote', 'java', 'assets', 'corona')
MCPACK_BASE = os.path.join(AQUI, 'res', 'pack', 'corona.mcpack')
OUT = os.path.join(AQUI, 'out_v2')
BED = os.path.join(OUT, 'bedrock')
JOUT = os.path.join(OUT, 'java', 'assets', 'corona')
VERSAO = [1, 6, 0]

MATERIAIS_ARMADURA = {'leather': 'LEATHER', 'chainmail': 'CHAINMAIL', 'golden': 'GOLDEN', 'iron': 'IRON', 'diamond': 'DIAMOND', 'netherite': 'NETHERITE'}
PROTECAO = {  # valores de armadura vanilla
    'leather': {'helmet': 1, 'chestplate': 3, 'leggings': 2, 'boots': 1},
    'chainmail': {'helmet': 2, 'chestplate': 5, 'leggings': 4, 'boots': 1},
    'golden': {'helmet': 2, 'chestplate': 5, 'leggings': 3, 'boots': 1},
    'iron': {'helmet': 2, 'chestplate': 6, 'leggings': 5, 'boots': 2},
    'diamond': {'helmet': 3, 'chestplate': 8, 'leggings': 6, 'boots': 3},
    'netherite': {'helmet': 3, 'chestplate': 8, 'leggings': 6, 'boots': 3},
}
PECAS = {'helmet': 'elmo', 'chestplate': 'peitoral', 'leggings': 'calca', 'boots': 'botas'}
MATERIAIS_ESPADA = ['wooden', 'stone', 'golden', 'iron', 'diamond', 'netherite']

sys.path.insert(0, os.path.join(AQUI, '..', 'modelos'))
try:
    from armaduras_tematicas import R as TEMAS_R  # cores por tema
except Exception:
    TEMAS_R = {}


def hexrgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def cores_tema(tema):
    t = TEMAS_R.get(tema.upper())
    if t and t[4] and t[5]:
        return hexrgb(t[4]), hexrgb(t[5])
    # fallback: cores medias da textura
    im = np.array(Image.open(os.path.join(JAVA, 'textures/entity/equipment/humanoid', tema + '.png')).convert('RGBA'))
    px = im[im[..., 3] > 0][..., :3]
    base = tuple(int(v) for v in np.median(px, axis=0))
    return base, tuple(min(255, int(v * 1.4 + 30)) for v in base)


# ------------------------------------------------------------------ icones 16x16

MASCARAS = {
    'helmet': [
        "................",
        "................",
        "................",
        ".....######.....",
        "....########....",
        "...##########...",
        "...##########...",
        "...##########...",
        "...###....###...",
        "...###....###...",
        "...###....###...",
        "...###....###...",
        "...###....###...",
        "................",
        "................",
        "................"],
    'chestplate': [
        "................",
        "................",
        "...##......##...",
        "..###......###..",
        "..############..",
        "..############..",
        "..############..",
        "..############..",
        "...##########...",
        "...##########...",
        "...##########...",
        "...##########...",
        "...##########...",
        "...##########...",
        "................",
        "................"],
    'leggings': [
        "................",
        "................",
        "...##########...",
        "...##########...",
        "...##########...",
        "...####..####...",
        "...####..####...",
        "...####..####...",
        "...####..####...",
        "...####..####...",
        "...####..####...",
        "...####..####...",
        "...####..####...",
        "...####..####...",
        "................",
        "................"],
    'boots': [
        "................",
        "................",
        "................",
        "................",
        "...###....###...",
        "...###....###...",
        "...###....###...",
        "...###....###...",
        "...###....###...",
        "..#####..#####..",
        ".######..######.",
        ".######..######.",
        ".######..######.",
        "................",
        "................",
        "................"],
    'sword': [
        "..............##",
        ".............###",
        "............###.",
        "...........###..",
        "..........###...",
        ".........###....",
        "........###.....",
        ".......###......",
        "..#...###.......",
        "...#.###........",
        "....###.........",
        "...####.........",
        "..##..##........",
        ".##....#........",
        "##..............",
        "................"],
    'mao': [
        "................",
        "......#.#.#.....",
        ".....##.#.#.#...",
        ".....##.#.#.#...",
        ".....#########..",
        ".#...#########..",
        ".##..#########..",
        "..##.#########..",
        "...###########..",
        "....##########..",
        ".....########...",
        "......######....",
        "......######....",
        ".....########...",
        ".....########...",
        "................"],
}


def icone(tipo, cor, brilho, seed=0):
    m = MASCARAS[tipo]
    img = np.zeros((16, 16, 4), np.uint8)
    rng = np.random.default_rng(seed)
    esc = tuple(max(0, int(v * 0.55)) for v in cor)
    for y in range(16):
        for x in range(16):
            if m[y][x] != '#':
                continue
            # borda escura, miolo com leve textura, brilho na diagonal superior-esquerda
            borda = any(not (0 <= y + dy < 16 and 0 <= x + dx < 16 and m[y + dy][x + dx] == '#') for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if borda:
                c = esc
            else:
                f = 1 + 0.12 * (rng.random() - 0.5)
                c = tuple(min(255, int(v * f)) for v in cor)
                if (x + y) % 7 == 2:
                    c = tuple(min(255, int(v * 0.6 + b * 0.4)) for v, b in zip(c, brilho))
            img[y, x] = (*c, 255)
    if tipo == 'sword':
        # lamina clara, cabo escuro
        for y in range(16):
            for x in range(16):
                if m[y][x] == '#' and x + y >= 15 and y <= 9:
                    img[y, x, :3] = [min(255, int(v * 0.5 + 128)) for v in brilho]
    return Image.fromarray(img, 'RGBA')


# ------------------------------------------------------------------ Java modelo -> Bedrock geometria

def geometria_de_modelo(ident, modelo):
    """Converte um modelo Java (elements com from/to/faces/rotation, 1 textura) numa geometria Bedrock.
    Centro do modelo Java (8,8,8) vai para o pivo (0,8,0) do osso 'geyser_z' (osso da extensao GeyserDisplayEntity)."""
    tw, th = modelo.get('texture_size', [16, 16])
    cubes = []
    for e in modelo['elements']:
        fx, fy, fz = e['from']
        tx, ty, tz = e['to']
        w, h, d = tx - fx, ty - fy, tz - fz
        cube = {'origin': [round(8 - tx, 4), round(fy, 4), round(fz - 8, 4)], 'size': [round(w, 4), round(h, 4), round(d, 4)], 'uv': {}}
        for face, dados in e.get('faces', {}).items():
            uv = dados.get('uv')
            if not uv:
                continue
            u0, v0, u1, v1 = uv
            cube['uv'][face] = {'uv': [round(u0 * tw / 16, 3), round(v0 * th / 16, 3)], 'uv_size': [round((u1 - u0) * tw / 16, 3), round((v1 - v0) * th / 16, 3)]}
        r = e.get('rotation')
        if r and r.get('angle'):
            ox, oy, oz = r['origin']
            ang = r['angle']
            rot = [0.0, 0.0, 0.0]
            eixo = r['axis']
            rot['xyz'.index(eixo)] = -ang if eixo in 'xy' else ang
            cube['pivot'] = [round(8 - ox, 4), round(oy, 4), round(oz - 8, 4)]
            cube['rotation'] = rot
        cubes.append(cube)
    return {
        'format_version': '1.16.0',
        'minecraft:geometry': [{
            'description': {'identifier': ident, 'texture_width': tw, 'texture_height': th,
                            'visible_bounds_width': 6, 'visible_bounds_height': 6, 'visible_bounds_offset': [0, 0.5, 0]},
            'bones': [{'name': 'geyser_z', 'pivot': [0, 8, 0], 'cubes': cubes}],
        }],
    }


def attachable_armadura(ident, textura, geometria, camada_var):
    return {"format_version": "1.10.0", "minecraft:attachable": {"description": {
        "identifier": ident,
        "materials": {"default": "armor", "enchanted": "armor_enchanted"},
        "textures": {"default": textura, "enchanted": "textures/misc/enchanted_actor_glint"},
        "geometry": {"default": geometria},
        "scripts": {"parent_setup": f"variable.{camada_var} = 0.0;"},
        "render_controllers": ["controller.render.armor"]}}}


def attachable_prop(ident, textura, geometria):
    return {"format_version": "1.10.0", "minecraft:attachable": {"description": {
        "identifier": ident,
        "materials": {"default": "entity_alphatest", "enchanted": "entity_alphatest_glint"},
        "textures": {"default": textura, "enchanted": "textures/misc/enchanted_actor_glint"},
        "geometry": {"default": geometria},
        "scripts": {"parent_setup": "variable.helmet_layer_visible = 0.0;"},
        "render_controllers": ["controller.render.armor"]}}}


def escrever_json(caminho, dado):
    os.makedirs(os.path.dirname(caminho), exist_ok=True)
    with open(caminho, 'w', encoding='utf-8') as f:
        json.dump(dado, f, ensure_ascii=False, separators=(',', ':'))


def main():
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    os.makedirs(BED)
    with zipfile.ZipFile(MCPACK_BASE) as z:
        z.extractall(BED)
    item_tex = json.load(open(os.path.join(BED, 'textures', 'item_texture.json'), encoding='utf-8'))
    mapeamento = {}  # java item -> lista de definicoes

    def definicao(java_item, modelo, bid, nome, icone_nome, protecao=None, handheld=False):
        d = {"type": "definition", "model": modelo, "bedrock_identifier": bid, "display_name": nome,
             "bedrock_options": {"icon": icone_nome, "creative_category": "equipment"}}
        if protecao is not None:
            d["bedrock_options"]["protection_value"] = protecao
        if handheld:
            d["bedrock_options"]["display_handheld"] = True
        mapeamento.setdefault(java_item, []).append(d)

    def icone_bedrock(nome, img):
        p = os.path.join(BED, 'textures', 'items', 'corona', 'v2', nome + '.png')
        os.makedirs(os.path.dirname(p), exist_ok=True)
        img.save(p)
        item_tex['texture_data'][nome] = {'textures': f'textures/items/corona/v2/{nome}'}

    # ---- armaduras tematicas
    temas = sorted(f[:-4] for f in os.listdir(os.path.join(JAVA, 'textures/entity/equipment/humanoid')) if f.endswith('.png'))
    temas = [t for t in temas if t not in ('coroa', 'herobrine')]
    n_arm = 0
    for tema in temas:
        cor, brilho = cores_tema(tema)
        for camada, pasta in ((1, 'humanoid'), (2, 'humanoid_leggings')):
            src = os.path.join(JAVA, 'textures/entity/equipment', pasta, tema + '.png')
            if os.path.exists(src):
                dst = os.path.join(BED, 'textures/models/armor', f'corona_{tema}_{camada}.png')
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                shutil.copy(src, dst)
        for peca, pt in PECAS.items():
            ic = icone(peca, cor, brilho, seed=hash(tema + peca) & 0xffff)
            # Java: textura, modelo e item
            tp = os.path.join(JOUT, 'textures/item/armaduras', f'{tema}_{pt}.png')
            os.makedirs(os.path.dirname(tp), exist_ok=True)
            ic.save(tp)
            escrever_json(os.path.join(JOUT, 'models/item/armaduras', f'{tema}_{pt}.json'),
                          {"parent": "minecraft:item/generated", "textures": {"layer0": f"corona:item/armaduras/{tema}_{pt}"}})
            escrever_json(os.path.join(JOUT, 'items/item/armaduras', f'{tema}_{pt}.json'),
                          {"model": {"type": "minecraft:model", "model": f"corona:item/armaduras/{tema}_{pt}"}})
            # Bedrock: icone + attachable por material
            icone_bedrock(f'corona_arm_{tema}_{pt}', ic)
            camada_tex = 2 if peca == 'leggings' else 1
            var = {'helmet': 'helmet_layer_visible', 'chestplate': 'chest_layer_visible', 'leggings': 'leg_layer_visible', 'boots': 'boot_layer_visible'}[peca]
            # elmo 3D (modelos/elmos3d.py -> pacote/elmo3d): geometria propria no Bedrock e select "head" no Java
            e3d = os.path.join(AQUI, 'pacote', 'elmo3d')
            geo3d = os.path.join(e3d, 'bedrock', 'models', 'entity', f'corona_elmo3d_{tema}.geo.json')
            usa3d = peca == 'helmet' and os.path.exists(geo3d)
            if usa3d:
                shutil.copy(geo3d, os.path.join(BED, 'models', 'entity', os.path.basename(geo3d)))
                for rel in (f'models/item/elmo3d/{tema}.json', f'textures/item/elmo3d/{tema}.png', f'items/item/armaduras/{tema}_elmo.json'):
                    src3d = os.path.join(e3d, 'java', 'assets', 'corona', rel)
                    os.makedirs(os.path.dirname(os.path.join(JOUT, rel)), exist_ok=True)
                    shutil.copy(src3d, os.path.join(JOUT, rel))
                tex3d = os.path.join(e3d, 'bedrock', 'textures', 'models', 'armor', f'corona_{tema}_1.png')
                shutil.copy(tex3d, os.path.join(BED, 'textures/models/armor', f'corona_{tema}_1.png'))
            for mat in MATERIAIS_ARMADURA:
                bid = f'corona:arm_{tema}_{pt}_{mat}'
                escrever_json(os.path.join(BED, 'attachables', f'arm_{tema}_{pt}_{mat}.json'),
                              attachable_armadura(bid, f'textures/models/armor/corona_{tema}_{camada_tex}',
                                                  f'geometry.corona.elmo3d.{tema}' if usa3d else f'geometry.player.armor.{peca}', var))
                definicao(f'minecraft:{mat}_{peca}', f'corona:item/armaduras/{tema}_{pt}', bid,
                          f'{pt.capitalize()} {tema}', f'corona_arm_{tema}_{pt}', PROTECAO[mat][peca])
                n_arm += 1
    # ---- espadas das reliquias (icone 2D no Bedrock)
    n_esp = 0
    for tema in temas:
        if not os.path.exists(os.path.join(JAVA, 'models/item/reliquias', f'espada_{tema}.json')):
            continue
        cor, brilho = cores_tema(tema)
        ic = icone('sword', cor, brilho, seed=hash(tema) & 0xffff)
        icone_bedrock(f'corona_espada_{tema}', ic)
        for mat in MATERIAIS_ESPADA:
            definicao(f'minecraft:{mat}_sword', f'corona:item/reliquias/espada_{tema}', f'corona:espada_{tema}_{mat}',
                      f'Espada {tema}', f'corona_espada_{tema}', handheld=True)
            n_esp += 1
    # ---- props das habilidades e coroas dos chefes (3D pela extensao)
    n_prop = 0

    def prop(java_modelo_chave, arq_modelo, bid, textura_java, nome):
        nonlocal n_prop
        modelo = json.load(open(arq_modelo, encoding='utf-8'))
        geo_id = 'geometry.corona.' + bid.split(':')[1]
        escrever_json(os.path.join(BED, 'models/entity/corona_v2', bid.split(':')[1] + '.geo.json'), geometria_de_modelo(geo_id, modelo))
        # textura: corona:item/props/x -> textures/item/props/x.png
        rel = textura_java.split(':', 1)[1]
        src = os.path.join(JAVA, 'textures', rel + '.png')
        tex_bed = 'textures/entity/corona_v2/' + rel.replace('/', '_')
        dst = os.path.join(BED, tex_bed + '.png')
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copy(src, dst)
        escrever_json(os.path.join(BED, 'attachables', bid.split(':')[1] + '.json'), attachable_prop(bid, tex_bed, geo_id))
        ic = Image.open(src).convert('RGBA').resize((16, 16), Image.LANCZOS)
        icone_bedrock('corona_' + bid.split(':')[1], ic)
        definicao('minecraft:leather_helmet', java_modelo_chave, bid, nome, 'corona_' + bid.split(':')[1], 1)
        n_prop += 1

    for f in sorted(os.listdir(os.path.join(JAVA, 'models/props'))):
        if not f.endswith('.json'):
            continue
        nome = f[:-5]
        m = json.load(open(os.path.join(JAVA, 'models/props', f), encoding='utf-8'))
        prop(f'corona:props/{nome}', os.path.join(JAVA, 'models/props', f), f'corona:prop_{nome}', m['textures']['0'], 'Prop ' + nome)
    for f in sorted(os.listdir(os.path.join(JAVA, 'models/item/chefes'))):
        if not f.endswith('.json'):
            continue
        nome = f[:-5]
        m = json.load(open(os.path.join(JAVA, 'models/item/chefes', f), encoding='utf-8'))
        prop(f'corona:item/chefes/{nome}', os.path.join(JAVA, 'models/item/chefes', f), f'corona:coroa_chefe_{nome}', m['textures']['0'], 'Coroa ' + nome)
    # ---- Fabrica Alegria: maos da Luva de Gancho na mao do jogador (icone proprio no celular)
    for cor, rgb, brilho in (('azul', (40, 110, 240), (150, 200, 255)), ('vermelha', (220, 40, 40), (255, 160, 150))):
        ic = icone('mao', rgb, brilho, seed=7 if cor == 'azul' else 9)
        icone_bedrock(f'corona_fab_mao_{cor}', ic)
        definicao('minecraft:carrot_on_a_stick', f'corona:props/fab_mao_{cor}_f0', f'corona:fab_mao_{cor}', f'Mao {cor}', f'corona_fab_mao_{cor}', handheld=True)
    # ---- efeitos das habilidades (emissores de particulas do Bedrock)
    import fx_bedrock
    n_fx = fx_bedrock.gerar(BED, JOUT, escrever_json, item_tex, definicao)
    # ---- fecha o pacote
    escrever_json(os.path.join(BED, 'textures', 'item_texture.json'), item_tex)
    man = json.load(open(os.path.join(BED, 'manifest.json'), encoding='utf-8'))
    man['header']['version'] = VERSAO
    man['header']['description'] = 'Corona Server: sons, monstros 3D, armaduras e habilidades (v2)'
    for mod in man['modules']:
        mod['version'] = VERSAO
    escrever_json(os.path.join(BED, 'manifest.json'), man)
    saida = os.path.join(OUT, 'corona.mcpack')
    with zipfile.ZipFile(saida, 'w', zipfile.ZIP_DEFLATED) as z:
        for raiz, _, arqs in os.walk(BED):
            for a in sorted(arqs):
                p = os.path.join(raiz, a)
                z.write(p, os.path.relpath(p, BED))
    escrever_json(os.path.join(OUT, 'corona-v2.json'), {"format_version": 2, "items": mapeamento})
    total = sum(len(v) for v in mapeamento.values())
    print(f'temas {len(temas)}; armaduras {n_arm}, espadas {n_esp}, props/coroas {n_prop}, fx {n_fx}; definicoes {total}; mcpack {os.path.getsize(saida) // 1024} KB')


if __name__ == '__main__':
    main()
