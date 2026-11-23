package com.vibe.oddslab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibe.oddslab.poker.Cards

val Felt = Color(0xFF0E1C15)
val Panel = Color(0xFF16291F)
val Btn = Color(0xFF223A2D)
val BtnOff = Color(0xFF18261F)
val Line = Color(0xFF35503F)
val Gold = Color(0xFFE8B84A)
val Good = Color(0xFF4CC38A)
val Bad = Color(0xFFEF5D55)
val Dim = Color(0xFF94AE9F)
val TextMain = Color(0xFFEDF3EF)

/** Four-colour deck: ♠ white, ♥ red, ♦ blue, ♣ green. */
val SuitColors = arrayOf(Color(0xFFF2F2F2), Color(0xFFFF5F5F), Color(0xFF62A8FF), Color(0xFF5ED68E))

fun cardColor(c: Int) = SuitColors[Cards.suit(c)]

val AppColors = darkColorScheme(
    primary = Gold, onPrimary = Color.Black,
    background = Felt, surface = Panel, onSurface = TextMain, onBackground = TextMain,
    secondaryContainer = Btn, onSecondaryContainer = TextMain,
)

@Composable
fun OddsTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = AppColors, content = content)

@Composable
fun PickButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TextMain,
    selected: Boolean = false,
    enabled: Boolean = true,
    height: Dp = 46.dp,
    fontSize: TextUnit = 19.sp,
    sub: String? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    val bg = when { !enabled -> BtnOff; selected -> Color(0xFF3B6B4F); else -> Btn }
    Box(
        modifier
            .height(height)
            .clip(shape)
            .background(bg)
            .border(if (selected) 2.dp else 1.dp, if (selected) Gold else Line, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, color = if (enabled) color else color.copy(alpha = 0.22f), fontSize = fontSize, fontWeight = FontWeight.Bold)
            if (sub != null) Text(sub, color = if (enabled) Dim else Dim.copy(alpha = 0.3f), fontSize = 10.sp)
        }
    }
}

@Composable
fun Section(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Panel)
            .padding(12.dp),
    ) {
        if (title != null) {
            Text(title, color = Gold, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
        }
        content()
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = TextMain, fontSize = 14.sp)
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Good, checkedThumbColor = Color.White),
        )
    }
}

@Composable
fun KV(k: String, v: String, vColor: Color = TextMain) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(k, Modifier.weight(1f), color = Dim, fontSize = 14.sp)
        Text(v, color = vColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Bar(label: String, frac: Double, valueText: String, color: Color = Good) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(112.dp), color = TextMain, fontSize = 13.sp)
        Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(BtnOff)) {
            Box(
                Modifier.fillMaxWidth(frac.toFloat().coerceIn(0f, 1f)).height(10.dp)
                    .clip(RoundedCornerShape(5.dp)).background(color),
            )
        }
        Text(valueText, Modifier.width(58.dp).padding(start = 6.dp), color = TextMain, fontSize = 13.sp)
    }
}

@Composable
fun Stepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(1f), color = TextMain, fontSize = 15.sp)
        PickButton("−", Modifier.size(46.dp), enabled = value > min) { onChange(value - 1) }
        Text("$value", Modifier.width(30.dp), color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        PickButton("+", Modifier.size(46.dp), enabled = value < max) { onChange(value + 1) }
    }
}

fun pct(x: Double, digits: Int = 1) = "%.${digits}f%%".format(x * 100)

fun evText(ev: Double) = (if (ev >= 0) "+" else "−") + "%.1f%%".format(kotlin.math.abs(ev) * 100)
