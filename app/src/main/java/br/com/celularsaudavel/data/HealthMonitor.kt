package br.com.celularsaudavel.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import br.com.celularsaudavel.MainActivity
import br.com.celularsaudavel.R
import br.com.celularsaudavel.model.DAY_MS
import br.com.celularsaudavel.model.computeHealth
import br.com.celularsaudavel.model.formatBytes
import java.util.concurrent.TimeUnit

/** Preferências do acompanhamento. */
class MonitorPrefs(context: Context) {
    private val p = context.getSharedPreferences("monitor", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = p.getBoolean("enabled", true)
        set(v) = p.edit().putBoolean("enabled", v).apply()

    var storageAlerts: Boolean
        get() = p.getBoolean("storage", true)
        set(v) = p.edit().putBoolean("storage", v).apply()

    var weeklyCheckup: Boolean
        get() = p.getBoolean("weekly", true)
        set(v) = p.edit().putBoolean("weekly", v).apply()

    var backupReminder: Boolean
        get() = p.getBoolean("backup", true)
        set(v) = p.edit().putBoolean("backup", v).apply()

    var lastLevel: Int
        get() = p.getInt("lastLevel", 0)
        set(v) = p.edit().putInt("lastLevel", v).apply()

    var lastWeekly: Long
        get() = p.getLong("lastWeekly", 0)
        set(v) = p.edit().putLong("lastWeekly", v).apply()

    var usedAtLastWeekly: Long
        get() = p.getLong("usedAtLastWeekly", 0)
        set(v) = p.edit().putLong("usedAtLastWeekly", v).apply()

    var lastBackupReminder: Long
        get() = p.getLong("lastBackupReminder", 0)
        set(v) = p.edit().putLong("lastBackupReminder", v).apply()

    /** Momento do último arquivo verificado no Drive (gravado pelo backup). */
    var lastBackupAt: Long
        get() = p.getLong("lastBackupAt", 0)
        set(v) = p.edit().putLong("lastBackupAt", v).apply()
}

object HealthNotifier {
    private const val CHANNEL = "saude"

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun notify(context: Context, id: Int, title: String, text: String) {
        if (!canNotify(context)) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Saúde do celular", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Avisos sobre espaço, check-up semanal e backup"
                }
            )
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setColor(0xFF1E7F55.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(id, n)
        } catch (_: SecurityException) {
        }
    }
}

/**
 * Acompanhamento em segundo plano (a cada ~12 h). Avisa só o que importa, sem alarmismo:
 * - quando o armazenamento passa de 80%, 90% e 95% (uma vez por faixa);
 * - check-up semanal com o índice e quanto o celular encheu na semana;
 * - lembrete de backup quando o último foi há mais de 30 dias.
 */
class HealthMonitorWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    companion object {
        private const val NAME = "health-monitor"
        const val ID_STORAGE = 101
        const val ID_WEEKLY = 102
        const val ID_BACKUP = 103

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<HealthMonitorWorker>(12, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }

        /** Roda as mesmas checagens na hora (usado no botão de teste). */
        fun runChecks(context: Context, force: Boolean = false) {
            val prefs = MonitorPrefs(context)
            if (!prefs.enabled && !force) return
            val storage = MediaRepository(context).storageInfo()
            val pct = (storage.usedFraction * 100).toInt()
            val now = System.currentTimeMillis()

            // 1) Faixas de ocupação
            if (prefs.storageAlerts) {
                val level = when {
                    pct >= 95 -> 95
                    pct >= 90 -> 90
                    pct >= 80 -> 80
                    else -> 0
                }
                if (level > prefs.lastLevel || (force && level > 0)) {
                    val (title, text) = when (level) {
                        95 -> "Seu celular está quase sem espaço" to
                            "Restam ${formatBytes(storage.freeBytes)}. Fotos e atualizações podem falhar. Vamos ver o que dá para liberar com segurança?"
                        90 -> "Espaço ficando curto" to
                            "Seu celular está $pct% ocupado (${formatBytes(storage.freeBytes)} livres). Um check-up rápido ajuda a encontrar o que sobra."
                        else -> "Hora de um check-up" to
                            "Seu celular passou de 80% ocupado. Que tal revisar duplicadas, pastas do WhatsApp e o cache dos apps?"
                    }
                    HealthNotifier.notify(context, ID_STORAGE, title, text)
                }
                // Quando o espaço volta a sobrar, a faixa é liberada para avisar de novo no futuro.
                prefs.lastLevel = level
            }

            // 2) Check-up semanal
            if (prefs.weeklyCheckup && (force || now - prefs.lastWeekly > 7 * DAY_MS)) {
                val score = computeHealth(storage, 0, null)
                val grew = if (prefs.usedAtLastWeekly > 0) storage.usedBytes - prefs.usedAtLastWeekly else 0L
                val growth = when {
                    prefs.usedAtLastWeekly == 0L -> ""
                    grew > 100_000_000L -> " Nesta semana ocupou mais ${formatBytes(grew)}."
                    grew < -100_000_000L -> " Nesta semana você liberou ${formatBytes(-grew)}. 👏"
                    else -> " O espaço ficou estável nesta semana."
                }
                HealthNotifier.notify(
                    context, ID_WEEKLY,
                    "Check-up semanal: ${score?.value ?: "--"}/100 · ${score?.label ?: ""}",
                    "${formatBytes(storage.freeBytes)} livres ($pct% ocupado).$growth"
                )
                prefs.lastWeekly = now
                prefs.usedAtLastWeekly = storage.usedBytes
            }

            // 3) Lembrete de backup
            val driveConnected = DriveRepository(context).connected
            if (prefs.backupReminder && driveConnected) {
                val last = prefs.lastBackupAt
                val stale = last == 0L || now - last > 30 * DAY_MS
                if (stale && (force || now - prefs.lastBackupReminder > 14 * DAY_MS)) {
                    HealthNotifier.notify(
                        context, ID_BACKUP,
                        "Suas fotos novas estão protegidas?",
                        if (last == 0L) "Você conectou o Google Drive, mas ainda não fez backup. Proteja suas fotos antes de liberar espaço."
                        else "Seu último backup foi há ${(now - last) / DAY_MS} dias. Toque para proteger as fotos novas."
                    )
                    prefs.lastBackupReminder = now
                }
            }
        }
    }

    override suspend fun doWork(): Result {
        return try {
            runChecks(applicationContext)
            Result.success()
        } catch (_: Exception) {
            Result.success()
        }
    }
}
