<#
.SYNOPSIS
Exports the existing Android launcher vector to the two Fastlane store icons.
.DESCRIPTION
Uses Windows System.Drawing; writes only fastlane/metadata/android/*/images/icon.png.
Quadratic curves are converted exactly to cubic curves before rasterization.
The vector's viewport, pivot, uniform scale, and classic colors are preserved.
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$projectRoot = Split-Path -Parent $PSScriptRoot
$resourceRoot = Join-Path $projectRoot 'app/src/main/res/drawable'
$androidNamespace = 'http://schemas.android.com/apk/res/android'
[xml]$foreground = Get-Content -LiteralPath (Join-Path $resourceRoot 'ic_launcher_foreground.xml') -Raw
[xml]$background = Get-Content -LiteralPath (Join-Path $resourceRoot 'ic_launcher_background.xml') -Raw
$vector = $foreground.DocumentElement
$group = $vector.SelectSingleNode('group')
$symbol = $group.SelectSingleNode('path')
$culture = [Globalization.CultureInfo]::InvariantCulture
function Get-Number([Xml.XmlElement]$Element, [string]$Name, [double]$Default) {
    $value = $Element.GetAttribute($Name, $androidNamespace)
    if ($value.Length -eq 0) { return $Default }
    return [double]::Parse($value, $culture)
}

$viewportWidth = Get-Number $vector 'viewportWidth' 0
$viewportHeight = Get-Number $vector 'viewportHeight' 0
$scaleX = Get-Number $group 'scaleX' 1
$scaleY = Get-Number $group 'scaleY' 1
$pivotX = Get-Number $group 'pivotX' 0
$pivotY = Get-Number $group 'pivotY' 0
$navy = $symbol.GetAttribute('fillColor', $androidNamespace)
$ivory = $background.DocumentElement.SelectSingleNode('path').GetAttribute('fillColor', $androidNamespace)
if ($viewportWidth -le 0 -or $viewportWidth -ne $viewportHeight -or $scaleX -ne $scaleY) {
    throw 'The store export requires the current square viewport and uniform symbol scale.'
}
if ($navy -ne '#102A43' -or $ivory -ne '#FFF7ED') {
    throw 'The source must retain the approved classic ivory and navy colors.'
}

$tokens = [regex]::Matches($symbol.GetAttribute('pathData', $androidNamespace), '[A-Za-z]|[-+]?(?:\d*\.\d+|\d+)')
$outline = [Drawing.Drawing2D.GraphicsPath]::new([Drawing.Drawing2D.FillMode]::Winding)
$x = $y = 0.0
$index = 0
while ($index -lt $tokens.Count) {
    $command = $tokens[$index++].Value
    switch ($command) {
        'M' {
            $outline.StartFigure()
            $x = [double]::Parse($tokens[$index++].Value, $culture)
            $y = [double]::Parse($tokens[$index++].Value, $culture)
        }
        'L' {
            $endX = [double]::Parse($tokens[$index++].Value, $culture)
            $endY = [double]::Parse($tokens[$index++].Value, $culture)
            $outline.AddLine([single]$x, [single]$y, [single]$endX, [single]$endY)
            $x = $endX; $y = $endY
        }
        'H' {
            $endX = [double]::Parse($tokens[$index++].Value, $culture)
            $outline.AddLine([single]$x, [single]$y, [single]$endX, [single]$y)
            $x = $endX
        }
        'V' {
            $endY = [double]::Parse($tokens[$index++].Value, $culture)
            $outline.AddLine([single]$x, [single]$y, [single]$x, [single]$endY)
            $y = $endY
        }
        'Q' {
            $controlX = [double]::Parse($tokens[$index++].Value, $culture)
            $controlY = [double]::Parse($tokens[$index++].Value, $culture)
            $endX = [double]::Parse($tokens[$index++].Value, $culture)
            $endY = [double]::Parse($tokens[$index++].Value, $culture)
            $firstX = $x + 2.0 / 3.0 * ($controlX - $x)
            $firstY = $y + 2.0 / 3.0 * ($controlY - $y)
            $secondX = $endX + 2.0 / 3.0 * ($controlX - $endX)
            $secondY = $endY + 2.0 / 3.0 * ($controlY - $endY)
            $outline.AddBezier([single]$x, [single]$y, [single]$firstX, [single]$firstY,
                [single]$secondX, [single]$secondY, [single]$endX, [single]$endY)
            $x = $endX; $y = $endY
        }
        'Z' { $outline.CloseFigure() }
        default { throw "Unsupported vector command: $command" }
    }
}

$exportSize = 512
$renderSize = $exportSize * 4
$pixelScale = $renderSize / $viewportWidth
$transform = [Drawing.Drawing2D.Matrix]::new([single]($scaleX * $pixelScale), 0, 0,
    [single]($scaleY * $pixelScale), [single]($pivotX * (1 - $scaleX) * $pixelScale),
    [single]($pivotY * (1 - $scaleY) * $pixelScale))
$outline.Transform($transform)
$canvas = [Drawing.Bitmap]::new($renderSize, $renderSize, [Drawing.Imaging.PixelFormat]::Format24bppRgb)
$graphics = [Drawing.Graphics]::FromImage($canvas)
$brush = [Drawing.SolidBrush]::new([Drawing.ColorTranslator]::FromHtml($navy))
$icon = [Drawing.Bitmap]::new($exportSize, $exportSize, [Drawing.Imaging.PixelFormat]::Format24bppRgb)
$exportGraphics = [Drawing.Graphics]::FromImage($icon)
$imageAttributes = [Drawing.Imaging.ImageAttributes]::new()
try {
    $graphics.Clear([Drawing.ColorTranslator]::FromHtml($ivory))
    $graphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.FillPath($brush, $outline)
    $exportGraphics.Clear([Drawing.ColorTranslator]::FromHtml($ivory))
    $exportGraphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBilinear
    $exportGraphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $imageAttributes.SetWrapMode([Drawing.Drawing2D.WrapMode]::TileFlipXY)
    $destination = [Drawing.Rectangle]::new(0, 0, $exportSize, $exportSize)
    $exportGraphics.DrawImage($canvas, $destination, 0, 0, $renderSize, $renderSize,
        [Drawing.GraphicsUnit]::Pixel, $imageAttributes)
    foreach ($locale in 'en-US', 'es-ES') {
        $images = Join-Path $projectRoot "fastlane/metadata/android/$locale/images"
        [void][IO.Directory]::CreateDirectory($images)
        $target = Join-Path $images 'icon.png'
        $icon.Save($target, [Drawing.Imaging.ImageFormat]::Png)
        Write-Output $target
    }
}
finally {
    $imageAttributes.Dispose()
    $exportGraphics.Dispose()
    $icon.Dispose()
    $brush.Dispose()
    $graphics.Dispose()
    $canvas.Dispose()
    $transform.Dispose()
    $outline.Dispose()
}
