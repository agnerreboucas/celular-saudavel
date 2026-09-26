package br.com.celularsaudavel.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.celularsaudavel.model.StorageInfo
import br.com.celularsaudavel.model.formatBytes
import br.com.celularsaudavel.model.formatCount
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Cores de status: só para estado (bom → crítico), separadas da cor da marca. */
object Status {
    val Green = Color(0xFF1E7F55)
    val Yellow = Color(0xFFD9A21B)
    val Orange = Color(0xFFE0782F)
    val Red = Color(0xFFCC3D3D)

    fun forScore(v: Int): Color = when {
        v >= 80 -> Green
        v >= 60 -> Yellow
        v >= 40 -> Orange
        else -> Red
    }
}

/** Nível de folga do armazenamento, com efeitos reais e verificáveis. */
data class Folga(val label: String, val color: Color, val text: String)

fun folgaOf(st: StorageInfo): Folga {
    val free = 1f - st.usedFraction
    return when {
        free < 0.05f -> Folga(
            "Crítica", Status.Red,
            "Com menos de 5% livre, o Android pode deixar de salvar fotos, gravar vídeos e instalar atualizações, inclusive as de segurança. Libere espaço hoje."
        )
        free < 0.10f -> Folga(
            "Apertada", Status.Orange,
            "Com menos de 10% livre, atualizações de apps e do sistema começam a falhar e o celular fica sem espaço para arquivos temporários. Vale limpar logo."
        )
        free < 0.20f -> Folga(
            "Atenção", Status.Yellow,
            "Ainda funciona bem, mas o espaço acaba rápido com vídeos e fotos novas. Uma revisão agora evita aperto depois."
        )
        else -> Folga(
            "Tranquila", Status.Green,
            "Há folga para fotos, vídeos e atualizações. Pode seguir tranquilo."
        )
    }
}

/** Medidor em meia-lua, de vermelho a verde, com o ponteiro no índice. */
@Composable
fun HealthGauge(value: Int, label: String, modifier: Modifier = Modifier) {
    val anim by animateFloatAsState(value.coerceIn(0, 100) / 100f, tween(900), label = "gauge")
    val track = CS.Surface2
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f)
        ) {
            val stroke = size.width * 0.075f
            val d = size.width - stroke
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(d, d)
            // Faixas: 0–40 vermelho, 40–60 laranja, 60–80 amarelo, 80–100 verde (com pequenos vãos)
            val zones = listOf(0f to 0.4f to Status.Red, 0.4f to 0.6f to Status.Orange, 0.6f to 0.8f to Status.Yellow, 0.8f to 1f to Status.Green)
            drawArc(track, 180f, 180f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            zones.forEach { (range, color) ->
                val (a, b) = range
                drawArc(
                    color.copy(alpha = 0.9f), 180f + a * 180f + 1.5f, (b - a) * 180f - 3f, false,
                    topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt)
                )
            }
            // Ponteiro
            val angle = Math.toRadians((180.0 + anim * 180.0))
            val c = Offset(size.width / 2, size.width / 2)
            val r = d / 2
            val p = Offset(c.x + (r * cos(angle)).toFloat(), c.y + (r * sin(angle)).toFloat())
            drawCircle(Color.White, radius = stroke * 0.72f, center = p)
            drawCircle(Status.forScore((anim * 100).toInt()), radius = stroke * 0.48f, center = p)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 2.dp)) {
            Text("$value", fontSize = 54.sp, fontWeight = FontWeight.Bold, color = CS.Ink, lineHeight = 54.sp)
            Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Status.forScore(value))
        }
    }
}

/** Rosca do armazenamento por categoria, com o % ocupado no meio. */
@Composable
fun StorageDonut(parts: List<Pair<Float, Color>>, centerTop: String, centerBottom: String, modifier: Modifier = Modifier) {
    val track = CS.Surface2
    Box(modifier.size(150.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.14f
            val d = size.minDimension - stroke
            val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
            drawArc(track, 0f, 360f, false, topLeft, Size(d, d), style = Stroke(stroke))
            var start = -90f
            parts.filter { it.first > 0.003f }.forEach { (frac, color) ->
                val sweep = frac * 360f
                drawArc(color, start, max(sweep - 1.2f, 0.5f), false, topLeft, Size(d, d), style = Stroke(stroke))
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerTop, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = CS.Ink)
            Text(centerBottom, fontSize = 12.sp, color = CS.Muted)
        }
    }
}

/** Selo colorido de status. */
@Composable
fun StatusChip(text: String, color: Color) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Folga do sistema: nível colorido + o que cabe no espaço livre. */
@Composable
fun FolgaCard(st: StorageInfo, avgPhotoBytes: Long) {
    val f = folgaOf(st)
    CsCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Folga do sistema", fontWeight = FontWeight.Bold, color = CS.Ink, fontSize = 16.sp, modifier = Modifier.weight(1f))
                StatusChip(f.label, f.color)
            }
            Text(f.text, color = CS.Ink, fontSize = 14.sp, lineHeight = 20.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val photos = if (avgPhotoBytes > 0) st.freeBytes / avgPhotoBytes else st.freeBytes / 3_000_000L
                Capacity("📷", "~${formatCount(photos.toInt().coerceAtLeast(0))}", "fotos cabem", Modifier.weight(1f))
                // Vídeo Full HD 30 fps ≈ 130 MB por minuto (média de celulares)
                val minutes = st.freeBytes / 130_000_000L
                Capacity("🎥", "~${formatCount(minutes.toInt().coerceAtLeast(0))} min", "de vídeo Full HD", Modifier.weight(1f))
            }
            Text(
                if (avgPhotoBytes > 0) "Estimativa com o tamanho médio das suas fotos." else "Estimativa com fotos de 3 MB.",
                color = CS.Muted, fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun Capacity(emoji: String, value: String, label: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CS.Surface2)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BareIcon(emoji, CS.Green, 18.dp)
            Spacer(Modifier.width(6.dp))
            Text(value, fontWeight = FontWeight.Bold, color = CS.Ink, fontSize = 16.sp)
        }
        Text(label, color = CS.Muted, fontSize = 12.sp)
    }
}

/** Antes x depois da sessão de limpeza. */
@Composable
fun SessionCard(startUsed: Long, st: StorageInfo, sessionFreed: Long, avgPhotoBytes: Long) {
    val saved = max(startUsed - st.usedBytes, sessionFreed)
    if (saved < 50_000_000L) return
    val total = st.totalBytes.toFloat().coerceAtLeast(1f)
    val before by animateFloatAsState(startUsed / total, tween(600), label = "before")
    val after by animateFloatAsState(st.usedBytes / total, tween(1200), label = "after")
    CsCard(color = CS.GreenSoft) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Você economizou ${formatBytes(saved)} nesta sessão 🎉", fontWeight = FontWeight.Bold, color = CS.Green, fontSize = 17.sp)
            BarRow("Quando abriu", before, formatBytes(startUsed), folgaOf(StorageInfo(st.totalBytes, startUsed)).color)
            BarRow("Agora", after, formatBytes(st.usedBytes), CS.Green)
            val photos = if (avgPhotoBytes > 0) saved / avgPhotoBytes else saved / 3_000_000L
            Text(
                "Isso abre espaço para mais ~${formatCount(photos.toInt())} fotos e dá mais folga para o Android atualizar apps e gravar vídeos.",
                color = CS.Green, fontSize = 13.sp, lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun BarRow(label: String, frac: Float, value: String, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(label, color = CS.Ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("$value usados", color = CS.Muted, fontSize = 13.sp)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(CS.Surface)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(frac.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(color)
            )
        }
    }
}

/** Janela de progresso enquanto apaga muitos arquivos. */
@Composable
fun DeletingDialog(done: Int, total: Int) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Column(
            Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(CS.Surface)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Liberando espaço…", fontWeight = FontWeight.Bold, color = CS.Ink, fontSize = 18.sp)
            val frac = if (total > 0) done.toFloat() / total else 0f
            LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth().height(10.dp), color = CS.Green, trackColor = CS.Surface2)
            Text("${formatCount(done)} de ${formatCount(total)} arquivos · ${(frac * 100).toInt()}%", color = CS.Muted, fontSize = 14.sp)
        }
    }
}

/** Comemoração depois de cada limpeza. */
@Composable
fun WinDialog(
    bytes: Long,
    count: Int,
    what: String,
    toTrash: Boolean,
    sessionSaved: Long,
    avgPhotoBytes: Long,
    onOpenTrash: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { AppIcon(if (toTrash) "🗑️" else "✅", size = 56.dp, corner = 16.dp) },
        title = {
            Text(
                if (toTrash) "Foi para a lixeira" else "Parabéns! Você economizou ${formatBytes(bytes)}",
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (toTrash) {
                    Text("${formatCount(count)} $what (${formatBytes(bytes)}) estão na lixeira. O espaço só volta quando você esvaziar a lixeira.")
                } else {
                    Text("${formatCount(count)} $what removidos.")
                    val photos = if (avgPhotoBytes > 0) bytes / avgPhotoBytes else bytes / 3_000_000L
                    if (photos > 0) Text("Isso abre espaço para mais ~${formatCount(photos.toInt())} fotos.")
                    if (sessionSaved > bytes) Text("Total nesta sessão: ${formatBytes(sessionSaved)}.", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Com mais espaço livre, o Android tem folga para atualizar apps, salvar fotos e gravar vídeos sem erro de armazenamento.",
                        color = CS.Muted, fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            if (toTrash) TextButton(onClick = { onDismiss(); onOpenTrash() }) { Text("Esvaziar a lixeira") }
            else TextButton(onClick = onDismiss) { Text("Continuar") }
        },
        dismissButton = if (toTrash) ({ TextButton(onClick = onDismiss) { Text("Depois") } }) else null
    )
}

data class Vital(val emoji: String, val name: String, val value: String, val status: String, val color: Color)

/** Sinais vitais, como num check-up: cada área com a sua cor. */
@Composable
fun VitalsCard(vitals: List<Vital>, onClick: (() -> Unit)? = null) {
    CsCard(onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Sinais vitais", fontWeight = FontWeight.Bold, color = CS.Ink, fontSize = 16.sp)
            vitals.forEach { v ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(v.emoji, size = 36.dp, tint = v.color, background = v.color.copy(alpha = 0.12f), corner = 10.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(v.name, color = CS.Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(v.value, color = CS.Muted, fontSize = 13.sp)
                    }
                    StatusChip(v.status, v.color)
                }
            }
        }
    }
}

val StatusNeutral = Color(0xFF8A9096)
