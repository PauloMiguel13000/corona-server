"""Diminui um resource pack (Java .zip ou Bedrock .mcpack) sem mudar o visual: cada PNG vira paleta de 256 cores
(sem pontilhado) quando isso fica menor e o alfa nao muda. Nos pacotes do Corona corta ~70-80% do tamanho.
Uso: python3 -I otimizar_png.py entrada.zip saida.zip
"""
import io
import sys
import zipfile
from PIL import Image, ImageChops


def otimizar(src, out):
    zi = zipfile.ZipFile(src)
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as zo:
        for i in zi.infolist():
            d = zi.read(i)
            if i.filename.endswith('.png'):
                try:
                    im = Image.open(io.BytesIO(d))
                    im.load()
                    rgba = im.convert('RGBA')
                    q = rgba.quantize(colors=256, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE)
                    o = io.BytesIO()
                    q.save(o, 'PNG', optimize=True)
                    alfa = ImageChops.difference(rgba.getchannel('A'), q.convert('RGBA').getchannel('A')).getextrema()[1]
                    if len(o.getvalue()) < len(d) and alfa <= 8:
                        d = o.getvalue()
                except Exception:
                    pass
            n = zipfile.ZipInfo(i.filename, (2026, 1, 1, 0, 0, 0))
            n.compress_type = zipfile.ZIP_DEFLATED
            zo.writestr(n, d)


if __name__ == '__main__':
    otimizar(sys.argv[1], sys.argv[2])
