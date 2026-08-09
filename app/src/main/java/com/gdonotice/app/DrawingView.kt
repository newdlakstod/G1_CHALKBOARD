package com.gdonotice.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import kotlin.math.hypot
import kotlin.random.Random

class DrawingView(context: Context, private val onSaved: (ByteArray) -> Unit) : View(context) {
    private val file = File(context.filesDir, "board.png")
    private var chalkColor = Color.rgb(243, 238, 213)
    private var erasing = false
    private var strokeDp = 7f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 8f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private lateinit var bitmap: Bitmap
    private lateinit var board: Canvas
    private var lastX = 0f
    private var lastY = 0f
    private val undoHistory = ArrayDeque<ByteArray>()
    private val redoHistory = ArrayDeque<ByteArray>()

    init {
        setBackgroundColor(Color.rgb(36, 85, 66))
        contentDescription = "손가락으로 그리는 칠판"
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        if (width == 0 || height == 0) return
        val previous = if (file.exists()) BitmapFactory.decodeFile(file.path) else null
        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        board = Canvas(bitmap).apply {
            drawColor(Color.rgb(36, 85, 66))
            previous?.let { drawBitmap(it, null, android.graphics.Rect(0, 0, width, height), null) }
        }
        previous?.recycle()
    }

    override fun onDraw(canvas: Canvas) {
        if (::bitmap.isInitialized) canvas.drawBitmap(bitmap, 0f, 0f, null)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                remember()
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                drawChalk(lastX, lastY, event.x, event.y)
                lastX = event.x
                lastY = event.y
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                drawChalk(lastX, lastY, event.x, event.y)
                parent.requestDisallowInterceptTouchEvent(false)
                save()
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> parent.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun clear() {
        if (!::board.isInitialized) return
        remember()
        board.drawColor(Color.rgb(36, 85, 66))
        invalidate()
        save()
    }

    fun setColor(color: Int) {
        chalkColor = color
        erasing = false
    }

    fun setEraser() {
        erasing = true
    }

    fun setStrokeWidth(widthDp: Float) {
        strokeDp = widthDp
    }

    fun replaceFromBytes(bytes: ByteArray) {
        if (!::board.isInitialized) return
        val remote = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
        board.drawColor(Color.rgb(36, 85, 66))
        board.drawBitmap(remote, null, android.graphics.Rect(0, 0, width, height), null)
        remote.recycle()
        FileOutputStream(file).use { it.write(bytes) }
        invalidate()
    }

    fun mergeFromBytes(bytes: ByteArray) {
        if (!::board.isInitialized) return
        val remote = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
        val scaled = Bitmap.createScaledBitmap(remote, width, height, true)
        val pixels = IntArray(width * height)
        val local = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.getPixels(local, 0, width, 0, 0, width, height)
        for (i in pixels.indices) {
            val color = pixels[i]
            val distance = kotlin.math.abs(Color.red(color) - 36) +
                kotlin.math.abs(Color.green(color) - 85) + kotlin.math.abs(Color.blue(color) - 66)
            if (distance > 45) local[i] = color
        }
        bitmap.setPixels(local, 0, width, 0, 0, width, height)
        if (scaled !== remote) scaled.recycle()
        remote.recycle()
        invalidate()
    }

    fun undo() {
        if (undoHistory.isEmpty()) return
        redoHistory.addLast(snapshot())
        restore(undoHistory.removeLast())
    }

    fun redo() {
        if (redoHistory.isEmpty()) return
        undoHistory.addLast(snapshot())
        restore(redoHistory.removeLast())
    }

    private fun drawChalk(fromX: Float, fromY: Float, toX: Float, toY: Float) {
        if (erasing) {
            paint.color = Color.rgb(36, 85, 66)
            paint.alpha = 255
            paint.strokeWidth = 30f * resources.displayMetrics.density
            board.drawLine(fromX, fromY, toX, toY, paint)
            return
        }

        val density = resources.displayMetrics.density
        paint.color = chalkColor
        paint.alpha = 28
        paint.strokeWidth = strokeDp * 1.85f * density
        board.drawLine(fromX, fromY, toX, toY, paint)
        paint.alpha = 90
        paint.strokeWidth = maxOf(0.8f, strokeDp * 0.23f) * density
        board.drawLine(fromX, fromY, toX, toY, paint)

        val distance = hypot(toX - fromX, toY - fromY)
        val steps = maxOf(1, (distance / (2f * density)).toInt())
        repeat(steps) { step ->
            val progress = step.toFloat() / steps
            val x = fromX + (toX - fromX) * progress
            val y = fromY + (toY - fromY) * progress
            repeat(5) {
                val spread = strokeDp * density
                paint.alpha = Random.nextInt(55, 210)
                board.drawCircle(
                    x + Random.nextFloat() * spread * 2 - spread,
                    y + Random.nextFloat() * spread * 2 - spread,
                    Random.nextFloat() * strokeDp * 0.18f * density + 0.25f * density,
                    paint
                )
            }
        }
    }

    private fun save() {
        val bytes = snapshot()
        FileOutputStream(file).use { it.write(bytes) }
        onSaved(bytes)
    }

    private fun remember() {
        if (!::bitmap.isInitialized) return
        undoHistory.addLast(snapshot())
        while (undoHistory.size > 20) undoHistory.removeFirst()
        redoHistory.clear()
    }

    private fun snapshot(): ByteArray = ByteArrayOutputStream().use {
        bitmap.compress(Bitmap.CompressFormat.JPEG, 82, it)
        it.toByteArray()
    }

    private fun restore(bytes: ByteArray) {
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
        board.drawColor(Color.rgb(36, 85, 66))
        board.drawBitmap(source, null, android.graphics.Rect(0, 0, width, height), null)
        source.recycle()
        invalidate()
        save()
    }
}
