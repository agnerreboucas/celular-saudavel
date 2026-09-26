package br.com.celularsaudavel.data

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import br.com.celularsaudavel.MainActivity
import br.com.celularsaudavel.R
import br.com.celularsaudavel.model.DAY_MS
import br.com.celularsaudavel.model.computeHealth
import br.com.celularsaudavel.model.formatBytes

/** Widget da tela inicial: índice de saúde, estado e espaço livre. */
class HealthWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(context)) }
    }

    companion object {
        fun views(context: Context): RemoteViews {
            val rv = RemoteViews(context.packageName, R.layout.widget_saude)
            try {
                val prefs = MonitorPrefs(context)
                val st = MediaRepository(context).storageInfo()
                val recent = prefs.lastScore >= 0 && System.currentTimeMillis() - prefs.lastScoreAt < 7 * DAY_MS
                val score = if (recent) prefs.lastScore else computeHealth(st, 0, null)?.value ?: 0
                val (label, color) = when {
                    score >= 80 -> "Saudável 😊" to 0xFF1E7F55.toInt()
                    score >= 60 -> "Atenção 😐" to 0xFFB8860B.toInt()
                    score >= 40 -> "Precisa de cuidado 😓" to 0xFFE0782F.toInt()
                    else -> "Urgente 🥵" to 0xFFCC3D3D.toInt()
                }
                rv.setTextViewText(R.id.widget_score, "$score")
                rv.setTextViewText(R.id.widget_label, label)
                rv.setTextColor(R.id.widget_label, color)
                rv.setTextViewText(R.id.widget_free, "${formatBytes(st.freeBytes)} livres · ${(st.usedFraction * 100).toInt()}% ocupado")
            } catch (_: Exception) {
            }
            val open = PendingIntent.getActivity(
                context, 7, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            rv.setOnClickPendingIntent(R.id.widget_root, open)
            return rv
        }

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, HealthWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, views(context))
        }
    }
}
