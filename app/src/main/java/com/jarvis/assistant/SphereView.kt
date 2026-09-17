package com.jarvis.assistant

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * A rotating particle sphere, styled after the reference JARVIS UI: a cloud
 * of dots on a sphere, denser-looking at the silhouette edge, tinted blue in
 * normal mode and red in serious mode, pulsing with live mic amplitude.
 */
class SphereView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private data class Pt(val theta: Double, val phi: Double, val jitter: Double, val size: Float)

    private val points = mutableListOf<Pt>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var rotation = 0.0
    private var energy = 0.08f
    private var targetEnergy = 0.08f
    private var serious = false

    init {
        val rnd = Random(42)
        repeat(1500) {
            val u = rnd.nextDouble()
            val v = rnd.nextDouble()
            val theta = 2 * Math.PI * u
            val phi = acos(2 * v - 1)
            points.add(Pt(theta, phi, (rnd.nextDouble() - 0.5) * 0.06, rnd.nextFloat() * 1.6f + 0.5f))
        }
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 16
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                energy += (targetEnergy - energy) * 0.12f
                rotation += 0.006 + energy * 0.02
                invalidate()
            }
        }.start()
    }

    /** 0f..1f, how "active" the sphere should look (mic level, speaking, etc). */
    fun setEnergy(e: Float) {
        targetEnergy = e.coerceIn(0f, 1f)
    }

    fun setSerious(s: Boolean) {
        serious = s
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val cx = w / 2f
        val cy = h / 2f
        val radius = min(w, h) * 0.42f
        val r: Int; val g: Int; val b: Int
        if (serious) { r = 255; g = 59; b = 59 } else { r = 79; g = 216; b = 255 }

        for (p in points) {
            val theta = p.theta + rotation
            val rr = 1.0 + p.jitter + energy * 0.05
            val x = rr * sin(p.phi) * cos(theta)
            val y = rr * cos(p.phi)
            val z = rr * sin(p.phi) * sin(theta)
            val scale = ((z + 1.6) / 2.6).toFloat().coerceIn(0f, 1f)
            val px = cx + (x * radius).toFloat()
            val py = cy + (y * radius).toFloat()
            val alpha = (60 + scale * 180).toInt().coerceIn(0, 255)
            val dotRadius = radius * 0.012f * p.size * (0.6f + scale * 0.8f) * (1f + energy * 1.4f)
            paint.color = Color.argb(alpha, r, g, b)
            canvas.drawCircle(px, py, dotRadius, paint)
        }
    }
}
