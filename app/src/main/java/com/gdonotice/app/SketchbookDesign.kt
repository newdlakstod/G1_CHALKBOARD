package com.gdonotice.app

enum class WindowMode { COMPACT, MEDIUM, EXPANDED }
enum class CanvasKind { DOCUMENT, WEB, CUSTOM }
enum class EntryRoute { COVER, HOME }

enum class CanvasPreset(
    val kind: CanvasKind,
    val width: Int,
    val height: Int
) {
    A4(CanvasKind.DOCUMENT, 210, 297),
    A5(CanvasKind.DOCUMENT, 148, 210),
    B5(CanvasKind.DOCUMENT, 176, 250),
    SQUARE(CanvasKind.WEB, 1, 1),
    PHOTO(CanvasKind.WEB, 4, 3),
    WIDE(CanvasKind.WEB, 16, 9),
    STORY(CanvasKind.WEB, 9, 16),
    CUSTOM(CanvasKind.CUSTOM, 0, 0);

    val ratio: Float get() = if (height == 0) 1f else width.toFloat() / height
}

object SketchbookDesign {
    const val COBALT = 0xFF123FA5.toInt()
    const val PAPER = 0xFFF5EBD8.toInt()
    const val INK = 0xFF252525.toInt()

    fun windowMode(widthDp: Int) = when {
        widthDp < 600 -> WindowMode.COMPACT
        widthDp < 840 -> WindowMode.MEDIUM
        else -> WindowMode.EXPANDED
    }

    fun validCustomSize(width: Int, height: Int) =
        width in 320..8192 && height in 320..8192 && width.toLong() * height <= 33_554_432L

    fun entryRoute(isSignedIn: Boolean, hasEntered: Boolean) =
        if (isSignedIn && hasEntered) EntryRoute.HOME else EntryRoute.COVER

    fun pixelSize(preset: CanvasPreset): Pair<Int, Int> = when (preset) {
        CanvasPreset.WIDE -> 1600 to 900
        CanvasPreset.CUSTOM -> 1080 to 1080
        else -> {
            val width = if (preset.ratio >= 1f) 1600 else 1000
            width to (width / preset.ratio).toInt()
        }
    }
}
