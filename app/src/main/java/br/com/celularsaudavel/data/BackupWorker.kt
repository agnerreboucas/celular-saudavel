package br.com.celularsaudavel.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import br.com.celularsaudavel.R

/**
 * Envia a fila para o Drive em segundo plano, arquivo por arquivo.
 * Se cair a conexão ou o app fechar, recomeça do próximo arquivo não verificado.
 */
class BackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    companion object {
        const val NAME = "backup"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"
        private const val CHANNEL = "backup"
        private const val NOTIF_ID = 42

        fun start(context: Context, wifiOnly: Boolean) {
            val req = OneTimeWorkRequestBuilder<BackupWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, req)
        }

        fun stop(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }

    private val db = BackupDb(ctx)
    private val drive = DriveRepository(ctx)

    private fun foreground(done: Int, total: Int): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Backup", NotificationManager.IMPORTANCE_LOW))
        }
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Protegendo seus arquivos")
            .setContentText("$done de $total enviados e verificados")
            .setProgress(total.coerceAtLeast(1), done, total == 0)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIF_ID, n)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foreground(0, 0)

    override suspend fun doWork(): Result {
        val startSummary = db.summary()
        val total = startSummary.pending
        var done = 0
        try {
            setForeground(foreground(0, total))
        } catch (_: Exception) {
            // Sem permissão de serviço em primeiro plano: segue enquanto o app estiver aberto.
        }

        try {
            drive.refreshToken()
            val root = drive.folderId(DriveRepository.ROOT_FOLDER, null)
            val photos = drive.folderId("Fotos", root)
            val videos = drive.folderId("Vídeos", root)

            while (!isStopped) {
                val batch = db.nextPending(20)
                if (batch.isEmpty()) break
                for (row in batch) {
                    if (isStopped) break
                    db.mark(row.uri, BackupState.UPLOADING)
                    try {
                        val parent = if (row.category == "video") videos else photos
                        val r = drive.upload(Uri.parse(row.uri), row.name, row.mime, parent)
                        if (r.verified) {
                            db.mark(row.uri, BackupState.VERIFIED, driveId = r.driveId, md5 = r.localMd5)
                            MonitorPrefs(applicationContext).lastBackupAt = System.currentTimeMillis()
                        } else {
                            db.mark(row.uri, BackupState.FAILED, driveId = r.driveId, error = "Conferência não bateu")
                        }
                    } catch (e: DriveFullException) {
                        db.mark(row.uri, BackupState.QUEUED, error = "Drive cheio")
                        return Result.failure(workDataOf(KEY_ERROR to "O Google Drive ficou sem espaço."))
                    } catch (e: NeedsConsentException) {
                        db.mark(row.uri, BackupState.QUEUED)
                        return Result.failure(workDataOf(KEY_ERROR to "Reconecte o Google Drive."))
                    } catch (e: java.io.FileNotFoundException) {
                        db.mark(row.uri, BackupState.FAILED, error = "Arquivo não existe mais")
                    } catch (e: java.io.IOException) {
                        // Rede caiu: devolve para a fila e deixa o WorkManager tentar de novo depois.
                        db.mark(row.uri, BackupState.QUEUED, error = e.message)
                        return Result.retry()
                    } catch (e: Exception) {
                        db.mark(row.uri, BackupState.FAILED, error = e.message)
                    }
                    done++
                    setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to total))
                    if (done % 5 == 0) {
                        try {
                            setForeground(foreground(done, total))
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        } catch (e: NeedsConsentException) {
            return Result.failure(workDataOf(KEY_ERROR to "Reconecte o Google Drive."))
        } catch (e: java.io.IOException) {
            return Result.retry()
        } catch (e: Exception) {
            return Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Erro no backup")))
        }
        return Result.success()
    }
}
