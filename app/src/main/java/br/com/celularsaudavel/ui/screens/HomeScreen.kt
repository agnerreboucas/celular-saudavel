package br.com.celularsaudavel.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.celularsaudavel.model.MediaAccess
import br.com.celularsaudavel.model.formatBytes
import br.com.celularsaudavel.model.formatCount
import br.com.celularsaudavel.ui.*

val ColorPhotos = Color(0xFF1E7F55)
val ColorVideos = Color(0xFF3B6FB6)
val ColorApps = Color(0xFFD08A2E)
val ColorAudio = Color(0xFF9B6BC4)
val ColorOther = Color(0xFFB8BCB5)

@Composable
fun HomeScreen(
    state: UiState,
    onScan: () -> Unit,
    onOpenClean: () -> Unit,
    onOpenDuplicates: () -> Unit,
    onOpenVideos: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenPermissions: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = ListSpacing
    ) {
        item {
            ScreenHeader("Olá 👋", state.health?.headline ?: "Vamos cuidar do seu celular.")
        }

        // ---------- Índice ----------
        item {
            CsCard {
                Column {
                    Text("SAÚDE DO CELULAR", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CS.Muted, letterSpacing = 1.sp)
                    Spacer(Modifier.height(10.dp))
                    val health = state.health
                    when {
                        state.scanning -> {
                            Text("Analisando…", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = CS.Ink)
                            Spacer(Modifier.height(6.dp))
                            Text(state.scanStep, color = CS.Muted, fontSize = 14.sp)
                            Spacer(Modifier.height(14.dp))
                            val p = state.scanProgress
                            if (p != null) {
                                LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth(), color = CS.Green, trackColor = CS.Surface2)
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = CS.Green, trackColor = CS.Surface2)
                            }
                        }
                        health != null -> {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                                Text("${health.value}", fontSize = 60.sp, fontWeight = FontWeight.Bold, color = CS.Ink, lineHeight = 60.sp)
                                Text(" /100", fontSize = 18.sp, color = CS.Muted, modifier = Modifier.padding(bottom = 10.dp))
                            }
                            Text(health.label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = CS.Green)
                            Spacer(Modifier.height(14.dp))
                            LinearProgressIndicator(
                                progress = { health.value / 100f },
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                color = CS.Green,
                                trackColor = CS.Surface2
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Índice de organização: espaço ocupado, arquivos repetidos e apps sem uso. Não mede desempenho.",
                                color = CS.Muted, fontSize = 12.sp, lineHeight = 16.sp
                            )
                            Spacer(Modifier.height(18.dp))
                            if (state.reviewableBytes > 0) {
                                Text(
                                    "${formatBytes(state.reviewableBytes)} podem ser revisados",
                                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = CS.Ink
                                )
                                Spacer(Modifier.height(12.dp))
                                PrimaryButton("Ver recomendações", onOpenClean)
                            } else {
                                Text("Nada para revisar agora. 👏", fontSize = 16.sp, color = CS.Ink)
                            }
                            Spacer(Modifier.height(4.dp))
                            TextButton(onClick = onScan) { Text("Analisar de novo", color = CS.Muted) }
                        }
                        else -> {
                            Text("Faça a primeira análise", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = CS.Ink)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Vamos olhar fotos, vídeos e aplicativos. Nada é apagado sem a sua confirmação.",
                                color = CS.Muted, fontSize = 14.sp, lineHeight = 20.sp
                            )
                            Spacer(Modifier.height(18.dp))
                            PrimaryButton("Analisar meu celular", onScan)
                        }
                    }
                }
            }
        }

        // ---------- Armazenamento ----------
        state.storage?.let { st ->
            item {
                CsCard {
                    Column {
                        Text("Armazenamento", fontWeight = FontWeight.Bold, color = CS.Ink, fontSize = 16.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${formatBytes(st.usedBytes)} usados de ${formatBytes(st.totalBytes)}",
                            fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = CS.Ink
                        )
                        Text(
                            "${(st.usedFraction * 100).toInt()}% ocupado · ${formatBytes(st.freeBytes)} livres",
                            color = CS.Muted, fontSize = 14.sp
                        )
                        Spacer(Modifier.height(14.dp))
                        val total = st.totalBytes.toFloat().coerceAtLeast(1f)
                        val m = state.media
                        if (m == null) {
                            SegmentedBar(listOf(st.usedFraction to CS.Ink))
                        } else {
                            val apps = state.appsBytes ?: 0L
                            val known = m.images.bytes + m.videos.bytes + m.audio.bytes + apps
                            val other = (st.usedBytes - known).coerceAtLeast(0)
                            SegmentedBar(
                                listOf(
                                    m.images.bytes / total to ColorPhotos,
                                    m.videos.bytes / total to ColorVideos,
                                    apps / total to ColorApps,
                                    m.audio.bytes / total to ColorAudio,
                                    other / total to ColorOther,
                                )
                            )
                            Spacer(Modifier.height(12.dp))
                            LegendDot(ColorPhotos, "Fotos (${formatCount(m.images.count)})", formatBytes(m.images.bytes))
                            LegendDot(ColorVideos, "Vídeos (${formatCount(m.videos.count)})", formatBytes(m.videos.bytes))
                            if (state.appsBytes != null) LegendDot(ColorApps, "Aplicativos", formatBytes(apps))
                            LegendDot(ColorAudio, "Áudios", formatBytes(m.audio.bytes))
                            LegendDot(
                                ColorOther,
                                if (state.appsBytes != null) "Sistema e outros" else "Apps, sistema e outros",
                                formatBytes(other)
                            )
                        }
                    }
                }
            }
        }

        if (state.scanned && state.mediaAccess != MediaAccess.FULL) {
            item {
                Notice(
                    if (state.mediaAccess == MediaAccess.PARTIAL)
                        "Você liberou só algumas fotos. A análise considera apenas elas."
                    else
                        "Sem acesso às fotos e vídeos, a análise fica incompleta.",
                    action = "Revisar permissões",
                    onAction = onOpenPermissions
                )
            }
        }

        // ---------- Recomendações ----------
        if (state.scanned) {
            item {
                RowCard(
                    "🖼️", "Fotos e vídeos duplicados",
                    if (state.duplicateCount > 0) "${formatCount(state.duplicateCount)} cópias · ${formatBytes(state.duplicateBytes)}"
                    else "Nenhuma cópia encontrada",
                    onClick = onOpenDuplicates
                )
            }
            item {
                RowCard(
                    "🎥", "Vídeos grandes",
                    if (state.largeVideos.isNotEmpty()) "${state.largeVideos.size} acima de 100 MB · ${formatBytes(state.largeVideoBytes)}"
                    else "Nenhum vídeo acima de 100 MB",
                    onClick = onOpenVideos
                )
            }
            item {
                RowCard(
                    "📱", "Aplicativos",
                    when {
                        !state.usageAccess -> "Libere o acesso de uso para ver os pouco usados"
                        state.unusedApps.isNotEmpty() -> "${state.unusedApps.size} sem uso há 90+ dias · ${formatBytes(state.unusedAppsBytes)}"
                        else -> "Nenhum app parado há mais de 90 dias"
                    },
                    onClick = onOpenApps
                )
            }
            state.media?.let { m ->
                item {
                    RowCard(
                        "☁️", "Backup",
                        "${formatBytes(m.images.bytes + m.videos.bytes)} em fotos e vídeos para proteger",
                        onClick = onOpenBackup
                    )
                }
            }
        }
    }
}
