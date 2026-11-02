# Publica una versión nueva de la app del conductor como release de GitHub.
#   .\scripts\publicar-release.ps1 -Version 1.2.0 [-Notas "Qué cambia"] [-ErpUrl https://erp.actiontruck.net]
# Compila los APK firmados (la clave vive en este equipo: keystore.properties, fuera de git), crea la etiqueta
# v1.2.0, la sube y publica la release con los APK. Las apps instaladas la verán en «Buscar actualizaciones».
param(
  [Parameter(Mandatory = $true)][string]$Version,
  [string]$Notas = "",
  [string]$ErpUrl = ""
)
$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)

if ($Version -notmatch '^\d+\.\d+\.\d+$') { throw "Versión no válida: '$Version' (se espera X.Y.Z, p. ej. 1.2.0)" }
$etiqueta = "v$Version"
if (-not (Test-Path keystore.properties)) { throw "Falta keystore.properties: sin la clave de firma no se pueden publicar actualizaciones" }
if (git status --porcelain) { throw "Hay cambios sin guardar en git: haga commit antes de publicar" }
if (git tag -l $etiqueta) { throw "La etiqueta $etiqueta ya existe" }

# La versión nueva tiene que ser mayor que la última publicada (si no, los móviles no la ofrecerían)
$ultima = (gh release list --limit 1 --json tagName --jq '.[0].tagName' 2>$null)
if ($ultima) {
  $a = [version]($ultima.TrimStart('v')); $b = [version]$Version
  if ($b -le $a) { throw "$etiqueta no es mayor que la última publicada ($ultima)" }
}

if (-not $env:JAVA_HOME) { $env:JAVA_HOME = "C:\Android\jdk17" }
$args = @("assembleRelease", "-PversionName=$Version", "--console=plain")
if ($ErpUrl) { $args += "-PerpUrl=$ErpUrl" }
& .\gradlew.bat @args
if ($LASTEXITCODE -ne 0) { throw "La compilación ha fallado" }

# Nombres con la versión y la arquitectura (la app elige el de su arquitectura o el universal)
$salida = "app\build\outputs\apk\release"
$publicar = New-Item -ItemType Directory -Force "app\build\publicar"
Get-ChildItem $publicar | Remove-Item -Force
foreach ($abi in "arm64-v8a", "armeabi-v7a", "x86_64", "universal") {
  $origen = Join-Path $salida "app-$abi-release.apk"
  if (-not (Test-Path $origen)) { throw "No se ha generado $origen" }
  Copy-Item $origen (Join-Path $publicar "ActionTruckConductor-$Version-$abi.apk")
}

git tag -a $etiqueta -m "App del conductor $etiqueta"
git push origin HEAD --follow-tags
if ($LASTEXITCODE -ne 0) { throw "No se pudo subir la etiqueta" }

$titulo = "App del conductor $etiqueta"
$apks = Get-ChildItem $publicar -Filter *.apk | ForEach-Object { $_.FullName }
if ($Notas) { gh release create $etiqueta @apks --title $titulo --notes $Notas }
else { gh release create $etiqueta @apks --title $titulo --generate-notes }
if ($LASTEXITCODE -ne 0) { throw "No se pudo crear la release" }
Write-Host "Publicada $etiqueta. Los móviles la verán en Jornada › Buscar actualizaciones."
