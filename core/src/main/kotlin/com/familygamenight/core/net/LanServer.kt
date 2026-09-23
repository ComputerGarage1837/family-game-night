package com.familygamenight.core.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/** How often each side pings, and how long silence means the other side has gone. */
internal const val PING_EVERY_MS = 2_000L
internal const val DROP_AFTER_MS = 8_000L

class LanConnection internal constructor(private val socket: Socket, scope: CoroutineScope) {
    val id: String = UUID.randomUUID().toString()
    @Volatile var profileId: String? = null
    @Volatile internal var lastHeard = System.currentTimeMillis()
    @Volatile internal var saidGoodbye = false
    private val outbox = Channel<String>(Channel.UNLIMITED)

    init {
        // All writes happen here, never on the caller's (possibly UI) thread.
        scope.launch(Dispatchers.IO) {
            val out: BufferedWriter = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)
            try {
                for (line in outbox) {
                    out.write(line)
                    out.write("\n")
                    out.flush()
                }
            } catch (_: IOException) {
                // fall through
            }
            // The outbox only closes when we're hanging up.
            runCatching { socket.close() }
        }
    }

    val remoteAddress: String get() = socket.inetAddress?.hostAddress ?: "?"

    /** Queues a message; never blocks. */
    fun send(msg: NetMessage) {
        outbox.trySend(msg.encode())
    }

    /** Drops the connection immediately. */
    fun close() {
        outbox.close()
        runCatching { socket.close() }
    }

    /** Sends whatever is queued (e.g. a "sorry, table's full"), then hangs up. */
    fun hangUp() {
        outbox.close()
    }

    internal fun lines(): Sequence<String> = socket.getInputStream().bufferedReader(Charsets.UTF_8).lineSequence()
}

/** Accepts players on the local network. Blocking sockets on the IO dispatcher keep this simple. */
class LanServer(private val scope: CoroutineScope, private val handler: Handler) {

    interface Handler {
        fun onMessage(conn: LanConnection, msg: NetMessage)
        /** [leftOnPurpose] is true when the player chose to leave rather than dropping out. */
        fun onClosed(conn: LanConnection, leftOnPurpose: Boolean)
    }

    private var server: ServerSocket? = null
    private val jobs = mutableListOf<Job>()
    val connections = CopyOnWriteArrayList<LanConnection>()

    val port: Int get() = server?.localPort ?: -1

    /** Starts listening. Falls back to any free port if [preferredPort] is taken. */
    fun start(preferredPort: Int = DEFAULT_PORT): Int {
        val ss = runCatching { ServerSocket(preferredPort) }.getOrElse { ServerSocket(0) }
        server = ss
        jobs += scope.launch(Dispatchers.IO) {
            while (isActive) {
                val socket = try {
                    ss.accept()
                } catch (e: SocketException) {
                    break
                }
                socket.tcpNoDelay = true
                val conn = LanConnection(socket, scope)
                connections += conn
                launch { readLoop(conn) }
            }
        }
        jobs += scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(PING_EVERY_MS)
                val now = System.currentTimeMillis()
                for (c in connections) {
                    if (now - c.lastHeard > DROP_AFTER_MS) c.close() else c.send(NetMessage.Ping(now))
                }
            }
        }
        return ss.localPort
    }

    private fun readLoop(conn: LanConnection) {
        try {
            for (line in conn.lines()) {
                if (line.isBlank()) continue
                val msg = runCatching { NetMessage.decode(line) }.getOrNull() ?: continue
                conn.lastHeard = System.currentTimeMillis()
                when (msg) {
                    is NetMessage.Ping -> conn.send(NetMessage.Pong(msg.t))
                    is NetMessage.Pong -> Unit
                    is NetMessage.Leave -> {
                        conn.saidGoodbye = true
                        break
                    }
                    else -> handler.onMessage(conn, msg)
                }
            }
        } catch (_: IOException) {
            // dropped
        } finally {
            conn.close()
            connections -= conn
            handler.onClosed(conn, conn.saidGoodbye)
        }
    }

    fun broadcast(msg: NetMessage) = connections.forEach { it.send(msg) }

    fun stop(goodbye: NetMessage? = null) {
        runCatching { server?.close() }
        jobs.forEach { it.cancel() }
        for (c in connections.toList()) {
            goodbye?.let { c.send(it) }
            c.hangUp()
        }
    }
}
