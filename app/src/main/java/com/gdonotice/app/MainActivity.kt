package com.gdonotice.app

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val prefs by lazy { getSharedPreferences("board", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        if (auth.currentUser?.isAnonymous == true) auth.signOut()
        route()
    }

    private fun route() {
        val code = prefs.getString("code", null)
        when {
            auth.currentUser == null -> showLogin()
            code.isNullOrBlank() -> showBoardChooser()
            else -> showBoard(code)
        }
    }

    private fun showLogin() {
        val root = centeredColumn()
        root.addView(TextView(this).apply {
            text = "G1_CHALKBOARD"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })
        root.addView(Button(this).apply {
            text = "Google로 로그인"
            isAllCaps = false
            setOnClickListener { signInWithGoogle() }
        }, LinearLayout.LayoutParams(-1, 54.dp).apply { topMargin = 28.dp })
        setContentView(root)
    }

    private fun signInWithGoogle() = lifecycleScope.launch {
        try {
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(getString(R.string.default_web_client_id))
                .build()
            val result = CredentialManager.create(this@MainActivity).getCredential(
                this@MainActivity,
                GetCredentialRequest.Builder().addCredentialOption(option).build()
            )
            val credential = result.credential
            if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                error("지원하지 않는 로그인 응답")
            }
            val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                .addOnSuccessListener { route() }
                .addOnFailureListener { toast("로그인하지 못했습니다.") }
        } catch (_: Exception) {
            toast("로그인이 취소되었거나 실패했습니다.")
        }
    }

    private fun showBoardChooser() {
        val root = centeredColumn()
        root.addView(TextView(this).apply {
            text = auth.currentUser?.email ?: "로그인됨"
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })
        root.addView(Button(this).apply {
            text = "새 공유 칠판 만들기"
            isAllCaps = false
            setOnClickListener { createBoard() }
        }, LinearLayout.LayoutParams(-1, 54.dp).apply { topMargin = 20.dp })
        val codeInput = EditText(this).apply {
            hint = "초대 코드 입력"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setSingleLine()
        }
        root.addView(codeInput, LinearLayout.LayoutParams(-1, 54.dp).apply { topMargin = 12.dp })
        root.addView(Button(this).apply {
            text = "공유 칠판 참가"
            isAllCaps = false
            setOnClickListener { joinBoard(codeInput.text.toString()) }
        }, LinearLayout.LayoutParams(-1, 54.dp))
        setContentView(root)
    }

    private fun createBoard() {
        val user = auth.currentUser ?: return
        val code = UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
        db.collection("boards").document(code).set(
            mapOf("ownerId" to user.uid, "members" to mapOf(user.uid to true), "updatedAt" to Timestamp.now())
        ).addOnSuccessListener { enterBoard(code) }
            .addOnFailureListener { toast("칠판을 만들지 못했습니다.") }
    }

    private fun joinBoard(rawCode: String) {
        val user = auth.currentUser ?: return
        val code = rawCode.trim().uppercase()
        if (!code.matches(Regex("[A-Z0-9]{8}"))) {
            toast("8자리 초대 코드를 입력해 주세요.")
            return
        }
        val doc = db.collection("boards").document(code)
        doc.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) return@addOnSuccessListener toast("초대 코드를 찾을 수 없습니다.")
            doc.update("members.${user.uid}", true)
                .addOnSuccessListener { enterBoard(code) }
                .addOnFailureListener { toast("칠판에 참가하지 못했습니다.") }
        }.addOnFailureListener { toast("초대 코드를 확인하지 못했습니다.") }
    }

    private fun enterBoard(code: String) {
        prefs.edit().putString("code", code).apply()
        showBoard(code)
    }

    private fun showBoard(code: String) {
        val document = db.collection("boards").document(code)
        lateinit var board: DrawingView
        board = DrawingView(this) { bytes ->
            BoardWidget.updateAll(this)
            val user = auth.currentUser ?: return@DrawingView
            document.update(
                mapOf("image" to Blob.fromBytes(bytes), "updatedAt" to Timestamp.now(), "updatedBy" to user.uid)
            ).addOnFailureListener { toast("그림을 동기화하지 못했습니다.") }
        }
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(36, 85, 66)) }
        root.addView(board, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        root.addView(makeToolbar(board), FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = 24.dp
        })
        root.addView(TextView(this).apply {
            text = "초대 코드  $code"
            textSize = 13f
            setTextColor(Color.rgb(35, 48, 42))
            setPadding(12.dp, 7.dp, 12.dp, 7.dp)
            background = rounded(Color.argb(220, 250, 248, 241), 18f)
            setOnLongClickListener {
                prefs.edit().remove("code").apply()
                showBoardChooser()
                true
            }
        }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = 18.dp })
        setContentView(root)

        document.addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener toast("동기화 연결을 확인해 주세요.")
            if (snapshot?.getString("updatedBy") == auth.currentUser?.uid) return@addSnapshotListener
            snapshot?.getBlob("image")?.toBytes()?.let {
                board.replaceFromBytes(it)
                BoardWidget.updateAll(this)
            }
        }
    }

    private fun makeToolbar(board: DrawingView) = LinearLayout(this).apply {
        gravity = Gravity.CENTER
        orientation = LinearLayout.HORIZONTAL
        elevation = 10.dp.toFloat()
        setPadding(10.dp, 8.dp, 10.dp, 8.dp)
        background = rounded(Color.argb(235, 250, 248, 241), 24f)

        val colors = listOf(
            "흰색" to Color.rgb(243, 238, 213), "노랑" to Color.rgb(248, 214, 92),
            "분홍" to Color.rgb(244, 153, 177), "파랑" to Color.rgb(137, 198, 235),
            "연두" to Color.rgb(166, 218, 126)
        )
        val colorButtons = mutableListOf<View>()
        lateinit var eraser: View
        colors.forEachIndexed { index, (name, color) ->
            val dot = View(this@MainActivity).apply {
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
            addView(dot, LinearLayout.LayoutParams(28.dp, 28.dp).apply { marginEnd = 7.dp })
        }
        addDivider(2, 9)
        eraser = LinearLayout(this@MainActivity).apply {
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
        addView(eraser, LinearLayout.LayoutParams(35.dp, 27.dp).apply { marginEnd = 10.dp })
        addDivider(0, 7)
        val widths = listOf(2.5f, 4.5f, 7f)
        val widthButtons = mutableListOf<TextView>()
        widths.forEachIndexed { index, width ->
            val line = TextView(this@MainActivity).apply {
                text = "—"
                gravity = Gravity.CENTER
                textSize = 11f + index * 5f
                setTextColor(Color.rgb(35, 48, 42))
                contentDescription = "분필 두께 ${index + 1}단계"
                alpha = if (index == 2) 1f else 0.42f
                setOnClickListener {
                    board.setStrokeWidth(width)
                    widthButtons.forEach { it.alpha = 0.42f }
                    alpha = 1f
                }
            }
            widthButtons += line
            addView(line, LinearLayout.LayoutParams(31.dp, 30.dp))
        }
        addDivider(5, 5)
        addView(ImageButton(this@MainActivity).apply {
            setImageResource(R.drawable.ic_delete_all)
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "전체 지우기"
            setPadding(7.dp, 7.dp, 7.dp, 7.dp)
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity).setMessage("그림을 모두 지울까요?")
                    .setNegativeButton("취소", null).setPositiveButton("전체 지우기") { _, _ -> board.clear() }.show()
            }
        }, LinearLayout.LayoutParams(36.dp, 36.dp))
    }

    private fun LinearLayout.addDivider(start: Int, end: Int) {
        addView(View(this@MainActivity).apply { setBackgroundColor(Color.LTGRAY) }, LinearLayout.LayoutParams(1.dp, 24.dp).apply {
            marginStart = start.dp
            marginEnd = end.dp
        })
    }

    private fun centeredColumn() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(42.dp, 42.dp, 42.dp, 42.dp)
        setBackgroundColor(Color.rgb(36, 85, 66))
    }

    private fun rounded(color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * resources.displayMetrics.density
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
