package br.com.celularsaudavel.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.MediaPlayer
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Size
import android.webkit.WebView
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import br.com.celularsaudavel.model.formatBytes
import br.com.celularsaudavel.model.formatDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** O que abrir na pré-visualização: um arquivo de pasta ou um item da galeria. */
data class PreviewTarget(
    val name: String,
    val sizeBytes: Long,
    val dateMs: Long,
    val path: String? = null,
    val uri: Uri? = null,
    val mime: String? = null,
    val location: String? = null,
)

enum class Kind { IMAGE, VIDEO, AUDIO, PDF, HTML, TEXT, OTHER }

private val imageExt = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
private val videoExt = setOf("mp4", "3gp", "mkv", "webm", "mov", "m4v")
private val audioExt = setOf("mp3", "m4a", "aac", "ogg", "opus", "wav", "amr", "flac", "3ga")
private val textExt = setOf("txt", "csv", "json", "xml", "log", "md", "vcf", "ini", "srt")

fun kindOf(name: String, mime: String?): Kind {
    val ext = name.substringAfterLast('.', "").lowercase()
    val m = mime ?: ""
    return when {
        m.startsWith("image/") || ext in imageExt -> Kind.IMAGE
        m.startsWith("video/") || ext in videoExt -> Kind.VIDEO
        m.startsWith("audio/") || ext in audioExt -> Kind.AUDIO
        m == "application/pdf" || ext == "pdf" -> Kind.PDF
        ext == "html" || ext == "htm" -> Kind.HTML
        m.startsWith("text/") || ext in textExt -> Kind.TEXT
        else -> Kind.OTHER
    }
}

fun kindEmoji(k: Kind) = when (k) {
    Kind.IMAGE -> "🖼️"; Kind.VIDEO -> "🎬"; Kind.AUDIO -> "🎵"; Kind.PDF -> "📕"
    Kind.HTML -> "🌐"; Kind.TEXT -> "📄"; Kind.OTHER -> "📦"
}

private fun PreviewTarget.contentUri(context: Context): Uri? =
    uri ?: path?.let {
        try {
            FileProvider.getUriForFile(context, context.packageName + ".arquivos", File(it))
        } catch (_: Exception) {
            null
        }
    }

private fun PreviewTarget.readUri(): Uri? = uri ?: path?.let { Uri.fromFile(File(it)) }

private fun openWithOtherApp(context: Context, t: PreviewTarget) {
    val u = t.contentUri(context) ?: return
    val mime = t.mime ?: br.com.celularsaudavel.data.RecycleBin.mimeOf(t.name)
    try {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_VIEW).setDataAndType(u, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                "Abrir com"
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
    }
}

/** Miniatura pequena de um arquivo de pasta: imagem, quadro do vídeo ou ícone do tipo. */
@Composable
fun FileThumb(path: String, name: String, modifier: Modifier = Modifier) {
    val kind = kindOf(name, null)
    val bmp by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            try {
                when (kind) {
                    Kind.IMAGE -> decodeSampled(File(path), 160)?.asImageBitmap()
                    Kind.VIDEO -> if (Build.VERSION.SDK_INT >= 29)
                        ThumbnailUtils.createVideoThumbnail(File(path), Size(160, 160), null).asImageBitmap()
                    else @Suppress("DEPRECATION") ThumbnailUtils.createVideoThumbnail(path, android.provider.MediaStore.Video.Thumbnails.MICRO_KIND)?.asImageBitmap()
                    else -> null
                }
            } catch (_: Throwable) {
                null
            }
        }
    }
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CS.Surface2),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) Image(b, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else BareIcon(kindEmoji(kind), CS.Green)
    }
}

private fun decodeSampled(file: File, target: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) sample *= 2
    return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
}

private fun decodeSampled(context: Context, uri: Uri, target: Int): Bitmap? {
    val r = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    r.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= target || bounds.outHeight / (sample * 2) >= target) sample *= 2
    return r.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
}

/** Tela cheia para ver o arquivo antes de decidir apagar. */
@Composable
fun PreviewDialog(t: PreviewTarget, onClose: () -> Unit) {
    val context = LocalContext.current
    val kind = remember(t) { kindOf(t.name, t.mime) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0E1111))
                .systemBarsPadding()
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(formatBytes(t.sizeBytes), if (t.dateMs > 0) formatDate(t.dateMs) else null, t.location).joinToString(" · "),
                        color = Color(0xFFB9C0BC), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = onClose) { Text("Fechar", color = Color.White) }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (kind) {
                    Kind.IMAGE -> ImagePreview(t)
                    Kind.VIDEO -> VideoPreview(t)
                    Kind.AUDIO -> AudioPreview(t)
                    Kind.PDF -> PdfPreview(t)
                    Kind.HTML -> HtmlPreview(t)
                    Kind.TEXT -> TextPreview(t)
                    Kind.OTHER -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        BareIcon("📦", Color.White, 56.dp)
                        Spacer(Modifier.height(8.dp))
                        Text("Este tipo de arquivo não abre aqui dentro.", color = Color.White)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { openWithOtherApp(context, t) }, modifier = Modifier.weight(1f)) {
                    Text("Abrir com outro app", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ImagePreview(t: PreviewTarget) {
    val context = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, t) {
        value = withContext(Dispatchers.IO) {
            try {
                t.readUri()?.let { decodeSampled(context, it, 1600) }?.asImageBitmap()
            } catch (_: Throwable) {
                null
            }
        }
    }
    val b = bmp
    if (b != null) Image(b, t.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
    else CircularProgressIndicator(color = Color.White)
}

@Composable
private fun VideoPreview(t: PreviewTarget) {
    val u = t.readUri() ?: return
    AndroidView(
        factory = { ctx ->
            VideoView(ctx).apply {
                val mc = MediaController(ctx)
                mc.setAnchorView(this)
                setMediaController(mc)
                setVideoURI(u)
                setOnPreparedListener { start() }
            }
        },
        onRelease = { it.stopPlayback() },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun AudioPreview(t: PreviewTarget) {
    val context = LocalContext.current
    // Abre o arquivo pelo próprio app (FileDescriptor): o tocador do sistema não tem acesso às pastas
    // do WhatsApp em Android/media, então passar só o caminho fazia o áudio não tocar.
    var failed by remember(t) { mutableStateOf(false) }
    val player by produceState<MediaPlayer?>(null, t) {
        value = withContext(Dispatchers.IO) {
            try {
                val mp = MediaPlayer()
                val path = t.path
                if (path != null) {
                    java.io.FileInputStream(File(path)).use { mp.setDataSource(it.fd) }
                } else {
                    val u = t.uri ?: return@withContext null
                    context.contentResolver.openFileDescriptor(u, "r")?.use { mp.setDataSource(it.fileDescriptor) }
                        ?: return@withContext null
                }
                mp.prepare()
                mp
            } catch (_: Exception) {
                null
            }
        }
        if (value == null) failed = true
    }
    val p0 = player
    DisposableEffect(p0) { onDispose { p0?.release() } }
    if (p0 == null) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            if (failed) {
                Text("Este áudio não toca aqui. Toque em \"Abrir com outro app\" para ouvir.", color = Color.White)
            } else {
                CircularProgressIndicator(color = Color.White)
                Spacer(Modifier.height(12.dp))
                Text("Preparando o áudio…", color = Color(0xFFB9C0BC), fontSize = 13.sp)
            }
        }
        return
    }
    val player = p0
    var playing by remember { mutableStateOf(false) }
    var pos by remember { mutableFloatStateOf(0f) }
    val dur = player.duration.coerceAtLeast(1)
    LaunchedEffect(playing) {
        while (playing) {
            pos = player.currentPosition.toFloat() / dur
            if (!player.isPlaying) playing = false
            delay(250)
        }
    }
    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BareIcon("🎵", Color.White, 64.dp)
        Spacer(Modifier.height(16.dp))
        Slider(
            value = pos,
            onValueChange = { pos = it; player.seekTo((it * dur).toInt()) },
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color(0xFF5CC394))
        )
        Text("${fmtTime((pos * dur).toInt())} / ${fmtTime(dur)}", color = Color(0xFFB9C0BC), fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (playing) player.pause() else player.start()
                playing = !playing
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
        ) { Text(if (playing) "Pausar" else "Tocar") }
    }
}

private fun fmtTime(ms: Int): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun PdfPreview(t: PreviewTarget) {
    val context = LocalContext.current
    val u = t.readUri()
    val holder = remember(t) {
        try {
            val pfd: ParcelFileDescriptor? = u?.let { context.contentResolver.openFileDescriptor(it, "r") }
            pfd?.let { it to PdfRenderer(it) }
        } catch (_: Exception) {
            null
        }
    }
    DisposableEffect(holder) {
        onDispose {
            try {
                holder?.second?.close(); holder?.first?.close()
            } catch (_: Exception) {
            }
        }
    }
    if (holder == null) {
        Text("Não foi possível abrir este PDF.", color = Color.White); return
    }
    val renderer = holder.second
    val lock = remember { Mutex() }
    val pages = renderer.pageCount.coerceAtMost(50)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(pages) { index ->
            val bmp by produceState<ImageBitmap?>(null, index) {
                value = withContext(Dispatchers.IO) {
                    lock.withLock {
                        try {
                            renderer.openPage(index).use { page ->
                                val w = 900
                                val h = (w.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                                val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                b.eraseColor(android.graphics.Color.WHITE)
                                page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                b.asImageBitmap()
                            }
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
            }
            val b = bmp
            if (b != null) Image(b, "Página ${index + 1}", modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
            else Box(Modifier.fillMaxWidth().height(300.dp).background(Color(0xFF222826)))
        }
        if (renderer.pageCount > pages) item {
            Text("Mostrando 50 de ${renderer.pageCount} páginas.", color = Color(0xFFB9C0BC), fontSize = 12.sp)
        }
    }
}

private fun readText(context: Context, t: PreviewTarget, max: Int = 200_000): String? = try {
    t.readUri()?.let { u ->
        context.contentResolver.openInputStream(u)?.use { input ->
            val buf = ByteArray(max)
            var n = 0
            while (n < max) {
                val r = input.read(buf, n, max - n)
                if (r < 0) break
                n += r
            }
            String(buf, 0, n, Charsets.UTF_8)
        }
    }
} catch (_: Exception) {
    null
}

@Composable
private fun TextPreview(t: PreviewTarget) {
    val context = LocalContext.current
    val text by produceState<String?>(null, t) { value = withContext(Dispatchers.IO) { readText(context, t) } }
    val s = text
    if (s == null) {
        CircularProgressIndicator(color = Color.White); return
    }
    SelectionContainer {
        Text(
            s,
            color = Color(0xFFE8ECE9), fontFamily = FontFamily.Monospace, fontSize = 13.sp,
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
        )
    }
}

@Composable
private fun HtmlPreview(t: PreviewTarget) {
    val context = LocalContext.current
    val html by produceState<String?>(null, t) { value = withContext(Dispatchers.IO) { readText(context, t) } }
    val h = html
    if (h == null) {
        CircularProgressIndicator(color = Color.White); return
    }
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                // Só mostra: sem JavaScript e sem acesso a arquivos, por segurança.
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.blockNetworkLoads = true
                loadDataWithBaseURL(null, h, "text/html", "utf-8", null)
            }
        },
        modifier = Modifier.fillMaxSize().background(Color.White)
    )
}
