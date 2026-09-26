package br.com.celularsaudavel.model

import android.net.Uri
import java.util.Locale
import kotlin.math.roundToInt

data class StorageInfo(val totalBytes: Long, val usedBytes: Long) {
    val freeBytes: Long get() = (totalBytes - usedBytes).coerceAtLeast(0)
    val usedFraction: Float get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f
}

data class CategoryStat(val count: Int = 0, val bytes: Long = 0)

data class MediaSummary(
    val images: CategoryStat,
    val videos: CategoryStat,
    val audio: CategoryStat
)

data class MediaFile(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val dateModifiedSec: Long,
    val isVideo: Boolean,
    /** Só para itens na lixeira: quando o Android apaga sozinho (segundos). */
    val expiresSec: Long = 0
)

/** Arquivos com conteúdo idêntico. O primeiro (mais antigo) é tratado como original. */
data class DuplicateGroup(val files: List<MediaFile>) {
    val original: MediaFile get() = files.first()
    val copies: List<MediaFile> get() = files.drop(1)
    val wastedBytes: Long get() = copies.sumOf { it.sizeBytes }
}

data class InstalledApp(
    val packageName: String,
    val name: String,
    val sizeBytes: Long,
    /** true = só o tamanho do instalador (sem dados/cache), por falta do acesso de uso */
    val sizeIsEstimate: Boolean,
    /** Cache do app; null quando o acesso de uso não foi liberado. */
    val cacheBytes: Long? = null,
    /** false para apps do sistema (ex.: a tela inicial), que não podem ser desinstalados */
    val removable: Boolean = true,
    val installTime: Long,
    /** null = sem registro de uso no último ano (ou acesso de uso não liberado) */
    val lastUsed: Long?
)

enum class MediaAccess { NONE, PARTIAL, FULL }

enum class HistoryType(val label: String) {
    DUPLICATES_REMOVED("Duplicadas removidas"),
    VIDEOS_TRASHED("Vídeos enviados à lixeira"),
    APP_UNINSTALLED("Aplicativo desinstalado"),
    TRASH_DELETED("Apagados da lixeira")
}

data class HistoryEntry(
    val timestamp: Long,
    val type: HistoryType,
    val count: Int,
    val bytes: Long,
    val detail: String = ""
)

data class HealthScore(val value: Int, val label: String, val headline: String)

const val DAY_MS = 24L * 60 * 60 * 1000
const val UNUSED_DAYS = 90

fun InstalledApp.isUnused(now: Long = System.currentTimeMillis()): Boolean {
    val oldEnough = now - installTime > UNUSED_DAYS * DAY_MS
    val last = lastUsed ?: return oldEnough
    return oldEnough && now - last > UNUSED_DAYS * DAY_MS
}

/**
 * Índice de organização/manutenção (0–100). Não é avaliação de desempenho.
 * Componentes: ocupação do armazenamento, espaço recuperável identificado e apps sem uso.
 */
fun computeHealth(
    storage: StorageInfo?,
    reclaimableBytes: Long,
    unusedApps: Int?,
): HealthScore? {
    if (storage == null || storage.totalBytes <= 0) return null
    val used = storage.usedFraction
    val storageScore = when {
        used <= 0.70f -> 100f
        used <= 0.90f -> 100f - (used - 0.70f) / 0.20f * 50f
        else -> (50f - (used - 0.90f) / 0.10f * 50f).coerceAtLeast(0f)
    }
    val reclaimPct = reclaimableBytes.toFloat() / storage.totalBytes * 100f
    val reclaimScore = (100f - reclaimPct * 8f).coerceIn(0f, 100f)

    val value = if (unusedApps != null) {
        val appsScore = (100f - unusedApps * 5f).coerceIn(0f, 100f)
        storageScore * 0.5f + reclaimScore * 0.25f + appsScore * 0.25f
    } else {
        storageScore * 0.65f + reclaimScore * 0.35f
    }.roundToInt().coerceIn(0, 100)

    return when {
        value >= 80 -> HealthScore(value, "Bom", "Seu celular está saudável.")
        value >= 60 -> HealthScore(value, "Pode melhorar", "Seu celular pede alguns cuidados.")
        else -> HealthScore(value, "Precisa de cuidado", "Vamos organizar seu celular com calma.")
    }
}

private val ptBR: Locale = Locale("pt", "BR")

/** Unidades decimais, iguais às usadas nas Configurações do Android. */
fun formatBytes(bytes: Long): String {
    val b = bytes.coerceAtLeast(0).toDouble()
    return when {
        b >= 1e9 -> String.format(ptBR, "%.1f GB", b / 1e9)
        b >= 1e6 -> String.format(ptBR, "%.0f MB", b / 1e6)
        b >= 1e3 -> String.format(ptBR, "%.0f KB", b / 1e3)
        else -> "${b.toLong()} B"
    }
}

fun formatCount(n: Int): String = String.format(ptBR, "%,d", n)

fun formatDaysAgo(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val days = ((now - timestamp) / DAY_MS).toInt()
    return when {
        days <= 0 -> "hoje"
        days == 1 -> "ontem"
        days < 30 -> "há $days dias"
        days < 365 -> {
            val m = days / 30
            if (m == 1) "há 1 mês" else "há $m meses"
        }
        else -> {
            val y = days / 365
            if (y == 1) "há 1 ano" else "há $y anos"
        }
    }
}

fun formatDate(timestamp: Long): String =
    java.text.SimpleDateFormat("dd/MM/yyyy", ptBR).format(java.util.Date(timestamp))
