package com.vibe.oddslab.poker

class PokerOutcome(
    val equity: EquityResult,
    val handNow: String?,
    val preflopClass: String?,
    val preflopTopPct: Double?,
    /** Cards that improve hero's hand category on the next street (flop/turn only). */
    val outs: List<Int>,
    /** Probability to hit at least one out by the river. */
    val outsHitByRiver: Double?,
    val unseenCount: Int,
)

class Decision(val verdict: String, val color: Int, val lines: List<String>)

object PokerAnalysis {
    suspend fun analyze(hero: IntArray, board: IntArray, opponents: Int, rangePct: Int): PokerOutcome {
        val eq = Equity.compute(hero, board, opponents, rangePct)
        val all = hero + board
        val handNow = if (board.isNotEmpty()) HandEval.describe(HandEval.eval(all, all.size)) else null
        var cls: String? = null
        var top: Double? = null
        if (board.isEmpty()) {
            val idx = PreflopTable.classIndex(hero[0], hero[1])
            cls = PreflopTable.className(idx)
            top = PreflopTable.table.endPct[idx]
        }
        val dead = BooleanArray(52).also { d -> all.forEach { d[it] = true } }
        val unseen = (0 until 52).filter { !dead[it] }
        val outs = mutableListOf<Int>()
        var hit: Double? = null
        if (board.size == 3 || board.size == 4) {
            val curCat = HandEval.category(HandEval.eval(all, all.size))
            val withHero = IntArray(all.size + 1).also { all.copyInto(it) }
            val boardOnly = IntArray(board.size + 1).also { board.copyInto(it) }
            for (c in unseen) {
                withHero[all.size] = c
                boardOnly[board.size] = c
                val nc = HandEval.category(HandEval.eval(withHero, withHero.size))
                val bc = HandEval.category(HandEval.eval(boardOnly, boardOnly.size))
                if (nc > curCat && nc > bc) outs += c
            }
            val u = unseen.size.toDouble(); val o = outs.size.toDouble()
            hit = if (board.size == 3) 1 - ((u - o) * (u - o - 1)) / (u * (u - 1)) else o / u
        }
        return PokerOutcome(eq, handNow, cls, top, outs, hit, unseen.size)
    }

    const val GOOD = 0
    const val MID = 1
    const val BAD = 2

    fun decide(eq: Double, opponents: Int, pot: Double, toCall: Double): Decision {
        val fair = 1.0 / (opponents + 1)
        val lines = mutableListOf<String>()
        lines += "Средняя доля банка при ${opponents + 1} игроках: ${pct(fair)}. У тебя ${pct(eq)} (×${"%.2f".format(eq / fair)})."
        if (toCall > 0 && pot > 0) {
            val need = toCall / (pot + toCall)
            val ev = eq * (pot + toCall) - toCall
            lines += "Шансы банка: надо вложить ${num(toCall)} в итоговый банк ${num(pot + toCall)} → нужно ≥ ${pct(need)} эквити."
            lines += "EV колла: ${sign(ev)}${num(ev)} фишек (${sign(ev)}${"%.0f".format(ev / toCall * 100)}% от колла)."
            return if (ev > 0) {
                if (eq >= 0.5 && eq >= 1.8 * fair) {
                    lines += "Эквити сильно выше нужного — выгоднее повышать (рейз), чтобы соперники платили больше."
                    Decision("РЕЙЗ", GOOD, lines)
                } else {
                    lines += "Запас по эквити: +${pct(eq - need)} к минимально нужному."
                    Decision("КОЛЛ", GOOD, lines)
                }
            } else {
                val implied = toCall / eq - pot - toCall
                lines += "Не хватает ${pct(need - eq)} эквити."
                if (eq > 0.0 && implied.isFinite()) lines += "Колл окупится, только если на следующих улицах добрать ещё ≥ ${num(implied)} фишек (implied odds)."
                Decision("ПАС", BAD, lines)
            }
        }
        lines += "Введи банк и сумму колла — посчитаю точное EV решения."
        return when {
            eq >= 0.5 && eq >= 1.6 * fair -> {
                lines += "Ты фаворит раздачи: ставь на вэлью (≈ ½–¾ банка)."
                Decision("СТАВКА / РЕЙЗ", GOOD, lines)
            }
            eq >= fair -> {
                lines += "Выше среднего, но не доминируешь: чек или небольшая ставка, на крупную ставку — сверяйся с шансами банка."
                Decision("ЧЕК / КОЛЛ", MID, lines)
            }
            else -> {
                lines += "Ниже средней доли: чек, а на ставку — пас, если шансы банка не позволяют колл."
                Decision("ЧЕК / ПАС", BAD, lines)
            }
        }
    }

    fun pct(x: Double) = "%.1f%%".format(x * 100)
    private fun num(x: Double) = if (kotlin.math.abs(x - Math.round(x)) < 1e-9) "%.0f".format(kotlin.math.abs(x)) else "%.2f".format(kotlin.math.abs(x))
    private fun sign(x: Double) = if (x >= 0) "+" else "−"
}
