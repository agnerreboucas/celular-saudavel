package br.com.celularsaudavel.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import br.com.celularsaudavel.data.BackupState
import br.com.celularsaudavel.data.BinPlace
import br.com.celularsaudavel.data.RecycleItem
import br.com.celularsaudavel.model.DAY_MS
import br.com.celularsaudavel.model.HistoryType
import br.com.celularsaudavel.model.formatBytes
import br.com.celularsaudavel.model.formatCount
import br.com.celularsaudavel.model.formatDate
import br.com.celularsaudavel.ui.*

private enum class HistTab(val label: String) { BACKUPS("Backups"), RESTORE("Dá para recuperar"), DELETED("Apagados") }

/**
 * Histórico em um lugar só:
 * - Backups: para qual e-mail cada arquivo foi, quanto e quando.
 * - Dá para recuperar: o que o app apagou e até quando ainda volta (com opção de apagar de vez).
 * - Apagados: o que já saiu do celular.
 */
@Composable
fun HistoryScreen(
    state: UiState,
    onBack: () -> Unit,
    onLoad: () -> Unit = {},
    onRestore: (List<RecycleItem>) -> Unit = {},
    onDeleteForever: (List<RecycleItem>) -> Unit = {},
    onOpenSystemTrash: () -> Unit = {},
    onOpenRecover: () -> Unit = {},
) {
    LaunchedEffect(Unit) { onLoad() }
    var tab by rememberSaveable { mutableStateOf(HistTab.BACKUPS) }
    var confirm by remember { mutableStateOf<List<RecycleItem>?>(null) }
    val d = state.drive
    val now = System.currentTimeMillis()
    val retentionMs = state.retentionDays * DAY_MS

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { ScreenHeader("Histórico", "O que foi salvo, o que ainda volta e o que já saiu do celular.", onBack) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HistTab.entries.forEach { t -> Pill(t.label, tab == t) { tab = t } }
            }
        }
        state.recycleMessage?.let { m -> item { Notice(m, soft = false) } }

        when (tab) {
            HistTab.BACKUPS -> {
                if (d.accounts.isEmpty()) {
                    item { EmptyNote("Quando você fizer backup, aparece aqui para qual e-mail cada arquivo foi.") }
                }
                items(d.accounts) { a ->
                    val current = a.email.isNotBlank() && a.email.equals(d.email, ignoreCase = true)
                    CsCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIcon("📧")
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    a.email.ifBlank { "Conta de backups antigos" },
                                    fontWeight = FontWeight.SemiBold, color = CS.Ink, fontSize = 15.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${formatCount(a.count)} arquivos · ${formatBytes(a.bytes)} · último em ${formatDate(a.lastAt)}",
                                    color = CS.Muted, fontSize = 13.sp
                                )
                            }
                            if (current) Tag("Atual")
                        }
                    }
                }
                if (d.recent.isNotEmpty()) {
                    item { SectionLabel("Últimos arquivos protegidos") }
                    items(d.recent, key = { "b" + it.uri }) { r ->
                        CsCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AppIcon(if (r.category == "video") "🎥" else "🖼️", size = 36.dp, corner = 10.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(r.name, color = CS.Ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        buildString {
                                            append(if (r.state == BackupState.DELETED_LOCAL) "Salvo e liberado do celular" else "Salvo, ainda no celular")
                                            append(" · ").append(formatDate(r.updated))
                                            r.account?.let { append(" · ").append(it) }
                                        },
                                        color = CS.Muted, fontSize = 12.sp, lineHeight = 16.sp
                                    )
                                }
                                Text(formatBytes(r.size), color = CS.Ink, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            HistTab.RESTORE -> {
                item {
                    Text(
                        "O que o app apagou fica guardado por ${state.retentionDays} dias. Depois disso, é apagado de vez.",
                        color = CS.Muted, fontSize = 13.sp, lineHeight = 18.sp
                    )
                }
                if (state.recycle.isEmpty()) {
                    item { EmptyNote("Nada guardado no momento.") }
                } else {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { onRestore(state.recycle) }, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)) {
                                Text("Recuperar tudo", color = CS.Ink)
                            }
                            OutlinedButton(onClick = { confirm = state.recycle }, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)) {
                                Text("Apagar tudo de vez", color = Status.Red)
                            }
                        }
                    }
                }
                items(state.recycle, key = { "r" + it.id }) { r ->
                    val until = r.deletedAt + retentionMs
                    val left = ((until - now) / DAY_MS).coerceAtLeast(0L)
                    CsCard {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AppIcon(if (r.place == BinPlace.CLOUD) "☁️" else "📱", size = 36.dp, corner = 10.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(r.name, color = CS.Ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        if (r.place == BinPlace.CLOUD) "No Google Drive${r.account?.let { " de $it" } ?: ""}" else "Guardado no celular",
                                        color = CS.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(formatBytes(r.size), color = CS.Ink, fontSize = 13.sp)
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BareIcon("🕒", if (left <= 7) Status.Orange else CS.Green, 16.dp)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Pode voltar até ${formatDate(until)} · faltam $left dia${if (left == 1L) "" else "s"}",
                                    color = if (left <= 7) Status.Orange else CS.Green, fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row {
                                TextButton(onClick = { onRestore(listOf(r)) }) { Text("Recuperar", color = CS.Green) }
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { confirm = listOf(r) }) { Text("Apagar de vez", color = Status.Red) }
                            }
                        }
                    }
                }
                item { RowCard("♻️", "Recuperar com filtros e prévia", "Escolha o período e veja cada arquivo antes", onOpenRecover) }
                item { RowCard("🗑️", "Lixeira da galeria", "Fotos e vídeos ficam lá por 30 dias", onOpenSystemTrash) }
            }

            HistTab.DELETED -> {
                item {
                    CsCard {
                        Column {
                            Text("Espaço liberado", color = CS.Muted, fontSize = 14.sp)
                            Text(formatBytes(state.totalFreed), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = CS.Ink)
                            val trashed = state.history.filter {
                                it.type == HistoryType.VIDEOS_TRASHED || it.type == HistoryType.SEQUENCE_TRASHED || it.type == HistoryType.SCREENSHOTS_TRASHED
                            }.sumOf { it.bytes }
                            if (trashed > 0) Text("+ ${formatBytes(trashed)} enviados à lixeira da galeria", color = CS.Muted, fontSize = 13.sp)
                        }
                    }
                }
                if (state.history.isEmpty()) {
                    item { EmptyNote("Quando você liberar espaço, o registro aparece aqui.") }
                }
                items(state.history) { h ->
                    CsCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(iconOf(h.type), size = 36.dp, corner = 10.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(h.type.label, fontWeight = FontWeight.SemiBold, color = CS.Ink, fontSize = 14.sp)
                                Text(
                                    buildString {
                                        append(formatDate(h.timestamp))
                                        if (h.detail.isNotBlank()) append(" · ").append(h.detail)
                                        else append(" · ${h.count} arquivo").append(if (h.count == 1) "" else "s")
                                        append(" · ").append(stateOf(h.type))
                                    },
                                    color = CS.Muted, fontSize = 12.sp, lineHeight = 16.sp
                                )
                            }
                            Text(formatBytes(h.bytes), fontWeight = FontWeight.SemiBold, color = CS.Ink, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    confirm?.let { list ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            icon = { AppIcon("🗑️", size = 52.dp, corner = 16.dp, tint = Status.Red, background = Status.Red.copy(alpha = 0.10f)) },
            title = { Text("Apagar de vez?") },
            text = {
                Text(
                    "${list.size} arquivo${if (list.size == 1) "" else "s"} (${formatBytes(list.sumOf { it.size })}) " +
                        "não poderão mais ser recuperados. Os que estão no Google Drive também saem de lá."
                )
            },
            confirmButton = {
                TextButton(onClick = { onDeleteForever(list); confirm = null }) { Text("Apagar de vez", color = Status.Red) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancelar", color = CS.Ink) } }
        )
    }
}

private fun iconOf(t: HistoryType): String = when (t) {
    HistoryType.DUPLICATES_REMOVED -> "🗂️"
    HistoryType.VIDEOS_TRASHED -> "🎥"
    HistoryType.APP_UNINSTALLED -> "📦"
    HistoryType.TRASH_DELETED -> "🗑️"
    HistoryType.FILES_DELETED -> "📂"
    HistoryType.SEQUENCE_TRASHED -> "📷"
    HistoryType.SCREENSHOTS_TRASHED -> "📸"
    HistoryType.BACKUP_FREED -> "☁️"
}

private fun stateOf(t: HistoryType): String = when (t) {
    HistoryType.VIDEOS_TRASHED, HistoryType.SEQUENCE_TRASHED, HistoryType.SCREENSHOTS_TRASHED -> "na lixeira da galeria (30 dias)"
    HistoryType.BACKUP_FREED -> "cópia no Google Drive"
    HistoryType.FILES_DELETED -> "veja em \"Dá para recuperar\""
    HistoryType.APP_UNINSTALLED -> "reinstale pela Play Store"
    else -> "apagado de vez"
}

@Composable
private fun Tag(text: String) {
    Text(
        text, color = CS.Green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(CS.GreenSoft).padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = CS.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun EmptyNote(text: String) {
    Text(text, color = CS.Muted, fontSize = 14.sp, lineHeight = 20.sp)
}
