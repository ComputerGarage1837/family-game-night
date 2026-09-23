package com.familygamenight.core.session

import com.familygamenight.core.game.Difficulty
import com.familygamenight.core.game.GameModule
import com.familygamenight.core.game.GameResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlin.random.Random

/**
 * The authoritative copy of a running game. Lives on the host device only; everyone else
 * (remote players and the AI) just gets views and sends actions.
 */
class GameHost(
    val module: GameModule,
    val rules: Map<String, Boolean>,
    val lan: Boolean,
    seats: List<Seat>,
    state: JsonElement,
    private val scope: CoroutineScope,
    private val random: Random = Random.Default,
    private val aiDelayMs: Long = 1400,
) {
    data class Snapshot(val seats: List<Seat>, val state: JsonElement, val version: Long) {
        val missing: List<Seat> get() = seats.filter { it.isMissing }
        val paused: Boolean get() = missing.isNotEmpty()
    }

    private val lock = Any()
    private val _snapshot = MutableStateFlow(Snapshot(seats, state, 0))
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    /** Called (under the host lock, in order) after every change – used to push views to remote players. */
    @Volatile var onChanged: ((Snapshot) -> Unit)? = null

    private var aiJob: Job? = null

    fun start() = synchronized(lock) { publish(_snapshot.value) }

    val currentSeat: Int? get() = module.currentSeat(_snapshot.value.state)
    fun result(): GameResult? = module.result(_snapshot.value.state)
    fun view(seat: Int): JsonElement = module.view(_snapshot.value.state, seat)

    /** Returns an error message, or null if the move was made. */
    fun submit(seat: Int, action: JsonElement): String? = synchronized(lock) {
        val s = _snapshot.value
        if (s.paused) return "The game is paused"
        module.validate(s.state, seat, action)?.let { return it }
        publish(s.copy(state = module.apply(s.state, seat, action), version = s.version + 1))
        null
    }

    fun seatOf(profileId: String): Seat? = _snapshot.value.seats.firstOrNull { it.profileId == profileId }

    fun setConnected(index: Int, connected: Boolean, leftOnPurpose: Boolean = false) = synchronized(lock) {
        updateSeat(index) { it.copy(connected = connected, leftOnPurpose = if (connected) false else leftOnPurpose) }
    }

    fun replaceWithAi(index: Int, difficulty: Difficulty, name: String? = null) = synchronized(lock) {
        val taken = _snapshot.value.seats.map { it.name }
        updateSeat(index) {
            it.copy(
                kind = SeatKind.AI,
                difficulty = difficulty,
                connected = true,
                leftOnPurpose = false,
                name = name ?: AiNames.pick(taken),
                profileId = "ai-${it.index}-${random.nextInt(1_000_000)}",
            )
        }
    }

    fun toSave(id: String, title: String, nowMillis: Long): SavedGame {
        val s = _snapshot.value
        return SavedGame(id, module.info.id, title, nowMillis, lan, rules, s.seats, s.state)
    }

    fun close() {
        aiJob?.cancel()
        onChanged = null
    }

    private fun updateSeat(index: Int, f: (Seat) -> Seat) {
        val s = _snapshot.value
        publish(s.copy(seats = s.seats.map { if (it.index == index) f(it) else it }, version = s.version + 1))
    }

    private fun publish(s: Snapshot) {
        _snapshot.value = s
        onChanged?.invoke(s)
        scheduleAi(s)
    }

    private fun scheduleAi(s: Snapshot) {
        aiJob?.cancel()
        if (s.paused) return
        val cur = module.currentSeat(s.state) ?: return
        val seat = s.seats[cur]
        if (seat.kind != SeatKind.AI) return
        aiJob = scope.launch {
            delay(aiDelayMs)
            synchronized(lock) {
                val now = _snapshot.value
                if (now.version != s.version) return@launch
                val view = module.view(now.state, cur)
                val action = module.aiAction(view, seat.difficulty ?: Difficulty.MEDIUM, random)
                publish(now.copy(state = module.apply(now.state, cur, action), version = now.version + 1))
            }
        }
    }
}
