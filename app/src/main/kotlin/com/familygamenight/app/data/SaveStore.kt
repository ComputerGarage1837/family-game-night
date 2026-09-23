package com.familygamenight.app.data

import android.content.Context
import com.familygamenight.core.game.GameJson
import com.familygamenight.core.session.SavedGame
import java.io.File

/** Saved games, one JSON file each. Only the host saves – it holds the real game. */
class SaveStore(context: Context) {
    private val dir = File(context.filesDir, "saves").apply { mkdirs() }

    fun list(): List<SavedGame> = dir.listFiles { f -> f.extension == "json" }.orEmpty()
        .mapNotNull { f -> runCatching { GameJson.decodeFromString(SavedGame.serializer(), f.readText()) }.getOrNull() }
        .sortedByDescending { it.savedAtMillis }

    fun save(game: SavedGame) {
        val tmp = File(dir, "${game.id}.tmp")
        tmp.writeText(GameJson.encodeToString(SavedGame.serializer(), game))
        tmp.renameTo(File(dir, "${game.id}.json"))
    }

    fun delete(id: String) {
        File(dir, "$id.json").delete()
    }
}
