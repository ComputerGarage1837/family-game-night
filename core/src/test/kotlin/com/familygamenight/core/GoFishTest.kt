package com.familygamenight.core

import com.familygamenight.core.cards.Card
import com.familygamenight.core.cards.Rank
import com.familygamenight.core.cards.Suit
import com.familygamenight.core.game.Difficulty
import com.familygamenight.core.game.GameJson
import com.familygamenight.core.gofish.GoFish
import com.familygamenight.core.gofish.GoFishAction
import com.familygamenight.core.gofish.GoFishAi
import com.familygamenight.core.gofish.GoFishConfig
import com.familygamenight.core.gofish.GoFishEvent
import com.familygamenight.core.gofish.GoFishModule
import com.familygamenight.core.gofish.GoFishRuleIds
import com.familygamenight.core.gofish.GoFishState
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoFishTest {

    private fun playOut(state0: GoFishState, difficulties: List<Difficulty>, random: Random): GoFishState {
        var s = state0
        val total = 52 * s.config.decks
        var moves = 0
        while (!s.over) {
            val cardsInPlay = s.hands.sumOf { it.size } + s.pond.size + s.books.sumOf { it.size } * s.config.bookSize
            assertEquals(total, cardsInPlay, "cards must be conserved")
            val seat = s.current
            val view = GoFish.view(s, seat)
            assertEquals(s.hands[seat], view.hand)
            val a = GoFishAi.choose(view, difficulties[seat], random)
            assertNull(GoFish.validate(s, seat, a), "AI must only make legal moves")
            s = GoFish.apply(s, seat, a)
            assertTrue(++moves < 5_000, "game must end")
        }
        assertTrue(s.hands.all { it.isEmpty() } && s.pond.isEmpty())
        assertEquals(total / s.config.bookSize, s.books.sumOf { it.size })
        assertTrue(s.log.last() is GoFishEvent.Over)
        return s
    }

    @Test
    fun everyPlayerCountAndRuleComboFinishes() {
        val random = Random(1)
        for (players in 2..10) for (pairs in listOf(false, true)) for (two in listOf(false, true)) for (lucky in listOf(false, true)) {
            val rules = mapOf(GoFishRuleIds.PAIRS to pairs, GoFishRuleIds.TWO_DECKS to two, GoFishRuleIds.LUCKY_FISH to lucky)
            repeat(3) {
                val s = GoFish.deal(GoFishConfig.from(players, rules), random)
                playOut(s, List(players) { Difficulty.entries[it % 3] }, random)
            }
        }
    }

    @Test
    fun handSizesAndDecks() {
        assertEquals(7, GoFishConfig.from(3, emptyMap()).handSize)
        assertEquals(5, GoFishConfig.from(4, emptyMap()).handSize)
        assertEquals(1, GoFishConfig.from(6, mapOf(GoFishRuleIds.TWO_DECKS to true)).decks)
        assertEquals(2, GoFishConfig.from(7, mapOf(GoFishRuleIds.TWO_DECKS to true)).decks)
        assertEquals(1, GoFishConfig.from(10, mapOf(GoFishRuleIds.TWO_DECKS to false)).decks)
        assertFailsWith<IllegalArgumentException> { GoFishConfig.from(11, emptyMap()) }
    }

    private fun c(r: Rank, s: Suit) = Card(r, s)

    @Test
    fun successfulAskTransfersAllAndKeepsTurn() {
        val cfg = GoFishConfig.from(2, emptyMap())
        val s = GoFishState(
            config = cfg,
            hands = listOf(
                listOf(c(Rank.SEVEN, Suit.CLUBS), c(Rank.TWO, Suit.CLUBS)),
                listOf(c(Rank.SEVEN, Suit.HEARTS), c(Rank.SEVEN, Suit.SPADES), c(Rank.NINE, Suit.CLUBS)),
            ),
            pond = listOf(c(Rank.KING, Suit.CLUBS)),
            books = listOf(emptyList(), emptyList()),
            current = 0,
            log = emptyList(),
        )
        val after = GoFish.apply(s, 0, GoFishAction(1, Rank.SEVEN))
        assertEquals(3, after.hands[0].count { it.rank == Rank.SEVEN })
        assertEquals(listOf(c(Rank.NINE, Suit.CLUBS)), after.hands[1])
        assertEquals(0, after.current)
        assertTrue(after.log.any { it is GoFishEvent.Give && it.count == 2 })
    }

    @Test
    fun goFishDrawsAndPassesUnlessLucky() {
        val cfg = GoFishConfig.from(2, emptyMap())
        fun state(top: Card) = GoFishState(
            config = cfg,
            hands = listOf(listOf(c(Rank.SEVEN, Suit.CLUBS)), listOf(c(Rank.NINE, Suit.CLUBS))),
            pond = listOf(top, c(Rank.TWO, Suit.HEARTS)),
            books = listOf(emptyList(), emptyList()),
            current = 0,
            log = emptyList(),
        )
        val unlucky = GoFish.apply(state(c(Rank.KING, Suit.CLUBS)), 0, GoFishAction(1, Rank.SEVEN))
        assertEquals(1, unlucky.current)
        assertEquals(c(Rank.KING, Suit.CLUBS), unlucky.lastDrawn[0])
        // The drawn card is private: the public log must not reveal its rank.
        assertNull((unlucky.log.last { it is GoFishEvent.Draw } as GoFishEvent.Draw).luckyRank)

        val lucky = GoFish.apply(state(c(Rank.SEVEN, Suit.HEARTS)), 0, GoFishAction(1, Rank.SEVEN))
        assertEquals(0, lucky.current)
        assertEquals(Rank.SEVEN, (lucky.log.last { it is GoFishEvent.Draw } as GoFishEvent.Draw).luckyRank)
    }

    @Test
    fun illegalMovesAreRejected() {
        val s = GoFish.deal(GoFishConfig.from(3, emptyMap()), Random(5))
        val seat = s.current
        val notHeld = Rank.entries.first { r -> s.hands[seat].none { it.rank == r } }
        assertNotNull(GoFish.validate(s, seat, GoFishAction((seat + 1) % 3, notHeld)))
        assertNotNull(GoFish.validate(s, seat, GoFishAction(seat, s.hands[seat].first().rank)))
        assertNotNull(GoFish.validate(s, (seat + 1) % 3, GoFishAction(seat, s.hands[(seat + 1) % 3].first().rank)))
    }

    @Test
    fun viewNeverLeaksOtherHands() {
        val module = GoFishModule
        val state = module.newGame(4, module.info.defaultRules(), Random(9))
        val s = module.decodeState(state)
        val viewJson = GameJson.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), module.view(state, 2))
        for (other in listOf(0, 1, 3)) for (card in s.hands[other]) {
            if (s.hands[2].contains(card)) continue
            assertTrue(!viewJson.contains("\"rank\":\"${card.rank}\",\"suit\":\"${card.suit}\""), "view leaked $card")
        }
    }

    @Test
    fun hardAiRemembersAndBeatsEasy() {
        val random = Random(42)
        var hardWins = 0
        var easyWins = 0
        repeat(400) { g ->
            val s = GoFish.deal(GoFishConfig.from(2, emptyMap()), random)
            val hardSeat = g % 2
            val diffs = List(2) { if (it == hardSeat) Difficulty.HARD else Difficulty.EASY }
            val end = playOut(s, diffs, random)
            val w = end.books.indices.filter { end.books[it].size == end.books.maxOf { b -> b.size } }
            if (w.size == 1) if (w[0] == hardSeat) hardWins++ else easyWins++
        }
        println("Hard $hardWins vs Easy $easyWins")
        assertTrue(hardWins > easyWins * 1.3, "Hard should clearly beat Easy ($hardWins vs $easyWins)")
    }

    @Test
    fun hardAiAsksForRankItHeardAbout() {
        val cfg = GoFishConfig.from(3, emptyMap())
        val s = GoFishState(
            config = cfg,
            hands = listOf(
                listOf(c(Rank.FIVE, Suit.CLUBS), c(Rank.JACK, Suit.CLUBS), c(Rank.TWO, Suit.CLUBS)),
                listOf(c(Rank.JACK, Suit.HEARTS), c(Rank.THREE, Suit.HEARTS)),
                listOf(c(Rank.NINE, Suit.HEARTS), c(Rank.FOUR, Suit.HEARTS)),
            ),
            pond = List(10) { c(Rank.KING, Suit.entries[it % 4]) },
            books = List(3) { emptyList() },
            current = 0,
            // Player 1 asked player 2 for jacks earlier – so player 1 has a jack.
            log = listOf(GoFishEvent.Ask(1, 2, Rank.JACK), GoFishEvent.GoFish(1, 2, Rank.JACK)),
        )
        repeat(20) {
            assertEquals(GoFishAction(1, Rank.JACK), GoFishAi.choose(GoFish.view(s, 0), Difficulty.HARD, Random(it)))
        }
    }

    @Test
    fun stateRoundTripsThroughJson() {
        val m = GoFishModule
        val st = m.newGame(5, m.info.defaultRules(), Random(3))
        val text = GameJson.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), st)
        val back = GameJson.decodeFromString(kotlinx.serialization.json.JsonElement.serializer(), text)
        assertEquals(m.decodeState(st), m.decodeState(back))
    }
}
