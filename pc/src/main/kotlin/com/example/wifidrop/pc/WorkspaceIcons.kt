package com.example.wifidrop.pc

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Qetara's desktop outline glyphs: 24-unit viewport, consistent 1.8-unit stroke. */
internal enum class WorkspaceGlyph(val pathData: String) {
    SHARE("M12 15V3 M8 7L12 3L16 7 M4 14V19Q4 21 6 21H18Q20 21 20 19V14"),
    RECEIVE("M12 3V15 M8 11L12 15L16 11 M4 14V19Q4 21 6 21H18Q20 21 20 19V14"),
    CHAT("M5 4H19Q21 4 21 6V15Q21 17 19 17H9L4 21V17Q3 17 3 15V6Q3 4 5 4Z M7 9H17 M7 13H13"),
    ACTIVITY("M21 12A9 9 0 1 1 3 12A9 9 0 1 1 21 12Z M12 7V12L16 14"),
    SETTINGS("M5 3V7 M5 11V21 M12 3V13 M12 17V21 M19 3V6 M19 10V21 M3 7H7V11H3Z M10 13H14V17H10Z M17 6H21V10H17Z"),
    FLASH("M13 2L5 14H11L10 22L19 9H13L13 2Z"),
    FILE("M6 3H14L19 8V19Q19 21 17 21H6Q4 21 4 19V5Q4 3 6 3Z M14 3V8H19 M8 13H15 M8 17H12"),
    FOLDER("M3 7V5Q3 3 5 3H10L13 6H19Q21 6 21 8V18Q21 20 19 20H5Q3 20 3 18V7Z M3 9H21"),
    DEVICES("M3 4H15Q17 4 17 6V13H3V6Q3 4 5 4 M3 13V15Q3 17 5 17H11 M8 17V20 M5 20H11 M16 10H20Q21 10 21 11V20Q21 21 20 21H16Q15 21 15 20V11Q15 10 16 10Z M18 18H18.01"),
    COPY("M9 8H18Q20 8 20 10V19Q20 21 18 21H9Q7 21 7 19V10Q7 8 9 8Z M16 8V5Q16 3 14 3H5Q3 3 3 5V14Q3 16 5 16H7"),
    CLOSE("M6 6L18 18 M18 6L6 18"),
    INFO("M21 12A9 9 0 1 1 3 12A9 9 0 1 1 21 12Z M12 11V17 M12 7H12.01"),
    LOCK("M7 10V7A5 5 0 0 1 17 7V10 M6 10H18Q20 10 20 12V19Q20 21 18 21H6Q4 21 4 19V12Q4 10 6 10Z M12 14V17"),
    SEARCH("M17 10A7 7 0 1 1 3 10A7 7 0 1 1 17 10Z M15 15L21 21"),
    CHECK("M5 12L10 17L19 7")
}

@Composable
internal fun WorkspaceIcon(
    glyph: WorkspaceGlyph,
    modifier: Modifier = Modifier,
    color: Color = qetaraInk
) {
    val path = remember(glyph) { PathParser().parsePathString(glyph.pathData).toPath() }
    Canvas(modifier.size(24.dp)) {
        val scale = size.minDimension / 24f
        withTransform({
            translate((size.width - size.minDimension) / 2f, (size.height - size.minDimension) / 2f)
            scale(scale, scale, pivot = androidx.compose.ui.geometry.Offset.Zero)
        }) {
            drawPath(path, color, style = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
