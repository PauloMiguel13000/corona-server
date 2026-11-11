# Empacota o servidor inteiro num unico .zip para levar para a VPS (sem copia intermediaria).
$S = 'C:\Users\server1\Documents\minecraft-server'
$Pai = 'C:\Users\server1\Documents'
$D = 'C:\Users\server1\Documents\backup-corona-server'
$L = Join-Path $D 'migracao-vps.log'
function Log([string]$m) { Add-Content -Path $L -Value ("{0} {1}" -f (Get-Date -Format 'dd/MM HH:mm:ss'), $m) }
New-Item -ItemType Directory -Force -Path $D | Out-Null
Log '==== inicio'
$carimbo = Get-Date -Format 'yyyy-MM-dd-HHmm'
$saida = Join-Path $D ('migracao-vps-' + $carimbo + '.zip')
function JavaServidor { Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object { $_.Path -like '*\minecraft-server\runtime\*' } }
$ligado = [bool](JavaServidor)
if ($ligado) {
  Log 'servidor ligado: mandando salvar o mundo e pausando o salvamento'
  & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $S 'otimizacao\comando.ps1') 'save-off'
  & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $S 'otimizacao\comando.ps1') 'save-all flush'
  Start-Sleep -Seconds 12
}
try {
  $fora = @('minecraft-server/logs','minecraft-server/cache','minecraft-server/crash-reports',
            'minecraft-server/otimizacao/ajustes*','minecraft-server/otimizacao/antes-*',
            'minecraft-server/otimizacao/apagados','*/session.lock','*.log')
  $p = @('-a','-c','-f',$saida,'-C',$Pai)
  foreach ($f in $fora) { $p += ('--exclude=' + $f) }
  $p += 'minecraft-server'
  Log ('compactando (pode demorar varios minutos)...')
  $r = & tar.exe @p 2>&1
  $rc = $LASTEXITCODE
  if (-not (Test-Path $saida)) { throw ('tar devolveu ' + $rc + ' e nao gerou arquivo: ' + (($r | Select-Object -First 5) -join ' | ')) }
  $zmb = [math]::Round((Get-Item $saida).Length / 1MB, 1)
  if ($rc -ne 0) { Log ('tar avisou (codigo ' + $rc + '): ' + (($r | Select-Object -First 3) -join ' | ')) }
  Log ("pronto: $saida ($zmb MB)")
} catch {
  Log ('FALHOU: ' + $_.Exception.Message)
} finally {
  if ($ligado) { & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $S 'otimizacao\comando.ps1') 'save-on' }
}
Log '==== fim'
