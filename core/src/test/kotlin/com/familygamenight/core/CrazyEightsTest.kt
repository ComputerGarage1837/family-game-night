package com.familygamenight.core

import com.familygamenight.core.cards.Card
import com.familygamenight.core.cards.Rank
import com.familygamenight.core.cards.Suit
import com.familygamenight.core.crazyeights.CrazyEights
import com.familygamenight.core.crazyeights.CrazyEightsAction
import com.familygamenight.core.crazyeights.CrazyEightsAi
import com.familygamenight.core.crazyeights.CrazyEightsConfig
import com.familygamenight.core.crazyeights.CrazyEightsPhase
import com.familygamenight.core.crazyeights.CrazyEightsRuleIds
import com.familygamenight.core.crazyeights.CrazyEightsState
import com.familygamenight.core.game.Difficulty
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CrazyEightsTest {
    private fun playOut(s0: CrazyEightsState, diffs: List<Difficulty>, random: Random): CrazyEightsState {
        var s = s0
        val total = 52 * s.config.decks
        var moves = 0
        while (!s.over) {
            assertEquals(total, s.hands.sumOf { it.size } + s.stock.size + s.discard.size, "cards must be conserved")
            val seat = s.current
            val a = CrazyEightsAi.choose(CrazyEights.view(s, seat), diffs[seat], random)
            assertNull(CrazyEights.validate(s, seat, a), "AI must make legal moves: $a in ${s.phase}")
            s = CrazyEights.apply(s, seat, a)
            assertTrue(++moves < 50_000, "game must end")
        }
        assertTrue(s.winners.isNotEmpty())
        return s
    }

    @Test
    fun everyPlayerCountAndRuleComboFinishes() {
        val random = Random(3)
        val ids = listOf(CrazyEightsRuleIds.DRAW_UNTIL_PLAYABLE, CrazyEightsRuleIds.TWOS_DRAW_TWO, CrazyEightsRuleIds.QUEENS_SKIP, CrazyEightsRuleIds.ACES_REVERSE)
        for (players in 2..10) for (mask in 0 until 16) {
            val rules = ids.mapIndexed { i, id -> id to (mask shr i and 1 == 1) }.toMap()
            val s = CrazyEights.deal(CrazyEightsConfig.from(players, rules), random)
            assertTrue(s.top.rank != Rank.EIGHT, "never start on an eight")
            playOut(s, List(players) { Difficulty.entries[it % 3] }, random)
        }
    }

    private fun c(r: Rank, s: Suit) = Card(r, s)

    private fun state(hands: List<List<Card>>, top: Card, rules: Map<String, Boolean> = emptyMap(), stock: List<Card>? = null) =
        CrazyEightsState(
            config = CrazyEightsConfig.from(hands.size, rules),
            hands = hands,
            stock = stock ?: List(10) { c(Rank.entries[it % 13], Suit.CLUBS) },
            discard = listOf(top),
            activeSuit = top.suit,
            current = 0,
        )

    @Test
    fun matchingAndEights() {
        val s = state(listOf(listOf(c(Rank.FIVE, Suit.HEARTS), c(Rank.EIGHT, Suit.SPADES), c(Rank.KING, Suit.CLUBS)), listOf(c(Rank.TWO, Suit.CLUBS))), c(Rank.FIVE, Suit.DIAMONDS))
        assertNull(CrazyEights.validate(s, 0, CrazyEightsAction.Play(c(Rank.FIVE, Suit.HEARTS))))
        assertNotNull(CrazyEights.validate(s, 0, CrazyEightsAction.Play(c(Rank.KING, Suit.CLUBS))))
        assertNotNull(CrazyEights.validate(s, 0, CrazyEightsAction.Play(c(Rank.EIGHT, Suit.SPADES))), "eight needs a suit")
        val after = CrazyEights.apply(s, 0, CrazyEightsAction.Play(c(Rank.EIGHT, Suit.SPADES), Suit.CLUBS))
        assertEquals(Suit.CLUBS, after.activeSuit)
        assertEquals(1, after.current)
        assertNull(CrazyEights.validate(after, 1, CrazyEightsAction.Play(c(Rank.TWO, Suit.CLUBS))))
        assertNotNull(CrazyEights.validate(after, 1, CrazyEightsAction.Pass), "must draw before passing")
    }

    @Test
    fun drawOneThenPlayOrPass() {
        val s = state(listOf(listOf(c(Rank.KING, Suit.CLUBS)), listOf(c(Rank.TWO, Suit.CLUBS))), c(Rank.FIVE, Suit.DIAMONDS),
            stock = listOf(c(Rank.NINE, Suit.SPADES), c(Rank.THREE, Suit.HEARTS)))
        val drawn = CrazyEights.apply(s, 0, CrazyEightsAction.Draw)
        assertEquals(CrazyEightsPhase.AfterDraw, drawn.phase)
        assertNotNull(CrazyEights.validate(drawn, 0, CrazyEightsAction.Draw), "only one draw by default")
        val passed = CrazyEights.apply(drawn, 0, CrazyEightsAction.Pass)
        assertEquals(1, passed.current)
    }

    @Test
    fun drawUntilPlayableHouseRule() {
        val s = state(listOf(listOf(c(Rank.KING, Suit.CLUBS)), listOf(c(Rank.TWO, Suit.CLUBS))), c(Rank.FIVE, Suit.DIAMONDS),
            rules = mapOf(CrazyEightsRuleIds.DRAW_UNTIL_PLAYABLE to true),
            stock = listOf(c(Rank.NINE, Suit.SPADES), c(Rank.THREE, Suit.DIAMONDS)))
        val once = CrazyEights.apply(s, 0, CrazyEightsAction.Draw)
        assertEquals(CrazyEightsPhase.Play, once.phase)
        assertNotNull(CrazyEights.validate(once, 0, CrazyEightsAction.Pass), "keep drawing")
        val twice = CrazyEights.apply(once, 0, CrazyEightsAction.Draw)
        assertNull(CrazyEights.validate(twice, 0, CrazyEightsAction.Play(c(Rank.THREE, Suit.DIAMONDS))))
    }

    @Test
    fun specialCards() {
        val hands = listOf(
            listOf(c(Rank.TWO, Suit.HEARTS), c(Rank.QUEEN, Suit.HEARTS), c(Rank.ACE, Suit.HEARTS), c(Rank.KING, Suit.SPADES)),
            listOf(c(Rank.SIX, Suit.CLUBS)),
            listOf(c(Rank.SEVEN, Suit.CLUBS)),
        )
        val all = mapOf(CrazyEightsRuleIds.TWOS_DRAW_TWO to true, CrazyEightsRuleIds.QUEENS_SKIP to true, CrazyEightsRuleIds.ACES_REVERSE to true)
        val base = state(hands, c(Rank.FIVE, Suit.HEARTS), all)

        val two = CrazyEights.apply(base, 0, CrazyEightsAction.Play(c(Rank.TWO, Suit.HEARTS)))
        assertEquals(1, two.current)
        assertEquals(CrazyEightsPhase.Penalty(2), two.phase)
        assertNotNull(CrazyEights.validate(two, 1, CrazyEightsAction.Play(c(Rank.SIX, Suit.CLUBS))))
        val d1 = CrazyEights.apply(two, 1, CrazyEightsAction.Draw)
        val d2 = CrazyEights.apply(d1, 1, CrazyEightsAction.Draw)
        assertEquals(3, d2.hands[1].size)
        assertEquals(2, d2.current, "drawing two ends the turn")

        val queen = CrazyEights.apply(base, 0, CrazyEightsAction.Play(c(Rank.QUEEN, Suit.HEARTS)))
        assertEquals(2, queen.current, "player 1 is skipped")

        val ace = CrazyEights.apply(base, 0, CrazyEightsAction.Play(c(Rank.ACE, Suit.HEARTS)))
        assertEquals(-1, ace.direction)
        assertEquals(2, ace.current, "play goes the other way")
    }

    @Test
    fun reshufflesWhenStockRunsOut() {
        val s = state(listOf(listOf(c(Rank.KING, Suit.CLUBS)), listOf(c(Rank.TWO, Suit.CLUBS))), c(Rank.FIVE, Suit.DIAMONDS), stock = emptyList())
            .copy(discard = listOf(c(Rank.THREE, Suit.SPADES), c(Rank.FOUR, Suit.SPADES), c(Rank.FIVE, Suit.DIAMONDS)))
        val drawn = CrazyEights.apply(s, 0, CrazyEightsAction.Draw)
        assertEquals(listOf(c(Rank.FIVE, Suit.DIAMONDS)), drawn.discard)
        assertEquals(2, drawn.hands[0].size)
        assertEquals(1, drawn.stock.size)
    }

    @Test
    fun hardBeatsEasy() {
        val random = Random(8)
        var hard = 0
        var easy = 0
        repeat(600) { g ->
            val s = CrazyEights.deal(CrazyEightsConfig.from(2, emptyMap()), random)
            val hardSeat = g % 2
            val end = playOut(s, List(2) { if (it == hardSeat) Difficulty.HARD else Difficulty.EASY }, random)
            if (end.winners.size == 1) if (end.winners[0] == hardSeat) hard++ else easy++
        }
        println("Crazy Eights: Hard $hard vs Easy $easy")
        assertTrue(hard > easy * 1.15, "Hard should beat Easy ($hard vs $easy)")
    }
}
