package com.vibe.oddslab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibe.oddslab.PokerState
import com.vibe.oddslab.poker.Cards
import com.vibe.oddslab.poker.HandEval
import com.vibe.oddslab.poker.PokerAnalysis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun PokerScreen(st: PokerState) {
    val snapshot = st.slots.toList()
    LaunchedEffect(snapshot, st.opponents, st.rangePct) {
        val h0 = snapshot[0]; val h1 = snapshot[1]
        val board = snapshot.subList(2, 7).filterNotNull()
        if (h0 == null || h1 == null || board.size in 1..2) {
            st.result = null; st.computing = false
            return@LaunchedEffect
        }
        delay(150)
        st.computing = true
        val r = withContext(Dispatchers.Default) {
            PokerAnalysis.analyze(intArrayOf(h0, h1), board.toIntArray(), st.opponents, st.rangePct)
        }
        st.result = r
        st.computing = false
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Section {
            Row(verticalAlignment = Alignment.Bottom) {
                Column {
                    Text("Твои карты", color = Dim, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        for (i in 0..1) CardSlot(st.slots[i], st.selected == i) { onSlotTap(st, i) }
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Стол: флоп · тёрн · ривер", color = Dim, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (i in 2..6) {
                            if (i == 5) Spacer(Modifier.width(3.dp))
                            CardSlot(st.slots[i], st.selected == i, small = true) { onSlotTap(st, i) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            CardPicker(st)
        }

        Section("Ситуация") {
            Stepper("Соперников в раздаче", st.opponents, 1, 9) { st.opponents = it }
            Spacer(Modifier.height(8.dp))
            Text("Какие руки у соперников", color = Dim, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf(100 to "Любые", 50 to "Топ 50%", 30 to "Топ 30%", 15 to "Топ 15%").forEach { (p, l) ->
                    PickButton(l, Modifier.weight(1f), selected = st.rangePct == p, fontSize = 13.sp, height = 40.dp) { st.rangePct = p }
                }
            }
            Text(
                "Кто доставляет ставки, обычно держит руки сильнее случайных — сужай диапазон против тайтовых игроков.",
                color = Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text("Твоя позиция", color = Dim, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                PokerAnalysis.POSITIONS.forEachIndexed { i, p ->
                    PickButton(p.name, Modifier.weight(1f), selected = st.position == i, fontSize = 12.sp, height = 40.dp) { st.position = i }
                }
            }
            Text(
                "Ранняя — первые 3 места после блайндов, средняя — следующие, поздняя — CO и баттон (дилер).",
                color = Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("Твой стек", st.stack, Modifier.weight(1f)) { st.stack = it }
                NumField("Большой блайнд", st.bb, Modifier.weight(1f)) { st.bb = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("Банк сейчас", st.pot, Modifier.weight(1f)) { st.pot = it }
                NumField("Нужно доставить", st.toCall, Modifier.weight(1f)) { st.toCall = it }
            }
            Text(
                "Банк — вместе с блайндами и ставками соперников. «Нужно доставить» — сколько добавить, чтобы уравнять (0 — если ставок нет).",
                color = Dim, fontSize = 11.sp,
            )
        }

        PokerResults(st)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun NumField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, { onChange(it.filter { c -> c.isDigit() || c == '.' || c == ',' }) },
        modifier, label = { Text(label, fontSize = 12.sp) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

private fun num(s: String) = s.replace(',', '.').toDoubleOrNull() ?: 0.0

private fun onSlotTap(st: PokerState, i: Int) {
    if (st.selected == i && st.slots[i] != null) st.clearSelected() else st.selected = i
}

@Composable
private fun CardSlot(card: Int?, selected: Boolean, small: Boolean = false, onClick: () -> Unit) {
    val w = if (small) 46.dp else 52.dp
    val h = if (small) 64.dp else 72.dp
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.size(w, h).clip(shape)
            .background(if (card != null) Color(0xFF0B1410) else BtnOff)
            .border(if (selected) 2.5.dp else 1.dp, if (selected) Gold else Line, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (card != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Cards.rankLabel(Cards.rank(card)), color = cardColor(card), fontSize = if (small) 19.sp else 22.sp, fontWeight = FontWeight.Bold)
                Text(Cards.SUITS[Cards.suit(card)], color = cardColor(card), fontSize = if (small) 17.sp else 20.sp)
            }
        } else {
            Text(if (selected) "▼" else "", color = Gold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun CardPicker(st: PokerState) {
    val pr = st.pendingRank
    val ps = st.pendingSuit
    fun rankEnabled(r: Int) = if (ps != null) !st.usedElsewhere(Cards.card(r, ps)) else (0..3).any { !st.usedElsewhere(Cards.card(r, it)) }
    fun suitEnabled(s: Int) = if (pr != null) !st.usedElsewhere(Cards.card(pr, s)) else true
    fun tapRank(r: Int) {
        if (ps != null) st.place(Cards.card(r, ps)) else st.pendingRank = if (pr == r) null else r
    }
    fun tapSuit(s: Int) {
        if (pr != null) st.place(Cards.card(pr, s)) else st.pendingSuit = if (ps == s) null else s
    }
    val rankColor = if (ps != null) SuitColors[ps] else TextMain

    Text("1) ранг  2) масть — в любом порядке", color = Dim, fontSize = 12.sp)
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (r in listOf(12, 11, 10, 9, 8, 7, 6)) {
            PickButton(Cards.rankLabel(r), Modifier.weight(1f), color = rankColor, selected = pr == r, enabled = rankEnabled(r)) { tapRank(r) }
        }
    }
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (r in listOf(5, 4, 3, 2, 1, 0)) {
            PickButton(Cards.rankLabel(r), Modifier.weight(1f), color = rankColor, selected = pr == r, enabled = rankEnabled(r)) { tapRank(r) }
        }
        PickButton("⌫", Modifier.weight(1f), color = Bad) { st.clearSelected() }
    }
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (s in 0..3) {
            PickButton(Cards.SUITS[s], Modifier.weight(1f), color = SuitColors[s], selected = ps == s, enabled = suitEnabled(s), height = 54.dp, fontSize = 30.sp) { tapSuit(s) }
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PickButton("Новая раздача", Modifier.weight(1f), fontSize = 14.sp, height = 40.dp) { st.clearAll() }
        PickButton("Очистить стол", Modifier.weight(1f), fontSize = 14.sp, height = 40.dp) {
            for (i in 2..6) st.slots[i] = null
            st.selected = 2
        }
    }
}

@Composable
private fun PokerResults(st: PokerState) {
    val board = st.slots.subList(2, 7).count { it != null }
    val r = st.result
    if (st.slots[0] == null || st.slots[1] == null) {
        Section { Text("Введи свои две карты — расчёт запустится сам.", color = Dim) }
        return
    }
    if (board in 1..2) {
        Section { Text("Флоп — это сразу 3 карты. Добавь остальные.", color = Dim) }
        return
    }
    if (r == null) {
        Section { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(22.dp), color = Gold); Spacer(Modifier.width(10.dp)); Text("Считаю…", color = Dim) } }
        return
    }
    val e = r.equity
    Section("Шансы") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(pct(e.equity), color = Gold, fontSize = 40.sp, fontWeight = FontWeight.Black)
                Text("эквити — твоя доля банка в среднем", color = Dim, fontSize = 12.sp)
            }
            if (st.computing) CircularProgressIndicator(Modifier.size(22.dp), color = Gold)
        }
        Spacer(Modifier.height(8.dp))
        Bar("Выигрыш", e.win, pct(e.win), Good)
        Bar("Делёж", e.tie, pct(e.tie), Gold)
        Bar("Проигрыш", e.lose, pct(e.lose), Bad)
        Spacer(Modifier.height(4.dp))
        Text(
            if (e.exact) "Точный перебор всех вариантов: ${"%,d".format(e.samples)}"
            else "Монте-Карло: ${"%,d".format(e.samples)} раздач, погрешность ±${pct(e.stdErr * 1.96, 2)} (95%)",
            color = Dim, fontSize = 11.sp,
        )
        r.handNow?.let { KV("Сейчас у тебя", it, Gold) }
        if (r.preflopClass != null && r.preflopTopPct != null) {
            KV("Стартовая рука", r.preflopClass)
            KV("Место среди 169 рук", "топ ${"%.1f".format(r.preflopTopPct)}%")
        }
    }

    val h0 = st.slots[0] ?: 0
    val h1 = st.slots[1] ?: 0
    val d = PokerAnalysis.decide(
        e.equity, st.opponents, num(st.pot), num(st.toCall),
        PokerAnalysis.Ctx(
            preflop = board == 0, top = r.preflopTopPct, cls = r.preflopClass, pos = st.position,
            stack = num(st.stack), bb = num(st.bb), range = st.rangePct, hero = intArrayOf(h0, h1),
        ),
    )
    val noBlindHint = board == 0 && num(st.bb) <= 0
    Section("Решение") {
        val c = when (d.color) { PokerAnalysis.GOOD -> Good; PokerAnalysis.MID -> Gold; else -> Bad }
        Text(d.verdict, color = c, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(4.dp))
        d.lines.forEach { Text("• $it", color = TextMain, fontSize = 13.sp, modifier = Modifier.padding(vertical = 1.dp)) }
        if (noBlindHint) {
            Text("• Введи большой блайнд (и стек) — дам префлоп-совет с учётом позиции: рейз, колл, пуш или пас.", color = Gold, fontSize = 13.sp)
        }
    }

    if (r.outsHitByRiver != null) {
        Section("Ауты (улучшающие карты)") {
            KV("Аутов", "${r.outs.size} из ${r.unseenCount}")
            KV(if (board == 3) "Поймать к риверу" else "Поймать на ривере", pct(r.outsHitByRiver), Gold)
            if (r.outs.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                r.outs.chunked(10).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        row.forEach { c ->
                            Box(
                                Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFF0B1410)).padding(horizontal = 4.dp, vertical = 2.dp),
                            ) { Text(Cards.label(c), color = cardColor(c), fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                }
            }
            Text("Аут — карта, повышающая твою комбинацию на уровень выше доски. Эквити выше уже учитывает всё точно.", color = Dim, fontSize = 11.sp)
        }
    }

    if (board < 5) {
        Section("Чем закончишь к риверу") {
            for (cat in 8 downTo 0) {
                val p = e.finalCat[cat]
                if (p < 0.0005) continue
                Bar(HandEval.NAMES[cat], p, pct(p), Gold)
            }
        }
    }
}
