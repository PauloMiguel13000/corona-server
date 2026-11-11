# Prepara a VPS para receber o Corona Server. Roda uma vez, como Administrador.
# Nao instala nada de terceiros: so cria pastas, confere o Java e ajusta o que e do Windows.
$ErrorActionPreference = 'Stop'
$Raiz = 'C:\corona'
$Serv = Join-Path $Raiz 'minecraft-server'
$Back = Join-Path $Raiz 'backup'

Write-Host '== conferindo quem sou eu'
Write-Host ('computador: {0}' -f $env:COMPUTERNAME)

Write-Host '== criando as pastas'
foreach ($p in @($Raiz, $Serv, $Back, (Join-Path $Back 'mundos'), (Join-Path $Serv 'otimizacao'))) {
    New-Item -ItemType Directory -Force -Path $p | Out-Null
}

Write-Host '== conferindo o Java'
$java = Get-Command java -ErrorAction SilentlyContinue
if ($java) {
    & java -version 2>&1 | ForEach-Object { Write-Host ('  ' + $_) }
} else {
    Write-Host '  Java NAO encontrado. O servidor traz o proprio Java na pasta runtime\java,'
    Write-Host '  entao isso so e um problema se a pasta runtime nao vier junto.'
}

Write-Host '== energia: nunca desligar nem hibernar'
& powercfg /change standby-timeout-ac 0
& powercfg /change hibernate-timeout-ac 0
& powercfg /change monitor-timeout-ac 0

Write-Host '== firewall: liberando as portas do Minecraft'
foreach ($r in @(@{n='Corona Minecraft Java'; p=25565; t='TCP'}, @{n='Corona Minecraft Bedrock'; p=19132; t='UDP'})) {
    if (-not (Get-NetFirewallRule -DisplayName $r.n -ErrorAction SilentlyContinue)) {
        New-NetFirewallRule -DisplayName $r.n -Direction Inbound -Action Allow -Protocol $r.t -LocalPort $r.p | Out-Null
        Write-Host ('  liberada {0} {1}' -f $r.t, $r.p)
    } else {
        Write-Host ('  ja existia: {0}' -f $r.n)
    }
}

Write-Host ''
Write-Host 'Pronto. Agora copie a pasta do servidor para' $Serv
Write-Host 'e depois rode o 2-LIGAR-NO-BOOT.ps1.'
