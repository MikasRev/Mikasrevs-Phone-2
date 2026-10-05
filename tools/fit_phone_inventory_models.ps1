param([string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot))

# Fit the complete bunny silhouette in an inventory slot, including both ears.
$ErrorActionPreference = 'Stop'
$assets = Join-Path $ProjectRoot 'src/main/resources/assets/newphone'
$utf8 = [System.Text.UTF8Encoding]::new($false)
$culture = [Globalization.CultureInfo]::InvariantCulture
$files = Get-ChildItem -LiteralPath (Join-Path $assets 'models') -Filter '*_case_bunny_*.json' -Recurse |
    Where-Object { $_.Name -match '^(z_phone|phone)_case_bunny_(blush|block)\.json$' }
foreach ($file in $files) {
    $model = [System.IO.File]::ReadAllText($file.FullName) | ConvertFrom-Json
    $gui = $model.display.gui
    $angles = @($gui.rotation | ForEach-Object { [double]$_ * [Math]::PI / 180 })
    $minX = $minY = [double]::PositiveInfinity
    $maxX = $maxY = [double]::NegativeInfinity
    $mesh = Join-Path $assets $model.model.Substring('newphone:'.Length)
    foreach ($line in [System.IO.File]::ReadLines($mesh)) {
        if (-not $line.StartsWith('v ')) { continue }
        $p = $line -split '\s+'
        $x = [double]::Parse($p[1], $culture) * 16 - 8
        $y = [double]::Parse($p[2], $culture) * 16 - 8
        $z = [double]::Parse($p[3], $culture) * 16 - 8
        # ItemTransform's XYZ quaternion applies Z, then Y, then X to vertices.
        $zx = [Math]::Cos($angles[2]) * $x - [Math]::Sin($angles[2]) * $y
        $zy = [Math]::Sin($angles[2]) * $x + [Math]::Cos($angles[2]) * $y
        $yx = [Math]::Cos($angles[1]) * $zx + [Math]::Sin($angles[1]) * $z
        $yz = -[Math]::Sin($angles[1]) * $zx + [Math]::Cos($angles[1]) * $z
        $xy = [Math]::Cos($angles[0]) * $zy - [Math]::Sin($angles[0]) * $yz
        $minX = [Math]::Min($minX, $yx); $maxX = [Math]::Max($maxX, $yx)
        $minY = [Math]::Min($minY, $xy); $maxY = [Math]::Max($maxY, $xy)
    }
    $scale = [Math]::Round(14.5 / [Math]::Max($maxX - $minX, $maxY - $minY), 6)
    $gui.scale = @($scale, $scale, $scale)
    $gui.translation = @([Math]::Round(-($minX + $maxX) * .5 * $scale, 6),
                         [Math]::Round(-($minY + $maxY) * .5 * $scale, 6), $gui.translation[2])
    [System.IO.File]::WriteAllText($file.FullName, ($model | ConvertTo-Json -Depth 30) + [Environment]::NewLine, $utf8)
}
Write-Output "Fitted $($files.Count) bunny inventory silhouettes."
