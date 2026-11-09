"""Coroas dos chefes das masmorras (Fase 3): um adorno 3D proprio por chefe, que flutua em cima da cabeca dele
(ItemDisplay carona). 14 moldes (coroa, chifres, caveira, capuz, halo, cristais, chamas, tentaculos, tricornio, mascara,
engrenagem, asas, lanterna, folhas), cada um pintado com as cores do tema. Java: modelo de item corona:item/chefes/<tema>.
No Bedrock aparece o item base escolhido por tema (cabecas e blocos 3D do jogo).

Uso: python3 -I coroas_chefes.py [ids...]  -> escreve no pacote Java + previas em /tmp/claude-0/modelos/coroas
"""
import json, math, os, sys
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'skins', 'monstros'))
from base import fbm, ridged, rampa, mix, C, ss, hash2  # noqa: E402
import kit  # noqa: E402
from kit import Cubo  # noqa: E402

AQUI = os.path.dirname(os.path.abspath(__file__))
JAVA = os.path.normpath(os.path.join(AQUI, '..', 'coronapack', 'pacote', 'java', 'assets', 'corona'))
PREV = '/tmp/claude-0/modelos/coroas'
os.makedirs(PREV, exist_ok=True)
TEMAS = json.load(open(os.path.join(AQUI, 'temas_reliquias.json'), encoding='utf-8'))

# tema -> (molde, item base no Bedrock)
MOLDE = {
    'CRIPTA': ('caveira', 'SKELETON_SKULL'), 'NINHO': ('tentaculos', 'COBWEB'), 'MINA': ('lanterna', 'LANTERN'),
    'PANTANO': ('capuz', 'CAULDRON'), 'GELO': ('cristais', 'BLUE_ICE'), 'DESERTO': ('coroa', 'GOLD_BLOCK'),
    'AFOGADA': ('coroa', 'PRISMARINE'), 'TORRE': ('halo', 'ENCHANTING_TABLE'), 'QUARTEL': ('chifres', 'ANVIL'),
    'SELVA': ('mascara', 'JUNGLE_LEAVES'), 'VENTOS': ('asas', 'WHITE_WOOL'), 'CASTELO': ('capuz', 'ANVIL'),
    'FORJA': ('chamas', 'MAGMA_BLOCK'), 'BASTIAO': ('coroa', 'GOLD_BLOCK'), 'WITHER': ('caveira', 'WITHER_SKELETON_SKULL'),
    'COVIL': ('chifres', 'NETHERRACK'), 'CIDADELA': ('halo', 'END_ROD'), 'PRISAO': ('engrenagem', 'IRON_BARS'),
    'SECRETA': ('halo', 'SEA_LANTERN'), 'CEMITERIO': ('capuz', 'SKELETON_SKULL'), 'ESGOTO': ('tentaculos', 'SLIME_BLOCK'),
    'ACAMPAMENTO': ('tricornio', 'CROSSBOW'), 'MOINHO': ('folhas', 'HAY_BLOCK'), 'FUNGOS': ('folhas', 'RED_MUSHROOM_BLOCK'),
    'COVIL_LOBOS': ('chifres', 'BONE_BLOCK'), 'URSO': ('caveira', 'SNOW_BLOCK'), 'BOSQUE_PALIDO': ('mascara', 'PALE_OAK_LEAVES'),
    'FAROL': ('tricornio', 'SEA_LANTERN'), 'LABORATORIO': ('cristais', 'BREWING_STAND'), 'CAMARA_TESTES': ('engrenagem', 'COPPER_BLOCK'),
    'FAZENDA': ('folhas', 'HAY_BLOCK'), 'CRATERA': ('chamas', 'MAGMA_BLOCK'), 'JARDIM': ('coroa', 'CHERRY_LEAVES'),
    'CATEDRAL': ('halo', 'GLOWSTONE'), 'TESOURO': ('coroa', 'GOLD_BLOCK'), 'COLISEU': ('coroa', 'IRON_BLOCK'),
    'TROVAO': ('cristais', 'LIGHTNING_ROD'), 'OBSERVATORIO': ('halo', 'END_ROD'), 'VIUVA': ('tentaculos', 'COBWEB'),
    'NAUFRAGIO': ('tricornio', 'SKELETON_SKULL'), 'MAUSOLEU': ('coroa', 'GOLD_BLOCK'), 'BIBLIOTECA': ('capuz', 'BOOKSHELF'),
    'VULCAO': ('chamas', 'MAGMA_BLOCK'), 'CIDADE_ANTIGA': ('tentaculos', 'SCULK'), 'GELEIRA': ('cristais', 'PACKED_ICE'),
    'SANGUE': ('coroa', 'REDSTONE_BLOCK'), 'RELOGIO': ('engrenagem', 'CLOCK'), 'LABIRINTO': ('chifres', 'BONE_BLOCK'),
    'CRIPTA_REAL': ('coroa', 'LAPIS_BLOCK'), 'DELTA': ('coroa', 'IRON_BLOCK'), 'CARMESIM': ('chifres', 'CRIMSON_NYLIUM'),
    'DISTORCIDO': ('folhas', 'WARPED_NYLIUM'), 'ALMAS': ('halo', 'SOUL_LANTERN'), 'OBSIDIANA': ('chifres', 'CRYING_OBSIDIAN'),
    'ILHAS': ('cristais', 'PURPUR_BLOCK'), 'NAVE': ('engrenagem', 'SHULKER_BOX'), 'TRONO': ('chifres', 'OBSIDIAN'),
    'CORO': ('folhas', 'CHORUS_FLOWER'), 'ABISMO': ('halo', 'END_ROD'),
}


def cor_tema(t):
    return np.array(t['rgb'], float) / 255


def escurecer(c, f):
    return np.clip(np.asarray(c) * f, 0, 1)


def clarear(c, f):
    c = np.asarray(c)
    return np.clip(c + (1 - c) * f, 0, 1)


# ------------------------------------------------------------------ moldes (cubos em unidades de modelo, centro x=8, z=8, base y=0)

def anel(cs, y0, y1, meia, esp, pele):
    cs.append(Cubo('anel_n', (8 - meia, y0, 8 - meia), (8 + meia, y1, 8 - meia + esp), pele))
    cs.append(Cubo('anel_s', (8 - meia, y0, 8 + meia - esp), (8 + meia, y1, 8 + meia), pele))
    cs.append(Cubo('anel_o', (8 - meia, y0, 8 - meia + esp), (8 - meia + esp, y1, 8 + meia - esp), pele))
    cs.append(Cubo('anel_l', (8 + meia - esp, y0, 8 - meia + esp), (8 + meia, y1, 8 + meia - esp), pele))


def molde(nome, rng):
    cs = []
    if nome == 'coroa':
        anel(cs, 0, 2.5, 5.5, 1.5, 'metal')
        alts = [4, 2.5, 5.5, 2.5, 4, 2.5, 5.5, 2.5]
        pos = [(-5.5, -5.5), (0, -5.5), (5.5, -5.5), (5.5, 0), (5.5, 5.5), (0, 5.5), (-5.5, 5.5), (-5.5, 0)]
        for i, (dx, dz) in enumerate(pos):
            h = alts[i]
            cs.append(Cubo(f'ponta{i}', (8 + dx - 0.8, 2.5, 8 + dz - 0.8), (8 + dx + 0.8, 2.5 + h, 8 + dz + 0.8), 'metal'))
            if h > 4:
                cs.append(Cubo(f'gema{i}', (8 + dx - 0.6, 2.5 + h, 8 + dz - 0.6), (8 + dx + 0.6, 3.7 + h, 8 + dz + 0.6), 'gema', emite=10))
        cs.append(Cubo('gema_frente', (7, 0.5, 2.3), (9, 2.2, 2.7), 'gema', emite=10))
    elif nome == 'chifres':
        for lado in (-1, 1):
            x = 8 + lado * 5
            cs.append(Cubo(f'base{lado}', (x - 1.5, 0, 6.5), (x + 1.5, 3, 9.5), 'chifre'))
            cs.append(Cubo(f'meio{lado}', (x - 1.2, 2.5, 6.8), (x + 1.2, 6.5, 9.2), 'chifre', rot=('z', -22.5 * lado, (x, 2.5, 8))))
            cs.append(Cubo(f'ponta{lado}', (x + lado * 1.2 - 0.9, 6, 7.1), (x + lado * 1.2 + 0.9, 9.5, 8.9), 'chifre_ponta', rot=('z', -45.0 * lado, (x + lado * 1.2, 6, 8))))
        cs.append(Cubo('faixa', (3, 0, 6.5), (13, 1.5, 9.5), 'metal'))
        cs.append(Cubo('gema', (7.2, 0.3, 6.1), (8.8, 1.3, 6.6), 'gema', emite=8))
    elif nome == 'caveira':
        cs.append(Cubo('cranio', (3, 0, 3), (13, 8, 13), 'osso'))
        cs.append(Cubo('queixo', (4, -2.5, 4), (12, 0, 10), 'osso'))
        for i in range(4):
            cs.append(Cubo(f'dente{i}', (4.6 + i * 1.9, -2.5, 3.2), (5.6 + i * 1.9, -0.8, 4.2), 'dente'))
        cs.append(Cubo('olho_e', (4.5, 3.5, 2.6), (7, 5.8, 3.1), 'olho', emite=12))
        cs.append(Cubo('olho_d', (9, 3.5, 2.6), (11.5, 5.8, 3.1), 'olho', emite=12))
        cs.append(Cubo('nariz', (7.3, 1.6, 2.7), (8.7, 3.0, 3.1), 'osso_escuro'))
        cs.append(Cubo('racha', (9.5, 6.5, 2.8), (10.3, 8.6, 3.05), 'osso_escuro'))
    elif nome == 'capuz':
        cs.append(Cubo('capuz', (2.5, 0, 2.5), (13.5, 10, 13.5), 'tecido'))
        cs.append(Cubo('bico', (4, 9.5, 4), (12, 13, 12), 'tecido'))
        cs.append(Cubo('ponta', (6, 12.5, 6), (10, 15.5, 10), 'tecido', rot=('x', 22.5, (8, 12.5, 8))))
        cs.append(Cubo('sombra', (3.2, 0.5, 2.3), (12.8, 7.5, 2.6), 'sombra'))
        cs.append(Cubo('olho_e', (4.8, 3.6, 2.1), (6.6, 4.9, 2.4), 'olho', emite=13))
        cs.append(Cubo('olho_d', (9.4, 3.6, 2.1), (11.2, 4.9, 2.4), 'olho', emite=13))
    elif nome == 'halo':
        anel(cs, 4, 5.2, 6.5, 1.3, 'luz')
        for i in range(4):
            a = i * math.pi / 2 + math.pi / 4
            x, z = 8 + math.cos(a) * 6.5, 8 + math.sin(a) * 6.5
            cs.append(Cubo(f'raio{i}', (x - 0.6, 3.4, z - 0.6), (x + 0.6, 6.8, z + 0.6), 'luz_forte', emite=15))
        cs.append(Cubo('nucleo', (6.8, 6.5, 6.8), (9.2, 8.9, 9.2), 'luz_forte', rot=('y', 45.0, (8, 7.7, 8)), emite=15))
    elif nome == 'cristais':
        pos = [(0, 0, 7.5, 0), (-3.5, 1.5, 4.5, -22.5), (3.5, 1.0, 5, 22.5), (-1.5, -3.5, 4, 22.5), (2, -3, 3.5, -22.5), (-3, -1.5, 3, -45), (3.5, 3, 3.2, 45)]
        for i, (dx, dz, h, ang) in enumerate(pos):
            w = 0.9 + (i % 3) * 0.3
            cs.append(Cubo(f'cristal{i}', (8 + dx - w, 0, 8 + dz - w), (8 + dx + w, h, 8 + dz + w), 'cristal', rot=('z', ang, (8 + dx, 0, 8 + dz)), emite=9))
        cs.append(Cubo('base', (4.5, -1, 4.5), (11.5, 0.6, 11.5), 'pedra'))
    elif nome == 'chamas':
        pos = [(0, 0, 8), (-3, -2, 5.5), (3, -1.5, 6), (-2, 3, 5), (2.5, 2.5, 5.5), (0, -3.5, 4), (0, 3.5, 4)]
        for i, (dx, dz, h) in enumerate(pos):
            w = 1.6 if i == 0 else 1.1
            cs.append(Cubo(f'chama{i}', (8 + dx - w, 0, 8 + dz - w), (8 + dx + w, h * 0.55, 8 + dz + w), 'chama', emite=15))
            cs.append(Cubo(f'lingua{i}', (8 + dx - w * 0.5, h * 0.5, 8 + dz - w * 0.5), (8 + dx + w * 0.5, h, 8 + dz + w * 0.5), 'chama_ponta',
                           rot=('z', 22.5 if i % 2 else -22.5, (8 + dx, h * 0.5, 8 + dz)), emite=15))
        cs.append(Cubo('brasa', (4, -1.2, 4), (12, 0.4, 12), 'brasa', emite=6))
    elif nome == 'tentaculos':
        cs.append(Cubo('corpo', (4.5, 0, 4.5), (11.5, 4.5, 11.5), 'carne'))
        for i in range(6):
            a = i * math.pi / 3
            x, z = 8 + math.cos(a) * 5.5, 8 + math.sin(a) * 5.5
            eixo = 'x' if abs(math.sin(a)) > 0.5 else 'z'
            sinal = 1 if (math.sin(a) if eixo == 'x' else -math.cos(a)) > 0 else -1
            cs.append(Cubo(f'tent{i}', (x - 0.9, 1, z - 0.9), (x + 0.9, 7, z + 0.9), 'carne', rot=(eixo, 22.5 * sinal, (x, 1, z))))
            cs.append(Cubo(f'tentp{i}', (x - 0.6, 6.5, z - 0.6), (x + 0.6, 9.5, z + 0.6), 'carne_ponta', rot=(eixo, 45.0 * sinal, (x, 6.5, z)), emite=4))
        cs.append(Cubo('olho', (6.5, 1.5, 3.9), (9.5, 3.8, 4.6), 'olho', emite=12))
    elif nome == 'tricornio':
        cs.append(Cubo('aba', (1, 0, 1), (15, 1.2, 15), 'tecido_escuro'))
        cs.append(Cubo('copa', (4, 1.2, 4), (12, 6.5, 12), 'tecido_escuro'))
        cs.append(Cubo('aba_frente', (2, 1.2, 0.8), (14, 4.5, 2.2), 'tecido_escuro', rot=('x', -22.5, (8, 1.2, 1.5))))
        cs.append(Cubo('caveira', (6.5, 2.2, 0.5), (9.5, 5, 1.3), 'dente'))
        cs.append(Cubo('faixa', (3.8, 1.2, 3.8), (12.2, 2.4, 12.2), 'metal'))
    elif nome == 'mascara':
        cs.append(Cubo('placa', (3, 0, 5.5), (13, 11, 7.5), 'madeira'))
        cs.append(Cubo('olho_e', (4.5, 5.5, 5.1), (7, 7.5, 5.6), 'olho', emite=11))
        cs.append(Cubo('olho_d', (9, 5.5, 5.1), (11.5, 7.5, 5.6), 'olho', emite=11))
        cs.append(Cubo('boca', (5.5, 1.5, 5.1), (10.5, 3, 5.6), 'sombra'))
        for i in range(5):
            x = 4 + i * 2
            cs.append(Cubo(f'pena{i}', (x - 0.6, 10.5, 6), (x + 0.6, 14 + (2 - abs(i - 2)) * 1.5, 7), 'pena', rot=('z', (i - 2) * 22.5 if abs(i - 2) < 2 else (i - 2) / 2 * 45.0, (x, 10.5, 6.5))))
    elif nome == 'engrenagem':
        cs.append(Cubo('roda', (3.5, 0, 6.5), (12.5, 9, 8.5), 'metal'))
        cs.append(Cubo('roda2', (3.5, 0, 6.5), (12.5, 9, 8.5), 'metal', rot=('z', 45.0, (8, 4.5, 7.5))))
        cs.append(Cubo('furo', (6.5, 3, 6.2), (9.5, 6, 8.8), 'sombra'))
        cs.append(Cubo('eixo', (7.2, 3.7, 5.9), (8.8, 5.3, 9.1), 'gema', emite=8))
        for i in range(4):
            a = i * math.pi / 2
            x, y = 8 + math.cos(a) * 5.2, 4.5 + math.sin(a) * 5.2
            cs.append(Cubo(f'dente{i}', (x - 1, y - 1, 6.8), (x + 1, y + 1, 8.2), 'metal_claro'))
    elif nome == 'asas':
        for lado in (-1, 1):
            x = 8 + lado * 6
            cs.append(Cubo(f'asa{lado}', (x - 1, 0, 3), (x + 1, 8, 13), 'pena', rot=('z', 22.5 * lado, (x, 0, 8))))
            cs.append(Cubo(f'asa2{lado}', (x + lado * 2.5 - 0.8, 2, 4), (x + lado * 2.5 + 0.8, 11, 12), 'pena', rot=('z', 45.0 * lado, (x + lado * 2.5, 2, 8))))
        anel(cs, 0, 1.5, 5, 1.2, 'metal')
        cs.append(Cubo('gema', (7, 0.3, 2.6), (9, 1.8, 3.1), 'gema', emite=10))
    elif nome == 'lanterna':
        cs.append(Cubo('capacete', (3, 0, 3), (13, 5, 13), 'metal_claro'))
        cs.append(Cubo('topo', (4.5, 5, 4.5), (11.5, 6.5, 11.5), 'metal_claro'))
        cs.append(Cubo('aba', (2, 0, 1.5), (14, 1, 3), 'metal_claro'))
        cs.append(Cubo('lampada', (6.5, 2, 1.5), (9.5, 4.5, 3), 'luz_forte', emite=15))
        cs.append(Cubo('aro', (6, 1.6, 2.6), (10, 4.9, 3.2), 'metal'))
    elif nome == 'folhas':
        anel(cs, 0, 2, 5.5, 1.5, 'folha')
        for i in range(10):
            a = i * math.pi / 5
            x, z = 8 + math.cos(a) * 5.5, 8 + math.sin(a) * 5.5
            h = 2 + (i % 3)
            cs.append(Cubo(f'folha{i}', (x - 1, 1.5, z - 1), (x + 1, 1.5 + h, z + 1), 'folha', rot=('y', 22.5, (x, 1.5, z))))
            if i % 3 == 0:
                cs.append(Cubo(f'flor{i}', (x - 0.7, 1.5 + h, z - 0.7), (x + 0.7, 2.6 + h, z + 0.7), 'flor', emite=5))
    return cs


# ------------------------------------------------------------------ pintura

def pintor(t, molde_nome):
    cor = cor_tema(t)
    sem = sum(ord(ch) for ch in t['de']) % 997

    def p(P):
        x, y, z, s, tt, pele = P['x'], P['y'], P['z'], P['s'], P['t'], P['pele']
        n = fbm(x * 1.4, y * 1.4, z * 1.4, 3, 1, sem)
        n2 = fbm(x * 3, y * 3, z * 3, 2, 1, sem + 5)
        borda = np.minimum(np.minimum(s, 1 - s), np.minimum(tt, 1 - tt))
        if pele == 'metal':
            c = rampa(n, [(0, '#5a5e66'), (0.5, '#a0a6b0'), (1, '#e8ecf0')])
            c = mix(c, cor, 0.4)
            c = mix(c, clarear(cor, 0.6), (borda < 0.12) * 0.7)
        elif pele == 'metal_claro':
            c = rampa(n, [(0, '#8a9098'), (0.5, '#c8ced6'), (1, '#f8fafc')])
            c = mix(c, cor, 0.25)
        elif pele == 'gema':
            c = rampa(n2, [(0, escurecer(cor, 0.7)), (0.5, clarear(cor, 0.3)), (1, '#ffffff')])
        elif pele in ('chifre', 'chifre_ponta'):
            anelz = 0.5 + 0.5 * np.sin(y * 2.5)
            c = rampa(anelz * 0.6 + n * 0.4, [(0, '#3a2a1a'), (0.5, '#6e5235'), (1, '#b89a70')])
            if pele == 'chifre_ponta':
                c = mix(c, C('#f0e8d8'), tt < 0.5)
        elif pele == 'osso':
            c = rampa(n, [(0, '#a89c86'), (0.5, '#dcd3bf'), (1, '#f4eedc')])
            rach = ridged(x * 0.8, y * 0.8, z * 0.8, 3, 1, sem + 9)
            c = mix(c, C('#6a604e'), ss(0.65, 0.95, rach) * 0.7)
        elif pele == 'osso_escuro':
            c = np.broadcast_to(C('#2a241c'), s.shape + (3,)).copy()
        elif pele == 'dente':
            c = rampa(n, [(0, '#d8d0b8'), (1, '#fffaf0')])
        elif pele == 'olho':
            c = rampa(n2, [(0, clarear(cor, 0.4)), (1, '#ffffff')])
            pupila = (np.abs(s - 0.5) < 0.18) & (np.abs(tt - 0.5) < 0.3)
            c = np.where(pupila[..., None], np.broadcast_to(escurecer(cor, 0.15), c.shape), c)
        elif pele in ('tecido', 'tecido_escuro'):
            dobra = np.sin(x * 1.5 + y * 0.7 + n * 4) * 0.5 + 0.5
            base = cor if pele == 'tecido' else C('#1e1a18')
            c = rampa(dobra * 0.6 + n * 0.4, [(0, escurecer(base, 0.45)), (0.5, escurecer(base, 0.85)), (1, clarear(base, 0.12))])
            c = mix(c, clarear(cor, 0.5), (borda < 0.08) * 0.5)
        elif pele == 'sombra':
            c = np.broadcast_to(C('#05060a'), s.shape + (3,)).copy()
        elif pele in ('luz', 'luz_forte'):
            c = rampa(n2, [(0, clarear(cor, 0.35)), (0.6, clarear(cor, 0.7)), (1, '#ffffff')])
            if pele == 'luz_forte':
                c = clarear(c, 0.3)
        elif pele == 'cristal':
            faceta = np.floor(s * 3) + np.floor(tt * 4)
            c = rampa(((faceta % 3) / 2) * 0.6 + n2 * 0.4, [(0, escurecer(cor, 0.6)), (0.5, cor), (1, clarear(cor, 0.75))])
            c = mix(c, C('#ffffff'), (borda < 0.1) * 0.5)
        elif pele == 'pedra':
            c = rampa(n, [(0, '#44484e'), (0.5, '#7a7f86'), (1, '#a8adb4')])
        elif pele in ('chama', 'chama_ponta'):
            c = rampa(tt + n2 * 0.3, [(0, '#fff6a0'), (0.4, '#ffb020'), (0.8, '#ff5a10'), (1, '#c02000')])
            c = mix(c, cor, 0.3)
        elif pele == 'brasa':
            rach = ridged(x * 0.8, y * 0.8, z * 0.8, 3, 1, sem + 11)
            c = rampa(n, [(0, '#140e0c'), (1, '#3a2a24')])
            c = mix(c, C('#ff7a10'), ss(0.55, 0.9, rach))
        elif pele in ('carne', 'carne_ponta'):
            c = rampa(n, [(0, escurecer(cor, 0.35)), (0.5, escurecer(cor, 0.7)), (1, cor)])
            ventosa = (np.abs(((tt * 6) % 1) - 0.5) < 0.2) & (np.abs(s - 0.5) < 0.25)
            c = np.where(ventosa[..., None], np.broadcast_to(clarear(cor, 0.4), c.shape), c)
            if pele == 'carne_ponta':
                c = mix(c, clarear(cor, 0.7), tt < 0.3)
        elif pele == 'madeira':
            veio = np.sin(y * 1.3 + n * 5) * 0.5 + 0.5
            c = rampa(veio * 0.6 + n * 0.4, [(0, '#4a3418'), (0.5, '#7a5a2c'), (1, '#b08a50')])
            pint = (np.abs(((tt * 5) % 1) - 0.5) < 0.12) & (s > 0.15) & (s < 0.85)
            c = np.where(pint[..., None], np.broadcast_to(cor, c.shape), c)
        elif pele == 'pena':
            faixa = 0.5 + 0.5 * np.sin(tt * 14)
            c = rampa(faixa * 0.5 + n * 0.5, [(0, escurecer(cor, 0.6)), (0.5, clarear(cor, 0.2)), (1, '#ffffff')])
        elif pele == 'folha':
            c = rampa(n, [(0, '#1e4a16'), (0.5, '#3f8a2c'), (1, '#7ccf52')])
            c = mix(c, cor, 0.3 * n2)
        elif pele == 'flor':
            c = rampa(n2, [(0, cor), (1, clarear(cor, 0.7))])
        else:
            c = rampa(n, [(0, '#404040'), (1, '#c0c0c0')])
        return c, 1

    return p


DISPLAY = {
    'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]},
    'head': {'rotation': [0, 0, 0], 'translation': [0, 14, 0], 'scale': [1, 1, 1]},
    'gui': {'rotation': [30, 45, 0], 'translation': [0, -2, 0], 'scale': [0.75, 0.75, 0.75]},
    'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
    'thirdperson_righthand': {'rotation': [0, 0, 0], 'translation': [0, 3, 1], 'scale': [0.5, 0.5, 0.5]},
    'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 3, 0], 'scale': [0.5, 0.5, 0.5]},
}


def gerar(id_, t, escrever=True):
    tema = id_.lower()
    molde_nome, _ = MOLDE.get(id_, ('coroa', 'GOLD_BLOCK'))
    rng = np.random.default_rng(sum(ord(c) for c in id_))
    cs = molde(molde_nome, rng)
    largura = 128
    altura = kit.arrumar_uv(cs, largura, 4)
    H = 16
    while H < altura:
        H *= 2
    atlas = kit.pintar_atlas(cs, largura, H, pintor(t, molde_nome), 4)
    if escrever:
        tex_dir = os.path.join(JAVA, 'textures', 'item', 'chefes')
        os.makedirs(tex_dir, exist_ok=True)
        kit.salvar_png_leve(atlas, os.path.join(tex_dir, f'{tema}.png'))
        modelo = kit.modelo_java(cs, f'corona:item/chefes/{tema}', largura, H, display=DISPLAY)
        kit.salvar_json(os.path.join(JAVA, 'models', 'item', 'chefes', f'{tema}.json'), modelo)
        kit.salvar_json(os.path.join(JAVA, 'items', 'item', 'chefes', f'{tema}.json'), kit.item_java(f'corona:item/chefes/{tema}'))
    pv = kit.previa(cs, atlas, 35, 20, escala=8)
    pv.save(os.path.join(PREV, f'{tema}.png'))
    return molde_nome


if __name__ == '__main__':
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    escrever = '--so-previa' not in sys.argv
    so = [s.upper() for s in args]
    n = 0
    for id_, t in TEMAS.items():
        if so and id_ not in so:
            continue
        m = gerar(id_, t, escrever)
        n += 1
        print('ok', id_, m)
    print(n, 'coroas', '(previas em ' + PREV + ')')
    # tabela para o plugin: tema -> item base do Bedrock
    print(json.dumps({k: v[1] for k, v in MOLDE.items()}))
