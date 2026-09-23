package com.familygamenight.core.crazyeights

import com.familygamenight.core.cards.Card
import com.familygamenight.core.cards.Decks
import com.familygamenight.core.cards.Rank
import com.familygamenight.core.cards.Suit
import com.familygamenight.core.cards.sortedForHand
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.random.Random

object CrazyEightsRuleIds {
    const val DRAW_UNTIL_PLAYABLE = "draw_until_playable"
    const val TWOS_DRAW_TWO = "twos_draw_two"
    const val QUEENS_SKIP = "queens_skip"
    const val ACES_REVERSE = "aces_reverse"
}

@Serializable
data class CrazyEightsConfig(
    val players: Int,
    val decks: Int,
    val handSize: Int,
    val drawUntilPlayable: Boolean,
    val twosDrawTwo: Boolean,
    val queensSkip: Boolean,
    val acesReverse: Boolean,
) {
    companion object {
        fun from(players: Int, rules: Map<String, Boolean>): CrazyEightsConfig {
            require(players in CrazyEights.MIN_PLAYERS..CrazyEights.MAX_PLAYERS) { "Crazy Eights needs 2–10 players" }
            return CrazyEightsConfig(
                players = players,
                // One deck runs thin beyond five players, so shuffle in a second.
                decks = if (players <= 5) 1 else 2,
                handSize = if (players == 2) 7 else 5,
                drawUntilPlayable = rules[CrazyEightsRuleIds.DRAW_UNTIL_PLAYABLE] ?: false,
                twosDrawTwo = rules[CrazyEightsRuleIds.TWOS_DRAW_TWO] ?: false,
                queensSkip = rules[CrazyEightsRuleIds.QUEENS_SKIP] ?: false,
                acesReverse = rules[CrazyEightsRuleIds.ACES_REVERSE] ?: false,
            )
        }
    }
}

/** Public table talk: everything here everyone saw. */
@Serializable
sealed class CrazyEightsEvent {
    @Serializable @SerialName("play")
    data class Played(val player: Int, val card: Card, val calledSuit: Suit? = null) : CrazyEightsEvent()

    @Serializable @SerialName("draw")
    data class Drew(val player: Int, val penalty: Boolean = false) : CrazyEightsEvent()

    @Serializable @SerialName("pass")
    data class Passed(val player: Int) : CrazyEightsEvent()

    @Serializable @SerialName("skip")
    data class Skipped(val player: Int) : CrazyEightsEvent()

    @Serializable @SerialName("reverse")
    data class Reversed(val player: Int) : CrazyEightsEvent()

    @Serializable @SerialName("must_draw")
    data class MustDraw(val player: Int, val count: Int) : CrazyEightsEvent()

    @Serializable @SerialName("reshuffle")
    data object Reshuffled : CrazyEightsEvent()

    @Serializable @SerialName("over")
    data class Over(val winners: List<Int>) : CrazyEightsEvent()
}

@Serializable
sealed class CrazyEightsPhase {
    /** Play a matching card (or an eight), or draw. */
    @Serializable @SerialName("play")
    data object Play : CrazyEightsPhase()

    /** Drew one card (standard rule): play a card if you can, or pass. */
    @Serializable @SerialName("after_draw")
    data object AfterDraw : CrazyEightsPhase()

    /** Hit by a two: draw [remaining] more cards, then your turn is over. */
    @Serializable @SerialName("penalty")
    data class Penalty(val remaining: Int) : CrazyEightsPhase()
}

@Serializable
data class CrazyEightsState(
    val config: CrazyEightsConfig,
    val hands: List<List<Card>>,
    val stock: List<Card>,
    /** Face-up pile; the last card is on top. */
    val discard: List<Card>,
    /** The suit to follow – the top card's suit, or whatever was called with an eight. */
    val activeSuit: Suit,
    val current: Int,
    val direction: Int = 1,
    val phase: CrazyEightsPhase = CrazyEightsPhase.Play,
    val log: List<CrazyEightsEvent> = emptyList(),
    val over: Boolean = false,
    val winners: List<Int> = emptyList(),
    val lastDrawn: List<Card?> = List(hands.size) { null },
    /** Passes in a row with nothing left to draw – if everyone passes, the game is stuck and ends. */
    val passesInARow: Int = 0,
    val shuffleSeed: Long = 0,
    val reshuffles: Int = 0,
) {
    val top: Card get() = discard.last()
}

@Serializable
sealed class CrazyEightsAction {
    /** Play [card]. An eight also needs the [suit] to call. */
    @Serializable @SerialName("play")
    data class Play(val card: Card, val suit: Suit? = null) : CrazyEightsAction()

    @Serializable @SerialName("draw")
    data object Draw : CrazyEightsAction()

    @Serializable @SerialName("pass")
    data object Pass : CrazyEightsAction()
}

@Serializable
data class CrazyEightsView(
    val seat: Int,
    val config: CrazyEightsConfig,
    val hand: List<Card>,
    val handCounts: List<Int>,
    val stockCount: Int,
    /** Cards that could be reshuffled into the stock when it runs out. */
    val discardCount: Int,
    val top: Card,
    val activeSuit: Suit,
    val current: Int,
    val direction: Int,
    val phase: CrazyEightsPhase,
    val log: List<CrazyEightsEvent>,
    val over: Boolean,
    val winners: List<Int>,
    val lastDrawn: Card?,
) {
    val myTurn get() = !over && current == seat
    val canDraw get() = stockCount + discardCount - 1 > 0 || stockCount > 0
    fun isPlayable(card: Card) = CrazyEights.matches(card, top, activeSuit)
    fun playable(): List<Card> =
        if (!myTurn || phase is CrazyEightsPhase.Penalty) emptyList() else hand.filter { isPlayable(it) }
    fun canPass(): Boolean = myTurn && (phase == CrazyEightsPhase.AfterDraw || (phase == CrazyEightsPhase.Play && !canDraw && playable().isEmpty()))
}

object CrazyEights {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 10

    fun matches(card: Card, top: Card, activeSuit: Suit) =
        card.rank == Rank.EIGHT || card.suit == activeSuit || card.rank == top.rank

    fun deal(config: CrazyEightsConfig, random: Random): CrazyEightsState {
        val deck = Decks.shuffled(config.decks, random).toMutableList()
        val hands = List(config.players) { deck.subList(it * config.handSize, (it + 1) * config.handSize).sortedForHand() }
        val rest = deck.drop(config.players * config.handSize).toMutableList()
        // Don't start on an eight: tuck it back into the middle of the stock.
        var first = rest.removeAt(0)
        while (first.rank == Rank.EIGHT) {
            rest.add(rest.size / 2, first)
            first = rest.removeAt(0)
        }
        return CrazyEightsState(
            config = config,
            hands = hands,
            stock = rest,
            discard = listOf(first),
            activeSuit = first.suit,
            current = 0,
            shuffleSeed = random.nextLong(),
        )
    }

    private fun canDraw(s: CrazyEightsState) = s.stock.isNotEmpty() || s.discard.size > 1

    fun validate(s: CrazyEightsState, seat: Int, a: CrazyEightsAction): String? {
        if (s.over) return "The game is over"
        if (seat != s.current) return "It's not your turn"
        return when (a) {
            is CrazyEightsAction.Play -> when {
                s.phase is CrazyEightsPhase.Penalty -> "You have to draw first"
                a.card !in s.hands[seat] -> "You don't have that card"
                !matches(a.card, s.top, s.activeSuit) -> "That card doesn't match"
                a.card.rank == Rank.EIGHT && a.suit == null -> "Pick a suit for your eight"
                else -> null
            }
            CrazyEightsAction.Draw -> when {
                s.phase == CrazyEightsPhase.AfterDraw -> "You've drawn – play a card or pass"
                !canDraw(s) -> "There's nothing left to draw"
                else -> null
            }
            CrazyEightsAction.Pass -> when {
                s.phase == CrazyEightsPhase.AfterDraw -> null
                s.phase is CrazyEightsPhase.Penalty -> if (canDraw(s)) "You have to draw first" else null
                canDraw(s) -> "Draw a card first"
                s.hands[seat].any { matches(it, s.top, s.activeSuit) } -> "You have a card you can play"
                else -> null
            }
        }
    }

    fun apply(s0: CrazyEightsState, seat: Int, a: CrazyEightsAction): CrazyEightsState {
        validate(s0, seat, a)?.let { throw IllegalArgumentException(it) }
        var s = s0
        when (a) {
            is CrazyEightsAction.Play -> {
                val called = if (a.card.rank == Rank.EIGHT) a.suit else null
                s = s.copy(
                    hands = s.hands.mapIndexed { i, h -> if (i == seat) h - a.card else h },
                    discard = s.discard + a.card,
                    activeSuit = called ?: a.card.suit,
                    log = s.log + CrazyEightsEvent.Played(seat, a.card, called),
                    passesInARow = 0,
                    phase = CrazyEightsPhase.Play,
                )
                if (s.hands[seat].isEmpty()) return finish(s, listOf(seat))
                s = afterPlay(s, seat, a.card)
            }
            CrazyEightsAction.Draw -> {
                s = drawOne(s, seat, penalty = s.phase is CrazyEightsPhase.Penalty)
                when (val p = s.phase) {
                    is CrazyEightsPhase.Penalty -> {
                        val left = p.remaining - 1
                        s = if (left > 0 && canDraw(s)) s.copy(phase = CrazyEightsPhase.Penalty(left))
                        else s.copy(phase = CrazyEightsPhase.Play, current = step(s, seat, 1))
                    }
                    else -> {
                        // Standard rule: draw one, then play or pass. House rule: keep drawing until you can play.
                        if (!s.config.drawUntilPlayable) s = s.copy(phase = CrazyEightsPhase.AfterDraw)
                    }
                }
            }
            CrazyEightsAction.Pass -> {
                val stuck = !canDraw(s)
                s = s.copy(
                    log = s.log + CrazyEightsEvent.Passed(seat),
                    phase = CrazyEightsPhase.Play,
                    current = step(s, seat, 1),
                    passesInARow = if (stuck) s.passesInARow + 1 else 0,
                )
                if (stuck && s.passesInARow >= s.config.players) {
                    // Nobody can go and there's nothing to draw: fewest cards wins.
                    val fewest = s.hands.minOf { it.size }
                    return finish(s, s.hands.indices.filter { s.hands[it].size == fewest })
                }
            }
        }
        return s
    }

    private fun step(s: CrazyEightsState, from: Int, times: Int): Int {
        val n = s.config.players
        return Math.floorMod(from + s.direction * times, n)
    }

    private fun afterPlay(s0: CrazyEightsState, seat: Int, card: Card): CrazyEightsState {
        var s = s0
        val cfg = s.config
        when {
            cfg.acesReverse && card.rank == Rank.ACE -> {
                s = s.copy(direction = -s.direction, log = s.log + CrazyEightsEvent.Reversed(seat))
                s = s.copy(current = step(s, seat, 1))
            }
            cfg.queensSkip && card.rank == Rank.QUEEN -> {
                val skipped = step(s, seat, 1)
                s = s.copy(log = s.log + CrazyEightsEvent.Skipped(skipped), current = step(s, seat, 2))
            }
            cfg.twosDrawTwo && card.rank == Rank.TWO -> {
                val victim = step(s, seat, 1)
                s = s.copy(current = victim)
                if (canDraw(s)) {
                    s = s.copy(phase = CrazyEightsPhase.Penalty(2), log = s.log + CrazyEightsEvent.MustDraw(victim, 2))
                }
            }
            else -> s = s.copy(current = step(s, seat, 1))
        }
        return s
    }

    private fun drawOne(s0: CrazyEightsState, seat: Int, penalty: Boolean): CrazyEightsState {
        var s = s0
        if (s.stock.isEmpty()) {
            // Shuffle everything under the top card back in as the new stock.
            val under = s.discard.dropLast(1)
            s = s.copy(
                stock = under.shuffled(Random(s.shuffleSeed + s.reshuffles)),
                discard = listOf(s.top),
                reshuffles = s.reshuffles + 1,
                log = s.log + CrazyEightsEvent.Reshuffled,
            )
        }
        val card = s.stock.first()
        return s.copy(
            stock = s.stock.drop(1),
            hands = s.hands.mapIndexed { i, h -> if (i == seat) (h + card).sortedForHand() else h },
            lastDrawn = s.lastDrawn.mapIndexed { i, c -> if (i == seat) card else c },
            log = s.log + CrazyEightsEvent.Drew(seat, penalty),
            passesInARow = 0,
        )
    }

    private fun finish(s: CrazyEightsState, winners: List<Int>) =
        s.copy(over = true, winners = winners, log = s.log + CrazyEightsEvent.Over(winners))

    fun view(s: CrazyEightsState, seat: Int) = CrazyEightsView(
        seat = seat,
        config = s.config,
        hand = s.hands[seat],
        handCounts = s.hands.map { it.size },
        stockCount = s.stock.size,
        discardCount = s.discard.size,
        top = s.top,
        activeSuit = s.activeSuit,
        current = s.current,
        direction = s.direction,
        phase = s.phase,
        log = s.log,
        over = s.over,
        winners = s.winners,
        lastDrawn = s.lastDrawn.getOrNull(seat),
    )

    /** Penalty points left in a hand (for the end-of-game table): eights 50, pictures 10, aces 1. */
    fun penaltyPoints(hand: List<Card>) = hand.sumOf {
        when (it.rank) {
            Rank.EIGHT -> 50
            Rank.JACK, Rank.QUEEN, Rank.KING, Rank.TEN -> 10
            Rank.ACE -> 1
            else -> it.rank.ordinal + 2
        }.toInt()
    }
}
