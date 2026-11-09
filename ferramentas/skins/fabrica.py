"""Skins (64x64) do minigame Fabrica Alegria (personagens ORIGINAIS): Tamanduá Tutu (mascote gigante de pelucia,
laranja e creme, focinho comprido, sorriso costurado com dentes de agulha) e Dona Bolota (boneca de corda rosa).
Saida: skins/fabrica/<id>.png + previas em /tmp/claude-0/skins-fabrica/."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import animatronicos as A  # noqa: E402

FAB = {
    'tutu': dict(pelo='#e0782a', focinho='#f3dfb8', nariz='#1a0e08', orelhas='redondas', orelha_cor='#b8561a', bico=True,
                 peito='#f3dfb8', rasgo=True, pupila='#050505', manchas='#a84a14', gravata='#2a6fd0'),
    'bolota': dict(pelo='#f08cb4', focinho='#fbd3e2', nariz='#7a2040', orelhas='crista', orelha_cor='#d0507a', bico=False,
                   peito='#fbe6ef', rasgo=False, pupila='#3a0a20', botoes='#ffd84a', bochecha='#ff5a8a', colete='#7a3ad0'),
}

def sorriso_costurado(caminho):
    """Tutu: sorriso largo de orelha a orelha com dentes de agulha e pontos de costura nas bochechas (rosto = x 8..15,
    y 8..15 da skin)."""
    from PIL import Image
    im = Image.open(caminho).convert('RGBA')
    px = im.load()
    X, Y = 8, 8
    escuro, dente, linha = (22, 8, 8, 255), (236, 232, 220, 255), (90, 20, 20, 255)
    for c in range(0, 8):
        px[X + c, Y + 5] = escuro if 1 <= c <= 6 else px[X + c, Y + 5]
    for c in range(1, 7):
        px[X + c, Y + 6] = dente if c % 2 == 1 else escuro
    for c in range(2, 6):
        px[X + c, Y + 7] = escuro
    # costura nas pontas do sorriso
    px[X + 0, Y + 4] = linha
    px[X + 7, Y + 4] = linha
    px[X + 0, Y + 6] = linha
    px[X + 7, Y + 6] = linha
    # olhos: botao branco grande com pupila pequena e fixa
    for (c, l) in ((1, 2), (2, 2), (1, 3), (2, 3), (5, 2), (6, 2), (5, 3), (6, 3)):
        px[X + c, Y + l] = (244, 244, 240, 255)
    px[X + 2, Y + 3] = (5, 5, 5, 255)
    px[X + 5, Y + 3] = (5, 5, 5, 255)
    # camada de cima (focinho saliente): fica so a ponta do focinho (linha 4); o sorriso aparece por baixo
    HX = 40
    for c in range(8):
        for l in (5, 6, 7):
            px[HX + c, Y + l] = (0, 0, 0, 0)
    im.save(caminho)


if __name__ == '__main__':
    A.ANIM.clear()
    A.ANIM.update(FAB)
    saida = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'fabrica')
    A.gerar(saida, '/tmp/claude-0/skins-fabrica')
    sorriso_costurado(os.path.join(saida, 'tutu.png'))
