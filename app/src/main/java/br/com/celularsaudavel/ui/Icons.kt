package br.com.celularsaudavel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Um só estilo de ícone em todo o app: traço arredondado, verde da marca,
 * dentro de um quadrado de cantos suaves (estilo iOS).
 */
fun iconFor(key: String): ImageVector? = when (key) {
    "🏠" -> Icons.Rounded.Home
    "🧹" -> Icons.Rounded.CleaningServices
    "☁️" -> Icons.Rounded.Cloud
    "📱" -> Icons.Rounded.PhoneAndroid
    "•••" -> Icons.Rounded.MoreHoriz
    "🖼️" -> Icons.Rounded.PhotoLibrary
    "🎥" -> Icons.Rounded.VideoLibrary
    "🎬" -> Icons.Rounded.Movie
    "🗑️" -> Icons.Rounded.Delete
    "♻️" -> Icons.Rounded.Restore
    "🧽" -> Icons.Rounded.AutoDelete
    "💤" -> Icons.Rounded.Bedtime
    "📷" -> Icons.Rounded.BurstMode
    "📂" -> Icons.Rounded.FolderOpen
    "💬" -> Icons.Rounded.Forum
    "📊" -> Icons.Rounded.History
    "🔐" -> Icons.Rounded.Lock
    "🛡️" -> Icons.Rounded.Shield
    "🔔" -> Icons.Rounded.NotificationsActive
    "⭐" -> Icons.Rounded.WorkspacePremium
    "🙂" -> Icons.Rounded.EmojiEmotions
    "👤" -> Icons.Rounded.Person
    "✨" -> Icons.Rounded.AutoAwesome
    "💾" -> Icons.Rounded.SdStorage
    "🗂️" -> Icons.Rounded.Inventory2
    "👀" -> Icons.Rounded.Visibility
    "📦" -> Icons.Rounded.InstallMobile
    "🎙️" -> Icons.Rounded.Mic
    "📄" -> Icons.Rounded.Description
    "✈️" -> Icons.Rounded.Send
    "📸" -> Icons.Rounded.PhotoCamera
    "📥" -> Icons.Rounded.Download
    "🩺" -> Icons.Rounded.MonitorHeart
    "🔍" -> Icons.Rounded.Search
    "🌅" -> Icons.Rounded.WbTwilight
    "⏰" -> Icons.Rounded.Alarm
    "🎵" -> Icons.Rounded.MusicNote
    "📕" -> Icons.Rounded.PictureAsPdf
    "🌐" -> Icons.Rounded.Language
    "📧" -> Icons.Rounded.AlternateEmail
    "🔄" -> Icons.Rounded.SwapHoriz
    "✅" -> Icons.Rounded.TaskAlt
    "💚" -> Icons.Rounded.Favorite
    "🧭" -> Icons.Rounded.Explore
    "📈" -> Icons.Rounded.Insights
    "👋" -> Icons.Rounded.WavingHand
    "🌱" -> Icons.Rounded.Spa
    "🕒" -> Icons.Rounded.Schedule
    else -> null
}

/** Ícone no quadradinho verde. Se não houver ícone para a chave, mostra o próprio emoji. */
@Composable
fun AppIcon(
    key: String,
    size: Dp = 44.dp,
    tint: Color = CS.Green,
    background: Color = CS.GreenSoft,
    corner: Dp = 12.dp,
) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        val v = iconFor(key)
        if (v != null) Icon(v, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
        else Text(key, fontSize = (size.value * 0.45f).sp)
    }
}

/** Só o desenho do ícone, sem fundo (barra de navegação). */
@Composable
fun BareIcon(key: String, tint: Color, size: Dp = 24.dp) {
    val v = iconFor(key)
    if (v != null) Icon(v, contentDescription = null, tint = tint, modifier = Modifier.size(size))
    else Text(key, fontSize = (size.value * 0.8f).sp)
}
