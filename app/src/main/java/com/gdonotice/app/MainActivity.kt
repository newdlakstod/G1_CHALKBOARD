package com.gdonotice.app

import android.app.Dialog
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.res.ResourcesCompat
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val prefs by lazy { getSharedPreferences("board", MODE_PRIVATE) }
    private var currentBoardCode: String? = null
    private var coverBoardCode: String? = null
    private val pretendard: Typeface by lazy { ResourcesCompat.getFont(this, R.font.pretendard_regular) ?: Typeface.DEFAULT }
    private val pickCover = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val code = coverBoardCode ?: return@registerForActivityResult
        uri ?: return@registerForActivityResult
        contentResolver.openInputStream(uri)?.use { input ->
            val source = BitmapFactory.decodeStream(input) ?: return@use
            showCoverEditor(code, source)
        }
    }

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
            setTextColor(Color.rgb(31, 31, 31))
            gravity = Gravity.CENTER
        })
        root.addView(Button(this).apply {
            text = "Google로 로그인"
            isAllCaps = false
            primaryStyle()
            setOnClickListener { signInWithGoogle() }
        }, LinearLayout.LayoutParams(-1, 54.dp).apply { topMargin = 28.dp })
        showContent(root)
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
        currentBoardCode = null
        val root = centeredColumn().apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setPadding(20.dp, 30.dp, 20.dp, 30.dp)
            clipChildren = false
            clipToPadding = false
        }
        root.addView(TextView(this).apply {
            text = "Hi, ${auth.currentUser?.displayName ?: auth.currentUser?.email?.substringBefore('@') ?: "친구"}"
            textSize = 28f
            setTextColor(Color.rgb(31, 31, 31))
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(TextView(this).apply {
            text = "함께 그리는 나의 칠판"
            textSize = 15f
            setTextColor(Color.rgb(116, 110, 105))
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 6.dp })
        val columns = if (resources.configuration.screenWidthDp >= 600) 6 else 3
        val mine = GridLayout(this).apply {
            columnCount = columns
            clipChildren = false
            clipToPadding = false
        }
        val shared = GridLayout(this).apply {
            columnCount = columns
            clipChildren = false
            clipToPadding = false
        }
        root.addView(LinearLayout(this).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            addView(ImageButton(this@MainActivity).apply {
                setImageResource(R.drawable.ic_sort)
                contentDescription = "칠판 정렬"
                setPadding(11.dp, 11.dp, 11.dp, 11.dp)
                background = rounded(Color.WHITE, 999f)
                elevation = 2.dp.toFloat()
                setOnClickListener { showSortDialog() }
            }, LinearLayout.LayoutParams(46.dp, 46.dp))
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 36.dp })
        root.addView(sectionTitle("내가 만든 칠판"), LinearLayout.LayoutParams(-1, -2).apply { topMargin = 20.dp })
        root.addView(mine, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8.dp })
        root.addView(sectionTitle("공유받은 칠판"), LinearLayout.LayoutParams(-1, -2).apply { topMargin = 24.dp })
        root.addView(shared, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8.dp })
        val codes = prefs.getStringSet("codes", emptySet()).orEmpty()
        val requests = codes.map { db.collection("boards").document(it).get() }
        Tasks.whenAllSuccess<DocumentSnapshot>(requests).addOnSuccessListener { snapshots ->
            val existing = snapshots.filter { it.exists() }
            prefs.edit().putStringSet("codes", existing.mapTo(mutableSetOf()) { it.id }).apply()
            val mode = prefs.getString("sortMode", "date_desc")
            val sorted = when (mode) {
                "name_asc" -> existing.sortedBy { it.getString("name").orEmpty().lowercase() }
                "name_desc" -> existing.sortedByDescending { it.getString("name").orEmpty().lowercase() }
                "date_asc" -> existing.sortedBy { (it.getTimestamp("createdAt") ?: it.getTimestamp("updatedAt"))?.seconds ?: 0L }
                else -> existing.sortedByDescending { (it.getTimestamp("createdAt") ?: it.getTimestamp("updatedAt"))?.seconds ?: 0L }
            }
            sorted.forEach { snapshot ->
                addBoardCard(
                    if (snapshot.getString("ownerId") == auth.currentUser?.uid) mine else shared,
                    snapshot.id,
                    columns,
                    snapshot
                )
            }
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(root)
        }
        showContent(FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(243, 240, 237))
            addView(scroll, FrameLayout.LayoutParams(-1, -1))
            addView(Button(this@MainActivity).apply {
                text = "+"
                primaryStyle()
                textSize = 32f
                includeFontPadding = false
                gravity = Gravity.CENTER
                contentDescription = "칠판 추가"
                setPadding(0, 0, 0, 3.dp)
                setOnClickListener { showAddBoardDialog() }
            }, FrameLayout.LayoutParams(58.dp, 58.dp, Gravity.BOTTOM or Gravity.END).apply {
                marginEnd = 22.dp
                bottomMargin = 24.dp
            })
        })
    }

    private fun sectionTitle(label: String) = TextView(this).apply {
        text = label
        textSize = 19f
        setTextColor(Color.rgb(31, 31, 31))
    }

    private fun showSortDialog() {
        lateinit var dialog: Dialog
        val choices = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            listOf(
                "이름 오름차순" to "name_asc",
                "이름 내림차순" to "name_desc",
                "생성일 오름차순" to "date_asc",
                "생성일 내림차순" to "date_desc"
            ).forEach { (label, mode) ->
                addView(Button(this@MainActivity).apply {
                    text = label
                    isAllCaps = false
                    secondaryStyle()
                    setOnClickListener {
                        prefs.edit().putString("sortMode", mode).apply()
                        dialog.dismiss()
                        showBoardChooser()
                    }
                }, LinearLayout.LayoutParams(-1, 50.dp).apply { bottomMargin = 8.dp })
            }
        }
        dialog = showRoundDialog("칠판 정렬", choices, "닫기") {}
    }

    private fun addBoardCard(root: GridLayout, code: String, columns: Int, snapshot: DocumentSnapshot) {
        val thumbnail = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.rgb(26, 66, 47))
        }
        val title = TextView(this).apply {
            text = snapshot.getString("name") ?: "칠판 $code"
            textSize = if (columns == 6) 14f else 16f
            setTextColor(Color.rgb(31, 31, 31))
            setTypeface(pretendard, Typeface.BOLD)
            maxLines = 1
        }
        @Suppress("UNCHECKED_CAST")
        val memberNames = snapshot.get("memberNames") as? Map<String, String>
        val creator = snapshot.getString("ownerName")
            ?: memberNames?.get(snapshot.getString("ownerId"))
            ?: "알 수 없음"
        val createdAt = snapshot.getTimestamp("createdAt") ?: snapshot.getTimestamp("updatedAt")
        val date = createdAt?.toDate()?.let {
            java.text.SimpleDateFormat("yyyy.MM.dd", java.util.Locale.KOREA).format(it)
        } ?: "날짜 없음"
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(7.dp, 7.dp, 7.dp, 10.dp)
            background = rounded(Color.WHITE, 10f)
            elevation = 6.dp.toFloat()
            translationZ = 0f
            clipChildren = false
            addView(FrameLayout(this@MainActivity).apply {
                background = rounded(Color.rgb(26, 66, 47), 5f)
                clipToOutline = true
                addView(thumbnail, FrameLayout.LayoutParams(-1, -1))
            }, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(title, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 9.dp })
            addView(TextView(this@MainActivity).apply {
                text = "$date · $creator"
                textSize = if (columns == 6) 9f else 10f
                setTextColor(Color.rgb(112, 106, 101))
                maxLines = 2
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 2.dp })
            setOnClickListener { enterBoard(code) }
            setOnLongClickListener {
                showBoardActions(code, title.text.toString(), snapshot.getString("ownerId") == auth.currentUser?.uid)
                true
            }
        }
        val cardWidth = (resources.displayMetrics.widthPixels - 40.dp - (columns * 8).dp) / columns
        root.addView(card, GridLayout.LayoutParams().apply {
            width = cardWidth
            height = (cardWidth * 1.38f).toInt()
            setMargins(4.dp, 8.dp, 4.dp, 10.dp)
        })
        (snapshot.getBlob("cover") ?: snapshot.getBlob("image"))?.toBytes()?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let(thumbnail::setImageBitmap)
            if (snapshot.getBlob("cover") != null) java.io.File(filesDir, "cover_$code.jpg").writeBytes(bytes)
        }
    }

    private fun showAddBoardDialog() {
        val nameInput = EditText(this).apply {
            hint = "새 칠판 이름 기입"
            setHintTextColor(Color.rgb(185, 185, 185))
            setSingleLine()
            background = rounded(Color.WHITE, 30f)
            setPadding(18.dp, 0, 18.dp, 0)
        }
        val codeInput = EditText(this).apply {
            hint = "초대코드로 입장"
            setHintTextColor(Color.rgb(185, 185, 185))
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setSingleLine()
            background = rounded(Color.WHITE, 30f)
            setPadding(18.dp, 0, 18.dp, 0)
        }
        lateinit var dialog: Dialog
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(nameInput, LinearLayout.LayoutParams(-1, 54.dp))
            addView(TextView(this@MainActivity).apply {
                text = "또는"
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(116, 110, 105))
            }, LinearLayout.LayoutParams(-1, 42.dp))
            addView(codeInput, LinearLayout.LayoutParams(-1, 54.dp))
        }
        dialog = showRoundDialog("칠판 추가", content, "만들기") {
            val name = nameInput.text.toString().trim()
            val code = codeInput.text.toString().trim()
            when {
                name.isNotBlank() -> createBoard(name)
                code.isNotBlank() -> joinBoard(code)
                else -> toast("칠판 이름이나 초대 코드를 입력해 주세요.")
            }
        }
    }

    private fun showBoardActions(code: String, currentName: String, isOwner: Boolean) {
        lateinit var dialog: Dialog
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fun action(label: String, onClick: () -> Unit) = addView(Button(this@MainActivity).apply {
                text = label
                isAllCaps = false
                secondaryStyle()
                setOnClickListener { onClick() }
            }, LinearLayout.LayoutParams(-1, 52.dp).apply { bottomMargin = 8.dp })
            action("이름 변경") {
                dialog.dismiss()
                askRenameBoard(code, currentName)
            }
            action("표지 변경") {
                dialog.dismiss()
                coverBoardCode = code
                pickCover.launch("image/*")
            }
            if (isOwner) action("칠판 삭제") {
                dialog.dismiss()
                confirmRemoveBoard(code)
            }
        }
        dialog = showRoundDialog(currentName, actions, "닫기") {}
    }

    private fun askRenameBoard(code: String, currentName: String) {
        val input = EditText(this).apply {
            setText(currentName)
            selectAll()
            setSingleLine()
            background = rounded(Color.WHITE, 30f)
            setPadding(18.dp, 0, 18.dp, 0)
        }
        showRoundDialog("칠판 이름 변경", input, "변경") {
            val name = input.text.toString().trim()
            if (name.isBlank()) toast("칠판 이름을 입력해 주세요.") else {
                db.collection("boards").document(code).update("name", name)
                    .addOnSuccessListener { showBoardChooser() }
                    .addOnFailureListener { toast("이름을 변경하지 못했습니다.") }
            }
        }
    }

    private fun confirmRemoveBoard(code: String) {
        showRoundDialog("칠판 삭제", TextView(this).apply { text = "이 칠판을 모든 참여자에게서 삭제할까요?" }, "삭제") {
            val codes = prefs.getStringSet("codes", emptySet()).orEmpty().toMutableSet().apply { remove(code) }
            prefs.edit().putStringSet("codes", codes).apply()
            db.collection("boards").document(code).delete().addOnCompleteListener { showBoardChooser() }
        }
    }

    private fun createBoard(name: String) {
        val user = auth.currentUser ?: return
        val code = UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
        db.collection("boards").document(code).set(
            mapOf(
                "name" to name,
                "ownerId" to user.uid,
                "ownerName" to memberLabel(),
                "members" to mapOf(user.uid to true),
                "memberNames" to mapOf(user.uid to memberLabel()),
                "backgroundColor" to 0xFF1A422F,
                "createdAt" to Timestamp.now(),
                "updatedAt" to Timestamp.now()
            )
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
            doc.update(mapOf("members.${user.uid}" to true, "memberNames.${user.uid}" to memberLabel()))
                .addOnSuccessListener { enterBoard(code) }
                .addOnFailureListener { toast("칠판에 참가하지 못했습니다.") }
        }.addOnFailureListener { toast("초대 코드를 확인하지 못했습니다.") }
    }

    private fun enterBoard(code: String) {
        val codes = prefs.getStringSet("codes", emptySet()).orEmpty().toMutableSet().apply { add(code) }
        prefs.edit().putString("code", code).putStringSet("codes", codes).apply()
        showBoard(code)
    }

    private fun showBoard(code: String) {
        currentBoardCode = code
        val document = db.collection("boards").document(code)
        lateinit var board: DrawingView
        board = DrawingView(this, code, prefs.getInt("background_$code", 0xFF1A422F.toInt())) { bytes, cleared ->
            BoardWidget.updateAll(this)
            val user = auth.currentUser ?: return@DrawingView
            val now = Timestamp.now()
            val update = mutableMapOf<String, Any>(
                "image" to Blob.fromBytes(bytes), "updatedAt" to now, "updatedBy" to user.uid
            )
            if (cleared) update["clearedAt"] = now
            document.update(update).addOnFailureListener { toast("그림을 동기화하지 못했습니다.") }
        }
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(36, 85, 66)) }
        root.addView(board, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        root.addView(makeToolbar(board, code), FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            bottomMargin = 24.dp
        })
        showContent(root)

        var firstSnapshot = true
        document.addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener toast("동기화 연결을 확인해 주세요.")
            val background = snapshot?.getLong("backgroundColor")?.toInt() ?: 0xFF1A422F.toInt()
            prefs.edit().putInt("background_$code", background).apply()
            board.setBoardBackgroundColor(background, false)
            if (firstSnapshot || snapshot?.getString("updatedBy") != auth.currentUser?.uid) {
                snapshot?.getBlob("image")?.toBytes()?.let {
                    val wasCleared = snapshot.getTimestamp("clearedAt") == snapshot.getTimestamp("updatedAt")
                    if (firstSnapshot || wasCleared) board.replaceFromBytes(it) else board.mergeFromBytes(it)
                    firstSnapshot = false
                    BoardWidget.updateAll(this)
                }
            }
        }
    }

    @Deprecated("Android back compatibility")
    override fun onBackPressed() {
        if (currentBoardCode != null) {
            prefs.edit().remove("code").apply()
            showBoardChooser()
        } else {
            super.onBackPressed()
        }
    }

    private fun makeToolbar(board: DrawingView, code: String) = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        isFillViewport = false
        overScrollMode = View.OVER_SCROLL_NEVER
        val content = makeToolbarContent(board, code)
        addView(content, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        post { scrollTo(maxOf(0, (content.width - width) / 2), 0) }
    }

    private fun makeToolbarContent(board: DrawingView, code: String) = LinearLayout(this).apply {
        gravity = Gravity.CENTER
        orientation = LinearLayout.HORIZONTAL
        elevation = 10.dp.toFloat()
        setPadding(10.dp, 6.dp, 10.dp, 6.dp)
        background = rounded(Color.argb(248, 31, 31, 31), 999f)

        val palette = listOf(
            0xFFFDE7D0.toInt(), 0xFFDBAB7D.toInt(), 0xFFD99858.toInt(), 0xFFB58352.toInt(), 0xFFA4805C.toInt(),
            0xFFB8C8A4.toInt(), 0xFFC3C7A4.toInt(), 0xFFC7C4A3.toInt(), 0xFFCAC281.toInt(), 0xFF837D41.toInt(),
            0xFF8DA198.toInt(), 0xFF709B87.toInt(), 0xFF587063.toInt(), 0xFFB0BFB8.toInt(), 0xFFAED5B8.toInt(),
            0xFF8593AE.toInt(), 0xFFAEBEDF.toInt(), 0xFF92BCD5.toInt(), 0xFF5D668F.toInt(), 0xFF494F67.toInt(),
            0xFFBF9EA7.toInt(), 0xFFDC8EA6.toInt(), 0xFF936572.toInt(), 0xFFC69A99.toInt(), 0xFFE28A88.toInt(),
            0xFFF87875.toInt(), 0xFFB7664B.toInt(), 0xFFDA6E54.toInt(), 0xFFB45632.toInt(), 0xFFC2551A.toInt()
        )
        val colors = mutableListOf(palette[0], palette[6], palette[11], palette[17], palette[25])
        board.setColor(colors.first())
        var selectedColor = colors.first()
        var selectedIndex = 0
        val colorButtons = mutableListOf<View>()
        lateinit var eraser: ImageButton
        val chalk = toolButton(R.drawable.ic_chalk, "분필") {
            board.setColor(selectedColor)
            eraser.alpha = 0.55f
        }
        addView(chalk)
        addDivider(3, 7)
        colors.forEachIndexed { index, color ->
            val dot = View(this@MainActivity).apply {
                contentDescription = "분필 색상 ${index + 1}"
                background = swatch(color, index == 0)
                setOnClickListener {
                    if (selectedIndex == index) {
                        showPalette(this, palette, selectedColor) { picked ->
                            selectedColor = picked
                            colors[index] = picked
                            board.setColor(picked)
                            colorButtons.forEachIndexed { i, button -> button.background = swatch(colors[i], i == index) }
                        }
                    } else {
                        selectedIndex = index
                        selectedColor = colors[index]
                        board.setColor(selectedColor)
                        colorButtons.forEachIndexed { i, button -> button.background = swatch(colors[i], i == index) }
                        eraser.alpha = 0.55f
                    }
                }
            }
            colorButtons += dot
            addView(dot, LinearLayout.LayoutParams(34.dp, 34.dp).apply { marginEnd = 7.dp })
        }

        addDivider(3, 7)
        var lastEraserTap = 0L
        eraser = ImageButton(this@MainActivity).apply {
            setImageResource(R.drawable.ic_eraser)
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "지우개, 두 번 누르면 전체 삭제"
            setPadding(8.dp, 8.dp, 8.dp, 8.dp)
            alpha = 0.55f
            setOnClickListener {
                val now = System.currentTimeMillis()
                if (now - lastEraserTap < 350) {
                    showRoundDialog(
                        "전체 지우기",
                        TextView(this@MainActivity).apply { text = "그림을 모두 지울까요?" },
                        "전체 지우기"
                    ) { board.clear() }
                    lastEraserTap = 0L
                } else {
                    board.setEraser()
                    colorButtons.forEachIndexed { i, button -> button.background = swatch(colors[i], false) }
                    alpha = 1f
                    lastEraserTap = now
                }
            }
        }
        addView(eraser, LinearLayout.LayoutParams(40.dp, 40.dp).apply { marginEnd = 5.dp })

        addDivider(2, 5)
        val widths = listOf(2.5f, 4.5f, 7f)
        val widthButtons = mutableListOf<View>()
        widths.forEachIndexed { index, width ->
            val holder = FrameLayout(this@MainActivity).apply {
                contentDescription = "분필 두께 ${index + 1}단계"
                alpha = if (index == 2) 1f else 0.45f
                addView(View(this@MainActivity).apply {
                    background = rounded(Color.WHITE, 8f)
                }, FrameLayout.LayoutParams(34.dp, (2 + index * 2).dp, Gravity.CENTER))
                setOnClickListener {
                    board.setStrokeWidth(width)
                    widthButtons.forEach { it.alpha = 0.45f }
                    alpha = 1f
                }
            }
            widthButtons += holder
            addView(holder, LinearLayout.LayoutParams(42.dp, 40.dp))
        }

        addDivider(5, 5)
        addView(toolButton(R.drawable.ic_undo, "작업 되돌리기") { board.undo() })
        addView(toolButton(R.drawable.ic_redo, "작업 다시 하기") { board.redo() })
        addView(toolButton(R.drawable.ic_settings, "칠판 설정") { showBoardSettings(code, board) })
    }

    private fun LinearLayout.addDivider(start: Int, end: Int) {
        addView(View(this@MainActivity).apply { setBackgroundColor(Color.rgb(105, 105, 105)) }, LinearLayout.LayoutParams(1.dp, 28.dp).apply {
            marginStart = start.dp
            marginEnd = end.dp
        })
    }

    private fun toolButton(icon: Int, description: String, action: () -> Unit) = ImageButton(this).apply {
        setImageResource(icon)
        setBackgroundColor(Color.TRANSPARENT)
        contentDescription = description
        setPadding(8.dp, 8.dp, 8.dp, 8.dp)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(40.dp, 40.dp)
    }

    private fun showPalette(anchor: View, colors: List<Int>, current: Int, onSelected: (Int) -> Unit) {
        val grid = GridLayout(this).apply {
            columnCount = 5
            setPadding(18.dp, 18.dp, 18.dp, 18.dp)
        }
        lateinit var popup: PopupWindow
        colors.forEachIndexed { index, color ->
            grid.addView(FrameLayout(this).apply {
                contentDescription = "팔레트 색상"
                addView(PaletteBlobView(this@MainActivity, color, index, color == current).apply {
                    setOnClickListener {
                        onSelected(color)
                        popup.dismiss()
                    }
                }, FrameLayout.LayoutParams(48.dp, 48.dp, Gravity.CENTER))
            }, GridLayout.LayoutParams().apply {
                width = 58.dp
                height = 58.dp
                setMargins(4.dp, 4.dp, 4.dp, 4.dp)
            })
        }
        val scroll = ScrollView(this).apply {
            background = rounded(Color.WHITE, 24f)
            addView(grid)
        }
        popup = PopupWindow(scroll, 366.dp, minOf(resources.displayMetrics.heightPixels - 80.dp, 448.dp), true).apply {
            elevation = 16.dp.toFloat()
            setBackgroundDrawable(GradientDrawable().apply { setColor(Color.TRANSPARENT) })
            isOutsideTouchable = true
        }
        applyPretendard(scroll)
        popup.showAtLocation(anchor, Gravity.CENTER, 0, 0)
    }

    private fun showBoardSettings(code: String, board: DrawingView) {
        db.collection("boards").document(code).get().addOnSuccessListener { snapshot ->
            @Suppress("UNCHECKED_CAST")
            val names = (snapshot.get("memberNames") as? Map<String, String>)?.values.orEmpty()
            @Suppress("UNCHECKED_CAST")
            val memberCount = (snapshot.get("members") as? Map<String, Boolean>)?.size ?: names.size
            val people = if (names.isEmpty()) "표시할 참여자 정보가 없습니다." else names.joinToString("\n") { "• $it" }
            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@MainActivity).apply {
                    text = "칠판 이름\n${snapshot.getString("name") ?: "이름 없는 칠판"}\n\n참여 코드\n$code\n\n참여자 ${memberCount}명\n$people"
                    textSize = 16f
                    setTextColor(Color.rgb(45, 52, 49))
                    setLineSpacing(4.dp.toFloat(), 1f)
                })
                addView(Button(this@MainActivity).apply {
                    text = "칠판 표지 변경"
                    isAllCaps = false
                    primaryStyle()
                    setOnClickListener {
                        coverBoardCode = code
                        pickCover.launch("image/*")
                    }
                }, LinearLayout.LayoutParams(-1, 50.dp).apply { topMargin = 14.dp })
                addView(TextView(this@MainActivity).apply {
                    text = "칠판 배경색"
                    textSize = 16f
                    setTextColor(Color.rgb(45, 52, 49))
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 18.dp })
                addView(LinearLayout(this@MainActivity).apply {
                    gravity = Gravity.CENTER
                    listOf(
                        "상아색" to 0xFFEFE9DE.toInt(),
                        "진회색" to 0xFF2E2E2E.toInt(),
                        "칠판색" to 0xFF1A422F.toInt()
                    ).forEach { (label, color) ->
                        addView(Button(this@MainActivity).apply {
                            text = label
                            textSize = 13f
                            isAllCaps = false
                            setTextColor(if (color == 0xFF2E2E2E.toInt() || color == 0xFF1A422F.toInt()) Color.WHITE else Color.BLACK)
                            background = rounded(color, 999f)
                            setOnClickListener {
                                prefs.edit().putInt("background_$code", color).apply()
                                db.collection("boards").document(code).update("backgroundColor", color.toLong())
                                board.setBoardBackgroundColor(color, true)
                            }
                        }, LinearLayout.LayoutParams(0, 48.dp, 1f).apply {
                            marginStart = 4.dp
                            marginEnd = 4.dp
                        })
                    }
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8.dp })
            }
            showRoundDialog("칠판 설정", info, "닫기") {}
        }.addOnFailureListener { toast("칠판 설정을 불러오지 못했습니다.") }
    }

    private fun showCoverEditor(code: String, source: android.graphics.Bitmap) {
        val editor = CoverCropView(this, source)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(TextView(this@MainActivity).apply {
                text = "한 손가락으로 이동하고 두 손가락으로 크기와 회전을 조절하세요."
                textSize = 14f
                setTextColor(Color.rgb(116, 110, 105))
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12.dp })
            addView(editor, LinearLayout.LayoutParams(190.dp, 251.dp))
        }
        showRoundDialog("표지 위치 및 크기", content, "저장") {
            val cover = editor.export()
            val bytes = java.io.ByteArrayOutputStream().use {
                cover.compress(android.graphics.Bitmap.CompressFormat.JPEG, 84, it)
                it.toByteArray()
            }
            cover.recycle()
            source.recycle()
            db.collection("boards").document(code).update("cover", Blob.fromBytes(bytes))
                .addOnSuccessListener {
                    java.io.File(filesDir, "cover_$code.jpg").writeBytes(bytes)
                    BoardWidget.updateAll(this)
                    toast("칠판 표지를 변경했습니다.")
                }
                .addOnFailureListener { toast("칠판 표지를 변경하지 못했습니다.") }
        }
    }

    private fun showRoundDialog(title: String, content: View, positiveText: String, onPositive: () -> Unit): Dialog {
        val dialog = Dialog(this)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp, 22.dp, 20.dp, 18.dp)
            background = rounded(Color.rgb(250, 248, 241), 24f)
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 21f
                setTextColor(Color.rgb(35, 48, 42))
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 18.dp })
            addView(content, LinearLayout.LayoutParams(-1, -2))
            addView(LinearLayout(this@MainActivity).apply {
                gravity = Gravity.END
                clipChildren = false
                clipToPadding = false
                setPadding(0, 6.dp, 0, 8.dp)
                if (positiveText != "닫기") addView(Button(this@MainActivity).apply {
                    text = "닫기"
                    isAllCaps = false
                    secondaryStyle()
                    setOnClickListener { dialog.dismiss() }
                }, LinearLayout.LayoutParams(0, 52.dp, 1f).apply { marginEnd = 7.dp })
                addView(Button(this@MainActivity).apply {
                    text = positiveText
                    isAllCaps = false
                    primaryStyle()
                    setOnClickListener {
                        dialog.dismiss()
                        onPositive()
                    }
                }, LinearLayout.LayoutParams(0, 52.dp, 1f).apply {
                    if (positiveText != "닫기") marginStart = 7.dp
                })
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 14.dp })
        }
        dialog.setContentView(card)
        applyPretendard(card)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = 0.42f }
        }
        dialog.setOnShowListener {
            val maxWidth = if (resources.configuration.screenWidthDp >= 600) 280.dp else 240.dp
            dialog.window?.setLayout(minOf(resources.displayMetrics.widthPixels - 96.dp, maxWidth), WindowManager.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
        return dialog
    }

    private fun memberLabel() = auth.currentUser?.displayName
        ?: auth.currentUser?.email
        ?: auth.currentUser?.uid?.take(8)
        ?: "참여자"

    private fun swatch(color: Int, selected: Boolean) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke((if (selected) 4 else 1).dp, if (selected) Color.WHITE else Color.TRANSPARENT)
    }

    private fun centeredColumn() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(42.dp, 42.dp, 42.dp, 42.dp)
        setBackgroundColor(Color.rgb(243, 240, 237))
    }

    private fun Button.primaryStyle() {
        setTextColor(Color.rgb(31, 31, 31))
        textSize = 16f
        elevation = 2.dp.toFloat()
        background = rounded(Color.rgb(255, 188, 53), 999f)
    }

    private fun Button.secondaryStyle() {
        setTextColor(Color.rgb(31, 31, 31))
        textSize = 16f
        background = rounded(Color.WHITE, 999f)
    }

    private fun rounded(color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * resources.displayMetrics.density
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun showContent(view: View) {
        applyPretendard(view)
        setContentView(view)
    }

    private fun applyPretendard(view: View) {
        if (view is TextView) view.typeface = Typeface.create(pretendard, view.typeface?.style ?: Typeface.NORMAL)
        if (view is EditText) view.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        if (view.isClickable && view !is EditText) applyPressAnimation(view)
        if (view is android.view.ViewGroup) repeat(view.childCount) { applyPretendard(view.getChildAt(it)) }
    }

    private fun applyPressAnimation(view: View) {
        view.setOnTouchListener { target, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> target.animate().scaleX(0.96f).scaleY(0.96f).alpha(0.88f).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    target.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(130).start()
            }
            false
        }
    }
    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
