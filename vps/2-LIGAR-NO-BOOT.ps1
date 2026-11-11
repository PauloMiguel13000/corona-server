# Faz o servidor subir sozinho com a VPS, sem depender de ninguem estar logado por RDP.
# Roda uma vez, como Administrador, depois que a pasta do servidor ja estiver no lugar.
$ErrorActionPreference = 'Stop'
$Serv = 'C:\corona\minecraft-server'
$Bat  = Join-Path $Serv 'iniciar-servidor.bat'

if (-not (Test-Path $Bat)) { throw "nao achei $Bat - copie a pasta do servidor primeiro" }

$acao = New-ScheduledTaskAction -Execute $Bat -WorkingDirectory $Serv
$gat  = New-ScheduledTaskTrigger -AtStartup
$conf = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 2)
$dono = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest

Register-ScheduledTask -TaskName 'Corona - servidor de Minecraft' -Action $acao -Trigger $gat -Settings $conf -Principal $dono -Force | Out-Null
Write-Host 'Agendado: o servidor sobe sozinho quando a VPS liga.'
Write-Host 'Para ligar agora sem reiniciar:  Start-ScheduledTask -TaskName "Corona - servidor de Minecraft"'
Write-Host ''
Write-Host 'ATENCAO: rodando como SYSTEM o servidor nao tem janela de console visivel.'
Write-Host 'Os comandos continuam indo pela fila em otimizacao\fila (como ja funciona hoje).'
