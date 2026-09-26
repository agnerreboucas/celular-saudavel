package br.com.celularsaudavel.ui.screens

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

const val SORT_SIZE = 0
const val SORT_UNUSED = 1
const val SORT_RECENT = 2
const val SORT_CACHE = 3

@Composable
fun AppsScreen(
    state: UiState,
    initialSort: Int,
    onOpenUsageSettings: () -> Unit,
    onUninstall: (InstalledApp) -> Unit,
    onOpenAppSettings: (InstalledApp) -> Unit,
) {
    var sort by rememberSaveable(initialSort) { mutableIntStateOf(initialSort) }

    if (!state.usageAccess && (sort == SORT_UNUSED || sort == SORT_CACHE)) sort = SORT_SIZE
    val list = when (sort) {
        SORT_UNUSED -> state.unusedApps
        SORT_CACHE -> state.apps.filter { (it.cacheBytes ?: 0L) > 0 }.sortedByDescending { it.cacheBytes ?: 0L }
        SORT_RECENT -> state.apps.sortedByDescending { it.installTime }
        else -> state.apps.sortedByDescending { it.sizeBytes }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ScreenHeader(
                "Aplicativos",
                if (state.appsLoaded) "${state.apps.count { it.removable }} instalados por você" else "Carregando…"
            )
        }
        if (!state.usageAccess) {
            item {
                Notice(
                    "Para ver o cache, o tamanho real e quando cada app foi usado pela última vez, libere o \"Acesso ao uso\" para o Celular Saudável.",
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
                if (state.usageAccess) Pill("Parados 90+ dias (${state.unusedApps.size})", sort == SORT_UNUSED) { sort = SORT_UNUSED }
                if (state.usageAccess) Pill("Mais cache", sort == SORT_CACHE) { sort = SORT_CACHE }
                Pill("Maior tamanho", sort == SORT_SIZE) { sort = SORT_SIZE }
                Pill("Recentes", sort == SORT_RECENT) { sort = SORT_RECENT }
            }
        }
        if (sort == SORT_CACHE && state.usageAccess) {
            item {
                Notice(
                    "Cache total: ${formatBytes(state.cacheBytes)}. O Android não deixa um app apagar o cache de outro sozinho. " +
                        "Toque em \"Limpar cache\" e, na tela que abrir, em Armazenamento → Limpar cache. Seus dados e logins não são apagados.",
                    soft = false
                )
            }
        }
        if (state.appsLoading && state.apps.isEmpty()) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = CS.Green, trackColor = CS.Surface2) }
        }
        items(list, key = { it.packageName }) { app ->
            AppCard(app, state.usageAccess, sort == SORT_CACHE, onUninstall, onOpenAppSettings)
        }
        if (state.appsLoaded && list.isEmpty()) {
            item { Notice("Nenhum aplicativo nesta lista. 👏", soft = false) }
        }
        item {
            Text(
                "Apps do sistema não aparecem (exceto a tela inicial, para limpar o cache dela). A desinstalação usa a janela oficial do Android.",
                color = CS.Muted, fontSize = 12.sp
            )
        }
    }
}

@Composable
fun AppCard(
    app: InstalledApp,
    usageAccess: Boolean,
    cacheFirst: Boolean,
    onUninstall: (InstalledApp) -> Unit,
    onOpenAppSettings: (InstalledApp) -> Unit,
) {
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
                    val cache = app.cacheBytes
                    Text(
                        if (cacheFirst && cache != null) "Cache: ${formatBytes(cache)} · total ${formatBytes(app.sizeBytes)}"
                        else formatBytes(app.sizeBytes) + if (app.sizeIsEstimate) " (só o app)" else "",
                        color = CS.Ink, fontSize = 14.sp
                    )
                    val usage = when {
                        !usageAccess -> "Instalado em ${formatDate(app.installTime)}"
                        app.lastUsed != null -> "Último uso: ${formatDaysAgo(app.lastUsed)}"
                        else -> "Sem uso no último ano"
                    }
                    Text(usage, color = CS.Muted, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val cache = app.cacheBytes
                if (cache != null && cache > 1_000_000L) {
                    OutlinedButton(
                        onClick = { onOpenAppSettings(app) },
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) { Text("Limpar cache", color = CS.Ink, fontSize = 13.sp) }
                }
                if (app.removable) {
                    OutlinedButton(
                        onClick = { onUninstall(app) },
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) { Text("Desinstalar", color = CS.Ink, fontSize = 13.sp) }
                }
            }
        }
    }
}
