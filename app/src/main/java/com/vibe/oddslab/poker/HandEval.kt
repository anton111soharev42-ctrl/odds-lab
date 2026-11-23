package com.vibe.oddslab.poker

/**
 * Evaluates the best poker hand out of 1..7 cards.
 * Score = category shl 20 | five 4-bit kicker nibbles (rank + 1). Higher score wins.
 */
object HandEval {
    const val HIGH = 0
    const val PAIR = 1
    const val TWO_PAIR = 2
    const val TRIPS = 3
    const val STRAIGHT = 4
    const val FLUSH = 5
    const val FULL_HOUSE = 6
    const val QUADS = 7
    const val STRAIGHT_FLUSH = 8

    val NAMES = arrayOf(
        "Старшая карта", "Пара", "Две пары", "Тройка", "Стрит",
        "Флеш", "Фулл-хаус", "Каре", "Стрит-флеш"
    )

    private val PLURAL_GEN = arrayOf(
        "двоек", "троек", "четвёрок", "пятёрок", "шестёрок", "семёрок", "восьмёрок",
        "девяток", "десяток", "валетов", "дам", "королей", "тузов"
    )

    fun category(score: Int) = score ushr 20

    fun eval(cards: IntArray, n: Int): Int {
        val cnt = IntArray(13)
        val sm = IntArray(4)
        var all = 0
        for (i in 0 until n) {
            val c = cards[i]
            val r = c shr 2
            cnt[r]++
            sm[c and 3] = sm[c and 3] or (1 shl r)
            all = all or (1 shl r)
        }
        // With <= 7 cards a flush excludes quads / full house, so it can be checked first.
        for (s in 0..3) {
            if (Integer.bitCount(sm[s]) >= 5) {
                val h = straightHigh(sm[s])
                return if (h >= 0) make(STRAIGHT_FLUSH, packTop(h + 1, 1, 0, 0))
                else make(FLUSH, packTop(0, 0, sm[s], 5))
            }
        }
        var quad = -1; var t1 = -1; var t2 = -1; var p1 = -1; var p2 = -1
        for (r in 12 downTo 0) {
            when (cnt[r]) {
                4 -> if (quad < 0) quad = r
                3 -> if (t1 < 0) t1 = r else if (t2 < 0) t2 = r
                2 -> if (p1 < 0) p1 = r else if (p2 < 0) p2 = r
            }
        }
        if (quad >= 0) return make(QUADS, packTop(quad + 1, 1, all and (1 shl quad).inv(), 1))
        if (t1 >= 0 && (t2 >= 0 || p1 >= 0)) {
            val pr = maxOf(t2, p1)
            return make(FULL_HOUSE, packTop(((t1 + 1) shl 4) or (pr + 1), 2, 0, 0))
        }
        val st = straightHigh(all)
        if (st >= 0) return make(STRAIGHT, packTop(st + 1, 1, 0, 0))
        if (t1 >= 0) return make(TRIPS, packTop(t1 + 1, 1, all and (1 shl t1).inv(), 2))
        if (p2 >= 0) {
            val rest = all and (1 shl p1).inv() and (1 shl p2).inv()
            return make(TWO_PAIR, packTop(((p1 + 1) shl 4) or (p2 + 1), 2, rest, 1))
        }
        if (p1 >= 0) return make(PAIR, packTop(p1 + 1, 1, all and (1 shl p1).inv(), 3))
        return make(HIGH, packTop(0, 0, all, 5))
    }

    private fun make(cat: Int, kick: Int) = (cat shl 20) or kick

    /** Appends the top [n] ranks of [mask] after [prefixCount] already packed nibbles, pads to 5. */
    private fun packTop(prefix: Int, prefixCount: Int, mask: Int, n: Int): Int {
        var v = prefix
        var placed = prefixCount
        var taken = 0
        var r = 12
        while (taken < n && r >= 0) {
            if (((mask shr r) and 1) != 0) {
                v = (v shl 4) or (r + 1); placed++; taken++
            }
            r--
        }
        while (placed < 5) { v = v shl 4; placed++ }
        return v
    }

    fun straightHigh(mask: Int): Int {
        for (h in 12 downTo 4) if (((mask shr (h - 4)) and 0x1F) == 0x1F) return h
        if ((mask and 0x100F) == 0x100F) return 3 // wheel A-2-3-4-5
        return -1
    }

    private fun nib(score: Int, i: Int) = ((score shr (16 - 4 * i)) and 0xF) - 1

    fun describe(score: Int): String {
        val cat = category(score)
        val a = nib(score, 0)
        val b = nib(score, 1)
        fun r(x: Int) = Cards.rankLabel(x)
        return when (cat) {
            HIGH -> "Старшая карта ${r(a)}"
            PAIR -> "Пара ${PLURAL_GEN[a]}"
            TWO_PAIR -> "Две пары: ${r(a)} и ${r(b)}"
            TRIPS -> "Тройка ${PLURAL_GEN[a]}"
            STRAIGHT -> "Стрит до ${r(a)}"
            FLUSH -> "Флеш, старшая ${r(a)}"
            FULL_HOUSE -> "Фулл-хаус: ${r(a)} на ${r(b)}"
            QUADS -> "Каре ${PLURAL_GEN[a]}"
            else -> if (a == 12) "Роял-флеш" else "Стрит-флеш до ${r(a)}"
        }
    }
}
