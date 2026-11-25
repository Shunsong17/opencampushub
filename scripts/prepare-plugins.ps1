$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$pluginNames = @('course', 'library', 'canteen', 'activity')
foreach ($pluginName in $pluginNames) {
    $sourceJar = Join-Path $projectRoot "$pluginName-plugin/target/$pluginName-plugin-0.1.0-SNAPSHOT.jar"
    if (-not (Test-Path -LiteralPath $sourceJar)) {
        throw "Missing $sourceJar. Run mvn clean verify first."
    }
}
$pluginDirectory = Join-Path $projectRoot 'plugins'
New-Item -ItemType Directory -Force -Path $pluginDirectory | Out-Null
foreach ($pluginName in $pluginNames) {
    $sourceJar = Join-Path $projectRoot "$pluginName-plugin/target/$pluginName-plugin-0.1.0-SNAPSHOT.jar"
    Copy-Item -LiteralPath $sourceJar -Destination (Join-Path $pluginDirectory "$pluginName-plugin-0.1.0-SNAPSHOT.jar") -Force
}
Write-Output "Four plugin JARs installed in $pluginDirectory"
