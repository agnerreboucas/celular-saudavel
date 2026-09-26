package br.com.celularsaudavel.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.celularsaudavel.ui.*
import kotlinx.coroutines.launch

private data class Slide(val icon: String, val title: String, val text: String, val points: List<String>)

private val slides = listOf(
    Slide(
        "🌱",
        "Agora você vai conhecer como o seu celular vai ficar mais saudável",
        "Em poucos passos, mostramos o que o Celular Saudável faz por você e como tudo funciona com segurança.",
        emptyList()
    ),
    Slide(
        "🩺",
        "Seu celular chegou aqui porque ninguém cuidou dele",
        "Com o tempo, fotos repetidas, vídeos esquecidos e arquivos escondidos vão se acumulando. Agora vamos cuidar da saúde do seu celular, para que ele volte a cumprir bem as suas funções.",
        listOf("Índice de saúde de 0 a 100", "Sinais vitais: espaço, organização, proteção e apps", "Tudo calculado aqui no celular")
    ),
    Slide(
        "🔍",
        "Descubra o que ocupa espaço",
        "Encontramos fotos repetidas, sequências, prints, vídeos grandes, apps parados, cache e as pastas escondidas do WhatsApp.",
        listOf("Veja cada arquivo antes de apagar", "Selecione um por um, por dia ou tudo", "Antes e depois de cada limpeza")
    ),
    Slide(
        "☁️",
        "Proteja primeiro. Libere depois.",
        "Suas fotos e vídeos vão para o SEU Google Drive, cada arquivo é conferido e só então o espaço é liberado.",
        listOf("Backup verificado arquivo por arquivo", "Escolha em qual conta do Google guardar", "Histórico de tudo o que foi salvo")
    ),
    Slide(
        "♻️",
        "Nada some na hora",
        "O que o app apaga fica guardado por até 6 meses. Mudou de ideia? Recupere com um toque.",
        listOf("Lixeira do app e lixeira na nuvem", "Você escolhe: 1, 3 ou 6 meses", "Confirmação clara antes de apagar de vez")
    ),
    Slide(
        "🔔",
        "Acompanhamento sem sustos",
        "Receba o boletim da saúde do celular no dia e na hora que preferir e veja o índice num widget na tela inicial.",
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
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AppIcon(s.icon, size = 120.dp, corner = 32.dp)
        Spacer(Modifier.height(28.dp))
        Text(s.title, fontSize = 25.sp, fontWeight = FontWeight.Bold, color = CS.Ink, textAlign = TextAlign.Center, lineHeight = 31.sp)
        Spacer(Modifier.height(12.dp))
        Text(s.text, fontSize = 16.sp, color = CS.Muted, textAlign = TextAlign.Center, lineHeight = 23.sp)
        if (s.points.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                s.points.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BareIcon("✅", CS.Green, 18.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(p, color = CS.Ink, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun NamePage(name: String, onName: (String) -> Unit, onDone: () -> Unit) {
    // Alinhado ao topo e com rolagem: com o teclado aberto em telas pequenas nada fica cortado.
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIcon("👋", size = 88.dp, corner = 24.dp)
        Spacer(Modifier.height(20.dp))
        Text("Como podemos te chamar?", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = CS.Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Seu nome fica só neste celular e serve para o app te cumprimentar.",
            fontSize = 15.sp, color = CS.Muted, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { onName(it.take(40)) },
            placeholder = { Text("Digite seu nome", fontSize = 20.sp, color = CS.Muted) },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 20.sp, color = CS.Ink),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CS.Green,
                cursorColor = CS.Green,
                focusedContainerColor = CS.Surface,
                unfocusedContainerColor = CS.Surface,
            ),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
        )
        if (name.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(
                "${br.com.celularsaudavel.data.greetingFor(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY))}, ${name.trim().substringBefore(' ')}!",
                fontSize = 20.sp, color = CS.Green, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center
            )
        }
    }
}
