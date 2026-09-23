package com.familygamenight.app.ui.table

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familygamenight.app.ui.TableModel
import com.familygamenight.app.ui.theme.Castle
import com.familygamenight.core.cards.Rank
import com.familygamenight.core.game.Difficulty
import com.familygamenight.core.gofish.DrawReason
import com.familygamenight.core.gofish.GoFishAction
import com.familygamenight.core.gofish.GoFishAi
import com.familygamenight.core.gofish.GoFishEvent
import com.familygamenight.core.gofish.GoFishModule
import com.familygamenight.core.gofish.GoFishPhase
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
    val canAsk = view.myTurn && !paused
    val asked = (view.phase as? GoFishPhase.Respond)?.takeIf { view.mustAnswer && !paused }
    val mustDraw = view.mustDraw && !paused
    fun send(a: GoFishAction) = onAction(GoFishModule.encodeAction(a))

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
            selectable = canAsk && selectedRank != null && view.canAsk(seat.index),
            bubble = bubbles[seat.index],
        )
    }

    Box(Modifier.fillMaxSize()) {
        TableScene(
            seats = sceneSeats,
            viewer = view.seat,
            center = TableCenter.Pond(view.pondCount, glow = mustDraw),
            onSeatTap = { target ->
                val rank = selectedRank
                if (canAsk && rank != null && view.canAsk(target)) {
                    send(GoFishAction.Ask(target, rank))
                    selectedRank = null
                }
            },
            onCenterTap = { if (mustDraw) send(GoFishAction.Draw) },
        )

        val (headline, detail) = banner(view, names, selectedRank, model.seats.getOrNull(view.current)?.kind == SeatKind.AI)
        TableBanner(headline, detail, Modifier.align(Alignment.TopCenter))

        val me = model.seats[view.seat]
        val myBooks = view.books[view.seat]
        MeBadge(
            me, avatars[me.profileId], colorOf(me.profileId),
            active = view.myTurn || view.mustAnswer || view.mustDraw,
            info = "${myBooks.size} book${if (myBooks.size == 1) "" else "s"}",
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            if (myBooks.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { myBooks.takeLast(7).forEach { MiniCard(it.label) } }
            }
        }

        if (view.config.memoryHelper) {
            TextButton(
                onClick = { showMemory = !showMemory },
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            ) { Text(if (showMemory) "Hide helper" else "🧠 Helper", color = Castle.GoldPale) }
            if (showMemory) MemoryPanel(view, names, Modifier.align(Alignment.TopEnd).padding(top = 44.dp, end = 8.dp))
        }

        // Someone asked me for a rank I don't have: I have to say it.
        if (asked != null && view.hand.none { it.rank == asked.rank }) {
            Button(
                onClick = { send(GoFishAction.SayGoFish) },
                colors = ButtonDefaults.buttonColors(containerColor = Castle.Felt, contentColor = Color.White),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 12.dp),
            ) { Text("Go fish! 🐟", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }

        FloatingHand(
            cards = view.hand,
            lifted = { it.rank == selectedRank },
            glowing = { card -> asked != null && card.rank == asked.rank },
            enabled = canAsk || asked != null,
            onTap = { card ->
                when {
                    asked != null -> if (card.rank == asked.rank) send(GoFishAction.Give)
                    canAsk -> selectedRank = if (selectedRank == card.rank) null else card.rank
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxHeight(0.33f).padding(start = 120.dp, end = 24.dp),
        )

        if (view.over) {
            val winners = view.winners()
            ResultsPanel(
                title = when {
                    winners.size > 1 -> "It's a tie!"
                    winners.single() == view.seat -> "You win! 🎉"
                    else -> "${names[winners.single()]} wins!"
                },
                rows = model.seats.sortedByDescending { view.books[it.index].size }
                    .map { ResultRow(it, it.index in winners, "${view.books[it.index].size} books") },
                avatars = avatars, colorOf = colorOf, onPlayAgain = onPlayAgain, onHome = onHome,
            )
        }
    }
}

/** Speech bubbles for what was said during the most recent ask. */
private fun bubblesFor(view: GoFishView, names: List<String>): Map<Int, String> {
    val start = view.log.indexOfLast { it is GoFishEvent.Ask }
    if (start < 0) return emptyMap()
    val out = HashMap<Int, String>()
    for (e in view.log.subList(start, view.log.size)) {
        when (e) {
            is GoFishEvent.Ask -> out[e.asker] = "${if (e.target == view.seat) "You" else names[e.target]}, got any ${e.rank.plural}?"
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
    val phase = view.phase
    val headline = when {
        view.mustAnswer && phase is GoFishPhase.Respond -> {
            val who = names[phase.asker]
            if (view.hand.any { it.rank == phase.rank }) "$who wants your ${phase.rank.plural} – tap them to hand over"
            else "$who asked for ${phase.rank.plural} – you have none. Say “Go fish!”"
        }
        view.mustDraw && phase is GoFishPhase.Draw -> when (phase.reason) {
            DrawReason.GO_FISH -> "Go fish! Tap the pond to draw a card"
            DrawReason.EMPTY_HAND -> "Your hand is empty – tap the pond to draw"
            DrawReason.NO_ONE_TO_ASK -> "Nobody has cards to ask – tap the pond to draw"
        }
        view.myTurn -> if (selected == null) "Your turn – tap a card to ask for" else "Now tap who to ask for ${selected.plural}"
        phase is GoFishPhase.Respond -> "${names[phase.target]} is checking their cards…"
        phase is GoFishPhase.Draw -> "${names[phase.player]} is fishing in the pond…"
        currentIsAi -> "${names[view.current]} is thinking…"
        else -> "${names[view.current]}'s turn"
    }
    return headline to last
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
