package com.vibe.oddslab.poker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import java.util.SplittableRandom
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.sqrt

class EquityResult(
    /** Expected share of the pot (wins + split shares). */
    val equity: Double,
    val win: Double,
    val tie: Double,
    val lose: Double,
    val samples: Long,
    val exact: Boolean,
    val stdErr: Double,
    /** Probability of hero finishing with each hand category (index = HandEval category). */
    val finalCat: DoubleArray,
)

private class Acc {
    var n = 0L
    var share = 0.0
    var shareSq = 0.0
    var win = 0L
    var tie = 0L
    var lose = 0L
    val cats = LongArray(9)

    fun add(hero: Int, bestOpp: Int, tiedOpps: Int) {
        n++
        cats[HandEval.category(hero)]++
        val s = when {
            hero > bestOpp -> { win++; 1.0 }
            hero == bestOpp -> { tie++; 1.0 / (tiedOpps + 1) }
            else -> { lose++; 0.0 }
        }
        share += s; shareSq += s * s
    }

    fun merge(o: Acc) {
        n += o.n; share += o.share; shareSq += o.shareSq
        win += o.win; tie += o.tie; lose += o.lose
        for (i in 0..8) cats[i] += o.cats[i]
    }

    fun result(exact: Boolean): EquityResult {
        val nn = n.coerceAtLeast(1).toDouble()
        val mean = share / nn
        val variance = (shareSq / nn - mean * mean).coerceAtLeast(0.0)
        return EquityResult(
            mean, win / nn, tie / nn, lose / nn, n, exact,
            if (exact) 0.0 else sqrt(variance / nn),
            DoubleArray(9) { cats[it] / nn },
        )
    }
}

object Equity {
    private const val EXACT_LIMIT = 1_500_000L

    suspend fun compute(hero: IntArray, board: IntArray, opponents: Int, rangePct: Int): EquityResult = coroutineScope {
        val allowed = if (rangePct >= 100) null else PreflopTable.allowed(rangePct)
        val dead = BooleanArray(52)
        hero.forEach { dead[it] = true }; board.forEach { dead[it] = true }
        val unseen = (0 until 52).filter { !dead[it] }.toIntArray()
        val missing = 5 - board.size

        if (opponents == 1 && missing <= 2) {
            var pairs = 0L
            for (i in unseen.indices) for (j in i + 1 until unseen.size) {
                if (allowed == null || allowed[PreflopTable.classIndex(unseen[i], unseen[j])]) pairs++
            }
            val boards = when (missing) { 0 -> 1L; 1 -> (unseen.size - 2).toLong(); else -> (unseen.size - 2L) * (unseen.size - 3) / 2 }
            if (pairs > 0 && pairs * boards <= EXACT_LIMIT) {
                val isAlive = { isActive }
                return@coroutineScope exact(hero, board, unseen, missing, allowed, isAlive).result(true)
            }
        }

        val iters = (400_000 / (1 + opponents)).coerceIn(60_000, 200_000)
        val workers = Runtime.getRuntime().availableProcessors().coerceIn(1, 6)
        val per = iters / workers
        val parts = (0 until workers).map { w ->
            async(Dispatchers.Default) {
                val alive = { isActive }
                monteCarlo(hero, board, unseen, missing, opponents, allowed, per, 7919L * (w + 1) + System.nanoTime(), alive)
            }
        }.awaitAll()
        val total = Acc()
        parts.forEach { total.merge(it) }
        total.result(false)
    }

    private fun exact(
        hero: IntArray, board: IntArray, u: IntArray, missing: Int,
        allowed: BooleanArray?, alive: () -> Boolean,
    ): Acc {
        val acc = Acc()
        val me = IntArray(7); val op = IntArray(7)
        me[0] = hero[0]; me[1] = hero[1]
        val bs = board.size
        for (k in 0 until bs) { me[2 + k] = board[k]; op[2 + k] = board[k] }
        val m = u.size
        val rest = IntArray(m)
        for (i in 0 until m) {
            if (!alive()) throw CancellationException()
            for (j in i + 1 until m) {
                if (allowed != null && !allowed[PreflopTable.classIndex(u[i], u[j])]) continue
                op[0] = u[i]; op[1] = u[j]
                var rn = 0
                for (k in 0 until m) if (k != i && k != j) rest[rn++] = u[k]
                when (missing) {
                    0 -> score(me, op, acc)
                    1 -> for (a in 0 until rn) {
                        me[6] = rest[a]; op[6] = rest[a]; score(me, op, acc)
                    }
                    else -> for (a in 0 until rn) {
                        me[5] = rest[a]; op[5] = rest[a]
                        for (b in a + 1 until rn) {
                            me[6] = rest[b]; op[6] = rest[b]; score(me, op, acc)
                        }
                    }
                }
            }
        }
        return acc
    }

    private fun score(me: IntArray, op: IntArray, acc: Acc) {
        val h = HandEval.eval(me, 7)
        val o = HandEval.eval(op, 7)
        acc.add(h, o, if (h == o) 1 else 0)
    }

    private fun monteCarlo(
        hero: IntArray, board: IntArray, unseen: IntArray, missing: Int, opponents: Int,
        allowed: BooleanArray?, iters: Int, seed: Long, alive: () -> Boolean,
    ): Acc {
        val rnd = SplittableRandom(seed)
        val deck = unseen.copyOf()
        val me = IntArray(7)
        me[0] = hero[0]; me[1] = hero[1]
        val bs = board.size
        for (k in 0 until bs) me[2 + k] = board[k]
        val ops = Array(opponents) { IntArray(7).also { o -> for (k in 0 until bs) o[2 + k] = board[k] } }
        val scores = IntArray(opponents)
        val acc = Acc()
        for (it in 0 until iters) {
            if ((it and 4095) == 0 && !alive()) throw CancellationException()
            var n = deck.size
            for (o in 0 until opponents) {
                var tries = 0
                while (true) {
                    var i = rnd.nextInt(n); var t = deck[i]; deck[i] = deck[n - 1]; deck[n - 1] = t; n--
                    i = rnd.nextInt(n); t = deck[i]; deck[i] = deck[n - 1]; deck[n - 1] = t; n--
                    val c1 = deck[n + 1]; val c2 = deck[n]
                    if (allowed == null || allowed[PreflopTable.classIndex(c1, c2)] || ++tries > 300) {
                        ops[o][0] = c1; ops[o][1] = c2
                        break
                    }
                    n += 2 // put both back and retry
                }
            }
            for (k in 0 until missing) {
                val i = rnd.nextInt(n); val t = deck[i]; deck[i] = deck[n - 1]; deck[n - 1] = t; n--
                val pos = 2 + bs + k
                me[pos] = t
                for (o in 0 until opponents) ops[o][pos] = t
            }
            val h = HandEval.eval(me, 7)
            var best = -1
            for (o in 0 until opponents) {
                val s = HandEval.eval(ops[o], 7); scores[o] = s; if (s > best) best = s
            }
            var tied = 0
            if (h == best) for (o in 0 until opponents) if (scores[o] == h) tied++
            acc.add(h, best, tied)
        }
        return acc
    }
}
