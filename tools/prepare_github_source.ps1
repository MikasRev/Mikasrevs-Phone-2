# Copies publishable source to an isolated directory; never deletes original files.
[CmdletBinding()]
param([string] $OutputDirectory = '')

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$outputRoot = if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    Join-Path $projectRoot 'build/github-source'
} else {
    $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputDirectory)
}
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$exportRoot = Join-Path $outputRoot $stamp
$sourceRoot = Join-Path $exportRoot 'MRPhone-2-0.9.4-beta-source'
$utf8 = [System.Text.UTF8Encoding]::new($false)

$rootFiles = @(
    '.gitignore', '.gitattributes', 'build.gradle', 'settings.gradle',
    'gradle.properties', 'gradlew', 'gradlew.bat', 'LICENSE.txt',
    'README.md', 'README_TR.md', 'CONTRIBUTING.md',
    'CHANGELOG.md', 'THIRD_PARTY_NOTICES.md', 'CREDITS.txt'
)
$sourceDirectories = @('src', 'gradle', 'tools', 'docs', 'licenses', '.github',
    'art', 'phone_models', 'Blockbench_Source')

function Test-PublishableFile([string] $relativePath) {
    if ($relativePath -eq 'gradle/wrapper/gradle-wrapper.jar') { return $true }
    if ($relativePath -match '(^|/)(build|\.gradle|\.idea|\.vscode|run|run-data|logs|crash-reports|mcef-libraries|node_modules|__pycache__|\.cache)/') { return $false }
    if ($relativePath -match '^art/iphone_duo/references/') { return $false }
    if ($relativePath -match '\.(class|jar|exe|dll|so|dylib|log|hprof|zip|7z|tar\.gz|blend[12]|bak|tmp|pyc|pem|key|p12|pfx|jks|keystore)$|~$') { return $false }
    $fileName = [System.IO.Path]::GetFileName($relativePath)
    if ($fileName -match '^\.env($|\.)' -and $fileName -notin @('.env.example', '.env.sample')) { return $false }
    if ($fileName -in @('credentials.json', 'secrets.json', 'local.properties',
        'gradle-local.properties', 'Thumbs.db', '.DS_Store')) { return $false }
    return $true
}

$files = [System.Collections.Generic.List[System.IO.FileInfo]]::new()
foreach ($name in $rootFiles) {
    $filePath = Join-Path $projectRoot $name
    if (Test-Path -LiteralPath $filePath -PathType Leaf) {
        $files.Add((Get-Item -LiteralPath $filePath -Force))
    }
}
foreach ($directory in $sourceDirectories) {
    $directoryPath = Join-Path $projectRoot $directory
    if (Test-Path -LiteralPath $directoryPath -PathType Container) {
        foreach ($entry in Get-ChildItem -LiteralPath $directoryPath -File -Recurse -Force) {
            # Do not export filesystem links pointing outside the checkout.
            if (($entry.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw "Source links require manual review: $($entry.Name)"
            }
            $relative = $entry.FullName.Substring($projectRoot.Length + 1).Replace('\', '/')
            if (Test-PublishableFile $relative) { $files.Add($entry) }
        }
    }
}

$secretPattern = '(?im)-----BEGIN (?:RSA |EC |OPENSSH |DSA |ENCRYPTED )?PRIVATE KEY-----|\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{50,}|AKIA[A-Z0-9]{16}|ASIA[A-Z0-9]{16}|AIza[A-Za-z0-9_-]{30,}|sk-(?:proj-)?[A-Za-z0-9_-]{24,}|xox[baprs]-[A-Za-z0-9-]{20,})\b|\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b|(?:jdbc:)?(?:mysql|postgres(?:ql)?|mongodb(?:\+srv)?):(?://)?[^\s"<>]+|https://(?:discord(?:app)?\.com/api/webhooks/\d+/[A-Za-z0-9_-]+|hooks\.slack\.com/services/[A-Za-z0-9/_-]+)'
$textExtensions = @('.java', '.gradle', '.properties', '.json', '.toml', '.md',
    '.txt', '.yml', '.yaml', '.py', '.cjs', '.ps1', '.bat', '.tsv', '.cfg',
    '.xml', '.gltf', '.bbmodel', '.obj', '.mtl')
foreach ($file in $files) {
    if ($file.Length -ge 100MB) { throw "File exceeds the 100 MiB source limit: $($file.Name)" }
    if ($file.Extension -in $textExtensions -or $file.Name -eq 'gradlew') {
        $body = [System.IO.File]::ReadAllText($file.FullName)
        if ($body -match $secretPattern) {
            $relative = $file.FullName.Substring($projectRoot.Length + 1)
            throw "Possible credential detected; review before export: $relative"
        }
    }
    if ($file.Extension -in @('.blend', '.png', '.glb')) {
        $metadata = [System.Text.Encoding]::GetEncoding(28591).GetString(
            [System.IO.File]::ReadAllBytes($file.FullName))
        if ($metadata -match 'C:[/\\]Users[/\\]|/Users/[A-Za-z0-9_-]+/|/home/[A-Za-z0-9_-]+/') {
            $relative = $file.FullName.Substring($projectRoot.Length + 1)
            throw "Personal filesystem path in asset metadata; sanitize before export: $relative"
        }
    }
}

New-Item -ItemType Directory -Path $sourceRoot -Force | Out-Null
$manifest = [System.Collections.Generic.List[string]]::new()
foreach ($file in ($files | Sort-Object FullName)) {
    $relative = $file.FullName.Substring($projectRoot.Length + 1)
    $target = Join-Path $sourceRoot $relative
    New-Item -ItemType Directory -Path ([System.IO.Path]::GetDirectoryName($target)) -Force | Out-Null
    Copy-Item -LiteralPath $file.FullName -Destination $target
    $hash = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant()
    $manifest.Add("$hash  $($relative.Replace('\', '/'))")
}
[System.IO.File]::WriteAllLines((Join-Path $exportRoot 'SOURCE-SHA256.txt'), $manifest, $utf8)

Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = Join-Path $exportRoot 'MRPhone-2-0.9.4-beta-source.zip'
[System.IO.Compression.ZipFile]::CreateFromDirectory($sourceRoot, $archive,
    [System.IO.Compression.CompressionLevel]::Optimal, $true)
$archiveHash = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
[System.IO.File]::WriteAllText((Join-Path $exportRoot 'ARCHIVE-SHA256.txt'),
    "$archiveHash  MRPhone-2-0.9.4-beta-source.zip`n", $utf8)

$properties = [System.IO.File]::ReadAllText((Join-Path $projectRoot 'gradle.properties'))
if ($properties -match '(?m)^mod_license=All Rights Reserved\s*$') {
    Write-Warning 'Project license is pending. Finalize it and regenerate this package before public release.'
}
Write-Output "Files exported: $($files.Count)"
Write-Output "Source: $sourceRoot"
Write-Output "Archive: $archive"
Write-Output "Archive SHA-256: $archiveHash"
