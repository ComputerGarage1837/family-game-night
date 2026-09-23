package com.familygamenight.core.net

import com.familygamenight.core.game.Difficulty
import com.familygamenight.core.game.GameJson
import com.familygamenight.core.session.Seat
import com.familygamenight.core.session.SeatKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

const val PROTOCOL_VERSION = 1
const val DEFAULT_PORT = 47_321
/** mDNS/NSD service type used to find games on the local network. */
const val NSD_SERVICE_TYPE = "_familygamenight._tcp."

@Serializable
data class LobbyPlayer(
    val profileId: String,
    val name: String,
    val kind: SeatKind,
    val difficulty: Difficulty? = null,
)

/** One JSON object per line over TCP. */
@Serializable
sealed class NetMessage {
    // ---- client -> host
    @Serializable @SerialName("hello")
    data class Hello(
        val protocol: Int,
        val profileId: String,
        val name: String,
        /** Small JPEG avatar, base64 encoded. */
        val avatar: String? = null,
    ) : NetMessage()

    @Serializable @SerialName("act")
    data class Act(val action: JsonElement) : NetMessage()

    @Serializable @SerialName("leave")
    data object Leave : NetMessage()

    // ---- both ways
    @Serializable @SerialName("ping")
    data class Ping(val t: Long) : NetMessage()

    @Serializable @SerialName("pong")
    data class Pong(val t: Long) : NetMessage()

    // ---- host -> client
    @Serializable @SerialName("welcome")
    data class Welcome(val hostName: String, val gameId: String) : NetMessage()

    @Serializable @SerialName("reject")
    data class Reject(val reason: String) : NetMessage()

    @Serializable @SerialName("lobby")
    data class Lobby(
        val gameId: String,
        val rules: Map<String, Boolean>,
        val players: List<LobbyPlayer>,
    ) : NetMessage()

    @Serializable @SerialName("avatars")
    data class Avatars(val avatars: Map<String, String>) : NetMessage()

    @Serializable @SerialName("table")
    data class Table(
        val gameId: String,
        val rules: Map<String, Boolean>,
        val seats: List<Seat>,
        val yourSeat: Int,
        val view: JsonElement,
        val version: Long,
    ) : NetMessage()

    @Serializable @SerialName("ended")
    data class Ended(val reason: String) : NetMessage()

    fun encode(): String = GameJson.encodeToString(serializer(), this)

    companion object {
        fun decode(line: String): NetMessage = GameJson.decodeFromString(serializer(), line)
    }
}
