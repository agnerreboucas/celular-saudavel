package br.com.celularsaudavel.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.celularsaudavel.model.formatBytes
import br.com.celularsaudavel.model.formatCount
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Como a lista é mostrada para seleção. */
enum class SelMode { ONE, DAY }

private val ptBR = Locale("pt", "BR")
private val dayFormat = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", ptBR)

fun dayOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

fun dayLabel(d: LocalDate): String {
    val today = LocalDate.now()
    return when (d) {
        today -> "Hoje"
        today.minusDays(1) -> "Ontem"
        else -> d.format(dayFormat).replaceFirstChar { it.uppercase() }
    }
}

/**
 * Barra com os três jeitos de selecionar: uma por uma, por dia e tudo.
 */
@Composable
fun SelectionBar(
    mode: SelMode,
    onMode: (SelMode) -> Unit,
    allSelected: Boolean,
    onToggleAll: () -> Unit,
    selectedCount: Int,
    selectedBytes: Long,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Pill("Uma por uma", mode == SelMode.ONE) { onMode(SelMode.ONE) }
            Pill("Por dia", mode == SelMode.DAY) { onMode(SelMode.DAY) }
            Pill(if (allSelected) "Desmarcar tudo" else "Selecionar tudo", allSelected) { onToggleAll() }
        }
        Text(
            if (selectedCount == 0) "Nada selecionado" else "${formatCount(selectedCount)} selecionados · ${formatBytes(selectedBytes)}",
            color = CS.Muted, fontSize = 13.sp
        )
    }
}

/**
 * Mostra os itens em lista simples ou agrupados por dia, com "Selecionar dia" em cada grupo.
 */
fun <T> LazyListScope.selectableItems(
    items: List<T>,
    mode: SelMode,
    keyOf: (T) -> String,
    dateMsOf: (T) -> Long,
    sizeOf: (T) -> Long,
    selected: Set<String>,
    onSelectedChange: (Set<String>) -> Unit,
    row: @Composable (T) -> Unit,
) {
    if (mode == SelMode.ONE) {
        items(items, key = keyOf) { row(it) }
        return
    }
    val groups = items.groupBy { dayOf(dateMsOf(it)) }.toSortedMap(compareByDescending { it })
    groups.forEach { (day, list) ->
        val keys = list.map(keyOf)
        val allSel = keys.all { it in selected }
        item(key = "day-$day") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(dayLabel(day), fontWeight = FontWeight.Bold, color = CS.Ink, fontSize = 15.sp)
                    Text("${list.size} itens · ${formatBytes(list.sumOf(sizeOf))}", color = CS.Muted, fontSize = 12.sp)
                }
                TextButton(onClick = {
                    onSelectedChange(if (allSel) selected - keys.toSet() else selected + keys)
                }) { Text(if (allSel) "Desmarcar dia" else "Selecionar dia", color = CS.Ink) }
            }
        }
        items(list, key = keyOf) { row(it) }
    }
}
