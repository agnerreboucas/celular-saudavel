package br.com.celularsaudavel.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import br.com.celularsaudavel.model.DAY_MS
import br.com.celularsaudavel.model.computeHealth
import br.com.celularsaudavel.model.formatBytes
import java.util.Calendar

/**
 * Boletim diário da saúde do celular, na hora escolhida (padrão 5h).
 * Usa um alarme que funciona mesmo com o celular em repouso, sem pedir permissão de alarme exato:
 * o Android pode atrasar alguns minutos para economizar bateria.
 */
object DailyBulletin {
    private const val REQ = 505
    const val ID_DAILY = 104

    private fun intent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQ, Intent(context, DailyReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /** Próximo disparo conforme frequência (diário, semanal, mensal), dia e horário escolhidos. */
    fun nextTrigger(prefs: MonitorPrefs, now: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, prefs.dailyHour)
            set(Calendar.MINUTE, prefs.dailyMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        when (prefs.bulletinFreq) {
            "W" -> {
                c.set(Calendar.DAY_OF_WEEK, prefs.weekDay)
                while (c.timeInMillis <= now + 60_000) c.add(Calendar.WEEK_OF_YEAR, 1)
            }
            "M" -> {
                c.set(Calendar.DAY_OF_MONTH, prefs.monthDay.coerceIn(1, 28))
                while (c.timeInMillis <= now + 60_000) c.add(Calendar.MONTH, 1)
            }
            else -> if (c.timeInMillis <= now + 60_000) c.add(Calendar.DAY_OF_YEAR, 1)
        }
        return c.timeInMillis
    }

    fun schedule(context: Context) {
        val prefs = MonitorPrefs(context)
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(intent(context))
        if (!prefs.enabled || !prefs.dailyEnabled) return
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger(prefs), intent(context))
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(intent(context))
    }

    /** Monta e envia o boletim. */
    fun send(context: Context) {
        val prefs = MonitorPrefs(context)
        val st = MediaRepository(context).storageInfo()
        val pct = (st.usedFraction * 100).toInt()
        val recent = prefs.lastScore >= 0 && System.currentTimeMillis() - prefs.lastScoreAt < 7 * DAY_MS
        val score = if (recent) prefs.lastScore else computeHealth(st, 0, null)?.value ?: 0
        val h = when {
            score >= 80 -> "Saudável 😊"
            score >= 60 -> "Atenção 😐"
            score >= 40 -> "Precisa de cuidado 😓"
            else -> "Urgente 🥵"
        }
        val since = when (prefs.bulletinFreq) { "W" -> "a semana passada"; "M" -> "o mês passado"; else -> "ontem" }
        val prev = prefs.usedAtLastDaily
        val diff = if (prev > 0) st.usedBytes - prev else 0L
        val change = when {
            prev == 0L -> ""
            diff > 50_000_000L -> " Desde $since ocupou mais ${formatBytes(diff)}."
            diff < -50_000_000L -> " Desde $since você liberou ${formatBytes(-diff)}. 👏"
            else -> " Estável desde $since."
        }
        val tip = when {
            pct >= 90 -> " Vale abrir o app e liberar espaço hoje."
            pct >= 80 -> " Um check-up rápido ajuda a manter a folga."
            else -> ""
        }
        HealthNotifier.notify(
            context, ID_DAILY,
            (when {
                prefs.bulletinFreq == "W" -> "Resumo da semana"
                prefs.bulletinFreq == "M" -> "Resumo do mês"
                prefs.dailyHour < 12 -> "Bom dia"
                prefs.dailyHour < 18 -> "Boa tarde"
                else -> "Boa noite"
            }) + "! Saúde do celular: $score/100 · $h",
            "${formatBytes(st.freeBytes)} livres ($pct% ocupado).$change$tip",
            channel = HealthNotifier.CHANNEL_DAILY
        )
        prefs.usedAtLastDaily = st.usedBytes
        HealthWidget.updateAll(context)
    }
}

/** Dispara o boletim e já agenda o do dia seguinte. */
class DailyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        Thread {
            try {
                val prefs = MonitorPrefs(context)
                if (prefs.enabled && prefs.dailyEnabled && isPremium(context)) DailyBulletin.send(context)
            } catch (_: Exception) {
            } finally {
                runCatching { DailyBulletin.schedule(context) }
                pending.finish()
            }
        }.start()
    }
}

/** Reagenda depois de reiniciar o celular, mudar a hora ou atualizar o app. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        runCatching { DailyBulletin.schedule(context) }
        runCatching { HealthWidget.updateAll(context) }
    }
}
