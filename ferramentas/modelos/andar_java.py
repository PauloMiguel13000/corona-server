"""Ciclo de andar suave dos monstros 3D no Java (so modelos novos; nada do Bedrock muda).

Gera <nome>_andar_00..15 (corpo) e <nome>_olhos_andar_00..15 (olhos) para cada monstro das Backrooms:
pernas e bracos balancando em seno, corpo subindo e descendo a cada passo e a cabeca acompanhando.
Desde a 1.21.6 o Java aceita qualquer angulo entre -45 e 45 nos elementos do modelo.

Uso: python3 -I andar_java.py   (le os mesmos monstros do gerar.py e usa o MESMO atlas/UV)
"""
import math, os, sys
import numpy as np
from PIL import Image
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import kit  # noqa: E402
import gerar  # noqa: E402
from backrooms import MONSTROS as BR  # noqa: E402

N = 16
LIM = 45.0
class _Todos:
    def __contains__(self, a):
        return -45.0 <= a <= 45.0


kit.ANGULOS = _Todos()  # o modelo_java passa a aceitar qualquer angulo entre -45 e 45

# grupo(s), eixo, amplitude (graus), pivo, sinal, frequencia (1 = um balanco por ciclo, 2 = um por passo)
SPEC = {
    'bacteria': dict(bob=0.55, partes=[
        (('perna_d',), 'x', 22.5, (0, -1, 0), +1, 1), (('perna_e',), 'x', 22.5, (0, -1, 0), -1, 1),
        (('braco_d',), 'x', 26.0, (0, 13, 4.5), -1, 1), (('braco_e',), 'x', 26.0, (0, 13, 4.5), +1, 1),
        (('cabeca',), 'x', 5.0, (0, 14, 3), +1, 2)]),
    'uivador': dict(bob=0.5, partes=[
        (('perna_d',), 'x', 28.0, (0, 0, 0), +1, 1), (('perna_e',), 'x', 28.0, (0, 0, 0), -1, 1),
        (('braco_d',), 'x', 28.0, (0, 11, 9), -1, 1), (('braco_e',), 'x', 28.0, (0, 11, 9), +1, 1),
        (('cabeca',), 'x', 4.0, None, +1, 2)]),
    'cao': dict(bob=0.35, partes=[
        (('pata_fd', 'pata_te'), 'x', 22.0, (0, -3, 0), +1, 1), (('pata_fe', 'pata_td'), 'x', 22.0, (0, -3, 0), -1, 1),
        (('cabeca',), 'x', 4.0, (0, -5, 7), +1, 2)]),
    'festeiro': dict(bob=0.6, partes=[
        (('perna_d',), 'x', 28.0, (0, 0, 0), +1, 1), (('perna_e',), 'x', 28.0, (0, 0, 0), -1, 1),
        (('braco_d',), 'x', 30.0, (0, 9, 0), -1, 1),
        (('cabeca',), 'z', 12.0, (0, 11.5, 0), +1, 1)]),
    'sombra': dict(bob=0.3, partes=[
        (('braco_d',), 'x', 24.0, (0, 14, 0), -1, 1), (('braco_e',), 'x', 24.0, (0, 14, 0), +1, 1),
        (('cabeca',), 'z', 4.0, (0, 16.5, 0), +1, 1)]),
    'corredor': dict(bob=0.9, trocar=True, partes=[
        (('perna_d',), 'x', 45.0, (0, 0, 0), +1, 1), (('perna_e',), 'x', 45.0, (0, 0, 0), -1, 1)]),
    'afogado': dict(bob=0.4, partes=[
        (('perna_d',), 'x', 24.0, (0, -4, 0), +1, 1), (('perna_e',), 'x', 24.0, (0, -4, 0), -1, 1),
        (('cabeca',), 'z', 5.0, (0, 12.5, 0), +1, 1)]),
}

_E = {'x': np.array([1.0, 0, 0]), 'y': np.array([0, 1.0, 0]), 'z': np.array([0, 0, 1.0])}


def _R(eixo, ang):
    a = math.radians(ang)
    c, s = math.cos(a), math.sin(a)
    if eixo == 'x':
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if eixo == 'y':
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def compor(c, eixo, a2, o2):
    """Gira o cubo c (que pode ja ter rotacao) mais a2 graus em `eixo` em volta de o2. Mesmo eixo: vira uma rotacao so
    (soma dos angulos, centro calculado). Eixo diferente: nao da para representar - devolve None."""
    o2 = np.array(o2, float)
    if not c.rot or c.rot[1] == 0:
        return c.com(rot=(eixo, float(a2), tuple(o2)))
    e1, a1, o1 = c.rot
    o1 = np.array(o1, float)
    if e1 != eixo:
        return None
    A = a1 + a2
    R2 = _R(eixo, a2)
    if abs(A) < 1e-6:
        t = (R2 - np.eye(3)) @ (o1 - o2)
        return c.com(de=c.de + t, ate=c.ate + t, rot=None)
    RA = _R(eixo, A)
    tl = -RA @ o1 + R2 @ (o1 - o2) + o2
    # (I - RA) c = tl, so no plano perpendicular ao eixo
    k = 'xyz'.index(eixo)
    idx = [i for i in range(3) if i != k]
    M = (np.eye(3) - RA)[np.ix_(idx, idx)]
    sol = np.linalg.solve(M, tl[idx])
    cen = o1.copy()
    cen[idx] = sol
    return c.com(rot=(eixo, float(A), tuple(float(v) for v in cen)))


def mover(c, dy):
    if dy == 0:
        return c
    d = np.array([0, dy, 0.0])
    rot = c.rot
    if rot:
        rot = (rot[0], rot[1], tuple(float(v) for v in np.array(rot[2], float) + d))
    return c.com(de=c.de + d, ate=c.ate + d, rot=rot)


def pivo_grupo(cubos, grupo):
    sel = [c for c in cubos if c.grupo == grupo]
    de = np.min([c.de for c in sel], axis=0)
    ate = np.max([c.ate for c in sel], axis=0)
    return ((de[0] + ate[0]) / 2, de[1], (de[2] + ate[2]) / 2)


def quadro(idle, olhos_idle, spec, k, avisos):
    fase = 2 * math.pi * k / N
    corpo = list(idle)
    olhos = list(olhos_idle)
    grupos = {c.grupo for c in corpo}
    for gs, eixo, amp, piv, sinal, freq in spec['partes']:
        gs = tuple(g for g in gs if g in grupos)
        if not gs:
            continue
        p = piv if piv is not None else pivo_grupo(corpo, gs[0])
        ang = sinal * amp * math.sin(freq * fase)
        if abs(ang) < 1e-4 and not spec.get('trocar'):
            continue
        def aplica(lista, alvo_olho):
            out = []
            for c in lista:
                if (c.grupo in gs) or (alvo_olho and 'cabeca' in gs):
                    # trocar: o giro do passo substitui o da pose (como o walk1/walk2 antigo do Corredor)
                    n = c.com(rot=(eixo, float(ang), tuple(float(v) for v in p))) if spec.get('trocar') else compor(c, eixo, ang, p)
                    if n is None:
                        avisos.add(f'{c.nome}: eixo diferente, ficou parado')
                        n = c
                    elif n.rot and abs(n.rot[1]) > LIM + 1e-6:
                        avisos.add(f'{c.nome}: {n.rot[1]:.1f} passou de 45, limitei')
                        n = compor(c, eixo, math.copysign(LIM, n.rot[1]) - (c.rot[1] if c.rot else 0.0), p) or c
                    out.append(n)
                else:
                    out.append(c)
            return out
        corpo = aplica(corpo, False)
        olhos = aplica(olhos, True)
    # sobe e desce: mais alto no meio do passo (pe embaixo do corpo), mais baixo quando as pernas abrem
    teto = 32.0 - max(float(c.ate[1]) for c in list(idle) + list(olhos_idle)) - 0.01
    dy = max(0.0, min(spec['bob'], teto)) * (0.5 + 0.5 * math.cos(2 * fase))
    return [mover(c, dy) for c in corpo], [mover(c, dy) for c in olhos]


def uv_do_gerar(nome, M):
    """Refaz exatamente o UV do gerar.py (mesmas chaves, mesma ordem) para os quadros novos baterem com o atlas."""
    base = M['cubos']()
    quadros = M['quadros'](base)
    olhos = M['olhos'](base)
    chave = lambda c: (c.nome, c.pele, c.so)
    reps = {}
    for lista in list(quadros.values()) + list(olhos.values()):
        for c in lista:
            reps.setdefault(chave(c), c)
    reps_l = list(reps.values())
    altura = kit.arrumar_uv(reps_l, gerar.LARGURA, gerar.ESCALA_TEX)
    H = gerar.pot2(altura)
    W = gerar.LARGURA if gerar.LARGURA & (gerar.LARGURA - 1) == 0 else gerar.pot2(gerar.LARGURA)
    for lista in list(quadros.values()) + list(olhos.values()):
        for c in lista:
            c.uv = dict(reps[chave(c)].uv)
    return quadros, olhos, W, H, reps, chave


def conferir_angulos(lista, nome):
    for c in lista:
        if c.rot and abs(c.rot[1]) > LIM + 1e-6:
            raise ValueError(f'{nome}/{c.nome}: angulo {c.rot[1]}')


def main():
    import json
    prev = []
    for nome, M in BR.items():
        quadros, olhos, W, H, reps, chave = uv_do_gerar(nome, M)
        tex_key = f'corona:item/backrooms/{nome}'
        # o idle gerado agora tem que ser identico ao que ja esta no pacote (prova de que o UV bate)
        atual = os.path.join(gerar.JAVA, 'models', 'entity', 'backrooms', f'{nome}_idle.json')
        with open(atual, encoding='utf-8') as f:
            velho = json.load(f)
        novo = json.loads(json.dumps(kit.modelo_java(quadros['idle'], tex_key, W, H)))
        if velho['elements'] != novo['elements']:
            raise SystemExit(f'{nome}: UV diferente do pacote, parei')
        avisos = set()
        spec = SPEC[nome]
        oi = olhos.get('idle', [])
        fr = []
        for k in range(N):
            corpo, olho = quadro(quadros['idle'], oi, spec, k, avisos)
            conferir_angulos(corpo + olho, nome)
            q = f'andar_{k:02d}'
            kit.salvar_json(os.path.join(gerar.JAVA, 'models', 'entity', 'backrooms', f'{nome}_{q}.json'), kit.modelo_java(corpo, tex_key, W, H))
            kit.salvar_json(os.path.join(gerar.JAVA, 'items', 'backrooms', f'{nome}_{q}.json'), kit.item_java(f'corona:entity/backrooms/{nome}_{q}'))
            if olho:
                kit.salvar_json(os.path.join(gerar.JAVA, 'models', 'entity', 'backrooms', f'{nome}_olhos_{q}.json'), kit.modelo_java(olho, tex_key, W, H))
                kit.salvar_json(os.path.join(gerar.JAVA, 'items', 'backrooms', f'{nome}_olhos_{q}.json'), kit.item_java(f'corona:entity/backrooms/{nome}_olhos_{q}'))
            fr.append(corpo + olho)
        print('ok', nome, N, 'quadros', ('avisos: ' + '; '.join(sorted(avisos))) if avisos else '')
        prev.append((nome, fr))
    return prev


if __name__ == '__main__':
    main()
