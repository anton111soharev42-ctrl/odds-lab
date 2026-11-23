package com.vibe.oddslab.poker

/** Card = rank * 4 + suit. rank 0..12 = 2..A, suit 0..3 = ♠ ♥ ♦ ♣. */
object Cards {
    const val RANKS = "23456789TJQKA"
    val SUITS = arrayOf("♠", "♥", "♦", "♣")

    fun rank(c: Int) = c shr 2
    fun suit(c: Int) = c and 3
    fun card(rank: Int, suit: Int) = (rank shl 2) or suit

    fun rankLabel(r: Int) = if (r == 8) "10" else RANKS[r].toString()
    fun label(c: Int) = rankLabel(rank(c)) + SUITS[suit(c)]
}
