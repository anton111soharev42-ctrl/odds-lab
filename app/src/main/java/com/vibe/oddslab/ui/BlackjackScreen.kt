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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibe.oddslab.BjState
import com.vibe.oddslab.bj.BjAction
import com.vibe.oddslab.bj.BjAnalysis
import com.vibe.oddslab.bj.BjEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun BlackjackScreen(st: BjState) {
    val player = st.player.toList()
    val seen = st.seen.toList()
    LaunchedEffect(player, st.up, st.rules, st.useCounter, seen) {
        val up = st.up
        if (player.size < 2 || up == null) {
            st.basic = null; st.counted = null; st.error = null
            return@LaunchedEffect
        }
        val rules = st.rules
        val fresh = BjEngine.freshShoe(rules.decks)
        val counter = IntArray(10) { rules.decks * BjEngine.STD[it] - seen[it] }
        val known = player + up
        known.forEach { fresh[it - 1]--; counter[it - 1]-- }
        if (fresh.any { it < 0 }) {
            st.error = "Столько таких карт нет в ${rules.decks}-колодном шузе."
            st.basic = null; st.counted = null
            return@LaunchedEffect
        }
        val counterBad = counter.any { it < 0 }
        val useCounter = st.useCounter && seen.sum() > 0
        val (b, c) = withContext(Dispatchers.Default) {
            val b = BjEngine.analyze(player, up, fresh, rules)
            val c = if (useCounter && !counterBad) BjEngine.analyze(player, up, counter, rules) else null
            b to c
        }
        st.error = if (useCounter && counterBad) "В счётчике уже выбыли все такие карты — проверь ввод. Показываю расчёт без счётчика." else null
        st.basic = b; st.counted = c
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp)) {
        RulesSection(st)

        Section {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("Дилер", color = Dim, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    BjCard(st.up?.let { BjEngine.label(it) }, st.toDealer || (st.player.size >= 2 && st.up == null)) {
                        if (st.up != null) st.up = null else st.toDealer = !st.toDealer
                    }
                }
                Column(Modifier.weight(3f)) {
                    val total = if (player.isEmpty()) "" else {
                        val h = player.sum(); val soft = 1 in player && h + 10 <= 21
                        " · " + (if (soft) "мягкие ${h + 10}" else "$h")
                    }
                    Text("Ты$total", color = Dim, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        player.forEachIndexed { i, v -> BjCard(BjEngine.label(v), false) { st.player.removeAt(i) } }
                        if (player.size < 6) BjCard(null, !st.toDealer && !(st.player.size >= 2 && st.up == null)) { st.toDealer = false }
                    }
                }
            }
            Text("Карты идут: 2 тебе → 1 дилеру → дальше тебе. Тап по карте — удалить, тап по слоту дилера — направить карту ему.", color = Dim, fontSize = 11.sp, modifier = Modifier.padding(vertical = 6.dp))
            ValuePad { st.addCard(it) }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PickButton("Очистить руку", Modifier.weight(1f), fontSize = 13.sp, height = 40.dp) { st.clearHand() }
                PickButton("Раунд сыгран → в счётчик", Modifier.weight(1.4f), fontSize = 13.sp, height = 40.dp, color = Gold) { st.commitRound() }
            }
            SwitchRow("Учитывать выбывшие карты из счётчика (${st.history.size})", st.useCounter) { st.useCounter = it }
        }

        st.error?.let { Section { Text(it, color = Bad) } }
        val b = st.basic
        if (b == null) {
            Section { Text("Введи свои 2 карты и открытую карту дилера.", color = Dim) }
        } else {
            BjResults(b, st.counted, st)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun RulesSection(st: BjState) {
    var open by remember { mutableStateOf(false) }
    val r = st.rules
    Section {
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Text("Правила стола", color = Gold, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(
                "${r.decks}к · ${if (r.h17) "H17" else "S17"} · ${if (r.bjPayout > 1.4) "3:2" else "6:5"} ${if (open) "▲" else "▼"}",
                color = Dim, fontSize = 13.sp,
            )
        }
        if (open) {
            Spacer(Modifier.height(8.dp))
            Text("Колод в шузе", color = Dim, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf(1, 2, 4, 6, 8).forEach { d ->
                    PickButton("$d", Modifier.weight(1f), selected = r.decks == d, height = 40.dp) { st.rules = r.copy(decks = d) }
                }
            }
            SwitchRow("Дилер берёт на мягких 17 (H17)", r.h17) { st.rules = r.copy(h17 = it) }
            SwitchRow("Удвоение после сплита (DAS)", r.das) { st.rules = r.copy(das = it) }
            SwitchRow("Можно сдаться (surrender)", r.surrender) { st.rules = r.copy(surrender = it) }
            SwitchRow("Блэкджек платит 3:2 (выкл = 6:5)", r.bjPayout > 1.4) { st.rules = r.copy(bjPayout = if (it) 1.5 else 1.2) }
            SwitchRow("Дилер проверяет блэкджек (peek)", r.peek) { st.rules = r.copy(peek = it) }
        }
    }
}

@Composable
private fun BjCard(text: String?, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.size(44.dp, 60.dp).clip(shape)
            .background(if (text != null) Color(0xFF0B1410) else BtnOff)
            .border(if (selected) 2.5.dp else 1.dp, if (selected) Gold else Line, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text ?: if (selected) "▼" else "", color = if (text != null) TextMain else Gold, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ValuePad(sub: ((Int) -> String)? = null, onTap: (Int) -> Unit) {
    val rows = listOf(listOf(1, 2, 3, 4, 5), listOf(6, 7, 8, 9, 10))
    rows.forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            row.forEach { v ->
                PickButton(
                    if (v == 10) "10·J·Q·K" else BjEngine.label(v), Modifier.weight(1f),
                    fontSize = if (v == 10) 14.sp else 21.sp, height = 50.dp, sub = sub?.invoke(v),
                ) { onTap(v) }
            }
        }
        Spacer(Modifier.height(5.dp))
    }
}

@Composable
private fun BjResults(basic: BjAnalysis, counted: BjAnalysis?, st: BjState) {
    val main = counted ?: basic
    val up = st.up ?: return

    if (main.isBlackjack) {
        Section("Блэкджек!") {
            KV("Ожидаемый выигрыш", evText(main.blackjackEv ?: 0.0), Good)
            if (up == 1) {
                KV("Вероятность BJ у дилера", pct(main.p10))
                val keep = main.blackjackEv ?: 0.0
                val verdict = if (1.0 > keep) "Бери «even money» (+100% гарантированно)" else "Откажись от even money — EV выше без него"
                Text(verdict, color = Gold, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            }
        }
        DealerSection(main, up)
        return
    }

    val best = main.best ?: return
    Section("Лучшее действие") {
        Text(best.ru.uppercase(), color = Good, fontSize = 32.sp, fontWeight = FontWeight.Black)
        Text("EV ${evText(main.evs[best] ?: 0.0)} от ставки", color = TextMain, fontSize = 14.sp)
        if (counted != null && basic.best != counted.best) {
            Spacer(Modifier.height(6.dp))
            Text(
                "⚠ Отклонение по счёту: по базовой стратегии — «${basic.best?.ru}», но с учётом вышедших карт выгоднее «${counted.best?.ru}».",
                color = Gold, fontSize = 13.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        val maxEv = main.evs.values.maxOrNull() ?: 0.0
        BjAction.entries.forEach { a ->
            val ev = main.evs[a] ?: return@forEach
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(a.ru, Modifier.weight(1f), color = if (a == best) Good else TextMain, fontWeight = if (a == best) FontWeight.Bold else FontWeight.Normal)
                val diff = ev - maxEv
                Text(evText(ev), color = if (ev >= 0) Good else Bad, fontWeight = FontWeight.SemiBold)
                Text(if (a == best) "" else "  (${evText(diff)})", Modifier.width(78.dp), color = Dim, fontSize = 12.sp)
            }
            if (counted != null) {
                basic.evs[a]?.let { Text("без счётчика: ${evText(it)}", color = Dim, fontSize = 11.sp) }
            }
        }
        Spacer(Modifier.height(6.dp))
        KV("Перебор, если взять карту", pct(main.bustOnHit), if (main.bustOnHit > 0.5) Bad else TextMain)
        Text(
            "EV в % от исходной ставки. Расчёт composition-dependent: учитывает реальный состав шуза${if (counted != null) " с учётом счётчика" else ""}; сплит — без пересплита.",
            color = Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp),
        )
    }
    if (up == 1) {
        Section("Страховка") {
            val ins = main.insuranceEv ?: 0.0
            KV("Шанс, что у дилера десятка", pct(main.p10))
            KV("EV страховки", evText(ins), if (ins > 0) Good else Bad)
            Text(
                if (ins > 0) "Страхуйся: доля десяток в шузе выше 1/3." else "Не страхуйся: нужна доля десяток > 33.3%.",
                color = if (ins > 0) Good else TextMain, fontWeight = FontWeight.Bold,
            )
        }
    }
    DealerSection(main, up)
}

@Composable
private fun DealerSection(a: BjAnalysis, up: Int) {
    Section("Дилер с картой ${BjEngine.label(up)}: чем закончит") {
        if (a.pDealerBj > 0) KV("Блэкджек у дилера (до проверки)", pct(a.pDealerBj))
        Bar("Перебор", a.dealer[5], pct(a.dealer[5]), Good)
        for (i in 0..4) Bar("${17 + i}", a.dealer[i], pct(a.dealer[i]), Bad)
        if (a.pDealerBj > 0) Text("Распределение — при условии, что блэкджека у дилера нет.", color = Dim, fontSize = 11.sp)
    }
}
