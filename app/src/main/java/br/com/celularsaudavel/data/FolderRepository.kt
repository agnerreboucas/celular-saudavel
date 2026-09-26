package br.com.celularsaudavel.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import br.com.celularsaudavel.model.FolderCategory
import br.com.celularsaudavel.model.LocalFile
import java.io.File

/**
 * Pastas que a permissão de fotos não mostra: WhatsApp (inclusive as ocultas),
 * Telegram, Instagram, Downloads, instaladores e temporários.
 * Exige "acesso a todos os arquivos" (Android 11+) ou leitura/escrita (Android 8–10).
 */
class FolderRepository(private val context: Context) {

    private val root: File = Environment.getExternalStorageDirectory()

    fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

    private fun f(rel: String) = File(root, rel)

    private val whatsappMediaRoots: List<File>
        get() = listOf(
            f("Android/media/com.whatsapp/WhatsApp/Media"),
            f("WhatsApp/Media"),
            f("Android/media/com.whatsapp.w4b/WhatsApp Business/Media"),
            f("WhatsApp Business/Media"),
        ).filter { it.isDirectory }

    private fun wa(vararg names: String): List<File> =
        whatsappMediaRoots.flatMap { base ->
            names.map { n -> File(base, n) } + names.map { n -> File(base, n.replace("WhatsApp", "WhatsApp Business")) }
        }.filter { it.isDirectory }.distinctBy { it.absolutePath }

    private fun walk(dirs: List<File>, filter: (File) -> Boolean = { true }, maxDepth: Int = 12): List<LocalFile> {
        val out = ArrayList<LocalFile>()
        for (d in dirs) {
            try {
                d.walkTopDown().maxDepth(maxDepth)
                    .filter { it.isFile && filter(it) }
                    .forEach { out += LocalFile(it.absolutePath, it.name, it.length(), it.lastModified()) }
            } catch (_: Exception) {
            }
        }
        return out.distinctBy { it.path }.sortedByDescending { it.sizeBytes }
    }

    fun scan(): List<FolderCategory> {
        if (!hasAllFilesAccess()) return emptyList()
        val tempExt = setOf("tmp", "temp", "log", "part", "crdownload", "partial")
        val list = listOf(
            // ---------- Seguros ----------
            FolderCategory(
                "temp", "🧹", "Arquivos temporários",
                "Miniaturas que a galeria recria sozinha e downloads incompletos. Pode apagar sem medo.",
                safe = true, group = "Temporários",
                files = walk(listOf(f("DCIM/.thumbnails"), f("Pictures/.thumbnails"), f("Movies/.thumbnails"), f("Music/.thumbnails"))) +
                    walk(listOf(f("Download"), f("Documents")), { it.extension.lowercase() in tempExt }, maxDepth = 3)
            ),
            FolderCategory(
                "wa_status", "👀", "Status já vistos do WhatsApp",
                "Cópias dos status que você assistiu. Ficam escondidas e somem do WhatsApp em 24 h de qualquer jeito.",
                safe = true, group = "WhatsApp",
                files = walk(wa(".Statuses"))
            ),
            FolderCategory(
                "apk", "📦", "Instaladores de apps (APK)",
                "Arquivos usados para instalar apps. Depois de instalado, o arquivo não é mais necessário.",
                safe = true, group = "Downloads",
                files = walk(listOf(f("Download"), f("Documents"), root), { it.extension.equals("apk", true) || it.extension.equals("apks", true) || it.extension.equals("xapk", true) }, maxDepth = 3)
            ),
            // ---------- Revisar com calma ----------
            FolderCategory(
                "wa_images", "🖼️", "Fotos do WhatsApp",
                "Fotos recebidas e enviadas, incluindo as pastas ocultas \"Enviadas\" e \"Particular\". Podem ser lembranças: revise antes.",
                safe = false, group = "WhatsApp",
                files = walk(wa("WhatsApp Images"))
            ),
            FolderCategory(
                "wa_video", "🎬", "Vídeos do WhatsApp",
                "Vídeos recebidos e enviados, inclusive os encaminhados que só ocupam espaço.",
                safe = false, group = "WhatsApp",
                files = walk(wa("WhatsApp Video", "WhatsApp Animated Gifs"))
            ),
            FolderCategory(
                "wa_audio", "🎙️", "Áudios do WhatsApp",
                "Mensagens de voz e áudios recebidos. Apagar aqui remove o áudio também da conversa.",
                safe = false, group = "WhatsApp",
                files = walk(wa("WhatsApp Voice Notes", "WhatsApp Audio"))
            ),
            FolderCategory(
                "wa_docs", "📄", "Documentos do WhatsApp",
                "PDFs, planilhas e outros arquivos recebidos.",
                safe = false, group = "WhatsApp",
                files = walk(wa("WhatsApp Documents"))
            ),
            FolderCategory(
                "wa_stickers", "🙂", "Figurinhas do WhatsApp",
                "Figurinhas baixadas. As favoritas podem precisar ser baixadas de novo.",
                safe = false, group = "WhatsApp",
                files = walk(wa("WhatsApp Stickers"))
            ),
            FolderCategory(
                "telegram", "✈️", "Telegram",
                "Fotos, vídeos e arquivos baixados pelo Telegram.",
                safe = false, group = "Outros apps",
                files = walk(listOf(f("Android/media/org.telegram.messenger/Telegram"), f("Telegram")))
            ),
            FolderCategory(
                "instagram", "📸", "Instagram",
                "Fotos e vídeos salvos ou publicados pelo Instagram.",
                safe = false, group = "Outros apps",
                files = walk(listOf(f("Pictures/Instagram"), f("Movies/Instagram"), f("DCIM/Instagram"), f("Android/media/com.instagram.android")))
            ),
            FolderCategory(
                "downloads", "📥", "Downloads",
                "Tudo o que foi baixado, inclusive arquivos ocultos. Revise antes de apagar.",
                safe = false, group = "Downloads",
                files = walk(listOf(f("Download")), { !it.extension.equals("apk", true) })
            ),
        )
        return list.filter { it.files.isNotEmpty() }
    }

    /** Maiores pastas do armazenamento compartilhado (1º e 2º nível). */
    fun biggestFolders(limit: Int = 12): List<Pair<String, Long>> {
        if (!hasAllFilesAccess()) return emptyList()
        val sizes = ArrayList<Pair<String, Long>>()
        root.listFiles()?.filter { it.isDirectory }?.forEach { top ->
            val children = top.listFiles()?.filter { it.isDirectory } ?: emptyList()
            val targets = if (top.name == "Android") children.filter { it.name == "media" } else listOf(top)
            targets.forEach { d ->
                val size = try {
                    d.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                } catch (_: Exception) {
                    0L
                }
                if (size > 0) sizes += d.absolutePath.removePrefix(root.absolutePath + "/") to size
            }
        }
        return sizes.sortedByDescending { it.second }.take(limit)
    }

    /** Apaga de verdade. Chamado só depois da confirmação do usuário. */
    fun delete(files: List<LocalFile>): List<LocalFile> {
        val deleted = files.filter { lf ->
            try {
                File(lf.path).delete()
            } catch (_: Exception) {
                false
            }
        }
        if (deleted.isNotEmpty()) {
            try {
                MediaScannerConnection.scanFile(context, deleted.map { it.path }.toTypedArray(), null, null)
            } catch (_: Exception) {
            }
        }
        return deleted
    }
}
