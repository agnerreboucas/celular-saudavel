package br.com.celularsaudavel.ui.screens

import androidx.compose.foundation.layout.*
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
                        "✓ Esta versão não usa internet."
                    ).forEach { Text(it, color = CS.Ink, fontSize = 15.sp, lineHeight = 21.sp) }
                }
            }
        }
    }
}
