package br.com.celularsaudavel

import android.app.Application
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Guarda o detalhe de qualquer falha num arquivo, para mostrar na próxima abertura
 * e o usuário poder enviar ao desenvolvedor.
 */
class CelularSaudavelApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                val info = buildString {
                    appendLine("Celular Saudável ${BuildConfig.VERSION_NAME} (${BuildConfig.FLAVOR})")
                    appendLine("Aparelho: ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    appendLine("Thread: ${thread.name}")
                    appendLine()
                    append(sw.toString().take(12_000))
                }
                crashFile(this).writeText(info)
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
    }

    companion object {
        fun crashFile(app: Application): File = File(app.filesDir, "ultima_falha.txt")
    }
}
