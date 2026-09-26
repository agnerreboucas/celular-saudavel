package br.com.celularsaudavel.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import br.com.celularsaudavel.model.MediaFile

/**
 * Estado de cada arquivo no backup (máquina de estados do PRD):
 * QUEUED → UPLOADING → VERIFIED (= elegível) → DELETED_LOCAL, ou FAILED.
 */
object BackupState {
    const val QUEUED = "QUEUED"
    const val UPLOADING = "UPLOADING"
    const val VERIFIED = "VERIFIED"
    const val FAILED = "FAILED"
    const val DELETED_LOCAL = "DELETED_LOCAL"
}

data class BackupRow(
    val uri: String,
    val name: String,
    val mime: String,
    val size: Long,
    val category: String,
    val state: String,
    val driveId: String?,
    val md5: String?,
    val error: String?
)

data class BackupSummary(
    val selected: Int = 0,
    val selectedBytes: Long = 0,
    val verified: Int = 0,
    val verifiedBytes: Long = 0,
    val failed: Int = 0,
    val pending: Int = 0,
    val pendingBytes: Long = 0,
    val freed: Int = 0,
    val freedBytes: Long = 0,
    val verifiedPhotos: Int = 0,
    val verifiedVideos: Int = 0,
)

class BackupDb(context: Context) : SQLiteOpenHelper(context, "backup.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE items(
                uri TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                mime TEXT NOT NULL,
                size INTEGER NOT NULL,
                category TEXT NOT NULL,
                state TEXT NOT NULL,
                drive_id TEXT,
                md5 TEXT,
                error TEXT,
                updated INTEGER NOT NULL
            )"""
        )
        db.execSQL("CREATE INDEX idx_state ON items(state)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    /** Coloca na fila; o que já foi verificado ou liberado não volta para a fila. */
    fun enqueue(files: List<MediaFile>, categoryOf: (MediaFile) -> String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            for (f in files) {
                val cv = ContentValues().apply {
                    put("uri", f.uri.toString()); put("name", f.name); put("mime", f.mimeType.ifBlank { "application/octet-stream" })
                    put("size", f.sizeBytes); put("category", categoryOf(f)); put("state", BackupState.QUEUED); put("updated", now)
                }
                db.insertWithOnConflict("items", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
            }
            val uris = files.map { it.uri.toString() }
            uris.chunked(500).forEach { chunk ->
                val marks = chunk.joinToString(",") { "?" }
                db.execSQL(
                    "UPDATE items SET state='${BackupState.QUEUED}', error=NULL WHERE state='${BackupState.FAILED}' AND uri IN ($marks)",
                    chunk.toTypedArray()
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun retryFailed() {
        writableDatabase.execSQL("UPDATE items SET state='${BackupState.QUEUED}', error=NULL WHERE state='${BackupState.FAILED}'")
    }

    fun nextPending(limit: Int = 20): List<BackupRow> = query(
        "state IN ('${BackupState.QUEUED}','${BackupState.UPLOADING}')", limit
    )

    fun verified(limit: Int = 100000): List<BackupRow> = query("state='${BackupState.VERIFIED}'", limit)
    fun failed(limit: Int = 500): List<BackupRow> = query("state='${BackupState.FAILED}'", limit)

    fun stateOf(uris: Collection<String>): Map<String, String> {
        val out = HashMap<String, String>()
        uris.chunked(500).forEach { chunk ->
            val marks = chunk.joinToString(",") { "?" }
            readableDatabase.rawQuery("SELECT uri, state FROM items WHERE uri IN ($marks)", chunk.toTypedArray()).use { c ->
                while (c.moveToNext()) out[c.getString(0)] = c.getString(1)
            }
        }
        return out
    }

    private fun query(where: String, limit: Int): List<BackupRow> {
        val out = ArrayList<BackupRow>()
        readableDatabase.rawQuery(
            "SELECT uri,name,mime,size,category,state,drive_id,md5,error FROM items WHERE $where ORDER BY size ASC LIMIT $limit", null
        ).use { c ->
            while (c.moveToNext()) {
                out += BackupRow(
                    c.getString(0), c.getString(1), c.getString(2), c.getLong(3), c.getString(4), c.getString(5),
                    c.getString(6), c.getString(7), c.getString(8)
                )
            }
        }
        return out
    }

    fun mark(uri: String, state: String, driveId: String? = null, md5: String? = null, error: String? = null) {
        val cv = ContentValues().apply {
            put("state", state); put("updated", System.currentTimeMillis())
            if (driveId != null) put("drive_id", driveId)
            if (md5 != null) put("md5", md5)
            put("error", error)
        }
        writableDatabase.update("items", cv, "uri=?", arrayOf(uri))
    }

    fun markDeleted(uris: Collection<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            uris.forEach { mark(it, BackupState.DELETED_LOCAL) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun summary(): BackupSummary {
        var s = BackupSummary()
        readableDatabase.rawQuery(
            "SELECT state, category, COUNT(*), COALESCE(SUM(size),0) FROM items GROUP BY state, category", null
        ).use { c ->
            while (c.moveToNext()) {
                val state = c.getString(0)
                val cat = c.getString(1)
                val n = c.getInt(2)
                val b = c.getLong(3)
                s = s.copy(selected = s.selected + n, selectedBytes = s.selectedBytes + b)
                s = when (state) {
                    BackupState.VERIFIED -> s.copy(
                        verified = s.verified + n, verifiedBytes = s.verifiedBytes + b,
                        verifiedPhotos = s.verifiedPhotos + if (cat == "video") 0 else n,
                        verifiedVideos = s.verifiedVideos + if (cat == "video") n else 0
                    )
                    BackupState.FAILED -> s.copy(failed = s.failed + n, pending = s.pending + n, pendingBytes = s.pendingBytes + b)
                    BackupState.DELETED_LOCAL -> s.copy(freed = s.freed + n, freedBytes = s.freedBytes + b)
                    else -> s.copy(pending = s.pending + n, pendingBytes = s.pendingBytes + b)
                }
            }
        }
        return s
    }

    fun clearAll() {
        writableDatabase.delete("items", null, null)
    }
}
