param(
    [Parameter(Position = 0, ValueFromRemainingArguments = $true)]
    [string[]] $AppArguments
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$apiJar = Join-Path $projectRoot 'campus-api/target/campus-api-0.1.0-SNAPSHOT.jar'
$coreJar = Join-Path $projectRoot 'campus-core/target/campus-core-0.1.0-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $apiJar) -or -not (Test-Path -LiteralPath $coreJar)) {
    throw 'Missing core/API JARs. Run mvn clean verify first.'
}
if (-not $AppArguments) { $AppArguments = @('list') }
$classPath = $apiJar + [IO.Path]::PathSeparator + $coreJar
$previousEncoding = [Console]::OutputEncoding
try {
    [Console]::OutputEncoding = [Text.UTF8Encoding]::new()
    & java '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -cp $classPath com.opencampus.core.Main (Join-Path $projectRoot 'plugins.properties') @AppArguments
} finally {
    [Console]::OutputEncoding = $previousEncoding
}
exit $LASTEXITCODE
