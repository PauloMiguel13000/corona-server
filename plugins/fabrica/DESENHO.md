# Fábrica Alegria — minigame original (estilo "fábrica de brinquedos abandonada")

Nada da franquia (nomes, personagens, sons, visuais). Mesmos OBJETIVOS do gênero:
explorar a fábrica abandonada, achar a ferramenta de agarrar à distância, ligar a energia com ela,
abrir caminhos, achar fitas com a história, fugir do monstro de pelúcia gigante na perseguição final.

## Personagens (originais)
- **Tamanduá Tutu** — mascote gigante de pelúcia (laranja e creme), braços e língua compridos, sorriso costurado
  com dentes de agulha. Fica parado no saguão (estátua) e some quando a energia volta. Persegue nos dutos no fim.
- **Dona Bolota** — boneca-bola de corda (pequena, rosa), aparece nas fitas e no fim como gancho do próximo capítulo.
- **Seu Arlindo** — o dono da fábrica (só nas fitas, voz em texto).

## Ferramenta: Luva de Gancho (mochila com duas mãos)
- Mão AZUL (clique esquerdo) e mão VERMELHA (clique direito): dispara uma mão que vai até 18 blocos.
  - Agarra **puxadores** (alavanca de parede) → puxa/abre.
  - Pega **cabo de energia** num **plugue** e leva até a **tomada** (enquanto segura, um fio de partículas liga você
    ao plugue; se andar mais que 14 blocos do plugue, o cabo solta).
  - Encosta em **botões de longe**.
  - Agarra **caixas** pequenas (puxa para você).
- A mão vermelha só é achada no meio do jogo (como no gênero): até lá só a azul.

## Fases (capítulo 1, ~15–20 min)
1. **Portaria** — porta trancada; código na fita 1 (sala da segurança). Teclado numérico (botões).
2. **Saguão** — Tutu gigante parado num pedestal. Porta da fábrica fechada sem energia.
3. **Sala da Luva** — pega a Luva (mão azul). Puzzle tutorial: puxador, botão longe.
4. **Energia do saguão** — cabo do plugue da parede até a tomada da porta (atravessando o saguão). Ao ligar, luzes
   piscam... e o pedestal do Tutu está VAZIO.
5. **Linha de produção** — esteira com caixas; agarrar a caixa certa (cor) e pôr na prensa; 3 cores.
   Achar a mão vermelha no armário dos funcionários. Fita 2 (história).
6. **Sala de controle** — dois cabos ao mesmo tempo (azul e vermelho) → liga a ponte rolante.
7. **Perseguição** — Tutu aparece no fim do corredor; corre pelos dutos (túnel 1×2 com curvas) atrás de você.
   Na saída, puxar a alavanca com a luva: uma caixa enorme cai e o Tutu despenca pelo vão. Fita 3 + gancho final.

## Regras do minigame
- 1 a 4 jogadores por instância (co-op); quem é pego vira espectador até o próximo checkpoint (ou usa moeda de reviver).
- Checkpoints nas fases 3, 5 e 7. Sem dano fora da perseguição.
- Recompensa: coronas por terminar; tempo recorde por jogador.
- Java: modelos 3D (luva na mão, mãos voando, Tutu) pelo pacote de recursos. Bedrock: mesmos modelos pelo pacote v2
  (attachables + GeyserDisplayEntity) e Tutu como Mannequin com skin + escala.
