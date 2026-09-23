package com.familygamenight.core.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * A player's connection to a host. Reconnects by itself if the Wi-Fi blips; the host
 * recognises the player by profile id and gives them their seat back.
 */
class LanClient(
    private val scope: CoroutineScope,
    val host: String,
    val port: Int,
    private val hello: NetMessage.Hello,
) {
    sealed interface Status {
        data object Connecting : Status
        data object Connected : Status
        data class Reconnecting(val attempt: Int) : Status
        data class Closed(val reason: String) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Connecting)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _lobby = MutableStateFlow<NetMessage.Lobby?>(null)
    val lobby: StateFlow<NetMessage.Lobby?> = _lobby.asStateFlow()

    private val _table = MutableStateFlow<NetMessage.Table?>(null)
    val table: StateFlow<NetMessage.Table?> = _table.asStateFlow()

    private val _avatars = MutableStateFlow<Map<String, String>>(emptyMap())
    val avatars: StateFlow<Map<String, String>> = _avatars.asStateFlow()

    private val _hostName = MutableStateFlow<String?>(null)
    val hostName: StateFlow<String?> = _hostName.asStateFlow()

    @Volatile private var socket: Socket? = null
    @Volatile private var outbox: Channel<String>? = null
    @Volatile private var finished = false
    @Volatile private var lastHeard = 0L
    private var job: Job? = null

    fun start() {
        job = scope.launch(Dispatchers.IO) {
            var attempt = 0
            launch { heartbeat() }
            while (isActive && !finished) {
                try {
                    val s = Socket()
                    s.connect(InetSocketAddress(host, port), 4_000)
                    s.tcpNoDelay = true
                    socket = s
                    val box = Channel<String>(Channel.UNLIMITED)
                    outbox = box
                    launch { writeLoop(s, box) }
                    lastHeard = System.currentTimeMillis()
                    send(hello)
                    for (line in s.getInputStream().bufferedReader(Charsets.UTF_8).lineSequence()) {
                        val msg = runCatching { NetMessage.decode(line) }.getOrNull() ?: continue
                        lastHeard = System.currentTimeMillis()
                        attempt = 0
                        handle(msg)
                        if (finished) break
                    }
                } catch (_: IOException) {
                    // fall through to reconnect
                } finally {
                    runCatching { socket?.close() }
                    outbox?.close()
                    socket = null
                    outbox = null
                }
                if (finished) break
                attempt++
                _status.value = Status.Reconnecting(attempt)
                delay(if (attempt < 3) 1_000 else 2_500)
            }
        }
    }

    private fun handle(msg: NetMessage) {
        when (msg) {
            is NetMessage.Ping -> send(NetMessage.Pong(msg.t))
            is NetMessage.Welcome -> {
                _hostName.value = msg.hostName
                _status.value = Status.Connected
            }
            is NetMessage.Reject -> finish(msg.reason)
            is NetMessage.Ended -> finish(msg.reason)
            is NetMessage.Lobby -> _lobby.value = msg
            is NetMessage.Avatars -> _avatars.value = _avatars.value + msg.avatars
            is NetMessage.Table -> _table.value = msg
            else -> Unit
        }
    }

    private suspend fun heartbeat() {
        while (!finished) {
            delay(PING_EVERY_MS)
            val s = socket ?: continue
            if (System.currentTimeMillis() - lastHeard > DROP_AFTER_MS) runCatching { s.close() }
            else send(NetMessage.Ping(System.currentTimeMillis()))
        }
    }

    private suspend fun writeLoop(s: Socket, box: Channel<String>) {
        try {
            val w: BufferedWriter = s.getOutputStream().bufferedWriter(Charsets.UTF_8)
            for (line in box) {
                w.write(line)
                w.write("\n")
                w.flush()
            }
        } catch (_: IOException) {
            runCatching { s.close() }
        }
    }

    /** Queues a message for the host; safe to call from the UI thread. */
    fun send(msg: NetMessage): Boolean = outbox?.trySend(msg.encode())?.isSuccess ?: false

    fun act(action: kotlinx.serialization.json.JsonElement) = send(NetMessage.Act(action))

    /** Leave on purpose – the host sees "X left the game". */
    fun leave() {
        send(NetMessage.Leave)
        finished = true
        _status.value = Status.Closed("You left the game")
        outbox?.close() // lets the goodbye drain before we hang up
        scope.launch(Dispatchers.IO) {
            delay(300)
            runCatching { socket?.close() }
            job?.cancel()
        }
    }

    private fun finish(reason: String) {
        finished = true
        _status.value = Status.Closed(reason)
        runCatching { socket?.close() }
        job?.cancel()
    }
}
