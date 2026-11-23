package com.vibe.oddslab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.vibe.oddslab.poker.PreflopTable
import com.vibe.oddslab.ui.BlackjackScreen
import com.vibe.oddslab.ui.Btn
import com.vibe.oddslab.ui.CounterScreen
import com.vibe.oddslab.ui.Dim
import com.vibe.oddslab.ui.Felt
import com.vibe.oddslab.ui.Gold
import com.vibe.oddslab.ui.OddsTheme
import com.vibe.oddslab.ui.Panel
import com.vibe.oddslab.ui.PokerScreen
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        thread(isDaemon = true, name = "preflop-table") { PreflopTable.table }
        setContent { OddsTheme { OddsApp() } }
    }
}

@Composable
fun OddsApp() {
    val app = remember { AppState() }
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("♠" to "Покер", "🂡" to "Блэкджек", "±" to "Счёт карт")
    Scaffold(
        containerColor = Felt,
        bottomBar = {
            NavigationBar(containerColor = Panel) {
                tabs.forEachIndexed { i, (icon, label) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Text(icon, fontSize = 22.sp) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedTextColor = Gold, unselectedTextColor = Dim,
                            selectedIconColor = Gold, unselectedIconColor = Dim, indicatorColor = Btn,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().background(Felt).padding(pad)) {
            when (tab) {
                0 -> PokerScreen(app.poker)
                1 -> BlackjackScreen(app.bj)
                else -> CounterScreen(app.bj)
            }
        }
    }
}
