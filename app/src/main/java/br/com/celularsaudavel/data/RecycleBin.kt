package br.com.celularsaudavel.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import br.com.celularsaudavel.model.DAY_MS
import br.com.celularsaudavel.model.LocalFile
import java.io.File

/** Onde o arquivo apagado ficou guardado. */
object BinPlace {
    const val PHONE = "phone"
    const val CLOUD = "cloud"
}

data class RecycleItem(
    val id: Long,
    val originalPath: String,
    val name: String,
    val size: Long,
    val deletedAt: Long,
    val place: String,
    val binPath: String?,
    val driveId: String?,
    /** Conta do Google onde ficou guardado (itens da nuvem). */
    val account: String? = null,
)

/**
 * Rede de segurança do Celular Saudável: o que o app apaga de pastas não some na hora.
 * - No celular: o arquivo é movido para uma pasta oculta e volta com um toque (ainda ocupa espaço).
 * - Na nuvem: o arquivo vai para "Celular Saudável/Recuperáveis" no Drive do usuário e sai do celular.
 * Depois do prazo escolhido (30, 90 ou 180 dias), é apagado de vez.
 */
class RecycleBin(private val context: Context) : SQLiteOpenHelper(context, "recycle.db", null, 2) {

    private val prefs = context.getSharedPreferences("recycle", Context.MODE_PRIVATE)

    var retentionDays: Int
        get() = prefs.getInt("days", 90)
        set(v) = prefs.edit().putInt("days", v).apply()

    private val binDir: File
        get() = File(Environment.getExternalStorageDirectory(), ".CelularSaudavel/Lixeira").also {
            if (!it.exists()) {
                it.mkdirs()
                try {
                    File(it.parentFile, ".nomedia").createNewFile()
                    File(it, ".nomedia").createNewFile()
                } catch (_: Exception) {
                }
            }
        }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE items(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                original TEXT NOT NULL,
                name TEXT NOT NULL,
                size INTEGER NOT NULL,
                deleted_at INTEGER NOT NULL,
                place TEXT NOT NULL,
                bin_path TEXT,
                drive_id TEXT,
                account TEXT
            )"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE items ADD COLUMN account TEXT")
    }

    private fun insert(original: String, name: String, size: Long, place: String, binPath: String?, driveId: String?, account: String? = null) {
        writableDatabase.insert("items", null, ContentValues().apply {
            put("original", original); put("name", name); put("size", size)
            put("deleted_at", System.currentTimeMillis()); put("place", place)
            put("bin_path", binPath); put("drive_id", driveId); put("account", account)
        })
    }

    /** Move para a pasta de segurança no próprio celular. */
    fun moveToPhoneBin(files: List<LocalFile>, onProgress: (Int) -> Unit): List<LocalFile> {
        val dir = binDir
        val moved = ArrayList<LocalFile>()
        files.forEachIndexed { i, lf ->
            try {
                val src = File(lf.path)
                val dest = File(dir, "${System.currentTimeMillis()}_${i}_${lf.name}")
                val ok = src.renameTo(dest) || (runCatching { src.copyTo(dest, overwrite = true); src.delete() }.getOrDefault(false))
                if (ok) {
                    insert(lf.path, lf.name, lf.sizeBytes, BinPlace.PHONE, dest.absolutePath, null)
                    moved += lf
                }
            } catch (_: Exception) {
            }
            onProgress(i + 1)
        }
        scan(moved.map { it.path })
        return moved
    }

    /** Envia para o Drive (com verificação) e só então tira do celular. */
    fun moveToCloud(files: List<LocalFile>, drive: DriveRepository, onProgress: (Int) -> Unit): List<LocalFile> {
        drive.refreshToken()
        val account = runCatching { drive.account().email }.getOrNull()?.ifBlank { null } ?: drive.email
        val root = drive.folderId(DriveRepository.ROOT_FOLDER, null)
        val folder = drive.folderId("Recuperáveis", root)
        val moved = ArrayList<LocalFile>()
        files.forEachIndexed { i, lf ->
            try {
                val src = File(lf.path)
                val r = drive.upload(Uri.fromFile(src), lf.name, mimeOf(lf.name), folder)
                if (r.verified && src.delete()) {
                    insert(lf.path, lf.name, lf.sizeBytes, BinPlace.CLOUD, null, r.driveId, account)
                    moved += lf
                } else if (!r.verified) {
                    runCatching { drive.delete(r.driveId) }
                }
            } catch (e: NeedsConsentException) {
                throw e
            } catch (_: Exception) {
            }
            onProgress(i + 1)
        }
        scan(moved.map { it.path })
        return moved
    }

    fun list(sinceMs: Long = 0): List<RecycleItem> {
        val out = ArrayList<RecycleItem>()
        readableDatabase.rawQuery(
            "SELECT id, original, name, size, deleted_at, place, bin_path, drive_id, account FROM items WHERE deleted_at >= ? ORDER BY deleted_at DESC",
            arrayOf(sinceMs.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out += RecycleItem(
                    c.getLong(0), c.getString(1), c.getString(2), c.getLong(3), c.getLong(4),
                    c.getString(5), c.getString(6), c.getString(7), c.getString(8)
                )
            }
        }
        return out
    }

    /** Devolve os arquivos ao lugar original (se já existir um com o mesmo nome, cria uma cópia numerada). */
    fun restore(items: List<RecycleItem>, drive: DriveRepository?, onProgress: (Int) -> Unit): List<RecycleItem> {
        val done = ArrayList<RecycleItem>()
        items.forEachIndexed { i, item ->
            try {
                val dest = freeName(File(item.originalPath))
                dest.parentFile?.mkdirs()
                val ok = when (item.place) {
                    BinPlace.PHONE -> {
                        val src = File(item.binPath ?: "")
                        src.exists() && (src.renameTo(dest) || runCatching { src.copyTo(dest); src.delete() }.getOrDefault(false))
                    }
                    else -> {
                        val d = drive?.forAccount(item.account) ?: return@forEachIndexed
                        val id = item.driveId ?: return@forEachIndexed
                        d.download(id, dest)
                        runCatching { d.delete(id) }
                        true
                    }
                }
                if (ok) {
                    remove(item.id)
                    done += item
                    scan(listOf(dest.absolutePath))
                }
            } catch (e: NeedsConsentException) {
                throw e
            } catch (_: Exception) {
            }
            onProgress(i + 1)
        }
        return done
    }

    fun deleteForever(items: List<RecycleItem>, drive: DriveRepository?): List<RecycleItem> {
        val done = ArrayList<RecycleItem>()
        for (it in items) {
            try {
                when (it.place) {
                    BinPlace.PHONE -> it.binPath?.let { p -> File(p).delete() }
                    else -> {
                        val id = it.driveId
                        if (id != null && drive != null) drive.forAccount(it.account).delete(id)
                    }
                }
                remove(it.id)
                done += it
            } catch (_: Exception) {
            }
        }
        return done
    }

    /** Apaga de vez o que passou do prazo. Itens da nuvem só saem se o Drive estiver acessível. */
    /** Até quando cada item ainda pode voltar. */
    fun expiresAt(item: RecycleItem): Long = item.deletedAt + retentionDays * DAY_MS

    fun purgeExpired(drive: DriveRepository?) {
        val limit = System.currentTimeMillis() - retentionDays * DAY_MS
        val expired = list().filter { it.deletedAt < limit }
        if (expired.isEmpty()) return
        val cloudOk = drive != null && runCatching { drive.refreshToken() }.isSuccess
        deleteForever(expired.filter { it.place == BinPlace.PHONE || cloudOk }, if (cloudOk) drive else null)
    }

    private fun remove(id: Long) {
        writableDatabase.delete("items", "id=?", arrayOf(id.toString()))
    }

    private fun freeName(f: File): File {
        if (!f.exists()) return f
        val base = f.nameWithoutExtension
        val ext = f.extension.let { if (it.isEmpty()) "" else ".$it" }
        var n = 1
        while (true) {
            val c = File(f.parentFile, "$base ($n)$ext")
            if (!c.exists()) return c
            n++
        }
    }

    private fun scan(paths: List<String>) {
        if (paths.isEmpty()) return
        try {
            MediaScannerConnection.scanFile(context, paths.toTypedArray(), null, null)
        } catch (_: Exception) {
        }
    }

    companion object {
        fun mimeOf(name: String): String =
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
                ?: "application/octet-stream"
    }
}
