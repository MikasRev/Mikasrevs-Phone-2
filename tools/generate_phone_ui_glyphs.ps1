param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot)
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Drawing

$outputPath = Join-Path $ProjectRoot "src/main/resources/assets/newphone/textures/gui/phone/ui_glyphs.png"
$outputDir = Split-Path -Parent $outputPath
New-Item -ItemType Directory -Force $outputDir | Out-Null

$cellSize = 64
$atlasSize = $cellSize * 4
$bitmap = [System.Drawing.Bitmap]::new(
    $atlasSize,
    $atlasSize,
    [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)

function New-RoundedPath([float]$x, [float]$y, [float]$width, [float]$height, [float]$radius) {
    $path = [System.Drawing.Drawing2D.GraphicsPath]::new()
    $diameter = [Math]::Max(1.0, $radius * 2.0)
    $path.AddArc($x, $y, $diameter, $diameter, 180, 90)
    $path.AddArc($x + $width - $diameter, $y, $diameter, $diameter, 270, 90)
    $path.AddArc($x + $width - $diameter, $y + $height - $diameter, $diameter, $diameter, 0, 90)
    $path.AddArc($x, $y + $height - $diameter, $diameter, $diameter, 90, 90)
    $path.CloseFigure()
    return $path
}

function New-IconPen([int]$alpha = 255, [float]$width = 4.5) {
    $color = [System.Drawing.Color]::FromArgb($alpha, 255, 255, 255)
    $pen = [System.Drawing.Pen]::new($color, $width)
    $pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
    return $pen
}

function Use-Cell([int]$index, [scriptblock]$draw) {
    $state = $graphics.Save()
    try {
        $graphics.TranslateTransform(($index % 4) * $cellSize, [Math]::Floor($index / 4) * $cellSize)
        & $draw
    } finally {
        $graphics.Restore($state)
    }
}

function Draw-Wifi([int]$alpha, [bool]$crossed) {
    $pen = New-IconPen $alpha 6.0
    $brush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb($alpha, 255, 255, 255))
    try {
        $graphics.DrawArc($pen, 6, 11, 52, 38, 200, 140)
        $graphics.DrawArc($pen, 18, 24, 28, 20, 200, 140)
        $graphics.FillEllipse($brush, 27, 37, 10, 10)
    } finally {
        $pen.Dispose()
        $brush.Dispose()
    }
    if ($crossed) {
        $slash = New-IconPen 255 5.2
        try {
            $graphics.DrawLine($slash, 14, 10, 51, 49)
        } finally {
            $slash.Dispose()
        }
    }
}

function Draw-Speaker([int]$alpha, [bool]$crossed) {
    $brush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb($alpha, 255, 255, 255))
    $speaker = [System.Drawing.Drawing2D.GraphicsPath]::new()
    $speaker.AddPolygon([System.Drawing.PointF[]]@(
        [System.Drawing.PointF]::new(8, 26),
        [System.Drawing.PointF]::new(19, 26),
        [System.Drawing.PointF]::new(33, 14),
        [System.Drawing.PointF]::new(37, 14),
        [System.Drawing.PointF]::new(37, 50),
        [System.Drawing.PointF]::new(33, 50),
        [System.Drawing.PointF]::new(19, 38),
        [System.Drawing.PointF]::new(8, 38)
    ))
    try {
        $graphics.FillPath($brush, $speaker)
    } finally {
        $speaker.Dispose()
        $brush.Dispose()
    }

    if ($crossed) {
        $slash = New-IconPen 255 5.2
        try {
            $graphics.DrawLine($slash, 13, 11, 54, 53)
        } finally {
            $slash.Dispose()
        }
    } else {
        $wave = New-IconPen $alpha 4.2
        try {
            $graphics.DrawArc($wave, 34, 20, 16, 24, -62, 124)
            $graphics.DrawArc($wave, 35, 12, 24, 40, -62, 124)
        } finally {
            $wave.Dispose()
        }
    }
}

try {
    $graphics.Clear([System.Drawing.Color]::Transparent)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $white = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::White)
    try {
        # 0: Airplane
        Use-Cell 0 {
            $plane = [System.Drawing.Drawing2D.GraphicsPath]::new()
            $plane.AddPolygon([System.Drawing.PointF[]]@(
                [System.Drawing.PointF]::new(58, 32),
                [System.Drawing.PointF]::new(53, 28),
                [System.Drawing.PointF]::new(39, 25),
                [System.Drawing.PointF]::new(30, 8),
                [System.Drawing.PointF]::new(25, 7),
                [System.Drawing.PointF]::new(27, 24),
                [System.Drawing.PointF]::new(14, 22),
                [System.Drawing.PointF]::new(9, 16),
                [System.Drawing.PointF]::new(5, 16),
                [System.Drawing.PointF]::new(9, 29),
                [System.Drawing.PointF]::new(26, 32),
                [System.Drawing.PointF]::new(9, 35),
                [System.Drawing.PointF]::new(5, 48),
                [System.Drawing.PointF]::new(9, 48),
                [System.Drawing.PointF]::new(14, 42),
                [System.Drawing.PointF]::new(27, 40),
                [System.Drawing.PointF]::new(25, 57),
                [System.Drawing.PointF]::new(30, 56),
                [System.Drawing.PointF]::new(39, 39),
                [System.Drawing.PointF]::new(53, 36)
            ))
            try { $graphics.FillPath($white, $plane) } finally { $plane.Dispose() }
        }

        # 1-2: Wi-Fi on/off
        Use-Cell 1 { Draw-Wifi 255 $false }
        Use-Cell 2 { Draw-Wifi 145 $true }

        # 3: Bluetooth
        Use-Cell 3 {
            $pen = New-IconPen 255 4.4
            try {
                $graphics.DrawLine($pen, 30, 7, 30, 57)
                $graphics.DrawLine($pen, 30, 7, 48, 23)
                $graphics.DrawLine($pen, 48, 23, 17, 48)
                $graphics.DrawLine($pen, 17, 16, 48, 41)
                $graphics.DrawLine($pen, 48, 41, 30, 57)
            } finally { $pen.Dispose() }
        }

        # 4-5: Sound on/off
        Use-Cell 4 { Draw-Speaker 255 $false }
        Use-Cell 5 { Draw-Speaker 145 $true }

        # 6: Focus moon
        Use-Cell 6 {
            $graphics.FillEllipse($white, 10, 8, 45, 48)
            $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
            $clear = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::Transparent)
            try { $graphics.FillEllipse($clear, 25, 3, 39, 39) } finally { $clear.Dispose() }
            $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceOver
        }

        # 7: Brightness sun
        Use-Cell 7 {
            $graphics.FillEllipse($white, 23, 23, 18, 18)
            $pen = New-IconPen 255 4.0
            try {
                foreach ($angle in 0,45,90,135,180,225,270,315) {
                    $radians = $angle * [Math]::PI / 180.0
                    $x1 = 32 + [Math]::Cos($radians) * 16
                    $y1 = 32 + [Math]::Sin($radians) * 16
                    $x2 = 32 + [Math]::Cos($radians) * 25
                    $y2 = 32 + [Math]::Sin($radians) * 25
                    $graphics.DrawLine($pen, [float]$x1, [float]$y1, [float]$x2, [float]$y2)
                }
            } finally { $pen.Dispose() }
        }

        # 8: Notifications bell
        Use-Cell 8 {
            $bell = [System.Drawing.Drawing2D.GraphicsPath]::new()
            $bell.AddBezier(13, 43, 18, 39, 18, 31, 18, 25)
            $bell.AddBezier(18, 25, 18, 16, 24, 11, 32, 11)
            $bell.AddBezier(32, 11, 40, 11, 46, 16, 46, 25)
            $bell.AddBezier(46, 25, 46, 31, 46, 39, 51, 43)
            $bell.AddLine(51, 43, 51, 46)
            $bell.AddLine(13, 46, 13, 43)
            $bell.CloseFigure()
            try {
                $graphics.FillPath($white, $bell)
                $graphics.FillEllipse($white, 27, 48, 10, 8)
                $graphics.FillEllipse($white, 29, 6, 6, 7)
            } finally { $bell.Dispose() }
        }

        # 9-10: Cellular signal on/off
        foreach ($entry in @(@{ Index = 9; Alpha = 255; Crossed = $false }, @{ Index = 10; Alpha = 145; Crossed = $true })) {
            Use-Cell $entry.Index {
                foreach ($bar in @(
                    @{ X = 8; Y = 43; H = 12 },
                    @{ X = 21; Y = 34; H = 21 },
                    @{ X = 34; Y = 24; H = 31 },
                    @{ X = 47; Y = 12; H = 43 }
                )) {
                    $path = New-RoundedPath $bar.X $bar.Y 9 $bar.H 4.5
                    $brush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb($entry.Alpha, 255, 255, 255))
                    try { $graphics.FillPath($brush, $path) } finally { $brush.Dispose(); $path.Dispose() }
                }
                if ($entry.Crossed) {
                    $slash = New-IconPen 255 5.2
                    try { $graphics.DrawLine($slash, 9, 11, 55, 56) } finally { $slash.Dispose() }
                }
            }
        }

        # 11: Full battery
        Use-Cell 11 {
            $outline = New-RoundedPath 5 18 50 29 7
            $outlinePen = New-IconPen 255 4.0
            $fill = New-RoundedPath 11 24 38 17 3.5
            $terminal = New-RoundedPath 57 26 4 13 2
            try {
                $graphics.DrawPath($outlinePen, $outline)
                $graphics.FillPath($white, $fill)
                $graphics.FillPath($white, $terminal)
            } finally {
                $terminal.Dispose()
                $fill.Dispose()
                $outlinePen.Dispose()
                $outline.Dispose()
            }
        }

        # 12-15: Media transport
        Use-Cell 12 {
            $graphics.FillRectangle($white, 11, 14, 6, 36)
            $graphics.FillPolygon($white, [System.Drawing.PointF[]]@(
                [System.Drawing.PointF]::new(18, 32),
                [System.Drawing.PointF]::new(49, 12),
                [System.Drawing.PointF]::new(49, 52)
            ))
        }
        Use-Cell 13 {
            $graphics.FillPolygon($white, [System.Drawing.PointF[]]@(
                [System.Drawing.PointF]::new(20, 10),
                [System.Drawing.PointF]::new(53, 32),
                [System.Drawing.PointF]::new(20, 54)
            ))
        }
        Use-Cell 14 {
            $leftBar = New-RoundedPath 17 11 10 42 4
            $rightBar = New-RoundedPath 37 11 10 42 4
            try {
                $graphics.FillPath($white, $leftBar)
                $graphics.FillPath($white, $rightBar)
            } finally { $rightBar.Dispose(); $leftBar.Dispose() }
        }
        Use-Cell 15 {
            $graphics.FillRectangle($white, 47, 14, 6, 36)
            $graphics.FillPolygon($white, [System.Drawing.PointF[]]@(
                [System.Drawing.PointF]::new(46, 32),
                [System.Drawing.PointF]::new(15, 12),
                [System.Drawing.PointF]::new(15, 52)
            ))
        }
    } finally {
        $white.Dispose()
    }

    $bitmap.Save($outputPath, [System.Drawing.Imaging.ImageFormat]::Png)
    Write-Host "Generated $outputPath"
} finally {
    $graphics.Dispose()
    $bitmap.Dispose()
}
