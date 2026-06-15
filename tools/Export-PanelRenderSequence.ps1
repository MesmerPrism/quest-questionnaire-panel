param(
    [string]$OutputDir = "artifacts\panel-render-sequence",
    [switch]$KeepSnapshots
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$outputPath = Join-Path $repoRoot $OutputDir
$snapshotPath = Join-Path $repoRoot "app\src\test\snapshots"
$expectedNames = @(
    "brb-01-language-select",
    "brb-02-demographics",
    "brb-03-prior-experience",
    "brb-04-post-pictographic",
    "brb-05-post-presence",
    "brb-06-lost-opportunity",
    "brb-07-final-confirmation",
    "brb-08-extra-presses-prompt",
    "brb-09-export-summary",
    "generic-01-intro",
    "generic-02-rating",
    "generic-03-comment",
    "generic-04-complete"
)

Push-Location $repoRoot
try {
    if (Test-Path -LiteralPath $outputPath) {
        Remove-Item -LiteralPath $outputPath -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path $outputPath | Out-Null

    if (Test-Path -LiteralPath $snapshotPath) {
        Remove-Item -LiteralPath $snapshotPath -Recurse -Force
    }

    .\gradlew.bat :app:recordPaparazziMinimalDebug --tests `
        "io.github.mesmerprism.questquestionnaire.panel.PanelRenderSequenceTest"
    if ($LASTEXITCODE -ne 0) {
        throw "Paparazzi record task failed with exit code $LASTEXITCODE."
    }

    $images = Get-ChildItem -Path $snapshotPath -Recurse -Filter "*.png" |
        Sort-Object FullName

    if (-not $images) {
        throw "No panel render images were produced under $snapshotPath."
    }

    $exportedNames = @()
    foreach ($image in $images) {
        $name = $image.BaseName
        $name = $name -replace "^.*PanelRenderSequenceTest[_-][^_-]+[_-]", ""
        Copy-Item -LiteralPath $image.FullName -Destination (Join-Path $outputPath "$name.png")
        $exportedNames += $name
    }

    $missingNames = $expectedNames | Where-Object { $exportedNames -notcontains $_ }
    if ($missingNames) {
        throw "Missing expected panel render images: $($missingNames -join ', ')"
    }

    if (-not $KeepSnapshots -and (Test-Path -LiteralPath $snapshotPath)) {
        Remove-Item -LiteralPath $snapshotPath -Recurse -Force
    }

    Get-ChildItem -Path $outputPath -Filter "*.png" |
        Sort-Object Name |
        Select-Object Name, Length, FullName
} finally {
    Pop-Location
}
