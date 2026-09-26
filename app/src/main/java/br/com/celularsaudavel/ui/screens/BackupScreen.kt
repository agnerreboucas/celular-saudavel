package br.com.celularsaudavel.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.celularsaudavel.model.formatBytes
import br.com.celularsaudavel.model.formatCount
import br.com.celularsaudavel.ui.*

@Composable
fun BackupScreen(state: UiState, onScan: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = ListSpacing
    ) {
        item { ScreenHeader("Backup", "Proteja seus arquivos antes da limpeza.") }

        item {
            CsCard {
                Column {
                    Text("Para proteger", fontWeight = FontWeight.Bold, color = CS.Ink)
                    Spacer(Modifier.height(8.dp))
                    val m = state.media
                    if (m == null) {
                        Text("Faça a análise para ver quanto há para proteger.", color = CS.Muted, fontSize = 14.sp)
                        Spacer(Modifier.height(12.dp))
                        PrimaryButton(if (state.scanning) "Analisando…" else "Analisar agora", onScan, enabled = !state.scanning)
                    } else {
                        Text(
                            formatBytes(m.images.bytes + m.videos.bytes),
                            fontSize = 34.sp, fontWeight = FontWeight.Bold, color = CS.Ink
                        )
                        Spacer(Modifier.height(8.dp))
                        LegendDot(ColorPhotos, "${formatCount(m.images.count)} fotos", formatBytes(m.images.bytes))
                        LegendDot(ColorVideos, "${formatCount(m.videos.count)} vídeos", formatBytes(m.videos.bytes))
                    }
                }
            }
        }

        item {
            CsCard {
                Column {
                    Text("Google Drive", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CS.Ink)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Em preparação nesta versão de teste. A conexão exige um cadastro no Google Cloud " +
                            "(tela de consentimento e credencial do app). Quando estiver pronta, esta tela envia os arquivos " +
                            "que você escolher, confere cada um e só então oferece liberar espaço.",
                        color = CS.Muted, fontSize = 14.sp, lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton("Conectar Google Drive (em breve)", onClick = {}, enabled = false)
                }
            }
        }

        item {
            CsCard(color = CS.GreenSoft) {
                Column {
                    Text("Como vai funcionar", fontWeight = FontWeight.Bold, color = CS.Green)
                    Spacer(Modifier.height(8.dp))
                    listOf(
                        "1. Você escolhe o que proteger",
                        "2. Os arquivos vão para a pasta \"Celular Saudável\" no seu Drive",
                        "3. Cada arquivo é verificado",
                        "4. Só os verificados podem ser liberados deste celular"
                    ).forEach { Text(it, color = CS.Green, fontSize = 14.sp, lineHeight = 22.sp) }
                }
            }
        }
    }
}
