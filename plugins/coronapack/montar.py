"""Monta os pacotes: Java (corona-pack.zip) e Bedrock (corona.mcpack) com os sons e as pinturas dos monstros."""
import json, os, shutil, zipfile
J = 'pacote/java'; B = 'pacote/bedrock'; OGG = '/tmp/claude-0/sons/ogg2'
NOVOS = {  # nome: (pasta, distancia, legenda)
    'estrondo': ('hotel', 64, 'O Estrondo se aproxima'), 'retorno': ('hotel', 64, 'O Retorno grita'),
    'figura': ('hotel', 40, 'A Figura respira'), 'perseguidor': ('hotel', 56, 'O Perseguidor borbulha'),
    'psst': ('hotel', 8, 'Psst!'), 'sussurro_ataque': ('hotel', 16, 'O Sussurro morde'),
    'passos': ('hotel', 24, 'Passos no corredor'),
    'cao_rosnado': ('backrooms', 40, 'Cão rosna'), 'cao_ataque': ('backrooms', 56, 'Cão ataca'),
    'festeiro_risada': ('backrooms', 24, 'Festeiro ri'),
}
OGG3 = '/tmp/claude-0/sons/ogg3'
NAOCHORE = {  # nome: (distancia, legenda)
    'respiracao': (12, 'Respiração atrás de você'), 'choro': (48, 'Alguém chora ao longe'), 'grito_vigia': (64, 'O Vigia grita'),
    'grito_chorona': (64, 'A Chorona berra'), 'grito_duplo': (64, 'O Duplo grita'), 'rosnado': (48, 'O Faminto rosna'), 'arranhao': (16, 'Arranhões'),
    'risada': (48, 'Uma risada de criança'), 'batida_porta': (24, 'Batidas na porta'), 'sussurro_nome': (8, 'Um sussurro'),
    'correndo': (32, 'Passos correndo'), 'batimento': (4, 'Seu coração'), 'zumbido_noite': (6, 'Zumbido'), 'nina': (24, 'Uma caixinha de música'),
    'estatica': (8, 'Estática'), 'maos': (12, 'Mãos saem do chão'), 'porta_rangendo': (24, 'A porta range'), 'voce_chorou': (8, 'Você chorou'),
}
sj = json.load(open(f'{J}/assets/minecraft/sounds.json'))
sd = json.load(open(f'{B}/sounds/sound_definitions.json'))
for nome, (pasta, dist, leg) in NOVOS.items():
    src = f'{OGG}/{nome}.ogg'
    if os.path.exists(src):
        for dst in (f'{J}/assets/minecraft/sounds/{pasta}', f'{B}/sounds/{pasta}'):
            os.makedirs(dst, exist_ok=True)
            shutil.copy(src, f'{dst}/{nome}.ogg')
    chave = f'{pasta}.{nome}'
    sj[chave] = {'subtitle': leg, 'sounds': [{'name': f'{pasta}/{nome}', 'attenuation_distance': dist}]}
    d = {'category': 'hostile', 'min_distance': 1.0, 'max_distance': float(dist), 'sounds': [{'name': f'sounds/{pasta}/{nome}', 'volume': 1.0}]}
    sd['sound_definitions'][chave] = d
    sd['sound_definitions']['minecraft:' + chave] = d
for nome, (dist, leg) in NAOCHORE.items():
    src = f'{OGG3}/{nome}.ogg'
    if os.path.exists(src):
        for dst in (f'{J}/assets/minecraft/sounds/naochore', f'{B}/sounds/naochore'):
            os.makedirs(dst, exist_ok=True)
            shutil.copy(src, f'{dst}/{nome}.ogg')
    chave = f'naochore.{nome}'
    sj[chave] = {'subtitle': leg, 'sounds': [{'name': f'naochore/{nome}', 'attenuation_distance': dist}]}
    d = {'category': 'hostile', 'min_distance': 1.0, 'max_distance': float(dist), 'sounds': [{'name': f'sounds/naochore/{nome}', 'volume': 1.0}]}
    sd['sound_definitions'][chave] = d
    sd['sound_definitions']['minecraft:' + chave] = d
# monstros 3D do Bedrock (entidades emprestadas redesenhadas): entra tudo de pacote/bedrock_3d
B3 = 'pacote/bedrock_3d'
if os.path.isdir(B3):
    for raiz, _, arqs in os.walk(B3):
        for a in arqs:
            src = os.path.join(raiz, a)
            dst = os.path.join(B, os.path.relpath(src, B3))
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copy(src, dst)
json.dump(sj, open(f'{J}/assets/minecraft/sounds.json', 'w'), ensure_ascii=False, indent=1)
json.dump(sd, open(f'{B}/sounds/sound_definitions.json', 'w'), ensure_ascii=False, indent=1)
mc = json.load(open(f'{J}/pack.mcmeta'))
mc['pack']['description'] = '§e§lCorona Server §7- monstros 3D, sustos e sons dos minigames'
json.dump(mc, open(f'{J}/pack.mcmeta', 'w'), ensure_ascii=False)
man = json.load(open(f'{B}/manifest.json'))
import time as _t
_v = [1, 2, 0]  # FIXO: o celular ja tem a 1.2.0 guardada; versao nova obriga a baixar pelo NetherNet, que trava em dados moveis
man['header']['version'] = _v
man['modules'][0]['version'] = list(_v)
man['header']['description'] = 'Sons, sustos e monstros dos minigames do Corona Server'
json.dump(man, open(f'{B}/manifest.json', 'w'), ensure_ascii=False, indent=1)


def zipar(pasta, saida):
    with zipfile.ZipFile(saida, 'w', zipfile.ZIP_DEFLATED) as z:
        for raiz, _, arqs in sorted(os.walk(pasta)):
            for a in sorted(arqs):
                caminho = os.path.join(raiz, a)
                info = zipfile.ZipInfo(os.path.relpath(caminho, pasta), (2026, 11, 5, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                z.writestr(info, open(caminho, 'rb').read())


zipar(J, 'res/pack/corona-pack.zip')
zipar(B, 'res/pack/corona.mcpack')
for f in ('res/pack/corona-pack.zip', 'res/pack/corona.mcpack'):
    print(f, os.path.getsize(f))
