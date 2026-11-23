package com.vibe.oddslab.poker

import java.util.SplittableRandom

/**
 * Ranks the 169 starting-hand classes by simulated equity vs one random hand.
 * Index: pair -> hi*13+hi, suited -> hi*13+lo, offsuit -> lo*13+hi.
 */
object PreflopTable {
    class Table(val equity: DoubleArray, val startPct: DoubleArray, val endPct: DoubleArray)

    val table: Table by lazy { build() }

    fun classIndex(c1: Int, c2: Int): Int {
        val r1 = Cards.rank(c1); val r2 = Cards.rank(c2)
        val hi = maxOf(r1, r2); val lo = minOf(r1, r2)
        return when {
            hi == lo -> hi * 13 + hi
            Cards.suit(c1) == Cards.suit(c2) -> hi * 13 + lo
            else -> lo * 13 + hi
        }
    }

    fun combosOf(idx: Int): Int {
        val a = idx / 13; val b = idx % 13
        return when { a == b -> 6; a > b -> 4; else -> 12 }
    }

    fun className(idx: Int): String {
        val a = idx / 13; val b = idx % 13
        val hi = maxOf(a, b); val lo = minOf(a, b)
        val base = "${Cards.RANKS[hi]}${Cards.RANKS[lo]}"
        return when { a == b -> base; a > b -> base + "s"; else -> base + "o" }
    }

    /** Classes whose position in the ranking starts inside the top [pct]% of all 1326 combos. */
    fun allowed(pct: Int): BooleanArray {
        val t = table
        return BooleanArray(169) { t.startPct[it] < pct }
    }

    private fun build(): Table {
        val rnd = SplittableRandom(20261124L)
        val eq = DoubleArray(169)
        for (idx in 0 until 169) {
            val a = idx / 13; val b = idx % 13
            val hi = maxOf(a, b); val lo = minOf(a, b)
            val h1 = Cards.card(hi, 0)
            val h2 = when { a == b -> Cards.card(lo, 1); a > b -> Cards.card(lo, 0); else -> Cards.card(lo, 1) }
            eq[idx] = simulate(h1, h2, 6000, rnd)
        }
        val order = (0 until 169).sortedByDescending { eq[it] }
        val start = DoubleArray(169); val end = DoubleArray(169)
        var cum = 0
        for (idx in order) {
            start[idx] = cum * 100.0 / 1326
            cum += combosOf(idx)
            end[idx] = cum * 100.0 / 1326
        }
        return Table(eq, start, end)
    }

    private fun simulate(h1: Int, h2: Int, iters: Int, rnd: SplittableRandom): Double {
        val deck = IntArray(50)
        var k = 0
        for (c in 0 until 52) if (c != h1 && c != h2) deck[k++] = c
        val me = IntArray(7); val op = IntArray(7)
        me[0] = h1; me[1] = h2
        var share = 0.0
        repeat(iters) {
            var n = 50
            fun draw(): Int {
                val i = rnd.nextInt(n); val c = deck[i]; deck[i] = deck[n - 1]; deck[n - 1] = c; n--; return c
            }
            op[0] = draw(); op[1] = draw()
            for (j in 2 until 7) { val c = draw(); me[j] = c; op[j] = c }
            val a = HandEval.eval(me, 7); val b = HandEval.eval(op, 7)
            share += if (a > b) 1.0 else if (a == b) 0.5 else 0.0
        }
        return share / iters
    }
}
