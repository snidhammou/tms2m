# Construit les APK de production de TMS2M Agent (une variante par marque) pour une nouvelle version.
#
# Les APK sont signés S2M (android-agent/keystore.properties), pointent sur le serveur de production
# et embarquent la clé d'enrôlement lue dans deploy/.env. Ils sont copiés dans android-agent/dist.
#
# Mise à jour du parc : publier les APK dans la console (Applications > Publier un APK). Chaque terminal
# reçoit automatiquement la version de SA variante au heartbeat suivant (TMS_AGENT_AUTO_UPDATE=true).
#
# Usage : powershell -ExecutionPolicy Bypass -File tools\build-agent-release.ps1 -Version 1.0.2

param(
    [Parameter(Mandatory = $true)][ValidatePattern('^\d{1,2}\.\d{1,2}\.\d{1,2}$')][string]$Version,
    [string]$ServerUrl = "https://tms2m.com"
)

$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$envFile = Join-Path $root "deploy\.env"
if (-not (Test-Path $envFile)) { throw "deploy\.env manquant : clé d'enrôlement de production introuvable" }
$key = ((Get-Content $envFile | Where-Object { $_ -like 'TMS_ENROLLMENT_KEY=*' }) -replace '^TMS_ENROLLMENT_KEY=', '').Trim()
if (-not $key) { throw "TMS_ENROLLMENT_KEY absent de deploy\.env" }

$agent = Join-Path $root "android-agent"
# Contournement Windows : Gradle ne peut pas créer ses sockets locales dans le TEMP par défaut
$env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=$env:USERPROFILE\.gradle"

Push-Location $agent
try {
    $ErrorActionPreference = "Continue"
    & .\gradlew.bat :app:assembleRelease "-PagentVersion=$Version" "-PtmsServerUrl=$ServerUrl" "-PtmsEnrollmentKey=$key" --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Build en échec (code $LASTEXITCODE)" }
    $ErrorActionPreference = "Stop"

    $dist = Join-Path $agent "dist"
    New-Item -ItemType Directory -Force $dist | Out-Null
    foreach ($flavor in 'universal', 'newland', 'pax', 'sunmi') {
        $apk = Join-Path $agent "app\build\outputs\apk\$flavor\release\app-$flavor-release.apk"
        $out = Join-Path $dist "TMS2M-Agent-$flavor-$Version-prod.apk"
        Copy-Item $apk $out -Force
        Write-Host ("{0,-10} {1}  ({2:N0} Ko)" -f $flavor, $out, ((Get-Item $out).Length / 1KB))
    }
    Write-Host "`nÀ publier dans la console : Applications > Publier un APK (les 4 fichiers)."
} finally {
    Pop-Location
}
