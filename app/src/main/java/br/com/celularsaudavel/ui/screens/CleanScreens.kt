package br.com.celularsaudavel.ui.screens

import android.app.Activity
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.celularsaudavel.model.MediaFile
import br.com.celularsaudavel.model.formatBytes
import br.com.celularsaudavel.model.formatCount
import br.com.celularsaudavel.model.formatDate
import br.com.celularsaudavel.ui.*

private val canRemove = Build.VERSION.SDK_INT >= 30

@Composable
fun CleanScreen(
    state: UiState,
    onScan: () -> Unit,
    onOpen: (String) -> Unit,
    onOpenApps: () -> Unit,
    onOpenCache: () -> Unit,
    onRequestFolders: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = ListSpacing
    ) {
        item { ScreenHeader("Limpeza", "Proteja antes de liberar espaço.") }
        val start = state.sessionStartUsed
        val stNow = state.storage
        if (start != null && stNow != null) {
            item { SessionCard(start, stNow, state.sessionFreed, state.avgPhotoBytes) }
        }
        if (!state.scanned) {
            item {
                CsCard {
                    Column {
                        Text(
                            if (state.scanning) state.scanStep else "Ainda não analisamos o seu celular.",
                            color = CS.Ink, fontSize = 16.sp
                        )
                        Spacer(Modifier.height(14.dp))
                        PrimaryButton(if (state.scanning) "Analisando…" else "Analisar agora", onScan, enabled = !state.scanning)
                    }
                }
            }
        } else {
            item { SectionTitle("Galeria") }
            item {
                RowCard(
                    "🖼️", "Duplicadas",
                    if (state.duplicateCount > 0) "${formatCount(state.duplicateCount)} cópias · ${formatBytes(state.duplicateBytes)}" else "Nenhuma cópia encontrada",
                    onClick = { onOpen("dups") }
                )
            }
            item {
                RowCard(
                    "📷", "Fotos em sequência",
                    if (state.sequences.isNotEmpty()) "${state.sequences.size} grupos · ${formatCount(state.sequenceCount)} fotos parecidas" else "Nenhuma sequência encontrada",
                    onClick = { onOpen("seq") }
                )
            }
            item {
                RowCard(
                    "📱", "Capturas de tela",
                    if (state.screenshots.isNotEmpty()) "${formatCount(state.screenshots.size)} prints · ${formatBytes(state.screenshotBytes)}" else "Nenhuma captura de tela",
                    onClick = { onOpen("shots") }
                )
            }
            item {
                RowCard(
                    "🎥", "Vídeos grandes",
                    if (state.largeVideos.isNotEmpty()) "${state.largeVideos.size} vídeos · ${formatBytes(state.largeVideoBytes)}" else "Nenhum vídeo acima de 100 MB",
                    onClick = { onOpen("videos") }
                )
            }
            item {
                RowCard(
                    "🗑️", "Lixeira",
                    when {
                        !canRemove -> "Disponível no Android 11 ou mais novo"
                        state.trash.isEmpty() -> "Vazia"
                        else -> "${formatCount(state.trash.size)} itens · ${formatBytes(state.trashBytes)}"
                    },
                    onClick = { onOpen("trash") }
                )
            }
            item {
                RowCard("♻️", "Recuperar arquivos", "Desfaça o que foi apagado pelo app", onClick = { onOpen("recover") })
            }

            if (state.foldersFeature) item { SectionTitle("Pastas: WhatsApp, Downloads e temporários") }
            if (!state.foldersFeature) {
                // Versão da Play: a leitura de pastas ocultas não está disponível.
            } else if (!state.folderAccess) {
                item {
                    Notice(
                        "O WhatsApp, os Downloads e os temporários ficam em pastas que a permissão de fotos não mostra, algumas ocultas. " +
                            "Para analisar e limpar, libere o \"Acesso a todos os arquivos\".",
                        action = "Liberar acesso às pastas",
                        onAction = onRequestFolders
                    )
                }
            } else {
                item {
                    RowCard(
                        "📂", "Pastas e arquivos ocultos",
                        if (state.folders.isEmpty()) "Nada encontrado" else
                            "${formatBytes(state.folders.sumOf { it.bytes })} · ${formatBytes(state.safeFolderBytes)} podem ir sem medo",
                        onClick = { onOpen("folders") }
                    )
                }
                item {
                    RowCard(
                        "🎙️", "Arquivos por origem",
                        "Áudios, fotos, vídeos e documentos separados: WhatsApp, gravador, câmera, Downloads…",
                        onClick = { onOpen("origins") }
                    )
                }
            }

            item { SectionTitle("Aplicativos") }
            item {
                RowCard(
                    "🧽", "Cache dos aplicativos",
                    if (state.usageAccess) "${formatBytes(state.cacheBytes)} em ${state.appsWithCache} apps" else "Precisa do acesso de uso",
                    onClick = onOpenCache
                )
            }
            item {
                RowCard(
                    "💤", "Aplicativos pouco usados",
                    if (state.usageAccess) "${state.unusedApps.size} apps · ${formatBytes(state.unusedAppsBytes)}" else "Precisa do acesso de uso",
                    onClick = onOpenApps
                )
            }
        }
        item {
            CsCard(color = CS.GreenSoft) {
                Column {
                    Text("Princípio de segurança", fontWeight = FontWeight.Bold, color = CS.Green)
                    Spacer(Modifier.height(6.dp))
                    Text("PROTEGER → VERIFICAR → LIBERAR", color = CS.Green, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Fotos e vídeos sem backup vão para a lixeira, de onde podem voltar. Depois do backup verificado no Google Drive, a aba Backup libera o espaço com segurança.",
                        color = CS.Green, fontSize = 13.sp, lineHeight = 18.sp
                    )
                }
            }
        }
        if (!canRemove) {
            item { Notice("A remoção de fotos pelo app exige Android 11 ou mais novo. Aqui você pode revisar e apagar pela Galeria.") }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CS.Ink, modifier = Modifier.padding(top = 8.dp))
}

// ---------------- Duplicadas ----------------

@Composable
fun DuplicatesScreen(
    state: UiState,
    onBack: () -> Unit,
    makeRequest: (List<Uri>) -> IntentSender?,
    onRemoved: (List<MediaFile>) -> Unit,
    groups: List<br.com.celularsaudavel.model.DuplicateGroup> = state.duplicates,
    title: String = "Duplicadas",
    subtitle: String = "Arquivos com conteúdo idêntico. Em cada grupo pelo menos um fica guardado.",
    preselect: Boolean = true,
    toTrash: Boolean = false,
) {
    var selected by remember(groups) {
        mutableStateOf(if (preselect) groups.flatMap { g -> g.copies.map { it.uri } }.toSet() else emptySet())
    }
    var hint by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    val remove = rememberBatchRemover(onRemoved)

    val chosen = groups.flatMap { it.files }.filter { it.uri in selected }
    val chosenBytes = chosen.sumOf { it.sizeBytes }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = ListSpacing
        ) {
            item {
                ScreenHeader(title, subtitle, onBack)
            }
            if (groups.isEmpty()) {
                item { Notice("Nenhuma cópia encontrada. 👏", soft = false) }
            }
            if (groups.isNotEmpty()) {
                item {
                    val allCopies = groups.flatMap { g -> g.copies.map { it.uri } }.toSet()
                    val all = chosen.size == allCopies.size && allCopies.all { it in selected }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill(if (all) "Desmarcar tudo" else "Selecionar tudo (fica 1 por grupo)", all) {
                                selected = if (all) emptySet() else allCopies
                                hint = null
                            }
                        }
                        Text(
                            if (chosen.isEmpty()) "Toque nas fotos para escolher uma por uma." else "${formatCount(chosen.size)} selecionadas · ${formatBytes(chosenBytes)}",
                            color = CS.Muted, fontSize = 13.sp
                        )
                    }
                }
            }
            hint?.let { item { Notice(it) } }
            items(groups, key = { it.original.uri.toString() }) { g ->
                CsCard {
                    Column {
                        Text(
                            if (toTrash) "${g.files.size} fotos · ${formatBytes(g.files.sumOf { it.sizeBytes })}" else "${g.files.size} iguais · ${formatBytes(g.original.sizeBytes)} cada",
                            fontWeight = FontWeight.SemiBold, color = CS.Ink
                        )
                        Text(g.original.name, color = CS.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            g.files.forEachIndexed { index, f ->
                                val isSel = f.uri in selected
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box {
                                        MediaThumb(
                                            f.uri,
                                            Modifier
                                                .size(96.dp)
                                                .clickable {
                                                    if (isSel) {
                                                        selected = selected - f.uri
                                                        hint = null
                                                    } else {
                                                        val others = g.files.filter { it.uri != f.uri }
                                                        if (others.all { it.uri in selected }) {
                                                            hint = "Pelo menos um arquivo de cada grupo precisa ficar."
                                                        } else {
                                                            selected = selected + f.uri
                                                            hint = null
                                                        }
                                                    }
                                                }
                                        )
                                        if (isSel) {
                                            Box(
                                                Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(6.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(CS.Ink)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) { Text("apagar", color = androidx.compose.ui.graphics.Color.White, fontSize = 11.sp) }
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        if (toTrash) formatBytes(f.sizeBytes) else if (index == 0) "mais antigo" else formatDate(f.dateModifiedSec * 1000),
                                        fontSize = 11.sp, color = CS.Muted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (groups.isNotEmpty()) {
            Column(Modifier.background(CS.Surface).padding(20.dp)) {
                Text("Toque nas fotos para escolher o que apagar.", color = CS.Muted, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                PrimaryButton(
                    (if (toTrash) "Mover ${chosen.size} para a lixeira · " else "Apagar ${chosen.size} cópias · ") + formatBytes(chosenBytes),
                    onClick = { confirm = true },
                    enabled = chosen.isNotEmpty() && canRemove
                )
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(if (toTrash) "Mover para a lixeira?" else "Confirme a liberação") },
            text = {
                Text(
                    if (toTrash) "${chosen.size} fotos (${formatBytes(chosenBytes)}) vão para a lixeira. Em cada grupo pelo menos uma fica. Dá para restaurar pela Lixeira por uns 30 dias."
                    else "${chosen.size} cópias (${formatBytes(chosenBytes)}) serão apagadas deste celular. " +
                        "Em cada grupo, pelo menos um arquivo idêntico continua guardado. " +
                        "O Android vai pedir uma confirmação final."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    remove(chosen, makeRequest)
                }) { Text("Liberar espaço") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } }
        )
    }
}

// ---------------- Vídeos grandes ----------------

@Composable
fun LargeVideosScreen(
    state: UiState,
    onBack: () -> Unit,
    makeRequest: (List<Uri>) -> IntentSender?,
    onRemoved: (List<MediaFile>) -> Unit,
    videos: List<MediaFile> = state.largeVideos,
    title: String = "Vídeos grandes",
    subtitle: String = "Acima de 100 MB, do maior para o menor.",
    noun: String = "vídeos",
    onPreview: (PreviewTarget) -> Unit = {},
) {
    var selected by remember(videos) { mutableStateOf(emptySet<String>()) }
    var mode by remember { mutableStateOf(SelMode.ONE) }
    var confirm by remember { mutableStateOf(false) }
    val remove = rememberBatchRemover(onRemoved)

    val chosen = videos.filter { it.uri.toString() in selected }
    val chosenBytes = chosen.sumOf { it.sizeBytes }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { ScreenHeader(title, subtitle, onBack) }
            item {
                Notice("Por segurança, o que você escolher vai para a lixeira do sistema e pode ser recuperado por cerca de 30 dias.")
            }
            if (videos.isNotEmpty()) {
                item {
                    SelectionBar(
                        mode = mode, onMode = { mode = it },
                        allSelected = chosen.size == videos.size,
                        onToggleAll = {
                            selected = if (chosen.size == videos.size) emptySet() else videos.map { it.uri.toString() }.toSet()
                        },
                        selectedCount = chosen.size, selectedBytes = chosenBytes
                    )
                }
            }
            if (videos.isEmpty()) item { Notice("Nada encontrado aqui. 👏", soft = false) }
            selectableItems(
                items = videos, mode = mode,
                keyOf = { it.uri.toString() },
                dateMsOf = { if (it.dateTakenMs > 0) it.dateTakenMs else it.dateModifiedSec * 1000 },
                sizeOf = { it.sizeBytes },
                selected = selected, onSelectedChange = { selected = it }
            ) { v ->
                val k = v.uri.toString()
                val isSel = k in selected
                CsCard(onClick = { selected = if (isSel) selected - k else selected + k }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MediaThumb(
                            v.uri,
                            Modifier
                                .size(64.dp)
                                .clickable {
                                    onPreview(PreviewTarget(v.name, v.sizeBytes, v.dateModifiedSec * 1000, uri = v.uri, mime = v.mimeType, location = v.folder))
                                }
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(formatBytes(v.sizeBytes), fontWeight = FontWeight.SemiBold, color = CS.Ink, fontSize = 17.sp)
                            Text(v.name, color = CS.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(formatDate(if (v.dateTakenMs > 0) v.dateTakenMs else v.dateModifiedSec * 1000), color = CS.Muted, fontSize = 12.sp)
                        }
                        Checkbox(
                            checked = isSel,
                            onCheckedChange = { selected = if (it) selected + k else selected - k },
                            colors = CheckboxDefaults.colors(checkedColor = CS.Ink)
                        )
                    }
                }
            }
        }
        if (videos.isNotEmpty()) {
            Column(Modifier.background(CS.Surface).padding(20.dp)) {
                PrimaryButton(
                    if (chosen.isEmpty()) "Selecione os $noun" else "Mover ${chosen.size} para a lixeira · ${formatBytes(chosenBytes)}",
                    onClick = { confirm = true },
                    enabled = chosen.isNotEmpty() && canRemove
                )
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Mover para a lixeira?") },
            text = {
                Text(
                    "${chosen.size} $noun (${formatBytes(chosenBytes)}) vão para a lixeira do sistema. " +
                        "O espaço é liberado de vez quando você esvaziar a lixeira aqui no app " +
                        "ou automaticamente depois de uns 30 dias."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    remove(chosen, makeRequest)
                }) { Text("Mover para a lixeira") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } }
        )
    }
}

// ---------------- Lixeira ----------------

@Composable
fun TrashScreen(
    state: UiState,
    onBack: () -> Unit,
    onLoad: () -> Unit,
    deleteRequest: (List<Uri>) -> IntentSender?,
    restoreRequest: (List<Uri>) -> IntentSender?,
    onDeleted: (List<MediaFile>) -> Unit,
    onRestored: () -> Unit,
    onPreview: (PreviewTarget) -> Unit = {},
) {
    LaunchedEffect(Unit) { onLoad() }
    val items = state.trash
    var selected by remember(items) { mutableStateOf(items.map { it.uri.toString() }.toSet()) }
    var mode by remember { mutableStateOf(SelMode.ONE) }
    var confirm by remember { mutableStateOf(false) }
    val remove = rememberBatchRemover(onDeleted)
    val restore = rememberBatchRemover { onRestored() }

    val chosen = items.filter { it.uri.toString() in selected }
    val chosenBytes = chosen.sumOf { it.sizeBytes }
    val nowSec = System.currentTimeMillis() / 1000

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                ScreenHeader(
                    "Lixeira",
                    "O que está aqui ainda ocupa espaço. O Android apaga sozinho depois de uns 30 dias.",
                    onBack
                )
            }
            if (!canRemove) {
                item { Notice("A lixeira do sistema existe a partir do Android 11.") }
            } else if (items.isEmpty()) {
                item { Notice("A lixeira está vazia. 👏", soft = false) }
            } else {
                item {
                    Text(
                        "${formatCount(items.size)} itens · ${formatBytes(items.sumOf { it.sizeBytes })} na lixeira",
                        color = CS.Ink, fontWeight = FontWeight.SemiBold
                    )
                }
                item {
                    SelectionBar(
                        mode = mode, onMode = { mode = it },
                        allSelected = chosen.size == items.size,
                        onToggleAll = {
                            selected = if (chosen.size == items.size) emptySet() else items.map { it.uri.toString() }.toSet()
                        },
                        selectedCount = chosen.size, selectedBytes = chosenBytes
                    )
                }
            }
            selectableItems(
                items = items, mode = mode,
                keyOf = { it.uri.toString() },
                dateMsOf = { if (it.dateTakenMs > 0) it.dateTakenMs else it.dateModifiedSec * 1000 },
                sizeOf = { it.sizeBytes },
                selected = selected, onSelectedChange = { selected = it }
            ) { f ->
                val k = f.uri.toString()
                val isSel = k in selected
                CsCard(onClick = { selected = if (isSel) selected - k else selected + k }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MediaThumb(
                            f.uri,
                            Modifier
                                .size(56.dp)
                                .clickable {
                                    onPreview(PreviewTarget(f.name, f.sizeBytes, f.dateModifiedSec * 1000, uri = f.uri, mime = f.mimeType))
                                }
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(formatBytes(f.sizeBytes), fontWeight = FontWeight.SemiBold, color = CS.Ink)
                            Text(f.name, color = CS.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (f.expiresSec > nowSec) {
                                val days = ((f.expiresSec - nowSec) / 86_400).toInt()
                                Text(
                                    if (days <= 0) "Apaga sozinho hoje" else "Apaga sozinho em $days dias",
                                    color = CS.Muted, fontSize = 12.sp
                                )
                            }
                        }
                        Checkbox(
                            checked = isSel,
                            onCheckedChange = { selected = if (it) selected + k else selected - k },
                            colors = CheckboxDefaults.colors(checkedColor = CS.Ink)
                        )
                    }
                }
            }
        }
        if (items.isNotEmpty() && canRemove) {
            Column(Modifier.background(CS.Surface).padding(20.dp)) {
                PrimaryButton(
                    if (chosen.isEmpty()) "Selecione os itens" else "Apagar de vez ${chosen.size} · ${formatBytes(chosenBytes)}",
                    onClick = { confirm = true },
                    enabled = chosen.isNotEmpty()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        restore(chosen, restoreRequest)
                    },
                    enabled = chosen.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("Restaurar para a galeria", color = CS.Ink) }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Apagar de vez?") },
            text = {
                Text(
                    "${chosen.size} itens (${formatBytes(chosenBytes)}) serão apagados definitivamente e não poderão ser recuperados " +
                        "por este celular. Se algum deles for importante e não tiver backup, restaure antes."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    remove(chosen, deleteRequest)
                }) { Text("Apagar de vez") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } }
        )
    }
}


// ---------------- Confirmação em lotes ----------------

/** O Android aceita no máximo 2.000 arquivos por confirmação. Usamos lotes de 1.000, um depois do outro. */
private const val BATCH = 1000

private class BatchState {
    var queue by mutableStateOf<List<MediaFile>>(emptyList())
    var current by mutableStateOf<List<MediaFile>>(emptyList())
    var done by mutableStateOf<List<MediaFile>>(emptyList())
    var request: ((List<Uri>) -> IntentSender?)? = null
    var step by mutableIntStateOf(0)
}

/**
 * Devolve uma função que pede a confirmação do Android em lotes e, no fim,
 * chama [onDone] uma única vez com tudo o que foi confirmado. Nunca derruba o app.
 */
@Composable
private fun rememberBatchRemover(onDone: (List<MediaFile>) -> Unit): (List<MediaFile>, (List<Uri>) -> IntentSender?) -> Unit {
    val st = remember { BatchState() }
    val context = LocalContext.current
    val finish = {
        val d = st.done
        st.queue = emptyList(); st.current = emptyList(); st.done = emptyList()
        if (d.isNotEmpty()) onDone(d)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            st.done = st.done + st.current
            st.current = emptyList()
            if (st.queue.isNotEmpty()) st.step++ else finish()
        } else {
            finish()
        }
    }
    val launchNext = launchNext@{
        val req = st.request ?: return@launchNext
        val batch = st.queue.take(BATCH)
        st.queue = st.queue.drop(BATCH)
        st.current = batch
        val sender = try {
            req(batch.map { it.uri })
        } catch (e: Exception) {
            null
        }
        if (sender != null) {
            try {
                launcher.launch(IntentSenderRequest.Builder(sender).build())
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "O Android não abriu a confirmação. Tente com menos arquivos.", android.widget.Toast.LENGTH_LONG).show()
                finish()
            }
        } else {
            android.widget.Toast.makeText(context, "O Android não abriu a confirmação. Tente com menos arquivos.", android.widget.Toast.LENGTH_LONG).show()
            finish()
        }
    }
    LaunchedEffect(st.step) { if (st.step > 0) launchNext() }
    return { files, req ->
        if (files.isNotEmpty()) {
            st.request = req
            st.done = emptyList()
            st.queue = files
            if (files.size > BATCH) {
                val n = (files.size + BATCH - 1) / BATCH
                android.widget.Toast.makeText(context, "São ${formatCount(files.size)} arquivos: o Android vai pedir $n confirmações.", android.widget.Toast.LENGTH_LONG).show()
            }
            launchNext()
        }
    }
}
