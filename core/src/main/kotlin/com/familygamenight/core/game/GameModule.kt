package com.familygamenight.core.game

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.random.Random

val GameJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "t"
}

/** Final result of a finished game. */
data class GameResult(val scores: List<Int>, val winners: List<Int>, val scoreLabel: String)

/**
 * A game the app can host. The session and network layers only deal in JSON, so adding a
 * new game means implementing this (usually through [TypedGameModule]) plus its table UI.
 */
interface GameModule {
    val info: GameInfo
    fun newGame(playerCount: Int, rules: Map<String, Boolean>, random: Random): JsonElement
    /** Seat whose move the game is waiting on, or null when the game is over. */
    fun currentSeat(state: JsonElement): Int?
    /** Whose turn it is (may differ from [currentSeat] while someone else answers, e.g. "go fish"). */
    fun turnOwner(state: JsonElement): Int? = currentSeat(state)
    /**
     * If [seat] only has to answer someone else's move and has no real choice (e.g. handing over
     * cards they must hand over), the move to make. Used in pass-and-play so the phone doesn't
     * need passing around just to say "go fish".
     */
    fun forcedResponse(state: JsonElement, seat: Int): JsonElement? = null
    /** True when [seat]'s next move needs no thinking (drawing, answering); the AI goes quicker. */
    fun isForcedMove(state: JsonElement, seat: Int): Boolean = false
    /** Returns an error message if [action] is not legal for [seat], else null. */
    fun validate(state: JsonElement, seat: Int, action: JsonElement): String?
    fun apply(state: JsonElement, seat: Int, action: JsonElement): JsonElement
    /** What [seat] is allowed to see – never other players' hidden cards. */
    fun view(state: JsonElement, seat: Int): JsonElement
    /** The AI only ever gets the same [view] a human in that seat would get. */
    fun aiAction(view: JsonElement, difficulty: Difficulty, random: Random): JsonElement
    fun result(state: JsonElement): GameResult?
}

abstract class TypedGameModule<S, A, V>(
    private val stateSer: kotlinx.serialization.KSerializer<S>,
    private val actionSer: kotlinx.serialization.KSerializer<A>,
    private val viewSer: kotlinx.serialization.KSerializer<V>,
) : GameModule {
    abstract fun newTyped(playerCount: Int, rules: Map<String, Boolean>, random: Random): S
    abstract fun currentSeatTyped(state: S): Int?
    abstract fun validateTyped(state: S, seat: Int, action: A): String?
    abstract fun applyTyped(state: S, seat: Int, action: A): S
    abstract fun viewTyped(state: S, seat: Int): V
    abstract fun aiTyped(view: V, difficulty: Difficulty, random: Random): A
    abstract fun resultTyped(state: S): GameResult?
    open fun turnOwnerTyped(state: S): Int? = currentSeatTyped(state)
    open fun forcedResponseTyped(state: S, seat: Int): A? = null
    open fun isForcedMoveTyped(state: S, seat: Int): Boolean = false

    fun decodeState(e: JsonElement): S = GameJson.decodeFromJsonElement(stateSer, e)
    fun encodeState(s: S): JsonElement = GameJson.encodeToJsonElement(stateSer, s)
    fun decodeAction(e: JsonElement): A = GameJson.decodeFromJsonElement(actionSer, e)
    fun encodeAction(a: A): JsonElement = GameJson.encodeToJsonElement(actionSer, a)
    fun decodeView(e: JsonElement): V = GameJson.decodeFromJsonElement(viewSer, e)
    fun encodeView(v: V): JsonElement = GameJson.encodeToJsonElement(viewSer, v)

    override fun newGame(playerCount: Int, rules: Map<String, Boolean>, random: Random) =
        encodeState(newTyped(playerCount, rules, random))

    override fun currentSeat(state: JsonElement) = currentSeatTyped(decodeState(state))
    override fun turnOwner(state: JsonElement) = turnOwnerTyped(decodeState(state))
    override fun forcedResponse(state: JsonElement, seat: Int) = forcedResponseTyped(decodeState(state), seat)?.let { encodeAction(it) }
    override fun isForcedMove(state: JsonElement, seat: Int) = isForcedMoveTyped(decodeState(state), seat)

    override fun validate(state: JsonElement, seat: Int, action: JsonElement): String? {
        val a = runCatching { decodeAction(action) }.getOrElse { return "Unrecognised move" }
        return validateTyped(decodeState(state), seat, a)
    }

    override fun apply(state: JsonElement, seat: Int, action: JsonElement) =
        encodeState(applyTyped(decodeState(state), seat, decodeAction(action)))

    override fun view(state: JsonElement, seat: Int) = encodeView(viewTyped(decodeState(state), seat))

    override fun aiAction(view: JsonElement, difficulty: Difficulty, random: Random) =
        encodeAction(aiTyped(decodeView(view), difficulty, random))

    override fun result(state: JsonElement) = resultTyped(decodeState(state))
}
