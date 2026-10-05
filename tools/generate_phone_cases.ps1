param([string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot), [string]$JavaExecutable = "")
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
if (!$JavaExecutable) {
    if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME "bin/java.exe"))) {
        $JavaExecutable = Join-Path $env:JAVA_HOME "bin/java.exe"
    } else { $JavaExecutable = (Get-Command java -ErrorAction Stop).Source }
}
Push-Location -LiteralPath $ProjectRoot
try {
    & $JavaExecutable (Join-Path $ProjectRoot "tools/PhoneCaseDesigner.java")
    if ($LASTEXITCODE -ne 0) { throw "Case generation failed ($LASTEXITCODE)" }
} finally { Pop-Location }