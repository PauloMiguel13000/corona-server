# Trilhas e sons do Corona Server

Tudo aqui é **original**, sintetizado pelos scripts desta pasta — não há material de terceiros.

- `gerar_trilhas.py` — as 8 trilhas por família de masmorra (cripta, mina, floresta, gelo, mar, fogo, vazio, arcano).
  Rodar: `python3 gerar_trilhas.py` (gera todas) ou `python3 gerar_trilhas.py cripta mina` (só essas).
- `gerar_masmorras.py` — as 3 faixas gerais: `explorar`, `combate` e `chefe`.
- `trilhas/` — os `.ogg` prontos (mono, 32 kHz, Vorbis q1–q2), que entram no pacote de recursos.

Precisa de `numpy`, `scipy` e `ffmpeg` com `libvorbis`.

As faixas fecham em loop sem emenda: o gerador dobra a cauda por cima do começo (`emendar`).
