package com.example.wifidrop.pc

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.PathParser

private val brandInk = Color(0xFF102A43)
private val brandCanvas = Color(0xFFFFF7ED)
private const val brandViewport = 108f
private const val mobileForegroundScale = 0.65625f
private const val appTileCornerRadius = 24f

// Exact Android foreground contour, without its adaptive-icon group transform.
// Its geometric bounds are (8, 21.6103)..(100, 86.3897), centered at (54, 54).
private const val qetaraBrandPathData =
    "M78.8495,21.6103Q76.4784,21.8948 73.0165,23.2227Q69.5546,24.5505 64.7175,27.5856Q59.8804,30.6206 53.1464,36.0268Q45.6536,42.1918 40.8165,45.4639Q35.9794,48.7361 32.6598,50.0165Q29.3402,51.2969 26.2103,51.2969H24.6928Q25.2619,49.3052 26.0206,45.8907Q26.7794,42.4763 27.5856,38.4454Q28.3918,34.4144 29.1031,30.5258Q29.8144,26.6371 30.1938,23.6969H14.2598Q13.3113,29.8619 11.9361,36.7381Q10.5608,43.6144 9.0433,50.1588Q8.3794,53.3835 8.1897,55.6124Q8,57.8412 8,58.5052Q8,66.0928 12.7423,68.3691Q19.3814,67.4206 24.5505,67.1835Q29.7196,66.9464 31.8062,66.9464Q49.068,66.9464 63.532,71.6887Q77.9959,76.4309 90.0412,86.3897L100,75.3876Q94.5938,69.6021 86.7216,65.0495Q78.8495,60.4969 70.0763,57.699Q61.3031,54.901 52.9567,54.2371V53.8577Q57.0351,52.0557 59.9753,50.301Q62.9155,48.5464 66.4247,46.2701Q69.2701,44.468 71.9258,42.8557Q74.5814,41.2433 76.9526,39.8206Q79.4186,38.3031 81.8845,37.3072Q84.3505,36.3113 86.7216,35.6474Z"

/**
 * Flat header mark: fit the contour itself to 78% of its slot, preserving its 1.42020457:1 aspect.
 * The launcher viewport padding is intentionally excluded so that the header stays readable.
 */
@Composable
internal fun QetaraLogoMark(
    modifier: Modifier = Modifier,
    color: Color = brandInk,
    fillRatio: Float = 0.78f
) {
    val path = remember { PathParser().parsePathString(qetaraBrandPathData).toPath() }
    Canvas(modifier = modifier) {
        val bounds = path.getBounds()
        val occupancy = fillRatio.coerceIn(0f, 1f)
        val factor = minOf(size.width / bounds.width, size.height / bounds.height) * occupancy
        drawBrandPath(path, color, factor, bounds.center)
    }
}

/**
 * Desktop app tile: preserve Android's 108x108 viewport and 21/32 foreground scale around (54,54).
 * Android launchers apply their own adaptive mask/crop to that viewport. Desktop does not: this
 * painter supplies a rounded tile and never stretches/crops the foreground to imitate a mask.
 * Packaged PNG/ICO/ICNS resources are rasterized from qetara-brand.svg with these same constants.
 */
internal class QetaraWindowIconPainter : Painter() {
    private val path = PathParser().parsePathString(qetaraBrandPathData).toPath()
    override val intrinsicSize: Size = Size(brandViewport, brandViewport)

    override fun DrawScope.onDraw() {
        val edge = size.minDimension
        val viewportScale = edge / brandViewport
        drawRoundRect(
            color = brandCanvas,
            topLeft = Offset((size.width - edge) / 2f, (size.height - edge) / 2f),
            size = Size(edge, edge),
            cornerRadius = CornerRadius(appTileCornerRadius * viewportScale)
        )
        drawBrandPath(path, brandInk, viewportScale * mobileForegroundScale, Offset(54f, 54f))
    }
}

private fun DrawScope.drawBrandPath(path: Path, color: Color, factor: Float, sourceCenter: Offset) {
    if (size.minDimension <= 0f || factor <= 0f) return
    withTransform({
        translate(size.width / 2f - sourceCenter.x * factor, size.height / 2f - sourceCenter.y * factor)
        scale(factor, factor, pivot = Offset.Zero)
    }) {
        drawPath(path, color)
    }
}
