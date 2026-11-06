"""Monstros 3D das Backrooms: Bacteria (Nivel 0), Uivador (Nivel 2), Cao (Nivel 3), Festeiro (Nivel Fun) e Sombra (Nivel 6).
Mesmo kit do Nao Chore: cubos + quadros (idle, walk1, walk2, grito) + olhos (modelo separado que brilha) + pintor + rosto do susto.
"""
import math, os, sys
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'skins', 'monstros'))
from base import fbm, ridged, rampa, mix, C, ss, elipse, dentro, dentes, vnoise, hash2  # noqa: E402
from kit import Cubo, girado  # noqa: E402
from naochore import luz, borda, sangue, respingo, dente_cor, boca_fundo, pano_rasgado, escorrido, cabelo_longo, olhos_brilho, pele_detalhe  # noqa: E402

MONSTROS = {}


def _grupo(lista, grupos, eixo, ang, orig):
    return [girado(c, eixo, ang, orig) if c.grupo in grupos else c for c in lista]


# ====================================================================== BACTERIA (Nivel 0)
# Alta e magra (escala 1,6 = quase 3 m), cinza molhada, cabeca comprida sem olhos (dois pontos vermelhos fundos),
# boca que e um rasgo vertical; bracos ate o chao com garras. No grito o rasgo abre a cabeca inteira.

def bacteria_cubos():
    """Quase 3 m (escala 1,6): pernas finas dobradas como de ave, tronco comprido e curvado para a frente, ombros
    ossudos, bracos ate o chao com garras, cabeca bulbosa sem pescoco. Nada de proporcao de jogador."""
    cs = []
    # pernas: canela vertical fina, coxa inclinada para tras (dobra de ave), pe comprido
    for lado, sx in (('d', -1), ('e', 1)):
        g = f'perna_{lado}'
        cs.append(Cubo(f'canela_{lado}', (sx * 3 - 1, -16, -1), (sx * 3 + 1, -7, 1), 'pele', grupo=g))
        cs.append(Cubo(f'coxa_{lado}', (sx * 3 - 1.2, -8, -1.2), (sx * 3 + 1.2, -1, 1.2), 'pele', grupo=g, rot=('x', 22.5, (0, -1, 0))))
        cs.append(Cubo(f'pe_{lado}', (sx * 3 - 1.4, -16, -1), (sx * 3 + 1.4, -14.6, 4.5), 'garra', grupo=g))
    cs.append(Cubo('quadril', (-3, -2, -1.5), (3, 1, 1.5), 'pele', grupo='tronco'))
    # tronco comprido, curvado para a frente
    cs.append(Cubo('tronco', (-2.8, 1, -1.8), (2.8, 13, 1.8), 'pele', grupo='tronco', rot=('x', 22.5, (0, 1, 0))))
    cs.append(Cubo('peito', (-3.6, 8, -2.4), (3.6, 14, 2.4), 'pele', grupo='tronco', rot=('x', 22.5, (0, 1, 0))))
    cs.append(Cubo('ombros', (-7, 12.5, -2), (7, 15, 2.2), 'pele', grupo='tronco', rot=('x', 22.5, (0, 1, 0))))
    # bracos: muito compridos, ate o chao (o tronco inclinado joga os ombros para a frente ~ z+5)
    for lado, sx in (('d', -1), ('e', 1)):
        g = f'braco_{lado}'
        cs.append(Cubo(f'braco_{lado}', (sx * 6.2 - 1, -9, 3.5), (sx * 6.2 + 1, 13.5, 5.5), 'pele', grupo=g))
        cs.append(Cubo(f'mao_{lado}', (sx * 6.2 - 1.5, -13, 3.2), (sx * 6.2 + 1.5, -9, 5.8), 'mao', grupo=g))
        for k, dx in enumerate((-1.3, -0.3, 0.7)):
            cs.append(Cubo(f'garra_{lado}{k}', (sx * 6.2 + dx, -16 + (k % 2) * 0.8, 4.2), (sx * 6.2 + dx + 0.6, -13, 4.8), 'garra', grupo=g))
    # cabeca bulbosa direto nos ombros (sem pescoco), tombada para a frente
    cs.append(Cubo('cabeca', (-3.5, 14, 1), (3.5, 26, 9), 'rosto', grupo='cabeca', rot=('x', 22.5, (0, 14, 3))))
    cs.append(Cubo('nuca', (-3, 20, -2.5), (3, 26.5, 1.5), 'pele', grupo='cabeca', rot=('x', 22.5, (0, 14, 3))))
    return cs


def bacteria_quadros(cs):
    def anda(sinal):
        out = _grupo(cs, ('perna_d',), 'x', 22.5 * sinal, (0, -1, 0))
        out = _grupo(out, ('perna_e',), 'x', -22.5 * sinal, (0, -1, 0))
        out = _grupo(out, ('braco_d',), 'x', -22.5 * sinal, (0, 13, 4.5))
        return _grupo(out, ('braco_e',), 'x', 22.5 * sinal, (0, 13, 4.5))

    def grito():
        out = [c.com(pele='rosto_grito') if c.nome == 'cabeca' else c for c in cs]
        out = _grupo(out, ('braco_d', 'braco_e'), 'x', -45, (0, 13, 4.5))
        return [girado(c, 'x', 0.0, (0, 14, 3)) if False else c for c in out]
    return {'idle': cs, 'walk1': anda(1), 'walk2': anda(-1), 'grito': grito()}


def bacteria_olhos(cs):
    # olhos fundos na frente da cabeca (a cabeca esta inclinada 22,5 em x: os olhos acompanham)
    o = [Cubo('olho_d', (-2.1, 20.5, 9.0), (-0.9, 21.5, 9.5), 'olho', so=('south', 'up', 'down', 'east', 'west'), rot=('x', 22.5, (0, 14, 3))),
         Cubo('olho_e', (0.9, 20.5, 9.0), (2.1, 21.5, 9.5), 'olho', so=('south', 'up', 'down', 'east', 'west'), rot=('x', 22.5, (0, 14, 3)))]
    return {'idle': o, 'grito': o}


def bacteria_pintor(P):
    x, y, z, s, t = P['x'], P['y'], P['z'], P['s'], P['t']
    pele, face, nome = P['pele'], P['face'], P['nome']
    if pele == 'olho':
        return olhos_brilho(P, '#ff1a10', '#ffd0b0')
    # pele cinza-escura, molhada (brilho em manchas), veias vermelhas, placas mais claras
    n = fbm(x * 0.9, y * 0.9, z * 0.9, 4, 0.5, 101)
    c = rampa(n, [(0, '#17181c'), (0.4, '#2e3036'), (0.75, '#4a4c52'), (1, '#6a6b6e')])
    molhado = ss(0.75, 0.95, fbm(x * 2.2, y * 2.2, z * 2.2, 2, 1, 102))
    c = mix(c, C('#9ea0a6'), molhado * 0.35)
    veia = ss(0.68, 0.92, ridged(x * 1.0, y * 0.45, z * 1.0, 3, 0.8, 103))
    c = mix(c, C('#5a1014'), veia * 0.6)
    placa = ss(0.6, 0.8, fbm(x * 0.5, y * 0.5, z * 0.5, 3, 1, 104))
    c = mix(c, C('#56585e'), placa * 0.4)
    c = pele_detalhe(P, c, 105, 0.8)
    if pele == 'garra':
        return mix(c, C('#0a0a0c'), ss(0.0, 1.0, t) * 0.9) * luz(P), 1
    if pele == 'mao':
        return mix(c, C('#111114'), ss(0.4, 1.0, t) * 0.7) * luz(P), 1
    if pele in ('rosto', 'rosto_grito') and face == 'south':
        grito = pele == 'rosto_grito'
        # cabeca lisa: sem nariz, dois buracos fundos (os olhos brilham por cima), rasgo vertical no meio
        for cx in (0.27, 0.73):
            d = elipse(s, t, cx, 0.6, 0.12, 0.09)
            c = c * (1 - 0.7 * dentro(d / 1.4, 0.4))[..., None]
            c = mix(c, C('#050406'), dentro(d, 0.1))
        if not grito:
            rasgo = (np.abs(s - 0.5 - 0.02 * np.sin(t * 25)) < 0.035 + 0.03 * ss(0.4, 0.9, t)) & (t > 0.3) & (t < 0.97)
            c = np.where(rasgo[..., None], boca_fundo(np.abs(s - 0.5) / 0.06), c)
            fio = ss(0.75, 0.95, ridged(s * 20, t * 6, 0, 2, 1, 106)) * rasgo
            c = mix(c, C('#8c1c22'), fio * 0.8)
            c = mix(c, sangue(P, 1), 0.7 * escorrido(s, t, [(0.5, 0.9, 0.1)], 1, 0.02).clip(0, 1))
        else:
            # o rasgo abriu a cabeca ao meio: boca vertical enorme cheia de agulhas
            d = elipse(s, t, 0.5, 0.62, 0.3, 0.4)
            c = c * (1 - 0.5 * dentro(d / 1.25, 0.3))[..., None]
            c = mix(c, boca_fundo(1 - d), dentro(d, 0.04))
            ang = np.arctan2((t - 0.62) / 0.4, (s - 0.5) / 0.3)
            m, p, _ = dentes(ang, 1 - d, 0.42, 0.22, afiado=2.2)
            m &= d < 1
            c = np.where(m[..., None], dente_cor(p, '#d8d2c0'), c)
            c = mix(c, sangue(P, 2), 0.9 * escorrido(s, t, [(0.3, 0.9, 0.1), (0.5, 0.97, 0.03), (0.7, 0.92, 0.08)], 2).clip(0, 1))
        return c * luz(P, 0.15) * borda(P, 0.12), 1
    if nome == 'peito' and face == 'south':
        for k in range(5):
            ty = 0.15 + k * 0.17
            q = (s - 0.5) / 0.5
            sulco = np.exp(-((t - ty - 0.06 * q ** 2) / 0.025) ** 2) * (np.abs(q) > 0.12)
            c = c * (1 - 0.45 * sulco)[..., None]
    return c * luz(P) * borda(P, 0.25), 1


def bacteria_rosto(P):
    return bacteria_pintor(dict(P, pele='rosto_grito', face='south', nome='cabeca'))


MONSTROS['bacteria'] = dict(cubos=bacteria_cubos, quadros=bacteria_quadros, olhos=bacteria_olhos, pintor=bacteria_pintor, rosto=bacteria_rosto,
                            escala=1.6, altura_pe=16, rosto_cubo=('cabeca', (-3.5, 14, 1), (3.5, 26, 9)))


# ====================================================================== UIVADOR (Nivel 2)
# Branco, esqueletico, bracos ate o chao, boca escancarada o tempo todo, olhos pretos chorando. Costelas a mostra.

def uivador_cubos():
    """Esqueletico, branco, curvado 45 graus para a frente com a cabeca na altura do peito de uma pessoa; bracos de
    quase 2 m que arrastam no chao; mandibula caida."""
    cs = []
    cs.append(Cubo('perna_d', (-3.6, -16, -1.5), (-0.8, 0, 1.5), 'pele', grupo='perna_d'))
    cs.append(Cubo('perna_e', (0.8, -16, -1.5), (3.6, 0, 1.5), 'pele', grupo='perna_e'))
    cs.append(Cubo('quadril', (-4, -1, -2), (4, 2, 2), 'pele', grupo='tronco'))
    # tronco inclinado 45 para a frente: sobe de y=2 e vai para +z
    cs.append(Cubo('tronco', (-3.5, 2, -2), (3.5, 15, 2), 'costela', grupo='tronco', rot=('x', 45.0, (0, 2, 0))))
    cs.append(Cubo('ombros', (-6.5, 13, -2.4), (6.5, 16, 2.4), 'pele', grupo='tronco', rot=('x', 45.0, (0, 2, 0))))
    cs.append(Cubo('espinha', (-1, 2, -2.9), (1, 15, -2), 'espinha', grupo='tronco', rot=('x', 45.0, (0, 2, 0))))
    # com o tronco a 45, os ombros ficam em ~ (y 11, z 9): bracos caem dali ate o chao
    for lado, sx in (('d', -1), ('e', 1)):
        g = f'braco_{lado}'
        cs.append(Cubo(f'braco_{lado}', (sx * 6 - 1.2, -11, 8), (sx * 6 + 1.2, 11.5, 10.4), 'braco', grupo=g))
        cs.append(Cubo(f'mao_{lado}', (sx * 6 - 2, -16, 7.5), (sx * 6 + 2, -11, 11), 'mao', grupo=g))
    # cabeca na ponta do tronco (y ~ 12, z ~ 10), olhando para a frente/cima; mandibula caida
    cs.append(Cubo('pescoco', (-1.5, 10.5, 8.5), (1.5, 13.5, 11.5), 'pele', grupo='cabeca'))
    cs.append(Cubo('cabeca', (-4, 12.5, 7), (4, 21, 15), 'rosto', grupo='cabeca'))
    cs.append(Cubo('queixo', (-3.5, 8.5, 8.5), (3.5, 12.6, 15.3), 'queixo', grupo='queixo', rot=('x', 22.5, (0, 12.6, 8.5))))
    return cs


def uivador_quadros(cs):
    def anda(sinal):
        out = _grupo(cs, ('perna_d',), 'x', 22.5 * sinal, (0, 0, 0))
        out = _grupo(out, ('perna_e',), 'x', -22.5 * sinal, (0, 0, 0))
        out = _grupo(out, ('braco_d',), 'x', -22.5 * sinal, (0, 11, 9))
        return _grupo(out, ('braco_e',), 'x', 22.5 * sinal, (0, 11, 9))

    def grito():
        out = [c.com(pele='rosto_grito') if c.nome == 'cabeca' else c for c in cs]
        out = _grupo(out, ('braco_d', 'braco_e'), 'x', -45, (0, 11, 9))
        return [girado(c, 'x', 45.0, (0, 12.6, 8.5)) if c.grupo == 'queixo' else c for c in out]   # a boca abre mais
    return {'idle': cs, 'walk1': anda(1), 'walk2': anda(-1), 'grito': grito()}


def uivador_olhos(cs):
    o = [Cubo('olho_d', (-2.6, 17.0, 15.0), (-1.2, 18.3, 15.5), 'olho', so=('south', 'up', 'down', 'east', 'west')),
         Cubo('olho_e', (1.2, 17.0, 15.0), (2.6, 18.3, 15.5), 'olho', so=('south', 'up', 'down', 'east', 'west'))]
    return {'idle': o, 'grito': [c.com(pele='olho_grito') for c in o]}


def uivador_pintor(P):
    x, y, z, s, t = P['x'], P['y'], P['z'], P['s'], P['t']
    pele, face, nome = P['pele'], P['face'], P['nome']
    if pele == 'olho':
        d = elipse(s, t, 0.5, 0.5, 0.5, 0.5)
        return rampa(1 - np.clip(d, 0, 1), [(0, '#050507'), (0.8, '#0a0a10'), (1, '#303038')]), np.where(d < 1, 1.0, 0.0)
    if pele == 'olho_grito':
        return olhos_brilho(P, '#f0f0ff', '#ffffff')
    # pele branca de cadaver, fina, mostrando o cinza por baixo; manchas azuladas
    n = fbm(x * 0.9, y * 0.9, z * 0.9, 4, 0.5, 111)
    c = rampa(n, [(0, '#8e9095'), (0.35, '#c4c6c8'), (0.7, '#e6e6e4'), (1, '#f4f3f0')])
    veia = ss(0.7, 0.93, ridged(x * 1.0, y * 0.5, z * 1.0, 3, 0.8, 112))
    c = mix(c, C('#4a4f66'), veia * 0.4)
    mancha = ss(0.62, 0.82, fbm(x * 0.6, y * 0.6, z * 0.6, 3, 1, 113))
    c = mix(c, C('#6f6a78'), mancha * 0.35)
    c = pele_detalhe(P, c, 114, 0.7)
    if pele == 'mao':
        return mix(c, C('#2a2630'), ss(0.3, 1.0, t) * 0.7) * luz(P), 1
    if pele == 'espinha':
        per = np.floor(t * 10)
        seg = np.abs(t * 10 - per - 0.5) < 0.36
        return np.where(seg[..., None], rampa(fbm(s * 4, t * 40, 0, 2, 1, 116), [(0, '#cfc6ad'), (1, '#f1ebdb')]), C('#3a2a30') * np.ones_like(c)) * luz(P, 0.1), 1
    if pele == 'costela':
        if face in ('south', 'north', 'east', 'west'):
            for k in range(6):
                ty = 0.12 + k * 0.14
                q = (s - 0.5) / 0.5
                sulco = np.exp(-((t - ty - 0.05 * q ** 2) / 0.03) ** 2) * (np.abs(q) > 0.08 if face in ('south', 'north') else 1)
                c = c * (1 - 0.6 * sulco)[..., None]
            if face == 'south':
                fundo = elipse(s, t, 0.5, 0.75, 0.22, 0.2)
                c = mix(c, C('#1c1216'), dentro(fundo, 0.3) * 0.8)   # barriga funda, escura
        return c * luz(P) * borda(P, 0.25), 1
    if pele == 'queixo':
        if face == 'south':
            cost = (s < 0.06) | (s > 0.94)
            c = np.where(cost[..., None], mix(c, C('#4a2a2a'), 0.7), c)
            c = mix(c, sangue(P, 3), 0.8 * escorrido(s, t, [(0.3, 0.0, 0.7), (0.62, 0.0, 0.5), (0.5, 0.0, 0.9)], 3, 0.02).clip(0, 1))
            return c * luz(P, 0.15) * borda(P, 0.15), 1
        if face == 'up':
            d = elipse(s, t, 0.5, 0.45, 0.42, 0.42)
            c = boca_fundo(1 - d * 0.6)
            m, p, _ = dentes(s, 1 - t, 0.35, 0.09, afiado=1.3)
            m &= (s > 0.08) & (s < 0.92)
            c = np.where(m[..., None], dente_cor(p, '#ddd4bd'), c)
            return c, 1
        return c * luz(P) * borda(P, 0.25), 1
    if pele in ('rosto', 'rosto_grito') and face == 'south':
        grito = pele == 'rosto_grito'
        c = np.clip(c * 1.05, 0, 1)
        for cx in (0.3, 0.7):
            d = elipse(s, t, cx, 0.4, 0.15, 0.17)
            c = c * (1 - 0.6 * dentro(d / 1.5, 0.5))[..., None]
            c = mix(c, C('#050507'), dentro(d, 0.06))
            for k, dx in enumerate((-0.07, -0.02, 0.03, 0.08)):
                comp = 0.3 + 0.5 * hash2(k, int(cx * 100), 115)
                lag = (np.abs(s - (cx + dx) - 0.008 * np.sin(t * 29 + k)) < 0.006) & (t > 0.5) & (t < 0.5 + comp)
                c = np.where(lag[..., None], C('#101014') * np.ones_like(c), c)
        nd = elipse(s, t, 0.5, 0.62, 0.035, 0.04)
        c = mix(c, C('#2a2226'), dentro(nd, 0.3) * 0.8)
        # boca: buraco preto do queixo para cima (a mandibula separada completa a boca)
        d = elipse(s, t, 0.5, 0.98, 0.3, 0.3 if grito else 0.2)
        c = c * (1 - 0.5 * dentro(d / 1.2, 0.3))[..., None]
        c = mix(c, boca_fundo(1 - d), dentro(d, 0.04))
        ang = np.arctan2((t - 0.98) / 0.3, (s - 0.5) / 0.3)
        m, p, _ = dentes(ang, 1 - d, 0.3, 0.3, afiado=1.3)
        m &= (d < 1) & (t < 0.98)
        c = np.where(m[..., None], dente_cor(p, '#ddd4bd'), c)
        return c * luz(P, 0.15) * borda(P, 0.12), 1
    return c * luz(P) * borda(P, 0.25), 1


def uivador_rosto(P):
    return uivador_pintor(dict(P, pele='rosto_grito', face='south', nome='cabeca'))


MONSTROS['uivador'] = dict(cubos=uivador_cubos, quadros=uivador_quadros, olhos=uivador_olhos, pintor=uivador_pintor, rosto=uivador_rosto,
                           escala=1.2, altura_pe=16, rosto_cubo=('cabeca', (-4, 12.5, 7), (4, 21, 15)))


# ====================================================================== CAO (Nivel 3)
# Gente de quatro: tronco na horizontal, bracos e pernas como patas, cabeca de caveira com cabelo comprido,
# espinha saltada e pele palida rasgada. Anda de quatro; no grito a boca da caveira abre.

def cao_cubos():
    """Gente de quatro virada bicho: coluna arqueada para cima, cabeca baixa e para a frente, focinho de caveira,
    patas compridas com garras, costelas e feridas."""
    cs = []
    for lado, sx in (('d', -1), ('e', 1)):
        cs.append(Cubo(f'pata_f{lado}', (sx * 3 - 1.4, -16, 4.5), (sx * 3 + 1.4, -3, 7.5), 'pele', grupo=f'pata_f{lado}'))
        cs.append(Cubo(f'mao_f{lado}', (sx * 3 - 1.8, -16, 4), (sx * 3 + 1.8, -14.6, 9.5), 'mao', grupo=f'pata_f{lado}'))
        cs.append(Cubo(f'pata_t{lado}', (sx * 3 - 1.4, -16, -8.5), (sx * 3 + 1.4, -5, -5.5), 'pele', grupo=f'pata_t{lado}', rot=('x', -22.5, (0, -5, -7))))
        cs.append(Cubo(f'pe_t{lado}', (sx * 3 - 1.8, -16, -10.5), (sx * 3 + 1.8, -14.6, -6), 'mao', grupo=f'pata_t{lado}'))
    cs.append(Cubo('quadril', (-4.5, -6, -11), (4.5, 0, -6), 'pele', grupo='tronco'))
    cs.append(Cubo('tronco', (-4.2, -5, -7), (4.2, 1.5, 6), 'costas', grupo='tronco', rot=('x', 22.5, (0, -2, -6))))   # arqueado: sobe para tras
    cs.append(Cubo('espinha', (-1.1, 1.5, -7), (1.1, 3.2, 5), 'espinha', grupo='tronco', rot=('x', 22.5, (0, -2, -6))))
    cs.append(Cubo('peito', (-4.6, -6.5, 3), (4.6, -1, 7.5), 'costas', grupo='tronco'))
    cs.append(Cubo('pescoco', (-1.8, -7, 6.5), (1.8, -3, 10.5), 'pele', grupo='cabeca', rot=('x', -22.5, (0, -5, 7))))
    cs.append(Cubo('cabeca', (-3.6, -8.5, 9), (3.6, -1.5, 15), 'rosto', grupo='cabeca'))
    cs.append(Cubo('focinho', (-2.4, -8, 15), (2.4, -3.5, 19), 'rosto', grupo='cabeca'))
    cs.append(Cubo('queixo', (-2.2, -9.6, 11), (2.2, -8, 19.2), 'queixo', grupo='queixo'))
    cs.append(Cubo('cabelo', (-4.2, -10, 8.4), (4.2, -0.9, 14.2), 'cabelo', grupo='cabeca'))
    return cs


def cao_quadros(cs):
    def anda(sinal):
        out = _grupo(cs, ('pata_fd', 'pata_te'), 'x', 22.5 * sinal, (0, -3, 0))
        return _grupo(out, ('pata_fe', 'pata_td'), 'x', -22.5 * sinal, (0, -3, 0))

    def grito():
        out = [c.com(pele='rosto_grito') if c.nome == 'cabeca' else c for c in cs]
        out = _grupo(out, ('queixo',), 'x', -45, (0, -8, 11))     # mandibula abre
        return _grupo(out, ('cabeca',), 'x', -22.5, (0, -5, 7))   # cabeca levanta
    return {'idle': cs, 'walk1': anda(1), 'walk2': anda(-1), 'grito': grito()}


def cao_olhos(cs):
    o = [Cubo('olho_d', (-2.5, -4.5, 15.0), (-1.1, -3.3, 15.5), 'olho', so=('south', 'up', 'down', 'east', 'west')),
         Cubo('olho_e', (1.1, -4.5, 15.0), (2.5, -3.3, 15.5), 'olho', so=('south', 'up', 'down', 'east', 'west'))]
    return {'idle': o, 'grito': [girado(c, 'x', -22.5, (0, -5, 7)) for c in o]}


def cao_pintor(P):
    x, y, z, s, t = P['x'], P['y'], P['z'], P['s'], P['t']
    pele, face, nome = P['pele'], P['face'], P['nome']
    if pele == 'olho':
        return olhos_brilho(P, '#f8f4e8', '#ffffff')
    if pele == 'cabelo':
        c, a = cabelo_longo(P, 121, '#06060a', y_fim=-10, dens=0.9, frente=(0.18, 0.3), topo_y=-1.2, cheio_y=-6)
        return c, a
    n = fbm(x * 0.9, y * 0.9, z * 0.9, 4, 0.5, 122)
    c = rampa(n, [(0, '#8f8578'), (0.4, '#c8bfb0'), (0.75, '#e2dbcd'), (1, '#efe9dd')])
    veia = ss(0.7, 0.93, ridged(x * 1.1, y * 0.5, z * 1.1, 3, 0.8, 123))
    c = mix(c, C('#5a3f44'), veia * 0.35)
    c = pele_detalhe(P, c, 124, 0.8)
    # feridas: rasgos vermelhos com borda escura
    rasgo = ss(0.8, 0.94, ridged(x * 0.9, y * 0.9, z * 0.9, 3, 1, 125))
    c = mix(c, C('#3a0a0c'), rasgo * 0.9)
    c = mix(c, C('#7a1518'), ss(0.86, 0.96, ridged(x * 0.9, y * 0.9, z * 0.9, 3, 1, 125)) * 0.9)
    if pele == 'mao':
        return mix(c, C('#2a1d1d'), ss(0.3, 1.0, t) * 0.75) * luz(P), 1
    if pele == 'espinha':
        if face == 'up':
            per = np.floor(t * 9)
            seg = np.abs(t * 9 - per - 0.5) < 0.38
            c = np.where(seg[..., None], rampa(fbm(s * 4, t * 40, 0, 2, 1, 126), [(0, '#c9bfa6'), (1, '#efe8d6')]), C('#3a0d10') * np.ones_like(c))
            return c * luz(P, 0.1), 1
        return mix(c, C('#4a1216'), 0.5) * luz(P), 1
    if pele == 'costas' and face == 'up':
        c = mix(c, C('#3a0d10'), ss(0.0, 0.2, np.abs(s - 0.5)) * 0 + (np.abs(s - 0.5) < 0.16) * 0.8)
        return c * luz(P) * borda(P, 0.2), 1
    if pele == 'queixo':
        if face == 'south':
            c = mix(c, C('#efe9dd'), 0.5)
            m, p, _ = dentes(s, t, 0.5, 0.11, afiado=1.2)
            m &= (s > 0.08) & (s < 0.92)
            c = np.where(m[..., None], dente_cor(p, '#e6dcc4'), c)
            return c * luz(P, 0.15), 1
        if face == 'up':
            d = elipse(s, t, 0.5, 0.45, 0.42, 0.42)
            c = boca_fundo(1 - d * 0.6)
            m, p, _ = dentes(s, 1 - t, 0.35, 0.1, afiado=1.3)
            c = np.where((m & (s > 0.1) & (s < 0.9))[..., None], dente_cor(p, '#e6dcc4'), c)
            return c, 1
        return c * luz(P) * borda(P, 0.25), 1
    if pele in ('rosto', 'rosto_grito') and face == 'south':
        grito = pele == 'rosto_grito'
        # caveira: osso claro, orbitas pretas enormes, nariz triangular, dentes na borda de baixo
        osso = rampa(fbm(x * 1.5, y * 1.5, z * 1.5, 3, 1, 127), [(0, '#b9ad96'), (0.5, '#ded4bf'), (1, '#f1ead9')])
        c = mix(c, osso, 0.85)
        rach = ss(0.8, 0.95, ridged(x * 2, y * 2, z * 2, 3, 1, 128))
        c = c * (1 - 0.6 * rach)[..., None]
        for cx in (0.28, 0.72):
            d = elipse(s, t, cx, 0.4, 0.17, 0.19)
            c = c * (1 - 0.6 * dentro(d / 1.4, 0.4))[..., None]
            c = mix(c, C('#040305'), dentro(d, 0.06))
        nar = (np.abs(s - 0.5) < 0.09 * (t - 0.52) / 0.18) & (t > 0.52) & (t < 0.72)
        c = np.where(nar[..., None], C('#0a0608') * np.ones_like(c), c)
        if not grito:
            m, p, _ = dentes(s, 1 - t, 0.1, 0.11, afiado=1.2)
            m &= (s > 0.12) & (s < 0.88) & (t > 0.82)
            c = np.where(m[..., None], dente_cor(p, '#e6dcc4'), c)
            c = mix(c, sangue(P, 4), 0.7 * escorrido(s, t, [(0.3, 0.85, 0.15), (0.66, 0.85, 0.15)], 4).clip(0, 1))
        else:
            d = elipse(s, t, 0.5, 1.0, 0.32, 0.3)
            c = mix(c, boca_fundo(1 - d), dentro(d, 0.04))
            ang = np.arctan2((t - 1.0) / 0.3, (s - 0.5) / 0.32)
            m, p, _ = dentes(ang, 1 - d, 0.3, 0.3, afiado=1.4)
            m &= (d < 1) & (t < 1.0)
            c = np.where(m[..., None], dente_cor(p, '#e6dcc4'), c)
            c = mix(c, sangue(P, 5), 0.9 * escorrido(s, t, [(0.25, 0.8, 0.2), (0.75, 0.8, 0.2), (0.5, 0.9, 0.1)], 5).clip(0, 1))
        return c * luz(P, 0.15) * borda(P, 0.12), 1
    if pele in ('rosto', 'rosto_grito'):
        osso = rampa(fbm(x * 1.5, y * 1.5, z * 1.5, 3, 1, 127), [(0, '#b9ad96'), (0.5, '#ded4bf'), (1, '#f1ead9')])
        return mix(c, osso, 0.85) * luz(P) * borda(P, 0.25), 1
    return c * luz(P) * borda(P, 0.25), 1


def cao_rosto(P):
    return cao_pintor(dict(P, pele='rosto_grito', face='south', nome='cabeca'))


MONSTROS['cao'] = dict(cubos=cao_cubos, quadros=cao_quadros, olhos=cao_olhos, pintor=cao_pintor, rosto=cao_rosto,
                       escala=1.1, altura_pe=16, rosto_cubo=('cabeca', (-3.6, -8.5, 9), (3.6, -1.5, 15)))


# ====================================================================== FESTEIRO (Nivel Fun)
# Amarelo vivo, sorriso enorme pintado, olhos "=)" pretos, chapeu de festa, respingos de sangue. Simpatico demais.

def festeiro_cubos():
    """Pernalta e desengoncado: pernas finas e compridas, tronco estreito, bracos finos ate o joelho e uma cabeca
    redonda enorme com o sorriso pintado. Chapeu de festa e balao."""
    cs = []
    cs.append(Cubo('perna_d', (-3.4, -16, -1.4), (-0.6, 0, 1.4), 'pele', grupo='perna_d'))
    cs.append(Cubo('perna_e', (0.6, -16, -1.4), (3.4, 0, 1.4), 'pele', grupo='perna_e'))
    cs.append(Cubo('tronco', (-3.2, 0, -1.8), (3.2, 10, 1.8), 'pele', grupo='tronco'))
    cs.append(Cubo('braco_d', (-5.6, -6, -1.3), (-3.2, 10, 1.3), 'pele', grupo='braco_d'))
    cs.append(Cubo('braco_e', (3.2, -6, -1.3), (5.6, 10, 1.3), 'pele', grupo='braco_e'))
    cs.append(Cubo('mao_d', (-6, -9, -1.5), (-3, -6, 1.5), 'mao', grupo='braco_d'))
    cs.append(Cubo('mao_e', (3, -9, -1.5), (6, -6, 1.5), 'mao', grupo='braco_e'))
    cs.append(Cubo('pescoco', (-1.2, 10, -1.2), (1.2, 11.5, 1.2), 'pele', grupo='cabeca'))
    cs.append(Cubo('cabeca', (-6, 11.5, -6), (6, 23.5, 6), 'rosto', grupo='cabeca'))
    cs.append(Cubo('chapeu1', (-3, 23.5, -3), (3, 26.5, 3), 'chapeu', grupo='cabeca'))
    cs.append(Cubo('chapeu2', (-1.8, 26.5, -1.8), (1.8, 29.5, 1.8), 'chapeu', grupo='cabeca'))
    cs.append(Cubo('pompom', (-1.3, 29.3, -1.3), (1.3, 31.6, 1.3), 'pompom', grupo='cabeca'))
    cs.append(Cubo('balao_fio', (6.2, -7, 0), (6.6, 20, 0.4), 'fio', grupo='braco_e'))
    cs.append(Cubo('balao', (4.2, 20, -2.4), (8.8, 26, 2.4), 'balao', grupo='braco_e'))
    return cs


def festeiro_quadros(cs):
    def anda(sinal):
        out = _grupo(cs, ('perna_d',), 'x', 22.5 * sinal, (0, 0, 0))
        out = _grupo(out, ('perna_e',), 'x', -22.5 * sinal, (0, 0, 0))
        out = _grupo(out, ('braco_d',), 'x', -22.5 * sinal, (0, 9, 0))
        return _grupo(out, ('cabeca',), 'z', 22.5 * sinal, (0, 11.5, 0))   # cabecona balancando, alegre demais

    def grito():
        out = [c.com(pele='rosto_grito') if c.nome == 'cabeca' else c for c in cs]
        out = _grupo(out, ('braco_d',), 'x', -45, (0, 9, 0))
        return _grupo(out, ('cabeca',), 'x', -22.5, (0, 11.5, 0))
    return {'idle': _grupo(cs, ('cabeca',), 'z', 22.5, (0, 11.5, 0)), 'walk1': anda(1), 'walk2': anda(-1), 'grito': grito()}


def festeiro_olhos(cs):
    return {'idle': [], 'grito': []}


def festeiro_pintor(P):
    x, y, z, s, t = P['x'], P['y'], P['z'], P['s'], P['t']
    pele, face, nome = P['pele'], P['face'], P['nome']
    if pele == 'balao':
        d = elipse(s, t, 0.5, 0.5, 0.52, 0.52)
        c = rampa(1 - np.clip(d, 0, 1), [(0, '#5a0a10'), (0.5, '#b8141c'), (1, '#e03a3a')])
        c = mix(c, C('#ff9a9a'), dentro(elipse(s, t, 0.35, 0.3, 0.12, 0.16), 0.5) * 0.6)
        return c, np.where(d < 1.05, 1.0, 0.0) if face in ('south', 'north', 'east', 'west') else np.ones_like(d)
    if pele == 'fio':
        return C('#dcdcdc') * np.ones(s.shape + (3,)), 1
    if pele == 'pompom':
        return rampa(fbm(x * 6, y * 6, z * 6, 2, 1, 131), [(0, '#9a1020'), (1, '#ff4060')]), 1
    if pele == 'chapeu':
        faixa = (np.floor((y - 16) * 1.2 + s * 2) % 2) == 0
        c = np.where(faixa[..., None], C('#1b3fa8') * np.ones(s.shape + (3,)), C('#f4e04a') * np.ones(s.shape + (3,)))
        c = mix(c, C('#4a0a10'), ss(0.7, 0.95, fbm(x * 3, y * 3, z * 3, 2, 1, 132)) * 0.6)
        return c * luz(P, 0.1), 1
    # amarelo vivo com sombra laranja, sujeira e respingos de sangue
    n = fbm(x * 0.9, y * 0.9, z * 0.9, 4, 0.5, 133)
    c = rampa(n, [(0, '#c99a12'), (0.4, '#f0c11c'), (0.75, '#fbd92a'), (1, '#fff07a')])
    sujo = ss(0.6, 0.88, fbm(x * 0.5, y * 0.4, z * 0.5, 3, 1, 134))
    c = mix(c, C('#6a4a12'), sujo * 0.35)
    c = pele_detalhe(P, c, 135, 0.5)
    pontos = [(-6, 2, 2, 2.2), (3, -8, 2, 1.6), (-2, 6, 2, 1.4), (6, -2, 2, 1.2), (0, 14, 6, 1.8), (-7, -3, -2, 1.3), (4, 3, -2, 1.8), (-4, 18, 6, 1.4)]
    c = mix(c, sangue(P, 11), respingo(P, pontos, 11).clip(0, 1) * 0.92)
    if pele == 'mao':
        return mix(c, C('#4a1010'), ss(0.2, 1.0, t) * 0.8) * luz(P), 1
    if pele in ('rosto', 'rosto_grito') and face == 'south':
        grito = pele == 'rosto_grito'
        c = np.clip(c * 1.05, 0, 1)
        if not grito:
            # olhos "=)" : dois tracos pretos inclinados; sorriso largo com dentes certinhos
            for cx, sg in ((0.3, 1), (0.7, -1)):
                for dy in (-0.04, 0.04):
                    tr = np.abs((t - 0.36 - dy) - sg * (s - cx) * 0.45) < 0.018
                    tr &= np.abs(s - cx) < 0.11
                    c = np.where(tr[..., None], C('#0a0a0c') * np.ones_like(c), c)
            yb = 0.7 + 0.5 * (s - 0.5) ** 2
            lab = (np.abs(t - yb) < 0.045) & (s > 0.12) & (s < 0.88)
            c = np.where(lab[..., None], C('#0a0a0c') * np.ones_like(c), c)
            dent = (t > yb - 0.03) & (t < yb + 0.03) & (s > 0.18) & (s < 0.82)
            col = np.floor(s * 12)
            sep = np.abs(s * 12 - col - 0.5) > 0.4
            c = np.where((dent & ~sep)[..., None], C('#f2ecd8') * np.ones_like(c), c)
        else:
            # os olhos viram buracos pretos escorrendo; o sorriso rasga ate as orelhas, cheio de dentes
            for cx in (0.3, 0.7):
                d = elipse(s, t, cx, 0.36, 0.13, 0.15)
                c = mix(c, C('#050406'), dentro(d, 0.06))
                c = mix(c, C('#0a0a0c'), 0.9 * escorrido(s, t, [(cx - 0.03, 0.45, 0.25), (cx + 0.04, 0.45, 0.18)], 12).clip(0, 1))
            d = elipse(s, t, 0.5, 0.74, 0.44, 0.19)
            c = c * (1 - 0.3 * dentro(d / 1.15, 0.3))[..., None]
            c = mix(c, boca_fundo(1 - d), dentro(d, 0.04))
            ang = np.arctan2((t - 0.74) / 0.19, (s - 0.5) / 0.44)
            m, p, _ = dentes(ang, 1 - d, 0.3, 0.22, afiado=1.5)
            m &= d < 1
            c = np.where(m[..., None], dente_cor(p, '#f2ecd8'), c)
        return c * luz(P, 0.1) * borda(P, 0.1), 1
    return c * luz(P) * borda(P, 0.25), 1


def festeiro_rosto(P):
    return festeiro_pintor(dict(P, pele='rosto_grito', face='south', nome='cabeca'))


MONSTROS['festeiro'] = dict(cubos=festeiro_cubos, quadros=festeiro_quadros, olhos=festeiro_olhos, pintor=festeiro_pintor, rosto=festeiro_rosto,
                            escala=1.1, altura_pe=16, rosto_cubo=('cabeca', (-6, 11.5, -6), (6, 23.5, 6)))


# ====================================================================== SOMBRA (Nivel 6)
# Toda preta, alta, sem cara; so os olhos vermelhos. Bordas esfumacadas (alpha). So anda quando ninguem olha.

def sombra_cubos():
    """Coluna preta que afina para baixo (sem pernas), ombros largos, bracos compridos com garras, cabeca oval sem
    cara e tentaculos de fumaca subindo das costas. So os olhos vermelhos."""
    cs = []
    cs.append(Cubo('base', (-3, -16, -2), (3, -6, 2), 'fumaca', grupo='tronco'))
    cs.append(Cubo('meio', (-4.5, -6, -2.5), (4.5, 6, 2.5), 'fumaca', grupo='tronco'))
    cs.append(Cubo('peito', (-5.5, 6, -3), (5.5, 16, 3), 'fumaca', grupo='tronco'))
    cs.append(Cubo('ombros', (-8, 13, -2.5), (8, 16.5, 2.5), 'fumaca', grupo='tronco'))
    for lado, sx in (('d', -1), ('e', 1)):
        g = f'braco_{lado}'
        cs.append(Cubo(f'braco_{lado}', (sx * 7 - 1.2, -10, -1.2), (sx * 7 + 1.2, 14, 1.2), 'fumaca', grupo=g, rot=('z', -sx * 0.0, (0, 14, 0))))
        for k, dx in enumerate((-1.2, -0.2, 0.8)):
            cs.append(Cubo(f'garra_{lado}{k}', (sx * 7 + dx, -15 + (k % 2), -0.3), (sx * 7 + dx + 0.5, -10, 0.3), 'garra', grupo=g))
    cs.append(Cubo('cabeca', (-3.5, 16.5, -3.5), (3.5, 26, 3.5), 'rosto', grupo='cabeca'))
    cs.append(Cubo('aura', (-5, 15, -5), (5, 27.5, 5), 'aura', grupo='cabeca'))
    for k, (dx, ang) in enumerate(((-5, 22.5), (0, 0), (5, -22.5))):
        cs.append(Cubo(f'tentaculo{k}', (dx - 0.8, 14, -4), (dx + 0.8, 31, -2.4), 'tentaculo', grupo='tronco', rot=('z', ang, (dx, 14, -3.2))))
    return cs


def sombra_quadros(cs):
    def anda(sinal):
        out = _grupo(cs, ('braco_d',), 'x', -22.5 * sinal, (0, 14, 0))
        return _grupo(out, ('braco_e',), 'x', 22.5 * sinal, (0, 14, 0))

    def grito():
        out = [c.com(pele='rosto_grito') if c.nome == 'cabeca' else c for c in cs]
        out = _grupo(out, ('braco_d', 'braco_e'), 'x', -45, (0, 14, 0))
        return _grupo(out, ('cabeca',), 'z', 22.5, (0, 16.5, 0))
    return {'idle': cs, 'walk1': anda(1), 'walk2': anda(-1), 'grito': grito()}


def sombra_olhos(cs):
    o = [Cubo('olho_d', (-2.6, 21.5, 3.5), (-0.9, 22.7, 4.0), 'olho', so=('south', 'up', 'down', 'east', 'west')),
         Cubo('olho_e', (0.9, 21.5, 3.5), (2.6, 22.7, 4.0), 'olho', so=('south', 'up', 'down', 'east', 'west'))]
    return {'idle': o, 'grito': [girado(c.com(pele='olho_grito'), 'z', 22.5, (0, 16.5, 0)) for c in o]}


def sombra_pintor(P):
    x, y, z, s, t = P['x'], P['y'], P['z'], P['s'], P['t']
    pele, face, nome = P['pele'], P['face'], P['nome']
    if pele == 'olho':
        return olhos_brilho(P, '#ff1414', '#ffb0a0')
    if pele == 'olho_grito':
        return olhos_brilho(P, '#ff3020', '#ffffff')
    n = fbm(x * 1.2, y * 1.2, z * 1.2, 4, 0.5, 141)
    c = rampa(n, [(0, '#000000'), (0.6, '#060608'), (1, '#121016')])
    if pele == 'aura':
        # camada em volta da cabeca: fumaca preta, so alguns farrapos (alpha)
        a = ss(0.55, 0.8, fbm(x * 1.6, y * 1.6, z * 1.6, 3, 1, 142)) * np.where(face == 'down', 0, 1)
        return c, a
    if pele in ('rosto', 'rosto_grito') and face == 'south':
        if pele == 'rosto_grito':
            d = elipse(s, t, 0.5, 0.78, 0.26, 0.2)
            c = mix(c, rampa(1 - np.clip(d, 0, 1), [(0, '#3a0000'), (0.5, '#8a0000'), (1, '#ff2020')]), dentro(d, 0.1))
        return c, 1
    if pele == 'garra':
        return rampa(fbm(x * 2, y * 2, z * 2, 2, 1, 145), [(0, '#000000'), (1, '#2a1a1a')]), 1
    if pele == 'tentaculo':
        a = ss(0.35, 0.75, fbm(x * 1.8, y * 0.7, z * 1.8, 3, 1, 146)) * (1 - ss(22, 31, y) * 0.8)
        return c, np.where(a > 0.3, 1.0, 0.0)
    if pele == 'fumaca':
        # bordas dos membros se desfazem em fumaca
        a = np.ones(x.shape)
        borda_m = np.minimum(np.minimum(s, 1 - s), np.minimum(t, 1 - t))
        a = np.where(borda_m < 0.12, ss(0.3, 0.7, fbm(x * 2.5, y * 2.5, z * 2.5, 2, 1, 143)), 1.0)
        if nome == 'base':
            a = np.where(y < -9, ss(0.45, 0.8, fbm(x * 2, y * 1.2, z * 2, 2, 1, 144)), a)   # a base se desfaz em fumaca
        return c, a
    return c, 1


def sombra_rosto(P):
    return sombra_pintor(dict(P, pele='rosto_grito', face='south', nome='cabeca'))


MONSTROS['sombra'] = dict(cubos=sombra_cubos, quadros=sombra_quadros, olhos=sombra_olhos, pintor=sombra_pintor, rosto=sombra_rosto,
                          escala=1.3, altura_pe=16, rosto_cubo=('cabeca', (-3.5, 16.5, -3.5), (3.5, 26, 3.5)))


# ====================================================================== CORREDOR (Nivel !: a coisa que corre atras)
# Esqueletico e palido, lancado para a frente (tronco a 45), bracos esticados a frente com maos abertas, pernas
# compridas em passada larga, caveira de boca escancarada. Rosto/queixo = caveira do Cao; corpo = pele do Uivador.

def corredor_cubos():
    cs = []
    cs.append(Cubo('perna_d', (-3.6, -16, -1.5), (-0.8, 0, 1.5), 'pele', grupo='perna_d', rot=('x', 22.5, (0, 0, 0))))
    cs.append(Cubo('perna_e', (0.8, -16, -1.5), (3.6, 0, 1.5), 'pele', grupo='perna_e', rot=('x', -22.5, (0, 0, 0))))
    cs.append(Cubo('quadril', (-4, -1, -2), (4, 2, 2), 'pele', grupo='tronco'))
    cs.append(Cubo('tronco', (-3.8, 2, -2.2), (3.8, 14, 2.2), 'costela', grupo='tronco', rot=('x', 45.0, (0, 2, 0))))
    cs.append(Cubo('ombros', (-7, 12.5, -2.5), (7, 15, 2.5), 'pele', grupo='tronco', rot=('x', 45.0, (0, 2, 0))))
    # ombros a 45 ficam em ~ (y 10.5, z 8.5): bracos esticados para a frente (+z), 20 de comprimento
    for lado, sx in (('d', -1), ('e', 1)):
        g = f'braco_{lado}'
        cs.append(Cubo(f'braco_{lado}', (sx * 6 - 1.2, 9.5, 8), (sx * 6 + 1.2, 11.9, 28), 'braco', grupo=g))
        cs.append(Cubo(f'mao_{lado}', (sx * 6 - 2.2, 9, 28), (sx * 6 + 2.2, 12.4, 31.5), 'mao', grupo=g))
    cs.append(Cubo('pescoco', (-1.5, 10, 8), (1.5, 13, 11), 'pele', grupo='cabeca'))
    cs.append(Cubo('cabeca', (-4, 11, 6.5), (4, 19, 14.5), 'rosto', grupo='cabeca'))
    cs.append(Cubo('queixo', (-3.4, 7.5, 8), (3.4, 11.1, 14.8), 'queixo', grupo='queixo', rot=('x', 22.5, (0, 11.1, 8))))
    return cs


def corredor_quadros(cs):
    def passo(sinal):
        out = [girado(c, 'x', 45.0 * sinal, (0, 0, 0)) if c.grupo == 'perna_d' else c for c in cs]
        out = [girado(c, 'x', -45.0 * sinal, (0, 0, 0)) if c.grupo == 'perna_e' else c for c in out]
        return out

    def grito():
        out = [c.com(pele='rosto_grito') if c.nome == 'cabeca' else c for c in cs]
        return [girado(c, 'x', 45.0, (0, 11.1, 8)) if c.grupo == 'queixo' else c for c in out]
    return {'idle': cs, 'walk1': passo(1), 'walk2': passo(-1), 'grito': grito()}


def corredor_olhos(cs):
    o = [Cubo('olho_d', (-2.6, 15.3, 14.5), (-1.1, 16.6, 15.0), 'olho', so=('south', 'up', 'down', 'east', 'west')),
         Cubo('olho_e', (1.1, 15.3, 14.5), (2.6, 16.6, 15.0), 'olho', so=('south', 'up', 'down', 'east', 'west'))]
    return {'idle': o, 'grito': o}


def corredor_pintor(P):
    pele = P['pele']
    if pele == 'olho':
        return olhos_brilho(P, '#ff2020', '#ffd0c0')
    if pele in ('rosto', 'rosto_grito', 'queixo'):
        return cao_pintor(P)
    return uivador_pintor(P)


def corredor_rosto(P):
    return cao_pintor(dict(P, pele='rosto_grito', face='south', nome='cabeca'))


MONSTROS['corredor'] = dict(cubos=corredor_cubos, quadros=corredor_quadros, olhos=corredor_olhos, pintor=corredor_pintor, rosto=corredor_rosto,
                            escala=1.15, altura_pe=16, rosto_cubo=('cabeca', (-4, 11, 6.5), (4, 19, 14.5)))


# ====================================================================== AFOGADO (Nivel 37: piscinas)
# Inchado, azulado, olhos selados (cegos), bracos grossos tateando para a frente, algas penduradas, pingando.

def afogado_cubos():
    cs = []
    cs.append(Cubo('perna_d', (-4.5, -16, -2.2), (-0.5, -4, 2.2), 'pele', grupo='perna_d'))
    cs.append(Cubo('perna_e', (0.5, -16, -2.2), (4.5, -4, 2.2), 'pele', grupo='perna_e'))
    cs.append(Cubo('barriga', (-6, -5, -3.5), (6, 5, 4), 'pele', grupo='tronco'))
    cs.append(Cubo('tronco', (-5.5, 4, -3), (5.5, 11, 3), 'pele', grupo='tronco'))
    cs.append(Cubo('ombros', (-8, 9, -2.8), (8, 12, 2.8), 'pele', grupo='tronco'))
    for lado, sx in (('d', -1), ('e', 1)):
        g = f'braco_{lado}'
        cs.append(Cubo(f'braco_{lado}', (sx * 7 - 1.8, 8, -1.8), (sx * 7 + 1.8, 11.5, 14), 'pele', grupo=g))     # esticado para a frente
        cs.append(Cubo(f'mao_{lado}', (sx * 7 - 2.2, 7.6, 14), (sx * 7 + 2.2, 11.9, 18), 'mao', grupo=g))
    cs.append(Cubo('pescoco', (-2, 11, -2), (2, 12.5, 2), 'pele', grupo='cabeca'))
    cs.append(Cubo('cabeca', (-4.5, 12.5, -4.5), (4.5, 21.5, 4.5), 'rosto', grupo='cabeca', rot=('z', 22.5, (0, 12.5, 0))))
    cs.append(Cubo('algas', (-5.2, 4, -5.2), (5.2, 22, 5.2), 'algas', grupo='cabeca', rot=('z', 22.5, (0, 12.5, 0))))
    return cs


def afogado_quadros(cs):
    def anda(sinal):
        out = _grupo(cs, ('perna_d',), 'x', 22.5 * sinal, (0, -4, 0))
        return _grupo(out, ('perna_e',), 'x', -22.5 * sinal, (0, -4, 0))

    def grito():
        out = [c.com(pele='rosto_grito') if c.nome == 'cabeca' else c for c in cs]
        return _grupo(out, ('cabeca',), 'z', -22.5, (0, 12.5, 0))
    return {'idle': cs, 'walk1': anda(1), 'walk2': anda(-1), 'grito': grito()}


def afogado_olhos(cs):
    return {'idle': [], 'grito': []}


def afogado_pintor(P):
    x, y, z, s, t = P['x'], P['y'], P['z'], P['s'], P['t']
    pele, face, nome = P['pele'], P['face'], P['nome']
    if pele == 'algas':
        c, a = cabelo_longo(P, 151, '#0d2a16', y_fim=4, dens=0.7, frente=(0.15, 0.3), topo_y=20.5, cheio_y=16)
        c = mix(c, C('#2d6b2a'), ss(0.5, 0.9, fbm(x * 2, y * 0.5, z * 2, 2, 1, 152)) * 0.5)
        return c, a
    # pele inchada de afogado: azul-cinza, manchas roxas, brilho molhado, pingos
    n = fbm(x * 0.8, y * 0.8, z * 0.8, 4, 0.5, 153)
    c = rampa(n, [(0, '#3f5566'), (0.4, '#6f8a9a'), (0.75, '#9fb4bf'), (1, '#c7d6dc')])
    veia = ss(0.66, 0.9, ridged(x * 0.9, y * 0.5, z * 0.9, 3, 0.8, 154))
    c = mix(c, C('#2a2448'), veia * 0.5)
    mancha = ss(0.6, 0.8, fbm(x * 0.5, y * 0.5, z * 0.5, 3, 1, 155))
    c = mix(c, C('#5a3a66'), mancha * 0.5)
    molhado = ss(0.8, 0.95, fbm(x * 2.5, y * 2.5, z * 2.5, 2, 1, 156))
    c = mix(c, C('#e8f4ff'), molhado * 0.4)
    c = pele_detalhe(P, c, 157, 0.6)
    if pele == 'mao':
        return mix(c, C('#1c2230'), ss(0.3, 1.0, t) * 0.6) * luz(P), 1
    if pele in ('rosto', 'rosto_grito') and face == 'south':
        grito = pele == 'rosto_grito'
        c = np.clip(c * 1.05, 0, 1)
        # olhos selados: fendas inchadas; no grito abrem brancos e cegos
        for cx in (0.3, 0.7):
            d = elipse(s, t, cx, 0.4, 0.15, 0.1)
            c = c * (1 - 0.35 * dentro(d / 1.5, 0.5))[..., None]
            if not grito:
                fenda = np.abs(t - 0.41 - 0.03 * np.sin((s - cx) * 20)) < 0.012
                fenda &= np.abs(s - cx) < 0.12
                c = np.where(fenda[..., None], C('#1a1f2e') * np.ones_like(c), c)
            else:
                c = mix(c, C('#eef4f8'), dentro(d, 0.08))
                c = mix(c, C('#8ea3b4'), (dentro(d / 0.5, 0.3)) * 0.6)
        nd = elipse(s, t, 0.5, 0.6, 0.05, 0.05)
        c = c * (1 - 0.4 * dentro(nd, 0.3))[..., None]
        d = elipse(s, t, 0.5, 0.82, 0.2 if grito else 0.13, 0.16 if grito else 0.05)
        c = c * (1 - 0.4 * dentro(d / 1.2, 0.3))[..., None]
        c = mix(c, boca_fundo(1 - d), dentro(d, 0.05))
        if grito:
            ang = np.arctan2((t - 0.82) / 0.16, (s - 0.5) / 0.2)
            m, p, _ = dentes(ang, 1 - d, 0.25, 0.4, afiado=1.2)
            m &= d < 1
            c = np.where(m[..., None], dente_cor(p, '#c9c2b0'), c)
        agua = escorrido(s, t, [(0.22, 0.3, 0.6), (0.5, 0.86, 0.14), (0.78, 0.35, 0.5)], 158, 0.01)
        c = mix(c, C('#dff2ff'), 0.7 * agua.clip(0, 1))
        return c * luz(P, 0.15) * borda(P, 0.12), 1
    return c * luz(P) * borda(P, 0.25), 1


def afogado_rosto(P):
    return afogado_pintor(dict(P, pele='rosto_grito', face='south', nome='cabeca'))


MONSTROS['afogado'] = dict(cubos=afogado_cubos, quadros=afogado_quadros, olhos=afogado_olhos, pintor=afogado_pintor, rosto=afogado_rosto,
                           escala=1.15, altura_pe=16, rosto_cubo=('cabeca', (-4.5, 12.5, -4.5), (4.5, 21.5, 4.5)))
