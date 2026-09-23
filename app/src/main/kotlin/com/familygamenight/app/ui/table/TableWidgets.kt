package com.familygamenight.app.ui.table

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familygamenight.app.ui.Avatar
import com.familygamenight.app.ui.theme.Castle
import com.familygamenight.core.session.Seat

/** The strip at the top: what to do now, and what just happened. */
@Composable
fun TableBanner(headline: String, detail: String?, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(top = 4.dp).fillMaxWidth(0.62f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xCC140E0A))
            .border(1.dp, Castle.Gold.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(headline, color = Castle.GoldPale, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (detail != null) Text(detail, color = Castle.Parchment, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Bottom-left: who "you" are at this table, plus a line of game-specific info. */
@Composable
fun MeBadge(me: Seat, avatar: ImageBitmap?, color: Color, active: Boolean, info: String, modifier: Modifier = Modifier, extra: @Composable () -> Unit = {}) {
    Row(
        modifier.padding(8.dp)
            .clip(RoundedCornerShape(12.dp)).background(Color(0xB3140E0A)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(me.name, avatar, color, 40.dp, ring = if (active) Castle.GoldPale else Castle.Gold)
        Column(Modifier.padding(start = 8.dp)) {
            Text(me.name, color = Castle.Parchment, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(info, color = Castle.GoldPale, fontSize = 12.sp)
            extra()
        }
    }
}

data class ResultRow(val seat: Seat, val winner: Boolean, val score: String)

@Composable
fun ResultsPanel(
    title: String,
    rows: List<ResultRow>,
    avatars: Map<String, ImageBitmap>,
    colorOf: (String) -> Color,
    onPlayAgain: (() -> Unit)?,
    onHome: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color(0xAA000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 460.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(2.dp, Castle.Gold, RoundedCornerShape(20.dp))
                .padding(18.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = Castle.GoldPale)
            rows.forEach { r ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(r.seat.name, avatars[r.seat.profileId], colorOf(r.seat.profileId), 32.dp)
                    Text(
                        (if (r.winner) "👑 " else "") + r.seat.name,
                        color = Castle.Parchment,
                        modifier = Modifier.weight(1f).padding(start = 10.dp),
                        fontWeight = if (r.winner) FontWeight.Bold else FontWeight.Normal,
                    )
                    Text(r.score, color = Castle.GoldPale)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                if (onPlayAgain != null) Button(onClick = onPlayAgain) { Text("Play again") }
                OutlinedButton(onClick = onHome) { Text("Home") }
            }
            if (onPlayAgain == null) Text("The host can start another round.", color = Castle.Parchment, fontSize = 12.sp)
        }
    }
}
