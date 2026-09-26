package br.com.celularsaudavel.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.celularsaudavel.model.HistoryType
import br.com.celularsaudavel.model.MediaAccess
import br.com.celularsaudavel.model.formatBytes
import br.com.celularsaudavel.model.formatDate
import br.com.celularsaudavel.ui.*

@Composable
fun MoreScreen(
    state: UiState,
    versionName: String,
    onOpenHistory: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenMonitor: () -> Unit,
    onOpenPremium: () -> Unit,
    onOpenRecover: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = ListSpacing
    ) {
        item { ScreenHeader("Mais") }
        item {
            RowCard(
                "📊", "Histórico",
                if (state.history.isEmpty()) "Nenhuma ação ainda" else "${formatBytes(state.totalFreed)} liberados até agora",
                onClick = onOpenHistory
            )
        }
        if (!br.com.celularsaudavel.BuildConfig.PREMIUM_FREE) {
            item {
                RowCard(
                    "⭐", "Premium",
                    if (state.premium) "Ativo · obrigado!" else "Backup verificado e check-up semanal",
                    onClick = onOpenPremium
                )
            }
        }
        item {
            RowCard(
                "🔔", "Acompanhamento",
                if (state.monitor.enabled) "Ligado · avisos de espaço e check-up semanal" else "Desligado",
                onClick = onOpenMonitor
            )
        }
        item { RowCard("♻️", "Recuperar arquivos", "Volte atrás no que foi apagado pelo app", onClick = onOpenRecover) }
        item { RowCard("🔐", "Permissões", "Veja e ajuste o que o app pode acessar", onClick = onOpenPermissions) }
        item { RowCard("🛡️", "Privacidade", "O que o app faz (e não faz) com seus dados", onClick = onOpenPrivacy) }
        item {
            Text(
                "Celular Saudável · versão de teste $versionName",
                color = CS.Muted, fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun HistoryScreen(state: UiState, onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { ScreenHeader("Histórico", null, onBack) }
        item {
            CsCard {
                Column {
                    Text("Espaço liberado", color = CS.Muted, fontSize = 14.sp)
                    Text(formatBytes(state.totalFreed), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = CS.Ink)
                    val trashed = state.history.filter { it.type == HistoryType.VIDEOS_TRASHED }.sumOf { it.bytes }
                    if (trashed > 0) {
                        Text("+ ${formatBytes(trashed)} na lixeira do sistema", color = CS.Muted, fontSize = 13.sp)
                    }
                }
            }
        }
        if (state.history.isEmpty()) {
            item { Text("Quando você liberar espaço, o registro aparece aqui.", color = CS.Muted) }
        }
        items(state.history) { h ->
            CsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(h.type.label, fontWeight = FontWeight.SemiBold, color = CS.Ink)
                        Text(
                            buildString {
                                append(formatDate(h.timestamp))
                                if (h.detail.isNotBlank()) append(" · ").append(h.detail)
                                else append(" · ${h.count} arquivo").append(if (h.count == 1) "" else "s")
                            },
                            color = CS.Muted, fontSize = 13.sp
                        )
                    }
                    Text(formatBytes(h.bytes), fontWeight = FontWeight.SemiBold, color = CS.Ink)
                }
            }
        }
    }
}

@Composable
fun PermissionsScreen(
    state: UiState,
    onBack: () -> Unit,
    onRequestMedia: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenUsageSettings: () -> Unit,
    onRequestFolders: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = ListSpacing
    ) {
        item { ScreenHeader("Permissões", "Pedimos só o necessário, e só quando precisa.", onBack) }
        item {
            PermissionCard(
                "Fotos, vídeos e áudios",
                "Para medir o espaço e encontrar duplicadas e vídeos grandes. A análise acontece no próprio celular.",
                when (state.mediaAccess) {
                    MediaAccess.FULL -> "Liberado"
                    MediaAccess.PARTIAL -> "Só algumas fotos"
                    MediaAccess.NONE -> "Não liberado"
                },
                ok = state.mediaAccess == MediaAccess.FULL,
                actions = listOf("Pedir acesso" to onRequestMedia, "Abrir ajustes do app" to onOpenAppSettings)
            )
        }
        if (state.foldersFeature) item {
            PermissionCard(
                "Acesso a todos os arquivos",
                "Para analisar pastas do WhatsApp (inclusive as ocultas), Telegram, Downloads e temporários.",
                if (state.folderAccess) "Liberado" else "Não liberado",
                ok = state.folderAccess,
                actions = listOf("Liberar" to onRequestFolders)
            )
        }
        item {
            PermissionCard(
                "Acesso ao uso",
                "Para saber o tamanho real de cada app e quando ele foi usado pela última vez.",
                if (state.usageAccess) "Liberado" else "Não liberado",
                ok = state.usageAccess,
                actions = listOf("Abrir ajuste" to onOpenUsageSettings)
            )
        }
        item {
            PermissionCard(
                "Desinstalar apps",
                "O Android mostra a própria janela de confirmação a cada app removido.",
                "Pedido a cada remoção",
                ok = true,
                actions = emptyList()
            )
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    why: String,
    status: String,
    ok: Boolean,
    actions: List<Pair<String, () -> Unit>>
) {
    CsCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.SemiBold, color = CS.Ink, modifier = Modifier.weight(1f))
                Text(status, color = if (ok) CS.Green else CS.Amber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(4.dp))
            Text(why, color = CS.Muted, fontSize = 14.sp, lineHeight = 20.sp)
            if (actions.isNotEmpty() && !ok) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    actions.forEach { (label, action) ->
                        OutlinedButton(onClick = action) { Text(label, color = CS.Ink, fontSize = 13.sp) }
                    }
                }
            }
        }
    }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = ListSpacing
    ) {
        item { ScreenHeader("Privacidade", "Se não precisamos dos dados, não coletamos.", onBack) }
        item {
            CsCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        "✓ Toda a análise acontece no seu celular.",
                        "✓ Nenhum arquivo é enviado sem você pedir.",
                        "✓ Nada é apagado sem a sua confirmação e a do Android.",
                        "✓ Não vendemos nem compartilhamos dados.",
                        "✓ O histórico fica só neste aparelho.",
                        "✓ A internet só é usada para o backup no SEU Google Drive, quando você pede.",
                        "✓ O app só enxerga no Drive os arquivos que ele mesmo enviou."
                    ).forEach { Text(it, color = CS.Ink, fontSize = 15.sp, lineHeight = 21.sp) }
                }
            }
        }
    }
}

@Composable
fun MonitorScreen(
    state: UiState,
    onBack: () -> Unit,
    onSet: (enabled: Boolean?, storage: Boolean?, weekly: Boolean?, backup: Boolean?) -> Unit,
    onAllowNotifications: () -> Unit,
    onTest: () -> Unit,
    onOpenPremium: () -> Unit,
    onSetDaily: (enabled: Boolean?, hour: Int?) -> Unit,
    onTestDaily: () -> Unit,
    onSetBulletin: (freq: String?, weekDay: Int?, monthDay: Int?, hour: Int?, minute: Int?) -> Unit,
) {
    val m = state.monitor
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = ListSpacing
    ) {
        item {
            ScreenHeader(
                "Acompanhamento",
                "O app acompanha a saúde do celular em segundo plano e avisa só quando importa.",
                onBack
            )
        }
        if (!m.canNotify) {
            item {
                Notice(
                    "As notificações estão bloqueadas para o Celular Saudável. Sem elas, os avisos não aparecem.",
                    action = "Permitir notificações",
                    onAction = onAllowNotifications
                )
            }
        }
        item {
            CsCard {
                Column {
                    SwitchRow("Acompanhamento ligado", "Checa o celular duas vezes por dia, sem gastar bateria.", m.enabled) {
                        onSet(it, null, null, null)
                    }
                    if (m.enabled) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = CS.Surface2)
                        SwitchRow("Avisos de espaço", "Quando passar de 80%, 90% e 95% ocupado. Um aviso por faixa.", m.storageAlerts) {
                            onSet(null, it, null, null)
                        }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = CS.Surface2)
                        if (state.premium) {
                            val hhmm = "%02d:%02d".format(m.dailyHour, m.dailyMinute)
                            val weekNames = listOf("Domingo", "Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado")
                            val whenText = when (m.freq) {
                                "W" -> "Toda ${weekNames[(m.weekDay - 1).coerceIn(0, 6)].lowercase()} às $hhmm"
                                "M" -> "Todo dia ${m.monthDay} às $hhmm"
                                else -> "Todo dia às $hhmm"
                            }
                            SwitchRow(
                                "Boletim da saúde do celular",
                                "$whenText. Aparece na tela de bloqueio, sem som nem vibração.",
                                m.dailyEnabled
                            ) { onSetDaily(it, null) }
                            if (m.dailyEnabled) {
                                val context = androidx.compose.ui.platform.LocalContext.current
                                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Frequência", color = CS.Muted, fontSize = 13.sp)
                                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Pill("Diário", m.freq == "D") { onSetBulletin("D", null, null, null, null) }
                                        Pill("Semanal", m.freq == "W") { onSetBulletin("W", null, null, null, null) }
                                        Pill("Mensal", m.freq == "M") { onSetBulletin("M", null, null, null, null) }
                                    }
                                    if (m.freq == "W") {
                                        Text("Dia da semana", color = CS.Muted, fontSize = 13.sp)
                                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            // Calendar: domingo = 1 ... sábado = 7; mostra de segunda a domingo
                                            listOf(2, 3, 4, 5, 6, 7, 1).forEach { d ->
                                                Pill(weekNames[d - 1].take(3), m.weekDay == d) { onSetBulletin(null, d, null, null, null) }
                                            }
                                        }
                                    }
                                    if (m.freq == "M") {
                                        Text("Dia do mês", color = CS.Muted, fontSize = 13.sp)
                                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            (1..28).forEach { d ->
                                                Pill("$d", m.monthDay == d) { onSetBulletin(null, null, d, null, null) }
                                            }
                                        }
                                    }
                                    Text("Horário", color = CS.Muted, fontSize = 13.sp)
                                    OutlinedButton(onClick = {
                                        android.app.TimePickerDialog(
                                            context,
                                            { _, h, min -> onSetBulletin(null, null, null, h, min) },
                                            m.dailyHour, m.dailyMinute, true
                                        ).show()
                                    }) { Text("🕔 $hhmm · alterar", color = CS.Ink) }
                                    TextButton(onClick = onTestDaily) { Text("Ver como chega o boletim", color = CS.Ink) }
                                }
                            }
                            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = CS.Surface2)
                            SwitchRow("Check-up semanal", "Índice de saúde, espaço livre e quanto o celular encheu na semana.", m.weeklyCheckup) {
                                onSet(null, null, it, null)
                            }
                            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = CS.Surface2)
                            SwitchRow("Lembrete de backup", "Se o último backup no Drive tiver mais de 30 dias.", m.backupReminder) {
                                onSet(null, null, null, it)
                            }
                        } else {
                            Text(
                                "⭐ Boletim da saúde (diário, semanal ou mensal), check-up semanal e lembrete de backup fazem parte do Premium.",
                                color = CS.Green, fontSize = 14.sp
                            )
                            TextButton(onClick = onOpenPremium) { Text("Conhecer o Premium", color = CS.Ink) }
                        }
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = onTest, modifier = Modifier.fillMaxWidth()) {
                Text("Fazer um check-up agora e notificar", color = CS.Ink)
            }
        }
        item {
            Text(
                "Sem alarmes falsos: o app nunca diz que o celular está \"em perigo\" nem pede para limpar o que não precisa.",
                color = CS.Muted, fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun SwitchRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = CS.Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(desc, color = CS.Muted, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = CS.Green)
        )
    }
}
