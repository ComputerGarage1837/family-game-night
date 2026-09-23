package com.familygamenight.core.crazyeights

import com.familygamenight.core.cards.Card
import com.familygamenight.core.cards.Rank
import com.familygamenight.core.cards.Suit
import com.familygamenight.core.game.Difficulty
import kotlin.random.Random

/**
 * Crazy Eights opponent. Like the Go Fish AI it only sees its own hand and the table.
 * - Easy: plays any card that fits, throws eights away early, calls a random suit.
 * - Normal: keeps eights for emergencies and calls the suit it holds most of.
 * - Hard: also notices which suits each player couldn't follow (they had to draw) and steers
 *   the game onto those suits, especially against whoever is close to going out.
 */
object CrazyEightsAi {

    fun choose(view: CrazyEightsView, difficulty: Difficulty, random: Random): CrazyEightsAction {
        if (view.phase is CrazyEightsPhase.Penalty) {
            return if (view.canDraw) CrazyEightsAction.Draw else CrazyEightsAction.Pass
        }
        val playable = view.playable()
        if (playable.isEmpty()) {
            return when {
                view.phase == CrazyEightsPhase.AfterDraw -> CrazyEightsAction.Pass
                view.canDraw -> CrazyEightsAction.Draw
                else -> CrazyEightsAction.Pass
            }
        }
        val card = when (difficulty) {
            Difficulty.EASY -> playable.random(random)
            Difficulty.MEDIUM -> pickSensible(view, playable, random, emptyMap())
            Difficulty.HARD -> pickSensible(view, playable, random, voidSuits(view))
        }
        val suit = if (card.rank == Rank.EIGHT) callSuit(view, card, difficulty, random) else null
        return CrazyEightsAction.Play(card, suit)
    }

    private fun pickSensible(view: CrazyEightsView, playable: List<Card>, random: Random, voids: Map<Int, Set<Suit>>): Card {
        val next = nextPlayer(view)
        val nextIsClose = view.handCounts[next] <= 2
        val suitCounts = view.hand.filter { it.rank != Rank.EIGHT }.groupingBy { it.suit }.eachCount()
        val nonEights = playable.filter { it.rank != Rank.EIGHT }
        // Keep eights as a get-out-of-jail card unless we're about to go out.
        val pool = if (nonEights.isNotEmpty() && view.hand.size > 2) nonEights else playable
        return pool.maxBy { c ->
            var score = (suitCounts[c.suit] ?: 0) * 1.0 // stay in the suit we hold most of
            if (c.rank != Rank.EIGHT && c.suit in (voids[next] ?: emptySet())) score += 3.0
            if (nextIsClose) {
                if (view.config.twosDrawTwo && c.rank == Rank.TWO) score += 4.0
                if (view.config.queensSkip && c.rank == Rank.QUEEN) score += 4.0
            }
            // Shed high-scoring picture cards first.
            if (c.rank in listOf(Rank.KING, Rank.QUEEN, Rank.JACK, Rank.TEN)) score += 0.4
            score + random.nextDouble() * 0.3
        }
    }

    private fun callSuit(view: CrazyEightsView, eight: Card, difficulty: Difficulty, random: Random): Suit {
        if (difficulty == Difficulty.EASY) return Suit.entries.random(random)
        val rest = view.hand.filter { it != eight && it.rank != Rank.EIGHT }
        val counts = rest.groupingBy { it.suit }.eachCount()
        val nextVoids = if (difficulty == Difficulty.HARD) voidSuits(view)[nextPlayer(view)] ?: emptySet() else emptySet()
        return Suit.entries.maxBy { (counts[it] ?: 0) * 2.0 + (if (it in nextVoids) 1.5 else 0.0) + random.nextDouble() * 0.1 }
    }

    private fun nextPlayer(view: CrazyEightsView) = Math.floorMod(view.seat + view.direction, view.config.players)

    /**
     * Suits each opponent seemingly can't follow: they drew instead of playing while that suit was
     * wanted. Forgotten as soon as they play that suit again.
     */
    fun voidSuits(view: CrazyEightsView): Map<Int, Set<Suit>> {
        val voids = HashMap<Int, MutableSet<Suit>>()
        var wanted: Suit? = null
        for (e in view.log) {
            when (e) {
                is CrazyEightsEvent.Played -> {
                    voids[e.player]?.remove(e.card.suit)
                    wanted = e.calledSuit ?: e.card.suit
                }
                is CrazyEightsEvent.Drew -> if (!e.penalty && e.player != view.seat) {
                    wanted?.let { voids.getOrPut(e.player) { mutableSetOf() }.add(it) }
                }
                else -> Unit
            }
        }
        return voids
    }
}
