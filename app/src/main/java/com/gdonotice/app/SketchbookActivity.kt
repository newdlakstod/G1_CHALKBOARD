package com.gdonotice.app

import android.app.Dialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.res.ResourcesCompat
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.tasks.Tasks
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.util.UUID

class SketchbookActivity : ComponentActivity() {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val prefs by lazy { getSharedPreferences("board", MODE_PRIVATE) }
    private val pretendard by lazy { ResourcesCompat.getFont(this, R.font.pretendard_regular) ?: Typeface.DEFAULT }
    private val cavorting by lazy { ResourcesCompat.getFont(this, R.font.cavorting) ?: Typeface.DEFAULT }
    private val cream = 0xFFFFF4DE.toInt()
    private val olive = 0xFF8D9440.toInt()
    private val orange = 0xFFE9823B.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = SketchbookDesign.COBALT
        window.navigationBarColor = SketchbookDesign.PAPER
        window.decorView.systemUiVisibility = 0
        showWelcome()
    }

    private fun showWelcome() {
        val expanded = SketchbookDesign.layoutFor(resources.configuration.screenWidthDp).usesTwoPaneHome
        val root = LinearLayout(this).apply {
            orientation = if (expanded) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(if (expanded) 54.dp else 30.dp, 34.dp, if (expanded) 54.dp else 30.dp, if (expanded) 58.dp else 30.dp)
            setBackgroundColor(SketchbookDesign.COBALT)
        }
        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (expanded) Gravity.CENTER_VERTICAL or Gravity.START else Gravity.CENTER_HORIZONTAL
            addView(label("G1\nSKETCHBOOK", if (expanded) 47f else 48f, cream, cavorting).apply {
                gravity = if (expanded) Gravity.START else Gravity.CENTER
                setLineSpacing((-8).dp.toFloat(), .88f)
            })
            addView(label("Draw together,\nkeep the little days.", if (expanded) 23f else 20f, cream, cavorting).apply {
                gravity = if (expanded) Gravity.START else Gravity.CENTER
            }, lp(-1, -2, top = if (expanded) 28 else 12))
            addView(LinearLayout(this@SketchbookActivity).apply {
                gravity = if (expanded) Gravity.START else Gravity.CENTER
                addView(outlineButton("Log in") { signIn() }, LinearLayout.LayoutParams(118.dp, 48.dp))
                addView(outlineButton("Enter") {
                    if (auth.currentUser == null) toast("먼저 Log in을 눌러 로그인해 주세요.") else {
                        prefs.edit().putBoolean("entered", true).apply()
                        showHome()
                    }
                }, LinearLayout.LayoutParams(118.dp, 48.dp).apply { marginStart = 14.dp })
            }, lp(-1, 52.dp, top = 24))
        }
        val duck = ImageView(this).apply {
            setImageResource(R.drawable.welcome_duck)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = "스케치북을 들고 걷는 오리"
        }
        if (expanded) {
            root.addView(copy, LinearLayout.LayoutParams(0, -1, 2f))
            root.addView(duck, LinearLayout.LayoutParams(0, -1, 3f))
        } else {
            root.addView(label("G1\nSKETCHBOOK", 47f, cream, cavorting).apply {
                gravity = Gravity.CENTER; setLineSpacing((-8).dp.toFloat(), .88f)
            }, LinearLayout.LayoutParams(-1, 0, 1.05f))
            root.addView(duck, LinearLayout.LayoutParams(-1, 0, 2.75f))
            root.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                addView(label("Draw together,\nkeep the little days.", 20f, cream, cavorting).apply { gravity = Gravity.CENTER })
                addView(LinearLayout(this@SketchbookActivity).apply {
                    gravity = Gravity.CENTER
                    addView(outlineButton("Log in") { signIn() }, LinearLayout.LayoutParams(118.dp, 48.dp))
                    addView(outlineButton("Enter") {
                        if (auth.currentUser == null) toast("먼저 Log in을 눌러 로그인해 주세요.") else {
                            prefs.edit().putBoolean("entered", true).apply(); showHome()
                        }
                    }, LinearLayout.LayoutParams(118.dp, 48.dp).apply { marginStart = 14.dp })
                }, lp(-1, 52.dp, top = 18))
            }, LinearLayout.LayoutParams(-1, 0, 1.2f))
        }
        setScreen(root, false)
    }

    private fun signIn() = lifecycleScope.launch {
        try {
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(getString(R.string.default_web_client_id)).build()
            val result = CredentialManager.create(this@SketchbookActivity).getCredential(
                this@SketchbookActivity,
                GetCredentialRequest.Builder().addCredentialOption(option).build()
            )
            val credential = result.credential
            require(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
            val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
            auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null))
                .addOnSuccessListener { toast("로그인되었습니다. Enter로 시작해 주세요.") }
                .addOnFailureListener { toast("로그인하지 못했습니다.") }
        } catch (_: Exception) { toast("로그인이 취소되었습니다.") }
    }

    private fun showHome() {
        val layout = SketchbookDesign.layoutFor(resources.configuration.screenWidthDp)
        val root = LinearLayout(this).apply {
            orientation = if (layout.usesTwoPaneHome) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            setPadding(24.dp, 24.dp, 24.dp, 18.dp)
            setBackgroundColor(SketchbookDesign.PAPER)
        }
        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(LinearLayout(this@SketchbookActivity).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(label("Hello, ${auth.currentUser?.displayName?.substringBefore(' ') ?: "friend"}", 32f, SketchbookDesign.INK, cavorting), LinearLayout.LayoutParams(0, -2, 1f))
                addView(avatarButton())
            })
            addView(label("Today’s little page", 14f, 0xFF766F65.toInt()), lp(-1, -2, top = 6))
            addView(label("Make something\nsmall together.", if (layout.usesTwoPaneHome) 38f else 30f, SketchbookDesign.COBALT, cavorting), lp(-1, -2, top = 28))
            addView(actionButton("New sketchbook", orange) { showCreateDialog() }, lp(-1, 54.dp, top = 26))
            addView(actionButton("Join with code", Color.TRANSPARENT, SketchbookDesign.COBALT) { showJoinDialog() }, lp(-1, 54.dp, top = 10))
            if (!layout.usesTwoPaneHome) addView(bottomNav(AppRoute.HOME), lp(-1, 62.dp, top = 18))
        }
        val recent = recentCard()
        if (layout.usesTwoPaneHome) {
            root.addView(copy, LinearLayout.LayoutParams(0, -1, layout.leadingPaneWeight).apply { marginEnd = 24.dp })
            root.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(label("Recent sketchbook", 16f, SketchbookDesign.INK).apply { setTypeface(pretendard, Typeface.BOLD) })
                addView(recent, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = 12.dp })
                addView(bottomNav(AppRoute.HOME), lp(-1, 62.dp, top = 14))
            }, LinearLayout.LayoutParams(0, -1, layout.trailingPaneWeight))
        } else {
            root.addView(copy, LinearLayout.LayoutParams(-1, -2))
            root.addView(label("Recent sketchbook", 16f, SketchbookDesign.INK).apply { setTypeface(pretendard, Typeface.BOLD) }, lp(-1, -2, top = 18))
            root.addView(recent, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = 10.dp })
        }
        setScreen(root)
        loadRecent(root)
    }

    private fun recentCard() = FrameLayout(this).apply {
        tag = "recentCard"
        background = rounded(0xFFFFFCF4.toInt(), 22f)
        elevation = 5.dp.toFloat()
        addView(ImageView(this@SketchbookActivity).apply {
            tag = "recentImage"; scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(0xFFE7D7BC.toInt())
        }, FrameLayout.LayoutParams(-1, -1))
        addView(label("Continue drawing  →", 15f, cream).apply {
            tag = "recentTitle"; setPadding(18.dp, 12.dp, 18.dp, 12.dp)
            background = rounded(0xCC123FA5.toInt(), 999f)
        }, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { setMargins(16.dp, 16.dp, 16.dp, 16.dp) })
        setOnClickListener { prefs.getString("recentCode", null)?.let(::openDrawing) ?: showCreateDialog() }
    }

    private fun loadRecent(root: View) {
        val code = prefs.getString("recentCode", null) ?: prefs.getStringSet("codes", emptySet()).orEmpty().firstOrNull() ?: return
        db.collection("boards").document(code).get().addOnSuccessListener { snap ->
            if (!snap.exists()) return@addOnSuccessListener
            findTag(root, "recentTitle")?.let { (it as TextView).text = "${snap.getString("name") ?: "Untitled"}  →" }
            val bytes = (snap.getBlob("cover") ?: snap.getBlob("image"))?.toBytes() ?: return@addOnSuccessListener
            (findTag(root, "recentImage") as? ImageView)?.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        }
    }

    private fun showLibrary() {
        val columns = SketchbookDesign.layoutFor(resources.configuration.screenWidthDp).libraryColumns
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(24.dp, 24.dp, 24.dp, 18.dp); setBackgroundColor(SketchbookDesign.PAPER)
            addView(LinearLayout(this@SketchbookActivity).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(label("My sketchbooks", 34f, SketchbookDesign.COBALT, cavorting), LinearLayout.LayoutParams(0, -2, 1f))
                addView(avatarButton())
            })
        }
        val grid = GridLayout(this).apply { columnCount = columns; setPadding(0, 18.dp, 0, 18.dp) }
        page.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        page.addView(bottomNav(AppRoute.LIBRARY), lp(-1, 62.dp))
        setScreen(page)
        val codes = prefs.getStringSet("codes", emptySet()).orEmpty()
        Tasks.whenAllSuccess<DocumentSnapshot>(codes.map { db.collection("boards").document(it).get() }).addOnSuccessListener { docs ->
            docs.filter { it.exists() }.forEach { grid.addView(libraryCard(it, columns)) }
            if (grid.childCount == 0) grid.addView(label("아직 스케치북이 없어요.\n첫 페이지를 만들어 보세요.", 16f, 0xFF766F65.toInt()).apply { gravity = Gravity.CENTER })
        }
    }

    private fun libraryCard(doc: DocumentSnapshot, columns: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(8.dp, 8.dp, 8.dp, 12.dp); background = rounded(Color.WHITE, 14f); elevation = 3.dp.toFloat()
        val image = ImageView(this@SketchbookActivity).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(0xFFE7D7BC.toInt()) }
        doc.getBlob("cover")?.toBytes()?.let { image.setImageBitmap(BitmapFactory.decodeByteArray(it, 0, it.size)) }
        addView(image, LinearLayout.LayoutParams(-1, 0, 1f))
        addView(label(doc.getString("name") ?: "Untitled", 16f, SketchbookDesign.INK).apply { setTypeface(pretendard, Typeface.BOLD) }, lp(-1, -2, top = 9))
        setOnClickListener { openDrawing(doc.id) }
        layoutParams = GridLayout.LayoutParams().apply {
            width = (resources.displayMetrics.widthPixels - (48 + (columns - 1) * 12).dp) / columns
            height = if (columns >= 4) 220.dp else 250.dp
            setMargins(5.dp, 6.dp, 5.dp, 6.dp)
        }
    }

    private fun showCreateDialog() {
        val field = EditText(this).apply { hint = "새 스케치북 이름"; setSingleLine(); setPadding(18.dp, 0, 18.dp, 0); background = rounded(Color.WHITE, 18f) }
        showDialog("New sketchbook", field, "Next") {
            val name = field.text.toString().trim()
            if (name.isEmpty()) toast("이름을 입력해 주세요.") else showPresetDialog(name)
        }
    }

    private fun showJoinDialog() {
        val field = EditText(this).apply { hint = "초대 코드"; setSingleLine(); setPadding(18.dp, 0, 18.dp, 0); background = rounded(Color.WHITE, 18f) }
        showDialog("Join a sketchbook", field, "Enter") {
            val code = field.text.toString().trim().uppercase()
            db.collection("boards").document(code).get().addOnSuccessListener { if (it.exists()) saveAndOpen(code) else toast("코드를 확인해 주세요.") }
        }
    }

    private fun showPresetDialog(name: String) {
        val grid = GridLayout(this).apply { columnCount = 3 }
        listOf(CanvasPreset.A4, CanvasPreset.A5, CanvasPreset.B5, CanvasPreset.SQUARE, CanvasPreset.PHOTO, CanvasPreset.WIDE).forEach { preset ->
            grid.addView(CanvasPresetIconView(this, preset).apply {
                contentDescription = preset.name
                setOnClickListener { createSketchbook(name, preset) }
            }, GridLayout.LayoutParams().apply { width = 76.dp; height = 96.dp; setMargins(5.dp, 5.dp, 5.dp, 5.dp) })
        }
        showDialog("Choose a canvas", grid, "Close") {}
    }

    private fun createSketchbook(name: String, preset: CanvasPreset) {
        val user = auth.currentUser ?: return toast("로그인이 필요합니다.")
        val code = UUID.randomUUID().toString().take(6).uppercase()
        val size = SketchbookDesign.pixelSize(preset)
        db.collection("boards").document(code).set(mapOf(
            "name" to name, "ownerId" to user.uid, "ownerName" to (user.displayName ?: user.email ?: "Creator"),
            "members" to mapOf(user.uid to true), "memberNames" to mapOf(user.uid to (user.displayName ?: "Creator")),
            "canvasWidth" to size.first, "canvasHeight" to size.second, "backgroundColor" to SketchbookDesign.PAPER.toLong(),
            "createdAt" to Timestamp.now(), "updatedAt" to Timestamp.now()
        )).addOnSuccessListener {
            prefs.edit().putInt("canvasWidth_$code", size.first).putInt("canvasHeight_$code", size.second).apply()
            saveAndOpen(code)
        }.addOnFailureListener { toast("스케치북을 만들지 못했습니다.") }
    }

    private fun saveAndOpen(code: String) {
        val codes = prefs.getStringSet("codes", emptySet()).orEmpty().toMutableSet().apply { add(code) }
        prefs.edit().putStringSet("codes", codes).putString("recentCode", code).apply()
        openDrawing(code)
    }

    private fun openDrawing(code: String) = startActivity(Intent(this, MainActivity::class.java).putExtra("boardCode", code))

    private fun showAccount() {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(auth.currentUser?.email ?: "로그인 정보 없음", 15f, 0xFF766F65.toInt()))
            addView(actionButton("Sign out", Color.WHITE, SketchbookDesign.COBALT) {
                auth.signOut(); prefs.edit().putBoolean("entered", false).apply(); showWelcome()
            }, lp(-1, 50.dp, top = 18))
        }
        showDialog("My account", body, "Close") {}
    }

    private fun bottomNav(selected: AppRoute) = LinearLayout(this).apply {
        gravity = Gravity.CENTER; background = rounded(0xFFFDF9F0.toInt(), 999f); elevation = 3.dp.toFloat(); setPadding(7.dp, 5.dp, 7.dp, 5.dp)
        listOf("Home" to AppRoute.HOME, "Sketchbooks" to AppRoute.LIBRARY).forEach { (text, route) ->
            addView(Button(this@SketchbookActivity).apply {
                this.text = text; isAllCaps = false; setTextColor(if (route == selected) cream else SketchbookDesign.COBALT)
                background = rounded(if (route == selected) SketchbookDesign.COBALT else Color.TRANSPARENT, 999f)
                setOnClickListener { if (route == AppRoute.HOME) showHome() else showLibrary() }
            }, LinearLayout.LayoutParams(0, -1, 1f))
        }
    }

    private fun avatarButton() = ImageButton(this).apply {
        setImageResource(R.drawable.welcome_duck); scaleType = ImageView.ScaleType.CENTER_CROP; contentDescription = "계정"
        background = rounded(olive, 999f); setPadding(7.dp, 7.dp, 7.dp, 7.dp); setOnClickListener { showAccount() }
        layoutParams = LinearLayout.LayoutParams(52.dp, 52.dp)
    }

    private fun showDialog(title: String, content: View, positive: String, action: () -> Unit) {
        val dialog = Dialog(this)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(22.dp, 22.dp, 22.dp, 18.dp); background = rounded(0xFFFFFAF0.toInt(), 26f)
            addView(label(title, 27f, SketchbookDesign.COBALT, cavorting), lp(-1, -2, bottom = 18))
            addView(content, LinearLayout.LayoutParams(-1, -2))
            addView(LinearLayout(this@SketchbookActivity).apply {
                gravity = Gravity.END
                addView(actionButton("Close", Color.WHITE, SketchbookDesign.COBALT) { dialog.dismiss() }, LinearLayout.LayoutParams(0, 50.dp, 1f).apply { marginEnd = 6.dp })
                addView(actionButton(positive, orange) { dialog.dismiss(); action() }, LinearLayout.LayoutParams(0, 50.dp, 1f).apply { marginStart = 6.dp })
            }, lp(-1, 50.dp, top = 18))
        }
        dialog.setContentView(card); dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)); dialog.show()
        dialog.window?.setLayout(minOf(resources.displayMetrics.widthPixels - 48.dp, 390.dp), WindowManager.LayoutParams.WRAP_CONTENT)
        applyFonts(card)
    }

    private fun actionButton(text: String, color: Int, textColor: Int = Color.WHITE, action: () -> Unit) = Button(this).apply {
        this.text = text; isAllCaps = false; this.setTextColor(textColor); textSize = 15f; background = rounded(color, 999f); setOnClickListener { action() }
    }

    private fun outlineButton(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text; isAllCaps = false; typeface = cavorting; textSize = 18f; setTextColor(cream)
        background = GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius = 999f; setStroke(1.dp, cream) }
        setOnClickListener { action() }
    }

    private fun label(text: String, size: Float, color: Int, font: Typeface = pretendard) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color); typeface = font
    }

    private fun setScreen(view: View, paperStatus: Boolean = true) {
        window.statusBarColor = if (paperStatus) SketchbookDesign.PAPER else SketchbookDesign.COBALT
        applyFonts(view); setContentView(view)
    }

    private fun applyFonts(view: View) {
        if (view is TextView && view.typeface != cavorting) view.typeface = Typeface.create(pretendard, view.typeface?.style ?: 0)
        if (view.isClickable && view !is EditText) view.setOnTouchListener { v, e ->
            when (e.actionMasked) { MotionEvent.ACTION_DOWN -> v.animate().scaleX(.96f).scaleY(.96f).setDuration(90).start(); MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(150).start() }
            false
        }
        if (view is ViewGroup) repeat(view.childCount) { applyFonts(view.getChildAt(it)) }
    }

    private fun findTag(view: View, tag: String): View? {
        if (view.tag == tag) return view
        if (view is ViewGroup) repeat(view.childCount) { findTag(view.getChildAt(it), tag)?.let { found -> return found } }
        return null
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply { setColor(color); cornerRadius = radius * resources.displayMetrics.density }
    private fun lp(width: Int, height: Int, top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(width, height).apply { topMargin = top.dp; bottomMargin = bottom.dp }
    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()
}
