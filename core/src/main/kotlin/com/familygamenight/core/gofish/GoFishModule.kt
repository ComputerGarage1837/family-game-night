package com.familygamenight.core.gofish

import com.familygamenight.core.game.Difficulty
import com.familygamenight.core.game.GameInfo
import com.familygamenight.core.game.GameResult
import com.familygamenight.core.game.OptionalRule
import com.familygamenight.core.game.TypedGameModule
import kotlin.random.Random

object GoFishModule : TypedGameModule<GoFishState, GoFishAction, GoFishView>(
    GoFishState.serializer(), GoFishAction.serializer(), GoFishView.serializer(),
) {
    override val info = GameInfo(
        id = "go_fish",
        name = "Go Fish",
        tagline = "Ask, fish and collect sets of four",
        minPlayers = GoFish.MIN_PLAYERS,
        maxPlayers = GoFish.MAX_PLAYERS,
        hiddenHands = true,
        aiHasSkillLevels = true, // it's a memory game, so skill genuinely matters
        howToPlay = listOf(
            "Everyone is dealt 7 cards (5 each with 4 or more players). The rest are spread face down as the pond.",
            "On your turn, pick a card rank you hold (say, sevens) and ask another player for it.",
            "If they have any, they must hand over all of them (tap the glowing cards) and you go again.",
            "If not, they say \"Go fish!\" and you draw one card from the pond (tap the pond). Then play passes left.",
            "Whenever you collect all four cards of a rank (a book), you lay it down.",
            "If your hand runs empty on your turn you draw one card from the pond.",
            "When every book has been made, the player with the most books wins.",
            "Tip: listen to what everyone asks for – it tells you what they're holding!",
        ),
        optionalRules = listOf(
            OptionalRule(
                GoFishRuleIds.LUCKY_FISH, "Lucky catch",
                "If you fish up the very rank you asked for, show it and take another turn.", true,
            ),
            OptionalRule(
                GoFishRuleIds.PAIRS, "Pairs instead of fours",
                "Collect pairs rather than sets of four – quicker and easier for little ones.", false,
            ),
            OptionalRule(
                GoFishRuleIds.TWO_DECKS, "Two decks for big groups",
                "With 7 or more players, shuffle two decks together so the pond doesn't run dry.", true,
            ),
            OptionalRule(
                GoFishRuleIds.MEMORY_HELPER, "Memory helper",
                "Show a list of who has asked for what. Handy for younger players (it's what Hard AI remembers anyway).", false,
            ),
        ),
    )

    override fun newTyped(playerCount: Int, rules: Map<String, Boolean>, random: Random) =
        GoFish.deal(GoFishConfig.from(playerCount, rules), random)

    override fun currentSeatTyped(state: GoFishState) = state.awaiting
    override fun turnOwnerTyped(state: GoFishState) = if (state.over) null else state.current

    override fun forcedResponseTyped(state: GoFishState, seat: Int): GoFishAction? {
        val p = state.phase as? GoFishPhase.Respond ?: return null
        if (p.target != seat || state.over) return null
        return if (state.hands[seat].any { it.rank == p.rank }) GoFishAction.Give else GoFishAction.SayGoFish
    }

    override fun isForcedMoveTyped(state: GoFishState, seat: Int) = state.phase != GoFishPhase.Ask
    override fun validateTyped(state: GoFishState, seat: Int, action: GoFishAction) = GoFish.validate(state, seat, action)
    override fun applyTyped(state: GoFishState, seat: Int, action: GoFishAction) = GoFish.apply(state, seat, action)
    override fun viewTyped(state: GoFishState, seat: Int) = GoFish.view(state, seat)
    override fun aiTyped(view: GoFishView, difficulty: Difficulty, random: Random) = GoFishAi.choose(view, difficulty, random)

    override fun resultTyped(state: GoFishState): GameResult? {
        if (!state.over) return null
        val scores = state.books.map { it.size }
        val best = scores.max()
        return GameResult(scores, scores.indices.filter { scores[it] == best }, "books")
    }
}
