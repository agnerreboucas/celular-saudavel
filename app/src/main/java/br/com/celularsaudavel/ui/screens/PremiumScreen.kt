package br.com.celularsaudavel.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.celularsaudavel.data.BillingRepository
import br.com.celularsaudavel.data.PlanOffer
import br.com.celularsaudavel.ui.*

@Composable
fun PremiumScreen(
    state: UiState,
    onBack: () -> Unit,
    onBuy: (PlanOffer) -> Unit,
    onRestore: () -> Unit,
    onManage: () -> Unit,
) {
    val b = state.billing
    var chosen by remember(b.plans) {
        mutableStateOf(b.plans.firstOrNull { it.basePlanId == BillingRepository.PLAN_YEARLY } ?: b.plans.firstOrNull())
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = ListSpacing
    ) {
        item { ScreenHeader("Celular Saudável Premium", "Proteja, verifique e acompanhe o seu celular o ano todo.", onBack) }

        if (state.premium) {
            item {
                CsCard(color = CS.GreenSoft) {
                    Column {
                        Text("Você é Premium 💚", fontWeight = FontWeight.Bold, color = CS.Green, fontSize = 20.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Vale em todos os celulares Android com a mesma conta Google. Para cancelar ou trocar de plano, use a Google Play.",
                            color = CS.Green, fontSize = 14.sp, lineHeight = 20.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = onManage) { Text("Gerenciar assinatura", color = CS.Ink) }
                    }
                }
            }
        }

        item {
            CsCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        "☁️" to "Backup no seu Google Drive com verificação de cada arquivo",
                        "🧹" to "Liberar espaço só do que já está protegido",
                        "🌅" to "Boletim da saúde no dia e na hora que você escolher",
                        "🔔" to "Check-up semanal com o índice de saúde",
                        "⏰" to "Lembrete quando o backup ficar antigo",
                        "📱" to "Use em todos os seus celulares com a mesma conta Google",
                    ).forEach { (e, t) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(e, size = 36.dp, corner = 10.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(t, color = CS.Ink, fontSize = 15.sp, lineHeight = 20.sp)
                        }
                    }
                    Text(
                        "Continua grátis: análise, índice, duplicadas, vídeos grandes, prints, lixeira, apps, cache e avisos de espaço.",
                        color = CS.Muted, fontSize = 13.sp, lineHeight = 18.sp
                    )
                }
            }
        }

        if (!state.premium) {
            if (b.plans.isEmpty()) {
                item {
                    Notice(
                        b.message ?: "Carregando os planos da Google Play… Se não aparecer, confira se o app foi instalado pela Play Store.",
                        action = "Tentar de novo",
                        onAction = onRestore
                    )
                }
            } else {
                b.plans.forEach { plan ->
                    item {
                        val selected = chosen?.basePlanId == plan.basePlanId
                        val yearly = plan.basePlanId == BillingRepository.PLAN_YEARLY
                        CsCard(
                            modifier = if (selected) Modifier.border(2.dp, CS.Green, RoundedCornerShape(24.dp)) else Modifier,
                            onClick = { chosen = plan }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selected, onClick = { chosen = plan }, colors = RadioButtonDefaults.colors(selectedColor = CS.Green))
                                Column(Modifier.weight(1f)) {
                                    Text(if (yearly) "Anual" else "Mensal", fontWeight = FontWeight.Bold, color = CS.Ink, fontSize = 17.sp)
                                    Text(
                                        plan.price + if (yearly) " por ano" else " por mês",
                                        color = CS.Ink, fontSize = 15.sp
                                    )
                                    plan.freeTrialDays?.let {
                                        Text("$it dias grátis para testar", color = CS.Green, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                if (yearly) {
                                    Text("Melhor valor", color = CS.Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                item {
                    val plan = chosen
                    PrimaryButton(
                        when {
                            plan == null -> "Escolha um plano"
                            plan.freeTrialDays != null -> "Começar ${plan.freeTrialDays} dias grátis"
                            else -> "Assinar"
                        },
                        onClick = { plan?.let(onBuy) },
                        enabled = plan != null
                    )
                }
                b.message?.let { msg -> item { Notice(msg) } }
            }
            item {
                TextButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
                    Text("Já assinei: restaurar", color = CS.Muted)
                }
            }
        }
        item {
            Text(
                "A cobrança é feita pela Google Play na sua conta. A assinatura renova sozinha até você cancelar, " +
                    "o que pode ser feito a qualquer momento em Play Store → Pagamentos e assinaturas. " +
                    "Desinstalar o app não cancela a assinatura.",
                color = CS.Muted, fontSize = 12.sp, lineHeight = 17.sp
            )
        }
    }
}

/** Cartão mostrado no lugar de um recurso Premium. */
@Composable
fun PremiumLock(title: String, text: String, onOpenPremium: () -> Unit) {
    CsCard(color = CS.GreenSoft) {
        Column {
            Text("⭐ $title", fontWeight = FontWeight.Bold, color = CS.Green, fontSize = 17.sp)
            Spacer(Modifier.height(6.dp))
            Text(text, color = CS.Green, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Conhecer o Premium", onOpenPremium)
        }
    }
}
