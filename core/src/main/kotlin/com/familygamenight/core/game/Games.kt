package com.familygamenight.core.game

import com.familygamenight.core.gofish.GoFishModule

/** Every game in the compilation. Add new favourites here. */
object Games {
    val all: List<GameModule> = listOf(GoFishModule)
    fun byId(id: String): GameModule = all.first { it.info.id == id }
}
