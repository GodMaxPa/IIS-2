# Стендовый мир-эталон ИИС 2 (D74) для Windows.
# Сервер разработки создаёт плоский мир run\iis_stand, строит в нём стенд (StandBuilder), даёт машинам 10 с поработать,
# сохраняет мир и выключается. Затем мир копируется в сохранения клиента как «IIS2_Stand» (прежний — в iis-backups).
#   powershell -ExecutionPolicy Bypass -File scripts\stand.ps1
param(
    [string]$Client = "$env:APPDATA\forServerClient",
    [string]$Jdk = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
Set-Location $root
$env:JAVA_HOME = $Jdk
$props = Join-Path $root 'run\server.properties'
$backup = "$props.iisbak"
$world = Join-Path $root 'run\iis_stand'
$log = Join-Path $root 'build\stand.log'
New-Item -ItemType Directory -Force (Join-Path $root 'build') | Out-Null

# server.properties: плоский мир iis_stand, творческий режим; исходный файл восстанавливается в конце
Copy-Item $props $backup -Force
$lines = Get-Content $props | Where-Object { $_ -notmatch '^(level-name|level-type|gamemode|generator-settings|spawn-protection|generate-structures)=' }
$lines += 'level-name=iis_stand', 'level-type=minecraft\:flat', 'gamemode=creative', 'generator-settings={}', 'spawn-protection=0', 'generate-structures=false'
Set-Content -Path $props -Value $lines -Encoding ascii
if (Test-Path $world) { Remove-Item -Recurse -Force $world }
try {
    cmd /c ".\gradlew.bat runStand --console=plain > `"$log`" 2>&1"
    $code = $LASTEXITCODE
} finally {
    Copy-Item $backup $props -Force
    Remove-Item $backup -Force
}
if (-not (Test-Path (Join-Path $world 'level.dat'))) { Write-Host "stand failed (exit $code), see $log"; exit 1 }

$saves = Join-Path $Client 'saves'
$target = Join-Path $saves 'IIS2_Stand'
if (Test-Path $target) {
    $bk = Join-Path $Client ("iis-backups\IIS2_Stand." + (Get-Date -Format 'yyyyMMdd-HHmm'))
    New-Item -ItemType Directory -Force (Split-Path $bk) | Out-Null
    Move-Item $target $bk
}
Copy-Item -Recurse $world $target
Write-Host "stand world copied to $target"
