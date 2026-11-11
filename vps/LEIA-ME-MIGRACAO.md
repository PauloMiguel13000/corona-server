# Migração do Corona Server para a VPS

Ordem das coisas. O que é **tua parte** está marcado — o resto eu faço quando estiver vinculado na VPS.

## Antes de tudo (tua parte, segurança)
1. **Trocar a senha** do Administrador (a que veio por mensagem está queimada).
2. Criar um usuário com outro nome e **desativar o Administrador**.
3. Depois que a migração estiver de pé, **limitar o RDP** (porta 3389) ao teu IP — eu monto a regra.

## Passo 1 — preparar a VPS (eu)
`1-PREPARAR-VPS.ps1` — cria `C:\corona`, confere o Java, tira a VPS de suspensão
e libera as portas 25565 (Java/TCP) e 19132 (Bedrock/UDP) no firewall.

## Passo 2 — levar o servidor para lá (tua parte + minha)
A pasta inteira tem servidor + plugins + mundo. O mundo compactado dá **~1,5 GB**.
Pelo RDP dá para copiar e colar arquivo direto da tua máquina para a VPS (o RDP já leva a área
de transferência). Alternativa: jogar o zip no teu OneDrive e baixar lá.
Eu não consigo transferir isso sozinho porque não tenho as duas máquinas ligadas a mim ao mesmo tempo.

O que precisa ir:
- `minecraft-server\` inteira (sem `logs\`, sem `cache\`, sem `otimizacao\ajustes*`)
- o zip mais recente de `backup-corona-server\mundos\`, se for levar o mundo separado

## Passo 3 — ligar sozinho (eu)
`2-LIGAR-NO-BOOT.ps1` — o servidor passa a subir junto com a VPS, como SYSTEM, sem depender
de sessão RDP aberta. **Importante:** ao sair do RDP use "Desconectar", nunca "Encerrar sessão".

## Passo 4 — backup (eu)
`3-BACKUP-NA-VPS.ps1` — backup diário do mundo às 05:40 na própria VPS, guardando os 5 últimos.
Mais a cópia semanal para o PC de casa (sexta), que é o que protege de a VPS morrer.

## Passo 5 — virar a chave (nós dois)
- Avisar no Discord com antecedência.
- Deixar no endereço antigo do playit uma mensagem com o **IP novo**.
- Atualizar o site e o `/ip` do jogo.
- Ficar com **dois endereços**: um para Java (25565) e um para Bedrock (19132).

## Antes de decidir, o teste que vale
Medir o ping para o endereço do playit e para `79.182.4.169`.
A diferença é exatamente o que o túnel custa hoje. Se der 20–40 ms a menos, a mudança se paga só nisso.
