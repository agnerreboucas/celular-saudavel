package br.com.celularsaudavel.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.items
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
val ColorTrash = Color(0xFF7A6F5C)

@Composable
fun HomeScreen(
    state: UiState,
    onScan: () -> Unit,
    onOpenClean: () -> Unit,
    onOpenDuplicates: () -> Unit,
    onOpenVideos: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenFolders: () -> Unit,
    onRequestFolders: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenUnused: () -> Unit,
    onOpenCache: () -> Unit,
    onOpenUsageSettings: () -> Unit,
    onUninstall: (br.com.celularsaudavel.model.InstalledApp) -> Unit,
    onOpenAppSettings: (br.com.celularsaudavel.model.InstalledApp) -> Unit,
    onOpenBackup: () -> Unit,
    onOpenPermissions: () -> Unit,
    onAllowNotifications: () -> Unit,
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
                            HealthGauge(health.value, health.label, Modifier.padding(horizontal = 12.dp))
                            Spacer(Modifier.height(14.dp))
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Status.forScore(health.value).copy(alpha = 0.10f))
                                    .padding(14.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Text(health.face, fontSize = 30.sp)
                                Spacer(Modifier.width(12.dp))
                                Text(health.likePerson, color = CS.Ink, fontSize = 14.sp, lineHeight = 20.sp)
                            }
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
                            } else if ((state.storage?.usedFraction ?: 0f) > 0.7f) {
                                Text(
                                    "O índice caiu porque o armazenamento está ${((state.storage?.usedFraction ?: 0f) * 100).toInt()}% ocupado. " +
                                        (if (state.foldersFeature) "Veja as pastas ocultas e o cache dos apps para achar onde está o espaço." else "Veja o cache e os apps parados para achar onde está o espaço."),
                                    fontSize = 15.sp, color = CS.Ink, lineHeight = 21.sp
                                )
                                Spacer(Modifier.height(12.dp))
                                PrimaryButton("Ver onde está o espaço", onOpenClean)
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

        if (state.scanned && state.monitor.enabled && !state.monitor.canNotify) {
            item {
                Notice(
                    "Quer que o app acompanhe a saúde do celular e avise quando o espaço estiver acabando?",
                    soft = false,
                    action = "Ativar avisos",
                    onAction = onAllowNotifications
                )
            }
        }

        // ---------- Antes x depois da sessão ----------
        val start = state.sessionStartUsed
        val stNow = state.storage
        if (start != null && stNow != null) {
            item { SessionCard(start, stNow, state.sessionFreed, state.avgPhotoBytes) }
        }

        // ---------- Sinais vitais ----------
        if (state.scanned && stNow != null) {
            item {
                val folga = folgaOf(stNow)
                val repeated = state.duplicateBytes + state.sequenceBytes
                val d = state.drive
                val unprotected = d.toProtect.values.sumOf { it.bytes }
                val vitals = listOf(
                    Vital("💾", "Espaço", "${formatBytes(stNow.freeBytes)} livres", folga.label, folga.color),
                    when {
                        repeated == 0L -> Vital("🗂️", "Organização", "Sem fotos repetidas", "Em ordem", Status.Green)
                        repeated < 1_000_000_000L -> Vital("🗂️", "Organização", "${formatBytes(repeated)} em repetidas", "Atenção", Status.Yellow)
                        else -> Vital("🗂️", "Organização", "${formatBytes(repeated)} em repetidas", "Acumulando", Status.Orange)
                    },
                    when {
                        !d.connected -> Vital("🛡️", "Proteção", "Fotos e vídeos sem backup", "Sem backup", Status.Orange)
                        d.summary.verified + d.summary.freed == 0 -> Vital("🛡️", "Proteção", "Drive conectado, backup não feito", "Pendente", Status.Yellow)
                        unprotected < 500_000_000L -> Vital("🛡️", "Proteção", "Quase tudo com backup verificado", "Protegido", Status.Green)
                        else -> Vital("🛡️", "Proteção", "${formatBytes(unprotected)} ainda sem backup", "Parcial", Status.Yellow)
                    },
                    when {
                        !state.usageAccess -> Vital("📱", "Aplicativos", "Libere o acesso de uso para medir", "Sem dados", StatusNeutral)
                        state.unusedApps.isEmpty() -> Vital("📱", "Aplicativos", "Nenhum app parado", "Em ordem", Status.Green)
                        state.unusedApps.size <= 5 -> Vital("📱", "Aplicativos", "${state.unusedApps.size} parados · ${formatBytes(state.unusedAppsBytes)}", "Atenção", Status.Yellow)
                        else -> Vital("📱", "Aplicativos", "${state.unusedApps.size} parados · ${formatBytes(state.unusedAppsBytes)}", "Acumulando", Status.Orange)
                    },
                    when {
                        state.trashBytes < 200_000_000L -> Vital("🗑️", "Lixeira", "Vazia ou quase", "Em ordem", Status.Green)
                        else -> Vital("🗑️", "Lixeira", "${formatBytes(state.trashBytes)} esperando", "Esvaziar", Status.Yellow)
                    },
                )
                VitalsCard(vitals, onClick = onOpenClean)
            }
            item { FolgaCard(stNow, state.avgPhotoBytes) }
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
                            val trash = state.trashBytes
                            val other = (st.usedBytes - known - trash).coerceAtLeast(0)
                            Box(Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                StorageDonut(
                                    listOf(
                                        m.images.bytes / total to ColorPhotos,
                                        m.videos.bytes / total to ColorVideos,
                                        apps / total to ColorApps,
                                        m.audio.bytes / total to ColorAudio,
                                        trash / total to ColorTrash,
                                        other / total to ColorOther,
                                    ),
                                    "${(st.usedFraction * 100).toInt()}%",
                                    "ocupado"
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("Toque para revisar", color = CS.Muted, fontSize = 12.sp)
                            LegendDot(ColorPhotos, "Fotos (${formatCount(m.images.count)})", formatBytes(m.images.bytes), onOpenDuplicates)
                            LegendDot(ColorVideos, "Vídeos (${formatCount(m.videos.count)})", formatBytes(m.videos.bytes), onOpenVideos)
                            LegendDot(ColorApps, "Aplicativos", if (state.appsBytes != null) formatBytes(apps) else "ver lista", onOpenApps)
                            LegendDot(ColorAudio, "Áudios", formatBytes(m.audio.bytes))
                            if (state.trash.isNotEmpty()) {
                                LegendDot(ColorTrash, "Lixeira (${formatCount(state.trash.size)})", formatBytes(trash), onOpenTrash)
                            }
                            LegendDot(
                                ColorOther,
                                if (state.appsBytes != null) "Sistema e outros" else "Apps, sistema e outros",
                                formatBytes(other),
                                onOpenFolders
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
            if (!state.foldersFeature) {
                // Versão da Play: sem pastas ocultas.
            } else if (!state.folderAccess) {
                item {
                    Notice(
                        "Boa parte do espaço pode estar em pastas ocultas do WhatsApp, Downloads e temporários. Libere o acesso para analisar.",
                        action = "Liberar acesso às pastas",
                        onAction = onRequestFolders
                    )
                }
            } else if (state.folders.isNotEmpty()) {
                if (state.safeFolderBytes > 0) {
                    item {
                        RowCard(
                            "🧹", "Temporários e sobras",
                            "${formatBytes(state.safeFolderBytes)} que podem ir sem medo",
                            onClick = onOpenFolders
                        )
                    }
                }
                if (state.whatsappBytes > 0) {
                    item {
                        RowCard("💬", "WhatsApp", "${formatBytes(state.whatsappBytes)} em fotos, vídeos, áudios e status", onClick = onOpenFolders)
                    }
                }
            }
            if (state.trash.isNotEmpty()) {
                item {
                    RowCard(
                        "🗑️", "Lixeira",
                        "${formatCount(state.trash.size)} itens · ${formatBytes(state.trashBytes)} ainda ocupando espaço",
                        onClick = onOpenTrash
                    )
                }
            }
            if (!state.usageAccess) {
                item {
                    Notice(
                        "Libere o \"Acesso ao uso\" para ver aqui os apps parados e o cache de cada app.",
                        action = "Liberar acesso de uso",
                        onAction = onOpenUsageSettings
                    )
                }
            } else {
                item {
                    RowCard(
                        "🧽", "Cache dos aplicativos",
                        if (state.cacheBytes > 0) "${formatBytes(state.cacheBytes)} em ${state.appsWithCache} apps · limpar sem perder dados"
                        else "Nenhum cache relevante",
                        onClick = onOpenCache
                    )
                }
                if (state.unusedApps.isNotEmpty()) {
                    item {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Apps parados há 90+ dias", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CS.Ink)
                                Text(
                                    "${state.unusedApps.size} apps · ${formatBytes(state.unusedAppsBytes)}",
                                    color = CS.Muted, fontSize = 14.sp
                                )
                            }
                            TextButton(onClick = onOpenUnused) { Text("Ver todos", color = CS.Ink) }
                        }
                    }
                    items(state.unusedApps.take(5), key = { "unused-" + it.packageName }) { app ->
                        AppCard(app, usageAccess = true, cacheFirst = false, onUninstall = onUninstall, onOpenAppSettings = onOpenAppSettings)
                    }
                } else {
                    item { RowCard("📱", "Aplicativos", "Nenhum app parado há mais de 90 dias", onClick = onOpenApps) }
                }
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
