package br.com.celularsaudavel.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.celularsaudavel.model.CategoryStat
import br.com.celularsaudavel.model.formatBytes
import br.com.celularsaudavel.model.formatCount
import br.com.celularsaudavel.ui.*

@Composable
fun BackupScreen(
    state: UiState,
    onScan: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onStart: (Set<String>, Boolean) -> Unit,
    onStop: () -> Unit,
    onRetry: (Boolean) -> Unit,
    onFree: () -> Unit,
    onRefresh: () -> Unit,
    onOpenPremium: () -> Unit,
) {
    val d = state.drive
    var sel by rememberSaveable { mutableStateOf(setOf(BackupCat.CAMERA)) }
    var wifiOnly by rememberSaveable { mutableStateOf(true) }
    var confirmFree by remember { mutableStateOf(false) }
    var confirmDisconnect by remember { mutableStateOf(false) }

    LaunchedEffect(d.connected) { if (d.connected && d.account == null) onRefresh() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = ListSpacing
    ) {
        item { ScreenHeader("Backup", "Proteja no seu Google Drive antes de liberar espaço.") }
        if (!state.premium) {
            item {
                PremiumLock(
                    "Backup verificado é Premium",
                    "Envie fotos e vídeos para o SEU Google Drive, confira cada arquivo e só então libere espaço no celular." +
                        (state.media?.let { " Você tem ${formatBytes(it.images.bytes + it.videos.bytes)} em fotos e vídeos para proteger." } ?: ""),
                    onOpenPremium
                )
            }
            return@LazyColumn
        }

        // ---------- Conexão ----------
        item {
            CsCard {
                Column {
                    Text("Google Drive", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CS.Ink)
                    Spacer(Modifier.height(6.dp))
                    val acc = d.account
                    when {
                        !d.connected -> {
                            Text(
                                "Conecte sua conta para guardar fotos e vídeos na pasta \"Celular Saudável\" do seu Drive. " +
                                    "O app só enxerga os arquivos que ele mesmo enviar.",
                                color = CS.Muted, fontSize = 14.sp, lineHeight = 20.sp
                            )
                            Spacer(Modifier.height(14.dp))
                            PrimaryButton(if (d.connecting) "Conectando…" else "Conectar Google Drive", onConnect, enabled = !d.connecting)
                        }
                        acc == null -> Text("Conectado. Carregando sua conta…", color = CS.Muted)
                        else -> {
                            Text(acc.email, color = CS.Ink, fontSize = 15.sp)
                            Spacer(Modifier.height(8.dp))
                            val limit = acc.limitBytes
                            if (limit != null && limit > 0) {
                                LinearProgressIndicator(
                                    progress = { (acc.usageBytes.toFloat() / limit).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(8.dp),
                                    color = CS.Green, trackColor = CS.Surface2
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "${formatBytes(acc.usageBytes)} usados de ${formatBytes(limit)} · ${formatBytes(acc.freeBytes ?: 0)} livres",
                                    color = CS.Muted, fontSize = 13.sp
                                )
                            } else {
                                Text("${formatBytes(acc.usageBytes)} usados · espaço ilimitado", color = CS.Muted, fontSize = 13.sp)
                            }
                            TextButton(onClick = { confirmDisconnect = true }) { Text("Desconectar", color = CS.Muted) }
                        }
                    }
                    d.error?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, color = CS.Amber, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }
            }
        }

        if (!state.scanned) {
            item {
                CsCard {
                    Column {
                        Text("Faça a análise para ver o que precisa de backup.", color = CS.Muted, fontSize = 14.sp)
                        Spacer(Modifier.height(12.dp))
                        PrimaryButton(if (state.scanning) "Analisando…" else "Analisar agora", onScan, enabled = !state.scanning)
                    }
                }
            }
        }

        // ---------- Em andamento ----------
        if (d.running) {
            item {
                CsCard {
                    Column {
                        Text("Backup em andamento", fontWeight = FontWeight.Bold, color = CS.Ink, fontSize = 17.sp)
                        Spacer(Modifier.height(8.dp))
                        val frac = if (d.total > 0) d.done.toFloat() / d.total else 0f
                        LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth().height(8.dp), color = CS.Green, trackColor = CS.Surface2)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (d.total > 0) "${formatCount(d.done)} de ${formatCount(d.total)} arquivos · ${(frac * 100).toInt()}%" else "Preparando…",
                            color = CS.Muted, fontSize = 13.sp
                        )
                        Text(
                            "Pode fechar o app: o envio continua com uma notificação. Se a internet cair, ele volta de onde parou.",
                            color = CS.Muted, fontSize = 12.sp, lineHeight = 17.sp
                        )
                        OutlinedButton(onClick = onStop, shape = RoundedCornerShape(14.dp)) { Text("Pausar", color = CS.Ink) }
                    }
                }
            }
        }

        // ---------- Seleção ----------
        if (d.connected && state.scanned && !d.running) {
            item {
                CsCard {
                    Column {
                        Text("O que proteger", fontWeight = FontWeight.Bold, color = CS.Ink, fontSize = 17.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("Mostramos só o que ainda não tem backup verificado.", color = CS.Muted, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        SelectRow("Fotos da câmera", d.toProtect[BackupCat.CAMERA], BackupCat.CAMERA in sel) {
                            sel = if (it) sel + BackupCat.CAMERA else sel - BackupCat.CAMERA
                        }
                        SelectRow("Outras fotos (WhatsApp, prints…)", d.toProtect[BackupCat.PHOTOS], BackupCat.PHOTOS in sel) {
                            sel = if (it) sel + BackupCat.PHOTOS else sel - BackupCat.PHOTOS
                        }
                        SelectRow("Vídeos", d.toProtect[BackupCat.VIDEO], BackupCat.VIDEO in sel) {
                            sel = if (it) sel + BackupCat.VIDEO else sel - BackupCat.VIDEO
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = wifiOnly, onCheckedChange = { wifiOnly = it }, colors = CheckboxDefaults.colors(checkedColor = CS.Ink))
                            Text("Enviar só no Wi-Fi", color = CS.Ink, fontSize = 14.sp)
                        }
                        val selBytes = sel.sumOf { d.toProtect[it]?.bytes ?: 0L }
                        val selCount = sel.sumOf { d.toProtect[it]?.count ?: 0 }
                        val free = d.account?.freeBytes
                        Spacer(Modifier.height(6.dp))
                        Text("${formatCount(selCount)} arquivos · ${formatBytes(selBytes)} selecionados", color = CS.Ink, fontWeight = FontWeight.SemiBold)
                        if (free != null && selBytes > free) {
                            Spacer(Modifier.height(8.dp))
                            Notice(
                                "Não cabe tudo: seu Drive tem ${formatBytes(free)} livres. O backup vai parar quando encher. " +
                                    "Escolha menos grupos ou aumente o espaço no Google One."
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        PrimaryButton("Fazer backup", onClick = { onStart(sel, wifiOnly) }, enabled = selCount > 0)
                    }
                }
            }
        }

        // ---------- Verificação ----------
        val s = d.summary
        if (s.selected > 0) {
            item {
                CsCard {
                    Column {
                        Text("Verificação", fontWeight = FontWeight.Bold, color = CS.Ink, fontSize = 17.sp)
                        Spacer(Modifier.height(8.dp))
                        StatLine("Selecionados", formatCount(s.selected), formatBytes(s.selectedBytes))
                        StatLine("Verificados no Drive", formatCount(s.verified + s.freed), formatBytes(s.verifiedBytes + s.freedBytes))
                        StatLine("Pendentes", formatCount(s.pending), formatBytes(s.pendingBytes))
                        if (s.freed > 0) StatLine("Já liberados deste celular", formatCount(s.freed), formatBytes(s.freedBytes))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Verificado = o Drive devolveu o mesmo tamanho e a mesma impressão digital (MD5) do arquivo do celular.",
                            color = CS.Muted, fontSize = 12.sp, lineHeight = 17.sp
                        )
                        if (s.failed > 0 && !d.running) {
                            Spacer(Modifier.height(8.dp))
                            Notice(
                                "Não recomendamos liberar ${formatCount(s.failed)} arquivos ainda: o envio ou a conferência falhou.",
                                action = "Tentar novamente",
                                onAction = { onRetry(wifiOnly) }
                            )
                        }
                    }
                }
            }
        }

        // ---------- Liberar ----------
        if (s.verified > 0 && !d.running) {
            item {
                CsCard(color = CS.GreenSoft) {
                    Column {
                        Text("Você pode liberar ${formatBytes(s.verifiedBytes)}", fontWeight = FontWeight.Bold, color = CS.Green, fontSize = 18.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${formatCount(s.verifiedPhotos)} fotos e ${formatCount(s.verifiedVideos)} vídeos protegidos e verificados.",
                            color = CS.Green, fontSize = 14.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        PrimaryButton("Liberar espaço", onClick = { confirmFree = true })
                    }
                }
            }
        }
    }

    if (confirmFree) {
        AlertDialog(
            onDismissRequest = { confirmFree = false },
            title = { Text("Confirme a liberação") },
            text = {
                Text(
                    "Os arquivos verificados serão removidos deste celular (até 1.000 por vez). " +
                        "O backup de cada um foi concluído e conferido no seu Google Drive, na pasta \"Celular Saudável\"."
                )
            },
            confirmButton = { TextButton(onClick = { confirmFree = false; onFree() }) { Text("Liberar espaço") } },
            dismissButton = { TextButton(onClick = { confirmFree = false }) { Text("Cancelar") } }
        )
    }
    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("Desconectar o Google Drive?") },
            text = { Text("Os arquivos que já estão no Drive continuam lá. O backup em andamento para.") },
            confirmButton = { TextButton(onClick = { confirmDisconnect = false; onDisconnect() }) { Text("Desconectar") } },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun SelectRow(label: String, stat: CategoryStat?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange, colors = CheckboxDefaults.colors(checkedColor = CS.Ink))
        Column(Modifier.weight(1f)) {
            Text(label, color = CS.Ink, fontSize = 15.sp)
            Text(
                if (stat == null || stat.count == 0) "Tudo protegido" else "${formatCount(stat.count)} · ${formatBytes(stat.bytes)}",
                color = CS.Muted, fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun StatLine(label: String, count: String, size: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(label, color = CS.Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text("$count · $size", color = CS.Muted, fontSize = 14.sp)
    }
}
