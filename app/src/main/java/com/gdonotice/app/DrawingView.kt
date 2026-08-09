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
    // The shared board always keeps the unfolded Fold's landscape ratio.
    // A folded cover screen crops this canvas; it never rotates or stretches it.
    private val boardWidth = 1239
    private val boardHeight = 819
    private val file = File(context.filesDir, "board_$boardCode.png")
    private var boardColor = Color.rgb(26, 66, 47)
    private val chalkColors = intArrayOf(
        0xFFFDE7D0.toInt(), 0xFFDBAB7D.toInt(), 0xFFD99858.toInt(), 0xFFB58352.toInt(), 0xFFA4805C.toInt(),
        0xFFB8C8A4.toInt(), 0xFFC3C7A4.toInt(), 0xFFC7C4A3.toInt(), 0xFFCAC281.toInt(), 0xFF837D41.toInt(),
        0xFF8DA198.toInt(), 0xFF709B87.toInt(), 0xFF587063.toInt(), 0xFFB0BFB8.toInt(), 0xFFAED5B8.toInt(),
        0xFF8593AE.toInt(), 0xFFAEBEDF.toInt(), 0xFF92BCD5.toInt(), 0xFF5D668F.toInt(), 0xFF494F67.toInt(),
        0xFFBF9EA7.toInt(), 0xFFDC8EA6.toInt(), 0xFF936572.toInt(), 0xFFC69A99.toInt(), 0xFFE28A88.toInt(),
        0xFFF87875.toInt(), 0xFFB7664B.toInt(), 0xFFDA6E54.toInt(), 0xFFB45632.toInt(), 0xFFC2551A.toInt(),
        Color.WHITE, Color.BLACK
    )
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
        setBackgroundColor(boardColor)
        contentDescription = "손가락으로 그리는 칠판"
        bitmap = Bitmap.createBitmap(boardWidth, boardHeight, Bitmap.Config.ARGB_8888)
        board = Canvas(bitmap).apply {
            drawColor(boardColor)
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
                panning = !isExpanded()
                drawing = false
                lastCenterX = (event.getX(0) + event.getX(1)) / 2f
                lastCenterY = (event.getY(0) + event.getY(1)) / 2f
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    if (!isExpanded()) {
                        val centerX = (event.getX(0) + event.getX(1)) / 2f
                        val centerY = (event.getY(0) + event.getY(1)) / 2f
                        panX += centerX - lastCenterX
                        panY += centerY - lastCenterY
                        lastCenterX = centerX
                        lastCenterY = centerY
                        constrainPan()
                    }
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
        board.drawColor(boardColor)
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

    fun setBoardBackgroundColor(color: Int, shouldSave: Boolean) {
        if (color == boardColor) return
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (i in pixels.indices) pixels[i] = rebasePixel(pixels[i], boardColor, color)
        boardColor = color
        setBackgroundColor(color)
        bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        invalidate()
        if (shouldSave) save(false)
    }

    private fun rebasePixel(pixel: Int, oldBackground: Int, newBackground: Int): Int {
        val pr = Color.red(pixel).toFloat()
        val pg = Color.green(pixel).toFloat()
        val pb = Color.blue(pixel).toFloat()
        val br = Color.red(oldBackground).toFloat()
        val bg = Color.green(oldBackground).toFloat()
        val bb = Color.blue(oldBackground).toFloat()
        var bestColor = chalkColors[0]
        var bestAlpha = 0f
        var bestError = Float.MAX_VALUE
        for (candidate in chalkColors) {
            val dr = Color.red(candidate) - br
            val dg = Color.green(candidate) - bg
            val db = Color.blue(candidate) - bb
            val length = dr * dr + dg * dg + db * db
            if (length < 1f) continue
            val alpha = (((pr - br) * dr + (pg - bg) * dg + (pb - bb) * db) / length).coerceIn(0f, 1f)
            val er = pr - (br + alpha * dr)
            val eg = pg - (bg + alpha * dg)
            val eb = pb - (bb + alpha * db)
            val error = er * er + eg * eg + eb * eb
            if (error < bestError) {
                bestError = error
                bestAlpha = alpha
                bestColor = candidate
            }
        }
        if (bestAlpha < 0.025f) return newBackground
        val inverse = 1f - bestAlpha
        return Color.rgb(
            (Color.red(bestColor) * bestAlpha + Color.red(newBackground) * inverse).toInt().coerceIn(0, 255),
            (Color.green(bestColor) * bestAlpha + Color.green(newBackground) * inverse).toInt().coerceIn(0, 255),
            (Color.blue(bestColor) * bestAlpha + Color.blue(newBackground) * inverse).toInt().coerceIn(0, 255)
        )
    }

    fun replaceFromBytes(bytes: ByteArray) {
        if (!::board.isInitialized) return
        val remote = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
        board.drawColor(boardColor)
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
            val distance = kotlin.math.abs(Color.red(color) - Color.red(boardColor)) +
                kotlin.math.abs(Color.green(color) - Color.green(boardColor)) +
                kotlin.math.abs(Color.blue(color) - Color.blue(boardColor))
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
            paint.color = boardColor
            paint.alpha = 255
            paint.strokeWidth = 30f * resources.displayMetrics.density / coverScale()
            board.drawLine(fromX, fromY, toX, toY, paint)
            return
        }

        // Keep the original screen-space chalk texture even when the fixed board is cropped/scaled.
        val density = resources.displayMetrics.density / coverScale()
        paint.color = chalkColor
        val distance = hypot(toX - fromX, toY - fromY)
        val steps = maxOf(1, (distance / (1.35f * density)).toInt())
        val directionX = if (distance == 0f) 0f else (toX - fromX) / distance
        val directionY = if (distance == 0f) 0f else (toY - fromY) / distance
        val normalX = -directionY
        val normalY = directionX
        val grains = maxOf(9, (strokeDp * 3.5f).toInt())
        repeat(steps) { step ->
            val progress = step.toFloat() / steps
            val x = fromX + (toX - fromX) * progress
            val y = fromY + (toY - fromY) * progress
            repeat(grains) {
                if (Random.nextFloat() >= 0.06f) {
                    val side = (Random.nextFloat() * 2f - 1f) * strokeDp * density
                    val along = (Random.nextFloat() * 2f - 1f) * 1.4f * density
                    val grainX = x + normalX * side + directionX * along
                    val grainY = y + normalY * side + directionY * along
                    paint.alpha = Random.nextInt(80, 246)
                    paint.strokeWidth = Random.nextFloat() * 0.55f * density + 0.22f * density
                    val length = Random.nextFloat() * 1.8f * density + 0.25f * density
                    board.drawLine(
                        grainX,
                        grainY,
                        grainX + directionX * length,
                        grainY + directionY * length,
                        paint
                    )
                }
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
        bitmap.compress(Bitmap.CompressFormat.JPEG, 94, it)
        it.toByteArray()
    }

    private fun restore(bytes: ByteArray) {
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
        board.drawColor(boardColor)
        board.drawBitmap(source, null, Rect(0, 0, bitmap.width, bitmap.height), null)
        source.recycle()
        invalidate()
        save(false)
    }

    private fun coverScale() = if (isExpanded()) {
        minOf(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
    } else {
        maxOf(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
    }

    private fun isExpanded() = resources.configuration.screenWidthDp >= 600

    private fun boardPoint(x: Float, y: Float): Pair<Float, Float> {
        val scale = coverScale()
        return Pair(
            (x - (width - bitmap.width * scale) / 2f - panX) / scale,
            (y - (height - bitmap.height * scale) / 2f - panY) / scale
        )
    }

    private fun constrainPan() {
        if (width == 0 || height == 0) return
        if (isExpanded()) {
            panX = 0f
            panY = 0f
            return
        }
        val scale = coverScale()
        val limitX = maxOf(0f, (bitmap.width * scale - width) / 2f)
        val limitY = maxOf(0f, (bitmap.height * scale - height) / 2f)
        panX = panX.coerceIn(-limitX, limitX)
        panY = panY.coerceIn(-limitY, limitY)
    }
}
