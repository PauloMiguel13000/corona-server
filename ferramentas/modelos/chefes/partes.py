"""Chefes das masmorras 1-10 em PECAS articuladas (para animar no jogo com ItemDisplay, Java e Bedrock).

Cada chefe vira varias pecas (tronco, cabeca, mandibula, braco/antebraco, coxa/canela, capa, saia, orbitas...). Cada
peca e um item corona:masmorras/<modelo>_<peca>, desenhado com o pivo da junta no centro do item (8,8,8). Os cubos que
atravessam uma junta (perna inteira, braco inteiro) sao cortados na junta. Sai tambem o corpos.json (pivos, pais e
tipo de esqueleto) que o plugin Masmorras le para animar.

uso: PYTHONPATH=plugins/coronapack:ferramentas/modelos python3 ferramentas/modelos/chefes/partes.py <saida> [modelos...]
Saida: <saida>/java (pacote Java so das masmorras), <saida>/bedrock (pacote Bedrock), corona-masmorras3d.json (Geyser),
<saida>/corpos.json e previas.
"""
import copy
import json
import os
import sys

import numpy as np
from PIL import Image

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)
sys.path.insert(0, os.path.join(AQUI, '..'))
import chefes3d as K  # noqa: E402
import bruxa3d as BX  # noqa: E402
import chefes3d_b as KB  # noqa: E402
import chefes3d_c as KC  # noqa: E402  (15-20 refeitos)
H, A, C = K.H, K.A, K.C
import kit  # noqa: E402

SAIDA = sys.argv[1] if len(sys.argv) > 1 else '/tmp/masm3d'

# ------------------------------------------------------------------ esqueletos
BRACO = ('umero', 'cotovelo', 'radio', 'mao', 'dedo', 'bracelete', 'manga', 'punho', 'luva', 'braco', 'unha', 'garra', 'chama_mao',
         'bracadeira', 'chama', 'chama2', 'pocao', 'rolha', 'manga_ponta')
ARMA = ('cabo', 'picareta', 'ponta', 'lamina', 'lamina_luz', 'guarda', 'cajado', 'cajado_aro', 'caveirinha', 'caveira_olho', 'orbe',
        'machado', 'machado2', 'fio', 'arco', 'arco_ponta', 'corda_arco', 'cristal_cajado', 'bola', 'cravo', 'gancho', 'gancho2',
        'tridente_base', 'ponta_luz', 'pena_cajado', 'cabo_aro', 'machado_ponta', 'sangue', 'cabo_alca', 'pa', 'pa_terra', 'farpa',
        'osso_cajado', 'cabo_ponta', 'fio_ponta', 'sangue2', 'pa_gola', 'pa_ponta', 'rebite')
PERNA = ('femur', 'joelho', 'canela', 'pe', 'perna', 'bota', 'joelheira', 'caneleira')
CAPA = ('capa', 'capa_barra', 'manto', 'trapo', 'estandarte', 'mastro', 'pele_capa', 'casaco_rabo')


def H_(**kw):
    """Esqueleto humano: ombro (dx,y,z), cotovelo y, quadril (dx,y), joelho y, pescoco (y,z), capa (y,z)."""
    base = dict(tipo='humanoide', ombro=(5.2, 6.8, 8.0), cotovelo=0.8, quadril=(2.1, -3.0), joelho=-9.6, pescoco=(8.6, 8.0),
                mand=None, capa=None, cabeca_nomes=(), braco_extra=(), perna_extra=(), extra={}, sem_pernas=False, saia=None, z_perna=8.0)
    base.update(kw)
    return base


RIGS = {
    'rei': dict(fonte='rei_b', **H_(mand=(9.6, 6.0), capa=(7.0, 11.0), esc=0.95)),
    'mineiro': dict(fonte='c_mineiro', **H_(ombro=(8.6, 9.0, 8.0), cotovelo=2.0, quadril=(2.8, -2.0), joelho=-8.4, pescoco=(9.6, 7.0),
                                            cabeca_nomes=('barba', 'capacete', 'aba', 'lampada'), braco_extra=('ombro',), esc=1.0)),
    'bruxa': dict(fonte='bruxa_a', **H_(ombro=(5.6, 5.8, 7.0), cotovelo=0.4, pescoco=(5.0, 7.0), mand=(5.4, 3.4), capa=(7.0, 12.3),
                                        sem_pernas=True, saia=(-3.0, 8.0), esc=1.0,
                                        cabeca_nomes=('cabelo', 'tranca', 'fita', 'aba', 'chapeu', 'fita_chapeu', 'fivela', 'remendo', 'nariz',
                                                      'verruga', 'sobrancelha', 'olho_fundo'),
                                        extra={'corvo': ((2.2, 7.0, 8.7), 'tronco', ('corvo', 'bico', 'cauda')),
                                               'orbita': ((8.0, 4.0, 8.0), None, ('pocao_voa', 'pocao_rolha'))})),
    'gelo': dict(fonte='c_gelo', **H_(mand=(9.6, 6.0), capa=(7.8, 11.3), cabeca_nomes=('barba', 'pingente', 'estalactite'), esc=1.0)),
    'mago': dict(fonte='c_mago', **H_(ombro=(5.4, 6.4, 8.0), cotovelo=1.0, pescoco=(7.0, 7.4), capa=(7.0, 11.2), sem_pernas=True,
                                      cabeca_nomes=('barba', 'barba_ponta', 'sobrancelha', 'nariz', 'chapeu', 'chapeu_aba', 'chapeu_topo',
                                                    'chapeu_ponta', 'estrela'),
                                      braco_extra=('garra',), esc=1.0,
                                      extra={'livro': ((1.8, 3.5, 4.0), None, ('livro', 'paginas'))})),
    'chamas': dict(fonte='c_chamas', **H_(ombro=(6.6, 9.0, 8.0), cotovelo=1.0, pescoco=(10.4, 8.0), sem_pernas=True, esc=1.0,
                                          cabeca_nomes=('chifre', 'coroa_fogo', 'boca'), braco_extra=('ombro',),
                                          extra={'fogo': ((8.0, -8.0, 8.0), None, ('fogo', 'fogo_ponta')),
                                                 'anel1': ((8.0, -8.5, 8.0), None, ()), 'anel2': ((8.0, -0.5, 8.0), None, ())})),
    'piglin': dict(fonte='c_piglin', **H_(ombro=(7.6, 8.6, 8.0), cotovelo=0.5, quadril=(2.6, -3.0), joelho=-8.4, pescoco=(9.0, 8.3),
                                          capa=(10.4, 11.4), cabeca_nomes=('orelha', 'focinho', 'narina', 'presa'),
                                          braco_extra=('ombreira', 'presa_ombro'), esc=1.0)),
    'general': dict(fonte='c_general', **H_(mand=(9.6, 6.0), capa=(9.0, 11.5), cabeca_nomes=('elmo', 'elmo_crista', 'viseira'), esc=1.05)),
    'vazio': dict(fonte='c_vazio', **H_(ombro=(4.0, 11.6, 8.0), cotovelo=3.0, quadril=(1.6, 0.0), joelho=-7.8, pescoco=(13.4, 8.0),
                                        capa=(12.0, 10.0), cabeca_nomes=('cristal', 'aro', 'boca'), esc=1.05,
                                        extra={'orbita': ((8.0, 6.0, 8.0), None, ('estilhaco', 'estilhaco_luz'))})),
    'rainha': dict(fonte='c_rainha', tipo='aranha', esc=0.95),
    # ---- lote 11-20
    'carcereiro': dict(fonte='c_carcereiro', **H_(ombro=(8.8, 10.4, 8.0), cotovelo=2.0, quadril=(3.0, -2.0), joelho=-8.4, pescoco=(11.0, 6.6),
                                                  mand=(11.0, 6.0), cabeca_nomes=('antena', 'antena_luz', 'grade', 'grade_h', 'boca_luz'),
                                                  braco_extra=('ombro', 'ombreira', 'algema', 'corrente'), esc=1.0)),
    'farao': dict(fonte='c_farao', **H_(ombro=(5.4, 7.0, 8.2), cotovelo=2.0, quadril=(2.0, -2.0), joelho=-8.4, pescoco=(7.4, 7.8), capa=(7.0, 11.0),
                                        cabeca_nomes=('nemes', 'nemes_lado', 'listra', 'listra_topo', 'nemes_trás', 'naja', 'naja_olho', 'faixa_rosto', 'barba'),
                                        braco_extra=('faixa_braco',), perna_extra=('faixa_perna',), esc=1.0)),
    'afogado': dict(fonte='c_afogado', **H_(ombro=(6.6, 8.0, 8.0), cotovelo=2.0, quadril=(2.6, -2.0), joelho=-8.4, pescoco=(8.2, 7.8), capa=(8.6, 11.4),
                                            cabeca_nomes=('meia_caveira', 'olho_fundo', 'barba', 'barba_ponta', 'cabelo', 'cabelo_lado', 'coroa', 'coroa_ponta',
                                                          'perola', 'coral', 'coral_ramo'),
                                            braco_extra=('ombreira', 'ombreira2', 'coral_ombro', 'coral_ombro_ramo', 'craca_ombro', 'alga_braco'),
                                            perna_extra=('alga_perna', 'craca'), esc=1.0)),
    'xama': dict(fonte='c_xama', **H_(ombro=(5.2, 7.0, 8.2), cotovelo=2.0, quadril=(2.0, -2.0), joelho=-8.4, pescoco=(7.0, 8.0),
                                      cabeca_nomes=('pena', 'faixa_cocar', 'mascara', 'mascara_testa', 'nariz_masc', 'boca_masc', 'pintura_masc', 'olho_fundo',
                                                    'sobrancelha_masc', 'dente_masc', 'chifre_masc'),
                                      braco_extra=('pintura_braco',), perna_extra=('pintura', 'perneira'), esc=1.0)),
    'carrasco': dict(fonte='c_carrasco', **H_(ombro=(7.4, 8.8, 8.0), cotovelo=1.0, quadril=(2.8, -2.0), joelho=-8.4, pescoco=(9.6, 8.0), capa=(9.8, 11.6),
                                              cabeca_nomes=('capuz', 'capuz_ponta', 'capuz_ponta2', 'capuz_manto', 'furo', 'costura', 'costura_v'),
                                              braco_extra=('ombreira', 'ombreira2', 'espeto', 'ombro', 'tira_ombro'), perna_extra=('bota_tira', 'bota_fivela'), esc=1.0)),
    'coveiro': dict(fonte='c_coveiro', **H_(ombro=(5.4, 7.6, 8.1), cotovelo=2.0, quadril=(2.0, -2.0), joelho=-8.4, pescoco=(8.4, 7.6), mand=(9.0, 6.0),
                                            capa=(-5.8, 10.3),
                                            cabeca_nomes=('aba', 'cartola', 'fita', 'cartola_topo', 'cartola_remendo', 'vela', 'cera', 'chama_vela', 'olho_fundo',
                                                          'nariz', 'olheira', 'ponto', 'cabelo'),
                                            braco_extra=('lampiao', 'lampiao_alca', 'lampiao_luz', 'lampiao_topo', 'lampiao_barra'), perna_extra=('bota_cano',), esc=1.0)),
    'ventos': dict(fonte='c_ventos', **H_(ombro=(6.6, 8.6, 8.0), cotovelo=1.0, pescoco=(9.8, 8.0), sem_pernas=True, esc=1.0,
                                          cabeca_nomes=('capuz', 'coroa_fogo', 'chifre', 'boca'), braco_extra=('ombro',),
                                          extra={'fogo': ((8.0, -10.0, 8.0), None, ('fogo', 'fogo_ponta')),
                                                 'anel1': ((8.0, -9.0, 8.0), None, ()), 'anel2': ((8.0, -2.0, 8.0), None, ())})),
    'devastador': dict(fonte='c_devastador', tipo='quadrupede', esc=1.0, corpo=(8.0, -4.0, 17.5), cabeca=(8.0, -1.0, 6.0), mand=(8.0, -8.0, 0.0),
                       cauda=(8.0, 0.0, 28.0), joelho=-10.0, patas={'fd': (12.6, 9.4), 'fe': (3.4, 9.4), 'td': (12.6, 24.2), 'te': (3.4, 24.2)},
                       cabeca_nomes=('cabeca', 'capacete', 'focinho', 'narina', 'olho', 'chifre', 'chifre2', 'chifre3', 'pescoco'),
                       mand_nomes=('mandibula', 'dente_b', 'presa')),
    'zoglin': dict(fonte='c_zoglin', tipo='quadrupede', esc=1.0, corpo=(8.0, -4.0, 16.0), cabeca=(8.0, -1.0, 5.0), mand=(8.0, -8.0, 2.0),
                   cauda=(8.0, 0.0, 25.0), joelho=-10.0, patas={'fd': (12.0, 9.0), 'fe': (4.0, 9.0), 'td': (11.6, 22.0), 'te': (4.4, 22.0)},
                   cabeca_nomes=('cabeca', 'mascara_osso', 'mascara_racha', 'focinho', 'focinho_ponta', 'narina', 'olho', 'olho_fundo', 'orelha', 'orelha_rasgo'),
                   mand_nomes=('mandibula', 'presa', 'presa_ponta', 'presa_ponta2', 'dente_b')),
    # 18/11: a Gosma virou a Rainha da Gosma (humana da cintura para cima, vestido de gosma no lugar das pernas)
    'gosma': dict(fonte='c_gosma', **H_(ombro=(3.9, 6.2, 8.0), cotovelo=1.4, pescoco=(7.4, 8.0), sem_pernas=True, saia=(-3.0, 8.0), capa=(13.0, 10.9),
                                        cabeca_nomes=('cabelo', 'franja', 'franja_ponta', 'mecha', 'mecha_ponta', 'laco_cabelo', 'coroa', 'coroa_ponta', 'joia',
                                                      'olho', 'pupila_olho', 'brilho_olho', 'cilio', 'bochecha', 'boca_s', 'pescoco'),
                                        braco_extra=('manga_fita',), esc=1.0,
                                        extra={'orbita': ((8.0, 4.0, 8.0), None, ('gota_voa', 'gota_voa_luz'))})),
}
# masmorra -> modelo
MASMORRAS = {'CRIPTA': 'rei', 'NINHO': 'rainha', 'MINA': 'mineiro', 'PANTANO': 'bruxa', 'GELO': 'gelo', 'TORRE': 'mago',
             'FORJA': 'chamas', 'BASTIAO': 'piglin', 'WITHER': 'general', 'CIDADELA': 'vazio',
             'PRISAO': 'carcereiro', 'DESERTO': 'farao', 'QUARTEL': 'devastador', 'VENTOS': 'ventos', 'AFOGADA': 'afogado',
             'SELVA': 'xama', 'CASTELO': 'carrasco', 'COVIL': 'zoglin', 'CEMITERIO': 'coveiro', 'ESGOTO': 'gosma'}


def cx(c):
    return (c.de[0] + c.ate[0]) / 2


def cy(c):
    return (c.de[1] + c.ate[1]) / 2


def comeca(n, nomes):
    return any(n == x or n.startswith(x + '_') or n == x for x in nomes)


def cortar(c, y):
    """Corta um cubo sem giro na altura y: devolve (de baixo, de cima)."""
    if c.rot is not None or not (c.de[1] < y < c.ate[1]):
        return None
    a, b = copy.deepcopy(c), copy.deepcopy(c)
    a.ate = a.ate.copy(); a.ate[1] = y
    b.de = b.de.copy(); b.de[1] = y
    a.uv, b.uv = {}, {}
    a.nome, b.nome = c.nome + '_b', c.nome + '_c'
    return a, b


def humanoide(nome, r, cs):
    """Distribui os cubos nas pecas e devolve {peca: [cubos]} e {peca: (pivo, pai)}."""
    odx, oy, oz = r['ombro']
    qdx, qy = r['quadril']
    ny, nz = r['pescoco']
    pecas = {'tronco': ((8.0, qy if not r['sem_pernas'] else -2.0, 8.0), None),
             'cabeca': ((8.0, ny, nz), 'tronco')}
    if r['mand']:
        pecas['mandibula'] = ((8.0, r['mand'][0], r['mand'][1]), 'cabeca')
    for lado, s in (('d', 1), ('e', -1)):
        pecas['braco_' + lado] = ((8 + s * odx, oy, oz), 'tronco')
        pecas['antebraco_' + lado] = ((8 + s * odx, r['cotovelo'], oz), 'braco_' + lado)
        if not r['sem_pernas']:
            pecas['coxa_' + lado] = ((8 + s * qdx, qy, r['z_perna']), 'tronco')
            pecas['canela_' + lado] = ((8 + s * qdx, r['joelho'], r['z_perna']), 'coxa_' + lado)
    if r['capa']:
        pecas['capa'] = ((8.0, r['capa'][0], r['capa'][1]), 'tronco')
    if r['saia']:
        pecas['saia'] = ((8.0, r['saia'][0], r['saia'][1]), 'tronco')
    for pn, (piv, pai, _) in r['extra'].items():
        pecas[pn] = (piv, pai)
    out = {k: [] for k in pecas}
    fila = list(cs)
    while fila:
        c = fila.pop(0)
        n = c.nome
        lado = 'd' if cx(c) > 8 else 'e'
        dest = None
        for pn, (_, _, nomes) in r['extra'].items():
            if comeca(n, nomes):
                dest = pn
        if dest is None and 'anel1' in pecas and comeca(n, ('vareta', 'vareta_luz')):
            dest = 'anel1' if cy(c) < -5.5 else 'anel2'
        if dest:
            out[dest].append(c)
            continue
        if r['mand'] and comeca(n, ('mandibula', 'dente_b', 'queixo')):
            out['mandibula'].append(c)
            continue
        if r['capa'] and comeca(n, CAPA):
            out['capa'].append(c)
            continue
        if comeca(n, BRACO + ARMA + tuple(r['braco_extra'])) and abs(cx(c) - 8) > 2.6:
            corte = cortar(c, r['cotovelo'])
            if corte:
                fila[:0] = list(corte)
                continue
            out[('antebraco_' if cy(c) < r['cotovelo'] or comeca(n, ARMA) else 'braco_') + lado].append(c)
            continue
        if not r['sem_pernas'] and comeca(n, PERNA + tuple(r['perna_extra'])) and cy(c) < qy + 1.0:
            corte = cortar(c, r['joelho'])
            if corte:
                fila[:0] = list(corte)
                continue
            out[('canela_' if cy(c) < r['joelho'] else 'coxa_') + lado].append(c)
            continue
        if r['saia'] and (comeca(n, ('saia', 'tira', 'musgo', 'vestido', 'tunica')) and cy(c) < r['saia'][0]):
            out['saia'].append(c)
            continue
        if cy(c) >= ny - 0.4 and abs(cx(c) - 8) < 3.8 or comeca(n, tuple(r['cabeca_nomes'])):
            out['cabeca'].append(c)
            continue
        out['tronco'].append(c)
    return out, pecas


def aranha(r, cs):
    pecas = {'corpo': ((8.0, -7.0, 5.0), None), 'abdomen': ((8.0, -6.0, 10.0), 'corpo'), 'presas': ((8.0, -10.0, 1.0), 'corpo')}
    zs = (2.2, 4.8, 7.4, 9.6)
    for lado, s in (('d', 1), ('e', -1)):
        for i, z in enumerate(zs):
            x0 = 8 + s * 5.0
            pecas[f'coxa_{lado}{i}'] = ((x0, -5.6 + i * 0.2, z), 'corpo')
            pecas[f'canela_{lado}{i}'] = ((x0 + s * 7.5, -5.4, z), f'coxa_{lado}{i}')
    out = {k: [] for k in pecas}
    for c in cs:
        n = c.nome
        lado = 'd' if cx(c) > 8 else 'e'
        zc = (c.de[2] + c.ate[2]) / 2
        i = int(np.argmin([abs(zc - z) for z in zs]))
        if comeca(n, ('coxa', 'joelho', 'pelo')):
            out[f'coxa_{lado}{i}'].append(c)
        elif comeca(n, ('canela',)):
            out[f'canela_{lado}{i}'].append(c)
        elif comeca(n, ('abdomen', 'abdomen_topo', 'ampulheta', 'ampulheta2', 'ampulheta3', 'listra', 'fiandeira')):
            out['abdomen'].append(c)
        elif comeca(n, ('quelicera', 'presa')):
            out['presas'].append(c)
        else:
            out['corpo'].append(c)
    return out, pecas


def quadrupede(r, cs):
    pecas = {'corpo': (r['corpo'], None), 'cabeca': (r['cabeca'], 'corpo'), 'mandibula': (r['mand'], 'cabeca'), 'cauda': (r['cauda'], 'corpo')}
    for k, (x, z) in r['patas'].items():
        pecas['coxa_' + k] = ((x, -6.0, z), 'corpo')
        pecas['canela_' + k] = ((x, r['joelho'], z), 'coxa_' + k)
    out = {k: [] for k in pecas}
    fila = list(cs)
    zc = r['corpo'][2]
    while fila:
        c = fila.pop(0)
        n = c.nome
        if comeca(n, ('pata', 'casco', 'joelheira', 'corrente', 'corrente_elo')):
            corte = cortar(c, r['joelho'])
            if corte:
                fila[:0] = list(corte)
                continue
            k = ('f' if (c.de[2] + c.ate[2]) / 2 < zc else 't') + ('d' if cx(c) > 8 else 'e')
            out[('canela_' if cy(c) < r['joelho'] else 'coxa_') + k].append(c)
        elif comeca(n, r['mand_nomes']):
            out['mandibula'].append(c)
        elif comeca(n, r['cabeca_nomes']):
            out['cabeca'].append(c)
        elif comeca(n, ('cauda',)):
            out['cauda'].append(c)
        else:
            out['corpo'].append(c)
    return out, pecas


def gosma(r, cs):
    pecas = {'corpo': ((8.0, -16.0, 8.0), None), 'cima': ((8.0, -5.0, 8.0), 'corpo'), 'mandibula': ((8.0, -11.0, 0.0), 'corpo')}
    out = {k: [] for k in pecas}
    for c in cs:
        n = c.nome
        if comeca(n, ('dente_b',)):
            out['mandibula'].append(c)
        elif cy(c) > -5.0 and not comeca(n, ('pingo', 'bolha')):
            out['cima'].append(c)
        else:
            out['corpo'].append(c)
    return out, pecas


def mover(c, d):
    n = copy.deepcopy(c)
    n.de = c.de + np.array(d)
    n.ate = c.ate + np.array(d)
    n.uv = {}
    if c.rot is not None:
        n.rot = (c.rot[0], c.rot[1], tuple(c.rot[2][i] + d[i] for i in range(3)))
    return n


def gerar(saida, modelos):
    sys.path.insert(0, os.path.join(AQUI, '..', '..', '..', 'plugins', 'coronapack'))
    import bedrock_v2 as BV
    J = os.path.join(saida, 'java', 'assets', 'corona')
    B = os.path.join(saida, 'bedrock')
    mapa, item_tex, corpos, folhas = [], {}, {}, []
    for nome in modelos:
        r = RIGS[nome]
        fonte = r['fonte']
        A.registrar(A.PALETAS[fonte])
        cs = H.CORPOS[fonte](False)
        out, pecas = {'aranha': aranha, 'quadrupede': quadrupede, 'gosma': gosma}.get(r['tipo'], lambda r, cs: humanoide(nome, r, cs))(r, cs)
        info = {'tipo': r['tipo'], 'esc': r['esc'], 'pecas': []}
        previas = []
        # previa do corpo inteiro (pecas no lugar), para conferir que nada sumiu
        tudo = [c for lst in out.values() for c in lst]
        assert len(tudo) >= len(cs), (nome, len(tudo), len(cs))
        for pn, lst in out.items():
            if not lst:
                continue
            piv, pai = pecas[pn]
            d = (8 - piv[0], 8 - piv[1], 8 - piv[2])
            mc = [mover(c, d) for c in lst]
            for c in mc:
                for k in range(3):
                    assert -16 <= c.de[k] and c.ate[k] <= 32, (nome, pn, c.nome, c.de, c.ate)
            ident = f'{nome}_{pn}'
            A.PALETAS[ident] = A.PALETAS[fonte]
            K.BASE_DE[ident] = fonte
            H.MATS[ident] = H.MATS[fonte]
            A.registrar(A.PALETAS[fonte])
            img, bri, esc = A.montar_atlas(mc, ident)
            os.makedirs(os.path.join(J, 'textures', 'item', 'masmorras'), exist_ok=True)
            Image.fromarray(A.alfa_binario(img), 'RGBA').save(os.path.join(J, 'textures', 'item', 'masmorras', ident + '.png'), optimize=True)
            tex_bed = f'textures/entity/corona_masm/{ident}'
            os.makedirs(os.path.join(B, os.path.dirname(tex_bed)), exist_ok=True)
            Image.fromarray(A.textura_bedrock(img, bri), 'RGBA').save(os.path.join(B, tex_bed + '.png'), optimize=True)
            modelo = kit.modelo_java(mc, f'corona:item/masmorras/{ident}', 512, 512, display={})
            modelo['credit'] = f'Corona Server - chefe original {nome}'
            kit.salvar_json(os.path.join(J, 'models', 'masmorras', ident + '.json'), modelo)
            kit.salvar_json(os.path.join(J, 'items', 'masmorras', ident + '.json'), kit.item_java(f'corona:masmorras/{ident}'))
            geo_id = f'geometry.corona.masm_{ident}'
            kit.salvar_json(os.path.join(B, 'models', 'entity', 'corona_masm', f'{ident}.geo.json'), BV.geometria_de_modelo(geo_id, modelo))
            at = BV.attachable_prop(f'corona:masm_{ident}', tex_bed, geo_id)
            at['minecraft:attachable']['description']['materials'] = {'default': 'entity_emissive_alpha', 'enchanted': 'entity_emissive_alpha'}
            kit.salvar_json(os.path.join(B, 'attachables', f'masm_{ident}.json'), at)
            ic = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
            os.makedirs(os.path.join(B, 'textures', 'items', 'corona', 'masm'), exist_ok=True)
            ic.save(os.path.join(B, 'textures', 'items', 'corona', 'masm', f'masm_{ident}.png'))
            item_tex[f'masm_{ident}'] = {'textures': f'textures/items/corona/masm/masm_{ident}'}
            mapa.append({'type': 'definition', 'model': f'corona:masmorras/{ident}', 'bedrock_identifier': f'corona:masm_{ident}',
                         'display_name': f'Chefe {ident}', 'bedrock_options': {'icon': f'masm_{ident}', 'creative_category': 'equipment',
                                                                               'protection_value': 1}})
            info['pecas'].append({'nome': pn, 'pivo': list(piv), 'pai': pai})
            previas.append(kit.previa(mc, A.alfa_binario(img), 160, 8, escala=5, fundo=(150, 158, 168)))
        # ordem: pai sempre antes do filho
        ordem, feitos = [], set()
        while len(ordem) < len(info['pecas']):
            for p in info['pecas']:
                if p['nome'] not in feitos and (p['pai'] is None or p['pai'] in feitos):
                    ordem.append(p)
                    feitos.add(p['nome'])
        info['pecas'] = ordem
        corpos[nome] = info
        folhas.append(kit.folha(previas, (150, 158, 168)))
        print(nome, 'pecas', len(info['pecas']), 'cubos', len(tudo))
    # item vazio: esconde o arco/espada do mob de verdade (o modelo 3D segura a arma dele)
    kit.salvar_json(os.path.join(J, 'items', 'masmorras', 'vazio.json'), {'model': {'type': 'minecraft:empty'}})
    kit.salvar_json(os.path.join(saida, 'corona-masmorras3d.json'), {'format_version': 2, 'items': {'minecraft:leather_helmet': mapa}})
    kit.salvar_json(os.path.join(saida, 'item_texture_masm.json'), item_tex)
    kit.salvar_json(os.path.join(saida, 'corpos.json'), {'masmorras': MASMORRAS, 'corpos': corpos})
    W = max(f.width for f in folhas)
    tudo = Image.new('RGB', (W, sum(f.height for f in folhas)), (150, 158, 168))
    y = 0
    for f in folhas:
        tudo.paste(f.convert('RGB'), (0, y))
        y += f.height
    tudo.save(os.path.join(saida, 'previa_pecas.png'))


if __name__ == '__main__':
    gerar(SAIDA, sys.argv[2:] or list(RIGS))
