"""Armaduras tematicas das masmorras (Fase 3, 08/11): uma armadura com cara propria para cada uma das 59 masmorras.

Pinta direto em 4x (256x128, que o jogo aceita como textura de armadura) a camada 1 (elmo, peitoral, botas) e a
camada 2 (calcas) do equipment asset corona:<tema>. Cada tema tem uma receita: material (placas de metal, osso, quitina,
couro, escamas, cristal, pedra, gelo, tecido, obsidiana, sculk, lava, pelo, madeira, vazio), um emblema no peito (pixel
art), o estilo do elmo (viseira, mascara, coroa, capuz, olhos brilhando, caveira), um detalhe (rebites, costuras,
rachaduras, runas, espinhos, correntes) e a cor do tema (temas_reliquias.json) com um acabamento (ouro, prata, cobre...).

Uso: python3 -I armaduras_tematicas.py [ids...]   (sem ids = todas)  -> escreve no pacote Java e salva previas.
"""
import json, os, sys
import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'skins', 'monstros'))
from base import fbm, ridged, rampa, mix, C, ss, hash2  # noqa: E402

AQUI = os.path.dirname(os.path.abspath(__file__))
JAVA = os.path.normpath(os.path.join(AQUI, '..', 'coronapack', 'pacote', 'java', 'assets', 'corona'))
PREV = '/tmp/claude-0/modelos/armaduras'
os.makedirs(PREV, exist_ok=True)
TEMAS = json.load(open(os.path.join(AQUI, 'temas_reliquias.json'), encoding='utf-8'))
K = 4  # texels por pixel da textura 64x32

# layout 64x32: parte -> (u, v, w, h, d)
PARTES = {'head': (0, 0, 8, 8, 8), 'body': (16, 16, 8, 12, 4), 'rarm': (40, 16, 4, 12, 4), 'rleg': (0, 16, 4, 12, 4)}


def faces_uv(u, v, w, h, d):
    return {'up': (u + d, v, u + d + w, v + d), 'down': (u + d + w, v, u + d + 2 * w, v + d),
            'west': (u, v + d, u + d, v + d + h), 'south': (u + d, v + d, u + d + w, v + d + h),
            'east': (u + d + w, v + d, u + 2 * d + w, v + d + h), 'north': (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h)}


# ------------------------------------------------------------------ receitas
# material, emblema, elmo, detalhe, acabamento (cor do friso), brilho (cor que emite) ou None
R = {
    'CRIPTA': ('osso', 'caveira', 'caveira', 'rachaduras', '#e8e2cf', '#9ad0ff'),
    'NINHO': ('quitina', 'teia', 'olhos', 'pelos', '#1d2a18', '#c8ff40'),
    'MINA': ('metal', 'picareta', 'lanterna', 'rebites', '#ffb84a', '#ffd27a'),
    'PANTANO': ('tecido', 'caldeirao', 'capuz', 'costuras', '#5b2d8a', '#c86bff'),
    'GELO': ('gelo', 'floco', 'coroa', 'rachaduras', '#dff6ff', '#b4ecff'),
    'DESERTO': ('ouro', 'olho', 'nemes', 'faixas', '#2b6fb0', '#ffe27a'),
    'AFOGADA': ('escamas', 'tridente', 'viseira', 'gotas', '#1f8aa8', '#7ff0ff'),
    'TORRE': ('tecido', 'runa', 'capuz', 'runas', '#f0c060', '#f0a0ff'),
    'QUARTEL': ('metal', 'machado', 'viseira', 'rebites', '#8a8f99', None),
    'SELVA': ('madeira', 'folha', 'mascara', 'penas', '#e8c84a', '#a0ff70'),
    'VENTOS': ('metal_claro', 'espiral', 'asa', 'linhas', '#ffffff', '#dff4ff'),
    'CASTELO': ('metal_escuro', 'machado', 'carrasco', 'rebites', '#c02020', '#ff4040'),
    'FORJA': ('lava', 'chama', 'viseira', 'rachaduras', '#ffb060', '#ff7a1a'),
    'BASTIAO': ('ouro', 'coroa', 'presas', 'rebites', '#ffe27a', '#ffd040'),
    'WITHER': ('obsidiana', 'caveira', 'caveira', 'rachaduras', '#303030', '#404040'),
    'COVIL': ('couro', 'presa', 'presas', 'costuras', '#e06060', '#ff8080'),
    'CIDADELA': ('vazio', 'olho', 'coroa', 'estrelas', '#b070ff', '#d090ff'),
    'PRISAO': ('sculk', 'corrente', 'viseira', 'correntes', '#2fb0c0', '#40e0ff'),
    'SECRETA': ('obsidiana', 'olho', 'olhos', 'rachaduras', '#ffffff', '#ffffff'),
    'CEMITERIO': ('pedra', 'cruz', 'capuz', 'rachaduras', '#c0c0c0', '#a0ffa0'),
    'ESGOTO': ('gosma', 'gota', 'viseira', 'bolhas', '#9ff07a', '#c8ff80'),
    'ACAMPAMENTO': ('couro', 'machado', 'viseira', 'costuras', '#9a9a9a', None),
    'MOINHO': ('tecido', 'sol', 'capuz', 'palha', '#8a5a20', '#ffd060'),
    'FUNGOS': ('cogumelo', 'cogumelo', 'cogumelo', 'pintas', '#ffe0e0', '#ff9090'),
    'COVIL_LOBOS': ('pelo', 'pata', 'lobo', 'pelos', '#808080', '#80c0ff'),
    'URSO': ('pelo_claro', 'garra', 'urso', 'pelos', '#6fa8d8', '#c8ecff'),
    'BOSQUE_PALIDO': ('madeira_palida', 'olho', 'mascara', 'musgo', '#5a6a5a', '#f0f0e0'),
    'FAROL': ('metal_claro', 'ancora', 'tricornio', 'linhas', '#2fa6b5', '#80f0ff'),
    'LABORATORIO': ('tecido', 'frasco', 'oculos', 'costuras', '#9a4fd0', '#d080ff'),
    'CAMARA_TESTES': ('cobre', 'engrenagem', 'viseira', 'rebites', '#c87533', '#ffb060'),
    'FAZENDA': ('couro', 'foice', 'chapeu', 'costuras', '#6e8b3d', '#b0ff60'),
    'CRATERA': ('lava', 'estrela', 'viseira', 'rachaduras', '#ff6a2a', '#ffb060'),
    'JARDIM': ('tecido', 'flor', 'kabuto', 'costuras', '#f4a7c6', '#ffc0e0'),
    'CATEDRAL': ('metal_claro', 'cruz', 'mitra', 'linhas', '#ffd700', '#fff0b0'),
    'TESOURO': ('ouro', 'coroa', 'coroa', 'gemas', '#ffd34a', '#ffffa0'),
    'COLISEU': ('bronze', 'escudo', 'gladiador', 'rebites', '#b83a2b', '#ffb060'),
    'TROVAO': ('metal', 'raio', 'coroa', 'runas', '#f5f05a', '#ffff80'),
    'OBSERVATORIO': ('tecido', 'lua', 'capuz', 'estrelas', '#b98cff', '#e0c0ff'),
    'VIUVA': ('quitina', 'ampulheta', 'olhos', 'pelos', '#3a1f1f', '#ff2020'),
    'NAUFRAGIO': ('madeira', 'caveira', 'tricornio', 'cracas', '#3d6e7a', '#80e0ff'),
    'MAUSOLEU': ('faixas', 'olho', 'mumia', 'faixas', '#e8d49a', '#ffe0a0'),
    'BIBLIOTECA': ('tecido', 'livro', 'capuz', 'runas', '#7a4fb0', '#c090ff'),
    'VULCAO': ('lava', 'chama', 'viseira', 'rachaduras', '#ff7a00', '#ffc040'),
    'CIDADE_ANTIGA': ('sculk', 'alma', 'olhos', 'correntes', '#1e5a66', '#40f0ff'),
    'GELEIRA': ('gelo', 'floco', 'viseira', 'rachaduras', '#8fd8ff', '#e0ffff'),
    'SANGUE': ('metal_escuro', 'morcego', 'coroa', 'linhas', '#8b0000', '#ff2020'),
    'RELOGIO': ('bronze', 'engrenagem', 'oculos', 'engrenagens', '#d9a441', '#ffe080'),
    'LABIRINTO': ('couro', 'chifres', 'chifres', 'costuras', '#a0522d', '#ffc080'),
    'CRIPTA_REAL': ('metal_escuro', 'caveira', 'coroa', 'runas', '#4060c0', '#60a0ff'),
    'DELTA': ('bronze', 'escudo', 'centuriao', 'rebites', '#b0703c', '#ffb060'),
    'CARMESIM': ('couro', 'presa', 'presas', 'pelos', '#c0303a', '#ff6070'),
    'DISTORCIDO': ('cogumelo', 'cogumelo', 'cogumelo', 'pintas', '#1fa59a', '#60ffe0'),
    'ALMAS': ('vazio', 'alma', 'capuz', 'estrelas', '#4fd6e0', '#90ffff'),
    'OBSIDIANA': ('obsidiana', 'chifres', 'chifres', 'rachaduras', '#5a1e8c', '#c040ff'),
    'ILHAS': ('vazio', 'estrela', 'coroa', 'estrelas', '#d07cff', '#f0c0ff'),
    'NAVE': ('metal_claro', 'concha', 'viseira', 'linhas', '#9b6bb0', '#e0b0ff'),
    'TRONO': ('obsidiana', 'olho', 'chifres', 'estrelas', '#6a1fa0', '#c060ff'),
    'CORO': ('tecido', 'flor', 'capuz', 'costuras', '#c9a0dc', '#f0d0ff'),
    'ABISMO': ('vazio', 'estrela', 'capuz', 'estrelas', '#2b2b40', '#8080ff'),
}

# ------------------------------------------------------------------ emblemas (pixel art 9x9: . vazio, # cor, o escuro, * brilho)
EMB = {
    'caveira': ["..#####..", ".#######.", "#########", "#o#####o#", "#oo###oo#", "#########", ".##o#o##.", "..#####..", "..#.#.#.."],
    'teia': ["#...#...#", ".#..#..#.", "..#.#.#..", "...###...", "#########", "...###...", "..#.#.#..", ".#..#..#.", "#...#...#"],
    'picareta': ["..#####..", ".#..o..#.", "#...o...#", "....o....", "....o....", "....o....", "....o....", "....o....", "....o...."],
    'caldeirao': ["..*.*.*..", ".*.*.*.*.", "#########", ".#######.", ".#######.", ".#######.", ".#######.", "..#####..", ".##...##."],
    'floco': ["....#....", ".#..#..#.", "..#.#.#..", "...###...", "#########", "...###...", "..#.#.#..", ".#..#..#.", "....#...."],
    'olho': [".........", "..#####..", ".#*****#.", "#**ooo**#", "#*oo*oo*#", "#**ooo**#", ".#*****#.", "..#####..", "........."],
    'tridente': ["#...#...#", "#...#...#", "#...#...#", "##..#..##", ".###o###.", "....o....", "....o....", "....o....", "....o...."],
    'runa': ["....#....", "...###...", "..#.#.#..", ".#..#..#.", "#...#...#", "....#....", "..#.#.#..", "...###...", "....#...."],
    'machado': ["...##....", "..###o...", ".####o...", "..###o...", "...##o...", ".....o...", ".....o...", ".....o...", ".....o..."],
    'folha': ["....#....", "...##....", "..###o...", ".####o...", "#####o...", ".####o#..", "..###o##.", "....o....", "....o...."],
    'espiral': ["..#####..", ".#.....#.", "#...##..#", "#..#..#.#", "#..#.##.#", "#..#....#", ".#..###..", "..#......", "...####.."],
    'chama': ["....#....", "...##....", "...###...", "..#####..", "..#*###..", ".##**###.", ".#***####", ".#*****#.", "..#***#.."],
    'coroa': ["#...#...#", "#...#...#", "##.###.##", "#########", "#########", "#*#*#*#*#", "#########", "#########", "........."],
    'presa': ["#.......#", "##.....##", "##.....##", ".##...##.", ".##...##.", "..#...#..", "..#...#..", "..#...#..", "..*...*.."],
    'corrente': ["..###....", ".#...#...", ".#...#...", "..###....", "...###...", "....#..#.", "...#.##.#", "...#....#", "....####."],
    'cruz': ["...###...", "...###...", ".#######.", "#########", ".#######.", "...###...", "...###...", "...###...", "...###..."],
    'gota': ["....#....", "....#....", "...###...", "...###...", "..#####..", "..#####..", ".###*###.", ".###*###.", "..#####.."],
    'sol': ["#...#...#", ".#..#..#.", "..#####..", ".##***##.", "####*####", ".##***##.", "..#####..", ".#..#..#.", "#...#...#"],
    'cogumelo': ["..#####..", ".##*###*.", "#########", "#*#####*#", "#########", "...ooo...", "...ooo...", "...ooo...", "..ooooo.."],
    'pata': [".##...##.", ".##...##.", "#..#.#..#", "#..#.#..#", "..#####..", ".#######.", ".#######.", "..#####..", "........."],
    'garra': ["#...#...#", "#...#...#", "##..#..##", ".#..#..#.", ".#..#..#.", ".##.#.##.", "..#.#.#..", "..##.##..", "...#.#..."],
    'ancora': ["...###...", "...#.#...", "...###...", "....#....", "#...#...#", "##..#..##", ".##.#.##.", "..#####..", "....#...."],
    'frasco': ["...###...", "....#....", "....#....", "...###...", "..#*..#..", ".#**...#.", ".#***..#.", ".#*****#.", "..#####.."],
    'engrenagem': ["...#.#...", ".#######.", ".##...##.", "##..#..##", "#..###..#", "##..#..##", ".##...##.", ".#######.", "...#.#..."],
    'foice': ["..#####..", ".##...###", "##......#", "#........", "#....o...", ".....o...", ".....o...", ".....o...", ".....o..."],
    'estrela': ["....#....", "....#....", "...###...", "#########", ".#######.", "..#####..", ".###.###.", "##.....##", "........."],
    'flor': ["...#.#...", "..#####..", ".###*###.", "..#***#..", ".###*###.", "..#####..", "...#.#...", "....o....", "....o...."],
    'escudo': ["#########", "#o#####o#", "#oo###oo#", "#ooo#ooo#", "#oo###oo#", ".#o###o#.", ".#######.", "..#####..", "...###..."],
    'raio': ["....####.", "...###...", "..###....", ".######..", "...###...", "..###....", ".###.....", "###......", "#........"],
    'lua': ["...####..", "..##.....", ".##......", "##.......", "##.......", "##.......", ".##......", "..##.....", "...####.."],
    'ampulheta': ["#########", ".#######.", "..#####..", "...###...", "....#....", "...#*#...", "..#***#..", ".#*****#.", "#########"],
    'livro': ["#########", "#o#####o#", "#o##.##o#", "#o##.##o#", "#o##.##o#", "#o##.##o#", "#o##.##o#", "#########", ".###.###."],
    'alma': ["..#####..", ".#######.", "#oo###oo#", "#oo###oo#", "#########", "##o###o##", "#########", ".#.#.#.#.", "#.#.#.#.#"],
    'morcego': ["#.......#", "##.....##", "###...###", "#########", "#*#####*#", ".#######.", "..##.##..", "...#.#...", "........."],
    'chifres': ["#.......#", "##.....##", "##.....##", ".##...##.", ".##...##.", "..#...#..", "..#...#..", "..##.##..", "...###..."],
    'concha': ["....#....", "...###...", "..#####..", ".###o###.", "###oo#o##", "#########", ".#o#o#o#.", "..#####..", "...###..."],
}


def cor(h):
    return C(h)


def escurecer(c, f):
    return np.clip(np.asarray(c) * f, 0, 1)


def clarear(c, f):
    c = np.asarray(c)
    return np.clip(c + (1 - c) * f, 0, 1)


# ------------------------------------------------------------------ materiais

def material(mat, tema_cor, X, Y, Z, sem):
    """Cor base do material (sem placas) em cada texel. X,Y,Z em unidades de pixel (1/4 de bloco de textura)."""
    n = fbm(X * 0.9, Y * 0.9, Z * 0.9, 3, 1, sem)
    n2 = fbm(X * 2.2, Y * 2.2, Z * 2.2, 2, 1, sem + 3)
    if mat == 'metal':
        c = rampa(n, [(0, '#4a5058'), (0.5, '#8c949e'), (1, '#d6dce2')])
        c = mix(c, tema_cor, 0.35)
    elif mat == 'metal_claro':
        c = rampa(n, [(0, '#9aa4b0'), (0.5, '#d0d8e0'), (1, '#f4f8fb')])
        c = mix(c, tema_cor, 0.25)
    elif mat == 'metal_escuro':
        c = rampa(n, [(0, '#1a1c22'), (0.5, '#3a3e48'), (1, '#6a7080')])
        c = mix(c, tema_cor, 0.3)
    elif mat == 'ouro':
        c = rampa(n, [(0, '#8a6210'), (0.5, '#e6b830'), (1, '#fff2a8')])
    elif mat == 'cobre':
        c = rampa(n, [(0, '#6e3a1a'), (0.5, '#c87533'), (1, '#f0b070')])
        verd = ss(0.65, 0.85, n2)
        c = mix(c, C('#4fa08a'), verd * 0.5)
    elif mat == 'bronze':
        c = rampa(n, [(0, '#5a3a14'), (0.5, '#a8743a'), (1, '#e0b070')])
    elif mat == 'osso':
        c = rampa(n, [(0, '#a89c86'), (0.5, '#dcd3bf'), (1, '#f4eedc')])
    elif mat == 'quitina':
        c = rampa(n, [(0, '#0c0f0a'), (0.5, '#1e261a'), (1, '#3c4a34')])
        c = mix(c, tema_cor, 0.25 * n2)
    elif mat == 'couro':
        c = rampa(n, [(0, '#4a2c16'), (0.5, '#7a4a26'), (1, '#a86c3c')])
        c = mix(c, tema_cor, 0.2)
    elif mat == 'escamas':
        esc = np.abs(((X * 0.5) % 1) - 0.5) * 2 + np.abs(((Y * 0.5 + (np.floor(X * 0.5) % 2) * 0.5) % 1) - 0.5) * 2
        c = rampa(ss(0.4, 1.2, esc) * 0.7 + n * 0.3, [(0, '#0f3a44'), (0.5, '#2c8a9c'), (1, '#9fe8f2')])
        c = mix(c, tema_cor, 0.3)
    elif mat == 'cristal':
        c = rampa(n2, [(0, '#5a3a9a'), (0.5, '#a070e0'), (1, '#f0e0ff')])
    elif mat == 'pedra':
        c = rampa(n, [(0, '#55585c'), (0.5, '#8a8e92'), (1, '#b8bcbf')])
    elif mat == 'gelo':
        c = rampa(n, [(0, '#6ab4e8'), (0.5, '#b4e2ff'), (1, '#f0fbff')])
        rach = ridged(X * 0.6, Y * 0.6, Z * 0.6, 3, 1, sem + 9)
        c = mix(c, C('#ffffff'), ss(0.55, 0.9, rach) * 0.6)
    elif mat == 'tecido':
        dobra = np.sin(X * 0.9 + n * 4) * 0.5 + 0.5
        c = rampa(dobra * 0.6 + n * 0.4, [(0, escurecer(tema_cor, 0.45)), (0.5, escurecer(tema_cor, 0.8)), (1, clarear(tema_cor, 0.15))])
    elif mat == 'obsidiana':
        c = rampa(n, [(0, '#07060c'), (0.5, '#181428'), (1, '#2e2848')])
        c = mix(c, tema_cor, 0.2 * n2)
    elif mat == 'sculk':
        c = rampa(n, [(0, '#041c20'), (0.5, '#0a3a40'), (1, '#146066')])
        veia = ridged(X * 0.5, Y * 0.5, Z * 0.5, 3, 1, sem + 5)
        c = mix(c, C('#2fe8ff'), ss(0.6, 0.95, veia) * 0.9)
    elif mat == 'lava':
        c = rampa(n, [(0, '#140e0c'), (0.5, '#2e221e'), (1, '#4a3a34')])
        rach = ridged(X * 0.5, Y * 0.5, Z * 0.5, 3, 1, sem + 11)
        c = mix(c, rampa(n2, [(0, '#ff5a00'), (1, '#ffd040')]), ss(0.55, 0.9, rach))
    elif mat == 'gosma':
        c = rampa(n, [(0, '#3a7a2a'), (0.5, '#6cc04a'), (1, '#b8ff80')])
        bol = ss(0.7, 0.9, n2)
        c = mix(c, C('#e8ffc0'), bol * 0.7)
    elif mat == 'cogumelo':
        c = rampa(n, [(0, escurecer(tema_cor, 0.5)), (0.5, tema_cor), (1, clarear(tema_cor, 0.3))])
        pinta = ss(0.72, 0.85, fbm(X * 1.4, Y * 1.4, Z * 1.4, 2, 1, sem + 21))
        c = mix(c, C('#fff6e8'), pinta)
    elif mat in ('pelo', 'pelo_claro'):
        fio = np.sin(Y * 3.2 + n * 6 + X * 0.3) * 0.5 + 0.5
        if mat == 'pelo':
            c = rampa(fio * 0.7 + n * 0.3, [(0, '#3a3a3a'), (0.5, '#7a7a7a'), (1, '#c8c8c8')])
        else:
            c = rampa(fio * 0.7 + n * 0.3, [(0, '#a8c8dc'), (0.5, '#e0f0f8'), (1, '#ffffff')])
    elif mat in ('madeira', 'madeira_palida'):
        veio = np.sin(Y * 1.1 + n * 5) * 0.5 + 0.5
        if mat == 'madeira':
            c = rampa(veio * 0.6 + n * 0.4, [(0, '#3e2a16'), (0.5, '#6b4a2a'), (1, '#9c7448')])
        else:
            c = rampa(veio * 0.6 + n * 0.4, [(0, '#6a6e62'), (0.5, '#9a9e92'), (1, '#c8ccbe')])
    elif mat == 'vazio':
        c = rampa(n, [(0, '#0a0614'), (0.5, '#1c1030'), (1, '#2e1c4a')])
        c = mix(c, tema_cor, 0.25 * n2)
        est = hash2(np.floor(X).astype(int), np.floor(Y + Z * 7).astype(int), sem) > 0.965
        c = np.where(est[..., None], C('#f0f0ff') * np.ones_like(c), c)
    elif mat == 'faixas':
        faixa = np.abs(((Y * 0.45 + X * 0.12) % 1) - 0.5) * 2
        c = rampa(faixa * 0.6 + n * 0.4, [(0, '#8a7a50'), (0.5, '#d8c890'), (1, '#f4ecc8')])
    else:
        c = rampa(n, [(0, '#404040'), (0.5, '#808080'), (1, '#c0c0c0')])
    return c


def placas(c, X, Y, S, T, W, H, mat, acab, sem, parte, face):
    """Segmenta em placas com chanfro (claro em cima/esquerda, escuro embaixo/direita) e friso na borda da face."""
    if mat in ('tecido', 'pelo', 'pelo_claro', 'gosma', 'faixas', 'cogumelo'):
        placa = np.zeros(S.shape, bool)
    else:
        # placas horizontais de 3 pixels (12 texels) com juntas escuras
        ph = 12 if parte != 'head' else 16
        j = ((Y + 2) % ph)
        junta = j < 1.0
        c = np.where(junta[..., None], escurecer(c, 0.55), c)
        topo = (j >= 1.0) & (j < 2.0)
        c = np.where(topo[..., None], clarear(c, 0.18), c)
        placa = junta
    # friso da borda da face
    bx = np.minimum(S, 1 - S) * W
    by = np.minimum(T, 1 - T) * H
    borda = np.minimum(bx, by)
    friso = borda < 1.0
    friso2 = (borda >= 1.0) & (borda < 2.0)
    ac = C(acab)
    c = np.where(friso[..., None], ac * np.ones_like(c), c)
    c = np.where(friso2[..., None], mix(c, escurecer(ac, 0.6), 0.5), c)
    return c


def detalhe(c, det, X, Y, S, T, W, H, tema_cor, brilho, sem):
    if det == 'rebites':
        rb = (np.abs((X % 8) - 4) < 0.8) & (np.abs(((Y + 4) % 12) - 6) < 0.8) & (S > 0.12) & (S < 0.88)
        c = np.where(rb[..., None], clarear(c, 0.45), c)
    elif det == 'costuras':
        cs = (np.abs(S - 0.5) * W < 0.6) & (((Y % 3) < 1.2))
        c = np.where(cs[..., None], escurecer(c, 0.5), c)
    elif det == 'rachaduras':
        r = ridged(X * 0.35, Y * 0.35, 0 * X, 3, 1, sem + 17)
        m = ss(0.62, 0.9, r)
        alvo = C(brilho) if brilho else escurecer(c, 0.3)
        c = mix(c, alvo * np.ones_like(c), m * 0.85)
    elif det in ('runas', 'estrelas'):
        h = hash2(np.floor(X / 2).astype(int), np.floor(Y / 2).astype(int), sem + 23)
        m = (h > 0.93) & (S > 0.15) & (S < 0.85) & (T > 0.1) & (T < 0.9)
        alvo = C(brilho) if brilho else clarear(tema_cor, 0.6)
        c = np.where(m[..., None], alvo * np.ones_like(c), c)
    elif det in ('pelos', 'palha'):
        fio = (hash2(np.floor(X).astype(int), np.floor(Y / 3).astype(int), sem + 29) > 0.7) & ((Y % 3) < 2)
        c = np.where(fio[..., None], clarear(c, 0.12), c)
    elif det == 'gotas' or det == 'bolhas':
        h = hash2(np.floor(X / 3).astype(int), np.floor(Y / 3).astype(int), sem + 31)
        m = (h > 0.88) & (((X % 3) - 1.5) ** 2 + ((Y % 3) - 1.5) ** 2 < 1.2)
        c = np.where(m[..., None], clarear(c, 0.5), c)
    elif det == 'linhas':
        m = (np.abs(S - 0.5) * W < 0.6) | (np.abs(T - 0.35) * H < 0.6)
        c = np.where(m[..., None], mix(c, C(brilho) if brilho else clarear(c, 0.4), 0.6), c)
    elif det == 'faixas':
        m = (((X + Y * 0.5) % 6) < 1.2)
        c = np.where(m[..., None], mix(c, C(brilho) if brilho else clarear(c, 0.3), 0.6), c)
    elif det == 'correntes':
        m = ((np.abs(S - 0.25) * W < 0.6) | (np.abs(S - 0.75) * W < 0.6)) & ((Y % 4) < 2.5)
        c = np.where(m[..., None], mix(c, C('#9aa0a8'), 0.8), c)
    elif det == 'espinhos':
        m = ((X % 6) < 1.5) & (((Y + 3) % 6) < 1.5)
        c = np.where(m[..., None], C('#e8e8f0') * np.ones_like(c), c)
    elif det == 'gemas':
        h = hash2(np.floor(X / 4).astype(int), np.floor(Y / 4).astype(int), sem + 37)
        m = (h > 0.8) & (((X % 4) - 2) ** 2 + ((Y % 4) - 2) ** 2 < 2.2)
        gema = rampa(h, [(0, '#ff3050'), (0.5, '#30c0ff'), (1, '#40ff80')])
        c = np.where(m[..., None], gema, c)
    elif det == 'penas':
        m = ((np.abs(S - 0.5) * W) < 0.8) & ((Y % 5) < 3)
        c = np.where(m[..., None], mix(c, C(brilho) if brilho else clarear(c, 0.4), 0.7), c)
    elif det == 'musgo':
        n = fbm(X * 0.8, Y * 0.8, 0 * X, 2, 1, sem + 41)
        c = mix(c, C('#4f7a3a'), ss(0.62, 0.8, n) * 0.8)
    elif det == 'pintas':
        h = hash2(np.floor(X / 3).astype(int), np.floor(Y / 3).astype(int), sem + 43)
        m = (h > 0.75) & (((X % 3) - 1.5) ** 2 + ((Y % 3) - 1.5) ** 2 < 1.4)
        c = np.where(m[..., None], C('#fff6e8') * np.ones_like(c), c)
    elif det == 'cracas':
        h = hash2(np.floor(X / 2).astype(int), np.floor(Y / 2).astype(int), sem + 47)
        m = (h > 0.86)
        c = np.where(m[..., None], mix(c, C('#c8c0a8'), 0.8), c)
    elif det == 'engrenagens':
        r = np.sqrt(((X % 10) - 5) ** 2 + ((Y % 10) - 5) ** 2)
        m = (np.abs(r - 3) < 0.7) | ((r < 1.2))
        c = np.where(m[..., None], mix(c, C(brilho) if brilho else clarear(c, 0.4), 0.7), c)
    return c


def emblema(c, nome, S, T, W, H, tema_cor, acab, brilho, cx=0.5, cy=0.42, tam=0.72):
    """Desenha o emblema (pixel art 9x9) centrado em (cx, cy) ocupando 'tam' da largura da face."""
    mapa = EMB.get(nome)
    if mapa is None:
        return c
    n = len(mapa)
    # tamanho em fracao da face: 'tam' da largura; altura proporcional (face pode ser mais alta que larga)
    lado = tam * W   # em texels
    px = (S * W - (cx * W - lado / 2)) / lado * n
    py = (T * H - (cy * H - lado / 2)) / lado * n
    ix = np.floor(px).astype(int)
    iy = np.floor(py).astype(int)
    dentro_m = (ix >= 0) & (ix < n) & (iy >= 0) & (iy < n)
    out = c.copy()
    cores = {'#': C(acab), 'o': escurecer(C(acab), 0.35), '*': C(brilho) if brilho else clarear(tema_cor, 0.7)}
    for yy in range(n):
        for xx in range(n):
            ch = mapa[yy][xx]
            if ch == '.':
                continue
            m = dentro_m & (ix == xx) & (iy == yy)
            out = np.where(m[..., None], cores[ch] * np.ones_like(c), out)
    # sombra do emblema (1 texel para baixo-direita)
    return out


def elmo(c, estilo, face, S, T, W, H, tema_cor, acab, brilho, mat):
    ac = C(acab)
    bri = C(brilho) if brilho else clarear(tema_cor, 0.7)
    if face == 'south':
        if estilo in ('viseira', 'lanterna', 'oculos', 'tricornio', 'kabuto', 'gladiador', 'centuriao'):
            fenda = (np.abs(T - 0.47) < 0.07) & (S > 0.12) & (S < 0.88)
            c = np.where(fenda[..., None], C('#05060a') * np.ones_like(c), c)
            olho = ((np.abs(S - 0.3) < 0.07) | (np.abs(S - 0.7) < 0.07)) & (np.abs(T - 0.47) < 0.045)
            c = np.where(olho[..., None], bri * np.ones_like(c), c)
            if estilo == 'gladiador':
                grade = (T > 0.55) & (np.abs(((S * 8) % 2) - 1) < 0.3)
                c = np.where(grade[..., None], escurecer(ac, 0.7), c)
            if estilo == 'oculos':
                aro = ((np.abs(np.hypot((S - 0.3) * 1.3, T - 0.47) - 0.13) < 0.03) | (np.abs(np.hypot((S - 0.7) * 1.3, T - 0.47) - 0.13) < 0.03))
                c = np.where(aro[..., None], ac * np.ones_like(c), c)
        elif estilo in ('olhos', 'caveira', 'lobo', 'urso', 'presas', 'chifres', 'mumia', 'cogumelo'):
            olho = (np.hypot((S - 0.3) * 1.2, (T - 0.45) * 1.4) < 0.1) | (np.hypot((S - 0.7) * 1.2, (T - 0.45) * 1.4) < 0.1)
            c = np.where(olho[..., None], bri * np.ones_like(c), c)
            pupila = (np.hypot((S - 0.3) * 1.2, (T - 0.45) * 1.4) < 0.04) | (np.hypot((S - 0.7) * 1.2, (T - 0.45) * 1.4) < 0.04)
            c = np.where(pupila[..., None], C('#05060a') * np.ones_like(c), c)
            if estilo in ('caveira', 'mumia'):
                nariz = (np.abs(S - 0.5) < 0.06) & (np.abs(T - 0.62) < 0.05)
                c = np.where(nariz[..., None], escurecer(c, 0.3), c)
                dente = (T > 0.78) & (np.abs(((S * 8) % 1) - 0.5) < 0.28)
                c = np.where(dente[..., None], C('#f0e8d0') * np.ones_like(c), c)
                vao = (T > 0.78) & (np.abs(((S * 8) % 1) - 0.5) >= 0.28)
                c = np.where(vao[..., None], escurecer(c, 0.3), c)
            if estilo in ('presas', 'lobo', 'urso'):
                presa = (T > 0.72) & ((np.abs(S - 0.28) < 0.05) | (np.abs(S - 0.72) < 0.05))
                c = np.where(presa[..., None], C('#f6f2e4') * np.ones_like(c), c)
            if estilo == 'lobo' or estilo == 'urso':
                focinho = (np.abs(S - 0.5) < 0.12) & (np.abs(T - 0.68) < 0.08)
                c = np.where(focinho[..., None], C('#1a1a1a') * np.ones_like(c), c)
        elif estilo in ('capuz', 'chapeu', 'nemes', 'mitra'):
            sombra = (T > 0.3) & (T < 0.75) & (S > 0.15) & (S < 0.85)
            c = np.where(sombra[..., None], escurecer(c, 0.35), c)
            olho = ((np.abs(S - 0.32) < 0.06) | (np.abs(S - 0.68) < 0.06)) & (np.abs(T - 0.5) < 0.04)
            c = np.where(olho[..., None], bri * np.ones_like(c), c)
            aba = (np.abs(T - 0.28) < 0.04)
            c = np.where(aba[..., None], ac * np.ones_like(c), c)
        elif estilo == 'mascara':
            padrao = (np.abs(S - 0.5) < 0.06) | ((np.abs(T - 0.35) < 0.05) & (S > 0.2) & (S < 0.8))
            c = np.where(padrao[..., None], ac * np.ones_like(c), c)
            olho = (np.hypot((S - 0.3) * 1.2, (T - 0.5) * 1.4) < 0.1) | (np.hypot((S - 0.7) * 1.2, (T - 0.5) * 1.4) < 0.1)
            c = np.where(olho[..., None], C('#05060a') * np.ones_like(c), c)
            pup = (np.hypot((S - 0.3) * 1.2, (T - 0.5) * 1.4) < 0.045) | (np.hypot((S - 0.7) * 1.2, (T - 0.5) * 1.4) < 0.045)
            c = np.where(pup[..., None], bri * np.ones_like(c), c)
        elif estilo == 'carrasco':
            buraco = (np.hypot((S - 0.3) * 1.2, (T - 0.47) * 1.4) < 0.09) | (np.hypot((S - 0.7) * 1.2, (T - 0.47) * 1.4) < 0.09)
            c = np.where(buraco[..., None], C('#05060a') * np.ones_like(c), c)
            pup = (np.hypot((S - 0.3) * 1.2, (T - 0.47) * 1.4) < 0.035) | (np.hypot((S - 0.7) * 1.2, (T - 0.47) * 1.4) < 0.035)
            c = np.where(pup[..., None], bri * np.ones_like(c), c)
        elif estilo == 'asa':
            fenda = (np.abs(T - 0.47) < 0.06) & (S > 0.1) & (S < 0.9)
            c = np.where(fenda[..., None], C('#05060a') * np.ones_like(c), c)
            olho = ((np.abs(S - 0.3) < 0.07) | (np.abs(S - 0.7) < 0.07)) & (np.abs(T - 0.47) < 0.04)
            c = np.where(olho[..., None], bri * np.ones_like(c), c)
        if estilo == 'coroa':
            fenda = (np.abs(T - 0.5) < 0.06) & (S > 0.12) & (S < 0.88)
            c = np.where(fenda[..., None], C('#05060a') * np.ones_like(c), c)
            olho = ((np.abs(S - 0.3) < 0.07) | (np.abs(S - 0.7) < 0.07)) & (np.abs(T - 0.5) < 0.04)
            c = np.where(olho[..., None], bri * np.ones_like(c), c)
    # coroa / pontas / cristas: faixa de pontas no alto de todas as faces laterais
    if face in ('south', 'north', 'east', 'west'):
        if estilo in ('coroa', 'mitra', 'kabuto', 'centuriao'):
            ponta = (T < 0.2) & (np.abs(((S * 4) % 1) - 0.5) < (0.5 - T * 2.2))
            c = np.where(ponta[..., None], ac * np.ones_like(c), c)
            if estilo == 'coroa':
                gema = (T < 0.1) & (np.abs(((S * 4) % 1) - 0.5) < 0.15)
                c = np.where(gema[..., None], bri * np.ones_like(c), c)
        if estilo in ('chifres', 'presas') and face in ('east', 'west'):
            chifre = (T < 0.45) & (np.abs(S - 0.5) < 0.18 - T * 0.3)
            c = np.where(chifre[..., None], C('#e8dcc0') * np.ones_like(c), c)
        if estilo == 'lanterna' and face == 'south':
            lamp = (np.hypot((S - 0.5) * 1.2, (T - 0.18) * 1.4) < 0.1)
            c = np.where(lamp[..., None], C('#fff2a0') * np.ones_like(c), c)
        if estilo == 'tricornio' and T.max() > 0:
            aba = (T < 0.14)
            c = np.where(aba[..., None], C('#1e1a16') * np.ones_like(c), c)
        if estilo == 'chapeu':
            aba = (T < 0.12)
            c = np.where(aba[..., None], C('#c89a3a') * np.ones_like(c), c)
    if face == 'up':
        if estilo in ('coroa', 'mitra'):
            anel = (np.abs(np.hypot(S - 0.5, T - 0.5) - 0.38) < 0.08)
            c = np.where(anel[..., None], ac * np.ones_like(c), c)
        if estilo == 'cogumelo':
            c = mix(c, tema_cor, 0.5)
    return c


# ------------------------------------------------------------------ pintura de uma face

def pintar_face(rec, t, parte, face, camada, x0, y0, x1, y1, sem):
    mat, emb, elm, det, acab, brilho = rec
    tema_cor = np.array(t['rgb'], float) / 255
    W, H = (x1 - x0) * K, (y1 - y0) * K
    s = (np.arange(W) + 0.5) / W
    tt = (np.arange(H) + 0.5) / H
    S, T = np.meshgrid(s, tt)
    X = x0 * K + S * W
    Y = y0 * K + T * H
    Z = np.full_like(S, {'north': 0, 'south': 7, 'east': 13, 'west': 3, 'up': 17, 'down': 23}[face])
    c = material(mat, tema_cor, X / 2, Y / 2, Z / 2, sem)
    c = placas(c, X, Y, S, T, W, H, mat, acab, sem, parte, face)
    c = detalhe(c, det, X, Y, S, T, W, H, tema_cor, brilho, sem)
    a = np.ones(S.shape)
    if parte == 'head':
        c = elmo(c, elm, face, S, T, W, H, tema_cor, acab, brilho, mat)
    if parte == 'body':
        if face == 'south':
            c = emblema(c, emb, S, T, W, H, tema_cor, acab, brilho)
            # cinto
            cinto = np.abs(T - 0.86) < 0.045
            c = np.where(cinto[..., None], escurecer(C(acab), 0.55), c)
            fivela = cinto & (np.abs(S - 0.5) < 0.1)
            c = np.where(fivela[..., None], C(acab) * np.ones_like(c), c)
        if face == 'north':
            # costas: versao menor do emblema
            c = emblema(c, emb, S, T, W, H, tema_cor, acab, brilho, cy=0.4, tam=0.5)
    if parte == 'rarm':
        # ombreira (parte de cima) e bracadeira
        ombro = T < 0.22
        c = np.where(ombro[..., None], mix(c, C(acab), 0.35), c)
        linha = np.abs(T - 0.22) < 0.03
        c = np.where(linha[..., None], C(acab) * np.ones_like(c), c)
        if camada == 2:
            a = np.zeros(S.shape)
    if parte == 'rleg':
        if camada == 1:
            # botas: so a parte de baixo
            a = np.where(T > 0.6, 1.0, 0.0)
            cano = np.abs(T - 0.62) < 0.035
            c = np.where(cano[..., None], C(acab) * np.ones_like(c), c)
            biqueira = (T > 0.88) & (face == 'south')
            c = np.where(biqueira[..., None], mix(c, C(acab), 0.6), c)
        else:
            # calcas: joelheira
            joelho = (np.abs(T - 0.5) < 0.07) & (face in ('south',))
            c = np.where(joelho[..., None], mix(c, C(acab), 0.7), c)
    if parte == 'body' and camada == 2:
        a = np.where(T > 0.6, 1.0, 0.0)
        cinto = np.abs(T - 0.64) < 0.04
        c = np.where(cinto[..., None], escurecer(C(acab), 0.55), c)
    if parte == 'head' and camada == 2:
        a = np.zeros(S.shape)
    # sombra leve nas faces laterais/baixo para dar volume
    if face in ('east', 'west'):
        c = escurecer(c, 0.85)
    if face in ('north', 'down'):
        c = escurecer(c, 0.78)
    return np.clip(c, 0, 1), a


def textura(id_, t, camada):
    rec = R.get(id_, ('metal', 'escudo', 'viseira', 'rebites', '#c0c0c0', None))
    img = np.zeros((32 * K, 64 * K, 4))
    sem = (sum(ord(ch) for ch in id_) * 7 + camada) % 997
    for parte, (u, v, w, h, d) in PARTES.items():
        for face, (x0, y0, x1, y1) in faces_uv(u, v, w, h, d).items():
            rgb, a = pintar_face(rec, t, parte, face, camada, x0, y0, x1, y1, sem)
            img[y0 * K:y1 * K, x0 * K:x1 * K, :3] = rgb
            img[y0 * K:y1 * K, x0 * K:x1 * K, 3] = a
    return (np.clip(img, 0, 1) * 255 + 0.5).astype(np.uint8)


def previa(id_, t):
    """Frente do personagem (elmo, peito, bracos, pernas, botas) a partir das texturas, 6x."""
    c1 = textura(id_, t, 1)
    c2 = textura(id_, t, 2)
    def face(img, parte, f):
        u, v, w, h, d = PARTES[parte]
        x0, y0, x1, y1 = faces_uv(u, v, w, h, d)[f]
        return Image.fromarray(img[y0 * K:y1 * K, x0 * K:x1 * K], 'RGBA')
    out = Image.new('RGBA', (16 * K, 32 * K), (40, 44, 52, 255))
    def cola(im, x, y):
        out.alpha_composite(im, (x * K, y * K))
    cola(face(c1, 'head', 'south'), 4, 0)
    cola(face(c2, 'body', 'south'), 4, 8)   # calcas (parte de baixo do corpo)
    cola(face(c1, 'body', 'south'), 4, 8)
    cola(face(c1, 'rarm', 'south'), 0, 8)
    cola(face(c1, 'rarm', 'south').transpose(Image.FLIP_LEFT_RIGHT), 12, 8)
    cola(face(c2, 'rleg', 'south'), 4, 20)
    cola(face(c2, 'rleg', 'south').transpose(Image.FLIP_LEFT_RIGHT), 8, 20)
    cola(face(c1, 'rleg', 'south'), 4, 20)
    cola(face(c1, 'rleg', 'south').transpose(Image.FLIP_LEFT_RIGHT), 8, 20)
    return out.resize((out.width * 3, out.height * 3), Image.NEAREST)


def gerar(id_, t, escrever=True):
    tema = id_.lower()
    if escrever:
        for camada, pasta in ((1, 'humanoid'), (2, 'humanoid_leggings')):
            d = os.path.join(JAVA, 'textures', 'entity', 'equipment', pasta)
            os.makedirs(d, exist_ok=True)
            Image.fromarray(textura(id_, t, camada), 'RGBA').save(os.path.join(d, f'{tema}.png'), optimize=True)
    previa(id_, t).save(os.path.join(PREV, f'{tema}.png'))


if __name__ == '__main__':
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    escrever = '--so-previa' not in sys.argv
    so = [s.upper() for s in args]
    n = 0
    for id_, t in TEMAS.items():
        if so and id_ not in so:
            continue
        gerar(id_, t, escrever)
        n += 1
        print('ok', id_, R.get(id_, ('?',))[0])
    print(n, 'temas', '(previas em ' + PREV + ')')
