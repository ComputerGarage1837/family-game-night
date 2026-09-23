package com.familygamenight.core.crazyeights

import com.familygamenight.core.game.Difficulty
import com.familygamenight.core.game.GameInfo
import com.familygamenight.core.game.GameResult
import com.familygamenight.core.game.OptionalRule
import com.familygamenight.core.game.TypedGameModule
import kotlin.random.Random

object CrazyEightsModule : TypedGameModule<CrazyEightsState, CrazyEightsAction, CrazyEightsView>(
    CrazyEightsState.serializer(), CrazyEightsAction.serializer(), CrazyEightsView.serializer(),
) {
    override val info = GameInfo(
        id = "crazy_eights",
        name = "Crazy Eights",
        tagline = "Match the suit or number – eights are wild",
        minPlayers = CrazyEights.MIN_PLAYERS,
        maxPlayers = CrazyEights.MAX_PLAYERS,
        hiddenHands = true,
        aiHasSkillLevels = true, // choosing what to play and which suit to call is real strategy
        howToPlay = listOf(
            "Everyone gets 5 cards (7 each with 2 players). One card is turned face up to start the pile.",
            "On your turn, play a card that matches the top card's suit or number – the cards you can play glow.",
            "Eights are wild: play one any time and call the suit the next player has to follow.",
            "Can't (or don't want to) play? Tap the deck to draw a card. If it fits you can play it, otherwise pass.",
            "If the deck runs out, the pile is shuffled to make a new one.",
            "The first player to get rid of all their cards wins!",
            "Tip: watch who has to draw – it means they don't have that suit.",
        ),
        optionalRules = listOf(
            OptionalRule(
                CrazyEightsRuleIds.DRAW_UNTIL_PLAYABLE, "Draw until you can play",
                "Instead of drawing one card, keep drawing until you get one you can play.", false,
            ),
            OptionalRule(
                CrazyEightsRuleIds.TWOS_DRAW_TWO, "Twos: draw two",
                "Playing a two makes the next player draw two cards and miss their go.", false,
            ),
            OptionalRule(
                CrazyEightsRuleIds.QUEENS_SKIP, "Queens skip",
                "Playing a queen skips the next player.", false,
            ),
            OptionalRule(
                CrazyEightsRuleIds.ACES_REVERSE, "Aces reverse",
                "Playing an ace reverses the direction of play.", false,
            ),
        ),
    )

    override fun newTyped(playerCount: Int, rules: Map<String, Boolean>, random: Random) =
        CrazyEights.deal(CrazyEightsConfig.from(playerCount, rules), random)

    override fun currentSeatTyped(state: CrazyEightsState) = if (state.over) null else state.current
    override fun validateTyped(state: CrazyEightsState, seat: Int, action: CrazyEightsAction) = CrazyEights.validate(state, seat, action)
    override fun applyTyped(state: CrazyEightsState, seat: Int, action: CrazyEightsAction) = CrazyEights.apply(state, seat, action)
    override fun viewTyped(state: CrazyEightsState, seat: Int) = CrazyEights.view(state, seat)
    override fun aiTyped(view: CrazyEightsView, difficulty: Difficulty, random: Random) = CrazyEightsAi.choose(view, difficulty, random)
    override fun isForcedMoveTyped(state: CrazyEightsState, seat: Int) = state.phase is CrazyEightsPhase.Penalty

    override fun resultTyped(state: CrazyEightsState): GameResult? {
        if (!state.over) return null
        return GameResult(state.hands.map { CrazyEights.penaltyPoints(it) }, state.winners, "points left")
    }
}
