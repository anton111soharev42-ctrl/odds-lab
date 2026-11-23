package com.vibe.oddslab

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vibe.oddslab.bj.BjAnalysis
import com.vibe.oddslab.bj.BjEngine
import com.vibe.oddslab.bj.BjRules
import com.vibe.oddslab.poker.PokerOutcome

class PokerState {
    /** 0..1 = hero, 2..6 = board (flop, flop, flop, turn, river). */
    val slots = mutableStateListOf<Int?>(null, null, null, null, null, null, null)
    var selected by mutableIntStateOf(0)
    var pendingRank by mutableStateOf<Int?>(null)
    var pendingSuit by mutableStateOf<Int?>(null)
    var opponents by mutableIntStateOf(1)
    var rangePct by mutableIntStateOf(100)
    var pot by mutableStateOf("")
    var toCall by mutableStateOf("")
    var result by mutableStateOf<PokerOutcome?>(null)
    var computing by mutableStateOf(false)

    fun usedElsewhere(c: Int) = slots.withIndex().any { it.index != selected && it.value == c }

    fun place(c: Int) {
        slots[selected] = c
        pendingRank = null; pendingSuit = null
        val order = (selected + 1 until 7) + (0 until selected)
        order.firstOrNull { slots[it] == null }?.let { selected = it }
    }

    fun clearSelected() { slots[selected] = null; pendingRank = null; pendingSuit = null }

    fun clearAll() {
        for (i in 0 until 7) slots[i] = null
        selected = 0; pendingRank = null; pendingSuit = null; result = null
    }

    fun clearBoardKeepNothing() = clearAll()
}

class BjState {
    var rules by mutableStateOf(BjRules())
    val player = mutableStateListOf<Int>()
    var up by mutableStateOf<Int?>(null)
    var toDealer by mutableStateOf(false)
    var useCounter by mutableStateOf(true)
    var basic by mutableStateOf<BjAnalysis?>(null)
    var counted by mutableStateOf<BjAnalysis?>(null)
    var error by mutableStateOf<String?>(null)

    // Shoe tracker (card counting)
    val seen = mutableStateListOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
    val history = mutableStateListOf<Int>()

    fun seenArray() = IntArray(10) { seen[it] }
    fun remaining(v: Int) = rules.decks * BjEngine.STD[v - 1] - seen[v - 1]
    fun cardsLeft() = (1..10).sumOf { remaining(it).coerceAtLeast(0) }

    fun addSeen(v: Int) { seen[v - 1]++; history.add(v) }
    fun undoSeen() {
        if (history.isEmpty()) return
        val v = history.removeAt(history.size - 1)
        seen[v - 1] = (seen[v - 1] - 1).coerceAtLeast(0)
    }
    fun newShoe() { for (i in 0 until 10) seen[i] = 0; history.clear() }

    fun addCard(v: Int) {
        if (toDealer || (player.size >= 2 && up == null)) {
            up = v; toDealer = false
        } else player.add(v)
    }

    fun clearHand() { player.clear(); up = null; toDealer = false }

    /** Moves the round's cards into the counter. */
    fun commitRound() {
        player.forEach { addSeen(it) }
        up?.let { addSeen(it) }
        clearHand()
    }
}

class AppState {
    val poker = PokerState()
    val bj = BjState()
}
