package br.com.celularsaudavel.data

import android.Manifest
import android.app.usage.StorageStatsManager
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.MediaStore
import br.com.celularsaudavel.model.CategoryStat
import br.com.celularsaudavel.model.DuplicateGroup
import br.com.celularsaudavel.model.MediaAccess
import br.com.celularsaudavel.model.MediaFile
import br.com.celularsaudavel.model.MediaSummary
import br.com.celularsaudavel.model.StorageInfo
import java.io.InputStream
import java.security.MessageDigest

class MediaRepository(private val context: Context) {

    private val resolver = context.contentResolver

    // ---------- Armazenamento ----------

    fun storageInfo(): StorageInfo {
        return try {
            val ssm = context.getSystemService(StorageStatsManager::class.java)
            val total = ssm.getTotalBytes(StorageManager.UUID_DEFAULT)
            val free = ssm.getFreeBytes(StorageManager.UUID_DEFAULT)
            StorageInfo(total, total - free)
        } catch (_: Exception) {
            val stat = StatFs(Environment.getDataDirectory().path)
            StorageInfo(stat.totalBytes, stat.totalBytes - stat.availableBytes)
        }
    }

    // ---------- Permissões ----------

    fun mediaAccess(): MediaAccess {
        fun granted(p: String) =
            context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
        return when {
            Build.VERSION.SDK_INT >= 33 && granted(Manifest.permission.READ_MEDIA_IMAGES) -> MediaAccess.FULL
            Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> MediaAccess.PARTIAL
            Build.VERSION.SDK_INT < 33 && granted(Manifest.permission.READ_EXTERNAL_STORAGE) -> MediaAccess.FULL
            else -> MediaAccess.NONE
        }
    }

    // ---------- Consulta de mídia ----------

    private fun query(collection: Uri, isVideo: Boolean): List<MediaFile> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_MODIFIED
        )
        val out = ArrayList<MediaFile>()
        try {
            resolver.query(collection, projection, null, null, null)?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val mimeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    out += MediaFile(
                        uri = ContentUris.withAppendedId(collection, id),
                        name = c.getString(nameCol) ?: "Sem nome",
                        sizeBytes = c.getLong(sizeCol),
                        mimeType = c.getString(mimeCol) ?: "",
                        dateModifiedSec = c.getLong(dateCol),
                        isVideo = isVideo
                    )
                }
            }
        } catch (_: Exception) {
            // Sem permissão ou coleção indisponível: retorna o que conseguiu.
        }
        return out
    }

    fun images(): List<MediaFile> = query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false)
    fun videos(): List<MediaFile> = query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
    private fun audio(): List<MediaFile> = query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, false)

    fun summary(images: List<MediaFile>, videos: List<MediaFile>): MediaSummary {
        val a = audio()
        return MediaSummary(
            images = CategoryStat(images.size, images.sumOf { it.sizeBytes }),
            videos = CategoryStat(videos.size, videos.sumOf { it.sizeBytes }),
            audio = CategoryStat(a.size, a.sumOf { it.sizeBytes })
        )
    }

    fun largeVideos(videos: List<MediaFile>, minBytes: Long = 100_000_000L): List<MediaFile> =
        videos.filter { it.sizeBytes >= minBytes }.sortedByDescending { it.sizeBytes }

    // ---------- Duplicadas ----------

    /**
     * 1) agrupa por tamanho + tipo; 2) confirma por impressão digital MD5 do conteúdo.
     * Arquivos até 32 MB: conteúdo inteiro. Maiores: primeiro e último 1 MB + tamanho.
     */
    fun findDuplicates(
        files: List<MediaFile>,
        onProgress: (done: Int, total: Int) -> Unit
    ): List<DuplicateGroup> {
        val buckets = files
            .filter { it.sizeBytes > 0 }
            .groupBy { it.sizeBytes to it.mimeType }
            .values
            .filter { it.size > 1 }
        val total = buckets.sumOf { it.size }
        var done = 0
        val groups = ArrayList<DuplicateGroup>()
        for (bucket in buckets) {
            val byHash = HashMap<String, MutableList<MediaFile>>()
            for (f in bucket) {
                val h = fingerprint(f)
                done++
                if (done % 10 == 0 || done == total) onProgress(done, total)
                if (h != null) byHash.getOrPut(h) { ArrayList() }.add(f)
            }
            byHash.values.filter { it.size > 1 }.forEach { list ->
                groups += DuplicateGroup(list.sortedBy { it.dateModifiedSec })
            }
        }
        return groups.sortedByDescending { it.wastedBytes }
    }

    private fun fingerprint(f: MediaFile): String? {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val input = resolver.openInputStream(f.uri) ?: return null
            input.use {
                val chunk = 1_048_576L
                if (f.sizeBytes <= 32L * chunk) {
                    digestAll(it, md)
                } else {
                    digestN(it, md, chunk)
                    skipFully(it, f.sizeBytes - 2 * chunk)
                    digestN(it, md, chunk)
                    md.update(f.sizeBytes.toString().toByteArray())
                }
            }
            md.digest().joinToString("") { b -> "%02x".format(b) }
        } catch (_: Exception) {
            null
        }
    }

    private fun digestAll(input: InputStream, md: MessageDigest) {
        val buf = ByteArray(64 * 1024)
        while (true) {
            val r = input.read(buf)
            if (r < 0) break
            md.update(buf, 0, r)
        }
    }

    private fun digestN(input: InputStream, md: MessageDigest, n: Long) {
        val buf = ByteArray(64 * 1024)
        var left = n
        while (left > 0) {
            val r = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (r < 0) break
            md.update(buf, 0, r)
            left -= r
        }
    }

    private fun skipFully(input: InputStream, n: Long) {
        var left = n
        while (left > 0) {
            val s = input.skip(left)
            if (s <= 0) {
                if (input.read() < 0) return
                left -= 1
            } else {
                left -= s
            }
        }
    }

    // ---------- Remoção (sempre com confirmação do sistema) ----------

    /** Exclusão definitiva. Usada só para cópias duplicadas (o original permanece). */
    fun deleteRequest(uris: List<Uri>): IntentSender? =
        if (Build.VERSION.SDK_INT >= 30) MediaStore.createDeleteRequest(resolver, uris).intentSender else null

    /** Lixeira do sistema: recuperável por cerca de 30 dias. */
    fun trashRequest(uris: List<Uri>): IntentSender? =
        if (Build.VERSION.SDK_INT >= 30) MediaStore.createTrashRequest(resolver, uris, true).intentSender else null
}
