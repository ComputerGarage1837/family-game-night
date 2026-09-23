package com.familygamenight.core.cards

import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
enum class Suit(val symbol: String, val isRed: Boolean) {
    CLUBS("♣", false),
    DIAMONDS("♦", true),
    HEARTS("♥", true),
    SPADES("♠", false),
}

@Serializable
enum class Rank(val label: String, val singular: String, val plural: String) {
    TWO("2", "Two", "Twos"),
    THREE("3", "Three", "Threes"),
    FOUR("4", "Four", "Fours"),
    FIVE("5", "Five", "Fives"),
    SIX("6", "Six", "Sixes"),
    SEVEN("7", "Seven", "Sevens"),
    EIGHT("8", "Eight", "Eights"),
    NINE("9", "Nine", "Nines"),
    TEN("10", "Ten", "Tens"),
    JACK("J", "Jack", "Jacks"),
    QUEEN("Q", "Queen", "Queens"),
    KING("K", "King", "Kings"),
    ACE("A", "Ace", "Aces"),
}

/** A playing card. [deck] tells apart identical cards when several decks are shuffled together. */
@Serializable
data class Card(val rank: Rank, val suit: Suit, val deck: Int = 0) {
    override fun toString() = "${rank.label}${suit.symbol}"
}

object Decks {
    fun standard(decks: Int = 1): List<Card> =
        (0 until decks).flatMap { d -> Suit.entries.flatMap { s -> Rank.entries.map { r -> Card(r, s, d) } } }

    fun shuffled(decks: Int, random: Random): List<Card> = standard(decks).shuffled(random)
}

/** Sorts a hand the way people hold one: grouped by rank, then suit. */
fun List<Card>.sortedForHand(): List<Card> = sortedWith(compareBy({ it.rank.ordinal }, { it.suit.ordinal }, { it.deck }))
