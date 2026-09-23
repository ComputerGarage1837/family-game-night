package com.familygamenight.app.ui.table

import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class V3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: V3) = V3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = V3(x * s, y * s, z * s)
    infix fun dot(o: V3) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: V3) = V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun length() = sqrt(this dot this)
    fun normalized() = this * (1f / length())

    companion object {
        val UP = V3(0f, 1f, 0f)
        /** A point on a horizontal circle around the table centre. Angle π/2 points at the camera. */
        fun onCircle(angle: Float, radius: Float, y: Float) = V3(cos(angle) * radius, y, sin(angle) * radius)
    }
}

/**
 * A simple pinhole camera looking down at the round table from just behind the viewer's seat.
 * World units: the table top has radius 1 and sits at y = 0.
 */
class Camera(val width: Float, val height: Float) {
    val eye = V3(0f, 1.55f, 2.3f)
    private val target = V3(0f, 0f, -0.1f)
    private val fwd = (target - eye).normalized()
    private val right = (fwd cross V3.UP).normalized()
    private val up = right cross fwd

    val focal: Float
    private val cx: Float
    private val cy: Float

    init {
        // Frame the far chairs and both sides of the table. The near edge may run off the bottom:
        // that's where the player's own hand floats anyway.
        val probe = buildList {
            for (i in 0 until 48) {
                val a = (i / 48f) * 2f * Math.PI.toFloat()
                add(V3.onCircle(a, 1.05f, -0.13f))
                if (sin(a) < 0.45f) add(V3.onCircle(a, 1.24f, 0.72f)) // tops of the chairs
            }
        }
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var minY = Float.MAX_VALUE
        for (p in probe) {
            val (x, y) = raw(p)
            minX = min(minX, x); maxX = max(maxX, x); minY = min(minY, y)
        }
        val (_, sideY) = raw(V3(1.05f, -0.13f, 0.35f))
        // Leave the top strip for the banner; the table's widest point should sit about 3/4 down.
        val topMargin = height * 0.15f
        focal = min(width * 0.95f / (maxX - minX), (height * 0.74f - topMargin) / (sideY - minY))
        cx = width / 2f - (minX + maxX) / 2f * focal
        cy = topMargin - minY * focal
    }

    private fun raw(p: V3): Pair<Float, Float> {
        val d = p - eye
        val z = d dot fwd
        return (d dot right) / z to -(d dot up) / z
    }

    fun depth(p: V3) = (p - eye) dot fwd

    fun project(p: V3): Offset {
        val (x, y) = raw(p)
        return Offset(cx + x * focal, cy + y * focal)
    }

    /** Pixels per world unit at point [p] – for sizing things drawn "at" a 3D position. */
    fun pixelsPerUnit(p: V3) = focal / depth(p)
}
