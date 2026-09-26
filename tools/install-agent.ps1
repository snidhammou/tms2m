# Installe l'agent TMS sur les terminaux branchés en USB du constructeur voulu,
# en choisissant la variante adaptée (newland = MESDK, pax = NeptuneLite, sunmi = PayLib, sinon universal).
# Un téléphone branché en même temps est ignoré.
#
# Usage : powershell -ExecutionPolicy Bypass -File tools\install-agent.ps1 [-Manufacturer newland] [-BuildType debug]

param(
    [string]$Manufacturer = "newland",
    [string]$BuildType = "debug"
)

$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = "adb" }

$flavor = switch ($Manufacturer.ToLower()) {
    "newland" { "newland" }
    "pax"     { "pax" }
    "sunmi"   { "sunmi" }
    default   { "universal" }
}
$apk = Join-Path $PSScriptRoot "..\android-agent\app\build\outputs\apk\$flavor\$BuildType\app-$flavor-$BuildType.apk"
if (-not (Test-Path $apk)) {
    Write-Error "APK introuvable : $apk (lancer d'abord : gradlew assemble$((Get-Culture).TextInfo.ToTitleCase($flavor))$((Get-Culture).TextInfo.ToTitleCase($BuildType)))"
    exit 1
}

$serials = & $adb devices | Select-String "\tdevice$" | ForEach-Object { ($_ -split "\t")[0] }
$targets = $serials | Where-Object { (& $adb -s $_ shell getprop ro.product.manufacturer).Trim() -ieq $Manufacturer }
if (-not $targets) {
    Write-Error "Aucun terminal '$Manufacturer' branché (appareils vus : $($serials -join ', '))"
    exit 1
}

foreach ($s in $targets) {
    $model = (& $adb -s $s shell getprop ro.product.model).Trim()
    Write-Host "[$s] $Manufacturer $model : installation de $(Split-Path $apk -Leaf)"
    & $adb -s $s install -r -g $apk
    & $adb -s $s shell am start -n com.tms.agent/.ui.MainActivity | Out-Null
}
