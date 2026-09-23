package com.familygamenight.app.ui.table

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.familygamenight.app.ui.initials
import com.familygamenight.app.ui.theme.Castle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** One person sitting at the table, as the scene needs to draw them. */
data class SceneSeat(
    val index: Int,
    val name: String,
    val avatar: ImageBitmap?,
    val color: Color,
    val cardCount: Int,
    /** Face-up collected sets, shown on the table in front of the player. */
    val books: List<String>,
    val isCurrent: Boolean,
    val isAi: Boolean,
    val missing: Boolean,
    val selectable: Boolean,
    val bubble: String?,
)

private const val TAU = (2 * PI).toFloat()
/** Angle (radians) kept clear either side of the viewer's own place. */
private const val VIEWER_GAP = 0.8f

/** Where each opponent sits: spread around the far arc, in turn order going left from the viewer. */
fun seatAngles(count: Int, viewer: Int): Map<Int, Float> {
    val others = (1 until count).map { (viewer + it) % count }
    val arc = TAU - 2 * VIEWER_GAP
    return others.mapIndexed { j, seat -> seat to (PI.toFloat() / 2 + VIEWER_GAP + (j + 0.5f) * arc / others.size) }.toMap()
}

/**
 * The castle hall and round table with every other player seated around it, their cards floating
 * in front of them (backs only, of course). The viewer's own hand is drawn separately on top.
 */
@Composable
fun TableScene(
    seats: List<SceneSeat>,
    viewer: Int,
    pondCount: Int,
    modifier: Modifier = Modifier,
    onSeatTap: (Int) -> Unit = {},
) {
    val measurer = rememberTextMeasurer()
    val anim = rememberInfiniteTransition(label = "scene")
    val t by anim.animateFloat(0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val hits = remember { mutableMapOf<Int, Pair<Offset, Float>>() }

    Canvas(
        modifier
            .fillMaxSize()
            .pointerInput(seats) {
                detectTapGestures { p ->
                    hits.entries
                        .filter { (_, v) -> (v.first - p).getDistance() <= v.second }
                        .minByOrNull { (_, v) -> (v.first - p).getDistance() }
                        ?.let { onSeatTap(it.key) }
                }
            },
    ) {
        val cam = Camera(size.width, size.height)
        drawCastleHall(cam, t)

        val angles = seatAngles(seats.size, viewer)
        val opponents = seats.filter { it.index != viewer && angles.containsKey(it.index) }
            .sortedBy { cam.depth(V3.onCircle(angles.getValue(it.index), 1.2f, 0f)) }.reversed() // far first
        val behind = opponents.filter { sin(angles.getValue(it.index)) < 0.25f }
        val beside = opponents - behind.toSet()

        // Shrink people a little when the table is crowded so neighbours don't overlap.
        val spacing = if (opponents.isEmpty()) 1f else (TAU - 2 * VIEWER_GAP) / opponents.size * 1.24f
        val scale = (spacing / 0.8f).coerceIn(0.72f, 1f)
        hits.clear()
        behind.forEach { drawSeatPerson(cam, it, angles.getValue(it.index), t, measurer, hits, scale) }
        drawRoundTable(cam)
        drawPond(cam, pondCount, measurer)
        opponents.forEach { drawBooks(cam, it, angles.getValue(it.index), measurer) }
        beside.forEach { drawSeatPerson(cam, it, angles.getValue(it.index), t, measurer, hits, scale) }
        opponents.forEach { drawFloatingHand(cam, it, angles.getValue(it.index), t, measurer) }
        opponents.forEach { s -> s.bubble?.let { drawBubble(cam, angles.getValue(s.index), it, measurer, scale) } }
    }
}

private fun head(angle: Float) = V3.onCircle(angle, 1.24f, 0.36f)

private fun DrawScope.drawSeatPerson(
    cam: Camera,
    s: SceneSeat,
    angle: Float,
    t: Float,
    measurer: TextMeasurer,
    hits: MutableMap<Int, Pair<Offset, Float>>,
    scale: Float,
) {
    val h = head(angle)
    val c = cam.project(h)
    val r = cam.pixelsPerUnit(h) * 0.15f * scale
    drawChair(c, r)

    // Turn glow / tap-to-ask highlight
    val pulse = 0.55f + 0.45f * sin(t * TAU * 2)
    if (s.isCurrent) {
        drawCircle(Castle.GoldPale.copy(alpha = 0.35f * pulse), radius = r * 1.55f, center = c)
    }
    if (s.selectable) {
        drawCircle(Color(0xFF7CFFB2).copy(alpha = 0.3f + 0.4f * pulse), radius = r * 1.45f, center = c, style = Stroke(r * 0.18f))
    }

    // Avatar
    val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }
    drawCircle(s.color, radius = r, center = c)
    if (s.avatar != null) {
        clipPath(circle) {
            drawImage(
                s.avatar,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(s.avatar.width, s.avatar.height),
                dstOffset = IntOffset((c.x - r).roundToInt(), (c.y - r).roundToInt()),
                dstSize = IntSize((2 * r).roundToInt(), (2 * r).roundToInt()),
            )
        }
    } else {
        drawCentredText(measurer, initials(s.name), c, (r * 0.8f), Color.White, bold = true)
    }
    drawCircle(if (s.isCurrent) Castle.GoldPale else Castle.Gold, radius = r, center = c, style = Stroke(r * if (s.isCurrent) 0.16f else 0.09f))
    if (s.missing) {
        drawCircle(Color(0xAA000000), radius = r, center = c)
        drawCentredText(measurer, "away", c, r * 0.45f, Castle.Parchment, bold = true)
    }

    // Name plate
    val label = s.name + if (s.isAi) " 🤖" else ""
    val fontPx = (r * 0.42f).coerceIn(11.sp.toPx(), 18.sp.toPx())
    val layout = measurer.measure(label, TextStyle(fontSize = (fontPx / density / fontScale).sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, color = Castle.Parchment))
    val plateW = layout.size.width + fontPx
    val plateH = layout.size.height + fontPx * 0.35f
    // The name sits on a plaque at the top of the chair, clear of the cards floating below.
    val plateTop = Offset(c.x - plateW / 2, c.y - r * 1.12f - plateH)
    drawRoundRect(Color(0xE6231913), plateTop, Size(plateW, plateH), CornerRadius(plateH / 2))
    drawRoundRect(if (s.isCurrent) Castle.GoldPale else Castle.Gold.copy(alpha = 0.7f), plateTop, Size(plateW, plateH), CornerRadius(plateH / 2), style = Stroke(1.5f))
    drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, plateTop.y + (plateH - layout.size.height) / 2))

    hits[s.index] = c to max(r * 1.8f, 36f * density)
}

private fun DrawScope.drawFloatingHand(cam: Camera, s: SceneSeat, angle: Float, t: Float, measurer: TextMeasurer) {
    if (s.cardCount <= 0) return
    val shown = min(s.cardCount, 10)
    val bob = sin((t + s.index * 0.13f) * TAU) * 0.012f
    val base = V3.onCircle(angle, 0.86f, 0.25f + bob)
    // Face the cards toward the table centre, turned a little toward the camera so they read well.
    val inward = V3.onCircle(angle, 1f, 0f) * -1f
    val toCam = (cam.eye - base).normalized()
    val normal = (inward * 0.45f + toCam * 0.55f).normalized()
    val right = (V3.UP cross normal).normalized()
    val cardW = 0.12f
    val cardH = 0.17f
    val spread = min(0.045f, 0.34f / shown)
    for (i in 0 until shown) {
        val off = i - (shown - 1) / 2f
        val center = base + right * (off * spread) + V3(0f, -abs(off) * 0.004f, 0f) + normal * (i * 0.002f)
        val quad = cardCorners(center, normal, cardW, cardH, roll = -off * 0.09f).map { cam.project(it) }
        drawCardBack(quad, outline = max(1f, cam.pixelsPerUnit(center) * 0.004f))
    }
    // Count badge
    val badgeAt = cam.project(base + V3(0f, cardH * 0.75f, 0f))
    val br = max(9f * density, cam.pixelsPerUnit(base) * 0.03f)
    drawCircle(Castle.Ink, br, badgeAt)
    drawCircle(Castle.Gold, br, badgeAt, style = Stroke(1.5f))
    drawCentredText(measurer, s.cardCount.toString(), badgeAt, br * 1.1f, Castle.GoldPale, bold = true)
}

private fun DrawScope.drawBooks(cam: Camera, s: SceneSeat, angle: Float, measurer: TextMeasurer) {
    if (s.books.isEmpty()) return
    val n = s.books.size
    val tangent = V3.onCircle(angle + PI.toFloat() / 2, 1f, 0f)
    val spacing = min(0.075f, 0.5f / n)
    val yaw = angle + PI.toFloat() / 2
    for ((i, label) in s.books.withIndex()) {
        val off = i - (n - 1) / 2f
        val center = V3.onCircle(angle, 0.74f, 0.004f) + tangent * (off * spacing)
        val quad = flatCardCorners(center, 0.07f, 0.1f, yaw).map { cam.project(it) }
        drawCardFaceFlat(quad)
        val c = centroid(quad)
        val size = cam.pixelsPerUnit(center) * 0.045f
        drawCentredText(measurer, label, c, size, Castle.Ink, bold = true)
    }
}

private fun DrawScope.drawPond(cam: Camera, count: Int, measurer: TextMeasurer) {
    val shown = min(count, 18)
    val rnd = java.util.Random(1234)
    repeat(shown) {
        val a = rnd.nextFloat() * TAU
        val r = kotlin.math.sqrt(rnd.nextFloat()) * 0.3f
        val center = V3.onCircle(a, r, 0.004f + it * 0.0008f)
        val quad = flatCardCorners(center, 0.11f, 0.16f, rnd.nextFloat() * TAU).map { cam.project(it) }
        drawCardBack(quad, outline = 1.2f)
    }
    val labelAt = cam.project(V3(0f, 0f, 0.44f))
    val text = if (count > 0) "Pond · $count" else "Pond is empty"
    val size = cam.pixelsPerUnit(V3(0f, 0f, 0.44f)) * 0.05f
    val layout = measurer.measure(text, TextStyle(fontSize = (size / density / fontScale).sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Castle.GoldPale))
    drawRoundRect(Color(0x99000000), Offset(labelAt.x - layout.size.width / 2f - 10, labelAt.y - layout.size.height / 2f - 3), Size(layout.size.width + 20f, layout.size.height + 6f), CornerRadius(12f))
    drawText(layout, topLeft = Offset(labelAt.x - layout.size.width / 2f, labelAt.y - layout.size.height / 2f))
}

private fun DrawScope.drawBubble(cam: Camera, angle: Float, text: String, measurer: TextMeasurer, scale: Float) {
    val h = head(angle)
    val c = cam.project(h)
    val r = cam.pixelsPerUnit(h) * 0.15f * scale
    val fontPx = (r * 0.4f).coerceIn(12.sp.toPx(), 17.sp.toPx())
    val layout = measurer.measure(
        text,
        TextStyle(fontSize = (fontPx / density / fontScale).sp, color = Castle.Ink, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
        constraints = Constraints(maxWidth = (size.width * 0.22f).toInt()),
    )
    val w = layout.size.width + fontPx
    val hgt = layout.size.height + fontPx * 0.6f
    // Put the bubble above the head, nudged toward the screen centre so it stays on screen.
    val onRight = c.x < size.width / 2
    var x = if (onRight) c.x + r * 1.35f else c.x - r * 1.35f - w
    x = x.coerceIn(4f, size.width - w - 4f)
    // Beside the head, on the side facing the middle of the screen, so it never hides the name.
    val y = (c.y - hgt / 2).coerceIn(4f, size.height - hgt - 4f)
    drawRoundRect(Castle.Parchment, Offset(x, y), Size(w, hgt), CornerRadius(hgt / 3))
    val edge = if (onRight) x + 1 else x + w - 1
    val tip = if (onRight) c.x + r * 1.05f else c.x - r * 1.05f
    val tail = Path().apply {
        moveTo(edge, c.y - fontPx * 0.35f)
        lineTo(edge, c.y + fontPx * 0.35f)
        lineTo(tip, c.y)
        close()
    }
    drawPath(tail, Castle.Parchment)
    drawText(layout, topLeft = Offset(x + (w - layout.size.width) / 2, y + (hgt - layout.size.height) / 2))
}

private fun DrawScope.drawCentredText(measurer: TextMeasurer, text: String, center: Offset, heightPx: Float, color: Color, bold: Boolean) {
    val layout = measurer.measure(
        text,
        TextStyle(
            fontSize = (heightPx / density / fontScale).sp,
            color = color,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontFamily = FontFamily.Serif,
        ),
    )
    drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f))
}
