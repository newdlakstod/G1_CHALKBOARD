package com.gdonotice.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import java.io.File

class BoardWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach {
            context.getSharedPreferences("polaroid_widgets", Context.MODE_PRIVATE).edit()
                .remove("board_$it").remove("title_$it").remove("meta_$it").apply()
            File(context.filesDir, "polaroid_widget_$it.jpg").delete()
        }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BoardWidget::class.java))
            ids.forEach { update(context, manager, it) }
        }

        fun update(context: Context, manager: AppWidgetManager, id: Int) {
            manager.updateAppWidget(id, views(context, id))
        }

        private fun views(context: Context, id: Int): RemoteViews {
            val code = context.getSharedPreferences("polaroid_widgets", Context.MODE_PRIVATE).getString("board_$id", null)
            val currentCover = code?.let { File(context.filesDir, "cover_$it.jpg") }
            val selectedThumbnail = File(context.filesDir, "polaroid_widget_$id.jpg")
            val image = listOfNotNull(currentCover, selectedThumbnail).firstOrNull { it.exists() }?.let {
                BitmapFactory.decodeFile(it.path)
            }
            val preferences = context.getSharedPreferences("polaroid_widgets", Context.MODE_PRIVATE)
            val title = preferences.getString("title_$id", "칠판을 선택하세요")
            val meta = preferences.getString("meta_$id", "생성일 · 생성자")
            val openApp = PendingIntent.getActivity(
                context,
                id,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            return RemoteViews(context.packageName, R.layout.board_widget).apply {
                image?.let { setImageViewBitmap(R.id.widget_drawing, it) }
                setTextViewText(R.id.widget_title, title)
                setTextViewText(R.id.widget_meta, meta)
                setOnClickPendingIntent(R.id.widget_board, openApp)
            }
        }
    }
}
