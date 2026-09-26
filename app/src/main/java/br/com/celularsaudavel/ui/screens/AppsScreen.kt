package br.com.celularsaudavel.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.celularsaudavel.model.InstalledApp
import br.com.celularsaudavel.model.formatBytes
import br.com.celularsaudavel.model.formatDate
import br.com.celularsaudavel.model.formatDaysAgo
import br.com.celularsaudavel.model.isUnused
import br.com.celularsaudavel.ui.*

@Composable
fun AppsScreen(
    state: UiState,
    onOpenUsageSettings: () -> Unit,
    onUninstallFinished: (InstalledApp) -> Unit,
) {
    val context = LocalContext.current
    var sort by rememberSaveable { mutableIntStateOf(0) }
    var onlyUnused by rememberSaveable { mutableStateOf(false) }
    var pendingApp by remember { mutableStateOf<InstalledApp?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        pendingApp?.let(onUninstallFinished)
        pendingApp = null
    }

    val base = if (onlyUnused && state.usageAccess) state.apps.filter { it.isUnused() } else state.apps
    val list = when (sort) {
        0 -> base.sortedByDescending { it.sizeBytes }
        1 -> base.sortedBy { it.lastUsed ?: 0L }
        else -> base.sortedByDescending { it.installTime }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ScreenHeader(
                "Aplicativos",
                if (state.appsLoaded) "${state.apps.size} instalados por você" else "Carregando…"
            )
        }
        if (!state.usageAccess) {
            item {
                Notice(
                    "Para ver o tamanho real (com dados) e quando cada app foi usado pela última vez, libere o \"Acesso ao uso\" para o Celular Saudável.",
                    action = "Liberar acesso de uso",
                    onAction = onOpenUsageSettings
                )
            }
        }
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Pill("Maior tamanho", sort == 0) { sort = 0 }
                if (state.usageAccess) Pill("Menos usados", sort == 1) { sort = 1 }
                Pill("Recentes", sort == 2) { sort = 2 }
                if (state.usageAccess) Pill("Só parados 90+ dias", onlyUnused) { onlyUnused = !onlyUnused }
            }
        }
        if (state.appsLoading && state.apps.isEmpty()) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = CS.Green, trackColor = CS.Surface2) }
        }
        items(list, key = { it.packageName }) { app ->
            CsCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(CS.Surface2),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(app.name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = CS.Ink)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.name, fontWeight = FontWeight.SemiBold, color = CS.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                formatBytes(app.sizeBytes) + if (app.sizeIsEstimate) " (só o app)" else "",
                                color = CS.Ink, fontSize = 14.sp
                            )
                            val usage = when {
                                !state.usageAccess -> "Instalado em ${formatDate(app.installTime)}"
                                app.lastUsed != null -> "Último uso: ${formatDaysAgo(app.lastUsed)}"
                                else -> "Sem uso no último ano"
                            }
                            Text(usage, color = CS.Muted, fontSize = 13.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = {
                                try {
                                    pendingApp = app
                                    launcher.launch(
                                        Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
                                    )
                                } catch (_: Exception) {
                                    pendingApp = null
                                    try {
                                        context.startActivity(
                                            Intent(
                                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                Uri.fromParts("package", app.packageName, null)
                                            )
                                        )
                                    } catch (_: Exception) {
                                    }
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) { Text("Desinstalar", color = CS.Ink, fontSize = 13.sp) }
                    }
                }
            }
        }
        if (state.appsLoaded && list.isEmpty()) {
            item { Notice("Nenhum aplicativo nesta lista.", soft = false) }
        }
        item {
            Text(
                "Apps do sistema não aparecem. A desinstalação é feita pela janela oficial do Android.",
                color = CS.Muted, fontSize = 12.sp
            )
        }
    }
}
