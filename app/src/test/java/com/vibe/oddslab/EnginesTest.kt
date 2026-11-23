package com.vibe.oddslab

import com.vibe.oddslab.bj.BjAction
import com.vibe.oddslab.bj.BjEngine
import com.vibe.oddslab.bj.BjRules
import com.vibe.oddslab.poker.Cards
import com.vibe.oddslab.poker.Equity
import com.vibe.oddslab.poker.HandEval
import com.vibe.oddslab.poker.PreflopTable
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnginesTest {
    /** "Ah" -> card */
    private fun c(s: String): Int {
        val r = Cards.RANKS.indexOf(s[0].uppercaseChar())
        val suit = "shdc".indexOf(s[1])
        return Cards.card(r, suit)
    }
    private fun cs(vararg s: String) = s.map { c(it) }.toIntArray()
    private fun ev(vararg s: String) = cs(*s).let { HandEval.eval(it, it.size) }

    @Test fun categories() {
        assertEquals(HandEval.STRAIGHT_FLUSH, HandEval.category(ev("Ah", "Kh", "Qh", "Jh", "Th", "2c", "3d")))
        assertEquals(HandEval.STRAIGHT, HandEval.category(ev("Ah", "2c", "3d", "4s", "5h", "9c", "Kd")))
        assertEquals(HandEval.FULL_HOUSE, HandEval.category(ev("Ah", "Ac", "Ad", "Ks", "Kh", "Kc", "2d")))
        assertEquals(HandEval.QUADS, HandEval.category(ev("9h", "9c", "9d", "9s", "Kh", "Kc", "Kd")))
        assertEquals(HandEval.TWO_PAIR, HandEval.category(ev("Ah", "Ac", "Kd", "Ks", "2h", "2c", "7d")))
        assertEquals(HandEval.FLUSH, HandEval.category(ev("2h", "7h", "9h", "Jh", "Kh", "Kc", "Kd")))
    }

    @Test fun comparisons() {
        assertTrue(ev("Ah", "Ac", "Kd", "Ks", "Qh", "2c", "3d") > ev("Ah", "Ac", "Kd", "Ks", "Jh", "2c", "3d"))
        assertTrue(ev("6h", "2c", "3d", "4s", "5h", "Tc", "Kd") > ev("Ah", "2c", "3d", "4s", "5h", "Tc", "Kd"))
        assertTrue(ev("Ah", "Ac", "Ad", "Ks", "Kh", "2c", "3d") > ev("Kh", "Kc", "Kd", "As", "Ah", "2c", "3d"))
        // Kickers beyond five cards do not matter
        assertEquals(ev("Ah", "Kc", "Qd", "Js", "9h", "3c", "2d"), ev("Ah", "Kc", "Qd", "Js", "9h", "4c", "2d"))
    }

    @Test fun equityAAvsRandom() = runBlocking {
        val r = Equity.compute(cs("Ah", "Ad"), IntArray(0), 1, 100)
        assertEquals(0.852, r.equity, 0.01)
    }

    @Test fun equity72vsRandom() = runBlocking {
        val r = Equity.compute(cs("7h", "2d"), IntArray(0), 1, 100)
        assertEquals(0.346, r.equity, 0.012)
    }

    @Test fun equityAAvsThree() = runBlocking {
        val r = Equity.compute(cs("Ah", "Ad"), IntArray(0), 3, 100)
        assertEquals(0.638, r.equity, 0.012)
    }

    @Test fun royalIsNut() = runBlocking {
        val r = Equity.compute(cs("Ah", "Kh"), cs("Qh", "Jh", "Th", "2c", "3d"), 1, 100)
        assertTrue(r.exact)
        assertEquals(1.0, r.equity, 1e-9)
    }

    @Test fun flopExactRuns() = runBlocking {
        val r = Equity.compute(cs("Ah", "Kh"), cs("Qh", "7h", "2c"), 1, 100)
        assertTrue(r.exact)
        assertEquals(1081L * 990L, r.samples)
    }

    @Test fun preflopRanking() {
        val t = PreflopTable.table
        val aa = 12 * 13 + 12
        val off72 = 0 * 13 + 5 // lo*13+hi -> 7-2 offsuit
        assertTrue(t.endPct[aa] < 0.5)
        assertTrue(t.startPct[off72] > 95.0)
    }

    private fun pre(a: String, b: String, eq: Double, pot: Double, call: Double, pos: Int, stack: Double, range: Int = 30): String {
        val h = cs(a, b)
        val idx = PreflopTable.classIndex(h[0], h[1])
        val ctx = com.vibe.oddslab.poker.PokerAnalysis.Ctx(
            true, PreflopTable.table.endPct[idx], PreflopTable.className(idx), pos, stack, 10.0, range, h,
        )
        return com.vibe.oddslab.poker.PokerAnalysis.decide(eq, 1, pot, call, ctx).verdict
    }

    @Test fun preflopAdvice() {
        assertEquals("РЕЙЗ", pre("As", "Kd", 0.65, 0.0, 0.0, 0, 1000.0))
        assertEquals("ПАС", pre("Ks", "9d", 0.58, 0.0, 0.0, 0, 1000.0))
        assertEquals("РЕЙЗ", pre("Ks", "9d", 0.58, 0.0, 0.0, 2, 1000.0))
        assertEquals("ЧЕК", pre("7s", "2d", 0.35, 15.0, 0.0, 4, 1000.0))
        assertEquals("ВА-БАНК", pre("As", "5d", 0.55, 0.0, 5.0, 3, 80.0))
        assertEquals("3-БЕТ", pre("Qs", "Qd", 0.8, 45.0, 30.0, 1, 1000.0))
        assertEquals("ПАС", pre("Js", "8d", 0.38, 45.0, 30.0, 2, 1000.0))
        assertEquals("КОЛЛ", pre("8s", "7s", 0.38, 45.0, 30.0, 2, 1000.0))
    }

    private val rules = BjRules(decks = 6, h17 = false, das = true, surrender = false)

    private fun best(player: List<Int>, up: Int, r: BjRules = rules): BjAction {
        val shoe = BjEngine.freshShoe(r.decks)
        (player + up).forEach { shoe[it - 1]-- }
        return BjEngine.analyze(player, up, shoe, r).best!!
    }

    @Test fun basicStrategySpots() {
        assertEquals(BjAction.DOUBLE, best(listOf(5, 6), 6))
        assertEquals(BjAction.HIT, best(listOf(10, 2), 2))
        assertEquals(BjAction.STAND, best(listOf(10, 2), 4))
        assertEquals(BjAction.HIT, best(listOf(1, 7), 9))
        assertEquals(BjAction.STAND, best(listOf(1, 7), 7))
        assertEquals(BjAction.SPLIT, best(listOf(8, 8), 10))
        assertEquals(BjAction.STAND, best(listOf(9, 9), 7))
        assertEquals(BjAction.SPLIT, best(listOf(1, 1), 6))
        assertEquals(BjAction.STAND, best(listOf(10, 10), 6))
        assertEquals(BjAction.HIT, best(listOf(10, 6), 7))
        assertEquals(BjAction.DOUBLE, best(listOf(1, 6), 4))
        assertEquals(BjAction.SURRENDER, best(listOf(10, 6), 10, rules.copy(surrender = true)))
    }

    @Test fun dealerBustUp6() {
        val shoe = BjEngine.freshShoe(6).also { it[5]-- }
        val d = BjEngine.dealerDist(6, shoe, rules)
        assertEquals(0.42, d[5], 0.01)
        assertEquals(1.0, d.sum(), 1e-9)
    }

    @Test fun richShoeTakesInsurance() {
        val shoe = BjEngine.freshShoe(1)
        for (v in 2..6) shoe[v - 1] = 0 // all low cards gone
        shoe[9]-- ; shoe[8]--; shoe[0]--
        val a = BjEngine.analyze(listOf(10, 9), 1, shoe, BjRules(decks = 1))
        assertTrue(a.insuranceEv!! > 0)
    }
}
