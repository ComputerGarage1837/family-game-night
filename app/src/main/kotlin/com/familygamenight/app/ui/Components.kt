package com.familygamenight.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familygamenight.app.ui.theme.Castle

fun initials(name: String): String =
    name.split(' ', '-').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

/** Stable fallback colour for players without a stored colour (AI, remote players). */
fun colorFor(key: String): Color {
    val palette = com.familygamenight.app.data.ProfileStore.palette
    return Color(palette[Math.floorMod(key.hashCode(), palette.size)])
}

@Composable
fun Avatar(name: String, image: ImageBitmap?, color: Color, size: Dp, modifier: Modifier = Modifier, ring: Color = Castle.Gold) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .border(size / 18, ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(image, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(CircleShape))
        } else {
            Text(initials(name), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.38f).sp)
        }
    }
}

@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack) { Text("‹ Back", fontSize = 16.sp) }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Castle.GoldPale, modifier = Modifier.weight(1f).padding(start = 4.dp))
        actions()
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = Castle.Gold, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, Castle.Gold.copy(alpha = 0.35f), MaterialTheme.shapes.large)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}
