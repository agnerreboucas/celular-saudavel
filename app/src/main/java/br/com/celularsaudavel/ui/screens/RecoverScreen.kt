package br.com.celularsaudavel.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.celularsaudavel.data.BinPlace
import br.com.celularsaudavel.data.RecycleItem
import br.com.celularsaudavel.model.DAY_MS
import br.com.celularsaudavel.model.formatBytes
import br.com.celularsaudavel.model.formatCount
import br.com.celularsaudavel.model.formatDate
import br.com.celularsaudavel.ui.*

@Composable
fun RecoverScreen(
    state: UiState,
    onBack: () -> Unit,
    onLoad: () -> Unit,
    onRestore: (List<RecycleItem>) -> Unit,
    onDeleteForever: (List<RecycleItem>) -> Unit,
    onSetRetention: (Int) -> Unit,
    onOpenSystemTrash: () -> Unit,
    onPreview: (PreviewTarget) -> Unit,
) {
    LaunchedEffect(Unit) { onLoad() }
    var period by remember { mutableIntStateOf(0) } // 0 = tudo
    val now = System.currentTimeMillis()
    val items = state.recycle.filter { period == 0 || now - it.deletedAt <= period * DAY_MS }
    var selected by remember(items) { mutableStateOf(emptySet<String>()) }
    var mode by remember { mutableStateOf(SelMode.ONE) }
    var confirmForever by remember { mutableStateOf(false) }
    val chosen = items.filter { it.id.toString() in selected }
    val chosenBytes = chosen.sumOf { it.size }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { ScreenHeader("Recuperar arquivos", "Tudo o que o Celular Saudável guardou antes de apagar.", onBack) }

            item {
                CsCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Guardar o que eu apagar por", fontWeight = FontWeight.SemiBold, color = CS.Ink)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(30, 90, 180).forEach { d ->
                                Pill(if (d == 30) "1 mês" else "${d / 30} meses", state.retentionDays == d) { onSetRetention(d) }
                            }
                        }
                        Text(
                            "Depois desse prazo o arquivo é apagado de vez, automaticamente.",
                            color = CS.Muted, fontSize = 12.sp
                        )
                    }
                }
            }

            item { RowCard("🖼️", "Fotos e vídeos na lixeira do Android", "Restaure pela Lixeira (até ~30 dias)", onClick = onOpenSystemTrash) }

            state.recycleMessage?.let { msg -> item { Notice(msg, soft = false) } }

            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Tudo", period == 0) { period = 0 }
                    Pill("7 dias", period == 7) { period = 7 }
                    Pill("30 dias", period == 30) { period = 30 }
                    Pill("3 meses", period == 90) { period = 90 }
                    Pill("6 meses", period == 180) { period = 180 }
                }
            }

            if (items.isEmpty()) {
                item {
                    Notice(
                        "Nada guardado neste período. Ao apagar arquivos de pastas, escolha \"Guardar na Lixeira do app\" ou \"Guardar na nuvem\" para poder recuperar aqui.",
                        soft = false
                    )
                }
            } else {
                item {
                    SelectionBar(
                        mode = mode, onMode = { mode = it },
                        allSelected = chosen.size == items.size,
                        onToggleAll = {
                            selected = if (chosen.size == items.size) emptySet() else items.map { it.id.toString() }.toSet()
                        },
                        selectedCount = chosen.size, selectedBytes = chosenBytes
                    )
                }
            }

            selectableItems(
                items = items, mode = mode,
                keyOf = { it.id.toString() },
                dateMsOf = { it.deletedAt },
                sizeOf = { it.size },
                selected = selected, onSelectedChange = { selected = it }
            ) { r ->
                val k = r.id.toString()
                val isSel = k in selected
                val left = state.retentionDays - ((now - r.deletedAt) / DAY_MS).toInt()
                CsCard(onClick = {
                    if (r.place == BinPlace.PHONE && r.binPath != null) {
                        onPreview(PreviewTarget(r.name, r.size, r.deletedAt, path = r.binPath, location = "Estava em ${r.originalPath.substringBeforeLast('/').substringAfter("/0/")}"))
                    } else {
                        selected = if (isSel) selected - k else selected + k
                    }
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (r.place == BinPlace.PHONE && r.binPath != null) {
                            FileThumb(r.binPath, r.name, Modifier.size(48.dp))
                        } else {
                            Box(Modifier.size(48.dp).background(CS.Surface2), contentAlignment = Alignment.Center) { Text("☁️", fontSize = 20.sp) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.name, color = CS.Ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${formatBytes(r.size)} · apagado em ${formatDate(r.deletedAt)} · " +
                                    (if (r.place == BinPlace.CLOUD) "na nuvem" else "no celular"),
                                color = CS.Muted, fontSize = 12.sp
                            )
                            Text(
                                if (left > 0) "Recuperável por mais $left dias" else "Será apagado de vez em breve",
                                color = if (left > 7) CS.Green else CS.Amber, fontSize = 12.sp
                            )
                        }
                        Checkbox(
                            checked = isSel,
                            onCheckedChange = { selected = if (it) selected + k else selected - k },
                            colors = CheckboxDefaults.colors(checkedColor = CS.Ink)
                        )
                    }
                }
            }

            item {
                Text(
                    "Arquivos apagados por outros apps, ou antes de você usar o Celular Saudável, não podem ser recuperados: " +
                        "o Android não deixa nenhum app ler o que já foi apagado da memória.",
                    color = CS.Muted, fontSize = 12.sp, lineHeight = 17.sp
                )
            }
        }
        if (items.isNotEmpty()) {
            Column(Modifier.background(CS.Surface).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(
                    if (chosen.isEmpty()) "Selecione o que recuperar" else "Recuperar ${formatCount(chosen.size)} · ${formatBytes(chosenBytes)}",
                    onClick = { onRestore(chosen); selected = emptySet() },
                    enabled = chosen.isNotEmpty()
                )
                OutlinedButton(
                    onClick = { confirmForever = true },
                    enabled = chosen.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Apagar de vez", color = CS.Ink) }
            }
        }
    }

    if (confirmForever) {
        AlertDialog(
            onDismissRequest = { confirmForever = false },
            title = { Text("Apagar de vez?") },
            text = { Text("${formatCount(chosen.size)} arquivos (${formatBytes(chosenBytes)}) serão apagados definitivamente e não poderão mais ser recuperados.") },
            confirmButton = {
                TextButton(onClick = { confirmForever = false; onDeleteForever(chosen); selected = emptySet() }) { Text("Apagar de vez") }
            },
            dismissButton = { TextButton(onClick = { confirmForever = false }) { Text("Cancelar") } }
        )
    }
}
