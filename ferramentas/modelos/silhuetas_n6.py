"""Silhuetas do Arsenal Ancestral (20/11, Paulo: "as armaduras estao boas mas estao repetindo, refaca").

Cada uma das 10 armaduras do nivel 6 tem um corte proprio (peito, ombros, cintura, pernas, elmo e nucleo):
  CINZAS    Forjador: placa diagonal em faixa, ombreira esquerda gigante (so a esquerda), mascara de forja com grade.
  CATACUMBA Relicario: costelas vazadas no peito, tabardo de pano caindo na frente, capuz fundo com olhos acesos.
  PINACULO  Para-raio: placas em V empilhadas, bobinas nos bracos, elmo com fenda vertical e aletas.
  RUBRA     Conde: casaco longo de gola alta, espartilho com cordao e botoes, mascara so nos olhos.
  ECO       Ressonancia: aneis concentricos no peito (alto-falante), tubos nos bracos, elmo com grade redonda.
  PUTRIDO   Esporo: placas organicas com borda rasgada, musgo, mascara de gas com dois filtros.
  GLACIAR   Geada: facetas em diagonal, pontas de gelo nas bordas, viseira em losango.
  ABISSAL   Mare: escamas sobrepostas, nadadeiras nos bracos, capacete de vidro (escafandro).
  TITAS     Colosso: blocos grossos com rebites, cinto largo de fivela, balde com grade em cruz.
  CAOS      Fenda: metade placa e metade vazio estrelado, corte em diagonal, um olho so aceso.
"""
import numpy as np

import armaduras_v3 as V3

VAZIO, PLACA, ACAB, PANO, PANO2, COURO, PELO, OSSO, MALHA, ESCURO, BRILHO, PELE_ARM, METAL2, FURO, VIDRO = range(15)
botas, cinto_saia, coxa = V3.botas, V3.cinto_saia, V3.coxa


def _tampa(f, tipo=PLACA):
    """Topo e baixo do tronco/braco (comum)."""
    if f.baixo:
        return True
    if f.cima:
        f.ret(tipo)
        return True
    return False


# ================================================================== corpos (peito/bracos/pernas), um por masmorra

def t_cinzas(f, var):
    P = f
    if f.parte == 'body' and f.camada == 1:
        if _tampa(f):
            return
        P.ret(COURO)
        if f.frente or f.costas:
            d = f.S - f.T * 0.8 if f.frente else (1 - f.S) - f.T * 0.8   # faixa diagonal
            P.pinta((d > -0.55) & (d < 0.05), PLACA)
            P.pinta((d > 0.05) & (d < 0.14), ACAB, nova_placa=False)
            P.ret(PLACA, 0, 1, 0, 0.14)
            P.pinta((f.T > 0.8), METAL2)
            P.dentes(0.92, 0.08, 0.2)
        else:
            P.ret(PLACA, 0, 1, 0, 0.2)
            P.laminas(METAL2, 0.55, 1, 3)
    elif f.parte == 'rarm' and f.camada == 1:
        if _tampa(f):
            return
        P.ret(PLACA, 0, 1, 0, 0.5)            # ombreira grossa de bloco forjado
        P.dentes(0.44, 0.06, 0.5, tipo=ESCURO)
        P.ret(ACAB, 0, 1, 0.5, 0.56)
        P.ret(COURO, 0, 1, 0.56, 0.74)
        P.ret(METAL2, 0, 1, 0.74, 1)
    elif f.parte == 'rleg' and f.camada == 1:
        botas(f, 0.5, METAL2, cano=ACAB)
    elif f.parte == 'body' and f.camada == 2:
        cinto_saia(f, COURO, faldas=True, saia_t=0.95, cor_cinto=METAL2)
    elif f.parte == 'rleg' and f.camada == 2:
        if _tampa(f, ESCURO):
            return
        P.ret(COURO)
        P.ret(PLACA, 0, 1, 0.2, 0.7) if f.frente else None


def t_catacumba(f, var):
    P = f
    if f.parte == 'body' and f.camada == 1:
        if _tampa(f, OSSO):
            return
        P.ret(ESCURO)
        if f.frente or f.costas:
            for k in range(5):                 # costelas
                a = 0.12 + k * 0.11
                P.pinta((f.T > a) & (f.T < a + 0.06) & (np.abs(f.S - 0.5) > 0.06), OSSO)
            P.ret(OSSO, 0.44, 0.56, 0.06, 0.66)  # esterno
            if f.frente:
                P.ret(PANO, 0.3, 0.7, 0.66, 1.0)   # tabardo
                P.franja(0.92, 0.08, 31)
        else:
            P.ret(PANO, 0, 1, 0, 1)
    elif f.parte == 'rarm' and f.camada == 1:
        if _tampa(f, PANO):
            return
        P.ret(PANO, 0, 1, 0, 0.62)
        P.franja(0.5, 0.12, 17)
        P.ret(PANO2, 0, 1, 0.62, 0.66)
        P.ret(OSSO, 0, 1, 0.66, 0.74)
        P.ret(PLACA, 0, 1, 0.74, 1)
        P.pinta((f.T > 0.5) & (f.T < 0.62) & (f.tipo == VAZIO), PANO2, nova_placa=False)
    elif f.parte == 'rleg' and f.camada == 1:
        botas(f, 0.62, PLACA, cano=OSSO)
    elif f.parte == 'body' and f.camada == 2:
        cinto_saia(f, PANO, faldas=True, saia_t=1.0, cor_cinto=OSSO)
    elif f.parte == 'rleg' and f.camada == 2:
        if _tampa(f, ESCURO):
            return
        P.ret(PANO2)
        if f.frente:
            P.ret(OSSO, 0.3, 0.7, 0.42, 0.56)


def t_pinaculo(f, var):
    P = f
    if f.parte == 'body' and f.camada == 1:
        if _tampa(f):
            return
        P.ret(MALHA)
        if f.frente or f.costas:
            for k in range(4):                 # V empilhados
                a = 0.06 + k * 0.2
                v = a + np.abs(f.S - 0.5) * -0.5 + 0.25
                P.pinta((f.T > v - 0.13) & (f.T < v), PLACA)
            P.ret(ACAB, 0, 1, 0, 0.06)
        else:
            P.ret(PLACA, 0, 1, 0, 0.3)
    elif f.parte == 'rarm' and f.camada == 1:
        if _tampa(f):
            return
        P.ret(PLACA, 0, 1, 0, 0.3)
        P.dentes(0.26, 0.08, 0.33, tipo=ESCURO)
        for k in range(4):                     # bobinas
            a = 0.36 + k * 0.16
            P.ret(ACAB, 0, 1, a, a + 0.07)
            P.ret(ESCURO, 0, 1, a + 0.07, a + 0.16)
    elif f.parte == 'rleg' and f.camada == 1:
        botas(f, 0.55, PLACA)
        if f.frente:
            P.pinta((f.T > 0.66) & (f.T < 0.8) & (np.abs(f.S - 0.5) < (f.T - 0.66) * 3), ACAB, nova_placa=False)
    elif f.parte == 'body' and f.camada == 2:
        cinto_saia(f, PLACA, faldas=False, cor_cinto=ACAB)
    elif f.parte == 'rleg' and f.camada == 2:
        if _tampa(f, ESCURO):
            return
        P.ret(MALHA)
        P.ret(PLACA, 0, 1, 0, 0.36)
        P.dentes(0.36, 0.1, 0.5, tipo=MALHA)


def t_rubra(f, var):
    P = f
    if f.parte == 'body' and f.camada == 1:
        if _tampa(f, PANO2):
            return
        P.ret(PANO2)
        if f.frente:
            P.ret(PLACA, 0.25, 0.75, 0.1, 0.8)          # espartilho
            for k in range(6):
                a = 0.16 + k * 0.1
                P.pinta((f.T > a) & (f.T < a + 0.03) & (np.abs(f.S - 0.5) < 0.2), ACAB, nova_placa=False)   # cordao
            P.ret(ESCURO, 0.48, 0.52, 0.1, 0.8)
            P.ret(PANO, 0, 0.18, 0, 1)                   # lapelas do casaco
            P.ret(PANO, 0.82, 1, 0, 1)
        if f.costas:
            P.ret(PANO, 0, 1, 0, 1)
            P.ret(PANO2, 0.45, 0.55, 0.5, 1)
    elif f.parte == 'rarm' and f.camada == 1:
        if _tampa(f, PANO2):
            return
        P.ret(PANO2)
        P.ret(PLACA, 0, 1, 0, 0.2)
        P.ret(ACAB, 0, 1, 0.86, 0.92)
        P.ret(PANO, 0, 1, 0.92, 1)
    elif f.parte == 'rleg' and f.camada == 1:
        botas(f, 0.45, COURO, cano=ACAB)
    elif f.parte == 'body' and f.camada == 2:
        cinto_saia(f, PANO, faldas=True, saia_t=1.0, cor_cinto=ACAB)   # aba do casaco
    elif f.parte == 'rleg' and f.camada == 2:
        if _tampa(f, ESCURO):
            return
        P.ret(PANO)
        if not f.lado:
            P.ret(PANO2, 0, 1, 0, 0.25)


def t_eco(f, var):
    P = f
    if f.parte == 'body' and f.camada == 1:
        if _tampa(f):
            return
        P.ret(PLACA)
        if f.frente:
            r = np.hypot((f.S - 0.5) * f.W, (f.T - 0.38) * f.H)
            for k, (a, b, tp) in enumerate(((0, 4, BRILHO), (4, 6, ESCURO), (6, 9, ACAB), (9, 11, ESCURO), (11, 14, PLACA))):
                P.pinta((r >= a) & (r < b), tp, nova_placa=(tp == PLACA))
            P.ret(METAL2, 0, 1, 0.72, 1)
        else:
            P.colunas(METAL2, 0, 1, 4, 0.1, 0.9)
    elif f.parte == 'rarm' and f.camada == 1:
        if _tampa(f):
            return
        P.ret(MALHA)
        P.ret(PLACA, 0, 1, 0, 0.26)
        P.pinta(np.abs(f.S - 0.5) < 0.16, METAL2, nova_placa=False)   # tubo
        P.ret(ACAB, 0, 1, 0.26, 0.3)
    elif f.parte == 'rleg' and f.camada == 1:
        botas(f, 0.58, METAL2)
    elif f.parte == 'body' and f.camada == 2:
        cinto_saia(f, METAL2, faldas=False, cor_cinto=ESCURO)
    elif f.parte == 'rleg' and f.camada == 2:
        if _tampa(f, ESCURO):
            return
        P.ret(MALHA)
        P.pinta(np.abs(f.S - 0.5) < 0.14, METAL2, nova_placa=False)
        P.ret(PLACA, 0, 1, 0.4, 0.58)


def t_putrido(f, var):
    P = f
    if f.parte == 'body' and f.camada == 1:
        if _tampa(f, PELO):
            return
        P.ret(COURO)
        bolha = V3.fbm(f.X * 0.12, f.Y * 0.12, 0 * f.X, 3, 1, 61)
        P.pinta(bolha > 0.46, PLACA)
        P.pinta((bolha > 0.38) & (bolha <= 0.46), PELO, nova_placa=False)
        if f.frente:
            P.ret(PELO, 0, 1, 0, 0.1)
        P.franja(0.88, 0.12, 41)
    elif f.parte == 'rarm' and f.camada == 1:
        if _tampa(f, PELO):
            return
        P.ret(COURO)
        P.ret(PLACA, 0, 1, 0, 0.36)
        P.ret(PELO, 0, 1, 0.36, 0.46)
        P.franja(0.4, 0.12, 23, tipo=COURO)
        P.ret(COURO, 0, 1, 0.46, 1)
    elif f.parte == 'rleg' and f.camada == 1:
        botas(f, 0.6, COURO, cano=PELO, franja_sal=7)
    elif f.parte == 'body' and f.camada == 2:
        cinto_saia(f, PELO, faldas=True, saia_t=0.9, cor_cinto=COURO)
        f.franja(0.82, 0.16, 47)
    elif f.parte == 'rleg' and f.camada == 2:
        if _tampa(f, ESCURO):
            return
        P.ret(COURO)
        bolha = V3.fbm(f.X * 0.15, f.Y * 0.15, 0 * f.X, 2, 1, 67)
        P.pinta(bolha > 0.52, PLACA)


def t_glaciar(f, var):
    P = f
    if f.parte == 'body' and f.camada == 1:
        if _tampa(f):
            return
        P.ret(PLACA)
        if not f.lado:
            for k in range(-3, 4):             # facetas diagonais
                d = (f.S + f.T * 0.6) * 4 - k
                P.pinta((d > 0) & (d < 0.12), ACAB, nova_placa=False)
            P.pinta(((f.S + f.T * 0.6) * 4 % 2) < 1, METAL2, nova_placa=False)
        P.dentes(0.86, 0.14, 0.2)
    elif f.parte == 'rarm' and f.camada == 1:
        if _tampa(f):
            return
        P.ret(PLACA, 0, 1, 0, 0.5)
        P.dentes(0.42, 0.14, 0.25, tipo=MALHA)
        P.ret(MALHA, 0, 1, 0.56, 0.8)
        P.ret(PLACA, 0, 1, 0.8, 1)
    elif f.parte == 'rleg' and f.camada == 1:
        botas(f, 0.52, PLACA, cano=ACAB)
        if not f.cima and not f.baixo:
            f.dentes(0.52 + 0.08, 0.06, 0.33, para_baixo=False)
    elif f.parte == 'body' and f.camada == 2:
        cinto_saia(f, PLACA, faldas=True, saia_t=0.92, cor_cinto=ACAB)
        if not (f.cima or f.baixo):
            f.dentes(0.86, 0.12, 0.25)
    elif f.parte == 'rleg' and f.camada == 2:
        if _tampa(f, ESCURO):
            return
        P.ret(METAL2)
        P.ret(PLACA, 0, 1, 0, 0.48)
        P.dentes(0.42, 0.12, 0.33, tipo=METAL2)


def t_abissal(f, var):
    P = f
    if f.parte == 'body' and f.camada == 1:
        if _tampa(f):
            return
        P.ret(PLACA)
        linha = np.floor(f.T * 8)
        x = f.S * 5 + (linha % 2) * 0.5
        arco = ((x % 1) - 0.5) ** 2 * 0.5 + (f.T * 8 % 1)
        P.pinta((arco > 0.82) & (arco < 1.0), ESCURO, nova_placa=False)   # escamas
        P.pinta((arco > 0.66) & (arco <= 0.82), METAL2, nova_placa=False)
        P.ret(ACAB, 0, 1, 0, 0.06)
    elif f.parte == 'rarm' and f.camada == 1:
        if _tampa(f):
            return
        P.ret(PLACA)
        if f.lado or f.costas:
            P.pinta(f.S > 0.45 + f.T * 0.3, PANO2)     # nadadeira
            P.franja(0.95, 0.05, 13)
        P.ret(ACAB, 0, 1, 0.6, 0.64)
    elif f.parte == 'rleg' and f.camada == 1:
        botas(f, 0.56, PLACA)
    elif f.parte == 'body' and f.camada == 2:
        cinto_saia(f, PANO2, faldas=True, saia_t=1.0, cor_cinto=ACAB)
        if not (f.cima or f.baixo):
            f.dentes(0.9, 0.1, 0.16)
    elif f.parte == 'rleg' and f.camada == 2:
        if _tampa(f, ESCURO):
            return
        P.ret(PLACA)
        linha = np.floor(f.T * 6)
        x = f.S * 3 + (linha % 2) * 0.5
        P.pinta(((x % 1) - 0.5) ** 2 * 0.5 + (f.T * 6 % 1) > 0.82, ESCURO, nova_placa=False)


def t_titas(f, var):
    P = f
    if f.parte == 'body' and f.camada == 1:
        if _tampa(f):
            return
        P.ret(PLACA, 0, 0.5, 0, 0.5)
        P.ret(PLACA, 0.5, 1, 0, 0.5)
        P.ret(PLACA, 0, 0.5, 0.5, 0.8)
        P.ret(PLACA, 0.5, 1, 0.5, 0.8)
        P.ret(ESCURO, 0, 1, 0.8, 1)
        for sx in (0.12, 0.88):                  # rebites
            for ty in (0.1, 0.4, 0.6):
                P.pinta(np.hypot((f.S - sx) * f.W, (f.T - ty) * f.H) < 1.6, ACAB, nova_placa=False)
    elif f.parte == 'rarm' and f.camada == 1:
        if _tampa(f):
            return
        P.ret(PLACA, 0, 1, 0, 0.42)             # ombreira quadrada grossa
        P.ret(ACAB, 0, 1, 0.42, 0.48)
        P.ret(ESCURO, 0, 1, 0.48, 0.62)
        P.ret(PLACA, 0, 1, 0.62, 1)
        P.pinta(np.hypot((f.S - 0.5) * f.W, (f.T - 0.2) * f.H) < 1.6, ACAB, nova_placa=False)
    elif f.parte == 'rleg' and f.camada == 1:
        botas(f, 0.48, PLACA, cano=ACAB)
    elif f.parte == 'body' and f.camada == 2:
        if f.cima:
            return
        if f.baixo:
            f.ret(ESCURO)
            return
        f.pinta(f.T < 0.5, VAZIO, nova_placa=False)
        f.ret(COURO, 0, 1, 0.5, 0.78)             # cinto largo
        if f.frente:
            f.ret(ACAB, 0.36, 0.64, 0.48, 0.8)     # fivela
            f.ret(ESCURO, 0.44, 0.56, 0.56, 0.72)
        f.ret(PLACA, 0, 1, 0.78, 1)
    elif f.parte == 'rleg' and f.camada == 2:
        if _tampa(f, ESCURO):
            return
        P.ret(PLACA, 0, 1, 0, 0.5)
        P.ret(ACAB, 0, 1, 0.5, 0.56)
        P.ret(PLACA, 0, 1, 0.56, 1)


def t_caos(f, var):
    P = f
    if f.parte == 'body' and f.camada == 1:
        if _tampa(f):
            return
        corte = f.S + (f.T - 0.5) * 0.7 + (V3.hash2(np.floor(f.T * 12).astype(int), 0 * f.T.astype(int), 5) - 0.5) * 0.12
        if f.costas:
            corte = 1 - corte
        P.ret(PLACA)
        P.pinta(corte > 0.62, ESCURO, nova_placa=False)     # metade vazio
        P.pinta((corte > 0.55) & (corte <= 0.62), BRILHO, nova_placa=False)
        P.laminas(PLACA, 0, 0.3, 2, 0, 0.5)
    elif f.parte == 'rarm' and f.camada == 1:
        if _tampa(f):
            return
        P.ret(PLACA, 0, 1, 0, 0.34)
        P.dentes(0.3, 0.1, 0.5, tipo=ESCURO)
        P.ret(ESCURO, 0, 1, 0.34, 1)
        P.pinta((np.abs(f.S - f.T) < 0.05) & (f.T > 0.34), BRILHO, nova_placa=False)
    elif f.parte == 'rleg' and f.camada == 1:
        botas(f, 0.56, PLACA)
    elif f.parte == 'body' and f.camada == 2:
        cinto_saia(f, ESCURO, faldas=True, saia_t=1.0, cor_cinto=PLACA)
        if not (f.cima or f.baixo):
            f.franja(0.84, 0.16, 53)
    elif f.parte == 'rleg' and f.camada == 2:
        if _tampa(f, ESCURO):
            return
        P.ret(ESCURO)
        P.ret(PLACA, 0, 1, 0, 0.3)
        P.pinta((np.abs(f.S - (1 - f.T)) < 0.06), BRILHO, nova_placa=False)


# ================================================================== elmos (camada 1 da cabeca)

def e_cinzas(f):          # mascara de forja: grade horizontal
    if f.cima:
        f.ret(PLACA); f.ret(ACAB, 0, 1, 0.4, 0.6); return
    f.ret(PLACA)
    if f.frente:
        f.ret(METAL2, 0.12, 0.88, 0.34, 0.82)
        for k in range(4):
            a = 0.38 + k * 0.11
            f.pinta((f.T > a) & (f.T < a + 0.05) & (np.abs(f.S - 0.5) < 0.34), BRILHO, nova_placa=False)
    if f.lado:
        f.ret(ACAB, 0.3, 0.42, 0.2, 0.8)


def e_catacumba(f):       # capuz fundo: rosto escuro e dois olhos acesos
    f.ret(PANO)
    if f.frente:
        f.pinta((np.abs(f.S - 0.5) < 0.36) & (f.T > 0.22), ESCURO, nova_placa=False)
        for sx in (0.36, 0.64):
            f.pinta((np.abs(f.S - sx) < 0.06) & (np.abs(f.T - 0.5) < 0.05), BRILHO, nova_placa=False)
        f.ret(OSSO, 0.2, 0.8, 0.16, 0.22)
    if f.costas:
        f.franja(0.9, 0.1, 71)


def e_pinaculo(f):        # fenda vertical e aletas
    f.ret(PLACA)
    if f.cima:
        f.ret(ACAB, 0.45, 0.55, 0, 1); return
    if f.frente:
        f.ret(ESCURO, 0.44, 0.56, 0.18, 0.9)
        f.ret(BRILHO, 0.47, 0.53, 0.3, 0.6)
        f.dentes(0.95, 0.05, 0.2)
    if f.lado:
        f.pinta(np.abs(f.S - 0.5) < (1 - f.T) * 0.3, ACAB, nova_placa=False)


def e_rubra(f):           # mascara so nos olhos; resto pano do casaco
    f.ret(PANO2)
    if f.cima:
        return
    if f.frente:
        f.ret(PLACA, 0.06, 0.94, 0.3, 0.52)
        for sx in (0.32, 0.68):
            f.pinta((np.abs(f.S - sx) < 0.1) & (np.abs(f.T - 0.41) < 0.04), BRILHO, nova_placa=False)
        f.pinta((f.T > 0.52) & (np.abs(f.S - 0.5) < 0.3), VAZIO, nova_placa=False)   # rosto aberto em baixo
    if f.lado:
        f.ret(PLACA, 0, 1, 0.3, 0.52)


def e_eco(f):             # grade redonda (alto-falante)
    f.ret(PLACA)
    if f.frente:
        r = np.hypot((f.S - 0.5) * f.W, (f.T - 0.55) * f.H)
        f.pinta(r < 11, METAL2, nova_placa=False)
        f.pinta((r < 11) & (((f.S * f.W) % 4) < 1.2), ESCURO, nova_placa=False)
        f.pinta(r < 3, BRILHO, nova_placa=False)
    if f.lado:
        r = np.hypot((f.S - 0.5) * f.W, (f.T - 0.5) * f.H)
        f.pinta(r < 7, ACAB, nova_placa=False)
        f.pinta(r < 4, BRILHO, nova_placa=False)


def e_putrido(f):         # mascara de gas com dois filtros
    f.ret(COURO)
    if f.cima:
        f.ret(PELO, 0, 1, 0, 0.5); return
    if f.frente:
        for sx in (0.3, 0.7):
            f.pinta(np.hypot((f.S - sx) * f.W, (f.T - 0.4) * f.H) < 5, VIDRO, nova_placa=False)
            f.pinta(np.hypot((f.S - sx) * f.W, (f.T - 0.4) * f.H) < 3, BRILHO, nova_placa=False)
        f.pinta(np.hypot((f.S - 0.5) * f.W, (f.T - 0.78) * f.H) < 5, PLACA)
        f.pinta(np.hypot((f.S - 0.5) * f.W, (f.T - 0.78) * f.H) < 2.4, ESCURO, nova_placa=False)


def e_glaciar(f):         # coroa de gelo no topo e viseira em losango
    f.ret(PLACA)
    if f.frente:
        d = np.abs(f.S - 0.5) * 1.6 + np.abs(f.T - 0.48) * 2.4
        f.pinta(d < 0.5, ESCURO, nova_placa=False)
        f.pinta(d < 0.26, BRILHO, nova_placa=False)
        f.dentes(0.12, 0.12, 0.2, para_baixo=False, tipo=ACAB)
    if f.lado or f.costas:
        f.dentes(0.12, 0.1, 0.25, para_baixo=False, tipo=ACAB)


def e_abissal(f):         # escafandro: janela de vidro redonda e rebites
    f.ret(METAL2)
    if f.frente:
        r = np.hypot((f.S - 0.5) * f.W, (f.T - 0.5) * f.H)
        f.pinta(r < 12, ACAB)
        f.pinta(r < 10, VIDRO, nova_placa=False)
        f.pinta((r < 10) & (f.T < 0.42) & (f.S < 0.45), BRILHO, nova_placa=False)
    if f.lado:
        f.pinta(np.hypot((f.S - 0.5) * f.W, (f.T - 0.5) * f.H) < 6, ACAB)


def e_titas(f):           # balde com grade em cruz
    f.ret(PLACA)
    if f.frente:
        f.ret(ESCURO, 0.1, 0.9, 0.36, 0.5)
        f.ret(ESCURO, 0.44, 0.56, 0.36, 0.9)
        f.ret(BRILHO, 0.16, 0.38, 0.4, 0.46)
        f.ret(BRILHO, 0.62, 0.84, 0.4, 0.46)
        for sx in (0.12, 0.88):
            f.pinta(np.hypot((f.S - sx) * f.W, (f.T - 0.8) * f.H) < 1.6, ACAB, nova_placa=False)
    if f.cima:
        f.ret(ACAB, 0, 1, 0.45, 0.55)


def e_caos(f):            # um olho so, rachado
    f.ret(PLACA)
    if f.frente:
        corte = f.S + (f.T - 0.5) * 0.7
        f.pinta(corte > 0.66, ESCURO, nova_placa=False)
        f.pinta(np.hypot((f.S - 0.36) * f.W, (f.T - 0.46) * f.H) < 4.5, ESCURO, nova_placa=False)
        f.pinta(np.hypot((f.S - 0.36) * f.W, (f.T - 0.46) * f.H) < 2.6, BRILHO, nova_placa=False)
        f.pinta((corte > 0.6) & (corte <= 0.66), BRILHO, nova_placa=False)


CORPO = {'CINZAS': t_cinzas, 'CATACUMBA': t_catacumba, 'PINACULO': t_pinaculo, 'RUBRA': t_rubra, 'ECO': t_eco,
         'PUTRIDO': t_putrido, 'GLACIAR': t_glaciar, 'ABISSAL': t_abissal, 'TITAS': t_titas, 'CAOS': t_caos}
ELMO = {'CINZAS': e_cinzas, 'CATACUMBA': e_catacumba, 'PINACULO': e_pinaculo, 'RUBRA': e_rubra, 'ECO': e_eco,
        'PUTRIDO': e_putrido, 'GLACIAR': e_glaciar, 'ABISSAL': e_abissal, 'TITAS': e_titas, 'CAOS': e_caos}


def elmo(id_, f):
    if f.baixo:
        return
    ELMO[id_](f)


# ================================================================== nucleo do peito: forma por masmorra

def nucleo(id_, f):
    """Mascara (gema, aro) do nucleo no peito, em texels."""
    dx, dy = (f.S - 0.5) * f.W, (f.T - 0.3) * f.H
    if id_ == 'CINZAS':                          # brasa redonda, deslocada para o lado da faixa
        dx = (f.S - 0.32) * f.W
        r = np.hypot(dx, dy)
        return r < 4.5, (r >= 4.5) & (r < 6.5)
    if id_ == 'CATACUMBA':                       # relicario (hexagono alto)
        d = np.maximum(np.abs(dx) * 1.2, np.abs(dy) * 0.7 + np.abs(dx) * 0.5)
        return d < 4.0, (d >= 4.0) & (d < 5.6)
    if id_ == 'PINACULO':                        # triangulo para baixo
        d = np.maximum(-dy - 4, np.abs(dx) * 1.0 + dy * 0.6 - 2.4)
        return d < 0, (d >= 0) & (d < 1.6)
    if id_ == 'RUBRA':                           # gota
        r = np.hypot(dx, (dy - 1) * 1.0) - np.clip(-dy, 0, 8) * 0.35
        return r < 3.2, (r >= 3.2) & (r < 4.6)
    if id_ == 'ECO':
        return np.zeros_like(dx, bool), np.zeros_like(dx, bool)   # ja tem o alto-falante
    if id_ == 'PUTRIDO':                         # cacho de 3 esporos
        g = np.zeros_like(dx, bool)
        for cx, cy in ((-2.6, 1.5), (2.6, 1.5), (0, -2.2)):
            g |= np.hypot(dx - cx, dy - cy) < 2.4
        return g, np.zeros_like(g)
    if id_ == 'GLACIAR':                         # floco: cruz + X
        g = ((np.abs(dx) < 1.0) & (np.abs(dy) < 6)) | ((np.abs(dy) < 1.0) & (np.abs(dx) < 6)) | \
            ((np.abs(np.abs(dx) - np.abs(dy)) < 0.9) & (np.abs(dx) < 4))
        return g, np.zeros_like(g)
    if id_ == 'ABISSAL':                         # perola com concha
        r = np.hypot(dx, dy)
        return r < 3.4, (r >= 3.4) & (r < 5.4) & (dy > -1)
    if id_ == 'TITAS':                           # reator quadrado
        d = np.maximum(np.abs(dx), np.abs(dy))
        return d < 3.5, (d >= 3.5) & (d < 5.5)
    # CAOS: fenda em diagonal
    d = np.abs(dx - dy * 0.6)
    return (d < 1.2) & (np.abs(dy) < 7), (d >= 1.2) & (d < 2.2) & (np.abs(dy) < 7)
