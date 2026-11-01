$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$outputDirectory = Join-Path $projectRoot 'target/editable-file-detection-tests'
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
$sources = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src') -Filter '*.java' -Recurse |
    ForEach-Object { $_.FullName })
$sources += @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'tests') -Filter '*.java' -Recurse |
    ForEach-Object { $_.FullName })
& javac --release 17 -encoding UTF-8 -d $outputDirectory @sources
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }
& java -cp $outputDirectory autograder.submission.EditableFileDetectorTest
if ($LASTEXITCODE -ne 0) { throw 'Editable-file detection tests failed.' }
