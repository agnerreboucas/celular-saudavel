package br.com.celularsaudavel.ui

import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CS {
    val Bg = Color(0xFFF5F6F3)
    val Surface = Color(0xFFFFFFFF)
    val Surface2 = Color(0xFFECEEE9)
    val Ink = Color(0xFF14171A)
    val Muted = Color(0xFF6B7178)
    val Green = Color(0xFF1E7F55)
    val GreenSoft = Color(0xFFE3F2EA)
    val Amber = Color(0xFF8A5D0E)
    val AmberSoft = Color(0xFFFBF1DC)
}

@Composable
fun CelularSaudavelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = CS.Ink,
            onPrimary = Color.White,
            secondary = CS.Green,
            onSecondary = Color.White,
            background = CS.Bg,
            onBackground = CS.Ink,
            surface = CS.Surface,
            onSurface = CS.Ink,
            surfaceVariant = CS.Surface2,
            onSurfaceVariant = CS.Muted,
            surfaceContainer = CS.Surface,
            secondaryContainer = CS.GreenSoft,
            onSecondaryContainer = CS.Green,
        ),
        content = content
    )
}

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth()) {
        if (onBack != null) {
            Text(
                "‹ Voltar",
                color = CS.Muted,
                fontSize = 15.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onBack)
                    .padding(vertical = 6.dp, horizontal = 2.dp)
            )
            Spacer(Modifier.height(4.dp))
        }
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = CS.Ink)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = CS.Muted, fontSize = 15.sp)
        }
    }
}

@Composable
fun CsCard(
    modifier: Modifier = Modifier,
    color: Color = CS.Surface,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val base = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(24.dp))
        .background(color)
    Box((if (onClick != null) base.clickable(onClick = onClick) else base).padding(20.dp)) {
        content()
    }
}

@Composable
fun RowCard(emoji: String, title: String, subtitle: String, onClick: (() -> Unit)? = null) {
    CsCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(emoji)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = CS.Ink, fontSize = 16.sp)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = CS.Muted, fontSize = 14.sp)
            }
            if (onClick != null) Text("›", fontSize = 26.sp, color = CS.Muted)
        }
    }
}

@Composable
fun Notice(text: String, soft: Boolean = true, action: String? = null, onAction: (() -> Unit)? = null) {
    CsCard(color = if (soft) CS.AmberSoft else CS.GreenSoft) {
        Column {
            Text(text, color = if (soft) CS.Amber else CS.Green, fontSize = 14.sp, lineHeight = 20.sp)
            if (action != null && onAction != null) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onAction, shape = RoundedCornerShape(14.dp)) {
                    Text(action, color = CS.Ink)
                }
            }
        }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = CS.Ink, contentColor = Color.White)
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun SegmentedBar(parts: List<Pair<Float, Color>>, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(CS.Surface2)
    ) {
        val visible = parts.filter { it.first > 0.002f }
        visible.forEach { (frac, color) ->
            Box(
                Modifier
                    .weight(frac.coerceIn(0.002f, 1f))
                    .height(10.dp)
                    .background(color)
            )
        }
        val rest = 1f - visible.sumOf { it.first.toDouble() }.toFloat()
        if (rest > 0.002f) Spacer(Modifier.weight(rest))
    }
}

@Composable
fun LegendDot(color: Color, label: String, value: String, onClick: (() -> Unit)? = null) {
    val base = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(10.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = (if (onClick != null) base.clickable(onClick = onClick) else base)
            .padding(vertical = if (onClick != null) 8.dp else 3.dp)
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(label, color = CS.Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = CS.Muted, fontSize = 14.sp)
        if (onClick != null) {
            Spacer(Modifier.width(6.dp))
            Text("›", color = CS.Muted, fontSize = 20.sp)
        }
    }
}

@Composable
fun MediaThumb(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bmp by produceState<ImageBitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= 29) {
                try {
                    context.contentResolver.loadThumbnail(uri, Size(256, 256), null).asImageBitmap()
                } catch (_: Exception) {
                    null
                }
            } else null
        }
    }
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CS.Surface2),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) {
            Image(b, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            BareIcon("🖼️", CS.Muted)
        }
    }
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) CS.Ink else CS.Surface,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
    ) {
        Text(
            text,
            color = if (selected) Color.White else CS.Ink,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

val ListSpacing = Arrangement.spacedBy(14.dp)
