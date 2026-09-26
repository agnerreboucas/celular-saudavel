package br.com.celularsaudavel.data

import android.content.Context
import android.net.Uri
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.DigestInputStream
import java.security.MessageDigest

class NeedsConsentException : Exception("Reconecte o Google Drive")
class DriveFullException : Exception("O Google Drive está cheio")

data class DriveAccount(val email: String, val name: String, val limitBytes: Long?, val usageBytes: Long) {
    val freeBytes: Long? get() = limitBytes?.let { (it - usageBytes).coerceAtLeast(0) }
}

data class UploadResult(val driveId: String, val remoteMd5: String?, val remoteSize: Long, val localMd5: String, val localSize: Long) {
    val verified: Boolean get() = remoteMd5 != null && remoteMd5.equals(localMd5, ignoreCase = true) && remoteSize == localSize
}

/**
 * Google Drive via REST, escopo mínimo drive.file (o app só enxerga o que ele mesmo criou).
 * O app é reconhecido pelo Google pelo nome do pacote + SHA-1 cadastrados no Google Cloud.
 */
class DriveRepository(private val context: Context) {

    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/drive.file"
        private const val API = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        private const val FOLDER_MIME = "application/vnd.google-apps.folder"
        const val ROOT_FOLDER = "Celular Saudável"

        fun authRequest(): AuthorizationRequest =
            AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()
    }

    private val prefs = context.getSharedPreferences("drive", Context.MODE_PRIVATE)

    var connected: Boolean
        get() = prefs.getBoolean("connected", false)
        set(v) {
            prefs.edit().putBoolean("connected", v).apply()
        }

    private var token: String? = null

    fun setToken(t: String?) {
        token = t
        if (t != null) connected = true
    }

    /** Pega um token sem mostrar tela (funciona depois da primeira autorização). Rodar fora da thread principal. */
    fun refreshToken(): String {
        val result = Tasks.await(Identity.getAuthorizationClient(context).authorize(authRequest()))
        if (result.hasResolution()) throw NeedsConsentException()
        val t = result.accessToken ?: throw NeedsConsentException()
        token = t
        return t
    }

    private fun currentToken(): String = token ?: refreshToken()

    private fun open(url: String, method: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 30_000
        c.readTimeout = 120_000
        c.setRequestProperty("Authorization", "Bearer ${currentToken()}")
        return c
    }

    private fun body(c: HttpURLConnection): String {
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code == 401) {
            token = null
            throw IOException("401")
        }
        if (code == 403 && text.contains("storageQuotaExceeded")) throw DriveFullException()
        if (code !in 200..299) throw IOException("Drive respondeu $code: ${text.take(200)}")
        return text
    }

    /** Repete uma vez com token novo se o token venceu. */
    private fun <T> withRetry(block: () -> T): T = try {
        block()
    } catch (e: IOException) {
        if (e.message == "401") {
            refreshToken(); block()
        } else throw e
    }

    fun account(): DriveAccount = withRetry {
        val c = open("$API/about?fields=user(emailAddress,displayName),storageQuota(limit,usage)", "GET")
        val o = JSONObject(body(c))
        val user = o.optJSONObject("user")
        val q = o.optJSONObject("storageQuota")
        DriveAccount(
            email = user?.optString("emailAddress") ?: "",
            name = user?.optString("displayName") ?: "",
            limitBytes = q?.optString("limit")?.toLongOrNull(),
            usageBytes = q?.optString("usage")?.toLongOrNull() ?: 0L
        )
    }

    fun folderId(name: String, parentId: String?): String = withRetry {
        val key = "folder:${parentId ?: "root"}:$name"
        prefs.getString(key, null)?.let { return@withRetry it }
        val safe = name.replace("'", "\\'")
        var q = "name='$safe' and mimeType='$FOLDER_MIME' and trashed=false"
        if (parentId != null) q += " and '$parentId' in parents"
        val c = open("$API/files?q=${URLEncoder.encode(q, "UTF-8")}&fields=files(id)&spaces=drive", "GET")
        val files = JSONObject(body(c)).optJSONArray("files") ?: JSONArray()
        val id = if (files.length() > 0) files.getJSONObject(0).getString("id") else {
            val cc = open("$API/files?fields=id", "POST")
            cc.doOutput = true
            cc.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            val meta = JSONObject().put("name", name).put("mimeType", FOLDER_MIME)
            if (parentId != null) meta.put("parents", JSONArray().put(parentId))
            cc.outputStream.use { it.write(meta.toString().toByteArray()) }
            JSONObject(body(cc)).getString("id")
        }
        prefs.edit().putString(key, id).apply()
        id
    }

    /**
     * Envio retomável (sessão resumable do Drive) com MD5 calculado durante a leitura.
     * A verificação compara MD5 e tamanho devolvidos pelo Drive com os do arquivo local.
     */
    fun upload(uri: Uri, name: String, mime: String, parentId: String): UploadResult = withRetry {
        val resolver = context.contentResolver
        val size = resolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: throw IOException("Arquivo não encontrado")

        val start = open("$UPLOAD/files?uploadType=resumable&fields=id,md5Checksum,size", "POST")
        start.doOutput = true
        start.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        start.setRequestProperty("X-Upload-Content-Type", mime)
        start.setRequestProperty("X-Upload-Content-Length", size.toString())
        val meta = JSONObject().put("name", name).put("parents", JSONArray().put(parentId))
        start.outputStream.use { it.write(meta.toString().toByteArray()) }
        body(start)
        val session = start.getHeaderField("Location") ?: throw IOException("Sessão de envio não criada")

        val put = URL(session).openConnection() as HttpURLConnection
        put.requestMethod = "PUT"
        put.doOutput = true
        put.connectTimeout = 30_000
        put.readTimeout = 300_000
        put.setRequestProperty("Content-Type", mime)
        put.setFixedLengthStreamingMode(size)
        val md = MessageDigest.getInstance("MD5")
        var sent = 0L
        resolver.openInputStream(uri)?.use { raw ->
            DigestInputStream(raw, md).use { input ->
                put.outputStream.use { out ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        val r = input.read(buf)
                        if (r < 0) break
                        out.write(buf, 0, r)
                        sent += r
                    }
                }
            }
        } ?: throw IOException("Não foi possível ler o arquivo")
        val o = JSONObject(body(put))
        UploadResult(
            driveId = o.getString("id"),
            remoteMd5 = o.optString("md5Checksum").ifBlank { null },
            remoteSize = o.optString("size").toLongOrNull() ?: -1,
            localMd5 = md.digest().joinToString("") { "%02x".format(it) },
            localSize = sent
        )
    }

    fun disconnect() {
        token = null
        prefs.edit().clear().apply()
    }
}
