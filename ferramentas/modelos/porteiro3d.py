"""O Porteiro e a Sombra de Cartola com ANDAR (15/11, Paulo: "o monstro esta duro"). Os mesmos corpos do hotel3d.py
(itens novos: corona:props/anim_porteiro_fN e anim_sombra_fN, para nao mexer no pacote do Hotel), agora com 6 quadros:
  f0 parado | f1 susto (bracos para a frente, boca aberta) | f2 e f3 passos da ronda (pernas e bracos alternados)
  f4 e f5 passos da cacada (bracos para a frente, perna larga).
Rodar: python3 porteiro3d.py <saida>
"""
import inspect, os, sys
sys.argv = [sys.argv[0]] + sys.argv[1:2]
import hotel3d as H
A, C = H.A, H.C
POSE = {'perna': 0, 'braco': 0}


def pernas(cs):
    for i, (xa, xb) in enumerate(((5.3, 7.5), (8.5, 10.7))):
        s = POSE['perna'] * (1 if i == 0 else -1)
        rot = ('x', 22.5 * s, (8.0, -2.0, 8.0)) if s else None
        cs.append(C('perna', xa, xb, -14.4, -2.0, 6.9, 9.1, 'tecido2', rot))
        cs.append(C('sapato', xa - 0.2, xb + 0.2, -16.0, -14.3, 5.2, 9.5, 'sapato', rot))


def bracos(cs, susto, comp, mat_mao):
    for lado in (-1, 1):
        cx = 8 + lado * 4.6
        s = POSE['braco'] * lado
        rot = ('x', 45.0, (cx, 8.6, 8.0)) if susto else (('x', 22.5 * s, (cx, 8.6, 8.0)) if s else None)
        cs.append(C('ombro', cx - 1.1, cx + 1.1, 7.6, 9.8, 6.8, 9.2, 'tecido', rot))
        cs.append(C('braco', cx - 0.85, cx + 0.85, 9.0 - comp, 7.8, 7.1, 8.9, 'tecido', rot))
        cs.append(C('punho', cx - 1.0, cx + 1.0, 8.4 - comp, 9.4 - comp, 7.0, 9.0, 'ouro', rot))
        cs.append(C('mao', cx - 0.8, cx + 0.8, 6.6 - comp, 8.5 - comp, 7.3, 8.7, mat_mao, rot))
        for dx in (-0.6, 0.0, 0.6):
            cs.append(C('dedo', cx + dx - 0.17, cx + dx + 0.17, 3.2 - comp + abs(dx), 6.7 - comp, 7.7, 8.2, mat_mao, rot))


H.pernas, H.bracos = pernas, bracos
BASE = {'porteiro': 'figura', 'sombra': 'perseguidor'}
for novo, velho in BASE.items():
    A.PALETAS[novo] = dict(A.PALETAS[velho])
    H.MATS[novo] = H.MATS[velho]
QUADRO = {0: (0, 0, False), 1: (0, 0, True), 2: (1, 1, False), 3: (-1, -1, False), 4: (1, 0, True), 5: (-1, 0, True)}


def cubos(ident, quadro=0):
    perna, braco, susto = QUADRO[quadro]
    POSE['perna'], POSE['braco'] = perna, braco
    return H.CORPOS[BASE[ident]](susto)


A.cubos = cubos
_pf = A.pintar_face
A.pintar_face = lambda c, f, wpx, hpx, sem, ident, esc: _pf(c, f, wpx, hpx, sem, BASE.get(ident, ident), esc)
# o gerar do animatronicos3d faz so 2 quadros: aqui a mesma funcao, com 6
fonte = inspect.getsource(A.gerar).replace('for q in (0, 1):', 'for q in (0, 1, 2, 3, 4, 5):')
exec(fonte, A.__dict__)

if __name__ == '__main__':
    A.gerar(A.SAIDA, ['porteiro', 'sombra'])
    os.replace(os.path.join(A.SAIDA, 'corona-animatronicos.json'), os.path.join(A.SAIDA, 'corona-porteiro.json'))
