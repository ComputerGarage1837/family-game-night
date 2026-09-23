package com.familygamenight.app.ui.table

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.familygamenight.core.cards.Card
import com.familygamenight.core.cards.Rank
import com.familygamenight.core.cards.Suit
import com.familygamenight.core.crazyeights.CrazyEightsAction
import com.familygamenight.core.crazyeights.CrazyEightsEvent
import com.familygamenight.core.crazyeights.CrazyEightsModule
import com.familygamenight.core.crazyeights.CrazyEightsPhase
import com.familygamenight.core.crazyeights.CrazyEightsView
import com.familygamenight.core.session.SeatKind

private val Suit.plural get() = name.lowercase().replaceFirstChar { it.uppercase() }

@Composable
fun CrazyEightsTable(
    model: TableModel,
    avatars: Map<String, ImageBitmap>,
    colorOf: (String) -> Color,
    onAction: (kotlinx.serialization.json.JsonElement) -> Unit,
    onPlayAgain: (() -> Unit)?,
    onHome: () -> Unit,
) {
    val view = remember(model.view) { CrazyEightsModule.decodeView(model.view) }
    val names = model.seats.map { it.name }
    val paused = model.missing.isNotEmpty()
    val myMove = view.myTurn && !paused
    val playable = if (myMove) view.playable().toSet() else emptySet()
    val penalty = view.phase as? CrazyEightsPhase.Penalty
    val stockGlow = myMove && view.canDraw && (penalty != null || (view.phase == CrazyEightsPhase.Play && playable.isEmpty()))
    var choosingSuitFor by remember { mutableStateOf<Card?>(null) }
    fun send(a: CrazyEightsAction) = onAction(CrazyEightsModule.encodeAction(a))

    LaunchedEffect(view.myTurn, view.hand) {
        if (!view.myTurn || choosingSuitFor !in view.hand) choosingSuitFor = null
    }

    val bubbles = remember(view.log.size) { bubblesFor(view) }
    val sceneSeats = model.seats.map { seat ->
        SceneSeat(
            index = seat.index,
            name = seat.name,
            avatar = avatars[seat.profileId],
            color = colorOf(seat.profileId),
            cardCount = view.handCounts[seat.index],
            books = emptyList(),
            isCurrent = !view.over && view.current == seat.index,
            isAi = seat.kind == SeatKind.AI,
            missing = seat.isMissing,
            selectable = false,
            bubble = bubbles[seat.index],
        )
    }

    Box(Modifier.fillMaxSize()) {
        TableScene(
            seats = sceneSeats,
            viewer = view.seat,
            center = TableCenter.Piles(
                stockCount = view.stockCount,
                top = view.top,
                calledSuit = view.activeSuit.takeIf { view.top.rank == Rank.EIGHT },
                glowStock = stockGlow,
            ),
            onCenterTap = {
                if (myMove && view.phase != CrazyEightsPhase.AfterDraw && view.canDraw) {
                    choosingSuitFor = null
                    send(CrazyEightsAction.Draw)
                }
            },
        )

        val (headline, detail) = banner(view, names, playable.isNotEmpty(), model.seats.getOrNull(view.current)?.kind == SeatKind.AI)
        TableBanner(headline, detail, Modifier.align(Alignment.TopCenter))

        val me = model.seats[view.seat]
        MeBadge(
            me, avatars[me.profileId], colorOf(me.profileId),
            active = view.myTurn,
            info = "${view.hand.size} card${if (view.hand.size == 1) "" else "s"} left",
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            if (view.config.acesReverse) {
                Text(if (view.direction == 1) "Play goes ↻ left" else "Play goes ↺ right", color = Castle.Parchment, fontSize = 11.sp)
            }
        }

        if (myMove && view.canPass()) {
            Button(
                onClick = { send(CrazyEightsAction.Pass) },
                colors = ButtonDefaults.buttonColors(containerColor = Castle.Velvet, contentColor = Color.White),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 12.dp),
            ) { Text("Pass", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }

        FloatingHand(
            cards = view.hand,
            lifted = { it == choosingSuitFor },
            glowing = { it in playable },
            enabled = myMove,
            onTap = { card ->
                if (card in playable) {
                    if (card.rank == Rank.EIGHT) choosingSuitFor = if (choosingSuitFor == card) null else card
                    else send(CrazyEightsAction.Play(card))
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxHeight(0.33f).padding(start = 120.dp, end = 24.dp),
        )

        choosingSuitFor?.let { eight ->
            SuitPicker(
                modifier = Modifier.align(Alignment.Center).padding(top = 40.dp),
                onPick = { suit ->
                    choosingSuitFor = null
                    send(CrazyEightsAction.Play(eight, suit))
                },
                onCancel = { choosingSuitFor = null },
            )
        }

        if (view.over) {
            val winners = view.winners
            ResultsPanel(
                title = when {
                    winners.size > 1 -> "It's a tie!"
                    winners.single() == view.seat -> "You win! 🎉"
                    else -> "${names[winners.single()]} wins!"
                },
                rows = model.seats.sortedBy { view.handCounts[it.index] }.map {
                    val left = view.handCounts[it.index]
                    ResultRow(it, it.index in winners, if (left == 0) "out!" else "$left card${if (left == 1) "" else "s"} left")
                },
                avatars = avatars, colorOf = colorOf, onPlayAgain = onPlayAgain, onHome = onHome,
            )
        }
    }
}

@Composable
private fun SuitPicker(modifier: Modifier, onPick: (Suit) -> Unit, onCancel: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xEE231913))
            .border(1.dp, Castle.Gold, RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Crazy eight! Call a suit", color = Castle.GoldPale, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
            Suit.entries.forEach { suit ->
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(Castle.Parchment)
                        .border(2.dp, Castle.Gold, CircleShape)
                        .clickable { onPick(suit) },
                    contentAlignment = Alignment.Center,
                ) { Text(suit.symbol, fontSize = 28.sp, color = if (suit.isRed) Castle.CardRed else Castle.CardBlack) }
            }
        }
        TextButton(onClick = onCancel) { Text("Cancel", color = Castle.Parchment) }
    }
}

private fun bubblesFor(view: CrazyEightsView): Map<Int, String> {
    val out = HashMap<Int, String>()
    val last = view.log.lastOrNull() ?: return out
    when (last) {
        is CrazyEightsEvent.Played -> last.calledSuit?.let { out[last.player] = "${it.plural}!" }
        is CrazyEightsEvent.Skipped -> out[last.player] = "Skipped!"
        is CrazyEightsEvent.MustDraw -> out[last.player] = "Oh no – draw ${last.count}!"
        is CrazyEightsEvent.Passed -> out[last.player] = "Pass."
        else -> Unit
    }
    // Anyone down to their last card announces it.
    view.handCounts.forEachIndexed { i, n -> if (n == 1 && i != view.seat && !view.over) out.putIfAbsent(i, "Last card!") }
    return out
}

private fun banner(view: CrazyEightsView, names: List<String>, havePlayable: Boolean, currentIsAi: Boolean): Pair<String, String?> {
    if (view.over) return "Game over!" to null
    val want = if (view.top.rank == Rank.EIGHT) "${view.activeSuit.symbol} ${view.activeSuit.plural}" else "${view.top.rank.label}${view.top.suit.symbol}"
    val headline = when {
        view.myTurn -> when (val p = view.phase) {
            is CrazyEightsPhase.Penalty -> "Hit by a two – tap the deck to draw (${p.remaining} to go)"
            CrazyEightsPhase.AfterDraw -> if (havePlayable) "Play a glowing card, or pass" else "No luck – tap Pass"
            CrazyEightsPhase.Play -> when {
                havePlayable -> "Your turn – play a glowing card on $want, or draw"
                view.canDraw -> "Nothing matches $want – tap the deck to draw"
                else -> "Nothing to play or draw – tap Pass"
            }
        }
        currentIsAi -> "${names[view.current]} is thinking…"
        else -> "${names[view.current]}'s turn"
    }
    return headline to lastHappening(view, names)
}

private fun lastHappening(view: CrazyEightsView, names: List<String>): String? {
    fun n(i: Int) = if (i == view.seat) "You" else names[i]
    val recent = view.log.takeLast(3).mapNotNull { e ->
        when (e) {
            is CrazyEightsEvent.Played -> if (e.calledSuit != null) "${n(e.player)} played an eight and called ${e.calledSuit!!.plural}"
            else "${n(e.player)} played ${e.card}"
            is CrazyEightsEvent.Drew -> if (e.player == view.seat) "you drew ${view.lastDrawn ?: "a card"}" else "${names[e.player]} drew a card"
            is CrazyEightsEvent.Passed -> "${n(e.player)} passed"
            is CrazyEightsEvent.Skipped -> "${n(e.player)} ${if (e.player == view.seat) "are" else "is"} skipped"
            is CrazyEightsEvent.Reversed -> "play reversed"
            is CrazyEightsEvent.MustDraw -> "${n(e.player)} must draw ${e.count}"
            CrazyEightsEvent.Reshuffled -> "the pile was shuffled into a new deck"
            else -> null
        }
    }
    return recent.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

