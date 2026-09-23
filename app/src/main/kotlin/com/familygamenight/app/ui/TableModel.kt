package com.familygamenight.app.ui

import com.familygamenight.core.game.GameModule
import com.familygamenight.core.session.Seat
import kotlinx.serialization.json.JsonElement

/** Everything the table screen needs, whether this device hosts or joined. */
data class TableModel(
    val module: GameModule,
    val rules: Map<String, Boolean>,
    val seats: List<Seat>,
    val viewerSeat: Int,
    val view: JsonElement,
    val version: Long,
    val isHost: Boolean,
    val lan: Boolean,
) {
    val missing: List<Seat> get() = seats.filter { it.isMissing }
}
