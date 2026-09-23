package com.familygamenight.core.session

import com.familygamenight.core.game.Difficulty
import com.familygamenight.core.game.GameModule
import com.familygamenight.core.net.DEFAULT_PORT
import com.familygamenight.core.net.LanConnection
import com.familygamenight.core.net.LanServer
import com.familygamenight.core.net.LobbyPlayer
import com.familygamenight.core.net.NetMessage
import com.familygamenight.core.net.PROTOCOL_VERSION
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Everything the host device runs: the lobby (who's playing), the LAN server (if any) and,
 * once started, the [GameHost]. Also used for purely local games, just without a server.
 */
class HostSession(
    val module: GameModule,
    rules: Map<String, Boolean>,
    val lan: Boolean,
    private val hostName: String,
    private val scope: CoroutineScope,
    private val random: Random = Random.Default,
    aiDelayMs: Long = 1400,
) : LanServer.Handler {

    /** Computer player speed; applies straight away to a running game. */
    var aiDelayMs: Long = aiDelayMs
        set(value) {
            field = value
            _game.value?.aiDelayMs = value
        }

    private val lock = Any()

    private val _rules = MutableStateFlow(rules)
    val rules: StateFlow<Map<String, Boolean>> = _rules.asStateFlow()

    private val _players = MutableStateFlow<List<LobbyPlayer>>(emptyList())
    val players: StateFlow<List<LobbyPlayer>> = _players.asStateFlow()

    /** profileId -> base64 JPEG, for local and remote players. */
    private val _avatars = MutableStateFlow<Map<String, String>>(emptyMap())
    val avatars: StateFlow<Map<String, String>> = _avatars.asStateFlow()

    private val _game = MutableStateFlow<GameHost?>(null)
    val game: StateFlow<GameHost?> = _game.asStateFlow()

    private val server: LanServer? = if (lan) LanServer(scope, this) else null
    private val connections = ConcurrentHashMap<String, LanConnection>() // profileId -> connection

    var port: Int = -1
        private set

    fun startServer(preferredPort: Int = DEFAULT_PORT): Int {
        port = server?.start(preferredPort) ?: -1
        return port
    }

    // ------------------------------------------------------------------ lobby

    fun setRules(rules: Map<String, Boolean>) {
        _rules.value = rules
        broadcastLobby()
    }

    fun addLocal(profileId: String, name: String, avatar: String?) = synchronized(lock) {
        if (_players.value.any { it.profileId == profileId }) return
        if (_players.value.size >= module.info.maxPlayers) return
        _players.value = _players.value + LobbyPlayer(profileId, name, SeatKind.LOCAL)
        avatar?.let { _avatars.value = _avatars.value + (profileId to it) }
        broadcastLobby()
        broadcastAvatars()
    }

    fun addAi(difficulty: Difficulty?) = synchronized(lock) {
        if (_players.value.size >= module.info.maxPlayers) return
        val name = AiNames.pick(_players.value.map { it.name })
        val id = "ai-" + random.nextInt(1_000_000_000)
        _players.value = _players.value + LobbyPlayer(id, name, SeatKind.AI, difficulty)
        broadcastLobby()
    }

    fun setAiDifficulty(profileId: String, difficulty: Difficulty) = synchronized(lock) {
        _players.value = _players.value.map { if (it.profileId == profileId) it.copy(difficulty = difficulty) else it }
        broadcastLobby()
    }

    fun remove(profileId: String) = synchronized(lock) {
        _players.value = _players.value.filterNot { it.profileId == profileId }
        connections.remove(profileId)?.let {
            it.send(NetMessage.Ended("The host removed you from the game"))
            it.hangUp()
        }
        broadcastLobby()
    }

    fun canStart(): Boolean = _players.value.size in module.info.minPlayers..module.info.maxPlayers

    // ------------------------------------------------------------------ game

    fun startGame() = synchronized(lock) {
        val seats = _players.value.mapIndexed { i, p ->
            Seat(i, p.profileId, p.name, p.kind, if (p.kind == SeatKind.AI) p.difficulty ?: Difficulty.MEDIUM else null)
        }
        val state = module.newGame(seats.size, _rules.value, random)
        launch(seats, state)
    }

    /** Same people, fresh deal. AI replacements keep their seats. */
    fun rematch() = synchronized(lock) {
        val old = _game.value ?: return
        val seats = old.snapshot.value.seats
        _players.value = seats.map { LobbyPlayer(it.profileId, it.name, it.kind, it.difficulty) }
        launch(seats, module.newGame(seats.size, _rules.value, random))
    }

    /** Resumes a save. Remote players start as missing until they reconnect (or get replaced). */
    fun resume(saved: SavedGame) = synchronized(lock) {
        _rules.value = saved.rules
        val seats = saved.seats.map { if (it.kind == SeatKind.REMOTE) it.copy(connected = connections.containsKey(it.profileId)) else it }
        _players.value = seats.map { LobbyPlayer(it.profileId, it.name, it.kind, it.difficulty) }
        launch(seats, saved.state)
    }

    private fun launch(seats: List<Seat>, state: kotlinx.serialization.json.JsonElement) {
        _game.value?.close()
        val host = GameHost(module, _rules.value, lan, seats, state, scope, random, aiDelayMs)
        host.onChanged = { snap -> pushTables(snap) }
        _game.value = host
        host.start()
    }

    fun end(reason: String) {
        _game.value?.close()
        server?.stop(NetMessage.Ended(reason))
        connections.clear()
    }

    // ------------------------------------------------------------------ network

    override fun onMessage(conn: LanConnection, msg: NetMessage) {
        when (msg) {
            is NetMessage.Hello -> onHello(conn, msg)
            is NetMessage.Act -> {
                val pid = conn.profileId ?: return
                val g = _game.value ?: return
                val seat = g.seatOf(pid)?.takeIf { it.kind == SeatKind.REMOTE } ?: return
                if (g.submit(seat.index, msg.action) != null) {
                    // Rejected (e.g. a stale tap) – resend the truth so their screen catches up.
                    sendTable(conn, g.snapshot.value, seat.index)
                }
            }
            else -> Unit
        }
    }

    private fun onHello(conn: LanConnection, hello: NetMessage.Hello) = synchronized(lock) {
        if (hello.protocol != PROTOCOL_VERSION) {
            conn.send(NetMessage.Reject("Different app versions – please update both devices"))
            conn.hangUp()
            return
        }
        hello.avatar?.let { _avatars.value = _avatars.value + (hello.profileId to it) }
        val g = _game.value
        if (g == null) {
            val existing = _players.value.firstOrNull { it.profileId == hello.profileId }
            if (existing != null && existing.kind != SeatKind.REMOTE) {
                conn.send(NetMessage.Reject("${hello.name} is already playing on the host device"))
                conn.hangUp()
                return
            }
            if (existing == null && _players.value.size >= module.info.maxPlayers) {
                conn.send(NetMessage.Reject("The table is full"))
                conn.hangUp()
                return
            }
            if (existing == null) _players.value = _players.value + LobbyPlayer(hello.profileId, hello.name, SeatKind.REMOTE)
            accept(conn, hello)
            broadcastLobby()
            broadcastAvatars()
        } else {
            val seat = g.seatOf(hello.profileId)
            if (seat == null || seat.kind != SeatKind.REMOTE) {
                conn.send(NetMessage.Reject("A game is already in progress"))
                conn.hangUp()
                return
            }
            accept(conn, hello)
            broadcastAvatars()
            if (seat.connected) sendTable(conn, g.snapshot.value, seat.index)
            else g.setConnected(seat.index, true) // pushes tables to everyone
        }
    }

    private fun accept(conn: LanConnection, hello: NetMessage.Hello) {
        conn.profileId = hello.profileId
        connections.put(hello.profileId, conn)?.takeIf { it !== conn }?.close()
        conn.send(NetMessage.Welcome(hostName, module.info.id))
        conn.send(lobbyMessage())
    }

    override fun onClosed(conn: LanConnection, leftOnPurpose: Boolean) = synchronized(lock) {
        val pid = conn.profileId ?: return
        // Ignore an old connection that has already been replaced by a reconnect.
        if (!connections.remove(pid, conn)) return
        val g = _game.value
        if (g == null) {
            _players.value = _players.value.filterNot { it.profileId == pid && it.kind == SeatKind.REMOTE }
            broadcastLobby()
        } else {
            val seat = g.seatOf(pid) ?: return
            if (seat.kind == SeatKind.REMOTE) g.setConnected(seat.index, false, leftOnPurpose)
        }
    }

    private fun lobbyMessage() = NetMessage.Lobby(module.info.id, _rules.value, _players.value)

    private fun broadcastLobby() {
        if (server == null) return
        val msg = lobbyMessage()
        connections.values.forEach { it.send(msg) }
    }

    private fun broadcastAvatars() {
        if (server == null) return
        val msg = NetMessage.Avatars(_avatars.value)
        connections.values.forEach { it.send(msg) }
    }

    private fun pushTables(snap: GameHost.Snapshot) {
        if (server == null) return
        for (seat in snap.seats) {
            if (seat.kind != SeatKind.REMOTE) continue
            connections[seat.profileId]?.let { sendTable(it, snap, seat.index) }
        }
    }

    private fun sendTable(conn: LanConnection, snap: GameHost.Snapshot, seat: Int) {
        conn.send(
            NetMessage.Table(
                gameId = module.info.id,
                rules = _rules.value,
                seats = snap.seats,
                yourSeat = seat,
                view = module.view(snap.state, seat),
                version = snap.version,
            ),
        )
    }
}
