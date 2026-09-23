package com.familygamenight.core.game

import kotlinx.serialization.Serializable

/** How well an AI opponent plays. Only offered for games where skill (e.g. memory) matters. */
@Serializable
enum class Difficulty(val label: String, val blurb: String) {
    EASY("Easy", "Plays like a young child – forgets a lot"),
    MEDIUM("Normal", "Plays like a teenager – remembers recent moves"),
    HARD("Hard", "Plays like an adult – remembers everything it has seen"),
}

/** A house rule players can switch on or off before a game. */
@Serializable
data class OptionalRule(
    val id: String,
    val title: String,
    val description: String,
    val defaultOn: Boolean,
)

@Serializable
data class GameInfo(
    val id: String,
    val name: String,
    val tagline: String,
    val minPlayers: Int,
    val maxPlayers: Int,
    /** True when players must not see each other's cards (so one shared screen needs pass-and-play). */
    val hiddenHands: Boolean,
    /** False for pure games of chance: the AI then has no difficulty levels. */
    val aiHasSkillLevels: Boolean,
    val howToPlay: List<String>,
    val optionalRules: List<OptionalRule>,
) {
    fun defaultRules(): Map<String, Boolean> = optionalRules.associate { it.id to it.defaultOn }
}
