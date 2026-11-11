# Instala o Corona Server nesta maquina a partir do zip da migracao.
# NAO liga o servidor no fim: ligar os dois ao mesmo tempo briga pelo tunel do playit.
$ErrorActionPreference = 'Stop'
$Aqui = Split-Path -Parent $MyInvocation.MyCommand.Path
$L = Join-Path $Aqui 'instalar-na-vps.log'
function Log([string]$m) { $t = "{0} {1}" -f (Get-Date -Format 'dd/MM HH:mm:ss'), $m; Add-Content -Path $L -Value $t; Write-Host $t }
Log '==== inicio'
if ($env:COMPUTERNAME -eq 'DESKTOP-JLG1NKU') {
  Log 'ESTA E A MAQUINA ANTIGA (DESKTOP-JLG1NKU). Nada foi feito - rode isto na VPS.'
  Read-Host 'Enter para fechar'
  exit 2
}
Log ("maquina: " + $env:COMPUTERNAME)
$zip = Get-ChildItem -Path $Aqui -Filter 'migracao-vps-*.zip' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $zip) { Log 'nao achei nenhum migracao-vps-*.zip nesta pasta'; Read-Host 'Enter para fechar'; exit 1 }
Log ("zip: " + $zip.Name + " (" + [math]::Round($zip.Length/1MB,1) + " MB)")
$destino = 'C:\corona'
if (Test-Path (Join-Path $destino 'minecraft-server')) {
  Log ("ja existe " + $destino + "\minecraft-server - nao vou sobrescrever. Renomeie ou apague antes.")
  Read-Host 'Enter para fechar'; exit 1
}
New-Item -ItemType Directory -Force -Path $destino | Out-Null
Log ('descompactando em ' + $destino + ' (demora alguns minutos)...')
& tar.exe -x -f $zip.FullName -C $destino
if ($LASTEXITCODE -ne 0) { Log ('tar devolveu ' + $LASTEXITCODE); Read-Host 'Enter para fechar'; exit 1 }
$S = Join-Path $destino 'minecraft-server'
Log ('descompactado: ' + $S)
$java = Join-Path $S 'runtime\bin\java.exe'
if (Test-Path $java) {
  $v = (& $java -version 2>&1 | Select-Object -First 1)
  Log ('Java proprio do servidor encontrado: ' + $v)
} else {
  Log 'ATENCAO: nao achei runtime\bin\java.exe. Vai precisar instalar o Java 21 nesta maquina.'
}
foreach ($p in @('server.jar','plugins','world','iniciar-servidor.bat')) {
  if (Test-Path (Join-Path $S $p)) { Log ('ok: ' + $p) } else { Log ('FALTANDO: ' + $p) }
}
Log ''
Log 'PRONTO. O servidor NAO foi ligado de proposito.'
Log 'Antes de ligar aqui:'
Log '  1) desligue o servidor da maquina antiga (senao os dois brigam pelo tunel do playit)'
Log '  2) instale/ligue o playit nesta maquina com a mesma conta'
Log '  3) rode ' + (Join-Path $S 'iniciar-servidor.bat')
Log '==== fim'
Read-Host 'Enter para fechar'
