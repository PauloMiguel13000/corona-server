# Backup diario do mundo na VPS. Mesma logica que roda no PC de casa.
# Agenda: Register-ScheduledTask abaixo (roda uma vez).
$ErrorActionPreference = 'Stop'
$ps = 'C:\corona\minecraft-server\otimizacao\backup-mundo.ps1'
if (-not (Test-Path $ps)) { throw "nao achei $ps - copie o backup-mundo.ps1 junto com o servidor" }

$acao = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument ('-NoProfile -ExecutionPolicy Bypass -File "{0}"' -f $ps)
$gat  = New-ScheduledTaskTrigger -Daily -At 05:40
$dono = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest
Register-ScheduledTask -TaskName 'Corona - backup do mundo' -Action $acao -Trigger $gat -Principal $dono -Force | Out-Null
Write-Host 'Backup agendado todo dia as 05:40, rodando mesmo sem ninguem logado.'
