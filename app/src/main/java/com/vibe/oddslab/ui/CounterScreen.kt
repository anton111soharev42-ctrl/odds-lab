package com.vibe.oddslab.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibe.oddslab.BjState
import com.vibe.oddslab.bj.BjEngine
import com.vibe.oddslab.bj.CountEngine

@Composable
fun CounterScreen(st: BjState) {
    val seen = IntArray(10) { st.seen[it] }
    val s = CountEngine.status(seen, st.rules)
    val left = st.cardsLeft()
    val total = st.rules.decks * 52

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Section {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Текущий счёт", color = Dim, fontSize = 12.sp)
                    Text(signed(s.running.toDouble(), 0), color = TextMain, fontSize = 38.sp, fontWeight = FontWeight.Black)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Истинный счёт", color = Dim, fontSize = 12.sp)
                    Text(
                        signed(s.trueCount, 1),
                        color = if (s.trueCount >= 1) Good else if (s.trueCount <= -1) Bad else Gold,
                        fontSize = 38.sp, fontWeight = FontWeight.Black,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            KV("Осталось карт", "$left из $total (${"%.1f".format(s.decksLeft)} колод)")
            KV("Пройдено шуза", pct(1 - left.toDouble() / total, 0))
            KV("Твой перевес (оценка)", (if (s.edgePct >= 0) "+" else "") + "%.2f%%".format(s.edgePct), if (s.edgePct > 0) Good else Bad)
            KV("Ставка", "${s.betUnits} ед.", Gold)
            Text(s.advice, color = TextMain, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }

        Section("Отметь вышедшую карту") {
            ValuePad(sub = { v ->
                val t = CountEngine.hiLo(v)
                (if (t > 0) "+1" else if (t < 0) "−1" else "0") + " · ост. ${st.remaining(v).coerceAtLeast(0)}"
            }) { st.addSeen(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PickButton("↶ Отменить", Modifier.weight(1f), fontSize = 14.sp, height = 42.dp, enabled = st.history.isNotEmpty()) { st.undoSeen() }
                PickButton("Новый шуз", Modifier.weight(1f), fontSize = 14.sp, height = 42.dp, color = Bad) { st.newShoe() }
            }
            if (st.history.isNotEmpty()) {
                Text(
                    "Последние: " + st.history.takeLast(14).joinToString(" ") { BjEngine.label(it) },
                    color = Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        Section("Шанс следующей карты") {
            for (v in listOf(10, 1, 9, 8, 7, 6, 5, 4, 3, 2)) {
                val rem = st.remaining(v).coerceAtLeast(0)
                val p = if (left > 0) rem.toDouble() / left else 0.0
                val base = BjEngine.STD[v - 1] / 52.0
                val name = if (v == 10) "10-ки" else BjEngine.label(v)
                Bar(name, p / 0.4, pct(p), if (p > base * 1.03) Good else if (p < base * 0.97) Bad else Gold)
            }
            Text("Зелёный — карт больше нормы, красный — меньше. Много тузов и десяток = выгодно игроку.", color = Dim, fontSize = 11.sp)
        }

        Section("Как пользоваться") {
            Text(
                "Hi-Lo: 2–6 = +1, 7–9 = 0, 10/картинки/туз = −1. Отмечай все открытые карты на столе (свои, чужие, дилера). " +
                    "Истинный счёт = текущий ÷ оставшиеся колоды; каждый +1 даёт игроку ≈ +0.5% перевеса. " +
                    "Вкладка «Блэкджек» использует этот шуз для точного расчёта решений.",
                color = TextMain, fontSize = 13.sp,
            )
        }
        Spacer(Modifier.width(1.dp).height(16.dp))
    }
}

private fun signed(x: Double, digits: Int) = (if (x > 0) "+" else if (x < 0) "−" else "") + "%.${digits}f".format(kotlin.math.abs(x))
