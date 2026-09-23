package com.familygamenight.core.gofish

import com.familygamenight.core.cards.Rank
import com.familygamenight.core.game.Difficulty
import kotlin.random.Random

/**
 * Go Fish opponent. It never cheats: it only looks at a [GoFishView], i.e. its own hand plus the
 * public table talk. Difficulty is purely about how much of that table talk it remembers and how
 * cleverly it uses it – like a real child, teenager or adult.
 */
object GoFishAi {

    private data class Memory(val span: Int, val recall: Double)

    private fun memoryFor(d: Difficulty) = when (d) {
        Difficulty.EASY -> Memory(span = 4, recall = 0.45)
        Difficulty.MEDIUM -> Memory(span = 24, recall = 0.85)
        Difficulty.HARD -> Memory(span = Int.MAX_VALUE, recall = 1.0)
    }

    /** What the AI believes about one opponent and one rank. */
    enum class Belief { HAS, HAS_NONE, UNKNOWN }

    /**
     * Rebuilds beliefs from the public log. A fact is only remembered if it's recent enough for the
     * difficulty and passes a (stable) recall roll, so the same AI doesn't flip-flop between turns.
     */
    fun beliefs(view: GoFishView, difficulty: Difficulty): Map<Pair<Int, Rank>, Belief> {
        val mem = memoryFor(difficulty)
        val now = view.log.size
        // Newest fact wins: walk the log and overwrite.
        val facts = HashMap<Pair<Int, Rank>, Pair<Belief, Int>>()
        fun remember(p: Int, r: Rank, b: Belief, idx: Int) {
            if (p != view.seat) facts[p to r] = b to idx
        }
        view.log.forEachIndexed { idx, e ->
            when (e) {
                is GoFishEvent.Ask -> remember(e.asker, e.rank, Belief.HAS, idx)
                is GoFishEvent.Give -> {
                    remember(e.from, e.rank, Belief.HAS_NONE, idx)
                    remember(e.to, e.rank, Belief.HAS, idx)
                }
                is GoFishEvent.GoFish -> remember(e.target, e.rank, Belief.HAS_NONE, idx)
                is GoFishEvent.Draw -> {
                    if (e.luckyRank != null) remember(e.player, e.luckyRank, Belief.HAS, idx)
                    else {
                        // An unknown card: any "has none" belief about that player is now shaky.
                        // Only the sharper players realise that.
                        if (difficulty != Difficulty.EASY) {
                            facts.entries.filter { it.key.first == e.player && it.value.first == Belief.HAS_NONE }
                                .forEach { facts[it.key] = Belief.UNKNOWN to idx }
                        }
                    }
                }
                is GoFishEvent.Book -> remember(e.player, e.rank, Belief.HAS_NONE, idx)
                else -> Unit
            }
        }
        val result = HashMap<Pair<Int, Rank>, Belief>()
        for ((key, fact) in facts) {
            val (belief, idx) = fact
            val age = now - idx
            val recalled = age <= mem.span && stableRoll(view.seat, idx, key.second) < mem.recall
            result[key] = if (recalled) belief else Belief.UNKNOWN
        }
        return result
    }

    fun choose(view: GoFishView, difficulty: Difficulty, random: Random): GoFishAction {
        when (val phase = view.phase) {
            // Answering and drawing aren't choices – the AI plays fair like everyone else.
            is GoFishPhase.Respond ->
                return if (view.hand.any { it.rank == phase.rank }) GoFishAction.Give else GoFishAction.SayGoFish
            is GoFishPhase.Draw -> return GoFishAction.Draw
            GoFishPhase.Ask -> Unit
        }
        val targets = view.handCounts.indices.filter { view.canAsk(it) }
        val myCounts = view.hand.groupingBy { it.rank }.eachCount()
        require(targets.isNotEmpty() && myCounts.isNotEmpty()) { "AI asked to move with no legal move" }

        // Little ones sometimes just pick anything.
        if (difficulty == Difficulty.EASY && random.nextDouble() < 0.35) {
            return GoFishAction.Ask(targets.random(random), myCounts.keys.random(random))
        }

        val beliefs = beliefs(view, difficulty)

        var best: GoFishAction.Ask? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for (rank in myCounts.keys) {
            for (t in targets) {
                val score = when (beliefs[t to rank] ?: Belief.UNKNOWN) {
                    Belief.HAS -> 10.0 + myCounts.getValue(rank)
                    Belief.HAS_NONE -> -5.0
                    Belief.UNKNOWN -> when (difficulty) {
                        // Chase the ranks closest to a book, from players holding the most cards.
                        Difficulty.HARD -> myCounts.getValue(rank) + view.handCounts[t] * HARD_HAND_WEIGHT
                        Difficulty.MEDIUM -> myCounts.getValue(rank) * 0.5 + view.handCounts[t] * 0.1
                        Difficulty.EASY -> 0.0
                    }
                } + random.nextDouble() * jitter(difficulty)
                if (score > bestScore) {
                    bestScore = score
                    best = GoFishAction.Ask(t, rank)
                }
            }
        }
        return best!!
    }

    private const val HARD_HAND_WEIGHT = 0.15

    private fun jitter(d: Difficulty) = when (d) {
        Difficulty.EASY -> 4.0
        Difficulty.MEDIUM -> 1.5
        Difficulty.HARD -> 0.2
    }

    private fun stableRoll(seat: Int, idx: Int, rank: Rank): Double {
        var h = seat * 1_000_003L + idx * 7_919L + rank.ordinal * 104_729L
        h = h xor (h ushr 33); h *= -0x61c8864680b583ebL; h = h xor (h ushr 29)
        return ((h ushr 11).toDouble() / (1L shl 53).toDouble()).let { it - kotlin.math.floor(it) }
    }
}
