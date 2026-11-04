# Checks Java source-file sizes and runs the Maven regression tests.
# Testing only; not required by the application.

param([string]$MavenCommand = 'mvn', [string[]]$MavenArguments = @())

$ErrorActionPreference = 'Stop'
Push-Location -LiteralPath $PSScriptRoot
try {
    $sourceFolders = @('src/main/java', 'src/test/java')
    $sourceFiles = @(Get-ChildItem -LiteralPath $sourceFolders -Filter '*.java' -Recurse)
    $overLimit = @($sourceFiles | Where-Object { [System.IO.File]::ReadAllLines($_.FullName).Length -gt 200 })
    if ($overLimit.Count -gt 0) { throw "Java files exceed 200 lines: $($overLimit.FullName -join ', ')" }
    & $MavenCommand @MavenArguments test
    if ($LASTEXITCODE -ne 0) { throw 'Maven regression tests failed.' }
} finally {
    Pop-Location
}
