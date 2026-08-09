package com.gdonotice.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )

        val auth = FirebaseAuth.getInstance()
        val boardDocument = FirebaseFirestore.getInstance().collection("boards").document("shared")
        lateinit var board: DrawingView
        board = DrawingView(this) { bytes ->
            BoardWidget.updateAll(this)
            val user = auth.currentUser ?: return@DrawingView
            boardDocument.set(
                mapOf(
                    "image" to Blob.fromBytes(bytes),
                    "updatedAt" to Timestamp.now(),
                    "updatedBy" to user.uid
                )
            )
        }
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(36, 85, 66)) }
        root.addView(board, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))

        val toolbar = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
            elevation = 10.dp.toFloat()
            setPadding(10.dp, 8.dp, 10.dp, 8.dp)
            background = rounded(Color.argb(235, 250, 248, 241), 24f)
        }

        val colors = listOf(
            "흰색" to Color.rgb(243, 238, 213),
            "노랑" to Color.rgb(248, 214, 92),
            "분홍" to Color.rgb(244, 153, 177),
            "파랑" to Color.rgb(137, 198, 235),
            "연두" to Color.rgb(166, 218, 126)
        )
        val colorButtons = mutableListOf<View>()
        lateinit var eraser: View
        colors.forEachIndexed { index, (name, color) ->
            val dot = View(this).apply {
                contentDescription = "$name 분필"
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    setStroke(2.dp, if (index == 0) Color.DKGRAY else Color.TRANSPARENT)
                }
                alpha = if (index == 0) 1f else 0.62f
                setOnClickListener {
                    board.setColor(color)
                    colorButtons.forEach { it.alpha = 0.62f }
                    alpha = 1f
                    eraser.alpha = 0.62f
                }
            }
            colorButtons += dot
            toolbar.addView(dot, LinearLayout.LayoutParams(28.dp, 28.dp).apply { marginEnd = 7.dp })
        }

        toolbar.addView(View(this).apply { setBackgroundColor(Color.LTGRAY) }, LinearLayout.LayoutParams(1.dp, 24.dp).apply {
            marginStart = 2.dp
            marginEnd = 9.dp
        })

        eraser = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            rotation = -12f
            contentDescription = "부분 지우개"
            background = rounded(Color.rgb(219, 195, 156), 5f)
            addView(View(this@MainActivity).apply { setBackgroundColor(Color.rgb(225, 207, 177)) }, LinearLayout.LayoutParams(-1, 0, 0.58f))
            addView(View(this@MainActivity).apply { setBackgroundColor(Color.rgb(112, 72, 48)) }, LinearLayout.LayoutParams(-1, 0, 0.42f))
            alpha = 0.62f
            setOnClickListener {
                board.setEraser()
                colorButtons.forEach { it.alpha = 0.62f }
                alpha = 1f
            }
        }
        toolbar.addView(eraser, LinearLayout.LayoutParams(35.dp, 27.dp).apply { marginEnd = 10.dp })

        toolbar.addView(View(this).apply { setBackgroundColor(Color.LTGRAY) }, LinearLayout.LayoutParams(1.dp, 24.dp).apply {
            marginEnd = 7.dp
        })

        val thicknesses = listOf(2.5f, 4.5f, 7f)
        val thicknessButtons = mutableListOf<TextView>()
        thicknesses.forEachIndexed { index, width ->
            val line = TextView(this).apply {
                text = "—"
                gravity = Gravity.CENTER
                textSize = 11f + index * 5f
                setTextColor(Color.rgb(35, 48, 42))
                contentDescription = "분필 두께 ${index + 1}단계"
                alpha = if (index == 2) 1f else 0.42f
                setOnClickListener {
                    board.setStrokeWidth(width)
                    thicknessButtons.forEach { it.alpha = 0.42f }
                    alpha = 1f
                }
            }
            thicknessButtons += line
            toolbar.addView(line, LinearLayout.LayoutParams(31.dp, 30.dp))
        }

        toolbar.addView(View(this).apply { setBackgroundColor(Color.LTGRAY) }, LinearLayout.LayoutParams(1.dp, 24.dp).apply {
            marginStart = 5.dp
            marginEnd = 5.dp
        })
        toolbar.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_delete_all)
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "전체 지우기"
            setPadding(7.dp, 7.dp, 7.dp, 7.dp)
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage("그림을 모두 지울까요?")
                    .setNegativeButton("취소", null)
                    .setPositiveButton("전체 지우기") { _, _ -> board.clear() }
                    .show()
            }
        }, LinearLayout.LayoutParams(36.dp, 36.dp))

        root.addView(toolbar, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = 24.dp
        })
        setContentView(root)

        auth.signInAnonymously().addOnSuccessListener {
            boardDocument.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "동기화 연결을 확인해 주세요.", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                if (snapshot?.getString("updatedBy") == auth.currentUser?.uid) return@addSnapshotListener
                snapshot?.getBlob("image")?.toBytes()?.let {
                    board.replaceFromBytes(it)
                    BoardWidget.updateAll(this)
                }
            }
        }.addOnFailureListener {
            Toast.makeText(this, "공유 칠판에 연결하지 못했습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun rounded(color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * resources.displayMetrics.density
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
