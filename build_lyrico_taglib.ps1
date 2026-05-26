$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$ModuleRoot = Join-Path $RepoRoot "lyrico-audiotag"
$OutputDir = Join-Path $ModuleRoot "src\main\jniLibs\arm64-v8a"
$BuiltSo = Join-Path $ModuleRoot "build\intermediates\stripped_native_libs\release\stripReleaseDebugSymbols\out\lib\arm64-v8a\liblyrico_taglib.so"

Write-Host "=== Building lyrico-audiotag native library ==="
Write-Host "Only arm64-v8a is packaged by default."

Push-Location $RepoRoot
try {
    .\gradlew.bat :lyrico-audiotag:assembleRelease -PellaBuildNative=true -PellaAbi=arm64-v8a
    if (-not (Test-Path $BuiltSo)) {
        throw "Built library not found: $BuiltSo"
    }
    New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
    Copy-Item -LiteralPath $BuiltSo -Destination (Join-Path $OutputDir "liblyrico_taglib.so") -Force
    Write-Host "Copied prebuilt library to $OutputDir"
} finally {
    Pop-Location
}
