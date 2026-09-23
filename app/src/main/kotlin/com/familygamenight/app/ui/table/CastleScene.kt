package com.familygamenight.app.ui.table

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.familygamenight.app.ui.theme.Castle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val TAU = (2 * PI).toFloat()
const val FLOOR_Y = -0.85f

fun pathOf(points: List<Offset>): Path = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
    close()
}

fun Camera.ring(radius: Float, y: Float, steps: Int = 96): List<Offset> =
    (0 until steps).map { i -> project(V3.onCircle(i * TAU / steps, radius, y)) }

/** Stone walls, moonlit windows, banners, torches and the flagstone floor. [t] animates the flames. */
fun DrawScope.drawCastleHall(cam: Camera, t: Float) {
    val w = size.width
    val h = size.height
    val horizon = cam.project(V3(0f, FLOOR_Y, -3.2f)).y.coerceIn(h * 0.25f, h * 0.7f)

    // Wall
    drawRect(Brush.verticalGradient(listOf(Color(0xFF17120E), Color(0xFF2C231C), Color(0xFF221A14)), endY = horizon))
    // Stone courses
    val rowH = h * 0.065f
    var row = 0
    var y = 0f
    while (y < horizon) {
        val blockW = rowH * 2.3f
        var x = if (row % 2 == 0) 0f else -blockW / 2
        while (x < w) {
            drawRect(Color(0x33000000), Offset(x, y), Size(blockW, rowH), style = Stroke(1.5f))
            drawLine(Color(0x14FFFFFF), Offset(x + 2, y + 2), Offset(x + blockW - 2, y + 2), 1f)
            x += blockW
        }
        y += rowH
        row++
    }

    // Moonlit gothic windows
    listOf(0.22f, 0.78f).forEach { fx -> drawGothicWindow(Offset(w * fx, h * 0.07f), w * 0.075f, horizon * 0.52f) }

    // Banners
    listOf(0.38f, 0.62f).forEach { fx -> drawBanner(Offset(w * fx, 0f), w * 0.055f, horizon * 0.5f) }

    // Torches with flickering flames
    listOf(0.07f, 0.5f, 0.93f).forEachIndexed { i, fx ->
        drawTorch(Offset(w * fx, horizon * 0.42f), h * 0.05f, t + i * 0.37f)
    }

    // Floor
    drawRect(
        Brush.verticalGradient(listOf(Color(0xFF2A211B), Color(0xFF15100C)), startY = horizon, endY = h),
        topLeft = Offset(0f, horizon), size = Size(w, h - horizon),
    )
    drawLine(Color(0x55000000), Offset(0f, horizon), Offset(w, horizon), 3f)
    // Flagstone grid in perspective
    val lineColor = Color(0x22000000)
    for (i in -8..8) {
        val a = cam.project(V3(i * 0.55f, FLOOR_Y, -3.2f))
        val b = cam.project(V3(i * 0.55f, FLOOR_Y, 2.0f))
        drawLine(lineColor, a, b, 2f)
    }
    var z = -3.2f
    while (z < 2.0f) {
        drawLine(lineColor, cam.project(V3(-6f, FLOOR_Y, z)), cam.project(V3(6f, FLOOR_Y, z)), 2f)
        z += 0.45f
    }
    // Royal carpet under the table
    val carpet = pathOf(cam.ring(1.9f, FLOOR_Y))
    drawPath(carpet, Color(0xFF4A0F16))
    drawPath(pathOf(cam.ring(1.78f, FLOOR_Y)), Castle.Gold.copy(alpha = 0.55f), style = Stroke(3f))
    drawPath(pathOf(cam.ring(1.9f, FLOOR_Y)), Castle.Gold.copy(alpha = 0.35f), style = Stroke(2f))

    // Warm vignette
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0x99000000)), center = Offset(w / 2, h * 0.55f), radius = w * 0.75f))
}

private fun DrawScope.drawGothicWindow(top: Offset, width: Float, height: Float) {
    val path = Path().apply {
        moveTo(top.x - width / 2, top.y + height)
        lineTo(top.x - width / 2, top.y + width * 0.6f)
        quadraticTo(top.x - width / 2, top.y, top.x, top.y - width * 0.1f)
        quadraticTo(top.x + width / 2, top.y, top.x + width / 2, top.y + width * 0.6f)
        lineTo(top.x + width / 2, top.y + height)
        close()
    }
    drawPath(path, Brush.verticalGradient(listOf(Color(0xFF0E2340), Color(0xFF355A86), Color(0xFF1B3050)), startY = top.y, endY = top.y + height))
    // Moon
    drawCircle(Color(0xFFE8EEF5), radius = width * 0.14f, center = Offset(top.x + width * 0.15f, top.y + height * 0.28f))
    // Leading
    drawLine(Color(0xFF15100C), Offset(top.x, top.y), Offset(top.x, top.y + height), width * 0.05f)
    drawLine(Color(0xFF15100C), Offset(top.x - width / 2, top.y + height * 0.55f), Offset(top.x + width / 2, top.y + height * 0.55f), width * 0.05f)
    drawPath(path, Color(0xFF6B5E52), style = Stroke(width * 0.09f))
    // Light spilling onto the wall
    drawCircle(Brush.radialGradient(listOf(Color(0x223D6A9C), Color.Transparent), center = Offset(top.x, top.y + height / 2), radius = width * 2.2f), radius = width * 2.2f, center = Offset(top.x, top.y + height / 2))
}

private fun DrawScope.drawBanner(top: Offset, width: Float, height: Float) {
    val path = Path().apply {
        moveTo(top.x - width / 2, top.y)
        lineTo(top.x + width / 2, top.y)
        lineTo(top.x + width / 2, top.y + height)
        lineTo(top.x, top.y + height * 0.84f)
        lineTo(top.x - width / 2, top.y + height)
        close()
    }
    drawPath(path, Brush.horizontalGradient(listOf(Color(0xFF5A0E18), Castle.Velvet, Color(0xFF5A0E18)), startX = top.x - width / 2, endX = top.x + width / 2))
    drawPath(path, Castle.Gold, style = Stroke(width * 0.06f))
    // A simple gold crest: crown on a shield
    val c = Offset(top.x, top.y + height * 0.42f)
    val s = width * 0.28f
    val shield = Path().apply {
        moveTo(c.x - s, c.y - s)
        lineTo(c.x + s, c.y - s)
        lineTo(c.x + s, c.y + s * 0.3f)
        quadraticTo(c.x + s, c.y + s * 1.1f, c.x, c.y + s * 1.4f)
        quadraticTo(c.x - s, c.y + s * 1.1f, c.x - s, c.y + s * 0.3f)
        close()
    }
    drawPath(shield, Castle.Gold.copy(alpha = 0.85f))
    drawPath(shield, Color(0xFF3A0A10), style = Stroke(width * 0.03f))
    drawCircle(Castle.Velvet, radius = s * 0.35f, center = c)
}

private fun DrawScope.drawTorch(at: Offset, size: Float, t: Float) {
    val flick = 0.85f + 0.15f * sin(t * TAU * 3f) * cos(t * TAU * 1.7f)
    drawCircle(
        Brush.radialGradient(listOf(Color(0x66FFB347), Color(0x22FF7A1A), Color.Transparent), center = at, radius = size * 5f * flick),
        radius = size * 5f * flick, center = at,
    )
    // Bracket
    drawLine(Color(0xFF2A2420), Offset(at.x, at.y + size * 0.3f), Offset(at.x, at.y + size * 1.4f), size * 0.25f, StrokeCap.Round)
    drawRoundRect(Color(0xFF4A3B2E), Offset(at.x - size * 0.3f, at.y + size * 0.1f), Size(size * 0.6f, size * 0.35f), CornerRadius(size * 0.1f))
    // Flame
    val fh = size * 1.2f * flick
    val flame = Path().apply {
        moveTo(at.x, at.y - fh)
        quadraticTo(at.x + size * 0.45f, at.y - fh * 0.35f, at.x + size * 0.25f, at.y + size * 0.1f)
        quadraticTo(at.x, at.y + size * 0.25f, at.x - size * 0.25f, at.y + size * 0.1f)
        quadraticTo(at.x - size * 0.45f, at.y - fh * 0.35f, at.x, at.y - fh)
        close()
    }
    drawPath(flame, Brush.verticalGradient(listOf(Color(0xFFFFF1B0), Color(0xFFFFA630), Color(0xFFD9480F)), startY = at.y - fh, endY = at.y + size * 0.2f))
}

/** The ornate round table: pedestal, carved edge, oak top with gold inlay and a felt centre. */
fun DrawScope.drawRoundTable(cam: Camera) {
    // Pedestal
    val footRing = cam.ring(0.5f, FLOOR_Y, 48)
    drawPath(pathOf(footRing), Color(0xFF2A170C))
    val stem = listOf(
        cam.project(V3(-0.22f, -0.1f, 0f)), cam.project(V3(0.22f, -0.1f, 0f)),
        cam.project(V3(0.38f, FLOOR_Y, 0f)), cam.project(V3(-0.38f, FLOOR_Y, 0f)),
    )
    drawPath(pathOf(stem), Brush.horizontalGradient(listOf(Color(0xFF24140A), Color(0xFF5A3620), Color(0xFF24140A)), startX = stem[3].x, endX = stem[2].x))

    // Carved edge: bottom ellipse first, the top covers its back half so only the front band shows.
    val bottom = cam.ring(1.0f, -0.13f)
    val top = cam.ring(1.0f, 0f)
    val bottomPath = pathOf(bottom)
    val topCentre = cam.project(V3(0f, 0f, 0f))
    val bottomY = bottom.maxOf { it.y }
    drawPath(bottomPath, Brush.verticalGradient(listOf(Color(0xFF6B4226), Color(0xFF2E1A0E)), startY = topCentre.y, endY = bottomY))
    // Gold banding along the edge
    drawPath(pathOf(cam.ring(1.0f, -0.045f)), Castle.Gold.copy(alpha = 0.9f), style = Stroke(cam.focal * 0.004f))
    drawPath(pathOf(cam.ring(1.0f, -0.1f)), Castle.Gold.copy(alpha = 0.55f), style = Stroke(cam.focal * 0.0025f))

    // Top
    val topPath = pathOf(top)
    val rx = (top.maxOf { it.x } - top.minOf { it.x }) / 2
    drawPath(
        topPath,
        Brush.radialGradient(
            listOf(Color(0xFF9A6A3E), Color(0xFF7A4E2B), Color(0xFF5A381E)),
            center = topCentre, radius = rx,
        ),
    )
    // Wood grain rings
    for (r in listOf(0.94f, 0.86f, 0.78f, 0.7f)) {
        drawPath(pathOf(cam.ring(r, 0f)), Color(0x1F000000), style = Stroke(cam.focal * 0.0018f))
    }
    // Ornate gold rim with diamonds
    drawPath(pathOf(cam.ring(0.985f, 0f)), Castle.Gold, style = Stroke(cam.focal * 0.006f))
    drawPath(pathOf(cam.ring(0.905f, 0f)), Castle.Gold.copy(alpha = 0.85f), style = Stroke(cam.focal * 0.0025f))
    val ornaments = 36
    for (k in 0 until ornaments) {
        val a = k * TAU / ornaments
        val d = listOf(
            cam.project(V3.onCircle(a, 0.965f, 0f)),
            cam.project(V3.onCircle(a + 0.035f, 0.945f, 0f)),
            cam.project(V3.onCircle(a, 0.925f, 0f)),
            cam.project(V3.onCircle(a - 0.035f, 0.945f, 0f)),
        )
        drawPath(pathOf(d), if (k % 3 == 0) Castle.Velvet else Castle.GoldPale.copy(alpha = 0.9f))
    }
    // Felt centre with filigree
    val felt = cam.ring(0.6f, 0.001f)
    drawPath(pathOf(felt), Brush.radialGradient(listOf(Color(0xFF2E7A50), Castle.Felt, Color(0xFF123B25)), center = topCentre, radius = rx * 0.6f))
    drawPath(pathOf(felt), Castle.Gold, style = Stroke(cam.focal * 0.004f))
    drawPath(pathOf(cam.ring(0.565f, 0.001f)), Castle.Gold.copy(alpha = 0.6f), style = Stroke(cam.focal * 0.0015f))
    // Compass-rose inlay
    for (k in 0 until 16) {
        val a = k * TAU / 16
        val len = if (k % 2 == 0) 0.5f else 0.3f
        val ray = listOf(
            cam.project(V3.onCircle(a, len, 0.002f)),
            cam.project(V3.onCircle(a + 0.12f, 0.1f, 0.002f)),
            cam.project(V3(0f, 0.002f, 0f)),
            cam.project(V3.onCircle(a - 0.12f, 0.1f, 0.002f)),
        )
        drawPath(pathOf(ray), Castle.Gold.copy(alpha = if (k % 2 == 0) 0.22f else 0.14f))
    }
}

/** Corners (TL, TR, BR, BL) of a card standing in space, facing along [normal]. */
fun cardCorners(center: V3, normal: V3, width: Float, height: Float, roll: Float = 0f): List<V3> {
    val right0 = (V3.UP cross normal).normalized()
    val up0 = normal cross right0
    val right = right0 * cos(roll) + up0 * sin(roll)
    val up = up0 * cos(roll) - right0 * sin(roll)
    val hw = width / 2
    val hh = height / 2
    return listOf(
        center - right * hw + up * hh,
        center + right * hw + up * hh,
        center + right * hw - up * hh,
        center - right * hw - up * hh,
    )
}

/** Corners of a card lying flat on the table, rotated by [yaw]. */
fun flatCardCorners(center: V3, width: Float, height: Float, yaw: Float): List<V3> {
    val right = V3(cos(yaw), 0f, sin(yaw))
    val fwd = V3(-sin(yaw), 0f, cos(yaw))
    val hw = width / 2
    val hh = height / 2
    return listOf(
        center - right * hw - fwd * hh,
        center + right * hw - fwd * hh,
        center + right * hw + fwd * hh,
        center - right * hw + fwd * hh,
    )
}

private fun lerp(a: Offset, b: Offset, f: Float) = Offset(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)

/** Card back: velvet red with a gold border and diamond, drawn on a projected quad. */
fun DrawScope.drawCardBack(q: List<Offset>, outline: Float = 1.5f) {
    val c = Offset(q.sumOf { it.x.toDouble() }.toFloat() / 4, q.sumOf { it.y.toDouble() }.toFloat() / 4)
    drawPath(pathOf(q), Color(0xFF7E1726))
    drawPath(pathOf(q.map { lerp(it, c, 0.16f) }), Castle.Gold.copy(alpha = 0.85f), style = Stroke(outline))
    val diamond = listOf(lerp(q[0], q[1], 0.5f), lerp(q[1], q[2], 0.5f), lerp(q[2], q[3], 0.5f), lerp(q[3], q[0], 0.5f))
        .map { lerp(it, c, 0.45f) }
    drawPath(pathOf(diamond), Castle.Gold.copy(alpha = 0.7f))
    drawPath(pathOf(q), Color(0xFFF4E8CF), style = Stroke(outline * 1.3f))
}

fun DrawScope.drawCardFaceFlat(q: List<Offset>) {
    drawPath(pathOf(q), Color(0xFFFBF7EE))
    drawPath(pathOf(q), Color(0xFF8C7650), style = Stroke(1.2f))
}

fun centroid(q: List<Offset>) = Offset(q.sumOf { it.x.toDouble() }.toFloat() / q.size, q.sumOf { it.y.toDouble() }.toFloat() / q.size)

fun DrawScope.drawChair(head: Offset, r: Float) {
    val w = r * 2.9f
    val h = r * 4.2f
    val rect = Rect(head.x - w / 2, head.y - r * 1.8f, head.x + w / 2, head.y - r * 1.8f + h)
    val back = Path().apply {
        addRoundRect(RoundRect(rect, topLeft = CornerRadius(w / 2), topRight = CornerRadius(w / 2), bottomLeft = CornerRadius.Zero, bottomRight = CornerRadius.Zero))
    }
    drawPath(back, Brush.horizontalGradient(listOf(Color(0xFF2A170C), Color(0xFF5A3620), Color(0xFF2A170C)), startX = rect.left, endX = rect.right))
    val inner = rect.deflate(w * 0.14f)
    val velvet = Path().apply {
        addRoundRect(RoundRect(inner, topLeft = CornerRadius(inner.width / 2), topRight = CornerRadius(inner.width / 2), bottomLeft = CornerRadius.Zero, bottomRight = CornerRadius.Zero))
    }
    drawPath(velvet, Castle.Velvet)
    drawPath(back, Castle.Gold.copy(alpha = 0.7f), style = Stroke(r * 0.08f))
    drawCircle(Castle.Gold, radius = r * 0.22f, center = Offset(head.x, rect.top - r * 0.12f))
}
