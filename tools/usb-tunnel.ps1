# Tunnel USB de développement terminal -> PC (adb reverse), rétabli automatiquement.
#
# Les terminaux (ex. Newland N950S) réinitialisent parfois leur connexion USB, ce qui
# efface "adb reverse" : l'agent affiche alors "Serveur injoignable". Ce script le remet
# en place toutes les 3 secondes. Laisser tourner pendant les tests, Ctrl+C pour arrêter.
#
# Seuls les appareils du constructeur ciblé sont concernés (un téléphone branché en même
# temps est ignoré). -Serial permet de viser un appareil précis.
#
# Usage : powershell -ExecutionPolicy Bypass -File tools\usb-tunnel.ps1 [-Port 8095] [-Manufacturer newland] [-Serial XXXX]

param(
    [int]$Port = 8095,
    [string]$Manufacturer = "newland",
    [string]$Serial = ""
)

$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = "adb" }

function Get-Targets {
    $serials = & $adb devices | Select-String "\tdevice$" | ForEach-Object { ($_ -split "\t")[0] }
    if ($Serial) { return $serials | Where-Object { $_ -eq $Serial } }
    return $serials | Where-Object {
        (& $adb -s $_ shell getprop ro.product.manufacturer).Trim() -ieq $Manufacturer
    }
}

Write-Host "Tunnel USB tcp:$Port -> PC:$Port pour les terminaux '$Manufacturer' (Ctrl+C pour arrêter)"
$known = @{}
while ($true) {
    $targets = @(Get-Targets)
    foreach ($s in $targets) {
        if (-not ((& $adb -s $s reverse --list) -match "tcp:$Port")) {
            & $adb -s $s reverse "tcp:$Port" "tcp:$Port" | Out-Null
            Write-Host "$(Get-Date -Format T)  [$s] tunnel (re)établi"
        }
        $known[$s] = $true
    }
    foreach ($s in @($known.Keys)) {
        if ($targets -notcontains $s) {
            Write-Host "$(Get-Date -Format T)  [$s] déconnecté, en attente..."
            $known.Remove($s)
        }
    }
    Start-Sleep -Seconds 3
}
