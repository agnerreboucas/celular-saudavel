package br.com.celularsaudavel.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.celularsaudavel.ui.*
import kotlinx.coroutines.launch

private data class Slide(val emoji: String, val tint: Color, val title: String, val text: String, val points: List<String>)

private val slides = listOf(
    Slide(
        "🩺", Status.Green,
        "Cuide do seu celular como cuida da saúde",
        "O Celular Saudável faz um check-up do aparelho e mostra, em cores, como ele está: saudável, cansado, sobrecarregado ou exausto.",
        listOf("Índice de saúde de 0 a 100", "Sinais vitais: espaço, organização, proteção e apps", "Tudo calculado aqui no celular")
    ),
    Slide(
        "🔍", Status.Yellow,
        "Descubra o que ocupa espaço",
        "Encontramos fotos repetidas, sequências, prints, vídeos grandes, apps parados, cache e as pastas escondidas do WhatsApp.",
        listOf("Veja cada arquivo antes de apagar", "Selecione um por um, por dia ou tudo", "Antes e depois de cada limpeza")
    ),
    Slide(
        "☁️", Color(0xFF3B6FB6),
        "Proteja primeiro. Libere depois.",
        "Suas fotos e vídeos vão para o SEU Google Drive, cada arquivo é conferido, e só então o espaço é liberado.",
        listOf("Backup verificado arquivo por arquivo", "Liberar só o que está protegido", "Fotos continuam acessíveis na nuvem")
    ),
    Slide(
        "♻️", Status.Orange,
        "Nada some na hora",
        "O que o app apaga fica guardado por até 6 meses. Mudou de ideia? Recupere com um toque.",
        listOf("Lixeira do app e lixeira na nuvem", "Você escolhe: 1, 3 ou 6 meses", "Confirmação clara antes de apagar de vez")
    ),
    Slide(
        "🔔", Color(0xFF9B6BC4),
        "Acompanhamento sem sustos",
        "Receba o boletim da saúde do celular no dia e hora que preferir, e veja o índice num widget na tela inicial.",
        listOf("Boletim diário, semanal ou mensal", "Aviso quando o espaço estiver acabando", "Sem alarmes falsos e sem som de madrugada")
    ),
)

@Composable
fun OnboardingScreen(initialName: String, onFinish: (String) -> Unit) {
    val total = slides.size + 1
    val pager = rememberPagerState(pageCount = { total })
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initialName) }

    Column(
        Modifier
            .fillMaxSize()
            .background(CS.Bg)
            .systemBarsPadding()
            .imePadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
            if (pager.currentPage < total - 1) {
                TextButton(onClick = { scope.launch { pager.animateScrollToPage(total - 1) } }) {
                    Text("Pular", color = CS.Muted)
                }
            } else {
                Spacer(Modifier.height(48.dp))
            }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            if (page < slides.size) SlidePage(slides[page]) else NamePage(name, onName = { name = it }, onDone = { onFinish(name) })
        }
        // Indicador de páginas
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
            repeat(total) { i ->
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (i == pager.currentPage) 22.dp else 8.dp, 8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (i == pager.currentPage) CS.Green else CS.Surface2)
                )
            }
        }
        Box(Modifier.padding(horizontal = 24.dp).padding(bottom = 20.dp)) {
            if (pager.currentPage < total - 1) {
                PrimaryButton("Próximo", onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } })
            } else {
                PrimaryButton(if (name.isBlank()) "Começar sem nome" else "Começar", onClick = { onFinish(name) })
            }
        }
    }
}

@Composable
private fun SlidePage(s: Slide) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(150.dp)
                .clip(CircleShape)
                .background(s.tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) { Text(s.emoji, fontSize = 72.sp) }
        Spacer(Modifier.height(28.dp))
        Text(s.title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = CS.Ink, textAlign = TextAlign.Center, lineHeight = 31.sp)
        Spacer(Modifier.height(12.dp))
        Text(s.text, fontSize = 16.sp, color = CS.Muted, textAlign = TextAlign.Center, lineHeight = 23.sp)
        Spacer(Modifier.height(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            s.points.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✓", color = s.tint, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(p, color = CS.Ink, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun NamePage(name: String, onName: (String) -> Unit, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(CS.GreenSoft),
            contentAlignment = Alignment.Center
        ) { Text("👋", fontSize = 60.sp) }
        Spacer(Modifier.height(24.dp))
        Text("Como podemos te chamar?", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = CS.Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Seu nome fica só neste celular e serve para o app te cumprimentar.",
            fontSize = 15.sp, color = CS.Muted, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { onName(it.take(40)) },
            label = { Text("Seu nome") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth()
        )
        if (name.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(
                "${br.com.celularsaudavel.data.greetingFor(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY))}, ${name.trim().substringBefore(' ')}! 😊",
                fontSize = 18.sp, color = CS.Green, fontWeight = FontWeight.SemiBold
            )
        }
    }
}
