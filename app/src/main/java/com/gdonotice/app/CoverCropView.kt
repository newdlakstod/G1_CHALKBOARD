package com.gdonotice.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.atan2

class CoverCropView(context: Context, private val source: Bitmap) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var lastDistance = 0f
    private var rotation = 0f
    private var lastAngle = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (oldw != 0 || w == 0 || h == 0) return
        scale = maxOf(w.toFloat() / source.width, h.toFloat() / source.height)
        offsetX = (w - source.width * scale) / 2f
        offsetY = (h - source.height * scale) / 2f
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.rgb(36, 85, 66))
        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)
        canvas.rotate(rotation, source.width / 2f, source.height / 2f)
        canvas.drawBitmap(source, 0f, 0f, paint)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_POINTER_DOWN -> if (event.pointerCount >= 2) {
                lastDistance = distance(event)
                lastAngle = angle(event)
            }
            MotionEvent.ACTION_MOVE -> if (event.pointerCount >= 2) {
                val distance = distance(event)
                if (lastDistance > 0f) {
                    val centerX = (event.getX(0) + event.getX(1)) / 2f
                    val centerY = (event.getY(0) + event.getY(1)) / 2f
                    val next = (scale * distance / lastDistance).coerceIn(0.2f, 8f)
                    offsetX = centerX - (centerX - offsetX) * next / scale
                    offsetY = centerY - (centerY - offsetY) * next / scale
                    scale = next
                }
                val angle = angle(event)
                rotation += ((angle - lastAngle + 540f) % 360f) - 180f
                lastAngle = angle
                lastDistance = distance
            } else {
                offsetX += event.x - lastX
                offsetY += event.y - lastY
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> lastDistance = 0f
        }
        invalidate()
        return true
    }

    fun export(): Bitmap {
        val result = Bitmap.createBitmap(600, 792, Bitmap.Config.ARGB_8888)
        Canvas(result).apply {
            drawColor(Color.rgb(36, 85, 66))
            val factor = 600f / width
            scale(factor, factor)
            translate(offsetX, offsetY)
            scale(scale, scale)
            rotate(rotation, source.width / 2f, source.height / 2f)
            drawBitmap(source, 0f, 0f, paint)
        }
        return result
    }

    private fun distance(event: MotionEvent) = hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))
    private fun angle(event: MotionEvent) = Math.toDegrees(
        atan2(event.getY(1) - event.getY(0), event.getX(1) - event.getX(0)).toDouble()
    ).toFloat()
}
