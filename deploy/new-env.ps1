# Génère deploy/.env avec des secrets aléatoires forts (fichier local, jamais versionné).
# Usage : powershell -ExecutionPolicy Bypass -File deploy\new-env.ps1 -Domain tms2m.com -Email vous@exemple.com

param(
    [Parameter(Mandatory = $true)][string]$Domain,
    [Parameter(Mandatory = $true)][string]$Email
)

$envFile = Join-Path $PSScriptRoot ".env"
if (Test-Path $envFile) { throw "deploy\.env existe déjà : supprimez-le d'abord si vous voulez régénérer les secrets." }

function New-Secret([int]$bytes) {
    $b = New-Object byte[] $bytes
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
    return [Convert]::ToBase64String($b).Replace('+', 'A').Replace('/', 'B').TrimEnd('=')
}

@"
TMS_DOMAIN=$Domain
ACME_EMAIL=$Email
DB_PASSWORD=$(New-Secret 24)
TMS_ADMIN_USER=admin
TMS_ADMIN_PASSWORD=$(New-Secret 18)
TMS_ENROLLMENT_KEY=$(New-Secret 18)
TMS_AUTO_ACCEPT=true
TMS_POLL_INTERVAL=60
"@ | Out-File -Encoding ascii $envFile

Write-Host "deploy\.env créé avec des secrets aléatoires. Conservez-le précieusement (gestionnaire de mots de passe)."
