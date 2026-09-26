package br.com.celularsaudavel.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.celularsaudavel.model.LocalFile
import br.com.celularsaudavel.model.formatBytes
import br.com.celularsaudavel.model.formatCount
import br.com.celularsaudavel.model.formatDate
import br.com.celularsaudavel.ui.*

@Composable
fun FoldersScreen(
    state: UiState,
    onBack: () -> Unit,
    onLoad: () -> Unit,
    onOpenCategory: (String) -> Unit,
    onRequestFolders: () -> Unit,
) {
    LaunchedEffect(state.folderAccess) { if (state.folderAccess) onLoad() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader(
                "Pastas e ocultos",
                "WhatsApp, Telegram, Instagram, Downloads e temporários, inclusive as pastas escondidas.",
                onBack
            )
        }
        if (!state.folderAccess) {
            item {
                Notice(
                    "Para ver essas pastas, libere o \"Acesso a todos os arquivos\" para o Celular Saudável. A análise acontece só no seu celular.",
                    action = "Liberar acesso às pastas",
                    onAction = onRequestFolders
                )
            }
            return@LazyColumn
        }
        if (state.foldersLoading && state.folders.isEmpty()) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = CS.Green, trackColor = CS.Surface2) }
        }
        val safe = state.folders.filter { it.safe }
        if (safe.isNotEmpty()) {
            item { SectionTitle("Pode apagar sem medo") }
            items(safe, key = { it.id }) { c ->
                RowCard(c.emoji, c.title, "${formatCount(c.files.size)} arquivos · ${formatBytes(c.bytes)}", onClick = { onOpenCategory(c.id) })
            }
        }
        state.folders.filter { !it.safe }.groupBy { it.group }.forEach { (group, cats) ->
            item { SectionTitle("$group · revise antes") }
            items(cats, key = { it.id }) { c ->
                RowCard(c.emoji, c.title, "${formatCount(c.files.size)} arquivos · ${formatBytes(c.bytes)}", onClick = { onOpenCategory(c.id) })
            }
        }
        if (!state.foldersLoading && state.folders.isEmpty()) {
            item { Notice("Nenhum arquivo encontrado nessas pastas. 👏", soft = false) }
        }
        if (state.bigFolders.isNotEmpty()) {
            item { SectionTitle("Maiores pastas do celular") }
            item {
                CsCard {
                    Column {
                        state.bigFolders.forEach { (path, size) ->
                            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(path, color = CS.Ink, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.width(8.dp))
                                Text(formatBytes(size), color = CS.Muted, fontSize = 14.sp)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "A pasta Android/data (dados internos dos apps) é protegida pelo Android e não pode ser lida por nenhum app.",
                            color = CS.Muted, fontSize = 12.sp
                        )
                    }
                }
            }
        } else if (!state.foldersLoading) {
            item {
                TextButton(onClick = onLoad) { Text("Calcular as maiores pastas", color = CS.Ink) }
            }
        }
    }
}

@Composable
fun FolderCategoryScreen(
    state: UiState,
    categoryId: String,
    onBack: () -> Unit,
    onDelete: (List<LocalFile>) -> Unit,
) {
    val cat = state.folders.firstOrNull { it.id == categoryId }
    val files = cat?.files ?: emptyList()
    var selected by remember(files) {
        mutableStateOf(if (cat?.safe == true) files.map { it.path }.toSet() else emptySet())
    }
    var confirm by remember { mutableStateOf(false) }
    val chosen = files.filter { it.path in selected }
    val chosenBytes = chosen.sumOf { it.sizeBytes }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { ScreenHeader(cat?.title ?: "Pasta", cat?.description, onBack) }
            if (cat != null && !cat.safe) {
                item { Notice("Esses arquivos não vão para a lixeira: apagar aqui é definitivo. Se forem importantes, faça o backup antes.") }
            }
            if (files.isEmpty()) {
                item { Notice("Nada aqui. 👏", soft = false) }
            } else {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${formatCount(files.size)} arquivos · ${formatBytes(files.sumOf { it.sizeBytes })}",
                            color = CS.Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            selected = if (selected.size == files.size) emptySet() else files.map { it.path }.toSet()
                        }) { Text(if (selected.size == files.size) "Desmarcar todos" else "Marcar todos", color = CS.Ink) }
                    }
                }
            }
            items(files.take(1500), key = { it.path }) { f ->
                val isSel = f.path in selected
                CsCard(onClick = { selected = if (isSel) selected - f.path else selected + f.path }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(f.name, color = CS.Ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${formatBytes(f.sizeBytes)} · ${formatDate(f.modifiedMs)}" +
                                    if (f.path.contains("/Sent/")) " · enviada" else if (f.path.contains("/Private/")) " · particular" else "",
                                color = CS.Muted, fontSize = 12.sp
                            )
                        }
                        Checkbox(
                            checked = isSel,
                            onCheckedChange = { selected = if (it) selected + f.path else selected - f.path },
                            colors = CheckboxDefaults.colors(checkedColor = CS.Ink)
                        )
                    }
                }
            }
            if (files.size > 1500) {
                item { Text("Mostrando os 1.500 maiores. \"Marcar todos\" inclui todos os ${formatCount(files.size)}.", color = CS.Muted, fontSize = 12.sp) }
            }
        }
        if (files.isNotEmpty()) {
            Column(Modifier.background(CS.Surface).padding(20.dp)) {
                PrimaryButton(
                    if (chosen.isEmpty()) "Selecione os arquivos" else "Apagar ${formatCount(chosen.size)} · ${formatBytes(chosenBytes)}",
                    onClick = { confirm = true },
                    enabled = chosen.isNotEmpty()
                )
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Apagar de vez?") },
            text = {
                Text(
                    "${formatCount(chosen.size)} arquivos (${formatBytes(chosenBytes)}) serão apagados definitivamente. " +
                        if (cat?.safe == true) "São arquivos que não fazem falta." else "Eles não vão para a lixeira e não têm backup pelo app."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onDelete(chosen)
                }) { Text("Apagar") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } }
        )
    }
}
