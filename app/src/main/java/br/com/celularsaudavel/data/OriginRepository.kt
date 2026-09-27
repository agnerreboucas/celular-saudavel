package br.com.celularsaudavel.data

import android.os.Environment
import br.com.celularsaudavel.model.FolderCategory
import br.com.celularsaudavel.model.LocalFile
import java.io.File

/** Tipo de arquivo usado na tela "Por origem". */
object FileType {
    const val PHOTOS = "Fotos"
    const val VIDEOS = "Vídeos"
    const val AUDIO = "Áudios"
    const val DOCS = "Documentos"
    val all = listOf(PHOTOS, VIDEOS, AUDIO, DOCS)
}

/**
 * Separa fotos, vídeos, áudios e documentos pela ORIGEM (WhatsApp, gravador, câmera, Downloads…),
 * olhando o caminho de cada arquivo. Assim dá para selecionar, por exemplo, todos os áudios do WhatsApp de uma vez.
 * Precisa do "acesso a todos os arquivos" (versão de instalação direta).
 */
class OriginRepository(private val folders: FolderRepository) {

    private val root: File = Environment.getExternalStorageDirectory()

    private val photoExt = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp")
    private val videoExt = setOf("mp4", "3gp", "mkv", "webm", "mov", "avi", "m4v")
    private val audioExt = setOf("mp3", "m4a", "aac", "ogg", "opus", "wav", "amr", "flac", "3ga", "awb", "wma", "mid")
    private val docExt = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "txt", "csv", "rtf", "epub",
        "zip", "rar", "7z", "vcf", "html", "htm"
    )

    private fun typeOf(f: File): String? {
        val e = f.extension.lowercase()
        return when (e) {
            in photoExt -> FileType.PHOTOS
            in videoExt -> FileType.VIDEOS
            in audioExt -> FileType.AUDIO
            in docExt -> FileType.DOCS
            else -> null
        }
    }

    /** Pastas que não fazem sentido listar: dados internos, miniaturas e a lixeira do próprio app. */
    private fun skipDir(d: File): Boolean {
        val rel = d.absolutePath.removePrefix(root.absolutePath).lowercase()
        return rel == "/android/data" || rel == "/android/obb" || rel.startsWith("/.celularsaudavel") ||
            d.name.equals(".thumbnails", true) || d.name.startsWith(".trashed", true) || d.name.equals(".cache", true)
    }

    /** Devolve (id, título, descrição) da origem de um arquivo. */
    private fun originOf(f: File, type: String): Triple<String, String, String> {
        val p = f.absolutePath.removePrefix(root.absolutePath).lowercase()
        val sent = p.contains("/sent/")
        fun t(id: String, title: String, desc: String) = Triple(id, title, desc)
        return when {
            p.contains("whatsapp business") || p.contains("com.whatsapp.w4b") -> when {
                p.contains("voice notes") -> t("wab_voice", "WhatsApp Business · mensagens de voz", "Áudios gravados nas conversas do WhatsApp Business.")
                else -> t("wab", "WhatsApp Business", "Arquivos recebidos e enviados pelo WhatsApp Business.")
            }
            p.contains("whatsapp") -> when {
                p.contains("voice notes") -> t("wa_voice", "WhatsApp · mensagens de voz", "Os áudios gravados nas conversas (aquele microfone do WhatsApp).")
                type == FileType.AUDIO -> t("wa_audio", "WhatsApp · áudios e músicas recebidos", "Arquivos de áudio encaminhados: músicas, programas, correntes.")
                p.contains("status") -> t("wa_status", "WhatsApp · status", "Status que você viu ou salvou.")
                sent -> t("wa_sent", "WhatsApp · enviados por você", "Cópias do que você mandou. O original costuma continuar na sua galeria.")
                else -> t("wa", "WhatsApp · recebidos", "O que chegou nas conversas e grupos.")
            }
            p.contains("telegram") -> t("telegram", "Telegram", "Arquivos baixados pelo Telegram.")
            p.contains("instagram") -> t("instagram", "Instagram", "Salvos ou publicados pelo Instagram.")
            p.contains("messenger") || p.contains("facebook") -> t("facebook", "Facebook e Messenger", "Salvos pelo Facebook ou Messenger.")
            p.contains("tiktok") || p.contains("musically") -> t("tiktok", "TikTok", "Vídeos salvos do TikTok.")
            p.contains("kwai") -> t("kwai", "Kwai", "Vídeos salvos do Kwai.")
            p.contains("bluetooth") -> t("bluetooth", "Recebidos por Bluetooth", "Arquivos que chegaram por Bluetooth.")
            p.contains("call") && (p.contains("record") || p.contains("gravac")) ->
                t("calls", "Chamadas gravadas", "Gravações de ligações telefônicas.")
            p.contains("screenrecord") || p.contains("screen record") || p.contains("gravação de tela") ->
                t("screenrec", "Gravações de tela", "Vídeos feitos gravando a tela do celular.")
            p.contains("screenshot") || p.contains("captura") -> t("screenshots", "Capturas de tela", "Prints da tela.")
            type == FileType.AUDIO && (p.contains("record") || p.contains("gravador") || p.contains("voice") || p.contains("sounds/")) ->
                t("recorder", "Gravador de voz", "Áudios que você mesmo gravou com o gravador do celular.")
            p.startsWith("/dcim/camera") || p.startsWith("/dcim/100") || p.startsWith("/dcim/opencamera") ->
                t("camera", "Câmera", "Fotos e vídeos tirados com a câmera do celular.")
            p.startsWith("/download") -> t("download", "Downloads", "Arquivos baixados da internet ou salvos de apps.")
            p.startsWith("/documents") -> t("documents", "Pasta Documentos", "Arquivos salvos na pasta Documentos.")
            p.startsWith("/music") || p.startsWith("/ringtones") || p.startsWith("/notifications") || p.startsWith("/alarms") ->
                t("music", "Músicas e toques", "Músicas e sons de toque salvos no celular.")
            p.startsWith("/dcim") || p.startsWith("/pictures") || p.startsWith("/movies") -> {
                val folder = f.parentFile?.name ?: "Outras"
                t("app_" + folder.lowercase().replace(Regex("[^a-z0-9]"), ""), "Pasta \"$folder\"", "Imagens e vídeos salvos por um app ou editor nesta pasta.")
            }
            else -> t("other", "Outras pastas", "Arquivos em outras pastas do celular.")
        }
    }

    fun scan(): List<FolderCategory> {
        if (!folders.hasAllFilesAccess()) return emptyList()
        // tipo -> origem -> arquivos
        val buckets = HashMap<String, HashMap<String, Pair<Triple<String, String, String>, MutableList<LocalFile>>>>()
        try {
            root.walkTopDown()
                .maxDepth(10)
                .onEnter { !skipDir(it) }
                .filter { it.isFile }
                .forEach { f ->
                    val type = typeOf(f) ?: return@forEach
                    val o = originOf(f, type)
                    val byOrigin = buckets.getOrPut(type) { HashMap() }
                    byOrigin.getOrPut(o.first) { o to ArrayList() }.second +=
                        LocalFile(f.absolutePath, f.name, f.length(), f.lastModified())
                }
        } catch (_: Exception) {
        }
        val out = ArrayList<FolderCategory>()
        for (type in FileType.all) {
            val byOrigin = buckets[type] ?: continue
            byOrigin.values
                .map { (o, files) ->
                    FolderCategory(
                        id = "origin:$type:${o.first}",
                        emoji = iconFor(type),
                        title = o.second,
                        description = o.third,
                        safe = false,
                        group = type,
                        files = files.sortedByDescending { it.sizeBytes }
                    )
                }
                .sortedByDescending { it.bytes }
                .forEach { out += it }
        }
        return out
    }

    private fun iconFor(type: String) = when (type) {
        FileType.PHOTOS -> "🖼️"
        FileType.VIDEOS -> "🎬"
        FileType.AUDIO -> "🎙️"
        else -> "📄"
    }
}
