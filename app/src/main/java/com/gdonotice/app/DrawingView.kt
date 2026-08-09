package com.gdonotice.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import kotlin.math.hypot
import kotlin.random.Random

class DrawingView(
    context: Context,
    boardCode: String,
    private val onSaved: (ByteArray, Boolean) -> Unit
) : View(context) {
    private val file = File(context.filesDir, "board_$boardCode.png")
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
    private var drawing = false
    private var panning = false
    private var panX = 0f
    private var panY = 0f
    private var lastCenterX = 0f
    private var lastCenterY = 0f
    private val undoHistory = ArrayDeque<ByteArray>()
    private val redoHistory = ArrayDeque<ByteArray>()

    init {
        setBackgroundColor(Color.rgb(36, 85, 66))
        contentDescription = "손가락으로 그리는 칠판"
        bitmap = Bitmap.createBitmap(819, 1239, Bitmap.Config.ARGB_8888)
        board = Canvas(bitmap).apply {
            drawColor(Color.rgb(36, 85, 66))
            if (file.exists()) BitmapFactory.decodeFile(file.path)?.let {
                drawBitmap(it, null, Rect(0, 0, bitmap.width, bitmap.height), null)
                it.recycle()
            }
        }
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        constrainPan()
    }

    override fun onDraw(canvas: Canvas) {
        val scale = coverScale()
        canvas.save()
        canvas.translate((width - bitmap.width * scale) / 2f + panX, (height - bitmap.height * scale) / 2f + panY)
        canvas.scale(scale, scale)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                drawing = false
                val point = boardPoint(event.x, event.y)
                lastX = point.first
                lastY = point.second
            }
            MotionEvent.ACTION_POINTER_DOWN -> if (event.pointerCount >= 2) {
                panning = true
                drawing = false
                lastCenterX = (event.getX(0) + event.getX(1)) / 2f
                lastCenterY = (event.getY(0) + event.getY(1)) / 2f
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    val centerX = (event.getX(0) + event.getX(1)) / 2f
                    val centerY = (event.getY(0) + event.getY(1)) / 2f
                    panX += centerX - lastCenterX
                    panY += centerY - lastCenterY
                    lastCenterX = centerX
                    lastCenterY = centerY
                    constrainPan()
                } else if (!panning) {
                    if (!drawing) remember()
                    drawing = true
                    val point = boardPoint(event.x, event.y)
                    drawChalk(lastX, lastY, point.first, point.second)
                    lastX = point.first
                    lastY = point.second
                }
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                parent.requestDisallowInterceptTouchEvent(false)
                if (drawing) save(false)
                drawing = false
                panning = false
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                drawing = false
                panning = false
                parent.requestDisallowInterceptTouchEvent(false)
            }
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
        save(true)
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
        board.drawBitmap(remote, null, Rect(0, 0, bitmap.width, bitmap.height), null)
        remote.recycle()
        FileOutputStream(file).use { it.write(bytes) }
        invalidate()
    }

    fun mergeFromBytes(bytes: ByteArray) {
        if (!::board.isInitialized) return
        val remote = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
        val scaled = Bitmap.createScaledBitmap(remote, bitmap.width, bitmap.height, true)
        val pixels = IntArray(bitmap.width * bitmap.height)
        val local = IntArray(bitmap.width * bitmap.height)
        scaled.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        bitmap.getPixels(local, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (i in pixels.indices) {
            val color = pixels[i]
            val distance = kotlin.math.abs(Color.red(color) - 36) +
                kotlin.math.abs(Color.green(color) - 85) + kotlin.math.abs(Color.blue(color) - 66)
            if (distance > 45) local[i] = color
        }
        bitmap.setPixels(local, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
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

    private fun save(cleared: Boolean) {
        val bytes = snapshot()
        FileOutputStream(file).use { it.write(bytes) }
        onSaved(bytes, cleared)
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
        board.drawBitmap(source, null, Rect(0, 0, bitmap.width, bitmap.height), null)
        source.recycle()
        invalidate()
        save(false)
    }

    private fun coverScale() = maxOf(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)

    private fun boardPoint(x: Float, y: Float): Pair<Float, Float> {
        val scale = coverScale()
        return Pair(
            (x - (width - bitmap.width * scale) / 2f - panX) / scale,
            (y - (height - bitmap.height * scale) / 2f - panY) / scale
        )
    }

    private fun constrainPan() {
        if (width == 0 || height == 0) return
        val scale = coverScale()
        val limitX = maxOf(0f, (bitmap.width * scale - width) / 2f)
        val limitY = maxOf(0f, (bitmap.height * scale - height) / 2f)
        panX = panX.coerceIn(-limitX, limitX)
        panY = panY.coerceIn(-limitY, limitY)
    }
}
