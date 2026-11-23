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

    class Position(val name: String, val full: String, val open: Double, val push: Double, val ip: Boolean)

    val POSITIONS = listOf(
        Position("Ранняя", "ранней позиции", 14.0, 13.0, true),
        Position("Средняя", "средней позиции", 20.0, 18.0, true),
        Position("Поздняя", "поздней позиции (CO/баттон)", 38.0, 32.0, true),
        Position("МБ", "малого блайнда", 35.0, 45.0, false),
        Position("ББ", "большого блайнда", 0.0, 50.0, false),
    )

    class Ctx(
        val preflop: Boolean,
        val top: Double?,
        val cls: String?,
        val pos: Int,
        val stack: Double,
        val bb: Double,
        val range: Int,
        val hero: IntArray,
    )

    fun decide(eq: Double, opponents: Int, pot: Double, toCall: Double, ctx: Ctx): Decision {
        val bb = if (ctx.bb > 0) ctx.bb else 0.0
        if (ctx.preflop && bb > 0 && ctx.top != null && ctx.cls != null) return preflop(eq, pot, toCall, ctx, ctx.top, ctx.cls)
        val stack = if (ctx.stack > 0) ctx.stack else 0.0
        fun chips(x: Double) = num(x) + if (bb > 0) " (${"%.1f".format(x / bb)} ББ)" else ""
        val fair = 1.0 / (opponents + 1)
        val lines = mutableListOf<String>()
        lines += "Средняя доля банка при ${opponents + 1} игроках: ${pct(fair)}. У тебя ${pct(eq)} (×${"%.2f".format(eq / fair)})."
        if (stack > 0 && pot > 0) {
            lines += "SPR (стек ÷ банк): ${"%.1f".format(stack / pot)}" + if (stack / pot <= 2.5) " — низкий: с хорошей рукой ты уже «привязан» к банку." else ""
        }
        if (toCall > 0 && pot > 0) {
            val call = if (stack > 0) minOf(toCall, stack) else toCall
            val allIn = stack > 0 && call >= stack
            val need = call / (pot + call)
            val ev = eq * (pot + call) - call
            lines += "Шансы банка: вкладываешь ${chips(call)} в итоговый банк ${chips(pot + call)} → нужно ≥ ${pct(need)} эквити."
            if (allIn) lines += "Колл = весь твой стек (${chips(stack)})."
            lines += "EV колла: ${sign(ev)}${num(ev)} фишек (${sign(ev)}${"%.0f".format(kotlin.math.abs(ev / call * 100))}% от колла)."
            if (ev > 0) {
                if (eq >= 0.5 && eq >= 1.8 * fair && !allIn) {
                    val r = toCall * 3 + pot
                    if (stack > 0 && r >= stack * 0.6) {
                        lines += "Рейз почти на весь стек — проще сразу ва-банк (${chips(stack)})."
                        return Decision("ВА-БАНК", GOOD, lines)
                    }
                    lines += "Эквити сильно выше нужного — выгоднее повышать, чтобы соперники платили больше. Рейз ≈ до ${chips(r)}."
                    return Decision("РЕЙЗ", GOOD, lines)
                }
                lines += "Запас по эквити: +${pct(eq - need)} к минимально нужному."
                return Decision(if (allIn) "КОЛЛ ВА-БАНК" else "КОЛЛ", GOOD, lines)
            }
            val implied = call / eq - pot - call
            lines += "Не хватает ${pct(need - eq)} эквити."
            if (!allIn && eq > 0.0 && implied.isFinite()) {
                lines += "Колл окупится, только если на следующих улицах добрать ещё ≥ ${chips(implied)} (implied odds)" +
                    if (stack > 0 && implied > stack - call) " — а у тебя за спиной меньше, так что точно пас." else "."
            }
            return Decision("ПАС", BAD, lines)
        }
        if (pot <= 0) lines += "Введи банк (и сумму колла, если против тебя ставка) — посчитаю точное EV решения и размеры ставок."
        return when {
            eq >= 0.5 && eq >= 1.6 * fair -> {
                if (pot > 0 && stack > 0 && stack / pot <= 2.5) {
                    lines += "Ты фаворит при низком SPR — ставь ва-банк (${chips(stack)})."
                    Decision("ВА-БАНК", GOOD, lines)
                } else {
                    lines += if (pot > 0) "Ты фаворит раздачи: ставь на вэлью ½–¾ банка = ${chips(pot * 0.5)} – ${chips(pot * 0.75)}."
                    else "Ты фаворит раздачи: ставь на вэлью ½–¾ банка."
                    Decision("СТАВКА", GOOD, lines)
                }
            }
            eq >= fair -> {
                lines += if (pot > 0) "Выше среднего, но не доминируешь: чек или небольшая ставка ⅓ банка = ${chips(pot / 3)}."
                else "Выше среднего, но не доминируешь: чек или небольшая ставка."
                Decision("ЧЕК", MID, lines)
            }
            else -> {
                lines += "Ниже средней доли: чек, а на ставку соперника — пас, если шансы банка не позволяют колл."
                Decision("ЧЕК / ПАС", BAD, lines)
            }
        }
    }

    private fun preflop(eq: Double, pot: Double, toCall: Double, ctx: Ctx, top: Double, cls: String): Decision {
        val bb = ctx.bb
        val p = POSITIONS.getOrElse(ctx.pos) { POSITIONS[0] }
        val stack = if (ctx.stack > 0) ctx.stack else 100 * bb
        val sBB = stack / bb
        fun chips(x: Double) = "${num(x)} (${"%.1f".format(x / bb)} ББ)"
        val posted = when (ctx.pos) { 4 -> bb; 3 -> bb / 2; else -> 0.0 }
        val facingRaise = toCall > bb - posted + bb * 0.01
        val potEff = if (pot > 0) pot else 1.5 * bb + if (facingRaise) toCall + posted else 0.0
        val limpers = if (!facingRaise && pot > 0) maxOf(0, Math.round((pot - 1.5 * bb) / bb).toInt()) else 0
        val lines = mutableListOf(
            "Рука $cls: топ ${"%.1f".format(top)}% стартовых рук. Стек: ${chips(stack)}" + (if (ctx.stack > 0) "." else " — не задан, считаю 100 ББ."),
        )
        if (pot <= 0) lines += "Банк не введён — оцениваю в ${chips(potEff)}."
        fun res(verdict: String, tone: Int, extra: String?): Decision {
            if (extra != null) lines += extra
            return Decision(verdict, tone, lines)
        }

        if (sBB <= 15) {
            lines += "Короткий стек (≤ 15 ББ): играем только «ва-банк или пас» — так соперники не могут тебя пережать."
            if (!facingRaise) {
                val pushPct = (p.push * Math.pow(10 / sBB, 0.6)).coerceIn(4.0, 90.0)
                lines += "С ${p.full} при ${"%.1f".format(sBB)} ББ пушим топ ${"%.0f".format(pushPct)}% рук."
                if (top <= pushPct) return res("ВА-БАНК", GOOD, "Ставь весь стек: ${chips(stack)}.")
                if (ctx.pos == 4 && toCall <= 0) return res("ЧЕК", MID, "Ты на большом блайнде и платить не нужно — бесплатно смотришь флоп.")
                return res("ПАС", BAD, "Рука слабее диапазона пуша.")
            }
            val call = minOf(toCall, stack)
            val need = call / (potEff + call)
            lines += "Нужно доставить ${chips(call)} в банк ${chips(potEff + call)} → нужно ≥ ${pct(need)} эквити, у тебя ${pct(eq)}."
            if (ctx.range >= 100) lines += "⚠ Против рейза выбери диапазон соперника «Топ 15–30%» — против «любых» рук эквити завышено."
            if (top <= 7) return res("ВА-БАНК", GOOD, "Сильнейшая рука — пушим весь стек ${chips(stack)} поверх рейза.")
            if (eq >= need) {
                val ev = eq * (potEff + call) - call
                return res(if (call >= stack) "КОЛЛ ВА-БАНК" else "ВА-БАНК", GOOD, "EV ${sign(ev)}${num(ev)} фишек.")
            }
            return res("ПАС", BAD, "Не хватает ${pct(need - eq)} эквити.")
        }

        if (!facingRaise) {
            if (ctx.pos == 4) {
                if (top <= 12) {
                    val to = minOf(stack, (3.5 + limpers) * bb)
                    return res("РЕЙЗ", GOOD, "Сильная рука на ББ — повышай до ${chips(to)}, наказывая лимперов.")
                }
                return res("ЧЕК", MID, "Ты на большом блайнде, рейза не было — чекай и смотри флоп бесплатно.")
            }
            val size = (if (ctx.pos == 3) 3.0 else 2.5) + limpers
            val to = minOf(stack, size * bb)
            lines += "С ${p.full} открываемся рейзом с топ ${"%.0f".format(p.open)}% рук." +
                if (limpers > 0) " Лимперов перед тобой: $limpers — +1 ББ к рейзу за каждого." else ""
            if (top <= p.open) return res("РЕЙЗ", GOOD, "Рейз до ${chips(to)}. Лимп (просто колл ББ) хуже — теряешь инициативу.")
            if (limpers > 0 && top <= p.open * 1.7 && sBB >= 40) {
                return res("КОЛЛ", MID, "Есть лимперы и глубокий стек — можно дёшево войти (${chips(bb - posted)}) ради сета/флеша/стрита. Без попадания на флопе — сбрасывай.")
            }
            return res("ПАС", BAD, "Рука вне диапазона открытия с этой позиции.")
        }

        val call = minOf(toCall, stack)
        val need = call / (potEff + call)
        val raiseTo = toCall + posted
        val raiseBB = raiseTo / bb
        val threeBet = if (raiseBB <= 4) 5.0 else if (raiseBB <= 8) 3.5 else 2.5
        lines += "Против рейза до ${chips(raiseTo)}: доставить ${chips(call)} в банк ${chips(potEff + call)} → нужно ≥ ${pct(need)} эквити, у тебя ${pct(eq)}."
        if (ctx.range >= 100) lines += "⚠ Тот, кто рейзит, держит не «любые» руки — выбери диапазон «Топ 15–30%», чтобы эквити было честным."
        if (call >= stack * 0.4) {
            if (top <= threeBet + 2 || eq >= need) return res(if (call >= stack) "КОЛЛ ВА-БАНК" else "ВА-БАНК", GOOD, "Колл съедает большую часть стека — играем сразу весь стек.")
            return res("ПАС", BAD, "Не хватает ${pct(need - eq)} эквити, а на кону почти весь стек.")
        }
        if (top <= threeBet) {
            val to = raiseTo * if (p.ip) 3.0 else 3.7
            if (to >= stack * 0.4) return res("ВА-БАНК", GOOD, "Топ ${"%.1f".format(threeBet)}% рук: ре-рейз почти на весь стек — ставь сразу ${chips(stack)}.")
            return res("3-БЕТ", GOOD, "Топ ${"%.1f".format(threeBet)}% рук: ре-рейз до ${chips(to)}" + if (p.ip) " (×3 от рейза)." else " (из блайндов ×3.5–4 от рейза).")
        }
        if (eq >= need && top <= 22) return res("КОЛЛ", GOOD, "Рука в диапазоне колла, и колл выгоден уже по прямым шансам банка.")
        var spec = ""
        if (ctx.hero.size == 2) {
            val r1 = Cards.rank(ctx.hero[0]); val r2 = Cards.rank(ctx.hero[1])
            val suited = Cards.suit(ctx.hero[0]) == Cards.suit(ctx.hero[1])
            val gap = kotlin.math.abs(r1 - r2)
            if (r1 == r2) spec = "Карманная пара — ловим сет (≈ 12% на флопе)"
            else if (suited && (gap <= 2 || maxOf(r1, r2) == 12)) spec = "Одномастная рука — ловим флеш/стрит"
        }
        if (spec.isNotEmpty()) {
            val ratio = (stack - call) / call
            lines += "$spec. За спиной ${"%.0f".format(ratio)}× колла" + if (ratio >= 15) " — достаточно для implied odds (нужно ≥ 15×)." else " — мало, нужно ≥ 15×."
            if (ratio >= 15) return res("КОЛЛ", MID, "Колл ради сильного попадания. Без сета/дро на флопе — сбрасывай на ставку.")
        }
        return res(
            "ПАС", BAD,
            if (eq < need) "Не хватает ${pct(need - eq)} эквити, а рука не тянет на implied odds."
            else "Рука слабовата против рейза: часто будешь доминирован (тот же туз/король с худшим кикером).",
        )
    }

    fun pct(x: Double) = "%.1f%%".format(x * 100)
    private fun num(x: Double) = if (kotlin.math.abs(x - Math.round(x)) < 1e-9) "%.0f".format(kotlin.math.abs(x)) else "%.2f".format(kotlin.math.abs(x))
    private fun sign(x: Double) = if (x >= 0) "+" else "−"
}
