package com.gdonotice.app

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.res.ResourcesCompat
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File

class WidgetConfigActivity : ComponentActivity() {
    private val db by lazy { FirebaseFirestore.getInstance() }
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return finish()

        val columns = if (resources.configuration.screenWidthDp >= 600) 6 else 3
        val grid = GridLayout(this).apply { columnCount = columns }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp, 30.dp, 20.dp, 30.dp)
            setBackgroundColor(Color.rgb(243, 240, 237))
            addView(TextView(this@WidgetConfigActivity).apply {
                text = "위젯 칠판 선택"
                textSize = 26f
                setTextColor(Color.rgb(31, 31, 31))
                typeface = ResourcesCompat.getFont(this@WidgetConfigActivity, R.font.pretendard_regular)
            })
            addView(grid, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 18.dp })
        }
        setContentView(ScrollView(this).apply { addView(root) })

        getSharedPreferences("board", MODE_PRIVATE).getStringSet("codes", emptySet()).orEmpty().sorted().forEach { code ->
            db.collection("boards").document(code).get().addOnSuccessListener { snapshot ->
                val bytes = (snapshot.getBlob("cover") ?: snapshot.getBlob("image"))?.toBytes()
                val cardWidth = (resources.displayMetrics.widthPixels - 40.dp - (columns * 6).dp) / columns
                grid.addView(FrameLayout(this).apply {
                    background = rounded(Color.rgb(36, 85, 66), 20f)
                    clipToOutline = true
                    elevation = 3.dp.toFloat()
                    addView(ImageView(this@WidgetConfigActivity).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        bytes?.let { setImageBitmap(BitmapFactory.decodeByteArray(it, 0, it.size)) }
                    }, FrameLayout.LayoutParams(-1, -1))
                    addView(TextView(this@WidgetConfigActivity).apply {
                        text = snapshot.getString("name") ?: code
                        textSize = if (columns == 6) 12f else 13f
                        setTextColor(Color.WHITE)
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(10.dp, 5.dp, 10.dp, 5.dp)
                        typeface = ResourcesCompat.getFont(this@WidgetConfigActivity, R.font.pretendard_regular)
                        background = rounded(Color.argb(205, 18, 28, 24), 0f)
                    }, FrameLayout.LayoutParams(-1, 46.dp, Gravity.BOTTOM))
                    setOnClickListener { select(code, bytes) }
                    setOnTouchListener { target, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> target.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).start()
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> target.animate().scaleX(1f).scaleY(1f).setDuration(130).start()
                        }
                        false
                    }
                }, GridLayout.LayoutParams().apply {
                    width = cardWidth
                    height = (cardWidth * 1.32f).toInt()
                    setMargins(3.dp, 6.dp, 3.dp, 6.dp)
                })
            }
        }
    }

    private fun select(code: String, bytes: ByteArray?) {
        getSharedPreferences("widgets", MODE_PRIVATE).edit().putString("board_$widgetId", code).apply()
        val file = File(filesDir, "widget_$widgetId.jpg")
        if (bytes == null) file.delete() else file.writeBytes(bytes)
        BoardWidget.update(this, AppWidgetManager.getInstance(this), widgetId)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    private fun rounded(color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * resources.displayMetrics.density
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
