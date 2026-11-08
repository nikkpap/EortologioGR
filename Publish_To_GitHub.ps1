# EortologioGR — one-time source publisher
# Run from the extracted EortologioPlus directory in PowerShell.
$ErrorActionPreference = 'Stop'
$repo = 'nikkpap/EortologioGR'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not (Get-Command git -ErrorAction SilentlyContinue)) { throw 'Install Git first: winget install Git.Git' }
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { throw 'Install GitHub CLI first: winget install GitHub.cli' }
& gh auth status 2>$null
if ($LASTEXITCODE -ne 0) { & gh auth login --web --git-protocol https }
$work = Join-Path $env:TEMP 'EortologioGR-publish'
if (Test-Path $work) { throw "Work folder already exists: $work. Remove or rename it after reviewing contents." }
& gh repo clone $repo $work
if ($LASTEXITCODE -ne 0) { throw 'Clone failed' }
foreach ($name in @('app','settings.gradle.kts','build.gradle.kts','gradle.properties','README.md','.gitignore','.github')) {
    $src = Join-Path $root $name; $dst = Join-Path $work $name
    if (Test-Path $src -PathType Container) {
        New-Item -ItemType Directory -Path $dst -Force | Out-Null
        Copy-Item -Path (Join-Path $src '*') -Destination $dst -Recurse -Force
    } else {
        Copy-Item -LiteralPath $src -Destination $dst -Force
    }
}
Push-Location $work
try {
    & git add -- app settings.gradle.kts build.gradle.kts gradle.properties README.md .gitignore .github
    & git diff --cached --check
    if ($LASTEXITCODE -ne 0) { throw 'Diff validation failed' }
    & git diff --cached --quiet
    if ($LASTEXITCODE -eq 0) { Write-Host 'Already up to date'; exit 0 }
    & git commit -m 'Add full EortologioGR Android source v1.0.0'
    if ($LASTEXITCODE -ne 0) { throw 'Commit failed' }
    & git push origin HEAD:main
    if ($LASTEXITCODE -ne 0) { throw 'Push failed. No force push attempted.' }
    Write-Host 'Source pushed to GitHub successfully'
} finally { Pop-Location }
