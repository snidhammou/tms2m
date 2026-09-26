# Déploie (ou met à jour) TMS2M sur le VPS : envoie les sources et relance les conteneurs.
#
# Prérequis : clé SSH ~/.ssh/tms2m_ed25519 autorisée sur le serveur, fichier deploy/.env rempli
# (voir .env.example, ou générer avec new-env.ps1).
#
# Usage : powershell -ExecutionPolicy Bypass -File deploy\deploy.ps1 -Server 1.2.3.4 [-User ubuntu]

param(
    [Parameter(Mandatory = $true)][string]$Server,
    [string]$User = "ubuntu",
    [string]$Key = "$env:USERPROFILE\.ssh\tms2m_ed25519"
)

$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$envFile = Join-Path $PSScriptRoot ".env"
if (-not (Test-Path $envFile)) { throw "deploy\.env manquant (lancer deploy\new-env.ps1)" }

$ssh = @("-i", $Key, "-o", "StrictHostKeyChecking=accept-new")
$target = "$User@$Server"

Write-Host "== Archive des sources (backend + deploy)"
$archive = Join-Path $env:TEMP "tms2m-deploy.tar.gz"
tar -czf $archive -C $root --exclude=backend/build --exclude=backend/.gradle --exclude=backend/data backend deploy

Write-Host "== Envoi vers $target"
scp @ssh $archive "${target}:/tmp/tms2m-deploy.tar.gz"

Write-Host "== Déploiement"
# sed : fichiers préparés sous Windows (CRLF) -> LF, sinon "\r" se colle aux valeurs du .env
ssh @ssh $target "sudo mkdir -p /opt/tms2m && sudo tar -xzf /tmp/tms2m-deploy.tar.gz -C /opt/tms2m && cd /opt/tms2m/deploy && sudo sed -i 's/\r$//' .env Caddyfile docker-compose.yml && sudo chmod 600 .env && sudo docker compose --env-file .env up -d --build && sudo docker compose ps"
Remove-Item $archive
Write-Host "Déploiement terminé."
