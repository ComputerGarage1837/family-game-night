package com.familygamenight.core

import com.familygamenight.core.gofish.GoFishAction
import com.familygamenight.core.gofish.GoFishModule
import com.familygamenight.core.gofish.GoFishPhase
import com.familygamenight.core.session.HostSession
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
import kotlin.test.assertTrue

class PassAndPlayTest {
    @Test
    fun answersFromSomeoneNotHoldingThePhoneAreAutomatic(): Unit = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val session = HostSession(GoFishModule, GoFishModule.info.defaultRules(), lan = false, hostName = "Mum", scope = scope, random = Random(4), aiDelayMs = 10)
            session.addLocal("mum", "Mum", null)
            session.addLocal("sam", "Sam", null)
            session.startGame()
            val g = session.game.value!!
            val s0 = GoFishModule.decodeState(g.snapshot.value.state)
            val asker = s0.current
            val target = 1 - asker
            val rank = s0.hands[asker].first().rank
            assertEquals(null, g.submit(asker, GoFishModule.encodeAction(GoFishAction.Ask(target, rank))))
            // Sam isn't holding the phone, and has no choice, so the host answers for them.
            withTimeout(5_000) {
                while (GoFishModule.decodeState(g.snapshot.value.state).phase is GoFishPhase.Respond) delay(10)
            }
            val s1 = GoFishModule.decodeState(g.snapshot.value.state)
            // Either they handed cards over (asker asks again) or the asker must now draw.
            assertTrue(s1.awaiting == asker)
            // ...but the asker's own draw is never automatic.
            delay(100)
            assertEquals(s1, GoFishModule.decodeState(g.snapshot.value.state))
        } finally {
            scope.cancel()
        }
    }
}
