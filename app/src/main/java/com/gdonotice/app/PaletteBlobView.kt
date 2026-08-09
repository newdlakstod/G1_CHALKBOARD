package com.gdonotice.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class PaletteBlobView(context: Context, private val color: Int, private val variant: Int, private val selected: Boolean) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = this@PaletteBlobView.color
        style = Paint.Style.FILL
    }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(31, 31, 31)
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 3f
    }

    override fun onDraw(canvas: Canvas) {
        val random = Random(variant * 7919 + 17)
        val count = 14
        val points = Array(count) { index ->
            val angle = -PI / 2 + index * 2 * PI / count
            val radius = 0.78f + random.nextFloat() * 0.2f
            val rx = width * 0.46f * radius
            val ry = height * 0.46f * (0.9f + random.nextFloat() * 0.14f)
            Pair(width / 2f + cos(angle).toFloat() * rx, height / 2f + sin(angle).toFloat() * ry)
        }
        val path = Path()
        val first = points[0]
        val last = points.last()
        path.moveTo((last.first + first.first) / 2f, (last.second + first.second) / 2f)
        points.forEachIndexed { index, point ->
            val next = points[(index + 1) % count]
            path.quadTo(point.first, point.second, (point.first + next.first) / 2f, (point.second + next.second) / 2f)
        }
        path.close()
        canvas.drawPath(path, fill)
        if (selected) canvas.drawPath(path, border)
    }
}
