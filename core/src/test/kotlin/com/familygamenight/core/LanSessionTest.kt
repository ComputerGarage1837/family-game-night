package com.familygamenight.core

import com.familygamenight.core.game.Difficulty
import com.familygamenight.core.gofish.GoFishAi
import com.familygamenight.core.gofish.GoFishModule
import com.familygamenight.core.net.LanClient
import com.familygamenight.core.net.NetMessage
import com.familygamenight.core.net.PROTOCOL_VERSION
import com.familygamenight.core.session.HostSession
import com.familygamenight.core.session.SeatKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LanSessionTest {
    private suspend fun eventually(what: String, ms: Long = 10_000, cond: () -> Boolean) {
        try {
            withTimeout(ms) { while (!cond()) delay(20) }
        } catch (e: Exception) {
            throw AssertionError("Timed out waiting for: $what", e)
        }
    }

    private fun hello(id: String, name: String) = NetMessage.Hello(PROTOCOL_VERSION, id, name, avatar = "QUJD")

    /** Plays one move for whichever human (host-local or the remote client) is up. Returns true if it moved. */
    private fun playStep(session: HostSession, client: LanClient?, random: Random): Boolean {
        val g = session.game.value ?: return false
        val snap = g.snapshot.value
        if (snap.paused) return false
        val cur = g.currentSeat ?: return false
        val seat = snap.seats[cur]
        when (seat.kind) {
            SeatKind.LOCAL -> {
                val v = GoFishModule.decodeView(g.view(cur))
                return g.submit(cur, GoFishModule.encodeAction(GoFishAi.choose(v, Difficulty.MEDIUM, random))) == null
            }
            SeatKind.REMOTE -> {
                val t = client?.table?.value ?: return false
                if (t.version != snap.version) return false // wait until the client is up to date
                val v = GoFishModule.decodeView(t.view)
                if (!v.myTurn) return false
                client.act(GoFishModule.encodeAction(GoFishAi.choose(v, Difficulty.MEDIUM, random)))
                return true
            }
            SeatKind.AI -> return false
        }
    }

    @Test
    fun fullLanGameWithDropOutReconnectAndAiReplacement(): Unit = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val random = Random(11)
        val session = HostSession(GoFishModule, GoFishModule.info.defaultRules(), lan = true, hostName = "Mum", scope = scope, random = random, aiDelayMs = 5)
        try {
            session.addLocal("mum", "Mum", null)
            val port = session.startServer(0)
            assertTrue(port > 0)

            val kid = LanClient(scope, "127.0.0.1", port, hello("kid", "Sam"))
            kid.start()
            eventually("kid in lobby") { kid.lobby.value?.players?.size == 2 }
            eventually("host got kid's avatar") { session.avatars.value["kid"] == "QUJD" }

            session.addAi(Difficulty.HARD)
            eventually("lobby update") { kid.lobby.value?.players?.size == 3 }
            session.startGame()
            eventually("kid sees the table") { kid.table.value != null }
            assertEquals(1, kid.table.value!!.yourSeat)

            // Play a few moves.
            var moves = 0
            withTimeout(15_000) {
                while (moves < 6 && session.game.value!!.currentSeat != null) {
                    if (playStep(session, kid, random)) moves++ else delay(10)
                }
            }

            // Kid leaves on purpose -> game pauses, flagged as intentional.
            kid.leave()
            val g = session.game.value!!
            eventually("pause after leaving") { g.snapshot.value.paused }
            val missing = g.snapshot.value.missing.single()
            assertEquals("Sam", missing.name)
            assertTrue(missing.leftOnPurpose)
            val cur = g.currentSeat
            if (cur != null && g.snapshot.value.seats[cur].kind == SeatKind.LOCAL) {
                assertNotNull(g.submit(cur, GoFishModule.encodeAction(GoFishAi.choose(GoFishModule.decodeView(g.view(cur)), Difficulty.EASY, random))))
            }

            // A stranger can't take the seat mid-game.
            val stranger = LanClient(scope, "127.0.0.1", port, hello("stranger", "Bob"))
            stranger.start()
            eventually("stranger rejected") { stranger.status.value is LanClient.Status.Closed }

            // Kid comes back (same profile) -> seat restored, game resumes.
            val kidAgain = LanClient(scope, "127.0.0.1", port, hello("kid", "Sam"))
            kidAgain.start()
            eventually("resumed") { !g.snapshot.value.paused }
            eventually("table resent") { kidAgain.table.value?.version == g.snapshot.value.version }

            // Save mid-game and check it round-trips.
            val save = g.toSave("s1", "Test", 0)
            assertEquals(3, save.seats.size)

            // Now the kid drops again and the host replaces them with AI; game plays to the end.
            kidAgain.leave()
            eventually("paused again") { g.snapshot.value.paused }
            g.replaceWithAi(1, Difficulty.EASY)
            assertEquals(SeatKind.AI, g.snapshot.value.seats[1].kind)
            withTimeout(30_000) {
                while (g.currentSeat != null) { if (!playStep(session, null, random)) delay(5) }
            }
            assertNotNull(g.result())
        } finally {
            session.end("test over")
            scope.cancel()
        }
    }

    @Test
    fun resumingLanSaveWaitsForRemotePlayers(): Unit = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val session = HostSession(GoFishModule, GoFishModule.info.defaultRules(), lan = true, hostName = "Dad", scope = scope, aiDelayMs = 5)
        try {
            session.addLocal("dad", "Dad", null)
            val port = session.startServer(0)
            val kid = LanClient(scope, "127.0.0.1", port, hello("kid", "Sam"))
            kid.start()
            eventually("joined") { session.players.value.size == 2 }
            session.startGame()
            val save = session.game.value!!.toSave("s", "t", 0)
            kid.leave()
            eventually("kid gone") { session.game.value!!.snapshot.value.paused }

            val session2 = HostSession(GoFishModule, emptyMap(), lan = true, hostName = "Dad", scope = scope, aiDelayMs = 5)
            session2.startServer(0)
            session2.resume(save)
            val g = session2.game.value!!
            assertTrue(g.snapshot.value.paused)
            assertEquals("Sam", g.snapshot.value.missing.single().name)
            val back = LanClient(scope, "127.0.0.1", session2.port, hello("kid", "Sam"))
            back.start()
            eventually("resumed after rejoin") { !g.snapshot.value.paused && back.table.value != null }
            session2.end("done")
        } finally {
            session.end("done")
            scope.cancel()
        }
    }
}
