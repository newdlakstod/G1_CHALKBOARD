package com.gdonotice.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.view.View
import android.widget.RemoteViews
import java.io.File

class BoardWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(context)) }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BoardWidget::class.java))
            ids.forEach { manager.updateAppWidget(it, views(context)) }
        }

        private fun views(context: Context): RemoteViews {
            val code = context.getSharedPreferences("board", Context.MODE_PRIVATE).getString("widgetCode", null)
            val cover = code?.let { File(context.filesDir, "cover_$it.jpg") }
            val drawing = code?.let { File(context.filesDir, "board_$it.png") }
            val image = listOfNotNull(cover, drawing).firstOrNull { it.exists() }?.let {
                BitmapFactory.decodeFile(it.path)
            }
            val openApp = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            return RemoteViews(context.packageName, R.layout.board_widget).apply {
                setViewVisibility(R.id.widget_drawing, if (image == null) View.GONE else View.VISIBLE)
                setViewVisibility(R.id.widget_hint, if (image == null) View.VISIBLE else View.GONE)
                image?.let { setImageViewBitmap(R.id.widget_drawing, it) }
                setOnClickPendingIntent(R.id.widget_board, openApp)
            }
        }
    }
}
