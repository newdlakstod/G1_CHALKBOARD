package com.gdonotice.app

object CanvasSizeInteraction {
    /** Reference-canvas coordinates. The bottom action area is deliberately full-width. */
    fun shouldCreate(y: Float): Boolean = y >= 560f

    fun presetIndex(x: Float, y: Float): Int? {
        if (y !in 90f..590f) return null
        val column = (x / 130f).toInt().coerceIn(0, 2)
        val row = if (y < 250f) 0 else 1
        return row * 3 + column
    }
}
