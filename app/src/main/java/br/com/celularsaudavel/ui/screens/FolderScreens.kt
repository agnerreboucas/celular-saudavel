package br.com.celularsaudavel.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
    onDelete: (List<LocalFile>, DeleteMode) -> Unit,
    onPreview: (PreviewTarget) -> Unit,
    onOpenRecover: () -> Unit,
) {
    val cat = (state.folders + state.origins).firstOrNull { it.id == categoryId }
    val title = if (cat != null && cat.id.startsWith("origin:")) "${cat.group}: ${cat.title}" else cat?.title
    val files = cat?.files ?: emptyList()
    var selected by remember(files) {
        mutableStateOf(if (cat?.safe == true) files.map { it.path }.toSet() else emptySet())
    }
    var confirm by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(SelMode.ONE) }
    val chosen = files.filter { it.path in selected }
    val chosenBytes = chosen.sumOf { it.sizeBytes }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { ScreenHeader(title ?: "Pasta", cat?.description, onBack) }
            if (files.isNotEmpty()) item {
                Text("Toque num arquivo para ver o que é. Marque a caixa para selecionar.", color = CS.Muted, fontSize = 13.sp)
            }
            if (cat != null && !cat.safe) {
                item { Notice("Na hora de apagar você escolhe: guardar na Lixeira do app, guardar na nuvem ou apagar de vez. Na dúvida, guarde: dá para recuperar em Mais → Recuperar arquivos.") }
            }
            if (files.isEmpty()) {
                item { Notice("Nada aqui. 👏", soft = false) }
            } else {
                item {
                    Text(
                        "${formatCount(files.size)} arquivos · ${formatBytes(files.sumOf { it.sizeBytes })}",
                        color = CS.Ink, fontWeight = FontWeight.SemiBold
                    )
                }
                item {
                    SelectionBar(
                        mode = mode, onMode = { mode = it },
                        allSelected = chosen.size == files.size,
                        onToggleAll = {
                            selected = if (chosen.size == files.size) emptySet() else files.map { it.path }.toSet()
                        },
                        selectedCount = chosen.size, selectedBytes = chosenBytes
                    )
                }
            }
            selectableItems(
                items = files.take(1500), mode = mode,
                keyOf = { it.path },
                dateMsOf = { it.modifiedMs },
                sizeOf = { it.sizeBytes },
                selected = selected, onSelectedChange = { selected = it }
            ) { f ->
                val isSel = f.path in selected
                val preview = {
                    onPreview(
                        PreviewTarget(
                            name = f.name, sizeBytes = f.sizeBytes, dateMs = f.modifiedMs, path = f.path,
                            location = f.path.substringBeforeLast('/').substringAfter("/0/")
                        )
                    )
                }
                CsCard(onClick = preview) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FileThumb(f.path, f.name, Modifier.size(48.dp))
                        Spacer(Modifier.width(12.dp))
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
                item { Text("Mostrando os 1.500 maiores. \"Selecionar tudo\" inclui todos os ${formatCount(files.size)}.", color = CS.Muted, fontSize = 12.sp) }
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
        val canCloud = state.premium && state.drive.connected
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Como apagar ${formatCount(chosen.size)} arquivos?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${formatBytes(chosenBytes)} selecionados.", fontWeight = FontWeight.SemiBold)
                    DeleteOption(
                        "☁️ Guardar na nuvem e liberar agora",
                        if (canCloud) "Vão para o seu Google Drive e saem do celular. Recuperáveis por ${state.retentionDays} dias."
                        else "Precisa do Premium e do Google Drive conectado.",
                        enabled = canCloud
                    ) { confirm = false; onDelete(chosen, DeleteMode.CLOUD_BIN) }
                    DeleteOption(
                        "🗂️ Guardar na Lixeira do app",
                        "Recuperáveis por ${state.retentionDays} dias. Continuam ocupando espaço até você esvaziar.",
                        enabled = true
                    ) { confirm = false; onDelete(chosen, DeleteMode.PHONE_BIN) }
                    DeleteOption(
                        "🗑️ Apagar de vez",
                        if (cat?.safe == true) "Libera o espaço agora. São arquivos que não fazem falta."
                        else "Libera o espaço agora e NÃO tem como recuperar.",
                        enabled = true
                    ) { confirm = false; onDelete(chosen, DeleteMode.FOREVER) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun DeleteOption(title: String, desc: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, if (enabled) CS.Muted else CS.Surface2, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(12.dp)
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, color = if (enabled) CS.Ink else CS.Muted)
        Text(desc, fontSize = 13.sp, color = CS.Muted, lineHeight = 18.sp)
    }
}


/** Fotos, vídeos, áudios e documentos separados pela origem: WhatsApp, gravador, câmera, Downloads… */
@Composable
fun OriginsScreen(
    state: UiState,
    onBack: () -> Unit,
    onLoad: () -> Unit,
    onOpenCategory: (String) -> Unit,
    onRequestFolders: () -> Unit,
) {
    LaunchedEffect(state.folderAccess) { if (state.folderAccess && state.origins.isEmpty()) onLoad() }
    var type by remember { mutableStateOf(br.com.celularsaudavel.data.FileType.AUDIO) }
    val list = state.origins.filter { it.group == type }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader(
                "Por origem",
                "Veja de onde veio cada arquivo e selecione tudo de uma origem de uma vez, como todos os áudios do WhatsApp.",
                onBack
            )
        }
        if (!state.folderAccess) {
            item {
                Notice(
                    "Para separar os arquivos por origem, libere o \"Acesso a todos os arquivos\". A análise acontece só no seu celular.",
                    action = "Liberar acesso",
                    onAction = onRequestFolders
                )
            }
            return@LazyColumn
        }
        item {
            Row(
                Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                br.com.celularsaudavel.data.FileType.all.forEach { t ->
                    val n = state.origins.filter { it.group == t }.sumOf { it.files.size }
                    Pill(if (n > 0) "$t · ${formatCount(n)}" else t, type == t) { type = t }
                }
            }
        }
        if (state.originsLoading) {
            item {
                Column {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = CS.Green, trackColor = CS.Surface2)
                    Spacer(Modifier.height(6.dp))
                    Text("Procurando arquivos em todas as pastas…", color = CS.Muted, fontSize = 13.sp)
                }
            }
        } else if (list.isEmpty()) {
            item { Notice("Nenhum arquivo deste tipo encontrado.", soft = false) }
        } else {
            item {
                Text(
                    "${formatCount(list.sumOf { it.files.size })} arquivos · ${formatBytes(list.sumOf { it.bytes })}",
                    color = CS.Ink, fontWeight = FontWeight.SemiBold
                )
            }
        }
        items(list, key = { it.id }) { c ->
            RowCard(
                c.emoji, c.title,
                "${formatCount(c.files.size)} arquivos · ${formatBytes(c.bytes)}\n${c.description}",
                onClick = { onOpenCategory(c.id) }
            )
        }
        if (!state.originsLoading) {
            item { TextButton(onClick = onLoad) { Text("Procurar de novo", color = CS.Ink) } }
        }
    }
}
