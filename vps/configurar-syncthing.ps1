# Configura o Syncthing deste PC: adiciona a VPS como dispositivo e compartilha a pasta
# backup-corona-server (so envio). Para o Syncthing, mexe no config.xml e liga de novo.
$O = 'C:\Users\server1\Documents\minecraft-server\otimizacao'
$H = Join-Path $O 'syncthing-home'
$C = Join-Path $H 'config.xml'
$L = Join-Path $O 'syncthing-config.log'
function Log([string]$m) { Add-Content -Path $L -Value ("{0} {1}" -f (Get-Date -Format 'dd/MM HH:mm:ss'), $m) }
$VPS = 'AHCXR5B-L2H7DEN-YYIZIHM-V2ICJZY-RCEBHLF-X57K352-RO75WWO-AEDYKAS'
$PASTA = 'C:\Users\server1\Documents\backup-corona-server'
Log '==== inicio'
try {
  Get-Process -Name syncthing -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
  Start-Sleep -Seconds 4
  if (-not (Test-Path $C)) { throw 'config.xml nao existe - ligue o Syncthing uma vez antes' }
  Copy-Item -Force $C ($C + '.antes')
  [xml]$x = Get-Content -Path $C -Encoding UTF8
  $eu = ($x.configuration.device | Select-Object -First 1).id
  if ($x.configuration.device.id -notcontains $VPS) {
    $d = $x.CreateElement('device')
    $d.SetAttribute('id', $VPS); $d.SetAttribute('name', 'EH-S25-25280')
    $d.SetAttribute('compression', 'metadata'); $d.SetAttribute('introducer', 'false')
    $d.SetAttribute('skipIntroductionRemovals', 'false'); $d.SetAttribute('autoAcceptFolders', 'false')
    $d.SetAttribute('maxSendKbps', '0'); $d.SetAttribute('maxRecvKbps', '0')
    $d.SetAttribute('paused', 'false'); $d.SetAttribute('untrusted', 'false')
    $a = $x.CreateElement('address'); $a.InnerText = 'dynamic'; $d.AppendChild($a) | Out-Null
    $x.configuration.AppendChild($d) | Out-Null
    Log 'adicionei a VPS como dispositivo'
  } else { Log 'a VPS ja estava na lista' }
  if ($x.configuration.folder.id -notcontains 'corona-migracao') {
    $f = $x.CreateElement('folder')
    $f.SetAttribute('id', 'corona-migracao'); $f.SetAttribute('label', 'Corona - migracao')
    $f.SetAttribute('path', $PASTA); $f.SetAttribute('type', 'sendonly')
    $f.SetAttribute('rescanIntervalS', '3600'); $f.SetAttribute('fsWatcherEnabled', 'true')
    $f.SetAttribute('fsWatcherDelayS', '10'); $f.SetAttribute('ignorePerms', 'false')
    $f.SetAttribute('autoNormalize', 'true')
    foreach ($id in @($eu, $VPS)) { $e = $x.CreateElement('device'); $e.SetAttribute('id', $id); $e.SetAttribute('introducedBy', ''); $f.AppendChild($e) | Out-Null }
    $m = $x.CreateElement('minDiskFree'); $m.SetAttribute('unit', '%'); $m.InnerText = '1'; $f.AppendChild($m) | Out-Null
    $x.configuration.AppendChild($f) | Out-Null
    Log 'compartilhei a pasta backup-corona-server (so envio)'
  } else { Log 'a pasta ja estava compartilhada' }
  $x.Save($C)
  Log 'config.xml salvo'
} catch {
  Log ('FALHOU: ' + $_.Exception.Message)
}
Start-Process -FilePath (Join-Path $O 'syncthing.exe') -ArgumentList @('serve', ('--home=' + $H), '--no-browser') -WindowStyle Minimized
Log 'Syncthing ligado de novo'
Log '==== fim'
