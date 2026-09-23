package com.familygamenight.app.ui.table

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.familygamenight.app.ui.TableModel
import com.familygamenight.app.ui.theme.Castle
import com.familygamenight.core.cards.Rank
import com.familygamenight.core.game.Difficulty
import com.familygamenight.core.gofish.GoFishAction
import com.familygamenight.core.gofish.GoFishAi
import com.familygamenight.core.gofish.GoFishEvent
import com.familygamenight.core.gofish.GoFishModule
import com.familygamenight.core.gofish.GoFishView
import com.familygamenight.core.session.SeatKind

@Composable
fun GoFishTable(
    model: TableModel,
    avatars: Map<String, ImageBitmap>,
    colorOf: (String) -> Color,
    onAction: (kotlinx.serialization.json.JsonElement) -> Unit,
    onPlayAgain: (() -> Unit)?,
    onHome: () -> Unit,
) {
    val view = remember(model.view) { GoFishModule.decodeView(model.view) }
    val names = model.seats.map { it.name }
    var selectedRank by remember { mutableStateOf<Rank?>(null) }
    var showMemory by remember { mutableStateOf(false) }
    val paused = model.missing.isNotEmpty()
    val canAct = view.myTurn && !paused

    LaunchedEffect(view.myTurn, view.hand) {
        if (!view.myTurn || view.hand.none { it.rank == selectedRank }) selectedRank = null
    }

    val bubbles = remember(view.log.size) { bubblesFor(view, names) }
    val sceneSeats = model.seats.map { seat ->
        SceneSeat(
            index = seat.index,
            name = seat.name,
            avatar = avatars[seat.profileId],
            color = colorOf(seat.profileId),
            cardCount = view.handCounts[seat.index],
            books = view.books[seat.index].map { it.label },
            isCurrent = !view.over && view.current == seat.index,
            isAi = seat.kind == SeatKind.AI,
            missing = seat.isMissing,
            selectable = canAct && selectedRank != null && view.canAsk(seat.index),
            bubble = bubbles[seat.index],
        )
    }

    Box(Modifier.fillMaxSize()) {
        TableScene(
            seats = sceneSeats,
            viewer = view.seat,
            pondCount = view.pondCount,
            onSeatTap = { target ->
                val rank = selectedRank
                if (canAct && rank != null && view.canAsk(target)) {
                    onAction(GoFishModule.encodeAction(GoFishAction(target, rank)))
                    selectedRank = null
                }
            },
        )

        // Top banner: what just happened / what to do
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 4.dp).fillMaxWidth(0.62f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xCC140E0A))
                .border(1.dp, Castle.Gold.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val (headline, detail) = banner(view, names, selectedRank, model.seats.getOrNull(view.current)?.kind == SeatKind.AI)
            Text(headline, color = Castle.GoldPale, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) Text(detail, color = Castle.Parchment, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        // Bottom-left: me and my books
        Row(
            Modifier.align(Alignment.BottomStart).padding(8.dp)
                .clip(RoundedCornerShape(12.dp)).background(Color(0xB3140E0A)).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val me = model.seats[view.seat]
            Avatar(me.name, avatars[me.profileId], colorOf(me.profileId), 40.dp, ring = if (view.myTurn) Castle.GoldPale else Castle.Gold)
            Column(Modifier.padding(start = 8.dp)) {
                Text(me.name, color = Castle.Parchment, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("${view.books[view.seat].size} book${if (view.books[view.seat].size == 1) "" else "s"}", color = Castle.GoldPale, fontSize = 12.sp)
                if (view.books[view.seat].isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        view.books[view.seat].takeLast(7).forEach { MiniCard(it.label) }
                    }
                }
            }
        }

        if (view.config.memoryHelper) {
            TextButton(
                onClick = { showMemory = !showMemory },
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            ) { Text(if (showMemory) "Hide helper" else "🧠 Helper", color = Castle.GoldPale) }
            if (showMemory) MemoryPanel(view, names, Modifier.align(Alignment.TopEnd).padding(top = 44.dp, end = 8.dp))
        }

        // My floating hand
        FloatingHand(
            cards = view.hand,
            selectedRank = selectedRank,
            enabled = canAct,
            onTap = { card -> selectedRank = if (selectedRank == card.rank) null else card.rank },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxHeight(0.33f).padding(start = 120.dp, end = 24.dp),
        )

        if (view.over) GameOverPanel(view, model, avatars, colorOf, onPlayAgain, onHome)
    }
}

/** Speech bubbles for what was said during the most recent ask. */
private fun bubblesFor(view: GoFishView, names: List<String>): Map<Int, String> {
    val start = view.log.indexOfLast { it is GoFishEvent.Ask }
    if (start < 0) return emptyMap()
    val out = HashMap<Int, String>()
    for (e in view.log.subList(start, view.log.size)) {
        when (e) {
            is GoFishEvent.Ask -> out[e.asker] = "${names[e.target]}, got any ${e.rank.plural}?"
            is GoFishEvent.Give -> out[e.from] = if (e.count == 1) "Here's one." else "Here – ${e.count} of them."
            is GoFishEvent.GoFish -> out[e.target] = "Go fish! 🐟"
            is GoFishEvent.Draw -> e.luckyRank?.let { out[e.player] = "Lucky catch – a ${it.singular}!" }
            is GoFishEvent.Book -> out[e.player] = "A book of ${e.rank.plural}!"
            else -> Unit
        }
    }
    return out
}

private fun banner(view: GoFishView, names: List<String>, selected: Rank?, currentIsAi: Boolean): Pair<String, String?> {
    val last = lastHappening(view, names)
    if (view.over) return "Game over!" to null
    return if (view.myTurn) {
        (if (selected == null) "Your turn – tap a card to choose what to ask for" else "Now tap who to ask for ${selected.plural}") to last
    } else {
        val who = names[view.current]
        (if (currentIsAi) "$who is thinking…" else "$who's turn") to last
    }
}

private fun lastHappening(view: GoFishView, names: List<String>): String? {
    fun n(i: Int) = if (i == view.seat) "You" else names[i]
    fun obj(i: Int) = if (i == view.seat) "you" else names[i]
    val start = view.log.indexOfLast { it is GoFishEvent.Ask }
    val parts = mutableListOf<String>()
    if (start >= 0) {
        for (e in view.log.subList(start, view.log.size)) {
            when (e) {
                is GoFishEvent.Ask -> parts += "${n(e.asker)} asked ${obj(e.target)} for ${e.rank.plural}"
                is GoFishEvent.Give -> parts += "got ${e.count}"
                is GoFishEvent.GoFish -> parts += "Go fish!"
                is GoFishEvent.Draw -> {
                    val lucky = e.luckyRank
                    if (e.player == view.seat) {
                        parts += if (lucky != null) "you caught the ${view.lastDrawn ?: lucky.singular} – go again!"
                        else "you drew ${view.lastDrawn ?: "a card"}"
                    } else if (lucky != null) {
                        parts += "lucky catch!"
                    }
                }
                is GoFishEvent.Book -> parts += "${n(e.player)} made a book of ${e.rank.plural}"
                is GoFishEvent.PondEmpty -> parts += "the pond is empty"
                else -> Unit
            }
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@Composable
private fun MemoryPanel(view: GoFishView, names: List<String>, modifier: Modifier) {
    // The same "who asked for what" memory the Hard computer player uses.
    val beliefs = GoFishAi.beliefs(view, Difficulty.HARD)
    Column(
        modifier.width(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xE6231913))
            .border(1.dp, Castle.Gold.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(10.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Who has what (from what's been said)", color = Castle.GoldPale, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        names.indices.filter { it != view.seat }.forEach { p ->
            val has = beliefs.filter { it.key.first == p && it.value == GoFishAi.Belief.HAS }.keys.map { it.second }.sortedBy { it.ordinal }
            Text(
                "${names[p]}: " + if (has.isEmpty()) "–" else has.joinToString { it.plural },
                color = Castle.Parchment, fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun GameOverPanel(
    view: GoFishView,
    model: TableModel,
    avatars: Map<String, ImageBitmap>,
    colorOf: (String) -> Color,
    onPlayAgain: (() -> Unit)?,
    onHome: () -> Unit,
) {
    val winners = view.winners()
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
            val title = when {
                winners.size > 1 -> "It's a tie!"
                winners.single() == view.seat -> "You win! 🎉"
                else -> "${model.seats[winners.single()].name} wins!"
            }
            Text(title, style = MaterialTheme.typography.headlineMedium, color = Castle.GoldPale)
            model.seats.sortedByDescending { view.books[it.index].size }.forEach { s ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(s.name, avatars[s.profileId], colorOf(s.profileId), 32.dp)
                    Text(
                        (if (s.index in winners) "👑 " else "") + s.name,
                        modifier = Modifier.weight(1f).padding(start = 10.dp),
                        fontWeight = if (s.index in winners) FontWeight.Bold else FontWeight.Normal,
                    )
                    Text("${view.books[s.index].size} books", color = Castle.GoldPale)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                if (onPlayAgain != null) Button(onClick = onPlayAgain) { Text("Play again") }
                OutlinedButton(onClick = onHome) { Text("Home") }
            }
            if (onPlayAgain == null) Text("The host can start another round.", fontSize = 12.sp)
        }
    }
}
