package com.vibe.oddslab.bj

import kotlin.math.max

/** Card values: 1 = Ace, 2..9, 10 = any ten-value card. Shoe = IntArray(10), index = value - 1. */
data class BjRules(
    val decks: Int = 6,
    val h17: Boolean = false,
    val das: Boolean = true,
    val surrender: Boolean = true,
    val bjPayout: Double = 1.5,
    val peek: Boolean = true,
)

enum class BjAction(val ru: String) {
    STAND("Стоять"), HIT("Взять карту"), DOUBLE("Удвоить"), SPLIT("Сплит"), SURRENDER("Сдаться")
}

class BjAnalysis(
    /** EV per unit of the initial bet. */
    val evs: Map<BjAction, Double>,
    val best: BjAction?,
    val total: Int,
    val soft: Boolean,
    val isBlackjack: Boolean,
    val blackjackEv: Double?,
    /** Dealer final totals 17,18,19,20,21,bust — given the dealer has no blackjack. */
    val dealer: DoubleArray,
    val pDealerBj: Double,
    val bustOnHit: Double,
    val p10: Double,
    /** EV per unit of insurance bet (only when the up-card is an ace). */
    val insuranceEv: Double?,
)

object BjEngine {
    val STD = intArrayOf(4, 4, 4, 4, 4, 4, 4, 4, 4, 16)

    fun freshShoe(decks: Int) = IntArray(10) { STD[it] * decks }

    fun bestTotal(hard: Int, ace: Boolean) = if (ace && hard + 10 <= 21) hard + 10 else hard

    fun label(v: Int) = if (v == 1) "A" else v.toString()

    /** [shoe] must already exclude the player's cards and the dealer up-card. */
    fun analyze(player: List<Int>, up: Int, shoe: IntArray, rules: BjRules): BjAnalysis {
        val s = shoe.copyOf()
        val tot = s.sum()
        val hard = player.sum()
        val ace = 1 in player
        val best = bestTotal(hard, ace)
        val soft = ace && hard + 10 <= 21
        val bjCard = when (up) { 1 -> 10; 10 -> 1; else -> 0 }
        val pDealerBj = if (bjCard == 0 || tot == 0) 0.0 else s[bjCard - 1].toDouble() / tot
        val p10 = if (tot == 0) 0.0 else s[9].toDouble() / tot
        val insurance = if (up == 1) 3 * p10 - 1 else null
        val d = dealerDist(up, s, rules)

        val isBj = player.size == 2 && best == 21
        if (isBj) {
            val ev = if (rules.peek && up == 10) rules.bjPayout else (1 - pDealerBj) * rules.bjPayout
            return BjAnalysis(emptyMap(), null, 21, soft, true, ev, d, pDealerBj, 0.0, p10, insurance)
        }

        val solver = Solver(d)
        val evs = linkedMapOf<BjAction, Double>()
        evs[BjAction.STAND] = standEv(best, d)
        if (best < 21) evs[BjAction.HIT] = solver.hit(hard, ace, s, tot, 0L)
        val two = player.size == 2
        if (two && best < 21) evs[BjAction.DOUBLE] = solver.double(hard, ace, s, tot)
        if (two && player[0] == player[1]) evs[BjAction.SPLIT] = splitEv(player[0], s, tot, rules, solver)
        if (two && rules.surrender) evs[BjAction.SURRENDER] = -0.5

        if (!rules.peek && pDealerBj > 0) {
            // European no-hole-card: on dealer BJ every extra bet (double / split) is lost too.
            for ((a, ev) in evs.entries.toList()) {
                val risk = when (a) { BjAction.DOUBLE, BjAction.SPLIT -> 2.0; else -> 1.0 }
                evs[a] = pDealerBj * -risk + (1 - pDealerBj) * ev
            }
        }

        var bust = 0.0
        if (tot > 0) for (v in 1..10) if (hard + v > 21) bust += s[v - 1].toDouble() / tot
        val bestAction = evs.maxByOrNull { it.value }?.key
        return BjAnalysis(evs, bestAction, best, soft, false, null, d, pDealerBj, bust, p10, insurance)
    }

    fun standEv(pt: Int, d: DoubleArray): Double {
        if (pt > 21) return -1.0
        var ev = d[5]
        for (i in 0..4) {
            val dt = 17 + i
            if (pt > dt) ev += d[i] else if (pt < dt) ev -= d[i]
        }
        return ev
    }

    /** Dealer final distribution [17,18,19,20,21,bust] conditioned on no dealer blackjack. */
    fun dealerDist(up: Int, shoe: IntArray, rules: BjRules): DoubleArray {
        val out = DoubleArray(6)
        val s = shoe.copyOf()
        val total = s.sum()
        val bjCard = when (up) { 1 -> 10; 10 -> 1; else -> 0 }
        val excl = if (bjCard != 0) s[bjCard - 1] else 0
        val denom = (total - excl).toDouble()
        if (denom <= 0) return out
        for (v in 1..10) {
            if (v == bjCard) continue
            val c = s[v - 1]
            if (c == 0) continue
            s[v - 1]--
            dealerRec(up + v, up == 1 || v == 1, s, total - 1, c / denom, out, rules.h17)
            s[v - 1]++
        }
        return out
    }

    private fun dealerRec(hard: Int, ace: Boolean, s: IntArray, tot: Int, prob: Double, out: DoubleArray, h17: Boolean) {
        val soft = ace && hard + 10 <= 21
        val best = if (soft) hard + 10 else hard
        if (best > 21) { out[5] += prob; return }
        if (best >= 17 && !(h17 && soft && best == 17)) { out[best - 17] += prob; return }
        if (tot <= 0) { out[5] += prob; return }
        for (v in 1..10) {
            val c = s[v - 1]
            if (c == 0) continue
            s[v - 1]--
            dealerRec(hard + v, ace || v == 1, s, tot - 1, prob * c / tot, out, h17)
            s[v - 1]++
        }
    }

    private fun splitEv(v: Int, s: IntArray, tot: Int, rules: BjRules, solver: Solver): Double {
        if (tot <= 0) return -2.0
        var ev = 0.0
        for (v2 in 1..10) {
            val c = s[v2 - 1]
            if (c == 0) continue
            val p = c.toDouble() / tot
            s[v2 - 1]--
            val hard = v + v2
            val ace = v == 1 || v2 == 1
            val hand = if (v == 1) {
                standEv(bestTotal(hard, ace), solver.d) // split aces get one card
            } else {
                var b = solver.standOrHit(hard, ace, s, tot - 1, 1L shl (5 * (v2 - 1)))
                if (rules.das) b = max(b, solver.double(hard, ace, s, tot - 1))
                b
            }
            ev += p * hand
            s[v2 - 1]++
        }
        return 2 * ev
    }

    /**
     * Composition-dependent player solver. Draw probabilities follow the actual shoe
     * (card removal included); dealer outcome probabilities are fixed per decision.
     */
    private class Solver(val d: DoubleArray) {
        private val memo = HashMap<Long, Double>()

        fun standOrHit(hard: Int, ace: Boolean, s: IntArray, tot: Int, key: Long): Double {
            val best = bestTotal(hard, ace)
            if (best > 21) return -1.0
            val st = standEv(best, d)
            if (best == 21) return st
            return max(st, hit(hard, ace, s, tot, key))
        }

        fun hit(hard: Int, ace: Boolean, s: IntArray, tot: Int, key: Long): Double {
            val k = key or (hard.toLong() shl 50) or (if (ace) 1L shl 56 else 0L)
            memo[k]?.let { return it }
            if (tot <= 0) return standEv(bestTotal(hard, ace), d)
            var ev = 0.0
            for (v in 1..10) {
                val c = s[v - 1]
                if (c == 0) continue
                val p = c.toDouble() / tot
                val nh = hard + v
                if (nh > 21) { ev -= p; continue }
                s[v - 1]--
                ev += p * standOrHit(nh, ace || v == 1, s, tot - 1, key + (1L shl (5 * (v - 1))))
                s[v - 1]++
            }
            memo[k] = ev
            return ev
        }

        fun double(hard: Int, ace: Boolean, s: IntArray, tot: Int): Double {
            if (tot <= 0) return 2 * standEv(bestTotal(hard, ace), d)
            var ev = 0.0
            for (v in 1..10) {
                val c = s[v - 1]
                if (c == 0) continue
                val p = c.toDouble() / tot
                val nh = hard + v
                ev += if (nh > 21) -2 * p else 2 * p * standEv(bestTotal(nh, ace || v == 1), d)
            }
            return ev
        }
    }
}
