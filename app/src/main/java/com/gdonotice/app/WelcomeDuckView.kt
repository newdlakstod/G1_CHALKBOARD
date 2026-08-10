package com.gdonotice.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

class WelcomeDuckView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFF4DE.toInt()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        val s = minOf(width, height).toFloat()
        val ox = (width - s) / 2f
        val oy = (height - s) / 2f
        paint.strokeWidth = s * .014f
        fun p(x: Float, y: Float) = Pair(ox + x * s, oy + y * s)
        val body = Path().apply {
            val a = p(.30f, .42f); moveTo(a.first, a.second)
            cubicTo(ox+.18f*s, oy+.58f*s, ox+.24f*s, oy+.78f*s, ox+.52f*s, oy+.75f*s)
            cubicTo(ox+.78f*s, oy+.72f*s, ox+.75f*s, oy+.50f*s, ox+.58f*s, oy+.43f*s)
            cubicTo(ox+.48f*s, oy+.39f*s, ox+.39f*s, oy+.37f*s, ox+.30f*s, oy+.42f*s)
        }
        canvas.drawPath(body, paint)
        canvas.drawCircle(ox+.58f*s, oy+.27f*s, .16f*s, paint)
        val beak = Path().apply { moveTo(ox+.72f*s, oy+.25f*s); lineTo(ox+.92f*s, oy+.31f*s); lineTo(ox+.72f*s, oy+.35f*s) }
        canvas.drawPath(beak, paint)
        canvas.drawCircle(ox+.62f*s, oy+.23f*s, .012f*s, paint)
        canvas.drawLine(ox+.42f*s, oy+.50f*s, ox+.60f*s, oy+.66f*s, paint)
        canvas.drawLine(ox+.60f*s, oy+.66f*s, ox+.63f*s, oy+.48f*s, paint)
        canvas.drawRect(ox+.34f*s, oy+.49f*s, ox+.55f*s, oy+.70f*s, paint)
        canvas.drawLine(ox+.40f*s, oy+.75f*s, ox+.36f*s, oy+.90f*s, paint)
        canvas.drawLine(ox+.61f*s, oy+.74f*s, ox+.70f*s, oy+.88f*s, paint)
        canvas.drawLine(ox+.31f*s, oy+.91f*s, ox+.40f*s, oy+.91f*s, paint)
        canvas.drawLine(ox+.67f*s, oy+.89f*s, ox+.77f*s, oy+.89f*s, paint)
    }
}
