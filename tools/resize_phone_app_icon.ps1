param(
    [Parameter(Mandatory = $true)][string]$SourcePath,
    [Parameter(Mandatory = $true)][string]$DestinationPath
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$source = [System.Drawing.Image]::FromFile((Resolve-Path -LiteralPath $SourcePath).Path)
try {
    if ($source.Width -ne $source.Height) { throw 'App icon source must be square.' }
    $icon = New-Object System.Drawing.Bitmap(64, 64, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb))
    try {
        $graphics = [System.Drawing.Graphics]::FromImage($icon)
        try {
            $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
            $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
            $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
            $graphics.DrawImage($source, 0, 0, 64, 64)
        } finally { $graphics.Dispose() }
        $icon.Save([System.IO.Path]::GetFullPath($DestinationPath), [System.Drawing.Imaging.ImageFormat]::Png)
        Write-Output "Saved 64x64 RGBA app icon: $DestinationPath"
    } finally { $icon.Dispose() }
} finally { $source.Dispose() }
