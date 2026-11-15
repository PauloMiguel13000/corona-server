"""Relevo 3D dos infectados ("menos caixote"): pecas em volume por cima do zumbi do jogo.
Cabeca (Java e Bedrock): sobrancelha, macas do rosto, queixo caido com dentes, orelhas e o que e do tipo (capuz de
verdade, pescoco do tanque, papo e lingua do cuspidor, capacete do explosivo, chifres e presas do chefe).
Corpo e bracos (so Bedrock, que deixa por geometria no peitoral): costelas, barriga, ombros, dinamite, espinhos.
Coordenadas no espaco do Bedrock (px): cabeca x -4..4, y 24..32, z -4..4; frente = -z. Cores = amostras 4x4 pintadas
na area livre da textura (x 32..64, y 0..16).
"""
AMOSTRAS = ['pele', 'pele_esc', 'carne', 'osso', 'roupa', 'cabelo', 'preto', 'dente', 'bolha', 'metal', 'vermelho', 'amarelo']
TAM = 4


def pos(nome):
    i = AMOSTRAS.index(nome)
    return 32 + (i % 8) * TAM, (i // 8) * TAM


def cores(t):
    p = t['pele']
    return {'pele': p, 'pele_esc': tuple(int(c * 0.62) for c in p), 'carne': (128, 22, 24), 'osso': (214, 204, 176),
            'roupa': t.get('capuz') or t['camisa'], 'cabelo': t['cabelo'], 'preto': (20, 16, 16), 'dente': (222, 212, 180),
            'bolha': t.get('bolha', (190, 220, 84)), 'metal': (112, 114, 122), 'vermelho': (170, 28, 26), 'amarelo': (212, 172, 44)}


def P(x0, x1, y0, y1, z0, z1, mat, osso='head'):
    return dict(a=(min(x0, x1), y0, min(z0, z1)), b=(max(x0, x1), y1, max(z0, z1)), mat=mat, osso=osso)


def par(x0, x1, y0, y1, z0, z1, mat, osso='head'):
    o2 = {'rightArm': 'leftArm'}.get(osso, osso)
    return [P(x0, x1, y0, y1, z0, z1, mat, osso), P(-x0, -x1, y0, y1, z0, z1, mat, o2)]


def rosto(queixo=1.0, sobr=1.0):
    ps = [P(-4.3, 4.3, 28.7, 29.4 + 0.3 * sobr, -5.5 - 0.3 * sobr, -5.0, 'pele_esc'),
          P(-0.7, 0.7, 26.7, 28.0, -5.7, -5.0, 'pele_esc')]
    ps += par(-4.7, -2.7, 26.2, 27.3, -5.5, -5.0, 'pele')
    ps += par(-5.8, -5.0, 26.6, 28.6, -1.0, 1.0, 'pele')
    w = 3.2 + 0.9 * (queixo - 1)
    ps.append(P(-w, w, 22.3 - 0.4 * (queixo - 1), 24.1, -5.9 - 0.3 * (queixo - 1), -2.4, 'pele'))
    for k in range(5):
        x = -2.7 + k * 1.2
        ps.append(P(x, x + 0.5, 24.1, 24.6, -5.75, -5.35, 'dente'))
    return ps


def pecas(tipo):
    if tipo == 'comum':
        ps = rosto()
        ps += [P(-4.2, -1, 33, 33.8, -3.5, 1, 'cabelo'), P(0.5, 3.8, 33, 34.3, 0, 4.2, 'cabelo'), P(-2.2, 2, 33, 33.5, 2.5, 5.3, 'cabelo'),
               P(-5.5, -5, 30, 33.3, -2, 4, 'cabelo'), P(2, 4.2, 33, 33.5, -3.6, -1.4, 'carne'), P(2.6, 3.6, 33.5, 34.0, -3, -2, 'osso')]
        for k in range(3):
            ps.append(P(-3.0, 0.2, 16.2 + k * 1.3, 16.7 + k * 1.3, -3.6, -3.0, 'osso', 'body'))
        ps += [P(-7.2, -4.8, 16, 16.9, -3.7, -3.0, 'osso', 'rightArm'), P(-3.4, -0.2, 15.6, 16.2, -3.5, -3.0, 'carne', 'body'),
               P(-4.2, 4.2, 23.2, 24.6, -3.3, 3.3, 'roupa', 'body')]
    elif tipo == 'corredor':
        ps = rosto()
        ps += [P(-5.7, 5.7, 33, 34.2, -4.6, 5.9, 'roupa'), P(-5.7, 5.7, 23.6, 33.4, 5.0, 6.2, 'roupa'), P(-2.2, 2.2, 29.5, 33.2, 6.2, 7.4, 'roupa'),
               P(-5.7, 5.7, 32.1, 33.4, -6.0, -4.6, 'roupa'), P(-1, 1, 31, 32.4, 7.4, 8.2, 'roupa')]
        ps += par(-6.1, -5.0, 23.8, 33.4, -4.2, 5.8, 'roupa')
        ps += par(-5.9, -5.0, 24.4, 32.2, -5.6, -4.2, 'roupa')
        ps += [P(-3.2, 3.2, 20.5, 24.6, 3.0, 4.7, 'roupa', 'body'), P(-3.2, 3.2, 14, 17, -3.7, -3.0, 'roupa', 'body'),
               P(-4.4, 4.4, 23.2, 24.8, -3.4, 3.4, 'roupa', 'body')]
        ps += par(-9.3, -3.7, 13.4, 14.6, -3.3, 3.3, 'roupa', 'rightArm')
    elif tipo == 'tanque':
        ps = rosto(1.8, 2.0)
        ps += [P(-7.5, 7.5, 22.0, 24.6, -2.6, 4.6, 'pele'), P(-6.0, 6.0, 24.6, 26.2, 1.0, 5.2, 'pele'), P(-1.5, 1.5, 33, 33.5, -2, 3, 'pele_esc')]
        ps += par(-4.7, -0.3, 19.0, 23.2, -4.0, -3.0, 'pele', 'body')
        ps += [P(-3.6, 3.6, 13.5, 18.6, -3.7, -3.0, 'pele', 'body'), P(-5.2, 5.2, 18, 24, 3.0, 4.2, 'pele', 'body')]
        ps += par(-9.8, -4.3, 20.4, 25.4, -3.4, 3.4, 'pele', 'rightArm')
        ps += par(-9.5, -8.9, 13.5, 20.4, -2.6, 2.6, 'pele', 'rightArm')
        ps += par(-9.4, -3.6, 10.4, 13.8, -3.3, 3.3, 'pele_esc', 'rightArm')
    elif tipo == 'cuspidor':
        ps = rosto(1.3)
        ps += [P(-3.3, 3.3, 20.4, 23.4, -5.7, -2.2, 'bolha'), P(-2.1, 2.1, 19.5, 20.4, -5.0, -2.8, 'bolha'), P(-0.8, 0.8, 20.8, 24.6, -6.3, -5.7, 'vermelho'),
               P(2.4, 4.6, 31.4, 33.7, -5.7, -4.2, 'bolha'), P(-5.8, -4.6, 28, 30.2, 1, 3.2, 'bolha'), P(-1.2, 1.2, 33, 34.0, 1, 3.4, 'bolha'),
               P(-3.5, -1.5, 33, 33.6, -3, 0, 'cabelo')]
        ps += [P(-3.9, 3.9, 13.0, 19.6, -5.2, -3.0, 'pele', 'body'), P(-2.9, 2.9, 14.0, 18.6, -6.1, -5.2, 'pele', 'body'),
               P(-2.1, -0.6, 16.0, 17.5, -6.6, -6.1, 'bolha', 'body'), P(1.0, 2.5, 14.6, 16.1, -6.6, -6.1, 'bolha', 'body'),
               P(-0.4, 0.8, 17.4, 18.4, -6.5, -6.1, 'bolha', 'body'), P(-3.2, 3.2, 17.5, 23.2, 3.0, 4.5, 'pele', 'body'),
               P(-4.6, -3.9, 14, 18.5, -4.6, 1.5, 'pele', 'body'), P(3.9, 4.6, 14, 18.5, -4.6, 1.5, 'pele', 'body')]
    elif tipo == 'explosivo':
        ps = rosto()
        ps += [P(-5.5, 5.5, 31.6, 33.7, -5.5, 5.5, 'amarelo'), P(-5.5, 5.5, 31.6, 32.2, -7.2, -5.5, 'amarelo'), P(-1, 1, 33.7, 34.4, -5.2, 5.2, 'amarelo'),
               P(-1.1, 1.1, 32.3, 33.5, -6.1, -5.5, 'metal'), P(-0.6, 0.6, 32.6, 33.2, -6.3, -6.1, 'dente')]
        for k in range(5):
            x = -3.9 + k * 1.6
            ps += [P(x, x + 1.2, 15.4, 21.0, -4.5, -3.0, 'vermelho', 'body'), P(x + 0.4, x + 0.8, 21.0, 22.0, -4.0, -3.5, 'preto', 'body')]
        ps += [P(-4.5, 4.5, 16.4, 17.3, -4.7, 3.3, 'preto', 'body'), P(-4.5, 4.5, 19.2, 20.1, -4.7, 3.3, 'preto', 'body'),
               P(-3.0, 3.0, 15.0, 22.0, 3.0, 5.2, 'metal', 'body'), P(-0.8, 0.8, 17.6, 19.0, -5.0, -4.5, 'metal', 'body')]
    else:  # chefe
        ps = rosto(1.6, 1.6)
        ps += par(-6.8, -5.0, 30.0, 32.0, -1.0, 1.0, 'osso')
        ps += par(-7.7, -6.2, 31.4, 35.0, -0.8, 0.8, 'osso')
        ps += par(-7.0, -5.8, 35.0, 36.6, -0.6, 0.6, 'osso')
        ps += par(-3.7, -2.8, 24.1, 26.6, -6.2, -5.4, 'osso')
        for k, x in enumerate((-3.6, -1.4, 0.6, 2.8)):
            ps.append(P(x, x + 0.8, 33, 34.3 + (k % 2) * 0.9, -4.6, -3.8, 'osso'))
        for k in range(3):
            ps.append(P(-0.6, 0.6, 26.4 + k * 2.2, 27.6 + k * 2.2, 5.0, 6.4 - k * 0.3, 'osso'))
        ps += par(-9.8, -4.5, 22.0, 25.5, -3.5, 3.5, 'metal', 'rightArm')
        ps += par(-8.3, -7.0, 25.5, 28.2, -0.7, 0.7, 'osso', 'rightArm')
        ps += par(-6.4, -5.4, 25.5, 27.2, -0.6, 0.6, 'osso', 'rightArm')
        for k in range(4):
            y = 15.4 + k * 1.7
            ps += par(-3.4, -0.5, y, y + 0.7, -3.7, -3.0, 'osso', 'body')
        ps.append(P(-0.4, 0.4, 15, 22.4, -3.6, -3.0, 'osso', 'body'))
        for k in range(3):
            ps.append(P(-0.6, 0.6, 14.5 + k * 3, 16.0 + k * 3, 3.0, 5.2 - k * 0.3, 'osso', 'body'))
        ps += par(-9.4, -8.9, 12.2, 14.0, -1.8, -1.0, 'osso', 'rightArm')
        ps += par(-9.4, -8.9, 12.2, 14.0, 0.6, 1.4, 'osso', 'rightArm')
    return ps


def _r(v):
    return round(float(v), 4)


def _uv_px(x0, y0, x1, y1):
    return [_r(x0 / 4), _r(y0 / 2), _r(x1 / 4), _r(y1 / 2)]


CASCO = {'north': _uv_px(8, 8, 16, 16), 'east': _uv_px(0, 8, 8, 16), 'south': _uv_px(24, 8, 32, 16), 'west': _uv_px(16, 8, 24, 16),
         'up': _uv_px(16, 8, 8, 0), 'down': _uv_px(16, 0, 24, 8)}


def modelo_java(tipo, tex):
    """So as pecas da cabeca (o Java desenha o modelo do item do elmo na cabeca)."""
    els = [{'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': {f: {'uv': uv, 'texture': '#t'} for f, uv in CASCO.items()}}]
    for c in pecas(tipo):
        if c['osso'] != 'head':
            continue
        (x0, y0, z0), (x1, y1, z1) = c['a'], c['b']
        xa, xb = (5 - x1) * 1.6, (5 - x0) * 1.6
        u, v = pos(c['mat'])
        m = 0.4
        uv = [_r((u + m) / 4), _r((v + m) / 2), _r((u + TAM - m) / 4), _r((v + TAM - m) / 2)]
        els.append({'from': [_r(xa), _r((y0 - 23) * 1.6), _r((z0 + 5) * 1.6)], 'to': [_r(xb), _r((y1 - 23) * 1.6), _r((z1 + 5) * 1.6)],
                    'faces': {f: {'uv': uv, 'texture': '#t'} for f in ('north', 'east', 'south', 'west', 'up', 'down')}})
    for e in els:
        for k in ('from', 'to'):
            assert all(-16 <= v <= 32 for v in e[k]), (tipo, e)
    return {'textures': {'t': tex, 'particle': tex}, 'elements': els,
            'display': {'head': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]}}}


def _cubo_bed(c):
    (x0, y0, z0), (x1, y1, z1) = c['a'], c['b']
    u, v = pos(c['mat'])
    m = 0.4
    face = {'uv': [_r(u + m), _r(v + m)], 'uv_size': [_r(TAM - 2 * m), _r(TAM - 2 * m)]}
    return {'origin': [_r(x0), _r(y0), _r(z0)], 'size': [_r(x1 - x0), _r(y1 - y0), _r(z1 - z0)],
            'uv': {f: dict(face) for f in ('north', 'east', 'south', 'west', 'up', 'down')}}


def _geo(ident, ossos):
    return {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': ident, 'texture_width': 64, 'texture_height': 32,
                        'visible_bounds_width': 4, 'visible_bounds_height': 4.5, 'visible_bounds_offset': [0, 1.5, 0]},
        'bones': ossos}]}


def geo_elmo(tipo):
    cubos = [{'origin': [-4, 24, -4], 'size': [8, 8, 8], 'uv': [0, 0], 'inflate': 1.0}]
    cubos += [_cubo_bed(c) for c in pecas(tipo) if c['osso'] == 'head']
    return _geo(f'geometry.corona.zumbi.{tipo}.elmo', [{'name': 'head', 'pivot': [0, 24, 0], 'cubes': cubos}])


def geo_peitoral(tipo):
    ps = pecas(tipo)
    return _geo(f'geometry.corona.zumbi.{tipo}.peitoral', [
        {'name': 'body', 'pivot': [0, 24, 0], 'cubes': [{'origin': [-4, 12, -2], 'size': [8, 12, 4], 'uv': [16, 16], 'inflate': 1.01}]
         + [_cubo_bed(c) for c in ps if c['osso'] == 'body']},
        {'name': 'rightArm', 'pivot': [-5, 22, 0], 'cubes': [{'origin': [-8, 12, -2], 'size': [4, 12, 4], 'uv': [40, 16], 'inflate': 1.0}]
         + [_cubo_bed(c) for c in ps if c['osso'] == 'rightArm']},
        {'name': 'leftArm', 'pivot': [5, 22, 0], 'mirror': True, 'cubes': [{'origin': [4, 12, -2], 'size': [4, 12, 4], 'uv': [40, 16], 'inflate': 1.0}]
         + [dict(_cubo_bed(c), mirror=False) for c in ps if c['osso'] == 'leftArm']}])
