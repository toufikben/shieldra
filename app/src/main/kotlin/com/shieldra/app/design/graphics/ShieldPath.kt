package com.shieldra.app.design.graphics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** Builds the original Shieldra shield silhouette inside a given size. */
fun createShieldPath(size: Size): Path = Path().apply {
    val w = size.width
    val h = size.height
    moveTo(w * 0.50f, h * 0.00f)
    cubicTo(
        w * 0.72f, h * 0.04f,
        w * 0.94f, h * 0.10f,
        w * 1.00f, h * 0.24f,
    )
    lineTo(w * 1.00f, h * 0.46f)
    cubicTo(
        w * 1.00f, h * 0.74f,
        w * 0.72f, h * 0.92f,
        w * 0.50f, h * 1.00f,
    )
    cubicTo(
        w * 0.28f, h * 0.92f,
        w * 0.00f, h * 0.74f,
        w * 0.00f, h * 0.46f,
    )
    lineTo(w * 0.00f, h * 0.24f)
    cubicTo(
        w * 0.06f, h * 0.10f,
        w * 0.28f, h * 0.04f,
        w * 0.50f, h * 0.00f,
    )
    close()
}

/** Compose Shape wrapping the shield path. */
val ShieldShape: Shape = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Generic(createShieldPath(size))
}

/** Dashed effect for the Disabled state. */
fun dashedStrokeEffect(): PathEffect =
    PathEffect.dashPathEffect(floatArrayOf(18f, 14f), 0f)

// ===== Glyph drawing inside a given size. =====

private fun strokeFor(size: Size, widthFraction: Float = 0.09f): Stroke = Stroke(
    width = size.minDimension * widthFraction,
    cap = StrokeCap.Round,
    join = StrokeJoin.Round,
)

fun DrawScope.drawGlyphCheck(size: Size, color: Color) {
    val s = strokeFor(size, 0.10f)
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w * 0.32f, h * 0.52f)
        lineTo(w * 0.45f, h * 0.66f)
        lineTo(w * 0.70f, h * 0.36f)
    }
    drawPath(path, color, style = s)
}

fun DrawScope.drawGlyphExclamation(size: Size, color: Color) {
    val s = strokeFor(size, 0.11f)
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w * 0.50f, h * 0.30f)
        lineTo(w * 0.50f, h * 0.60f)
    }
    drawPath(path, color, style = s)
    drawCircle(
        color = color,
        radius = size.minDimension * 0.055f,
        center = Offset(w * 0.50f, h * 0.72f),
    )
}

fun DrawScope.drawGlyphX(size: Size, color: Color) {
    val s = strokeFor(size, 0.11f)
    val w = size.width
    val h = size.height
    val p1 = Path().apply {
        moveTo(w * 0.34f, h * 0.34f)
        lineTo(w * 0.66f, h * 0.66f)
    }
    val p2 = Path().apply {
        moveTo(w * 0.66f, h * 0.34f)
        lineTo(w * 0.34f, h * 0.66f)
    }
    drawPath(p1, color, style = s)
    drawPath(p2, color, style = s)
}

fun DrawScope.drawGlyphSlash(size: Size, color: Color) {
    val s = strokeFor(size, 0.11f)
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w * 0.30f, h * 0.72f)
        lineTo(w * 0.70f, h * 0.28f)
    }
    drawPath(path, color, style = s)
}
