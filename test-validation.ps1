# Compiles and runs submission and validation regression tests.
# Testing only; not required by the application.

$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$outputDirectory = Join-Path $projectRoot 'target/validation-tests'
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
$sources = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src') -Filter '*.java' -Recurse |
    ForEach-Object { $_.FullName })
$sources += @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'tests/autograder/validation') -Filter '*.java' |
    ForEach-Object { $_.FullName })
$overLimit = @($sources | Where-Object { [System.IO.File]::ReadAllLines($_).Length -gt 200 })
if ($overLimit.Count -gt 0) { throw "Java files exceed 200 lines: $($overLimit -join ', ')" }
& javac --release 17 -encoding UTF-8 -d $outputDirectory @sources
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }
$testClasses = @('AnswerFileRuleTest', 'AnswerFileLocatorTest', 'ValidationWorkflowTest', 'Pair1ValidationTest',
    'HeaderSectionDetectionTest', 'EditableFileSelectionTest', 'FileUtilsTest',
    'StudentHeaderReaderTest', 'SubmissionLoaderTest')
foreach ($testClass in $testClasses) {
    & java -cp $outputDirectory "autograder.validation.$testClass"
    if ($LASTEXITCODE -ne 0) { throw "$testClass failed." }
}
