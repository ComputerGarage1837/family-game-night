package com.familygamenight.core.gofish

import com.familygamenight.core.cards.Card
import com.familygamenight.core.cards.Decks
import com.familygamenight.core.cards.Rank
import com.familygamenight.core.cards.sortedForHand
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.random.Random

object GoFishRuleIds {
    const val LUCKY_FISH = "lucky_fish"
    const val PAIRS = "pairs"
    const val TWO_DECKS = "two_decks"
    const val MEMORY_HELPER = "memory_helper"
}

@Serializable
data class GoFishConfig(
    val players: Int,
    val decks: Int,
    val handSize: Int,
    /** 4 for classic books, 2 when playing the "pairs" house rule. */
    val bookSize: Int,
    val luckyFish: Boolean,
    val memoryHelper: Boolean,
) {
    companion object {
        fun from(players: Int, rules: Map<String, Boolean>): GoFishConfig {
            require(players in GoFish.MIN_PLAYERS..GoFish.MAX_PLAYERS) { "Go Fish needs 2–10 players" }
            val decks = if (rules[GoFishRuleIds.TWO_DECKS] == true && players >= 7) 2 else 1
            return GoFishConfig(
                players = players,
                decks = decks,
                handSize = if (players <= 3) 7 else 5,
                bookSize = if (rules[GoFishRuleIds.PAIRS] == true) 2 else 4,
                luckyFish = rules[GoFishRuleIds.LUCKY_FISH] ?: true,
                memoryHelper = rules[GoFishRuleIds.MEMORY_HELPER] ?: false,
            )
        }
    }
}

/** Everything in the log is public knowledge – anyone at the table saw or heard it. */
@Serializable
sealed class GoFishEvent {
    @Serializable @SerialName("ask")
    data class Ask(val asker: Int, val target: Int, val rank: Rank) : GoFishEvent()

    @Serializable @SerialName("give")
    data class Give(val from: Int, val to: Int, val rank: Rank, val count: Int) : GoFishEvent()

    @Serializable @SerialName("fish")
    data class GoFish(val asker: Int, val target: Int, val rank: Rank) : GoFishEvent()

    /** A card was drawn. Its rank is only public when it was a lucky catch that the player showed. */
    @Serializable @SerialName("draw")
    data class Draw(val player: Int, val luckyRank: Rank? = null) : GoFishEvent()

    @Serializable @SerialName("pond_empty")
    data class PondEmpty(val player: Int) : GoFishEvent()

    @Serializable @SerialName("book")
    data class Book(val player: Int, val rank: Rank) : GoFishEvent()

    /** Player had no cards at the start of their turn and drew one. */
    @Serializable @SerialName("refill")
    data class Refill(val player: Int) : GoFishEvent()

    /** Nobody else had cards to ask, so the player just drew. */
    @Serializable @SerialName("no_one")
    data class NoOneToAsk(val player: Int) : GoFishEvent()

    @Serializable @SerialName("over")
    data class Over(val winners: List<Int>) : GoFishEvent()
}

/** Why a player has to draw from the pond. */
@Serializable
enum class DrawReason { GO_FISH, EMPTY_HAND, NO_ONE_TO_ASK }

/** What the game is waiting for. Every step is a separate move so everyone can follow along. */
@Serializable
sealed class GoFishPhase {
    /** The turn owner picks someone and a rank. */
    @Serializable @SerialName("ask")
    data object Ask : GoFishPhase()

    /** [target] must hand over their [rank]s, or say "Go fish!" if they have none. */
    @Serializable @SerialName("respond")
    data class Respond(val asker: Int, val target: Int, val rank: Rank) : GoFishPhase()

    /** [player] must draw a card from the pond. */
    @Serializable @SerialName("draw")
    data class Draw(val player: Int, val reason: DrawReason, val askedRank: Rank? = null) : GoFishPhase()
}

@Serializable
data class GoFishState(
    val config: GoFishConfig,
    val hands: List<List<Card>>,
    val pond: List<Card>,
    val books: List<List<Rank>>,
    /** Whose turn it is. */
    val current: Int,
    val log: List<GoFishEvent>,
    val over: Boolean = false,
    /** Private: the card each player most recently drew (only shown to them). */
    val lastDrawn: List<Card?> = List(hands.size) { null },
    val phase: GoFishPhase = GoFishPhase.Ask,
) {
    /** The seat the game is waiting on. */
    val awaiting: Int?
        get() = when {
            over -> null
            else -> when (val p = phase) {
                GoFishPhase.Ask -> current
                is GoFishPhase.Respond -> p.target
                is GoFishPhase.Draw -> p.player
            }
        }
}

@Serializable
sealed class GoFishAction {
    @Serializable @SerialName("ask")
    data class Ask(val target: Int, val rank: Rank) : GoFishAction()

    /** Hand over every card of the asked rank. */
    @Serializable @SerialName("give")
    data object Give : GoFishAction()

    @Serializable @SerialName("go_fish")
    data object SayGoFish : GoFishAction()

    /** Take the top card of the pond. */
    @Serializable @SerialName("draw")
    data object Draw : GoFishAction()
}

@Serializable
data class GoFishView(
    val seat: Int,
    val config: GoFishConfig,
    val hand: List<Card>,
    val handCounts: List<Int>,
    val pondCount: Int,
    val books: List<List<Rank>>,
    val current: Int,
    val over: Boolean,
    val log: List<GoFishEvent>,
    val lastDrawn: Card?,
    val phase: GoFishPhase = GoFishPhase.Ask,
) {
    /** It's my turn to ask someone. */
    val myTurn get() = !over && current == seat && phase == GoFishPhase.Ask
    /** Someone has asked me and I must answer. */
    val mustAnswer get() = !over && (phase as? GoFishPhase.Respond)?.target == seat
    /** I must draw from the pond. */
    val mustDraw get() = !over && (phase as? GoFishPhase.Draw)?.player == seat
    fun canAsk(target: Int) = target != seat && handCounts.getOrElse(target) { 0 } > 0
    fun winners(): List<Int> {
        val best = books.maxOf { it.size }
        return books.indices.filter { books[it].size == best }
    }
}

object GoFish {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 10

    fun deal(config: GoFishConfig, random: Random): GoFishState {
        val deck = Decks.shuffled(config.decks, random)
        val n = config.players
        val hands = List(n) { i -> deck.subList(i * config.handSize, (i + 1) * config.handSize).sortedForHand() }
        var s = GoFishState(
            config = config,
            hands = hands,
            pond = deck.drop(n * config.handSize),
            books = List(n) { emptyList() },
            current = 0,
            log = emptyList(),
        )
        for (p in 0 until n) s = collectBooks(s, p)
        return normalize(s)
    }

    fun targets(s: GoFishState, seat: Int) = s.hands.indices.filter { it != seat && s.hands[it].isNotEmpty() }

    fun validate(s: GoFishState, seat: Int, a: GoFishAction): String? {
        if (s.over) return "The game is over"
        if (seat != s.awaiting) return "It's not your move"
        val phase = s.phase
        return when (a) {
            is GoFishAction.Ask -> when {
                phase != GoFishPhase.Ask -> "You can't ask right now"
                a.target == seat -> "You can't ask yourself"
                a.target !in s.hands.indices -> "No such player"
                s.hands[a.target].isEmpty() -> "That player has no cards"
                s.hands[seat].none { it.rank == a.rank } -> "You can only ask for a rank you hold"
                else -> null
            }
            GoFishAction.Give -> when {
                phase !is GoFishPhase.Respond -> "Nobody asked you for anything"
                s.hands[seat].none { it.rank == phase.rank } -> "You don't have any ${phase.rank.plural} – say \"Go fish!\""
                else -> null
            }
            GoFishAction.SayGoFish -> when {
                phase !is GoFishPhase.Respond -> "Nobody asked you for anything"
                s.hands[seat].any { it.rank == phase.rank } -> "No fibbing – you have ${phase.rank.plural} to hand over!"
                else -> null
            }
            GoFishAction.Draw -> when {
                phase !is GoFishPhase.Draw -> "You don't need to draw right now"
                s.pond.isEmpty() -> "The pond is empty"
                else -> null
            }
        }
    }

    fun apply(s0: GoFishState, seat: Int, a: GoFishAction): GoFishState {
        validate(s0, seat, a)?.let { throw IllegalArgumentException(it) }
        var s = s0
        when (a) {
            is GoFishAction.Ask -> {
                s = s.copy(
                    log = s.log + GoFishEvent.Ask(seat, a.target, a.rank),
                    phase = GoFishPhase.Respond(seat, a.target, a.rank),
                )
            }
            GoFishAction.Give -> {
                val p = s.phase as GoFishPhase.Respond
                val caught = s.hands[p.target].filter { it.rank == p.rank }
                s = s.copy(
                    hands = s.hands.mapIndexed { i, h ->
                        when (i) {
                            p.target -> h - caught.toSet()
                            p.asker -> (h + caught).sortedForHand()
                            else -> h
                        }
                    },
                    log = s.log + GoFishEvent.Give(p.target, p.asker, p.rank, caught.size),
                    phase = GoFishPhase.Ask, // asker goes again
                )
                s = collectBooks(s, p.asker)
            }
            GoFishAction.SayGoFish -> {
                val p = s.phase as GoFishPhase.Respond
                s = s.copy(log = s.log + GoFishEvent.GoFish(p.asker, p.target, p.rank))
                s = if (s.pond.isEmpty()) {
                    s.copy(log = s.log + GoFishEvent.PondEmpty(p.asker), current = next(s, p.asker), phase = GoFishPhase.Ask)
                } else {
                    s.copy(phase = GoFishPhase.Draw(p.asker, DrawReason.GO_FISH, p.rank))
                }
            }
            GoFishAction.Draw -> {
                val p = s.phase as GoFishPhase.Draw
                val card = s.pond.first()
                when (p.reason) {
                    DrawReason.GO_FISH -> {
                        val lucky = s.config.luckyFish && card.rank == p.askedRank
                        s = drawTop(s, seat, if (lucky) card.rank else null)
                        s = collectBooks(s, seat)
                        s = s.copy(phase = GoFishPhase.Ask, current = if (lucky) seat else next(s, seat))
                    }
                    DrawReason.EMPTY_HAND -> {
                        s = s.copy(log = s.log + GoFishEvent.Refill(seat))
                        s = drawTop(s, seat, null)
                        s = collectBooks(s, seat)
                        s = s.copy(phase = GoFishPhase.Ask) // now ask as normal
                    }
                    DrawReason.NO_ONE_TO_ASK -> {
                        s = s.copy(log = s.log + GoFishEvent.NoOneToAsk(seat))
                        s = drawTop(s, seat, null)
                        s = collectBooks(s, seat)
                        s = s.copy(phase = GoFishPhase.Ask, current = next(s, seat))
                    }
                }
            }
        }
        return normalize(s)
    }

    private fun next(s: GoFishState, seat: Int) = (seat + 1) % s.config.players

    private fun drawTop(s: GoFishState, seat: Int, luckyRank: Rank?): GoFishState {
        val card = s.pond.first()
        return s.copy(
            pond = s.pond.drop(1),
            hands = s.hands.mapIndexed { i, h -> if (i == seat) (h + card).sortedForHand() else h },
            lastDrawn = s.lastDrawn.mapIndexed { i, c -> if (i == seat) card else c },
            log = s.log + GoFishEvent.Draw(seat, luckyRank),
        )
    }

    private fun collectBooks(s0: GoFishState, seat: Int): GoFishState {
        var s = s0
        val size = s.config.bookSize
        while (true) {
            val rank = s.hands[seat].groupBy { it.rank }.entries.firstOrNull { it.value.size >= size }?.key ?: break
            val book = s.hands[seat].filter { it.rank == rank }.take(size).toSet()
            s = s.copy(
                hands = s.hands.mapIndexed { i, h -> if (i == seat) h - book else h },
                books = s.books.mapIndexed { i, b -> if (i == seat) b + rank else b },
                log = s.log + GoFishEvent.Book(seat, rank),
            )
        }
        return s
    }

    /** At the start of a turn, works out whether the player can ask or must draw (or be skipped). */
    private fun normalize(s0: GoFishState): GoFishState {
        var s = s0
        if (s.phase != GoFishPhase.Ask) return s
        repeat(1_000) {
            if (s.over) return s
            if (s.pond.isEmpty() && s.hands.all { it.isEmpty() }) return finish(s)
            val p = s.current
            when {
                s.hands[p].isEmpty() && s.pond.isNotEmpty() -> return s.copy(phase = GoFishPhase.Draw(p, DrawReason.EMPTY_HAND))
                s.hands[p].isEmpty() -> s = s.copy(current = next(s, p))
                targets(s, p).isEmpty() && s.pond.isNotEmpty() -> return s.copy(phase = GoFishPhase.Draw(p, DrawReason.NO_ONE_TO_ASK))
                // Only this player holds cards and the pond is dry: nothing more can happen.
                targets(s, p).isEmpty() -> return finish(s)
                else -> return s
            }
        }
        return finish(s)
    }

    private fun finish(s: GoFishState): GoFishState {
        val best = s.books.maxOf { it.size }
        val winners = s.books.indices.filter { s.books[it].size == best }
        return s.copy(over = true, log = s.log + GoFishEvent.Over(winners))
    }

    fun view(s: GoFishState, seat: Int) = GoFishView(
        seat = seat,
        config = s.config,
        hand = s.hands[seat],
        handCounts = s.hands.map { it.size },
        pondCount = s.pond.size,
        books = s.books,
        current = s.current,
        over = s.over,
        log = s.log,
        lastDrawn = s.lastDrawn.getOrNull(seat),
        phase = s.phase,
    )
}
