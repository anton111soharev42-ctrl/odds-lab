package com.vibe.oddslab.bj

import kotlin.math.floor

object CountEngine {
    /** Hi-Lo tag of a card value (1 = ace, 10 = ten-value). */
    fun hiLo(v: Int) = when (v) { in 2..6 -> 1; in 7..9 -> 0; else -> -1 }

    /** Off-the-top player edge (in %) for the rules, standard published approximations. */
    fun baseEdgePct(r: BjRules): Double {
        var e = -0.50 // 6 decks, S17, DAS, no surrender, 3:2, peek
        e += when (r.decks) { 1 -> 0.48; 2 -> 0.19; 4 -> 0.06; 8 -> -0.02; else -> 0.0 }
        if (r.h17) e -= 0.20
        if (!r.das) e -= 0.14
        if (r.surrender) e += 0.08
        if (r.bjPayout < 1.4) e -= 1.39
        if (!r.peek) e -= 0.11
        return e
    }

    class Status(
        val running: Int,
        val decksLeft: Double,
        val trueCount: Double,
        val edgePct: Double,
        val betUnits: Int,
        val kellyPct: Double,
        val advice: String,
    )

    fun status(seen: IntArray, rules: BjRules): Status {
        var rc = 0
        var seenTotal = 0
        for (v in 1..10) { rc += hiLo(v) * seen[v - 1]; seenTotal += seen[v - 1] }
        val left = (rules.decks * 52 - seenTotal).coerceAtLeast(0)
        val decksLeft = (left / 52.0).coerceAtLeast(0.25)
        val tc = rc / decksLeft
        val edge = baseEdgePct(rules) + 0.5 * tc
        val units = if (tc < 1) 1 else floor(tc).toInt().coerceAtMost(12)
        val kelly = if (edge > 0) edge / 1.33 else 0.0
        val advice = when {
            tc <= -2 -> "Колода «холодная»: минимальная ставка, а лучше пропусти раздачи / смени стол."
            edge <= 0 -> "Перевес у казино (${"%.2f".format(edge)}%). Ставь минимум."
            else -> "Перевес у тебя ≈ +${"%.2f".format(edge)}%. Ставка ≈ $units ед., по Келли ≈ ${"%.2f".format(kelly)}% банкролла (безопаснее ½ Келли)."
        }
        return Status(rc, decksLeft, tc, edge, units, kelly, advice)
    }
}
