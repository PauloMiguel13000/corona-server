"""Monta os pacotes SO das masmorras com os chefes 3D (18/11).

uso: PYTHONPATH=plugins/coronapack:ferramentas/modelos python3 ferramentas/modelos/chefes/empacotar.py <saida>
Sai em <saida>/:
  corona_masmorras3d_java-<sha10>.zip   -> plugins/CoronaPack/pacotes-site/ (o Java baixa ao entrar nas masmorras)
  corona_masmorras3d.mcpack             -> plugins/Geyser-Spigot/packs/ (o CoronaPack manda por link para o Bedrock)
  corona-masmorras3d.json               -> plugins/Geyser-Spigot/custom_mappings/
  corpos.json                           -> plugins/masmorras/res/ (pivos e pais das pecas, o plugin le)
"""
import hashlib
import json
import os
import shutil
import sys
import uuid
import zipfile

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)
SAIDA = sys.argv[1] if len(sys.argv) > 1 else '/tmp/masm3d_final'
TMP = SAIDA + '_tmp'
sys.argv = [sys.argv[0], TMP]
import partes  # noqa: E402
from PIL import Image  # noqa: E402

shutil.rmtree(TMP, ignore_errors=True)
shutil.rmtree(SAIDA, ignore_errors=True)
os.makedirs(SAIDA)
partes.gerar(TMP, list(partes.RIGS))


def zipar(pasta, destino, extras):
    with zipfile.ZipFile(destino, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for nome, dados in extras.items():
            zi = zipfile.ZipInfo(nome, (2026, 1, 1, 0, 0, 0))
            zi.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(zi, dados)
        for raiz, _, arqs in sorted(os.walk(pasta)):
            for a in sorted(arqs):
                f = os.path.join(raiz, a)
                zi = zipfile.ZipInfo(os.path.relpath(f, pasta).replace(os.sep, '/'), (2026, 1, 1, 0, 0, 0))
                zi.compress_type = zipfile.ZIP_DEFLATED
                z.writestr(zi, open(f, 'rb').read())


# ---- Java
mcmeta = json.dumps({'pack': {'description': '§6§lMasmorras §7- chefes 3D (Corona Server)', 'min_format': [97, 0], 'max_format': [199, 0]}})
jz = os.path.join(SAIDA, 'java.zip')
zipar(os.path.join(TMP, 'java'), jz, {'pack.mcmeta': mcmeta})
sha = hashlib.sha1(open(jz, 'rb').read()).hexdigest()
nome_java = f'corona_masmorras3d_java-{sha[:10]}.zip'
os.replace(jz, os.path.join(SAIDA, nome_java))

# ---- Bedrock
B = os.path.join(TMP, 'bedrock')
it = json.load(open(os.path.join(TMP, 'item_texture_masm.json')))
os.makedirs(os.path.join(B, 'textures', 'items', 'corona', 'masm'), exist_ok=True)
Image.new('RGBA', (16, 16), (0, 0, 0, 0)).save(os.path.join(B, 'textures', 'items', 'corona', 'masm', 'masm_vazio.png'))
it['masm_vazio'] = {'textures': 'textures/items/corona/masm/masm_vazio'}
json.dump({'resource_pack_name': 'corona_masmorras3d', 'texture_name': 'atlas.items', 'texture_data': it},
          open(os.path.join(B, 'textures', 'item_texture.json'), 'w'))
u = lambda s: str(uuid.uuid5(uuid.NAMESPACE_URL, 'corona-masmorras3d-' + s))
manifest = {'format_version': 2, 'header': {'name': 'Corona Masmorras 3D', 'description': 'Chefes 3D das masmorras do Corona Server',
                                            'uuid': u('header'), 'version': [1, 0, 0], 'min_engine_version': [1, 21, 0]},
            'modules': [{'type': 'resources', 'uuid': u('modulo'), 'version': [1, 0, 0]}]}
zipar(B, os.path.join(SAIDA, 'corona_masmorras3d.mcpack'), {'manifest.json': json.dumps(manifest, indent=1)})

# ---- Geyser: pecas + arma invisivel na mao do mob
mapa = json.load(open(os.path.join(TMP, 'corona-masmorras3d.json')))
for item in ('bow', 'diamond_pickaxe', 'netherite_axe', 'netherite_sword', 'golden_sword', 'trident'):
    mapa['items'].setdefault(f'minecraft:{item}', []).append({
        'type': 'definition', 'model': 'corona:masmorras/vazio', 'bedrock_identifier': f'corona:masm_vazio_{item}',
        'display_name': 'Arma do chefe', 'bedrock_options': {'icon': 'masm_vazio', 'display_handheld': True}})
json.dump(mapa, open(os.path.join(SAIDA, 'corona-masmorras3d.json'), 'w'))
shutil.copy(os.path.join(TMP, 'corpos.json'), os.path.join(SAIDA, 'corpos.json'))
shutil.copy(os.path.join(TMP, 'previa_pecas.png'), os.path.join(SAIDA, 'previa_pecas.png'))
print('java', nome_java, sha)
for f in sorted(os.listdir(SAIDA)):
    print(f, os.path.getsize(os.path.join(SAIDA, f)))
