package com.familygamenight.app.ui.table

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.familygamenight.app.ui.theme.Castle
import com.familygamenight.core.cards.Card
import com.familygamenight.core.cards.Rank
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun CardFace(card: Card, width: Dp, highlighted: Boolean, modifier: Modifier = Modifier) {
    val height = width * 1.42f
    val ink = if (card.suit.isRed) Castle.CardRed else Castle.CardBlack
    val corner = (width.value * 0.25f).sp
    Box(
        modifier
            .size(width, height)
            .shadow(if (highlighted) 14.dp else 6.dp, RoundedCornerShape(width * 0.1f))
            .clip(RoundedCornerShape(width * 0.1f))
            .background(Color(0xFFFBF7EE))
            .border(if (highlighted) 3.dp else 1.dp, if (highlighted) Castle.Gold else Color(0xFFB9A98A), RoundedCornerShape(width * 0.1f)),
    ) {
        Column(Modifier.align(Alignment.TopStart).padding(start = width * 0.07f, top = width * 0.04f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(card.rank.label, color = ink, fontSize = corner, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, lineHeight = corner)
            Text(card.suit.symbol, color = ink, fontSize = corner * 0.9f, lineHeight = corner)
        }
        Text(
            card.suit.symbol,
            color = ink,
            fontSize = (width.value * 0.55f).sp,
            modifier = Modifier.align(Alignment.Center).offset(y = width * 0.06f),
        )
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = width * 0.07f, bottom = width * 0.04f).rotate(180f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(card.rank.label, color = ink, fontSize = corner, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, lineHeight = corner)
            Text(card.suit.symbol, color = ink, fontSize = corner * 0.9f, lineHeight = corner)
        }
    }
}

/**
 * The viewer's own cards, floating in front of them and tilted back slightly like they're held
 * up over the table edge. Tapping picks a rank; all cards of that rank lift up.
 */
@Composable
fun FloatingHand(
    cards: List<Card>,
    selectedRank: Rank?,
    enabled: Boolean,
    onTap: (Card) -> Unit,
    modifier: Modifier = Modifier,
) {
    val anim = rememberInfiniteTransition(label = "hand")
    val t by anim.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart), label = "bob")

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val n = cards.size.coerceAtLeast(1)
        val cardW = min(maxHeight / 1.42f * 0.8f, 84.dp)
        val available = maxWidth * 0.86f - cardW
        val step = if (n > 1) min(cardW * 0.78f, available / (n - 1)) else 0.dp
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            cards.forEachIndexed { i, card ->
                val off = i - (n - 1) / 2f
                val lifted = card.rank == selectedRank
                val lift by animateDpAsState(if (lifted) cardW * 0.32f else 0.dp, label = "lift")
                val bob = sin((t + i * 0.07f) * 2 * PI).toFloat() * 3f
                CardFace(
                    card,
                    cardW,
                    highlighted = lifted,
                    modifier = Modifier
                        .offset(x = step * off, y = (abs(off) * abs(off) * 0.9f).dp - lift - 6.dp)
                        .graphicsLayer {
                            rotationZ = off * (28f / n).coerceAtMost(4f)
                            rotationX = 12f
                            cameraDistance = 14f * density
                            translationY = bob
                        }
                        .clickable(enabled = enabled) { onTap(card) },
                )
            }
        }
    }
}

@Composable
fun MiniCard(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(22.dp, 30.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFFBF7EE))
            .border(1.dp, Castle.Gold, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Castle.Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
}

