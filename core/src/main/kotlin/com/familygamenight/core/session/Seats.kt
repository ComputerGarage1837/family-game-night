package com.familygamenight.core.session

import com.familygamenight.core.game.Difficulty
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
enum class SeatKind {
    /** A person playing on the host device (several = pass-and-play). */
    LOCAL,
    /** A person playing on another device over the LAN. */
    REMOTE,
    AI,
}

@Serializable
data class Seat(
    val index: Int,
    val profileId: String,
    val name: String,
    val kind: SeatKind,
    val difficulty: Difficulty? = null,
    val connected: Boolean = true,
    val leftOnPurpose: Boolean = false,
) {
    val isMissing get() = kind == SeatKind.REMOTE && !connected
}

@Serializable
data class SavedGame(
    val id: String,
    val gameId: String,
    val title: String,
    val savedAtMillis: Long,
    val lan: Boolean,
    val rules: Map<String, Boolean>,
    val seats: List<Seat>,
    val state: JsonElement,
)

/** Names for AI players, in keeping with the castle. */
object AiNames {
    val all = listOf(
        "Sir Pip", "Lady Wren", "Merlin", "Sir Bramble", "Lady Rowan",
        "Squire Tom", "Dame Holly", "Friar Tuck", "Sir Gawain", "Lady Elaine",
    )

    fun pick(taken: Collection<String>): String =
        all.firstOrNull { it !in taken } ?: "Knight ${taken.size + 1}"
}
