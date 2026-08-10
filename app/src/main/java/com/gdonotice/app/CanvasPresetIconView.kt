package com.gdonotice.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

class CanvasPresetIconView(context: Context, val preset: CanvasPreset) : View(context) {
    var selectedPreset = false
        set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f * resources.displayMetrics.density }

    override fun onDraw(canvas: Canvas) {
        paint.color = if (selectedPreset) SketchbookDesign.COBALT else SketchbookDesign.INK
        val pad = width * .22f
        val left = pad; val top = height * .16f; val right = width - pad; val bottom = height * .84f
        if (preset.kind == CanvasKind.DOCUMENT) {
            val fold = (right - left) * .25f
            val path = Path().apply {
                moveTo(left, top); lineTo(right - fold, top); lineTo(right, top + fold)
                lineTo(right, bottom); lineTo(left, bottom); close()
                moveTo(right - fold, top); lineTo(right - fold, top + fold); lineTo(right, top + fold)
            }
            canvas.drawPath(path, paint)
        } else {
            val ratio = if (preset == CanvasPreset.CUSTOM) 1f else preset.ratio
            val maxW = right - left; val maxH = bottom - top
            val w = if (ratio >= 1f) maxW else maxH * ratio
            val h = if (ratio >= 1f) maxW / ratio else maxH
            val cx = width / 2f; val cy = height / 2f
            canvas.drawRoundRect(cx-w/2, cy-h/2, cx+w/2, cy+h/2, 10f, 10f, paint)
        }
        if (selectedPreset) {
            paint.style = Paint.Style.FILL; canvas.drawCircle(width*.78f, height*.20f, width*.07f, paint); paint.style = Paint.Style.STROKE
        }
    }
}
