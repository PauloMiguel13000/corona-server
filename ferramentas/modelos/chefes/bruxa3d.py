import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import chefes3d as K
H, A, C, LUZ = K.H, K.A, K.C, K.LUZ


def bruxa_a(susto):
    """A) Bruxa do Pantano: bem curvada, dedos compridos, corvo no ombro, pocoes girando, tranças e capa de musgo."""
    cs = []
    # vestido rasgado em tiras ate o chao
    cs.append(C('saia', 3.6, 12.4, -12.0, -3.0, 5.4, 11.4, 'tecido'))
    for i, x in enumerate((3.6, 5.4, 7.2, 9.0, 10.8)):
        fim = (-16.0, -14.6, -16.0, -15.0, -16.0)[i]
        cs.append(C('tira', x, x + 1.6, fim, -11.8, 5.2 + (i % 2) * 0.3, 11.6, 'tecido', so=('north', 'south', 'east', 'west', 'down')))
    cs.append(C('musgo', 3.4, 12.6, -5.0, -3.0, 5.2, 11.6, 'corda'))
    cs.append(C('cinto', 3.4, 12.6, -3.4, -2.2, 5.1, 11.7, 'tecido2'))
    for x, h in ((4.2, 2.0), (6.0, 1.6), (10.6, 2.2)):
        cs.append(C('frasco', x - 0.6, x + 0.6, -3.4 - h, -3.2, 4.6, 5.4, 'pupila', **LUZ))
    cs.append(C('caveirinha', 8.2, 9.8, -5.8, -3.6, 4.5, 5.4, 'pano'))
    # tronco caido para a frente (corcunda alta)
    cs.append(C('tronco', 4.2, 11.8, -3.0, 3.6, 5.6, 10.8, 'tecido'))
    cs.append(C('corcunda', 4.0, 12.0, 1.2, 7.4, 7.4, 12.4, 'tecido'))
    cs.append(C('capa', 3.0, 13.0, -13.0, 7.0, 11.8, 12.8, 'corda', ('x', -22.5, (8.0, 7.0, 12.3))))
    cs.append(C('xale', 2.8, 13.2, 4.0, 7.0, 4.0, 11.0, 'tecido2'))
    cs.append(C('colar', 5.0, 11.0, 3.4, 4.2, 3.7, 4.1, 'pano', so=('north', 'up', 'down')))
    for x in (6.0, 8.0, 10.0):
        cs.append(C('dente_colar', x - 0.3, x + 0.3, 2.4, 3.5, 3.75, 4.05, 'pano'))
    # bracos compridos com dedos de garra; mao direita com chama verde, esquerda com pocao
    for lado in (-1, 1):
        cx = 8 + lado * 5.6
        rot = ('x', 45.0, (cx, 5.8, 7.0)) if susto else None
        cs.append(C('manga', cx - 1.5, cx + 1.5, -1.2, 6.2, 5.4, 8.6, 'tecido2', rot))
        cs.append(C('manga_ponta', cx - 1.8, cx + 1.8, -2.8, -1.0, 5.0, 9.0, 'tecido2', rot))
        cs.append(C('mao', cx - 0.9, cx + 0.9, -4.4, -2.6, 6.1, 7.9, 'pele', rot))
        for d in (-0.6, 0.0, 0.6):
            cs.append(C('dedo', cx + d - 0.2, cx + d + 0.2, -7.0, -4.2, 6.2, 6.7, 'pele', rot))
            cs.append(C('unha', cx + d - 0.2, cx + d + 0.2, -7.8, -6.9, 6.0, 6.5, 'sapato', rot))
        if lado == 1:
            cs.append(C('chama', cx - 0.9, cx + 0.9, -4.0, -1.8, 4.2, 6.0, 'pupila', rot, **LUZ))
            cs.append(C('chama2', cx - 0.5, cx + 0.5, -1.8, 0.4, 4.6, 5.6, 'pupila', rot, **LUZ))
    # cabeca baixa e projetada para a frente
    cs.append(C('cabeca', 5.0, 11.0, 4.6, 10.6, 2.6, 8.6, 'pele'))
    for x in (6.5, 9.5):
        cs.append(C('olho_fundo', x - 0.9, x + 0.9, 7.6, 9.0, 2.45, 2.62, 'boca', so=('north',)))
        cs.append(C('olho', x - 0.5, x + 0.5, 7.9, 8.7, 2.35, 2.5, 'pupila', so=('north',), **LUZ))
    cs.append(C('sobrancelha', 5.0, 11.0, 9.0, 9.8, 2.3, 2.7, 'tecido2'))
    cs.append(C('nariz', 7.3, 8.7, 6.0, 8.6, 0.2, 2.7, 'pele'))
    cs.append(C('nariz_ponta', 7.4, 8.6, 5.0, 6.4, -0.6, 0.8, 'pele'))
    cs.append(C('verruga', 8.6, 9.3, 6.6, 7.3, 0.4, 1.1, 'corda'))
    mand = ('x', -22.5, (8.0, 5.4, 3.4)) if susto else None
    cs.append(C('boca', 6.0, 10.0, 5.2, 6.0, 2.45, 2.65, 'boca', so=('north',)))
    A.dentes(cs, 6.2, 9.8, 5.4, 6.0, 2.3, 2.5, passo=1.1, larg=0.5)
    cs.append(C('queixo', 5.8, 10.2, 3.6, 5.0, 2.4, 6.0, 'pele', mand))
    # cabelo branco desgrenhado com trancas
    cs.append(C('cabelo', 4.4, 11.6, 4.0, 10.8, 7.6, 10.2, 'pano'))
    for x in (4.2, 11.0):
        cs.append(C('tranca', x - 0.6, x + 0.6, -3.0, 6.0, 5.6, 7.0, 'pano'))
        cs.append(C('fita', x - 0.7, x + 0.7, -2.0, -1.0, 5.5, 7.1, 'corda'))
    # chapeu grande, torto, com remendo, fivela e ponta caida
    cs.append(C('aba', 1.0, 15.0, 10.4, 11.2, 0.6, 12.6, 'tecido2'))
    cs.append(C('chapeu1', 4.0, 12.0, 11.0, 14.6, 2.8, 10.4, 'tecido2'))
    cs.append(C('fita_chapeu', 3.9, 12.1, 11.2, 12.4, 2.7, 10.5, 'corda'))
    cs.append(C('fivela', 7.0, 9.0, 11.0, 12.6, 2.5, 2.8, 'ouro'))
    cs.append(C('remendo', 9.4, 11.2, 12.6, 14.2, 2.65, 2.85, 'tecido', so=('north',)))
    cs.append(C('chapeu2', 5.0, 11.0, 14.4, 18.4, 3.8, 9.4, 'tecido2', ('z', -22.5, (8.0, 14.4, 6.6))))
    cs.append(C('chapeu3', 6.4, 9.6, 17.6, 21.2, 4.6, 8.4, 'tecido2', ('z', -45.0, (8.0, 17.6, 6.5))))
    cs.append(C('chapeu4', 7.2, 8.8, 20.4, 23.0, 5.4, 7.4, 'tecido2', ('z', -45.0, (8.0, 17.6, 6.5))))
    # corvo no ombro esquerdo
    cx = 8 - 5.8
    cs.append(C('corvo', cx - 1.0, cx + 1.0, 7.0, 9.4, 7.0, 10.4, 'sapato'))
    cs.append(C('corvo_cab', cx - 0.8, cx + 0.8, 9.0, 10.6, 6.0, 7.8, 'sapato'))
    cs.append(C('bico', cx - 0.3, cx + 0.3, 9.4, 9.9, 5.0, 6.1, 'ouro'))
    cs.append(C('corvo_olho', cx - 0.85, cx + 0.85, 9.9, 10.3, 6.4, 6.8, 'pupila', **LUZ))
    cs.append(C('cauda', cx - 0.6, cx + 0.6, 6.4, 7.4, 10.2, 12.6, 'sapato'))
    # pocoes flutuando em volta
    for x, y, z in ((-3.0, 2.0, 6.0), (18.0, 6.0, 8.0), (-1.0, 12.0, 12.0), (17.0, -6.0, 4.0)):
        cs.append(C('pocao_voa', x, x + 1.6, y, y + 2.2, z, z + 1.6, 'pupila', **LUZ))
        cs.append(C('pocao_rolha', x + 0.4, x + 1.2, y + 2.2, y + 2.8, z + 0.4, z + 1.2, 'corda'))
    return cs


def bruxa_b(susto):
    """B) Bruxa Ancestral: alta e magra, vestido longo negro, rosto de mascara de osso, cajado com caldeirao-lanterna."""
    cs = []
    cs.append(C('vestido', 4.0, 12.0, -16.0, -2.0, 5.2, 10.8, 'tecido'))
    cs.append(C('vestido_barra', 3.4, 12.6, -16.0, -14.4, 4.6, 11.4, 'tecido2'))
    for y in (-12.0, -8.0, -4.0):
        cs.append(C('runa', 6.6, 9.4, y, y + 0.6, 5.05, 5.2, 'pupila', so=('north',), **LUZ))
    cs.append(C('tronco', 4.4, 11.6, -2.2, 6.6, 5.8, 10.4, 'tecido'))
    cs.append(C('capa', 2.6, 13.4, -16.0, 8.0, 10.6, 11.6, 'tecido2', ('x', -22.5, (8.0, 8.0, 11.1))))
    for lado in (-1, 1):
        cx = 8 + lado * 5.0
        cs.append(C('ombro_pena', cx - 2.2, cx + 2.2, 5.0, 8.4, 5.0, 11.0, 'sapato'))
        for dz in (5.6, 8.0, 10.4):
            cs.append(C('pena', cx - 0.4 + lado * 1.6, cx + 0.4 + lado * 1.6, 8.2, 10.8, dz - 0.4, dz + 0.4, 'sapato'))
        rot = ('x', 45.0, (cx, 6.0, 8.0)) if susto else None
        cs.append(C('braco', cx - 1.0, cx + 1.0, -3.0, 6.0, 7.0, 9.0, 'tecido2', rot))
        cs.append(C('mao', cx - 0.8, cx + 0.8, -5.0, -2.8, 7.2, 8.8, 'pele', rot))
        for d in (-0.5, 0.0, 0.5):
            cs.append(C('dedo', cx + d - 0.18, cx + d + 0.18, -7.6, -4.8, 7.4, 7.8, 'sapato', rot))
    cx = 8 + 5.0
    rot = ('x', 45.0, (cx, 6.0, 8.0)) if susto else None
    cs.append(C('cajado', cx - 0.45, cx + 0.45, -16.0, 14.0, 5.8, 6.7, 'corda', rot))
    cs.append(C('gancho', cx - 0.45, cx + 0.45, 13.4, 14.4, 3.0, 6.7, 'corda', rot))
    cs.append(C('caldeirao', cx - 1.4, cx + 1.4, 10.0, 12.8, 2.0, 4.8, 'ouro', rot))
    cs.append(C('caldeirao_luz', cx - 1.0, cx + 1.0, 12.6, 13.0, 2.4, 4.4, 'pupila', rot, **LUZ))
    cs.append(C('fumaca', cx - 0.6, cx + 0.6, 13.0, 15.0, 2.8, 4.0, 'pupila', rot, **LUZ))
    cs.append(C('pescoco', 7.0, 9.0, 6.4, 8.0, 7.0, 9.0, 'pele'))
    cs.append(C('cabeca', 5.2, 10.8, 7.6, 13.6, 5.0, 10.6, 'pele'))
    cs.append(C('mascara', 5.0, 11.0, 7.4, 13.0, 4.5, 5.05, 'pano', so=('north', 'east', 'west', 'up', 'down')))
    for x in (6.5, 9.5):
        cs.append(C('olho_fundo', x - 0.9, x + 0.9, 10.4, 11.8, 4.3, 4.52, 'boca', so=('north',)))
        cs.append(C('olho', x - 0.45, x + 0.45, 10.7, 11.5, 4.2, 4.33, 'pupila', so=('north',), **LUZ))
    cs.append(C('chifre_masc', 4.0, 5.0, 11.0, 14.0, 4.8, 5.8, 'pano', ('z', 22.5, (4.5, 11.0, 5.3))))
    cs.append(C('chifre_masc', 11.0, 12.0, 11.0, 14.0, 4.8, 5.8, 'pano', ('z', -22.5, (11.5, 11.0, 5.3))))
    cs.append(C('boca', 6.4, 9.6, 8.2, 9.0, 4.3, 4.52, 'boca', so=('north',)))
    A.dentes(cs, 6.6, 9.4, 8.4, 9.0, 4.2, 4.4, passo=0.7, larg=0.4)
    cs.append(C('cabelo', 4.6, 11.4, -2.0, 13.0, 9.6, 11.0, 'tecido2'))
    cs.append(C('aba', 1.6, 14.4, 13.2, 14.0, 1.6, 13.6, 'tecido2'))
    cs.append(C('chapeu1', 4.4, 11.6, 13.8, 17.6, 4.4, 11.0, 'tecido2'))
    cs.append(C('fita', 4.3, 11.7, 14.0, 15.0, 4.3, 11.1, 'pupila', **LUZ))
    cs.append(C('chapeu2', 5.6, 10.4, 17.4, 21.4, 5.4, 10.0, 'tecido2', ('x', 22.5, (8.0, 17.4, 7.7))))
    cs.append(C('chapeu3', 6.8, 9.2, 20.8, 24.0, 6.2, 9.0, 'tecido2', ('x', 45.0, (8.0, 20.8, 7.6))))
    return cs


A.PALETAS['bruxa_a'] = dict(pupila=(0.45, 1.0, 0.25))
A.PALETAS['bruxa_b'] = dict(pupila=(0.70, 0.30, 1.0))
H.MATS['bruxa_a'] = K.mats(tecido=K.mat((0.20, 0.14, 0.28)), tecido2=K.mat((0.07, 0.06, 0.09)), pele=K.mat((0.46, 0.58, 0.36)),
                           pano=K.mat((0.86, 0.84, 0.78)), corda=K.mat((0.22, 0.34, 0.14)), ouro=K.mat((0.80, 0.64, 0.24), 0.8, 50, 0.7),
                           sapato=K.mat((0.04, 0.04, 0.06), 0.5, 40, 0.3))
H.MATS['bruxa_b'] = K.mats(tecido=K.mat((0.06, 0.05, 0.08)), tecido2=K.mat((0.13, 0.07, 0.17)), pele=K.mat((0.40, 0.42, 0.44)),
                           pano=K.mat((0.88, 0.85, 0.76)), corda=K.mat((0.24, 0.16, 0.10)), ouro=K.mat((0.20, 0.20, 0.22), 0.8, 60, 0.6),
                           sapato=K.mat((0.03, 0.03, 0.04), 0.5, 40, 0.3))
H.CORPOS['bruxa_a'] = bruxa_a
H.CORPOS['bruxa_b'] = bruxa_b
if __name__ == '__main__':
    A.gerar(A.SAIDA, ['bruxa_a', 'bruxa_b'])
